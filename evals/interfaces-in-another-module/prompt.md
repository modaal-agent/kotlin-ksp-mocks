---
description: The interfaces are in :core and the tests are in :feature — which module gets the wiring.
tags: [wiring, multi-module]
allowed_tools: [Read, Glob, Grep, Skill]
max_turns: 8
expected_outcome: |
  KSP is wired in :feature, the module that holds the tests, and com.example.core.Repository is
  listed in :feature's kspMocksTargets. :core has to be on :feature's test compile classpath,
  which implementation(project(":core")) already gives. The mock is generated into :feature's own
  build/ directory, in the interface's package com.example.core, so the test imports it.
---

Two Kotlin/JVM modules. `:core` declares the interfaces:

```kotlin
package com.example.core

interface Repository {
  suspend fun fetch(id: String): Record
}
```

`:feature` holds the tests, and already has `implementation(project(":core"))`.

I want the dev.modaal mocks-processor to generate `RepositoryMock` for `:feature`'s tests. Which
module do I wire, what do I list, and which package does the mock end up in?
