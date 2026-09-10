---
description: Generation fails because a named interface declares type parameters.
tags: [diagnosis, unsupported]
allowed_tools: [Read, Glob, Grep, Skill]
max_turns: 8
expected_outcome: |
  Generic interfaces are unsupported by design; the processor refuses rather than emitting
  something that fails later at the consumer's compile. The two ways forward are to wrap the use
  site in a non-generic interface and mock that, or to hand-write the double for this one type.
  Hand-editing generated output is not one of them.
---

`./gradlew :feature:test` fails during generation:

```
e: [ksp] kspMocksTargets: com.example.core.Cache declares type parameters — generic interfaces are not supported
```

```kotlin
package com.example.core

interface Cache<T> {
  fun get(key: String): T?
  fun put(key: String, value: T)
}
```

The build script lists `com.example.core.Cache` in `kspMocksTargets`. How do I get a mock for it?
