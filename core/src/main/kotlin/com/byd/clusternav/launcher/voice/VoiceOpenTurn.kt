package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ VOICE-OPEN-TURN · CÂU CÒN DỞ THÌ **CHƯA ĐÓNG LƯỢT** ═════════════════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car. Backlog `OQ9`, owner 2026-09-26: *"làm voice-open-turn cho chuẩn"*.
 *
 * ## Bệnh nó chữa — nhóm quãng ngừng **để nghĩ** 720–1 060 ms, [ĐO 30 bản thu thật của xe 26/09]
 * `docs/diagnostics/offcar-2026-09-26/voice-tail-fuzzy-phonetic.md` §1.4 đo hai nhóm quãng ngừng khác hẳn nhau:
 *
 * | loại quãng ngừng | giá trị đo được | số ca |
 * |---|---|---|
 * | trong cùng một vế (*"mở youtube ⟨…⟩ vào ô số hai"*) | 120 · 120 · 140 · 160 · 160 · 180 · 200 · 260 ms | 8 |
 * | **ngừng để NGHĨ giữa hai vế** | **720 · 820 · 820 · 1 060 ms** | 4 |
 *
 * Mặc định `VoiceVadTrim.MIN_SILENCE_MS` = 600 ms đỡ trọn nhóm trên (≥ 2,3× giá trị lớn nhất) và **không** giá
 * trị nào ≤ 800 đỡ được nhóm dưới. Nâng mặc định lên 1 100 thì cộng ≥ 500 ms vào **mọi** lượt nói và phá bất
 * biến *"VAD chốt sớm hơn bộ RMS"* (`VoiceEndpointer.HANGOVER_MS` = 800) — xem KDoc [VoiceVadTrim.MIN_SILENCE_MS].
 * ⇒ Đường rẻ hơn: **chỉ** câu còn dở mới được chờ thêm, câu đủ nghĩa thì đóng lượt đúng như hôm nay.
 *
 * ## Hai câu hỏi thuần ở tệp này, và ranh giới
 *  1. [isOpen] — chuỗi vừa nghe có kết thúc bằng một **vế dở** không?
 *  2. [join] — nối vế sau vào vế trước thành **một** câu cho bộ phân tích (không có bộ phân tích thứ hai).
 *
 * Tệp này **không** mở micro, không biết mili-giây nào đã trôi, không biết VAD là gì. Phần thi hành (giữ micro
 * mở, giải mã song song, cắt khúc vế sau) ở `:app` (`VoiceOpenTurnArm` + `VoiceCapture`).
 *
 * ## Vì sao bảng vế-dở được **SINH** từ ngữ pháp, không khai tay (CLAUDE.md §7)
 * Một danh sách chuỗi khai tay sẽ lệch khỏi ngữ pháp ngay lần ai đó thêm một cách nói mới (đúng bệnh
 * `auto_container`/`AutoContainer` mà §7 lấy làm ví dụ). Năm họ dưới đây đều **đọc lại chính bảng** mà tầng nghe
 * và tầng chữ đang dùng, nên thêm một cách nói ở đó là tự có vế dở ở đây:
 *
 * | họ | nguồn | vì sao nó là vế DỞ |
 * |---|---|---|
 * | [S] ô | [VoiceSlotPhrases.SPOKEN] bỏ con số đuôi · [VoiceLexicon.SLOT_HEADS]×[VoiceLexicon.SLOT_ORDINALS] | mệnh đề chỉ ô **bắt buộc** có một con số; *"vào ô"* / *"vào ô số"* là mệnh đề mất đối số |
 * | [P] hồ sơ | [VoiceProfileNames.MARKERS] | [ĐO xe] 8/8 lượt *"chuyển sang hồ sơ"* rụng đúng cái tên ⇒ cụm đánh dấu đứng cuối = thiếu tên |
 * | [N] nav/nhạc | [VoiceOpenVocab.TRIGGERS] lọc bằng **chính bộ phân tích** | cụm ấy chỉ có nghĩa khi *"còn ít nhất một từ đứng sau"* ([VoiceOpenVocab.triggerOf]); đứng cuối = mất cái tên |
 * | [B] *"bằng &lt;app&gt;"* | [VoiceLexicon.BY_APP_MARKERS] + **cổng tiền tố** | cụm đánh dấu chọn app mà không có app |
 * | [V] động từ trần | [VoiceGrammar.VERBS] + **cổng bộ phân tích** | *"mở"* · *"bật"* — động từ chưa có đối tượng |
 *
 * ## ⚠ Hai cổng dùng **bộ phân tích thật** để lọc, không dùng trí nhớ
 * Nếu cứ lấy nguyên bảng thì hai họ sau tự bắn vào chân mình, và cả hai ca đều là chuỗi THẬT có trong bộ ca
 * `scripts/emulator/voice-cases.tsv`:
 *  • [N]: *"phát nhạc"* / *"play"* nằm trong [VoiceOpenVocab.TRIGGERS] nhưng **tự nó đã là một câu lệnh đủ**.
 *  • [B]: *"quá"* bỏ dấu ra `qua`, trùng một cụm đánh dấu chọn app ⇒ *"hôm nay trời đẹp quá"* sẽ bị coi là dở.
 *
 * ⇒ [N] chỉ giữ cụm mà `VoiceIntentParser.parseOne(cụm)` trả [VoiceIntent.Unknown] (tức tự nó KHÔNG là lệnh), và
 * [B] đòi **phần câu trước cụm đánh dấu** phải phân tích ra một vế nhạc/dẫn đường/mở-app thật. Bài
 * `VoiceOpenTurnCasesTest` quét **cả 67 câu** của bộ ca thật và bắt buộc **0 câu** bị coi là dở — đó là cái lưới,
 * không phải lời hứa.
 *
 * ## Hai con số thời gian — và vì sao câu ĐỦ NGHĨA không mất một mili-giây nào
 * [OPEN_JOIN_WINDOW_MS] = **1 200 ms** kể từ **điểm ngắt câu** (không phải từ lúc hết tiếng): điểm ngắt nổ sau
 * `MIN_SILENCE_MS` = 600 ms im lặng, nên bốn quãng ngừng đo được (720/820/820/1 060 ms) rơi vào **120–460 ms sau
 * điểm ngắt**. Cửa sổ 1 200 ms là **2,6×** giá trị lớn nhất ấy, tức còn chỗ cho một quãng ngừng 1 800 ms chưa ai
 * đo. [OPEN_MAX_EXTRA_MS] = **1 500 ms** là trần cứng của phần chờ: câu bị **bỏ giữa** (*"mở vietmap vào ô"* rồi
 * thôi) vẫn phải ra kết quả, và nó ra bằng đúng hành vi hôm nay (phân tích phần đã có ⇒ hỏi lại *"ô nào"*).
 *
 * Câu đủ nghĩa **không** đi qua đường này: [isOpen] trả `false` ⇒ chỗ gọi đóng lượt ngay tại điểm ngắt. Và cả ở
 * câu dở, phần chờ **trùng** với thời gian bộ giải mã vốn đã chạy ([ĐO xe] 1,3–2 s mỗi lượt) nên trên xe nó gần
 * như không thêm giây nào — xem KDoc `VoiceOpenTurnArm`.
 */
object VoiceOpenTurn {

    /**
     * Chờ thêm bao lâu kể từ **điểm ngắt câu** để vế sau **bắt đầu**. Bảng số + cách suy ra ở KDoc lớp.
     *
     * Đây là cửa sổ cho **điểm bắt đầu**, không phải cho cả vế sau: vế sau đã bắt đầu thì nó được nói hết bình
     * thường (VAD chốt như mọi lượt), chỉ còn trần cứng `VoiceSession.MAX_LISTEN_MS` = 8 s gác — một người đang
     * nói dở không được ngắt lời bằng đồng hồ.
     */
    const val OPEN_JOIN_WINDOW_MS = 1_200L

    /**
     * Trần cứng của phần chờ, kể từ điểm ngắt câu: hết ngần này mà **chưa có tiếng nào** ⇒ thôi chờ.
     *
     * Rộng hơn [OPEN_JOIN_WINDOW_MS] đúng 300 ms — bằng 1,5 khối đọc micro (`CHUNK_SAMPLES` = 200 ms) — để cửa
     * sổ 1 200 ms được xét **trọn**: kẹp hai con số bằng nhau thì khối cuối cùng của cửa sổ có thể rơi ngay sau
     * trần và cửa sổ thật chỉ còn 1 000 ms. Nó cũng là con số mà yêu cầu *"≤ 1,5 s thêm cho câu bị bỏ giữa"* nói
     * tới, nên nó nằm ở đây để bài kiểm đọc được, không nằm rải rác trong `:app`.
     */
    const val OPEN_MAX_EXTRA_MS = 1_500L

    /**
     * Chuỗi vừa nghe có kết thúc bằng một vế DỞ không ⇒ có nên chờ thêm không.
     *
     * Chuỗi rỗng / chỉ gồm từ đệm ⇒ `false`: đó là ca *"không nghe thấy gì"*, đã có đường riêng
     * (`VoiceSilenceGate` + *"Không nghe rõ"*), và chờ thêm ở đó là giữ micro mở cho một lượt không có ai nói.
     */
    fun isOpen(text: String): Boolean {
        val words = tail(text)
        if (words.isEmpty()) return false
        // [S] · [P] · [N] — cụm đứng cuối câu là đủ kết luận, không cần hỏi bộ phân tích.
        if (HEADS.any { endsWith(words, it) }) return true
        // [B] — cụm đánh dấu chọn app chỉ là vế dở khi phần TRƯỚC nó thật sự là một vế nhạc/dẫn đường/mở app.
        if (words.size >= 2 && words.last() in VoiceLexicon.BY_APP_MARKERS && attachable(words.dropLast(1))) return true
        // [V] — cả câu chỉ là một động từ, và chính bộ phân tích nói nó chưa thành lệnh.
        return bareVerb(words)
    }

    /**
     * Nối vế sau vào vế trước thành **một** câu.
     *
     * @param first chữ của vế trước (đã giải mã riêng — [ĐO §1.2] giải mã RIÊNG từng khúc mới ra đủ chữ; giải mã
     *   cả cửa sổ có khoảng lặng ở giữa là đúng ca *"mở vietmap **một**"* nuốt ba chữ).
     * @param second chữ của vế sau; rỗng ⇒ trả nguyên [first] (hành vi hôm nay, không có gì đổi).
     *
     * ## Vì sao phải gỡ phần TRÙNG, không nối thẳng
     * [ĐO §1.2] cùng bản thu `…-184201`: khúc `[900..2600] ms` cho ra *"áp **vào ô số một**"*, khúc
     * `[1400..3400] ms` cho ra *"**ô số một**"* — vế sau **lặp lại** cái đầu dở của vế trước, vì người nói nhắc
     * lại cụm khi nói tiếp. Nối thẳng ra *"mở vietmap vào ô vào ô số một"*: bộ phân tích đọc mệnh đề ô **hai**
     * lần, và không ai biết nó chọn cái nào. Gỡ phần trùng **dài nhất** ở biên hai vế thì cả hai cách nói tiếp
     * (*"số hai"* và *"vào ô số hai"*) cùng ra một câu.
     */
    fun join(first: String, second: String): String {
        val a = VoiceLexicon.tokenize(first)
        val b = VoiceLexicon.dropLeadingFillers(VoiceLexicon.tokenize(second))
        if (b.isEmpty()) return first.trim()
        if (a.isEmpty()) return raw(b)
        // Vế sau đọc lại NGUYÊN vế trước (bộ giải mã chạy hai lần trên cùng khúc tiếng) ⇒ giữ một bản.
        if (norms(a) == norms(b)) return raw(a)
        val overlap = overlap(a, b)
        return raw(a.dropLast(overlap) + b)
    }

    /**
     * ═══ R6 (2.76) · CÓ vế sau thì **luôn thử ghép** — kể cả khi vế trước đã đủ nghĩa ════════════════════════
     *
     * Một luật cho **cả hai** lối vào (phiên mic `VoiceOpenTurnArm.result` + cầu đo `VoiceWavProbe`): `null` =
     * giữ nguyên [head] (đúng hành vi 2.75). Chỗ gọi chỉ được gọi tới đây khi bộ ngắt câu **đã có** một đoạn tiếng
     * sau điểm ngắt (`tailRange ≠ null`) — tệp này không mở micro và không kéo dài một mili-giây nghe nào.
     *
     * ## Bệnh nó chữa — [ĐO xe 2026-09-27 10:42:46, log `KachiVoiceTiming`]
     * Owner nói *"mở vietmap"* ⟨ngừng để nghĩ⟩ *"vào ô số hai"*. Vế trước *"mở vietmap"* **đủ nghĩa** ([isOpen] =
     * `false`) nên `result()` của 2.75 trả về ngay — trong khi bộ ngắt câu đã thu được vế sau (`tieng_dut` 2 400 →
     * 4 384 ms sau `flush`) vì vế sau bắt đầu **trong lúc** lượt giải mã vế trước còn chạy (1,3–2 s). Khúc tiếng ấy
     * bị bỏ đi; VietMap mở vào ô CŨ, người lái không biết vì sao.
     *
     * ## Hai nhánh, và cổng của nhánh mới
     *  • vế trước **dở** ⇒ [join] như 2.74 (không đổi một byte);
     *  • vế trước **đủ** ⇒ [refine]: chỉ nhận câu ghép khi bộ phân tích đọc nó ra **cùng ý định, đầy đủ hơn** — hôm
     *    nay là *mở app* có thêm **ô** (`OpenApp.slot`: `null` → có số, mọi trường khác y nguyên). Vế sau là một lệnh
     *    khác (*"mở vietmap"* + *"bật đèn đọc"*), là tiếng ồn, hay là một vế ô **cụt** (*"vào ô"*) ⇒ `null` ⇒ giữ vế
     *    trước — không có đường nào làm một câu đang đúng thành sai.
     *
     * Không nới sang họ khác (lệnh xe · nhạc · dẫn đường) vì chưa có một lượt đo nào cho chúng ([CHƯA BIẾT]); luật
     * *"cùng ý định, đầy đủ hơn"* viết theo **hình dạng** dữ liệu để khi có số đo thì thêm một nhánh `when`, không
     * thêm một bảng câu.
     */
    fun attach(head: String, tail: String): String? =
        if (isOpen(head)) join(head, tail) else refine(head, tail)

    /**
     * ═══ Vế trước còn CHỖ để ghép không — cổng đọc **trước** khi bỏ công giải mã vế sau ([P2] soát 2026-09-27) ══════
     *
     * [attach] chỉ có hai đường nhận: vế trước **dở** ([isOpen] ⇒ [join]), hoặc vế trước là *mở app CHƯA có ô*
     * ([refines] — điều kiện `before.slot == null`). Mọi vế trước khác ⇒ `attach` trả `null` **100 %**, tức lượt giải
     * mã vế sau (`rec.rangeResult`, [ĐO xe] 1,3–2 s cho một lượt) là công bỏ đi — mà nó nằm trên **đường tới hành
     * động**: *"bật đèn đọc"* + một tiếng nói của khách trong cabin ⇒ đèn lên muộn hơn 2.75 đúng một lượt giải mã,
     * trong lúc xe đang chạy.
     *
     * Hàm này là **phép hỏi thuần** (cùng bộ phân tích, cùng từ vựng tĩnh với [refine]) nên nó KHÔNG đổi kết quả một
     * lượt nào: mọi vế trước bị nó chặn đều là vế mà [attach] sẽ trả `null`. Ở `:core` chứ không phải `:app` để không
     * mở một ngữ pháp thứ hai ngoài bộ phân tích (CLAUDE.md §7), và để test được off-car.
     *
     * ⚠ KHÔNG gộp thêm cổng *"vế sau bắt đầu trong `OPEN_JOIN_WINDOW_MS`"* hay *"vế sau đủ dài"*: ca [ĐO] 27/09
     * 10:42:46 chính là một vế sau bắt đầu **trong lúc** lượt giải mã vế trước còn chạy (`speaking()` còn `true`,
     * chưa qua cửa sổ 1 200 ms) ⇒ hai cổng ấy sẽ bỏ đúng ca mà R6 sinh ra để cứu, và không có phép đo nào cho chúng.
     */
    fun mayAttach(head: String): Boolean {
        if (isOpen(head)) return true
        val before = VoiceIntentParser.parseOne(head)
        return before is VoiceIntent.OpenApp && before.slot == null
    }

    /**
     * Câu ghép nếu nó là bản **đầy đủ hơn** của [head] (cùng ý định), ngược lại `null`. Xem KDoc [attach].
     *
     * Đi qua đúng `VoiceIntentParser.parseOne` với từ vựng **tĩnh** (không nhãn app đã cài): *"vietmap"* giải qua
     * bảng đích, còn nhãn máy (*"YouTube"*) thì đường khớp mờ/tiền tố cũng tới được vì cả hai đường đều đọc bảng
     * đích. Nhãn app **chỉ có trên máy** (không trong bảng đích) ⇒ cả hai vế cùng ra `Unknown` ⇒ `null` — thành thật
     * hơn là đoán: chỗ gọi không có danh sách app ở tầng nghe, và vế trước vẫn được xử lý y như 2.75.
     */
    fun refine(head: String, tail: String): String? {
        val joined = join(head, tail)
        if (joined == head.trim()) return null
        val before = VoiceIntentParser.parseOne(head)
        val after = VoiceIntentParser.parseOne(joined)
        return if (refines(before, after)) joined else null
    }

    /** [after] là [before] **có thêm** phần còn thiếu — hôm nay: ô của một lệnh mở app. */
    private fun refines(before: VoiceIntent, after: VoiceIntent): Boolean = when {
        before is VoiceIntent.OpenApp && after is VoiceIntent.OpenApp ->
            before.slot == null && after.slot != null && before.copy(slot = after.slot) == after
        else -> false
    }

    // ── Bảng vế dở: SINH từ ngữ pháp, không khai tay ────────────────────────────────────────────

    /**
     * Giới từ mở đầu một mệnh đề chỉ ô. Ba từ đầu là ba cách nói thật (*"**vào** ô 2"* · *"**ở** ô 2"* ·
     * *"**sang** ô 2"*), hai từ sau là dạng EN của [VoiceLexicon.SLOT_WORDS].
     *
     * ⚠ Khai ở ĐÂY, không thêm vào [VoiceLexicon.SLOT_WORDS]: bảng kia là **nguồn của tệp hotword** và của tầng
     * so khớp chữ, nên mỗi từ thêm vào đó đổi cả hai đường đang chạy tốt (CLAUDE.md §6 — đường mới xuống cuối).
     * Ở đây chúng chỉ tham gia đúng một câu hỏi *"câu này có dở không"*, và luôn đi kèm một [VoiceLexicon.SLOT_HEADS]
     * nên cụm ngắn nhất vẫn là **hai** từ (*"ở ô"*) — không có vế dở một-từ nào ra đời từ họ này.
     */
    private val SLOT_PREPS = listOf("vao", "o", "sang", "in", "into")

    /**
     * Mọi vế dở của ba họ [S] · [P] · [N] — mỗi phần tử là một cụm **không dấu** theo hợp đồng [Token.norm].
     *
     * Xếp theo độ dài giảm dần để phép so ở [endsWith] gặp cụm dài trước (luật *dãy dài nhất thắng* của cả dự án).
     */
    private val HEADS: List<List<String>> by lazy {
        val out = LinkedHashSet<List<String>>()
        // [S] — lấy chính bảng hotword mệnh đề ô rồi **bỏ con số đuôi**: cái còn lại đúng là mệnh đề mất đối số.
        VoiceSlotPhrases.SPOKEN.forEach { phrase ->
            val w = norms(VoiceLexicon.tokenize(phrase))
            if (w.size >= 3 && w.last() in VoiceLexicon.NUMBER_WORDS) out.add(w.dropLast(1))
        }
        // [S] — và mọi tổ hợp giới từ × đầu mệnh đề × (có/không từ đệm), cho những cách nói bảng trên không sinh.
        SLOT_PREPS.forEach { p ->
            VoiceLexicon.SLOT_HEADS.forEach { h ->
                out.add(listOf(p, h))
                VoiceLexicon.SLOT_ORDINALS.forEach { o -> out.add(listOf(p, h, o)); out.add(listOf(h, o)) }
            }
        }
        // [P] — cụm đánh dấu hồ sơ đứng cuối câu = thiếu tên (ca 8/8 lượt của xe).
        VoiceProfileNames.MARKERS.forEach { out.add(it) }
        // [N] — cụm mở từ vựng, BỎ những cụm tự nó đã là một câu lệnh đủ (*"phát nhạc"*, *"play"*).
        VoiceOpenVocab.TRIGGERS.forEach { if (nothingOnItsOwn(it)) out.add(it) }
        out.filter { it.size >= 2 || it.size == 1 && it.first().length >= LONE_HEAD_MIN }
            .sortedByDescending { it.size }
    }

    /**
     * Vế dở **một từ** phải dài ≥ 5 ký tự (*"profile"*).
     *
     * Từ một-âm không dấu (`o`, `so`, `qua`) trùng quá nhiều tiếng động và tiếng đệm tiếng Việt; KDoc
     * [VoiceLexicon.SLOT_HEADS] đã ghi đúng cái bẫy ấy cho chữ `o`. Ngưỡng này giữ nó ngoài cửa **bằng một luật**,
     * không bằng một danh sách loại trừ phải nhớ cập nhật.
     */
    private const val LONE_HEAD_MIN = 5

    // ── Hai cổng hỏi chính bộ phân tích ─────────────────────────────────────────────────────────

    /** Cụm này **tự nó** đã là một câu lệnh chưa — hỏi đúng bộ phân tích của tầng chữ, không đoán. */
    private fun nothingOnItsOwn(phrase: List<String>): Boolean =
        VoiceIntentParser.parseOne(phrase.joinToString(" ")) is VoiceIntent.Unknown

    /**
     * Phần câu trước một cụm đánh dấu *"bằng &lt;app&gt;"* có phải một vế mà tên app **gắn được** vào không.
     *
     * Cùng ba loại mà [VoiceTailClause.appAfterMarker] nhận đuôi chọn app: nhạc · dẫn đường · mở app. Nhờ cổng
     * này mà *"hôm nay trời đẹp quá"* (bỏ dấu ra `… qua`) không bị coi là câu dở.
     */
    private fun attachable(before: List<String>): Boolean =
        when (VoiceIntentParser.parseOne(before.joinToString(" "))) {
            is VoiceIntent.Media, is VoiceIntent.Nav, is VoiceIntent.NavigateSaved, is VoiceIntent.OpenApp -> true
            else -> false
        }

    /**
     * Cả câu chỉ là một động từ của [VoiceGrammar.VERBS] và bộ phân tích nói nó chưa thành lệnh.
     *
     * Cổng thứ hai là cái phân biệt *"mở"* (dở) với *"tạm dừng"* (một câu lệnh PAUSE **đủ**, và nó cũng là một
     * cụm động từ trọn vẹn) — không có cổng ấy thì mỗi lệnh tạm dừng phải chờ thêm 1,2 s.
     */
    private fun bareVerb(words: List<String>): Boolean =
        VoiceGrammar.VERBS.any { (phrase, _) -> phrase == words } && nothingOnItsOwn(words)

    // ── Tiện ích chuỗi ──────────────────────────────────────────────────────────────────────────

    /**
     * Các từ **có nghĩa** của câu, đã bỏ lời khách sáo hai đầu và từ đệm ở đuôi.
     *
     * Bỏ đuôi đệm là bắt buộc: bộ giải mã rất hay mọc thêm *"ừ"* / *"ạ"* ở cuối ([ĐO] log xe, xem KDoc
     * [VoiceVadTrim]), và một chữ đệm ở đuôi sẽ che mất vế dở đứng ngay trước nó.
     */
    private fun tail(text: String): List<String> {
        val t = VoiceLexicon.stripCourtesy(VoiceLexicon.tokenize(text))
        return norms(t).dropLastWhile { it in VoiceLexicon.FILLERS }
    }

    private fun norms(t: List<Token>): List<String> = t.map { it.norm }

    private fun raw(t: List<Token>): String = t.joinToString(" ") { it.raw }

    private fun endsWith(words: List<String>, phrase: List<String>): Boolean =
        words.size >= phrase.size && words.subList(words.size - phrase.size, words.size) == phrase

    /** Số từ ở ĐUÔI vế trước cũng là ĐẦU vế sau — dài nhất trước, 0 khi không trùng gì. */
    private fun overlap(a: List<Token>, b: List<Token>): Int {
        val aw = norms(a)
        val bw = norms(b)
        for (k in minOf(aw.size, bw.size) downTo 1) {
            if (aw.subList(aw.size - k, aw.size) == bw.subList(0, k)) return k
        }
        return 0
    }
}
