# 01 — Project Setup

## Objective

Bootstrap the Go-Gobra repository: Go module, directory layout, build tooling, license headers,
and CI. This is the prerequisite for all other work.

## Repository Structure (resolved — see D8 in DECISIONS.md)

The Go implementation lives in a `gobra-go/` subdirectory of the existing Gobra repository,
on the `self-hosting` branch. This gives immediate access to:
- `src/test/resources/regressions/` — the regression test corpus
- `src/main/resources/` — the built-in Go stdlib stubs
- `viperserver/` submodule — Silicon/Carbon JARs needed for backend subprocess

**Endgame:** Once Go-Gobra reaches full parity and self-hosting, the Scala source is deleted
and `gobra-go/` is promoted to the repo root in a cut-over commit. The `viperserver/` submodule stays (still needed for the backend subprocess).

**Module path:** `github.com/viperproject/gobra` — same as the Scala project, since this is
a replacement, not a parallel tool. The subdirectory path avoids conflict during development.

## Scope

**In scope:**
- Create `gobra-go/` directory at the repo root on the `self-hosting` branch
- `go mod init github.com/viperproject/gobra` inside `gobra-go/`
- Top-level directory skeleton mirroring the logical pipeline stages
- Makefile with common targets (build, test, lint)
- License header tooling: extend `.github/license-check/config.json` to cover `gobra-go/**/*.go` (MPL-2.0); verify with `npx github:viperproject/check-license-header#v1 check --config .github/license-check/config.json --strict`
- GitHub Actions CI stub for the Go build (separate job from the existing Scala CI), triggered on push and pull_request events targeting the `self-hosting` branch
- `.gitignore` additions for Go build artifacts and JVM temp files

**Out of scope:**
- Any actual Go source beyond skeleton `package` declarations
- Subprocess configuration (that's [15-jni-setup.md](15-jni-setup.md))

## Dependencies

None — this is the root of the dependency graph.

## Reference: Current Gobra

- `build.sbt` — understand the module structure and dependency versions
- `.github/` — existing CI workflows and license check config
- `src/main/scala/viper/gobra/` — top-level package layout to mirror in Go

## Directory Layout

```
gobra-go/                         ← Go module root (go.mod here)
  cmd/gobra/                      ← main package, CLI entry point
  internal/
    frontend/                     ← parser, annotation parser, gobrafier, package resolver
    info/                         ← type checker
    ast/
      frontend/                   ← frontend AST node types
      internal/                   ← internal AST node types
    desugar/                      ← desugarer
    transform/                    ← internal transforms
    translator/                   ← translator core + encodings
    silver/                       ← Silver IR Go types + printer
    backend/                      ← subprocess setup, Silicon/Carbon callers
    reporting/                    ← error reporter
    diagnostic/                   ← shared Diagnostic type (source owned by plan 32a)
  tests/
    testdata/regressions/ → ../../src/test/resources/regressions/  (symlink or copy)
    testdata/stubs/       → ../../src/main/resources/               (symlink or copy)
  KNOWN_LIMITATIONS.md                ← empty file; plans 20, 27, 28 append to it
```

Note: there is no `pkg/` directory. This is a replacement tool, not a library — nothing should
be exported from the Go module. If a programmatic Go embedding API is needed in the future,
add `pkg/` at that time.

Paths relative to the existing repo root:
```
viperproject/gobra/
  gobra-go/          ← NEW: Go implementation (this project)
  src/               ← existing Scala implementation (reference; deleted at cut-over)
  viperserver/       ← existing submodule: stays permanently (backend JARs)
  .plans/            ← this planning directory
```

## Deliverables

- `gobra-go/` directory created on the `self-hosting` branch with the skeleton layout above
- `go.mod` with module path `github.com/viperproject/gobra` and minimum Go version `go 1.21`
- `go build ./...` succeeds (even with only skeleton packages)
- `go vet ./...` passes
- `gobra-go/KNOWN_LIMITATIONS.md` created as an empty file (plans 20, 27, 28 append to it)
- `.github/license-check/config.json` extended to cover `gobra-go/**/*.go`; license header check passes (MPL-2.0 headers on all `.go` files)
- CI job `go-build` defined in `.github/workflows/`, triggered on push and pull_request to `self-hosting`, runs alongside the existing Scala CI job
- Test data accessible from `gobra-go/tests/testdata/` (symlinks or relative paths)

## Open Questions

- Symlinks vs. copies for test data: symlinks are cleaner but may cause issues on Windows CI
  runners. Use symlinks; add a note in CI to enable symlink support if needed.
- Should `gobra-go/` have its own `README.md` explaining it is the Go rewrite in progress?
  Yes — add a minimal one pointing to `CONTEXT.md` and `DECISIONS.md` in `.plans/`.

## Verification Specifications (C9)

C9: N/A — This plan delivers repository skeleton, go.mod, CI configuration, Makefile, and
license header tooling. No Go functions with verifiable pre/postconditions are implemented.
