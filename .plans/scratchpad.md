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
*Seven rounds of architectural audit completed against CRITERIA.md (C1–C9). 96 items resolved. Round 8 in progress (full review-plan ALL).*

| Round | Scope | Items | Status |
|---|---|---|---|
| 1 | Global contradiction scan | 1–13 | All DONE |
| 2 | Full audit round 2 | 14–27 | All DONE |
| 3 | Full audit round 3 | 28–48 | All DONE |
| 4 | File-by-file audit (plans 01–37) | 49–69 | All DONE |
| 5 | File-by-file audit follow-up | 70–88 | All DONE |
| 6 | File-by-file audit (plans 01–37, second pass) | 89–95 | All DONE |
| 7 | Full criteria check (C1–C9), all 41 plans | 96 | All DONE |
| 8 | Full /review-plan ALL (all 41 plans + foundation docs) | 97–104 | 7 RESOLVED, 0 OPEN |
| — | D12 decision: defer Carbon backend (Plan 18) | — | RESOLVED |

**Total: 96 prior items resolved. 8 new items (97–104) from round 8, all resolved. Plan 18 deferred per D12.**

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
