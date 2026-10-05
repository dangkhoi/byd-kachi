package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.89 · A3–A5 (spec `docs/specs/kachi-289-field-fixes.html`) — DÂY NỐI ════════════════════════════════════════════════
 *
 * Phần thuần ở `:core` (`SlotCloseTest` · `SlotOpenTest` · `SlotFrameShapeTest`). `:app` không có Robolectric ⇒ bài này canh MÃ
 * (đã bỏ chú thích) — mỗi khẳng định là một mắt xích bài thuần không thấy, và mọi hàm mới có chỗ gọi production (CLAUDE.md §8):
 *  - A3 SLOT-CLOSE-SETTLE: [ĐO xe 05/10] lệnh gỡ chậm hơn cửa sổ đọc lại ⇒ khung đứng + *"chưa tắt được"* nhầm;
 *  - A4 SLOT-PLACE-KEEPS-MUSIC: [ĐO máy ảo QA 2.89] đặt app đang phát vào ô ⇒ `am force-stop` tắt nhạc;
 *  - A5(a) nhạc lên xe sang Hệ thống › Khởi động · A5(b) SLOT-CORNER-ROUND [ĐO ảnh xe 05/10] góc ô vuông.
 */
class Kachi289SlotFixesWiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    private val host by lazy { code("VdAppHost.kt") }
    private val actions by lazy { code("KachiHomeSlotActions.kt") }
    private val run by lazy { code("SlotReturnRun.kt") }
    private val workspace by lazy { code("WorkspaceView.kt") }

    private fun order(src: String, vararg parts: String) {
        var at = -1
        parts.forEach { p ->
            val i = src.indexOf(p, at + 1)
            assertTrue(i > at, "thứ tự sai / thiếu '$p' trong: ${src.take(900)}")
            at = i
        }
    }

    // ── A3 ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `A3 - lenh da gui thi giau mat ve ngay, het lich ma con thi hien lai + cau bao`() {
        val fn = SourceRoots.body(actions, "private fun closeApp(")
        order(fn, "if (host == null || stage == null || stage.pkg != pkg || sh == null)", "busy += index",
            "closer.run(sh, stage.vd, pkg) { main.post { host.closing(pkg, on = true) } }",
            "if (r.slotFree) revert(index, Event.APP_CLOSED, pkg)",
            "else { host.closing(pkg, on = false); sayIfStill(index, R.string.kachi_slot_close_failed, pkg) }")
        val closing = SourceRoots.body(host, "fun closing(expect: String, on: Boolean)")
        assertTrue("surface.visibility = if (on) INVISIBLE else VISIBLE" in closing, closing)
        assertFalse("sh(" in closing || "force-stop" in closing || "release()" in closing, "giấu mặt vẽ = 0 lệnh, không nhả màn ảo")
    }

    // ── A4 ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `A4 - mo o - R1-8 roi app da co task thi K8, force-stop chi SAU, va kiem lai o con song`() {
        val launch = SourceRoots.body(host, "private fun launchInto(")
        order(launch, "if (released) return",
            "SlotReturnRun.bringBackMarked(context, displayId, p, sh) || SlotReturnRun.openLive(context, displayId, p, cmd, sh)",
            "SlotLiveProbe.watch(", "return",
            // Lượt đọc / K8 của A4 tốn thời gian ⇒ ô có thể đã bị tháo: kiểm lại TRƯỚC khi giết app (cùng lẽ khối SOÁT OCR).
            "if (released) return", "sh(\"am force-stop \$p\")", "Thread.sleep(1000)", "if (released) return", "sh(cmd)")
        assertEquals(1, Regex("am force-stop").findAll(launch).count(), "đúng một lệnh dừng app trong thân mở ô")
        assertTrue(launch.indexOf("am force-stop") > launch.indexOf("SlotReturnRun.openLive("), "force-stop chỉ SAU đường A4")
    }

    @Test
    fun `A4 - ben thi hanh - quyet dinh o core, dung dang man nha, khong force-stop, quen ban do o 7 da rong`() {
        val fn = SourceRoots.body(run, "fun openLive(ctx: Context, vd: Int, pkg: String, cmd: String, sh: (String) -> String): Boolean {")
        order(fn, "val parkedVd = ParkedApps.vdOf(pkg)",
            "seq(sh).openLive(vd, pkg, BehindMarksStore(ctx).read(), DefaultHome.shownComponents(ctx), cmd)",
            "catch (e: IOException)", "catch (e: RuntimeException)", "Log.i(TAG, out.line)",
            "if (out.result != SlotReturn.Back.IN_SLOT) return false",
            "if (parkedVd != null && out.from == parkedVd) main.post { if (ParkedApps.vdOf(pkg) == parkedVd) ParkedApps.forget(pkg) }",
            "return true")
        listOf("force-stop", "\"am ", "move-task", "--display").forEach { assertFalse(it in fn, "'$it' — lệnh chỉ dựng ở :core (K8 có sẵn)") }
    }

    // ── A5(a) ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `A5a - Tu mo nhac khi len xe o He thong ngay duoi Mo app khi no may, App nhac mac dinh o lai Giong noi`() {
        val sections = code("SettingsSections.kt")
        order(sections, "deps.bridge.setHeadlessAutostart(", "SettingsTripAppsSection(context, rows, deps).section(body)",
            "SettingsTripMusicSection(context, rows, deps).section(body)", "R.string.kachi_sub_maint")
        val voice = code("SettingsVoiceSection.kt")
        assertFalse("SettingsTripMusicSection(" in voice, "không dựng hai lần (hai chỗ ghi cùng khoá)")
        assertTrue("deps.bridge.setMusicDefaultApp(" in voice, "App nhạc mặc định (khoá giọng nói) ở lại trang Giọng nói")
        val catalog = SettingsCatalog.ENTRIES
        val music = catalog.single { it.prefKey == "ignition_music" }
        assertEquals(SettingsGroup.SYSTEM, music.group)
        assertEquals("system_ignition_music", music.id)
        val system = catalog.filter { it.group == SettingsGroup.SYSTEM }.map { it.id }
        assertEquals(system.indexOf("system_ignition_apps") + 1, system.indexOf("system_ignition_music"), "danh mục: ngay dưới app khi nổ máy")
        assertEquals(SettingsGroup.VOICE, catalog.single { it.id == "voice_music_default_app" }.group)
    }

    // ── A5(b) ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `A5b - khung o cat theo vien bo RIENG, khong muon nen (anh nen = vien chu nhat)`() {
        val make = SourceRoots.body(workspace, "private fun makeSlot(")
        order(make, "KachiGlass.apply(fl, Sp.RADIUS_L", "SlotFrameClip.apply(fl, Sp.dpf(context, Sp.RADIUS_L))", "is SlotContent.App ->")
        assertFalse("fl.clipToOutline = true" in make, "không còn clipToOutline trần (viền mượn từ nền — chữ nhật khi có ảnh nền)")
        val clip = code("SlotFrameClip.kt")
        order(SourceRoots.body(clip, "fun apply(frame: View, radiusPx: Float)"), "frame.outlineProvider = Round(radiusPx)", "frame.clipToOutline = true")
        order(SourceRoots.body(clip, "override fun getOutline(view: View, outline: Outline)"),
            "SlotFrameShape.radius(view.width, view.height, r) ?: return outline.setEmpty()",
            "outline.setRoundRect(0, 0, view.width, view.height, rad)")
        listOf("TextureView", "clipPath", "BitmapShader", "onDraw", "Paint(").forEach {
            assertFalse(it in clip, "'$it' — không mở cơ chế vẽ / cắt thứ hai (mặt nạ, TextureView)")
        }
    }
}
