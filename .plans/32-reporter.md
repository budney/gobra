# 32 — Reporter & Error Mapping

## Objective

Implement the reporter: receive `VerificationError` objects from Silicon (17), map their
Silver source positions back to original Go source locations, and format human-readable
diagnostic output.

## Scope

**In scope:**
- Map a Silver position (method name + Silver statement/expression position) back to the
  Go source file, line, and column that generated it, using position info stored in the
  Silver AST (14)
- Format error messages in the same style as the current Gobra (or better)
- Error categories:
  - Precondition violations at call sites
  - Postcondition violations at return points
  - Invariant violations (loop entry/exit)
  - Assert/exhale failures
  - Permission errors (insufficient permission to access field)
  - Termination check failures
  - Overflow check failures
- Counterexample extraction (if Silicon provides one)
- Output modes: human-readable text, JSON (for IDE integration)
- Warning suppression for `//@ trusted` and `//@ assume`

**Out of scope:**
- Error detection (that's Silicon's job)
- Source position tracking during translation (positions are stored in the Silver Go AST in 14)

## Dependencies

- [14-silver-ast.md](14-silver-ast.md) — Silver AST carries Go source positions
- [17-silicon-backend.md](17-silicon-backend.md) — source of VerificationError objects

## Reference: Current Gobra

- `src/main/scala/viper/gobra/reporting/` — the entire reporting package
  - `VerificationResultReport.scala`
  - `Reporter.scala`
  - `DefaultReporter.scala`
- `src/main/scala/viper/gobra/backend/BackendVerifier.scala` — how errors flow from Silicon
  to the reporter

## Deliverables

- `internal/reporting/reporter.go` — `Report(result *VerificationResult, info *TypeInfo) []Diagnostic`
- `Diagnostic` type: `{File, Line, Col, Message, Category}`
- Text formatter and JSON formatter
- Tests: given a known Silicon error response, verify the correct Go source position is
  reconstructed

## Resolved Questions

**Error position extraction (resolved):** Silicon returns a `VerificationError` with an
`offendingNode` field (a Silver AST node). Each Silver node carries metadata set during
translation: a `Source.Verifier.Info` containing the `frontend.PNode` and `AbstractOrigin`
(file/line/col) of the Go construct that generated it. The reporter calls
`Source.unapply(error.offendingNode)` (or its Go equivalent: read the `GoPos` field from the
Silver node's metadata) to extract the Go source position.

**Multi-position heuristic (resolved):** When a Silver node was generated from multiple Go
constructs (e.g., a desugared multi-assignment), the position stored on the Silver node is that
of the **outermost enclosing Go construct** — the statement or expression that triggered the
desugaring, not the synthetic sub-nodes it produced. The translator must store the outermost
position when constructing synthetic Silver nodes during desugaring. This matches the Scala
Gobra's behavior.
