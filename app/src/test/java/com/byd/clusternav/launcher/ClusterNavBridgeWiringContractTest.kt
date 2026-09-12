package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CẦU ClusterNav — bài canh DÂY NỐI (quét mã nguồn `:app`) ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` T2 · N2 · N3.
 *
 * ## Vì sao phải canh bằng quét mã, không phải bằng chạy thử
 * [ClusterNavBridge] **lặp lại** chuỗi lời gọi của `MainActivity.kt` (không trích hàm ra dùng chung
 * được: `activity_main.xml`/`strings.xml` bị hash-seal T11 và `MainActivity.kt` bị ~10 wiring test
 * ghim source — spec §2/§9). Bản sao thì **trôi**: ai đó sửa một nhánh mà quên nhánh kia, mọi test
 * đơn vị vẫn xanh vì hai bên không gọi nhau. Bài này khoá đúng những mắt xích mà "quên một dòng" là
 * mất hẳn hành vi trên xe — mỗi ca nói rõ hỏng cái gì nếu mất.
 *
 * ## Vì sao bài nằm ở `:app` (không phải `:core`)
 * Nó quét **mã nguồn `:app`**. [ĐO] hai lần trong dự án: bài quét mã của module X mà đặt ở module Y
 * thì Gradle không coi tệp của X là đầu vào ⇒ task `UP-TO-DATE` ⇒ bài **không bao giờ chạy lại**
 * (dấu xanh giả). Xem KDoc `ProfileKeysWiringContractTest`.
 */
class ClusterNavBridgeWiringContractTest {

    private val BRIDGE = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt"
    private val CAST = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeCast.kt"
    private val KEYS = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeKeys.kt"
    private val MSG = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeMsg.kt"
    private val MAIN = "src/main/java/com/byd/clusternav/MainActivity.kt"

    /** MÃ đã bỏ chú thích — mọi phép "phải/không được chứa chuỗi X" đều chạy trên bản này. */
    private fun bridge() = SourceRoots.codeOf(BRIDGE)
    private fun cast() = SourceRoots.codeOf(CAST)
    private fun keys() = SourceRoots.codeOf(KEYS)

    private fun body(src: String, signature: String) = SourceRoots.body(src, signature)

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 1. Công tắc Dẫn đường — mắt xích dài nhất, và là nơi "quên một dòng" tốn cả chuyến đi
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * `setNavEnabled` phải lặp ĐỦ chuỗi của `MainActivity.kt:97–128`. Thiếu từng mảnh thì:
     *  - `Prefs.setLane(app, true)` — làn cụm ở `false` với bản cài cũ ⇒ nav không bao giờ lên cụm;
     *  - `setOutputEnabled(CLUSTER_LANE, true)` — coordinator dựng trước màn hình giữ làn TẮT;
     *  - `selfGrant(` — chưa có quyền đọc thông báo thì bật xong vẫn câm (IVI khoá màn Settings, dadb
     *    là đường DUY NHẤT);
     *  - `grantAccessibility(` — bộ đọc màn GMaps mồ côi ⇒ đã [ĐO] hai chuyến screenRead RỖNG;
     *  - `NavRepository.stop(` — tắt công tắc mà luồng gửi vẫn chạy.
     */
    @Test
    fun `setNavEnabled lap du chuoi bat va tat cua man cu`() {
        val b = body(bridge(), "fun setNavEnabled(on: Boolean, onDone: (Boolean) -> Unit = {})")
        listOf(
            "Prefs.setEnabled(app, on)",
            "speedSign.onMasterEnabled(on)",
            "Prefs.setLane(app, true)",
            "setLane(",
            "NavRepository.setOutputEnabled(app, NavigationOutputTarget.CLUSTER_LANE, true)",
            "speedSign.onOutputEnabled(SpeedSignOutput.CLUSTER, true)",
            "NavConnect.ensureConnected(app)",
            "selfGrant(",
            "grantAccessibility(",
            "NavRepository.stop(",
        ).forEach { token ->
            assertTrue(token in b, "setNavEnabled thiếu `$token` — mất một mắt xích của MainActivity.kt:97–128")
        }
    }

    /**
     * Đường cấp quyền Hỗ trợ phải escalate khi **thiếu setting HOẶC service chưa bound**.
     *
     * [ĐO 2026-09-01] Sau reboot, `enabled_accessibility_services` còn nguyên nhưng service KHÔNG
     * bound ⇒ `onKeyEvent` chết. Gate chỉ-kiểm-setting sẽ bỏ qua đúng ca cần chữa (CLAUDE.md §3:
     * "không gate đường phục hồi bằng dữ liệu mà chỉ chính đường đó làm mới được").
     */
    @Test
    fun `cap quyen Ho tro escalate ca khi enabled nhung chua bound`() {
        val b = body(bridge(), "fun setNavEnabled(on: Boolean, onDone: (Boolean) -> Unit = {})")
        assertTrue(
            Regex("""!accessibilityBoosterGranted\(\)\s*\|\|\s*!accessibilityBound\(\)""").containsMatchIn(b),
            "phải là `!granted || !bound` — chỉ kiểm setting thì sau reboot phím/booster không bao giờ tự lành",
        )
    }

    /** `setClusterMode` phải áp NGAY, không chờ frame kế bị dedup nuốt (`MainActivity.kt:190–194`). */
    @Test
    fun `setClusterMode persist roi reapply ngay`() {
        val b = body(bridge(), "fun setClusterMode(mode: Int)")
        assertTrue("Prefs.setNavClusterScreenMode(app, mode)" in b, "phải ghi đúng khoá nav_cluster_screen_mode")
        assertTrue("NavRepository.reapplyClusterMode(app)" in b, "thiếu re-assert ⇒ đổi chế độ chỉ có tác dụng sau reboot")
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 2. Mỗi setter ghi ĐÚNG khoá thật (bản đồ khoá spec §4.3) — không tạo khoá song song
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * R2: "ghi đúng khoá thật đang được runtime đọc; không tạo khoá song song". Đây là danh sách
     * `hàm setter → lời gọi Prefs bắt buộc`. Sai một cái thì Settings mới đổi một khoá KHÁC với khoá
     * runtime đọc ⇒ nút bấm được, lưu được, mà xe không đổi gì (đúng kiểu "nút chết").
     */
    @Test
    fun `moi setter goi dung Prefs set cua khoa that`() {
        val b = bridge()
        val expected = listOf(
            "fun setMarquee(on: Boolean)" to "Prefs.setMarquee(app, on)",
            "fun setBadgeEnabled(on: Boolean)" to "Prefs.setBadgeEnabled(app, on)",
            "fun setUpcomingBadge(on: Boolean)" to "Prefs.setShowUpcomingBadge(app, on)",
            "fun setAlertChip(on: Boolean)" to "Prefs.setShowAlertChip(app, on)",
            "fun setBadgeSizeDp(sizeDp: Int)" to "Prefs.setBadgeSizeDp(app,",
            "fun setVmBubbleEnabled(on: Boolean)" to "Prefs.setVmBubbleEnabled(app, on)",
            "fun setSeatEnabled(on: Boolean)" to "Prefs.setSeatComfortEnabled(app, on)",
            "fun setSeatMode(mode: Int)" to "Prefs.setSeatComfortMode(app, mode)",
            "fun setSeatLevel(seatIndex: Int, level: Int)" to "Prefs.setSeatComfortLevel(app, seatIndex, level)",
            "fun setPm25Enabled(on: Boolean)" to "Prefs.setPm25FilterEnabled(app, on)",
            "fun setRecircOnStart(on: Boolean)" to "Prefs.setRecircOnStartEnabled(app, on)",
            "fun setHeadlessAutostart(on: Boolean)" to "Prefs.setHeadlessAutostart(app, on)",
        )
        expected.forEach { (signature, call) ->
            assertTrue(call in body(b, signature), "`$signature` phải ghi qua `$call` (khoá thật, spec §4.3)")
        }
    }

    /** Toạ độ/cỡ badge phải đi qua bộ kẹp THUẦN ở `:core` — không tự viết lại phép kẹp trong UI. */
    @Test
    fun `badge dung BadgeLayout clamp cua core`() {
        assertTrue(
            "BadgeLayout.clampSizeDp(" in body(bridge(), "fun setBadgeSizeDp(sizeDp: Int)"),
            "cỡ badge phải kẹp bằng BadgeLayout.clampSizeDp (dải 60..240)",
        )
        val center = body(bridge(), "fun setBadgeCenter(cx: Int, cy: Int)")
        assertTrue("BadgeLayout.clampCenter(" in center, "tâm badge phải kẹp bằng BadgeLayout.clampCenter")
        assertTrue("clusterSize()" in center, "phải kẹp trên kích cụm THẬT đang chiếu, không hằng 1920×720 viết cứng")
        assertTrue("badgeSizePx()" in center, "kẹp phải tính theo cỡ badge hiện tại, nếu không badge lọt ra ngoài cụm")
    }

    /** Badge/bong bóng bật ⇒ auto-start VietMap một lần (`BadgePlacementController.kt:52`, `MainActivity.kt:1094`). */
    @Test
    fun `bat badge va bong bong keo theo autostart VietMap`() {
        listOf("fun setBadgeEnabled(on: Boolean)", "fun setVmBubbleEnabled(on: Boolean)").forEach { sig ->
            val b = body(bridge(), sig)
            assertTrue(
                "if (on) VietMapAutostartService.startForAppOpen(app)" in b,
                "`$sig` phải auto-start VietMap khi BẬT (widget mới có nguồn tốc độ) — và CHỈ khi bật",
            )
        }
    }

    /** Mỗi công tắc badge phải đánh thức lớp phủ DÙNG CHUNG, nếu không phải mở lại app mới thấy đổi. */
    @Test
    fun `cac cong tac badge danh thuc lop phu dung chung`() {
        val pairs = listOf(
            "fun setBadgeEnabled(on: Boolean)" to "speedSign.onBadgeEnabledChanged()",
            "fun setUpcomingBadge(on: Boolean)" to "speedSign.onUpcomingBadgeEnabledChanged()",
            "fun setAlertChip(on: Boolean)" to "speedSign.onAlertChipEnabledChanged()",
            "fun setBadgeSizeDp(sizeDp: Int)" to "speedSign.debugRefreshBadgeLayout()",
            "fun setBadgeCenter(cx: Int, cy: Int)" to "speedSign.debugRefreshBadgeLayout()",
        )
        pairs.forEach { (sig, call) ->
            assertTrue(call in body(bridge(), sig), "`$sig` thiếu `$call` ⇒ đổi xong cụm không đổi gì")
        }
    }

    /**
     * Vị trí bong bóng phải đi qua [com.byd.clusternav.VmOverlayPosition] — nơi vừa kẹp, vừa ghi prefs,
     * vừa **bắn broadcast** `VM_BUBBLE_POS`. Ghi thẳng prefs thì mod VietMap không bao giờ nhận được.
     */
    @Test
    fun `vi tri bong bong di qua VmOverlayPosition`() {
        assertTrue(
            "VmOverlayPosition.setAbsoluteTopLeft(app, absX, absY)" in
                body(bridge(), "fun setVmBubblePos(absX: Int, absY: Int)"),
            "phải gọi VmOverlayPosition (ghi + broadcast), không ghi thẳng vm_bubble_x/y",
        )
    }

    /**
     * Ghế: đổi MỨC một ghế phải dùng `applySeat` (đường theo-ghế), KHÔNG phải `applyNow` (đường bulk).
     * [ĐO] trước v1.34 đường bulk bỏ qua mức "Tắt" ⇒ kéo về Tắt mà ghế vẫn chạy.
     */
    @Test
    fun `doi muc mot ghe dung applySeat khong dung applyNow`() {
        val level = body(bridge(), "fun setSeatLevel(seatIndex: Int, level: Int)")
        assertTrue("SeatComfortApplier.applySeat(app, seatIndex, level)" in level, "phải ghi HAL cho chính ghế đó")
        assertTrue(
            "applyNow" !in level,
            "đường bulk applyNow bỏ qua mức Tắt ⇒ dùng ở đây là không tắt được ghế (lỗi đã sửa ở v1.34)",
        )
        assertTrue(
            "SeatComfortApplier.applyNow(app)" in body(bridge(), "fun setSeatMode(mode: Int)"),
            "đổi CHẾ ĐỘ (mát↔sưởi) mới là đường bulk applyNow",
        )
    }

    /** PM2.5: công tắc gọi enable/disable; "Lọc ngay" là quick-clean chủ động, độc lập công tắc. */
    @Test
    fun `pm25 noi dung ba duong cua man cu`() {
        val toggle = body(bridge(), "fun setPm25Enabled(on: Boolean)")
        assertTrue("Pm25FilterApplier.enable(app)" in toggle && "Pm25FilterApplier.disable(app)" in toggle,
            "công tắc phải bật/tắt lọc-liên-tục")
        assertTrue(
            "Pm25FilterApplier.cleanNow(app)" in body(bridge(), "fun pm25CleanNow()"),
            "nút Lọc ngay phải gọi quick-clean (popup suông không lọc thật — lỗi owner báo 2026-09-08)",
        )
    }

    /**
     * N5: đọc HAL (mức PM2.5, số ghế) phải chạy trên thread NỀN rồi post về [ClusterNavBridge.ui].
     * Đọc trên luồng vẽ = reflection + HAL trên main ⇒ khựng màn hình trên xe.
     */
    @Test
    fun `doc HAL chay tren thread nen roi post ve luong ve`() {
        listOf("fun pm25Level(onLevel: (level: Int) -> Unit)", "fun seatCount(onCount: (Int) -> Unit)")
            .forEach { sig ->
                val b = body(bridge(), sig)
                assertTrue("Thread(" in b, "`$sig` phải đọc HAL trên thread nền (spec N5)")
                assertTrue("ui(Runnable" in b, "`$sig` phải post kết quả về luồng vẽ qua ui(...)")
            }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 3. Cast
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
            "CastDeepRescueAction.CONFLICT_PACKAGES" in deep,
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
    // 4. Phím vô-lăng
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
     * Bảng preset mã phím phải trùng KHÍT màn cũ — **đúng mã, đúng thứ tự**. Lệch một mã là hai màn
     * cho hai danh sách nút khác nhau cho cùng một chiếc xe.
     */
    @Test
    fun `bang preset ma phim trung khit man cu`() {
        val old = Regex("""\bto (\d+)\b""")
            .findAll(body(SourceRoots.codeOf(MAIN), "private fun voiceKeyPresets(): List<Pair<String, Int>>"))
            .map { it.groupValues[1] }.toList()
        val new = Regex("""\d+""")
            .findAll(body(keys(), "fun ClusterNavBridge.buttonPresetCodes(): List<Int>"))
            .map { it.value }.toList()
        assertEquals(old, new, "preset mã phím của cầu phải trùng khít `MainActivity.voiceKeyPresets()`")
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

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // 5. Ràng buộc cứng: cầu không giữ View, không đụng màn cũ
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * N2/§4.2: cầu **không giữ View**. Cấm `import android.view.` / `import android.widget.` ở cả ba
     * tệp. (Tham chiếu ĐẦY ĐỦ `android.view.KeyEvent.keyCodeToString` được phép: đó là hàm tiện ích
     * tĩnh dịch mã phím sang tên, không phải một View bị giữ lại.)
     */
    @Test
    fun `cau khong giu View`() {
        listOf(BRIDGE to bridge(), CAST to cast(), KEYS to keys(), MSG to SourceRoots.codeOf(MSG))
            .forEach { (name, src) ->
            listOf("import android.view.", "import android.widget.").forEach { bad ->
                assertTrue(
                    bad !in src,
                    "$name có `$bad` — cầu sống lâu hơn Activity, giữ View là rò màn hình + crash sau recreate()",
                )
            }
        }
    }

    /**
     * N4 · `LauncherI18nContractTest`: cầu **không mang chữ**. Cấm `import com.byd.clusternav.Lang`
     * (cơ chế song ngữ lúc-chạy của màn cũ) ở cả ba tệp — mọi thông điệp đi ra bằng mã [BridgeMsg] /
     * [VoiceKeyStatus] và tầng Settings dịch bằng tài nguyên của launcher.
     *
     * Vì sao canh CHÍNH TỆP CẦU chứ không phó mặc bài i18n: bài i18n bắt theo **dấu tiếng Việt**, nên
     * một câu tiếng Anh viết cứng (`Lang.t` vế EN, hay chuỗi không dấu) vẫn lọt qua nó. Ở cầu thì luật
     * chặt hơn — *không câu nào cả*, kể cả tiếng Anh.
     */
    @Test
    fun `cau khong mang chu`() {
        listOf(BRIDGE to bridge(), CAST to cast(), KEYS to keys()).forEach { (name, src) ->
            assertTrue(
                "com.byd.clusternav.Lang" !in src,
                "$name dùng `Lang` — cầu phải trả MÃ (BridgeMsg/VoiceKeyStatus), để tầng Settings dịch bằng tài nguyên",
            )
            assertTrue(
                "toast: (BridgeMsg)" in bridge(),
                "chữ ký toast phải nhận BridgeMsg, không nhận String — nhận String là mở lại cửa cho câu chữ",
            )
        }
    }

    /** Cầu phải neo vào applicationContext, không giữ Activity (cùng lý do trên). */
    @Test
    fun `cau neo vao applicationContext`() {
        assertTrue(
            "internal val app: Context = app.applicationContext" in bridge(),
            "phải tự quy về applicationContext ngay tại cửa — chặn việc lỡ truyền Activity vào vật sống lâu",
        )
    }

    /**
     * N3: **KHÔNG sửa `MainActivity.kt`** (và hai tệp byte-seal T11). Đợt gộp này cố ý "dựng lại trong
     * Kachi, cùng khoá" thay vì trích hàm ra, chính vì sửa màn cũ = phá niêm phong hoặc viết lại chục
     * wiring test cho một màn sắp hạ cấp (spec §9).
     */
    @Test
    fun `khong dung vao man cu va hai tep niem phong`() {
        val sealed = listOf(
            "app/src/main/java/com/byd/clusternav/MainActivity.kt",
            "app/src/main/res/layout/activity_main.xml",
            "app/src/main/res/values/strings.xml",
        )
        // Gốc repo = thư mục tổ tiên gần nhất có `.git` (working dir của test tuỳ module — xem SourceRoots).
        val root = generateSequence(SourceRoots.path(MAIN).toAbsolutePath()) { it.parent }
            .firstOrNull { java.nio.file.Files.exists(it.resolve(".git")) }
            ?: return  // không phải checkout git (CI tarball) ⇒ bỏ qua, các bài khác vẫn canh

        val out = ProcessBuilder(listOf("git", "diff", "--stat", "HEAD", "--") + sealed)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText()

        assertTrue(
            out.isBlank(),
            "T2 KHÔNG được sửa màn cũ / tệp niêm phong, nhưng `git diff` thấy:\n$out",
        )
    }
}
