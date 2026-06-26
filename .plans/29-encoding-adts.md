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

## Proposed Approach (from Scala source analysis)

### ADTs (resolved: one domain per ADT type, fully monomorphized)

`adt X { C1{f: F}; C2{g: G} }` generates one Silver domain `X`:
```silver
domain X {
  function X_C1(f: F): X         // constructor
  function X_C2(g: G): X
  function X_f(t: X): F          // destructor
  function X_g(t: X): G
  function X_default(): X
  function X_tag(t: X): Int
  unique function X_C1_tag(): Int
  unique function X_C2_tag(): Int
  function rank$X(t: X): Int

  // Constructor axioms (tag + destructor round-trip)
  axiom { forall f: F :: {X_C1(f)} X_tag(X_C1(f)) == X_C1_tag() && X_f(X_C1(f)) == f }
  // Destructor axioms (reconstruction from tag)
  axiom { forall t: X :: {X_f(t)} X_tag(t) == X_C1_tag() ==> t == X_C1(X_f(t)) }
  // Exhaustiveness
  axiom { forall t: X :: {X_tag(t)} t == X_C1(X_f(t)) || t == X_C2(X_g(t)) }
  // Rank bounds
  axiom { forall x: X :: {rank$X(x)} 0 <= rank$X(x) }
  // Rank decrease for recursive fields
  axiom { forall f: F :: {rank$X(X_C1(f))} rank$F(f) < rank$X(X_C1(f)) }
}
```

**Pattern matching (`match`):**
- Match expressions → nested Silver `CondExp` chains, one per clause.
- Match statements → a boolean tracking variable and Silver `if` chains.

### Mathematical Collections

**`seq[T]`**: Exclusive → Silver `Seq[T]` (built-in, no domain). Operations map directly to
Viper built-ins. Special case: sparse literals use a generated `emptySeq_{T}(n: Int): Seq[T]`
function with postcondition `|result| == n && forall i :: 0 <= i < n ==> result[i] == dflt(T)`.
The literal is split into chunks: a gap of **5 or more** consecutive missing indices between
explicit elements triggers an `EmptyChunk` (rendered as `emptySeq(size)`); tightly-packed
elements (gap < 5) form `NonEmptyChunk`s. Dense literals with no gaps are emitted directly
as Silver sequence constructors. This threshold of 5 matches the Scala implementation's
hardcoded `threshold: Int = 5` in `SequenceEncoding.chunkify()`.

**`set[T]`**: Exclusive → Silver `Set[T]` (built-in). All set operations are Viper built-ins.

**`mset[T]`**: Exclusive → Silver `Multiset[T]` (built-in). `x # s` (multiplicity) = Viper's
built-in multiset containment, which returns the count.

**`dict[K]V`** (ghost mathematical map, owned by this plan): Exclusive → Silver `Map[K,V]`
(Silver's built-in `Map` type). No `Ref`, no Silver field. All operations use Silver built-in
map operations directly: `m[k]` → `Lookup(m, k)`, `k in m` → `Contains(m, k)`,
`m[k := v]` → `Map(m, k, v)`, `|m|` → `|m|`. This is the same Silver `MapType(K, V)` that
runtime `map[K]V` uses for its underlying field content (plan 24), but for `dict[K]V` the
entire value *is* the `Map[K,V]` — there is no wrapping `Ref`. The encoding lives in
`mathcollections.go` alongside `seq`, `set`, and `mset`.

**`option[T]`**: Encode as a dedicated Silver domain per element type:
```silver
domain Option_{T} {
  function none_{T}(): Option_{T}
  function some_{T}(x: T): Option_{T}
  function isNone_{T}(o: Option_{T}): Bool
  function get_{T}(o: Option_{T}): T
  axiom { isNone_{T}(none_{T}()) }
  axiom { forall x: T :: {some_{T}(x)} !isNone_{T}(some_{T}(x)) && get_{T}(some_{T}(x)) == x }
}
```

## Deliverables

- `internal/translator/encodings/adts.go` (ADT domain generation + match encoding)
- `internal/translator/encodings/mathcollections.go` (seq, set, mset, dict, option)
- Tests: ADT with two constructors + match expression; seq literal with gap; set union; option some/get
