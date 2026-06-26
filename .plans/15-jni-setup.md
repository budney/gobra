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

1. `$JAVA_HOME/lib/server/libjvm.dylib` (macOS, standard HotSpot)
2. `$JAVA_HOME/jre/lib/server/libjvm.dylib` (older macOS JDK layouts)
3. `$JAVA_HOME/lib/server/libjvm.so` (Linux)
4. `$JAVA_HOME/jre/lib/amd64/server/libjvm.so` (Linux, older)
5. `%JAVA_HOME%\bin\server\jvm.dll` (Windows)

If none succeed, fail fast with a message directing the user to set `JAVA_HOME` correctly.

**jenv compatibility**: jenv manages `JAVA_HOME` via shell shims. The shims affect PATH but
`JAVA_HOME` must resolve to the *real* JDK installation directory, not a shim. Require users
to enable jenv's export plugin: `jenv enable-plugin export`. Document this in the error message
when `libjvm` is not found.

**Thread attachment**: JNI requires every OS thread that calls into the JVM to be attached.
CGo goroutines may run on any OS thread. The JVM wrapper must attach the current thread before
any JNI call and detach when done (or keep it attached for the lifetime of the goroutine).
Use a `sync.Map` keyed by goroutine OS thread ID to track attachment state. Because the JVM
is a singleton and JNI is not goroutine-safe in general, all JNI calls from Go-Gobra are
channeled through a single dedicated goroutine (the "JNI worker"). Other goroutines send
requests and await results over a channel. This pattern also makes parallel test execution
safe (see plan 34).

**SilverBridge JAR**: At JVM startup, extract the embedded `SilverBridge.jar` (see plan 16)
to a temp directory and add it to the classpath alongside the ViperServer JAR.

## Deliverables

- `internal/backend/jvm/jvm.go` — `Start(cfg JVMConfig) (*JVM, error)` and `(*JVM).Stop()`
- `JVMConfig` struct: ViperServer JAR path, SilverBridge JAR path (embedded), JVM flags, Z3 path
- JNI worker goroutine with request/response channel
- `libjvm` runtime probing across all known paths
- Build tag or `cgo` preamble isolating the JNI dependency
- Tests: start the JVM, call `java.lang.System.getProperty("java.version")`, verify it returns
  a non-empty string; confirm probe succeeds under a jenv-managed `JAVA_HOME`

## Open Questions

- Should the JVM be a global singleton (one per Go-Gobra process) or allow multiple instances?
  JNI only supports one JVM per process; singleton is correct.
- CGo complicates cross-compilation; document this limitation explicitly in the README.
