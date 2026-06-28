# 04 — Go Parser Integration

## Objective

Implement the first parsing pass: use `go/parser` from the stdlib to parse `.go` and `.gobra`
files into a `go/ast` tree, then convert that tree into Gobra frontend AST nodes (03). Comment
nodes containing `//@ ...` annotations are preserved for the annotation mini-parser (05) to
process in a second pass.

## Scope

**In scope:**
- Drive `go/parser.ParseFile` with `parser.ParseComments` mode, passing the preprocessed
  source bytes directly via `go/parser`'s `src interface{}` parameter — **no temp file needed**
- Walk the `go/ast` tree and produce Gobra frontend AST nodes
- Collect all `//@ ...` comment groups and attach them (by position) to the AST nodes they
  annotate, so 05 can parse them
- Handle `.gobra` files (treated as Go with relaxed import rules — already handled upstream by
  the Gobrafier in 06, but the parser must accept the post-processed form)
- Error recovery: surface parse errors as structured diagnostics; accumulate all errors in
  a file rather than stopping at the first. If a file has parse errors, return partial AST
  and all diagnostics — do not abort; the caller (07, 33) decides whether to continue.

**Out of scope:**
- Parsing annotation expressions (05-annotation-parser.md)
- Preprocessing `.go` files (06-gobrafier.md) — the parser receives already-preprocessed bytes
- Type checking (08)

## Dependencies

- [03-frontend-ast.md](03-frontend-ast.md) — target AST types
- [06-gobrafier.md](06-gobrafier.md) — must preprocess input files before this parser sees
  them; `.go` files pass through the Gobrafier first; `.gobra` files are handled directly

**Coordination model:** Plan 07 (Package Resolver) is the caller that coordinates the three
steps. For each file, plan 07 calls: (1) Gobrafier (06) to preprocess the file, receiving
preprocessed `[]byte` in return, then (2) this parser's `ParseFile(filename, src)` with the
original filename and preprocessed bytes (no temp file), then (3) the annotation parser (05)
on the collected `//@ ` comment strings. `ParseFile` does NOT call the Gobrafier internally —
it receives already-preprocessed bytes. Plan 06 is listed as a dependency because the Gobrafier
must exist before plan 07 can orchestrate the full pipeline, not because `ParseFile` calls it.

`go/parser.ParseFile` accepts a `src interface{}` parameter; passing `[]byte` makes it parse
from memory rather than the filesystem. The `filename` argument is still required for
`token.FileSet` position attribution and error-message formatting — pass the original source
file path, not any temp-file path.

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

- `internal/frontend/parser.go` — `ParseFile(filename string, src []byte) (*frontend.PFile, map[*frontend.PBlockStmt][]RawAnnotation, []Diagnostic)`
  - `filename` is the original source path (for position tracking and error messages).
  - `src` is the Gobrafier-preprocessed content (passed directly to `go/parser.ParseFile` as the `src` parameter — no temp file).
  - Returns a partial `*frontend.PFile` with Go-only `PBlockStmt` nodes (ghost statements not yet interleaved), a per-block map of raw `//@ ` annotation strings, and all parse-error diagnostics. A non-empty `[]Diagnostic` does not necessarily mean `*frontend.PFile` is nil; callers decide whether to abort.
  - **`PBlockStmt.Stmts` is NOT fully assembled here.** It contains only `PGoStmt`-wrapped Go statements at this point. Plan 07 calls plan 05 on the returned `map` and runs `MergeGhostStatements` to complete each `PBlockStmt` (step 4 of the coordination model in plan 07).
  - **File-scope annotations** (those not inside any block — produced by the Gobrafier for ghost ADTs, predicates, funcs) are collected into `map[*frontend.PBlockStmt][]RawAnnotation` under the key `nil`. Plan 07 detects the `nil` key and routes these to `PFile.GhostDecls` instead of any `PBlockStmt`. **Callers must always check for the nil key explicitly** — iterating the map with `range` visits the nil key, but a range loop body that type-asserts the key as non-nil will panic. Plan 07's `MergeGhostStatements` handles this with an explicit `if key == nil { ... }` branch before the merge loop.
- `RawAnnotation` type: `{Text string; Pos token.Pos}` — the raw `//@ ...` text stripped of the `//@ ` prefix, with its source position
- Structured `Diagnostic` type re-exported from `internal/reporting` (see plan 00 cross-cutting contract)
- Tests: parse a representative set of `.go` and `.gobra` files from
  `src/test/resources/regressions/` and verify the AST shape

## Bad* Node Rejection

`go/parser` runs in error-recovery mode by default (and `go/packages` always uses this mode).
When a file contains syntax errors, the parser produces `*ast.BadStmt`, `*ast.BadExpr`, or
`*ast.BadDecl` placeholder nodes rather than aborting. These implement `ast.Node` and can
appear anywhere in the `*ast.File` tree.

`ParseFile` must detect and reject them explicitly. After calling `go/parser.ParseFile`,
walk the result with `ast.Inspect` and check for any `*ast.BadStmt`, `*ast.BadExpr`, or
`*ast.BadDecl` node. For each one found, emit a `Diagnostic` at its `Pos()` with message
`"syntax error"`. Do not attempt to translate a `*ast.File` that contains Bad* nodes — return
`nil` for the `*frontend.PFile` and the accumulated diagnostics.

This is separate from the parse errors already returned in `(*ast.File).Comments` /
`token.FileSet` error lists; Bad* node detection is a belt-and-suspenders check to ensure
no malformed node reaches the desugarer. Do not add explicit handling for Bad* nodes in the
desugarer (plan 12) or the catch-all panic (plan 19) — they must never reach that far.

## Resolved Questions

**Ghost type declarations in `.gobra` files (resolved — see plan 06):** The Gobrafier
handles `.gobra` files with a dedicated transformation mode: ghost top-level constructs
(`adt`, `ghost func`, `pred`) are converted into `//@ ...` comment blocks plus blank Go stubs,
producing output that is valid Go and parseable by `go/parser`. This parser (plan 04) therefore
never sees raw `.gobra`-specific syntax; it always receives Gobrafier-preprocessed input.
The full handoff contract — including which constructs are transformed and how line counts are
preserved — is specified in plan 06 ("For `.gobra` files (resolved)" and "Position Mapping
Strategy").
