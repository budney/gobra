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
`isComparable(k, kType)` using the `Type` domain (from the interface encoding). This
assertion fires at every call site.

**`len(m)`**: Requires read permission; `|m.underlyingMapField| == cardinality` of the
underlying Silver map.

**Ghost `dict[K]V`** (mathematical map): Exclusive `dict[K]V` → `vpr.MapType(K, V)` directly
(Silver's built-in `Map`). No field, no `Ref`. All operations are Silver built-in map ops.

**Comma-ok idiom** (`v, ok := m[k]`): produce a tuple `(MapLookup(content, k), MapContains(content, k))` and decompose via tuple projection. The desugarer should handle the tuple decomposition.

**`range` over maps:** Model as non-deterministic selection; the verifier cannot reason about
iteration order. Use a ghost variable or quantifier over the map domain.

## Deliverables

- `internal/translator/encodings/maps.go` (regular maps + ghost dict)
- Tests: encode map lookup, assignment, deletion, comma-ok, len
