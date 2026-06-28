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
- Exclusive: `dflt(T°)` — the default value of the **exclusive** encoding of the pointed-to
  type `T`. This is the zero value of whatever Silver type `T°` maps to:
  - `*int`: `dflt(Int)` = 0
  - `*bool`: `dflt(Bool)` = false
  - `*S` (struct): `dflt(Tuple[...])` = default tuple (via `TupleDomain`)
  - `*[]int`: `dflt(Slice[Int])` = `nilSlice_{Int}()`
  - `*I` where `I` is a Go interface: `T°` is `InterfaceDomain`; `dflt(InterfaceDomain)` is
    the nil interface encoding `iface(null, nilType())` (see plan 25).
  - `*P` where `P` is itself a pointer type: apply the rule recursively — `dflt((P)°)`.

  **Note on `null`:** Silver's `null` literal is the default `Ref` value and equals `dflt(Ref)`
  by Silver's built-in semantics. When `T°` happens to be `Ref` (i.e., `T` encodes exclusively
  as `Ref`), then `dflt(T°) = dflt(Ref) = null`. This is correct — no additional axiom is
  needed because Silver defines `null` as the zero `Ref`. The bullet "`*Ref`-typed T → null"
  previously in this list was a shorthand for this case; it has been removed to avoid confusion
  with the shared encoding (where `T@` is always `Ref`, but that is irrelevant to the exclusive
  nil). Always derive the exclusive nil as `dflt(T°)` for the specific Silver type that `T°` is.

  **Do not use `dflt(T@)`** (the default of the shared encoding). For primitive types, `T@` is
  `Ref` and `dflt(Ref) = null`, which has Silver type `Ref` — but exclusive `*int` has Silver
  type `Int`, producing a type error. Always use `dflt(T°)` for exclusive nil pointers.
- Nil checks: exclusive `*T == nil` is `v == dflt(T°)`. Shared nil pointer: compare against
  `null` (since shared `*T@` = `Ref` for all T).

**`new(T)`:** Allocates a fresh `Ref`, inhales `acc(loc, write)` for all fields/locations,
inhales default values for each field.

**`*int` specifically (resolved):** Exclusive `*int` = `vpr.Int` (not a Ref + field). Shared
`*int` = `vpr.Ref`. There is no synthetic field for exclusive pointer-to-primitive — the value
itself is the pointer.

## Worked Example: Pointer to Slice (`*[]int`)

The exclusive/shared duality compounds for nested types. Here is how `*[]int` is encoded in
both modes, to make the recursion concrete:

**Exclusive `*[]int°`** — a pointer value used as a pure mathematical value (not yet on the
heap). The encoding rule is "exclusive `*T` = exclusive `T`" applied recursively:
- exclusive `[]int` = a `Slice[Int]` domain value (plan 23)
- therefore exclusive `*[]int` = `Slice[Int]`

**Shared `*[]int@`** — the pointer itself lives on the heap (e.g., as a field of a shared
struct). The rule "shared `*T` = Ref" applies:
- `vpr.Ref`
- Dereferencing it yields the exclusive `Slice[Int]` value stored at that location.
- Ownership: `acc(p, write)` where `p: Ref`.

**Dereferencing `*p` where `p: *[]int`:**
- If `p` is exclusive: `*p` is just `p` itself (the `Slice[Int]` value — no heap access).
- If `p` is shared: `p` is a `Ref`; `*p` reads the heap at `p`, yielding `Slice[Int]`.

**`new([]int)`:** Allocates a fresh `Ref`, inhales `acc(ref, write)`, inhales the default
`Slice[Int]` value (nil slice) at that `Ref`, and returns the `Ref`.

**Practical heuristic:** If you are unsure whether a type is in exclusive or shared mode at a
given program point, ask: "Is this value reachable by another pointer?" If yes → shared. If
it is a local variable on the Go stack, not pointed to by anything in scope → exclusive.

## Deliverables

- `internal/translator/encodings/pointers.go`
- Tests: encode `new(int)`, exclusive pointer dereference, shared pointer dereference, nil check,
  and a round-trip through `*[]int` (new, write via pointer, read via pointer) to validate the
  nested exclusive/shared encoding
