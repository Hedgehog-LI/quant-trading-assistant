#!/usr/bin/env node

import { cp, mkdir, readFile, rm } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const projectRoot = path.resolve(import.meta.dirname, "..");
const canonicalRoot = path.join(projectRoot, ".agents", "skills");
const compatibilityRoot = path.join(projectRoot, ".claude", "skills");

const manifest = JSON.parse(await readFile(
  path.join(projectRoot, ".agents", "skill-manifest.json"),
  "utf8"
));
const managedSkills = manifest.skills.map((skill) => skill.id);

const deprecatedSkills = ["qta-quality-acceptance"];

await mkdir(compatibilityRoot, { recursive: true });

for (const skill of managedSkills) {
  const source = path.join(canonicalRoot, skill);
  const target = path.join(compatibilityRoot, skill);
  await rm(target, { recursive: true, force: true });
  await cp(source, target, { recursive: true });
}

for (const skill of deprecatedSkills) {
  await rm(path.join(compatibilityRoot, skill), { recursive: true, force: true });
}

console.log(`Synced ${managedSkills.length} QTA skills to .claude/skills.`);
