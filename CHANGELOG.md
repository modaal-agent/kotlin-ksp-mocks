# Changelog

## 0.2.0 — 2026-08-25

Toolchain only — no processor behavior, no change to the generated member
vocabulary, and no change to the generated output for any input.

- The build moves to Kotlin 2.4.10, Gradle 9.7.1 and JDK 25 (`coroutines`
  1.11.0). `ksp` stays at 2.3.11: KSP's 2.x line versions independently of
  the compiler, and 2.3.11 processes Kotlin 2.4.10 — `:receipt` generates and
  its suite passes on this build.
- The wrapper carries `distributionSha256Sum`, so the Gradle distribution is
  verified before it is unpacked.
- **The published jar is class-file major 69.** A processor jar is loaded by
  the Kotlin compile worker of the project consuming it, so that project's
  compile JVM must be a 25 — which it is wherever the consuming module
  declares `jvmToolchain(25)`. A consumer on `jvmToolchain(21)` should stay on
  `0.1.0`.

## 0.1.0 — 2026-08-19

- `dev.modaal:mocks-processor` — a KSP `SymbolProcessor` generating
  recording-spy mocks for Kotlin interfaces into the test compilation
  (`kspTest`/`kspJvmTest`), selected by the `kspMocksTargets` option.
  Member vocabulary: `<fn>CallCount` / `<fn>Args` / `<fn>Handler`,
  `<prop>SetCount`, `<prop>GetCount`/`<prop>GetHandler`, `Channel`-backed
  `Flow` members, and a constructor-seeded bag for members without a
  guessable default. Output is byte-deterministic (members name-sorted).
- `:receipt` — unpublished end-to-end proof module; its tests run against
  mocks the processor generated during this build's own test compilation.
