package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ SYS-TASKBAR-VOICE-FOCUS — TẤM CHỮ GIỌNG NÓI KHÔNG ĐƯỢC CHẠM VÀO THANH HỆ THỐNG ══════════════════════════
 *
 * Owner: *"overlay kéo taskbar hệ thống lên — không muốn cái này"* (18/09), và **vẫn còn** ở buổi xe 26/09 đúng
 * lúc Kachi đọc phản hồi. Gốc thật + trích dẫn AOSP `android-10.0.0_r47`: KDoc `VoiceOverlay.show`; số đo:
 * `docs/diagnostics/perf-oncar-2026-09-26/logcat-stream-2.70.txt` · `taskbar-window-dump.txt`; tổng kết:
 * `docs/diagnostics/offcar-2026-09-26/taskbar-focus-and-shell-budget.md`.
 *
 * ## Bài học mà bài canh này khoá (đổi chiều so với bản 18/09)
 * Vòng 18/09 chữa bằng *"overlay mang cùng bộ cờ immersive với màn chính"* và **giữ** tiêu điểm cho đường thoát
 * Back. [ĐO 26/09] cách đó chỉ đủ cho lượt overlay **nhận** tiêu điểm (nháy 37 ms rồi tự ẩn lại); lượt overlay
 * **mất** tiêu điểm thì display 0 không còn cửa sổ nào có tiêu điểm (`mCurrentFocus=null`) nên
 * `DisplayPolicy.updateSystemUiVisibilityLw` (`DisplayPolicy.java:3112-3119`) tính lại từ cửa sổ khác và thanh
 * điều hướng lên **39,6 s** cho tới khi tấm chữ tắt. ⇒ Cách duy nhất đúng: **thôi tham gia** —
 * `FLAG_NOT_FOCUSABLE`, đổi Back lấy chạm-ra-ngoài.
 *
 * ## Vì sao bài canh này quét SOURCE
 * Thứ cần khẳng định là *"cửa sổ overlay mang cờ gì"* — nó chỉ quan sát được khi có một `WindowManager` thật và
 * một thanh hệ thống thật (máy ảo không có taskbar của BYD, và dự án không dựng Activity/View trong JVM thuần:
 * không Robolectric). Cùng lệ `VoiceFastNaturalWiringContractTest` / `VoiceTtsIsolationContractTest`, và mọi phép
 * cắt vùng đi qua [SourceRoots.body] (**nổ** nếu mốc không còn).
 *
 * ## Bài thật ở đây — bốn chiều, không chỉ một
 * Bản vá này rất dễ *"chữa quá tay"*: bỏ tiêu điểm mà **quên** đường thoát thì người lái bị khoá tấm chữ 8 giây;
 * làm nó nổi bật bằng `FLAG_DIM_BEHIND` thì tối cả màn xe khi đang lái; giữ lại `dispatchKeyEvent`/
 * `onWindowFocusChanged` thì để lại mã không bao giờ chạy (CLAUDE.md §8) làm người sau tin rằng Back còn sống.
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
     * Bộ cờ `LAYOUT_*` giữ khung tấm chữ bằng cả màn (thanh hệ thống hiện thì tấm chữ KHÔNG nhảy lên 90 px giữa
     * lúc đang nói). Giữ **đúng một bộ** với màn chính để không có ca nào hai bề mặt xin hai trạng thái khác nhau.
     * So **tập cờ**, không so chuỗi: thứ tự `or` đổi được mà nghĩa không đổi.
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
     * Áp một lần lúc dựng là KHÔNG đủ: hệ thống có thể tự đặt lại cờ của View (quệt cạnh, một app khác xin), và
     * lúc ấy khung tấm chữ co lại giữa lúc đang nói.
     */
    @Test
    fun `co duoc ap lai khi he thong dat lai co, khong chi mot lan luc dung`() {
        val attached = SourceRoots.body(overlay, "override fun onAttachedToWindow()")
        assertTrue(attached.contains("goImmersive(this)"), "lượt đầu: cửa sổ vừa được thêm")

        assertTrue(
            overlay.contains("setOnSystemUiVisibilityChangeListener"),
            "hệ thống đặt lại cờ (quệt cạnh / một app khác xin) ⇒ phải áp lại",
        )
        // ⚠ Điều kiện này là thứ chặn VÒNG LẶP: lượt áp lại của chính ta bắn listener lần nữa, lần đó cờ FULLSCREEN
        // đã bật ⇒ nhánh không chạy tiếp. Gỡ điều kiện = áp lại vô hạn trên luồng vẽ.
        assertTrue(
            overlay.contains("if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) goImmersive(this)"),
            "lượt áp lại phải có cổng 'cờ chưa bật' — không thì listener tự gọi lại chính nó mãi",
        )
    }

    /**
     * ═══ Bài chính của SYS-TASKBAR-VOICE-FOCUS ═══════════════════════════════════════════════════════════════
     *
     * Cửa sổ **có tiêu điểm** là cửa sổ điều khiển thanh hệ thống (`DisplayPolicy.java:3112-3119`) ⇒ tấm chữ chỉ
     * đứng ngoài được bằng cách KHÔNG BAO GIỜ nhận tiêu điểm: `WindowState.canReceiveKeys`
     * (`WindowState.java:2559-2565`) loại thẳng cửa sổ mang `FLAG_NOT_FOCUSABLE` khỏi `findFocusedWindow`.
     *
     * Các assert dưới là từng cách bản vá này có thể bị gỡ ngược mà vẫn compile xanh.
     */
    @Test
    fun `cua so KHONG lay tieu diem va do la lua chon co y`() {
        val show = SourceRoots.body(overlay, "fun show()")
        assertTrue(
            show.contains("WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE"),
            "thiếu cờ này là cửa sổ lại nhận tiêu điểm ⇒ taskbar lại trồi lên mỗi lượt nói (SYS-TASKBAR-VOICE-FOCUS)",
        )
        assertTrue(
            show.contains("WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS"),
            "vẫn phủ trọn màn: vùng chạm-để-huỷ = cả màn trừ tấm chữ",
        )
        // Cửa sổ không tiêu điểm thì ba nhánh này KHÔNG BAO GIỜ chạy — để lại là mã chết, và tệ hơn: làm người sau
        // tin rằng Back vẫn là đường thoát (CLAUDE.md §8).
        assertFalse(overlay.contains("dispatchKeyEvent"), "không tiêu điểm ⇒ không có phím tới: nhánh này là mã chết")
        assertFalse(overlay.contains("KEYCODE_BACK"), "Back không còn tới cửa sổ này — KDoc show nói rõ vì sao")
        assertFalse(
            overlay.contains("onWindowFocusChanged"),
            "cửa sổ không lấy tiêu điểm thì onWindowFocusChanged không bao giờ được gọi",
        )
        assertFalse(overlay.contains("isFocusableInTouchMode"), "view nhận phím làm gì khi cửa sổ không nhận phím")
    }

    /**
     * Bỏ Back thì đường thoát CÒN LẠI phải sống: chạm ra ngoài tấm chữ huỷ phiên, và **chỉ** ngoài tấm chữ (chạm
     * nút *"Mở Cài đặt"* bên trong là bấm nút). Đây là assert quan trọng nhất về phía người dùng — gỡ nó đi là
     * người lái bị một tấm chữ không tắt được cho tới trần 8 giây.
     */
    @Test
    fun `cham ra ngoai van la duong thoat va chi ngoai tam chu`() {
        val touch = SourceRoots.body(overlay, "setOnTouchListener {")
        assertTrue(touch.contains("MotionEvent.ACTION_DOWN"), "quệt tay là đủ ý 'thôi' — không chờ UP")
        assertTrue(touch.contains("!inside(card, ev)"), "chỉ huỷ khi cú chạm NGOÀI tấm chữ")
        assertTrue(touch.contains("onCancel()"), "và nó phải thật sự huỷ phiên")
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
