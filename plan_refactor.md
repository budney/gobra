# Plan Refactor Checklist: Architecture Critique Recs 1, 2, 4, 5

Rec 3 (serialization-based node identity) is subsumed by Rec 1: out-of-process gRPC requires
serializing the Silver AST over the wire, so node IDs and positions become Protobuf fields.
The JNI allocation storm and `globalRefs` leakage disappear as direct consequences.

Apply waves in order. Rec 4 and Rec 5 share a wave because both touch plan 08 — revise plan 08
once covering both changes rather than twice. Rec 1 comes last; it benefits from Rec 2's
subprocess Z3 already being reflected in the plans it rewrites.

---

## Wave 1 — Rec 2: Subprocess Z3

### Decisions
- [x] `DECISIONS.md` — add D15: Z3 execution = subprocess (`--z3Exe`); rejects Java API (not thread-safe, forces `poolSize=1`); consequence: `poolSize` constraint removed

### Plans
- [x] `17-silicon-backend.md` — remove `--z3APIMode` path; pass `--z3Exe=$Z3_EXE` to `Silicon.initialize()`
- [x] `15b-worker-pool-expansion.md` — remove `poolSize=1` Z3-API-mode constraint from scope and sync contracts
- [x] `00-overview.md` — update cross-cutting concurrency note; remove single-threaded qualifier
- [x] `scratchpad.md` — delete sync contracts row: "Z3 API mode | forces `poolSize=1`; warning logged if `--workers > 1` | 15b"

---

## Wave 2 — Rec 4 + Rec 5: Frontend Restructure

### Decisions
- [x] `DECISIONS.md` — revise D10: Option B (companion wrappers) → Option A (side-table + `PBlockStmt` exception); correct false "single traversal mechanism" claim (plan 03 itself requires two mechanisms regardless); reverse alternatives section
- [x] `DECISIONS.md` — add D16: `GobraScope` overlay wraps `*go/types.Scope`; checks ghost declarations first, delegates on miss; rejects split-tier call sites; `GhostTypeInfo` remains as storage not lookup

### Plans — Group 0/1: Foundation & Frontend
- [x] `00-overview.md` — update decisions table: "Frontend visitor" row (Option B → Option A + `PBlockStmt` exception, see D10)
- [x] `03-frontend-ast.md` — **MAJOR**: remove wrapper structs (`PFunctionDecl`, `PMethodDecl`, `PTypeDecl`, `PInterfaceType`, `PForStmt`, `PRangeStmt`); add `GobraMetadata` side-table (`map[ast.Node]*GobraMetadata` on `PFile`); `PBlockStmt` survives as standalone container (not a wrapper — `ast.BlockStmt` cannot represent interleaved ghost/Go statement order); shrink `Visitor` interface to ghost-only nodes; `GhostDecls []PDecl` survives unchanged
- [x] `04-go-parser.md` — parser fills `Metadata[node]` entries instead of constructing wrapper structs; output signature `*PFile` unchanged
- [x] `05-annotation-parser.md` — minor: remove prose describing attachment via wrapper fields; output types unchanged
- [x] `07-package-resolver.md` — `MergeGhostStatements` writes to `Metadata` side-table entries instead of wrapper struct fields; `GhostDecls` and 4-step model unchanged

### Plans — Group 2: Type Checker
- [x] `08-type-checker-core.md` — **SUBSTANTIAL** (covers both Rec 4 and Rec 5 in one pass): replace all wrapper type-assertions (`*PFunctionDecl`, `*PMethodDecl`, etc.) with `pfile.Metadata[node]` lookups; add `GobraScope` interface + `gobraScopeImpl` (ghost-first lookup, delegates to `types.Scope`); two-tier storage (`*types.Info` + `GhostTypeInfo`) unchanged; `TypeInfo` output unchanged
- [x] `09-type-checker-specs.md` — name resolution routes through `GobraScope.Lookup` instead of direct `GhostTypeInfo` queries; spec node types unchanged
- [x] `10-type-checker-multipackage.md` — `Importer` wraps `GobraScope` per imported package for cross-package ghost name resolution; dependency on 08 unchanged

### Plans — Group 3: Internal Representation
- [x] `12-desugarer.md` — **SUBSTANTIAL**: replace two-dispatch traversal model (Gobra visitor + `ast.Inspect` inside handlers) with unified `ast.Inspect` + conditional `Metadata[node]` lookups; desugarer output (`*internal.Program`) unchanged; cascade stops here

### Scratchpad
- [x] `scratchpad.md` — package ownership registry: remove wrapper type entries (`PFunctionDecl`, `PMethodDecl`, `PTypeDecl`, `PInterfaceType`, `PRangeStmt`, `PForStmt`); add `GobraMetadata`; update `PFile` entry to include `Metadata` field; pipeline stage I/O table unchanged

---

## Wave 3 — Rec 1: Out-of-Process JVM

### New artifact
- [x] `SilverServer` — thin Scala gRPC server (~300 lines) + `silver.proto` schema; add owner-plan entry to scratchpad registry; built as fat JAR, embedded via `//go:embed`, forked as subprocess at startup

### Decisions
- [x] `DECISIONS.md` — **revise D2**: decision changes to out-of-process gRPC subprocess (`SilverServer`); address self-contradiction (D2 rejected "second language artifact" but then added `SilverBridge.java` — that line was already crossed); remove Prusti/Nagini precedent argument; remove "direct `silver.ast.Program` passing" advantage; delete `SilverBridge.java` addendum; add `SilverServer` addendum; update jenv note (still applies to subprocess JVM); delete "Go-Gobra requires CGo" consequence; soften cross-compilation consequence (Go binary cross-compiles freely; JVM required at runtime only); add "JVM crash sandboxed to subprocess" consequence; revise alternatives (gRPC shim is now the chosen approach; collapse alternatives 2 and 3)

### Plans — Group 4: Silver IR & Backend (major rewrites)
- [x] `15-jni-setup.md` — **MAJOR REWRITE** → Subprocess Lifecycle: drop `jnigi`, drop CGo; `Start(cfg SubprocessConfig) (*Backend, error)` forks JVM process and waits for ready signal; `(*Backend).Stop()` sends SIGTERM; no `sync.Once` singleton; no `runtime.LockOSThread()`; no `AttachCurrentThread`; delete `backend.ThreadAttached()` predicate entirely; rewrite C9 section (goroutine-safety and subprocess health specs replace thread-attachment specs)
- [x] `15b-worker-pool-expansion.md` — **MAJOR REWRITE** → Goroutine Worker Pool: workers are plain goroutines (no OS-thread pinning); one subprocess per worker for fault isolation; delete `LockOSThread`/`AttachCurrentThread` annotations; rewrite C9 section
- [x] `16-silver-jni-builder.md` — **MAJOR REWRITE** → Silver Protobuf Serializer: delete `SilverBridge.java`, `Makefile` JAR target, `//go:embed` for `SilverBridge.jar`; new deliverable: `Serialize(prog *silver.Program) (*proto.SilverProgram, error)` walks Go Silver AST and produces Protobuf message tree; node IDs assigned here as `uint64 node_id` Protobuf fields (Rec 3 absorbed); `NodeMap` populated during serialization (lives in Go memory, trivially GC'd); delete `BuiltProgram.globalRefs` and `BuiltProgram.Close()`; rewrite C9 section (no thread preconditions, no `globalRefs` ownership specs)
- [x] `17-silicon-backend.md` — **MAJOR REWRITE** → gRPC Backend: `Verify()` serializes AST (plan 16), sends gRPC `VerifyRequest`, receives `VerifyResponse`, reconstructs `VerificationError` structs via `NodeMap`; no JNI thread preconditions; dual NodeInfo storage (Java-side `AnnotationInfo` + Go-side fields) collapses to Go-side `NodeInfo` only; rewrite C9 section
- [x] `17b-parallel-workers.md` — **MAJOR REWRITE** → Parallel Goroutine Workers: workers are goroutines; one subprocess per worker; each worker has its own Z3 subprocess (Rec 2 already in place); rewrite C9 section

### Plans — Group 4 (minor downstream)
- [x] `14-silver-ast.md` — add note: `NodeInfo` travels as Protobuf fields rather than `AnnotationInfo` chains; type definitions unchanged

### Plans — Group 6/7/8: Reporting, CLI, Testing (cascade)
- [x] `32-reporter.md` — remove `BuiltProgram.Close()` lifecycle pattern; `VerificationResult.Close` field removed or no-op; drop plan 16 from Dependencies section (plan 15 still owns `VerificationResult`/`VerificationError`)
- [x] `33-cli.md` — update lifecycle config: JVM probing + `JAVA_HOME` → subprocess config (path to `SilverServer` fat JAR, JVM flags); remove CGo build requirement; update plan 16 dependency rationale
- [x] `34-test-infrastructure.md` — update warm/cold path: "warm" = reuse existing subprocess connection (not "skip JNI init"); update `SiliconConfig`/`SiliconInstance` shapes (`jobject` references gone); delete JVM test-isolation note (separate binaries for JVM startup failures no longer needed)

### Plans — Overview
- [x] `00-overview.md` — update decisions table: "Backend interface" row (JNI → out-of-process gRPC, see D2); update concurrency note

### Scratchpad
- [x] `scratchpad.md` — package ownership registry: remove `jvm`, `jnigi`, `jobject`, `SilverBridge`, `globalRefs` entries; add `SilverServer` owner entry; add subprocess/gRPC entries; sync contracts table: remove all JNI thread-safety rows; pipeline stage I/O: `Silicon Backend (17)` input stays `*silver.Program`, no other changes

---

## Post-Refactor: Audit Round

- [x] Run `/check-plan ALL` focused on:
  - C1 — D2 and D10 reversals create new cross-reference obligations across all affected plans
  - C4 — `GobraMetadata` and gRPC types replace many current cross-plan type references
  - C5 — plan 32 drops plan 16 dependency; `SilverServer` needs an owner plan entry; verify no dangling refs to deleted types (`jobject`, `BuiltProgram.globalRefs`, wrapper structs)
  - C9 — plans 15, 15b, 16, 17 need fresh C9 sections after `ThreadAttached` deletion; verify no plan still references `backend.ThreadAttached()`
