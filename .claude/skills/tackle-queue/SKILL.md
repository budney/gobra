---
description: Run an autonomous, self-correcting engineering loop to sequentially resolve all pending issues logged in the .plans/scratchpad.md Remediation Queue. Invoke with no arguments to sweep the entire queue top-down.
allowed-tools: Bash, Read, Edit, Write
---

## Purpose
You are executing an autonomous, top-down remediation loop across the Go-Gobra rewrite plan files. Your job is to physically modify the files on disk to resolve the structural contradictions, gaps, and logic errors logged in the scratchpad, ensuring the entire plan set completely satisfies `CRITERIA.md`.

## Execution Protocol (Non-Negotiable)
1. **Tool-First Entry:** Your very first output token must be a physical filesystem tool call (e.g., Read or Edit) targeting `.plans/scratchpad.md`. Do not provide conversational filler, introductions, or summaries in the chat window.
2. **Strict Severity-Order:** Identify the lowest-numbered incomplete item in the scratchpad's Remediation Queue. You must completely resolve this item before moving to any higher-numbered items.
3. **Write-Through Cache:** Every time you finish a sub-task or modify a plan file, your immediate next tool call must be to update that specific item's status checkbox to `[x]` directly on disk in `.plans/scratchpad.md`. Do not buffer state in your chat context memory.
4. **Autonomous Chaining:** Automatically loop back to Step 2 and pick up the next priority item. Continue executing tool calls sequentially without pausing or asking the user for permission.

## Exit Conditions
You are permitted to stop execution and return control to the user ONLY under two conditions:
1. Every single item in the Remediation Queue is marked complete on disk, and a final global `/check-plan` pass yields zero failures.
2. You hit an irreconcilable structural paradox (e.g., a Go package circular dependency trade-off) that mathematically requires a human architectural decision. If this happens, outline the choices clearly in the scratchpad, flush it to disk, and halt.

## Output Format
While looping, output absolutely nothing to the chat window. Upon clean exit or a forced halt, return a single sentence indicating whether the queue was successfully cleared or if a human design choice blocks progress.
