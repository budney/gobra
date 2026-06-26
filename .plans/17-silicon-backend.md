# 17 — Silicon Backend

## Objective

Call Silicon's verification API via JNI with a constructed Java Silver AST object (16) and
return structured verification results (success or a list of errors with positions).

## Scope

**In scope:**
- JNI calls to initialize and configure Silicon (`silicon.Silicon`, `SiliconFrontendAPI`)
- Passing the `silver.ast.Program` Java object to Silicon's `verify()` method
- Receiving the `silver.verifier.VerificationResult` (Success or Failure with error list)
- Extracting error information: error type, position (Silver position → Go source position),
  error message, reason, counterexample (if available)
- Mapping Silicon's `VerificationError` objects back to Go source locations (fed into 32)
- Silicon configuration: timeout, Z3 executable path, additional Silicon flags

**Out of scope:**
- Carbon backend (18-carbon-backend.md)
- JVM lifecycle (15)
- Silver AST construction (16)
- Error formatting/display (32-reporter.md)

## Dependencies

- [16-silver-jni-builder.md](16-silver-jni-builder.md) — provides the Java Silver AST object

## Reference: Current Gobra

- `src/main/scala/viper/gobra/backend/Silicon.scala` — how Gobra calls Silicon today
- `src/main/scala/viper/gobra/backend/ViperServer.scala` — the server-based path (not used
  in Go-Gobra, but shows the Silicon API surface)
- `viperserver/silicon/src/main/scala/Silicon.scala` — Silicon's own entry point and API

## Key Silicon API (via JNI)

```
silicon.Silicon.newInstance() → Silicon instance
silicon.start(args: Array[String])
silicon.verify(program: silver.ast.Program) → VerificationResult
silicon.stop()
```

`VerificationResult` is either:
- `silver.verifier.Success`
- `silver.verifier.Failure(errors: Seq[AbstractError])`

Each `AbstractError` has `.pos`, `.fullId`, `.readableMessage`, `.reason`.

## Deliverables

- `internal/backend/silicon/silicon.go` — `Verify(prog jobject, cfg SiliconConfig) (*VerificationResult, error)`
- `VerificationResult` Go type: success flag + slice of `VerificationError{Pos, FullID, Message, Reason}`
- Silicon initialization and teardown integrated with JVM lifecycle (15)
- Tests: verify a trivially correct Silver program → expect Success; verify a trivially
  incorrect one → expect Failure with at least one error

## Open Questions

- Silicon is not thread-safe in general; should Go-Gobra serialize all calls to Silicon, or
  run multiple Silicon instances in parallel (one per goroutine)? Current Gobra uses one
  instance per verification job; start with serial execution.
