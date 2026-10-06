package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-WAKE-SLOTCOUNT (2026-10-02) — `:wake` KHÔNG được quyết bằng state GIẢ ═══════════════════════════════════
 *
 * [ĐO ảnh owner 02/10, 2.85] bố cục tự vẽ 6 khung mà *"mở YouTube vào ô 6"* qua `:wake` trả *"bố cục hiện chỉ có 3 ô"*.
 * Gốc: `buildSession` đưa dispatcher `state = { grammar().homeState() }` — ảnh chụp chỉ mang hồ sơ + sổ địa chỉ; mọi
 * trường khác của `HomeUiState` là MẶC ĐỊNH (bố cục 3 ô · bố cục tự vẽ `null` · `CarStatus()` rỗng).
 *
 * Bài này canh bốn dây (quét SOURCE đã bỏ chú thích, cắt thân bằng [SourceRoots.body]):
 *  1. **Bảng đọc `state()` của dispatcher là ĐÓNG**: liệt kê đúng từng chỗ đọc ở ba tệp của chuỗi dispatcher. Thêm
 *     một chỗ đọc trường khác (`state().workspace`, `val st = state()`…) là ĐỎ — người thêm phải trả lời "trong
 *     `:wake` trường ấy thật hay giả?" trước khi sửa bảng này. Đưa nguyên lambda `state` sang lớp mới cũng ĐỎ.
 *  2. **Số ô** chỉ đọc từ `state()` ở mặc định in-process của `place`; `:wake` truyền `placeInSlot` đi relay.
 *  3. **Relay mang số ô thật** — Activity kiểm bằng `VoiceSlotPlace.decide` + state thật, ack kèm số ô; `:wake` giải mã.
 *  4. **Số liệu xe** trong `:wake` đọc TƯƠI: nhu cầu màn của `:wake` = rỗng (không phải `null` = "poll đọc hết").
 */
class VoiceWakeFakeStateContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val dispatcher by lazy { code("src/main/java/com/byd/clusternav/launcher/VoiceDispatcher.kt") }
    private val targets by lazy { code("src/main/java/com/byd/clusternav/launcher/VoiceTargetDispatch.kt") }
    private val controls by lazy { code("src/main/java/com/byd/clusternav/launcher/VoiceControlDispatch.kt") }
    private val factory by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessionFactory.kt") }
    private val relay by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeHomeRelay.kt") }
    private val entry by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceEntry.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val voiceWiring by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWiring.kt") }

    /**
     * Mọi chỗ đọc `state()`: kèm chuỗi thành viên theo sau (`state().carStatus.controls`), hoặc — khi `state()` được
     * đưa NGUYÊN cho một hàm — tên hàm bọc nó (`VoiceSlotPlace.slotCountOf(state())`). `val st = state()` ra `state()`
     * trần ⇒ không có trong bảng nào ⇒ ĐỎ (bí danh là cách lách bảng này).
     */
    private fun reads(src: String): List<String> =
        Regex("(\\w+\\.\\w+\\()?\\bstate\\(\\)((?:\\.\\w+)*)").findAll(src).map { m ->
            val (wrap, chain) = m.destructured
            if (chain.isEmpty() && wrap.isNotEmpty()) wrap + "state())" else "state()$chain"
        }.toList().sorted()

    // ══ (1) bảng đọc state() là ĐÓNG ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `bang doc state cua chuoi dispatcher la dong - them truong khac la do`() {
        // Trường THẬT trong `:wake` (ảnh chụp mang): profiles · savedPlaces. Trường GIẢ: còn lại — số ô ⇒ placeInSlot;
        // carStatus của câu hỏi số liệu + tốc độ cổng cốp/ca-pô ⇒ freshCar đọc tươi trước (test (4)). `carStatus.controls`
        // (gió đang AUTO?): 2.93 VOICE-WAKE-AUTOON — `readState(autoId)` đọc tươi TRƯỚC, ảnh chụp (`:wake` luôn rỗng, Activity
        // khi ô điều hoà không trên màn cũng rỗng) chỉ là đường lùi — canh ở test (4).
        assertEquals(
            listOf("VoiceSlotPlace.slotCountOf(state())", "state().carStatus", "state().profiles", "state().savedPlaces"),
            reads(dispatcher), "VoiceDispatcher đọc state() ở chỗ mới — trong `:wake` trường ấy thật hay GIẢ?",
        )
        assertEquals(listOf("state().savedPlaces"), reads(targets), "VoiceTargetDispatch đọc state() ở chỗ mới")
        assertEquals(
            listOf("state().carStatus.controls", "state().carStatus.drivetrain.speedKmh"),
            reads(controls), "VoiceControlDispatch đọc state() ở chỗ mới",
        )
        // Bảng trên chỉ ĐÓNG khi lambda `state` không chảy sang lớp nào ngoài ba tệp đã quét — xem [handOffs].
        assertEquals(
            listOf("VoiceControlDispatch(state = state)", "VoiceTargetDispatch(state = state)"),
            handOffs(dispatcher), "VoiceDispatcher chuyển lambda `state` cho chỗ mới — quét chỗ ấy vào bảng này",
        )
        assertEquals(emptyList<String>(), handOffs(targets), "VoiceTargetDispatch chuyển lambda `state` đi tiếp")
        assertEquals(emptyList<String>(), handOffs(controls), "VoiceControlDispatch chuyển lambda `state` đi tiếp")
    }

    /**
     * [SOÁT lượt 2 · 02/10] [reads] chỉ thấy LỜI GỌI `state()`. Đưa NGUYÊN lambda cho một lớp khác (`Foo(state = state)`,
     * `Foo(state)`, `val s = state`, `::state`) là lách bảng: lớp ấy đọc gì cũng không ai quét. Trả về tên lớp nhận lambda
     * qua tham số tên, cộng mọi chỗ nhắc `state` mà không gọi nó (ngoài dòng khai báo) — kể cả một `state = state` không
     * nhận ra được lớp nhận.
     */
    private fun handOffs(src: String): List<String> {
        val named = Regex("(\\w+)\\((?:[^()]*?,)?\\s*state = state\\b").findAll(src).map { it.groupValues[1] + "(state = state)" }.toList()
        val unnamed = Regex("\\bstate = state\\b").findAll(src).count() - named.size
        val rest = src.replace("private val state: () -> HomeUiState", "").replace(Regex("\\bstate = state\\b"), "")
        val bare = Regex("\\bstate\\b(?!\\s*\\()").findAll(rest).map { "state không gọi @" + it.range.first }.toList()
        return (named + List(unnamed) { "state = state (lớp nhận ?)" } + bare).sorted()
    }

    // ══ (2) số ô: chỉ mặc định in-process đọc state; :wake giao Activity ════════════════════════════════════════

    @Test
    fun `so o chi doc tu state o mac dinh in-process, runOpenApp khong tu tinh`() {
        val place = SourceRoots.body(dispatcher, "private val place: (Int, String) -> SlotPlaceOutcome")
        assertTrue(place.contains("placeInSlot ?: { idx, pkg ->"), "bề mặt truyền placeInSlot (`:wake`) phải THẮNG mặc định")
        assertTrue(place.contains("VoiceSlotPlace.decide(idx, VoiceSlotPlace.slotCountOf(state())) { assignAppToSlot(idx, pkg) }"),
            "mặc định in-process: cùng luật dải ô với Activity, rồi CHÍNH assignAppToSlot (y nguyên 2.85)")
        val open = SourceRoots.body(dispatcher, "private fun runOpenApp(")
        assertTrue(open.contains("place(slot - 1, pkg)"), "1-based → 0-based ở ĐÚNG một chỗ, qua `place`")
        assertFalse(open.contains("state()"), "runOpenApp không được tự đọc bố cục — trong `:wake` đó là bố cục giả")
        assertFalse(open.contains("EffectiveLayout"), "runOpenApp không được tự tính số ô")
        // kachi-i18n-zh-th-ms T2: thêm `lang` (tiếng GIỌNG NÓI của cầu) — số ô vẫn là `out.slotCount` của nơi giữ bố cục.
        assertTrue(open.contains("is SlotPlaceOutcome.OutOfRange -> VoiceReply.slotOutOfRange(shown, out.slotCount, lang)"),
            "câu 'chỉ có N ô' phải mang N do nơi giữ bố cục trả về")

        val build = SourceRoots.body(factory, "internal fun VoiceWakeService.buildSession(): VoiceSession {")
        assertTrue(build.contains("state = { grammar().homeState() }"), "tiền đề: state của `:wake` là ảnh chụp (giả ngoài 3 trường)")
        assertTrue(build.contains("placeInSlot = { idx, pkg -> relay.performSlot(idx, pkg) }"),
            "`:wake` phải giao NGUYÊN lệnh gắn ô cho Activity — thiếu dòng này là quay lại 'chỉ có 3 ô'")
        assertFalse(build.contains("assignAppToSlot ="),
            "`:wake` không được nối assignAppToSlot: đường ấy chỉ chạy qua mặc định `place` — tức kiểm dải bằng state GIẢ")
        assertTrue(voiceWiring.contains("placeInSlot: ((Int, String) -> SlotPlaceOutcome)? = null"))
        assertTrue(SourceRoots.body(voiceWiring, "fun dispatcher(").contains("placeInSlot = placeInSlot,"),
            "bộ dây chung phải chuyển placeInSlot xuống dispatcher — nuốt nó là `:wake` lặng lẽ về mặc định")
    }

    // ══ (3) relay mang số ô thật ══════════════════════════════════════════════════════════════════════════════

    @Test
    fun `relay - Activity kiem bang state that va ack so o, wake giai ma`() {
        val perform = SourceRoots.body(entry, "fun perform(action: VoiceHomeAction, arg: String?): VoiceHomeRelay.Ack = when (action) {")
        assertTrue(perform.contains("VoiceHomeRelay.ackOf(VoiceSlotPlace.decide(s.slot, slotCount()) { assignAppToSlot(s.slot, s.pkg) })"),
            "Activity: cùng luật dải ô với in-process, rồi CHÍNH lambda assignAppToSlot")
        assertTrue(wiring.contains("slotCount = { VoiceSlotPlace.slotCountOf(state()) }"),
            "số ô của Activity phải từ state THẬT của màn chính (`state` của voiceSession = viewModel.uiState)")
        val ack = SourceRoots.body(entry, "fun ackHome(ctx: Context, nonce: String, ack: VoiceHomeRelay.Ack)")
        assertTrue(ack.contains(".putExtra(EXTRA_HOME_ACTION_SLOTS, ack.outOfRangeSlots)"), "ack phải mang số ô thật")
        assertTrue(ack.contains(".putExtra(EXTRA_HOME_ACTION_DONE, ack.done)"))

        val slot = SourceRoots.body(relay, "fun performSlot(slot: Int, pkg: String): SlotPlaceOutcome {")
        assertTrue(slot.contains("VoiceHomeRelay.slotOutcome(exchange(VoiceHomeAction.ASSIGN_APP_TO_SLOT, VoiceHomeRelay.encodeSlot(slot, pkg)))"))
        assertFalse(slot.contains("slotCount"), "`:wake` không được tự kiểm dải — nó không có bố cục thật")
        // [SOÁT lượt 3 · 02/10] ✓ và số ô chỉ đến từ ack của Activity (qua `slotOutcome`). [ĐO phá thử] chèn
        // `if (slot > 1000) return SlotPlaceOutcome.Placed` vào `performSlot` ⇒ bản trước vẫn xanh.
        assertFalse(slot.contains("Placed") || slot.contains("OutOfRange"),
            "`:wake` không được tự bịa kết quả gắn ô — ✓ / 'chỉ có N ô' chỉ đến từ ack của Activity")
        val ex = SourceRoots.body(relay, "private fun exchange(action: VoiceHomeAction, arg: String?): VoiceHomeRelay.Ack? {")
        assertTrue(ex.contains("slots.set(i.getIntExtra(VoiceEntry.EXTRA_HOME_ACTION_SLOTS, 0))"), "`:wake` phải đọc số ô từ ack")
    }

    // ══ (4) số liệu xe trong :wake đọc tươi ═══════════════════════════════════════════════════════════════════

    @Test
    fun `wake - nhu cau man rong de cau hoi so lieu va cong toc do doc TUOI, khong roi ve CarStatus rong`() {
        val build = SourceRoots.body(factory, "internal fun VoiceWakeService.buildSession(): VoiceSession {")
        assertTrue(build.contains("screenless = true,"),
            "`:wake` để nhu cầu `null` ⇒ refreshForRead luôn null ⇒ đọc CarStatus() rỗng của homeState()")
        // Không chạm đồ thị DI trong tệp wake (bài canh `VoiceWakeIsolationContractTest`): việc đặt nhu cầu nằm ở bộ dây
        // chung, LƯỜI trong `freshCar` — cùng chỗ đã gọi `AppContainer.get(ctx).refreshForRead` từ trước bản này.
        val wiringFn = SourceRoots.body(voiceWiring, "fun dispatcher(")
        assertTrue(voiceWiring.contains("screenless: Boolean = false,"), "mặc định phải là hành vi 2.85 (bề mặt có màn)")
        val set = wiringFn.indexOf("if (screenless && c.carDemand.get() == null) c.carDemand.set(emptySet())")
        assertTrue(set >= 0, "tiến trình không màn: nhu cầu màn = RỖNG (sự thật), không phải null (= 'poll đọc hết')")
        // 2.93 VOICE-READ-STALE-BG (đổi chốt có lý do): lượt đọc tươi đi `readFresh` — `refreshForRead` + (màn chính đã khuất,
        // nhu cầu `null`) đọc ĐÚNG MỘT datum qua `withSoloIfIdle`. `:wake` đặt nhu cầu rỗng trước ⇒ nhánh solo không bao giờ chạy ở đó.
        assertTrue(set in 0 until wiringFn.indexOf("c.readFresh(id)"), "nhu cầu phải đặt TRƯỚC lượt đọc tươi")
        val readFresh = SourceRoots.body(code("src/main/java/com/byd/clusternav/AppContainer.kt"), "fun readFresh(id: String)")
        assertTrue(readFresh.contains("refreshForRead(id) ?: carDemand.withSoloIfIdle(setOf(id)) { carStatusRepository.refreshNow() }"),
            "tiến trình chính, màn khuất (wake TẮT, app khác toàn màn) ⇒ câu hỏi số liệu phải đọc TƯƠI, không ảnh chụp lúc màn còn hiện")
        // 2.93 VOICE-WAKE-AUTOON — cờ AUTO đọc TƯƠI qua đúng cửa readState (cùng lượt mốc mức), ảnh chụp chỉ là đường lùi.
        val auto = SourceRoots.body(controls, "private fun autoState(def: ControlDef): Int?")
        assertTrue(auto.contains("runCatching { control().readState(def.autoId) }.getOrNull() ?: state().carStatus.controls[def.autoId]"),
            "trong `:wake` ảnh chụp controls luôn rỗng ⇒ phải đọc tươi cờ AUTO, nếu không 'tăng gió' lúc AUTO đi SetLevel thay vì LeaveAuto")
        assertTrue(controls.contains("ClimateAuto.autoOnFromControl(autoState(def))"), "bảng quyết định phải nhận cờ đọc tươi")
        // Hai chỗ đọc carStatus mà kết quả là SỐ cho người nghe / CỔNG an toàn: phải hỏi freshCar TRƯỚC, state chỉ là đường lùi.
        assertTrue(dispatcher.contains("runCatching { freshCar(i.datumId) }.getOrNull() ?: state().carStatus"))
        assertTrue(Regex("freshCar\\(\"speed\"\\)[^\\n]*\\n\\s*\\?: state\\(\\)\\.carStatus\\.drivetrain\\.speedKmh").containsMatchIn(controls),
            "cổng cốp/ca-pô phải đọc tốc độ TƯƠI trước — trong `:wake` state().carStatus là CarStatus() rỗng (fail-open)")
        // refreshForRead: nhu cầu khác `null` và datum không trong nhu cầu ⇒ đọc NGAY đúng datum ấy.
        val refresh = SourceRoots.body(code("src/main/java/com/byd/clusternav/AppContainer.kt"), "fun refreshForRead(id: String)")
        assertTrue(refresh.contains("val want = carDemand.get() ?: return null") && refresh.contains("carDemand.withExtra(setOf(id)) { carStatusRepository.refreshNow() }"),
            "tiền đề của dây (4) đổi — xem lại vì sao `:wake` đặt nhu cầu rỗng")
    }

    /**
     * [SOÁT lượt 3 · 02/10] *"Đường Activity không đổi"* chỉ đúng khi CHỈ `:wake` rời mặc định của hai tham số mới. Bài
     * *"in-process - cung ba ca"* chứng minh MẶC ĐỊNH đúng, nhưng không gì chặn một bề mặt chung tiến trình với màn
     * (`KachiHomeWiring.voiceSession` · `VoiceTextConsole` · `TestBridgeHooks`) truyền chúng: `screenless = true` ở tiến
     * trình chính đè `null` của `CarDataDemand.Holder` (= lượt poll đầu đọc ĐỦ khi màn quay lại) bằng tập rỗng;
     * `placeInSlot` ở đó là mở đường gắn ô thứ hai. [ĐO phá thử] thêm `screenless = true` vào `voiceSession` ⇒ bản trước
     * vẫn xanh. Chuyển tiếp nguyên (`placeInSlot = placeInSlot` trong `VoiceWiring`) không tính.
     */
    @Test
    fun `chi wake roi mac dinh placeInSlot va screenless - be mat chung tien trinh voi man giu nguyen`() {
        val leave = Regex("""\b(placeInSlot|screenless)\s*=(?!=)\s*+(?!\1\b)""")
        val passers = mainFiles().filter { (_, src) -> leave.containsMatchIn(src) }.map { it.first }.distinct().sorted()
        assertEquals(listOf("VoiceWakeSessionFactory.kt"), passers,
            "chỉ `:wake` (không màn, state = ảnh chụp) được rời mặc định placeInSlot/screenless — bề mặt chung tiến trình với màn giữ 2.85")
    }

    /** Mọi tệp mã chính đã bỏ chú thích bằng bộ quét dùng chung (giữ nguyên string literal). */
    private fun mainFiles(): List<Pair<String, String>> = SourceRoots.moduleSourceRoots().flatMap { root ->
        Files.walk(root).use { s ->
            s.filter { it.toString().endsWith(".kt") }
                .map { it.fileName.toString() to KotlinSource.stripComments(it.toFile().readText()) }.toList()
        }
    }

    @Test
    fun `cac tep cua luot nay khong vuot tran 500 dong`() {
        listOf(
            "src/main/java/com/byd/clusternav/launcher/VoiceDispatcher.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt",
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceEntry.kt",
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeHomeRelay.kt",
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeSessionFactory.kt",
            "src/main/java/com/byd/clusternav/launcher/voice/VoiceWiring.kt",
        ).forEach { rel -> val n = SourceRoots.text(rel).lines().size; assertTrue(n <= 500, "$rel dài $n dòng — trần là 500") }
    }
}
