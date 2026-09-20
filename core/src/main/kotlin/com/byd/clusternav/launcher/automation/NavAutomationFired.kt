package com.byd.clusternav.launcher.automation

/**
 * ═══ SỔ ĐÃ-DẪN — `id luật` → `khoá ngày`, mã hoá một dòng một luật ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2.4 (*"dẫn 1 lần / khung giờ / ngày"*). Thuần Kotlin (`:core`, cấm
 * `android.*`) ⇒ kiểm off-car. Đây là thứ [ScheduledNavPolicy.firstToLaunch] nhận vào tham số `firedDays`.
 *
 * ## Vì sao codec ở `:core` chứ không phải vài dòng `getString` trong `:app`
 * Cùng lẽ [NavAutomationBook] / `SavedPlaces` / `SlotCodec`: dạng chuỗi của một thứ **lưu bền** khai đúng một
 * chỗ, ở nơi bài kiểm chạm tới được. Phép giải mã này có **ba** nhánh hỏng thật (dòng thiếu dấu `=` · id rỗng ·
 * dòng trùng id) và cả ba đều hỏng **im lặng** theo cùng một kiểu: sổ đọc ra thiếu một mục ⇒ luật đó tưởng là
 * *"chưa dẫn hôm nay"* ⇒ nó **nổ lại**, tức app dẫn đường mở lên lần thứ hai trong cùng một khung giờ. Để phép
 * này ở `:app` thì nó không có bài canh nào (đường prefs không test off-car được).
 *
 * ## Dạng lưu
 * ```
 * r1=2026-09-20
 * r2=2026-09-19
 * ```
 * Đọc được bằng mắt (cứu tay qua `adb shell run-as … cat`), cùng lệ `saved_places` / `grid_layout`.
 *
 * ## ⚠ Khoá ngày là chuỗi ĐỐI CHIẾU, không phải một ngày để tính toán
 * [ScheduledNavPolicy] chỉ **so bằng nhau**, nên `:core` không mang một phép định dạng ngày thứ hai vào mình
 * (quy ước `yyyy-MM-dd` là của `:app`). Hệ quả phải biết: chuỗi nào cũng lưu được, kể cả chuỗi rỗng — nên
 * [put] **từ chối** ngày rỗng. Ngày rỗng lọt vào sổ là `lastFiredDay == today` thành đúng ở ca
 * [NavSkipReason.NO_DAY_KEY], tức chặn luôn một luật vĩnh viễn.
 */
object NavAutomationFired {

    /** Ngăn giữa `id` và khoá ngày. Không bị khử ở đâu vì cả hai vế đều không phải chữ người dùng gõ. */
    private const val PAIR = '='

    /**
     * Sổ rỗng — dùng khi chưa lưu gì. Có hằng để chỗ gọi không phải viết `emptyMap()` rồi tự hỏi nó nghĩa là
     * *"chưa dẫn bao giờ"* hay *"không đọc được"*: ở đây hai ca ấy **cùng** nghĩa (chưa dẫn), và đó là hướng sai
     * an toàn — tệ nhất là dẫn thêm một lần, chứ không bao giờ là chặn mất một lượt đi.
     */
    val EMPTY: Map<String, String> = emptyMap()

    fun encode(fired: Map<String, String>): String =
        fired.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString("\n") { "${it.key}$PAIR${it.value}" }

    /**
     * Đọc sổ từ chuỗi đã lưu. Dòng hỏng ⇒ **bỏ đúng dòng đó**, không ném và không bỏ cả sổ (chuỗi này đến từ đĩa
     * và sửa tay được).
     *
     * Dòng trùng `id` ⇒ giữ **dòng đầu**, cùng luật [NavAutomationBook.decode] — một phép chọn tuỳ tiện ở đây sẽ
     * làm chuỗi trên đĩa đọc ra khác nhau giữa các lần.
     */
    fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return EMPTY
        val out = LinkedHashMap<String, String>()
        raw.split('\n').forEach { line ->
            if (line.isBlank()) return@forEach
            val at = line.indexOf(PAIR)
            if (at <= 0) return@forEach                     // không có dấu `=`, hoặc id rỗng
            val id = line.substring(0, at).trim()
            val day = line.substring(at + 1).trim()
            if (id.isEmpty() || day.isEmpty()) return@forEach
            out.putIfAbsent(id, day)
        }
        return out
    }

    /**
     * Đóng dấu *"luật [id] đã dẫn ngày [today]"*. Ngày rỗng ⇒ sổ **không đổi** (xem ⚠ ở KDoc lớp).
     *
     * Trả về sổ MỚI (không sửa tại chỗ): chỗ gọi ghi cả chuỗi xuống prefs trong cùng một lượt, nên một bản đồ
     * thay đổi được chỉ mở đường cho hai bề mặt cùng giữ một tham chiếu rồi lệch nhau.
     */
    fun put(fired: Map<String, String>, id: String, today: String): Map<String, String> {
        if (id.isBlank() || today.isBlank()) return fired
        return LinkedHashMap(fired).also { it[id.trim()] = today.trim() }
    }

    /**
     * Bỏ dấu của những luật **không còn trong sổ luật** — gọi sau khi người dùng xoá một luật.
     *
     * Không phải dọn rác cho gọn: [NavAutomationBook.newId] cấp lại `id` đã **không còn ai dùng** (`r1` sau khi
     * xoá `r1`), nên một dấu mồ côi `r1=<hôm nay>` sẽ đóng dấu cho **luật mới vừa tạo** ⇒ luật ấy không chạy hôm
     * nay, im lặng. Đúng ca mà KDoc [NavAutomationBook.newId] đã lường, chỉ ở nửa còn lại của cặp.
     */
    fun prune(fired: Map<String, String>, liveIds: Set<String>): Map<String, String> =
        fired.filterKeys { it in liveIds }
}
