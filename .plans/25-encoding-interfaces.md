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

**Interface value representation:** A Go interface value is encoded as a value of a dedicated
`InterfaceDomain` Silver domain (not `TupleDomain(2)` — using TupleDomain would create a type
collision with exclusive 2-field structs encoded by plan 21). `InterfaceDomain` is emitted
lazily on the first use of any interface type in the program and cached (like `TupleDomain` in
plan 19). If no interface values appear in the program, the domain is never emitted. The domain is:

**`Type` domain emission trigger (critical — shared with plan 24):** The `Type` domain must be
emitted on the **first occurrence of any feature that references it**, which includes:
1. First use of any interface type (the primary trigger, covered below).
2. First map lookup or key-in-map check — because `comparableType(kType)` is emitted by plan 24
   for every such operation, even in programs with no interfaces.
The encoding module must expose a `ensureTypeDomain(ctx *Context)` helper that emits the domain
on first call and is a no-op on subsequent calls. Plan 24's map encoding calls this helper before
emitting any `comparableType` assertion; plan 25's interface encoding calls it before emitting
`InterfaceDomain`. Do NOT make plan 24 depend on plan 25 having already executed; the trigger
must be idempotent and callable from any encoding module.

```silver
domain InterfaceDomain {
  function iface(polyVal: Ref, dynType: Type): InterfaceDomain
  function iPolyVal(i: InterfaceDomain): Ref
  function iDynType(i: InterfaceDomain): Type
  axiom { forall v: Ref, t: Type :: {iface(v, t)} iPolyVal(iface(v, t)) == v && iDynType(iface(v, t)) == t }
}
```

`InterfaceDomain` is emitted lazily (on first use) and cached as a global singleton. Do NOT use
`TupleDomain(2)` for interface values — the Silver types would be indistinguishable from
exclusive 2-field structs, breaking type safety in the Silver program.

The two fields of an interface value are:
- `iPolyVal(i)`: the universally-boxed concrete value (see `Poly[T]` domain below).
- `iDynType(i)`: a term of the `Type` domain (see below) carrying the runtime type tag.

**`Poly[T]` domain** (one per concrete type `T` that is boxed):
```silver
domain Poly[T] {
  function box(x: T): Ref
  function unbox(y: Ref): T
  axiom { forall x: T :: {box(x)} unbox(box(x)) == x }
}
```

The unbounded round-trip axiom `unbox(box(x)) == x` is safe for infinite types (`Int`,
`Ref`, `Seq[T]`, etc.) where distinct values of `T` are guaranteed to be distinct.

**`Bool` is a special case.** `Bool` has only two values (`true`, `false`). The unbounded
axiom `forall x: Bool :: unbox(box(x)) == x` is still logically sound, but some SMT solvers
(Z3 in particular) may derive unexpected consequences: since there are only two boxed booleans,
Z3 can deduce `box(true) != box(false)` from the axiom, but it may also collapse the `unbox`
values for boxes of *other types* (`Int`, etc.) without the converse axiom `box(unbox(y)) == y`.
The converse axiom is too strong (it would mean every `Ref` is a box of something), so it is
omitted.

The safe approach (used in the Scala Gobra) is to guard the `unbox` direction with a
`dynType` check: for `Bool`, instead of the unbounded converse, rely on the fact that
`unbox` is only called at type-assertion sites where `dynType(i) == bool_Type()` is already
established in the context. Silicon's path-sensitivity handles the rest. No special axiom is
needed beyond the standard round-trip above — just ensure the type-assertion encoding in the
`Type` domain always establishes `dynType` before unboxing.

**`Poly[Bool]` axiom guard:** `Bool` has only two values, so Z3 can derive that any two `Ref`s
with `unbox_Bool` mappings must cover exactly `{true, false}`. Add an explicit range axiom to
make this constraint visible to the solver and prevent unexpected model collapse:

```silver
domain Poly[Bool] {
  // ... standard round-trip axiom ...
  axiom gobra__poly_bool_range {
    forall y: Ref :: {unbox_Bool(y)} unbox_Bool(y) == true || unbox_Bool(y) == false
  }
}
```

This axiom is only needed for `Poly[Bool]`; other instantiations of `Poly` do not need it.

**Required invariant — `unbox` is only called after a `dynType` check:** This invariant must
hold at every `Poly[T].unbox(...)` call site generated by the translator. For non-Bool types
the round-trip axiom makes unboxing safe regardless of `dynType`; for Bool, correctness
depends on the path condition having established `dynType(i) == bool_Type()` before `unbox`
is applied. The following translator sites must all establish `dynType` before unboxing:
type assertion expressions (`i.(T)`), type switch arms (each arm's `dynType` check is the
case guard), and any other location that unboxes an interface value. If a new encoding site
is added that unboxes without a prior `dynType` check, it is a soundness bug.

**Required test:** Verify a program that stores a `bool` in `interface{}`, type-asserts it
back to `bool`, and uses the result in a postcondition:

```go
//@ ensures result == (x.(bool))
func roundtrip(x interface{}) (result bool) {
    result = x.(bool)
    return
}
```

Go-Gobra must accept this program. Failure indicates the `dynType`-before-`unbox` path is
broken for `Bool`.

**`Type` domain** (global, shared across all interface encodings):
```silver
domain Type {
  unique function bool_Type(): Type
  unique function int_Type(): Type
  unique function string_Type(): Type
  unique function nilType(): Type      // type tag for nil interface values (iface(null, nilType()))
  function slice_Type(elem: Type): Type
  function pointer_Type(elem: Type): Type
  function chan_Type(elem: Type): Type  // channel types: chan_Type(int_Type()) ≠ int_Type()
  function struct_Type_{S}(): Type     // one per concrete struct type (emitted lazily on first use)
  // ... one per type constructor used in the program
  function behavioral_subtype(sub: Type, sup: Type): Bool
  function comparableType(t: Type): Bool

  // Reflexivity of behavioral_subtype
  axiom gobra__bs_refl {
    forall t: Type :: {behavioral_subtype(t, t)} behavioral_subtype(t, t)
  }
  // Transitivity of behavioral_subtype
  axiom gobra__bs_trans {
    forall s: Type, t: Type, u: Type ::
      {behavioral_subtype(s, t), behavioral_subtype(t, u)}
      behavioral_subtype(s, t) && behavioral_subtype(t, u) ==> behavioral_subtype(s, u)
  }
  // comparableType: primitive types and structs of comparable fields are comparable
  axiom gobra__comparable_primitives {
    comparableType(bool_Type()) && comparableType(int_Type()) && comparableType(string_Type())
  }
  // nilType is not comparable (nil interface values cannot be compared to non-nil in specs)
  axiom gobra__nil_not_comparable {
    !comparableType(nilType())
  }
}
```

**`chan_Type` is required (plan 28 dependency):** The exclusive encoding of `chan T` is `vpr.Int`
(an opaque integer ID). Without `chan_Type(elem: Type)` in the `Type` domain, a channel stored
in `interface{}` would be indistinguishable from a plain `int` at the Silver level: both would
box as `Poly[Int]` with an `int_Type()` tag. `chan_Type(elem)` is emitted lazily on the first
use of any channel type in an interface-boxing position (plan 28 encoding). Its Silver
`unique function` guarantee ensures `chan_Type(e) != int_Type()` automatically.

**No integer tags.** The `Type` domain does NOT include a `tag(t: Type): Int` function or `{TypeName}_tag(): Int` per-type integer constants. Type assertions and type switches use `Type`-valued comparisons (`dynType(i) == T_Type()`) directly. Silver's `unique function` guarantees distinctness between all ground `Type` terms, so integer tags are redundant and would only add solver load.

**Behavioral subtype axiom emission:** The axiom `behavioral_subtype(T_Type(), I_Type())` is emitted **lazily when T is first used as interface I** (i.e., when a value of concrete type T is first assigned to or passed as an interface I). It is NOT emitted eagerly for all T-I pairs reachable in the type hierarchy. This matches the Scala Gobra's lazy emission and avoids bloating the Silver program with axioms for unused type-interface pairs. The encoding module tracks emitted (T, I) pairs in a set to avoid duplicates.

**Scalability note for self-hosting (plan 36/37):** The `Type` domain accumulates one
`unique function struct_Type_{S}(): Type` per concrete struct type in the verified program.
For Go-Gobra itself (which has many internal struct types), this domain may grow large and
slow Z3 via unique-function interaction constraints. The Scala Gobra uses lazy population
(only emitting type-tag functions for types that actually appear in interface-boxed positions).
Go-Gobra should do the same: emit `struct_Type_{S}` only on demand when `S` is first seen
as an interface-implementing type, not for every struct in the program. If Z3 timeouts appear
during self-hosting, consult plan 37's `--z3Exe` timeout tuning as a mitigation and check
whether eager type-domain population is the cause.

**Type switch / type assertions (resolved):** The `Type` domain's `unique function` guarantees
distinctness between ground type terms. `i.(T)` asserts `dynType(i) == T_Type()` (using the
`Type`-valued constructor directly — no integer tag needed) and unboxes `polyVal` via
`Poly[T].unbox`. Type switches are chained `if` or conditional expressions on `dynType == T_Type()`.
Silver's `unique function` guarantees `T_Type() != U_Type()` for distinct types without any
additional axioms.

**Interface predicates:** All predicates from the same interface are merged into one parametric
Silver predicate, with the body as a chain of
`typeOf(recv) == T_Type() ? body_of_T_predicate(...) : ...` conditional expressions.

**Interface methods:** A pure interface method `I.M` becomes a Silver function with
postconditions `typeOf(itf) == T_Type() ==> result == proof_{T}_{I}_{M}(valueOf(itf, T), args)`
for each implementing type `T`.

**Nil interface:** `iface(null, nilType())` where `nilType()` is a domain function in the
`Type` domain returning the unique nil type tag.

## Bodyless Functions

Per plan 19: every bodyless Viper function must be verified against the Scala source before
this encoding is considered complete. Scala reference:
`src/main/scala/viper/gobra/translator/encodings/interfaces/`.

Interface methods encoded as Silver functions (pure interface methods, not concrete
implementations) are bodyless. Each such function has postconditions of the form:
`typeOf(recv) == T_Type() ==> result == proof_{T}_{I}_{M}(valueOf(recv, T), args)` for each
implementing type `T`. The exact postcondition set for a given interface method grows as
implementing types are added; the encoding module must emit one postcondition clause per
known implementor.

| Item | Nature | Required postconditions | Scala reference |
|------|--------|-------------------------|-----------------|
| Silver function for pure interface method `I.M` | Bodyless Silver function | One clause per implementing type (see above) — verify clause shape against Scala | `InterfaceEncoding.scala` |
| `Poly[T].box` / `Poly[T].unbox` | Domain functions (axioms, not postconditions) | Round-trip axiom: `forall x: T :: {box(x)} unbox(box(x)) == x` — verify this is the only axiom (no converse) | `InterfaceEncoding.scala` |

**`Poly[T]` domain functions note:** `box` and `unbox` are Silver domain functions, not
Silver functions, so they are governed by domain axioms rather than pre/postconditions.
The "bodyless functions" invariant from plan 19 does not apply to them directly, but the
axiom content is equally critical for soundness. Verify the axiom set (one round-trip axiom,
no converse) against the Scala source before marking this encoding complete.

**Audit checklist:** For each pure interface method encoded, verify the postcondition clause
shape matches the Scala `InterfaceEncoding.scala`. Verify `Poly[T]` has exactly one axiom
(round-trip, no converse). Mark ✓ when confirmed.

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/translator/encodings/interfaces.go`
and verified before this plan is considered complete.

**`EncodeInterface` output ownership (emitted Silver domain is globally unique):**
```go
//@ requires ctx != nil && iface != nil
//@ ensures  result != nil
//@ ensures  result.Name == "InterfaceDomain"   // singleton: same name every call
func EncodeInterface(ctx *Context, iface *types.Interface) *silver.Domain
```

**`BoxValue` unbox-before-dyntype invariant:**
```go
//@ requires dynTypeEstablished(ctx, val, T)
//@ ensures  unboxed != nil
func BoxValue(ctx *Context, val silver.Expr, T types.Type) (boxed silver.Expr, unboxed silver.Expr)
```

**`Type` domain singleton (emitted at most once):**
```go
//@ invariant len(ctx.emittedTypeDomain) <= 1
```

## Deliverables

- `internal/translator/encodings/interfaces.go`
- `Poly[T]` domain generation (one per boxed concrete type)
- `Type` domain generation (global singleton, accumulated across all encodings)
- Tests: encode an interface with one method, a struct implementing it, a dynamic call, and
  a type switch with two cases
