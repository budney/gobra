# Go-Gobra Rewrite: Project Context

**Read this first** if you are an AI agent or contributor picking up this project.

---

## What this project is

A complete rewrite of [Gobra](https://github.com/viperproject/gobra) — a formal verifier for
Go programs — from Scala into Go. The goal is a **self-hosting verifier**: a Go program that
can formally verify its own source code using the Viper verification infrastructure as a backend.

The existing Scala Gobra lives in this repository. The Go rewrite will be a new codebase
(exact location TBD — see [01-project-setup.md](01-project-setup.md)).

---

## What Gobra does (the pipeline)

```
Annotated .go/.gobra files
  └─ Gobrafier        preprocess .go files (strip/rewrite ghost syntax)
  └─ Parser           go/parser + annotation mini-parser → frontend AST
  └─ Type Checker     resolve types, check specs → TypeInfo
  └─ Desugarer        frontend AST → internal AST (eliminate sugar)
  └─ Transforms       constant propagation, overflow checks, termination
  └─ Translator       internal AST → Silver IR (Go structs)
  └─ gRPC Backend     Silver Go structs → Protobuf → SilverServer gRPC → Silicon → VerificationResult
  └─ Reporter         map Silicon errors back to Go source positions → output
```

The verification backend (Viper/Silver/Silicon/Carbon) is **not being rewritten** — it is
used as-is via gRPC (an out-of-process `SilverServer` subprocess). Only the pipeline above is
being rewritten in Go.

---

## Repository layout (current and future)

```
viperproject/gobra/          ← existing repo, self-hosting branch
  gobra-go/                  ← NEW: Go implementation lives here during development
    go.mod                   ← module: github.com/viperproject/gobra
    cmd/gobra/               ← CLI entry point
    internal/                ← all pipeline stages
    tests/testdata/          ← symlinks to regression tests and stubs (see below)
  src/                       ← existing Scala implementation (reference; deleted at cut-over)
  viperserver/               ← git submodule: Silicon/Carbon JARs for gRPC subprocess (kept permanently)
  .plans/                    ← this planning directory
```

Test data is accessible from `gobra-go/` via relative paths or symlinks:
- Regression tests: `../../src/test/resources/regressions/`
- Built-in stubs: `../../src/main/resources/`

**Endgame (cut-over):** When self-hosting is achieved, delete the Scala source and promote
`gobra-go/` to the repo root. See D8 in [DECISIONS.md](DECISIONS.md) for the full cut-over
step list.

## Key decisions (summary)

All decisions are fully documented with rationale in [DECISIONS.md](DECISIONS.md). Summary:

| # | Decision | Choice |
|---|----------|--------|
| D1 | Scope | Rewrite Gobra only; Viper/Silver/Silicon/Carbon unchanged |
| D2 | Backend interface | Out-of-process gRPC subprocess (`SilverServer` embedded JAR) — see DECISIONS.md D2 |
| D3 | Go parser | `go/parser` stdlib + custom recursive-descent annotation mini-parser |
| D4 | Annotation syntax | **Resolved** — keep `//@ ...` unchanged (see 02) |
| D5 | Feature scope | Full parity with pinned Scala Gobra commit, built incrementally |
| D6 | Testing | Port regression suite; Scala Gobra is the oracle |
| D7 | Team/timeline | Solo, no hard deadline |
| D8 | Code location | `gobra-go/` subdirectory; promoted to root at cut-over |
| D9 | Frontend AST | Embed `go/ast` nodes; add Gobra-specific types alongside |
| D10 | Frontend visitor | Companion wrapper structs for go/ast nodes Gobra extends; single Visitor interface |

**D4 (annotation syntax) is resolved — keep `//@ ...`. Frontend work may begin.**

---

## Work breakdown structure

41 plan files, organized in 9 groups. See [00-overview.md](00-overview.md) for the full
dependency table and list of currently unblocked work items.

Two plans in Group 4 (Silver IR & gRPC Backend) address parallelism:
- [16b-silver-chopper.md](16b-silver-chopper.md) — Go port of the Silver Chopper; splits
  a `*silver.Program` into sub-programs for parallel verification; no subprocess dependency
- [17b-parallel-workers.md](17b-parallel-workers.md) — expands the goroutine worker pool from
  N=1 to N workers; enables true parallel verification of chopped sub-programs

**To find what you can work on next:** open `00-overview.md` and find plan files whose
dependencies are all marked complete.

**First tasks after project setup (01) and annotation syntax decision (02):**
These four are independent and can be done in any order:
- [03-frontend-ast.md](03-frontend-ast.md) — define Go types for the frontend AST
- [11-internal-ast.md](11-internal-ast.md) — define Go types for the internal AST
- [14-silver-ast.md](14-silver-ast.md) — define Go types for the Silver IR
- [15-jni-setup.md](15-jni-setup.md) — SilverServer subprocess lifecycle

---

## Current status

**Branch:** `self-hosting`

**Completed:** Planning only. No Go code has been written yet.

**Next action:** Begin 01 (project setup). D4 is resolved; 03, 05, and frontend work are unblocked.

---

## Repository layout (current Scala Gobra — for reference)

```
src/main/scala/viper/gobra/
  frontend/         Parser, Gobrafier, PackageResolver, Info (type checker), Desugar
  ast/
    frontend/       Frontend AST node types
    internal/       Internal AST node types + transforms
  translator/       MainTranslator, Context, all encoding modules
  backend/          ViperBackends, Silicon, Carbon, ViperServer wrappers
  reporting/        Reporter, error formatting
src/test/resources/regressions/   ← regression test corpus (port to Go-Gobra)
src/main/resources/               ← built-in Go stdlib stubs (.gobra files)
viperserver/                      ← git submodule: ViperServer + Silicon + Carbon + Silver
```

---

## Prerequisites for running Go-Gobra (once built)

- Go 1.21+ (`CGO_ENABLED=0` builds supported — no CGo required)
- Java 11+ 64-bit (`JAVA_HOME` set — needed to run the `SilverServer` subprocess JAR)
- Z3 4.8.7+ 64-bit (`Z3_EXE` set)
- SilverServer fat JAR — embedded in the Go binary via `//go:embed`; no external env var required. Override: set `SILVERSERVER_JAR` to a local JAR path for development/testing without rebuilding.
- Optional (Carbon backend, deferred — see D12): Boogie (`BOOGIE_EXE` set)

---

## 🤖 Agent Context Handoff Protocol

This project spans many separate agent sessions. A chat transcript is not durable: it can be
summarized, truncated, or lost entirely between sessions, and a fresh agent picking up this
work has no access to a prior session's conversation — only to what's on disk. `.plans/scratchpad.md`
exists to carry the state a new agent needs to resume without re-deriving it from scratch
(current constraints, dependency status, in-progress findings, the remediation queue). Explaining
your work to the user in the chat window is expected and encouraged throughout — this protocol is
about *also* keeping the scratchpad current, not about limiting what you say.

### 1. Log incrementally, not just at the end
When you complete a sub-task, find a bug, or modify a plan file, write that delta to
`.plans/scratchpad.md` promptly rather than saving it all for a final summary. If a session ends
unexpectedly (crash, context cutoff, user interruption), whatever hasn't been written to the
scratchpad yet is effectively lost to the next agent — the chat window won't help them.

### 2. Leave the scratchpad in a resumable state
Before ending a task, make sure `.plans/scratchpad.md` reflects your final state: findings,
remediation queue status, and anything a new agent would need to know to continue. Chat output
is for the user in this session; the scratchpad is for whoever (human or agent) picks this up next.

### 3. Process the Remediation Queue in severity order
Work through the `Remediation Queue` in `.plans/scratchpad.md` top-down (critical global blockers
first) rather than jumping to low-severity items first — a resumed session should find the most
important work already handled, not skipped over in favor of easier items.

### 4. Execute the queue; don't just report on it
When you're instructed to work the Remediation Queue, fix each item directly rather than coming
back to the user with a summary of what you found or a proposed plan for what to do about it.
"Here's what's wrong and here's how I'd fix it, want me to proceed?" is the failure mode this
guards against — for routine, in-scope queue items, do the fix and narrate what you did, don't
stop to ask permission first. This doesn't override the harness's normal judgment about pausing
for genuinely destructive or ambiguous actions; it's specifically about not stalling on ordinary,
already-authorized remediation work.

