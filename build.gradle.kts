plugins {
  java
  jacoco
  checkstyle
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.spotless)
}

group = "ru.pricewatch"
version = "0.1.0"

java {
  toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

val integrationTest: SourceSet by sourceSets.creating

configurations[integrationTest.implementationConfigurationName]
  .extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName]
  .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  runtimeOnly("io.micrometer:micrometer-registry-prometheus")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  add(integrationTest.implementationConfigurationName, "org.springframework.boot:spring-boot-starter-test")
  add(integrationTest.implementationConfigurationName, "org.springframework.boot:spring-boot-testcontainers")
  add(integrationTest.implementationConfigurationName, "org.testcontainers:junit-jupiter")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

val integrationTestTask =
  tasks.register<Test>("integrationTest") {
    description = "Интеграционные тесты (Testcontainers)."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = configurations[integrationTest.runtimeClasspathConfigurationName] + integrationTest.output
    shouldRunAfter(tasks.test)
  }

spotless {
  java {
    palantirJavaFormat(libs.versions.palantirJavaFormat.get())
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle { ktlint() }
}

checkstyle {
  toolVersion = libs.versions.checkstyle.get()
  configFile = rootProject.file("config/checkstyle/checkstyle.xml")
  isIgnoreFailures = false
  maxWarnings = 0
}

jacoco { toolVersion = libs.versions.jacoco.get() }

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required = true
    html.required = true
  }
}

// Из счёта исключены точка входа, конфигурация и DTO.
val coverageExcludes =
  listOf(
    "**/PriceWatchApplication.class",
    "**/config/**",
    "**/dto/**",
  )

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.jacocoTestReport)
  violationRules {
    rule {
      element = "BUNDLE"
      limit {
        counter = "INSTRUCTION"
        value = "COVEREDRATIO"
        minimum = "0.70".toBigDecimal()
      }
    }
  }
  classDirectories.setFrom(
    files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }),
  )
}

tasks.check { dependsOn(integrationTestTask, tasks.jacocoTestCoverageVerification) }
