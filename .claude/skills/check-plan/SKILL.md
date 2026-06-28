---
description: Check Go-Gobra rewrite plan docs against the acceptance criteria in .plans/CRITERIA.md. Invoke as /check-plan [file-or-glob] to scope the check, or with no arguments to check all docs. Useful after applying /review-plan recommendations to verify the fixes are correct and complete.
allowed-tools: Bash, Read
---

## Purpose

You are verifying that the Go-Gobra rewrite plan documents in `.plans/` satisfy the acceptance
criteria defined in `.plans/CRITERIA.md`. This skill is meant to be run after applying
recommendations from `/review-plan`, to confirm that the fixes are correct and nothing new was
broken.

## Setup

0. PRE-FLIGHT BOUNDARY: Read `.plans/scratchpad.md` first. If a plan file passes all criteria in
   `CRITERIA.md`, update its status checkbox in the scratchpad to
   `[x] PASSED`. If it fails, update its status to `[ ] FAILED` and
   append the detailed structural failure text directly into the
   scratchpad's "Active Blockers / Contradictions" section so it
   is preserved across execution turns.
1. Read `.plans/CRITERIA.md` in full first — this is the authoritative checklist.
2. Read `.plans/00-overview.md` to load the WBS, dependency graph, and cross-cutting notes.
3. If `$ARGUMENTS` names specific files, check only those files against the criteria that apply
   to them. Otherwise check all `.plans/*.md` files.

## How to check

Work through each criterion in `CRITERIA.md` in order. For each criterion:

- Determine which plan file(s) it applies to.
- Read the relevant sections of those files.
- Evaluate: does the current text satisfy the criterion?
- Record the result as PASS, FAIL, or N/A (with a brief reason for FAIL or N/A).

Do not guess or infer satisfication — if a criterion requires an explicit statement and the
text is silent, that is a FAIL, not a PASS.

When checking cross-file criteria (interface consistency, dependency graph, data flow), read
all relevant files before rendering a verdict — do not rely on memory of earlier reads.

Streaming State: Do not wait until the entire command is finished
to output data. Write severe violations (Blockers/Contradictions)
to `.plans/scratchpad.md` immediately upon discovery before processing
the next file in the glob.

## Output format

```
## Check scope
<files checked>

## Criteria results

### 1. Structural completeness
- [ ] PASS/FAIL/N/A — <criterion summary> — <evidence or reason>
...

### 2. Dependency graph
...

(continue for all numbered sections in CRITERIA.md)

## Summary
<count> passed, <count> failed, <count> N/A

### Failures requiring action
- **[Criterion N.N]** <file:section> — <what is wrong and what is needed to fix it>

### Newly introduced issues (regressions)
<any criteria that were previously passing but now fail, if known>
```

If all criteria pass, say so clearly and skip the failure sections.

List every failure with enough detail that the user can act on it without re-reading the docs
themselves.
