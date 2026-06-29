---
description: Perform a deep, high-attention serial audit of every single plan file individually. It forces the agent to read and evaluate files one-by-one to prevent context skimming.
allowed-tools: Bash, Read, Edit, Write
---

## Purpose
You are executing a high-precision, serial architectural audit. Instead of reading all plan files simultaneously—which causes context sinking—you will evaluate each file in absolute isolation to catch deep semantic and logic errors.

## Execution Rules
1. **The Serial Loop:** Read `.plans/00-overview.md` to load the full sequential list of all 41 files.
2. **Isolated Focus Pass:** Select the first file in the list. Execute a tool call to Read *only* that file, alongside `CRITERIA.md`.
3. **Deep Evaluation:** Run your internal `/review-plan` logic exclusively on this single file. Evaluate every sentence against criteria C1 through C9.
4. **Immediate Flush:** Call your editing tools to write any discovered contradictions, gaps, or logic errors directly into Section 3 of `.plans/scratchpad.md` under a dedicated heading for that specific file.
5. **No Conversational Bleed:** Do not stop to talk, output summaries, or ask the user for permission. 
6. **Next File Chain:** Automatically clear your temporary file memory, increment to the next sequential file path in the WBS list, and loop back to Step 2.

## Exit Condition
Continue looping through all 41 files one-by-one until every single plan document has received an isolated, high-attention audit pass and its specific bugs are preserved on disk.
