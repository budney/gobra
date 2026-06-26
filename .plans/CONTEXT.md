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

## Key decisions (summary)

All decisions are fully documented with rationale in [DECISIONS.md](DECISIONS.md). Summary:

| # | Decision | Choice |
|---|----------|--------|
| D1 | Scope | Rewrite Gobra only; Viper/Silver/Silicon/Carbon unchanged |
| D2 | Backend interface | JNI via [jnigi](https://github.com/timob/jnigi) — embed JVM in-process |
| D3 | Go parser | `go/parser` stdlib + custom recursive-descent annotation mini-parser |
| D4 | Annotation syntax | **Pending** — recommendation is keep `//@ ...` unchanged |
| D5 | Feature scope | Full parity with Scala Gobra, built incrementally |
| D6 | Testing | Port regression suite; Scala Gobra is the oracle |
| D7 | Team/timeline | Solo, no hard deadline |

**D4 (annotation syntax) must be resolved before any frontend work begins.**

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

**Next action:** Resolve D4 (annotation syntax), then begin 01 (project setup).

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
