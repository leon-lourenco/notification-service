plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.cardbilling"
version = "1.0.0-SNAPSHOT"
description = "Notification dispatch with a transactional outbox"

java {
    toolchain {
        // Pinned at 21 (not the newest LTS available on the build machine) because
        // ARCHITECTURE.md fixes Java 21 for all four services in this initiative - a service
        // silently compiled against a newer JDK would break the "these four run the same stack"
        // claim the case study makes.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // The starter, not spring-kafka on its own: as of Boot 4 the Kafka auto-configuration and the
    // Testcontainers connection-details factories moved out of the monolithic autoconfigure module
    // into spring-boot-kafka, which only the starter pulls in. Declaring spring-kafka alone
    // compiles fine and then fails at runtime with no KafkaTemplate bean.
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // Redpanda rather than the generic Kafka module: docker-compose.yml runs Redpanda, so the
    // integration test exercises the same broker implementation the service is actually run
    // against locally, not a different one that merely speaks the same protocol.
    testImplementation("org.testcontainers:testcontainers-redpanda")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
