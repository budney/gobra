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

**Path from frontend to Silver:** The desugarer (plan 12) lowers `PSeqLit` nodes to `internal.SeqLit`
nodes (defined in plan 11), filling in all concrete indices. Plan 29's encoding module receives
`SeqLit` nodes from the translator dispatch and performs gap analysis and chunking at that point.
The desugarer performs NO gap analysis — all chunking logic lives here.

The literal is split into chunks: a gap of **5 or more** consecutive missing indices between
explicit elements triggers an `EmptyChunk` (rendered as `emptySeq(size)`); tightly-packed
elements (gap < 5) form `NonEmptyChunk`s. Dense literals with no gaps are emitted directly
as Silver sequence constructors. This threshold of 5 matches the Scala implementation's
hardcoded `threshold: Int = 5` in `SequenceEncoding.chunkify()`.

**Algorithm (applied at translation time to `SeqLit.Elements`):**
1. Sort elements by index (the desugarer guarantees all indices are concrete non-negative integers).
2. Walk consecutive pairs; if the gap between `elements[i].Index` and `elements[i+1].Index` is ≥ 5,
   emit an `EmptyChunk` for the gap region, then a `NonEmptyChunk` for the next element.
3. Concatenate all chunks using Silver's `++` sequence append.
4. The final Silver expression for the entire literal is `chunk0 ++ chunk1 ++ ...`.

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
  function isSome_{T}(o: Option_{T}): Bool   // explicit, not derived as !isNone
  function get_{T}(o: Option_{T}): T
  axiom gobra__option_none_{T} { isNone_{T}(none_{T}()) }
  axiom gobra__option_some_{T} {
    forall x: T :: {some_{T}(x)}
      !isNone_{T}(some_{T}(x)) && isSome_{T}(some_{T}(x)) && get_{T}(some_{T}(x)) == x
  }
  axiom gobra__option_isSome_{T} {
    forall o: Option_{T} :: {isSome_{T}(o)} isSome_{T}(o) == !isNone_{T}(o)
  }
}
```

`isSome` is included as an explicit domain function (with its own axiom linking it to
`!isNone`) rather than relying on callers to write negation. This matches Gobra's surface
syntax (`isSome(o)` appears directly in specs) and avoids negation overhead at every call
site in generated Silver. The axiom `isSome_{T}(o) == !isNone_{T}(o)` is the canonical
connection; Z3 uses the trigger `{isSome_{T}(o)}` to instantiate it.

## Bodyless Functions

Per plan 19: every bodyless Viper function must be verified against the Scala source before
this encoding is considered complete. Scala reference:
`src/main/scala/viper/gobra/translator/encodings/sequences/` and
`src/main/scala/viper/gobra/translator/encodings/adts/`.

| Function | Preconditions | Required postconditions | Scala reference |
|----------|--------------|-------------------------|-----------------|
| `emptySeq_{T}(n: Int): Seq[T]` | `n >= 0` | `\|result\| == n`, `forall i: Int :: {result[i]} 0 <= i && i < n ==> result[i] == dflt(T)` — verify exact form against Scala | `SequenceEncoding.scala` |

**Audit checklist:** For this row, open `SequenceEncoding.scala`, find the `emptySeq` function
definition, and confirm every postcondition in the Go implementation matches exactly.
Mark ✓ when confirmed. The quantifier trigger `{result[i]}` is critical — omitting it can
cause Z3 to time out. Do not mark this encoding complete until the row is checked.

## Verification Specifications (C9)

The following Gobra annotations will be written into `internal/translator/encodings/adts.go`
and `internal/translator/encodings/mathcollections.go` and verified before this plan is
considered complete.

1. **ADT `match` exhaustiveness postcondition** — encoding a `match` expression produces a
   Silver conditional chain that covers every constructor arm; the chain is never a dangling
   `else false`:
   ```go
   //@ requires ctx != nil && matchExpr != nil
   //@ requires allArmsReachable(matchExpr) // ghost: no arm is unreachable by type
   //@ ensures  result != nil
   //@ ensures  matchIsExhaustive(result, matchExpr) // ghost: Silver cond chain covers all ctors
   func EncodeMatch(ctx *Context, matchExpr *internal.MatchExpr) (result silver.Expr)
   ```

2. **`SeqLit` chunk-coverage postcondition** — every source index in `SeqLit.Elements`
   appears in the output Silver sequence; no element is silently dropped by the chunking:
   ```go
   //@ requires ctx != nil && lit != nil
   //@ requires allIndicesConcrete(lit) // ghost: desugarer guarantee — no Index == -1
   //@ ensures  result != nil
   //@ ensures  forall i int :: i in sourceIndices(lit) ==> indexPresent(result, i)
   func EncodeSeqLit(ctx *Context, lit *internal.SeqLit) (result silver.Expr)
   ```

3. **`emptySeq_{T}` postcondition (no-panic guard)** — the bodyless Silver function emitted
   by this encoding satisfies its length and element-default postconditions as specified in the
   Bodyless Functions table; the Go code that emits it must include the postconditions verbatim:
   ```go
   // Emitting emptySeq_{T}; postconditions must exactly match the Bodyless Functions table.
   //@ ensures |result| == n
   //@ ensures forall i: Int :: {result[i]} 0 <= i && i < n ==> result[i] == dflt(T)
   ```
   This is verified by the Scala oracle test (plan 34 differential mode).

4. **Termination** — `EncodeMatch` terminates because `match` arms are finite and each
   recursive call operates on a strictly smaller sub-expression:
   ```go
   //@ decreases len(matchExpr.Arms)
   func EncodeMatch(ctx *Context, matchExpr *internal.MatchExpr) (result silver.Expr)
   ```

## Deliverables

- `internal/translator/encodings/adts.go` (ADT domain generation + match encoding)
- `internal/translator/encodings/mathcollections.go` (seq, set, mset, dict, option)
- Tests: ADT with two constructors + match expression; seq literal with gap; set union; option some/get
