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

This unblocks 03-frontend-ast.md. Plan 05 (annotation-parser.md) is also blocked by 03; resolving this plan removes 02's blocker from plan 05 but plan 05 cannot start until plan 03 is also complete.

Rationale: The regression test suite is the primary validation mechanism throughout this
project; breaking it at the start adds unnecessary risk with no clear benefit for a solo
implementation effort.

## Deliverables

- This file updated with the chosen option and any syntax extension notes
- The `## Annotation Grammar` section of this file (see below) — **owned and written by plan 05**,
  not by this plan. This plan records the syntax decision; plan 05 produces the grammar.

**Note on grammar sketch:** The grammar sketch is a deliverable of **plan 05** (annotation
parser). Plan 05 writes the completed grammar into the `## Annotation Grammar` section of THIS
file (02) upon completing the parser implementation. This file owns the section as a stable
home for the grammar; plan 05 owns the task of writing it. The grammar must cover:
  - `requires`, `ensures`, `preserves`, `invariant`
  - `acc()`, `unfolding`, `fold`/`unfold`
  - `pure`, `ghost`, `trusted`, `opaque`
  - `decreases [expr | _]` — termination measure (function and loop)
  - `assert`, `assume`, `inhale`, `exhale` — proof manipulation statements
  - Quantifiers (`forall`, `exists`)
  - Permission amounts (`1/2`, `wildcard`)
  - Assertion operators (`&&`, `||`, `==>`, `!`)
  - Magic wands (`A --* B`, `package`, `apply`)
  - Gobra-specific: `seq`, `set`, `mset`, `dict`, `option`, ADT constructors
  - Ghost fields, ghost methods, ghost return values

Plan 05 references `src/main/antlr4/GobraParser.g4` and `GobraLexer.g4` as the authoritative
source while writing the grammar. Those files reside in the Scala source tree scheduled for
deletion at cut-over (DECISIONS.md D8). Before cut-over, the grammar sketch must be written
into the `## Annotation Grammar` section of this file so the specification survives after the
Scala source is removed. **This is a blocking deliverable for cut-over.**

**Gating condition**: The grammar sketch must be completed and committed to the `self-hosting`
branch **before** the cut-over commit described in plan 37 and DECISIONS.md D8. Plan 37's
success criteria include a checklist item: "annotation grammar sketch in `## Annotation Grammar`
section of `02-annotation-syntax-decision.md` is complete." The cut-over commit must be
blocked until this item is checked.

**Independent validation procedure**: Once the grammar sketch exists in `## Annotation Grammar`:
1. For each production in the sketch, locate the corresponding rule in `GobraParser.g4` or
   `GobraLexer.g4` and confirm the coverage matches. Mark each production ✓.
2. Confirm that every annotation form listed in the Deliverables section above has at least
   one production rule in the sketch. Missing forms are blocking.
3. Confirm that all plan 05 table-driven test cases parse correctly when the parser is driven
   by this grammar as specification (i.e., the sketch is consistent with the implemented parser).

This plan is not complete until all three checks pass.

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
patterns that the Gobrafier recognizes by regex. Document the full list in plan 06 (the
Gobrafier owns the regex-based stripping logic; plan 05 only sees the already-reduced
`//@ ...` fragment).

**Multi-line annotations**: Consecutive `//@ ` lines in the same comment block are
concatenated (with synthetic newlines for position tracking) before lexing. A single
logical annotation can span multiple `//@ ` lines:

```go
//@ requires acc(x) &&
//@          acc(y)
func f(x, y *int) { ... }
```

Position mapping: the byte offset within the concatenated string maps back to the original
comment line's `token.Pos` plus the character offset within that line. Plan 05 must implement
this mapping and must not invent an incompatible position scheme.

## Annotation Grammar

*This section is owned and written by plan 05 upon completing the annotation parser
implementation. It must be filled in before the cut-over commit (plan 37 hard gate). See
Deliverables above for the coverage requirements and validation procedure.*

<!-- plan 05: write the full BNF/EBNF grammar here -->

