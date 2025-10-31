import org.gradle.api.publish.maven.MavenPublication

plugins {
	java
	id("org.springframework.boot") version "3.5.6"
	id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "io.ussopmm"
version = "1.0.0-SNAPSHOT"
description = "Avro schema centralization module"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(24)
	}
}

repositories {
	mavenCentral()
}

configurations.all { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.apache.avro:avro:1.11.3")

}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named("compileJava") {
    dependsOn("generateAvroJava")
}

/*
──────────────────────────────────────────────────────
============== Resolve NEXUS credentials ==============
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
        "NEXUS details are not set. Create a .env file with correct properties: " +
                "NEXUS_URL, NEXUS_USERNAME, NEXUS_PASSWORD"
    )
}

/*
──────────────────────────────────────────────────────
============== Nexus Publishing ==============
──────────────────────────────────────────────────────
*/

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "io.ussopmm"
            artifactId = project.name
            version = "1.0.0-SNAPSHOT"

            pom {
                name.set("Avro Schemas - ${project.name}")
                description.set("Avro schema centralization module")
            }
        }
    }
    
    repositories {
        maven {
            name = "nexus"
            url = uri(nexusUrl)
            isAllowInsecureProtocol = true
            credentials {
                username = nexusUser
                password = nexusPassword
            }
        }
    }
}
