# Go-Gobra Rewrite: Automation Scratchpad
> **Rule for AI Agent:** Do not edit any other `.plans/*.md` files until you have updated the active constraints, dependency states, and task items in this scratchpad.

## 1. Active Global Constraints & Invariants
*These are cross-cutting rules that must NEVER be violated by any individual plan file.*
- **[AST-INVARIANT]:** Frontend AST (`03-frontend-ast.md`) must explicitly embed `go/ast` types directly (per D9) to avoid massive conversion overhead.
- **[JNI-LIFECYCLE]:** The JVM lifecycle (`15-jni-setup.md`) must dictate a single, persistent global thread lock via CGo to prevent JVM crashes during parallel verification (`17b`).
- **[TRANSFORM-FLOW]:** Internal AST (`11`) output must strictly match the expected Go structs for Silver IR (`14`) input.

## 2. Global WBS Synchronization State
*Total Files: 40 | Status Trackers*

### Group 1: Setup & Syntax
- [ ] 01-project-setup.md (Status: UNCHECKED)
- [ ] 02-annotation-syntax.md (Status: UNCHECKED)

### Group 2: Frontend & AST (D9/D10 Dependency)
- [ ] 03-frontend-ast.md (Status: UNCHECKED)

### Group 4: Silver IR & JNI Backend (Parallelism Vector)
- [ ] 14-silver-ast.md (Status: UNCHECKED)
- [ ] 15-jni-setup.md (Status: UNCHECKED)
- [ ] 16b-silver-chopper.md (Status: UNCHECKED)
- [ ] 17b-parallel-workers.md (Status: UNCHECKED)

*(Note: The AI agent will populate the remaining 33 files here on its first autonomous pass)*

## 3. Active Verification Loop Log
- **Current Target Loop:** Post-review-plan fix verification.
- **Active Blockers / Contradictions:**
  - **C1**: `scratchpad.md` present in `.plans/` but not listed in WBS or Reference Documents in `00-overview.md` (pre-existing).
  - **C4**: `00-overview.md` Cross-Cutting Notes, line 139: "`Diagnostic` type … (defined in plan 32)" — should say "defined in plan 32a".
  - **C5**: Plan 33 Dependencies section lists `15-jni-setup.md` but omits `15b-worker-pool-expansion.md`. Plan 33's `pipeline.go` creates an N-worker pool (NewPool) owned by plan 15b; WBS correctly shows 15b as a blocker.
  - **C4**: Plan 17b Objective says "Expand the JNI worker from a single goroutine (plan 15) to a pool of N workers" — this is now plan 15b's job; 17b's scope is DispatchChopped only.
  - **C5 minor**: Plan 32a Placement Note lists plan 19 as "affected" (must list 32a as dependency) but plan 19's `Translate` returns `(*silver.Program, error)`, not `[]Diagnostic`, so 19 does not need 32a.
- **Next Steps:** All failures resolved; /check-plan passed.
