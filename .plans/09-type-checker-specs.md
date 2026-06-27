# 09 — Type Checker: Specification Expressions

## Objective

Extend the type checker to handle Gobra specification expressions: preconditions, postconditions,
invariants, ghost statements, permission expressions, quantifiers, and all other annotation-layer
constructs. These have their own typing rules that differ from standard Go.

## Scope

**In scope:**
- Type checking all annotation AST nodes produced by the annotation parser (05)
- Typing rules for:
  - Permission expressions: `acc(e)`, `acc(e, p)` — `e` must be a field access or predicate call
  - Quantifiers: `forall`/`exists` — bound variable scoping and body type must be `bool`
  - `old(e)` — `e` must be a valid heap expression; only in postconditions
  - `before(e)` — only in `preserves` clauses
  - Magic wands `A --* B`
  - Sequence/set/multiset/dict/option expressions and their element types
  - ADT constructor expressions and `match`
  - Ghost function/method calls
  - `unfolding P in e`
  - Termination measures (`decreases e`) — must be a well-ordered type
- Checking structural constraints:
  - `requires`/`ensures` only on function/method declarations
  - `invariant` only in loop bodies
  - `pure` functions: no heap access, no ghost statements in body

**Out of scope:**
- Standard Go type checking (08-type-checker-core.md)
- Cross-package ghost type information (10)

## Dependencies

- [08-type-checker-core.md](08-type-checker-core.md) — base type checker; spec checking extends it

## Reference: Current Gobra

- `src/main/scala/viper/gobra/frontend/info/implementation/typing/` — spec expression typing
- `src/main/scala/viper/gobra/frontend/info/implementation/property/` — structural constraints
  (e.g., `pure` function validity, `old` placement checks)
- `src/main/scala/viper/gobra/frontend/info/implementation/resolution/MemberPath.scala` —
  used for field/predicate access resolution in `acc()`

## Deliverables

- Extension of `TypeInfo` covering all spec expression nodes
- Structural constraint checks integrated into the checker pass
- Tests: annotation-heavy regression files from `src/test/resources/regressions/features/`

## Resolved Questions

**Separate pass vs. integrated (resolved):** Spec type checking runs as a **separate pass**
after the core Go type checker (plan 08). Plan 08's `Check` function returns a `*TypeInfo`
covering standard Go constructs; plan 09 takes that `TypeInfo` plus the Gobra-specific AST
nodes and runs a second pass to annotate ghost/spec types. The two passes share the same
`TypeInfo` output struct (plan 09 fills in the `GhostTypeInfo` portion). This matches the
current Gobra architecture and avoids complicating the core type checker with spec-specific
rules. Plan 08 need not expose extension hooks beyond returning a partial `TypeInfo` — plan 09
reads it and appends to it.
