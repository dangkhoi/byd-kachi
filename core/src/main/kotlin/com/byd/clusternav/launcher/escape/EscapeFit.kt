package com.byd.clusternav.launcher.escape

/**
 * ═══ 2.98 · R7 (owner 09/10 #2) — KHUNG TASK so với khung Ô: đẩy thanh tiêu đề freeform LÊN TRÊN ô (thuần, `:core`) ══════════
 *
 * Owner: *"bỏ luôn cái thanh xám… top nó phải fix khung"*. Thanh tiêu đề freeform (□ ✕) nằm TRONG cửa sổ app (AOSP Q
 * `DecorView.updateDecorCaptionStatus` → `DecorCaptionView`; hiện khi `WindowConfiguration.hasWindowDecorCaption()` = activity
 * `standard` + freeform, `isFillingScreen` chỉ đúng ở FULLSCREEN — [ĐO nguồn android-10.0.0_r47 `DecorView.java:2044-2072`, `:2138`,
 * `WindowConfiguration.java:645-648`; ĐO jadx fw 2606 `framework.jar` giống hệt]) ⇒ không tắt được từ ngoài. Cách duy nhất để nội
 * dung app bắt đầu ĐÚNG đỉnh ô là đặt khung task CAO hơn ô một thanh tiêu đề ([liftPx]) — thanh tiêu đề nằm trên vùng đầu màn
 * nhà Kachi (lớp che gương nó như mọi khi).
 *
 * ## Hệ đẩy khung xuống dưới vùng ổn định — [ĐO nguồn + ĐO máy ảo]
 * AOSP Q `TaskRecord.resolveOverrideConfiguration` (`TaskRecord.java:2222-2246` (intersect `:2235`, dời `:2242-2246`); jadx fw 2606 giống hệt): task FREEFORM bị
 * `parentBounds.intersect(getStableRect)` rồi `offsetTop = parentBounds.top - bounds.top > 0 ⇒ bounds.offset(0, offsetTop)` — DỜI cả
 * khung xuống (giữ cỡ), không kẹp. `mStable.top = max(status_bar_height, cutout an toàn)` (`DisplayPolicy.java:1588-1591`). [ĐO máy ảo
 * 09/10] `mStable=[0,36]…`: xin `[19,25][1901,985]` ⇒ hệ đặt `[19,36][1901,996]`. Xe: `status_bar_height_portrait = 56dp`
 * (framework-res fw 2606) = 84 px @240dpi = đúng `StatusBar [0,0][1920,84]` [ĐO xe 14/09], cutout 80 ⇒ [SUY] đỉnh ổn định 84 ⇒ nội
 * dung app chỉ lên được tới 84 + thanh tiêu đề ≈ 148, ô bắt đầu ở 89 ⇒ còn ~59 px đầu ô là [EscapeCoverPlan.Kind.GAP] (vẽ NỀN sau
 * ô, không phải khay xám — xem đó). 🚗 đọc `dumpsys window displays | grep mStable` để chốt.
 *
 * Không đoán đỉnh ổn định: lần đầu xin khung lý tưởng, đọc lại thấy hệ DỜI xuống ⇒ học [learnedMinTop] (cả tiến trình, một
 * display) rồi xin lại khung đã trừ — CLAUDE.md §6 "tự đo rồi chọn".
 */
object EscapeFit {

    /**
     * Khung task cho ô [slot]: [liftPx] = 0 (không có lớp che — dịch vụ trợ năng chưa nối) ⇒ đúng khung ô như trước. Có lớp che
     * ⇒ đỉnh = `slot.top − liftPx`, nhưng không cao hơn [minTop] (đỉnh ổn định đã học) và không thấp hơn đỉnh ô; đáy/trái/phải = ô.
     */
    fun taskRect(slot: PxRect, liftPx: Int, minTop: Int?): PxRect {
        if (liftPx <= 0) return slot
        // Soát Pass 9 [P3]: chưa học ⇒ sàn là mép màn (0), không xin đỉnh âm (`am task resize … -41 …` — chưa đo `am` nhận số âm không).
        val top = maxOf(slot.top - liftPx, minTop ?: 0).coerceAtMost(slot.top)
        return PxRect(slot.left, top, slot.right, slot.bottom)
    }

    /**
     * Soát Pass 9 [P2] — gợi lại đỉnh ổn định từ DẤU sau khi tiến trình chết (BYD giết Kachi mỗi lần tắt máy ⇒ [SlotEscapeRun.TopMemory]
     * rỗng mỗi chuyến): dấu [marker] đang đúng khung thật [actual] và đỉnh của nó nằm CHẶT giữa `slot.top − liftPx` (khung lý tưởng)
     * và `slot.top` ⇒ đỉnh đó chỉ có thể là đỉnh ổn định đã học (xem [taskRect]). Không gợi được ⇒ `null`. Gợi sai (ô đã đổi
     * khung từ lúc ghi dấu) chỉ tốn một lần hệ dời khung ⇒ [learnedMinTop] sửa lại — đỉnh học chỉ tăng, không dao động.
     */
    fun seedMinTop(marker: PxRect, actual: PxRect?, slot: PxRect, liftPx: Int): Int? =
        marker.top.takeIf { liftPx > 0 && actual == marker && it > slot.top - liftPx && it < slot.top }

    /**
     * Khung đọc lại [actual] là [want] bị hệ DỜI xuống (cùng trái/phải/cỡ, đỉnh thấp hơn) ⇒ đỉnh ổn định = `actual.top`. Khác dạng
     * này (kẹp cỡ, dời ngang, không đọc được) ⇒ `null` — không học gì, bên gọi xử như đọc lại không khớp.
     */
    fun learnedMinTop(want: PxRect, actual: PxRect?): Int? =
        actual?.takeIf {
            it.left == want.left && it.right == want.right && it.height == want.height && it.top > want.top
        }?.top
}
