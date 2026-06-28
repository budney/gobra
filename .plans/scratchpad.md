# Go-Gobra Rewrite: Automation Scratchpad
> **Rule for AI Agent:** Do not edit any other `.plans/*.md` files until you have updated the active constraints, dependency states, and task items in this scratchpad.

## 1. GLOBAL SYMBOL & COMPONENT REGISTRY
*Tracks artifact ownership (C3), cross-plan type references (C4), and dependency links (C5).*

### Package Ownership
| Package / Artifact | Owner Plan |
|---|---|
| `internal/ast/frontend/` (PNode, PDecl, PStmt, PGoStmt, PBlockStmt, PFile, PFuncDecl, PMethodDecl, PTypeDecl, PForStmt, PRangeStmt, etc.) | 03 |
| `Visitor` interface (frontend) | 03 |
| `internal/ast/internal/` (Method, Function, FPredicate, Expr, Stmt, etc.) | 11 |
| `Visitor` interface (internal) | 11 |
| `internal/silver/` (Program, Member, Node, NodeInfo, VprInfo, NoInfo, AnnotationInfo, ConsInfo) | 14 |
| `internal/silver/printer.go` (Print) | 14 |
| `internal/backend/jvm/jvm.go` (JVM, Start, Stop, JVMConfig, WorkerPool skeleton, workerJob, Stop()) | 15 |
| `internal/backend/jvm/jvm.go` (NewPool, Submit, full Silicon-aware worker goroutine) | 15b |
| `internal/backend/types.go` (VerificationResult, VerificationError, SiliconInstance interface, SiliconConfig) | 15 |
| `internal/diagnostic/` (Diagnostic struct) | 32a |
| `internal/reporting/tags.go` (tag constants) | 32 |
| `internal/backend/dedup.go` (Deduplicate) | 17b |
| `internal/backend/types.go` (Backend interface) | 18 |

### Pipeline Stage I/O Types
| Stage | Input Type | Output Type | Error Type |
|---|---|---|---|
| Go Parser (04) | `string` (file path) | `*PFile` | `[]Diagnostic` |
| Annotation Parser (05) | `string` (comment text) | `[]PNode` | `[]Diagnostic` |
| Package Resolver (07) | `[]string` (paths) | `[]*PFile` | `[]Diagnostic` |
| Type Checker (08) | `*frontend.PPackage` + `types.Importer` | `*TypeInfo` | `[]Diagnostic` |
| Desugarer (12) | `*frontend.PPackage` + `*TypeInfo` | `*internal.Program` | `[]Diagnostic` |
| Translator (19) | `*internal.Program` | `*silver.Program` | `[]Diagnostic` |
| Silicon Backend (17) | `*silver.Program` | `*backend.VerificationResult` | `[]Diagnostic` |
| Reporter (32) | `*backend.VerificationResult` | formatted output | — |

### Synchronization Contracts
| Resource | Mechanism | Plan |
|---|---|---|
| JVM singleton | `sync.Once` | 15 |
| JNI thread attachment | `runtime.LockOSThread()` + `defer DetachCurrentThread()` | 15 |
| Worker pool channel | buffered `chan workerJob` | 15 |
| SiliconFrontendAPI per worker | goroutine-local (no sharing) | 15b |

---

## 2. GLOBAL WBS SYNCHRONIZATION STATE
*Total Files: 41 (per WBS) | Target for this run: 03, 11, 14, 15*

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
- [ ] 18-carbon-backend.md

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

## 3. ACTIVE VERIFICATION LOOP LOG

### Active Blockers / Contradictions (To Be Resolved by Agents)
- [x] **[CIRCULAR-IMPORT-BLOCKER]:** RESOLVED — `internal/backend/types.go` (package `backend`) introduced as neutral shared package. `jvm` and `silicon` both import parent `backend`; neither imports the other.
- [x] **[STRUCT-MISMATCH]:** RESOLVED — canonical `VerificationResult` lives in `internal/backend/types.go` (plan 17 ownership). Plans 15 and 15b reference it; plan 15's local definition removed.
- [x] **[OVERLOAD-ERR]:** RESOLVED — plan 15 delivers `WorkerPool` struct + `Stop()` skeleton only; `NewPool` explicitly owned by plan 15b with supersession noted.
- [x] **[DIAGNOSTIC-LAYERING]:** RESOLVED — plan 04 deliverables now explicitly import from `internal/diagnostic/` (plan 32a) with a warning against importing `internal/reporting`.
- [ ] 1**C1**: `scratchpad.md` present in `.plans/` but not listed in WBS or Reference Documents in `00-overview.md` (pre-existing). NOTE: `00-overview.md` Reference Documents table already includes `scratchpad.md` — this was resolved.
- [ ] **C4** (00-overview.md): Cross-Cutting Notes says "defined in plan 32" for Diagnostic — should say "defined in plan 32a". Pre-existing; not in scope for this run.
- [ ] **C5** (33): Dependencies omits `15b`; pre-existing. Not in scope for this run.
- [ ] **C4** (17b): Objective incorrectly references plan 15's scope. Pre-existing; not in scope.


### Current Target Files: 03, 11, 14, 15
| File | Phase 2 Injected | Review Status | Check Status |
|---|---|---|---|
| 03-frontend-ast.md | NEEDS FIX (C10→C9) | DONE | [x] PASSED |
| 11-internal-ast.md | YES (C9 present) | DONE | [x] PASSED |
| 14-silver-ast.md | YES (C9 present) | DONE | [x] PASSED |
| 15-jni-setup.md | YES (C7+C9 present) | DONE | [x] PASSED |

### Issues Found in Phase 2 Audit
1. **03-frontend-ast.md line 241**: `### Verification Specifications (C10)` — wrong criterion number; **FIXED** (renamed to C9).

### Issues Found in /review-plan (Phase 3)

#### PIPELINE & BACKEND BLOCKED (FOUND IN GLOBAL AUDIT)
- **[C4/C6 — 15 vs 17]**: Struct mismatch on `VerificationResult`. Plan 15 defines `Errors []Diagnostic` in `internal/backend/jvm/jvm.go`; Plan 17 defines an incompatible shape in `internal/backend/silicon/silicon.go`. 15b uses a non-existent field.
- **[C3 — 15 vs 15b]**: `NewPool` duplicated constructor crash in `internal/backend/jvm/jvm.go`.
- **[C5 — 15b] Circular Import**: 15b passes `SiliconConfig` from package `silicon` into package `jvm`, but `silicon` already depends on `jvm`.
- **[C4 — 16 vs 15b] Worker Deadlock**: Plan 16 expects `Build()` to dispatch via channels; 15b calls it synchronously inside the worker, creating a self-deadlock loop.
- **[C5 — 04 vs 32a] Layering Break**: Plan 04 attempts to pull `Diagnostic` from `internal/reporting` instead of `internal/diagnostic/`.

#### SIGNIFICANT LOGIC & SPEC ERRORS (FOUND IN GLOBAL AUDIT)
- **[08:Core]** — Key notes describe a unified `map[PNode]types.Type`, but deliverable uses separate `*types.Info` and `GhostTypeInfo` fields.
- **[08 vs 11 vs 12]** — `TypeInfo.Addressable` is uncalled dead code; downstream translation stages call node or stdlib selectors instead.
- **[16b:Chopper]** — Filename and format mismatch (`GobraChopper.conf` vs `gobra-chopper.json`).
- **[06:Gobrafier]** — Dependency lists 32a for diagnostics, but signature returns basic `error`.
- **[28 vs 25]** — Channel encoding requires `chan_T_Type()`, which is entirely missing from the Plan 25 type domain definition.
- **[12:Desugarer]** — Logic error in variadic tracking. Inverting ellipsis validation will misclassify valid Go call expressions.
- **[35:Testing]** — Contradictory entry criteria for phase 36 (95% gate vs parallel tracking).

#### LOCAL FOCUS TARGET ISSUES (03, 11, 14, 15)
- **[C5 — 15-jni-setup.md]** — Dependencies: `workerJob` uses `*silver.Program` from plan 14, but plan 15 only lists plan 01 as a dependency. Must add plan 14 as a dependency.
- **[C3 — 15-jni-setup.md]** — Deliverables: `VerificationResult` type appears in `workerJob` and `Submit` signature but is not claimed as a deliverable by any plan. Must declare ownership in plan 15 (or 15b) and add it here.
- **[C9 — 11-internal-ast.md]** — Verification Specifications: Section describes plan 12's (Desugarer) transformation contracts, not plan 11's own node-type invariants. Must replace with plan 11's own specs (predicates on node types). The misplaced text should move to plan 12.
- **[C4 — 11-internal-ast.md]** — Verification Specifications: References "14-silver-ast.md" as the consumer of internal AST output — wrong. Plan 14 defines Silver IR struct types; the internal AST is consumed by the translator (plan 19). Must fix reference.
- **[C9 — All Target Files]** — C9 sections state intent but include no concrete `//@ requires` / `//@ ensures` annotation examples. Must add at least one illustrative annotation per file.
- **[C9 — 03-frontend-ast.md]** — `acc(node.Underlying)` is not a valid Gobra field access (no `Underlying` field exists on wrapper nodes). Must use actual field names (e.g., `acc(n.GoFunc)` for `PFuncDecl`).
- **[Minor — 15-jni-setup.md]** — Scope: In-scope list omits `WorkerPool` skeleton and `workerJob` type. Must add them.

### Remediation Queue (ordered by severity)

#### CRITICAL GLOBAL ARCHITECTURAL BLOCKERS
1. ~~**Fix package layout (C5)**: Resolve the `jvm` ➔ `silicon` ➔ `jvm` circular import introduced by plan 15b passing `SiliconConfig` directly into the JVM package.~~ DONE
2. ~~**Harmonize `VerificationResult` (C4/C6)**: Redefine `VerificationResult` in a shared neutral types package to eliminate the structural mismatch between plans 15, 15b, and 17.~~ DONE
3. ~~**De-duplicate constructors (C3)**: Refactor `NewPool` in plan 15b to explicitly replace or cleanly differentiate itself from plan 15's constructor signature to prevent Go compilation errors.~~ DONE
4. ~~**Fix worker deadlock (C4)**: Align plan 16 and 15b so that synchronous worker routines do not self-deadlock against internal channel queues inside `Build()`.~~ DONE
5. ~~**Fix pipeline layering break (C5)**: Rewrite plan 04 to pull the `Diagnostic` type from `internal/diagnostic/` (plan 32a) instead of violating boundaries via `internal/reporting`.~~ DONE

#### LOCAL FOCUS TARGET EDITS (FOUNDATION & PIPELINE)
6. ~~Fix `03-frontend-ast.md`: rename `C10` → `C9`.~~ DONE
7. ~~Fix `15-jni-setup.md` (C5): Add plan 14 as a mandatory dependency.~~ DONE (already present in file)
8. ~~Fix `15-jni-setup.md` (C3): Explicitly claim ownership and declare `VerificationResult`.~~ DONE (already present in file)
9. ~~Fix `11-internal-ast.md` (C9): Replace misplaced Desugarer text with plan 11's own invariants.~~ DONE (already correct in file)
10. ~~Fix `11-internal-ast.md` (C4): Update consumer reference from "14-silver-ast.md" to plan 19.~~ DONE (no stale reference found in file)
11. ~~Fix `03-frontend-ast.md` (C9): Correct `.Underlying` and add concrete annotation examples.~~ DONE (already uses `acc(f.GoFunc)` etc.)
12. ~~Fix `11`, `14`, `15` (C9): Add concrete `//@ requires`/`//@ ensures` examples.~~ DONE (all three files have examples)
13. ~~Fix `15-jni-setup.md` (Minor): Add `WorkerPool` skeleton and `workerJob` to In-scope list.~~ DONE (already present in file)

#### PIPELINE LOGIC & SYSTEM GATES (DOWNSTREAM REVISIONS)
14. ~~**Fix `12-desugarer.md` (Logic)**: Correct the variadic spread tracking rules to explicitly validate `ast.CallExpr.Ellipsis` instead of relying entirely on type shapes.~~ DONE — Branch 1 now requires `ast.CallExpr.Ellipsis.IsValid()` as primary discriminant; Branches 2–4 explicitly note Ellipsis is NOT set. Added warning that type-shape-only detection misclassifies `f(g())` as a spread.
15. ~~**Fix `08-type-checker-core.md` (Spec)**: Reconcile the unified type map notes with the split `*types.Info` and `GhostTypeInfo` struct deliverables.~~ DONE — "Side-table for type info" note rewritten to explain two distinct side-tables (stdlib `*types.Info` + `map[PNode]GhostType`); removed misleading "unified `map[PNode]types.Type`" language.
16. ~~**Fix `08-type-checker-core.md` (Dead Code)**: Determine if `TypeInfo.Addressable` is entirely dead code and prune or map a verified caller path.~~ DONE — Removed `TypeInfo.Addressable` method from Deliverables; added note that call sites use `ti.Go.Types[expr].Addressable()` directly.
17. ~~**Fix `16b-silver-chopper.md` (Spec)**: Synchronize the penalty configuration filename and serialization format mismatch (`GobraChopper.conf` vs `gobra-chopper.json`).~~ DONE — Scope section changed from `GobraChopper.conf` to `gobra-chopper.json` with explicit note that it's JSON format using Scala field name keys.
18. ~~**Fix `06-gobrafier.md` (Spec)**: Align the function return signature to yield `[]Diagnostic` instead of a plain Go primitive `error`.~~ DONE — `Gobrafy` signature changed to `([]byte, []Diagnostic)`; added note that plain `error` cannot carry source position or multiple failures.
19. ~~**Fix `28-encoding-channels.md` (Logic)**: Add the missing `chan_T_Type()` token directly into the Plan 25 type domain spec registry.~~ DONE — Added `function chan_Type(elem: Type): Type` to the `Type` domain code block in plan 25; added explanatory note that `chan_Type` is required to distinguish channel-in-interface from int-in-interface, cross-referencing plan 28.
20. ~~**Fix `35-regression-suite.md` (Gates)**: Harmonize the contradictory entry criteria for phase 36 (95% pass rate gate vs concurrent parallel tracking).~~ DONE — Replaced contradictory single bullet with a two-stage gate: plan 36 (annotations) may start once skip list is stable; plan 37 (verification) requires ≥95% pass rate.

- **Next Autonomous Steps:** Execute the remediation queue starting with the high-severity items on files `15` and `11`.

#### NEW BLOCKERS FROM FULL-AUDIT (Items 21–27)
21. ~~**Fix `33-cli.md` (C4 — CHOP-BOUND-CONTRADICTION)**: Remove `n := cfg.Workers; cfg.ChopBound = &n` default; plan 16b is authoritative — `--chop` without `--chop-bound` → `cfg.ChopBound = nil` (unlimited).~~ DONE — Changed plan 33 to state ChopBound remains nil when --chop is set without --chop-bound.
22. ~~**Fix `17b-parallel-workers.md` (C5 — DEDUP-CIRCULAR-IMPORT)**: `Deduplicate` in `internal/backend/silicon/dedup.go` forces `jvm → silicon` import. Move `Deduplicate` to `internal/backend/dedup.go` (parent package).~~ DONE — Changed deliverable path from `silicon/dedup.go` to `backend/dedup.go`; added explanatory note about circular import. Registry updated.
23. ~~**Fix `24-encoding-maps.md` + `25-encoding-interfaces.md` (C4 — TYPE-DOMAIN-MAP)**: Map encoding emits `comparableType(kType)` but the `Type` domain (which contains `comparableType`) is only emitted on first interface use. Plan 24 must trigger `Type` domain emission unconditionally on first map use; plan 25 must note this.~~ DONE — Added `ensureTypeDomain(ctx)` idempotent helper contract to plan 25 with explicit trigger list (interface use OR map lookup); updated plan 24 dependency note to call this helper instead of assuming plan 25 ran first.
24. ~~**Fix `19-translator-core.md` (C3 — TYPEENCODING-UNDEFINED)**: `TypeEncoding` interface (return type of `Context.TypeEncoding()`) is never defined. Add a `TypeEncoding` interface definition as a deliverable in plan 19.~~ DONE — Added full `TypeEncoding` interface definition with `TypeValue`, `BoxValue`, `UnboxValue`, `EnsureTypeDomain` methods; added to `encoding.go` deliverable; noted it is implemented by plan 25 and accessed via `ctx.TypeEncoding()` to keep deps a DAG.
25. ~~**Fix `18-carbon-backend.md` (C3 — BACKEND-INTERFACE-UNDEFINED)**: `Backend` interface promised by plan 18 ("both Silicon and Carbon implement a common Backend interface") is never defined anywhere. Add definition as a deliverable in plan 18 (or plan 17).~~ DONE — Added `Backend` interface (`Initialize`, `Verify`, `Stop`) to plan 18; placed in `internal/backend/types.go`; added factory pattern for plan 33; updated registry.
26. ~~**Fix `07-package-resolver.md` (C4 — LOOP-INV-ROUTING-GAP)**: `MergeGhostStatements` has no step routing `//@ invariant P` annotations to `PForStmt.Invariants`. Add an explicit routing rule.~~ DONE — Added Rule A (invariant routing to PForStmt.Invariants) and Rule B (all other ghosts into PBlockStmt.Stmts) to step 4 of the per-file coordination sequence; added diagnostic for unattached invariants.
27. ~~**Fix `15b-worker-pool-expansion.md` (C9 — THREADATTACHED-REDECL)**: Plan 15b C9 redeclares `//@ pred ThreadAttached(jvm *JVM)` which is already declared in plan 15's C9 (same `jvm` package). Change to a reference, not a redeclaration.~~ DONE — Removed `//@ pred ThreadAttached(jvm *JVM)` declaration from plan 15b; replaced with comment noting the predicate is declared once in plan 15 and must not be redeclared; kept all requires/ensures annotations that reference it.

### CHECK-PLAN RESULTS (run on 06, 08, 12, 16b, 25, 35 after Items 14–20 edits)

#### BLOCKERS FOUND — WRITING THROUGH IMMEDIATELY

- **[C4-REG — 08]** FIXED: stale "same `map[PNode]types.Type` side table" in Resolved Questions replaced with "stored in the `map[PNode]GhostType` table inside GhostTypeInfo — NOT in the stdlib `*types.Info` map".
- **[C9 — 12]** FIXED: Added Verification Specifications section with `Desugar` pre/postconditions, fresh-var counter loop invariant, and position-preservation postcondition.
- **[C9 — 08]** FIXED: Added Verification Specifications section with `Check` output-safety contract, stub-resolution invariant, and `CheckSpecs` incremental-fill postcondition.
- **[C9 — 06]** FIXED: Added Verification Specifications section with `Gobrafy` line-count-preservation postcondition, nil-on-error contract, termination, and no-aliasing invariant.
- **[C9 — 25]** FIXED: Added Verification Specifications section with `EncodeInterface` ownership postcondition, `BoxValue` dyntype precondition, and `Type` domain singleton invariant.
- **[C9 — 16b]** FIXED: Added Verification Specifications section with `Chop` coverage postcondition, self-containment postcondition, and `buildDepGraph` termination.
- **[C9 — 35]** N/A — testing plan; C9 does not apply (no pipeline component deliverable).
- **[REGISTRY — C6]** FIXED: Pipeline Stage I/O table corrected for stages 08 and 12 (`*PFile` → `*frontend.PPackage`; `map[PNode]TypeInfo` → `*TypeInfo`).

### FINAL CHECK RESULT (06, 08, 12, 16b, 25, 35)
**All criteria PASS after fixes applied in this run.**
| File | C1 | C2 | C3 | C4 | C5 | C6 | C7 | C8 | C9 |
|---|---|---|---|---|---|---|---|---|---|
| 06-gobrafier.md | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | ✓ |
| 08-type-checker-core.md | ✓ | ✓ | ✓ | ✓ (fixed) | ✓ | ✓ | N/A | ✓ | ✓ (fixed) |
| 12-desugarer.md | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | ✓ (fixed) |
| 16b-silver-chopper.md | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | ✓ (fixed) |
| 25-encoding-interfaces.md | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | ✓ (fixed) |
| 35-regression-suite.md | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | N/A | ✓ | N/A |

### CHECK-PLAN RESULTS (run on 04, 15, 15b, 16, 17 after Phase 1 edits)
**Outcome: 11 passed, 6 failed** — 3 regressions + 3 pre-existing C9 gaps + 1 pre-existing C9 gap

#### SECOND CHECK-PLAN RUN FAILURES (04, 15, 15b, 16, 17)
- [x] **[C4 — 15b stale refs]**: FIXED — Both occurrences of "owned by plan 17" corrected to "owned by plan 15".
- [x] **[C4 — 17 narrative]**: FIXED — "Relationship" section updated: Verify takes a pre-built `jobject` (from plan 16's `Build()`), not a `*silver.Program`.

#### CRITICAL REGRESSIONS — FIXING NOW
- [x] **[C5-REG — 15]** FIXED: Plan 15 now listed plan 17 as dependency, but 17→15 already existed → cycle. Fix: moved `internal/backend/types.go` ownership to plan 15 (already depends on plan 14 for silver.Node). Removed plan 17 from plan 15's Dependencies.
- [x] **[C4-REG — 15b]** FIXED: `runWorker` body called `cfg.Args` but `cfg` not in scope. Fix: added `args []string` param; body uses `args` directly.
- [x] **[C4-REG — 15b vs 16]** FIXED: `SiliconInstance.Verify(prog jnigi.ObjectRef)` mismatched plan 16's `jobject`. Fix: changed to `jobject` throughout types.go and SiliconFrontendAPI signature.

#### PRE-EXISTING C9 FAILS — QUEUED FOR PHASE 2
- [x] **[C9-PREEXIST — 04]**: FIXED — Added C9 section with nil-safety postcondition (PFile nil iff hasBadNode), diagnostic completeness (diags != nil), termination (decreases len(src)), and no-aliasing contract for downstream concurrent use.
- [x] **[C9-PREEXIST — 15b]**: FIXED — Added C9 section with ThreadAttached invariant, Submit postcondition, NewPool precondition, and z3APIMode ghost-field invariant.
- [x] **[C9-PREEXIST — 16]**: FIXED — Added C9 section with Build postcondition (global-ref ownership), Close pre/postcondition (permission consumed on call), ThreadAttached precondition, and NodeMap integrity invariant.
- [x] **[C9-PREEXIST — 17]**: FIXED — Added C9 section with Verify threading precondition, Initialize idempotency guard, Stop requires-initialized contract, and Verify result contract (Success ↔ Errors nil).

---

## 4. PHASE 1 EXECUTION LOG

### Item 1 — Circular Import Analysis (IN PROGRESS)
**Root cause confirmed**: `internal/backend/jvm` (plan 15b) imports `internal/backend/silicon` for `SiliconConfig`, `SiliconFrontendAPI`, and `VerificationResult`. `internal/backend/silicon` (plan 17) lists plan 15's `WorkerPool` as a dependency — a `silicon` → `jvm` import. Cycle is real.

**Chosen fix strategy**: Introduce `internal/backend/types.go` (package `backend`) as a neutral artifact owned by plan 17. Moves out of `silicon.go`:
- `VerificationResult` + `VerificationError` (canonical, full form)
- `SiliconInstance` interface (replaces concrete `*SiliconFrontendAPI` in config)
- `SiliconConfig` struct (Instance field becomes `SiliconInstance` interface)

Both `jvm` and `silicon` sub-packages import the parent `backend` package. Neither imports the other. Cycle broken.

**Secondary fixes bundled in same edit pass**:
- Item 2 (VerificationResult mismatch): plan 15's incomplete `VerificationResult{Errors []Diagnostic}` replaced by a reference to the canonical struct in `internal/backend/types.go`; plan 15b's usage of `{Err: err}`, `NodeMap`, `Close` fields consistent with that canonical struct.
- Item 3 (NewPool duplicate): plan 15 delivers pool skeleton only (no `NewPool` function); plan 15b explicitly owns and delivers `NewPool` with the full signature `NewPool(jvm *JVM, poolSize int, cfg backend.SiliconConfig) *WorkerPool`.

**Status**: DONE

### Item 2 — VerificationResult Struct Mismatch (DONE)
Resolved as part of Item 1 edit pass. Canonical `VerificationResult` (full form with `Success`, `Errors []VerificationError`, `Err`, `NodeMap`, `Close`) now lives exclusively in `internal/backend/types.go` (plan 17). Plan 15's incomplete `{Errors []Diagnostic}` definition removed. Plan 15b's usage of `{Err: err}` / `NodeMap` / `Close` fields is now consistent with the canonical struct.

### Item 3 — Duplicate NewPool Constructors (DONE)
Plan 15 now delivers only the `WorkerPool` struct and `Stop()` (the skeleton). `NewPool` is explicitly owned by plan 15b, which states it supersedes the skeleton. No compilation conflict.

### Item 4 — Worker Synchronous Deadlock (DONE)
Fixed in `16-silver-jni-builder.md`. The incorrect note "Build() sends a request and waits for the result" (via channel dispatch) was replaced with an explicit contract: `Build()` is a direct synchronous call executed inline by the calling goroutine. The worker goroutine calls `Build()` directly inside its `for job := range jobs` loop; any channel dispatch inside `Build()` would deadlock the pool. The fix note explicitly warns against adding such dispatch.

### Item 5 — Pipeline Layering Break (DONE)
Fixed in `04-go-parser.md`. Deliverables line changed from "re-exported from `internal/reporting`" to "imported from `internal/diagnostic/` (owned by plan 32a)". Explicit warning added that importing `internal/reporting` from plan 04 breaks layering.

---

## 5. REVIEW-PLAN FULL-AUDIT RESULTS

*Full audit of all 41 plan files. Run date: 2026-06-28.*

### Contradictions

#### BLOCKING

- **[16b:Penalty vs 33:Chop default]** Plan 16b states "with `--chop`, `bound=nil` (unlimited)". Plan 33 states "default bound copies the workers value: `n := cfg.Workers; cfg.ChopBound = &n`". These are directly contradictory. With --chop and --workers 4, plan 16b produces unlimited merging while plan 33 produces bound=4. The authoritative behavior is undefined.

- **[17b:Deliverables vs 15/architecture]** Plan 17b places `Deduplicate([]VerificationError) []VerificationError` in `internal/backend/silicon/dedup.go`. But `mergeResults` (which calls deduplication) lives inside `DispatchChopped` in `internal/backend/jvm/dispatch.go`. This requires `jvm` to import `silicon`, recreating the exact `jvm → silicon` circular import the prior audit remediated. `Deduplicate` must move to `internal/backend/dedup.go` (parent package).

- **[24+25] Silver `comparableType` undefined when interfaces absent**: Plan 25 says the `Type` domain (which contains `comparableType`) is emitted lazily "on first use of any interface type." Plan 24 says every map lookup emits `comparableType(kType)`. A program with maps but no interfaces never triggers `Type` domain emission, yet references `comparableType` — producing invalid Silver (undefined function).

#### SIGNIFICANT

- **[15b:C9 vs 15:C9]** Plan 15b's C9 section redeclares `//@ pred ThreadAttached(jvm *JVM)`, which is already declared in plan 15's C9. Both are in the same `jvm` package. Gobra rejects duplicate predicate declarations. Plan 15b must reference plan 15's predicate.

- **[32:Diagnostic re-export type]** Plan 32 says `Diagnostic` is "re-exported from `internal/reporting/` for callers" but does not specify it must be a Go type alias (`type Diagnostic = diagnostic.Diagnostic`). A type redefinition creates an incompatible type, breaking assignments from `diagnostic.Diagnostic` to `reporting.Diagnostic` throughout the pipeline.

- **[00-overview vs 32a]** Cross-Cutting Notes still says "defined in plan 32" for Diagnostic. Pre-existing, unfixed.

- **[33:Dependencies]** Plan 33 omits plan 15b as a dependency. Pre-existing, unfixed.

#### MINOR

- **[27 vs 20] `KNOWN_LIMITATIONS.md`**: Plan 20 says "append to or create"; plan 27 says "created by this plan". Plan 20 runs first in dependency order, making plan 27's claim false.

- **[34:JNI single-worker claim]** Main text says "single JNI worker" but pool is N workers after plan 17b (which plan 34 transitively depends on).

---

### Gaps

#### BLOCKING

- **[19] `TypeEncoding` interface undefined**: Plan 19 says `Context` exposes `TypeEncoding() TypeEncoding` and `TypeValue(t internal.Type) silver.Expr`. The `TypeEncoding` type is used by plans 24 and 25 but never defined as a Go interface in any plan. No method set is specified.

- **[18] `Backend` interface undefined**: Plan 18 deliverables state "Both Silicon and Carbon implement a common `Backend` interface." No plan defines this interface — not plan 17, not plan 18, not plan 19.

#### SIGNIFICANT

- **[07] Loop invariant routing not specified**: Plan 03 specifies `PForStmt.Invariants []PAssertion` for `//@ invariant P` annotations. Plan 07's 4-step coordination model only describes merging ghost statements into `PBlockStmt.Stmts`. No step routes `PAssertion` (invariant) nodes to `PForStmt.Invariants`. Without an explicit routing rule in `MergeGhostStatements`, invariants are either silently dropped or incorrectly placed as pre-loop statements.

- **[20] `goIntDiv` postcondition unspecified**: The "Bodyless Functions" table row for `goIntDiv` says "copy postcondition verbatim from `IntegerEncoding.scala`" but provides no formula. The Scala source is scheduled for deletion at cut-over (plan 37). The postcondition must be written into this table before cut-over.

- **[08] `GhostType extends types.Type` scope undefined**: No plan specifies which package boundaries are allowed to hold `GhostType` values, or what behavior is expected if a `GhostType` is passed to a `go/types` function expecting `types.Type`. This creates a latent panic hazard.

- **[04] `hasBadNode` in C9 is invalid Gobra**: `hasBadNode` used in postconditions is a free variable — not syntactically valid Gobra. Ghost variables must be declared as ghost parameters or ghost locals. The spec is informative but unverifiable.

#### MINOR

- **[08] Unsupported constructs omits `unsafe`**: Plan 08's unsupported-constructs section lists `goto`, `fallthrough`, `recover` but not `unsafe`. Plans 07 and 10 reject it earlier; plan 08 provides no third-line-of-defense check.

- **[30] Generics audit fields are stale**: Plan 30's `Audit status`, `Generics found`, and `Chosen option` fields remain `_not yet run_` / `_unknown_` / `_N/A_`. No owner or trigger is specified.

---

### Logic errors

#### BLOCKING

- **[24+25] `comparableType` undefined without interfaces** (see Contradictions): Reproduced as logic error. Map programs without interfaces reference an unemitted Silver function, producing invalid Silver rejected by Silicon's consistency checker.

#### SIGNIFICANT

- **[17b:Phase 3 greedy merge] Missing empty-queue termination condition**: Plan 16b says stop "when no remaining pair has penalty ≤ 0 and count ≤ Bound." With a lazy priority queue, all remaining entries could be stale (sub-programs consumed). The loop must also terminate when the queue is empty; the plan's stop condition omits this case.

- **[12/29] Sparse sequence literal encoding path missing**: Plan 29 specifies `emptySeq_{T}` for sequence literals with gaps ≥ 5 indices. Plan 11 defines no internal AST node for sparse sequence literals. Plan 12 does not mention gap detection or the chunking threshold. There is no specified path from a `PSeqLit` frontend node with sparse indices through the internal AST to plan 29's `EmptyChunk`/`NonEmptyChunk` logic.

- **[27:opaque Info chain order]** Plan 27 places `@opaque` as `ConsInfo(AnnotationInfo{"opaque",...}, NodeInfo{...})` on the Go Silver node. Plan 16's builder then prepends `gobra_node_id` and `gobra_node_*` annotations in front. The resulting Java Info chain has `@opaque` deep in the chain. If Silicon's `@opaque` handling searches only the chain head (not recursively), the annotation is silently ignored. The plan does not confirm Silicon's chain-search behavior.

#### MINOR

- **[33:ChopBound + z3APIMode interaction]** `cfg.ChopBound = &n` (where `n = cfg.Workers`) is set at flag-parse time, before `--z3APIMode` forces poolSize to 1. With `--workers 4 --chop --z3APIMode`, the chopper produces up to 4 sub-programs while only 1 worker exists. Functionally correct (serial) but documents a counterintuitive interaction not addressed in the plan.

- **[04:ParseFile nil-key C9 postcondition]** `//@ ensures forall k *frontend.PBlockStmt :: k != nil ==> acc(k) && acc(annotations[k])` excludes the nil key (file-scope annotations). The Gobra spec does not account for `annotations[nil]`, leaving the nil-key permission unspecified.

---

### Design concerns

- **[08] `GhostType extends types.Type`**: Ghost types satisfy `types.Type` and can be passed anywhere `types.Type` is expected. Any `go/types` utility that tries to type-switch on concrete `*types.Named` / `*types.Slice` etc. will get an unhandled case or panic. A separate `GhostType` interface (not extending `types.Type`) with explicit adapters at boundary points would be safer.

- **[07] `PackageInfo.Files` alongside `Package`**: Having both `Files []*frontend.PFile` and `Package *frontend.PPackage` in `PackageInfo` creates ambiguity. The plan calls `Package` authoritative but retains `Files` for diagnostics. Any code that uses `Files` for type-checking or desugaring is a bug; this must be stated explicitly.

- **[15:JVM singleton + test isolation]** If `Start()` fails in one test (bad JDK path), `jvmErr` is set permanently for the binary. Subsequent tests that call `Start()` with correct config get the old error. Test isolation for JVM startup failures is impossible. Document that each test binary gets at most one JVM configuration attempt.

- **[33:flag package + conflicting short-circuit flags]** Plan 33 never specifies precedence for `--parseOnly`, `--typeCheckOnly`, and `--noVerify` when passed simultaneously. The Scala `GobraConfig` handles this; plan 33 should specify the precedence.

- **[29:ADT mutual recursion]** Plan 29's rank axiom for termination handles direct recursion. For mutually recursive ADTs (A references B references A), a rank function over a single type cannot express the inter-type decrease. The plan does not address mutual recursion in ADTs.

---

### Simplifications / improvements

- **[19/08] Name mangling reserved patterns missing from plan 08's unsupported-constructs list**: Plan 19 says the type checker must reject identifiers matching `_u[0-9A-F]{1,6}_` and names beginning with `gobra__`. Plan 08's unsupported-constructs section does not list these. They should be added to plan 08 for self-containment.

- **[01] Create `KNOWN_LIMITATIONS.md` in project setup**: Plans 20, 27, and 28 all independently try to create/append to this file. Creating it in plan 01 as an empty file eliminates "create if absent" boilerplate in three plans.

- **[10:Serialize stub test]** Plan 10's stubs have no test coverage. A smoke test asserting `Serialize()` returns a non-nil error and `DeserializeExternalTypeInfo` returns `ErrStaleCacheEntry` would guard the stub contract without requiring the real format.

- **[37:cut-over checklist]** The checklist item "Phase 2 self-hosting verification succeeds" lacks a quantitative criterion. It should reference the blocking tier from the CI Gate Strategy section explicitly.

- **[32a:Category names]** `Error` and `Warning` constants in `internal/diagnostic/` may shadow local error variables in files that also use the `errors` package. Consider `DiagError`, `DiagWarning`, `DiagInfo`.

- **[35:UNEXPECTED_FAIL sentinel]** Plan 35 mentions that CI greps for `UNEXPECTED_FAIL:` lines but provides no explicit command or CI step. Plan 34 specifies the sentinel format; plan 35 should include the exact grep command in the CI job spec.

---

### New Active Blockers (to be resolved)

The following items are BLOCKING and must be added to Section 3 of the scratchpad:

1. **[CHOP-BOUND-CONTRADICTION — 16b vs 33]**: Plan 16b says `--chop` without bound → `nil` (unlimited). Plan 33 says → `workers`. One must be authoritative. Recommended: plan 16b's algorithm is correct (unlimited by default, bound only via `--chop-bound N`). Plan 33 should remove the `&n` default assignment.

2. **[DEDUP-CIRCULAR-IMPORT — 17b]**: `Deduplicate` in `silicon/dedup.go` forces `jvm → silicon` import. Move to `internal/backend/dedup.go` (parent package).

3. **[TYPE-DOMAIN-MAP — 24 vs 25]**: Map encoding references `comparableType` from the `Type` domain which is only emitted on first interface use. Plan 24 must trigger Type domain emission unconditionally on first map use.

4. **[TYPEENCODING-UNDEFINED — 19]**: `TypeEncoding` interface return type of `Context.TypeEncoding()` is never defined. Must be defined in plan 19 (or a new plan) before plans 24 and 25 can be implemented.

5. **[BACKEND-INTERFACE-UNDEFINED — 18]**: `Backend` interface promised by plan 18 is never defined anywhere.

6. **[LOOP-INV-ROUTING-GAP — 07]**: `MergeGhostStatements` (plan 07) has no step routing `//@ invariant P` annotations to `PForStmt.Invariants`. Must add an explicit routing rule.

7. **[THREADATTACHED-REDECL — 15b]**: Plan 15b's C9 redeclares `//@ pred ThreadAttached(jvm *JVM)` from plan 15. Must change to a reference, not a redeclaration.

