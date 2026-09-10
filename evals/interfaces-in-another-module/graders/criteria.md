---
type: llm
---
The answer must place the wiring in `:feature`, the module whose tests need the mocks, not in
`:core` where the interface is declared. It must:

1. Put the KSP plugin and the processor's test-configuration dependency in `feature/build.gradle.kts`.
2. List the interface by its fully-qualified name, `com.example.core.Repository`, in `:feature`'s
   `kspMocksTargets`.
3. State that `:core` must be on `:feature`'s test compile classpath, and that the existing
   `implementation(project(":core"))` satisfies that.
4. Say the mock is generated in the interface's package — `com.example.core` — so a test in another
   package imports `com.example.core.RepositoryMock`.

Wiring the processor into `:core`, or listing the interface in `:core`'s build script, fails this
criterion.
