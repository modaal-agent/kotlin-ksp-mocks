// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

package dev.modaal.mocks.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
  fun `constructor bag seeds the non-defaultable property`() {
    val environment = mock()
    assertEquals(config, environment.staticConfig)
    assertEquals(0L, environment.idleTimeoutMs)
    // Read-only requirement is a var override: a test may re-seed directly.
    environment.idleTimeoutMs = 250L
    assertEquals(250L, environment.idleTimeoutMs)
  }

  @Test
  fun `pure-property surface is a constructor bag on every member without a default`() {
    val dependency = ReceiptDependencyMock(config = config)
    assertEquals(config, dependency.config)
    assertEquals(0.0, dependency.step)
  }

  @Test
  fun `mutable property counts writes, construction does not count`() {
    val environment = mock()
    assertEquals(0, environment.volumeSetCount)
    environment.volume = 0.5
    environment.volume = 0.7
    assertEquals(2, environment.volumeSetCount)
    assertEquals(0.7, environment.volume)
  }

  @Test
  fun `flow property replays the channel and counts reads`() = runTest {
    val environment = mock()
    environment.configUpdatesChannel.trySend(config)
    environment.configUpdatesChannel.trySend(config.copy(retryLimit = 5))
    // Closing the channel is the teardown that ends the stream.
    environment.configUpdatesChannel.close()
    val collected = environment.configUpdates.toList()
    assertEquals(listOf(3, 5), collected.map { it.retryLimit })
    assertEquals(1, environment.configUpdatesGetCount)
  }

  @Test
  fun `flow property get handler takes precedence over the channel`() = runTest {
    val environment = mock()
    environment.configUpdatesGetHandler = { kotlinx.coroutines.flow.flowOf(config) }
    assertEquals(listOf(config), environment.configUpdates.toList())
  }

  @Test
  fun `flow function replays the channel and the handler takes precedence when set`() = runTest {
    val environment = mock()
    environment.eventsChannel.trySend(ReceiptEvent.Tick(1))
    environment.eventsChannel.trySend(ReceiptEvent.Done)
    environment.eventsChannel.close()
    assertEquals(listOf(ReceiptEvent.Tick(1), ReceiptEvent.Done), environment.events().toList())
    assertEquals(1, environment.eventsCallCount)

    environment.eventsHandler = { kotlinx.coroutines.flow.flowOf(ReceiptEvent.Done) }
    assertEquals(listOf<ReceiptEvent>(ReceiptEvent.Done), environment.events().toList())
    assertEquals(2, environment.eventsCallCount)
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
