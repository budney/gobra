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

## Proposed Approach (from Scala source analysis)

**Interface value representation:** A Go interface value is encoded as a Silver **2-tuple**:
`(polyVal: Ref, dynType: Type)` where:
- `polyVal` is the universally-boxed concrete value (see `Poly[T]` domain below).
- `dynType` is a term of the `Type` domain (see below) carrying the runtime type tag.

**`Poly[T]` domain** (one per concrete type `T` that is boxed):
```silver
domain Poly[T] {
  function box(x: T): Ref
  function unbox(y: Ref): T
  axiom { forall x: T :: {box(x)} unbox(box(x)) == x }
  // For finite types (bool): conditional round-trip axiom only when dynType matches
}
```

**`Type` domain** (global, shared across all interface encodings):
```silver
domain Type {
  unique function bool_Type(): Type
  unique function int_Type(): Type
  function slice_Type(elem: Type): Type
  function struct_Type_{S}(): Type  // per struct type
  // ... one per type constructor
  function tag(t: Type): Int
  unique function {TypeName}_tag(): Int  // per concrete type
  function behavioral_subtype(sub: Type, sup: Type): Bool
  function comparableType(t: Type): Bool
  // axioms: tag uniqueness, subtype reflexivity/transitivity, comparability
}
```

**Type switch / type assertions (resolved):** The `Type` domain's `tag` function and unique
tag constants serve as the type discriminant. `i.(T)` asserts `dynType(i) == T_tag()` and
unboxes `polyVal` via `Poly[T].unbox`. Type switches are chained `if` or conditional expressions
on `dynType`.

**Interface predicates:** All predicates from the same interface are merged into one parametric
Silver predicate, with the body as a chain of
`typeOf(recv) == T_Type() ? body_of_T_predicate(...) : ...` conditional expressions.

**Interface methods:** A pure interface method `I.M` becomes a Silver function with
postconditions `typeOf(itf) == T_Type() ==> result == proof_{T}_{I}_{M}(valueOf(itf, T), args)`
for each implementing type `T`.

**Nil interface:** `tuple2(null, nilType())` where `nilType()` is a domain function.

## Deliverables

- `internal/translator/encodings/interfaces.go`
- `Poly[T]` domain generation (one per boxed concrete type)
- `Type` domain generation (global singleton, accumulated across all encodings)
- Tests: encode an interface with one method, a struct implementing it, a dynamic call, and
  a type switch with two cases
