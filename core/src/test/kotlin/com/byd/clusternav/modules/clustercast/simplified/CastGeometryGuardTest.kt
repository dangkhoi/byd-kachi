package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V-CLUSTER · VC-R4 [P0] — chuỗi hình học đi thẳng vào shell: KIỂM trước, không ném ═══════════════════════
 *
 * Spec §11.4.4 / §11.5 C2 / §11.6 V-5. Bài này khoá **cả hai chiều** của bộ kiểm:
 *  • mọi dạng độc (`;`, `&&`, `$()`, dấu nháy, số âm sai chỗ, số khổng lồ, NBSP, chữ số Unicode) ⇒ BỊ TỪ CHỐI;
 *  • mọi giá trị THẬT hôm nay (chip 320/240/160, `1920x720`, overscan CarPlay âm, khung cả cụm) ⇒ LỌT QUA.
 * Chiều thứ hai quan trọng ngang chiều thứ nhất: một bộ kiểm chặt quá tay là cụm mất cấu hình của người lái.
 */
class CastGeometryGuardTest {

    private val poisonDensity = listOf(
        "240;reboot", "240 && reboot", "$(id)", "`id`", "'320'", "\"320\"", "-240", "+240", "0240", "9999",
        "79", "641", "123456789", "", " 320", "320\n", "320 ", "٣٢٠", "3 20", "RESET", "reset;id",
    )
    private val poisonSize = listOf(
        "1920x720 && rm -rf /", "1920x720;reboot", "$(id)x720", "1920X720", "0x720", "1920x0", "9000x720",
        "-1920x720", "1920x720 ", "1920x", "x720", "", "123456789x1", "1920 x720", "١٩٢٠x720",
    )
    private val poisonOverscan = listOf(
        "0,0,0,0;reboot", "0,0,0", "0,0,0,0,0", "a,b,c,d", "10,-120,10,50 && id", "5000,0,0,0", "-5000,0,0,0",
        "0,,0,0", "0, 0,0,0", "$(id),0,0,0", "--1,0,0,0",
    )
    private val poisonBounds = listOf(
        "a,b,c,d", "10,0,5,720", "0,720,1920,10", "0,0,0,720", "-10,0,1920,720", "0,0,9000,720",
        "0,0,1920,720;reboot", "0,0,1920", "0,0,1920,720,5", "999999,0,1,1", "0,0,1920,720 ", "", "$(id)",
    )

    // ── 1 · Chiều ĐỘC: không gì lọt ─────────────────────────────────────────────────────────────

    @Test
    fun `density doc bi tu choi`() {
        poisonDensity.forEach { assertNull(CastGeometryGuard.density(it), "density lọt: '$it'") }
        assertNull(CastGeometryGuard.density(null))
    }

    @Test
    fun `wmSize doc bi tu choi`() {
        poisonSize.forEach { assertNull(CastGeometryGuard.wmSize(it), "wmSize lọt: '$it'") }
    }

    @Test
    fun `overscan doc bi tu choi`() {
        poisonOverscan.forEach { assertNull(CastGeometryGuard.overscan(it), "overscan lọt: '$it'") }
    }

    @Test
    fun `bounds doc bi tu choi, ke ca khung lon nguoc`() {
        poisonBounds.forEach { assertNull(CastGeometryGuard.bounds(it), "bounds lọt: '$it'") }
    }

    // ── 2 · Chiều THẬT: giá trị hôm nay phải lọt nguyên văn ────────────────────────────────────

    @Test
    fun `gia tri that hom nay deu lot nguyen van`() {
        listOf("320", "240", "160", "80", "640", "reset").forEach { assertEquals(it, CastGeometryGuard.density(it)) }
        listOf("1920x720", "1422x800", "1920x1080", "960x720").forEach { assertEquals(it, CastGeometryGuard.wmSize(it)) }
        listOf("0,0,0,0", "10,-120,10,50").forEach { assertEquals(it, CastGeometryGuard.overscan(it)) }
        assertEquals(CastBounds(0, 0, 1920, 720), CastGeometryGuard.bounds("0,0,1920,720"))
        assertEquals(CastBounds(960, 0, 1920, 720), CastGeometryGuard.bounds("960,0,1920,720"))
    }

    @Test
    fun `ba hang cua ma dieu la shell-safe`() {
        listOf(DisplayConfig.CARPLAY, DisplayConfig.ANDROID_AUTO, DisplayConfig.NORMAL_DEFAULT).forEach {
            assertTrue(CastGeometryGuard.isShellSafe(it), "hằng trong mã bị chốt cuối chặn: $it")
        }
    }

    @Test
    fun `chot cuoi so DUNG byte sap gui, khong chi so parse duoc`() {
        val ok = DisplayConfig.NORMAL_DEFAULT
        assertFalse(CastGeometryGuard.isShellSafe(ok.copy(density = "0240")), "parse ra 240 nhưng byte gửi là 0240")
        assertFalse(CastGeometryGuard.isShellSafe(ok.copy(density = "240;reboot")))
        assertFalse(CastGeometryGuard.isShellSafe(ok.copy(wmSize = "1920x720 && id")))
        assertFalse(CastGeometryGuard.isShellSafe(ok.copy(overscan = "0,0,0,0|id")))
        assertFalse(CastGeometryGuard.isShellSafe(ok.copy(bounds = CastBounds(100, 0, 50, 720))))
    }

    // ── 3 · Khoá của họ: tên gói không mang được lệnh ──────────────────────────────────────────

    @Test
    fun `khoa ho hop le va doc`() {
        listOf(
            "config_density_vn.vietmap.live",
            "config_size_com.google.android.apps.maps",
            "config_bounds_com.google.android.apps.maps__L30",
            "config_overscan_a.b__R90",
            "config_density_a.b__L10",
        ).forEach { assertTrue(CastGeometryGuard.isFamilyKey(it), "khoá thật bị coi là lạ: $it") }
        listOf(
            "config_density_$(id)", "config_density_a.b;reboot", "config_density_nodot", "config_density_a.b__L30;x",
            "config_density_a.b-c", "config_density_a.b__L3 0", "config_colour_a.b", "@family:cast_geometry",
            "config_density_", "config_density_1a.b", "config_density_a.b ", "config_density_a..b",
            "config_density_a.b\"", "config_density_a.b'", "config_density_a.b`id`",
        ).forEach { assertFalse(CastGeometryGuard.isFamilyKey(it), "khoá độc lọt: $it") }
        // [ĐO test 2026-09-30] Một đoạn tên gói Android ĐƯỢC chứa `_` và chữ số, nên `a.b__L55` là khoá FULL của gói
        // `a.b__L55` — hợp lệ về hình, và không mang ký tự shell nào. Regex không (và không cần) tách được hai cách đọc.
        assertTrue(CastGeometryGuard.isFamilyKey("config_density_a.b__L55"))
    }

    @Test
    fun `dai ti le cua khoa sinh tu SPLIT_PERCENTS`() {
        CastProfile.SPLIT_PERCENTS.forEach { pct ->
            assertTrue(CastGeometryGuard.isFamilyKey("config_bounds_a.b__L$pct"))
            assertTrue(CastGeometryGuard.isFamilyKey("config_bounds_a.b__R$pct"))
        }
        assertEquals(4, CastGeometryGuard.KEY_PREFIXES.size, "một bản ghi = 4 trường (spec K4)")
    }

    @Test
    fun `gia tri kiem theo DUNG truong cua khoa`() {
        assertTrue(CastGeometryGuard.isValidValue("config_density_a.b", "320"))
        assertFalse(CastGeometryGuard.isValidValue("config_density_a.b", "1920x720"), "giá trị của trường khác")
        assertTrue(CastGeometryGuard.isValidValue("config_size_a.b", "1920x720"))
        assertTrue(CastGeometryGuard.isValidValue("config_overscan_a.b", "10,-120,10,50"))
        assertTrue(CastGeometryGuard.isValidValue("config_bounds_a.b__L30", "0,0,576,720"))
        assertFalse(CastGeometryGuard.isValidValue("khac_a.b", "320"))
    }

    // ── 4 · Lớp 3: đọc tệp sống KHÔNG BAO GIỜ ném ──────────────────────────────────────────────

    @Test
    fun `readConfig giu nguyen hanh vi cu voi gia tri hop le`() {
        val logs = mutableListOf<String>()
        assertNull(CastGeometryGuard.readConfig(null, "1,2,3,4", "320", "0,0,1,1") { logs += it }, "vắng size ⇒ null")
        assertEquals(
            DisplayConfig("1920x720", "0,0,0,0", "reset", null),
            CastGeometryGuard.readConfig("1920x720", null, null, null) { logs += it },
        )
        assertEquals(
            DisplayConfig("1920x720", "10,-120,10,50", "320", CastBounds(0, 0, 1920, 720)),
            CastGeometryGuard.readConfig("1920x720", "10,-120,10,50", "320", "0,0,1920,720") { logs += it },
        )
        assertTrue(logs.isEmpty(), "giá trị hợp lệ không được sinh log: $logs")
    }

    @Test
    fun `readConfig gap gia tri doc thi bo dung truong do, ghi log, khong nem`() {
        val logs = mutableListOf<String>()
        val c = CastGeometryGuard.readConfig("1920x720", "0,0,0,0;reboot", "240;reboot", "a,b,c,d") { logs += it }
        assertEquals(DisplayConfig("1920x720", "0,0,0,0", "reset", null), c)
        assertEquals(3, logs.size, "mỗi trường hỏng một dòng log: $logs")
        assertTrue(logs.none { '\n' in it || '\u0000' in it }, "log không được mang ký tự điều khiển của tệp độc")
        assertNull(CastGeometryGuard.readConfig("1920x720 && id", "0,0,0,0", "320", null) { logs += it })
    }

    @Test
    fun `sanitizeForSave luu DUNG thu da ap`() {
        val bad = DisplayConfig("1920x720", "0,0,0,0", "700", CastBounds(10, 0, 5, 720))
        assertEquals(DisplayConfig("1920x720", "0,0,0,0", "reset", null), CastGeometryGuard.sanitizeForSave(bad))
        assertNull(CastGeometryGuard.sanitizeForSave(bad.copy(wmSize = "x")), "thiếu size ⇒ không ghi bản ghi")
        assertEquals(DisplayConfig.NORMAL_DEFAULT, CastGeometryGuard.sanitizeForSave(DisplayConfig.NORMAL_DEFAULT))
    }

    // ── 5 · Đo khung màn (gom hai bản chép) ────────────────────────────────────────────────────

    @Test
    fun `parseDisplaySize giu hanh vi cua tung ben`() {
        val both = "Physical size: 1920x720\nOverride size: 1600x720"
        assertEquals(1600 to 720, CastGeometryGuard.parseDisplaySize(both, preferOverride = true))
        assertEquals(1920 to 720, CastGeometryGuard.parseDisplaySize(both, preferOverride = false))
        assertEquals(1920 to 720, CastGeometryGuard.parseDisplaySize("Physical size: 1920x720", preferOverride = true))
        assertNull(CastGeometryGuard.parseDisplaySize("Override size: 1600x720", preferOverride = false))
        assertNull(CastGeometryGuard.parseDisplaySize("Physical size: 0x720", preferOverride = true))
        assertNull(CastGeometryGuard.parseDisplaySize("error: no display", preferOverride = true))
    }
}

/**
 * V-5 — CHỐT CUỐI ở tầng THI HÀNH: cấu hình độc lọt qua mọi lớp trước (prefs giả trả thẳng chuỗi độc) vẫn ra **0 lệnh**.
 * Bỏ `isShellSafe` ở `DisplayConfigurator.apply` / `CastGeometryController.applyPinned` ⇒ bài này đỏ.
 * (C6 · 2026-09-30: `applyPinned` THAY `applySavedProfile` — chốt đi theo hàm mới, đúng lời dặn của lượt `:core` trước.)
 */
class CastGeometryShellGateTest {

    private val poisoned = DisplayConfig("1920x720", "0,0,0,0", "240;reboot", CastBounds(0, 0, 1920, 720))

    @Test
    fun `DisplayConfigurator tu choi cau hinh doc, khong mot lenh nao`() {
        val shell = FakeShell()
        assertFalse(DisplayConfigurator(shell) {}.apply(2, poisoned))
        assertFalse(DisplayConfigurator(shell) {}.apply(2, poisoned.copy(density = "320", wmSize = "1920x720 && id")))
        assertTrue(shell.history.isEmpty(), "lệnh đã chạy: ${shell.history}")
        assertTrue(DisplayConfigurator(shell) {}.apply(2, DisplayConfig.NORMAL_DEFAULT), "cấu hình sạch vẫn áp")
        assertTrue(shell.history.none { ';' in it || '&' in it || '$' in it })
    }

    @Test
    fun `applyPinned tu choi ban ghi doc, khong mot lenh nao`() {
        val shell = FakeShell()
        CastGeometryController(shell, FakePrefs(), { 2 }) {}.applyPinned("vn.vietmap.live", poisoned)
        CastGeometryController(shell, FakePrefs(), { 2 }) {}
            .applyPinned("vn.vietmap.live", poisoned.copy(density = "240", bounds = CastBounds(0, 0, 0, 720)))
        assertTrue(shell.history.isEmpty(), "lệnh đã chạy: ${shell.history}")
    }
}
