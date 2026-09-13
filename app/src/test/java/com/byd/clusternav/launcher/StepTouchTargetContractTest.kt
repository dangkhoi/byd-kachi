package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T6 · R7 — ĐÍCH CHẠM CỦA NÚT − / + TRÊN THANH NÚT XE ═════════════════════════════════════════════════════
 *
 * `kachi-settings-ia-v2.html` §3 R7 + design-system §10 Pass 2 `[P1]`: *"nút −/+ rộng 14–22dp — đích chạm nhỏ nhất
 * màn"*. Yêu cầu: nới đích chạm **mà không phóng glyph, không đổi hình ô**.
 *
 * ## Hai lớp, vì hai loại sai khác nhau
 *  1. **Phép tính vùng chạm** ([StepTouchTarget.zone]) — thuần, chạy thật: đúng thì nó phải nới ĐỦ [KachiSpace.TOUCH]
 *     khi còn chỗ, **trượt vào trong** (không cắt cụt) khi đụng biên, và **không bao giờ để hai vùng chồng nhau**
 *     (chồng = một cú chạm ra hai lệnh xe).
 *  2. **Cách nới** — quét mã: phải là [android.view.TouchDelegate], KHÔNG phải `minWidth`/`minHeight` trên chính
 *     nút. KDoc [KachiSpace.TOUCH_TIGHT] đã ghi phép đo: *"lần đầu tôi đặt 36dp và nó LÀM HỎNG ô: 2×36 = 72 > 68
 *     ⇒ chữ giá trị `22°` xuống hai dòng"*. Nới bằng kích thước view ở ô 84dp là bất khả — bài này chặn người sau
 *     "sửa cho đúng test" theo đúng cách đã hỏng một lần.
 */
class StepTouchTargetContractTest {

    private val min = KachiSpace.TOUCH
    private val helper by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/StepTouchTarget.kt") }

    // ══ (1) PHÉP TÍNH VÙNG — chạy thật ════════════════════════════════════════════════════════════════════

    /** Còn chỗ ⇒ vùng đúng bằng [min] mỗi chiều, đặt quanh TÂM nút (không lệch về một bên). */
    @Test
    fun `con cho thi noi du dich cham`() {
        val z = StepTouchTarget.zone(centreX = 100, centreY = 100, min = min, laneLeft = 0, laneRight = 200, hostHeight = 200)
        assertEquals(min, z[2] - z[0], "bề ngang phải đủ đích chạm")
        assertEquals(min, z[3] - z[1], "bề dọc phải đủ đích chạm")
        assertEquals(100, (z[0] + z[2]) / 2, "và đặt quanh tâm nút")
    }

    /**
     * Đụng biên thì **TRƯỢT vào trong**, không cắt cụt.
     *
     * Cắt cụt là cách viết một dòng (`coerceIn`) và nó làm mất luôn phần vừa nới ra — nút sát mép ô (đúng chỗ nút −
     * đang nằm) sẽ không được lợi gì. [ĐO] tâm nút − ở x≈18 trong làn 0..42: cắt cụt cho 18+24=42 → rộng 42−0=42
     * nhưng chỉ vì làn hẹp; còn ở làn rộng thì cắt cụt cho 42 trong khi trượt cho đủ 48.
     */
    @Test
    fun `dung bien thi truot vao trong, khong cat cut`() {
        val z = StepTouchTarget.zone(centreX = 5, centreY = 5, min = min, laneLeft = 0, laneRight = 200, hostHeight = 200)
        assertEquals(0, z[0], "trượt sát biên trái")
        assertEquals(min, z[2] - z[0], "và vẫn giữ ĐỦ đích chạm (cắt cụt sẽ ra ${5 + min / 2})")
        assertEquals(min, z[3] - z[1], "trục dọc cũng vậy")
    }

    /** Làn/ô hẹp hơn đích chạm ⇒ vùng đúng bằng chỗ CÓ THẬT — không nói dối, cũng không tràn sang làn kia. */
    @Test
    fun `lan hep hon dich cham thi lay dung be rong co that`() {
        val z = StepTouchTarget.zone(centreX = 21, centreY = 43, min = min, laneLeft = 0, laneRight = 42, hostHeight = 86)
        assertEquals(0, z[0]); assertEquals(42, z[2])
        assertEquals(42, z[2] - z[0], "làn 42dp thì nhiều nhất là 42 — ô thanh nút ngang 84dp chia đôi")
        assertEquals(min, z[3] - z[1], "bề dọc vẫn đủ 48 vì ô cao 86dp")
    }

    /**
     * ⚠⚠ HAI VÙNG KHÔNG ĐƯỢC CHỒNG NHAU — chồng thì một cú chạm ở giữa ô ra **hai** lệnh xe.
     *
     * Dựng đúng hình học ô thanh nút ngang thật: 84×86dp, hai nút ở ~1/4 và ~3/4 bề ngang, chia làn tại giữa.
     */
    @Test
    fun `hai vung cua nut trai va phai khong chong nhau`() {
        val w = KachiSpace.DOCK_TILE_W
        val h = KachiSpace.DOCK_TILE_H
        val mid = w / 2
        val left = StepTouchTarget.zone(w / 4, h * 2 / 3, min, 0, mid, h)
        val right = StepTouchTarget.zone(w * 3 / 4, h * 2 / 3, min, mid, w, h)
        assertTrue(left[2] <= right[0], "vùng trái kết thúc trước khi vùng phải bắt đầu (${left[2]} ≤ ${right[0]})")
        assertTrue(left[2] - left[0] >= 32, "vẫn phải rộng hơn hẳn 14–22dp đo được trước khi vá")
        assertEquals(min, left[3] - left[1], "bề dọc đủ đích chạm ở ô cao ${h}dp")
    }

    /** Thanh nút DỌC (ô rộng 100dp) thì đủ 48 cả hai chiều — ghi lại để khỏi ai nghĩ giới hạn 42 là do phép tính. */
    @Test
    fun `thanh nut doc du 48 ca hai chieu`() {
        val w = KachiSpace.DOCK_TILE_W_VERTICAL
        val z = StepTouchTarget.zone(w / 4, KachiSpace.DOCK_TILE_H_VERTICAL / 2, min, 0, w / 2, KachiSpace.DOCK_TILE_H_VERTICAL)
        assertEquals(min, z[2] - z[0], "ô dọc rộng ${w}dp ⇒ mỗi làn ${w / 2}dp ≥ $min")
    }

    // ══ (2) CÁCH NỚI — TouchDelegate, KHÔNG phóng nút, KHÔNG phóng glyph ══════════════════════════════════

    @Test
    fun `noi bang TouchDelegate, khong noi chinh nut`() {
        val factory = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt")
        assertTrue(
            SourceRoots.body(factory, "private fun tileStep(").contains("StepTouchTarget.attach(tile, minus, plus)"),
            "ô STEP phải giao nửa trái/nửa phải cho −/+ qua TouchDelegate",
        )
        assertTrue(helper.contains(": TouchDelegate("), "phép nới phải là TouchDelegate của nền tảng")

        val stepBtn = SourceRoots.body(factory, "private fun stepBtn(")
        assertFalse(
            Regex("""minWidth = """).containsMatchIn(stepBtn),
            "KHÔNG đặt minWidth cho nút: [ĐO] 2×36dp = 72 > 68dp dùng được của ô ⇒ chữ giá trị xuống hai dòng " +
                "(ghi ở KDoc KachiSpace.TOUCH_TIGHT) — nới VÙNG CHẠM, không nới view",
        )
        assertTrue(
            stepBtn.contains("minHeight = dpi(ctx, Sp.TOUCH_TIGHT)"),
            "chiều cao nút giữ TOUCH_TIGHT (32) — đó là số đã ĐO từ chỗ còn lại trong ô, không phải chọn cho đẹp",
        )
        assertTrue(
            stepBtn.contains("size.valueSp"),
            "glyph −/+ giữ nguyên cỡ theo vùng, KHÔNG phóng to (yêu cầu R7 nói rõ 'hình không đổi')",
        )
    }

    /** Ngưỡng nới phải LẤY từ thang, không phải một con số thứ hai cho cùng một khái niệm "đích chạm". */
    @Test
    fun `nguong noi lay tu thang khoang cach`() {
        assertTrue(
            Regex("""val min = dpi\(host\.context, Sp\.TOUCH\)""").containsMatchIn(helper),
            "ngưỡng phải là Sp.TOUCH — gõ 48 tại chỗ là con số thứ hai cho cùng một khái niệm",
        )
    }

    /**
     * Cử chỉ phải **dính** vào vùng trúng lúc `ACTION_DOWN`.
     *
     * Không giữ thì ngón tay nhích qua giữa ô giữa chừng sẽ đổi đích, và cú bấm rơi vào nút kia — tức bấm "giảm"
     * lại ra "tăng". Ở một ô điều khiển xe thì đó không phải phiền, đó là sai lệnh.
     */
    @Test
    fun `cu chi dinh vao vung trung luc DOWN`() {
        assertTrue(helper.contains("ACTION_DOWN"), "chỉ chọn vùng ở ACTION_DOWN")
        assertTrue(
            Regex("""private var active: Zone\? = null""").containsMatchIn(helper),
            "và giữ vùng đó cho tới hết cử chỉ",
        )
        assertTrue(
            helper.contains("ACTION_UP") && helper.contains("ACTION_CANCEL"),
            "nhả chốt ở CẢ UP LẪN CANCEL — quên CANCEL thì cử chỉ bị hệ thống cắt sẽ để lại vùng dính vĩnh viễn",
        )
    }

    /**
     * ⚠ **Vùng chạm mở rộng phải HIỆN RA VỚI TALKBACK** (backlog D2d).
     *
     * TalkBack không đi qua [android.view.TouchDelegate.onTouchEvent]; nó hỏi cây trợ năng, và cây đó chỉ thấy
     * vùng mở rộng khi delegate trả [android.view.accessibility.AccessibilityNodeInfo.TouchDelegateInfo]. Lớp
     * `SplitDelegate` truyền `Rect()` **RỖNG** cho ctor cơ sở (bộ định tuyến thật là danh sách `zones`), nên nếu
     * không override thì info mặc định dựng từ khung rỗng ⇒ với người dùng trợ năng, đích chạm vẫn là 20×32dp
     * như trước bản vá — tức R7 không có tác dụng với đúng nhóm cần nó nhất.
     *
     * [ĐO] `javap` trên `android.jar` của `compileSdk = 37`: `TouchDelegate.getTouchDelegateInfo()` và
     * `TouchDelegateInfo(java.util.Map<Region, View>)` — cả hai **API 29**, `minSdk = 29` ⇒ cấm rẽ nhánh SDK ở đây
     * (rẽ nhánh thừa là nói dối rằng có ROM không chạy được đường này).
     */
    @Test
    fun `TalkBack phai thay duoc vung cham mo rong`() {
        assertTrue(
            helper.contains("override fun getTouchDelegateInfo()"),
            "SplitDelegate phải khai vùng chạm cho cây trợ năng — không có thì TalkBack vẫn thấy nút 20×32dp",
        )
        assertTrue(
            Regex("""AccessibilityNodeInfo\.TouchDelegateInfo\(zones\.associate""").containsMatchIn(helper),
            "info phải dựng từ CHÍNH danh sách zones đang định tuyến cú chạm — dựng từ nguồn khác là hai sự thật",
        )
        assertTrue(
            helper.contains("Region(it.bounds)"),
            "khoá của map là Region bọc đúng khung vùng (ctor nhận Map<Region, View>)",
        )
        assertFalse(
            Regex("""SDK_INT""").containsMatchIn(helper),
            "API 29 mà minSdk 29 ⇒ không được rẽ nhánh SDK",
        )
    }
}
