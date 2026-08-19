// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

// The receipt surface: one interface per generated shape the processor
// supports, exercised end-to-end by the module's tests. These are `main`
// declarations on purpose — the processor sees them the way a consumer's
// test compilation sees its production interfaces.
package dev.modaal.mocks.receipt

import kotlinx.coroutines.flow.Flow

data class ReceiptConfig(val retryLimit: Int, val label: String)

sealed interface ReceiptEvent {
  data class Tick(val n: Int) : ReceiptEvent

  data object Done : ReceiptEvent
}

interface ReceiptEnvironment {
  /** Defaultable read-only requirement → stored `var` override seeded 0L. */
  val idleTimeoutMs: Long

  /** Non-defaultable read-only requirement → constructor-seeded. */
  val staticConfig: ReceiptConfig

  /** Mutable requirement → stored value whose setter counts. */
  var volume: Double

  /** Read-only Flow property → GetCount/GetHandler + channel fallback. */
  val configUpdates: Flow<ReceiptConfig>

  /** Flow-returning function → CallCount/Handler + channel fallback. */
  fun events(): Flow<ReceiptEvent>

  /** Suspend, non-defaultable return → unset handler fails. */
  suspend fun load(id: String): ReceiptConfig

  /** Unit, one parameter → recorded directly. */
  fun log(message: String)

  /** Several parameters + a closure: the closure reaches the handler but
   * stays out of the args record; the Boolean return defaults to false. */
  fun measure(width: Int, height: Int, onDone: (Int) -> Unit): Boolean

  /** Zero parameters, Unit → CallCount only. */
  fun reset()

  /** Nullable return → null fallback, never a failure. */
  fun findLabel(id: String): String?
}

/** Pure-property surface → the constructor-seeded bag (adding a member here
 * breaks consumers at compile time, which is the point). */
interface ReceiptDependency {
  val config: ReceiptConfig
  val step: Double
}
