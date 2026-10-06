package com.byd.clusternav.launcher.voice

/**
 * Một tên đã dạy còn SỐNG (gói đang cài, không bị nhãn thật của app khác che) — đi vào từ vựng như một cụm
 * [VoiceTermKind.APP] có `id` = [labelKey] (nhãn thật của gói) ⇒ `OpenApp(appName = nhãn thật)`, câu đọc lại là
 * nhãn người dùng NHÌN THẤY, và phép khớp mờ coi tên đã dạy + nhãn là MỘT app (spec §4.6).
 *
 * @property targetKey mã bảng đích ([VoiceAppTargets]) nếu gói thuộc bảng — để *"phát nhạc trên &lt;tên đã dạy&gt;"*
 *   giao chuỗi được cho đúng app (tra [VoiceAppTarget.packages], không viết cứng gói nào).
 */
data class VoiceAppAlias(
    val words: List<String>,
    val accented: String,
    val labelKey: String,
    val pkg: String,
    val targetKey: String?,
    val source: TaughtSource,
)

/**
 * ═══ 2.91 VOICE-APP-NAMES · C3 — BẢNG GỌI APP CỦA PHIÊN, bốn tầng ═══════════════════════════════════════════════
 *
 * Spec §4.6. Thứ tự (luật "đè dạng ĐOÁN, không đè chữ NHÌN THẤY"):
 *  1. **nhãn thật** (locale máy, đã khử trùng bởi [VoiceAppLabelPick]) — không gì đè được;
 *  2. **tên đã dạy** của hồ sơ đang dùng — bỏ (vào [shadowed]) khi trùng nhãn thật của gói KHÁC, hoặc khi hai gói
 *     cùng mang một tên đã dạy (không đoán hộ người dùng);
 *  3. **suy từ nhãn**: nhãn locale thứ hai (vi/en, `putIfAbsent`) + dạng đọc âm Việt ([VoiceAppPhonetics]) — bỏ khi
 *     trùng một tên đã dạy;
 *  4. khớp mờ — không ở đây, nằm ở [VoiceLastResort] (cổng duy nhất + neo + sàn có sẵn, R7 không thêm phép so lỏng).
 *
 * [keys] giữ đúng hợp đồng `appsByLabel()` hôm nay (khoá → gói) nên mọi chỗ gọi cũ giữ nguyên. Tên đã dạy KHÔNG vào
 * [keys] (chúng vào từ vựng qua [aliases] với id = nhãn thật) — TRỪ ca gói **thua ở khử trùng nhãn** (hai app cùng
 * nhãn): gói ấy không có khoá nhãn nào, nên dạng có dấu của tên đã dạy thành khoá của nó — đường DUY NHẤT để gọi được
 * gói thua bằng giọng.
 */
data class VoiceAppIndex(
    val keys: Map<String, String>,
    val aliases: List<VoiceAppAlias>,
    val shadowed: List<TaughtName>,
) {
    companion object {
        val EMPTY = VoiceAppIndex(emptyMap(), emptyList(), emptyList())

        /** Khoá so trùng của một chuỗi: tách + bỏ dấu + nối một dấu cách (cùng phép [TaughtName.norm]). */
        fun normOf(s: String): String = VoiceLexicon.tokenize(s).joinToString(" ") { it.norm }

        /**
         * @param labels nhãn thật → gói, ĐÃ khử trùng ([VoiceAppLabelPick.Picked.labels]), thứ tự giữ nguyên.
         * @param installed MỌI gói có màn khởi chạy (kể cả gói thua khử trùng) — tên đã dạy chỉ sống khi gói ở đây.
         * @param alt nhãn locale thứ hai → gói (R9); rỗng = không nạp.
         * @param spoken dạng đọc của một nhãn — mặc định [VoiceAppPhonetics.spokenForms] (tham số để test thuần).
         */
        fun build(
            labels: List<Pair<String, String>>,
            installed: Set<String>,
            taught: List<TaughtName>,
            alt: List<Pair<String, String>> = emptyList(),
            spoken: (String) -> List<String> = VoiceAppPhonetics::spokenForms,
        ): VoiceAppIndex {
            val out = LinkedHashMap<String, String>(labels.size * 2 + taught.size)
            labels.forEach { (label, pkg) -> out[label] = pkg }
            val tier1 = HashMap<String, MutableSet<String>>()
            out.forEach { (k, pkg) -> tier1.getOrPut(normOf(k)) { HashSet() }.add(pkg) }
            val tier1Pkgs = out.values.toSet()
            val present = installed + tier1Pkgs
            val live = liveTaught(taught, present) { n, pkg -> tier1[n]?.any { it != pkg } == true }
            // Gói thua khử trùng nhãn: chưa có khoá nào ⇒ tên đã dạy thành khoá (xem KDoc lớp).
            live.forEach { t -> if (t.pkg !in tier1Pkgs) out.putIfAbsent(t.accented, t.pkg) }
            val taughtNorms = live.mapTo(HashSet()) { it.norm }
            fun derived(key: String, pkg: String) { if (key.isNotBlank() && normOf(key) !in taughtNorms) out.putIfAbsent(key, pkg) }
            // Nhãn phụ chỉ của gói đang có màn khởi chạy — nếu không, hai chỗ dựng (build · aliasesOf) lệch nhau.
            val alt2 = alt.filter { it.second in present }
            alt2.forEach { (label, pkg) -> derived(label, pkg) }
            (labels + alt2).forEach { (label, pkg) -> spoken(label).forEach { derived(it, pkg) } }
            val aliases = aliasesOf(out, taught)
            val alive = aliases.mapTo(HashSet()) { it.pkg to normOf(it.accented) }
            val shadowed = taught.filter { it.pkg in present && (it.pkg to it.norm) !in alive }
            return VoiceAppIndex(out, aliases, shadowed)
        }

        /**
         * Tên đã dạy còn sống của bảng [keys] — HÀM THUẦN của `(keys, taught)` để hai chỗ dựng (tiến trình chính ·
         * `:wake`, `VoiceDispatcher` · phiên nghe) luôn ra cùng một kết quả mà không phải chở cả [VoiceAppIndex] qua
         * mọi lambda `appsByLabel` đang có. `build(...).aliases == aliasesOf(build(...).keys, taught)` — bài canh khoá.
         */
        fun aliasesOf(keys: Map<String, String>, taught: List<TaughtName>): List<VoiceAppAlias> {
            val byNorm = HashMap<String, MutableSet<String>>()
            keys.forEach { (k, pkg) -> byNorm.getOrPut(normOf(k)) { HashSet() }.add(pkg) }
            val live = liveTaught(taught, keys.values.toSet()) { n, pkg -> byNorm[n]?.any { it != pkg } == true }
            val firstKey = LinkedHashMap<String, String>()
            keys.forEach { (k, pkg) -> firstKey.putIfAbsent(pkg, k) }
            return live.map { t ->
                VoiceAppAlias(
                    words = t.words,
                    accented = t.accented,
                    labelKey = firstKey[t.pkg] ?: t.accented,
                    pkg = t.pkg,
                    targetKey = VoiceAppTargets.ALL.firstOrNull { t.pkg in it.packages }?.key,
                    source = t.source,
                )
            }
        }

        /**
         * Tên đã dạy được BIAS cho phiên lệnh (R8) — cho chỗ gọi chỉ có DANH SÁCH khoá + tập gói (hợp đồng
         * `VoiceRecognizer.open(apps, installed)`, không có cặp khoá→gói). Cùng luật sống của [aliasesOf] (gói có mặt ·
         * một chủ duy nhất · không trùng khoá khác), rồi chỉ giữ nguồn GIỌNG (OQ3). Khoá bằng ĐÚNG dạng có dấu của một
         * tên đã dạy là khoá [build] thêm cho gói THUA khử trùng nhãn — không phải va chạm (bản đầu loại nhầm chính những
         * tên ấy khỏi hotword, trong khi đó là đường gọi DUY NHẤT của gói thua).
         */
        fun hotwordNames(keys: Collection<String>, installed: Set<String>, taught: List<TaughtName>): List<TaughtName> {
            val own = taught.mapTo(HashSet()) { it.accented }
            val clash = keys.filterNot { it in own }.mapTo(HashSet()) { normOf(it) }
            return liveTaught(taught, installed) { n, _ -> n in clash }.filter { it.source == TaughtSource.SPEECH }
        }

        /**
         * Tên có gói trong [present], không bị [clash] (nhãn/khoá của gói khác), và không trùng tên đã dạy của một gói
         * KHÁC (xét trên TOÀN bộ [taught], kể cả app đã gỡ — để hai chỗ dựng không lệch nhau theo tập gói). Một gói
         * nhiều mục cùng chuẩn hoá ⇒ giữ mục đầu.
         */
        private fun liveTaught(
            taught: List<TaughtName>,
            present: Set<String>,
            clash: (String, String) -> Boolean,
        ): List<TaughtName> {
            val owners = HashMap<String, MutableSet<String>>()
            taught.forEach { owners.getOrPut(it.norm) { HashSet() }.add(it.pkg) }
            val seen = HashSet<Pair<String, String>>()
            return taught.filter { t ->
                t.words.isNotEmpty() && t.pkg in present && owners[t.norm].orEmpty().size == 1 &&
                    !clash(t.norm, t.pkg) && seen.add(t.pkg to t.norm)
            }
        }
    }
}
