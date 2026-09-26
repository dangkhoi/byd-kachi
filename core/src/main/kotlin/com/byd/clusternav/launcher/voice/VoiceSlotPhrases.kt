package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.LayoutPreset

/**
 * ═══ MỆNH ĐỀ **"VÀO Ô SỐ N"** Ở DẠNG CÓ DẤU — để tầng NGHE bias được cái đuôi ═════════════════════════════════
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car. Cùng khuôn [VoiceLayouts.SPOKEN] (dạng có dấu, khai tay, canh bằng máy).
 *
 * ## Bệnh nó chữa — VOICE-SLOT-TAIL-CUT, [ĐO xe 2026-09-26] + [ĐO máy ảo 2026-09-26, 30 WAV thật]
 * Owner: *"mở vietmap vào ô số 2"* nói 10 lần được 1. Phát lại 30 bản thu thật của xe qua `cmd wav` trên máy ảo
 * (cùng mô hình `zipformer-vi-int8-2025-04-20`) cho ra **đúng** chuỗi mà xe nghe được, nên chuỗi nguyên nhân dưới
 * đây là [ĐO], không phải suy:
 *
 *  1. **Không phải VAD cắt.** Bản thu `…-184201` dài 3 400 ms, `VoiceVadTrim` cắt còn **2 600 ms** — mà tiếng nói
 *     thật hết ở **2 040 ms**: phần bị cắt là im lặng. Cả 13 bản thu câu-có-ô đều vậy (điểm cắt ≥ điểm hết tiếng).
 *  2. **Tiếng nói CÓ mang cái đuôi.** Cắt tay chính bản thu ấy rồi giải mã từng khúc: `[900..2600] ms` ⇒
 *     *"áp vào ô số một"*, `[1400..3400] ms` ⇒ *"ô số một"*. Giải mã **nguyên cửa sổ** thì chỉ ra *"mở vietmap
 *     một"* — tức bộ giải mã **bỏ ba chữ ở giữa**, không phải mic bỏ.
 *  3. **Không có một dòng hotword nào đỡ cái đuôi.** Soát tệp thật (`core/build/hotwords/hotwords-phrases.txt`,
 *     2 223 dòng): **0 dòng** chứa *"VÀO Ô"*. Đổi `voice_hotword_score` 2,0 → 3,0 → 4,0 cho ra **y hệt** một chuỗi
 *     trên cả 6 bản thu — đúng điều phải xảy ra khi đường đúng **không có** hotword nào để được cộng điểm.
 *
 * ⇒ Tệp này cấp đúng thứ còn thiếu: mệnh đề ô trở thành cụm hotword, nên đường giải mã *"… vào ô số một"* được
 * cộng điểm như mọi cụm lệnh khác. Nó **chỉ thêm dòng**, không bỏ dòng nào — cụm *"MỞ VIỆT MÁP"* và họ của nó
 * không đụng tới (CLAUDE.md §6: đường mới xuống cuối, không đảo đường đang chạy).
 *
 * ## Vì sao khai tay dạng CÓ DẤU, không sinh từ [VoiceLexicon]
 * [VoiceLexicon.SLOT_WORDS] khai **không dấu** (hợp đồng của tầng so khớp chữ), còn hotword phải **HOA CÓ DẤU**
 * mới mã hoá được bằng bảng BPE của mô hình VN (xem KDoc [SherpaHotwords]). Khôi phục dấu bằng máy đã [ĐO
 * 2026-09-15] là sai ở đúng những từ cần nhất (`so` → *"sô"* hay *"số"*?). Nên dạng có dấu được **khai**, và bài
 * kiểm `VoiceSlotProfileHotwordTest` canh hai chiều: mỗi cụm bỏ dấu ra phải **chỉ gồm** từ đã có trong
 * [VoiceLexicon.SLOT_WORDS] + [VoiceLexicon.NUMBER_WORDS] ⇒ đây không thể thành nơi lén thêm một câu lệnh mới.
 *
 * ## Vì sao KHÔNG có dòng *"ô"* / *"vào ô"* trần
 * [SherpaHotwords.isPhrase] bỏ dòng một từ và [SherpaHotwords.dropPrefixes] bỏ dòng là tiền tố của dòng khác —
 * *"VÀO Ô"* sẽ rụng ở tầng lọc. Không sinh nó ngay từ đây để tệp này nói đúng thứ nó muốn: **cả mệnh đề**.
 */
object VoiceSlotPhrases {

    /**
     * Số ô tối đa mà một bố cục có — đọc thẳng [LayoutPreset] thay vì viết một con số ở đây.
     *
     * Thêm một bố cục nhiều ô hơn ⇒ tự có cụm, không ai phải nhớ sửa hai chỗ (cùng lẽ [VoiceLayouts.WORDS]).
     * Số ngoài dải này vẫn **hiểu** được (bộ phân tích không kẹp — xem KDoc [VoiceIntent.OpenApp.slot], chỗ gọi
     * mới nói *"bố cục hiện chỉ có N ô"*); nó chỉ không được **bias**, vì không bố cục nào có ô ấy.
     */
    val MAX_SLOT: Int = LayoutPreset.entries.maxOf { it.slotCount }

    /** Con số **có dấu**, 1-based — chỉ tới [MAX_SLOT] (phần tử `0` không dùng, giữ chỗ cho chỉ số 1-based). */
    private val NUMBERS = listOf("", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám")

    /**
     * Mọi cụm đáng bias cho một mệnh đề chỉ ô, thứ tự ổn định (để `diff` hai lượt sinh tệp).
     *
     * Ba cách nói **đã gặp thật** trong 30 bản thu: *"vào ô số hai"* · *"vào ô hai"* · *"ô số hai"* (khi động từ
     * kéo dài, người ta bỏ chữ *"vào"*). Cả ba đều ≥ 2 từ nên không rụng ở [SherpaHotwords.isPhrase], và không
     * cụm nào là tiền tố theo TỪ của cụm nào (*"vào ô hai"* ≠ tiền tố của *"vào ô số hai"*).
     */
    val SPOKEN: List<String> = buildList {
        for (n in 1..MAX_SLOT) {
            val num = NUMBERS.getOrNull(n) ?: continue
            add("vào ô số $num")
            add("vào ô $num")
            add("ô số $num")
        }
    }
}
