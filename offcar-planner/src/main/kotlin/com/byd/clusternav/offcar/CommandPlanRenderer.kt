package com.byd.clusternav.offcar

class CommandPlanRenderer(
    private val fakeTransport: FakeVehicleTransport = FakeVehicleTransport(),
) {
    fun renderText(plans: List<CommandPlan>): String = buildString {
        appendLine("CLUSTERNAV OFF-CAR INERT PLAN DATA")
        appendLine("corpus=${FirmwareEvidenceCatalog.corpusVerdict}")
        appendLine("visualPass=false")
        appendLine("candidateCount=${plans.size}")
        plans.forEach { plan ->
            appendLine("PLAN ${plan.id} disposition=${plan.disposition} feature=${plan.candidate.feature}")
            fakeTransport.capture(plan).forEach { record ->
                appendLine("  ${record.ordinal}. ${record.phase} ${record.detail}")
            }
        }
    }.trimEnd() + "\n"

    fun renderMilestonePack(
        milestone: String,
        feature: CandidateFeature,
        diagnosticKey: String,
        productionKey: String,
        allPlans: List<CommandPlan>,
    ): String {
        val featurePlans = allPlans.filter { it.candidate.feature == feature }
        val plans = if (feature == CandidateFeature.NAV_HUD) {
            featurePlans.filterNot { it.candidate.id == "OFFCAR-H0-PHYSICAL-HUD" }
        } else {
            featurePlans
        }
        val blocked = plans.count { it.disposition == PlanDisposition.BLOCKED }
        val unknown = plans.count { it.disposition == PlanDisposition.UNKNOWN }
        return buildString {
            appendLine("{")
            appendLine("  \"schema\": \"clusternav.offcar-milestone-pack/v1\",")
            appendLine("  \"milestone\": ${q(milestone)},")
            appendLine("  \"corpusVerdict\": ${q(FirmwareEvidenceCatalog.corpusVerdict.name)},")
            appendLine("  \"truthState\": ${q(FirmwareEvidenceCatalog.truthState)},")
            appendLine("  \"visualPass\": false,")
            appendLine("  \"candidateCount\": ${plans.size},")
            appendLine("  \"blockedCount\": $blocked,")
            appendLine("  \"unknownCount\": $unknown,")
            appendLine("  ${q(diagnosticKey)}: {")
            appendLine("    \"state\": \"INERT_DATA_ONLY\",")
            appendLine("    \"visualPass\": false,")
            appendLine("    \"plans\": [")
            plans.forEachIndexed { index, plan ->
                append(renderPlan(plan, "      "))
                appendLine(if (index == plans.lastIndex) "" else ",")
            }
            appendLine("    ]")
            appendLine("  },")
            appendLine("  ${q(productionKey)}: {")
            appendLine("    \"state\": \"BLOCKED_PENDING_FIELD_PROOF\",")
            appendLine("    \"capability\": \"UNKNOWN\",")
            appendLine("    \"visualPass\": false,")
            appendLine("    \"sourcePlanIds\": ${stringArray(plans.map { it.id })}")
            appendLine("  }")
            appendLine("}")
        }
    }

    fun renderCandidateReport(plans: List<CommandPlan>): String {
        val blocked = plans.count { it.disposition == PlanDisposition.BLOCKED }
        val unknown = plans.count { it.disposition == PlanDisposition.UNKNOWN }
        val rows = plans.joinToString("\n") { plan ->
            val issues = PropertyCandidateValidator.validate(plan.candidate).joinToString(", ")
                .ifEmpty { "none; field proof still absent" }
            """<tr><td><code>${h(plan.id)}</code></td><td>${plan.candidate.feature}</td><td><span class="${plan.disposition.name.lowercase()}">${plan.disposition}</span></td><td>${plan.candidate.risk}</td><td>${h(plan.candidate.evidenceIds.joinToString(", "))}</td><td>${h(issues)}</td></tr>"""
        }
        return """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ClusterNav Off-Car Candidate Report</title><style>
:root{color-scheme:light dark;--bg:#f5f5f7;--paper:#fff;--ink:#1d1d1f;--muted:#6e6e73;--line:#d2d2d7;--blue:#0071e3;--amber:#9a5b00;--red:#c9342f} @media(prefers-color-scheme:dark){:root{--bg:#09090b;--paper:#171719;--ink:#f5f5f7;--muted:#a1a1a6;--line:#38383c;--blue:#5ac8fa;--amber:#ffb340;--red:#ff6961}}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.55 -apple-system,BlinkMacSystemFont,"SF Pro Display",sans-serif}.wrap{max-width:1200px;margin:auto;padding:64px 24px 96px}h1{font-size:clamp(38px,6vw,64px);line-height:1.02;letter-spacing:-.045em;margin:.2em 0}.lead{max-width:900px;color:var(--muted);font-size:18px}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:30px 0}.card,.table{background:var(--paper);border:1px solid var(--line);border-radius:18px}.card{padding:18px}.card b{display:block;font-size:26px}.card span{color:var(--muted)}.table{overflow:auto}table{border-collapse:collapse;width:100%;min-width:950px}th,td{padding:12px 14px;text-align:left;vertical-align:top;border-bottom:1px solid var(--line)}th{font-size:11px;text-transform:uppercase;color:var(--muted)}tr:last-child td{border:0}code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}.unknown{color:var(--amber);font-weight:700}.blocked{color:var(--red);font-weight:700}.notice{border-left:4px solid var(--amber);padding:14px 18px;background:var(--paper);border-radius:12px;margin:24px 0}@media(max-width:760px){.stats{grid-template-columns:1fr 1fr}}
</style></head><body><main class="wrap"><p>ClusterNav · generated contract data</p><h1>Off-car candidate report</h1><p class="lead">Source-backed planning hypotheses only. No transport, no field execution, no capability promotion, and no visual PASS.</p><div class="stats"><div class="card"><b>${plans.size}</b><span>generated candidates</span></div><div class="card"><b>$blocked</b><span>blocked</span></div><div class="card"><b>$unknown</b><span>unknown</span></div><div class="card"><b>0</b><span>field-proven</span></div></div><div class="notice"><strong>Corpus: NOT_EXHAUSTIVE.</strong> Missing property registry/provider/QML and partition artifacts keep every capability UNKNOWN or explicitly blocked; none is UNSUPPORTED.</div><div class="table"><table><thead><tr><th>Plan</th><th>Feature</th><th>State</th><th>Risk</th><th>Evidence</th><th>Proof gaps</th></tr></thead><tbody>$rows</tbody></table></div></main></body></html>
"""
    }

    fun renderTraceability(): String {
        fun reverse(ids: Set<String>, selector: (TraceLink) -> Set<String>): String =
            ids.sorted().joinToString(",\n") { id ->
                "    ${q(id)}: ${stringArray(TraceabilityCatalog.links.filter { id in selector(it) }.map { it.requirement })}"
            }
        return buildString {
            appendLine("{")
            appendLine("  \"schema\": \"clusternav.hud-sign-traceability/v1\",")
            appendLine("  \"corpusVerdict\": \"NOT_EXHAUSTIVE\",")
            appendLine("  \"visualPass\": false,")
            appendLine("  \"requirements\": [")
            TraceabilityCatalog.links.forEachIndexed { index, link ->
                append("    {\"id\":${q(link.requirement)},\"status\":${q(link.status.name)},\"tasks\":${stringArray(link.tasks)},\"gates\":${stringArray(link.gates)},\"futureIds\":${stringArray(link.futureIds)},\"artifact\":${q(link.artifact)}}")
                appendLine(if (index == TraceabilityCatalog.links.lastIndex) "" else ",")
            }
            appendLine("  ],")
            appendLine("  \"tasksToRequirements\": {\n${reverse(TraceabilityCatalog.tasks) { it.tasks }}\n  },")
            appendLine("  \"gatesToRequirements\": {\n${reverse(TraceabilityCatalog.gates) { it.gates }}\n  },")
            appendLine("  \"futureToRequirements\": {\n${reverse(TraceabilityCatalog.futureIds) { it.futureIds }}\n  }")
            appendLine("}")
        }
    }

    private fun renderPlan(plan: CommandPlan, indent: String): String {
        val records = fakeTransport.capture(plan)
        val issues = PropertyCandidateValidator.validate(plan.candidate).map(Enum<*>::name).sorted()
        return buildString {
            appendLine("${indent}{")
            appendLine("$indent  \"id\": ${q(plan.id)},")
            appendLine("$indent  \"candidateId\": ${q(plan.candidate.id)},")
            appendLine("$indent  \"feature\": ${q(plan.candidate.feature.name)},")
            appendLine("$indent  \"disposition\": ${q(plan.disposition.name)},")
            appendLine("$indent  \"reason\": ${q(plan.reason)},")
            appendLine("$indent  \"risk\": ${q(plan.candidate.risk.name)},")
            appendLine("$indent  \"evidenceIds\": ${stringArray(plan.candidate.evidenceIds)},")
            appendLine("$indent  \"validationIssues\": ${stringArray(issues)},")
            appendLine("$indent  \"visualPass\": false,")
            appendLine("$indent  \"steps\": [")
            records.forEachIndexed { index, record ->
                append("$indent    {\"ordinal\":${record.ordinal},\"phase\":${q(record.phase)},\"detail\":${q(record.detail)}}")
                appendLine(if (index == records.lastIndex) "" else ",")
            }
            appendLine("$indent  ],")
            appendLine("$indent  \"textTranscript\": ${q(records.joinToString("\\n") { "${it.ordinal}. ${it.phase} ${it.detail}" })}")
            append("$indent}")
        }
    }

    private fun stringArray(values: Iterable<String>): String =
        values.toList().sorted().joinToString(prefix = "[", postfix = "]") { q(it) }

    private fun q(value: String): String = "\"" + value
        .replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

    private fun h(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}
