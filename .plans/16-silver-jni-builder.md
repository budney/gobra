# 16 — Silver JNI Builder

## Objective

Implement the bridge that takes a Go Silver AST (14) and constructs equivalent Java Silver AST
objects via JNI, using the live JVM (15). The resulting Java `viper.silver.ast.Program` object
is passed directly to Silicon.

## Scope

**In scope:**
- JNI bindings for every Silver Java class needed to construct a `silver.ast.Program`:
  programs, domains, methods, functions, predicates, fields, statements, expressions, types,
  positions, and info objects
- A recursive builder that walks the Go Silver AST and calls the corresponding Java constructors
  via JNI
- Position objects: Silver uses `viper.silver.ast.SourcePosition`; construct these from the
  Go source positions carried in the Silver Go AST
- Info objects: Silver nodes carry an `Info` field (usually `NoInfo` or `SimpleInfo`); handle
  both

**Out of scope:**
- JVM lifecycle (15-jni-setup.md)
- Defining the Go Silver AST types (14-silver-ast.md)
- Calling Silicon's verify method (17-silicon-backend.md)

## Dependencies

- [14-silver-ast.md](14-silver-ast.md) — input: Go Silver AST
- [15-jni-setup.md](15-jni-setup.md) — live JVM to call into

## Reference: Current Gobra / Silver

- `viperserver/silicon/silver/src/main/scala/viper/silver/ast/` — the Java/Scala classes
  that must be constructed via JNI; use these class names and constructor signatures as the
  JNI call targets
- Prusti's `viper-sys/` and `jni-gen/` — the closest prior art; Rust uses JNI similarly;
  adapt the class-lookup and method-call patterns to jnigi
- `viperserver/silicon/silver/src/main/scala/viper/silver/ast/Program.scala` — `Program`
  constructor signature is the top-level target

## Key Implementation Notes

- **Class caching**: JNI class lookups (`FindClass`) are expensive; cache all class and method
  references at JVM startup, not per-call
- **Local vs. global references**: JNI local references are freed when the native frame exits;
  use `NewGlobalRef` for objects that outlive a single call
- **Error handling**: check for Java exceptions after every JNI call (`ExceptionCheck`);
  convert them to Go errors
- **Memory**: Silver AST objects are on the JVM heap; they are GC'd by the JVM; no manual
  free needed, but the JVM GC must be allowed to run (don't hold the GIL equivalent)
- **Array construction**: Silver constructors take Scala `Seq` arguments; construct these as
  `scala.collection.immutable.Seq` via JNI or use the Java `Arrays.asList` bridge

## Deliverables

- `internal/backend/silver/builder.go` — `Build(prog *silver.Program, jvm *JVM) (jobject, error)`
- Cached class/method reference table initialized at JVM start
- Tests: build a minimal Silver program (one method, one statement) via JNI; confirm no
  exceptions are thrown and the resulting object passes `silver.ast.Program.checkTransitively()`

## Open Questions

- Scala collections (Seq, Set, Map) are needed to call Silver constructors; the easiest path
  is to use `scala.jdk.CollectionConverters` to convert Java lists. Confirm this is available
  in the Silver/Silicon JAR bundled classpath.
