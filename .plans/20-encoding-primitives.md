# 20 — Encoding: Primitive Types

## Objective

Implement the encoding of Go primitive types and their operations into Silver. This covers
integers (all sizes and signedness), booleans, strings, bytes, runes, and uintptr.

## Scope

**In scope:**
- Type encodings: `int`, `int8/16/32/64`, `uint`, `uint8/16/32/64`, `uintptr`, `bool`,
  `string`, `byte` (alias for `uint8`), `rune` (alias for `int32`)
- Expression encodings: arithmetic, bitwise, comparison, boolean operators
- Literal encodings: integer literals, boolean literals, string literals
- Overflow handling: when overflow checks are enabled (transform 13 inserts assertions;
  the encoding must produce Silver that checks them)
- Built-in conversions between numeric types
- `unsafe.Sizeof`, `unsafe.Alignof` (if in scope for full parity)

**Out of scope:**
- Composite types (21–25)
- Pointer arithmetic (22)
- String operations beyond literals (indexing, slicing — those involve slices, 23)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context and encoding interface

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/` — look for integer, boolean, string
  encoding files
- `src/main/scala/viper/gobra/translator/encodings/IntEncoding.scala` (or similar)
- Note: Gobra encodes Go's fixed-width integers using Silver's unbounded `Int` type plus
  range assertions (or a modular arithmetic domain); understand which approach is used

## Key Encoding Decisions

- Silver has one integer type (`Int`, unbounded); Go has many sized types. Current Gobra
  uses a combination of range constraints and modular arithmetic domains for sized integers.
- Boolean in Silver is `Bool`; maps directly.
- Strings in Silver are typically encoded as a domain with limited operations.

## Deliverables

- `internal/translator/encodings/primitives.go`
- Tests: encode representative arithmetic expressions and verify the Silver output

## Open Questions

- Does Gobra use a separate Silver domain for each integer size, or one parameterized domain?
  Read `IntEncoding.scala` carefully before implementing.
