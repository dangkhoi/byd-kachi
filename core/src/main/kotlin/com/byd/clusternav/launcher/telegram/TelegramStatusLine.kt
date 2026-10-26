package com.byd.clusternav.launcher.telegram

/**
 * Bảng trạng thái SỐNG (phần text của tin menu) — spec `kachi-telegram-buttons.html` R2/QD-03 (xương sống). Thuần
 * Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. `:app` đọc số đo thật (control xe + `MediaBridge.read()` +
 * pending) đổ vào [Snapshot]; lớp này CHỈ ghép chữ. Đọc không được ⇒ `null` ⇒ hiện "—" (QD-03, không bịa).
 *
 * Mọi nhãn TIẾNG ANH (owner 26/10 OQ3). Giá trị `null` = chưa biết (màn khuất / chưa đọc được) ⇒ "—".
 */
object TelegramStatusLine {

    /** Ảnh chụp trạng thái để ghép text. `null` ở trường nào = chưa biết trường đó. */
    data class Snapshot(
        val carLinked: Boolean,
        val gpsOk: Boolean?,          // null = chưa biết
        val acAuto: Boolean?,
        val tempC: Int?,
        val fan: Int?,
        val seatDriver: String?,      // đã định dạng sẵn, vd "cool 1" / "off" / null
        val seatPassenger: String?,
        val pendingPlace: String?,    // tên điểm đến chờ, null = chưa có
        val nowPlaying: String?,      // "title — artist", null = không phát
        val updatedHhmm: String,      // giờ cập nhật "HH:mm" (do :app đưa vào)
    )

    private fun v(x: Any?): String = x?.toString() ?: "—"
    private fun onOff(b: Boolean?): String = when (b) { true -> "ON"; false -> "OFF"; null -> "—" }

    /**
     * Ghép 4 dòng text. Dùng emoji + từ tiếng Anh ngắn (QD-19). Không Markdown để tránh ký tự đặc biệt của tên bài
     * làm hỏng parse — `:app` gửi `parse_mode` trống.
     */
    fun render(s: Snapshot): String = buildString {
        append(if (s.carLinked) "🚗 Kachi · BYD   ● car linked" else "🚗 Kachi · BYD   ○ car NOT linked")
        append('\n')
        append("❄️ AUTO ").append(onOff(s.acAuto))
            .append(" · ").append(v(s.tempC)).append(if (s.tempC != null) "°" else "")
            .append(" · fan ").append(v(s.fan))
        append('\n')
        append("🪑 Driver: ").append(s.seatDriver ?: "—")
            .append(" · Passenger: ").append(s.seatPassenger ?: "—")
        append('\n')
        append("📍 ")
        append(if (s.pendingPlace != null) "To: ${s.pendingPlace}" else "No destination")
        append(" · GPS ").append(gps(s.gpsOk))
        append(" · ♪ ").append(s.nowPlaying ?: "—")
        append('\n')
        append("updated ").append(s.updatedHhmm)
    }

    private fun gps(ok: Boolean?): String = when (ok) { true -> "✓"; false -> "✗"; null -> "—" }
}
