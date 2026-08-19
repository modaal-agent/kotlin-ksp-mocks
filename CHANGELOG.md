# Changelog

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
