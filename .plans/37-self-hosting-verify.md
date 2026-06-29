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

## Bootstrapping Sequence

This plan has two sub-milestones:

1. **Phase 1 complete**: Scala Gobra verifies Go-Gobra's annotated source without errors.
   This validates that the specs are correct and the implementation matches them. It is a
   prerequisite for Phase 2.

2. **Phase 2 complete (true self-hosting)**: Go-Gobra verifies its own annotated source and
   agrees with Scala Gobra's results. Any divergence is a bug in Go-Gobra. This is the
   project's completion milestone.

If Go-Gobra and Scala Gobra disagree on a verification result, Scala Gobra is the oracle (D6).

## Success Criteria

The milestone is achieved when Go-Gobra (Phase 2) successfully verifies at least the core
pipeline (parser through translator) using Silicon, with no `//@ assume` or `//@ trusted`
shortcuts on the critical path, and the result matches the Scala Gobra run (Phase 1).

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

## Cut-Over Checklist

Before the cut-over commit (delete Scala source, promote `gobra-go/` to repo root — see
DECISIONS.md D8), all of the following must be satisfied:

- [ ] Annotation grammar sketch is complete in `02-annotation-syntax-decision.md` (a
      `## Annotation Grammar` section with full BNF/EBNF coverage of all annotation forms).
      This is a **hard gate** — the cut-over deletes `src/main/antlr4/GobraParser.g4` and
      `GobraLexer.g4`, which are the only complete specification of the annotation grammar.
      **Owner: Plan 05** is responsible for writing the `## Annotation Grammar` section in
      `02-annotation-syntax-decision.md` before plan 37 cut-over proceeds.
- [ ] Phase 2 self-hosting verification succeeds (Go-Gobra verifies its own source).
- [ ] CI job `gobra-self-verify` is passing for all blocking-tier modules.
- [ ] `SELF_HOSTING.md` is written and up-to-date.
- [ ] All regression tests pass (or are in `skip.txt` with documented reasons).
- [ ] Last Scala release is tagged (e.g., `v0.9.9-scala-final`) before the cut-over commit.
      **Owner: project lead / release manager** — this is a meta-project action, not part of any plan's code deliverables.
- [ ] **CI runner has Go ≥ 1.21 installed.** Go-Gobra requires `go` in `$PATH` at runtime for
      package resolution (`go list`). A runner with only the Go-Gobra binary but no Go toolchain
      will fail at plan-07 package resolution with an unclear error. The CI workflow for
      `gobra-self-verify` must include a `go` setup step (e.g., `actions/setup-go@v4` with
      `go-version: '1.21'`). The bootstrap sequence is:
      (1) build Go-Gobra with Go → (2) run Go-Gobra (requires `go` in PATH) → (3) verify source.

## CI Gate Strategy

The `gobra-self-verify` CI job has two tiers:

**Blocking tier** (fails the build if it regresses): verification of the following modules,
which are pure functional code with no CGo or concurrency:
- `internal/diagnostic/` — zero-dependency data types (added first; trivial to verify, establishes the CI gate)
- `internal/silver/` — Silver printer (pure function, no heap mutation)
- `internal/ast/internal/` — internal AST traversal
- `internal/desugar/` — desugarer (pure tree transformation)
- `internal/translator/mangle.go` — name mangler (pure function)

These are the recommended starting points from plan 37's "Approach" section. Once each module
verifies successfully, it is added to the blocking tier. The blocking tier starts with
`internal/diagnostic/` and grows as modules are verified; it never shrinks.

**Advisory tier** (runs on every push, reports results, does not block merges): everything
outside the blocking tier, including the type checker, the full translator, and any module
with `//@ trusted` boundaries. Advisory failures are tracked in `SELF_HOSTING.md` as known
gaps; they are not ignored, but they do not block development.

This two-tier structure allows CI to be useful from the first verified module while not
blocking merges on the harder modules that are still being annotated.

## Resolved Questions

**`//@ assume` for Z3 timeouts (resolved):** Yes, add `//@ assume` for specific goals that
Z3 cannot prove within a 60-second timeout. Document each one in `SELF_HOSTING.md` with the
goal text, the timeout observed, and any Z3 tuning attempted. Do not leave undocumented
assumptions in the code.

**CI blocking scope (resolved):** See "CI Gate Strategy" above. Block on the pure functional
modules; run full self-hosting as advisory until stable.
