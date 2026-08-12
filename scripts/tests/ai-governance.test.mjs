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
import { dispatchAuditErrors, validateDeliveryReadiness } from "../check-ai-delivery-ready.mjs";
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

test("rejects cross-repository write paths and excessive role dispatches", () => {
  const control = validVerifiedControl();
  control.contract.implementationSlices[0].allowedWritePaths = ["../another-repository/src"];
  control.roleRuns.push(...Array.from({ length: 11 }, (_, index) => {
    const attempt = role("IMPLEMENTER", 1, `extra-${index}`);
    attempt.executionOutcome = "FAILED";
    attempt.status = "BLOCKED";
    attempt.artifactAccepted = false;
    attempt.artifactPath = "";
    attempt.artifactSha256 = "";
    return attempt;
  }));
  const errors = validateTaskControl(control).errors.join("\n");
  assert.match(errors, /must stay inside one repository/);
  assert.match(errors, /role-run budget exceeded/);
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

test("candidate diff generator excludes tracked historical patches and writes only runtime artifacts", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-candidate-diff-"));
  try {
    for (const args of [["init", "-q"], ["config", "user.email", "qta@example.test"],
      ["config", "user.name", "QTA Test"]]) {
      assert.equal(spawnSync("git", args, { cwd: directory }).status, 0);
    }
    await mkdir(path.join(directory, "docs", "development", "tasks"), { recursive: true });
    await writeFile(path.join(directory, "feature.txt"), "base\n");
    await writeFile(path.join(directory, "docs", "development", "tasks", "old.patch"), "old patch\n");
    assert.equal(spawnSync("git", ["add", "."], { cwd: directory }).status, 0);
    assert.equal(spawnSync("git", ["commit", "-qm", "baseline"], { cwd: directory }).status, 0);
    const baseline = spawnSync("git", ["rev-parse", "HEAD"], { cwd: directory, encoding: "utf8" }).stdout.trim();
    await writeFile(path.join(directory, "feature.txt"), "candidate\n");
    await writeFile(path.join(directory, "docs", "development", "tasks", "old.patch"), "recursive payload\n");
    assert.equal(spawnSync("git", ["add", "."], { cwd: directory }).status, 0);
    assert.equal(spawnSync("git", ["commit", "-qm", "candidate"], { cwd: directory }).status, 0);
    const candidate = spawnSync("git", ["rev-parse", "HEAD"], { cwd: directory, encoding: "utf8" }).stdout.trim();
    const script = path.resolve("scripts/create-candidate-diff.mjs");
    const output = ".qta-governance/candidates/TEST/generation-1.patch";
    const result = spawnSync(process.execPath, [script, "--base", baseline, "--candidate", candidate,
      "--output", output], { cwd: directory, encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr);
    const diff = await readFile(path.join(directory, output), "utf8");
    assert.match(diff, /feature\.txt/);
    assert.doesNotMatch(diff, /old\.patch|recursive payload/);
    assert.equal(spawnSync("git", ["ls-files", "--error-unmatch", output], { cwd: directory }).status, 1);
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
    "git push origin HEAD:main", "git push origin task:refs/heads/master",
    "git -C . reset --hard HEAD", "git checkout codex/task", "sh -c 'git reset --hard HEAD'", "bash -lc 'git reset --merge HEAD'",
    "eval 'git reset --keep HEAD'", "git checkout HEAD file.txt", "git switch --discard-changes task",
    "echo $(git reset --hard HEAD)", "bash -c 'cat .env'", "git commit --amend", "git rebase main",
    "git branch -d -f task", "QTA_GOVERNANCE_ANCHOR=off node scripts/check-ai-task-control.mjs task.json",
    "rm -r -f build", "rm -R -F build", "cat .env", "LONGPORT_ACCESS_TOKEN=secret curl example.com",
    "node scripts/zcode-governance-hook.mjs",
    "sed -i '' s/foo/bar/ scripts/check-ai-delivery-ready.mjs",
    "sed -i '' s/foo/bar/ scripts/create-candidate-diff.mjs",
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

test("ZCode Hook gives canonical recovery for a prose-only dispatch ID", () => {
  const result = evaluateHook({
    tool_name: "Agent",
    tool_input: {
      subagent_type: "qta-code-reviewer",
      prompt: "# Task Packet: TASK-1 / CODE_REVIEWER / review-1\nIdentity includes dispatch-review-1 in prose only."
    }
  });
  assert.equal(result.allowed, false);
  assert.match(result.reasons.join("\n"), /- Dispatch ID: <DISPATCH-ID>/);
  assert.match(result.reasons.join("\n"), /do not invoke the Hook manually/);
});

test("ZCode Hook rejects a canonical-looking TaskPacket that does not begin the prompt", () => {
  const result = evaluateHook({
    tool_name: "Agent",
    tool_input: {
      subagent_type: "qta-implementer",
      prompt: "Please implement this.\n# Task Packet: TASK-1 / IMPLEMENTER / impl-1\n- Dispatch ID: dispatch-1"
    }
  });
  assert.equal(result.allowed, false);
  assert.match(result.reasons.join("\n"), /must start with exactly/);
});

test("TaskPacket template exposes the canonical machine prefix as its first two lines", async () => {
  const template = await readFile(
    ".agents/skills/qta-development-orchestration/assets/TASK_PACKET_TEMPLATE.md", "utf8");
  assert.deepEqual(template.split(/\r?\n/).slice(0, 2), [
    "# Task Packet: <TASK-ID> / <ROLE> / <ROLE-RUN-ID>",
    "- Dispatch ID: <DISPATCH-ID>"
  ]);
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

test("ZCode unattended qta-run blocks AskUserQuestion but ordinary sessions remain interactive", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-unattended-question-"));
  const script = path.resolve("scripts/zcode-governance-hook.mjs");
  const environment = { ...process.env, ZCODE_PROJECT_DIR: directory };
  try {
    await mkdir(path.join(directory, ".git"), { recursive: true });
    const ordinary = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "PreToolUse", session_id: "ordinary-session", cwd: directory,
        tool_name: "AskUserQuestion", tool_input: { questions: [{ question: "Choose?" }] }
      }), encoding: "utf8", env: environment
    });
    assert.equal(ordinary.status, 0, ordinary.stderr);

    const sessionId = "unattended-parent";
    const activation = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "UserPromptSubmit", session_id: sessionId, cwd: directory,
        prompt: "/qta-run finish one bounded task"
      }), encoding: "utf8", env: environment
    });
    assert.equal(activation.status, 0, activation.stderr);
    const blocked = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "PreToolUse", session_id: sessionId, cwd: directory,
        tool_name: "AskUserQuestion", tool_input: { questions: [{ question: "Choose recommended?" }] }
      }), encoding: "utf8", env: environment
    });
    assert.equal(blocked.status, 2);
    assert.match(blocked.stderr, /blocked AskUserQuestion/);
    assert.match(blocked.stderr, /persist.*BLOCKED/i);

    const childBlocked = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "PreToolUse", session_id: "unattended-child", cwd: directory,
        tool_name: "AskUserQuestion", tool_input: { questions: [{ question: "Choose for child?" }] }
      }), encoding: "utf8", env: environment
    });
    assert.equal(childBlocked.status, 2);
    assert.match(childBlocked.stderr, /blocked AskUserQuestion/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("expanded qta-run sentinel activates governance and records real doctor event sequence", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-expanded-run-"));
  const script = path.resolve("scripts/zcode-governance-hook.mjs");
  const sessionId = "expanded-run-session";
  const environment = { ...process.env, ZCODE_PROJECT_DIR: directory };
  try {
    await mkdir(path.join(directory, ".git"), { recursive: true });
    const activation = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "UserPromptSubmit", session_id: sessionId, cwd: directory,
        prompt: "QTA_GOVERNED_RUN\n\nInvocation arguments:\nfinish one bounded task"
      }), encoding: "utf8", env: environment
    });
    assert.equal(activation.status, 0, activation.stderr);
    const preTool = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "PreToolUse", session_id: sessionId, cwd: directory,
        tool_name: "Bash", tool_input: { command: "git status" }
      }), encoding: "utf8", env: environment
    });
    assert.equal(preTool.status, 0, preTool.stderr);

    const sessionHash = createHash("sha256").update(sessionId).digest("hex");
    const active = JSON.parse(await readFile(
      path.join(directory, ".git", "qta-governance", "active", `${sessionHash}.json`), "utf8"));
    const doctor = JSON.parse(await readFile(
      path.join(directory, ".git", "qta-governance", "doctor", `${sessionHash}.json`), "utf8"));
    assert.equal(active.parentSessionId, sessionId);
    assert.equal(doctor.promptKind, "QTA_RUN");
    assert.ok(doctor.promptObservedAt);
    assert.ok(doctor.preToolObservedAt);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("user-level ZCode Hook installer preserves config and never registers Stop", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-user-hook-install-"));
  const installer = path.resolve("scripts/install-zcode-governance-user-hooks.mjs");
  const environment = { ...process.env, ZCODE_USER_CONFIG_DIR: directory };
  try {
    await writeFile(path.join(directory, "config.json"), `${JSON.stringify({
      theme: "existing",
      hooks: { events: { SessionStart: [{ matcher: "startup", hooks: [] }] } }
    }, null, 2)}\n`);
    const installed = spawnSync(process.execPath, [installer, "--install"], {
      encoding: "utf8", env: environment
    });
    assert.equal(installed.status, 0, installed.stderr);
    const config = JSON.parse(await readFile(path.join(directory, "config.json"), "utf8"));
    assert.equal(config.theme, "existing");
    assert.equal(config.hooks.enabled, true);
    assert.equal(config.hooks.events.SessionStart.length, 1);
    assert.equal(config.hooks.events.Stop, undefined);
    for (const event of ["UserPromptSubmit", "PreToolUse", "PostToolUse", "PostToolUseFailure"]) {
      assert.equal(config.hooks.events[event].length, 1);
    }

    const checked = spawnSync(process.execPath, [installer, "--check"], {
      encoding: "utf8", env: environment
    });
    assert.equal(checked.status, 0, checked.stderr);
    const reinstalled = spawnSync(process.execPath, [installer, "--install"], {
      encoding: "utf8", env: environment
    });
    assert.equal(reinstalled.status, 0, reinstalled.stderr);
    const idempotent = JSON.parse(await readFile(path.join(directory, "config.json"), "utf8"));
    assert.equal(idempotent.hooks.events.PreToolUse.length, 1);

    const removed = spawnSync(process.execPath, [installer, "--uninstall"], {
      encoding: "utf8", env: environment
    });
    assert.equal(removed.status, 0, removed.stderr);
    const afterRemoval = JSON.parse(await readFile(path.join(directory, "config.json"), "utf8"));
    assert.equal(afterRemoval.theme, "existing");
    assert.equal(afterRemoval.hooks.events.SessionStart.length, 1);
    assert.equal(afterRemoval.hooks.events.PreToolUse, undefined);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("user-level dispatcher routes only repositories that contain the QTA project Hook", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-user-hook-dispatch-"));
  const dispatcher = path.resolve("scripts/zcode-governance-user-dispatcher.mjs");
  try {
    await mkdir(path.join(directory, ".git"), { recursive: true });
    await mkdir(path.join(directory, "scripts"), { recursive: true });
    const absent = spawnSync(process.execPath, [dispatcher], {
      input: JSON.stringify({ cwd: directory, tool_name: "Bash", tool_input: { command: "git reset --hard" } }),
      encoding: "utf8", env: { ...process.env, QTA_GOVERNANCE_AUDIT: "off" }
    });
    assert.equal(absent.status, 0, absent.stderr);

    await writeFile(path.join(directory, "scripts", "zcode-governance-hook.mjs"),
      await readFile("scripts/zcode-governance-hook.mjs", "utf8"));
    const blocked = spawnSync(process.execPath, [dispatcher], {
      input: JSON.stringify({ cwd: directory, tool_name: "Bash", tool_input: { command: "git reset --hard" } }),
      encoding: "utf8", env: { ...process.env, QTA_GOVERNANCE_AUDIT: "off" }
    });
    assert.equal(blocked.status, 2);
    assert.match(blocked.stderr, /QTA governance blocked this action/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
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
    assert.equal(spawnSync("git", ["init", "-b", "main"], { cwd: directory }).status, 0);
    const script = path.resolve("scripts/zcode-governance-hook.mjs");
    const input = {
      hook_event_name: "PreToolUse", session_id: "parent-session-1", tool_use_id: "tool-agent-1", cwd: directory,
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
    assert.equal(receipt.status, "PENDING");
    assert.match(dispatchAuditErrors({ taskId, roleRuns: [{ dispatchId }] }, directory).join("\n"),
      /remains PENDING/);

    const mismatched = spawnSync(process.execPath, [script], {
      input: JSON.stringify({ ...input, hook_event_name: "PostToolUse", tool_use_id: "another-tool" }),
      encoding: "utf8", env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.notEqual(mismatched.status, 0);
    assert.match(mismatched.stderr, /does not match its pending receipt/);

    const completed = spawnSync(process.execPath, [script], {
      input: JSON.stringify({ ...input, hook_event_name: "PostToolUse" }), encoding: "utf8",
      env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.equal(completed.status, 0, completed.stderr);
    const outcome = JSON.parse(await readFile(path.join(directory, ".git", "qta-governance", "dispatches",
      taskHash, `${dispatchHash}.outcome.json`), "utf8"));
    assert.equal(outcome.status, "SUCCEEDED");
    assert.deepEqual(dispatchAuditErrors({ taskId, roleRuns: [{ dispatchId }] }, directory), []);

    const duplicateOutcome = spawnSync(process.execPath, [script], {
      input: JSON.stringify({ ...input, hook_event_name: "PostToolUse" }), encoding: "utf8",
      env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.equal(duplicateOutcome.status, 0, duplicateOutcome.stderr);

    await writeFile(path.join(directory, ".git", "qta-governance", "dispatches", taskHash,
      `${createHash("sha256").update("diagnostic-only").digest("hex")}.json`), JSON.stringify({
      version: 2, taskId, dispatchId: "diagnostic-only", roleRunId: "diagnostic-only"
    }));
    assert.deepEqual(dispatchAuditErrors({ taskId, roleRuns: [{ dispatchId }] }, directory), []);

    await writeFile(path.join(directory, ".git", "qta-governance", "dispatches",
      taskHash, `${dispatchHash}.outcome.json`), JSON.stringify({ ...outcome, toolUseId: "tampered-tool" }));
    assert.match(dispatchAuditErrors({ taskId, roleRuns: [{ dispatchId }] }, directory).join("\n"),
      /dispatch outcome does not match pending receipt/);
    await writeFile(path.join(directory, ".git", "qta-governance", "dispatches",
      taskHash, `${dispatchHash}.outcome.json`), JSON.stringify(outcome));

    const duplicate = spawnSync(process.execPath, [script], {
      input: JSON.stringify(input), encoding: "utf8",
      env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.notEqual(duplicate.status, 0);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("governed dispatches enforce lifecycle role order and one pending role at a time", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-role-order-"));
  const sessionId = "ordered-parent";
  const taskId = "ORDERED-TASK";
  try {
    assert.equal(spawnSync("git", ["init", "-q"], { cwd: directory }).status, 0);
    await mkdir(path.join(directory, "docs", "development", "tasks"), { recursive: true });
    const script = path.resolve("scripts/zcode-governance-hook.mjs");
    const environment = { ...process.env, ZCODE_PROJECT_DIR: directory };
    assert.equal(spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "UserPromptSubmit", session_id: sessionId, cwd: directory,
        prompt: "/qta-run ordered task"
      }), encoding: "utf8", env: environment
    }).status, 0);
    await writeFile(path.join(directory, "docs", "development", "tasks", `${taskId}-CONTROL.json`),
      JSON.stringify({ taskId, lane: "L1", lifecycleState: "CONTRACT_FROZEN", roleRuns: [] }));
    const implementerInput = {
      hook_event_name: "PreToolUse", session_id: sessionId, cwd: directory, tool_name: "Agent",
      tool_input: {
        subagent_type: "qta-implementer",
        prompt: `# Task Packet: ${taskId} / IMPLEMENTER / impl-1\n- Dispatch ID: dispatch-impl-1`
      }
    };
    const first = spawnSync(process.execPath, [script], {
      input: JSON.stringify(implementerInput), encoding: "utf8", env: environment
    });
    assert.equal(first.status, 0, first.stderr);
    const parallel = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        ...implementerInput,
        tool_input: {
          subagent_type: "qta-implementer",
          prompt: `# Task Packet: ${taskId} / IMPLEMENTER / impl-2\n- Dispatch ID: dispatch-impl-2`
        }
      }), encoding: "utf8", env: environment
    });
    assert.notEqual(parallel.status, 0);
    assert.match(parallel.stderr, /still PENDING/);
    await writeFile(path.join(directory, "docs", "development", "tasks", `${taskId}-CONTROL.json`),
      JSON.stringify({ taskId, lane: "L1", lifecycleState: "CONTRACT_DRAFTED", roleRuns: [] }));
    const wrongRole = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        ...implementerInput,
        tool_input: {
          subagent_type: "qta-code-reviewer",
          prompt: `# Task Packet: ${taskId} / CODE_REVIEWER / review-1\n- Dispatch ID: dispatch-review-1`
        }
      }), encoding: "utf8", env: environment
    });
    assert.notEqual(wrongRole.status, 0);
    assert.match(wrongRole.stderr, /not allowed/);
    await writeFile(path.join(directory, "docs", "development", "tasks", `${taskId}-CONTROL.json`),
      JSON.stringify({
        taskId, lane: "L1", lifecycleState: "IMPLEMENTING",
        roleRuns: Array.from({ length: 10 }, (_, index) => ({ roleRunId: `spent-${index}` }))
      }));
    const exhausted = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        ...implementerInput,
        tool_input: {
          subagent_type: "qta-implementer",
          prompt: `# Task Packet: ${taskId} / IMPLEMENTER / impl-11\n- Dispatch ID: dispatch-impl-11`
        }
      }), encoding: "utf8", env: environment
    });
    assert.notEqual(exhausted.status, 0);
    assert.match(exhausted.stderr, /role-run budget is exhausted/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("a conflicting parent task is rejected before any dispatch receipt is written", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-dispatch-conflict-"));
  const sessionId = "conflicting-parent";
  const attemptedTask = "NEW-TASK";
  try {
    await mkdir(path.join(directory, ".git", "qta-governance", "active"), { recursive: true });
    await writeFile(path.join(directory, ".git", "qta-governance", "active",
      `${createHash("sha256").update(sessionId).digest("hex")}.json`), JSON.stringify({
      taskId: "EXISTING-TASK",
      parentSessionId: sessionId,
      projectRootSha256: createHash("sha256").update(path.resolve(directory)).digest("hex")
    }));
    const script = path.resolve("scripts/zcode-governance-hook.mjs");
    const result = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "PreToolUse", session_id: sessionId, cwd: directory,
        tool_name: "Agent", tool_input: {
          subagent_type: "qta-implementer",
          prompt: `# Task Packet: ${attemptedTask} / IMPLEMENTER / impl-conflict\n- Dispatch ID: dispatch-conflict`
        }
      }),
      encoding: "utf8", env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /already owns active governed task EXISTING-TASK/);
    const receiptPath = path.join(directory, ".git", "qta-governance", "dispatches",
      createHash("sha256").update(attemptedTask).digest("hex"),
      `${createHash("sha256").update("dispatch-conflict").digest("hex")}.json`);
    await assert.rejects(readFile(receiptPath), { code: "ENOENT" });
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("ZCode Hook records a failed Agent outcome without pretending success", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-dispatch-failure-"));
  const taskId = "DISPATCH-FAILURE";
  const dispatchId = "dispatch-failed-1";
  const toolInput = {
    subagent_type: "qta-code-reviewer",
    prompt: `# Task Packet: ${taskId} / CODE_REVIEWER / review-failed-1\n- Dispatch ID: ${dispatchId}`
  };
  try {
    await mkdir(path.join(directory, ".git"), { recursive: true });
    const script = path.resolve("scripts/zcode-governance-hook.mjs");
    const environment = { ...process.env, ZCODE_PROJECT_DIR: directory };
    for (const event of ["PreToolUse", "PostToolUseFailure"]) {
      const result = spawnSync(process.execPath, [script], {
        input: JSON.stringify({
          hook_event_name: event, session_id: "parent-failed-session", cwd: directory,
          tool_name: "Agent", tool_input: toolInput
        }), encoding: "utf8", env: environment
      });
      assert.equal(result.status, 0, result.stderr);
    }
    const taskHash = createHash("sha256").update(taskId).digest("hex");
    const dispatchHash = createHash("sha256").update(dispatchId).digest("hex");
    const outcome = JSON.parse(await readFile(path.join(directory, ".git", "qta-governance", "dispatches",
      taskHash, `${dispatchHash}.outcome.json`), "utf8"));
    assert.equal(outcome.status, "FAILED");
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("ZCode Hook ignores a failure event when PreToolUse never created a receipt", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-dispatch-no-receipt-"));
  const taskId = "DISPATCH-NO-RECEIPT";
  try {
    await mkdir(path.join(directory, ".git"), { recursive: true });
    const result = spawnSync(process.execPath, [path.resolve("scripts/zcode-governance-hook.mjs")], {
      input: JSON.stringify({
        hook_event_name: "PostToolUseFailure", session_id: "parent-no-receipt", cwd: directory,
        tool_name: "Agent",
        tool_input: {
          subagent_type: "qta-implementer",
          prompt: `# Task Packet: ${taskId} / IMPLEMENTER / impl-no-receipt\n- Dispatch ID: dispatch-no-receipt`
        }
      }), encoding: "utf8", env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.equal(result.status, 0, result.stderr);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("ZCode Hook fails closed for a successful Agent without a PreToolUse receipt", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-dispatch-success-without-receipt-"));
  const taskId = "DISPATCH-SUCCESS-NO-RECEIPT";
  try {
    await mkdir(path.join(directory, ".git"), { recursive: true });
    const result = spawnSync(process.execPath, [path.resolve("scripts/zcode-governance-hook.mjs")], {
      input: JSON.stringify({
        hook_event_name: "PostToolUse", session_id: "parent-success-no-receipt", cwd: directory,
        tool_name: "Agent",
        tool_input: {
          subagent_type: "qta-implementer",
          prompt: `# Task Packet: ${taskId} / IMPLEMENTER / impl-success-no-receipt\n- Dispatch ID: dispatch-success-no-receipt`
        }
      }), encoding: "utf8", env: { ...process.env, ZCODE_PROJECT_DIR: directory }
    });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /Hook failed closed|ENOENT/i);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("governed sessions block default-branch writes and allow task-branch writes", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-protected-branch-"));
  const script = path.resolve("scripts/zcode-governance-hook.mjs");
  const environment = { ...process.env, ZCODE_PROJECT_DIR: directory };
  try {
    assert.equal(spawnSync("git", ["init", "-b", "main"], { cwd: directory }).status, 0);
    await writeFile(path.join(directory, "README.md"), "baseline\n");
    spawnSync("git", ["config", "user.name", "QTA Test"], { cwd: directory });
    spawnSync("git", ["config", "user.email", "qta@example.invalid"], { cwd: directory });
    spawnSync("git", ["add", "README.md"], { cwd: directory });
    assert.equal(spawnSync("git", ["commit", "-m", "baseline"], { cwd: directory }).status, 0);
    const sessionId = "protected-main-session";
    const activation = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "UserPromptSubmit", session_id: sessionId, cwd: directory,
        prompt: "/qta-run protected branch fixture"
      }), encoding: "utf8", env: environment
    });
    assert.equal(activation.status, 0, activation.stderr);
    for (const toolCall of [
      { tool_name: "Write", tool_input: { file_path: path.join(directory, "blocked.txt") } },
      { tool_name: "Bash", tool_input: { command: "git commit -m blocked" } },
      { tool_name: "Bash", tool_input: { command: "git reset --soft HEAD" } },
      { tool_name: "Bash", tool_input: { command: "git update-ref refs/heads/main HEAD" } },
      { tool_name: "Bash", tool_input: { command: "git notes add -m note HEAD" } },
      { tool_name: "Bash", tool_input: { command: "git stash push" } }
    ]) {
      const blocked = spawnSync(process.execPath, [script], {
        input: JSON.stringify({ hook_event_name: "PreToolUse", session_id: sessionId, cwd: directory, ...toolCall }),
        encoding: "utf8", env: environment
      });
      assert.equal(blocked.status, 2);
      assert.match(blocked.stderr, /protected default branch/);
    }
    const pushBlocked = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "PreToolUse", session_id: sessionId, cwd: directory,
        tool_name: "Bash", tool_input: { command: "git push origin HEAD:main" }
      }), encoding: "utf8", env: environment
    });
    assert.equal(pushBlocked.status, 2);
    assert.match(pushBlocked.stderr, /protected default branch|direct default-branch push/);
    const switchAllowed = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "PreToolUse", session_id: sessionId, cwd: directory,
        tool_name: "Bash", tool_input: { command: "git switch -c codex/protected-fixture" }
      }), encoding: "utf8", env: environment
    });
    assert.equal(switchAllowed.status, 0, switchAllowed.stderr);
    assert.equal(spawnSync("git", ["switch", "-c", "codex/protected-fixture"], { cwd: directory }).status, 0);
    const allowed = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "PreToolUse", session_id: sessionId, cwd: directory,
        tool_name: "Write", tool_input: { file_path: path.join(directory, "allowed.txt") }
      }), encoding: "utf8", env: environment
    });
    assert.equal(allowed.status, 0, allowed.stderr);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("qta-run activation reconciles terminal locks but preserves non-terminal locks", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-terminal-locks-"));
  const script = path.resolve("scripts/zcode-governance-hook.mjs");
  const environment = { ...process.env, ZCODE_PROJECT_DIR: directory };
  const tasks = path.join(directory, "docs", "development", "tasks");
  const active = path.join(directory, ".git", "qta-governance", "active");
  try {
    await mkdir(tasks, { recursive: true });
    await mkdir(active, { recursive: true });
    const projectHash = createHash("sha256").update(path.resolve(directory)).digest("hex");
    for (const fixture of [
      { session: "old-blocked", task: "OLD-BLOCKED", state: "BLOCKED" },
      { session: "old-running", task: "OLD-RUNNING", state: "IMPLEMENTING" }
    ]) {
      const controlPath = `docs/development/tasks/${fixture.task}-CONTROL.json`;
      await writeFile(path.join(directory, controlPath), JSON.stringify({
        taskId: fixture.task, lifecycleState: fixture.state
      }));
      await writeFile(path.join(active,
        `${createHash("sha256").update(fixture.session).digest("hex")}.json`), JSON.stringify({
        taskId: fixture.task, controlPath, parentSessionId: fixture.session,
        projectRootSha256: projectHash, updatedAt: new Date().toISOString()
      }));
    }
    const activation = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "UserPromptSubmit", session_id: "new-session", cwd: directory,
        prompt: "/qta-run new fixture"
      }), encoding: "utf8", env: environment
    });
    assert.notEqual(activation.status, 0);
    assert.match(activation.stderr, /project already has active governed task OLD-RUNNING/);
    await assert.rejects(readFile(path.join(active,
      `${createHash("sha256").update("old-blocked").digest("hex")}.json`)), { code: "ENOENT" });
    assert.equal(JSON.parse(await readFile(path.join(active,
      `${createHash("sha256").update("old-running").digest("hex")}.json`), "utf8")).taskId, "OLD-RUNNING");
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("qta-run explicitly transfers a matching non-terminal task to a replacement session", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "qta-resume-lock-"));
  const script = path.resolve("scripts/zcode-governance-hook.mjs");
  const environment = { ...process.env, ZCODE_PROJECT_DIR: directory };
  const taskId = "RESUME-TASK";
  const oldSession = "unavailable-parent";
  const newSession = "replacement-parent";
  const tasks = path.join(directory, "docs", "development", "tasks");
  const active = path.join(directory, ".git", "qta-governance", "active");
  try {
    await mkdir(tasks, { recursive: true });
    await mkdir(active, { recursive: true });
    const controlPath = `docs/development/tasks/${taskId}-CONTROL.json`;
    await writeFile(path.join(directory, controlPath), JSON.stringify({
      taskId, lifecycleState: "IMPLEMENTING"
    }));
    await writeFile(path.join(active,
      `${createHash("sha256").update(oldSession).digest("hex")}.json`), JSON.stringify({
      taskId, controlPath, parentSessionId: oldSession,
      projectRootSha256: createHash("sha256").update(path.resolve(directory)).digest("hex"),
      lastDispatchId: "dispatch-before-resume"
    }));
    const resumed = spawnSync(process.execPath, [script], {
      input: JSON.stringify({
        hook_event_name: "UserPromptSubmit", session_id: newSession, cwd: directory,
        prompt: `/qta-run --resume ${taskId} ${controlPath}`
      }), encoding: "utf8", env: environment
    });
    assert.equal(resumed.status, 0, resumed.stderr);
    await assert.rejects(readFile(path.join(active,
      `${createHash("sha256").update(oldSession).digest("hex")}.json`)), { code: "ENOENT" });
    const replacement = JSON.parse(await readFile(path.join(active,
      `${createHash("sha256").update(newSession).digest("hex")}.json`), "utf8"));
    assert.equal(replacement.taskId, taskId);
    assert.equal(replacement.parentSessionId, newSession);
    assert.equal(replacement.lastDispatchId, "dispatch-before-resume");
    assert.equal(replacement.resumedFromSessionSha256,
      createHash("sha256").update(oldSession).digest("hex"));
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("ZCode has no Stop continuation hook", async () => {
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

    const stop = spawnSync(process.execPath, [script], {
      input: JSON.stringify({ hook_event_name: "Stop", session_id: sessionId, cwd: directory }),
      encoding: "utf8", env: environment
    });
    assert.equal(stop.status, 0, stop.stderr);
    const activePath = path.join(directory, ".git", "qta-governance", "active",
      `${createHash("sha256").update(sessionId).digest("hex")}.json`);
    assert.equal(JSON.parse(await readFile(activePath, "utf8")).parentSessionId, sessionId);
    const installer = await readFile("scripts/install-zcode-governance-user-hooks.mjs", "utf8");
    assert.doesNotMatch(installer, /\bStop\s*:/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

function methodBodyFixture(lineCount) {
  const lines = ["export function Comp() {"];
  for (let index = 0; index < lineCount; index += 1) lines.push(`  const a${index} = 0;`);
  lines.push("}");
  return `${lines.join("\n")}\n`;
}

function securitySelectorIntegrationFixture(baselineBodyLines) {
  const lines = ["export function Comp() {"];
  for (let index = 0; index < baselineBodyLines; index += 1) lines.push(`  const a${index} = 0;`);
  lines.push("  const permissions = useSecuritySelector((state) => state.permissions);");
  lines.push("  const identity = useSecuritySelector((state) => state.identity);");
  lines.push("  const profile = useSecuritySelector((state) => state.profile);");
  lines.push("  const entitlements = useSecuritySelector((state) => state.entitlements);");
  lines.push("  const session = useSecuritySelector((state) => state.session);");
  lines.push("  return null;");
  lines.push("}");
  return `${lines.join("\n")}\n`;
}

async function runBaselineAwareGate({ candidateDir, baselineDir, candidateFile, reportPath,
  candidateRoot, baselineCommit, allowedWorsenDelta, cwd }) {
  const script = path.resolve("scripts/check-ai-architecture.mjs");
  const baselineAbs = path.resolve(baselineDir);
  const candidateAbs = path.resolve(candidateDir, candidateFile);
  const args = [script,
    "--files", candidateAbs,
    "--baseline", baselineAbs,
    "--candidate-identity", "candidate-baseline-aware",
    "--json-output", reportPath];
  if (candidateRoot) args.push("--candidate-root", candidateRoot);
  if (baselineCommit) args.push("--baseline-commit", baselineCommit);
  if (allowedWorsenDelta !== undefined) args.push("--allowed-worsen-delta", String(allowedWorsenDelta));
  return spawnSync(process.execPath, args, { cwd: cwd ?? candidateDir, encoding: "utf8" });
}

test("baseline-aware gate does not block unchanged pre-existing React method debt", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-preexisting-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-preexisting-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    const baselineBodyLines = 148;
    await mkdir(path.join(baselineDir, path.dirname(relativeFile)), { recursive: true });
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await writeFile(path.join(baselineDir, relativeFile), methodBodyFixture(baselineBodyLines));
    await writeFile(path.join(candidateDir, relativeFile),
      securitySelectorIntegrationFixture(baselineBodyLines));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const result = await runBaselineAwareGate({
      candidateDir, baselineDir, candidateFile: relativeFile, reportPath,
      allowedWorsenDelta: 20
    });
    assert.equal(result.status, 0, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "PASS");
    assert.equal(report.exitCode, 0);
    assert.equal(report.blockingErrorCount, 0);
    assert.equal(report.errors.length, 0);
    assert.equal(report.baselineIdentity, path.resolve(baselineDir));
    const longestMethodErrors = [
      ...report.introducedErrors, ...report.worsenedErrors, ...report.preExistingErrors
    ].filter((entry) => entry.message.includes("longest method"));
    assert.ok(longestMethodErrors.length >= 1);
    assert.deepEqual(report.introducedErrors, []);
    assert.deepEqual(report.worsenedErrors, []);
    for (const entry of report.preExistingErrors) {
      assert.equal(entry.classification, "pre-existing");
      assert.equal(typeof entry.candidateMetric, "number");
      assert.equal(typeof entry.baselineMetric, "number");
      assert.equal(typeof entry.delta, "number");
    }
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});

test("baseline-aware gate blocks a newly introduced over-threshold method", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-introduced-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-introduced-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    await mkdir(path.join(baselineDir, path.dirname(relativeFile)), { recursive: true });
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await writeFile(path.join(baselineDir, relativeFile), methodBodyFixture(93));
    await writeFile(path.join(candidateDir, relativeFile), methodBodyFixture(118));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const result = await runBaselineAwareGate({
      candidateDir, baselineDir, candidateFile: relativeFile, reportPath
    });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "FAIL");
    assert.equal(report.exitCode, 1);
    assert.ok(report.blockingErrorCount >= 1);
    assert.equal(report.errors.length, report.blockingErrorCount);
    const introduced = report.introducedErrors.filter((entry) => entry.message.includes("longest method"));
    assert.ok(introduced.length >= 1);
    for (const entry of introduced) {
      assert.equal(entry.classification, "introduced");
      assert.equal(entry.baselineMetric, null);
      assert.equal(entry.delta, null);
      assert.equal(typeof entry.candidateMetric, "number");
    }
    assert.ok(report.errors.some((entry) => introduced.some((match) => match.id === entry.id)));
    assert.deepEqual(report.worsenedErrors, []);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});

test("baseline-aware gate blocks a worsened pre-existing method beyond the allowed delta", async () => {
  const worsenedCandidate = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-worsened-cand-"));
  const worsenedBaseline = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-worsened-base-"));
  const boundaryCandidate = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-boundary-cand-"));
  const boundaryBaseline = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-boundary-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    for (const directory of [worsenedCandidate, worsenedBaseline, boundaryCandidate, boundaryBaseline]) {
      await mkdir(path.join(directory, path.dirname(relativeFile)), { recursive: true });
    }
    await writeFile(path.join(worsenedBaseline, relativeFile), methodBodyFixture(103));
    await writeFile(path.join(worsenedCandidate, relativeFile), methodBodyFixture(128));
    await writeFile(path.join(boundaryBaseline, relativeFile), methodBodyFixture(108));
    await writeFile(path.join(boundaryCandidate, relativeFile), methodBodyFixture(128));

    const worsenedReportPath = path.join(worsenedCandidate, "architecture-report.json");
    const worsened = await runBaselineAwareGate({
      candidateDir: worsenedCandidate, baselineDir: worsenedBaseline,
      candidateFile: relativeFile, reportPath: worsenedReportPath,
      allowedWorsenDelta: 20
    });
    assert.equal(worsened.status, 1, worsened.stdout);
    const worsenedReport = JSON.parse(await readFile(worsenedReportPath, "utf8"));
    assert.equal(worsenedReport.allowedWorsenDelta, 20);
    assert.equal(worsenedReport.status, "FAIL");
    assert.equal(worsenedReport.exitCode, 1);
    assert.ok(worsenedReport.blockingErrorCount >= 1);
    const worsenedEntries = worsenedReport.worsenedErrors
      .filter((entry) => entry.message.includes("longest method"));
    assert.ok(worsenedEntries.length >= 1);
    for (const entry of worsenedEntries) {
      assert.equal(entry.classification, "worsened");
      assert.equal(entry.delta, 25);
      assert.equal(entry.candidateMetric, 130);
      assert.equal(entry.baselineMetric, 105);
    }
    assert.ok(worsenedReport.errors.some((entry) => worsenedEntries.some((match) => match.id === entry.id)));

    const boundaryReportPath = path.join(boundaryCandidate, "architecture-report.json");
    const boundary = await runBaselineAwareGate({
      candidateDir: boundaryCandidate, baselineDir: boundaryBaseline,
      candidateFile: relativeFile, reportPath: boundaryReportPath,
      allowedWorsenDelta: 20
    });
    assert.equal(boundary.status, 0, boundary.stdout);
    const boundaryReport = JSON.parse(await readFile(boundaryReportPath, "utf8"));
    assert.equal(boundaryReport.allowedWorsenDelta, 20);
    assert.equal(boundaryReport.status, "PASS");
    assert.equal(boundaryReport.exitCode, 0);
    assert.equal(boundaryReport.blockingErrorCount, 0);
    assert.deepEqual(boundaryReport.introducedErrors, []);
    assert.deepEqual(boundaryReport.worsenedErrors, []);
    const boundaryPreExisting = boundaryReport.preExistingErrors
      .filter((entry) => entry.message.includes("longest method"));
    assert.ok(boundaryPreExisting.length >= 1);
    for (const entry of boundaryPreExisting) {
      assert.equal(entry.classification, "pre-existing");
      assert.equal(entry.delta, 20);
      assert.equal(entry.candidateMetric, 130);
      assert.equal(entry.baselineMetric, 110);
    }
  } finally {
    await rm(worsenedCandidate, { recursive: true, force: true });
    await rm(worsenedBaseline, { recursive: true, force: true });
    await rm(boundaryCandidate, { recursive: true, force: true });
    await rm(boundaryBaseline, { recursive: true, force: true });
  }
});

function javaMethodBodyFixture(lineCount) {
  const lines = ["class LongMethod {", "  public void calculate(int input) {"];
  for (let index = 0; index < lineCount; index += 1) lines.push(`    int value${index} = ${index};`);
  lines.push("    return;");
  lines.push("  }");
  lines.push("}");
  return `${lines.join("\n")}\n`;
}

test("baseline-aware gate still blocks over-long Java methods", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-java-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-java-base-"));
  try {
    const relativeFile = "src/main/java/demo/LongMethod.java";
    await mkdir(path.join(baselineDir, path.dirname(relativeFile)), { recursive: true });
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await writeFile(path.join(baselineDir, relativeFile), javaMethodBodyFixture(95));
    await writeFile(path.join(candidateDir, relativeFile), javaMethodBodyFixture(120));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const result = await runBaselineAwareGate({
      candidateDir, baselineDir, candidateFile: relativeFile, reportPath
    });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "FAIL");
    assert.ok(report.blockingErrorCount >= 1);
    const introduced = report.introducedErrors
      .filter((entry) => entry.message.includes("longest method"));
    assert.ok(introduced.length >= 1);
    for (const entry of introduced) {
      assert.equal(entry.classification, "introduced");
      assert.equal(entry.baselineMetric, null);
      assert.equal(entry.delta, null);
      assert.equal(typeof entry.candidateMetric, "number");
      assert.ok(entry.candidateMetric > 100);
    }
    assert.ok(report.errors.some((entry) => introduced.some((match) => match.id === entry.id)));
    assert.deepEqual(report.worsenedErrors, []);
    assert.equal(report.candidateIdentity, "candidate-baseline-aware");
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});

test("architecture gate keeps strict behavior when no baseline is supplied", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-strict-no-baseline-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await writeFile(path.join(candidateDir, relativeFile), methodBodyFixture(118));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const script = path.resolve("scripts/check-ai-architecture.mjs");
    const result = spawnSync(process.execPath, [script,
      "--files", path.resolve(candidateDir, relativeFile),
      "--candidate-identity", "strict-candidate",
      "--json-output", reportPath
    ], { cwd: candidateDir, encoding: "utf8" });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "FAIL");
    assert.equal(report.errors.length, report.errorCount);
    assert.ok(report.errors.length >= 1);
    assert.ok(report.errors.some((entry) => entry.message.includes("longest method")));
    assert.equal(report.baselineIdentity, undefined);
    assert.equal(report.allowedWorsenDelta, undefined);
    assert.equal(report.blockingErrorCount, undefined);
    assert.equal(report.introducedErrors, undefined);
    assert.equal(report.worsenedErrors, undefined);
    assert.equal(report.preExistingErrors, undefined);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
  }
});

test("architecture report binds candidate and baseline identity and the full governance suite passes", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-identity-bind-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-identity-bind-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    await mkdir(path.join(baselineDir, path.dirname(relativeFile)), { recursive: true });
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await writeFile(path.join(baselineDir, relativeFile), methodBodyFixture(148));
    await writeFile(path.join(candidateDir, relativeFile),
      securitySelectorIntegrationFixture(148));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const script = path.resolve("scripts/check-ai-architecture.mjs");
    const baselineAbs = path.resolve(baselineDir);
    const candidateAbs = path.resolve(candidateDir, relativeFile);
    const result = spawnSync(process.execPath, [script,
      "--files", candidateAbs,
      "--baseline", baselineAbs,
      "--candidate-identity", "cand-X",
      "--allowed-worsen-delta", "20",
      "--json-output", reportPath
    ], { cwd: candidateDir, encoding: "utf8" });
    assert.equal(result.status, 0, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.candidateIdentity, "cand-X");
    assert.equal(report.baselineIdentity, baselineAbs);
    assert.equal(report.errorCount, 0);
    assert.equal(report.status, "PASS");
    assert.equal(report.exitCode, 0);

    const suiteScript = path.resolve("scripts/run-ai-governance-gates.mjs");
    const suite = spawnSync(process.execPath, [suiteScript],
      { cwd: path.resolve(import.meta.dirname, ".."), encoding: "utf8" });
    assert.equal(suite.status, 0, suite.stderr);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});

test("baseline-aware gate treats a missing baseline file as introduced not silently dropped", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-missing-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-missing-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await mkdir(path.join(baselineDir, "src", "unrelated"), { recursive: true });
    await writeFile(path.join(candidateDir, relativeFile), methodBodyFixture(118));
    await writeFile(path.join(baselineDir, "src/unrelated/Other.tsx"), methodBodyFixture(10));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const result = await runBaselineAwareGate({
      candidateDir, baselineDir, candidateFile: relativeFile, reportPath
    });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "FAIL");
    assert.ok(report.blockingErrorCount >= 1);
    const introduced = report.introducedErrors
      .filter((entry) => entry.message.includes("longest method"));
    assert.ok(introduced.length >= 1);
    for (const entry of introduced) {
      assert.equal(entry.classification, "introduced");
      assert.equal(entry.baselineMetric, null);
      assert.equal(entry.delta, null);
      assert.equal(typeof entry.candidateMetric, "number");
    }
    assert.ok(report.errors.some((entry) => introduced.some((match) => match.id === entry.id)));
    assert.deepEqual(report.worsenedErrors, []);
    assert.deepEqual(report.preExistingErrors, []);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});

function namedMethodFixture(name, lineCount) {
  const lines = [`export function ${name}() {`];
  for (let index = 0; index < lineCount; index += 1) lines.push(`  const a${index} = 0;`);
  lines.push("}");
  return `${lines.join("\n")}\n`;
}

function buildBaselineContentHash(entries) {
  const sorted = entries.slice().sort((a, b) => (a.file < b.file ? -1 : a.file > b.file ? 1 : 0));
  return createHash("sha256").update(JSON.stringify(sorted)).digest("hex");
}

test("candidate-root makes baseline-aware classification cwd-independent", async () => {
  const candidateRoot = await mkdtemp(path.join(os.tmpdir(), "qta-cwd-root-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-cwd-baseline-"));
  const cwdA = await mkdtemp(path.join(os.tmpdir(), "qta-cwd-run-a-"));
  const cwdB = await mkdtemp(path.join(os.tmpdir(), "qta-cwd-run-b-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    for (const directory of [candidateRoot, baselineDir, cwdA, cwdB]) {
      await mkdir(path.join(directory, path.dirname(relativeFile)), { recursive: true });
    }
    const baselineContent = namedMethodFixture("baselineLong", 118);
    const candidateContent = namedMethodFixture("replacementLong", 120);
    await writeFile(path.join(baselineDir, relativeFile), baselineContent);
    await writeFile(path.join(candidateRoot, relativeFile), candidateContent);
    const candidateAbs = path.join(candidateRoot, relativeFile);

    const script = path.resolve("scripts/check-ai-architecture.mjs");
    const run = (cwd) => spawnSync(process.execPath, [script,
      "--files", candidateAbs,
      "--baseline", path.resolve(baselineDir),
      "--candidate-root", candidateRoot,
      "--candidate-identity", "candidate-cwd-ind",
      "--json-output", path.join(cwd, "architecture-report.json")
    ], { cwd, encoding: "utf8" });

    const resultA = run(cwdA);
    const resultB = run(cwdB);
    assert.equal(resultA.status, 1, resultA.stdout);
    assert.equal(resultA.status, resultB.status);

    const reportA = JSON.parse(await readFile(path.join(cwdA, "architecture-report.json"), "utf8"));
    const reportB = JSON.parse(await readFile(path.join(cwdB, "architecture-report.json"), "utf8"));
    assert.equal(reportA.status, reportB.status);
    assert.equal(reportA.exitCode, reportB.exitCode);
    assert.equal(reportA.blockingErrorCount, reportB.blockingErrorCount);
    assert.deepEqual(reportA.introducedErrors, reportB.introducedErrors);
    assert.deepEqual(reportA.worsenedErrors, reportB.worsenedErrors);
    assert.deepEqual(reportA.preExistingErrors, reportB.preExistingErrors);
    const classificationOf = (report) => [
      ...report.introducedErrors, ...report.worsenedErrors, ...report.preExistingErrors
    ].filter((entry) => entry.message.includes("longest method")).map((entry) => entry.classification).sort();
    assert.deepEqual(classificationOf(reportA), classificationOf(reportB));
    const longestEntries = [
      ...reportA.introducedErrors, ...reportA.worsenedErrors
    ].filter((entry) => entry.message.includes("longest method"));
    assert.ok(longestEntries.length >= 1);
    for (const entry of longestEntries) {
      assert.equal(entry.classification, "introduced");
      assert.equal(entry.baselineMetric, null);
      assert.equal(entry.delta, null);
    }
  } finally {
    await rm(candidateRoot, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
    await rm(cwdA, { recursive: true, force: true });
    await rm(cwdB, { recursive: true, force: true });
  }
});

test("baseline-aware gate classifies a replacement over-threshold method as introduced", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-replace-method-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-replace-method-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    for (const directory of [candidateDir, baselineDir]) {
      await mkdir(path.join(directory, path.dirname(relativeFile)), { recursive: true });
    }
    await writeFile(path.join(baselineDir, relativeFile), namedMethodFixture("originalLong", 120));
    await writeFile(path.join(candidateDir, relativeFile), namedMethodFixture("replacementLong", 122));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const result = await runBaselineAwareGate({
      candidateDir, baselineDir, candidateFile: relativeFile, reportPath,
      allowedWorsenDelta: 50
    });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "FAIL");
    assert.equal(report.exitCode, 1);
    assert.ok(report.blockingErrorCount >= 1);
    const introduced = report.introducedErrors
      .filter((entry) => entry.message.includes("longest method"));
    assert.ok(introduced.length >= 1);
    for (const entry of introduced) {
      assert.equal(entry.classification, "introduced");
      assert.equal(entry.baselineMetric, null);
      assert.equal(entry.delta, null);
      assert.equal(typeof entry.candidateMetric, "number");
      assert.ok(entry.candidateMetric > 100);
    }
    assert.ok(report.errors.some((entry) => introduced.some((match) => match.id === entry.id)));
    const replacementAsPreExisting = report.preExistingErrors
      .filter((entry) => entry.message.includes("longest method"));
    assert.equal(replacementAsPreExisting.length, 0);
    assert.deepEqual(report.worsenedErrors, []);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});

test("baseline-aware report records baseline commit content hash and used worsen delta", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-record-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-record-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    for (const directory of [candidateDir, baselineDir]) {
      await mkdir(path.join(directory, path.dirname(relativeFile)), { recursive: true });
    }
    const baselineContent = namedMethodFixture("baselineLong", 118);
    const candidateContent = namedMethodFixture("replacementLong", 120);
    await writeFile(path.join(baselineDir, relativeFile), baselineContent);
    await writeFile(path.join(candidateDir, relativeFile), candidateContent);
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const baselineCommit = "deadbeefcafebabe0000000000000000abcdef12";
    const result = await runBaselineAwareGate({
      candidateDir, baselineDir, candidateFile: relativeFile, reportPath,
      baselineCommit, allowedWorsenDelta: 20
    });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.baselineCommit, baselineCommit);
    assert.equal(report.allowedWorsenDelta, 20);
    assert.equal(report.baselineIdentity, path.resolve(baselineDir));
    const expectedHash = buildBaselineContentHash([
      { file: relativeFile, sha256: createHash("sha256").update(baselineContent).digest("hex") }
    ]);
    assert.equal(report.baselineFileContentsSha256, expectedHash);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});

test("baseline-aware gate defaults worsen delta to zero and blocks any growth", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-delta-zero-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-delta-zero-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    for (const directory of [candidateDir, baselineDir]) {
      await mkdir(path.join(directory, path.dirname(relativeFile)), { recursive: true });
    }
    await writeFile(path.join(baselineDir, relativeFile), namedMethodFixture("sameMethod", 118));
    await writeFile(path.join(candidateDir, relativeFile), namedMethodFixture("sameMethod", 119));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const result = await runBaselineAwareGate({
      candidateDir, baselineDir, candidateFile: relativeFile, reportPath
    });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.allowedWorsenDelta, 0);
    assert.equal(report.status, "FAIL");
    assert.equal(report.exitCode, 1);
    const worsened = report.worsenedErrors
      .filter((entry) => entry.message.includes("longest method"));
    assert.ok(worsened.length >= 1);
    for (const entry of worsened) {
      assert.equal(entry.classification, "worsened");
      assert.equal(entry.delta, 1);
      assert.equal(typeof entry.candidateMetric, "number");
      assert.equal(typeof entry.baselineMetric, "number");
    }
    assert.ok(report.errors.some((entry) => worsened.some((match) => match.id === entry.id)));
    assert.deepEqual(report.introducedErrors, []);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});

test("baseline-aware repair keeps the full governance suite and strict no-baseline behavior green", async () => {
  const suiteScript = path.resolve("scripts/run-ai-governance-gates.mjs");
  const suite = spawnSync(process.execPath, [suiteScript],
    { cwd: path.resolve(import.meta.dirname, ".."), encoding: "utf8" });
  assert.equal(suite.status, 0, suite.stderr);

  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-repair-static-strict-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await writeFile(path.join(candidateDir, relativeFile), methodBodyFixture(118));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const script = path.resolve("scripts/check-ai-architecture.mjs");
    const result = spawnSync(process.execPath, [script,
      "--files", path.resolve(candidateDir, relativeFile),
      "--candidate-identity", "strict-repair-candidate",
      "--json-output", reportPath
    ], { cwd: candidateDir, encoding: "utf8" });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "FAIL");
    assert.equal(report.exitCode, 1);
    assert.ok(report.errors.length >= 1);
    assert.ok(report.errors.some((entry) => entry.message.includes("longest method")));
    assert.equal(report.allowedWorsenDelta, undefined);
    assert.equal(report.baselineIdentity, undefined);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
  }
});
