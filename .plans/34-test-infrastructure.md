# 34 — Test Infrastructure

## Objective

Build the test runner framework that discovers `.gobra` regression test files, runs Go-Gobra
on them, and compares results to the expected outcomes declared in `//@ expectedError` and
`//@ expectedOutput` annotations.

## Scope

**In scope:**
- Test file discovery: walk `src/test/resources/regressions/` (or a Go-Gobra equivalent)
  and collect `.gobra` test files
- Parse test metadata from each file:
  - `//@ expectedError <errorId>` — expect a specific error at this line
  - `//@ unexpectedError` — expect no error at this line (regression guard)
  - `//@ expectedOutput <text>` — expect specific stdout
- Run Go-Gobra on each file (invoking the pipeline from 33 via its Go API, not subprocess)
- Compare actual errors to expected errors; report pass/fail per file
- Integration with `go test`: implement as `TestMain` or table-driven tests so `go test ./...`
  runs the regression suite
- **JNI/JVM coordination for parallel tests**: The JVM is a process-wide singleton (plan 15);
  all JNI calls are routed through a single JNI worker goroutine via a channel. Test goroutines
  running in parallel safely share this single JNI worker — they block on the channel until
  their JNI request is served. `go test -parallel N` is safe because the serialization happens
  inside the JNI worker, not at the test level. The JVM is initialized once in `TestMain` and
  shut down after all tests complete.
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
run concurrently. Before plan 17b (pool expansion), all JNI calls are serialized through a
single worker, so `-parallel N` increases goroutine scheduling overhead without improving
throughput — use `-parallel 1` for the plan 15/17 baseline. After plan 17b, set `-parallel`
to match the pool size: `go test -parallel $(nproc)` is appropriate. Beyond `runtime.NumCPU()`
workers, memory use increases (each worker holds a Silicon + Z3 process) with no throughput
gain. Document the recommended value in the CI workflow and README.
