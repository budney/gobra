# 08 — Type Checker Core

## Objective

Implement the core type checker: resolve identifiers, infer types for expressions, manage
scopes and symbol tables, and annotate the frontend AST with type information. This is the
largest single unit of work in the frontend.

## Scope

**In scope:**
- Symbol table construction (packages, files, blocks, function scopes)
- Name resolution (identifier → declaration)
- Type inference and checking for all Go expression forms
- Assignability, comparability, and conversion rules per the Go spec
- Method set resolution (value vs. pointer receivers)
- Struct field access and embedding resolution
- Type assertion and type switch checking
- Constant expression evaluation
- Error accumulation (report all errors, not just the first)

**Out of scope:**
- Type checking specification expressions (09-type-checker-specs.md)
- Cross-package type information (10-type-checker-multipackage.md)
- Ghost type checking (09 handles this)

## Dependencies

- [03-frontend-ast.md](03-frontend-ast.md) — AST nodes to annotate
- [04-go-parser.md](04-go-parser.md) — provides the AST to type-check
- [05-annotation-parser.md](05-annotation-parser.md) — annotation nodes are type-checked in 09,
  but 08 must leave hooks for them

## Reference: Current Gobra

- `src/main/scala/viper/gobra/frontend/info/` — the entire `info` package
  - `ExternalTypeInfo.scala` — the public interface exposed to the rest of the pipeline
  - `implementation/TypeInfoImpl.scala` — main implementation
  - `implementation/property/` — individual property checkers (assignability, convertibility, etc.)
  - `implementation/resolution/` — name and type resolution
  - `implementation/typing/` — expression typing
- Note: current Gobra's type checker is built on Kiama's attribute grammar framework; the Go
  rewrite should use a more direct imperative approach (two-pass: collect declarations, then check)

## Key Implementation Notes

- **Two-pass approach**: Pass 1 collects all declarations in scope (needed for forward references
  and mutually recursive types/functions). Pass 2 resolves and checks all uses.
- Consider using `go/types` from the stdlib as a foundation for checking standard Go constructs,
  then layering Gobra-specific checks on top. This avoids reimplementing the entire Go type
  system from scratch and reduces risk of spec divergence.
- The output is a `TypeInfo` object that maps AST nodes to their types and declarations;
  this is queried by the desugarer and translator.

## Deliverables

- `internal/info/typeinfo.go` — `TypeInfo` interface
- `internal/info/checker.go` — `Check(pkg *frontend.PPackage, ...) (*TypeInfo, []error)`
- Symbol table with scope chain
- Tests: type-check a selection of regression test files and verify reported errors match
  `//@ expectedError` annotations

## Open Questions

- Use `go/types` as a sub-component (call `go/types.Check` on the Go subset, then extend for
  Gobra-specific constructs) or implement the full type system from scratch?
  Using `go/types` is strongly recommended to avoid reimplementing a complex, spec-mandated system.
- How to attach type information to AST nodes? Options: map from node identity (pointer) to
  type; or add type fields to AST structs (requires mutable AST). A side-table map is cleaner.
