# gobra-stubs

This directory contains hand-written **Gobra stubs** for standard-library
and third-party packages that this project imports. A stub declares the
signatures and contracts (`requires`/`ensures`/`decreases`) of a package's
API without providing implementations — Gobra trusts the contract and
verifies callers against it, the same way it would for a `.go` file with
`// +build ignore`-style trusted bodies.

Stubs are needed for two reasons:

1. **Gobra's bundled stubs are incomplete.** Some packages (e.g. `fmt`)
   ship with an empty built-in stub, so any symbol from that package is
   reported as "unknown identifier" unless we provide our own.
2. **Third-party packages have no Gobra stubs at all.** Anything under
   `github.com/...` (e.g. `mark3labs/mcp-go`) needs a stub written from
   scratch.

## How this directory is wired in

`scripts/run-gobra.sh` passes this directory to Gobra as an include path:

```sh
--include /gobra/input/gobra-stubs
```

Gobra resolves imports by searching include paths for a directory matching
the import path, so the layout under `gobra-stubs/` **mirrors Go import
paths**:

```
gobra-stubs/fmt/fmt.gobra                                  -> "fmt"
gobra-stubs/os/os.gobra                                     -> "os"
gobra-stubs/github.com/mark3labs/mcp-go/mcp/mcp.gobra       -> "github.com/mark3labs/mcp-go/mcp"
```

The `.gobra` extension and filename don't matter to Gobra's resolver — only
the directory path and the `package` declaration inside the file. By
convention we name the file after the package (e.g. `os/os.gobra`).

## Writing a new stub

1. **Create the directory** matching the import path, and a `.gobra` file
   inside it with `package <name>`.
2. **Only declare symbols this project actually uses.** Stubs are not meant
   to be complete reimplementations of the package — just enough surface
   area for Gobra to type-check and verify callers.
3. **Omit bodies.** Declare signatures only (`func Foo(...) ReturnType`,
   with no `{ }`), and give each one a `decreases _` clause so Gobra
   doesn't attempt (and fail) to prove termination for code it can't see.
   This is the same approach Gobra's own stub library uses (see e.g.
   `strconv.Atoi`).
4. **Derive contracts from published documentation.** Reference
   https://pkg.go.dev/<package> in a doc comment, and translate documented
   guarantees into `ensures` clauses. The most common pattern in this
   project is the standard library's `(value, error)` convention:

   ```go
   // Exactly one of the returned byte slice and the error is nil.
   ensures (res == nil) != (err == nil)
   decreases _
   func ReadFile(name string) (res []byte, err error)
   ```

   See [`internal/gobra/return_types.gobra`](../internal/gobra/return_types.gobra)
   for `gobra.ExactlyOneOf`, a helper for stating this same property in
   your own (non-stub) code.

## Known Gobra encoding limitations to work around

These came up while writing the existing stubs and are worth knowing before
adding more:

- **Named function types as slice/variadic/return element types crash
  Gobra.** A defined type like `type ToolOption func(*Tool)` used as
  `...ToolOption` or as a return type triggers an internal "Logic error...
  did not match with any implemented case" crash. Workaround: write out the
  anonymous function type instead (`...func(*Tool)`), both in the stub and
  in any real `.go` code that references it.
- **Local variables in `.go` files are never addressable.** `&someLocal`
  fails Gobra's addressability check. If a stubbed function needs a
  pointer, prefer signatures that let callers pass a composite literal
  (`&T{...}`), which is always addressable.
- **Slice indexing inside `ensures`/`pure` function postconditions crashes
  the backend.** Don't write contracts like `ensures res == items[0]` for a
  slice parameter — this triggers an internal Silicon error regardless of
  element type. Keep postconditions in terms of `len()` and the function's
  own parameters/results, not indexed slice elements.

## Existing stubs

| Path | Covers |
| --- | --- |
| `bufio/` | `bufio` |
| `context/` | `context` |
| `encoding/base64/` | `encoding/base64` |
| `encoding/json/` | `encoding/json` |
| `fmt/` | `fmt` (replaces Gobra's empty built-in stub) |
| `io/` | `io` |
| `log/` | `log` |
| `net/http/` | `net/http` |
| `net/url/` | `net/url` |
| `os/` | `os` |
| `path/filepath/` | `path/filepath` |
| `sort/` | `sort` |
| `github.com/mark3labs/mcp-go/mcp/` | `github.com/mark3labs/mcp-go/mcp` |
| `github.com/mark3labs/mcp-go/server/` | `github.com/mark3labs/mcp-go/server` |
