#!/usr/bin/env node

import { readFile, readdir, stat } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import path from "node:path";
import process from "node:process";

const root = path.resolve(import.meta.dirname, "..");
const canonicalRoot = path.join(root, ".agents", "skills");
const compatibilityRoot = path.join(root, ".claude", "skills");
const agentRoot = path.join(root, ".zcode", "agents");

const manifest = JSON.parse(await readFile(
  path.join(root, ".agents", "skill-manifest.json"),
  "utf8"
));
const expectedSkills = manifest.skills.map((skill) => skill.id);

const expectedAgentPolicies = {
  "qta-test-designer": {
    permissionMode: "plan",
    maxTurns: "8",
    tools: ["Read", "Glob", "Grep", "Skill"],
    disallowedTools: [
      "Bash", "Edit", "Write", "ApplyPatch", "NotebookEdit",
      "Agent", "Task", "EnterPlanMode", "ExitPlanMode"
    ],
    skills: ["qta-context-bootstrap", "qta-task-contract"]
  },
  "qta-implementer": {
    permissionMode: "bypassPermissions",
    maxTurns: "20",
    tools: ["Read", "Glob", "Grep", "Edit", "Write", "Bash", "Skill"],
    disallowedTools: ["Agent", "Task", "EnterPlanMode", "ExitPlanMode"],
    skills: [
      "qta-context-bootstrap", "qta-backend-implementation",
      "qta-frontend-implementation", "qta-task-checkpoint"
    ]
  },
  "qta-code-reviewer": {
    permissionMode: "plan",
    maxTurns: "10",
    tools: ["Read", "Glob", "Grep", "Skill"],
    disallowedTools: [
      "Bash", "Edit", "Write", "ApplyPatch", "NotebookEdit",
      "Agent", "Task", "EnterPlanMode", "ExitPlanMode"
    ],
    skills: ["qta-context-bootstrap"]
  },
  "qta-final-verifier": {
    permissionMode: "bypassPermissions",
    maxTurns: "12",
    tools: ["Read", "Glob", "Grep", "Bash", "Skill"],
    disallowedTools: [
      "Edit", "Write", "ApplyPatch", "NotebookEdit",
      "Agent", "Task", "EnterPlanMode", "ExitPlanMode"
    ],
    skills: ["qta-context-bootstrap", "qta-independent-verification"]
  }
};
const expectedAgents = Object.keys(expectedAgentPolicies);

const errors = [];

function frontmatter(content) {
  const match = content.match(/^---\n([\s\S]*?)\n---\n/);
  return match?.[1] ?? "";
}

function scalar(yaml, key) {
  const match = yaml.match(new RegExp(`^\\s*${key}:\\s*[\"']?([^\\n\"']+)[\"']?\\s*$`, "m"));
  return match?.[1]?.trim();
}

function topLevelKeys(yaml) {
  return [...yaml.matchAll(/^([a-zA-Z][a-zA-Z0-9-]*):/gm)].map((item) => item[1]);
}

function listValues(yaml, key) {
  const block = yaml.match(new RegExp(`^${key}:\\s*\\n((?:\\s+-\\s+[^\\n]+\\n?)+)`, "m"));
  return block ? [...block[1].matchAll(/^\s+-\s+(.+)$/gm)].map((item) => item[1].trim()) : [];
}

async function filesUnder(directory, prefix = "") {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const relative = path.join(prefix, entry.name);
    if (entry.isDirectory()) {
      files.push(...await filesUnder(path.join(directory, entry.name), relative));
    } else {
      files.push(relative);
    }
  }
  return files.sort();
}

async function directoryNames(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  return entries.filter((entry) => entry.isDirectory()).map((entry) => entry.name).sort();
}

const [canonicalDirectories, compatibilityDirectories] = await Promise.all([
  directoryNames(canonicalRoot),
  directoryNames(compatibilityRoot)
]);
const sortedExpectedSkills = [...expectedSkills].sort();
if (JSON.stringify(canonicalDirectories) !== JSON.stringify(sortedExpectedSkills)) {
  errors.push(`canonical skill directories differ from manifest: [${canonicalDirectories.join(", ")}]`);
}
if (JSON.stringify(compatibilityDirectories) !== JSON.stringify(sortedExpectedSkills)) {
  errors.push(`compatibility skill directories differ from manifest: [${compatibilityDirectories.join(", ")}]`);
}

for (const skill of expectedSkills) {
  const canonicalDir = path.join(canonicalRoot, skill);
  const skillFile = path.join(canonicalDir, "SKILL.md");
  const metadataFile = path.join(canonicalDir, "agents", "openai.yaml");
  let skillContent = "";
  let metadataContent = "";

  try {
    skillContent = await readFile(skillFile, "utf8");
    metadataContent = await readFile(metadataFile, "utf8");
  } catch (error) {
    errors.push(`${skill}: missing SKILL.md or agents/openai.yaml (${error.code})`);
    continue;
  }

  const meta = frontmatter(skillContent);
  const skillName = scalar(meta, "name") ?? "";
  if (skillName !== skill) {
    errors.push(`${skill}: frontmatter name must match directory`);
  }
  if (!/^[a-z0-9-]+$/.test(skillName) || skillName.startsWith("-")
      || skillName.endsWith("-") || skillName.includes("--") || skillName.length > 64) {
    errors.push(`${skill}: name violates skill-creator naming rules`);
  }
  const unexpectedKeys = topLevelKeys(meta)
    .filter((key) => !["name", "description", "when_to_use", "license", "allowed-tools", "metadata"].includes(key));
  if (unexpectedKeys.length > 0) {
    errors.push(`${skill}: unexpected SKILL.md frontmatter keys ${unexpectedKeys.join(", ")}`);
  }
  const description = scalar(meta, "description") ?? "";
  if (!description.startsWith("Use ")) {
    errors.push(`${skill}: description must start with a positive Use trigger`);
  }
  if (description.length > 250 || description.includes("<") || description.includes(">")) {
    errors.push(`${skill}: description must front-load routing within 250 characters`);
  }
  const whenToUse = scalar(meta, "when_to_use") ?? "";
  if (whenToUse.length < 40 || !whenToUse.includes("Do not")) {
    errors.push(`${skill}: when_to_use must contain concrete positive and negative routing guidance`);
  }
  if (!skillContent.includes("## Trigger Conditions")) {
    errors.push(`${skill}: missing Trigger Conditions section`);
  }
  if (skillContent.split("\n").length > 500) {
    errors.push(`${skill}: SKILL.md exceeds the progressive-disclosure line budget`);
  }
  if (!metadataContent.includes(`$${skill}`)) {
    errors.push(`${skill}: openai.yaml default_prompt must mention $${skill}`);
  }
  const shortDescription = scalar(metadataContent, "short_description") ?? "";
  if (shortDescription.length < 25 || shortDescription.length > 64) {
    errors.push(`${skill}: openai.yaml short_description must be 25-64 characters`);
  }
  if (!/allow_implicit_invocation:\s*true/.test(metadataContent)) {
    errors.push(`${skill}: implicit invocation must be enabled`);
  }

  const references = [...skillContent.matchAll(/`(docs\/[^`<>]+)`/g)].map((item) => item[1]);
  for (const reference of references) {
    try {
      await stat(path.join(root, reference));
    } catch {
      errors.push(`${skill}: referenced project path does not exist: ${reference}`);
    }
  }

  try {
    const canonicalFiles = await filesUnder(canonicalDir);
    const mirrorDir = path.join(compatibilityRoot, skill);
    const mirrorFiles = await filesUnder(mirrorDir);
    if (JSON.stringify(canonicalFiles) !== JSON.stringify(mirrorFiles)) {
      errors.push(`${skill}: compatibility mirror file list differs`);
      continue;
    }
    for (const relative of canonicalFiles) {
      const [canonical, mirror] = await Promise.all([
        readFile(path.join(canonicalDir, relative), "utf8"),
        readFile(path.join(mirrorDir, relative), "utf8")
      ]);
      if (canonical !== mirror) {
        errors.push(`${skill}: compatibility mirror differs at ${relative}`);
      }
    }
  } catch (error) {
    errors.push(`${skill}: compatibility mirror unavailable (${error.code})`);
  }
}

try {
  await stat(path.join(compatibilityRoot, "qta-quality-acceptance"));
  errors.push("deprecated qta-quality-acceptance compatibility skill still exists");
} catch {
  // Expected.
}

for (const agent of expectedAgents) {
  const file = path.join(agentRoot, `${agent}.md`);
  let content = "";
  try {
    content = await readFile(file, "utf8");
  } catch (error) {
    errors.push(`${agent}: missing agent template (${error.code})`);
    continue;
  }
  const meta = frontmatter(content);
  const policy = expectedAgentPolicies[agent];
  if (scalar(meta, "name") !== agent) {
    errors.push(`${agent}: frontmatter name must match file`);
  }
  if (scalar(meta, "permissionMode") !== policy.permissionMode) {
    errors.push(`${agent}: permissionMode must be ${policy.permissionMode}`);
  }
  if (scalar(meta, "maxTurns") !== policy.maxTurns) {
    errors.push(`${agent}: maxTurns must be ${policy.maxTurns}`);
  }
  const tools = listValues(meta, "tools");
  const disallowed = listValues(meta, "disallowedTools");
  const skills = listValues(meta, "skills");
  if (JSON.stringify(tools) !== JSON.stringify(policy.tools)) {
    errors.push(`${agent}: tools must match the role allowlist`);
  }
  for (const tool of policy.disallowedTools) {
    if (!disallowed.includes(tool)) errors.push(`${agent}: must disallow ${tool}`);
  }
  if (JSON.stringify(skills) !== JSON.stringify(policy.skills)) {
    errors.push(`${agent}: skill combination violates the role boundary`);
  }
  for (const skill of skills) {
    if (!expectedSkills.includes(skill)) {
      errors.push(`${agent}: references unknown skill ${skill}`);
    }
  }
  for (const required of ["session ID", "compaction", "role instance"]) {
    if (!content.includes(required)) errors.push(`${agent}: missing V2 role-instance rule: ${required}`);
  }
}

const requiredGovernanceFiles = [
  ".agents/schemas/qta-task-control.schema.json",
  ".agents/skills/qta-development-orchestration/assets/TASK_CONTROL_TEMPLATE.json",
  ".agents/skills/qta-development-orchestration/references/GOVERNANCE_V2_POLICY.md",
  ".zcode/commands/qta-doctor.md",
  "scripts/check-ai-task-control.mjs",
  "scripts/check-ai-delivery-ready.mjs",
  "scripts/create-candidate-diff.mjs",
  "scripts/check-ai-architecture.mjs",
  "scripts/run-ai-evidence-command.mjs",
  "scripts/zcode-governance-hook.mjs",
  "scripts/zcode-governance-user-dispatcher.mjs",
  "scripts/install-zcode-governance-user-hooks.mjs",
  "scripts/qta-governance-doctor.mjs",
  "scripts/tests/ai-governance.test.mjs"
];
for (const relative of requiredGovernanceFiles) {
  try {
    await stat(path.join(root, relative));
  } catch {
    errors.push(`missing governance V2 asset: ${relative}`);
  }
}

try {
  JSON.parse(await readFile(path.join(root, ".agents", "schemas", "qta-task-control.schema.json"), "utf8"));
  const [installer, dispatcher, runCommand, doctorCommand] = await Promise.all([
    readFile(path.join(root, "scripts", "install-zcode-governance-user-hooks.mjs"), "utf8"),
    readFile(path.join(root, "scripts", "zcode-governance-user-dispatcher.mjs"), "utf8"),
    readFile(path.join(root, ".zcode", "commands", "qta-run.md"), "utf8"),
    readFile(path.join(root, ".zcode", "commands", "qta-doctor.md"), "utf8")
  ]);
  for (const event of ["UserPromptSubmit", "PreToolUse", "PostToolUse", "PostToolUseFailure"]) {
    if (!installer.includes(`${event}:`)) errors.push(`user Hook installer is missing ${event}`);
  }
  if (!installer.includes("QTA_GOVERNED_RUN") || !installer.includes("QTA_GOVERNANCE_DOCTOR")) {
    errors.push("user Hook installer must match expanded qta-run and qta-doctor sentinels");
  }
  if (/\bStop\s*:/.test(installer)) {
    errors.push("QTA user Hook installer must not register a Stop event");
  }
  if (!dispatcher.includes('"scripts", "zcode-governance-hook.mjs"')) {
    errors.push("user Hook dispatcher must resolve the project governance Hook dynamically");
  }
  if (!runCommand.includes("QTA_GOVERNED_RUN")
      || !runCommand.includes("qta-governance-doctor.mjs --runtime --require-active")) {
    errors.push("qta-run must expose its sentinel and require runtime Hook preflight");
  }
  if (!doctorCommand.includes("QTA_GOVERNANCE_DOCTOR")
      || !doctorCommand.includes("qta-governance-doctor.mjs --runtime")) {
    errors.push("qta-doctor must expose its sentinel and runtime check");
  }
  try {
    await stat(path.join(root, ".zcode", "config.json"));
    errors.push("project-level .zcode/config.json must stay absent because ZCode ignores project Hooks");
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
  }
} catch (error) {
  errors.push(`governance V2 configuration is invalid (${error.message})`);
}

const triggerResult = spawnSync(process.execPath, [path.join(root, "scripts", "evaluate-skill-triggers.mjs")], {
  cwd: root,
  encoding: "utf8"
});
if (triggerResult.status !== 0) {
  const detail = (triggerResult.stderr || triggerResult.stdout).trim();
  errors.push(`trigger evaluation failed${detail ? `: ${detail}` : ""}`);
}

const activeDocs = [
  "AGENTS.md",
  "CLAUDE.md",
  "docs/AI_DEVELOPMENT_INDEX.md",
  "docs/AI_HANDOFF.md",
  "docs/DEVELOPMENT_WORKFLOW.md",
  "docs/ai/PROGRESSIVE_DISCLOSURE_PROTOCOL.md",
  "docs/ai/SKILL_AND_AGENT_GOVERNANCE.md"
];

for (const relative of activeDocs) {
  const content = await readFile(path.join(root, relative), "utf8");
  if (content.includes("qta-quality-acceptance")) {
    errors.push(`${relative}: references deprecated qta-quality-acceptance`);
  }
  if (content.includes(".claude/skills/qta-context-bootstrap")) {
    errors.push(`${relative}: treats the compatibility path as the canonical skill`);
  }
}

const orchestrationCommand = await readFile(path.join(root, ".zcode", "commands", "qta-run.md"), "utf8");
if (!orchestrationCommand.includes("skills: qta-development-orchestration")
    || !orchestrationCommand.includes("$ARGUMENTS")) {
  errors.push("qta-run command must mount qta-development-orchestration and forward task arguments");
}
for (const required of ["TASK_CONTROL", "fresh", "two waits", "architecture", "delivery-ready", "parent coordinator"]) {
  if (!orchestrationCommand.toLowerCase().includes(required.toLowerCase())) {
    errors.push(`qta-run command missing V2 instruction: ${required}`);
  }
}

if (errors.length > 0) {
  console.error("AI governance validation failed:");
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log(`AI governance validation passed: ${expectedSkills.length} skills, ${expectedAgents.length} agents.`);
