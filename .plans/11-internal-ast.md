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
  `Make`, `GoCall`, `Defer`, `FunctionCall`, `MethodCall`, `ClosureCall`
  - Note: `FunctionCall`, `MethodCall`, and `ClosureCall` carry a `targets: []LocalVar` slice
    for the multiple assignment targets at call sites. This is how multiple return values are
    decomposed: the call is a statement with N targets, not an expression.
- **Expressions**: `Var`, `Deref`, `Ref`, `FieldRef`, `IndexedExp`, `SliceExp`,
  `PureFunctionCall`, `PureMethodCall`, `Unary`, `Binary`, `Old`, `Conditional`,
  `Tuple` (for comma-ok and channel-receive pairs — see below)
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
(`v, ok := m[k]`; `v, ok := <-ch`), the internal AST uses a `Tuple(args []Expr)` expression
with `TupleT(ts []Type)` — because these are expressions in a larger statement, not
function calls. Pure functions (`PureFunctionCall`, `PureMethodCall`) return a single value
(Go's `pure` functions are restricted to a single non-error return).
