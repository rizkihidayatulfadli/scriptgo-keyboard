import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.20"
}

group = "com.magickeyboard"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.40")

    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Bytecode stays at Java 17 so the module can be consumed by the Android app.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("fixturesDir", rootDir.resolve("../docs").absolutePath)
}
