# 21 — Encoding: Structs & Fields

## Objective

Implement the encoding of Go struct types and field accesses into Silver. Structs are the
primary heap-allocated data structure in Go; their encoding is central to permission reasoning.

## Scope

**In scope:**
- Encoding Go struct types as Silver `Ref` values with Silver `Field` declarations
- Field access encoding: `s.f` → Silver `FieldAccess`
- Struct literals: `S{f: v, ...}` — allocate a new `Ref`, assign fields
- Struct assignment (value semantics): copy all fields
- Embedded fields: access via the embedding chain
- Anonymous structs
- Struct comparability (`==` on structs)
- Permission encoding for struct fields: `acc(s.f)` → `AccessPredicate`

**Out of scope:**
- Pointer-to-struct (22-encoding-pointers.md handles `*S`)
- Interface embedding (25-encoding-interfaces.md)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context and encoding interface

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/structs/` — struct encoding
- `src/main/scala/viper/gobra/translator/encodings/structs/StructEncoding.scala`
- Note: Gobra represents struct values on the heap (as `Ref`) even when Go would use value
  semantics; understand how value-copy semantics are encoded via Silver's `inhale`/`exhale`

## Key Encoding Pattern

In Silver, Go struct `S { f int; g bool }` typically becomes:
```
field S_f: Int
field S_g: Bool
```
A Go value of type `S` is encoded as a `Ref`. Ownership of `s` gives `acc(s.S_f)` and
`acc(s.S_g)`.

## Deliverables

- `internal/translator/encodings/structs.go`
- Tests: encode a struct type, a field read, a field write, a struct literal

## Open Questions

- How are field names mangled when two structs have a field with the same name? Silver fields
  are global; the current Gobra uses qualified names. Confirm the mangling scheme.
