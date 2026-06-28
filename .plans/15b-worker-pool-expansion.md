# 15b — Parallel JNI Worker Pool Expansion

## Objective

Expand the JNI `WorkerPool` (plan 15) from a single goroutine to a pool of N workers. Each
worker is locked to its own OS thread via `runtime.LockOSThread()`, attached to the JVM, and
holds its own `SiliconFrontendAPI` instance. This plan owns all Silicon-aware worker
infrastructure; plan 15 owns only the JVM-level plumbing.

**Prerequisite**: plan 17 (Silicon Backend) must be complete before this plan begins, because
the worker goroutine template initialises a `SiliconFrontendAPI` instance (defined in plan 17).

## Scope

**In scope:**
- Expand `WorkerPool` (plan 15) from `poolSize=1` to configurable `poolSize=N`
- Full Silicon-aware worker goroutine template (see below): each worker initialises its own
  `SiliconFrontendAPI` at startup and reuses it across jobs
- `WorkerPool.Submit(prog *silver.Program) *VerificationResult` sends a job to the pool and
  blocks until a worker completes the full Build → Verify → result cycle
- Z3 API mode caveat: if `--z3APIMode` is set and `--workers > 1`, log a warning and force
  `--workers 1`

**Out of scope:**
- `DispatchChopped` fan-out and result merging (plan 17b)
- JVM startup and thread attach/detach lifecycle (plan 15)
- Silver AST construction (plan 16)

## Dependencies

- [15-jni-setup.md](15-jni-setup.md) — `WorkerPool` type skeleton, JVM singleton, thread
  lifecycle (`AttachCurrentThread` / `DetachCurrentThread`)
- [16-silver-jni-builder.md](16-silver-jni-builder.md) — `silver.NewBuilder`, `BuiltProgram`
- [17-silicon-backend.md](17-silicon-backend.md) — `SiliconFrontendAPI`, `SiliconConfig`,
  `VerificationResult` types

## Worker Goroutine Template

Each of the N workers follows this pattern (moved from plan 15 baseline, now fully defined here):

```go
func runWorker(jvm *jvm.JVM, cfg SiliconConfig, jobs <-chan workerJob) {
    runtime.LockOSThread()
    jvm.AttachCurrentThread()
    defer jvm.DetachCurrentThread()

    silicon := newSiliconFrontendAPI(jvm)
    silicon.initialize(cfg.Args)
    defer silicon.stop()

    builder := silver.NewBuilder(jvm)

    for job := range jobs {
        built, err := builder.Build(job.prog, jvm)
        if err != nil {
            job.result <- &VerificationResult{Err: err}
            continue
        }
        result := silicon.verify(built.JavaObject)
        result.NodeMap = built.NodeMap
        result.Close = built.Close
        // Populate Node field on each error for searchInfo DFS (plan 32)
        for i := range result.Errors {
            if id := result.Errors[i].Pos.NodeID; id != 0 {
                if node, ok := built.NodeMap[id]; ok {
                    result.Errors[i].Node = node
                }
            }
        }
        job.result <- result
    }
}
```

The `workerJob` struct carries `prog *silver.Program` and a `result chan *VerificationResult`.

## `Submit` Contract

`Submit(prog *silver.Program) *VerificationResult` sends the program to the next available
pool worker and blocks until the worker completes the Build → Verify cycle. The returned
`*VerificationResult` carries a `Close func()` set by the worker to `built.Close`. The caller
must `defer result.Close()` before calling `Report()`.

## Z3 API Mode Caveat

`--z3APIMode` runs Z3 in-process via the Z3 Java API. Z3's in-process API has shared global
state that is not safe across concurrent calls from different threads in the same process.
If `--z3APIMode` is set and `--workers > 1`, log a warning and force `--workers 1`.
Document this constraint in `--help` for both flags.

## Deliverables

- Updated `internal/backend/jvm/jvm.go` — `WorkerPool` expanded to configurable `poolSize`;
  `NewPool(jvm *JVM, poolSize int, cfg SiliconConfig) *WorkerPool` starts N worker goroutines
  at construction time.
- Tests:
  - Start a pool with `poolSize=3`; submit 5 sequential jobs; confirm all return results.
  - Confirm `--z3APIMode --workers 3` logs exactly one warning and forces `--workers 1`.

## Scaling Note

Diminishing returns begin at `runtime.NumCPU()` workers: each worker holds a Silicon + Z3
subprocess; Z3 is CPU-bound. Beyond NumCPU, adding workers increases memory use and OS
scheduler overhead without improving throughput. The default cap is correct and should be
documented in `--help` for `--workers`. A value of 4–8 covers most developer machines.
