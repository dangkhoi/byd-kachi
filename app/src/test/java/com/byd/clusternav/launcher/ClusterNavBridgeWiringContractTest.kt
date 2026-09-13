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
 *
 * ## Bài KHÔNG còn ở đây
 * Mục **3. Cast** và **4. Phím vô-lăng** đã tách sang [ClusterNavBridgeCastKeysWiringContractTest]
 * (D2c — tệp này từng 511 dòng, quá trần 500 của CLAUDE.md §4.1). Cắt theo **tệp cầu được quét**: ở đây
 * còn `ClusterNavBridge.kt` (+ ràng buộc cứng của cả ba tệp), bên kia là `…Cast.kt` + `…Keys.kt`.
 * Không đổi một assert nào khi tách.
 */
class ClusterNavBridgeWiringContractTest {

    private val BRIDGE = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt"
    private val CAST = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeCast.kt"
    private val KEYS = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeKeys.kt"
    private val MSG = "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeMsg.kt"

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
    // 3. Ràng buộc cứng: cầu không giữ View, không đụng màn cũ  (mục 5 của bản gộp trước D2c)
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
     * S3 (2026-09-13): `MainActivity.kt` đã bị **xoá** cùng cả màn cũ, nên nó rời khỏi danh sách này —
     * hai tệp tài nguyên byte-seal T11 thì Ở LẠI trên đĩa như hiện vật và vẫn không được đụng một byte
     * (spec `kachi-remove-legacy-screen.html` R5; `LegacyScreenAbsenceContractTest` canh hash của chúng).
     */
    @Test
    fun `khong dung vao hai tep niem phong`() {
        val sealed = listOf(
            "app/src/main/res/layout/activity_main.xml",
            "app/src/main/res/values/strings.xml",
        )
        // Gốc repo = thư mục tổ tiên gần nhất có `.git` (working dir của test tuỳ module — xem SourceRoots).
        val root = generateSequence(SourceRoots.path(BRIDGE).toAbsolutePath()) { it.parent }
            .firstOrNull { java.nio.file.Files.exists(it.resolve(".git")) }
            ?: return  // không phải checkout git (CI tarball) ⇒ bỏ qua, các bài khác vẫn canh

        val out = ProcessBuilder(listOf("git", "diff", "--stat", "HEAD", "--") + sealed)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText()

        assertTrue(
            out.isBlank(),
            "hai tệp niêm phong T11 KHÔNG được sửa, nhưng `git diff` thấy:\n$out",
        )
    }
}
