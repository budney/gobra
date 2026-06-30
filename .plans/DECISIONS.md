# Architectural Decisions: Go-Gobra Rewrite

This file records all significant architectural decisions made for the Go-Gobra rewrite project,
including the rationale and alternatives considered. It is the authoritative record; any AI
agent or contributor resuming work should read this before starting.

---

## D1 — Scope: Rewrite Gobra only; keep Viper/Silver/Silicon/Carbon as-is

**Decision:** The rewrite is limited to Gobra itself. Viper, Silver, Silicon, Carbon, and
ViperServer are trusted as correct and used without modification.

**Rationale:** The goal is a self-hosting Go verifier, not a from-scratch verification
infrastructure. Viper is a mature, well-tested system; rewriting it would dwarf the Gobra
rewrite in effort and risk.

**Alternatives considered:** None seriously — this was the stated requirement.

---

## D2 — Backend interface: out-of-process gRPC subprocess (`SilverServer`)

**Decision:** Go-Gobra communicates with Silicon via an out-of-process JVM subprocess running
`SilverServer` — a thin Scala gRPC server (~300 lines). Go-Gobra serializes the Go Silver AST
to a Protobuf message (`SilverProgram`), sends a `VerifyRequest` over gRPC, and receives a
`VerifyResponse` with structured error objects. `SilverServer` is built as a fat JAR, embedded
in the Go binary via `//go:embed`, and forked as a subprocess at startup by plan 15's
lifecycle code. Each worker goroutine (plan 15b) owns its own `SilverServer` subprocess for
fault isolation.

**Rationale:**
- The originally planned JNI approach (via jnigi) required CGo, a process-wide `sync.Once`
  JVM singleton, `runtime.LockOSThread()` on every worker goroutine, and `SilverBridge.java`
  to work around Scala collection construction difficulties. At that point — with a Java
  artifact already present — the "no second language artifact" argument against a gRPC shim
  was moot. The gRPC subprocess achieves the same result with fewer constraints.
- JVM crashes are sandboxed to the subprocess: a GC failure or Silicon assertion error kills
  only the worker's subprocess, not the Go-Gobra process. Workers restart their subprocess
  independently.
- No CGo: Go-Gobra is buildable with `CGO_ENABLED=0`. The Go binary cross-compiles freely;
  a JVM of the right version is required only at runtime on the target platform.
- Worker goroutines are plain goroutines (no `runtime.LockOSThread()`). Pool size is limited
  only by available CPU and memory, not by OS thread counts.
- Z3 runs as a subprocess per D15; each worker's `SilverServer` manages its own Z3 process,
  so no Z3 thread-safety constraints apply across workers.

**`SilverServer` artifact:** A thin Scala gRPC server in
`internal/backend/silverserver/SilverServer.scala` (~300 lines). It receives a
`VerifyRequest` containing a serialized `SilverProgram`, deserializes it into a real
`silver.ast.Program` using Scala Protobuf bindings, calls Silicon's `verify()`, and returns
a `VerifyResponse` with structured results. Built as a fat JAR, embedded in the Go binary
via `//go:embed` in `internal/backend/silverserver/jar.go`, and forked by plan 15's subprocess
lifecycle code. See [15-jni-setup.md](15-jni-setup.md).

**jenv compatibility:** `JAVA_HOME` must resolve to the real JDK installation, not a shell
shim. Users with jenv must enable the export plugin: `jenv enable-plugin export`. The `java`
binary locator uses `JAVA_HOME` for the subprocess fork; without the real `JAVA_HOME`, the
fork will fail. See [15-jni-setup.md](15-jni-setup.md).

**Alternatives considered:**
1. **ViperServer HTTP API + `.vpr` files** — simplest, but requires a shared filesystem,
   temp file I/O, and a separately managed ViperServer process. No production Viper front-end
   uses this path. Rejected.
2. **JNI via jnigi (in-process)** — originally selected; later found to require CGo,
   OS-thread pinning, a `sync.Once` JVM singleton, and `SilverBridge.java` for Scala
   collection construction. The gRPC subprocess achieves the same integration with fewer
   constraints. Rejected after discovering the JNI complexity.
3. **Prusti-style JVM shim server** — effectively equivalent to `SilverServer` but without
   the Protobuf protocol. Collapsed into the chosen approach.

**Consequences:**
- Go-Gobra does NOT require CGo. `CGO_ENABLED=0` builds are supported.
- Cross-compilation: the Go binary cross-compiles freely. A JVM of the correct version must
  be available at runtime on the target platform.
- `backend.ThreadAttached()` predicate and all JNI thread-safety specs are removed entirely.
  Plan 15b's C9 specs use goroutine-safety and subprocess health invariants instead.
- `SilverServer` and `silver.proto` must be updated in lockstep with the Silver submodule
  version. When the `viperserver/` submodule is updated, regenerate the Protobuf bindings and
  rebuild the fat JAR.
- The `SilverServer` fat JAR is embedded in the Go binary; binary size increases by
  approximately the size of the fat JAR (dominated by Silicon and dependencies).

---

## D3 — Go parser: `go/parser` stdlib + custom annotation mini-parser

**Decision:** Use Go's standard library `go/parser` to parse Go syntax, then walk comment
nodes to extract `//@ ...` annotations and parse them with a hand-written recursive-descent
mini-parser.

**Rationale:**
- Go's grammar is large, subtle, and evolving (especially generics). The stdlib parser is
  correct, spec-compliant, and updated with each Go release.
- Writing a full custom parser (as the Scala Gobra does with ANTLR4) would require months of
  work on the parser alone and risks subtle spec divergence.
- Gobra annotations are syntactically isolated in `//@ ...` comments; they don't interleave
  with Go syntax at the grammar level. A two-pass approach (stdlib for Go, custom for
  annotations) is therefore clean.
- `go/parser` preserves comment nodes; `//@ ...` comments can be matched to their enclosing
  AST scope by comparing `token.Pos` ranges.

**Alternatives considered:**
1. **Full custom parser** — maximum control, but months of work and ongoing spec-tracking
   burden. Rejected as disproportionate for a solo project.
2. **ANTLR4 (Go target)** — generates a parser from the existing Gobra ANTLR grammar. Possible,
   but the ANTLR Go runtime is less mature than the Scala/Java runtime, and the grammar would
   still need porting.

**Consequence:** Annotation expressions are parsed in a second pass, decoupled from the Go AST
at parse time. Error positions in annotation expressions must be computed relative to the
comment node's source position.

---

## D4 — Annotation syntax: keep `//@ ...` (resolved)

**Status: RESOLVED.** See [02-annotation-syntax-decision.md](02-annotation-syntax-decision.md).
Plan 03 (Frontend AST) is fully unblocked. Plan 05 (Annotation Parser) has its 02-blocker
removed but still requires plan 03 to complete. Plan 04 (Go Parser) still requires both 03
and 06; see the WBS in `00-overview.md` for the authoritative dependency list.

**Rationale:**
- Full compatibility with all existing `.gobra` test files and the regression suite.
- The regression suite is the primary validation mechanism throughout this project; breaking
  it at the start adds unnecessary risk.
- No clear ergonomic improvement has been identified that would justify migration cost.

**Alternatives considered:**
1. **Redesigned annotation syntax** (separate spec files, structured comments, build tags) —
   would break all existing tests and require a migration tool. No concrete design advantage
   identified for a solo implementation. Deferred to post-self-hosting if desired.

---

## D5 — Feature scope: full parity, built incrementally

**Decision:** The goal is full feature parity with a pinned version of Scala Gobra, achieved
incrementally. Self-hosting (Go-Gobra verifies its own source) is the completion milestone.

**Version pin**: Do not chase upstream Scala Gobra changes during the port; post-parity sync
is a separate effort.
Target commit: `d7e0b582` (Allow outer function outputs in outline preconditions, fix #1029)

**Rationale:** Full parity ensures the Go rewrite is a true replacement, not a subset tool.
Incremental delivery allows early validation and keeps the project tractable.

**Alternatives considered:**
1. **Exactly enough to self-host** — underspecified; Go-Gobra's own source may eventually use
   features not needed for the initial self-hosting cut, creating a moving target.
2. **Full parity up-front before any testing** — too risky; incremental allows early feedback.

---

## D6 — Testing strategy: port regression suite; Scala Gobra as oracle

**Decision:** The primary validation mechanism is the existing Gobra regression test suite
(`src/test/resources/regressions/`), ported to Go-Gobra. For any test where results differ,
the Scala Gobra output is authoritative (Scala Gobra is the oracle).

**Rationale:** The existing test suite covers a wide range of features and edge cases
accumulated over years of development. Reusing it avoids having to design a new test corpus.
Differential testing against Scala Gobra catches regressions immediately.

**Alternatives considered:**
1. **Write a new test suite from scratch** — loses years of accumulated edge case coverage.
   Rejected.

---

## D8 — Code location: `gobra-go/` subdirectory in existing repo, promoted at cut-over

**Decision:** The Go implementation lives in a `gobra-go/` subdirectory of the existing Gobra
repository, on the `self-hosting` branch. When Go-Gobra reaches full parity and self-hosting,
the Scala source is deleted and `gobra-go/` is promoted to the repo root in a single cut-over
commit.

**Rationale:**
- The existing repo contains everything Go-Gobra needs during development: the regression test
  corpus (`src/test/resources/regressions/`), the built-in stubs (`src/main/resources/`), and
  the `viperserver/` submodule with the Silicon/Carbon JARs required for JNI. Having these
  in place without duplication or additional submodules significantly reduces setup friction.
- The Scala source is the reference implementation throughout the port; having it in the same
  repo (and on the same branch) makes cross-referencing trivial.
- The endgame cut-over commit (delete Scala, promote Go) is a one-time cost. `git log --follow`
  loses history before the move, but this is acceptable for a replacement project.

**Alternatives considered:**
1. **New repository** (`viperproject/gobra-go`) — clean separation and independent CI, but
   requires duplicating or submoduling the regression tests, stubs, and `viperserver/` tree.
   The daily friction during development outweighs the clean-repo benefit. Rejected.
2. **Replace at root on `self-hosting` branch** — avoids the cut-over awkwardness but makes
   the branch so divergent from `master` that tracking upstream Scala changes becomes very
   difficult. Rejected.

**Module path:** `github.com/viperproject/gobra` — same as the Scala project, since this is a
replacement. No conflict during development because the Go module root is `gobra-go/go.mod`,
not the repo root.

**Cut-over steps (deferred to completion of 37-self-hosting-verify.md):**
1. Delete `src/`, `build.sbt`, `project/`, and other Scala artifacts
2. Move contents of `gobra-go/` to the repo root
3. Update `go.mod` path if needed
4. Update CI to remove Scala jobs
5. Tag the last Scala release before the cut-over

---

## D9 — Frontend AST: embed go/ast nodes

**Decision:** The Gobra frontend AST wraps and extends `go/ast` rather than defining parallel
types for every Go construct. Gobra-specific nodes (specifications, ghost constructs, ADTs,
permission expressions) are defined as additional types alongside the embedded stdlib nodes.

**Rationale:**
- `go/types` can be called directly on the underlying `*ast.File`, avoiding a full
  reimplementation of the Go type system (~10k lines, ongoing spec-tracking burden).
- `go/parser` already produces `*ast.File`; a thin wrapper avoids a second full-tree walk
  just to convert node types.
- The Go AST is stable and well-documented; coupling to it is low-risk.

**Alternatives considered:**
1. **Parallel types** — full control but blocks `go/types` usage and requires implementing
   the entire Go type system from scratch. Rejected: disproportionate effort for a solo project.

**Consequence:** The type checker (08) uses `go/types` for standard Go constructs and a
parallel `GhostTypeInfo` for Gobra-specific constructs. The two are combined into a unified
`TypeInfo` output. The package resolver (07) must supply a `types.Importer` compatible with
`go/types.Check` (see plan 10 for the topological ordering this implies).

---

## D10 — Frontend AST visitor: side-table + `PBlockStmt` exception (Option A)

**Decision:** The Gobra frontend AST uses a **`GobraMetadata` side-table**
(`map[ast.Node]*GobraMetadata` field on `PFile`) to attach Gobra extensions to `go/ast` nodes.
No companion wrapper structs are defined for `go/ast` node types. The single exception is
`PBlockStmt`, which survives as a **standalone container** (not a wrapper) — `*ast.BlockStmt`
cannot represent interleaved ghost/Go statement order, so `PBlockStmt` fills that structural gap.

**Nodes extended via the side-table** (not wrappers):
- `*ast.FuncDecl` → `GobraMetadata.Spec *PFunctionSpec`, `.BodyParamInfo *PBodyParameterInfo`,
  `.Receiver *PReceiver` (non-nil only for method declarations). `PReceiver` carries the full
  Gobra receiver definition: named vs. unnamed, value/actual-pointer/ghost-pointer type, and
  addressability — `go/ast` cannot distinguish ghost pointer receivers from actual pointer
  receivers (both are `*ast.StarExpr`). `ast.FuncDecl.Recv` is still used by `go/types` for
  Go-level type checking.
- `*ast.TypeSpec` → `GobraMetadata.GhostExt PNode`
- `*ast.InterfaceType` → `GobraMetadata.GhostMethods []*PFunctionSpec`
- `*ast.ForStmt` → `GobraMetadata.LoopSpec *PLoopSpec`
- `*ast.RangeStmt` → `GobraMetadata.LoopSpec *PLoopSpec`
- `*ast.BlockStmt` → `GobraMetadata.Block *PBlockStmt` (the corresponding `PBlockStmt` with
  interleaved ghost statements)

`PBlockStmt` itself is a standalone node (not a wrapper). It exists because `*ast.BlockStmt`
is structurally incapable of representing the interleaved ghost/Go statement order that Gobra
requires. All other extension data travels via `pfile.Metadata[node]`.

The `Visitor` interface is restricted to ghost-only nodes. Traversal of `go/ast` nodes uses
`ast.Inspect` directly. The type checker (plan 08) and desugarer (plan 12) both use unified
`ast.Inspect` + `pfile.Metadata[node]` lookups.

**Rationale:**
- Plan 03 requires two traversal mechanisms regardless of this choice: `go/types.Check` drives
  traversal of the `*ast.File` for Go type checking; ghost nodes need their own handling.
  Wrapper structs did not reduce this to one mechanism — they added boilerplate without
  eliminating the two-mechanism structure. The "single traversal mechanism" advantage of
  Option B was illusory.
- No wrapper boilerplate: adding Gobra data to a Go AST node requires one `GobraMetadata`
  struct field lookup, not a new named type plus constructor plus Visitor method.
- Callers access the embedded `go/ast` node directly from `ast.Inspect`; no wrapper
  type-assertion is needed to reach the underlying node.

**Alternatives considered:**
1. **Companion wrapper structs (Option B)** — wrappers for every `go/ast` node Gobra extends.
   The stated advantage ("single Visitor mechanism") was illusory: plan 03 requires two
   traversal mechanisms regardless (go/types.Check + ghost visitor). Rejected: adds wrapper
   boilerplate without eliminating the two-mechanism structure.
2. **`PFile` + targeted extension visitor (Option C)** — middle ground. Rejected as offering
   neither the simplicity of A nor the uniformity of B.

**Consequence:** See plan 03 for the `GobraMetadata` struct definition, the `PFile.Metadata`
field, and the shrunk `Visitor` interface (ghost-only nodes). Plan 08 looks up
`pfile.Metadata[node]` for Gobra extensions. Plan 12 uses unified `ast.Inspect` with
`pfile.Metadata[node]` lookups instead of a two-dispatch traversal model.

---

## D7 — Solo project, no hard deadline

**Decision:** This is a solo effort with no fixed timeline. The WBS is written for sequential
or depth-first execution by a single developer (or AI agent).

**Consequence:** The plan does not optimize for parallel team execution. However, the
dependency graph in [00-overview.md](00-overview.md) identifies workstreams that are
independent and can be parallelized if the project gains contributors.

---

## D12 — Carbon backend deferred indefinitely (post-self-hosting scope)

**Decision:** Plan 18 (Carbon backend) is removed from the active WBS and deferred until after
the self-hosting milestone. Silicon is the only active verification backend.

**Rationale:**
- Carbon requires Boogie, which requires Mono on Linux — a heavy transitive dependency that
  most users will not have and that adds no value to the self-hosting milestone.
- Silicon is the default backend in practice; Carbon usage is negligible among real Gobra users.
- The self-hosting CI job (plan 37) runs Silicon exclusively; Carbon has zero contribution to
  the completion milestone.
- The JNI pattern established by Silicon (plan 17) fully documents how a second backend would
  be added; Plan 18 remains as a reference design if Carbon is ever needed post-parity.

**Consequence:**
- The `Backend` interface (previously planned for `internal/backend/types.go` in plan 18) is
  not added. `SiliconInstance` (plan 15) remains the only active backend interface.
- Plan 33 (CLI) does not wire a `--backend carbon` flag; `--backend` flag is omitted entirely
  or reserved for future use.
- The two-interface confusion identified in item 100 (scratchpad round 8) is resolved: there
  is only one interface (`SiliconInstance`), not two.
- If Carbon support is added post-self-hosting, Plan 18's design remains valid; the only change
  needed is adding the `Backend` interface and the `CarbonFrontendAPI` implementation.

**Alternatives considered:**
1. **Keep Carbon in scope** — provides complementary coverage for edge cases where Silicon
   times out. Rejected: the Boogie/Mono dependency burden outweighs the niche benefit for a
   solo self-hosting project.

---

## D13 — Generic-declaration detection uses AST inspection, not grep

**Decision:** The function that identifies `.gobra` test files containing generic declarations
(used to pre-populate the `generics-not-implemented` skip list) parses each file with
`go/parser` and inspects the resulting AST, rather than applying a grep pattern to raw source
text. The function is `HasGenericDecl(*ast.File) bool` (plan 34 deliverable).

**Rationale:**
- A grep pattern is not formally relatable to Go syntax. The pattern
  `^\s*(func|type)\s+\w+\s*\[` is a heuristic that requires manual false-positive review
  and cannot be verified.
- `go/parser` is the authoritative Go parser. A declaration is syntactically generic iff
  `go/parser` sets a non-nil, non-empty `TypeParams` field on the corresponding AST node
  (`*ast.FuncDecl.Type.TypeParams` or `*ast.TypeSpec.TypeParams`). `HasGenericDecl` checks
  exactly those fields.
- Because the check is defined in terms of the parsed AST, its correctness is expressible
  as a Gobra postcondition and can be machine-verified. This aligns with the self-hosting
  goal: the test infrastructure itself becomes a target for formal verification (plan 37
  blocking tier).

**Consequence:** Plan 34 owns `HasGenericDecl` and three ghost predicates
(`funcDeclIsGeneric`, `genDeclHasGenericSpec`, `typeSpecIsGeneric`). Plan 35 specifies the
required Gobra postcondition. Plan 37 must verify `HasGenericDecl` as part of the blocking
tier before cut-over. The grep-based approach is permanently superseded.

---

## D11 — Self-hosting may require extending the annotation language and refactoring Go-Gobra

**Decision:** If Gobra's current annotation language cannot express a key invariant of
Go-Gobra's own source, extending the annotation language is in scope for this project.
Similarly, if Go-Gobra's internal structure makes certain invariants impossible to specify
concisely, refactoring the code to make it more verifiable is in scope.

**Rationale:**
- Self-hosting is the completion milestone (D5). If limitations in the annotation language
  block meaningful specs, extending the language is the correct response — not weakening the
  specs or marking more code `//@ trusted`.
- Go-Gobra is its own first customer. If annotation language gaps show up during self-hosting,
  they are genuine language bugs that would also affect external users. Fixing them here is
  doubly motivated.
- Code refactoring for verifiability is a known practice in the formal verification community.
  Restructuring to eliminate invariant-breaking patterns (e.g., replacing implicit shared
  state with explicit parameters) produces cleaner code as a side effect.

**Scope limitations:**
- Annotation language extensions must be implemented in Go-Gobra itself (not backported to
  Scala Gobra, which is frozen at commit `d7e0b582`).
- Extensions needed for self-hosting should be documented in `SELF_HOSTING.md` with
  rationale; they may warrant promotion to plan 36 deliverables.
- Refactoring is bounded by the requirement that the refactored Go-Gobra still passes the
  regression suite (D6).

**Consequence:** Plans 36 and 37 are open-ended by design. The annotation language and
Go-Gobra implementation are co-evolving artifacts during the self-hosting phase. A gap
discovered in plan 36 may block plan 37 until resolved — this is expected, not a failure.

---

## D14 — Translator encoding modules use internal AST types in their public interfaces

**Decision:** The public function signatures of all translator encoding modules (plans 19–31)
use `internal.Type` and related types from `internal/ast/internal/` as parameters and return
values. They never take `go/types` stdlib types (`types.Type`, `types.Interface`, etc.)
directly. When a function needs `go/types` information, it accesses it through
`ctx.TypeInfo()`, not through a direct parameter of a `go/types` type.

**Rationale:**
- The translator is a distinct pipeline stage that operates on the internal AST (plan 11),
  not on the frontend's `go/types`-backed representation. Passing `go/types` objects across
  the stage boundary couples the translator to the frontend's type-checking layer and makes
  the boundary ill-defined.
- Self-hosting verification (plan 37) is easier when each stage's interface is expressed
  purely in terms of types the project owns. Reasoning about `go/types` internals from within
  Gobra specs would require either trusting those types or providing stubs for them.
- The rule is enforceable by inspection: any `go/types.*` import in an encoding module's
  public signature is a violation.

**Alternatives considered:**
1. **Pass `go/types` objects directly** — avoids a lookup through `ctx.TypeInfo()` at call
   sites, but couples encoding modules to the frontend layer and complicates self-hosting
   verification. The convenience is small; the coupling cost is ongoing.

**Consequence:** Encoding modules that need `go/types` information (e.g., to look up method
sets or interface satisfaction) call `ctx.TypeInfo()` to retrieve the `*TypeInfo` and access
`TypeInfo.Go` from there. The `Context` interface (plan 19) is the approved channel for
cross-stage information. This rule was the basis for fixing `EncodeInterface` (plan 25) from
`*types.Interface` to `*internal.InterfaceType` and `BoxValue`'s `T` parameter from
`types.Type` to `internal.Type`.

---

## D15 — Z3 execution: subprocess via `--z3Exe` (Java API rejected)

**Decision:** Go-Gobra runs Z3 as a subprocess, specified via `--z3Exe=$Z3_EXE`, and passes
this flag as part of the `args []string` to `silicon.initialize(args)`. The Z3 Java API
(`--z3APIMode`) is not implemented.

**Rationale:**
- The Z3 Java API has shared global state that is not thread-safe for concurrent calls from
  different threads in the same process. Using it requires forcing `poolSize=1`, eliminating
  all parallelism benefits from the worker pool.
- Z3 as a subprocess imposes no such restriction: each worker forks its own Z3 subprocess
  independently with no shared state. This is the standard Silicon deployment mode.
- `--z3Exe` is Silicon's default path; the Java API (`--z3APIMode`) is a rarely-used
  alternative that no production Gobra deployment relies on.

**Alternatives considered:**
1. **Z3 Java API (`--z3APIMode`)** — eliminates subprocess fork overhead but forces
   `poolSize=1` due to thread-safety limitations. Rejected: the parallelism constraint
   outweighs the minor startup speedup on any machine with more than one CPU.

**Consequence:** The `poolSize=1` Z3-API-mode constraint is removed entirely. Workers are not
forced to single-threaded operation on account of Z3. The `--z3APIMode` flag and associated
warning/clamping logic are not implemented in Go-Gobra. Plan 15b's worker pool is free to use
any `poolSize >= 1` without Z3-related restrictions.

---

## D16 — Ghost name resolution: `GobraScope` overlay over `*go/types.Scope`

**Decision:** Ghost name resolution in the type checker (plan 08) routes through a `GobraScope`
interface that wraps a `*go/types.Scope`. `GobraScope.Lookup(name)` checks the ghost
declaration table first; on miss, it delegates to the underlying `types.Scope.Lookup(name)`.
No call site accesses `GhostTypeInfo` directly for name resolution; all name-by-string lookups
go through `GobraScope`. `GhostTypeInfo` remains as **storage** (the map of resolved ghost
types keyed by `PNode`), not as a name-lookup mechanism.

**`GobraScope` interface (defined in `internal/info/checker.go`):**
```go
type GobraScope interface {
    // Lookup checks ghost declarations first, then delegates to the Go scope.
    Lookup(name string) types.Object
    // GoScope returns the underlying *go/types.Scope for go/types interop.
    GoScope() *types.Scope
}
```

**`gobraScopeImpl` (unexported implementation):**
```go
type gobraScopeImpl struct {
    ghostDecls map[string]types.Object  // name → ghost object (predicate, ghost func, ADT)
    goScope    *types.Scope
}
func (s *gobraScopeImpl) Lookup(name string) types.Object {
    if obj, ok := s.ghostDecls[name]; ok { return obj }
    return s.goScope.Lookup(name)
}
func (s *gobraScopeImpl) GoScope() *types.Scope { return s.goScope }
```

**Rationale:**
- Without `GobraScope`, callers must check `GhostTypeInfo.Types[node]` and
  `go/types.Scope.Lookup(name)` separately and reconcile results — split-tier call sites
  that are error-prone and verbose.
- `GobraScope` mirrors the `go/types.Scope` API, so the ghost-lookup path is structurally
  identical to the Go-lookup path. Plans 09 and 10 both need to look up names across both
  tiers; one `Lookup` covers both.
- `GhostTypeInfo.Types` is keyed by `PNode` (for resolved type annotation), not by name.
  Keeping storage and lookup separate avoids conflating the two distinct roles.

**Alternatives considered:**
1. **Direct `GhostTypeInfo` queries at each call site** — requires each caller to implement
   the ghost-first fallback pattern manually. Rejected: duplicates the logic and misses the
   ghost-first priority at any call site that forgets to check ghost first.

**Consequence:** Plan 08 constructs a `gobraScopeImpl` per block/file/package scope,
populating `ghostDecls` from `PFile.GhostDecls` during Pass 1. Plan 09 calls
`GobraScope.Lookup` for name resolution in spec expressions instead of direct `GhostTypeInfo`
queries. Plan 10 wraps `GobraScope` per imported package for cross-package ghost name
resolution. The `GhostTypeInfo.Types` map is written by plan 09's `CheckSpecs` and read by
downstream plans (desugarer, translator) — it is never used for name-by-string lookup.
