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

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type returned by `CheckSpecs`
- [08-type-checker-core.md](08-type-checker-core.md) — base type checker; spec checking extends it

## Reference: Current Gobra

- `src/main/scala/viper/gobra/frontend/info/implementation/typing/` — spec expression typing
- `src/main/scala/viper/gobra/frontend/info/implementation/property/` — structural constraints
  (e.g., `pure` function validity, `old` placement checks)
- `src/main/scala/viper/gobra/frontend/info/implementation/resolution/MemberPath.scala` —
  used for field/predicate access resolution in `acc()`

## Deliverables

- `internal/info/specchecker.go` — `CheckSpecs(pkg *frontend.PPackage, info *TypeInfo) []Diagnostic`
  Entry point for the spec type-checking pass. Mutates `info.Ghost` in place.
- `internal/info/specvisitor.go` — the spec expression visitor; one visitor method per spec
  AST node type handled. The following spec-AST node types are handled by `CheckSpecs`:
  - `PRequires`, `PEnsures`, `PPreserves` — precondition/postcondition/preservation clauses
  - `PInvariant` — loop invariant expression
  - `PDecreases` — termination measure; element type must be well-ordered
  - `PAccess` (`acc(e)`, `acc(e, p)`) — `e` must be a field access or predicate call
  - `PForall`, `PExists` — quantifier; bound variable scoping; body must be `bool`
  - `POld` — `old(e)`; only valid inside postconditions
  - `PBefore` — `before(e)`; only valid inside `preserves` clauses
  - `PMagicWand` — `A --* B`; A and B must be permission expressions
  - `PSeq`, `PSet`, `PMSet`, `PDict`, `POption` — ghost collection expressions
  - `PMatch` — pattern match; exhaustiveness check
  - `PGhostCall` — ghost function/method call
  - `PUnfolding` — `unfolding P in e`
  - `PPure`, `PTrusted`, `POpaque`, `PMayBeUsedInInit` — function modifier constraints
- `internal/info/ghosttypes.go` — `GhostTypeInfo`, `GhostType`, and all concrete implementations
  (already defined in plan 08; plan 09 populates the `GhostTypeInfo.Types` map)
- Structural constraint checks integrated into the checker pass (see Scope above)
- Tests: annotation-heavy regression files from `src/test/resources/regressions/features/`

## Resolved Questions

**Separate pass vs. integrated (resolved):** Spec type checking runs as a **separate pass**
after the core Go type checker (plan 08). Plan 08's `Check` function returns a `*TypeInfo`
(the concrete exported struct, not an interface) with `TypeInfo.Go` populated and
`TypeInfo.Ghost` zero-valued. Plan 09's entry point is:

```go
// CheckSpecs annotates ghost and spec nodes in pkg, filling in info.Ghost.
// Must be called after plan 08's Check (info.Go must be populated).
// Returns additional diagnostics; callers accumulate these with plan 08's diagnostics.
func CheckSpecs(pkg *frontend.PPackage, info *TypeInfo) []Diagnostic
```

Plan 09 receives the same `*TypeInfo` pointer returned by plan 08 and mutates `info.Ghost`
directly — no type assertion or interface casting needed, because `TypeInfo` is a concrete
struct. This matches the current Gobra architecture and avoids complicating the core type
checker with spec-specific rules.

## Verification Specifications (C9)

```go
// CheckSpecs pre/postcondition:
//@ requires pkg != nil && info != nil && info.Go != nil
//@ ensures  len(result) >= 0
//@ ensures  forall i int :: 0 <= i && i < len(result) ==> result[i] != (Diagnostic{})
//@ ensures  len(result) == 0 ==> info.Ghost.Resolved()
//@ decreases
func CheckSpecs(pkg *frontend.PPackage, info *TypeInfo) (result []Diagnostic)
```

**Contracts:**
- `CheckSpecs` requires `info != nil` and `info.Go != nil`; calling before plan 08's `Check` completes is a precondition violation.
- If `CheckSpecs` returns a non-empty diagnostics slice, `info.Ghost` may be partially populated; callers must not use `info.Ghost` if any diagnostics have `Category == DiagError`.
- Result slice is never nil; an empty slice (not nil) is returned when no errors are found.

**Spec-scope balance invariant**: Every quantifier bound-variable scope opened during spec traversal must be closed before `CheckSpecs` returns. This is an implementation-internal invariant tracked by a ghost local counter inside the visitor loop:

```go
// Inside the spec visitor loop body:
//@ ghost var scopeDepth int = 0  // declared as ghost local, not a function parameter
//@ invariant scopeDepth >= 0
// On exit:
//@ assert scopeDepth == 0   // verified at end of traversal, not as function postcondition
```

**Incremental-fill postcondition** (matches plan 08 C9 contract): `info.Ghost.Types` entries are filled monotonically — once set for a node they are never cleared. This allows callers to partially consume `info.Ghost` while `CheckSpecs` is still running (e.g., parallel spec checking of independent packages).

```go
//@ ensures forall n PNode :: old(info.Ghost.Types[n]) != nil ==> info.Ghost.Types[n] == old(info.Ghost.Types[n])
```
