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
  └─ JNI Backend      Silver Go structs → Java Silver objects → Silicon → VerificationResult
  └─ Reporter         map Silicon errors back to Go source positions → output
```

The verification backend (Viper/Silver/Silicon/Carbon) is **not being rewritten** — it is
used as-is via JNI. Only the pipeline above is being rewritten in Go.

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
  viperserver/               ← git submodule: Silicon/Carbon JARs for JNI (kept permanently)
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
| D2 | Backend interface | JNI via [jnigi](https://github.com/timob/jnigi) + thin Java helper JAR (`SilverBridge.java`) |
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

38 plan files, organized in 9 groups. See [00-overview.md](00-overview.md) for the full
dependency table and list of currently unblocked work items.

**To find what you can work on next:** open `00-overview.md` and find plan files whose
dependencies are all marked complete.

**First tasks after project setup (01) and annotation syntax decision (02):**
These four are independent and can be done in any order:
- [03-frontend-ast.md](03-frontend-ast.md) — define Go types for the frontend AST
- [11-internal-ast.md](11-internal-ast.md) — define Go types for the internal AST
- [14-silver-ast.md](14-silver-ast.md) — define Go types for the Silver IR
- [15-jni-setup.md](15-jni-setup.md) — jnigi integration and JVM lifecycle

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

- Go 1.21+ (with CGo enabled)
- Java 11+ 64-bit (`JAVA_HOME` set)
- Z3 4.8.7+ 64-bit (`Z3_EXE` set)
- ViperServer/Silicon JAR (`VIPERSERVER_JAR` set, or built via `sbt assembly` in `viperserver/`)
- Optional (Carbon backend): Boogie (`BOOGIE_EXE` set)
