# Go AST Coverage Table

This table enumerates every concrete `go/ast` node type and assigns it a disposition for
Go-Gobra translation. It is a completeness checklist, not a design document — gaps here are
the right place to discover missing encoding work before hitting a runtime panic.

**Dispositions:**

- **In scope** — handled; encoding plan noted
- **Out of scope** — explicitly excluded; reason noted; type checker or desugarer must reject
  inputs containing this construct before translation begins
- **N/A** — not a semantic AST node (infrastructure type, error node, or parse-time artifact);
  does not reach the translator

A `?` on any row means the coverage is uncertain and needs a decision before that encoding
plan is considered complete.

---

## Declarations

| Node | Disposition | Plan / Notes |
|------|-------------|--------------|
| `FuncDecl` | In scope | 27 — top-level function and method declarations |
| `GenDecl` | In scope | 12 — structural wrapper for `TypeSpec`/`ValueSpec`; desugared by iterating specs |
| `TypeSpec` | In scope | 12 dispatches to 20–30 based on underlying type kind |
| `ValueSpec` | In scope | 12 — variable and constant declarations; desugared to `Assign` |
| `BadDecl` | N/A | Parser error node; programs containing it are rejected before type checking |
| `ImportSpec` | N/A | 07 — handled by package resolver; does not reach translator |

---

## Statements

| Node | Disposition | Plan / Notes |
|------|-------------|--------------|
| `AssignStmt` | In scope | 12 — covers `=`, `:=`, `+=`, `-=`, etc.; desugared to internal `Assign` |
| `BlockStmt` | In scope | 12 — desugared to internal `Seqn` |
| `BranchStmt` (`break`) | In scope | 12 — desugared to internal `Break` with label tracking |
| `BranchStmt` (`continue`) | In scope | 12 — desugared to internal `Continue` with label tracking |
| `BranchStmt` (`goto`) | Out of scope | Not in Scala Gobra frontend AST (`PGoto` exists but is not desugared); type checker must reject |
| `BranchStmt` (`fallthrough`) | Out of scope | Not in Scala Gobra; type checker must reject |
| `CaseClause` | In scope | 12 — switch arms; desugared to if-else chain |
| `CommClause` | In scope | 28 — select statement arms |
| `DeclStmt` | In scope | 12 — wrapper for in-body `GenDecl`; desugared to declarations + assigns |
| `DeferStmt` | In scope | 27 |
| `EmptyStmt` | In scope | 12 — desugared to empty `Seqn` |
| `ExprStmt` | In scope | 12 — wraps a call expression used as a statement |
| `ForStmt` | In scope | 12 — desugared to internal `While` |
| `GoStmt` | In scope | 28 — goroutine spawning |
| `IfStmt` | In scope | 12 — desugared to internal `If`; optional init stmt desugared first |
| `IncDecStmt` | In scope | 12 — desugared to `Assign` with `+1` or `-1` |
| `LabeledStmt` (loop label) | In scope | 12 — labels used as targets of `break`/`continue`; Scala uses `LabelProxy` |
| `LabeledStmt` (goto target) | Out of scope | `goto` is out of scope; if only goto-targeted labels appear, type checker must reject |
| `RangeStmt` | In scope | 12 — desugared to indexed `While`; key/value vars introduced as temporaries |
| `ReturnStmt` | In scope | 27 |
| `SelectStmt` | In scope | 28 |
| `SendStmt` | In scope | 28 |
| `SwitchStmt` | In scope | 12 — desugared to if-else chain; tag expression evaluated once |
| `TypeSwitchStmt` | In scope | 25 — type switch desugaring uses interface encoding |
| `BadStmt` | N/A | Parser error node; programs containing it are rejected before type checking |

---

## Expressions

| Node | Disposition | Plan / Notes |
|------|-------------|--------------|
| `BasicLit` | In scope | 20 — integer, float, string, rune literals; bool literals are `Ident` (`true`/`false`) |
| `BinaryExpr` | In scope | 20 — arithmetic, comparison, logical; plan 23 for slice/array length ops |
| `CallExpr` | In scope | 27 (user functions/methods/closures); 31 (builtins: `len`, `cap`, `make`, `new`, `append`, `copy`, `delete`, `close`) |
| `CompositeLit` | In scope | 12 dispatches to: 21 (struct literal), 23 (slice/array literal), 24 (map literal) |
| `Ellipsis` (`[...]T` array) | In scope | 23 — `[...]T{...}` array size; `go/types` resolves concrete length before desugaring |
| `Ellipsis` (variadic `...T`) | In scope | 27 — `...T` in parameter list; desugared to slice type. Note: `f(s...)` spread at call site uses `CallExpr.Ellipsis` (a `token.Pos` field), not an `*ast.Ellipsis` node — spread detection is by type shape (plan 12) |
| `FuncLit` | In scope | 27 — closures; captured variables encoded as additional parameters |
| `Ident` | In scope | 12 — variable references, type names, `true`/`false`, `nil`; qualified names via `SelectorExpr` |
| `IndexExpr` | In scope | 23 (slice/array indexing); 24 (map lookup, including comma-ok form) |
| `IndexListExpr` | In scope | 30 — generic type/function instantiation (Go 1.18+) |
| `KeyValueExpr` | In scope | 12 — key-value pairs inside `CompositeLit`; desugared as part of literal handling |
| `ParenExpr` | In scope | 12 — transparent; stripped (inner expression forwarded) |
| `SelectorExpr` | In scope | 21 (field access); 27 (qualified function/method reference, embedded field path expansion) |
| `SliceExpr` | In scope | 23 — `s[lo:hi]`, `s[lo:hi:max]` |
| `StarExpr` (expression) | In scope | 22 — pointer dereference `*p` |
| `StarExpr` (type) | In scope | 22 — pointer type expression `*T` |
| `TypeAssertExpr` | In scope | 25 — `x.(T)` and comma-ok form `x.(T)` |
| `UnaryExpr` (`!`, `-`, `^`, `+`) | In scope | 20 |
| `UnaryExpr` (`&`) | In scope | 22 — address-of |
| `UnaryExpr` (`<-`) | In scope | 28 — channel receive |
| `BadExpr` | N/A | Parser error node; programs containing it are rejected before type checking |

---

## Type Expressions

| Node | Disposition | Plan / Notes |
|------|-------------|--------------|
| `ArrayType` | In scope | 23 |
| `ChanType` | In scope | 28 |
| `FuncType` | In scope | 27 |
| `InterfaceType` | In scope | 25 |
| `MapType` | In scope | 24 |
| `StructType` | In scope | 21 |

---

## Infrastructure / Not AST Nodes

| Node | Disposition | Notes |
|------|-------------|-------|
| `BadDecl`, `BadExpr`, `BadStmt` | N/A | Parser error nodes |
| `ChanDir` | N/A | Enum constant embedded in `ChanType`; not a standalone node |
| `Comment`, `CommentGroup` | N/A | Stripped during preprocessing; Gobra annotations extracted separately (plan 05) |
| `Directive`, `DirectiveArg` | N/A | Go compiler directives (`//go:noinline` etc.); ignored |
| `Field`, `FieldList` | N/A | Structural wrappers inside `StructType`/`FuncType`; handled as part of parent |
| `File` | N/A | File container; handled by package resolver (plan 07) |
| `ImportSpec` | N/A | 07 — package resolver |
| `MergeMode` | N/A | Constant used with `ast.MergePackageFiles`; not a node |
| `Object`, `ObjKind` | N/A | Deprecated `go/ast` scope objects; resolution done by `go/types` |
| `Package` | N/A | Package container; handled by package resolver (plan 07) |
| `Scope` | N/A | Deprecated `go/ast` scope; resolution done by `go/types` |
| `CommentMap` | N/A | `map[Node][]*CommentGroup` utility type; not a node |
| `FieldFilter`, `Filter` | N/A | Function types used by `ast.Fprint`/`ast.PackageExports`; never appear as nodes |
| `Importer` | N/A | Deprecated function type tied to `*ast.Object`; superseded by `go/types` |
| `Decl`, `Expr`, `Node`, `Spec`, `Stmt` | N/A | Interfaces, not concrete node types |
| `Visitor` | N/A | Interface for `ast.Walk`; an implementation concern, not a node type |

---

## Known Gaps and Open Questions

These items require an explicit decision before the corresponding encoding plan is marked
complete. Add a row to the table above once the decision is made.

| Construct | Status | Notes |
|-----------|--------|-------|
| `goto` / `LabeledStmt` (goto target) | **Action required** | Scala crashes with `???` (`NotImplementedError`) — not a clean error. Go rewrite must emit an explicit user-facing diagnostic from the type checker. Do not rely on the catch-all panic. |
| `fallthrough` | **Action required** | Absent from Gobra's ANTLR grammar; never reaches Scala code. `go/parser` *does* parse it as `BranchStmt(token.FALLTHROUGH)`. Go rewrite type checker must explicitly reject it with a diagnostic. |
| `unsafe` package | **Action required** | Not handled in Scala; silently fails when `unsafe.*` names are unresolved. Go rewrite should explicitly reject any `import "unsafe"` with a diagnostic during package resolution (plan 07) or type checking (plan 08). |
| `recover()` | **Action required** | Not in Gobra's grammar at all. `go/types` resolves `recover` as a built-in; the Go rewrite type checker must explicitly reject calls to it with a diagnostic. |
| ~~`init()` functions~~ | Resolved: in scope | Plan 27 (lines 138–157): each `init` → `{pkg}_init_N`; wrapper `{pkg}_run_inits` calls them in order; cross-package ordering via topological translation. |
| ~~Variadic spread `f(s...)`~~ | Resolved: in scope | Four-branch dispatch documented in plan 12 (Resolved Questions). Detection by type shape, not `CallExpr.Ellipsis`. |
| ~~Named return values in closures~~ | Resolved: in scope | Handled via same `PNamedParameter` substitution as regular functions (Desugar.scala). No special closure case needed. |
| ~~Multi-value map/channel assign~~ | Resolved: in scope | `v, ok = m[k]` → `SafeMapLookup` (Desugar.scala:2026); `v, ok = <-ch` → `SafeReceive` (~2020). Both two-LHS cases handled. |
