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
- Selection predicate: identify important members by Go source position (using `NodeInfo` on
  Silver nodes, set by the translator — plan 14)
- Penalty configuration: default values (from Scala's `Penalty.Default`) and optional
  config file (`gobra-chopper.json`) in the working directory for tuning; JSON format,
  keys matching Scala `Penalty.defaultPenaltyConfig` field names (see Greedy Merge section)
- Error deduplication note: errors from multiple sub-programs that verify the same shared
  member (fields, domain functions, predicate signatures) must be deduplicated by the result
  merger in plan 17b; the chopper itself does not deduplicate

**Out of scope:**
- JVM or JNI (chopper is pure Go, no JVM dependency)
- Parallel verification of the chopped programs (17b-parallel-workers.md)
- The `--isolate` selection path for now; implement default selection first

## Dependencies

- [14-silver-ast.md](14-silver-ast.md) — input: Go Silver AST types; `NodeInfo` struct on each
  node is used by the selection predicate to identify members belonging to the input package

## Reference: Scala Implementation

- `viperserver/silicon/silver/src/main/scala/viper/silver/ast/utility/chopper/Chopper.scala`
  — the single source file containing all chopper logic: the three-phase algorithm
  (selection → per-member sub-programs → greedy merge), plus the `Vertices`, `Edges`, and
  `Penalty` traits/objects (all defined inside `Chopper.scala` — there are no separate
  `Vertices.scala`, `Penalty.scala`, or `Edges.scala` files in this directory)
- `src/main/scala/viper/gobra/util/ChopperUtil.scala` — Gobra's wrapper; study
  `computeIsolateMap` for the selection predicate logic based on Go source positions

## Algorithm (Three Phases)

### Phase 1 — Selection

Identify "important members": those to be actively verified. Two modes:

- **Default (no `--isolate`)**: all methods, functions, and predicates whose `NodeInfo.File`
  names a file in the input package being verified. Members from imported/dependency packages
  are included only as dependencies, not as important members.
- **Isolate mode (`--isolate <positions>`)**: only members whose `NodeInfo` (File/Line/Col)
  falls within the specified file/line ranges. Implement after default mode is working.

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
| `DomainAxiom(*DomainAxiom, *Domain)` | individual domain axiom (identified by pointer pair, not name, because axiom names are not unique across domains — see implementation note below) |
| `DomainFunction(funcName)` | individual domain function |
| `Field(name)` | Silver field |
| `DomainType(domainName)` | domain type declaration |
| `Always` | sentinel: every node depends on this; ensures it appears in all sub-programs |

**Dependency edges** are computed by traversing Silver AST references: a method body that
calls function F has edges `MethodBody → FunctionSpec`. A function body that folds predicate
P has edges `FunctionBody → PredicateSig`. A domain axiom using a type T has edges to
`DomainType(T)`. See `Edges` trait inside `Chopper.scala` for the complete edge set.

**Implementation note — pointer-keyed `DomainAxiom` vertices:** `DomainAxiom` vertices are
keyed by `(*silver.DomainAxiom, *silver.Domain)` pointer pairs, not by name. The dependency
graph must therefore hold actual pointers into the Go Silver AST (plan 14), not copies. This
is safe because the Silver AST is immutable — all fields are set at construction with no
post-construction mutation (plan 14) — so pointer values remain stable for the entire chopper
lifetime. Always reference axioms as `prog.Domains[i].Axioms[j]` directly; never copy or
reconstruct Silver nodes when building the dependency graph. Using a copy would create a
different pointer, causing the vertex to be treated as a new, distinct axiom and breaking
deduplication in the merge phase.

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

**Iteration order over `Program` members (resolved):** The Go Silver `Program` struct (plan 14)
stores members in separate slices: `Methods`, `Functions`, `Predicates`, `Domains`, `Fields`.
For the chopper's phase 2 DFS, iterate in a fixed, deterministic order:
`Functions` → `Methods` → `Predicates` (matching the Scala `program.members` ordering, which
lists functions before methods before predicates). `Domains` and `Fields` are not "important
members" (they cannot be individually selected) but appear as dependency vertices. The merged
result preserves this ordering when assembling sub-programs.

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

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/silver/chopper.go` and
verified before this plan is considered complete.

**`Chop` coverage: union of sub-programs contains all important members:**
```go
//@ requires prog != nil
//@ ensures  forall m silver.Member :: cfg.Selection(m) ==>
//@             exists sp in result :: memberIn(m, sp)
//@ ensures  forall sp in result :: sp != nil
//@ decreases // pure computation over finite AST
func Chop(prog *Program, cfg ChopConfig) (result []*Program)
```

**`Chop` self-containment: each sub-program is dependency-closed:**
```go
//@ ensures forall sp in result ::
//@   forall m silver.Member :: memberIn(m, sp) ==>
//@     forall dep silver.Member :: dependsOn(m, dep) ==> memberIn(dep, sp)
```

**`buildDepGraph` termination (finite Silver AST, no cycles):**
```go
//@ decreases len(prog.Methods) + len(prog.Functions) + len(prog.Predicates)
func buildDepGraph(prog *Program) depGraph
```

## Deliverables

- `internal/silver/chopper.go` — `Chop(prog *Program, cfg ChopConfig) []*Program`
- `internal/silver/vertices.go` — `Vertex` type (with `Always` sentinel) and dependency
  edge extraction, including the `PluginAwareEdges` logic for `WellFoundedOrder` domains
- `internal/silver/penalty.go` — `PenaltyConfig` with defaults and `gobra-chopper.json` loader
- `ChopConfig` struct: `{Bound *int, Penalty PenaltyConfig, Selection func(silver.Member) bool}`
  where `silver.Member` is the interface defined in plan 14 (`*Method`, `*Function`,
  `*Predicate`, `*Field`, `*Domain` all satisfy it)
- Tests: chop a Silver program with 3 methods; verify each sub-program is self-contained;
  verify the union of sub-programs covers all members; test that greedy merging with
  bound=2 reduces a 3-sub-program result to 2; test that a `WellFoundedOrder` domain is
  present in every sub-program when termination axioms are used

## Performance Note

The greedy merge (phase 3) initializes a priority queue with all O(n²) pairs of sub-programs,
where n is the number of important members produced by phase 2. After each merge, new pairs
are enqueued for the merged result against all remaining sub-programs (O(n) new entries per
merge), so total queue operations are O(n² log n) in the worst case.

In practice n is small: it is bounded by the number of public methods, functions, and
predicates in the package being verified, not the total Silver program size. For typical Go
packages (n < 200), the algorithm is fast. For very large packages (n > 500), profiling may
show this step as a bottleneck.

**Implemented fast paths that avoid the full O(n²) setup:**
- With the default `bound=1` (no `--chop`), the merge is skipped entirely: `Chop` returns a
  single sub-program equal to the full input program. Phase 3 only runs when `bound > 1` or
  `bound=nil` (unlimited).
- If `len(progs) <= 1` after phase 2, skip phase 3 unconditionally.
- With `--isolate`, only one member is important, so phase 2 produces one sub-program;
  phase 3 is a no-op.

Add a `debug`-level log line at the start of phase 3 reporting `n` (sub-program count before
merging) so slow runs are diagnosable.

## Integration Note

The chopper is called by **`pipeline.go` (plan 33)**, not by the pool dispatcher (plan 17b).
`DispatchChopped` (plan 17b) receives an already-chopped `[]*silver.Program` and does NOT call
`Chop()` internally. The pipeline is:

```
Translator (plan 19) → *silver.Program
  → pipeline.go calls Chop (plan 16b) → []*silver.Program
    → pipeline.go calls WorkerPool.DispatchChopped (plan 17b) → parallel JNI workers
      → Results merged and deduplicated
```

## Open Questions

- The Scala Chopper requires that all quantified expressions in the input program have
  triggers. The Gobra translator should ensure this; add a debug-mode assertion in `Chop()`
  that checks for trigger-less quantifiers and panics if any are found. This catches
  translator bugs before they produce incorrect chop results.
- The `--isolate` selection mode interacts with the `--chop` bound: with `--isolate`, only
  one member is important, so bound=1 and no merging is needed. Implement this fast path.
