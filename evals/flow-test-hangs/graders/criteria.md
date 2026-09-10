---
type: llm
---
The answer must name the cause — the mock replays `eventsChannel` while `eventsHandler` is unset,
and a collector on a channel that was never closed waits forever — and give at least one of the
two fixes:

1. `environment.eventsChannel.close()` after the `trySend` calls.
2. `environment.eventsHandler = { flowOf(FeedEvent.Tick) }`, which takes precedence over the
   channel and needs no close.

An answer that blames `runTest`'s timeout, `toList()`, the dispatcher, or the test framework, and
does not reach the unclosed channel, fails this criterion.
