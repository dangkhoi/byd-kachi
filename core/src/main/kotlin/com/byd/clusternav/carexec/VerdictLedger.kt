package com.byd.clusternav.carexec

/** Kết luận cho một candidate. */
enum class Verdict { OK, FAIL, SKIPPED }

data class VerdictEntry(
    val recordedAt: String,
    val feature: CarFeature,
    val stepId: String,
    val candidateId: String,
    val verdict: Verdict,
    val source: VerdictSource,
    val endpoint: String,
    val note: String,
) {
    fun toRow(): String = listOf(
        recordedAt, feature.name, stepId, candidateId, verdict.name, source.name, endpoint,
        note.replace('\t', ' ').replace('\n', ' '),
    ).joinToString("\t")

    companion object {
        const val HEADER = "recordedAt\tfeature\tstep\tcandidate\tverdict\tsource\tendpoint\tnote"

        fun fromRow(row: String): VerdictEntry? {
            val parts = row.split('\t')
            if (parts.size < 7) return null
            val feature = runCatching { CarFeature.valueOf(parts[1]) }.getOrNull() ?: return null
            val verdict = runCatching { Verdict.valueOf(parts[4]) }.getOrNull() ?: return null
            val source = runCatching { VerdictSource.valueOf(parts[5]) }.getOrNull() ?: return null
            return VerdictEntry(parts[0], feature, parts[2], parts[3], verdict, source, parts[6], parts.getOrElse(7) { "" })
        }
    }
}

/**
 * Sổ verdict: append-only, một dòng một lần chạy.
 *
 * Append-only là có lý do. Nếu ghi đè, một candidate từng FAIL rồi sau đó OK sẽ trông như luôn OK, và ta
 * mất đúng thông tin đáng giá nhất: nó từng sai trong hoàn cảnh nào. Trạng thái "hiện tại" của một
 * candidate được suy ra bằng cách lấy dòng mới nhất, chứ không phải bằng cách xoá dòng cũ.
 */
class VerdictLedger(private val rows: List<VerdictEntry>) {

    /** Kết luận mới nhất cho từng candidate. */
    fun latest(): Map<String, VerdictEntry> =
        rows.groupBy { it.candidateId }.mapValues { (_, entries) -> entries.last() }

    fun verdictFor(candidateId: String): Verdict? = latest()[candidateId]?.verdict

    /** Step được coi là OK khi có ít nhất một candidate của nó đang OK. */
    fun okCandidateFor(stepId: String): String? {
        val step = CarExecCatalog.step(stepId) ?: return null
        val latest = latest()
        return step.candidates.map { it.id }.firstOrNull { latest[it]?.verdict == Verdict.OK }
    }

    /**
     * Chuỗi E2E: chỉ gồm các step đã có candidate OK, theo đúng thứ tự khai báo.
     * Step nào chưa OK thì bị bỏ ra và nêu tên — E2E không được âm thầm chạy qua thứ chưa chứng minh.
     */
    fun e2eChain(feature: CarFeature): E2ePlan {
        val ordered = CarExecCatalog.steps.filter { it.feature == feature }
        val ready = ArrayList<Pair<String, String>>()
        val blocked = ArrayList<String>()
        ordered.forEach { step ->
            val candidate = okCandidateFor(step.id)
            if (candidate == null) blocked += step.id else ready += step.id to candidate
        }
        return E2ePlan(feature, ready, blocked)
    }

    companion object {
        fun parse(text: String): VerdictLedger =
            VerdictLedger(text.lineSequence().mapNotNull { VerdictEntry.fromRow(it.trim()) }.toList())
    }
}

data class E2ePlan(
    val feature: CarFeature,
    /** step id → candidate id, theo thứ tự chạy. */
    val ready: List<Pair<String, String>>,
    /** step chưa có candidate nào OK. */
    val blocked: List<String>,
) {
    val runnable: Boolean get() = blocked.isEmpty()
}
