# 05 — Annotation Mini-Parser

## Objective

Implement a custom recursive-descent parser for `//@ ...` annotation expressions. The Go parser
(04) extracts raw comment strings; this parser turns them into typed frontend AST nodes (03).

## Scope

**In scope:**
- Lexer for annotation tokens (keywords, identifiers, operators, literals, punctuation).
  **Input is already `//@ `-prefix-stripped and multi-line-concatenated by plan 04** (see
  `RawAnnotation.Text`). The lexer does NOT strip prefixes or concatenate lines.
- Recursive-descent parser producing frontend AST specification nodes
- Source position tracking relative to the comment's position in the file (so error messages
  point to the right line/column within the annotation, not just the comment start)
- **Ghost AST type definitions** in `internal/ast/frontend/` (plan 05 owns these; plan 03
  defines wrapper types for Go constructs, plan 05 defines the ghost-specific types):
  - File-scope declaration nodes: `PAdtType`, `PAdtClause`, `PGhostFunc`, `PPredDecl`
    (all implement `PDecl` so they can be stored in `PFile.GhostDecls`)
  - Ghost statement nodes (implement `PStmt` for interleaving in `PBlockStmt.Stmts`):
    `PFold`, `PUnfold`, `PAssert`, `PAssume`, `PExhale`, `PInhale`, `PPackageWand`, `PApplyWand`
  - Inline pattern nodes (implement `PStmt`):
    `PGhostArgs` (ghost call arguments), `PGhostResult` (ghost return result), `PGhostAssign`
    (ghost assignment replacing a Go assignment)
  - Ghost expression nodes (implement `PNode`):
    `PForall`, `PExists`, `PAccess`, `POld`, `PBefore`, `PUnfolding`, `PPermission`,
    sequence/set/multiset/dict/option literals and operations
  - Visitor interface extensions: one `VisitP*` method per ghost type above, added to
    the `Visitor` interface in `internal/ast/frontend/` (extending the base set from plan 03)
- Full coverage of the Gobra annotation language as decided in 02:
  - Contract clauses: `requires`, `ensures`, `preserves`
  - Loop invariants: `invariant`
  - Termination measures: `decreases`
  - Permission expressions: `acc(e)`, `acc(e, p)`, `wildcard`, `write`, `none`
  - Assertion operators: `&&`, `||`, `==>`, `!`, `--*`
  - Quantifiers: `forall x: T :: P`, `exists x: T :: P`
  - Ghost statements: `fold`, `unfold`, `assert`, `assume`, `exhale`, `inhale`
  - Magic wands: `package`, `apply`
  - Old/before: `old(e)`, `before(e)`
  - Type assertions in specs
  - Sequence/set/multiset/dict/option literals and operations
  - ADT constructors and `match` expressions
  - `pure`, `ghost`, `trusted`, `opaque` modifiers
  - Labeled old: `old[l](e)`
  - Permission fractions: `1/2`, `p/q`
  - `unfolding e in e`
  - Ghost fields, ghost methods, ghost return values

**Out of scope:**
- Parsing standard Go syntax (that's `go/parser` in 04)
- Type-checking the annotation expressions (08, 09)

## Dependencies

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type returned by `ParseAnnotation`
- [02-annotation-syntax-decision.md](02-annotation-syntax-decision.md) — grammar decision and the `## Annotation Grammar` target section (written by this plan)
- [03-frontend-ast.md](03-frontend-ast.md) — target AST node types for spec expressions

## Reference: Current Gobra

- `src/main/antlr4/GobraParser.g4` — the definitive grammar for all annotation constructs;
  use this as the specification for the recursive-descent parser
- `src/main/antlr4/GobraLexer.g4` — token definitions
- `src/main/scala/viper/gobra/frontend/ParseTreeTransformer.scala` — shows how parse tree
  nodes map to AST nodes for spec expressions

## Key Implementation Notes

- A hand-written recursive-descent parser is appropriate here: the annotation grammar is
  unambiguous, operator precedence is well-defined, and it avoids a parser-generator dependency
- **Input format**: `ParseAnnotation` receives `RawAnnotation.Text` from plan 04 — already
  `//@ `-prefix-stripped and multi-line-concatenated with `\n` separators. The lexer starts
  directly at the annotation keyword; it must NOT attempt to strip `//@ ` again.
- Position reconstruction: `base token.Pos` is `RawAnnotation.Pos` (position of the first
  `//@ ` line). Each token's position is `base + byteOffsetWithinText`. Newlines in
  concatenated multi-line annotations advance the line counter; the lexer must track line
  breaks within `Text` to report accurate line/column positions.
- Operator precedence (low to high) — **must be verified against `GobraParser.g4` before
  implementation; mark each level ✓ in the verification gate below**:
  `--*` (magic wand), `==>`, `||`, `&&`, comparison
  (`==`, `!=`, `<`, `<=`, `>`, `>=`), additive (`+`, `-`), multiplicative (`*`, `/`, `%`),
  unary prefix (including `!`, unary `-`, `old`, `before`, `unfolding`), postfix/primary
- Note: `!` is a unary prefix operator, not a binary one; it belongs at the unary level,
  not between `&&` and comparison

**Operator Precedence Verification Gate** (blocking deliverable before implementation):

Before writing the recursive-descent parser, cross-check each precedence level against the
corresponding grammar rule in `GobraParser.g4`. Record the result here:

| Precedence level | GobraParser.g4 rule | Checked |
|---|---|---|
| `--*` (magic wand / separating impl.) | _to be filled_ | ☐ |
| `==>` (implication) | _to be filled_ | ☐ |
| `\|\|` | _to be filled_ | ☐ |
| `&&` | _to be filled_ | ☐ |
| comparison (`==`, `!=`, `<`, `<=`, `>`, `>=`) | _to be filled_ | ☐ |
| additive (`+`, `-`) | _to be filled_ | ☐ |
| multiplicative (`*`, `/`, `%`) | _to be filled_ | ☐ |
| unary prefix (`!`, `-`, `old`, `before`, `unfolding`) | _to be filled_ | ☐ |
| postfix / primary | _to be filled_ | ☐ |

Any divergence from the ANTLR4 grammar is a bug — fix it before proceeding. This table must
be fully checked (all ☑) before plan 05 is marked complete.

## Deliverables

- Ghost AST type definitions in `internal/ast/frontend/` — all ghost node types listed in
  the In-scope section above (plan 05 is the owner; these extend the package begun by plan 03)
- `internal/frontend/annotationparser.go` —
  ```go
  func ParseAnnotation(
      src        string,     // RawAnnotation.Text: already stripped, already concatenated
      base       token.Pos,  // RawAnnotation.Pos: position of first //@ line
      isFileScope bool,      // true when parsing a nil-key (file-scope) annotation block
  ) (nodes []PNode, decls []PDecl, diags []Diagnostic)
  ```
  - When `isFileScope` is false: all produced nodes go into `nodes` (`[]PNode`). These are
    spec clauses (`PRequires`, `PEnsures`, etc.), ghost statements (`PFold`, `PAssert`, etc.),
    or inline pattern nodes (`PGhostArgs`, `PGhostAssign`, etc.).
  - When `isFileScope` is true: top-level ghost declarations (`PAdtType`, `PGhostFunc`,
    `PPredDecl`) go into `decls` (`[]PDecl`); any unexpected non-declaration annotation
    returns a diagnostic and is not placed in either slice.
  - `diags` is never nil (empty slice on success). Non-empty `diags` does not prevent
    returning nodes/decls — callers decide whether to abort.
  - Callers: plan 07's `MergeGhostStatements` passes `isFileScope=true` for the nil-key
    annotations and `isFileScope=false` for all block-keyed annotations.
- Full grammar coverage of the Gobra annotation language
- **`## Annotation Grammar` section written into `02-annotation-syntax-decision.md`** — this
  is a primary deliverable of plan 05. Once the parser is implemented and tested, extract the
  complete BNF/EBNF grammar from the implementation and write it into plan 02's `## Annotation
  Grammar` section. This section is plan 05's responsibility to produce; plan 02 records the
  decision and hosts the section. Write it while the ANTLR4 files (`GobraParser.g4`,
  `GobraLexer.g4`) are still accessible as reference — do not defer to cut-over.
- Position-accurate error messages (column relative to comment start)
- Table-driven tests covering each annotation form, including error cases

## Expanded Scope: Top-Level Ghost Declarations in `.gobra` Files (Resolved)

The Gobrafier (plan 06) transforms `.gobra`-specific top-level syntax into `//@ ` comments
at file scope (e.g., `adt`, `ghost func`, `pred` declarations become `//@ ...` comment blocks
plus blank Go stubs). The annotation parser must therefore handle not only inline spec clauses
but also **top-level ghost declarations** when they appear in file-scope `//@ ` comment blocks:

- `//@ adt T { C1{...}; C2{...} }` → `PAdtType` declaration node
- `//@ ghost func f(x T) T { body }` → `PGhostFunc` declaration node
- `//@ pred P(x *int) { body }` → `PPredDecl` declaration node

These file-scope annotations are recognized by their position (comment group immediately before
or not associated with any Go declaration) and by their leading keyword. The parser should
dispatch on the first token of each `//@ ` block at file scope to determine whether it is an
inline spec clause or a top-level ghost declaration.

**`PDecl` requirement for file-scope ghost declarations:** The three node types produced above
(`PAdtType`, `PGhostFunc`, and `PPredDecl`) are **file-scope declaration nodes** — they will be
stored in `PFile.GhostDecls []PDecl` (plan 03). All three must therefore implement both `PNode`
and `PDecl` (the marker interface defined in plan 03). With the split-return signature, these
appear in `decls []PDecl` directly — no type assertion needed in plan 07. Inline spec nodes
(`PFunctionSpec`, `PExpression`, ghost statement nodes, etc.) go into `nodes []PNode` and
are NOT stored in `GhostDecls`.

## Inline Annotation Positional Patterns (Complete List)

Plan 02 notes four specific inline patterns where a `//@ ...` comment appears on the same
source line as the Go expression it annotates. These are NOT standalone spec clauses — the
Gobrafier (plan 06) recognizes them by regex and strips the Go-side syntax, leaving the
`//@ ...` portion for this parser. The annotation parser must handle all four:

| Pattern | Example source | After Gobrafier | AST node produced |
|---------|----------------|-----------------|-------------------|
| Ghost argument injection at call site | `f(args) //@ with ghostArgs` | `//@ with ghostArgs` | `PGhostArgs{...}` attached to enclosing call |
| Ghost result at return | `return x, y //@ with ghostResult` | `//@ with ghostResult` | `PGhostResult{...}` attached to enclosing return |
| Ghost assignment annotation | `x := rhs //@ as ghost_assignment` | `//@ as ghost_assignment` | `PGhostAssign{...}` replacing the Go assignment |
| Inline unfolding | `f(x) //@ unfolding P(x)` | `//@ unfolding P(x)` | `PUnfolding{...}` wrapped around the call expression |

These patterns appear only as single-line `//@ ` annotations whose `Pos` is on the same line
as the preceding Go statement. The annotation parser distinguishes them from standalone spec
clauses by their leading keyword (`with`, `as`, `unfolding` at the start of the annotation
text after prefix-stripping).

**Position note:** The Gobrafier preserves line counts when stripping inline annotations, so
error positions within these annotations can be recovered from the original source line.

## Grammar Sketch Ownership

Writing the `## Annotation Grammar` section in `02-annotation-syntax-decision.md` is a
**primary deliverable of plan 05** (not just a timing note). The grammar sketch must be:
- Derived from the implemented parser, not written ahead of it
- Written while `GobraParser.g4` and `GobraLexer.g4` are still accessible for cross-checking
- Complete before marking plan 05 done — it is a gate for plan 37 cut-over

Once the annotation parser is implemented and passing all table-driven tests, extract the
grammar into BNF/EBNF and write it into plan 02's `## Annotation Grammar` section. Cross-check
each production against the ANTLR4 source and mark each ✓. Any discrepancy is a bug in the
parser or the grammar — fix it before declaring plan 05 complete.

## Verification Specifications (C9)

`ParseAnnotation` is a pure transformation function (no shared mutable state, no JNI). Its
Gobra specifications focus on nil-safety, termination, and the `PDecl` subtype guarantee.

1. **Non-nil diagnostics postcondition**:
   ```go
   //@ ensures diags != nil
   func ParseAnnotation(src string, base token.Pos, isFileScope bool) (nodes []PNode, decls []PDecl, diags []Diagnostic)
   ```

2. **`PDecl` subtype guarantee** (when `isFileScope` is true, every element of `decls`
   implements `PDecl` — enforced by the return type; the spec makes it explicit):
   ```go
   //@ ensures isFileScope ==>
   //@     forall i int :: 0 <= i && i < len(decls) ==> decls[i] != nil
   ```

3. **Termination**: the recursive-descent parser terminates because each recursive call
   consumes at least one token, and the token stream is finite (bounded by `len(src)`):
   ```go
   //@ decreases len(src)
   func ParseAnnotation(src string, base token.Pos, isFileScope bool) (...)
   ```

4. **No aliasing**: the returned slices share no mutable state with each other or with `src`;
   callers may safely pass `nodes` and `decls` to concurrent downstream stages:
   ```go
   //@ ensures forall i int :: 0 <= i && i < len(nodes) ==> acc(nodes[i])
   //@ ensures forall i int :: 0 <= i && i < len(decls) ==> acc(decls[i])
   ```

## Resolved Questions

**Separate AST vs. direct frontend AST production (resolved):** The annotation parser produces
frontend AST specification nodes directly (the types defined in plans 03 and 05 —
`PFunctionSpec`, `PExpression`, ghost statement/expression types, etc.). There is no
intermediate annotation-specific AST that is merged in a later pass. Direct production is
simpler: fewer types to define, no merge step, and error positions are attached to nodes at
the point of construction.

**Annotations spanning constructs (resolved):** The parser does not attempt to resolve which
construct an annotation belongs to. It returns nodes; plan 07 routes them to the correct AST
node by source position. The type checker (plan 09) is responsible for validating structural
constraints — e.g., that `requires` appears only on function/method declarations, that
`invariant` appears only in loop bodies, and that `old(e)` appears only in postconditions.
