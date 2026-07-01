# 17b — Parallel Goroutine Workers

## Objective

Implement the `DispatchChopped` dispatch layer: given `[]*silver.Program` produced by the
chopper (plan 16b), fan them out to the N-worker goroutine pool (plan 15b), collect results,
merge them, and deduplicate errors. **Pool creation and the Silicon-aware `runWorker` template
are owned by plan 15b, not this plan.**

**Prerequisite**: plan 17 (single-worker baseline) and plan 15b (N-worker pool) must be
complete before this plan begins.

## Scope

**In scope:**
- Dispatch layer: given `[]*silver.Program` from the chopper (plan 16b), submit each
  sub-program to an available pool worker; await all results
- Result merging: success iff all sub-programs succeed; errors accumulated across all workers
- Error deduplication: shared members (fields, domains, predicate signatures) appear in
  multiple sub-programs and may produce duplicate errors; deduplicate by
  `(NodeInfo.File, NodeInfo.Line, NodeInfo.Col, errorID)`
- `--workers N` CLI flag (wired in plan 33); pool size defaults to `runtime.NumCPU()`. The
  actual concurrent job count is `min(poolSize, len(subPrograms))` — `DispatchChopped`'s
  semaphore caps it naturally.
- Non-chopped path unchanged: when `--chop` is not set, pool dispatches to a single worker
  exactly as plan 17 does

**Out of scope for this plan (owned by 15b):**
- Expanding `WorkerPool` from `poolSize=1` to configurable `poolSize=N`
- The Silicon-aware `runWorker` goroutine template with subprocess + `SiliconFrontendAPI`
  per worker

**Out of scope:**
- Carbon backend parallelism (add separately if needed after Silicon pool is stable)
- Pool resizing at runtime

## Dependencies

- [15b-worker-pool-expansion.md](15b-worker-pool-expansion.md) — N-worker pool with
  Silicon-aware `runWorker` template; provides `WorkerPool.Submit`
- [15-jni-setup.md](15-jni-setup.md) — `VerificationResult` and `VerificationResult.NodeMap`
  (plan 15's type) are what `mergeResults` merges; `Deduplicate` operates on `[]VerificationError`
  also from plan 15
- [14-silver-ast.md](14-silver-ast.md) — `silver.Program` type (element type of
  `[]*silver.Program` from the chopper); `silver.Node` type in merged `NodeMap`
- [17-silicon-backend.md](17-silicon-backend.md) — single-worker baseline; provides
  `SiliconConfig` and `VerificationResult` types reused here

## Worker Goroutine Lifecycle

The Silicon-aware worker goroutine template (subprocess + `SiliconFrontendAPI` init + Verify
loop) is defined in plan 15b. This plan consumes the pool via `WorkerPool.Submit`. Closing
the `jobs` channel signals all workers to drain, call `silicon.Stop()`, and exit. The pool
`Stop()` method (plan 15b) closes the channel and waits for all workers to exit.

## Dispatch and Merge

Use a semaphore to cap in-flight programs at `poolSize`:

```go
func (p *WorkerPool) DispatchChopped(progs []*silver.Program) *backend.VerificationResult {
    type indexed struct{ idx int; res *backend.VerificationResult }
    ch := make(chan indexed, len(progs))
    sem := make(chan struct{}, p.poolSize)
    for i, prog := range progs {
        sem <- struct{}{}
        go func(i int, prog *silver.Program) {
            defer func() { <-sem }()
            ch <- indexed{i, p.Submit(prog)}
        }(i, prog)
    }
    results := make([]*backend.VerificationResult, len(progs))
    for range progs {
        r := <-ch
        results[r.idx] = r.res
    }
    return mergeResults(results)
}
```

`mergeResults` folds results: success if all succeed; accumulated + deduplicated errors
otherwise.

**No composite `Close()` contract**: `VerificationResult` has no `Close` field in the gRPC
design (there are no JNI global references to free). `mergeResults` simply merges the `NodeMap`
maps from all sub-results and returns a single merged result. Callers do not need to call
`Close()`.

**NodeMap merging (safe — IDs are globally unique):** Because plan 16 uses a process-wide
`globalNodeID` atomic counter, every Silver node across all parallel serializations gets a
unique `uint64` ID. The `NodeMap`s from all sub-results can be merged into a single
`map[uint64]silver.Node` without key collisions.

```go
func mergeResults(results []*backend.VerificationResult) *backend.VerificationResult {
    merged := make(map[uint64]silver.Node)
    var allErrors []backend.VerificationError
    allSucceeded := true
    for _, r := range results {
        if !r.Success {
            allSucceeded = false
            allErrors = append(allErrors, r.Errors...)
        }
        for id, node := range r.NodeMap {
            merged[id] = node
        }
    }
    return &backend.VerificationResult{
        Success: allSucceeded,
        Errors:  backend.Deduplicate(allErrors),
        NodeMap: merged,
    }
}
```

## Deliverables

- `internal/backend/subprocess/dispatch.go` — `func (p *WorkerPool) DispatchChopped(progs []*silver.Program) *VerificationResult`
  (WorkerPool expansion to configurable poolSize and Submit are delivered by plan 15b)
- `internal/backend/dedup.go` — `Deduplicate([]VerificationError) []VerificationError`
  (package `backend`, not `silicon` — placing it in `silicon` would force a circular import)
- `--workers N` CLI flag (plan 33 integration)
- Tests:
  - Verify a 3-method Silver program with `--chop --workers 3`; confirm result matches
    serial verification (plan 17 baseline)
  - Confirm deduplication removes exactly the expected duplicate errors for a program where
    two sub-programs both include the same predicate with an error

## Verification Specifications (C9)

Plan 17b's dispatch and merge functions are pipeline-critical and must carry Gobra annotations
written into `internal/backend/subprocess/dispatch.go` and `internal/backend/dedup.go`.

1. **`DispatchChopped` postcondition** — result is always non-nil; `Success` iff all
   sub-results succeed; `Errors` is nil iff `Success`:
   ```go
   //@ requires p != nil && len(progs) > 0
   //@ ensures  result != nil
   //@ ensures  result.Success == (result.Errors == nil || len(result.Errors) == 0)
   //@ ensures  !result.Success ==> result.Errors != nil && len(result.Errors) > 0
   func (p *WorkerPool) DispatchChopped(progs []*silver.Program) (result *backend.VerificationResult)
   ```
   The full per-sub-result aggregation semantics (`result.Success == forall i :: subResults[i].Success`)
   cannot be expressed in a postcondition without a ghost return for `subResults`; the
   `result.Errors`-based form above is equivalent and statically verifiable.

2. **`mergeResults` postcondition** — the returned result is always non-nil and its `NodeMap`
   contains all entries from all sub-results:
   ```go
   //@ requires len(results) > 0
   //@ requires forall i int :: { results[i] } 0 <= i < len(results) ==> results[i] != nil
   //@ ensures  result != nil
   func mergeResults(results []*backend.VerificationResult) (result *backend.VerificationResult)
   ```
   The `Close != nil` postcondition from the former JNI design is dropped — there is no
   `Close` field on `VerificationResult` in the gRPC design.

3. **`Deduplicate` purity contract** — result is a sub-multiset of the input with no two
   entries sharing the same `(File, Line, Col, FullID)` key:
   ```go
   //@ ensures len(result) <= len(errs)
   //@ ensures forall i, j int :: { result[i], result[j] }
   //@     0 <= i < j < len(result) ==>
   //@     !(result[i].Pos.File  == result[j].Pos.File  &&
   //@       result[i].Pos.Line  == result[j].Pos.Line  &&
   //@       result[i].Pos.Col   == result[j].Pos.Col   &&
   //@       result[i].FullID    == result[j].FullID)
   func Deduplicate(errs []backend.VerificationError) (result []backend.VerificationError)
   ```

## Scaling Note

Diminishing returns begin at `runtime.NumCPU()` workers: each worker holds one `SilverServer`
subprocess + one Z3 subprocess (per D15). Beyond NumCPU, adding workers increases memory and
OS scheduler overhead without improving throughput. The default cap is correct and should be
documented in `--help` for `--workers`. A value of 4–8 covers most developer machines.
