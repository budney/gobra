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

**Worker pool**: Go-Gobra uses a `WorkerPool` of JNI worker goroutines rather than a single
dedicated worker. This plan implements the pool with `poolSize=1` as the baseline; plan 17b
expands it to N workers for parallel verification of chopped sub-programs.

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
- `WorkerPool` type: `NewPool(jvm *JVM, poolSize int) *WorkerPool`; `Submit(job VerifyJob) <-chan Result`; `Stop()`. Implement with `poolSize=1` in this plan; plan 17b expands to N.
- Worker goroutine template: `runtime.LockOSThread()` at startup, `AttachCurrentThread`, request loop, `DetachCurrentThread` on exit
- `libjvm` runtime probing across all known paths
- Build tag or `cgo` preamble isolating the JNI dependency
- Tests: start the JVM, call `java.lang.System.getProperty("java.version")`, verify it returns
  a non-empty string; confirm probe succeeds under a jenv-managed `JAVA_HOME`

## Open Questions

- Should the JVM be a global singleton (one per Go-Gobra process) or allow multiple instances?
  JNI only supports one JVM per process; singleton is correct.
- CGo complicates cross-compilation; document this limitation explicitly in the README.
