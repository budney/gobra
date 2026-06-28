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
  - Variadic call argument desugaring (see Resolved Questions below)
  - Spec desugaring: `preserves P` → `requires P` + `ensures P`
  - Ghost statement desugaring
  - `break` / `continue` (labeled and unlabeled) → internal `Break` / `Continue` with optional
    label field; labeled statements → internal `Label` wrapping the body
  - `select` statement: count arms (channel cases + optional default), choose discriminant
    type (see Resolved Questions below), desugar to an internal `If` node chain (using the
    `If`/`While` nodes defined in plan 11); delegate channel send/receive expression encoding
    to plan 28 at translation time. The desugarer produces **internal AST**, not Silver — the
    translator converts internal `If` nodes to Silver `if`/`elsif` via the standard
    `If`-statement encoding. Do NOT produce Silver nodes here.

**Out of scope:**
- Overflow check insertion (13-internal-transforms.md)
- Constant propagation (13)
- Termination measure handling (13)

## Dependencies

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type returned by `Desugar`
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
- Addressability propagation: for each frontend expression node `e` lowered to an internal
  `Expr` node, copy `go/types.Info.Types[e].Addressable()` into the internal node's
  `Addressable bool` field. Desugarer-introduced temporaries (fresh `LocalVar`s etc.) and
  ghost expression nodes have no frontend counterpart; leave their `Addressable` field as
  `false` (exclusive / value-semantic)
- Error reporting: desugaring should not fail (type checking catches errors earlier); if an
  unexpected node is encountered, panic with a clear message

## Deliverables

- `internal/desugar/desugar.go` — `Desugar(pkg *frontend.PPackage, info *TypeInfo) (*internal.Program, []Diagnostic)`
  - The desugarer should not encounter type errors (those are caught by the type checker). If an unexpected node is encountered (a bug), panic with a clear message rather than returning a diagnostic — panics are reserved for internal consistency violations (see plan 00 cross-cutting contract). The `[]Diagnostic` return is for the rare case where desugaring discovers a structural invariant that the type checker should have caught; in practice it should always be empty.
- Tests: for each major desugaring rule, a before/after pair showing frontend → internal AST

## Resolved Questions

**Multiple return values (resolved — see plan 11):** `FunctionCall`/`MethodCall`/`ClosureCall`
are statements with a `targets: []LocalVar` field. The desugarer introduces one fresh `LocalVar`
per return value and populates `targets` from them. For comma-ok expressions (`m[k]`, `<-ch`),
the desugarer produces a `Tuple([]Expr{...})` expression and then decomposes it via individual
`Assign` statements to the LHS variables.

**Variadic call arguments (resolved):** Every call to a variadic function `f(params ...T)` goes
through a four-branch dispatch (reference: `functionCallArgsD` in `Desugar.scala:2338–2390`).
Detect the variadic case by checking whether the last parameter type is `VariadicT(elem)` via
`go/types`. Then match on the argument shape:

1. **Spread passthrough** (`f(s...)`): `ast.CallExpr.Ellipsis.IsValid()` is true. The last
   argument has type `[]elem` and the argument count equals the parameter count. Pass the
   slice as-is — do **not** emit a `NewSliceLit`. Primary detection MUST use
   `ast.CallExpr.Ellipsis.IsValid()`; relying on type shape alone misclassifies `f(g())`
   where `g()` returns a `[]T` (Branch 2 case) as a spread.
2. **Tuple-chaining** (`f(g())` where `g` returns multiple values and `f` takes one variadic
   param): `ast.CallExpr.Ellipsis` is NOT set. The single desugared argument is a `TupleT`.
   Unpack the tuple elements, construct a `NewSliceLit` from them, and pass the slice.
3. **Normal packing** (`f(a, b, c)`): `ast.CallExpr.Ellipsis` is NOT set and argument count
   ≥ parameter count. Collect the trailing arguments (from index `paramCount-1` onward),
   construct a `NewSliceLit` into a fresh temp var, and replace the trailing args with that var.
4. **Zero variadic args** (`f(a)` with no variadic argument): `ast.CallExpr.Ellipsis` is NOT
   set and argument count == parameter count - 1. Append `in.NilLit(SliceT(elem, nil))` as
   the variadic argument.

Any other shape is an internal bug (type checker should have caught it) — panic.

**`select` discriminant type (canonical definition — referenced in plan 28):** The desugarer
counts arms before choosing the discriminant type. Let C = the number of channel-operation
arms (not counting any `default` arm), and D = 1 if a `default` arm is present, 0 otherwise.

- **C=1, D=1** (one channel op + default): use a `bool` discriminant — `true` → channel op,
  `false` → default.
- **C≥2 or (C≥1 and D=0)**: use an `Int` discriminant chosen non-deterministically in `[0, C+D-1]`,
  where arms 0..C-1 correspond to channel operations and arm C (if D=1) corresponds to default.

A `select` with C=0 (no channel arms) and D=1 (only a default) reduces to the default body;
the discriminant is unnecessary. A `select` with C=0 and D=0 is a compile error; the type
checker rejects it.

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/desugar/desugar.go` and
verified before this plan is considered complete.

**`Desugar` termination and output safety:**
```go
//@ requires pkg != nil && info != nil
//@ ensures  result != nil || len(diags) > 0
//@ decreases // termination delegated to visitor recursion depth over finite AST
func Desugar(pkg *frontend.PPackage, info *TypeInfo) (result *internal.Program, diags []Diagnostic)
```

**Fresh variable counter monotonicity (loop invariant inside `desugarFunc`):**
```go
//@ invariant counter >= old(counter)
//@ invariant forall v in introduced :: v.Name != ""
```

**Position preservation postcondition on each node-lowering helper:**
```go
//@ ensures  result.Pos() == src.Pos()
```

**Division by zero (resolved):** The desugarer does NOT insert a call-site assertion for
`r != 0` before integer division or modulo. The generated `goIntDiv`/`goIntMod` Viper functions
already carry `requires r != 0` in their preconditions (plan 20). Silicon enforces this
precondition at every call site and produces a precondition-violation error pointing to the
division expression. No additional call-site assertion is needed. This behavior is unconditional
— it is not gated on `--overflow`.
