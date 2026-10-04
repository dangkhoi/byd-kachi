package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.behind.BehindHomeSequence.Outcome
import com.byd.clusternav.launcher.behind.BehindHomeSequence.Result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Lỗi xe 2.87 (owner 04/10) — lý do ngắn của một lượt BEHIND-HOME ([BehindReason]) cho câu báo trên ảnh chụp (CLAUDE.md §11).
 *
 * Các ca chạy CHUỖI THẬT ở `BehindHomeSlotCoverTest.ly do ngan cho anh chup…`. Ở đây chỉ những dạng dòng mà chuỗi `:core`
 * không tự in được off-device: dòng của bên thi hành `:app` (`BehindHomeRunner.runOnce` / `failed` — mũi tên ASCII `->`,
 * chép đúng mẫu chuỗi ở đó) và các dạng `moveBehind` cần ROM làm hỏng giữa chừng (giữ chỗ không lên, dấu bền hỏng, đọc lại
 * sau move-task hỏng, move-task không ăn). Mỗi ca khoá: một ảnh chụp đủ biết chuỗi dừng ở BƯỚC nào.
 */
class BehindReasonTest {

    private val tag = "slot-back vd=295 A=com.google.android.youtube"
    private val evict = "evict vd=295 A=com.google.android.youtube B=com.byd.launcher"

    private fun short(r: Result, line: String) = BehindReason.short(Outcome(r, line))

    @Test
    fun `dong cua ben thi hanh - mui ten ASCII, bo duoi 0 cmd`() {
        val what = "slot-back slot=0 X=com.google.android.youtube"
        assertEquals("DISABLED · disabled (anchor-in-front)", short(Result.DISABLED, "$what -> disabled (anchor-in-front), 0 cmd"))
        assertEquals("NO_CHANNEL · no channel", short(Result.NO_CHANNEL, "$what -> no channel, 0 cmd"))
        assertEquals("NO_CHANNEL · IOException", short(Result.NO_CHANNEL, "$what -> NO_CHANNEL (IOException) anchors=0"))
        assertEquals("KEPT_UNDER · IllegalStateException", short(Result.KEPT_UNDER, "$what -> KEPT_UNDER (IllegalStateException) anchors=-1"))
    }

    @Test
    fun `dong moveBehind qua afterStage - lay dung cho hong, khong lay so dem`() {
        fun line(inner: String) = "$tag chờ=0ms · $evict → $inner"
        assertEquals("KEPT_UNDER · A_ON_TOP", short(Result.KEPT_UNDER, line("KEPT_UNDER (A_ON_TOP) đọc=4 dọn=0")))
        assertEquals("KEPT_UNDER · giữ chỗ Missing", short(Result.KEPT_UNDER, line("KEPT_UNDER (giữ chỗ Missing) gỡ=0")))
        assertEquals("ANCHOR_IN_FRONT · giữ chỗ InFront", short(Result.ANCHOR_IN_FRONT, line("ANCHOR_IN_FRONT (giữ chỗ InFront) gỡ=1")))
        assertEquals("KEPT_UNDER · ghi dấu bền hỏng — không đẩy", short(Result.KEPT_UNDER, line("KEPT_UNDER (ghi dấu bền hỏng — không đẩy) gỡ=1")))
        assertEquals("KEPT_UNDER · đọc lại trước lệnh: Stop(why=A_ON_TOP)",
            short(Result.KEPT_UNDER, line("KEPT_UNDER (đọc lại trước lệnh: Stop(why=A_ON_TOP)) gỡ=1")), "ngoặc lồng")
        assertEquals("UNREAD · đọc lại sau move-task hỏng — giữ dấu",
            short(Result.UNREAD, line("UNREAD (đọc lại sau move-task hỏng — giữ dấu) task=3380 S=745 gỡ=1 dọn-trước=0")))
        assertEquals("KEPT_UNDER · kiểm=NOT_MOVED",
            short(Result.KEPT_UNDER, line("KEPT_UNDER task=3380 S=745 top0=740 kiểm=NOT_MOVED gỡ=1 dọn-trước=0")), "move-task không ăn")
        assertEquals("KEPT_UNDER · không mở được giữ chỗ", short(Result.KEPT_UNDER, line("không mở được giữ chỗ")))
    }

    @Test
    fun `duoi ket luan co ⇒ thang moi mui ten phia truoc`() {
        val unread = "$tag chờ=0ms · $evict → MOVED task=3380 S=745 top0=740 kiểm=OK gỡ=1 dọn-trước=0 · " +
            "đọc lại hỏng ⇒ chưa rõ X có lên trước màn nhà, 0 dấu 0 K12"
        assertEquals("UNREAD · đọc lại hỏng ⇒ chưa rõ X có lên trước màn nhà, 0 dấu 0 K12", short(Result.UNREAD, unread))
    }

    @Test
    fun `dong la hoac qua dai - chi ma, hoac cat trong tran`() {
        assertEquals("KEPT_UNDER", short(Result.KEPT_UNDER, "dong khong co mui ten"))
        assertEquals("TIMEOUT", short(Result.TIMEOUT, ""))
        val long = short(Result.KEPT_UNDER, "$tag → " + "lý do rất dài ".repeat(20))
        assertTrue(long.length <= BehindReason.MAX && long.endsWith("…") && long.startsWith("KEPT_UNDER · lý do"), long)
    }

    @Test
    fun `report giu ket qua roi o cua chuoi va dong day du`() {
        val out = Outcome(Result.X_FRONT_HOME_RESTORED, "$tag chờ=0ms · $evict → MOVED task=1 S=2 top0=3 kiểm=OK gỡ=1 dọn-trước=0 · X lên trước màn nhà → dấu=1 K12")
        val r = BehindReason.report(out)
        assertTrue(r.left, "X_FRONT_HOME_RESTORED = đã ra khỏi ô (outOfStage)")
        assertEquals(out.line, r.line)
        assertEquals(false, BehindReason.report(Outcome(Result.KEPT_UNDER, "$tag → x")).left)
    }
}
