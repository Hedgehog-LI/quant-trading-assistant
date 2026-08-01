#!/usr/bin/env node

import { createHash } from "node:crypto";
import { appendFile, lstat, mkdir, readFile, readlink } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

export const LANE_POLICY = Object.freeze({
  L0: { maxAcceptanceCriteria: 3, maxBlockingAmendments: 0, rawTokenBudget: 2_000_000 },
  L1: { maxAcceptanceCriteria: 5, maxBlockingAmendments: 1, rawTokenBudget: 6_000_000 },
  L2: { maxAcceptanceCriteria: 8, maxBlockingAmendments: 3, rawTokenBudget: 12_000_000 },
  L3: { maxAcceptanceCriteria: 10, maxBlockingAmendments: 5, rawTokenBudget: 20_000_000 }
});

const ORDERED_STATES = [
  "CONTEXT_READY", "CONTRACT_DRAFTED", "TEST_DESIGN_READY", "CONTRACT_FROZEN",
  "IMPLEMENTING", "SELF_CHECKED", "CANDIDATE_FROZEN", "REVIEW_CLEAR", "VERIFIED", "FINALIZED"
];
const EXTRA_STATES = new Set(["CHECKPOINTED", "BLOCKED"]);
const ROLE_NAMES = new Set(["TEST_DESIGNER", "IMPLEMENTER", "CODE_REVIEWER", "FINAL_VERIFIER"]);
const QUALITY_RESULTS = new Set(["PASS", "FAIL", "NOT_VERIFIED", "NOT_REQUIRED"]);
const DIMENSION_RESULTS = new Set(["PASS", "FAIL", "BLOCKED", "NOT_VERIFIED", "NOT_REQUIRED"]);

function present(value) {
  return typeof value === "string" && value.trim() !== "" && !value.includes("<");
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function atLeast(state, expected) {
  const current = ORDERED_STATES.indexOf(state);
  const target = ORDERED_STATES.indexOf(expected);
  return current >= target && target >= 0;
}

function duplicateValues(values) {
  const seen = new Set();
  return [...new Set(values.filter((value) => {
    if (!present(value)) return false;
    if (seen.has(value)) return true;
    seen.add(value);
    return false;
  }))];
}

function roleRunAccepted(run) {
  return run?.artifactAccepted === true && ["COMPLETED", "CLOSED"].includes(run?.status)
    && present(run?.artifactPath) && present(run?.artifactSha256);
}

function allowedTransition(from, to, lane) {
  if (to === "BLOCKED" || to === "CHECKPOINTED") return true;
  if (from === "CHECKPOINTED") return ORDERED_STATES.includes(to);
  if (lane === "L0" && from === "CONTRACT_DRAFTED" && to === "CONTRACT_FROZEN") return true;
  if (lane === "L0" && from === "CANDIDATE_FROZEN" && to === "VERIFIED") return true;
  if (["CANDIDATE_FROZEN", "REVIEW_CLEAR", "VERIFIED"].includes(from) && to === "IMPLEMENTING") return true;
  return ORDERED_STATES.indexOf(to) === ORDERED_STATES.indexOf(from) + 1;
}

export function validateJsonSchema(value, schema, currentPath = "$", rootSchema = schema) {
  if (schema.$ref?.startsWith("#/")) {
    const target = schema.$ref.slice(2).split("/").reduce((current, key) => current?.[key], rootSchema);
    return target ? validateJsonSchema(value, target, currentPath, rootSchema)
      : [`${currentPath} references an unknown schema: ${schema.$ref}`];
  }
  const errors = [];
  if (schema.const !== undefined && value !== schema.const) errors.push(`${currentPath} must equal ${schema.const}`);
  if (schema.enum && !schema.enum.includes(value)) errors.push(`${currentPath} is not in the allowed enum`);

  if (schema.type === "object") {
    if (value === null || typeof value !== "object" || Array.isArray(value)) return [`${currentPath} must be an object`];
    for (const key of schema.required ?? []) if (!(key in value)) errors.push(`${currentPath}.${key} is required`);
    for (const [key, childSchema] of Object.entries(schema.properties ?? {})) {
      if (key in value) errors.push(...validateJsonSchema(value[key], childSchema, `${currentPath}.${key}`, rootSchema));
    }
  } else if (schema.type === "array") {
    if (!Array.isArray(value)) return [`${currentPath} must be an array`];
    if (schema.minItems !== undefined && value.length < schema.minItems) errors.push(`${currentPath} has too few items`);
    if (schema.uniqueItems && new Set(value.map((item) => JSON.stringify(item))).size !== value.length) {
      errors.push(`${currentPath} must contain unique items`);
    }
    if (schema.items) value.forEach((item, index) => errors.push(...validateJsonSchema(item, schema.items, `${currentPath}[${index}]`, rootSchema)));
  } else if (schema.type === "string") {
    if (typeof value !== "string") errors.push(`${currentPath} must be a string`);
    else if (schema.minLength !== undefined && value.length < schema.minLength) errors.push(`${currentPath} is too short`);
  } else if (schema.type === "integer") {
    if (!Number.isInteger(value)) errors.push(`${currentPath} must be an integer`);
  } else if (schema.type === "number") {
    if (typeof value !== "number" || !Number.isFinite(value)) errors.push(`${currentPath} must be a number`);
  } else if (schema.type === "boolean" && typeof value !== "boolean") {
    errors.push(`${currentPath} must be a boolean`);
  }
  if (typeof value === "number") {
    if (schema.minimum !== undefined && value < schema.minimum) errors.push(`${currentPath} is below minimum`);
    if (schema.maximum !== undefined && value > schema.maximum) errors.push(`${currentPath} exceeds maximum`);
  }
  return errors;
}

export function validateTaskControl(control) {
  const errors = [];
  const warnings = [];
  const lane = LANE_POLICY[control?.lane];
  const state = control?.lifecycleState;

  if (control?.schemaVersion !== 2) errors.push("schemaVersion must be 2");
  if (!present(control?.taskId)) errors.push("taskId must be populated");
  if (!present(control?.controlPath)) errors.push("controlPath must be populated");
  if (!present(control?.startedAt) || !Number.isFinite(Date.parse(control.startedAt))) {
    errors.push("startedAt must be an ISO-8601 timestamp");
  }
  if (!lane) errors.push("lane must be one of L0, L1, L2, L3");
  if (!ORDERED_STATES.includes(state) && !EXTRA_STATES.has(state)) errors.push("lifecycleState is invalid");

  const criteria = control?.contract?.acceptanceCriteria;
  const criteriaList = Array.isArray(criteria) ? criteria : [];
  if (!Array.isArray(criteria) || criteria.length === 0) {
    errors.push("contract.acceptanceCriteria must be a non-empty array");
  } else {
    const ids = criteria.map((criterion) => criterion?.id);
    for (const duplicate of duplicateValues(ids)) errors.push(`duplicate AC ID: ${duplicate}`);
    for (const [index, criterion] of criteria.entries()) {
      if (!present(criterion?.id)) errors.push(`contract.acceptanceCriteria[${index}].id is required`);
      if (!Array.isArray(criterion?.requiredEvidence) || criterion.requiredEvidence.length === 0) {
        errors.push(`contract.acceptanceCriteria[${index}].requiredEvidence is required`);
      } else if (criterion.requiredEvidence.length > 2) {
        errors.push(`${criterion.id} has more than two mandatory evidence types`);
      }
    }
    if (lane && criteria.length > lane.maxAcceptanceCriteria) {
      errors.push(`${control.lane} allows at most ${lane.maxAcceptanceCriteria} ACs, found ${criteria.length}`);
    }
  }
  const blockingAmendments = control?.contract?.blockingAmendments ?? [];
  if (!Array.isArray(blockingAmendments)) errors.push("contract.blockingAmendments must be an array");
  if (lane && blockingAmendments.length > lane.maxBlockingAmendments) {
    errors.push(`${control.lane} allows at most ${lane.maxBlockingAmendments} blocking amendments`);
  }
  if (!present(control?.contract?.path) || !present(control?.contract?.sha256)) {
    errors.push("contract path and SHA-256 must be populated");
  }

  const transitions = control?.transitionHistory;
  if (!Array.isArray(transitions) || transitions.length === 0) {
    errors.push("transitionHistory must be a non-empty append-only array");
  } else {
    let expectedFrom = "CONTEXT_READY";
    transitions.forEach((transition, index) => {
      const prefix = `transitionHistory[${index}]`;
      if (transition?.sequence !== index + 1) errors.push(`${prefix}.sequence must be ${index + 1}`);
      if (transition?.from !== expectedFrom) errors.push(`${prefix}.from must be ${expectedFrom}`);
      if (!allowedTransition(transition?.from, transition?.to, control?.lane)) {
        errors.push(`${prefix} contains an invalid lifecycle transition`);
      }
      if (!present(transition?.at) || !present(transition?.actor)) errors.push(`${prefix} needs at and actor`);
      expectedFrom = transition?.to;
    });
    if (expectedFrom !== state) errors.push("last transition does not match lifecycleState");
  }

  const budget = control?.budget ?? {};
  if (budget.maxRepairRounds !== 2) errors.push("maxRepairRounds must be 2");
  if (budget.maxWaitCallsPerRole !== 2) errors.push("maxWaitCallsPerRole must be 2");
  if (budget.maxShellPollsPerCommand !== 3) errors.push("maxShellPollsPerCommand must be 3");
  if (!["RUNTIME", "MANUAL", "UNAVAILABLE"].includes(budget.contextMeasurement)) {
    errors.push("contextMeasurement must be RUNTIME, MANUAL, or UNAVAILABLE");
  }
  if (budget.contextMeasurement === "UNAVAILABLE") {
    if (budget.contextPercent !== null) errors.push("UNAVAILABLE context measurement requires contextPercent=null");
  } else if (!Number.isFinite(budget.contextPercent)
      || budget.contextPercent < 0 || budget.contextPercent > 100) {
    errors.push("measured contextPercent must be between 0 and 100");
  }
  if (!Number.isInteger(budget.compactionCount) || budget.compactionCount < 0) {
    errors.push("compactionCount must be a non-negative integer");
  }
  if (!Number.isInteger(budget.repairRound) || budget.repairRound < 0) {
    errors.push("repairRound must be a non-negative integer");
  }
  if ((budget.repairRound ?? 0) > 2) errors.push("repair round limit exceeded");
  if ((budget.contextPercent ?? 0) >= 60 && !["CHECKPOINTED", "BLOCKED"].includes(state)) {
    errors.push("context at or above 60% requires CHECKPOINTED or BLOCKED");
  } else if ((budget.contextPercent ?? 0) >= 40 && !["CHECKPOINTED", "BLOCKED"].includes(state)) {
    warnings.push("context at or above 40%: checkpoint before another lifecycle transition");
  }
  if ((budget.compactionCount ?? 0) > 0 && !["CHECKPOINTED", "BLOCKED"].includes(state)) {
    errors.push("first compaction requires immediate checkpoint and role termination");
  }
  if (lane && Number.isFinite(budget.rawTokens) && budget.rawTokens > lane.rawTokenBudget) {
    errors.push(`${control.lane} raw-token budget exceeded`);
  }
  if (Number.isFinite(budget.weeklyAllowancePercent) && budget.weeklyAllowancePercent > 5) {
    errors.push("task consumed more than 5% of weekly allowance");
  }

  if (!Array.isArray(control?.repairHistory)) errors.push("repairHistory must be an array");
  const repairHistory = Array.isArray(control?.repairHistory) ? control.repairHistory : [];
  if (repairHistory.length !== (budget.repairRound ?? 0)) errors.push("repairHistory length must equal repairRound");
  const repairsByFingerprint = new Map();
  repairHistory.forEach((repair, index) => {
    if (repair?.round !== index + 1) errors.push(`repairHistory[${index}].round must be ${index + 1}`);
    if (!present(repair?.failureFingerprint)) errors.push(`repairHistory[${index}].failureFingerprint is required`);
    const count = (repairsByFingerprint.get(repair?.failureFingerprint) ?? 0) + 1;
    repairsByFingerprint.set(repair?.failureFingerprint, count);
    if (count > 2) errors.push(`failure fingerprint exceeded two repairs: ${repair.failureFingerprint}`);
    if (repair?.fromGeneration !== index + 1 || repair?.toGeneration !== index + 2) {
      errors.push(`repairHistory[${index}] generation chain is invalid`);
    }
    if (!present(repair?.findingRoleRunId) || !present(repair?.implementerRoleRunId)) {
      errors.push(`repairHistory[${index}] finding and implementer role-run IDs are required`);
    }
  });

  if (!Array.isArray(control?.roleRuns)) errors.push("roleRuns must be an array");
  const roleRuns = Array.isArray(control?.roleRuns) ? control.roleRuns : [];
  for (const duplicate of duplicateValues(roleRuns.map((run) => run?.roleRunId))) errors.push(`reused roleRunId: ${duplicate}`);
  for (const duplicate of duplicateValues(roleRuns.map((run) => run?.sessionId))) errors.push(`reused role sessionId: ${duplicate}`);
  for (const [index, run] of roleRuns.entries()) {
    const prefix = `roleRuns[${index}]`;
    if (!ROLE_NAMES.has(run?.role)) errors.push(`${prefix}.role is invalid`);
    if (!present(run?.roleRunId) || !present(run?.sessionId)) errors.push(`${prefix} role/session ID is required`);
    if (!present(run?.startedAt) || !Number.isFinite(Date.parse(run.startedAt))) {
      errors.push(`${prefix}.startedAt must be an ISO-8601 timestamp`);
    }
    const terminalRun = ["COMPLETED", "CLOSED", "POLICY_VIOLATION", "BLOCKED"].includes(run?.status);
    if (!terminalRun) errors.push(`${prefix}.status must be terminal before the role run is appended`);
    if (terminalRun && (!present(run?.finishedAt) || !Number.isFinite(Date.parse(run.finishedAt)))) {
      errors.push(`${prefix}.finishedAt is required for a terminal role run`);
    } else if (present(run?.finishedAt) && Date.parse(run.finishedAt) < Date.parse(run.startedAt)) {
      errors.push(`${prefix}.finishedAt cannot precede startedAt`);
    }
    if (!Number.isInteger(run?.generation) || run.generation < 0) errors.push(`${prefix}.generation is invalid`);
    if (run?.contextMode !== "FRESH") errors.push(`${prefix}.contextMode must be FRESH`);
    if (!Number.isInteger(run?.waitCalls) || run.waitCalls < 0 || run.waitCalls > 2) {
      errors.push(`${prefix}.waitCalls must be between 0 and 2`);
    }
    if (!Number.isInteger(run?.maxShellPollsForOneCommand)
        || run.maxShellPollsForOneCommand < 0 || run.maxShellPollsForOneCommand > 3) {
      errors.push(`${prefix}.maxShellPollsForOneCommand must be between 0 and 3`);
    }
    if (!Number.isInteger(run?.compactionCount) || run.compactionCount < 0) {
      errors.push(`${prefix}.compactionCount must be a non-negative integer`);
    } else if (run.compactionCount > 0 && run?.status !== "POLICY_VIOLATION") {
      errors.push(`${prefix} compacted but is not marked POLICY_VIOLATION`);
    }
    if (run?.enforcement !== "ADVISORY") {
      errors.push(`${prefix}.enforcement must be ADVISORY until ZCode exposes platform-authenticated attestation`);
    }
    if (run?.enforcement === "ADVISORY" && !present(run?.compensatingIsolation)) {
      errors.push(`${prefix}.compensatingIsolation is required for ADVISORY enforcement`);
    }
    if (run?.artifactAccepted === true && !roleRunAccepted(run)) {
      errors.push(`${prefix} accepted artifact requires CLOSED/COMPLETED status and artifact path/hash`);
    }
  }
  repairHistory.forEach((repair, index) => {
    const findingRun = roleRuns.find((run) => run.roleRunId === repair?.findingRoleRunId);
    const implementerRun = roleRuns.find((run) => run.roleRunId === repair?.implementerRoleRunId);
    if (!findingRun || !["CODE_REVIEWER", "FINAL_VERIFIER"].includes(findingRun.role)
        || findingRun.generation !== repair?.fromGeneration || !roleRunAccepted(findingRun)) {
      errors.push(`repairHistory[${index}].findingRoleRunId is not an accepted finding role for the source generation`);
    }
    if (!implementerRun || implementerRun.role !== "IMPLEMENTER"
        || implementerRun.generation !== repair?.toGeneration || !roleRunAccepted(implementerRun)) {
      errors.push(`repairHistory[${index}].implementerRoleRunId is not an accepted implementer for the repaired generation`);
    }
  });

  const candidate = control?.candidate ?? {};
  const expectedGeneration = (budget.repairRound ?? 0) + 1;
  if (atLeast(state, "CANDIDATE_FROZEN")) {
    if (candidate.generation !== expectedGeneration) errors.push("candidate generation must equal repairRound + 1");
    if (!present(candidate.identity)) errors.push("frozen candidate identity is required");
    if (!present(candidate.diffArtifactPath) || !present(candidate.diffArtifactSha256)) {
      errors.push("frozen candidate diff artifact path/hash is required");
    }
    if (candidate.mode === "COMMIT") {
      for (const field of ["commit", "treeHash", "patchSha256"]) {
        if (!present(candidate[field])) errors.push(`candidate.${field} is required in COMMIT mode`);
      }
      if (candidate.identity !== candidate.commit) errors.push("COMMIT candidate identity must equal commit");
      if (present(candidate.diffArtifactSha256) && candidate.diffArtifactSha256 !== candidate.patchSha256) {
        errors.push("COMMIT diff artifact SHA-256 must equal patchSha256");
      }
    } else if (candidate.mode === "SNAPSHOT") {
      for (const field of ["manifestPath", "manifestSha256", "entrySetSha256"]) {
        if (!present(candidate[field])) errors.push(`candidate.${field} is required in SNAPSHOT mode`);
      }
      if (candidate.identity !== candidate.manifestSha256) {
        errors.push("SNAPSHOT candidate identity must equal manifestSha256");
      }
    } else errors.push("candidate.mode must be COMMIT or SNAPSHOT");

    for (let generation = 1; generation <= candidate.generation; generation += 1) {
      if (!roleRuns.some((run) => run.role === "IMPLEMENTER" && run.generation === generation && roleRunAccepted(run))) {
        errors.push(`accepted implementer role run missing for generation ${generation}`);
      }
    }
  }
  if (lane && control.lane !== "L0" && atLeast(state, "CONTRACT_FROZEN")
      && !roleRuns.some((run) => run.role === "TEST_DESIGNER" && roleRunAccepted(run))) {
    errors.push(`${control.lane} requires an accepted fresh test-designer role run`);
  }

  const review = control?.review ?? {};
  if (control?.lane === "L0") {
    if (review.omitted !== true || !present(review.omissionReason)) errors.push("L0 requires an explicit code-review omission record");
  } else if (atLeast(state, "REVIEW_CLEAR")) {
    if (review.candidateIdentity !== candidate.identity || review.generation !== candidate.generation) {
      errors.push("review candidate identity/generation mismatch");
    }
    if (review.functionalVerdict !== "PASS" || review.architectureVerdict !== "PASS") {
      errors.push("functional and architecture reviews must pass");
    }
    for (let generation = 1; generation <= candidate.generation; generation += 1) {
      if (!roleRuns.some((run) => run.role === "CODE_REVIEWER" && run.generation === generation && roleRunAccepted(run))) {
        errors.push(`accepted reviewer role run missing for generation ${generation}`);
      }
    }
    if (!present(review.artifactPath) || !present(review.artifactSha256)) errors.push("review artifact path/hash is required");
    const acceptedReviewer = roleRuns.find((run) => run.role === "CODE_REVIEWER"
      && run.generation === candidate.generation && roleRunAccepted(run));
    if (acceptedReviewer && (acceptedReviewer.artifactPath !== review.artifactPath
        || acceptedReviewer.artifactSha256 !== review.artifactSha256)) {
      errors.push("review artifact is not bound to the accepted reviewer role run");
    }
  } else {
    for (const value of [review.functionalVerdict, review.architectureVerdict]) {
      if (value !== undefined && !QUALITY_RESULTS.has(value)) errors.push("review quality verdict is invalid");
    }
  }

  const verification = control?.verification ?? {};
  if (atLeast(state, "VERIFIED")) {
    if (verification.candidateIdentity !== candidate.identity) errors.push("verification candidate identity mismatch");
    if (verification.functionalVerdict !== "PASS" || verification.architectureVerdict !== "PASS") {
      errors.push("functional and architecture verification must pass");
    }
    if (!["ACCEPTED", "CONDITIONALLY_ACCEPTED"].includes(verification.verdict)) {
      errors.push("verified state requires ACCEPTED or CONDITIONALLY_ACCEPTED");
    }
    if (verification.deliveryPermitted !== true) errors.push("verified state requires deliveryPermitted=true");
    if (!present(verification.artifactPath) || !present(verification.artifactSha256)) {
      errors.push("verification artifact path/hash is required");
    }
    const acceptedVerifier = roleRuns.find((run) => run.role === "FINAL_VERIFIER"
      && run.generation === candidate.generation && roleRunAccepted(run));
    if (!acceptedVerifier) errors.push("accepted fresh final verifier role run is missing");
    else if (acceptedVerifier.artifactPath !== verification.artifactPath
        || acceptedVerifier.artifactSha256 !== verification.artifactSha256) {
      errors.push("verification artifact is not bound to the accepted final-verifier role run");
    }

    const evidence = Array.isArray(control?.evidence) ? control.evidence : [];
    for (const criterion of criteriaList) {
      for (const kind of criterion.requiredEvidence ?? []) {
        if (!evidence.some((item) => item.acId === criterion.id && item.kind === kind
          && item.candidateIdentity === candidate.identity && present(item.artifactPath)
          && present(item.artifactSha256))) {
          errors.push(`${criterion.id} is missing ${kind} evidence for the frozen candidate`);
        }
      }
    }
  }

  const dimensions = verification?.dimensions ?? {};
  for (const name of ["STATIC", "AUTOMATION", "RUNTIME", "DEPLOYMENT"]) {
    const dimension = dimensions[name];
    if (!dimension || !DIMENSION_RESULTS.has(dimension.status)) errors.push(`verification dimension ${name} is missing or invalid`);
    else if (atLeast(state, "VERIFIED") && dimension.required === true && dimension.status !== "PASS") {
      errors.push(`required verification dimension ${name} did not pass`);
    }
  }
  if (control?.lane === "L3" && atLeast(state, "VERIFIED")) {
    for (const name of ["RUNTIME", "DEPLOYMENT"]) {
      if (dimensions[name]?.required !== true || dimensions[name]?.status !== "PASS") errors.push(`L3 requires ${name}=PASS`);
    }
  }

  const finalization = control?.finalization ?? {};
  if (state === "FINALIZED") {
    if (verification.deliveryPermitted !== true) errors.push("finalization requires a delivery-permitted verdict");
    if (finalization.status !== "COMPLETED" || finalization.candidateIdentity !== candidate.identity
      || !present(finalization.artifactPath) || !present(finalization.artifactSha256)) {
      errors.push("FINALIZED requires a completed finalization artifact bound to the candidate");
    }
  }

  return { errors, warnings };
}

async function validateArtifact(root, relative, expectedSha, label, errors) {
  if (!present(relative) || !present(expectedSha)) return;
  try {
    const content = await readFile(path.resolve(root, relative));
    if (sha256(content) !== expectedSha) errors.push(`${label} SHA-256 mismatch: ${relative}`);
  } catch (error) {
    errors.push(`${label} is unavailable: ${relative} (${error.code ?? error.message})`);
  }
}

async function validateSnapshotEntry(root, entry, errors) {
  if (!entry || !present(entry.path) || !["file", "symlink", "absent"].includes(entry.type)) {
    errors.push("candidate manifest contains an invalid entry");
    return;
  }
  const absoluteRoot = path.resolve(root);
  const absolute = path.resolve(absoluteRoot, entry.path);
  if (absolute !== absoluteRoot && !absolute.startsWith(`${absoluteRoot}${path.sep}`)) {
    errors.push(`candidate manifest entry escapes repository root: ${entry.path}`);
    return;
  }

  let info;
  try {
    info = await lstat(absolute);
  } catch (error) {
    if (error.code === "ENOENT" && entry.type === "absent") return;
    errors.push(`candidate snapshot entry is unavailable: ${entry.path}`);
    return;
  }
  if (entry.type === "absent") {
    errors.push(`candidate snapshot expected an absent path that now exists: ${entry.path}`);
    return;
  }
  if (entry.type === "file") {
    if (!info.isFile() || sha256(await readFile(absolute)) !== entry.sha256) {
      errors.push(`candidate snapshot file drifted: ${entry.path}`);
    }
    return;
  }
  if (!info.isSymbolicLink() || sha256(Buffer.from(await readlink(absolute), "utf8")) !== entry.sha256) {
    errors.push(`candidate snapshot symlink drifted: ${entry.path}`);
  }
}

function nulSeparatedPaths(value) {
  return value.toString("utf8").split("\0").filter(Boolean)
    .map((item) => item.split(path.sep).join("/"));
}

function actualChangedPaths(root, baselineCommit) {
  const tracked = execFileSync("git", ["diff", "--name-only", "-z", baselineCommit, "--"], {
    cwd: root, stdio: ["ignore", "pipe", "pipe"]
  });
  const untracked = execFileSync("git", ["ls-files", "--others", "--exclude-standard", "-z"], {
    cwd: root, stdio: ["ignore", "pipe", "pipe"]
  });
  return new Set([...nulSeparatedPaths(tracked), ...nulSeparatedPaths(untracked)]);
}

function taskMetadataPaths(control) {
  const values = [
    control.controlPath, control.contract?.path, control.candidate?.manifestPath,
    control.candidate?.diffArtifactPath, control.review?.artifactPath,
    control.verification?.artifactPath, control.finalization?.artifactPath,
    ...(control.git?.preExistingDirtyPaths ?? []), ...(control.finalization?.changedPaths ?? []),
    ...(control.roleRuns ?? []).map((run) => run.artifactPath),
    ...(control.evidence ?? []).map((item) => item.artifactPath)
  ];
  return new Set(values.filter(present).map((item) => item.split(path.sep).join("/")));
}

async function validateRuntimeReceipt(root, control, run, errors) {
  if (!present(run.runtimeReceiptPath)) return;
  try {
    const receipt = JSON.parse(await readFile(path.resolve(root, run.runtimeReceiptPath), "utf8"));
    if (receipt.sessionId !== run.sessionId) errors.push(`role ${run.roleRunId} runtime receipt session mismatch`);
    if (receipt.projectRootSha256 !== sha256(path.resolve(root))) {
      errors.push(`role ${run.roleRunId} runtime receipt belongs to another project`);
    }
    const firstSeen = Date.parse(receipt.firstSeenAt);
    if (!Number.isFinite(firstSeen) || firstSeen < Date.parse(control.startedAt)
        || firstSeen < Date.parse(run.startedAt) || firstSeen > Date.parse(run.finishedAt)) {
      errors.push(`role ${run.roleRunId} runtime receipt is outside the declared fresh-run window`);
    }
    if (!present(receipt.nonce)) errors.push(`role ${run.roleRunId} runtime receipt has no nonce`);
  } catch (error) {
    errors.push(`role ${run.roleRunId} runtime receipt is unavailable (${error.code ?? error.message})`);
  }
}

export async function validateTaskControlFiles(control, root = process.cwd()) {
  const errors = [];
  await validateArtifact(root, control.contract?.path, control.contract?.sha256, "contract", errors);
  if (atLeast(control.lifecycleState, "CANDIDATE_FROZEN")) {
    await validateArtifact(root, control.candidate?.diffArtifactPath,
      control.candidate?.diffArtifactSha256, "candidate diff artifact", errors);
  }

  if (control.candidate?.mode === "SNAPSHOT" && present(control.candidate?.manifestPath)) {
    await validateArtifact(root, control.candidate.manifestPath, control.candidate.manifestSha256, "candidate manifest", errors);
    try {
      const manifest = JSON.parse(await readFile(path.resolve(root, control.candidate.manifestPath), "utf8"));
      if (!Array.isArray(manifest.entries)) errors.push("candidate manifest entries must be an array");
      else {
        const entrySetSha256 = sha256(Buffer.from(JSON.stringify(manifest.entries), "utf8"));
        if (manifest.entrySetSha256 !== entrySetSha256
            || entrySetSha256 !== control.candidate.entrySetSha256) {
          errors.push("candidate entry-set SHA-256 mismatch");
        }
        for (const entry of manifest.entries) await validateSnapshotEntry(root, entry, errors);
        try {
          const changedPaths = actualChangedPaths(root, control.git?.baselineCommit);
          const coveredPaths = new Set(manifest.entries.map((entry) => entry.path));
          const metadataPaths = taskMetadataPaths(control);
          for (const changedPath of changedPaths) {
            if (!coveredPaths.has(changedPath) && !metadataPaths.has(changedPath)) {
              errors.push(`candidate SNAPSHOT omits changed path: ${changedPath}`);
            }
          }
        } catch (error) {
          errors.push(`candidate SNAPSHOT changed-path coverage cannot be verified (${error.message})`);
        }
      }
    } catch (error) {
      errors.push(`candidate manifest cannot be parsed (${error.message})`);
    }
  }

  if (control.candidate?.mode === "COMMIT" && present(control.candidate?.commit)) {
    try {
      const tree = execFileSync("git", ["show", "-s", "--format=%T", control.candidate.commit], {
        cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"]
      }).trim();
      if (tree !== control.candidate.treeHash) errors.push("candidate tree hash does not match Git");
      const patch = execFileSync("git", ["diff", "--binary", control.git.baselineCommit, control.candidate.commit, "--"], {
        cwd: root, stdio: ["ignore", "pipe", "pipe"]
      });
      if (sha256(patch) !== control.candidate.patchSha256) errors.push("candidate patch SHA-256 does not match Git");
    } catch (error) {
      errors.push(`candidate commit cannot be verified (${error.message})`);
    }
  }

  for (const run of control.roleRuns ?? []) {
    if (run.artifactAccepted) await validateArtifact(root, run.artifactPath, run.artifactSha256, `role ${run.roleRunId} artifact`, errors);
    await validateRuntimeReceipt(root, control, run, errors);
  }
  if (control.lane !== "L0") await validateArtifact(root, control.review?.artifactPath, control.review?.artifactSha256, "review artifact", errors);
  if (atLeast(control.lifecycleState, "VERIFIED")) {
    await validateArtifact(root, control.verification?.artifactPath, control.verification?.artifactSha256, "verification artifact", errors);
    for (const item of control.evidence ?? []) {
      await validateArtifact(root, item.artifactPath, item.artifactSha256, `evidence ${item.evidenceId ?? item.acId}`, errors);
    }
  }
  if (control.lifecycleState === "FINALIZED") {
    await validateArtifact(root, control.finalization?.artifactPath, control.finalization?.artifactSha256, "finalization artifact", errors);
  }
  return errors;
}

function exactPrefix(previous, current) {
  return Array.isArray(previous) && Array.isArray(current) && previous.length <= current.length
    && previous.every((item, index) => JSON.stringify(item) === JSON.stringify(current[index]));
}

export function validateMonotonicControl(previous, current) {
  const errors = [];
  if (previous.taskId !== current.taskId || previous.startedAt !== current.startedAt
      || previous.controlPath !== current.controlPath) {
    errors.push("control identity/start time changed after anchoring");
  }
  if (JSON.stringify(previous.git) !== JSON.stringify(current.git)) {
    errors.push("Git baseline or pre-existing dirty-path manifest changed after anchoring");
  }
  if (!exactPrefix(previous.transitionHistory, current.transitionHistory)) {
    errors.push("transitionHistory rewrote or removed anchored events");
  }
  if (!exactPrefix(previous.repairHistory, current.repairHistory)) {
    errors.push("repairHistory rewrote or removed anchored repairs");
  }
  if (!exactPrefix(previous.contract?.blockingAmendments, current.contract?.blockingAmendments)) {
    errors.push("blocking amendment history rewrote or removed anchored entries");
  }
  if ((current.budget?.repairRound ?? -1) < (previous.budget?.repairRound ?? -1)) {
    errors.push("repairRound decreased after anchoring");
  }
  if ((current.budget?.compactionCount ?? -1) < (previous.budget?.compactionCount ?? -1)) {
    errors.push("compactionCount decreased after anchoring");
  }
  if (Number.isFinite(previous.budget?.rawTokens)
      && (!Number.isFinite(current.budget?.rawTokens) || current.budget.rawTokens < previous.budget.rawTokens)) {
    errors.push("rawTokens decreased or disappeared after anchoring");
  }
  if (Number.isFinite(previous.budget?.weeklyAllowancePercent)
      && (!Number.isFinite(current.budget?.weeklyAllowancePercent)
        || current.budget.weeklyAllowancePercent < previous.budget.weeklyAllowancePercent)) {
    errors.push("weeklyAllowancePercent decreased or disappeared after anchoring");
  }
  if (!exactPrefix(previous.roleRuns, current.roleRuns)) {
    errors.push("roleRuns rewrote or removed anchored terminal role instances");
  }
  if (atLeast(previous.lifecycleState, "CONTRACT_FROZEN")) {
    for (const field of ["path", "version", "sha256", "acceptanceCriteria"]) {
      if (JSON.stringify(previous.contract?.[field]) !== JSON.stringify(current.contract?.[field])) {
        errors.push(`frozen contract.${field} changed after anchoring`);
      }
    }
  }
  if ((current.candidate?.generation ?? 0) < (previous.candidate?.generation ?? 0)) {
    errors.push("candidate generation decreased after anchoring");
  } else if (current.candidate?.generation === previous.candidate?.generation
      && present(previous.candidate?.identity) && current.candidate?.identity !== previous.candidate.identity) {
    errors.push("candidate identity changed without a new generation");
  }
  if (["FINALIZED", "BLOCKED"].includes(previous.lifecycleState)
      && current.lifecycleState !== previous.lifecycleState) {
    errors.push("terminal lifecycle state changed after anchoring");
  }
  if (atLeast(previous.lifecycleState, "REVIEW_CLEAR") && previous.review !== undefined
      && JSON.stringify(previous.review) !== JSON.stringify(current.review)) {
    errors.push("review evidence changed after anchoring");
  }
  if (atLeast(previous.lifecycleState, "VERIFIED")) {
    if (previous.verification !== undefined
        && JSON.stringify(previous.verification) !== JSON.stringify(current.verification)) {
      errors.push("verification evidence changed after anchoring");
    }
    if (previous.evidence !== undefined && !exactPrefix(previous.evidence, current.evidence)) {
      errors.push("AC evidence rewrote or removed anchored entries");
    }
  }
  if (previous.lifecycleState === "FINALIZED" && previous.finalization !== undefined
      && JSON.stringify(previous.finalization) !== JSON.stringify(current.finalization)) {
    errors.push("finalization evidence changed after anchoring");
  }
  return errors;
}

function controlAnchorSnapshot(control) {
  return {
    taskId: control.taskId,
    controlPath: control.controlPath,
    startedAt: control.startedAt,
    lifecycleState: control.lifecycleState,
    contract: {
      version: control.contract?.version,
      sha256: control.contract?.sha256,
      blockingAmendments: control.contract?.blockingAmendments ?? []
    },
    git: {
      automation: control.git?.automation,
      branch: control.git?.branch,
      baselineCommit: control.git?.baselineCommit,
      preExistingDirtyPaths: control.git?.preExistingDirtyPaths ?? []
    },
    transitionHistory: control.transitionHistory ?? [],
    budget: {
      repairRound: control.budget?.repairRound,
      compactionCount: control.budget?.compactionCount,
      rawTokens: control.budget?.rawTokens,
      weeklyAllowancePercent: control.budget?.weeklyAllowancePercent
    },
    repairHistory: control.repairHistory ?? [],
    roleRuns: control.roleRuns ?? [],
    candidate: {
      mode: control.candidate?.mode,
      generation: control.candidate?.generation,
      identity: control.candidate?.identity
    },
    review: control.review,
    verification: control.verification,
    finalization: control.finalization,
    evidence: control.evidence ?? []
  };
}

function controlAnchorPath(root, taskId) {
  const relative = execFileSync("git", ["rev-parse", "--git-path",
    `qta-governance/tasks/${sha256(taskId)}.jsonl`], {
    cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"]
  }).trim();
  return path.resolve(root, relative);
}

async function readControlAnchors(root, taskId) {
  const file = controlAnchorPath(root, taskId);
  let content = "";
  try {
    content = await readFile(file, "utf8");
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  const entries = content.split("\n").filter(Boolean).map((line) => JSON.parse(line));
  let previousHash = null;
  for (const [index, entry] of entries.entries()) {
    const { eventHash, ...payload } = entry;
    if (entry.sequence !== index + 1 || entry.previousHash !== previousHash
        || sha256(JSON.stringify(payload)) !== eventHash) {
      throw new Error(`control anchor chain is invalid at sequence ${index + 1}`);
    }
    previousHash = eventHash;
  }
  return { file, entries };
}

export async function validateControlAnchor(control, root) {
  if (process.env.QTA_GOVERNANCE_ANCHOR === "off") return [];
  try {
    const { entries } = await readControlAnchors(root, control.taskId);
    if (entries.length === 0) {
      return ["CONTEXT_READY", "CONTRACT_DRAFTED"].includes(control.lifecycleState) ? []
        : ["control anchor is missing; validate and anchor CONTRACT_DRAFTED before advancing"];
    }
    return validateMonotonicControl(entries.at(-1).snapshot, controlAnchorSnapshot(control));
  } catch (error) {
    return [`control anchor cannot be verified (${error.message})`];
  }
}

export async function appendControlAnchor(control, root) {
  if (process.env.QTA_GOVERNANCE_ANCHOR === "off") return;
  const { file, entries } = await readControlAnchors(root, control.taskId);
  const snapshot = controlAnchorSnapshot(control);
  if (entries.length > 0 && JSON.stringify(entries.at(-1).snapshot) === JSON.stringify(snapshot)) return;
  const payload = {
    version: 1,
    sequence: entries.length + 1,
    previousHash: entries.at(-1)?.eventHash ?? null,
    recordedAt: new Date().toISOString(),
    snapshot
  };
  const entry = { ...payload, eventHash: sha256(JSON.stringify(payload)) };
  await mkdir(path.dirname(file), { recursive: true });
  await appendFile(file, `${JSON.stringify(entry)}\n`, { encoding: "utf8", mode: 0o600 });
}

export function controlAnchorWriteGuidance(error) {
  if (!["EPERM", "EACCES"].includes(error?.code)) return null;
  return "control content passed, but .git/qta-governance is not writable; grant scoped permission to "
    + "node scripts/check-ai-task-control.mjs and rerun. Do not disable the anchor.";
}

async function main() {
  const file = process.argv[2];
  if (!file) {
    console.error("Usage: node scripts/check-ai-task-control.mjs <task-control.json>");
    process.exit(2);
  }
  const absolute = path.resolve(file);
  const root = process.cwd();
  const control = JSON.parse(await readFile(absolute, "utf8"));
  const schema = JSON.parse(await readFile(path.join(root, ".agents", "schemas", "qta-task-control.schema.json"), "utf8"));
  const result = validateTaskControl(control);
  result.errors.unshift(...validateJsonSchema(control, schema));
  result.errors.push(...await validateTaskControlFiles(control, root));
  result.errors.push(...await validateControlAnchor(control, root));
  for (const warning of result.warnings) console.warn(`WARN: ${warning}`);
  if (result.errors.length > 0) {
    console.error("Task control validation failed:");
    for (const error of [...new Set(result.errors)]) console.error(`- ${error}`);
    process.exit(1);
  }
  try {
    await appendControlAnchor(control, root);
  } catch (error) {
    const guidance = controlAnchorWriteGuidance(error);
    if (!guidance) throw error;
    console.error(`Task control anchor failed: ${guidance}`);
    process.exit(1);
  }
  console.log(`Task control validation passed: ${control.taskId} ${control.lane} ${control.lifecycleState}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
