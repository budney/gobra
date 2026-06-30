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
      DiagError   Category = iota // verification or type error; blocks subsequent pipeline stages
      DiagWarning                 // non-fatal; pipeline continues
      DiagInfo                    // informational; e.g. parse-only output
  )
  // DiagError/DiagWarning/DiagInfo names avoid shadowing the built-in `error` type
  // and the `errors` package sentinel in files that import both.

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
section. The following plans are affected: 04, 05, 06, 07, 08, 09, 10, 12, 13, 19, 32.
(Plan 19's `Translate` returns `(result *silver.Program, diags []diagnostic.Diagnostic)` —
consistent with all other pipeline stages — so plan 19 lists plan 32a as a dependency.)

## Verification Specifications (C9)

**C9: N/A** — This plan defines only data type declarations (`Diagnostic` struct, `Category`
int type, and three constants). There are no functions with pre/postconditions to specify.
The struct fields carry no invariants beyond Go type-system guarantees (e.g., `Category` is
an `int` with no enforced range at construction time; pipeline stages are responsible for
using only the declared constants). The unit test ("construct a `Diagnostic`, verify the
fields are accessible") is the sole validation artifact per C8.
