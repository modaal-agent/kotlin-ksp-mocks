// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

package dev.modaal.mocks.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/// Every assertion here runs against code the processor generated during THIS
/// build's test compilation (build/generated/ksp/…, never committed). An
/// interface member added in Receipt.kt fails the next compile of this file —
/// the always-hot property, demonstrated by construction.
class GeneratedMockReceiptTest {

  private val config = ReceiptConfig(retryLimit = 3, label = "receipt")

  private fun mock() = ReceiptEnvironmentMock(staticConfig = config)

  @Test
  fun `constructor bag seeds the store, and construction moves no counter`() {
    val environment = mock()
    assertEquals(0, environment.staticConfigGetCount)
    assertEquals(config, environment.staticConfig)
    assertEquals(1, environment.staticConfigGetCount)
    // A read-only requirement is a `val` override, so `_<prop>` is the seed path.
    assertEquals(0L, environment.idleTimeoutMs)
    environment._idleTimeoutMs = 250L
    assertEquals(250L, environment.idleTimeoutMs)
    assertEquals(2, environment.idleTimeoutMsGetCount)
  }

  @Test
  fun `pure-property surface is a constructor bag on every member without a default`() {
    val dependency = ReceiptDependencyMock(config = config)
    assertEquals(config, dependency.config)
    assertEquals(0.0, dependency.step)
    assertEquals(1, dependency.configGetCount)
    assertEquals(1, dependency.stepGetCount)
  }

  @Test
  fun `mutable property counts reads and writes, construction does not count`() {
    val environment = mock()
    assertEquals(0, environment.volumeSetCount)
    assertEquals(0, environment.volumeGetCount)
    environment.volume = 0.5
    environment.volume = 0.7
    assertEquals(2, environment.volumeSetCount)
    assertEquals(0.7, environment.volume)
    assertEquals(1, environment.volumeGetCount)
    // The store is the seed-and-read path that moves no counter.
    assertEquals(0.7, environment._volume)
    environment._volume = 0.9
    assertEquals(0.9, environment._volume)
    assertEquals(1, environment.volumeGetCount)
    assertEquals(2, environment.volumeSetCount)
  }

  @Test
  fun `property get handler decides the read and leaves the store alone`() {
    val environment = mock()
    environment.volume = 0.5
    environment.volumeGetHandler = { 9.0 }
    assertEquals(9.0, environment.volume)
    assertEquals(9.0, environment.volume)
    assertEquals(0.5, environment._volume)
    assertEquals(2, environment.volumeGetCount)
    // Clearing the handler hands the next read back to the store.
    environment.volumeGetHandler = null
    assertEquals(0.5, environment.volume)
  }

  @Test
  fun `flow property counts the read, the subscription, the outputs and the completion`() = runTest {
    val environment = mock()
    environment.configUpdatesChannel.trySend(config)
    environment.configUpdatesChannel.trySend(config.copy(retryLimit = 5))
    // Closing the channel is the teardown that ends the stream.
    environment.configUpdatesChannel.close()

    val stream = environment.configUpdates
    assertEquals(1, environment.configUpdatesGetCount)
    // Reading the property is not collecting it.
    assertEquals(0, environment.configUpdatesSubscribeCount)

    assertEquals(listOf(3, 5), stream.toList().map { it.retryLimit })
    assertEquals(1, environment.configUpdatesSubscribeCount)
    assertEquals(2, environment.configUpdatesOutputCount)
    assertEquals(listOf(3, 5), environment.configUpdatesOutputs.map { it.retryLimit })
    assertEquals(1, environment.configUpdatesCompletionCount)
    assertEquals(0, environment.configUpdatesSubscribeCancelCount)
  }

  @Test
  fun `a collector that stops early counts a cancellation and not a completion`() = runTest {
    val environment = mock()
    environment.configUpdatesChannel.trySend(config)
    environment.configUpdatesChannel.trySend(config.copy(retryLimit = 5))
    // No close: `first()` ends the collection itself, with a CancellationException.
    assertEquals(config, environment.configUpdates.first())
    assertEquals(1, environment.configUpdatesSubscribeCount)
    assertEquals(1, environment.configUpdatesSubscribeCancelCount)
    assertEquals(0, environment.configUpdatesCompletionCount)
    // The value that was delivered still counted.
    assertEquals(1, environment.configUpdatesOutputCount)
  }

  @Test
  fun `flow property get handler takes precedence over the channel, even seeded late`() = runTest {
    val environment = mock()
    // Captured before the handler exists: the handler is read at collection.
    val stream = environment.configUpdates
    environment.configUpdatesGetHandler = {
      kotlinx.coroutines.flow.flowOf(config.copy(label = "late"))
    }
    assertEquals(listOf("late"), stream.toList().map { it.label })
    assertEquals(1, environment.configUpdatesSubscribeCount)
    assertEquals(1, environment.configUpdatesOutputCount)
    assertEquals(1, environment.configUpdatesCompletionCount)
  }

  @Test
  fun `flow function counts the channel stream and the handler's stream alike`() = runTest {
    val environment = mock()
    environment.eventsChannel.trySend(ReceiptEvent.Tick(1))
    environment.eventsChannel.trySend(ReceiptEvent.Done)
    environment.eventsChannel.close()
    assertEquals(listOf(ReceiptEvent.Tick(1), ReceiptEvent.Done), environment.events().toList())
    assertEquals(1, environment.eventsCallCount)
    assertEquals(1, environment.eventsSubscribeCount)
    assertEquals(2, environment.eventsOutputCount)
    assertEquals(listOf(ReceiptEvent.Tick(1), ReceiptEvent.Done), environment.eventsOutputs)

    environment.eventsHandler = { kotlinx.coroutines.flow.flowOf(ReceiptEvent.Done) }
    assertEquals(listOf<ReceiptEvent>(ReceiptEvent.Done), environment.events().toList())
    assertEquals(2, environment.eventsCallCount)
    // The counters wrap the handler's stream too.
    assertEquals(2, environment.eventsSubscribeCount)
    assertEquals(3, environment.eventsOutputCount)
    assertEquals(2, environment.eventsCompletionCount)
  }

  @Test
  fun `output handler sees each value as it lands and can send the next one`() = runTest {
    val environment = mock()
    environment.eventsOutputHandler = { event ->
      if (event is ReceiptEvent.Tick) {
        environment.eventsChannel.trySend(ReceiptEvent.Done)
        environment.eventsChannel.close()
      }
    }
    environment.eventsChannel.trySend(ReceiptEvent.Tick(1))
    assertEquals(listOf(ReceiptEvent.Tick(1), ReceiptEvent.Done), environment.events().toList())
    assertEquals(2, environment.eventsOutputCount)
  }

  @Test
  fun `unset handler on a non-defaultable return fails with the family's exact string`() = runTest {
    val environment = mock()
    val failure = assertFailsWith<IllegalStateException> { environment.load("m-1") }
    assertEquals("loadHandler expected to be set.", failure.message)
    // The call was still recorded — args are appended before the handler runs.
    assertEquals(1, environment.loadCallCount)
    assertEquals(listOf("m-1"), environment.loadArgs)
  }

  @Test
  fun `seeded suspend handler returns and records`() = runTest {
    val environment = mock()
    environment.loadHandler = { id -> config.copy(label = id) }
    assertEquals("m-2", environment.load("m-2").label)
    assertEquals(listOf("m-2"), environment.loadArgs)
  }

  @Test
  fun `unit function records args and invokes the handler when set`() {
    val environment = mock()
    environment.log("first")
    var handled: String? = null
    environment.logHandler = { message -> handled = message }
    environment.log("second")
    assertEquals(2, environment.logCallCount)
    assertEquals(listOf("first", "second"), environment.logArgs)
    assertEquals("second", handled)
  }

  @Test
  fun `args data class records values, closures stay out, default return stands in`() {
    val environment = mock()
    val handled = environment.measure(width = 4, height = 3, onDone = {})
    assertEquals(false, handled)
    assertEquals(listOf(ReceiptEnvironmentMock.MeasureArgs(width = 4, height = 3)), environment.measureArgs)

    environment.measureHandler = { _, _, onDone ->
      onDone(12)
      true
    }
    var area = 0
    assertEquals(true, environment.measure(2, 6) { area = it })
    assertEquals(12, area)
    assertEquals(2, environment.measureCallCount)
  }

  @Test
  fun `zero-parameter function counts calls`() {
    val environment = mock()
    environment.reset()
    environment.reset()
    assertEquals(2, environment.resetCallCount)
  }

  @Test
  fun `nullable return falls back to null`() {
    val environment = mock()
    assertNull(environment.findLabel("x"))
    environment.findLabelHandler = { id -> "label:$id" }
    assertEquals("label:x", environment.findLabel("x"))
    assertEquals(listOf("x", "x"), environment.findLabelArgs)
  }
}
