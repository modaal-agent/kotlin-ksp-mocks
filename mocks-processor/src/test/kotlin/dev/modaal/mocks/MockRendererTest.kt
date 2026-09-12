// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

package dev.modaal.mocks

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/// Emission rules, one test per rule, against hand-built models — no compiler
/// in the loop (the :receipt module compiles and runs generated output).
class MockRendererTest {

  private fun function(
    name: String,
    parameters: List<MockParameter> = emptyList(),
    returnType: String = "kotlin.Unit",
    returnIsNullable: Boolean = false,
    returnDefault: String? = null,
    flowElementType: String? = null,
    isSuspend: Boolean = false,
  ) =
    MockFunction(
      name = name,
      isSuspend = isSuspend,
      parameters = parameters,
      renderedReturnType = returnType,
      returnIsNullable = returnIsNullable,
      returnDefault = returnDefault,
      flowElementType = flowElementType,
    )

  /** The mock source of a target that renders; a collision fails the cast. */
  private fun render(target: MockTarget) =
    assertIs<MockRenderer.Rendering.Rendered>(MockRenderer.render(target)).text

  private fun collision(target: MockTarget) =
    assertIs<MockRenderer.Rendering.Collision>(MockRenderer.render(target)).message

  private fun target(
    functions: List<MockFunction> = emptyList(),
    properties: List<MockProperty> = emptyList(),
  ) =
    MockTarget(
      packageName = "com.example",
      interfaceName = "Service",
      qualifiedName = "com.example.Service",
      functions = functions,
      properties = properties,
    )

  @Test
  fun `args record table - single parameter recorded directly`() {
    val text =
      render(
        target(
          functions =
            listOf(
              function(
                "log",
                parameters = listOf(MockParameter("message", "kotlin.String", false))))))
    assertContains(text, "val logArgs: kotlin.collections.MutableList<kotlin.String> = mutableListOf()")
    assertContains(text, "logArgs.add(message)")
    assertContains(text, "var logCallCount: kotlin.Int = 0")
  }

  @Test
  fun `args record table - several parameters become a data class, closures dropped`() {
    val text =
      render(
        target(
          functions =
            listOf(
              function(
                "measure",
                parameters =
                  listOf(
                    MockParameter("width", "kotlin.Int", false),
                    MockParameter("height", "kotlin.Int", false),
                    MockParameter("onDone", "(kotlin.Int) -> kotlin.Unit", true)),
                returnType = "kotlin.Boolean",
                returnDefault = "false"))))
    assertContains(text, "data class MeasureArgs(")
    assertContains(text, "val width: kotlin.Int,")
    assertContains(text, "val height: kotlin.Int,")
    // The closure parameter is dropped from the record but reaches the handler.
    assertFalse(text.contains("val onDone"), "closure parameter must not be recorded")
    assertContains(text, "measureArgs.add(MeasureArgs(width, height))")
    assertContains(text, "measureHandler?.let { return it(width, height, onDone) }")
    // Defaultable return: the unset-handler fallback is the default, not a failure.
    assertContains(text, "return false")
  }

  @Test
  fun `handler dispatch - non-defaultable return fails with the family's exact string`() {
    val text =
      render(
        target(
          functions =
            listOf(
              function(
                "load",
                parameters = listOf(MockParameter("id", "kotlin.String", false)),
                returnType = "com.example.Config",
                isSuspend = true))))
    assertContains(text, "override suspend fun load(id: kotlin.String): com.example.Config {")
    assertContains(text, "loadHandler?.let { return it(id) }")
    assertContains(text, "error(\"loadHandler expected to be set.\")")
    assertContains(text, "var loadHandler: (suspend (kotlin.String) -> com.example.Config)? = null")
  }

  @Test
  fun `handler dispatch - nullable return falls back to null, unit invokes handler only`() {
    val text =
      render(
        target(
          functions =
            listOf(
              function(
                "findLabel",
                parameters = listOf(MockParameter("id", "kotlin.String", false)),
                returnType = "kotlin.String?",
                returnIsNullable = true,
                returnDefault = "null"),
              function("reset"))))
    assertContains(text, "return null")
    assertFalse(text.contains("findLabelHandler expected"), "nullable return must not fail unset")
    assertContains(text, "resetHandler?.invoke()")
    assertFalse(text.contains("resetArgs"), "zero-parameter function must have no args record")
  }

  @Test
  fun `stream through handler - Flow return carries a channel fallback`() {
    val text =
      render(
        target(
          functions =
            listOf(
              function(
                "events",
                returnType = "kotlinx.coroutines.flow.Flow<com.example.Event>",
                flowElementType = "com.example.Event"))))
    assertContains(text, "import kotlinx.coroutines.flow.receiveAsFlow")
    // The handler is read at call time and its stream is counted like the channel's.
    assertContains(text, "    return (eventsHandler?.invoke() ?: eventsChannel.receiveAsFlow())")
    assertContains(text, "      .onStart { eventsSubscribeCount += 1 }")
    assertContains(text, "        eventsOutputCount += 1")
    assertContains(text, "        eventsOutputs.add(value)")
    assertContains(text, "        eventsOutputHandler?.invoke(value)")
    assertContains(
      text,
      "        if (cause is kotlinx.coroutines.CancellationException) eventsSubscribeCancelCount += 1")
    assertContains(text, "        else eventsCompletionCount += 1")
    assertContains(
      text, "val eventsChannel: kotlinx.coroutines.channels.Channel<com.example.Event> =")
    assertContains(
      text,
      "val eventsOutputs: kotlin.collections.MutableList<com.example.Event> = mutableListOf()")
    assertContains(text, "var eventsOutputHandler: ((com.example.Event) -> kotlin.Unit)? = null")
    // The flow builder is the read-only property's shape; a function needs neither import.
    assertFalse(text.contains("import kotlinx.coroutines.flow.flow"), "unused import")
    assertFalse(text.contains("import kotlinx.coroutines.flow.emitAll"), "unused import")
  }

  @Test
  fun `property counters - a mutable requirement counts reads and writes over the store`() {
    val text =
      render(
        target(
          properties =
            listOf(
              MockProperty("volume", "kotlin.Double", isMutable = true, defaultValue = "0.0", flowElementType = null))))
    assertContains(text, "override var volume: kotlin.Double\n")
    assertContains(text, "volumeGetCount += 1")
    assertContains(text, "volumeGetHandler?.let { return it() }")
    assertContains(text, "return _volume")
    assertContains(text, "volumeSetCount += 1")
    assertContains(text, "_volume = value")
    assertContains(text, "var volumeGetCount: kotlin.Int = 0")
    assertContains(text, "var volumeGetHandler: (() -> kotlin.Double)? = null")
    assertContains(text, "var volumeSetCount: kotlin.Int = 0")
    assertContains(text, "var _volume: kotlin.Double = 0.0")
  }

  @Test
  fun `property counters - a read-only requirement is a val over the store and counts no write`() {
    val text =
      render(
        target(
          properties =
            listOf(
              MockProperty("idleTimeoutMs", "kotlin.Long", isMutable = false, defaultValue = "0L", flowElementType = null))))
    assertContains(text, "override val idleTimeoutMs: kotlin.Long\n")
    assertContains(text, "idleTimeoutMsGetCount += 1")
    assertContains(text, "return _idleTimeoutMs")
    assertContains(text, "var _idleTimeoutMs: kotlin.Long = 0L")
    // A `val` requirement has no setter to count, and `_<prop>` is the seed path.
    assertFalse(text.contains("idleTimeoutMsSetCount"), "a read-only requirement must count no write")
    assertFalse(text.contains("set(value)"), "a read-only requirement must emit no setter")
  }

  @Test
  fun `property counters - read-only Flow property gets GetCount, GetHandler and a channel`() {
    val text =
      render(
        target(
          properties =
            listOf(
              MockProperty(
                "config",
                "kotlinx.coroutines.flow.Flow<com.example.Config>",
                isMutable = false,
                defaultValue = null,
                flowElementType = "com.example.Config"))))
    assertContains(text, "import kotlinx.coroutines.flow.emitAll")
    assertContains(text, "import kotlinx.coroutines.flow.flow")
    assertContains(text, "configGetCount += 1")
    // The handler is read inside the builder, so seeding it after the property
    // was read still decides the stream.
    assertContains(
      text,
      "      return flow { emitAll(configGetHandler?.invoke() ?: configChannel.receiveAsFlow()) }")
    assertContains(text, "        .onStart { configSubscribeCount += 1 }")
    assertContains(text, "        else configCompletionCount += 1")
    assertContains(
      text, "var configGetHandler: (() -> kotlinx.coroutines.flow.Flow<com.example.Config>)? = null")
    assertContains(text, "var configOutputCount: kotlin.Int = 0")
    assertContains(text, "var configSubscribeCancelCount: kotlin.Int = 0")
    // Computed, never constructor-seeded, and the channel is the only fallback.
    assertFalse(text.contains("class ServiceMock("), "Flow property must not join the constructor bag")
    assertFalse(text.contains("_config"), "a read-only Flow property must have no store")
  }

  @Test
  fun `bag shape - non-defaultable stored properties are constructor-seeded`() {
    val text =
      render(
        target(
          properties =
            listOf(
              MockProperty("config", "com.example.Config", isMutable = false, defaultValue = null, flowElementType = null),
              MockProperty("step", "kotlin.Double", isMutable = false, defaultValue = "0.0", flowElementType = null))))
    assertContains(text, "class ServiceMock(")
    assertContains(text, "  config: com.example.Config,")
    // The constructor parameter keeps the declared name and seeds the store.
    assertContains(text, "var _config: com.example.Config = config")
    // Defaultable member stays out of the bag.
    assertFalse(text.contains("step: kotlin.Double,\n)"), "defaultable property must not be constructor-seeded")
    assertContains(text, "var _step: kotlin.Double = 0.0")
  }

  @Test
  fun `overloads - fewest-parameter overload keeps the plain name`() {
    val text =
      render(
        target(
          functions =
            listOf(
              function(
                "update",
                parameters =
                  listOf(
                    MockParameter("id", "kotlin.String", false),
                    MockParameter("force", "kotlin.Boolean", false))),
              function("update", parameters = listOf(MockParameter("id", "kotlin.String", false))))))
    assertContains(text, "var updateCallCount: kotlin.Int = 0")
    assertContains(text, "var updateIdForceCallCount: kotlin.Int = 0")
  }

  @Test
  fun `name collision - a requirement that generates another's member fails the render`() {
    val message =
      collision(
        target(
          properties =
            listOf(
              MockProperty("draft", "kotlin.String", isMutable = true, defaultValue = "\"\"", flowElementType = null),
              MockProperty("draftSetCount", "kotlin.Int", isMutable = false, defaultValue = "0", flowElementType = null))))
    assertEquals(
      "kspMocksTargets: com.example.Service — draftSetCount is generated twice, for draft and for " +
        "draftSetCount; rename one of the two interface members.",
      message)
  }

  @Test
  fun `name collision - a requirement named like the store fails the render`() {
    val message =
      collision(
        target(
          properties =
            listOf(
              MockProperty("volume", "kotlin.Double", isMutable = true, defaultValue = "0.0", flowElementType = null),
              MockProperty("_volume", "kotlin.Double", isMutable = false, defaultValue = "0.0", flowElementType = null))))
    assertEquals(
      "kspMocksTargets: com.example.Service — _volume is generated twice, for _volume and for " +
        "volume; rename one of the two interface members.",
      message)
  }

  @Test
  fun `name collision - overloads left sharing a bookkeeping name fail the render`() {
    val message =
      collision(
        target(
          functions =
            listOf(
              function("f", parameters = listOf(MockParameter("a", "kotlin.Int", false))),
              function("f", parameters = listOf(MockParameter("a", "kotlin.String", false))),
              function("f", parameters = listOf(MockParameter("a", "kotlin.Boolean", false))))))
    assertEquals(
      "kspMocksTargets: com.example.Service — fACallCount is generated twice, for f(a: kotlin.Int) " +
        "and for f(a: kotlin.String); rename one of the two interface members.",
      message)
  }

  @Test
  fun `name collision - a property and a function of the same name are not one`() {
    // Kotlin allows a class to carry both, so function override names are not
    // in the checked set; their bookkeeping members are.
    val text =
      render(
        target(
          functions = listOf(function("draft", returnType = "kotlin.Int", returnDefault = "0")),
          properties =
            listOf(
              MockProperty("draft", "kotlin.Int", isMutable = false, defaultValue = "0", flowElementType = null))))
    assertContains(text, "override val draft: kotlin.Int\n")
    assertContains(text, "override fun draft(): kotlin.Int {")
  }

  @Test
  fun `byte determinism - member order in the model does not reach the output`() {
    val functions =
      listOf(
        function("beta", parameters = listOf(MockParameter("x", "kotlin.Int", false))),
        function("alpha"),
        function("gamma", returnType = "kotlin.Boolean", returnDefault = "false"))
    val properties =
      listOf(
        MockProperty("zed", "kotlin.Double", isMutable = true, defaultValue = "0.0", flowElementType = null),
        MockProperty("apex", "com.example.Config", isMutable = false, defaultValue = null, flowElementType = null))
    val straight = render(target(functions, properties))
    val shuffled = render(target(functions.reversed(), properties.reversed()))
    assertEquals(straight, shuffled)
    val digest = MessageDigest.getInstance("SHA-256")
    assertEquals(
      digest.digest(straight.toByteArray()).joinToString("") { "%02x".format(it) },
      MessageDigest.getInstance("SHA-256").digest(shuffled.toByteArray()).joinToString("") { "%02x".format(it) },
    )
    // Name-sorted: properties first (apex before zed), then functions
    // alpha < beta < gamma.
    val order =
      listOf("override val apex", "override var zed", "fun alpha", "fun beta", "fun gamma")
        .map(straight::indexOf)
    assertTrue(order == order.sorted() && order.all { it >= 0 }, "emission order must be name-sorted: $order")
  }
}
