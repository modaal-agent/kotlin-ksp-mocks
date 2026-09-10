---
type: llm
---
The answer must:

1. Add the processor to the `kspJvmTest` configuration through `add("kspJvmTest", …)`, and say
   that the typed accessor `kspJvmTest(…)` does not exist in a multiplatform module — a build
   script that calls it fails to compile with `Unresolved reference 'kspJvmTest'`.
2. Answer the `commonTest` question with no: a mock generated for the JVM test compilation is not
   visible from `commonTest`, and the test that uses `LoaderMock` belongs in `jvmTest`.
3. List `com.example.kmp.Loader` in `ksp { arg("kspMocksTargets", …) }`.

An answer that writes `kspJvmTest("dev.modaal:mocks-processor:…")` as a function call, or that
says the test can live in `commonTest`, fails this criterion.
