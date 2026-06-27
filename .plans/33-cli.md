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
