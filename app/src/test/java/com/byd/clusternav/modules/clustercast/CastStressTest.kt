package com.byd.clusternav.modules.clustercast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * STRESS / LOAD — mô phỏng chiếu-đổi-app + chỉnh-kích-thước LIÊN TỤC (yêu cầu hiện trường). Chạy logic thật
 * qua [FakeShell] N vòng, khẳng định: KHÔNG tích luỹ stack mồ côi, guard idempotent, fail-safe khi move hụt,
 * resize không bao giờ đụng task ≠ VD. Bọc SL1–SL5 trong docs/review/track1-feature-usecase-test-plan.md.
 */
class CastStressTest {

    private val silent: (String) -> Unit = {}
    private val N = 200
    /** ★ v0.7x: guard-heavy loop đi qua GENTLE THẬT (Thread.sleep) → dùng N nhỏ để suite không chậm; đủ để stress bất biến. */
    private val SL1_N = 20

    private fun sinkStack(id: Int, task: Int, disp: Int) =
        FakeStack(id, disp, task, "com.byd.carplay.ui/com.byd.carplay.ui.VideoActivity")

    // SL1: đổi app qua lại liên tục — target đặt LÊN TRÊN (occlude) sink CP/AA cũ → guard gentle bê sink khỏi VD
    //   (giữ phiên, KHÔNG move-stack…0). N nhỏ hơn bản cũ (200) vì mỗi vòng đi qua gentle THẬT (Thread.sleep) —
    //   giá trị stress = LẶP + bất biến (no-accumulate, no-move-stack, no-force-stop), không phải con số tuyệt đối.
    @Test
    fun `SL1 doi app lien tuc khong sinh mo coi`() {
        val dev = FakeDevice(vd = 1).add(FakeStack(3, 0, 5, "com.android.launcher3/.Launcher"))
        val keeper = FakeStack(2, 1, 3, "vn.vietmap.live/.Main", mode = "freeform")
        dev.add(keeper)                                    // target (keeper) trên VD
        var nextId = 100
        repeat(SL1_N) { i ->
            // giả lập: 1 sink CP/AA cũ vừa trên VD, rồi target được đặt LÊN TRÊN (occlude sink cũ)
            dev.add(sinkStack(nextId, nextId + 1000, 1)); nextId++
            keeper.front = dev.nextFront()                 // target re-front → phủ (occlude) sink cũ
            val ok = ClusterCast.guardSinksOffVd(fakeShell(dev), vd = 1, keepPkg = "vn.vietmap.live", log = silent)
            assertTrue(ok, "vòng $i: sink occluded → gentle bê off VD → VD sạch sink")
            assertTrue(ClusterCast.phoneProjectionSinksOn(StackParse.parse(dev.amStackList()), 1).isEmpty(),
                "vòng $i: VD sạch sink sau guard")
        }
        assertEquals(0, dev.moveCount, "T9: guard KHÔNG dùng move-stack (returnAppToMain gentle)")
        assertTrue(dev.forceStoppedPkgs.isEmpty(), "sink giữ phiên — KHÔNG bao giờ force-stop")
        // không stack mồ côi tích luỹ: mọi sink đã gentle về display 0
        assertTrue(dev.stacks.filter { it.comp.contains("carplay") }.all { it.displayId == 0 }, "mọi sink đã gentle về d0")
    }

    // SL3: guard gọi lặp trên state đã sạch → idempotent, không lệnh thừa, không force-stop.
    @Test
    fun `SL3 guard idempotent - goi lai khong be thua`() {
        val dev = FakeDevice(vd = 1)
            .add(sinkStack(66, 71, 1))                                         // sink add trước
            .add(FakeStack(2, 1, 3, "vn.vietmap.live/.Main", mode = "freeform")) // keeper add sau → phủ sink
        assertTrue(ClusterCast.guardSinksOffVd(fakeShell(dev), 1, "vn.vietmap.live", silent), "sink occluded → gentle off VD")
        assertEquals(0, dev.moveCount, "T9: gentle, KHÔNG move-stack")
        assertEquals(0, dev.stacks.first { it.stackId == 66 }.displayId, "sink đã về d0")
        // gọi lại nhiều lần: sink đã ở d0 → không còn sink trên VD → true, không đụng gì
        repeat(20) { assertTrue(ClusterCast.guardSinksOffVd(fakeShell(dev), 1, "vn.vietmap.live", silent)) }
        assertEquals(0, dev.moveCount, "không phát move-stack thừa")
        assertTrue(dev.forceStoppedPkgs.isEmpty(), "idempotent — không force-stop lần nào (giữ phiên)")
    }

    // SL5: sink CƯỠNG LẠI occlude (size-compat) LẶP giữa stress → LUÔN leave-in-place (KHÔNG force-stop, KHÔNG
    //   move-stack, KHÔNG nửa vời). Đây là fail-"soft" mới: thà để sink sau target còn hơn tạo orphan (R11/OQ3).
    @Test
    fun `SL5 sink cuong lai giua stress luon leave-in-place khong force-stop`() {
        repeat(30) { i ->
            val dev = FakeDevice(vd = 1, resistReturnPkgs = mutableSetOf("com.byd.carplay.ui")).add(sinkStack(66, 71, 1))
            val ok = ClusterCast.guardSinksOffVd(fakeShell(dev), 1, "", silent)
            assertFalse(ok, "vòng $i: sink cưỡng lại (visible) → ĐỂ YÊN → false")
            assertEquals(1, dev.stacks.first().displayId, "vòng $i: sink vẫn trên VD (leave-in-place, không nửa vời)")
            assertFalse(dev.forceStoppedPkgs.contains("com.byd.carplay.ui"), "vòng $i: KHÔNG force-stop sink (giữ phiên)")
            assertEquals(0, dev.moveCount, "vòng $i: KHÔNG move-stack")
        }
    }

    // SL2: chỉnh kích thước LIÊN TỤC (nhiều %) — luôn đúng tier, không bao giờ đụng task ≠ VD.
    @Test
    fun `SL2 resize lien tuc luon dung VD khong dung man giua`() {
        val dev = FakeDevice(vd = 1, freeformAlive = true)
        val onVd = StackEntry(1, 1, "freeform", "standard", 71, "com.byd.carplay.ui/.VideoActivity", true)
        val onMain = StackEntry(2, 0, "fullscreen", "standard", 99, "vn.vietmap.live/.MainActivity", true)
        repeat(N) { i ->
            val pct = 50 + (i % 51)   // 50%..100% đổi liên tục
            val rw = 1920 * pct / 100; val rh = 720 * pct / 100
            val scale = AppScale(dpi = 300 + (i % 40), rectL = 0, rectT = 0, rectR = rw, rectB = rh)
            // task đúng VD → resize chạy
            val r1 = ClusterCast.applyBounds(fakeShell(dev), 1, onVd, scale, 1920, 720)
            assertFalse(r1.contains("BỎ QUA"), "vòng $i: task ở VD phải resize được")
            // task màn giữa → LUÔN bị từ chối (P0 guard)
            val before = dev.resizeCount
            val r2 = ClusterCast.applyBounds(fakeShell(dev), 1, onMain, scale, 1920, 720)
            assertTrue(r2.contains("BỎ QUA"), "vòng $i: task màn giữa phải bị từ chối")
            assertEquals(before, dev.resizeCount, "vòng $i: KHÔNG resize task màn giữa")
        }
    }

    // SL4: divergence (WM↔AM lệch = mồ côi) XEN GIỮA chuỗi đổi app → CHẶN đúng lúc orphan, MỞ LẠI khi sạch.
    //   Khoá đúng yêu cầu plan §STRESS SL4 ("chặn đúng lúc orphan, mở lại khi sạch"): con mắt thứ hai
    //   (divergenceOn) phải hoạt động ĐAN XEN với luồng đổi app, không phải chỉ ở trạng thái tĩnh (UC9).
    @Test
    fun `SL4 divergence xen giua stress - chan luc orphan mo lai khi sach`() {
        val dev = FakeDevice(vd = 1).add(FakeStack(3, 0, 5, "com.android.launcher3/.Launcher"))
        val keeper = FakeStack(2, 1, 3, "vn.vietmap.live/.Main", mode = "freeform")
        dev.add(keeper)                                     // target (keeper) trên VD — để occlude sink cũ mỗi vòng
        var nextId = 300
        var blocked = 0
        repeat(6) { i ->
            if (i == 3) {
                // ★ GIỮA stress: 1 sink hoá MỒ CÔI (WM thấy, `am stack list` không) → mọi thao tác cụm phải DỪNG.
                dev.add(FakeStack(9000, 1, 9001, "com.byd.carplay.ui/com.byd.carplay.ui.VideoActivity", amVisible = false))
                assertNotNull(ClusterCast.divergenceOn(fakeShell(dev), 1), "vòng $i: orphan xen giữa → PHẢI chặn")
                blocked++
                // mô phỏng tắt-mở máy dọn sạch mồ côi (AM↔WM khớp lại) → mở lại
                dev.stacks.removeAll { it.stackId == 9000 }
                assertNull(ClusterCast.divergenceOn(fakeShell(dev), 1), "vòng $i: sau khi sạch → MỞ LẠI")
            } else {
                // đổi app bình thường: sink CP/AA cũ trên VD, target đặt LÊN TRÊN (occlude) → guard gentle bê sink off VD
                dev.add(sinkStack(nextId, nextId + 1000, 1)); nextId++
                keeper.front = dev.nextFront()             // target re-front → phủ (occlude) sink cũ
                assertNull(ClusterCast.divergenceOn(fakeShell(dev), 1), "vòng $i: cụm sạch → KHÔNG được chặn")
                assertTrue(ClusterCast.guardSinksOffVd(fakeShell(dev), 1, "vn.vietmap.live", silent),
                    "vòng $i: guard gentle bê được sink (occluded)")
                assertTrue(ClusterCast.phoneProjectionSinksOn(StackParse.parse(dev.amStackList()), 1).isEmpty(),
                    "vòng $i: VD sạch sink sau guard")
            }
        }
        assertEquals(1, blocked, "đúng 1 lần chặn giữa chuỗi (khi có orphan), còn lại đều thông")
        assertTrue(dev.forceStoppedPkgs.isEmpty(), "sink giữ phiên xuyên suốt — KHÔNG force-stop")
        assertEquals(0, dev.moveCount, "T9: guard KHÔNG move-stack")
    }
}
