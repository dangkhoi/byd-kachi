package com.byd.clusternav.launcher

/**
 * ═══ 3.1.6 R29 (perf §8.1) — GIỮ chọn họ khi nội dung một mặt đang CHUYỂN ĐỘNG, chọn lại MỘT lần khi dừng ═══════════════════
 *
 * [ĐO máy ảo 15/10, `docs/diagnostics/perf-315-2026-10-14.md` §8.1] xoay xe 3D: mỗi lượt layout của cửa sổ (~20 Hz trong lúc xoay)
 * gọi `OnLayoutChangeListener` của mặt khung 3D; nhãn lốp / đĩa ghế dời bằng translation ⇒ nền dưới chúng "đổi" ⇒ đo lại + chọn họ
 * (`decide` + `prepare` của chấm) ~53 lần/đợt thay vì 3 — luồng chính 160–190 → 240–380 ms/đợt.
 *
 * Luật (chung mọi mặt, không riêng xe 3D): lượt SỰ KIỆN (layout · gắn bộ vẽ · tô lại — không ép, không xin chọn lại) thấy một phần
 * tử đã đo nay ĐỔI CHỖ/CỠ ⇒ [hold]: không đo, không chọn, hẹn MỘT lượt [settle] [quietMs] sau lần dời CUỐI (mỗi lần dời đặt lại
 * hẹn — một hẹn duy nhất, không nhịp lặp); lượt đó đo lại cái đã dời và chọn lại họ chỉ khi nền thật sự đổi. Lượt đầy đủ (ép ·
 * chủ đề · ảnh · phần tử mới · tín hiệu dừng tường minh như `Car3dInk.settle`) ⇒ [solving]: bỏ hẹn đang chờ (nó thừa) ⇒ đúng MỘT
 * lượt sau khi dừng. Trễ họ cũ trong lúc dời: chấp
 * nhận (mực cùng họ; [ContrastInk.choose] vẫn có trễ khi chọn lại).
 *
 * THUẦN (hẹn giờ truyền vào: [post] / [unpost], ở `:app` là `View.postDelayed` / `removeCallbacks` của mặt gốc) ⇒
 * `InkMotionTest` khoá off-device. CHỈ luồng chính.
 */
class InkMotion(
    private val quietMs: Long,
    private val post: (Runnable, Long) -> Unit,
    private val unpost: (Runnable) -> Unit,
    private val settle: () -> Unit,
) {
    /** Có một lượt chọn lại đang hẹn. */
    var armed = false
        private set

    private val fire = Runnable { armed = false; settle() }

    /** Lượt sự kiện thấy nội dung dời ⇒ giữ; hẹn (lại) một lượt chọn [quietMs] sau lần dời này. */
    fun hold() {
        if (armed) unpost(fire)
        post(fire, quietMs)
        armed = true
    }

    /** Một lượt chọn đầy đủ sắp chạy ⇒ hẹn đang chờ thừa ⇒ bỏ. */
    fun solving() {
        if (!armed) return
        unpost(fire)
        armed = false
    }

    companion object {
        /** Lặng bao lâu sau lần dời cuối thì coi là DỪNG (quán tính xoay dừng hẳn < 1 khung; cuộn danh sách dừng ~1 khung). */
        const val QUIET_MS = 200L
    }
}
