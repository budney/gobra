# 34 — Test Infrastructure

## Objective

Build the test runner framework that discovers `.gobra` regression test files, runs Go-Gobra
on them, and compares results to the expected outcomes declared in `//@ expectedError` and
`//@ expectedOutput` annotations.

## Scope

**In scope:**
- Test file discovery: walk `tests/testdata/regressions/` and collect `.gobra` test files
- Parse test metadata from each file:
  - `//@ expectedError <errorId>` — expect a specific error at this line
  - `//@ unexpectedError` — expect no error at this line (regression guard)
  - `//@ expectedOutput <text>` — expect specific stdout
- Run Go-Gobra on each file (invoking the pipeline from 33 via its Go API, not subprocess)
- Compare actual errors to expected errors; report pass/fail per file
- Integration with `go test`: implement as `TestMain` or table-driven tests so `go test ./...`
  runs the regression suite
- **JNI/JVM coordination for parallel tests**: The JVM is a process-wide singleton (plan 15).
  Test goroutines share the N-worker pool (plan 15b); each worker owns its own `SiliconFrontendAPI`
  instance and OS-thread lock. `go test -parallel N` is safe because goroutines that exceed
  pool capacity block inside `pool.Submit()` until a worker is free — serialization happens
  inside the pool, not at the test level. The JVM is initialized once in `TestMain` and shut
  down after all tests complete.
  - **Before plan 17b is implemented** (single-worker baseline): set `--workers 1` and
    `-parallel 1`. Extra parallel goroutines would all block in `Submit()` without improving
    throughput.
  - **After plan 17b**: set `-parallel ≤ --workers` to bound peak memory. Each worker holds
    one Silicon + one Z3 process; `-parallel` beyond the pool size adds goroutines that block
    in `Submit()` while holding `*silver.Program` ASTs in memory, wasting RAM with no
    throughput gain. Recommended: `-parallel $(nproc)` and `--workers $(nproc)`.
  Document the recommended value in the CI workflow and README.
- Differential mode: optionally run Scala Gobra on the same file and compare results

**Out of scope:**
- The regression test files themselves (35-regression-suite.md)
- Performance benchmarking (future work)

## Dependencies

- [33-cli.md](33-cli.md) — the pipeline must be callable from test code without spawning a
  subprocess (call the Go API directly for speed)

## Reference: Current Gobra

- `src/test/scala/viper/gobra/GobraTests.scala` — the Scala test runner; study the expected
  error annotation format and test discovery logic
- `src/test/resources/regressions/` — the test corpus; understand the directory structure
  and how tests are categorized (features/, examples/, ...)

## Deliverables

- `internal/testing/runner.go` — test runner logic
- `tests/regression_test.go` — Go test file that plugs the runner into `go test`
- Documentation on how to add new test cases

### Required output format for skip-list integration (plan 35 depends on this)

The runner must emit the following sentinel lines to stdout for the `prune-skips` Makefile
target (plan 35) to function correctly:

```
UNEXPECTED_PASS: tests/testdata/regressions/features/generics/basic.gobra
UNEXPECTED_FAIL: tests/testdata/regressions/features/foo/bar.gobra
```

- `UNEXPECTED_PASS:` — emitted when a test is in `skip.txt` but passes (stale skip entry).
- `UNEXPECTED_FAIL:` — emitted when a test is NOT in `skip.txt` but fails (regression).

Both are emitted in addition to the normal `go test` output (not instead of it). The
`prune-skips` target greps for `UNEXPECTED_PASS:` lines; CI failure checks grep for
`UNEXPECTED_FAIL:` lines. Do not use any other prefix for these sentinel lines — the
`prune-skips` implementation and CI scripts depend on exact string matching.

## Resolved Questions

**Differential testing cadence (resolved):** Differential testing against Scala Gobra runs
on-demand only, not in CI. Scala Gobra has significant startup time (JVM + Silicon init);
running it in CI on every push is impractical for a solo project. A `go test -run Differential`
target or a separate Makefile target is sufficient.

**Test timeout (resolved):** Always pass `-timeout 30m` (or longer) when running the full
regression suite. The default `go test` timeout of 10 minutes is too short for a large
corpus of Silicon verification jobs. CI must set `-timeout 30m` explicitly. Document this
in the repo README and the CI workflow file.

**Parallelism ceiling (resolved):** `go test -parallel N` controls how many test goroutines
run concurrently. Before plan 17b, set `--workers 1` and `-parallel 1` (single-worker baseline).
After plan 17b, the pool has N workers; set `-parallel ≤ --workers` to bound peak memory — each
worker holds one Silicon + one Z3 process. Goroutines beyond the pool size block in `Submit()`,
holding `*silver.Program` ASTs in memory with no throughput benefit. Recommended after plan 17b:
both `-parallel` and `--workers` at `$(nproc)`. Document in CI workflow and README.
