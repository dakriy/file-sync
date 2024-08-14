plugins {
    kotlin("jvm") version "2.0.0"
}

group = "org.klrf"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val slf4jVersion = "2.0.16"

    implementation(kotlin("stdlib-jdk8"))

    implementation("com.uchuhimo", "konf", "1.1.2")
    implementation("io.github.oshai", "kotlin-logging-jvm", "7.0.0")
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.1")

    implementation("org.slf4j", "slf4j-api", slf4jVersion)
    runtimeOnly("org.slf4j", "slf4j-simple", slf4jVersion)

    testImplementation(kotlin("test"))
    testImplementation("io.kotest", "kotest-assertions-core-jvm", "5.9.1")
}

tasks {
    test {
        useJUnitPlatform()
    }
}
