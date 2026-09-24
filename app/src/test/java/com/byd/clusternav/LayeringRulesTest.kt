package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cưỡng chế các quy tắc xếp chỗ trong docs/refactor-car-execution/layering-rules.md.
 *
 * Có test này vì trong một ngày tôi xếp sai chuồng bốn lần và mỗi lần chỉ phát hiện khi tình cờ đo lại.
 * Quy tắc viết trong tài liệu mà không ai cưỡng chế thì chỉ là ý định.
 */
class LayeringRulesTest {

    /**
     * ⚠ Trả null khi KHÔNG tìm thấy gốc — mọi chỗ gọi PHẢI `?: error(...)`, tuyệt đối không `?: return`.
     *
     * [ĐO] 2026-09-12: bản trước dùng `?: return` ở **9 chỗ**, tức 7 bài canh trong tệp này **tự tắt và báo
     * XANH** nếu thư mục cần quét không giải ra được (đổi layout thư mục, đổi working dir của task test,
     * dời module). Một bài canh im lặng bỏ qua chính đối tượng nó canh thì tệ hơn không có bài nào — vì nó
     * còn phát ra dấu xanh. `error(...)` biến ca đó thành đỏ nói rõ lý do.
     */
    private fun root(vararg candidates: String): Path? =
        candidates.map(Paths::get).firstOrNull(Files::exists)

    private fun kotlinFiles(root: Path): List<Path> =
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
        }

    private val androidOrDadb = Regex("""(?m)^import (android|androidx|dadb)\.""")
    private val androidOnly = Regex("""(?m)^import android""")

    /**
     * Test của logic `:core` phải nằm trong `:core`.
     *
     * Checklist thủ công 2026-07-27 bắt được 8 file test nằm sai chuồng, trong đó hai file do chính tôi
     * viết cùng tối. Hại thật sự: bộ test riêng của `:core` không phủ chính lớp của nó, nên có thể phá
     * `:core` mà chỉ biết khi chạy bộ test Android — và tệ hơn, một test như thế có thể lỡ phụ thuộc
     * Android mà không ai thấy, làm mất luôn ý nghĩa "core chạy được không cần thiết bị".
     *
     * Tiêu chí: test trong `:app` mà KHÔNG import Android và KHÔNG dùng khai báo nào của riêng `:app` thì
     * nó đang kiểm `:core`. Ngoại lệ phải kể tên và nói lý do, không được để danh sách trống mọc dần.
     */
    @Test
    fun `test cua logic core khong duoc nam trong app`() {
        val appTests = root("app/src/test/java", "../app/src/test/java") ?: error("khong tim thay cay nguon can quet — bai canh dang tu tat")
        val appMain = root("app/src/main/java", "../app/src/main/java") ?: error("khong tim thay cay nguon can quet — bai canh dang tu tat")
        val coreMain = root("core/src/main/kotlin", "../core/src/main/kotlin") ?: error("khong tim thay cay nguon can quet — bai canh dang tu tat")

        val declaration = Regex(
            """(?m)^(?:internal |private )?(?:data |sealed |enum |abstract )*(?:class|object|interface|fun|val) ([A-Za-z0-9_]+)""",
        )
        fun declarations(root: Path) = kotlinFiles(root)
            .flatMap { declaration.findAll(it.toFile().readText()).map { match -> match.groupValues[1] } }
            .toSet()

        val appOnly = declarations(appMain) - declarations(coreMain)

        // Ngoại lệ: đối tượng kiểm của nó là build script CỦA `:app`, nên nó thuộc `:app` dù không chạm
        // Android hay lớp Kotlin nào.
        val allowed = setOf("BuildArtifactNamingTest.kt")

        val misplaced = kotlinFiles(appTests)
            .filter { it.fileName.toString() !in allowed }
            .filter { file ->
                val text = file.toFile().readText()
                val touchesAndroid = androidOrDadb.containsMatchIn(text) || text.contains("Robolectric")
                val touchesAppOnly = appOnly.any { name -> Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(text) }
                !touchesAndroid && !touchesAppOnly
            }
            .map { it.fileName.toString() }

        assertTrue(
            misplaced.isEmpty(),
            "test chỉ dùng logic :core mà nằm trong :app — dời sang core/src/test: $misplaced",
        )
    }

    @Test
    fun `core khong duoc biet Android hay dadb`() {
        val root = root("core/src/main/kotlin", "../core/src/main/kotlin") ?: error("khong tim thay cay nguon can quet — bai canh dang tu tat")
        val offenders = kotlinFiles(root).filter { androidOrDadb.containsMatchIn(it.toFile().readText()) }
        assertEquals(emptyList<Path>(), offenders, "quy tắc Q1: :core là JVM thuần")
    }

    @Test
    fun `car-integration khong duoc biet Android`() {
        // Nó nói với head unit qua adb; API Android cục bộ là việc của :app. Nếu Android lọt vào đây thì
        // CLI runner không chạy được nữa — tức mất đúng lý do module này tồn tại.
        val root = root("car-integration/src/main/kotlin", "../car-integration/src/main/kotlin") ?: error("khong tim thay cay nguon can quet — bai canh dang tu tat")
        val offenders = kotlinFiles(root).filter { androidOnly.containsMatchIn(it.toFile().readText()) }
        assertEquals(emptyList<Path>(), offenders, "quy tắc Q1: transport phải chạy trên JVM thuần")
    }

    /**
     * Số file trong `:app` không dùng Android/dadb gì cả — tức đang nằm sai chuồng theo Q1.
     *
     * Chỉ được phép **giảm**. Khởi điểm 27/7: 7 file / 995 LOC. Sau khi review B1–B5 theo checklist, bốn
     * file đã về đúng chuồng: `WmParse`, `StackParse`, `DisplayParse` (comment của chính chúng ghi "PURE
     * (không đụng Android)" — parser thuộc `:core` theo bảng xếp chỗ) và `AppScale` (dữ liệu cấu hình).
     *
     * Sau khi gỡ cast v2 (2026-08-17) không còn file thuần nào ở `:app`: `CastActivityRefresh` và
     * `CastOperationStatus` — hai file thuần cuối cùng — đã bị xoá cùng stack v2 (cả hai import gói v2
     * cũ). Parser thuần (`WmParse`/`StackParse`/`DisplayParse`) và dữ liệu (`AppScale`) đã về `:core`
     * từ trước.
     *
     * `LocalProcessShellGateway` đã bị xoá (dead code, 2026-08-02).
     * `ModuleRegistry` đã bị xoá hoặc di chuyển trước đó.
     */
    private val pureFilesStillInApp = 0

    /**
     * File **thuần theo phép đo của bài này** nhưng PHẢI ở lại `:app`, kèm lý do (lệ `SettingsCatalog.NOT_SETTINGS`).
     *
     * ⚠ Vì sao là danh sách-kèm-lý-do chứ không phải nâng [pureFilesStillInApp] lên 2: con số đó là một **cái chốt
     * chỉ-được-giảm**, và giá trị của nó nằm ở chỗ *chạm vào là phải giải thích*. Nâng con số lên thì lần sau ai đặt
     * một parser thuần vào `:app` cũng chỉ cần nâng thêm một — chốt mất nghĩa. Danh sách tên thì vẫn đỏ với **file
     * mới**, đúng việc bài này sinh ra để làm.
     */
    private val pureButMustStayInApp: Map<String, String> = mapOf(
        // T1: luật của dự án là "sắc thái ở :core, MÃ MÀU ở :app" (bài học ChipTone — bản nháp RW0 viết #37d67a ở
        // :core trong khi bảng là #34d399 ⇒ hai bảng màu lệch nhau ngay dòng đầu). Đây LÀ bảng màu, nên nó thuần về
        // kỹ thuật nhưng thuộc `:app` về layering. `GroupTileWiringContractTest` canh chiều còn lại (:core = 0 hex).
        "KachiPalette.kt" to "là bảng MÃ MÀU — :core bị cấm giữ hex (luật ChipTone)",
        // Interface glue cho engine wake: hai thi hành (VoiceWakeKws/VoiceWakeAsr) đều dùng native sherpa +
        // Android, sống ở :app. Interface thuần theo phép đo nhưng thuộc :app về layering (cùng lẽ ChipTone).
        "WakeEngine.kt" to "interface hoán đổi engine wake — thi hành dùng native/Android, thuộc :app",
        // Glue DI của AppContainer: bọc CarControlAdapter + CarDataAdapter để sau write đánh thức read-cache
        // (owner 2026-09-24 "action phải chuyển ngay"). Nối HAI adapter cụ thể của :app ⇒ thuộc :app, dù "thuần"
        // theo phép đo (không import android.*). :core không có hai adapter đó để nối.
        "WakeOnWriteControl.kt" to "glue DI nối control-write ↔ read-cache của AppContainer — thuộc :app",
        // Bridge extension W-WAKE: gọi VoiceWakeService (FGS Android) + Prefs(Context) qua lời gọi; "thuần" chỉ
        // vì phép đo soi `import android.*` + vài tên lớp, không soi phụ thuộc bắc cầu. Thực thuộc :app.
        "ClusterNavBridgeWake.kt" to "cầu Settings gọi VoiceWakeService/Prefs — không chuyển được sang :core",
        // 1.85 — cùng ca `ClusterNavBridgeWake.kt`: hàm mở rộng của một lớp `:app` (`ClusterNavBridge`), gọi
        // `AutomationService` (FGS Android) + `Prefs(Context)` + `ScheduledNavApplier` qua lời gọi. Nó "thuần" chỉ
        // vì phép đo soi `import android.*` + vài tên lớp, không soi phụ thuộc bắc cầu. `:core` không thể chứa nó
        // (không có lớp `ClusterNavBridge` ở đó để mở rộng).
        "ClusterNavBridgeAutomation.kt" to
            "cầu Settings gọi AutomationService/Prefs/ScheduledNavApplier — không chuyển được sang :core",
        // Gọi `KachiTheme.applyTheme`, mà `KachiTheme` import android.graphics.Color ⇒ KHÔNG chuyển được sang :core.
        // Nó "thuần" chỉ vì phép đo soi `import android` + vài tên lớp Android, không soi phụ thuộc bắc cầu.
        "ThemeHost.kt" to "phụ thuộc KachiTheme (Android) qua lời gọi, không chuyển được sang :core",
        // V1 pha NGHE: cửa DUY NHẤT mở kết nối HTTPS (cập nhật APK + tải mô hình nhận dạng). Nó "thuần" theo phép
        // đo ở đây vì `java.net` là JVM chứ không phải `android.*` — nhưng nó **làm I/O ra mạng thật**, mà `:core`
        // là tầng quyết định phải kiểm được off-car **không chạm mạng**. Đẩy nó xuống `:core` là mở đường cho một
        // bài kiểm `:core` nào đó lặng lẽ gọi ra Internet, và một bộ test off-car đi hỏi mạng thì nó không còn là
        // off-car nữa. (Đối chiếu: `VoskWordList` ở `:core` chỉ **phân tích** một `InputStream` do chỗ gọi đưa
        // vào — không tự mở gì, nên nó thuộc về bên kia ranh giới.)
        "HttpConn.kt" to "làm I/O ra mạng thật — :core phải kiểm được off-car mà không chạm mạng",
        // S5: hàm mở rộng của `ClusterNavBridge` (Android/Context-bound) cho "màn hình chính". "Thuần" theo phép đo
        // ở đây chỉ vì nó không `import android.*` trực tiếp và không nhắc chữ Context — nhưng nó gọi `AdbKeys`,
        // `LocalDeviceShell`, `WorkspacePrefs`, `DefaultHome` (đều thuộc :app/:car-integration) và mở rộng một lớp
        // giữ Context. Không chuyển được sang :core — cùng lẽ với `ThemeHost.kt`.
        "ClusterNavBridgeHome.kt" to "hàm mở rộng ClusterNavBridge (Context-bound) — gọi AdbKeys/LocalDeviceShell/WorkspacePrefs",
        // Voice pha 2 (1.65 · 2026-09-16): nửa của `VoiceDispatcher` tách ra vì trần 500 dòng. Nó "thuần" theo phép đo ở đây
        // (không `import android.*`, không nhắc chữ Context) nhưng nó **là** cầu sang các đường Android của
        // `:app`: `VoiceAppIntents.send` bắn `startActivity`, `MediaTransport` là `MediaBridge` (MediaSession),
        // `HomeUiState` là state của màn chính. Chuyển nó sang `:core` là kéo cả ba thứ đó theo — cùng lẽ với
        // `ThemeHost.kt`/`ClusterNavBridgeHome.kt`. ⚠ `VoiceDispatcher.kt` không có ở đây vì nó nhắc chữ
        // `Context` (KDoc) nên phép đo không coi nó là thuần — một chi tiết của bộ quét, không phải một luật.
        "VoiceTargetDispatch.kt" to "nửa tách ra của VoiceDispatcher — cầu sang VoiceAppIntents/MediaBridge/HomeUiState của :app",
        // Lượt E (2026-09-19): vai *"ghi xong thì đọc lại xe rồi mới nói"* tách khỏi `VoiceDispatcher` vì trần 500
        // dòng. \"Thuần\" theo phép đo ở đây (không `import android.*`, không nhắc chữ Context) nhưng nó **đọc và
        // SỬA** `ControlTileState` — bảng trạng thái ô dùng chung của màn chính, sống ở `:app`. Chuyển sang `:core`
        // là kéo bảng ấy theo, mà bảng ấy là state của tầng vẽ. Cùng lẽ với `VoiceTargetDispatch.kt` ngay trên.
        "VoiceReadback.kt" to "vai đọc-lại tách khỏi VoiceDispatcher — đọc/sửa ControlTileState (state tầng vẽ :app)",
        // [SOÁT Pass 1 · 2026-09-16] Ba hàm ngôn ngữ tách khỏi `WorkspacePrefs.kt` vì trần 500 dòng. "Thuần" theo
        // phép đo ở đây chỉ vì nó không `import android.*` và không nhắc chữ `Context` — nhưng nó là **hàm mở rộng
        // của `WorkspacePrefs`** (giữ `SharedPreferences` + `Context`) và gọi `ClusterNavLang` (prefs của ClusterNav).
        // Chuyển sang `:core` là kéo cả hai thứ đó theo — cùng lẽ `ThemeHost.kt`/`ClusterNavBridgeHome.kt`.
        "WorkspacePrefsLang.kt" to "hàm mở rộng WorkspacePrefs (SharedPreferences/Context-bound) — gọi ClusterNavLang",
        // VISUAL-REFRESH P1b · R8: phép SUY bảng màu theo lựa chọn người dùng — hàm mở rộng của `KachiPalette` (bảng
        // MÃ MÀU, đã ở danh sách này), đọc hạt giống hex của nó. Phép TÍNH thì đã ở :core (`ColorMath` ·
        // `ContrastGuard`); thứ ở lại :app là đúng phần chạm vào hex — cùng lẽ với chính `KachiPalette.kt`.
        "KachiPaletteDerive.kt" to "hàm mở rộng KachiPalette (bảng hex) — phép tính đã ở :core (ColorMath/ContrastGuard)",
        // VISUAL-REFRESH P3: phần CHỌN ĐƯỢC của bảng màu (hạt giống nhấn · tông · màu sơn) tách khỏi `KachiPalette.kt`
        // vì trần 500 dòng — cùng lẽ với chính `KachiPalette.kt`: nó LÀ hex, và hex chỉ được sống ở :app.
        "KachiPaletteSeeds.kt" to "nửa tách ra của KachiPalette (bảng hex) — hạt giống màu nhấn · tông thẻ",
        // UX-OVERHAUL WP5 (2026-09-20): nửa tách ra của `KachiSpace.kt` (trần 500 dòng) — hình học hai thanh.
        // Không chuyển được sang `:core` **do luật của chính dự án**: `SpacingScaleContractTest.core khong giu so dp`
        // CẤM `:core` giữ số dp (*"khoảng cách là việc của tầng vẽ"*, bài học ChipTone). Nó "thuần" theo phép đo ở
        // đây chỉ vì nó KHÔNG có hàm đổi dp nào (bản gốc `KachiSpace.kt` có `dp(ctx, …)` nên nhắc `Context`) — cùng
        // lẽ với `KachiPalette.kt`/`KachiPaletteSeeds.kt` ngay trên: thuần về kỹ thuật, thuộc `:app` về layering.
        "KachiSpaceBars.kt" to "nửa tách ra của KachiSpace (thang dp) — :core bị CẤM giữ số dp",
    )

    @Test
    fun `so file thuan con nam trong app chi duoc giam`() {
        val root = root("app/src/main/java", "../app/src/main/java") ?: error("khong tim thay cay nguon can quet — bai canh dang tu tat")
        val pure = kotlinFiles(root).filter { file ->
            val text = file.toFile().readText()
            !androidOrDadb.containsMatchIn(text) &&
                !Regex("""\b(Context|View|Activity|Service|Bitmap|Canvas)\b""").containsMatchIn(text) &&
                file.fileName.toString() !in pureButMustStayInApp
        }
        assertEquals(
            pureFilesStillInApp,
            pure.size,
            "đổi rồi: nếu giảm thì hạ con số; nếu tăng thì file thuần mới đang bị đặt vào :app. " +
                "Hiện: ${pure.map { it.fileName.toString() }.sorted()}",
        )
    }

    /**
     * Q1 áp cho `:car-integration`: mọi file ở đây phải thật sự nói với thiết bị.
     *
     * Sinh ra từ một lỗi thật: `CarExecCommands` 276 dòng nằm trong module transport với **0** lần dùng
     * dadb, bị giữ lại chỉ vì phụ thuộc một kiểu kết quả. Logic thuần nằm nhờ trong module thiết bị thì
     * không ai test nó off-car được — đúng cái giá đã phải trả với `CastAndroidRuntime`.
     */
    @Test
    fun `moi file trong car-integration phai that su dung dadb`() {
        val root = root("car-integration/src/main/kotlin", "../car-integration/src/main/kotlin") ?: error("khong tim thay cay nguon can quet — bai canh dang tu tat")
        val pure = kotlinFiles(root).filter { !it.toFile().readText().contains("dadb") }
        assertEquals(
            emptyList<String>(),
            pure.map { it.fileName.toString() },
            "file không dùng dadb thì thuộc :core, không phải :car-integration",
        )
    }

    /**
     * Q3: mọi file phải thuộc một cột feature. File nằm ở gốc package là file không có chủ.
     *
     * Sinh ra từ lỗi thật: sau khi dời, 5 file navigation nằm ở gốc `com.byd.clusternav` trong `:core`,
     * không thuộc cột nào — nên không ai biết ai sở hữu chúng khi cần đổi.
     */
    @Test
    fun `khong file nao nam o goc package cua core`() {
        val root = root("core/src/main/kotlin/com/byd/clusternav", "../core/src/main/kotlin/com/byd/clusternav")
            ?: error("khong tim thay cay nguon can quet — bai canh dang tu tat")
        val orphans = Files.list(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
        }
        assertEquals(
            emptyList<String>(),
            orphans.map { it.fileName.toString() },
            "mỗi file phải nằm trong một cột feature (navigation/, modules/, carexec/, core/)",
        )
    }

    /**
     * P4: tên không được nói sai về chuồng.
     *
     * `CastAndroidGateway` sau khi sang module không có Android thì cái tên thành lời nói dối, và
     * `AndroidObservedStateParser` trong `:core` cũng vậy. Người đọc sau sẽ xếp chỗ sai theo cái tên.
     */
    @Test
    fun `khong ten nao chua Android trong module khong co Android`() {
        val roots = listOfNotNull(
            root("core/src/main/kotlin", "../core/src/main/kotlin"),
            root("car-integration/src/main/kotlin", "../car-integration/src/main/kotlin"),
        )
        val liars = roots.flatMap(::kotlinFiles).filter { file ->
            Regex("""(?m)^(?:internal )?(?:object|class|interface) [A-Za-z]*Android""")
                .containsMatchIn(file.toFile().readText())
        }
        assertEquals(emptyList<String>(), liars.map { it.fileName.toString() }, "tên nói sai về chuồng")
    }

    /** Q3: feature không gọi ngang feature, kiểm trong `:core` nơi cả hai cùng sống. */
    @Test
    fun `navigation va cast khong goi ngang nhau trong core`() {
        val root = root("core/src/main/kotlin/com/byd/clusternav", "../core/src/main/kotlin/com/byd/clusternav")
            ?: error("khong tim thay cay nguon can quet — bai canh dang tu tat")
        val nav = root.resolve("navigation")
        val cast = root.resolve("modules")
        if (Files.exists(nav)) {
            kotlinFiles(nav).forEach { file ->
                assertTrue(
                    !file.toFile().readText().contains("modules.clustercast"),
                    "${file.fileName}: navigation không được import Cast",
                )
            }
        }
        if (Files.exists(cast)) {
            kotlinFiles(cast).forEach { file ->
                assertTrue(
                    !file.toFile().readText().contains("clusternav.navigation"),
                    "${file.fileName}: Cast không được import navigation",
                )
            }
        }
    }

    @Test
    fun `tai lieu quy tac ton tai va liet ke du cot cuong che`() {
        val doc = root(
            "docs/refactor-car-execution/layering-rules.md",
            "../docs/refactor-car-execution/layering-rules.md",
        )
        assertTrue(doc != null, "thiếu tài liệu quy tắc")
        val text = doc!!.toFile().readText()
        listOf(
            "Q1", "Q2", "Q3", "P1", "P2", "P3", "P4",
            "chưa cưỡng chế", "LayeringRulesTest", "attestation",
        ).forEach {
            assertTrue(text.contains(it), "tài liệu thiếu phần '$it'")
        }
    }
}
