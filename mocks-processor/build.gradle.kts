// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

// dev.modaal:mocks-processor — a KSP SymbolProcessor generating recording-spy
// test doubles for Kotlin interfaces. Consumers wire it into the TEST
// compilation only (`kspTest` / `kspJvmTest`) and select targets with the
// `kspMocksTargets` option; the main compilation gets no processor, no
// annotation artifact, and no generated code.
plugins {
  alias(libs.plugins.kotlin.jvm)
  `maven-publish`
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(libs.ksp.api)
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
  }
}
