plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * ⚠⚠ `T10ContractsTest` ĐỌC KẾ HOẠCH PHIÊN TRONG `docs/` — phải khai, không thì xanh GIẢ.
 *
 * [ĐO] 2026-09-12, TRƯỚC khi vá: thêm một khoảng trắng vào
 * `docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json` ⇒ `:vehicle-contracts:test`
 * **UP-TO-DATE / BUILD SUCCESSFUL**; ép chạy (`--rerun`) ⇒ **BUILD FAILED**.
 */
tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    inputs.file(
        rootProject.layout.projectDirectory
            .file("docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json"),
    )
        .withPropertyName("vehicleSessionPlanForT10ContractsTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
