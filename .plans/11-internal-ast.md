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
  `Make`, `GoCall`, `Defer`
- **Expressions**: `Var`, `Deref`, `Ref`, `FieldRef`, `IndexedExp`, `SliceExp`,
  `Call`, `PureFunctionCall`, `Unary`, `Binary`, `Old`, `Conditional`
- **Assertions**: `SepAnd`, `Access`, `Predicate`, `ExprAssertion`, `MagicWand`,
  `Forall`, `Exists`
- **Types**: `IntT`, `BoolT`, `StringT`, `PointerT`, `StructT`, `InterfaceT`,
  `SliceT`, `ArrayT`, `MapT`, `ChannelT`, `FunctionT`, `AdtT`, `PermissionT`

## Deliverables

- `internal/ast/internal/` package with all node type definitions
- `Visitor` interface and default no-op traversal
- `PrettyPrinter` for debugging (helpful throughout development)
- Unit tests: construct representative internal AST trees and print/traverse them

## Open Questions

- Should the internal AST be immutable (all fields set at construction, no mutation) or allow
  post-construction annotation? Immutable is safer and matches the Scala implementation.
