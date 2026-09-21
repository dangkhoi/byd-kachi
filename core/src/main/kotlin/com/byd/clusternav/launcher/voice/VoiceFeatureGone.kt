package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ D3 · TÍNH NĂNG **ĐÃ BỎ** / **KHÔNG CÓ ĐƯỜNG ĐIỀU KHIỂN** — trả lời lịch sự, đúng tên ═══════════════════
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car. Là **DỮ LIỆU**, không phải mã: thêm một dòng là nói được thêm một câu.
 *
 * ## Vì sao phải có — [ĐO xe 2026-09-18] (`oncar-voice-cases-findings-2026-09-18.md` §D3)
 * 31/90 lượt log ra *"không hiểu"*, và trong đó có những câu người lái nói **hoàn toàn đúng** về một tính năng
 * mà Kachi **từng có rồi bỏ** (dây an toàn — (N) ADAS-PURGE; gập gương · theo dõi sạc · đổi chế độ lái — (V)
 * FEATURE-FILTER, owner chấm NO) hoặc **chưa bao giờ điều khiển được** (đèn khẩn cấp · xi-nhan: bộ đăng ký chỉ
 * có datum ĐỌC `emergency_alarm` / `light_left_turn`, không có nút). Câu *"Không tìm thấy thứ đó trong xe"* ở đó
 * là **sai sự thật** — thứ đó có trên xe, chỉ là Kachi không làm.
 *
 * ## …và nó còn chặn một lỗi AN TOÀN đo được
 * [ĐO cùng log] *"bật đèn khẩn cấp"* → **`Control(trunk, 1)`** = **mở cốp**. Cơ chế: câu không khớp gì ⇒
 * [VoicePhoneticMatch] "sửa" *"cấp"* thành *"cốp"* rồi đọc lại. Bảng này được hỏi **trước** tầng chữa chính tả
 * (xem chỗ gọi ở [VoiceIntentParser]), nên câu ấy dừng lại ở một lời từ chối lịch sự thay vì một lệnh thân xe.
 *
 * ## Luật khai (viết ra để lần sau khỏi cãi)
 *  1. **Cụm ≥ 2 từ.** Một từ đứng trần (*"sạc"*, *"gương"*) sẽ cướp câu của người khác — *"chỉ đường đến trạm
 *     sạc gần nhất"* là một ca THẬT đang có bài canh.
 *  2. **Chỉ khai cụm mà Kachi KHÔNG làm.** Còn đường ĐỌC thì đường đọc vẫn phải thắng: bảng này chỉ được hỏi khi
 *     câu **đã** không hiểu được (xem chỗ gọi), nên *"xem chế độ lái"* → `Read(op_mode)` không hề đi qua đây.
 *  3. **Ghi rõ [removed]**: `true` = Kachi từng có rồi bỏ (owner quyết) · `false` = bộ đăng ký chưa bao giờ có
 *     nút. Hai câu trả lời khác nhau vì hai việc khác nhau — gộp lại là nói dối một trong hai.
 */
internal object VoiceFeatureGone {

    /**
     * Một tính năng Kachi không làm.
     *
     * @property words cụm đã bỏ dấu (đúng dạng [VoiceLexicon.deaccent] trả về), khớp theo **dãy**.
     * @property label tên tính năng để đọc lên (VI) — nói tên thay vì nói *"thứ đó"*.
     * @property labelEn tên tiếng Anh.
     * @property removed `true` = đã bỏ theo quyết định owner · `false` = chưa bao giờ có nút điều khiển.
     */
    data class Gone(
        val words: List<String>,
        val label: String,
        val labelEn: String,
        val removed: Boolean,
    )

    /**
     * Bảng cụm → tính năng. Mỗi dòng kèm nguồn quyết định; **cụm dài xét trước** (cùng luật [VoiceGrammar]).
     *
     * ⚠ `light_left_turn` / `light_right_turn` / `emergency_alarm` / `op_mode` vẫn là datum ĐỌC có thật — đó là lý
     * do [removed] của bốn dòng đầu nói về **nút**, không nói về thông tin. (`mirror_fold` thì đã purge hẳn ở
     * WP8, nên cụm *"gập gương"* nay đúng nghĩa *"đã bỏ"* ở cả hai vế.)
     */
    val ALL: List<Gone> = listOf(
        // Chưa bao giờ có nút: bộ đăng ký chỉ có datum ĐỌC `emergency_alarm` (*"Cảnh báo khẩn"*).
        Gone(listOf("den", "khan", "cap"), "đèn khẩn cấp", "the hazard lights", removed = false),
        Gone(listOf("den", "canh", "bao"), "đèn khẩn cấp", "the hazard lights", removed = false),
        // Chưa bao giờ có nút: chỉ có datum ĐỌC `light_left_turn` / `light_right_turn`.
        Gone(listOf("xi", "nhan"), "xi-nhan", "the indicators", removed = false),
        // (N) ADAS-PURGE 2026-09-16 — owner gỡ toàn bộ ADAS/an toàn khỏi launcher.
        Gone(listOf("day", "an", "toan"), "dây an toàn", "the seat belts", removed = true),
        Gone(listOf("diem", "mu"), "cảnh báo điểm mù", "blind-spot warning", removed = true),
        Gone(listOf("chuyen", "lan"), "cảnh báo chuyển làn", "lane-change warning", removed = true),
        Gone(listOf("cam", "bien", "do"), "cảm biến đỗ", "the parking sensors", removed = true),
        // (V) FEATURE-FILTER 2026-09-17 — owner chấm NO cho nút gập gương, mục sạc, nút đổi chế độ lái.
        Gone(listOf("gap", "guong"), "gập gương", "folding the mirrors", removed = true),
        Gone(listOf("gap", "kieng"), "gập gương", "folding the mirrors", removed = true),
        Gone(listOf("sac", "pin"), "theo dõi sạc", "charge monitoring", removed = true),
        Gone(listOf("dang", "sac"), "theo dõi sạc", "charge monitoring", removed = true),
        // ── UX-OVERHAUL · WP8 2026-09-20 — owner purge 37 mã BỎ; năm cụm dưới là những cụm người lái VẪN có thể
        // nói (họ từng bấm được chúng), nên phải trả lời đúng tên thay vì "không tìm thấy".
        Gone(listOf("do", "sang", "hud"), "độ sáng HUD", "the HUD brightness", removed = true),
        Gone(listOf("hud", "kinh", "lai"), "HUD kính lái", "the windscreen HUD", removed = true),
        Gone(listOf("den", "vien"), "đèn viền", "the ambient light", removed = true),
        Gone(listOf("gat", "mua"), "gạt mưa", "the wipers", removed = true),
        Gone(listOf("muc", "tai", "tao"), "mức tái tạo", "the regen level", removed = true),
        // ⚠ **KHÔNG** khai `["che","do","lai"]`, dù nút `drive_mode` đã bị gỡ. [ĐO off-car 2026-09-18] thêm nó thì
        // *"xe đang ở chế độ lái nào"* — một câu log ghi là ĐANG CHẠY ĐÚNG (`Read(op_mode)`, §D4 của findings) —
        // rơi vào FEATURE_GONE, vì bảng này được hỏi TRƯỚC tầng chữa chính tả và chính tầng ấy mới sửa *"xe"* →
        // *"xem"* cho câu đó. Datum ĐỌC `op_mode` vẫn còn, nên *"đổi chế độ lái"* dừng ở MISMATCH (*"việc đó
        // không đi với thứ đó"*) — một câu đã đúng, và nó không giết đường đọc.
    ).sortedByDescending { it.words.size }

    /**
     * ═══ WP8 · TỪ **CHẶN CỨNG**: câu có nó thì KHÔNG bao giờ thành lệnh xe ══════════════════════════════════════
     *
     * [match] chỉ được hỏi khi câu **đã** không hiểu được, nên nó không cứu được ca câu hiểu **SAI**. [ĐO off-car
     * 2026-09-20 sau purge] *"bật HUD kính lái"* ra `Control(window, 1)` = **hạ kính lái**: cụm *"kính lái"* là từ
     * đồng nghĩa của `window` từ 1.66, nên khi nhãn dài *"HUD kính lái"* mất đi thì phần đuôi thắng.
     *
     * `hud` đủ điều kiện làm từ chặn cứng: [ĐO grep sau purge] không nhãn/từ-đồng-nghĩa nào còn chứa nó, nên cổng
     * không thể cướp câu của ai. Thêm từ vào đây phải kiểm đúng điều kiện ấy — một từ còn sống mà vào đây thì nó
     * giết luôn tính năng đang chạy.
     */
    val HARD_BLOCK: Set<String> = setOf("hud")

    /** Câu [t] có chứa một từ chặn cứng không — hỏi TRƯỚC mọi phép khớp (xem [HARD_BLOCK]). */
    fun blocked(t: List<Token>): Boolean = t.any { it.norm in HARD_BLOCK }

    /** Tính năng mà câu [t] đang nói tới, hoặc `null`. Khớp ở BẤT KỲ vị trí — cụm dài xét trước. */
    fun match(t: List<Token>): Gone? =
        ALL.firstOrNull { g -> t.indices.any { VoiceLexicon.phraseAt(t, it, g.words) } }

    /**
     * Câu trả lời cho [g] — hai câu cho hai việc khác nhau (xem luật khai §3).
     *
     * Ở `:core` vì nó ghép **tên tính năng** vào giữa, đúng lý do đã ghi ở KDoc [VoiceReply].
     */
    fun reply(g: Gone): String = if (g.removed) {
        Strings.t(
            "Tính năng ${g.label} đã bỏ khỏi Kachi — dùng màn hình của xe",
            "${g.labelEn.replaceFirstChar { it.uppercase() }} was removed from Kachi — use the car's own screen",
        )
    } else {
        Strings.t(
            "Kachi chưa điều khiển được ${g.label} — dùng nút trên xe",
            "Kachi cannot control ${g.labelEn} yet — use the car's own control",
        )
    }
}
