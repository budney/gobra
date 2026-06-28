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
- Entry criteria for downstream plans (two-stage gate):
  - **Plan 36 (self-hosting annotations)** may begin once the skip list is stable and all
    remaining failures are understood (i.e., every failing test has a documented `SKIP:` entry
    with a known reason). The ≥95% threshold need not be met yet — annotation work and
    late-stage bug fixing can proceed in parallel.
  - **Plan 37 (self-hosting verification)** requires ≥95% pass rate (counting only non-skipped
    tests) with a documented skip list. A 100% gate is impractical — obscure failures in
    channels, generics, or edge cases could block self-hosting indefinitely. The skip list
    (`tests/testdata/skip.txt`) must include an explanation for each skipped test before
    plan 37 begins.
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

**Pre-populating the generics skip list:** Before the first test run, identify all test files
that declare generic functions or types and pre-mark them as `SKIP:generics-not-implemented`.
Use a pattern that targets generic *declarations* (type parameter lists on `func` or `type`),
not array types or other bracket uses:

```bash
find src/test/resources/regressions -name "*.gobra" \
  | xargs grep -El "^\s*(func|type)\s+\w+\s*\[" \
  | sort
```

This matches lines like `func F[T any](...)` and `type Stack[T any] struct{...}` but not
array types (`[N]T` always appears in a type position, not immediately after a name).
Review matches manually for false positives (e.g., slice-typed return types on the same
line as a declaration), then add confirmed generics tests to `tests/testdata/skip.txt`.

## Deliverables

- All test files copied/linked into `tests/testdata/regressions/`
- `tests/testdata/skip.txt` — skip list with one entry per skipped test and a reason
- Passing rate tracked in `tests/COVERAGE.md`
- CI job running the full regression suite on every push
- `Makefile` target `prune-skips`: runs the full suite, identifies skip-listed tests that
  now pass (unexpected passes), and prints the `skip.txt` lines that should be removed.
  Does **not** modify `skip.txt` automatically — the developer reviews the output and removes
  entries manually, preserving intentional review of "why is this now passing?" Example output:

  ```
  PRUNE: tests/testdata/regressions/features/generics/basic.gobra
         reason was: generics-not-implemented
         test now passes — remove from skip.txt
  ```

  Implement as a thin shell wrapper around `go test -run TestRegression ./tests/... -v 2>&1
  | grep UNEXPECTED_PASS`; the test runner (plan 34) already emits `UNEXPECTED_PASS:` lines
  for unexpected passes.

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

**Test runner requirement**: the runner (plan 34) must run skip-listed tests and compare their
result to the skip expectation, not simply omit them. Concretely:
- Load `skip.txt` at startup.
- For each skip-listed test, run Go-Gobra normally but mark the test as "expected to fail."
- If the test *passes* (no errors when errors were expected, or vice versa), report it as an
  **unexpected pass** and fail the CI job.
- If the test *fails as expected*, report it as skipped (not counted against pass rate).
This two-mode design means adding a new feature automatically flags stale skip entries on the
next CI run without any manual bookkeeping.
