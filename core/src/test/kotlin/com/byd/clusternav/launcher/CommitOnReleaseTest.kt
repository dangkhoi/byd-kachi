package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 2.88 · R-OP — thanh kéo "Độ trong suốt nền" áp khi THẢ tay ([CommitOnRelease]). Khoá (soát Pass 10 [P3]): đường áp
 * ghi prefs theo hồ sơ + dựng lại thanh nút, nên áp theo từng nấc kéo là tới 20 lượt dựng lại trên GPU yếu của đầu xe.
 * Chuỗi sự kiện mô phỏng đúng thứ tự `AbsSeekBar` r47 gọi (KDoc lớp).
 */
class CommitOnReleaseTest {

    /** Áp mọi kết quả khác `null` vào [out] — đúng việc `sliderRow` làm với `onCommit`. */
    private fun Int?.into(out: MutableList<Int>) { this?.let(out::add) }

    @Test
    fun `keo 0 toi 9 chi ap mot lan luc tha tay`() {
        val g = CommitOnRelease(0)
        val out = mutableListOf<Int>()
        g.onStart()
        (1..9).forEach { g.onChange(it, fromUser = true).into(out) }
        assertEquals(emptyList<Int>(), out, "trong lúc kéo không áp nấc nào")
        g.onStop(9).into(out)
        assertEquals(listOf(9), out)
        assertFalse(g.tracking)
        assertEquals(9, g.committed)
    }

    @Test
    fun `cham mot cai la mot lan ap`() {
        val g = CommitOnRelease(4)
        val out = mutableListOf<Int>()
        // Chạm-không-kéo trong vùng cuộn (Cài đặt là ScrollView): start → một nấc → stop.
        g.onStart(); g.onChange(12, fromUser = true).into(out); g.onStop(12).into(out)
        assertEquals(listOf(12), out)
    }

    @Test
    fun `huy giua luot keo van ap vi tri dang dung`() {
        val g = CommitOnRelease(0)
        val out = mutableListOf<Int>()
        g.onStart(); g.onChange(3, fromUser = true).into(out)
        g.onStop(3).into(out)   // ACTION_CANCEL khi đang kéo cũng gọi onStopTrackingTouch
        assertEquals(listOf(3), out)
    }

    @Test
    fun `keo roi tra ve cho cu khong ap gi`() {
        val g = CommitOnRelease(6)
        g.onStart()
        listOf(7, 8, 7, 6).forEach { assertNull(g.onChange(it, fromUser = true)) }
        assertNull(g.onStop(6), "về đúng vị trí đang lưu ⇒ không ghi prefs, không dựng lại")
        assertNull(CommitOnRelease(6).let { it.onStart(); it.onStop(6) }, "chạm lại đúng chỗ cũ ⇒ không ghi gì")
    }

    @Test
    fun `doi so do ma khong bao gio ap`() {
        val g = CommitOnRelease(0)
        (0..20).forEach { assertNull(g.onChange(it, fromUser = false)) }
        g.onStart()
        assertNull(g.onChange(5, fromUser = false))
        assertEquals(0, g.committed)
    }

    @Test
    fun `phim va tro nang ap ngay tung nac`() {
        val g = CommitOnRelease(10)
        val out = mutableListOf<Int>()
        // Không qua start/stop: mỗi lần đổi số là một lần áp; trùng vị trí đã áp thì bỏ.
        listOf(11, 12, 12, 11).forEach { g.onChange(it, fromUser = true).into(out) }
        assertEquals(listOf(11, 12, 11), out)
        // Lượt chạm sau đó vẫn theo luật thả tay, và so với lần áp CUỐI (của phím).
        g.onStart(); g.onChange(15, fromUser = true).into(out)
        assertEquals(listOf(11, 12, 11), out)
        g.onStop(11).into(out)
        assertEquals(listOf(11, 12, 11), out, "thả đúng chỗ phím vừa áp ⇒ không áp lại")
    }
}
