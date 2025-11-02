val versions = mapOf(
    "mapstructVersion" to "1.5.5.Final",
    "testContainersVersion" to "1.19.3",
    "junitJupiterVersion" to "5.10.0",
    "logbackClassicVersion" to "1.5.18",
    "cassandraDriverVersion" to "4.19.0",
    "springKafkaVersion" to "3.3.10",
    "kafkaAvroSerializerVersion" to "8.0.0",
    "opentelemetryInstrumentationVersion" to "2.11.0",
    "springKafkaTestVersion" to "3.3.10",
    "flywayVersion" to "11.3.3",
    "cassandraWrapperVersion" to "4.11.1"
)

plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "io.ussopmm"
version = "0.0.1-SNAPSHOT"
description = "event-collector-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    mavenLocal()
//    SOLUTION FOR exception "Could not find io.confluent:kafka-avro-serializer:8.0.0."
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencies {
    //NEXUS
    implementation("io.ussopmm:avro-schemas:1.0.0-SNAPSHOT")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")


    // KAFKA
    implementation("org.springframework.kafka:spring-kafka:${versions["springKafkaVersion"]}")
    implementation("io.confluent:kafka-avro-serializer:${versions["kafkaAvroSerializerVersion"]}")

    // OBSERVABILITY
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:micrometer-tracing")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:${versions["opentelemetryInstrumentationVersion"]}")
    implementation("ch.qos.logback:logback-classic:${versions["logbackClassicVersion"]}")


    // PERSISTENCE
    implementation("org.springframework.boot:spring-boot-starter-data-cassandra")

    // FLYWAY MIGRATIONS
    implementation("org.flywaydb:flyway-core:${versions["flywayVersion"]}")
    runtimeOnly("org.flywaydb:flyway-database-cassandra:${versions["flywayVersion"]}")
    runtimeOnly("com.ing.data:cassandra-jdbc-wrapper:${versions["cassandraWrapperVersion"]}")

    // HELPERS
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    compileOnly("org.mapstruct:mapstruct:${versions["mapstructVersion"]}")

    // TESTS
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("org.junit.jupiter:junit-jupiter:${versions["junitJupiterVersion"]}")
    testImplementation("org.testcontainers:testcontainers:${versions["testContainersVersion"]}")
    testImplementation("org.testcontainers:cassandra:${versions["testContainersVersion"]}")
    testImplementation("org.testcontainers:junit-jupiter:${versions["testContainersVersion"]}")
    testImplementation("org.testcontainers:kafka:${versions["testContainersVersion"]}")
    testImplementation("org.springframework.kafka:spring-kafka-test:${versions["springKafkaTestVersion"]}")

}

tasks.withType<Test> {
    useJUnitPlatform()
}


/*
──────────────────────────────────────────────────────
============== Resolve NEXUS credentials =============
──────────────────────────────────────────────────────
*/

file(".env").takeIf { it.exists() }?.readLines()?.forEach {
    val (k, v) = it.split("=", limit = 2)
    System.setProperty(k.trim(), v.trim())
    logger.lifecycle("${k.trim()}=${v.trim()}")
}

val nexusUrl = System.getenv("NEXUS_URL") ?: System.getProperty("NEXUS_URL")
val nexusUser = System.getenv("NEXUS_USERNAME") ?: System.getProperty("NEXUS_USERNAME")
val nexusPassword = System.getenv("NEXUS_PASSWORD") ?: System.getProperty("NEXUS_PASSWORD")

if (nexusUrl.isNullOrBlank() || nexusUser.isNullOrBlank() || nexusPassword.isNullOrBlank()) {
    throw GradleException(
        "NEXUS_URL or NEXUS_USER or NEXUS_PASSWORD not set. " +
                "Please create a .env file with these properties or set environment variables."
    )
}


repositories {
    mavenCentral()
    maven {
        url = uri(nexusUrl)
        isAllowInsecureProtocol = true
        credentials {
            username = nexusUser
            password = nexusPassword
        }
    }
}