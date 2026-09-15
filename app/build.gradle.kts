import groovy.json.JsonSlurper
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.UUID

plugins {
    id("com.android.application")
    // AGP 9.3.1 built-in Kotlin — org.jetbrains.kotlin.android is no longer needed.
}

// Authorized candidate builds never overwrite historical app/build APK bytes.
val authorizedBuildSourceId = providers.gradleProperty("exactSourceId")
if (authorizedBuildSourceId.isPresent) {
    val value = authorizedBuildSourceId.get()
    if (value.matches(Regex("[0-9a-f]{64}"))) {
        layout.buildDirectory.set(rootProject.layout.projectDirectory.dir(".authorized-build/${value.take(12)}/app"))
    }
}

// Khoá ký release nạp từ keystore.properties (KHÔNG commit — maintainer giữ local cùng release.keystore).
// Người clone không có file này -> release/vehicleTest build sẽ FAIL (signingConfig = null → unsigned APK rejected by Android).
val keystorePropsFile = rootProject.file("keystore.properties")
val hasKeystore = keystorePropsFile.exists()
val keystoreProps = Properties().apply { if (hasKeystore) keystorePropsFile.inputStream().use { load(it) } }

android {
    namespace = "com.byd.clusternav"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.byd.launcher"
        minSdk = 29
        targetSdk = 37
        versionCode = 58
        versionName = "1.57"

        // ─── V1 pha NGHE · Vosk mang thư viện NATIVE, và APK chỉ chở ABI có thật trên xe ───────────────
        // [ĐO] 2026-09-14 `vosk-android-0.3.47.aar` (12,3 MB) chở `libvosk.so` cho BỐN ABI:
        //   arm64-v8a 8,86 MB · armeabi-v7a 8,28 MB · x86 9,67 MB · x86_64 9,68 MB = 36,5 MB.
        // Chở cả bốn thì APK 9,06 MB thành ~45 MB, mà kênh cập nhật là `apk/` trên GitHub, tải qua mạng 4G
        // của xe (`UpdateChecker`) — tức mỗi bản vá một dòng chữ cũng bắt người dùng tải thêm 19 MB x86 mà
        // KHÔNG đầu xe nào chạy được. [ĐO] dump xe trong `docs/diagnostics/` chỉ thấy thư viện `*_arm64-v8a`;
        // máy ảo đang dùng là `sdk_gphone64_arm64`. Giữ `armeabi-v7a` vì các đời DiLink cũ chưa được ĐO —
        // CLAUDE.md §7 cấm chốt theo một đời xe, và 8 MB rẻ hơn một dòng xe câm lặng không báo lỗi.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        // DIAG build flag — a DIAGNOSTIC log-collection build for a teammate to drive-test VietMap/Waze.
        // Default FALSE so the normal RELEASE build stays byte-identical (A8/D3: verbose logging default OFF —
        // normal use collects NO logs/PNGs/screenshots). Only `./gradlew :app:assembleRelease -PdiagLog=true`
        // (or assembleDebug) flips it to true, which pre-ONs verbose logging via Prefs.navVerboseLog's default
        // (see Prefs.K_NAV_VERBOSE_LOG) so the tester doesn't have to find the hidden toggle. The persisted
        // pref still overrides this default once the user flips the switch either way.
        buildConfigField("boolean", "DIAG_LOG", (project.findProperty("diagLog") == "true").toString())
    }

    buildFeatures {
        // BuildConfig.APPLICATION_ID is the single source of truth for this app's own package at runtime
        // (self-grants, self-component names, cast self-exclusion). Enables true app isolation from the
        // legacy com.byd.clusternav app while keeping the internal code namespace unchanged.
        buildConfig = true
    }

    // V2 pha NGHE · sherpa-onnx AAR chở 4 thư viện native/ABI: libsherpa-onnx-jni.so + libonnxruntime.so (đường
    // Kotlin JNI DÙNG) và libsherpa-onnx-c-api.so + libsherpa-onnx-cxx-api.so (cho consumer C/C++ — ta KHÔNG dùng).
    // [ĐO] loại c-api+cxx-api tiết kiệm ~8 MB (arm64 4,3+0,4 · armv7 3,1+0,3). Giữ jni + onnxruntime.
    packaging {
        jniLibs {
            excludes += setOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }

    signingConfigs {
        // Ký release bằng keystore.properties (gitignored). Người clone không có -> release build fails (intentional).
        if (hasKeystore) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile", "release.keystore"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false   // tắt minify: experiment dùng reflection HAL, tránh proguard phá
            isDebuggable = false
            // Release MUST use the real signing key. No debug fallback — fail loudly if keystore.properties is missing.
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else null
        }
        create("vehicleTest") {
            // Same as release but debuggable — for on-car diagnostics with `adb shell run-as`.
            initWith(getByName("release"))
            isDebuggable = true
            // Still requires release signing key (no debug fallback).
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else null
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    lint {
        // Release builds must pass lint — abort on error to prevent shipping broken APKs.
        checkReleaseBuilds = true
        abortOnError = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP built-in Kotlin: jvmTarget automatically follows targetCompatibility (17).
    // No kotlinOptions{} block needed.

    testOptions {
        // Chạy JUnit 5 off-device qua ./gradlew testDebugUnitTest.
        unitTests.all { it.useJUnitPlatform() }
    }
}

/**
 * ⚠⚠ MỌI TỆP CÂY-NGUỒN MÀ TEST ĐỌC PHẢI LÀ ĐẦU VÀO CỦA TASK TEST — không khai thì bài canh **không bao
 * giờ chạy lại** và cho dấu xanh GIẢ.
 *
 * [ĐO] 2026-09-11 (T2, thử phá mutation 2): sửa `android:strokeWidth` của `ic_lock.xml` từ 1.6 → 2.4 rồi chạy
 * `:app:testDebugUnitTest` ⇒ Gradle báo **`Task :app:testDebugUnitTest UP-TO-DATE`**, `BUILD SUCCESSFUL`, 0 đỏ —
 * trong khi `IconStyleContractTest` sinh ra chính để bắt ca đó. Lý do: bài đọc tệp TRỰC TIẾP từ cây nguồn (qua
 * `SourceRoots`), còn task test chỉ khai đầu vào là classpath + tài nguyên đã trộn; đổi NỘI DUNG một tệp vector
 * không đổi `R.java` nên không có gì trong chuỗi đầu vào của nó thay đổi. (Thêm tệp MỚI thì lại đỏ đúng — vì
 * `R.java` đổi. Chính sự bất đối xứng đó làm lỗi này rất dễ tưởng là đã an toàn.)
 *
 * [ĐO] 2026-09-12 (lượt truy quét cùng-họ) — cùng bệnh, đo trên tệp KHÁC, TRƯỚC khi vá:
 *  - `src/main/AndroidManifest.xml`: nhân đôi `<uses-permission RECEIVE_BOOT_COMPLETED>` (đúng ca
 *    `KachiAutostartServiceWiringTest` đếm `count() == 1`) ⇒ `:app:testDebugUnitTest` **UP-TO-DATE /
 *    BUILD SUCCESSFUL**; ép chạy (`--rerun`) ⇒ **BUILD FAILED**. Manifest KHÔNG nằm trong classpath
 *    unit-test (AGP mặc định `unitTests.isIncludeAndroidResources = false`) nên không có gì đổi.
 *
 * `src/main/java` khai TƯỜNG MINH dù đã có gián tiếp qua classpath: đường gián tiếp chỉ bắt được thay đổi
 * làm ĐỔI BYTECODE. Rất nhiều bài ở đây quét **văn bản gốc kể cả KDoc/chú thích** (`SourceRoots.text`), mà
 * sửa chú thích trong một dòng có sẵn thì bytecode y nguyên ⇒ không khai thì lại xanh giả.
 *
 * `core/`+`car-integration/` là cây nguồn của module KHÁC: `LayeringRulesTest` quét chúng, và `:app` KHÔNG
 * biên dịch chúng thành nguồn ⇒ đổi văn bản ở đó không đụng gì trong chuỗi đầu vào của task này.
 *
 * Đây là **cùng họ với lỗi S1** ("bài quét mã của module X phải nằm trong module X"): điều kiện để một bài canh
 * còn sống là Gradle BIẾT thứ nó quét. Đặt đúng module chỉ là một nửa; nửa còn lại là khai đầu vào.
 */
tasks.withType<Test>().configureEach {
    // ── cây nguồn CỦA CHÍNH :app mà test đọc trực tiếp ──────────────────────────────────────────
    inputs.dir(layout.projectDirectory.dir("src/main/res"))
        .withPropertyName("appResForSourceScanningTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("src/main/AndroidManifest.xml"))
        .withPropertyName("appManifestForSourceScanningTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/main/java"))
        .withPropertyName("appMainSourceTextForSourceScanningTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // `src/vehicleTest` = bề mặt probe mà `VehicleTestSurfaceContractTest` quét theo VĂN BẢN.
    inputs.dir(layout.projectDirectory.dir("src/vehicleTest"))
        .withPropertyName("appVehicleTestSourceForSurfaceContractTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // `src/release` là source set TÙY CHỌN (hiện KHÔNG tồn tại) — `MainProbeSurfaceAbsenceTest` quét nó
    // nếu có. Dùng `inputs.files` chứ KHÔNG `inputs.dir`: `inputs.dir` nổ khi thư mục chưa tồn tại, còn
    // `inputs.files` chấp nhận rỗng và vẫn đỏ đúng lúc ai đó TẠO thư mục đó kèm bề mặt probe.
    inputs.files(layout.projectDirectory.dir("src/release"))
        .withPropertyName("appReleaseSourceIfPresentForProbeAbsenceTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // `LayeringRulesTest` quét CHÍNH cây test (tên tệp + khai báo + văn bản) để bắt bài nằm sai module.
    // Cây test đã là đầu vào gián tiếp qua `compileTestKotlin`, nhưng lại chỉ bắt thay đổi đổi BYTECODE —
    // khai tường minh để sửa chú thích/đổi tên tệp cũng làm bài canh chạy lại.
    inputs.dir(layout.projectDirectory.dir("src/test/java"))
        .withPropertyName("appTestSourceTextForLayeringRulesTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // `BuildArtifactNamingTest` đọc chính tệp này.
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
        .withPropertyName("appBuildScriptForBuildArtifactNamingTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // ── cây nguồn MODULE KHÁC + tài liệu mà `LayeringRulesTest` / probe-contract quét ────────────
    inputs.dir(rootProject.layout.projectDirectory.dir("core/src/main/kotlin"))
        .withPropertyName("coreSourceTextForLayeringRulesTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("car-integration/src/main/kotlin"))
        .withPropertyName("carIntegrationSourceTextForLayeringAndProbeTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("docs/refactor-car-execution/layering-rules.md"))
        .withPropertyName("layeringRulesDocForLayeringRulesTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.layout.projectDirectory.dir("scripts/vehicle"))
        .withPropertyName("vehicleScriptsForVehicleTestSurfaceContractTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    // "CÁI KIA" — dadb: ADB client thuần JVM, tự nối localhost:5555 -> uid 2000 -> chạy navopen (HAL trực tiếp,
    // ETA + icon hoàn hảo như DashCast). KÉO okio + bouncycastle transitively.
    // ⚠️ Build LẦN ĐẦU ở cty PHẢI có internet (KHÔNG dùng --offline) để tải dep; sau khi cache xong build offline OK.
    // :core giữ quyết định, dữ liệu, metadata, config. :app chỉ được đi xuống, không có đường ngược lại.
    implementation(project(":core"))
    implementation(project(":car-integration"))
    testImplementation(testFixtures(project(":core")))
    implementation("dev.mobile:dadb:2.0.0")

    // — B5a launcher UI-state: coroutines + AndroidX Lifecycle ViewModel/runtime (StateFlow single-source-of-truth) —
    // Versions verified via Context7 + Maven Central (2026-09-10): kotlinx-coroutines 1.10.2 (latest stable band,
    // README master = 1.11), androidx.lifecycle 2.9.0 (ktx artifacts transitively provide viewModelScope /
    // repeatOnLifecycle / lifecycleScope / LifecycleRegistry), Turbine 1.2.1 (latest stable). Modern API only
    // (MutableStateFlow.update, asStateFlow, runTest, Dispatchers.setMain) — no deprecated patterns.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")

    // — U9 `CarFrames`: đọc CHUỖI path SVG của bộ icon v2 thành `android.graphics.Path` —
    // `android.util.PathParser` của nền tảng là @hide (không gọi được từ app, và đã từng đổi chữ ký giữa các đời
    // ROM) ⇒ đường duy nhất còn lại là bản AndroidX. Version 1.19.0 = latest stable (Google Maven group-index đọc
    // 2026-09-13); `androidx.core.graphics.PathParser.createPathFromPathData` là API CÔNG KHAI của gói này.
    implementation("androidx.core:core:1.19.0")

    // ─── V2 pha NGHE · NHẬN DẠNG TIẾNG NÓI TẠI MÁY (sherpa-onnx, thay Vosk) ───────────────────────────
    // Vì sao đổi: Vosk small-vn (32 MB, ngữ pháp FST cứng) trên xe thật NÓI CẢ CÂU RA 1 TỪ — gốc bệnh là
    // model quá nhỏ + giải mã ràng FST. V2 chuyển sang sherpa-onnx OfflineRecognizer + Zipformer-vi
    // (transducer, giải mã TỰ DO + contextual biasing từ nhãn control) — xem `SherpaModelManifest`,
    // `VoiceRecognizer` (đã viết lại), spec `docs/specs/kachi-voice-engine-v2.html`.
    //
    // Version kiểm 2026-09-14 (rule global §1.1): Context7 `/k2-fsa/sherpa-onnx` ⇒ **v1.13.8 latest stable**;
    // engine Apache-2.0. Chỉ phát hành qua JitPack (khai group ở `settings.gradle.kts`). AAR chở native cho
    // 4 ABI (libsherpa-onnx-jni.so + libonnxruntime.so); `abiFilters` ở trên đã lọc còn arm64-v8a + armeabi-v7a.
    // API HIỆN HÀNH (Context7 + java-api README): `OfflineRecognizer(OfflineRecognizerConfig)` ·
    // `OfflineTransducerModelConfig(encoder,decoder,joiner)` · `OfflineModelConfig(transducer,tokens,...)` ·
    // `createStream()` · `OfflineStream.acceptWaveform(float[], int sampleRate)` · `decode(stream)` ·
    // `getResult(stream).getText()`. KHÔNG dùng lớp mic/VAD tự-dựng-AudioRecord của gói — Kachi giữ
    // `AudioRecord` cho mình (`VoiceCapture`) để đường ghi âm nằm trong tầm bài canh "không gửi audio ra mạng".
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8")

    // — JVM unit + property tests (off-device, chạy bằng ./gradlew testDebugUnitTest) —
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // B5a: test StateFlow của HomeViewModel — coroutines-test (runTest/Dispatchers.setMain) + Turbine (awaitItem).
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    // Real org.json on the unit-test classpath: android.jar ships a stub that throws "Stub!",
    // so WazeMod HLP/1 parse tests need the reference implementation. Pinned. Test-only
    // (no prod classpath impact — production uses the platform org.json on-device).
    testImplementation("org.json:json:20260719")
}

// ─── EXACT-SOURCE IDENTITY (producer-matching derivation) ───────────────────────
// Identity = SHA-256 over canonical JSON of the manifest object with its own `sourceId`
// member removed: recursively lexicographic object keys, original array order, compact
// separators, UTF-8, no BOM. The whole-file byte hash is NOT the identity — the file
// embeds `sourceId`, so byte hashing can never bind a manifest to its own identity.
// The manifest is named per invocation; no superseded path may be baked in here.
object ExactSourceIdentity {
    private val MANIFEST_PATH = Regex("docs/_handoff/[A-Za-z0-9._-]{1,120}\\.json")

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun resolveManifest(root: File, declaredPath: String?): File {
        val path = declaredPath
            ?: throw org.gradle.api.GradleException(
                "Missing -PexactSourceManifest=docs/_handoff/<manifest>.json"
            )
        if (!MANIFEST_PATH.matches(path)) {
            throw org.gradle.api.GradleException("Invalid exactSourceManifest path: $path")
        }
        val file = File(root, path)
        if (!file.isFile) {
            throw org.gradle.api.GradleException("Exact-source manifest is missing: $path")
        }
        return file
    }

    /** Returns canonical identity plus the identity the manifest declares for itself. */
    fun compute(manifest: File): Pair<String, String?> {
        val parsed = JsonSlurper().parse(manifest.readBytes(), "UTF-8")
        if (parsed !is Map<*, *>) {
            throw org.gradle.api.GradleException("Exact-source manifest root must be a JSON object")
        }
        val declared = parsed["sourceId"] as? String
        val withoutIdentity = parsed.filterKeys { it != "sourceId" }
        return sha256(canonical(withoutIdentity).toByteArray(Charsets.UTF_8)) to declared
    }

    fun verify(root: File, declaredPath: String?, requestedId: String): File {
        val manifest = resolveManifest(root, declaredPath)
        val (computed, declared) = compute(manifest)
        if (declared == null) {
            throw org.gradle.api.GradleException("Exact-source manifest declares no sourceId member")
        }
        if (declared != computed) {
            throw org.gradle.api.GradleException(
                "Exact-source manifest is not self-consistent: declared=$declared computed=$computed"
            )
        }
        if (requestedId != computed) {
            val legacy = sha256(manifest.readBytes())
            val hint = if (requestedId == legacy) {
                " — that value is the manifest whole-file byte hash, not its canonical identity"
            } else {
                ""
            }
            throw org.gradle.api.GradleException(
                "exactSourceId $requestedId does not match ${manifest.name} identity $computed$hint"
            )
        }
        return manifest
    }

    /**
     * Fails when the working tree no longer matches the attested build inputs.
     *
     * Only `intendedUntracked` is checked: those are the actual build inputs. The exclusion
     * inventory deliberately covers local tooling and runtime state that may legitimately change
     * (for example editor/LSP settings), so hashing it here would fail builds for the wrong reason.
     *
     * Without this check the gate only proves the manifest is internally consistent, so a build
     * could be produced from a tree the attestation does not describe and the resulting candidate
     * would carry a misleading identity into vehicle evidence.
     */
    fun verifyTree(root: File, manifest: File): Int {
        val parsed = JsonSlurper().parse(manifest.readBytes(), "UTF-8") as? Map<*, *>
            ?: throw org.gradle.api.GradleException("Exact-source manifest root must be a JSON object")
        val inputs = parsed["intendedUntracked"] as? Iterable<*>
            ?: throw org.gradle.api.GradleException("Exact-source manifest declares no intendedUntracked inputs")
        val drift = mutableListOf<String>()
        var checked = 0
        for (entry in inputs) {
            val row = entry as? Map<*, *> ?: continue
            val path = row["path"] as? String ?: continue
            val expected = row["sha256"] as? String ?: continue
            val file = File(root, path)
            if (!file.isFile) {
                drift += "$path (missing)"
                continue
            }
            checked++
            val bytes = file.readBytes()
            val actual = sha256(bytes)
            val declaredLength = (row["byteLength"] as? Number)?.toInt()
            if (actual != expected) {
                drift += "$path (attested ${expected.take(12)}, actual ${actual.take(12)})"
            } else if (declaredLength != null && declaredLength != bytes.size) {
                drift += "$path (attested $declaredLength bytes, actual ${bytes.size})"
            }
        }
        if (drift.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Working tree does not match ${manifest.name}: ${drift.size} attested input(s) drifted. " +
                    "Regenerate the attestation with scripts/evidence/gen-exact-source.py and use the new " +
                    "identity. First drifted: " + drift.take(5).joinToString("; ")
            )
        }
        verifyTrackedState(root, manifest.name, parsed)
        return checked
    }

    /**
     * The untracked inventory above covers the product sources, but build inputs that ARE tracked
     * (this script, gradle.properties, the wrapper) live in the recorded diff instead. Without this
     * the gate could be edited and the mismatch would go unnoticed.
     */
    private fun verifyTrackedState(root: File, manifestName: String, parsed: Map<*, *>) {
        val tracked = parsed["trackedDiff"] as? Map<*, *>
            ?: throw org.gradle.api.GradleException("$manifestName declares no trackedDiff")
        val expectedHash = tracked["sha256"] as? String
            ?: throw org.gradle.api.GradleException("$manifestName trackedDiff has no sha256")
        val expectedLength = (tracked["byteLength"] as? Number)?.toInt()

        val head = String(git(root, "rev-parse", "HEAD"), Charsets.UTF_8).trim()
        val declaredHead = parsed["head"] as? String
        if (declaredHead != null && declaredHead != head) {
            throw org.gradle.api.GradleException(
                "HEAD does not match $manifestName: attested $declaredHead, actual $head"
            )
        }
        val diff = git(root, "diff", "--binary", "--no-ext-diff", "HEAD")
        val actualHash = sha256(diff)
        if (actualHash != expectedHash || (expectedLength != null && expectedLength != diff.size)) {
            throw org.gradle.api.GradleException(
                "Working tree does not match $manifestName: tracked diff drifted " +
                    "(attested ${expectedHash.take(12)}/${expectedLength ?: "?"} bytes, " +
                    "actual ${actualHash.take(12)}/${diff.size} bytes). Regenerate the attestation."
            )
        }
    }

    private fun git(root: File, vararg args: String): ByteArray {
        val builder = ProcessBuilder(listOf("git", *args))
            .directory(root)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        // Matches the producer recorded in the manifest, so the hash is comparable.
        builder.environment()["GIT_OPTIONAL_LOCKS"] = "0"
        val process = try {
            builder.start()
        } catch (error: java.io.IOException) {
            throw org.gradle.api.GradleException(
                "git is required to verify the attested tracked diff: ${error.message}"
            )
        }
        // Drain before waiting: the diff exceeds the pipe buffer and would otherwise deadlock.
        val output = process.inputStream.use { it.readBytes() }
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw org.gradle.api.GradleException("git ${args.joinToString(" ")} timed out")
        }
        if (process.exitValue() != 0) {
            throw org.gradle.api.GradleException(
                "git ${args.joinToString(" ")} failed with exit ${process.exitValue()}"
            )
        }
        return output
    }

    fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is String -> quote(value)
        is Int, is Long, is BigInteger -> value.toString()
        is BigDecimal -> value.stripTrailingZeros().let {
            if (it.scale() <= 0) it.toBigIntegerExact().toString() else it.toPlainString()
        }
        is Double, is Float -> throw org.gradle.api.GradleException(
            "Binary floating point is not canonicalizable: $value"
        )
        is Map<*, *> -> value.entries
            .map { entry ->
                val key = entry.key as? String
                    ?: throw org.gradle.api.GradleException("Canonical JSON requires string object keys")
                // Kotlin sorts by UTF-16 code unit and Python by code point; they agree only for
                // ASCII. Rejecting anything else removes a silent producer/consumer divergence.
                if (key.any { it.code > 0x7F }) {
                    throw org.gradle.api.GradleException("Canonical JSON requires ASCII object keys: $key")
                }
                key to entry.value
            }
            .sortedBy { it.first }
            .joinToString(",", "{", "}") { quote(it.first) + ":" + canonical(it.second) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { canonical(it) }
        else -> throw org.gradle.api.GradleException(
            "Unsupported JSON node type: ${value::class.java.name}"
        )
    }

    private fun quote(text: String): String {
        val out = StringBuilder(text.length + 2)
        out.append('"')
        for (ch in text) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch < ' ') out.append("\\u%04x".format(ch.code)) else out.append(ch)
            }
        }
        out.append('"')
        return out.toString()
    }
}

// Report-only identity gate: proves the requested id binds to the named manifest before
// any authorized build is invoked. Runs no assemble and writes no artifact.
abstract class VerifyExactSourceIdentity : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    @get:org.gradle.api.tasks.Optional
    abstract val exactSourceId: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Input
    @get:org.gradle.api.tasks.Optional
    abstract val exactSourceManifest: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Internal
    abstract val repositoryRoot: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val root = repositoryRoot.get().asFile
        val requested = exactSourceId.orNull
            ?: throw org.gradle.api.GradleException("Missing -PexactSourceId=<64 lowercase hex>")
        if (!requested.matches(Regex("[0-9a-f]{64}"))) {
            throw org.gradle.api.GradleException("exactSourceId must be 64 lowercase hexadecimal characters")
        }
        val manifest = ExactSourceIdentity.verify(root, exactSourceManifest.orNull, requested)
        val checked = ExactSourceIdentity.verifyTree(root, manifest)
        logger.lifecycle("Exact-source identity verified: ${manifest.name} -> $requested")
        logger.lifecycle("Attested inputs matching the working tree: $checked")
    }
}

tasks.register<VerifyExactSourceIdentity>("verifyExactSourceIdentity") {
    group = "verification"
    description = "Verify -PexactSourceId against the canonical identity of -PexactSourceManifest"
    exactSourceId.set(providers.gradleProperty("exactSourceId"))
    exactSourceManifest.set(providers.gradleProperty("exactSourceManifest"))
    repositoryRoot.set(rootProject.layout.projectDirectory)
    outputs.upToDateWhen { false }
}

// ─── AUTHORIZED APK COLLECTION (NO AUTOMATIC FINALIZER) ─────────────────────────
// Historical APK bytes under apk/ are immutable. Collection is explicit, release-only, exact-source
// named, freshness checked, and collision failing; implementation authorization alone never invokes it.
// Stage 2 supersedes the historical automatic-copy behavior above. This task is intentionally
// NOT a finalizer and must be invoked explicitly by a later BUILD_AUTH.
abstract class CollectAuthorizedApk : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val requestedVariant: org.gradle.api.provider.Property<String>
    @get:org.gradle.api.tasks.Input
    abstract val slice: org.gradle.api.provider.Property<String>
    @get:org.gradle.api.tasks.Input
    abstract val exactSourceId: org.gradle.api.provider.Property<String>
    @get:org.gradle.api.tasks.Input
    abstract val exactSourceManifest: org.gradle.api.provider.Property<String>
    @get:org.gradle.api.tasks.Input
    abstract val artifactVersion: org.gradle.api.provider.Property<String>
    @get:org.gradle.api.tasks.Input
    abstract val artifactVersionCode: org.gradle.api.provider.Property<Int>
    @get:org.gradle.api.tasks.Input
    abstract val invocationStartedAtMillis: org.gradle.api.provider.Property<Long>
    @get:org.gradle.api.tasks.InputDirectory
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val sourceDirectory: org.gradle.api.file.DirectoryProperty
    @get:org.gradle.api.tasks.Internal
    abstract val destinationDirectory: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun collect() {
        val variant = requestedVariant.orNull
            ?: throw org.gradle.api.GradleException("Missing -PclusterNavVariant=release")
        if (variant != "release") {
            throw org.gradle.api.GradleException("Only the requested release variant may be collected")
        }
        val safeSlice = slice.orNull
            ?: throw org.gradle.api.GradleException("Missing -PclusterNavSlice=<slice>")
        if (!safeSlice.matches(Regex("[a-z0-9][a-z0-9-]{0,31}"))) {
            throw org.gradle.api.GradleException("Invalid clusterNavSlice: $safeSlice")
        }
        val sourceId = exactSourceId.orNull
            ?: throw org.gradle.api.GradleException("Missing -PexactSourceId=<64 lowercase hex>")
        if (!sourceId.matches(Regex("[0-9a-f]{64}"))) {
            throw org.gradle.api.GradleException("exactSourceId must be 64 lowercase hexadecimal characters")
        }
        val manifest = ExactSourceIdentity.verify(
            project.rootProject.projectDir,
            exactSourceManifest.orNull,
            sourceId,
        )
        val attested = ExactSourceIdentity.verifyTree(project.rootProject.projectDir, manifest)
        logger.lifecycle("Bound authorized build to ${manifest.name} identity $sourceId ($attested attested inputs)")
        val candidates = sourceDirectory.get().asFile.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "apk" }
        if (candidates.size != 1) {
            throw org.gradle.api.GradleException("Expected exactly one requested release APK, found ${candidates.size}")
        }
        val source = candidates.single()
        if (source.lastModified() < invocationStartedAtMillis.get()) {
            throw org.gradle.api.GradleException("Requested release APK is stale; rerun authorized assemble in this invocation")
        }
        val destinationDir = destinationDirectory.get().asFile
        destinationDir.mkdirs()
        val destination = destinationDir.resolve(
            "ClusterNav-${artifactVersion.get()}-$safeSlice-${sourceId.take(12)}-release.apk"
        )
        if (destination.exists()) {
            throw org.gradle.api.GradleException("Refusing to overwrite existing artifact: ${destination.absolutePath}")
        }
        val temporary = destinationDir.resolve(
            ".${destination.name}.${UUID.randomUUID()}.tmp"
        ).toPath()
        try {
            Files.copy(source.toPath(), temporary)
            try {
                Files.move(
                    temporary,
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination.toPath())
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        val collectedSha = ExactSourceIdentity.sha256(destination.readBytes())

        // ─── APK content verification ────────────────────────────────────────────────
        // Use aapt2 to verify the APK's embedded metadata matches the build config.
        verifyApkContent(destination, variant)

        val repositoryRoot = project.rootProject.projectDir
        val relativeApk = destination.relativeTo(repositoryRoot).path.replace(File.separatorChar, '/')
        val candidateManifest = File(repositoryRoot, "docs/_handoff/vehicle-candidate.json")
        candidateManifest.parentFile.mkdirs()
        // Single source of truth for the on-car scripts: they read this file instead of
        // carrying a baked hash, so a new candidate can never be shadowed by a stale one.
        candidateManifest.writeText(
            ExactSourceIdentity.canonical(
                mapOf(
                    "apk" to relativeApk,
                    "builtAtUtc" to Instant.ofEpochMilli(destination.lastModified())
                        .atOffset(ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.SECONDS)
                        .toString(),
                    "exactSourceId" to sourceId,
                    "exactSourceManifest" to
                        manifest.relativeTo(repositoryRoot).path.replace(File.separatorChar, '/'),
                    "schema" to "clusternav.vehicle-candidate/v1",
                    "sha256" to collectedSha,
                    "slice" to safeSlice,
                    "versionCode" to artifactVersionCode.get(),
                    "versionName" to artifactVersion.get(),
                )
            ) + "\n",
            Charsets.UTF_8,
        )
        logger.lifecycle("Collected authorized APK: ${destination.absolutePath}")
        logger.lifecycle("Candidate SHA-256: $collectedSha")
        logger.lifecycle("Recorded candidate manifest: ${candidateManifest.absolutePath}")
    }

    /**
     * Verify the APK's embedded metadata matches expectations for the given variant.
     * Uses `aapt2 dump badging` to extract package name, versionCode, and debuggable flag.
     */
    private fun verifyApkContent(apk: File, variant: String) {
        val androidHome = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: throw org.gradle.api.GradleException(
                "ANDROID_HOME or ANDROID_SDK_ROOT must be set for APK verification"
            )
        // Find aapt2 — prefer build-tools matching compileSdk, fall back to any available.
        val buildToolsDir = File(androidHome, "build-tools")
        val aapt2 = buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedDescending()
            ?.map { File(it, "aapt2") }
            ?.firstOrNull { it.canExecute() }
            ?: throw org.gradle.api.GradleException(
                "Cannot find aapt2 in $buildToolsDir — install Android build-tools"
            )

        val process = ProcessBuilder(listOf(aapt2.absolutePath, "dump", "badging", apk.absolutePath))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw org.gradle.api.GradleException("aapt2 dump badging timed out")
        }
        if (process.exitValue() != 0) {
            throw org.gradle.api.GradleException("aapt2 dump badging failed: $output")
        }

        // Verify package name
        val pkgMatch = Regex("package: name='([^']+)'").find(output)
            ?: throw org.gradle.api.GradleException("APK verification: cannot extract package name from aapt2 output")
        val actualPkg = pkgMatch.groupValues[1]
        val expectedPkg = "com.byd.clusternav"
        if (actualPkg != expectedPkg) {
            throw org.gradle.api.GradleException(
                "APK verification FAILED: package='$actualPkg', expected='$expectedPkg'"
            )
        }

        // Verify versionCode
        val vcMatch = Regex("versionCode='(\\d+)'").find(output)
        val actualVc = vcMatch?.groupValues?.get(1)?.toIntOrNull()
        val expectedVc = artifactVersionCode.get()
        if (actualVc != expectedVc) {
            throw org.gradle.api.GradleException(
                "APK verification FAILED: versionCode=$actualVc, expected=$expectedVc"
            )
        }

        // Verify debuggable flag matches variant expectations
        val isDebuggable = output.contains("application-debuggable")
        val expectDebuggable = variant != "release"  // vehicleTest=debuggable, release=not
        if (isDebuggable != expectDebuggable) {
            throw org.gradle.api.GradleException(
                "APK verification FAILED: debuggable=$isDebuggable, expected=$expectDebuggable for variant '$variant'"
            )
        }

        logger.lifecycle("APK verification PASSED: pkg=$actualPkg versionCode=$actualVc debuggable=$isDebuggable")
    }
}

val collectionInvocationStartedAt = System.currentTimeMillis()
tasks.register<CollectAuthorizedApk>("collectAuthorizedApk") {
    group = "build"
    description = "Collect one fresh, authorized release APK without overwriting historical artifacts"
    requestedVariant.set(providers.gradleProperty("clusterNavVariant"))
    slice.set(providers.gradleProperty("clusterNavSlice"))
    exactSourceId.set(providers.gradleProperty("exactSourceId"))
    exactSourceManifest.set(providers.gradleProperty("exactSourceManifest"))
    artifactVersion.set(android.defaultConfig.versionName ?: "0")
    artifactVersionCode.set(android.defaultConfig.versionCode ?: 0)
    invocationStartedAtMillis.set(collectionInvocationStartedAt)
    sourceDirectory.set(layout.buildDirectory.dir("outputs/apk/release"))
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("apk"))
    dependsOn("verifyExactSourceIdentity")
    dependsOn("assembleRelease")
    outputs.upToDateWhen { false }
}

// Fail on a wrong identity before a full release assemble is spent. `matching` is lazy, so this
// applies whenever AGP creates the task and both are in the same graph.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    mustRunAfter("verifyExactSourceIdentity")
}
