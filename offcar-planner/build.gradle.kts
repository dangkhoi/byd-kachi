plugins {
    id("java-library")
    id("application")
    id("org.jetbrains.kotlin.jvm")
}

application {
    mainClass.set("com.byd.clusternav.offcar.OffCarPlannerMain")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":vehicle-contracts"))
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * ⚠⚠ BỘ SEAL Ở ĐÂY QUÉT CẢ REPO — không khai đầu vào thì **niêm phong là dấu xanh GIẢ**.
 *
 * `:offcar-planner` không phụ thuộc module nào ngoài `:vehicle-contracts`, nhưng test của nó lại **ghim
 * SHA-256** của tệp thuộc `:app` (`ExpansionTransportFenceTest.PARENT_ARTIFACT_HASHES`:
 * `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/values/strings.xml`), quét
 * `app|core|car-integration/src/main` tìm tham chiếu planner bị cấm (`DependencyBoundaryTest`), đọc
 * `settings.gradle.kts` + 4 tệp `build.gradle.kts`, và đọc `docs/` + `scripts/`. KHÔNG một thứ nào trong đó
 * nằm trên classpath của task test.
 *
 * [ĐO] 2026-09-12, TRƯỚC khi vá — ba phép, cả ba xanh giả:
 *  1. thêm MỘT dấu cách vào `app/src/main/res/values/strings.xml` (tệp NIÊM PHONG) ⇒ hash đổi
 *     `45fa51a8…` → `83fb9fc7…`, nhưng `:offcar-planner:test` báo **`UP-TO-DATE` / BUILD SUCCESSFUL, 0 đỏ**;
 *     ép chạy (`--rerun`) ⇒ **BUILD FAILED** đúng chỗ (`app/src/main/res/values/strings.xml ==> expected
 *     <45fa51a8…>`). Nghĩa là **mọi lần sửa layout/strings từ trước tới nay đều đi qua một cái niêm phong
 *     không hề mở ra xem**, trừ khi tình cờ có thứ khác làm task chạy lại.
 *  2. chèn token bị cấm `com.byd.clusternav.offcar` vào `app/src/main/res/values/strings.xml` (ca của
 *     `DependencyBoundaryTest`) ⇒ **UP-TO-DATE / BUILD SUCCESSFUL**; ép chạy ⇒ **BUILD FAILED**.
 *  3. sửa `settings.gradle.kts` thành `include( ":offcar-planner")` ⇒ **UP-TO-DATE / BUILD SUCCESSFUL**;
 *     ép chạy ⇒ **BUILD FAILED**.
 *
 * Vì phạm vi quét của bộ seal là "cả repo", đầu vào khai theo THƯ MỤC chứ không theo từng tệp: khai từng tệp
 * thì mỗi lần bộ seal ghim thêm một đường dẫn mới lại phải nhớ sửa chỗ này — mà "phải nhớ" chính là cơ chế
 * đã sinh ra lỗi này. Đổi bất kỳ tài liệu nào làm bộ 99 bài chạy lại (~3 giây) — cái giá đúng để đổi lấy việc
 * niêm phong thật sự có canh.
 */
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("clusternav.root", rootProject.projectDir.absolutePath)

    listOf(
        "app/src/main",
        "core/src/main",
        "car-integration/src/main",
        "vehicle-contracts/src/main",
        "offcar-planner/src/main",
        "docs",
        "scripts",
        // Cây TEST của các module khác: bộ seal đòi những đường dẫn này **tồn tại và là tệp thường**
        // (`ExpansionTraceabilityTest` với `artifactPaths`, nhánh "authorized T10 may be absent" của
        // `ExpansionTransportFenceTest`). Yếu hơn ghim-hash nên tôi KHÔNG dựng được phép đo ra ĐỎ cho
        // nhóm này (theo thiết kế nó tha cho tệp vắng mặt) — khai vì nó vẫn là tệp cây-nguồn bị đọc.
        "app/src/test",
        "app/src/testVehicleTest",
        "app/src/vehicleTest",
        "core/src/test",
        "car-integration/src/test",
        "vehicle-contracts/src/test",
        "gradle",
    ).forEach { relative ->
        inputs.dir(rootProject.layout.projectDirectory.dir(relative))
            .withPropertyName("sealScan-" + relative.replace('/', '-'))
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
    listOf(
        "settings.gradle.kts",
        "build.gradle.kts",
        "app/build.gradle.kts",
        "core/build.gradle.kts",
        "car-integration/build.gradle.kts",
        "offcar-planner/build.gradle.kts",
        "vehicle-contracts/build.gradle.kts",
        ".gitignore",
    ).forEach { relative ->
        inputs.file(rootProject.layout.projectDirectory.file(relative))
            .withPropertyName("sealScript-" + relative.replace('/', '-').removePrefix("."))
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}


tasks.register<JavaExec>("renderExpansionPack") {
    group = "verification"
    description = "Render the fixed canonical expansion pack with no arguments"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.byd.clusternav.offcar.ExpansionMain")
    workingDir = rootProject.projectDir
}