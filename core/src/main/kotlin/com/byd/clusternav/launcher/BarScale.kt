package com.byd.clusternav.launcher

import kotlin.math.roundToInt

/**
 * ═══ 2.89 · B3 DOCK-SCALE — CỠ THANH NÚT XE theo %, phần SỐ HỌC THUẦN (`kachi-289-field-fixes` §B3) ═══════════════════
 *
 * Owner 05/10: *"cái taskbar hơi to, cho chỉnh size luôn trong hiển thị nhé, theo % … size chỉnh xong thì phải scale các
 * thứ như widget, short cut app icon này kia theo cho đẹp"* rồi *"50-150% đi"*. MỘT con số theo hồ sơ
 * ([DockConfig.scalePct], hậu tố `dock_scale`). Tệp này là phần không-Android: giá trị nào hợp lệ, vị trí thanh kéo ↔ %,
 * mật độ (dpi) mà cây view của thanh dựng bằng, và luật đích chạm theo trục thanh. Chỗ áp lên `Context` là
 * `DockScaleContext` (`:app`).
 *
 * ## Hai luật owner chốt (khác bản thiết kế 80–140 % có sàn chữ 90 %)
 *  1. **Hình đúng số kéo** — *"kéo bao nhiêu hiển thị bấy nhiêu"*: ô, icon, chữ, lề, bo góc, bề dày thanh nhân ĐÚNG
 *     `pct/100` (một mật độ ghi đè cho cả cây view ⇒ chữ `sp` theo cùng hệ số vì `scaledDensity = density × fontScale`
 *     [ĐO AOSP r47 `ResourcesImpl.java:435-443`]). Không sàn chữ, không sàn ô.
 *  2. **Đích chạm không co** — mỗi ô bấm ≥ 48 dp THẬT dọc theo trục thanh nhờ NỚI VÙNG CHẠM ([cellAlongPx]), không nới
 *     hình. Ngang trục thanh, vùng chạm = trọn bề dày thanh (ở 50 % thanh ngang dày 93 × 0,5 = 46,5 dp — số thật, ghi rõ ở
 *     spec §B3, không thể hơn khi chính thanh mỏng như vậy).
 *
 * [DEFAULT] = 100 = hôm nay TỪNG PIXEL: mọi chỗ gặp nó đi đường mã cũ ([isIdentity], [scaledDensity] trả lại chính số gốc).
 */
object BarScale {

    /** 100 % = đúng hôm nay; khoá lưu bị XOÁ ở mức này ([encode] = `null`). */
    const val DEFAULT = 100

    /** Owner 05/10 *"50-150% đi"*. */
    const val MIN = 50
    const val MAX = 150

    /** Bước thanh kéo, cùng nhịp `ChromeOpacity.STEP`. Ở 240 dpi mỗi bước = 12 dpi ⇒ mật độ luôn là số nguyên đúng. */
    const val STEP = 5

    /** Vị trí cuối của thanh kéo (0 = [MIN] … [POSITIONS] = [MAX]) — 21 vị trí. */
    const val POSITIONS = (MAX - MIN) / STEP

    /** `DisplayMetrics.DENSITY_DEFAULT` — chép số (không import Android ở `:core`). */
    private const val DENSITY_DEFAULT = 160

    /** `DisplayMetrics.DENSITY_DEFAULT_SCALE` = `1.0f / DENSITY_DEFAULT` — CÙNG phép float để ra cùng bit. */
    private const val DENSITY_DEFAULT_SCALE = 1.0f / DENSITY_DEFAULT

    /** Bội của [STEP] gần nhất, kẹp `[MIN, MAX]` (rác/ngoài dải không bao giờ ra số lạ). Không cấp phát. */
    fun snap(pct: Int): Int = (pct.coerceIn(MIN, MAX) - MIN + STEP / 2) / STEP * STEP + MIN

    /** Vị trí thanh kéo (0..[POSITIONS]) của [pct]. */
    fun position(pct: Int): Int = (snap(pct) - MIN) / STEP

    /** % của vị trí [pos] (kẹp 0..[POSITIONS]) — nghịch đảo của [position]. */
    fun ofPosition(pos: Int): Int = MIN + pos.coerceIn(0, POSITIONS) * STEP

    /** `true` ⇔ đường mã cũ (100 %). */
    fun isIdentity(pct: Int): Boolean = snap(pct) == DEFAULT

    /**
     * Mật độ (dpi) mà cây view của thanh dựng bằng: [DEFAULT] ⇒ đúng [baseDpi]; khác ⇒ `round(baseDpi × pct / 100)`,
     * không dưới 1. Ở 240 dpi: 50 % ⇒ 120 · 85 % ⇒ 204 · 150 % ⇒ 360 (đúng, không làm tròn).
     */
    fun scaledDpi(baseDpi: Int, pct: Int): Int =
        if (isIdentity(pct)) baseDpi else ((baseDpi.toLong() * snap(pct) + 50) / 100).toInt().coerceAtLeast(1)

    /**
     * `density` (float) tương ứng — ĐÚNG phép `ResourcesImpl` dùng cho `Configuration.densityDpi` ghi đè
     * (`densityDpi × DENSITY_DEFAULT_SCALE`, [ĐO AOSP r47 `ResourcesImpl.java:435-439`]) ⇒ bề dày thanh do
     * `DockAreaLayout` tính và ô do `Context` ghi đè dựng ra CÙNG px. [DEFAULT] ⇒ trả CHÍNH [baseDensity] (không tính lại).
     */
    fun scaledDensity(baseDensity: Float, pct: Int): Float =
        if (isIdentity(pct)) baseDensity
        else scaledDpi((baseDensity * DENSITY_DEFAULT).roundToInt(), pct) * DENSITY_DEFAULT_SCALE

    /**
     * Bề DÀI theo trục thanh (px) của khung chạm một ô ở cỡ ≠ 100 %.
     *
     * = `max(hình + 2 × lề, sàn)`, với `sàn = min(targets × touchPx, len100Px)`:
     *  • [visualPx] + 2 × [marginPx]: khung ôm đúng hình đã co/giãn (≥ ~61 % thì sàn không kích hoạt ⇒ thanh co đều);
     *  • [touchPx] = 48 dp THẬT; [targets] = số đích chạm XẾP DỌC THEO TRỤC trong ô (ô STEP ở thanh ngang = 2: − và +);
     *  • [len100Px] = bề dài hình ô ở 100 % — trần của sàn: ô STEP ngang ở 100 % chỉ có 35,5 dp mỗi nửa (giới hạn đã ghi
     *    ở `StepTouchTarget` từ WP5), nên ở cỡ nhỏ hơn nó GIỮ đúng 35,5 dp chứ không đòi 48 — đòi 48 nghĩa là ô ở 95 %
     *    dài hơn ở 100 % (96 > 79 dp), vô lý. Ô một-đích (71/60 dp ở 100 %) ⇒ sàn đủ 48 dp.
     */
    fun cellAlongPx(visualPx: Int, marginPx: Int, touchPx: Int, targets: Int, len100Px: Int): Int =
        maxOf(visualPx + 2 * marginPx, minOf(targets.coerceAtLeast(1) * touchPx, len100Px))

    /**
     * Toạ độ chạm trong khung ô quy về hình ô ([size] px) — kẹp vào `[0, size − 1]`: chạm vào phần khung NỚI ra ⇒ coi như
     * chạm mép hình gần nhất (ô STEP: mép trái ⇒ −, mép phải ⇒ +, vì hình nằm giữa khung).
     */
    fun clampInto(pos: Float, size: Int): Float = pos.coerceIn(0f, (size - 1).coerceAtLeast(0).toFloat())

    /** Mã hoá lưu bền: [DEFAULT] ⇒ `null` (= xoá khoá, tệp prefs của người chưa chạm thanh kéo giữ nguyên từng byte). */
    fun encode(pct: Int): String? = if (isIdentity(pct)) null else snap(pct).toString()

    /** Giải mã: vắng/rác ⇒ [DEFAULT]; số ngoài dải ⇒ kẹp ([snap]). Không bao giờ ném (tệp nhập ngoài). */
    fun decode(raw: String?): Int = raw?.trim()?.toIntOrNull()?.let(::snap) ?: DEFAULT
}
