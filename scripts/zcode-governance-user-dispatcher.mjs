#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { access, readFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

async function readStdin() {
  let content = "";
  for await (const chunk of process.stdin) content += chunk;
  return content.trim() ? JSON.parse(content) : {};
}

async function isGitRoot(directory) {
  try {
    await access(path.join(directory, ".git"));
    return true;
  } catch {
    return false;
  }
}

async function findProjectRoot(startDirectory) {
  let current = path.resolve(startDirectory);
  while (true) {
    if (await isGitRoot(current)) return current;
    const parent = path.dirname(current);
    if (parent === current) return "";
    current = parent;
  }
}

async function main() {
  const input = await readStdin();
  const startDirectory = input?.cwd ?? process.cwd();
  const projectRoot = await findProjectRoot(startDirectory);
  if (!projectRoot) return;

  const projectHook = path.join(projectRoot, "scripts", "zcode-governance-hook.mjs");
  try {
    await readFile(projectHook, "utf8");
  } catch (error) {
    if (error.code === "ENOENT") return;
    throw error;
  }

  const result = spawnSync(process.execPath, [projectHook], {
    cwd: projectRoot,
    encoding: "utf8",
    input: JSON.stringify(input),
    env: {
      ...process.env,
      ZCODE_PROJECT_DIR: projectRoot,
      CLAUDE_PROJECT_DIR: projectRoot,
      CLAUDE_SESSION_ID: input?.session_id ?? input?.sessionId ?? process.env.CLAUDE_SESSION_ID ?? ""
    },
    timeout: 10_000
  });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error) throw result.error;
  process.exitCode = result.status ?? 2;
}

try {
  await main();
} catch (error) {
  console.error(`QTA user Hook dispatcher failed closed: ${error?.message ?? error}`);
  process.exitCode = 2;
}
