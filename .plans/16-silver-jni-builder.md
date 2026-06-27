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

## Approach: Thin Java Helper JAR (SilverBridge)

Constructing Scala collection objects (`scala.collection.immutable.Seq`) directly from Go via
raw JNI is extremely painful: it requires calling `Nil$.MODULE$` and `$colon$colon()` for
every list element in reverse order, for every Silver constructor argument. Instead, a thin
Java helper class (`SilverBridge.java`, ~150 lines) wraps each Silver constructor with a
Java-friendly signature taking `Object[]` arrays. The Java code handles Scala collection
construction internally using `scala.jdk.CollectionConverters`. Go calls simple Java static
methods instead of navigating Scala internals.

```java
// SilverBridge.java (excerpt)
import scala.jdk.CollectionConverters;
import java.util.Arrays;
import viper.silver.ast.*;

public class SilverBridge {
    public static Method makeMethod(
            String name, Object[] params, Object[] returns,
            Object[] pres, Object[] posts, Seqn body,  // null = no body (abstract method)
            Object pos, Object info) {
        // scala.Option$.MODULE$.apply(x) returns Some(x) when x != null, None when x == null.
        // This is the correct Java interop idiom for Scala Option.
        scala.Option<Seqn> bodyOpt = scala.Option$.MODULE$.apply(body);
        // NoTrafos$.MODULE$ is the identity error transformation (no re-labelling).
        // Do NOT use ErrTrafo — that is a separate case class for custom transformations.
        Object errT = viper.silver.ast.NoTrafos$.MODULE$;
        return new Method(name,
            CollectionConverters.ListHasAsScala(Arrays.asList(params)).asScala().toList(),
            CollectionConverters.ListHasAsScala(Arrays.asList(returns)).asScala().toList(),
            CollectionConverters.ListHasAsScala(Arrays.asList(pres)).asScala().toList(),
            CollectionConverters.ListHasAsScala(Arrays.asList(posts)).asScala().toList(),
            bodyOpt, (Position) pos, (Info) info, errT);
    }
    // ... one method per Silver node type that Go needs to construct
}
```

**Note on `ErrTrafo`:** Silver nodes take an `ErrorTrafo` parameter used for error re-labelling
in transformations. Go-Gobra passes the identity transformation for all directly-constructed
nodes. The correct value is `NoTrafos$.MODULE$` — verified against the Silver submodule:
`NoTrafos` is a Scala `case object` implementing `ErrorTrafo` as the identity (no re-labelling).
`ErrTrafo` is a separate case class for custom transformations; it is not used here. In
`SilverBridge.java`, declare `errT` parameters as `Object` and pass `NoTrafos$.MODULE$`
from `viper.silver.ast.NoTrafos$`.

The JAR is compiled at build time (Makefile target), embedded in the Go binary via
`//go:embed`, extracted to a temp directory at startup, and added to the JVM classpath.
`scala.jdk.CollectionConverters` is available in the Silver/ViperServer fat JAR classpath.

## Key Implementation Notes

- **Go calls Java, Java calls Scala**: Go only calls `SilverBridge` static methods via jnigi.
  `SilverBridge` calls Silver Scala constructors normally (Scala is callable from Java).
- **Class caching**: cache the `SilverBridge` class reference and all static method IDs at JVM
  startup; do not call `FindClass` or `GetStaticMethodID` per-call.
- **Local vs. global references**: JNI local references are freed when the native frame exits;
  use `NewGlobalRef` for the `SilverBridge` class reference and any cached objects.
- **`NewGlobalRef` for `nodeMap` entries**: every Java object added to `nodeMap` must be
  promoted to a global reference via `jnigi.NewGlobalRef` *before* its pointer is stored as
  the map key. A local reference pointer is only stable within the current JNI frame; after
  the frame returns, the JVM may reuse the address for a different object, corrupting lookups.
  Promote immediately after construction; the map then owns the global reference for the
  object's lifetime. Call `DeleteGlobalRef` on all entries when the `Builder` is freed.
- **Error handling**: check for Java exceptions after every jnigi call; convert them to Go errors.
- **Memory**: Silver AST objects live on the JVM heap and are GC'd by the JVM; no manual free.
- **All JNI calls via JNI worker**: as specified in plan 15, route all calls through the
  dedicated JNI worker goroutine; `Build()` sends a request and waits for the result.

## JNI Object Identity Map

The reporter (plan 32) needs to retrieve the `NodeInfo` for an error's `offendingNode` — a
Java object returned by Silicon. The builder maintains a map from Java object pointer
(the `uintptr` of the JNI `jobject`) to the corresponding `*silver.Node` Go struct:

```go
type Builder struct {
    jvm      *jvm.JVM
    nodeMap  map[uintptr]*silver.Node // JNI jobject pointer → Go node
}
```

Every Silver node constructed via `SilverBridge` is registered in `nodeMap` immediately after
construction. When the reporter receives an offending node from Silicon, it extracts the
`jobject` pointer via jnigi, looks up `nodeMap`, and retrieves the `NodeInfo`.

**Lifetime:** The `nodeMap` is valid for the lifetime of the `Builder` and the corresponding
Silver program object. It must not be garbage-collected while Silicon holds references to the
Java Silver objects. The builder holds global JNI references (via `jnigi.NewGlobalRef`) on all
constructed objects to prevent the JVM from GC'ing them during verification.

## Deliverables

- `internal/backend/silver/SilverBridge.java` — Java helper class (~150 lines); includes
  methods `makeNoInfo()`, `makeAnnotationInfo(key, values)`, `makeConsInfo(head, tail)` for
  constructing the `@opaque`/`@reveal` info chains needed by plan 27
- `Makefile` target: compile `SilverBridge.java` against the ViperServer JAR, produce
  `SilverBridge.jar`, embed it via `//go:embed` in `internal/backend/silver/bridge_jar.go`
- `internal/backend/silver/builder.go` — `Build(prog *silver.Program, jvm *JVM) (*BuiltProgram, error)`,
  where:

  ```go
  type BuiltProgram struct {
      JavaObject jobject
      NodeMap    map[uintptr]*silver.Node // JNI object pointer → Go Silver node
  }

  // Close releases all JNI global references held for the Java Silver objects.
  // Must be called after BOTH:
  //   1. Silicon has finished verifying (Verify() has returned), AND
  //   2. The reporter has completed all NodeMap lookups (Report() has returned).
  //
  // After Close() returns, NodeMap entries are invalid and must not be accessed.
  // Close() must NOT be called concurrently with any NodeMap lookup — doing so
  // is a data race.
  //
  // Correct usage pattern (defer fires after both Verify and Report return):
  //
  //   built, err := builder.Build(prog, jvm)
  //   if err != nil { ... }
  //   defer built.Close()                              // runs after Report() returns
  //   result := silicon.Verify(built.JavaObject, cfg)
  //   diagnostics := reporter.Report(result, built.NodeMap, info)
  //   return diagnostics  // defer fires here, after Report() has completed
  //
  // Do NOT call Close() in a separate goroutine or before Report() returns,
  // even if Verify() has already returned.
  func (b *BuiltProgram) Close()
  ```

  Every entry in `NodeMap` uses a `NewGlobalRef`-promoted pointer as the key (see Key
  Implementation Notes). `Close()` calls `DeleteGlobalRef` on every entry. The `Builder`
  type itself is stateless after `Build()` returns and does not need a `Close()` method.
- Tests: build a minimal Silver program (one method, one statement); confirm no exceptions;
  confirm the resulting object passes `silver.ast.Program.checkTransitively()`; confirm that
  `NodeMap` contains an entry for the method's body statement whose `NodeInfo` matches the
  Go source position used during construction

## Version Coupling Note

`SilverBridge.java` must be compiled against **exactly the same version of Silver** that is
bundled in the ViperServer JAR used at runtime. If the Silver API changes (constructor
signatures, collection types, etc.), `SilverBridge.java` must be updated in lockstep.

To prevent silent breakage:
- The Makefile target must accept an explicit `VIPERSERVER_JAR` variable and fail if it is
  not set (no hardcoded default path).
- The compiled `SilverBridge.jar` should embed the Silver version string (read from the JAR
  manifest) as a constant, and `jvm.go` should assert at startup that the version string in
  `SilverBridge.jar` matches the version in the runtime ViperServer JAR. Fail fast with a
  clear message if they differ.
- When the `viperserver/` submodule is updated, re-running `make SilverBridge.jar` is
  mandatory; document this in the repo README.

## Resolved Questions

**One method per Silver node type vs. grouped (resolved):** `SilverBridge.java` defines one
static method per Silver node type. Grouping (e.g., a single `makeStmt` with a discriminant
integer) would require casting on both sides and loses the type-safety that makes JNI code
auditable. One method per type is verbose (~150 lines for the full Silver AST) but each
method is trivially correct, independently testable, and easy to extend when a new Silver node
type is needed. Name each method `make{NodeType}` (e.g., `makeMethod`, `makeSeqn`,
`makeFuncApp`) for consistency.
