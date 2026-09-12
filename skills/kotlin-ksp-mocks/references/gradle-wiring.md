# Gradle wiring reference

Every shape below was run against a real Gradle build. Where a shape needs something unobvious — a
`add(…)` call instead of a configuration accessor, a placeholder source file, a plugin that must not
be applied — that is stated with the error you get without it.

## The artifact and its version

`dev.modaal:mocks-processor` is served by a static Maven host, not by Maven Central and not by
Google's repository. Declare it once:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven { url = uri("https://modaal-agent.github.io/maven") }
  }
}
```

A build whose repositories omit it fails with `Could not find dev.modaal:mocks-processor` and a
"Searched in the following locations" list that does not include the host.

Resolve the newest version from the host's metadata rather than writing a number from memory:

```bash
curl -s https://modaal-agent.github.io/maven/dev/modaal/mocks-processor/maven-metadata.xml
```

The `<release>` element is the version to write. With a version catalog:

```toml
# gradle/libs.versions.toml
[versions]
mocks-processor = "<release from the metadata>"
ksp = "<a KSP release supporting this project's Kotlin>"

[libraries]
mocks-processor = { module = "dev.modaal:mocks-processor", version.ref = "mocks-processor" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

KSP's 2.x line versions independently of the Kotlin compiler — one KSP release processes a range of
Kotlin versions, because a processor couples to the KSP API rather than to the compiler.

## Kotlin/JVM module

```kotlin
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

The KSP task is `:<module>:kspTestKotlin` and it writes to
`<module>/build/generated/ksp/test/kotlin/<interface package>/`. Besides the internal
`*ProcessorClasspath` entries, a Kotlin/JVM module offers two KSP configurations — `ksp` for the main
compilation, which this processor is never wired into, and `kspTest`.

## Kotlin Multiplatform module

The typed accessor does not exist. Writing `kspJvmTest("…")` fails the build script with
`Unresolved reference 'kspJvmTest'`, so add the dependency by configuration name:

```kotlin
plugins {
  kotlin("multiplatform")
  id("com.google.devtools.ksp") version "<ksp version>"
}

kotlin {
  jvm()
  sourceSets {
    commonTest.dependencies { implementation(kotlin("test")) }
  }
}

dependencies {
  add("kspJvmTest", "dev.modaal:mocks-processor:<version>")
}

ksp { arg("kspMocksTargets", "com.example.Loader") }
```

The task is `:kspTestKotlinJvm`, writing to `build/generated/ksp/jvm/jvmTest/kotlin/`. The
configurations a `jvm()` module offers are `ksp`, `kspCommonMainMetadata`, `kspJvm` and `kspJvmTest`.

**Tests that use the mock live in `jvmTest`.** A `commonTest` source set does not see it — the
reference from common code fails with `Unresolved reference` on the mock class, because the mock was
generated for the JVM test compilation only.

## Android module

Both AGP majors use `kspTest`, which covers the unit tests of every variant; `kspTestDebug` and
`kspTestRelease` are the per-variant configurations, and `kspAndroidTest*` is for instrumented
tests. There is no `kspTestDebugUnitTest`.

```kotlin
plugins {
  id("com.android.library")
  // On AGP 8 add: kotlin("android")
  id("com.google.devtools.ksp") version "<ksp version>"
}

android {
  namespace = "com.example.feed"
  compileSdk = <sdk>
  defaultConfig { minSdk = <sdk> }
}

dependencies {
  testImplementation("junit:junit:<version>")
  add("kspTest", "dev.modaal:mocks-processor:<version>")
}

ksp { arg("kspMocksTargets", "com.example.feed.Repository") }
```

- **AGP 9 refuses `kotlin("android")`** — applying it fails with "The 'org.jetbrains.kotlin.android'
  plugin is no longer required for Kotlin support since AGP 9.0." Kotlin support is built into AGP
  there.
- **AGP 8 requires it**, and does not run on a JDK as new as 25 — an AGP 8 build on JDK 25 fails
  with the JDK's version string as the whole message. Run AGP 8 builds on JDK 21 or older.

The task is `:<module>:kspDebugUnitTestKotlin`, writing to
`<module>/build/generated/ksp/debugUnitTest/kotlin/`. In a default AGP library only the debug variant
has a unit-test task, so `testDebugUnitTest` is what runs the tests.

## Interfaces in one module, tests in another

Wire KSP in the module that holds the tests, and list the other module's interfaces there:

```kotlin
// feature/build.gradle.kts
dependencies {
  implementation(project(":core"))
  kspTest("dev.modaal:mocks-processor:<version>")
}

ksp { arg("kspMocksTargets", "com.example.core.Repository") }
```

`:core` has to be on `:feature`'s test compile classpath, which `implementation(project(":core"))`
gives. The mock is generated under `feature/build/`, in `:core`'s package — `com.example.core` — so
a test in `com.example.feature` writes `import com.example.core.RepositoryMock`.

Two modules that both name the same interface each generate their own copy. That costs nothing at
runtime and keeps each module's test compilation independent; use `testFixtures` below when one copy
is wanted.

## One set of mocks shared by several modules

```kotlin
// core/build.gradle.kts
plugins {
  kotlin("jvm")
  id("com.google.devtools.ksp") version "<ksp version>"
  `java-test-fixtures`
}

dependencies {
  add("kspTestFixtures", "dev.modaal:mocks-processor:<version>")
}

ksp { arg("kspMocksTargets", "com.example.core.Repository") }
```

```kotlin
// feature/build.gradle.kts
dependencies {
  testImplementation(testFixtures(project(":core")))
}
```

**`src/testFixtures/kotlin/` must hold at least one Kotlin file.** KSP skips a source set with no
Kotlin source, reporting `:core:kspTestFixturesKotlin NO-SOURCE`, and nothing is generated — the
consumer then fails with `Unresolved reference` on the mock. One file of test data or one helper is
enough.

The task is `:<module>:kspTestFixturesKotlin`, writing to
`<module>/build/generated/ksp/testFixtures/kotlin/`.

## Naming targets

`kspMocksTargets` is one comma-separated string of fully-qualified interface names. Surrounding
whitespace around each name is trimmed, and an empty entry is ignored, so a list may be built for
readability:

```kotlin
ksp {
  arg(
    "kspMocksTargets",
    listOf(
      "com.example.core.Repository",
      "com.example.core.Outer.Inner",
    ).joinToString(","))
}
```

A nested interface is written with dots — `com.example.core.Outer.Inner` — and generates
`InnerMock`, from the simple name, in the enclosing package.

The option is read per module. A module that wires the processor but names no target generates
nothing and reports no error.

## The JVM the processor runs on

The published jar is class-file major 61, so it loads wherever the consuming build's Kotlin compile
worker runs Java 17 or newer. That worker runs on the JVM the consuming build's daemon started. A
daemon on an older JVM reports `UnsupportedClassVersionError` naming `KspMocksProcessorProvider`;
state the daemon JVM per repository in `gradle/gradle-daemon-jvm.properties` with
`toolchainVersion=<major>`.

The Java toolchain a module compiles with is a separate setting and does not decide this.

## Generated sources, the IDE, and version control

Generated files land under the consuming module's `build/`, which a Kotlin or Android `.gitignore`
already excludes. Nothing is committed and there is no drift to check — an interface member added in
`main` fails the next test compile against the regenerated mock.

When a member name is in question, print the generated file with `printMockApi`
([printing-members.md](printing-members.md)). After wiring the processor for the first time, run the
module's test compilation once (`./gradlew :<module>:test`) so the IDE indexes the generated sources.
