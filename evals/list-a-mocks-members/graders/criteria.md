---
type: llm
---
The answer must show the user how to see the generated mock while `main` does not compile. It must:

1. Give the commands that download the init script from
   `https://raw.githubusercontent.com/modaal-agent/kotlin-ksp-mocks/<version>/gradle/print-mock-api.init.gradle.kts`,
   where `<version>` is the processor version `:feed` declares, and run it on `:feed` —
   `./gradlew -I <downloaded file> :feed:printMockApi -q`.
2. Say the task does not compile `:feed`, so it runs while `FeedService` has the compile error.
3. Say it prints the whole generated `FeedEnvironmentMock.kt`, the file the test compilation writes.
4. Name the members `measure` generates: `measureCallCount`, `measureArgs` holding
   `MeasureArgs(width, height)` records, and `measureHandler`, which returns `false` while unset.

Telling the user that `main` has to compile before the mock can be seen, moving the processor to the
`ksp` configuration, or hand-writing the mock fails this criterion.
