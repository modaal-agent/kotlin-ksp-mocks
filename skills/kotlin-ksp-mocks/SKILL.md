---
name: kotlin-ksp-mocks
description: Generate recording-spy mocks for Kotlin interfaces with the dev.modaal mocks-processor KSP processor. Use when asked to add mock generation to a Gradle module, mock a Kotlin interface for a test, replace a hand-written fake, wire kspTest or kspJvmTest, set kspMocksTargets, or read a generated <Interface>Mock — and when diagnosing "Handler expected to be set.", a test that hangs collecting a mock's Flow, "is not resolvable in this compilation", "declares type parameters", or a missing <fn>Args record. Covers Kotlin/JVM, Kotlin Multiplatform, Android and testFixtures wiring. Generation runs in the test compilation only, so no annotation reaches the main classpath and no generated file is committed.
license: MIT
metadata:
  repository: https://github.com/modaal-agent/kotlin-ksp-mocks
---

# Kotlin interface mocks with dev.modaal mocks-processor

A KSP processor that writes one `<Interface>Mock` per interface named in a build-script list. The
mock records calls, seeds returns through handlers, and is generated into the test compilation of
the module that wires it. Nothing is committed and nothing lands on the main classpath.

Interfaces are selected in the build script, not by an annotation on the interface. An interface
declared in `main` or `commonMain` reaches the test compilation as a compiled binary, where no
comment or KDoc marker survives to be read.

## Pick the configuration for the module shape

| the module | the dependency line | where the mock lands |
| --- | --- | --- |
| Kotlin/JVM | `kspTest("dev.modaal:mocks-processor:<version>")` | `build/generated/ksp/test/kotlin/` |
| Kotlin Multiplatform, JVM tests | `dependencies { add("kspJvmTest", "dev.modaal:mocks-processor:<version>") }` — the typed accessor `kspJvmTest(…)` does not exist and the build script will not compile with it | `build/generated/ksp/jvm/jvmTest/kotlin/` |
| Android, AGP 9 | `dependencies { add("kspTest", …) }`, and do not apply `kotlin("android")` — AGP 9 refuses it | `build/generated/ksp/debugUnitTest/kotlin/` |
| Android, AGP 8 | `dependencies { add("kspTest", …) }`, with `kotlin("android")` applied | `build/generated/ksp/debugUnitTest/kotlin/` |
| the interfaces live in another module | wire KSP in the module that holds the tests and list the other module's interfaces there. That module must be on this one's test compile classpath, which `implementation(project(":core"))` already gives | the test module's own `build/`, in the interface's package |
| several modules' tests need the same mocks | `dependencies { add("kspTestFixtures", …) }` in the module that owns the interfaces, plus at least one Kotlin file under `src/testFixtures/kotlin/`, then `testImplementation(testFixtures(project(":core")))` in each consumer | `build/generated/ksp/testFixtures/kotlin/` |

A multiplatform module's mock is visible to the platform test source set only. A `commonTest` source
set cannot see a mock generated for the JVM test compilation.

## Wire it into the module whose tests need the mocks

Three edits. First, the repository that serves the artifact — it is on neither Maven Central nor
Google's repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven { url = uri("https://modaal-agent.github.io/maven") }
  }
}
```

Then the KSP plugin, the processor on the test configuration the table above names, and the
target list:

```kotlin
// build.gradle.kts of the module whose tests need mocks
plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp") version "<ksp version>"
}

dependencies {
  testImplementation(kotlin("test"))
  kspTest("dev.modaal:mocks-processor:<version>")
}

ksp {
  arg("kspMocksTargets", "com.example.FeedEnvironment,com.example.FeedDependency")
}
```

`kspMocksTargets` is a comma-separated list of fully-qualified interface names. A nested interface is
written with dots — `com.example.Outer.Inner`.

**Resolve `<version>` rather than pinning a number from memory.** The host publishes a Maven metadata
file whose `<release>` element is the newest published version:

```bash
curl -s https://modaal-agent.github.io/maven/dev/modaal/mocks-processor/maven-metadata.xml
```

`<ksp version>` is a KSP release that supports the Kotlin version the project compiles with. KSP's
2.x line versions independently of the Kotlin compiler, so one KSP release serves a range of Kotlin
versions.

The full wiring reference, including what to do when the compile worker runs an old JVM, is
[references/gradle-wiring.md](references/gradle-wiring.md).

## What the generated mock gives a test

The mock is a class named `<Interface>Mock` in the **interface's** package, so a test in another
package imports it.

```kotlin
interface FeedEnvironment {
  suspend fun load(id: String): FeedPage
  fun log(message: String)
  var volume: Double
}

val environment = FeedEnvironmentMock()
environment.loadHandler = { id -> FeedPage(id) }

environment.load("p1")
environment.loadCallCount   // 1
environment.loadArgs        // ["p1"]
```

| member | what it holds |
| --- | --- |
| `<fn>CallCount` | how many times the requirement was called, counted before the handler runs |
| `<fn>Args` | one entry per call, in order. One recordable parameter is stored directly, so the list is `MutableList<String>`; two or more become a nested `<Fn>Args` data class labelled with the parameter names |
| `<fn>Handler` | the nullable lambda a test sets to control the return value and the side effects. It is the only seeding mechanism, and `suspend` is carried through to it |
| `<fn>Channel` | for a function returning `Flow` — what the mock replays while `<fn>Handler` is unset |
| `<prop>SetCount` | writes to a mutable property requirement. Construction does not count |
| `<prop>GetCount`, `<prop>GetHandler`, `<prop>Channel` | reads, the seeding lambda and the replay channel of a read-only `Flow` property |

**Without a handler**, a function returns by these rules, in order: a `Unit` function returns
nothing; a `Flow` return replays `<fn>Channel`; a nullable return gives `null`; a guessable default
(`0`, `0L`, `0.0`, `false`, `""`, `emptyList()`, `emptyMap()`, `emptySet()` and the mutable
collections) is returned; anything else fails with `IllegalStateException` carrying
`"<fn>Handler expected to be set."`.

**Properties** follow the same idea: a `var` requirement is stored and counts writes; a read-only
`Flow` property gets the get-count, get-handler and channel; a read-only requirement with a guessable
default is a stored `var` a test can re-seed; a read-only requirement without one becomes a
constructor parameter. An interface of properties alone therefore generates a constructor-seeded bag
— `FeedDependencyMock(config = config)` — and adding a member to that interface breaks the test's
constructor call at compile time.

Every emitted shape, with the generated Kotlin beside it, is in
[references/generated-api.md](references/generated-api.md).

## Streams are channel-backed

Push, then close to end the stream. A collector waits on an open channel forever, so the close is
what makes the test terminate.

```kotlin
@Test
fun `events reach the collector`() = runTest {
  val environment = FeedEnvironmentMock()
  environment.eventsChannel.trySend(FeedEvent.Tick)
  environment.eventsChannel.close()

  assertEquals(listOf(FeedEvent.Tick), environment.events().toList())
  assertEquals(1, environment.eventsCallCount)
}
```

Seeding `eventsHandler = { flowOf(FeedEvent.Tick) }` takes precedence over the channel and needs no
close.

## Shape the interface so its mock is usable

- **Generic interfaces and `vararg` parameters fail generation** with an error naming the member.
  Wrap the use site in a non-generic interface, or take a `List` instead of a `vararg`.
- **Interfaces only.** A class or an object named in `kspMocksTargets` fails generation.
- **Function-typed parameters reach the handler but stay out of `<fn>Args`** — storing a closure
  would pin the caller's captures to the mock's lifetime. Assert on those through the handler.
- **Overloads** share one set of bookkeeping members, so all but the overload with the fewest
  parameters carry their capitalized parameter names — `update(id, force)` beside `update(id)` gives
  `updateIdForceCallCount`.
- **A nested interface** generates `<SimpleName>Mock` in the enclosing package, so two nested
  interfaces with the same simple name in one package collide.
- **Do not hand-edit a generated mock.** It is rewritten on the next test compilation. Change the
  interface, or seed a handler.

## When it goes wrong

| symptom | cause | action |
| --- | --- | --- |
| `Could not find dev.modaal:mocks-processor` | the host repository is not declared | add the `maven { … }` line to `settings.gradle.kts` |
| `kspMocksTargets: <fqn> is not resolvable in this compilation` | the interface is not on the test compile classpath of the module KSP runs in, or the name is misspelled | add the dependency on the declaring module; write a nested interface as `Outer.Inner` |
| `kspMocksTargets: <fqn> is not an interface` | a class, object or data class was named | interfaces only |
| `kspMocksTargets: <fqn> declares type parameters — generic interfaces are not supported` | a generic interface | wrap the use site in a non-generic interface, or hand-write that double |
| `kspMocksTargets: <fqn>.<fn> has a vararg parameter — not supported` | a `vararg` parameter | take a `List`, or hand-write that double |
| `Unresolved reference` on `<Interface>Mock` | KSP did not run for that source set, the interface is missing from `kspMocksTargets`, or the test is in another package and needs the import | check the module table above, then the target list, then the import |
| `IllegalStateException` with `<fn>Handler expected to be set.` | the return type has no guessable default and no handler was set | set `<fn>Handler` |
| a test collecting a mock's `Flow` never finishes — under `runTest`, `UncompletedCoroutinesError` after the default timeout | the channel was never closed | `close()` the channel after the sends, or seed the handler |
| `<fn>Args` does not exist | every parameter is function-typed, or the function takes none | assert through `<fn>Handler` and `<fn>CallCount` |
| the mock constructor demands an argument | a read-only requirement with no guessable default is constructor-seeded | pass it, and expect this break whenever such a member is added |
| `UnsupportedClassVersionError` naming `KspMocksProcessorProvider` | the consuming build's Kotlin compile worker runs a JVM older than 17 | point the daemon at a newer JDK — `gradle/gradle-daemon-jvm.properties` holding `toolchainVersion=<major>` states it per repository |

Each of these in full, with the text to match and the commands to confirm it, is in
[references/troubleshooting.md](references/troubleshooting.md).

## References

- [references/gradle-wiring.md](references/gradle-wiring.md) — every module shape, the version
  resolution, the JVM floor, and what the KSP tasks are called.
- [references/generated-api.md](references/generated-api.md) — one section per emitted shape, the
  defaults table, and the member map for a codebase that also runs the Swift twin.
- [references/troubleshooting.md](references/troubleshooting.md) — one section per symptom.
