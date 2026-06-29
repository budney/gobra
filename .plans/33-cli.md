# 33 — CLI & Entry Point

## Objective

Implement the command-line interface: parse flags, construct the configuration, drive the
full verification pipeline from source files to reported results, and exit with an appropriate
status code.

## Scope

**In scope:**
- All CLI flags from the current Gobra (replicate `--help` output parity):
  - Input: `-i`/`--input` (files), `-p`/`--packages`, `--projectRoot`, `--include`
  - Backend: `--backend` (silicon/carbon), `--z3Exe`, `--boogieExe`
  - JVM: `--viperServerJar` (path to the ViperServer/Silicon fat JAR; also read from
    `VIPERSERVER_JAR` env var); `--jvmArgs` (additional JVM flags)
  - Verification: `--overflow`, `--checkConsistency`, `--module`, `--assumeInjectivityOnInhale`
  - Output: `--logLevel`, `--printViper`, `--parseOnly`, `--typeCheckOnly`, `--noVerify`
  - Performance: `--parallelizeBranches` (see note below), `--cacheFile`
- Pipeline orchestration:
  Gobrafier → Parser → Type Checker → Desugarer → Transforms → Translator → **Chopper** → JNI Backend → Reporter

  The **Chopper** step (plan 16b) sits between the Translator and the JNI Backend. After
  `Translate(prog)` returns a single `*silver.Program`, `pipeline.go` calls
  `Chop(silverProg, chop.ChopConfig{Bound: cfg.ChopBound})` to produce `[]*silver.Program`
  (sub-programs split by method for parallel verification). `cfg.ChopBound` is a `*int`
  populated from the `--chop-bound N` flag (nil means unlimited). When `--chop` is set without
  an explicit `--chop-bound`, `cfg.ChopBound` remains nil — the chopper merges freely until
  no free merges remain (see plan 16b Phase 3 stop condition). Use `--chop-bound N` to cap
  the final sub-program count explicitly. Do NOT default `cfg.ChopBound` to `cfg.Workers`;
  doing so over-constrains merging when the user sets `--workers` for unrelated reasons.
  This slice is then passed to `WorkerPool.DispatchChopped`
  (plan 17b), which dispatches sub-programs to up to `--workers N` JNI workers concurrently.
  The number of sub-programs is determined by the chopper (via `--chop-bound`), not by
  `--workers`; if there are fewer sub-programs than workers, the excess workers simply idle.
  Pipeline.go must import plan 16b and call `Chop` explicitly — plan 17b's `DispatchChopped`
  does not call it internally.
- **`{pkg}_run_inits` invocation**: the translator (plan 27) synthesizes a Silver method
  `{pkg}_run_inits` for each package. This method is a regular member of the `*silver.Program`
  returned by the translator — Silicon verifies it independently, like any other Silver method.
  `pipeline.go` does NOT need to inject a call into any entry point; `{pkg}_run_inits` is
  verified by Silicon automatically because it is present in the Silver program. The pipeline's
  only responsibility is to ensure packages are translated in dependency order (innermost
  dependencies first), so that `{pkg}_run_inits` for an imported package is part of the Silver
  program before the importer's methods reference it. If the program has no `init` functions,
  `{pkg}_run_inits` is an empty Silver method with no body; it still verifies trivially.
  Note: Silver has no global "entry point" that Silicon verifies first — every method in the
  program is verified in isolation. Do NOT attempt to prepend a `MethodCall` node into any
  existing method; the synthesized `{pkg}_run_inits` method is self-contained.
- Exit code: 0 on verification success, non-zero on failure or error
- `--printViper`: print the generated Silver text (using the Silver printer from 14) without
  verifying; useful for debugging

**Out of scope:**
- IDE/LSP mode (not in initial scope)
- Daemon/server mode

## Dependencies

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type accumulated and forwarded by `pipeline.go`; `pipeline.go` imports `internal/diagnostic/` directly (do NOT use `internal/reporting` in `pipeline.go`)
- [07-package-resolver.md](07-package-resolver.md) — package loading (produces `[]*PackageInfo` with assembled `*frontend.PPackage` per package)
- [08-type-checker-core.md](08-type-checker-core.md) — `Check(pkg *frontend.PPackage, importer types.Importer) (*TypeInfo, []Diagnostic)`; invoked by pipeline for each package
- [09-type-checker-specs.md](09-type-checker-specs.md) — `CheckSpecs(pkg *frontend.PPackage, info *TypeInfo) []Diagnostic`; invoked after plan 08 per package
- [10-type-checker-multipackage.md](10-type-checker-multipackage.md) — custom `types.Importer` and `ExternalTypeInfo`; wired into plan 08's `Check` call
- [12-desugarer.md](12-desugarer.md) — `Desugar(pkg *frontend.PPackage, info *TypeInfo) (*internal.Program, []Diagnostic)`; invoked after type checking
- [13-internal-transforms.md](13-internal-transforms.md) — transform pipeline (`Apply`)
- [15-jni-setup.md](15-jni-setup.md) — JVM lifecycle and `WorkerPool` skeleton
- [15b-worker-pool-expansion.md](15b-worker-pool-expansion.md) — N-worker `NewPool`; `pipeline.go` constructs the pool using plan 15b's expanded `NewPool(jvm, poolSize, cfg)`
- [16-silver-jni-builder.md](16-silver-jni-builder.md) — Silver JNI builder (Build + nodeMap)
- [16b-silver-chopper.md](16b-silver-chopper.md) — `Chop()` and `ChopConfig`; called by `pipeline.go` between Translator and JNI Backend
- [17-silicon-backend.md](17-silicon-backend.md) — Silicon backend (Verify)
- [17b-parallel-workers.md](17b-parallel-workers.md) — parallel worker pool (--workers N)
- [18-carbon-backend.md](18-carbon-backend.md) — Carbon backend; required because plan 33
  exposes `--backend carbon` as a flag
- [19-translator-core.md](19-translator-core.md) — translation
- [27-encoding-methods.md](27-encoding-methods.md) — encoding must be substantially complete
- [32-reporter.md](32-reporter.md) — output

## Reference: Current Gobra

- `src/main/scala/viper/gobra/GobraRunner.scala` — main runner
- `src/main/scala/viper/gobra/GobraConfig.scala` — all configuration options; replicate these

## Deliverables

- `cmd/gobra/main.go` — entry point
- `internal/config/config.go` — `Config` struct and flag parsing; includes `--workers N` flag
  (default = `runtime.NumCPU()`; wired to `WorkerPool` in plan 17b). Note: the actual
  concurrent JNI job count is capped at `min(poolSize, len(subPrograms))` at dispatch time
  by the semaphore in `DispatchChopped` — excess workers simply idle. `numSubPrograms` is
  only known after the translator and chopper run and cannot be used to set the pool size
  at flag-parse time.
- `internal/pipeline/pipeline.go` — `Run(cfg *Config) error` orchestrating all stages.
  Two sentinel errors are defined in this package:
  ```go
  // ErrVerificationFailed is returned when Silicon reports one or more verification
  // errors. The tool ran correctly; the program under verification has a proof failure.
  // Distinct from infrastructure errors so callers (tests, CI) can distinguish the two.
  var ErrVerificationFailed = errors.New("verification failed")
  ```
  `Run` returns `ErrVerificationFailed` when verification finds errors (exit code 1),
  a wrapped infrastructure error when the JVM, JAR, or file system fails (exit code 1),
  and `nil` when verification succeeds (exit code 0). The reporter is called inside `Run`
  before returning; all diagnostic output is written to stdout/stderr by `Run` itself.
- Tests: integration tests running the full pipeline on small `.gobra` files

## Resolved Questions

**CLI library (resolved):** Use Go's stdlib `flag` package. `cobra` adds subcommand support
but also adds a dependency and annotation burden for self-hosting. The current Gobra has no
subcommands; `flag` is sufficient and keeps the codebase simpler to verify.

**ViperServer JAR path precedence (resolved):** The `--viperServerJar` flag and the
`VIPERSERVER_JAR` environment variable both specify the JAR path. Resolution order:
1. `--viperServerJar` flag (explicit CLI override, highest priority)
2. `VIPERSERVER_JAR` environment variable
3. Fatal error if neither is set.

Document this precedence in `--help` output and the README.

**`--parallelizeBranches` (resolved):** This flag enables Silicon's internal branch-level
parallelism within a single Silicon verification job. It is a Silicon-side option, not a
Gobra-level chopping option. Implementation: append the string `"--parallelizeBranches"` to
the `SiliconConfig.Args` slice that is passed to Silicon at startup (plan 17). No Gobra-level
logic is needed beyond that passthrough.

This is **distinct** from `--workers N` (plan 17b):
- `--parallelizeBranches`: parallelism *within* one Silicon call, managed by Silicon's own
  branch-exploration logic.
- `--workers N`: parallelism *across* multiple chopped sub-programs, managed by Go-Gobra.

Both can be set simultaneously. Document in `--help` for both flags that combining them
multiplies thread usage and the total should not exceed available CPU cores.

**`--chop` + `--z3APIMode` interaction (documented):** When `--z3APIMode` is set, Silicon
uses the Z3 API instead of process-based Z3; this forces `poolSize = 1` (Z3 API mode is not
thread-safe across multiple simultaneous verification jobs). With `--chop --workers 4
--z3APIMode`, the chopper may produce up to 4 sub-programs, but only 1 worker can run at a
time — execution is serialized despite appearing parallel from the config. **This is
functionally correct** (all sub-programs are eventually verified) but counterintuitive and
slower than `--workers 1` would suggest. Document in `--help` for `--z3APIMode`: "Forces
sequential verification even when --workers N > 1."

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/pipeline/pipeline.go` and
`internal/config/config.go` and verified before this plan is considered complete.

1. **`Run` error postcondition**: `nil` on clean verification; `ErrVerificationFailed` when
   Silicon reports proof failures; a wrapped infrastructure error on tool failure. The reporter
   is called inside `Run`; all output is written before `Run` returns. Callers need only check
   `err != nil` for the exit code — they do not receive or re-print diagnostics.
   ```go
   //@ requires cfg != nil
   //@ ensures  err == nil || err == ErrVerificationFailed || isInfrastructureError(err)
   //@ decreases // terminates when the verification backend terminates
   func Run(cfg *Config) (err error)
   ```
   Exit-code mapping in `main.go`: `err != nil` → exit 1, `err == nil` → exit 0.
   Tests distinguish verification failure from infrastructure failure via
   `errors.Is(err, ErrVerificationFailed)`.

2. **`parseFlags` nil-safety**: flag parsing either succeeds with a non-nil `Config` or fails
   with a non-nil error; never returns `(nil, nil)`:
   ```go
   //@ requires args != nil
   //@ ensures  (cfg != nil) != (err != nil) // exactly one is non-nil
   func parseFlags(args []string) (cfg *Config, err error)
   ```

3. **Short-circuit flag precedence** (`--parseOnly` > `--typeCheckOnly` > `--noVerify`):
   The pipeline bails out at the first active short-circuit flag in that order.
   ```go
   //@ requires cfg != nil
   //@ ensures  cfg.ParseOnly  ==> !cfg.TypeCheckOnly && !cfg.NoVerify  // most restrictive wins
   //@ ensures  cfg.TypeCheckOnly ==> !cfg.NoVerify
   func validateFlagPrecedence(cfg *Config)
   ```

4. **No-panic contract**: `Run` must not panic. Internal invariant violations in downstream
   stages surface as non-nil `error` values. The only permitted panics are in downstream
   functions that explicitly declare `panic` as their error-reporting contract (e.g., the
   desugarer on unexpected node types — plan 12). `Run` itself catches no panics; callers
   should not recover from panics produced by `Run`.
   ```go
   // Informally: Run is panic-free modulo downstream explicit-panic contracts.
   // Formal Gobra termination annotation:
   //@ decreases // delegated to each stage's own termination proof
   ```
