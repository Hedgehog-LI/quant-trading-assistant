import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { analyzeSource } from "../check-ai-architecture.mjs";
import {
  appendControlAnchor,
  controlAnchorWriteGuidance,
  validateControlAnchor,
  validateJsonSchema,
  validateTaskControl,
  validateTaskControlFiles
} from "../check-ai-task-control.mjs";
import { validateDeliveryReadiness } from "../check-ai-delivery-ready.mjs";
import { evaluateHook } from "../zcode-governance-hook.mjs";

function role(role, generation, id) {
  const agentByRole = {
    TEST_DESIGNER: "qta-test-designer",
    IMPLEMENTER: "qta-implementer",
    CODE_REVIEWER: "qta-code-reviewer",
    FINAL_VERIFIER: "qta-final-verifier"
  };
  const capabilityByRole = {
    TEST_DESIGNER: "READ_ONLY",
    IMPLEMENTER: "READ_WRITE",
    CODE_REVIEWER: "READ_ONLY",
    FINAL_VERIFIER: "VERIFY_EXECUTE"
  };
  return {
    roleRunId: id,
    dispatchId: `dispatch-${id}`,
    sessionId: `session-${id}`,
    startedAt: "2026-08-01T00:00:00Z",
    finishedAt: "2026-08-01T00:00:30Z",
    runtimeReceiptPath: `.git/qta-governance/sessions/${id}.json`,
    dispatchReceiptPath: `.git/qta-governance/dispatches/task/${id}.json`,
    role,
    generation,
    executorType: "SUBAGENT",
    agentDefinition: `.zcode/agents/${agentByRole[role]}.md`,
    sliceId: role === "IMPLEMENTER" ? "SLICE-01" : "",
    executionOutcome: "COMPLETED",
    capability: capabilityByRole[role],
    contextMode: "FRESH",
    enforcement: "ADVISORY",
    compensatingIsolation: "disposable worktree plus before/after candidate hashes",
    waitCalls: 1,
    maxShellPollsForOneCommand: role === "CODE_REVIEWER" ? 0 : 2,
    compactionCount: 0,
    status: "CLOSED",
    artifactAccepted: true,
    artifactPath: `artifacts/${id}.md`,
    artifactSha256: `sha-${id}`
  };
}

function transitions(states) {
  return states.slice(0, -1).map((from, index) => ({
    sequence: index + 1,
    from,
    to: states[index + 1],
    at: `2026-08-01T00:00:${String(index).padStart(2, "0")}Z`,
    actor: "parent-1"
  }));
}

function validVerifiedControl() {
  const identity = "commit-1";
  return {
    schemaVersion: 3,
    taskId: "GOVERNANCE-SMOKE",
    controlPath: "artifacts/control.json",
    startedAt: "2026-08-01T00:00:00Z",
    lane: "L2",
    lifecycleState: "VERIFIED",
    contract: {
      path: "artifacts/contract.md",
      version: "1",
      sha256: "sha-contract",
      acceptanceCriteria: [
        { id: "AC-01", requiredEvidence: ["STATIC", "AUTOMATION"] },
        { id: "AC-02", requiredEvidence: ["STATIC"] }
      ],
      implementationSlices: [{
        id: "SLICE-01", description: "bounded governance fixture", acIds: ["AC-01", "AC-02"],
        allowedWritePaths: ["src/main/java/example"], maxExpectedFiles: 4, maxProductionLineDelta: 300
      }],
      testInventory: [
        { testId: "TEST-STATIC-01", acIds: ["AC-01", "AC-02"], kind: "STATIC", required: true,
          sourcePath: "scripts/tests/ai-governance.test.mjs", selector: "accepts a structurally valid" },
        { testId: "TEST-AUTO-01", acIds: ["AC-01"], kind: "AUTOMATION", required: true,
          sourcePath: "scripts/tests/ai-governance.test.mjs", selector: "rejects reused sessions" }
      ],
      blockingAmendments: []
    },
    git: { automation: "COMMIT", branch: "codex/smoke", baselineCommit: "base", preExistingDirtyPaths: [] },
    transitionHistory: transitions([
      "CONTEXT_READY", "CONTRACT_DRAFTED", "TEST_DESIGN_READY", "CONTRACT_FROZEN",
      "IMPLEMENTING", "SELF_CHECKED", "CANDIDATE_FROZEN", "REVIEW_CLEAR", "VERIFIED"
    ]),
    candidate: {
      mode: "COMMIT", generation: 1, identity, commit: identity, treeHash: "tree-1",
      patchSha256: "patch-1", manifestPath: "", manifestSha256: "", entrySetSha256: "",
      diffArtifactPath: "artifacts/candidate.patch", diffArtifactSha256: "patch-1"
    },
    budget: {
      contextPercent: 22, contextMeasurement: "RUNTIME", compactionCount: 0,
      repairRound: 0, maxRepairRounds: 2,
      maxWaitCallsPerRole: 2, maxShellPollsPerCommand: 3, rawTokens: 1000,
      weeklyAllowancePercent: 1
    },
    repairHistory: [],
    roleRuns: [
      role("TEST_DESIGNER", 0, "test-1"), role("IMPLEMENTER", 1, "impl-1"),
      role("CODE_REVIEWER", 1, "review-1"), role("FINAL_VERIFIER", 1, "verify-1")
    ],
    review: {
      omitted: false, omissionReason: "", generation: 1, candidateIdentity: identity,
      functionalVerdict: "PASS", architectureVerdict: "PASS",
      artifactPath: "artifacts/review-1.md", artifactSha256: "sha-review-1",
      architectureGateSha256: "sha-architecture-1", findingIds: []
    },
    architectureGate: {
      required: true, candidateIdentity: identity, status: "PASS", exitCode: 0,
      errorCount: 0, warningCount: 0, warningDispositions: [],
      reportPath: "artifacts/architecture-1.json", reportSha256: "sha-architecture-1",
      generatedBy: "scripts/check-ai-architecture.mjs"
    },
    verification: {
      candidateIdentity: identity, verdict: "ACCEPTED", deliveryPermitted: true,
      functionalVerdict: "PASS", architectureVerdict: "PASS",
      artifactPath: "artifacts/verify-1.md", artifactSha256: "sha-verify-1",
      architectureGateSha256: "sha-architecture-1",
      dimensions: {
        STATIC: { required: true, status: "PASS" },
        AUTOMATION: { required: true, status: "PASS" },
        RUNTIME: { required: false, status: "NOT_REQUIRED" },
        DEPLOYMENT: { required: false, status: "NOT_REQUIRED" }
      }
    },
    finalization: {
      status: "NOT_STARTED", candidateIdentity: "", artifactPath: "", artifactSha256: "",
      completedAt: "", changedPaths: []
    },
    testEvidence: [
      { testId: "TEST-STATIC-01", candidateIdentity: identity, executedByRoleRunId: "verify-1",
        result: "PASS", exitCode: 0, receiptPath: "artifacts/test-static-1.json",
        receiptSha256: "sha-test-static-1", observedSelectors: ["accepts a structurally valid"] },
      { testId: "TEST-AUTO-01", candidateIdentity: identity, executedByRoleRunId: "verify-1",
        result: "PASS", exitCode: 0, receiptPath: "artifacts/test-auto-1.json",
        receiptSha256: "sha-test-auto-1", observedSelectors: ["rejects reused sessions"] }
    ],
    evidence: [
      { evidenceId: "E-1", acId: "AC-01", kind: "STATIC", candidateIdentity: identity,
        sourceType: "TEST_RECEIPT", sourceId: "TEST-STATIC-01",
        artifactPath: "artifacts/test-static-1.json", artifactSha256: "sha-test-static-1" },
      { evidenceId: "E-2", acId: "AC-01", kind: "AUTOMATION", candidateIdentity: identity,
        sourceType: "TEST_RECEIPT", sourceId: "TEST-AUTO-01",
        artifactPath: "artifacts/test-auto-1.json", artifactSha256: "sha-test-auto-1" },
      { evidenceId: "E-3", acId: "AC-02", kind: "STATIC", candidateIdentity: identity,
        sourceType: "TEST_RECEIPT", sourceId: "TEST-STATIC-01",
        artifactPath: "artifacts/test-static-1.json", artifactSha256: "sha-test-static-1" }
    ]
  };
}

test("accepts a structurally valid L2 verified control ledger", () => {
  assert.deepEqual(validateTaskControl(validVerifiedControl()).errors, []);
});

test("rejects reused sessions, excessive waits, and compacted accepted roles", () => {
  const control = validVerifiedControl();
  control.roleRuns[2].sessionId = control.roleRuns[1].sessionId;
  control.roleRuns[2].waitCalls = 3;
  control.roleRuns[1].compactionCount = 1;
  const errors = validateTaskControl(control).errors.join("\n");
  assert.match(errors, /reused role sessionId/);
  assert.match(errors, /waitCalls must be between 0 and 2/);
  assert.match(errors, /compacted but is not marked POLICY_VIOLATION/);
});

test("rejects unverifiable ENFORCED claims", () => {
  const control = validVerifiedControl();
  control.roleRuns[0].enforcement = "ENFORCED";
  assert.match(validateTaskControl(control).errors.join("\n"), /must be ADVISORY/);
});

test("rejects parent substitution and plan-only final verification", () => {
  const control = validVerifiedControl();
  control.roleRuns.find((run) => run.role === "IMPLEMENTER").executorType = "PARENT";
  const verifier = control.roleRuns.find((run) => run.role === "FINAL_VERIFIER");
  verifier.executionOutcome = "PLAN_ONLY";
  verifier.status = "BLOCKED";
  verifier.artifactAccepted = true;
  const errors = validateTaskControl(control).errors.join("\n");
  assert.match(errors, /parent coordinator cannot be accepted as IMPLEMENTER/);
  assert.match(errors, /plan-only role output cannot be accepted/);
  assert.match(errors, /accepted fresh final verifier role run is missing/);
});

test("requires timeout attempts to be recorded and blocks after two timeouts for one slice", () => {
  const control = validVerifiedControl();
  for (const id of ["impl-timeout-1", "impl-timeout-2"]) {
    const attempt = role("IMPLEMENTER", 1, id);
    attempt.executionOutcome = "TIMED_OUT";
    attempt.status = "BLOCKED";
    attempt.artifactAccepted = false;
    attempt.artifactPath = "";
    attempt.artifactSha256 = "";
    control.roleRuns.splice(1, 0, attempt);
  }
  assert.match(validateTaskControl(control).errors.join("\n"), /two timeouts.*requires BLOCKED/i);
});

test("treats architecture errors as an unwaivable hard block", () => {
  const control = validVerifiedControl();
  control.architectureGate.status = "FAIL";
  control.architectureGate.exitCode = 1;
  control.architectureGate.errorCount = 1;
  assert.match(validateTaskControl(control).errors.join("\n"), /architecture gate must have zero errors/);
});

test("rejects missing frozen test-inventory evidence", () => {
  const control = validVerifiedControl();
  control.testEvidence = control.testEvidence.filter((item) => item.testId !== "TEST-AUTO-01");
  assert.match(validateTaskControl(control).errors.join("\n"), /TEST-AUTO-01.*missing passing machine receipt/);
});

test("rejects future and non-monotonic declared timestamps", () => {
  const control = validVerifiedControl();
  control.roleRuns[0].startedAt = "2099-01-01T00:00:00Z";
  control.roleRuns[0].finishedAt = "2099-01-01T00:00:01Z";
  control.transitionHistory[2].at = "2026-07-31T00:00:00Z";
  const errors = validateTaskControl(control).errors.join("\n");
  assert.match(errors, /timestamp is in the future/);
  assert.match(errors, /transition timestamps must be monotonic/);
});

test("rejects skipped implementation and accepted blocked roles", () => {
  const control = validVerifiedControl();
  control.roleRuns = control.roleRuns.filter((run) => run.role !== "IMPLEMENTER");
  control.roleRuns[0].status = "BLOCKED";
  const errors = validateTaskControl(control).errors.join("\n");
  assert.match(errors, /accepted implementer role run missing/);
  assert.match(errors, /accepted artifact requires a completed SUBAGENT, CLOSED\/COMPLETED/);
});

test("enforces repair generations and per-lane contract budgets", () => {
  const control = validVerifiedControl();
  control.lane = "L1";
  control.budget.repairRound = 2;
  control.candidate.generation = 3;
  control.contract.acceptanceCriteria = Array.from({ length: 6 }, (_, index) => ({
    id: `AC-0${index + 1}`, requiredEvidence: ["STATIC"]
  }));
  const errors = validateTaskControl(control).errors.join("\n");
  assert.match(errors, /L1 allows at most 5 ACs/);
  assert.match(errors, /repairHistory length must equal repairRound/);
  assert.match(errors, /implementer role run missing for generation 2/);
});

test("allows the explicit L0 direct-verifier lifecycle", () => {
  const control = validVerifiedControl();
  control.lane = "L0";
  control.contract.acceptanceCriteria = [{ id: "AC-01", requiredEvidence: ["STATIC"] }];
  control.contract.implementationSlices[0].acIds = ["AC-01"];
  control.contract.testInventory = [control.contract.testInventory[0]];
  control.contract.testInventory[0].acIds = ["AC-01"];
  control.testEvidence = [control.testEvidence[0]];
  control.transitionHistory = transitions([
    "CONTEXT_READY", "CONTRACT_DRAFTED", "CONTRACT_FROZEN", "IMPLEMENTING",
    "SELF_CHECKED", "CANDIDATE_FROZEN", "VERIFIED"
  ]);
  control.roleRuns = [role("IMPLEMENTER", 1, "impl-1"), role("FINAL_VERIFIER", 1, "verify-1")];
  control.review = {
    omitted: true, omissionReason: "L0 contract-lite", generation: 0, candidateIdentity: "",
    functionalVerdict: "NOT_REQUIRED", architectureVerdict: "NOT_REQUIRED",
    artifactPath: "", artifactSha256: "", findingIds: []
  };
  control.evidence = [control.evidence[0]];
  assert.deepEqual(validateTaskControl(control).errors, []);
});

test("applies the task-control JSON schema", async () => {
  const schema = JSON.parse(await readFile(".agents/schemas/qta-task-control.schema.json", "utf8"));
  const control = validVerifiedControl();
  delete control.transitionHistory;
  assert.match(validateJsonSchema(control, schema).join("\n"), /transitionHistory is required/);
  control.verification.dimensions.STATIC.status = "MADE_UP";
  assert.match(validateJsonSchema(control, schema).join("\n"), /STATIC.status is not in the allowed enum/);
});

test("rejects missing scalar budgets and malformed role or repair ledgers", () => {
  const control = validVerifiedControl();
  delete control.budget.maxWaitCallsPerRole;
  control.roleRuns = {};
  control.repairHistory = {};
  const errors = validateTaskControl(control).errors.join("\n");
  assert.match(errors, /maxWaitCallsPerRole must be 2/);
  assert.match(errors, /roleRuns must be an array/);
  assert.match(errors, /repairHistory must be an array/);
});

test("does not invent context percentages when runtime telemetry is unavailable", () => {
  const control = validVerifiedControl();
  control.budget.contextMeasurement = "UNAVAILABLE";
  control.budget.contextPercent = null;
  assert.deepEqual(validateTaskControl(control).errors, []);
  control.budget.contextPercent = 5;
  assert.match(validateTaskControl(control).errors.join("\n"), /requires contextPercent=null/);
});

test("returns scoped guidance for a read-only Git anchor store", () => {
  assert.match(controlAnchorWriteGuidance({ code: "EPERM" }), /grant scoped permission/);
  assert.equal(controlAnchorWriteGuidance({ code: "ENOENT" }), null);
});

test("binds review and verification artifacts to their accepted role runs", () => {
  const control = validVerifiedControl();
  control.review.artifactPath = "artifacts/unrelated-review.md";
  control.verification.artifactSha256 = "sha-unrelated-verifier";
  const errors = validateTaskControl(control).errors.join("\n");
  assert.match(errors, /review artifact is not bound/);
  assert.match(errors, /verification artifact is not bound/);
});

test("requires repair history to identify the finding and repair role runs", () => {
  const control = validVerifiedControl();
  const identity = "commit-2";
  control.budget.repairRound = 1;
  control.candidate = {
    ...control.candidate, generation: 2, identity, commit: identity
  };
  control.transitionHistory = transitions([
    "CONTEXT_READY", "CONTRACT_DRAFTED", "TEST_DESIGN_READY", "CONTRACT_FROZEN",
    "IMPLEMENTING", "SELF_CHECKED", "CANDIDATE_FROZEN", "REVIEW_CLEAR",
    "IMPLEMENTING", "SELF_CHECKED", "CANDIDATE_FROZEN", "REVIEW_CLEAR", "VERIFIED"
  ]);
  control.roleRuns = [
    role("TEST_DESIGNER", 0, "test-1"), role("IMPLEMENTER", 1, "impl-1"),
    role("CODE_REVIEWER", 1, "review-1"), role("IMPLEMENTER", 2, "impl-2"),
    role("CODE_REVIEWER", 2, "review-2"), role("FINAL_VERIFIER", 2, "verify-2")
  ];
  control.repairHistory = [{
    round: 1, failureFingerprint: "P1:artifact-binding", fromGeneration: 1, toGeneration: 2,
    findingRoleRunId: "review-1", implementerRoleRunId: "impl-2"
  }];
  control.review = {
    ...control.review, generation: 2, candidateIdentity: identity,
    artifactPath: "artifacts/review-2.md", artifactSha256: "sha-review-2"
  };
  control.verification = {
    ...control.verification, candidateIdentity: identity,
    artifactPath: "artifacts/verify-2.md", artifactSha256: "sha-verify-2"
  };
  control.architectureGate.candidateIdentity = identity;
  control.testEvidence = control.testEvidence.map((item) => ({
    ...item, candidateIdentity: identity, executedByRoleRunId: "verify-2"
  }));
  control.evidence = control.evidence.map((item) => ({ ...item, candidateIdentity: identity }));
  assert.deepEqual(validateTaskControl(control).errors, []);

  control.repairHistory[0].findingRoleRunId = "impl-1";
  assert.match(validateTaskControl(control).errors.join("\n"), /not an accepted finding role/);
});

test("rejects repair, transition, counter, and role identity rollback from an anchored control", async () => {
  const { validateMonotonicControl } = await import("../check-ai-task-control.mjs");
  const previous = validVerifiedControl();
  previous.budget.repairRound = 1;
  previous.budget.compactionCount = 1;
  previous.repairHistory = [{
    round: 1, failureFingerprint: "P1:rollback", fromGeneration: 1, toGeneration: 2,
    findingRoleRunId: "review-1", implementerRoleRunId: "impl-2"
  }];
  const current = structuredClone(previous);
  current.transitionHistory.pop();
  current.repairHistory = [];
  current.budget.repairRound = 0;
  current.budget.compactionCount = 0;
  current.roleRuns[1].sessionId = "invented-session";
  const errors = validateMonotonicControl(previous, current).join("\n");
  assert.match(errors, /transitionHistory rewrote or removed/);
  assert.match(errors, /repairHistory rewrote or removed/);
  assert.match(errors, /repairRound decreased/);
  assert.match(errors, /compactionCount decreased/);
  assert.match(errors, /roleRuns rewrote or removed/);
});

test("rejects verification and finalization evidence rewrite after anchoring", async () => {
  const { validateMonotonicControl } = await import("../check-ai-task-control.mjs");
  const previous = validVerifiedControl();
  previous.lifecycleState = "FINALIZED";
  previous.finalization = {
    status: "COMPLETED", candidateIdentity: previous.candidate.identity,
    artifactPath: "artifacts/finalization.md", artifactSha256: "finalization-sha", changedPaths: []
  };
  const current = structuredClone(previous);
  current.verification.verdict = "REJECTED";
  current.finalization.artifactSha256 = "rewritten-sha";
  current.evidence = [];
  const errors = validateMonotonicControl(previous, current).join("\n");
  assert.match(errors, /verification evidence changed/);
  assert.match(errors, /finalization evidence changed/);
  assert.match(errors, /AC evidence rewrote or removed/);
});

test("hash-chained control anchor persists and detects rollback on disk", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-control-anchor-"));
  try {
    assert.equal(spawnSync("git", ["init", "-q"], { cwd: directory }).status, 0);
    const previous = validVerifiedControl();
    assert.match((await validateControlAnchor(previous, directory)).join("\n"), /control anchor is missing/);
    const draft = structuredClone(previous);
    draft.lifecycleState = "CONTRACT_DRAFTED";
    assert.deepEqual(await validateControlAnchor(draft, directory), []);
    await appendControlAnchor(previous, directory);
    assert.deepEqual(await validateControlAnchor(previous, directory), []);

    const rollback = structuredClone(previous);
    rollback.transitionHistory.pop();
    assert.match((await validateControlAnchor(rollback, directory)).join("\n"), /transitionHistory rewrote/);

    const anchorName = createHash("sha256").update(previous.taskId).digest("hex");
    const anchorPath = path.join(directory, ".git", "qta-governance", "tasks", `${anchorName}.jsonl`);
    await writeFile(anchorPath, "tampered\n");
    assert.match((await validateControlAnchor(previous, directory)).join("\n"), /control anchor cannot be verified/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("file validation rejects fabricated contract, candidate, role, and evidence hashes", async () => {
  const errors = await validateTaskControlFiles(validVerifiedControl(), process.cwd());
  assert.ok(errors.length >= 4);
  assert.match(errors.join("\n"), /contract is unavailable/);
});

test("file validation rejects drift from a frozen SNAPSHOT manifest", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-task-snapshot-"));
  try {
    const sourcePath = "candidate.txt";
    await writeFile(path.join(directory, sourcePath), "frozen\n");
    const entries = [{
      path: sourcePath, type: "file",
      sha256: createHash("sha256").update("frozen\n").digest("hex")
    }];
    const entrySetSha256 = createHash("sha256").update(JSON.stringify(entries)).digest("hex");
    const manifest = `${JSON.stringify({ version: 1, entrySetSha256, entries }, null, 2)}\n`;
    await writeFile(path.join(directory, "candidate.json"), manifest);
    await writeFile(path.join(directory, sourcePath), "drifted\n");
    const control = {
      contract: {}, lane: "L0", lifecycleState: "CANDIDATE_FROZEN", roleRuns: [],
      candidate: {
        mode: "SNAPSHOT", manifestPath: "candidate.json",
        manifestSha256: createHash("sha256").update(manifest).digest("hex"), entrySetSha256,
        diffArtifactPath: "", diffArtifactSha256: ""
      }
    };
    const errors = await validateTaskControlFiles(control, directory);
    assert.match(errors.join("\n"), /candidate snapshot file drifted/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("SNAPSHOT validation rejects a changed file omitted from the manifest", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-task-coverage-"));
  try {
    for (const args of [["init", "-q"], ["config", "user.email", "qta@example.test"],
      ["config", "user.name", "QTA Test"]]) {
      assert.equal(spawnSync("git", args, { cwd: directory }).status, 0);
    }
    await writeFile(path.join(directory, "covered.txt"), "base\n");
    await writeFile(path.join(directory, "omitted.txt"), "base\n");
    assert.equal(spawnSync("git", ["add", "."], { cwd: directory }).status, 0);
    assert.equal(spawnSync("git", ["commit", "-qm", "baseline"], { cwd: directory }).status, 0);
    await writeFile(path.join(directory, "covered.txt"), "candidate\n");
    await writeFile(path.join(directory, "omitted.txt"), "candidate\n");
    const entries = [{
      path: "covered.txt", type: "file",
      sha256: createHash("sha256").update("candidate\n").digest("hex")
    }];
    const entrySetSha256 = createHash("sha256").update(JSON.stringify(entries)).digest("hex");
    const manifest = `${JSON.stringify({ version: 1, entrySetSha256, entries }, null, 2)}\n`;
    await writeFile(path.join(directory, "candidate.json"), manifest);
    await writeFile(path.join(directory, "candidate.patch"), "frozen patch\n");
    const control = {
      controlPath: "control.json", contract: {}, lane: "L0", lifecycleState: "CANDIDATE_FROZEN",
      git: { baselineCommit: "HEAD", preExistingDirtyPaths: [] }, roleRuns: [], evidence: [],
      finalization: { changedPaths: [] },
      candidate: {
        mode: "SNAPSHOT", manifestPath: "candidate.json",
        manifestSha256: createHash("sha256").update(manifest).digest("hex"), entrySetSha256,
        diffArtifactPath: "candidate.patch",
        diffArtifactSha256: createHash("sha256").update("frozen patch\n").digest("hex")
      }
    };
    const errors = await validateTaskControlFiles(control, directory);
    assert.match(errors.join("\n"), /candidate SNAPSHOT omits changed path: omitted\.txt/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("rejects a fabricated finalization without a bound artifact", () => {
  const control = validVerifiedControl();
  control.lifecycleState = "FINALIZED";
  control.transitionHistory.push({
    sequence: 9, from: "VERIFIED", to: "FINALIZED", at: "2026-08-01T00:00:09Z", actor: "parent-1"
  });
  assert.match(validateTaskControl(control).errors.join("\n"), /completed finalization artifact/);
});

test("delivery readiness rejects reused verification artifacts and non-ready state", () => {
  const control = validVerifiedControl();
  let errors = validateDeliveryReadiness(control, { checkGit: false }).errors.join("\n");
  assert.match(errors, /lifecycleState must be DELIVERY_READY/);

  control.lifecycleState = "DELIVERY_READY";
  control.transitionHistory.push({
    sequence: 9, from: "VERIFIED", to: "FINALIZED", at: "2026-08-01T00:00:09Z", actor: "parent-1"
  });
  control.transitionHistory.push({
    sequence: 10, from: "FINALIZED", to: "DELIVERY_READY", at: "2026-08-01T00:00:10Z", actor: "parent-1"
  });
  control.finalization = {
    status: "COMPLETED", candidateIdentity: control.candidate.identity,
    artifactPath: control.verification.artifactPath,
    artifactSha256: control.verification.artifactSha256,
    completedAt: "2026-08-01T00:01:00Z", changedPaths: []
  };
  errors = validateDeliveryReadiness(control, { checkGit: false }).errors.join("\n");
  assert.match(errors, /finalization artifact must be distinct from verification evidence/);
});

test("architecture gate flags a service that parses files and persists", () => {
  const methods = Array.from({ length: 31 }, (_, index) =>
    `  public void method${index}() { mapper.save(reader.readLine()); }`).join("\n");
  const filler = Array.from({ length: 620 }, () => "  private int value = 1;").join("\n");
  const source = `package demo;\nimport java.io.BufferedReader;\nclass HugeService {\n  private SecurityMapper mapper;\n${methods}\n${filler}\n}`;
  const report = analyzeSource("src/main/java/demo/service/HugeService.java", source);
  assert.ok(report.errors.some((error) => error.includes("service combines file/protocol parsing")));
  assert.ok(report.errors.some((error) => error.includes("class/module")));
});

test("architecture parser detects multiline long Java methods", () => {
  const body = Array.from({ length: 110 }, (_, index) => `    int value${index} = ${index};`).join("\n");
  const source = `class LongMethod {\n  public int calculate(\n      int input\n  ) {\n${body}\n    return input;\n  }\n}`;
  const report = analyzeSource("src/main/java/demo/LongMethod.java", source);
  assert.equal(report.methods, 1);
  assert.ok(report.errors.some((error) => error.includes("longest method")));
});

test("architecture parser does not count TypeScript conditionals as methods", () => {
  const conditionals = Array.from({ length: 31 }, (_, index) => `  if (value === ${index}) { value += 1; }`).join("\n");
  const source = `export function calculate(value: number) {\n${conditionals}\n  return value;\n}`;
  const report = analyzeSource("src/features/demo/calculate.ts", source);
  assert.equal(report.methods, 1);
  assert.ok(!report.errors.some((error) => error.includes("methods")));
});

test("architecture gate accepts a small layered class", () => {
  const source = "package demo;\nclass PriceManager {\n  public int add(int left, int right) { return left + right; }\n}";
  assert.deepEqual(analyzeSource("src/main/java/demo/manager/PriceManager.java", source).errors, []);
});

test("architecture CLI analyzes the exact frozen SNAPSHOT manifest", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-architecture-snapshot-"));
  const sourcePath = "src/main/java/demo/service/SnapshotService.java";
  try {
    await mkdir(path.join(directory, path.dirname(sourcePath)), { recursive: true });
    await writeFile(path.join(directory, sourcePath), "class SnapshotService {}\n");
    for (const args of [["init", "-q"], ["config", "user.email", "qta@example.test"],
      ["config", "user.name", "QTA Test"], ["add", sourcePath], ["commit", "-qm", "baseline"]]) {
      assert.equal(spawnSync("git", args, { cwd: directory }).status, 0);
    }
    const methods = Array.from({ length: 31 }, (_, index) =>
      `  public void method${index}() { mapper.save(reader.readLine()); }`).join("\n");
    const filler = Array.from({ length: 620 }, () => "  private int value = 1;").join("\n");
    const source = `import java.io.BufferedReader;\nclass SnapshotService {\n  private SecurityMapper mapper;\n${methods}\n${filler}\n}`;
    await writeFile(path.join(directory, sourcePath), source);
    const hash = createHash("sha256").update(source).digest("hex");
    await writeFile(path.join(directory, "candidate.json"), JSON.stringify({
      entries: [{ path: sourcePath, type: "file", sha256: hash }]
    }));
    const reportPath = "architecture-report.json";
    const script = path.resolve("scripts/check-ai-architecture.mjs");
    const result = spawnSync(process.execPath, [script, "--base", "HEAD", "--manifest", "candidate.json",
      "--architecture-review-count", "2", "--candidate-identity", "candidate-1",
      "--json-output", reportPath], { cwd: directory, encoding: "utf8" });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /SnapshotService\.java/);
    assert.match(result.stdout, /service combines file\/protocol parsing with persistence/);
    const report = JSON.parse(await readFile(path.join(directory, reportPath), "utf8"));
    assert.equal(report.candidateIdentity, "candidate-1");
    assert.equal(report.status, "FAIL");
    assert.ok(report.errors.some((item) => item.id === "ARCH-E-001"));
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("evidence runner emits a low-noise candidate-bound receipt", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-evidence-runner-"));
  try {
    for (const args of [["init", "-q"], ["config", "user.email", "qta@example.test"],
      ["config", "user.name", "QTA Test"]]) {
      assert.equal(spawnSync("git", args, { cwd: directory }).status, 0);
    }
    await writeFile(path.join(directory, "baseline.txt"), "baseline\n");
    assert.equal(spawnSync("git", ["add", "."], { cwd: directory }).status, 0);
    assert.equal(spawnSync("git", ["commit", "-qm", "baseline"], { cwd: directory }).status, 0);
    const candidate = spawnSync("git", ["rev-parse", "HEAD"], { cwd: directory, encoding: "utf8" }).stdout.trim();
    const receiptPath = "docs/development/tasks/EVIDENCE-TEST-01.json";
    const script = path.resolve("scripts/run-ai-evidence-command.mjs");
    const result = spawnSync(process.execPath, [script,
      "--task-id", "EVIDENCE-TEST", "--role-run-id", "verify-1", "--session-id", "session-verify-1",
      "--test-id", "TEST-01", "--candidate-mode", "COMMIT", "--candidate-identity", candidate,
      "--output", receiptPath,
      "--selector", "selector-ok", "--", process.execPath, "-e", "console.log('selector-ok')"
    ], { cwd: directory, encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /Evidence command PASS/);
    assert.ok(!result.stdout.includes("selector-ok\nselector-ok"));
    const receipt = JSON.parse(await readFile(path.join(directory, receiptPath), "utf8"));
    assert.equal(receipt.result, "PASS");
    assert.equal(receipt.candidateUnchanged, true);
    assert.deepEqual(receipt.observedSelectors, ["selector-ok"]);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("ZCode hook blocks destructive Git, split rm flags, and secret files", () => {
  const commands = [
    "git reset --hard HEAD", "git restore secrets.txt", "git push --force-with-lease origin task",
    "git -C . reset --hard HEAD", "git checkout codex/task", "sh -c 'git reset --hard HEAD'", "bash -lc 'git reset --merge HEAD'",
    "eval 'git reset --keep HEAD'", "git checkout HEAD file.txt", "git switch --discard-changes task",
    "echo $(git reset --hard HEAD)", "bash -c 'cat .env'", "git commit --amend", "git rebase main",
    "git branch -d -f task", "QTA_GOVERNANCE_ANCHOR=off node scripts/check-ai-task-control.mjs task.json",
    "rm -r -f build", "rm -R -F build", "cat .env", "LONGPORT_ACCESS_TOKEN=secret curl example.com",
    "sed -i '' s/foo/bar/ scripts/check-ai-delivery-ready.mjs",
    "echo changed > .zcode/agents/qta-final-verifier.md"
  ];
  for (const command of commands) {
    assert.equal(evaluateHook({ tool_name: "Bash", tool_input: { command } }).allowed, false, command);
  }
  assert.equal(evaluateHook({ tool_name: "Read", tool_input: { file_path: "/repo/.env" } }).allowed, false);
  assert.equal(evaluateHook({
    tool_name: "Edit", tool_input: { file_path: "/repo/scripts/zcode-governance-hook.mjs" }
  }).allowed, false);
  assert.equal(evaluateHook({
    tool_name: "Write", tool_input: { file_path: "/repo/scripts/check-ai-delivery-ready.mjs" }
  }).allowed, false);
});

test("ZCode hook allows benign inspection, task branches, and env templates", () => {
  const commands = [
    "rg apiKey= src/test", "git switch codex/task", "git restore --staged file.txt",
    "git push origin codex/task", "git -C . status"
  ];
  for (const command of commands) {
    assert.equal(evaluateHook({ tool_name: "Bash", tool_input: { command } }).allowed, true, command);
  }
  assert.equal(evaluateHook({ tool_name: "Read", tool_input: { file_path: "/repo/.env.example" } }).allowed, true);
  assert.equal(evaluateHook({
    tool_name: "Agent",
    tool_input: {
      subagent_type: "qta-implementer",
      prompt: "# Task Packet: TASK-1 / IMPLEMENTER / impl-1\n- Dispatch ID: dispatch-1"
    }
  }).allowed, true);
  assert.equal(evaluateHook({
    tool_name: "Agent", tool_input: { subagent_type: "qta-final-verifier", prompt: "verify it" }
  }).allowed, false);
});

test("ZCode hook process uses the documented exit-code contract", () => {
  const script = path.resolve("scripts/zcode-governance-hook.mjs");
  const blocked = spawnSync(process.execPath, [script], {
    input: JSON.stringify({ tool_name: "Bash", tool_input: { command: "git -C . reset --hard HEAD" } }),
    encoding: "utf8", env: { ...process.env, QTA_GOVERNANCE_AUDIT: "off" }
  });
  assert.equal(blocked.status, 2);
  assert.match(blocked.stderr, /QTA governance blocked this action/);
  assert.equal(blocked.stdout, "");

  const allowed = spawnSync(process.execPath, [script], {
    input: JSON.stringify({ tool_name: "Bash", tool_input: { command: "git -C . status" } }),
    encoding: "utf8", env: { ...process.env, QTA_GOVERNANCE_AUDIT: "off" }
  });
  assert.equal(allowed.status, 0);
  assert.equal(allowed.stdout, "");
});

test("ZCode Hook receipt binds ADVISORY evidence to an observed runtime session", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-runtime-receipt-"));
  const sessionId = "runtime-session-1";
  const startedAt = new Date(Date.now() - 1000).toISOString();
  try {
    await mkdir(path.join(directory, ".git"), { recursive: true });
    const script = path.resolve("scripts/zcode-governance-hook.mjs");
    const hook = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        session_id: sessionId, cwd: directory, transcript_path: "/tmp/transcript.jsonl",
        tool_name: "Bash", tool_input: { command: "git status" }
      }),
      encoding: "utf8",
      env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.equal(hook.status, 0);
    const receiptName = `${createHash("sha256").update(sessionId).digest("hex")}.json`;
    const receiptPath = `.git/qta-governance/sessions/${receiptName}`;
    const control = {
      startedAt,
      contract: {}, lane: "L0", lifecycleState: "CONTEXT_READY", candidate: {},
      roleRuns: [{
        roleRunId: "role-runtime-1", sessionId, role: "IMPLEMENTER", generation: 1,
        startedAt, finishedAt: new Date(Date.now() + 1000).toISOString(),
        enforcement: "ADVISORY", runtimeReceiptPath: receiptPath, artifactAccepted: false
      }]
    };
    assert.deepEqual(await validateTaskControlFiles(control, directory), []);
    control.roleRuns[0].sessionId = "invented-session";
    assert.match((await validateTaskControlFiles(control, directory)).join("\n"), /receipt session mismatch/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("ZCode Hook creates an immutable receipt for each fixed-role dispatch", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-dispatch-receipt-"));
  const taskId = "DISPATCH-AUDIT";
  const dispatchId = "dispatch-1";
  try {
    await mkdir(path.join(directory, ".git"), { recursive: true });
    const script = path.resolve("scripts/zcode-governance-hook.mjs");
    const input = {
      session_id: "parent-session-1", cwd: directory,
      tool_name: "Agent",
      tool_input: {
        subagent_type: "qta-implementer",
        prompt: `# Task Packet: ${taskId} / IMPLEMENTER / impl-1\n- Dispatch ID: ${dispatchId}`
      }
    };
    const hook = spawnSync(process.execPath, [script], {
      input: JSON.stringify(input), encoding: "utf8",
      env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.equal(hook.status, 0, hook.stderr);
    const taskHash = createHash("sha256").update(taskId).digest("hex");
    const dispatchHash = createHash("sha256").update(dispatchId).digest("hex");
    const receipt = JSON.parse(await readFile(path.join(directory, ".git", "qta-governance", "dispatches",
      taskHash, `${dispatchHash}.json`), "utf8"));
    assert.equal(receipt.roleRunId, "impl-1");
    assert.equal(receipt.agentDefinition, ".zcode/agents/qta-implementer.md");

    const duplicate = spawnSync(process.execPath, [script], {
      input: JSON.stringify(input), encoding: "utf8",
      env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.notEqual(duplicate.status, 0);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("ZCode Stop Hook blocks premature qta-run completion and releases BLOCKED tasks", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-stop-gate-"));
  const sessionId = "parent-stop-session";
  const taskId = "STOP-GATE-TASK";
  try {
    await mkdir(path.join(directory, ".git"), { recursive: true });
    await mkdir(path.join(directory, "docs", "development", "tasks"), { recursive: true });
    const script = path.resolve("scripts/zcode-governance-hook.mjs");
    const environment = { ...process.env, ZCODE_PROJECT_DIR: directory };
    const activation = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "UserPromptSubmit", session_id: sessionId, cwd: directory,
        prompt: "/qta-run build a bounded task"
      }), encoding: "utf8", env: environment
    });
    assert.equal(activation.status, 0, activation.stderr);

    const premature = spawnSync(process.execPath, [script], {
      input: JSON.stringify({ hook_event_name: "Stop", session_id: sessionId, cwd: directory }),
      encoding: "utf8", env: environment
    });
    assert.equal(premature.status, 2);
    assert.match(premature.stderr, /bootstrap has not produced/);

    const dispatch = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "PreToolUse", session_id: sessionId, cwd: directory,
        tool_name: "Agent",
        tool_input: {
          subagent_type: "qta-test-designer",
          prompt: `# Task Packet: ${taskId} / TEST_DESIGNER / test-1\n- Dispatch ID: dispatch-test-1`
        }
      }), encoding: "utf8", env: environment
    });
    assert.equal(dispatch.status, 0, dispatch.stderr);
    await writeFile(path.join(directory, "docs", "development", "tasks", `${taskId}-CONTROL.json`),
      JSON.stringify({ taskId, lifecycleState: "BLOCKED" }));
    const blockedTerminal = spawnSync(process.execPath, [script], {
      input: JSON.stringify({ hook_event_name: "Stop", session_id: sessionId, cwd: directory }),
      encoding: "utf8", env: environment
    });
    assert.equal(blockedTerminal.status, 0, blockedTerminal.stderr);
    const activePath = path.join(directory, ".git", "qta-governance", "active",
      `${createHash("sha256").update(sessionId).digest("hex")}.json`);
    await assert.rejects(readFile(activePath), { code: "ENOENT" });
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
