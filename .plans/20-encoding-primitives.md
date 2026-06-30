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
- `unsafe.Sizeof`, `unsafe.Alignof` — **not in scope** (see note below)

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
  use floor division, but Go truncates toward zero. Generate **body-carrying** Viper functions
  `goIntDiv(l, r: Int): Int` and `goIntMod(l, r: Int): Int` with explicit Silver bodies
  extracted verbatim from `IntegerEncoding.scala` (see Silver Functions Table below). The body
  is the sole specification for the SMT solver; do NOT add postconditions. Emit these functions
  once and cache them.
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

## Silver Functions Table

The following Viper functions are emitted by this encoding. Two categories:

**Interpreted (body-carrying)**: `goIntDiv` and `goIntMod` have explicit Silver function
bodies that define truncating semantics using Silver's built-in floor-division operators.
They carry no postconditions — the body is the sole specification for the SMT solver.
The body was extracted verbatim from `IntegerEncoding.scala` (see below). Do NOT rewrite
the body formula independently; a prior attempt produced an off-by-one error for
l=−7, r=3 (yielded −1 instead of Go's −2).

**Uninterpreted (bodyless)**: bitwise and shift functions have no body and no postconditions.

| Function | Preconditions | Body / Postconditions | Notes |
|----------|--------------|------------------------|-------|
| `goIntDiv(l, r: Int): Int` | `r != 0; decreases _` | **Body**: `(0 <= l ? l / r : -((-l) / r))` where `/` is Silver's built-in floor division. No postconditions. | Verified against `IntegerEncoding.scala` — `posts = Seq.empty` in Scala ✓ |
| `goIntMod(l, r: Int): Int` | `r != 0; decreases _` | **Body**: `(0 <= l \|\| l % r == 0 ? l % r : l % r - (0 <= r ? r : -r))` where `%` is Silver's built-in floor modulo. No postconditions. | Verified against `IntegerEncoding.scala` — `posts = Seq.empty` in Scala ✓ |
| `bitwiseAnd(l, r: Int): Int` | none | none (uninterpreted) | Integer encoding |
| `bitwiseOr(l, r: Int): Int` | none | none (uninterpreted) | Integer encoding |
| `bitwiseXor(l, r: Int): Int` | none | none (uninterpreted) | Integer encoding |
| `bitwiseAndNot(l, r: Int): Int` | none | none (uninterpreted) | Integer encoding |
| `shiftLeft(l, r: Int): Int` | `r >= 0` | none (uninterpreted) | Integer encoding |
| `shiftRight(l, r: Int): Int` | `r >= 0` | none (uninterpreted) | Integer encoding |
| `strSlice(s, lo, hi: Int): Int` | `0 <= lo && lo <= hi` | `strLen(result) == hi - lo` — postcondition, no body | Verify against Scala string encoding |

**Audit checklist:** For each body-carrying row (`goIntDiv`, `goIntMod`), the body has been
verified against `src/main/scala/viper/gobra/translator/encodings/IntEncoding.scala` and
matches exactly. For bodyless rows, open the Scala source and confirm each has `posts = Seq.empty`
and no function body before marking complete.

**Uninterpreted bitwise/shift functions:** These have NO postconditions by design — the
SMT solver treats them as opaque. This means proofs involving bitwise operations will not
go through unless the user adds `//@ assume` or the spec avoids bitwise results. Append to
or create `KNOWN_LIMITATIONS.md` at the repo root documenting this limitation. (The file is
first created by plan 27; plans implemented before plan 27 should create it if absent.)

## Deliverables

- `internal/translator/encodings/primitives.go` (integers, booleans, floats, byte, rune)
- `internal/translator/encodings/strings.go` (string domain + concat/slice functions)
- Tests: encode representative arithmetic including division, bitwise ops, string concat

## Verification Specifications (C9)

The following Gobra annotations will be written into the Go source files for this encoding
and verified before this plan is considered complete.

1. **Type encoder — total, non-nil result**: every internal type supported by this encoding
   maps to a valid Silver type without panic:
   ```go
   //@ requires t != nil && ctx != nil
   //@ ensures  result != nil
   //@ decreases // terminates: internal.Type is a finite tagged union
   func (e *IntegerEncoding) EncodeType(t internal.Type, ctx Context) (result silver.Type)
   ```

2. **Expression encoder — non-nil result, no type-mismatch panic**: the encoding function
   for integer and boolean expressions returns a non-nil Silver expression and never panics
   on a type the encoding owns:
   ```go
   //@ requires expr != nil && ctx != nil
   //@ ensures  result != nil
   func (e *IntegerEncoding) EncodeExpr(expr internal.Expr, ctx Context) (result silver.Expr)
   ```

3. **`emitGoIntDiv` / `emitGoIntMod` emission — well-formed body**: each emission helper
   returns a non-nil Silver function with a non-nil body (not a bodyless stub):
   ```go
   //@ ensures result != nil && result.Name == "goIntDiv" && result.Body != nil
   func (e *IntegerEncoding) emitGoIntDiv() (result *silver.Function)

   //@ ensures result != nil && result.Name == "goIntMod" && result.Body != nil
   func (e *IntegerEncoding) emitGoIntMod() (result *silver.Function)
   ```

4. **Idempotency invariant — emit-once caching**: `goIntDiv` and `goIntMod` are emitted at
   most once per encoding instance; subsequent calls return the cached pointer unchanged.
   This prevents duplicate Silver members (which Silver rejects):
   ```go
   //@ ghost field goIntDivEmitted bool
   //@ invariant goIntDivEmitted ==> goIntDivFn != nil
   //@ invariant goIntDivEmitted ==> old(goIntDivFn) == goIntDivFn // pointer stable
   ```

5. **String domain singleton**: the `Strings` Silver domain is emitted exactly once per
   translation context. All string-literal functions refer to the same domain instance:
   ```go
   //@ ensures ctx.HasDomain("Strings")
   //@ ensures forall lit string :: ctx.HasDomainFn("stringLit_" + hexEncode(lit))
   func (e *StringEncoding) ensureStringsDomain(ctx Context)
   ```

## Resolved Questions

**`unsafe.Sizeof` / `unsafe.Alignof` (resolved — not an encoder concern):** The `unsafe`
package is rejected at import time by plan 07 (package resolver) and plan 08 (type checker
core), both of which emit an explicit diagnostic for `import "unsafe"`. No file containing
`unsafe.*` references will reach the encoding stage. This encoding plan has no work to do for
`unsafe`: do not add encoder-level handling, dead code paths, or `// TODO` markers for it.
