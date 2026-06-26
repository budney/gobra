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

## Deliverables

- `internal/translator/encodings/maps.go`
- Tests: encode map lookup, assignment, deletion

## Open Questions

- How is the comma-ok idiom (`v, ok := m[k]`) encoded? It requires producing two Silver
  values from one expression. Confirm the Scala approach.
