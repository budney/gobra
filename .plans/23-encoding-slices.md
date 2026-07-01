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
- [11-internal-ast.md](11-internal-ast.md) — input types (`internal.SliceT`, `internal.IndexExpr`, etc.)
- [14-silver-ast.md](14-silver-ast.md) — output types (`silver.Domain`, `silver.DomainFunc`, etc.)

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
- `sconstruct(a, offset, len, cap): Slice[T]` — Viper **function** (pure, no body) with
  pre/postconditions fixing projections to arguments; falls under the bodyless-function
  invariant from plan 19 — every postcondition must match the Scala implementation exactly
- `nilSlice_{T}(): Slice[T]` — the nil slice per element type
- Sub-slicing operations: `ssliceFromSlice(s, lo, hi)`, `sfullSliceFromSlice(s, lo, hi, max)`,
  `ssliceFromArray(a, lo, hi)`, `sfullSliceFromArray(a, lo, hi, max)`

**Shared `[]T@`**: `vpr.Ref`.

**Permissions on slices:** `acc(s: []T, perm)` → `forall idx: Int :: {loc(sarray(s), soffset(s)+idx)} 0 <= idx < slen(s) ==> elem_acc(sarray(s), soffset(s)+idx, perm)`. Default bound is **length**, not capacity. Accessing capacity requires explicit `SliceBound.Cap`.

**Function bodies**: slice constructor functions have **no bodies** — correctness relies entirely
on pre/postconditions. Do not generate Viper function bodies for slice operations.

**High-risk area**: The slice encoding is the most complex in the system. Bodyless Viper
functions are uninterpreted — see the general warning in plan 19. Every postcondition on
every slice constructor function must be verified against the Scala implementation during
porting. Use the Scala Gobra as oracle: if a regression test passes Scala but fails
Go-Gobra, check for a missing postcondition first.

**`make([]T, len, cap)`**: inhales permissions over the full capacity; asserts default values
over length only.

### Arrays

**Exclusive `[n]T°`** (fixed-size): encoded as a `Seq[vprElemType]` **boxed** by a pair of
Viper functions `box_{n}_{T}(x: Seq[T]): BoxedArray` / `unbox_{n}_{T}(y: BoxedArray): Seq[T]`
with pre/postconditions enforcing `|result| == n`. The underlying type is `Seq[T]` so array
equality uses Viper's built-in sequence axioms.

**Shared `[n]T@`** (heap-allocated): encoded using the `Array[T]` parametric Silver domain
(with `array_loc(a, i)`, `alen(a)` domain functions), also boxed with `box/unbox` enforcing
`alen(result) == n`. `nilArray_{T}()` is the typed sentinel null array; the type parameter
preserves type information and is required for the `nilSlice_{T}()` postcondition. Do not use
an untyped `arrayNil()` — the typed form keeps array element type information visible to the
solver.

**Additional generated items (per `(n, T)` pair, monomorphized):**
- `arrayConversion(x: [n]T@): [n]T°` — reads shared array as exclusive; requires wildcard perms
- `arrayDefault(): [n]T°` — default value with quantified per-element defaults

**Monomorphization**: fully monomorphized per `(n, elementType)` pair. Array types of different
sizes or element types generate separate `box/unbox` function pairs.

**Resolved**: slices use the `Slice[T]` domain (not `Seq`); exclusive arrays use `Seq[T]` (not the `Array[T]` domain).

## Bodyless Functions

Per plan 19: every bodyless Viper function must be verified against the Scala source before
this encoding is considered complete. Scala reference: `src/main/scala/viper/gobra/translator/encodings/slices/`
and `src/main/scala/viper/gobra/translator/encodings/arrays/`.

### Slice bodyless functions

| Function | Preconditions | Required postconditions | Scala reference |
|----------|--------------|-------------------------|-----------------|
| `sconstruct_{T}(a: Array[T], offset, len, cap: Int): Slice[T]` | `0 <= offset`, `0 <= len`, `len <= cap` | `sarray(result) == a`, `soffset(result) == offset`, `slen(result) == len`, `scap(result) == cap` | `SliceEncoding.scala` |
| `nilSlice_{T}(): Slice[T]` | none | `slen(result) == 0`, `scap(result) == 0`, `sarray(result) == nilArray_{T}()` | `SliceEncoding.scala` |
| `ssliceFromSlice_{T}(s: Slice[T], lo, hi: Int): Slice[T]` | `0 <= lo`, `lo <= hi`, `hi <= slen(s)` | `slen(result) == hi - lo`, `scap(result) == scap(s) - lo` — verify exact form against Scala | `SliceEncoding.scala` |
| `sfullSliceFromSlice_{T}(s: Slice[T], lo, hi, max: Int): Slice[T]` | `0 <= lo`, `lo <= hi`, `hi <= max`, `max <= scap(s)` | `slen(result) == hi - lo`, `scap(result) == max - lo` — verify exact form | `SliceEncoding.scala` |
| `ssliceFromArray_{T}(a: Array[T], lo, hi: Int): Slice[T]` | `0 <= lo`, `lo <= hi`, `hi <= alen(a)` | `slen(result) == hi - lo`, `sarray(result) == a`, `soffset(result) == lo` — verify | `SliceEncoding.scala` |
| `sfullSliceFromArray_{T}(a, lo, hi, max): Slice[T]` | `0 <= lo`, `lo <= hi`, `hi <= max`, `max <= alen(a)` | `slen(result) == hi - lo`, `scap(result) == max - lo` — verify | `SliceEncoding.scala` |

### Array bodyless functions (per `(n, T)` pair)

| Function | Preconditions | Required postconditions | Scala reference |
|----------|--------------|-------------------------|-----------------|
| `box_{n}_{T}(x: Seq[T]): BoxedArray` | `\|x\| == n` | `unbox_{n}_{T}(result) == x` | `ArrayEncoding.scala` |
| `unbox_{n}_{T}(y: BoxedArray): Seq[T]` | none | `\|result\| == n` | `ArrayEncoding.scala` |
| `arrayConversion_{n}_{T}(x: Array[T]): Seq[T]` | `acc(x, wildcard)`, `alen(x) == n` | `\|result\| == n`, `forall i :: 0 <= i < n ==> result[i] == loc(x, i)` — verify | `ArrayEncoding.scala` |
| `arrayDefault_{n}_{T}(): Seq[T]` | none | `\|result\| == n`, `forall i :: 0 <= i < n ==> result[i] == dflt(T)` — verify | `ArrayEncoding.scala` |

**Audit checklist:** For each row, verify every postcondition against the Scala source.
Mark each row ✓ when confirmed. This is the highest-risk encoding in the system; missing
postconditions on bodyless slice functions are the most likely source of silent unsoundness.

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/translator/encodings/slices.go`
and verified before this plan is considered complete.

**`EncodeSlice` non-nil result:**
```go
//@ requires ctx != nil && t != nil
//@ ensures  result != nil
func (e *SliceEncoding) EncodeSlice(ctx Context, t internal.Type) (result silver.Type)
```

**`nilSlice` encoding postcondition — the nil slice is the default:**
```go
//@ requires ctx != nil
//@ ensures  result != nil
func (e *SliceEncoding) NilSlice(ctx Context, elemType internal.Type) (result silver.Expr)
```
`ctx.Dflt` routes through the encoding dispatch (which has side effects — it may emit
auxiliary Silver definitions) so it cannot be declared `//@ pure` and cannot appear in a
Gobra postcondition. The invariant that `NilSlice` returns the same expression as
`ctx.Dflt(internal.SliceType{Elem: elemType})` is maintained by construction: both paths
produce `nilSlice_{T}()` domain function application for the same `T`.

**`EncodeSliceOp` non-nil result (indexing, append, sub-slice):**
```go
//@ requires ctx != nil && op != nil
//@ ensures  result != nil
func (e *SliceEncoding) EncodeSliceOp(ctx Context, op *internal.SliceOp) (result silver.Expr)
```

## Deliverables

- `internal/translator/encodings/slices.go` — `Slice[T]` domain + all slice operations
- `internal/translator/encodings/arrays.go` — box/unbox + `Array[T]` domain + conversion fns
- Tests: encode slice indexing, append, sub-slicing, len/cap, array literal, array indexing
