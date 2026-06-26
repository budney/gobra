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
- **Use `go/types` for standard Go (resolved — see D9 in DECISIONS.md)**: Because plan 03
  commits to embedding `go/ast` nodes, `go/types.Check` can be called directly on the
  underlying `*ast.File`. This handles all standard Go type rules. Gobra-specific checks
  (ghost types, spec expression types, ADTs, permissions) are layered on top as a second pass.
- **Two-tier type system**: `go/types` produces a `*types.Info` side table for Go constructs.
  Gobra maintains a parallel `GhostTypeInfo` for ghost/spec-only constructs. Both are combined
  into the unified `TypeInfo` output consumed by the desugarer.
- **`go/types` ↔ package resolver coupling**: `go/types.Check` requires an `types.Importer`
  to resolve imported packages. This importer must be wired to the package resolver (07), which
  in turn must supply already-type-checked `*types.Package` objects for imports. The result is
  a topological ordering constraint: dependencies must be type-checked before dependents (handled
  in plan 10).
- **Error accumulation**: Collect all type errors rather than stopping at the first. Use a
  `[]Diagnostic` accumulator passed through the checker; continue checking after an error where
  doing so produces meaningful additional diagnostics. Abort (do not proceed to desugaring) if
  any errors are present.
- **Side-table for type info**: Attach type information via a `map[ast.Node]types.Type`
  side table (pointer identity as key), not via fields on AST nodes. This keeps AST nodes
  immutable and decouples type info from the AST definition.

## Deliverables

- `internal/info/typeinfo.go` — `TypeInfo` interface (combines `*types.Info` + `GhostTypeInfo`)
- `internal/info/checker.go` — `Check(pkg *frontend.PPackage, importer types.Importer) (*TypeInfo, []Diagnostic)`
- Symbol table with scope chain for ghost/spec constructs (Go symbols are handled by `go/types`)
- Tests: type-check a selection of regression test files and verify reported errors match
  `//@ expectedError` annotations

## Open Questions

- How to handle Gobra ghost types that have no `go/types` representation (e.g., `seq[T]`,
  `set[T]`, ADT types)? Define a parallel `GhostType` interface extending `types.Type` so
  they can coexist in the same type-info map.
