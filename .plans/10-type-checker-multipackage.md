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

## Deliverables

- `ExternalTypeInfo` interface and implementation
- Integration with 08's symbol table to expose imported symbols
- Tests: multi-package regression tests from `src/test/resources/regressions/`

## Open Questions

- How to handle incremental verification: if package A imports package B, and B has already
  been verified, should we re-type-check B or load its type info from a cache?
  Start with always re-checking; caching is an optimization for later.
