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
  - Performance: `--parallelizeBranches`, `--cacheFile`
- Pipeline orchestration: Gobrafier → Parser → Type Checker → Desugarer → Transforms →
  Translator → JNI Backend → Reporter
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
- `internal/config/config.go` — `Config` struct and flag parsing
- `internal/pipeline/pipeline.go` — `Run(cfg *Config) error` orchestrating all stages
- Tests: integration tests running the full pipeline on small `.gobra` files

## Open Questions

- Should the CLI use `flag` (stdlib), `cobra`, or `pflag`? `cobra` is common for Go CLIs
  and supports subcommands if needed in the future; `flag` is simpler. Given this is a solo
  project, `flag` is sufficient.
