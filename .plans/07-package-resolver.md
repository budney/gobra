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
- **Bootstrap / self-hosting implication**: Because Go-Gobra calls `go list` at runtime, a
  pre-built Go-Gobra binary cannot verify Go programs on a system where the Go toolchain is
  not installed. This means the self-hosting CI job (plan 37) requires Go to be installed
  on the CI runner — not just the Go-Gobra binary. This is expected (Go-Gobra is a
  development-time tool, not a production runtime), but document it in the CI workflow so
  the requirement is explicit. The bootstrap sequence for plan 37 is therefore:
  build Go-Gobra with Go → run Go-Gobra (requires Go in PATH) → verify Go-Gobra source.
- **Topological ordering**: The resolver must produce packages in dependency order (a
  topological sort of the import graph). The type checker (10) requires that all imported
  packages are type-checked before the importing package. Detect and report import cycles as
  an error; do not proceed to type checking if a cycle exists.
- **Error accumulation**: If one package fails to parse or resolve, record the error and
  continue resolving other packages where possible. Report all errors at the end rather than
  stopping at the first failure.
- **Stub package resolution (resolved):** In the Scala Gobra, stub packages use *standard Go
  import paths* (e.g., `"sync"`, `"fmt"`) and shadow the real stdlib: the resolver searches
  `stubs/` directories before GOPATH, so `import "sync"` resolves to `stubs/sync/*.gobra`
  rather than the real Go `sync` package.

  The Go-Gobra equivalent requires a custom `types.Importer` that checks the embedded stubs
  directory *first* for every import path. If `stubs/<importPath>/` contains any `.gobra`
  files, it loads and returns those (as a virtual `*types.Package` built from the stub's
  exported declarations); otherwise it falls through to `go/packages` for real Go packages.

  **Ownership: plan 10 implements and owns this custom importer** as a named deliverable. Plan
  07 depends on it at runtime (the package resolver calls `go/types.Check` with this importer),
  but does not implement it. Plan 10 must be completed before plan 07's multi-package path
  can be exercised end-to-end. The importer is shared between plan 07 (for resolution) and
  plan 10 (for type-checking); plan 10 exports it from `internal/info/importer.go`.

  The `BuiltInImport` package (Gobra's own built-in declarations, separate from stdlib stubs)
  is resolved via a special sentinel import path `"gobra/builtin"` that always maps to the
  embedded `builtin/` directory — never to a real Go package.

- Built-in stubs (Go stdlib ghost specs) ship as embedded resources in the Go binary using
  `//go:embed`; they are parsed the same way as user packages.
- The resolver produces a `PackageInfo` list in topological order: each entry is
  `{ImportPath string, Files []*frontend.PFile, Deps []string}`.

## Deliverables

- `internal/frontend/packageresolver.go` — main entry point:
  ```go
  // Resolve loads, preprocesses, and parses all packages transitively imported
  // by the given entry-point paths (file paths or import paths). Returns packages
  // in topological order (dependencies before dependents) and all accumulated
  // diagnostics. A non-empty []Diagnostic does not prevent returning a partial
  // result; callers (plan 33 pipeline) abort if any diagnostics are present.
  func Resolve(inputs []string, cfg *ResolverConfig) ([]*PackageInfo, []Diagnostic)
  ```
  `ResolverConfig` includes: stub directory (embedded), Go module root, any `--include`/
  `--exclude` patterns, and a reference to the type-checked package cache for plan 10.
- `PackageInfo` type: `{ImportPath string, Files []*frontend.PFile, Deps []string}`
- `ResolverConfig` type (see above)
- Embedded built-in stub files (`internal/frontend/stubs/` with `//go:embed`)
- Coordination note: for each file, `Resolve` runs a **4-step sequence**:
  1. `Gobrafy(rawBytes, filename)` (plan 06) → preprocessed `[]byte` (no temp file written)
  2. `ParseFile(filename, preprocessedBytes)` (plan 04) → `(*PFile, map[*PBlockStmt][]RawAnnotation, diags)` — `PBlockStmt` nodes contain Go statements only at this point
  3. For each `RawAnnotation` in the returned map: `ParseAnnotation(raw.Text, raw.Pos)` (plan 05) → ghost statement nodes
  4. `MergeGhostStatements(pfile, ghostNodes)` → interleaves ghost statement nodes into each `PBlockStmt.Stmts` by `Pos()`, completing the block's statement list in source order

  `MergeGhostStatements` is a pure function in `internal/frontend/packageresolver.go`; it sorts a merged slice of `PGoStmt` and ghost nodes by their `Pos()` value.

  File-scope `//@ ` comments (those not within any block, e.g. ghost ADT/predicate/func declarations produced by the Gobrafier) are identified by position (no enclosing `*PBlockStmt` in the map) and attached to `PFile.GhostDecls` rather than any `PBlockStmt`.
- Tests: resolve a multi-package example from `src/test/resources/regressions/` and verify
  the correct set of files is loaded in dependency order

## Resolved Questions

**Lazy vs. eager (resolved):** Eager — load and parse all transitively imported packages at
startup before type checking begins. This matches the Scala Gobra behavior and is simpler to
implement: one linear phase of loading, one phase of type checking in topological order.
If profiling later shows that loading unused packages is a bottleneck (e.g., in `--parseOnly`
or `--typeCheckOnly` mode), add lazy loading as an optimization at that time.

**Partially-annotated packages:** Treat all files in a package uniformly — parse and type-check
them all together, regardless of whether individual files contain Gobra annotations. Files with
no `//@ ` comments simply produce no ghost AST nodes. This is the simplest approach and
matches the current Gobra behavior.
