#!/usr/bin/env node

import { createHash } from "node:crypto";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const DEFAULT_MAX_BYTES = 512 * 1024;
const RUNTIME_PREFIX = ".qta-governance/candidates/";

function usage() {
  console.error("Usage: node scripts/create-candidate-diff.mjs --base <commit> --output .qta-governance/candidates/<task>/<generation>.patch [--candidate <commit> | --manifest <manifest.json>] [--max-bytes <bytes>]");
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function relativePosix(root, value) {
  return path.relative(root, value).split(path.sep).join("/");
}

function gitDiff(root, args, maxBuffer) {
  return execFileSync("git", ["diff", "--binary", "--no-ext-diff", ...args], {
    cwd: root,
    encoding: "buffer",
    maxBuffer
  });
}

const args = process.argv.slice(2);
let root = process.cwd();
let base = "";
let candidate = "";
let manifestPath = "";
let outputPath = "";
let maxBytes = DEFAULT_MAX_BYTES;
for (let index = 0; index < args.length; index += 1) {
  const value = args[index];
  if (value === "--root") root = path.resolve(args[++index] ?? "");
  else if (value === "--base") base = args[++index] ?? "";
  else if (value === "--candidate") candidate = args[++index] ?? "";
  else if (value === "--manifest") manifestPath = args[++index] ?? "";
  else if (value === "--output") outputPath = args[++index] ?? "";
  else if (value === "--max-bytes") maxBytes = Number(args[++index]);
  else {
    usage();
    process.exit(2);
  }
}

if (!base || !outputPath || Boolean(candidate) === Boolean(manifestPath)
    || !Number.isInteger(maxBytes) || maxBytes < 1024 || maxBytes > 2 * 1024 * 1024) {
  usage();
  process.exit(2);
}

const output = path.resolve(root, outputPath);
const relativeOutput = relativePosix(root, output);
if (!relativeOutput.startsWith(RUNTIME_PREFIX) || relativeOutput.includes("../")) {
  console.error(`Candidate diff output must be under ${RUNTIME_PREFIX}`);
  process.exit(2);
}

const maxBuffer = maxBytes + 64 * 1024;
const exclusions = [
  ":(exclude)docs/development/tasks/*.patch",
  ":(exclude).qta-governance/**"
];
let content;
if (candidate) {
  content = gitDiff(root, [base, candidate, "--", ".", ...exclusions], maxBuffer);
} else {
  const manifest = JSON.parse(await readFile(path.resolve(root, manifestPath), "utf8"));
  const paths = [...new Set((manifest.entries ?? []).map((entry) => entry.path)
    .filter((entry) => typeof entry === "string" && entry !== relativeOutput))];
  content = gitDiff(root, [base, "--", ...paths, ...exclusions], maxBuffer);
  const chunks = [content];
  for (const target of paths) {
    const tracked = spawnSync("git", ["ls-files", "--error-unmatch", "--", target], {
      cwd: root,
      stdio: "ignore"
    }).status === 0;
    if (tracked) continue;
    const untracked = spawnSync("git", ["diff", "--binary", "--no-ext-diff", "--no-index", "--", "/dev/null", target], {
      cwd: root,
      encoding: "buffer",
      maxBuffer
    });
    if (![0, 1].includes(untracked.status)) {
      throw new Error(`Cannot generate candidate diff for untracked path ${target}`);
    }
    chunks.push(untracked.stdout ?? Buffer.alloc(0));
  }
  content = Buffer.concat(chunks);
}

if (content.length > maxBytes) {
  console.error(`Candidate diff is ${content.length} bytes, above the ${maxBytes}-byte limit; split the task before review`);
  process.exit(1);
}
await mkdir(path.dirname(output), { recursive: true });
await writeFile(output, content);
console.log(JSON.stringify({
  output: relativeOutput,
  bytes: content.length,
  sha256: sha256(content)
}));
