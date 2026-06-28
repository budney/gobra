# Plan Review Acceptance Criteria

Each criterion is binary (pass/fail). A plan set passes only if all
eight criteria pass.  Edit this file to add, remove, or tighten
criteria as the project evolves.

---

## C1 — Registry completeness

Every entry in the `00-overview.md` WBS table exists as a file in
`.plans/`.  Every `.plans/*.md` file is either (a) listed in the
WBS, or (b) listed in the "Reference Documents" table in `00-overview.md`,
or (c) is `CRITERIA.md` itself (this file), or (d) is `00-overview.md`
(the master index — it cannot list itself).  Reference documents
have no Objective/Scope/Deliverables sections; they belong in the
"Reference Documents" table, not the WBS. Current reference documents:
`DECISIONS.md`, `CONTEXT.md`, `COVERAGE.md`.  No orphan docs. No
phantom plans.

## C2 — Required sections

Every plan file has all four of these sections: **Objective**,
**Scope** (with both In-scope and Out-of-scope subsections),
**Dependencies**, and **Deliverables**.  A plan missing any section
cannot be implemented or checked against.

## C3 — Single owner per artifact

Each named artifact (type, struct, interface, package path) is
claimed in exactly one plan's In-scope section. If two plans both
claim to define the same artifact, that is a failure regardless of
whether their definitions agree.

## C4 — Cross-plan references resolve exactly

Wherever plan A names a type, function signature, or package defined
in plan B, the name and shape must be identical in both documents.
Paraphrases, structural mismatches, or "something like X" formulations
are failures.

## C5 — Dependency graph is acyclic and closed

No plan may depend (directly or transitively) on itself. Every entry
in a plan's Dependencies section must exist in the WBS. Every
artifact a plan consumes must be produced by one of its listed
dependencies — not by an unlisted plan, and not invented inline.

## C6 — Every pipeline boundary has explicit, matching types

Each plan that acts as a pipeline stage names its input type(s),
output type(s), and error type explicitly. The declared output type
of stage N must match the declared input type of stage N+1 with no
implicit conversion gap.

## C7 — Every shared resource has a synchronization contract

Any mutable state accessed from multiple goroutines names the
synchronization mechanism (mutex, channel, or atomic). Every goroutine
that calls JNI explicitly documents `runtime.LockOSThread()` and
the JVM attach/detach lifecycle. Silence is a failure — there is
no implicit pass for undocumented shared state.

## C8 — Every plan is independently validatable

Each plan describes at least one concrete method to verify its
deliverable works in isolation, before the full pipeline is connected.
"It will be tested end-to-end later" is a failure.

## C9 — Self-Verification Annotations Exist

Every plan describing a pipeline component or internal logic must
explicitly detail the formal Gobra specifications (`//@ requires`,
`//@ ensures`, and loop invariants) that will be written into the
resulting Go source code. The plan must describe what safety
properties (e.g., pointer validity, data-race freedom, memory
deallocation at JNI boundaries) the component will prove about
itself using the Viper backend before it is considered complete.
Silence or deferring verification to a later stage is an automatic
failure.

