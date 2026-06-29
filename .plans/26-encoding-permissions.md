# 26 — Encoding: Permissions & Predicates

## Objective

Implement the encoding of Gobra's permission model: access permissions (`acc`), predicate
definitions and calls, fractional permissions, wildcards, magic wands, and the ghost heap.

## Scope

**In scope:**
- `acc(e)` — full permission to a field or predicate
- `acc(e, p)` — permission amount `p` (fraction, wildcard, `write`, `none`)
- `wildcard` permission
- Fractional permissions: `1/2`, `p/q` where `p`, `q` are expressions
- `FullPerm`, `NoPerm`, `WildcardPerm` Silver constructs
- Permission arithmetic: `p + q`, `p - q`, `p * q`, `p / q`
- Predicate declarations: `pred P(x T) { body }`
- Predicate calls in assertions: `P(e)`
- `fold P(e)` / `unfold P(e)` statements
- Magic wands: `A --* B`
- `package (A --* B) { proof }` and `apply (A --* B)`
- Ghost-only heap locations

**Out of scope:**
- Standard Go field access permissions (handled per-encoding in 21–24)
- Method/function contracts (27)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/` — permission-related encodings
- Look for predicate encoding, permission expression encoding
- Silver's `AccessPredicate`, `PredicateAccessPredicate`, `FractionalPerm`, `WildcardPerm`

## Proposed Approach (from Scala source analysis)

**User-defined predicates** map directly to Silver predicates. No desugaring required:
`pred P(x T) { body }` → Silver `predicate P(x: silverT) { silverBody }`.

**`acc(e)` and `acc(e, p)`**: translate to Silver `AccessPredicate` or
`PredicateAccessPredicate` depending on whether `e` is a field access or predicate call.
Full permission (`write`) → `FullPerm()`. Wildcard → `WildcardPerm()`.

**Fractional permissions** `p/q`: → Silver `FractionalPerm(p, q)`. Permission expressions
`p + q`, `p - q`, `p * q`, `p / q` → Silver `PermAdd`, `PermSub`, `PermMul`, `PermDiv`.

**Magic wands** `A --* B`: → Silver `MagicWand(A, B)`.
- `package (A --* B) { proof }`: → Silver `Package(wand, proof)` statement.
- `apply (A --* B)`: → Silver `Apply(wand)` statement.

**`fold P(e)` / `unfold P(e)`**: → Silver `Fold` / `Unfold` statements directly.

**Ghost-only heap locations** are standard Silver fields with no corresponding Go field;
their Silver field declarations are emitted by the relevant encoding module.

## Deliverables

- `internal/translator/encodings/permissions.go`
- Tests: encode predicate definition, fold/unfold, fractional permission, magic wand

## Verification Specifications (C9)

```go
// EncodeAcc postcondition — result is always a Silver permission expression node:
//@ requires e != nil && p != nil
//@ ensures  result != nil
//@ ensures  typeOf(result) == *silver.AccessPredicate ||
//@          typeOf(result) == *silver.PredicateAccessPredicate
//@ ensures  result.Perm() != nil
//@ decreases
func EncodeAcc(e silver.Expr, p silver.Expr) (result silver.Node)
```

**Permission amount range contract**: Every permission amount expression produced by this encoding represents a value in the range `(0, 1]` in Silver's permission algebra. `NoPerm` (0) is never used as the amount inside an `acc()` assertion emitted by this module — a zero-permission `acc` is a no-op that Silver's verifier rejects as a type error.

```go
//@ ensures p != nil ==> !isNoPerm(p)   // FullPerm, WildcardPerm, or FractionalPerm > 0
```

**Fold/Unfold postcondition**: `EncodeFold` and `EncodeUnfold` each produce a Silver `Fold` or `Unfold` statement node wrapping a `PredicateAccessPredicate`. They never return an expression node.

```go
//@ ensures typeOf(result) == *silver.Fold || typeOf(result) == *silver.Unfold
```

**Magic wand structural invariant**: `EncodeMagicWand(a, b)` produces a `silver.MagicWand` node where `Left == a` and `Right == b`. Neither `Left` nor `Right` is `nil`.

```go
//@ requires a != nil && b != nil
//@ ensures  result != nil && result.Left == a && result.Right == b
//@ decreases
func EncodeMagicWand(a, b silver.Assertion) (result *silver.MagicWand)
```

**No-panic contract**: No function in this encoding panics on well-typed input. All panics are reserved for internal consistency violations (e.g., a node type that should have been caught by the type checker). Termination is guaranteed for any finite input AST.

```go
//@ decreases inputSize(e)
```
