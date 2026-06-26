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

## D2 — Backend interface: JNI via jnigi (embed JVM in-process)

**Decision:** Go-Gobra embeds a JVM in-process using [jnigi](https://github.com/timob/jnigi)
(a CGo-based JNI binding for Go) and calls Silicon/Carbon directly as Java objects, without
going through the ViperServer HTTP API.

**Rationale:**
- The ViperServer HTTP API accepts a local filesystem path to a `.vpr` text file; ViperServer
  reads the file itself. This requires a shared filesystem, a running ViperServer process,
  generating Silver as text, and writing it to disk — significant overhead and complexity.
- JNI is what Prusti (Rust verifier) and Nagini (Python verifier) do: they embed the JVM
  in-process and call Silicon/Carbon as native Java objects. This is the established pattern
  for non-JVM Viper front-ends.
- In-process JNI gives: no filesystem boundary, no HTTP overhead, richer error object access,
  and direct `silver.ast.Program` object passing (same as Gobra's current Scala approach).
- Go JNI libraries exist: jnigi, jnigo, GoJVM. jnigi is the most actively maintained.

**Alternatives considered:**
1. **ViperServer HTTP API + `.vpr` files** — simplest to implement but requires shared
   filesystem, temp file I/O, and a separately managed ViperServer process. Only `viper_client`
   (a demo/testing tool) uses this; no production Viper front-end does.
2. **gRPC shim** — write a ~200-line Scala server accepting Silver via gRPC from Go. Clean
   protocol, typed interface, but requires maintaining a second artifact in a second language,
   which conflicts with the goal of a self-contained Go codebase.
3. **Prusti-style JVM shim server** — a small JVM process accepting requests from Go. More
   moving parts than direct JNI with no clear advantage.

**Thin Java helper JAR addendum**: Direct JNI construction of `scala.collection.immutable.Seq`
objects (required by Silver constructors) is impractical from Go — it requires calling
`Nil$.MODULE$` and `$colon$colon()` for every list element in reverse. A thin Java helper class
(`SilverBridge.java`, ~150 lines) wraps Silver constructors with Java-friendly `Object[]`
signatures and handles Scala collection construction internally. This is compiled into
`SilverBridge.jar`, embedded in the Go binary via `//go:embed`, and loaded alongside the
ViperServer JAR at startup. See [16-silver-jni-builder.md](16-silver-jni-builder.md).

**jenv compatibility**: `JAVA_HOME` must resolve to the real JDK installation, not a shell
shim. Users with jenv must enable the export plugin: `jenv enable-plugin export`. The
`libjvm` locator uses runtime probing across multiple known paths rather than a hardcoded
single path — see [15-jni-setup.md](15-jni-setup.md).

**Consequence:** Go-Gobra requires CGo. Cross-compilation is limited to platforms where a
JVM is available. `JAVA_HOME` must be set at runtime and resolve to the real JDK root.

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
Plans 03, 04, and 05 are unblocked.

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

## D10 — Frontend AST visitor: companion wrapper structs

**Decision:** The Gobra frontend AST uses **companion wrapper structs** (Option B) for every
`go/ast` node that Gobra extends. A single `Visitor` interface covers all Gobra node types,
giving the type checker (08) and desugarer (12) one traversal mechanism.

**Nodes that get wrappers** (only those Gobra actually extends):
- `PFuncDecl` wraps `*ast.FuncDecl` + `*PFunctionSpec`
- `PMethodDecl` wraps `*ast.FuncDecl` + receiver + `*PFunctionSpec`
- `PTypeDecl` wraps `*ast.TypeSpec` + optional Gobra type extension
- `PInterfaceType` wraps `*ast.InterfaceType` + ghost method specs
- `PBlockStmt` wraps `*ast.BlockStmt` + interleaved ghost statements
- `PForStmt` / `PRangeStmt` wrap their `go/ast` counterparts + loop invariants

Leaf nodes Gobra doesn't annotate (`*ast.BasicLit`, `*ast.Ident`, etc.) stay as `go/ast` types.

**Rationale:**
- A single visitor mechanism for the type checker and desugarer avoids having to coordinate
  two traversal APIs (`go/ast`'s own visitor and a separate Gobra visitor) at every call site.
- Boilerplate is bounded: only a small, fixed set of `go/ast` node types need wrappers.
- The approach is consistent with the user's preference for abstraction.

**Alternatives considered:**
1. **Side-table only (Option A)** — no wrappers; extensions in `map[ast.Node]GobExtension`.
   Minimal boilerplate but callers must manage two traversals.
2. **PFile + targeted extension visitor (Option C)** — middle ground. Rejected as offering
   neither the simplicity of A nor the uniformity of B.

**Consequence:** See plan 03 for the complete list of wrapper types and the visitor interface.

---

## D7 — Solo project, no hard deadline

**Decision:** This is a solo effort with no fixed timeline. The WBS is written for sequential
or depth-first execution by a single developer (or AI agent).

**Consequence:** The plan does not optimize for parallel team execution. However, the
dependency graph in [00-overview.md](00-overview.md) identifies workstreams that are
independent and can be parallelized if the project gains contributors.
