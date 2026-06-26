# 23 — Encoding: Slices & Arrays

## Objective

Implement the encoding of Go slice and array types into Silver. Slices are the most commonly
used collection type in Go and have complex permission semantics (shared backing arrays,
sub-slicing).

## Scope

**In scope:**
- Slice type `[]T` encoding
- Array type `[N]T` encoding (fixed-size)
- Slice literals, `make([]T, len, cap)`, `append`, `copy`
- Slice indexing `s[i]`, slice expressions `s[lo:hi]`, `s[lo:hi:max]`
- `len(s)`, `cap(s)`
- Permission model: `acc(s[i])` for individual elements, full-slice permissions
- Nil slice and empty slice
- Slice equality (identity, not element-wise — Go slices compare only to nil)
- Byte slices and string↔[]byte conversion
- `range` over slices

**Out of scope:**
- Maps (24-encoding-maps.md)
- Channels (28)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/slices/` — slice encoding (likely the
  most complex single encoding module)
- `src/main/scala/viper/gobra/translator/encodings/arrays/` — array encoding
- The slice encoding is known to be one of the most intricate parts of Gobra; read carefully

## Proposed Approach (from Scala source analysis)

### Slices

**Exclusive `[]T°`**: a value of the parametric Silver domain `Slice[T]`, which is a 4-tuple
`(array: Array[T], offset: Int, len: Int, cap: Int)` with domain functions:
- `sarray(s: Slice[T]): Array[T]`, `soffset(s)`, `slen(s)`, `scap(s)`
- `sconstruct(a, offset, len, cap): Slice[T]` — Viper function with pre/postconditions
  fixing projections to arguments
- `nilSlice_{T}(): Slice[T]` — the nil slice per element type
- Sub-slicing operations: `ssliceFromSlice(s, lo, hi)`, `sfullSliceFromSlice(s, lo, hi, max)`,
  `ssliceFromArray(a, lo, hi)`, `sfullSliceFromArray(a, lo, hi, max)`

**Shared `[]T@`**: `vpr.Ref`.

**Permissions on slices:** `acc(s: []T, perm)` → `forall idx: Int :: {loc(sarray(s), soffset(s)+idx)} 0 <= idx < slen(s) ==> elem_acc(sarray(s), soffset(s)+idx, perm)`. Default bound is **length**, not capacity. Accessing capacity requires explicit `SliceBound.Cap`.

**Function bodies**: slice constructor functions have **no bodies** — correctness relies entirely
on pre/postconditions. Do not generate Viper function bodies for slice operations.

**High-risk area**: Because Silicon treats bodyless Viper functions as uninterpreted, any
missing or incomplete postcondition silently weakens verification — Silicon will not complain,
it will simply be unable to prove things that depend on the omitted fact, or worse, will
accept an incorrect proof if a precondition is too weak. The slice encoding is the most
complex in the system. Every postcondition on every slice constructor function must be
verified against the Scala implementation during porting. Use the Scala Gobra as oracle: if a
regression test passes Scala but fails Go-Gobra, check for a missing postcondition first.

**`make([]T, len, cap)`**: inhales permissions over the full capacity; asserts default values
over length only.

### Arrays

**Exclusive `[n]T°`** (fixed-size): encoded as a `Seq[vprElemType]` **boxed** by a pair of
Viper functions `box_{n}_{T}(x: Seq[T]): BoxedArray` / `unbox_{n}_{T}(y: BoxedArray): Seq[T]`
with pre/postconditions enforcing `|result| == n`. The underlying type is `Seq[T]` so array
equality uses Viper's built-in sequence axioms.

**Shared `[n]T@`** (heap-allocated): encoded using the `Array[T]` parametric Silver domain
(with `array_loc(a, i)`, `alen(a)` domain functions), also boxed with `box/unbox` enforcing
`alen(result) == n`. `arrayNil()` is the sentinel null array.

**Additional generated items (per `(n, T)` pair, monomorphized):**
- `arrayConversion(x: [n]T@): [n]T°` — reads shared array as exclusive; requires wildcard perms
- `arrayDefault(): [n]T°` — default value with quantified per-element defaults

**Monomorphization**: fully monomorphized per `(n, elementType)` pair. Array types of different
sizes or element types generate separate `box/unbox` function pairs.

**Resolved**: slices use the `Slice[T]` domain (not `Seq`); exclusive arrays use `Seq[T]` (not the `Array[T]` domain).

## Deliverables

- `internal/translator/encodings/slices.go` — `Slice[T]` domain + all slice operations
- `internal/translator/encodings/arrays.go` — box/unbox + `Array[T]` domain + conversion fns
- Tests: encode slice indexing, append, sub-slicing, len/cap, array literal, array indexing
