# 18 — Carbon Backend [DEFERRED — NOT IN ACTIVE WBS]

> **This plan is deferred indefinitely per D12 in DECISIONS.md.** It is not part of the
> active dependency graph. Plan 33 (CLI) does **not** depend on this plan and does not
> expose `--backend carbon`. No other active plan depends on this one.
>
> **To pick this up after self-hosting:** (1) Add the `Backend` interface to
> `internal/backend/types.go` (see "Backend Interface Definition" below), (2) wire
> `--backend silicon/carbon` and `--boogieExe` flags into Plan 33 (CLI), (3) implement
> `CarbonFrontendAPI` following the pattern in Plan 17. Plan 33 will need to add
> [18-carbon-backend.md](18-carbon-backend.md) back to its Dependencies at that time.

## Objective

Add Carbon (VCG backend) as a second verification backend, using the same gRPC/subprocess
infrastructure as Silicon (17). This is lower priority than Silicon and can be deferred until Silicon is fully
working.

## Scope

**In scope:**
- gRPC calls to Carbon's `CarbonFrontendAPI` following the same pattern as 17
- Carbon-specific configuration: Boogie executable path, additional Carbon flags
- Same `VerificationResult` type as Silicon (reuse from 17)

**Out of scope:**
- Silicon backend (17) — must be complete first
- Any Carbon-specific encoding differences (Carbon and Silicon accept the same Silver IR)

## Dependencies

- [16-silver-jni-builder.md](16-silver-jni-builder.md) — provides the serialized Protobuf program
- [17-silicon-backend.md](17-silicon-backend.md) — establishes the backend interface pattern
  to follow

## Reference: Current Gobra

- `src/main/scala/viper/gobra/backend/Carbon.scala`
- Carbon requires Boogie: `BOOGIE_EXE` environment variable

## `Backend` Interface Definition

The `Backend` interface lives in `internal/backend/types.go` (the parent `backend` package,
owned by plan 15). Both `silicon.SiliconFrontendAPI` (plan 17) and `carbon.CarbonFrontendAPI`
(this plan) implement it. Pipeline.go (plan 33) programs against this interface so it can
switch backends via `--backend silicon/carbon` without importing either sub-package directly.

```go
// Backend is implemented by both SiliconFrontendAPI (plan 17) and CarbonFrontendAPI (plan 18).
// Configuration is captured at construction time; Verify operates on an already-built jobject.
type Backend interface {
    // Initialize starts the backend and applies configuration. Must be called exactly once
    // before Verify. Panics if called a second time on the same instance.
    Initialize(args []string)

    // Verify runs the backend verifier on a serialized proto.SilverProgram.
    // gRPC errors are captured in VerificationResult.Err;
    // the method never returns nil — callers must check result.Err for infrastructure failures.
    Verify(prog *proto.SilverProgram) *VerificationResult

    // Stop shuts down the backend and releases its resources. May only be called after
    // Initialize. After Stop returns, the instance must not be used again.
    Stop()
}
```

`Backend` is added to `internal/backend/types.go` as a companion to `SiliconInstance`. Plan 33
imports `gobra/internal/backend` and holds a `backend.Backend` value; it constructs the
concrete backend via a factory based on `--backend`:

```go
var be backend.Backend
switch cfg.Backend {
case "silicon":
    be = silicon.NewSiliconFrontendAPI()
case "carbon":
    be = carbon.NewCarbonFrontendAPI(cfg.BoogieExe)
}
be.Initialize(cfg.BackendArgs)
defer be.Stop()
```

## Deliverables

- `internal/backend/carbon/carbon.go` — `CarbonFrontendAPI` struct implementing `backend.Backend`;
  `Verify(prog *proto.SilverProgram) *backend.VerificationResult` (gRPC errors encoded in result.Err, no
  separate error return; matches `SiliconFrontendAPI.Verify` signature so both implement
  `backend.Backend` and `backend.SiliconInstance`) and `CarbonConfig`
- `Backend` interface in `internal/backend/types.go` (see above; added to plan 15's file)
- Tests:
  - Verify a trivially correct Silver program using Carbon → expect `Success`
  - Verify a trivially incorrect Silver program (e.g., `assert false`) using Carbon → expect
    `Failure` with at least one `VerificationError`
  - Confirm that when `BOOGIE_EXE` is not set or points to a non-existent file, either
    `NewCarbonFrontendAPI`/`Initialize` returns an error, or `Verify` returns a
    `VerificationResult` with `result.Err != nil` (never a nil result). Note: `Verify` returns
    `*VerificationResult`, not `(*VerificationResult, error)` — gRPC/subprocess failures are encoded in `result.Err`.

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/backend/carbon/carbon.go`
and verified before this plan is considered complete.

1. **`Initialize` idempotency guard** — must be called exactly once per instance:
   ```go
   //@ ghost field initialized bool
   //@ requires acc(c) && !c.initialized
   //@ ensures  acc(c) && c.initialized
   func (c *CarbonFrontendAPI) Initialize(args []string)
   ```

2. **`Verify` postcondition** — verifier must return a non-nil result:
   ```go
    //@ ensures  result != nil   // never returns nil; gRPC errors encoded in result.Err
    func (c *CarbonFrontendAPI) Verify(prog *proto.SilverProgram) (result *backend.VerificationResult)
   ```

3. **`Stop` requires-initialized contract** — may only be called after `Initialize`:
   ```go
   //@ requires acc(c) && c.initialized
   //@ ensures  acc(c) && !c.initialized
   func (c *CarbonFrontendAPI) Stop()
   ```

4. **`Verify` result contract** — on a successful gRPC call, distinguishes proof success from
   verification failure; `Errors` is non-nil iff `Success == false`:
   ```go
   //@ ensures result.Err == nil ==>
   //@     (result.Success ==> result.Errors == nil) &&
   //@     (!result.Success ==> result.Errors != nil)
   ```

## Resolved Questions

**Carbon and self-hosting (resolved):** Carbon is not a target for self-hosting verification.
Self-hosting (plans 36/37) uses Silicon exclusively. Carbon is an optional backend for users
who prefer VCG; it is implemented after Silicon is fully stable and the regression suite
passes. The CI self-hosting job (`gobra-self-verify`, plan 37) runs Silicon only. If a user
passes `--backend carbon` to verify their own code, that is supported but not part of the
self-hosting milestone.
