---
type: llm
---
The answer must:

1. Name the cause: `load` returns `FeedPage`, a type with no guessable default, and no handler was
   set, so the mock throws rather than invent a value.
2. Give the fix as an assignment to `environment.loadHandler` before the call — for example
   `environment.loadHandler = { id -> FeedPage(id) }`.
3. Answer the second question with the fallbacks that apply while a handler is unset: a `Unit`
   function returns nothing, a `Flow` return replays the mock's channel, a nullable return gives
   `null`, and a guessable default (`0`, `false`, `""`, `emptyList()` and the rest) is returned.
   Anything outside those throws this exception.

An answer that treats the exception as a bug in the processor, or that seeds the value through a
constructor argument or a setter the mock does not have, fails this criterion.
