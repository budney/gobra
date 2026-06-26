# 22 — Encoding: Pointers

## Objective

Implement the encoding of Go pointer types and pointer operations into Silver.

## Scope

**In scope:**
- `*T` type encoding (pointer to any type T)
- `&x` (address-of): take a pointer to a variable or field
- `*p` (dereference): read/write through a pointer
- `nil` pointer literal and nil checks
- Pointer equality (`p == q`, `p == nil`)
- `new(T)`: allocate a zero-initialized T on the heap, return a pointer
- Pointer permission: `acc(*p)` — ownership of the pointed-to value
- Interaction with struct encoding: `p.f` where `p` is `*S`

**Out of scope:**
- Unsafe pointer arithmetic (`unsafe.Pointer` — defer to a later iteration)
- Slice/array pointers (23-encoding-slices.md)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context
- [21-encoding-structs.md](21-encoding-structs.md) — pointer-to-struct patterns

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/` — pointer-related files
- Look for `PointerEncoding.scala` or similar
- Note: in Gobra's encoding, a pointer `*T` is typically a `Ref` pointing to a location
  that holds the value; the value itself may be on the heap as another `Ref` or stored in
  a Silver field

## Deliverables

- `internal/translator/encodings/pointers.go`
- Tests: encode `new(int)`, pointer dereference, `nil` check

## Open Questions

- How does Gobra encode a pointer to a primitive (e.g., `*int`)? A Silver `Ref` with a
  synthetic field holding the integer? Confirm by reading the Scala code.
