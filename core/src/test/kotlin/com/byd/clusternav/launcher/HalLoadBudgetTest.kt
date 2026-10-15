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
    private fun perMinute(state: HomeUiState, degraded: Set<String> = emptySet()): Int {
        val gw = AnswerAll()
        var now = 0L
        val holder = CarDataDemand.Holder()
        holder.publish(state, degraded)
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
        println("  ${state.workspace.preset} lùi=$degraded: chậm=${slow / 10} nhanh=${(gw.reads - slow) / 10} datum=${holder.get()?.size} nút=${holder.controls()}")
        return gw.reads / 10
    }

    private val qa2 = HomeUiState(
        workspace = WorkspaceState.of(LayoutPreset.ONE, SlotContent.Widget("w_car3d"), SlotContent.Widget("g_tyres"), SlotContent.Widget("g_windows")),
        dock = DockConfig(enabled = listOf("trunk", "readl", "pm25", "seatc", "temp", "fan", "win_lf"), visible = false),
    )
    private val defaultScreen = HomeUiState(workspace = WorkspaceState.DEFAULT)

    /**
     * [ĐO off-car, trần trên] trước 3.1.5: mặc định 600/phút (chậm 240 + nhanh 360 = 6 nút × 60) · QA2 594 (ô ẩn `g_tyres`/`g_windows`
     * + thanh nút ẨN 7 mã vẫn đọc). Sau: ô ẩn + thanh ẩn không đọc; xe 3D lùi 2D chỉ đọc ô nhóm kính.
     * ⚠ K1 (150) KHÔNG đạt được ở màn mặc định: riêng nhịp NHANH của ô nút là 6 × 60 = 360 — luật owner #5 (09-21, *"không
     * realtime"*: nút đọc mỗi 1 s) — ghi ở backlog `CAR3D-K1-BUDGET` cho owner chốt; bài này GHIM số đo để không phình âm thầm.
     */
    @Test
    fun `PERF 315 tai HAL ghim - o an va thanh an khong doc, xe 3D lui chi doc o nhom`() {
        val d = perMinute(defaultScreen)
        val q = perMinute(qa2)
        val qFallen = perMinute(qa2, setOf("w_car3d"))
        println("HAL/phút mặc định=$d · QA2=$q · QA2 xe 3D lùi=$qFallen")
        // 3.1.6 — nhịp thân xe (cửa 3 s thay 10 s, owner 15/10 *"mở/đóng cửa nhận chậm"*): +56/phút ĐÚNG khi cửa đang hiện
        // (4 × 20 − 4 × 6), ghi tường minh — không phình âm thầm. Hai màn dưới đều bày xe 3D (có cửa). Lý do + số xe: KDoc
        // `CarDataDemand.BODY_IDS` · `docs/diagnostics/oncar-bugs-2026-10-15.md` §3.
        // 3.1.6 · R32 — CỐP (`getDoorState(6)`) đi chung nhịp thân xe: +20/phút (1 lượt / 3 s) khi xe 3D/bảng cửa/ô cốp hiện; nút
        // `trunk` lấy luôn số ấy (KHÔNG đọc ở nhịp nhanh 1 s — bỏ dòng mirror ở `CarDataAdapter.readControls` thì +60 ⇒ đỏ).
        assertTrue(d <= 600 + 56 + 20, "màn mặc định $d > 676 (600 trước 3.1.5 + 56 nhịp thân xe + 20 cốp 3.1.6 — không được phình thêm)")
        assertTrue(q <= 474 + 56 + 20, "QA2 $q > 550: ô ẩn / thanh nút ẩn lại bị đọc")
        assertTrue(qFallen <= 72, "QA2 xe 3D lùi 2D $qFallen > 72 (= 4 kính + 8 datum chip thanh trên, × 6 nhịp): vẫn đọc nút ẩn/lốp của bản 3D")
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
        // Bản lùi: không nút ẩn, không lốp, chỉ kính.
        val fallen = CarDataDemand.of(noTyres.copy(colorChoice = ColorChoice.DEFAULT), setOf("w_car3d"))!!
        assertTrue("window_lf" in fallen && "light_low_beam" !in fallen && "tyre_p_fl" !in fallen, "lùi 2D: $fallen")
        assertTrue(CarDataDemand.controlsOf(st, setOf("w_car3d")).isEmpty(), "lùi 2D ⇒ không đọc nút ẩn của hàng chân")
    }

    @Test
    fun `PERF 315 so lui - mot ban song thi van doc du, roi cua so thi xoa`() {
        val reg = WidgetFallbacks()
        val seen = ArrayList<Set<String>>()
        reg.onChange = { d, _ -> seen += d }
        val a = Any(); val b = Any()
        reg.attach(a, "w_car3d", isFallen = false); reg.attach(b, "w_car3d", isFallen = false)
        reg.fell(b, "w_car3d")
        assertTrue(reg.degraded().isEmpty(), "còn một bản sống ⇒ đọc đủ")
        reg.fell(a, "w_car3d")
        assertTrue(reg.degraded() == setOf("w_car3d"))
        reg.detach(a); reg.detach(b)
        assertTrue(reg.degraded().isEmpty())
        reg.fell(Any(), "w_car3d")   // bản chưa gắn ⇒ bỏ qua, không mồ côi
        assertTrue(reg.degraded().isEmpty())
        assertTrue(seen == listOf(setOf("w_car3d"), emptySet()), "báo đúng hai lần đổi: $seen")
    }

    @Test
    fun `PERF 315 republish - lui sau khi cong bo thi tinh lai, man da roi thi khong`() {
        val h = CarDataDemand.Holder()
        h.publish(qa2, emptySet())
        assertTrue(h.controls().isNotEmpty())
        h.republish(setOf("w_car3d"))
        assertTrue(h.controls().isEmpty(), "lùi 2D ⇒ nhu cầu tính lại ngay")
        h.clear(); h.republish(emptySet())
        assertTrue(h.get() == null && h.controls().isEmpty(), "màn đã rời ⇒ republish không công bố lại")
    }

    @Test
    fun `PERF 315 widget khuat - 0 luot doc cho no, hien lai thi doc lai`() {
        val reg = WidgetFallbacks()
        val got = ArrayList<Set<String>>()
        reg.onChange = { _, h -> got += h }
        val a = Any()
        reg.attach(a, "w_car3d", isFallen = false)
        reg.shown(a, false)
        assertTrue(reg.hidden() == setOf("w_car3d") && reg.degraded().isEmpty())
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
        h.publish(qa2, emptySet(), setOf("w_car3d"))
        val gw = AnswerAll(); var now = 0L; var s = CarStatus()
        val adapter = CarDataAdapter(HalBindingTable(gw), h::get, clock = { now }, controlDemand = h::controls)
        for (t in 0 until 660) { if (t == 60) gw.reads = 0; if (t % 10 == 0) s = adapter.readSlow(s); if (adapter.fastNeeded()) s = adapter.readFast(s); if (t % 3 == 0 && adapter.bodyNeeded()) s = adapter.readBody(s); now += 1_000 }
        return gw.reads / 10
    }
}
