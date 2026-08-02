#!/usr/bin/env node

import process from "node:process";
import { createHash, randomUUID } from "node:crypto";
import { spawnSync } from "node:child_process";
import { mkdir, open, readFile, rename, unlink, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

function firstString(object, keys) {
  for (const key of keys) if (typeof object?.[key] === "string") return object[key];
  return "";
}

const QTA_AGENT_ROLES = Object.freeze({
  "qta-test-designer": "TEST_DESIGNER",
  "qta-implementer": "IMPLEMENTER",
  "qta-code-reviewer": "CODE_REVIEWER",
  "qta-final-verifier": "FINAL_VERIFIER"
});

function qtaDispatchMetadata(input) {
  const tool = input?.tool_name ?? input?.toolName ?? "";
  if (!/^(?:Agent|Task)$/.test(tool)) return null;
  const toolInput = input?.tool_input ?? input?.toolInput ?? {};
  const agentName = firstString(toolInput, ["subagent_type", "subagentType", "agent", "name"]);
  const role = QTA_AGENT_ROLES[agentName];
  if (!role) return null;
  const prompt = firstString(toolInput, ["prompt", "input", "task", "description"]);
  const header = prompt.match(/^# Task Packet:\s*([^/\n]+?)\s*\/\s*([^/\n]+?)\s*\/\s*([^\n]+?)\s*$/m);
  const dispatch = prompt.match(/^- Dispatch ID:\s*(\S+)\s*$/m);
  return {
    agentName,
    agentDefinition: `.zcode/agents/${agentName}.md`,
    expectedRole: role,
    prompt,
    taskId: header?.[1]?.trim() ?? "",
    declaredRole: header?.[2]?.trim() ?? "",
    roleRunId: header?.[3]?.trim() ?? "",
    dispatchId: dispatch?.[1]?.trim() ?? ""
  };
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
  const normalized = `/${value.replaceAll("\\", "/").replace(/^\/+/, "")}`;
  return normalized.endsWith("/.zcode/config.json")
    || normalized.includes("/.zcode/agents/")
    || normalized.endsWith("/.zcode/commands/qta-run.md")
    || normalized.endsWith("/scripts/zcode-governance-hook.mjs")
    || normalized.endsWith("/scripts/check-ai-task-control.mjs")
    || normalized.endsWith("/scripts/check-ai-delivery-ready.mjs")
    || normalized.endsWith("/scripts/check-ai-architecture.mjs")
    || normalized.endsWith("/scripts/run-ai-evidence-command.mjs")
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

  const command = path.basename(tokens[0] ?? "");
  const commonFileMutators = new Set([
    "sed", "perl", "tee", "cp", "mv", "truncate", "touch", "install", "patch", "apply_patch"
  ]);
  if (tokens.some(isProtectedGovernancePath)
      && (commonFileMutators.has(command) || tokens.some((token) => /^(?:>|>>|1>|2>)$/.test(token)))) {
    reasons.push("governed roles must not rewrite active governance controls through Bash");
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
  const dispatch = qtaDispatchMetadata(input);

  if (dispatch && (!dispatch.taskId || !dispatch.roleRunId || !dispatch.dispatchId)) {
    reasons.push("QTA specialist dispatch requires a complete Task Packet header and Dispatch ID");
  }
  if (dispatch && dispatch.declaredRole !== dispatch.expectedRole) {
    reasons.push("QTA specialist dispatch role does not match its fixed Agent definition");
  }

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

async function recordDispatchReceipt(input) {
  if (process.env.QTA_GOVERNANCE_AUDIT === "off") return;
  const dispatch = qtaDispatchMetadata(input);
  if (!dispatch) return;
  const parentSessionId = input?.session_id ?? input?.sessionId ?? process.env.CLAUDE_SESSION_ID;
  const projectRoot = process.env.ZCODE_PROJECT_DIR ?? process.env.CLAUDE_PROJECT_DIR
    ?? input?.cwd ?? process.cwd();
  if (!parentSessionId || !projectRoot) throw new Error("QTA dispatch audit requires parent session and project root");
  const directory = path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "dispatches",
    sha256(dispatch.taskId));
  await mkdir(directory, { recursive: true });
  const receiptPath = path.join(directory, `${sha256(dispatch.dispatchId)}.json`);
  const handle = await open(receiptPath, "wx", 0o600);
  try {
    await handle.writeFile(`${JSON.stringify({
      version: 1,
      taskId: dispatch.taskId,
      dispatchId: dispatch.dispatchId,
      roleRunId: dispatch.roleRunId,
      role: dispatch.expectedRole,
      agentDefinition: dispatch.agentDefinition,
      parentSessionId,
      observedAt: new Date().toISOString(),
      projectRootSha256: sha256(path.resolve(projectRoot)),
      promptSha256: sha256(dispatch.prompt),
      nonce: randomUUID()
    }, null, 2)}\n`);
  } finally {
    await handle.close();
  }
  const activeDirectory = path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "active");
  const activePath = path.join(activeDirectory, `${sha256(parentSessionId)}.json`);
  await mkdir(activeDirectory, { recursive: true });
  let active = null;
  try {
    active = JSON.parse(await readFile(activePath, "utf8"));
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  if (active?.taskId && active.taskId !== dispatch.taskId) {
    throw new Error(`parent session already owns active governed task ${active.taskId}`);
  }
  const temporary = `${activePath}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify({
    version: 1,
    taskId: dispatch.taskId,
    controlPath: `docs/development/tasks/${dispatch.taskId}-CONTROL.json`,
    parentSessionId,
    projectRootSha256: sha256(path.resolve(projectRoot)),
    lastDispatchId: dispatch.dispatchId,
    updatedAt: new Date().toISOString()
  }, null, 2)}\n`, { mode: 0o600 });
  await rename(temporary, activePath);
}

async function activateRunPrompt(input) {
  if (process.env.QTA_GOVERNANCE_AUDIT === "off") return;
  const event = input?.hook_event_name ?? input?.hookEventName ?? input?.event ?? "";
  if (event !== "UserPromptSubmit") return;
  const prompt = firstString(input, ["prompt", "user_prompt", "userPrompt"]);
  if (!/(?:^|\s)\/qta-run(?:\s|$)/.test(prompt)) return;
  const parentSessionId = input?.session_id ?? input?.sessionId ?? process.env.CLAUDE_SESSION_ID;
  const projectRoot = process.env.ZCODE_PROJECT_DIR ?? process.env.CLAUDE_PROJECT_DIR
    ?? input?.cwd ?? process.cwd();
  if (!parentSessionId || !projectRoot) throw new Error("/qta-run activation requires session and project root");
  const activeDirectory = path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "active");
  const activePath = path.join(activeDirectory, `${sha256(parentSessionId)}.json`);
  await mkdir(activeDirectory, { recursive: true });
  try {
    const existing = JSON.parse(await readFile(activePath, "utf8"));
    if (existing.taskId) throw new Error(`parent session already owns active governed task ${existing.taskId}`);
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  await writeFile(activePath, `${JSON.stringify({
    version: 1,
    taskId: "",
    controlPath: "",
    parentSessionId,
    projectRootSha256: sha256(path.resolve(projectRoot)),
    lastDispatchId: "",
    updatedAt: new Date().toISOString()
  }, null, 2)}\n`, { mode: 0o600 });
}

async function enforceUnattendedQuestionPolicy(input) {
  const tool = input?.tool_name ?? input?.toolName ?? "";
  if (tool !== "AskUserQuestion") return false;
  const sessionId = input?.session_id ?? input?.sessionId ?? process.env.CLAUDE_SESSION_ID;
  const projectRoot = process.env.ZCODE_PROJECT_DIR ?? process.env.CLAUDE_PROJECT_DIR
    ?? input?.cwd ?? process.cwd();
  if (!sessionId || !projectRoot) return false;
  const activePath = path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "active",
    `${sha256(sessionId)}.json`);
  let active;
  try {
    active = JSON.parse(await readFile(activePath, "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") return false;
    throw error;
  }
  if (active.projectRootSha256 !== sha256(path.resolve(projectRoot))) {
    console.error("QTA unattended policy: active-task receipt belongs to another project");
    process.exit(2);
  }
  console.error("QTA unattended policy blocked AskUserQuestion: choose the documented/recommended reversible option automatically; if safe progress requires product, destructive, credential, or external input, persist an evidence-backed BLOCKED checkpoint instead of waiting for the user");
  process.exit(2);
}

async function enforceStopGate(input) {
  const event = input?.hook_event_name ?? input?.hookEventName ?? input?.event ?? "";
  if (event !== "Stop") return false;
  const sessionId = input?.session_id ?? input?.sessionId ?? process.env.CLAUDE_SESSION_ID;
  const projectRoot = process.env.ZCODE_PROJECT_DIR ?? process.env.CLAUDE_PROJECT_DIR
    ?? input?.cwd ?? process.cwd();
  if (!sessionId || !projectRoot) return true;
  const activePath = path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "active",
    `${sha256(sessionId)}.json`);
  let active;
  try {
    active = JSON.parse(await readFile(activePath, "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") return true;
    throw error;
  }
  if (active.projectRootSha256 !== sha256(path.resolve(projectRoot))) {
    console.error("QTA Stop gate: active-task receipt belongs to another project");
    process.exit(2);
  }
  if (!active.taskId || !active.controlPath) {
    console.error("QTA Stop gate: /qta-run bootstrap has not produced a governed task/control yet. Continue with context, contract, and test-design dispatch or explicitly cancel outside Goal mode.");
    process.exit(2);
  }
  let control;
  try {
    control = JSON.parse(await readFile(path.resolve(projectRoot, active.controlPath), "utf8"));
  } catch (error) {
    console.error(`QTA Stop gate: active control is unavailable (${error.code ?? error.message}); checkpoint BLOCKED or restore the control file`);
    process.exit(2);
  }
  if (control.taskId !== active.taskId) {
    console.error("QTA Stop gate: active task/control identity mismatch");
    process.exit(2);
  }
  if (control.lifecycleState === "BLOCKED") {
    await unlink(activePath);
    return true;
  }
  if (control.lifecycleState !== "DELIVERY_READY") {
    console.error(`QTA Stop gate: ${active.taskId} is ${control.lifecycleState}, not DELIVERY_READY or BLOCKED. Continue the frozen lifecycle; do not declare completion.`);
    process.exit(2);
  }
  const gate = spawnSync(process.execPath, [path.join(projectRoot, "scripts", "check-ai-delivery-ready.mjs"),
    active.controlPath], { cwd: projectRoot, encoding: "utf8", timeout: 30_000 });
  if (gate.status !== 0) {
    const detail = (gate.stderr || gate.stdout || "delivery-ready gate failed").trim();
    console.error(`QTA Stop gate: delivery readiness failed. Continue with repair or checkpoint BLOCKED.\n${detail.slice(-4000)}`);
    process.exit(2);
  }
  await unlink(activePath);
  return true;
}

async function readStdin() {
  let content = "";
  for await (const chunk of process.stdin) content += chunk;
  return content.trim() ? JSON.parse(content) : {};
}

async function main() {
  const input = await readStdin();
  if (await enforceStopGate(input)) return;
  await activateRunPrompt(input);
  await enforceUnattendedQuestionPolicy(input);
  const result = evaluateHook(input);
  if (!result.allowed) {
    console.error(`QTA governance blocked this action: ${result.reasons.join("; ")}`);
    process.exit(2);
  }
  await recordRuntimeReceipt(input);
  await recordDispatchReceipt(input);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
