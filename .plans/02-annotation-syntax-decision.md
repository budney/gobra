# 02 — Annotation Syntax Design Decision

## Objective

Resolve the annotation language design before any parser work begins. The choice affects the
frontend AST shape, the annotation parser grammar, and compatibility with existing `.gobra` files
and the regression test suite.

## Scope

**In scope:**
- Evaluate the two main options (keep `//@ ...` vs. redesign) and pick one
- Document the decision and its implications in this file
- If `//@ ...` is kept: specify which Gobra annotation forms must be supported and in what order
- If redesigned: specify the new syntax and a migration path for existing `.gobra` test files

**Out of scope:**
- Implementing the parser (that's 05-annotation-parser.md)
- Changing the semantics of annotations (only syntax is in scope here)

## Dependencies

- [01-project-setup.md](01-project-setup.md) — repository must exist before committing this decision

## The Choice

### Option A: Keep `//@ ...` syntax (recommended)

Gobra annotations appear as specially-prefixed Go comments:

```go
//@ requires acc(x)
//@ ensures  *x == old(*x) + 1
func increment(x *int) {
    *x++
}
```

**Pros:**
- Full compatibility with existing `.gobra` files and the regression test suite
- `go/parser` preserves comment nodes; annotation extraction is straightforward
- No migration effort

**Cons:**
- Go IDEs treat annotations as plain comments (no syntax highlighting without a plugin)
- Error messages from the annotation parser must reconstruct source positions from comment offsets

### Option B: Redesign annotation syntax

Examples: separate `.spec` files, Go build tag blocks, structured comment directives.

**Pros:**
- Opportunity to improve ergonomics and toolability
- Could enable tighter integration with Go tooling

**Cons:**
- Breaks all existing `.gobra` files and tests
- Requires a migration tool or parallel test corpus
- No clear consensus on a better design; risk of churn

## Decision

**Record the chosen option here once resolved. Until then, 03-frontend-ast.md and**
**05-annotation-parser.md are blocked.**

Recommendation: **Option A** (keep `//@ ...`). The regression test suite is the primary
validation mechanism throughout this project; breaking it at the start adds unnecessary risk
with no clear benefit for a solo implementation effort.

## Deliverables

- This file updated with the chosen option and any syntax extension notes
- A brief annotation grammar sketch (BNF or pseudocode) covering:
  - `requires`, `ensures`, `preserves`, `invariant`
  - `acc()`, `unfolding`, `fold`/`unfold`
  - `pure`, `ghost`, `trusted`
  - Quantifiers (`forall`, `exists`)
  - Permission amounts (`1/2`, `wildcard`)
  - Assertion operators (`&&`, `||`, `==>`, `!`)
  - Gobra-specific: `seq`, `set`, `mset`, `dict`, `option`, ADT constructors

## Open Questions

- Should `//@ ...` annotations be allowed mid-statement (current Gobra allows some), or only
  at statement/declaration boundaries?
- How are multi-line annotations handled? Current Gobra uses consecutive `//@ ` lines.
