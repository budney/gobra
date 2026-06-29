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
- **Node ID approach for `nodeMap`**: every Java object added to `nodeMap` is keyed by a
  unique `uint64` ID, NOT by its JNI object pointer. JNI does not guarantee that the `uintptr`
  of a local reference returned by Silicon (for an offending node) equals the `uintptr` of the
  global reference originally stored — the JNI spec requires `IsSameObject` for reference
  comparison. Using pointer equality would cause nodeMap lookups to fail non-deterministically.
  
  The correct approach:
  1. Assign a monotonically-increasing `uint64` ID to each Go Silver node at Build() time.
  2. Embed the ID in the node's Viper `Info` chain as `AnnotationInfo("gobra_node_id", [id])`,
     chained via `ConsInfo` ahead of any existing `VprInfo`.
  3. The nodeMap key is the `uint64` ID: `nodeMap map[uint64]silver.Node`.
  4. `SilverBridge.getNodeId(node)` (see Deliverables) retrieves the ID from the node's
     `Info` chain, enabling stable lookups regardless of JNI reference type.
  
  Global JNI references are still required for the Java Silver objects themselves (to prevent
  GC while Silicon holds the program), but they are NOT used as map keys. Call
  `jnigi.NewGlobalRef` on each constructed node for GC safety; call `DeleteGlobalRef` in
  `BuiltProgram.Close()` to release them.
- **Error handling**: check for Java exceptions after every jnigi call; convert them to Go errors.
- **Memory**: Silver AST objects live on the JVM heap and are GC'd by the JVM; no manual free.
- **Threading model for `Build()`**: `Build()` is a **direct, synchronous call** — it does NOT
  dispatch via a channel or send a request to the worker pool. The caller is responsible for
  ensuring it is already executing on the designated JNI OS thread (i.e., inside a goroutine
  that has called `runtime.LockOSThread()` and `jvm.AttachCurrentThread()`). In practice,
  `Build()` is called by plan 15b's worker goroutine from inside the `for job := range jobs`
  loop. If `Build()` were to dispatch via the worker channel, the worker goroutine would
  deadlock: it would block waiting for itself to process the dispatch request while already
  blocked inside `Build()`. Do not add channel dispatch logic to `Build()`.

## Node Identity Map

The worker (plan 15/17b) fills in `VerificationError.Pos` for each error returned by Silicon
by resolving the error's offending node back to its Go Silver struct (which carries `NodeInfo`).
This resolution uses a `uint64`-keyed node map, not JNI pointer comparison.

```go
// globalNodeID is a process-wide atomic counter for Silver node IDs.
// Using a global counter (rather than a per-Builder counter) ensures IDs are
// unique across all Builder instances and all parallel builds (plan 17b).
// A per-Builder counter starting at 0 would produce overlapping ID spaces
// when multiple workers call Build() concurrently, breaking merged NodeMap lookups.
var globalNodeID atomic.Uint64

// NodeMap maps a stable uint64 node ID to the corresponding Go Silver node interface value.
// silver.Node is an interface (not a pointer-to-interface); concrete stored values are
// *silver.Method, *silver.Assert, etc. This is the canonical type definition used across
// plans 16, 17, 17b, and 32. Always map[uint64]silver.Node — never map[uint64]*silver.Node.
type Builder struct {
    jvm     *jvm.JVM
    nodeMap map[uint64]silver.Node  // stable integer ID → Go Silver node
}
```

**Build-time registration (per Silver node):**
1. Increment `globalNodeID` atomically (`id := globalNodeID.Add(1)`); assign the current value as this node's ID.
2. Wrap the node's existing `VprInfo` with the ID:
   - `info = SilverBridge.makeConsInfo(SilverBridge.makeAnnotationInfo("gobra_node_id", id.toString()), existingVprInfo)`
3. Pass the wrapped info to the Silver constructor via `SilverBridge.make*(...)`.
4. Register: `nodeMap[id] = goSilverNode`.
5. Promote the Java object to a global JNI reference via `jnigi.NewGlobalRef` for GC safety
   (the global ref is NOT used as the map key).

**Lookup (by the worker, plan 15/17b):**
When Silicon returns an `AbstractError`, call `SilverBridge.getNodeId(offendingNode)` (see
Deliverables) to retrieve the `uint64` ID from the node's `Info` chain, then look up
`nodeMap[id]` to get the Go Silver node and its `NodeInfo`. This is O(1) and immune to JNI
reference-type differences.

The `searchInfo` DFS fallback (plan 32) also uses the nodeMap: when a synthetic node's `Pos`
has `Tag == "synthetic"`, the reporter calls `searchInfo(goSilverNode)` which calls
`Children()` on Go Silver structs — requiring the nodeMap entry for the starting node.

**Lifetime:** `nodeMap` is valid while `BuiltProgram` is alive (before `Close()` is called).
`Close()` calls `DeleteGlobalRef` on all promoted Java objects and nils the nodeMap.

## Deliverables

- `internal/backend/silver/SilverBridge.java` — Java helper class (~175 lines); includes:
  - Construction methods: `makeNoInfo()`, `makeAnnotationInfo(key, values)`, `makeConsInfo(head, tail)` for constructing info chains (including `@opaque`/`@reveal` annotations for plan 27 and `gobra_node_id` annotations for node identity). **`@opaque` may be placed anywhere in the ConsInfo chain** — Silicon's `getUniqueInfo` (in `Ast.scala`) recursively searches head→tail until found; no hoisting to chain head is required.
  - **`static long getNodeId(Object node)`** — reads the `gobra_node_id` annotation from a Silver node's `Info` chain and returns it as a `long`. Returns `-1` if no `gobra_node_id` annotation is present (indicating a node built outside Go-Gobra, which should not happen in practice). Implementation: walk the info chain (`ConsInfo`, `AnnotationInfo`) looking for key `"gobra_node_id"`; parse the first value as a long.
  - **`static String getNodeFile(Object node)`**, **`static int getNodeLine(Object node)`**, **`static int getNodeCol(Object node)`**, **`static String getNodeTag(Object node)`** — extract individual `NodeInfo` fields stored as `gobra_node_file`, `gobra_node_line`, `gobra_node_col`, `gobra_node_tag` annotations (see note below). These allow the worker to populate `VerificationError.Pos` without a nodeMap lookup for the common non-synthetic case.
  
  **Note on NodeInfo in the Info chain:** In addition to `gobra_node_id`, the builder also embeds the `NodeInfo` fields as annotations: `AnnotationInfo("gobra_node_file", [file])`, `AnnotationInfo("gobra_node_line", [line])`, `AnnotationInfo("gobra_node_col", [col])`, `AnnotationInfo("gobra_node_tag", [tag])`, all chained before the existing `VprInfo`. The `getNode*` methods above retrieve these. This mirrors the Scala Gobra's approach of embedding Go source position directly in the Viper `Info` chain — the Go-side `nodeMap` is kept as a fallback for `searchInfo` DFS (which needs the Go Silver struct for `Children()`), not as the primary position source.
- `Makefile` target: compile `SilverBridge.java` against the ViperServer JAR, produce
  `SilverBridge.jar`, embed it via `//go:embed` in `internal/backend/silver/bridge_jar.go`
- `internal/backend/silver/builder.go` — `Build(prog *silver.Program, jvm *JVM) (*BuiltProgram, error)`,
  where:

  ```go
  type BuiltProgram struct {
      JavaObject jobject
      NodeMap    map[uint64]silver.Node   // stable integer ID → Go Silver node (canonical definition; plans 17, 17b, 32 use this type)
      globalRefs []jobject               // JNI global refs for GC safety; freed by Close()
  }

  // Close releases all JNI global references in globalRefs (the Java Silver objects
  // promoted via jnigi.NewGlobalRef during Build). The nodeMap uint64 keys are NOT
  // JNI references and do not need freeing.
  //
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

  The `globalRefs` field (add it to `BuiltProgram` alongside `JavaObject` and `NodeMap`)
  tracks the JNI global references promoted via `jnigi.NewGlobalRef` for each constructed
  Silver node. These Java Silver objects are NOT the nodeMap keys — the map key is the
  `uint64` ID. `Close()` calls `jnigi.DeleteGlobalRef` on every entry in `globalRefs`, then
  nils `NodeMap` and `globalRefs`. The `Builder` type itself is stateless after `Build()`
  returns and does not need a `Close()` method.

  Updated `BuiltProgram` struct:
  ```go
  type BuiltProgram struct {
      JavaObject jobject
      NodeMap    map[uint64]silver.Node   // stable integer ID → Go Silver node (canonical definition; plans 17, 17b, 32 use this type)
      globalRefs []jobject               // JNI global refs promoted for GC safety; freed by Close()
  }
  ```
- Tests: build a minimal Silver program (one method, one statement); confirm no exceptions;
  confirm the resulting object passes `silver.ast.Program.checkTransitively()`; confirm that
  `NodeMap` contains an entry for the method's body statement whose `NodeInfo` matches the
  Go source position used during construction
- **Dual-NodeInfo sync test**: for the same minimal Silver program, assert that the position
  extracted via `SilverBridge.getNodeFile/Line/Col/Tag(javaObject)` (the Java-side copy)
  matches the Go-side `NodeInfo` field on the corresponding `*silver.Node` in `NodeMap`. This
  test guards the invariant that both copies are written from the same source data during
  `Build()` and will catch any future refactor that updates one copy but not the other.

## Dual NodeInfo Storage (Intentional)

Every Silver node stores `NodeInfo` in two places:

1. **Go-side `NodeInfo` field** on the Go Silver struct (e.g., `silver.Assert.Info NodeInfo`).
   Used by `searchInfo` DFS in plan 32, which calls `Children()` on Go Silver structs to walk
   downward from a synthetic node to find the nearest non-synthetic `NodeInfo`.

2. **Java-side `AnnotationInfo` entries** embedded in the Viper `Info` chain of the
   constructed Java Silver object. Used by the JNI worker (plan 17) to call
   `SilverBridge.getNodeFile/Line/Col/Tag(offendingNode)` and populate `VerificationError.Pos`
   **without any cross-language roundtrip through the Go nodeMap** — the position is read
   directly from the Java object's annotations after Silicon returns it.

Both copies are required and must stay in sync. The Go-side copy enables the DFS fallback;
the Java-side copy enables the primary (fast) position extraction path. Do not treat one as
authoritative and omit the other.

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

## Verification Specifications (C9)

Plan 16 constructs JNI global references and transfers them across the JNI boundary; Gobra
permissions formalize the ownership contract so the compiler can statically reject use-after-
close and double-close bugs.

1. **`Build` postcondition — global-ref ownership**: `Build` produces a `*BuiltProgram` whose
   `globalRefs` are live JNI references. The caller receives exclusive ownership of this
   resource bundle, represented as a fractional permission:
   ```go
   //@ ensures err == nil ==>
   //@     acc(built) && built != nil && acc(built.NodeMap) && built.NodeMap != nil
   func Build(prog *silver.Program, jvm *JVM) (built *BuiltProgram, err error)
   ```

2. **`Close` precondition — ownership required, consumed on call**: `Close` must be called
   exactly once after `Verify` and `Report` have both returned. Gobra enforces this by
   consuming the permission:
   ```go
   //@ requires acc(built) && acc(built.NodeMap)
   //@ ensures  !acc(built) && !acc(built.NodeMap)
   func (built *BuiltProgram) Close()
   ```
   A second call to `Close` after the permission is consumed is a static verification error.

3. **Thread precondition on `Build`**: `Build` makes JNI calls and requires the caller to
   already hold the OS-thread lock and JVM attachment (plan 15's `ThreadAttached` predicate):
   ```go
   //@ requires acc(jvm.ThreadAttached(), 1)
   //@ ensures  acc(jvm.ThreadAttached(), 1)
   func Build(prog *silver.Program, jvm *JVM) (built *BuiltProgram, err error)
   ```

4. **NodeMap integrity**: after a successful `Build`, every entry in `built.NodeMap` is
   non-nil and corresponds to a Silver node that was registered during the build:
   ```go
   //@ ensures err == nil ==>
   //@     forall id uint64 :: id in domain(built.NodeMap) ==>
   //@         built.NodeMap[id] != nil
   ```

## Resolved Questions

**One method per Silver node type vs. grouped (resolved):** `SilverBridge.java` defines one
static method per Silver node type. Grouping (e.g., a single `makeStmt` with a discriminant
integer) would require casting on both sides and loses the type-safety that makes JNI code
auditable. One method per type is verbose (~150 lines for the full Silver AST) but each
method is trivially correct, independently testable, and easy to extend when a new Silver node
type is needed. Name each method `make{NodeType}` (e.g., `makeMethod`, `makeSeqn`,
`makeFuncApp`) for consistency.
