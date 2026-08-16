import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { mkdir, mkdtemp, rm, unlink, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { validateTaskControl, validateTaskControlFiles } from "../check-ai-task-control.mjs";

// ---------------------------------------------------------------------------
// Minimal fixture factory copied from ai-governance.test.mjs (not imported, so
// this suite's node --test run does not double-register the legacy cases), but
// with STRICTLY ORDERED timestamps: designer -> implementer -> reviewer ->
// REVIEW_CLEAR formation -> verifier dispatch -> verifier start.
// ---------------------------------------------------------------------------

const AGENT_BY_ROLE = {
  TEST_DESIGNER: "qta-test-designer",
  IMPLEMENTER: "qta-implementer",
  CODE_REVIEWER: "qta-code-reviewer",
  FINAL_VERIFIER: "qta-final-verifier"
};
const CAPABILITY_BY_ROLE = {
  TEST_DESIGNER: "READ_ONLY",
  IMPLEMENTER: "READ_WRITE",
  CODE_REVIEWER: "READ_ONLY",
  FINAL_VERIFIER: "VERIFY_EXECUTE"
};

function orderedRole(roleName, generation, id, startedAt, finishedAt) {
  return {
    roleRunId: id,
    dispatchId: `dispatch-${id}`,
    sessionId: `session-${id}`,
    startedAt,
    finishedAt,
    runtimeReceiptPath: "",
    dispatchReceiptPath: `.git/qta-governance/dispatches/ordering/${id}.json`,
    role: roleName,
    generation,
    executorType: "SUBAGENT",
    agentDefinition: `.zcode/agents/${AGENT_BY_ROLE[roleName]}.md`,
    sliceId: roleName === "IMPLEMENTER" ? "SLICE-01" : "",
    executionOutcome: "COMPLETED",
    capability: CAPABILITY_BY_ROLE[roleName],
    contextMode: "FRESH",
    enforcement: "ADVISORY",
    compensatingIsolation: "disposable worktree plus before/after candidate hashes",
    waitCalls: 1,
    maxShellPollsForOneCommand: roleName === "CODE_REVIEWER" ? 0 : 2,
    compactionCount: 0,
    status: "CLOSED",
    artifactAccepted: true,
    artifactPath: `artifacts/${id}.md`,
    artifactSha256: `sha-${id}`
  };
}

function timedTransitions(states, times) {
  return states.slice(0, -1).map((from, index) => ({
    sequence: index + 1,
    from,
    to: states[index + 1],
    at: times[index],
    actor: "parent-1"
  }));
}

// Replays the old-task overlap shape from the contract FACT section: the gen-1
// reviewer finished at 19:56:00 while the verifier had already started at
// 19:53:16. Reviewer/verifier windows are overridable per test.
function verifiedOrderingControl({ reviewer, verifier } = {}) {
  const identity = "commit-ordering-1";
  return {
    schemaVersion: 3,
    taskId: "ORDERING-SMOKE",
    controlPath: "artifacts/control.json",
    startedAt: "2026-08-14T19:40:00Z",
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
        id: "SLICE-01", description: "bounded ordering fixture", acIds: ["AC-01", "AC-02"],
        allowedWritePaths: ["src/main/java/example"], maxExpectedFiles: 4, maxProductionLineDelta: 300
      }],
      testInventory: [
        { testId: "TEST-STATIC-01", acIds: ["AC-01", "AC-02"], kind: "STATIC", required: true,
          sourcePath: "scripts/tests/qta-role-ordering.test.mjs", selector: "started before same-generation reviewer" },
        { testId: "TEST-AUTO-01", acIds: ["AC-01"], kind: "AUTOMATION", required: true,
          sourcePath: "scripts/tests/qta-role-ordering.test.mjs", selector: "precedes REVIEW_CLEAR formation" }
      ],
      blockingAmendments: []
    },
    git: { automation: "COMMIT", branch: "codex/ordering", baselineCommit: "base", preExistingDirtyPaths: [] },
    transitionHistory: timedTransitions(
      ["CONTEXT_READY", "CONTRACT_DRAFTED", "TEST_DESIGN_READY", "CONTRACT_FROZEN",
        "IMPLEMENTING", "SELF_CHECKED", "CANDIDATE_FROZEN", "REVIEW_CLEAR", "VERIFIED"],
      ["2026-08-14T19:40:30Z", "2026-08-14T19:40:35Z", "2026-08-14T19:40:40Z", "2026-08-14T19:40:45Z",
        "2026-08-14T19:41:50Z", "2026-08-14T19:41:55Z", "2026-08-14T19:57:00Z", "2026-08-14T20:30:00Z"]
    ),
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
      orderedRole("TEST_DESIGNER", 0, "test-1", "2026-08-14T19:40:10Z", "2026-08-14T19:40:40Z"),
      orderedRole("IMPLEMENTER", 1, "impl-1", "2026-08-14T19:41:00Z", "2026-08-14T19:41:30Z"),
      orderedRole("CODE_REVIEWER", 1, "review-1",
        reviewer?.startedAt ?? "2026-08-14T19:50:00Z", reviewer?.finishedAt ?? "2026-08-14T19:56:00Z"),
      orderedRole("FINAL_VERIFIER", 1, "verify-1",
        verifier?.startedAt ?? "2026-08-14T20:05:00Z", verifier?.finishedAt ?? "2026-08-14T20:25:00Z")
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
        receiptSha256: "sha-test-static-1", observedSelectors: ["started before same-generation reviewer"] },
      { testId: "TEST-AUTO-01", candidateIdentity: identity, executedByRoleRunId: "verify-1",
        result: "PASS", exitCode: 0, receiptPath: "artifacts/test-auto-1.json",
        receiptSha256: "sha-test-auto-1", observedSelectors: ["precedes REVIEW_CLEAR formation"] }
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

// ---------------------------------------------------------------------------
// File-stage fixture: accepted reviewer + verifier runs with real dispatch
// receipts (and optional reviewer outcome receipts) under the deterministic
// .git/qta-governance/dispatches/<sha(taskId)>/<sha(dispatchId)>.json layout.
// cycles records one CANDIDATE_FROZEN per candidate generation plus an optional
// REVIEW_CLEAR formation; reviewClearAt: null replays a review FAIL that never
// cleared, so generations and REVIEW_CLEAR occurrences do not map 1:1.
// ---------------------------------------------------------------------------

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

async function writeOrderingFiles(directory, taskId, runSpecs, cycles) {
  const projectRootSha256 = sha256(path.resolve(directory));
  const taskHash = sha256(taskId);
  const dispatchDirectory = path.join(directory, ".git", "qta-governance", "dispatches", taskHash);
  await mkdir(dispatchDirectory, { recursive: true });
  await mkdir(path.join(directory, "artifacts"), { recursive: true });
  const roleRuns = [];
  for (const spec of runSpecs) {
    const run = orderedRole(spec.role, spec.generation, spec.id, spec.startedAt, spec.finishedAt);
    const dispatchHash = sha256(run.dispatchId);
    const receiptRelative = `.git/qta-governance/dispatches/${taskHash}/${dispatchHash}.json`;
    run.dispatchReceiptPath = receiptRelative;
    const artifactContent = `${spec.id} artifact\n`;
    await writeFile(path.join(directory, "artifacts", `${spec.id}.md`), artifactContent);
    run.artifactSha256 = sha256(artifactContent);
    await writeFile(path.join(dispatchDirectory, `${dispatchHash}.json`), `${JSON.stringify({
      version: 2,
      taskId,
      dispatchId: run.dispatchId,
      roleRunId: run.roleRunId,
      role: spec.role,
      agentDefinition: run.agentDefinition,
      parentSessionId: `parent-${spec.id}`,
      observedAt: spec.dispatchObservedAt,
      projectRootSha256,
      status: "PENDING"
    }, null, 2)}\n`);
    if (spec.outcomeObservedAt !== undefined) {
      await writeFile(path.join(dispatchDirectory, `${dispatchHash}.outcome.json`), `${JSON.stringify({
        version: 1,
        taskId,
        dispatchId: run.dispatchId,
        roleRunId: run.roleRunId,
        parentSessionId: `parent-${spec.id}`,
        status: "SUCCEEDED",
        observedAt: spec.outcomeObservedAt,
        projectRootSha256
      }, null, 2)}\n`);
    }
    roleRuns.push(run);
  }
  const transitionHistory = [];
  let sequence = 0;
  for (const cycle of cycles) {
    transitionHistory.push({
      sequence: (sequence += 1), from: "SELF_CHECKED", to: "CANDIDATE_FROZEN",
      at: cycle.frozenAt, actor: "parent-1"
    });
    if (cycle.reviewClearAt !== null) {
      transitionHistory.push({
        sequence: (sequence += 1), from: "CANDIDATE_FROZEN", to: "REVIEW_CLEAR",
        at: cycle.reviewClearAt, actor: "parent-1"
      });
    }
  }
  return {
    taskId,
    startedAt: "2026-08-14T19:40:00Z",
    lane: "L2",
    lifecycleState: "REVIEW_CLEAR",
    contract: {},
    candidate: { mode: "COMMIT" },
    transitionHistory,
    roleRuns,
    review: {},
    verification: {}
  };
}

async function withOrderingDirectory(run, prefix = "qta-verifier-ordering-") {
  const directory = await mkdtemp(path.join(os.tmpdir(), prefix));
  try {
    await run(directory);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

// TD-05-01: overlap fails, replaying the old-task shape (reviewer finished
// 19:56:00, verifier started 19:53:16).
test("verifier that started before the same-generation reviewer finished is rejected", () => {
  const control = verifiedOrderingControl({
    verifier: { startedAt: "2026-08-14T19:53:16Z", finishedAt: "2026-08-14T20:10:00Z" }
  });
  const errors = validateTaskControl(control).errors;
  assert.deepEqual(errors, ["final verifier verify-1 started before same-generation reviewer review-1 finished"]);
});

// TD-05-02: dispatch receipt observed before REVIEW_CLEAR formation fails.
test("verifier dispatch observed before REVIEW_CLEAR formation is rejected", async () => {
  await withOrderingDirectory(async (directory) => {
    const control = await writeOrderingFiles(directory, "ORDERING-EARLY-DISPATCH", [
      { role: "CODE_REVIEWER", generation: 1, id: "review-1",
        startedAt: "2026-08-14T19:50:00Z", finishedAt: "2026-08-14T19:56:00Z",
        dispatchObservedAt: "2026-08-14T19:49:55Z", outcomeObservedAt: undefined },
      { role: "FINAL_VERIFIER", generation: 1, id: "verify-1",
        startedAt: "2026-08-14T19:50:05Z", finishedAt: "2026-08-14T20:10:00Z",
        dispatchObservedAt: "2026-08-14T19:50:00Z" }
    ], [{ frozenAt: "2026-08-14T19:49:00Z", reviewClearAt: "2026-08-14T19:55:00Z" }]);
    const warnings = [];
    const errors = await validateTaskControlFiles(control, directory, warnings);
    assert.deepEqual(errors, ["final verifier verify-1 dispatch precedes REVIEW_CLEAR formation"]);
    assert.match(warnings.join("\n"), /review-1 outcome receipt is missing or untimestamped/);
  });
});

// TD-05-03: strictly serial chain passes both structural and file gates.
test("serial reviewer -> REVIEW_CLEAR -> dispatch -> verifier chain passes", async () => {
  const control = verifiedOrderingControl();
  assert.deepEqual(validateTaskControl(control).errors, []);

  await withOrderingDirectory(async (directory) => {
    const fileControl = await writeOrderingFiles(directory, "ORDERING-SERIAL", [
      { role: "CODE_REVIEWER", generation: 1, id: "review-1",
        startedAt: "2026-08-14T19:50:00Z", finishedAt: "2026-08-14T19:56:00Z",
        dispatchObservedAt: "2026-08-14T19:49:55Z", outcomeObservedAt: "2026-08-14T19:56:30Z" },
      { role: "FINAL_VERIFIER", generation: 1, id: "verify-1",
        startedAt: "2026-08-14T20:05:00Z", finishedAt: "2026-08-14T20:25:00Z",
        dispatchObservedAt: "2026-08-14T19:58:00Z" }
    ], [{ frozenAt: "2026-08-14T19:49:00Z", reviewClearAt: "2026-08-14T19:57:00Z" }]);
    const warnings = [];
    assert.deepEqual(await validateTaskControlFiles(fileControl, directory, warnings), []);
    assert.deepEqual(warnings, []);
  });
});

// TD-05-04: equality boundary is legal.
test("verifier starting exactly when the reviewer finished is accepted", () => {
  const control = verifiedOrderingControl({
    verifier: { startedAt: "2026-08-14T19:56:00Z", finishedAt: "2026-08-14T20:10:00Z" }
  });
  assert.deepEqual(validateTaskControl(control).errors, []);
});

// TD-05-05: multi-generation history compares same-generation pairs only; the
// gen-2 reviewer finishes long after the gen-1 verifier started and must not
// trip it, and each verifier's dispatch is checked against its own generation's
// REVIEW_CLEAR formation.
test("multi-generation repairs compare reviewer and verifier pairs per generation only", async () => {
  const identity = "commit-ordering-2";
  const control = verifiedOrderingControl();
  control.taskId = "ORDERING-MULTIGEN";
  control.budget.repairRound = 1;
  control.repairHistory = [{
    round: 1, failureFingerprint: "P1:ordering-multigen", fromGeneration: 1, toGeneration: 2,
    findingRoleRunId: "review-1", implementerRoleRunId: "impl-2"
  }];
  control.candidate = { ...control.candidate, generation: 2, identity, commit: identity };
  control.transitionHistory = timedTransitions(
    ["CONTEXT_READY", "CONTRACT_DRAFTED", "TEST_DESIGN_READY", "CONTRACT_FROZEN",
      "IMPLEMENTING", "SELF_CHECKED", "CANDIDATE_FROZEN", "REVIEW_CLEAR",
      "IMPLEMENTING", "SELF_CHECKED", "CANDIDATE_FROZEN", "REVIEW_CLEAR", "VERIFIED"],
    ["2026-08-14T19:40:30Z", "2026-08-14T19:40:35Z", "2026-08-14T19:40:40Z", "2026-08-14T19:40:45Z",
      "2026-08-14T19:41:50Z", "2026-08-14T19:41:55Z", "2026-08-14T19:42:26Z",
      "2026-08-14T19:43:05Z", "2026-08-14T19:43:50Z", "2026-08-14T19:43:55Z",
      "2026-08-14T19:44:26Z", "2026-08-14T19:45:10Z"]
  );
  control.roleRuns = [
    orderedRole("TEST_DESIGNER", 0, "test-1", "2026-08-14T19:40:10Z", "2026-08-14T19:40:40Z"),
    orderedRole("IMPLEMENTER", 1, "impl-1", "2026-08-14T19:41:00Z", "2026-08-14T19:41:30Z"),
    orderedRole("CODE_REVIEWER", 1, "review-1", "2026-08-14T19:42:00Z", "2026-08-14T19:42:20Z"),
    orderedRole("FINAL_VERIFIER", 1, "verify-1", "2026-08-14T19:42:30Z", "2026-08-14T19:43:00Z"),
    orderedRole("IMPLEMENTER", 2, "impl-2", "2026-08-14T19:43:10Z", "2026-08-14T19:43:40Z"),
    orderedRole("CODE_REVIEWER", 2, "review-2", "2026-08-14T19:44:00Z", "2026-08-14T19:44:20Z"),
    orderedRole("FINAL_VERIFIER", 2, "verify-2", "2026-08-14T19:44:30Z", "2026-08-14T19:45:00Z")
  ];
  control.review = {
    ...control.review, generation: 2, candidateIdentity: identity,
    artifactPath: "artifacts/review-2.md", artifactSha256: "sha-review-2"
  };
  control.architectureGate.candidateIdentity = identity;
  control.verification = {
    ...control.verification, candidateIdentity: identity,
    artifactPath: "artifacts/verify-2.md", artifactSha256: "sha-verify-2"
  };
  control.testEvidence = control.testEvidence.map((item) => ({
    ...item, candidateIdentity: identity, executedByRoleRunId: "verify-2"
  }));
  control.evidence = control.evidence.map((item) => ({ ...item, candidateIdentity: identity }));
  assert.deepEqual(validateTaskControl(control).errors, []);

  await withOrderingDirectory(async (directory) => {
    const fileControl = await writeOrderingFiles(directory, "ORDERING-MULTIGEN", [
      { role: "CODE_REVIEWER", generation: 1, id: "review-1",
        startedAt: "2026-08-14T19:42:00Z", finishedAt: "2026-08-14T19:42:20Z",
        dispatchObservedAt: "2026-08-14T19:41:55Z", outcomeObservedAt: "2026-08-14T19:42:25Z" },
      { role: "FINAL_VERIFIER", generation: 1, id: "verify-1",
        startedAt: "2026-08-14T19:42:30Z", finishedAt: "2026-08-14T19:43:00Z",
        dispatchObservedAt: "2026-08-14T19:42:28Z" },
      { role: "CODE_REVIEWER", generation: 2, id: "review-2",
        startedAt: "2026-08-14T19:44:00Z", finishedAt: "2026-08-14T19:44:20Z",
        dispatchObservedAt: "2026-08-14T19:43:55Z", outcomeObservedAt: "2026-08-14T19:44:25Z" },
      { role: "FINAL_VERIFIER", generation: 2, id: "verify-2",
        startedAt: "2026-08-14T19:44:30Z", finishedAt: "2026-08-14T19:45:00Z",
        dispatchObservedAt: "2026-08-14T19:44:28Z" }
    ], [
      { frozenAt: "2026-08-14T19:42:20Z", reviewClearAt: "2026-08-14T19:42:26Z" },
      { frozenAt: "2026-08-14T19:44:20Z", reviewClearAt: "2026-08-14T19:44:26Z" }
    ]);
    const warnings = [];
    assert.deepEqual(await validateTaskControlFiles(fileControl, directory, warnings), []);
    assert.deepEqual(warnings, []);
  });
});

// TD-05-06: reviewer outcome cross-check errors when the outcome lands after
// the verifier dispatch, and degrades to a warning when the outcome file is
// absent (AMD-002: Hook-owned artifacts must not hard-fail the validator).
test("reviewer outcome observed after the verifier dispatch fails; a missing outcome only warns", async () => {
  await withOrderingDirectory(async (directory) => {
    const build = () => writeOrderingFiles(directory, "ORDERING-OUTCOME", [
      { role: "CODE_REVIEWER", generation: 1, id: "review-1",
        startedAt: "2026-08-14T19:50:00Z", finishedAt: "2026-08-14T19:56:00Z",
        dispatchObservedAt: "2026-08-14T19:49:55Z", outcomeObservedAt: "2026-08-14T19:59:30Z" },
      { role: "FINAL_VERIFIER", generation: 1, id: "verify-1",
        startedAt: "2026-08-14T20:00:00Z", finishedAt: "2026-08-14T20:10:00Z",
        dispatchObservedAt: "2026-08-14T19:58:00Z" }
    ], [{ frozenAt: "2026-08-14T19:49:00Z", reviewClearAt: "2026-08-14T19:57:00Z" }]);

    const control = await build();
    const warnings = [];
    const errors = await validateTaskControlFiles(control, directory, warnings);
    assert.deepEqual(errors, ["final verifier verify-1 dispatch precedes reviewer review-1 outcome receipt"]);
    assert.deepEqual(warnings, []);

    const dispatchHash = sha256("dispatch-review-1");
    await unlink(path.join(directory, ".git", "qta-governance", "dispatches",
      sha256("ORDERING-OUTCOME"), `${dispatchHash}.outcome.json`));
    const degradedWarnings = [];
    const degradedErrors = await validateTaskControlFiles(control, directory, degradedWarnings);
    assert.deepEqual(degradedErrors, []);
    assert.match(degradedWarnings.join("\n"),
      /review-1 outcome receipt is missing or untimestamped; cross-check against final verifier verify-1 dispatch degraded to advisory/);
  });
});

// TD-05-08: replays the BLOCKED-closure timeline (QTA-V2-MR0-CLOSEOUT-20260815-R1 §1).
// gen-1 review FAILs and never forms a REVIEW_CLEAR; repair to gen-2, which clears at
// 17:50:00 and dispatches its verifier legally at 17:51:10; repair to gen-3, which
// clears later at 18:28:00. The gen-2 verifier must stay bound to the gen-2 formation —
// occurrences[generation - 1] indexing mis-bound it to gen-3's REVIEW_CLEAR.
test("gen-2 verifier stays bound to gen-2 REVIEW_CLEAR when gen-1 failed review and gen-3 clears later", async () => {
  await withOrderingDirectory(async (directory) => {
    const control = await writeOrderingFiles(directory, "ORDERING-MULTICYCLE", [
      { role: "CODE_REVIEWER", generation: 1, id: "review-1",
        startedAt: "2026-08-15T17:30:00Z", finishedAt: "2026-08-15T17:35:00Z",
        dispatchObservedAt: "2026-08-15T17:29:55Z", outcomeObservedAt: "2026-08-15T17:35:30Z" },
      { role: "CODE_REVIEWER", generation: 2, id: "review-2",
        startedAt: "2026-08-15T17:44:00Z", finishedAt: "2026-08-15T17:48:15Z",
        dispatchObservedAt: "2026-08-15T17:43:55Z", outcomeObservedAt: "2026-08-15T17:48:37Z" },
      { role: "FINAL_VERIFIER", generation: 2, id: "verify-2",
        startedAt: "2026-08-15T17:51:23Z", finishedAt: "2026-08-15T18:10:00Z",
        dispatchObservedAt: "2026-08-15T17:51:10Z" }
    ], [
      { frozenAt: "2026-08-15T17:20:00Z", reviewClearAt: null },
      { frozenAt: "2026-08-15T17:45:00Z", reviewClearAt: "2026-08-15T17:50:00Z" },
      { frozenAt: "2026-08-15T18:20:00Z", reviewClearAt: "2026-08-15T18:28:00Z" }
    ]);
    const warnings = [];
    assert.deepEqual(await validateTaskControlFiles(control, directory, warnings), []);
    assert.deepEqual(warnings, []);
  });
});

// TD-05-09: a gen-3 verifier dispatched before the gen-3 REVIEW_CLEAR must fail even
// though the gen-2 REVIEW_CLEAR already exists; the legal gen-2 verifier in the same
// history stays clean.
test("verifier dispatched before its own generation's REVIEW_CLEAR fails despite an earlier generation's clear", async () => {
  await withOrderingDirectory(async (directory) => {
    const control = await writeOrderingFiles(directory, "ORDERING-EARLY-G3", [
      { role: "CODE_REVIEWER", generation: 2, id: "review-2",
        startedAt: "2026-08-15T17:44:00Z", finishedAt: "2026-08-15T17:48:15Z",
        dispatchObservedAt: "2026-08-15T17:43:55Z", outcomeObservedAt: "2026-08-15T17:48:37Z" },
      { role: "FINAL_VERIFIER", generation: 2, id: "verify-2",
        startedAt: "2026-08-15T17:51:23Z", finishedAt: "2026-08-15T18:10:00Z",
        dispatchObservedAt: "2026-08-15T17:51:10Z" },
      { role: "CODE_REVIEWER", generation: 3, id: "review-3",
        startedAt: "2026-08-15T18:21:00Z", finishedAt: "2026-08-15T18:24:00Z",
        dispatchObservedAt: "2026-08-15T18:20:55Z", outcomeObservedAt: "2026-08-15T18:24:30Z" },
      { role: "FINAL_VERIFIER", generation: 3, id: "verify-3",
        startedAt: "2026-08-15T18:26:00Z", finishedAt: "2026-08-15T18:40:00Z",
        dispatchObservedAt: "2026-08-15T18:25:00Z" }
    ], [
      { frozenAt: "2026-08-15T17:20:00Z", reviewClearAt: null },
      { frozenAt: "2026-08-15T17:45:00Z", reviewClearAt: "2026-08-15T17:50:00Z" },
      { frozenAt: "2026-08-15T18:20:00Z", reviewClearAt: "2026-08-15T18:28:00Z" }
    ]);
    const warnings = [];
    const errors = await validateTaskControlFiles(control, directory, warnings);
    assert.deepEqual(errors, ["final verifier verify-3 dispatch precedes REVIEW_CLEAR formation"]);
    assert.deepEqual(warnings, []);
  });
});

// TD-05-10: an accepted verifier whose generation never formed a REVIEW_CLEAR
// (dispatched while its generation's review is still pending) must fail explicitly;
// the old validator silently skipped the temporal gate in this shape.
test("accepted verifier without a same-generation REVIEW_CLEAR formation is rejected", async () => {
  await withOrderingDirectory(async (directory) => {
    const control = await writeOrderingFiles(directory, "ORDERING-NO-CLEAR", [
      { role: "CODE_REVIEWER", generation: 1, id: "review-1",
        startedAt: "2026-08-14T19:50:00Z", finishedAt: "2026-08-14T19:56:00Z",
        dispatchObservedAt: "2026-08-14T19:49:55Z", outcomeObservedAt: "2026-08-14T19:56:30Z" },
      { role: "FINAL_VERIFIER", generation: 2, id: "verify-2",
        startedAt: "2026-08-14T20:05:00Z", finishedAt: "2026-08-14T20:25:00Z",
        dispatchObservedAt: "2026-08-14T20:00:00Z" }
    ], [
      { frozenAt: "2026-08-14T19:49:00Z", reviewClearAt: "2026-08-14T19:57:00Z" },
      { frozenAt: "2026-08-14T19:58:00Z", reviewClearAt: null }
    ]);
    const warnings = [];
    const errors = await validateTaskControlFiles(control, directory, warnings);
    assert.deepEqual(errors,
      ["final verifier verify-2 has no REVIEW_CLEAR formation for generation 2"]);
    assert.deepEqual(warnings, []);
  });
});

// TD-05-07: the pre-existing governance assertion set keeps passing under the
// new ordering gate (fixture timestamps there were adjusted, not assertions).
// NODE_TEST_CONTEXT is stripped so the child is a real suite run, not a
// skipped "recursive" nested run detected inside this test file.
test("legacy ai-governance suite still passes under the ordering gate", () => {
  const environment = { ...process.env };
  delete environment.NODE_TEST_CONTEXT;
  const result = spawnSync(process.execPath, ["--test", "scripts/tests/ai-governance.test.mjs"], {
    cwd: path.resolve(import.meta.dirname, "..", ".."), encoding: "utf8", env: environment
  });
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  assert.match(`${result.stdout}\n${result.stderr}`, /fail 0/);
  assert.match(`${result.stdout}\n${result.stderr}`, /pass 7[0-9]/);
});
