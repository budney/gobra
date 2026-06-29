# 32 — Reporter & Error Mapping

## Objective

Implement the reporter: receive `VerificationError` objects from Silicon (17), map their
Silver source positions back to original Go source locations, and format human-readable
diagnostic output.

## Scope

**In scope:**
- `internal/reporting/tags.go` — named constants for all valid `NodeInfo.Tag` strings
  (e.g., `TagCall`, `TagReturn`, `TagField`, `TagAssert`, `TagLoopInv`, `TagFold`,
  `TagExhale`, `TagTermination`, `TagOverflow`, `TagSynthetic`); referenced by the translator
  (plans 19–31) and consumed by the reporter's error-dispatch table
- Map a Silver position (method name + Silver statement/expression position) back to the
  Go source file, line, and column that generated it, using position info stored in the
  Silver AST (14)
- Format error messages in the same style as the current Gobra (or better)
- Error categories:
  - Precondition violations at call sites
  - Postcondition violations at return points
  - Invariant violations (loop entry/exit)
  - Assert/exhale failures
  - Permission errors (insufficient permission to access field)
  - Termination check failures
  - Overflow check failures
- Counterexample extraction (if Silicon provides one)
- Output modes: human-readable text, JSON (for IDE integration)
- Warning suppression for `//@ trusted` and `//@ assume`

**Out of scope:**
- Error detection (that's Silicon's job)
- Source position tracking during translation (positions are stored in the Silver Go AST in 14)

## Dependencies

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type returned by `Report()`
- [14-silver-ast.md](14-silver-ast.md) — `NodeInfo` on every Silver node
- [15-jni-setup.md](15-jni-setup.md) — defines `VerificationResult`, `VerificationError`, `VerificationError.Node`, `VerificationError.Pos` in `internal/backend/types.go`; reporter accesses these fields directly
- [16-silver-jni-builder.md](16-silver-jni-builder.md) — JNI object-to-Go-node identity map
- [17-silicon-backend.md](17-silicon-backend.md) — `Verify()` returns `*backend.VerificationResult`; `VerificationError.Node` populated by the worker before `Report()` is called

## Reference: Current Gobra

- `src/main/scala/viper/gobra/reporting/` — the entire reporting package
  - `VerificationResultReport.scala`
  - `Reporter.scala`
  - `DefaultReporter.scala`
- `src/main/scala/viper/gobra/backend/BackendVerifier.scala` — how errors flow from Silicon
  to the reporter

## Deliverables

- `internal/reporting/tags.go` — named tag constants (`TagCall`, `TagReturn`, `TagField`,
  `TagAssert`, `TagLoopInv`, `TagFold`, `TagExhale`, `TagTermination`, `TagOverflow`,
  `TagSynthetic`); encoding plans reference these constants; the reporter's dispatch table
  uses them
- `internal/reporting/reporter.go` — `Report(result *VerificationResult) []Diagnostic`
- `Diagnostic` type: defined in plan 32a (`internal/diagnostic/`); re-exported from
  `internal/reporting/` for callers using a Go type alias:
  ```go
  // In internal/reporting/reporter.go (or a separate types.go in the same package)
  import "gobra/internal/diagnostic"
  type Diagnostic = diagnostic.Diagnostic  // type alias — NOT a redefinition
  ```
  **This MUST be a type alias (`=`), not a type definition.** A type definition would create
  an incompatible type; `diagnostic.Diagnostic` values returned by all pipeline stages would
  not be assignable to `reporting.Diagnostic` variables. The alias makes them identical types.
- Text formatter and JSON formatter
- Tests: given a known Silicon error response, verify the correct Go source position is
  reconstructed

**`Report` signature**: takes only `*VerificationResult`. The `nodeMap` and `TypeInfo`
parameters are gone:
- Each `VerificationError` already carries `err.Node silver.Node` (populated by the worker,
  plan 17/15b) — `searchInfo` starts from `err.Node` directly, with no nodeMap lookup.
- `TypeInfo` was never used by the reporter; removed.

`Report` must be called before `result.Close()` — see plan 16 for the correct call
ordering. `result.Close()` releases JNI global references; the `err.Node` Go Silver structs
are safe to read until then.

## Error Backtranslation Design

### How Silicon returns errors

Silicon's `VerificationResult` is either `Success` or a list of `VerificationError` objects.
Each `VerificationError` has:
- An **error type** (e.g., `PreconditionMightNotHold`, `PostconditionViolated`,
  `AssertFailed`, `InsufficientPermission`, etc.) — determined by Silicon from the proof
  obligation that failed.
- An **offending node** — the Silver AST node that Silicon identified as the failure point.

The JNI worker (plan 17) processes Silicon's raw errors before handing `VerificationResult`
to the reporter: for each raw error, the worker calls `SilverBridge.getNodeFile/Line/Col/Tag(offendingNode)`
to extract the `NodeInfo` that was embedded in the node's Viper `Info` chain during Build()
(plan 16), and stores it as `VerificationError.Pos`. By the time `Report()` is called,
every `VerificationError.Pos` is already populated. The reporter does NOT make additional JNI
calls for primary position lookup.

### Position extraction

```go
// resolvePos returns the NodeInfo for a VerificationError. The worker already
// populated err.Pos; if its Tag is "synthetic", fall back to searchInfo DFS on err.Node.
func resolvePos(err VerificationError) silver.NodeInfo {
    if err.Pos.Tag != "synthetic" {
        return err.Pos
    }
    return searchInfo(err.Node) // walk children downward; see below
}
```

`searchInfo` starts from `err.Node` (the Go Silver node pre-loaded by the worker via
`NodeMap[Pos.NodeID]`) and does a DFS **downward into children**, returning the first
non-synthetic `NodeInfo` found. No nodeMap lookup is needed inside `searchInfo` — it receives
the root node directly.

The primary path — `resolvePos` returning `err.Pos` immediately — should be the common case.
Nodes that Silicon can directly cite as `offendingNode` (Assert, Exhale, MethodCall, field
access, etc.) always carry a real `NodeInfo` by the invariant stated in plan 14. `searchInfo`
exists for unexpected edge cases only; if it is reached frequently, it indicates a translator
bug (a directly-citable node was marked synthetic).

### Error message dispatch

The reporter dispatches on the pair `(silverErrorType, nodeInfo.Tag)` to produce a
human-readable Go diagnostic. This matches how the Scala Gobra uses
`DefaultErrorBackTranslator` with its chain of partial functions on `(VerificationError, Source.Verifier.Info)`.

Representative dispatch table (not exhaustive — each encoding plan adds its own tags):

| Silver error type | Tag | Go diagnostic |
|-------------------|-----|---------------|
| `PreconditionMightNotHold` | `"call"` | "Precondition of {fn} might not hold" |
| `PostconditionViolated` | `"return"` | "Postcondition might not hold on return" |
| `InsufficientPermission` | `"field"` | "Insufficient permission to access field" |
| `AssertFailed` | `"assert"` | "Assert might fail" |
| `AssertFailed` | `"loop-inv"` | "Loop invariant might not be maintained" |
| `AssertFailed` | `"fold"` | "Fold might fail" |
| `AssertFailed` | `"exhale"` | "Exhale might fail" |
| `LoopInvariantNotEstablished` | `"loop-inv"` | "Loop invariant might not hold on entry" |
| `TerminationFailed` | `"termination"` | "Termination measure might not decrease" |
| `OverflowCheckFailed` | `"overflow"` | "Integer overflow might occur" |

Unrecognized `(errorType, tag)` pairs fall through to a generic message:
`"Viper error: {errorType.readableMessage} at {nodeInfo}"`.

### Multi-position heuristic

When a Silver node was generated from multiple Go constructs (e.g., a desugared
multi-assignment), the `NodeInfo` stored on the Silver node is that of the **outermost
enclosing Go construct** — the statement or expression that triggered the desugaring. The
translator must propagate the outermost position when constructing synthetic Silver nodes.

### JNI integration note

The Scala Gobra uses `Source.unapply(offendingNode)` — which calls `node.getPrettyMetadata._2.getUniqueInfo[Verifier.Info]` — to extract Go source info directly from a Silver node's Viper `Info` field. Go-Gobra uses the analogous approach: the builder (plan 16) embeds `NodeInfo` as `AnnotationInfo` entries in each node's Viper `Info` chain during construction, and the worker (plan 17) calls `SilverBridge.getNodeFile/Line/Col/Tag(offendingNode)` to retrieve those fields after Silicon reports errors.

For the `searchInfo` DFS, the reporter uses `err.Node` (the Go Silver struct pre-loaded by the worker, carrying `Children()`) rather than performing a nodeMap lookup inside the reporter. The `NodeMap` field on `VerificationResult` is retained for lifecycle purposes (it is referenced by `Close()` to free JNI global references) but the reporter does not access it directly.

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/reporting/reporter.go` and
verified before this plan is considered complete.

1. **`Report` non-nil result** — always returns a slice (may be empty on success; nil slice
   is not returned):
   ```go
   //@ requires result != nil
   //@ ensures  diags != nil   // slice header is always initialized, even if len == 0
   func Report(result *backend.VerificationResult) (diags []diagnostic.Diagnostic)
   ```

2. **Error-count correspondence** — the number of returned diagnostics matches the number of
   verification errors in the result (one diagnostic per error):
   ```go
   //@ requires result != nil && result.Err == nil
   //@ ensures  result.Success ==> len(diags) == 0
   //@ ensures  !result.Success ==> len(diags) == len(result.Errors)
   ```

3. **`searchInfo` DFS termination** — the downward child-walk terminates because Silver node
   trees are acyclic (enforced by the Silver AST immutability invariant in plan 14):
   ```go
   //@ requires node != nil && acc(silverNodeTree(node), _)
   //@ ensures  result.Tag != "synthetic" || result == silver.NoInfo
   //@ decreases silverTreeSize(node)   // well-founded: acyclic tree decreases on each step
   func searchInfo(node silver.Node) (result silver.NodeInfo)
   ```

4. **Called-before-Close contract** — `Report` must be called while JNI global refs are still
   live (before `result.Close()`); this is enforced by a ghost permission:
   ```go
   //@ requires acc(jniRefsLive(result), _)   // ghost: JNI global refs not yet freed
   //@ ensures  acc(jniRefsLive(result), _)   // ghost: permission returned; Close still valid
   func Report(result *backend.VerificationResult) (diags []diagnostic.Diagnostic)
   ```
