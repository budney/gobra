# 15 — JNI Setup & JVM Lifecycle

## Objective

Integrate [jnigi](https://github.com/timob/jnigi) to embed a JVM in the Go process, load the
Silicon/ViperServer JARs onto the classpath, and provide a lifecycle API (start, stop) that
the rest of the backend uses.

## Scope

**In scope:**
- Add `jnigi` as a Go module dependency (requires CGo)
- JVM startup: locate `libjvm` (platform-specific: `.so` on Linux, `.dylib` on macOS,
  `.dll` on Windows), load it at runtime
- Classpath construction: locate the Silicon/ViperServer fat JAR (from an environment variable
  or config flag, e.g. `VIPERSERVER_JAR`), Z3 executable location
- JVM options: heap size, stack size, Silicon-specific JVM flags
- Graceful shutdown: shut down the JVM when Go-Gobra exits
- Thread attachment: JNI requires each OS thread that calls into the JVM to be attached;
  manage attach/detach around JNI calls

**Out of scope:**
- Building the Silver Java AST (16-silver-jni-builder.md)
- Calling Silicon's verify method (17-silicon-backend.md)

## Dependencies

- [01-project-setup.md](01-project-setup.md) — repository and build tooling

## Reference: Prior Art

- [jnigi README](https://github.com/timob/jnigi) — usage examples
- Prusti's JVM setup in `viper/src/` — see how it locates `libjvm` and constructs the
  classpath; the Go equivalent follows the same pattern
- `src/main/scala/viper/gobra/backend/ViperBackends.scala` — current Gobra's JVM setup
  (but embedded, not via JNI)

## Platform Notes

**Do not hardcode a single path** — `libjvm` location varies across JDK distributions, versions,
and package managers. Use runtime probing in this order:

1. `$JAVA_HOME/lib/server/libjvm.dylib` (macOS, standard HotSpot Intel/ARM64)
2. `$JAVA_HOME/lib/libjvm.dylib` (macOS, Temurin/Zulu on Apple Silicon — no `server/` subdir)
3. `$JAVA_HOME/jre/lib/server/libjvm.dylib` (older macOS JDK layouts)
4. `$JAVA_HOME/lib/server/libjvm.so` (Linux)
5. `$JAVA_HOME/jre/lib/amd64/server/libjvm.so` (Linux, older)
6. `%JAVA_HOME%\bin\server\jvm.dll` (Windows)

If none succeed, fail fast with a message directing the user to set `JAVA_HOME` correctly.

**jenv compatibility**: jenv manages `JAVA_HOME` via shell shims. The shims affect PATH but
`JAVA_HOME` must resolve to the *real* JDK installation directory, not a shim. Require users
to enable jenv's export plugin: `jenv enable-plugin export`. Document this in the error message
when `libjvm` is not found.

**Worker pool skeleton**: Go-Gobra uses a `WorkerPool` of JNI worker goroutines. This plan
delivers the `poolSize=1` baseline pool skeleton: JVM lifecycle, thread attach/detach, and
the `workerJob` channel infrastructure. The full Silicon-aware worker template (which
initialises a `SiliconFrontendAPI` per worker) and N-worker expansion are in plan 15b
(which depends on plan 17 for `SiliconFrontendAPI`). This plan does NOT reference
`SiliconFrontendAPI` or `SiliconConfig` — doing so would create a dependency cycle
(plan 17 depends on plan 15).

Each worker goroutine **must call `runtime.LockOSThread()` at startup** and never unlock it.
`LockOSThread` pins the goroutine to a single OS thread for its entire lifetime, which is
required because JNI thread attachment is per-OS-thread: `AttachCurrentThread` is called once
on that fixed OS thread, and all subsequent JNI calls from that worker run on the same
attached thread. Without `LockOSThread`, the Go scheduler can silently migrate the goroutine
to a different OS thread between CGo calls, making the JNI attachment state inconsistent.

Each worker goroutine is independent — workers do not share JNI state. The pool design is
therefore correct for both `poolSize=1` and `poolSize=N` without any coordination between
workers. Plan 34 (test infrastructure) is safe at any pool size because each worker
serializes its own JNI calls internally.

**SilverBridge JAR**: At JVM startup, extract the embedded `SilverBridge.jar` (see plan 16)
to a temp directory and add it to the classpath alongside the ViperServer JAR.

## Deliverables

- `internal/backend/jvm/jvm.go` — `Start(cfg JVMConfig) (*JVM, error)` and `(*JVM).Stop()`
- `JVMConfig` struct: ViperServer JAR path, SilverBridge JAR path (embedded), JVM flags, Z3 path
- `WorkerPool` type skeleton: `NewPool(jvm *JVM, poolSize int) *WorkerPool`; `Stop()`.
  This plan delivers the channel infrastructure and thread-lifecycle skeleton only.
  The `Submit(prog *silver.Program) *VerificationResult` method and the full Silicon-aware
  worker goroutine template are defined in plan 15b (which has the `SiliconFrontendAPI`
  dependency). Plan 15 exports the `workerJob` channel and the `WorkerPool` struct so that
  plan 15b can complete the implementation.
- Worker goroutine skeleton — establishes the thread-locking invariant:

  ```go
  // runWorkerSkeleton is the JVM-level thread setup. The Silicon-specific
  // body (SiliconFrontendAPI init, Build, Verify loop) is added in plan 15b.
  func runWorkerSkeleton(jvm *JVM, jobs <-chan workerJob) {
      runtime.LockOSThread()
      jvm.AttachCurrentThread()
      defer jvm.DetachCurrentThread()
      // Plan 15b fills in the rest.
  }
  ```

  The `workerJob` struct (defined here):
  ```go
  type workerJob struct {
      prog   *silver.Program
      result chan *VerificationResult
  }
  ```
- `libjvm` runtime probing across all known paths
- Build tag or `cgo` preamble isolating the JNI dependency
- Tests: start the JVM, call `java.lang.System.getProperty("java.version")`, verify it returns
  a non-empty string; confirm probe succeeds under a jenv-managed `JAVA_HOME`

## Resolved Questions

**JVM singleton (resolved):** JNI supports at most one JVM per OS process. `JNI_CreateJavaVM`
returns an error if called a second time in the same process and may crash on concurrent calls.
Go-Gobra uses a process-wide JVM singleton initialized via `sync.Once`: `jvm.Start()` is safe
to call from multiple goroutines — only the first call creates the JVM; subsequent calls return
the existing instance. Implement as:

```go
var (
    jvmOnce     sync.Once
    jvmInstance *JVM
    jvmErr      error
)

func Start(cfg JVMConfig) (*JVM, error) {
    jvmOnce.Do(func() {
        jvmInstance, jvmErr = createJVM(cfg)
    })
    return jvmInstance, jvmErr
}
```

`jvm.Stop()` is called once at process exit via `defer` in `main`. Do not call `Stop()`
concurrently with any JNI worker.

**CGo and cross-compilation (resolved):** CGo is required (jnigi depends on it). Cross-
compilation is limited to target platforms where a JVM is available. Document in the README:
"Go-Gobra requires CGo and cannot be cross-compiled to platforms without JVM support. Set
`CGO_ENABLED=1` (the default) when building."

### Synchronization Contract (C7)

1. Operating System Thread Lock: Every Go function initializing or invoking a JNI call must explicitly execute `runtime.LockOSThread()` before interacting with the `jnigi` environment.
2. JVM Attach/Detach Lifecycle: All entry points must utilize a `defer` block ensuring the current OS thread is cleanly detached from the JVM upon function exit.
3. Global Instance Mutex: Access to the initialized JVM instance pointer must be guarded by a package-level sync.Once or sync.Mutex.

### Verification Specifications (C9)

1. The JNI bridge package must formally specify thread state. It must use Gobra thread-ownership permissions to prove that an un-locked or un-attached thread can never issue a raw call to the JVM helper (`SilverBridge.java`).

