package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraChannelFallback.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 · R3 — lùi CHANNEL → PANO **đúng một lần**, không lặp (AC của spec: "HAL giả rc=false ⇒ 1 rebuild") ═════
 *
 * HAL giả ở đây = hai đầu vào mà `AvmCamera`/`CameraGlRenderer` đưa cho máy: `channelRefused` và `frames=N`. Máy là
 * thuần nên bài này chạy được số mô phỏng cả một buổi xe: ba lượt xi-nhan, một lượt đổi bên, một lượt HAL hỏng thật.
 */
class CameraChannelFallbackTest {

    private val P = CameraSignalPolicy

    /** AC: HAL từ chối kênh ⇒ ĐÚNG MỘT lượt dựng lại, ở PANO, và lượt PANO ấy không lùi lần nữa dù HAL vẫn nói không. */
    @Test fun `HAL rc false thi lui dung mot lan ve PANO khong lap`() {
        val f = CameraChannelFallback()
        f.onSessionStart(forcedPano = false)
        assertEquals(P.SOURCE_CHANNEL, f.sourceFor(P.SOURCE_CHANNEL), "chưa lùi ⇒ đúng pref")
        assertEquals(Action.REBUILD_PANO, f.onHalResult(channel = true, channelRefused = true))
        assertTrue(f.fellBack)
        // Phiên dựng lại (forcedPano = true): nguồn ép PANO, và mọi tín hiệu hỏng SAU đó đều KHÔNG sinh lượt lùi thứ hai.
        f.onSessionStart(forcedPano = true)
        assertEquals(P.SOURCE_PANO, f.sourceFor(P.SOURCE_CHANNEL), "đã lùi ⇒ PANO bất kể pref")
        assertEquals(Action.NONE, f.onHalResult(channel = false, channelRefused = true), "phiên PANO: không có kênh để lùi")
        assertEquals(Action.NONE, f.onHalResult(channel = true, channelRefused = true), "kể cả tín hiệu lạc ⇒ vẫn không lùi lần 2")
        assertEquals(Action.NONE, f.onFirstFrameBudget(channel = true, frames = 0L), "camera hỏng thật ⇒ dừng, không dựng vô hạn")
        assertTrue(f.fellBack)
    }

    /** Lượt xi-nhan MỚI mở lại cửa: owner vẫn chọn CHANNEL thì lượt rẽ sau thử lại từ đầu (pref là ý owner). */
    @Test fun `phien xi nhan moi dat lai co, thu CHANNEL lai`() {
        val f = CameraChannelFallback()
        f.onSessionStart(forcedPano = false)
        assertEquals(Action.REBUILD_PANO, f.onHalResult(true, true))
        f.onSessionStart(forcedPano = true)
        // …hết xi-nhan, lượt rẽ sau:
        f.onSessionStart(forcedPano = false)
        assertFalse(f.fellBack)
        assertEquals(P.SOURCE_CHANNEL, f.sourceFor(P.SOURCE_CHANNEL))
        assertEquals(Action.NONE, f.onHalResult(channel = true, channelRefused = false), "HAL nhận ⇒ không lùi")
    }

    /** Ngân sách khung đầu: `frames = 0` ⇒ lùi; có khung ⇒ không; `null` (không đếm được) ⇒ KHÔNG lùi theo số không có. */
    @Test fun `ngan sach khung dau chi lui khi dem duoc va bang 0`() {
        val f = CameraChannelFallback()
        f.onSessionStart(false)
        assertEquals(Action.NONE, f.onFirstFrameBudget(channel = true, frames = 12L))
        assertEquals(Action.NONE, f.onFirstFrameBudget(channel = true, frames = null), "đường TV/SV không có bộ đếm ⇒ không kết luận")
        assertEquals(Action.NONE, f.onFirstFrameBudget(channel = false, frames = 0L), "PANO không có gì để lùi")
        assertEquals(Action.REBUILD_PANO, f.onFirstFrameBudget(channel = true, frames = 0L))
        assertEquals(Action.NONE, f.onFirstFrameBudget(channel = true, frames = 0L), "lần hai: không")
    }

    /** `framesOf` đọc đúng chuỗi thống kê THẬT của luồng vẽ; chuỗi rỗng/không có bộ đếm ⇒ null. */
    @Test fun `framesOf doc chuoi stats cua luong ve`() {
        assertEquals(4052L, CameraChannelFallback.framesOf("frames=4052 busySkip=0 fpsSkip=1780 fpsCap=15 max=16384"))
        assertEquals(0L, CameraChannelFallback.framesOf("frames=0 busySkip=0"))
        assertNull(CameraChannelFallback.framesOf(""), "đường TV/SV: rỗng ⇒ không đếm được")
        assertNull(CameraChannelFallback.framesOf("busySkip=3"))
        assertNull(CameraChannelFallback.framesOf("xframes=3"), "phải là từ nguyên `frames=`")
    }

    /**
     * Ngân sách [SUY] nằm TRÊN ca chậm đã đo (helper nối lại 1,7 s — [ĐO E9 27/09]) và DƯỚI một lượt rẽ thường
     * (phiên xi-nhan 09:58:15 → 09:58:26 = 11 s trên log xe) để phép lùi kịp cho ra hình trong chính lượt ấy.
     * KHÔNG so với `CameraHold.HOLD_MS` (1,2 s): đó là khoảng nối pha tắt của đèn nháy, một việc khác.
     */
    @Test fun `ngan sach khung dau tren 1 7 s va duoi mot luot re`() {
        assertTrue(CameraChannelFallback.FIRST_FRAME_BUDGET_MS > 1_700L, "[ĐO E9 27/09] helper nối lại 1,7 s")
        assertTrue(CameraChannelFallback.FIRST_FRAME_BUDGET_MS <= 5_000L, "phải xong trong một lượt rẽ thường")
        assertEquals(3_000L, CameraChannelFallback.FIRST_FRAME_BUDGET_MS, "đổi số ⇒ đổi doc camera-ia-profile.md §cơ chế + 🚗 CAM-F2")
    }
}
