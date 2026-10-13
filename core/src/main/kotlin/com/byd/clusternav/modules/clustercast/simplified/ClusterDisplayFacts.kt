package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 3.1 · R26 — sự thật hình học của MÀN cụm trong một phiên chiếu (THUẦN, không shell) ═════════════════════════════════
 *
 * [ĐO xe Seal 13/10, `docs/diagnostics/oncar-2026-10-13/`]:
 *  - `01-read.txt`: màn cụm `fission_bg_xdjaVirtualSurface` (display 1, 1920×720) — mọi stack trên đó mang
 *    `mDisplayWindowingMode=freeform`; task chiếu là freeform.
 *  - `04`/`06`: Waze bị nhốt trong `parent=[0,0][1920,520]` (màn cụm bị ép hình học); overscan âm / `wm size 1920x760` /
 *    overscan 0,90,0,90 KHÔNG đổi `mFrame` của task freeform ⇒ freeform bỏ qua ép hình học mức display.
 *  - `07-after-reset.txt`: `wm size reset -d 1` + `wm overscan reset -d 1` ⇒ `parent=[0,0][1920,720]`, Waze `[0,60][1920,520]`,
 *    owner xác nhận bằng mắt.
 *
 * Từ đó (luật V1 `CastShell.resetDisplayGeometry` — "task tự quản khung → gỡ ép hình học, GIỮ DPI"): khi màn cụm freeform thì
 * khung là việc của TASK (`am task resize`), màn phải về kích vật lý; `wm size` lưu theo app là rác (khoá màn nhỏ lại cho mọi lần
 * chiếu sau). Tệp này chỉ ĐỌC/PHÁN — chỗ chạm shell ở [CastGeometryController.displayFacts].
 */
object ClusterDisplayFacts {

    /** Kết quả một lượt đọc cho display [displayId]: [freeform] `null` = không đọc được; [physical] `null` = đo hụt. */
    data class Facts(val displayId: Int, val freeform: Boolean?, val physical: Pair<Int, Int>?)

    private val STACK_HEADER = Regex("""^\s*Stack id=\d+ .*\bdisplayId=(\d+)""")
    private val DISPLAY_MODE = Regex("""\bmDisplayWindowingMode=([a-z_-]+)""")

    /**
     * Chế độ cửa sổ của MÀN [displayId] từ `am stack list` (dòng `configuration=… mDisplayWindowingMode=<mode>` ngay dưới
     * tiêu đề stack): `true` = freeform, `false` = chế độ khác đọc được, `null` = không có stack/dòng cấu hình nào trên màn đó
     * (chưa biết — người gọi giữ hành vi cũ). Chỉ dòng cấu hình của CHÍNH stack đang đứng mới được tính (không lấy nhầm display 0).
     */
    fun displayFreeform(stackList: String, displayId: Int): Boolean? {
        var current = -1
        for (line in stackList.lines()) {
            val header = STACK_HEADER.find(line)
            if (header != null) { current = header.groupValues[1].toIntOrNull() ?: -1; continue }
            if (current != displayId) continue
            val mode = DISPLAY_MODE.find(line)?.groupValues?.get(1) ?: continue
            return mode == "freeform"
        }
        return null
    }

    /** `"WxH"` của [physical]. */
    fun sizeString(physical: Pair<Int, Int>): String = "${physical.first}x${physical.second}"

    /**
     * Bản ghi [config] của một app THƯỜNG đọc ra để áp: màn cụm đang freeform VÀ biết kích vật lý ⇒ `wmSize` = kích vật lý
     * (bản ghi `wm size` cũ — từ đường lùi tầng 2 — là rác: task tự quản khung, màn nhỏ lại chỉ nhốt app). Còn lại: nguyên bản
     * (màn không freeform thì `wm size` là cơ chế thật của đường lùi). KHÔNG ghi lại prefs — làm sạch lúc đọc, đảo được.
     * CP/AA không bao giờ đi qua đây (`pinFull` trả sớm với hằng đã đo của chúng).
     */
    fun sanitize(config: DisplayConfig, facts: Facts?): DisplayConfig {
        val phys = facts?.physical ?: return config
        if (facts.freeform != true) return config
        val want = sizeString(phys)
        return if (config.wmSize == want) config else config.copy(wmSize = want)
    }

    /**
     * Khung (rộng × cao) cho dải −/+ của màn Cài đặt: kích VẬT LÝ của cụm khi đã đo, lùi về `wmSize` của cấu hình đang áp, lùi
     * tiếp về [DisplayConfig.NORMAL_DEFAULT]. Toạ độ khung luôn là toạ độ vật lý (cả đường lùi `wm size` cũng tính kích từ khung
     * trong 0..physical) — dùng `wmSize` cũ (vd 1920×520) làm trần là bẫy bánh cóc: không bao giờ kéo được mép dưới quá 520.
     */
    fun editFrameSize(physical: Pair<Int, Int>?, wmSize: String?): Pair<Int, Int> {
        if (physical != null && physical.first > 0 && physical.second > 0) return physical
        val parts = (wmSize ?: DisplayConfig.NORMAL_DEFAULT.wmSize).split("x")
        val w = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 } ?: 1920
        val h = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 720
        return w to h
    }
}
