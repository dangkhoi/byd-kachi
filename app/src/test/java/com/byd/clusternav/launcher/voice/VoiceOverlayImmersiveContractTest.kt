package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [ĐO xe 2026-09-18 §C] TẤM CHỮ GIỌNG NÓI KHÔNG ĐƯỢC KÉO THANH HỆ THỐNG LÊN ═══════════════════════════════
 *
 * Owner: *"overlay kéo taskbar hệ thống lên — không muốn cái này"*. Bằng chứng + gốc:
 * `docs/diagnostics/oncar-voice-cases-findings-2026-09-18.md` §C.
 *
 * ## Vì sao bài canh này quét SOURCE
 * Thứ cần khẳng định là *"cửa sổ overlay mang cờ ẩn thanh hệ thống"* — nó chỉ quan sát được khi có một
 * `WindowManager` thật và một thanh hệ thống thật (máy ảo không có taskbar của BYD, và dự án không dựng
 * Activity/View trong JVM thuần: không Robolectric). Cùng lệ `VoiceFastNaturalWiringContractTest` /
 * `VoiceTtsIsolationContractTest`, và mọi phép cắt vùng đi qua [SourceRoots.body] (**nổ** nếu mốc không còn).
 *
 * ## Bài thật ở đây — hai chiều, không chỉ một
 * Bản vá này rất dễ *"chữa quá tay"*: ẩn thanh hệ thống bằng cách bỏ tiêu điểm (`FLAG_NOT_FOCUSABLE`) thì mất
 * đường thoát bằng Back, còn làm nó nổi bật bằng `FLAG_DIM_BEHIND` thì tối cả màn xe khi đang lái. Nên bài canh
 * đòi **cả hai chiều**: cờ immersive phải CÓ, và ba thứ kia phải KHÔNG.
 */
class VoiceOverlayImmersiveContractTest {

    private val overlay by lazy {
        SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceOverlay.kt")
    }
    private val homeWiring by lazy {
        SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt")
    }

    private fun flagsIn(src: String): Set<String> =
        Regex("SYSTEM_UI_FLAG_\\w+").findAll(src).map { it.value }.toSet()

    /**
     * Hai bề mặt **lệch cờ nhau** là chính cái bệnh: Android đọc cờ ẩn thanh hệ thống từ cửa sổ **đang có tiêu
     * điểm**, nên overlay thiếu cờ ⇒ giây nó nhận tiêu điểm là giây thanh hệ thống hiện lại. So **tập cờ**, không
     * so chuỗi: thứ tự `or` đổi được mà nghĩa không đổi.
     */
    @Test
    fun `tam chu dung DUNG bo co an thanh he thong cua man chinh`() {
        val mine = flagsIn(SourceRoots.body(overlay, "private fun goImmersive(v: View)"))
        val home = flagsIn(SourceRoots.body(homeWiring, "internal fun Activity.goImmersiveWindow()"))
        assertEquals(home, mine, "tấm chữ giọng nói phải mang ĐÚNG bộ cờ của màn chính, không phải một bộ gần giống")
        // Khoá luôn con số: nếu một ngày CẢ HAI bề mặt cùng rụng một cờ thì phép so trên vẫn xanh.
        assertEquals(6, mine.size, "bộ cờ immersive có 6 cờ (3 LAYOUT_* + HIDE_NAVIGATION + FULLSCREEN + STICKY)")
        assertTrue(
            mine.contains("SYSTEM_UI_FLAG_IMMERSIVE_STICKY"),
            "STICKY là thứ cho một cú quệt cạnh chỉ hiện thanh TẠM rồi tự ẩn — bỏ nó là ghim thanh lên lại",
        )
        assertTrue(mine.contains("SYSTEM_UI_FLAG_FULLSCREEN") && mine.contains("SYSTEM_UI_FLAG_HIDE_NAVIGATION"))
    }

    /**
     * Áp một lần lúc dựng là KHÔNG đủ: tấm chữ mất/lấy lại tiêu điểm nhiều lần trong một phiên (hộp xác nhận ·
     * lượt nghe nối · một cửa sổ khác chen vào rồi rút), và hệ thống có thể tự cho thanh hiện lại (quệt cạnh).
     */
    @Test
    fun `co duoc ap lai moi luot lay lai tieu diem, khong chi mot lan luc dung`() {
        val attached = SourceRoots.body(overlay, "override fun onAttachedToWindow()")
        assertTrue(attached.contains("goImmersive(this)"), "lượt đầu: cửa sổ vừa được thêm")

        val focus = SourceRoots.body(overlay, "override fun onWindowFocusChanged(hasWindowFocus: Boolean)")
        assertTrue(
            focus.contains("if (hasWindowFocus) goImmersive(this)"),
            "mỗi lượt LẤY LẠI tiêu điểm phải áp lại — và chỉ khi lấy lại, không khi mất",
        )

        assertTrue(
            overlay.contains("setOnSystemUiVisibilityChangeListener"),
            "hệ thống cho thanh hiện lại (quệt cạnh / một app khác xin) ⇒ phải áp lại",
        )
        // ⚠ Điều kiện này là thứ chặn VÒNG LẶP: lượt áp lại của chính ta bắn listener lần nữa, lần đó cờ FULLSCREEN
        // đã bật ⇒ nhánh không chạy tiếp. Gỡ điều kiện = áp lại vô hạn trên luồng vẽ.
        assertTrue(
            overlay.contains("if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) goImmersive(this)"),
            "lượt áp lại phải có cổng 'cờ chưa bật' — không thì listener tự gọi lại chính nó mãi",
        )
    }

    /**
     * Cách *"chữa"* sai mà rất dễ nghĩ ra: bỏ tiêu điểm thì thanh hệ thống cũng không bị kéo lên — nhưng
     * `dispatchKeyEvent` chỉ tới cửa sổ CÓ tiêu điểm, nên đổi lấy việc mất đường thoát bằng Back (KDoc `show`).
     */
    @Test
    fun `tieu diem va duong thoat bang Back KHONG bi go de an thanh he thong`() {
        assertTrue(overlay.contains("isFocusableInTouchMode = true"), "tấm chữ phải nhận được phím")
        assertTrue(overlay.contains("KeyEvent.KEYCODE_BACK"), "Back là đường thoát")
        assertTrue(overlay.contains("onCancel()"), "và Back phải thật sự huỷ phiên")
        assertFalse(
            overlay.contains("FLAG_NOT_FOCUSABLE"),
            "bỏ tiêu điểm ẩn được thanh hệ thống nhưng giết luôn đường thoát bằng Back",
        )
    }

    /** Ẩn thanh hệ thống là việc của cờ immersive — KHÔNG phải cái cớ để làm tối màn xe khi đang lái. */
    @Test
    fun `an thanh he thong khong duoc keo theo lam toi man`() {
        assertFalse(overlay.contains("FLAG_DIM_BEHIND"), "không làm tối phía sau tấm chữ")
        assertTrue(overlay.contains("lp.dimAmount = 0f"), "và nói rõ điều đó ở tham số cửa sổ")
    }

    /** CLAUDE.md §4.1 — hai tệp lượt này chạm tới đều phải dưới trần 500 dòng. */
    @Test
    fun `hai tep cua luot va nay duoi tran 500 dong`() {
        listOf(
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceOverlay.kt",
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt",
        ).forEach {
            val n = SourceRoots.text(it).lines().size
            assertTrue(n <= 500, "$it = $n dòng — vượt trần 500 (CLAUDE.md §4.1)")
        }
    }
}
