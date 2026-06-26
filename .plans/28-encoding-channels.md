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

**`select` with `default`**: the default case represents non-blocking behaviour. Encode as
a Silver `if` with a non-deterministic boolean `b` selecting between the channel operation
and the default. Both branches must verify independently.

**Implication for annotations**: channel-using code is heavily annotation-dependent. The test
in this plan must include user-written `SendChannel`/`RecvChannel` predicates.

## Deliverables

- `internal/translator/encodings/channels.go`
- Tests: encode a simple channel send/receive pair with explicit `SendChannel`/`RecvChannel`
  predicate annotations; encode a buffered channel `make`; encode `select` with default
