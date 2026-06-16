import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)

    // Kotlin
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    // OpenAPI
    implementation(libs.springdoc.openapi)

    // DB
    runtimeOnly(libs.mysql.connector.j)
    runtimeOnly(libs.h2)

    // JWT
    implementation(libs.jjwt.api)
    runtimeOnly(libs.bundles.jjwt.runtime)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.mockk)
    testImplementation(libs.bundles.testcontainers.mysql)

    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-Xshare:off")
}

tasks.named<Test>("test") {
    description = "단위 테스트를 실행합니다."

    // 클래스명이 *IntegrationTest 인 테스트는 기본 test에서 제외합니다.
    exclude("**/*IntegrationTest*")
}

val integrationTest by tasks.registering(Test::class) {
    description = "MySQL Testcontainers 기반 통합 테스트를 실행합니다."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    shouldRunAfter(tasks.named("test"))

    useJUnitPlatform()

    // 클래스명이 *IntegrationTest 인 테스트만 실행합니다.
    include("**/*IntegrationTest*")
}

// 통합 테스트는 Docker 의존성이 있으므로 기본 check에는 연결하지 않습니다.
// 필요할 때 명시적으로 실행:
//
// ./gradlew integrationTest
