#!/usr/bin/env node

import { createHash } from "node:crypto";
import { spawnSync, execFileSync } from "node:child_process";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function optionValues(args, name) {
  const values = [];
  for (let index = 0; index < args.length; index += 1) {
    if (args[index] === name && args[index + 1]) values.push(args[index + 1]);
  }
  return values;
}

function optionValue(args, name) {
  return optionValues(args, name).at(-1) ?? "";
}

function candidateFingerprint(root) {
  const head = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  const tree = execFileSync("git", ["rev-parse", "HEAD^{tree}"], { cwd: root, encoding: "utf8" }).trim();
  const status = execFileSync("git", ["status", "--porcelain=v1", "-z", "--untracked-files=all"], {
    cwd: root, encoding: "buffer"
  });
  const diff = execFileSync("git", ["diff", "--binary", "HEAD", "--"], { cwd: root, encoding: "buffer" });
  return { head, tree, statusSha256: sha256(status), diffSha256: sha256(diff) };
}

function outputTail(value, limit = 4000) {
  const text = value?.toString("utf8") ?? "";
  return text.length <= limit ? text : text.slice(-limit);
}

async function observedSelectors(selectors, command, stdout, stderr, reportPaths, root) {
  let searchable = `${command.join(" ")}\n${stdout.toString("utf8")}\n${stderr.toString("utf8")}`;
  const reports = [];
  for (const reportPath of reportPaths) {
    const content = await readFile(path.resolve(root, reportPath));
    reports.push({ path: reportPath, sha256: sha256(content) });
    searchable += `\n${content.toString("utf8")}`;
  }
  return {
    reports,
    observed: selectors.filter((selector) => searchable.includes(selector))
  };
}

function required(value, name) {
  if (!value) throw new Error(`${name} is required`);
  return value;
}

async function main() {
  const separator = process.argv.indexOf("--");
  if (separator < 0 || separator === process.argv.length - 1) {
    throw new Error("Usage: run-ai-evidence-command.mjs <options> -- <executable> [args...]");
  }
  const args = process.argv.slice(2, separator);
  const command = process.argv.slice(separator + 1);
  const root = process.cwd();
  const taskId = required(optionValue(args, "--task-id"), "--task-id");
  const roleRunId = required(optionValue(args, "--role-run-id"), "--role-run-id");
  const testId = required(optionValue(args, "--test-id"), "--test-id");
  const candidateIdentity = required(optionValue(args, "--candidate-identity"), "--candidate-identity");
  const candidateMode = required(optionValue(args, "--candidate-mode"), "--candidate-mode");
  if (!["COMMIT", "SNAPSHOT"].includes(candidateMode)) throw new Error("--candidate-mode must be COMMIT or SNAPSHOT");
  const outputPath = required(optionValue(args, "--output"), "--output");
  const declaredSessionId = required(optionValue(args, "--session-id"), "--session-id");
  const observedSessionId = process.env.ZCODE_SESSION_ID ?? process.env.CLAUDE_SESSION_ID ?? "";
  if (observedSessionId && observedSessionId !== declaredSessionId) {
    throw new Error("declared session ID differs from the runtime session");
  }
  const absoluteOutput = path.resolve(root, outputPath);
  const taskRoot = path.resolve(root, "docs/development/tasks");
  if (!absoluteOutput.startsWith(`${taskRoot}${path.sep}`)) {
    throw new Error("evidence receipt must be written under docs/development/tasks/");
  }
  const selectors = optionValues(args, "--selector");
  if (selectors.length === 0) throw new Error("at least one --selector is required");
  const reportPaths = optionValues(args, "--report-file");
  const timeoutMs = Number.parseInt(optionValue(args, "--timeout-ms") || "900000", 10);

  const before = candidateFingerprint(root);
  if (candidateMode === "COMMIT" && before.head !== candidateIdentity) {
    throw new Error("COMMIT candidate identity does not match verifier worktree HEAD");
  }
  if (candidateMode === "SNAPSHOT") {
    const manifestPath = required(optionValue(args, "--manifest"), "--manifest for SNAPSHOT mode");
    const manifest = await readFile(path.resolve(root, manifestPath));
    if (sha256(manifest) !== candidateIdentity) throw new Error("SNAPSHOT candidate identity does not match manifest");
  }
  const startedAt = new Date().toISOString();
  const run = spawnSync(command[0], command.slice(1), {
    cwd: root,
    encoding: "buffer",
    timeout: timeoutMs,
    maxBuffer: 16 * 1024 * 1024,
    env: process.env
  });
  const finishedAt = new Date().toISOString();
  const stdout = run.stdout ?? Buffer.alloc(0);
  const stderr = run.stderr ?? Buffer.alloc(0);
  const after = candidateFingerprint(root);
  const candidateUnchanged = JSON.stringify(before) === JSON.stringify(after);
  let selectorResult = { reports: [], observed: [] };
  let selectorError = "";
  try {
    selectorResult = await observedSelectors(selectors, command, stdout, stderr, reportPaths, root);
  } catch (error) {
    selectorError = error.message;
  }
  const exitCode = Number.isInteger(run.status) ? run.status : 1;
  const selectorsComplete = selectorResult.observed.length === selectors.length;
  const passed = exitCode === 0 && candidateUnchanged && selectorsComplete && !selectorError;
  const payload = {
    schemaVersion: 1,
    generatedBy: "scripts/run-ai-evidence-command.mjs",
    taskId,
    roleRunId,
    sessionId: observedSessionId || declaredSessionId,
    testId,
    candidateMode,
    candidateIdentity,
    command,
    startedAt,
    finishedAt,
    exitCode,
    result: passed ? "PASS" : "FAIL",
    timedOut: run.error?.code === "ETIMEDOUT",
    stdoutSha256: sha256(stdout),
    stderrSha256: sha256(stderr),
    reportFiles: selectorResult.reports,
    expectedSelectors: selectors,
    observedSelectors: selectorResult.observed,
    selectorError,
    candidateBefore: before,
    candidateAfter: after,
    candidateUnchanged
  };
  await mkdir(path.dirname(absoluteOutput), { recursive: true });
  const temporary = `${absoluteOutput}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify(payload, null, 2)}\n`, { mode: 0o600 });
  await rename(temporary, absoluteOutput);

  if (!passed) {
    const tail = [outputTail(stdout), outputTail(stderr)].filter(Boolean).join("\n");
    if (tail) process.stderr.write(`${tail}\n`);
  }
  console.log(`Evidence command ${passed ? "PASS" : "FAIL"}: ${testId}; receipt=${outputPath}; exit=${exitCode}; candidateUnchanged=${candidateUnchanged}; selectors=${selectorResult.observed.length}/${selectors.length}`);
  if (!passed) process.exit(1);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(`Evidence command failed: ${error.message}`);
    process.exit(2);
  });
}
