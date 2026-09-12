# 002 — Counted property accessors, and Flow members that count what they deliver

**Status:** Proposed. Nothing here is implemented. §2 states the rule this proposes, §3 records each
decision with the options considered and the one taken, §4 phases the work, §6 is the release. P1, P2
and P3 are separable: each changes the generated output on its own, and P2 can be taken without
either of the others. P6 — the tag — waits on P5, the re-alignment with the Swift implementation
(§9).

**Decisions:** every decision in §3 is taken, and in each one the option taken is (a). The property
shape of §2.1 (D1) and the inclusion of the stream counters of §2.3 were taken when this spec was
commissioned; D2 through D14 on 2026-09-12. §3 keeps the options not taken, with their trade-offs, as
the record of what was weighed.

**Measurements:** every number, path, quoted string and generated snippet in §1 was produced on
2026-09-12 against `main` at `93c93d2`, where `./gradlew clean build` is green (14 actionable tasks,
13 of them executed; the `:mocks-processor` and `:receipt` suites pass). Two measurements ran outside
this repository, in the session scratchpad, and changed nothing in it: the shapes §2 proposes,
hand-written as the renderer would emit them and run (§1.8), and a probe consumer wired to
`dev.modaal:mocks-processor:0.2.1-SNAPSHOT` from `./gradlew :mocks-processor:publishToMavenLocal`
(§1.4, §1.5, §1.6).

**Scope of the change:** `mocks-processor/src/main/kotlin/dev/modaal/mocks/MockRenderer.kt`,
`KspMocksProcessor.kt`, `mocks-processor/src/test/kotlin/dev/modaal/mocks/MockRendererTest.kt`,
`receipt/src/main/kotlin/dev/modaal/mocks/receipt/Receipt.kt`,
`receipt/src/test/kotlin/dev/modaal/mocks/receipt/GeneratedMockReceiptTest.kt`, `README.md`
§"Generated API", `CONTRIBUTING.md` §"The mock dialect is a contract", `CHANGELOG.md`,
`skills/kotlin-ksp-mocks/SKILL.md`, `references/generated-api.md`, `references/troubleshooting.md`,
`scripts/check-skill.sh` (K6's extraction, D13), and the `-SNAPSHOT` literal in
`build.gradle.kts:18`. It is a breaking change to generated output; the release is 0.3.0 (§6).

**Not in scope:** `MockModel.kt`. Every fact the new emission reads is already on the model —
`MockProperty.isMutable` (`:39`), `defaultValue` (`:41`), `flowElementType` (`:43`),
`MockFunction.flowElementType` (`:33`) — so the change is the renderer's and the diagnostic's, not
the KSP adapter's. An effectful property accessor (swift 004 §2.6) has nothing to port: a Kotlin
interface cannot declare a suspending or throwing getter. A per-declaration opt-out for the stream
recorders (swift's `skipArgumentRecording`) needs an annotation on the consumer's main classpath,
which `CONTRIBUTING.md` §"Development rules" 3 rules out; D9 records the KSP-option alternative.
Channel-backing a `StateFlow` or `SharedFlow` requirement, and mocking a sink-shaped requirement
(`FlowCollector`, `SendChannel`), stay coverage gaps — measured in §1.6, carried in §8.

**Obsoletes:** nothing.

**Follows:** `swift-sourcery-templates/specs/004-mock-member-naming/spec.md` §2.5 (every property
requirement is counted), §2.7 (what a stream member counts), and its decisions D8, D9, D11, D12,
D13. That spec's §8 records: "`kotlin-ksp-mocks` adopting §2.5 is a separate iteration in that
repository." This is that iteration, and it takes §2.7 with it. 004 is itself unimplemented ("Status:
Proposed"), so the two repositories reach the same shape independently and neither waits on the
other; §9 states what each side owes the other's twin table.

---

## 0. TL;DR

1. A property requirement generates a counter only where the value is already computed. A mutable
   requirement gets `<prop>SetCount` and nothing else; a read-only requirement with a guessable
   default gets no member at all; a read-only `Flow` requirement gets `<prop>GetCount`,
   `<prop>GetHandler` and `<prop>Channel` (`MockRenderer.kt:106-142`, §1.1).
2. Across the two mocks `:receipt` generates, **21 bookkeeping members exist today and 0 of them
   count a read of a stored property** (§1.2). Under §2.1 the two mocks carry 48.
3. A `Flow` member records nothing about what it delivered: `<fn>Args` has no counterpart on the
   stream path, so a test that wants the values writes its own collector (§1.3).
4. An interface declaring both `draft` and `draftSetCount` generates a file that does not compile —
   measured as four Kotlin errors in a file the adopter is told never to edit, with no diagnostic
   from the processor (§1.4).
5. A residual overload collision leaves the renderer as
   `e: [ksp] java.lang.IllegalArgumentException: overloads collide on bookkeeping names even after
   parameter-name disambiguation: [fA]` — measured, and it names neither the interface nor the two
   members (§1.5).
6. A `StateFlow`, a `SharedFlow` and a `FlowCollector` requirement are each constructor-seeded
   stored `var` overrides, which no document states (§1.6).
7. **The rule this proposes:** every property requirement generates `<prop>GetCount`,
   `<prop>GetHandler` and a `_<prop>` store, with `<prop>SetCount` on a `var` requirement; a
   read-only requirement's override becomes `val` and `_<prop>` is the seed-and-read path that moves
   no counter; a channel-backed `Flow` member counts `<name>SubscribeCount`,
   `<name>SubscribeCancelCount`, `<name>OutputCount`, `<name>CompletionCount`, records
   `<name>Outputs` and calls `<name>OutputHandler`; and every emitted name goes through one
   uniqueness check that fails generation naming the interface and the two members (§2).
8. All of it compiles and behaves as stated: 40 checks over the hand-written shapes pass on Kotlin
   2.4.10 with coroutines 1.11.0 (§1.8), including a `<prop>GetHandler` seeded after the code under
   test captured the flow deciding the stream.
9. Two behaviours the measurement pinned down and the documents have to carry: a collector that
   stops early (`take(1)`, `first()`, a timeout) counts `<name>SubscribeCancelCount`, not
   `<name>CompletionCount`; and two collectors on `Dispatchers.Default` lost a count or a recorded
   value in 4 of 6 rounds, because the counters are plain `Int` and the recorder a plain
   `MutableList` (§1.8, D10).

---

## 1. Measured

### 1.1 What a property generates today

`MockRenderer.renderProperty` (`:106-142`) has three branches:

| requirement | emitted | members |
| --- | --- | --- |
| read-only `Flow<E>` (`:110-125`) | computed `val` over the channel | `GetCount`, `GetHandler`, `Channel` |
| `var` (`:131-137`) | stored `var` with a counting setter | `SetCount` |
| read-only, non-`Flow` (`:139`) | stored `var` override, seeded from the default or the constructor | none |

The read-only branch emits `override var` for a `val` requirement so a test can re-seed the value
directly (`GeneratedMockReceiptTest.kt:29` does exactly that). A read of any stored property moves
nothing.

### 1.2 The two receipt mocks carry 21 bookkeeping members

Counted over `receipt/build/generated/ksp/test/kotlin/dev/modaal/mocks/receipt/` at `93c93d2`:

- `ReceiptEnvironmentMock` — 21: `configUpdatesGetCount`, `configUpdatesGetHandler`,
  `configUpdatesChannel`, `volumeSetCount`, and three each for `events`, `findLabel`, `load`, `log`
  and `measure`, two for `reset`.
- `ReceiptDependencyMock` — 0. Both its requirements are read-only properties, so the file is a
  constructor and two `override var` lines (12 lines total).

Under §2.1 and §2.3 the same two interfaces produce 48: `ReceiptEnvironmentMock` 42
(`configUpdates` 9, `events` 9, `volume` 4, `idleTimeoutMs` 3, `staticConfig` 3, the five remaining
functions 14) and `ReceiptDependencyMock` 6.

### 1.3 A Flow member records nothing about what it delivered

`MockRenderer.kt:183` and `:204-206` give a `Flow`-returning function a channel and a handler;
`:110-125` give a read-only `Flow` property the same. Neither counts a collection, a delivered
value or a completion, and neither keeps the values. `GeneratedMockReceiptTest.kt:51-60` asserts on
`configUpdatesGetCount` and on the collected list a test built itself with `toList()`.

### 1.4 A property-name collision reaches the consumer's compiler

Probe interface, generated through the published processor:

```kotlin
interface CollidesToday {
  var draft: String
  val draftSetCount: Int
}
```

The generated file declares `var draftSetCount: kotlin.Int = 0` beside
`override var draftSetCount: kotlin.Int = 0`, and the consumer's test compilation fails with four
errors on `CollidesTodayMock.kt` — `:9:7 Overload resolution ambiguity between candidates`,
`:12:7 Conflicting declarations`, `:12:7 'draftSetCount' hides member of supertype 'CollidesToday'
and needs an 'override' modifier`, `:14:16 Conflicting declarations`. The processor logs nothing:
`MockRenderer.kt:99-102`'s guard covers function bookkeeping names only.

### 1.5 An overload collision leaves the renderer as `IllegalArgumentException`

Three one-parameter overloads sharing a parameter name (`fun f(a: Int)`, `fun f(a: String)`,
`fun f(a: Boolean)`) reach `:100`'s `require`, and the build reports:

```
e: [ksp] java.lang.IllegalArgumentException: overloads collide on bookkeeping names even after parameter-name disambiguation: [fA]
> A failure occurred while executing com.google.devtools.ksp.gradle.KspAAWorkerAction
```

It names the colliding bookkeeping name and not the interface, not the two declarations, and not
what to do. `AGENTS.md` §"Read the generated output" requires an error naming the interface member
and the action.

### 1.6 `StateFlow`, `SharedFlow` and a sink-shaped requirement are constructor-seeded

`KspMocksProcessor.flowElement` (`:145-152`) matches `kotlinx.coroutines.flow.Flow` exactly
(`:147`), so a subtype is not channel-backed. Measured on a probe interface declaring
`val plain: Flow<String>`, `val state: StateFlow<String>`, `val shared: SharedFlow<String>`: `plain`
got the computed getter with `plainChannel`; `state` and `shared` became constructor parameters and
`override var` lines. A `FlowCollector<String>` requirement did the same. Usable — a test seeds a
`MutableStateFlow` through the constructor — and stated in no document: `SKILL.md:112` and
`references/generated-api.md:139-177` describe the `Flow` case only.

### 1.7 Where the property rule is stated, and the sentence this change makes false

- `README.md:92-99` — "a mutable requirement is stored and counts writes in `<prop>SetCount`".
- `CONTRIBUTING.md:14-21` — the vocabulary list, `<prop>SetCount` among it.
- `SKILL.md:111-112` — the two property rows of the member table; `:120-125` — the Properties
  paragraph.
- `references/generated-api.md:139-177` — §"Properties", whose closing line reads: "There is no
  `<prop>GetCount` for a stored property — a read of a stored `var` is not counted." (`:168`).
- `references/generated-api.md:196-215` — the twin table, which pairs `<prop>SetCount` with
  `<var>SetCount` and does not say that the Swift side counts no read either.
- `MockRenderer.kt:9-24` — the KDoc that states the vocabulary for a contributor.
- `receipt/src/main/kotlin/dev/modaal/mocks/receipt/Receipt.kt:21-31` — the per-requirement comments
  naming which shape each member exercises.

### 1.8 The proposed shapes, compiled and run

The §2 emissions were hand-written into a scratch Gradle project (Kotlin 2.4.10, coroutines 1.11.0,
`jvmToolchain(25)`) as the renderer would emit them, with a check harness. All 40 equality checks
pass. What they establish:

| check | result |
| --- | --- |
| construction moves no counter, and `_<prop>` holds the constructor argument | `staticConfigGetCount` 0, `volumeSetCount` 0 |
| one read counts one get; one write counts one set and lands in `_<prop>` | 1, 1, `_volume == 0.5` |
| reading or assigning `_<prop>` moves no counter | get count unchanged at 1 |
| `<prop>GetHandler` wins over the store | `9.0` returned, both reads counted |
| a read-only requirement reads the store a test seeded | `250L`, `GetCount` 1 |
| a `Flow` property read counts a get and no subscription | `GetCount` 1, `SubscribeCount` 0 |
| collecting it counts one subscription, two outputs, one completion, no cancel | 1, 2, 1, 0 |
| `<prop>Outputs` holds the delivered values | `[3, 5]` |
| a `GetHandler` seeded **after** the property was read decides the stream, and its values count | `[late]`, `OutputCount` 1 |
| two collections of one channel-backed property | `SubscribeCount` 2, first collector got 2 values, second got 0, each delivered value counted once |
| `take(1)`, `first()`, a `withTimeoutOrNull` cancel | each: `SubscribeCount` 1, `SubscribeCancelCount` 1, `CompletionCount` 0 |
| a `Flow`-returning method: the channel path and the handler path both counted | outputs `[a]` then `[a, b, c]`, `CompletionCount` 2 |
| `<fn>OutputHandler` runs after the counter and can send the next value from inside itself | `[one, two]`, `OutputCount` 2 |
| a mutable `Flow` property is a store with get and set counters | read 1, write 1 |
| two collectors on `Dispatchers.Default`, 100 values, 6 rounds | delivered 100 every round; `OutputCount` read 99 in 1 round, `Outputs` held 99 in 3 |

The last row is the race D10 decides: `var <name>OutputCount: Int` and
`val <name>Outputs: MutableList<T>` are not synchronized, and concurrent collection loses
increments.

### 1.9 What check K6 gates, and what it does not

`scripts/check-skill.sh`'s `py_k6` reads the emitted member set out of `MockRenderer.kt` with
`\$\{(?:fn|name|capitalized)\}([A-Za-z]+)` and compares it with `<(?:fn|prop|Fn)>([A-Za-z]+)` over
every `.md` under `skills/`. Three consequences for this change:

1. A new member is gated only if the renderer writes it as `${name}Suffix` or `${fn}Suffix`. A name
   built any other way leaves the skill free to quote a member the renderer never emits.
2. The direction is one-way: the skill naming a member the renderer does not emit is red; the
   renderer gaining a member the skill does not mention is green, and so is a skill sentence
   asserting a member's **absence** — `references/generated-api.md:168` is exactly that sentence,
   and P1 has to move it by hand.
3. `_<prop>` matches neither pattern (both require a letter after the placeholder), so the store's
   spelling is gated on neither side. D13 decides whether K6 grows a third pattern.

---

## 2. The rule this proposes

### 2.1 Every property requirement is counted

Every property requirement generates `<prop>GetCount` and `<prop>GetHandler`; a `var` requirement
also generates `<prop>SetCount`; and the value moves to a store named `_<prop>`:

```kotlin
override var volume: kotlin.Double
  get() {
    volumeGetCount += 1
    volumeGetHandler?.let { return it() }
    return _volume
  }
  set(value) {
    volumeSetCount += 1
    _volume = value
  }
var volumeGetCount: kotlin.Int = 0
var volumeGetHandler: (() -> kotlin.Double)? = null
var volumeSetCount: kotlin.Int = 0
var _volume: kotlin.Double = 0.0
```

Stated for a reader in one sentence: **a property requirement carries the same members whether it is
stored or computed, and `_<prop>` is the value.**

`<prop>SetCount` keeps the meaning and the spelling it has today, on the requirements that have it
today (`var` requirements). Today it is the only member a stored property generates
(`MockRenderer.kt:131-137`); under this rule it sits beside `GetCount`, `GetHandler` and `_<prop>`.

### 2.2 `_<prop>` is the store, and a read-only requirement's override is `val`

```kotlin
override val staticConfig: ReceiptConfig
  get() {
    staticConfigGetCount += 1
    staticConfigGetHandler?.let { return it() }
    return _staticConfig
  }
var staticConfigGetCount: kotlin.Int = 0
var staticConfigGetHandler: (() -> ReceiptConfig)? = null
var _staticConfig: ReceiptConfig = staticConfig
```

Five rules the shape carries:

- **Construction counts nothing.** The constructor parameter keeps the declared name, so
  `ReceiptDependencyMock(config = config)` is unchanged, and it initializes `_<prop>` rather than the
  requirement (measured, §1.8).
- **A test seeds and reads through `_<prop>` when it does not want to move a counter.** Assigning
  `mock.volume` moves `SetCount` as it does today; reading `mock.volume` starts moving `GetCount`.
- **A read-only requirement's override becomes `val`** (D2). `mock.<prop> = value` on such a
  requirement stops compiling; `mock._<prop> = value` replaces it. This is the one source break in
  the change, and `GeneratedMockReceiptTest.kt:29` is the first line it breaks.
- **`<prop>GetHandler` wins over the store**, and never traps: the store always holds a value,
  seeded from the guessable default or from the constructor. Swift 004 §2.4's second diagnostic —
  `<prefix>GetHandler expected to be set.` — has no Kotlin counterpart, because nothing here
  corresponds to its `/// sourcery: handler` annotation.
- **A read-only `Flow` requirement has no store.** Its fallback is `<prop>Channel`, which is what
  the Swift side does with `<var>Subject` (004 §2.7). §2.3 states the rest of that member.

### 2.3 A Flow member counts subscriptions, values and completions

A channel-backed `Flow` member — a read-only `Flow<E>` property, or a function returning exactly
`Flow<E>` — gains six members. The property reads its handler inside the flow builder, so a handler
seeded after the code under test captured the flow decides the stream (D6, measured):

```kotlin
override val configUpdates: kotlinx.coroutines.flow.Flow<ReceiptConfig>
  get() {
    configUpdatesGetCount += 1
    return flow { emitAll(configUpdatesGetHandler?.invoke() ?: configUpdatesChannel.receiveAsFlow()) }
      .onStart { configUpdatesSubscribeCount += 1 }
      .onEach { value ->
        configUpdatesOutputCount += 1
        configUpdatesOutputs.add(value)
        configUpdatesOutputHandler?.invoke(value)
      }
      .onCompletion { cause ->
        if (cause is kotlinx.coroutines.CancellationException) configUpdatesSubscribeCancelCount += 1
        else configUpdatesCompletionCount += 1
      }
  }
var configUpdatesSubscribeCount: kotlin.Int = 0
var configUpdatesSubscribeCancelCount: kotlin.Int = 0
var configUpdatesOutputCount: kotlin.Int = 0
val configUpdatesOutputs: kotlin.collections.MutableList<ReceiptConfig> = mutableListOf()
var configUpdatesOutputHandler: ((ReceiptConfig) -> kotlin.Unit)? = null
var configUpdatesCompletionCount: kotlin.Int = 0
```

A function keeps its handler at call time — that is where the arguments are, and where the member's
`suspend` applies — and the counters wrap whichever stream the call produced:

```kotlin
override fun events(): kotlinx.coroutines.flow.Flow<ReceiptEvent> {
  eventsCallCount += 1
  val upstream = eventsHandler?.invoke() ?: eventsChannel.receiveAsFlow()
  return upstream
    .onStart { eventsSubscribeCount += 1 }
    .onEach { value ->
      eventsOutputCount += 1
      eventsOutputs.add(value)
      eventsOutputHandler?.invoke(value)
    }
    .onCompletion { cause ->
      if (cause is kotlinx.coroutines.CancellationException) eventsSubscribeCancelCount += 1
      else eventsCompletionCount += 1
    }
}
```

Six rules the shape carries:

- **The counters record what crossed the member.** `<name>GetCount` and `<name>CallCount` keep
  counting reads and calls; `<name>SubscribeCount` counts collections; `<name>OutputCount` counts
  values delivered. A value sent while nobody collects is held by the unlimited channel and counts
  nothing until it is delivered.
- **A collector that stops early counts a cancellation, not a completion.** `take(1)`, `first()` and
  a collection cancelled by a timeout each leave `<name>SubscribeCancelCount` at 1 and
  `<name>CompletionCount` at 0 (measured, §1.8): `kotlinx.coroutines.flow.take` ends the upstream
  with kotlinx's internal `AbortFlowException`, a `CancellationException` subclass, and a timeout
  cancels the collecting coroutine.
- **A stream that fails counts a completion.** `onCompletion`'s `cause` is the failure, and
  everything that is not a `CancellationException` counts in `<name>CompletionCount` — the same
  member that counts a normal end, as on the Swift side (004 §2.3).
- **`<name>Outputs` records each delivered value, and `<name>OutputHandler` runs after it**, in the
  order `<fn>CallCount` / `<fn>Args` / `<fn>Handler` already establishes. A test asserts
  `mock.<name>Outputs == listOf(expected)` with no handler written; the handler is for what a
  recorder cannot do, which is to see each value as it lands and send the next one from inside it
  (measured, §1.8). There is no opt-out (D9): a recorded value lives as long as the mock.
- **The channel stays single-consumer.** Two collections of one channel-backed member count two
  subscriptions and split the values — the first collector took both and the second took none in the
  measurement. `<prop>GetHandler` returning a `SharedFlow` is how a test gives two collectors the
  same values.
- **The counters are not synchronized** (D10). Concurrent collection can lose an increment or a
  recorded value (§1.8).

Generated files that carry such a member import `emitAll`, `flow`, `onCompletion`, `onEach`,
`onStart` and `receiveAsFlow` from `kotlinx.coroutines.flow`, name-sorted, in place of today's single
`receiveAsFlow` import (`MockRenderer.kt:56-58`): a Kotlin extension cannot be called
fully qualified, so these six are imports rather than qualified call sites.

The method snippet above and this import rule are both narrower in what P3 emitted, in two ways that
change no count and no member name: §10.3 records them.

### 2.4 One suffix set

| suffix | on | emitted when |
| --- | --- | --- |
| `CallCount` | function | always |
| `Args` | function | at least one recordable parameter |
| `Handler` | function | always |
| `Channel` | function, property | a function returning exactly `Flow<E>`, or a read-only property typed exactly `Flow<E>` |
| `GetCount`, `GetHandler` | property | always (§2.1) |
| `SetCount` | property | the requirement is `var` |
| `_` prefix | property | `_<prop>` is the store, on every property except a read-only `Flow` one (§2.2) |
| `SubscribeCount` | function, property | the member is channel-backed — something collected it |
| `SubscribeCancelCount` | function, property | the member is channel-backed — a collection was cancelled |
| `OutputCount`, `Outputs`, `OutputHandler` | function, property | the member is channel-backed — values delivered: counted, recorded, handed to the handler |
| `CompletionCount` | function, property | the member is channel-backed — the stream ended or failed |

**A counter gets a handler where the shape being mirrored has one** (004 D12, unchanged here):
`<fn>CallCount`/`<fn>Handler` and `<prop>GetCount`/`<prop>GetHandler` supply what the member
returns; `<name>OutputCount`/`<name>OutputHandler` observe the payload crossing it.
`<prop>SetCount`, `<name>SubscribeCount`, `<name>SubscribeCancelCount` and `<name>CompletionCount`
are counters alone.

The six stream suffixes are Combine's words, taken from swift 004 §2.3 unchanged, so that one word
per concept crosses both platforms (D7).

### 2.5 Every emitted name goes through one uniqueness check

The renderer collects every name it emits — each property override, each bookkeeping member, each
nested `<Fn>Args` class — and fails generation when one appears twice:

```
kspMocksTargets: com.example.Service — draftSetCount is generated twice, for draft and for draftSetCount; rename one of the two interface members.
```

- The check covers what §1.4 and §1.5 both reach, and it replaces `MockRenderer.kt:100`'s `require`.
- Function **override** names stay out of the set: overloads share a declared name legitimately, and
  Kotlin allows a property and a function to share one — measured:
  `class Both { var draftCallCount: Int = 0; fun draftCallCount(): Int = draftCallCount }` compiles
  on Kotlin 2.4.10.
- The message names the interface, the generated member and the two declarations behind it. There is
  no per-declaration rename annotation to point at — selection is a build-script list — so the
  action is to rename one of the interface members.
- The processor logs it with `env.logger.error` and creates no file (D11), so it reads as
  `e: [ksp] kspMocksTargets: …` beside the four the processor already logs
  (`KspMocksProcessor.kt:58`, `:62`, `:66`, `:117`).

### 2.6 What does not change

`<Interface>Mock`, its package and its constructor signature; name-sorted member emission and byte
determinism; `<fn>CallCount` / `<fn>Args` / `<fn>Handler` and the `<Fn>Args` data class; the
unset-handler fallback table and the string `"<fn>Handler expected to be set."`; the overload rule
(the fewest-parameter overload keeps the plain name, the others append their capitalized parameter
names); the channel's `UNLIMITED` capacity and the fact that closing it is what ends a stream; the
constructor-seeded bag for a requirement with no guessable default; `kspMocksTargets` and the four
diagnostics the processor logs today; the published jar's class-file major 61.

---

## 3. Decisions

### D1 — what a property requirement generates

- **(a) `GetCount`, `GetHandler` and `_<prop>` on every property requirement, `SetCount` unchanged
  on a `var` one (taken when this spec was commissioned).** One sentence covers every property, and a test stops having to read the
  generated file to learn which members a property has. It is swift 004 D8 (a), so the two dialects
  state the same rule.
- (b) `GetCount` alone on a stored property. The same rewrite — a read cannot be counted without a
  store — for a vocabulary that then needs `GetHandler`'s absence stated as an exception.
- (c) Leave stored properties uncounted. Keeps `references/generated-api.md:168` true and keeps the
  two dialects apart on the one point 004 §8 names.

### D2 — the read-only requirement's override

- **(a) `override val` with a computed getter, and `_<prop>` as the seed path (taken).** The
  mock's member then has the shape the requirement declares, and there is one seeding path for every
  property. It breaks `mock.<prop> = value`, which `GeneratedMockReceiptTest.kt:29` and any adopter
  test written the same way rely on; the compiler names every occurrence.
- (b) Keep `override var` for a read-only requirement, with the setter counting `SetCount`. Nothing
  breaks, and `<prop>SetCount` then appears on a requirement that declares no setter — a member
  whose emission rule takes an exception to state, and one the Swift side does not have (004 §2.3
  emits `SetCount` for `{ get set }` only).
- (c) Keep `override var` with an uncounted setter. Nothing breaks and the vocabulary stays as in
  (a), at the cost of a write that moves no counter on some properties and `SetCount` on others.

§12.1's D15 (c) and §12.4 carry this decision across both repositories: `_<prop>` is the only
assignment that seeds a read-only requirement on either platform, and the Swift side adopts the
get-only witness.

### D3 — the store's spelling

- **(a) `_<prop>` (taken).** Swift 004 §2.5 and D9 (a) spell it the same way, so a
  cross-platform codebase has one name for the store. The underscore prefix is what Kotlin already
  uses by convention for a backing field.
- (b) `<prop>Value`. Self-describing at the use site, a likelier collision with a real declaration,
  and a second spelling to keep in step with the twin.
- (c) `<prop>Storage`. The same trade as (b).

Under any of the three the collision is what decides the outcome, and §2.5's check is what turns it
into a diagnostic.

### D4 — does a read-only `Flow` property get a store?

- **(a) No: its fallback stays `<prop>Channel` (taken).** The channel is the seeding mechanism
  a test already uses, and 004 §2.7's publisher property keeps `<var>Subject` as its fallback with
  no `_<var>` beside it.
- (b) A store as well, seeded through the constructor. Every property would then have one, at the
  cost of a constructor parameter on every stream requirement and two fallbacks to document.

### D5 — a mutable `Flow` property

- **(a) A store with `GetCount`, `GetHandler` and `SetCount`, no channel and no stream counters
  (taken).** It is what `MockRenderer.kt:73-74`'s `isReadOnlyFlow` already decides: the mock
  does not own that stream, the test assigns it. Measured working in §1.8's last property check.
- (b) Channel-back it as well, and count its collections. The member would then have both a store a
  test assigns and a channel it pushes into, and the two would disagree about what a read returns.

`StateFlow` and `SharedFlow` requirements land in the constructor-seeded branch either way (§1.6).

### D6 — when a stream member's handler is read

- **(a) A property's handler is read inside the flow builder; a function's at call time
  (taken).** A `<prop>GetHandler` seeded after the code under test captured the flow then
  decides the stream (measured, §1.8), which is the case a test hits when the subject collects in
  its own initializer. A function's handler takes the call's arguments and may be `suspend`, so it
  stays where it is today; its result is wrapped by the counters, so `<fn>OutputCount` counts values
  whichever way the stream was seeded.
- (b) Read both at accessor time, using `onStart` in place of the builder. One sentence fewer to
  document, and the late-seeding case goes back to depending on collection order.
- (c) Read both at collection time. One rule for both members, and it moves a function handler's
  exceptions and side effects from the call to the collection.

Swift 004 §2.7 returns a method's handler-supplied publisher uncounted (`return try __shareHandler(id)`
before the `handleEvents` chain). §9 carries the proposal that it wrap there too.

### D7 — the stream suffix set

- **(a) Combine's words, from 004 §2.3 (taken):** `SubscribeCount`, `SubscribeCancelCount`,
  `OutputCount`, `Outputs`, `OutputHandler`, `CompletionCount`. A codebase on both platforms reads
  one word per concept, and no Swift-side amendment is needed.
- (b) Kotlin's own words: `ValueCount`, `Values`, `ValueHandler` for what a `Flow` emits,
  `CollectCount` for a collection. Each is the word the coroutines API uses, and each is a second
  name for a concept the twin already names.

### D8 — cancellation and completion as two counters

- **(a) Two counters, decided by `onCompletion`'s cause (taken).** A test can tell "the stream
  ended" from "the collector stopped", which is the pair 004 §2.7 carries as `CompletionCount` and
  `SubscribeCancelCount`.
- (b) One `CompletionCount` for both. One member fewer, and `take(1)` becomes indistinguishable from
  a closed channel.

The measured consequence either way: `take(1)`, `first()` and a timeout each count a cancellation
(§1.8), which P3 documents in `references/troubleshooting.md`.

### D9 — an opt-out for the recorders

- **(a) None; `<name>Outputs` always records (taken).** `<fn>Args` already records
  unconditionally, and the mock's lifetime is a test's.
- (b) A KSP option, `kspMocksRecordStreamValues=false`, read in `KspMocksProcessor.process`. It is
  the only place an opt-out can live — an annotation would have to sit on the consumer's main
  classpath (`CONTRIBUTING.md` §"Development rules" 3) — and it is build-wide rather than
  per-declaration, so it cannot express what Swift's `skipArgumentRecording` expresses. Deferred
  until a consumer measures a cost; §8 question 3.

### D10 — thread safety of the counters and the recorder

- **(a) Plain `Int` and `MutableList`, with the limitation documented (taken).** It is what
  every existing counter is, on both platforms, and a test that collects one mock member from two
  coroutines concurrently is the case that loses increments — measured at 1 lost count and 3 lost
  recordings over 6 rounds of 100 values (§1.8).
- (b) `AtomicInteger` and a synchronized list. It changes the type of every counter a test reads —
  `assertEquals(2, mock.volumeSetCount)` becomes `assertEquals(2, mock.volumeSetCount.get())` at
  every assertion in every consumer — and it is a cross-repo decision the Swift side has no
  equivalent for.
- (c) Count in a `Mutex`-guarded block. Same member types, and it makes every counted read a
  suspending one, which a property getter cannot be.

### D11 — how the collision is reported

- **(a) `MockRenderer.render` returns a sealed result; the processor logs (taken).**
  `Rendered(text)` or `Collision(message)`, built in the one pass that also writes the text, so the
  checked set and the emitted set cannot drift. `KspMocksProcessor.generate` (`:55-82`, the render call at `:71`) logs the
  message with `env.logger.error` and creates no file. `MockRendererTest`'s 10 existing tests take a
  one-line helper to unwrap `Rendered`.
- (b) A separate `MockRenderer.validate(target): String?` the processor calls before `render`. No
  change to the 10 tests, and two functions that have to agree about which names are emitted.
- (c) Keep throwing, and catch in the processor. The message reaches the log instead of the stack
  trace, and the renderer keeps a control-flow exception for an input the caller can check.

§10.2 records how the checked set is built — read back out of the emitted text — and where the
option name is spelled now that the renderer names it in a diagnostic.

### D12 — where the vocabulary lives

- **(a) `MockRenderer.kt`, interpolated as `${name}Suffix` / `${fn}Suffix` (taken).**
  `AGENTS.md` §"State a rule once" already names that file as the one place a member name is
  spelled, and `check-skill.sh`'s K6 reads the names out of exactly those interpolations (§1.9), so
  a member built any other way silently leaves the gate.
- (b) A `MockNames.kt` beside it, as swift 004 D5 (a) does with `MockNaming.swift`. The Kotlin
  renderer interpolates a member name at 23 sites in one 210-line file — 8 `${name}`, 12 `${fn}`,
  3 `${capitalized}` — against the 32 sites in three files that motivated the Swift extraction, and the
  move would need `check-skill.sh`'s `RENDERER` path and `AGENTS.md` updated in the same commit.

### D13 — does K6 grow to cover `_<prop>`?

- **(a) Yes: add `_\$\{name\}` to the renderer-side pattern and `_<prop>` to the skill-side one
  (taken).** Three lines in `py_k6` plus a seeded violation in `--self-test`, and the store —
  which the skill will name as the seed-and-read path in every property example — becomes gated like
  every other member.
- (b) No. The store's spelling is then checked by review only, as §1.9 measures it today.

§10.4 records the patterns as implemented: the two this decision names, and one more.

### D14 — migration aids for consumers

- **(a) None beyond the `CHANGELOG.md` entry (taken).** The one source break (D2) is a compile
  error naming the member and the type; everything else is additive.
- (b) Emit the read-only requirement as `var` for one release and deprecate it. The renderer would
  have to emit both shapes, which is the condition this change removes.

---

## 4. Phasing

P1 to P4 land on the branch this spec's own commit starts,
`spec/002-property-accessors-and-stream-counters` (`AGENTS.md` §"Changes reach `main` through a pull
request"), and reach `main` through one PR. Each phase is one commit or a short series, carries the
prefix `[002-property-accessors-and-stream-counters]`, and lands the skill edit for the members it
moves in the **same** commit as the renderer edit (`AGENTS.md` §"The skill under `skills/` teaches
adopters"). The gate for every phase: `./gradlew build` before and after, the emitted sources under
`receipt/build/generated/ksp/` read by hand, and `scripts/check-skill.sh`.

**P1 — the property accessors (§2.1, §2.2; D1, D2, D3).** Rewrite `MockRenderer.renderProperty`
(`:106-142`): every property gets the computed accessor over `_<prop>`, a read-only requirement's
override becomes `val`, the constructor initializes `_<prop>`, and `MockRenderer.kt:9-24`'s KDoc
states the new rule. `MockRendererTest`: the two property tests (`:146-180`) and the bag test
(`:181-197`) rewritten, plus new cases for a read-only requirement's `val` override, the store's
initial value, and `GetHandler` precedence. `:receipt`: `GeneratedMockReceiptTest.kt:29` moves to
`_idleTimeoutMs`, and new tests for construction counting nothing, one read counting one get, and a
seeded store. Documents in the same commit: `README.md:92-99`, `CONTRIBUTING.md:14-21`,
`SKILL.md:111-112` and `:120-125`, `references/generated-api.md:139-177` — including the sentence at
`:168`, which K6 cannot catch (§1.9) — and `Receipt.kt:21-31`'s comments.

**P2 — the uniqueness check (§2.5; D11).** `MockRenderer.render` returns the sealed result, the
`require` at `:100` goes, `KspMocksProcessor.generate` logs and skips the file. Unit tests: a
property colliding with another's bookkeeping member, a declared `_<prop>`, and the three-overload
residue of §1.5. `references/troubleshooting.md` gains a section for the new diagnostic, and
`SKILL.md`'s failure table a row. Re-run the probe of §1.4 to confirm the four compiler errors
become one logged error. Independent of P1 and P3, and worth taking first if either is deferred.

**P3 — the stream counters (§2.3; D4–D10).** The read-only `Flow` property branch (`:110-125`) and
the `Flow`-returning function branch (`:183`, `:204-206`) gain the six members and the operator
chain; the import block becomes the six names. Unit tests for both branches' emitted text.
`:receipt`: cases for subscribe/output/completion counts, a cancelled collection, a late-seeded
`configUpdatesGetHandler`, and `eventsOutputHandler` driving the next value — the checks of §1.8,
moved into the module that compiles real generated output. `SKILL.md`'s member table and
§"Streams are channel-backed"; `references/generated-api.md` §"A method returning `Flow`" and
§"Properties"; `references/troubleshooting.md` rows for a `CompletionCount` that stays 0 because the
collector used `first()` and an `OutputCount` that stays 0 because nothing collected.

**P4 — the documents that are not phase-local.** `references/generated-api.md`'s twin table gains
the rows §9 lists; `SKILL.md`'s property paragraph states the `StateFlow`/`SharedFlow` and
sink-shaped cases measured in §1.6; `check-skill.sh`'s K6 grows the `_<prop>` patterns and its
`--self-test` seed (D13), run with `scripts/check-skill.sh --self-test`; `AGENTS.md` §"State a rule
once" and its `CLAUDE.md` copy take the store's spelling, with `cp AGENTS.md CLAUDE.md`.

**P5 — re-align with the Swift implementation (§9).** The Swift side's 004 §2.5 and §2.7 phases land
on their own branch there, each repository's PR goes green, and then the two emitted member sets are
compared name by name: the property members of §2.1, the six stream members of §2.3, the two
diagnostics of §2.5 and 004 §2.4, and the twin table in each repository's skill. Adjustments to
either side go in here, before either tag. D6's proposal for 004 §2.7 — wrapping a method's
handler-supplied publisher so its output counters move — is settled in this window, and the
follow-up file beside 004 (§9) is written from what the comparison found.

**P6 — release 0.3.0 (§6).** After P5: a published version is immutable
(`CONTRIBUTING.md` §"Development rules" 6), so a member name that moves during the re-alignment moves
before the tag, not in a 0.3.1.

---

## 5. What a consumer does

1. **Rebuild.** The mock regenerates in the test compilation and the compiler names what moved.
2. **One break to fix:** a test that assigned a read-only requirement through `mock.<prop>` assigns
   `mock._<prop>` instead. No other test line stops compiling; item 6 is the one interface shape
   that stops generating.
3. **A property read now counts.** `mock.volume` moves `volumeGetCount`; `mock._volume` reads the
   same value and moves nothing. A test that reads a mock property inside an assertion changes no
   result — it changes what a *new* assertion on that count would read.
4. **Every property gains a handler.** `mock.volumeGetHandler = { 0.5 }` decides what a read
   returns, which is how a test makes a property fail or vary per read without touching the store.
5. **A `Flow` member gains six members.** `<name>SubscribeCount` is how a test asserts that the code
   under test actually collected; `<name>Outputs` is the delivered values without writing a
   collector; `<name>OutputHandler` sees each value as it lands. A collector that stops early
   (`first()`, `take(n)`, a timeout) counts `<name>SubscribeCancelCount` and not
   `<name>CompletionCount`. A `<prop>GetHandler` on a `Flow` property is read when the flow is
   collected, so seeding it after the code under test read the property still decides the stream,
   and clearing it between the read and the collection hands the collector the channel.
6. **An interface declaring a name the mock generates now fails generation** with
   `e: [ksp] kspMocksTargets: …` naming both members, where it previously emitted a file that did
   not compile (§1.4). Rename the interface member.

---

## 6. Release

The tag is cut after P5 — both implementations landed, both PRs green, the member sets compared and
whatever the comparison moved already in. `CONTRIBUTING.md` §"Development rules" 4 and 6, and
`AGENTS.md` §"Do not tag without measuring", in order:

1. **Write the `CHANGELOG.md` entry first**, under the format the file's existing entries use, as
   **0.3.0**:
   - *Generated output* — every property requirement now carries `<prop>GetCount`,
     `<prop>GetHandler` and a `_<prop>` store, and a `var` requirement keeps `<prop>SetCount`; a
     read-only requirement's override is `val`; a channel-backed `Flow` member carries
     `<name>SubscribeCount`, `<name>SubscribeCancelCount`, `<name>OutputCount`, `<name>Outputs`,
     `<name>OutputHandler` and `<name>CompletionCount`. For the two interfaces `:receipt` declares,
     21 bookkeeping members become 48 (§1.2).
   - *Breaking* — `mock.<prop> = value` on a read-only requirement no longer compiles; assign
     `mock._<prop>`. An interface declaring a name the mock generates (`<prop>GetCount`, `_<prop>`,
     …) now fails generation with a logged error instead of emitting a file that does not compile.
     A `<prop>GetHandler` on a `Flow` property is read when the flow is collected rather than when
     the property is read, so clearing it between the read and the collection changes what the
     collector gets.
   - *Adopting* — rebuild; fix what the compiler names; `_<prop>` is the seed-and-read path that
     moves no counter; `<name>Outputs` replaces a hand-written collector.
   - The published jar stays class-file major 61 (`publishedBytecodeTarget`,
     `mocks-processor/build.gradle.kts:23`), and `checkPublishedBytecodeVersion` holds it there.
2. **Move the development version literal** in `build.gradle.kts:18` to `0.3.0-SNAPSHOT`, in the
   commit that will carry the tag.
3. **Measure the consumer on that commit:** `./gradlew clean build`, then read the two files under
   `receipt/build/generated/ksp/test/kotlin/dev/modaal/mocks/receipt/` and confirm the member counts
   the changelog states. `scripts/check-skill.sh` and `cmp AGENTS.md CLAUDE.md` pass.
4. **Tag `0.3.0`.** `.github/workflows/publish.yml` runs `scripts/publish-maven.sh 0.3.0`, which
   stages, asserts the coordinate set is complete and refuses to overwrite a published version.
5. The skill is not a release asset: all four install channels read `main`, so P1–P4's skill edits
   reach adopters when they land, ahead of the tag. An adopter on 0.2.1 who installs the new skill
   reads members their processor does not emit. §8 question 5 carries whether the skill states the
   version a member arrived in.

---

## 7. Not measured

- The generated line count and file size after the change. Only member counts were derived (§1.2).
- Whether any adopter declares a member named `_<prop>` or `<prop>GetCount`. The probe in §1.4 is
  constructed, not found in a real interface.
- Whether a consumer module declaring its own top-level `Flow` extension named `onStart`, `onEach`
  or `onCompletion` makes the generated import block ambiguous. The six imports compiled in §1.8's
  project, which declares no such extension.
- Contention beyond the 6 rounds of §1.8: how often a counter is lost under load, and whether a
  single-threaded `runTest` can lose one at all.
- Whether the six `evals/` cases answer differently against the new `SKILL.md`. No CI job runs them
  (`CONTRIBUTING.md` §"Running the build"), and `evals/README.md` carries the last round.
- What `<prop>GetCount` reads for a property the code under test reads inside a `Flow` operator
  chain the test also collects; every measurement in §1.8 read properties from the test body.

## 8. Open questions

1. **Should a `StateFlow` or `SharedFlow` requirement be backed rather than constructor-seeded?**
   Measured today as a constructor parameter (§1.6). A `MutableStateFlow`-backed shape would give it
   `GetCount`, a handler and a value a test sets, and it needs a decision about what the initial
   value is for a `StateFlow` with no default. Swift 004 D11 names the same gap from the other side.
2. **Should a sink-shaped requirement record what was pushed in?** `FlowCollector` and `SendChannel`
   reach the constructor-seeded branch (§1.6). The Swift side has `<name>EventCallCount`,
   `<name>EventHandler` and `<name>Events` for `AnyObserver`, with no Kotlin counterpart in either
   repository's twin table.
3. **When does D9 (b) stop being deferred?** The trigger is a consumer measuring memory pressure or
   a test asserting on a stream it does not want recorded.
4. **Do the counters need to be thread-safe?** D10 (a) is taken on the evidence of one measurement;
   the trigger to revisit is a flaky count in a real suite.
5. **Should the skill state which release a member arrived in?** K10 forbids a version literal under
   `skills/`, and §6 point 5 is the gap that creates for an adopter on an older processor.

## 9. The cross-repository half

The member vocabulary is shared with
[swift-sourcery-templates](https://github.com/modaal-agent/swift-sourcery-templates), so this change
takes words from its 004 and owes it the records below.

**Taken unchanged from 004 §2.3 and §2.5:** `GetCount`, `GetHandler`, `SetCount`, the `_<prop>` store,
`SubscribeCount`, `SubscribeCancelCount`, `OutputCount`, `Outputs`, `OutputHandler`,
`CompletionCount`.

**Rows the twin table in `references/generated-api.md:196-215` gains** (and the matching table in
`skills/swift-sourcery-mocks/references/generated-api.md`, whose §"The same vocabulary" is the other
half):

| Kotlin | Swift |
| --- | --- |
| `<prop>GetCount`, `<prop>GetHandler` on every property | `<var>GetCount`, `<var>GetHandler` on every property (004 §2.5) |
| `_<prop>` | `_<var>` (004 §2.5) |
| `<name>SubscribeCount`, `<name>SubscribeCancelCount`, `<name>OutputCount`, `<name>Outputs`, `<name>OutputHandler`, `<name>CompletionCount` on a channel-backed `Flow` member | the same six on an `AnyPublisher` member (004 §2.7) |
| no counterpart | `<method>EventCallCount`, `<method>EventHandler`, `<name>Events` for an `AnyObserver` member (004 §2.3) |
| no counterpart | `<method>CancelCallCount`/`<method>CancelHandler` for `AnyCancellable`, `<method>DisposeCallCount`/`<method>DisposeHandler` for `Disposable` (004 D4) |
| no counterpart — selection is a build-script list | `methodName`, `handler`, `init`, `const`, `subject`, `skipArgumentRecording` |
| `<fn>Channel`, single-consumer | `<method>Subject`, broadcast to every subscriber |

**What the Swift side is owed, once this lands:** 004 §8's closing paragraph — "Until it lands, its
stored-property branch emits `SetCount` alone (`MockRenderer.kt:127-140`)" — is superseded, and 004's
P8 row for this repository's `references/generated-api.md` is satisfied by P1 and P4 here. Both are
additions to a closed section rather than edits to it (`AGENTS.md` §"Specs are an append-only
decision ledger"), so they belong in a follow-up file beside 004.

**How the two releases are sequenced (P5, P6).** Each repository implements its own half on its own
branch and opens its own PR. Neither tags until both are merged and green and the two member sets
have been compared name by name, so that a disagreement found in the comparison is fixed in the
implementation rather than in a second release. The comparison is also where a name either side wants
to change gets changed — after the tag it costs both repositories a release.

**One proposal for 004, from D6:** its §2.7 method shape returns a handler-supplied publisher before
the `handleEvents` chain, so `<name>OutputCount` counts nothing for a seeded method stream, while
the property branch counts one because the operators wrap whatever the closure returned. The Kotlin
shape wraps in both cases (§2.3, measured). Wrapping on the Swift side too is one move of the early
return inside the chain, and it makes the output counters mean the same thing on both platforms and
on both member kinds.

---

## 10. What landed

Added 2026-09-12, when P1 to P4 were implemented on this branch. Every statement here was measured on
the tree that carries them: `./gradlew clean build` green, `scripts/check-skill.sh --self-test` green
(now 14 seeded violations, one per check plus a second for K6), `cmp AGENTS.md CLAUDE.md` equal.

### 10.1 P1 — the property accessors (§2.1, §2.2)

`MockRenderer.renderProperty` (`:180-227`) emits the two shapes §2.1 and §2.2 print, character for
character. The KDoc at `MockRenderer.kt:6-36` carries the rule for a contributor, and the comment in
`render` (`:53-55`) says which property has no store.

Tests: the mutable-property test now asserts the accessor, the store and all four members; a new test
pins the read-only `val` override, its seeded store and the absence of `SetCount` and of a setter; the
bag test asserts `var _config: com.example.Config = config`; the determinism test looks for
`override val apex` and `override var zed`. In `:receipt`, `GeneratedMockReceiptTest.kt:29`'s
`environment.idleTimeoutMs = 250L` became `environment._idleTimeoutMs = 250L`, and four cases were
added: construction moves no counter, one read counts one get, the store reads and writes without
moving a counter, and `volumeGetHandler` decides a read while `_volume` keeps its value.

Documents in the same change: `README.md` §"Generated API", `CONTRIBUTING.md` §"The mock dialect is a
contract", `SKILL.md`'s member table and Properties paragraph, `references/generated-api.md`
§"Properties" — the sentence §1.7 and §1.9 item 2 name, "There is no `<prop>GetCount` for a stored
property", is gone, and the emitted shape stands where it was — and `Receipt.kt`'s per-requirement
comments.

### 10.2 P2 — the uniqueness check (§2.5, D11)

`MockRenderer.render` returns `Rendering.Rendered(text)` or `Rendering.Collision(message)`
(`:39-46`); `DeclaredNames` (`:111-147`) holds the names; `withBookkeepingNames`'s `require` is gone;
`KspMocksProcessor.generate` logs the message with `env.logger.error` and writes no file (`:76-86`).

**How the checked set is built, narrower than D11 (a) says.** D11 (a) has the set collected in the
pass that writes the text. It is instead read back out of that text: `DeclaredNames.DECLARATION`
matches a `var`, a `val` or a `data class` at two-space indentation, so the set is exactly what the
emitted file declares and cannot drift from it, and function overrides stay out because the pattern
matches no `fun`. A collision's two owners are the property name, or the function's signature —
`f(a: kotlin.Int)`.

**Where the option name is spelled.** Check K7 requires a diagnostic in one string literal and
normalises `$OPTION` to `kspMocksTargets`, so `OPTION` moved out of `KspMocksProcessor`'s private
companion to a top-level `internal const val` in the same file (`KspMocksProcessor.kt:19-22`), which
the renderer interpolates. The name is still spelled once.

Four unit tests: the three messages of §1.4, the `_<prop>` collision and §1.5's overload residue, and
one pinning that a property and a function of the same declared name are not a collision.

Measured again through the published processor, on the probe of §1.4 and §1.5 (`0.2.1-SNAPSHOT` from
`./gradlew :mocks-processor:publishToMavenLocal`):

```
e: [ksp] kspMocksTargets: probe.CollidesToday — draftSetCount is generated twice, for draft and for draftSetCount; rename one of the two interface members.
e: [ksp] kspMocksTargets: probe.Overloads — fACallCount is generated twice, for f(a: kotlin.Int) and for f(a: kotlin.String); rename one of the two interface members.
```

`find build/generated/ksp -name '*.kt'` in the probe lists nothing. §1.4's four Kotlin errors in a
generated file and §1.5's `IllegalArgumentException` are both replaced by the line above.

### 10.3 P3 — the stream counters (§2.3)

The read-only `Flow` property branch and the `Flow`-returning function branch both hang
`streamOperators` (`:296-322`) on the stream they return, and both emit `streamMembers` (`:324-337`).
Two differences from §2.3's snippets, neither of which changes a count or a member name:

1. **No local for the method's stream.** §2.3 writes `val upstream = …` and chains on it. The
   renderer emits `return (<fn>Handler?.invoke(…) ?: <fn>Channel.receiveAsFlow())` and chains on
   that, because a generated local named `upstream` would shadow an interface parameter of that name.
2. **The import block is four names or six.** §2.3 says a file carrying a channel-backed member
   imports all six. It imports `onCompletion`, `onEach`, `onStart` and `receiveAsFlow`, and adds
   `emitAll` and `flow` only when the target declares a read-only `Flow` property — the one shape that
   uses the builder — so no generated file carries an unused import (`MockRenderer.kt:80-90`).

The `onEach { value -> … }` and `onCompletion { cause -> … }` lambdas shadow an interface parameter of
either name, and Kotlin 2.4.10 compiles that without a warning: a probe interface declaring
`fun stream(value: String, cause: Int): Flow<String>` generated the chain over both and
`gradle clean compileTestKotlin` printed no `w:` line. The same probe is where the four-import case
above was read.

`:receipt` carries the §1.8 checks against real generated output: the read that counts no
subscription, one collection counting 1 subscription / 2 outputs / 1 completion / 0 cancels with
`configUpdatesOutputs` holding the values, `first()` counting a cancellation and no completion, a
`configUpdatesGetHandler` seeded after the property was read deciding the stream, both of a function's
stream paths counted, and `eventsOutputHandler` sending the value that follows from inside itself.

Documents: `README.md`, `CONTRIBUTING.md`, `SKILL.md`'s member table and §"Streams are channel-backed
and count what they deliver", `references/generated-api.md` §"A method returning `Flow`" and
§"Properties", and `references/troubleshooting.md` §"A stream counter reads 0 when the test expected
1".

### 10.4 P4 — the twin table, the measured gaps, and the gate

`references/generated-api.md`'s twin table gained the rows §9 lists, with two differences: the
`AnyObserver` row is spelled `<method>Events` rather than `<name>Events`, so that no `<name>`
placeholder appears under `skills/`, and the `AnyCancellable` and `Disposable` members share one row.
The file stands at 240 lines against K5's 250.

`SKILL.md` states §1.6's measurement: only an exact `Flow<E>` property is channel-backed, and a
`StateFlow`, a `SharedFlow` or a sink-shaped requirement is an ordinary constructor-seeded property.

K6 grew by the two patterns D13 (a) names and by one more. The renderer side reads `_${name}` as the
key `_`; the skill side reads `_<prop>`, `_<fn>` and `_<name>` the same way; and the suffix side
accepts `<name>Suffix` beside `<fn>`, `<prop>` and `<Fn>`, so a member the skill spells with `<name>`
is gated as well — the skill spells none that way today. `--self-test` gained a second K6 case,
`K6_store`, seeded with the sentence "The store is spelled `_<prop>Value`."; `expect_red` names the
case when it differs from the check. `AGENTS.md` §"State a rule once" now says which interpolations
K6 reads, and `CLAUDE.md` is its byte copy.

### 10.5 What the two receipt mocks carry

48 bookkeeping members — the number §1.2 derived — counted with `grep -cE '^  (var|val) '` over
`receipt/build/generated/ksp/test/kotlin/dev/modaal/mocks/receipt/`: `ReceiptEnvironmentMock` 42,
`ReceiptDependencyMock` 6, against 21 and 0 at `93c93d2`. `MockRendererTest` holds 15 tests, up from
10; `GeneratedMockReceiptTest` 15, up from 12.

### 10.6 What P1 to P4 did not touch

P5 and P6 are untouched: no member set has been compared with the Swift implementation, `CHANGELOG.md`
carries no 0.3.0 entry, and the development version literal in `build.gradle.kts:18` still reads
`0.2.1-SNAPSHOT`. §7's list and §8's five questions stand as written.

---

## 11. P5 — measured against the Swift implementation

Added 2026-09-12, after P1 to P4 landed here. The Swift half is `spec/004-mock-member-naming` at
`c332601` in `swift-sourcery-templates` — `origin/spec/004-mock-member-naming`, fetched and read on
2026-09-12 — where 004's P1 to P11 are implemented and its §11 records what landed. Nothing in this
section is implemented here, and D15 to D18 are proposed rather than ruled.

Read there: `templates/Mocks/MockNaming.swift` (339 lines, the one place a Swift mock member's name
is built), `MockVar.swift`, `MockMethod.swift`, `SourceryRuntimeExtensions.swift`'s publisher branch
(`:430-490`), `MockGenerator.swift`'s `MockError` (`:4-26`, `:177-212`),
`Tests/Checks/Snapshots/Mocks.generated.swift` (1,819 lines of generated output the fast lane
checks), `skills/swift-sourcery-mocks/references/generated-api.md` §"The same vocabulary in the
Kotlin twin" (`:220-245`), and `specs/004-mock-member-naming/spec.md` §10, §11 and D14 to D17.

### 11.1 What agrees

| the member | this processor | swift-sourcery-templates | read from |
| --- | --- | --- | --- |
| method | `<fn>CallCount`, `<fn>Args`, `<fn>Handler` | `<method>CallCount`, `<method>Args`, `<method>Handler` | `MockNaming.swift:216-218` |
| property | `<prop>GetCount`, `<prop>GetHandler`, `<prop>SetCount`, `_<prop>` | the same four suffixes and the same store prefix | `MockNaming.swift:226-239` |
| stream | `SubscribeCount`, `SubscribeCancelCount`, `OutputCount`, `Outputs`, `OutputHandler`, `CompletionCount` | the same six words | `MockNaming.swift:248-253` |
| stream fallback | `<fn>Channel` | `<method>Subject` | different by decision (§9's twin table) |
| unset handler | `"<fn>Handler expected to be set."` | the same string, `fatalError` rather than `error` | `MockNaming.swift:275-277` |

Four shapes agree beyond the names:

- **The property accessor body and the order its members are emitted in.** The Swift snapshot's
  `draft` (`Mocks.generated.swift:225-241`) increments `GetCount`, consults `GetHandler`, returns
  `_draft`, and counts `SetCount` in the setter before assigning the store; the members follow in the
  order witness, `GetCount`, `GetHandler`, `SetCount`, `_draft`. §2.1's emission is the same
  statement for statement and in the same order.
- **The stream member order.** Swift emits `GetCount`, `GetHandler`, `SubscribeCount`,
  `SubscribeCancelCount`, `OutputCount`, `Outputs`, `OutputHandler`, `CompletionCount`, then the
  subject (`Mocks.generated.swift:416-438`); §2.3 emits the same sequence with the channel last.
- **The overload rule.** `makeUniqueByUsingLongNamesExceptForFewestArgumentMethod`
  (`MockMethod.swift:389`) keeps the plain name for the overload with the fewest parameters and gives
  every other one the capitalized words the caller writes — argument labels there, parameter names
  here, which is what a Kotlin caller writes. `end(at:)` gives `endAt*`; `update(id, force)` gives
  `updateIdForce*`.
- **A collision refuses generation.** `MockNaming.checkForCollisions` (`:321-330`) reads the names
  back out of the emitted declarations, as `DeclaredNames` does (§10.2), and raises
  `MockError.collidingMemberNames` instead of writing a file that does not compile.

### 11.2 Five divergences

1. **A read-only requirement's witness is settable there and not here.** `MockVar.mockImpl`'s
   `hasSetter` (`MockVar.swift:178`) emits a setter for a `{ get }` requirement, and that setter
   moves no counter: the snapshot's `analytics` is `get { … } set { _analytics = newValue }` with no
   `analyticsSetCount` (`Mocks.generated.swift:210-224`). The comment above it records why —
   a re-seed by assignment worked before 004 and still works. D2 (a) here took the other option,
   (c): the override is `val`, and `mock._<prop> = value` is the only seed path. The member
   vocabulary is the same on both sides; what differs is that `mock.<prop> = value` compiles on the
   Swift side and does not here. `mock._<prop> = value` compiles on both, which is the assignment a
   test written for both platforms uses. D15.
2. **A method's handler-supplied stream is counted here and not there.** `replay(tag:)` returns
   `__replayHandler(tag)` before the `Deferred`/`handleEvents` chain
   (`Mocks.generated.swift:441-463`), so a seeded method stream moves no `OutputCount`,
   `SubscribeCount` or `CompletionCount`; the property branch reads its handler **inside** the
   `Deferred` and is counted. §2.3 wraps both. §9's proposal therefore stands unadopted, and what it
   costs there is more than moving the early return: Combine counts the subscription inside the
   `Deferred` closure, so a handler-supplied publisher needs its own `Deferred` — `Deferred { self?.<name>SubscribeCount += 1; return handler(args) }` — for `SubscribeCount` to move.
3. **The collision diagnostic says different things.** Here:
   `kspMocksTargets: <fqn> — <member> is generated twice, for <a> and for <b>; rename one of the two
   interface members.` There (`MockGenerator.swift:202-209`): `` `<Type>Mock` would declare
   `<member>` twice `` plus the cause and `/// sourcery: methodName = "customName"` as the escape
   hatch. This side names the two declarations behind the name; that side names the annotation, which
   has no counterpart here. Neither string is part of the shared contract, which is the member names
   and `"<fn>Handler expected to be set."`.
4. **The opt-out and the members with no counterpart are as §9's twin table records them**, with one
   addition measured here: `/// sourcery: skipArgumentRecording` turns `<var>Outputs` off as well as
   `<method>Args` (`MockVar.swift:76-78`, `SourceryRuntimeExtensions.swift:461-467`), so the Swift
   side can generate a stream member that counts without recording. D9 (a) has no equivalent, and D9
   (b) is where one would go.
5. **The tie-break inside an overload group is not the same rule.** Swift orders by parameter count
   and then prefers the overload with no argument label (`MockMethod.swift:389-400`); this side
   orders by parameter count and then by the joined rendered parameter types
   (`MockRenderer.kt:161-178`). Two overloads with the same parameter count can therefore keep the
   plain name on one platform and take the long form on the other. Not measured on either side: no
   fixture in either repository declares such a pair.

### 11.3 A gap this comparison found: a keyword-named requirement

Swift's §2.1 is "the declared name with backticks removed" (`MockNaming.swift:31-42`, `:67-69`): the
witness keeps the backticks the declaration carries, the bookkeeping members drop them. This
processor has no such rule — it writes `property.name` and `function.name` raw at both places.

Measured on 2026-09-12 against this branch, published with
`./gradlew :mocks-processor:publishToMavenLocal` and consumed by the probe project of §1.4:

```kotlin
interface Keywords {
  val `object`: String
  var `interface`: Int
  fun `in`(`val`: Int): Int
}
```

emits `override var interface: kotlin.Int`, `override val object: kotlin.String`,
`override fun in(val: kotlin.Int)` and `inArgs.add(val)`, and the consumer's test compilation fails
with **48 errors** on `KeywordsMock.kt`, beginning
`Class 'KeywordsMock' is not abstract and does not implement abstract members:` and
`:7:15 Syntax error: Expecting property name or receiver type.` The bookkeeping members the same run
emitted are already right: `interfaceGetCount`, `objectGetHandler`, `_object`, `inCallCount`.

The rule that closes it: escape a name that is a Kotlin keyword at every declaration and call site
the renderer emits — the override's name, the constructor parameter, the parameter list, the
forwarded argument list and a `<Fn>Args` data class's property names — and leave every bookkeeping
member name bare, which is what `withoutBackticks` does on the Swift side. D18.

### 11.4 004's in-file naming comments, judged for this processor

What landed there (004 §10, D14 to D16, their P10): a five-line file header stating the rule, two
header lines under every class's `// MARK:`, an index of the class's renamed members under that, the
same comment line above each renamed witness, and `run-checks.sh`'s fifth gate reading both back out
of the generated file. Measured there: 47 renamed members in 10 of 34 classes, and 2,934 → 3,148
lines in their reference consumer, all of it comments.

Three differences decide the shape a port takes here:

1. **One cause, not three.** An overload's long form is the only prefix this processor produces that
   a reader cannot derive from the declaration. Kotlin rejects two functions whose signatures differ
   only in return type, so 004 §2.2 step 3's discriminator has no input here, and target selection is
   a build-script list, so there is no `methodName` annotation to record.
2. **One class per file.** `<Interface>Mock.kt` holds one class, so a file header is in view wherever
   the class is, and 004 D14 (b)'s per-class header and D15 (c)'s per-class index have nothing to do
   that the header does not already do. Their file carries 34 classes and 3,148 lines, which is what
   those two exist for.
3. **No renamed member exists in this repository's generated output.** The two files `:receipt`
   generates carry six functions and no overload. The rename is pinned by `MockRendererTest`'s
   overload test (`updateIdForce`), not by a generated file, so a gate here reads the renderer's
   output in a unit test rather than a generated file in a shell script.

### 11.5 004's file-finding guidance, judged for this processor

What landed there (004 D17 (a), (a2), their P11): `references/spm-plugin.md` §"Finding the generated
file on disk" with both lanes' path shapes, one `find`, the `xcodebuild -showBuildSettings` line for
relocated derived data and the plugin's own build-log remark; a `references/troubleshooting.md`
section; and the `find` in `SKILL.md`. The problem it solves is a build root whose path a reader
cannot guess.

Here the paths are already stated for a reader who knows the module shape: `SKILL.md`'s module table
carries them as a column — `build/generated/ksp/test/kotlin/`, `.../jvm/jvmTest/...`,
`.../debugUnitTest/...`, `.../testFixtures/...` — `references/gradle-wiring.md` states each again
with its source set, and `references/troubleshooting.md` ends by sending the reader to the file under
`build/generated/ksp/`. The gap is the agent that does not know which of the four shapes it is in, and
`./gradlew :<module>:kspTestKotlin --info` (`troubleshooting.md`, §"Unresolved reference") is already
the equivalent of the build-log remark. D17.

### 11.6 Decisions this section proposes

None is ruled. Each changes the generated output or the skill, so each lands before the tag if it
lands at all (§6, a published version is immutable).

**Ruled on 2026-09-12: D15 (c), D16 (a), D17 (a), D18 (c) — §12.1.** The recommendations below for
D15 and D18 are not the options taken; §12 is what stands.

**D15 — the read-only requirement's witness.**

- **(a) Keep `val` here, and record the difference in both twin tables (recommended).** D2 (a) was
  taken for a reason that still holds: the override has the shape the requirement declares, and
  `_<prop>` is one seeding path for every property. The tables gain the row "`mock._<prop> = value`
  seeds a read-only requirement on both platforms; `mock.<prop> = value` compiles on the Swift side
  only."
- (b) Adopt the Swift shape: `override var` with an uncounted setter. `mock.<prop> = value` keeps
  compiling, which retires §5 item 2 and the one source break in this change, at the cost of a write
  that moves no counter on some properties and `SetCount` on others — 004's own D-note calls that
  backward compatibility with tests written before it.
- (c) Ask the Swift side to adopt `val`-equivalent (a get-only witness). It is their source break,
  in a consumer with 34 mock classes, for a difference a test can already avoid by assigning
  `_<var>`.

**D16 — port the in-file naming comments.**

- **(a) The file header and the per-member comment, and no index (recommended).** Three lines added
  to every generated file, under the two the header already carries:

  ```
  // Member names are the requirement's declared name plus a suffix: `fun load()` gives loadCallCount,
  // loadArgs and loadHandler; `var name` gives nameGetCount, nameGetHandler, nameSetCount and the store
  // _name. An overload that does not keep the plain name carries a comment above it.
  ```

  and, above an overload that took the long form:

  ```kotlin
  // `update(id, force)` members are named updateIdForce* — overload of update, parameter names appended
  override fun update(id: kotlin.String, force: kotlin.Boolean) {
  ```

  The comment comes from `withBookkeepingNames`, which is where the long form is decided, so a
  comment that disagrees with the member under it cannot be emitted (004 D16 (a)). A
  `MockRendererTest` case pins both, and the two `:receipt` files grow by three lines each.
- (b) All of 004's shape, the per-class header and index included. Two more lines per file and an
  index that repeats what the comment above each member already says, in a file that holds one class.
- (c) None. `references/troubleshooting.md` §"A generated member has a name the test did not expect"
  stays the only place the long form is explained, and it is read only by someone who already went
  looking.

**D17 — port the file-finding guidance.**

- **(a) One `find` in `SKILL.md`, after the module table (recommended).**

  ```bash
  find . -path '*/build/generated/ksp/*' -name '<Interface>Mock.kt'
  ```

  It covers every module shape in the table, including the two an agent cannot classify from the
  build script alone, and `SKILL.md` is 221 lines against K5's 400.
- (b) (a) plus a `references/troubleshooting.md` section on the same subject. All three reference
  files are within 11 lines of K5's 250-line cap (236, 239, 240), so this one pays for itself by
  trimming another section, as 004's P10 did with `generated-api.md`.
- (c) None. The four paths in the module table stay the whole answer.

**D18 — a keyword-named requirement (§11.3).**

- **(a) Escape at the declaration and call sites, leave the member names bare (recommended).** It is
  Swift's §2.1 rule, it changes no existing generated byte — no interface in `:receipt` or in any
  fixture declares such a name — and it turns 48 errors in a file the adopter must not edit into a
  file that compiles. A `:receipt` interface member and a `MockRendererTest` case pin it.
- (b) Refuse generation for such an interface, with a diagnostic naming the member, as generic
  interfaces and `vararg` parameters are refused (`KspMocksProcessor.kt:71`, `:133`). Cheaper to
  write, and it leaves the adopter to rename a requirement that Kotlin allows.
- (c) Leave it. The 48 errors stay, and the reader is not told which requirement caused them.

### 11.7 What P5 leaves open

- **The Swift skill's twin table is stale in three rows** now that P1 to P3 have landed here, and its
  own gate does not compare the Kotlin column (as K6 here does not compare the `<method>` column).
  `skills/swift-sourcery-mocks/references/generated-api.md:220-245` still reads
  "`<prop>GetCount`, `<prop>GetHandler` — on a `Flow` property only", "a stored property has
  `SetCount` alone; no read counter, no handler, no store", and "none — a `Flow` property is the
  channel, unwrapped" for the six stream members. The correction is a change in that repository.
- **004 §8's closing paragraph and its P8 row** are superseded by P1 to P4 here, which §9 records as
  belonging in a follow-up file beside 004 — also a change in that repository, and not made here.
- **D6's proposal for 004 §2.7 is not settled**, contrary to what §4's P5 paragraph expected: the
  Swift side returns a method's handler-supplied publisher uncounted (§11.2 item 2), and adopting the
  wrap there is a Swift-side change with a Combine-specific cost.
- **P6 waits on the D15 to D18 rulings.** D16 (a), D17 (a) and D18 (a) each change what the processor
  writes or what the skill says, and a published version is immutable, so whichever of them is taken
  lands before the 0.3.0 tag rather than in a 0.3.1.

---

## 12. The rulings, and the work each repository takes on

Ruled on 2026-09-12: **D15 (c), D16 (a), D17 (a), D18 (c)**. Two of the four differ from §11.6's
recommendation, which §11.6 now points here for.

### 12.1 The four rulings

**D15 — (c). A read-only requirement is read-only, and the Swift side adopts.** `override val` stays
here and `mock._<prop> = value` stays the seed path. The settable witness `swift-sourcery-templates`
emits for a `{ get }` requirement (§11.2 item 1) becomes get-only there. §12.3 item 2 is what that
takes.

**D16 — (a). The file header and the per-member comment, no per-class index**, pinned by
`MockRendererTest`. §12.2.

**D17 — (a). One `find` in `SKILL.md`, after the module table.** §12.2.

**D18 — (c). No keyword escaping.** The two platforms' source declarations are not required to be the
same declarations, and each language's reserved words stop at its own boundary: `object` and `in` are
Kotlin keywords and ordinary Swift identifiers; `func` and `guard` are the reverse. An interface that
names a requirement with a Kotlin keyword is the adopter's to rename. What §11.3 measured stands
unchanged — such an interface generates a file that fails the consumer's compile with 48 errors, and
no diagnostic names the requirement — and neither the processor nor the skill says so. The option to
revisit to is D18 (b), refusal with the member named; the trigger is an adopter reporting it.

### 12.2 What this repository does before the tag (D16, D17)

Neither ruling moves or adds a member name, so K6 and K7 compare the same sets they compare today,
and `GeneratedMockReceiptTest` keeps every assertion it has.

**D16 (a) — three lines in every generated file, and one above a renamed overload.**

1. `MockRenderer.render` writes the header after the two lines it writes today (`:77-78`):

   ```
   // Member names are the requirement's declared name plus a suffix: `fun load()` gives loadCallCount,
   // loadArgs and loadHandler; `var name` gives nameGetCount, nameGetHandler, nameSetCount and the store
   // _name. An overload that does not keep the plain name carries a comment above it.
   ```

2. `withBookkeepingNames` (`:161-178`) returns the comment beside the bookkeeping name, so the
   comment is built in the branch that appends the capitalized parameter names and a comment that
   disagrees with the member under it cannot be emitted (004 D16 (a)). `renderFunction` writes it
   above the override:

   ```kotlin
   // `update(id, force)` members are named updateIdForce* — overload of update, parameter names appended
   override fun update(id: kotlin.String, force: kotlin.Boolean) {
   ```

3. `MockRendererTest` gains two cases: the header in a target with no overload, and the comment above
   `updateIdForce` with none above `update`. The overload test already pins both names.
4. `skills/kotlin-ksp-mocks/references/generated-api.md` §"The file" quotes the generated header, so
   it takes the three lines, and §"Overloads" takes the comment. `references/troubleshooting.md`
   §"A generated member has a name the test did not expect" gains the sentence that the file names it
   itself. The file is at 240 lines against K5's 250.
5. `:receipt`'s two generated files grow by three lines each; the `CHANGELOG.md` entry of §6 states
   the header as generated output.

**D17 (a) — one command in `SKILL.md`.** After the module table, for the case where the module shape
is not known:

```bash
find . -path '*/build/generated/ksp/*' -name '<Interface>Mock.kt'
```

`./gradlew :<module>:kspTestKotlin --info` (`references/troubleshooting.md`, §"Unresolved reference")
is already the answer to "did KSP run at all", which is what 004's P11 added the build-log remark
for. `SKILL.md` goes from 221 lines to about 225, against K5's 400.

The gate for both: `./gradlew clean build`, the emitted files under `receipt/build/generated/ksp/`
read by hand, and `scripts/check-skill.sh`.

### 12.3 What `swift-sourcery-templates` does

Four items. Each is a change in that repository, made under its own rules — a spec branch, its
`run-checks.sh` gates, its own CHANGELOG. Items 1 and 2 are what D15 (c) and the comparison require;
item 3 is the open proposal of §9 and D6; item 4 is the record 004 owes.

**1. The twin table in `skills/swift-sourcery-mocks/references/generated-api.md:220-245`.** Three
rows describe this processor as it was before P1 to P3, and that repository's skill gate reads its
own renderer, not this one, so nothing there goes red on them. In the first table
(Swift → Kotlin), these rows replace what stands:

| Swift | Kotlin |
| --- | --- |
| `<var>GetCount`, `<var>GetHandler` | `<prop>GetCount`, `<prop>GetHandler`, on every property |
| `<var>SetCount` | `<prop>SetCount`, on a `var` requirement |
| `_<var>` | `_<prop>` — the store a test seeds and reads without moving a counter |
| `<name>Subject` for an `AnyPublisher` member, broadcast to every subscriber | `<fn>Channel` for a `Flow` member, single-consumer |
| `<name>SubscribeCount`, `<name>SubscribeCancelCount`, `<name>OutputCount`, `<name>Outputs`, `<name>OutputHandler`, `<name>CompletionCount` | the same six, on a `Flow`-returning function or a read-only `Flow` property |

In the second table, "Members with no counterpart there", two rows go: the one reading
"`<var>GetCount` / `<var>GetHandler` / `_<var>` on **every** property | a stored property has
`SetCount` alone; no read counter, no handler, no store", and the one reading "…the six stream
members | none — a `Flow` property is the channel, unwrapped". The `AnyObserver`, `AnyCancellable` /
`Disposable` and per-declaration-annotation rows stay. Until item 2 lands, one row is added and then
removed with it:

| this side | Kotlin |
| --- | --- |
| a settable witness for a `{ get }` requirement | the override is `val`; a test assigns `_<prop>`, which compiles on both sides |

Two prose claims move with the rows: `references/generated-api.md:132-133` ("a read-only
requirement's witness is still settable, and re-seeding it counts nothing") and
`CONTRIBUTING.md:242-243` ("The witness stays settable wherever it was settable before that change").
Both become the get-only rule when item 2 lands.

**2. D15 (c) — the get-only witness for a `{ get }` requirement.**

- `MockVar.swift:178` becomes `let hasSetter = !hasEffects && variable.isMutable`, and the
  `if variable.isMutable && hasSetter` guard on `<var>SetCount` (`:195`) reduces to `hasSetter`. The
  store stays `var _<var>` for everything but `const`, so `_<var>` remains the seed path; the
  `handler` and `const` branches are already get-only and do not move.
- The emitted-rule table in `specs/004-mock-member-naming/spec.md:1245-1252` takes one row's new
  value: `{ get }` → witness `get` only, `<var>SetCount` not emitted.
- **What it breaks there, already measured by them:** three assignments in their own
  `Tests/Checks/Behaviour/Main.swift` — `recordPermission` at `:45`, `installationId` at `:110` and
  `:422` — fail with `cannot assign to property: … is a get-only property`, and each becomes
  `_recordPermission` / `_installationId`. The count in `modaal-firebase-wrappers` is not measured;
  the compiler names every occurrence, and `_<var>` is the replacement at each.
- `MockVar.swift:167-177`'s comment, which records why the witness stayed settable, is what the
  change supersedes; the new rule is that a read-only requirement is read-only and `_<var>` is how a
  test seeds it.
- Their `CHANGELOG.md` states it as a source break with the one-line fix, the way §6 states this
  side's.

**3. The open proposal of §9 and D6 — wrap a method's handler-supplied publisher.** Not ruled, and
not part of D15 to D18. `MockMethod`'s publisher branch returns `__<name>Handler(args)` before the
`Deferred` / `handleEvents` chain (`Tests/Checks/Snapshots/Mocks.generated.swift:441-463`), so a
seeded method stream moves no counter there while it does here (§11.2 item 2). Wrapping it means
giving the handler's publisher its own `Deferred` — `Deferred { self?.<name>SubscribeCount += 1;
return handler(args) }` — before the existing chain, because Combine counts the subscription inside
that closure. Until it lands, `<name>OutputCount` means "values delivered" on both platforms for a
property and for a channel-backed method, and "values delivered by the channel only" for a
handler-seeded method there.

**4. The record 004 owes.** Under that repository's append-only rule this goes in a follow-up file
beside 004 rather than into its sections: 004 §8's closing paragraph ("Until it lands, its
stored-property branch emits `SetCount` alone") and its P8 row for this repository's
`references/generated-api.md` are both superseded by P1 to P4 here, and D15 (c) supersedes the
settable-witness rule its P5 decided.

### 12.4 D15 (c) stated as one rule, and what it costs the Swift side

Added 2026-09-12, when the ruling was read back: **`mock._<prop> = value` is the only assignment that
seeds a read-only requirement, and it is the same expression on both platforms.
`mock.<prop> = value` compiles on neither.** §12.1's D15 paragraph says the same thing from the two
sides; this is the rule in one sentence.

Here it is what P1 already emits (§2.2, D2 (a)). `_<prop>` is a `var` the constructor seeds, so the
value is updatable at any point in a test — `GeneratedMockReceiptTest`'s
`environment._idleTimeoutMs = 250L` is that assignment — and the requirement's own name is read-only,
as the interface declares it. There it is the get-only witness of §12.3 item 2.

**Viable on the Swift side, and the break lands only on code holding the concrete mock type.**

1. A computed get-only `var x: T { … }` satisfies a `{ get }` requirement, and it is the shape
   `MockVar.accessor` already emits when `setter` is `nil` (`MockVar.swift:97-111`): the publisher
   property, `/// sourcery: const` and `/// sourcery: handler` all take it today. The change is
   `hasSetter`, not the emission.
2. **Nothing can assign such a requirement through the protocol.** A `{ get }` requirement exposes no
   setter on an existential or a generic parameter, so every assignment that stops compiling is one
   written against the concrete `<Type>Mock` — a test, or a helper that seeds mocks. Their own
   measurement found three, all in `Tests/Checks/Behaviour/Main.swift` (§12.3 item 2); the count in
   `modaal-firebase-wrappers` is not measured, and the compiler names each one.
3. `const` and `handler` are unaffected: both are get-only already, `const`'s `let _<var>` is fixed
   at construction by design, and `<var>GetHandler` is the seed path where there is no store.

**No deprecation window is available for it.** Swift's `@available(*, deprecated)` marks a property,
not one accessor, so a release that keeps the setter and warns on it cannot be written. The change
lands as `cannot assign to property: … is a get-only property` at each site, with `_<var>` the
replacement.

### 12.5 How a read-only requirement gets its first value, on both platforms

Added 2026-09-12, with §12.4: that section states how a seeded value is **updated**, this one how it
is **seeded at construction**. The two sides emit the same shape.

| | this processor | swift-sourcery-templates |
| --- | --- | --- |
| what reaches the constructor | a requirement, read-only or `var`, whose type has no guessable default (`MockRenderer.kt:56-57`) | the same rule, `MockVar.provideValueInInitializer` (`MockVar.swift:26-30`) |
| what a defaultable type does instead | the store is seeded with the literal — `var _idleTimeoutMs: kotlin.Long = 0L` | the same — the store is seeded with the smart default |
| the parameter's name | the requirement's declared name | the requirement's declared name |
| what the parameter assigns | `_<prop>`, so construction moves no counter | `self._<var>`, the same (`Mocks.generated.swift:274-277`) |
| a stream member | a read-only `Flow` property has no store and no parameter; the channel is the seed | a publisher property has no store and no parameter; the subject is the seed |

```kotlin
class ReceiptDependencyMock(config: ReceiptConfig) : ReceiptDependency {   // this side
```
```swift
init(analytics: AnalyticsTracking, memoryRepository: MemoryRepositoryProtocol) {   // the Swift side
```

**One difference, from the annotations this processor has no counterpart for.**
`/// sourcery: init` forces a requirement whose type *has* a default into the initializer there, and
`/// sourcery: handler` keeps one out of it (that requirement has no store at all). Neither has an
input here — targets are a build-script list — so a defaultable requirement is seeded by the literal
and then by `_<prop>` assignment, and never by a constructor parameter. §9's twin-table row for the
annotations already carries the cause; this is the consequence a test author sees.

**Nothing in §2 or §5 changes under D15 (c).** §2.2's first bullet already states that the
constructor parameter keeps the declared name and initializes `_<prop>`; §2.2's third bullet and §5
item 2 already state `mock._<prop> = value` as the seed path and the one source break. The ruling
adds the Swift side to that rule (§12.3 item 2); it moves nothing on this side.

---

## 13. What landed for D16 and D17

Added 2026-09-12, when §12.2's two rulings were implemented. `./gradlew clean build` green,
`scripts/check-skill.sh` green, `cmp AGENTS.md CLAUDE.md` equal. D15 (c) and D18 (c) need no code
here, and the work they name in `swift-sourcery-templates` (§12.3) is untouched.

### 13.1 D16 (a) — the rule in the file, and the comment above a renamed overload

Every generated file opens with three lines after the two it carried
(`MockRenderer.kt:83-91`), and they are the same three in every file:

```
// Member names are the requirement's declared name plus a suffix: `fun load()` gives loadCallCount,
// loadArgs and loadHandler; `var name` gives nameGetCount, nameGetHandler, nameSetCount and the store
// _name. An overload that does not keep the plain name carries a comment above it.
```

`withBookkeepingNames` (`:199-216`) returns `Bookkeeping(function, name, comment)` (`:124-128`) in
place of a pair, and `bookkeeping` (`:130-131`) decides the comment by the bookkeeping name against
the declared name — not by which branch produced it, which is 004 D16 (a)'s rule — so a comment that
disagrees with the member under it cannot be emitted. `namingComment` (`:138-141`) is the one place
the line is spelled, and `renderFunction` writes it directly above the override (`:291`).

**Measured through the published processor**, `./gradlew :mocks-processor:publishToMavenLocal` into
the probe consumer of §1.4, on an interface declaring `update(id)` and `update(id, force)`:

```kotlin
  data class UpdateIdForceArgs(
    val id: kotlin.String,
    val force: kotlin.Boolean,
  )
  // `update(id, force)` members are named updateIdForce* — overload of update, parameter names appended
  override fun update(id: kotlin.String, force: kotlin.Boolean) {
```

The overload that kept the plain name carries no comment, and the file compiles in the consumer's
test compilation. Two things the shape settles, which §12.2 did not state:

- The comment sits **below** the nested `<Fn>Args` data class and directly above the override, since
  the data class is emitted first. The class is renamed with the rest — `UpdateIdForceArgs` — which
  is what the comment's `updateIdForce*` covers.
- `:receipt` exercises the header only: its two interfaces declare no overload, so the comment is
  measured in the probe above and pinned by `MockRendererTest`'s two new cases (17 tests, up from
  15). Adding an overload to the receipt surface would move the member count §6's changelog entry
  states, and was not done.

The two generated files grow by three lines each — `ReceiptDependencyMock.kt` 28 → 31,
`ReceiptEnvironmentMock.kt` 151 → 154 — and no member name moves, so `GeneratedMockReceiptTest` is
unchanged at 15 tests.

### 13.2 D17 (a) — one command that finds the file

`SKILL.md` gains it after the module table, for the case where the module shape is not known:

```bash
find . -path '*/build/generated/ksp/*' -name '<Interface>Mock.kt'
```

with the sentence that an empty result means KSP did not run for that source set, which
`./gradlew :<module>:kspTestKotlin --info` reports. `SKILL.md` is 230 lines against K5's 400.

### 13.3 The documents, and one budget that is now spent

`references/generated-api.md` §"The file" carries the three header lines in its snippet and one
sentence for them; §"Overloads" carries the comment above the renamed override.
`references/troubleshooting.md` §"A generated member has a name the test did not expect" says the
generated file names it.

`generated-api.md` is at **exactly 250 lines**, K5's cap for a reference file. Three sentences were
tightened to pay for the twelve lines this added — the §"The file" prose, the §"Overloads" lead-in
and the stream paragraph of §"A method returning `Flow`" — and the next addition to that file has to
pay the same way.

### 13.4 What the release entry takes from this

§6 point 1's `CHANGELOG.md` entry gains the naming header as generated output, with the two file
sizes above, beside the member counts it already states. No member name moved, so the *Breaking*
paragraph is unchanged.

### 13.5 The token budget K5 does not read, measured

`SKILL.md` is held by a second budget: the compaction floor keeps the first 5,000 tokens of a loaded
skill, which `swift-sourcery-templates`' `specs/002-annotation-registry-and-agent-skill/spec.md` §3.1
established and its §15 records being breached twice while the line cap passed. Nothing in this
repository had measured it. Measured on 2026-09-12 with that repository's §15.1 tool — a local
marketplace, a local install, and `claude plugin details`, which prints the on-invoke cost from the
working tree:

| `SKILL.md` | chars | lines | on-invoke |
| --- | ---: | ---: | ---: |
| with D16 and D17 landed, before this measurement | 14,701 | 230 | **~5.2k — over the floor** |
| the failure table compressed to symptom → action | 13,908 | 230 | ~5k |
| five passages a reference already carries, compressed | 13,409 | 227 | **~4.8k** |

Always-on is ~290 throughout, unchanged by any of it. K5 was green at every step: 230 lines against
its 400.

**What moved, and where the detail went.** Nothing was deleted outright. The failure table lost its
`cause` column and kept all thirteen rows — `references/troubleshooting.md` carries each symptom as
its own section, with the text to match and the command to confirm it. The `<fn>Args` and
`<fn>Handler` rows, the unset-handler list and the four stream rules were compressed against
`references/generated-api.md`, which states each in full. Every instruction the body carried before
the trim is still in it.

**The rule now has a home.** `AGENTS.md` §"The skill under `skills/` teaches adopters" carries the
floor, the four commands that measure it, and the instruction to keep the body near 4.8k, so the next
addition to the body is paid for rather than discovered at a release. No check measures it — that is
the same open item `swift-sourcery-templates` records in its §15.6, and for the same reason:
`check-skill.sh` runs with `grep`, `awk` and `python3` and no toolchain, while this number needs the
`claude` CLI and an install.
