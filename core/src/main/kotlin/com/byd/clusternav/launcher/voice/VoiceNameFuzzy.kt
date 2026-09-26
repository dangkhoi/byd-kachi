package com.byd.clusternav.launcher.voice

/**
 * ═══ SO KHỚP **TÊN RIÊNG** CHỊU LỖI ASR — một phép so, ba chỗ gọi ═══════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bệnh nó chữa — [ĐO xe 2026-09-26 · 30 bản thu thật, chạy lại off-car trên máy ảo 2026-09-26]
 * Cùng mô hình (`zipformer-vi-int8-2025-04-20`), cùng WAV, phát lại qua `cmd wav` cho ra **đúng** chuỗi mà xe
 * nghe được — nên ba ca dưới là ca [ĐO], không phải ca dựng tay:
 *
 * | nói | mô hình in ra | trước bản này |
 * |---|---|---|
 * | mở YouTube vào ô hai | *"mở **youtubex** vào ô hai"* | `NO_OBJECT` |
 * | đặt VietMap vào ô số một | *"đặt **vietp** vào ô số một"* | `MISMATCH` |
 * | chuyển sang hồ sơ Mặc định | *"chuyển sang hồ sơ **định**"* | `MISMATCH` |
 *
 * Tên app và tên hồ sơ là **danh từ riêng**: mô hình VN không có token cho chúng nên nó ghép âm gần nhất, và cái
 * lệch luôn ở **đuôi** (thêm `x`, rụng `ma`) hoặc ở **một từ đầu bị nuốt** (`mặc` mất). Hai hình dạng lệch ấy
 * cần hai phép so khác nhau — và cả hai phải hẹp, vì đoán sai ở đây là **mở nhầm app** hoặc **đổi hồ sơ tài xế**.
 *
 * ## Hai phép so, và cổng của từng cái
 *  • [nearly] — **lệch ký tự trong cùng một cái tên** (`youtubex` ↔ `youtube`). Ba cổng: neo [PREFIX_ANCHOR] ký
 *    tự đầu · lệch ≤ [MAX_EDITS] · cả hai chuỗi ≥ [MIN_LEN] ký tự. Đây đúng luật mà [VoiceAppTargets.bySpokenFuzzy]
 *    đã đo từ 2026-09-20 (*"vietna"* ⇒ VietMap); tệp này chỉ **đem nó ra một chỗ** để tên hồ sơ và nhãn app đã
 *    cài dùng cùng một phép so, thay vì mỗi nơi một bản (CLAUDE.md §4.1 DRY).
 *  • [wordEdge] — **rụng hẳn một/hai TỪ ở đầu hoặc cuối** một cái tên nhiều từ (*"định"* ⇒ *"Mặc định"*).
 *    [nearly] không bắt được ca này (neo tiền tố `dinh` ≠ `macd`), và nới neo ra để bắt nó là mở cửa cho mọi âm
 *    tiết lẻ khớp vào một cái tên — nên nó là một luật RIÊNG, với cổng riêng: phần người ta nói phải **trùng
 *    nguyên văn** một dải từ ở **đầu hoặc cuối** tên, dài ≥ [MIN_EDGE_CHARS] ký tự, và tên không được dài hơn
 *    quá [MAX_EDGE_MISSING] từ.
 *
 * ## Luật *"nhập nhằng thì KHÔNG chọn"*
 * Tệp này chỉ trả lời *"hai cái tên này có gần nhau không"*. Việc **chọn** nằm ở [pickUnique]: có hai ứng viên
 * khác danh tính cùng khớp ⇒ trả `null`. Cùng lẽ [VoicePhoneticMatch.MARGIN]: một câu hỏi lại tốn hai giây, một
 * lệnh đoán sai trên xe đang chạy thì không lấy lại được.
 */
internal object VoiceNameFuzzy {

    /** Cụm ngắn hơn ngần này ký tự thì rụng một âm cũng thành một từ khác hẳn ⇒ không khớp mờ. */
    const val MIN_LEN = 5

    /** Số ký tự đầu phải khớp Y NGUYÊN ở [nearly] — cái neo giữ nó không bắt sang một cái tên khác. */
    const val PREFIX_ANCHOR = 4

    /** Lệch tối đa (thêm/bớt/đổi) sau khi đã neo tiền tố. 2 = đủ cho *"vietp"* ↔ *"vietmap"*. */
    const val MAX_EDITS = 2

    /** Dải từ ở biên phải dài ngần này ký tự mới được tính ⇒ *"số"*, *"2"*, *"ai"* không khớp được tên nào. */
    const val MIN_EDGE_CHARS = 4

    /** Tên được phép dài hơn phần người ta nói ngần này TỪ. 2 = *"định"* ⇒ *"Mặc định"*, không tới *"A B C định"*. */
    const val MAX_EDGE_MISSING = 2

    /**
     * [said] có phải chính [full] với ≤ [MAX_EDITS] ký tự lệch, sau khi neo [PREFIX_ANCHOR] ký tự đầu?
     *
     * So trên chuỗi đã **ghép liền** nên không phụ thuộc việc mô hình tách *"vietna"* hay *"viet na"*.
     */
    fun nearly(full: String, said: String): Boolean {
        if (full.length < MIN_LEN || said.length < MIN_LEN) return false
        if (full.take(PREFIX_ANCHOR) != said.take(PREFIX_ANCHOR)) return false
        // Chặn theo ĐỘ DÀI trước để khỏi chạy bảng cho hai chuỗi lệch hẳn nhau (*"youtub"* vs *"youtubemusic"*).
        if (kotlin.math.abs(full.length - said.length) > MAX_EDITS) return false
        return edits(full, said) <= MAX_EDITS
    }

    /**
     * [said] có trùng nguyên văn một dải từ ở **đầu** hoặc **cuối** [full] không (ca *nuốt mất một từ*)?
     *
     * Ba cổng: dải phải dài ≥ [MIN_EDGE_CHARS] ký tự (ghép liền) · phần thiếu ≤ [MAX_EDGE_MISSING] từ · [said]
     * không rỗng. Cố ý **không** nhận dải ở GIỮA: *"Nhà ngoại quê"* thì *"ngoại"* nằm giữa, và một từ giữa tên
     * là thứ trùng với quá nhiều câu thường.
     */
    fun wordEdge(said: List<String>, full: List<String>): Boolean {
        if (said.isEmpty() || full.isEmpty() || said.size >= full.size) return false
        if (full.size - said.size > MAX_EDGE_MISSING) return false
        if (said.sumOf { it.length } < MIN_EDGE_CHARS) return false
        return full.take(said.size) == said || full.takeLast(said.size) == said
    }

    /**
     * Ứng viên DUY NHẤT khớp, hoặc `null` khi không có / có nhiều hơn một **danh tính** khác nhau.
     *
     * @param candidates cặp (danh tính, các từ đã bỏ dấu của tên). Hai dòng cùng danh tính (nhãn app đã cài và
     *   một cách gọi của cùng app đó) **không** phải nhập nhằng — chúng dẫn về cùng một chỗ.
     * @param match phép so đã chọn ([nearly] trên chuỗi ghép liền, hay [wordEdge] trên dãy từ).
     */
    fun <T> pickUnique(candidates: List<Pair<T, List<String>>>, match: (List<String>) -> Boolean): T? {
        var hit: T? = null
        candidates.forEach { (id, words) ->
            if (!match(words)) return@forEach
            if (hit != null && hit != id) return null
            if (hit == null) hit = id
        }
        return hit
    }

    /** Cả hai phép so, thứ tự: lệch ký tự trước (chặt hơn), rụng từ ở biên sau. */
    fun matches(said: List<String>, full: List<String>): Boolean =
        nearly(full.joinToString(""), said.joinToString("")) || wordEdge(said, full)

    /**
     * Khoảng cách Levenshtein. Bảng **hai hàng** (chuỗi ở đây dài ≤ ~15 ký tự nên không cần tối ưu gì thêm);
     * thuần Kotlin, không `android.*`, kiểm cạn off-car.
     */
    fun edits(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val sub = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(sub, prev[j] + 1, cur[j - 1] + 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
