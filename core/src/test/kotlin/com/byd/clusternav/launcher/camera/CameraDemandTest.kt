package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraDemand.Op
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · CAMERA-ON-DEMAND — máy trạng thái + mã đích (spec `docs/specs/kachi-293-cam.html` R1) ═══════════════════
 *
 * Khoá ba lời owner 06/10: *"các nút đều là toggle"* · *"(a) đi"* (một camera, khác ⇒ thay, cùng ⇒ tắt) · *"không nên
 * timeout"* (không có hằng thời gian nào), và luật *"camera xi-nhan vẫn hiện như cũ"* (sự kiện mới nhất thắng).
 */
class CameraDemandTest {

    private val all = CameraWhich.ALL

    @Test fun `bon camera dung thu tu dai sau trai phai truoc`() {
        assertEquals(listOf(0, 1, 2, 3), all.map { it.strip }, "[ĐO 27/09 + 28/09] sau · trái · phải · trước")
        assertEquals(CameraPanoCrop.defaultStrip(left = true), CameraWhich.LEFT.strip, "trùng dải của camera xi-nhan trái")
        assertEquals(CameraPanoCrop.defaultStrip(left = false), CameraWhich.RIGHT.strip)
        assertEquals(listOf("rear", "left", "right", "front"), all.map { it.code })
        assertEquals(all.size, all.map { it.code }.toSet().size, "mã không trùng")
        all.forEach { assertEquals(it, CameraWhich.ofCode(it.code.uppercase() + " ")) }
        assertNull(CameraWhich.ofCode("off"))
        assertNull(CameraWhich.ofCode(""))
        assertNull(CameraWhich.ofCode(null))
    }

    @Test fun `dau dich x theo camera - guong doi xung, camera giua khong dich`() {
        assertEquals(1, CameraWhich.LEFT.panXSign)
        assertEquals(-1, CameraWhich.RIGHT.panXSign)
        assertEquals(0, CameraWhich.REAR.panXSign)
        assertEquals(0, CameraWhich.FRONT.panXSign)
        assertTrue(CameraWhich.LEFT.side && CameraWhich.RIGHT.side && !CameraWhich.REAR.side && !CameraWhich.FRONT.side)
        assertEquals(CameraWhich.LEFT, CameraWhich.ofTurn(CameraSignalPolicy.Turn.LEFT))
        assertEquals(CameraWhich.RIGHT, CameraWhich.ofTurn(CameraSignalPolicy.Turn.RIGHT))
        assertNull(CameraWhich.ofTurn(CameraSignalPolicy.Turn.NONE))
        all.filter { it.side }.forEach { assertEquals(it, CameraWhich.ofTurn(it.turn)) }
    }

    /** Owner *"(a) đi"*: nút = toggle; camera khác ⇒ THAY; cùng camera ⇒ TẮT. */
    @Test fun `nut bat tat - cung camera thi tat, camera khac thi thay`() {
        all.forEach { w ->
            assertEquals(w, CameraDemand.next(null, Op.Toggle(w)), "chưa mở ⇒ mở $w")
            assertNull(CameraDemand.next(w, Op.Toggle(w)), "bấm lại đúng $w ⇒ tắt")
            all.filter { it != w }.forEach { o -> assertEquals(o, CameraDemand.next(w, Op.Toggle(o)), "$w đang mở, bấm $o ⇒ thay") }
        }
    }

    /**
     * Soát 2.93 — nút là toggle theo thứ người lái THẤY: camera theo yêu cầu đang bị camera xi-nhan CHE thì bấm nút của nó
     * là ĐƯA LÊN, không tắt (tắt thứ không thấy = nút "chết"); đang hiện thì bấm là tắt như thường.
     */
    @Test fun `nut bat tat theo thu dang hien - bi xi nhan che thi dua len, khong tat`() {
        val rear = CameraWhich.REAR
        val left = CameraWhich.LEFT
        assertEquals(rear, CameraDemand.next(rear, Op.Toggle(rear), visible = left), "bị xi-nhan trái che ⇒ bấm *Camera sau* = đưa lên")
        assertNull(CameraDemand.next(rear, Op.Toggle(rear), visible = rear), "đang hiện ⇒ bấm lại = tắt")
        assertEquals(left, CameraDemand.next(null, Op.Toggle(left), visible = left), "xi-nhan trái đang hiện, bấm *Camera trái* ⇒ giữ lại sau xi-nhan")
        assertNull(CameraDemand.next(left, Op.Toggle(left), visible = left))
        // Giọng nói + *Tắt camera* không đổi nghĩa theo thứ đang hiện (động từ đã nói rõ).
        assertNull(CameraDemand.next(rear, Op.Close(rear), visible = left), "*tắt camera sau* tắt cả khi đang bị che")
        assertEquals(rear, CameraDemand.next(rear, Op.Open(rear), visible = left))
        assertNull(CameraDemand.next(rear, Op.CloseAll, visible = left))
    }

    /**
     * [Op.Open] (cầu kiểm thử `open:` — câu MỞ của giọng nói từ Pass 1 là [Op.Toggle], xem `CameraDemandStateTest`): mở thì
     * luôn mở (lặp lại không tắt). [Op.Close] (câu TẮT của giọng nói): chỉ tắt đúng camera ấy.
     */
    @Test fun `giong noi - mo khong bao gio tat, tat khong tat nham camera khac`() {
        all.forEach { w ->
            assertEquals(w, CameraDemand.next(null, Op.Open(w)))
            assertEquals(w, CameraDemand.next(w, Op.Open(w)), "*mở camera X* lần hai KHÔNG tắt X")
            assertNull(CameraDemand.next(w, Op.Close(w)))
            assertNull(CameraDemand.next(null, Op.Close(w)))
            all.filter { it != w }.forEach { o ->
                assertEquals(o, CameraDemand.next(o, Op.Close(w)), "*tắt camera $w* khi đang xem $o ⇒ giữ $o")
                assertEquals(w, CameraDemand.next(o, Op.Open(w)), "*mở camera $w* ⇒ thay $o")
            }
            assertNull(CameraDemand.next(w, Op.CloseAll))
        }
        assertNull(CameraDemand.next(null, Op.CloseAll))
    }

    /** Camera xi-nhan vẫn chạy như cũ: xi-nhan mới hơn ⇒ camera xi-nhan; hết xi-nhan ⇒ camera theo yêu cầu quay lại. */
    @Test fun `su kien moi nhat thang, het xi nhan thi camera theo yeu cau quay lai`() {
        val l = CameraWhich.LEFT
        val r = CameraWhich.REAR
        assertEquals(l, CameraDemand.shown(blinker = l, demand = r, demandNewer = false), "xi-nhan bật SAU ⇒ camera xi-nhan")
        assertEquals(r, CameraDemand.shown(blinker = l, demand = r, demandNewer = true), "bấm nút SAU xi-nhan ⇒ nút thắng")
        assertEquals(r, CameraDemand.shown(blinker = null, demand = r, demandNewer = false), "hết xi-nhan ⇒ quay lại camera đang bật")
        assertEquals(l, CameraDemand.shown(blinker = l, demand = null, demandNewer = true), "tắt camera theo yêu cầu giữa xi-nhan ⇒ còn camera xi-nhan")
        assertNull(CameraDemand.shown(null, null, true))
        assertNull(CameraDemand.shown(null, null, false))
    }

    @Test fun `ma dich phim cam - khu tron, khong trung dich cu`() {
        assertEquals(5, CameraDemand.KEY_OPS.size, "bốn camera + Tắt camera")
        CameraDemand.KEY_OPS.forEach { op ->
            val spec = CameraDemand.keySpec(op)!!
            assertTrue(CameraDemand.isKey(spec) && spec.startsWith("cam:"), spec)
            assertFalse(':' in spec.removePrefix("cam:"), "một tầng mã: $spec")
            assertEquals(op, CameraDemand.parseKey(spec), "khứ hồi $spec")
            assertEquals(op, CameraDemand.parseKey(spec.uppercase().replace("CAM:", "cam:")), "chịu hoa/thường")
        }
        assertEquals("cam:rear", CameraDemand.keySpec(Op.Toggle(CameraWhich.REAR)))
        assertEquals("cam:off", CameraDemand.keySpec(Op.CloseAll))
        assertNull(CameraDemand.keySpec(Op.Open(CameraWhich.REAR)), "phím = nút = toggle")
        // Không đụng không gian mã cũ: tên gói (không có ':'), sentinel `__X__`, nút xe `ctl:`.
        listOf("ai.zalo.kiki.car", "__KACHI_VOICE__", "ctl:fan:+1", "camera", "cam", "cam:", "cam:top", "cam:rear:x")
            .forEach { assertNull(CameraDemand.parseKey(it), it) }
        assertFalse(CameraDemand.isKey("ctl:win_lf:open"))
        assertTrue(CameraWhich.ALL.none { it.code == CameraDemand.KEY_OFF }, "`off` không được là mã camera")
    }

    @Test fun `cau kiem thu va cau wake - cung mot cu phap, khu tron`() {
        assertEquals(Op.Toggle(CameraWhich.FRONT), CameraDemand.parseBridge("demand:front"))
        assertEquals(Op.Open(CameraWhich.LEFT), CameraDemand.parseBridge(" OPEN:Left "))
        assertEquals(Op.Close(CameraWhich.RIGHT), CameraDemand.parseBridge("close:right"))
        assertEquals(Op.CloseAll, CameraDemand.parseBridge("demand:off"))
        // Lệnh `camera` CŨ (xi-nhan giả) không có ':' ⇒ không bị nuốt.
        listOf("left", "right", "none", "", "demand", "demand:", ":rear", "open:off", "close:off", "flip:rear")
            .forEach { assertNull(CameraDemand.parseBridge(it), it) }
        val ops = CameraWhich.ALL.flatMap { listOf(Op.Toggle(it), Op.Open(it), Op.Close(it)) } + Op.CloseAll
        ops.forEach { op ->
            val s = CameraDemand.encode(op)
            assertTrue(s.all { it.code < 128 }, "ASCII: $s")
            assertEquals(op, CameraDemand.decode(s), "khứ hồi $s")
        }
        assertNull(CameraDemand.decode(null))
        assertNull(CameraDemand.decode("rác"))
    }
}
