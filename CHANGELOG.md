# Changelog

## 0.3.0 — 2026-09-12

**Breaking for generated output.** Every property requirement is counted, a
channel-backed `Flow` member records what it delivered, and a name a mock would
generate twice fails generation instead of emitting a file that does not
compile. The published jar stays class-file major 61 (Java 17), which
`checkPublishedBytecodeVersion` holds.

*Generated output*

- **Every property requirement** carries `<prop>GetCount`, `<prop>GetHandler`
  and a `_<prop>` store the getter falls back to; a `var` requirement keeps
  `<prop>SetCount`. The store is seeded from the guessable default, or from a
  constructor parameter of the declared name, and construction moves no
  counter. A read-only requirement's override is now `val`.
- **A channel-backed `Flow` member** — a function returning `Flow`, or a
  read-only `Flow` property — carries `<fn>SubscribeCount`,
  `<fn>SubscribeCancelCount`, `<fn>OutputCount`, `<fn>Outputs`,
  `<fn>OutputHandler` and `<fn>CompletionCount`. A collector that stops early
  (`first()`, `take(n)`, a timeout) counts a cancellation, not a completion.
- **Every emitted name goes through one uniqueness check.** Two requirements
  that would generate one member fail generation with
  `e: [ksp] kspMocksTargets: <fqn> — <member> is generated twice, for <a> and
  for <b>; rename one of the two interface members.` and no file is written.
- **The file states how its members are named**, in three lines under the
  header, and an overload that does not keep the plain name carries a comment
  above its override.
- For the two interfaces `:receipt` declares, 21 bookkeeping members become 48.

*Breaking*

- `mock.<prop> = value` on a read-only requirement no longer compiles. Assign
  `mock._<prop>`, which is also the assignment the Swift twin takes from its
  0.9.0 onwards.
- An interface declaring a name the mock generates (`<prop>GetCount`,
  `_<prop>`, …) now fails generation with a logged error. Before this it
  emitted a file that failed the consumer's compile with several errors and no
  diagnostic.
- A `<prop>GetHandler` on a `Flow` property is read when the flow is collected
  rather than when the property is read, so clearing it between the read and
  the collection changes what the collector gets.

*Adopting*

- Rebuild and fix what the compiler names; `_<prop>` is the seed-and-read path
  that moves no counter.
- `<name>Outputs` replaces a hand-written collector, and `<name>SubscribeCount`
  is how a test asserts that the code under test collected at all.
- The member vocabulary matches
  [swift-sourcery-templates](https://github.com/modaal-agent/swift-sourcery-templates)
  0.9.0 name for name, including the `_<prop>` store and the six stream
  members.

## 0.2.1 — 2026-08-25

The published jar is class-file major 61 (Java 17). No processor behavior
change, no change to the generated member vocabulary, and no change to the
generated output for any input.

- `mocks-processor` compiles on the current toolchain and emits for Java 17:
  `jvmToolchain(25)` decides which compiler runs, `compilerOptions.jvmTarget`
  and the matching `sourceCompatibility`/`targetCompatibility` decide what it
  writes. A consuming build loads the processor jar in the Kotlin compile
  worker its own daemon started, so the jar's major is what decides which
  daemons can run it; the KSP API this links against is class-file 52, which
  is the only floor the dependency imposes.
- `:mocks-processor:check` gains `checkPublishedBytecodeVersion`, which reads
  every `.class` entry in the jar the publication ships and fails on any above
  the declared target. A toolchain bump that raises the target reds this build
  instead of reaching a consumer as `UnsupportedClassVersionError`.

## 0.2.0 — 2026-08-25

Toolchain only — no processor behavior, no change to the generated member
vocabulary, and no change to the generated output for any input.

- The build moves to Kotlin 2.4.10, Gradle 9.7.1 and JDK 25 (`coroutines`
  1.11.0). `ksp` stays at 2.3.11: KSP's 2.x line versions independently of
  the compiler, and 2.3.11 processes Kotlin 2.4.10 — `:receipt` generates and
  its suite passes on this build.
- The wrapper carries `distributionSha256Sum`, so the Gradle distribution is
  verified before it is unpacked.
- **The published jar is class-file major 69.** A processor jar is loaded by
  the Kotlin compile worker of the project consuming it, so that project's
  compile JVM must be a 25 — which it is wherever the consuming module
  declares `jvmToolchain(25)`. A consumer on `jvmToolchain(21)` should stay on
  `0.1.0`.

## 0.1.0 — 2026-08-19

- `dev.modaal:mocks-processor` — a KSP `SymbolProcessor` generating
  recording-spy mocks for Kotlin interfaces into the test compilation
  (`kspTest`/`kspJvmTest`), selected by the `kspMocksTargets` option.
  Member vocabulary: `<fn>CallCount` / `<fn>Args` / `<fn>Handler`,
  `<prop>SetCount`, `<prop>GetCount`/`<prop>GetHandler`, `Channel`-backed
  `Flow` members, and a constructor-seeded bag for members without a
  guessable default. Output is byte-deterministic (members name-sorted).
- `:receipt` — unpublished end-to-end proof module; its tests run against
  mocks the processor generated during this build's own test compilation.
