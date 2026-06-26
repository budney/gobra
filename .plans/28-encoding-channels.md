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

## Deliverables

- `internal/translator/encodings/channels.go`
- Tests: encode a simple channel send/receive pair with permission transfer

## Open Questions

- How does Gobra model the happens-before relationship for channel operations? This is
  necessary for soundness of concurrent programs.
- Is `select` with a `default` case treated differently from one without?
