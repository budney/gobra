# Gobra `isolated` Feature Plan

## Goal

Add a new Gobra function qualifier `isolated` that makes functions spec-callable without requiring the extreme body restrictions of `pure`, while still enforcing that the function does not access external heap state.

`isolated` is intended for heap-local computations that may allocate and return pointers to locally owned data, but must not dereference or read heap state that originates outside the call chain.

## Motivation

Current `master` lacks a qualifier that permits:
- normal multi-statement implementations,
- pointer allocation or returning locally-owned heap values,
- use of the function inside specs,
- while still forbidding external heap reads.

Existing qualifiers:
- `pure`: too strict; single-expression body only, disallows heap allocation/ownership semantics.
- (no other spec-callable qualifier on `master`): means many useful deterministic functions cannot be referenced in specs.

`isolated` bridges this gap.

## Semantics

An `isolated` function must satisfy:

1. **Heap-locality**
   - It can allocate fresh heap values (`new`, `make`, other allocation expressions).
   - It may return pointers to fresh/owned data.
   - It may create and manipulate local heap structures.

2. **No external heap access**
   - It may not dereference a pointer or access a heap field from a value that is external to the function.
   - External values include:
     - parameters that are non-owned references,
     - global variables,
     - values received from non-isolated callees,
     - closure-captured/shared heap data.

3. **Transitive isolation**
   - If an `isolated` function calls another function, that callee must preserve the same heap-local invariant:
     - ideally by being `isolated`, or
     - by being a safe heap-independent helper whose body does not touch external heap.

4. **Spec-callability**
   - `isolated` should make the function usable inside specs, either by being treated as spec-callable itself or by enabling an analogous verification path.
   - The weakest design is: `isolated` functions are spec-callable but bodies are checked under heap-locality rules, not full `pure` restrictions.

## Design Overview

### Differentiation from existing qualifiers

- `pure`: syntactically strict, body must be one expression, no heap semantics.
- `verified`: present in current branch, but not on `master`; intended for spec-only domain axioms and weaker body restrictions than `pure`.
- `isolated`: new qualifier for implementation bodies that need heap-local behavior plus spec usability.

`isolated` should be orthogonal to `pure`; it is not a replacement but a companion qualifier.

## Implementation Scope

### Files likely impacted

- `src/main/scala/viper/gobra/ast/frontend/Ast.scala`
- Parser/grammar and annotation handling in `src/main/scala/viper/gobra/frontend/ParseTreeTranslator.scala` or related parser files
- `src/main/scala/viper/gobra/frontend/info/implementation/typing/ghost/GhostMiscTyping.scala`
- `src/main/scala/viper/gobra/frontend/info/implementation/typing/ghost/GhostMemberTyping.scala`
- Possibly `src/main/scala/viper/gobra/frontend/info/base/SymbolTable.scala`
- Regression tests under `src/test/resources/regressions/`

### Major implementation components

1. AST/annotation support
2. `isolated` annotation validation
3. Heap-locality ownership analysis
4. Transitive callee checks
5. Spec-callability support and interaction with existing spec typing
6. Regression tests

## Detailed Plan

### 1. AST and parser support

- Add `isIsolated: Boolean = false` to `PFunctionSpec` in `src/main/scala/viper/gobra/ast/frontend/Ast.scala`.
- Update pretty-printing if necessary so `isolated` shows in AST dumps and formatted output.
- Ensure parser recognizes `isolated` in function declaration comments/annotations.
  - If the parser already recognizes generic function qualifiers, extend that mechanism.
  - Otherwise, add grammar/parse-tree translation support analogous to `pure`/`trusted`/`verified`.
- Propagate `isIsolated` through frontend AST nodes to the type-checking stage.

### 2. Annotation validation

- In `GhostMiscTyping.scala`, add checks for `isolated` annotations:
  - disallow invalid qualifier combinations, such as `isolated pure` if the design forbids it.
  - decide whether `isolated verified` is allowed.
    - A viable design: allow `isolated verified` to combine spec-only verification with heap-local implementation.
  - enforce that `isolated` can only appear on functions/methods with bodies.
- Add a semantic error message for invalid `isolated` uses.

### 3. Heap-locality checker

Implement a new type-checking pass for `isolated` function bodies.

#### Where
- Prefer `GhostMemberTyping.scala` because it already handles ghost-related function/member checks and spec-only callee reasoning.
- If there is a generic expression/body validator, extend it with an `isolated` mode.

#### What it checks
- Owned/fresh expression provenance:
  - Local variables declared in the function body are owned.
  - Results of `new`/`make` are owned.
  - Results returned from callees that are `isolated` are owned.

- External expressions:
  - Function parameters are external unless there is a future explicit ownership annotation.
  - Globals and package-level references are external.
  - Values received from normal, non-`isolated` callees are external.

- Heap operations forbidden on external expressions:
  - `PDeref(base)` or pointer dereference expressions.
  - field access through pointers or pointer-like references.
  - array/slice indexing or map lookup on external pointer/heap values.
  - `acc(...)` or other direct resource/permission assertions on external heap values.

- Calls from `isolated` bodies:
  - Require callee is `isolated`, or
  - allow a narrower class of heap-independent functions if the design chooses.
- Channel operations:
  - Consider channels a special boundary.
  - Sending a message should require the message be owned/fresh if it carries heap references.
  - Receiving from a channel may introduce external heap unless channel element ownership is enforced, so restrict `isolated` receives conservatively.

#### Call chain invariants
- `isolated` should impose a transitive invariant on callees:
  - If `f` is `isolated` and calls `g`, then `g` must not read external heap either.
  - The simplest rule: `g` must itself be `isolated` or pure/heap-independent.
- This avoids the situation where `isolated` returns a pointer from `g`, but `g` internally reads external heap.

### 4. Spec-callability and typing interaction

- Decide how `isolated` interacts with spec expressions.
- Two options:
  1. Treat `isolated` as spec-callable directly (like `verified`/`pure`).
  2. Treat it as a body-level qualifier only, with a separate rule that only `pure` and `verified` are spec-callable.
- The most compelling design for the proposal is option 1, because the feature exists to make ordinary heap-local functions usable in specs.
- Update `SymbolTable.isSpecCallable` or the spec-expr typing condition if needed:
  - `isSpecCallable = isPure || isVerified || isIsolated`
- Make sure spec expression checks accept `isolated` calls when they used existing spec-callable logic.

### 5. Regression tests

Add a regression directory and/or files for `isolated`:
- `src/test/resources/regressions/features/isolated/positive.gobra`
- `src/test/resources/regressions/features/isolated/negative.gobra`

Suggested tests:

Positive cases
- Local pointer return:
  - `func isolated makePair() (*T) { x := new(T); return x }`
- Owned allocation and return:
  - `func isolated myAbs(x int) int { ... }` with spec `ensures myAbs(myAbs(x)) == myAbs(x)`
- `isolated` caller calling `isolated` callee and returning its result.
- `isolated` function usable in spec expressions.

Negative cases
- `isolated` function dereferencing a pointer parameter.
- `isolated` function reading a global array pointer.
- `isolated` function calling a normal non-isolated helper that performs heap reads.
- `isolated` receive from channel when message ownership is not provably transferred.

Parser tests
- `isolated` annotation recognized in function declarations.
- invalid qualifier combinations rejected.

### 6. Build and regression verification

- Run `sbt testOnly *Ghost*` or the focused test target that covers typing and spec validation.
- Run regression checks for the new `isolated` tests.
- Confirm existing `pure` and `verified` behavior remains unchanged.

## Implementation milestones

1. Add AST/parser support for `isolated`.
2. Add annotation validation and error diagnostics.
3. Implement owned-expression heap-locality analysis.
4. Add call-chain/callee isolation rules.
5. Wire `isolated` into spec-callable logic if desired.
6. Add regression tests.
7. Run verification and cross-check existing qualifiers.

## Risks and design tradeoffs

- If `isolated` is too permissive, it may allow subtle external-heap aliasing.
- If it is too strict, it may become no more useful than `pure`.
- The right balance is a conservative ownership analysis that permits local allocations and `isolated` callees, but rejects all dereferences of external pointers.
- Channels are especially tricky; a conservative first iteration should treat incoming channel values as external unless explicit ownership transfer is proven.

## Future extensions

- Add explicit parameter or result ownership qualifiers to make the contract more precise.
- Add a distinct `owned` or `fresh` annotation on values passed through the call chain.
- Refine channel ownership semantics with dedicated send/receive ownership rules.
- Consider a later `isolated verified` mode if useful for combining domain-axiom-style specs with heap-local implementation.
