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
