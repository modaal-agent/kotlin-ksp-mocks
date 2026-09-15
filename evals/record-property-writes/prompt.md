---
description: a test has to assert the values the code under test wrote to a var requirement, in order, and run its own code on each write to a function-typed var.
tags: [members, properties]
allowed_tools: [Read, Glob, Grep, Skill]
max_turns: 8
expected_outcome: |
  The answer asserts the writes to volume through volumeSetArgs, in order —
  assertEquals(listOf(0.5, 0.7), environment.volumeSetArgs) — and runs code on each write to
  onChange by assigning onChangeSetHandler = { listener -> … }, which receives the value written.
  It says onChange has no onChangeSetArgs, because a function-typed property is not recorded. The
  answer does not hand-write a fake, does not subclass the mock, and does not propose a mocking
  library.
---

`:feed` is a Kotlin/JVM module wired like this:

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
  arg("kspMocksTargets", "com.example.feed.FeedEnvironment")
}
```

```kotlin
interface FeedEnvironment {
  var volume: Double
  var onChange: (() -> Unit)?
}
```

`FeedService` writes `volume` several times while the user drags a slider, and assigns a listener to
`onChange` when it starts. In a test against `FeedEnvironmentMock`, how do I assert which values
`FeedService` wrote to `volume`, in order, and how do I run my own code each time it writes to
`onChange`?
