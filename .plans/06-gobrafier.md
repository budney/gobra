# 06 — Go File Preprocessor (Gobrafier)

## Objective

Implement the preprocessing step that transforms standard `.go` files into a form that
`go/parser` can parse cleanly. This includes stripping or rewriting Gobra-specific syntax
that isn't valid Go (ghost declarations, `//@ ` annotations embedded in code positions, etc.).

## Scope

**In scope:**
- Read a raw `.go` file and produce a preprocessed version safe for `go/parser`
- Strip `//@ ` annotation lines from positions where they would confuse the parser
- Transform Gobra ghost type/variable declarations into valid Go (e.g., blank identifiers,
  build-tag-guarded stubs)
- Preserve source positions so that `token.Pos` values from the preprocessed file can be
  mapped back to the original file for error reporting
- Handle `.gobra` files differently from `.go` files (`.gobra` files are already in Gobra
  syntax; `.go` files need minimal transformation)

**Out of scope:**
- Parsing the annotations themselves (05-annotation-parser.md)
- Any semantic transformation (that's the desugarer)

## Dependencies

- [01-project-setup.md](01-project-setup.md) — repository must exist

The Gobrafier is a pure text preprocessor with no dependency on the parser. It is a
**prerequisite for** plan 04 (Go Parser Integration), not a downstream dependent of it.
The parser's dependency list includes 06; 06's dependency list does not include 04.

## Reference: Current Gobra

- `src/main/scala/viper/gobra/frontend/Gobrafier.scala` — the canonical implementation;
  study this carefully to understand every transformation applied
- `src/main/scala/viper/gobra/frontend/Parser.scala` — shows how Gobrafier output is handed
  to the parser

## Key Transformations (from Gobrafier.scala)

- Remove `//@ ...` lines that would appear inside expressions or statement positions
- Replace ghost statements (e.g., `//@ assert e`) with no-ops or blank statements
- Transform `ghost` keyword in declarations (not a Go keyword) into valid Go
- Handle `pure` function declarations
- Handle `trusted` modifier
- Handle Gobra import statements for ghost packages

## Position Mapping Strategy (Resolved)

The Gobrafier's primary position contract is: **preserve line count**. When content is removed
or transformed on a line, insert a `\n` to keep the preprocessed file's line numbers identical
to the original. This matches the Scala Gobrafier's approach (which explicitly adds `"\n"` in
each regex replacement to "maintain line consistency").

Consequence: errors reported by `go/parser` on the preprocessed file will have correct line
numbers pointing into the original source. Column positions within a line may shift when inline
ghost content is removed (e.g., `f(args) //@ with ghost_args` → `f(args)` shifts nothing
after the call, but ghost parameter lists inserted before `)` do shift trailing columns).

**Known limitation:** column positions within a transformed line are best-effort. This matches
the Scala implementation's behavior and is acceptable in practice because most Gobra error
messages are reported at line granularity, and the transformed content is typically at
end-of-line positions.

**For `.gobra` files (resolved):** The Scala Gobra passes `.gobra` files directly to the
ANTLR4 grammar, bypassing the Gobrafier entirely. The Go rewrite cannot do this — `go/parser`
cannot parse `.gobra`-specific syntax (`adt`, top-level `ghost func`, top-level `pred`, etc.).

**Resolution:** The Gobrafier handles `.gobra` files with a separate transformation mode that
converts Gobra-specific top-level constructs into `//@ ` annotated Go stubs:
- `adt Tree { Leaf{}; Node{l Tree; r Tree} }` → `//@ adt Tree ...` comment + blank
  `type Tree struct{}` placeholder
- `ghost func f() { ... }` → `//@ ghost func f() { ... }` comment + blank stub
- `pred P(x *int) { ... }` → `//@ pred P(x *int) { ... }` comment + blank stub

This keeps the pipeline uniform: **both `.go` and `.gobra` files go through Gobrafier →
`go/parser` → annotation parser**. The annotation parser (plan 05) picks up ghost top-level
declarations from the `//@ ` comments it finds at file scope. The annotation parser's scope
must be extended to handle top-level ghost declarations (ADTs, predicates, ghost functions),
not only inline spec clauses.

Source positions in the transformed output preserve line counts (blank stubs are one line per
original declaration line), so position mapping remains the identity as stated below.

`PositionMap` is therefore a simple line-delta table (preprocessed line → original line).
In practice, since line counts are preserved, the table is the identity mapping and can be
omitted entirely for the initial implementation. Add a non-trivial mapping only if a
transformation is found that changes line counts.

## Deliverables

- `internal/frontend/gobrafier.go` — `Gobrafy(src []byte, filename string) ([]byte, error)`
  (no PositionMap needed for the initial implementation; see position mapping note above)
- Tests: compare preprocessed output for a selection of `.go` and `.gobra` test files against
  expected output (golden files); verify line count is preserved in all cases
- **Required golden-file test**: include at least one `.gobra` input containing all three
  top-level ghost constructs — an `adt` declaration, a `ghost func`, and a `pred` — and verify
  the Gobrafier output exactly matches the golden file. This test guards the `.gobra`
  transformation mode (ghost → `//@ ` comment + blank stub). Regenerate goldens with
  `GOBRAFY_UPDATE=1 go test ./internal/frontend/` when the transformation changes intentionally.
  Golden files live in `internal/frontend/testdata/gobrafy/`.
