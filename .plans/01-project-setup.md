# 01 — Project Setup

## Objective

Bootstrap the Go-Gobra repository: Go module, directory layout, build tooling, license headers,
and CI. This is the prerequisite for all other work.

## Scope

**In scope:**
- `go mod init` for the new module (e.g. `github.com/viperproject/gobra`)
- Top-level directory skeleton mirroring the logical pipeline stages
- Makefile or `just`-file with common targets (build, test, lint)
- License header tooling (reuse the viperproject MPL-2.0 setup)
- GitHub Actions CI stub (build + test on push)
- `.gitignore` for Go build artifacts and JVM temp files
- Decision on whether this lives in the existing repo (new subdirectory or branch) or a new repo

**Out of scope:**
- Any actual Go source beyond skeleton `package` declarations
- CGo / JNI configuration (that's 15-jni-setup.md)

## Dependencies

None — this is the root of the dependency graph.

## Reference: Current Gobra

- `build.sbt` — understand the module structure and dependency versions
- `.github/` — existing CI workflows and license check config
- `src/main/scala/viper/gobra/` — top-level package layout to mirror in Go

## Proposed Directory Layout

```
gobra-go/
  cmd/gobra/        ← main package, CLI entry point
  internal/
    frontend/       ← parser, annotation parser, gobrafier, package resolver
    info/           ← type checker
    ast/
      frontend/     ← frontend AST node types
      internal/     ← internal AST node types
    desugar/        ← desugarer
    transform/      ← internal transforms
    translator/     ← translator + encodings
    silver/         ← Silver IR Go types
    backend/        ← JNI setup, Silicon/Carbon callers
    reporting/      ← error reporter
  pkg/              ← any exported utilities
```

## Deliverables

- Repository compiles with `go build ./...` (even if empty)
- `go vet ./...` and `golint` pass
- License header check passes
- CI runs on push

## Open Questions

- New repo vs. subdirectory/branch of the existing Gobra repo?
- Module path: `github.com/viperproject/gobra` (same name, new impl) or a distinct path?
