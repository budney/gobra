# 30 — Encoding: Go Generics (New Feature — Not in Scala Gobra)

## Objective

Add support for Go 1.18+ generics (type parameters, type constraints, generic functions and
types) to Go-Gobra. **This feature does not exist in Scala Gobra** — it is new work, not a
port. The Scala codebase uses "typeParam" internally to mean the element type of parameterized
types like `[]T` or `chan T`, not Go type parameters.

## Status

**Not supported in the reference implementation.** There is no Scala source to read for this
feature. This plan must be designed from first principles.

All regression tests using Go generics must be marked `SKIP: generics-not-implemented` in
`tests/testdata/skip.txt` (see plan 35). Do not block the main port on this plan.

**Audit requirement — blocking prerequisite before plan 36:** Before beginning self-hosting
annotation work (plan 36), audit Go-Gobra's own source for use of Go generics. This audit
is **not optional**: if Go-Gobra uses generics that it cannot yet verify, plan 36 is blocked
until either the generics are removed or this plan is implemented. Run the audit as the first
action when plan 36 is scheduled:

```bash
grep -rE '^\s*(func|type)\s+\w+\s*\[' gobra-go/internal/ gobra-go/cmd/
```

**If generics are found in Go-Gobra's own source:**
- Option A (preferred): replace them with concrete types or interfaces. Go-Gobra has no
  stability requirement on internal APIs; the rewrite cost is low. Prefer this option — it
  unblocks plan 36 without requiring this plan to be complete.
- Option B: implement this plan before plan 36. Only choose this option if Option A is
  impractical (e.g., a key algorithm is significantly cleaner with generics).

**If no generics are found:** this plan can be deferred to after the self-hosting milestone
without blocking plan 36 or 37.

**Record the audit result here** (update this file when the audit is completed):
- Audit status: _not yet run_
- Generics found in Go-Gobra source: _unknown_
- Chosen option if generics found: _N/A_

## Scope

**In scope (when this plan is implemented):**
- Generic function declarations: `func F[T any](x T) T`
- Generic type declarations: `type Stack[T any] struct { ... }`
- Predeclared constraints: `any`, `comparable`
- Instantiation of generic functions and types at call/use sites

**Out of scope (defer to a follow-on plan):**
- Union type constraints: `interface { ~int | string }` — no Silver counterpart; requires
  design work beyond this plan
- Generics involving channels

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context
- [21-encoding-structs.md](21-encoding-structs.md) — generic struct instantiation
- [25-encoding-interfaces.md](25-encoding-interfaces.md) — interface constraints

## Proposed Encoding Strategy

**Monomorphization**: generate a separate Silver encoding for each concrete type instantiation
at call/use sites. This is simpler to implement and avoids needing Silver-level polymorphism.
For widely-used generic types it produces larger Silver programs, but correctness is easier
to establish.

Procedure:
1. At each instantiation site, substitute concrete types for type parameters.
2. Run the resulting monomorphic code through the existing encoding machinery.
3. Cache results by `(genericName, concreteTypes...)` to avoid re-encoding the same
   instantiation twice.

Silver's own domain type variables exist but are limited; monomorphization is the safer choice
for an initial implementation.

## Deliverables

- `internal/translator/encodings/generics.go`
- Monomorphization cache in the translator context
- Tests: encode a generic identity function and a generic stack type with one instantiation each

## Open Questions

- How are `comparable` constraints enforced? Use the `comparableType` predicate from the
  `Type` domain (plan 25).
- Union constraints (`~int | string`): no Silver representation exists. Options: (a) treat as
  `any` and rely on the user to add sufficient specs; (b) encode as a domain with axioms for
  each case. Defer until a concrete use case arises.
