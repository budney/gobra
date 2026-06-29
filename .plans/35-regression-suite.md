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

Do **not** use a grep pattern for this. A text pattern cannot be formally related to Go syntax
and requires manual false-positive review. Instead, parse each file with `go/parser` and
inspect the resulting AST. The function `HasGenericDecl` (plan 34 deliverable,
`internal/testing/runner.go`) does this and carries a Gobra postcondition that makes its
correctness machine-checkable:

```go
// HasGenericDecl reports whether f contains at least one generic function or type
// declaration — i.e., an *ast.FuncDecl whose TypeParams list is non-empty, or an
// *ast.TypeSpec (inside a *ast.GenDecl) whose TypeParams list is non-empty.
//
// Correctness argument: go/parser is the authoritative Go parser. A declaration is
// syntactically generic iff go/parser sets a non-nil, non-empty TypeParams field on
// the corresponding AST node. HasGenericDecl checks exactly those fields, so its
// result is true iff the file contains a syntactically valid generic declaration.
//
// Caller requirement: f must be the result of parsing gobrafied source bytes, not raw
// .gobra bytes. Raw .gobra files contain Gobra-specific syntax (ghost parameters,
// pure/trusted modifiers, top-level adt/pred/ghost func) that is not valid Go;
// go/parser may fail on those declarations and produce a partial AST that silently
// omits generic declarations — a false negative. Gobrafied output is valid Go and
// produces a complete AST. See the pre-population sequence below.
//
//@ requires f != nil && acc(f)
//@ ensures  result ==
//@     (exists i int :: 0 <= i && i < len(f.Decls) &&
//@         (funcDeclIsGeneric(f.Decls[i]) || genDeclHasGenericSpec(f.Decls[i])))
//@ pure
//@ decreases
func HasGenericDecl(f *ast.File) (result bool)

// Ghost predicates used in the spec above:
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

`HasGenericDecl` is a pure function over an already-parsed `*ast.File`. The skip-list
pre-population script must gobrafy each file before parsing. Pre-population sequence
for each `.gobra` test file:

```go
src, err := os.ReadFile(path)
// ... handle err ...
gobrafied, diags := frontend.Gobrafy(src, path)
if len(diags) > 0 {
    // gobrafication failed; conservatively treat as non-generic
    // (the test run will catch it and it can be skip-listed manually)
    continue
}
fset := token.NewFileSet()
f, _ := parser.ParseFile(fset, path, gobrafied, parser.SkipObjectResolution)
if f != nil && HasGenericDecl(f) {
    // append entry to skip.txt
}
```

`parser.SkipObjectResolution` is used for speed (type-checking is not needed). Always
check `f != nil` before calling `HasGenericDecl` — `ParseFile` may return a non-nil
error alongside a non-nil partial AST, and a nil `f` must not be passed to
`HasGenericDecl`. Files where it returns `true` are added to `skip.txt` with reason
`generics-not-implemented`.

**Note**: plan 34 is responsible for implementing `HasGenericDecl` and its ghost predicates.
Plan 35 consumes it and specifies these Gobra annotations as a deliverable requirement on
plan 34. The self-hosting verification run (plan 37) will verify `HasGenericDecl`'s
postcondition as part of the blocking tier.

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

   The skip list parser **must reject any unrecognised reason slug as a parse error at startup**,
   failing the test run before any tests execute. This enforces the fixed set of slugs at the
   tool level rather than relying on convention — a typo like `SKIP:generics-unimplemented`
   is caught immediately rather than silently skipping the test with a wrong label.

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
