// :core — pure Kotlin JVM module (no Android, no dadb).
plugins {
    id("java-library")
    id("java-test-fixtures")
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":vehicle-contracts"))
    // W1b: StateFlow cho CarStatusRepository (poll 2 nhịp). kotlinx-coroutines-core = JVM THUẦN (không android) →
    // hợp luật Q1 :core. Version 1.10.2 khớp :app (kotlinx-coroutines-android), verify Context7 2026-09-10.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // W1b: test StateFlow/poll của CarStatusRepository (runTest/advanceTimeBy/runCurrent).
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
