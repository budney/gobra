# 37 — Self-Hosting: Achieve Verification

## Objective

Run Go-Gobra on its own annotated source code (36) and achieve successful verification.
This is the project's completion milestone.

## Scope

**In scope:**
- Iteratively debug verification failures: distinguish between incorrect specs, missing specs,
  and genuine bugs in Go-Gobra
- Fix any bugs in Go-Gobra that self-verification reveals
- Strengthen or weaken specs as needed to make verification tractable for Silicon
- Handle Z3 timeouts: add triggers, split methods, add lemmas, adjust Silicon options
- Document what is proved (safety, functional correctness) vs. what is trusted
- Establish a CI job that runs Go-Gobra on itself on every push

**Out of scope:**
- Writing new specs (36)
- Achieving 100% functional correctness proof (a partial verification — even of one module —
  counts as meaningful self-hosting progress)

## Dependencies

- [36-self-hosting-annotations.md](36-self-hosting-annotations.md) — specs must be written

## Success Criteria

The milestone is achieved when Go-Gobra successfully verifies at least the core pipeline
(parser through translator) using Silicon, with no `//@ assume` or `//@ trusted` shortcuts
on the critical path.

Stretch goal: verify the JNI backend layer (this requires specs for the JVM interaction,
which is challenging but possible using ghost modeling of the JVM state).

## Approach

1. Start with the Silver printer (pure functional code, no heap) — easiest to verify
2. Move to the internal AST traversals (pure tree recursion)
3. Then the desugarer, translator core, individual encodings
4. Finally the type checker (most complex, highest value)

## Deliverables

- Successful `go-gobra -i internal/...` producing "Verification successful"
- CI job: `gobra-self-verify` running on every push to `master`
- `SELF_HOSTING.md` document describing what is proved, what is trusted, and known limitations

## Open Questions

- If Z3 cannot prove certain goals within a reasonable timeout, is it acceptable to add
  `//@ assume` for those specific goals? Yes, but document each one as a known gap.
- Should the self-hosting CI job block merges (like a test failure), or run as advisory?
  Block on a reduced scope; full self-hosting as advisory until stable.
