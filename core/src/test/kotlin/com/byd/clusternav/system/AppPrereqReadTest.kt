package com.byd.clusternav.system

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.89 · B2 VM-PREREQ-TRUTH — khoá hai bộ đọc sự thật trên đầu ra NGUYÊN VĂN máy ảo `clusternav10` (A10, 05/10,
 * `docs/diagnostics/vm-prereq-emulator-2026-10-05/`). Lỗi xe khoá ở đây: VietMap cài lại mất miễn pin mà Kachi không
 * biết (cờ một-lần) ⇒ hộp "IVI không hỗ trợ" mỗi lần khởi động. Bộ đọc phải nói ĐÚNG "có/không/không biết" — nói
 * nhầm "có" là không chữa, nói nhầm "không" khi đọc hỏng là ghi mù.
 */
class AppPrereqReadTest {

    private val vm = "vn.vietmap.live"

    private fun fixture(name: String): String {
        val root = System.getProperty("clusternav.root") ?: error("clusternav.root chưa set — xem core/build.gradle.kts")
        val f = File(root, "docs/diagnostics/vm-prereq-emulator-2026-10-05/$name")
        check(f.isFile) { "thiếu fixture $f" }
        return f.readText()
    }

    // ── cmd deviceidle whitelist ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `danh sach that - VietMap o user la MIEN, shell o system la MIEN`() {
        val out = fixture("01-whitelist-before.txt")
        assertEquals(DozeWhitelistRead.Entry.USER, DozeWhitelistRead.entry(out, vm))
        assertEquals(Truth.YES, DozeWhitelistRead.entry(out, vm).exempt)
        assertEquals(DozeWhitelistRead.Entry.SYSTEM, DozeWhitelistRead.entry(out, "com.android.shell"))
    }

    @Test
    fun `VietMap vang - KHONG mien (ca hien truong sau khi cai lai)`() {
        val out = fixture("03-whitelist-vietmap-absent.txt")
        assertEquals(DozeWhitelistRead.Entry.NONE, DozeWhitelistRead.entry(out, vm))
        assertEquals(Truth.NO, DozeWhitelistRead.entry(out, vm).exempt)
    }

    @Test
    fun `chi o system-excidle thi KHONG tinh la mien (isIgnoringBatteryOptimizations bo qua)`() {
        val out = fixture("03-whitelist-vietmap-absent.txt")
        assertEquals(DozeWhitelistRead.Entry.EXCEPT_IDLE_ONLY, DozeWhitelistRead.entry(out, "com.android.providers.calendar"))
        assertEquals(Truth.NO, DozeWhitelistRead.entry(out, "com.android.providers.calendar").exempt)
        // Gói có cả hai khối: `system,` thắng.
        assertEquals(DozeWhitelistRead.Entry.SYSTEM, DozeWhitelistRead.entry(out, "com.android.providers.downloads"))
    }

    @Test
    fun `khong khop tien to - gioi han dung ten goi`() {
        val out = fixture("01-whitelist-before.txt")
        assertEquals(DozeWhitelistRead.Entry.NONE, DozeWhitelistRead.entry(out, "vn.vietmap"))
        assertEquals(DozeWhitelistRead.Entry.NONE, DozeWhitelistRead.entry(out, "vn.vietmap.live2"))
    }

    @Test
    fun `doc hong hoac dong la ma khong thay goi - KHONG BIET, khong phai KHONG`() {
        assertEquals(DozeWhitelistRead.Entry.UNKNOWN, DozeWhitelistRead.entry(null, vm))
        // Dạng lỗi dịch vụ (chuỗi của `cmd` khi không tìm thấy dịch vụ) — không chứng minh được VẮNG.
        assertEquals(DozeWhitelistRead.Entry.UNKNOWN, DozeWhitelistRead.entry("cmd: Can't find service: deviceidle", vm))
        // Bằng chứng dương thắng dòng lạ.
        assertEquals(
            DozeWhitelistRead.Entry.USER,
            DozeWhitelistRead.entry("Warning: something\n" + fixture("01-whitelist-before.txt"), vm),
        )
    }

    @Test
    fun `dau ra rong (ba danh sach rong) la su that hop le - KHONG mien`() {
        assertEquals(DozeWhitelistRead.Entry.NONE, DozeWhitelistRead.entry("", vm))
        assertEquals(DozeWhitelistRead.Entry.NONE, DozeWhitelistRead.entry("\n\n", vm))
    }

    @Test
    fun `ket qua lenh them - Added va Unknown package`() {
        assertFalse(DozeWhitelistRead.unknownPackage(fixture("04-add-vietmap.txt"), vm))
        assertTrue(DozeWhitelistRead.unknownPackage(fixture("06-add-not-installed.txt"), "com.example.kachi.notinstalled"))
        assertFalse(DozeWhitelistRead.unknownPackage(null, vm))
    }

    @Test
    fun `lenh ghi chi nhan ten goi that (goi co the den tu ho so nhap ngoai)`() {
        assertEquals("cmd deviceidle whitelist +vn.vietmap.live", DozeWhitelistRead.addCommand(vm))
        listOf("vn.vietmap.live; reboot", "", "-x", "a b", "vn.vietmap.live\nreboot", "../x").forEach { bad ->
            assertThrows(IllegalArgumentException::class.java, { DozeWhitelistRead.addCommand(bad) }, bad)
            assertThrows(IllegalArgumentException::class.java, { OverlayOpRead.allowCommand(bad) }, bad)
            assertThrows(IllegalArgumentException::class.java, { OverlayOpRead.readCommand(bad) }, bad)
        }
    }

    // ── appops get <gói> SYSTEM_ALERT_WINDOW ───────────────────────────────────────────────────────────────

    @Test
    fun `appops that - allow, default (bi tu choi), No operations`() {
        assertEquals("allow", OverlayOpRead.effectiveMode(fixture("07-appops-vietmap-allow.txt")))
        assertEquals(Truth.YES, OverlayOpRead.allowed("allow"))
        assertEquals("default", OverlayOpRead.effectiveMode(fixture("08-appops-youtube-default.txt")))
        assertEquals("default", OverlayOpRead.effectiveMode(fixture("09-appops-maps-no-operations.txt")))
        assertEquals(Truth.NO, OverlayOpRead.allowed("default"))
        assertEquals(Truth.NO, OverlayOpRead.allowed("ignore"))
    }

    @Test
    fun `goi chua cai hoac phien hong - KHONG BIET`() {
        assertNull(OverlayOpRead.effectiveMode(fixture("10-appops-not-installed.txt")))
        assertNull(OverlayOpRead.effectiveMode(null))
        assertNull(OverlayOpRead.effectiveMode(""))
        assertEquals(Truth.UNKNOWN, OverlayOpRead.allowed(null))
    }

    @Test
    fun `loi AOSP 10 - dong Uid mode mang op KHAC thi KHONG BIET, khong tin dong cua goi`() {
        // [ĐO máy ảo] đặt `--uid … SYSTEM_ALERT_WINDOW ignore` ⇒ r47 in nhầm op uid đầu tiên.
        val measured = fixture("11-appops-chrome-uid-ignore.txt")
        assertTrue(measured.contains("Uid mode: COARSE_LOCATION"), measured)
        assertNull(OverlayOpRead.effectiveMode(measured))
        // Có chế độ uid (in nhầm) + dòng của gói `allow` ⇒ vẫn KHÔNG BIẾT (uid thắng gói, mà giá trị uid không đọc được).
        assertNull(OverlayOpRead.effectiveMode(measured + fixture("07-appops-vietmap-allow.txt")))
    }

    @Test
    fun `dang r34 - dong Uid mode dung op thang dong cua goi`() {
        // [SUY nguồn r34 `AppOpsService.java:5534-5541` + `:2222-2229`] dòng uid in TRƯỚC, đúng op.
        val out = "Uid mode: SYSTEM_ALERT_WINDOW: ignore\nSYSTEM_ALERT_WINDOW: allow; time=+1m ago\n"
        assertEquals("ignore", OverlayOpRead.effectiveMode(out))
        assertEquals("allow", OverlayOpRead.effectiveMode("Uid mode: SYSTEM_ALERT_WINDOW: allow\n"))
    }

    @Test
    fun `lenh appops dung mot goi, dung mot op`() {
        assertEquals("appops get vn.vietmap.live SYSTEM_ALERT_WINDOW", OverlayOpRead.readCommand(vm))
        assertEquals("appops set vn.vietmap.live SYSTEM_ALERT_WINDOW allow", OverlayOpRead.allowCommand(vm))
    }
}
