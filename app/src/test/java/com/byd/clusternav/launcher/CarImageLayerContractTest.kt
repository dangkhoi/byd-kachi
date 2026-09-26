package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CLOSEOUT 2026-09-25 · R3 — HÌNH XE: vẽ MỘT lớp riêng, KHÔNG repaint khi số đổi; bitmap thuộc KHO chung ═══
 *
 * Backlog owner 2026-09-25 *"HÌNH XE NHÁY 1-2 s/lần"*: số lốp đổi mỗi 1–2 s (nhiễu áp suất/nhiệt) ⇒ `set()` ⇒
 * `invalidate()` ⇒ `onDraw` re-composite CẢ hình xe. Vá (`6872281`): [TyreBoardView] = FrameLayout(nền [CarImageView]
 * chỉ hình xe · đè `CellsView` trong suốt chỉ số). Bài này KHOÁ kiến trúc đó ở mức source, vì unit-test `:app` không
 * có Robolectric/mock (android.jar stub ném) — không dựng được View/Canvas giả để đếm `drawBitmap` trực tiếp.
 *
 * Cùng lượt (audit RAM §8): ba [CarImageLayer] nay dùng chung bitmap qua [CarImageStore] ⇒ lớp KHÔNG được
 * `recycle()` (lớp khác còn vẽ) — khoá luôn ở đây.
 */
class CarImageLayerContractTest {

    private val tyre = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/TyreBoardView.kt")
    private val carView = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/CarImageView.kt")
    private val layer = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/CarImageLayer.kt")
    private val store = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/CarImageStore.kt")

    // ── hình xe tách lớp ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `TyreBoardView la FrameLayout xep CarImageView nen + lop so trong suot`() {
        assertTrue(Regex("""class TyreBoardView\(.*\)\s*:\s*FrameLayout\(""").containsMatchIn(tyre), "phải là FrameLayout 2 lớp")
        assertTrue(tyre.contains("CarImageView(context)"), "lớp nền = CarImageView")
        assertTrue(Regex("""inner class CellsView\(.*\)\s*:\s*View\(""").containsMatchIn(tyre), "lớp số = View trong suốt")
    }

    /**
     * `set(số)` chỉ chạm lớp SỐ. Lớp số **không** vẽ bitmap, **không** gọi `invalidate` của lớp nền, **không** gọi
     * `car.draw` — đây là điều kiện đủ để 5 lần `set()` số khác nhau ⇒ 0 lần vẽ lại hình xe (hình xe chỉ vẽ ở
     * `CarImageView.onDraw`, mà view đó chỉ `invalidate` khi ẢNH nạp xong).
     */
    @Test
    fun `set() so chi invalidate lop so - lop so khong dung toi hinh xe`() {
        val set = SourceRoots.body(tyre, "fun set(")
        assertTrue(set.contains("cells.set("), "TyreBoardView.set phải uỷ quyền cho lớp số: $set")
        assertFalse(set.contains("carView"), "TyreBoardView.set KHÔNG được đụng lớp nền")

        val cells = tyre.substringAfter("inner class CellsView")
        for (forbidden in listOf("drawBitmap(", "carView.invalidate", "car.draw(", "CarImageLayer(", "carView.ensure")) {
            assertFalse(cells.contains(forbidden), "lớp số chứa '$forbidden' ⇒ số đổi sẽ vẽ lại hình xe")
        }
        assertTrue(cells.contains("carView.contentRect("), "lớp số lấy khung letterbox từ lớp nền để bám bánh")
    }

    @Test
    fun `CarImageView chi invalidate khi anh nap xong, khong co set() du lieu`() {
        assertFalse(Regex("""fun set\(""").containsMatchIn(carView), "CarImageView không nhận dữ liệu số")
        val invalidates = Regex("""\binvalidate\(\)""").findAll(carView).count()
        assertEquals(1, invalidates, "đúng MỘT invalidate: callback nạp ảnh của CarImageLayer")
        assertTrue(carView.contains("CarImageLayer(context) { invalidate() }"))
    }

    @Test
    fun `drawBitmap hinh xe chi o mot cho - CarImageLayer draw`() {
        val draw = SourceRoots.body(layer, "fun draw(")
        assertEquals(1, Regex("""drawBitmap\(""").findAll(draw).count())
        val elsewhere = Regex("""drawBitmap\(""").findAll(layer).count() - 1
        assertEquals(0, elsewhere, "CarImageLayer chỉ vẽ bitmap trong draw()")
        for (f in listOf(tyre, carView)) assertFalse(f.contains("drawBitmap("), "view bảng không tự vẽ bitmap")
    }

    // ── kho chung + quyền sở hữu ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `CarImageLayer lay anh qua kho chung (peek + shared), khong tu giai ma`() {
        val ensure = SourceRoots.body(layer, "fun ensure(")
        assertTrue(ensure.contains("CarImageStore.peek("), "hỏi kho trước — có sẵn thì gán ngay, không lượt nền")
        assertTrue(ensure.contains("CarImageStore.shared("), "thiếu thì nạp QUA kho (cất lại cho lớp khác)")
        assertTrue(ensure.contains("CarImageStore.cacheKey("), "khoá dùng chung do kho định nghĩa")
        assertFalse(layer.contains("loadFeathered("), "lớp không được giải mã riêng (bản riêng = tốn RAM ×3)")
    }

    // ── CLOSE-5 · nạp hỏng ⇒ thử lại theo đồng hồ, không kẹt placeholder, không vòng vô hạn ────────────────────────

    /**
     * Lớp không dựng được off-car (stub `Handler`), nên khoá WIRING ở mức source: `ensure()` phải hỏi
     * [CarImageStore.LoadRetry] trước khi nạp lại khoá đã hỏng; callback hỏng phải ghi lần hỏng + hẹn đánh thức đúng
     * mốc (có guard `gen`); nạp được / tháo ô ⇒ reset. Phần SỐ HỌC của lịch giãn khoá ở `CarImageLoadRetryTest`.
     */
    @Test
    fun `ensure() hoi LoadRetry truoc khi nap lai khoa da hong - hong thi ghi + hen danh thuc dung moc`() {
        val ensure = SourceRoots.body(layer, "fun ensure(")
        assertTrue(layer.contains("CarImageStore.LoadRetry()"), "lịch thử-lại là lớp THUẦN của kho, không tự viết lại")
        assertTrue(ensure.contains("retry.shouldTry(now())"), "khoá đã nạp mà không có ảnh ⇒ chỉ nạp lại khi tới mốc")
        assertTrue(ensure.contains("retry.recordFailure(now())"), "nạp hỏng phải ghi lần hỏng (giãn 1 s → ×2 → 60 s)")
        assertTrue(ensure.contains("main.postDelayed({ if (my == gen) onReady() }, delay)"),
            "hẹn MỘT lượt đánh thức đúng mốc, guard gen để đổi cỡ/nhả ô huỷ được")
        assertEquals(3, Regex("""retry\.reset\(\)""").findAll(ensure).count(),
            "reset ở 2 đường thành công (peek hit + nạp xong) + 1 khi đổi khoá giữa chừng")
        assertTrue(SourceRoots.body(layer, "fun release(").contains("retry.reset()"), "tháo ô ⇒ quên lịch giãn")
        // Không được quay lại vòng vô hạn: đường hỏng KHÔNG gọi onReady() trực tiếp, chỉ qua postDelayed đúng mốc.
        val failBranch = ensure.substringAfter("} else {").substringBefore("main.postDelayed")
        assertFalse(failBranch.contains("onReady()"), "đường hỏng không invalidate ngay")
    }

    @Test
    fun `CarImageLayer KHONG recycle - bitmap thuoc kho, lop khac co the con ve`() {
        assertEquals(0, Regex("""\.recycle\(\)""").findAll(layer).count(),
            "recycle ở lớp = lớp khác vẽ trúng bitmap đã huỷ ⇒ crash luồng vẽ")
    }

    @Test
    fun `kho KHONG recycle ban bi day khoi LRU (chi feather recycle anh tho trung gian)`() {
        val recycles = Regex("""\.recycle\(\)""").findAll(store).count()
        val feather = SourceRoots.body(store, "fun feather(")
        assertEquals(1, recycles, "chỉ một recycle trong kho")
        assertTrue(feather.contains("src.recycle()"), "và nó là ảnh thô trung gian trong feather()")
        assertFalse(SourceRoots.body(store, "class SharedLru").contains("recycle"), "LRU không huỷ bản bị đẩy")
    }

    @Test
    fun `anh mac dinh giai ma theo ke hoach decodePlan (co inScaled), khong chi inSampleSize`() {
        val def = SourceRoots.body(store, "private fun loadDefaultScaled(")
        assertTrue(def.contains("decodePlan("), "phải qua decodePlan")
        assertTrue(def.contains("inScaled = true") && def.contains("inTargetDensity"), "phải hạ đúng khung trong lúc giải mã")
    }

    @Test
    fun `kho chung duoc ba lop dung - khong lop nao tu tao bitmap rieng`() {
        for (rel in listOf("DoorBoardView.kt", "CarMiniView.kt", "CarImageView.kt")) {
            val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$rel")
            assertTrue(src.contains("CarImageLayer(context)"), "$rel phải đi qua CarImageLayer")
            assertFalse(src.contains("CarImageStore.load") || src.contains("BitmapFactory"), "$rel không tự giải mã")
        }
    }
}
