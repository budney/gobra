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

- **macOS**: `$JAVA_HOME/lib/server/libjvm.dylib`
- **Linux**: `$JAVA_HOME/lib/server/libjvm.so`
- **Windows**: `%JAVA_HOME%\bin\server\jvm.dll`
- All platforms: `JAVA_HOME` env var or runtime detection via `java -XshowSettings:all`

## Deliverables

- `internal/backend/jvm/jvm.go` — `Start(cfg JVMConfig) (*JVM, error)` and `(*JVM).Stop()`
- `JVMConfig` struct: JAR paths, JVM flags, Z3 path
- Build tag or `cgo` preamble isolating the JNI dependency
- Tests: start the JVM, call `java.lang.System.getProperty("java.version")`, verify it returns
  a non-empty string

## Open Questions

- Should the JVM be a global singleton (one per Go-Gobra process) or allow multiple instances?
  JNI only supports one JVM per process; singleton is correct.
- How to handle the case where `libjvm` cannot be found? Fail fast with a clear error message
  directing the user to set `JAVA_HOME`.
- CGo complicates cross-compilation; document this limitation explicitly.
