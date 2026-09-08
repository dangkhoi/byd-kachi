package com.byd.clusternav.offcar

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

object OffCarPlannerMain {
    private val packDefinitions = listOf(
        PackDefinition("M1_NAV_HUD", CandidateFeature.NAV_HUD, "D-M1-NAV-HUD", "P-M1-NAV-HUD", "m1-nav-hud-plan.json"),
        PackDefinition("M2_HUD_ROAD", CandidateFeature.HUD_ROAD, "D-M2-HUD-ROAD", "P-M2-HUD-ROAD", "m2-hud-road-plan.json"),
        PackDefinition("M3_CLUSTER_SIGN", CandidateFeature.CLUSTER_SIGN, "D-M3-CLUSTER-SIGN", "P-M3-CLUSTER-SIGN", "m3-cluster-sign-plan.json"),
        PackDefinition("M4_HUD_SIGN", CandidateFeature.HUD_SIGN, "D-M4-HUD-SIGN", "P-M4-HUD-SIGN", "m4-hud-sign-plan.json"),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty()) { "This generator accepts no command-line options" }
        val working = Path.of("").toAbsolutePath().normalize()
        val root = generateSequence(working) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
            ?: error("ClusterNav project root not found")
        val output = root.resolve("docs/diagnostics/hud-sign-re")
        val result = writeReports(output)
        println("generated=${result.candidateCount} blocked=${result.blockedCount} unknown=${result.unknownCount}")
    }

    fun writeReports(outputDirectory: Path): GenerationResult {
        val plans = CandidateScenarioGenerator().generate()
        val renderer = CommandPlanRenderer()
        Files.createDirectories(outputDirectory)
        write(outputDirectory.resolve("candidate-report.html"), renderer.renderCandidateReport(plans))
        write(outputDirectory.resolve("traceability.json"), renderer.renderTraceability())
        packDefinitions.forEach { definition ->
            write(
                outputDirectory.resolve(definition.fileName),
                renderer.renderMilestonePack(
                    definition.milestone,
                    definition.feature,
                    definition.diagnosticKey,
                    definition.productionKey,
                    plans,
                ),
            )
        }
        return GenerationResult(
            candidateCount = plans.size,
            blockedCount = plans.count { it.disposition == PlanDisposition.BLOCKED },
            unknownCount = plans.count { it.disposition == PlanDisposition.UNKNOWN },
            files = packDefinitions.map { it.fileName } + listOf("candidate-report.html", "traceability.json"),
        )
    }

    private fun write(path: Path, content: String) {
        Files.writeString(
            path,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    private data class PackDefinition(
        val milestone: String,
        val feature: CandidateFeature,
        val diagnosticKey: String,
        val productionKey: String,
        val fileName: String,
    )
}

data class GenerationResult(
    val candidateCount: Int,
    val blockedCount: Int,
    val unknownCount: Int,
    val files: List<String>,
)
