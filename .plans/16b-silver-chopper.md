# 16b — Silver Program Chopper

## Objective

Port the Silver program chopper to Go: given a `*silver.Program` (plan 14 Go structs), split
it into multiple smaller `*silver.Program` objects based on member dependency analysis. Each
sub-program contains exactly one "important member" (a method, function, or predicate being
verified) plus its transitive dependencies. Sub-programs are then merged greedily to reduce
the total count while keeping verification jobs balanced.

This is a Go port of `viper.silver.ast.utility.chopper.Chopper` in the Silver submodule.
It operates entirely on Go Silver structs — no JVM, no JNI. The parallel verification of
the resulting sub-programs is handled by plan 17b.

## Scope

**In scope:**
- Dependency graph construction over Silver Go AST members (who references whom)
- Transitive closure (reachability) to compute minimal sub-programs per important member
- Greedy merging of sub-programs subject to a penalty function and an upper bound
- Selection predicate: identify important members by Go source position (using `GoPos` on
  Silver nodes, set by the translator — plan 14)
- Penalty configuration: default values (from Scala's `Penalty.Default`) and optional
  config file (`GobraChopper.conf`) in the working directory for tuning
- Error deduplication note: errors from multiple sub-programs that verify the same shared
  member (fields, domain functions, predicate signatures) must be deduplicated by the result
  merger in plan 17b; the chopper itself does not deduplicate

**Out of scope:**
- JVM or JNI (chopper is pure Go, no JVM dependency)
- Parallel verification of the chopped programs (17b-parallel-workers.md)
- The `--isolate` selection path for now; implement default selection first

## Dependencies

- [14-silver-ast.md](14-silver-ast.md) — input: Go Silver AST types; `GoPos` field on each
  node is used by the selection predicate to identify members belonging to the input package

## Reference: Scala Implementation

- `viperserver/silicon/silver/src/main/scala/viper/silver/ast/utility/chopper/Chopper.scala`
  — three-phase algorithm (selection → per-member sub-programs → greedy merge)
- `viperserver/silicon/silver/src/main/scala/viper/silver/ast/utility/chopper/Vertices.scala`
  — vertex types for fine-grained dependency units
- `viperserver/silicon/silver/src/main/scala/viper/silver/ast/utility/chopper/Penalty.scala`
  — default penalty configs and `PenaltyConfig` struct
- `src/main/scala/viper/gobra/util/ChopperUtil.scala` — Gobra's wrapper; study
  `computeIsolateMap` for the selection predicate logic based on Go source positions

## Algorithm (Three Phases)

### Phase 1 — Selection

Identify "important members": those to be actively verified. Two modes:

- **Default (no `--isolate`)**: all methods, functions, and predicates whose `GoPos` points
  to a file in the input package being verified. Members from imported/dependency packages
  are included only as dependencies, not as important members.
- **Isolate mode (`--isolate <positions>`)**: only members whose `GoPos` falls within the
  specified file/line ranges. Implement after default mode is working.

### Phase 2 — Per-Member Sub-Programs

For each important member M, compute its minimal sub-program via transitive dependency closure.
Silver members decompose into fine-grained **vertices** at sub-member granularity:

| Vertex | Contents |
|--------|----------|
| `MethodSpec(name)` | method signature: params, pre/post only |
| `Method(name)` | method body (depends on MethodSpec) |
| `FunctionSpec(name)` | function signature + postconditions |
| `Function(name)` | function body (depends on FunctionSpec) |
| `PredicateSig(name)` | predicate signature only |
| `PredicateBody(name)` | predicate body (depends on PredicateSig) |
| `DomainAxiom(*DomainAxiom, *Domain)` | individual domain axiom (identified by pointer, not name, because names are not unique across domains) |
| `DomainFunction(funcName)` | individual domain function |
| `Field(name)` | Silver field |
| `DomainType(domainName)` | domain type declaration |
| `Always` | sentinel: every node depends on this; ensures it appears in all sub-programs |

**Dependency edges** are computed by traversing Silver AST references: a method body that
calls function F has edges `MethodBody → FunctionSpec`. A function body that folds predicate
P has edges `FunctionBody → PredicateSig`. A domain axiom using a type T has edges to
`DomainType(T)`. See Scala `Edges.scala` for the complete edge set.

The sub-program for M is assembled from all Silver members that contain at least one vertex
reachable from M's vertex in the dependency graph.

### Phase 3 — Greedy Merge

Merge sub-programs to reduce total count while respecting the upper bound. The algorithm
considers **all pairs**, not just adjacent ones. It uses a priority queue:

1. Initialize the queue with the merge penalty for every pair of distinct sub-programs.
2. Pop the pair with the lowest penalty. If the merge is still valid (neither sub-program
   has been consumed by an earlier merge), execute it:
   - Remove both sub-programs; insert the merged result.
   - Enqueue new penalties for the merged result paired with every surviving sub-program.
3. Repeat while: (a) the lowest-penalty pair has penalty ≤ 0 (free merge), **or** (b) the
   current count exceeds `Bound`.
4. Stop when: (a) no remaining pair has penalty ≤ 0, **and** (b) count ≤ `Bound`.

This is the algorithm in `Cut.mergePrograms` (Scala `Chopper.scala`): a lazy priority queue
of all pairs, with stale entries discarded on pop. The order of sub-programs entering phase 3
is declaration order of important members in the Silver program — the DFS in phase 2
processes `program.members` (filtered by the selection predicate) in order, and `notRoot`
elimination preserves that order among dominating nodes.

**Merge penalty formula** (from `Penalty.DefaultImpl.mergePenalty`):

```
penalty(L, R) = (lhsExclusive + rhsExclusive) * ceil((sharedThreshold + shared) / sharedThreshold)
```

where `lhsExclusive`, `rhsExclusive`, `shared` are the sums of vertex weights for nodes
only in L, only in R, or in both, respectively.

**Default vertex weights** (from Scala `Penalty.defaultPenaltyConfig`):

| Vertex type | Scala field | Weight |
|-------------|-------------|--------|
| `Method` (full body) | `method` | 0 |
| `MethodSpec` (spec only) | `methodSpec` | 0 |
| `Function` (full body) | `function` | 20 |
| `FunctionSpec` (sig only) | `functionSig` | 5 |
| `PredicateBody` | `predicate` | 10 |
| `PredicateSig` | `predicateSig` | 2 |
| `Field` | `field` | 1 |
| `DomainType` | `domainType` | 1 |
| `DomainFunction` | `domainFunction` | 1 |
| `DomainAxiom` | `domainAxiom` | 5 |
| `Always` (sentinel) | — | 0 |
| shared threshold | `sharedThreshold` | 50 |

`Always` is a sentinel vertex: every def-vertex and use-vertex has an edge to `Always`,
ensuring that members with no other dependencies are still included in every sub-program.
Its weight is 0 and it is never serialized to a Silver sub-program.

An optional `gobra-chopper.json` file in the working directory overrides individual weights:

```json
{
  "function": 15,
  "domainAxiom": 3,
  "sharedThreshold": 30
}
```

Keys are the Scala field names from the table above. Unknown keys are ignored.

**Default bound**: The Scala default is `bound = Some(1)` — a single merged program.
Go-Gobra follows this: without `--chop`, `bound=1`; with `--chop`, `bound=nil` (unlimited)
or `--chop-bound N` overrides it.

**PluginAwareChopper**: Gobra uses `PluginAwareChopper`, not the base `Chopper`. This
variant adds artificial edges from `Always` to any domain whose name ends in
`WellFoundedOrder` (the termination plugin's convention), ensuring those domains and their
axioms are never stripped by the chopper. The Go port must replicate this: after computing
standard edges, for each domain whose name ends in `"WellFoundedOrder"`, add the edge
`Always → DomainType(d)` plus edges from `DomainType(d)` to each of its axioms and functions.

## Deliverables

- `internal/silver/chopper.go` — `Chop(prog *Program, cfg ChopConfig) []*Program`
- `internal/silver/vertices.go` — `Vertex` type (with `Always` sentinel) and dependency
  edge extraction, including the `PluginAwareEdges` logic for `WellFoundedOrder` domains
- `internal/silver/penalty.go` — `PenaltyConfig` with defaults and `gobra-chopper.json` loader
- `ChopConfig` struct: `{Bound *int, Penalty PenaltyConfig, Selection func(*Member) bool}`
- Tests: chop a Silver program with 3 methods; verify each sub-program is self-contained;
  verify the union of sub-programs covers all members; test that greedy merging with
  bound=2 reduces a 3-sub-program result to 2; test that a `WellFoundedOrder` domain is
  present in every sub-program when termination axioms are used

## Integration Note

The chopper is called by the backend dispatcher (plan 17b) before dispatching to JNI workers.
It is not called by the JNI layer itself. The pipeline is:

```
Translator (plan 19) → *silver.Program
  → Chopper (plan 16b) → []*silver.Program
    → Pool dispatcher (plan 17b) → one JNI worker per sub-program (in parallel)
      → Results merged and deduplicated
```

## Open Questions

- The Scala Chopper requires that all quantified expressions in the input program have
  triggers. The Gobra translator should ensure this; add a debug-mode assertion in `Chop()`
  that checks for trigger-less quantifiers and panics if any are found. This catches
  translator bugs before they produce incorrect chop results.
- The `--isolate` selection mode interacts with the `--chop` bound: with `--isolate`, only
  one member is important, so bound=1 and no merging is needed. Implement this fast path.
