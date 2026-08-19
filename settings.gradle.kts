// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
  }
}

rootProject.name = "kotlin-ksp-mocks"

// The published artifact: dev.modaal:mocks-processor.
include(":mocks-processor")
// End-to-end proof, not published: a module whose tests consume mocks the
// processor generated during this build's own test compilation.
include(":receipt")
