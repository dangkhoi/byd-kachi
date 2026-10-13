package com.byd.clusternav.modules.clustercast.simplified

import kotlin.math.abs

/**
 * ═══ 3.1 · R6 — "Dịch nội dung" (overscan TOP có dấu) — phần THUẦN: giá trị · bộ đọc khung cửa sổ · phán quyết ═══════════
 *
 * Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` R6, §4.2. Owner 13/10: *"trước đây clusternav chạy đấy, nên làm thôi"*.
 *
 * ## Vì sao phải TỰ ĐO lúc chạy (không tin lệnh, không tin máy ảo)
 * [ĐO máy ảo A10 13/10, `docs/diagnostics/emu-overscan-neg-2026-10-13.md`] overscan top ÂM KHÔNG dời cửa sổ activity thường
 * (`FLAG_LAYOUT_IN_SCREEN`: Settings/Đồng hồ/Google Maps — `mFrame`, `parent` y nguyên), chỉ dời cửa sổ không cờ đó (Chrome
 * FirstRun: `parent=[0,-T]`, `mFrame` dịch T/2 vì căn giữa). Overscan DƯƠNG đi đường INSET (`Cur insets: overscan=[0,T]`,
 * `mFrame` giữ nguyên, nội dung đệm xuống T). Màn ảo cụm BYD (xdja) là màn khác hẳn overlay display của máy ảo — [CHƯA BIẾT]
 * ROM BYD có ăn không; bản đang chạy đã áp overscan âm cho CarPlay (`DisplayConfig.CARPLAY` top −120) và ClusterNav V1 tự kiểm
 * bằng `overscanVerified`. ⇒ áp → ĐỌC khung cửa sổ thật của app → giữ hoặc trả lại (CLAUDE.md §2/§6/§7: đo, không `if` theo gói).
 *
 * ## Bẫy đã đo (vì sao đọc CỬA SỔ APP, không đọc DisplayFrames)
 * Mọi ca overscan âm đều đổi `DisplayFrames` của display (`mUnrestricted/mContent/mStable=[0,-T]…`, `mDisplayInfoOverscan`)
 * kể cả khi app KHÔNG dời ⇒ đọc khối đó là dương tính giả. Phép đo ở đây chỉ đọc khối `Window #… <gói>/…` của ĐÚNG gói trên
 * ĐÚNG display: đỉnh nội dung = `mFrame.top` + `Cur insets: overscan` top (bắt cả đường dời khung lẫn đường inset).
 */
object CastContentShift {

    /** Dải người lái chỉnh được (px, âm = kéo nội dung LÊN). */
    const val MIN = -200
    const val MAX = 200

    /**
     * Tiền tố khoá phán quyết "app nhận dịch nội dung trên xe này" (`<tiền tố><gói>` → `"ok"`/`"no"`, tệp `simple_cast_prefs`) —
     * phạm vi XE (`ProfileScopeCluster.DEVICE_KEY_PREFIXES`): sự thật đo được của ROM × app, không phải sở thích người lái.
     */
    const val VERDICT_KEY_PREFIX = "cast_shift_verdict_"

    /** Thẻ việc xả trên executor của hàng gộp "Dịch nội dung" (khác thẻ `resize` của bốn thanh khung). */
    const val TAG = "content-shift"

    /**
     * Đọc cửa sổ — chỉ các dòng phép đo cần (tiêu đề khối, display, loại cửa sổ, khung, insets); cùng khuôn `grep` với
     * `ClusterThemeGuard.WINDOWS_CMD`. CHỈ ĐỌC.
     */
    const val WINDOWS_CMD: String = "dumpsys window windows | grep -E 'Window #|mDisplayId=|ty=|mFrame=|Cur insets'"

    /** Overscan ĐANG áp của các display (`DisplayParse.overscan` đọc dòng `mDisplayInfoOverscan` theo `Display: mDisplayId=`). */
    const val DISPLAYS_CMD: String = "dumpsys window displays | grep -E 'Display: mDisplayId=|mDisplayInfoOverscan='"

    /** `wm overscan` chỉ có tới Android 10 (API 29) — [ĐO source A11/A12] lệnh đã gỡ (spec §2.2). */
    fun usableOn(sdkInt: Int): Boolean = sdkInt in 1..29

    fun clamp(px: Int): Int = px.coerceIn(MIN, MAX)

    /** Thành phần TOP của chuỗi overscan `l,t,r,b`; chuỗi hỏng ⇒ 0. */
    fun topOf(overscan: String?): Int = parts(overscan)?.get(1) ?: 0

    /** Chuỗi overscan với TOP = [top] (đã kẹp [MIN]..[MAX]), ba cạnh còn lại GIỮ NGUYÊN; chuỗi hỏng ⇒ coi như `0,0,0,0`. */
    fun withTop(overscan: String?, top: Int): String {
        val p = parts(overscan) ?: intArrayOf(0, 0, 0, 0)
        return "${p[0]},${clamp(top)},${p[2]},${p[3]}"
    }

    private fun parts(overscan: String?): IntArray? {
        val clean = CastGeometryGuard.overscan(overscan) ?: return null
        return clean.split(',').map { it.toInt() }.toIntArray()
    }

    /** Khung cửa sổ CHÍNH của một gói trên một display, đủ cho phép đo dịch. */
    data class WindowProbe(val frameTop: Int, val insetTop: Int) {
        /** Đỉnh nội dung app thật sự vẽ: khung + đệm overscan (đường dương). */
        val contentTop: Int get() = frameTop + insetTop
    }

    private val RE_HDR = Regex("""^Window #\d+ Window\{\S+ u\d+ ([^}]*)\}""")
    private val RE_DISPLAY = Regex("""\bmDisplayId=(-?\d+)""")
    private val RE_TYPE = Regex("""\bty=(\S+)""")
    private val RE_FRAME = Regex("""\bmFrame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]""")
    private val RE_INSET = Regex("""Cur insets: overscan=\[(-?\d+),(-?\d+)\]""")

    private class Block(val tag: String) {
        var display: Int? = null
        var type: String? = null
        var frameTop: Int? = null
        var insetTop: Int = 0
    }

    /**
     * Cửa sổ của [pkg] trên display [displayId] từ `dumpsys window windows` (bản đầy đủ hoặc bản `grep` [WINDOWS_CMD]).
     * Tiêu đề khối phải là `<gói>/…` khớp CHÍNH XÁC gói (không khớp hậu tố gói khác). Nhiều cửa sổ ⇒ ưu tiên
     * `ty=BASE_APPLICATION` (cửa sổ activity), không có thì cửa sổ đầu tiên có khung. `null` = không thấy (không đo được).
     */
    fun probe(dump: String, pkg: String, displayId: Int): WindowProbe? {
        val blocks = ArrayList<Block>()
        var cur: Block? = null
        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            val hdr = RE_HDR.find(line)
            if (hdr != null) {
                cur = Block(hdr.groupValues[1]).also { blocks += it }
                continue
            }
            val b = cur ?: continue
            if (b.display == null) RE_DISPLAY.find(line)?.let { b.display = it.groupValues[1].toInt() }
            if (b.type == null) RE_TYPE.find(line)?.let { b.type = it.groupValues[1] }
            if (b.frameTop == null) RE_FRAME.find(line)?.let { b.frameTop = it.groupValues[2].toInt() }
            RE_INSET.find(line)?.let { b.insetTop = it.groupValues[2].toInt() }
        }
        val mine = blocks.filter { it.tag.startsWith("$pkg/") && it.display == displayId && it.frameTop != null }
        val pick = mine.firstOrNull { it.type == "BASE_APPLICATION" } ?: mine.firstOrNull() ?: return null
        return WindowProbe(pick.frameTop!!, pick.insetTop)
    }

    /**
     * Phán quyết: nội dung có dời ĐÚNG CHIỀU ít nhất NỬA độ dịch xin ([requested] = top mới − top cũ)? Nửa vì cửa sổ căn giữa
     * dời T/2 khi `parent` dời T ([ĐO] Chrome FirstRun −40 ⇒ 20, −120 ⇒ 60). Không dời (activity thường trên máy ảo) ⇒ `false`.
     * [requested] = 0 ⇒ `true` (không có gì để chứng minh).
     */
    fun moved(before: WindowProbe, after: WindowProbe, requested: Int): Boolean {
        if (requested == 0) return true
        val delta = after.contentTop - before.contentTop
        return delta != 0 && (delta > 0) == (requested > 0) && abs(delta) * 2 >= abs(requested)
    }
}

/** Một yêu cầu "Dịch nội dung" cho app [pkg] đang chiếu TOÀN cụm: [shift] px (âm = lên). */
data class CastShiftRequest(val pkg: String, val shift: Int)

/** Kết cục một yêu cầu dịch. */
enum class CastShiftResult {
    /** Đã áp và ĐO thấy nội dung dời (app nhận) — giá trị được lưu cho (gói, hồ sơ). */
    APPLIED,

    /** Cụm đã ở đúng giá trị xin — không lệnh nào. */
    UNCHANGED,

    /** Đã áp nhưng ĐO thấy app không dời ⇒ đã TRẢ overscan cũ ngay, (gói) bị đánh dấu "không nhận" — hàng ẩn từ đó. */
    INEFFECTIVE,

    /** Không đo được / lệnh hỏng — không lưu gì, không đánh dấu (có thể chỉ là chớp). */
    FAILED,

    /** Không chạy: bị đẩy khỏi hàng / trạng thái chiếu không còn khớp / đời xe không có `wm overscan`. */
    DROPPED,
}
