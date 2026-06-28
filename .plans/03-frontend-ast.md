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
- Definition of formal Gobra ghost fields or predicates tracking memory ownership of wrapped go/ast nodes.

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
- `PDecl` interface (a sub-interface of `PNode`) implemented by all declaration node types:
  `PMethodDecl`, `PFunctionDecl`, `PVarDecl`, `PConstDecl`, `PTypeDecl`, plus ghost-only
  declaration nodes (`PAdtType`, ghost function/predicate declarations produced by plan 05).
  Definition:
  ```go
  // PDecl is implemented by all declaration-level nodes (Go and ghost).
  // It narrows PNode to the declaration category, allowing GhostDecls to be typed as []PDecl
  // while remaining assignable from the []PNode returned by ParseAnnotation for file-scope nodes.
  type PDecl interface {
      PNode
      pDecl() // unexported marker method
  }
  ```
  All concrete declaration types in `internal/ast/frontend/` must implement `pDecl()`.
  Ghost declaration types produced by plan 05 (`PAdtType`, `PGhostFunc`, `PPredDecl`, etc.)
  must also implement `PDecl`. Plan 07's `MergeGhostStatements` type-asserts file-scope
  `PNode` values to `PDecl` before appending to `GhostDecls`; nodes that do not implement
  `PDecl` are a desugaring error (panic).
- `PFile` exposes the embedded `*ast.File` (e.g., a public `GoFile *ast.File` field) so that
  the type checker (plan 08) can pass it directly to `go/types.Check` without unwrapping, plus
  a `GhostDecls []PDecl` slice for file-scope ghost declarations (ADTs, ghost funcs, predicates)
  that the Gobrafier emits as file-scope `//@ ` comments and plan 07 routes here instead of into
  any `PBlockStmt`
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

- **Visitor interface — companion wrappers (resolved — see D10 in DECISIONS.md)**: The tree
  mixes `go/ast` types and Gobra-specific types, which cannot be covered by a single visitor
  interface without a common base. The chosen approach is Option B: **companion wrapper structs**
  for every `go/ast` node that Gobra extends, plus standalone Gobra types for ghost-only
  constructs. A single `Visitor` interface covers all Gobra node types, enabling the type
  checker (08) and desugarer (12) to traverse the tree with one mechanism.

  **Which nodes get wrappers** (only nodes Gobra actually extends):
  - `PFuncDecl` wraps `*ast.FuncDecl` + `*PFunctionSpec`
  - `PMethodDecl` wraps `*ast.FuncDecl` + receiver + `*PFunctionSpec`
    (Note: `ast.FuncDecl.Recv` holds the standard Go receiver parameter list. The additional
    `receiver` field on `PMethodDecl` is a Gobra-specific extension — it carries ghost
    annotations on the receiver, such as a ghost type assertion or a permission annotation
    on the receiver value. It is NOT redundant with `FuncDecl.Recv`; standard Go receivers
    are read from `FuncDecl.Recv` while Gobra-specific receiver info is in this field.
    Define it as `*PReceiver` containing the ghost marker and any spec-level receiver info.)
  - `PTypeDecl` wraps `*ast.TypeSpec` + optional Gobra type extension
  - `PInterfaceType` wraps `*ast.InterfaceType` + ghost method specs
  - `PBlockStmt` wraps `*ast.BlockStmt` + interleaved ghost statements
  - `PForStmt` / `PRangeStmt` wrap their `go/ast` counterparts + loop invariants

  Leaf nodes that Gobra does not annotate (`*ast.BasicLit`, `*ast.Ident`, `*ast.SelectorExpr`,
  etc.) are referenced directly as `go/ast` types from within Gobra wrapper nodes — no wrapper
  needed for them.

- **Consequence for node shape**: For any Go construct that `go/ast` already represents (file,
  function declaration, statement, expression, type), the Gobra frontend AST defines a companion
  struct embedding the `go/ast` node plus Gobra-specific data (attached specs, ghost markers).
  For constructs `go/ast` has no representation for (predicates, magic wands, ADTs, ghost
  parameters), define new Gobra types from scratch. The `go/types.Check` call in plan 08 runs
  on the embedded `*ast.File` directly, independent of the Gobra wrapper layer.

## PNode Interface

The type checker (plan 08) maintains side-tables keyed by AST node identity. Because the
Gobra AST mixes `go/ast` node types and Gobra-specific ghost/spec node types, the key cannot
be `ast.Node` (which ghost nodes do not implement). Define a unified `PNode` interface that
both universes satisfy:

```go
// PNode is implemented by both go/ast nodes and Gobra ghost/spec nodes.
// It is the key type for all AST-node-keyed side-tables (type info, ghost type info, etc.).
// Pointer identity is used for equality; all concrete PNode types are pointer types.
type PNode interface {
    Pos() token.Pos
    End() token.Pos
}
```

All `go/ast` node types already implement `PNode` via `ast.Node`. Gobra ghost/spec node types
must implement `Pos()` and `End()` — they already carry `token.Pos` for position tracking
(required by the source-position invariant in this plan). Both methods must return the node's
source span in the original file.

`PNode` is defined in `internal/ast/frontend/pnode.go`. All side-table maps that were
previously typed `map[ast.Node]T` must use `map[PNode]T`.

## Traversal Model (Critical Design Note)

The Gobra frontend AST mixes two node universes: Gobra wrapper/ghost types and `go/ast` types.
The `Visitor` interface covers only Gobra node types; `go/ast` traversal uses `ast.Inspect`
(or equivalent) directly. The two mechanisms are kept separate intentionally.

**Type checker (plan 08):**
- Go type checking: `go/types.Check` drives traversal of the underlying `*ast.File` — no
  Gobra visitor needed.
- Ghost type checking: the Gobra visitor traverses spec/ghost nodes only.
- These are two separate, non-interleaved passes over the same source. No coordination needed.

**Desugarer (plan 12):**
- Drives the Gobra visitor for all wrapper and ghost nodes.
- Inside each wrapper node handler, calls `ast.Inspect` to recurse into the embedded `go/ast`
  subtree for the Go-side content.
- This two-dispatch model is the intended implementation pattern; do not attempt to unify them.

### Interleaved content: PBlockStmt

The most important structural consequence of the two-universe mixing is `PBlockStmt`. A Go
block statement contains regular Go statements (`ast.Stmt`) and Gobra ghost statements
interleaved in source order. A pure `*ast.BlockStmt` cannot express this ordering; a side
table keyed by position would require callers to sort on every traversal.

**Resolution: `PStmt` interface with a thin `PGoStmt` wrapper.**

```go
// PStmt is either a regular Go statement or a Gobra ghost statement.
// It is the element type of PBlockStmt.Stmts, preserving source order.
type PStmt interface {
    pStmt()
    Pos() token.Pos
}

// PGoStmt wraps an ast.Stmt for interleaving in PBlockStmt.Stmts.
// This is the ONLY go/ast node type that receives a wrapper purely for
// ordering purposes (not because Gobra extends it). All other go/ast leaf
// types that do not appear in interleaved position (BasicLit, Ident, etc.)
// remain unwrapped.
type PGoStmt struct{ Stmt ast.Stmt }
func (PGoStmt) pStmt() {}
func (s PGoStmt) Pos() token.Pos { return s.Stmt.Pos() }

// Gobra ghost statement types also implement PStmt.
// e.g.: func (*PAssert) pStmt() {}
```

`PBlockStmt` holds:

```go
type PBlockStmt struct {
    Stmts          []PStmt    // ordered mix of PGoStmt and ghost statement types
    Lbrace, Rbrace token.Pos
}
```

Plan 04's `ParseFile` returns `PBlockStmt` nodes with Go statements only (`PGoStmt` wrappers
around `ast.Stmt` entries from `*ast.BlockStmt.List`), plus a per-block side-table of raw
`//@ ` annotation strings. Plan 07 then calls plan 05 on those raw strings and merges the
resulting ghost statement nodes into each `PBlockStmt` by token position in step 4 of the
coordination model. `PBlockStmt.Stmts` is not fully populated until after plan 07 runs
`MergeGhostStatements`. See plan 07 for the complete 4-step coordination model.

The `Visitor` interface includes a `VisitPGoStmt(*PGoStmt)` method so the desugarer can
dispatch on it and invoke `ast.Inspect` for the wrapped Go statement.

### Loop invariants: PForStmt / PRangeStmt

Loop invariants are **not** interleaved within the body; they appear in `//@ invariant P`
comment lines immediately preceding the opening `{`. They are attached as a field on the
wrapper — not as `PStmt` entries in the body's `PBlockStmt`:

```go
type PForStmt struct {
    GoFor      *ast.ForStmt
    Invariants []PAssertion  // from //@ invariant comments before the body
    Body       *PBlockStmt   // the loop body, interleaving Go and ghost stmts
}
```

`PRangeStmt` follows the same pattern. The body's `PBlockStmt` contains only statements
inside the braces, not the invariants.

### Verification Specifications (C9)

All wrapper constructors and traversal entry points carry Gobra pre/postconditions enforcing
two invariants: non-nil embedded `go/ast` fields, and read-only structural immutability.

1. **Non-nil embedded field invariant**: Each wrapper constructor requires its `go/ast` argument
   to be non-nil and guarantees the wrapper's embedded field is accessible:
   ```go
   //@ requires goFunc != nil && spec != nil
   //@ ensures  acc(f.GoFunc) && f.GoFunc != nil
   //@ ensures  acc(f.Spec)   && f.Spec   != nil
   func NewPFuncDecl(goFunc *ast.FuncDecl, spec *PFunctionSpec) (f *PFuncDecl)

   //@ requires goStmt != nil
   //@ ensures  acc(w.Stmt) && w.Stmt != nil
   func NewPGoStmt(goStmt ast.Stmt) (w *PGoStmt)

   //@ requires goFor != nil
   //@ ensures  acc(s.GoFor) && s.GoFor != nil
   func NewPForStmt(goFor *ast.ForStmt, invs []PAssertion, body *PBlockStmt) (s *PForStmt)
   ```

2. **Read-only traversal immutability**: The `Visitor.Visit` dispatch methods require read
   access to the node but do not modify it:
   ```go
   //@ requires acc(n, 1/2)
   //@ ensures  acc(n, 1/2)
   func (v *defaultVisitor) VisitPFuncDecl(n *PFuncDecl)
   ```
   The `1/2` fractional permission expresses that the visitor holds a read-only share; the
   caller retains the other half, preventing concurrent mutation.

3. **PBlockStmt source-order invariant**: After `MergeGhostStatements` (plan 07), every
   adjacent pair of statements in `PBlockStmt.Stmts` must be in non-decreasing `Pos()` order.
   This invariant is expressed as a loop invariant on the merge function (plan 07), but is
   defined here as a predicate over `PBlockStmt`:
   ```go
   //@ pred orderedStmts(b *PBlockStmt)
   // orderedStmts holds when for all 0 <= i < len(b.Stmts)-1:
   //   b.Stmts[i].Pos() <= b.Stmts[i+1].Pos()
   ```

