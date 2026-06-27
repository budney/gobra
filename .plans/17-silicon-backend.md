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

## Relationship to Chopping and Parallelism

This plan establishes the **single-program, single-worker** baseline: `Verify` accepts one
`*silver.Program`, builds its Java equivalent via plan 16, calls Silicon, and returns a result.

Chopping and parallel verification are layered on top in separate plans:
- Plan 16b (Silver Chopper) splits a `*silver.Program` into sub-programs before JNI-building.
- Plan 17b (Parallel Workers) expands the pool to N workers and dispatches sub-programs in
  parallel. Callers of plan 17's `Verify` function do not change — the dispatch layer in 17b
  wraps it.

## Reference: Current Gobra

- `src/main/scala/viper/gobra/backend/Silicon.scala` — how Gobra calls Silicon today
- `src/main/scala/viper/gobra/backend/ViperServer.scala` — the server-based path (not used
  in Go-Gobra, but shows the Silicon API surface)
- `viperserver/silicon/src/main/scala/Silicon.scala` — Silicon's own entry point and API

## Key Silicon API (via JNI)

```
SiliconFrontendAPI(reporter: viper.silver.reporter.Reporter) → SiliconFrontendAPI instance
silicon.initialize(args: Seq[String])
silicon.verify(program: silver.ast.Program) → VerificationResult
silicon.stop()
```

Note: `SiliconFrontendAPI` is instantiated directly (no factory method). The startup method
is `initialize`, not `start`. Do NOT call `silicon.Silicon.newInstance()` — no such static
factory exists. See `viperserver/silicon/src/main/scala/Silicon.scala` for the correct class
hierarchy (`SiliconFrontendAPI` extends `ViperFrontendAPI` which provides `initialize`).

`VerificationResult` is either:
- `silver.verifier.Success`
- `silver.verifier.Failure(errors: Seq[AbstractError])`

Each `AbstractError` has `.pos`, `.fullId`, `.readableMessage`, `.reason`.

## Deliverables

- `internal/backend/silicon/silicon.go` — `Verify(prog jobject, cfg SiliconConfig) (*VerificationResult, error)`
- `VerificationResult` Go type:
  ```go
  type VerificationResult struct {
      Success bool
      Errors  []VerificationError         // non-nil only when Success == false
      NodeMap map[uintptr]*silver.Node    // JNI pointer → Go Silver node; for Reporter
      Close   func()                      // caller must defer result.Close() before Report()
  }
  type VerificationError struct {
      Pos     NodeInfo
      FullID  string
      Message string
      Reason  string
  }
  ```
  `NodeMap` and `Close` are set by the JNI worker (plan 15/17b); callers must `defer
  result.Close()` in the same stack frame as the `Report()` call — this releases JNI global
  references held for the Java Silver objects. Failure to call `Close()` leaks JNI memory.
- `SiliconConfig` struct, including a `Instance *SiliconFrontendAPI` field for the warm path:

  ```go
  type SiliconConfig struct {
      Args     []string           // Silicon startup flags (Z3 path, timeout, etc.)
      Instance *SiliconFrontendAPI // non-nil: reuse this already-started instance (warm path)
                                   // nil: start a fresh Silicon instance (cold path)
  }
  ```

  When `Instance` is non-nil, `Verify` skips `silicon.initialize(cfg.Args)` and uses the
  provided instance directly. When `Instance` is nil, `Verify` calls `initialize` and `stop`
  around the verification call (cold path; slower, used only for one-off invocations).
  The test infrastructure (`TestMain` in plan 34) creates one `SiliconFrontendAPI`, starts it
  once, and passes it as `Instance` in every test call. This is the primary motivation for the
  warm-path design.

- Silicon initialization and teardown integrated with JVM lifecycle (15)
- Tests: verify a trivially correct Silver program → expect Success; verify a trivially
  incorrect one → expect Failure with at least one error

## Resolved Questions

**Silicon thread safety (resolved):** Serialize all calls to Silicon through a single instance.
Silicon is not thread-safe; the current Scala Gobra uses one instance per verification job.
Start with serial execution (one Silicon instance per Go-Gobra process lifetime).

**Silicon pre-warming for tests (resolved):** Silicon initialization (JVM startup + Z3
initialization) takes significant time. The test infrastructure (plan 34) initializes Silicon
once in `TestMain` and reuses the same instance for all tests. Plan 17's `SiliconConfig` must
therefore support a "warm" path where the already-started Silicon instance is handed to each
`Verify` call without re-initialization. Do not start/stop Silicon per test.
