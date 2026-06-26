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
- Name mangling utilities
- Tests: translate a trivial internal AST (empty method) and verify the Silver output shape

## Open Questions

- Should encodings be stateless (pure functions taking Context) or stateful (objects with
  accumulated state)? The current Gobra uses stateful encoding objects; this is simpler for
  encodings that need to accumulate auxiliary definitions (e.g., domain declarations).
