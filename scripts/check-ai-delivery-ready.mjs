#!/usr/bin/env node

import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readdirSync, readFileSync } from "node:fs";
import { readFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

import {
  validateControlAnchor,
  validateJsonSchema,
  validateTaskControl,
  validateTaskControlFiles
} from "./check-ai-task-control.mjs";

function present(value) {
  return typeof value === "string" && value.trim() !== "" && !value.includes("<");
}

function unique(values) {
  return [...new Set(values.filter(present).map((value) => value.replaceAll("\\", "/")))];
}

function requiredArtifactPaths(control) {
  return unique([
    control.controlPath,
    control.contract?.path,
    control.candidate?.manifestPath,
    control.review?.omitted ? "" : control.review?.artifactPath,
    control.architectureGate?.required ? control.architectureGate?.reportPath : "",
    control.verification?.artifactPath,
    control.finalization?.artifactPath,
    ...(control.roleRuns ?? []).filter((run) => run.artifactAccepted).map((run) => run.artifactPath),
    ...(control.testEvidence ?? []).map((item) => item.receiptPath),
    ...(control.evidence ?? []).map((item) => item.artifactPath),
    ...(control.finalization?.changedPaths ?? [])
  ]);
}

function gitOutput(root, args, encoding = "utf8") {
  return execFileSync("git", args, { cwd: root, encoding, stdio: ["ignore", "pipe", "pipe"] });
}

function dirtyPaths(root) {
  const records = gitOutput(root, ["status", "--porcelain=v1", "-z", "--untracked-files=all"])
    .split("\0").filter(Boolean);
  const paths = [];
  for (let index = 0; index < records.length; index += 1) {
    const record = records[index];
    const status = record.slice(0, 2);
    paths.push(record.slice(3).replaceAll("\\", "/"));
    if (/[RC]/.test(status)) index += 1;
  }
  return paths;
}

function isTracked(root, relative) {
  try {
    gitOutput(root, ["ls-files", "--error-unmatch", "--", relative]);
    return true;
  } catch {
    return false;
  }
}

export function dispatchAuditErrors(control, root) {
  const errors = [];
  const taskHash = createHash("sha256").update(control.taskId ?? "").digest("hex");
  const relative = gitOutput(root, ["rev-parse", "--git-path", `qta-governance/dispatches/${taskHash}`]).trim();
  const directory = path.resolve(root, relative);
  let receipts = [];
  try {
    receipts = readdirSync(directory)
      .filter((name) => name.endsWith(".json") && !name.endsWith(".outcome.json"))
      .map((name) => JSON.parse(readFileSync(path.join(directory, name), "utf8")));
  } catch (error) {
    errors.push(`dispatch audit directory is unavailable (${error.code ?? error.message})`);
    return errors;
  }
  const receiptByDispatch = new Map(receipts.map((receipt) => [receipt.dispatchId, receipt]));
  const recorded = new Set((control.roleRuns ?? []).map((run) => run.dispatchId).filter(present));
  for (const dispatchId of recorded) {
    const receipt = receiptByDispatch.get(dispatchId);
    if (!receipt) {
      errors.push(`roleRuns contains a dispatch without Hook audit: ${dispatchId}`);
      continue;
    }
    if (receipt.taskId !== control.taskId) errors.push(`dispatch audit contains another task: ${receipt.dispatchId}`);
    if (receipt.version === 2) {
      const outcomePath = path.join(directory,
        `${createHash("sha256").update(receipt.dispatchId ?? "").digest("hex")}.outcome.json`);
      try {
        const outcome = JSON.parse(readFileSync(outcomePath, "utf8"));
        if (!new Set(["SUCCEEDED", "FAILED"]).has(outcome.status)
            || outcome.taskId !== receipt.taskId || outcome.dispatchId !== receipt.dispatchId
            || outcome.roleRunId !== receipt.roleRunId || outcome.parentSessionId !== receipt.parentSessionId
            || outcome.promptSha256 !== receipt.promptSha256 || outcome.toolUseId !== receipt.toolUseId) {
          errors.push(`dispatch outcome does not match pending receipt: ${receipt.dispatchId}`);
        }
      } catch (error) {
        errors.push(`dispatch remains PENDING without a terminal outcome: ${receipt.dispatchId} (${error.code ?? error.message})`);
      }
    }
  }
  return errors;
}

export function validateDeliveryReadiness(control, options = {}) {
  const errors = [];
  const warnings = [];
  const root = options.root ?? process.cwd();
  if (control?.schemaVersion !== 3) {
    errors.push(`delivery requires task-control schemaVersion=3; found ${control?.schemaVersion ?? "missing"}`);
    if (control?.lifecycleState !== "DELIVERY_READY") errors.push("delivery lifecycleState must be DELIVERY_READY");
    if (!(control?.contract?.implementationSlices?.length > 0)) errors.push("legacy control has no frozen implementation slices");
    if (!(control?.contract?.testInventory?.length > 0) || !(control?.testEvidence?.length > 0)) {
      errors.push("legacy control has no frozen test inventory with machine receipts");
    }
    if (!control?.architectureGate || control.architectureGate.errorCount !== 0
        || control.architectureGate.status !== "PASS") {
      errors.push("legacy control has no passing candidate-bound architecture report");
    }
    if ((control?.roleRuns ?? []).some((run) => /PARENT-(?:IMPLEMENTER|REVIEWER|VERIFIER)|parent[- ]run/i
      .test(run?.compensatingIsolation ?? ""))) {
      errors.push("legacy role ledger explicitly declares parent substitution; specialist independence is invalid");
    }
    if (control?.finalization?.artifactPath === control?.verification?.artifactPath
        || control?.finalization?.artifactSha256 === control?.verification?.artifactSha256) {
      errors.push("finalization artifact must be distinct from verification evidence");
    }
    const timestamps = [control?.startedAt, ...(control?.transitionHistory ?? []).map((item) => item.at),
      ...(control?.roleRuns ?? []).flatMap((run) => [run.startedAt, run.finishedAt])];
    if (timestamps.some((value) => Number.isFinite(Date.parse(value)) && Date.parse(value) > Date.now() + 300_000)) {
      errors.push("legacy control contains future timestamps");
    }
    if (options.checkGit !== false) {
      for (const artifact of requiredArtifactPaths(control)) {
        if (!isTracked(root, artifact)) errors.push(`delivery artifact is not tracked by Git: ${artifact}`);
      }
    }
    return { errors: [...new Set(errors)], warnings };
  }
  errors.push(...validateTaskControl(control).errors);
  if (control?.lifecycleState !== "DELIVERY_READY") errors.push("delivery lifecycleState must be DELIVERY_READY");
  if (control?.verification?.deliveryPermitted !== true) errors.push("delivery requires deliveryPermitted=true");
  if (control?.finalization?.status !== "COMPLETED") errors.push("delivery requires completed finalization");
  if (control?.finalization?.artifactPath === control?.verification?.artifactPath
      || control?.finalization?.artifactSha256 === control?.verification?.artifactSha256) {
    errors.push("finalization artifact must be distinct from verification evidence");
  }
  if ((control?.roleRuns ?? []).some((run) => run.executorType === "PARENT" && run.artifactAccepted)) {
    errors.push("delivery cannot use a parent-authored specialist artifact");
  }
  if ((control?.roleRuns ?? []).some((run) => /PARENT-(?:IMPLEMENTER|REVIEWER|VERIFIER)|parent[- ]run/i
    .test(run?.compensatingIsolation ?? ""))) {
    errors.push("legacy role ledger explicitly declares parent substitution; specialist independence is invalid");
  }
  if ((control?.roleRuns ?? []).some((run) => run.executionOutcome === "PLAN_ONLY" && run.artifactAccepted)) {
    errors.push("delivery cannot use a plan-only verification artifact");
  }
  if (control?.architectureGate?.errorCount !== 0 || control?.architectureGate?.status !== "PASS") {
    errors.push("delivery requires architecture errors=0 and status=PASS");
  }

  if (options.checkGit !== false) {
    const artifacts = requiredArtifactPaths(control);
    for (const artifact of artifacts) {
      if (!isTracked(root, artifact)) errors.push(`delivery artifact is not tracked by Git: ${artifact}`);
    }
    errors.push(...dispatchAuditErrors(control, root));
    const allowedDirty = new Set(control?.git?.preExistingDirtyPaths ?? []);
    const unapprovedDirty = dirtyPaths(root).filter((dirty) => !allowedDirty.has(dirty));
    for (const dirty of unapprovedDirty.slice(0, 10)) {
      errors.push(`delivery worktree has an unapproved dirty path: ${dirty}`);
    }
    if (unapprovedDirty.length > 10) {
      errors.push(`delivery worktree has ${unapprovedDirty.length - 10} additional unapproved dirty paths`);
    }
    try {
      const branch = gitOutput(root, ["branch", "--show-current"]).trim();
      if (branch !== control?.git?.branch) errors.push(`delivery branch mismatch: expected ${control?.git?.branch}, found ${branch}`);
      if (control?.candidate?.mode === "COMMIT") {
        execFileSync("git", ["merge-base", "--is-ancestor", control.candidate.commit, "HEAD"], {
          cwd: root, stdio: "ignore"
        });
      }
    } catch (error) {
      errors.push(`delivery Git ancestry cannot be verified (${error.status ?? error.message})`);
    }
  }
  return { errors: [...new Set(errors)], warnings };
}

async function main() {
  const file = process.argv[2];
  if (!file) {
    console.error("Usage: node scripts/check-ai-delivery-ready.mjs <task-control.json>");
    process.exit(2);
  }
  const root = process.cwd();
  const absolute = path.resolve(root, file);
  const control = JSON.parse(await readFile(absolute, "utf8"));
  const schema = JSON.parse(await readFile(path.join(root, ".agents/schemas/qta-task-control.schema.json"), "utf8"));
  const result = validateDeliveryReadiness(control, { root, checkGit: true });
  if (control.schemaVersion === 3) {
    result.errors.unshift(...validateJsonSchema(control, schema));
    result.errors.push(...await validateTaskControlFiles(control, root));
    result.errors.push(...await validateControlAnchor(control, root));
  }
  const relative = path.relative(root, absolute).replaceAll("\\", "/");
  if (control.controlPath !== relative) result.errors.push("controlPath does not identify the checked control file");
  if (result.errors.length > 0) {
    console.error("AI delivery readiness failed:");
    for (const error of [...new Set(result.errors)]) console.error(`- ${error}`);
    process.exit(1);
  }
  console.log(`AI delivery ready: ${control.taskId}; candidate=${control.candidate.identity}; branch=${control.git.branch}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
