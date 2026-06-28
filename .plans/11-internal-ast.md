# 11 — Internal AST

## Objective

Define Go type definitions for the Gobra internal AST — the lower-level, desugared
representation used by the translator. The internal AST is simpler than the frontend AST:
syntactic sugar is eliminated, implicit operations are made explicit, and ghost/non-ghost
constructs are unified.

## Scope

**In scope:**
- Go struct/interface types for every internal AST node
- Node families: members (methods, functions, fields, predicates), statements, expressions,
  assertions, types
- Position tracking (carried from frontend AST via desugaring)
- Visitor interface for traversal (used by transforms and translator)

**Out of scope:**
- Desugaring logic (12-desugarer.md)
- Internal transforms (13)
- Translation to Silver (19+)

## Dependencies

- [01-project-setup.md](01-project-setup.md) — repository must exist
- Note: 11 is **independent of** the frontend AST (03) and can be written in parallel

## Reference: Current Gobra

- `src/main/scala/viper/gobra/ast/internal/` — the canonical source
  - `Ast.scala` — all internal node types; study this carefully
  - `PrettyPrinter.scala` — useful for understanding node shapes
- `src/main/scala/viper/gobra/frontend/Desugar.scala` — shows what each frontend node
  becomes in the internal AST; read alongside Ast.scala

## Key Internal Node Families

- **Members**: `Method`, `Function`, `FPredicate`, `MPredicate`, `Field`, `GlobalVar`
- **Statements**: `Seqn`, `Assign`, `Return`, `If`, `While`, `Assert`, `Assume`,
  `Exhale`, `Inhale`, `Fold`, `Unfold`, `PackageWand`, `ApplyWand`, `New`,
  `Make`, `GoCall`, `Defer`, `FunctionCall`, `MethodCall`, `ClosureCall`,
  `Break`, `Continue`, `Label`
  - Note: `FunctionCall`, `MethodCall`, and `ClosureCall` carry a `targets: []LocalVar` slice
    for the multiple assignment targets at call sites. This is how multiple return values are
    decomposed: the call is a statement with N targets, not an expression.
  - `Break` and `Continue` carry an optional `Label string` field (the target label, or empty
    for unlabeled break/continue). `Label` wraps a `Body` statement with a `Name string`;
    labeled `break`/`continue` in desugared form jump to the labeled statement. The Scala
    implementation uses `LabelProxy`; Go-Gobra uses `Label` for the same role.
- **Expressions**: `Var`, `Deref`, `Ref`, `FieldRef`, `IndexedExp`, `SliceExp`,
  `PureFunctionCall`, `PureMethodCall`, `Unary`, `Binary`, `Old`, `Conditional`,
  `Tuple` (transient desugaring-only construct — see below; not stable in the final AST)

  The `Expr` interface includes an `Addressable() bool` method. Every concrete expression
  struct stores an `addressable bool` field and implements `Addressable() bool { return e.addressable }`.
  The desugarer (plan 12) sets the stored field from `go/types.Info.Types[frontendExpr].Addressable()`
  when lowering each frontend expression. Ghost expression nodes and desugarer-introduced
  temporaries (no frontend counterpart) default to `addressable = false`. The translator
  (plan 19) calls `node.Addressable()` to choose between `ExclusiveType` and `SharedType` —
  it does NOT call `TypeInfo.Addressable` on internal nodes.

- **Assertions**: `SepAnd`, `Access`, `Predicate`, `ExprAssertion`, `MagicWand`,
  `Forall`, `Exists`
- **Types**: `IntT`, `BoolT`, `StringT`, `PointerT`, `StructT`, `InterfaceT`,
  `SliceT`, `ArrayT`, `MapT`, `ChannelT`, `FunctionT`, `AdtT`, `PermissionT`,
  `TupleT` (for multi-value expression types)

## Deliverables

- `internal/ast/internal/` package with all node type definitions
- `Visitor` interface and default no-op traversal
- `PrettyPrinter` for debugging (helpful throughout development)
- Unit tests: construct representative internal AST trees and print/traverse them

## Resolved Questions

**Immutability (resolved):** The internal AST is immutable — all fields are set at construction,
no post-construction mutation. This is consistent with plan 13, where transforms are pure
`*internal.Program → *internal.Program` functions that construct new trees. Immutability
eliminates aliasing bugs in the transform pipeline and matches the Scala implementation.

**Multiple return values (resolved):** Go functions with multiple return values are decomposed
at the call site. `FunctionCall`, `MethodCall`, and `ClosureCall` are **statements** (not
expressions) with a `targets: []LocalVar` field. The desugarer introduces one fresh `LocalVar`
per return value and assigns them from the call statement. For the comma-ok idiom specifically
(`v, ok := m[k]`; `v, ok := <-ch`), the desugarer produces a `Tuple(args []Expr)` expression
transiently and immediately decomposes it into individual `Assign` statements targeting the LHS
variables. `Tuple` is a **desugaring-only intermediate** — it does not appear in the final
internal AST handed to the translator, and the translator has no encoding for it. If a
`Tuple` node reaches the translator, it is a desugarer bug (panic, do not silently drop it).
`TupleT` is similarly transient. Pure functions (`PureFunctionCall`, `PureMethodCall`) return
a single value (Go's `pure` functions are restricted to a single non-error return).

### Verification Specifications (C9)

The internal AST node types are immutable after construction. Gobra specs on traversal and
construction functions enforce structural invariants on the node types themselves:

1. **Non-nil body invariant**: Members with a required body (`Method`, `Function`, `FPredicate`)
   carry a non-nil postcondition on their constructors:
   ```go
   //@ ensures m != nil && acc(m.Body) && m.Body != nil
   func NewMethod(name string, body Stmt, ...) (m *Method)
   ```

2. **Position preservation**: Every node must carry a valid (non-zero) source position, proved
   by a postcondition on all constructor functions:
   ```go
   //@ ensures n != nil && n.Pos() != token.NoPos
   func NewAssign(lhs, rhs Expr, pos token.Pos) (n *Assign)
   ```

3. **No Tuple in final AST**: The `Tuple` expression type is a desugaring intermediate and must
   not appear in the AST handed to the translator. This invariant is enforced by a `wellFormed`
   predicate called at the end of desugaring (plan 12), but it is defined here alongside the
   node types:
   ```go
   //@ pred wellFormedExpr(e Expr)
   // wellFormedExpr holds when e contains no Tuple sub-expression.
   // Defined in internal/ast/internal/invariants.go; used by plan 12's postcondition.
   ```

4. **Addressability flag**: The `Addressable() bool` method on `Expr` returns a stored field;
   the stored field must be set exactly once at construction. This is enforced by making the
   `addressable` field unexported and only settable via the constructor:
   ```go
   //@ ensures r.Addressable() == addressable
   func NewVar(name string, typ Type, addressable bool) (r *Var)
   ```

