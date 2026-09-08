package com.byd.clusternav.carexec

import java.io.File
import java.time.Instant

/**
 * Bốn lệnh của quy trình đánh giá: liệt kê, chạy một candidate, ghi verdict, ghép E2E.
 *
 * Quy trình có chủ ý tách "chạy" khỏi "kết luận". Chạy xong máy in ra những gì nó thấy; **người** quyết
 * định đạt hay không rồi ghi verdict bằng lệnh riêng. Với các step chỉ mắt người kết luận được (mở/đóng
 * chiếu) đây là cách duy nhất trung thực; với các step đo được thì nó vẫn giữ cho ledger là một chuỗi
 * quyết định có chủ, không phải một đống output.
 */
object CarExecCommands {

    const val DEFAULT_LEDGER = "docs/refactor-car-execution/verdicts.tsv"

    fun steps(): String = buildString {
        CarExecCatalog.steps.forEach { step ->
            appendLine("${step.id}  [${step.feature}]  ${step.purpose}")
            appendLine("    tiền đề: ${step.precondition}")
            step.candidates.forEach { candidate ->
                appendLine("    - ${candidate.id}  (verdict: ${candidate.verdictSource}, rủi ro: ${candidate.risk})")
                appendLine("        ${candidate.purpose}")
                candidate.commands.forEach { appendLine("        $ $it") }
                appendLine("        đạt khi: ${candidate.evidence}")
                candidate.fieldNote?.let { appendLine("        field: $it") }
            }
        }
    }.trimEnd()

    /** Thay placeholder bằng giá trị thật; báo lỗi nếu còn placeholder chưa có giá trị. */
    fun resolve(command: String, values: Map<String, String>): String {
        var resolved = command
        values.forEach { (key, value) -> resolved = resolved.replace(key, value) }
        val leftover = Regex("""\{[a-zA-Z]+\}""").find(resolved)
        require(leftover == null) { "thiếu giá trị cho ${leftover?.value} trong: $command" }
        return resolved
    }

    /** In lệnh sẽ gửi cho một candidate, không gửi gì. */
    fun dryRunCandidate(candidateId: String, values: Map<String, String>): String {
        val found = CarExecCatalog.candidate(candidateId) ?: return "không có candidate $candidateId"
        val (step, candidate) = found
        return buildString {
            appendLine("step=${step.id} candidate=${candidate.id}  (DRY-RUN, không gửi gì)")
            appendLine("đạt khi: ${candidate.evidence}")
            candidate.commands.forEach { raw ->
                val resolved = runCatching { resolve(raw, values) }.getOrElse { "THIẾU THAM SỐ: ${it.message}" }
                appendLine("  $ $resolved")
            }
        }.trimEnd()
    }

    fun runCandidate(runner: (String) -> ShellOutcome, candidateId: String, values: Map<String, String>): String {
        val found = CarExecCatalog.candidate(candidateId)
            ?: return "không có candidate $candidateId; xem 'steps'"
        val (step, candidate) = found
        return buildString {
            appendLine("step=${step.id} candidate=${candidate.id} verdict-source=${candidate.verdictSource}")
            appendLine("đạt khi: ${candidate.evidence}")
            candidate.commands.forEach { raw ->
                val command = runCatching { resolve(raw, values) }.getOrElse { failure ->
                    appendLine("  BỎ QUA: ${failure.message}")
                    return@forEach
                }
                val result = runner(command)
                appendLine("  $ $command")
                appendLine("    exit=${result.exitCode} (${result.elapsedMs} ms)")
                result.output.lines().take(12).filter { it.isNotBlank() }.forEach { appendLine("    | $it") }
            }
            appendLine()
            appendLine(
                when (candidate.verdictSource) {
                    VerdictSource.HUMAN -> "→ NHÌN CỤM rồi ghi: verdict ${candidate.id} ok|fail --note \"...\""
                    VerdictSource.MEASURED -> "→ Kiểm bằng 'observe' rồi ghi: verdict ${candidate.id} ok|fail"
                },
            )
        }.trimEnd()
    }

    fun recordVerdict(
        ledgerPath: String,
        candidateId: String,
        verdict: Verdict,
        endpoint: String,
        note: String,
        now: () -> Instant = Instant::now,
    ): String {
        val found = CarExecCatalog.candidate(candidateId) ?: return "không có candidate $candidateId"
        val (step, candidate) = found
        val entry = VerdictEntry(
            now().toString(), step.feature, step.id, candidate.id, verdict, candidate.verdictSource, endpoint, note,
        )
        val file = File(ledgerPath)
        file.parentFile?.mkdirs()
        if (!file.exists()) file.writeText(VerdictEntry.HEADER + "\n")
        file.appendText(entry.toRow() + "\n")
        return "đã ghi: ${entry.toRow()}"
    }

    fun ledger(ledgerPath: String): VerdictLedger {
        val file = File(ledgerPath)
        return if (file.exists()) VerdictLedger.parse(file.readText()) else VerdictLedger(emptyList())
    }

    fun scenarios(ledgerPath: String): String {
        val ledger = ledger(ledgerPath)
        return buildString {
            CarExecScenarios.all.forEach { scenario ->
                val readiness = CarExecScenarios.readiness(scenario, ledger)
                appendLine("${scenario.id}  [${scenario.feature}]  ${if (readiness.runnable) "CHẠY ĐƯỢC" else "CHƯA ĐỦ"}")
                appendLine("    ${scenario.purpose}")
                if (readiness.blockedSteps.isNotEmpty()) {
                    appendLine("    step chưa OK: ${readiness.blockedSteps.joinToString()}")
                }
            }
        }.trimEnd()
    }

    fun scenario(ledgerPath: String, id: String): String {
        val scenario = CarExecScenarios.scenario(id) ?: return "không có kịch bản $id; xem 'scenarios'"
        val readiness = CarExecScenarios.readiness(scenario, ledger(ledgerPath))
        return buildString {
            appendLine("${scenario.id}  [${scenario.feature}]")
            appendLine(scenario.purpose)
            appendLine()
            scenario.actions.forEachIndexed { index, action ->
                val ok = action.stepId !in readiness.blockedSteps
                appendLine("${index + 1}. ${action.stepId}  ${if (ok) "" else "[STEP CHƯA OK]"}")
                appendLine("     ý định: ${action.intent}")
                appendLine("     phải đúng: ${action.expect}")
                appendLine("     ai kiểm: ${action.checkedBy}")
            }
            appendLine()
            appendLine(
                if (readiness.runnable) {
                    "→ Mọi step đã OK. Chạy tuần tự bằng 'run <candidate>' theo đúng thứ tự trên."
                } else {
                    "→ CHƯA ráp được. Làm cho xong các step này trước: ${readiness.blockedSteps.joinToString()}"
                },
            )
        }.trimEnd()
    }

    /**
     * Chạy kịch bản tuần tự, dừng ở mỗi mốc cần mắt người.
     *
     * Dừng chứ không bỏ qua, và cũng không tự đoán. Bước nào máy đo được thì chạy liền; bước nào phải
     * nhìn cụm thì in ra điều cần nhìn rồi thoát, kèm đúng câu lệnh để ghi verdict và câu lệnh để chạy
     * tiếp từ bước sau. Đây là lý do có `--from`: một phiên trên xe bị cắt ngang giữa đường vẫn nối lại
     * được mà không phải làm lại từ đầu — thứ mà một script chạy một lèo không cho.
     */
    /**
     * In ra đúng chuỗi lệnh sẽ gửi, không gửi gì cả.
     *
     * Để duyệt kịch bản trước khi lên xe. Thời gian trên xe đắt và bị giới hạn bởi những thứ ngoài tầm
     * kiểm soát — hôm 26/7 mất cả phiên vì ACC standby — nên phát hiện thiếu tham số ở đây rẻ hơn nhiều
     * so với phát hiện lúc đang ngồi trong xe.
     */
    fun planScenario(id: String, values: Map<String, String>): String {
        val scenario = CarExecScenarios.scenario(id) ?: return "không có kịch bản $id"
        return buildString {
            appendLine("${scenario.id} — chuỗi lệnh sẽ gửi (KHÔNG chạy gì)")
            appendLine()
            scenario.actions.forEachIndexed { index, action ->
                val step = CarExecCatalog.step(action.stepId)!!
                appendLine("${index + 1}. ${action.stepId}  [${action.checkedBy}]  ${action.intent}")
                step.candidates.forEach { candidate ->
                    appendLine("     ~ ${candidate.id}")
                    candidate.commands.forEach { raw ->
                        val resolved = runCatching { resolve(raw, values + action.values) }
                            .getOrElse { "THIẾU THAM SỐ: ${it.message}" }
                        appendLine("         $ $resolved")
                    }
                }
                appendLine("     phải đúng: ${action.expect}")
            }
            val missing = missingPlaceholders(scenario, values)
            appendLine()
            appendLine(
                if (missing.isEmpty()) {
                    "→ đủ tham số cho mọi lệnh của kịch bản này."
                } else {
                    "→ CÒN THIẾU tham số: ${missing.joinToString()} — truyền vào trước khi lên xe."
                },
            )
        }.trimEnd()
    }

    /** Placeholder nào mà kịch bản cần nhưng phiên chưa cấp giá trị. */
    fun missingPlaceholders(scenario: CarScenario, values: Map<String, String>): List<String> {
        val needed = LinkedHashSet<String>()
        scenario.actions.forEach { action ->
            val step = CarExecCatalog.step(action.stepId) ?: return@forEach
            step.candidates.forEach { candidate ->
                candidate.commands.forEach { command ->
                    Regex("""\{[a-zA-Z]+\}""").findAll(command).forEach { match ->
                        if (match.value !in values && match.value !in action.values) needed += match.value
                    }
                }
            }
        }
        return needed.toList()
    }

    fun runScenario(
        ledgerPath: String,
        id: String,
        values: Map<String, String>,
        fromIndex: Int = 1,
        execute: (String) -> ShellOutcome,
    ): String {
        val scenario = CarExecScenarios.scenario(id) ?: return "không có kịch bản $id; xem 'scenarios'"
        val ledger = ledger(ledgerPath)
        return buildString {
            appendLine("chạy ${scenario.id} từ bước $fromIndex")
            appendLine()
            scenario.actions.forEachIndexed { index, action ->
                val number = index + 1
                if (number < fromIndex) return@forEachIndexed
                val candidateId = ledger.okCandidateFor(action.stepId)
                if (candidateId == null) {
                    appendLine("$number. ${action.stepId} — DỪNG: step này chưa có candidate nào OK")
                    appendLine("   làm cho xong step đó trước, rồi chạy lại với --from $number")
                    return@buildString
                }
                val candidate = CarExecCatalog.candidate(candidateId)!!.second
                val actionValues = values + action.values
                appendLine("$number. ${action.stepId} → $candidateId")
                appendLine("   ý định: ${action.intent}")
                candidate.commands.forEach { raw ->
                    val command = runCatching { resolve(raw, actionValues) }.getOrElse { failure ->
                        appendLine("   DỪNG: ${failure.message}")
                        appendLine("   truyền thêm giá trị rồi chạy lại với --from $number")
                        return@buildString
                    }
                    val result = execute(command)
                    appendLine("   $ $command")
                    appendLine("     exit=${result.exitCode} (${result.elapsedMs} ms)")
                    if (!result.ok) {
                        appendLine("     | ${result.output.lines().firstOrNull().orEmpty()}")
                        appendLine("   DỪNG: lệnh thất bại. Ghi verdict fail rồi xử lý trước khi đi tiếp:")
                        appendLine("     verdict $candidateId fail --note \"...\"")
                        return@buildString
                    }
                }
                if (action.checkedBy == VerdictSource.HUMAN) {
                    appendLine()
                    appendLine("   ⏸ CẦN NHÌN: ${action.expect}")
                    appendLine("   đúng  → verdict $candidateId ok --note \"...\"")
                    appendLine("   sai   → verdict $candidateId fail --note \"...\"")
                    appendLine("   rồi chạy tiếp: scenario ${scenario.id} --run --from ${number + 1}")
                    return@buildString
                }
                appendLine("   ✓ đo được, phải đúng: ${action.expect}")
            }
            appendLine()
            appendLine("hết kịch bản ${scenario.id}")
        }.trimEnd()
    }

    fun e2e(ledgerPath: String, feature: CarFeature = CarFeature.CLUSTER_CAST): String {
        val plan = ledger(ledgerPath).e2eChain(feature)
        return buildString {
            appendLine("E2E ${plan.feature}: ${if (plan.runnable) "CHẠY ĐƯỢC" else "CHƯA ĐỦ"}")
            appendLine("các step đã OK, theo thứ tự:")
            if (plan.ready.isEmpty()) appendLine("  (chưa có step nào OK)")
            plan.ready.forEach { (step, candidate) -> appendLine("  $step → $candidate") }
            if (plan.blocked.isNotEmpty()) {
                appendLine("chưa có candidate OK — E2E không đi qua các step này:")
                plan.blocked.forEach { appendLine("  $it") }
            }
        }.trimEnd()
    }
}
