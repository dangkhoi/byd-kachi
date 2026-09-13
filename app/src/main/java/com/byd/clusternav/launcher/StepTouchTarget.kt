package com.byd.clusternav.launcher

import android.graphics.Rect
import android.graphics.Region
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ ĐÍCH CHẠM CỦA NÚT − / + TRONG Ô THANH NÚT XE ════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §3 **R7** + design-system §10 Pass 2 `[P1]`: *"nút − / + trên thanh
 * nút xe rộng 14–22dp — đích chạm nhỏ nhất màn"*. Yêu cầu: **≥ [Sp.TOUCH] mỗi chiều, glyph KHÔNG phóng to, bố cục
 * ô KHÔNG đổi hình**.
 *
 * ## ⚠⚠ Vì sao KHÔNG nới chính cái nút (minWidth / minHeight) — đã ĐO, đã hỏng một lần
 * KDoc [KachiSpace.TOUCH_TIGHT] ghi lại phép đo: ô thanh nút ngang là `DOCK_TILE_W`×`DOCK_TILE_H` = 84×86dp, trừ
 * lề trong còn **68×70dp**, mà bề ngang phải chứa `[−] [giá trị] [+]`. *"Lần đầu tôi đặt 36dp và nó LÀM HỎNG ô:
 * 2×36 = 72 > 68 ⇒ chữ giá trị bị bóp, `22°` xuống hai dòng"*. Hai nút 48dp thì cần 96 > 68 — chắc chắn hỏng.
 * Nói cách khác **đích chạm 48dp bằng kích thước VIEW là bất khả** ở ô 84dp; đòi nó là đòi đổi hình cái ô.
 *
 * ## Cách đúng: nới **VÙNG NHẬN CHẠM**, không nới view
 * [TouchDelegate] cho phép ô cha nhận cú chạm rồi chuyển cho nút con — kích thước vẽ không đổi một pixel. Ô cho
 * chế độ STEP **không có lệnh chạm nào khác** (`tileStep` không đặt `setOnClickListener` cho ô), nên toàn bộ nửa
 * trái/nửa phải quanh hàng nút là chỗ trống có thể giao cho − và +.
 *
 * ## Nói THẲNG giới hạn còn lại (§2 CLAUDE.md — cơ chế vs quy kết)
 * [ĐO số học] ô ngang 84dp ⇒ mỗi bên nhiều nhất **42dp** bề ngang (hai vùng không được chồng nhau, chồng là chạm
 * một chỗ ra hai lệnh). Nên kết quả thật là **42×48dp** ở thanh ngang và **50×48dp** ở thanh dọc
 * (`DOCK_TILE_W_VERTICAL` = 100). Đủ 48×48 hai chiều chỉ đạt được nếu ô rộng ≥ 96dp — tức **đổi hình thanh nút**,
 * việc thuộc quyền owner, không phải của bản vá này. Diện tích vẫn tăng [ĐO] từ ~20×32 = 640dp² lên 2016dp² (3.1×).
 */
object StepTouchTarget {

    /**
     * PHẦN THUẦN — tính khung nhận chạm của một nút, **không đụng Android** nên kiểm được off-device.
     *
     * Nới quanh TÂM nút cho đủ [min] mỗi chiều, rồi **trượt vào trong** (không cắt cụt) khi đụng biên: trượt giữ
     * đủ [min] khi còn chỗ, trong khi cắt cụt thì mất luôn phần vừa nới ra. Chỉ khi làn/ô hẹp hơn [min] thật thì
     * kết quả mới nhỏ hơn — và khi đó nó đúng bằng bề rộng có thật, không nói dối.
     *
     * @param centreX tâm nút theo trục ngang, trong hệ toạ độ của ô cha.
     * @param centreY tâm nút theo trục dọc, trong hệ toạ độ của ô cha.
     * @param laneLeft/[laneRight] LÀN của nút này — nửa ô thuộc về nó; hai làn không được giao nhau.
     * @param hostHeight chiều cao ô cha.
     * @return `[trái, trên, phải, dưới]`.
     */
    fun zone(centreX: Int, centreY: Int, min: Int, laneLeft: Int, laneRight: Int, hostHeight: Int): IntArray {
        val (l, r) = span(centreX, min, laneLeft, laneRight)
        val (t, b) = span(centreY, min, 0, hostHeight)
        return intArrayOf(l, t, r, b)
    }

    /** Một trục: nới quanh [centre] cho đủ [min], trượt vào trong khi vượt `[lo, hi]`, kẹp cuối cùng. */
    private fun span(centre: Int, min: Int, lo: Int, hi: Int): Pair<Int, Int> {
        var a = centre - min / 2
        var b = a + min
        if (a < lo) { b += lo - a; a = lo }
        if (b > hi) { a -= b - hi; b = hi }
        return a.coerceAtLeast(lo) to b.coerceAtMost(hi)
    }

    /**
     * Giao nửa trái của [host] cho [minus] và nửa phải cho [plus].
     *
     * Chạy trong `post` vì lúc dựng ô chưa view nào có kích thước; gắn lại mỗi lượt bố cục (`addOnLayoutChange`)
     * vì ô thanh nút đổi cỡ theo viền thanh (ngang 84 / dọc 100) và theo vùng (thanh nút vs ô nhóm).
     */
    fun attach(host: View, minus: View, plus: View) {
        val min = dpi(host.context, Sp.TOUCH)
        fun apply() {
            if (host.width <= 0 || minus.width <= 0 || plus.width <= 0) return
            val mid = (centreX(minus, host) + centreX(plus, host)) / 2
            host.touchDelegate = SplitDelegate(
                host,
                listOf(
                    Zone(rect(zone(centreX(minus, host), centreY(minus, host), min, 0, mid, host.height)), minus),
                    Zone(rect(zone(centreX(plus, host), centreY(plus, host), min, mid, host.width, host.height)), plus),
                ),
            )
        }
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }
        host.post { apply() }
    }

    private fun centreX(v: View, host: View): Int = offset(v, host, vertical = false) + v.width / 2

    private fun centreY(v: View, host: View): Int = offset(v, host, vertical = true) + v.height / 2

    /** Lệch của [v] so với [host] — cộng dồn qua các cha trung gian (nút nằm trong một hàng, hàng nằm trong ô). */
    private fun offset(v: View, host: View, vertical: Boolean): Int {
        var acc = 0
        var cur: View = v
        while (cur !== host) {
            acc += if (vertical) cur.top else cur.left
            cur = cur.parent as? View ?: return acc
        }
        return acc
    }

    private fun rect(b: IntArray) = Rect(b[0], b[1], b[2], b[3])

    private class Zone(val bounds: Rect, val target: View)

    /**
     * [TouchDelegate] **nhiều vùng** — nền tảng chỉ cho MỘT delegate trên một view, mà ở đây có hai nút.
     *
     * Giữ vùng đã trúng lúc `ACTION_DOWN` cho tới hết cử chỉ: không giữ thì ngón tay trượt qua giữa ô sẽ đổi đích
     * giữa chừng và cú bấm rơi vào nút kia.
     */
    private class SplitDelegate(host: View, private val zones: List<Zone>) : TouchDelegate(Rect(), host) {
        private var active: Zone? = null

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                active = zones.firstOrNull { it.bounds.contains(event.x.toInt(), event.y.toInt()) }
            }
            val z = active ?: return false
            // Đặt toạ độ về TÂM nút đích: nút không tự biết cú chạm tới từ đâu, và một toạ độ ngoài khung của nó
            // sẽ bị chính nó coi là "trượt ra ngoài" ⇒ mất cú bấm.
            val copy = MotionEvent.obtain(event).apply { setLocation(z.target.width / 2f, z.target.height / 2f) }
            val handled = try { z.target.dispatchTouchEvent(copy) } finally { copy.recycle() }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                active = null
            }
            return handled
        }

        /**
         * ⚠ **TalkBack không dùng [onTouchEvent]** — nó hỏi cây trợ năng, và cây đó chỉ biết vùng chạm mở rộng nếu
         * delegate khai ra ở đây. Không override ⇒ `TouchDelegate` cơ sở trả về info dựng từ `Rect()` RỖNG mà lớp
         * này truyền cho ctor (nó phải rỗng: bộ định tuyến thật là [zones], xem KDoc lớp) ⇒ với người dùng trợ năng
         * vùng chạm vẫn là **20×32dp** như trước bản vá, tức cả `StepTouchTarget` không có tác dụng với họ.
         *
         * [ĐO] chữ ký, đọc thẳng `android.jar` của `compileSdk = 37` (CLAUDE.md §3, không dựa trí nhớ):
         * `TouchDelegate.getTouchDelegateInfo()` → `AccessibilityNodeInfo$TouchDelegateInfo`, và
         * `TouchDelegateInfo(java.util.Map<Region, View>)` là ctor công khai duy nhất. Cả hai **API 29**, mà
         * `minSdk = 29` ⇒ **không cần rẽ nhánh SDK**.
         *
         * Thứ tự [zones] được giữ (`associate` trả `LinkedHashMap`): trái (−) trước, phải (+) sau — cùng thứ tự
         * người dùng trợ năng nghe khi duyệt ô.
         *
         * **Dựng MỘT lần** (`by lazy`) đúng như bản cơ sở của nền tảng làm (`TouchDelegate` nhớ vào `mTouchDelegateInfo`):
         * `View.onInitializeAccessibilityNodeInfo` gọi hàm này **mỗi lần** dựng một nút trợ năng cho ô, mà
         * [android.graphics.Region] là đối tượng có phần cấp phát NATIVE. Nhớ được là vì delegate **bất biến** —
         * [zones] là `val` và mỗi lượt bố cục [attach] dựng hẳn một `SplitDelegate` mới thay vì sửa cái cũ.
         */
        private val info: AccessibilityNodeInfo.TouchDelegateInfo by lazy {
            AccessibilityNodeInfo.TouchDelegateInfo(zones.associate { Region(it.bounds) to it.target })
        }

        override fun getTouchDelegateInfo(): AccessibilityNodeInfo.TouchDelegateInfo = info
    }
}
