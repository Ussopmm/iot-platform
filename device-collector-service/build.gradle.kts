val versions = mapOf(
    "testContainersVersion" to "1.19.3",
    "junitJupiterVersion" to "5.10.0",
    "logbackClassicVersion" to "1.5.18",
    "springKafkaVersion" to "3.3.10",
    "kafkaAvroSerializerVersion" to "8.0.0",
    "opentelemetryInstrumentationVersion" to "2.11.0",
    "springKafkaTestVersion" to "3.3.10",
    "flywayVersion" to "11.15.0",
    "hibernateEnversVersion" to "7.1.0.Final",
    "avroSchemasVersion" to "1.0.0-SNAPSHOT",
    "apacheShardingSphereVersion" to "5.5.2",
    "postgresqlVersion" to "42.7.4",
    "resilience4jVersion" to "2.3.0"
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

configurations.all {
    resolutionStrategy.dependencySubstitution {
        // unify to GlassFish coordinates
        substitute(module("com.sun.xml.bind:jaxb-core"))
            .using(module("org.glassfish.jaxb:jaxb-core:4.0.5"))
        substitute(module("com.sun.xml.bind:jaxb-impl"))
            .using(module("org.glassfish.jaxb:jaxb-runtime:4.0.5"))
    }
}


repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencies {
    // SPRING
	implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
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
    implementation("org.postgresql:postgresql:${versions["postgresqlVersion"]}")
    implementation("org.hibernate.orm:hibernate-envers")
    implementation("org.apache.shardingsphere:shardingsphere-jdbc:${versions["apacheShardingSphereVersion"]}")

    // FLYWAY MIGRATIONS
    implementation("org.flywaydb:flyway-core:${versions["flywayVersion"]}")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:${versions["flywayVersion"]}")


    // HELPERS
    compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

    // TESTS
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
//    implementation("org.springframework.boot:spring-boot-starter-webflux-test")

    // NEXUS DEPS
    implementation("io.ussopmm:avro-schemas:${versions["avroSchemasVersion"]}")

    // RESILIENCE4J
    implementation("io.github.resilience4j:resilience4j-retry:${versions["resilience4jVersion"]}")

}

tasks.withType<Test> {
	useJUnitPlatform()
}


/*
──────────────────────────────────────────────────────
============== Resolve NEXUS credentials =============
──────────────────────────────────────────────────────
*/

//file(".env").takeIf { it.exists() }?.readLines()?.forEach {
//    val (k, v) = it.split("=", limit = 2)
//    System.setProperty(k.trim(), v.trim())
//    logger.lifecycle("${k.trim()}=${v.trim()}")
//}

file(".env").takeIf { it.exists() }?.readLines()?.forEach { line ->
    if (line.isNotBlank() && line.contains("=")) {
        val parts = line.split("=", limit = 2)
        if (parts.size == 2) {
            val (k, v) = parts
            System.setProperty(k.trim(), v.trim())
            logger.lifecycle("${k.trim()}=${v.trim()}")
        }
    }
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