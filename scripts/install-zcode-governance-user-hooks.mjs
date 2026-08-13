#!/usr/bin/env node

import { chmod, copyFile, mkdir, readFile, rename, unlink, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import process from "node:process";

const root = path.resolve(import.meta.dirname, "..");
const sourceDispatcher = path.join(root, "scripts", "zcode-governance-user-dispatcher.mjs");
const userDirectory = process.env.ZCODE_USER_CONFIG_DIR
  ? path.resolve(process.env.ZCODE_USER_CONFIG_DIR)
  : path.join(os.homedir(), ".zcode", "cli");
const hookDirectory = path.join(userDirectory, "hooks");
const targetDispatcher = path.join(hookDirectory, "qta-governance-dispatcher.mjs");
const configPath = path.join(userDirectory, "config.json");
const mode = process.argv[2] ?? "--install";

const eventPolicies = Object.freeze({
  UserPromptSubmit: {
    matcher: "QTA_GOVERNED_RUN|QTA_GOVERNANCE_DOCTOR|(^|\\s)/qta-(?:run|doctor)(\\s|$)",
    statusMessage: "Activating or diagnosing QTA governance"
  },
  PreToolUse: {
    matcher: "Bash|Read|Write|Edit|ApplyPatch|Agent|Task|AskUserQuestion",
    statusMessage: "Checking QTA governance policy"
  },
  PostToolUse: {
    matcher: "Agent|Task",
    statusMessage: "Recording QTA role dispatch outcome"
  },
  PostToolUseFailure: {
    matcher: "Agent|Task",
    statusMessage: "Recording failed QTA role dispatch"
  }
});

function isQtaHook(hook) {
  return hook?.type === "process"
    && Array.isArray(hook.args)
    && hook.args.some((argument) => argument === targetDispatcher
      || argument.endsWith("/qta-governance-dispatcher.mjs"));
}

function removeQtaGroups(events = {}) {
  const next = {};
  for (const [event, groups] of Object.entries(events)) {
    const retained = Array.isArray(groups)
      ? groups.filter((group) => !(group?.hooks ?? []).some(isQtaHook))
      : groups;
    if (!Array.isArray(retained) || retained.length > 0) next[event] = retained;
  }
  return next;
}

function qtaGroup(policy) {
  return {
    matcher: policy.matcher,
    hooks: [{
      type: "process",
      command: process.execPath,
      args: [targetDispatcher],
      timeoutMs: 10_000,
      statusMessage: policy.statusMessage
    }]
  };
}

async function readConfig() {
  try {
    return JSON.parse(await readFile(configPath, "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") return {};
    throw new Error(`cannot parse ${configPath}: ${error.message}`);
  }
}

async function check() {
  const errors = [];
  const config = await readConfig();
  if (config?.hooks?.enabled !== true) errors.push("user hooks are not enabled");
  let installedDispatcher = "";
  try {
    installedDispatcher = await readFile(targetDispatcher, "utf8");
  } catch (error) {
    errors.push(`dispatcher is unavailable (${error.code ?? error.message})`);
  }
  const expectedDispatcher = await readFile(sourceDispatcher, "utf8");
  if (installedDispatcher && installedDispatcher !== expectedDispatcher) {
    errors.push("installed dispatcher differs from the repository source");
  }
  for (const [event, policy] of Object.entries(eventPolicies)) {
    const groups = config?.hooks?.events?.[event] ?? [];
    const matching = groups.filter((group) => (group?.hooks ?? []).some(isQtaHook));
    if (matching.length !== 1) errors.push(`${event} must contain exactly one QTA user Hook group`);
    if (matching[0]?.matcher !== policy.matcher) errors.push(`${event} matcher is stale`);
  }
  const stopGroups = config?.hooks?.events?.Stop ?? [];
  if (stopGroups.some((group) => (group?.hooks ?? []).some(isQtaHook))) {
    errors.push("QTA Stop Hook must remain absent");
  }
  if (errors.length > 0) {
    console.error(`QTA ZCode user Hook check failed:\n- ${errors.join("\n- ")}`);
    process.exitCode = 1;
    return;
  }
  console.log(`QTA ZCode user Hook is installed and current: ${configPath}`);
}

async function install() {
  await mkdir(hookDirectory, { recursive: true });
  await copyFile(sourceDispatcher, targetDispatcher);
  await chmod(targetDispatcher, 0o700);

  const config = await readConfig();
  const events = removeQtaGroups(config?.hooks?.events);
  for (const [event, policy] of Object.entries(eventPolicies)) {
    events[event] = [...(events[event] ?? []), qtaGroup(policy)];
  }
  const next = {
    ...config,
    hooks: {
      ...(config.hooks ?? {}),
      enabled: true,
      timeoutMs: config?.hooks?.timeoutMs ?? 10_000,
      maxOutputBytes: config?.hooks?.maxOutputBytes ?? 4096,
      events
    }
  };
  const temporary = `${configPath}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify(next, null, 2)}\n`, { mode: 0o600 });
  await rename(temporary, configPath);
  await chmod(configPath, 0o600);
  console.log(`Installed QTA ZCode user Hook: ${configPath}`);
  console.log("Restart ZCode, then run /qta-doctor in a new task before /qta-run.");
}

async function uninstall() {
  const config = await readConfig();
  const events = removeQtaGroups(config?.hooks?.events);
  const next = {
    ...config,
    hooks: { ...(config.hooks ?? {}), events }
  };
  const temporary = `${configPath}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify(next, null, 2)}\n`, { mode: 0o600 });
  await rename(temporary, configPath);
  try {
    await unlink(targetDispatcher);
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
  console.log("Removed only the QTA ZCode user Hook groups and dispatcher.");
}

if (mode === "--check") await check();
else if (mode === "--install") await install();
else if (mode === "--uninstall") await uninstall();
else throw new Error(`unsupported mode: ${mode}`);
