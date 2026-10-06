package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraDemand.Op
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · CAMERA-ON-DEMAND — trạng thái RAM của controller + hai lối dịch lệnh (spec `kachi-293-cam.html` R1) ═══════
 *
 * Luật chọn đã khoá ở [CameraDemandTest]; bài này khoá phần controller DÙNG luật ấy: chuỗi sự kiện thật (bấm · xi-nhan ·
 * hết xi-nhan · nói) ra đúng camera, cờ *"mới hơn"* đặt/hạ đúng chỗ, và người nghe (nút *Xem thử* ở Cài đặt) không làm
 * hỏng nhau. Không một hằng thời gian nào (owner *"không nên timeout"*).
 */
class CameraDemandStateTest {

    private val rear = CameraWhich.REAR
    private val left = CameraWhich.LEFT
    private val right = CameraWhich.RIGHT

    @Test fun `nut thanh nut va phim - cham la bat tat, null la tat het`() {
        CameraWhich.ALL.forEach { assertEquals(Op.Toggle(it), CameraDemand.tap(it)) }
        assertEquals(Op.CloseAll, CameraDemand.tap(null), "ô *Tắt camera* (không camera) ⇒ tắt hết")
    }

    /**
     * Điều phối 2.93 (owner 06/10): câu MỞ là bật/tắt — *"mở cam trái"* lần hai thì TẮT (một camera một lúc vẫn giữ); câu
     * TẮT chỉ tắt đúng camera ấy, không bao giờ mở.
     */
    @Test fun `giong noi - cau mo la bat tat, cau tat chi tat`() {
        CameraWhich.ALL.forEach {
            assertEquals(Op.Toggle(it), CameraDemand.spoken(it, off = false), "*mở camera X* = bật/tắt X")
            assertEquals(Op.Close(it), CameraDemand.spoken(it, off = true), "*tắt camera X* chỉ tắt đúng X")
        }
        val s = CameraDemandState()
        s.apply(CameraDemand.spoken(left, off = false))
        assertEquals(left, s.current, "nói lần một ⇒ mở")
        s.apply(CameraDemand.spoken(left, off = false), visible = s.shown(null))
        assertNull(s.current, "nói lại đúng câu ấy ⇒ tắt")
        s.apply(CameraDemand.spoken(left, off = true))
        assertNull(s.current, "*tắt cam trái* khi không mở ⇒ vẫn tắt, không mở nhầm")
    }

    /** Chuỗi một chuyến: bấm camera sau → xi-nhan trái chen → hết xi-nhan ⇒ camera sau quay lại → bấm lại ⇒ tắt. */
    @Test fun `mot chuyen that - xi nhan chen roi tra lai camera theo yeu cau`() {
        val s = CameraDemandState()
        assertNull(s.shown(null), "khởi đầu: không gì")
        assertNull(s.apply(Op.Toggle(rear)), "trả camera TRƯỚC lệnh (cho log)")
        assertEquals(rear, s.current)
        assertTrue(s.newer)
        assertEquals(rear, s.shown(null))
        // Xi-nhan trái BẬT — controller hạ cờ (sự kiện mới nhất là xi-nhan).
        s.newer = false
        assertEquals(left, s.shown(left), "camera xi-nhan hiện như cũ")
        assertEquals(rear, s.current, "camera theo yêu cầu vẫn ĐANG BẬT (chỉ bị che)")
        // Hết xi-nhan ⇒ camera sau quay lại, không ai phải bấm gì.
        assertEquals(rear, s.shown(null))
        // Bấm lại camera sau ⇒ tắt.
        assertEquals(rear, s.apply(Op.Toggle(rear)))
        assertNull(s.current)
        assertNull(s.shown(null))
    }

    /** Đang có xi-nhan mà bấm nút ⇒ nút thắng (sự kiện mới nhất); tắt camera theo yêu cầu ⇒ còn camera xi-nhan. */
    @Test fun `bam giua luc xi nhan - nut thang, tat thi con xi nhan`() {
        val s = CameraDemandState()
        s.newer = false
        assertEquals(right, s.shown(right))
        s.apply(Op.Toggle(rear))
        assertEquals(rear, s.shown(right), "bấm SAU khi xi-nhan bật ⇒ camera bấm thắng")
        s.apply(Op.CloseAll)
        assertEquals(right, s.shown(right), "tắt camera theo yêu cầu khi xi-nhan còn ⇒ về camera xi-nhan")
        // Camera khác thay camera đang mở (owner *"(a) đi"*).
        s.apply(Op.Toggle(left))
        s.apply(Op.Toggle(CameraWhich.FRONT))
        assertEquals(CameraWhich.FRONT, s.current)
    }

    /** Soát 2.93 — camera theo yêu cầu đang bị xi-nhan che: bấm nút của nó (phím/thanh nút) ⇒ ĐƯA LÊN, bấm lần nữa ⇒ tắt. */
    @Test fun `bam nut khi dang bi xi nhan che - dua len roi moi tat`() {
        val s = CameraDemandState()
        s.apply(Op.Toggle(rear))
        s.newer = false   // xi-nhan trái bật
        val visible = s.shown(left)
        assertEquals(left, visible)
        assertEquals(rear, s.apply(Op.Toggle(rear), visible = visible))
        assertEquals(rear, s.current, "không tắt thứ người lái đang không thấy")
        assertEquals(rear, s.shown(left), "đưa lên trên camera xi-nhan")
        assertEquals(rear, s.apply(Op.Toggle(rear), visible = s.shown(left)))
        assertNull(s.current, "đang hiện ⇒ bấm lại là tắt")
        assertEquals(left, s.shown(left), "còn camera xi-nhan")
    }

    /**
     * Soát senior 2.93 [P1] — lệnh TẮT TRƯỢT (camera ấy không mở) KHÔNG phải *"sự kiện mới nhất"*: dựng cờ thì camera theo
     * yêu cầu KHÁC đang bị xi-nhan che nhảy lên đè camera điểm mù giữa lúc rẽ. Lệnh MỞ (kể cả lượt đưa lên) vẫn thắng.
     */
    @Test fun `lenh tat truot khong dua camera theo yeu cau len de camera xi nhan`() {
        val s = CameraDemandState()
        s.apply(Op.Toggle(left))   // camera trái theo yêu cầu đang mở
        s.newer = false            // xi-nhan PHẢI bật ⇒ controller hạ cờ, camera phải hiện
        assertEquals(right, s.shown(right))
        assertEquals(left, s.apply(Op.Close(rear), visible = s.shown(right)), "*tắt cam sau* — camera sau không mở")
        assertEquals(left, s.current, "lệnh trượt không đổi camera theo yêu cầu")
        assertFalse(s.newer, "lệnh trượt không phải sự kiện mới nhất")
        assertEquals(right, s.shown(right), "camera điểm mù bên phải vẫn hiện giữa lúc rẽ")
        s.apply(Op.CloseAll, visible = s.shown(right))   // *"tắt cam"* — tắt THẬT camera trái
        assertNull(s.current)
        assertEquals(right, s.shown(right))
        // Lệnh MỞ vẫn là sự kiện mới nhất (đưa lên trên camera xi-nhan) — luật cũ giữ nguyên.
        s.newer = false
        s.apply(Op.Toggle(rear), visible = s.shown(right))
        assertTrue(s.newer)
        assertEquals(rear, s.shown(right))
    }

    @Test fun `nguoi nghe - nhan dung trang thai, go duoc, mot nguoi nem khong chan ai`() {
        val s = CameraDemandState()
        val got = mutableListOf<CameraWhich?>()
        val off1 = s.listen { got += it }
        s.listen { error("người nghe hỏng") }
        var selfRemoving: () -> Unit = {}
        var selfCalls = 0
        selfRemoving = s.listen { selfCalls++; selfRemoving() }   // tự gỡ trong lúc nhận — không được ném CME
        s.apply(Op.Toggle(rear)); s.notifyChanged()
        s.apply(Op.Toggle(rear)); s.notifyChanged()
        assertEquals(listOf(rear, null), got, "người nghe sau người ném vẫn nhận đủ")
        assertEquals(1, selfCalls, "đã tự gỡ ⇒ không nhận lần hai")
        off1()
        s.apply(Op.Toggle(left)); s.notifyChanged()
        assertEquals(listOf(rear, null), got, "đã gỡ ⇒ không nhận nữa")
        assertFalse(got.contains(left))
    }
}
