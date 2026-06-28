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
| `internal/backend/jvm/jvm.go` (JVM, Start, Stop, JVMConfig, WorkerPool, workerJob, VerificationResult) | 15 |
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
| Silicon Backend (17) | `*silver.Program` | `*VerificationResult` | `[]Diagnostic` |
| Reporter (32) | `*VerificationResult` | formatted output | — |

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
- [ ] **[CIRCULAR-IMPORT-BLOCKER]:** `15b-worker-pool-expansion.md` introduces a circular import (`jvm` -> `silicon` -> `jvm`) by trying to import `SiliconConfig` into the JVM package.
  - *Fix Strategy:* Abstract `SiliconConfig` out to a shared configuration package or pass it as an untyped interface/primitive value.
- [ ] **[STRUCT-MISMATCH]:** `VerificationResult` has two incompatible shapes across `15`, `15b`, and `17`.
  - *Fix Strategy:* Centralize `VerificationResult` into a single shared types package (e.g., `internal/backend/types.go`) and delete conflicting definitions.
- [ ] **[OVERLOAD-ERR]:** Duplicate `NewPool` constructors in `15` and `15b`.
  - *Fix Strategy:* Refactor `15b` to explicitly state that it *replaces* the signature in `15`, or rename it to `NewConfiguredPool`.
- [ ] **[DIAGNOSTIC-LAYERING]:** File `04` breaks early-pipeline separation rules by importing `Diagnostic` from `internal/reporting` instead of `internal/diagnostic/` (defined in `32a`).
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
1. **Fix package layout (C5)**: Resolve the `jvm` ➔ `silicon` ➔ `jvm` circular import introduced by plan 15b passing `SiliconConfig` directly into the JVM package.
2. **Harmonize `VerificationResult` (C4/C6)**: Redefine `VerificationResult` in a shared neutral types package to eliminate the structural mismatch between plans 15, 15b, and 17.
3. **De-duplicate constructors (C3)**: Refactor `NewPool` in plan 15b to explicitly replace or cleanly differentiate itself from plan 15's constructor signature to prevent Go compilation errors.
4. **Fix worker deadlock (C4)**: Align plan 16 and 15b so that synchronous worker routines do not self-deadlock against internal channel queues inside `Build()`.
5. **Fix pipeline layering break (C5)**: Rewrite plan 04 to pull the `Diagnostic` type from `internal/diagnostic/` (plan 32a) instead of violating boundaries via `internal/reporting`.

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

