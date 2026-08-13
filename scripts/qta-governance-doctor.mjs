#!/usr/bin/env node

import { createHash } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import path from "node:path";
import process from "node:process";

const root = path.resolve(import.meta.dirname, "..");
const requireRuntime = process.argv.includes("--runtime");
const requireActive = process.argv.includes("--require-active");

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

async function gitMetadataDirectory(projectRoot) {
  const dotGit = path.join(projectRoot, ".git");
  try {
    const marker = await readFile(dotGit, "utf8");
    const match = marker.match(/^gitdir:\s*(.+)\s*$/m);
    return match ? path.resolve(projectRoot, match[1]) : dotGit;
  } catch (error) {
    if (error.code === "EISDIR") return dotGit;
    throw error;
  }
}

async function recentDoctorReceipt() {
  const directory = path.join(await gitMetadataDirectory(root), "qta-governance", "doctor");
  let names = [];
  try {
    names = await readdir(directory);
  } catch (error) {
    if (error.code === "ENOENT") return null;
    throw error;
  }
  const receipts = [];
  for (const name of names.filter((item) => item.endsWith(".json"))) {
    try {
      receipts.push(JSON.parse(await readFile(path.join(directory, name), "utf8")));
    } catch {
      // A malformed runtime receipt fails by being ignored.
    }
  }
  return receipts
    .filter((receipt) => receipt.projectRootSha256 === sha256(root))
    .sort((left, right) => Date.parse(right.preToolObservedAt ?? right.promptObservedAt ?? 0)
      - Date.parse(left.preToolObservedAt ?? left.promptObservedAt ?? 0))[0] ?? null;
}

async function hasActiveLock() {
  const directory = path.join(await gitMetadataDirectory(root), "qta-governance", "active");
  try {
    return (await readdir(directory)).some((name) => name.endsWith(".json"));
  } catch (error) {
    if (error.code === "ENOENT") return false;
    throw error;
  }
}

const installCheck = spawnSync(process.execPath,
  [path.join(root, "scripts", "install-zcode-governance-user-hooks.mjs"), "--check"],
  { cwd: root, encoding: "utf8" });
if (installCheck.status !== 0) {
  process.stderr.write(installCheck.stderr || installCheck.stdout);
  process.exit(1);
}

if (requireRuntime) {
  const receipt = await recentDoctorReceipt();
  const promptAge = receipt?.promptObservedAt ? Date.now() - Date.parse(receipt.promptObservedAt) : Infinity;
  const preToolAge = receipt?.preToolObservedAt ? Date.now() - Date.parse(receipt.preToolObservedAt) : Infinity;
  if (!receipt || promptAge > 10 * 60_000 || preToolAge > 10 * 60_000) {
    console.error("QTA runtime doctor failed: this ZCode task did not produce recent UserPromptSubmit and PreToolUse Hook evidence.");
    console.error("Restart ZCode and invoke /qta-doctor or /qta-run from a new task; do not continue role dispatch.");
    process.exit(2);
  }
}

if (requireActive && !await hasActiveLock()) {
  console.error("QTA runtime doctor failed: /qta-run did not create an active governed-task lock.");
  process.exit(2);
}

console.log(`QTA governance doctor PASS (${requireRuntime ? "user-config + runtime" : "user-config"}${requireActive ? " + active-task" : ""}).`);
