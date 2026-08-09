#!/usr/bin/env node

import process from "node:process";
import { createHash, randomUUID } from "node:crypto";
import { spawnSync } from "node:child_process";
import { mkdir, open, readFile, readdir, rename, unlink, writeFile } from "node:fs/promises";
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
const MAX_ROLE_RUNS_BY_LANE = Object.freeze({ L0: 4, L1: 10, L2: 14, L3: 18 });

export function qtaDispatchMetadata(input) {
  const tool = input?.tool_name ?? input?.toolName ?? "";
  if (!/^(?:Agent|Task)$/.test(tool)) return null;
  const toolInput = input?.tool_input ?? input?.toolInput ?? {};
  const agentName = firstString(toolInput, ["subagent_type", "subagentType", "agent", "name"]);
  const role = QTA_AGENT_ROLES[agentName];
  if (!role) return null;
  const prompt = firstString(toolInput, ["prompt", "input", "task", "description"]);
  const prefix = prompt.match(/^# Task Packet:\s*([^/\r\n]+?)\s*\/\s*([^/\r\n]+?)\s*\/\s*([^\r\n]+?)\s*\r?\n- Dispatch ID:\s*(\S+)\s*(?:\r?\n|$)/);
  return {
    agentName,
    agentDefinition: `.zcode/agents/${agentName}.md`,
    expectedRole: role,
    prompt,
    taskId: prefix?.[1]?.trim() ?? "",
    declaredRole: prefix?.[2]?.trim() ?? "",
    roleRunId: prefix?.[3]?.trim() ?? "",
    dispatchId: prefix?.[4]?.trim() ?? ""
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
    || normalized.endsWith("/scripts/create-candidate-diff.mjs")
    || normalized.endsWith("/scripts/create-candidate-manifest.mjs")
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
      if (refs.some((ref) => {
        const normalized = ref.replace(/^\+/, "");
        return /^(?:refs\/heads\/)?(?:main|master)(?::|$)/.test(normalized)
          || /:(?:refs\/heads\/)?(?:main|master)$/.test(normalized);
      })) {
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
  if (tokens.some((token) => token.replaceAll("\\", "/").endsWith("scripts/zcode-governance-hook.mjs"))) {
    reasons.push("do not execute the governance Hook manually; correct the blocked tool input and retry it once");
  }
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
    reasons.push("QTA specialist dispatch must start with exactly '# Task Packet: <TASK-ID> / <ROLE> / <ROLE-RUN-ID>' followed by '- Dispatch ID: <DISPATCH-ID>' on its own line; correct the Agent prompt once and do not invoke the Hook manually");
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

function hookEvent(input) {
  return input?.hook_event_name ?? input?.hookEventName ?? input?.event ?? "";
}

function inputSessionId(input) {
  return input?.session_id ?? input?.sessionId ?? process.env.CLAUDE_SESSION_ID;
}

function inputProjectRoot(input) {
  return process.env.ZCODE_PROJECT_DIR ?? process.env.CLAUDE_PROJECT_DIR
    ?? input?.cwd ?? process.cwd();
}

function requestedResumeTask(prompt) {
  return prompt.match(/(?:^|\s)\/qta-run\s+--resume\s+([A-Za-z0-9._-]+)(?:\s|$)/)?.[1] ?? "";
}

async function governanceActiveDirectory(projectRoot) {
  return path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "active");
}

async function activeEntries(projectRoot) {
  const directory = await governanceActiveDirectory(projectRoot);
  let names = [];
  try {
    names = await readdir(directory);
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
  const entries = [];
  for (const name of names.filter((item) => item.endsWith(".json"))) {
    const file = path.join(directory, name);
    try {
      entries.push({ file, value: JSON.parse(await readFile(file, "utf8")) });
    } catch {
      // Preserve malformed evidence for explicit diagnosis.
    }
  }
  return entries;
}

async function reconcileTerminalActiveLocks(projectRoot) {
  for (const entry of await activeEntries(projectRoot)) {
    const active = entry.value;
    if (!active?.taskId || !active?.controlPath) continue;
    let control;
    try {
      control = JSON.parse(await readFile(path.resolve(projectRoot, active.controlPath), "utf8"));
    } catch {
      continue;
    }
    if (control.taskId !== active.taskId) continue;
    if (control.lifecycleState === "BLOCKED") {
      await unlink(entry.file);
      continue;
    }
    if (control.lifecycleState === "DELIVERY_READY") {
      const gate = spawnSync(process.execPath, [path.join(projectRoot, "scripts", "check-ai-delivery-ready.mjs"),
        active.controlPath], { cwd: projectRoot, encoding: "utf8", timeout: 30_000 });
      if (gate.status === 0) await unlink(entry.file);
    }
  }
}

function currentBranch(projectRoot) {
  const result = spawnSync("git", ["branch", "--show-current"], {
    cwd: projectRoot, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"]
  });
  return result.status === 0 ? result.stdout.trim() : "";
}

function mutatesDefaultBranch(input) {
  const tool = input?.tool_name ?? input?.toolName ?? "";
  const toolInput = input?.tool_input ?? input?.toolInput ?? {};
  if (/^(?:Write|Edit|ApplyPatch)$/.test(tool)) return true;
  if (tool !== "Bash") return false;
  const commandText = firstString(toolInput, ["command", "cmd"]);
  const fileMutators = new Set([
    "sed", "perl", "tee", "cp", "mv", "truncate", "touch", "install", "patch", "apply_patch", "rm"
  ]);
  for (const segment of commandSegments(shellTokens(commandText))) {
    if (fileMutators.has(path.basename(segment[0] ?? ""))) return true;
    if (segment.some((token) => /^(?:[12]?)>{1,2}/.test(token))) return true;
    const git = segment.findIndex((token) => path.basename(token) === "git");
    if (git < 0) continue;
    const args = gitCommandArgs(segment, git);
    const subcommand = args[0] ?? "";
    const readOnly = new Set([
      "status", "diff", "log", "show", "rev-parse", "ls-files", "ls-tree", "cat-file", "grep",
      "describe", "merge-base", "name-rev", "shortlog", "blame"
    ]).has(subcommand)
      || (subcommand === "branch" && (args.length === 1
        || args.slice(1).every((arg) => /^(?:--show-current|--list|-a|--all|-r|--remotes|-v|--verbose)$/.test(arg))))
      || (subcommand === "remote" && (args.length === 1
        || args.slice(1).every((arg) => /^(?:-v|--verbose)$/.test(arg))));
    const switchTarget = args.filter((arg) => !arg.startsWith("-")).at(-1) ?? "";
    const taskBranchEscape = subcommand === "switch" && /^codex\//.test(switchTarget)
      && !args.some((arg) => ["--discard-changes", "-C", "--force-create", "--force", "-f"].includes(arg));
    if (!readOnly && !taskBranchEscape) return true;
  }
  return false;
}

async function enforceGovernedBranchPolicy(input) {
  if (hookEvent(input) !== "PreToolUse") return;
  const projectRoot = inputProjectRoot(input);
  const projectHash = sha256(path.resolve(projectRoot));
  const governed = (await activeEntries(projectRoot))
    .some((entry) => entry.value?.projectRootSha256 === projectHash);
  if (!governed || !/^(?:main|master)$/.test(currentBranch(projectRoot)) || !mutatesDefaultBranch(input)) return;
  console.error("QTA governance blocked a write on the protected default branch; create or switch to the frozen codex/* task branch before editing, staging, committing, or merging");
  process.exit(2);
}

async function recordRuntimeReceipt(input) {
  if (process.env.QTA_GOVERNANCE_AUDIT === "off") return;
  const sessionId = inputSessionId(input);
  const projectRoot = inputProjectRoot(input);
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
  if (hookEvent(input) !== "PreToolUse") return;
  const dispatch = qtaDispatchMetadata(input);
  if (!dispatch) return;
  const parentSessionId = inputSessionId(input);
  const projectRoot = inputProjectRoot(input);
  if (!parentSessionId || !projectRoot) throw new Error("QTA dispatch audit requires parent session and project root");
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
  const controlPath = active?.controlPath || `docs/development/tasks/${dispatch.taskId}-CONTROL.json`;
  if (active) {
    try {
      const control = JSON.parse(await readFile(path.resolve(projectRoot, controlPath), "utf8"));
      const allowedByState = {
        CONTRACT_DRAFTED: new Set(["TEST_DESIGNER"]),
        CONTRACT_FROZEN: new Set(["IMPLEMENTER"]),
        IMPLEMENTING: new Set(["IMPLEMENTER"]),
        CANDIDATE_FROZEN: new Set(control.lane === "L0" ? ["FINAL_VERIFIER"] : ["CODE_REVIEWER"]),
        REVIEW_CLEAR: new Set(["FINAL_VERIFIER"])
      };
      if (!(allowedByState[control.lifecycleState] ?? new Set()).has(dispatch.expectedRole)) {
        throw new Error(`role ${dispatch.expectedRole} is not allowed while ${dispatch.taskId} is ${control.lifecycleState}`);
      }
      const maxRoleRuns = MAX_ROLE_RUNS_BY_LANE[control.lane];
      if (!Number.isInteger(maxRoleRuns) || !Array.isArray(control.roleRuns)) {
        throw new Error("task control must declare a valid lane and roleRuns ledger before dispatch");
      }
      if (control.roleRuns.length >= maxRoleRuns) {
        throw new Error(`${control.lane} role-run budget is exhausted; checkpoint BLOCKED instead of dispatching another role`);
      }
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
      if (dispatch.expectedRole !== "TEST_DESIGNER") {
        throw new Error(`task control must exist before dispatching ${dispatch.expectedRole}`);
      }
    }
  }
  const directory = path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "dispatches",
    sha256(dispatch.taskId));
  await mkdir(directory, { recursive: true });
  for (const name of await readdir(directory)) {
    if (!name.endsWith(".json") || name.endsWith(".outcome.json")) continue;
    try {
      await readFile(path.join(directory, name.replace(/\.json$/, ".outcome.json")), "utf8");
    } catch (error) {
      if (error.code === "ENOENT") {
        throw new Error("another fixed-role dispatch is still PENDING; wait for its terminal outcome before dispatching the next role");
      }
      throw error;
    }
  }
  const receiptPath = path.join(directory, `${sha256(dispatch.dispatchId)}.json`);
  const handle = await open(receiptPath, "wx", 0o600);
  try {
    await handle.writeFile(`${JSON.stringify({
      version: 2,
      taskId: dispatch.taskId,
      dispatchId: dispatch.dispatchId,
      roleRunId: dispatch.roleRunId,
      role: dispatch.expectedRole,
      agentDefinition: dispatch.agentDefinition,
      parentSessionId,
      observedAt: new Date().toISOString(),
      projectRootSha256: sha256(path.resolve(projectRoot)),
      promptSha256: sha256(dispatch.prompt),
      toolUseId: input?.tool_use_id ?? input?.toolUseId ?? null,
      status: "PENDING",
      nonce: randomUUID()
    }, null, 2)}\n`);
  } finally {
    await handle.close();
  }
  const temporary = `${activePath}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify({
    version: 1,
    taskId: dispatch.taskId,
    controlPath,
    parentSessionId,
    projectRootSha256: sha256(path.resolve(projectRoot)),
    lastDispatchId: dispatch.dispatchId,
    updatedAt: new Date().toISOString()
  }, null, 2)}\n`, { mode: 0o600 });
  await rename(temporary, activePath);
}

async function recordDispatchOutcome(input) {
  if (process.env.QTA_GOVERNANCE_AUDIT === "off") return;
  const event = hookEvent(input);
  if (!new Set(["PostToolUse", "PostToolUseFailure"]).has(event)) return;
  const dispatch = qtaDispatchMetadata(input);
  if (!dispatch) return;
  const parentSessionId = inputSessionId(input);
  const projectRoot = inputProjectRoot(input);
  if (!parentSessionId || !projectRoot) throw new Error("QTA dispatch outcome requires parent session and project root");
  const directory = path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "dispatches",
    sha256(dispatch.taskId));
  const dispatchHash = sha256(dispatch.dispatchId);
  let receipt;
  try {
    receipt = JSON.parse(await readFile(path.join(directory, `${dispatchHash}.json`), "utf8"));
  } catch (error) {
    // A rejected PreToolUse has no pending receipt. Do not turn its failure event into a second Hook failure.
    if (error.code === "ENOENT" && event === "PostToolUseFailure") return;
    throw error;
  }
  const toolUseId = input?.tool_use_id ?? input?.toolUseId ?? null;
  if (receipt.parentSessionId !== parentSessionId || receipt.promptSha256 !== sha256(dispatch.prompt)
      || receipt.toolUseId !== toolUseId) {
    throw new Error("QTA dispatch outcome does not match its pending receipt");
  }
  const outcomePath = path.join(directory, `${dispatchHash}.outcome.json`);
  const expectedStatus = event === "PostToolUse" ? "SUCCEEDED" : "FAILED";
  let handle;
  try {
    handle = await open(outcomePath, "wx", 0o600);
  } catch (error) {
    if (error.code === "EEXIST") {
      const existing = JSON.parse(await readFile(outcomePath, "utf8"));
      if (existing.taskId === dispatch.taskId && existing.dispatchId === dispatch.dispatchId
          && existing.roleRunId === dispatch.roleRunId && existing.parentSessionId === parentSessionId
          && existing.promptSha256 === receipt.promptSha256 && existing.toolUseId === toolUseId
          && existing.status === expectedStatus) return;
      throw new Error("QTA dispatch outcome already exists with different binding");
    }
    throw error;
  }
  try {
    await handle.writeFile(`${JSON.stringify({
      version: 1,
      taskId: dispatch.taskId,
      dispatchId: dispatch.dispatchId,
      roleRunId: dispatch.roleRunId,
      parentSessionId,
      projectRootSha256: sha256(path.resolve(projectRoot)),
      promptSha256: receipt.promptSha256,
      toolUseId,
      status: expectedStatus,
      observedAt: new Date().toISOString(),
      nonce: randomUUID()
    }, null, 2)}\n`);
  } finally {
    await handle.close();
  }
}

async function activateRunPrompt(input) {
  if (process.env.QTA_GOVERNANCE_AUDIT === "off") return;
  const event = hookEvent(input);
  if (event !== "UserPromptSubmit") return;
  const prompt = firstString(input, ["prompt", "user_prompt", "userPrompt"]);
  if (!/(?:^|\s)\/qta-run(?:\s|$)/.test(prompt)) return;
  const parentSessionId = inputSessionId(input);
  const projectRoot = inputProjectRoot(input);
  if (!parentSessionId || !projectRoot) throw new Error("/qta-run activation requires session and project root");
  await reconcileTerminalActiveLocks(projectRoot);
  const projectHash = sha256(path.resolve(projectRoot));
  const resumeTaskId = requestedResumeTask(prompt);
  const anotherActiveTask = (await activeEntries(projectRoot)).find((entry) =>
    entry.value?.projectRootSha256 === projectHash && entry.value?.parentSessionId !== parentSessionId);
  if (anotherActiveTask) {
    const active = anotherActiveTask.value;
    if (!resumeTaskId || resumeTaskId !== active.taskId || !active.controlPath) {
      throw new Error(`project already has active governed task ${active.taskId || "BOOTSTRAPPING"}; continue its parent session or explicitly run '/qta-run --resume ${active.taskId || "<TASK-ID>"} <objective-or-control-path>' from the replacement session`);
    }
    let control;
    try {
      control = JSON.parse(await readFile(path.resolve(projectRoot, active.controlPath), "utf8"));
    } catch (error) {
      throw new Error(`cannot resume ${resumeTaskId}: active control is unavailable (${error.code ?? error.message})`);
    }
    if (control.taskId !== resumeTaskId || new Set(["BLOCKED", "DELIVERY_READY"]).has(control.lifecycleState)) {
      throw new Error(`cannot resume ${resumeTaskId}: control identity or lifecycle state is not resumable`);
    }
    const activeDirectory = path.join(await gitMetadataDirectory(projectRoot), "qta-governance", "active");
    const replacementPath = path.join(activeDirectory, `${sha256(parentSessionId)}.json`);
    const temporary = `${replacementPath}.tmp-${process.pid}`;
    await writeFile(temporary, `${JSON.stringify({
      ...active,
      version: 1,
      parentSessionId,
      projectRootSha256: projectHash,
      resumedFromSessionSha256: sha256(active.parentSessionId ?? ""),
      updatedAt: new Date().toISOString()
    }, null, 2)}\n`, { mode: 0o600 });
    await rename(temporary, replacementPath);
    await unlink(anotherActiveTask.file);
    return;
  }
  if (resumeTaskId) throw new Error(`cannot resume ${resumeTaskId}: no matching active governed task exists`);
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
  const projectHash = sha256(path.resolve(projectRoot));
  const activeEntry = (await activeEntries(projectRoot)).find((entry) =>
    entry.value?.projectRootSha256 === projectHash);
  if (!activeEntry) return false;
  const taskId = activeEntry.value?.taskId || "the active governed task";
  console.error(`QTA unattended policy blocked AskUserQuestion for ${taskId}: choose the documented/recommended reversible option automatically; if safe progress requires product, destructive, credential, or external input, persist an evidence-backed BLOCKED checkpoint instead of waiting for the user`);
  process.exit(2);
}

async function readStdin() {
  let content = "";
  for await (const chunk of process.stdin) content += chunk;
  return content.trim() ? JSON.parse(content) : {};
}

async function main() {
  const input = await readStdin();
  await activateRunPrompt(input);
  await enforceUnattendedQuestionPolicy(input);
  await enforceGovernedBranchPolicy(input);
  if (hookEvent(input) === "PreToolUse" || !hookEvent(input)) {
    const result = evaluateHook(input);
    if (!result.allowed) {
      console.error(`QTA governance blocked this action: ${result.reasons.join("; ")}`);
      process.exit(2);
    }
  }
  await recordRuntimeReceipt(input);
  await recordDispatchReceipt(input);
  await recordDispatchOutcome(input);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(`QTA governance Hook failed closed: ${error?.message ?? error}`);
    process.exitCode = 2;
  });
}
