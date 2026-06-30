# 16 — Silver Protobuf Serializer

## Objective

Walk the Go Silver AST (plan 14) and serialize it to a `proto.SilverProgram` Protobuf message
that `SilverServer` can deserialize into a real `silver.ast.Program` on the Scala side. Assign
stable `uint64` node IDs during serialization and populate the `NodeMap` for error backtranslation.

## Scope

**In scope:**
- `Serialize(prog *silver.Program) (*SerializedProgram, error)` — walks the Go Silver AST
  and produces a Protobuf message tree
- Assign a monotonically-increasing `uint64 node_id` field (defined in `silver.proto`) to
  every Silver node during serialization; the Scala `SilverServer` carries these IDs through
  to `VerifyResponse.Error.node_id`
- `NodeMap` population: `map[uint64]silver.Node` built during serialization; lives entirely
  in Go memory and is GC'd normally after the result is consumed
- `SerializedProgram` struct: carries the `*proto.SilverProgram` Protobuf message and the
  `NodeMap`; no `Close()` method (no JNI references to free)
- `silver.proto` schema: defines `SilverProgram`, `SilverMethod`, `SilverExpr`, etc. — one
  proto message per Silver node family. `silver.proto` and its generated Go Protobuf bindings
  (`gobra/internal/proto`) are owned by plan 15 (the `SilverServer` artifact). This plan
  consumes `internal/proto/` types from plan 15's deliverables.

**Out of scope:**
- Subprocess lifecycle (plan 15)
- Defining the Go Silver AST types (plan 14)
- Calling Silicon's verify method (plan 17)
- `SilverServer.scala` and `silver.proto` source (owned by the `SilverServer` registry entry)

## Dependencies

- [14-silver-ast.md](14-silver-ast.md) — input: Go Silver AST types
- [15-jni-setup.md](15-jni-setup.md) — `SubprocessConfig` and the `SilverServer` fat JAR
  artifact (generates the `silver.proto` Go bindings consumed here)

## Approach: Direct Protobuf Serialization

The serializer walks the Go Silver AST in a single pass, constructing the Protobuf message
tree bottom-up. For each Silver node it:

1. Recursively serializes child nodes first.
2. Atomically increments the process-wide node ID counter and assigns `node_id` to this node's
   proto message.
3. Embeds `NodeInfo` fields (`file`, `line`, `col`, `tag`) directly as Protobuf fields on the
   node message — no `AnnotationInfo` chains, no Java-side embedding required.
4. Records `nodeMap[id] = goSilverNode`.

```go
// globalNodeID is a process-wide atomic counter for Silver node IDs.
// A global counter (not per-serializer) ensures IDs are unique across
// all parallel serializations (plan 17b).
var globalNodeID atomic.Uint64

type Serializer struct {
    nodeMap map[uint64]silver.Node
}

func Serialize(prog *silver.Program) (*SerializedProgram, error) {
    s := &Serializer{nodeMap: make(map[uint64]silver.Node)}
    msg, err := s.serializeProgram(prog)
    if err != nil {
        return nil, err
    }
    return &SerializedProgram{Program: msg, NodeMap: s.nodeMap}, nil
}

type SerializedProgram struct {
    Program *proto.SilverProgram
    NodeMap map[uint64]silver.Node
}
// No Close() method — NodeMap is plain Go memory, GC'd automatically.
```

**Eliminated artifacts** (compared to the former JNI builder):
- No `SilverBridge.java`
- No `Makefile` JAR compilation target
- No `//go:embed SilverBridge.jar`
- No `BuiltProgram.globalRefs []jobject`
- No `BuiltProgram.Close()` / `DeleteGlobalRef` lifecycle
- No `jnigi.NewGlobalRef` calls
- No dual NodeInfo storage (Go-side field + Java-side `AnnotationInfo`): NodeInfo is embedded
  directly in the Protobuf message; the Go-side `NodeInfo` field on each Silver struct is
  the single source of truth

## Node Identity

```go
// globalNodeID: unique uint64 per serialized Silver node across all concurrent workers.
// Counter starts at 1; ID 0 is reserved for synthetic nodes (NodeID == 0 → skip NodeMap).
```

**Serialization-time registration (per node):**
1. Serialize all child nodes first (bottom-up).
2. `id := globalNodeID.Add(1)` — fetch next ID.
3. Set `msg.NodeId = id` in the Protobuf message.
4. Set `msg.NodeFile`, `msg.NodeLine`, `msg.NodeCol`, `msg.NodeTag` from `goNode.NodeInfo()`.
5. `nodeMap[id] = goSilverNode`.

**Synthetic nodes** carry `NodeID = 0` in their `NodeInfo`; the serializer sets `msg.NodeId = 0`
and skips the `nodeMap` registration for them.

**Lookup (by the worker, plan 17):**
When `SilverServer` returns a `VerifyResponse.Error`, it carries `node_id` (read from the
proto message's field that Silicon passed through). The Go worker looks up
`NodeMap[error.NodeId]` to retrieve the Go Silver struct for `SearchInfo` DFS (plan 32).
The primary position (`File`, `Line`, `Col`, `Tag`) comes directly from
`error.NodeFile`/`error.NodeLine`/etc. on the `VerifyResponse.Error` proto message — no
nodeMap lookup needed for position, only for the `Children()` DFS fallback.

## `silver.proto` Schema (excerpt)

```proto
syntax = "proto3";
package silver;

message SilverProgram {
    repeated SilverDomain  domains    = 1;
    repeated SilverField   fields     = 2;
    repeated SilverFunction functions  = 3;
    repeated SilverPredicate predicates = 4;
    repeated SilverMethod  methods    = 5;
}

message SilverMethod {
    uint64 node_id   = 1;
    string node_file = 2;
    int32  node_line = 3;
    int32  node_col  = 4;
    string node_tag  = 5;
    string name      = 6;
    // ... params, returns, pres, posts, body ...
}

// Every Silver node message carries node_id, node_file, node_line, node_col, node_tag.
// SilverServer reads these fields and propagates node_id through error responses.
```

## Deliverables

- `internal/backend/silver/serializer.go` — `Serialize(prog *silver.Program) (*SerializedProgram, error)`,
  `SerializedProgram` struct, `globalNodeID` atomic counter
- `internal/proto/` — consumed from plan 15's deliverables (not owned here; see plan 15)
- Tests:
  - Serialize a minimal Silver program (one method, one statement); confirm `NodeMap` contains
    an entry for the method node; confirm `NodeMap[id].NodeInfo().File` matches the info used
    during construction
  - Confirm that a second call to `Serialize` on the same program produces different node IDs
    (monotonically increasing counter is global, not per-call)

## Version Coupling Note

`silver.proto` must stay in sync with the Silver submodule version bundled in `SilverServer`.
When the `viperserver/` submodule is updated, regenerate the Protobuf bindings and rebuild
the `SilverServer` fat JAR. Document this in the repo README.

## Verification Specifications (C9)

Plan 16 serializes the Go Silver AST to a Protobuf message with stable node IDs. The C9
specs focus on node ID uniqueness and NodeMap integrity. There are no thread preconditions —
serialization is goroutine-safe (each `Serializer` instance is local to one goroutine; the
only shared state is `globalNodeID`, which is protected by `atomic.Uint64`).

1. **`Serialize` postcondition — NodeMap integrity**: after a successful call, every key in
   `NodeMap` is positive (≥ 1) and maps to a non-nil Silver node:
   ```go
   //@ ensures err == nil ==>
   //@     result != nil &&
   //@     forall id uint64 :: id in domain(result.NodeMap) ==>
   //@         id >= 1 && result.NodeMap[id] != nil
   func Serialize(prog *silver.Program) (result *SerializedProgram, err error)
   ```

2. **Node ID uniqueness** — no two nodes in the same `SerializedProgram` share an ID:
   ```go
   //@ ensures err == nil ==>
   //@     forall id1, id2 uint64 ::
   //@         id1 in domain(result.NodeMap) && id2 in domain(result.NodeMap) &&
   //@         id1 != id2 ==>
   //@             result.NodeMap[id1] != result.NodeMap[id2]
   ```

3. **No `Close` requirement** — `SerializedProgram` carries no resources that require
   explicit release; callers may let it go out of scope and rely on the GC:
   ```go
   // C9: N/A for Close — SerializedProgram has no finalizer and no JNI references.
   // NodeMap is a plain Go map; it is GC'd when the last reference drops.
   ```

4. **Termination** — `serializeProgram` terminates because the Silver AST is a finite DAG
   (guaranteed by plan 14's `acyclicExp` predicate):
   ```go
   //@ requires acyclicExp(prog, set[silver.Node]{})
   //@ decreases prog.NodeCount()
   func (s *Serializer) serializeProgram(prog *silver.Program) (msg *proto.SilverProgram, err error)
   ```
