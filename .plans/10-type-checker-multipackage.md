# 10 — Type Checker: Multi-Package

## Objective

Extend the type checker to handle cross-package type information: importing and exporting types,
declarations, and specifications across package boundaries so that multi-package programs can be
verified as a whole.

## Scope

**In scope:**
- Constructing an `ExternalTypeInfo` for each imported package (its exported declarations and specs)
- Making cross-package declarations available during name resolution in the importing package
- Handling packages that are pre-verified (trusted) vs. packages being verified together
- Ghost package imports and their exported ghost declarations

**Out of scope:**
- Single-package type checking (08, 09)
- Package resolution/loading (07)

## Dependencies

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type used by multi-package checker
- [07-package-resolver.md](07-package-resolver.md) — provides all loaded package ASTs
- [08-type-checker-core.md](08-type-checker-core.md) — the base checker to extend

## Reference: Current Gobra

- `src/main/scala/viper/gobra/frontend/info/ExternalTypeInfo.scala` — the interface for
  cross-package type information
- `src/main/scala/viper/gobra/frontend/info/implementation/TypeInfoImpl.scala` — how it
  integrates into the main type checker
- `src/main/scala/viper/gobra/frontend/PackageResolver.scala` — how packages are linked

## Key Implementation Notes

- **Topological ordering (required)**: The package resolver (07) delivers packages in
  dependency order. This checker processes them in that order: when type-checking package A,
  all packages A imports already have a completed `ExternalTypeInfo`. Never type-check a
  package before its dependencies.
- **`go/types` importer**: The `go/types.Check` call for each package requires a
  `types.Importer`. Implement a custom importer (`gobImporter`) that serves `*types.Package`
  objects from already-completed packages in this session, falling back to the stdlib importer
  for standard library packages not being verified. The importer wraps a `GobraScope` per
  imported package (see D16 in DECISIONS.md) so that cross-package ghost name resolution
  routes through `GobraScope.Lookup` rather than accessing `GhostTypeInfo` directly.
- **Ghost info across packages**: `ExternalTypeInfo` must expose not just standard Go
  exported symbols but also exported ghost declarations (predicates, ghost functions, ADT
  types). These are not visible to `go/types`; the custom importer surfaces them via a
  `GobraScope` constructed from each imported package's `PFile.GhostDecls`.

## Deliverables

- `ExternalTypeInfo` interface and implementation
- `Serialize() ([]byte, error)` and `DeserializeExternalTypeInfo` / `ErrStaleCacheEntry`
  as **stubs only** in this plan (see note below)
- Custom `types.Importer` bridging the package resolver to `go/types.Check`; exported from
  `internal/info/importer.go`. The importer must:
  - Perform stub-directory-first resolution (check embedded `stubs/<importPath>/` before
    falling through to real stdlib packages).
  - Return an error for `"unsafe"` import requests. Error message:
    `"import of package \"unsafe\" is not supported by Gobra"`.
    (Plan 07 also scans the import graph for `"unsafe"` at the resolver level; this importer
    rejection is the second line of defense for imports that reach type-checking.)
- Integration with 08's symbol table to expose imported symbols (both Go and ghost)
- Commented-out `// TODO: cache` insertion point in the type-checker after each package
  completes (see Resolved Questions)
- Tests:
  - Multi-package regression tests from `src/test/resources/regressions/`
  - Confirm that importing `"unsafe"` returns the expected error (not a panic)

**Serialization stub note:** The `Serialize()` and `DeserializeExternalTypeInfo` functions
are delivered as stubs in this plan:

```go
func (e *externalTypeInfo) Serialize() ([]byte, error) {
    return nil, errors.New("ExternalTypeInfo serialization not yet implemented")
}

func DeserializeExternalTypeInfo(data []byte, sourceHashes map[string][32]byte) (ExternalTypeInfo, error) {
    return nil, ErrStaleCacheEntry
}
```

The concrete format (magic number, version fields, SHA-256 hashes) is deferred until a disk
cache caller exists to drive the design. The `// TODO: cache` insertion point is present so
the call site is already wired; the stubs ensure the `ErrStaleCacheEntry` fallback path is
exercised from day one. Implement the real serialization format when adding the disk cache.
Do not write a round-trip unit test until the format is non-stub; a test of the stub would
only verify that `Serialize` returns an error.

## Resolved Questions

**Caching (resolved):** Defer the disk-cache implementation to a later optimization pass.
Rationale: Silicon/Z3 time dominates verification time by orders of magnitude; type-checking
time is negligible in comparison. The cache introduces asymmetric correctness risk — a stale
hit silently skips type errors — and the invalidation logic is non-trivial.

**`ExternalTypeInfo` serialization (resolved):** Deliver `Serialize()` and
`DeserializeExternalTypeInfo` as stubs in this plan (see Deliverables). The concrete wire
format — magic number, Go-Gobra version, Silver version string, SHA-256 source hashes — is
deferred until a disk-cache caller exists to drive the design. When the cache is added, update
the stubs to the real format and add a round-trip unit test (serialize → deserialize →
compare). Do not design or test the format now; a test of the current stubs would only verify
that `Serialize` returns an error.

**`// TODO: cache` insertion point:** The type-checker leaves one commented-out block after
type-checking each package:

```go
// TODO: cache — serialize eti and write to cacheDir/<contentHash>.etcache
// eti, _ := result.ExternalTypeInfo().Serialize()
// _ = os.WriteFile(cachePath, eti, 0644)
```

This is the only change needed in the type-checker to wire a future cache. Do not implement
the disk cache until profiling shows type-checking is a bottleneck.

## Verification Specifications (C9)

```go
// Pure helper — used in the idempotency postcondition below.
// Gobra does not support { stmt; expr } blocks inside postconditions;
// round-trip equality is expressed via a pure function instead.
//@ pure func serializeIdempotent(eti *externalTypeInfo) bool

// ExternalTypeInfo.Serialize idempotency:
//@ requires eti != nil
//@ ensures  err == nil ==> len(result) > 0
//@ ensures  err == nil ==> serializeIdempotent(eti)
// serializeIdempotent(eti) holds iff calling Serialize() again returns equal bytes with no error.
//@ decreases
func (eti *externalTypeInfo) Serialize() (result []byte, err error)
```

```go
// DeserializeExternalTypeInfo roundtrip postcondition:
// If Serialize produced data, DeserializeExternalTypeInfo recovers an equivalent ExternalTypeInfo.
//@ requires data != nil
//@ ensures  result != nil <==> err == nil
//@ ensures  err == ErrStaleCacheEntry ==> result == nil
//@ decreases
func DeserializeExternalTypeInfo(
    data []byte, sourceHashes map[string][32]byte,
) (result ExternalTypeInfo, err error)
```

```go
// Importer.Import mutual exclusion contract:
// Returns a non-nil *types.Package or a non-nil error — never both nil.
//@ ensures  (pkg != nil) != (err != nil)
//@ ensures  pkg != nil ==> pkg.Complete()
//@ decreases
func (imp *gobImporter) Import(path string) (pkg *types.Package, err error)
```

**Stub contract**: While `Serialize` is the stub implementation, it always returns `(nil, non-nil-error)`. The postcondition above applies to the final implementation; the stub is exempt but must not panic.

**Unsafe-import contract**: `Import("unsafe")` must return `(nil, non-nil-error)` with message `"import of package \"unsafe\" is not supported by Gobra"`. This is a hard constraint verified by the test in Deliverables.
