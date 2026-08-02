# Self-Check Artifact: ARCH-GATE-BASELINE-AWARE-20260802 / SLICE-IDX

- Status: `BLOCKED` (concrete cross-slice blocker; see Blocker section)
- Slice ID: `SLICE-IDX`
- Dispatch ID: `dispatch-imp-agba-idx-2`
- Role instance policy: `FRESH_ONLY` (fresh retry; prior `IMP-AGBA-IDX-1` was CANCELLED before returning — not inherited)
- Lane: `L2`
- Assigned AC IDs: AC-04, AC-05, AC-06
- Contract hash: `79ec00e82eb4a797fa3767b34f9567ddd56b45507d531d121c36caa6769af148`
- Baseline commit: `979b080` (branch `codex/security-directory-d2-20260802`; verified == HEAD at role start)
- Repair round: 0
- Role started-at: `2026-08-02T10:05:00Z` (approx; role session start)
- Role finished-at: `2026-08-02T10:31:00Z`
- Executor type: `SUBAGENT`
- Enforcement level: `ADVISORY` (self-tests are SELF_CHECKED only; not independent acceptance)
- Runtime receipt path: created by the workspace hook for this dispatch session
- Wait calls: 0
- Shell polls for one command: at most 2 (focused test selectors)
- Context/compaction status: no compaction occurred; well under threshold

## Files changed

1. `scripts/tests/ai-governance.test.mjs` — added 4 tests (TEST-AG-04, TEST-AG-05, TEST-AG-06, TEST-AG-07)
   using the verbatim selectors from the Frozen Test Inventory, plus one local helper
   `javaMethodBodyFixture(lineCount)`. +319 lines. TEST-AG-04 and TEST-AG-07 PASS. TEST-AG-05 and TEST-AG-06
   FAIL on a single shared root cause (see Blocker) that is outside this slice's allowed write paths.
2. `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md` — NEW governance doc (93 lines, within the 120-line cap).
   Unaffected by the blocker; documents classification semantics, default delta 20, report fields, the
   coherence invariant, and the honesty rule.

Allowed write paths respected: ONLY `scripts/tests/ai-governance.test.mjs` and
`docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md` were edited, plus this self-check artifact. The detector
`scripts/check-ai-architecture.mjs` was READ ONLY and was NOT modified. No Git stage/commit/push performed.

## Gate results

| Gate | Command | Exit | Result |
|---|---|---|---|
| focused (verbatim contract cmd) | `node --test --test-name-pattern="baseline-aware\|architecture gate keeps strict\|architecture report binds" scripts/tests/ai-governance.test.mjs` | 0 | NOTE: the `\|` pattern is a literal escaped pipe in JS RegExp, so it matches ZERO test names; Node reports the file module as trivially passing (pass 1). This verbatim command does NOT exercise the 4 new tests. See "Focused-gate pattern caveat" below. |
| focused (correct alternation) | `node --test --test-name-pattern="baseline-aware|architecture gate keeps strict|architecture report binds" scripts/tests/ai-governance.test.mjs` | 1 | AG-04 PASS, AG-07 PASS; AG-05 FAIL, AG-06 FAIL (both on `report.errorCount` — see Blocker) |
| full regression | `node --test scripts/tests/ai-governance.test.mjs` | 1 | tests 45, pass 43, fail 2 (AG-05, AG-06 only). All 41 pre-existing tests still pass — no regression from this slice's additions. |
| governance suite | `node scripts/run-ai-governance-gates.mjs` | 1 | Fails because the suite runs the full test file, which includes the 2 failing new tests. The `validate-ai-governance.mjs` leg is independent of this work. |

## Blocker (concrete, cross-slice, outside allowed write paths)

**Root cause:** `scripts/check-ai-architecture.mjs` (owned by SLICE-DET; READ-ONLY for SLICE-IDX) does NOT
serialize an `errorCount` field in the JSON report payload. The `errorCount` local variable exists (line 377;
set at lines 394 and 398) and is correctly used for the summary log (line 400), `status` (line 420),
`exitCode` (line 421), and `process.exit` (line 434), but the payload object (lines 408-422) only carries
`errors`, `status`, `exitCode` (and, in baseline-aware mode, `baselineIdentity`/`blockingErrorCount`/
`introducedErrors`/`worsenedErrors`/`preExistingErrors`). No `errorCount` key is ever assigned to the payload.

Verified payload keys for the baseline-aware PASS path: `schemaVersion, generatedBy, generatedAt,
candidateIdentity, base, manifestPath, architectureReviewCount, files, additions, warnings, errors, status,
exitCode` (+ `baselineIdentity, blockingErrorCount, introducedErrors, worsenedErrors, preExistingErrors`).
`Object.prototype.hasOwnProperty.call(report, "errorCount") === false`. Same omission in the no-baseline path.

**Why this blocks AC-05 and AC-06 (as written):**
- AC-05 Expected result requires "`errorCount == errors.length`". With `report.errorCount === undefined`,
  `undefined == errors.length` is false.
- AC-06 Expected result requires "`report.errorCount === 0`" for an only-pre-existing report. With the field
  absent, the assertion fails (`undefined !== 0`).
- The Task Packet's TEST-AG-05/TEST-AG-06 specs both reference `report.errorCount` literally.

**Evidence (reproducible):** an only-pre-existing baseline-aware run yields
`status: PASS, exitCode: 0, errors.length: 0, blockingErrorCount: 0, preExistingErrors: 1` but
`report.errorCount === undefined`. The report IS otherwise coherent (`errors.length === blockingErrorCount
=== 0`; `status === PASS`; `exitCode === 0`); only the serialized scalar `errorCount` field is missing.

**Operational impact (low):** `scripts/check-ai-task-control.mjs` line 789 validates the candidate-bound
report via `report.errors?.length !== control.architectureGate.errorCount`, i.e. it reads the report's
`errors.length`, NOT `report.errorCount`. So D2's operational path is unaffected. The gap is purely that the
contract's AC-05/AC-06 test assertions reference a serialized `errorCount` field the detector does not emit.

**Why SLICE-IDX cannot resolve it:** the Task Packet scope states "Allowed write paths (ONLY these two files):
`scripts/tests/ai-governance.test.mjs`, `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md`" and "You MAY read (not
edit) `scripts/check-ai-architecture.mjs` ... Do NOT modify the detector." Adding `errorCount` to the payload
requires editing the detector, which is prohibited.

**Boundary-respecting options for the parent:**
1. Governed SLICE-DET repair round: add `errorCount` to the payload object (one line:
   `errorCount,` inside the payload literal at line ~419). Once serialized, TEST-AG-05 and TEST-AG-06 pass
   UNCHANGED as written below (they already encode the contract verbatim). This is the prescribed route per
   `GOVERNANCE_V2_POLICY.md` §Architecture Gate ("If the detector itself is wrong, repair and validate the
   detector in a separate governed task").
2. Contract amendment to relax the AC-05/AC-06 `errorCount` assertion to `errors.length` (operationally
   equivalent and what downstream already uses). NOT done here — weakening an AC is explicitly prohibited for
   the implementer.

I did NOT substitute `errors.length` for `errorCount` in the tests, because silently substituting an assertion
would weaken an explicit AC ("Do not change product meaning or weaken acceptance criteria"; "Do not add fake
success paths, permissive fallbacks"). The failing assertions are left in place as evidence of the exact gap.

## Focused-gate pattern caveat

The Required-gates table specifies the focused command with the pattern
`baseline-aware\|architecture gate keeps strict\|architecture report binds`. In JavaScript RegExp, `\|` is a
literal escaped pipe (not alternation), so this pattern matches NO test name (none contains a literal `|`).
Node's test runner then reports the file module as passing with zero matched subtests. To actually exercise
the 4 new tests, the alternation must be unescaped: `baseline-aware|architecture gate keeps strict|
architecture report binds`. Recommend the parent/verifier use the unescaped form. This is a contract-gate
wording issue, not a code defect; flagged here for the reviewer.

## New test bodies

### TEST-AG-04 (PASS) — selector: `baseline-aware gate still blocks over-long Java methods`

```js
function javaMethodBodyFixture(lineCount) {
  const lines = ["class LongMethod {", "  public void calculate(int input) {"];
  for (let index = 0; index < lineCount; index += 1) lines.push(`    int value${index} = ${index};`);
  lines.push("    return;");
  lines.push("  }");
  lines.push("}");
  return `${lines.join("\n")}\n`;
}

test("baseline-aware gate still blocks over-long Java methods", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-java-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-java-base-"));
  try {
    const relativeFile = "src/main/java/demo/LongMethod.java";
    await mkdir(path.join(baselineDir, path.dirname(relativeFile)), { recursive: true });
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await writeFile(path.join(baselineDir, relativeFile), javaMethodBodyFixture(95));
    await writeFile(path.join(candidateDir, relativeFile), javaMethodBodyFixture(120));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const result = await runBaselineAwareGate({
      candidateDir, baselineDir, candidateFile: relativeFile, reportPath
    });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "FAIL");
    assert.ok(report.blockingErrorCount >= 1);
    const introduced = report.introducedErrors
      .filter((entry) => entry.message.includes("longest method"));
    assert.ok(introduced.length >= 1);
    for (const entry of introduced) {
      assert.equal(entry.classification, "introduced");
      assert.equal(entry.baselineMetric, null);
      assert.equal(entry.delta, null);
      assert.equal(typeof entry.candidateMetric, "number");
      assert.ok(entry.candidateMetric > 100);
    }
    assert.ok(report.errors.some((entry) => introduced.some((match) => match.id === entry.id)));
    assert.deepEqual(report.worsenedErrors, []);
    assert.equal(report.candidateIdentity, "candidate-baseline-aware");
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});
```

### TEST-AG-05 (FAIL — `errorCount` serialization gap) — selector: `architecture gate keeps strict behavior when no baseline is supplied`

```js
test("architecture gate keeps strict behavior when no baseline is supplied", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-strict-no-baseline-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await writeFile(path.join(candidateDir, relativeFile), methodBodyFixture(118));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const script = path.resolve("scripts/check-ai-architecture.mjs");
    const result = spawnSync(process.execPath, [script,
      "--files", path.resolve(candidateDir, relativeFile),
      "--candidate-identity", "strict-candidate",
      "--json-output", reportPath
    ], { cwd: candidateDir, encoding: "utf8" });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "FAIL");
    assert.equal(report.errors.length, report.errorCount);   // FAILS: report.errorCount === undefined
    assert.ok(report.errors.length >= 1);
    assert.ok(report.errors.some((entry) => entry.message.includes("longest method")));
    assert.equal(report.baselineIdentity, undefined);
    assert.equal(report.blockingErrorCount, undefined);
    assert.equal(report.introducedErrors, undefined);
    assert.equal(report.worsenedErrors, undefined);
    assert.equal(report.preExistingErrors, undefined);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
  }
});
```

### TEST-AG-06 (FAIL — `errorCount` serialization gap) — selector: `architecture report binds candidate and baseline identity and the full governance suite passes`

```js
test("architecture report binds candidate and baseline identity and the full governance suite passes", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-identity-bind-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-identity-bind-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    await mkdir(path.join(baselineDir, path.dirname(relativeFile)), { recursive: true });
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await writeFile(path.join(baselineDir, relativeFile), methodBodyFixture(148));
    await writeFile(path.join(candidateDir, relativeFile),
      securitySelectorIntegrationFixture(148));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const script = path.resolve("scripts/check-ai-architecture.mjs");
    const baselineAbs = path.resolve(baselineDir);
    const candidateAbs = path.resolve(candidateDir, relativeFile);
    const result = spawnSync(process.execPath, [script,
      "--files", candidateAbs,
      "--baseline", baselineAbs,
      "--candidate-identity", "cand-X",
      "--json-output", reportPath
    ], { cwd: candidateDir, encoding: "utf8" });
    assert.equal(result.status, 0, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.candidateIdentity, "cand-X");
    assert.equal(report.baselineIdentity, baselineAbs);
    assert.equal(report.errorCount, 0);                     // FAILS: report.errorCount === undefined
    assert.equal(report.status, "PASS");
    assert.equal(report.exitCode, 0);

    const suiteScript = path.resolve("scripts/run-ai-governance-gates.mjs");
    const suite = spawnSync(process.execPath, [suiteScript],
      { cwd: path.resolve(import.meta.dirname, ".."), encoding: "utf8" });
    assert.equal(suite.status, 0, suite.stderr);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});
```

### TEST-AG-07 (PASS) — selector: `baseline-aware gate treats a missing baseline file as introduced not silently dropped`

```js
test("baseline-aware gate treats a missing baseline file as introduced not silently dropped", async () => {
  const candidateDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-missing-cand-"));
  const baselineDir = await mkdtemp(path.join(os.tmpdir(), "qta-baseline-missing-base-"));
  try {
    const relativeFile = "src/pages/Example.tsx";
    await mkdir(path.join(candidateDir, path.dirname(relativeFile)), { recursive: true });
    await mkdir(path.join(baselineDir, "src", "unrelated"), { recursive: true });
    await writeFile(path.join(candidateDir, relativeFile), methodBodyFixture(118));
    await writeFile(path.join(baselineDir, "src/unrelated/Other.tsx"), methodBodyFixture(10));
    const reportPath = path.join(candidateDir, "architecture-report.json");
    const result = await runBaselineAwareGate({
      candidateDir, baselineDir, candidateFile: relativeFile, reportPath
    });
    assert.equal(result.status, 1, result.stdout);
    const report = JSON.parse(await readFile(reportPath, "utf8"));
    assert.equal(report.status, "FAIL");
    assert.ok(report.blockingErrorCount >= 1);
    const introduced = report.introducedErrors
      .filter((entry) => entry.message.includes("longest method"));
    assert.ok(introduced.length >= 1);
    for (const entry of introduced) {
      assert.equal(entry.classification, "introduced");
      assert.equal(entry.baselineMetric, null);
      assert.equal(entry.delta, null);
      assert.equal(typeof entry.candidateMetric, "number");
    }
    assert.ok(report.errors.some((entry) => introduced.some((match) => match.id === entry.id)));
    assert.deepEqual(report.worsenedErrors, []);
    assert.deepEqual(report.preExistingErrors, []);
  } finally {
    await rm(candidateDir, { recursive: true, force: true });
    await rm(baselineDir, { recursive: true, force: true });
  }
});
```

## AC status

| AC | Status | Evidence |
|---|---|---|
| AC-04 | SELF_CHECKED | TEST-AG-04 PASS (Java >100 method blocks as introduced; `candidateIdentity == input`). |
| AC-05 | BLOCKED | TEST-AG-05 FAILS solely on `report.errorCount` (detector omits the field). Strict no-baseline behavior itself is preserved (exitCode 1, no baseline fields). |
| AC-06 | BLOCKED | TEST-AG-06 FAILS solely on `report.errorCount` (detector omits the field). Identity binding (`candidateIdentity`/`baselineIdentity`), `status`, `exitCode`, and the only-pre-existing ⇒ blocking=0 coherence all PASS. |

## Remaining risks / next action

- **Next smallest action (parent-owned):** governed SLICE-DET repair round adding `errorCount` to the JSON
  payload in `scripts/check-ai-architecture.mjs` (one line in the payload literal). TEST-AG-05 and TEST-AG-06
  then pass unchanged. This role's tests and doc are ready for that fix; no further SLICE-IDX work is needed.
- No Git operations performed (parent owns Git). Candidate is NOT frozen.
- Dimensions: AUTOMATION partially SELF_CHECKED (AG-04/07 green; AG-05/06 blocked on detector field).
  STATIC/validate-ai-governance leg independent of this slice. RUNTIME/DEPLOYMENT NOT_REQUIRED (governance
  tooling).

## Changed-path manifest (proposed commit message for the parent)

Changed paths:
- `scripts/tests/ai-governance.test.mjs` (modified; +319 lines: 4 tests + 1 helper)
- `docs/ai/ARCHITECTURE_GATE_BASELINE_AWARE.md` (new; 93 lines)
- `docs/development/tasks/ARCH-GATE-BASELINE-AWARE-20260802-SELF-CHECK-IDX.md` (new; this artifact)

Proposed commit message (NOT committed by this role; parent owns Git):
```
test(arch-gate): add SLICE-IDX identity/suite tests + baseline-aware governance doc

Add TEST-AG-04 (Java over-long method blocks), TEST-AG-05 (strict no-baseline),
TEST-AG-06 (candidate+baseline identity + governance suite), TEST-AG-07 (missing
baseline file => introduced), and the ARCHITECTURE_GATE_BASELINE_AWARE.md doc.

TEST-AG-05/06 are blocked on a SLICE-DET gap: the detector does not serialize
`errorCount` in the JSON payload. See SELF-CHECK-IDX for the repair request.
```
