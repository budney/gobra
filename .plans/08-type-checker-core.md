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
  uses `go/ast` nodes directly (no wrappers), `go/types.Check` is called on `pfile.GoFile`
  without any unwrapping step. Gobra extensions are accessed via `pfile.Metadata[node]` lookups
  during the ghost type-checking pass. Gobra-specific checks (ghost types, spec expression
  types, ADTs, permissions) are layered on top as a second pass.
- **Metadata-based Gobra extension access**: Instead of type-asserting through companion
  wrapper structs (e.g., `n.(*PFunctionDecl)`), the type checker reads Gobra data via
  `pfile.Metadata[goAstNode]`. For a function declaration `fd *ast.FuncDecl`, its spec is
  `pfile.Metadata[fd].Spec` and its receiver (if any) is `pfile.Metadata[fd].Receiver`.
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
- **Two side-tables for type info**: Do not attach type information as fields on AST nodes;
  use side tables keyed by `PNode` (pointer identity). There are two distinct tables:
  1. `*types.Info` produced by `go/types.Check` — covers all standard Go nodes (expressions,
     identifiers, selections, etc.) using the stdlib's own key type (`ast.Node`).
  2. `map[PNode]GhostType` within `GhostTypeInfo` — covers Gobra ghost and spec nodes that
     have no `go/ast` counterpart.
  Both are wrapped in the exported `TypeInfo` struct (see Deliverables). Do not attempt to
  merge them into a single `map[PNode]types.Type`; `GhostType` values implement `types.Type`
  but the stdlib `*types.Info` map is not extensible by callers.

## Deliverables

- `type Diagnostic = diagnostic.Diagnostic` alias in `internal/info/checker.go`
  (per plan 00 cross-cutting convention; keeps `[]Diagnostic` signatures unqualified)
- `internal/info/checker.go` — `GobraScope` interface and `gobraScopeImpl` (see D16 in
  DECISIONS.md). Plan 08 constructs a `gobraScopeImpl` per block/file/package scope,
  populating `ghostDecls` from `PFile.GhostDecls` during Pass 1. All name lookups that need
  to cover both Go and ghost namespaces go through `GobraScope.Lookup`; no call site accesses
  `GhostTypeInfo` directly for name-by-string resolution:
  ```go
  type GobraScope interface {
      Lookup(name string) types.Object
      GoScope() *types.Scope
  }
  ```
- `internal/info/typeinfo.go` — `TypeInfo` **concrete exported struct** (not an interface):
  ```go
  type TypeInfo struct {
      Go    *types.Info   // populated by go/types.Check in plan 08
      Ghost GhostTypeInfo // populated by plan 09's CheckSpecs pass
  }
  ```
  **No `Addressable` wrapper method.** Call sites (primarily the desugarer, plan 12) call
  `ti.Go.Types[expr].Addressable()` directly. Adding a `TypeInfo.Addressable(PNode) bool`
  wrapper would be dead code — ghost nodes and non-expression nodes do not appear in
  `go/types.Info.Types`, so callers already handle the two cases separately.
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

**Reserved identifier patterns**: Plan 19's name-mangling scheme reserves two identifier
patterns for internal Silver names. If Go-Gobra source uses these patterns, the generated
Silver will have a name collision with a Go-Gobra internal identifier — producing an invalid
Silver program with no clear error message. Reject both patterns here (third-line-of-defense,
after plan 07 rejects `unsafe` at parse time) with a user-facing diagnostic:

- Identifiers matching `_u[0-9A-F]{1,6}_` (unicode escape encoding): reject any
  `*ast.Ident` whose `Name` matches the regular expression `^_u[0-9A-F]{1,6}_$`.
  Error message: `"identifier %q uses a pattern reserved for Gobra's unicode encoding"`
- Identifiers beginning with `gobra__` (internal namespace prefix): reject any `*ast.Ident`
  whose `Name` has the prefix `gobra__`.
  Error message: `"identifier %q uses the 'gobra__' prefix reserved for internal Gobra names"`

These checks apply to all declared names (function names, variable names, type names, field
names, method names). Run as a second pass over declarations after `go/types.Check` completes.

These three checks should run as a single AST walk after `go/types.Check` completes, before
the ghost type checking pass (plan 09).

## Resolved Questions

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/info/checker.go` and
`internal/info/typeinfo.go` and verified before this plan is considered complete.

**`Check` output safety (non-nil info iff no errors):**
```go
//@ requires pkg != nil && importer != nil
//@ ensures  (len(diags) == 0) == (info != nil)
//@ ensures  info != nil ==> info.Go != nil
func Check(pkg *frontend.PPackage, importer types.Importer) (info *TypeInfo, diags []Diagnostic)
```

**Stub resolution invariant (end of Pass 2 assertion):**

`ghostTable` is an unexported local; it cannot appear in a function-level Gobra annotation.
The invariant is expressed via a pure predicate on the exported `TypeInfo.Ghost` field instead:
```go
// Pure predicate: every ghost type in the GhostTypeInfo table is resolved (not a stub).
//@ pure func allGhostTypesResolved(g GhostTypeInfo) bool

// After Check completes, all ghost types must be resolved:
//@ ensures info != nil ==> allGhostTypesResolved(info.Ghost)
```

**`CheckSpecs` incremental fill (Ghost field populated, Go field unchanged):**
```go
//@ requires info != nil && info.Go != nil
//@ ensures  info.Go == old(info.Go)   // Go side-table not mutated
//@ ensures  len(diags) == 0 ==> info.Ghost.Resolved()
func CheckSpecs(pkg *frontend.PPackage, info *TypeInfo) []Diagnostic
```

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
extending `types.Type` (implementing `Underlying() types.Type`, `String() string`, and
`IsStub() bool`). `IsStub()` returns true for a forward-reference placeholder not yet resolved;
false once the full type is known. All concrete implementations return `false` for `IsStub()`.
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
| forward-reference stub | `StubType{Name string}` — `IsStub()` returns `true` |

`ADTConstructor` definition (in `internal/info/ghosttypes.go`):
```go
type ADTConstructor struct {
    Tag    string      // constructor name, e.g. "Leaf", "Node"
    Fields []GhostType // field types in declaration order
}
```

`GhostTypeInfo` definition (in `internal/info/ghosttypes.go`):
```go
type GhostTypeInfo struct {
    Types map[PNode]GhostType // ghost type for each ghost/spec AST node; nil until CheckSpecs fills it
}

// Resolved returns true iff Types is non-nil and contains no StubType entries.
func (g GhostTypeInfo) Resolved() bool {
    if g.Types == nil { return false }
    for _, t := range g.Types { if t.IsStub() { return false } }
    return true
}
```

These satisfy `types.Type` and are stored in the `map[PNode]GhostType` table inside
`GhostTypeInfo` — NOT in the stdlib `*types.Info` map (which is not extensible by callers).
The `GhostTypeInfo` wraps `map[PNode]GhostType` for ghost-only constructs; combined with
the `*types.Info` from `go/types.Check`, they form the two-table `TypeInfo` output (see
Key Implementation Notes). All `GhostType` implementations live in
`internal/info/ghosttypes.go`.

**Note on `PNode` key type**: `ast.Node` (the `go/ast` node interface) cannot be used as the
map key because Gobra ghost nodes (`PForall`, `PAccess`, etc.) do not implement it. Instead,
use `PNode` — a unified interface defined in plan 03 that both `go/ast` nodes and Gobra ghost
nodes implement. See plan 03 ("PNode Interface") for the definition. Map keys use pointer
identity (concrete types are always pointer types), so no custom equality is needed.
