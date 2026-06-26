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

**Option A: keep `//@ ...` unchanged.**

This unblocks 03-frontend-ast.md and 05-annotation-parser.md.

Rationale: The regression test suite is the primary validation mechanism throughout this
project; breaking it at the start adds unnecessary risk with no clear benefit for a solo
implementation effort.

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

## Resolved Questions

**Inline (mid-statement) annotations**: In scope — full parity with the Scala implementation.
The Gobrafier handles specific inline patterns where a `//@ ...` comment appears on the same
line as the Go expression it annotates. Examples from the Scala Gobrafier:

```go
f(args) //@ with ghostArgs           // ghost argument injection at call site
return x, y //@ with ghostResult     // ghost result at return
x := rhs //@ as ghost_assignment     // ghost assignment annotation
f(x) //@ unfolding P(x)             // unfolding access inline
```

The annotation parser (05) must handle these in addition to standalone `//@ ...` lines.
The Gobrafier (06) strips the Go-side syntax; the annotation parser sees the `//@ ...`
portion. These are not arbitrary mid-expression placement — they are specific positional
patterns that the Gobrafier recognizes by regex. Document the full list in plan 05.

**Multi-line annotations**: Consecutive `//@ ` lines in the same comment block are
concatenated (with synthetic newlines for position tracking) before lexing. A single
logical annotation can span multiple `//@ ` lines:

```go
//@ requires acc(x) &&
//@          acc(y)
func f(x, y *int) { ... }
```
