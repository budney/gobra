# 29 — Encoding: Ghost ADTs & Mathematical Types

## Objective

Implement the encoding of Gobra's ghost algebraic data types (ADTs), mathematical sequences,
sets, multisets, dictionaries, and option types into Silver domains and built-in Silver types.

## Scope

**In scope:**
- **ADTs**: user-defined `adt T { constructor(...) ... }` → Silver domain with constructor
  functions, destructor functions, discriminant function, and injectivity/exhaustiveness axioms
- `match` expressions and statements over ADTs
- **Sequences** (`seq[T]`): Silver `Seq[T]` — literal, `|s|`, `s[i]`, `s[lo:hi]`,
  `s ++ t`, `e in s`, `s == t`
- **Sets** (`set[T]`): Silver `Set[T]` — literal, `|s|`, `e in s`, `s union t`,
  `s intersection t`, `s setminus t`, `s subset t`
- **Multisets** (`mset[T]`): Silver `Multiset[T]` — same operations as sets with multiplicity
- **Dictionaries** (`dict[K]V`): Silver domain encoding (Silver doesn't have native maps;
  encode as a domain)
- **Option** (`option[T]`): `none`, `some(v)`, `isNone`, `isSome`, `get(o)` — encode as a
  domain or use Silver's built-in option if available
- Ghost variables and ghost function calls involving these types

**Out of scope:**
- Standard Go maps (24-encoding-maps.md)
- Generics over ADTs (30)

## Dependencies

- [19-translator-core.md](19-translator-core.md) — Context

## Reference: Current Gobra

- `src/main/scala/viper/gobra/translator/encodings/adts/` — ADT encoding
- `src/main/scala/viper/gobra/translator/encodings/sequences/` — sequence encoding
- `src/main/scala/viper/gobra/translator/encodings/sets/` — set encoding
- Silver's built-in `Seq`, `Set`, `Multiset` types are used directly where possible

## Deliverables

- `internal/translator/encodings/adts.go`
- `internal/translator/encodings/mathcollections.go` (seq, set, mset, dict, option)
- Tests: encode an ADT with two constructors, a match expression, and a seq literal

## Open Questions

- Does Gobra's ADT encoding generate one Silver domain per ADT type, or a polymorphic
  domain? Read the Scala code to determine.
