# 04 — Go Parser Integration

## Objective

Implement the first parsing pass: use `go/parser` from the stdlib to parse `.go` and `.gobra`
files into a `go/ast` tree, then convert that tree into Gobra frontend AST nodes (03). Comment
nodes containing `//@ ...` annotations are preserved for the annotation mini-parser (05) to
process in a second pass.

## Scope

**In scope:**
- Drive `go/parser.ParseFile` with `parser.ParseComments` mode
- Walk the `go/ast` tree and produce Gobra frontend AST nodes
- Collect all `//@ ...` comment groups and attach them (by position) to the AST nodes they
  annotate, so 05 can parse them
- Handle `.gobra` files (treated as Go with relaxed import rules — already handled upstream by
  the Gobrafier in 06, but the parser must accept the post-processed form)
- Error recovery: surface parse errors as structured diagnostics; accumulate all errors in
  a file rather than stopping at the first. If a file has parse errors, return partial AST
  and all errors — do not abort; the caller (07, 33) decides whether to continue.

**Out of scope:**
- Parsing annotation expressions (05-annotation-parser.md)
- Preprocessing `.go` files (06-gobrafier.md) — the parser receives already-preprocessed input
- Type checking (08)

## Dependencies

- [03-frontend-ast.md](03-frontend-ast.md) — target AST types
- [06-gobrafier.md](06-gobrafier.md) — must preprocess input files before this parser sees
  them; `.go` files pass through the Gobrafier first; `.gobra` files are handled directly

## Reference: Current Gobra

- `src/main/scala/viper/gobra/frontend/Parser.scala` — main parser; uses ANTLR4 grammar +
  Kiama; the Go rewrite replaces this entirely with `go/parser` + a custom annotation parser
- `src/main/antlr4/` — the ANTLR4 grammar; useful as a reference for which constructs must
  be handled, even though it won't be used directly
- `src/main/scala/viper/gobra/frontend/ParseTreeTransformer.scala` — shows how parse tree
  nodes map to frontend AST nodes; the Go walk of `go/ast` is the equivalent

## Key Implementation Notes

- `go/parser` produces a `*ast.File`; walk it with `ast.Inspect` or a custom `ast.Visitor`
- Comment association: `go/ast` attaches comments to `ast.File.Comments`; match them to
  enclosing nodes by comparing `token.Pos` ranges
- Position translation: `go/token.FileSet` maps `token.Pos` values to file/line/column;
  store the `FileSet` alongside the AST for later error reporting
- `.gobra` files may contain Gobra-specific syntax not in Go (e.g., ghost type declarations);
  the Gobrafier (06) transforms these to legal Go before this parser sees them

## Deliverables

- `internal/frontend/parser.go` — `ParseFile(path string) (*frontend.PFile, error)`
- Association of raw `//@ ...` comment strings to their enclosing AST scope
- Structured parse error type with file/line/column
- Tests: parse a representative set of `.go` and `.gobra` files from
  `src/test/resources/regressions/` and verify the AST shape

## Open Questions

- How to handle Gobra ghost type declarations in `.gobra` files that aren't valid Go? The
  Gobrafier should strip or transform them first; confirm the handoff contract.
