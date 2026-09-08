package com.byd.clusternav.carexec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarExecScenariosTest {

    @Test
    fun `moi scenario chi dung step co trong catalog`() {
        CarExecScenarios.all.forEach { scenario ->
            scenario.stepIds.forEach { stepId ->
                assertTrue(CarExecCatalog.step(stepId) != null, "${scenario.id} dùng step lạ: $stepId")
            }
        }
    }

    @Test
    fun `moi hanh dong phai noi state mong doi va ai kiem`() {
        CarExecScenarios.all.forEach { scenario ->
            scenario.actions.forEach { action ->
                assertTrue(action.expect.isNotBlank(), "${scenario.id}/${action.stepId} không nói state mong đợi")
                assertTrue(action.intent.isNotBlank(), "${scenario.id}/${action.stepId} không nói ý định")
            }
        }
    }

    @Test
    fun `scenario khong duoc coi la chay duoc khi con step chua OK`() {
        val ledger = VerdictLedger(
            listOf(
                VerdictEntry("t", CarFeature.CLUSTER_CAST, "place", "place.freeform-only", Verdict.OK, VerdictSource.MEASURED, "car", ""),
            ),
        )
        val readiness = CarExecScenarios.readiness(CarExecScenarios.scenario("cast.rotate-a-b-c-a")!!, ledger)
        assertFalse(readiness.runnable)
        assertTrue(readiness.readySteps.contains("place"))
        assertTrue(readiness.blockedSteps.contains("open-projection"))
    }

    @Test
    fun `kich ban giu duoc thiet lap phai co buoc kiem sau khi cast lai`() {
        // Đây là yêu cầu nghiệp vụ, không phải chi tiết kỹ thuật: chỉnh khung xong, trả app về màn chính,
        // chiếu lại thì kích thước và DPI phải đúng như đã chỉnh. Không có bước kiểm này thì kịch bản
        // "chỉnh khung" chỉ chứng minh lệnh chạy được, không chứng minh thiết lập được giữ.
        val scenario = CarExecScenarios.scenario("cast.geometry-persist")!!
        val last = scenario.actions.indexOfLast { it.stepId == "observe" }
        val recast = scenario.actions.indexOfLast { it.stepId == "place" }
        assertTrue(recast in 0 until last, "phải kiểm SAU khi cast lại")
        assertTrue(scenario.actions[last].expect.contains("bounds"), scenario.actions[last].expect)
        assertTrue(scenario.actions[last].expect.contains("density"), scenario.actions[last].expect)
    }

    @Test
    fun `xoay vong app phai co it nhat ba lan doi app`() {
        val rotate = CarExecScenarios.scenario("cast.rotate-a-b-c-a")!!
        assertEquals(3, rotate.actions.count { it.stepId == "switch" })
    }

    @Test
    fun `phu du 32 ca canonical`() {
        // Nguồn sự thật là bảng 32 ca trong docs/specs/cluster-cast-rebaseline.html. Có test này để câu
        // "đã đủ chưa" trả lời được bằng máy, chứ không phải bằng cảm giác của người viết.
        assertEquals(emptyList<Int>(), CarExecScenarios.uncoveredCases(), "còn ca chưa có kịch bản nào phủ")
    }

    @Test
    fun `moi step trong catalog duoc dung boi it nhat mot kich ban`() {
        val used = CarExecScenarios.all.flatMap { it.stepIds }.toSet()
        val unused = CarExecCatalog.steps.map { it.id }.filterNot { it in used }
        assertEquals(emptyList<String>(), unused, "step khai báo mà không kịch bản nào dùng: $unused")
    }

    @Test
    fun `khong force-stop app duoc bao ve`() {
        // Ràng buộc sản phẩm: CarPlay/Android Auto không bao giờ bị tắt để lấy cụm. Kịch bản nào dính
        // phiên được bảo vệ thì không được chứa step giết tiến trình.
        CarExecScenarios.all.filter { s -> s.stepIds.any { it == "resume-protected" } }.forEach { scenario ->
            assertFalse(scenario.stepIds.contains("target-process"), "${scenario.id} không được giết app được bảo vệ")
        }
    }

    @Test
    fun `step lap lai nhieu lan phai mang gia tri khac nhau`() {
        // Nếu bốn lần `adjust-geometry` gửi cùng một lệnh thì kịch bản chỉ TRÔNG như đang kiểm bốn cạnh.
        CarExecScenarios.all.forEach { scenario ->
            scenario.actions.groupBy { it.stepId }.filter { it.value.size > 1 }.forEach { (stepId, repeats) ->
                val distinct = repeats.map { it.values }.toSet()
                if (stepId == "adjust-geometry") {
                    assertEquals(
                        repeats.size, distinct.size,
                        "${scenario.id}: $stepId lặp ${repeats.size} lần mà chỉ có $distinct bộ giá trị",
                    )
                }
            }
        }
    }

    @Test
    fun `kich ban giu thiet lap phai neu ro con so phai kiem`() {
        val check = CarExecScenarios.scenario("cast.geometry-persist")!!.actions.last { it.stepId == "observe" }
        assertTrue(check.expect.contains("280"), "phải nói rõ density mong đợi: ${check.expect}")
        assertTrue(check.expect.contains("1890"), "phải nói rõ bounds mong đợi: ${check.expect}")
    }

    @Test
    fun `ca hai feature deu co kich ban`() {
        assertTrue(CarExecScenarios.all.any { it.feature == CarFeature.CLUSTER_CAST })
        assertTrue(CarExecScenarios.all.any { it.feature == CarFeature.NAVIGATION })
    }
}
