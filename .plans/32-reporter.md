# 32 — Reporter & Error Mapping

## Objective

Implement the reporter: receive `VerificationError` objects from Silicon (17), map their
Silver source positions back to original Go source locations, and format human-readable
diagnostic output.

## Scope

**In scope:**
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

- [14-silver-ast.md](14-silver-ast.md) — `NodeInfo` on every Silver node
- [16-silver-jni-builder.md](16-silver-jni-builder.md) — JNI object-to-Go-node identity map
- [17-silicon-backend.md](17-silicon-backend.md) — source of VerificationError objects

## Reference: Current Gobra

- `src/main/scala/viper/gobra/reporting/` — the entire reporting package
  - `VerificationResultReport.scala`
  - `Reporter.scala`
  - `DefaultReporter.scala`
- `src/main/scala/viper/gobra/backend/BackendVerifier.scala` — how errors flow from Silicon
  to the reporter

## Deliverables

- `internal/reporting/reporter.go` — `Report(result *VerificationResult, info *TypeInfo) []Diagnostic`
- `Diagnostic` type: `{File, Line, Col, Message, Category}`
- Text formatter and JSON formatter
- Tests: given a known Silicon error response, verify the correct Go source position is
  reconstructed

## Error Backtranslation Design

### How Silicon returns errors

Silicon's `VerificationResult` is either `Success` or a list of `VerificationError` objects.
Each `VerificationError` has:
- An **error type** (e.g., `PreconditionInMightNotHold`, `PostconditionViolated`,
  `AssertFailed`, `InsufficientPermission`, etc.) — determined by Silicon from the proof
  obligation that failed.
- An **offending node** — the Silver AST node that Silicon identified as the failure point.
  This is the *same object* (by identity) that was passed to Silicon via JNI; it carries the
  `NodeInfo` set by the translator.

The reporter receives these errors via JNI (plan 17): for each error, it calls back through
JNI to retrieve `error.offendingNode.info` (the `NodeInfo` stored on the Silver node). See
plan 16 for the `SilverBridge.java` wrapper that exposes this.

### Position extraction

```go
func extractInfo(node *silver.Node) *silver.NodeInfo {
    if node.Info != nil && node.Info.File != "" {
        return node.Info
    }
    return searchInfo(node) // walk children downward; see below
}
```

`searchInfo` does a DFS over the Silver AST subtree rooted at `node`, walking **downward
into children**, returning the first non-synthetic `NodeInfo` found. This is a safety net for
translator-internal synthesized wrapper nodes (e.g., a synthetic `Seqn`) that don't carry a
direct Go source position (`Tag == "synthetic"`).

The primary path — `extractInfo` returning immediately — should be the common case. Nodes that
Silicon can directly cite as `offendingNode` (Assert, Exhale, MethodCall, field access, etc.)
always carry a real `NodeInfo` by the invariant stated in plan 14. `searchInfo` exists for
unexpected edge cases only; if it is reached frequently, it indicates a translator bug (a
directly-citable node was marked synthetic).

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

The `offendingNode` returned by Silicon is the Java object originally passed to Silicon via
JNI. Go-Gobra must maintain a map from JNI object identity (the Java object pointer returned
when constructing Silver nodes in plan 16) to the corresponding `*silver.Node` Go struct.
This map is populated during `SilverBridge.buildProgram(...)` (plan 16) and looked up here.
This is the concrete mechanism that replaces the Scala `Source.unapply(offendingNode)` call.
