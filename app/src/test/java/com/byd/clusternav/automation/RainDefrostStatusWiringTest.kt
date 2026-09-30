package com.byd.clusternav.automation

import com.byd.clusternav.launcher.automation.RainDefrostAction
import com.byd.clusternav.launcher.automation.RainDefrostChoice
import com.byd.clusternav.launcher.automation.RainDefrostGlasses
import com.byd.clusternav.launcher.automation.RainDefrostState
import com.byd.clusternav.launcher.automation.RainDefrostStatus
import com.byd.clusternav.launcher.automation.RainGlass
import com.byd.clusternav.launcher.automation.RainGlassLast
import com.byd.clusternav.launcher.automation.RainGlassOutcome
import com.byd.clusternav.launcher.automation.RainStatusWords
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ kachi-automation V8.1 · DÒNG TÌNH TRẠNG + NHỊP ĐẦU — BÀI CANH DÂY NỐI của `:app` (spec §V8.1 · T13) ═══════════
 *
 * Luật chọn/ghép chữ + nhịp chạy THẬT ở `:core` (`RainDefrostStatusTest`, `RainDefrostCadenceTest`). Ở đây canh phần
 * `:core` không thấy được:
 *  1. màn Cài đặt → cầu → applier → `:core` nối đúng, và KHÔNG có lượt HAL/ghi bền nào trên đường hiển thị;
 *  2. chữ trên màn: bảng [RainStatusWords] dựng từ CHÍNH hai tệp `strings_kachi.xml` theo ĐÚNG ánh xạ trường → khoá
 *     mà mã màn hình dùng (đọc từ thân `rainWords`) ⇒ gõ lộn khoá, thiếu khoá, hay dịch sai chỗ điền đều đỏ ở đây;
 *  3. applier ghi kết quả + báo nhịp theo đúng thứ tự, vòng gọi `loopStarted()` TRƯỚC khi vào vòng.
 *
 * Cắt vùng bằng [SourceRoots.body] (đếm ngoặc, nổ khi mốc không tồn tại) — không `substringAfter/Before`.
 */
class RainDefrostStatusWiringTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val rain by lazy { app("automation/RainDefrostApplier.kt") }
    private val service by lazy { app("automation/AutomationService.kt") }
    private val bridge by lazy { app("launcher/ClusterNavBridgeAutomation.kt") }
    private val ui by lazy { app("launcher/SettingsSectionsCar.kt") }

    // ══ (1) Nối dây UI → cầu → applier → :core ══════════════════════════════════════════════════════════════

    /** Khối dòng tình trạng đứng NGAY dưới hai hàng sấy, trước ghi chú; dựng lại khi dựng trang và sau mỗi cú chạm. */
    @Test
    fun `khoi dong tinh trang ngay duoi hai hang, lam tuoi khi dung trang va sau cu cham`() {
        val body = SourceRoots.body(ui, "private fun rainDefrost(")
        val order = listOf(
            "bridge.setRainDefrostGlass(RainGlass.FRONT, on)",
            "bridge.setRainDefrostGlass(RainGlass.REAR, on)",
            "body.addView(rainStatus)",
            "kachi_rain_defrost_note",
        ).map(body::indexOf)
        assertTrue(order.all { it >= 0 }, "thiếu mắt xích: $order\n$body")
        assertEquals(order.sorted(), order, "thứ tự trên màn: hai hàng → dòng tình trạng → ghi chú")
        assertEquals(3, Regex("""refreshRainStatus\(\)""").findAll(body).count(), "dựng trang + sau mỗi cú chạm (×2)")
        listOf(RainGlass.FRONT, RainGlass.REAR).forEach { g ->
            val tap = body.indexOf("bridge.setRainDefrostGlass(RainGlass.$g, on)")
            val refresh = body.indexOf("refreshRainStatus()", tap)
            // Biên = hàng kế, hoặc chỗ gắn khối (hàng cuối) — không để lượt làm tươi lúc DỰNG TRANG giả làm lượt sau cú chạm.
            val bound = listOf(body.indexOf("rows.checkRow(", tap), body.indexOf("body.addView(rainStatus)", tap))
                .filter { it >= 0 }.min()
            assertTrue(refresh in (tap + 1) until bound, "cú chạm ô $g phải làm tươi dòng tình trạng NGAY sau khi ghi")
        }
    }

    /**
     * Soát V8.1 Pass 7 · P2 — trang Cài đặt được NHỚ suốt một lượt mở bảng (`SettingsPanel.show` gắn lại trang cũ khi
     * đổi nhóm) và bảng còn mở khi HOME bị app khác che ⇒ chỉ làm tươi lúc dựng là dòng tình trạng đứng im: quay lại
     * sau một phút vẫn "chưa kiểm lần nào" và ảnh chụp nói sai. Khoá: chính khối dòng tình trạng làm tươi mỗi lần cửa
     * sổ HIỆN (gắn lại cũng đi qua `onWindowVisibilityChanged` — AOSP trích ở KDoc), và làm qua `post` (không sửa cây
     * view giữa lượt phát sự kiện gắn).
     */
    @Test
    fun `khoi dong tinh trang lam tuoi moi lan trang hien lai`() {
        val decl = SourceRoots.body(ui, "private val rainStatus: LinearLayout = object : LinearLayout(context) {")
        val hook = SourceRoots.body(decl, "override fun onWindowVisibilityChanged(visibility: Int)")
        assertTrue("super.onWindowVisibilityChanged(visibility)" in hook, hook)
        assertTrue("if (visibility == View.VISIBLE) post { refreshRainStatus() }" in hook, "chỉ khi HIỆN, và qua post: $hook")
        assertEquals(1, Regex("""\bval rainStatus\b""").findAll(ui).count(), "một khối duy nhất — chính khối có móc được gắn")
    }

    /** Đường hiển thị chỉ đọc RAM qua cầu: không HAL, không ghi bền, mỗi dòng một `statusRow`, bốn màu đủ bốn ca. */
    @Test
    fun `lam tuoi chi doc RAM qua cau, khong HAL, khong ghi ben`() {
        val refresh = SourceRoots.body(ui, "private fun refreshRainStatus(")
        assertTrue("rainStatus.removeAllViews()" in refresh, "dựng lại, không chồng dòng cũ")
        assertTrue("bridge.rainDefrostStatus(rainWords())" in refresh)
        assertTrue("rows.statusRow(colour, line.text)" in refresh)
        listOf("RainStatusTone.OK -> KachiTheme.GREEN", "RainStatusTone.WAIT -> KachiTheme.AMBER",
            "RainStatusTone.FAIL -> KachiTheme.RED", "RainStatusTone.IDLE -> KachiTheme.MUT2")
            .forEach { assertTrue(it in refresh, "màu: $it") }

        val status = SourceRoots.body(bridge, "fun ClusterNavBridge.rainDefrostStatus(")
        assertTrue(
            "RainDefrostStatus.lines(rainDefrostChoice(), RainDefrostApplier::lastOf, RainDefrostApplier.nextCheckWallMs(), words, clock)" in status,
            "cầu → :core với ĐÚNG nguồn kết quả từng kính: $status",
        )
        listOf(refresh, status, SourceRoots.body(rain, "fun lastOf("), SourceRoots.body(rain, "fun nextCheckWallMs("))
            .forEach { src ->
                listOf("carControl", "BydHalGateway", "readState(", "featureGet(", "toggle(", "getSharedPreferences", ".edit()", "Prefs.set")
                    .forEach { bad -> assertFalse(bad in src, "đường hiển thị không được chạm '$bad': $src") }
            }
        assertTrue("lastResults.of(glass)" in SourceRoots.body(rain, "fun lastOf("), "kết quả của ĐÚNG kính được hỏi")
        assertTrue(
            "cadence.nextDueMs(elapsed, due.get())" in SourceRoots.body(rain, "fun nextCheckWallMs("),
            "giờ nhịp kế tính cả cờ due chưa được vòng đọc (đọc, không xoá — không `consumeDue()` ở đường hiển thị)",
        )
        assertFalse("consumeDue()" in SourceRoots.body(rain, "fun nextCheckWallMs("), "đường hiển thị không được tiêu cờ")
    }

    /**
     * Applier: gác nhịp → chạy → ghi kết quả (đồng hồ tường, chỉ để hiện) → báo nhịp đọc có đủ không. Ghi kết quả
     * TRƯỚC `ran` không bắt buộc về luật, nhưng cả ba phải có — thiếu `record` là dòng tình trạng mãi "chưa kiểm".
     */
    @Test
    fun `applier gac nhip, ghi ket qua tung kinh, bao nhip doc du hay khong`() {
        val tick = SourceRoots.body(rain, "fun tickIfDue(")
        val steps = listOf(
            "if (!cadence.shouldRun(nowMs, consumeDue())) return false",
            "val outcomes = tick(ctx)",
            "lastResults.record(System.currentTimeMillis(), outcomes)",
            "cadence.ran(nowMs, RainDefrostCadence.halReady(outcomes))",
        )
        val at = steps.map(tick::indexOf)
        assertTrue(at.all { it >= 0 }, "thiếu mắt xích: $steps\n$tick")
        assertEquals(at.sorted(), at, "thứ tự: $steps")
        assertTrue("retryMs = AutomationService.TICK_MS" in rain, "sàn/khoảng thử lại = một lượt thức (60 s)")
        assertTrue("cadence.loopStarted()" in SourceRoots.body(rain, "fun loopStarted("))
    }

    /** R-V8.8 — vòng báo "bắt đầu" TRƯỚC `while` ⇒ nhịp mưa đầu ở lượt thức đầu, không chờ uptime 5′. */
    @Test
    fun `vong goi loopStarted truoc khi vao vong`() {
        val loop = SourceRoots.body(service, "private fun startLoop(")
        val started = loop.indexOf("RainDefrostApplier.loopStarted()")
        val whileAt = loop.indexOf("while (myGen == generation && anyEnabled(app))")
        assertTrue(started >= 0 && whileAt >= 0, loop)
        assertTrue(started < whileAt, "loopStarted() phải chạy TRƯỚC lượt thức đầu")
        assertEquals(1, Regex("""RainDefrostApplier\.loopStarted\(\)""").findAll(service).count())
    }

    // ══ (2) Chữ THẬT từ tài nguyên — ảnh chữ mẫu mọi ca ══════════════════════════════════════════════════════

    /** Ánh xạ trường → khoá đọc từ CHÍNH thân `rainWords` của màn hình (không chép tay bảng thứ hai). */
    private val fieldToKey: Map<String, String> by lazy {
        val body = SourceRoots.body(ui, "private fun rainWords(")
        Regex("""(\w+) = context\.getString\(R\.string\.(\w+)\)""").findAll(body)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun xml(relative: String): Map<String, String> =
        Regex("""<string name="(kachi_rain_st_\w+)">(.*?)</string>""").findAll(SourceRoots.text(relative))
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun words(relative: String): RainStatusWords {
        val res = xml(relative)
        val w = fieldToKey.mapValues { (field, key) -> requireNotNull(res[key]) { "$relative thiếu $key (trường $field)" } }
        return RainStatusWords(
            front = w.getValue("front"), rear = w.getValue("rear"), never = w.getValue("never"), next = w.getValue("next"),
            rain = w.getValue("rain"), dry = w.getValue("dry"), sensorError = w.getValue("sensorError"),
            carOn = w.getValue("carOn"), carOff = w.getValue("carOff"), carOffAfterOn = w.getValue("carOffAfterOn"),
            carError = w.getValue("carError"),
            turnOnOk = w.getValue("turnOnOk"), turnOnFail = w.getValue("turnOnFail"),
            turnOffOk = w.getValue("turnOffOk"), turnOffFail = w.getValue("turnOffFail"), skipped = w.getValue("skipped"),
            keepOn = w.getValue("keepOn"), nothingToDo = w.getValue("nothingToDo"), notOurs = w.getValue("notOurs"),
        )
    }

    private val vi by lazy { words("src/main/res/values/strings_kachi.xml") }
    private val en by lazy { words("src/main/res/values-en/strings_kachi.xml") }

    private val clock: (Long) -> String = { ms -> val m = (ms / 60_000) % 1440; "%02d:%02d".format(m / 60, m % 60) }
    private val t1402 = (14 * 60 + 2) * 60_000L
    private val t1407 = (14 * 60 + 7) * 60_000L
    private val t1357 = (13 * 60 + 57) * 60_000L

    /** Kết quả một làn dựng bằng ĐÚNG luật của `:core` (`plan` + `settle`), không dựng tay quyết định. */
    private fun last(
        glass: RainGlass,
        speed: Int?,
        read: Boolean?,
        owned: Boolean = false,
        writeOk: Boolean? = true,
        offAfterOn: Long? = null,
    ): RainGlassLast {
        val plan = RainDefrostGlasses.plan(glass, RainDefrostState(owned = owned), speed, read)
        val w = if (plan.action == RainDefrostAction.Leave) null else writeOk
        return RainGlassLast(t1402, RainGlassOutcome(plan, w, RainDefrostGlasses.settle(plan, w), committed = true), offAfterOn)
    }

    private fun text(words: RainStatusWords, glass: RainGlass, last: RainGlassLast?, next: Long? = null): String =
        RainDefrostStatus.line(glass, last, next, words, clock).text

    @Test
    fun `du 19 khoa, hai ngon ngu, anh xa man hinh khop tai nguyen`() {
        assertEquals(19, fieldToKey.size, "mỗi trường của RainStatusWords một khoá: $fieldToKey")
        assertEquals(fieldToKey.values.toSet(), xml("src/main/res/values/strings_kachi.xml").keys, "vi: đúng bộ khoá")
        assertEquals(fieldToKey.values.toSet(), xml("src/main/res/values-en/strings_kachi.xml").keys, "en: đúng bộ khoá")
        listOf(vi, en).forEach { w ->
            assertTrue(RainDefrostStatus.SPEED in w.rain && RainDefrostStatus.SPEED in w.dry, "chỗ điền mức gạt")
            assertTrue(RainDefrostStatus.TIME in w.next && RainDefrostStatus.TIME in w.carOffAfterOn, "chỗ điền giờ")
        }
    }

    /** Ảnh chữ mẫu — tiếng Việt, mọi ca (spec V8.1 §C bảng ca). */
    @Test
    fun `chu mau tieng Viet moi ca`() {
        val f = RainGlass.FRONT
        val r = RainGlass.REAR
        mapOf(
            text(vi, f, null, t1407) to "Kính trước · chưa kiểm lần nào từ lúc mở xe · nhịp kế ~14:07",
            text(vi, r, null) to "Kính sau + gương · chưa kiểm lần nào từ lúc mở xe",
            text(vi, r, last(r, null, null)) to "Kính sau + gương · 14:02 · cảm biến mưa không đọc được ⇒ để nguyên",
            text(vi, r, last(r, 3, null)) to "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: không đọc được",
            text(vi, r, last(r, 3, false)) to "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: tắt → Kachi bật: tới xe ✓",
            text(vi, r, last(r, 3, false, writeOk = false)) to
                "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: tắt → lệnh bật: không tới xe ✗",
            text(vi, r, last(r, 3, false, writeOk = null)) to
                "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: tắt → bỏ lệnh (vừa đổi lựa chọn)",
            text(vi, r, last(r, 3, false, owned = true, offAfterOn = t1357)) to
                "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: vẫn tắt sau lệnh bật 13:57 → Kachi bật: tới xe ✓",
            text(vi, f, last(f, 3, true)) to "Kính trước · 14:02 · mưa (gạt 3) · xe báo: bật → đang sấy, để nguyên",
            text(vi, f, last(f, 1, true, owned = true)) to "Kính trước · 14:02 · khô (gạt 1) · xe báo: bật → Kachi tắt: tới xe ✓",
            text(vi, f, last(f, 1, true, owned = true, writeOk = false)) to
                "Kính trước · 14:02 · khô (gạt 1) · xe báo: bật → lệnh tắt: không tới xe ✗",
            text(vi, r, last(r, 1, true)) to "Kính sau + gương · 14:02 · khô (gạt 1) · xe báo: bật → Kachi không quản, để nguyên",
            text(vi, f, last(f, 1, false)) to "Kính trước · 14:02 · khô (gạt 1) · xe báo: tắt → không cần sấy",
        ).forEach { (actual, expected) -> assertEquals(expected, actual) }
    }

    /** Ảnh chữ mẫu — tiếng Anh, cùng các ca (không dấu tiếng Việt — `LauncherI18nContractTest` canh riêng). */
    @Test
    fun `chu mau tieng Anh moi ca`() {
        val f = RainGlass.FRONT
        val r = RainGlass.REAR
        mapOf(
            text(en, f, null, t1407) to "Front windscreen · not checked since the car started · next check ~14:07",
            text(en, r, last(r, null, null)) to "Rear + mirrors · 14:02 · rain sensor unreadable ⇒ left as is",
            text(en, r, last(r, 3, null)) to "Rear + mirrors · 14:02 · rain (wiper 3) · car says: unreadable",
            text(en, r, last(r, 3, false)) to
                "Rear + mirrors · 14:02 · rain (wiper 3) · car says: off → Kachi turned it on: reached the car ✓",
            text(en, r, last(r, 3, false, writeOk = false)) to
                "Rear + mirrors · 14:02 · rain (wiper 3) · car says: off → turn-on command: did not reach the car ✗",
            text(en, r, last(r, 3, false, writeOk = null)) to
                "Rear + mirrors · 14:02 · rain (wiper 3) · car says: off → command dropped (selection just changed)",
            text(en, r, last(r, 3, false, owned = true, offAfterOn = t1357)) to
                "Rear + mirrors · 14:02 · rain (wiper 3) · car says: still off after the 13:57 turn-on → Kachi turned it on: reached the car ✓",
            text(en, f, last(f, 3, true)) to "Front windscreen · 14:02 · rain (wiper 3) · car says: on → already on, left alone",
            text(en, f, last(f, 1, true, owned = true)) to
                "Front windscreen · 14:02 · dry (wiper 1) · car says: on → Kachi turned it off: reached the car ✓",
            text(en, f, last(f, 1, true, owned = true, writeOk = false)) to
                "Front windscreen · 14:02 · dry (wiper 1) · car says: on → turn-off command: did not reach the car ✗",
            text(en, r, last(r, 1, true)) to "Rear + mirrors · 14:02 · dry (wiper 1) · car says: on → not managed by Kachi, left alone",
            text(en, f, last(f, 1, false)) to "Front windscreen · 14:02 · dry (wiper 1) · car says: off → nothing to do",
        ).forEach { (actual, expected) -> assertEquals(expected, actual) }
    }

    /** Chỉ kính đang chọn có dòng, theo thứ tự trước → sau (qua `lines`, đúng hàm cầu gọi). */
    @Test
    fun `chi kinh dang chon co dong`() {
        val lastOf = { g: RainGlass -> last(g, 3, false) }
        assertEquals(listOf(RainGlass.REAR), RainDefrostStatus.lines(RainDefrostChoice(false, true), lastOf, null, vi, clock).map { it.glass })
        assertEquals(RainGlass.entries, RainDefrostStatus.lines(RainDefrostChoice(true, true), lastOf, null, vi, clock).map { it.glass })
        assertTrue(RainDefrostStatus.lines(RainDefrostChoice.NONE, lastOf, null, vi, clock).isEmpty())
    }
}
