# 05 — Annotation Mini-Parser

## Objective

Implement a custom recursive-descent parser for `//@ ...` annotation expressions. The Go parser
(04) extracts raw comment strings; this parser turns them into typed frontend AST nodes (03).

## Scope

**In scope:**
- Lexer for annotation tokens (keywords, identifiers, operators, literals, punctuation)
- Recursive-descent parser producing frontend AST specification nodes
- Source position tracking relative to the comment's position in the file (so error messages
  point to the right line/column within the annotation, not just the comment start)
- Full coverage of the Gobra annotation language as decided in 02:
  - Contract clauses: `requires`, `ensures`, `preserves`
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

**Out of scope:**
- Parsing standard Go syntax (that's `go/parser` in 04)
- Type-checking the annotation expressions (08, 09)

## Dependencies

- [02-annotation-syntax-decision.md](02-annotation-syntax-decision.md) — grammar is defined here
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
- The lexer should skip whitespace and handle `//@ ` prefix stripping
- Multi-line annotations: consecutive `//@ ` lines in the same comment block are concatenated
  before lexing, with synthetic newlines to preserve line numbers
- Operator precedence (low to high): `--*` (magic wand), `==>`, `||`, `&&`, comparison
  (`==`, `!=`, `<`, `<=`, `>`, `>=`), additive (`+`, `-`), multiplicative (`*`, `/`, `%`),
  unary prefix (including `!`, unary `-`, `old`, `before`, `unfolding`), postfix/primary
- Note: `!` is a unary prefix operator, not a binary one; it belongs at the unary level,
  not between `&&` and comparison

## Deliverables

- `internal/frontend/annotationparser.go` — `ParseAnnotation(src string, base token.Pos) (SpecNode, error)`
- Full grammar coverage of the Gobra annotation language
- Position-accurate error messages (column relative to comment start)
- Table-driven tests covering each annotation form, including error cases

## Expanded Scope: Top-Level Ghost Declarations in `.gobra` Files (Resolved)

The Gobrafier (plan 06) transforms `.gobra`-specific top-level syntax into `//@ ` comments
at file scope (e.g., `adt`, `ghost func`, `pred` declarations become `//@ ...` comment blocks
plus blank Go stubs). The annotation parser must therefore handle not only inline spec clauses
but also **top-level ghost declarations** when they appear in file-scope `//@ ` comment blocks:

- `//@ adt T { C1{...}; C2{...} }` → `PAdtType` declaration node
- `//@ ghost func f(x T) T { body }` → `PFunctionDecl` with ghost marker
- `//@ pred P(x *int) { body }` → predicate declaration node

These file-scope annotations are recognized by their position (comment group immediately before
or not associated with any Go declaration) and by their leading keyword. The parser should
dispatch on the first token of each `//@ ` block at file scope to determine whether it is an
inline spec clause or a top-level ghost declaration.

## Resolved Questions

**Separate AST vs. direct frontend AST production (resolved):** The annotation parser produces
frontend AST specification nodes directly (the types defined in plan 03 — `PFunctionSpec`,
`PAssertion`, `PGhostStatement`, etc.). There is no intermediate annotation-specific AST that
is merged in a later pass. Direct production is simpler: fewer types to define, no merge step,
and error positions are attached to nodes at the point of construction.

**Annotations spanning constructs (resolved):** The parser does not attempt to resolve which
construct an annotation belongs to. It produces nodes and attaches them to the enclosing AST
scope by source position. The type checker (plan 09) is responsible for validating structural
constraints — e.g., that `requires` appears only on function/method declarations, that
`invariant` appears only in loop bodies, and that `old(e)` appears only in postconditions.
