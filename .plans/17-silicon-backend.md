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
- [15-jni-setup.md](15-jni-setup.md) — `WorkerPool` that invokes `Verify`; establishes the
  thread-locking contract that `Verify` relies on

## `Verify` Function Scope

The top-level Go function `Verify(prog jobject, cfg backend.SiliconConfig) (*backend.VerificationResult, error)`
defined in this plan is used in **two scenarios only**:

1. **Test infrastructure (plan 34)**: `TestMain` creates one `SiliconFrontendAPI`, starts it
   once, and passes it as `cfg.Instance` in every test call. `Verify` handles the warm/cold
   dispatch on `cfg.Instance`.
2. **Cold-path one-off invocations** (outside the worker pool): future use cases where a
   single verification is needed without a persistent pool.

**The worker pool in plans 15 and 17b does NOT call this function.** Worker goroutines own
their own `SiliconFrontendAPI` instances (created and initialized at pool startup) and call
`silicon.verify(built.JavaObject)` directly on those instances — bypassing the warm/cold
dispatch logic in `Verify`. This is intentional: workers initialize Silicon once at startup
and reuse the instance across all jobs; the dispatch logic in `Verify` is only needed for
callers that don't own a persistent instance.

Do not refactor the worker loop to call `Verify` — it would add unnecessary overhead and
obscure the lifetime of the `SiliconFrontendAPI` instance.

## Threading Precondition

`Verify` makes JNI calls and **must be invoked from a goroutine that has already called
`runtime.LockOSThread()` and `jvm.AttachCurrentThread()`**. This precondition is established
by the `WorkerPool` worker goroutines defined in plan 15 (and expanded in plan 17b). `Verify`
itself does not call `LockOSThread` — callers are responsible. Violating this precondition
will corrupt JNI thread state and crash the JVM.

## Relationship to Chopping and Parallelism

This plan establishes the **single-program, single-worker** baseline: `Verify` accepts a
pre-built Java Silver AST object (`jobject`, produced by plan 16's `Build()`) and a
`SiliconConfig`, calls Silicon, and returns a result. The Go `*silver.Program` → `jobject`
conversion is plan 16's responsibility; plan 17 operates on the already-constructed Java AST.

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

### `internal/backend/types.go` — Shared Types (defined in plan 15, consumed here)

The shared types `VerificationResult`, `VerificationError`, `SiliconInstance`, and
`SiliconConfig` are defined in `internal/backend/types.go` (package `backend`), owned by
**plan 15**. Plan 17 depends on plan 15 and consumes these types from `gobra/internal/backend`.

**Position filling**: for each `VerificationError` in `Errors`, the JNI worker fills in
`Pos` by calling `SilverBridge.getNodeFile/Line/Col/Tag(offendingNode)` (plan 16) to
extract the `NodeInfo` embedded in the Java Silver node's `Info` chain. The worker also
populates `Node` by looking up `NodeMap[Pos.NodeID]` — this gives the reporter the Go
Silver struct for `searchInfo` DFS without a separate nodeMap parameter. For synthetic nodes
(where `Tag == "synthetic"`), the reporter (plan 32) walks `Node.Children()` to find a
non-synthetic ancestor. `NodeMap` carries the full Go Silver struct (needed for `Children()`
calls) and is NOT used for primary position lookup.

`NodeMap` and `Close` are set by the JNI worker (plan 15b); callers must `defer
result.Close()` in the same stack frame as the `Report()` call — this releases JNI global
references held for the Java Silver objects. Failure to call `Close()` leaks JNI memory.

**Warm path**: when `SiliconConfig.Instance` is non-nil, `Verify` skips `Initialize` and
uses the provided instance directly. When nil, `Verify` calls `Initialize` and `Stop` around
the verification call (cold path; slower, for one-off invocations). The test infrastructure
(`TestMain` in plan 34) creates one `SiliconFrontendAPI`, starts it once, and passes it as
`Instance` in every test call. `NewInstance` is used only by `NewPool` (plan 15b) to create
per-worker instances at pool construction time.

### `internal/backend/silicon/silicon.go` — Silicon Verification

- `Verify(prog jobject, cfg backend.SiliconConfig) (*backend.VerificationResult, error)`
- `SiliconFrontendAPI` struct — implements `backend.SiliconInstance`:
  ```go
  type SiliconFrontendAPI struct { /* JNI handle fields */ }
  func NewSiliconFrontendAPI() *SiliconFrontendAPI
  func (s *SiliconFrontendAPI) Initialize(args []string)
  func (s *SiliconFrontendAPI) Verify(prog jobject) *backend.VerificationResult
  func (s *SiliconFrontendAPI) Stop()
  ```
  Note: `NewSiliconFrontendAPI` does NOT take a `*jvm.JVM` parameter — it accesses the
  process-wide JNI environment via the `jnigi` package directly. The `silicon` sub-package
  imports `gobra/internal/backend` (parent) for shared types and the `ThreadAttached` predicate
  used in C9 annotations. There is no direct import of `gobra/internal/backend/jvm`, avoiding import cycles.
  the `silicon` package, so there is no import cycle. `jobject` is jnigi's JNI object
  reference type, consistent with plan 16's `BuiltProgram.JavaObject jobject`.

- Silicon initialization and teardown integrated with JVM lifecycle (15)
- Tests: verify a trivially correct Silver program → expect Success; verify a trivially
  incorrect one → expect Failure with at least one error

## Verification Specifications (C9)

Plan 17's `Verify` function and `SiliconFrontendAPI` lifecycle must be formally specified
so Gobra can statically enforce the threading precondition and result validity contract.

1. **`Verify` threading precondition**: `Verify` makes JNI calls and may only be called from
   a goroutine that holds the OS-thread lock and JVM attachment:
   ```go
   //@ requires acc(backend.ThreadAttached(), 1)
   //@ ensures  acc(backend.ThreadAttached(), 1)
   //@ ensures  err == nil ==> result != nil
   func Verify(prog jobject, cfg backend.SiliconConfig) (result *backend.VerificationResult, err error)
   ```

2. **`SiliconFrontendAPI.Initialize` — idempotency guard**: `Initialize` must only be called
   once per instance; calling it a second time is a contract violation:
   ```go
   //@ ghost field initialized bool
   //@ requires !initialized
   //@ ensures  initialized
   func (s *SiliconFrontendAPI) Initialize(args []string)
   ```

3. **`SiliconFrontendAPI.Stop` — requires initialized**: `Stop` may only be called after
   `Initialize` has completed:
   ```go
   //@ requires initialized
   //@ ensures  !initialized
   func (s *SiliconFrontendAPI) Stop()
   ```

4. **`Verify` result contract**: on a successful JNI call (err == nil), the result
   distinguishes success from verification failure; the `Errors` field is non-nil iff
   `Success == false`:
   ```go
   //@ ensures err == nil ==>
   //@     (result.Success ==> result.Errors == nil) &&
   //@     (!result.Success ==> result.Errors != nil)
   ```

## Resolved Questions

**Silicon thread safety (resolved):** Serialize all calls to Silicon through a single instance.
Silicon is not thread-safe; the current Scala Gobra uses one instance per verification job.
Start with serial execution (one Silicon instance per Go-Gobra process lifetime).

**Silicon pre-warming for tests (resolved):** Silicon initialization (JVM startup + Z3
initialization) takes significant time. The test infrastructure (plan 34) initializes Silicon
once in `TestMain` and reuses the same instance for all tests. Plan 17's `SiliconConfig` must
therefore support a "warm" path where the already-started Silicon instance is handed to each
`Verify` call without re-initialization. Do not start/stop Silicon per test.
