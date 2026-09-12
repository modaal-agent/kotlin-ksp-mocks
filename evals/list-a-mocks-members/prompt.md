---
description: main does not compile, and the test needs the members FeedEnvironmentMock will have — print the mock without compiling the module.
tags: [printing, members]
allowed_tools: [Read, Glob, Grep, Skill]
max_turns: 8
expected_outcome: |
  The answer runs the init script the skill ships:
  ./gradlew -I <skill directory>/scripts/print-mock-api.init.gradle.kts :feed:printMockApi -q.
  It prints the whole generated FeedEnvironmentMock.kt, compiles nothing of :feed, and so runs while
  main has the error in FeedService. For measure(width, height) the mock carries measureCallCount,
  measureArgs of MeasureArgs(width, height), and measureHandler. The answer does not require fixing
  main first, does not move the processor to the ksp configuration, and does not hand-write the mock.
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

I'm halfway through a refactor, and `./gradlew :feed:kspTestKotlin` fails in `compileKotlin`:

```
e: file:///…/feed/src/main/kotlin/com/example/feed/FeedService.kt:46:21 Return type mismatch: expected 'Int', actual 'String'.
```

The error is in `FeedService`, not in `FeedEnvironment`. I just added
`fun measure(width: Int, height: Int): Boolean` to `FeedEnvironment` and want to write its test now.
Which members will `FeedEnvironmentMock` have for it, and how do I see the whole mock before `main`
compiles again?
