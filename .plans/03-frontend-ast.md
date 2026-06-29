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
  // It narrows PNode to the declaration category, allowing GhostDecls to be typed as []PDecl.
  // ParseAnnotation(isFileScope=true) returns file-scope ghost declarations directly in
  // decls []PDecl — no type assertion needed by plan 07's MergeGhostStatements.
  type PDecl interface {
      PNode
      pDecl() // unexported marker method
  }
  ```
  All concrete declaration types in `internal/ast/frontend/` must implement `pDecl()`.
  Ghost declaration types produced by plan 05 (`PAdtType`, `PGhostFunc`, `PPredDecl`, etc.)
  must also implement `PDecl`. Plan 07's `MergeGhostStatements` receives these directly in
  the `decls []PDecl` return from `ParseAnnotation(isFileScope=true)` and appends them to
  `pfile.GhostDecls` without any type assertion.
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
  - `PFunctionDecl` wraps `*ast.FuncDecl` + `*PFunctionSpec` + `*PBodyParameterInfo`
  - `PMethodDecl` wraps `*ast.FuncDecl` + `Receiver *PReceiver` + `*PFunctionSpec` + `*PBodyParameterInfo`
    (Note: `PReceiver` carries the **full** receiver definition — named vs. unnamed, the
    receiver type (value / actual-pointer / ghost-pointer), the identifier, and whether the
    receiver is addressable. This is not purely a ghost extension: `go/ast` cannot distinguish
    a ghost pointer receiver from a real pointer receiver (both are `*ast.StarExpr`); `PReceiver`
    fills that gap. `ast.FuncDecl.Recv` is still needed by `go/types.Check`; `PReceiver` is
    Gobra's authoritative receiver representation. See the Type Definitions section for the
    full `PReceiver` / `PMethodRecvType` hierarchy.)
  - `PTypeDecl` wraps `*ast.TypeSpec` + optional Gobra type extension
  - `PInterfaceType` wraps `*ast.InterfaceType` + ghost method specs
  - `PBlockStmt` wraps `*ast.BlockStmt` + interleaved ghost statements
  - `PForStmt` / `PRangeStmt` wrap their `go/ast` counterparts + `PLoopSpec`

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
// Pointer receivers are used so that *PGoStmt stored in []PStmt can be
// type-asserted back to *PGoStmt by the Visitor dispatcher.
type PGoStmt struct{ Stmt ast.Stmt }
func (*PGoStmt) pStmt() {}
func (s *PGoStmt) Pos() token.Pos { return s.Stmt.Pos() }
func (s *PGoStmt) End() token.Pos { return s.Stmt.End() }

// Gobra ghost statement types also implement PStmt via pointer receivers.
// e.g.: func (*PAssert) pStmt() {}
```

`PBlockStmt` holds:

```go
type PBlockStmt struct {
    Stmts          []PStmt    // ordered mix of *PGoStmt and ghost statement types
    Lbrace, Rbrace token.Pos
}
```

**Raw annotation side-table**: Plan 04's `ParseFile` captures `//@ ` comment groups and
records them in `PFile.BlockAnnotations` (see `PFile` and `RawAnnotation` in the Type
Definitions section). Each `go/ast.CommentGroup` of consecutive `//@ ` lines becomes one
`RawAnnotation` (lines concatenated with `\n`, prefix stripped). `PBlockStmt.Stmts` contains
only Go statements (`*PGoStmt`) when `ParseFile` returns. Plan 07 reads
`PFile.BlockAnnotations`, calls plan 05's annotation parser on each `RawAnnotation.Text`,
and merges the resulting ghost statement nodes into the correct `PBlockStmt` by token
position in step 4 of its coordination model. After `MergeGhostStatements` returns,
`PFile.BlockAnnotations` is set to nil. `PBlockStmt.Stmts` is not fully populated until
after plan 07 runs. See plan 07 for the complete 4-step model.

The `Visitor` interface includes a `VisitPGoStmt(*PGoStmt)` method so the desugarer can
dispatch on it and invoke `ast.Inspect` for the wrapped Go statement.

### Loop spec: PForStmt / PRangeStmt

Loop invariants and termination measures are **not** interleaved within the body; they appear
in `//@ invariant P` and `//@ decreases e` comment lines immediately preceding the opening
`{`. They are attached via a `PLoopSpec` field on the wrapper — not as `PStmt` entries in
the body's `PBlockStmt`:

```go
type PLoopSpec struct {
    Invariants        []PExpression      // from //@ invariant P lines
    TerminationMeasure *PTerminationMeasure // from //@ decreases e (nil if absent)
}

type PForStmt struct {
    GoFor *ast.ForStmt
    Spec  PLoopSpec    // invariants and termination measure; populated by plan 07
    Body  *PBlockStmt  // the loop body, interleaving Go and ghost stmts
}
```

`PRangeStmt` follows the same pattern (`GoRange *ast.RangeStmt`, `Spec PLoopSpec`,
`Body *PBlockStmt`). The body's `PBlockStmt` contains only statements inside the braces.

`PLoopSpec` matches the Scala `PLoopSpec` exactly. `PExpression` is used for invariants
(Gobra has no separate `PAssertion` trait — assertions are expressions).

### Verification Specifications (C9)

All wrapper constructors and traversal entry points carry Gobra pre/postconditions enforcing
two invariants: non-nil embedded `go/ast` fields, and read-only structural immutability.

1. **Non-nil embedded field invariant**: Each wrapper constructor requires its `go/ast` argument
   to be non-nil and guarantees the wrapper's embedded field is accessible:
   ```go
   //@ requires goFunc != nil && spec != nil
   //@ ensures  acc(f.GoFunc) && f.GoFunc != nil
   //@ ensures  acc(f.Spec)   && f.Spec   != nil
   func NewPFunctionDecl(goFunc *ast.FuncDecl, spec *PFunctionSpec, bpi *PBodyParameterInfo) (f *PFunctionDecl)

   //@ requires goStmt != nil
   //@ ensures  acc(w.Stmt) && w.Stmt != nil
   func NewPGoStmt(goStmt ast.Stmt) (w *PGoStmt)

   //@ requires goFor != nil && body != nil
   //@ ensures  acc(s.GoFor) && s.GoFor != nil
   //@ ensures  acc(s.Body)  && s.Body  != nil
   func NewPForStmt(goFor *ast.ForStmt, spec PLoopSpec, body *PBlockStmt) (s *PForStmt)
   ```

2. **Read-only traversal immutability**: The `Visitor.Visit` dispatch methods require read
   access to the node but do not modify it:
   ```go
   //@ requires acc(n, 1/2)
   //@ ensures  acc(n, 1/2)
   func (v *defaultVisitor) VisitPFunctionDecl(n *PFunctionDecl)
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

## Type Definitions

All types below are defined in `internal/ast/frontend/`. This section is the authoritative
contract; downstream plans must not redefine these types.

### PFile

```go
// RawAnnotation is a single logical //@ annotation captured by plan 04's ParseFile.
// Consecutive //@ lines within one go/ast CommentGroup are concatenated with "\n"
// into a single RawAnnotation (Text has the prefix stripped; Pos is the first line's position).
// Exported so that internal/frontend/parser.go (plan 04) can use it in ParseFile's signature
// without a circular import.
type RawAnnotation struct {
    Text string    // content after stripping the "//@ " prefix; multi-line uses "\n" separator
    Pos  token.Pos // position of the first //@ line in the source file
}

type PFile struct {
    GoFile     *ast.File // the underlying go/ast tree; passed to go/types.Check
    GhostDecls []PDecl   // file-scope ghost declarations (ADTs, ghost funcs, predicates)
                         // populated by plan 07's MergeGhostStatements

    // BlockAnnotations maps each PBlockStmt to its raw //@ annotations.
    // Key nil holds file-scope annotations (ghost ADTs, predicates, funcs produced by the
    // Gobrafier for top-level //@ blocks); plan 07 routes these to GhostDecls.
    // Populated by plan 04 (ParseFile); consumed and set to nil by plan 07 (MergeGhostStatements).
    // Nil after merge is complete. Callers must not treat PBlockStmt.Stmts as fully merged
    // until BlockAnnotations is nil.
    BlockAnnotations map[*PBlockStmt][]RawAnnotation
}
```

### PFunctionDecl / PMethodDecl

```go
// PBodyParameterInfo tracks parameters declared shared in the function body.
// Matches Scala PBodyParameterInfo. Nil when there is no body or no shared params.
type PBodyParameterInfo struct {
    ShareableParameters []*ast.Ident
}

type PFunctionDecl struct {
    GoFunc       *ast.FuncDecl
    Spec         *PFunctionSpec
    BodyParamInfo *PBodyParameterInfo // nil if no body or no shared params
}

type PMethodDecl struct {
    GoFunc       *ast.FuncDecl
    Receiver     *PReceiver      // full Gobra receiver (named/unnamed, value/ptr/ghost-ptr)
    Spec         *PFunctionSpec
    BodyParamInfo *PBodyParameterInfo
}
```

### PReceiver / PMethodRecvType

```go
// PReceiver is the Gobra representation of a method receiver.
// Two cases: named (r *T) and unnamed (*T).
type PReceiver interface {
    PNode
    pReceiver()
    RecvType() PMethodRecvType
}

type PNamedReceiver struct {
    ID          *ast.Ident     // the receiver variable name
    Type        PMethodRecvType
    Addressable bool           // whether the receiver can have its address taken
}
func (*PNamedReceiver) pReceiver() {}
func (r *PNamedReceiver) RecvType() PMethodRecvType { return r.Type }

type PUnnamedReceiver struct {
    Type PMethodRecvType
}
func (*PUnnamedReceiver) pReceiver() {}
func (r *PUnnamedReceiver) RecvType() PMethodRecvType { return r.Type }

// PMethodRecvType distinguishes the three receiver type forms.
// go/ast cannot distinguish PMethodReceiveGhostPointer from PMethodReceiveActualPointer
// (both are *ast.StarExpr); this hierarchy fills that gap.
type PMethodRecvType interface {
    PNode
    pMethodRecvType()
    TypeName() *ast.Ident // the base named type (e.g. MyType in *MyType)
}

type PMethodReceiveName          struct{ Typ *ast.Ident } // value recv:        (r MyType)
type PMethodReceiveActualPointer struct{ Typ *ast.Ident } // actual ptr recv:   (r *MyType)
type PMethodReceiveGhostPointer  struct{ Typ *ast.Ident } // ghost ptr recv:    annotation-only
```

### PFunctionSpec

Matches Scala `PFunctionSpec` exactly. Preconditions and postconditions are not stored as
flat slices; they are derived from the clause list (same as Scala's `pres`/`posts` methods).

```go
type PFunctionSpec struct {
    Clauses             []PFunctionSpecClause
    TerminationMeasures []PTerminationMeasure
    BackendAnnotations  []PBackendAnnotation
    IsPure              bool
    IsTrusted           bool
    IsOpaque            bool
    MayBeUsedInInit     bool
}

// Pres returns all precondition expressions (requires + preserves clauses).
func (s *PFunctionSpec) Pres() []PExpression { ... }
// Posts returns all postcondition expressions (preserves + ensures clauses).
func (s *PFunctionSpec) Posts() []PExpression { ... }

// PFunctionSpecClause is one clause in a function spec.
type PFunctionSpecClause interface {
    PNode
    pClause()
    ClauseExp() PExpression
}

type PRequires  struct{ Exp PExpression }
type PEnsures   struct{ Exp PExpression }
type PPreserves struct{ Exp PExpression }

func (*PRequires)  pClause() {}; func (c *PRequires)  ClauseExp() PExpression { return c.Exp }
func (*PEnsures)   pClause() {}; func (c *PEnsures)   ClauseExp() PExpression { return c.Exp }
func (*PPreserves) pClause() {}; func (c *PPreserves) ClauseExp() PExpression { return c.Exp }

// PTerminationMeasure is either a wildcard (decreases _) or a tuple of expressions.
type PTerminationMeasure interface {
    PNode
    pTerminationMeasure()
}

type PWildcardMeasure struct {
    Cond *PExpression // optional condition; nil means unconditional
}
func (*PWildcardMeasure) pTerminationMeasure() {}

type PTupleTerminationMeasure struct {
    Tuple []PExpression
    Cond  *PExpression // optional condition; nil means unconditional
}
func (*PTupleTerminationMeasure) pTerminationMeasure() {}

type PBackendAnnotation struct {
    Key    string
    Values []string
}
```

### Visitor Interface

The `Visitor` interface covers all Gobra wrapper node types defined in this plan. Ghost
expression and statement types added by plan 05 extend the same interface in the same package
(`internal/ast/frontend/`) — no circular dependency, as both plans contribute to one package.

```go
type Visitor interface {
    VisitPFunctionDecl(n *PFunctionDecl)
    VisitPMethodDecl(n *PMethodDecl)
    VisitPTypeDecl(n *PTypeDecl)
    VisitPInterfaceType(n *PInterfaceType)
    VisitPBlockStmt(n *PBlockStmt)
    VisitPGoStmt(n *PGoStmt)
    VisitPForStmt(n *PForStmt)
    VisitPRangeStmt(n *PRangeStmt)
    // Ghost expression / statement visitors added by plan 05:
    // VisitPFold, VisitPUnfold, VisitPAssert, VisitPAssume, VisitPExhale,
    // VisitPInhale, VisitPPackageWand, VisitPApplyWand,
    // VisitPForall, VisitPExists, VisitPAccess, VisitPOld, VisitPUnfolding, ...
}

// defaultVisitor provides no-op implementations of all Visitor methods.
// Embed it to implement only the methods you care about.
type defaultVisitor struct{}
```

