# 27 — Encoding: Methods & Functions

## Objective

Implement the encoding of Go functions, methods, closures, and their specifications into
Silver methods and functions. This is the central encoding that ties all others together and
is required before any end-to-end verification is possible.

## Scope

**In scope:**
- Go function declarations → Silver methods (with body) or functions (pure, no body allowed
  in Silver functions — pure Go functions become Silver functions)
- Method declarations (value and pointer receivers) → Silver methods
- Preconditions (`requires`), postconditions (`ensures`), `preserves` desugaring
- Ghost parameters and results
- Multiple return values
- Named return values
- Recursive function calls (same-package)
- Cross-package function calls
- `pure` functions: encoded as Silver functions (no side effects, result is an expression)
- `trusted` functions: no body generated, only spec
- Function literals (closures): encode as Silver methods with captured variables as parameters
- `defer` statement encoding (execute-on-return semantics)
- `init` functions
- Method expressions and method values

**Out of scope:**
- Goroutines/`go` statements (28-encoding-channels.md)
- Interface dynamic dispatch (25)
- Built-in functions like `len`, `cap`, `make`, `new`, `append` (these are handled by the
  respective type encodings 23–24 and called from here)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context
- [20-encoding-primitives.md](20-encoding-primitives.md) — parameter/return type encodings
- [21-encoding-structs.md](21-encoding-structs.md) — struct receiver encoding
- [22-encoding-pointers.md](22-encoding-pointers.md) — pointer receiver encoding
- [23-encoding-slices.md](23-encoding-slices.md) — slice params
- [24-encoding-maps.md](24-encoding-maps.md) — map params
- [25-encoding-interfaces.md](25-encoding-interfaces.md) — interface params
- [26-encoding-permissions.md](26-encoding-permissions.md) — permission specs

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/` — function/method encoding
- `src/main/scala/viper/gobra/translator/context/` — how the context manages method encoding
- `src/main/scala/viper/gobra/translator/encodings/MethodEncoding.scala` (or similar)
- `src/main/scala/viper/gobra/frontend/Desugar.scala` — `preserves` desugaring

## Key Silver Output Shape

A Go method `func (r *S) M(x int) (y bool)` with spec becomes:
```silver
method M(r: Ref, x: Int) returns (y: Bool)
  requires ...
  ensures  ...
{ ... }
```

## Deliverables

- `internal/translator/encodings/methods.go`
- `internal/translator/encodings/functions.go`
- End-to-end test: translate a small but non-trivial annotated Go program (e.g., the swap
  example from `src/test/resources/regressions/examples/`) and verify it passes Silicon

## Resolved Questions

**`defer` encoding (resolved):** The desugarer converts `defer f(args)` into an
`in.Defer(in.FunctionCall(...))` node in the internal AST. The translator handles all
`in.Defer` nodes via a dedicated `DeferEncoding` pass on the method body:

For each `defer` statement:
1. Generate a boolean activation variable `active_N = false` and N temporary variables
   `temp_N_0, ..., temp_N_k` (one per argument).
2. At the defer site: `active_N = true; temp_N_0 = arg0; ...` (arguments evaluated eagerly,
   matching Go semantics).
3. At the end of the method body: emit, in **reverse program order** (sorted by line number
   descending), `if (active_N) { f(temp_N_0, ...) }`.

There is no runtime stack. Each defer has its own statically-known activation boolean. This is
sound for Silver verification because all execution paths are explored exhaustively.

**Known limitation:** Defers inside loops (`for { defer f() }`) are not correctly handled
by this encoding — each iteration overwrites the same activation variable. The Scala
implementation has the same limitation. Document this in the CLI `--help` output.

**Closures with captured mutable variables:** Encode captured mutable variables as additional
method parameters passed by reference (`Ref`). The closure's Silver method takes these as extra
`Ref` arguments alongside the explicit parameters. Study `ClosureEncoding.scala` in the Scala
translator for the exact lifting strategy before implementing.
