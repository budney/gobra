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

- Go's own `golang.org/x/tools/go/packages` library can drive module-aware package loading;
  consider using it rather than reimplementing module resolution from scratch
- Built-in stubs (Go stdlib ghost specs) ship as embedded resources in the Go binary using
  `//go:embed`; they are parsed the same way as user packages
- The resolver produces a `PackageInfo` map: import path → parsed AST set

## Deliverables

- `internal/frontend/packageresolver.go`
- Embedded built-in stub files (`internal/frontend/stubs/` with `//go:embed`)
- Tests: resolve a multi-package example from `src/test/resources/regressions/` and verify
  the correct set of files is loaded

## Open Questions

- Should package resolution be lazy (resolve on demand) or eager (resolve all transitively at
  startup)? Eager is simpler and matches the current Gobra behavior.
- How to handle packages that are only partially annotated (some files have specs, others don't)?
