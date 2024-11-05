import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    alias(libs.plugins.shadow)
    id("application")
}

group = "com.persignum"

val projectMainClass = "com.persignum.filesync.MainKt"
application {
    mainClass.set(projectMainClass)
}

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
    implementation(libs.clikt)

    runtimeOnly(libs.slf4jSimple)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockFTPServer)
    testImplementation(libs.kotestCore)
    testImplementation(libs.jimFs)
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = projectMainClass
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    test {
        useJUnitPlatform()
    }
}
