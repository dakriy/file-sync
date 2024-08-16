plugins {
    kotlin("jvm") version "2.0.0"
}

group = "org.klrf"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(libs.konfCore)
    implementation(libs.konfYaml)

    implementation(libs.exposedCore)
    runtimeOnly(libs.exposedJdbc)

    implementation(libs.kotlinLogger)
    implementation(libs.kotlinCoroutinesCore)

    implementation(libs.slf4j)
    runtimeOnly(libs.slf4jSimple)
    runtimeOnly(libs.postgres)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotestCore)
    testRuntimeOnly(libs.h2)
}

tasks {
    test {
        useJUnitPlatform()
    }
}
