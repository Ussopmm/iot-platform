import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.gradle.api.tasks.SourceSet


val versions = mapOf(
    "springKafkaVersion" to "3.3.10",
    "kafkaAvroSerializerVersion" to "8.0.0",
    "opentelemetryInstrumentationVersion" to "2.11.0",
    "logbackClassicVersion" to "1.5.18",
    "redisVersion" to "3.5.6",
    "feignMicrometerVersion" to "13.6",
    "keycloakAdminClientVersion" to "22.0.3",
    "springdocOpenapiStarterWebfluxUiVersion" to "2.5.0",
    "apacheShardingSphereVersion" to "5.5.2",
    "postgresqlVersion" to "42.7.4",
    "mapstructVersion" to "1.5.5.Final"
    )

plugins {
    java
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.13.0"

}

group = "io.ussopmm"
version = "0.0.1-SNAPSHOT"
description = "device-service"

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
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.15.0")
    }
}

dependencies {
    // SPRING
//    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // HELPERS
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("io.github.openfeign:feign-micrometer:${versions["feignMicrometerVersion"]}")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:${versions["springdocOpenapiStarterWebfluxUiVersion"]}")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.mapstruct:mapstruct:${versions["mapstructVersion"]}")
    annotationProcessor("org.mapstruct:mapstruct-processor:${versions["mapstructVersion"]}")


    // KAFKA
    implementation("org.springframework.kafka:spring-kafka:${versions["springKafkaVersion"]}")
    implementation("io.confluent:kafka-avro-serializer:${versions["kafkaAvroSerializerVersion"]}")

    // KEYCLOAK
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.keycloak:keycloak-admin-client:${versions["keycloakAdminClientVersion"]}")

    // OBSERVABILITY
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:micrometer-tracing")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:${versions["opentelemetryInstrumentationVersion"]}")
    implementation("ch.qos.logback:logback-classic:${versions["logbackClassicVersion"]}")

    // CACHE
    implementation("org.springframework.boot:spring-boot-starter-data-redis:${versions["redisVersion"]}")

    // PERSISTENCE
    implementation("org.postgresql:postgresql:${versions["postgresqlVersion"]}")
    implementation("org.apache.shardingsphere:shardingsphere-jdbc:${versions["apacheShardingSphereVersion"]}")

    // TESTING
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // NEXUS
    implementation("io.ussopmm:avro-schemas:1.0.0-SNAPSHOT")
}

tasks.withType<Test> {
    useJUnitPlatform()
}





/*
──────────────────────────────────────────────────────
============== Api generation ==============
──────────────────────────────────────────────────────
*/

val openApiDir = file("${rootDir}/openapi")

val foundSpecifications = openApiDir.listFiles { f -> f.extension in listOf("yaml", "yml") } ?: emptyArray()
logger.lifecycle("Found ${foundSpecifications.size} specifications: " + foundSpecifications.joinToString { it.name })

foundSpecifications.forEach { specFile ->
    val ourDir = getAbsolutePath(specFile.nameWithoutExtension)
    val packageName = defineJavaPackageName(specFile.nameWithoutExtension)

    val taskName = buildGenerateApiTaskName(specFile.nameWithoutExtension)
    logger.lifecycle("Register task ${taskName} from ${ourDir.get()}")
    val basePackage = "io.ussopmm.${packageName}"

    tasks.register(taskName, GenerateTask::class) {
        generatorName.set("spring")
        inputSpec.set(specFile.absolutePath)
        outputDir.set(ourDir)

        configOptions.set(
            mapOf(
                "library" to "spring-cloud",
                "skipDefaultInterface" to "true",
                "useBeanValidation" to "true",
                "openApiNullable" to "false",
                "useFeignClientUrl" to "true",
                "useTags" to "true",
                "useJakartaEe" to "true",
                "apiPackage" to "${basePackage}.api",
                "modelPackage" to "${basePackage}.dto",
                "configPackage" to "${basePackage}.config"
            )
        )

        doFirst {
            logger.lifecycle("$taskName: starting generation from ${specFile.name}")
        }
    }
}


fun getAbsolutePath(nameWithoutExtension: String): Provider<String> {
    return layout.buildDirectory
        .dir("generated-sources/openapi/${nameWithoutExtension}")
        .map { it.asFile.absolutePath }
}

fun defineJavaPackageName(name: String): String {
    val beforeDash = name.substringBefore('-')
    val match = Regex("^[a-z]+]").find(beforeDash)
    return match?.value ?: beforeDash.lowercase()
}

fun buildGenerateApiTaskName(name: String): String {
    return buildTaskName("generate", name)
}

fun buildJarTaskName(name: String): String {
    return buildTaskName("jar", name)
}

fun buildTaskName(taskPrefix: String, name: String): String {
    val prepareName = name
        .split(Regex("[^A-Za-z0-9]"))
        .filter { it.isNotBlank() }
        .joinToString("") { it.replaceFirstChar(Char::uppercase) }

    return "${taskPrefix}-${prepareName}"
}

val withoutExtensionNames = foundSpecifications.map { it.nameWithoutExtension }

sourceSets.named("main") {
    withoutExtensionNames.forEach { name ->
        java.srcDir(layout.buildDirectory.dir("generated-sources/openapi/$name/src/main/java"))
    }
}


tasks.register("generateAllOpenApi") {
    foundSpecifications.forEach { specFile ->
        dependsOn(buildGenerateApiTaskName(specFile.nameWithoutExtension))
    }
    doLast {
        logger.lifecycle("generateAllOpenApi: all specifications has been generated")
    }
}

tasks.named("compileJava") {
    dependsOn("generateAllOpenApi")
}


sourceSets {
    main {
        java {
            srcDir("build/generated-sources/openapi/device-api/src/main/java")
        }
    }
}





/*
──────────────────────────────────────────────────────
============== Resolve NEXUS credentials =============
──────────────────────────────────────────────────────
*/

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
