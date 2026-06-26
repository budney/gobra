# 30 — Encoding: Generics

## Objective

Implement the encoding of Go generics (type parameters, type constraints, generic functions
and types) into Silver. This was added to Go in 1.18 and to Gobra subsequently; it is one
of the more recently added and complex features.

## Scope

**In scope:**
- Generic function declarations: `func F[T any](x T) T`
- Generic type declarations: `type Stack[T any] struct { ... }`
- Type parameter constraints: `interface { ~int | string }`, predeclared constraints
  (`any`, `comparable`)
- Instantiation of generic functions and types at call/use sites
- Encoding strategy: monomorphization (instantiate per concrete type) vs. parametric
  encoding (polymorphic Silver domain)

**Out of scope:**
- Non-generic functions and types (handled in earlier encodings)
- Generics involving channels (complex interaction; defer to after basic generics work)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context
- [21-encoding-structs.md](21-encoding-structs.md) — generic struct instantiation
- [25-encoding-interfaces.md](25-encoding-interfaces.md) — interface constraints

## Reference: Current Gobra

- Search `src/main/scala/viper/gobra/` for generics-related encoding — this was added more
  recently; look for commits mentioning "generics" or "type parameters" in git log
- `src/main/scala/viper/gobra/translator/encodings/` — any TypeParameter or Generic files

## Key Design Decision

- **Monomorphization**: generate a separate Silver encoding for each concrete instantiation
  (simpler, but potentially large Silver programs for widely-used generics)
- **Parametric**: use Silver's domain type variables to encode generics once (harder, but
  produces smaller Silver programs)
- Current Gobra's approach determines which is used here; check before implementing.

## Deliverables

- `internal/translator/encodings/generics.go`
- Tests: encode a generic identity function, a generic stack type, and their instantiation

## Open Questions

- Does the current Gobra monomorphize or use a parametric encoding? This is the most
  important question for this unit; read the Scala code before designing.
- How are type parameter constraints (especially union constraints `~int | string`) encoded
  in Silver? These don't have a direct Silver counterpart.
