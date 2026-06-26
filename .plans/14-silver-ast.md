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
subtree upward to find the nearest non-synthetic ancestor's `NodeInfo` when the immediate
node's `NodeInfo` is synthetic.

**Silver debug position:** For debugging translator output (e.g., pretty-printing with Silver
line numbers), the pretty-printer assigns Silver line numbers as it serializes the AST. No
Silver position field is needed on Go Silver nodes themselves.

**`NodeInfo` must never be nil** on any Silver node. Synthetic nodes use the sentinel
`NodeInfo{Tag: "synthetic"}`; every other node must have a valid File, Line, and Col.
