package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 · R14 — khoá bộ lọc ANR của `ClusterDiag`: CHỈ mục `data_app_anr` có tiêu đề nêu đúng gói Kachi, mục MỚI NHẤT, ≤ 64 KB.
 * Mẫu dropbox TỔNG HỢP, dựng đúng định dạng nguồn r47 (`DropBoxManagerService.java:598-612`, `ActivityManagerService.java:9459-9496`)
 * — chưa có dump thật từ xe (sẽ thay bằng `docs/diagnostics/carlog-*` khi có, CLAUDE.md §10).
 */
class AnrDropboxTest {

    private val self = "com.byd.clusternav2"
    private val sep = "========================================"

    private fun entry(ts: String, process: String, pkg: String, body: String, activity: String = "$pkg/.Main") = """
        |$sep
        |$ts data_app_anr (text, 1234 bytes)
        |Process: $process
        |PID: 4242
        |UID: 10086
        |Flags: 0x38c83e44
        |Package: $pkg v198 (2.95)
        |Foreground: Yes
        |Activity: $activity
        |Subject: Input dispatching timed out
        |Build: BYD/di3/…
        |
        |$body
        |
    """.trimMargin()

    private val preamble = """
        |Drop box contents: 5 entries
        |Max entries: 1000
        |Low priority rate limit period: 2000 ms
        |Low priority tags: []
        |
    """.trimMargin()

    @Test
    fun `picks the NEWEST entry of Kachi, never another app`() {
        val dump = preamble + "\n" +
            entry("2026-10-07 20:00:01", self, self, "\"main\" old-kachi-stack") +
            entry("2026-10-07 21:04:40", self, self, "\"main\" prio=5 tid=1 Blocked\n  at com.byd.clusternav.X.onReceive") +
            entry("2026-10-07 21:10:00", "com.other.app", "com.other.app", "\"main\" other-app-secret",
                activity = "$self/.launcher.KachiHomeActivity") // nhắc gói Kachi ở Activity: — KHÔNG được tính
        val got = AnrDropbox.latestFor(dump, self)!!
        assertTrue(got.startsWith("2026-10-07 21:04:40 data_app_anr"), got)
        assertTrue(got.contains("tid=1 Blocked"), got)
        assertFalse(got.contains("other-app-secret") || got.contains("old-kachi-stack"), got)
    }

    @Test
    fun `sub-process of Kachi counts, prefix-lookalike package does not`() {
        val dump = entry("2026-10-07 21:00:00", "${self}x", "${self}x", "lookalike") +
            entry("2026-10-07 21:01:00", "$self:voice", self, "voice-proc")
        assertTrue(AnrDropbox.latestFor(dump, self)!!.contains("voice-proc"))
        assertNull(AnrDropbox.latestFor(entry("2026-10-07 21:00:00", "${self}x", "${self}x", "lookalike"), self))
    }

    @Test
    fun `no Kachi entry, empty dump, blank package or other tag - null`() {
        assertNull(AnrDropbox.latestFor(entry("2026-10-07 21:00:00", "com.other", "com.other", "x"), self))
        assertNull(AnrDropbox.latestFor(preamble + "(No entries found.)\n", self))
        assertNull(AnrDropbox.latestFor("", self))
        assertNull(AnrDropbox.latestFor(entry("2026-10-07 21:00:00", self, self, "x"), ""))
        val crash = entry("2026-10-07 21:00:00", self, self, "x").replace("data_app_anr", "data_app_crash")
        assertNull(AnrDropbox.latestFor(crash, self), "chỉ thẻ data_app_anr")
    }

    @Test
    fun `body mentioning Kachi does not make another app's entry ours`() {
        val dump = entry("2026-10-07 21:00:00", "com.other", "com.other", "Process: $self\nPackage: $self v1")
        assertNull(AnrDropbox.latestFor(dump, self), "chỉ phần tiêu đề (trước dòng trống) mới tính")
    }

    @Test
    fun `tail-cut first block without its date line is dropped`() {
        val cut = entry("2026-10-07 20:00:00", self, self, "cut-body").substringAfter("data_app_anr (text, 1234 bytes)\n")
        val dump = cut + entry("2026-10-07 21:00:00", "com.other", "com.other", "x")
        assertNull(AnrDropbox.latestFor(dump, self))
    }

    @Test
    fun `entry is bounded to maxBytes (UTF-8) and keeps the head`() {
        val big = "\"main\" prio=5 tid=1 Blocked\n" + "ầ".repeat(100_000)
        val got = AnrDropbox.latestFor(entry("2026-10-07 21:00:00", self, self, big), self)!!
        assertTrue(got.encodeToByteArray().size <= AnrDropbox.MAX_BYTES, "${got.encodeToByteArray().size}")
        assertTrue(got.contains("tid=1 Blocked"), "giữ ĐẦU mục (tiêu đề + luồng main)")
        assertEquals(got, AnrDropbox.truncateUtf8(got, AnrDropbox.MAX_BYTES), "đã dưới trần ⇒ giữ nguyên")
    }

    @Test
    fun `command is read-only and capped`() {
        assertEquals("dumpsys dropbox --print data_app_anr | tail -c 2097152", AnrDropbox.CMD)
    }
}
