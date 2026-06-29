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

- [15-jni-setup.md](15-jni-setup.md) — `WorkerPool` struct skeleton, JVM singleton, thread
  lifecycle (`AttachCurrentThread` / `DetachCurrentThread`)
- [16-silver-jni-builder.md](16-silver-jni-builder.md) — `silver.NewBuilder`, `BuiltProgram`
- [17-silicon-backend.md](17-silicon-backend.md) — `SiliconFrontendAPI` concrete type (for
  implementing `backend.SiliconInstance`); behavioral reference for the verify call sequence.
  **Import note**: plan 15b does NOT import `internal/backend/silicon` directly. The
  `SiliconConfig` and `SiliconInstance` types come from the parent package
  `gobra/internal/backend` (see `internal/backend/types.go`, owned by plan 15). The concrete
  `SiliconFrontendAPI` constructor is injected via the `backend.SiliconConfig.NewInstance`
  factory field — the caller (plan 33, CLI wiring) binds `silicon.NewSiliconFrontendAPI` to
  that field, keeping the `jvm` package free of a direct `silicon` import.

## Worker Goroutine Template

Each of the N workers follows this pattern (moved from plan 15 baseline, now fully defined here):

```go
func runWorker(jvm *JVM, instance backend.SiliconInstance, args []string, jobs <-chan workerJob) {
    runtime.LockOSThread()
    jvm.AttachCurrentThread()
    defer jvm.DetachCurrentThread()

    instance.Initialize(args)
    defer instance.Stop()

    builder := silver.NewBuilder(jvm)

    for job := range jobs {
        built, err := builder.Build(job.prog, jvm)
        if err != nil {
            job.result <- &backend.VerificationResult{Err: err}
            continue
        }
        result := instance.Verify(built.JavaObject)
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

`backend.SiliconInstance` is an interface defined in `internal/backend/types.go` (package
`backend`, owned by plan 15). The concrete `silicon.SiliconFrontendAPI` implements this
interface. `SiliconInstance.Verify` takes `jobject` (jnigi's JNI object reference type),
matching `BuiltProgram.JavaObject jobject` from plan 16. Each worker receives its own
pre-created instance via `cfg.NewInstance()` (called during pool construction in `NewPool`)
and the startup args via the `args []string` parameter — not by importing the `silicon`
package directly.

The `workerJob` struct carries `prog *silver.Program` and a `result chan *backend.VerificationResult`.
Both types are owned by their respective plans (14 and 15); plan 15b uses them from their
canonical import paths (`gobra/internal/silver` and `gobra/internal/backend`).

## `Submit` Contract

`Submit(prog *silver.Program) *backend.VerificationResult` sends the program to the next
available pool worker and blocks until the worker completes the Build → Verify cycle. The
returned `*backend.VerificationResult` carries a `Close func()` set by the worker to
`built.Close`. The caller must `defer result.Close()` before calling `Report()`.

## Z3 API Mode Caveat

`--z3APIMode` runs Z3 in-process via the Z3 Java API. Z3's in-process API has shared global
state that is not safe across concurrent calls from different threads in the same process.
If `--z3APIMode` is set and `--workers > 1`, log a warning and force `--workers 1`.
Document this constraint in `--help` for both flags.

## Deliverables

- Updated `internal/backend/jvm/jvm.go` — `WorkerPool` expanded to configurable `poolSize`;
  `NewPool(jvm *JVM, poolSize int, cfg backend.SiliconConfig) *WorkerPool` starts N worker
  goroutines at construction time. `NewPool` calls `cfg.NewInstance()` once per worker to
  create a `backend.SiliconInstance` for that worker; the `silicon` package is never imported
  by the `jvm` package. This is the authoritative `NewPool` constructor — it supersedes the
  skeleton from plan 15 (which delivered the `WorkerPool` struct and `Stop()` only).
  **`runWorkerSkeleton` removal**: plan 15 delivers a `runWorkerSkeleton` stub. Plan 15b
  delivers the full `runWorker` that supersedes it. When implementing plan 15b, **delete
  `runWorkerSkeleton` from `jvm.go`** — only `runWorker` (defined in this plan) must remain.
  Leaving both in the package produces dead code that misleads future readers.
- Tests:
  - Start a pool with `poolSize=3`; submit 5 sequential jobs; confirm all return results.
  - Confirm `--z3APIMode --workers 3` logs exactly one warning and forces `--workers 1`.

## Verification Specifications (C9)

Plan 15b's worker goroutines run JNI calls on OS-pinned threads and must formally specify
thread ownership and result validity using Gobra permissions.

1. **Worker thread-lock invariant** — each `runWorker` goroutine holds an exclusive
   `ThreadAttached` token for its lifetime (produced by `AttachCurrentThread`, consumed by
   `DetachCurrentThread` on defer). `ThreadAttached` is declared **once** in plan 15's C9
   (`internal/backend/jvm/jvm.go`); plan 15b reuses it — do NOT redeclare it here, as both
   plans contribute to the same `jvm` package and Gobra rejects duplicate predicate declarations.
   ```go
   // ThreadAttached is declared in plan 15 (jvm.go) — reference only, not redeclared.
   //@ requires acc(ThreadAttached(), 1)
   //@ ensures  acc(ThreadAttached(), 1) && acc(result) && result != nil
   func (instance *SiliconFrontendAPI) Verify(prog jobject) *backend.VerificationResult
   ```

2. **Submit postcondition** — `Submit` blocks until a result is available; the returned
   pointer is always non-nil (either a verification result or an infrastructure error):
   ```go
   //@ ensures result != nil
   func (p *WorkerPool) Submit(prog *silver.Program) (result *backend.VerificationResult)
   ```

3. **NewPool constructor** — `NewPool` guarantees that exactly `poolSize` worker goroutines
   are running and attached before it returns:
   ```go
   //@ requires poolSize >= 1
   //@ ensures  acc(pool) && pool != nil
   func NewPool(jvm *JVM, poolSize int, cfg backend.SiliconConfig) (pool *WorkerPool)
   ```

4. **Z3 API mode invariant** — if `--z3APIMode` is active, `poolSize` is forced to 1 before
   `runWorker` goroutines are spawned. Gobra can verify the absence of concurrent Z3 access
   via a ghost field:
   ```go
   //@ ghost field z3APIMode bool
   //@ invariant z3APIMode ==> len(workers) == 1
   ```

## Scaling Note

Diminishing returns begin at `runtime.NumCPU()` workers: each worker holds a Silicon + Z3
subprocess; Z3 is CPU-bound. Beyond NumCPU, adding workers increases memory use and OS
scheduler overhead without improving throughput. The default cap is correct and should be
documented in `--help` for `--workers`. A value of 4–8 covers most developer machines.
