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
- `HasGenericDecl(f *ast.File) bool` — pure AST predicate that reports whether a parsed
  Go file contains at least one generic declaration; Gobra-annotated with the postcondition
  specified in plan 35; ghost predicates `funcDeclIsGeneric`, `genDeclHasGenericSpec`,
  `typeSpecIsGeneric` defined alongside it

- **Skip-list support** (consumed by plan 35): The runner accepts a `SkipConfig` at
  construction time:
  ```go
  type SkipConfig struct {
      File       string          // path to skip.txt; empty disables skip-list support
      ValidSlugs map[string]bool // slug set supplied by the caller (plan 35 defines the values)
  }
  ```
  When a `SkipConfig` is provided:
  1. Load and parse `skip.txt` at startup before any tests run.
  2. For each entry, validate the reason slug against `ValidSlugs`; if any slug is
     unrecognised, return a startup error immediately (no tests run).
  3. Run skip-listed tests normally but in "expected-to-fail" mode: if a skip-listed test
     *passes*, emit `UNEXPECTED_PASS: <path>` and count it as a CI failure; if it fails as
     expected, report it as skipped (not counted against the pass rate).
  The `SkipConfig` design keeps slug definitions out of this plan — plan 35 supplies the
  `ValidSlugs` map when it constructs the runner; plan 34 only enforces the contract.

**Out of scope:**
- The regression test files themselves (35-regression-suite.md)
- The specific set of valid reason slugs (defined in 35-regression-suite.md and passed in via `SkipConfig.ValidSlugs`)
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

- `internal/testing/runner.go` — test runner logic, including `SkipConfig` struct,
  skip-list loader/validator, and `HasGenericDecl(*ast.File) bool` with its ghost predicates
  (`funcDeclIsGeneric`, `genDeclHasGenericSpec`, `typeSpecIsGeneric`) and the Gobra
  postcondition specified in plan 35. `HasGenericDecl` is a pure function; its spec must
  be verified before plan 37 self-hosting verification begins.
- `tests/regression_test.go` — Go test file that plugs the runner into `go test`; supplies
  the `SkipConfig` (with `ValidSlugs` from plan 35) when constructing the runner
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

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/testing/runner.go` and verified
before this plan is considered complete.

1. **`HasGenericDecl` postcondition** — the function's result is true iff the file contains at
   least one generic function or type declaration. This is the canonical spec; plan 35 references
   it as the specification driver:

   ```go
   // HasGenericDecl reports whether f contains at least one generic function or type
   // declaration — i.e., an *ast.FuncDecl whose TypeParams list is non-empty, or an
   // *ast.TypeSpec (inside a *ast.GenDecl) whose TypeParams list is non-empty.
   //
   //@ requires f != nil && acc(f)
   //@ ensures  result ==
   //@     (exists i int :: 0 <= i && i < len(f.Decls) &&
   //@         (funcDeclIsGeneric(f.Decls[i]) || genDeclHasGenericSpec(f.Decls[i])))
   //@ pure
   //@ decreases
   func HasGenericDecl(f *ast.File) (result bool)

   //@ ghost pure func funcDeclIsGeneric(d ast.Decl) bool {
   //@     fd, ok := d.(*ast.FuncDecl)
   //@     return ok && fd.Type != nil && fd.Type.TypeParams != nil &&
   //@            len(fd.Type.TypeParams.List) > 0
   //@ }
   //@ ghost pure func genDeclHasGenericSpec(d ast.Decl) bool {
   //@     gd, ok := d.(*ast.GenDecl)
   //@     if !ok { return false }
   //@     return exists i int :: 0 <= i && i < len(gd.Specs) &&
   //@         typeSpecIsGeneric(gd.Specs[i])
   //@ }
   //@ ghost pure func typeSpecIsGeneric(s ast.Spec) bool {
   //@     ts, ok := s.(*ast.TypeSpec)
   //@     return ok && ts.TypeParams != nil && len(ts.TypeParams.List) > 0
   //@ }
   ```

   This spec is verified by plan 37's blocking tier before the self-hosting cut-over.

2. **`loadSkipList` fail-fast contract** — returns a non-nil list on success and nil on any
   parse or validation failure (including unrecognised slugs); never returns a partially-loaded
   list:

   ```go
   //@ requires cfg.File == "" || fileExists(cfg.File)
   //@ ensures  err == nil ==> list != nil
   //@ ensures  err != nil ==> list == nil
   //@ decreases
   func loadSkipList(cfg SkipConfig) (list *SkipList, err error)
   ```

3. **Test runner loop termination** — the runner iterates over a finite, statically-bounded set
   of discovered test files; the loop terminates:

   ```go
   //@ decreases len(pending)
   ```
