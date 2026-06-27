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

**Coordination model:** Plan 07 (Package Resolver) is the caller that coordinates the three
steps. For each file, plan 07 calls: (1) Gobrafier (06) to preprocess the file to a temp
location, then (2) this parser's `ParseFile` with the preprocessed path, then (3) the
annotation parser (05) on the collected `//@ ` comment strings. `ParseFile` does NOT call
the Gobrafier internally — it receives already-preprocessed input. Plan 06 is listed as a
dependency because the Gobrafier must exist before plan 07 can orchestrate the full pipeline,
not because `ParseFile` calls it.

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

## Resolved Questions

**Ghost type declarations in `.gobra` files (resolved — see plan 06):** The Gobrafier
handles `.gobra` files with a dedicated transformation mode: ghost top-level constructs
(`adt`, `ghost func`, `pred`) are converted into `//@ ...` comment blocks plus blank Go stubs,
producing output that is valid Go and parseable by `go/parser`. This parser (plan 04) therefore
never sees raw `.gobra`-specific syntax; it always receives Gobrafier-preprocessed input.
The full handoff contract — including which constructs are transformed and how line counts are
preserved — is specified in plan 06 ("For `.gobra` files (resolved)" and "Position Mapping
Strategy").
