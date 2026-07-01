# 24 — Encoding: Maps

## Objective

Implement the encoding of Go map types into Silver.

## Scope

**In scope:**
- Map type `map[K]V` encoding
- Map literals `map[K]V{k: v, ...}`
- `make(map[K]V)`
- Map lookup `m[k]` (single-value and comma-ok forms)
- Map assignment `m[k] = v`
- Map deletion `delete(m, k)`
- `len(m)`
- Nil map and empty map
- Permission model for maps
- `range` over maps (non-deterministic iteration order must be modeled)

**Out of scope:**
- Sync maps, concurrent map access

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context
- [11-internal-ast.md](11-internal-ast.md) — input types (`internal.MapT`, `internal.MapLookup`, etc.)
- [14-silver-ast.md](14-silver-ast.md) — output types (`silver.Domain`, `silver.DomainFunc`, etc.)
- [25-encoding-interfaces.md](25-encoding-interfaces.md) — **required**: the `Type` domain and
  `comparableType` function are defined there. Plan 24 must call `ensureTypeDomain(ctx)` (defined
  in plan 25) before emitting any `comparableType` assertion. This helper is idempotent and safe
  to call from map encoding even in programs with no interfaces; it must NOT be replaced by a
  bare assumption that plan 25's interface encoding has already run, since map-only programs
  would produce invalid Silver with an undefined `comparableType` function.

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/maps/` — map encoding
- Note: Go maps are typically encoded via a Silver domain capturing the map as a mathematical
  function; the permission model must track map ownership

## Proposed Approach (from Scala source analysis)

**Both Exclusive and Shared `map[K]V`** are encoded as `vpr.Ref`. There is no value-semantic
map type — maps are always heap-allocated. `nil` map is Silver `null`.

**The map content** is stored in a Silver field per `(K,V)` pair:
- `field underlyingMapField_{K}_{V}: Map[K, V]` (Silver's built-in `Map` type)
- Both the field and its type are monomorphized per `(K, V)`.
- `acc(m: map[K]V, perm)` translates to `acc(m.underlyingMapField_{K}_{V}, perm)`.

**Key comparability:** Every map lookup and key-in-map check emits a precondition
`comparableType(kType)` using the `Type` domain (from the interface encoding, plan 25). This
assertion fires at every call site. `kType` is the `Type` domain value for the key's Go type,
obtained via `ctx.TypeEncoding().TypeValue(keyType)`.

**`len(m)`**: Requires read permission; `|m.underlyingMapField| == cardinality` of the
underlying Silver map.

**Ghost `dict[K]V`**: handled entirely by plan 29 (`mathcollections.go`). Plan 24 covers only
runtime `map[K]V`. Do not add dict encoding here.

**Comma-ok idiom** (`v, ok := m[k]`): by the time the translator sees this form, the desugarer
has already decomposed it into two separate `Assign` statements — one targeting `v` with a
`MapLookup(content, k)` expression and one targeting `ok` with a `MapContains(content, k)`
expression (see plan 11 and plan 12). The translator encodes each expression independently;
no tuple handling is needed here.

**`range` over maps:** Model as non-deterministic selection; the verifier cannot reason about
iteration order. Use a ghost variable or quantifier over the map domain.

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/translator/encodings/maps.go`
and verified before this plan is considered complete.

**`EncodeMap` non-nil result:**
```go
//@ requires ctx != nil && t != nil
//@ ensures  result != nil
func (e *MapEncoding) EncodeMap(ctx Context, t *internal.MapType) (result silver.Type)
```

**`EmptyMap` encoding postcondition — the empty map is the default:**
```go
//@ requires ctx != nil
//@ ensures  result != nil
func (e *MapEncoding) EmptyMap(ctx Context, t *internal.MapType) (result silver.Expr)
```

**`Type` domain emission guard** — map lookup emits `comparableType` from the `Type` domain
(plan 25). The domain must be emitted before any map encoding call; enforced by calling
`ctx.TypeEncoding().EnsureTypeDomain(ctx)` at the start of each `EncodeMap` / `EncodeMapOp`:
```go
//@ requires ctx.TypeEncoding() != nil
//@ ensures  typeDomainEmitted(ctx)  // ghost: Type domain is present in ctx's Silver program
```

## Deliverables

- `internal/translator/encodings/maps.go` (runtime `map[K]V` only; ghost `dict[K]V` is in plan 29)
- Tests: encode map lookup, assignment, deletion, comma-ok, len
