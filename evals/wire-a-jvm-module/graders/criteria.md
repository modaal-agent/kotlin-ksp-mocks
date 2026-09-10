---
type: llm
---
The answer wires build-time mock generation with the dev.modaal mocks-processor. It must:

1. Declare the Maven host `https://modaal-agent.github.io/maven` in a repositories block, and
   say the artifact is on neither Maven Central nor Google's repository.
2. Apply the KSP plugin and put the processor on a test configuration of `:feature`.
3. List `com.example.feature.FeedEnvironment` in `ksp { arg("kspMocksTargets", …) }` as a
   fully-qualified name.
4. Leave the processor version as a placeholder to resolve from the host's `maven-metadata.xml`,
   or state that the newest version has to be read from there. A pinned version number invented
   without a source fails this criterion.
5. Say that generation runs in the test compilation and the generated file is not committed.

It must not propose MockK, Mockito, or a hand-written fake, and must not put an annotation or any
processor artifact on the main classpath.
