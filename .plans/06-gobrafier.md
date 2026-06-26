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

## Deliverables

- `internal/frontend/gobrafier.go` — `Gobrafy(src []byte, filename string) ([]byte, PositionMap, error)`
- `PositionMap` type that maps preprocessed `token.Pos` back to original source positions
- Tests: compare preprocessed output for a selection of `.go` and `.gobra` test files against
  expected output (golden files)

## Open Questions

- Should the Gobrafier produce an in-memory `[]byte` or write a temp file? In-memory is
  preferable (no filesystem I/O); `go/parser` accepts `[]byte` directly via `ParseFile`'s
  `src` parameter.
- Position mapping strategy: maintain a line-offset table mapping preprocessed line N to
  original line M, or track byte-offset ranges? Line-offset table is simpler.
