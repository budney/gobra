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

- `ExternalTypeInfo` interface and implementation
- Custom `types.Importer` bridging the package resolver to `go/types.Check`
- Integration with 08's symbol table to expose imported symbols (both Go and ghost)
- Tests: multi-package regression tests from `src/test/resources/regressions/`

## Open Questions

- **Caching**: Once a package is type-checked, its `ExternalTypeInfo` could be cached to disk
  (keyed by content hash) to avoid re-checking on subsequent runs. Start with no caching —
  always re-check — but design the `ExternalTypeInfo` interface to be serializable so caching
  can be added later without breaking the API. Mark the cache insertion point with a `// TODO: cache`
  comment in the implementation.
