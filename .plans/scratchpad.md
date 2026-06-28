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

### Pre-existing Blockers (from prior run, not yet fixed)
- **C1**: `scratchpad.md` present in `.plans/` but not listed in WBS or Reference Documents in `00-overview.md` (pre-existing). NOTE: `00-overview.md` Reference Documents table already includes `scratchpad.md` — this was resolved.
- **C4** (00-overview.md): Cross-Cutting Notes says "defined in plan 32" for Diagnostic — should say "defined in plan 32a". Pre-existing; not in scope for this run.
- **C5** (33): Dependencies omits `15b`; pre-existing. Not in scope for this run.
- **C4** (17b): Objective incorrectly references plan 15's scope. Pre-existing; not in scope.

### Current Target Files: 03, 11, 14, 15
| File | Phase 2 Injected | Review Status | Check Status |
|---|---|---|---|
| 03-frontend-ast.md | NEEDS FIX (C10→C9) | PENDING | PENDING |
| 11-internal-ast.md | YES (C9 present) | PENDING | PENDING |
| 14-silver-ast.md | YES (C9 present) | PENDING | PENDING |
| 15-jni-setup.md | YES (C7+C9 present) | PENDING | PENDING |

### Issues Found in Phase 2 Audit
1. **03-frontend-ast.md line 241**: `### Verification Specifications (C10)` — wrong criterion number; **FIXED** (renamed to C9).

### Issues Found in /review-plan (Phase 3)

#### BLOCKING
1. **C5 — 15-jni-setup.md / Dependencies**: `workerJob` uses `*silver.Program` from plan 14, but plan 15 only lists plan 01 as a dependency. Must add plan 14 as a dependency.

#### SIGNIFICANT
2. **C3 — 15-jni-setup.md / Deliverables**: `VerificationResult` type appears in `workerJob` and `Submit` signature but is not claimed as a deliverable by any plan. Must declare ownership in plan 15 (or 15b) and add it here.
3. **C9 — 11-internal-ast.md / Verification Specifications**: Section describes plan 12's (Desugarer) transformation contracts, not plan 11's own node-type invariants. Must replace with plan 11's own specs (predicates on node types). The misplaced text should move to plan 12.
4. **C4 — 11-internal-ast.md / Verification Specifications**: References "14-silver-ast.md" as the consumer of internal AST output — wrong. Plan 14 defines Silver IR struct *types*; the internal AST is consumed by the translator (plan 19). Must fix reference.
5. **C9 — all four files**: C9 sections state intent but include no concrete `//@ requires`/`//@ ensures` annotation examples. Must add at least one illustrative annotation per file.
6. **C9 — 03-frontend-ast.md**: `acc(node.Underlying)` is not a valid Gobra field access (no `Underlying` field exists on wrapper nodes). Must use actual field names (e.g., `acc(n.GoFunc)` for `PFuncDecl`).

#### MINOR
7. **[15:Scope]**: In-scope list omits `WorkerPool` skeleton and `workerJob` type. Must add them.

### Remediation Queue (ordered by severity)
1. ~~Fix `03-frontend-ast.md`: rename `C10` → `C9`.~~ DONE
2. Fix `15-jni-setup.md`: add plan 14 as dependency (C5 blocking).
3. Fix `15-jni-setup.md`: declare `VerificationResult` as a deliverable (C3).
4. Fix `15-jni-setup.md`: add `WorkerPool` skeleton to In-scope list (minor).
5. Fix `11-internal-ast.md`: replace C9 section with plan 11's own node-type invariants.
6. Fix `11-internal-ast.md`: change "14-silver-ast.md" reference to plan 19 in C9.
7. Fix `03-frontend-ast.md` C9: use real field names, add concrete annotation examples.
8. Fix `11-internal-ast.md` C9: add concrete annotation examples.
9. Fix `14-silver-ast.md` C9: add concrete annotation examples.
10. Fix `15-jni-setup.md` C9: add concrete annotation examples.
