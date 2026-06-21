# Gobra `isolated` Function Extension Plan

## Goal
Add a new Gobra function qualifier `isolated` that enforces “no external heap state” while still allowing pointer creation and pointer returns from fresh local/allocated data.

This feature is intended as a least-invasive alternative to `pure`, preserving pointer-based heap allocation and return semantics while preventing access to caller/global heap data.

## Design Overview

### Semantics
- `isolated` means the function can allocate and manipulate heap values it owns, but may not read or dereference heap-state that originates outside the function.
- The function may return pointers to:
  - freshly allocated objects (`new`, `make`, other supported allocations)
  - addresses of local variables declared in the same function body
  - results of other `isolated` functions
- The following are forbidden in an `isolated` function body:
  - dereference or heap-field access of a pointer or reference that comes from a parameter, a global, or an external non-isolated callee
  - calls to functions that are not `isolated` (unless the callee is pure in a way that does not access external heap)
  - any access to memory through `acc(...)` or predicate-based ownership from values not provably created inside the body

### Differentiation from existing qualifiers
- `verified`: already enforces spec restrictions and no `acc(...)` in the body’s ghost postconditions, but does not guarantee external heap isolation for the implementation body.
- `pure`: is too strict because it requires a single-expression body and prohibits useful pointer-based heap allocation/returns.
- `isolated`: sits between `verified` and `pure` as a new static checker property.

## Implementation Plan

### 1. AST and parser changes
- Add a new field to `PFunctionSpec` in `src/main/scala/viper/gobra/ast/frontend/Ast.scala`:
  - `isIsolated: Boolean = false`
- Update the parser and AST construction to recognize `isolated` as a valid function annotation.
  - Search existing parser rules for `trusted`, `verified`, `pure` or annotations around functions.
  - Add grammar support if needed for the `isolated` keyword.
- Ensure `isolated` is preserved in the frontend AST and passed through to the type-checking stage.

### 2. Checker validation logic
- Add `isolated` validation in `src/main/scala/viper/gobra/frontend/info/implementation/typing/ghost/GhostMiscTyping.scala` or the module responsible for function spec annotation validation.
  - Reject invalid combinations such as `isolated pure`, `isolated verified`, or `isolated trusted` if these combinations are intended to be disallowed.
  - Optionally allow `isolated verified` if the design should permit spec verification plus heap isolation.
- Introduce a dedicated body-level analysis pass in the ghost typing pipeline.
  - Candidate location: `GhostMemberTyping.scala` or an existing file that already walks function bodies and validates ghost expressions.
  - Add a new checker method for `isolated` functions:
    - Track which expressions are “owned” or fresh.
    - Reject dereference/field access (`PDeref`, heap reads) on expressions that are not provably local fresh state.
    - Reject channel send/receive of external heap values unless the message is provably owned/fresh.
    - Reject calls to non-`isolated` functions unless the callee is provably heap-independent or is treated as an owned/fresh helper.
    - Reject access to parameters or global/shared values through pointers or heap field dereferences.
    - Enforce a transitive isolation invariant: callees used by an `isolated` function must preserve the same owned/fresh argument invariant.

### 3. Owned-expression analysis strategy
- Implement a conservative ownership policy:
  - Mark the following as owned/fresh inside the body:
    - local variables declared in the function body
    - results of `new` and `make` expressions
    - values returned by callees declared `isolated`
  - Mark function parameters, globals, and shared values as external.
- Use this policy to verify that all heap accesses in an `isolated` body are on owned expressions.
- Specific checks:
  - For `PDeref(base)`, ensure `base` is owned.
  - For field access or array indexing on a pointer-like expression, ensure the receiver expression is owned.
  - For call expressions, ensure the callee is `isolated` or otherwise provably safe for external heap independence.
  - For `PSendStmt`/send expressions, ensure the sent message is owned/fresh if it can carry heap references.
  - For `PReceive`/receive expressions, treat incoming messages as potentially external heap and restrict them unless the channel pattern guarantees ownership transfer.
  - Preserve ownership through argument passing: `isolated` callees can accept owned/fresh pointers, but they must not access globals/closure variables or non-owned inputs.
- Add positive tests demonstrating:
  - `isolated` function returning `&x` for a local variable
  - `isolated` function returning `new(T)` or `make(...)`
  - `isolated` function calling another `isolated` helper and returning its result
- Add negative tests demonstrating rejected cases:
  - dereference of a pointer parameter inside `isolated`
  - field access on a shared/global pointer inside `isolated`
  - call to a non-`isolated` function from an `isolated` function
  - `isolated` function body with predicate-based heap reads from external memory
- Add tests for parser/annotation handling if needed.

### 6. Build and regression verification
- Run the Gobra compiler/type-checker on the new regression files.
- Run existing targeted tests around ghost typing and function qualifiers.
- Validate that `isolated` does not break existing `verified` or `pure` semantics.

## Estimated Change List
- `src/main/scala/viper/gobra/ast/frontend/Ast.scala`
- Parser grammar/source that parses function annotations
- `src/main/scala/viper/gobra/frontend/info/implementation/typing/ghost/GhostMiscTyping.scala`
- `src/main/scala/viper/gobra/frontend/info/implementation/typing/ghost/GhostMemberTyping.scala`
- `src/test/resources/regressions/features/isolated/`

## Notes
- This plan is intentionally conservative: it avoids changing the Viper encoding or existing backend models.
- The core work is a frontend static check for heap access provenance, not a new runtime semantics.
- If the parser already supports generic qualifiers, the implementation may be limited to AST and the ghost typing pass.
- If needed, the `isolated` qualifier can be extended later with explicit ownership transfer postconditions.

## Follow-up
After implementing the feature, verify with:
- `sbt testOnly *Ghost*`
- targeted regression runs for the new `isolated` tests
- a cross-check that `pure` functions still reject pointer-returning heap-allocating bodies if expected

## Subsequent iterations
- Later iterations can relax the call-chain requirement by allowing explicit heap-independent helpers with a separate qualifier or annotation.
- Another future extension is explicit ownership-transfer specs for channel send/receive boundary conditions.
- Yet another iteration could add a syntactic `owned` or `fresh` annotation on parameters/results to make the ownership contract explicit rather than inferred.
