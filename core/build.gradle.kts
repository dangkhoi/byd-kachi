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

/**
 * ⚠⚠ `:core:test` ĐỌC CÂY NGUỒN CỦA MODULE KHÁC — phải khai, không thì bài canh cho dấu xanh GIẢ.
 *
 * Ba bài ở đây quét mã của module khác (`SourceRoots` / `moduleSourceRoots()` giải sang `app/`, `car-integration/`):
 * `LauncherWindowingGuardTest` (cấm `:app` chạm display ≥ 1), `PersistentWindowStateWriterGuardTest`
 * (một-nơi-ghi-duy-nhất), `CarExecCommandsTest` (`:app` không được dùng shell thô). `PreflightTest` còn đọc
 * `CarExecCli.kt` của `:car-integration` + sổ `verdicts.tsv`, `T10SessionSafetyTest` đọc kế hoạch phiên trong `docs/`.
 *
 * [ĐO] 2026-09-12, TRƯỚC khi vá — hai phép, cả hai xanh giả:
 *  - đổi chuỗi `"--pkg"` → `"--pkgZ"` trong `car-integration/src/main/kotlin/.../CarExecCli.kt` (đúng ca
 *    `PreflightTest` đòi mọi placeholder có cờ CLI) ⇒ `:core:test` **UP-TO-DATE / BUILD SUCCESSFUL**;
 *    ép chạy ⇒ **BUILD FAILED**. Đây là đổi BYTECODE mà vẫn không rerun, vì `:core` KHÔNG phụ thuộc
 *    `:car-integration` (chiều ngược lại) nên classpath của nó không có gì động.
 *  - thêm một dòng vào `docs/refactor-car-execution/verdicts.tsv` (phá `startsWith(HEADER)`) ⇒ **UP-TO-DATE /
 *    BUILD SUCCESSFUL**; ép chạy ⇒ **BUILD FAILED**.
 *
 * `src/main/kotlin` của chính `:core` cũng khai tường minh: đường gián tiếp qua classpath chỉ bắt thay đổi
 * làm ĐỔI BYTECODE, còn các bài này quét văn bản gốc (kể cả chú thích/KDoc).
 */
tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Golden voice-coverage test đọc corpus câu nói ở scripts/voice/data/*.tsv qua clusternav.root.
    systemProperty("clusternav.root", rootProject.projectDir.absolutePath)
    inputs.dir(rootProject.layout.projectDirectory.dir("scripts/voice/data"))
        .withPropertyName("voiceGoldenCorpus")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs.dir(rootProject.layout.projectDirectory.dir("app/src/main/java"))
        .withPropertyName("appSourceTextForCoreGuardTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("car-integration/src/main/kotlin"))
        .withPropertyName("carIntegrationSourceTextForPreflightAndGuardTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/main/kotlin"))
        .withPropertyName("coreSourceTextForCoreGuardTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("docs/refactor-car-execution/verdicts.tsv"))
        .withPropertyName("verdictLedgerForPreflightTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(
        rootProject.layout.projectDirectory
            .file("docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json"),
    )
        .withPropertyName("vehicleSessionPlanForT10SessionSafetyTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
