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
