package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ §8.2 (A) — ẢNH CHỤP NGỮ PHÁP: tiến trình chính GHI ở mọi đường ghi, `:wake` ĐỌC TỆP mỗi phiên ═══════════════
 *
 * Vá **[P1] Pass 1 (2026-09-26)**: CLOSE-3 đưa mọi lối vào qua `:wake` khi "Hey Kachi" bật, mà `buildSession` dựng
 * ngữ pháp với `profiles`/`places = emptyList()` ⇒ mất *"đổi sang hồ sơ X"* / *"về nhà"*. Bài này canh BA dây, quét
 * SOURCE (dự án không dựng Service/SharedPreferences trong JVM — cùng lệ [VoiceEntryRouteWiringContractTest]):
 *  (a) **mọi** hàm ghi hồ sơ / sổ địa chỉ của `WorkspacePrefs` + `WorkspacePrefsProfile.kt` kết bằng
 *      `VoiceGrammarSnapshotStore.write(this)` — thiếu một hàm là một đường đổi hồ sơ mà `:wake` không thấy;
 *  (b) `VoiceWakeService.buildSession` đọc store cho CẢ BA chỗ dùng (hotword · parser · dispatcher), không còn
 *      `emptyList()`/`HomeUiState()` trần;
 *  (c) đường đọc là ĐỌC TỆP — store `read` và `VoiceWakeService.kt` không chạm `SharedPreferences`/`WorkspacePrefs`
 *      (cache theo tiến trình = chính cái bệnh); `:wake` KHÔNG ghi (cache cũ của nó sẽ đè ảnh chụp mới).
 */
class VoiceGrammarSnapshotWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val prefs by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt") }
    private val prefsProfile by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspacePrefsProfile.kt") }
    private val store by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceGrammarSnapshotStore.kt") }
    private val service by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt") }
    // 2.69 — `buildSession` ở tệp riêng; ba tệp `:wake` chạm phiên đều bị canh "không chạm prefs / không ghi ảnh chụp".
    private val factory by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessionFactory.kt") }
    private val wakeFiles by lazy {
        listOf(
            "VoiceWakeService.kt" to service,
            "VoiceWakeSessionFactory.kt" to factory,
            "VoiceWakeHomeRelay.kt" to code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeHomeRelay.kt"),
            "VoiceWakeSessions.kt" to code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessions.kt"),
        )
    }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }

    private val hook = "VoiceGrammarSnapshotStore.write(this)"

    // ══ (a) Mọi đường ghi hồ sơ / sổ địa chỉ đều chụp ảnh ═══════════════════════════════════════════════════

    /** Danh sách ĐẦY ĐỦ các hàm ghi `profiles` / `active_profile` / `saved_places` — thêm hàm ghi mới thì thêm vào đây. */
    private val writers = listOf(
        "WorkspacePrefs.kt" to "fun setActiveProfile(name: String)",
        "WorkspacePrefs.kt" to "fun addProfile(name: String)",
        "WorkspacePrefs.kt" to "fun deleteProfile(name: String)",
        "WorkspacePrefs.kt" to "fun setSavedPlaces(places: List<SavedPlace>)",
        "WorkspacePrefsProfile.kt" to "internal fun WorkspacePrefs.duplicateActiveProfile(name: String)",
        "WorkspacePrefsProfile.kt" to "internal fun WorkspacePrefs.migrateScenesOnce()",
        "WorkspacePrefsProfile.kt" to "fun WorkspacePrefs.renameProfile(old: String, new: String): Boolean",
        "WorkspacePrefsProfile.kt" to "internal fun WorkspacePrefs.importProfile(data: String, name: String? = null): Boolean",
    )

    @Test
    fun `moi ham ghi ho so va so dia chi deu goi VoiceGrammarSnapshotStore write SAU apply`() {
        writers.forEach { (file, sig) ->
            val src = if (file == "WorkspacePrefs.kt") prefs else prefsProfile
            val body = SourceRoots.body(src, sig)
            val apply = body.lastIndexOf(".apply()")
            val write = body.lastIndexOf(hook)
            assertTrue(apply >= 0, "$file `$sig` phải ghi prefs bằng apply()")
            assertTrue(write >= 0, "$file `$sig` không gọi `$hook` — `:wake` sẽ không thấy lượt đổi này (§8.2 A)")
            assertTrue(write > apply, "$file `$sig`: ảnh chụp phải chụp SAU khi prefs đã ghi, không thì chụp giá trị cũ")
        }
    }

    /** Không có hàm nào ghi ba khoá ấy mà lọt ngoài [writers] — bài (a) mới đủ nghĩa "mọi". */
    @Test
    fun `khong co duong ghi K_PROFILES hoac K_ACTIVE hoac saved_places nao ngoai danh sach da canh`() {
        val keys = listOf("K_PROFILES", "K_ACTIVE", "K_PLACES")
        listOf("WorkspacePrefs.kt" to prefs, "WorkspacePrefsProfile.kt" to prefsProfile).forEach { (name, src) ->
            val covered = writers.filter { it.first == name }.map { (_, sig) -> SourceRoots.body(src, sig) }
            keys.forEach { key ->
                val re = Regex("""put\w+\((?:WorkspacePrefs\.)?$key\b|put\w+\(key\($key\)""")
                val puts = re.findAll(src).count()
                val inCovered = covered.sumOf { re.findAll(it).count() }
                assertTrue(puts == inCovered, "$name ghi `$key` $puts lần nhưng chỉ $inCovered nằm trong hàm đã canh — có đường ghi mới chưa gắn hook")
            }
        }
    }

    @Test
    fun `mo man chinh chup mot lan de may vua nang cap co anh chup ngay`() {
        val fn = SourceRoots.body(wiring, "internal fun Activity.voiceSession(")
        assertTrue(fn.contains("VoiceGrammarSnapshotStore.write(WorkspacePrefs(this))"), "voiceSession() phải chụp một lần lúc dựng phiên màn chính")
    }

    // ══ (b) `:wake` đọc store cho CẢ BA chỗ dùng ═════════════════════════════════════════════════════════════

    @Test
    fun `buildSession doc anh chup cho hotword, parser va dispatcher - khong con emptyList hay HomeUiState tran`() {
        val build = SourceRoots.body(factory, "internal fun VoiceWakeService.buildSession(): VoiceSession {")
        assertTrue(build.contains("VoiceGrammarSnapshotStore.read(app)"), "buildSession phải đọc tệp ảnh chụp")
        assertTrue(build.contains("profiles = { grammar().profiles }"), "hồ sơ vào hotword + parser (VoiceSession.profiles)")
        assertTrue(build.contains("places = { grammar().placeLabels() }"), "nhãn sổ địa chỉ vào hotword + parser (VoiceSession.places)")
        assertTrue(build.contains("state = { grammar().homeState() }"), "dispatcher giải địa chỉ từ state().savedPlaces (VoiceTargetDispatch.runNavSaved)")
        assertFalse(build.contains("emptyList()"), "không còn ngữ pháp rỗng trong `:wake` (khe (a) §8.2 đã đóng)")
        assertFalse(build.contains("HomeUiState()"), "không còn HomeUiState() trần — dispatcher sẽ không thấy sổ địa chỉ")
        // Lambda đọc lại MỖI lần gọi (không `val grammar = read(...)` chụp một lần rồi giữ suốt vòng đời service).
        assertTrue(build.contains("val grammar = { VoiceGrammarSnapshotStore.read(app) }"), "phải là lambda — service `:wake` sống suốt chuyến, chụp một lần là cache")
        assertTrue(build.contains("onSwitchProfile = { name -> openHome(VoiceHomeAction.SWITCH_PROFILE, name) }"), "đổi hồ sơ từ `:wake` trả về Activity")
    }

    // ══ (c) Đọc là ĐỌC TỆP; `:wake` không ghi ══════════════════════════════════════════════════════════════

    @Test
    fun `store read di thang he tep - khong SharedPreferences, khong WorkspacePrefs, khong Prefs`() {
        val read = SourceRoots.body(store, "fun read(ctx: Context): VoiceGrammarSnapshot {")
        listOf("SharedPreferences", "getSharedPreferences", "WorkspacePrefs", "Prefs.").forEach {
            assertFalse(read.contains(it), "read() chạm `$it` — cache theo tiến trình là đúng cái bệnh tệp này chữa")
        }
        assertTrue(read.contains("readText()"), "read() phải đọc tệp")
        assertTrue(read.contains("VoiceGrammarSnapshot.decode("), "giải mã ở :core (đã test thuần), không tự đọc dòng ở đây")
        wakeFiles.forEach { (n, src) ->
            assertFalse(src.contains("WorkspacePrefs"), "$n không được mở WorkspacePrefs (SharedPreferences cache theo tiến trình)")
            assertFalse(src.contains("SharedPreferences"), "$n không được chạm SharedPreferences")
        }
    }

    @Test
    fun `store write nguyen tu va chi tien trinh chinh - wake khong bao gio ghi`() {
        val write = SourceRoots.body(store, "fun write(prefs: WorkspacePrefs)")
        assertTrue(write.contains("if (!isMainProcess(ctx))") && write.contains("return"), "chỉ tiến trình chính ghi — cache prefs của `:wake`/`:tts` là cũ")
        val atomic = SourceRoots.body(store, "private fun writeAtomic(ctx: Context, text: String): Boolean")
        assertTrue(atomic.contains("renameTo("), "ghi nguyên tử: tệp tạm + renameTo (khuôn VoiceModelStore/AdbKeys)")
        assertTrue(atomic.indexOf("writeText(") < atomic.indexOf("renameTo("), "ghi tạm TRƯỚC rồi mới đổi tên")
        wakeFiles.forEach { (n, src) -> assertFalse(src.contains("VoiceGrammarSnapshotStore.write"), "$n: `:wake` KHÔNG được ghi ảnh chụp (cache prefs cũ đè dữ liệu mới)") }
        assertTrue(store.contains("Application.getProcessName() == ctx.packageName"), "tiến trình chính = tên tiến trình bằng tên gói (phụ mang hậu tố :wake/:tts)")
        // SOÁT 2.68 · Pass 2 · P3 — một tên tệp tạm dùng chung ⇒ cặp "so lastBody – ghi" phải tuần tự (hai luồng của
        // cùng tiến trình chính, vd cầu kiểm thử gọi từ luồng binder, sẽ writeText xen nhau rồi renameTo bản lẫn).
        assertTrue(write.contains("synchronized(lock)"), "cặp so-ghi phải nằm trong một khoá — tệp tạm dùng chung một tên")
    }

    // ══ Trần 500 dòng cho các tệp lượt này chạm ══════════════════════════════════════════════════════════════

    @Test
    fun `cac tep cua luot nay khong vuot tran 500 dong`() {
        mapOf(
            "WorkspacePrefs.kt" to SourceRoots.text("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt"),
            "WorkspacePrefsProfile.kt" to SourceRoots.text("src/main/java/com/byd/clusternav/launcher/WorkspacePrefsProfile.kt"),
            "KachiHomeWiring.kt" to SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt"),
            "VoiceWakeService.kt" to SourceRoots.text("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt"),
            "VoiceWakeSessionFactory.kt" to SourceRoots.text("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessionFactory.kt"),
            "VoiceWakeHomeRelay.kt" to SourceRoots.text("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeHomeRelay.kt"),
            "VoiceWakeSessions.kt" to SourceRoots.text("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessions.kt"),
            "VoiceGrammarSnapshotStore.kt" to SourceRoots.text("src/main/java/com/byd/clusternav/launcher/voice/VoiceGrammarSnapshotStore.kt"),
        ).forEach { (name, src) -> assertTrue(src.lines().size <= 500, "$name dài ${src.lines().size} dòng — trần là 500") }
    }
}
