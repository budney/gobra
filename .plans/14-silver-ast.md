# 14 — Silver IR (Go Structs)

## Objective

Define Go struct types for the Silver intermediate representation. These types are the output
of the translator and the input to the Protobuf serializer (16). They mirror the Silver AST defined
in the `silver` submodule's Scala types, but expressed purely in Go.

## Scope

**In scope:**
- Go types for every Silver node: programs, domains, fields, functions, predicates, methods,
  statements, expressions, types, and error positions
- A pretty-printer that serializes the Go Silver AST to valid `.vpr` text (useful for
  debugging and potentially as a fallback verification path)
- Position information on every node (maps to Go source for error reporting)

**Out of scope:**
- Protobuf serialization of the Silver AST (16-silver-jni-builder.md)
- Translation logic (19+)

## Dependencies

- [01-project-setup.md](01-project-setup.md) — repository must exist
- Note: 14 is **independent** of 03, 11 and can be written in parallel with them

## Reference: Current Gobra / Silver

- `viperserver/silicon/silver/src/main/scala/viper/silver/ast/` — the canonical Silver AST
  - `Program.scala` — top-level program structure
  - `Member.scala` — methods, functions, predicates, fields, domains
  - `Statement.scala` — statement nodes
  - `Expression.scala` — expression nodes
  - `Type.scala` — Silver types
  - `utility/Positions.scala` — position tracking
- The Silver AST is what Go-Gobra must produce; match it precisely

## Key Silver Node Families

- **Program**: `Program{Domains, Fields, Functions, Predicates, Methods, Extensions}`
- **Members**:
  - `Method{Name, Params, Returns, Pres, Posts, Body}`
  - `Function{Name, Params, RetType, Pres, Posts, Body}` (pure)
  - `Predicate{Name, Params, Body}`
  - `Field{Name, Type}`
  - `Domain{Name, Functions, Axioms, TypeVars}`
- **Statements**: `Seqn`, `LocalVarAssign`, `FieldAssign`, `Inhale`, `Exhale`,
  `Assert`, `Assume`, `Fold`, `Unfold`, `Goto`, `Label`, `If`, `While`,
  `MethodCall`, `NewStmt`, `Apply`, `Package`
- **Expressions**: `LocalVar`, `Result`, `FieldAccess`, `PredicateAccess`,
  `Unfolding`, `Applying`, `Old`, `LabelledOld`, `Conditional`,
  `Let`, `Forall`, `Exists`, `InhaleExhaleExp`, `WildcardPerm`,
  `FullPerm`, `NoPerm`, `FractionalPerm`, `PermMinus`, `PermAdd`, `PermMul`, `PermDiv`,
  `PermGtCmp`, `PermGeCmp`, `PermLtCmp`, `PermLeCmp`, `CurrentPerm`,
  `EpsilonPerm`, `AccessPredicate`, `PredicateAccessPredicate`,
  arithmetic, comparison, boolean, `IntLit`, `BoolLit`, `NullLit`,
  `FuncApp`, `DomainFuncApp`, sequence/set/multiset operations
- **Types**: `Int`, `Bool`, `Perm`, `Ref`, `SeqType`, `SetType`, `MultisetType`,
  `MapType`, `DomainType`, `FunctionType`
- **Info chain** (backend annotations): Silver nodes carry an `Info` field. Two info types
  are needed beyond `NodeInfo` (which is a Go-level concept layered on top):
  - `NoInfo` — the empty info; used when no annotation is needed
  - `AnnotationInfo{Key string, Values []string}` — carries a backend annotation such as
    `@opaque` or `@reveal` (see plan 27)
  - `ConsInfo{Head Info, Tail Info}` — chains multiple info objects together
  The Go Silver AST must model these: `NodeInfo` is stored alongside a `VprInfo interface`
  that represents the Viper-level info chain (`NoInfo | AnnotationInfo | ConsInfo`).
  The Protobuf serializer (plan 16) embeds `NodeInfo` fields directly on each Protobuf message; the `VprInfo` chain is serialized separately for backend annotation fields such as `@opaque`/`@reveal`.

## Deliverables

- `internal/silver/ast.go` — all Silver Go type definitions, including the `Member` interface
  (see below) and the `Node` interface
- `internal/silver/printer.go` — `Print(prog *Program) string` producing valid `.vpr` text
- Tests: construct a simple Silver program in Go structs; print it; compare the output against
  a known-good `.vpr` file using string equality (no ViperServer dependency at this stage —
  offline validation of the printer output is sufficient for Plan 14)

### `Member` interface

Silver top-level members (methods, functions, predicates, fields, domains) share no common
Scala supertype that maps cleanly to Go, so Go-Gobra defines its own:

```go
// Member is implemented by every Silver top-level declaration:
// *Method, *Function, *Predicate, *Field, *Domain.
type Member interface {
    Node
    memberNode() // unexported sentinel — prevents accidental external implementation
    MemberName() string
}
```

All five concrete types implement `Member`. The `Selection func(Member) bool` field in
`ChopConfig` (plan 16b) uses this interface to identify important members.

## Position and Error-Backtranslation Design

Each Silver Go node carries a `NodeInfo` struct (not a bare `GoPos`). This is the Go
equivalent of Scala Gobra's `Source.Verifier.Info`:

```go
type NodeInfo struct {
    File   string // Go source file path
    Line   int    // 1-based line in Go source
    Col    int    // 1-based column in Go source
    Tag    string // identifies which Go construct produced this node (see below)
    NodeID uint64 // unique node ID assigned by plan 16's serializer; 0 for synthetic nodes
}
```

**`NodeID` assignment**: the Protobuf serializer (plan 16) assigns a globally unique `uint64`
ID to each Silver node during `Serialize()`, stores it as the `node_id` field of the
corresponding Protobuf message, and records the mapping `NodeID → silver.Node` in
`SerializedProgram.NodeMap`. `SilverServer` propagates these IDs through to
`VerifyResponse.Error.node_id`. When Silicon reports an error, the Go worker reads the
`node_id` from the gRPC response and looks up `NodeMap[id]` to retrieve the Go Silver struct
for `SearchInfo` DFS (plan 32). The primary position (`File`, `Line`, `Col`, `Tag`) comes
from the `node_file`, `node_line`, `node_col`, `node_tag` Protobuf fields on the error
response — no `AnnotationInfo` chain extraction is needed. Synthetic nodes carry `NodeID = 0`;
the worker skips the `NodeMap` lookup for ID 0.

**NodeInfo travels as Protobuf fields**: every Silver node message in `silver.proto` carries
`node_id`, `node_file`, `node_line`, `node_col`, and `node_tag` as scalar Protobuf fields.
There are no `AnnotationInfo` chains and no `ConsInfo` structures for position data. The Go
Silver AST's `NodeInfo` struct (above) is the single source of truth; the serializer copies it
into these fields. The `VprInfo` field on Go Silver nodes is used only for backend-specific
annotations (e.g., `@opaque`/`@reveal` for plan 27), not for position data.

**Why `Tag`?** When Silicon reports an error on a Silver node, the error type alone (e.g.,
"precondition might not hold") is not enough to generate the right Go-level message. A
precondition violation could come from a Go function call, a fold/unfold, an interface method
dispatch, a loop invariant check-on-entry, etc. The `Tag` string encodes which case applies,
so the reporter can dispatch to the correct error message without needing to inspect the Silver
AST structure. Tags are short identifiers like `"call"`, `"fold"`, `"return"`, `"loop-inv"`,
`"assert"`, `"exhale"`. Each encoding plan (19–31) defines the tags it uses.

**Synthesized nodes:** Translator-internal nodes with no direct Go source correspondence (e.g.,
auxiliary domain functions, helper Silver fields) carry a `NodeInfo` with an empty `File` and
`Tag = "synthetic"`. The reporter's `SearchInfo` function (see plan 32) walks a Silver AST
subtree **downward** (DFS over children) to find the nearest non-synthetic descendant's `NodeInfo`
when the immediate node's `NodeInfo` is synthetic. Parent pointers are not required.

**Children() method for DFS:** `SearchInfo` requires the ability to enumerate a node's
children. Every Silver node type must implement a `Children() []Node` method returning its
direct child nodes in declaration order. This method is also used by the chopper (plan 16b)
and the Silver printer (plan 14). Add `Children() []Node` to the `Node` interface in
`internal/silver/ast.go` and implement it on every concrete Silver node type. Structural
(non-leaf) nodes return their child slices concatenated; leaf nodes (IntLit, BoolLit,
NullLit, LocalVar, etc.) return nil. A missing `Children()` implementation on a new node
type is a compile error (the interface is not satisfied), so the requirement is
self-enforcing.

**`NodeInfo()` method on `Node` interface:** The reporter's `extractInfo` (plan 32) calls
`node.NodeInfo()` on a `silver.Node` interface value. Add `NodeInfo() NodeInfo` to the `Node`
interface alongside `Children() []Node`. Every concrete Silver node type stores a `NodeInfo`
field and returns it from this method. The full `Node` interface is therefore:

```go
type Node interface {
    Children() []Node
    NodeInfo() NodeInfo
}
```

Both methods must be implemented on every concrete Silver node type.

**Design invariant**: nodes that Silicon can directly cite as `offendingNode` (Assert, Exhale,
MethodCall, FieldAccess, etc.) must always carry a non-synthetic `NodeInfo`. Only structural
wrapper nodes (Seqn generated for control flow, synthetic If for desugaring) may be synthetic.
`SearchInfo` is a fallback for unexpected cases, not the primary path.

**Silver debug position:** For debugging translator output (e.g., pretty-printing with Silver
line numbers), the pretty-printer assigns Silver line numbers as it serializes the AST. No
Silver position field is needed on Go Silver nodes themselves.

**`NodeInfo.File` must be non-empty** on every non-synthetic Silver node. (NodeInfo is a
Go value-type struct, not a pointer — it cannot be nil. The constraint is on the `File` field:
every node must have a non-empty `NodeInfo.File` except synthetic nodes, which carry
`NodeInfo{Tag: TagSynthetic}` with `File == ""`.)

**Single NodeInfo storage**: each Go Silver struct carries exactly one `NodeInfo` field. The
serializer (plan 16) copies this into the Protobuf message's `node_file`/`node_line`/
`node_col`/`node_tag` scalar fields. `SilverServer` propagates these fields in
`VerifyResponse.Error`. There is no `AnnotationInfo` chain and no Java-side position
embedding — the Protobuf fields are the cross-language transport. The Go-side `NodeInfo` field
is also used by `searchInfo` DFS via `Children()` calls (plan 32).

**Centralized tag registry:** The `Tag` strings set during translation (`"call"`, `"fold"`,
`"return"`, etc.) are consumed by the reporter's `(errorType, tag)` dispatch table.

`TagSynthetic = "synthetic"` is the one exception: it is defined here in plan 14's
`internal/silver/ast.go` (a `const`) because plan 14's own C9 factory preconditions reference
it, and plan 14 must not import plan 32 (which would be circular: plan 32 depends on plan 14).

All other tag constants (`TagCall`, `TagFold`, `TagReturn`, `TagLoopInv`, `TagAssert`, etc.)
are defined in `internal/reporting/tags.go` (owned by plan 32). Encoding modules import those
constants from plan 32's package when constructing `NodeInfo`. No bare string literals for tags
anywhere outside `ast.go` (for `TagSynthetic`) and `tags.go` (for all others).
See plan 32 for the full constant definitions and the canonical list of tags.

### Verification Specifications (C9)

The Silver IR generation module proves structural validity via Gobra predicates on the node
types and postconditions on factory functions.

1. **Non-nil NodeInfo.File on non-synthetic nodes**: Every factory that constructs a non-synthetic
   Silver node must guarantee a non-empty `File` field:
   ```go
   //@ requires info.File != "" || info.Tag == TagSynthetic
   //@ ensures  n != nil && n.NodeInfo().File == info.File
   func NewAssert(exp Exp, info NodeInfo) (n *Assert)

   //@ requires info.File != "" || info.Tag == TagSynthetic
   //@ ensures  n != nil && n.NodeInfo().File == info.File
   func NewMethod(name string, params, returns []*LocalVarDecl,
                  pres, posts []Exp, body *Seqn, info NodeInfo) (n *Method)
   ```

2. **Acyclicity of expression trees**: Silver expressions form a DAG (no cycles). This is
   expressed via a recursive predicate that the translator (plan 19) must establish on every
   `Exp` it constructs:
   ```go
   //@ pred acyclicExp(e Exp, visited set[Exp])
   // acyclicExp(e, visited) holds when e ∉ visited and
   // for every direct child c of e: acyclicExp(c, visited ∪ {e})
   ```
   Factory functions for compound expressions carry the predicate as a postcondition:
   ```go
   //@ requires acyclicExp(left, set[Exp]{}) && acyclicExp(right, set[Exp]{})
   //@ ensures  acyclicExp(result, set[Exp]{})
   func NewAdd(left, right Exp, info NodeInfo) (result *BinExp)
   ```

3. **Children() completeness**: The `Node` interface requires `Children() []Node`. Every
   structural node's `Children()` implementation must return exactly the node's direct
   child nodes (no extras, no omissions). This is self-enforcing via the interface at compile
   time, but the precondition for `SearchInfo`'s DFS is that every node it reaches via
   `Children()` satisfies `acyclicExp` / is accessible:
   ```go
   //@ requires acc(n, 1/2)
   //@ ensures  acc(n, 1/2) && forall i int :: 0 <= i && i < len(result) ==> result[i] != nil
   func (n *Method) Children() (result []Node)
   ```

4. **`acyclicProg` predicate** — extends `acyclicExp` to top-level programs for use in
   plan 16's termination spec:
   ```go
   //@ pred acyclicProg(prog *Program, visited set[Node])
   // acyclicProg(prog, visited) holds when prog ∉ visited and
   // for every member m of prog: acyclicExp(m, visited ∪ {prog})
   ```
   Factory `NewProgram` carries the postcondition:
   ```go
   //@ ensures acyclicProg(result, set[Node]{})
   func NewProgram(domains []*Domain, fields []*Field, functions []*Function,
                   predicates []*Predicate, methods []*Method) (result *Program)
   ```

5. **Ghost `NodeCount()` method** — used by plan 16's termination spec. Defined alongside
   the Silver AST types in `internal/silver/ast.go`:
   ```go
   //@ pure
   //@ ensures result >= 0
   func (p *Program) NodeCount() (result int)
   ```
   Counts all `Node` values reachable from `p` via `Children()` traversal.

