# 13 — Internal Transforms

## Objective

Apply post-desugaring program transformations to the internal AST before translation: constant
propagation, integer overflow check insertion, termination measure processing, and call graph
edge annotation.

## Scope

**In scope:**
- **Constant propagation**: fold constant expressions (integer, boolean, string) to literals
- **Overflow checks**: insert explicit bounds assertions for integer operations when overflow
  checking is enabled (`--overflow` flag); wrap arithmetic in range assertions
- **Termination**: process `decreases` clauses; insert termination check calls for recursive
  functions and loops; handle `decreases _` (no termination proof) and `decreases` (infer)
- **Call graph edges**: annotate function call sites with the set of possible callees (needed
  for the termination encoding)

**Out of scope:**
- Desugaring (12)
- Translation to Silver (19+)
- The transforms are optional/flag-controlled; the infrastructure must support running zero or
  more transforms in sequence

## Dependencies

- [11-internal-ast.md](11-internal-ast.md) — AST to transform
- [12-desugarer.md](12-desugarer.md) — produces the AST that is the input to transforms

## Reference: Current Gobra

- `src/main/scala/viper/gobra/ast/internal/transform/` — all transform implementations
  - `ConstantPropagation.scala`
  - `OverflowChecksTransform.scala`
  - `CGEdgesTerminationTransform.scala`
  - `SyntacticTerminationTransform.scala`

## Key Implementation Notes

- Each transform is a function `*internal.Program → *internal.Program` (or in-place mutation)
- Transforms are composed in a fixed order: constant propagation → overflow → termination
- The call graph construction for termination requires a fixed-point analysis over mutually
  recursive functions

## Deliverables

- `internal/transform/` package with one file per transform
- `internal/transform/pipeline.go` — `Apply(prog *internal.Program, cfg Config) *internal.Program`
- Tests: verify that overflow assertions are inserted correctly; verify constant folding

## Open Questions

- Should transforms mutate the AST in place or produce a new AST? New AST is safer; in-place
  is more memory-efficient. Follow the pattern established in 11.
