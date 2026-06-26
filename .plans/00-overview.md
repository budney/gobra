# Go-Gobra Rewrite: Master Plan

## Goal

Rewrite Gobra in Go, achieving full feature parity with the Scala implementation and ultimately
self-hosting: Go-Gobra verifies its own source code.

## Architectural Decisions (Resolved)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Viper backend | Keep ViperServer/Silicon/Carbon as-is | Scope limited to Gobra itself |
| Backend interface | JNI via [jnigi](https://github.com/timob/jnigi) | Mirrors Prusti (Rust); in-process JVM, no filesystem boundary, no HTTP overhead |
| Go parser | `go/parser` stdlib + custom annotation mini-parser | Go grammar is large/subtle; stdlib is correct, maintained, handles generics |
| Annotation syntax | **Unresolved — see 02-annotation-syntax-decision.md** | Must be resolved before parser work begins |
| Feature scope | Full parity, built incrementally | Self-hosting defines "done enough" |
| Testing strategy | Port regression suite; differential testing vs. Scala Gobra | Scala Gobra is the oracle throughout |
| Team / timeline | Solo, no hard deadline | Plan supports sequential or depth-first execution |

## Work Breakdown Structure

### Group 0: Foundation
| File | Title | Blocked by |
|------|-------|------------|
| [01-project-setup.md](01-project-setup.md) | Project Setup | — |
| [02-annotation-syntax-decision.md](02-annotation-syntax-decision.md) | Annotation Syntax Design Decision | 01 |

### Group 1: Frontend
| File | Title | Blocked by |
|------|-------|------------|
| [03-frontend-ast.md](03-frontend-ast.md) | Frontend AST | 01, 02 |
| [04-go-parser.md](04-go-parser.md) | Go Parser Integration | 03 |
| [05-annotation-parser.md](05-annotation-parser.md) | Annotation Mini-Parser | 02, 03 |
| [06-gobrafier.md](06-gobrafier.md) | Go File Preprocessor (Gobrafier) | 04 |
| [07-package-resolver.md](07-package-resolver.md) | Package Resolver | 04, 06 |

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
| [17-silicon-backend.md](17-silicon-backend.md) | Silicon Backend | 16 |
| [18-carbon-backend.md](18-carbon-backend.md) | Carbon Backend (optional) | 16 |

### Group 5: Translator
| File | Title | Blocked by |
|------|-------|------------|
| [19-translator-core.md](19-translator-core.md) | Translator Core & Context | 11, 14 |
| [20-encoding-primitives.md](20-encoding-primitives.md) | Encoding: Primitive Types | 19 |
| [21-encoding-structs.md](21-encoding-structs.md) | Encoding: Structs & Fields | 19 |
| [22-encoding-pointers.md](22-encoding-pointers.md) | Encoding: Pointers | 19 |
| [23-encoding-slices.md](23-encoding-slices.md) | Encoding: Slices & Arrays | 19 |
| [24-encoding-maps.md](24-encoding-maps.md) | Encoding: Maps | 19 |
| [25-encoding-interfaces.md](25-encoding-interfaces.md) | Encoding: Interfaces | 19, 21 |
| [26-encoding-permissions.md](26-encoding-permissions.md) | Encoding: Permissions & Predicates | 19 |
| [27-encoding-methods.md](27-encoding-methods.md) | Encoding: Methods & Functions | 19, 20, 21, 22, 23, 24, 25, 26 |
| [28-encoding-channels.md](28-encoding-channels.md) | Encoding: Channels | 19, 26 |
| [29-encoding-adts.md](29-encoding-adts.md) | Encoding: Ghost ADTs | 19 |
| [30-encoding-generics.md](30-encoding-generics.md) | Encoding: Generics | 19, 21, 25 |
| [31-encoding-builtins.md](31-encoding-builtins.md) | Encoding: Built-in Stubs | 27 |

### Group 6: Error Reporting
| File | Title | Blocked by |
|------|-------|------------|
| [32-reporter.md](32-reporter.md) | Reporter & Error Mapping | 14, 17 |

### Group 7: CLI & Integration
| File | Title | Blocked by |
|------|-------|------------|
| [33-cli.md](33-cli.md) | CLI & Entry Point | 07, 13, 19, 27, 32 |

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

## Unblocked Work (can start immediately)

After completing 01 and 02:
- 03, 11, 14, 15 are all unblocked and independent of each other
