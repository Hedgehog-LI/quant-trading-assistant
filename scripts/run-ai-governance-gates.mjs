#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import path from "node:path";
import process from "node:process";

const root = path.resolve(import.meta.dirname, "..");
const gates = [
  [process.execPath, [path.join(root, "scripts", "validate-ai-governance.mjs")]],
  [process.execPath, ["--test", path.join(root, "scripts", "tests", "ai-governance.test.mjs")]]
];

for (const [command, args] of gates) {
  const result = spawnSync(command, args, { cwd: root, encoding: "utf8" });
  process.stdout.write(result.stdout ?? "");
  process.stderr.write(result.stderr ?? "");
  if (result.status !== 0) process.exit(result.status ?? 1);
}

console.log("QTA AI governance gates passed.");
