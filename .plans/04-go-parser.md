# 04 — Go Parser Integration

## Objective

Implement the first parsing pass: use `go/parser` from the stdlib to parse `.go` and `.gobra`
files into a `go/ast` tree, then convert that tree into Gobra frontend AST nodes (03). Comment
nodes containing `//@ ...` annotations are preserved for the annotation mini-parser (05) to
process in a second pass.

## Scope

**In scope:**
- Drive `go/parser.ParseFile` with `parser.AllErrors | parser.ParseComments` mode, passing
  the preprocessed source bytes directly via `go/parser`'s `src interface{}` parameter —
  **no temp file needed**. `parser.AllErrors` is required to report all parse errors, not
  just the first ~10; without it the "accumulate all errors" goal is silently broken.
- Walk the `go/ast` tree and populate `PFile.Metadata` entries for nodes with Gobra extensions
  (function/method declarations get spec/receiver metadata; loop bodies get block mapping)
- Collect all `//@ ...` comment groups and record them in `PFile.BlockAnnotations`:
  - Each `go/ast.CommentGroup` of consecutive `//@ ` lines becomes **one** `frontend.RawAnnotation`
    (lines joined with `\n`, `//@ ` prefix stripped, `Pos` is the first line). Concatenation
    happens here so plan 05 always receives one logical annotation per `RawAnnotation`.
  - Each `RawAnnotation` is placed under the key of the **innermost enclosing `*ast.BlockStmt`**
    whose source range contains the comment's `Pos()`. Loop-spec annotations (`//@ invariant P`,
    `//@ decreases e`) that appear immediately before a `for` loop's `{` fall inside the
    **enclosing block** (the block containing the `for` statement), not the loop body. They
    are stored under that enclosing block's key; plan 07 routes them to the for/range node's
    `GobraMetadata.LoopSpec` during `MergeGhostStatements` (step 4, Rule A).
  - File-scope annotations (from `//@ ` blocks at the top level, produced by the Gobrafier
    for ghost ADTs / predicates / funcs) are stored under the **nil key**. Plan 07 detects
    the nil key and routes these to `PFile.GhostDecls`.
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

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type returned by `ParseFile`
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
- Comment association: `go/ast` attaches comments to `ast.File.Comments`; associate each
  `CommentGroup` with the innermost enclosing scope by comparing its `Pos()` against the
  source ranges of `PBlockStmt` nodes built during the walk
- Position tracking: the `*token.FileSet` is owned by plan 07 (one per package) and passed
  in as `fset`. `go/parser.ParseFile` records this file's byte range into `fset`.
  `ParseFile` must NOT create its own FileSet; doing so makes positions from different files
  incommensurable and breaks `go/types.Check` in plan 08, which requires a single `fset`
  covering all files in the package
- `.gobra` files may contain Gobra-specific syntax not in Go (e.g., ghost type declarations);
  the Gobrafier (06) transforms these to legal Go before this parser sees them

## Deliverables

- `type Diagnostic = diagnostic.Diagnostic` alias in `internal/frontend/parser.go`
  (per plan 00 cross-cutting convention; keeps `[]Diagnostic` signatures unqualified)
- `internal/frontend/parser.go` — `ParseFile(fset *token.FileSet, filename string, src []byte) (*frontend.PFile, []Diagnostic)`
  - `fset` is the `*token.FileSet` owned by plan 07 (one per package, shared across all
    files). Plan 07 creates it before the first `ParseFile` call; `go/parser.ParseFile`
    records each file's position range into `fset`. Plan 08 receives the same `fset` to call
    `go/types.Check`. **`ParseFile` must not create its own FileSet** — doing so makes
    per-file positions incommensurable and breaks `go/types.Check` for multi-file packages.
  - `filename` is the original source path (for position attribution in `fset` and error messages).
  - `src` is the Gobrafier-preprocessed content (passed directly to `go/parser.ParseFile` as
    the `src` parameter — no temp file).
  - **`PFile.BlockAnnotations` is populated by `ParseFile`** before returning. Plan 07 accesses
    annotations via `pfile.BlockAnnotations`; there is no separate annotation-map return value.
    The nil-key convention (file-scope annotations) is described in the In-scope bullet above.
    Plan 07's `MergeGhostStatements` must check `if key == nil { ... }` before the merge loop.
    Keys are `*ast.BlockStmt` (not `*PBlockStmt`); the `PBlockStmt` corresponding to each block
    is obtained via `pfile.Metadata[blockStmt].Block`.
  - **Recoverable parse errors**: return a *partial* `*frontend.PFile` alongside diagnostics.
    `go/parser` (in `AllErrors` mode) reports all errors and continues; callers (plan 07, plan 33)
    inspect diagnostics and decide whether to abort.
  - **Bad\* nodes** (see section below): return `nil` for `*frontend.PFile`. Diagnostics still returned.
  - In both cases `[]Diagnostic` is non-nil (empty slice on success, non-empty on any error).
  - **`PBlockStmt.Stmts` is NOT fully assembled here.** It contains only `*PGoStmt`-wrapped Go
    statements; plan 07 runs `MergeGhostStatements` to complete each `PBlockStmt`. Each
    `*ast.BlockStmt` in the `*ast.File` has a corresponding `PBlockStmt` stored in
    `pfile.Metadata[blockStmt].Block` — populated by `ParseFile` before returning.
- `frontend.RawAnnotation` type is defined in `internal/ast/frontend/` (plan 03):
  `{Text string; Pos token.Pos}` — the logical annotation text with `//@ ` prefix stripped,
  position of the first line. Multi-line annotations are pre-concatenated by `ParseFile`.
- `Diagnostic` type imported from `internal/diagnostic/` (owned by plan 32a; do NOT import
  `internal/reporting` — that package is downstream of the parser and would break pipeline
  layering). The `[]Diagnostic` return value uses the same type as all other early-pipeline
  stages (plans 05, 06, 07, 08) per the cross-cutting contract in plan 00.
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

## Verification Specifications (C9)

Plan 04's `ParseFile` is a pure transformation function (no shared mutable state, no JNI)
so its Gobra specifications focus on nil-safety, termination, and diagnostic completeness.

1. **`ParseFile` nil-safety postcondition**: the returned `*PFile` is nil iff Bad\* nodes
   were found; otherwise non-nil (possibly partial). `hasBadNode` is a ghost result:
   ```go
   //@ ensures hasBadNode ==> pfile == nil
   //@ ensures !hasBadNode ==> pfile != nil
   func ParseFile(fset *token.FileSet, filename string, src []byte) (pfile *frontend.PFile,
       diags []Diagnostic, /*@ ghost hasBadNode bool @*/)
   ```
   `hasBadNode` is set to `true` inside the `ast.Inspect` callback on any Bad\* node.
   Callers may ignore it (ghost results are invisible to non-Gobra callers).

2. **Diagnostic completeness**: every `go/parser` error token is reflected in the returned
   `[]Diagnostic`; the slice is never nil (empty slice on success):
   ```go
   //@ ensures diags != nil
   ```

3. **Termination**: `ParseFile` is non-recursive and terminates unconditionally. The
   `ast.Inspect` walk terminates because the `go/ast` tree is finite and acyclic:
   ```go
   //@ decreases
   func ParseFile(fset *token.FileSet, filename string, src []byte) (...)
   ```

4. **BlockAnnotations ownership**: after a successful call, `pfile.BlockAnnotations` and
   `pfile.Metadata` are fully populated and no other live reference to the maps exist;
   plan 07 holds exclusive access. In Gobra/Viper, map permission is granted on the whole
   map, not per-key:
   ```go
   //@ ensures pfile != nil ==> acc(pfile.BlockAnnotations)
   //@ ensures pfile != nil ==> acc(pfile.Metadata)
   ```

## Resolved Questions

**Ghost type declarations in `.gobra` files (resolved — see plan 06):** The Gobrafier
handles `.gobra` files with a dedicated transformation mode: ghost top-level constructs
(`adt`, `ghost func`, `pred`) are converted into `//@ ...` comment blocks plus blank Go stubs,
producing output that is valid Go and parseable by `go/parser`. This parser (plan 04) therefore
never sees raw `.gobra`-specific syntax; it always receives Gobrafier-preprocessed input.
The full handoff contract — including which constructs are transformed and how line counts are
preserved — is specified in plan 06 ("For `.gobra` files (resolved)" and "Position Mapping
Strategy").
