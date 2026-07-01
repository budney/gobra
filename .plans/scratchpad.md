# Go-Gobra Rewrite: Automation Scratchpad
> **Rule for AI Agent:** Do not edit any other `.plans/*.md` files until you have updated the active constraints, dependency states, and task items in this scratchpad.

## 1. GLOBAL SYMBOL & COMPONENT REGISTRY
*Tracks artifact ownership (C3), cross-plan type references (C4), and dependency links (C5).*

### Package Ownership
| Package / Artifact | Owner Plan |
|---|---|
| `internal/ast/frontend/` — `PNode`, `PDecl`, `PStmt`, `PGoStmt`, `PBlockStmt`, `PFile`, `GobraMetadata`, `PLoopSpec`, `PBodyParameterInfo`, `PReceiver`, `PFunctionSpec`, `Visitor` | 03 |
| `internal/ast/frontend/` — `PExpression`, `PInvariant`, `PDecreases`, `PWildcardMeasure`, `PTupleTerminationMeasure` | 03 |
| `internal/ast/frontend/` — `PModifier`, `PPure`, `PTrusted`, `POpaque`, `PMayBeUsedInInit` | 03 |
| `internal/ast/frontend/` — `PMagicWand`, `PGhostCall`, `PMatch`, `PMatchCase` | 03 |
| `Visitor` interface (frontend) | 03 |
| `internal/info/checker.go` — `GobraScope` interface, `gobraScopeImpl` | 08 |
| `internal/info/importer.go` — `stubLoaded` ghost field, `isFromStub` ghost method on `*gobImporter` | 10 |
| `internal/ast/internal/` — `Method`, `Function`, `FPredicate`, `Expr`, `Stmt`, etc. | 11 |
| `Visitor` interface (internal) | 11 |
| `internal/silver/` — `Program`, `Member`, `Node`, `NodeInfo`, `VprInfo`, `NoInfo`, `AnnotationInfo`, `ConsInfo`, `TagSynthetic` | 14 |
| `internal/silver/printer.go` (`Print`) | 14 |
| `internal/backend/subprocess/subprocess.go` — `Backend`, `Start`, `Stop`, `Ping`, `SubprocessConfig`, `WorkerPool` struct, `workerJob` | 15 |
| `internal/backend/types.go` — `VerificationResult`, `VerificationError`, `SiliconInstance` interface, `SiliconConfig`, `ThreadAttached()` | 15 |
| `internal/backend/subprocess/subprocess.go` — `NewPool`, `Submit`, full Silicon-aware goroutine worker | 15b |
| `internal/proto/` — generated Go Protobuf bindings for `silver.proto` (`protoc --go_out`) | 15 |
| `internal/backend/silverserver/` — `SilverServer.scala`, `silver.proto`, `SilverServer.jar` (embedded fat JAR) | 15 |
| `internal/backend/silver/serializer.go` — `Serialize`, `SerializedProgram`, `globalNodeID` | 16 |
| `internal/backend/subprocess/dispatch.go` — `DispatchChopped` | 17b |
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
| `pred ThreadAttached()` | Zero-arg predicate in `internal/backend/types.go` (plan 15); no JNI, no thread pinning |
| Loop invariants | `[]PInvariant` (wrapper with source pos), not bare `[]PExpression` |
| Termination measures | `PDecreases` interface (renamed from `PTerminationMeasure`) |
| Function modifiers | `[]PModifier` slice on `PFunctionSpec` with node types `PPure`/`PTrusted`/`POpaque`/`PMayBeUsedInInit` |
| `Context` passing | By-value interface (`ctx Context`), never `*Context`, across all translator/encoding plans |
| `Diagnostic` alias | Every pipeline package declares `type Diagnostic = diagnostic.Diagnostic` (plan 00 cross-cutting note) |

### Synchronization Contracts
| Resource | Mechanism | Plan |
|---|---|---|
| Subprocess fork | `sync.Mutex` on `Backend` struct during `Start`/`Stop` | 15 |
| Worker pool channel | buffered `chan workerJob` | 15 |
| `SiliconFrontendAPI` per worker | goroutine-local (no sharing) | 15b |
| `globalNodeID` atomic counter | `atomic.Uint64` | 16 |

**Removed**: JNI, CGo, `sync.Once` JVM singleton, `runtime.LockOSThread()`, `AttachCurrentThread`,
`DetachCurrentThread` — none apply in the gRPC subprocess design. `ThreadAttached()` now models
OS-thread/subprocess synchronization only, lives in `internal/backend/types.go` (plan 15).

---

## 2. GLOBAL WBS SYNCHRONIZATION STATE
*41 plan files. All plan documents are in a consistent, audited state (21 audit rounds complete, 178 remediation items all resolved). The checkboxes below track implementation status, not plan-document status.*

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

### Group 4: Silver IR & gRPC Backend
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
- [DEFERRED] 30-encoding-generics.md — see D13; check `HasGenericDecl` gate before scheduling
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

## 3. AUDIT HISTORY (condensed)
*21 rounds of architectural audit against CRITERIA.md (C1–C9). 178 items resolved; 0 open.
Full item-by-item detail for rounds 1–21 has been archived out of this file (all items were
RESOLVED with fixes already applied to the plan files themselves — the plan files are the
source of truth, not this log). Round summary:*

| Round | Scope | Items | Status |
|---|---|---|---|
| 1–8 | Global contradiction scans, file-by-file audits, /review-plan ALL passes | 1–104 | All DONE |
| 9–11 | /review-plan ALL passes (2nd–4th) | 105–118 | All DONE |
| 12–14 | /check-plan passes (5th–7th) | 119–126 | All DONE |
| 15–16 | /check-plan & /review-plan (final consistency passes) | — | All PASS |
| 17 | Wave 3 gRPC refactor audit (JNI→gRPC migration cleanup) | 127–138 | All DONE |
| 18 | Deep-dive review, all plans + foundation docs | 139–147 | All DONE |
| 19 | Full file-by-file review, all 41 plans + foundation docs | 148–176 | All DONE |
| 20 | Full /check-plan, all 9 criteria | — | 9/9 PASS |
| 21 | Full /review-plan ALL (post-compaction continuation) | 177–178 | All DONE |

**Key resolved decisions folded into plan text (not restated here):** D12 (Carbon deferred),
D13 (AST-based generic detection replaces grep), D14 (encoding modules use `internal.Type`,
never `go/types`, in public signatures — see below).

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

## 4. TASK DECOMPOSITION STATUS (this session)

**Source of truth:** `decomposition.md` (repo root) — the registry/index for all sub-task work
items, with the naming convention `NN.M-kebab-slug.md` (or `NN a.M-slug.md` for lettered plans).

**Completed:** All 141 sub-tasks listed in `decomposition.md` have been materialized as
individual files in `.plans/tasks/`, one file per `#### NN.M:` entry, following the Standard
Sub-task File Template (Parent plan link, Read first, Requires, Scope, Deliverables, C9,
Done when). Generated mechanically from `decomposition.md`'s own Objective/Deliverables/
Verification Criteria bullets via a one-off script
(`/private/tmp/claude-501/.../scratchpad/gen_tasks.py`, not part of the repo) to guarantee
1:1 fidelity with the registry — no hand-transcription drift.

- 141 files created, covering 39 plans (all plans in decomposition.md except 18 and 30, which
  decomposition.md itself leaves without sub-task entries because both are DEFERRED — see
  Group 4/5 WBS notes above. If 18 or 30 are ever un-deferred, sub-tasks must be added to
  `decomposition.md` first, then corresponding `.plans/tasks/18.N-*.md` / `30.N-*.md` files.
- Per-plan file counts verified against `grep -c '^####'` on `decomposition.md` (141 total).
- `Requires:` field: first sub-task of each plan points to the parent plan's §Dependencies
  section (cross-plan prerequisites); subsequent sub-tasks point to the immediately preceding
  sub-task file (in-plan sequencing).
- `C9:` field: sub-tasks under plans 03, 11, 14 get an explicit N/A (these parent plans carry
  no `## Verification Specifications (C9)` section — annotation is deferred to plan 36).
  All other plans' sub-tasks reference the parent's C9 section directly.
- No parent plan files in `.plans/*.md` were modified — decomposition.md's "Referencing
  Discipline" (parents are single source of truth, sub-tasks reference only) was followed
  exactly; nothing needed correcting to make the decomposition clean.

**Verification performed:** file count (141), per-plan sub-task counts cross-checked against
decomposition.md line-by-line, spot-checked generated files (01.1, 13.6) for template
fidelity, confirmed C9 N/A logic against the `## Verification Specifications (C9)` grep
across all 39 parent plan files.
