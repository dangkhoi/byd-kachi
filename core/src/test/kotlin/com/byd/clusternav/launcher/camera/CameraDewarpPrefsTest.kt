package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R8-B — MIỀN + MẶC ĐỊNH + phép áp phần trăm của sáu núm nắn méo ════════════════════════════════════════════
 *
 * `CameraDewarpPrefs` là nguồn sự thật duy nhất mà **ba** chỗ tra: `PrefsCameraDewarp` (đọc đĩa),
 * `TestBridgePrefsSet` (ghi qua cầu kiểm thử) và `SettingsSectionsCar` (hàng −/+). Một miền lệch giữa ba chỗ ấy là
 * một giá trị ghi được mà đọc lên thành mặc định — owner chạm núm, màn hiện số mới, và khung **không đổi gì**.
 */
class CameraDewarpPrefsTest {

    private val P = CameraDewarpPrefs

    /** Mặc định của cả bảy = **hành vi đã suy ra**, không phải một con số ai đó thích. */
    @Test fun `mac dinh la nan DU tai dung bo suy ra`() {
        assertEquals(100, P.AMOUNT_DEFAULT, "nắn đủ — thanh trượt tồn tại để owner HẠ xuống, không để tìm mô hình khác")
        assertEquals(100, P.PCT_DEFAULT, "100 % = đúng giá trị suy ra cho ô đang hiện")
        assertEquals(0, P.CENTER_DEFAULT, "lệch tâm = 0 ⇒ đúng tâm quang đã suy")
        assertTrue(P.TEX_MATRIX_DEFAULT, "AOSP dặn áp getTransformMatrix (SurfaceTexture.java:44-47)")
    }

    /** Bước nhảy phải **thấy được bằng mắt** cho núm tỉ lệ, và **rà từng chút** cho tâm (§4 tài liệu toán). */
    @Test fun `buoc nhay 5 phan tram cho ti le, 1 phan tram cho tam`() {
        assertEquals(5, P.PCT_STEP)
        assertEquals(5, P.AMOUNT_STEP)
        assertEquals(1, P.CENTER_STEP, "tâm lệch 5 % là QUÁ một bước — nắn lệch tâm thì một bên thẳng bên kia còng")
    }

    /** Ba phép kiểm miền — thứ `prefs_set` dùng để **từ chối** thay vì kẹp im lặng. */
    @Test fun `mien hop le dung dai, bien nam TRONG`() {
        listOf(0, 1, 50, 99, 100).forEach { assertTrue(P.isAmountPct(it), "độ nắn $it") }
        listOf(-1, 101, 1000, Int.MIN_VALUE, Int.MAX_VALUE).forEach { assertFalse(P.isAmountPct(it), "độ nắn $it") }

        listOf(25, 100, 400).forEach { assertTrue(P.isPct(it), "tỉ lệ $it") }
        listOf(24, 401, 0, -100).forEach { assertFalse(P.isPct(it), "tỉ lệ $it") }

        listOf(-300, -1, 0, 1, 300).forEach { assertTrue(P.isCenterPct(it), "tâm $it") }
        listOf(-301, 301).forEach { assertFalse(P.isCenterPct(it), "tâm $it") }
    }

    /**
     * Phép suy `base` chuyển tiếp thẳng sang [DewarpParams.derive] — và đường kính vòng ảnh `<= 0` ⇒ **bề cao ô**.
     *
     * `imageCircleDiameterPx` là giả định **[ĐOÁN]** (*"vòng ảnh ≈ bề cao khung"*, `camera-dewarp-math.md` §3) nên
     * chỗ gọi phải truyền được số thật khi khung chụp từ xe nói khác. Mặc định phải là thứ khớp `DEFAULT_K`.
     */
    @Test fun `base khop DewarpParams derive, thieu duong kinh thi lay be cao o`() {
        val direct = DewarpParams.derive(rectWidthPx = 1280f, rectHeightPx = 960f, imageCircleDiameterPx = 960f)
        val viaBase = P.base(cellWidthPx = 1280f, cellHeightPx = 960f, imageCircleDiameterPx = 960f)
        assertEquals(direct.k, viaBase.k, 1e-6f)
        assertEquals(direct.focal, viaBase.focal, 1e-6f)
        assertEquals(DewarpParams.DEFAULT_K, viaBase.k, 1e-5f, "dải đầy 1280×960 ⇒ đúng hằng đã suy")

        val fallback = P.base(cellWidthPx = 1280f, cellHeightPx = 960f, imageCircleDiameterPx = 0f)
        assertEquals(viaBase.k, fallback.k, 1e-6f, "đường kính ≤ 0 ⇒ bề cao ô")

        // Ô suy biến (chưa đo được gì) không được cho ra `NaN`/chia-0 — `clamped()` của DewarpParams canh chỗ này.
        val degenerate = P.base(0f, 0f, 0f)
        assertTrue(degenerate.k.isFinite() && degenerate.focal.isFinite(), "ô 0×0 vẫn ra số hữu hạn")
    }

    /** `apply`: phần trăm nhân vào bộ suy ra; lệch tâm **cộng** vào tâm đã suy. */
    @Test fun `apply nhan ti le va cong lech tam`() {
        val base = P.base(1280f, 960f, 960f)
        val out = P.apply(
            base = base, centerX = 1.25f, centerY = 0.5f,
            amountPct = 60, focalPct = 200, kPct = 50, scalePct = 150,
            centerXPct = -25, centerYPct = 10,
        )
        assertEquals(0.60f, out.amount, 1e-6f)
        assertEquals(base.focal * 2f, out.focal, 1e-6f)
        assertEquals(base.k * 0.5f, out.k, 1e-6f)
        assertEquals(1.5f, out.scale, 1e-6f, "SCALE suy ra = 1 ⇒ 150 % = 1,5")
        assertEquals(1.25f - 0.25f, out.centerX, 1e-6f, "lệch −25 % ⇒ tâm về 1,0")
        assertEquals(0.5f + 0.10f, out.centerY, 1e-6f)
    }

    /** Mặc định của cả sáu núm ⇒ **đúng** bộ suy ra, không lệch một ulp. */
    @Test fun `apply voi mac dinh tra dung bo suy ra`() {
        val base = P.base(512f, 960f, 960f)
        val out = P.apply(base = base, centerX = 1.25f, centerY = 0.5f)
        assertEquals(base.k, out.k, 1e-6f)
        assertEquals(base.focal, out.focal, 1e-6f)
        assertEquals(base.scale, out.scale, 1e-6f)
        assertEquals(DewarpParams.DEFAULT_AMOUNT, out.amount, 1e-6f)
        assertEquals(1.25f, out.centerX, 1e-6f)
    }

    /**
     * Giá trị NGOÀI miền ⇒ **mặc định của núm ấy**, không phải biên gần nhất.
     *
     * Lượt GHI đã từ chối (`bad_prefs_value:` ở `TestBridgePrefsSet`), nên đây là lưới an toàn cho một tệp prefs bị
     * sửa tay hoặc còn giá trị của một bản cũ. *"Mặc định biết trước"* là câu trả lời đọc được; một biên lại trông
     * như một lựa chọn của owner (`400 %` chứ không phải `100 %`) và sẽ bị chẩn đoán sai.
     */
    @Test fun `gia tri ngoai mien ve MAC DINH, khong ve bien`() {
        val base = P.base(1280f, 960f, 960f)
        val out = P.apply(
            base = base, centerX = 0.5f, centerY = 0.5f,
            amountPct = 999, focalPct = 9999, kPct = -5, scalePct = 0,
            centerXPct = -9999, centerYPct = 9999,
        )
        assertEquals(1f, out.amount, 1e-6f, "999 ⇒ mặc định 100 %, KHÔNG phải biên 100 tình cờ giống")
        assertEquals(base.focal, out.focal, 1e-6f, "9999 ⇒ 100 %, không phải trần 400 %")
        assertEquals(base.k, out.k, 1e-6f)
        assertEquals(base.scale, out.scale, 1e-6f)
        assertEquals(0.5f, out.centerX, 1e-6f, "lệch lạ ⇒ 0 ⇒ giữ đúng tâm đã suy")
        assertEquals(0.5f, out.centerY, 1e-6f)
    }

    /** Kết quả luôn đã `clamped()` ⇒ không `NaN`/`∞` nào tới được uniform (một `NaN` = cả khung đen, không ai báo). */
    @Test fun `ket qua luon da kep ve mien dung duoc`() {
        val huge = P.apply(
            base = DewarpParams(focal = DewarpParams.MAX_FOCAL, k = DewarpParams.MAX_GAIN),
            centerX = 0f, centerY = 0f, focalPct = 400, kPct = 400, centerXPct = 300, centerYPct = 300,
        )
        assertTrue(huge.focal <= DewarpParams.MAX_FOCAL, "F bị kẹp về trần của DewarpParams")
        assertTrue(huge.k <= DewarpParams.MAX_GAIN)
        assertTrue(huge.centerX <= DewarpParams.MAX_CENTER && huge.centerY <= DewarpParams.MAX_CENTER)
        assertTrue(huge.focal.isFinite() && huge.k.isFinite())
    }

    /** `amount = 0` ⇒ ba cổng của shader đóng ⇒ **đúng** đường 2.73, không lệch một bit nào. */
    @Test fun `do nan 0 thi phep nan tat han`() {
        val out = P.apply(base = P.base(512f, 960f, 960f), centerX = 1.25f, centerY = 0.5f, amountPct = 0)
        assertFalse(out.enabled)
        assertEquals(0f, out.amount, 1e-6f)
    }
}
