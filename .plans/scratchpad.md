# Go-Gobra Rewrite: Automation Scratchpad
> **Rule for AI Agent:** Do not edit any other `.plans/*.md` files until you have updated the active constraints, dependency states, and task items in this scratchpad.

## 1. GLOBAL SYMBOL & COMPONENT REGISTRY
*Tracks artifact ownership (C3), cross-plan type references (C4), and dependency links (C5).*

### Package Ownership
| Package / Artifact | Owner Plan |
|---|---|
| `internal/ast/frontend/` — `PNode`, `PDecl`, `PStmt`, `PGoStmt`, `PBlockStmt`, `PFile`, `PFunctionDecl`, `PMethodDecl`, `PTypeDecl`, `PInterfaceType`, `PRangeStmt`, `PForStmt`, `PLoopSpec`, `PBodyParameterInfo`, `PReceiver`, `PFunctionSpec`, `Visitor` | 03 |
| `internal/ast/frontend/` — `PExpression`, `PInvariant`, `PDecreases`, `PWildcardMeasure`, `PTupleTerminationMeasure` | 03 |
| `internal/ast/frontend/` — `PModifier`, `PPure`, `PTrusted`, `POpaque`, `PMayBeUsedInInit` | 03 |
| `internal/ast/frontend/` — `PMagicWand`, `PGhostCall`, `PMatch`, `PMatchCase` | 03 |
| `Visitor` interface (frontend) | 03 |
| `internal/ast/internal/` — `Method`, `Function`, `FPredicate`, `Expr`, `Stmt`, etc. | 11 |
| `Visitor` interface (internal) | 11 |
| `internal/silver/` — `Program`, `Member`, `Node`, `NodeInfo`, `VprInfo`, `NoInfo`, `AnnotationInfo`, `ConsInfo` | 14 |
| `internal/silver/printer.go` (`Print`) | 14 |
| `internal/backend/jvm/jvm.go` — `JVM`, `Start`, `Stop`, `JVMConfig`, `WorkerPool` struct, `workerJob`, `pred ThreadAttached()` | 15 |
| `internal/backend/jvm/jvm.go` — `NewPool`, `Submit`, full Silicon-aware worker goroutine | 15b |
| `internal/backend/types.go` — `VerificationResult`, `VerificationError`, `SiliconInstance` interface, `SiliconConfig`, `Backend` interface | 15 / 18 |
| `internal/backend/dedup.go` (`Deduplicate`) | 17b |
| `internal/diagnostic/` (`Diagnostic` struct, `DiagError`/`DiagWarning`/`DiagInfo`) | 32a |
| `internal/reporting/tags.go` (tag constants) | 32 |
| `internal/pipeline/pipeline.go` (`Run`, `ErrVerificationFailed`) | 33 |

### Pipeline Stage I/O Types
| Stage | Input | Output | Error |
|---|---|---|---|
| Gobrafier (06) | `[]byte` (raw src) | `[]byte` (preprocessed) | `[]Diagnostic` |
| Go Parser (04) | `string` (file path) | `*PFile` | `[]Diagnostic` |
| Annotation Parser (05) | `string` + `token.Pos` + `bool` (isFileScope) | `([]PNode, []PDecl)` | `[]Diagnostic` |
| Package Resolver (07) | `[]string` (paths) | `[]*PackageInfo` | `[]Diagnostic` |
| Type Checker (08) | `*frontend.PPackage` + `types.Importer` | `*TypeInfo` | `[]Diagnostic` |
| Spec Checker (09) | `*frontend.PPackage` + `*TypeInfo` | — (mutates TypeInfo) | `[]Diagnostic` |
| Desugarer (12) | `*frontend.PPackage` + `*TypeInfo` | `*internal.Program` | `[]Diagnostic` |
| Translator (19) | `*internal.Program` | `*silver.Program` | `[]Diagnostic` |
| Silicon Backend (17) | `*silver.Program` | `*backend.VerificationResult` | (in result.Err) |
| Reporter (32) | `*backend.VerificationResult` | formatted output | — |
| Pipeline (33) | `*Config` | — | `error` (`ErrVerificationFailed` or infra error) |

### Key Type Decisions
| Type / Signature | Decision |
|---|---|
| `Translate` return | `(result *silver.Program, diags []diagnostic.Diagnostic)` — consistent with all other pipeline stages |
| `Resolve` return | `(result []*PackageInfo, diags []Diagnostic)` — nil result on any error (strict; matches C9) |
| `Run` return | `error` only — reporter runs inside Run; callers use `errors.Is(err, ErrVerificationFailed)` |
| `pred ThreadAttached()` | No argument — JVM is a process-wide singleton; predicate models OS-thread state |
| Loop invariants | `[]PInvariant` (wrapper with source pos), not bare `[]PExpression` |
| Termination measures | `PDecreases` interface (renamed from `PTerminationMeasure`) |
| Function modifiers | `[]PModifier` slice on `PFunctionSpec` with node types `PPure`/`PTrusted`/`POpaque`/`PMayBeUsedInInit` |

### Synchronization Contracts
| Resource | Mechanism | Plan |
|---|---|---|
| JVM singleton | `sync.Once` | 15 |
| JNI thread attachment | `runtime.LockOSThread()` + `defer DetachCurrentThread()` + `pred ThreadAttached()` | 15 |
| Worker pool channel | buffered `chan workerJob` | 15 |
| `SiliconFrontendAPI` per worker | goroutine-local (no sharing) | 15b |
| Z3 API mode | forces `poolSize=1`; warning logged if `--workers > 1` | 15b |

---

## 2. GLOBAL WBS SYNCHRONIZATION STATE
*41 plan files. All plan documents are in a consistent, audited state (6 audit rounds complete, 95 remediation items all resolved). The checkboxes below track implementation status, not plan-document status.*

### Group 0: Foundation
- [ ] 01-project-setup.md
- [ ] 02-annotation-syntax-decision.md
- [ ] 32a-diagnostics.md

### Group 1: Frontend
- [ ] 03-frontend-ast.md
- [ ] 04-go-parser.md
- [ ] 05-annotation-parser.md
- [ ] 06-gobrafier.md
- [ ] 07-package-resolver.md

### Group 2: Type Checker
- [ ] 08-type-checker-core.md
- [ ] 09-type-checker-specs.md
- [ ] 10-type-checker-multipackage.md

### Group 3: Internal Representation
- [ ] 11-internal-ast.md
- [ ] 12-desugarer.md
- [ ] 13-internal-transforms.md

### Group 4: Silver IR & JNI Backend
- [ ] 14-silver-ast.md
- [ ] 15-jni-setup.md
- [ ] 15b-worker-pool-expansion.md
- [ ] 16-silver-jni-builder.md
- [ ] 16b-silver-chopper.md
- [ ] 17-silicon-backend.md
- [ ] 17b-parallel-workers.md
- [DEFERRED] 18-carbon-backend.md — post-self-hosting; see D12

### Group 5: Translator
- [ ] 19-translator-core.md
- [ ] 20-encoding-primitives.md
- [ ] 21-encoding-structs.md
- [ ] 22-encoding-pointers.md
- [ ] 23-encoding-slices.md
- [ ] 24-encoding-maps.md
- [ ] 25-encoding-interfaces.md
- [ ] 26-encoding-permissions.md
- [ ] 27-encoding-methods.md
- [ ] 28-encoding-channels.md
- [ ] 29-encoding-adts.md
- [ ] 30-encoding-generics.md
- [ ] 31-encoding-builtins.md

### Group 6: Error Reporting
- [ ] 32-reporter.md

### Group 7: CLI & Integration
- [ ] 33-cli.md

### Group 8: Testing
- [ ] 34-test-infrastructure.md
- [ ] 35-regression-suite.md

### Group 9: Self-Hosting
- [ ] 36-self-hosting-annotations.md
- [ ] 37-self-hosting-verify.md

---

## 3. AUDIT HISTORY
*Nine rounds of architectural audit completed against CRITERIA.md (C1–C9). 109 items resolved.*

| Round | Scope | Items | Status |
|---|---|---|---|
| 1 | Global contradiction scan | 1–13 | All DONE |
| 2 | Full audit round 2 | 14–27 | All DONE |
| 3 | Full audit round 3 | 28–48 | All DONE |
| 4 | File-by-file audit (plans 01–37) | 49–69 | All DONE |
| 5 | File-by-file audit follow-up | 70–88 | All DONE |
| 6 | File-by-file audit (plans 01–37, second pass) | 89–95 | All DONE |
| 7 | Full criteria check (C1–C9), all 41 plans | 96 | All DONE |
| 8 | Full /review-plan ALL (all 41 plans + foundation docs) | 97–104 | All DONE |
| — | D12 decision: defer Carbon backend (Plan 18) | — | RESOLVED |
| 9 | Full /review-plan ALL second pass (verified 34–37, COVERAGE, CRITERIA) | 105–109 | All DONE |
| 10 | Full /review-plan ALL third pass (all 41 plans + foundation docs) | 110–113 | All DONE |
| — | D13 decision: AST-based generic detection replaces grep | — | RESOLVED |
| 11 | Full /review-plan ALL fourth pass (all 41 plans + foundation docs) | 114–118 | All DONE |

**Total: 118 items resolved. All rounds complete. No open items.**

### Active Global Constraint — HasGenericDecl ownership

`HasGenericDecl(*ast.File) bool` and its ghost predicates (`funcDeclIsGeneric`,
`genDeclHasGenericSpec`, `typeSpecIsGeneric`) are owned by **plan 34**
(`internal/testing/runner.go`). Plan 35 specifies the required Gobra postcondition; plan 37
blocking tier must verify it before cut-over. No other plan may define a competing generic-
detection function. The grep-based pre-population approach (former plan 35 text) is
superseded and must not be re-introduced.

### Active Global Constraint — D14: translator encoding module type layering

Encoding module public signatures (plans 19–31) must use `internal.Type` and other types
from `internal/ast/internal/` — never `go/types` stdlib types (`types.Type`,
`types.Interface`, etc.) as direct parameters or return values. Cross-stage `go/types`
access goes through `ctx.TypeInfo()`. Any `go/types.*` type in an encoding function's
public signature is a layering violation. See DECISIONS.md D14.

---

## 4. OPEN ITEMS

### Item 96 — C9 FAIL: `17b-parallel-workers.md` missing Verification Specifications section

**Criterion**: C9 — Gobra specs (`//@ requires`/`ensures`/invariants) in all pipeline component plans.

**Finding**: Plan 17b implements `DispatchChopped` (the parallel fan-out dispatch layer) and
`mergeResults` (result aggregation with composite `Close()`). Both are pipeline-critical
functions. The plan has no `## Verification Specifications (C9)` section. All other
pipeline-component plans (15, 15b, 16, 16b, 17, 18, 19, 20–29, 31, 32, 33) carry explicit C9
sections.

**Required fix**: Add a `## Verification Specifications (C9)` section to `17b-parallel-workers.md`
specifying at minimum:

1. `DispatchChopped` postcondition — result is always non-nil; result.Success iff all sub-results
   are successful:
   ```go
   //@ requires len(progs) > 0
   //@ ensures  result != nil
   //@ ensures  result.Success == forall i int :: 0 <= i < len(progs) ==> subResults[i].Success
   func (p *WorkerPool) DispatchChopped(progs []*silver.Program) (result *backend.VerificationResult)
   ```

2. `mergeResults` composite Close contract — the merged result's `Close` transitively calls
   every sub-result's `Close()`:
   ```go
   //@ requires len(results) > 0 && forall i int :: 0 <= i < len(results) ==> results[i] != nil
   //@ ensures  result != nil
   //@ ensures  result.Close != nil  // composite close is always set
   func mergeResults(results []*backend.VerificationResult) (result *backend.VerificationResult)
   ```

3. `Deduplicate` purity contract — result is a subset of the input with no duplicates:
   ```go
   //@ ensures len(result) <= len(errs)
   //@ ensures forall i, j int :: 0 <= i < j < len(result) ==>
   //@     !(result[i].Pos.File == result[j].Pos.File && result[i].Pos.Line == result[j].Pos.Line &&
   //@       result[i].Pos.Col == result[j].Pos.Col && result[i].FullID == result[j].FullID)
   func Deduplicate(errs []backend.VerificationError) (result []backend.VerificationError)
   ```

**Status**: RESOLVED — C9 section added to `17b-parallel-workers.md`.

---

### Item 97 — BLOCKING CONTRADICTION: Plan 16 C9 uses `ThreadAttached(jvm)` with argument; predicate defined with no argument

**Criterion**: C1 — Contradictions between plan documents.

**Finding**: Plan 16's C9 verification specs (lines 308–309) write:
```go
//@ requires acc(backend.ThreadAttached(jvm), 1)
//@ ensures  acc(backend.ThreadAttached(jvm), 1)
```
But Plan 15 defines the predicate as `pred ThreadAttached()` — zero arguments — because the JVM
is a process-wide singleton and the predicate models OS-thread attachment state, not per-JVM
state. Plans 15b, 17, and 18 all correctly use `ThreadAttached()` with no argument. If Plan 16
is implemented as written, the Gobra verifier will reject the C9 annotations because
`ThreadAttached(jvm)` is not a declared predicate.

**Decision**: Remove the `jvm` argument from every invocation of `ThreadAttached()` across all
plan files. Scan all plans for `ThreadAttached(` and replace with `ThreadAttached()`.

**Status**: RESOLVED — fix to be applied to `16-silver-jni-builder.md` (the only offender found).

---

### Item 98 — Minor Stale Reference: Plan 00 bodyless-functions warning still lists Plan 20

**Criterion**: C1 — Contradictions between plan documents.

**Finding**: `00-overview.md` cross-cutting notes list Plan 20 in the "bodyless Viper functions"
warning. But Plan 20 explicitly resolves `goIntDiv` and `goIntMod` as body-carrying Silver
functions with extracted bodies from `IntegerEncoding.scala`. The warning note in plan 00 is
stale relative to plan 20's resolution.

**Decision**: Remove Plan 20 from the bodyless-functions warning in `00-overview.md`. Correct
list is Plans 21, 23, 25, 27.

**Status**: RESOLVED — fix to be applied to `00-overview.md`.

---

### Item 99 — Gap: Encoding module Init ordering unspecified; `InterfaceDomain` dflt may not be registered

**Criterion**: C2 — Gaps in coverage.

**Finding**: Plan 19 (translator core) initialises encoding modules via Init phases but does not
specify the order. Plan 25 registers `none_InterfaceDomain()` as the domain default for
`InterfaceDomain` during its Init. Plan 22 calls `ctx.Dflt(DomainType("InterfaceDomain"))` for
`*I` nil pointers. If a program has pointer-to-interface expressions but Plan 25's Init has not
run, the dflt lookup will panic (no registered default). Plan 19 implies all Init phases run at
translator startup but does not guarantee ordering.

**Decision**: The only hard ordering constraint is InterfaceEncoding (plan 25) before
PointerEncoding (plan 22), MapEncoding (plan 24), and ChannelEncoding (plan 28) — these three
all call into the Type domain or look up the InterfaceDomain default registered by plan 25. All
other encodings are order-independent. Safe rule: run plan 25's Init first; all others after in
any order. Plan 19 must add an explicit `MainTranslator` startup sequence documenting this
(e.g., a fixed ordered slice of encoding modules in `MainTranslator.init()`).

**Status**: RESOLVED — fix to be applied to `19-translator-core.md`.

---

### Item 100 — Gap: `SiliconInstance` vs `Backend` interface overlap creates potential confusion

**Criterion**: C2 — Gaps; C4 — Bad design.

**Finding**: Plan 15 defines `SiliconInstance` interface in `internal/backend/types.go`. Plan 18
adds a `Backend` interface to the same file, with identical methods (`Initialize`, `Verify`,
`Stop`). Plan 15b uses `SiliconInstance` for the worker goroutine parameter; Plan 33 uses
`Backend` to hold the active verifier. Both `SiliconFrontendAPI` and `CarbonFrontendAPI`
implement both interfaces.

**Decision**: Carbon backend (Plan 18) deferred indefinitely — see D12 in DECISIONS.md. The
`Backend` interface is not added. `SiliconInstance` is the only active backend interface.
Plan 33 does not add a `--backend` flag. The two-interface conflict is resolved by removal.

**Status**: RESOLVED — no code change needed; Plan 18 removed from active WBS.

---

### Item 101 — Gap: Plan 34 test path is ambiguous (Scala vs. Go location)

**Criterion**: C2 — Gaps.

**Finding**: Plan 34's Scope says "walk `src/test/resources/regressions/`" (the Scala Gobra
directory). But Plan 34's sentinel line examples use `tests/testdata/regressions/` (the
Go-Gobra test directory). Plan 35 clarifies that files are copied from the Scala location to
`tests/testdata/regressions/`. Plan 34's runner should target the Go-Gobra location only.
The stale Scala path reference may cause implementers to hardcode the wrong directory.

**Decision**: Replace `src/test/resources/regressions/` in Plan 34's Scope section with
`tests/testdata/regressions/`.

**Status**: RESOLVED — fix to be applied to `34-test-infrastructure.md`.

---

### Item 102 — Gap: `--chop-bound` defaults unclear across plan 16b and plan 33

**Criterion**: C2 — Gaps.

**Finding**: Plan 16b says "Default bound=1 (single merged program)." Plan 33 says
"`--chop-bound` (not defaulted to `--workers`)." These are not contradictory but together they
imply that without `--chop`, the bound is 1 (one merged program = no chopping). But the
interaction is: `--chop` enables unlimited bound; `--chop-bound N` caps it; without `--chop`
the chopper produces exactly 1 sub-program regardless. Plan 33 doesn't explicitly say what
the default value of `--chop-bound` is, which could cause implementers to leave the flag
with an unspecified default.

**Constraint identified**: If `--chop-bound > --workers`, the excess goroutines in
`DispatchChopped` pile up blocked on the semaphore, each holding a `*silver.Program` in memory.
No correctness problem, but memory scales with `--chop-bound`, not `--workers`. The natural
constraint is `--chop-bound ≤ --workers`.

**Decision**: Plan 33 must document/enforce: when `--chop` is set and `--chop-bound` is
unspecified, default to `--workers` (not MaxInt). If `--chop-bound > --workers`, clamp to
`--workers` and log a warning. This caps peak memory at `--workers` concurrent Silver programs.
Plan 16b's "unlimited" default must be revised to match — the effective ceiling is always the
worker count.

**Status**: RESOLVED — fix to be applied to `33-cli.md` and `16b-silver-chopper.md`.

---

### Item 103 — Improvement: Plan 35 skip list should reject unknown reason slugs at parse time

**Criterion**: C3 — Operational robustness.

**Finding**: Plan 35 defines a fixed set of reason slugs (`generics-not-implemented`,
`feature-not-implemented`, `known-z3-timeout`, `known-false-negative`, `known-false-positive`).
The skip list parser is not specified to reject unknown slugs; typos would silently be treated
as valid entries, defeating the enforcement goal of the two-mode design.

**Decision**: Accept the suggestion. Plan 35 must specify that the skip list parser treats any
unrecognised `SKIP:<reason>` slug as a parse error that fails the test run at startup. This
makes the fixed set of reason slugs machine-enforced, not just documented convention.

**Status**: RESOLVED — fix to be applied to `35-regression-suite.md`.

---

### Item 104 — Improvement: Plan 37 blocking-tier CI should include `internal/diagnostic/` from day one

**Criterion**: C3 — Operational robustness.

**Finding**: Plan 37's blocking tier starts with `internal/silver/`, `internal/ast/internal/`,
`internal/desugar/`, and `internal/translator/mangle.go`. `internal/diagnostic/` (plan 32a) is
a zero-dependency pure data structure package — trivially the simplest module to verify —  and
is conspicuously absent from the initial blocking tier.

**Decision**: Add `internal/diagnostic/` to the blocking tier in Plan 37. It should be the
first module verified and added, establishing the CI gate before any other module, and giving
the self-hosting pipeline a warm-up target with near-zero verification effort.

**Status**: RESOLVED — fix to be applied to `37-self-hosting-verify.md`.

---

## ROUND 9 — Full /review-plan ALL (all 41 plans + foundation docs, second pass)

### Item 105 — BLOCKING CONTRADICTION: Plan 33 still contains `--backend carbon` and Plan 18 dependency despite D12

**Criterion**: C1 — Contradictions between plan documents and decisions.

**Finding**: Item 100 was marked RESOLVED ("no code change needed; Plan 18 removed from active WBS") but Plan 33's actual text was never updated. Plan 33 still contains:
- In-scope bullet: "`--backend` (silicon/carbon), `--z3Exe`, `--boogieExe`"
- Dependencies section: "- [18-carbon-backend.md](18-carbon-backend.md) — Carbon backend; required because plan 33 exposes `--backend carbon` as a flag"

D12 states: "Plan 33 (CLI) does not wire a `--backend carbon` flag; `--backend` flag is omitted entirely or reserved for future use." The WBS scratchpad state (Section 2) correctly marks Plan 18 as `[DEFERRED]`, but the plan 33 text was not updated to match.

**Required fix**:
1. Remove `--backend (silicon/carbon)` from Plan 33's In-scope flag list; replace with `--backend silicon` or remove `--backend` entirely per D12.
2. Remove `--boogieExe` from Plan 33's flag list (Carbon-only flag).
3. Remove the Plan 18 dependency entry from Plan 33's Dependencies section.

**Status**: RESOLVED — `33-cli.md` updated: removed `--backend (silicon/carbon)` and
`--boogieExe` from scope, removed Plan 18 from Dependencies, added Carbon to Out-of-scope with
forward pointer. `00-overview.md` WBS row updated to `[DEFERRED — D12]` with no active
dependency arrows. `18-carbon-backend.md` given a prominent deferred notice and pickup
instructions. The Carbon dependency tree is now isolated: no active plan depends on Plan 18.

---

### Item 106 — LOGIC ERROR: Plan 13 C9 NodeCount postcondition is false when `--overflow` is enabled

**Criterion**: C9 — Self-verification annotations must be correct.

**Finding**: Plan 13's C9 postcondition for the transform pipeline:
```go
//@ ensures len(diags) == 0 ==> result.NodeCount() <= old(prog.NodeCount())
```
The overflow transform (`overflowChecks`) inserts range-assertion nodes into the AST for every arithmetic expression when `--overflow` is enabled. These insertions are correct behavior and produce no diagnostics (`len(diags) == 0`). So with `--overflow` enabled, it is possible that `len(diags) == 0` AND `result.NodeCount() > old(prog.NodeCount())` simultaneously — directly falsifying the postcondition.

**Required fix**: Weaken the spec to account for overflow-check insertions. Options:
1. Conditionalize: `//@ ensures !cfg.Overflow && len(diags) == 0 ==> result.NodeCount() <= old(prog.NodeCount())`
2. Replace with a monotone bound: `//@ ensures len(diags) == 0 ==> result.NodeCount() <= old(prog.NodeCount()) + cfg.Overflow ? prog.ArithExprCount() : 0`
Option 1 is simpler to state; Option 2 is stronger but requires `ArithExprCount()` ghost method.

**Status**: RESOLVED — C9 annotation conditioned on `!cfg.Overflow`; prose note updated to
explain why overflow-check insertions legitimately increase node count without diagnostics.

---

### Item 107 — NOTATION: Plans 16 and 17 C9 specs use `backend.ThreadAttached()` but predicate lives in `jvm` sub-package

**Criterion**: C4 — Cross-plan references must use exact names.

**Finding**: `pred ThreadAttached()` is declared in `internal/backend/jvm/jvm.go` (Plan 15). Plans 16 and 17 reference it in their C9 specs as `acc(backend.ThreadAttached(), 1)`. From the packages that Plans 16 and 17 deliver (`internal/backend/silver/builder.go` and `internal/backend/silicon/silicon.go`), the correct import path is `jvm.ThreadAttached()`. The package qualifier `backend` refers to the parent package, not the `jvm` subpackage — as written, the spec would not compile.

Note: Item 97 fixed the argument count (`ThreadAttached(jvm)` → `ThreadAttached()`); this finding is separate: the package qualifier is also wrong.

**Required fix**: Replace `backend.ThreadAttached()` with `jvm.ThreadAttached()` in Plans 16 and 17 C9 specs.

**Status**: RESOLVED — both plans updated to `jvm.ThreadAttached()`. Plan 16 already imports
`jvm` (takes `*jvm.JVM` parameter). Plan 17 import note updated: `silicon` safely imports
`jvm` for annotation purposes — no cycle because `jvm` depends on the `backend.SiliconInstance`
interface, not on the `silicon` package.

---

### Item 108 — GAP: Plan 34 does not specify skip-list loading or slug validation, but Plan 35 requires both of the runner

**Criterion**: C5 — Every artifact a plan consumes must be produced by a listed dependency.

**Finding**: Plan 35 specifies that the skip list parser "must reject any unrecognised reason slug as a parse error at startup, failing the test run before any tests execute." It also specifies the two-mode design: skip-listed tests must be run (not omitted) and emit `UNEXPECTED_PASS:` on unexpected pass.

Plan 34 (which delivers `internal/testing/runner.go`) describes the `UNEXPECTED_PASS:`/`UNEXPECTED_FAIL:` sentinel format but does not mention: (a) loading `tests/testdata/skip.txt` at startup, (b) validating slugs against the fixed set, or (c) the two-mode run-expected-to-fail behavior. Plan 35 specifies behavior that Plan 34's deliverable must implement but Plan 34's own scope and deliverables section is silent about it.

**Required fix**: Add to Plan 34's Scope section: loading `tests/testdata/skip.txt` at startup; validating slugs against the fixed set from Plan 35; failing the run on unrecognized slugs; running skip-listed tests in "expected-to-fail" mode and emitting `UNEXPECTED_PASS:` if they pass.

**Status**: RESOLVED — `34-test-infrastructure.md` updated. Added `SkipConfig` struct
(with `File` and `ValidSlugs` fields) to In-scope; runner accepts it at construction time,
validates slugs against the caller-supplied set, and runs skip-listed tests in expected-to-fail
mode. Slug set definition stays in Plan 35, avoiding circular content duplication.

---

### Item 109 — DOCUMENTATION: CRITERIA.md C1 enumeration of reference documents omits `scratchpad.md`

**Criterion**: C1 — Internal consistency.

**Finding**: CRITERIA.md C1 states: "Current reference documents: `DECISIONS.md`, `CONTEXT.md`, `COVERAGE.md`." The `00-overview.md` Reference Documents table lists four entries: `CONTEXT.md`, `DECISIONS.md`, `COVERAGE.md`, `scratchpad.md`. `scratchpad.md` is missing from CRITERIA.md's enumeration, so C1 as written is internally inconsistent with the master index.

**Required fix**: Update CRITERIA.md C1 to list `scratchpad.md` in its "Current reference documents" enumeration.

**Status**: RESOLVED — `CRITERIA.md` C1 updated to include `scratchpad.md` in the reference
documents enumeration.

---

## ROUND 10 — Full /review-plan ALL (all 41 plans + foundation docs, third pass)

### Item 110 — Minor Contradiction: Plan 27 claims to "create" KNOWN_LIMITATIONS.md, but Plan 01 already creates it

**Criterion**: C1 — Contradictions between plan documents.

**Finding**: `27-encoding-methods.md` contains the note: "KNOWN_LIMITATIONS.md: created by this plan." Plan 01's Deliverables section creates `gobra-go/KNOWN_LIMITATIONS.md` as an empty file in the initial project skeleton. Plans 20 and 28 also append to it. Plan 27 cannot create a file that plan 01 already creates.

**Required fix**: Update Plan 27's KNOWN_LIMITATIONS.md note from "created by this plan" to "appended to by this plan." Match the language used in `00-overview.md`'s cross-cutting note which says "plans 20, 27, 28 append to it (plan 01 creates it)."

**Status**: RESOLVED — `27-encoding-methods.md` Deliverables updated: "created by this plan" → "created as an empty file by plan 01; appended to by this plan."

---

### Item 111 — Logic Error: Plan 29 ADT rank-decrease axiom assumes rank function exists for non-ADT field types

**Criterion**: C3 — Encoding correctness.

**Finding**: Plan 29's rank-decrease axiom template is:
```
forall f: F :: {rank$X(X_C1(f))} rank$F(f) < rank$X(X_C1(f))
```
This assumes a `rank$F` Silver function exists for every field type `F`. For ADT-typed fields, `rank$F` is defined in the field type's own domain. For primitive Silver types (Int, Bool, Ref, Seq[T], etc.), no `rank$` function exists — Silver domains are nominal and only ADT-typed nodes generate rank functions. As written, the axiom template would be emitted for all constructor fields regardless of type, producing ill-formed Silver (referencing undefined functions) for any ADT that has a non-ADT-typed field.

**Required fix**: Plan 29 must clarify that rank-decrease axioms are only emitted for constructor fields whose type is itself a Gobra ADT (i.e., whose type has a corresponding `rank$` Silver function). Fields of primitive type, Silver Seq, Silver Map, Silver Ref, etc. get no rank-decrease axiom.

**Status**: RESOLVED — `29-encoding-adts.md` rank-decrease axiom block updated with an explicit comment stating the axiom is only emitted for fields whose type is a Gobra ADT with its own `rank$` function; primitive Silver types are explicitly excluded.

---

### Item 112 — Gap: Plan 19 TupleDomain dflt registration is underspecified

**Criterion**: C2 — Gaps.

**Finding**: Plan 19 specifies a `Dflt` formula for TupleDomain:
```
gobra__Tuple{N} → gobra__tuple{N}(dflt(T0), ..., dflt(T{N-1}))
```
This formula is type-parameterized: it depends on the concrete type arguments T0..T{N-1} of each tuple instance. Plan 19 uses `RegisterDomainDefault` for domain defaults, but that mechanism takes a static Silver expression at registration time. The tuple dflt formula is not a static expression — it depends on the concrete type instantiation. Plan 19 does not explain who calls `RegisterDomainDefault` for TupleDomain, what expression is registered, or how the type-dependent formula is resolved at runtime. This is a genuine gap: without this, the translator will either panic (no registered default) or emit incorrect dflt values for tuple types.

**Required fix**: Plan 19 must specify the dflt mechanism for TupleDomain. Options: (a) register a dflt lazily per concrete tuple type when first instantiated; (b) emit an explicit Silver domain axiom `axiom { gobra__Tuple{N}_default == gobra__tuple{N}(dflt(T0), ...) }` instead of using RegisterDomainDefault; (c) treat TupleDomain dflt as a domain axiom, not a Go-side registration. The plan must pick one and be explicit.

**Status**: RESOLVED — `ctx.Dflt` signature changed from `silver.Type` to `internal.Type` in
plan 19, matching Scala Gobra's `in.DfltVal` dispatch approach. The `RegisterDomainDefault`
mechanism is removed from plans 19 and 25; each encoding handles `DfltVal` for its own types
via the expression encoding dispatch. Plans 22, 23, 24, and 25 updated accordingly: plan 22
C9 now uses `ctx.Dflt(ptr.Elem)` (passing internal type directly), plan 23 C9 uses
`ctx.Dflt(internal.SliceType{Elem: elemType})`, plan 24 C9 uses `ctx.Dflt(t)`, and plan 25
replaces the `RegisterDomainDefault` call with a description of expression-encoder dispatch
for `DfltVal(InterfaceT)`. The dflt table in plan 19 now maps internal Go types (not Silver
domain names) to their zero-value Silver expressions.

---

### Item 113 — Misleading Note: Plan 20 defers `unsafe.Sizeof`/`unsafe.Alignof` to encoding stage, but `unsafe` is rejected upstream

**Criterion**: C2 — Gaps / misleading documentation.

**Finding**: Plan 20 contains a note deferring `unsafe.Sizeof` and `unsafe.Alignof` to a future encoding. However, plan 07 (package resolver) and plan 08 (type checker core) both explicitly reject `import "unsafe"` with a diagnostic. COVERAGE.md's open-questions table also lists `unsafe` as requiring an explicit rejection diagnostic at the resolver or type-checker level. If `unsafe` is rejected before reaching the encoding stage, no encoder will ever see `unsafe.Sizeof`/`unsafe.Alignof` — the "deferred" note in plan 20 implies there is future encoding work to do, but there is none: the rejection happens entirely upstream.

**Required fix**: Update Plan 20's note to clarify that `unsafe` is rejected at the import level (plan 07/08), so no encoding-stage handling is needed or expected. The "deferred" framing should be removed to avoid future implementers adding dead encoder code.

**Status**: RESOLVED — `20-encoding-primitives.md` updated: Scope bullet changed to "not in scope"; Open Questions section replaced with a Resolved Questions entry explaining that `unsafe` is rejected by plan 07/08 and no encoder-level handling is needed or should be added.

---

## ROUND 11 — Full /review-plan ALL (all 41 plans + foundation docs, fourth pass)

| Round | Scope | Items | Status |
|---|---|---|---|
| 11 | Full /review-plan ALL fourth pass (all 41 plans + foundation docs) | 114–118 | In progress |

### Item 114 — SIGNIFICANT Logic Error: Plan 31 C9 ghost field on stdlib type `*types.Package` is unimplementable

**Criterion**: C9 — Self-verification annotations must be correct.

**Finding**: Plan 31's C9 section defines:
```go
//@ ghost field stubSourceTag bool  // on *types.Package; set only by stub-loading path
```
This annotates a ghost field on `*types.Package`, which is `go/types.Package` — a stdlib type not owned by the Go-Gobra codebase. Gobra ghost fields can only be added to types declared in the verified code, not to external package types. If implemented as written, the Gobra verifier will reject the annotation because `*types.Package` is a foreign type without a Gobra source file.

**Required fix**: Replace the ghost field with a `map[*types.Package]bool` maintained inside the importer struct (plan 10). The C9 predicate `isFromStub(pkg, path)` should be defined in terms of this map (a ghost field of the importer, not of the stdlib type).

**Status**: RESOLVED — `31-encoding-builtins.md` updated: removed `//@ ghost field stubSourceTag bool` on `*types.Package`; added `//@ ghost field stubLoaded map[*types.Package]bool` on `*Importer` (plan 10's type); `isFromStub` redefined as a pure method on `*Importer` reading that map; both the stub-resolution postcondition and the ghost-predicate-ownership section updated to use the receiver form `i.isFromStub(pkg, path)`.

---

### Item 115 — MINOR Notation: Plan 06 C9 `bytes.Count(out, '\n')` takes rune, not []byte

**Criterion**: C9 — Self-verification annotations must be correct.

**Finding**: Plan 06's C9 spec comment uses `bytes.Count(out, '\n')`. Go's `bytes.Count` signature is `func Count(s, sep []byte) int` — the separator must be `[]byte`, not a rune. The correct notation is `bytes.Count(out, []byte("\n"))`. As written, the spec comment would not compile.

**Required fix**: Update Plan 06 C9 to use `bytes.Count(out, []byte("\n"))`.

**Status**: RESOLVED — `06-gobrafier.md` C9 annotation updated to `bytes.Count(out, []byte("\n"))` and `bytes.Count(src, []byte("\n"))`.

---

### Item 116 — MINOR Logic Error: Plan 22 C9 `isNilPointer(ptr)` checks the type object, not the value expression

**Criterion**: C9 — Self-verification annotations must be correct.

**Finding**: Plan 22's C9 postcondition on `EncodePointer`:
```go
//@ ensures isNilPointer(ptr) ==> result == ctx.Dflt(ptr.Elem)
func (e *PointerEncoding) EncodePointer(ctx Context, ptr *internal.PointerT, val internal.Expr)
```
`ptr` is the pointer *type* (`*internal.PointerT`), not a pointer *value*. Nil-ness is a property of `val` (the value expression being encoded), not of the type descriptor `ptr`. The predicate `isNilPointer(ptr)` testing the type object is semantically wrong — the type object is never nil when called from the translator's dispatch.

**Required fix**: Replace `isNilPointer(ptr)` with `isNilValue(val)`, checking the value expression: `//@ ensures isNilValue(val) ==> result == ctx.Dflt(ptr.Elem)`.

**Status**: RESOLVED — `22-encoding-pointers.md` C9 updated: `isNilPointer(ptr)` → `isNilValue(val)` with explanatory comment that `val` is the value expression and `ptr` is the type descriptor, which is never nil in the translator.

---

### Item 117 — MINOR Design Concern: Plan 25 C9 `EncodeInterface` takes `*types.Interface` (stdlib) not `*internal.InterfaceType`

**Criterion**: C4 — Cross-plan references; layering discipline.

**Finding**: Plan 25's C9 spec:
```go
func EncodeInterface(ctx *Context, iface *types.Interface) *silver.Domain
```
`*types.Interface` is from the stdlib `go/types` package. The translator operates on internal AST types (`*internal.InterfaceType` from plan 11). Passing a stdlib type across the translator boundary is a layering violation: the translator should not depend on `go/types` directly; it should receive internal AST nodes and access `go/types` information through `ctx.TypeInfo()` if needed.

**Required fix**: Change the parameter type to `*internal.InterfaceType`. The implementation may call `ctx.TypeInfo()` to look up the corresponding `go/types` information if needed.

**Status**: RESOLVED — `25-encoding-interfaces.md` C9 updated: `EncodeInterface` parameter changed from `*types.Interface` to `*internal.InterfaceType`; `BoxValue` `T` parameter changed from `types.Type` to `internal.Type`. Both carry explanatory notes about the layering rule.

---

### Item 118 — MINOR Gap: Plan 35 `HasGenericDecl` pre-population applied to `.gobra` files may need gobrafication first

**Criterion**: C2 — Gaps in coverage.

**Finding**: Plan 35 says to pre-populate the generics skip list by calling `go/parser.ParseFile` on each `.gobra` test file and passing the result to `HasGenericDecl`. Some `.gobra` test files use Gobra-specific inline syntax (e.g., `ghost` parameters in function signatures) that is not valid Go and will cause `go/parser` to return a partial or error parse. For detecting generic declarations, `go/parser` needs to reach the `TypeParams` field of each `FuncDecl`/`TypeSpec`, which may be prevented if a prior syntax error causes the parser to bail out of a top-level declaration.

**Required fix**: Plan 35 (or the pre-population script in plan 34) should specify: call `Gobrafy(src, filename)` (plan 06) on each `.gobra` file before passing to `go/parser`, so the file is valid Go before AST inspection. The error mode for `go/parser` should be `parser.AllErrors` to obtain a maximally-complete partial AST even when the gobrafied content contains residual issues. Alternatively, document the known failure mode: for files that `go/parser` cannot parse even after gobrafication, conservatively treat `HasGenericDecl` as `false` (may produce false negatives in pre-population, caught by the actual test run).

**Status**: RESOLVED — `35-regression-suite.md` updated: added a "Caller requirement" note inside `HasGenericDecl`'s doc comment explaining why gobrafication is required; replaced the single-sentence pre-population description with a full code sequence (`Gobrafy` → `parser.ParseFile` → `HasGenericDecl`) including nil-check and conservative fallback on gobrafication failure.

---

## ROUND 12 — Full /check-plan (all 41 plans + foundation docs, fifth pass)

| Round | Scope | Items | Status |
|---|---|---|---|
| 12 | Full /check-plan (all 41 plans + foundation docs) | 119–123 | In progress |

### Item 119 — C5 FAIL: Plan 32 missing plan 15 dependency for `VerificationError` type

**Criterion**: C5 — Every artifact a plan consumes must be produced by one of its listed dependencies.

**Finding**: Plan 32 (`internal/reporting/reporter.go`) directly uses `VerificationError`, `VerificationResult.Errors`, `VerificationError.Node`, and `VerificationError.Pos` — all types/fields defined in plan 15's deliverable `internal/backend/types.go`. Plan 32's Dependencies section lists: `32a`, `14`, `16`, `17`. Plan 15 is not listed. The comment on the plan 17 dependency says "source of VerificationError objects," but plan 17's deliverable is the `Verify()` function; the `VerificationError` *type* is plan 15's artifact. Per C5: "Every artifact a plan consumes must be produced by one of its listed dependencies — not by an unlisted plan."

**Required fix**: Add `[15-jni-setup.md](15-jni-setup.md) — defines `VerificationResult`, `VerificationError`, `VerificationError.Node`, `VerificationError.Pos` in `internal/backend/types.go`; reporter accesses these fields directly` to plan 32's Dependencies section.

**Status**: RESOLVED — `32-reporter.md` Dependencies updated: plan 15 added between plan 14 and plan 16; plan 17's description updated to clarify it delivers `Verify()` while plan 15 owns the types.

---

### Item 120 — C5 FAIL: Plan 35 missing plan 06 dependency for `frontend.Gobrafy`

**Criterion**: C5 — Every artifact a plan consumes must be produced by one of its listed dependencies.

**Finding**: Plan 35's pre-population sequence (added in Item 118 fix) directly calls `frontend.Gobrafy(src, path)`. `Gobrafy` is plan 06's deliverable (`internal/frontend/gobrafier.go`). Plan 35's Dependencies section lists only plan 34. Plan 06 is not listed. Plan 34→33→(various) does not transitively surface plan 06 in plan 35's listed dependencies. Per C5, plan 35 must list plan 06 because it directly consumes `Gobrafy`.

**Required fix**: Add `[06-gobrafier.md](06-gobrafier.md) — `Gobrafy(src []byte, filename string) ([]byte, []Diagnostic)` called by the pre-population sequence` to plan 35's Dependencies section.

**Status**: RESOLVED — `35-regression-suite.md` Dependencies updated: plan 06 added before plan 34 with a note explaining it is called by the pre-population sequence.

---

### Item 121 — C9 FAIL: Plan 32a missing C9 section

**Criterion**: C9 — Silence is an automatic failure.

**Finding**: Plan 32a defines the `Diagnostic` struct and `Category` type in `internal/diagnostic/`. It has no `## Verification Specifications (C9)` section and no explicit "C9: N/A" statement. The criterion requires either formal specs or an explicit N/A rationale. Every other data-type plan (11, 14) has a C9 section. Plan 32a is silent.

**Required fix**: Add a `## Verification Specifications (C9)` section to `32a-diagnostics.md`. Since plan 32a defines only data types with no behavioral functions, the appropriate content is an explicit N/A statement:

> **C9: N/A** — This plan defines only data type declarations (`Diagnostic` struct, `Category` int). There are no functions with pre/postconditions to specify. The struct fields have no invariants beyond Go type-system guarantees. The unit test ("construct a Diagnostic, verify the fields are accessible") is the validation method per C8.

**Status**: OPEN

---

### Item 122 — C9 FAIL: Plan 34 C9 deferred to plan 35; skip-list loader has no spec

**Criterion**: C9 — Silence or deferring verification to a later stage is an automatic failure.

**Finding**: Plan 34 defines two functions with verifiable behavioral logic:
1. `HasGenericDecl(*ast.File) bool` — its Gobra postcondition is "specified in plan 35" (plan 34's text references plan 35 for the spec rather than stating it).
2. The skip-list loader/validator (startup behavior: load `tests/testdata/skip.txt`, validate slugs against `ValidSlugs`, fail on unknown slug) — no spec stated anywhere.

Plan 34 has no `## Verification Specifications (C9)` section. The spec for `HasGenericDecl` is physically located in plan 35, not plan 34 (the owning plan). C9 requires the owning plan to "describe what safety properties the component will prove about itself."

**Required fix**: Add a `## Verification Specifications (C9)` section to `34-test-infrastructure.md` with:
1. The `HasGenericDecl` spec (copy from plan 35 into plan 34's C9 section; plan 35 may retain it as the specification driver).
2. A spec for the skip-list loader: e.g.,
   ```go
   //@ requires cfg.File == "" || fileExists(cfg.File)
   //@ ensures  err == nil ==> list != nil
   //@ ensures  err != nil ==> list == nil  // fail-fast on unknown slug
   //@ decreases
   func loadSkipList(cfg SkipConfig) (list *SkipList, err error)
   ```
3. A termination annotation for the test runner loop (iterates over a finite set of discovered test files):
   ```go
   //@ decreases len(pending)
   ```

**Status**: OPEN

---

### Item 123 — C9 FAIL: Plans 35, 36, 37 missing explicit C9 N/A statements

**Criterion**: C9 — Silence is an automatic failure.

**Finding**: Plans 35, 36, and 37 have no `## Verification Specifications (C9)` section and no explicit N/A statement. Each is a reasonable C9 N/A candidate (plan 35 = test corpus copy + CI job; plan 36 = annotation authoring, IS the C9 work; plan 37 = verification run + CI job), but the criterion requires explicit acknowledgment.

**Required fix**: Add a `## Verification Specifications (C9)` section to each plan with a brief N/A justification:

- **Plan 35**: "C9: N/A — This plan's deliverables are test files, `skip.txt`, CI configuration, and a Makefile target (`prune-skips`). These are not Go functions with verifiable pre/postconditions. The Gobra spec for `HasGenericDecl` (used during pre-population) is owned by plan 34 and specified in this plan for reference; plan 37's blocking tier verifies it."
- **Plan 36**: "C9: N/A — This plan IS the annotation work. Its deliverables are Gobra annotations (`//@ requires`, `//@ ensures`, invariants) added to all other plans' deliverable files. The C9 requirement is satisfied transitively: each annotated module gains a C9 section as part of plan 36's work."
- **Plan 37**: "C9: N/A — This plan delivers a CI job and `SELF_HOSTING.md` documentation. No new Go functions with verifiable logic are implemented. The self-hosting verification run (Go-Gobra verifying itself) is the C9 validation for all other plans collectively, not a C9 target for plan 37 itself."

**Status**: OPEN
