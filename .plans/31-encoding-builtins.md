# 31 — Encoding: Built-in Stubs

## Objective

Port the Go standard library ghost stubs (pre-verified package specifications) from the Scala
Gobra's resource directory into the Go-Gobra codebase. These stubs allow users to verify
programs that call standard library functions by providing their contracts without requiring
the full stdlib source to be verified.

## Scope

**In scope:**
- Port all stub files from `src/main/resources/` (`.gobra` stub files for `sync`, `io`,
  `fmt`, `os`, `math`, `sort`, `strings`, `strconv`, etc.)
- Embed them in the Go binary using `//go:embed`
- Wire them into the package resolver (07) so they are found when user code imports the
  corresponding standard packages
- Verify that the ported stubs are accepted by Go-Gobra (end-to-end test)

**Out of scope:**
- Writing new stubs (that's user-community work after the verifier is functional)
- Verifying the stubs themselves (they are trusted by definition)

## Dependencies

- [07-package-resolver.md](07-package-resolver.md) — the `//go:embed`-based stub-resolution
  framework (custom importer, embedded `stubs/` directory, virtual package loading) must exist
  before plan 31's stub files are loadable at runtime. Plan 07 provides this mechanism;
  plan 31 provides the stub file contents.
- [10-type-checker-multipackage.md](10-type-checker-multipackage.md) — the custom
  `types.Importer` (owned by plan 10, exported from `internal/info/importer.go`) performs
  stub-directory-first resolution and must be complete before stub packages can be loaded
  through plan 07's resolver. Plan 31 is blocked until both plan 07 and plan 10 are complete.
- [27-encoding-methods.md](27-encoding-methods.md) — stubs define function contracts; the
  method encoding must be complete before stubs can be loaded and used

## Reference: Current Gobra

- `src/main/resources/` — all existing stub files; these are the direct source
- `src/main/scala/viper/gobra/frontend/PackageResolver.scala` — how stubs are located and
  injected as virtual packages

## Deliverables

- `internal/frontend/stubs/` — all ported stub files (`.gobra` format, unchanged)
- `//go:embed` declaration to bundle them in the binary
- Integration with package resolver to serve them as virtual packages
- Tests: verify a program that calls `sync.Mutex.Lock` using the stub

## Resolved Questions

**Stub compatibility (resolved):** D4 (plan 02) keeps the `//@ ...` annotation syntax
unchanged. The existing stub files in `src/main/resources/` use `//@ ...` syntax exclusively.
No stub updates are needed for syntax reasons. The stubs can be copied as-is into
`internal/frontend/stubs/`.
