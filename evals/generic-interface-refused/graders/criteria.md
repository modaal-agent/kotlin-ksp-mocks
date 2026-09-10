---
type: llm
---
The answer must:

1. State that the processor does not support generic interfaces, and that the error is a
   deliberate refusal rather than a misconfiguration to work around.
2. Offer wrapping the use site in a non-generic interface — for example a `RecordCache` with
   `Cache<Record>`'s members spelled out — and listing that in `kspMocksTargets` instead, or
   hand-writing the double for this one type.
3. Say to remove `com.example.core.Cache` from `kspMocksTargets`, since leaving it there keeps the
   build failing.

An answer that proposes editing a generated file by hand, or that claims a type argument, a
`reified` parameter or a KSP option makes the generic interface work, fails this criterion.
