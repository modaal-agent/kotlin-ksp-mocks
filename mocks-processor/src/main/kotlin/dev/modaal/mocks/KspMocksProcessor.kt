// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

package dev.modaal.mocks

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

class KspMocksProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
    KspMocksProcessor(environment)
}

/**
 * Generates one `<Interface>Mock` per fully-qualified interface name listed in
 * the `kspMocksTargets` option (comma-separated). Selection is a build-script
 * list rather than an in-source marker by measurement: an interface declared
 * in `commonMain`/`main` reaches the TEST compilation's KSP pass as a binary
 * (`origin=KOTLIN_LIB`), and KDoc does not survive compilation — an in-source
 * marker would demand an annotation artifact on the main classpath, which
 * this processor exists to avoid. Wire it into the test configurations only:
 *
 * ```
 * dependencies { kspJvmTest(libs.mocks.processor) }   // or kspTest
 * ksp { arg("kspMocksTargets", "com.example.FeedEnvironment") }
 * ```
 */
class KspMocksProcessor(private val env: SymbolProcessorEnvironment) : SymbolProcessor {
  private var done = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    // One pass: the generated files would otherwise be re-presented to this
    // processor in the next round.
    if (done) return emptyList()
    done = true

    env.options[OPTION].orEmpty()
      .split(',')
      .map(String::trim)
      .filter(String::isNotEmpty)
      .forEach { fqn -> generate(resolver, fqn) }
    return emptyList()
  }

  private fun generate(resolver: Resolver, fqn: String) {
    val declaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(fqn))
    if (declaration == null) {
      env.logger.error("$OPTION: $fqn is not resolvable in this compilation")
      return
    }
    if (declaration.classKind != ClassKind.INTERFACE) {
      env.logger.error("$OPTION: $fqn is not an interface")
      return
    }
    if (declaration.typeParameters.isNotEmpty()) {
      env.logger.error("$OPTION: $fqn declares type parameters — generic interfaces are not supported")
      return
    }

    val target = buildTarget(declaration, fqn) ?: return
    val text = MockRenderer.render(target)
    val file =
      env.codeGenerator.createNewFile(
        // The target usually arrives as a classpath binary with no containing
        // file; `aggregating` re-runs generation when inputs it cannot name
        // change. Generation is measured in fractions of a second per module.
        Dependencies(aggregating = true, *listOfNotNull(declaration.containingFile).toTypedArray()),
        target.packageName,
        "${target.interfaceName}Mock",
      )
    file.writer().use { it.append(text) }
  }

  private fun buildTarget(declaration: KSClassDeclaration, fqn: String): MockTarget? {
    // All abstract members, inherited ones included (the Swift twin mocks a
    // protocol's full requirement set); members with interface-default bodies
    // keep their defaults.
    val abstractFunctions = declaration.getAllFunctions().filter { it.isAbstract }.toList()
    val functions = abstractFunctions.map { toMockFunction(it, fqn) ?: return null }
    val properties =
      declaration.getAllProperties()
        .filter { it.isAbstract() }
        .map { property ->
          val type = property.type.resolve()
          val rendered = renderType(type)
          MockProperty(
            name = property.simpleName.asString(),
            renderedType = rendered,
            isMutable = property.isMutable,
            defaultValue = defaultLiteral(rendered, type),
            flowElementType = flowElement(type),
          )
        }
        .toList()
    return MockTarget(
      packageName = declaration.packageName.asString(),
      interfaceName = declaration.simpleName.asString(),
      qualifiedName = declaration.qualifiedName?.asString() ?: fqn,
      functions = functions,
      properties = properties,
    )
  }

  private fun toMockFunction(fn: KSFunctionDeclaration, fqn: String): MockFunction? {
    for (parameter in fn.parameters) {
      if (parameter.isVararg) {
        env.logger.error(
          "$OPTION: $fqn.${fn.simpleName.asString()} has a vararg parameter — not supported")
        return null
      }
    }
    val parameters =
      fn.parameters.map { parameter ->
        val type = parameter.type.resolve()
        MockParameter(
          name = parameter.name?.asString() ?: "p",
          renderedType = renderType(type),
          isFunctionType = type.isFunctionType || type.isSuspendFunctionType,
        )
      }
    val returnType = fn.returnType?.resolve()
    val rendered = returnType?.let(::renderType) ?: "kotlin.Unit"
    return MockFunction(
      name = fn.simpleName.asString(),
      isSuspend = Modifier.SUSPEND in fn.modifiers,
      parameters = parameters,
      renderedReturnType = rendered,
      returnIsNullable = returnType?.isMarkedNullable == true,
      returnDefault = returnType?.let { defaultLiteral(rendered, it) },
      flowElementType = returnType?.let(::flowElement),
    )
  }

  /** Element type when [type] is exactly kotlinx.coroutines.flow.Flow<E>. */
  private fun flowElement(type: KSType): String? {
    if (type.isMarkedNullable) return null
    if (type.declaration.qualifiedName?.asString() != "kotlinx.coroutines.flow.Flow") return null
    val element = type.arguments.singleOrNull()?.type?.resolve() ?: return null
    return renderType(element)
  }

  /** Fully-qualified rendering, valid in any package. Function types use the
   * arrow syntax (`SuspendFunctionN` is not denotable in source). */
  private fun renderType(type: KSType): String {
    if (type.isFunctionType || type.isSuspendFunctionType) {
      val arguments = type.arguments.map { it.type?.resolve()?.let(::renderType) ?: "*" }
      val parameters = arguments.dropLast(1).joinToString(", ")
      val returnType = arguments.last()
      val prefix = if (type.isSuspendFunctionType) "suspend " else ""
      val core = "$prefix($parameters) -> $returnType"
      return if (type.isMarkedNullable) "($core)?" else core
    }
    val declaration = type.declaration
    val base = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
    val arguments =
      if (type.arguments.isEmpty()) ""
      else
        type.arguments.joinToString(", ", "<", ">") { argument ->
          argument.type?.resolve()?.let(::renderType) ?: "*"
        }
    val nullable = if (type.isMarkedNullable) "?" else ""
    return "$base$arguments$nullable"
  }

  private fun defaultLiteral(rendered: String, type: KSType): String? =
    when {
      type.isMarkedNullable -> "null"
      rendered == "kotlin.Int" -> "0"
      rendered == "kotlin.Long" -> "0L"
      rendered == "kotlin.Double" -> "0.0"
      rendered == "kotlin.Float" -> "0.0f"
      rendered == "kotlin.Boolean" -> "false"
      rendered == "kotlin.String" -> "\"\""
      rendered.startsWith("kotlin.collections.List<") -> "emptyList()"
      rendered.startsWith("kotlin.collections.Map<") -> "emptyMap()"
      rendered.startsWith("kotlin.collections.Set<") -> "emptySet()"
      rendered.startsWith("kotlin.collections.MutableList<") -> "mutableListOf()"
      rendered.startsWith("kotlin.collections.MutableMap<") -> "mutableMapOf()"
      rendered.startsWith("kotlin.collections.MutableSet<") -> "mutableSetOf()"
      else -> null
    }

  private companion object {
    const val OPTION = "kspMocksTargets"
  }
}
