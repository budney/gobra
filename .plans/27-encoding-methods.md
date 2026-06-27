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
- `init` functions (see encoding note below)
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
implementation has the same limitation. Document this in `README.md` and a
`KNOWN_LIMITATIONS.md` file at the repo root, not in `--help` output (`--help` is for
flags, not soundness caveats that users need to read before writing specs).

**Closures with captured mutable variables:** Encode captured mutable variables as additional
method parameters passed by reference (`Ref`). The closure's Silver method takes these as extra
`Ref` arguments alongside the explicit parameters. Study `ClosureEncoding.scala` in the Scala
translator for the exact lifting strategy before implementing.

**`init` function encoding (resolved):** Go's `init` functions run automatically in package
initialization order; they cannot be called explicitly by user code. Each `init` function in
a package is encoded as a separate Silver method `{pkg}_init_N` (N = 0, 1, 2, … in source
order of the files, then in source order within each file). The translator synthesizes a
`{pkg}_run_inits` Silver method that calls each `{pkg}_init_N` in declaration order.
Callers (the translator entry point in plan 33) invoke `{pkg}_run_inits` for each package
before verifying any user method in that package.

Constraints:
- The translator must never emit a call to any `{pkg}_init_N` from anywhere except
  `{pkg}_run_inits`, enforcing Go's "init is not callable" rule at the Silver level.
- Specs on `init` functions should be kept minimal; `init` functions commonly have
  side effects (registering handlers, setting global state) that are difficult to specify
  formally. Mark heavily-specced `init` functions `//@ trusted` when necessary.
- If an `init` function has no meaningful spec, emit it with empty pre/postconditions.
  Silicon will still verify that the body does not violate any implicit permissions.

**`opaque` pure functions (resolved):** The `opaque` modifier is only valid on `pure`
functions and `pure` methods — the type checker (plan 09) enforces this. The encoding is:

1. **Function declaration**: emit a Silver `Function` exactly as for a non-opaque pure
   function, but attach an `@opaque` backend annotation to its `Info` chain:
   ```
   info = ConsInfo(AnnotationInfo{"opaque", []}, NodeInfo{...})
   ```
   Silicon treats `@opaque` functions as unfolded only at explicitly `reveal`ed call sites;
   callers otherwise see only the spec.

2. **Call sites with `reveal f(args)`**: the internal AST carries `reveal=true` on
   `PureFunctionCall` / `PureMethodCall` nodes (set by the desugarer from the `reveal`
   keyword). The translator attaches `@reveal` to the Silver `FuncApp`'s `Info` chain:
   ```
   info = ConsInfo(AnnotationInfo{"reveal", []}, NodeInfo{...})
   ```

The Silver `ConsInfo` / `AnnotationInfo` types must be defined in the Go Silver AST (plan 14)
and constructable via `SilverBridge.java` `makeAnnotationInfo` / `makeConsInfo` methods (plan 16).
