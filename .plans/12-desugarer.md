# 12 — Desugarer

## Objective

Implement the desugaring pass: lower the type-checked frontend AST into the internal AST.
This eliminates syntactic sugar, makes implicit operations explicit (nil checks, bounds checks,
overflow checks stubs), and produces the flat, uniform representation the translator expects.

## Scope

**In scope:**
- Transform every frontend AST node into the corresponding internal AST node(s)
- Desugar:
  - Multi-assignment into individual assignments
  - Short variable declarations into declarations + assignments
  - `for range` into indexed loops
  - Method calls on embedded fields (path expansion)
  - Named return values (introduce explicit variables)
  - `defer` statements
  - Interface method calls (dynamic dispatch stubs)
  - Type assertions
  - Composite literals (struct/slice/map)
  - String concatenation, built-in functions (`len`, `cap`, `make`, `new`, `append`, `copy`,
    `delete`, `close`)
  - `go` statements (goroutine spawning)
  - Spec desugaring: `preserves P` → `requires P` + `ensures P`
  - Ghost statement desugaring

**Out of scope:**
- Overflow check insertion (13-internal-transforms.md)
- Constant propagation (13)
- Termination measure handling (13)

## Dependencies

- [03-frontend-ast.md](03-frontend-ast.md) — source AST
- [08-type-checker-core.md](08-type-checker-core.md) — type info needed for desugaring decisions
- [09-type-checker-specs.md](09-type-checker-specs.md) — spec type info
- [10-type-checker-multipackage.md](10-type-checker-multipackage.md) — cross-package info
- [11-internal-ast.md](11-internal-ast.md) — target AST

## Reference: Current Gobra

- `src/main/scala/viper/gobra/frontend/Desugar.scala` — the canonical implementation;
  this is a large file (~4000 lines); study it section by section
- Pay particular attention to how method calls on non-pointer receivers are handled,
  how `preserves` is split, and how ghost code is threaded through

## Key Implementation Notes

- The desugarer is a recursive transformation; use the visitor pattern from 11
- Fresh variable generation: the desugarer introduces temporary variables; maintain a counter
  per function scope
- Position preservation: every internal AST node should carry the source position of the
  frontend node it was derived from
- Error reporting: desugaring should not fail (type checking catches errors earlier); if an
  unexpected node is encountered, panic with a clear message

## Deliverables

- `internal/desugar/desugar.go` — `Desugar(pkg *frontend.PPackage, info *TypeInfo) (*internal.Program, error)`
- Tests: for each major desugaring rule, a before/after pair showing frontend → internal AST

## Open Questions

- How to handle Go's multiple return values in the internal AST? The current internal AST
  models them as tuples; confirm the internal AST design in 11 before implementing.
