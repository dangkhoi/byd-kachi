package com.byd.clusternav.launcher

/**
 * ═══ 3.01 POLISH — MÀU ĐÈN BÁO + NHỊP XI-NHAN của điểm CHỈ-XEM trên bảng Đèn (thuần `:core`) ═══════════════════════════
 *
 * Owner 13/10: *"phân biệt ra dạng icon cho hiểu, với cả cái đèn xinhan có nhấp nháy theo xe không?"*. Điểm chỉ-xem vẽ như
 * ĐÈN BÁO trên đồng hồ (không đĩa, không vòng) — sáng đúng màu của loại đèn, để khác HÌNH hẳn với nút (đĩa tông + vòng).
 *
 * ## Màu — dữ liệu quy ước, không rẽ nhánh theo nhóm (CLAUDE.md §7)
 * Theo quy ước đèn báo ô tô (ISO 2575 / UN R121, màu đèn báo): pha = XANH DƯƠNG · cốt, sương mù trước, đèn hông, ban ngày =
 * XANH LÁ · sương mù sau = HỔ PHÁCH. Xi-nhan: đèn báo ISO là xanh lá, nhưng điểm nằm ĐÚNG chỗ bóng đèn trên hình xe ⇒ dùng màu
 * BÓNG THẬT (hổ phách) theo yêu cầu owner. Mã không có trong bảng ⇒ `null` ⇒ bộ vẽ dùng màu nhấn (không đoán màu).
 *
 * ## Nhịp xi-nhan — [ĐO đọc mã] số đọc KHÔNG phải pha bóng
 * `light_left_turn`/`light_right_turn` = `BYDAutoLightDevice.getLightStatus(4/5)` ([HalReadTables.readArg]), đọc ở nhịp CHẬM
 * 10 s (`CarStatusRepository` `slowMs`, `CarDataAdapter.readSlow`) — không thể theo pha bóng ~1,5 Hz [ĐO xe 09-24, spec
 * camera-turn-signal-hal-socket]. Nên khi số đọc nói BẬT, bảng tự nháy TẠI CHỖ ([litAt]) — KHÔNG đồng bộ pha với bóng thật.
 * ⚠ [ĐO xe 2026-09-25] getter này trên trim đã đo LUÔN trả 0 — nguồn xi-nhan thật là socket HAL helper. Từ 3.01 TURN, khi
 * socket ĐANG nối (camera xi-nhan BẬT, hoặc từ LAMPLIVE: màn chính bày đèn) [LampLive.overlay] đè số đọc; socket không chạy ⇒
 * vẫn là getter. Socket báo TRẠNG THÁI, không phải pha ⇒ nháy vẫn tại chỗ.
 */
object LampTone {

    enum class Tone(val argb: Int) {
        /** Hổ phách — đủ sáng để ≥ 3:1 trên bóng tối của điểm (bài canh `LampIndicatorContractTest`). */
        AMBER(0xFFFFB020.toInt()),
        GREEN(0xFF3DDC84.toInt()),
        BLUE(0xFF5AA9FF.toInt()),
    }

    private val TONES: Map<String, Tone> = mapOf(
        "light_left_turn" to Tone.AMBER, "light_right_turn" to Tone.AMBER,
        "light_rear_fog" to Tone.AMBER,
        "light_front_fog" to Tone.GREEN, "light_low_beam" to Tone.GREEN,
        "light_side" to Tone.GREEN, "light_drl" to Tone.GREEN,
        "light_high_beam" to Tone.BLUE,
    )

    /** Đèn tự NHÁY khi bật (đèn xi-nhan) — dữ liệu về bản chất đèn, không phải về nhóm/ô. */
    private val BLINKING: Set<String> = setOf("light_left_turn", "light_right_turn")

    fun of(datumId: String?): Tone? = datumId?.let { TONES[it] }

    fun blinks(datumId: String?): Boolean = datumId != null && datumId in BLINKING

    /** Nửa chu kỳ nháy: sáng [HALF_MS] · tắt [HALF_MS] ⇒ 1 000 / (2 × 333) ≈ 1,5 Hz (cùng tần số đo bóng thật 09-24). */
    const val HALF_MS = 333L

    /** Pha nháy sau [elapsedMs] kể từ lúc thấy BẬT: bắt đầu SÁNG (bật lên là thấy ngay). Âm ⇒ sáng. */
    fun litAt(elapsedMs: Long): Boolean = elapsedMs < 0 || (elapsedMs / HALF_MS) % 2 == 0L

    /** Còn bao lâu (ms, ≥ 1) tới mép pha kế — bộ vẽ hẹn đúng MỘT lần vẽ lại, không vòng khung hình. */
    fun nextEdgeIn(elapsedMs: Long): Long = if (elapsedMs < 0) 1 else (HALF_MS - elapsedMs % HALF_MS).coerceAtLeast(1)
}
