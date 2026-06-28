---
description: Review Go-Gobra rewrite plan docs in .plans/ for contradictions, gaps, logic errors, bad design decisions, and possible simplifications or improvements. Invoke as /review-plan [file-or-glob] to scope the review, or with no arguments to review all docs.
allowed-tools: Bash, Read
---

## Purpose

You are reviewing the Go-Gobra self-hosting rewrite plan documents in `.plans/`. These are
design and implementation plan files (Markdown) that together describe how to rewrite Gobra in
Go. The master index is `.plans/00-overview.md`.

Your job is a rigorous technical review — not a style check. Look for real problems that could
cause wasted effort or incorrect implementation.

## What to review

If `$ARGUMENTS` names a specific file or glob, review only those files. Otherwise review all
`.plans/*.md` files, starting with `00-overview.md` to understand the overall structure and
dependency graph.

## Review dimensions

For each document (or across documents when doing a full review), evaluate:

1. **Contradictions** — Does any statement in one doc conflict with a statement in another?
   Check interface signatures, data structure shapes, dependency ordering, and architectural
   decisions. Cross-reference `DECISIONS.md` if it exists.

2. **Gaps** — Are there steps, interfaces, or data flows mentioned as inputs/outputs of a plan
   that are never defined anywhere? Are there dependencies in the WBS that block each other
   circularly? Are there deliverables that nothing consumes?

3. **Logic errors** — Would following this plan produce incorrect behavior? Look for: encoding
   schemes that don't round-trip, type mappings that lose information, concurrency designs with
   races or deadlocks, JNI lifecycle assumptions that would crash the JVM, missing error
   propagation paths.

4. **Bad design decisions** — Does a chosen approach introduce unnecessary complexity, violate
   Go idioms badly, or create tight coupling that will be painful later? Would a simpler
   alternative obviously work just as well?

5. **Simplifications and improvements** — Are there places where the plan specifies more than
   is needed for the stated goal? Are there standard Go libraries or patterns that would replace
   significant custom work?

## How to conduct the review

0. BEFORE READING TARGET FILES: Open and read `.plans/scratchpad.md`.
   Check Section 1 for Active Global Constraints. If any file you are
   reviewing violates a constraint listed there, immediately mark it
   as a high-severity "Design Concern" or "Logic Error" in your findings
   and log the conflict in Section 3 of the scratchpad.
1. Read `.plans/00-overview.md` first to load the WBS and resolved architectural decisions.
2. Read each targeted plan file fully before commenting on it.
3. When you spot an issue, note the specific file and section, quote the relevant text, and
   explain the problem concisely.
4. Group findings by dimension (Contradictions / Gaps / Logic Errors / Design / Simplifications).
5. Within each group, order findings by severity: blocking > significant > minor.
6. If `$ARGUMENTS` is empty, explicitly note which files you reviewed so the user knows the
   scope was complete.

## Output format

Use this structure:

```
## Review scope
<list of files reviewed>

## Contradictions
- **[file:section]** <quoted text> — <explanation>

## Gaps
- **[file:section]** <description of missing piece>

## Logic errors
- **[file:section]** <explanation of incorrect behavior>

## Design concerns
- **[file:section]** <concern and suggested alternative>

## Simplifications / improvements
- **[file:section]** <suggestion>

## Summary
<2–4 sentence overall assessment>
```

If a dimension has no findings, write "None found." Don't skip the heading.
