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

    /** Tệp hotword cuối, có tên đã dạy. [taught] rỗng ⇒ ĐÚNG [baseFile] (không cấp phát gì thêm). */
    fun file(baseFile: String, base: List<String>, appNames: List<String>, taught: List<TaughtName>): String {
        if (taught.isEmpty()) return baseFile
        val p = plan(baseFile, taught)
        if (p.accepted.isEmpty()) return baseFile
        val names = p.accepted.map { it.accented }
        val out = SherpaHotwords.phraseFile(base + p.accepted.flatMap { linesOf(it.accented) }, appNames + names)
        val kept = lineSet(baseFile)
        return if (lineSet(out).containsAll(kept)) out else baseFile
    }

    /**
     * Tên nào được bias. [baseFile] = tệp tĩnh ĐÃ lọc (đúng chuỗi [SherpaBiasing.hotwordsFile] trả khi không có tên).
     * Chỉ tên nguồn [TaughtSource.SPEECH] (OQ3: tên gõ không bảo đảm mã hoá được/đúng dấu).
     */
    fun plan(baseFile: String, taught: List<TaughtName>): Plan {
        val kept = lineSet(baseFile)
        val speech = taught.filter { it.source == TaughtSource.SPEECH }
        val accepted = ArrayList<TaughtName>()
        val excluded = ArrayList<Excluded>()
        speech.forEach { t ->
            val killed = killedBy(t.accented, kept)
            val extends = speech.any { o -> o.pkg != t.pkg && (properPrefix(o.words, t.words) || o.norm == t.norm) }
            if (killed.isEmpty() && !extends && linesOf(t.accented).isNotEmpty()) accepted += t
            else excluded += Excluded(t.accented, killed, extends)
        }
        return Plan(accepted, excluded)
    }

    /** Dòng TĨNH (trong [kept]) mà tên [accented] sẽ làm rụng — qua đường (1) hoặc (2) ở KDoc lớp. */
    fun killedBy(accented: String, kept: Set<String>): List<String> {
        val name = SherpaHotwords.normalize(accented) ?: return emptyList()
        val out = LinkedHashSet<String>()
        // (1) dòng mở đầu bằng chính cái tên.
        kept.forEach { line -> if (line == name || line.startsWith("$name ")) out += line }
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

    private fun properPrefix(short: List<String>, long: List<String>): Boolean =
        short.isNotEmpty() && short.size < long.size && long.take(short.size) == short

    private fun lineSet(file: String): Set<String> = file.split('\n').filterTo(HashSet()) { it.isNotBlank() }
}
