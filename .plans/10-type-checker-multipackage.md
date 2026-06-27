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
  `types.Importer`. Implement a custom importer that serves `*types.Package` objects from
  already-completed packages in this session, falling back to the stdlib importer for
  standard library packages not being verified.
- **Ghost info across packages**: `ExternalTypeInfo` must expose not just standard Go
  exported symbols but also exported ghost declarations (predicates, ghost functions, ADT
  types). These are not visible to `go/types` and must be handled separately.

## Deliverables

- `ExternalTypeInfo` interface and implementation, including `Serialize() ([]byte, error)`
  and the package-level `DeserializeExternalTypeInfo` + `ErrStaleCacheEntry` (see Resolved
  Questions above for signatures)
- Custom `types.Importer` bridging the package resolver to `go/types.Check`
- Integration with 08's symbol table to expose imported symbols (both Go and ghost)
- Commented-out `// TODO: cache` insertion point in the type-checker after each package
  completes (see Resolved Questions)
- Tests:
  - Multi-package regression tests from `src/test/resources/regressions/`
  - Round-trip unit test: type-check a small multi-package program, serialize the
    `ExternalTypeInfo` of the dependency package, deserialize it, and verify the result is
    equal to the original (same exported symbols, same ghost declarations). This test validates
    the serialization format without requiring a live disk cache.

## Resolved Questions

**Caching (resolved):** Implement the `ExternalTypeInfo` serialization interface as a
deliverable in this plan, but defer the disk-cache implementation to a later optimization
pass. Rationale: Silicon/Z3 time dominates verification time by orders of magnitude;
type-checking time is negligible in comparison. The cache introduces asymmetric correctness
risk — a stale hit silently skips type errors — and the invalidation logic is non-trivial.
The serialization interface is the hard design question; once it exists, the cache
implementation is mechanical.

**`ExternalTypeInfo` serialization interface (add to deliverables):**

```go
// Serialize encodes the ExternalTypeInfo to a portable, versioned byte
// representation. The format is self-describing: it includes a magic number,
// a Go-Gobra format version, the Silver version string (from the ViperServer
// JAR manifest), and the SHA-256 content hashes of all source files that
// were type-checked to produce this ExternalTypeInfo.
//
// Stale entries are detected at deserialization time: if the format version,
// Silver version, or content hashes do not match, DeserializeExternalTypeInfo
// returns ErrStaleCacheEntry and the caller must re-type-check.
Serialize() ([]byte, error)

// DeserializeExternalTypeInfo reconstructs an ExternalTypeInfo from bytes
// previously produced by Serialize. Returns ErrStaleCacheEntry if the format
// version or Silver version does not match, or if any source file hash in the
// serialized data does not match the current file content.
func DeserializeExternalTypeInfo(data []byte, sourceHashes map[string][32]byte) (ExternalTypeInfo, error)

var ErrStaleCacheEntry = errors.New("cache entry is stale or from a different version")
```

The cache insertion point in the type-checker implementation is a single function call after
type-checking each package:

```go
// TODO: cache — serialize eti and write to cacheDir/<contentHash>.etcache
// eti, _ := result.ExternalTypeInfo().Serialize()
// _ = os.WriteFile(cachePath, eti, 0644)
```

Leave this as a commented-out `// TODO: cache` block. The serialization interface is tested
by a round-trip unit test (serialize → deserialize → compare), not by an end-to-end cache
test. Do not implement the disk cache until profiling shows type-checking is a bottleneck.
