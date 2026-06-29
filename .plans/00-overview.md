# Go-Gobra Rewrite: Master Plan

## Goal

Rewrite Gobra in Go, achieving full feature parity with the Scala implementation and ultimately
self-hosting: Go-Gobra verifies its own source code.

## Architectural Decisions (Resolved)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Viper backend | Keep ViperServer/Silicon/Carbon as-is | Scope limited to Gobra itself |
| Backend interface | JNI via [jnigi](https://github.com/timob/jnigi) + thin Java helper JAR | In-process JVM; helper JAR wraps Silver constructors with Java-friendly signatures, avoiding raw Scala collection construction from Go |
| Go parser | `go/parser` stdlib + custom annotation mini-parser | Go grammar is large/subtle; stdlib is correct, maintained, handles generics |
| Annotation syntax | Keep `//@ ...` unchanged — see 02-annotation-syntax-decision.md | Resolved |
| Feature scope | Full parity with commit `d7e0b582`, built incrementally | Self-hosting defines "done enough" |
| Testing strategy | Port regression suite; differential testing vs. Scala Gobra | Scala Gobra is the oracle throughout |
| Team / timeline | Solo, no hard deadline | Plan supports sequential or depth-first execution |
| Frontend visitor | Companion wrapper structs (Option B) — see D10 in DECISIONS.md | Resolved |

## Work Breakdown Structure

### Group 0: Foundation
| File | Title | Blocked by |
|------|-------|------------|
| [01-project-setup.md](01-project-setup.md) | Project Setup | — |
| [02-annotation-syntax-decision.md](02-annotation-syntax-decision.md) | Annotation Syntax Design Decision | 01 |
| [32a-diagnostics.md](32a-diagnostics.md) | Diagnostic Type | — |

### Group 1: Frontend
| File | Title | Blocked by |
|------|-------|------------|
| [03-frontend-ast.md](03-frontend-ast.md) | Frontend AST | 01, 02 |
| [04-go-parser.md](04-go-parser.md) | Go Parser Integration | 03, 06 |
| [05-annotation-parser.md](05-annotation-parser.md) | Annotation Mini-Parser | 02, 03 |
| [06-gobrafier.md](06-gobrafier.md) | Go File Preprocessor (Gobrafier) | 01 |
| [07-package-resolver.md](07-package-resolver.md) | Package Resolver | 03, 04, 05, 06 (see note†) |

### Group 2: Type Checker
| File | Title | Blocked by |
|------|-------|------------|
| [08-type-checker-core.md](08-type-checker-core.md) | Type Checker Core | 03, 04, 05 |
| [09-type-checker-specs.md](09-type-checker-specs.md) | Type Checker: Specification Expressions | 08 |
| [10-type-checker-multipackage.md](10-type-checker-multipackage.md) | Type Checker: Multi-Package | 07, 08 |

### Group 3: Internal Representation
| File | Title | Blocked by |
|------|-------|------------|
| [11-internal-ast.md](11-internal-ast.md) | Internal AST | 01 |
| [12-desugarer.md](12-desugarer.md) | Desugarer | 03, 08, 09, 10, 11 |
| [13-internal-transforms.md](13-internal-transforms.md) | Internal Transforms | 11, 12 |

### Group 4: Silver IR & JNI Backend
| File | Title | Blocked by |
|------|-------|------------|
| [14-silver-ast.md](14-silver-ast.md) | Silver IR (Go structs) | 01 |
| [15-jni-setup.md](15-jni-setup.md) | JNI Setup & JVM Lifecycle | 01 |
| [16-silver-jni-builder.md](16-silver-jni-builder.md) | Silver JNI Builder | 14, 15 |
| [16b-silver-chopper.md](16b-silver-chopper.md) | Silver Program Chopper | 14 |
| [17-silicon-backend.md](17-silicon-backend.md) | Silicon Backend | 15, 16 |
| [15b-worker-pool-expansion.md](15b-worker-pool-expansion.md) | Worker Pool Expansion (N workers) | 15, 16, 17 |
| [17b-parallel-workers.md](17b-parallel-workers.md) | Parallel JNI Worker Pool | 15b, 16, 17 |
| [18-carbon-backend.md](18-carbon-backend.md) | Carbon Backend (optional) | 16, 17 |

### Group 5: Translator
| File | Title | Blocked by |
|------|-------|------------|
| [19-translator-core.md](19-translator-core.md) | Translator Core & Context | 11, 14 |
| [20-encoding-primitives.md](20-encoding-primitives.md) | Encoding: Primitive Types | 19 |
| [21-encoding-structs.md](21-encoding-structs.md) | Encoding: Structs & Fields | 19 |
| [22-encoding-pointers.md](22-encoding-pointers.md) | Encoding: Pointers | 19, 21 |
| [23-encoding-slices.md](23-encoding-slices.md) | Encoding: Slices & Arrays | 19 |
| [24-encoding-maps.md](24-encoding-maps.md) | Encoding: Maps | 19, 25 |
| [25-encoding-interfaces.md](25-encoding-interfaces.md) | Encoding: Interfaces | 19, 21 |
| [26-encoding-permissions.md](26-encoding-permissions.md) | Encoding: Permissions & Predicates | 19 |
| [27-encoding-methods.md](27-encoding-methods.md) | Encoding: Methods & Functions | 19, 20, 21, 22, 23, 24, 25, 26 |
| [28-encoding-channels.md](28-encoding-channels.md) | Encoding: Channels | 19, 26 |
| [29-encoding-adts.md](29-encoding-adts.md) | Encoding: Ghost ADTs | 19 |
| [30-encoding-generics.md](30-encoding-generics.md) | Encoding: Generics | 19, 21, 25 |
| [31-encoding-builtins.md](31-encoding-builtins.md) | Encoding: Built-in Stubs | 07, 10, 27 |

### Group 6: Error Reporting
| File | Title | Blocked by |
|------|-------|------------|
| [32-reporter.md](32-reporter.md) | Reporter & Error Mapping | 08, 14, 16, 17 |

### Group 7: CLI & Integration
| File | Title | Blocked by |
|------|-------|------------|
| [33-cli.md](33-cli.md) | CLI & Entry Point | 07, 08, 09, 10, 12, 13, 15b, 16, 16b, 17, 17b, 19, 27, 32 |

### Group 8: Testing
| File | Title | Blocked by |
|------|-------|------------|
| [34-test-infrastructure.md](34-test-infrastructure.md) | Test Infrastructure | 33 |
| [35-regression-suite.md](35-regression-suite.md) | Regression Test Suite | 34 |

### Group 9: Self-Hosting
| File | Title | Blocked by |
|------|-------|------------|
| [36-self-hosting-annotations.md](36-self-hosting-annotations.md) | Self-Hosting: Write Specs | 35 |
| [37-self-hosting-verify.md](37-self-hosting-verify.md) | Self-Hosting: Achieve Verification | 36 |

## Reference Documents (not implementation plans)

These files have no Objective/Scope/Deliverables — they provide background context only.

| File | Purpose |
|------|---------|
| [CONTEXT.md](CONTEXT.md) | Project orientation for new contributors and AI agents |
| [DECISIONS.md](DECISIONS.md) | Log of resolved architectural decisions (D1–D11+) |
| [COVERAGE.md](COVERAGE.md) | Go AST coverage table mapping internal AST node types to encoding plans |
| [scratchpad.md](scratchpad.md) | Automation scratchpad for AI agents running /check-plan and /review-plan |

## Key Reference: Current Gobra Pipeline

```
Parser (frontend/Parser.scala + Gobrafier.scala)
  └─> Type Checker / Info (frontend/info/)
        └─> Desugarer (frontend/Desugar.scala)
              └─> Internal Transforms (ast/internal/transform/)
                    └─> Translator (translator/)
                          └─> Backend (backend/) ──> ViperServer/Silicon
                                └─> Reporter (reporting/)
```

## Cross-Cutting Notes

**Concurrency model**: Go-Gobra uses a `WorkerPool` of JNI worker goroutines, each locked to
its own OS thread via `runtime.LockOSThread()` and each holding its own `SiliconFrontendAPI`
instance. Plan 15 delivers the JVM lifecycle and a `WorkerPool` skeleton (`poolSize=1`).
Plan 15b expands it to N Silicon-aware workers (each with its own `SiliconFrontendAPI`).
Plan 17b adds `DispatchChopped` fan-out and result merging. With N workers and `--chop`,
multiple chopped sub-programs verify in parallel — matching the parallelism Scala Gobra
achieves via `Future.traverse`. Without `--chop`, or before plan 15b is complete,
verification is effectively single-threaded at the Silicon level. Plan 33 wires `--workers N`
and plan 34 accounts for the JNI worker pool in test infrastructure.

**Error/diagnostic contract**: Every pipeline stage uses the same `Diagnostic` type
`{File, Line, Col, Message, Category}` (defined in plan 32a, `internal/diagnostic/`). Each stage accumulates errors
into a `[]Diagnostic` slice and continues where possible; the pipeline aborts only if any
errors exist before moving to the next stage. Panics (not errors) are reserved for internal
consistency violations that cannot be recovered from.

**Bodyless Viper functions**: See the critical warning in plan 19 — missing postconditions
on bodyless Viper functions silently weaken verification. This affects plans 21, 23, 25, 27.
(Plan 20's `goIntDiv`/`goIntMod` carry explicit bodies extracted from `IntegerEncoding.scala`
and are not bodyless. Plan 24 uses Silver built-in `Map` operations with no bodyless functions.
Plan 29's ADT constructors are Silver domain functions governed by axioms, not bodyless Silver
functions — different failure mode, covered separately in plan 25's domain-axioms note below.)

**Domain axioms vs. bodyless Silver functions (plan 25)**: Plan 25's `Poly[T]` domain
uses Silver *domain functions* governed by axioms, not Silver *functions* with postconditions.
The failure mode is different: a wrong domain axiom can unsound the proof directly, whereas a
missing postcondition merely weakens it. The same audit discipline applies — verify the axiom
set against `InterfaceEncoding.scala` — but do not confuse domain axioms with the bodyless
Silver function invariant. See plan 25's "Bodyless Functions" section for the distinction.

## Unblocked Work (can start immediately)

**D4 is resolved.** After completing 01 (project setup), all of the following are unblocked
and independent of each other:
- 03 (Frontend AST)
- 05 (Annotation Parser) — **design is unblocked** (D4/plan 02 resolved the grammar); but
  **implementation is blocked on 03** (the parser produces 03's node types — `PFunctionSpec`,
  `PExpression`, etc. — which must be defined first). Do not start coding 05 until 03 is done.
- 06 (Gobrafier)
- 11 (Internal AST)
- 14 (Silver IR)
- 15 (JNI Setup)

Note: 04 (Go Parser Integration) is blocked by **both** 03 and 06; it cannot start until
both the Frontend AST and the Gobrafier are complete. The Gobrafier is a text preprocessor
that runs before the parser — it is a prerequisite for 04, not a downstream dependent of it.

†**Plan 07 / Plan 10 runtime coupling note:** The WBS lists plan 07 as blocked only by 03,
04, 05, 06 — which is correct for **implementation**. However, plan 07's multi-package path
requires plan 10's custom `types.Importer` at **runtime**. Plan 07 can be implemented and
single-package tested without plan 10, but end-to-end multi-package resolution cannot be
exercised until plan 10 is complete. This is intentional: plan 10 is blocked by plan 07 (a
true build-ordering constraint), so plan 10 cannot be listed as a plan-07 dependency without
creating a cycle. The coupling is documented in plan 07's "Stub package resolution" note.
