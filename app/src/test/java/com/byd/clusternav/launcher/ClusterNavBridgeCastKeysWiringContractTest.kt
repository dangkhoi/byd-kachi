package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CẦU ClusterNav — CHIẾU + PHÍM VÔ-LĂNG (bài canh DÂY NỐI, quét mã nguồn `:app`) ══════════════
 *
 * Tách khỏi [ClusterNavBridgeWiringContractTest] (backlog D2c: tệp đó 511 dòng, quá trần 500 của
 * CLAUDE.md §4.1). Ranh giới cắt theo **tệp cầu được quét**, không cắt theo số dòng: bài ở đây quét
 * `ClusterNavBridgeCast.kt` + `ClusterNavBridgeKeys.kt`, còn tệp kia quét `ClusterNavBridge.kt` (công
 * tắc Dẫn đường, bản đồ khoá Prefs, huy hiệu/bong bóng/ghế/PM2.5) và các ràng buộc cứng của cả ba tệp.
 * Cắt theo tệp nghĩa là sửa một tệp cầu chỉ phải đọc MỘT tệp bài canh.
 *
 * Lý do bài phải quét mã (bản sao của `MainActivity.kt` thì **trôi**) và lý do bài nằm ở `:app` (không
 * phải `:core`, để Gradle không coi task là `UP-TO-DATE` ⇒ dấu xanh giả): xem KDoc
 * [ClusterNavBridgeWiringContractTest]. Toàn bộ assert giữ NGUYÊN văn từ bản gộp.
 */
class ClusterNavBridgeCastKeysWiringContractTest {

    private val BRIDGE = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt"
    private val CAST = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeCast.kt"
    private val KEYS = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeKeys.kt"

    /** MÃ đã bỏ chú thích — mọi phép "phải/không được chứa chuỗi X" đều chạy trên bản này. */
    private fun bridge() = SourceRoots.codeOf(BRIDGE)
    private fun cast() = SourceRoots.codeOf(CAST)
    private fun keys() = SourceRoots.codeOf(KEYS)

    private fun body(src: String, signature: String) = SourceRoots.body(src, signature)

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 1. Cast  (mục 3 của bản gộp trước D2c)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Công tắc Cast lặp `CastEnableSwitch.kt:62–80`: BẬT mở chiếu + nút nổi; TẮT dừng + đóng chiếu +
     * dừng service và **KHÔNG mở lại**. Thiếu `closeProjection` ⇒ tắt Cast mà cụm vẫn treo màn chiếu.
     */
    @Test
    fun `setCastEnabled mo va dong chieu dung thu tu`() {
        val b = body(cast(), "fun ClusterNavBridge.setCastEnabled(on: Boolean)")
        listOf(
            "coordinator.prefs.setCastEnabled(on)",
            "coordinator.openProjection()",
            "FloatingBubbleService",
            "SimpleCastIntent.Stop()",
            "coordinator.closeProjection()",
            "stopService(",
        ).forEach { token ->
            assertTrue(token in b, "setCastEnabled thiếu `$token` (CastEnableSwitch.kt:62–80)")
        }
        assertEquals(
            listOf("openProjection", "closeProjection"),
            Regex("""(openProjection|closeProjection)""").findAll(b).map { it.value }.toList(),
            "thứ tự phải là mở (nhánh BẬT) rồi đóng (nhánh TẮT) — đảo lại là tắt xong tự mở lại",
        )
    }

    /** Tỉ lệ chia phải đi đường LIVE (persist + resize tại chỗ), không ghi prefs suông. */
    @Test
    fun `setSplitPct di duong applySplitRatioLive`() {
        val b = body(cast(), "fun ClusterNavBridge.setSplitPct(pct: Int)")
        assertTrue("coordinator.applySplitRatioLive(" in b, "phải dùng applySplitRatioLive (persist + resize tại chỗ)")
        assertTrue("CastProfile.normalizePercent(pct)" in b, "phần trăm lạ phải rơi về 50 qua normalizePercent")
    }

    /** Hai chế độ tự-chiếu loại trừ nhau — hai driver cùng chạy đã từng gây đua SLOT_OCCUPIED (R1/T1). */
    @Test
    fun `tu chieu full va chia doi loai tru lan nhau`() {
        assertTrue(
            "coordinator.prefs.setAutoStartSplitEnabled(false)" in
                body(cast(), "fun ClusterNavBridge.setAutostartFull(on: Boolean)"),
            "bật tự-chiếu FULL phải tắt tự-chiếu CHIA ĐÔI",
        )
        assertTrue(
            "coordinator.prefs.setAutoStartEnabled(false)" in
                body(cast(), "fun ClusterNavBridge.setAutostartSplit(on: Boolean)"),
            "bật tự-chiếu CHIA ĐÔI phải tắt tự-chiếu FULL",
        )
    }

    /** Ba hành động chiếu phải dispatch đúng intent + đúng phía. */
    @Test
    fun `hanh dong chieu dispatch dung intent`() {
        assertTrue(
            "SimpleCastIntent.CastFull(pkg, AppMover.classifyApp(pkg))" in
                body(cast(), "fun ClusterNavBridge.castFull(pkg: String)"),
            "chiếu full phải kèm AppMover.classifyApp (chọn hồ sơ hiển thị đúng loại app)",
        )
        assertTrue(
            "SimpleCastIntent.CastSlot(pkg, ClusterSlotSide.LEFT)" in
                body(cast(), "fun ClusterNavBridge.castLeft(pkg: String)"),
            "castLeft phải nhắm ClusterSlotSide.LEFT",
        )
        assertTrue(
            "SimpleCastIntent.CastSlot(pkg, ClusterSlotSide.RIGHT)" in
                body(cast(), "fun ClusterNavBridge.castRight(pkg: String)"),
            "castRight phải nhắm ClusterSlotSide.RIGHT",
        )
    }

    /**
     * Hai đường cứu hộ KHÁC NHAU ở đúng một điểm và không được lẫn:
     *  - [restoreCluster] (thường) — Stop + đóng chiếu rồi **MỞ LẠI** sau 2 s;
     *  - [deepRescue] — đứng hẳn xuống, force-stop bên tranh chấp, reset VD, **KHÔNG mở lại**.
     *
     * Nếu deepRescue lỡ mở lại chiếu thì nó thôi là cứu hộ: ClusterNav lại giành cụm với DashCast, tức
     * đúng cái kẹt mà nó sinh ra để gỡ.
     */
    @Test
    fun `hai duong cuu ho khac nhau o cho mo lai chieu`() {
        val restore = body(cast(), "fun ClusterNavBridge.restoreCluster()")
        assertTrue("coordinator.openProjection()" in restore, "cứu hộ thường PHẢI mở lại chiếu sau 2 s")

        val deep = body(cast(), "fun ClusterNavBridge.deepRescue(")
        assertTrue("openProjection" !in deep, "dọn sạch cụm TUYỆT ĐỐI không mở lại chiếu — mở lại là giành cụm tiếp")
        assertTrue(
            "CAST_CONFLICT_PACKAGES" in deep,
            "danh sách app tranh chấp phải DÙNG CHUNG hằng gốc, không chép tay (hai bản sẽ trôi khỏi nhau)",
        )
        listOf("am force-stop", "wm size reset", "wm density reset", "wm overscan reset").forEach {
            assertTrue(it in deep, "dọn sạch cụm thiếu bước `$it` (CastDeepRescueAction.kt:73–83)")
        }
    }

    /**
     * Lệnh reset VD phải nhắm ĐÚNG display đo được (`-d $vd`) và chỉ chạy khi `vd >= 0` — CLAUDE.md §4:
     * không bao giờ quét mù, `vd < 1`/không rõ thì không làm gì.
     */
    @Test
    fun `reset VD chi chay khi do duoc display id`() {
        val deep = body(cast(), "fun ClusterNavBridge.deepRescue(")
        assertTrue("DisplayParse.clusterDisplayId(" in deep, "phải ĐO display id từ dumpsys, không đoán")
        assertTrue("if (vd >= 0)" in deep, "không đo được display thì KHÔNG chạy lệnh reset nào")
        assertTrue("-d \$vd" in deep, "mọi lệnh wm phải nhắm tường minh display vừa đo")
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 2. Phím vô-lăng  (mục 4 của bản gộp trước D2c)
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Toggle OFF→ON phải `grantAccessibility(reset = true)` — đó là nơi owner yêu cầu xoá single-flight
     * kẹt rồi cấp lại + force-rebind, để phím-thoại tự lành sau reboot mà KHÔNG phải khởi động lại app.
     */
    @Test
    fun `bat phim thoai grant voi reset true`() {
        val b = body(keys(), "fun ClusterNavBridge.setVoiceKeyEnabled(on: Boolean, onDone: (Boolean) -> Unit = {})")
        assertTrue("Prefs.setVoiceKeyEnabled(app, on)" in b, "phải ghi đúng khoá voicekey_enabled")
        assertTrue(
            "NavConnect.grantAccessibility(app, reset = true)" in b,
            "OFF→ON phải reset=true (xoá cờ kẹt + force-rebind) — thiếu thì sau reboot phím chết cho tới khi cài lại",
        )
        assertTrue(
            "NavConnect.grantAccessibility(app, reset = true)" in body(keys(), "fun ClusterNavBridge.checkFix(onDone: (Boolean) -> Unit = {})"),
            "nút Kiểm tra/Sửa ngay phải dùng CÙNG đường heal, không được nghĩ ra đường thứ hai",
        )
    }

    /** Trạng thái phím đọc ground-truth `NavAccessibilitySource.connected`, không đoán theo pref. */
    @Test
    fun `trang thai phim doc co bound that`() {
        assertTrue(
            "accessibilityBound()" in body(keys(), "fun ClusterNavBridge.voiceKeyStatus()"),
            "trạng thái phím phải đọc cờ connected của service (đã BOUND chưa), không suy từ setting",
        )
        assertTrue(
            "com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected" in
                body(bridge(), "fun accessibilityBound(): Boolean"),
            "ground-truth là NavAccessibilitySource.connected",
        )
    }

    /**
     * Công thức "đặt trợ lý hệ thống = Google/Gemini" chỉ chạy khi cấu hình THẬT SỰ đổi.
     * [ĐO] F3: đặt nó ở nơi chạy mỗi lần chạm ⇒ bung một thread + một phiên dadb + 2 toast oan.
     */
    @Test
    fun `cong thuc Gemini chi chay khi cau hinh that su doi`() {
        val b = body(keys(), "fun ClusterNavBridge.addBinding(keyCode: Int, targetSpec: String): String?")
        assertTrue("Prefs.addVoiceKeyBinding(app, keyCode, targetSpec)" in b, "phải ghi đúng khoá voicekey_bindings")
        assertTrue(
            Regex("""replaced\s*!=\s*targetSpec\s*&&""").containsMatchIn(b),
            "phải gác bằng `replaced != targetSpec` — bấm lại đúng cặp đang có thì KHÔNG chạy recipe",
        )
        assertTrue("AssistantLauncher.isGeminiVoiceSpec(targetSpec)" in b, "chỉ đích Gemini mới cần recipe này")
        assertTrue("Thread(" in b, "setSystemAssistant đi dadb ~1-2 s ⇒ phải chạy nền, không chặn luồng vẽ")
    }

    /**
     * Bảng preset mã phím — **đúng mã, đúng thứ tự**, ghim bằng danh sách chữ số.
     *
     * Tới 2026-09-13 bài này so bảng của cầu với `MainActivity.voiceKeyPresets()` (hai bản sao phải
     * trùng khít). Màn cũ đã gỡ ⇒ cầu là bản DUY NHẤT, nên bài chuyển sang ghim thẳng chín mã: chúng
     * là **mã phím vật lý của xe** ([ĐO] on-car 2026-08-13 cho `328` — nút mic vô-lăng nhấn-giữ), không
     * phải lựa chọn tuỳ ý. Sửa một con số ở đây là đổi nút mà người dùng đang bấm ngoài đường, nên nó
     * phải trả giá bằng một dòng test đỏ.
     *
     * Thứ tự cũng bị ghim: nó là thứ tự hiện trong danh sách chọn nút ở *Cài đặt › Phím vô-lăng*.
     */
    @Test
    fun `bang preset ma phim ghim dung chin ma va dung thu tu`() {
        val codes = Regex("""\d+""")
            .findAll(body(keys(), "fun ClusterNavBridge.buttonPresetCodes(): List<Int>"))
            .map { it.value }.toList()
        assertEquals(
            listOf("328", "231", "219", "85", "88", "87", "79", "5", "84"), codes,
            "preset mã phím (mic vô-lăng 328 đứng đầu) — đổi mã/thứ tự là đổi nút người dùng đang bấm",
        )
    }

    /**
     * Nút tự học: cầu lưu **nguyên chuỗi nhãn nhận được**.
     *
     * Màn cũ tự ghép `"<tên> (mã <code>)"` (`MainActivity.kt:820`), nhưng "mã" là chữ tiếng Việt và
     * tầng `launcher/` cấm chữ cứng ⇒ nhãn do tầng Settings dựng từ tài nguyên. Điều bài này canh là
     * cầu KHÔNG tự chế thêm một khuôn thứ hai — nếu nó ghép lại thì hai màn hiện hai nhãn cho một nút.
     */
    @Test
    fun `nut tu hoc luu nguyen nhan nhan duoc`() {
        val b = body(keys(), "fun ClusterNavBridge.addCustomButton(displayName: String, code: Int)")
        assertTrue(
            "Prefs.addVoiceKeyCustomButton(app, displayName, code)" in b,
            "phải lưu nguyên chuỗi nhãn của tầng Settings, không tự ghép khuôn thứ hai",
        )
    }

    /**
     * "Học phím" nhận mã qua [com.byd.clusternav.modules.voicekey.VoiceKeyLearnBus] (callback trong
     * tiến trình), KHÔNG poll `Prefs.voiceKeyLearn` — cờ đó chỉ nói "đang ở chế độ học", không mang mã.
     */
    @Test
    fun `hoc phim nhan ma qua VoiceKeyLearnBus`() {
        val start = body(keys(), "fun ClusterNavBridge.startLearn(onLearned: (Int) -> Unit)")
        assertTrue("Prefs.setVoiceKeyLearn(app, true)" in start, "phải bật cờ voicekey_learn cho service biết đang học")
        assertTrue("VoiceKeyLearnBus.setListener" in start, "phải nhận mã qua bus, không poll pref")
        assertTrue(
            "VoiceKeyLearnBus.setListener(null)" in body(keys(), "fun ClusterNavBridge.stopLearn()"),
            "phải gỡ listener khi kết thúc — bus là singleton app-scoped, giữ lambda là rò",
        )
    }
}
