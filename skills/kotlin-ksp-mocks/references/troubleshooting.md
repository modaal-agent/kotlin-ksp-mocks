# Troubleshooting

One section per symptom. The quoted text is what the build prints; `<fqn>`, `<fn>` and `<prop>` stand
for the names your build puts there. Processor diagnostics reach the log with KSP's own `e: [ksp] `
prefix.

## `Could not find dev.modaal:mocks-processor`

```
> Could not find dev.modaal:mocks-processor:<version>.
  Searched in the following locations:
```

The artifact is on a static Maven host that the build does not declare. Add it in
`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven { url = uri("https://modaal-agent.github.io/maven") }
  }
}
```

If the build uses `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`, this is the only
place it can go. Confirm the host is reachable and which versions exist:

```bash
curl -s https://modaal-agent.github.io/maven/dev/modaal/mocks-processor/maven-metadata.xml
```

## `e: [ksp] kspMocksTargets: <fqn> is not resolvable in this compilation`

KSP looked the name up in the compilation it runs in and found nothing. Three causes, in the order
worth checking:

1. **The declaring module is not on the test compile classpath** of the module KSP runs in. Add it —
   `implementation(project(":core"))` or `testImplementation(project(":core"))`.
2. **The name is misspelled**, or it is a nested interface written with the wrong separator. Nested
   interfaces use dots — `com.example.core.Outer.Inner`.
3. **The wrong KSP configuration carries the processor**, so the pass that ran is not the one the
   interface is visible in. `kspTest` reads the test compile classpath; `ksp` reads the main one.

## `e: [ksp] kspMocksTargets: <fqn> is not an interface`

A class, data class or object was named. The processor mocks interfaces only. Extract the
requirement the test needs into an interface the production type implements, and mock that.

## `e: [ksp] kspMocksTargets: <fqn> declares type parameters — generic interfaces are not supported`

Generic interfaces are refused, deliberately and with a diagnostic rather than a partial mock.

Wrap the use site in a non-generic interface and mock that:

```kotlin
interface Cache<T> { fun put(key: String, value: T) }

// what the code under test depends on, and what to name in kspMocksTargets
interface ItemCache : Cache<Item>
```

If the generic requirement is genuinely needed as such, hand-write that double. Do not hand-edit a
generated file to add it.

## `e: [ksp] kspMocksTargets: <fqn>.<fn> has a vararg parameter — not supported`

Change the signature to take a `List`, which the mock records as one argument, or hand-write the
double for that interface.

## `Unresolved reference` on `<Interface>Mock`

The mock was not generated, or it was generated somewhere the test cannot see. Check in this order:

1. **Is the interface in `kspMocksTargets`?** The option is per module, and a module that names no
   target generates nothing without reporting anything.
2. **Is the processor on the configuration that source set uses?** `kspTest` for Kotlin/JVM and
   Android, `add("kspJvmTest", …)` for a multiplatform module's JVM tests, `add("kspTestFixtures", …)`
   for a test-fixtures source set.
3. **Is the test in the right source set?** A multiplatform module's mock is visible in `jvmTest`,
   not in `commonTest`.
4. **Is the import there?** The mock is generated in the *interface's* package. A test in
   `com.example.feature` mocking `com.example.core.Repository` writes
   `import com.example.core.RepositoryMock`.
5. **Did the KSP task run?** `./gradlew :<module>:kspTestKotlin --info` names the task and its
   outcome. `NO-SOURCE` means the source set holds no Kotlin file — see the next section.

## `:<module>:kspTestFixturesKotlin NO-SOURCE`, and no mock

KSP skips a source set with no Kotlin source. A `testFixtures` source set that holds only the
processor wiring has none, so nothing is generated and every consumer fails with `Unresolved
reference`.

Put at least one Kotlin file under `src/testFixtures/kotlin/` — a builder, a sample value, anything
the fixtures genuinely need — and the task runs.

## `Unresolved reference 'kspJvmTest'` while the build script compiles

A multiplatform module has no typed accessor for that configuration. Add the dependency by name:

```kotlin
dependencies {
  add("kspJvmTest", "dev.modaal:mocks-processor:<version>")
}
```

The same applies to `kspTestFixtures` and to the Android per-variant configurations.

## `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0`

AGP 9 carries Kotlin support itself. Remove `kotlin("android")` from that module. On AGP 8 the plugin
is required and this message does not appear.

## An AGP 8 build fails with a bare JDK version string

AGP 8 does not run on the newest JDKs. The failure is terse — the version string as the whole "What
went wrong" message. Run AGP 8 builds on JDK 21 or older, or move the module to AGP 9.

## `IllegalStateException` with `<fn>Handler expected to be set.`

The method's return type has no guessable default, so the mock cannot invent a value. Seed the
handler:

```kotlin
environment.loadHandler = { id -> ReceiptConfig(retryLimit = 1, label = id) }
```

The call is still recorded when this fires — the count and the argument are appended before the
handler is consulted, so `<fn>CallCount` and `<fn>Args` are valid in the assertion that follows the
failure.

Returns that never fail this way: `Unit`, a nullable type, a `Flow`, and the types with guessable
defaults listed in [generated-api.md](generated-api.md).

## A test collecting a mock's `Flow` never finishes

Under `runTest` the observable form is a timeout rather than a hang:

```
kotlinx.coroutines.test.UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion
```

The channel that backs the member is still open, and `receiveAsFlow()` completes only when it closes.
Either close it after the sends:

```kotlin
environment.eventsChannel.trySend(ReceiptEvent.Done)
environment.eventsChannel.close()
```

or seed the handler, which bypasses the channel:

```kotlin
environment.eventsHandler = { flowOf(ReceiptEvent.Done) }
```

The same holds for a read-only `Flow` property and its `<prop>Channel`.

## `<fn>Args` does not exist

No args record is generated when the method takes no parameters, or when every parameter is
function-typed — storing a closure would keep the caller's captures alive for the mock's lifetime.
Assert through `<fn>CallCount` and through what the handler observes:

```kotlin
var seen: Int? = null
environment.measureHandler = { width, _, onDone -> onDone(width); true }
```

## The mock's constructor demands an argument that was not there before

A read-only requirement with no guessable default is seeded through the constructor. Adding such a
member to the interface breaks every construction of the mock at compile time, on purpose — the
alternative is a mock that returns an invented value for a requirement the test never considered.

Pass the value: `ReceiptDependencyMock(config = config)`.

## `UnsupportedClassVersionError` naming `KspMocksProcessorProvider`

The consuming build's Kotlin compile worker runs a JVM older than Java 17, which cannot load the
processor jar. The worker runs on the JVM the build's daemon started, which the consuming build
chooses. State it per repository:

```properties
# gradle/gradle-daemon-jvm.properties
toolchainVersion=<major, 17 or newer>
```

A module's Java toolchain setting does not decide this.

## A generated member has a name the test did not expect

Two rules produce names that look surprising and are not defects:

- **Overloads.** All but the overload with the fewest parameters carry their capitalized parameter
  names — `updateIdForceCallCount` for `update(id, force)` beside `update(id)`.
- **Nested interfaces.** `Outer.Inner` generates `InnerMock` in the enclosing package. Two nested
  interfaces with the same simple name in one package collide; rename one, or move it.

The generated file under `build/generated/ksp/` is the answer for any interface — read it rather than
guessing at the member name.
