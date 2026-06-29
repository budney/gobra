# 07 — Package Resolver

## Objective

Implement multi-package resolution: given a set of entry-point files or package paths, locate
and load all transitively-imported packages (including Gobra ghost packages and built-in stubs),
producing a complete set of parsed frontend ASTs ready for type checking.

## Scope

**In scope:**
- Resolve import paths to filesystem directories using Go module conventions (`go list`, GOPATH,
  or module-aware resolution)
- Load and parse each resolved package (invoking 06 + 04 + 05 for each file)
- Handle Gobra-specific imports (ghost packages, built-in stub packages from
  `src/main/resources/`)
- Detect and report import cycles
- Support the `--include` / `--exclude` flags from the current Gobra CLI

**Out of scope:**
- Type checking the resolved packages (08–10)
- Encoding built-in stubs (31-encoding-builtins.md)

## Dependencies

- [32a-diagnostics.md](32a-diagnostics.md) — `Diagnostic` type returned by `Resolve`
- [03-frontend-ast.md](03-frontend-ast.md) — `PPackage`, `PFile`, `PProgram` types assembled by this plan
- [04-go-parser.md](04-go-parser.md) — parse each package's files
- [05-annotation-parser.md](05-annotation-parser.md) — `ParseAnnotation` called in step 3 of the per-file coordination sequence
- [06-gobrafier.md](06-gobrafier.md) — preprocess `.go` files before parsing

## Reference: Current Gobra

- `src/main/scala/viper/gobra/frontend/PackageResolver.scala` — canonical implementation
- `src/main/scala/viper/gobra/frontend/Source.scala` — source file abstraction
- `src/main/resources/` — built-in stub packages (these must be bundled with Go-Gobra)

## Key Implementation Notes

- **`golang.org/x/tools/go/packages`**: Use this library for module-aware package loading
  rather than reimplementing module resolution. Note: it invokes `go list` under the hood,
  which requires a Go toolchain to be installed at runtime (`go` in `$PATH`). This is
  acceptable for a development-time verifier. Document this runtime requirement prominently:
  "Go-Gobra requires a Go toolchain at runtime for package resolution (`go list`)." Fail fast
  with a clear message if `go` is not found in `$PATH`. Note this also means Go-Gobra cannot
  be used in environments where only a pre-compiled binary is deployed without a Go toolchain
  (e.g., some CI containers) — document this as a known limitation.
- **Bootstrap / self-hosting implication**: Because Go-Gobra calls `go list` at runtime, a
  pre-built Go-Gobra binary cannot verify Go programs on a system where the Go toolchain is
  not installed. This means the self-hosting CI job (plan 37) requires Go to be installed
  on the CI runner — not just the Go-Gobra binary. This is expected (Go-Gobra is a
  development-time tool, not a production runtime), but document it in the CI workflow so
  the requirement is explicit. The bootstrap sequence for plan 37 is therefore:
  build Go-Gobra with Go → run Go-Gobra (requires Go in PATH) → verify Go-Gobra source.
- **Topological ordering**: The resolver must produce packages in dependency order (a
  topological sort of the import graph). The type checker (10) requires that all imported
  packages are type-checked before the importing package. Detect and report import cycles as
  an error; do not proceed to type checking if a cycle exists.
- **Error accumulation**: If one package fails to parse or resolve, record the error and
  continue resolving other packages where possible. Report all errors at the end rather than
  stopping at the first failure.
- **Stub package resolution (resolved):** In the Scala Gobra, stub packages use *standard Go
  import paths* (e.g., `"sync"`, `"fmt"`) and shadow the real stdlib: the resolver searches
  `stubs/` directories before GOPATH, so `import "sync"` resolves to `stubs/sync/*.gobra`
  rather than the real Go `sync` package.

  The Go-Gobra equivalent requires a custom `types.Importer` that checks the embedded stubs
  directory *first* for every import path. If `stubs/<importPath>/` contains any `.gobra`
  files, it loads and returns those (as a virtual `*types.Package` built from the stub's
  exported declarations); otherwise it falls through to `go/packages` for real Go packages.

  **Ownership: plan 10 implements and owns this custom importer** as a named deliverable. Plan
  07 depends on it at runtime (the package resolver calls `go/types.Check` with this importer),
  but does not implement it. Plan 10 must be completed before plan 07's multi-package path
  can be exercised end-to-end. The importer is shared between plan 07 (for resolution) and
  plan 10 (for type-checking); plan 10 exports it from `internal/info/importer.go`.

  The `BuiltInImport` package (Gobra's own built-in declarations, separate from stdlib stubs)
  is resolved via a special sentinel import path `"gobra/builtin"` that always maps to the
  embedded `builtin/` directory — never to a real Go package.

- Built-in stubs (Go stdlib ghost specs) ship as embedded resources in the Go binary using
  `//go:embed`; they are parsed the same way as user packages.
- The resolver produces a `PackageInfo` list in topological order: each entry is
  `{ImportPath string, Package *frontend.PPackage, Files []*frontend.PFile, Deps []string}`.
  `Package` is assembled from `Files` after the 4-step per-file sequence completes for all
  files in the package; it is the `*frontend.PPackage` value that plan 08 (`Check`) and plan
  12 (`Desugar`) consume. `Files` is retained in `PackageInfo` for diagnostic tools and
  selective re-parsing; `Package` is the authoritative assembled form.

## Deliverables

- `internal/frontend/packageresolver.go` — main entry point:
  ```go
  // Resolve loads, preprocesses, and parses all packages transitively imported
  // by the given entry-point paths (file paths or import paths). Returns packages
  // in topological order (dependencies before dependents) and all accumulated
  // diagnostics. A non-empty []Diagnostic does not prevent returning a partial
  // result; callers (plan 33 pipeline) abort if any diagnostics are present.
  func Resolve(inputs []string, cfg *ResolverConfig) ([]*PackageInfo, []Diagnostic)
  ```
  `ResolverConfig` includes: stub directory (embedded), Go module root, any `--include`/
  `--exclude` patterns, and a reference to the type-checked package cache for plan 10.
- `PackageInfo` type:
  ```go
  type PackageInfo struct {
      ImportPath string
      Package    *frontend.PPackage  // assembled from Files; nil if parsing failed
      Files      []*frontend.PFile   // individual file ASTs (used by diagnostic tools)
      Deps       []string            // import paths of direct dependencies
  }
  ```
  `Resolve` assembles `Package` from the parsed `Files` after completing the 4-step per-file
  sequence. The `PPackage` contains the merged file list in declaration order. Plan 08's
  `Check(pkg *frontend.PPackage, ...)` and plan 12's `Desugar(pkg *frontend.PPackage, ...)`
  both receive `info.Package` from the caller (plan 33 `pipeline.go`).

  **Field usage rule**: `PackageInfo.Package` is the **authoritative** assembled form for all
  type-checking, desugaring, and translation pipeline stages. `PackageInfo.Files` is retained
  only for diagnostic source-position lookup and selective re-parsing. **Using `Files` directly
  for type-checking or desugaring is a bug** — the individual `PFile` values do not have merged
  ghost declarations, and `GhostDecls` may be scattered across files before plan 07's assembly
  step populates `Package`. Downstream plans must use `info.Package`, not `info.Files`.
- `ResolverConfig` type (see above)
- Embedded built-in stub files (`internal/frontend/stubs/` with `//go:embed`)
- Coordination note: for each file, `Resolve` runs a **4-step sequence**:
  1. `Gobrafy(rawBytes, filename)` (plan 06) → preprocessed `[]byte` (no temp file written)
  2. `ParseFile(fset, filename, preprocessedBytes)` (plan 04) → `(*PFile, []Diagnostic)` —
     `pfile.BlockAnnotations` is fully populated on return; `PBlockStmt` nodes contain Go
     statements only at this point. `fset` is a `*token.FileSet` created once per package
     before the file loop and passed to every `ParseFile` call in that package.
  3. For each `key, annotations := range pfile.BlockAnnotations`: call
     `ParseAnnotation(raw.Text, raw.Pos, key == nil)` (plan 05) → `(nodes []PNode, decls []PDecl, diags)`.
     - `key == nil` signals file-scope: `decls` contains ghost declaration nodes (`PAdtType`,
       `PGhostFunc`, `PPredDecl`) which are appended directly to `pfile.GhostDecls []PDecl`.
     - `key != nil` (block-scope): `nodes` contains spec clause and ghost statement nodes;
       pass them to `MergeGhostStatements` for step 4.
  4. `MergeGhostStatements(pfile, ghostNodes)` → interleaves ghost statement nodes into the
     correct locations in the `PFile`. Two separate routing rules apply:

     **Rule A — loop invariant routing**: Any ghost node whose annotation keyword is
     `invariant` (i.e., the raw annotation text parsed by plan 05 begins with `invariant`)
     must be appended to `PForStmt.Spec.Invariants` (or `PRangeStmt.Spec.Invariants`) on the
     `PForStmt` or `PRangeStmt` node (via the embedded `PLoopSpec` field) whose `Pos()`
     immediately follows the invariant's `Pos()` in the enclosing `PBlockStmt.Stmts`. Do NOT
     insert invariant nodes into `PBlockStmt.Stmts` — they are not statements. If no
     `PForStmt`/`PRangeStmt` immediately follows, emit a diagnostic:
     `"invariant annotation not attached to a for-loop"`.

     **Rule B — all other ghost nodes**: Insert into `PBlockStmt.Stmts` in source order by
     `Pos()`, interleaved with existing `PGoStmt` nodes.

  `MergeGhostStatements` is a pure function in `internal/frontend/packageresolver.go`. It
  first separates ghost nodes by type (invariants vs. other), routes invariants to their
  enclosing for-loop via `PForStmt.Spec.Invariants` (Rule A), then merges the remainder into
  the block's statement list in `Pos()` order (Rule B).

  File-scope `//@ ` comments are stored under the **nil key** in `pfile.BlockAnnotations`
  (plan 04 convention). `ParseAnnotation(isFileScope=true)` returns their parsed declarations
  directly in `decls []PDecl`. `MergeGhostStatements` must check `if key == nil` before the
  merge loop and append `decls` to `pfile.GhostDecls` rather than routing through Rule A/B.
- Tests: resolve a multi-package example from `src/test/resources/regressions/` and verify
  the correct set of files is loaded in dependency order

## Explicitly Unsupported Constructs

The following constructs are absent from Scala Gobra's ANTLR grammar and never reached the
Scala type checker or desugarer. Because Go-Gobra uses `go/parser` + `go/types`, they are
now visible and must be explicitly rejected with user-facing diagnostics rather than relying
on a downstream panic.

**`import "unsafe"`**: Reject any import of the `unsafe` package during resolution, before
type checking begins. The custom `types.Importer` (plan 10) should return an error when asked
to import `"unsafe"`. Additionally, `Resolve` should scan the import graph and accumulate a
diagnostic for any file that imports `"unsafe"` directly or transitively. Error message:
`"import of package \"unsafe\" is not supported by Gobra"`.

## Resolved Questions

**Lazy vs. eager (resolved):** Eager — load and parse all transitively imported packages at
startup before type checking begins. This matches the Scala Gobra behavior and is simpler to
implement: one linear phase of loading, one phase of type checking in topological order.
If profiling later shows that loading unused packages is a bottleneck (e.g., in `--parseOnly`
or `--typeCheckOnly` mode), add lazy loading as an optimization at that time.

**Partially-annotated packages:** Treat all files in a package uniformly — parse and type-check
them all together, regardless of whether individual files contain Gobra annotations. Files with
no `//@ ` comments simply produce no ghost AST nodes. This is the simplest approach and
matches the current Gobra behavior.

## Verification Specifications (C9)

`Resolve` orchestrates the 4-step per-file pipeline and produces a topologically-sorted
package list. The following Gobra annotations will be written into
`internal/frontend/packageresolver.go` and verified before this plan is considered complete.

1. **`Resolve` output-safety postcondition** — returns a non-nil result list iff diagnostics
   are empty (partial results are not returned — on any error, return nil + diagnostics):
   ```go
   //@ requires inputs != nil && cfg != nil
   //@ ensures  (result != nil) == (len(diags) == 0)
   //@ ensures  diags != nil
   //@ decreases
   func Resolve(inputs []string, cfg *ResolverConfig) (result []*PackageInfo, diags []Diagnostic)
   ```

2. **Topological order postcondition** — every package in the result slice appears after all
   its dependencies:
   ```go
   //@ ensures forall i, j int ::
   //@     0 <= i && i < j && j < len(result) ==>
   //@     !result[j].dependsOn(result[i].ImportPath)
   ```
   (`dependsOn` is a ghost predicate: `p.dependsOn(path)` holds iff `path` is in `p.Deps`
   or in the transitive deps of any element of `p.Deps`.)

3. **`MergeGhostStatements` nil-key precondition** — the nil-key entry in `BlockAnnotations`
   is processed before the block-keyed entries; `pfile.GhostDecls` is populated from the
   nil-key decls before `MergeGhostStatements` merges block-scope nodes:
   ```go
   //@ requires pfile != nil && acc(pfile.BlockAnnotations)
   //@ ensures  acc(pfile.GhostDecls)
   //@ decreases
   func MergeGhostStatements(pfile *frontend.PFile, blockNodes map[*frontend.PBlockStmt][]frontend.PNode) []Diagnostic
   ```

4. **Termination** — `Resolve` terminates because the import graph is finite and acyclic (cycles
   are detected and reported as errors before recursion descends):
   ```go
   //@ decreases len(unresolved)
   ```
