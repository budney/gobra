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

## Verification Specifications (C9)

Plan 31's primary deliverable is a static file-copy operation (stub files embedded at compile time) rather than runtime pipeline logic. C9 applies to the embed roundtrip and integration contract.

**Embed roundtrip contract**: The embedded bytes for every stub file decode to valid UTF-8 Go source. No file is truncated or corrupted during the `//go:embed` operation.

```go
// At init time, for every embedded stub file f:
//@ invariant len(stubFS[f]) > 0
//@ invariant utf8.Valid(stubFS[f])
//@ invariant !bytes.Contains(stubFS[f], []byte{0x00})  // no null bytes in Go source
```

**Stub-resolution contract**: When the custom importer (plan 10) resolves an import path that matches an embedded stub, it must return the stub package, not the real stdlib package. This is a correctness invariant — serving a wrong package silently unsounds all proofs that rely on stub contracts.

```go
// Importer.Import postcondition for stub paths:
//@ requires isStubPath(path)   // path has a matching file in internal/frontend/stubs/
//@ ensures  pkg != nil && i.isFromStub(pkg, path)
//@ ensures  err == nil
```

**Ghost predicate ownership**:

- `isStubPath` — defined in `internal/frontend/stubs/stubs.go` (owned by this plan, plan 31).
  Pure function over the embedded filesystem; needs no receiver state.
- `isFromStub` — defined as a ghost method on `*gobImporter` in `internal/info/importer.go`
  (owned by plan 10). Plan 31 consumes it in the stub-resolution postcondition below.

`isStubPath` is a pure function over the embedded filesystem:

```go
// isStubPath reports whether the given import path has a corresponding embedded stub file.
// Defined in internal/frontend/stubs/stubs.go (plan 31).
//
//@ pure
//@ decreases
//@ func isStubPath(path string) bool {
//@     return stubFS.Exists(path + ".gobra") || stubFS.Exists(path + "/stub.gobra")
//@ }
```

`isFromStub` cannot be implemented as a ghost field on `*types.Package` — that is a stdlib
type not owned by this codebase, and Gobra cannot annotate ghost fields on foreign types.
Instead, the importer (plan 10) owns a ghost map field tracking which packages it loaded
from stubs. See plan 10 for the full definition; the relevant signatures are:

```go
// In internal/info/importer.go (plan 10):
//@ ghost field stubLoaded map[*types.Package]bool  // on *gobImporter; keyed by package pointer

// isFromStub is a pure ghost method on *gobImporter (defined in plan 10).
//@ pure
//@ requires acc(i.stubLoaded, _)
//@ decreases
//@ func (i *gobImporter) isFromStub(pkg *types.Package, path string) bool
```

The stub-resolution postcondition on `Import` (annotated in plan 10's C9 section):

```go
//@ requires isStubPath(path)
//@ ensures  pkg != nil && i.isFromStub(pkg, path)
//@ ensures  err == nil
```

The importer sets `i.stubLoaded[pkg] = true` (ghost statement) for every package it
creates from embedded stub bytes, and never sets it for packages loaded from disk. This
makes `isFromStub` decidable and falsifiable: a `*types.Package` built from disk for a
stub path (e.g., by accidentally falling through to the real stdlib resolver) will not
be present in `i.stubLoaded`, so the postcondition will fail.

**No-new-stub contract**: Plan 31 does not write new stub content — it only ports existing files from `src/main/resources/`. If a stub file is absent from `internal/frontend/stubs/` that was present in `src/main/resources/`, that is a gap requiring a new stub (out of scope for plan 31). Termination: the porting loop iterates over a finite, known file set.

```go
//@ decreases len(remainingFiles)
```
