# 15 — Subprocess Lifecycle

## Objective

Fork a `SilverServer` JVM subprocess per worker, manage its lifecycle (start, health-check,
shutdown), and provide the shared backend types used by all plans in Group 4. No CGo, no JNI,
no `runtime.LockOSThread()`.

## Scope

**In scope:**
- `SubprocessConfig` struct: path to `SilverServer` fat JAR, JVM flags, Z3 executable path,
  gRPC port allocation
- `Start(cfg SubprocessConfig) (*Backend, error)` — forks the JVM subprocess, waits for the
  gRPC ready signal (stdout sentinel line `"SILVERSERVER_READY"`), returns a connected `*Backend`
- `(*Backend).Stop()` — sends SIGTERM to the subprocess; waits up to a configurable grace
  period, then SIGKILL
- Health check: `(*Backend).Ping() error` — sends a gRPC `PingRequest`; used by workers to
  detect and restart dead subprocesses
- `WorkerPool` type skeleton and `workerJob` struct (channel infrastructure and goroutine-
  lifecycle skeleton; full Silicon-aware body is in plan 15b)
- `internal/backend/types.go` — shared backend types owned by this plan: `VerificationResult`,
  `VerificationError`, `SiliconInstance` interface, `SiliconConfig` struct. These live in the
  parent `backend` package so both `subprocess` and `silicon` sub-packages can import them
  without a circular dependency. See Deliverables for the full definitions.

**Out of scope:**
- Protobuf serialization of the Silver AST (16-silver-jni-builder.md)
- Calling Silicon's verify method (17-silicon-backend.md)
- `SilverServer.scala` and `silver.proto` (owned by this plan's registry entry but built
  separately; see `SilverServer` artifact in D2)

## Dependencies

- [01-project-setup.md](01-project-setup.md) — repository and build tooling
- [14-silver-ast.md](14-silver-ast.md) — `*silver.Program` is the job payload type in
  `workerJob`; `silver.Node` and `silver.NodeInfo` are used in `VerificationResult` and
  `VerificationError` (defined by this plan in `internal/backend/types.go`)

## Reference: Prior Art

- D2 in `DECISIONS.md` — architectural rationale for the subprocess approach
- `src/main/scala/viper/gobra/backend/ViperBackends.scala` — current Gobra's backend
  interface (reference only; not the implementation path used here)

## Platform Notes

**JVM location**: Go-Gobra locates the JVM binary (`java`) via `JAVA_HOME`:
1. `$JAVA_HOME/bin/java` (preferred on all platforms)
2. PATH lookup as fallback if `JAVA_HOME` is unset

If neither resolves, fail fast with a message directing the user to set `JAVA_HOME`.

**jenv compatibility**: jenv manages `JAVA_HOME` via shell shims. Require users to enable
jenv's export plugin: `jenv enable-plugin export`. The subprocess fork uses `JAVA_HOME/bin/java`
directly; PATH shims are insufficient. Document this in the error message when the JVM is
not found.

**Subprocess per worker**: Each worker goroutine (plan 15b) owns one `SilverServer`
subprocess for fault isolation — a JVM crash kills only that worker's subprocess, not the
Go-Gobra process. Workers restart their subprocess independently via `Start()`. No singleton.

**Import discipline**: Plan 15 does NOT import the `silicon` sub-package. Both `subprocess`
and `silicon` sub-packages import their shared parent package `gobra/internal/backend` for
`VerificationResult`, `SiliconInstance`, and `SiliconConfig`. This avoids the import cycle.

## Deliverables

### `internal/backend/types.go` (package `backend`) — Shared Backend Types

```go
// SiliconInstance is the interface implemented by silicon.SiliconFrontendAPI.
// Defined here so the subprocess sub-package can use it without importing silicon.
type SiliconInstance interface {
    Initialize(args []string)
    Verify(prog *proto.SilverProgram) *VerificationResult
    Stop()
}

// SiliconConfig holds configuration for a Silicon verification session.
type SiliconConfig struct {
    Args        []string        // Silicon startup flags (Z3 path, timeout, etc.)
    Instance    SiliconInstance // non-nil: reuse existing instance (warm path, e.g. tests)
                                // nil: NewInstance will be called (cold path)
    NewInstance func() SiliconInstance // factory: called once per worker in NewPool
}

// VerificationResult holds the outcome of one Silicon verification call.
type VerificationResult struct {
    Success bool
    Errors  []VerificationError    // non-nil only when Success == false
    Err     error                  // non-nil if the gRPC call itself failed
    NodeMap map[uint64]silver.Node // stable node ID → Go Silver node; for searchInfo fallback
}

// VerificationError is a single verification failure with position and message.
type VerificationError struct {
    Pos     silver.NodeInfo
    Node    silver.Node  // Go Silver node for searchInfo DFS; populated by worker
    FullID  string
    Message string
    Reason  string
}
```

`silver.Node` and `silver.NodeInfo` are from plan 14's `internal/silver/` package. `proto`
refers to the generated Go Protobuf bindings for `silver.proto` (owned by the `SilverServer`
artifact, D2).

Plan 17 (`internal/backend/silicon/`) and plan 15b (`internal/backend/subprocess/`) both
import `gobra/internal/backend` (this package) for these shared types.

**`VerificationResult.Close` is removed** — there are no JNI global references to free.
The Go `NodeMap` (a plain `map[uint64]silver.Node`) is GC'd normally after the result is
consumed. Callers do NOT need to call `Close()` after `Report()`.

### `internal/backend/subprocess/subprocess.go` — Subprocess Lifecycle

- `SubprocessConfig` struct: `JarPath string`, `JVMArgs []string`, `Z3Exe string`,
  `GRPCPort int` (0 = pick a free port automatically)
- `Start(cfg SubprocessConfig) (*Backend, error)` — forks `java -jar <JarPath> <JVMArgs>
  --z3Exe <Z3Exe> --port <port>`, reads stdout until the sentinel line
  `"SILVERSERVER_READY"`, dials a gRPC connection, returns `*Backend`
- `type Backend struct { cmd *exec.Cmd; conn *grpc.ClientConn; client proto.SilverServerClient }`
- `(*Backend).Stop()` — sends SIGTERM; waits `cfg.ShutdownGrace` (default 5s); sends SIGKILL
- `(*Backend).Ping() error` — `client.Ping(ctx, &proto.PingRequest{})` with a short timeout;
  returns non-nil if the subprocess is unresponsive
- `(*Backend).Client() proto.SilverServerClient` — returns the gRPC client for use by plan 17
- `WorkerPool` type skeleton: the `WorkerPool` struct and `Stop()` method.
  This plan delivers the channel infrastructure and goroutine-lifecycle skeleton only.
  `NewPool` (the constructor with full signature) and `Submit` are defined in plan 15b.
- `workerJob` struct (defined here):
  ```go
  type workerJob struct {
      prog   *silver.Program
      result chan *backend.VerificationResult
  }
  ```
- Tests: start a `SilverServer` subprocess; call `Ping()`; confirm it responds; call `Stop()`;
  confirm the process exits

### `internal/backend/silverserver/` — Embedded Fat JAR

- `internal/backend/silverserver/jar.go` — `//go:embed SilverServer.jar`; exports
  `var SilverServerJAR []byte` (written to a temp file at startup for subprocess fork)
- `SilverServer.scala` (~300 lines) and `silver.proto` are the Scala/gRPC artifacts owned by
  this plan's package ownership registry entry; they are built via `make SilverServer.jar`
  and embedded. See D2 in `DECISIONS.md` for the full artifact description.

### `internal/proto/` — Generated Go Protobuf Bindings

- Generated by `protoc --go_out=internal/proto --go-grpc_out=internal/proto silver.proto`
  (run from `internal/backend/silverserver/`). Checked in as generated code.
- Exports: `proto.SilverProgram`, `proto.SilverMethod`, `proto.VerifyRequest`,
  `proto.VerifyResponse`, `proto.SilverServerClient`, `proto.PingRequest`, etc.
- Plan 16 (`Serialize`), plan 17 (`Verify`), and plan 15b (worker goroutine) all import
  this package. Because `silver.proto` is owned by plan 15, the generated bindings are also
  plan 15's deliverable — no circular dependency arises.

## Resolved Questions

**No JVM singleton**: Each worker forks its own subprocess. There is no `sync.Once` and no
process-wide JVM state. If a worker's subprocess crashes, `Ping()` detects it; the worker
calls `Start()` again to restart it. Other workers are unaffected.

**No thread pinning**: Worker goroutines are plain goroutines. `runtime.LockOSThread()` is
not called. There is no `AttachCurrentThread` / `DetachCurrentThread`. Workers communicate
with their subprocess via gRPC over a local TCP connection — no OS thread constraints apply.

**CGo removed**: `CGO_ENABLED=0` builds are fully supported. The Go binary cross-compiles
freely; a JVM of the correct version is required only at runtime on the target platform (D2).

**`backend.ThreadAttached()` predicate**: This predicate and all JNI thread-safety specs are
**removed entirely**. No plan may reference `ThreadAttached()`. The predicate does not exist
in `internal/backend/types.go` in this design.

### Synchronization Contract (C7)

| Resource | Mechanism | Plan |
|---|---|---|
| Subprocess fork | `sync.Mutex` on `Backend` struct during `Start`/`Stop` | 15 |
| Worker pool channel | buffered `chan workerJob` | 15 |
| `SiliconFrontendAPI` per worker | goroutine-local (no sharing) | 15b |

No `sync.Once` JVM singleton. No OS-thread locking. Workers may call `Start()` concurrently;
each call forks an independent subprocess.

### Verification Specifications (C9)

1. **`Start` postcondition** — on success the returned `*Backend` is non-nil and the
   subprocess is reachable:
   ```go
   //@ ensures err == nil ==> b != nil
   func Start(cfg SubprocessConfig) (b *Backend, err error)
   ```

2. **`Stop` postcondition** — after `Stop` returns, the subprocess is no longer running:
   ```go
   //@ requires b != nil
   //@ ensures  !b.Running()
   func (b *Backend) Stop()
   ```
   `Running()` is a ghost pure function that inspects `b.cmd.ProcessState`.

3. **`WorkerPool` non-nil result**:
   ```go
   //@ requires poolSize >= 1
   //@ ensures  pool != nil
   func NewPool(poolSize int, cfg backend.SiliconConfig) (pool *WorkerPool)
   ```
   (`NewPool`'s full body is in plan 15b; the spec is declared here because `WorkerPool` is
   owned by this plan.)
