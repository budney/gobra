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

## Proposed Approach (from Scala source analysis)

**Integers (`int`, `int8/16/32/64`, `uint*`):**
- Exclusive: `vpr.Int` (unbounded) for all integer sizes. Range constraints for sized types
  are inserted by the overflow check transform (plan 13), not by this encoding.
- Shared: `vpr.Ref`.
- No Silver domain for integers.
- **Integer division and modulo**: Do NOT use Silver's built-in `/` and `%` operators — they
  use floor division, but Go truncates toward zero. Generate **bodyless** Viper functions
  `goIntDiv(l, r: Int): Int` and `goIntMod(l, r: Int): Int` with preconditions and
  postconditions specifying Go's truncation semantics (see Bodyless Functions table below).
  Do NOT emit a Silver function body — the truncation specification is expressed entirely
  via postconditions, which the SMT solver axiomatizes. Emit these functions once and cache them.
  Both functions carry a precondition `requires r != 0` — Go panics on integer division by
  zero at runtime. **No call-site assertion is inserted** by the desugarer or any transform.
  The function's precondition is sufficient: Silicon requires proof that `r != 0` at every
  call site and emits a precondition-violation error pointing to the division expression.
  This check is unconditional — it is not gated on `--overflow`.
- **Bitwise operators** (`&`, `|`, `^`, `&^`, `<<`, `>>`): encode as uninterpreted Viper
  functions (`bitwiseAnd(l, r: Int): Int`, etc.) with **no axioms**. They are black boxes
  for the SMT solver. Preconditions on shifts: `right >= 0`.

**Booleans:** `vpr.Bool` (Exclusive); `vpr.Ref` (Shared). Direct Silver mapping.

**Strings:**
- Exclusive: `vpr.Int` — an opaque integer identifier in the `Strings` domain.
- Shared: `vpr.Ref`.
- Generate a Silver domain `Strings` with:
  - `unique function stringLit_{hexEncodedValue}(): Int` per string literal
  - `function strLen(id: Int): Int` with axiom `0 <= strLen(x)` and per-literal axioms
  - `function strConcat(l, r: Int): Int` with `strLen(concat) == strLen(l) + strLen(r)` axiom
  - Pure Viper function `strSlice(s, lo, hi: Int): Int` with length postcondition

**Floats (`float32`, `float64`):**
- Exclusive: `vpr.Int` (opaque identifier, same pattern as strings).
- Shared: `vpr.Ref`.
- All arithmetic operations become uninterpreted Viper functions (`addFloat64`, etc.) with
  no axioms. Float reasoning is fully opaque to the SMT solver.

**`byte` / `rune`:** Aliases for `uint8` / `int32` respectively; use those encodings.

## Bodyless Functions

Per plan 19: every bodyless Viper function must be verified against the Scala source before
this encoding is considered complete. Scala reference: `src/main/scala/viper/gobra/translator/encodings/`
(look for integer and string encoding files).

| Function | Preconditions | Required postconditions | Scala reference |
|----------|--------------|-------------------------|-----------------|
| `goIntDiv(l, r: Int): Int` | `r != 0` | Truncation semantics: `result == l - r * (if l >= 0 && r > 0 \|\| l <= 0 && r < 0 then l / r else -((-l) / r))` — verify exact form against Scala | Integer encoding |
| `goIntMod(l, r: Int): Int` | `r != 0` | `result == l - r * goIntDiv(l, r)` | Integer encoding |
| `bitwiseAnd(l, r: Int): Int` | none | none (uninterpreted) | Integer encoding |
| `bitwiseOr(l, r: Int): Int` | none | none (uninterpreted) | Integer encoding |
| `bitwiseXor(l, r: Int): Int` | none | none (uninterpreted) | Integer encoding |
| `bitwiseAndNot(l, r: Int): Int` | none | none (uninterpreted) | Integer encoding |
| `shiftLeft(l, r: Int): Int` | `r >= 0` | none (uninterpreted) | Integer encoding |
| `shiftRight(l, r: Int): Int` | `r >= 0` | none (uninterpreted) | Integer encoding |
| `strSlice(s, lo, hi: Int): Int` | `0 <= lo && lo <= hi` | `strLen(result) == hi - lo` — verify exact postconditions against Scala | String encoding |

**Audit checklist:** For each row, open the Scala source file named in the reference column,
find the bodyless function definition, and confirm every postcondition in the Go implementation
matches exactly. Mark each row ✓ when confirmed. Do not mark this encoding complete until all
rows are checked.

**Uninterpreted bitwise/shift functions:** These have NO postconditions by design — the
SMT solver treats them as opaque. This means proofs involving bitwise operations will not
go through unless the user adds `//@ assume` or the spec avoids bitwise results. Document
this limitation in `KNOWN_LIMITATIONS.md`.

## Deliverables

- `internal/translator/encodings/primitives.go` (integers, booleans, floats, byte, rune)
- `internal/translator/encodings/strings.go` (string domain + concat/slice functions)
- Tests: encode representative arithmetic including division, bitwise ops, string concat

## Open Questions

- `unsafe.Sizeof` / `unsafe.Alignof`: treat as opaque integer constants for the initial
  implementation; mark with `// TODO: unsafe` comments.
