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

## Design Decisions (Resolved)

- **Embed `go/ast` nodes (resolved — see D9 in DECISIONS.md)**: The frontend AST wraps and
  extends `go/ast` rather than defining parallel types for every Go construct. Gobra-specific
  nodes (specifications, ghost constructs, ADTs, permission expressions) are defined as
  additional types alongside the embedded stdlib nodes. This enables the type checker (08)
  to drive `go/types` on the underlying `*ast.File` for standard Go type checking, avoiding
  a full reimplementation of the Go type system from scratch.

- **Tagged unions (resolved)**: Use `interface` + type switch for node polymorphism. This is
  idiomatic Go and mirrors the Scala sealed trait hierarchy. Avoid large structs with optional
  fields.

- **Consequence for node shape**: For any Go construct that `go/ast` already represents (file,
  function declaration, statement, expression, type), the Gobra frontend AST adds a thin wrapper
  or companion struct carrying Gobra-specific data (attached specs, ghost markers). For constructs
  `go/ast` has no representation for (predicates, magic wands, ADTs, ghost parameters), define
  new types from scratch.
