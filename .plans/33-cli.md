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
- Pipeline orchestration: Gobrafier → Parser → Type Checker → Desugarer → Transforms →
  Translator → JNI Backend → Reporter
- **`{pkg}_run_inits` invocation**: after translation, and before dispatching to Silicon,
  the pipeline must invoke the synthesized `{pkg}_run_inits` Silver method for each package
  in dependency order (innermost dependencies first). This drives verification of all `init`
  functions. See plan 27 ("init function encoding") for the Silver method naming convention.
  Concretely: `internal/pipeline/pipeline.go` calls the translator to get a `*silver.Program`,
  then prepends a `MethodCall` to `{pkg}_run_inits` at the start of the verification entry
  point for each package. If the program has no `init` functions, `{pkg}_run_inits` is an
  empty Silver method and the call is a no-op; emit it anyway to keep the logic uniform.
- Exit code: 0 on verification success, non-zero on failure or error
- `--printViper`: print the generated Silver text (using the Silver printer from 14) without
  verifying; useful for debugging

**Out of scope:**
- IDE/LSP mode (not in initial scope)
- Daemon/server mode

## Dependencies

- [07-package-resolver.md](07-package-resolver.md) — package loading
- [13-internal-transforms.md](13-internal-transforms.md) — transform pipeline
- [19-translator-core.md](19-translator-core.md) — translation
- [27-encoding-methods.md](27-encoding-methods.md) — encoding must be substantially complete
- [32-reporter.md](32-reporter.md) — output

## Reference: Current Gobra

- `src/main/scala/viper/gobra/GobraRunner.scala` — main runner
- `src/main/scala/viper/gobra/GobraConfig.scala` — all configuration options; replicate these

## Deliverables

- `cmd/gobra/main.go` — entry point
- `internal/config/config.go` — `Config` struct and flag parsing; includes `--workers N` flag
  (default = `min(runtime.NumCPU(), numSubPrograms)`; wired to `WorkerPool` in plan 17b)
- `internal/pipeline/pipeline.go` — `Run(cfg *Config) error` orchestrating all stages
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
