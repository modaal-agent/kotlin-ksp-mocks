// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

import java.util.zip.ZipFile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// dev.modaal:mocks-processor — a KSP SymbolProcessor generating recording-spy
// test doubles for Kotlin interfaces. Consumers wire it into the TEST
// compilation only (`kspTest` / `kspJvmTest`) and select targets with the
// `kspMocksTargets` option; the main compilation gets no processor, no
// annotation artifact, and no generated code.
plugins {
  alias(libs.plugins.kotlin.jvm)
  `maven-publish`
}

// The class-file major of the published jar, as `<Java major>`. A processor jar
// is loaded by the Kotlin compile worker of the project consuming it, and that
// worker runs on the JVM the consuming build's daemon started — which the
// consumer chooses, not this build. 17 is the floor an Android or Gradle build
// guarantees, and the KSP API this links against is class-file 52, so nothing
// below imposes a higher one.
val publishedBytecodeTarget = JvmTarget.JVM_17

kotlin {
  // The build compiles on the current toolchain and emits for the floor above:
  // two independent numbers. `jvmToolchain` alone would set both, which is how
  // a jar consumers on an older JVM cannot load gets published.
  jvmToolchain(25)
  compilerOptions {
    jvmTarget = publishedBytecodeTarget
  }
}

java {
  sourceCompatibility = JavaVersion.toVersion(publishedBytecodeTarget.target)
  targetCompatibility = JavaVersion.toVersion(publishedBytecodeTarget.target)
}

dependencies {
  implementation(libs.ksp.api)
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

// The `jvmTarget` above sets the class-file major; this task is what keeps it
// set. It reads the jar the publication ships and fails on any entry above the
// declared floor, so a toolchain bump that silently raises the target reds this
// build instead of reaching consumers as `UnsupportedClassVersionError`.
val checkPublishedBytecodeVersion = tasks.register("checkPublishedBytecodeVersion") {
  val jarFile = tasks.jar.flatMap { it.archiveFile }
  val ceiling = publishedBytecodeTarget.target.toInt() + 44
  val label = publishedBytecodeTarget.target
  inputs.file(jarFile)
  doLast {
    val offenders = ZipFile(jarFile.get().asFile).use { zip ->
      zip.entries().asSequence()
        .filter { it.name.endsWith(".class") }
        .mapNotNull { entry ->
          val header = zip.getInputStream(entry).use { it.readNBytes(8) }
          val major = ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
          if (major > ceiling) "${entry.name}: major $major" else null
        }
        .toList()
    }
    if (offenders.isNotEmpty()) {
      error(
        buildString {
          appendLine("mocks-processor.jar carries class files above Java $label (major $ceiling):")
          offenders.take(10).forEach { appendLine("  $it") }
          if (offenders.size > 10) appendLine("  … ${offenders.size - 10} more")
          append("A consumer whose compile worker runs an older JVM fails to load the processor.")
        })
    }
  }
}

tasks.check {
  dependsOn(checkPublishedBytecodeVersion)
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
  }
}
