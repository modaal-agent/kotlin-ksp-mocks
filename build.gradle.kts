// Copyright (c) 2026 Modaal.dev
// Licensed under the MIT License. See LICENSE file for details.

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.ksp) apply false
}

allprojects {
  group = "dev.modaal"
  // Published versions are DERIVED FROM THE TAG: the publish workflow
  // (.github/workflows/publish.yml) passes `-PpublishVersion=<tag>`, so a
  // tagged publish is exact by construction and cannot lag a hand-moved
  // literal. The `-SNAPSHOT` literal is the development default only, for
  // `publishToMavenLocal` while iterating; it tracks the current release
  // line and moves in the commit that gets tagged.
  version = providers.gradleProperty("publishVersion").getOrElse("0.2.0-SNAPSHOT")
}

subprojects {
  // The publish workflow stages the publication here first
  // (scripts/publish-maven.sh): the staged tree is asserted complete and a
  // published version immutable before anything reaches the static Maven
  // host.
  plugins.withId("maven-publish") {
    configure<PublishingExtension> {
      repositories {
        maven {
          name = "staging"
          url = uri(rootProject.layout.projectDirectory.dir("build/staging-maven"))
        }
      }
    }
  }
}
