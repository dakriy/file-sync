plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
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
    implementation(libs.kotlinLogger)
    implementation(libs.kotlinCoroutinesCore)
    implementation(libs.commons)
    implementation(libs.slf4j)
    implementation(libs.ktorClient)
    implementation(libs.ktorClientJava)
    implementation(libs.ktorContentNegotiation)
    implementation(libs.ktorSerialization)
    implementation(libs.jaudiotagger)
    implementation(libs.serialization)
    implementation(libs.webdav)
    implementation(libs.jsoup)
    implementation(libs.reflect)

    runtimeOnly(libs.slf4jSimple)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockFTPServer)
    testImplementation(libs.kotestCore)
    testImplementation(libs.jimFs)
}

tasks {
    test {
        useJUnitPlatform()
    }
}
