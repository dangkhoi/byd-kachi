package com.byd.clusternav.modules.clustercast.nowplaying

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.11 · CLUSTER-NOW-PLAYING — khoá thứ tự ghi, cổng, khử trùng lặp, đường trả lại ở mọi lối, dấu bền lúc khởi động và hồ
 * sơ chưa hỗ trợ. Cơ chế: `docs/diagnostics/cluster-now-playing-2026-10-16.md` §4.A. Sink giả ghi lại MỌI lượt chạm HAL.
 */
class ClusterNowPlayingPlanTest {

    private val ids = ClusterNowPlayingIds.FW2606
    private val SRC = ids.sourceId
    private val TITLE = ids.titleId
    private val STATE = ids.stateId

    /** HAL giả: một ô nhớ theo id (đọc lại = giá trị ghi cuối, trừ khi [readable] = false). */
    private class FakeSink(var readable: Boolean = true, var markerOk: Boolean = true) : NowPlayingSink {
        val bus = HashMap<Int, Int>()
        val ops = ArrayList<String>()
        var marker: String? = null
        val lines = ArrayList<String>()
        override fun readInt(id: Int): Int? { ops += "read:$id"; return if (readable) bus[id] else null }
        override fun writeInt(id: Int, value: Int): String { ops += "int:$id=$value"; bus[id] = value; return "0" }
        override fun writeBytes(id: Int, bytes: ByteArray): String { ops += "bytes:$id=${bytes.size}"; return "0" }
        override fun saveMarker(marker: String): Boolean { ops += "marker"; if (markerOk) this.marker = marker; return markerOk }
        override fun clearMarker() { ops += "clear"; marker = null }
        override fun log(line: String) { lines += line }
        fun writes() = ops.filter { it.startsWith("int:") || it.startsWith("bytes:") }
    }

    private fun yt(title: String? = "Song A", playing: Boolean = true) = NowPlayingMedia("app.a", thirdParty = true, playing = playing, title = title)
    private val on: () -> Int? = { 2 }

    private fun armed(sink: FakeSink): ClusterNowPlayingPlan = ClusterNowPlayingPlan(ids, sink).also { it.onSwitch(true, 0) }

    @Test fun `bai moi - doc nguon, dau ben TRUOC, roi nguon - ten - trang thai, doc lai`() {
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt(), on, 1)
        assertEquals(
            listOf("read:$SRC", "marker", "int:$SRC=11", "bytes:$TITLE=12", "int:$STATE=1", "read:$SRC"),
            s.ops,
        )
        assertEquals("$SRC:26:11", s.marker, "dấu bền mang nguồn trước lúc ghi đè")
        assertEquals(NowPlayingStatus.Kind.ACTIVE, p.status.kind)
        assertTrue(s.lines.single().startsWith("np write src=0x33f00030 v=11 rc=0"), s.lines.toString())
        assertFalse(s.lines.single().contains("Song"), "không log tên bài")
    }

    @Test fun `su kien trung lap cung bai cung trang thai - 0 viec`() {
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt(), on, 1)
        val n = s.ops.size
        repeat(5) { p.onMedia(yt(), { error("không được hỏi nguồn xe") }, 2) }
        assertEquals(n, s.ops.size)
    }

    @Test fun `doi bai - ghi lai 3 luot, khong ghi dau lan nua`() {
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt("A"), on, 1)
        s.ops.clear()
        p.onMedia(yt("B"), on, 2)
        assertEquals(listOf("int:$SRC=11", "bytes:$TITLE=2", "int:$STATE=1"), s.writes())
        assertFalse("marker" in s.ops)
    }

    @Test fun `doc lai lech - ghi lai dung mot lan moi bai`() {
        val s = object : NowPlayingSink {
            val ops = ArrayList<String>()
            override fun readInt(id: Int): Int? = 26                       // MediaController "thắng" mọi lần
            override fun writeInt(id: Int, value: Int): String { ops += "int:$id=$value"; return "0" }
            override fun writeBytes(id: Int, bytes: ByteArray): String { ops += "bytes"; return "0" }
            override fun saveMarker(marker: String) = true
            override fun clearMarker() {}
            override fun log(line: String) {}
        }
        val p = ClusterNowPlayingPlan(ids, s).also { it.onSwitch(true, 0) }
        p.onMedia(yt("A"), on, 1)
        assertEquals(listOf("int:$SRC=11", "bytes", "int:$STATE=1", "int:$SRC=11", "bytes"), s.ops)
    }

    @Test fun `cat ten UTF-16LE o bien diem ma, toi da 255 byte`() {
        assertArrayEquals("Ab".toByteArray(Charsets.UTF_16LE), NowPlayingTitle.encode("Ab", 255))
        assertEquals(254, NowPlayingTitle.encode("x".repeat(300), 255).size)
        val emoji = "🎵"                                        // 🎵 = 1 điểm mã, 4 byte
        val t = "a".repeat(125) + emoji                                   // 250 + 4 = 254 ⇒ vừa
        assertEquals(254, NowPlayingTitle.encode(t, 255).size)
        val t2 = "a".repeat(126) + emoji                                  // 252 + 4 > 255 ⇒ bỏ cả cặp, không bỏ nửa
        assertArrayEquals("a".repeat(126).toByteArray(Charsets.UTF_16LE), NowPlayingTitle.encode(t2, 255))
        assertEquals("Đường xa", String(NowPlayingTitle.encode("Đường xa", 255), Charsets.UTF_16LE))
    }

    @Test fun `cong tat - khong ghi gi ca`() {
        val s = FakeSink()
        val p = ClusterNowPlayingPlan(ids, s)
        p.onMedia(yt(), on, 1)
        assertTrue(s.ops.isEmpty())
        assertEquals(NowPlayingStatus.Kind.OFF, p.status.kind)
    }

    @Test fun `ho so chua ho tro - khong ghi, bao UNSUPPORTED`() {
        val s = FakeSink()
        val p = ClusterNowPlayingPlan(null, s)
        p.onSwitch(true, 0)
        p.onMedia(yt(), on, 1)
        assertTrue(s.ops.isEmpty())
        assertEquals(NowPlayingStatus.Kind.UNSUPPORTED, p.status.kind)
    }

    @Test fun `app he thong, khong ten, xe tat, nguon da hien - khong ghi`() {
        val s = FakeSink().apply { bus[SRC] = 6 }
        val p = armed(s)
        p.onMedia(NowPlayingMedia("app.sys", thirdParty = false, playing = true, title = "x"), on, 1)
        assertEquals(NowPlayingStatus.Kind.SYSTEM_APP, p.status.kind)
        p.onMedia(yt(title = " "), on, 2)
        assertEquals(NowPlayingStatus.Kind.NO_TITLE, p.status.kind)
        p.onMedia(yt("P"), { 1 }, 3)
        assertEquals(NowPlayingStatus.Kind.POWER_OFF, p.status.kind)
        p.onMedia(yt("Q"), on, 4)
        assertEquals(NowPlayingStatus.Kind.ALREADY_SHOWN, p.status.kind, "BT (6) đã hiện — không đụng")
        assertTrue(s.writes().isEmpty(), s.ops.toString())
    }

    @Test fun `xe khong doc duoc nguon xe - van ghi, tra ve 26`() {
        val s = FakeSink(readable = false)
        val p = armed(s)
        p.onMedia(yt(), { null }, 1)
        assertEquals("$SRC:26:11", s.marker)
        assertEquals(listOf("int:$SRC=11", "bytes:$TITLE=12", "int:$STATE=1"), s.writes())
    }

    @Test fun `dau ben ghi hong - khong ghi de`() {
        val s = FakeSink(markerOk = false).apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt(), on, 1)
        assertTrue(s.writes().isEmpty())
        assertEquals(NowPlayingStatus.Kind.MARKER_FAILED, p.status.kind)
    }

    @Test fun `tam dung - ghi trang thai 2, het an han thi tra nguon`() {
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt(), on, 0)
        s.ops.clear()
        assertEquals(ClusterNowPlayingPlan.PAUSE_RESTORE_MS, p.onMedia(yt(playing = false), on, 1_000))
        assertEquals(listOf("int:$STATE=2"), s.writes())
        p.onPauseCheck(1_000 + ClusterNowPlayingPlan.PAUSE_RESTORE_MS - 1)
        assertEquals(listOf("int:$STATE=2"), s.writes(), "chưa đủ ân hạn")
        p.onPauseCheck(1_000 + ClusterNowPlayingPlan.PAUSE_RESTORE_MS)
        assertEquals(listOf("int:$STATE=2", "int:$SRC=26"), s.writes())
        assertNull(s.marker)
    }

    /** Soát Pass 12 [P1]: sự kiện dừng lặp lại không được hẹn lại (bên gọi hẹn lại từ đầu mỗi khi nhận độ trễ ⇒ mốc trả bị dời mãi). */
    @Test fun `tam dung lap lai - khong hen lai, van tra dung moc dau`() {
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt(), on, 0)
        assertEquals(ClusterNowPlayingPlan.PAUSE_RESTORE_MS, p.onMedia(yt(playing = false), on, 1_000))
        repeat(3) { i -> assertNull(p.onMedia(yt(playing = false), on, 2_000 + i * 5_000L), "sự kiện dừng lặp lại không hẹn lại") }
        s.ops.clear()
        p.onPauseCheck(1_000 + ClusterNowPlayingPlan.PAUSE_RESTORE_MS)
        assertEquals(listOf("int:$SRC=26"), s.writes(), "trả nguồn đúng mốc của lượt dừng ĐẦU")
        assertNull(s.marker)
        // Dừng → phát → dừng: quãng dừng mới ⇒ hẹn lại một lượt.
        p.onMedia(yt(), on, 30_000)
        assertEquals(ClusterNowPlayingPlan.PAUSE_RESTORE_MS, p.onMedia(yt(playing = false), on, 31_000))
    }

    /** Soát Pass 12 [P2]: BẬT xong tình trạng là "đang chờ" (không phải OFF) — cổng NO_ACCESS ở `:app` và dòng Cài đặt dựa vào đó. */
    @Test fun `bat cong tac - tinh trang dang cho, khong phai OFF`() {
        assertEquals(NowPlayingStatus.Kind.NO_MEDIA, armed(FakeSink()).status.kind)
        assertEquals(NowPlayingStatus.Kind.OFF, ClusterNowPlayingPlan(ids, FakeSink()).status.kind)
    }

    /** Soát Pass 12 [P2]: xe OFF/ACC ⇒ bài đó đã xét; sự kiện trùng không được hỏi nguồn xe (một lượt đọc HAL) nữa. */
    @Test fun `xe tat - su kien trung khong doc lai nguon xe`() {
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt("A"), { 1 }, 1)
        assertEquals(NowPlayingStatus.Kind.POWER_OFF, p.status.kind)
        repeat(3) { p.onMedia(yt("A"), { error("không được hỏi nguồn xe lần nữa") }, 2) }
        assertTrue(s.writes().isEmpty(), s.ops.toString())
        p.onMedia(yt("B"), on, 3)                                            // đổi bài ⇒ xét lại ⇒ xe ON ⇒ ghi
        assertEquals(NowPlayingStatus.Kind.ACTIVE, p.status.kind)
    }

    @Test fun `tam dung roi phat lai cung bai - chi ghi trang thai 1, khong tra`() {
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt(), on, 0)
        p.onMedia(yt(playing = false), on, 1_000)
        s.ops.clear()
        p.onMedia(yt(), on, 2_000)
        p.onPauseCheck(1_000 + ClusterNowPlayingPlan.PAUSE_RESTORE_MS)
        assertEquals(listOf("int:$STATE=1"), s.writes())
        assertEquals("$SRC:26:11", s.marker)
    }

    @Test fun `tra lai o moi loi - cong tac, dung, het phien, xe tat`() {
        listOf<(ClusterNowPlayingPlan) -> Unit>(
            { it.onSwitch(false, 5) },
            { it.stop(5) },
            { it.onMedia(null, on, 5) },
        ).forEach { exit ->
            val s = FakeSink().apply { bus[SRC] = 26 }
            val p = armed(s)
            p.onMedia(yt(), on, 1)
            exit(p)
            assertEquals(26, s.bus[SRC], s.ops.toString())
            assertNull(s.marker)
        }
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt("A"), on, 1)
        p.onMedia(yt("B"), { 0 }, 2)
        assertEquals(26, s.bus[SRC]); assertNull(s.marker)
        assertEquals(NowPlayingStatus.Kind.POWER_OFF, p.status.kind)
    }

    @Test fun `phien sang app he thong - KHONG ghi de nguon cua MediaController`() {
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt(), on, 1)
        s.ops.clear()
        p.onMedia(NowPlayingMedia("app.sys", thirdParty = false, playing = true, title = "x"), on, 2)
        assertTrue(s.writes().isEmpty(), s.ops.toString())
        assertNull(s.marker)
    }

    @Test fun `tra lai thay nguon nguoi khac da ghi - giu cua ho`() {
        val s = FakeSink().apply { bus[SRC] = 26 }
        val p = armed(s)
        p.onMedia(yt(), on, 1)
        s.bus[SRC] = 1                                                     // MediaController ghi nguồn mới
        s.ops.clear()
        p.onSwitch(false, 2)
        assertTrue(s.writes().isEmpty())
        assertEquals(1, s.bus[SRC])
    }

    @Test fun `khoi dong - dau ben con thi tra va don`() {
        val s = FakeSink().apply { bus[SRC] = 11; marker = "$SRC:26:11" }
        ClusterNowPlayingPlan.restoreAtStartup(s.marker, s)
        assertEquals(26, s.bus[SRC]); assertNull(s.marker)

        val other = FakeSink().apply { bus[SRC] = 6; marker = "$SRC:26:11" }
        ClusterNowPlayingPlan.restoreAtStartup(other.marker, other)
        assertEquals(6, other.bus[SRC], "người khác đã ghi — giữ"); assertNull(other.marker)

        val blind = FakeSink(readable = false).apply { marker = "$SRC:26:11" }
        ClusterNowPlayingPlan.restoreAtStartup(blind.marker, blind)
        assertTrue(blind.writes().isEmpty(), "không đọc được ⇒ không ghi mù"); assertNull(blind.marker)

        val bad = FakeSink().apply { marker = "rac" }
        ClusterNowPlayingPlan.restoreAtStartup(bad.marker, bad)
        assertTrue(bad.writes().isEmpty()); assertNull(bad.marker)

        assertNull(ClusterNowPlayingPlan.restoreAtStartup(null, FakeSink()))
    }

    @Test fun `phep quyet app ngoai he thong la generic`() {
        assertTrue(NowPlayingOwner.thirdParty(systemImage = false, updatedSystem = false, platformSigned = false, uid = 10_123))
        assertTrue(NowPlayingOwner.thirdParty(systemImage = true, updatedSystem = true, platformSigned = false, uid = 10_050), "app cài sẵn đã cập nhật")
        assertFalse(NowPlayingOwner.thirdParty(systemImage = true, updatedSystem = false, platformSigned = false, uid = 10_050))
        assertFalse(NowPlayingOwner.thirdParty(systemImage = false, updatedSystem = false, platformSigned = true, uid = 10_050))
        assertFalse(NowPlayingOwner.thirdParty(systemImage = false, updatedSystem = false, platformSigned = false, uid = 1000))
    }

    @Test fun `so lieu fw2606`() {
        assertEquals(0x33F00030, ids.sourceId); assertEquals(0x43FB1008, ids.titleId); assertEquals(0x43E0000A, ids.stateId)
        assertEquals(11, ids.overrideSource); assertEquals(26, ids.restoreFallback)
        assertTrue(ids.shown(11)); assertFalse(ids.shown(26)); assertFalse(ids.shown(8)); assertTrue(ids.shown(171))
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 22, 29, 30, 171), ids.shownSources)
        assertEquals(NowPlayingMarker.Marker(SRC, 26, 11), NowPlayingMarker.decode(NowPlayingMarker.encode(SRC, 26, 11)))
    }
}
