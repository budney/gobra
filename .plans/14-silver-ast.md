# 14 — Silver IR (Go Structs)

## Objective

Define Go struct types for the Silver intermediate representation. These types are the output
of the translator and the input to the JNI builder (16). They mirror the Silver AST defined
in the `silver` submodule's Scala types, but expressed purely in Go.

## Scope

**In scope:**
- Go types for every Silver node: programs, domains, fields, functions, predicates, methods,
  statements, expressions, types, and error positions
- A pretty-printer that serializes the Go Silver AST to valid `.vpr` text (useful for
  debugging and potentially as a fallback verification path)
- Position information on every node (maps to Go source for error reporting)

**Out of scope:**
- JNI construction of Java Silver objects (16-silver-jni-builder.md)
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
  `SilverBridge.java` exposes methods to construct each type (see plan 16).

## Deliverables

- `internal/silver/ast.go` — all Silver Go type definitions
- `internal/silver/printer.go` — `Print(prog *Program) string` producing valid `.vpr` text
- Tests: construct a simple Silver program in Go structs; print it; verify the output is
  valid Silver by passing it to ViperServer

## Position and Error-Backtranslation Design

Each Silver Go node carries a `NodeInfo` struct (not a bare `GoPos`). This is the Go
equivalent of Scala Gobra's `Source.Verifier.Info`:

```go
type NodeInfo struct {
    File string // Go source file path
    Line int    // 1-based line in Go source
    Col  int    // 1-based column in Go source
    Tag  string // identifies which Go construct produced this node (see below)
}
```

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

**Design invariant**: nodes that Silicon can directly cite as `offendingNode` (Assert, Exhale,
MethodCall, FieldAccess, etc.) must always carry a non-synthetic `NodeInfo`. Only structural
wrapper nodes (Seqn generated for control flow, synthetic If for desugaring) may be synthetic.
`SearchInfo` is a fallback for unexpected cases, not the primary path.

**Silver debug position:** For debugging translator output (e.g., pretty-printing with Silver
line numbers), the pretty-printer assigns Silver line numbers as it serializes the AST. No
Silver position field is needed on Go Silver nodes themselves.

**`NodeInfo` must never be nil** on any Silver node. Synthetic nodes use the sentinel
`NodeInfo{Tag: "synthetic"}`; every other node must have a valid File, Line, and Col.

**Centralized tag registry:** The `Tag` strings set during translation (`"call"`, `"fold"`,
`"return"`, etc.) are consumed by the reporter's `(errorType, tag)` dispatch table (plan 32).
To prevent typos from silently falling through to the generic error message, define all valid
tag strings as named constants in `internal/reporting/tags.go`:

```go
package reporting

const (
    TagCall        = "call"
    TagReturn      = "return"
    TagField       = "field"
    TagAssert      = "assert"
    TagLoopInv     = "loop-inv"
    TagFold        = "fold"
    TagExhale      = "exhale"
    TagTermination = "termination"
    TagOverflow    = "overflow"
    TagSynthetic   = "synthetic"
    // Each encoding plan (20–31) adds its own tags here.
)
```

Encoding modules import `reporting.TagXxx` constants when constructing `NodeInfo`; the
reporter imports the same constants for its dispatch table. No bare string literals for tags
anywhere outside `tags.go`.
