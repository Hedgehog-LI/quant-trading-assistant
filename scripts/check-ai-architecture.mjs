#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function sanitizeSource(content) {
  let result = "";
  let state = "CODE";
  let quote = "";
  for (let index = 0; index < content.length; index += 1) {
    const current = content[index];
    const next = content[index + 1];
    if (state === "LINE_COMMENT") {
      if (current === "\n") { state = "CODE"; result += "\n"; } else result += " ";
    } else if (state === "BLOCK_COMMENT") {
      if (current === "*" && next === "/") { result += "  "; index += 1; state = "CODE"; }
      else result += current === "\n" ? "\n" : " ";
    } else if (state === "STRING") {
      if (current === "\\") { result += "  "; index += 1; }
      else if (current === quote) { result += " "; state = "CODE"; }
      else result += current === "\n" ? "\n" : " ";
    } else if (current === "/" && next === "/") {
      result += "  "; index += 1; state = "LINE_COMMENT";
    } else if (current === "/" && next === "*") {
      result += "  "; index += 1; state = "BLOCK_COMMENT";
    } else if (["\"", "'", "`"].includes(current)) {
      quote = current; result += " "; state = "STRING";
    } else result += current;
  }
  return result;
}

function significantLines(content) {
  return sanitizeSource(content).split(/\r?\n/).filter((line) => line.trim() !== "").length;
}

function methodBraceIndexes(content, extension) {
  const source = sanitizeSource(content);
  const expressions = extension === ".java"
    ? [/(?:^|\n)\s*(?:(?:public|protected|private|static|final|synchronized|abstract|native|default)\s+)*(?:<[^>{}]+>\s+)?(?:[\w$.,?<>\[\]]+\s+)+[\w$]+\s*\([^;{}]*\)\s*(?:throws\s+[^{}]+)?\{/gm]
    : [
      /\bfunction\s+[A-Za-z_$][\w$]*\s*(?:<[^>{}]+>)?\s*\([^;{}]*\)\s*(?::\s*[^={]+)?\s*\{/gm,
      /\b(?:const|let)\s+[A-Za-z_$][\w$]*\s*=\s*(?:async\s*)?\([^;{}]*\)\s*(?::\s*[^=]+)?=>\s*\{/gm,
      /(?:^|\n)\s*(?:(?:public|private|protected|static|async|readonly|get|set)\s+)*(?!(?:if|for|while|switch|catch)\b)[A-Za-z_$][\w$]*\s*(?:<[^>{}]+>)?\s*\([^;{}]*\)\s*(?::\s*[^={]+)?\s*\{/gm
    ];
  const indexes = new Set();
  for (const expression of expressions) {
    for (const match of source.matchAll(expression)) indexes.add(match.index + match[0].lastIndexOf("{"));
  }
  return { source, indexes: [...indexes].sort((left, right) => left - right) };
}

function methodMetrics(content, extension) {
  const { source, indexes } = methodBraceIndexes(content, extension);
  let longest = 0;
  for (const start of indexes) {
    let depth = 0;
    let end = start;
    for (; end < source.length; end += 1) {
      if (source[end] === "{") depth += 1;
      else if (source[end] === "}") depth -= 1;
      if (depth === 0) break;
    }
    const before = source.slice(0, start).split("\n").length;
    const after = source.slice(0, end).split("\n").length;
    longest = Math.max(longest, after - before + 1);
  }
  return { count: indexes.length, longest };
}

function directDependencies(content, extension) {
  const source = sanitizeSource(content);
  if (extension === ".java") {
    const fields = [...source.matchAll(/^\s*private\s+(?:final\s+)?([A-Z][\w$.<>?, ]*)\s+\w+\s*;/gm)]
      .map((match) => match[1].replace(/<.*>/, "").trim());
    return new Set(fields).size;
  }
  return new Set([...source.matchAll(/^import(?:[\s\S]*?)from\s+["']([^"']+)["'];?$/gm)]
    .map((match) => match[1])).size;
}

function responsibilityTags(file, content) {
  const tags = [];
  const rules = [
    ["http", /@RestController|@Controller|ResponseEntity|RequestMapping/],
    ["transaction", /@Transactional|TransactionTemplate/],
    ["persistence", /Mapper\b|Repository\b|SqlSession|JdbcTemplate/],
    ["file-protocol", /\bCSV\b|\bCsv\w*|BufferedReader|InputStream|MultipartFile|Files\./],
    ["conversion", /Converter\b|MapStruct|\btoVo\b|\btoDto\b/],
    ["provider", /Provider\b|Client\b|WebSocket|HttpClient/],
    ["scheduling", /@Scheduled|Scheduler\b|TaskScheduler/],
    ["search-scoring", /\bscore\b|ranking|Comparator|\.sort\s*\(/i]
  ];
  for (const [tag, expression] of rules) if (expression.test(content)) tags.push(tag);
  if (file.replaceAll("\\", "/").includes("/controller/")) tags.push("controller-layer");
  if (file.replaceAll("\\", "/").includes("/service/")) tags.push("service-layer");
  return [...new Set(tags)];
}

function isProductionSource(file) {
  const normalized = file.replaceAll("\\", "/");
  if (normalized.endsWith(".java")) return normalized.includes("/src/main/java/") || normalized.startsWith("src/main/java/");
  return /\.(?:ts|tsx)$/.test(normalized)
    && (normalized.includes("/src/") || normalized.startsWith("src/"))
    && !/\.(?:test|spec)\.(?:ts|tsx)$/.test(normalized)
    && !normalized.includes("/__tests__/");
}

export function analyzeSource(file, content) {
  const extension = path.extname(file);
  const lines = significantLines(content);
  const method = methodMetrics(content, extension);
  const dependencies = directDependencies(content, extension);
  const responsibilities = responsibilityTags(file, content);
  const warnings = [];
  const errors = [];

  if (lines > 400) warnings.push(`significant lines ${lines} > 400`);
  if (method.count > 20) warnings.push(`methods ${method.count} > 20`);
  if (method.longest > 60) warnings.push(`longest method ${method.longest} lines > 60`);
  if (dependencies > 10) warnings.push(`direct dependencies ${dependencies} > 10`);
  if (lines > 600 && (method.count > 30 || responsibilities.length > 3)) {
    errors.push(`class/module ${lines} lines, ${method.count} methods, ${responsibilities.length} responsibilities`);
  }
  if (method.longest > 100) errors.push(`longest method ${method.longest} lines > 100`);
  if (file.includes("/controller/") && /@Transactional|\b\w+Mapper\b|JdbcTemplate/.test(content)) {
    errors.push("controller crosses transaction/persistence boundary");
  }
  if (file.includes("/service/") && responsibilities.includes("file-protocol")
      && responsibilities.includes("persistence")) {
    errors.push("service combines file/protocol parsing with persistence");
  }
  if (!file.includes("/mapper/") && /\b(?:SELECT|INSERT|UPDATE|DELETE)\s+.+\b(?:FROM|INTO|SET)\b/is.test(sanitizeSource(content))) {
    errors.push("SQL appears outside mapper/persistence boundary");
  }
  return {
    file, lines, methods: method.count, longestMethod: method.longest,
    dependencies, responsibilities, warnings, errors
  };
}

function gitOutput(args, options = {}) {
  return execFileSync("git", args, { encoding: "utf8", ...options });
}

function untrackedProductionFiles() {
  return gitOutput(["ls-files", "--others", "--exclude-standard"])
    .split(/\r?\n/).filter(isProductionSource);
}

function changedWorkingFiles(base) {
  const tracked = gitOutput(["diff", "--name-only", base, "--"])
    .split(/\r?\n/).filter(isProductionSource);
  return [...new Set([...tracked, ...untrackedProductionFiles()])];
}

async function manifestCandidate(base, manifestPath) {
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  const files = [];
  const errors = [];
  let additions = 0;
  for (const entry of manifest.entries ?? []) {
    if (entry.type !== "file" || !isProductionSource(entry.path)) continue;
    let current;
    try {
      current = await readFile(entry.path);
    } catch (error) {
      errors.push(`manifest file unavailable: ${entry.path} (${error.code})`);
      continue;
    }
    if (sha256(current) !== entry.sha256) {
      errors.push(`manifest file hash mismatch: ${entry.path}`);
      continue;
    }
    let baseline = null;
    try {
      baseline = execFileSync("git", ["show", `${base}:${entry.path}`]);
    } catch {
      // New candidate file.
    }
    if (baseline && sha256(baseline) === entry.sha256) continue;
    files.push(entry.path);
    if (!baseline) additions += significantLines(current.toString("utf8"));
    else {
      const numstat = gitOutput(["diff", "--numstat", base, "--", entry.path]).trim().split("\t")[0];
      additions += Number.parseInt(numstat, 10) || 0;
    }
  }
  return { files, additions, errors };
}

function workingCandidateStats(base, files) {
  let additions = 0;
  const numstat = gitOutput(["diff", "--numstat", base, "--"]);
  for (const line of numstat.split(/\r?\n/)) {
    const [added, , file] = line.split("\t");
    if (isProductionSource(file ?? "")) additions += Number.parseInt(added, 10) || 0;
  }
  const tracked = new Set(gitOutput(["ls-files"]).split(/\r?\n/));
  return { additions, tracked, files };
}

async function main() {
  const baseIndex = process.argv.indexOf("--base");
  const filesIndex = process.argv.indexOf("--files");
  const manifestIndex = process.argv.indexOf("--manifest");
  const reviewCountIndex = process.argv.indexOf("--architecture-review-count");
  const candidateIdentityIndex = process.argv.indexOf("--candidate-identity");
  const jsonOutputIndex = process.argv.indexOf("--json-output");
  const architectureReviewCount = reviewCountIndex >= 0 ? Number.parseInt(process.argv[reviewCountIndex + 1], 10) || 0 : 0;
  const base = baseIndex >= 0 ? process.argv[baseIndex + 1] : "";
  const candidateIdentity = candidateIdentityIndex >= 0 ? process.argv[candidateIdentityIndex + 1] : "";
  const jsonOutput = jsonOutputIndex >= 0 ? process.argv[jsonOutputIndex + 1] : "";
  let files = [];
  let additions = 0;
  const candidateErrors = [];

  if (manifestIndex >= 0) {
    if (!base) throw new Error("--manifest requires --base");
    const candidate = await manifestCandidate(base, process.argv[manifestIndex + 1]);
    files = candidate.files;
    additions = candidate.additions;
    candidateErrors.push(...candidate.errors);
  } else if (base) {
    files = changedWorkingFiles(base);
    const stats = workingCandidateStats(base, files);
    additions = stats.additions;
    for (const file of files) {
      if (!stats.tracked.has(file)) additions += significantLines((await readFile(file)).toString("utf8"));
    }
  } else if (filesIndex >= 0) files = process.argv.slice(filesIndex + 1);
  else {
    console.error("Usage: node scripts/check-ai-architecture.mjs --base <git-ref> [--manifest <candidate.json>] [--architecture-review-count N] [--candidate-identity <id> --json-output <report.json>] | --files <paths...>");
    process.exit(2);
  }

  const reports = [];
  for (const file of files) {
    if (!/\.(?:java|ts|tsx)$/.test(file)) continue;
    try { reports.push(analyzeSource(file, await readFile(file, "utf8"))); }
    catch (error) { if (error.code !== "ENOENT") throw error; }
  }
  for (const report of reports) {
    const status = report.errors.length > 0 ? "ERROR" : report.warnings.length > 0 ? "REVIEW" : "OK";
    console.log(`${status} ${report.file}: lines=${report.lines}, methods=${report.methods}, dependencies=${report.dependencies}, responsibilities=${report.responsibilities.join("|") || "none"}`);
    for (const warning of report.warnings) console.log(`  WARN ${warning}`);
    for (const error of report.errors) console.log(`  ERROR ${error}`);
  }
  const candidateWarnings = [];
  if (additions > 800) {
    const warning = `candidate adds ${additions} production lines across ${files.length} files`;
    candidateWarnings.push(warning);
    console.log(`CANDIDATE WARN ${warning}`);
  }
  if (additions > 3000 && architectureReviewCount < 2) {
    candidateErrors.push("candidate over 3000 production lines requires two clean-context architecture reviews");
  } else if (additions > 1500 && architectureReviewCount < 1) {
    candidateErrors.push("candidate over 1500 production lines requires an independent architecture review");
  }
  for (const error of candidateErrors) console.log(`CANDIDATE ERROR ${error}`);

  const warningDetails = [];
  const errorDetails = [];
  for (const report of reports) {
    for (const message of report.warnings) warningDetails.push({ file: report.file, message });
    for (const message of report.errors) errorDetails.push({ file: report.file, message });
  }
  for (const message of candidateWarnings) warningDetails.push({ file: "<candidate>", message });
  for (const message of candidateErrors) errorDetails.push({ file: "<candidate>", message });
  const warnings = warningDetails.map((item, index) => ({ id: `ARCH-W-${String(index + 1).padStart(3, "0")}`, ...item }));
  const errors = errorDetails.map((item, index) => ({ id: `ARCH-E-${String(index + 1).padStart(3, "0")}`, ...item }));
  const warningCount = warnings.length;
  const errorCount = reports.reduce((sum, report) => sum + report.errors.length, 0) + candidateErrors.length;
  console.log(`Architecture gate: files=${reports.length}, additions=${additions}, warnings=${warningCount}, errors=${errorCount}`);
  if (jsonOutput) {
    if (!candidateIdentity) {
      console.error("--json-output requires --candidate-identity so the report can bind to a frozen candidate");
      process.exit(2);
    }
    const output = path.resolve(jsonOutput);
    const temporary = `${output}.tmp-${process.pid}`;
    const payload = {
      schemaVersion: 1,
      generatedBy: "scripts/check-ai-architecture.mjs",
      generatedAt: new Date().toISOString(),
      candidateIdentity,
      base,
      manifestPath: manifestIndex >= 0 ? process.argv[manifestIndex + 1] : "",
      architectureReviewCount,
      files: reports,
      additions,
      warnings,
      errors,
      status: errorCount === 0 ? "PASS" : "FAIL",
      exitCode: errorCount === 0 ? 0 : 1
    };
    await mkdir(path.dirname(output), { recursive: true });
    await writeFile(temporary, `${JSON.stringify(payload, null, 2)}\n`, { mode: 0o600 });
    await rename(temporary, output);
  }
  if (errorCount > 0) process.exit(1);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
