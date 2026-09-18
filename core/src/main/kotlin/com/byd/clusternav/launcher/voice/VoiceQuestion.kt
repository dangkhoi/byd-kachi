package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ D1 · CÂU HỎI THÌ KHÔNG BAO GIỜ ĐƯỢC THÀNH LỆNH GHI ══════════════════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Tách khỏi [VoiceIntentParser] theo VAI: bộ phân tích
 * quyết *"câu này là việc gì"*, tệp này chỉ trả lời **một** câu hỏi — *"câu này có phải một câu HỎI không"*.
 *
 * ## Bệnh nó chữa — [ĐO xe 2026-09-18, log 53 phiên thật] (`oncar-voice-cases-findings-2026-09-18.md` §D1)
 * Người lái hỏi *"tất cả cửa đang khóa hay đang mở"* và Kachi trả về **`Control(sunroof, 1)`** — tức **mở cửa
 * sổ trời** trên xe đang chạy. Một câu hỏi không bao giờ được làm gì cả; nó chỉ được **trả lời**.
 *
 * Cơ chế đã đo được: câu ấy không khớp datum nào ⇒ [VoiceIntent.Unknown] ⇒ tầng chữa chính tả
 * ([VoicePhoneticMatch]) "sửa" một âm tiết thành một cụm của xe rồi đọc lại, và lần đọc lại **có động từ**
 * (*"tất"* bỏ dấu = *"tắt"*) nên nó ra một lệnh GHI. Cùng cơ chế, cùng phiên log: *"bật đèn khẩn cấp"* →
 * `Control(trunk, 1)` (**mở cốp**) vì *"cấp"* bị sửa thành *"cốp"* — cặp lẫn mà chính owner đã nêu (*"cốp với
 * cấp khác gì nhau đâu"*).
 *
 * ## Hai hình dạng câu hỏi, và vì sao chỉ có hai
 *  1. **Cụm hỏi cắt được** ([VoiceLexicon.READ_TAILS] + [ASK_EXTRA]): *"… bao nhiêu"*, *"… mức mấy"*. Cắt cụm ra
 *     rồi đọc phần còn lại — đường này đã có từ V1, tệp này chỉ **thêm cụm**, không đổi cách làm.
 *  2. **Câu hỏi LỰA CHỌN** ([isChoice]): *"… đang A **hay** B"*, *"… **hay không**"*, *"**có** … **không**"*.
 *     Không có cụm nào cắt ra được, nên nó cần một phép nhận dạng riêng.
 *
 * Không có hình dạng thứ ba (vd bắt mỗi chữ *"mấy"*): bỏ dấu xong *"mấy"* trùng hệt **"máy"** (*"bật **máy**
 * lạnh"*), nên một dấu hiệu MỘT TỪ ở đây sẽ ép cả họ câu lệnh máy lạnh thành câu hỏi — đúng họ lỗi mà
 * [VoiceLexicon.FILLERS] đã phải rút ngắn vì nó.
 */
internal object VoiceQuestion {

    /**
     * Cụm hỏi **thêm** từ log xe 2026-09-18, cùng vai với [VoiceLexicon.READ_TAILS] (cắt ra rồi đọc phần còn lại).
     *
     * Khai ở đây chứ không nhét vào [VoiceLexicon.READ_TAILS] vì hai bảng có **hai mức bằng chứng** khác nhau:
     * bảng kia là năm cụm hỏi thuần đã dùng từ V1, còn đây là cụm đo được trong một phiên log cụ thể (*"ghế mát
     * **mức mấy**"* — heard `ghế mất mấy`). Ghép chung thì lần sau không ai biết cụm nào đến từ đâu.
     *
     * ⚠ Cả hai cụm đều **hai từ**. Chữ *"mấy"* đứng trần bị loại — xem KDoc lớp.
     */
    val ASK_EXTRA: List<List<String>> = listOf(
        listOf("muc", "may"),
        listOf("may", "muc"),
    )

    /** Mọi cụm hỏi cắt được — [VoiceLexicon.READ_TAILS] (V1) cộng [ASK_EXTRA] (log xe 2026-09-18). */
    val ASK_PHRASES: List<List<String>> = VoiceLexicon.READ_TAILS + ASK_EXTRA

    /**
     * Vị trí + độ dài của cụm hỏi cắt được ở BẤT KỲ đâu trong [t], hoặc `null`.
     *
     * Chuyển từ `VoiceIntentParser.askAt` sang đây **không đổi một luật nào**: vẫn quét mọi vị trí (*"còn bao
     * nhiêu pin"* cũng là câu hỏi như *"pin còn bao nhiêu"*), vẫn khớp nguyên cụm.
     */
    fun askAt(t: List<Token>): Pair<Int, Int>? =
        ASK_PHRASES.firstNotNullOfOrNull { words ->
            t.indices.firstOrNull { VoiceLexicon.phraseAt(t, it, words) }?.let { it to words.size }
        }

    /**
     * Từ dựng nên bộ khung của một câu hỏi lựa chọn — **không** mang nghĩa về xe, nên cắt ra là an toàn.
     *
     * `hay` đã nằm trong [VoiceLexicon.FILLERS] với vai *"hãy"* (bỏ dấu trùng nhau); ở đây nó có vai thứ hai là
     * *"hoặc"*. Hai vai sống chung được vì [strip] chỉ chạy sau khi [isChoice] đã nhận dạng cả câu.
     */
    private val SCAFFOLD: Set<String> = setOf("dang", "hay", "co", "khong", "chua", "la", "the", "xe", "a")

    /**
     * Câu [t] có hình dạng **hỏi lựa chọn** không: *"… đang A hay B"* · *"… hay không"* · *"có … không"*.
     *
     * ## Ba cổng, mỗi cổng chặn một cách nhận nhầm
     *  1. **Phải có ≥ 3 từ.** Hai từ thì không đủ chỗ cho *"X hay Y"*.
     *  2. **Dấu hiệu `hay`/`chưa`/`không` không được đứng ở vị trí 0.** *"hãy bật đèn"* mở đầu bằng `hay` —
     *     đó là câu RA LỆNH, và [ĐO] nó đang chạy đúng (tiếng đệm `hay` bị [VoiceLexicon.FILLERS] bỏ).
     *  3. **Phải có thêm một từ ngữ cảnh hỏi** (`dang` · `co` · `hay` · `chua`) ngoài chính dấu hiệu ấy.
     *     *"bật đèn đọc không"* chỉ có một mình `khong` ở cuối ⇒ **không** bị coi là câu hỏi: nó vẫn là một lệnh,
     *     và đoán ngược lại là làm câm một câu đang chạy.
     *
     * ## ⚠⚠ [SOÁT senior 2026-09-18 · P0] Cổng (3) một mình để HỞ đúng hình dạng câu hỏi phổ biến NHẤT
     * Ba cổng trên đóng được ca của nhật ký (*"tất cả cửa đang khóa **hay** đang mở"* — có `hay` + hai chữ
     * `đang`), nhưng [ĐO off-car, lượt soát] chúng **không** thấy dạng *"&lt;đối tượng&gt; đang &lt;trạng thái&gt;
     * không/chưa"* — chỉ MỘT từ ngữ cảnh nên `context = 1 < 2`:
     *
     * | câu | trước | sau |
     * |---|---|---|
     * | *"cửa sổ trời đang mở không"* | `Unknown(NO_VERB)` ⇒ hỏi lại **carry rỗng** ⇒ trả lời *"cửa sổ trời"* = **`Control(sunroof,1)`** | `Read(sunroof_state)` |
     * | *"cốp đã mở chưa"* | nt ⇒ trả lời *"mở cốp"* = **`Control(trunk,1)`** | `Read(...)` / hỏi lại đường ĐỌC |
     * | *"xe đã khóa cửa chưa"* · *"điều hòa đang bật không"* · *"kính hạ hết chưa"* | nt | nt |
     *
     * Tức **đúng tai nạn của nhật ký**, chỉ khác một hình dạng câu — mở cửa sổ trời / mở cốp trên xe đang chạy.
     *
     * ⇒ cổng (4): đuôi `không`/`chưa` + **≥ 1** từ ngữ cảnh + **không có động từ HÀNH ĐỘNG ở vị trí 0**. Chính điều
     * kiện cuối là thứ giữ nguyên ca cổng (3) sinh ra để bảo vệ: *"bật đèn đọc không"* mở đầu bằng `bật` ⇒ vẫn là
     * một lệnh ([ĐO] `context = 0` ở câu đó, nên nó còn bị cổng (4) loại hai lần).
     */
    fun isChoice(t: List<Token>): Boolean {
        if (t.size < MIN_WORDS) return false
        val norms = t.map { it.norm }
        val alt = norms.indexOf("hay").takeIf { it >= 1 }
        val tailNo = norms.last() == "khong" || norms.last() == "chua"
        if (alt == null && !tailNo) return false
        val context = norms.count { it == "dang" || it == "co" || it == "hay" || it == "chua" }
        if (context >= if (alt != null && tailNo) 1 else CONTEXT_MIN) return true
        // Cổng (4) — xem KDoc. Chỉ cho đuôi `không`/`chưa`, và chỉ khi câu KHÔNG mở đầu bằng động từ hành động.
        return tailNo && context >= 1 && !actionAtHead(t)
    }

    /**
     * Câu [t] mở đầu bằng một cụm **động từ HÀNH ĐỘNG** (*"bật"*, *"mở"*, *"tắt"*… — mọi thứ không phải ĐỌC).
     *
     * Dùng ở hai chỗ ([isChoice] cổng (4) và [bareAskBody] cổng (3)) nên khai một lần: hai bản sao của phép hỏi
     * này là hai bản sẽ lệch, và lệch ở đây nghĩa là một câu lệnh bị coi thành câu hỏi (hoặc ngược lại — một câu
     * hỏi thành một lệnh ghi).
     */
    private fun actionAtHead(t: List<Token>): Boolean = VoiceGrammar.VERBS.any { (words, verb) ->
        VoiceGrammar.isAction(verb) && VoiceLexicon.phraseAt(t, 0, words)
    }

    /** Ít nhất ba từ mới đủ chỗ cho một câu hỏi lựa chọn — xem cổng (1) ở [isChoice]. */
    private const val MIN_WORDS = 3

    /** Cần ít nhất hai dấu hiệu hỏi (vd `đang` + `hay`) — xem cổng (3) ở [isChoice]. */
    private const val CONTEXT_MIN = 2

    /** Bỏ bộ khung câu hỏi, để phần còn lại đem đi tra datum (*"tất cả **cửa** … "* → `[tat, ca, cua]`). */
    fun strip(t: List<Token>): List<Token> = t.filterNot { it.norm in SCAFFOLD }

    /** Câu [t] là một câu HỎI — một trong hai hình dạng ở KDoc lớp, hoặc mở đầu bằng cụm dẫn [READ_LEADS]. */
    fun isQuestion(t: List<Token>): Boolean = askAt(t) != null || isChoice(t) || readsLead(t)

    /**
     * Cụm dẫn *"chỉ số X"* = *"cho biết giá trị của X"* — một câu ĐỌC kể cả khi **không** có cụm hỏi nào
     * (*"chỉ số bụi mịn hiện nay"*).
     *
     * Khai ở đây (trước đây là một `private val` của [VoiceIntentParser]) vì nay **hai** chỗ cần cùng câu trả lời:
     * bộ phân tích để đi đường ĐỌC, và [VoiceClarify] để biết lượt trả lời cũng phải đi đường ĐỌC. Hai bản sao của
     * một bảng hai từ là hai bản sẽ lệch — và lệch ở đây nghĩa là một câu hỏi có thể thành một lệnh ghi.
     */
    val READ_LEADS: List<List<String>> = listOf(listOf("chi", "so"))

    /** Câu [t] mở đầu bằng một cụm dẫn [READ_LEADS]. */
    fun readsLead(t: List<Token>): Boolean = READ_LEADS.any { VoiceLexicon.phraseAt(t, 0, it) }

    /**
     * ═══ D2 · Câu hỏi mức bằng **một chữ ở cuối** (*"ghế mát mức mấy"* → ASR *"ghế mất mấy"*) ════════════════
     *
     * Trả phần THÂN (bỏ chữ hỏi cuối) khi câu có hình dạng `<đối tượng> mấy`, hoặc `null`.
     *
     * ## Vì sao cần một hình dạng thứ ba, dù KDoc lớp nói *"chỉ có hai"*
     * [ĐO xe 2026-09-18, `20260918-…-ghế mất mấy.json`] owner nói *"ghế mát mức mấy"*; mô hình in ra **`ghế mất
     * mấy`** — rụng hẳn chữ *"mức"*, tức cụm hỏi HAI TỪ [ASK_EXTRA] không còn gì để khớp. Bỏ dấu thì *"mát"* và
     * *"mất"* trùng nhau (`mat`) nên phần đối tượng vẫn đúng; thứ duy nhất thiếu là **chữ hỏi**. Câu rơi vào
     * `NO_VERB` = *"không hiểu"*, trong khi người lái đã nói một câu hoàn toàn rõ.
     *
     * ## Ba cổng — và cổng thứ tư nằm ở chỗ gọi
     * Chữ `may` đứng trần là thứ KDoc lớp **cấm** làm dấu hiệu, vì bỏ dấu xong nó trùng hệt *"máy"* (*"bật **máy**
     * lạnh"*). Nên nó chỉ được nhận trong đúng một hình dạng rất hẹp:
     *  1. **phải là từ CUỐI câu** ⇒ *"bật máy lạnh"* (chữ `may` ở giữa) không bao giờ vào đây;
     *  2. **phải còn ≥ 2 từ phía trước** ⇒ *"mở máy"* · *"tắt máy"* · *"nổ máy"* bị loại bằng chính độ dài;
     *  3. **không có động từ HÀNH ĐỘNG ở vị trí 0** ⇒ một câu ra lệnh vẫn là câu ra lệnh.
     *  4. …và chỗ gọi ([VoiceIntentParser]) chỉ nhận kết quả khi phần thân ra được **một datum THẬT**; không thì
     *     câu đi tiếp y như chưa có gì xảy ra. Đây là cổng mạnh nhất: nó làm hình dạng này chỉ biến được câu
     *     thành một lệnh **ĐỌC**, không bao giờ thành một lệnh ghi, và chỉ khi thứ đứng trước thật sự đọc được.
     */
    fun bareAskBody(t: List<Token>): List<Token>? {
        if (t.size < MIN_BARE_WORDS || t.last().norm !in BARE_ASK) return null
        if (actionAtHead(t)) return null
        return t.subList(0, t.size - 1)
    }

    /**
     * ═══ [SOÁT senior 2026-09-18 · P0] Câu [t] **có dấu hiệu hỏi**, dù [isQuestion] chưa dám nhận ══════════════
     *
     * Câu hỏi yếu hơn [isQuestion]: chỉ cần một đuôi `không`/`chưa`, hoặc một chữ `hay` không đứng đầu câu.
     *
     * ## Vì sao cần một phép hỏi THỨ HAI, lỏng hơn
     * [isQuestion] phải **chặt** vì nó đổi cách phân tích cả câu ngay ở lượt đầu — nhận nhầm là làm câm một lệnh
     * đang chạy. Nhưng [VoiceClarify.Ask.carry] cần một câu trả lời cho một câu hỏi **khác hẳn**: *"lượt trả lời
     * của câu này nên đi đường ĐỌC hay đường HÀNH ĐỘNG"*. Ở đó chọn sai về phía ĐỌC chỉ tốn một lượt nói lại,
     * còn chọn sai về phía HÀNH ĐỘNG là **mở cửa sổ trời trên xe đang chạy** — hai đầu không đối xứng, nên hai
     * phép hỏi không được dùng chung một ngưỡng.
     *
     * [ĐO off-car, lượt soát] `carry` **rỗng** là ô nhớ duy nhất mà tai nạn của nhật ký đi qua được: câu trả lời
     * đứng một mình thì *"cửa sổ trời"* · *"mở cốp"* · *"khóa xe"* · *"rời xe"* đều là lệnh ghi hợp lệ. Hàm này
     * thu hẹp ô ấy lại: câu nào có dấu hiệu hỏi thì lượt trả lời mang theo động từ ĐỌC.
     *
     * ⚠ **Không** thay [isQuestion] bằng hàm này ở [VoiceIntentParser]: *"bật đèn đọc không"* sẽ thành câu hỏi và
     * một lệnh đang chạy đúng sẽ câm — đúng ca mà cổng (3) của [isChoice] sinh ra để giữ.
     */
    fun looksAsked(t: List<Token>): Boolean {
        if (t.isEmpty()) return false
        val norms = t.map { it.norm }
        return norms.last() == "khong" || norms.last() == "chua" || norms.indexOf("hay") >= 1
    }

    /**
     * Chữ hỏi đứng TRẦN ở cuối câu — xem ba cổng ở [bareAskBody].
     *
     * Chỉ `may` (*"mấy"*). **Không** thêm `nhieu`: *"bao nhiêu"* đã là một cụm của [VoiceLexicon.READ_TAILS] cắt
     * được ở bất kỳ đâu, nên thêm nó ở đây chỉ nhân đôi một đường đang chạy — và một bản sao là một bản sẽ lệch.
     */
    private val BARE_ASK: Set<String> = setOf("may")

    /** Ít nhất ba từ: hai từ đối tượng + chữ hỏi — xem cổng (2) ở [bareAskBody]. */
    private const val MIN_BARE_WORDS = 3

    /**
     * MỌI từ chỉ dựng nên **bộ khung câu hỏi** — không từ nào mang nghĩa về xe.
     *
     * Gom từ đúng ba bảng của tệp này (không khai một danh sách thứ tư — một bản sao là một bản sẽ lệch), để
     * [VoiceClarify] trừ chúng ra trước khi đo *"từ nào trong câu ủng hộ họ nào"*. [ĐO off-car 2026-09-18] thiếu
     * phép trừ đó thì *"kính lái đang mở **bao nhiêu**"* hỏi lại *"Đang nào — Tốc độ hay Đèn đọc?"*: hai chữ
     * *"bao nhiêu"* của khung câu hỏi trùng cách nói *"đang chạy bao nhiêu"* của datum `speed`, nên chúng nâng một
     * họ **không liên quan** lên trên họ kính mà người lái đang hỏi.
     */
    val FRAME_WORDS: Set<String> = buildSet {
        ASK_PHRASES.forEach { addAll(it) }
        addAll(SCAFFOLD)
        addAll(BARE_ASK)
    }

    /**
     * Ý định này có **GHI vào xe** không — tức có thể làm một việc không hoàn lại được.
     *
     * [VoiceIntent.Macro] cũng tính: một gói lệnh là nhiều lệnh ghi liền nhau (*"Rời xe"* đóng kính rồi khoá xe).
     * Đổi hồ sơ / đổi bố cục / mở app **không** tính — chúng không chạm thân xe và đảo lại được bằng một câu.
     */
    fun writesToCar(i: VoiceIntent): Boolean = i is VoiceIntent.Control || i is VoiceIntent.Macro
}
