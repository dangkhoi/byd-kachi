package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ WP2 · R2 — KHOÁ DÂY NỐI của "UX trạng thái control" ══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP2. [ControlVisualsTest] (`:core`) khoá **luật**; bài này khoá việc
 * tầng vẽ **thật sự hỏi** luật đó — đúng bài học đã ghi ở
 * `GroupTileTightSpaceContractTest`: *"một thuộc tính ở `:core` mà tầng vẽ không đọc là công tắc CHẾT"*
 * ([ĐO] `actionIconsDistinguish` đổi thành `get() = true` mà **0 bài đỏ**).
 *
 * Dự án không dùng Robolectric nên không dựng được `View` trong test đơn vị ⇒ bốn nhóm dưới đây quét MÃ NGUỒN, và
 * mỗi nhóm nói rõ nó chặn hiện tượng nào.
 */
class ControlStateUxContractTest {

    private fun code(rel: String): String = SourceRoots.codeOf(rel)

    private val factory by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt") }
    private val levelBar by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlLevelBar.kt") }

    private val kindBuilders = listOf(
        "private fun tileToggle(",
        "private fun tileStep(",
        "private fun tileCover(",
        "private fun tileSelect(",
        "private fun tileButton(",
    )

    // ── 1 · MỘT cửa quyết trạng thái, và cả năm kiểu đều đi qua ──────────────────────────────────

    /**
     * ⚠⚠ Bệnh bài này chặn — [ĐO đọc mã trước WP2]: `tileStep` · `tileCover` · `tileSelect` · `tileButton` đều gọi
     * `tint(icon, label, true)` (mực *"đang bật"*) ngay cạnh `applyBg(tile, false)` (nền *"đang tắt"*), tức hai nửa
     * của cùng một ô nói hai điều khác nhau — và **không bài nào** thấy, vì luật nằm rải trong năm hàm dựng view.
     */
    @Test
    fun `ca nam kieu o deu hoi core truoc khi to mau`() {
        kindBuilders.forEach { sig ->
            val fn = SourceRoots.body(factory, sig)
            assertTrue(fn.contains("look("), "$sig phải đi qua `look(` (cửa duy nhất áp trạng thái do :core quyết)")
        }
        val look = SourceRoots.body(factory, "private fun look(")
        assertTrue(look.contains("ControlVisuals.of("), "`look` phải hỏi :core, không tự suy trạng thái")
        // ĐÚNG HAI chỗ, và cả hai phải nói được vì sao: (1) `look` = ÁP trạng thái lên view; (2) đường đọc-lại của
        // COVER = HỎI trạng thái để so trước khi vẽ lại (không so thì `applyBg` dựng một Drawable mới mỗi nhịp 1 Hz).
        // Chỗ thứ ba là một luật thứ hai sẽ lệch — đúng bẫy hai-bản-sao dự án đã trả giá nhiều lần.
        assertEquals(
            2, Regex("""ControlVisuals\.of\(""").findAll(factory).count(),
            "chỉ được hai chỗ gọi ControlVisuals.of: `look` (áp) và đường đọc-lại của COVER (so sánh)",
        )
        assertTrue(
            SourceRoots.body(factory, "private fun tileCover(").contains("ControlVisuals.of("),
            "và chỗ thứ hai phải đúng là đường đọc-lại của COVER",
        )
    }

    @Test
    fun `khong con nhanh nao to muc dang-bat khi nen dang-tat`() {
        kindBuilders.forEach { sig ->
            val fn = SourceRoots.body(factory, sig)
            assertFalse(
                Regex("""tint\([^)]*,\s*true\s*\)""").containsMatchIn(fn),
                "$sig còn tô mực 'đang bật' bằng cờ CỨNG — đó chính là chỗ nền và mực nói hai điều khác nhau",
            )
            assertFalse(
                Regex("""applyBg\([^)]*,\s*(true|false)\s*\)""").containsMatchIn(fn),
                "$sig còn đặt nền bằng cờ CỨNG thay vì theo trạng thái :core quyết",
            )
        }
    }

    // ── 2 · R2.1 · icon active/mờ, và KHÔNG có chữ "Bật/Tắt" ─────────────────────────────────────

    @Test
    fun `trang thai TAT ha do duc cua ICON chu khong ha ca o`() {
        val tint = SourceRoots.body(factory, "private fun tint(")
        // 2.93 WIDGET-ICON-OFF-FAINT (đổi ghim có lý do): độ đục TẮT nay đặt qua `KachiIcons.fadeOff` — vẫn hạ độ đục của
        // ICON (tới ICON_OFF_ALPHA) nhưng giữ lớp chính ≥ 4,5:1 trên nền ô tắt (IconFade, :core — IconFadeTest).
        assertTrue(tint.contains("KachiIcons.fadeOff(icon, size.iconDp, active, ICON_OFF_ALPHA)"), "R2.1 'icon active/mờ' — trạng thái tắt phải hạ độ đục của icon")
        assertFalse(
            tint.contains("label.alpha ="),
            "NHÃN phải giữ độ đục: nó trả lời 'ô này là cái gì', câu đó không phụ thuộc bật/tắt " +
                "([ĐO] 2.33:1 của lượt kiểm toán UX khi làm mờ cả ô)",
        )
    }

    /**
     * Owner: *"bỏ chữ 'Bật/Tắt' — nhà quê"*. Ô bật/tắt và ô bấm-một-phát chỉ được có **nhãn** + icon + màu; không
     * `TextView` phụ nào để nhồi chữ trạng thái vào.
     */
    @Test
    fun `o bat-tat va o bam-mot-phat khong dung TextView phu nao de noi trang thai`() {
        listOf("private fun tileToggle(", "private fun tileButton(").forEach { sig ->
            val fn = SourceRoots.body(factory, sig)
            assertFalse(fn.contains("TextView("), "$sig không được dựng chữ trạng thái — trạng thái nói bằng icon + màu")
        }
    }

    // ── 3 · R2.2 · vạch mức thay chữ, và CHỈ cho thang mức ───────────────────────────────────────

    @Test
    fun `o nhieu muc ve VACH, va so vach hoi core`() {
        val fn = SourceRoots.body(factory, "private fun tileSelect(")
        assertTrue(fn.contains("ControlVisuals.tickCount(def)"), "số vạch do :core quyết, không đếm args ở tầng vẽ")
        assertTrue(fn.contains("ControlLevelBar.build("), "phải dựng dải vạch")
        assertTrue(fn.contains("ControlLevelBar.light("), "và sáng đúng số vạch của mức hiện tại")
        assertTrue(
            Regex("""if \(ticks > 0\)""").containsMatchIn(fn),
            "phải RẼ NHÁNH: chỉ thang mức mới đổi chữ thành vạch — tập lựa chọn (màu đèn viền · EV/HEV) giữ chữ, " +
                "vẽ 'Xanh lá' thành '3 trên 5 vạch' là nói sai",
        )
        assertTrue(fn.contains("optView?.text = v.option"), "tập lựa chọn vẫn phải hiện chữ lựa chọn")
    }

    /** Dải vạch làm mới theo nhịp 1 Hz ⇒ [ControlLevelBar.light] không được cấp phát gì. */
    @Test
    fun `sang vach chi doi do mo, khong dung lai Drawable moi nhip`() {
        val fn = SourceRoots.body(levelBar, "fun light(")
        assertTrue(fn.contains(".alpha ="), "chỉ đổi alpha")
        listOf("GradientDrawable(", "setColor(", "background =").forEach {
            assertFalse(fn.contains(it), "`light` KHÔNG được $it — nó chạy mỗi nhịp trạng thái xe (1 Hz trên xe)")
        }
        assertTrue(
            SourceRoots.body(levelBar, "fun build(").contains("GradientDrawable("),
            "nền của vạch dựng MỘT LẦN lúc dựng dải",
        )
    }

    // ── 4 · R2.3 · cốp/kính mở ⇒ màu nhấn (ô này trước WP2 không đọc lại xe) ─────────────────────

    @Test
    fun `o COVER doc lai trang thai that cua xe`() {
        val dispatch = SourceRoots.body(factory, "fun actionTile(")
        assertTrue(
            dispatch.contains("ControlKind.COVER -> tileCover("),
            "COVER phải TRẢ hàm đọc-lại; trước WP2 nhánh này trả no-op nên ô cốp trông y hệt lúc mở và lúc đóng",
        )
        val fn = SourceRoots.body(factory, "private fun tileCover(")
        assertTrue(fn.contains("car.controls[def.id]"), "phải đọc giá trị thật của nút từ nhịp poll")
        assertTrue(fn.contains("state.touchedWithin(def.id)"), "và nhường cửa sổ ân hạn sau khi người lái vừa bấm")
        assertTrue(
            Regex("""if \(v\.active != open\)""").containsMatchIn(fn),
            "chỉ vẽ lại khi trạng thái ĐỔI — `applyBg` dựng một Drawable mới mỗi lượt, mà nhịp là 1 Hz trên xe",
        )
    }

    // ── WP3-v5 · Task B · cốp/rèm bỏ chữ close/open — MỘT tile text-free ────────────────────────

    /**
     * Owner (WP3-v5): *"sao phải có chữ close/open"*. Cốp/rèm nay MỘT tile text-free: ≤2 mức = toggle active/mờ;
     * ≥3 mức = VẠCH như ghế ([ControlLevelBar]); bấm CYCLE gửi `coverLevel`. Không rẽ vùng, không nút phụ, không
     * TextView chữ trạng thái.
     */
    @Test
    fun `WP3v5 COVER la MOT tile text-free`() {
        val cover = SourceRoots.body(factory, "private fun tileCover(")
        assertFalse(cover.contains("size.narrow"), "COVER nay MỘT kiểu cho mọi vùng — không rẽ narrow/rộng")
        assertFalse(cover.contains("coverButtons(") || cover.contains("coverCycle("), "bỏ hai biến thể cũ")
        assertFalse(cover.contains("TextView("), "KHÔNG dựng chữ trạng thái (Đóng/Mở/Nửa) — nói bằng màu + vạch")
        assertFalse(factory.contains("miniBtn("), "nút phụ có nhãn đã bỏ hẳn")
        assertTrue(
            cover.contains("ControlLevelBar.build(") && cover.contains("ControlLevelBar.light("),
            "nhiều mức ⇒ VẠCH (như ghế), không chữ",
        )
        assertTrue(
            cover.contains("ControlTileLogic.nextSelectIndex(") && cover.contains("control().coverLevel("),
            "bấm CYCLE qua các mức, gửi coverLevel(id, mức)",
        )
    }

    // ── 5 · R2.4 · stepper cân đối giống nhau ────────────────────────────────────────────────────

    @Test
    fun `o gia tri cua stepper co SAN be ngang lay tu registry`() {
        val fn = SourceRoots.body(factory, "private fun tileStep(")
        assertTrue(
            fn.contains("ControlVisuals.STEP_VALUE_CHARS"),
            "SÀN bề ngang ô giá trị phải suy từ registry — đó là thứ làm '4' và '22°' chiếm cùng một ô (R2.4)",
        )
        assertTrue(fn.contains("minWidth ="), "và phải áp bằng minWidth trên chính ô giá trị")
        assertTrue(
            fn.contains("SIDE_WEIGHT") && fn.contains("VALUE_WEIGHT"),
            "ba cột phải theo TỈ LỆ cố định; với 'giá trị WRAP' như trước WP2 thì hai nút −/+ trôi theo độ dài số",
        )
        assertEquals(
            2, Regex("""SIDE_WEIGHT""").findAll(fn).count(),
            "hai nút −/+ phải dùng CÙNG một tỉ lệ ⇒ đối xứng; lệch nhau là lệch tâm ô giá trị",
        )
    }

    /**
     * Đơn vị của ô giá trị phải là **dữ liệu** (đơn vị của datum nút đọc), không phải một nhánh theo mã.
     * CLAUDE.md §7 — và đây là ca cụ thể: `if (def.id == "temp") "°" else ""` từng nằm ở tầng vẽ.
     */
    @Test
    fun `don vi o gia tri khong con la nhanh re theo ma nut`() {
        assertFalse(
            Regex("""def\.id\s*==\s*"temp"""").containsMatchIn(factory),
            "đơn vị phải lấy từ ControlVisuals.stepUnit (đọc TelemetryRegistry), không rẽ nhánh theo mã",
        )
        val fn = SourceRoots.body(factory, "private fun tileStep(")
        assertTrue(fn.contains("ControlVisuals.stepText(def"), "chữ ô giá trị dựng ở :core (số đã kẹp + đơn vị)")
        assertEquals(
            0, Regex("""[$]unit""").findAll(fn).count(),
            "không còn ghép đơn vị bằng tay ở tầng vẽ",
        )
    }

    /**
     * ═══ UX4 — nấc đáy của một nút có [ControlDef.autoId] là **AUTO**, và ô KHÔNG tự quyết lấy ════════════════
     *
     * Ba dây, mỗi dây chặn một cách hỏng đã thấy trước:
     *  1. luật nằm ở `:core` ([ClimateAuto]) — ô chỉ THI HÀNH; viết lại bảng quyết định ở tầng vẽ là bản sao thứ hai
     *     mà không bài chạy-thật nào với tới được;
     *  2. sàn bề ngang CHUNG **không** được nới cho chữ AUTO (xem `ControlVisualsTest`) — thay vào đó ô CO chữ;
     *  3. đường đọc-lại phải lấy cả cờ auto, nếu không ô mãi mãi không biết xe đang ở chế độ nào ⇒ tính năng câm
     *     (CLAUDE.md §8: compile xanh ≠ chạy đúng).
     */
    @Test
    fun `nac AUTO cua stepper do core quyet, khong noi san chung, va co doc lai co auto`() {
        val fn = SourceRoots.body(factory, "private fun tileStep(")
        assertTrue(fn.contains("ClimateAuto.stepPlan(def,"), "cú bấm phải hỏi `:core` (bảng quyết định ở ClimateAuto)")
        assertTrue(
            fn.contains("ClimateAuto.autoOnFromControl(car.controls[def.autoId])"),
            "đường đọc-lại phải lấy cờ auto qua cửa của NÚT (đã applyInverted) — dùng cửa của datum là đảo hai lần",
        )
        assertFalse(
            fn.contains("ClimateAuto.autoOnFromRaw("),
            "`autoOnFromRaw` là cửa của DATUM (số thô 0 = AUTO); dùng nó ở đây thì ô hiện AUTO đúng lúc xe chỉnh tay",
        )
        assertTrue(
            fn.contains("minWidth = ceil(paint.measureText(DIGIT.repeat(ControlVisuals.STEP_VALUE_CHARS))"),
            "sàn bề ngang vẫn là sàn CHUNG suy từ registry — không được nới vì một chữ AUTO của một nút",
        )
        assertTrue(
            fn.contains("StepValueFit.apply(vtext, def, size.valueSp)"),
            "chữ dài hơn sàn thì CO lại; `maxLines = 1` không ellipsize nên không co là cắt CỨNG",
        )
    }

    /**
     * ═══ [P2 · SOÁT Opus 2026-09-27] Chữ ô giá trị co rồi phải **NỞ LẠI** — chiều cao phải được GHIM ═══════════
     *
     * [ĐO máy ảo · QA lượt 2, `docs/diagnostics/offcar-2026-09-26/visual-pass-2026-09-27.md` §1b]: ô gió mức 1 có mực
     * cao **15 px** (ô 47×29); đi qua "AUTO" rồi bấm `+` ⇒ "2" chỉ còn **10 px** (ô 47×**19**) và **không bao giờ to
     * lại**. Con số người lái đọc trong lúc lái nhỏ đi một phần ba, vĩnh viễn, sau đúng một lượt AUTO.
     *
     * Vì `autoSizeText` chọn cỡ theo **khoảng trống đo được**, mà ô có `WRAP_CONTENT` bề dọc ⇒ khoảng trống đi theo
     * cỡ chữ đang vẽ ⇒ khoá cứng ở sàn. Và không sửa lại được bằng `setTextSize` ([ĐO] AOSP `TextView.setTextSize`
     * mở đầu bằng `if (!isAutoSizeEnabled())` ⇒ no-op). Nên bất biến phải ghim ở **chiều cao**.
     *
     * Bài này đọc mã (`SharedPreferences`/`TextView` không chạy trên JVM; phép đo hành vi là ảnh máy ảo ở doc trên).
     * Ba vế: chiều cao lấy từ `fontMetricsInt` của **chính** `paint` (⇒ tự đúng ở cả ba vùng `TileSize`, không hằng
     * dp), autosize vẫn bật, và phép ghim **chỉ** chạy cho nút vượt sàn chung ⇒ ô nhiệt độ/âm lượng không đổi một
     * pixel nào (bảo đảm R2.4 *"hai nút −/+ đứng đúng một chỗ"*).
     *
     * ## ⚠ Vế thứ tư, thêm sau QA lượt 3 — ghim ở ĐÂU, không chỉ "có ghim hay không"
     * Bản vá đầu ghim bằng `v.height = …` (`TextView.setHeight`) và **bài này vẫn xanh** trong khi màn hình đã sai:
     * [ĐO máy ảo · QA lượt 3, cùng tài liệu §2c.3] chữ `AUTO` hiện thành **`AUT` / `O` hai dòng**, mép dưới chạm hàng
     * pixel cuối. Nguyên nhân — [ĐO] AOSP `android-10.0.0_r47` `core/java/android/widget/TextView.java`: `setMaxLines`
     * (`:5336-5339`) và `setHeight` (`:5438-5441`) ghi **CÙNG** cặp trường `mMaximum`/`mMaxMode`, nên phép ghim **xoá**
     * `maxLines = 1` ⇒ `getMaxLines()` = −1 (`:5357-5359`) ⇒ chốt "quá số dòng" của autosize bị bỏ qua (`:9530`) ⇒
     * autosize nhận cỡ to hơn kèm **xuống dòng**. `setMaxHeight` (`:5376-5378`) và `setMinHeight` (`:5297-5299`) cũng
     * ghi vào cặp trường ấy ⇒ **cả ba đều bị cấm ở đây**. Đường đúng: ghim px qua `LayoutParams` ở chỗ `addView` —
     * `getChildMeasureSpec` gặp `lp.height >= 0` ⇒ đo con EXACTLY ([ĐO] `LinearLayout.java:1380-1383`), khoảng trống
     * bị ghim y như vậy mà `mMaxMode` giữ nguyên `LINES`.
     *
     * Ba dây dưới đây **chặn đúng mutation đã xảy ra thật**: (a) không đường `setHeight`/`setMinHeight`/`setMaxHeight`
     * nào quay lại `StepValueFit`/`tileStep`; (b) phép ghim phải đi qua `LayoutParams` của ô giá trị; (c) con số px
     * vẫn đo bằng `paint` và vẫn đo **trước** khi bật autosize. Bài trên là hợp đồng — nó **không** thay được phép đo
     * ảnh (§2c/§2d của tài liệu QA), nhưng nay nó ít nhất đỏ khi ai đó ghim sai chỗ.
     *
     * ## ⚠ Vế thứ năm — lề trong phải NHƯỜNG một nửa, nếu không chữ `AUTO` bị cắt còn `AUT`
     * [ĐO máy ảo · QA lượt 4 §2d.3] với lề trong đầy đủ ([KachiSpace.XS] mỗi bên = 6px ở 240dpi) ô 47px chỉ còn **35px**
     * cho chữ, mà `"AUTO"` ở **sàn** [StepValueFit.MIN_SP] vẫn cần ~37px ⇒ autosize hết cỡ vẫn không vừa một dòng ⇒
     * `StaticLayout` ngắt dòng, `TextView` cao đúng một dòng nên dòng hai **bị cắt khỏi vùng vẽ** ⇒ người lái đọc ra
     * `AUT`. Đây là lỗi **có từ UX4** (ảnh lượt 2 `43-fan-auto.png` cũng `AUT`), không phải do phép ghim chiều cao.
     * Nhường một nửa lề (3px mỗi bên) ⇒ 41px cho chữ ⇒ đủ cho `AUTO` nguyên chữ, và ô vẫn **47×31** y như cũ vì bề
     * ngang ô do `weight` quyết, không do lề trong ([ĐO] cả 5 trạng thái ở §2d.1).
     */
    @Test
    fun `o gia tri co chu roi phai no lai duoc - chieu cao ghim`() {
        val fit = code("src/main/java/com/byd/clusternav/launcher/StepValueFit.kt")
        val fn = SourceRoots.body(fit, "fun apply(")
        assertTrue(
            fn.contains("if (!needsFit(def)) return ViewGroup.LayoutParams.WRAP_CONTENT"),
            "nút KHÔNG vượt sàn chung phải nhận `WRAP` y như trước 2.74 (0 pixel đổi ở ô nhiệt độ/âm lượng)",
        )
        assertTrue(
            SourceRoots.body(fit, "fun needsFit(")
                .contains("ControlVisuals.stepValueChars(def) > ControlVisuals.STEP_VALUE_CHARS"),
            "điều kiện co là dữ liệu của chính nút đó (registry), không phải danh sách mã nút viết tay",
        )
        assertTrue(
            SourceRoots.body(fit, "fun cellHeightPx(").contains("paint.fontMetricsInt"),
            "chiều cao phải ĐO bằng paint của view, không gõ hằng dp",
        )
        assertTrue(fn.contains("cellHeightPx(v.paint)"), "`apply` phải lấy số px từ chính phép tính thuần ấy")
        assertTrue(
            fn.contains("val sidePad = v.paddingLeft / 2") &&
                fn.contains("v.setPadding(sidePad, v.paddingTop, sidePad, v.paddingBottom)"),
            "nút phải co được nhường MỘT NỬA lề trong hai bên — đủ lề đầy thì `AUTO` ở sàn 9sp vẫn quá 35px ⇒ " +
                "ngắt dòng ⇒ dòng hai bị cắt khỏi vùng vẽ ⇒ màn hình đọc ra `AUT` (ĐO máy ảo lượt 4 §2d.3)",
        )
        assertTrue(
            fn.indexOf("setPadding(") < fn.indexOf("cellHeightPx(v.paint)"),
            "đổi lề TRƯỚC khi đo `paint`/bật autosize — đổi sau là autosize đã chọn cỡ theo khoảng trống cũ",
        )
        assertTrue(
            fn.contains("setAutoSizeTextTypeUniformWithConfiguration(MIN_SP, maxSp.toInt(), 1"),
            "autosize vẫn phải bật (không co thì `maxLines = 1` cắt CỨNG chữ AUTO)",
        )
        assertTrue(
            fn.indexOf("cellHeightPx(v.paint)") < fn.indexOf("setAutoSizeTextTypeUniform"),
            "đo `paint` TRƯỚC khi bật autosize — đo sau là đo cỡ chữ autosize đã chọn, không phải cỡ gốc",
        )
        // (a) Ba cửa `TextView` ghi vào `mMaximum`/`mMaxMode` đều bị CẤM ở cả hai vùng — chúng xoá `maxLines = 1`.
        val step = SourceRoots.body(factory, "private fun tileStep(")
        listOf(
            Regex("""\.height\s*=""") to "gán `.height` của TextView",
            Regex("""setHeight\(""") to "setHeight(",
            Regex("""(?i)minHeight""") to "minHeight",
            Regex("""(?i)maxHeight""") to "maxHeight",
        ).forEach { (re, what) ->
            assertFalse(
                re.containsMatchIn(fit) || re.containsMatchIn(step),
                "CẤM $what trong StepValueFit/tileStep — nó ghi vào mMaximum/mMaxMode và XOÁ `maxLines = 1` " +
                    "(AOSP TextView:5438/5376/5297) ⇒ chữ AUTO xuống 2 dòng (ĐO máy ảo lượt 3 §2c.3)",
            )
        }
        // (b) …và phép ghim phải là chiều cao của `LayoutParams` ở chỗ `addView` ô giá trị.
        assertTrue(
            step.contains("val valueH = StepValueFit.apply(vtext, def, size.valueSp)"),
            "số px phải về tay chỗ gọi (không ghi thẳng vào view) để đem vào LayoutParams",
        )
        assertTrue(
            step.contains("addView(vtext, LinearLayout.LayoutParams(0, valueH, VALUE_WEIGHT))"),
            "ghim chiều cao NGOÀI view: `lp.height >= 0` ⇒ LinearLayout đo con EXACTLY mà `mMaxMode` giữ LINES",
        )
    }

    /** Ô stepper vẫn phải giữ nguyên hai bảo đảm đã ĐO trước đó (R7 · đích chạm · không phóng to glyph). */
    @Test
    fun `noi vung cham va co glyph cua stepper khong bi luot WP2 lam mat`() {
        val fn = SourceRoots.body(factory, "private fun tileStep(")
        assertTrue(fn.contains("StepTouchTarget.attach(tile, minus, plus)"), "vùng chạm −/+ vẫn phải được nới")
        val btn = SourceRoots.body(factory, "private fun stepBtn(")
        assertFalse(btn.contains("minWidth ="), "KHÔNG đặt minWidth cho NÚT — [ĐO] 2×36dp > 68dp dùng được của ô")
        assertTrue(btn.contains("size.valueSp"), "glyph −/+ giữ cỡ theo vùng, không phóng to")
    }

    // ── 6 · Trần 500 dòng cho mọi tệp lượt WP2 chạm tới (CLAUDE.md §4.1) ────────────────────────

    @Test
    fun `moi tep cua luot WP2 duoi tran 500 dong`() {
        listOf(
            "src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt",
            // UX4 (2026-09-26): vai "ô hành động của CHÍNH launcher" tách ra đây để tệp trên còn chỗ cho nấc AUTO.
            "src/main/java/com/byd/clusternav/launcher/LauncherTile.kt",
            // [SOÁT Opus 2026-09-27]: vai *"ô một dòng tự chọn cỡ chữ mà vẫn giữ đúng một hộp"* tách ra đây — tệp
            // trên đã 495/500 và lời giải thích của phép ghim chiều cao dài hơn chính phép ghim.
            "src/main/java/com/byd/clusternav/launcher/StepValueFit.kt",
            "src/main/java/com/byd/clusternav/launcher/ControlLevelBar.kt",
            "src/main/java/com/byd/clusternav/launcher/TileSize.kt",
            "src/main/java/com/byd/clusternav/launcher/ReadTile.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiSpace.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/ControlVisuals.kt",
            // UX5b (2026-09-27): chip ghế thứ hai đẩy `TopStrip.kt` tới trần ⇒ vai *"dựng chữ + hình của một chip"*
            // tách sang `TopStripChips.kt`. Ghim CẢ HAI ở đây để lượt sau không lặng lẽ nhồi lại vào một tệp.
            "src/main/kotlin/com/byd/clusternav/launcher/TopStrip.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/TopStripChips.kt",
        ).forEach { rel ->
            val n = SourceRoots.text(rel).lines().size
            assertTrue(n <= 500, "$rel dài $n dòng — trần là 500 (CLAUDE.md §4.1)")
        }
    }
}
