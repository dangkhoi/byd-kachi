package com.byd.clusternav.launcher

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * ═══ PERF 3.1.5 — TẢI HAL CẢ HAI NHỊP trên màn THẬT (spec/diag `perf-315-2026-10-14.md`) ═══════════════════════════════════
 *
 * `TyreChipTest` đo NHỊP CHẬM; bài này đo **cả vòng** như [CarStatusRepository] chạy: mỗi giây một lượt nhanh (khi
 * [CarDataAdapter.fastNeeded]) + mỗi 10 s một lượt chậm, nhu cầu công bố ĐÚNG như `KachiHomeWiring.collectHome`
 * ([CarDataDemand.of] + [CarDataDemand.controlsOf]). Gateway trả lời MỌI getter (trần trên: không datum nào nguội vì
 * *"xe không có"*), màu cụm lốp trắng — đếm đúng như `KachiPerf.Counter.HAL_READ` (mọi lượt vào cửa gateway).
 */
class HalLoadBudgetTest {

    private class AnswerAll : HalGateway by FakeHalGateway() {
        var reads = 0
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? { reads++; return if (method == "getTyrePressureValue") "250" else "1" }
        override fun featureGet(deviceFqn: String, id: Int): String? { reads++; return "int=1" }
        override fun settingGet(key: String): String? { reads++; return "1" }
        override fun localGet(target: String, method: String, arg: Int?): String? { reads++; return "1" }
        override fun featureIdByName(constName: String): Int? = 8000 + (constName.hashCode() and 0xFFFF)
    }

    /** Lượt đọc/phút ổn định: 1 phút làm nóng rồi đo 10 phút, nhịp nhanh 1 s · chậm 10 s (mặc định của repository). */
    private fun perMinute(state: HomeUiState, hidden: Set<String> = emptySet()): Int {
        val gw = AnswerAll()
        var now = 0L
        val holder = CarDataDemand.Holder()
        holder.publish(state, hidden)
        val adapter = CarDataAdapter(HalBindingTable(gw), holder::get, clock = { now }, controlDemand = holder::controls)
        var s = CarStatus()
        var slow = 0
        fun second(t: Int) {
            if (t % 10 == 0) { val r0 = gw.reads; s = adapter.readSlow(s); slow += gw.reads - r0 }
            if (adapter.fastNeeded()) s = adapter.readFast(s)
            if (t % 3 == 0 && adapter.bodyNeeded()) s = adapter.readBody(s)   // 3.1.6 — nhịp thân xe 3 s (cửa)
            now += 1_000
        }
        for (t in 0 until 60) second(t)
        gw.reads = 0; slow = 0
        for (t in 0 until 600) second(t)
        println("  ${state.workspace.preset} khuất/không vẽ=$hidden: chậm=${slow / 10} nhanh=${(gw.reads - slow) / 10} datum=${holder.get()?.size} nút=${holder.controls()}")
        return gw.reads / 10
    }

    private val qa2 = HomeUiState(
        workspace = WorkspaceState.of(LayoutPreset.ONE, SlotContent.Widget("w_car3d"), SlotContent.Widget("g_tyres"), SlotContent.Widget("g_windows")),
        dock = DockConfig(enabled = listOf("trunk", "readl", "pm25", "seatc", "temp", "fan", "win_lf"), visible = false),
    )
    private val defaultScreen = HomeUiState(workspace = WorkspaceState.DEFAULT)

    /**
     * [ĐO off-car, trần trên] trước 3.1.5: mặc định 600/phút (chậm 240 + nhanh 360 = 6 nút × 60) · QA2 594 (ô ẩn `g_tyres`/`g_windows`
     * + thanh nút ẨN 7 mã vẫn đọc). Sau: ô ẩn + thanh ẩn không đọc; xe 3D không vẽ (3.1.8: chờ/không khả thi — không còn bảng 2D) không đọc gì.
     * ⚠ K1 (150) KHÔNG đạt được ở màn mặc định: riêng nhịp NHANH của ô nút là 6 × 60 = 360 — luật owner #5 (09-21, *"không
     * realtime"*: nút đọc mỗi 1 s) — ghi ở backlog `CAR3D-K1-BUDGET` cho owner chốt; bài này GHIM số đo để không phình âm thầm.
     */
    @Test
    fun `PERF 315 tai HAL ghim - o an va thanh an khong doc, xe 3D khong ve thi khong doc`() {
        val d = perMinute(defaultScreen)
        val q = perMinute(qa2)
        val qIdle = perMinute(qa2, setOf("w_car3d"))
        println("HAL/phút mặc định=$d · QA2=$q · QA2 xe 3D không vẽ=$qIdle")
        // 3.1.6 — nhịp thân xe (cửa 3 s thay 10 s, owner 15/10 *"mở/đóng cửa nhận chậm"*): +56/phút ĐÚNG khi cửa đang hiện
        // (4 × 20 − 4 × 6), ghi tường minh — không phình âm thầm. Hai màn dưới đều bày xe 3D (có cửa). Lý do + số xe: KDoc
        // `CarDataDemand.BODY_IDS` · `docs/diagnostics/oncar-bugs-2026-10-15.md` §3.
        // 3.1.6 · R32 — CỐP (`getDoorState(6)`) đi chung nhịp thân xe: +20/phút (1 lượt / 3 s) khi xe 3D/bảng cửa/ô cốp hiện; nút
        // `trunk` lấy luôn số ấy (KHÔNG đọc ở nhịp nhanh 1 s — bỏ dòng mirror ở `CarDataAdapter.readControls` thì +60 ⇒ đỏ).
        assertTrue(d <= 600 + 56 + 20, "màn mặc định $d > 676 (600 trước 3.1.5 + 56 nhịp thân xe + 20 cốp 3.1.6 — không được phình thêm)")
        assertTrue(q <= 474 + 56 + 20, "QA2 $q > 550: ô ẩn / thanh nút ẩn lại bị đọc")
        assertTrue(qIdle <= 48, "QA2 xe 3D không vẽ $qIdle > 48 (= 8 datum chip thanh trên × 6 nhịp): vẫn đọc cho widget không bày gì")
    }

    @Test
    fun `PERF 315 nhu cau - o an, thanh an, ban lui`() {
        val st = qa2
        val demand = CarDataDemand.of(st)!!
        assertTrue("tyre_ps_fl" !in demand || st.colorChoice.carTyres, "lốp chỉ qua CAR3D_TYRES (công tắc lốp)")
        assertTrue(CarDataDemand.controlsOf(st).none { it == "win_lf" || it == "seatc" }, "thanh nút ẩn ⇒ không đọc nút của nó")
        val shownDock = st.copy(dock = st.dock.copy(visible = true))
        assertTrue("win_lf" in CarDataDemand.controlsOf(shownDock), "thanh nút hiện ⇒ đọc như cũ")
        // Ô ẩn: g_tyres ở ô 2 khi bố cục 1 ô ⇒ bỏ; bố cục 3 ô ⇒ đọc.
        val noTyres = st.copy(colorChoice = ColorChoice(carTyres = false))
        assertTrue("tyre_p_fl" !in CarDataDemand.of(noTyres)!!, "g_tyres ở ô ẩn không được đọc")
        val three = noTyres.copy(workspace = noTyres.workspace.copy(preset = LayoutPreset.THREE))
        assertTrue("tyre_p_fl" in CarDataDemand.of(three)!!, "g_tyres ở ô hiện thì đọc")
        // 3.1.8: xe 3D không vẽ (chờ/không khả thi) ⇒ không datum, không lốp, không nút ẩn của nó (không còn bản lùi 2D để đọc).
        val idle = CarDataDemand.of(noTyres.copy(colorChoice = ColorChoice.DEFAULT), hidden = setOf("w_car3d"))!!
        assertTrue("window_lf" !in idle && "light_low_beam" !in idle && "tyre_p_fl" !in idle, "xe 3D không vẽ: $idle")
        assertTrue(CarDataDemand.controlsOf(st, hidden = setOf("w_car3d")).isEmpty(), "không vẽ ⇒ không đọc nút ẩn của hàng chân")
    }

    @Test
    fun `318 so khong ve - mot ban dang ve thi van doc du, het ban ve thi 0, roi cua so thi xoa`() {
        // 3.1.8 CAR3D-FALLBACK-STICKY: trạng thái "lùi 2D" bỏ hẳn — widget không vẽ (chờ chỗ / ô nhỏ / lỗi / không khả thi) = không bày gì.
        val reg = WidgetFallbacks()
        val seen = ArrayList<Set<String>>()
        reg.onChange = { h -> seen += h }
        val a = Any(); val b = Any()
        reg.attach(a, "w_car3d"); reg.attach(b, "w_car3d")
        reg.rendering(b, false)
        assertTrue(reg.hidden().isEmpty(), "còn một bản đang vẽ ⇒ đọc đủ")
        reg.rendering(a, false)
        assertTrue(reg.hidden() == setOf("w_car3d"), "không bản nào vẽ ⇒ 0 lượt đọc cho nó")
        reg.rendering(a, true)
        assertTrue(reg.hidden().isEmpty(), "vẽ lại ⇒ đọc lại")
        reg.detach(a)
        assertTrue(reg.hidden() == setOf("w_car3d"), "bản đang vẽ rời cửa sổ, còn bản chờ ⇒ không đọc")
        reg.detach(b)
        assertTrue(reg.hidden().isEmpty())
        reg.rendering(Any(), false)   // bản chưa gắn ⇒ bỏ qua, không mồ côi
        assertTrue(reg.hidden().isEmpty())
        reg.attach(b, "w_car3d", rendering = false)
        assertTrue(reg.hidden() == setOf("w_car3d"), "gắn lại một bản đang chờ ⇒ không đọc")
        val idle = setOf("w_car3d")
        assertTrue(seen == listOf(idle, emptySet(), idle, emptySet(), idle), "mỗi lần tập đổi báo đúng một lần: $seen")
    }

    @Test
    fun `PERF 315 republish - khong ve sau khi cong bo thi tinh lai, man da roi thi khong`() {
        val h = CarDataDemand.Holder()
        h.publish(qa2)
        assertTrue(h.controls().isNotEmpty())
        h.republish(setOf("w_car3d"))
        assertTrue(h.controls().isEmpty(), "xe 3D thôi vẽ ⇒ nhu cầu tính lại ngay")
        h.clear(); h.republish(emptySet())
        assertTrue(h.get() == null && h.controls().isEmpty(), "màn đã rời ⇒ republish không công bố lại")
    }

    @Test
    fun `PERF 315 widget khuat - 0 luot doc cho no, hien lai thi doc lai`() {
        val reg = WidgetFallbacks()
        val got = ArrayList<Set<String>>()
        reg.onChange = { h -> got += h }
        val a = Any()
        reg.attach(a, "w_car3d")
        reg.shown(a, false)
        assertTrue(reg.hidden() == setOf("w_car3d"))
        val hiddenDemand = CarDataDemand.of(qa2, hidden = reg.hidden())!!
        assertTrue("window_lf" !in hiddenDemand && "seat_heat_state" in hiddenDemand, "khuất ⇒ không datum xe 3D (chip ghế vẫn đọc): $hiddenDemand")
        assertTrue(CarDataDemand.controlsOf(qa2, hidden = reg.hidden()).isEmpty(), "khuất ⇒ không đọc nút ẩn của hàng chân")
        assertTrue(perMinuteHidden() <= 48, "QA2 xe 3D khuất: chỉ còn 8 datum chip × 6 nhịp")
        reg.shown(a, true)
        assertTrue(reg.hidden().isEmpty() && got == listOf(setOf("w_car3d"), emptySet()))
        reg.shown(Any(), false)   // bản chưa gắn ⇒ bỏ qua
        assertTrue(reg.hidden().isEmpty())
    }

    private fun perMinuteHidden(): Int {
        val h = CarDataDemand.Holder()
        h.publish(qa2, setOf("w_car3d"))
        val gw = AnswerAll(); var now = 0L; var s = CarStatus()
        val adapter = CarDataAdapter(HalBindingTable(gw), h::get, clock = { now }, controlDemand = h::controls)
        for (t in 0 until 660) { if (t == 60) gw.reads = 0; if (t % 10 == 0) s = adapter.readSlow(s); if (adapter.fastNeeded()) s = adapter.readFast(s); if (t % 3 == 0 && adapter.bodyNeeded()) s = adapter.readBody(s); now += 1_000 }
        return gw.reads / 10
    }
}
