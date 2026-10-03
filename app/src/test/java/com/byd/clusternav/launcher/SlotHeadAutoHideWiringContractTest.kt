package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.87 · R-AH1..3 — nút ⇄ TỰ ẨN: dây nối + năm điều không được sai ═════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §4.2. `:app` không có Robolectric ⇒ bài này canh MÃ (đã bỏ chú thích,
 * [SourceRoots.codeOf]); luật nghỉ thuần có bảng đủ ô ở `SlotHeadRestTest` (`:core`). Mỗi bài khoá một cách làm hỏng
 * app đang chạy trong ô mà mắt thường khó thấy trên máy ảo:
 *  - `observe` gọi TRƯỚC `super` ⇒ cú DOWN đầu tiên chọn trúng ⇄ vừa hiện thay vì app (cướp chạm — R-AH2);
 *  - ẩn bằng alpha ⇒ nút vô hình vẫn bấm được (FIX286 · ES1); ẩn bằng `GONE` ⇒ đo lại cả workspace mỗi cú chạm;
 *  - animate mà không `cancel()` trước ⇒ hành động-cuối `INVISIBLE` của lượt mờ cũ chạy muộn, ⇄ kẹt ẩn;
 *  - ngưỡng nhấp đúp chụp sẵn vào field ⇒ lệch với máy; thiếu chặn "DOWN trong vùng ⇄" ⇒ chạm lần hai vào ô tìm kiếm
 *    của Google Maps mở bảng chọn;
 *  - hẹn giờ không gỡ khi rời cửa sổ ⇒ chạy trên view đã tháo; hàm viết xong mà không ai gọi (CLAUDE.md §8).
 */
class SlotHeadAutoHideWiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    private val workspace by lazy { code("WorkspaceView.kt") }
    private val helper by lazy { code("SlotHeadAutoHide.kt") }

    @Test
    fun `dispatchTouchEvent goi super TRUOC roi moi nhin, tra nguyen ket qua`() {
        val fn = SourceRoots.body(workspace, "override fun dispatchTouchEvent(ev: MotionEvent): Boolean")
        val sup = fn.indexOf("val handled = super.dispatchTouchEvent(ev)")
        val obs = fn.indexOf("heads.observe(ev, slotViews, handled)")
        assertTrue(sup >= 0, "phải gọi lớp cha và GIỮ kết quả: $fn")
        assertTrue(obs > sup, "observe phải đứng SAU super — đích chạm chọn xong rồi ⇄ mới đổi trạng thái (R-AH2)")
        assertTrue("return handled" in fn, "trả NGUYÊN kết quả của super — không nuốt, không nhả chạm của ai")
        assertFalse(Regex("""return\s+(true|false)""").containsMatchIn(fn), "không được tự quyết nhận/bỏ chạm")
        assertEquals(1, Regex("""super\.dispatchTouchEvent\(""").findAll(fn).count(), "super đúng một lần")
    }

    @Test
    fun `observe khong nuot cham, khong sua MotionEvent`() {
        assertTrue(
            Regex("""fun observe\(ev: MotionEvent, slots: List<View>, consumed: Boolean\)\s*\{""").containsMatchIn(helper),
            "observe trả Unit — nó KHÔNG có quyền nói 'đã nhận chạm'",
        )
        val fn = SourceRoots.body(helper, "fun observe(")
        listOf("ev.setAction", "ev.setLocation", "ev.offsetLocation", "ev.recycle", ".action =").forEach {
            assertFalse(it in fn, "observe không được đổi sự kiện chạm ($it) — app trong ô đã nhận chính đối tượng đó")
        }
    }

    @Test
    fun `an la INVISIBLE, khong GONE, khong chi alpha`() {
        assertFalse("View.GONE" in helper, "GONE ⇒ requestLayout cả workspace mỗi cú chạm (lỗi 'nháy dựt')")
        val settle = SourceRoots.body(helper, "private fun settle(")
        assertTrue("View.INVISIBLE" in settle && "e.head.alpha = if (hidden) 0f else 1f" in settle,
            "trạng thái ẩn ban đầu = alpha 0 + INVISIBLE (alpha-0 mà VISIBLE là nút vô hình bấm được)")
        val hide = SourceRoots.body(helper, "private fun hide(")
        assertTrue(Regex("""withEndAction \{ head\.visibility = View\.INVISIBLE \}""").containsMatchIn(hide),
            "mờ xong phải về INVISIBLE — không thì vùng ⇄ vẫn ăn chạm của app")
        val reveal = SourceRoots.body(helper, "private fun reveal(")
        assertTrue("e.head.visibility = View.VISIBLE" in reveal, "hiện = VISIBLE trước rồi mới mờ vào")
    }

    @Test
    fun `cancel truoc MOI animate`() {
        val names = Regex("""private fun (\w+)\(""").findAll(helper).map { it.groupValues[1] }.toList()
        val animating = names.filter { "animate().alpha(" in SourceRoots.body(helper, "private fun $it(") }
        assertEquals(listOf("reveal", "hide"), animating, "chỉ hai chỗ animate — chỗ mới phải được bài này soi")
        assertEquals(animating.size, Regex("""animate\(\)\.alpha\(""").findAll(helper).count(), "mỗi hàm một animate")
        animating.forEach { name ->
            val fn = SourceRoots.body(helper, "private fun $name(")
            val cancel = fn.indexOf("animate().cancel()")
            assertTrue(cancel in 0 until fn.indexOf("animate().alpha("),
                "$name: cancel() TRƯỚC animate — r47 cancel xoá hành động-cuối, không thì INVISIBLE muộn làm ⇄ kẹt ẩn")
        }
        assertTrue("animate().cancel()" in SourceRoots.body(helper, "private fun settle("), "đặt trạng thái đầu cũng phải huỷ lượt mờ cũ")
    }

    @Test
    fun `nhip nhap dup doc tai cho goi va chan DOWN trong vung nut`() {
        val fn = SourceRoots.body(helper, "fun observe(")
        assertTrue("ViewConfiguration.getDoubleTapTimeout()" in fn, "đọc ngưỡng nhấp đúp tại chỗ gọi (không chụp sẵn)")
        assertEquals(1, Regex("""getDoubleTapTimeout""").findAll(helper).count(), "không có bản chụp thứ hai ở field")
        assertTrue("downInHead = e != null && inHit(e, slots[i], ev.x, ev.y)" in fn, "DOWN phải ghi lại có rơi vào khung chạm ⇄ không")
        assertTrue(Regex("""else if \(!downInHead\) \{\s*host\.postDelayed\(e\.reveal""").containsMatchIn(fn),
            "DOWN trong vùng ⇄ đang ẩn ⇒ KHÔNG hiện (ô tìm kiếm Google Maps giữa-trên vẫn gõ được)")
        val inHit = SourceRoots.body(helper, "private fun inHit(")
        assertTrue("e.hit.left" in inHit && "e.hit.width" in inHit, "vùng chặn là KHUNG CHẠM của ⇄, không phải cả dải đầu ô")
    }

    @Test
    fun `cham khong ai nhan thi hien ngay - khung do hoac moi khung`() {
        val fn = SourceRoots.body(helper, "fun observe(")
        assertTrue(Regex("""!consumed && i >= 0 -> e\?\.let\(::reveal\)""").containsMatchIn(fn), "khung trống: hiện ⇄ khung đó")
        assertTrue(Regex("""!consumed -> slots\.indices\.forEach \{ j -> valid\(j, slots\)\?\.let\(::reveal\) \}""").containsMatchIn(fn),
            "ngoài mọi khung: hiện ⇄ mọi khung (owner: 'nhấn đại vào màn nó lại lòi ra')")
        val reveal = SourceRoots.body(helper, "private fun reveal(")
        assertTrue("host.postDelayed(e.hide, SlotHeadRest.HIDE_AFTER_MS)" in reveal, "hiện xong phải hẹn ẩn lại")
    }

    @Test
    fun `go hen gio khi roi cua so va nghe lai TalkBack khi gan`() {
        assertTrue("heads.release()" in SourceRoots.body(workspace, "override fun onDetachedFromWindow()"))
        assertTrue("heads.attach()" in SourceRoots.body(workspace, "override fun onAttachedToWindow()"))
        val release = SourceRoots.body(helper, "fun release()")
        assertTrue("entries.values.forEach(::drop)" in release, "gỡ hẹn giờ của MỌI ô")
        assertTrue("removeTouchExplorationStateChangeListener(teListener)" in release, "gỡ người nghe TalkBack (không giữ view)")
        val drop = SourceRoots.body(helper, "private fun drop(")
        assertTrue("host.removeCallbacks(e.reveal)" in drop && "host.removeCallbacks(e.hide)" in drop && "animate().cancel()" in drop)
        assertTrue("addTouchExplorationStateChangeListener(teListener)" in SourceRoots.body(helper, "fun attach()"))
    }

    /** CLAUDE.md §8 — hàm mới phải có chỗ gọi thật; và ⇄ phải là con CUỐI của khung ở cả bốn nhánh. */
    @Test
    fun `cho goi ton tai va nut la con cuoi cua moi nhanh`() {
        val makeSlot = SourceRoots.body(workspace, "private fun makeSlot(")
        val reg = makeSlot.indexOf("heads.register(index, fl, content)")
        assertTrue(reg > makeSlot.lastIndexOf("slotHead("), "đăng ký SAU khi mọi nhánh đã gắn ⇄ (một chỗ cho cả bốn đường dựng lại)")
        val branches = Regex("""(?m)^\s*(is SlotContent\.\w+ ->|SlotContent\.Empty ->)""").findAll(makeSlot).map { it.range.first }.toList()
        assertEquals(4, branches.size, "makeSlot có đúng bốn nhánh")
        val ends = branches.drop(1) + reg
        branches.zip(ends).forEach { (from, to) ->
            val seg = makeSlot.substring(from, to)
            assertTrue(seg.lastIndexOf("addView(") == seg.lastIndexOf("addView(slotHead("),
                "⇄ phải được gắn SAU CÙNG trong nhánh (nổi trên cùng, và register lấy con cuối): ${seg.take(60)}")
        }
        assertTrue("SlotHeadRest.rest(" in SourceRoots.body(helper, "private fun settle("), "luật nghỉ đi qua :core")
        assertTrue("SlotHeadRest.kindOf(" in SourceRoots.body(helper, "fun register("))
        assertTrue("heads.setEnabled(on)" in SourceRoots.body(workspace, "fun setSlotHeadAutoHide("))
        val render = code("KachiHomeRender.kt")
        assertTrue(
            "if (prev?.slotHeadAutoHide != state.slotHeadAutoHide) workspace.setSlotHeadAutoHide(state.slotHeadAutoHide)" in render,
            "màn chính phải áp công tắc khi state đổi (lượt đầu, đổi hồ sơ, ô tích)",
        )
        assertTrue(render.indexOf("workspace.setSlotHeadAutoHide(") < render.indexOf("workspace.render("),
            "áp cờ TRƯỚC khi dựng ô của lượt này")
        assertFalse("SlotHeadAutoHide" in code("SlotSwapButton.kt"), "nối dây NGOÀI bộ dựng nút (bộ dựng bị ghim byte)")
    }

    @Test
    fun `cong tac theo ho so di mot chieu tu Cai dat toi o luu`() {
        val home = code("SettingsSectionsHome.kt")
        assertTrue("on = deps.state().slotHeadAutoHide" in home && "deps.onSlotHeadAutoHide(on)" in home)
        assertTrue("slotHeads(body)" in SourceRoots.body(home, "fun build("), "hàng phải được dựng trên trang")
        assertTrue("onSlotHeadAutoHide = { on -> onSlotHeadAutoHide(on) }" in code("HomePanels.kt"))
        assertTrue("onSlotHeadAutoHide = { on -> viewModel.setSlotHeadAutoHide(on) }" in code("KachiHomeWiring.kt"))
        val vm = SourceRoots.body(code("HomeViewModel.kt"), "fun setSlotHeadAutoHide(")
        assertTrue("_uiState.update" in vm && "repository.setSlotHeadAutoHide(on)" in vm, "state + lưu bền trong MỘT lượt")
        val repo = code("PrefsWorkspaceRepository.kt")
        assertTrue("slotHeadAutoHide = prefs.slotHeadAutoHide()" in SourceRoots.body(repo, "override fun load()"),
            "nạp cùng lượt ⇒ đổi hồ sơ là ⇄ đổi theo")
        val prefs = code("WorkspacePrefsSlotHead.kt")
        assertTrue("sp.booleanOrNull(key(K_SWAP_AUTOHIDE)) ?: true" in prefs, "vắng khoá ⇒ BẬT (mặc định owner chọn)")
        assertFalse(Regex("""const val K_\w+ = "slot_""").containsMatchIn(prefs), "khoá cờ không được rơi vào họ `slot_` (nội dung ô)")
    }
}
