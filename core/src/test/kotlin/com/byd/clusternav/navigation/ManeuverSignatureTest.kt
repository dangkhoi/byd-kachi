package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 226 dòng thuật toán chữ ký hướng rẽ, trước 2026-07-27 chưa từng có một test nào — vì nó nhận
 * `android.graphics.Bitmap` và gọi `android.util.Log`, hai thứ nó không thật sự cần.
 *
 * Test này chỉ khẳng định những gì đầu vào tổng hợp chứng minh được: biên, tính tất định, và seam log
 * không bắt buộc. Kiểm ĐÚNG tên hướng rẽ cần ảnh mũi thật lưu thành fixture — chưa có, và tôi không giả vờ
 * là đã phủ.
 */
class ManeuverSignatureTest {

    private fun frame(w: Int, h: Int, ink: (Int, Int) -> Boolean): PixelFrame =
        ArrayPixelFrame(w, h, IntArray(w * h) { i ->
            if (ink(i % w, i / w)) 0xFF000000.toInt() else 0x00FFFFFF
        })

    @Test
    fun `khung null hoac qua nho thi khong ket luan`() {
        assertNull(ManeuverSignature.classify(null))
        assertNull(ManeuverSignature.classify(frame(4, 4) { _, _ -> true }))
        assertNull(ManeuverSignature.classifyHal(frame(7, 7) { _, _ -> true }))
    }

    @Test
    fun `B3_11 - crop THUA (it cell sang) KHONG false-positive`() {
        // 15x15 nền TRẮNG đục + vài cell ĐEN → chữ ký THƯA (< MIN_SIG_BITS=10). Trước đây query ~toàn-0 khớp
        // NHẦM template thưa (Hamming 0→18 ≤ MAX_HAMMING) → tín hiệu rẽ GIẢ. Guard MIN_SIG_BITS chặn.
        val white = setOf(2, 88, 150, 200)   // 4 cell TRẮNG trên nền ĐEN → ~4 bit sáng (thưa)
        val f = ArrayPixelFrame(15, 15, IntArray(225) { if (it in white) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() })
        val bits = ManeuverSignature.signatureBits(f)
        assertNotNull(bits)
        assertTrue(bits!!.count { it == '1' } < 10, "khung phải THƯA (<10 bit); thực tế=${bits.count { it == '1' }}")
        assertNull(ManeuverSignature.classify(f), "crop thưa KHÔNG được ra amap (false-positive)")
        assertNull(ManeuverSignature.classifyManeuver(f))
    }

    @Test
    fun `cung dau vao thi cung ket qua`() {
        val f = frame(24, 24) { x, y -> (x + y) % 3 == 0 }
        assertEquals(ManeuverSignature.classify(f), ManeuverSignature.classify(f))
    }

    @Test
    fun `khong can gan logger van chay duoc`() {
        // Seam mặc định không làm gì: :core không phụ thuộc logging của nền tảng nào.
        val previous = ManeuverSignature.note
        ManeuverSignature.note = {}
        try {
            ManeuverSignature.classify(frame(16, 16) { x, _ -> x < 8 })
        } finally {
            ManeuverSignature.note = previous
        }
    }

    /** [ManeuverSignature.classify] chỉ là [ManeuverSignature.classifyDetailed] bỏ tên — không được rẽ nhánh
     *  khác nhau, nếu không dòng vết chẩn đoán sẽ mô tả một quyết định khác với quyết định thật gửi ra cụm. */
    @Test
    fun `classifyDetailed va classify luon dong y ve ma icon`() {
        listOf(
            null,
            frame(4, 4) { _, _ -> true },                             // quá nhỏ
            frame(16, 16) { _, _ -> false },                          // trống trơn -> quá mờ
            frame(20, 20) { x, y -> x in 5..15 && y in 5..15 },
            frame(24, 24) { x, y -> (x + y) % 3 == 0 },
        ).forEach { f ->
            assertEquals(ManeuverSignature.classify(f), ManeuverSignature.classifyDetailed(f).amap)
        }
    }

    /** KHOÁ lỗi dữ liệu: trước 2026-07-30 call site chẩn đoán đọc [ManeuverSignature.lastName] SAU khi gọi
     *  classify — mà classify return sớm KHÔNG ghi field khi ảnh null/nhỏ, nên tên đọc được là tên CŨ còn
     *  sót của khung trước (và có thể là của luồng khác). Tên phải đi CÙNG mã trong một giá trị trả về. */
    @Test
    fun `khong co anh thi ten la NO_INPUT chu khong phai ten con sot cua khung truoc`() {
        ManeuverSignature.classify(frame(20, 20) { x, y -> x in 5..15 && y in 5..15 })  // để lại lastName
        val m = ManeuverSignature.classifyDetailed(null)
        assertNull(m.amap)
        assertEquals(ManeuverSignature.NO_INPUT, m.name)
    }

    @Test
    fun `logger duoc goi khi co gan`() {
        val lines = mutableListOf<String>()
        val previous = ManeuverSignature.note
        ManeuverSignature.note = { lines += it }
        try {
            ManeuverSignature.classify(frame(20, 20) { x, y -> x in 5..15 && y in 5..15 })
        } finally {
            ManeuverSignature.note = previous
        }
        assertTrue(lines.isNotEmpty(), "thuật toán phải nói được nó quyết định gì")
    }

    // ── Track B (2026-08-14): kiểm mapping TÊN→MÃ thật qua pipeline đầy đủ (signature + match + nameToAmap/Hal).
    //    Dựng khung 15×15 TỪ chữ ký registry — arrow = trắng ĐỤC (0xFFFFFFFF), nền = TRONG SUỐT (0) → tái
    //    tạo ĐÚNG 225-bit của app gốc (Hamming = 0) → classify khớp CHÍNH tên đó. Không cần hé lộ hàm private.
    private fun bitsFor(name: String): String =
        ManeuverRegistry.RAW.first { it.second == name }.first

    private fun registryFrame(name: String): PixelFrame {
        val bits = bitsFor(name)
        val g = 15
        return ArrayPixelFrame(g, g, IntArray(g * g) { i ->
            if (bits[i] == '1') 0xFFFFFFFF.toInt() else 0x00000000
        })
    }

    /** CANARY: chứng minh kỹ thuật dựng-khung-từ-registry ĐÚNG bằng một mapping vốn đã đúng TRƯỚC Track B. */
    @Test
    fun `canary — khung tu registry tai tao dung chu ky (turn_normal_left thi ra 2)`() {
        assertEquals(2, ManeuverSignature.classify(registryFrame("maneuver_turn_normal_left")))
    }

    @Test
    fun `Track B — merge tren lan cum ra DI THANG 9, khong con chech phai 5`() {
        val f = registryFrame("maneuver_merge")
        assertEquals(9, ManeuverSignature.classify(f))       // AMAP: 9 = đi thẳng (sửa bug owner "merge → rẽ phải")
        assertEquals(11, ManeuverSignature.classifyHal(f))   // HAL: 11 = đi thẳng (vốn đã đúng)
    }

    @Test
    fun `Track B — off-ramp trai ra CHECH, khong con cua 90 do`() {
        val f = registryFrame("maneuver_off_ramp_normal_left")
        assertEquals(4, ManeuverSignature.classify(f))       // AMAP: 4 = chếch trái (trước: 2 = rẽ trái hẳn)
        assertEquals(3, ManeuverSignature.classifyHal(f))    // HAL: 3 = chếch trái (trước: 1 = rẽ trái hẳn)
    }

    @Test
    fun `Track B — off-ramp phai ra CHECH`() {
        val f = registryFrame("maneuver_off_ramp_normal_right")
        assertEquals(5, ManeuverSignature.classify(f))       // AMAP: 5 = chếch phải
        assertEquals(5, ManeuverSignature.classifyHal(f))    // HAL: 5 = chếch phải
    }

    @Test
    fun `Track B — u_turn_right tren lan cum giu HUONG PHAI 19, khong con gop ve trai 8`() {
        val f = registryFrame("maneuver_u_turn_right")
        assertEquals(19, ManeuverSignature.classify(f))      // AMAP: 19 = quay đầu phải (trước: 8 = quay đầu trái)
        assertEquals(10, ManeuverSignature.classifyHal(f))   // HAL: 10 = quay đầu phải (vốn đã đúng)
    }

    @Test
    fun `Track B — RA KHOI vong xuyen (exit thuan) ra 12-24, khong con 11-20 (vao)`() {
        // #37/#38: chữ ký "..._exit_ccw/_cw" THUẦN = thoát vòng xuyến → NEW_ICON 12 (驶出环岛→CAN24),
        // KHÔNG phải "vào" (11→CAN13). Trước Track B collapse hết về 11/20 (defect §4 #37/#38 "exit hiện enter").
        val ccw = registryFrame("maneuver_roundabout_exit_ccw")
        assertEquals(12, ManeuverSignature.classify(ccw))      // AMAP: 12 = ra khỏi vòng xuyến
        assertEquals(24, ManeuverSignature.classifyHal(ccw))   // HAL: 24 = TurnIdMapToCAN[12] (khớp làn cụm)
        val cw = registryFrame("maneuver_roundabout_exit_cw")
        assertEquals(12, ManeuverSignature.classify(cw))       // gộp RHT: cả CCW/CW → 12 (như UTURN gộp về trái)
        assertEquals(24, ManeuverSignature.classifyHal(cw))
    }

    @Test
    fun `Track B — guard !enter- enter_and_exit VAN la VAO vong xuyen 11-20 (khong bi rule exit de)`() {
        // REGRESSION GUARD: 16 tên "enter_and_exit_*" chứa CẢ "enter" LẪN "exit" — glyph vòng-xuyến CHÍNH.
        // Rule exit-thuần (!enter) KHÔNG được đè các tên này về 12/24, phải giữ 11/20 (vào vòng xuyến).
        for (name in listOf(
            "maneuver_roundabout_enter_and_exit_ccw_normal_left",
            "maneuver_roundabout_enter_and_exit_cw_straight",
            "maneuver_roundabout_enter_ccw",
        )) {
            val f = registryFrame(name)
            assertEquals(11, ManeuverSignature.classify(f), "$name phải VÀO vòng xuyến (AMAP 11), không phải ra (12)")
            assertEquals(20, ManeuverSignature.classifyHal(f), "$name phải giữ HAL 20 (vòng xuyến), không phải 24")
        }
    }

    // ── TASK 1 (closeout 1.28): classifyManeuver map chữ ký vòng xuyến → [Maneuver] CÓ HƯỚNG (hết generic).
    //    Bottleneck AMAP-int (classify→11→fromAmapIcon→ROUNDABOUT) vứt hướng ra; đường này giữ lại hướng cho HUD.

    @Test
    fun `TASK1 — classifyManeuver vong xuyen CCW ra dung huong`() {
        assertEquals(
            Maneuver.ROUNDABOUT_LEFT,
            ManeuverSignature.classifyManeuver(registryFrame("maneuver_roundabout_enter_and_exit_ccw_normal_left")),
        )
        assertEquals(
            Maneuver.ROUNDABOUT_RIGHT,
            ManeuverSignature.classifyManeuver(registryFrame("maneuver_roundabout_enter_and_exit_ccw_normal_right")),
        )
        assertEquals(
            Maneuver.ROUNDABOUT_STRAIGHT,
            ManeuverSignature.classifyManeuver(registryFrame("maneuver_roundabout_enter_and_exit_ccw_straight")),
        )
        assertEquals(
            Maneuver.ROUNDABOUT_UTURN,
            ManeuverSignature.classifyManeuver(registryFrame("maneuver_roundabout_enter_and_exit_ccw_u_turn")),
        )
    }

    @Test
    fun `TASK1 — classifyManeuver vong xuyen CW giu chieu (_cw khong lan _ccw)`() {
        assertEquals(
            Maneuver.ROUNDABOUT_LEFT_CW,
            ManeuverSignature.classifyManeuver(registryFrame("maneuver_roundabout_enter_and_exit_cw_normal_left")),
        )
    }

    @Test
    fun `TASK1 — classifyManeuver exit thuan ra ROUNDABOUT_EXIT, enter generic ra ROUNDABOUT`() {
        assertEquals(
            Maneuver.ROUNDABOUT_EXIT,
            ManeuverSignature.classifyManeuver(registryFrame("maneuver_roundabout_exit_ccw")),
        )
        assertEquals(
            Maneuver.ROUNDABOUT,
            ManeuverSignature.classifyManeuver(registryFrame("maneuver_roundabout_enter_ccw")),
        )
    }

    @Test
    fun `TASK1 — classifyManeuver KHONG phai vong xuyen ra null (caller fallback fromAmapIcon)`() {
        // turn_left = non-roundabout → null → NavRepository giữ đường fromAmapIcon cũ, KHÔNG đổi hành vi.
        assertNull(ManeuverSignature.classifyManeuver(registryFrame("maneuver_turn_normal_left")))
    }

    @Test
    fun `TASK1 — classifyManeuver anh null hoac qua nho ra null (cung guard classify)`() {
        assertNull(ManeuverSignature.classifyManeuver(null))
        assertNull(ManeuverSignature.classifyManeuver(frame(4, 4) { _, _ -> true }))
    }
}
