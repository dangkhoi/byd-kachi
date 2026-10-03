package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import com.byd.clusternav.testsupport.SwapDiscModel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · R-ES — DÂY NỐI của ô trống trong suốt (spec `docs/specs/kachi-286-field-fixes.html` §3.4) ═══════════
 *
 * Phần THUẦN đã có bài riêng: `WorkspaceDefaultTest` (`:core` — khi nào nạp mặc định) và
 * `ThemePaletteContractTest.o trong nhan ra duoc…` (tương phản ⇄ trên đĩa kính, mô hình `SwapDiscModel`). Bài này khoá
 * phần mà bài thuần KHÔNG thấy được: hàm thuần có thật sự được GỌI ở đúng chỗ không (CLAUDE.md §8 — `CastShell.evictVd`
 * viết cẩn thận mà 0 call site). Mỗi khẳng định ứng với một mắt xích đã thử phá.
 */
class EmptySlotFix286WiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    /** ES5 — `load()` đi qua luật "chưa từng lưu", không còn luật "mọi ô trống". */
    @Test
    fun `load nap mac dinh chi khi ho so chua tung luu`() {
        val src = code("PrefsWorkspaceRepository.kt")
        val load = SourceRoots.body(src, "override fun load()")
        assertTrue("workspace = defaultIfNeverSaved(" in load, "load() phải đi qua defaultIfNeverSaved")
        assertFalse("defaultIfEmpty" in src, "luật cũ 'mọi ô trống ⇒ mặc định' phải gỡ hẳn (owner 03/10: toàn ô trống giữ trống)")
        val helper = SourceRoots.body(src, "private fun defaultIfNeverSaved(")
        assertTrue(
            "WorkspaceDefault.resolve(ws, everSaved = hasStoredSlots())" in helper,
            "quyết định ở :core (WorkspaceDefault), dấu 'đã lưu' đọc từ đĩa qua hasStoredSlots",
        )
        assertTrue(
            Regex("""if \(out !== ws\) \{\s*android\.util\.Log\.i\("KachiWorkspace", "\[ws-default\]""").containsMatchIn(helper),
            "nhánh nạp mặc định phải nói ra [ws-default] — và CHỈ nhánh đó",
        )
        val slots = SourceRoots.body(src, "private fun hasStoredSlots()")
        assertTrue(
            "WorkspaceState.SLOT_CAP" in slots && "prefs.sp.contains(prefs.key(\"slot_\$it\"))" in slots,
            "dấu 'đã lưu' = có khoá slot_* của hồ sơ đang dùng (đủ trần ô), hỏi CÓ KHOÁ chứ không hỏi giá trị",
        )
    }

    /**
     * OQ8 phương án B (chốt 2026-10-03 — phiên điều phối, owner có thể đổi) — ⇄ của ô trống nằm trên ĐĨA KÍNH.
     *
     * Bản A (ES2: không nền, tô màu theo độ chói đo dưới nút — `SwapTint`/`SwapTintBinding`) [ĐO máy ảo 03/10] không đạt
     * 3:1 trên ảnh nhiều chi tiết ⇒ gỡ cả bộ (bản khôi phục: `docs/diagnostics/fix286-oq8-disc-emulator-2026-10-03/`).
     * Mỗi khẳng định là một mắt xích mà mô hình tương phản giả định: đĩa đi qua ĐÚNG cửa kính ở tone NEUTRAL (hợp đồng
     * lớp che có `MUT`), nằm SAU icon, cỡ suy ra từ thang và lọt khung chạm; icon không bị tô lại; lớp nhuộm mà mô hình
     * mô phỏng vẫn đúng 0x0f; và không còn mảnh nào của A sống dở (§8).
     */
    @Test
    fun `nut doi app o trong nam tren dia kinh`() {
        val centered = SourceRoots.body(code("SlotSwapButton.kt"), "fun centered(")
        // ⚠ 2.87 · R-OP (2026-10-03) — ghim đổi từ `…SurfaceTone.NEUTRAL)` sang `…SurfaceTone.NEUTRAL, fade = false)`:
        // đĩa là NÚT, không phải nền; 4.86:1 của SwapDiscModel đo ở 100 %. Độ đục nền chung (KachiChrome) làm mờ mọi
        // kính NEUTRAL — thiếu `fade = false` thì đĩa mờ theo và hợp đồng tương phản của nó mất mà bài này vẫn xanh.
        assertTrue("KachiGlass.apply(d, Sp.SWAP_DISC / 2, SurfaceTone.NEUTRAL, fade = false)" in centered, "đĩa = kính NEUTRAL, tròn, KHÔNG mờ theo R-OP")
        val disc = centered.indexOf("addView(d, FrameLayout.LayoutParams(KachiTheme.dpi(context, Sp.SWAP_DISC), KachiTheme.dpi(context, Sp.SWAP_DISC), Gravity.CENTER))")
        val icon = centered.indexOf("visual,")
        assertTrue(disc in 0 until icon, "đĩa phải thêm TRƯỚC icon (nằm sau icon), canh giữa khung chạm")
        assertTrue(Regex("""const val SWAP_DISC = S \+ ICON_S \+ S\b""").containsMatchIn(SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiSpace.kt")))
        assertTrue(KachiSpace.SWAP_DISC <= KachiSpace.SLOT_HEAD_CLEAR && KachiSpace.SWAP_DISC <= KachiSpace.TOUCH, "đĩa lọt khung chạm")
        assertTrue(
            "const val TINT_ALPHA = 0x${"%02x".format(SwapDiscModel.TINT_ALPHA)}" in code("WallGlass.kt"),
            "lớp nhuộm mà SwapDiscModel mô phỏng phải khớp WallWindowDrawable",
        )
        val launcher = SourceRoots.path("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt").parent
        val left = Files.walk(launcher).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
            .filter { f -> listOf("SwapTint", "luminanceRange", "kachi_swap_tint").any { it in f.toFile().readText() } }
        assertEquals(emptyList<Path>(), left, "mảnh phương án A còn sống dở")
        assertFalse("kachi_swap_tint" in SourceRoots.text("src/main/res/values/ids.xml"), "id tag của A phải gỡ")
    }

    /** ES6 — dòng `[slot-empty]` ở mọi lượt dựng/đổi cấu trúc ô (không mỗi ô). */
    @Test
    fun `nhat ky o trong o moi luot dung lai`() {
        val src = code("WorkspaceView.kt")
        val render = SourceRoots.body(src, "private fun renderInternal(")
        assertTrue(
            "WorkspaceRenderPlan.RebuildAll -> { rebuild(); EmptySlotLog.note(displayed.slots, slotViews.size); return }" in render,
            "dựng lại tất cả theo state thật phải ghi [slot-empty]",
        )
        // Lượt render ĐẦU của bố cục toàn ô trống KHÔNG đổi ô nào so với state rỗng của `init` (không cấu trúc nào đổi) —
        // chỉ ghi khi "có thay đổi cấu trúc" là mất đúng dòng cần cho ca owner [ĐO máy ảo 03/10]. Ghi mọi lượt, khử trùng.
        assertTrue(
            Regex("""if \(structural\) \{ requestLayout\(\); invalidate\(\) \}\s*EmptySlotLog\.note\(displayed\.slots, slotViews\.size\)""")
                .containsMatchIn(render),
            "mọi lượt render (cả lượt không đổi cấu trúc) phải qua EmptySlotLog — khử trùng ở trong",
        )
        assertTrue(
            "EmptySlotLog.note(displayed.slots, slotViews.size)" in SourceRoots.body(src, "fun restyle()"),
            "đổi chủ đề (restyle) phải ghi [slot-empty]",
        )
        // [ĐO máy ảo 03/10] `init { rebuild() }` chạy với state RỖNG trước lượt render đầu ⇒ ghi ở `rebuild()` là một dòng
        // "3 ô trống" giả mỗi lần tiến trình bật. Cũng không ghi mỗi Ô (restyle dựng lại liên tục).
        assertFalse("EmptySlotLog" in SourceRoots.body(src, "private fun rebuild()"), "không ghi ở rebuild() trần (init, state rỗng)")
        assertFalse("EmptySlotLog" in SourceRoots.body(src, "private fun makeSlot("), "không ghi mỗi Ô")
        val note = SourceRoots.body(code("WorkspaceViewCards.kt"), "fun note(")
        assertTrue("[slot-empty]" in note, "đúng nhãn để grep usage log")
        assertTrue("trong suốt · ⇄ trên đĩa kính" in note, "nhật ký nói đúng phương án đang chạy (OQ8 · B) — ảnh chụp từ xe")
    }

    /** ES7 — vai/hàm/chuỗi của ô trống đục cũ không còn ai dùng ⇒ đã gỡ, không mọc lại. */
    @Test
    fun `vai mau va chuoi cua o trong duc cu da go`() {
        val root = SourceRoots.path("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt").parent
        val hits = Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }.flatMap { f ->
            val c = SourceRoots.codeOf(root.relativize(f).toString().let { "src/main/java/com/byd/clusternav/launcher/$it" })
            listOf("emptyFill", "EMPTY_FILL", "emptyAdd(", "kachi_slot_open_app").filter { it in c }.map { "${f.fileName}: $it" }
        }
        assertEquals(emptyList<String>(), hits, "ô trống đục cũ mọc lại: $hits")
        listOf("values/strings_kachi.xml", "values-en/strings_kachi.xml").forEach { rel ->
            val xml = SourceRoots.text("src/main/res/$rel")
            assertFalse("kachi_slot_open_app" in xml, "$rel còn chuỗi '＋ Mở ứng dụng' thừa")
            listOf("kachi_slot_swap_empty", "kachi_slot_swap").forEach { k ->
                assertTrue(Regex("""<string name="$k">[^<]*%1\${'$'}d[^<]*</string>""").containsMatchIn(xml), "$rel thiếu $k (có %1\$d)")
            }
        }
    }
}
