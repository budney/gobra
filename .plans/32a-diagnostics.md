# 32a — Diagnostic Type

## Objective

Define the `Diagnostic` type used by every pipeline stage to accumulate and report errors and
warnings. This is a zero-dependency support package; all other plans that accumulate or return
errors import from here.

## Scope

**In scope:**
- The `Diagnostic` struct and `Category` type
- The `internal/diagnostic/` package with no imports from any other Go-Gobra package

**Out of scope:**
- Formatting or displaying diagnostics (that is plan 32's job)
- Collecting or filtering diagnostics (each pipeline stage manages its own `[]Diagnostic` slice)

## Dependencies

None. This plan has no dependencies on any other plan. It is a prerequisite for all plans
that produce or consume diagnostics.

## Reference: Current Gobra

- `src/main/scala/viper/gobra/reporting/` — the Scala `VerifierError` / `VerifierWarning`
  hierarchy provides the model; Go-Gobra flattens this into a single struct with a `Category`
  discriminant for simplicity.

## Deliverables

- `internal/diagnostic/diagnostic.go`:

  ```go
  package diagnostic

  // Category classifies a Diagnostic for filtering and display.
  type Category int

  const (
      Error   Category = iota // verification or type error; blocks subsequent pipeline stages
      Warning                 // non-fatal; pipeline continues
      Info                    // informational; e.g. parse-only output
  )

  // Diagnostic is a structured error or warning produced by any pipeline stage.
  // Every stage returns []Diagnostic; the pipeline aborts if any Error-category
  // diagnostics are present after a stage completes (see plan 00 cross-cutting contract).
  type Diagnostic struct {
      File     string   // source file path (empty for synthetic/internal diagnostics)
      Line     int      // 1-based; 0 if unknown
      Col      int      // 1-based; 0 if unknown
      Message  string
      Category Category
  }
  ```

- Unit test: construct a `Diagnostic`, verify the fields are accessible.

## Placement Note

This package must be defined in `internal/diagnostic/` — **not** in `internal/reporting/`.
`internal/reporting/` (plan 32) imports from here. The separation ensures that early-pipeline
plans (04 through 13) can use `Diagnostic` without incurring a dependency on the late-pipeline
reporting package.

Every plan that produces `[]Diagnostic` must list plan 32a as a dependency in its Dependencies
section. The following plans are affected: 04, 05, 06, 07, 08, 09, 10, 12, 13, 32.
(Plan 19's `Translate` returns `(*silver.Program, error)` — translation panics on internal bugs
rather than accumulating diagnostics — so plan 19 does not need this dependency.)
