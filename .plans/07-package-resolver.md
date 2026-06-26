# 07 — Package Resolver

## Objective

Implement multi-package resolution: given a set of entry-point files or package paths, locate
and load all transitively-imported packages (including Gobra ghost packages and built-in stubs),
producing a complete set of parsed frontend ASTs ready for type checking.

## Scope

**In scope:**
- Resolve import paths to filesystem directories using Go module conventions (`go list`, GOPATH,
  or module-aware resolution)
- Load and parse each resolved package (invoking 06 + 04 + 05 for each file)
- Handle Gobra-specific imports (ghost packages, built-in stub packages from
  `src/main/resources/`)
- Detect and report import cycles
- Support the `--include` / `--exclude` flags from the current Gobra CLI

**Out of scope:**
- Type checking the resolved packages (08–10)
- Encoding built-in stubs (31-encoding-builtins.md)

## Dependencies

- [04-go-parser.md](04-go-parser.md) — parse each package's files
- [06-gobrafier.md](06-gobrafier.md) — preprocess `.go` files before parsing

## Reference: Current Gobra

- `src/main/scala/viper/gobra/frontend/PackageResolver.scala` — canonical implementation
- `src/main/scala/viper/gobra/frontend/Source.scala` — source file abstraction
- `src/main/resources/` — built-in stub packages (these must be bundled with Go-Gobra)

## Key Implementation Notes

- **`golang.org/x/tools/go/packages`**: Use this library for module-aware package loading
  rather than reimplementing module resolution. Note: it invokes `go list` under the hood,
  which requires a Go toolchain to be installed at runtime (`go` in `$PATH`). This is
  acceptable for a development-time verifier. Document this runtime requirement prominently:
  "Go-Gobra requires a Go toolchain at runtime for package resolution (`go list`)." Fail fast
  with a clear message if `go` is not found in `$PATH`. Note this also means Go-Gobra cannot
  be used in environments where only a pre-compiled binary is deployed without a Go toolchain
  (e.g., some CI containers) — document this as a known limitation.
- **Topological ordering**: The resolver must produce packages in dependency order (a
  topological sort of the import graph). The type checker (10) requires that all imported
  packages are type-checked before the importing package. Detect and report import cycles as
  an error; do not proceed to type checking if a cycle exists.
- **Error accumulation**: If one package fails to parse or resolve, record the error and
  continue resolving other packages where possible. Report all errors at the end rather than
  stopping at the first failure.
- Built-in stubs (Go stdlib ghost specs) ship as embedded resources in the Go binary using
  `//go:embed`; they are parsed the same way as user packages.
- The resolver produces a `PackageInfo` list in topological order: each entry is
  `{ImportPath string, Files []*frontend.PFile, Deps []string}`.

## Deliverables

- `internal/frontend/packageresolver.go`
- `PackageInfo` type and topologically-sorted `[]*PackageInfo` result
- Embedded built-in stub files (`internal/frontend/stubs/` with `//go:embed`)
- Tests: resolve a multi-package example from `src/test/resources/regressions/` and verify
  the correct set of files is loaded in dependency order

## Open Questions

- Should package resolution be lazy (resolve on demand) or eager (resolve all transitively at
  startup)? Eager is simpler and matches the current Gobra behavior.
- How to handle packages that are only partially annotated (some files have specs, others don't)?
