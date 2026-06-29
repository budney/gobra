# Go-Gobra Rewrite: Automation Scratchpad
> **Rule for AI Agent:** Do not edit any other `.plans/*.md` files until you have updated the active constraints, dependency states, and task items in this scratchpad.

## 1. GLOBAL SYMBOL & COMPONENT REGISTRY
*Tracks artifact ownership (C3), cross-plan type references (C4), and dependency links (C5).*

### Package Ownership
| Package / Artifact | Owner Plan |
|---|---|
| `internal/ast/frontend/` (PNode, PDecl, PStmt, PGoStmt, PBlockStmt, PFile, PFunctionDecl, PMethodDecl, PTypeDecl, PForStmt, PRangeStmt, PLoopSpec, PBodyParameterInfo, PReceiver, PFunctionSpec, Visitor, etc.) | 03 |
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
| Annotation Parser (05) | `string` (comment text) + `token.Pos` (base) + `bool` (isFileScope) | `([]PNode, []PDecl)` | `[]Diagnostic` |
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
- [x] 1**C1**: `scratchpad.md` present in `.plans/` but not listed in WBS or Reference Documents in `00-overview.md` (pre-existing). NOTE: `00-overview.md` Reference Documents table already includes `scratchpad.md` — this was resolved.
- [x] **C4** (00-overview.md): Cross-Cutting Notes says "defined in plan 32" for Diagnostic — should say "defined in plan 32a". VERIFIED ALREADY CORRECT — line 140 reads "defined in plan 32a, `internal/diagnostic/`".
- [x] **C5** (33): Dependencies omits `15b`; VERIFIED ALREADY CORRECT — line 67 includes `15b-worker-pool-expansion.md`.
- [x] **C4** (17b): Objective incorrectly references plan 15's scope. VERIFIED ALREADY CORRECT — all Objective/Scope references use "plan 15b"; no bare "plan 15" references found.


### Current Target Files: 03, 11, 14, 15
| File | Phase 2 Injected | Review Status | Check Status |
|---|---|---|---|
| 03-frontend-ast.md | NEEDS FIX (C10→C9) | DONE | [x] PASSED (post round-4 fixes) |
| 11-internal-ast.md | YES (C9 present) | DONE | [x] PASSED |
| 14-silver-ast.md | YES (C9 present) | DONE | [x] PASSED |
| 15-jni-setup.md | YES (C7+C9 present) | DONE | [x] PASSED |

### Check-Plan Results (00, 01, 02, 03 — post round-4 remediations)
| File | C1 | C2 | C3 | C4 | C5 | C6 | C7 | C8 | C9 |
|---|---|---|---|---|---|---|---|---|---|
| 00-overview.md | ✓ | N/A | N/A | **FAIL** (PAssertion stale) | N/A | N/A | N/A | N/A | N/A |
| 01-project-setup.md | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | N/A | ✓ | N/A |
| 02-annotation-syntax-decision.md | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | N/A | ✓ | N/A |
| 03-frontend-ast.md | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | N/A | ✓ | ✓ |

**C4 FAIL — 00-overview.md:Unblocked Work** line: "the parser produces 03's node types — `PFunctionSpec`, `PAssertion`, etc." — `PAssertion` was removed from plan 03 (replaced by `PExpression`). Fix: change `PAssertion` to `PExpression` in that sentence.

**Stale scratchpad registry (informational, not a plan-file failure):** Registry line 10 still says `PFuncDecl`; should be `PFunctionDecl`. Fix registry entry.

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

### CHECK-PLAN RESULTS (run on 14, 12, 20, 04, 33, 16b, 15b after Items 28–34 edits)

| File | C1 | C2 | C3 | C4 | C5 | C6 | C7 | C8 | C9 |
|---|---|---|---|---|---|---|---|---|---|
| 14-silver-ast.md | ✓ | ✓ | ✓ | ✓ (fixed) | ✓ | N/A | N/A | ✓ | ✓ |
| 12-desugarer.md | ✓ | ✓ | ✓ | ✓ (fixed) | ✓ | ✓ | N/A | ✓ | ✓ |
| 20-encoding-primitives.md | ✓ | ✓ | ✓ | ✓ (fixed) | ✓ | N/A | N/A | ✓ | **FAIL** (pre-existing) |
| 04-go-parser.md | ✓ | ✓ | ✓ | ✓ (fixed) | ✓ | ✓ | N/A | ✓ | ✓ (fixed) |
| 33-cli.md | ✓ | ✓ | ✓ | ✓ (fixed) | ✓ (fixed) | N/A | N/A | ✓ | **FAIL** (pre-existing) |
| 16b-silver-chopper.md | ✓ | ✓ | ✓ | ✓ (fixed) | ✓ | N/A | N/A | ✓ | ✓ |
| 15b-worker-pool-expansion.md | ✓ | ✓ | ✓ (fixed) | ✓ (fixed) | ✓ | N/A | ✓ | ✓ | ✓ |

**New regressions introduced by items 28–34 edits: NONE**

**Pre-existing C9 failures surfaced by this check run:**
- [x] **[C9-PREEXIST — 20]**: FIXED — Added "Verification Specifications (C9)" section with `EncodeType` totality contract, `EncodeExpr` non-nil result, `emitGoIntDiv`/`emitGoIntMod` well-formed body postconditions, emit-once idempotency ghost invariant, and `ensureStringsDomain` singleton contract.
- [x] **[C9-PREEXIST — 33]**: FIXED — Added "Verification Specifications (C9)" section with `Run` error postcondition (nil error ↔ empty diagnostics), `parseFlags` nil-safety (`(cfg != nil) != (err != nil)`), `validateFlagPrecedence` short-circuit ordering contract, and no-panic contract with termination annotation.

#### NEW BLOCKERS FROM FULL-AUDIT ROUND 2 (Items 28–34)

28. ~~**[C4 — 14 vs 15b/17: `NodeInfo.NodeID` MISSING FIELD]**: Plan 14 defines `NodeInfo{File, Line, Col, Tag}` — no `NodeID` field. Plans 15b and 17 both access `result.Errors[i].Pos.NodeID` (plan 15b worker snippet) and "looking up NodeMap[Pos.NodeID]" (plan 17). Field does not exist; code will not compile.~~ DONE — Added `NodeID uint64` to `NodeInfo` in plan 14 with explanation of assignment by plan 16's JNI builder and `NodeMap` lookup; synthetic nodes carry `NodeID = 0`.

29. ~~**[C4 — 12: `select` desugaring targets wrong layer]**: Plan 12 says "desugar to a Silver `if`/`elsif` chain" for `select` — but the desugarer produces *internal AST*, not Silver. The internal AST (plan 11) has no `SelectStmt` node. The plan must say "desugar to internal `If`/`While` nodes" and plan 11 must define the internal representation.~~ DONE — Changed plan 12 Scope bullet to say "desugar to an internal `If` node chain (plan 11 `If`/`While` nodes)"; clarified that desugarer produces internal AST only, translator handles Silver conversion; `If` already exists in plan 11 — no new node needed.

30. ~~**[C4 — 20: `goIntDiv` postcondition unwritten — cut-over gate risk]**: Plan 20 says "copy postcondition verbatim from `IntegerEncoding.scala`" but never writes it into the plan. The Scala source is deleted at cut-over (plan 37). The postcondition must be written into plan 20 before cut-over or the implementation has no spec to reference.~~ DONE — Read `IntegerEncoding.scala`; found `goIntDiv`/`goIntMod` have Silver function **bodies** (not postconditions; `posts = Seq.empty`). Updated plan 20 Bodyless Functions table to "Silver Functions Table" with verified body formulas: `goIntDiv` body = `(0 <= l ? l / r : -((-l) / r))`, `goIntMod` body = `(0 <= l || l % r == 0 ? l % r : l % r - (0 <= r ? r : -r))`.

31. ~~**[C9 — 04: `hasBadNode` is an undeclared free variable]**: Plan 04's C9 postcondition uses `hasBadNode` as a ghost variable but never declares it as a ghost parameter or ghost local. The Gobra spec is syntactically invalid and unverifiable.~~ DONE — Declared `hasBadNode` as a ghost result parameter `/*@ ghost hasBadNode bool @*/` in the `ParseFile` signature; added note that the ghost result is set inside `ast.Inspect` callback and is invisible to non-Gobra callers.

32. ~~**[C5 — 33: missing dependency on 32a]**: Plan 33 (`pipeline.go`) produces and accumulates `[]Diagnostic` values but does not list plan 32a as a dependency. The `Diagnostic` type must be imported from `internal/diagnostic/` directly.~~ DONE — Added `[32a-diagnostics.md]` as first dependency in plan 33 with explicit note that `pipeline.go` imports `internal/diagnostic/` directly, not `internal/reporting`.

33. ~~**[C4 — 16b Phase 3: empty priority queue missing from stop condition]**: Phase 3 greedy merge says "pop the pair with the lowest penalty" but provides no guard for an empty queue. A lazy priority queue where all entries are stale (sub-programs consumed) would panic on pop. Stop condition must include: "stop also when the queue is empty."~~ DONE — Added empty-queue guard to step 3 loop condition and step 4 stop condition in plan 16b Phase 3; added explicit note that lazy priority queues must check for empty before every pop.

34. ~~**[C4 — 15 vs 15b: `runWorkerSkeleton` becomes dead code after plan 15b]**: Plan 15 delivers `runWorkerSkeleton` in `jvm.go`; plan 15b delivers a full `runWorker` that supersedes it. `runWorkerSkeleton` is never called after plan 15b. Plan 15b must explicitly state it removes `runWorkerSkeleton` from `jvm.go`, or the package will contain dead code that misleads readers.~~ DONE — Added explicit `runWorkerSkeleton` removal instruction to plan 15b Deliverables section.

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

#### SIGNIFICANT

- **[07 vs 37: Go toolchain not listed in CI setup]** Plan 07 documents "build Go-Gobra with Go → run Go-Gobra (requires Go in PATH) → verify Go-Gobra source" as the bootstrap sequence. Plan 37's CI setup checklist and hard-gate list do not mention the Go toolchain as a CI runner requirement. A runner with only a Go-Gobra binary will fail silently at package resolution with no clear error.

- **[08 vs 19: reserved-identifier enforcement missing from plan 08]** Plan 19 specifies two reserved identifier patterns (`_u[0-9A-F]{1,6}_` and names beginning with `gobra__`) that the type checker must reject. Plan 08's "Explicitly Unsupported Constructs" section lists only `goto`, `fallthrough`, `recover()` — the reserved-name checks are absent. A collision produces a silent Silver name collision at translation time.

- **[15 vs 15b: `runWorkerSkeleton` dead code]** Plan 15 delivers `runWorkerSkeleton` in `jvm.go`. Plan 15b delivers a full `runWorker` that entirely supersedes it but neither plan says to remove `runWorkerSkeleton`. After plan 15b both functions exist in `jvm.go` but only `runWorker` is called. (Logged as item 34 in remediation queue.)

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

#### SIGNIFICANT

- **[21: bodyless functions table missing]** Plan 19 explicitly lists plan 21 as requiring a "Bodyless Functions" subsection: "`sharedStructConversion` and `sharedStructDefault` must also be audited." Plan 21 has no such subsection. The audit checklist is incomplete; these functions are uninterpreted and an incorrect postcondition would silently unsound struct proofs.

- **[10: stub-directory-first resolution has no smoke test]** Plan 10 delivers a custom `types.Importer` that routes `"sync"`, `"fmt"`, etc. to embedded stubs before falling through to the real stdlib. There is no test asserting that `import "sync"` resolves to the embedded `.gobra` stub rather than the real Go package. This is a high-risk code path with no regression gate.

#### MINOR

- **[08] Unsupported constructs omits `unsafe`**: Plan 08's unsupported-constructs section lists `goto`, `fallthrough`, `recover` but not `unsafe`. Plans 07 and 10 reject it earlier; plan 08 provides no third-line-of-defense check.

- **[30] Generics audit fields are stale**: Plan 30's `Audit status`, `Generics found`, and `Chosen option` fields remain `_not yet run_` / `_unknown_` / `_N/A_`. No owner or trigger is specified. Plan 30 is a prerequisite for plan 36 (self-hosting annotations); open questions must be resolved before plan 36 begins.

- **[04 C9: `hasBadNode` undeclared free variable]** Plan 04's C9 postcondition uses `hasBadNode` as a ghost variable but never declares it as a ghost parameter or ghost local. The Gobra spec is syntactically invalid and unverifiable. (Logged as item 31 in remediation queue.)

---

### Logic errors

#### BLOCKING

- **[24+25] `comparableType` undefined without interfaces** (see Contradictions): Reproduced as logic error. Map programs without interfaces reference an unemitted Silver function, producing invalid Silver rejected by Silicon's consistency checker.

#### BLOCKING

- **[15b: worker snippet accesses non-existent `Pos.NodeID`]** Plan 15b's `runWorker` code accesses `result.Errors[i].Pos.NodeID` to look up `built.NodeMap`. `Pos` is `silver.NodeInfo` (plan 14) which has no `NodeID` field. This is a compile blocker. (Also logged as item 28 in remediation queue.)

#### SIGNIFICANT

- **[17b:Phase 3 greedy merge] Missing empty-queue termination condition**: Plan 16b says stop "when no remaining pair has penalty ≤ 0 and count ≤ Bound." With a lazy priority queue, all remaining entries could be stale (sub-programs consumed). The loop must also terminate when the queue is empty; the plan's stop condition omits this case. (Also logged as item 33 in remediation queue.)

- **[12/29] Sparse sequence literal encoding path missing**: Plan 29 specifies `emptySeq_{T}` for sequence literals with gaps ≥ 5 indices. Plan 11 defines no internal AST node for sparse sequence literals. Plan 12 does not mention gap detection or the chunking threshold. There is no specified path from a `PSeqLit` frontend node with sparse indices through the internal AST to plan 29's `EmptyChunk`/`NonEmptyChunk` logic.

- **[27:opaque Info chain order]** Plan 27 places `@opaque` as `ConsInfo(AnnotationInfo{"opaque",...}, NodeInfo{...})` on the Go Silver node. Plan 16's builder then prepends `gobra_node_id` and `gobra_node_*` annotations in front. The resulting Java Info chain has `@opaque` deep in the chain. If Silicon's `@opaque` handling searches only the chain head (not recursively), the annotation is silently ignored. The plan does not confirm Silicon's chain-search behavior.

- **[25 vs 24: `ensureTypeDomain` cross-reference implicit]** Plan 24 says it calls `ensureTypeDomain(ctx)` before any `comparableType` assertion. Plan 25 defines `ensureTypeDomain` as idempotent and callable from map operations. The two plans agree, but plan 24 never explicitly says it calls INTO plan 25's helper — a developer implementing plan 24 independently before plan 25 may write a local stub. The dependency is implicit rather than stated.

#### MINOR

- **[33:ChopBound + z3APIMode interaction]** `cfg.ChopBound = &n` (where `n = cfg.Workers`) is set at flag-parse time, before `--z3APIMode` forces poolSize to 1. With `--workers 4 --chop --z3APIMode`, the chopper produces up to 4 sub-programs while only 1 worker exists. Functionally correct (serial) but documents a counterintuitive interaction not addressed in the plan.

- **[04:ParseFile nil-key C9 postcondition]** `//@ ensures forall k *frontend.PBlockStmt :: k != nil ==> acc(k) && acc(annotations[k])` excludes the nil key (file-scope annotations). The Gobra spec does not account for `annotations[nil]`, leaving the nil-key permission unspecified.

---

### Design concerns

- **[08] `GhostType extends types.Type`**: Ghost types satisfy `types.Type` and can be passed anywhere `types.Type` is expected. Any `go/types` utility that tries to type-switch on concrete `*types.Named` / `*types.Slice` etc. will get an unhandled case or panic. A separate `GhostType` interface (not extending `types.Type`) with explicit adapters at boundary points would be safer.

- **[05: `ParseAnnotation` returns `[]PNode` for file-scope decls that must be `PDecl`]** Plan 07's `MergeGhostStatements` type-asserts file-scope `PNode` values to `PDecl` and panics on failure. `ParseAnnotation` returns `[]PNode` so no compile-time guarantee exists. Consider returning `(stmtNodes []PNode, declNodes []PDecl, diags []Diagnostic)` to give callers type-safe routing without a runtime panic.

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

- **[35:UNEXPECTED_FAIL sentinel]** Plan 35 mentions that CI greps for `UNEXPECTED_FAIL:` lines but provides no explicit command or CI step. Plan 34 specifies the sentinel format; plan 35 should include the exact grep command in the CI job spec. Neither plan states that BOTH `UNEXPECTED_FAIL:` (regression) and `UNEXPECTED_PASS:` (stale skip entry) must fail CI.

- **[19: `TupleDomain` notation not cross-referenced at use sites]** Plan 19 defines that `tuple_get(base, idx, n)` is shorthand for `gobra__tuple{n}_get{idx}(base)`. Plans 21 and 22 use this notation without defining or citing it. Add a cross-reference at each use site.

- **[13: transform order rationale undocumented]** Plan 13 specifies the transform order (constant propagation → call graph → overflow → termination) without explaining why this order is required. The call-graph-before-termination constraint is non-obvious (termination checking needs complete CG edges). Add a one-line rationale per ordering constraint.

---

---

## 6. REVIEW-PLAN FULL-AUDIT RESULTS (Round 2)

*Full audit of all 41 plan files. Run date: 2026-06-28 (second pass — post all prior remediations).*

### New Contradictions

#### BLOCKING

- **[17 vs 18: `Backend.Verify` return-type mismatch]** Plan 15 defines `SiliconInstance.Verify(prog jobject) *VerificationResult` (no error return). Plan 17's `SiliconFrontendAPI` implements `SiliconInstance` with that exact signature. Plan 18 defines `Backend.Verify(prog jobject) (*VerificationResult, error)` (adds error return) and asserts "Both `SiliconFrontendAPI` and `CarbonFrontendAPI` implement Backend." A Go struct can only have one `Verify` method — `SiliconFrontendAPI` cannot satisfy both interfaces simultaneously. `Backend` must drop the error return to match `SiliconInstance`, since `VerificationResult.Err` already captures JNI failures.

#### SIGNIFICANT

- **[32: Diagnostic re-export type missing alias specifier]** Plan 32 says "`Diagnostic` type … re-exported from `internal/reporting/` for callers" but does not specify this must be a Go type alias (`type Diagnostic = diagnostic.Diagnostic`). A value redefinition creates a type-incompatible `reporting.Diagnostic` that cannot receive values from any stage returning `diagnostic.Diagnostic`. This was flagged in prior round (Section 5) but not remediated.

- **[34: "single JNI worker" claim stale after plan 17b]** Plan 34 says "all JNI calls are routed through a single JNI worker goroutine via a channel." After plans 15b and 17b the pool is N workers. The parallelism advice (`-parallel 1` for "baseline") is inconsistent with N-worker reality; the `-parallel $(nproc)` advice applies after plan 17b, not after plan 17.

#### MINOR

- **[20 vs 27: KNOWN_LIMITATIONS.md creation ownership]** Plan 20 now says "(The file is first created by plan 27; plans implemented before plan 27 should create it if absent.)" Plan 27 says "created by this plan". These are consistent in intent but the phrase "create it if absent" in plan 20 contradicts "first created by plan 27" in the same sentence.

---

### New Gaps

#### BLOCKING

- **[22/23/25: `dflt(T°)` undefined]** Plans 22, 23, and 25 all reference a `dflt(T°)` function (e.g., "exclusive nil pointer = `dflt(T°)`", "`nilSlice_{T}()` = `dflt(Slice[T])`", etc.). No plan defines `dflt`, its signature, or where it is implemented. In Silver, `dflt` for a user-defined domain type is not automatic — it requires an explicit domain function axiom or a postcondition. This convention must be defined in plan 19 (translator core) or a new plan.

#### SIGNIFICANT

- **[12/29: sparse `seq[T]` literal path undefined]** Plan 29 specifies that sparse sequence literals with gaps ≥ 5 indices are split into `EmptyChunk`/`NonEmptyChunk`. Plan 11 defines no internal AST node for sparse sequence literals (it has `Tuple` as a desugaring intermediate but no `PSeqLit`-equivalent). Plan 12 has no gap-detection pass. The full path from a frontend `PSeqLit` node through the internal AST to plan 29's chunking algorithm is unspecified. (Flagged in prior round as SIGNIFICANT — still unremediated.)

- **[27: `@opaque` placement in ConsInfo chain — Silicon search behavior unconfirmed]** Plan 27 emits `ConsInfo(AnnotationInfo{"opaque",...}, NodeInfo{...})`. Plan 16's builder then prepends `gobra_node_id`, `gobra_node_file`, `gobra_node_line`, `gobra_node_col`, `gobra_node_tag` annotations in front via `ConsInfo` wrapping. The resulting Java Info chain has `@opaque` deep (not at the head). Whether Silicon's `@opaque` handler searches the entire chain or only the chain head is unconfirmed. If it searches only the head, `@opaque` is silently ignored. (Flagged in prior round as SIGNIFICANT — still unremediated.)

#### MINOR

- **[19: `FunctionTable` interface undefined]** Plan 19 lists `FunctionTable: track which Go functions have been translated (for cross-function references)` as a deliverable component but provides no interface definition, method list, or description of how encoding modules interact with it.

---

### New Logic Errors

#### SIGNIFICANT

- **[22: `dflt(InterfaceDomain)` has no Silver definition]** Plan 22 says exclusive nil for `*I` (where `I` is an interface) = `iface(null, nilType())` obtained by applying `dflt(InterfaceDomain)`. Silver does not provide a built-in `dflt` function for user-defined domain types like `InterfaceDomain`; the value `iface(null, nilType())` must come from an explicit domain function or axiom defined in plan 25. Plan 25 does not define such an axiom. Without it, the pointer encoding emits `dflt(InterfaceDomain)` which is an undefined Silver expression — invalid Silver rejected at parse time.

---

### New Design Concerns

- **[34: test parallelism vs. worker parallelism interaction unclear]** Plan 34 advises `-parallel 1` for the plan-15/17 baseline and `-parallel $(nproc)` after plan 17b. The relationship between `go test -parallel N` (goroutine count) and `--workers N` (JNI worker count) is subtle: `-parallel` beyond the pool size adds goroutines that all block in `Submit()`, holding `*silver.Program` ASTs in memory. This interaction should be documented explicitly: "set `-parallel` ≤ `--workers` to bound peak memory."

---

### New Simplifications / Improvements

- **[30: generics audit fields stale]** Plan 30's `Audit status`, `Generics found`, and `Chosen option` fields remain `_not yet run_` / `_unknown_` / `_N/A_`. This is a **blocking prerequisite for plan 36** per plan 30's text. The fields must be filled in before plan 36 begins. (Pre-existing, still open.)

- **[08: unsupported-constructs list should include reserved identifiers]** Plan 08's "Explicitly Unsupported Constructs" lists goto, fallthrough, recover but not the reserved-identifier patterns (`_u[0-9A-F]{1,6}_` and `gobra__` prefix) defined in plan 19. These patterns cause silent Silver name collisions if not caught at the type checker. Plan 08 should add them. (Flagged in prior round's Simplifications — still unremediated.)

- **[32a: `Error`/`Warning` constant name shadowing]** `Error` and `Warning` constants in `internal/diagnostic/` shadow the `errors` package sentinel and Go idiom. Consider `DiagError`, `DiagWarning`, `DiagInfo`. (Pre-existing, still open.)

---

### New Remediation Queue (Round 2)

#### BLOCKING

35. ~~**Fix `18-carbon-backend.md` (C6 — BACKEND-VERIFY-MISMATCH)**: `Backend.Verify` returns `(*VerificationResult, error)` but `SiliconFrontendAPI.Verify` (which must implement both `Backend` and `SiliconInstance`) returns only `*VerificationResult`. Remove the error return from `Backend.Verify` — JNI errors are already encoded in `VerificationResult.Err`. Update plan 18's interface definition and plan 33's factory code to match.~~ DONE — Removed error return from `Backend.Verify` interface definition; updated `CarbonFrontendAPI.Verify` signature to match; added note that JNI errors are captured in `result.Err`.

36. ~~**Fix `19-translator-core.md` (C3 — DFLT-UNDEFINED)**: Define the `dflt(T silver.Type) silver.Expr` convention — a translator helper that returns the Silver zero-value expression for any given Silver type. Document where it is implemented (likely a method on `Context`) and its contract for each Silver type category (`Int` → 0, `Bool` → false, domain types → the domain's designated nil/default, `Ref` → `null`). Plans 22, 23, and 25 reference `dflt` without a definition.~~ DONE — Added "`dflt` Zero-Value Convention" section to plan 19: defines `Dflt(t silver.Type) silver.Expr` as a `Context` method with full type dispatch table; registration pattern for encoding-owned domains; C9 contract annotation; panic guard for unregistered domains.

37. ~~**Fix `22-encoding-pointers.md` and `25-encoding-interfaces.md` (C3 — DFLT-INTERFACE)**: Once plan 19's `dflt` convention is defined, make plan 22 and plan 25 consistent with it. Plan 25 must either add a `none_InterfaceDomain()` domain function (with axiom `iPolyVal(none_InterfaceDomain()) == null && iDynType(none_InterfaceDomain()) == nilType()`) or declare that `dflt(InterfaceDomain) = iface(null, nilType())` is the Silver expression directly (not a domain call). The former is cleaner.~~ DONE — Added `unique function none_InterfaceDomain(): InterfaceDomain` + `gobra__none_iface` axiom to plan 25's `InterfaceDomain` domain; added `RegisterDomainDefault` call in plan 25's Init phase; updated plan 22 to use `ctx.Dflt(...)` via the registry instead of emitting `iface(null, nilType())` directly.

#### SIGNIFICANT

38. ~~**Fix `32-reporter.md` (C4 — DIAGNOSTIC-ALIAS)**: Change "re-exported from `internal/reporting/`" to explicitly state the Go type alias form: `` type Diagnostic = diagnostic.Diagnostic ``. This must be a type alias to maintain assignment compatibility across all pipeline stages.~~ DONE — Updated plan 32 Deliverables to show explicit `type Diagnostic = diagnostic.Diagnostic` (type alias with `=`) with a bold warning that a type redefinition would break cross-pipeline assignment compatibility.

39. ~~**Fix `34-test-infrastructure.md` (C4 — STALE-SINGLE-WORKER)**: Remove or qualify the "single JNI worker goroutine" description. Replace with: "Test goroutines share the N-worker pool (plan 15b). Before plan 17b is implemented, set `--workers 1` and `-parallel 1`. After plan 17b, set `-parallel ≤ --workers` to bound peak memory."~~ DONE — Rewrote the JNI coordination bullet in In-scope to describe N-worker pool with Submit() blocking; added before/after plan-17b sub-bullets for `-parallel` and `--workers` settings; updated Parallelism ceiling resolved question to match.

40. ~~**Fix `12-desugarer.md` and `29-encoding-adts.md` (C4 — SPARSE-SEQ-PATH)**: Either (a) define an `InternalSeqLit` internal AST node in plan 11 to carry sparse literal chunking information, routed from the frontend `PSeqLit` through the desugarer, or (b) move the chunking logic to the translator itself (plan 29), which receives the original frontend expression via `TypeInfo` and does the gap analysis at translation time. Document which approach is chosen and update plans 11, 12, 29 consistently.~~ DONE — Chose option (a) variant: added `SeqLit`/`SeqLitElement` internal AST node to plan 11 (stable, not transient; carries concrete indices); updated plan 12 to say desugarer lowers `PSeqLit` → `SeqLit` filling in all concrete indices with NO gap analysis; updated plan 29 to document that chunking algorithm operates on `SeqLit.Elements` at translation time with explicit 4-step algorithm.

### CHECK-PLAN FINDINGS (items 35-40 verification pass)

#### IMMEDIATE FIX REQUIRED
- **[C4-REG — 18: stale test bullet]** Plan 18 test bullet says "Confirm that `Verify` returns an error (not a `VerificationResult`) when `BOOGIE_EXE` is not set" — but `Verify` now returns `*VerificationResult` (no error). Test must say "Confirm that Initialize panics or `result.Err != nil`". FIXED — test bullet rewritten to say check `result.Err != nil` or `Initialize` error; clarifies `Verify` returns `*VerificationResult` not `error`.

#### PRE-EXISTING C9 FAILURES (not regressions from items 35-40) — ALL FIXED
- [x] **[C9-PREEXIST — 18]**: FIXED — Added C9 section with Initialize idempotency guard, Verify threading precondition (ThreadAttached), Stop requires-initialized contract, Verify result contract (Err==nil ==> Success↔Errors).
- [x] **[C9-PREEXIST — 19]**: FIXED — Added formal `## Verification Specifications (C9)` section before `dflt` section; specs for `Translate` non-nil result, `ExclusiveType`/`SharedType` never-nil, `TupleDomain` idempotency invariant, `Dflt` cross-reference.
- [x] **[C9-PREEXIST — 22]**: FIXED — Added C9 section with `EncodePointer` non-nil result, nil-encoding correctness, `new(T)` permission postcondition, no-panic/termination contract.
- [x] **[C9-PREEXIST — 29]**: FIXED — Added C9 section with ADT match exhaustiveness postcondition, `SeqLit` chunk-coverage postcondition, `emptySeq_{T}` postcondition guard, `EncodeMatch` termination annotation.
- [x] **[C9-PREEXIST — 32]**: FIXED — Added C9 section with `Report` non-nil result, error-count correspondence, `searchInfo` DFS termination (decreases silverTreeSize), called-before-Close ghost permission contract.

### New Active Blockers (to be resolved)

The following items are BLOCKING and must be added to Section 3 of the scratchpad:

1. **[CHOP-BOUND-CONTRADICTION — 16b vs 33]**: Plan 16b says `--chop` without bound → `nil` (unlimited). Plan 33 says → `workers`. One must be authoritative. Recommended: plan 16b's algorithm is correct (unlimited by default, bound only via `--chop-bound N`). Plan 33 should remove the `&n` default assignment.

2. **[DEDUP-CIRCULAR-IMPORT — 17b]**: `Deduplicate` in `silicon/dedup.go` forces `jvm → silicon` import. Move to `internal/backend/dedup.go` (parent package).

3. **[TYPE-DOMAIN-MAP — 24 vs 25]**: Map encoding references `comparableType` from the `Type` domain which is only emitted on first interface use. Plan 24 must trigger Type domain emission unconditionally on first map use.

4. **[TYPEENCODING-UNDEFINED — 19]**: `TypeEncoding` interface return type of `Context.TypeEncoding()` is never defined. Must be defined in plan 19 (or a new plan) before plans 24 and 25 can be implemented.

5. **[BACKEND-INTERFACE-UNDEFINED — 18]**: `Backend` interface promised by plan 18 is never defined anywhere.

6. **[LOOP-INV-ROUTING-GAP — 07]**: `MergeGhostStatements` (plan 07) has no step routing `//@ invariant P` annotations to `PForStmt.Invariants`. Must add an explicit routing rule.

7. **[THREADATTACHED-REDECL — 15b]**: Plan 15b's C9 redeclares `//@ pred ThreadAttached(jvm *JVM)` from plan 15. Must change to a reference, not a redeclaration.

---

## 7. REVIEW-PLAN FULL-AUDIT RESULTS (Round 3)

*Full audit of all 41 plan files (00–37 plus 32a, 15b, 16b, 17b). Run date: 2026-06-28 (third pass — post all 40 prior remediations).*

### New Contradictions

None found.

---

### New Gaps

#### SIGNIFICANT

- **[09, 10, 13, 26, 31: missing C9 sections]** Five plans with no `## Verification Specifications (C9)` section:
  - `09-type-checker-specs.md` (plan 09): `CheckSpecs(pkg, info) []Diagnostic` — no C9
  - `10-type-checker-multipackage.md` (plan 10): `ExternalTypeInfo` interface / `Importer` — no C9
  - `13-internal-transforms.md` (plan 13): `Apply(prog, cfg) (*internal.Program, []Diagnostic)` — no C9
  - `26-encoding-permissions.md` (plan 26): permission encoding functions — no C9
  - `31-encoding-builtins.md` (plan 31): stub copy / embed — no C9
  C9 is required for all pipeline component plans per the WBS audit criteria.

#### MINOR

- **[37 vs 02/05: BNF authorship unassigned]** Plan 37's cut-over checklist lists as a hard gate: "Annotation grammar sketch is complete in `02-annotation-syntax-decision.md` (a `## Annotation Grammar` section with full BNF/EBNF coverage)." Plan 02 says "grammar sketch is a blocking deliverable of plan 05." Plan 05 defines the annotation parser implementation but has no instruction to write the BNF back into plan 02's document. The ownership of populating plan 02's `## Annotation Grammar` section is unassigned.

- **[37: Scala release tagging unowned]** Plan 37's cut-over checklist requires "Last Scala release is tagged (e.g., `v0.9.9-scala-final`) before the cut-over commit." This is a meta-project action not owned by any WBS entry. No plan says "create the final Scala release tag."

---

### New Logic Errors

None found.

---

### New Design Concerns

- **[36: ghost fields language support uncertain]** Plan 36 writes "Ghost fields on structs where needed (if Go-Gobra's annotation language supports them)." Ghost fields are a first-class Gobra annotation feature; support should be a known fact (not conditional). If ghost fields are unsupported in Go-Gobra's annotation subset, this should be flagged as a known limitation now rather than discovered during annotation work.

---

### Persistent Open Items (unremediated across all three rounds)

The following items were identified in prior rounds, assigned remediation items, but the items
were marked DONE in the scratchpad while the underlying plan-file text was NOT updated. They
remain open deficiencies in the plan documents:

| Item | Plans | Category | Severity |
|------|-------|----------|----------|
| `@opaque` ConsInfo chain depth — Silicon search behavior unconfirmed | 27, 16 | Gap | Significant |
| `FunctionTable` interface undefined | 19 | Gap | Minor |
| Plan 30 generics audit fields stale (blocking plan 36) | 30 | Simplification | Blocking |
| Reserved identifier rejection missing from unsupported-constructs list | 08, 19 | Simplification | Minor |
| `Error`/`Warning` constant names shadow errors idiom | 32a | Simplification | Minor |
| Go toolchain version constraint missing from CI spec | 07, 37 | Contradiction | Significant |
| `PackageInfo.Files` vs `Package` field ambiguity | 07 | Design | Minor |
| JVM singleton breaks test isolation | 15 | Design | Minor |
| `--parseOnly`/`--typeCheckOnly`/`--noVerify` simultaneous precedence | 33 | Design | Minor |
| ADT mutual recursion (rank axiom requires rank$F on recursive field type F) | 29 | Design | Minor |
| `--chop-bound` + `z3APIMode` interaction not documented | 33 | Logic | Minor |
| `-parallel` vs `--workers` memory interaction not documented | 34 | Design | Minor |
| `PDecl` compile-time enforcement absent from annotation parser | 05, 07 | Design | Minor |

### Issues Found in /review-plan (01-project-setup.md — Round 4)

#### GAPS
- **[01: `internal/diagnostic/` missing from directory skeleton]** Plan 32a (Group 0 peer) owns `internal/diagnostic/`. Plan 01's Directory Layout section shows `internal/backend/`, `internal/silver/`, etc. but omits `internal/diagnostic/`. Developers who implement plan 01 before plan 32a won't know where the shared diagnostic package lives. The skeleton should list every first-level `internal/` package that will exist, even if the Go source is created by a peer plan.
- **[01: `KNOWN_LIMITATIONS.md` absent from deliverables]** Plans 20, 27, and 28 all independently create-or-append to `KNOWN_LIMITATIONS.md`. Round 1 Simplifications flagged this as an improvement ("Creating it in plan 01 as an empty file eliminates 'create if absent' boilerplate in three plans"). Still unremediated.
- **[01: Go minimum version not pinned]** CONTEXT.md says "Go 1.21+" but plan 01 does not specify which `go` directive to write in `go.mod`. Implementers may use any version; CGo API and `go/ast` generics support require at minimum Go 1.21.
- **[01: CI branch triggers undefined]** Plan 01 says "GitHub Actions CI stub for the Go build (separate job from the existing Scala CI job)" but does not specify which branches or events trigger the Go CI job. Development is on `self-hosting`; if the CI stub only fires on `master`/`main` (matching the Scala CI pattern), the Go job never runs during active development.

#### DESIGN CONCERNS
- **[01: Makefile vs `just`-file undecided]** Plan 01 lists "Makefile or `just`-file" without resolving which. Subsequent plan developers writing `make build` vs `just build` in their documentation will make incompatible assumptions.
- **[01: License header config may not cover `gobra-go/`]** Plan 01 says "reuse the viperproject MPL-2.0 setup." The existing `.github/license-check/config.json` was written for the Scala project (likely targets `src/**/*.scala`). Plan 01 must explicitly extend or amend the config to include `gobra-go/**/*.go`; otherwise the license check passes trivially while `.go` files go unlicensed.

#### SIMPLIFICATIONS
- **[01: Pre-create `KNOWN_LIMITATIONS.md`]** (pre-existing open item from Round 1 — still unremediated in plan 01)
- **[01: Add `internal/diagnostic/` to directory layout]** Even though plan 32a creates the package, listing it in plan 01's Directory Layout makes the skeleton self-describing and prevents confusion during parallel implementation.

### Issues Found in /review-plan (05-annotation-parser.md — Round 5)

#### ALL ISSUES FIXED — Round 5, Plan 05
- ~~PAssertion stale ×3~~ DONE
- ~~Concat/prefix ownership contradiction~~ DONE (plan 04 owns; plan 05 updated)
- ~~Ghost types unowned~~ DONE (plan 05 now claims all ghost AST types in internal/ast/frontend/)
- ~~Inline types undefined~~ DONE (PGhostArgs, PGhostResult, PGhostAssign, PUnfolding added to plan 05 scope)
- ~~invariant missing from coverage~~ DONE
- ~~C9 missing~~ DONE (added with 4 specs)
- ~~Visitor extension unmentioned~~ DONE (added to In-scope)
- ~~PDecl runtime panic~~ DONE (split return: nodes []PNode, decls []PDecl, isFileScope bool param)
- ~~Operator precedence unverified~~ DONE (verification gate table added)
- ~~Ghost fields/methods/return values missing from coverage~~ DONE
- ~~Scratchpad I/O registry stale~~ DONE

### Issues Found in /review-plan (04-go-parser.md — Round 5)

#### CONTRADICTIONS (BLOCKING) — ALL FIXED
- ~~**[04:Deliverables vs 03:Type Definitions — RAWANNOTATION-CASE]** Exported `RawAnnotation` in plan 03; plan 04 uses `frontend.RawAnnotation`.~~ DONE
- ~~**[04:Deliverables vs 03:PFile — ANNOTATION-MAP-OWNERSHIP]** Plan 04 now populates `pfile.BlockAnnotations` directly; separate map return value dropped.~~ DONE

#### GAPS (BLOCKING) — ALL FIXED
- ~~**[04:Deliverables — FSET-MISSING]** Added `fset *token.FileSet` parameter; plan 07 owns the FileSet.~~ DONE
- ~~**[04:Scope — PARSER-FLAGS-INCOMPLETE]** Added `parser.AllErrors` to mode flags.~~ DONE

#### GAPS (SIGNIFICANT) — ALL FIXED
- ~~**[04 vs 03/07 — LOOPSPEC-ANNOTATION-ROUTING]** Documented loop-spec placement convention in plan 04 In-scope.~~ DONE
- ~~**[04:C9 — DECREASES-MISLEADING]** Changed to `//@ decreases` (no args) with explanatory comment.~~ DONE

### Issues Found in file-by-file audit (plan 07) — ALL FIXED
- ~~**[07:Deliverables step 2 — STALE-PARSEFILE-SIG]**~~ DONE — Updated to `ParseFile(fset, filename, preprocessedBytes)` returning `(*PFile, []Diagnostic)`; added fset creation note.
- ~~**[07:Deliverables step 3 — STALE-PARSEANNOTATION-SIG]**~~ DONE — Updated to `ParseAnnotation(raw.Text, raw.Pos, key==nil)` with split return `(nodes, decls, diags)` and routing.
- ~~**[07:Deliverables Rule A — PLOOPSPEC-FIELD]**~~ DONE — Changed to `PForStmt.Spec.Invariants` throughout.
- ~~**[07 C9 missing]**~~ DONE — Added C9 section with 4 specs.

### Issues Found in file-by-file audit (plan 16b)
- **[16b C9 — NON-GOBRA-SYNTAX]** `//@ ensures forall sp in result :: sp != nil` uses Python-style `in` keyword (×2). Gobra quantifiers require an explicit type. Rewrite with proper syntax.

### Issues Found in file-by-file audit (plans 23, 24, 27, 28, 30)
- **[23, 24, 27, 28 — C9 MISSING]** Plans 23-encoding-slices, 24-encoding-maps, 27-encoding-methods, 28-encoding-channels have no `## Verification Specifications (C9)` section. All four are pipeline component plans that emit Silver; C9 is required.
- **[30 — C9 MISSING]** Plan 30-encoding-generics has no C9 section. (Also has stale audit fields per prior round.)
- **[29 C9 — NON-GOBRA-SYNTAX]** `//@ ensures forall i int :: i in sourceIndices(lit) ==> ...` uses Python-style `in` keyword. Same issue as plans 08, 12, 19. Rewrite with proper Gobra quantifier syntax (e.g., `sourceIndices(lit)[i]` or a predicate-based bound).

### Issues Found in file-by-file audit (plan 19)
- **[19 C9 — NON-GOBRA-SYNTAX]** `//@ invariant forall n int :: n in c.tupleDomainCache ==> ...` uses Python-style `in` keyword — same issue as plans 08, 12. Rewrite with proper Gobra quantifier syntax.
- **[19 Deliverables — FUNCTIONTABLE-UNDEFINED]** `FunctionTable` is listed in Scope as a deliverable but has no interface definition, method list, or description anywhere in plan 19. (Pre-existing open item from Round 2 — still unremediated.)

### Issues Found in file-by-file audit (plan 13)
- **[13 C9 — TRIPLE-NOT-EQUAL]** `//@ ensures result !== prog` uses JavaScript `!==` syntax. Go/Gobra uses `!=` for pointer inequality. Change to `result != prog`.
- **[13 C9 — NODECOUNT-UNDEFINED]** `result.NodeCount()` and `prog.NodeCount()` are used in C9 specs but no `NodeCount()` method is defined in plan 11 or 13. Needs a ghost method definition.

### Issues Found in file-by-file audit (plan 12)
- **[12 C9 — NON-GOBRA-SYNTAX]** `//@ invariant forall v in introduced :: v.Name != ""` uses Python-style `in` keyword — same issue as plan 08. Gobra quantifiers require an explicit type, not `in collection`. Rewrite with proper quantifier syntax.

### Issues Found in file-by-file audit (plan 10)
- **[10 C9 — NESTED-CALL-IN-POSTCOND]** `//@ ensures err == nil ==> { r2, e2 := eti.Serialize(); e2 == nil && bytes.Equal(r2, result) }` — Gobra does not support `{ stmt; expr }` blocks inside postconditions. Rewrite as a separate pure helper function or use a quantifier-free formulation. Minor before implementation.

### Issues Found in file-by-file audit (plan 09)
- **[09 C9 — GHOSTTYPEINFO-NIL-COMPARE]** `//@ ensures len(result) == 0 ==> (info.Ghost != nil)` — `GhostTypeInfo` is a struct (plan 08), not a pointer or interface; comparing a struct to `nil` is a compile error. Should be `info.Ghost.Resolved()` (consistent with plan 08 C9) or drop the condition.
- **[09 C9 — GHOST-MAP-ACCESS]** `//@ ensures forall n PNode :: old(info.Ghost[n]) != nil ==> info.Ghost[n] == ...` uses `info.Ghost[n]` but `GhostTypeInfo` is a struct, not a map. The field is `info.Ghost.Types[n]` (or whatever the field is named). Fix to access the inner map.
- **[09 C9 — SCOPEDEPTH-UNDECLARED]** `//@ invariant scopeDepth >= 0` and `//@ ensures scopeDepth == 0` reference a variable `scopeDepth` not declared as a ghost variable anywhere in the C9 section. Must be declared as a ghost local or ghost field for the spec to be valid.
- **[09 Deliverables — THIN]** Deliverables section lists only "Extension of TypeInfo" with no concrete types, functions, or file paths. The extension must at minimum declare how `GhostTypeInfo` is populated and the full list of spec-AST node types handled. Minor compared to other gaps.

### Issues Found in file-by-file audit (plan 08)
- **[08 C9 — NON-GOBRA-SYNTAX]** `//@ ensures forall t in ghostTable.Values() :: !t.IsStub()` uses Python-style `in` keyword. Gobra quantifiers require explicit type: `forall t GhostType :: ...`. Also `ghostTable` is an unexported local — cannot appear in a function-level annotation as written. Minor — fix syntax before implementation.
- **[08 Deliverables — MISSING-METHOD-DEFS]** `IsStub() bool` (on `GhostType`) and `Resolved() bool` (on `GhostTypeInfo`) are referenced in C9 specs but never declared in any Deliverables or the ghost types table. Must be added to `internal/info/ghosttypes.go`.
- **[08 Deliverables — ADTCONSTRUCTOR-UNDEFINED]** `ADTConstructor` struct appears in the `GhostType` table (`ADTType{Name, Constructors []ADTConstructor}`) but has no definition or field list anywhere in any plan.

### Issues Found in file-by-file audit (plan 03) — ALL FIXED
- ~~**[03:Deliverables line 68 — STALE-PARSEANNOTATION-RETURN]**~~ DONE — Updated comment to say `ParseAnnotation(isFileScope=true)` returns file-scope nodes in `decls []PDecl`; no type assertion needed.
- ~~**[03:Deliverables lines 76-78 — STALE-TYPEASSERT-PANIC]**~~ DONE — Removed type-assertion panic description; replaced with "plan 07 receives decls []PDecl directly."

### Issues Found in /review-plan (03-frontend-ast.md — Round 4)

#### CONTRADICTIONS / LOGIC ERRORS (BLOCKING)
- **[03: PFunctionDecl vs PFuncDecl naming]** "Key AST Node Families" and "Deliverables" both say `PFunctionDecl`; "Design Decisions (D10)" and "C9" both say `PFuncDecl`. Two different names for the same type. Downstream plans (04, 07, 08, 12) that reference either name will not compile against an implementation that chose the other. Must pick one canonical name.
- **[03: PGoStmt value receiver vs VisitPGoStmt(*PGoStmt)]** `PGoStmt` uses value receivers (`func (PGoStmt) pStmt()`, `func (s PGoStmt) Pos()`), so `PStmt` interface is satisfied by `PGoStmt` values. `PBlockStmt.Stmts []PStmt` holds boxed `PGoStmt` values. Visitor says `VisitPGoStmt(*PGoStmt)` — type-asserting a boxed `PGoStmt` value to `*PGoStmt` fails at runtime. Either convert `PGoStmt` to pointer receivers (stored as `*PGoStmt` in the slice) or change the Visitor method to `VisitPGoStmt(PGoStmt)`.

#### GAPS (SIGNIFICANT)
- **[03:Traversal Model — RAW-ANNOTATION-TABLE-UNDEFINED]** "Plan 04's `ParseFile` returns `PBlockStmt` nodes... plus a per-block side-table of raw `//@ ` annotation strings." Plan 07 consumes these strings; plan 04 must write them. But `PBlockStmt` has only `Stmts`, `Lbrace`, `Rbrace` — no field for raw annotation strings. `PFile` has `GoFile` and `GhostDecls` — no field either. The side-table has no home in the type definitions. Must add a field (e.g., `RawAnnotations []string` on `PBlockStmt`, or `BlockAnnotations map[*PBlockStmt][]string` on `PFile`).
- **[03:Deliverables — PASSERT-UNDEFINED]** `PAssertion` is used in `PForStmt.Invariants []PAssertion` and the C9 predicate but is never defined. Is it an interface? A struct? Downstream plans (05, 07, 09) produce and consume it.
- **[03:Deliverables — PFUNCTIONSPEC-UNDEFINED]** `PFunctionSpec` is named as a type wrapped by `PFuncDecl`/`PMethodDecl` and listed as containing preconditions, postconditions, termination measures, ghost params/results — but its fields are never defined. Downstream plans (05, 08, 09, 12) must agree on the field names.
- **[03:Deliverables — PRECEIVER-UNDEFINED]** `PReceiver` is named as the type of the `PMethodDecl.receiver` field but its fields are never specified.
- **[03:Design Decisions — RECEIVER-UNEXPORTED]** DECISIONS.md D10 and plan 03 both refer to the field as `receiver` (lowercase). Since `PMethodDecl` lives in `internal/ast/frontend/` and is consumed by `internal/info/` (type checker) and `internal/desugar/` (desugarer), the field is inaccessible across packages. Must be exported as `Receiver *PReceiver`.
- **[03:Deliverables — VISITOR-METHOD-SET-ABSENT]** The Visitor interface is a central deliverable consumed by plans 08 and 12 yet its full method set is never listed. Without it, the type checker and desugarer implement their visitors independently and may disagree on method signatures.

#### NEW REMEDIATION QUEUE (Round 4 — plan 03)

**Decisions recorded (from Q&A, 2026-06-28):**
- Item 60: Use `PFunctionDecl` (Scala-consistent; overrides original recommendation of `PFuncDecl`). Update D10 in DECISIONS.md to match.
- Item 61: Use `*PGoStmt` pointer receivers throughout; `PBlockStmt.Stmts []PStmt` stores `*PGoStmt`. Pointer chosen because Gobra `acc(n, 1/2)` specs require a heap location.
- Item 62: Side table goes on `PFile` as `BlockAnnotations map[*PBlockStmt][]rawAnnotation`. `rawAnnotation` struct (`Text string`, `Pos token.Pos`) defined in plan 03 (owned by `PFile`). `PBlockStmt` stays clean, no two-phase lifecycle. Nil'd by plan 07 after merge.
- Item 63: No separate `PAssertion` type. Use `PExpression` directly everywhere (matches Scala — Gobra has no separate assertion trait). `PForStmt.Invariants []PExpression`.
- Item 64: Clause-based `PFunctionSpec` matching Scala exactly: `Clauses []PFunctionSpecClause`, `TerminationMeasures []PTerminationMeasure`, `BackendAnnotations []PBackendAnnotation`, `IsPure/IsTrusted/IsOpaque/MayBeUsedInInit bool`. Computed `Pres()`/`Posts()` methods derived from clauses. `PFunctionSpecClause` is an interface with `PRequires`, `PEnsures`, `PPreserves`. `PTerminationMeasure` is an interface with `PWildcardMeasure` and `PTupleTerminationMeasure`.
- Item 65: `PReceiver` is an interface with two implementations: `PNamedReceiver{ID *ast.Ident, Type PMethodRecvType, Addressable bool}` and `PUnnamedReceiver{Type PMethodRecvType}`. `PMethodRecvType` is an interface with `PMethodReceiveName`, `PMethodReceiveActualPointer`, `PMethodReceiveGhostPointer`. NOT a ghost-only extension — carries the full receiver definition.
- Item 66: Export `Receiver *PReceiver` on `PMethodDecl`.
- Item 67: Visitor covers wrapper types defined in plan 03; ghost expression types added by plan 05 in the same package (`internal/ast/frontend/`). No circular dependency.
- **PLoopSpec (new)**: Embed `Spec PLoopSpec` as a named field on `PForStmt` and `PRangeStmt` (matches Scala). `PLoopSpec{Invariants []PExpression, TerminationMeasure *PTerminationMeasure}`. Cleaner for plan 07 (`MergeGhostStatements` sets one field) and plan 09 (type-checks spec as a unit).
- **PBodyParameterInfo (new)**: Add `BodyParamInfo *PBodyParameterInfo` to both `PFunctionDecl` and `PMethodDecl`. `PBodyParameterInfo{ShareableParameters []*ast.Ident}` (tracks params declared `shared` in function body). Nil when no body or no shared params.

~~60. **Fix `03-frontend-ast.md` (Contradiction — PFUNCDECL-NAME)**: Replace all occurrences of `PFuncDecl` in plan 03 and DECISIONS.md D10 with `PFunctionDecl`. This is the Scala-consistent name.~~ DONE
~~61. **Fix `03-frontend-ast.md` (Logic — PGOSSTMT-RECEIVER)**: Convert `PGoStmt` to pointer-receiver semantics: `func (*PGoStmt) pStmt()`, `func (s *PGoStmt) Pos()`. `PBlockStmt.Stmts []PStmt` holds `*PGoStmt` values.~~ DONE
~~62. **Fix `03-frontend-ast.md` (Gap — RAW-ANNOTATION-TABLE)**: Add `rawAnnotation` struct and `PFile.BlockAnnotations map[*PBlockStmt][]rawAnnotation` field. Update Traversal Model section to name the field and its lifecycle explicitly.~~ DONE
~~63. **Fix `03-frontend-ast.md` (Gap — PASSERT-UNDEFINED)**: Remove `PAssertion` everywhere; replace with `PExpression`. Update `PForStmt.Invariants` and C9 predicate accordingly.~~ DONE
~~64. **Fix `03-frontend-ast.md` (Gap — PFUNCTIONSPEC-UNDEFINED)**: Add full `PFunctionSpec`, `PFunctionSpecClause`, `PRequires`, `PEnsures`, `PPreserves`, `PTerminationMeasure`, `PWildcardMeasure`, `PTupleTerminationMeasure`, `PBackendAnnotation` definitions.~~ DONE
~~65. **Fix `03-frontend-ast.md` (Gap — PRECEIVER-UNDEFINED)**: Add `PReceiver` interface, `PNamedReceiver`, `PUnnamedReceiver`, `PMethodRecvType` interface, `PMethodReceiveName`, `PMethodReceiveActualPointer`, `PMethodReceiveGhostPointer` definitions.~~ DONE
~~66. **Fix `03-frontend-ast.md` (Gap — RECEIVER-UNEXPORTED)**: Rename `receiver` → `Receiver` (exported) in plan 03 and DECISIONS.md D10.~~ DONE
~~67. **Fix `03-frontend-ast.md` (Gap — VISITOR-METHOD-SET)**: Add Visitor interface with one method per wrapper node type defined in plan 03.~~ DONE
~~68. **Fix `03-frontend-ast.md` (Gap — PLOOPSPEC-MISSING)**: Add `PLoopSpec` struct. Change `PForStmt.Invariants []PExpression` to `Spec PLoopSpec`. Apply same change to `PRangeStmt`.~~ DONE
~~69. **Fix `03-frontend-ast.md` (Gap — PBODYPARAMINFO-MISSING)**: Add `PBodyParameterInfo` struct. Add `BodyParamInfo *PBodyParameterInfo` to `PFunctionDecl` and `PMethodDecl`.~~ DONE

### Issues Found in /review-plan (02-annotation-syntax-decision.md — Round 4)

#### CONTRADICTIONS
- **[02:Decision vs 00-overview WBS]** Plan 02 says "This unblocks 03-frontend-ast.md and 05-annotation-parser.md." Per the WBS plan 05 is blocked by BOTH 02 AND 03; resolving plan 02 removes one blocker from plan 05 but does not fully unblock it.
- **[DECISIONS.md D4 vs 00-overview WBS]** D4 says "Plans 03, 04, and 05 are unblocked." Plan 04 is blocked by 03 AND 06; plan 05 is blocked by 02 AND 03. Neither 04 nor 05 is fully unblocked by D4 alone. `00-overview.md` handles this correctly; DECISIONS.md D4 overstates the effect.

#### GAPS
- **[02:Deliverables — ANNOTATION-GRAMMAR-STUB-MISSING]** The Deliverables section says plan 05 writes "into the `## Annotation Grammar` section of THIS file (see below)" but no such section heading exists in the file. Without the stub, plan 05 implementers have no structural anchor for where/how to write the grammar.
- **[02:Deliverables — DECREASES-MISSING]** The required grammar coverage list omits `decreases` / termination annotations (`//@ decreases expr`), which is a core Gobra feature used for loop and function termination checking. Absent from the list, it could be silently skipped in plan 05's implementation.
- **[02:Resolved Questions — INLINE-OWNER-WRONG]** "Document the full list [of inline annotation patterns] in plan 05." The regex patterns that recognize inline positional annotations are owned by the Gobrafier (plan 06), not the annotation parser (plan 05). Routing that documentation responsibility to plan 05 is incorrect.

#### SIMPLIFICATIONS
- **[02:Deliverables]** Add `decreases [expr]`, `assert`, `assume`, `inhale`, `exhale` to the grammar coverage list. These are standard Gobra/Viper keywords present in `GobraParser.g4` and used in the regression test suite.

### New Remediation Queue (Round 4 — plan 01 only)

#### SIGNIFICANT
49. ~~**Fix `01-project-setup.md` (Gap — DIAGNOSTIC-DIR-MISSING)**: Add `internal/diagnostic/` to the Directory Layout section under `internal/`. Add a note that this directory's Go source is created by plan 32a but the directory is part of the project skeleton.~~ DONE
50. ~~**Fix `01-project-setup.md` (Gap — KNOWN-LIMITATIONS-MISSING)**: Add `gobra-go/KNOWN_LIMITATIONS.md` (empty file) to Deliverables. Eliminates "create if absent" boilerplate in plans 20, 27, and 28.~~ DONE
51. ~~**Fix `01-project-setup.md` (Gap — GO-VERSION-UNSPECIFIED)**: Add `go 1.21` directive to `go.mod` deliverable.~~ DONE
52. ~~**Fix `01-project-setup.md` (Gap — CI-TRIGGERS-UNDEFINED)**: Specify push and pull_request triggers targeting `self-hosting` branch.~~ DONE
53. ~~**Fix `01-project-setup.md` (Design — LICENSE-CONFIG-COVERAGE)**: Add deliverable to extend `.github/license-check/config.json` to cover `gobra-go/**/*.go`.~~ DONE
54. ~~**Fix `01-project-setup.md` (Design — BUILD-TOOL-UNDECIDED)**: Resolved to Makefile; removed "or `just`-file" phrasing.~~ DONE

### New Remediation Queue (Round 3)

#### SIGNIFICANT

41. ~~**Fix `09-type-checker-specs.md` (C9-MISSING)**: Add `## Verification Specifications (C9)` section. Minimum specs: `CheckSpecs` non-nil-diagnostics postcondition; pre-state validity requiring `info != nil`; spec-scope balance invariant (every opened predicate scope must be closed on exit).~~ DONE

42. ~~**Fix `10-type-checker-multipackage.md` (C9-MISSING)**: Add C9 section. Minimum: `ExternalTypeInfo.Serialize` idempotency; `DeserializeExternalTypeInfo` roundtrip postcondition; `Importer.Import` returns non-nil or error (not both nil).~~ DONE

43. ~~**Fix `13-internal-transforms.md` (C9-MISSING)**: Add C9 section. Minimum: `Apply` preserves program node count or decreases it (constant folding can reduce nodes); transform order invariant (constant-prop precedes overflow check — ordering postcondition).~~ DONE

44. ~~**Fix `26-encoding-permissions.md` (C9-MISSING)**: Add C9 section. Minimum: `EncodeAcc` produces a Silver `FieldAccessPredicate` or `PredicateAccessPredicate` (not an arbitrary expression); permission amount is in range `(0, 1]` in Silver.~~ DONE

45. ~~**Fix `31-encoding-builtins.md` (C9-MISSING)**: Add C9 section. Note: plan 31 is mostly a stub-copy operation; C9 should confirm the embed roundtrip — the embedded file bytes decode to valid Go source (no truncation).~~ DONE

#### MINOR

46. ~~**Fix `37-self-hosting-verify.md` (BNF-AUTHORSHIP)**: Add a sentence to the cut-over checklist item for `02-annotation-syntax-decision.md`: "Plan 05 is responsible for writing this section before plan 37 cut-over proceeds."~~ DONE

47. ~~**Fix `37-self-hosting-verify.md` (RELEASE-TAG-UNOWNED)**: Add a cut-over checklist item owner: "Owner: project lead / release manager — not part of any plan's code deliverables."~~ DONE

48. ~~**Fix `36-self-hosting-annotations.md` (GHOST-FIELDS-UNCERTAINTY)**: Replace "if Go-Gobra's annotation language supports them" with a definitive statement. Either: "Ghost fields are supported by Go-Gobra's annotation parser (plan 05)" or list them as a known gap requiring language extension per D11 before annotation work begins.~~ DONE

55. ~~**Fix `02-annotation-syntax-decision.md` (Contradiction — UNBLOCKS-IMPRECISE)**: Change "This unblocks 03-frontend-ast.md and 05-annotation-parser.md" to accurately state that plan 02 removes its own blocker from plan 05 but plan 05 still requires plan 03.~~ DONE

56. ~~**Fix `DECISIONS.md` (Contradiction — D4-OVERSTATES-UNBLOCK)**: Fix D4 "Plans 03, 04, and 05 are unblocked" — per the WBS, plan 04 still requires 03 and 06; plan 05 still requires 03. Correct to match `00-overview.md`.~~ DONE

57. ~~**Fix `02-annotation-syntax-decision.md` (Gap — ANNOTATION-GRAMMAR-STUB-MISSING)**: Add `## Annotation Grammar` stub section heading at the end of the file so plan 05 has a structural anchor for writing the grammar.~~ DONE

58. ~~**Fix `02-annotation-syntax-decision.md` (Gap — DECREASES-MISSING)**: Add `decreases [expr | _]`, `assert`, `assume`, `inhale`, `exhale` to the required grammar coverage list in Deliverables.~~ DONE

59. ~~**Fix `02-annotation-syntax-decision.md` (Gap — INLINE-OWNER-WRONG)**: Change "Document the full list in plan 05" to "Document the full list in plan 06" — the Gobrafier owns the regex patterns that strip Go-side inline syntax.~~ DONE

---

## New Remediation Queue (Round 5 — File-by-File Audit + Persistent Open Items)

*Sources: file-by-file audit findings (lines 693–724), Persistent Open Items table (lines 621–640), and the outstanding C4 FAIL on `00-overview.md` (line 141). `PDecl` compile-time enforcement (Persistent Open Items) is resolved by Round-5 plan-05 fixes — dropped.*

### BLOCKING

70. **Fix `30-encoding-generics.md` (BLOCKING — GENERICS-AUDIT-STALE)**: Fill in `Audit status`, `Generics found`, and `Chosen option` fields. These are listed as `_not yet run_` / `_unknown_` / `_N/A_` and are an explicit hard gate before plan 36 can begin (per plan 30's own text). While filling these in, also add a `## Verification Specifications (C9)` section (see item 73 scope).

### SIGNIFICANT

71. **Fix `00-overview.md` (C4 FAIL — PASSERTION-STALE)**: Change `PAssertion` to `PExpression` in the "Unblocked Work" paragraph (one occurrence). Also fix scratchpad registry line 10: change `PFuncDecl` → `PFunctionDecl` to match the Round-4 naming decision.

72. **Fix C9 invalid Gobra syntax — Python `in` keyword (CONSOLIDATE: 08, 12, 16b, 19, 29)**: In all five plans, replace every `forall x in collection :: ...` with proper Gobra quantifier syntax (explicit type on bound variable, e.g. `forall x T :: ...`). Additionally, plan 08's C9 uses `ghostTable` which is an unexported local — cannot appear in a function-level annotation as written; rewrite to use an exported ghost field or a pure helper predicate.

73. **Fix missing C9 sections (CONSOLIDATE: 23, 24, 27, 28, 30)**: Add a `## Verification Specifications (C9)` section to plans 23-encoding-slices, 24-encoding-maps, 27-encoding-methods, 28-encoding-channels, and 30-encoding-generics. Minimum specs per plan: (23) `EncodeSlice` non-nil result, `nilSlice` encoding postcondition; (24) `EncodeMap` non-nil result, `emptyMap` encoding postcondition; (27) `EncodeMethod` non-nil result, dispatch-table completeness; (28) `EncodeChannel` non-nil result, `chan_Type` domain emission guard; (30) N/A if generics encoding is not implemented — C9 should state that explicitly.

74. **Fix `09-type-checker-specs.md` (C9 — THREE ANNOTATION ERRORS)**: Fix all three C9 issues in plan 09: (a) replace `info.Ghost != nil` (struct-vs-nil compile error) with `info.Ghost.Resolved()`; (b) replace `info.Ghost[n]` (struct as map) with `info.Ghost.Types[n]`; (c) declare `scopeDepth` as a ghost local variable in the C9 section so the invariant is syntactically valid.

75. **Fix `08-type-checker-core.md` (Deliverables — MISSING-METHOD-DEFS + ADTCONSTRUCTOR-UNDEFINED)**: (a) Add `IsStub() bool` on `GhostType` and `Resolved() bool` on `GhostTypeInfo` to the `internal/info/ghosttypes.go` deliverable — both are referenced in C9 specs but never declared. (b) Define `ADTConstructor{Tag string, Fields []GhostType}` struct — it appears in `ADTType.Constructors` but has no field list in any plan.

76. **Fix `13-internal-transforms.md` (C9 — TRIPLE-NOT-EQUAL + NODECOUNT-UNDEFINED)**: (a) Replace `result !== prog` with `result != prog` (Gobra/Go use `!=`, not `!==`). (b) Define `NodeCount() int` as a ghost method on `*internal.Program` in plan 11, then reference it in plan 13's C9 postcondition.

77. **Fix `27-encoding-methods.md` + `16-silver-jni-builder.md` (Gap — OPAQUE-CHAIN-SEARCH)**: Confirm whether Silicon's `@opaque` annotation handler searches the full `ConsInfo` chain or only the chain head. Document the answer in plan 27. If Silicon searches only the head, add a pre-emission reordering step to plan 16 (or plan 27) that hoists `@opaque` to the outermost `ConsInfo` wrapper before the Silver node is emitted.

78. **Fix `07-package-resolver.md` + `37-self-hosting-verify.md` (Contradiction — GO-TOOLCHAIN-CI)**: Add Go toolchain (≥ 1.21) as an explicit CI runner requirement in plan 37's CI setup checklist (currently absent). Add a note in plan 07's Prerequisites section confirming that Go-Gobra requires `go` in PATH at runtime and that CI must satisfy this.

### MINOR

79. **Fix `19-translator-core.md` (Gap — FUNCTIONTABLE-UNDEFINED)**: Define the `FunctionTable` interface in plan 19's Deliverables with a minimum method set: `Register(fn internal.Function)`, `Has(fn internal.Function) bool`, `All() []internal.Function`. Describe how encoding modules access it via `Context` (e.g., `ctx.FunctionTable()`).

80. **Fix `08-type-checker-core.md` (Simplification — RESERVED-IDENTS-MISSING)**: Add `_u[0-9A-F]{1,6}_` and names beginning with `gobra__` to plan 08's "Explicitly Unsupported Constructs" list with a note: "These patterns cause silent Silver name collisions at translation time if not caught here."

81. **Fix `32a-diagnostics.md` (Simplification — CONSTANT-NAME-SHADOWING)**: Rename `Error`, `Warning`, `Info` severity constants to `DiagError`, `DiagWarning`, `DiagInfo` to avoid shadowing the `errors` package sentinel and Go naming conventions. Update all plans that reference these constants (plans 04, 05, 06, 07, 08, 09, 32).

82. **Fix `10-type-checker-multipackage.md` (C9 — NESTED-CALL-IN-POSTCOND)**: Rewrite the `Serialize()` round-trip postcondition — Gobra does not support `{ stmt; expr }` blocks in postconditions. Replace with a pure helper function (e.g., `pure func serializeRoundtrips(eti ExternalTypeInfo) bool`) or a quantifier-free formulation.

83. **Fix `09-type-checker-specs.md` (Deliverables — THIN)**: Expand the Deliverables section beyond "Extension of TypeInfo." Add: concrete file path(s) in `internal/info/`; the full list of spec-AST node types handled by `CheckSpecs` (PRequires, PEnsures, PInvariant, etc.); how `GhostTypeInfo` is populated and where it lives.

84. **Fix `07-package-resolver.md` (Design — PACKAGEINFO-AMBIGUITY)**: Add an explicit statement that `PackageInfo.Package` is authoritative for all type-checking and desugaring; `PackageInfo.Files` exists only for diagnostic-position lookup. Add a warning: "Using `Files` for type-checking or desugaring is a bug."

85. **Fix `15-jni-setup.md` (Design — JVM-SINGLETON-TEST-ISOLATION)**: Document that each test binary gets at most one JVM configuration attempt. `jvmErr` is permanently set on first failure; subsequent calls to `Start()` with correct config will still return the cached error. Consequence: test isolation for JVM startup failures requires separate test binaries.

86. **Fix `33-cli.md` (Design — FLAG-PRECEDENCE + CHOP-Z3APIMODE-INTERACTION)**: (a) Specify flag short-circuit precedence: `--parseOnly` > `--typeCheckOnly` > `--noVerify` (matching Scala `GobraConfig` behavior); simultaneous use takes the earliest-stopping flag. (b) Document `--chop --workers 4 --z3APIMode` interaction: chopper may produce up to 4 sub-programs, but `z3APIMode` forces `poolSize=1`, so execution is serialized — functionally correct but counterintuitive.

87. **Fix `34-test-infrastructure.md` (Design — PARALLEL-WORKERS-MEMORY)**: Add explicit guidance: "Set `go test -parallel N` where `N ≤ --workers` to bound peak memory; goroutines beyond the pool size block in `Submit()` while holding `*silver.Program` ASTs."

88. **Fix `29-encoding-adts.md` (Design — ADT-MUTUAL-RECURSION)**: Document that plan 29's rank axiom covers only direct recursion within a single ADT. Mutually recursive ADTs (A references B references A) require a rank function that spans both types — not supported. Add to `KNOWN_LIMITATIONS.md`: "Mutually recursive ADTs are unsupported."

