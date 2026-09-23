package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.SavedPlace
import com.byd.clusternav.launcher.SavedPlaces
import com.byd.clusternav.launcher.Strings

/**
 * ═══ CÁCH **NÓI** MỘT NƠI ĐÃ LƯU ═════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-addresses.html` R2 · R4 · R6. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Nó giải bài toán gì
 * Tới V1.1, mọi điểm đến là **từ vựng MỞ**: người lái phải đọc nguyên tên địa điểm và chuỗi ấy do nhận dạng
 * **tự do** đọc ra — phần kém chính xác nhất trong cả câu (đó là lý do `VoiceRiskTable` bắt hỏi lại). Nhưng hai
 * điểm đến người ta đi **hằng ngày** thì không cần mở chút nào: nhà và công ty là một **tập đóng gồm 1–2 phần
 * tử** mà chính người dùng đã gõ sẵn. Lớp này là chỗ nối *"cụm người ta nói"* → *"nhãn trong sổ"*.
 *
 * ## ⚠ Nhãn đã lưu KHÔNG vào từ vựng chung ([VoiceGrammar.terms]) — có chủ ý
 * Chúng chỉ được tra ở **đúng một vị trí**: phần ĐUÔI sau một động từ dẫn đường (chỗ mà hôm nay là điểm đến).
 * Lý do đã ghi sẵn ở nhánh (c) của [VoiceIntentParser] theo chiều ngược lại — một điểm đến bất kỳ có thể chứa
 * một cụm của xe (*"trạm sạc"*). Thả nhãn người dùng vào từ vựng chung là mở lại đúng cái cửa ấy: một mục tên
 * *"Bãi đỗ"* sẽ cướp mọi câu có hai chữ đó, kể cả câu nói về một cái nút.
 *
 * ## Cách nói dựng sẵn là DỮ LIỆU, không phải `if`
 * [ALIASES] ánh xạ *cụm nói → nhãn chuẩn*. Người dùng đặt nhãn khác (*"Nhà ngoại"*) thì nhãn ấy tự khớp bằng
 * chính tên nó, không cần khai gì — đúng luật CLAUDE.md §7 (khác biệt lộ ra qua **đo/khớp dữ liệu**, không qua
 * một nhánh theo tên riêng).
 */
object VoicePlaces {

    /** Nhãn chuẩn của *"nhà"* — cũng là nhãn gợi ý sẵn ở màn Cài đặt. */
    const val HOME = "Nhà"

    /** Nhãn chuẩn của *"công ty"*. */
    const val WORK = "Công ty"

    /**
     * Nhãn chuẩn → **cách nói** (giữ NGUYÊN DẤU: chúng còn được khai với bộ nhận dạng ở [spokenPhrases]; phép so
     * khớp tự bỏ dấu qua [VoiceLexicon.deaccent] nên khai một bản có dấu là đủ cho cả hai đầu).
     *
     * ## Vì sao cụm có cả *"về nhà"* lẫn *"nhà"*
     * Động từ ăn **một hoặc hai** từ đầu câu rồi phần còn lại mới đi tra ở đây: *"về nhà"* ⇒ động từ `về` + đuôi
     * `nhà`; *"đi về nhà"* ⇒ động từ `đi` + đuôi `về nhà`. Cùng một ý, hai chiều dài đuôi khác nhau — nên cả hai
     * đều phải có mặt, nếu không thì một trong hai câu đời thường nhất lại rơi vào *"không hiểu"*.
     *
     * *"làm"* đứng một mình là cố ý (*"đi làm"* ⇒ đuôi `làm`): nó CHỈ được tra ở vị trí điểm đến nên không đụng
     * bất kỳ câu lệnh nào khác.
     */
    val ALIASES: Map<String, List<String>> = mapOf(
        HOME to listOf("nhà", "về nhà", "nhà mình", "nhà tôi", "home"),
        WORK to listOf("công ty", "cơ quan", "chỗ làm", "nơi làm việc", "làm", "đi làm", "office"),
    )

    /**
     * ═══ ĐỘNG TỪ **CÓ ĐIỀU KIỆN** của sổ địa chỉ — *"về · đi · đến · tới"* ════════════════════════════════════
     *
     * Khai ở đây, KHÔNG phải trong [VoiceGrammar.VERBS], và đó là điểm thiết kế quan trọng nhất của cả tính năng.
     *
     * ## Vì sao không cho chúng vào bảng động từ chung
     * Bảng kia gán nghĩa **vô điều kiện**: một từ vào đó thì mọi câu mở đầu bằng nó đều thành lệnh dẫn đường.
     * [ĐO] ngay trong bộ test đang có — `VoiceIntentParserTest.hai cau con lai noi thang la chua lam duoc` giữ câu
     * *"về bố cục 2 cột"* ở [VoiceUnknownReason.NO_VERB] **có chủ ý** (bố cục chưa nằm trong tập đóng của giọng
     * nói). Cho `ve` làm động từ NAV thì câu đó lặng lẽ thành *"dẫn đường tới «bố cục 2 cột»"*: app bản đồ mở lên
     * tìm một cái tên vô nghĩa, tức máy **làm một việc khác** việc được bảo — đúng họ lỗi mà
     * [VoiceLexicon.VI_TENS_SHORT] đã phải chữa một lần.
     *
     * ## Điều kiện: phần đuôi phải THẬT SỰ là một nơi
     * Chỗ gọi ([VoiceIntentParser]) chỉ nhận cụm ở đây khi **cả phần đuôi** khớp một nơi qua [match] (nhãn đã lưu
     * hoặc cách nói dựng sẵn). Không khớp ⇒ coi như không có động từ nào, câu đi tiếp đúng đường cũ. Nhờ vậy
     * *"về nhà"* chạy mà *"về bố cục 2 cột"* vẫn nói thẳng là chưa làm được — không phải liệt kê từ cấm nào.
     *
     * ⚠ Nhờ điều kiện ấy mà `tới`/`đến` vào được dù bỏ dấu chúng trùng *"tôi"* và *"đèn"*: một câu mở đầu bằng
     * *"tôi…"* / *"đèn…"* gần như không bao giờ có phần đuôi bằng đúng một nhãn trong sổ, và ca *"đèn đọc"* thì
     * đã được `headMatch` chặn trước cả khi tới đây (nhãn nút dài hơn thì nhãn thắng).
     */
    val PLACE_VERBS: List<String> = listOf("đi đến", "đi tới", "về tới", "về", "đi", "đến", "tới")

    /** [PLACE_VERBS] ở dạng dãy từ đã bỏ dấu, **dài trước ngắn** (cùng luật *"dãy dài nhất thắng"*). */
    val PLACE_VERB_WORDS: List<List<String>> = PLACE_VERBS.map { norm(it) }.sortedByDescending { it.size }

    /**
     * Nhãn HIỆN cho người đọc: hai nhãn chuẩn có bản tiếng Anh, nhãn người dùng tự đặt thì trả **nguyên văn**.
     *
     * Cùng lệ [VoiceReply.labelOf]/`ProfileNames.display`: mã lạ ⇒ trả chính nó, không sập và không bịa.
     */
    fun displayLabel(label: String): String = when (label) {
        HOME -> Strings.t(HOME, "Home")
        WORK -> Strings.t(WORK, "Work")
        else -> label
    }

    /**
     * Cụm [words] (đã bỏ dấu) có phải là cách gọi một nơi không → trả về **nhãn** để tra sổ, hoặc `null`.
     *
     * Thứ tự xét, và cả ba bước đều cần thiết:
     *  1. **Nhãn ĐÃ LƯU khớp nguyên cụm** ⇒ trả đúng nhãn ấy (giữ nguyên chữ người dùng gõ, kể cả hoa/thường và
     *     dấu — đó là khoá để tra lại sổ).
     *  2. **Cách nói dựng sẵn** ⇒ tìm nhãn đã lưu ứng với nhãn chuẩn đó; có thì trả nhãn đã lưu (người dùng có
     *     thể đã đặt tên *"cty"* cho mục công ty), không có thì trả **nhãn chuẩn**.
     *  3. còn lại `null` ⇒ chỗ gọi hiểu là điểm đến MỞ như trước.
     *
     * ## Vì sao bước 2 vẫn trả một nhãn khi sổ TRỐNG
     * *"về nhà"* mà chưa lưu gì thì bắn chữ *"nhà"* cho Google Maps là dẫn người ta tới một quán ăn tên *"Nhà"*
     * cách đó mười cây số — máy **làm một việc khác** việc được bảo, đúng họ lỗi mà `VoiceLexicon.VI_TENS_SHORT`
     * đã phải chữa. Trả nhãn chuẩn để tầng thi hành nói thẳng *"chưa lưu địa chỉ Nhà"* (R4).
     */
    fun match(words: List<String>, savedLabels: List<String>): String? {
        if (words.isEmpty()) return null
        // Khớp trực tiếp trước (giữ nguyên hành vi cũ).
        savedLabels.firstOrNull { norm(it) == words }?.let { return it }
        // Chuẩn hoá số đọc↔chữ số CẢ hai vế rồi khớp lại: "công ty một"(nghe) == "Công ty 1"(lưu) — findings 2026-09-23.
        val wDig = VoiceNumberNorm.wordsToDigits(words)
        savedLabels.firstOrNull { VoiceNumberNorm.wordsToDigits(norm(it)) == wDig }?.let { return it }
        val canonical = ALIASES.entries.firstOrNull { (_, spoken) -> spoken.any { norm(it) == words } }?.key
            ?: return null
        return savedLabels.firstOrNull { SavedPlaces.keyOf(it) == SavedPlaces.keyOf(canonical) } ?: canonical
    }

    /**
     * Cụm cần khai với **bộ nhận dạng** cho các nơi ĐÃ LƯU (R6) — nhãn + mọi cách nói của nhãn chuẩn tương ứng.
     *
     * ## Vì sao chỉ khai nơi ĐÃ LƯU
     * Cùng luật với tên app đích (`VoicePhrases.labelPhrases`): khai *"công ty"* trên một chiếc xe chưa ai lưu
     * địa chỉ công ty là mở thêm một đường cho bộ giải mã nghe nhầm vào một mục **không tồn tại**, mà không đổi
     * lại được gì — câu ấy rồi cũng chỉ nhận được câu trả lời *"chưa lưu"*.
     */
    fun spokenPhrases(savedLabels: List<String>): List<String> {
        if (savedLabels.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        savedLabels.forEach { label ->
            out.add(label)
            // ⚠ Kèm **một cụm có động từ** (*"đến Nhà ngoại"*). Không phải cho đẹp: bộ nhận dạng dựng LM bigram
            // (xem KDoc [VoicePhrases]) nên nó cần biết hai từ ấy **đi cạnh nhau được**; khai mỗi cái nhãn thì
            // câu *"đến Nhà ngoại"* vẫn có thể ra một cặp từ rời rạc. Chỉ thêm MỘT cụm, không nhân chéo bảy động
            // từ × N nhãn — bigram tự lo phần còn lại, còn nhân chéo là nổ tổ hợp đúng chỗ KDoc kia cấm.
            out.add("$VERB_FOR_PHRASE $label")
            ALIASES.entries.firstOrNull { (canon, _) -> SavedPlaces.keyOf(canon) == SavedPlaces.keyOf(label) }
                ?.let { out.addAll(it.value) }
        }
        return out.toList()
    }

    /** Nhãn của mọi mục trong sổ — chỗ gọi hay cần đúng danh sách này (bộ phân tích, ngữ pháp, biasing). */
    fun labelsOf(places: List<SavedPlace>): List<String> = places.map { it.name }

    /** Động từ dùng để dựng cụm mẫu cho bộ nhận dạng ([spokenPhrases]) — một cái là đủ, xem chú thích ở đó. */
    private const val VERB_FOR_PHRASE = "đến"

    private fun norm(phrase: String): List<String> = VoiceLexicon.tokenize(phrase).map { it.norm }
}
