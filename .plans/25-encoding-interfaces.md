# 25 — Encoding: Interfaces

## Objective

Implement the encoding of Go interface types, dynamic dispatch, and type assertions into Silver.
Interface encoding is one of the most complex parts of Gobra due to behavioral subtyping and
the need to encode method contracts for dynamic dispatch.

## Scope

**In scope:**
- Interface type `interface{ M(...) ... }` encoding
- Interface satisfaction: checking that a type implements an interface's method set
- Dynamic dispatch: `i.M()` where `i` is an interface value
- Type assertions: `i.(T)`, `i.(T)` comma-ok form
- Type switches: `switch v := i.(type) { case T: ... }`
- `nil` interface values
- Empty interface `interface{}` / `any`
- Interface equality
- Behavioral subtyping: the callee's postcondition must be at least as strong as the
  interface method's postcondition (Liskov substitution in specs)
- Predicate abstraction for interface methods
- `comparable` constraint

**Out of scope:**
- Generics with interface constraints (30-encoding-generics.md)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context
- [21-encoding-structs.md](21-encoding-structs.md) — structs as common interface implementors

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/interfaces/` — interface encoding;
  this is one of the more complex encoding modules
- `src/main/scala/viper/gobra/translator/encodings/interfaces/InterfaceEncoding.scala`
- Pay special attention to how behavioral subtyping is encoded via Silver `apply` and
  predicate abstractions

## Deliverables

- `internal/translator/encodings/interfaces.go`
- Tests: encode an interface with one method, a struct implementing it, and a dynamic call

## Open Questions

- How does Gobra encode the "iterable set of types implementing an interface" for type switches?
  This requires some form of type tag or discriminant in the Silver encoding.
