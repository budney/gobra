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
- Run Go-Gobra on each file (invoking the pipeline from 33)
- Compare actual errors to expected errors; report pass/fail per file
- Integration with `go test`: implement as `TestMain` or table-driven tests so `go test ./...`
  runs the regression suite
- Parallel test execution (Go's test framework supports `-parallel N`)
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

## Open Questions

- Should differential testing against Scala Gobra run as part of CI, or only on demand?
  On-demand is safer (Scala Gobra has a slow startup time).
