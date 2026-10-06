package com.byd.clusternav.launcher

/**
 * ═══ 2.93 `WALLART-CACHE-BOUND` — bộ đệm ảnh mờ `wallpapers/.kachi-art/` có TRẦN (thuần, `:core`) ══════════════════════════
 *
 * Bệnh [ĐO mã]: `WallArtBuilder` ghi `<khoá>.png` + `<khoá>.txt` (vài chục KB/khoá) và KHÔNG ai xoá: khoá mang mtime + cỡ tệp
 * + cỡ màn + cách phủ + mức làm tối ⇒ mỗi lần đổi ảnh / đổi mức làm tối / đổi ảnh cùng tên là một cặp tệp MỚI, cặp cũ nằm
 * lại mãi. Từ 2.92 bộ dọn chẩn đoán cũng KHÔNG chạm thư mục ảnh nền (ADR-0004 — dữ liệu người dùng) ⇒ trần phải là luật
 * riêng của bộ đệm này (spec `kachi-292-diag-cap.html` OQ1).
 *
 * Luật (chỉ đụng tệp ĐỆM — `.png`/`.txt` có khoá đúng khuôn; ảnh gốc của người dùng nằm ở thư mục cha, không bao giờ vào
 * đây):
 *  1. ảnh nguồn không còn trong thư mục ảnh ⇒ xoá mọi khoá của nó (đệm mồ côi);
 *  2. mỗi ảnh nguồn giữ [KEEP_PER_SOURCE] khoá MỚI nhất (khoá đang dùng + một bản trước để đổi qua lại mức làm tối không
 *     phải nấu lại);
 *  3. tổng ≤ [MAX_KEYS] khoá, bỏ khoá CŨ nhất trước;
 *  4. khoá vừa nấu/đang hiển thị ([protect]) không bao giờ bị xoá.
 */
object WallArtCachePolicy {

    const val KEEP_PER_SOURCE = 2
    /** Trần tổng: 128 khoá × vài chục KB ≈ vài MB — trình chiếu 100+ ảnh vẫn giữ đủ đệm cho mỗi ảnh một khoá. [ĐỀ XUẤT]. */
    const val MAX_KEYS = 128

    /** Một khoá trong bộ đệm: tên khoá (không đuôi) + thời điểm sửa mới nhất của các tệp của nó (ms). */
    data class Entry(val key: String, val modifiedMs: Long)

    /** Số đoạn cố định ở cuối khoá: mtime · cỡ tệp · WxH · cách phủ · mức làm tối (`WallArtBuilder.build`). */
    private const val TAIL = 5

    private val WXH = Regex("""\d+x\d+""")

    /**
     * Tên ẢNH NGUỒN của [key] (`<tên ảnh>-<mtime>-<cỡ>-<W>x<H>-<FIT>-<dim>`; tên ảnh được chứa `-`), `null` = không đúng
     * khuôn ⇒ tệp lạ, không đụng.
     */
    fun sourceOf(key: String): String? {
        val parts = key.split('-')
        if (parts.size <= TAIL) return null
        val tail = parts.takeLast(TAIL)
        val ok = tail[0].toLongOrNull() != null && tail[1].toLongOrNull() != null && WXH.matches(tail[2]) &&
            tail[3].isNotEmpty() && tail[4].toIntOrNull() != null
        return if (ok) parts.dropLast(TAIL).joinToString("-").takeIf { it.isNotEmpty() } else null
    }

    /** Các khoá phải xoá (luật 1–4 ở KDoc lớp). [sources] = tên các tệp hiện có trong thư mục ảnh. */
    fun victims(
        entries: List<Entry>,
        sources: Set<String>,
        protect: String?,
        keepPerSource: Int = KEEP_PER_SOURCE,
        maxKeys: Int = MAX_KEYS,
    ): List<String> {
        val known = entries.filter { sourceOf(it.key) != null }
        val out = LinkedHashSet<String>()
        known.groupBy { sourceOf(it.key)!! }.forEach { (src, list) ->
            val newestFirst = list.sortedWith(compareByDescending<Entry> { it.modifiedMs }.thenByDescending { it.key })
            if (src !in sources) out += list.map { it.key }
            else newestFirst.drop(keepPerSource.coerceAtLeast(1)).forEach { out += it.key }
        }
        val left = known.filter { it.key !in out }.sortedWith(compareByDescending<Entry> { it.modifiedMs }.thenByDescending { it.key })
        left.drop(maxKeys.coerceAtLeast(1)).forEach { out += it.key }
        out.remove(protect)
        return out.toList()
    }
}
