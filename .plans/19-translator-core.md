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
- `internal/translator/encoding.go` — `Encoding` interface
- `internal/translator/translator.go` — `Translate(prog *internal.Program, info *TypeInfo) (*silver.Program, error)`
- `internal/translator/mangle.go` — name mangling utilities (see spec below)
- `TupleDomain(n int) *silver.Domain` helper on `Context`: lazily emits (and caches) a parametric
  Silver tuple domain for arity `n`. Used by the struct encoding (plan 21) for exclusive structs.
  The domain exposes `tuple_get(base, idx, n)` projections. Emit once per arity; cache in translator state.
- Tests: translate a trivial internal AST (empty method) and verify the Silver output shape

## Name Mangling Specification

Silver identifiers must match `[a-zA-Z$_][a-zA-Z0-9$_]*`. Go identifiers can contain
Unicode characters; Go package paths contain `/` and `.`. The mangling scheme must be
**injective** (no two distinct Go names produce the same Silver name).

**Rules (applied in order):**

1. Replace `/` with `_` and `.` with `_` in package paths.
2. Replace any character not in `[a-zA-Z0-9_$]` with `_u{hex}_` where `{hex}` is the
   Unicode code point in uppercase hex (e.g., `α` → `_u03B1_`).
3. Prepend `G_` if the result starts with a digit (Silver identifiers cannot start with a digit).
4. Silver field names are globally scoped; qualify them as `{mangledPkg}_{TypeName}_{FieldName}`
   to avoid collisions across packages (same convention as plan 21).
5. Silver method and function names: `{mangledPkg}_{funcName}` for package-level functions;
   `{mangledPkg}_{TypeName}_{methodName}` for methods.
6. Auxiliary generated names (tuple domains, box/unbox functions, etc.) use a `gobra__` prefix
   to avoid collisions with user-defined Silver names.

This scheme is injective because: package separators (`/`, `.`) become `_`; the `_u{hex}_`
encoding is unambiguous; the `gobra__` prefix is reserved and never emitted for user names.

**Note on collision with Scala Gobra**: The Scala Gobra uses a similar but not identical
mangling scheme. Differential testing (plan 34) compares verification *results*, not Silver
output, so scheme differences between Go-Gobra and Scala Gobra are acceptable as long as the
generated Silver is valid and produces correct results.

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
has been checked against the Scala Gobra. Plans with this requirement: 20, 23, 25, 27.

## Resolved Questions

**Encoding state (resolved):** Encodings are stateful objects (matching the Scala implementation).
Each encoding accumulates auxiliary Silver definitions (domains, functions, fields) in its
internal state and emits them at the end of translation. The `Context` holds references to all
encoding objects (initialized together at translator startup), allowing mutual access.
