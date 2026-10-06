package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
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
        // SOÁT vòng 1 · P1 (đổi chốt có lý do): `KeyCtlPlan.of` nay BẮT BUỘC nguồn vận tốc — CHÍNH nguồn của cổng thi hành
        // (`VoiceControlDispatch.speedKmh`), để Đảo cốp từ trí nhớ lúc xe chạy lùi về ĐÓNG thay vì kẹt MỞ-bị-chặn.
        assertTrue(run.contains("KeyCtlPlan.of(def, f.target, f.count, speedKmh = controls::speedKmh)"))
        assertTrue(run.contains("controls.run(o.intent)"))
        // Không có lệnh ghi HAL nào viết tay trong tệp phím.
        listOf(".toggle(", ".cover(", ".coverLevel(", ".select(", "actByKind(", "HalBindingTable", "featureSet", "namedInt")
            .forEach { assertFalse(dispatch.contains(it), "đường ghi thứ hai trong KeyCtlDispatch: $it") }
        assertFalse(Regex("""(port\(\)|carControl|control\(\))\s*\.\s*(step|press)\(""").containsMatchIn(dispatch),
            "đường ghi thứ hai (step/press) trong KeyCtlDispatch")
        val fire = SourceRoots.body(dispatch, "fun fire(ctx: Context, spec: String): Boolean")
        assertFalse(fire.contains("carControl") || fire.contains("readState"), "fire() chạy trong onKeyEvent — cấm chạm HAL")
        val fresh = SourceRoots.body(dispatch, "private fun fresh(app: Context, id: String): CarStatus?")
        // 2.93 VOICE-READ-STALE-BG (đổi chốt có lý do): biểu thức dời NGUYÊN VĂN sang `AppContainer.readFresh` để giọng nói
        // tiến trình chính dùng CHUNG (DRY, CLAUDE.md §4.1) — chốt canh cả lời gọi ở đây lẫn thân hàm ở AppContainer.
        assertTrue(fresh.contains("AppContainer.get(app).readFresh(id)"), "phím gán nút phải đọc qua cửa đọc-tươi dùng chung")
        val readFresh = SourceRoots.body(SourceRoots.codeOf("src/main/java/com/byd/clusternav/AppContainer.kt"), "fun readFresh(id: String)")
        assertTrue(readFresh.contains("refreshForRead(id) ?: carDemand.withSoloIfIdle(setOf(id)) { carStatusRepository.refreshNow() }"),
            "màn nhà khuất (poll đã dừng) ⇒ đọc TƯƠI đúng một datum — luật ở CarDataDemand.Holder.withSoloIfIdle (:core)")
        // Senior review 2.93 Pass 1 · [P2]: hộp nhu cầu chỉ giữ MỘT tập ghim, mà cửa này có hai người gọi trên hai luồng (giọng
        // nói ở luồng vẽ · phím ở làn nền) ⇒ cả biểu thức phải nằm TRONG khoá, nếu không lượt sau ghi đè ghim của lượt trước và
        // cổng tốc độ của cốp đọc ảnh chụp cũ.
        assertTrue(readFresh.trimStart().startsWith("= synchronized(freshLock) {"),
            "readFresh phải khoá TOÀN BỘ lượt ghim + đọc (một người ghim tại một lúc): $readFresh")
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
        // 2.93 · CAMERA-ON-DEMAND: loại đích thứ hai là *Camera theo yêu cầu* ⇒ nhóm xe dời một chỉ số (kind − 2).
        assertTrue(pick.contains("pickApp(onSpec)") && pick.contains("pickControl(groups[kind - 2], onSpec)"))
        assertTrue(pick.contains("1 -> pickCamera(onSpec)"), "loại đích camera phải nối vào bộ chọn camera")
        val cam = SourceRoots.body(section, "private fun pickCamera(onSpec: (String) -> Unit)")
        assertTrue(cam.contains("CameraDemand.KEY_OPS") && cam.contains("CameraDemand.keySpec(ops[i])"),
            "đích camera SINH từ `:core` (danh sách + mã bền), không chép tay `cam:…`")
        assertTrue("\"cam:" !in section, "mã `cam:` không được chép trần trong Cài đặt")
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

    /**
     * 2.87 · R-FL2 — ô trên màn, phím Đảo và giọng nói dùng CHUNG MỘT bảng lệnh cuối (`ControlLastSent.shared`). Hành vi
     * có bài chạy thật (`KeyCtlSafetyTest.o va phim dung chung mot bang lenh cuoi`); ở đây khoá các mắt xích mà bài chạy
     * không thấy: ô không có bản riêng, phím không tiêm bảng khác, ô VẼ LẠI khi bảng đổi bởi phím/giọng nói.
     */
    @Test
    fun `R-FL2 - o, phim, giong noi dung chung mot bang lenh cuoi`() {
        val state = code("$base/launcher/ControlTileState.kt")
        assertTrue(state.contains("private val sent: ControlLastSent = ControlLastSent.shared"), "ô phải đọc bảng dùng chung của tiến trình")
        listOf("fun isOn(id: String): Boolean = sent.index(id) > 0", "fun sel(id: String): Int = sent.index(id)",
            "fun setOn(id: String, v: Boolean) { sent.record(id, if (v) 1 else 0) }", "fun setSel(id: String, i: Int) { sent.record(id, i) }",
        ).forEach { assertTrue(state.contains(it), "ControlTileState phải đi QUA ControlLastSent: $it") }
        val run = SourceRoots.body(dispatch, "fun run(f: KeyCtlThrottle.Step.Fire)")
        assertFalse(run.contains("ControlLastSent("), "phím không được dựng bảng riêng — mặc định của KeyCtlPlan.of là bảng dùng chung")
        val plan = code("src/main/java/com/byd/clusternav/launcher/KeyCtlPlan.kt")
        assertTrue(plan.contains("memory: ControlLastSent = ControlLastSent.shared"))
        // Giọng nói + phím ghi bảng ở tầng thi hành, CHỈ khi ok — kể cả COVER (trước 2.87 nhánh COVER bỏ qua).
        val vcd = SourceRoots.body(code("$base/launcher/VoiceControlDispatch.kt"), "fun finish(ok: Boolean)")
        assertTrue(vcd.contains("if (ok) when (def.kind)") && vcd.contains("ControlKind.SELECT, ControlKind.COVER -> st.setSel(def.id, arg)"))
        // Ô vẽ lại khi bảng đổi bởi phím/giọng nói (nút không readKey: nhịp đọc xe không có số để so).
        val factory = code("$base/launcher/ControlTileFactory.kt")
        assertTrue(SourceRoots.body(factory, "private fun look(").contains("TileResync.drew(tile, value)"), "look là cửa DUY NHẤT ghi hình đã vẽ")
        listOf("private fun tileToggle(", "private fun tileCover(", "private fun tileSelect(").forEach { sig ->
            assertTrue(SourceRoots.body(factory, sig).contains("TileResync.stale(tile,"), "$sig: refresh phải so hình với bảng lệnh cuối")
        }
    }

    /** CLAUDE.md §8 — hàm mới phải có call site ngoài định nghĩa (đếm trên mã đã bỏ chú thích). */
    @Test
    fun `call site cua cac ham moi`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.map { f ->
                // Bỏ chú thích bằng ĐÚNG bộ quét của [SourceRoots.codeOf]: token nằm trong KDoc không phải một call site.
                KotlinSource.stripComments(f.readText())
            }.toList()
        }.joinToString("\n")
        mapOf(
            "KeyCtlDispatch.fire(" to 1, "KeyCtlTargets.groups(" to 1, "KeyCtlTargets.displayLabelOf(" to 1,
            // 2.87 · R-FL2: `KeyCtlPlan.unreadableReply` GỠ cùng ca `Unreadable` (Đảo/Kế tiếp lùi về lệnh cuối) ⇒ bỏ khỏi
            // danh sách; `invalidReply` nay thêm một chỗ gọi (`Outcome.Invalid`).
            "KeyCtlPlan.of(" to 1, "KeyCtlPlan.invalidReply(" to 3,
            "throttle.press(" to 1, "throttle.flush(" to 1,
            "ControlLastSent.shared" to 2, "TileResync.drew(" to 1, "TileResync.stale(" to 3,
        ).forEach { (token, min) ->
            assertTrue(Regex(Regex.escape(token)).findAll(all).count() >= min, "$token: thiếu call site (§8)")
        }
    }
}
