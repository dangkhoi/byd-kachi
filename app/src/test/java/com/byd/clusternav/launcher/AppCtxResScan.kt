package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots

/**
 * ═══ R9 (spec `kachi-i18n-zh-th-ms.html`) — TRA CHUỖI QUA CONTEXT ỨNG DỤNG = theo locale MÁY ══════════════════════
 *
 * Context ứng dụng không đi qua `attachBaseContext` của Activity nào ⇒ tài nguyên của nó theo locale của XE. Trước
 * khi có `values-zh-rCN/-th/-ms` thì mọi locale lạ rơi về `values/` (tiếng Việt) nên lỗi không lộ; có ba thư mục ấy
 * rồi thì xe đặt tiếng Trung + người dùng chọn Tiếng Việt ra chữ Trung — [ĐO soát 2.87] ở nhãn camera
 * (`CameraOverlayMask.labelFor` nhận `appCtx` qua bí danh `val ctx = appCtx`), toast `kachi_sc_place_failed`
 * (`app.getString`) và toast `kachi_access_feature_blocked` (`Toast.makeText(c, R.string…)` với `c` = `applicationContext`).
 *
 * Bài canh cũ (`moi Activity ap ngon ngu…`) chỉ soi tệp có `attachBaseContext` ⇒ mù cả ba. Bộ quét này (văn bản, đã
 * bỏ chú thích) bắt ba dạng, theo đúng ba ca thật:
 *  1. `<ctx-ứng-dụng>.getString(` / `getText(` / `getQuantityString(` … (kể cả qua `.resources.`);
 *  2. `Toast.makeText(<ctx-ứng-dụng>, R.string.…` — bản nhận mã chuỗi tra bằng tài nguyên của chính Context đó;
 *  3. `<hàm-tra-chuỗi>(<ctx-ứng-dụng>…` — hàm (ở BẤT KỲ tệp nào được quét) nhận `Context` ở tham số đầu rồi tự gọi
 *     dạng 1/2 trên tham số đó (hoặc bí danh `val c = ctx ?: …` của nó).
 *
 * "Context ứng dụng" = tên quy ước [APP_NAMES] + bí danh gán thẳng từ chúng trong cùng tệp (`val ctx = appCtx`).
 * Tra qua `LangHost.localized(x)` không khớp mẫu nào (`localized(x).getString(` không có `x.` đứng trước) ⇒ đường
 * sửa đúng tự đi qua. Thận trọng về phía báo THỪA (bí danh tính cho cả tệp): ca hợp lệ khai ở danh sách trắng kèm lý do.
 */
internal object AppCtxResScan {

    val APP_NAMES = setOf("app", "appCtx", "appContext", "applicationContext")

    private const val RES = "getString|getText|getQuantityString|getQuantityText|getStringArray|getTextArray"
    private val READER_FUN = Regex("""\bfun\s+(?:[\w.<>?]+\.)?(\w+)\s*\(\s*(\w+)\s*:\s*Context\??\s*[,)]""")

    /** Hàm nhận `Context` ở tham số đầu và tự tra chuỗi trên nó — tên hàm, trên MỌI tệp đưa vào. */
    fun readers(files: Map<String, String>): Set<String> = files.values.flatMap { code ->
        READER_FUN.findAll(code).mapNotNull { m ->
            val body = runCatching { SourceRoots.body(code.substring(m.range.first), "fun ") }.getOrNull() ?: return@mapNotNull null
            val p = m.groupValues[2]
            val names = setOf(p) + Regex("""\bva[lr]\s+(\w+)\s*=\s*${Regex.escape(p)}\b(?!\s*[.(])""").findAll(body).map { it.groupValues[1] }
            m.groupValues[1].takeIf { names.any { n -> readsWith(body, n) } }
        }.toList()
    }.toSet()

    /** `"Tệp.kt: đoạn khớp"` cho mọi chỗ tra chuỗi qua Context ứng dụng. [files] = tên tệp → mã đã bỏ chú thích. */
    fun offenders(files: Map<String, String>): List<String> {
        val readers = readers(files)
        return files.flatMap { (name, code) ->
            val names = APP_NAMES + aliases(code)
            val alt = names.joinToString("|") { Regex.escape(it) }
            val hits = mutableListOf<String>()
            names.forEach { n -> hitsWith(code, n).forEach { hits += it } }
            if (readers.isNotEmpty()) {
                val fn = readers.joinToString("|") { Regex.escape(it) }
                Regex("""\b(?:$fn)\s*\(\s*(?:[\w.]+\.)?(?:$alt)\b\s*[,)]""").findAll(code).forEach { hits += it.value }
            }
            hits.distinct().map { "$name: ${it.trim()}" }
        }.sorted()
    }

    /** Bí danh: `val x = app` / `val x: Context = activity.applicationContext` (giá trị là CHÍNH Context, không gọi gì thêm). */
    private fun aliases(code: String): Set<String> {
        val alt = APP_NAMES.joinToString("|")
        return Regex("""\bva[lr]\s+(\w+)\s*(?::\s*Context\??)?\s*=\s*(?:[\w.]+\.)?(?:$alt)\b(?!\s*[.(])""")
            .findAll(code).map { it.groupValues[1] }.toSet()
    }

    private fun readsWith(code: String, n: String): Boolean = hitsWith(code, n).isNotEmpty()

    private fun hitsWith(code: String, n: String): List<String> {
        val q = Regex.escape(n)
        return Regex("""(?<![\w)])$q\s*\??\.\s*(?:resources\s*\??\.\s*)?(?:$RES)\s*\(""").findAll(code).map { it.value }.toList() +
            Regex("""Toast\.makeText\(\s*(?:[\w.]+\.)?$q\s*,\s*R\.string\.""").findAll(code).map { it.value }.toList()
    }
}
