# 03 — Frontend AST

## Objective

Define Go type definitions for the Gobra frontend AST — the in-memory representation of a parsed,
annotated Go program before type checking. This is the data contract between the parser and the
type checker.

## Scope

**In scope:**
- Go struct/interface types for every AST node the current Gobra frontend represents
- Source position tracking (`token.Pos` or a wrapper) on every node
- Node categories: declarations, statements, expressions, types, specifications, ghost constructs
- Visitor/walker interface for traversal (used by type checker and desugarer)

**Out of scope:**
- Parsing logic (04-go-parser.md, 05-annotation-parser.md)
- Type information attached to nodes (that's added by the type checker in 08)
- Internal AST (11-internal-ast.md — separate, post-desugaring representation)

## Dependencies

- [01-project-setup.md](01-project-setup.md) — repository must exist
- [02-annotation-syntax-decision.md](02-annotation-syntax-decision.md) — annotation node shapes
  depend on the chosen syntax

## Reference: Current Gobra

- `src/main/scala/viper/gobra/ast/frontend/` — the canonical source of truth
  - `Ast.scala` — all frontend node types
  - `AstPattern.scala` — pattern aliases / extractors
- `src/main/scala/viper/gobra/frontend/Parser.scala` — shows which AST nodes the parser produces
- Note: current Gobra re-uses `go/ast` types for standard Go nodes and adds Gobra-specific nodes
  on top; the Go rewrite can do the same by embedding `go/ast` nodes or wrapping them

## Key AST Node Families

- **Program / Package**: `PPackage`, `PProgram`, `PImport`
- **Declarations**: `PMethodDecl`, `PFunctionDecl`, `PVarDecl`, `PConstDecl`, `PTypeDecl`
- **Statements**: `PBlock`, `PAssignment`, `PReturn`, `PIf`, `PFor`, `PDefer`, `PGo`, etc.
- **Expressions**: `PNamedOperand`, `PCall`, `PIndexed`, `PSlice`, `PUnary`, `PBinary`, etc.
- **Types**: `PNamedType`, `PStructType`, `PInterfaceType`, `PSliceType`, `PMapType`,
  `PPointerType`, `PFunctionType`, `PChannelType`
- **Specifications**: `PMethodSpec`, `PFunctionSpec` containing:
  - Preconditions (`requires`), postconditions (`ensures`), preserves
  - Termination measures
  - Ghost parameters / results
- **Ghost constructs**: `PGhostStatement`, `PFold`, `PUnfold`, `PAssert`, `PAssume`,
  `PExhale`, `PInhale`, `PPackageWand`, `PApplyWand`
- **Ghost expressions**: `PForall`, `PExists`, `PAccess`, `POld`, `PBefore`,
  `PPermission`, `PUnfolding`, sequence/set/multiset/dict literals and operations
- **ADTs**: `PAdtClause`, `PAdtType`, `PMatchStatement`, `PMatchExp`

## Deliverables

- `internal/ast/frontend/` package with all node type definitions
- `Visitor` interface and default no-op implementation
- Position wrapper that maps nodes to source file + line + column
- Unit tests confirming that key node types can be constructed and traversed

## Open Questions

- Embed `go/ast` nodes directly (thin wrapper) or define parallel types for every Go construct?
  Embedding is less code but couples the AST to stdlib; parallel types give full control.
- Use tagged unions (interface + type switch) or a single large struct with optional fields?
  Interface approach is idiomatic Go and mirrors the Scala sealed trait hierarchy.
