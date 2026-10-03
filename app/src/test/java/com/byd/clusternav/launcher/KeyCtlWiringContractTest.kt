package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · R-KC — phím gán nút xe: bài canh NỐI DÂY (quét source, [SourceRoots.body]) ════════════════════════
 *
 * Phần thuần có bài thật ở `:core` (`KeyCtlTargetTest` · `KeyCtlPlanTest`), hành vi thi hành có bài chạy thật ở
 * `KeyCtlSafetyTest`. Ở đây khoá các mắt xích Android mà JVM không dựng được — đúng chỗ CLAUDE.md §8 đã trả giá
 * (`CastShell.evictVd` viết cẩn thận, compile sạch, **0 call site**):
 *  KC1 — đích `ctl:` rẽ TRƯỚC mọi nhánh mở app trong lối phát đích duy nhất;
 *  KC2 — thi hành = `VoiceControlDispatch` (không đường ghi thứ hai), trên làn nền của ô đơn, `onKeyEvent` không chặn;
 *  KC4 — chống dồn có mặt trên đường chạy (cả cạnh đầu lẫn cạnh cuối);
 *  KC5 — Cài đặt sinh nhóm + việc từ `:core`, nhãn đích `ctl:` không hiện mã thô; chuỗi vi/en đủ cặp;
 *  KC6 — phản hồi là toast một-tại-một-thời-điểm, không đọc thành tiếng.
 */
class KeyCtlWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val base = "src/main/java/com/byd/clusternav"
    private val launcher by lazy { code("$base/modules/voicekey/AssistantLauncher.kt") }
    private val dispatch by lazy { code("$base/modules/voicekey/KeyCtlDispatch.kt") }
    private val section by lazy { code("$base/launcher/SettingsSectionsKeys.kt") }
    private val a11y by lazy { code("$base/modules/navaccess/NavAccessibilityService.kt") }

    @Test
    fun `KC1 - lan phat dich duy nhat re ctl TRUOC moi nhanh mo app`() {
        val fn = SourceRoots.body(launcher, "fun launch(ctx: Context, spec: String): Boolean")
        val ctl = fn.indexOf("if (KeyCtlTargets.isCtl(spec)) return KeyCtlDispatch.fire(ctx, spec)")
        assertTrue(ctl >= 0, "đích nút xe phải rẽ sang KeyCtlDispatch")
        assertTrue(ctl < fn.indexOf("TARGET_KACHI_VOICE") && ctl < fn.indexOf("getLaunchIntentForPackage"),
            "rẽ SAU nhánh mở app ⇒ `ctl:fan:+1` bị coi là tên gói và chết im lặng")
        // Lối vào phím không đổi: onKeyEvent → matcher → AssistantLauncher.launch (một chỗ phát đích).
        val key = SourceRoots.body(a11y, "override fun onKeyEvent(event: KeyEvent?): Boolean")
        assertTrue(key.contains("AssistantLauncher.launch(app, spec)"))
        assertTrue(key.contains("return if (decision.consume) true else super.onKeyEvent(event)"),
            "phím đã gán phải bị NUỐT (owner: ghi đè chức năng cũ)")
    }

    @Test
    fun `KC2 - thi hanh la VoiceControlDispatch tren lan nen cua o don, khong duong ghi rieng`() {
        val sub = SourceRoots.body(dispatch, "private fun submit(app: Context, f: KeyCtlThrottle.Step.Fire, why: String)")
        assertTrue(sub.contains("MacroExec.submitSerial(ControlTileWrite.LANE)"), "HAL xuống làn nền — onKeyEvent có hạn 500 ms")
        val mk = SourceRoots.body(dispatch, "private fun runner(app: Context): KeyCtlRunner")
        assertTrue(mk.contains("VoiceControlDispatch("), "cổng tốc độ cốp · AUTO · 'xe này không có' nằm ở đó")
        assertTrue(mk.contains("control = { AppContainer.get(app).carControl }"), "cùng cổng xe (WakeOnWriteControl) với ô/giọng nói")
        assertTrue(mk.contains("freshCar = { id -> fresh(app, id) }"), "cổng tốc độ phải đọc TƯƠI, không ảnh chụp cũ")
        val run = SourceRoots.body(dispatch, "fun run(f: KeyCtlThrottle.Step.Fire)")
        assertTrue(run.contains("KeyCtlPlan.of(def, f.target, f.count)"))
        assertTrue(run.contains("controls.run(o.intent)"))
        // Không có lệnh ghi HAL nào viết tay trong tệp phím.
        listOf(".toggle(", ".cover(", ".coverLevel(", ".select(", "actByKind(", "HalBindingTable", "featureSet", "namedInt")
            .forEach { assertFalse(dispatch.contains(it), "đường ghi thứ hai trong KeyCtlDispatch: $it") }
        assertFalse(Regex("""(port\(\)|carControl|control\(\))\s*\.\s*(step|press)\(""").containsMatchIn(dispatch),
            "đường ghi thứ hai (step/press) trong KeyCtlDispatch")
        val fire = SourceRoots.body(dispatch, "fun fire(ctx: Context, spec: String): Boolean")
        assertFalse(fire.contains("carControl") || fire.contains("readState"), "fire() chạy trong onKeyEvent — cấm chạm HAL")
        val fresh = SourceRoots.body(dispatch, "private fun fresh(app: Context, id: String): CarStatus?")
        assertTrue(fresh.contains("c.refreshForRead(id) ?: c.carDemand.withSoloIfIdle(setOf(id)) { c.carStatusRepository.refreshNow() }"),
            "màn nhà khuất (poll đã dừng) ⇒ đọc TƯƠI đúng một datum — luật ở CarDataDemand.Holder.withSoloIfIdle (:core)")
    }

    @Test
    fun `KC4 - chong don co mat ca canh dau lan canh cuoi`() {
        val fire = SourceRoots.body(dispatch, "fun fire(ctx: Context, spec: String): Boolean")
        assertTrue(fire.contains("throttle.press(t, SystemClock.uptimeMillis())"))
        assertTrue(fire.contains("is KeyCtlThrottle.Step.Fire -> submit(app, s"))
        assertTrue(fire.contains("main.postAtTime("), "cạnh cuối phải được hẹn — không thì nấc gộp mất")
        assertTrue(fire.contains("throttle.flush(t.controlId, SystemClock.uptimeMillis())"))
        assertTrue(fire.contains("KeyCtlTargets.decode(spec)"), "mã hỏng/không còn ⇒ báo, không bắn")
    }

    @Test
    fun `KC5 - Cai dat sinh nhom va viec tu core, nhan ctl khong hien ma tho`() {
        val add = SourceRoots.body(section, "private fun addBinding()")
        assertTrue(add.contains("pickTarget {"), "bước 2 phải qua bộ chọn loại đích")
        val pick = SourceRoots.body(section, "private fun pickTarget(onSpec: (String) -> Unit)")
        assertTrue(pick.contains("KeyCtlTargets.groups()"), "nhóm SINH từ registry, không chép tay")
        assertTrue(pick.contains("pickApp(onSpec)") && pick.contains("pickControl(groups[kind - 1], onSpec)"))
        val ctl = SourceRoots.body(section, "private fun pickControl(group: KeyCtlGroup, onSpec: (String) -> Unit)")
        assertTrue(ctl.contains("KeyCtlTargets.displayLabel(it)") && ctl.contains("onSpec(group.targets[i].spec)"))
        val lbl = SourceRoots.body(section, "private fun targetLabel(spec: String, targets: List<TargetOption>): String")
        assertTrue(lbl.contains("KeyCtlTargets.displayLabelOf(spec)"), "dòng đã gán hiện 'Gió +1', không 'ctl:fan:+1'")
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        listOf("kachi_keys_pick_kind", "kachi_keys_kind_apps", "kachi_keys_pick_action").forEach { k ->
            assertTrue("name=\"$k\"" in vi && "name=\"$k\"" in en, "thiếu chuỗi $k ở vi hoặc en")
        }
        assertEquals(1, Regex("%1\\\$s").findAll(Regex("name=\"kachi_keys_pick_action\">([^<]*)<").find(en)!!.value).count())
    }

    @Test
    fun `KC6 - phan hoi la toast mot tai mot thoi diem, khong doc thanh tieng`() {
        val t = SourceRoots.body(dispatch, "private fun toast(app: Context, text: String)")
        assertTrue(t.contains("lastToast?.cancel()"), "núm vặn sinh nhiều câu — xếp hàng thì câu cuối trễ cả chục giây")
        listOf("VoiceSpeaker", "TextToSpeech", "speak(").forEach { assertFalse(dispatch.contains(it), "phím không đọc thành tiếng: $it") }
    }

    /** CLAUDE.md §8 — hàm mới phải có call site ngoài định nghĩa (đếm trên mã đã bỏ chú thích). */
    @Test
    fun `call site cua cac ham moi`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.map { f ->
                // Bỏ chú thích như [SourceRoots.codeOf]: token nằm trong KDoc không phải một call site.
                f.readText().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                    .replace(Regex("(?m)//.*$"), "")
            }.toList()
        }.joinToString("\n")
        mapOf(
            "KeyCtlDispatch.fire(" to 1, "KeyCtlTargets.groups(" to 1, "KeyCtlTargets.displayLabelOf(" to 1,
            "KeyCtlPlan.of(" to 1, "KeyCtlPlan.unreadableReply(" to 1, "KeyCtlPlan.invalidReply(" to 2,
            "throttle.press(" to 1, "throttle.flush(" to 1,
        ).forEach { (token, min) ->
            assertTrue(Regex(Regex.escape(token)).findAll(all).count() >= min, "$token: thiếu call site (§8)")
        }
    }
}
