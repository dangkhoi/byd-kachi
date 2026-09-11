// :car-integration — JVM module talking to head unit via adb (no Android).
plugins {
    id("java-library")
    id("application")
    id("org.jetbrains.kotlin.jvm")
}

application {
    mainClass.set("com.byd.clusternav.carexec.CarExecCli")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core"))
    implementation("dev.mobile:dadb:2.0.0")
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * ⚠⚠ `:car-integration:test` ĐỌC SCRIPT + TÀI LIỆU TỪ CÂY NGUỒN — phải khai, không thì xanh GIẢ.
 *
 * `WrapperContractTest` là hợp đồng giữa vỏ shell `scripts/vehicle/carexec.sh` và CLI; `DadbVehicleTransportTest`
 * đọc kế hoạch phiên trong `docs/`. Không tệp nào trong số đó nằm trên classpath của task test.
 *
 * [ĐO] 2026-09-12, TRƯỚC khi vá: đổi `LEDGER=` → `LEDGERX=` trong `scripts/vehicle/carexec.sh` (đúng ca
 * `WrapperContractTest` đòi sổ verdict neo vào `$ROOT`) ⇒ `:car-integration:test` **UP-TO-DATE /
 * BUILD SUCCESSFUL**; ép chạy (`--rerun`) ⇒ **BUILD FAILED**.
 *
 * `src/main/kotlin` khai tường minh vì `CarExecCliTest`/`WrapperContractTest` quét VĂN BẢN gốc của `CarExecCli.kt`
 * (đường gián tiếp qua classpath chỉ bắt thay đổi làm đổi bytecode).
 */
tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    inputs.dir(layout.projectDirectory.dir("src/main/kotlin"))
        .withPropertyName("carIntegrationSourceTextForContractTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("scripts/vehicle/carexec.sh"))
        .withPropertyName("carexecWrapperScriptForWrapperContractTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("docs/refactor-car-execution/run-on-car.md"))
        .withPropertyName("runOnCarDocForWrapperContractTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(
        rootProject.layout.projectDirectory
            .file("docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json"),
    )
        .withPropertyName("vehicleSessionPlanForDadbTransportTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Fixed no-argument T10 host entry. Tests must never execute this dadb-capable task.
tasks.register<JavaExec>("runHudSignT10") {
    group = "verification"
    description = "Run the fixed-path, no-argument HUD/sign T10 host gate"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.byd.clusternav.vehicleprobe.T10RunnerMain")
    workingDir = rootProject.projectDir
    args(emptyList<String>())
}
