val versions = mapOf(
    "testContainersVersion" to "1.19.3",
    "junitJupiterVersion" to "5.10.0",
    "logbackClassicVersion" to "1.5.18",
    "springKafkaVersion" to "3.3.10",
    "kafkaAvroSerializerVersion" to "8.0.0",
    "opentelemetryInstrumentationVersion" to "2.11.0",
    "springKafkaTestVersion" to "3.3.10",
    "flywayVersion" to "11.3.3",
    "hibernateEnversVersion" to "7.1.0.Final"
)

plugins {
	java
	id("org.springframework.boot") version "3.5.6"
	id("io.spring.dependency-management") version "1.1.7"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "io.ussopmm"
version = "0.0.1-SNAPSHOT"
description = "Service for collecting all device data"

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
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencies {
    // SPRING
	implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

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
    implementation("org.postgresql:postgresql")
    implementation("org.hibernate.orm:hibernate-envers:${versions["hibernateEnversVersion"]}")

    // FLYWAY MIGRATIONS
    implementation("org.flywaydb:flyway-core:${versions["flywayVersion"]}")
    runtimeOnly("org.flywaydb:flyway-database-cassandra:${versions["flywayVersion"]}")

    // HELPERS
    compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

    // TESTS
    testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // NEXUS DEPS
    implementation("io.ussopmm:avro-schemas:1.0.0-SNAPSHOT")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
