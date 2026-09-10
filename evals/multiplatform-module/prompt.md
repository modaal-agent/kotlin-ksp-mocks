---
description: A Kotlin Multiplatform module with a JVM test target asks which KSP configuration to use.
tags: [wiring, kmp]
allowed_tools: [Read, Glob, Grep, Skill]
max_turns: 8
expected_outcome: |
  dependencies { add("kspJvmTest", "dev.modaal:mocks-processor:<version>") } — written with add(),
  because the typed accessor kspJvmTest(…) does not exist and a build script using it does not
  compile. The tests that use the mock live in jvmTest; a commonTest source set cannot see a mock
  generated for the JVM test compilation.
---

This module is Kotlin Multiplatform:

```kotlin
plugins {
  kotlin("multiplatform")
}

kotlin {
  jvm()
  sourceSets {
    commonMain.dependencies { implementation(libs.coroutines.core) }
    commonTest.dependencies { implementation(kotlin("test")) }
  }
}
```

`com.example.kmp.Loader` is declared in `commonMain`. I want the dev.modaal mocks-processor to
generate `LoaderMock` for my JVM tests. What exactly goes in the build script, and can the test
that uses the mock live in `commonTest`?
