# 36 — Self-Hosting: Write Specifications

## Objective

Annotate the Go-Gobra source code with Gobra specifications — preconditions, postconditions,
invariants, predicate definitions — sufficient to allow Go-Gobra to verify itself. This is the
intellectual heart of the self-hosting milestone.

## Scope

**In scope:**
- Identify the key invariants of each pipeline stage and express them as Gobra specs:
  - Parser: output AST is well-formed (no nil nodes where non-nil required)
  - Type checker: output TypeInfo is consistent (every node has a type, scopes are balanced)
  - Desugarer: output internal AST is well-formed
  - Translator: output Silver AST is well-formed (Silver program is valid)
  - JNI backend: resource safety (JVM is started before any JNI calls)
- Write ghost helper functions and predicates as needed (e.g., `ValidAST(n Node) bool`)
- Annotate data structure invariants (e.g., scope chain properties, Silver program properties)
- Ghost fields on structs where needed (if Go-Gobra's annotation language supports them)
- Prioritize: start with the simplest modules (CLI, reporter, Silver printer) and work up
  to the hardest (type checker, translator)

**Out of scope:**
- Achieving successful verification (37-self-hosting-verify.md)
- Modifying the core algorithm to make verification feasible (only specs and ghost code)

## Dependencies

- [35-regression-suite.md](35-regression-suite.md) — Go-Gobra must be functionally complete
  before self-hosting annotation begins

## Bootstrapping Sequence

Self-hosting has two distinct phases with different verifiers:

1. **Phase 1 (Scala Gobra verifies Go-Gobra)**: The initial annotations are verified using
   Scala Gobra as the verifier. This is the trusted baseline — Scala Gobra is the oracle for
   all verification during the port (D6). This phase produces a Go-Gobra that is externally
   verified to be correct in the specified properties.

2. **Phase 2 (Go-Gobra verifies Go-Gobra)**: Once Go-Gobra is externally verified (Phase 1),
   use Go-Gobra to re-verify its own source. If Go-Gobra is correct, both runs should agree.
   Divergence between the two indicates a bug in Go-Gobra. This is true self-hosting and is
   the milestone for plan 37.

Start all annotation work targeting Scala Gobra as the verifier (Phase 1); Phase 2 follows
naturally in plan 37.

## Notes

This is the most open-ended and creative part of the project. There is no fixed checklist;
the goal is to write enough specs that Go-Gobra can prove its own correctness properties.
Expect to iterate between this phase and 37 many times.

**Start small**: annotate and verify one module at a time. The Silver printer (in 14) is a
good first target — it's a pure function with no heap allocation beyond string building.

## Deliverables

- Gobra annotations throughout `internal/` and `cmd/` packages
- Ghost helper library in `internal/ghost/` (predicates, lemmas)
- Document which properties are proved vs. trusted
- **Validation**: run `scala-gobra -i internal/...` (Phase 1) and confirm zero verification
  errors on each annotated module before moving to the next. Each module must pass in
  isolation using Scala Gobra as the verifier before the full-program self-hosting run in
  plan 37.

## Unsafe Operations

Go-Gobra itself uses CGo (via jnigi) and may indirectly use `unsafe.Pointer` in the JNI
layer. Gobra cannot currently reason about `unsafe.Pointer` semantics. For self-hosting:

- Mark the entire `internal/backend/jvm/` package as `//@ trusted` at the package boundary.
  The JVM lifecycle and JNI calling convention is verified by design-review and testing, not
  by formal proof.
- Document each `//@ trusted` annotation in `SELF_HOSTING.md` with the reason and the
  test/invariant that stands in for the missing proof.
- The stretch goal (plan 37) of verifying the JNI backend layer is explicitly post-milestone.

## Open Questions

- Which safety properties are most important to prove? Memory safety (no nil dereferences)
  and type soundness of the Silver output are the highest-value targets. Start with the Silver
  printer (pure function, no heap mutation) and work inward toward the type checker.

## Annotation Language Gaps

If the current Gobra annotation language cannot express a required invariant, **extending the
language is in scope** (see D11 in DECISIONS.md). When a gap is discovered:

1. Document it in `SELF_HOSTING.md`: which invariant, which code path, why it can't be
   expressed.
2. Decide: is there a code refactoring that makes the invariant expressible within the
   current language? If yes, prefer refactoring (it produces cleaner code). If no, extend
   the annotation language.
3. Track each extension as a deliverable in this plan. Extensions needed for self-hosting
   are first-class features, not workarounds.

The same applies to code structure: if a module in Go-Gobra has an invariant that requires
deep changes to verify, refactoring is preferred over an ever-expanding `//@ trusted` boundary.
