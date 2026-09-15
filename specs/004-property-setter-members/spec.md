# 004 — A `var` requirement's setter handler and recorded writes

**Status:** Proposed. Nothing here is implemented. §1 is what was measured, §2 the shape this proposes,
§3 eight decisions with a recommendation in each — none is ruled — and §4 the phasing.

**Ruled on 2026-09-16 — §9:** D1 to D8, each as recommended.

**Ruled on 2026-09-15, in the request that asked for this spec:**

- a `var` requirement generates the two members `swift-sourcery-templates`' spec 007 rules for a
  `{ get set }` requirement: a setter handler called on every write, and a recorder of every value
  written. 007 §8.1 ruled that the Swift `0.10.2` is released together with a release of this
  processor that emits them, and 007 §8.2 lists that counterpart;
- the release here is a patch, synchronized with the Swift release: `0.3.1` beside `0.10.2`.

`AGENTS.md` §"Read the generated output, do not commit it" requires a stop before a change to the
member vocabulary. The request is the answer to that stop; 007 §7.1 D1 is where the names were ruled.

**Measurements:** every number, path and quoted line in §1 was produced on 2026-09-15 on macOS 26.6.2
(25G83) with Temurin 25.0.4.1, Gradle 9.7.1, Kotlin 2.4.10, KSP 2.3.11, kotlinx-coroutines 1.11.0 and
Claude Code 2.1.271, against `main` at `0aa6c10`. The Swift side was read in `swift-sourcery-templates`
on its branch `spec/007-property-setter-members` at `bdf04f9`. The probes ran in copies of this
checkout in the session scratchpad and changed no file here:

| probe | what it is |
| --- | --- |
| `proto` | a copy of this checkout without `.git` and the build directories, built in five stages, s0 to s4 (§1.4). From s1 on, `Receipt.kt`'s `ReceiptEnvironment` declares `var onVolumeChange: ((Double) -> Unit)?`, and a new `SetterShapes.kt` declares the interface `SetterShapes`, added to `receipt/build.gradle.kts`' `kspMocksTargets` (§1.5). From s2 on, a `SetterProbeTest.kt` under `receipt/src/test/` runs 14 checks in 9 tests (§1.6) |
| `collision` | `proto` at s4 with `CollidingSetArgs` or `CollidingSetHandler` added as a target, one per build, then removed (§1.7) |
| `skillmeasure` | a second copy, with the plugin installed at local scope by the commands `AGENTS.md` §"The skill under `skills/` teaches adopters" gives, and `proto`'s s4 `MockRenderer.kt` copied in for `scripts/check-skill.sh` (§1.8). Plugin state before and after: one marketplace, `claude-plugins-official`, and one user-scope plugin, `swift-lsp@claude-plugins-official` |

**Scope of the change:**

- the processor: `MockModel.kt`, `KspMocksProcessor.kt` and `MockRenderer.kt` under
  `mocks-processor/src/main/kotlin/dev/modaal/mocks/`, and `MockRendererTest.kt`;
- the consumer measurement: `receipt/src/main/kotlin/dev/modaal/mocks/receipt/Receipt.kt` and
  `receipt/src/test/kotlin/dev/modaal/mocks/receipt/GeneratedMockReceiptTest.kt`;
- the skill: `skills/kotlin-ksp-mocks/SKILL.md`, `references/generated-api.md` and
  `references/troubleshooting.md`;
- subject to D7, an eighth case under `evals/`, its row in `evals/README.md`, and the case count in
  `CONTRIBUTING.md`;
- the documents: `README.md` §"Generated API", `CONTRIBUTING.md` §"The mock dialect is a contract",
  `CHANGELOG.md`;
- the development version literal at `build.gradle.kts:17`;
- one line appended to each of `specs/002-property-accessors-and-stream-counters/spec.md` §2.4 and
  §13.1, pointing to this spec (§2.5).

**Not in scope:**

- `swift-sourcery-templates`: its templates, snapshot, skill and tags are 007's phases and 007's R;
- an opt-out for the new recorder. 007 D4 (a) has `skipArgumentRecording` drop `<var>SetArgs`; this
  processor reads no per-declaration annotation, and `CONTRIBUTING.md` §"Development rules" 3 keeps
  one off the consumer's main classpath (002 D9);
- whether a typealias to a function type, or a `fun interface`, counts as function-typed (§1.5, §8
  question 1). This change applies the rule a parameter follows today;
- `scripts/check-skill.sh`, `scripts/check-print-mock-api.sh` and `.github/workflows/ci.yml`, which
  pass or run unchanged (§1.4, §1.8).

**Obsoletes:**

- 002 §2.4 in part: the sentence "`<prop>SetCount`, `<name>SubscribeCount`,
  `<name>SubscribeCancelCount` and `<name>CompletionCount` are counters alone" no longer holds for
  `<prop>SetCount`, which gains `<prop>SetHandler`;
- 002 §12.2 item 1 and §13.1 in part: the three header lines they quote become four (D5).

**Follows:** `swift-sourcery-templates/specs/007-property-setter-members/spec.md` §7.1 (D1 to D5, D7 and
D8 ruled), §7.2 (the comment in the generated mock), §8.1 (D6 (b), the synchronized release) and §8.2
(the counterpart this processor emits). 007 §8.4 leaves two items to this repository: whether the Kotlin
mock carries §7.2's comment (D4 here) and which repository tags first (D8 here).

---

## 0. TL;DR

1. A `var` requirement's setter counts the write and assigns the store (`MockRenderer.kt:251-256`), and
   `<prop>SetCount` is its one setter member (`:259-261`). No member records the value written or runs
   on a write (§1.1).
2. 007 rules `<var>SetArgs` and `<var>SetHandler` for Swift, in the order count, record, store, handler,
   with no recorder for a closure-typed property and a comment above the handler saying why. 007 §8.2
   names the Kotlin counterpart (§1.2).
3. The prototype emits `<prop>SetArgs` as a `MutableList<T>` and `<prop>SetHandler` as
   `((T) -> kotlin.Unit)?`. With §2.4's fixture, `:receipt`'s `ReceiptEnvironmentMock.kt` goes from 154
   to 178 lines and 42 to 49 member declarations, and `ReceiptDependencyMock.kt` from 31 to 32 lines.
   Every stage builds green, and 14 behaviour checks pass (§1.4, §1.6).
4. A function-typed `var` — nullable, non-null or `suspend` — gets the handler and no recorder, decided
   by the expression that keeps function-typed parameters out of `<fn>Args` (`KspMocksProcessor.kt:143`).
   A typealias to a function type and a `fun interface` are recorded, as parameters today and as
   properties in the prototype (§1.5).
5. `var draft` beside `val draftSetArgs` fails generation with the existing collision diagnostic.
   `var draft` beside `fun draftSetHandler()` generates and compiles (§1.7).
6. `SKILL.md` reports ~4.8k on invoke at 13,393 bytes. The member-table edit with two others reports
   ~4.9k; three cuts of text that the member table, §"References" or a reference file also carries
   bring it to 13,174 bytes and ~4.8k. `references/generated-api.md` stays at 250 lines (§1.8).
7. **Proposed:** the recorder and the handler as 007 §8.2 lists them (D1, D2), no recorder for a
   function-typed `var` (D3), 007 §7.2's comment in the generated file (D4), a header naming the two
   members (D5), the skill cuts (D6), an eighth eval case (D7), and `0.3.1` tagged before the Swift
   tags, after both pull requests merge (D8).

---

## 1. Measured

### 1.1 What a `var` requirement generates at `0aa6c10`

`MockRenderer.renderProperty` (`MockRenderer.kt:218-264`) emits one accessor shape for every property
except a read-only `Flow` one. For `property.isMutable` it adds a setter that counts and assigns
(`:251-256`) and declares `<prop>SetCount` (`:259-261`). `ReceiptEnvironment.volume`
(`Receipt.kt:28-29`) generates, in `receipt/build/generated/ksp/test/kotlin/dev/modaal/mocks/receipt/ReceiptEnvironmentMock.kt`:

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

- The file header states that `var name` gives "nameGetCount, nameGetHandler, nameSetCount and the store
  _name" (`:87-91`), and the KDoc says the same (`:18-21`).
- `MockProperty` (`MockModel.kt:36-44`) carries no function-type flag. `MockParameter.isFunctionType`
  (`:11-19`) does; `KspMocksProcessor.kt:143` sets it to `type.isFunctionType || type.isSuspendFunctionType`,
  and `MockRenderer.kt:271` leaves those parameters out of `<fn>Args`.
- `ReceiptEnvironmentMock.kt` is 154 lines with 42 member declarations and `ReceiptDependencyMock.kt`
  31 lines with 6, counted with `grep -cE '^  (var|val) '`. `MockRendererTest` runs 18 tests and
  `GeneratedMockReceiptTest` 15.
- `GeneratedMockReceiptTest.kt:46-62` checks that writes move `volumeSetCount` and not
  `volumeGetCount`, and that `_volume` reads and writes without moving a counter.
  `MockRendererTest.kt:169-187` pins the setter's two lines and the four declarations.

### 1.2 What 007 rules, and what it names for this repository

At `bdf04f9`, `spec/007-property-setter-members` holds 007's spec commit and none of its phases. The
newest Swift tags are `0.10.1` and `templates-0.10.1`.

| 007 | ruled there | 007 §8.2's counterpart here |
| --- | --- | --- |
| D1 | the recorder is `<var>SetArgs: [T]`, appended on every write | `<prop>SetArgs`, a `MutableList<T>` in `<fn>Args`' form (`MockRenderer.kt:321-323`) |
| §2.1 | `<var>SetHandler: ((_ newValue: T) -> ())?`, called with the written value | `<prop>SetHandler`, in `<name>OutputHandler`'s form, `((T) -> kotlin.Unit)?` (`:372`) |
| D2 | count, record, store, handler | the same order in `set(value)` |
| D3 | a closure-typed property gets the handler and no recorder | no recorder for a function-typed property, by the rule `MockParameter.isFunctionType` applies to a parameter |
| D4 | `skipArgumentRecording` drops `<var>SetArgs` | none: this processor reads no per-declaration annotation |
| D5 | the file header and each class header name `nameSetArgs` and `nameSetHandler` | this file's header, `MockRenderer.kt:87-91` |
| §7.2 | one comment line above `<method>Handler` when a parameter is not recorded, and above `<var>SetHandler` for a closure-typed property | not ruled: 007 §8.4 item 1 |
| §8.1 | `0.10.2` is released with the release here that emits the counterpart | which repository tags first is not ruled: 007 §8.4 item 2 |

007 §7.2's two strings, as it quotes them:

```swift
// `completion` is not recorded: a stored closure keeps strong references to what it captures for as long as the mock lives. `scheduleHandler` receives it.
// Values written to `onChange` are not recorded: a stored closure keeps strong references to what it captures for as long as the mock lives. `onChangeSetHandler` receives each one.
```

For two or more parameters 007 writes "`a` and `b` are not recorded: …", ending "receives them".

007 D7 (an eval grader) and D8 (cuts to its `SKILL.md`) concern that repository's files; D6 and D7 here
are the same questions for this repository's files.

### 1.3 How releases here are versioned

- `CHANGELOG.md:3-54`, the 0.3.0 entry: *Generated output*, *Breaking* and *Adopting*, the class-file
  major, and the Swift version whose vocabulary it matches, 0.9.0 (`:51-54`).
- 003 §9.3 records the practice: a minor version for a change an adopter has to act on (0.2.0, 0.3.0),
  a patch otherwise (0.2.1).
- The development literal is `0.3.0-SNAPSHOT` (`build.gradle.kts:17`).
- `README.md:160-164`: the skill is not a release asset, and a change to it reaches adopters when it
  lands on `main`.

### 1.4 The prototype, stage by stage

`proto`, `./gradlew build` at each stage. Every build exited 0. `MockRendererTest` ran 18 tests and
`GeneratedMockReceiptTest` 15 at every stage, and `SetterProbeTest` 9 from s2 on. Lines and member
declarations per generated file, with each stage's diff against the stage before:

| stage | what the stage adds | `ReceiptEnvironmentMock.kt` | `ReceiptDependencyMock.kt` | `SetterShapesMock.kt` |
| --- | --- | --- | --- | --- |
| s0 | nothing: `main` at `0aa6c10` | 154 lines, 42 members | 31, 6 | — |
| s1 | `var onVolumeChange: ((Double) -> Unit)?` in `ReceiptEnvironment`, and `SetterShapes` as a third target, under the renderer of `0aa6c10` | 169 (+15 −0), 46 | 31 (+0 −0), 6 | 168, 41 |
| s2 | §2.1's setter and members, without the header and the comment | 175 (+6 −0), 49 | 31 (+0 −0), 6 | 198 (+30 −0), 56 |
| s3 | D5 (a)'s header; the assertion at `MockRendererTest.kt:285` changed to the new lines | 176 (+3 −2) | 32 (+3 −2) | 199 (+3 −2) |
| s4 | D4 (a)'s comment | 178 (+2 −0) | 32 (+0 −0) | 204 (+5 −0) |

- s2's +6 in `ReceiptEnvironmentMock.kt` are 4 lines for `volume` — `volumeSetArgs.add(value)`,
  `volumeSetHandler?.invoke(value)` and the two declarations — and 2 for `onVolumeChange`, the handler
  call and its declaration. s2's +30 in `SetterShapesMock.kt` are 4 lines for each of the six recorded
  properties and 2 for each of the three function-typed ones.
- s4's +2 are the comments above `onVolumeChangeSetHandler` and above `measureHandler`: `measure`
  declares a function-typed parameter, `onDone` (`Receipt.kt:47`). s4's +5 in `SetterShapesMock.kt` are
  three property comments and two function comments.
- Of `ReceiptEnvironmentMock.kt`'s 24 added lines from s0 to s4, 18 belong to the `onVolumeChange`
  fixture. The other 6 are what 0.3.0's `ReceiptEnvironment` gains: 4 for `volume`, 1 net for the
  header, 1 for the comment above `measureHandler`. Its member declarations go from 42 to 44 without
  the fixture.
- At s4, `scripts/check-print-mock-api.sh` in `proto` passed G1 to G4.

### 1.5 The emission for each property shape

`SetterShapes` at s4. Every row compiled in `:receipt`'s test compilation. `MutableList` stands for
`kotlin.collections.MutableList`.

| requirement | `<prop>SetArgs` | `<prop>SetHandler` |
| --- | --- | --- |
| `var value: Int`, named like the setter's parameter | `MutableList<kotlin.Int>` | `((kotlin.Int) -> kotlin.Unit)?` |
| `var label: String?` | `MutableList<kotlin.String?>` | `((kotlin.String?) -> kotlin.Unit)?` |
| `var names: List<String>` | `MutableList<kotlin.collections.List<kotlin.String>>` | `((kotlin.collections.List<kotlin.String>) -> kotlin.Unit)?` |
| `var updates: Flow<Int>`, no guessable default, so a constructor parameter | `MutableList<kotlinx.coroutines.flow.Flow<kotlin.Int>>` | `((kotlinx.coroutines.flow.Flow<kotlin.Int>) -> kotlin.Unit)?` |
| `var onChange: (() -> Unit)?` | none | `(((() -> kotlin.Unit)?) -> kotlin.Unit)?`, with the comment |
| `var transform: (Int) -> Int`, a constructor parameter | none | `(((kotlin.Int) -> kotlin.Int) -> kotlin.Unit)?`, with the comment |
| `var loader: suspend () -> Int`, a constructor parameter | none | `((suspend () -> kotlin.Int) -> kotlin.Unit)?`, with the comment |
| `var listener: Listener?`, with `typealias Listener = (Int) -> Unit` | `MutableList<dev.modaal.mocks.receipt.Listener?>` | `((dev.modaal.mocks.receipt.Listener?) -> kotlin.Unit)?` |
| `var callback: Callback?`, with `fun interface Callback` | `MutableList<dev.modaal.mocks.receipt.Callback?>` | `((dev.modaal.mocks.receipt.Callback?) -> kotlin.Unit)?` |

The typealias row follows the rule a parameter follows at `0aa6c10`. At s1, under that renderer,
`fun observe(listener: Listener, onEvent: (Int) -> Unit, id: String)` generated
`data class ObserveArgs(val listener: dev.modaal.mocks.receipt.Listener, val id: kotlin.String)`: KSP's
`isFunctionType` is false for the alias, so `listener` is recorded and `onEvent` is not. At s4 the line
above `observeHandler` names `onEvent` alone, and `fun watch(first: () -> Unit, second: () -> Unit)`,
which has no `watchArgs`, carries "`first` and `second` are not recorded: … `watchHandler` receives
them."

007 §1.4 measured a non-optional closure-typed `{ get set }` property that the Swift initializer takes
failing to compile, with and without 007. `transform` and `loader` here are constructor parameters and
compile at s1 and at s4.

### 1.6 Behaviour

`SetterProbeTest`, against s2's and s4's generated mocks. All 14 checks passed:

1. construction records no write: `volumeSetArgs` is empty and `volumeSetCount` is 0;
2. two writes with no handler set give `volumeSetArgs == [0.5, 0.7]`;
3. and `volumeSetCount == 2`;
4. `mock._volume = 9.0` records nothing and counts nothing;
5. with `volumeSetHandler` set, `volume = 0.25` hands the handler 0.25;
6. `_volume` is 0.25 when the handler reads it;
7. `volumeSetArgs` is `[0.25]` when the handler reads it;
8. the write moved no read counter: `volumeGetCount` is 0;
9. the value read back is 0.25, and that read makes `volumeGetCount` 1;
10. a handler that reads `volume` reads the value just written, 0.4, and moves `volumeGetCount` to 1;
11. a handler that throws: the `IllegalStateException` reaches the assignment, and `volumeSetCount`
    is 1, `volumeSetArgs` is `[0.6]` and `_volume` is 0.6;
12. a handler that assigns `_volume = v * 2` decides the next read: after `volume = 0.5` a read
    returns 1.0, and `volumeSetArgs` is `[0.5]`;
13. `onVolumeChangeSetHandler` receives the closure instance written to `onVolumeChange`
    (`assertSame`), then `null`, and `onVolumeChangeSetCount` is 2;
14. on `SetterShapesMock(loader = { 1 }, transform = { it + 1 }, updates = flowOf(1))`: one write to
    `transform` gives `transformSetCount == 1`; `loaderSetHandler` runs once for one write;
    `valueSetArgs == [3, 4]`; `labelSetArgs == [null, "x"]`; `namesSetArgs == [["a"]]`; and
    `listenerSetArgs`, `callbackSetArgs` and `updatesSetArgs` each hold one element after one write.

### 1.7 Collisions

`collision`, `./gradlew :receipt:compileTestKotlin`:

- `interface CollidingSetArgs { var draft: String; val draftSetArgs: List<String> }` exits 1 with

  ```
  e: [ksp] kspMocksTargets: dev.modaal.mocks.receipt.CollidingSetArgs — draftSetArgs is generated twice, for draft and for draftSetArgs; rename one of the two interface members.
  ```

  and writes no `CollidingSetArgsMock.kt`. `DeclaredNames` reads every `var` and `val` the renderer
  emits at member indentation (`MockRenderer.kt:149-185`), so the new declarations are checked with no
  edit to it.
- `interface CollidingSetHandler { var draft: String; fun draftSetHandler() }` exits 0. The mock
  declares `var draftSetHandler: ((kotlin.String) -> kotlin.Unit)? = null` beside
  `override fun draftSetHandler()`, `draftSetHandlerCallCount` and `draftSetHandlerHandler`, and the
  setter's `draftSetHandler?.invoke(value)` compiles against the property. 007 §1.5 measured the Swift
  pair failing its consumer's compile; `MockRendererTest.kt:360-373` pins that a property and a
  function of one name are not a collision here.

### 1.8 The skill against its budgets

`skillmeasure`, `claude plugin details kotlin-ksp-mocks` after each edit. Always-on read ~299 for the
plugin and ~300 for the component in every run.

| `SKILL.md` | lines | bytes | chars | on invoke |
| --- | ---: | ---: | ---: | --- |
| `main` | 228 | 13,393 | 13,323 | ~4.8k |
| `proposed`: `main` with E1 to E3 | 229 | 13,634 | 13,564 | ~4.9k |
| `trimA`: `proposed` without E3, and with T1 | 226 | 13,408 | 13,338 | ~4.9k |
| `trimB`: `trimA` with T2 and T3 | 222 | 13,174 | 13,104 | ~4.8k |

`main` at 13,393 bytes reads ~4.8k and `trimA` at 13,408 bytes reads ~4.9k, so for text of this
composition the boundary lies between those two sizes. `trimB` is 219 bytes smaller than `main`.

The edits:

| id | `SKILL.md` | edit |
| --- | --- | --- |
| E1 | `:115` | the row for `<prop>SetCount` becomes a row for `<prop>SetCount`, `<prop>SetArgs`, `<prop>SetHandler`: "writes to a `var` requirement: counted, each value recorded, then handed to the lambda after the store is assigned. Construction does not count, and a function-typed property has no `<prop>SetArgs`" |
| E2 | `:127-128` | "plus `<prop>SetCount` for a `var`" becomes "plus the three `Set` members for a `var`" |
| E3 | `:190-191` | the function-typed bullet names values written to a function-typed `var`, and `<prop>SetArgs` beside `<fn>Args` |

The cuts:

| id | `SKILL.md` | text removed | also stated in |
| --- | --- | --- | --- |
| T1 | `:195-196` | "A nested interface generates `<SimpleName>Mock` in the enclosing package, so two nested interfaces with the same simple name in one package collide." | `references/troubleshooting.md:240-241`; `references/gradle-wiring.md:212-213` |
| T2 | `:127-128`, E2 included | "carry `<prop>GetCount`, `<prop>GetHandler` and the `_<prop>` store, plus the three `Set` members for a `var`." The paragraph then opens "**Properties.** Seed a read-only one with …" | the member table, `SKILL.md:114-117` with E1 |
| T3 | `:137-138` and the blank line after them | "Every emitted shape, with the generated Kotlin beside it, is in [references/generated-api.md](references/generated-api.md)." | `SKILL.md:224-225`, §"References" |

The two references, with §2.5's edits applied in `skillmeasure`:

| file | `main` | with §2.5's edits | K5's budget |
| --- | ---: | ---: | ---: |
| `references/generated-api.md` | 250 | 250 (+18 −18) | 250 |
| `references/troubleshooting.md` | 244 | 245 (+8 −7) | 250 |

`scripts/check-skill.sh` in `skillmeasure`, with `trimB`, both reference edits and s4's
`MockRenderer.kt`: K1 to K14 pass. K6 reads `${name}SetArgs` and `${name}SetHandler` out of the
renderer, so the skill's `<prop>SetArgs` and `<prop>SetHandler` are gated with no edit to the check.

### 1.9 Where the documents state a setter's members

| file | lines | what |
| --- | --- | --- |
| `README.md` | `:74-78` | the `<fn>Args` bullet: function-typed parameters stay out of the record |
| `README.md` | `:98-106` | the Properties paragraph: "a mutable requirement counts writes in `<prop>SetCount` as well" |
| `CONTRIBUTING.md` | `:14-23` | §"The mock dialect is a contract": `<prop>GetCount`/`<prop>GetHandler`/`<prop>SetCount` |
| `MockRenderer.kt` | `:18-21`, `:87-91` | the KDoc; the header |
| `MockModel.kt` | `:11-13` | `MockParameter`'s KDoc on function-typed parameters |
| `Receipt.kt` | `:28-29` | "Mutable requirement → accessors counting reads and writes over the store." |
| `SKILL.md` | `:115`, `:127-128`, `:190-191`, `:214` | the member-table row; the Properties paragraph; the function-typed bullet; the failure row for "`<fn>Args` does not exist" |
| `references/generated-api.md` | `:11-13` | §"The file": the header |
| `references/generated-api.md` | `:58-59`, `:68-70` | §"A method with several recordable parameters": `measureHandler`, and `onDone` left out of the record |
| `references/generated-api.md` | `:141-203` | §"Properties", with the `volume` snippet at `:145-161` |
| `references/generated-api.md` | `:239` | the twin table's `SetCount` row |
| `references/troubleshooting.md` | `:79-87` | §"… is generated twice": the example and the list of generated names |
| `references/troubleshooting.md` | `:197-206` | §"`<fn>Args` does not exist" |
| `specs/002-property-accessors-and-stream-counters/spec.md` | §2.4, §13.1 | the suffix table and "counters alone"; the header text |

### 1.10 The eval suite

`evals/` holds seven cases (`evals/README.md:10-18`). None asks about a property requirement:
`list-a-mocks-members` asks about `fun measure(width: Int, height: Int): Boolean` and grades
`measureCallCount`, `measureArgs` and `measureHandler` (`evals/list-a-mocks-members/graders/criteria.md:11-12`).
The round of 2026-09-12 ran that case's two arms for $0.4824 (`evals/README.md:161-172`). K13 parses
every case (`scripts/check-skill.sh:199-225`); no CI job runs one.

---

## 2. The shape this proposes

### 2.1 The emission

In `MockRenderer.renderProperty`, for `property.isMutable`. As s4 emits `volume` and `onVolumeChange`:

```kotlin
  override var volume: kotlin.Double
    get() {
      volumeGetCount += 1
      volumeGetHandler?.let { return it() }
      return _volume
    }
    set(value) {
      volumeSetCount += 1
      volumeSetArgs.add(value)
      _volume = value
      volumeSetHandler?.invoke(value)
    }
  var volumeGetCount: kotlin.Int = 0
  var volumeGetHandler: (() -> kotlin.Double)? = null
  var volumeSetCount: kotlin.Int = 0
  val volumeSetArgs: kotlin.collections.MutableList<kotlin.Double> = mutableListOf()
  var volumeSetHandler: ((kotlin.Double) -> kotlin.Unit)? = null
  var _volume: kotlin.Double = 0.0
```

```kotlin
  override var onVolumeChange: ((kotlin.Double) -> kotlin.Unit)?
    get() {
      onVolumeChangeGetCount += 1
      onVolumeChangeGetHandler?.let { return it() }
      return _onVolumeChange
    }
    set(value) {
      onVolumeChangeSetCount += 1
      _onVolumeChange = value
      onVolumeChangeSetHandler?.invoke(value)
    }
  var onVolumeChangeGetCount: kotlin.Int = 0
  var onVolumeChangeGetHandler: (() -> ((kotlin.Double) -> kotlin.Unit)?)? = null
  var onVolumeChangeSetCount: kotlin.Int = 0
  // Values written to `onVolumeChange` are not recorded: a stored closure keeps strong references to what it captures for as long as the mock lives. `onVolumeChangeSetHandler` receives each one.
  var onVolumeChangeSetHandler: ((((kotlin.Double) -> kotlin.Unit)?) -> kotlin.Unit)? = null
  var _onVolumeChange: ((kotlin.Double) -> kotlin.Unit)? = null
```

1. **The setter** counts, records, assigns the store, then calls the handler (D2).
2. **`<prop>SetArgs`** is declared as `<fn>Args` is for one recorded parameter (D1). It is appended on
   every write whether or not the handler is set. The constructor and an assignment to `_<prop>`
   record nothing (§1.6 checks 1 and 4).
3. **`<prop>SetHandler`** is `((T) -> kotlin.Unit)?`, the form `<name>OutputHandler` takes. It is not
   `suspend`: a Kotlin setter cannot suspend.
4. **A function-typed `var`** gets `<prop>SetHandler`, no `<prop>SetArgs` (D3), and the comment line
   directly above the handler (D4).
5. **Declaration order** inside the block: `GetCount`, `GetHandler`, `SetCount`, `SetArgs`,
   `SetHandler`, the store, which is 007 §2.1's order. Blocks stay sorted by property name
   (`MockRenderer.kt:54`).
6. **Unchanged:** a read-only requirement, a read-only `Flow` property, the getter, the constructor,
   and every function body.

### 2.2 The model and the processor

- `MockProperty` gains `isFunctionType: Boolean` after `flowElementType`. Its KDoc states that a value
  written to a function-typed `var` is not recorded, for the reason `MockParameter`'s KDoc gives
  (`MockModel.kt:11-13`), and that `<prop>SetHandler` receives it.
- `KspMocksProcessor.buildTarget` sets it with `KspMocksProcessor.kt:143`'s expression,
  `type.isFunctionType || type.isSuspendFunctionType`.
- The field has no default, as `MockParameter.isFunctionType` has none, and every `MockProperty(…)`
  call in `MockRendererTest` passes it. The prototype gave it a default of `false` to leave those calls
  as they were.

### 2.3 The comment and the header

**The comment (D4 (a)).** One line at member indentation:

- above `<fn>Handler`, when at least one of the function's parameters is function-typed — the names
  joined as "`a` is", "`a` and `b` are" or "`a`, `b` and `c` are", and "receives it" or "receives
  them";
- above `<prop>SetHandler`, for a function-typed `var`.

As s4 emits the three forms:

```
  // `onDone` is not recorded: a stored closure keeps strong references to what it captures for as long as the mock lives. `measureHandler` receives it.
  // `first` and `second` are not recorded: a stored closure keeps strong references to what it captures for as long as the mock lives. `watchHandler` receives them.
  // Values written to `onVolumeChange` are not recorded: a stored closure keeps strong references to what it captures for as long as the mock lives. `onVolumeChangeSetHandler` receives each one.
```

The strings are 007 §7.2's with this processor's member names. The clause "a stored closure keeps
strong references to what it captures for as long as the mock lives." is spelled once in
`MockRenderer.kt`, and the member each line names is written `${fn}Handler` or `${name}SetHandler`,
the interpolations K6 reads (`AGENTS.md` §"State a rule once"). `DeclaredNames.DECLARATION` matches no
comment line (`MockRenderer.kt:180-183`).

**The header (D5 (a)).** `MockRenderer.kt:87-91`'s three lines become four:

```
// Member names are the requirement's declared name plus a suffix: `fun load()` gives loadCallCount,
// loadArgs and loadHandler; `var name` gives nameGetCount, nameGetHandler, nameSetCount, nameSetArgs,
// nameSetHandler and the store _name. An overload that does not keep the plain name carries a comment
// above it.
```

The KDoc at `MockRenderer.kt:18-21` names the two members, the function-typed rule and the comment.

### 2.4 The tests

`MockRendererTest`:

- `property counters - a mutable requirement counts reads and writes over the store` (`:169-187`)
  asserts the setter's four lines in order and the two new declarations;
- a new case: a function-typed `var` emits `<prop>SetHandler`, no `<prop>SetArgs`, and the comment on
  the line directly above the handler;
- a new case: a function with one function-typed parameter carries the singular comment above
  `<fn>Handler`, a function with two the plural one, and a function with none no comment;
- a new collision case: `var draft` beside `val draftSetArgs`, asserting §1.7's message;
- the header assertion at `:285` reads the new third and fourth lines.

`:receipt`:

- `Receipt.kt` declares `var onVolumeChange: ((Double) -> Unit)?` in `ReceiptEnvironment`:
  `CONTRIBUTING.md` §"Development rules" 1 requires a `:receipt` case for a new emission shape;
- `GeneratedMockReceiptTest` takes §1.6's checks 1 to 13. `SetterShapes` and check 14 stay in the probe.

Expected from `./gradlew build`: `ReceiptEnvironmentMock.kt` 154 → 178 lines and 42 → 49 member
declarations, `ReceiptDependencyMock.kt` 31 → 32 lines, as s4 measured. A hunk in either file that §1.4
does not list is a defect in P1.

`scripts/check-skill.sh` and `scripts/check-print-mock-api.sh` pass with no edit to either (§1.4, §1.8).

### 2.5 The documents

- **`SKILL.md`**: E1, T1, T2 and T3 of §1.8 (D6). `claude plugin details` before and after, recorded in
  this spec with lines, bytes and characters.
- **`references/generated-api.md`**, +18 −18 as measured:
  - §"The file": §2.3's four header lines;
  - §"A method with several recordable parameters": the comment line above `measureHandler`, and
    "`onDone` is function-typed, so it reaches the handler, stays out of the record, and the line above
    `measureHandler` says so.";
  - §"Properties": §2.1's `volume` snippet without the `// var requirement: …` line that heads it
    today (`:146`), and one bullet: "A write counts, records the value, assigns the store, then calls
    `<prop>SetHandler`, so the handler reads the value just written. A function-typed `var` has no
    `<prop>SetArgs`, and the line above its `<prop>SetHandler` says so. Construction and `_<prop>`
    assignments record nothing.";
  - the twin table's `SetCount` row pairs `<prop>SetCount`, `<prop>SetArgs`, `<prop>SetHandler` with
    `<var>SetCount`, `<var>SetArgs`, `<var>SetHandler`;
  - paid for by removing "A nullable return therefore never fails an unseeded test: …" (`:105-106`),
    which the fallback table at `:87-93` states, and by compressing the constructor-seeded bag passage
    (`:196-203`) from eight lines to three.
- **`references/troubleshooting.md`**, +8 −7: the list at `:84-87` names `<prop>SetArgs` and
  `<prop>SetHandler`; §"`<fn>Args` does not exist" becomes §"`<fn>Args` or `<prop>SetArgs` does not
  exist" and states the function-typed `var`.
- **`README.md`**: `:74-78` says the generated file states the reason above the handler; `:98-106`
  names `<prop>SetArgs` and `<prop>SetHandler` beside `<prop>SetCount`, and the function-typed rule.
- **`CONTRIBUTING.md`**: `:14-20` lists `<prop>SetArgs` and `<prop>SetHandler`.
- **`Receipt.kt`**: the comment on `volume` names the recorder and the handler.
- **`CHANGELOG.md`**: §6.
- **`specs/002-property-accessors-and-stream-counters/spec.md`**: one line appended at the end of §2.4
  and one at the end of §13.1, each naming this spec and what it supersedes there (`AGENTS.md`
  §"Specs are an append-only decision ledger").

### 2.6 What does not change

`<Interface>Mock`, its package and its constructor; name-sorted emission and byte determinism; every
existing member name and the string `"<fn>Handler expected to be set."`; the collision diagnostic's
text; the overload rule and its comment; `kspMocksTargets` and the diagnostics the processor logs; the
`printMockApi` init script; the published jar's class-file major 61.

---

## 3. Decisions

None is ruled. Each lists the option recommended first.

**Superseded by §9:** every decision is ruled, each as recommended.

### D1 — the recorder's declaration

- **(a) `val <prop>SetArgs: kotlin.collections.MutableList<T> = mutableListOf()` (recommended).** It is
  `<fn>Args`' declaration for one recorded parameter (`MockRenderer.kt:321-323`) and 007 §8.2's
  counterpart. A test asserts `assertEquals(listOf(0.5, 0.7), mock.volumeSetArgs)`, and can `clear()`
  the record between two phases of a test, as it can `<fn>Args`.
- **(b) A read-only `List<T>` over a private list.** A test cannot clear the record. Cost: `<fn>Args`
  and `<name>Outputs` are `MutableList`, so the three recorders would be declared two ways.

### D2 — the setter's order and the handler's type

- **(a) Count, record, store, handler; `((T) -> kotlin.Unit)?` (recommended).** It is 007 D2 (a), so a
  test written for both platforms sees the same state from inside the handler. Measured: the handler
  reads the new value from `_<prop>` and from the property, sees the write already recorded, and a
  handler that throws leaves the write counted, recorded and stored (§1.6 checks 6, 7, 10, 11). Cost:
  the handler cannot read the previous value from `_<prop>`; it reads `<prop>SetArgs`' second-to-last
  element or its own capture.
- **(b) Count, record, handler, store.** The handler reads the previous value in `_<prop>`. Cost: a read
  of the property inside the handler returns the previous value here and the new value on the Swift
  side, under 007 D2 (a).
- **(c) The handler in place of the store assignment while it is set**, as `<prop>GetHandler` replaces
  the store read. Cost: with a handler set, `mock.volume = 0.5` followed by a read returns the earlier
  value; 007 D2 (c) records the same cost.

### D3 — a function-typed `var`

- **(a) The handler and no recorder, decided by `MockParameter`'s expression (recommended).**
  `MockProperty.isFunctionType` is set from `type.isFunctionType || type.isSuspendFunctionType`
  (`KspMocksProcessor.kt:143`), measured true for `(() -> Unit)?`, `(Int) -> Int` and `suspend () -> Int`
  (§1.5). It is 007 D3 (a). A typealias to a function type and a `fun interface` are recorded, as a
  parameter of either type is today (§1.5, §8 question 1).
- **(b) Record it as well.** `MutableList<(() -> kotlin.Unit)?>` compiles. Cost: the record keeps every
  written closure, and what it captures, reachable for the mock's lifetime, which is the reason
  `MockModel.kt:11-13` and `README.md:75-78` give for leaving a parameter out; and the Swift mock
  records none.

### D4 — the comment in the generated file (007 §8.4 item 1)

- **(a) 007 §7.2's rule and text, with this processor's member names (recommended).** One line above
  `<fn>Handler` for every function with a function-typed parameter, and one above `<prop>SetHandler`
  for a function-typed `var` (§2.3). A reader of the generated file sees why `onDone` is missing from
  `MeasureArgs`, or why `<prop>SetArgs` is absent, in the file — the reason `MockRenderer.kt:83-86`
  gives for the header. `java.lang.ref` calls an object reachable through such a reference strongly
  reachable, so the text holds on the JVM. Cost: the comment also changes the output for an interface
  that declares no `var` — every function with a function-typed parameter gains a line, `measureHandler`
  in `:receipt` among them (§1.4) — and each comment is one unwrapped line, longer than the header's
  lines.
- **(b) No comment.** The reason stays in `MockModel.kt:11-13`, `README.md:75-78` and
  `references/generated-api.md:68-70`. Cost: the Swift mock carries a line the Kotlin mock lacks for the
  same declaration, and `ReceiptEnvironmentMock.kt` shows `MeasureArgs` without `onDone` and no
  statement why.

### D5 — the header

- **(a) Name `nameSetArgs` and `nameSetHandler` (recommended).** §2.3's four lines, the content of 007
  D5 (a). The header then names every member a `var` requirement generates. Cost: +3 −2 in every
  generated file, for every input (§1.4), and the assertion at `MockRendererTest.kt:285` changes.
- **(b) Unchanged.** Cost: the header names four of the six members a `var` requirement generates, while
  the Swift header names all of them.

### D6 — the skill text

E1 is in both options. Measured in §1.8.

- **(a) E1 with T1, T2 and T3 (recommended).** 13,174 bytes and ~4.8k, 219 bytes smaller than `main`.
  Each cut removes a sentence that the member table, §"References" or a reference file states. Cost: the
  nested-interface rule is then stated in `references/troubleshooting.md` and
  `references/gradle-wiring.md` only.
- **(b) E1 to E3 with no cut.** ~4.9k. Cost: `AGENTS.md` §"The skill under `skills/` teaches adopters"
  keeps the body near 4.8k, and the next addition to the body has to be paid for first.

### D7 — an eval case

- **(a) An eighth case, `record-property-writes` (recommended).** The prompt carries
  `interface FeedEnvironment { var volume: Double; var onChange: (() -> Unit)? }` and asks how a test
  asserts which values `FeedService` wrote to `volume`, in order, and how it runs code on each write to
  `onChange`. Graders: `regex` on `volumeSetArgs`, `regex` on `onChangeSetHandler`, an `llm` criteria
  grader that also requires the answer to say `onChange` has no `SetArgs` record, and `skill-fired`. Each
  arm runs once by hand as `evals/README.md` §"Running them" gives it, and the round is recorded there;
  a with-arm answer that misses the members is a defect in `SKILL.md`. `CONTRIBUTING.md:41` and `:122`,
  and `evals/README.md:3`, count eight cases. Cost: two model sessions; the last one-case round cost
  $0.4824 (§1.10).
- **(b) No case.** K6 gates the names the skill quotes. Cost: nothing measures whether an agent answers
  with `<prop>SetArgs` and `<prop>SetHandler`.

### D8 — the order of the two releases (007 §8.4 item 2)

The request and 007 §2.6 fix both numbers: `0.3.1` here, `0.10.2` there. Each `CHANGELOG.md` entry names
the other's version.

- **(a) Both pull requests merge; `0.3.1` is tagged here; then 007's R pushes `templates-0.10.2` and
  `0.10.2` (recommended).** 007 §8.3 holds its tags until this repository's counterpart is merged, and
  a `0.3.1` published first is on the Maven host when the Swift entry naming it is tagged. The skill
  here reaches adopters at merge (`README.md:160-164`), so tagging on the day of the merge limits to that
  day the time during which an adopter on 0.3.0 reads `<prop>SetArgs` in the skill and has no release
  that emits it. Cost: between the two tags, this repository's entry names a Swift version that is not
  tagged yet.
- **(b) The Swift tags first, then `0.3.1`.** 007 §8.3 permits it once this pull request is merged.
  Cost: the window is the same with the roles swapped, and the skill here documents members of an
  unpublished processor for as long as 007's R takes, which includes its pin commit and its by-URL
  example job (007 §4).

---

## 4. Phasing

All on `spec/004-property-setter-members`, created from `main` at `0aa6c10`, each phase one commit with
the prefix `[004-property-setter-members]`. This spec is the branch's first commit. The gate for every
phase that touches code: `./gradlew build` before and after, the files under
`receipt/build/generated/ksp/test/kotlin/` read against §1.4, `scripts/check-skill.sh`, and
`scripts/check-print-mock-api.sh`.

- **P1 — the processor, the consumer measurement and the skill.** §2.1 to §2.4 in `MockModel.kt`,
  `KspMocksProcessor.kt`, `MockRenderer.kt`, `MockRendererTest.kt`, `Receipt.kt` and
  `GeneratedMockReceiptTest.kt`, with §2.5's `SKILL.md`, `references/generated-api.md` and
  `references/troubleshooting.md` edits in the same commit: `references/generated-api.md` quotes the
  header P1 changes (`AGENTS.md` §"The skill under `skills/` teaches adopters"). `claude plugin details`
  before and after the `SKILL.md` edit. Expected: `ReceiptEnvironmentMock.kt` 154 → 178 lines,
  `ReceiptDependencyMock.kt` 31 → 32.
- **P2 — the documents.** `README.md`, `CONTRIBUTING.md` §"The mock dialect is a contract", and the
  lines appended to 002 §2.4 and §13.1.
- **P3 — the eval case**, under D7 (a).
- **P4 — the release entry and the record.** `CHANGELOG.md`'s `0.3.1` entry (§6), `build.gradle.kts:17`
  at `0.3.1-SNAPSHOT`, and a section of this spec recording what landed and what was measured on the way.

Then the pull request, and after it merges, **R** on `main` in D8's order (§6 steps 3 to 5). R's record
is an addition to this spec on `main`. Pushing, opening the pull request, merging and the tag each need
their own go-ahead.

---

## 5. What a consumer does

1. **Rebuild.** The mock regenerates in the test compilation. No existing member moves, and a test that
   compiles against 0.3.0 still compiles, unless the interface declares a property §1.7's first case
   describes.
2. **Assert on writes** with `<prop>SetArgs`: `assertEquals(listOf(0.5, 0.7), mock.volumeSetArgs)`.
   `<prop>SetCount` with a read of `_<prop>` gives the count and the last value only.
3. **Run code on each write** with `mock.<prop>SetHandler = { value -> … }`. The store already holds
   `value` when it runs. Assigning `_<prop>` inside it decides what the next read returns (§1.6 check 12).
   Assigning `mock.<prop>` inside it calls the setter, and the handler, again (§7).
4. **A function-typed `var`** has `<prop>SetHandler` and no `<prop>SetArgs`: capture the closure in the
   handler.
5. **An interface declaring a property named `<prop>SetArgs` or `<prop>SetHandler` beside `var <prop>`**
   fails generation with the line naming both; rename one of the two.

---

## 6. Release

`0.3.1`, a patch, as the request rules. In the order `AGENTS.md` §"Do not tag without measuring" and
`CONTRIBUTING.md` §"Development rules" 4 and 6 give:

1. **The `CHANGELOG.md` entry, in P4**, in the form of the 0.3.0 entry:
   - *Generated output*: a `var` requirement's setter records each value written in `<prop>SetArgs` and
     then, after the store is assigned, calls `<prop>SetHandler`; a function-typed `var` gets the handler
     and no recorder. A function with a function-typed parameter, and a function-typed `var`, carry a
     comment above the handler naming what is not recorded and why. The file header names `nameSetArgs`
     and `nameSetHandler`. For 0.3.0's `ReceiptEnvironment`, `ReceiptEnvironmentMock.kt` goes from 154 to
     160 lines and 42 to 44 member declarations, and `ReceiptDependencyMock.kt` from 31 to 32 lines
     (§1.4).
   - *Breaking*: an interface that declares a property named `<prop>SetArgs` or `<prop>SetHandler`
     beside `var <prop>` fails generation with `e: [ksp] kspMocksTargets: <fqn> — <member> is generated
     twice, …`; with 0.3.0 it generated. A function of either name generates and compiles (§1.7).
   - *Adopting*: nothing to change; §5 items 2 to 4. The member vocabulary matches
     `swift-sourcery-templates` `0.10.2` name for name.
   - The published jar stays class-file major 61, which `checkPublishedBytecodeVersion` holds.
2. **`build.gradle.kts:17`** reads `0.3.1-SNAPSHOT` in P4's commit.
3. **The pull request merges by rebase**, so that P4's commit, which carries the entry and the literal,
   is the commit on `main` that gets tagged (`CONTRIBUTING.md` §"Development rules" 4).
4. **On that commit:** `./gradlew clean build`, the two generated files read against §1.4,
   `scripts/check-skill.sh`, `scripts/check-print-mock-api.sh` and `cmp AGENTS.md CLAUDE.md`.
5. **Tag `0.3.1`**, in D8's order. `.github/workflows/publish.yml` runs `scripts/publish-maven.sh`, whose
   coordinate set does not change.

---

## 7. Not measured

- A consumer of the published processor. Every measurement ran through `:receipt`'s
  `kspTest(project(":mocks-processor"))` in a copy of this checkout; `publishToMavenLocal` was not run.
- A multiplatform or Android module. The renderer does not read the module shape, and no such module
  generated with the prototype.
- A `var` inherited from an interface declared in another module.
- A `<prop>SetHandler` that assigns the property it observes, which calls the setter and the handler
  again.
- `<prop>SetArgs` over many writes: it grows by one element per write for the mock's lifetime, as
  `<fn>Args` does per call.
- Two threads writing one property. The recorder is a plain `MutableList`, as 002 D10 (a) decided for
  the other recorders.
- That a written closure stays reachable through the mock, which is the reason the comment states. No
  `WeakReference` probe was run.
- 007's emission. Its templates are unchanged at `bdf04f9`, so the Swift half of every row in §1.2 is
  007 §2 and §7.2 as written.
- The eval arms, with or without E1 and the cuts.
- `claude plugin details` on a Claude Code other than 2.1.271.

## 8. Open questions

1. **Does a typealias to a function type, or a `fun interface`, count as function-typed?** Both are
   recorded today as parameters (`ObserveArgs.listener`, §1.5) and would be recorded as properties under
   D3 (a). Resolving an alias to its target before the check would move them out of `<fn>Args` and
   `<prop>SetArgs`, which changes a record an adopter's test may assert on, so it would be a release of
   its own. 007 §1.4 did not measure a typealias on the Swift side.
2. **Does a check measure `SKILL.md`'s token figure?** K5 counts lines, and §1.8 measured `main` reading
   ~4.8k at 15 bytes below a text reading ~4.9k. `AGENTS.md` §"The skill under `skills/` teaches
   adopters" records that no check reads the figure.

---

## 9. The rulings of 2026-09-16

Added 2026-09-16, before this spec's first commit. The owner ruled every decision of §3 as recommended.

| decision | ruled | the phase that carries it |
| --- | --- | --- |
| D1 | (a): `val <prop>SetArgs: kotlin.collections.MutableList<T> = mutableListOf()` | P1 |
| D2 | (a): count, record, store, handler; `((T) -> kotlin.Unit)?` | P1 |
| D3 | (a): a function-typed `var` gets `<prop>SetHandler` and no `<prop>SetArgs`, by `KspMocksProcessor.kt:143`'s expression | P1 |
| D4 | (a): 007 §7.2's comment above `<fn>Handler` and above `<prop>SetHandler` | P1 |
| D5 | (a): the header names `nameSetArgs` and `nameSetHandler` | P1 |
| D6 | (a): E1 with cuts T1, T2 and T3 | P1 |
| D7 | (a): the eighth case, `record-property-writes`, and one run of each arm | P3 |
| D8 | (a): both pull requests merge, `0.3.1` is tagged, then 007's R pushes its tags | R |

§4's phases and §6's release stand as written. The scope item "subject to D7" is in scope.

---

## 10. What landed

Added 2026-09-16, in P4's commit. Every phase landed on `spec/004-property-setter-members` as one commit,
measured on the toolchain the header names, with Claude Code 2.1.271.

| phase | commit | files |
| --- | --- | --- |
| P1 | `467945b` | `MockModel.kt`, `KspMocksProcessor.kt`, `MockRenderer.kt`, `MockRendererTest.kt`, `Receipt.kt`, `GeneratedMockReceiptTest.kt`, `SKILL.md`, `references/generated-api.md`, `references/troubleshooting.md` |
| P2 | `8b0714b` | `README.md`, `CONTRIBUTING.md`, `specs/002-property-accessors-and-stream-counters/spec.md` |
| P3 | `321fd77` | `evals/record-property-writes/` (a prompt and four graders), `evals/README.md`, `CONTRIBUTING.md` |
| P4 | this commit | `CHANGELOG.md`, `build.gradle.kts`, this section |

### 10.1 P1 — the processor, the consumer measurement and the skill

- `./gradlew build` before the edits: `ReceiptEnvironmentMock.kt` 154 lines and 42 member declarations,
  `ReceiptDependencyMock.kt` 31 and 6, the s0 row of §1.4. After: 178 and 49, 32 and 6. The diff of
  each file against the build before holds §1.4's hunks and no other: the header +3 −2 in both, 18
  lines for `onVolumeChange`, 4 for `volume`, 1 above `measureHandler`.
- `MockRendererTest` 18 → 21 tests, `GeneratedMockReceiptTest` 15 → 21. `scripts/check-skill.sh` K1 to
  K14 and `scripts/check-print-mock-api.sh` G1 to G4 passed with no edit to either.
- `SKILL.md`, measured with `claude plugin details` in two copies under the session scratchpad, one
  from `HEAD` before P1 and one from the working tree after it: 228 lines, 13,393 bytes, 13,323
  characters, ~4.8k on invoke before; 222 lines, 13,174 bytes, 13,104 characters, ~4.8k after.
  Always-on ~299 for the plugin and ~300 for the component in both. The plugin state before and after
  was §"Measurements"' one marketplace and one user-scope plugin.
- The three skill files are `skillmeasure`'s copies from §1.8, byte for byte.

What P1 did that §2 does not state:

- `MockRenderer.kt` spells §2.3's shared clause once, as the private constant `CLOSURE_RETAINED`, and
  both comment lines interpolate it beside `${fn}Handler` or `${name}SetHandler`.
- For a function-typed `var` the renderer emits the comment in the place of the `<prop>SetArgs`
  declaration, so it is directly above `<prop>SetHandler` by construction.
- `MockRendererTest`'s new comment case covers the three-name form, "`a`, `b` and `c` are", beside
  the one- and two-name forms, and counts three comments for four functions. The read-only case's
  assertion was widened from `idleTimeoutMsSetCount` to `idleTimeoutMsSet`, so it fails on any of the
  three setter members.
- `GeneratedMockReceiptTest` carries §1.6's checks 1 to 13 as six tests: checks 1 to 4 in
  `mutable property records each write in order, …`, 5 to 9 in `set handler runs after the write is
  counted, recorded and stored`, and one test each for 10, 11, 12 and 13.
- `MockRenderer.kt`'s KDoc also states, beside the paragraph on the overload comment, that a handler
  receiving an unrecorded function-typed value carries a comment above it.

### 10.2 P2 — the documents

`README.md`'s `<fn>Args` bullet names the comment above `<fn>Handler`, and its Properties paragraph
names `<prop>SetCount`, `<prop>SetArgs`, `<prop>SetHandler`, the order of a write and the function-typed
rule. `CONTRIBUTING.md` §"The mock dialect is a contract" lists the two members. 002 §2.4 and §13.1
each end with a "Superseded in part by" line naming this spec. `scripts/check-skill.sh` passed.

### 10.3 P3 — the eval case

`evals/record-property-writes/` carries §3 D7 (a)'s prompt and graders, named
`names-the-write-recorder`, `names-the-set-handler`, `criteria` and `skill-fired`. K13 parses it.
`CONTRIBUTING.md:41` and `:122` and `evals/README.md:3` count eight cases.

Each arm ran once by hand as `evals/README.md` §"Running them" gives it, adding `--model sonnet` and
`--output-format stream-json --verbose`, each from its own `mktemp -d` directory. $0.3227 in total;
`evals/README.md` §"The round of 2026-09-16" carries the verdicts and both answers:

| arm | tools called | turns | cost | scored graders |
| --- | --- | ---: | ---: | --- |
| without | `Glob`, `ToolSearch`, `WebSearch` (denied) | 4 | $0.1564 | 0 of 3 |
| with | `Skill`, then `Read` of `references/generated-api.md` | 4 | $0.1663 | 3 of 3 |

The with-arm's `Read` took the absolute path of that file in this checkout, outside the run
directory, under `--restricted`. `evals/README.md` §"Running them" states, from a 2.1.267 run, that
`--restricted` stops such a read. §7's "the eval arms" is measured by this round for the skill after
E1 and the cuts.

### 10.4 P4 — the release entry

- `CHANGELOG.md` carries the `0.3.1` entry §6 step 1 gives, and `build.gradle.kts:17` reads
  `0.3.1-SNAPSHOT`.
- §6 step 1's figures for 0.3.0's `ReceiptEnvironment`, which §1.4 derived from the stages, were
  measured: a copy of the working tree after P3, with `onVolumeChange` and its test removed, built
  `ReceiptEnvironmentMock.kt` at 160 lines and 44 member declarations and `ReceiptDependencyMock.kt` at
  32 lines. Against the output before P1 the environment mock differs in 10 lines: the header's 5, 4
  for `volume` and 1 above `measureHandler`.
- On the working tree of this commit: `./gradlew clean build --no-build-cache --rerun-tasks`, 14 tasks
  executed, exit 0, with 21 and 21 tests and the generated files at 178 and 32 lines;
  `mocks-processor-0.3.1-SNAPSHOT.jar`'s `MockRenderer.class` opens `cafe babe 0000 003d`, class-file
  major 61; `scripts/check-print-mock-api.sh` G1 to G4 passed; `cmp AGENTS.md CLAUDE.md` exited 0.

### 10.5 What is not done

- The branch is not pushed and no pull request is open. §6 steps 3 to 5 — the merge by rebase, the
  build on the merged commit and the tag `0.3.1` — and D8's order with 007's R follow the merge, each
  with its own go-ahead.
- §8's two questions stay open.
