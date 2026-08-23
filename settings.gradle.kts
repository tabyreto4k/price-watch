plugins {
  // JDK 21 может отсутствовать на машине — Gradle скачает нужный сам.
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "price-watch"
