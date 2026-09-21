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
        assertTrue(tint.contains("icon.alpha ="), "R2.1 'icon active/mờ' — trạng thái tắt phải hạ độ đục của icon")
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
            "src/main/java/com/byd/clusternav/launcher/ControlLevelBar.kt",
            "src/main/java/com/byd/clusternav/launcher/TileSize.kt",
            "src/main/java/com/byd/clusternav/launcher/ReadTile.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiSpace.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/ControlVisuals.kt",
        ).forEach { rel ->
            val n = SourceRoots.text(rel).lines().size
            assertTrue(n <= 500, "$rel dài $n dòng — trần là 500 (CLAUDE.md §4.1)")
        }
    }
}
