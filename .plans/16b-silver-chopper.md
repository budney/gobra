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
| `MethodBody(name)` | method body (depends on MethodSpec) |
| `FunctionSpec(name)` | function signature + postconditions |
| `FunctionBody(name)` | function body (depends on FunctionSpec) |
| `PredicateSig(name)` | predicate signature only |
| `PredicateBody(name)` | predicate body (depends on PredicateSig) |
| `DomainAxiom(domainName, axiomName)` | individual domain axiom |
| `DomainFunction(domainName, funcName)` | individual domain function |
| `Field(name)` | Silver field |
| `DomainType(name)` | domain type declaration |

**Dependency edges** are computed by traversing Silver AST references: a method body that
calls function F has edges `MethodBody → FunctionSpec`. A function body that folds predicate
P has edges `FunctionBody → PredicateSig`. A domain axiom using a type T has edges to
`DomainType(T)`. See Scala `Edges.scala` for the complete edge set.

The sub-program for M is assembled from all Silver members that contain at least one vertex
reachable from M's vertex in the dependency graph.

### Phase 3 — Greedy Merge

Merge sub-programs to reduce total count while respecting the upper bound:

1. Compute the merge penalty for each adjacent pair of sub-programs. Penalty = sum of
   penalty weights of members that would be duplicated by the merge.
2. Merge the pair with the lowest penalty if: (a) penalty ≤ 0 (free merge), or (b) the
   current count exceeds `Bound`.
3. Repeat until: (a) no pair has penalty ≤ 0, and (b) count ≤ `Bound` (if specified).

**Default penalty weights** (from Scala `Penalty.Default`):

| Member type | Weight |
|-------------|--------|
| method body | 10 |
| method spec | 1 |
| function body | 3 |
| function spec | 1 |
| predicate body | 2 |
| predicate spec | 1 |
| field | 0 |
| domain type | 0 |
| domain function | 0 |
| domain axiom | 0 |
| shared threshold | 10 |

An optional `GobraChopper.conf` Java properties file in the working directory overrides
individual weights using the same key names as the columns above (e.g., `method_body=5`).

## Deliverables

- `internal/silver/chopper.go` — `Chop(prog *Program, cfg ChopConfig) []*Program`
- `internal/silver/vertices.go` — `Vertex` type and dependency edge extraction
- `internal/silver/penalty.go` — `PenaltyConfig` with defaults and config-file loader
- `ChopConfig` struct: `{Bound *int, Penalty PenaltyConfig, Selection func(*Member) bool}`
- Tests: chop a Silver program with 3 methods; verify each sub-program is self-contained;
  verify the union of sub-programs covers all members; test that greedy merging with
  bound=2 reduces a 3-sub-program result to 2

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
