package com.byd.clusternav.automation

import com.byd.clusternav.launcher.automation.RainGlass
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ kachi-automation V8 · HAI KÍNH ĐỘC LẬP — BÀI CANH DÂY NỐI của `:app` (spec §V8 · T5) ═══════════════════
 *
 * Luật + trình tự một nhịp chạy THẬT ở `:core` (`RainDefrostGlassesTest`, ca C1–C19). Ở đây canh phần `:core`
 * không thấy được: `:app` có nối **đúng kính vào đúng nút** không, rc có quay về không, đổi lựa chọn có ghi đủ ba
 * khoá + quên đúng kính + đánh thức nhịp không, và giao diện còn hàng mờ/khoá nào không.
 *
 * Cắt vùng bằng [SourceRoots.body] (đếm ngoặc, nổ khi mốc không tồn tại) — không `substringAfter/Before`.
 */
class RainDefrostV8WiringTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")
    private fun raw(relative: String): String = SourceRoots.text(relative)

    private val rain by lazy { app("automation/RainDefrostApplier.kt") }
    private val service by lazy { app("automation/AutomationService.kt") }
    private val bridge by lazy { app("launcher/ClusterNavBridgeAutomation.kt") }
    private val prefs by lazy { app("PrefsAutomation.kt") }
    private val ui by lazy { app("launcher/SettingsSectionsCar.kt") }

    /** Mã nút của từng kính — chạy THẬT (không quét nguồn): gõ lộn hai nhánh là ghi kính sau khi mưa hỏi kính trước. */
    @Test
    fun `moi kinh mot ma nut, dung ma ControlRegistry`() {
        assertEquals("defrost", RainDefrostApplier.controlOf(RainGlass.FRONT))
        assertEquals("defrost_rear", RainDefrostApplier.controlOf(RainGlass.REAR))
    }

    /**
     * K1 · K2 — mỗi làn ĐỌC và GHI qua `controlOf(glass)` của CHÍNH kính đó, và rc của lượt ghi là giá trị trả về
     * (về `:core` ⇒ `settle` của đúng kính). V7: một mỏ neo đọc cho cả hai, chỉ rc của mỏ neo được kiểm.
     */
    @Test
    fun `doc va ghi cua mot lan cung qua ma cua chinh kinh do`() {
        val read = SourceRoots.body(rain, "override fun readGlass(")
        assertTrue("readDefrost(app, controlOf(glass))" in read, "làn đọc chính nó: $read")
        val write = SourceRoots.body(rain, "override fun writeGlass(")
        assertTrue("write(app, controlOf(glass), on)" in write, "làn ghi chính nó, rc là giá trị trả về: $write")
        assertFalse("RainGlass.FRONT" in read || "RainGlass.REAR" in read, "làn không được đọc mã cứng của kính khác")
        assertFalse("RainGlass.FRONT" in write || "RainGlass.REAR" in write, "làn không được ghi mã cứng của kính khác")
        val tick = SourceRoots.body(rain, "fun tick(")
        assertTrue("RainDefrostGlasses.tick(seq.incrementAndGet(), memory, HalIo(app))" in tick)
        listOf("pick.first()", "fun selection(", "writeSelected", "fun stateNow(", "fun reset(").forEach {
            assertFalse(it in rain, "đường V7 phải gỡ hẳn: $it")
        }
        val w = SourceRoots.body(rain, "private fun write(")
        assertTrue("control.toggle(controlId, on)" in w, "ghi đi đúng cửa của một cú chạm (carControl.toggle)")
    }

    /**
     * R-V8.5 — đổi lựa chọn chạy ở lượt thức kế (≤ 60 s): cờ `due` đọc-và-xoá MỖI lượt thức.
     *
     * kachi-automation V8.1 (spec bảng V8.1-b): chuỗi `if (consumeDue() || nowMs - lastRainMs >= …)` được thay bằng
     * `tickIfDue` gọi KHÔNG điều kiện mỗi lượt, trong đó `consumeDue()` là ĐỐI SỐ của `cadence.shouldRun` — không còn
     * đường tắt `||` nào có thể bỏ qua nó (chặt hơn thứ tự hai vế). Yêu cầu chưa qua sàn 60 s được GIỮ: `:core
     * RainDefrostCadenceTest › yeu cau chua qua san…` chạy THẬT.
     */
    @Test
    fun `vong nhip doc co due truoc moc 5 phut`() {
        val loop = SourceRoots.body(service, "private fun startLoop(")
        val call = loop.lines().map(String::trim).filter { "RainDefrostApplier.tickIfDue(" in it }
        assertEquals(1, call.size, "đúng một lượt gọi nhịp mưa trong vòng: $call")
        assertTrue(
            call.single().startsWith("runCatching { RainDefrostApplier.tickIfDue(app, nowMs) }"),
            "gọi mỗi lượt thức, không nằm sau một `if` nào: ${call.single()}",
        )
        assertTrue(
            "cadence.shouldRun(nowMs, consumeDue())" in SourceRoots.body(rain, "fun tickIfDue("),
            "consumeDue() là đối số ⇒ luôn được đọc-và-xoá",
        )
        assertFalse("interrupt()" in service, "D6: không ngắt luồng — cờ thôi")
        assertTrue("due.getAndSet(false)" in SourceRoots.body(rain, "fun consumeDue("), "đọc-và-xoá nguyên tử")
        assertTrue("due.set(true)" in SourceRoots.body(rain, "fun requestSoon("))
    }

    /** Động cơ hỏi đúng lựa chọn HIỆU LỰC; tắt hẳn ⇒ quên cả hai kính. */
    @Test
    fun `dong co hoi lua chon hieu luc va quen ca hai khi tat`() {
        assertTrue("RainDefrostApplier.choice(app).any" in SourceRoots.body(service, "fun anyEnabled("))
        assertFalse("rainDefrostEnabled" in service, "công tắc chính một mình không còn là cổng")
        assertTrue("RainDefrostApplier.forgetAll()" in SourceRoots.body(service, "fun sync("))
    }

    /**
     * Cầu: đổi MỘT kính ⇒ đọc lựa chọn → `with(glass, on)` → ghi ba khoá → quên RIÊNG kính đó → xin nhịp sớm →
     * log → `sync`. Ghi prefs phải đứng TRƯỚC `forget` (nhịp nền chụp ký ức rồi mới đọc lựa chọn — KDoc cầu).
     */
    @Test
    fun `cau doi mot kinh ghi du ba khoa, quen dung kinh, danh thuc nhip`() {
        val set = SourceRoots.body(bridge, "fun ClusterNavBridge.setRainDefrostGlass(")
        val steps = listOf(
            "Prefs.rainDefrostChoice(app).with(glass, on)",
            "Prefs.setRainDefrostChoice(app, after)",
            "RainDefrostApplier.forget(glass)",
            "RainDefrostApplier.requestSoon()",
            "RainDefrostApplier.logChoice(after, glass)",
            "AutomationService.sync(app)",
        )
        steps.forEach { assertTrue(it in set, "thiếu mắt xích: $it") }
        val at = steps.map(set::indexOf)
        assertEquals(at.sorted(), at, "thứ tự là hợp đồng: $steps")
        assertFalse("forgetAll" in set, "đổi một kính KHÔNG được quên ký ức của kính kia (V7 reset() xoá cả hai)")
        listOf("fun ClusterNavBridge.setRainDefrost(", "setRainDefrostFront", "setRainDefrostRear", "fun ClusterNavBridge.rainDefrost(")
            .forEach { assertFalse(it in bridge, "cửa ghi/đọc V7 phải gỡ: $it") }
    }

    /** D2 — một `edit()`, ba `putBoolean`, giá trị lấy từ `toKeys()` (`enabled = trước || sau`), rồi `apply()`. */
    @Test
    fun `prefs ghi ca ba khoa trong mot edit`() {
        val set = SourceRoots.body(prefs, "fun Prefs.setRainDefrostChoice(")
        assertEquals(1, Regex("""\.edit\(\)""").findAll(set).count(), "đúng MỘT edit() — ba khoá một lượt")
        assertEquals(3, Regex("""\.putBoolean\(""").findAll(set).count())
        listOf("K_RAIN_DEFROST, k.enabled", "K_RAIN_DEFROST_FRONT, k.front", "K_RAIN_DEFROST_REAR, k.rear").forEach {
            assertTrue(it in set, "ghi sai cột: $it")
        }
        assertTrue("choice.toKeys()" in set)
        assertTrue(".apply()" in set)
        listOf("fun Prefs.setRainDefrostEnabled(", "fun Prefs.setRainDefrostFront(", "fun Prefs.setRainDefrostRear(")
            .forEach { assertFalse(it in prefs, "setter lẻ V7 phải gỡ (ghi lẻ = ô con cũ sống lại): $it") }
    }

    /**
     * D2 · soát V8 Pass 5 — `enabled` phải được ĐỌC TRƯỚC hai ô con. Ba `getBoolean` là ba lần khoá riêng ⇒ nhịp nền
     * chen giữa một lượt ghi đọc được "khoá đầu cũ, khoá sau mới"; đọc `enabled` trước thì mọi bản lẫn vô hại
     * (`RainDefrostGlassesTest › doc lan cu moi…`), đọc con trước thì sinh kính "ma". Kotlin tính đối số theo thứ tự
     * VIẾT ở chỗ gọi (kể cả đối số có tên) ⇒ canh thứ tự chữ là canh thứ tự đọc.
     */
    @Test
    fun `doc enabled truoc hai o con`() {
        val read = SourceRoots.body(prefs, "fun Prefs.rainDefrostChoice(")
        val at = listOf("K_RAIN_DEFROST, false", "K_RAIN_DEFROST_FRONT, true", "K_RAIN_DEFROST_REAR, true").map(read::indexOf)
        assertTrue(at.all { it >= 0 }, "thiếu khoá: $read")
        assertTrue(at[0] < at[1] && at[0] < at[2], "enabled phải đọc TRƯỚC hai ô con: $read")
    }

    /**
     * R-V8.1 · K4 — hai hàng tự đủ nghĩa, KHÔNG hàng chính, KHÔNG hàng mờ/khoá theo hàng khác. Mỗi hàng hiện đúng
     * lựa chọn hiệu lực của chính nó và ghi đúng kính của chính nó.
     */
    @Test
    fun `giao dien hai hang doc lap khong hang mo`() {
        val body = SourceRoots.body(ui, "private fun rainDefrost(")
        assertTrue("val choice = bridge.rainDefrostChoice()" in body)
        assertTrue("on = choice.front" in body && "on = choice.rear" in body)
        assertTrue("bridge.setRainDefrostGlass(RainGlass.FRONT, on)" in body)
        assertTrue("bridge.setRainDefrostGlass(RainGlass.REAR, on)" in body)
        assertEquals(2, Regex("""rows\.checkRow\(""").findAll(body).count(), "đúng hai hàng ô tích")
        listOf("isEnabled", "alpha", "gateRainGlass", "bridge.setRainDefrost(", "kachi_rain_defrost_title").forEach {
            assertFalse(it in ui, "V7 hàng chính/làm mờ phải gỡ: $it")
        }
    }

    /** Chữ hai hàng tự đủ nghĩa ở cả hai ngôn ngữ; chuỗi của hàng chính đã gỡ khỏi cả hai tệp. */
    @Test
    fun `chu hai hang tu du nghia, chuoi hang chinh da go`() {
        val vi = raw("src/main/res/values/strings_kachi.xml")
        val en = raw("src/main/res/values-en/strings_kachi.xml")
        assertTrue(">Mưa thì tự bật sấy kính trước<" in vi)
        assertTrue(">Mưa thì tự bật sấy kính sau + gương<" in vi)
        assertTrue(">Turn on the front defroster when it rains<" in en)
        assertTrue(">Turn on the rear defroster + mirrors when it rains<" in en)
        listOf(vi, en).forEach { xml ->
            assertFalse("kachi_rain_defrost_title" in xml)
            assertFalse("\"kachi_rain_defrost_sub\"" in xml)
        }
        assertTrue("Kachi bật lại" in vi, "ghi chú phải nói vế \"đang mưa thì bật lại\" của luật V3")
        assertTrue("turned back on" in en, "bản EN cũng phải nói vế \"bật lại\" (soát V8 Pass 5 — i18n cân hai tệp)")
    }
}
