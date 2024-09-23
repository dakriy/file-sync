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

    implementation(libs.kotlinLogger)
    implementation(libs.kotlinCoroutinesCore)

    implementation(libs.commons)

    implementation(libs.slf4j)
    runtimeOnly(libs.slf4jSimple)

    implementation(libs.ktorClient)
    implementation(libs.ktorClientJava)

    implementation(libs.jaudiotagger)

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
