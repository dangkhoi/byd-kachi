package com.byd.clusternav.carexec

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Nhãn rủi ro phải đúng, vì người bấm dựa vào nó để quyết định có gửi lệnh lúc xe đang chạy hay không.
 *
 * Dán nhãn READ_ONLY cho một lệnh có ghi là kiểu lỗi tệ nhất ở đây: nó không làm test đổ, không làm mã sai,
 * chỉ làm người vận hành tin sai — rồi một lệnh đổi trạng thái được gửi trong lúc đang lái.
 */
class CandidateRiskLabelTest {

    /** Động từ có ghi. `service call` KHÔNG nằm đây vì nó vừa dùng để đọc (descriptor) vừa để ghi. */
    // `am stack list` và `service call ... 1598968902` (đọc descriptor) là ĐỌC, nên danh sách phải chính
    // xác tới mức lệnh con: "am stack move" chứ không phải "am stack".
    private val mutatingVerbs = listOf(
        "settings put", "setprop ", "pm disable", "pm enable", "pm install", "pm uninstall",
        "appops set", "am broadcast", "am start", "am task resize", "am stack move", "am force-stop",
        // Thêm 2026-08-01 sau khi lưới này để lọt đúng lệnh nguy hiểm nhất trong catalog: hai candidate
        // `am display move-stack …` mang nhãn READ_ONLY suốt một thời gian dài. Không khớp "am stack move"
        // vì thứ tự từ khác hẳn ("am DISPLAY move-stack"). Bài học: liệt kê động từ bằng cách đọc lệnh
        // THẬT trong catalog, đừng suy từ trí nhớ về cú pháp `am`.
        "am display move-stack", "am display move",
        "input tap", "input swipe", "rm ", "mv ", "kill ",
    )

    private val allCandidates = CarExecCatalog.steps.flatMap { step -> step.candidates.map { step to it } }

    @Test
    fun `candidate ghi READ_ONLY thi khong duoc chua lenh co ghi`() {
        val mislabelled = allCandidates.filter { (_, candidate) ->
            candidate.risk == CandidateRisk.READ_ONLY &&
                candidate.commands.any { command -> mutatingVerbs.any { command.contains(it) } }
        }.map { (step, candidate) ->
            val offending = candidate.commands.first { command -> mutatingVerbs.any { command.contains(it) } }
            "${step.id}/${candidate.id}: $offending"
        }
        assertTrue(mislabelled.isEmpty(), "dán nhãn READ_ONLY cho lệnh có ghi: $mislabelled")
    }

    @Test
    fun `candidate co the treo may hoac lam gian doan tai xe phai giai thich vi sao`() {
        // Không có lời giải thích thì người bấm không cân nhắc được, và nhãn thành hình thức.
        val risky = setOf(CandidateRisk.MAY_DISRUPT_DRIVER, CandidateRisk.MAY_HANG_SYSTEM)
        val unexplained = allCandidates
            .filter { (_, candidate) -> candidate.risk in risky }
            .filter { (step, candidate) -> candidate.fieldNote.isNullOrBlank() && step.precondition.isBlank() }
            .map { (step, candidate) -> "${step.id}/${candidate.id}" }
        assertTrue(unexplained.isEmpty(), "rủi ro cao mà không nói vì sao: $unexplained")
    }

    @Test
    fun `step co candidate treo may phai ghi dieu kien xe do trong precondition`() {
        val missing = CarExecCatalog.steps
            .filter { step -> step.candidates.any { it.risk == CandidateRisk.MAY_HANG_SYSTEM } }
            .filter { step -> !step.precondition.contains("ĐỖ") }
            .map { it.id }
        assertTrue(missing.isEmpty(), "step có lệnh có thể treo máy mà precondition không yêu cầu xe đỗ: $missing")
    }

    @Test
    fun `moi step deu co it nhat mot candidate`() {
        val empty = CarExecCatalog.steps.filter { it.candidates.isEmpty() }.map { it.id }
        assertTrue(empty.isEmpty(), "step rỗng thì không đánh cờ được: $empty")
    }
}
