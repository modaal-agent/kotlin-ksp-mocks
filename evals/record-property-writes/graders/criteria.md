---
type: llm
---
The answer must use the members `FeedEnvironmentMock` generates for the two `var` requirements. It must:

1. Assert the values written to `volume`, in order, through `volumeSetArgs` — for example
   `assertEquals(listOf(0.5, 0.7), environment.volumeSetArgs)`.
2. Run code on each write to `onChange` by assigning `onChangeSetHandler`, a lambda that receives the
   value written.
3. Say that `onChange` has no `onChangeSetArgs` record, because a function-typed property is not
   recorded.

Hand-writing a fake, subclassing the mock or overriding its setter, or proposing a mocking library
fails this criterion.
