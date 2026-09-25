package com.byd.clusternav.automation

import com.byd.clusternav.launcher.SettingsNavAutomationFormat
import com.byd.clusternav.launcher.automation.ScheduledNavRules
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ AUTOMATION 1.85 · BÀI CANH DÂY NỐI của `:app` (stage 3) ══════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R1–R5. Hai loại bài trong một tệp, và ranh giới giữa chúng là **có đo
 * được off-car hay không**:
 *  • **thuần** — phép đọc/in giờ ([SettingsNavAutomationFormat]) chạy thật;
 *  • **quét nguồn** — mọi thứ chạm HAL / `LocationManager` / FGS thì off-car không chạy được, nên thứ canh được là
 *    *"đường dây có nối đúng chỗ không"*. Đây đúng khuôn `ClusterNavBridgeWiringContractTest` /
 *    `KachiAutostartServiceWiringTest` của dự án: chuỗi lời gọi **thật**, không phải nhãn.
 *
 * ⚠ Bài quét nguồn không thay được một lượt đo trên xe — nó chỉ chặn *"ai đó gỡ một mắt xích rồi build vẫn xanh"*.
 * Phần phải đo trên xe ghi ở `docs/_handoff/1.85-stage3.md`.
 */
class Automation185WiringTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val rain by lazy { app("automation/RainDefrostApplier.kt") }
    private val navApplier by lazy { app("automation/ScheduledNavApplier.kt") }
    private val service by lazy { app("automation/AutomationService.kt") }
    private val gps by lazy { app("automation/GpsAvailability.kt") }
    private val bridge by lazy { app("launcher/ClusterNavBridgeAutomation.kt") }
    private val autostart by lazy { app("KachiAutostart.kt") }
    private val boot by lazy { app("BootSetupService.kt") }

    // ── Phần THUẦN: đọc/in giờ ─────────────────────────────────────────────────────────────────────

    @Test
    fun `in gio ra HH mm va kep gia tri rac`() {
        assertEquals("07:30", SettingsNavAutomationFormat.hhmm(7 * 60 + 30))
        assertEquals("00:00", SettingsNavAutomationFormat.hhmm(0))
        assertEquals("23:59", SettingsNavAutomationFormat.hhmm(ScheduledNavRules.MAX_MIN))
        // Giá trị rác trên đĩa KHÔNG được in ra "25:73" — kẹp về hai đầu dải.
        assertEquals("23:59", SettingsNavAutomationFormat.hhmm(99_999))
        assertEquals("00:00", SettingsNavAutomationFormat.hhmm(-5))
    }

    @Test
    fun `doc gio nhan ba dang nguoi dung go`() {
        assertEquals(7 * 60 + 30, SettingsNavAutomationFormat.parseHhmm("07:30"))
        assertEquals(7 * 60 + 30, SettingsNavAutomationFormat.parseHhmm("7:30"))
        assertEquals(7 * 60 + 30, SettingsNavAutomationFormat.parseHhmm("7h30"))
        assertEquals(7 * 60 + 30, SettingsNavAutomationFormat.parseHhmm(" 7 30 "))
        assertEquals(0, SettingsNavAutomationFormat.parseHhmm("0:00"))
        assertEquals(ScheduledNavRules.MAX_MIN, SettingsNavAutomationFormat.parseHhmm("23:59"))
    }

    /**
     * Giờ sai ⇒ `null`, để chỗ gọi đưa `-1` xuống `:core` và chính [ScheduledNavRules.of] từ chối rồi UI **nói ra**.
     * Lùi về một giá trị mặc định ở tầng đọc sẽ lưu một khung giờ người dùng KHÔNG gõ, im lặng.
     */
    @Test
    fun `gio sai tra null chu khong doan`() {
        listOf("", "  ", "abc", "7", "7:", ":30", "24:00", "23:60", "7:30:00", "-1:00", "7:-5")
            .forEach { assertNull(SettingsNavAutomationFormat.parseHhmm(it), "\"$it\" phải bị từ chối") }
    }

    @Test
    fun `vong tron in roi doc lai giu nguyen phut`() {
        (0..ScheduledNavRules.MAX_MIN step 7).forEach { m ->
            assertEquals(m, SettingsNavAutomationFormat.parseHhmm(SettingsNavAutomationFormat.hhmm(m)))
        }
    }

    // ── Phần QUÉT NGUỒN: đường dây ────────────────────────────────────────────────────────────────

    /**
     * R1: sấy đọc trạng thái **TỪ XE**, không từ một cờ RAM. Cờ RAM không thấy người lái bấm nút sấy trên màn xe
     * ⇒ R1.5 (*"chỉ tắt cái sấy do automation bật"*) chết im lặng.
     *
     * ⚠ V7 (owner 2026-09-25): nút được đọc không còn CỐ ĐỊNH là sấy trước — nó là **mỏ neo** = phần tử đầu của
     * `selection()`. Bài canh vì thế đòi hai điều thay cho một chuỗi cứng: đọc qua `readState` của **một mã truyền
     * vào**, và mã ấy lấy từ `pick.first()`. Đó là bất biến thật sự cần giữ — *cái được ĐỌC phải là một cái đang
     * được GHI*; đọc `defrost` trong khi chỉ ghi `defrost_rear` thì mỗi nhịp đều đọc về `tắt`, ra lệnh bật, rồi
     * lại đọc về `tắt` = ghi HAL mỗi 5 phút suốt chuyến mà không bao giờ thấy việc mình làm.
     */
    @Test
    fun `say doc trang thai tu xe qua readState`() {
        assertTrue("carControl.readState(controlId)" in rain, "phải đọc sấy qua đường readKey của nút")
        assertTrue("val anchor = pick.first()" in rain, "nút được đọc = MỎ NEO của selection(), không phải mã cứng")
        assertTrue("readDefrost(app, anchor)" in rain, "và lượt đọc phải dùng chính mỏ neo đó")
        assertTrue("RainDefrostPolicy.plausible(" in rain, "lần đọc mưa phải qua bộ lọc sentinel của :core")
        assertTrue("RainDefrostOwner.step(" in rain, "quyết định phải đi qua máy trạng thái R1.5")
        assertTrue("RainDefrostOwner.stepUnknown(" in rain, "đọc không được ⇒ giữ nguyên, KHÔNG coi như trời khô")
    }

    /**
     * V7 (owner 2026-09-25) — **ĐẢO CHIỀU bài canh của 1.85.**
     *
     * R1.3 cũ chốt *"bật/tắt cả hai nút sấy"* và bài này khoá đúng chuỗi `listOf(CTL_FRONT, CTL_REAR)`. Owner nay
     * chốt hai ô CHỌN riêng (trước · sau+gương · cả hai), nên chuỗi cũ phải biến mất — và điều cần khoá đổi thành:
     *  1. lượt ghi chỉ chạm **những nút đã chọn** (`pick.forEachIndexed`), không phải một danh sách cứng;
     *  2. `selection()` dựng từ đúng HAI khoá prefs — không có đường thứ ba nào quyết định kính nào được sấy;
     *  3. **cả hai bỏ tích ⇒ no-op**: `pick.isEmpty()` phải gác ngay ở cửa `tick`, và phải `reset()` ký ức. Thiếu
     *     cổng này thì bỏ tích cả hai ô là *"vẫn đọc cảm biến mỗi 5 phút rồi không ghi gì"* — vô hại nhưng dối, và
     *     lượt sau ai thêm một nút thứ ba sẽ thừa hưởng một `pick` rỗng chạy qua cả thân hàm.
     *
     * Hai hằng mã nút vẫn phải đúng nguyên văn: chúng là mã của `ControlRegistry`, gõ sai là ghi vào hư không.
     */
    @Test
    fun `say chi ghi nhung nut da chon`() {
        assertTrue("CTL_FRONT = \"defrost\"" in rain)
        assertTrue("CTL_REAR = \"defrost_rear\"" in rain)
        assertTrue("pick.forEachIndexed" in rain, "một lượt ghi chỉ chạm các nút ĐÃ CHỌN")
        assertFalse(
            "listOf(CTL_FRONT, CTL_REAR)" in rain,
            "danh sách CỨNG hai nút đã bị V7 thay bằng selection() — còn nó là còn đường ghi cả hai bất chấp lựa chọn",
        )
        assertTrue("Prefs.rainDefrostFront(app)" in rain, "selection() phải đọc đúng khoá của ô TRƯỚC")
        assertTrue("Prefs.rainDefrostRear(app)" in rain, "selection() phải đọc đúng khoá của ô SAU")
        assertTrue("pick.isEmpty()" in rain, "cả hai bỏ tích ⇒ coi như tính năng TẮT, gác ngay ở cửa tick")
    }

    /**
     * Bind theo **TÊN HẰNG** trước, số đo được chỉ là đường lùi (R11: `BYDAutoFeatureIds` gán giá trị trong
     * `static {}` theo cấu hình xe ⇒ dán số là đúng cho một xe).
     */
    @Test
    fun `mua bind theo ten hang truoc roi moi lui ve so do duoc`() {
        assertTrue("SETTING_FRONT_RAIN_WIPER_SPEED" in rain)
        assertTrue("featureIdByName(RAIN_CONST) ?: MEASURED_ID" in rain, "tên hằng trước, số là đường lùi")
        assertEquals(
            1196425250,
            RainDefrostApplier.MEASURED_ID,
            "[ĐO xe 2026-09-20] đổi số này là đổi một dữ kiện đã đo — phải có lượt đo mới",
        )
        assertTrue("rawIsSentinel(raw)" in rain, "sentinel > ngưỡng mưa ⇒ phải lọc, không thì bật sấy giữa nắng")
    }

    /**
     * ⚠⚠ R2.4 · usecase hầm: `GpsAvailability` trả **ba** giá trị (`true`/`false`/`null`), và `null` = *chưa biết*
     * phải xử như **CHỜ**. So `== true` là cách duy nhất làm điều đó; `!= false` sẽ biến *"chưa cấp quyền"* thành
     * *"có GPS"* ⇒ dẫn trong hầm, im lặng, đúng trên chiếc xe chưa cấp quyền.
     */
    @Test
    fun `gps chua biet thi CHO chu khong di luon`() {
        assertTrue(
            "GpsAvailability.isAvailable(app, nowMs) == true" in navApplier,
            "null (chưa biết) phải rơi về false = chờ; cấm dùng `!= false`",
        )
        assertTrue("Boolean?" in gps, "phải phơi được ca \"không đọc được\"")
        assertTrue("MAX_FIX_AGE_MS" in gps, "phải hỏi TUỔI fix, không chỉ hỏi provider có bật")
        assertTrue("elapsedRealtimeNanos" in gps, "đồng hồ đơn điệu: đầu xe VẶN đồng hồ tường lúc nổ máy")
    }

    /**
     * R2.4: đóng dấu **NGAY** sau khi giao được, và **KHÔNG** đóng dấu khi chưa giao được.
     *  • thiếu vế đầu ⇒ nhịp sau (60 s) mở app dẫn đường lần thứ hai, suốt cả khung giờ;
     *  • thêm vế sau sai ⇒ một lần hỏng tạm thời (app chưa cài) làm mất cả lượt đi của ngày hôm đó.
     */
    @Test
    fun `dong dau da-dan chi khi giao duoc`() {
        assertTrue("if (launched) {" in navApplier)
        assertTrue("NavAutomationFired.put(fired, rule.id, today)" in navApplier)
        assertTrue("setNavAutomationFired(app" in navApplier)
        val afterElse = navApplier.substringAfter("if (launched) {").substringAfter("} else {")
        assertTrue(
            "setNavAutomationFired" !in afterElse.substringBefore("\n        }"),
            "nhánh KHÔNG giao được tuyệt đối không được đóng dấu",
        )
    }

    /** Đi đúng đường mà một câu lệnh giọng nói đi — không dựng `Intent` thứ hai (xem KDoc `VoiceAppIntents`). */
    @Test
    fun `nav dung lai duong ban y-dinh cua voice`() {
        assertTrue("VoiceAppIntents.send(app, handoff)" in navApplier)
        assertTrue("Intent(Intent.ACTION_VIEW" !in navApplier, "cấm dựng Intent dẫn đường thứ hai ở đây")
        assertTrue("SavedPlaces.find(" in navApplier, "điểm đến tra từ sổ địa chỉ của hồ sơ đang dùng")
        assertTrue("PackageQueries.queryActivities(" in navApplier, "hỏi app đã cài qua cửa duy nhất của dự án")
    }

    /** R4: nhịp 60 s cho nav, và rule mưa đếm nhịp ra ~5 phút (KHÔNG dựng vòng thứ hai). */
    @Test
    fun `mot dong co, mot vong, hai nhip`() {
        assertEquals(60_000L, AutomationService.TICK_MS)
        assertEquals(5, AutomationService.RAIN_EVERY_TICKS)
        // 2026-09-24: rule mưa nay theo THỜI GIAN TRÔI (nhịp base đổi tốc độ theo camera 1s/60s ⇒ không đếm nhịp được).
        assertTrue("nowMs - lastRainMs >= TICK_MS * RAIN_EVERY_TICKS" in service, "rule mưa theo mốc thời gian ~5 phút")
        // 2026-09-25 (closeout B2/BG-13/BG-15): camera KHÔNG còn nhịp 250 ms; sự kiện xi-nhan tới qua socket + hẹn giờ
        // HOLD (`CameraHold`), controller dùng chung ở `AppContainer`. FGS vẫn là nơi giữ nó sống khi HOME stopped
        // (owner 2026-09-24: lái xe HOME stopped) — qua `syncCamera(app)` mỗi nhịp và ở `finally`.
        assertTrue("syncCamera(app)" in service, "FGS nền phải sync camera (owner 2026-09-24: lái xe HOME stopped)")
        assertTrue("container.cameraSignal.tick()" in service, "camera đi qua controller dùng chung của AppContainer (BG-15)")
        assertTrue("CAMERA_TICK_MS" !in service, "BG-13: không còn vòng 250 ms riêng cho camera")
        assertEquals(
            1,
            Regex("""Thread\(\{""").findAll(service).count(),
            "đúng MỘT vòng nhịp — vòng thứ hai là thứ thứ hai phải nhớ dừng lúc huỷ",
        )
    }

    /**
     * Guard vòng đúng cơ chế đã proven ở `Pm25FilterApplier`: `running` + **token thế hệ**. Thiếu token thì chuỗi
     * bật→tắt→bật để thread cũ sống cạnh thread mới, và `finally` của thread cũ xoá cờ của thread mới.
     */
    @Test
    fun `vong nhip co token the he va tu thoat`() {
        assertTrue("myGen = ++generation" in service)
        assertTrue("myGen == generation && anyEnabled(app)" in service, "vòng phải tự thoát khi hết việc")
        assertTrue("if (myGen == generation) running = false" in service, "chỉ thế hệ hiện tại được nhả cờ")
        assertTrue("generation++" in service, "tắt công tắc phải vô hiệu vòng NGAY")
    }

    /** Hết việc ⇒ service tự chết. Một FGS thường trú không làm gì là chi phí ròng + một dòng log gây hiểu sai. */
    @Test
    fun `khong con viec thi service tu dung`() {
        assertTrue("if (!anyEnabled(applicationContext))" in service)
        assertTrue("stopSelf" in service)
        assertTrue("startForegroundOnce()" in service, "phải lên foreground TRƯỚC mọi việc (hợp đồng ~5 s)")
    }

    /**
     * ⚠ Mỗi lượt GHI phải kèm một lượt `sync` — [ĐO] S4: **0** chỗ nào trong dự án đăng ký
     * `registerOnSharedPreferenceChangeListener`, nên ghi prefs xong là *"đúng trên đĩa mà không ai đang chạy biết"*.
     */
    @Test
    fun `moi setter cua cau deu dong bo dong co`() {
        val setRain = bridge.substringAfter("fun ClusterNavBridge.setRainDefrost(").substringBefore("\n}")
        assertTrue("setRainDefrostEnabled(app, on)" in setRain, "phải ghi ĐÚNG khoá thật")
        assertTrue("AutomationService.sync(app)" in setRain, "ghi xong phải đồng bộ engine")
        val setRules = bridge.substringAfter("fun ClusterNavBridge.setNavRules(").substringBefore("\n}")
        assertTrue("setNavAutomationRules(app, NavAutomationBook.encode(rules))" in setRules)
        assertTrue("ScheduledNavApplier.pruneFired(app)" in setRules, "phải dọn dấu đã-dẫn mồ côi")
        assertTrue("AutomationService.sync(app)" in setRules)
    }

    /**
     * ⚠⚠ Phải re-arm ở **CẢ HAI** đường boot: `KachiAutostart` gác sau `launcher_autostart`, còn `BootSetupService`
     * gác sau `headless_autostart` — hai công tắc RIÊNG. Gác automation sau một công tắc không liên quan là đúng
     * bẫy đã ăn ở P7 (cảnh khởi động chết theo `launcher_autostart`, im lặng).
     */
    @Test
    fun `hai duong boot deu dung lai dong co`() {
        assertTrue("AutomationService.sync(app)" in autostart, "KachiAutostart phải re-arm engine")
        assertTrue("AutomationService.sync(applicationContext)" in boot, "BootSetupService phải re-arm engine")
    }

    /** Vòng nhịp là RAM ⇒ không re-arm thì hai automation chỉ chạy đúng phiên người dùng gạt công tắc. */
    @Test
    fun `engine khong tu nho vong qua lan no may`() {
        assertTrue(
            "@Volatile private var running" in service && "@Volatile private var generation" in service,
            "hai cờ vòng là RAM (có chủ ý) — đó là lý do phải re-arm ở boot",
        )
    }
}
