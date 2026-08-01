#!/usr/bin/env node

import process from "node:process";
import { createHash, randomUUID } from "node:crypto";
import { mkdir, open, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

function firstString(object, keys) {
  for (const key of keys) if (typeof object?.[key] === "string") return object[key];
  return "";
}

function shellTokens(command) {
  return [...command.matchAll(/"(?:\\.|[^"\\])*"|'[^']*'|&&|\|\||[;|]|[^\s;&|]+/g)]
    .map((match) => match[0].replace(/^(?:"|')|(?:"|')$/g, ""));
}

function isSecretPath(value) {
  const normalized = value.replaceAll("\\", "/").replace(/[;|&]+$/, "");
  const base = normalized.slice(normalized.lastIndexOf("/") + 1);
  return /^\.env(?:\..+)?$/.test(base) && base !== ".env.example";
}

function isProtectedGovernancePath(value) {
  const normalized = value.replaceAll("\\", "/");
  return normalized.endsWith("/.zcode/config.json")
    || normalized.endsWith("/scripts/zcode-governance-hook.mjs")
    || normalized.endsWith("/scripts/check-ai-task-control.mjs")
    || normalized.endsWith("/.agents/schemas/qta-task-control.schema.json")
    || normalized.includes("/.agents/skills/qta-development-orchestration/");
}

function commandSegments(tokens) {
  const segments = [];
  let current = [];
  for (const token of tokens) {
    if ([";", "&&", "||", "|"].includes(token)) {
      if (current.length > 0) segments.push(current);
      current = [];
    } else current.push(token);
  }
  if (current.length > 0) segments.push(current);
  return segments;
}

function hasCommandSubstitution(command) {
  return command.includes("$(") || command.includes("`");
}

function gitCommandArgs(tokens, git) {
  const args = tokens.slice(git + 1);
  let cursor = 0;
  while (cursor < args.length) {
    const value = args[cursor];
    if (["-C", "-c", "--git-dir", "--work-tree", "--namespace"].includes(value)) {
      cursor += 2;
    } else if (/^--(?:git-dir|work-tree|namespace)=/.test(value)
        || ["--no-pager", "--paginate", "--literal-pathspecs", "--no-literal-pathspecs"].includes(value)) {
      cursor += 1;
    } else break;
  }
  return args.slice(cursor);
}

function evaluateSegment(tokens, reasons, depth = 0) {
  const shell = tokens.findIndex((token) => /^(?:ba|z|da)?sh$/.test(path.basename(token)));
  if (shell >= 0 && depth < 3) {
    const commandOption = tokens.findIndex((token, index) => index > shell && /^-[a-zA-Z]*c[a-zA-Z]*$/.test(token));
    if (commandOption >= 0 && tokens[commandOption + 1]) {
      reasons.push("nested shell command execution is prohibited in governed runs");
      for (const nested of commandSegments(shellTokens(tokens[commandOption + 1]))) {
        evaluateSegment(nested, reasons, depth + 1);
      }
    }
  }
  const evaluation = ["eval", "source", "."].includes(path.basename(tokens[0] ?? "")) ? 0 : -1;
  if (evaluation >= 0 && depth < 3 && tokens[evaluation + 1]) {
    reasons.push("dynamic shell evaluation is prohibited in governed runs");
    for (const nested of commandSegments(shellTokens(tokens.slice(evaluation + 1).join(" ")))) {
      evaluateSegment(nested, reasons, depth + 1);
    }
  }

  const git = tokens.findIndex((token) => path.basename(token) === "git");
  if (git >= 0) {
    const args = gitCommandArgs(tokens, git);
    const subcommand = args[0];
    if (subcommand === "reset" && args.some((arg) => ["--hard", "--merge", "--keep"].includes(arg))) {
      reasons.push("destructive git reset mode is prohibited");
    }
    if (subcommand === "clean" && args.some((arg) => /^-[^-]*f/.test(arg) || arg === "--force")) {
      reasons.push("git clean --force is prohibited");
    }
    if (subcommand === "checkout") reasons.push("git checkout is prohibited; use git switch for branches");
    if (subcommand === "switch" && ["--discard-changes", "-C", "--force-create", "--force", "-f"]
      .some((option) => args.includes(option))) reasons.push("destructive git switch mode is prohibited");
    if (subcommand === "restore") {
      const stagedOnly = args.includes("--staged") && !args.includes("--worktree");
      if (!stagedOnly) reasons.push("git restore may destructively replace working-tree files");
    }
    if (subcommand === "push") {
      if (args.some((arg) => arg === "-f" || arg.startsWith("--force"))) reasons.push("force push is prohibited");
      const refs = args.filter((arg) => !arg.startsWith("-")).slice(1);
      if (refs.some((ref) => /^(?:refs\/heads\/)?(?:main|master)(?::|$)/.test(ref))) {
        reasons.push("direct default-branch push is prohibited");
      }
    }
    if (subcommand === "branch" && (args.includes("-D")
        || (args.some((arg) => /^-[^-]*d/.test(arg)) && args.some((arg) => /^-[^-]*f/.test(arg))))) {
      reasons.push("forced branch deletion is prohibited");
    }
    if (subcommand === "commit" && args.includes("--amend")) reasons.push("git commit --amend is prohibited");
    if (subcommand === "rebase") reasons.push("git rebase is prohibited in autonomous task runs");
  }

  const rm = tokens.findIndex((token) => path.basename(token) === "rm");
  if (rm >= 0) {
    const flags = tokens.slice(rm + 1).filter((token) => token.startsWith("-")).join("").toLowerCase();
    if (flags.includes("r") && flags.includes("f")) reasons.push("recursive forced deletion is prohibited");
  }

  if (tokens.some(isSecretPath)) reasons.push("shell access to local .env secrets is prohibited");
  if (tokens.some((token) => /^(?:LONGPORT_APP_SECRET|LONGPORT_ACCESS_TOKEN|GITHUB_TOKEN)=.+/.test(token))) {
    reasons.push("commands must not embed credential values");
  }
}

export function evaluateHook(input) {
  const tool = input?.tool_name ?? input?.toolName ?? "";
  const toolInput = input?.tool_input ?? input?.toolInput ?? {};
  const command = firstString(toolInput, ["command", "cmd"]);
  const file = firstString(toolInput, ["file_path", "path", "filePath"]);
  const reasons = [];

  if (/^(?:Read|Write|Edit|ApplyPatch)$/.test(tool) && file) {
    const normalized = file.replaceAll("\\", "/");
    if (isSecretPath(normalized)) reasons.push("AI roles must not read or modify local secret-bearing .env files");
    if (normalized.includes("/.git/qta-governance/")) {
      reasons.push("AI roles must not access the append-only governance audit store directly");
    }
    if (/\/(?:\.zcode\/v2\/config\.json|\.zcode\/v2\/credentials\.json)$/.test(normalized)) {
      reasons.push("AI roles must not access ZCode credential configuration");
    }
    if (/^(?:Write|Edit|ApplyPatch)$/.test(tool) && isProtectedGovernancePath(normalized)) {
      reasons.push("governed roles must not rewrite the active governance controls");
    }
  }

  if (tool === "Bash" && command) {
    if (hasCommandSubstitution(command)) reasons.push("shell command substitution is prohibited in governed runs");
    if (command.includes(".git/qta-governance")) reasons.push("direct governance-audit access is prohibited");
    if (command.includes("QTA_GOVERNANCE_ANCHOR=off") || command.includes("QTA_GOVERNANCE_AUDIT=off")) {
      reasons.push("governed runs must not disable runtime governance evidence");
    }
    for (const segment of commandSegments(shellTokens(command))) evaluateSegment(segment, reasons);
  }
  return { allowed: reasons.length === 0, reasons: [...new Set(reasons)] };
}

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

async function recordRuntimeReceipt(input) {
  if (process.env.QTA_GOVERNANCE_AUDIT === "off") return;
  const sessionId = input?.session_id ?? input?.sessionId ?? process.env.CLAUDE_SESSION_ID;
  const projectRoot = process.env.ZCODE_PROJECT_DIR ?? process.env.CLAUDE_PROJECT_DIR
    ?? input?.cwd ?? process.cwd();
  if (!sessionId || !projectRoot) return;
  const directory = path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "sessions");
  await mkdir(directory, { recursive: true });
  const receiptPath = path.join(directory, `${sha256(sessionId)}.json`);
  let handle;
  try {
    handle = await open(receiptPath, "wx", 0o600);
    await handle.writeFile(`${JSON.stringify({
      version: 1,
      sessionId,
      firstSeenAt: new Date().toISOString(),
      projectRootSha256: sha256(path.resolve(projectRoot)),
      transcriptPathSha256: input?.transcript_path ? sha256(input.transcript_path) : null,
      nonce: randomUUID()
    }, null, 2)}\n`);
  } catch (error) {
    if (error.code !== "EEXIST") throw error;
  } finally {
    await handle?.close();
  }
}

async function readStdin() {
  let content = "";
  for await (const chunk of process.stdin) content += chunk;
  return content.trim() ? JSON.parse(content) : {};
}

async function main() {
  const input = await readStdin();
  await recordRuntimeReceipt(input);
  const result = evaluateHook(input);
  if (result.allowed) return;
  console.error(`QTA governance blocked this action: ${result.reasons.join("; ")}`);
  process.exit(2);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
