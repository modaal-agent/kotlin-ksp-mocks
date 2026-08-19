// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

package dev.modaal.mocks

/// The resolved shape of one interface to mock. The KSP adapter
/// (KspMocksProcessor) builds this from resolved symbols; MockRenderer turns
/// it into source text. The split keeps every emission rule unit-testable
/// without a compiler in the loop.

/** One function parameter. Function-typed parameters are never recorded in
 * the `Args` table (a stored closure would keep the caller's captures alive
 * for as long as the mock); the handler still receives them. */
data class MockParameter(
  val name: String,
  /** Rendered, fully-qualified type as it appears in the override signature. */
  val renderedType: String,
  val isFunctionType: Boolean,
)

data class MockFunction(
  val name: String,
  val isSuspend: Boolean,
  val parameters: List<MockParameter>,
  /** Rendered return type; "kotlin.Unit" when the function returns nothing. */
  val renderedReturnType: String,
  val returnIsNullable: Boolean,
  /** Rendered literal for a guessable default return (0, "", emptyList(), …);
   * null when the type has none. */
  val returnDefault: String?,
  /** Element type when the return is exactly kotlinx.coroutines.flow.Flow<E>;
   * the mock then carries a Channel the test pushes through and closes. */
  val flowElementType: String?,
)

data class MockProperty(
  val name: String,
  val renderedType: String,
  val isMutable: Boolean,
  /** Rendered literal default; null when the value must be constructor-seeded. */
  val defaultValue: String?,
  /** Element type when a READ-ONLY property is typed Flow<E>. */
  val flowElementType: String?,
)

data class MockTarget(
  val packageName: String,
  /** Simple name; the mock class is "<interfaceName>Mock". */
  val interfaceName: String,
  /** Fully-qualified name, used in the supertype clause. */
  val qualifiedName: String,
  val functions: List<MockFunction>,
  val properties: List<MockProperty>,
)
