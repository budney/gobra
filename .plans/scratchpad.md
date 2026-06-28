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

### Pipeline Stage I/O Types
| Stage | Input Type | Output Type | Error Type |
|---|---|---|---|
| Go Parser (04) | `string` (file path) | `*PFile` | `[]Diagnostic` |
| Annotation Parser (05) | `string` (comment text) | `[]PNode` | `[]Diagnostic` |
| Package Resolver (07) | `[]string` (paths) | `[]*PFile` | `[]Diagnostic` |
| Type Checker (08) | `*PFile` | side-table `map[PNode]TypeInfo` | `[]Diagnostic` |
| Desugarer (12) | `*PFile` + type tables | `*internal.Program` | `[]Diagnostic` |
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
14. **Fix `12-desugarer.md` (Logic)**: Correct the variadic spread tracking rules to explicitly validate `ast.CallExpr.Ellipsis` instead of relying entirely on type shapes.
15. **Fix `08-type-checker-core.md` (Spec)**: Reconcile the unified type map notes with the split `*types.Info` and `GhostTypeInfo` struct deliverables.
16. **Fix `08-type-checker-core.md` (Dead Code)**: Determine if `TypeInfo.Addressable` is entirely dead code and prune or map a verified caller path.
17. **Fix `16b-silver-chopper.md` (Spec)**: Synchronize the penalty configuration filename and serialization format mismatch (`GobraChopper.conf` vs `gobra-chopper.json`).
18. **Fix `06-gobrafier.md` (Spec)**: Align the function return signature to yield `[]Diagnostic` instead of a plain Go primitive `error`.
19. **Fix `28-encoding-channels.md` (Logic)**: Add the missing `chan_T_Type()` token directly into the Plan 25 type domain spec registry.
20. **Fix `35-regression-suite.md` (Gates)**: Harmonize the contradictory entry criteria for phase 36 (95% pass rate gate vs concurrent parallel tracking).

- **Next Autonomous Steps:** Execute the remediation queue starting with the high-severity items on files `15` and `11`.

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

