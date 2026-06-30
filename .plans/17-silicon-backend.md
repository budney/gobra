# 17 — Silicon gRPC Backend

## Objective

Call `SilverServer` via gRPC with a serialized `proto.SilverProgram` (plan 16) and return
structured verification results (success or a list of errors with positions). No JNI, no CGo,
no thread preconditions.

## Scope

**In scope:**
- gRPC call: serialize the Go Silver AST (plan 16), send a `VerifyRequest` to the worker's
  `SilverServer` subprocess, receive a `VerifyResponse` with structured error objects
- Reconstruct `VerificationError` structs from the response: `Pos` from `error.NodeFile`,
  `error.NodeLine`, `error.NodeCol`, `error.NodeTag`; `Node` from `NodeMap[error.NodeId]`
- Silicon configuration: timeout, Z3 executable path (passed to `SilverServer` at subprocess
  startup via plan 15's `SubprocessConfig.Z3Exe`; `--z3APIMode` is not implemented per D15)
- `SiliconFrontendAPI` struct that wraps a `*subprocess.Backend` and implements
  `backend.SiliconInstance`

**Out of scope:**
- Carbon backend (18-carbon-backend.md)
- Subprocess lifecycle (plan 15)
- Silver Protobuf serialization (plan 16)
- Error formatting/display (plan 32)

## Dependencies

- [16-silver-jni-builder.md](16-silver-jni-builder.md) — `Serialize()` produces the
  `*proto.SilverProgram` sent in the `VerifyRequest`; `SerializedProgram.NodeMap` used for
  error node lookup
- [15-jni-setup.md](15-jni-setup.md) — `subprocess.Backend` and its `Client()` method;
  `WorkerPool` that invokes `Verify`

## `Verify` Function Scope

`Verify(prog *silver.Program, cfg backend.SiliconConfig) (*backend.VerificationResult, error)`
is used in **two scenarios only**:

1. **Test infrastructure (plan 34)**: `TestMain` creates one `SiliconFrontendAPI`, starts it
   once, and passes it as `cfg.Instance` in every test call. `Verify` handles the warm/cold
   dispatch on `cfg.Instance`.
2. **Cold-path one-off invocations** outside the worker pool.

**The worker pool in plans 15 and 17b does NOT call this function.** Worker goroutines own
their own `SiliconFrontendAPI` instances (created at pool startup) and call
`instance.Verify(serialized.Program)` directly — bypassing the warm/cold dispatch logic.

## gRPC Call Flow

```
worker goroutine
  │
  ├─ silver.Serialize(prog)      → SerializedProgram{Program, NodeMap}
  │
  ├─ client.Verify(ctx, &proto.VerifyRequest{Program: serialized.Program})
  │       (gRPC over local TCP to worker's SilverServer subprocess)
  │
  └─ response *proto.VerifyResponse
       │
       ├─ response.Success == true  → VerificationResult{Success: true}
       │
       └─ response.Success == false
            └─ for each response.Errors[i]:
                 err.Pos  ← NodeInfo{File: e.NodeFile, Line: e.NodeLine,
                                     Col:  e.NodeCol,  Tag:  e.NodeTag,
                                     NodeID: e.NodeId}
                 err.Node ← NodeMap[e.NodeId]   // nil if NodeId == 0 (synthetic)
                 err.FullID  ← e.FullId
                 err.Message ← e.Message
                 err.Reason  ← e.Reason
```

**Single source of truth for NodeInfo**: positions come directly from the Protobuf response
fields. The Go `NodeMap` is used only for `Children()` DFS in the `searchInfo` fallback
(plan 32). There is no dual-storage requirement — `AnnotationInfo` chains and `SilverBridge`
extraction methods are entirely absent from this design.

## Relationship to Chopping and Parallelism

This plan establishes the **single-program, single-worker** baseline: `Verify` accepts a
`*silver.Program`, serializes it, sends it via gRPC, and returns a result. Chopping and
parallel verification are layered on top in separate plans:
- Plan 16b (Silver Chopper) splits a `*silver.Program` into sub-programs.
- Plan 17b (Parallel Goroutine Workers) dispatches sub-programs in parallel.

## Deliverables

### `internal/backend/types.go` — Shared Types (defined in plan 15, consumed here)

The shared types `VerificationResult`, `VerificationError`, `SiliconInstance`, and
`SiliconConfig` are defined in `internal/backend/types.go` (package `backend`), owned by
**plan 15**. Plan 17 depends on plan 15 and consumes these types from `gobra/internal/backend`.

**`VerificationResult` has no `Close` field** — there are no JNI references to free. The
`NodeMap` is a plain Go map GC'd automatically.

**Warm path**: when `SiliconConfig.Instance` is non-nil, `Verify` skips `Initialize` and
uses the provided instance directly. When nil, `Verify` calls `Initialize` and `Stop` around
the verification call (cold path; slower, for one-off invocations).

### `internal/backend/silicon/silicon.go` — Silicon gRPC Backend

- `Verify(prog *silver.Program, cfg backend.SiliconConfig) (*backend.VerificationResult, error)`
- `SiliconFrontendAPI` struct — implements `backend.SiliconInstance`:
  ```go
  type SiliconFrontendAPI struct {
      backend *subprocess.Backend  // the worker's SilverServer subprocess connection
      // ghost field:
      //@ ghost field initialized bool
  }
  func NewSiliconFrontendAPI(b *subprocess.Backend) *SiliconFrontendAPI
  func (s *SiliconFrontendAPI) Initialize(args []string)
  func (s *SiliconFrontendAPI) Verify(prog *proto.SilverProgram) *backend.VerificationResult
  func (s *SiliconFrontendAPI) Stop()
  ```
  `Initialize` sends a gRPC `InitializeRequest` carrying the startup args (Z3 path, timeout,
  etc.) to the `SilverServer` subprocess. `Stop` sends a `StopRequest`. `Verify` sends a
  `VerifyRequest` and receives a `VerifyResponse`.

  The `silicon` sub-package imports `gobra/internal/backend` (parent) for shared types and
  `gobra/internal/backend/subprocess` for `*subprocess.Backend`. There is no import of `jvm`.

- Silicon initialization and teardown integrated with subprocess lifecycle (plan 15)
- Tests: verify a trivially correct Silver program → expect `Success: true`; verify a trivially
  incorrect one → expect `Success: false` with at least one error

## Verification Specifications (C9)

Plan 17's `Verify` function and `SiliconFrontendAPI` lifecycle are formally specified for
correctness and goroutine-safety. There are no thread preconditions — any goroutine may call
`Verify` without acquiring an OS thread lock.

1. **`Verify` result postcondition**: on a successful gRPC call (err == nil), the result
   distinguishes success from verification failure:
   ```go
   //@ ensures err == nil ==> result != nil
   //@ ensures err == nil ==>
   //@     (result.Success ==> result.Errors == nil) &&
   //@     (!result.Success ==> result.Errors != nil)
   func Verify(prog *silver.Program, cfg backend.SiliconConfig) (result *backend.VerificationResult, err error)
   ```

2. **`SiliconFrontendAPI.Initialize` — idempotency guard**: `Initialize` must only be called
   once per instance; calling it a second time is a contract violation:
   ```go
   //@ requires !s.initialized
   //@ ensures  s.initialized
   func (s *SiliconFrontendAPI) Initialize(args []string)
   ```

3. **`SiliconFrontendAPI.Stop` — requires initialized**: `Stop` may only be called after
   `Initialize` has completed:
   ```go
   //@ requires s.initialized
   //@ ensures  !s.initialized
   func (s *SiliconFrontendAPI) Stop()
   ```

4. **`SiliconFrontendAPI.Verify` — requires initialized**: `Verify` may only be called
   between `Initialize` and `Stop`:
   ```go
   //@ requires s.initialized
   //@ ensures  s.initialized && result != nil
   func (s *SiliconFrontendAPI) Verify(prog *proto.SilverProgram) (result *backend.VerificationResult)
   ```

5. **Goroutine safety**: each `SiliconFrontendAPI` instance is owned by exactly one worker
   goroutine (plan 15b's `runWorker`). No locking is needed because the instance is never
   shared. This is documented as a usage constraint, not a Gobra predicate.

## Resolved Questions

**Silicon thread safety (resolved):** Each worker owns exactly one `SiliconFrontendAPI`
instance. No sharing, no locking. Silicon's own thread safety is the `SilverServer`
subprocess's concern — from Go-Gobra's perspective, each gRPC call is independent.

**Silicon pre-warming for tests (resolved):** The test infrastructure (plan 34) initialises
one `SiliconFrontendAPI` once in `TestMain` and reuses the same instance for all tests via
`cfg.Instance`. Plan 17's `SiliconConfig` supports this warm path unchanged.
