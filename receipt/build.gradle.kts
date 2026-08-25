// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

// End-to-end proof (not published): the interfaces live in `main`, the
// processor runs in the TEST compilation only, and the tests exercise every
// generated shape. This is also the wiring consumers copy: `kspTest` (or
// `kspJvmTest` in a multiplatform module) plus the `kspMocksTargets` option.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ksp)
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(kotlin("test"))
  testImplementation(libs.kotlinx.coroutines.test)
  kspTest(project(":mocks-processor"))
}

ksp {
  arg(
    "kspMocksTargets",
    listOf(
      "dev.modaal.mocks.receipt.ReceiptDependency",
      "dev.modaal.mocks.receipt.ReceiptEnvironment",
    ).joinToString(","))
}

tasks.test {
  useJUnitPlatform()
}
