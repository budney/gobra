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

## 3. AUDIT HISTORY
*Six rounds of architectural audit completed against CRITERIA.md (C1–C9). All findings resolved.*

| Round | Scope | Items | Status |
|---|---|---|---|
| 1 | Global contradiction scan | 1–13 | All DONE |
| 2 | Full audit round 2 | 14–27 | All DONE |
| 3 | Full audit round 3 | 28–48 | All DONE |
| 4 | File-by-file audit (plans 01–37) | 49–69 | All DONE |
| 5 | File-by-file audit follow-up | 70–88 | All DONE |
| 6 | File-by-file audit (plans 01–37, second pass) | 89–95 | All DONE |

**Total: 95 remediation items, all resolved.**

---

## 4. OPEN ITEMS

*None.*
