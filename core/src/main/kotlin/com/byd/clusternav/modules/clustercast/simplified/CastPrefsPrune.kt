package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 2.98 · R6-E — gỡ khoá hình học chiếu của app ĐÃ GỠ KHỎI XE (có ân hạn) ═════════════════════════════════════
 *
 * [ĐO mã] khoá theo app của lớp chiếu không bao giờ bị gỡ, kể cả khi app đã gỡ khỏi xe:
 *  • `simple_cast_prefs`: `config_{size,overscan,density,bounds}_<gói>[__L|R<pct>][__RECT]` ([CastGeometryGuard.FAMILY_KEY]);
 *  • `cast-v2-app-catalog` (V2, không còn đường đọc sống): `scale-{dpi,l,t,r,b}:<gói>`, `dpi:<gói>`.
 * Mỗi app ≈ vài trăm byte, mỗi lần đọc prefs nạp lại cả tệp ⇒ phình theo số app từng chiếu, không bao giờ co.
 *
 * ## Luật (fail-safe — CLAUDE.md §4: phạm vi tường minh, có đường trả lại)
 *  1. Chỉ chạm khoá mà [packageOf] nhận ra (danh sách mẫu CHO PHÉP); khoá lạ ⇒ không đụng.
 *  2. Hỏi "gói còn cài không" qua [installed]: `true` = còn · `false` = CHẮC CHẮN không cài (PackageManager trả
 *     NameNotFound) · `null` = không biết. **Bất kỳ** câu trả lời `null` nào ⇒ cả lượt KHÔNG gỡ gì ([Plan.aborted]) —
 *     binder hỏng không bao giờ được biến thành "xoá cấu hình của mọi app".
 *  3. **Ân hạn [GRACE_MS] (30 ngày)**: gói vắng lần đầu ⇒ chỉ ghi sổ "vắng từ lúc …"; còn vắng sau ân hạn mới gỡ. Lý do:
 *     cài lại bản mod khác chữ ký (VietMap mod, YouTube ReVanced) BẮT BUỘC gỡ rồi cài — người lái không được mất khung
 *     đã chỉnh chỉ vì làm việc đó qua một lần tắt máy. Gói quay lại ⇒ xoá khỏi sổ.
 *  4. Đồng hồ lùi (`now < since`) ⇒ đặt lại mốc = now (không gỡ sớm vì giờ đầu xe nhảy).
 *  5. Có biên: tối đa [MAX_PACKAGES] gói mỗi lượt; sổ chỉ chứa gói đang có khoá ⇒ không tự phình.
 *
 * THUẦN ⇒ `CastPrefsPruneTest`. Chỗ gọi: `:app` `housekeeping/CastPrefsHousekeeping` (một lần mỗi tiến trình, luồng nền).
 */
object CastPrefsPrune {

    const val GRACE_MS: Long = 30L * 24L * 60L * 60L * 1000L

    const val MAX_PACKAGES: Int = 256

    private val CATALOG_KEY = Regex("^(?:scale-(?:dpi|l|t|r|b)|dpi):(" + CastGeometryGuard.PACKAGE.pattern + ")$")
    /**
     * Gói của một khoá theo app, hoặc `null` nếu khoá không thuộc họ nào được phép gỡ. Họ `config_*`: bỏ tiền tố trường rồi
     * dùng CHÍNH [CastGeometryGuard.appOfRecord] (bỏ `__L30`/`__RECT`) — không chép lại luật hậu tố.
     */
    fun packageOf(key: String): String? {
        if (CastGeometryGuard.isFamilyKey(key)) {
            val field = CastGeometryGuard.fieldOf(key) ?: return null
            return CastGeometryGuard.appOfRecord(key.removePrefix(field.prefix)).takeIf { CastGeometryGuard.PACKAGE.matches(it) }
        }
        return CATALOG_KEY.matchEntire(key)?.groupValues?.get(1)
    }

    /**
     * @param removeKeys khoá cần gỡ (theo thứ tự đầu vào).
     * @param missingSince sổ mới: gói đang vắng → mốc lần đầu thấy vắng (ms, giờ tường).
     * @param aborted `true` ⇒ có gói không trả lời được ⇒ KHÔNG gỡ gì, sổ giữ nguyên.
     */
    data class Plan(val removeKeys: List<String>, val missingSince: Map<String, Long>, val aborted: Boolean)

    fun plan(
        keys: Collection<String>,
        installed: (String) -> Boolean?,
        missingSince: Map<String, Long>,
        nowMs: Long,
        graceMs: Long = GRACE_MS,
    ): Plan {
        val byPkg = LinkedHashMap<String, MutableList<String>>()
        keys.forEach { k -> packageOf(k)?.let { byPkg.getOrPut(it) { mutableListOf() }.add(k) } }
        val pkgs = byPkg.keys.take(MAX_PACKAGES)
        val presence = HashMap<String, Boolean>()
        for (p in pkgs) {
            presence[p] = installed(p) ?: return Plan(emptyList(), missingSince, aborted = true)
        }
        val remove = mutableListOf<String>()
        val ledger = LinkedHashMap<String, Long>()
        // Gói ngoài phần đã hỏi lượt này (vượt MAX_PACKAGES) giữ mốc cũ, chưa quyết.
        missingSince.forEach { (p, t) -> if (p in byPkg && p !in presence) ledger[p] = t }
        for (p in pkgs) {
            if (presence.getValue(p)) continue
            val since = missingSince[p]?.takeIf { it <= nowMs } ?: nowMs
            if (nowMs - since >= graceMs) remove += byPkg.getValue(p) else ledger[p] = since
        }
        return Plan(remove, ledger, aborted = false)
    }

    /** Sổ "vắng từ lúc" ⇄ chuỗi `gói=ms;gói=ms` (một khoá prefs). Mục hỏng ⇒ bỏ (an toàn: chỉ làm trễ một lượt gỡ). */
    fun encode(ledger: Map<String, Long>): String = ledger.entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decode(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Long>()
        raw.split(';').forEach { e ->
            val i = e.indexOf('=')
            if (i <= 0) return@forEach
            val p = e.substring(0, i)
            val t = e.substring(i + 1).toLongOrNull() ?: return@forEach
            if (CastGeometryGuard.PACKAGE.matches(p) && t >= 0) out[p] = t
        }
        return out
    }
}
