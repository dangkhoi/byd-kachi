package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlace
import com.byd.clusternav.launcher.SavedPlaces

/**
 * ═══ ĐIỂM ĐẾN CHỜ — MÔ HÌNH + MÃ HOÁ MỘT DÒNG, **NGUỒN DUY NHẤT** ═════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-telegram-remote.html` R1 · R4. Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car.
 *
 * ## Vai
 * Một "điểm đến đặt sẵn" — do người dùng đặt TRƯỚC khi lên xe (qua Telegram hoặc ô nhập tay), lưu bền **theo hồ
 * sơ**. Lúc `TripStart` lên xe, [com.byd.clusternav.launcher.automation.PendingNavPolicy] đọc nó TRƯỚC lịch dẫn
 * đường; có pending chưa dùng ⇒ bắn dẫn + đóng dấu sổ đã-dẫn (chặn lịch) + đánh dấu [consumed] (một-lần).
 *
 * ## Vì sao bọc [SavedPlace] thay vì tự giữ query/toạ độ
 * [SavedPlace] đã quyết định *"một điểm đến gồm những gì"* (tên · chữ · lat · lng tuỳ chọn) và
 * `com.byd.clusternav.launcher.voice.VoiceNavProviders.expand` đã biết nhân nó ra mỗi app một ý-định. Dựng lại
 * một mô hình điểm-đến thứ hai là mở đường cho hai định nghĩa lệch nhau (bài học `unitPrefs` ×4 bản).
 *
 * ## Dạng lưu: MỘT dòng, năm trường ngăn bằng `|`
 * ```
 * Office|Keangnam Landmark 72|21.0045|105.8412|gmaps+vietmap
 * Nhà|123 Nguyễn Trãi||gmaps
 * ```
 * Trường thứ sáu [setAtMs] và cờ [consumed] lưu RIÊNG (xem [PendingNavStore] ở `:app`): chúng là trạng thái
 * vòng đời, không phải nội dung điểm đến — trộn vào một dòng làm chuỗi điểm đến đổi mỗi lần bắn.
 *
 * ## ⚠ Ký tự ngăn bị KHỬ ở cửa VÀO
 * Cùng bài học `SlotCodec.SEP` / `SavedPlaces`: tên + địa chỉ là chữ người dùng gõ (hoặc từ Telegram), một `|`
 * lọt vào làm **mất cả bản ghi** im lặng. [SavedPlaces.sanitize] đã khử ở cửa dựng [SavedPlace]; ở đây khử
 * lại danh sách app vì nó cũng đi vào cùng dòng.
 *
 * @property place điểm đến (đã khử, hợp lệ — [SavedPlaces.of] bảo đảm).
 * @property apps mã app dẫn đường (`gmaps` · `vietmap` · `waze`), ≥ 1. Nối `+` khi lưu (như [ScheduledNavRule]).
 */
data class PendingDestination(
    val place: SavedPlace,
    val apps: List<String>,
) {
    /** Danh sách app nối `+` để lưu (như `ScheduledNavRule.navApp`). */
    val appsEncoded: String get() = apps.joinToString("+")
}

object PendingDestinations {

    /** Ngăn TRƯỜNG. Bị khử khỏi mọi chữ người dùng/Telegram gõ nên không thể xuất hiện trong dữ liệu. */
    private const val FIELD = '|'

    /** Số trường của một dòng điểm đến hợp lệ: tên · địa chỉ · lat · lng · app. */
    private const val FIELDS = 5

    /** Ngăn phần tử danh sách app. Cũng bị khử khỏi chữ người dùng, cùng lý do [FIELD]. */
    private const val APP_SEP = '+'

    /**
     * Dựng một điểm đến chờ, hoặc `null` khi không dùng được.
     *
     * `null` chứ không phải điểm "gần đúng" (cùng lẽ [SavedPlaces.of] / [ScheduledNavRules.of]): một pending
     * không tên / không địa chỉ / không app thì vừa không dẫn được, vừa làm người dùng tưởng đã đặt xong. Chỗ
     * gọi (Telegram / ô nhập tay) nói ra được chỗ sai thay vì lưu một dòng chết.
     *
     * @param coords chuỗi *"lat, lng"* (tuỳ chọn) — hỏng/rỗng ⇒ pending VẪN hợp lệ, chỉ không có toạ độ (VietMap
     *   bỏ qua, Google Maps dùng chữ) — đúng ngữ nghĩa [SavedPlace.hasCoords].
     */
    fun of(name: String, query: String, apps: List<String>, coords: String? = null): PendingDestination? {
        val place = SavedPlaces.of(name, query, coords) ?: return null
        val cleanApps = apps.map { sanitizeApp(it) }.filter { it.isNotEmpty() }
        if (cleanApps.isEmpty()) return null
        return PendingDestination(place, cleanApps)
    }

    /** Khử ký tự ngăn khỏi một mã app; mã app vốn chỉ chữ+số (`gmaps`) nên khử là đủ, không cần dạng khác. */
    private fun sanitizeApp(raw: String): String =
        raw.trim().filter { it != FIELD && it != APP_SEP && it != '\n' && it != '\r' }

    /** MỘT dòng cho dạng lưu nội dung điểm đến (KHÔNG gồm [setAtMs]/[consumed] — xem KDoc lớp). */
    fun encode(p: PendingDestination): String = listOf(
        p.place.name,
        p.place.query,
        p.place.lat?.toString().orEmpty(),
        p.place.lng?.toString().orEmpty(),
        p.appsEncoded,
    ).joinToString(FIELD.toString())

    /**
     * Một dòng → điểm đến; **hỏng ⇒ `null`**, không ném (chuỗi đến từ đĩa, sửa tay được).
     *
     * Mọi phép kiểm đi qua [of] ⇒ không đường thứ hai cho một pending không dùng được lọt vào bộ nhớ. Nửa cặp
     * toạ độ (chỉ lat) ⇒ pending không toạ độ, cùng luật [SavedPlaces.decode].
     */
    fun decode(line: String?): PendingDestination? {
        if (line.isNullOrBlank()) return null
        val f = line.split(FIELD)
        if (f.size != FIELDS) return null
        val lat = f[2].trim()
        val lng = f[3].trim()
        val coords = if (lat.isNotEmpty() && lng.isNotEmpty()) "$lat, $lng" else null
        val apps = f[4].split(APP_SEP).map { it.trim() }.filter { it.isNotEmpty() }
        return of(f[0], f[1], apps, coords)
    }
}
