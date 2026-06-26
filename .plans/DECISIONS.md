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

**Consequence:** Go-Gobra requires CGo. Cross-compilation is limited to platforms where a
JVM is available. `JAVA_HOME` must be set at runtime.

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

## D4 — Annotation syntax: keep `//@ ...` (recommended, not yet resolved)

**Status: RECOMMENDATION — must be confirmed before work on 03, 04, 05 begins.**
See [02-annotation-syntax-decision.md](02-annotation-syntax-decision.md).

**Recommendation:** Keep the existing `//@ ...` syntax unchanged.

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

**Decision:** The goal is full feature parity with the current Scala Gobra, achieved
incrementally. Self-hosting (Go-Gobra verifies its own source) is the completion milestone.

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

## D7 — Solo project, no hard deadline

**Decision:** This is a solo effort with no fixed timeline. The WBS is written for sequential
or depth-first execution by a single developer (or AI agent).

**Consequence:** The plan does not optimize for parallel team execution. However, the
dependency graph in [00-overview.md](00-overview.md) identifies workstreams that are
independent and can be parallelized if the project gains contributors.
