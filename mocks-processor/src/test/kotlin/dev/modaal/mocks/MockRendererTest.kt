// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

package dev.modaal.mocks

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
      MockRenderer.render(
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
      MockRenderer.render(
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
      MockRenderer.render(
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
      MockRenderer.render(
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
      MockRenderer.render(
        target(
          functions =
            listOf(
              function(
                "events",
                returnType = "kotlinx.coroutines.flow.Flow<com.example.Event>",
                flowElementType = "com.example.Event"))))
    assertContains(text, "import kotlinx.coroutines.flow.receiveAsFlow")
    assertContains(text, "eventsHandler?.let { return it() }")
    assertContains(text, "return eventsChannel.receiveAsFlow()")
    assertContains(
      text, "val eventsChannel: kotlinx.coroutines.channels.Channel<com.example.Event> =")
  }

  @Test
  fun `property counters - mutable stored property counts writes`() {
    val text =
      MockRenderer.render(
        target(
          properties =
            listOf(
              MockProperty("volume", "kotlin.Double", isMutable = true, defaultValue = "0.0", flowElementType = null))))
    assertContains(text, "override var volume: kotlin.Double = 0.0")
    assertContains(text, "volumeSetCount += 1")
    assertContains(text, "var volumeSetCount: kotlin.Int = 0")
  }

  @Test
  fun `property counters - read-only Flow property gets GetCount, GetHandler and a channel`() {
    val text =
      MockRenderer.render(
        target(
          properties =
            listOf(
              MockProperty(
                "config",
                "kotlinx.coroutines.flow.Flow<com.example.Config>",
                isMutable = false,
                defaultValue = null,
                flowElementType = "com.example.Config"))))
    assertContains(text, "configGetCount += 1")
    assertContains(text, "configGetHandler?.let { return it() }")
    assertContains(text, "return configChannel.receiveAsFlow()")
    assertContains(
      text, "var configGetHandler: (() -> kotlinx.coroutines.flow.Flow<com.example.Config>)? = null")
    // Computed, never constructor-seeded.
    assertFalse(text.contains("class ServiceMock("), "Flow property must not join the constructor bag")
  }

  @Test
  fun `bag shape - non-defaultable stored properties are constructor-seeded`() {
    val text =
      MockRenderer.render(
        target(
          properties =
            listOf(
              MockProperty("config", "com.example.Config", isMutable = false, defaultValue = null, flowElementType = null),
              MockProperty("step", "kotlin.Double", isMutable = false, defaultValue = "0.0", flowElementType = null))))
    assertContains(text, "class ServiceMock(")
    assertContains(text, "  config: com.example.Config,")
    assertContains(text, "override var config: com.example.Config = config")
    // Defaultable member stays out of the bag.
    assertFalse(text.contains("step: kotlin.Double,\n)"), "defaultable property must not be constructor-seeded")
    assertContains(text, "override var step: kotlin.Double = 0.0")
  }

  @Test
  fun `overloads - fewest-parameter overload keeps the plain name`() {
    val text =
      MockRenderer.render(
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
    val straight = MockRenderer.render(target(functions, properties))
    val shuffled = MockRenderer.render(target(functions.reversed(), properties.reversed()))
    assertEquals(straight, shuffled)
    val digest = MessageDigest.getInstance("SHA-256")
    assertEquals(
      digest.digest(straight.toByteArray()).joinToString("") { "%02x".format(it) },
      MessageDigest.getInstance("SHA-256").digest(shuffled.toByteArray()).joinToString("") { "%02x".format(it) },
    )
    // Name-sorted: properties first (apex before zed), then functions
    // alpha < beta < gamma.
    val order =
      listOf("var apex", "var zed", "fun alpha", "fun beta", "fun gamma").map(straight::indexOf)
    assertTrue(order == order.sorted() && order.all { it >= 0 }, "emission order must be name-sorted: $order")
  }
}
