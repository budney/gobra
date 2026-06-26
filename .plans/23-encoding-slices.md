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

## Key Encoding Pattern

Gobra represents slices as a Silver domain capturing the slice header (ptr, len, cap) and
uses a combination of Silver fields and sequence axioms to model the backing array and
element permissions. Study the existing encoding before designing the Go version.

## Deliverables

- `internal/translator/encodings/slices.go`
- `internal/translator/encodings/arrays.go`
- Tests: encode slice indexing, append, sub-slicing, len/cap

## Open Questions

- Does the current Gobra encoding use a Silver `Seq` to model slice contents, or a
  heap-based array model? This significantly affects complexity. Read the Scala source first.
