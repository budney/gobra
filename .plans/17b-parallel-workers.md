# 17b — Parallel JNI Worker Pool

## Objective

Expand the JNI worker from a single goroutine (plan 15) to a pool of N workers. Each worker
is locked to its own OS thread via `runtime.LockOSThread()`, attached to the JVM, and holds
its own `SiliconFrontendAPI` instance. The `--chop` path dispatches chopped sub-programs to
pool workers in parallel, recovering the true parallelism that Scala Gobra achieves via
`Future.traverse` in `BackendVerifier.verify`.

**Prerequisite**: plan 17 (single-worker Silicon verification) must be correct and passing
regression tests before this plan begins. This plan is an expansion of a working baseline,
not a concurrent development.

## Scope

**In scope:**
- Expand `WorkerPool` (plan 15) from `poolSize=1` to configurable `poolSize=N`
- Dispatch layer: given `[]*silver.Program` from the chopper (plan 16b), submit each
  sub-program to an available pool worker; await all results
- Each pool worker initializes its own `SiliconFrontendAPI` at pool startup and reuses it
  across jobs (matching plan 17's "warm path" design; do not init/stop Silicon per job)
- Result merging: success iff all sub-programs succeed; errors accumulated across all workers
- Error deduplication: shared members (fields, domains, predicate signatures) appear in
  multiple sub-programs and may produce duplicate errors; deduplicate by `(GoPos, errorID)`
- `--workers N` CLI flag (wired in plan 33); default = `min(runtime.NumCPU(), numSubPrograms)`
- Non-chopped path unchanged: when `--chop` is not set, pool dispatches to a single worker
  exactly as plan 17 does

**Out of scope:**
- Carbon backend parallelism (add separately if needed after Silicon pool is stable)
- Pool resizing at runtime

## Dependencies

- [15-jni-setup.md](15-jni-setup.md) — pool-ready `WorkerPool` API (pool-size=1 baseline)
- [16b-silver-chopper.md](16b-silver-chopper.md) — produces `[]*silver.Program` to dispatch
- [17-silicon-backend.md](17-silicon-backend.md) — single-worker baseline; provides
  `SiliconConfig` and `VerificationResult` types reused here

## Worker Goroutine Lifecycle

Each of the N workers follows the same pattern:

```go
func runWorker(jvm *JVM, cfg SiliconConfig, jobs <-chan verifyJob) {
    runtime.LockOSThread()
    jvm.AttachCurrentThread()
    defer jvm.DetachCurrentThread()

    silicon := newSiliconFrontendAPI(jvm)
    silicon.initialize(cfg.Args)
    defer silicon.stop()

    for job := range jobs {
        result := silicon.verify(job.prog)
        job.result <- result
    }
}
```

Closing the `jobs` channel signals all workers to drain, call `silicon.stop()`, detach, and
exit. The pool `Stop()` method closes the channel and waits for all workers to exit.

## Dispatch and Merge

```go
func (p *WorkerPool) DispatchChopped(progs []*silver.Program) *VerificationResult {
    type indexed struct{ idx int; res *VerificationResult }
    ch := make(chan indexed, len(progs))
    for i, prog := range progs {
        go func(i int, prog *silver.Program) {
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
otherwise. Deduplication key: `(GoPos.File, GoPos.Line, GoPos.Col, errorID)`.

## Z3 API Mode Caveat

`--z3APIMode` runs Z3 in-process via the Z3 Java API. Z3's in-process API has shared global
state that is not safe across concurrent calls from different threads in the same process.
If `--z3APIMode` is set and `--workers` > 1, log a warning and force `--workers 1`.
Document this constraint in `--help` for both flags.

## Deliverables

- Updated `internal/backend/jvm/jvm.go` — `WorkerPool` expanded to configurable `poolSize`;
  `Submit(prog *silver.Program) *VerificationResult` dispatches to next available worker
- `internal/backend/silicon/dispatch.go` — `DispatchChopped([]*silver.Program, *WorkerPool)`
- `internal/backend/silicon/dedup.go` — `Deduplicate([]VerificationError) []VerificationError`
- `--workers N` CLI flag (plan 33 integration)
- Tests:
  - Verify a 3-method Silver program with `--chop --workers 3`; confirm result matches
    serial verification (plan 17 baseline)
  - Confirm deduplication removes exactly the expected duplicate errors for a program
    where two sub-programs both include the same predicate with an error
  - Confirm `--z3APIMode --workers 3` falls back to `--workers 1` with a warning

## Scaling Note

Diminishing returns begin at `runtime.NumCPU()` workers: each worker holds a Silicon + Z3
subprocess; Z3 is CPU-bound. Beyond NumCPU, adding workers increases memory use and OS
scheduler overhead without improving throughput. The default cap is correct and should be
documented in `--help` for `--workers`. A value of 4–8 covers most developer machines.
