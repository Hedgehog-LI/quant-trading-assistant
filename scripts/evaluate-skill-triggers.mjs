#!/usr/bin/env node

import { readFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(import.meta.dirname, "..");

function matchesAny(prompt, patterns) {
  return patterns.some((pattern) => new RegExp(pattern, "i").test(prompt));
}

function matches(skill, prompt) {
  const included = matchesAny(prompt, skill.positivePatterns ?? []);
  const excluded = matchesAny(prompt, skill.negativePatterns ?? []);
  return included && !excluded;
}

export function selectSkills(prompt, manifest, contextState = "fresh") {
  const selected = [];
  const bootstrap = manifest.skills.find((skill) => skill.kind === "bootstrap");
  if (bootstrap && contextState === "fresh") selected.push(bootstrap.id);
  if (bootstrap && contextState !== "fresh" && matches(bootstrap, prompt)) selected.push(bootstrap.id);

  const controller = manifest.skills
    .filter((skill) => skill.kind === "controller" && matches(skill, prompt))
    .sort((left, right) => right.priority - left.priority)[0];

  if (controller) {
    selected.push(controller.id);
  } else {
    const lifecycleMatches = manifest.skills
      .filter((skill) => skill.kind === "lifecycle" && matches(skill, prompt));
    const winningPriority = Math.max(...lifecycleMatches.map((skill) => skill.priority), -1);
    selected.push(...lifecycleMatches
      .filter((skill) => skill.priority === winningPriority)
      .map((skill) => skill.id));
  }

  selected.push(...manifest.skills
    .filter((skill) => skill.kind === "overlay" && matches(skill, prompt))
    .map((skill) => skill.id));

  return [...new Set(selected)];
}

function wouldSelectWithoutExclusions(prompt, skill) {
  return matchesAny(prompt, skill.positivePatterns ?? []);
}

function sameSet(left, right) {
  return left.length === right.length && left.every((item) => right.includes(item));
}

export function evaluateTriggerCases(manifest, cases) {
  const errors = [];
  const knownSkills = new Set(manifest.skills.map((skill) => skill.id));
  const ids = new Set();
  const positiveCoverage = new Map([...knownSkills].map((skill) => [skill, 0]));
  const negativeRuleCoverage = new Map([...knownSkills].map((skill) => [skill, 0]));

  for (const testCase of cases) {
    if (ids.has(testCase.id)) errors.push(`${testCase.id}: duplicate case id`);
    ids.add(testCase.id);

    const expected = testCase.expectedSkills ?? [];
    const negativeRuleSkills = testCase.negativeRuleSkills ?? [];
    for (const skill of [...expected, ...negativeRuleSkills]) {
      if (!knownSkills.has(skill)) errors.push(`${testCase.id}: unknown skill ${skill}`);
    }

    const selected = selectSkills(testCase.prompt, manifest, testCase.contextState ?? "fresh");
    if (!sameSet(selected, expected)) {
      errors.push(`${testCase.id}: expected exact [${expected.join(", ")}], selected [${selected.join(", ")}]`);
    }

    for (const skill of expected) {
      positiveCoverage.set(skill, (positiveCoverage.get(skill) ?? 0) + 1);
    }
    for (const skillId of negativeRuleSkills) {
      const skill = manifest.skills.find((item) => item.id === skillId);
      negativeRuleCoverage.set(skillId, (negativeRuleCoverage.get(skillId) ?? 0) + 1);
      if (!wouldSelectWithoutExclusions(testCase.prompt, skill)) {
        errors.push(`${testCase.id}: does not exercise a positive trigger before excluding ${skillId}`);
      }
      if (!matchesAny(testCase.prompt, skill.negativePatterns ?? [])) {
        errors.push(`${testCase.id}: does not match a negative pattern for ${skillId}`);
      }
    }
  }

  for (const skill of knownSkills) {
    if (positiveCoverage.get(skill) === 0) errors.push(`${skill}: missing positive trigger case`);
    if (negativeRuleCoverage.get(skill) === 0) errors.push(`${skill}: missing exercised negative trigger case`);
  }

  return errors;
}

async function main() {
  const manifest = JSON.parse(await readFile(path.join(projectRoot, ".agents", "skill-manifest.json"), "utf8"));
  const suite = JSON.parse(await readFile(
    path.join(projectRoot, ".agents", "skill-evals", "trigger-cases.json"),
    "utf8"
  ));
  const errors = evaluateTriggerCases(manifest, suite.cases);
  if (errors.length > 0) {
    console.error("Skill heuristic routing evaluation failed:");
    for (const error of errors) console.error(`- ${error}`);
    process.exit(1);
  }
  console.log(`Skill heuristic routing evaluation passed: ${suite.cases.length} exact cases.`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main();
}
