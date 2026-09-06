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

// Свой source set видит только то, что ему выдали: без main.output @SpringBootTest не находит
// @SpringBootConfiguration, без extendsFrom(implementation) — зависимостей сервиса.
val integrationTest: SourceSet by sourceSets.creating {
  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += sourceSets.main.get().output
}

configurations[integrationTest.implementationConfigurationName]
  .extendsFrom(configurations.implementation.get(), configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName]
  .extendsFrom(configurations.runtimeOnly.get(), configurations.testRuntimeOnly.get())

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation(libs.telegrambots.starter)
  implementation(libs.telegrambots.client)
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.flywaydb:flyway-core")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation(libs.resilience4j.ratelimiter)
  implementation(libs.jsoup)

  // Flyway 10 вынес поддержку каждой СУБД в отдельный модуль.
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  runtimeOnly("io.micrometer:micrometer-registry-prometheus")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(libs.mockwebserver)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  add(integrationTest.implementationConfigurationName, "org.springframework.boot:spring-boot-starter-test")
  add(integrationTest.implementationConfigurationName, "org.springframework.boot:spring-boot-testcontainers")
  add(integrationTest.implementationConfigurationName, "org.testcontainers:junit-jupiter")
  add(integrationTest.implementationConfigurationName, "org.testcontainers:postgresql")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Xlint:deprecation") }

val integrationTestTask =
  tasks.register<Test>("integrationTest") {
    description = "Интеграционные тесты (Testcontainers)."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
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

// Домен проверяется интеграционными тестами (констрейнты, транзакции), поэтому в счёт
// идут оба прогона: иначе порог требовал бы дублировать IT юнит-тестами с моками.
tasks.jacocoTestReport {
  dependsOn(tasks.test, integrationTestTask)
  executionData(tasks.test.get(), integrationTestTask.get())
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
  executionData(tasks.test.get(), integrationTestTask.get())
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

// Сервис поставляется bootJar'ом, обычный jar никому не нужен. Пока он собирался, в build/libs
// лежало два архива, и шаблон *.jar в Dockerfile выхватывал -plain.jar без манифеста.
tasks.named("jar") { enabled = false }
