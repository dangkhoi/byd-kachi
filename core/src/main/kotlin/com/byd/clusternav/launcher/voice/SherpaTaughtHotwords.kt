package com.byd.clusternav.launcher.voice

/**
 * ═══ 2.91 VOICE-APP-NAMES · C6 — DÒNG HOTWORD CỦA TÊN ĐÃ DẠY + LUẬT ĐƠN ĐIỆU ═════════════════════════════════════
 *
 * Spec §4.8 (+ Pass 1). Mỗi tên nguồn GIỌNG sinh cụm `MỞ/ĐƯA/BẬT <TÊN>` (động từ = [SherpaSpokenWords.VERBS] của
 * [VoiceVerb.OPEN] + [VoiceVerb.ON], cùng nguồn với tên app đích ở [SherpaPhraseHotwords]). Chuỗi do chính mô hình in
 * ra được ghép từ token của nó ⇒ [SUY] luôn mã hoá được BPE; viết HOA là đủ ([SherpaHotwords.normalize]).
 *
 * ## Luật ĐƠN ĐIỆU (bắt buộc): `tệp(tĩnh + tên đã dạy) ⊇ tệp(tĩnh)`
 * [SherpaHotwords.phraseFile] có HAI đường làm rụng một dòng tĩnh, và tên đã dạy chạm được cả hai:
 *  1. [SherpaHotwords.dropAppNameLeading] — tên đi vào `appNames` ⇒ mọi dòng MỞ ĐẦU bằng tên ấy bị xoá (bẫy *"quay"* /
 *     `QUAY LẠI BÀI` mà [SherpaPhraseHotwords.appNames] đã phải trừ);
 *  2. [SherpaHotwords.dropPrefixes] — dòng `MỞ <TÊN>` làm một dòng tĩnh thành TIỀN TỐ theo từ của nó
 *     (`MỞ CỐP XE` ⊂ `MỞ CỐP XE ĐẸP`) ⇒ dòng tĩnh rụng.
 * Tên nào làm rụng ≥ 1 dòng tĩnh thì bị loại khỏi CẢ dòng của nó LẪN `appNames` — tên vẫn khớp ở tầng chữ, chỉ không
 * được kéo ở tầng âm. Thêm: tên nối dài tên đã dạy của một app KHÁC (khuôn `extendsAnotherApp`, bẫy `w10`) cũng bị loại.
 * Phép tra là tập băm trên các dòng đã giữ — O(số tên × số từ), không O(tên × ~2 200 dòng). Sau cùng còn một lưới an
 * toàn: tệp cuối không chứa đủ dòng tĩnh ⇒ trả tệp tĩnh (fail-safe; bài canh đòi nhánh ấy không bao giờ chạy).
 *
 * ## 2.93 — chi phí dựng tệp nằm trên đường *bấm → micro mở* (`VoiceRecognizer.open` chạy TRƯỚC khi mở micro)
 * [ĐO host 2026-10-06, JVM đã hâm, trung vị 40 lượt] tới 2.92 câu "tập băm" ở trên chỉ đúng cho đường (2): đường (1)
 * duyệt cả tệp cho từng tên, phép so "nối dài" tách chữ lại ở MỖI cặp tên, và `dropAppNameLeading` duyệt mọi tên app
 * cho mỗi dòng ⇒ 120 tên đã dạy tốn 34 ms. Nay ([headIndex] · tách chữ một lần · [SherpaHotwords.leadsWith]): 120 tên
 * 6 ms · 110 nhãn app 30,9 → 10,2 ms; tệp ra TRÙNG TỪNG BYTE trước/sau (7 cấu hình + killedBy + plan, 127 phép so).
 */
object SherpaTaughtHotwords {

    /** Lý do một tên không được bias — chỗ gọi (TeachGuard C) hiện cho người dùng, kèm dòng tĩnh bị rụng. */
    data class Excluded(val accented: String, val killedLines: List<String>, val extendsOther: Boolean)

    data class Plan(val accepted: List<TaughtName>, val excluded: List<Excluded>)

    private val verbs: List<String> by lazy {
        (SherpaSpokenWords.VERBS[VoiceVerb.OPEN].orEmpty() + SherpaSpokenWords.VERBS[VoiceVerb.ON].orEmpty()).distinct()
    }

    /** Dòng HOA của một tên (rỗng khi tên không chuẩn hoá được thành cụm — vd toàn chữ số). */
    fun linesOf(accented: String): List<String> {
        val name = SherpaHotwords.normalize(accented) ?: return emptyList()
        return verbs.mapNotNull { SherpaHotwords.normalize("$it $name") }.distinct()
    }

    /** Một tầng của tệp hotword: [file] ĐÃ lọc + đúng hai đầu vào đã sinh ra nó ([SherpaHotwords.phraseFile]). */
    class Stack(val file: String, val phrases: List<String>, val appNames: List<String>)

    /**
     * Chồng một lớp tên lên [s] — tệp hotword cuối của lớp ấy. [names] rỗng / không tên nào qua [plan] ⇒ trả ĐÚNG [s]
     * (không cấp phát gì thêm). Tệp mới không chứa đủ dòng của [Stack.file] ⇒ cũng trả [s] (lưới an toàn của luật đơn
     * điệu). 2.93 (thay `file()` của 2.91, cùng phép từng bước): tên đã dạy là lớp 1, nhãn app chữ Việt
     * ([SherpaLabelHotwords]) là lớp 2 — lớp sau xét [killedBy] trên tệp CỦA lớp trước, nên không làm rụng dòng nào của nó.
     */
    fun push(s: Stack, names: List<TaughtName>): Stack {
        if (names.isEmpty()) return s
        val p = plan(s.file, names)
        if (p.accepted.isEmpty()) return s
        val phrases = s.phrases + p.accepted.flatMap { linesOf(it.accented) }
        val apps = s.appNames + p.accepted.map { it.accented }
        val out = SherpaHotwords.phraseFile(phrases, apps)
        return if (lineSet(out).containsAll(lineSet(s.file))) Stack(out, phrases, apps) else s
    }

    /**
     * Tên nào được bias. [baseFile] = tệp của lớp DƯỚI, ĐÃ lọc (lớp tên đã dạy: đúng chuỗi [SherpaBiasing.hotwordsFile]
     * trả khi không có tên; lớp nhãn app: tệp tĩnh + tên đã dạy). Chỉ tên nguồn [TaughtSource.SPEECH] (OQ3: tên gõ không
     * bảo đảm mã hoá được/đúng dấu).
     */
    fun plan(baseFile: String, taught: List<TaughtName>): Plan {
        val kept = lineSet(baseFile)
        val speech = taught.filter { it.source == TaughtSource.SPEECH }
        val heads = if (speech.isEmpty()) emptyMap() else headIndex(kept)
        // `words` là thuộc tính TÍNH (tách chữ mỗi lần đọc) ⇒ tách MỘT lần mỗi tên; phép so cặp O(tên²) dưới đây trước
        // 2.93 tách lại ở mỗi cặp — [ĐO host] phần lớn chi phí dựng tệp khi có 100+ tên/nhãn. `norm` = `words` nối dấu cách.
        val words = speech.map { it.words }
        val accepted = ArrayList<TaughtName>()
        val excluded = ArrayList<Excluded>()
        speech.forEachIndexed { i, t ->
            val killed = killedBy(t.accented, kept, heads)
            val extends = speech.indices.any { j ->
                speech[j].pkg != t.pkg && (properPrefix(words[j], words[i]) || words[j] == words[i])
            }
            if (killed.isEmpty() && !extends && linesOf(t.accented).isNotEmpty()) accepted += t
            else excluded += Excluded(t.accented, killed, extends)
        }
        return Plan(accepted, excluded)
    }

    /** Dòng TĨNH (trong [kept]) mà tên [accented] sẽ làm rụng — qua đường (1) hoặc (2) ở KDoc lớp. */
    fun killedBy(accented: String, kept: Set<String>): List<String> = killedBy(accented, kept, null)

    /**
     * [heads] = chỉ mục đầu-dòng của [kept] ([headIndex]) — [plan] dựng MỘT lần cho mọi tên (KDoc lớp, mục 2.93); `null`
     * ⇒ duyệt [kept] (một tên lẻ của `TeachGuard`). Hai nhánh cùng kết quả, cùng thứ tự dòng.
     */
    private fun killedBy(accented: String, kept: Set<String>, heads: Map<String, List<String>>?): List<String> {
        val name = SherpaHotwords.normalize(accented) ?: return emptyList()
        val out = LinkedHashSet<String>()
        // (1) dòng mở đầu bằng chính cái tên.
        if (heads != null) heads[name]?.let { out += it }
        else kept.forEach { line -> if (line == name || line.startsWith("$name ")) out += line }
        // (2) tiền tố theo từ của một dòng `MỞ/ĐƯA/BẬT <TÊN>`.
        linesOf(accented).forEach { line ->
            val w = line.split(' ')
            for (n in 2 until w.size) {
                val prefix = w.take(n).joinToString(" ")
                if (prefix in kept) out += prefix
            }
        }
        return out.toList()
    }

    /** Mỗi tiền tố theo từ của một dòng (kể cả cả dòng) → các dòng [kept] mở đầu bằng nó, theo thứ tự duyệt [kept]. */
    private fun headIndex(kept: Set<String>): Map<String, List<String>> {
        val idx = HashMap<String, MutableList<String>>(kept.size * 4)
        kept.forEach { line ->
            var i = line.indexOf(' ')
            while (i > 0) {
                idx.getOrPut(line.substring(0, i)) { ArrayList(2) }.add(line)
                i = line.indexOf(' ', i + 1)
            }
            idx.getOrPut(line) { ArrayList(2) }.add(line)
        }
        return idx
    }

    private fun properPrefix(short: List<String>, long: List<String>): Boolean =
        short.isNotEmpty() && short.size < long.size && long.take(short.size) == short

    private fun lineSet(file: String): Set<String> = file.split('\n').filterTo(HashSet()) { it.isNotBlank() }
}
