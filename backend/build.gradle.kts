plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "com.crichere"
version = "0.0.1-SNAPSHOT"
description = "Crichere backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("software.amazon.awssdk:bom:2.54.10")
		// Spring Boot's BOM imports this transitively, but io.spring.dependency-management
		// doesn't reliably resolve doubly-nested BOM imports — declare it explicitly,
		// pinned to the same version Spring Boot 4.1.1 manages.
		mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
	}
}

dependencies {
	// Spring Initializr baseline (versions managed by the Spring Boot BOM)
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("org.postgresql:postgresql")

	// Added manually per Task 1 brief (not offered by Spring Initializr)
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
	implementation("com.google.firebase:firebase-admin:9.9.0")
	implementation("software.amazon.awssdk:s3")
	implementation("com.bucket4j:bucket4j-core:8.10.1")

	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// Added manually per Task 1 brief
	testImplementation("io.mockk:mockk:1.14.11")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// The JVM's default timezone ID (e.g. the legacy "Asia/Calcutta" alias) is sent to
	// Postgres as a session TimeZone setting; Postgres doesn't recognize some legacy
	// Java tz aliases and refuses the connection. Pin the test JVM to an unambiguous zone
	// so this boots consistently regardless of the host machine's timezone.
	systemProperty("user.timezone", "UTC")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs = listOf("-Duser.timezone=UTC")
}
