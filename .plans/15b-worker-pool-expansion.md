# 15b — Goroutine Worker Pool

## Objective

Expand the `WorkerPool` (plan 15) from a skeleton to a fully operational pool of N goroutine
workers. Each worker owns one `SilverServer` subprocess and one `SiliconFrontendAPI` instance
and processes jobs from the shared channel. No OS-thread pinning, no JNI, no CGo.

**Prerequisite**: plan 17 (Silicon Backend) must be complete before this plan begins, because
the worker goroutine template initialises a `SiliconFrontendAPI` instance (defined in plan 17).

## Scope

**In scope:**
- Expand `WorkerPool` (plan 15) from skeleton to configurable `poolSize=N`
- Full Silicon-aware worker goroutine template (see below): each worker forks its own
  `SilverServer` subprocess at startup via `subprocess.Start()` (plan 15), creates its own
  `SiliconFrontendAPI` instance, and reuses both across all jobs
- Worker health monitoring: if `(*Backend).Ping()` fails mid-job, the worker calls `Stop()`
  on the dead subprocess and `Start()` to restart it, then retries the job
- `WorkerPool.Submit(prog *silver.Program) *backend.VerificationResult` sends a job to the pool and
  blocks until a worker completes the full Serialize → Verify cycle

**Out of scope:**
- `DispatchChopped` fan-out and result merging (plan 17b)
- Subprocess lifecycle primitives (plan 15)
- Silver Protobuf serialization (plan 16)

## Dependencies

- [15-jni-setup.md](15-jni-setup.md) — `WorkerPool` struct skeleton; `SubprocessConfig`;
  `subprocess.Start()` / `(*Backend).Stop()` / `(*Backend).Ping()`
- [16-silver-jni-builder.md](16-silver-jni-builder.md) — `Serialize(prog *silver.Program)
  (*SerializedProgram, error)` used by the worker goroutine; `SerializedProgram.Program`
  (`*proto.SilverProgram`) is passed to `instance.Verify`; `SerializedProgram.NodeMap`
  is used to populate `VerificationError.Node` for searchInfo DFS
- [17-silicon-backend.md](17-silicon-backend.md) — `SiliconFrontendAPI` concrete type (for
  implementing `backend.SiliconInstance`); behavioral reference for the verify call sequence.
  **Import note**: plan 15b does NOT import `internal/backend/silicon` directly. The
  `SiliconConfig` and `SiliconInstance` types come from the parent package
  `gobra/internal/backend` (see `internal/backend/types.go`, owned by plan 15). The concrete
  `SiliconFrontendAPI` constructor is injected via the `backend.SiliconConfig.NewInstance`
  factory field — the caller (plan 33, CLI wiring) binds `silicon.NewSiliconFrontendAPI` to
  that field, keeping the `subprocess` package free of a direct `silicon` import.

## Worker Goroutine Template

Each of the N workers follows this pattern:

```go
func runWorker(cfg SubprocessConfig, instance backend.SiliconInstance, args []string,
               jobs <-chan workerJob) {
    // Fork the SilverServer subprocess for this worker.
    // Call Start directly (no package prefix — runWorker lives in package subprocess).
    sub, err := Start(cfg)
    if err != nil {
        // Drain the channel returning errors until it is closed.
        for job := range jobs {
            job.result <- &backend.VerificationResult{Err: err}
        }
        return
    }
    defer sub.Stop()

    instance.Initialize(args)
    defer instance.Stop()

    for job := range jobs {
        // Health check: ping before each job; restart subprocess on failure.
        if err := sub.Ping(); err != nil {
            sub.Stop()
            sub, err = Start(cfg)
            if err != nil {
                // Subprocess cannot be restarted; return error and drain.
                job.result <- &backend.VerificationResult{Err: err}
                for remaining := range jobs {
                    remaining.result <- &backend.VerificationResult{Err: err}
                }
                return
            }
        }
        // silverser is an import alias for "gobra/internal/backend/silver" to avoid
        // conflict with "gobra/internal/silver" (both default package name "silver").
        serialized, err := silverser.Serialize(job.prog)
        if err != nil {
            job.result <- &backend.VerificationResult{Err: err}
            continue
        }
        result := instance.Verify(serialized.Program) // *proto.SilverProgram
        // Populate Node field on each error for searchInfo DFS (plan 32)
        for i := range result.Errors {
            if id := result.Errors[i].Pos.NodeID; id != 0 {
                if node, ok := serialized.NodeMap[id]; ok {
                    result.Errors[i].Node = node
                }
            }
        }
        job.result <- result
    }
}
```

`backend.SiliconInstance` is an interface defined in `internal/backend/types.go` (package
`backend`, owned by plan 15). The concrete `silicon.SiliconFrontendAPI` implements this
interface. Each worker receives its own pre-created instance via `cfg.NewInstance()` (called
during pool construction in `NewPool`) and the startup args via the `args []string` parameter.

There is **no `runtime.LockOSThread()`** call. Workers are plain goroutines. Subprocess
communication is via gRPC over a local TCP socket — no OS thread constraints apply.

The `workerJob` struct carries `prog *silver.Program` and a `result chan *backend.VerificationResult`.
Both types are owned by their respective plans (14 and 15); plan 15b uses them from their
canonical import paths (`gobra/internal/silver` and `gobra/internal/backend`).

## `Submit` Contract

`Submit(prog *silver.Program) *backend.VerificationResult` sends the program to the next
available pool worker and blocks until the worker completes the Serialize → Verify cycle.
The returned `*backend.VerificationResult` is always non-nil. **There is no `Close func()`
on the result** — the serialized Protobuf and the Go `NodeMap` are plain Go values GC'd
automatically after the caller is done with them.

## Deliverables

- Updated `internal/backend/subprocess/subprocess.go` — `WorkerPool` expanded to
  configurable `poolSize`; `NewPool(poolSize int, cfg backend.SiliconConfig) *WorkerPool`
  starts N worker goroutines at construction time. `NewPool` calls `cfg.NewInstance()` once
  per worker and `Start(cfg)` once per worker to create the dedicated subprocess (unqualified:
  `NewPool` is inside package `subprocess`, so no package prefix is needed).
  **`runWorkerSkeleton` removal**: plan 15 delivers a `runWorkerSkeleton` stub. Plan 15b
  delivers the full `runWorker` that supersedes it. When implementing plan 15b, **delete
  `runWorkerSkeleton`** — only `runWorker` must remain.
- Tests:
  - Start a pool with `poolSize=3`; submit 5 sequential jobs; confirm all return non-nil results.

## Verification Specifications (C9)

Plan 15b's worker goroutines are plain goroutines communicating with subprocesses via gRPC.
There are no JNI thread preconditions. The C9 specs focus on goroutine-safety and result
validity.

1. **Submit postcondition** — `Submit` blocks until a result is available; the returned
   pointer is always non-nil:
   ```go
   //@ ensures result != nil
   func (p *WorkerPool) Submit(prog *silver.Program) (result *backend.VerificationResult)
   ```

2. **NewPool constructor** — `NewPool` guarantees that exactly `poolSize` worker goroutines
   are running before it returns:
   ```go
   //@ requires poolSize >= 1
   //@ ensures  acc(pool) && pool != nil
   func NewPool(poolSize int, cfg backend.SiliconConfig) (pool *WorkerPool)
   ```

3. **Worker subprocess health** — each worker goroutine maintains a ghost invariant that its
   subprocess is alive (Ping succeeds) before processing any job:
   ```go
   //@ invariant sub.Running()
   ```
   If `Ping()` fails mid-run (ghost invariant broken), the worker restarts the subprocess via
   `Stop()`/`Start()` before processing the next job. This is expressed as a loop invariant
   on the worker's `for job := range jobs` loop; `sub` is the `*Backend` variable in the
   worker template (renamed from `backend` to avoid shadowing the `backend` package).

## Scaling Note

Diminishing returns begin at `runtime.NumCPU()` workers: each worker holds a Silicon + Z3
subprocess; Z3 is CPU-bound. Beyond NumCPU, adding workers increases memory use and OS
scheduler overhead without improving throughput. The default cap is correct and should be
documented in `--help` for `--workers`. A value of 4–8 covers most developer machines.
