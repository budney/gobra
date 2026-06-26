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

## Open Questions

- Are all existing stubs syntactically compatible with the Go-Gobra parser, or will some
  need updates if the annotation syntax decision (02) results in any changes?
