# 35 — Regression Test Suite

## Objective

Port the existing Gobra regression test corpus to Go-Gobra, run it to establish a baseline,
and achieve full pass rate parity with the Scala implementation.

## Scope

**In scope:**
- Copy all test files from `src/test/resources/regressions/` into the Go-Gobra test directory
- For each test category (features/, examples/, regressions/), run Go-Gobra and compare to
  Scala Gobra's results
- Track which tests pass, which fail, and which are known failures (skip list)
- Identify gaps: tests that expose missing features and file them as sub-issues
- Achieve ≥95% pass rate with a documented skip list before proceeding to self-hosting (36).
  A 100% gate is impractical: obscure failures in channels, generics, or edge cases could
  block the self-hosting milestone indefinitely. The skip list (`tests/testdata/skip.txt`)
  must include an explanation for each skipped test.
- Self-hosting annotation work (36) may begin in parallel once the skip list is stable and
  the remaining failures are understood.
- Run continuously in CI to prevent regressions

**Out of scope:**
- Writing new test cases (the existing corpus is the target)
- Performance tests

## Dependencies

- [34-test-infrastructure.md](34-test-infrastructure.md) — test runner

## Key Test Categories in Current Gobra

- `regressions/features/` — one directory per language feature (slices, maps, interfaces,
  channels, generics, ADTs, permissions, termination, overflow, ...)
- `regressions/examples/` — larger end-to-end examples (swap, concurrent stack, ...)
- `regressions/issues/` — regression tests for specific bug fixes

## Approach

1. Start by running the full corpus and measuring the initial pass rate
2. Prioritize failures by feature (fix all slice failures before moving on, etc.)
3. Use the Scala Gobra as oracle: if behavior differs, Scala Gobra is correct
4. Maintain a `testdata/skip.txt` (or equivalent) for known failures with explanations

## Deliverables

- All test files copied/linked into `tests/testdata/regressions/`
- `tests/testdata/skip.txt` — skip list with one entry per skipped test and a reason
- Passing rate tracked in `tests/COVERAGE.md`
- CI job running the full regression suite on every push

## Skip List Format (Resolved)

`tests/testdata/skip.txt` uses a machine-parseable format, one entry per line:

```
# Comments start with #
tests/testdata/regressions/features/generics/basic.gobra SKIP:generics-not-implemented
tests/testdata/regressions/features/channels/select.gobra SKIP:select-not-implemented
tests/testdata/regressions/issues/123.gobra SKIP:known-z3-timeout
```

Fields (tab- or space-separated):
1. Path relative to the `gobra-go/` directory
2. `SKIP:<reason-slug>` — one of a fixed set of reason slugs:
   - `generics-not-implemented`
   - `feature-not-implemented` (other missing features)
   - `known-z3-timeout`
   - `known-false-negative` (Go-Gobra accepts what Scala Gobra rejects — investigate separately)
   - `known-false-positive` (Go-Gobra rejects what Scala Gobra accepts — investigate separately)

The CI job that runs the suite must fail if a test in the skip list now passes (i.e., skip
entries must be pruned as features are implemented). This prevents the skip list from silently
accumulating stale entries.
