# Go-Gobra Rewrite Plan Decompositions

This document is the index and registry for all sub-task work items. Each entry here
corresponds to a file that will be created in `.plans/tasks/` when that sub-task is
ready to be assigned.

---

## File Organization (Option A)

Parent plan files remain in `.plans/` unchanged and are the authoritative source of truth
for design decisions, type definitions, implementation notes, C9 specs, and reference Scala
pointers. They are never modified by sub-task work.

Sub-task files live in a dedicated `.plans/tasks/` subdirectory:

```
.plans/
  01-project-setup.md       ← authoritative parent, unchanged
  03-frontend-ast.md        ← authoritative parent, unchanged
  ...
  tasks/
    01.1-go-module-init.md
    01.2-directory-layout.md
    03.1-base-interfaces.md
    ...
  scratchpad.md
  CRITERIA.md
```

**Naming convention:** `NN.M-kebab-slug.md` — plan number, dot, sub-task number, hyphen,
short lowercase slug. For lettered plans use the letter: `32a.1-diagnostic-struct.md`.

---

## Referencing Discipline

Sub-task files must **not** duplicate content from their parent plan. The parent is the
single source of truth. A sub-task file contains only:

- Which sections of the parent plan to read before starting
- The exact scope boundary for this sub-task (what is and is not done here)
- The specific deliverables (file paths, function signatures, type names)
- The C9 annotation requirement (which specs from the parent's §Verification Specifications
  belong to code written in this sub-task)
- The done-criterion

Design decisions, implementation notes, type definitions, and reference Scala pointers are
referenced by section name, not copied.

---

## Standard Sub-task File Template

```markdown
# Sub-task NN.M — Short Title
**Parent plan:** [NN-slug.md](../NN-slug.md)
**Read first:** §Scope, §<relevant sections>
**Requires:** <prior sub-tasks or parent plan prerequisites>

## Scope (this sub-task only)
<one paragraph: what this sub-task specifically does and does not do>

## Deliverables
<bullet list: file paths, function/type names — no design rationale>
For full context see parent plan §<section>.

## C9
<"N/A" | "Implement all Gobra annotations from parent plan §Verification Specifications
whose functions/types are defined in this sub-task.">

## Done when
<single concrete criterion: command that must pass, or artifact that must exist>
```

---

## Plan 01 — Project Setup
Parent plan: [.plans/01-project-setup.md](.plans/01-project-setup.md)

#### 01.1: Go Module Initialization
- **Objective**: Initialize the Go module under the correct module path.
- **Deliverables**:
  - Create directory `gobra-go/`.
  - Run `go mod init github.com/viperproject/gobra`.
  - Define standard Go version in `go.mod` (minimum 1.21).
- **Verification Criteria**:
  - Verify `go.mod` exists with the correct package path and compiles cleanly.

#### 01.2: Directory Layout Skeleton
- **Objective**: Establish the packages folder structure reflecting the pipeline design.
- **Deliverables**:
  - Create folder skeleton: `cmd/gobra/`, `internal/frontend/`, `internal/info/`, `internal/ast/frontend/`, `internal/ast/internal/`, `internal/desugar/`, `internal/transform/`, `internal/translator/`, `internal/silver/`, `internal/backend/`, `internal/reporting/`, `internal/diagnostic/`, `tests/`.
  - Place minimal dummy `.go` files in each directory with correct `package` declarations.
- **Verification Criteria**:
  - Run `go build ./...` inside `gobra-go/` and verify it succeeds.

#### 01.3: Development Makefile Tooling
- **Objective**: Set up a standard development Makefile to automate linting and compilation tasks.
- **Deliverables**:
  - Create `gobra-go/Makefile` with basic targets: `build`, `test`, `lint`.
- **Verification Criteria**:
  - Run `make build` and verify it builds all skeleton modules.

#### 01.4: License Header Config & Validation
- **Objective**: Configure and verify the license header checks for Go files.
- **Deliverables**:
  - Extend `.github/license-check/config.json` to cover `gobra-go/**/*.go` using MPL-2.0 license.
- **Verification Criteria**:
  - Run `npx github:viperproject/check-license-header#v1 check --config .github/license-check/config.json --strict` and verify it passes for Go files.

#### 01.5: CI Workflow Setup
- **Objective**: Add automated build jobs targeting the Go compiler implementation.
- **Deliverables**:
  - Define a `go-build` CI job in `.github/workflows/` triggered on push/PRs to the `self-hosting` branch.
- **Verification Criteria**:
  - Ensure CI triggers and runs Go compilation checks on push.

#### 01.6: Test Data & Stubs Linking
- **Objective**: Link the existing regression suites and stub files for development.
- **Deliverables**:
  - Add symlinks under `gobra-go/tests/testdata/regressions` and `gobra-go/tests/testdata/stubs` referencing equivalent resources from the Scala project root.
- **Verification Criteria**:
  - Ensure symlink paths resolve correctly and can be loaded in Go test scripts.

#### 01.7: KNOWN_LIMITATIONS Initialization
- **Objective**: Initialize the known limitations tracking file.
- **Deliverables**:
  - Create an empty `gobra-go/KNOWN_LIMITATIONS.md` at the root of the Go module.
- **Verification Criteria**:
  - Assert file exists and is empty.

---

## Plan 02 — Annotation Syntax Design Decision
Parent plan: [.plans/02-annotation-syntax-decision.md](.plans/02-annotation-syntax-decision.md)
#### 02.1: Design Evaluation & Log
- **Objective**: Log the decision and rationale for keeping the standard comment syntax.
- **Deliverables**:
  - Add evaluations and choice description to `02-annotation-syntax-decision.md`.
- **Verification Criteria**:
  - Confirm the decision document is committed.

#### 02.2: Annotation Extraction Rules
- **Objective**: Define structural constraints for inline annotation positions.
- **Deliverables**:
  - Document positional constraints for inline syntax (ghost arguments, returns).
- **Verification Criteria**:
  - Assert all rules cover syntax found in current regression files.

#### 02.3: Position Tracking Spec
- **Objective**: Define rules for mapping multi-line comments to virtual token positions.
- **Deliverables**:
  - Document line concatenation and coordinate reconstruction logic.
- **Verification Criteria**:
  - Confirm spec maps comments to precise token boundaries.

---

## Plan 32a — Diagnostic Type
Parent plan: [.plans/32a-diagnostics.md](.plans/32a-diagnostics.md)
#### 32a.1: Diagnostic Struct & Categories
- **Objective**: Create the unified diagnostic type used across the early compilation pipeline.
- **Deliverables**:
  - Create `internal/diagnostic/diagnostic.go`.
  - Define `Category` constants (`DiagError`, `DiagWarning`, `DiagInfo`).
  - Define `Diagnostic` struct containing `File`, `Line`, `Col`, `Message`, and `Category` fields.
- **Verification Criteria**:
  - Compile the package and verify it has zero dependencies.

#### 32a.2: Diagnostic Unit Testing
- **Objective**: Verify field accessors and representation of Diagnostic records.
- **Deliverables**:
  - Create unit tests under `internal/diagnostic/diagnostic_test.go`.
- **Verification Criteria**:
  - Run `go test ./internal/diagnostic/...` and confirm all tests pass.

---

## Plan 03 — Frontend AST
Parent plan: [.plans/03-frontend-ast.md](.plans/03-frontend-ast.md)
#### 03.1: Base Node Interfaces
- **Objective**: Define core interfaces representing AST elements and declaration nodes.
- **Deliverables**:
  - Define interface `PNode` in `internal/ast/frontend/`.
  - Define interface `PDecl` with an unexported marker method `pDecl()`.
- **Verification Criteria**:
  - Compile check the interfaces.

#### 03.2: AST Position Wrapper
- **Objective**: Implement a coordinate position tracker for AST node mapping.
- **Deliverables**:
  - Implement structures representing source code file, line, and column coordinates for nodes.
- **Verification Criteria**:
  - Verify coordinates can be fetched for dummy nodes.

#### 03.3: Go Declaration AST Nodes
- **Objective**: Define nodes representing standard Go declarations.
- **Deliverables**:
  - Implement types: `PMethodDecl`, `PFunctionDecl`, `PVarDecl`, `PConstDecl`, `PTypeDecl` satisfying `PDecl`.
- **Verification Criteria**:
  - Assert all declaration types satisfy both `PNode` and `PDecl` interfaces.

#### 03.4: Go Statement AST Nodes
- **Objective**: Define nodes representing Go statements.
- **Deliverables**:
  - Implement statement types: `PGoStmt`, `PBlockStmt`, and the root container `PFile`.
  - Expose standard `*ast.File` metadata on `PFile`.
- **Verification Criteria**:
  - Verify statement structures compile and correctly nested blocks can be represented.

#### 03.5: AST Visitor Traversal
- **Objective**: Implement the frontend visitor traversal framework.
- **Deliverables**:
  - Define `Visitor` interface detailing method declarations for all Go node types.
  - Provide a default `BaseVisitor` doing recursive no-op traversals.
- **Verification Criteria**:
  - Run `BaseVisitor` on a test AST tree and assert traversal completes.

---

## Plan 04 — Go Parser Integration
Parent plan: [.plans/04-go-parser.md](.plans/04-go-parser.md)
#### 04.1: Native Go Parsing Wrapper
- **Objective**: Invoke standard `go/parser` inside Go-Gobra using shared FileSets.
- **Deliverables**:
  - Create `internal/frontend/parser.go`.
  - Implement entry point `ParseFile` using shared `token.FileSet`.
- **Verification Criteria**:
  - Assert parser executes and creates correct FileSet registrations.

#### 04.2: AST Conversion
- **Objective**: Translate standard `ast.File` tree to the frontend AST.
- **Deliverables**:
  - Map native Go structures to frontend wrapper types (`PFile`, `PGoStmt`, declarations).
- **Verification Criteria**:
  - Verify converted tree matches structural details of simple Go inputs.

#### 04.3: Comment Scan & Raw Extraction
- **Objective**: Filter comment blocks containing `//@` annotations.
- **Deliverables**:
  - Scan comment lists and extract lines with annotation prefixes.
- **Verification Criteria**:
  - Assert correct comments are captured for files with comments.

#### 04.4: Multi-line Concatenation & Mapping
- **Objective**: Join multi-line comments and map coordinates back to comments.
- **Deliverables**:
  - Concatenate comments and reconstruct accurate character positions.
- **Verification Criteria**:
  - Assert position offset maps match comment source coordinates.

#### 04.5: Block Annotation Registry
- **Objective**: Construct the mapping of statements to their raw annotation strings.
- **Deliverables**:
  - Populate `PFile.BlockAnnotations` with extracted annotations, using nil keys for file scope.
- **Verification Criteria**:
  - Assert correct block and file scope mappings are populated.

#### 04.6: Parser Diagnostics Integration
- **Objective**: Map parser syntax errors into Go-Gobra Diagnostics.
- **Deliverables**:
  - Intercept parser syntax failures and return partial ASTs with diagnostics.
- **Verification Criteria**:
  - Assert invalid syntax prints diagnostics and doesn't crash.

---

## Plan 05 — Annotation Mini-Parser
Parent plan: [.plans/05-annotation-parser.md](.plans/05-annotation-parser.md)
#### 05.1: Ghost AST Nodes (Specs & Clauses)
- **Objective**: Define frontend AST node types representing specification declarations.
- **Deliverables**:
  - Create structures: `PRequires`, `PEnsures`, `PPreserves`, `PInvariant`, `PDecreases` in `internal/ast/frontend/`.
- **Verification Criteria**:
  - Verify compile checks for specification nodes.

#### 05.2: Ghost AST Nodes (Statements & Expressions)
- **Objective**: Define nodes for ghost statements and permission expressions.
- **Deliverables**:
  - Define structures: `PFold`, `PUnfold`, `PAssert`, `PAssume`, `PInhale`, `PExhale`, `PAccess`, `PMagicWand`, quantifiers, and collection constructors.
- **Verification Criteria**:
  - Verify statements and expressions compile.

#### 05.3: Annotation Lexer
- **Objective**: Implement lexer for annotation syntax strings.
- **Deliverables**:
  - Create a custom tokenizer yielding tokens for spec words, operators, identifiers.
- **Verification Criteria**:
  - Verify token stream outputs correctly for various spec clauses.

#### 05.4: Annotation Parser Core
- **Objective**: Implement recursive-descent parser producing Ghost AST nodes.
- **Deliverables**:
  - Create `internal/frontend/annotationparser.go` with `ParseAnnotation` function.
  - Parse expressions, specs, and file-scope declarations (`PAdtType`, `PGhostFunc`, `PPredDecl`).
- **Verification Criteria**:
  - Write table-driven tests checking correct parsing for a variety of annotation constructs.

#### 05.5: Grammar Serialization
- **Objective**: Write the completed EBNF specification into the syntax decision document.
- **Deliverables**:
  - Record grammar rules in `02-annotation-syntax-decision.md`.
- **Verification Criteria**:
  - Assert grammar matches implementation rules and references.

---

## Plan 06 — Go File Preprocessor (Gobrafier)
Parent plan: [.plans/06-gobrafier.md](.plans/06-gobrafier.md)
#### 06.1: Preprocessor Line Preserving Scanner
- **Objective**: Implement line preservation scanner for preprocessor rewriting.
- **Deliverables**:
  - Create `internal/frontend/gobrafier.go`.
  - Implement structural scanner preserving total lines.
- **Verification Criteria**:
  - Assert line count matches inputs.

#### 06.2: Ghost Declaration Comment-Out
- **Objective**: Comment out invalid Go syntax declarations.
- **Deliverables**:
  - Rewrite top-level ghost functions, ADTs, and inline annotations to comments.
- **Verification Criteria**:
  - Confirm preprocessed files parse cleanly with standard Go parser.

#### 06.3: Position Shift Tracker
- **Objective**: Track position offset shift changes.
- **Deliverables**:
  - Map offset transitions.
- **Verification Criteria**:
  - Assert error positions mapping correctness.

#### 06.4: Gobrafier Golden Testing
- **Objective**: Run golden tests for file preprocessing.
- **Deliverables**:
  - Set up golden data testing in `internal/frontend/testdata/gobrafy/`.
- **Verification Criteria**:
  - Run tests with `GOBRAFY_UPDATE=1` and ensure files are correct.

---

## Plan 07 — Package Resolver
Parent plan: [.plans/07-package-resolver.md](.plans/07-package-resolver.md)
#### 07.1: Loop Invariant Spec Routing
- **Objective**: Interleave loop invariants (`invariant`) into the `PLoopSpec` of corresponding for-loops.
- **Deliverables**:
  - Implement loop invariant routing rules (Rule A) based on relative positions.
- **Verification Criteria**:
  - Verify that loop invariants are attached to loop structures and diagnostics are raised if detached.

#### 07.2: Block Ghost Statement Weaving
- **Objective**: Interleave ghost statements into Go blocks in correct position order.
- **Deliverables**:
  - Implement statement weaving (Rule B) sorting statements by source positions.
- **Verification Criteria**:
  - Assert ghost statements are woven in correct execution order.

#### 07.3: Import Dependency Scanner
- **Objective**: Scan packages to extract import paths.
- **Deliverables**:
  - Read import declarations from parsed packages.
- **Verification Criteria**:
  - Verify correct dependency lists returned.

#### 07.4: Packages Topological Sorter
- **Objective**: Order packages topologically according to their dependency structures.
- **Deliverables**:
  - Implement topological sorting, handling import cycles.
- **Verification Criteria**:
  - Assert sort order satisfies dependency prerequisites.

#### 07.5: Embedded Stubs Bundler & Virtual Loader
- **Objective**: Intercept imports targeting standard libraries and serve embedded stubs.
- **Deliverables**:
  - Integrate stubs directory into resolver using `//go:embed`.
- **Verification Criteria**:
  - Assert loading standard packages resolves stub resources.

---

## Plan 08 — Type Checker Core
Parent plan: [.plans/08-type-checker-core.md](.plans/08-type-checker-core.md)
#### 08.1: Scope Management
- **Objective**: Implement the scope lookup hierarchy for Go and ghost namespaces.
- **Deliverables**:
  - Create `GobraScope` interface and lookup methods.
- **Verification Criteria**:
  - Verify lookups locate variables across both scopes.

#### 08.2: Go Symbol Table & Checker Run
- **Objective**: Run native Go checker and save type information.
- **Deliverables**:
  - Wrap `go/types.Check` and populate `TypeInfo.Go`.
- **Verification Criteria**:
  - Verify standard types resolve.

#### 08.3: Identifier Binding to Scopes
- **Objective**: Walk AST declarations and bind symbols to scopes.
- **Deliverables**:
  - Populate scope symbol mappings for packages, methods, blocks.
- **Verification Criteria**:
  - Verify identifier scopes.

#### 08.4: Core Type Inference on AST
- **Objective**: Run type checking on basic declarations and expressions.
- **Deliverables**:
  - Implement core type checker pass checking assignments, returns, declarations.
- **Verification Criteria**:
  - Assert type check failures generate correct diagnostics.

#### 08.5: Ghost Type System Definitions
- **Objective**: Define the ghost type hierarchy and the two-pass stub-resolution algorithm used by the ghost type checker.
- **Deliverables**:
  - Create `internal/info/ghosttypes.go` with `GhostType` interface, all concrete implementations (`SeqType`, `SetType`, `MSetType`, `DictType`, `OptionType`, `ADTType`, `PermissionType`, `WandType`, `StubType`), `ADTConstructor` struct, and `GhostTypeInfo` struct.
  - Implement Pass 1 (register stubs) and Pass 2 (resolve bodies); assert no stubs remain after Pass 2.
- **Verification Criteria**:
  - All ghost types resolve cleanly on a file containing ADT, sequence, and permission expressions.

---

## Plan 09 — Type Checker: Specification Expressions
Parent plan: [.plans/09-type-checker-specs.md](.plans/09-type-checker-specs.md)
#### 09.1: Pre/Post Spec Checkers
- **Objective**: Verify specification declarations compile and have correct Boolean type mapping.
- **Deliverables**:
  - Implement check visitors for `PRequires`, `PEnsures`, `PPreserves`.
- **Verification Criteria**:
  - Assert invalid spec structures trigger diagnostics.

#### 09.2: Loop Invariant Checkers
- **Objective**: Verify loop invariants are Boolean expressions.
- **Deliverables**:
  - Implement check visitor for `PInvariant`.
- **Verification Criteria**:
  - Assert non-Boolean loop invariants return errors.

#### 09.3: Permission Expression Checkers
- **Objective**: Verify permission expressions (`acc`, magic wands) have valid shapes.
- **Deliverables**:
  - Implement checkers for `PAccess` and `PMagicWand`.
- **Verification Criteria**:
  - Assert permission checks enforce syntax constraints.

#### 09.4: Quantifier Scope & Typing
- **Objective**: Type check variables and body expressions within quantifiers.
- **Deliverables**:
  - Implement checkers for `PForall` and `PExists`.
- **Verification Criteria**:
  - Assert body expressions return boolean types.

#### 09.5: Collection Literals Checkers
- **Objective**: Check ghost collections (set, seq, dict, option) and their parameter types.
- **Deliverables**:
  - Implement checks for collection expressions.
- **Verification Criteria**:
  - Assert elements match collection parameter types.

#### 09.6: ADT Match Pattern Checker
- **Objective**: Verify match patterns exhaustively cover constructors.
- **Deliverables**:
  - Implement check visitor for `PMatch`.
- **Verification Criteria**:
  - Assert match checks detect unhandled cases.

---

## Plan 10 — Type Checker: Multi-Package
Parent plan: [.plans/10-type-checker-multipackage.md](.plans/10-type-checker-multipackage.md)
#### 10.1: Custom types.Importer
- **Objective**: Implement importer loading compiled packages and stubs.
- **Deliverables**:
  - Create `internal/info/importer.go`.
- **Verification Criteria**:
  - Verify importing packages loads symbols correctly.

#### 10.2: Unsafe Import Guard
- **Objective**: Block unsafe imports during compiler runs.
- **Deliverables**:
  - Reject 'unsafe' package imports with diagnostic errors.
- **Verification Criteria**:
  - Verify importing unsafe throws expected diagnostic.

#### 10.3: Serialization / Deserialization Interface Stubs
- **Objective**: Create placeholders for external type serializations.
- **Deliverables**:
  - Implement `Serialize()` and `DeserializeExternalTypeInfo()` stubs.
- **Verification Criteria**:
  - Verify cache fallback triggers `ErrStaleCacheEntry`.

---

## Plan 11 — Internal AST
Parent plan: [.plans/11-internal-ast.md](.plans/11-internal-ast.md)
#### 11.1: Simplified Internal AST Node Structs
- **Objective**: Define lower-level internal AST node models.
- **Deliverables**:
  - Create nodes under `internal/ast/internal/`.
- **Verification Criteria**:
  - Verify structural AST packages compile.

#### 11.2: Internal AST Visitor
- **Objective**: Implement traversers for internal AST representations.
- **Deliverables**:
  - Provide Visitor and traverser implementations.
- **Verification Criteria**:
  - Verify traversal path traversal checks.

#### 11.3: AST Debug Pretty-Printer
- **Objective**: Implement pretty printer for internal AST representation debug outputs.
- **Deliverables**:
  - Create `PrettyPrinter` print rules.
- **Verification Criteria**:
  - Verify sample print runs output expected code layout.

---

## Plan 12 — Desugarer
Parent plan: [.plans/12-desugarer.md](.plans/12-desugarer.md)
#### 12.1: Desugaring Driver & Core Lowering
- **Objective**: Implement desugaring walkthrough that maps frontend AST to internal AST.
- **Deliverables**:
  - Create `internal/desugar/desugar.go`.
- **Verification Criteria**:
  - Verify structural lowering yields equivalent declarations.

#### 12.2: Nil Check Assertions Insertion
- **Objective**: Generate explicit nil-checking assertions.
- **Deliverables**:
  - Insert explicit nil-safety assertions.
- **Verification Criteria**:
  - Verify pointer dereferences insert nil checking.

#### 12.3: Bounds Checks Assertions Insertion
- **Objective**: Generate bounds check safety validations.
- **Deliverables**:
  - Insert index bound assertions.
- **Verification Criteria**:
  - Verify array/slice indexes generate checks.

#### 12.4: Overflow Check Assertions Insertion
- **Objective**: Insert stubs for checking integer arithmetic bounds.
- **Deliverables**:
  - Insert basic overflow assertions.
- **Verification Criteria**:
  - Verify arithmetic generates check nodes.

---

## Plan 13 — Internal Transforms
Parent plan: [.plans/13-internal-transforms.md](.plans/13-internal-transforms.md)
#### 13.1: Constant Folding Transform
- **Objective**: Perform constant evaluation on internal AST expressions.
- **Deliverables**:
  - Implement fold visitor simplify binary math expressions.
- **Verification Criteria**:
  - Verify fold transforms reduce basic arithmetic structures.

#### 13.2: Constant Propagation Transform
- **Objective**: Propagate constants through variables.
- **Deliverables**:
  - Implement propagation traversers.
- **Verification Criteria**:
  - Assert constant references replace variable calls.

#### 13.3: Overflow Check Placement
- **Objective**: Refine and filter arithmetic safety check placements.
- **Deliverables**:
  - Instrument arithmetic operations with overflow constraints.
- **Verification Criteria**:
  - Verify overflow constraints map correctly.

#### 13.4: Termination Measure Evaluator
- **Objective**: Check loop decreases values are well-ordered.
- **Deliverables**:
  - Validate decreases terms.
- **Verification Criteria**:
  - Assert diagnostic alerts raise on invalid decreases checks.

#### 13.5: Call Graph Annotations
- **Objective**: Annotate call graph edges to resolve method dependencies.
- **Deliverables**:
  - Implement call-graph mappings.
- **Verification Criteria**:
  - Confirm edge counts matching test calls.

#### 13.6: Transform Pipeline Driver
- **Objective**: Implement the `Apply` entry point that chains all five transforms in the required fixed order.
- **Deliverables**:
  - Create `internal/transform/pipeline.go` with `Apply(prog *internal.Program, cfg Config) (*internal.Program, []Diagnostic)`.
  - Run transforms in order: constant propagation → call graph edges → overflow → termination. All four always run; diagnostics accumulate rather than short-circuit.
- **Verification Criteria**:
  - A program with overflow and termination issues produces diagnostics from both transforms in a single `Apply` call.

---

## Plan 14 — Silver IR (Go Structs)
Parent plan: [.plans/14-silver-ast.md](.plans/14-silver-ast.md)
#### 14.1: Silver AST Go Representation
- **Objective**: Define Go AST structures representing Viper Silver constructs.
- **Deliverables**:
  - Create `internal/silver/ast.go` detailing structs representing declarations and expressions.
- **Verification Criteria**:
  - Ensure Silver AST representation compiles.

#### 14.2: Silver Printer Setup
- **Objective**: Configure base visitor output structure for printing.
- **Deliverables**:
  - Create base output printer framework.
- **Verification Criteria**:
  - Verify setup compiles.

#### 14.3: Silver AST Printer
- **Objective**: Implement code formatting rendering valid `.vpr` code representation.
- **Deliverables**:
  - Create `internal/silver/printer.go` implementation.
- **Verification Criteria**:
  - Ensure formatted files match expected Viper structures.

---

## Plan 15 — Subprocess Lifecycle
Parent plan: [.plans/15-jni-setup.md](.plans/15-jni-setup.md)
#### 15.1: Subprocess Management
- **Objective**: Start, monitor, and close JVM `SilverServer` subprocesses.
- **Deliverables**:
  - Create JVM subprocess execution drivers.
- **Verification Criteria**:
  - Ensure JVM child process executes and stops cleanly on command.

#### 15.2: Shared Backend Structs
- **Objective**: Define output models for verification outcomes.
- **Deliverables**:
  - Create `internal/backend/types.go` defining verification outcomes and error logs.
- **Verification Criteria**:
  - Verify shared structures compile cleanly.

---

## Plan 15b — Goroutine Worker Pool
Parent plan: [.plans/15b-worker-pool-expansion.md](.plans/15b-worker-pool-expansion.md)
#### 15b.1: Goroutine Task Queue
- **Objective**: Manage job channels and distribute jobs to multiple goroutine workers.
- **Deliverables**:
  - Implement queue worker loops and dispatch channels.
- **Verification Criteria**:
  - Verify multiple jobs run in parallel across the pool.

#### 15b.2: Worker Process Lifespans
- **Objective**: Ensure worker goroutines manage dedicated JVM subprocess processes.
- **Deliverables**:
  - Assign a separate subprocess process to each worker and guarantee cleanups on pool stop.
- **Verification Criteria**:
  - Assert that stopping the pool kills all associated JVM subprocesses.

---

## Plan 16 — Silver Protobuf Serializer
Parent plan: [.plans/16-silver-jni-builder.md](.plans/16-silver-jni-builder.md)
#### 16.1: Protobuf Generator
- **Objective**: Serialize the Go Silver AST into a Protobuf message payload.
- **Deliverables**:
  - Create `internal/backend/silver/serializer.go`.
- **Verification Criteria**:
  - Assert serialized outputs match the schema specs.

#### 16.2: Stable ID Generation
- **Objective**: Generate unique incremental IDs for Silver AST nodes.
- **Deliverables**:
  - Implement stable global incremental ID assigner.
- **Verification Criteria**:
  - Ensure assigned IDs are unique.

#### 16.3: Error Backtranslation NodeMap
- **Objective**: Maintain the registry mapping node IDs back to original AST nodes.
- **Deliverables**:
  - Construct backtranslation `NodeMap` mapping IDs to Go structures.
- **Verification Criteria**:
  - Verify ID resolution returns original AST reference.

---

## Plan 16b — Silver Program Chopper
Parent plan: [.plans/16b-silver-chopper.md](.plans/16b-silver-chopper.md)
#### 16b.1: Vertex Extraction & Graph Assembly
- **Objective**: Generate dependency graph nodes for Silver methods and types.
- **Deliverables**:
  - Create `internal/silver/vertices.go`.
- **Verification Criteria**:
  - Verify graph dependencies match on mock structures.

#### 16b.2: Chopper Partitioning
- **Objective**: Partition members into independent, self-contained sub-programs.
- **Deliverables**:
  - Create `internal/silver/chopper.go`.
- **Verification Criteria**:
  - Assert sub-programs cover dependencies.

#### 16b.3: Greedy Merging
- **Objective**: Group sub-programs greedily to limit verification overhead.
- **Deliverables**:
  - Create merging logic under penalty parameters.
- **Verification Criteria**:
  - Verify that greedy merging reduces sub-program counts within bounds.

---

## Plan 17 — Silicon gRPC Backend
Parent plan: [.plans/17-silicon-backend.md](.plans/17-silicon-backend.md)
#### 17.1: gRPC Client Communication Setup
- **Objective**: Connect to `SilverServer` over gRPC and post serialized program payloads.
- **Deliverables**:
  - Implement client communication channels.
- **Verification Criteria**:
  - Ensure client connects and retrieves replies from the JVM server.

#### 17.2: Silicon Result Conversion
- **Objective**: Transform gRPC responses into structured Go verification structures.
- **Deliverables**:
  - Parse server response records into `VerificationResult` and errors.
- **Verification Criteria**:
  - Verify correct mapping of error positions and descriptions.

---

## Plan 17b — Parallel Goroutine Workers
Parent plan: [.plans/17b-parallel-workers.md](.plans/17b-parallel-workers.md)
#### 17b.1: Fan-out Dispatcher
- **Objective**: Distribute sub-program verification runs over the worker pool.
- **Deliverables**:
  - Create `internal/backend/subprocess/dispatch.go`.
- **Verification Criteria**:
  - Verify parallel verification runs execute concurrently.

#### 17b.2: Error Deduplication
- **Objective**: Filter redundant error logs produced during parallel execution.
- **Deliverables**:
  - Create filters in `internal/backend/dedup.go`.
- **Verification Criteria**:
  - Assert duplicate error messages from identical source lines report once.

---

## Plan 18 — Carbon Backend
Parent plan: [.plans/18-carbon-backend.md](.plans/18-carbon-backend.md)

> **DEFERRED** — explicitly deferred per D12 in `.plans/DECISIONS.md`. Do not implement.
> Sub-tasks 18.1 and 18.2 are on hold until Carbon is un-deferred. When un-deferred, add a
> `Backend` interface to `internal/backend/types.go` before implementing these sub-tasks.

---

## Plan 19 — Translator Core & Context
Parent plan: [.plans/19-translator-core.md](.plans/19-translator-core.md)
#### 19.1: Translator Context
- **Objective**: Implement state storage and lookup tables for translation passes.
- **Deliverables**:
  - Create `internal/translator/context.go`.
- **Verification Criteria**:
  - Verify context saves and reads state keys.

#### 19.2: Name Mangler
- **Objective**: Implement unique name-mapping algorithms.
- **Deliverables**:
  - Create `internal/translator/mangle.go`.
- **Verification Criteria**:
  - Verify name outputs are repeatable and unique.

#### 19.3: Translator Driver Skeleton
- **Objective**: Implement orchestrator driving translation passes.
- **Deliverables**:
  - Create `internal/translator/translator.go`.
- **Verification Criteria**:
  - Verify skeleton code outputs valid Silver programs.

#### 19.4: Tuple Domain Creator
- **Objective**: Build Z3-triggerable Silver domains for tuples of varying sizes.
- **Deliverables**:
  - Implement dynamic tuple domain generation with constructor triggers.
- **Verification Criteria**:
  - Assert that generated tuple domains define proper projection functions and axioms.

---

## Plan 20 — Encoding: Primitive Types
Parent plan: [.plans/20-encoding-primitives.md](.plans/20-encoding-primitives.md)
#### 20.1: Integer Types & Arithmetic
- **Objective**: Translate integers, arithmetic operations, division, and modulo.
- **Deliverables**:
  - Create `internal/translator/encodings/primitives.go`.
- **Verification Criteria**:
  - Verify arithmetic translations generate correct Silver math nodes.

#### 20.2: Boolean & Logical Operations
- **Objective**: Translate boolean variables and logical gates.
- **Deliverables**:
  - Translate logical operations.
- **Verification Criteria**:
  - Verify boolean evaluation mappings.

#### 20.3: String Domain & Concat
- **Objective**: Model strings and concatenate functions.
- **Deliverables**:
  - Create `internal/translator/encodings/strings.go`.
- **Verification Criteria**:
  - Verify string operations compile to valid domain calls.

---

## Plan 21 — Encoding: Structs & Fields
Parent plan: [.plans/21-encoding-structs.md](.plans/21-encoding-structs.md)
#### 21.1: Struct Domain & Fields Mapping
- **Objective**: Map struct definitions to Silver domains and fields.
- **Deliverables**:
  - Create `internal/translator/encodings/structs.go`.
- **Verification Criteria**:
  - Verify fields are mapped to correct Silver representation fields.

#### 21.2: Struct Reads & Writes
- **Objective**: Translate struct field read and write statements.
- **Deliverables**:
  - Translate field selectors.
- **Verification Criteria**:
  - Verify field reads translate to correct heap operations.

#### 21.3: Struct Literals
- **Objective**: Translate struct instantiation literals.
- **Deliverables**:
  - Translate structural literal assignments.
- **Verification Criteria**:
  - Verify literal definitions allocate heap structures.

---

## Plan 22 — Encoding: Pointers
Parent plan: [.plans/22-encoding-pointers.md](.plans/22-encoding-pointers.md)
#### 22.1: Pointer Type & Allocation
- **Objective**: Encode pointer types, nil pointers, and allocations via `new`.
- **Deliverables**:
  - Create `internal/translator/encodings/pointers.go`.
- **Verification Criteria**:
  - Assert heap allocation translates to new instance allocations.

#### 22.2: Pointer Dereferencing
- **Objective**: Translate read and write operations over pointer targets.
- **Deliverables**:
  - Translate dereference selectors.
- **Verification Criteria**:
  - Assert dereferencing compiles to pointer target lookups.

#### 22.3: Shared & Exclusive Heap Permissions
- **Objective**: Translate access permission constraints for shared/exclusive pointer states.
- **Deliverables**:
  - Implement permissions mapping for pointers.
- **Verification Criteria**:
  - Verify pointer lookups are gated by correct access verification checks.

---

## Plan 23 — Encoding: Slices & Arrays
Parent plan: [.plans/23-encoding-slices.md](.plans/23-encoding-slices.md)
#### 23.1: Array Representation
- **Objective**: Translate fixed Go arrays and check indices are bounded.
- **Deliverables**:
  - Create `internal/translator/encodings/arrays.go`.
- **Verification Criteria**:
  - Assert out-of-bounds indexing raises verification failures.

#### 23.2: Slice Domain
- **Objective**: Establish the Silver slice representation model.
- **Deliverables**:
  - Create `internal/translator/encodings/slices.go`.
- **Verification Criteria**:
  - Verify slice type variables compile.

#### 23.3: Slice Operations
- **Objective**: Translate indexing, sub-slicing, length, and capacity attributes.
- **Deliverables**:
  - Translate slice selectors and expressions.
- **Verification Criteria**:
  - Verify sub-slicing compiles to backing array offsets.

#### 23.4: Append Operation
- **Objective**: Translate the slice `append` built-in function.
- **Deliverables**:
  - Implement append translation.
- **Verification Criteria**:
  - Assert append operations verify against backing array limits.

---

## Plan 24 — Encoding: Maps
Parent plan: [.plans/24-encoding-maps.md](.plans/24-encoding-maps.md)
#### 24.1: Map Domain
- **Objective**: Establish Map domains and type mappings.
- **Deliverables**:
  - Create `internal/translator/encodings/maps.go`.
- **Verification Criteria**:
  - Verify map declarations compile.

#### 24.2: Map Lookup & Assignment
- **Objective**: Translate map lookups and key insertions.
- **Deliverables**:
  - Translate assignments and indexing.
- **Verification Criteria**:
  - Assert lookups resolve key assignments.

#### 24.3: Map Delete & Comma-ok
- **Objective**: Translate deletion operations and map comma-ok lookup assertions.
- **Deliverables**:
  - Translate delete calls and comma-ok expressions.
- **Verification Criteria**:
  - Verify correct return values for map accesses.

---

## Plan 25 — Encoding: Interfaces
Parent plan: [.plans/25-encoding-interfaces.md](.plans/25-encoding-interfaces.md)
#### 25.1: Interface Domain & Boxing
- **Objective**: Encode Go interfaces, boxed concrete types, and dynamic types.
- **Deliverables**:
  - Create `internal/translator/encodings/interfaces.go`.
- **Verification Criteria**:
  - Assert dynamic checks on boxed objects match type definitions.

#### 25.2: Subtyping Axioms
- **Objective**: Define interface subtyping rules in the Silver domain.
- **Deliverables**:
  - Implement subtyping axioms.
- **Verification Criteria**:
  - Verify interface assignments are validated.

#### 25.3: Dynamic Dispatch
- **Objective**: Translate method calls over interface variables.
- **Deliverables**:
  - Translate method calls on dynamic targets.
- **Verification Criteria**:
  - Verify methods execute dynamic checks.

#### 25.4: Type Assertions & Switches
- **Objective**: Translate type assertions and type switch expressions.
- **Deliverables**:
  - Translate switches and assertions.
- **Verification Criteria**:
  - Assert invalid type assertions fail verification.

---

## Plan 26 — Encoding: Permissions & Predicates
Parent plan: [.plans/26-encoding-permissions.md](.plans/26-encoding-permissions.md)
#### 26.1: Access Permissions
- **Objective**: Translate basic heap access permissions.
- **Deliverables**:
  - Create `internal/translator/encodings/permissions.go`.
- **Verification Criteria**:
  - Verify permission checks generate correct `acc` constraints.

#### 26.2: Predicate Definitions
- **Objective**: Translate predicate specifications and declarations.
- **Deliverables**:
  - Translate predicates to Silver definitions.
- **Verification Criteria**:
  - Verify predicates compile.

#### 26.3: Fold/Unfold
- **Objective**: Translate predicate fold and unfold commands.
- **Deliverables**:
  - Translate fold/unfold blocks.
- **Verification Criteria**:
  - Assert folds and unfolds update permissions.

#### 26.4: Fractional & Wildcard Permissions
- **Objective**: Translate permission limits (constants, wildcards).
- **Deliverables**:
  - Translate fractions.
- **Verification Criteria**:
  - Verify correctness of wildcard verification.

#### 26.5: Magic Wands
- **Objective**: Translate magic wands and associated apply/package operations.
- **Deliverables**:
  - Translate wands.
- **Verification Criteria**:
  - Assert wands apply permissions correctly.

---

## Plan 27 — Encoding: Methods & Functions
Parent plan: [.plans/27-encoding-methods.md](.plans/27-encoding-methods.md)
#### 27.1: Function Signatures & Specs
- **Objective**: Translate signatures and pre/postconditions.
- **Deliverables**:
  - Create `internal/translator/encodings/methods.go` and `functions.go`.
- **Verification Criteria**:
  - Verify mapped signatures match specifications.

#### 27.2: Variable Scopes & Returns
- **Objective**: Translate variable definitions, declarations, and return registers.
- **Deliverables**:
  - Translate declarations and returns.
- **Verification Criteria**:
  - Assert values map to return registers.

#### 27.3: Control Flow
- **Objective**: Translate block execution sequences, loops, and conditions.
- **Deliverables**:
  - Translate statements.
- **Verification Criteria**:
  - Verify statements compile correctly.

#### 27.4: Closure Scopes
- **Objective**: Translate closures capturing local variables.
- **Deliverables**:
  - Translate closure definitions.
- **Verification Criteria**:
  - Verify captured variables resolve.

#### 27.5: Known Limitations Log
- **Objective**: Append closure and loop limitations to limitations log.
- **Deliverables**:
  - Append details to `KNOWN_LIMITATIONS.md`.
- **Verification Criteria**:
  - Verify file contains limitations logs.

---

## Plan 28 — Encoding: Channels
Parent plan: [.plans/28-encoding-channels.md](.plans/28-encoding-channels.md)
#### 28.1: Channel Representation & Make
- **Objective**: Translate channel types and allocations via `make`.
- **Deliverables**:
  - Create `internal/translator/encodings/channels.go`.
- **Verification Criteria**:
  - Verify channel types compile.

#### 28.2: Send/Receive Permissions
- **Objective**: Translate send and receive channel actions and permissions transfer.
- **Deliverables**:
  - Translate sends and receives.
- **Verification Criteria**:
  - Assert sends transfer resource permissions.

#### 28.3: Select Statement
- **Objective**: Translate select blocks, default choices, and channels.
- **Deliverables**:
  - Translate select cases.
- **Verification Criteria**:
  - Verify select structures compile.

#### 28.4: Channel Close
- **Objective**: Translate channel close states and verify postconditions.
- **Deliverables**:
  - Translate close calls.
- **Verification Criteria**:
  - Assert sends after close fail verification.

---

## Plan 29 — Encoding: Ghost ADTs & Mathematical Types
Parent plan: [.plans/29-encoding-adts.md](.plans/29-encoding-adts.md)
#### 29.1: ADT Domains & Matches
- **Objective**: Translate user-defined ADTs, constructor functions, and match expressions.
- **Deliverables**:
  - Create `internal/translator/encodings/adts.go`.
- **Verification Criteria**:
  - Verify match expressions map to case blocks.

#### 29.2: Mathematical Sequences
- **Objective**: Translate ghost sequence types and actions.
- **Deliverables**:
  - Create `internal/translator/encodings/mathcollections.go`.
- **Verification Criteria**:
  - Verify sequence lookups compile.

#### 29.3: Sets & Multisets
- **Objective**: Translate set and multiset types.
- **Deliverables**:
  - Translate sets.
- **Verification Criteria**:
  - Verify set union operations.

#### 29.4: Dictionaries & Options
- **Objective**: Translate dictionary types and Option wrappers.
- **Deliverables**:
  - Translate options and dict structures.
- **Verification Criteria**:
  - Verify dictionary insertions.

---

## Plan 30 — Encoding: Go Generics
Parent plan: [.plans/30-encoding-generics.md](.plans/30-encoding-generics.md)

> **DEFERRED** — not an active pipeline component until the plan 36 audit confirms that
> Go-Gobra's own source uses generics. Before scheduling sub-tasks 30.1–30.3, run:
> ```
> grep -rE '^\s*(func|type)\s+\w+\s*\[' gobra-go/internal/ gobra-go/cmd/
> ```
> If the grep finds no hits, this plan remains deferred through self-hosting.

---

## Plan 31 — Encoding: Built-in Stubs
Parent plan: [.plans/31-encoding-builtins.md](.plans/31-encoding-builtins.md)
#### 31.1: Stub Porting & Embedding
- **Objective**: Copy stdlib ghost stubs and bundle them using embed directives.
- **Deliverables**:
  - Port `.gobra` files into `internal/frontend/stubs/`.
- **Verification Criteria**:
  - Assert stub files are embedded in the binary.

#### 31.2: Virtual Stub Package Resolving
- **Objective**: Integrate virtual stub resolving inside the package loader.
- **Deliverables**:
  - Resolve standard library imports using embedded stubs.
- **Verification Criteria**:
  - Verify stdlib calls resolve contracts from stubs.

---

## Plan 32 — Reporter & Error Mapping
Parent plan: [.plans/32-reporter.md](.plans/32-reporter.md)
#### 32.1: NodeMap Position Lookup
- **Objective**: Extract Go file positions from Silver node IDs using the NodeMap.
- **Deliverables**:
  - Create `internal/reporting/reporter.go` and `tags.go`.
- **Verification Criteria**:
  - Assert error mapping resolves original Go positions.

#### 32.2: Text Output Formatter
- **Objective**: Format diagnostic errors into human-readable text prints.
- **Deliverables**:
  - Implement text print formatters.
- **Verification Criteria**:
  - Verify console print formatting.

#### 32.3: JSON Diagnostic Formatter
- **Objective**: Format diagnostics as structured JSON records.
- **Deliverables**:
  - Implement JSON output formatters.
- **Verification Criteria**:
  - Verify structured JSON output match schemas.

---

## Plan 33 — CLI & Entry Point
Parent plan: [.plans/33-cli.md](.plans/33-cli.md)
#### 33.1: Flag Parsing & Configuration
- **Objective**: Implement CLI flag parsing and configuration rules.
- **Deliverables**:
  - Create `cmd/gobra/main.go` and `internal/config/config.go`.
- **Verification Criteria**:
  - Verify CLI flags parse.

#### 33.2: E2E Pipeline Orchestrator
- **Objective**: Wire up and execute compilation pipeline steps in sequence.
- **Deliverables**:
  - Create `internal/pipeline/pipeline.go`.
- **Verification Criteria**:
  - Verify compiler processes, resolves, and verifies code, exiting with correct status codes.

---

## Plan 34 — Test Infrastructure
Parent plan: [.plans/34-test-infrastructure.md](.plans/34-test-infrastructure.md)
#### 34.1: Test Discovery & Header Parsing
- **Objective**: Scan directories and parse test directives.
- **Deliverables**:
  - Create `internal/testing/runner.go`.
- **Verification Criteria**:
  - Assert test discovery identifies target files and directives.

#### 34.2: Expected Outcome Assertions Evaluator
- **Objective**: Assert verification outcomes match expected declarations.
- **Deliverables**:
  - Compare verifier outcomes against `//@ expectedError` annotations.
- **Verification Criteria**:
  - Verify tests with correct expectations pass and unmatched errors fail.

#### 34.3: Skip-List Support & Sentinel Output
- **Objective**: Let the runner treat known-failing tests as expected failures instead of CI breaks, and report stale/regressed skip entries in a format plan 35's `prune-skips` tool can consume.
- **Deliverables**:
  - Add `SkipConfig` struct (`File string`, `ValidSlugs map[string]bool`) to `internal/testing/runner.go`.
  - Implement `loadSkipList` to parse `skip.txt`, validate each entry's reason slug against `ValidSlugs`, and fail fast (return `nil, err`) on any unrecognised slug before any tests run.
  - Run skip-listed tests in "expected-to-fail" mode and emit the sentinel lines `UNEXPECTED_PASS: <path>` (skip-listed test passed) and `UNEXPECTED_FAIL: <path>` (non-skip-listed test failed) to stdout, in addition to normal `go test` output.
- **Verification Criteria**:
  - Verify `loadSkipList` returns a startup error and runs zero tests when `skip.txt` contains an unrecognised slug.
  - Assert a skip-listed test that unexpectedly passes emits `UNEXPECTED_PASS:` and counts as a CI failure; a skip-listed test that fails as expected is reported as skipped, not counted against the pass rate.

#### 34.4: HasGenericDecl Predicate
- **Objective**: Provide the pure AST predicate that plan 35 uses to know which regression tests exercise generics, replacing the superseded grep-based approach.
- **Deliverables**:
  - Implement `HasGenericDecl(f *ast.File) bool` in `internal/testing/runner.go`, plus the ghost predicates `funcDeclIsGeneric`, `genDeclHasGenericSpec`, and `typeSpecIsGeneric` it depends on.
  - Write the Gobra postcondition specified in parent plan 34's §Verification Specifications (C9), item 1.
- **Verification Criteria**:
  - Verify `HasGenericDecl` returns `true` iff the file contains at least one generic function or type declaration, on files with and without generic declarations.
  - Confirm the postcondition is present and verifies cleanly — this spec is required to pass before plan 37's self-hosting cut-over.

#### 34.5: Regression Test Entrypoint & Docs
- **Objective**: Plug the runner into `go test` and document how to add new regression test cases.
- **Deliverables**:
  - Create `tests/regression_test.go`, constructing the runner with the `SkipConfig` (supplying `ValidSlugs` from plan 35) and running it as `TestMain` or table-driven tests so `go test ./...` runs the regression suite.
  - Write documentation on how to add new test cases.
- **Verification Criteria**:
  - Run `go test ./tests/...` and verify it discovers and executes the regression suite end to end.

---

## Plan 35 — Regression Test Suite
Parent plan: [.plans/35-regression-suite.md](.plans/35-regression-suite.md)
#### 35.1: Directory Setup & Skip List
- **Objective**: Migrate tests and initialize skip lists.
- **Deliverables**:
  - Setup skip lists `tests/testdata/skip.txt`.
- **Verification Criteria**:
  - Confirm runner uses skips.

#### 35.2: CI Test Suite Integration
- **Objective**: Run regression checks inside Github Actions.
- **Deliverables**:
  - Configure CI runner sweeps.
- **Verification Criteria**:
  - Verify CI sweeps execute checks.

#### 35.3: prune-skips Tool
- **Objective**: Identify obsolete skip list records.
- **Deliverables**:
  - Implement `prune-skips` tool.
- **Verification Criteria**:
  - Verify tool lists passing tests that are skip-listed.

---

## Plan 36 — Self-Hosting: Write Specifications
Parent plan: [.plans/36-self-hosting-annotations.md](.plans/36-self-hosting-annotations.md)
#### 36.1: AST & Frontend Annotations
- **Objective**: Annotate AST and frontend structures with specifications.
- **Deliverables**:
  - Write specs inside AST and frontend packages.
- **Verification Criteria**:
  - Verify packages verify using Scala Gobra.

#### 36.2: Checker & Translator Annotations
- **Objective**: Annotate checker and translator structures with specifications.
- **Deliverables**:
  - Write specs inside checker and translator packages.
- **Verification Criteria**:
  - Verify checkers verify using Scala Gobra.

#### 36.3: Backend Annotations
- **Objective**: Annotate backend structures with specifications.
- **Deliverables**:
  - Write specs inside backend packages.
- **Verification Criteria**:
  - Verify backend verifies using Scala Gobra.

---

## Plan 37 — Self-Hosting: Achieve Verification
Parent plan: [.plans/37-self-hosting-verify.md](.plans/37-self-hosting-verify.md)
#### 37.1: Go-Gobra Self-Verification Execution
- **Objective**: Run Go-Gobra verifier on itself.
- **Deliverables**:
  - Resolve verification failures.
- **Verification Criteria**:
  - Assert self-verification succeeds with zero errors.

#### 37.2: Self-Verify Job CI
- **Objective**: Integrate self-verification into push actions.
- **Deliverables**:
  - Add self-verification job.
- **Verification Criteria**:
  - Verify CI verifies codebase successfully.

#### 37.3: SELF_HOSTING.md Compilation
- **Objective**: Document verified properties, trust boundaries, and details.
- **Deliverables**:
  - Write `SELF_HOSTING.md`.
- **Verification Criteria**:
  - Ensure documentation compiles trust structures.

---
