package com.byd.clusternav.system

import com.byd.clusternav.system.AppPrereqPlan.Marks
import com.byd.clusternav.system.AppPrereqPlan.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · VM-PREREQ-PKG-ADDED — gói vừa CÀI lúc Kachi sống ⇒ phạm vi của riêng gói đó, đúng thứ lượt kế sẽ chữa ═══════════
 *
 * Lỗi khoá (B2-OQ5, spec 2.89): VietMap gỡ hẳn rồi cài lại (mod đổi khoá ký) ⇒ ROM xoá khỏi danh sách miễn pin [ĐO nguồn r47
 * `DeviceIdleController.java:585-592`] ⇒ lần mở VietMap ĐẦU TIÊN ngay sau đó hiện hộp "IVI không hỗ trợ" vì lượt chữa kế tiếp
 * (kênh sẵn sàng · autostart · mở chiếu) chưa tới. Bộ thu `ACTION_PACKAGE_ADDED` gọi [AppPrereqPlan.forAddedPackage]: CÙNG tập
 * với [AppPrereqPlan.keep] — không thêm phạm vi nào mới, và lượt ready sau không trả lại thứ vừa thêm.
 */
class AppPrereqAddedTest {

    private val vm = "vn.vietmap.live"
    private val yt = "com.google.android.youtube"
    private val other = "com.example.chat"

    private fun facts(
        bubble: Boolean = false,
        badge: Boolean = false,
        castDefault: Boolean = false,
        autoCast: List<String> = emptyList(),
        installed: Set<String> = setOf(vm, yt, other),
    ) = AppPrereqPlan.Facts(vm, vm in installed, bubble, badge, castDefault, autoCast) { it in installed }

    @Test
    fun `VietMap cai lai, moi cong tac TAT - van mien pin (lan no may ke cung chua dung vay)`() {
        val t = AppPrereqPlan.forAddedPackage(facts(), vm).single()
        assertEquals(vm, t.pkg)
        assertEquals(setOf(Prereq.DOZE_EXEMPT), t.needs, "lượt VietMapAutostart / mở chiếu luôn kéo VietMap đã cài vào miễn pin")
        assertTrue(Role.AUTOSTART_PASS in t.roles && Role.CAST_OPEN in t.roles)
    }

    @Test
    fun `VietMap cai lai, bong BAT - mien pin + ve noi`() {
        val t = AppPrereqPlan.forAddedPackage(facts(bubble = true), vm).single()
        assertEquals(setOf(Prereq.DOZE_EXEMPT, Prereq.OVERLAY), t.needs)
    }

    @Test
    fun `app tu chieu cua ho so cai lai - mien pin, app ngoai pham vi - khong doc, khong ghi`() {
        assertEquals(setOf(Prereq.DOZE_EXEMPT), AppPrereqPlan.forAddedPackage(facts(autoCast = listOf(yt)), yt).single().needs)
        assertEquals(emptyList<AppPrereqPlan.Target>(), AppPrereqPlan.forAddedPackage(facts(autoCast = listOf(yt)), other))
        assertEquals(emptyList<AppPrereqPlan.Target>(), AppPrereqPlan.forAddedPackage(facts(), yt), "không còn là app tự chiếu ⇒ ngoài phạm vi")
    }

    @Test
    fun `goi chua thay cai (nhan tin truoc PackageManager) hoac ten la - rong`() {
        assertEquals(emptyList<AppPrereqPlan.Target>(), AppPrereqPlan.forAddedPackage(facts(installed = setOf(yt)), vm))
        assertEquals(emptyList<AppPrereqPlan.Target>(), AppPrereqPlan.forAddedPackage(facts(autoCast = listOf("x;rm -rf /")), "x;rm -rf /"))
    }

    /** Đường trả lại của lượt ready KHÔNG được gỡ thứ lượt PACKAGE_ADDED vừa thêm (không lật qua lật lại). */
    @Test
    fun `cung tap voi keep - luot ready sau khong tra lai thu vua them`() {
        listOf(facts(), facts(bubble = true), facts(badge = true, autoCast = listOf(yt))).forEach { f ->
            listOf(vm, yt, other).forEach { pkg ->
                val added = AppPrereqPlan.forAddedPackage(f, pkg).associate { it.pkg to it.needs }
                val keep = AppPrereqPlan.keep(f)
                added.forEach { (p, needs) -> assertTrue(keep[p].orEmpty().containsAll(needs), "$p: $needs ⊄ keep ${keep[p]}") }
            }
            val marks = object : Marks {
                override fun added(p: Prereq) = AppPrereqPlan.forAddedPackage(f, vm).filter { p in it.needs }.map { it.pkg }.toSet()
                override fun mark(p: Prereq, pkg: String) = true
                override fun unmark(p: Prereq, pkg: String) = true
            }
            assertEquals(emptyList<Pair<Prereq, String>>(), AppPrereqPlan.stale(f, marks), "dấu của lượt PACKAGE_ADDED không bao giờ 'rời phạm vi' ngay")
        }
    }
}
