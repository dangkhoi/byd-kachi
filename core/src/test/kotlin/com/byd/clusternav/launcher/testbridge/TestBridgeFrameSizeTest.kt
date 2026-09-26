package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T-BRIDGE · BÀI CANH `camera_frame` + CỠ ẢNH CHỤP ════════════════════════════════════════════════════════
 *
 * Lệnh này chỉ có MỘT mục đích: trả lời *"vòng fisheye tròn hay kín khung"* bằng một tệp PNG ở cỡ luồng gốc. Nếu
 * bộ phân tích cỡ sai thì lượt đo trên xe hoặc trả về một ảnh vô nghĩa, hoặc xin một lần cấp phát khổng lồ ngay
 * trên đầu máy — cả hai đều chỉ lộ ra khi đã ngồi trong xe. Vậy nên cả hai luật (mặc định + trần) khoá ở đây.
 */
class TestBridgeFrameSizeTest {

    // ── Lệnh có thật trong bảng ─────────────────────────────────────────────────────────────────

    @Test
    fun `camera_frame la lenh that, khong doi doi so nao, nhan name tuy chon`() {
        assertTrue(TestBridgeCommands.CAMERA_FRAME in TestBridgeCommands.NAMES)
        val spec = TestBridgeCommands.SPECS.first { it.name == TestBridgeCommands.CAMERA_FRAME }
        assertEquals(emptyList<String>(), spec.required, "chụp một khung không cần đối số nào")
        assertEquals(listOf(TestBridgeCommands.EXTRA_ARG), spec.optional, "`--es name <W>x<H>` là đối số tuỳ chọn")
    }

    @Test
    fun `camera_frame phan tich duoc khong co doi so, va doc duoc name`() {
        val bare = TestBridgeCommands.parse(
            mapOf(TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.CAMERA_FRAME),
            emptySet(),
        )
        assertTrue(bare is TestBridgeParse.Ok, "thiếu `name` phải chạy được (⇒ cỡ mặc định), nhận: $bare")
        assertEquals("", (bare as TestBridgeParse.Ok).cmd.arg)

        val sized = TestBridgeCommands.parse(
            mapOf(
                TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.CAMERA_FRAME,
                TestBridgeCommands.EXTRA_ARG to "1280x720",
            ),
            emptySet(),
        )
        assertTrue(sized is TestBridgeParse.Ok)
        assertEquals("1280x720", (sized as TestBridgeParse.Ok).cmd.arg)
    }

    /** `camera_frame` KHÔNG được trùng tiền tố xử lý với `camera`: hai lệnh, hai nhánh (bẫy `startsWith`). */
    @Test
    fun `camera va camera_frame la hai ma khac nhau`() {
        assertTrue(TestBridgeCommands.CAMERA != TestBridgeCommands.CAMERA_FRAME)
        assertEquals("camera_frame", TestBridgeCommands.CAMERA_FRAME)
    }

    // ── Cỡ: mặc định ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `vang hoac la thi ve CO MAC DINH 5120x960, khong bao gio ve 0`() {
        val bad = listOf("", "   ", "abc", "1280", "x720", "1280x", "1280x720x3", "-1x-1", "0x0", "1280xabc")
        bad.forEach { arg ->
            val s = TestBridgeFrameSize.parse(arg)
            assertEquals(TestBridgeFrameSize.DEFAULT, s, "chuỗi `$arg` phải cho cỡ mặc định, nhận $s")
        }
        assertEquals(5120, TestBridgeFrameSize.DEFAULT_W, "cỡ fisheye 4-in-1 [ĐO xe 2026-09-25]")
        assertEquals(960, TestBridgeFrameSize.DEFAULT_H)
    }

    @Test
    fun `doc duoc WxH, chu X HOA va khoang trang thua deu chay`() {
        assertEquals(TestBridgeFrameSize.Size(1280, 720), TestBridgeFrameSize.parse("1280x720"))
        assertEquals(TestBridgeFrameSize.Size(1280, 720), TestBridgeFrameSize.parse(" 1280X720 "))
        assertEquals(TestBridgeFrameSize.Size(5120, 960), TestBridgeFrameSize.parse("5120x960"))
        assertEquals(TestBridgeFrameSize.Size(1, 1), TestBridgeFrameSize.parse("1x1"))
    }

    // ── Cỡ: hai trần ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `moi canh bi ha ve tran 8192`() {
        val s = TestBridgeFrameSize.parse("99999x100")
        assertEquals(TestBridgeFrameSize.MAX_SIDE, s.w, "cạnh vượt trần phải bị hạ, không được ném")
        assertEquals(100, s.h, "cạnh còn lại không bị đụng khi diện tích vẫn dưới trần")
    }

    @Test
    fun `dien tich vuot tran thi co DEU hai canh, giu ti le`() {
        // 8192×8192 = 67 M px ⇒ vượt trần 20 M. Vuông vào thì phải vuông ra (tỉ lệ 1:1 giữ nguyên).
        val s = TestBridgeFrameSize.parse("8192x8192")
        assertTrue(s.area <= TestBridgeFrameSize.MAX_AREA, "diện tích sau khi co phải dưới trần, nhận ${s.area}")
        assertEquals(s.w, s.h, "ảnh vuông co đều thì vẫn vuông — méo tỉ lệ là méo đúng thứ lệnh này đo")
        assertTrue(s.w > 4000, "co quá tay: cạnh còn ${s.w}, mong ~4472")
    }

    @Test
    fun `moi ket qua deu hop le - hai canh tu 1 den 8192 va dien tich duoi tran`() {
        val args = listOf("", "abc", "1x1", "5120x960", "8192x8192", "99999x99999", "8192x4000", "7x9000000")
        args.forEach { arg ->
            val s = TestBridgeFrameSize.parse(arg)
            assertTrue(s.w in 1..TestBridgeFrameSize.MAX_SIDE, "`$arg` → w=${s.w} ngoài khoảng")
            assertTrue(s.h in 1..TestBridgeFrameSize.MAX_SIDE, "`$arg` → h=${s.h} ngoài khoảng")
            assertTrue(s.area <= TestBridgeFrameSize.MAX_AREA, "`$arg` → diện tích ${s.area} vượt trần")
        }
    }

    /** Cỡ MẶC ĐỊNH tự nó phải qua được mọi trần — nếu không thì mọi lượt gọi không đối số đều sai. */
    @Test
    fun `co mac dinh tu no da hop le`() {
        val d = TestBridgeFrameSize.DEFAULT
        assertTrue(d.w in 1..TestBridgeFrameSize.MAX_SIDE && d.h in 1..TestBridgeFrameSize.MAX_SIDE)
        assertTrue(d.area <= TestBridgeFrameSize.MAX_AREA, "cỡ mặc định ${d.area} px vượt trần diện tích")
        assertEquals(TestBridgeFrameSize.DEFAULT, TestBridgeFrameSize.parse("5120x960"), "gõ đúng cỡ mặc định thì trùng")
    }
}
