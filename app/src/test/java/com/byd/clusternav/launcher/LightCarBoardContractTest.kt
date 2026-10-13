package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R24 — owner 13/10 *"cái widget đèn cũng làm vậy luôn, có hình xe và chấm tròn hay icon đèn xung quanh, đúng vị trí đèn,
 * nhấn tắt, nhấn mở"*. Khoá tầng vẽ [CarIconBoardView]: điểm từ DỮ LIỆU ảnh (`:core`), chạm = ô nút THẬT (cùng đường ghi +
 * cổng chạm), vùng chạm không nền (bài học "mảng xanh"), không chữ trên màn, lùi lưới icon theo chỗ đo.
 */
class LightCarBoardContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher/"
    private val src = SourceRoots.codeOf(dir + "CarIconBoardView.kt")

    @Test fun `diem tu du lieu anh, vua cho do bang core`() {
        assertTrue(src.contains("CarIconBoard.place(tiles, art)"), "điểm = CarArt.spot qua :core")
        assertTrue(src.contains("CarImageStore.artOf(CarImageStore.builtinSignature(context))"), "cùng ảnh dựng sẵn với bảng Kính")
        assertTrue(src.contains("CarIconBoard.fits("), "vừa chỗ quyết ở :core")
        assertTrue(src.contains("IconBoardView(context, tiles, data)"), "lùi lưới icon")
        listOf("\"light_", "\"headl\"", "\"drl\"", "\"readl\"", "\"g_lights").forEach { assertFalse(src.contains(it), "chép mã $it") }
    }

    @Test fun `cham = o nut that, vung cham khong nen, diem nay`() {
        assertTrue(src.contains("kit.tile(a, null, icon = false)") && src.contains("View.GONE"), "ô nút thật ẩn")
        assertTrue(src.contains("val on = kind(t) != CarDots.Kind.OPEN") && src.contains("kit.set(a.id, if (on) 1 else 0)"), "chạm = đảo cái ĐANG THẤY, cùng hàm ghi ô")
        assertTrue(src.contains("LampHold.shared.put(a.id, on,"), "POLISH2: giữ đích lạc quan tới khi xe xác nhận / ~3 s")
        assertTrue(src.contains("hidden[a.id]?.performClick()"), "ô không đặt tường minh ⇒ performClick ô nút (gated)")
        assertTrue(src.contains("layer.pulse(t)"), "phản hồi = điểm nảy")
        val zone = src.substringAfter("nodes[t] = View(context).apply {").substringBefore("addView(this)")
        assertFalse(zone.contains("background") || zone.contains("Ripple"), "vùng chạm không nền/sóng")
        assertTrue(src.contains("dots.drawIcon("), "điểm icon vẽ bằng bộ tô chấm chung")
        assertFalse(src.contains("TextView"), "không chữ trên màn")
        assertTrue(src.contains("R.string.klight_tap_off") && src.contains("R.string.klight_tap_on"), "đọc to việc cú chạm kế")
    }

    @Test fun `nhom ICONS di qua bang hinh xe`() {
        assertTrue(SourceRoots.codeOf(dir + "GroupBodies.kt").contains("GroupIconBoard.of(m)?.let { CarIconBoardView("))
        assertTrue(SourceRoots.codeOf(dir + "StateDotPainter.kt").contains("fun drawIcon("))
    }

    /**
     * 3.01 TAPFIX — owner 13/10 *"nhấn nút này nó xanh nút khác"* [ĐO logcat máy ảo: ~20 chạm chỉ ra drl/headl/readl]. Khoá:
     * chủ cú chạm quyết ở `:core` trên MỌI điểm vẽ (cả chỉ-xem), tâm lấy từ cùng hàm vẽ; điểm chỉ-xem nuốt cú chạm (lắc + đọc
     * to "chỉ xem"), không lệnh; nút trợ năng cho MỌI ô, ô vuông riêng không chồng; điểm có nút / chỉ-xem khác hình.
     */
    @Test fun `diem chi-xem so huu vung cua no, khong chuyen cho den ben canh`() {
        assertFalse(src.contains("CarIconBoard.zones("), "vùng k-d chỉ trên điểm có nút đã gỡ")
        assertTrue(src.contains("SpotTouch.owner(points(), x, y, SpotTouch.reachPx(touch))"), "chủ = điểm vẽ gần nhất, trong tầm")
        assertTrue(src.contains("spots.map { CarIconBoard.centerIn(it, f.left, f.top, f.width(), f.height()) }"), "tâm chạm = tâm vẽ")
        assertTrue(src.contains("override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = onCar"), "bảng tự nhận chạm")
        assertTrue(src.contains("ownerAt(ev.x, ev.y) === t"), "chỉ bắn khi THẢ vẫn trên cùng chủ lúc NHẤN")
        val press = src.substringAfter("private fun press(t: IconTile) {").substringBefore("\n    }")
        assertTrue(press.contains("if (a == null) {") && press.contains("layer.shake(t)") && press.contains("R.string.klight_view_only"))
        assertTrue(press.indexOf("return") < press.indexOf("tap(a)"), "chỉ-xem return TRƯỚC mọi đường ghi")
        assertTrue(src.contains("tiles.forEach { t ->") && src.contains("SpotTouch.ownSquare("), "nút trợ năng cho MỌI ô, không chồng")
        // 3.01 POLISH (owner 13/10 *"phân biệt ra dạng icon cho hiểu"*): có nút = đĩa tông (drawIcon tappable) · chỉ-xem = đèn báo trần.
        assertTrue(src.contains("if (s.tile.action != null) dots.drawIcon(") && src.contains("else dots.drawLamp("), "điểm có nút / chỉ-xem khác HÌNH")
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { d ->
            assertTrue(SourceRoots.text("src/main/res/$d/strings_kachi_safety301.xml").contains("name=\"klight_view_only\""), "$d thiếu chỉ xem")
        }
    }
}
