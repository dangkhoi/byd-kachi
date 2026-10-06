package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.trip.TripGate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 · BEHIND-MARKS-BOOT (spec `docs/specs/kachi-293-slot.html` R5) — dấu bền "task Kachi đẩy ra sau màn nhà" chỉ có nghĩa
 * trong lần khởi động đã ghi nó. Khoá lỗi [SUY review lượt 7, spec autostart §10 Pass 8]: id task trên xe bắt đầu lại từ số nhỏ
 * sau khởi động lại thật ([ĐO dump xe 14/09: taskId 4–10]) ⇒ dấu `7:com.app` của đời máy trước trùng đúng app vừa mở có id 7 ⇒
 * lượt trả lại bắn K12 che nó.
 */
class BehindMarksBootTest {

    private val marks = linkedMapOf(7 to "com.google.android.apps.youtube.music", 9 to "vn.vietmap.live")

    @Test
    fun `dau cua lan khoi dong khac bi bo - cung lan khoi dong thi giu`() {
        val raw = BehindMarks.encode(marks, "n38")
        assertEquals("@n38,7:com.google.android.apps.youtube.music,9:vn.vietmap.live", raw)
        assertEquals(emptyMap<Int, String>(), BehindMarks.forBoot(raw, "n39"), "đời máy trước ⇒ không dấu nào dùng được")
        assertEquals(marks, BehindMarks.forBoot(raw, "n38"), "cùng lần khởi động ⇒ đủ dấu")
        // Ca lỗi: task id 7 của đời máy mới trùng gói ⇒ bản ≤ 2.92 coi là "của Kachi"; nay không.
        assertTrue(BehindMarks.forBoot(raw, "n39")[7] == null)
    }

    @Test
    fun `chuoi cua ban cu khong khoa thi giu nhu cu, ban cu doc chuoi moi van dung`() {
        val legacy = BehindMarks.encode(marks)
        assertEquals("7:com.google.android.apps.youtube.music,9:vn.vietmap.live", legacy, "không khoá = byte của bản ≤ 2.92")
        assertNull(BehindMarks.bootOf(legacy))
        assertEquals(marks, BehindMarks.forBoot(legacy, "n39"), "không biết lần khởi động ghi ⇒ giữ (không biết thì không bỏ)")
        // Hạ cấp: bản ≤ 2.92 chỉ có `decode` — mục `@n38` không có ':' ⇒ rơi vào nhánh rác, dấu còn nguyên.
        assertEquals(marks, BehindMarks.decode(BehindMarks.encode(marks, "n38")))
    }

    @Test
    fun `khoa gio tuong khong du de bo dau - gio tuong bi chinh giua phien`() {
        assertFalse(TripGate.stableBoot("w29012345"))
        assertTrue(TripGate.stableBoot("n39"))
        assertFalse(TripGate.stableBoot("n"), "thiếu số")
        assertFalse(TripGate.stableBoot("nx"))
        val wall = BehindMarks.encode(marks, "w29012345")
        assertEquals(marks, BehindMarks.forBoot(wall, "n39"), "ghi bằng khoá lùi ⇒ giữ")
        assertEquals(marks, BehindMarks.forBoot(BehindMarks.encode(marks, "n38"), "w29012346"), "đọc bằng khoá lùi ⇒ giữ")
        assertEquals(TripGate.bootKey(39, 0L, 0L), "n39", "khoá đọc ở kho = khoá của sổ chuyến")
    }

    @Test
    fun `khoa la khong vao chuoi, chuoi hong khong nem`() {
        assertEquals(BehindMarks.encode(marks), BehindMarks.encode(marks, "n3,9:x"), "khoá có ',' / ':' ⇒ không ghi khoá")
        assertEquals(BehindMarks.encode(marks), BehindMarks.encode(marks, ""))
        assertNull(BehindMarks.bootOf("@n3 9,7:a.b"), "khoá lạ trên đĩa ⇒ coi như không khoá")
        assertEquals(mapOf(7 to "a.b"), BehindMarks.forBoot("@n3 9,7:a.b", "n40"))
        assertEquals(emptyMap<Int, String>(), BehindMarks.forBoot(null, "n39"))
        assertEquals(emptyMap<Int, String>(), BehindMarks.forBoot("@n38", "n38"), "chỉ có khoá, không dấu")
        assertEquals(mapOf(7 to "a.b"), BehindMarks.forBoot("7:a.b,@n38", "n39"), "khoá chỉ đọc ở mục ĐẦU; mục giữa là rác")
    }
}
