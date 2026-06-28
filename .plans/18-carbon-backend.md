# 18 — Carbon Backend (Optional)

## Objective

Add Carbon (VCG backend) as a second verification backend, using the same JNI infrastructure
as Silicon (17). This is lower priority than Silicon and can be deferred until Silicon is fully
working.

## Scope

**In scope:**
- JNI calls to Carbon's `CarbonFrontendAPI` following the same pattern as 17
- Carbon-specific configuration: Boogie executable path, additional Carbon flags
- Same `VerificationResult` type as Silicon (reuse from 17)

**Out of scope:**
- Silicon backend (17) — must be complete first
- Any Carbon-specific encoding differences (Carbon and Silicon accept the same Silver IR)

## Dependencies

- [16-silver-jni-builder.md](16-silver-jni-builder.md) — provides the Java Silver AST object
- [17-silicon-backend.md](17-silicon-backend.md) — establishes the backend interface pattern
  to follow

## Reference: Current Gobra

- `src/main/scala/viper/gobra/backend/Carbon.scala`
- Carbon requires Boogie: `BOOGIE_EXE` environment variable

## Deliverables

- `internal/backend/carbon/carbon.go` — `Verify(prog jobject, cfg CarbonConfig) (*VerificationResult, error)`
- Both Silicon and Carbon implement a common `Backend` interface
- Tests:
  - Verify a trivially correct Silver program using Carbon → expect `Success`
  - Verify a trivially incorrect Silver program (e.g., `assert false`) using Carbon → expect
    `Failure` with at least one `VerificationError`
  - Confirm that `Verify` returns an error (not a `VerificationResult`) when `BOOGIE_EXE` is
    not set or points to a non-existent file

## Resolved Questions

**Carbon and self-hosting (resolved):** Carbon is not a target for self-hosting verification.
Self-hosting (plans 36/37) uses Silicon exclusively. Carbon is an optional backend for users
who prefer VCG; it is implemented after Silicon is fully stable and the regression suite
passes. The CI self-hosting job (`gobra-self-verify`, plan 37) runs Silicon only. If a user
passes `--backend carbon` to verify their own code, that is supported but not part of the
self-hosting milestone.
