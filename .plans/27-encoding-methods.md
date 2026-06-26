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

## Open Questions

- How are closures with captured mutable variables encoded? This is a known hard case;
  study the Scala implementation carefully.
- How is `defer` order (LIFO) encoded in Silver? Likely via a sequence of inhale/exhale
  at return points.
