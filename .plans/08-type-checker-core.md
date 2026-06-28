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

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type returned by `Check`
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
- **Side-table for type info**: Attach type information via a `map[PNode]types.Type`
  side table (pointer identity as key), not via fields on AST nodes. This keeps AST nodes
  immutable and decouples type info from the AST definition. `PNode` (defined in plan 03)
  covers both `go/ast` nodes and Gobra ghost nodes — do not use `ast.Node` as the key type
  since ghost nodes do not implement it.

## Deliverables

- `internal/info/typeinfo.go` — `TypeInfo` **concrete exported struct** (not an interface):
  ```go
  type TypeInfo struct {
      Go    *types.Info   // populated by go/types.Check in plan 08
      Ghost GhostTypeInfo // populated by plan 09's CheckSpecs pass
  }

  // Addressable reports whether node n is addressable (can appear as operand of &).
  // Uses Go.Types[expr].Addressable() for go/ast expressions; returns false for
  // ghost nodes and non-expression nodes (they are always exclusive / value-semantic).
  // Used by the translator (plan 19) to decide ExclusiveType vs. SharedType.
  func (ti *TypeInfo) Addressable(n PNode) bool
  ```
- `internal/info/checker.go` — `Check(pkg *frontend.PPackage, importer types.Importer) (*TypeInfo, []Diagnostic)`
  Returns a `*TypeInfo` with `Go` fully populated and `Ghost` zero-valued. Plan 09's
  `CheckSpecs(pkg, info *TypeInfo) []Diagnostic` fills in `info.Ghost` in a second pass.
- Symbol table with scope chain for ghost/spec constructs (Go symbols are handled by `go/types`)
- Tests: type-check a selection of regression test files and verify reported errors match
  `//@ expectedError` annotations

## Explicitly Unsupported Constructs

The following constructs are absent from Scala Gobra's ANTLR grammar and therefore never
reached the Scala type checker or desugarer. Because Go-Gobra uses `go/parser` + `go/types`,
they surface here and must be explicitly rejected with user-facing diagnostics. Do not rely
on the downstream catch-all panic (plan 19) — that is for internal bugs, not unsupported
language features.

Add a post-`go/types` scan of the AST that checks for each of these and accumulates a
`Diagnostic` if found:

**`goto`**: Reject any `*ast.BranchStmt` with `Tok == token.GOTO`. Scala crashes with `???`
(`NotImplementedError`) on `PGoto` — this must be a clean error instead.
Error message: `"goto is not supported by Gobra"`.

**`fallthrough`**: Reject any `*ast.BranchStmt` with `Tok == token.FALLTHROUGH`.
Not in Scala Gobra's grammar at all; `go/parser` parses it without error.
Error message: `"fallthrough is not supported by Gobra"`.

**`recover()`**: Reject any `*ast.CallExpr` whose function resolves (via `go/types`) to the
built-in `recover`. Not in Scala Gobra's grammar; `go/types` resolves `recover` as a
`*types.Builtin` with `Id() == "recover"`.
Error message: `"recover is not supported by Gobra"`.

These three checks should run as a single AST walk after `go/types.Check` completes, before
the ghost type checking pass (plan 09).

## Resolved Questions

**Ghost forward references (resolved):** The Scala type checker uses Kiama's circular
attribute evaluation to handle mutually recursive ghost types lazily. The Go two-pass approach
handles this differently:

- **Pass 1 (stub registration)**: Walk all ghost declarations and register their names in the
  ghost type table as **stubs** — a `GhostType` value that records the name and kind but has
  no body yet (e.g., `ADTStub{name: "MyADT"}`).
- **Pass 2 (body resolution)**: Walk all ghost declarations again, now resolving each body.
  Because all names are already in the table as stubs, forward references and mutual recursion
  are handled: the resolver finds the stub and returns it; the stub is later filled in.

Stubs must never escape the type checker — by the time `TypeInfo` is returned, all stubs
must be fully resolved. Add an assertion at the end of Pass 2 that no `GhostType` in the
table is still a stub. If any remain, it indicates a missing declaration, which should have
been caught as a "type not found" error earlier.

This two-stub-pass approach handles all patterns the Scala checker handles, including
self-recursive ADTs (`adt Tree { Leaf{}; Node{left Tree; right Tree} }`).

**Ghost types with no `go/types` representation (resolved):** Define a `GhostType` interface
extending `types.Type` (implementing `Underlying() types.Type` and `String() string`).
Concrete implementations:

| Go-Gobra ghost type | GhostType implementation |
|---------------------|--------------------------|
| `seq[T]`            | `SeqType{Elem types.Type}` |
| `set[T]`            | `SetType{Elem types.Type}` |
| `mset[T]`           | `MSetType{Elem types.Type}` |
| `dict[K]V`          | `DictType{Key, Val types.Type}` |
| `option[T]`         | `OptionType{Elem types.Type}` |
| ADT types           | `ADTType{Name string, Constructors []ADTConstructor}` |
| permission amount   | `PermissionType{}` |
| magic wand          | `WandType{Left, Right types.Type}` |

These satisfy `types.Type` and can be stored in the same `map[PNode]types.Type` side table
alongside `go/types` results. The `GhostTypeInfo` wraps `map[PNode]GhostType` for
ghost-only constructs; combined with the `*types.Info` from `go/types.Check`, they form the
unified `TypeInfo` output. All `GhostType` implementations live in
`internal/info/ghosttypes.go`.

**Note on `PNode` key type**: `ast.Node` (the `go/ast` node interface) cannot be used as the
map key because Gobra ghost nodes (`PForall`, `PAccess`, etc.) do not implement it. Instead,
use `PNode` — a unified interface defined in plan 03 that both `go/ast` nodes and Gobra ghost
nodes implement. See plan 03 ("PNode Interface") for the definition. Map keys use pointer
identity (concrete types are always pointer types), so no custom equality is needed.
