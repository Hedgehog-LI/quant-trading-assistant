#!/usr/bin/env node

import { createHash } from "node:crypto";
import { lstat, readFile, readdir, readlink, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

function usage() {
  console.error("Usage: node scripts/create-candidate-manifest.mjs --output <file> [--root <dir>] <path>...");
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function posixRelative(root, target) {
  return path.relative(root, target).split(path.sep).join("/");
}

async function collect(root, target, output, entries) {
  const absolute = path.resolve(root, target);
  if (absolute === output) return;

  let info;
  try {
    info = await lstat(absolute);
  } catch (error) {
    if (error.code === "ENOENT") {
      entries.push({ path: posixRelative(root, absolute), type: "absent", sha256: null });
      return;
    }
    throw error;
  }

  if (info.isDirectory()) {
    const children = await readdir(absolute);
    for (const child of children.sort()) {
      await collect(root, path.join(posixRelative(root, absolute), child), output, entries);
    }
    return;
  }

  if (info.isSymbolicLink()) {
    const linkTarget = await readlink(absolute);
    entries.push({
      path: posixRelative(root, absolute),
      type: "symlink",
      sha256: sha256(Buffer.from(linkTarget, "utf8"))
    });
    return;
  }

  if (!info.isFile()) {
    throw new Error(`Unsupported candidate path type: ${absolute}`);
  }

  entries.push({
    path: posixRelative(root, absolute),
    type: "file",
    sha256: sha256(await readFile(absolute))
  });
}

const args = process.argv.slice(2);
let root = process.cwd();
let outputArg = "";
const targets = [];

for (let index = 0; index < args.length; index += 1) {
  if (args[index] === "--root") {
    root = path.resolve(args[index + 1] ?? "");
    index += 1;
  } else if (args[index] === "--output") {
    outputArg = args[index + 1] ?? "";
    index += 1;
  } else {
    targets.push(args[index]);
  }
}

if (!outputArg || targets.length === 0) {
  usage();
  process.exit(2);
}

const output = path.resolve(root, outputArg);
const entries = [];
for (const target of targets) {
  await collect(root, target, output, entries);
}
entries.sort((left, right) => left.path.localeCompare(right.path));

const entrySetSha256 = sha256(Buffer.from(JSON.stringify(entries), "utf8"));
const manifest = {
  version: 1,
  algorithm: "sha256(sorted JSON entries: path,type,sha256)",
  root: ".",
  entrySetSha256,
  entries
};
const content = `${JSON.stringify(manifest, null, 2)}\n`;
await writeFile(output, content, "utf8");

console.log(JSON.stringify({
  output: posixRelative(root, output),
  entryCount: entries.length,
  entrySetSha256,
  manifestSha256: sha256(Buffer.from(content, "utf8"))
}));
