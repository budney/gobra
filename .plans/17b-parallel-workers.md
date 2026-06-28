# 17b — Parallel JNI Worker Pool

## Objective

Implement the `DispatchChopped` dispatch layer: given `[]*silver.Program` produced by the
chopper (plan 16b), fan them out to the N-worker pool (plan 15b), collect results, merge
them, and deduplicate errors. This recovers the parallelism that Scala Gobra achieves via
`Future.traverse` in `BackendVerifier.verify`. **Pool creation and the Silicon-aware
`runWorker` template are owned by plan 15b, not this plan.**

**Prerequisite**: plan 17 (single-worker baseline) and plan 15b (N-worker pool) must be
complete before this plan begins.

## Scope

**In scope:**
- Dispatch layer: given `[]*silver.Program` from the chopper (plan 16b), submit each
  sub-program to an available pool worker; await all results
- Result merging: success iff all sub-programs succeed; errors accumulated across all workers
- Error deduplication: shared members (fields, domains, predicate signatures) appear in
  multiple sub-programs and may produce duplicate errors; deduplicate by `(NodeInfo.File, NodeInfo.Line, NodeInfo.Col, errorID)`
- `--workers N` CLI flag (wired in plan 33); pool size defaults to `runtime.NumCPU()`. The
  actual concurrent JNI job count is `min(poolSize, len(subPrograms))` — `DispatchChopped`'s
  semaphore caps it naturally. `numSubPrograms` is not available at pool-creation time and
  cannot be used to set the pool size.
- Non-chopped path unchanged: when `--chop` is not set, pool dispatches to a single worker
  exactly as plan 17 does

**Out of scope for this plan (owned by 15b):**
- Expanding `WorkerPool` from `poolSize=1` to configurable `poolSize=N`
- The Silicon-aware `runWorker` goroutine template with `SiliconFrontendAPI` per worker

**Out of scope:**
- Carbon backend parallelism (add separately if needed after Silicon pool is stable)
- Pool resizing at runtime

## Dependencies

- [15b-worker-pool-expansion.md](15b-worker-pool-expansion.md) — N-worker pool with Silicon-aware `runWorker` template; provides `WorkerPool.Submit`
- [16-silver-jni-builder.md](16-silver-jni-builder.md) — `BuiltProgram` carries `NodeMap` and `Close`; merged by `mergeResults`
- [14-silver-ast.md](14-silver-ast.md) — `silver.Program` type (the element type of `[]*silver.Program` received from the chopper via plan 33); plan 17b does NOT call `Chop()` — that is pipeline.go's responsibility (plan 33)
- [17-silicon-backend.md](17-silicon-backend.md) — single-worker baseline; provides
  `SiliconConfig` and `VerificationResult` types reused here

## Worker Goroutine Lifecycle

The Silicon-aware worker goroutine template (with `SiliconFrontendAPI` init, `Build`, and
`Verify` loop) is defined in plan 15b. This plan consumes the pool via `WorkerPool.Submit`.
See plan 15b for the full `runWorker` implementation including `Node` field population on each
`VerificationError` for `searchInfo` DFS (plan 32).

Closing the `jobs` channel signals all workers to drain, call `silicon.stop()`, detach, and
exit. The pool `Stop()` method (plan 15b) closes the channel and waits for all workers to exit.

## Dispatch and Merge

Naive fan-out (one goroutine per sub-program) would hold all sub-programs in memory
simultaneously — a problem when there are many large sub-programs and few workers. Instead,
use a semaphore to cap in-flight programs at `poolSize` (the number of workers):

```go
func (p *WorkerPool) DispatchChopped(progs []*silver.Program) *VerificationResult {
    type indexed struct{ idx int; res *VerificationResult }
    ch := make(chan indexed, len(progs))
    // Semaphore caps the number of goroutines blocked in Submit to poolSize,
    // matching the number of JNI workers. Without this cap, all len(progs)
    // goroutines would pile up in Submit's send when workers are busy, holding
    // all sub-programs in memory simultaneously.
    sem := make(chan struct{}, p.poolSize)
    for i, prog := range progs {
        sem <- struct{}{}
        go func(i int, prog *silver.Program) {
            defer func() { <-sem }()
            ch <- indexed{i, p.Submit(prog)}
        }(i, prog)
    }
    results := make([]*VerificationResult, len(progs))
    for range progs {
        r := <-ch
        results[r.idx] = r.res
    }
    return mergeResults(results)
}
```

`mergeResults` folds results left: success if all succeed; accumulated + deduplicated errors
otherwise. Deduplication key: `(NodeInfo.File, NodeInfo.Line, NodeInfo.Col, errorID)`.

**Composite `Close()` contract (required):** Each sub-result carries its own `Close func()`
that releases the JNI global references from that sub-result's `Build()` call. `mergeResults`
must produce a merged `VerificationResult` whose `Close` transitively calls every sub-result's
`Close()`. Failure to do so leaks JNI global references. Concretely:

```go
func mergeResults(results []*VerificationResult) *VerificationResult {
    // ... merge errors and NodeMaps ...
    return &VerificationResult{
        Success: allSucceeded,
        Errors:  deduplicated,
        NodeMap: mergedNodeMap,
        Close: func() {
            for _, r := range results {
                if r.Close != nil {
                    r.Close()
                }
            }
        },
    }
}
```

The caller of `DispatchChopped` must `defer result.Close()` before calling `Report()`, exactly
as for single-program results (plan 16 / plan 17 contract).

**NodeMap merging (safe — IDs are globally unique):** Because plan 16 uses a process-wide
`globalNodeID` atomic counter (not a per-Builder counter), every Silver node across all
parallel builds gets a unique `uint64` ID. The NodeMaps from all sub-results can be merged
into a single `map[uint64]silver.Node` without key collisions:

```go
mergedNodeMap := make(map[uint64]silver.Node)
for _, r := range results {
    for id, node := range r.NodeMap {
        mergedNodeMap[id] = node
    }
}
```

This is safe because `globalNodeID` ensures no two Build() calls — even in separate goroutines
on different workers — produce the same ID.

## Z3 API Mode Caveat

`--z3APIMode` runs Z3 in-process via the Z3 Java API. Z3's in-process API has shared global
state that is not safe across concurrent calls from different threads in the same process.
If `--z3APIMode` is set and `--workers` > 1, log a warning and force `--workers 1`.
Document this constraint in `--help` for both flags.

## Deliverables

- `internal/backend/jvm/dispatch.go` — `func (p *WorkerPool) DispatchChopped(progs []*silver.Program) *VerificationResult`
  (WorkerPool expansion to configurable poolSize and Submit are delivered by plan 15b)
- `internal/backend/silicon/dedup.go` — `Deduplicate([]VerificationError) []VerificationError`
- `--workers N` CLI flag (plan 33 integration)
- Tests:
  - Verify a 3-method Silver program with `--chop --workers 3`; confirm result matches
    serial verification (plan 17 baseline)
  - Confirm deduplication removes exactly the expected duplicate errors for a program
    where two sub-programs both include the same predicate with an error
  - Confirm `--z3APIMode --workers 3` logs exactly one warning containing both flag names,
    forces `--workers 1`, and still produces a correct verification result — i.e., the
    forced fallback does not break verification. Use a trivially correct Silver program for
    the result check so the test is fast and deterministic.

## Scaling Note

Diminishing returns begin at `runtime.NumCPU()` workers: each worker holds a Silicon + Z3
subprocess; Z3 is CPU-bound. Beyond NumCPU, adding workers increases memory use and OS
scheduler overhead without improving throughput. The default cap is correct and should be
documented in `--help` for `--workers`. A value of 4–8 covers most developer machines.
