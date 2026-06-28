# 28 — Encoding: Channels

## Objective

Implement the encoding of Go channels and goroutine spawning into Silver. Channels are used
for concurrent communication; their verification requires modeling ownership transfer.

## Scope

**In scope:**
- Channel type `chan T`, `<-chan T`, `chan<- T` encoding
- `make(chan T)`, `make(chan T, n)` (buffered)
- Send `ch <- v` and receive `v = <-ch`, `v, ok = <-ch`
- `close(ch)`
- Channel permission model: ownership of channel and transfer of element permissions on send/receive
- `go f()` goroutine spawning — encode as a method call with permission transfer
- `select` statement (non-deterministic channel operation)

**Out of scope:**
- `sync` package primitives (mutex, waitgroup) — these are future work via built-in stubs (31)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context
- [26-encoding-permissions.md](26-encoding-permissions.md) — permission transfer model

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/channels/` — channel encoding
- `src/main/scala/viper/gobra/translator/encodings/programs/` — goroutine encoding

## Important Limitation: No Automatic Soundness for Concurrent Code

**All channel and goroutine verification in Go-Gobra depends entirely on user-supplied ghost
predicates.** Go-Gobra does not automatically encode Go's memory model, happens-before
guarantees, or channel communication protocols in Silver axioms. A concurrent Go program with
channels but without `SendChannel`/`RecvChannel`/`SendGivenPerm`/`RecvGotPerm` predicate
annotations will pass Go-Gobra's channel encoding with no errors — not because it is
correct, but because there is nothing to check.

**Consequence for users:** Concurrent programs verified by Go-Gobra are only as sound as
their ghost predicate annotations. An under-annotated concurrent program can pass verification
while containing data races or protocol violations. Document this prominently in:
- The README ("Concurrency Verification" section)
- A `KNOWN_LIMITATIONS.md` file at the repo root
- The `--help` output for any flag that affects concurrent verification

**Consequence for self-hosting (plan 36/37):** Go-Gobra uses goroutines internally (the JNI
worker pool, plan 15). These goroutines must either be annotated with the full permission
protocol or marked `//@ trusted` at the package boundary. Marking `internal/backend/jvm/`
as trusted (plan 36) is the expected approach; document the reason.

## Proposed Approach (from Scala source analysis)

**Channel type encoding (resolved):**
- Exclusive `chan T°`: encoded as `vpr.Int` — an **opaque integer identifier**. The integer
  value is never interpreted; it is purely nominal.
- Shared `chan T@`: `vpr.Ref`.

**All channel semantics are user-specified via ghost predicates.** Gobra does not attempt to
encode the channel communication protocol in Silver axioms. Instead, the user must provide
ghost predicate annotations:
- `SendChannel(c)` / `RecvChannel(c)` — ownership to send/receive
- `SendGivenPerm()` / `SendGotPerm()` — permissions transferred on send
- `RecvGivenPerm()` / `RecvGotPerm()` — permissions transferred on receive
- `isChannel(c)` + `BufferSize(c)` — channel identity and buffering

**`make(chan T, n)`**: inhales `isChannel(a)` and `BufferSize(a) == n`.

**Send `ch <- v`**: asserts `acc(SendChannel(ch), wildcard)`, exhales `SendGivenPerm()(v)`,
inhales `SendGotPerm()()`.

**Receive `v = <-ch`**: asserts `acc(RecvChannel(ch), wildcard)`, exhales `RecvGivenPerm()()`,
inhales `RecvGotPerm()(v)`.

**Happens-before (resolved):** entirely delegated to the user's predicate specifications.
Gobra does not encode a happens-before relation in Silver; soundness for concurrent programs
depends on the user-supplied predicates being correct.

**`close(ch)`**: exhales `SendChannel(ch)` (the sender's permission to send is consumed) and
inhales `ClosedChannel(ch)`. `ClosedChannel` is a new predicate defined alongside the other
channel predicates:

```
predicate ClosedChannel(c: Int)
```

The `ClosedChannel(ch)` predicate represents the fact that channel `ch` has been closed.
Attempting to send on a closed channel is caught because the `send` encoding requires
`acc(SendChannel(ch), wildcard)` and `ClosedChannel` and `SendChannel` are disjoint — the
user's annotations must establish that both predicates cannot hold simultaneously. Attempting
to receive from a closed channel and the "comma-ok" case (`v, ok := <-ch` where `ok == false`
on a closed channel) must be handled via user-supplied ghost annotations; the encoding itself
does not model the receive-from-closed behavior automatically (document in KNOWN_LIMITATIONS.md).

**`select` with `default`**: the default case represents non-blocking behaviour. Encode as
a Silver `if` with a non-deterministic boolean `b` selecting between the channel operation
and the default. Both branches must verify independently.

**`select` without `default` (N blocking cases)**: a `select` with N purely blocking channel
cases (no default) must be encoded with an integer discriminant `d: Int` non-deterministically
chosen in `[0, N-1]`, with N arms in a Silver `if`/`elsif` chain — one per channel case.

**`select` discriminant type rule (canonical definition — also specified in plan 12):** Let C
= the number of channel-operation arms and D = 1 if a `default` arm is present, 0 otherwise.

- **C=1, D=1** (exactly one channel op + one default): use a `bool` discriminant.
- **C≥2 or (C≥1, D=0)**: use an `Int` discriminant in `[0, C+D-1]`. Arms 0..C-1 correspond
  to channel operations; arm C (if D=1) corresponds to the default.
- **C=0, D=1** (only default, no channel ops): degenerate case — reduce to the default body
  directly; no discriminant needed.
- **C=0, D=0**: compile error; rejected by the type checker before desugaring.

A boolean discriminant is NOT sufficient when C≥2 even if a default is present, because
C+D≥3 total arms cannot be encoded with 1 bit.

**Interaction with `Poly[chan T]` boxing (plan 25):** When a channel value is stored in an
`interface{}`, the exclusive `chan T` encoding (`vpr.Int`) is boxed via `Poly[Int]` (since
the exclusive type of `chan T` is `Int`). Do NOT instantiate `Poly[chan T]` expecting `Ref`
as the type argument — the exclusive `chan T` type is `Int`, so the box/unbox pair is
`Poly[Int].box(chanID): Ref` and `Poly[Int].unbox(r): Int`. The `dynType` must carry
`chan_T_Type()` to distinguish channel-in-interface from int-in-interface. Add a regression
test that stores a channel in `interface{}` and asserts its type tag after extraction.

**Implication for annotations**: channel-using code is heavily annotation-dependent. The test
in this plan must include user-written `SendChannel`/`RecvChannel` predicates.

## Deliverables

- `internal/translator/encodings/channels.go`
- Tests: encode a simple channel send/receive pair with explicit `SendChannel`/`RecvChannel`
  predicate annotations; encode a buffered channel `make`; encode `select` with default;
  encode `close(ch)` and verify that a subsequent send is rejected by the verifier (requires
  a test that annotates the disjointness of `SendChannel` and `ClosedChannel`)
