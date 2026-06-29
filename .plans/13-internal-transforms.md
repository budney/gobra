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

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type returned by `Apply`
- [11-internal-ast.md](11-internal-ast.md) — AST to transform
- [12-desugarer.md](12-desugarer.md) — produces the AST that is the input to transforms

## Reference: Current Gobra

- `src/main/scala/viper/gobra/ast/internal/transform/` — all transform implementations
  - `ConstantPropagation.scala`
  - `OverflowChecksTransform.scala`
  - `CGEdgesTerminationTransform.scala`
  - `SyntacticTerminationTransform.scala`

## Key Implementation Notes

- Each transform is a function `(*internal.Program, []Diagnostic) → (*internal.Program, []Diagnostic)` (returns a new tree plus accumulated diagnostics; the internal AST is immutable — see Resolved Questions below)
- Transforms are composed in a fixed order: constant propagation → call graph edges → overflow → termination
- The call graph construction for termination requires a fixed-point analysis over mutually
  recursive functions
- Diagnostics from each transform are appended to the accumulator with one important caveat:
  constant propagation and call-graph edge computation are **pure transforms** (they produce no
  diagnostics and cannot produce structurally invalid output). The overflow and termination
  transforms may emit diagnostics. All four transforms always run — even when overflow or
  termination emit diagnostics — because their diagnostics are user-visible errors (overflow or
  termination violations) that do not make the resulting AST structurally invalid. The pipeline
  abort rule from `00-overview.md` applies at the transform-chain boundary (i.e., if any
  diagnostics exist after `Apply` returns, the pipeline does not proceed to translation), not
  inside the chain itself.

## Deliverables

- `internal/transform/` package with one file per transform
- `internal/transform/pipeline.go` — `Apply(prog *internal.Program, cfg Config) (*internal.Program, []Diagnostic)`
- Tests: verify that overflow assertions are inserted correctly; verify constant folding;
  verify that a diagnostic-producing transform does not suppress output from subsequent transforms

## Resolved Questions

**Mutation vs. new AST (resolved — see plan 11):** Each transform produces a new
`*internal.Program` tree. The internal AST is immutable (all fields set at construction);
in-place mutation is not an option. This matches the Scala implementation.

**Division by zero (resolved — see plan 12):** The overflow transform does NOT insert
`assert r != 0` before integer division. That check is handled at the Viper level via the
`requires r != 0` precondition on `goIntDiv`/`goIntMod` (plan 20). The overflow transform
is responsible only for bounded-integer range checks (e.g., asserting that an `int8` result
fits in [-128, 127]) when `--overflow` is enabled.

## Verification Specifications (C9)

```go
// Apply pipeline entry-point:
//@ requires prog != nil
//@ ensures  result != nil
//@ ensures  len(diags) >= 0
//@ ensures  !cfg.Overflow && len(diags) == 0 ==> result.NodeCount() <= old(prog.NodeCount())
//@ decreases
func Apply(prog *internal.Program, cfg Config) (result *internal.Program, diags []Diagnostic)
```

**Node-count invariant**: Constant folding reduces or preserves the node count; no transform adds nodes without also removing the node(s) it replaces. Formally, `result.NodeCount() ≤ prog.NodeCount()` when there are no diagnostics and `--overflow` is not enabled. When `cfg.Overflow == true`, the overflow transform inserts range-assertion nodes for every arithmetic expression; this is correct behaviour that increases node count without producing diagnostics, so the non-increasing guarantee is conditioned on `!cfg.Overflow`.

**Transform order postcondition**: The four transforms run in the fixed sequence
`constantProp → callGraphEdges → overflowChecks → termination`. This ordering is a postcondition of `Apply`: the resulting program has CG edge annotations present before overflow checks are applied, which overflow checks may inspect.

```go
// Ordering contract: after Apply, every call site in result carries CG edge annotations
// (set by callGraphEdges) and every integer arithmetic node carries a range assertion
// (set by overflowChecks, when cfg.Overflow == true).
//@ ensures cfg.Overflow ==>
//@   forall n internal.ArithNode :: result.Contains(n) ==>
//@     n.HasRangeAssertion()
//@ ensures forall c internal.CallExpr :: result.Contains(c) ==>
//@   len(c.CGEdges) >= 0  // may be empty for interface calls resolved at translation
```

**Immutability contract**: Neither `Apply` nor any sub-transform modifies the input `prog` in place. The input tree is read-only; all structural changes produce a new tree.

```go
//@ ensures result != prog  // different pointer; input is untouched
//@ ensures old(prog.NodeCount()) == prog.NodeCount()  // input unchanged
```
