package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1 · R1–R4, R7 — DÂY NỐI phía `:app` của bốn thanh −/+ khung chiếu (spec `docs/specs/kachi-301-cast-steps-dialog-ia.html`
 * §2.1, §4.1). Phép tính thuần có bài chạy thật ở `:core` (`CastGeometryIntentTest`, `CastResizeQueueTest`); bài này hỏi câu
 * `:core` không hỏi được: màn có THẬT SỰ đi qua các phép đó không.
 *
 * Khoá lỗi owner 13/10 *"−/+ khung chiếu cụm nhảy loạn"* — gốc: `nudge` đọc lại bản ghim NGAY sau một lệnh bất đồng bộ.
 * Thử ĐỎ: trả `nudge` về `val after = bridge.geometryBounds(target)` sau `setGeometryBounds` ⇒ bài 1 đỏ.
 */
class SettingsCastGeometryWiringContractTest {

    private fun launcher(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")
    private val block by lazy { launcher("SettingsSectionsCastGeometry.kt") }
    private val geo by lazy { launcher("ClusterNavBridgeGeometry.kt") }

    @Test
    fun `R1 - cu bam cong tren Y DINH, kep dong bo bang phep cua cau, khong doc lai ban ghim sau lenh`() {
        val nudge = SourceRoots.body(block, "private fun nudge(edge: CastEdge, sign: Int)")
        assertTrue(nudge.contains("intent.nudge(target, truth, edge, sign * step) { bridge.geometryClamp(target, it) }"), nudge)
        val show = nudge.indexOf("show(next)")
        val send = nudge.indexOf("bridge.setGeometryBounds(target, next)")
        assertTrue(show in 0 until send, "hiện số TRƯỚC khi gửi (không đợi lệnh): $nudge")
        assertEquals(1, Regex("""bridge\.geometryBounds\(""").findAll(nudge).count(), "chỉ đọc sự thật MỘT lần, trước khi cộng: $nudge")
        assertFalse(nudge.substring(send).contains("geometryBounds("), "không đọc lại bản ghim sau lệnh bất đồng bộ (gốc lỗi)")
        // Cầu kẹp bằng CHÍNH phép màn dùng.
        assertTrue(SourceRoots.body(geo, "fun ClusterNavBridge.setGeometryBounds(target: CastGeometryTarget, bounds: CastBounds)")
            .contains("val clamped = geometryClamp(target, bounds)"))
    }

    @Test
    fun `R2 - dong bo ve su that chi khi o ranh, khong dung lai trang`() {
        val sync = SourceRoots.body(block, "fun sync()")
        val busy = sync.indexOf("if (bridge.geometryResizeBusy(target)) return")
        assertTrue(busy >= 0 && busy < sync.indexOf("intent.forget(target)"), sync)
        assertFalse(sync.contains("rebuild()"), "đồng bộ = đặt lại số, không dựng lại khối")
        val outcome = SourceRoots.body(block, "private fun onOutcome(outcome: CastResizeOutcome)")
        assertTrue(outcome.contains("CastResizeResult.FAILED") && outcome.contains("R.string.kachi_cast_geom_resize_failed"))
        assertTrue(outcome.contains("sync()"))
        assertTrue(block.contains("bridge.observeGeometryOutcome {"), "nghe kết cục lệnh khung")
        val cast = launcher("SettingsSectionsCast.kt")
        val live = SourceRoots.body(cast, "private fun liveStatus(anchor: View)")
        assertTrue(live.contains("state.geometryKey()") && live.contains("geometryBlock.sync()"), live)
        assertTrue(live.contains("if (key == shown && it != geometry) geometryBlock.sync()"), "bản ghim đổi ⇒ chỉ sync, không refreshStatus")
    }

    @Test
    fun `R3 - nhan giu gan vao dung hai nut cua hang −+`() {
        val add = SourceRoots.body(block, "private fun addEdgeSteppers(holder: LinearLayout, target: CastGeometryTarget)")
        assertTrue(add.contains("SettingsRepeatTouch.attachToStepper(stepper.view, minus, plus)"), add)
        // Hợp đồng hình dạng mà bộ gắn dựa vào: nhãn · − · số · + — chỉ hai nút bấm được, − trước +.
        val rows = launcher("SettingsRows.kt")
        val stepper = SourceRoots.body(rows, "fun stepperRow(label: String, valueText: String, onMinus: () -> Unit, onPlus: () -> Unit): Stepper")
        val order = listOf("addView(rowLabel(label))", "addView(minus)", "addView(value)", "addView(plus)").map { stepper.indexOf(it) }
        assertTrue(order.all { it >= 0 } && order == order.sorted(), "thứ tự con của hàng −/+ đổi ⇒ xem lại SettingsRepeatTouch: $order")
        assertFalse(stepper.contains("value.setOnClickListener") || stepper.contains("isClickable = true"), "ô số không được bấm được")
        val touch = launcher("SettingsRepeatTouch.kt")
        assertTrue(touch.contains("const val REPEAT_MS = 150L"), "R3: ~150 ms một bước")
        assertTrue(touch.contains("if (repeated) cancelClick(v, ev) else false"), "đã lặp thì nhả tay KHÔNG thêm một bước")
    }

    @Test
    fun `R4 - chip buoc 5 20 50 doc ghi qua cau theo ho so`() {
        val rebuild = SourceRoots.body(block, "fun rebuild()")
        assertTrue(rebuild.contains("step = bridge.geometryStep()"))
        assertTrue(rebuild.contains("CastGeometrySteps.OPTIONS.map") && rebuild.contains("bridge.setGeometryStep(step)"))
        assertTrue(geo.contains("putInt(CastGeometrySteps.KEY,"), "ghi đúng khoá theo hồ sơ")
        assertTrue(geo.contains("app.getSharedPreferences(ProfileScopeCluster.SIMPLE_CAST_FILE, Context.MODE_PRIVATE)"),
            "khoá bước nằm ở tệp mà ảnh chụp hồ sơ chụp (simple_cast_prefs)")
        assertEquals(ProfileScope.Scope.PROFILE, ProfileScope.scopeOf("cast_frame_step"))
    }

    @Test
    fun `R7 - chi toan cum duoc tran phai, moi o duoc tran duoi`() {
        val clamp = SourceRoots.body(geo, "private fun ClusterNavBridge.clampToBand(target: CastGeometryTarget, bounds: CastBounds): CastBounds {")
        assertTrue(clamp.contains("val maxRight = if (target.side == null) CastGeometryGuard.overflowMax(width) else f.right"), clamp)
        assertTrue(clamp.contains("CastGeometryGuard.overflowMax(height)"), clamp)
        // Lượt áp tự động (repin) dùng CÙNG trần tràn — không thì mỗi lần chiếu lại khung tràn bị kẹp mất.
        val apply = SourceRoots.body(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/CastGeometryController.kt"),
            "fun applyPinned(pkg: String, pinned: DisplayConfig?)",
        )
        assertTrue(apply.contains("CastGeometryGuard.overflowMax(w), CastGeometryGuard.overflowMax(h)"), apply)
    }

    @Test
    fun `chuoi moi du nam ngon ngu`() {
        val keys = listOf("kachi_cast_frame_step", "kachi_cast_geom_resize_failed", "kachi_cast_geom_overflow_note")
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { dir ->
            val xml = SourceRoots.text("src/main/res/$dir/strings_kachi.xml")
            keys.forEach { assertTrue(xml.contains("name=\"$it\""), "$dir thiếu $it") }
        }
    }
}
