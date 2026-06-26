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

## Proposed Approach (from Scala source analysis)

**Exclusive `*T°`** (a pointer value, not what it points to): encoded as **the same Silver
type as `T` itself** (value semantics). The exclusive representation of a pointer *is* the
value it points to — it is a pure mathematical value, not a heap location.

**Shared `*T@`** (a pointer living on the heap, accessible by another pointer): encoded as
`vpr.Ref`.

**Dereferencing (`*p`):**
- When `p` is an exclusive pointer, `*p` is just `p` itself (the value).
- When `p` is a shared pointer (`Ref`), `*p` accesses the heap at `p`.

**`nil` pointer:**
- Exclusive: `dflt(T@)` — the default value of the shared version of `T`. For `Ref`-based
  types this is Silver `null`.
- Nil checks: compare against `null` for `Ref`-typed shared pointers.

**`new(T)`:** Allocates a fresh `Ref`, inhales `acc(loc, write)` for all fields/locations,
inhales default values for each field.

**`*int` specifically (resolved):** Exclusive `*int` = `vpr.Int` (not a Ref + field). Shared
`*int` = `vpr.Ref`. There is no synthetic field for exclusive pointer-to-primitive — the value
itself is the pointer.

## Deliverables

- `internal/translator/encodings/pointers.go`
- Tests: encode `new(int)`, exclusive pointer dereference, shared pointer dereference, nil check
