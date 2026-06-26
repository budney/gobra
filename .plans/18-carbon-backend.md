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

## Open Questions

- Is Carbon a target for self-hosting verification, or only Silicon? Deferring Carbon until
  after self-hosting is achieved is reasonable.
