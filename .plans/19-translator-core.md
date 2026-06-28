# 19 — Translator Core & Context

## Objective

Implement the translator infrastructure: the `Context` type that encoding modules use to
generate Silver, the coordination layer that drives all encodings, and the interfaces that
each encoding module implements. This is the skeleton into which all encoding modules (20–31)
plug.

## Scope

**In scope:**
- `Context` interface/struct: provides encoding modules with access to:
  - Type info (`TypeInfo` from 08–10)
  - Other encoding modules (mutual access pattern)
  - Fresh Silver name generation
  - Silver program accumulator (where generated members/domains/fields land)
  - `TypeEncoding() TypeEncoding` — returns the type encoding module (plan 25); used by any
    encoding that needs to box/unbox values or look up the Silver Type domain expression for a Go type
  - `TypeValue(t internal.Type) silver.Expr` — returns the Silver `Type` domain expression
    for Go type `t` (e.g., `T_Type()` for a concrete type `T`); delegates to the type encoding.
    Used wherever a `dynType(r)` assertion or behavioral subtype axiom is generated.
- `Encoding` interface that each encoding module implements
- `MainTranslator`: instantiates all encodings, wires them together via `Context`, drives
  the translation of each internal AST member
- Name mangling: Go identifiers → valid Silver identifiers (handle dots, unicode, length limits)
- `FunctionTable`: track which Go functions have been translated (for cross-function references)

**Out of scope:**
- Individual encoding implementations (20–31)
- Silver AST types (14) — already defined
- Internal AST types (11) — already defined

## Dependencies

- [11-internal-ast.md](11-internal-ast.md) — input to translation
- [14-silver-ast.md](14-silver-ast.md) — output of translation

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/` — top-level
  - `MainTranslator.scala` — the coordination layer; study this carefully
  - `context/Context.scala` — the context interface
  - `context/ContextImpl.scala` — implementation
  - `interfaces/` — encoding interfaces
- `src/main/scala/viper/gobra/translator/encodings/` — all encoding modules that plug in

## Key Design Points

- The `Context` is passed to every encoding call; it is the primary coupling mechanism
- Encodings are mutually dependent (e.g., the struct encoding calls the type encoding);
  this is resolved by making `Context` hold references to all encodings (initialized together)
- Silver name generation must be deterministic (same input → same Silver name) for caching
  and debugging

## The Exclusive / Shared Duality (Critical Architecture Concept)

Every Go type has **two encodings** depending on whether the value is addressable (reachable
via pointer) at the point of use:

- **Exclusive** (`T°`): value semantics — the Go value is a pure mathematical value. Silver
  uses a value type (`Int`, `Bool`, a domain type, or `Seq[T]`). No heap, no permissions.
- **Shared** (`T@`): the value lives on the heap and is reachable by pointer. Silver uses
  `Ref`. Ownership of the value is an `acc()` permission.

This duality applies uniformly across all types (see encoding plans 20–31). The `Context`
must provide a method `ExclusiveType(t internal.Type) silver.Type` and
`SharedType(t internal.Type) silver.Type` that each encoding delegates to. The translator
core is responsible for deciding which mode to use at each program point, based on type info
from the desugarer.

## Deliverables

- `internal/translator/context.go` — `Context` interface and implementation
- `internal/translator/encoding.go` — `Encoding` interface and `TypeEncoding` interface (see below)
- `internal/translator/translator.go` — `Translate(prog *internal.Program, info *TypeInfo) (*silver.Program, error)`
- `internal/translator/mangle.go` — name mangling utilities (see spec below)
- `TupleDomain(n int) *silver.Domain` helper on `Context`: lazily emits (and caches) a Silver
  tuple domain for arity `n`. Used by the struct encoding (plan 21) for exclusive structs and
  by the pointer encoding (plan 22). Emit once per arity; cache in translator state.

  **Complete domain structure for arity N** (Silver does not support variadic type parameters,
  so each arity is a distinct domain; the domain name encodes the arity):

  ```silver
  domain gobra__Tuple{N}[T0, T1, ..., T{N-1}] {
    // Constructor: one per domain
    function gobra__tuple{N}(x0: T0, x1: T1, ..., x{N-1}: T{N-1}): gobra__Tuple{N}[T0,...,T{N-1}]
    // Projections: one per field index
    function gobra__tuple{N}_get0(t: gobra__Tuple{N}[T0,...,T{N-1}]): T0
    function gobra__tuple{N}_get1(t: gobra__Tuple{N}[T0,...,T{N-1}]): T1
    // ... one per index up to N-1
    // Round-trip axiom (all projections in one axiom to give Z3 a single trigger)
    axiom gobra__tuple{N}_proj {
      forall x0: T0, ..., x{N-1}: T{N-1} ::
        { gobra__tuple{N}(x0, ..., x{N-1}) }
        gobra__tuple{N}_get0(gobra__tuple{N}(x0,...,x{N-1})) == x0
     && gobra__tuple{N}_get1(gobra__tuple{N}(x0,...,x{N-1})) == x1
     && ...
    }
  }
  ```

  The notation `tuple_get(base, idx, n)` used in plan 21 and plan 22 is a shorthand for
  `gobra__tuple{n}_get{idx}(base)`. Silver has no run-time integer-indexed accessor; each
  field access compiles to a specific named projection function. `TupleDomain(n)` generates
  the entire domain including all N projection functions and the single combined axiom.

  **Axiom trigger**: Use `{ gobra__tuple{N}(x0,...,x{N-1}) }` as the trigger (on the
  constructor call), not individual getter calls. This gives Z3 a single matching term to
  instantiate all projections simultaneously and avoids trigger loops.
- Tests: translate a trivial internal AST (empty method) and verify the Silver output shape

## Name Mangling Specification

Silver identifiers must match `[a-zA-Z$_][a-zA-Z0-9$_]*`. Go identifiers can contain
Unicode characters; Go package paths contain `/` and `.`. The mangling scheme must be
**injective** (no two distinct Go names produce the same Silver name).

**Rules (applied in order):**

1. Replace any character not in `[a-zA-Z0-9_$]` with `_u{HEX}_` where `{HEX}` is the
   Unicode code point in uppercase hex (e.g., `α` → `_u03B1_`, `/` → `_u002F_`,
   `.` → `_u002E_`). This includes package path separators — do NOT replace `/` or `.`
   with bare `_` before this step; they must go through hex-encoding to preserve injectivity.
2. Prepend `G_` if the result starts with a digit (Silver identifiers cannot start with a digit).
3. Silver field names are globally scoped; qualify them as `{mangledPkg}_{TypeName}_{FieldName}`
   to avoid collisions across packages (same convention as plan 21).
4. Silver method and function names: `{mangledPkg}_{funcName}` for package-level functions;
   `{mangledPkg}_{TypeName}_{methodName}` for methods.
5. Auxiliary generated names (tuple domains, box/unbox functions, etc.) use a `gobra__` prefix
   to avoid collisions with user-defined Silver names.

This scheme is injective because: every non-`[a-zA-Z0-9_$]` character (including `/` and `.`)
is replaced by its unique `_u{HEX}_` encoding; the `gobra__` prefix is reserved and never
emitted for user names. **Do not use a simpler "replace `/` and `.` with `_`" rule** — that
would collapse `a/b` and `a_b` to the same mangled string, breaking injectivity.

**Residual collision note:** A Go identifier that literally contains the hex-escape pattern as a
substring (e.g., a variable named `_u002F_`) would collide with a hex-escaped `/` in a
package path after mangling. Similarly, a user-defined identifier beginning with `gobra__`
would collide with translator-generated Silver names.

**Resolution — reserved at the type checker, not the mangler:** Rather than a runtime panic
inside the mangler (which fires late and produces a poor diagnostic), the type checker (plan 08)
must reject Go identifiers that match either reserved pattern:
- Any identifier whose name matches the regex `_u[0-9A-F]{1,6}_` is reserved. Use a range of
  1–6 hex digits to cover the full Unicode range U+0000 through U+10FFFF (emoji and other
  code points above U+FFFF encode as 5–6 hex digits, e.g., `_u1F600_`). Do NOT restrict to
  4 digits — that silently misses supplementary code points.
- Any identifier beginning with `gobra__` is reserved for translator-generated Silver names.

The type checker emits a clean diagnostic: `"identifier '_u002F_' is reserved by Go-Gobra's
name mangling scheme"`. The mangler may include a defensive panic with the same message as an
internal bug guard, but the primary enforcement is at the type checker.

The `gobra__` prefix for synthetic names avoids this issue for translator output.

**Note on collision with Scala Gobra**: The Scala Gobra uses a similar but not identical
mangling scheme. Differential testing (plan 34) compares verification *results*, not Silver
output, so scheme differences between Go-Gobra and Scala Gobra are acceptable as long as the
generated Silver is valid and produces correct results.

## Exclusive / Shared Decision Algorithm

The translator calls `ctx.ExclusiveType(t)` or `ctx.SharedType(t)` at each program point.
The decision is based on the `Addressable bool` field on each internal `Expr` node (plan 11),
which the desugarer (plan 12) propagates from `go/types.Info.Types[frontendExpr].Addressable()`
during lowering. The translator reads this field directly — it does NOT look up `TypeInfo`:

```go
func translateExpr(ctx Context, node internal.Expr) silver.Expr {
    t := typeOf(node)
    if node.Addressable() {
        return ctx.SharedType(t)  // heap-resident; use Ref or acc-based encoding
    }
    return ctx.ExclusiveType(t)   // value-semantic; use domain/Int/Bool encoding
}
```

**Ghost AST nodes** and desugarer-introduced temporaries have `Addressable = false` (set by
plan 12). Ghost values in Gobra specs are mathematical values with no heap location.

**Addressability rules**: `go/types.Addressable()` reflects the Go type system's rules
(variables, struct fields, array elements are addressable; map values, interface values are
not). This matches Gobra's ownership model closely. The one case requiring care is struct
fields accessed via a pointer receiver (`p.f` where `p` is `*S`): `p` is addressable
(shared), but `f` itself may be accessed in exclusive mode if it is not further pointed-to.
The desugarer (plan 12) and struct encoding (plan 21) handle this via the exclusive/shared
field-access rules from the `StructEncoding.scala` reference.

## Critical Invariant: Bodyless Viper Functions Are Uninterpreted

**All encoding authors must read this.**

When a Viper `Function` has no body, Silicon treats it as an **uninterpreted function** — it
is axiomatized solely by its preconditions and postconditions. There is no built-in soundness
check that postconditions are consistent; Silicon simply assumes them. The consequences:

- A **missing postcondition** silently weakens verification: Silicon cannot prove anything that
  depends on the omitted fact, but it will not report an error.
- An **incorrect postcondition** (one that is false in some model) silently unsounds the proof:
  Silicon may accept an incorrect program by using the false axiom.
- An **overly strong precondition** makes the function uncallable for legitimate inputs.

This invariant affects: slice constructors (plan 23), array box/unbox functions (plan 23),
the `Poly[T]` domain (plan 25), the `Type` domain (plan 25), string domain functions (plan 20),
`goIntDiv`/`goIntMod` (plan 20), and any other place where a bodyless Viper function is emitted.

**For each bodyless function, verify every postcondition against the Scala implementation
before considering that encoding complete.** Use the Scala Gobra as oracle: if a regression
test passes Scala but fails Go-Gobra, check for a missing postcondition first.

**Per-encoding documentation requirement:** Every encoding plan that emits bodyless Viper
functions must include a **"Bodyless Functions"** subsection that lists each function by name,
its required postconditions (matching the Scala source), and the Scala file and line where
those postconditions can be verified. This table is the audit checklist for the oracle-test
procedure. An encoding plan is not complete until every row of its "Bodyless Functions" table
has been checked against the Scala Gobra. Plans with this requirement: **20, 23, 25, 27, 29**.
(Plan 24 uses Silver's built-in `Map` type operations — no bodyless Viper functions are
generated for runtime maps. Plan 21 generates `sharedStructConversion` and `sharedStructDefault`
which must also be audited; plan 21 must add its own "Bodyless Functions" subsection.)

## Catch-All for Unhandled Nodes

The encoding dispatch must include a final catch-all that fires a clear panic when no encoding
module claims a node. This is the Go-Gobra equivalent of Scala's `FinalTypeEncoding`, which
throws a `Violation` for any unmatched node.

**Where to add it:** `MainTranslator` drives translation of each internal AST member by calling
encoding modules in order (the same chained-encoding pattern as the Scala `CombinedEncoding`).
After all registered encodings have been tried, if none matched, panic:

```go
panic(fmt.Sprintf("no encoding for node %T at %s: %+v",
    node, node.Pos(), node))
```

This applies at every dispatch point:
- Statement translation (`translateStmt`)
- Expression translation (`translateExpr`)
- Type translation (`ExclusiveType` / `SharedType`)
- Member translation (functions, methods, fields, predicates)

**Why panic rather than return an error:** An unhandled node is always an internal bug —
either a node type was added to the internal AST without a corresponding encoding, or the
dispatch logic has a gap. It cannot be caused by user input (the type checker and desugarer
reject unsupported Go constructs before translation begins). A panic surfaces the bug
immediately with a stack trace; returning an error would propagate a nil or zero Silver node
and produce a cryptic failure downstream.

**Relationship to coverage:** The catch-all is a runtime safety net, not a substitute for
coverage analysis. Gaps should be discovered by auditing internal AST node types against the
encoding plans before implementation, not by waiting for a panic in production.

## `TypeEncoding` Interface Definition

`TypeEncoding` is the interface returned by `ctx.TypeEncoding()`. It is implemented by the
interface encoding module (plan 25). Any encoding module that needs to box/unbox values or
reference the Silver `Type` domain calls through this interface.

```go
// TypeEncoding provides access to the Silver Type domain and boxing/unboxing helpers.
// Implemented by the interface encoding (plan 25); exposed on Context.TypeEncoding().
type TypeEncoding interface {
    // TypeValue returns the Silver Type domain expression for Go type t.
    // E.g., int_Type() for int, bool_Type() for bool, struct_Type_Foo() for struct Foo.
    // Triggers Type domain emission (ensureTypeDomain) on first call.
    TypeValue(t internal.Type) silver.Expr

    // BoxValue boxes a value of Go type T into Poly[T].box(val), for use in interface encoding.
    // Requires that the Silver Type domain has already been emitted (call TypeValue first).
    BoxValue(ctx *Context, val silver.Expr, T internal.Type) silver.Expr

    // UnboxValue unboxes a Ref into a value of Go type T via Poly[T].unbox(val).
    // Caller is responsible for establishing dynType(iface) == TypeValue(T) before calling.
    UnboxValue(ctx *Context, val silver.Expr, T internal.Type) silver.Expr

    // EnsureTypeDomain forces emission of the global Type domain into the Silver program
    // accumulator if it has not been emitted yet. Idempotent; safe to call from any encoding.
    EnsureTypeDomain(ctx *Context)
}
```

`TypeEncoding` is distinct from the general `Encoding` interface. `Context` holds one
`TypeEncoding` implementation (the plan 25 module) and exposes it via `TypeEncoding()`. All
other encoding modules call `ctx.TypeEncoding().TypeValue(t)` rather than importing plan 25
directly, keeping the dependency graph a DAG through `Context`.

## Resolved Questions

**Encoding state (resolved):** Encodings are stateful objects (matching the Scala implementation).
Each encoding accumulates auxiliary Silver definitions (domains, functions, fields) in its
internal state and emits them at the end of translation. The `Context` holds references to all
encoding objects (initialized together at translator startup), allowing mutual access.
