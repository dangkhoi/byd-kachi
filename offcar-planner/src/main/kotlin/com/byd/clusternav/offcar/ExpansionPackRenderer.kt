package com.byd.clusternav.offcar
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
data class ExpansionPackResult(val files: Map<String, ByteArray>, val packSha256: String, val manifestSelfSha256: String)
class ExpansionPackRenderer(private val projectRoot: Path) {
    companion object {
        const val OUTPUT_DIRECTORY = "docs/diagnostics/hud-sign-re/expansion"
        const val COVERAGE = "corpus-coverage.json"; const val BASELINE = "legacy-baseline.json"
        const val EVIDENCE = "evidence-map.json"; const val REGISTRY = "candidate-registry.json"
        const val DIFF = "candidate-diff.json"; const val REPORT = "candidate-expansion-report.html"
        const val PLAN = "vehicle-session-plan.json"; const val PLAN_TEXT = "vehicle-session-plan.txt"
        const val CHECKLIST = "vehicle-session-checklist.html"; const val LEDGER_SCHEMA = "result-ledger.schema.json"
        const val TRACE = "traceability.json"; const val MANIFEST = "pack-manifest.json"
        val OUTPUT_NAMES = setOf(BASELINE, COVERAGE, EVIDENCE, REGISTRY, DIFF, REPORT, PLAN, PLAN_TEXT,
            CHECKLIST, LEDGER_SCHEMA, TRACE, MANIFEST)
        private const val ZERO_SHA256 = "0000000000000000000000000000000000000000000000000000000000000000"
        private const val RENDERER_ID = "RENDERER-EXPANSION-X3"
    }
    private val root = projectRoot.toAbsolutePath().normalize()
    private val outputRelative = "$OUTPUT_DIRECTORY/"
    fun writePack(outputDirectory: Path): ExpansionPackResult {
        val output = prepareOutput(outputDirectory); val files = linkedMapOf<String, ByteArray>()
        fun publish(name: String, bytes: ByteArray) {
            require(name in OUTPUT_NAMES); val target = ExpansionPathFence.validate(output, output.resolve(name).normalize())
            require(target.parent == output) { "unsafe expansion output path" }; write(target, bytes)
            ExpansionPathFence.requireRegularInput(output, target); files[name] = bytes
        }
        val baselineBytes = LegacyBaselineIdentity.capture(root).canonicalBytes(); publish(BASELINE, baselineBytes)
        val coverageSource = ExpansionPathFence.requireRegularInput(root, root.resolve(outputRelative + COVERAGE).normalize())
        val coverageBytes = readNoFollow(coverageSource)
        if (coverageSource != output.resolve(COVERAGE)) publish(COVERAGE, coverageBytes) else files[COVERAGE] = coverageBytes
        val coverage = CoverageMetadata.parse(coverageBytes); val publishedCandidates = SourceBackedExpansionCatalog.publishedCandidates
        coverage.validateRegistry(publishedCandidates)
        val evidenceBytes = evidenceMap(baselineBytes, coverageBytes, coverage); publish(EVIDENCE, evidenceBytes)
        val registryModel = CandidateRegistryRoot.create(ExpansionHashing.sha256(baselineBytes),
            ExpansionHashing.sha256(coverageBytes), ExpansionHashing.sha256(evidenceBytes), publishedCandidates,
            SourceBackedExpansionCatalog.publishedRegistryHistory)
        val registryBytes = registryModel.canonicalJson().toByteArray(StandardCharsets.UTF_8); publish(REGISTRY, registryBytes)
        val diffBytes = candidateDiff(registryModel); publish(DIFF, diffBytes)
        val template = SessionTemplateGenerator.generate(registryModel.candidates, registryModel.history, DiscoveryProbeCatalog.all)
        ForwardPruneForest.create(registryModel.candidates, template.rows)
        val planDocument = vehiclePlan(template, coverageBytes, evidenceBytes, registryBytes); publish(PLAN, planDocument.bytes)
        publish(LEDGER_SCHEMA, resultLedgerSchema()); publish(REPORT, report(registryModel, coverage, diffBytes, registryBytes, coverageBytes))
        val planHash = ExpansionHashing.sha256(planDocument.bytes)
        publish(PLAN_TEXT, planText(template, planDocument.packSha256, planHash))
        publish(CHECKLIST, checklist(template, planDocument.bytes.decodeToString(), planHash)); publish(TRACE, traceability(files))
        val manifestDocument = manifest(files); publish(MANIFEST, manifestDocument.bytes); require(files.keys == OUTPUT_NAMES)
        val actual = Files.list(output).use { stream -> stream.map { it.fileName.toString() }.toList().toSet() }
        require(actual == OUTPUT_NAMES) { "expansion output directory must contain exactly the 12 fixed outputs" }
        return ExpansionPackResult(files.toMap(), planDocument.packSha256, manifestDocument.selfSha256)
    }
    private fun evidenceMap(baseline: ByteArray, coverageBytes: ByteArray, coverage: CoverageMetadata): ByteArray {
        val facts = coverage.factHits.entries.sortedBy(Map.Entry<String, List<String>>::key).map { (fact, hits) ->
            CanonicalJson.obj("factId" to JsonText(fact), "sourceHitIds" to strings(hits))
        }
        return rootWithSelf(
            "coverageFileSha256" to JsonText(ExpansionHashing.sha256(coverageBytes)),
            "edges" to CanonicalJson.array(emptyList()),
            "facts" to CanonicalJson.array(facts),
            "legacyBaselineFileSha256" to JsonText(ExpansionHashing.sha256(baseline)),
            "schemaId" to JsonText("clusternav.expansion-evidence-map/v1"),
        )
    }
    private fun candidateDiff(registry: CandidateRegistryRoot): ByteArray {
        val prior = registry.history.getOrNull(registry.history.lastIndex - 1)
        val priorIds = prior?.candidateRefs?.map(CandidateRevisionRef::candidateRevisionId).orEmpty().toSet()
        val added = registry.candidates.filter { it.candidateRevisionId !in priorIds }.map {
            CanonicalJson.obj("candidateRevisionId" to JsonText(it.candidateRevisionId), "revisionSha256" to JsonText(it.revisionSha256))
        }
        return rootWithSelf(
            "addedCandidates" to CanonicalJson.array(added),
            "currentRegistryRevisionSha256" to JsonText(registry.history.last().registryRevisionSha256),
            "priorRegistryRevisionSha256" to CanonicalJson.text(prior?.registryRevisionSha256),
            "proposalDispositions" to CanonicalJson.array(emptyList()),
            "schemaId" to JsonText("clusternav.expansion-candidate-diff/v1"),
        )
    }
    private data class PlanDocument(val bytes: ByteArray, val packSha256: String)
    private fun vehiclePlan(model: SessionTemplateModel, coverageBytes: ByteArray, evidenceBytes: ByteArray,
        registryBytes: ByteArray): PlanDocument {
        fun identity() = CanonicalJson.obj(
            "blockerIds" to strings(listOf("BLOCKER-MISSING-AUTHORIZED-T10-HANDOFF")),
            "resolvedExactIdentity" to JsonNull,
            "state" to JsonText("INERT_IDENTITY_BLOCKED"),
        )
        fun template(packSha256: String) = CanonicalJson.obj(
            "allowedCandidateRevisionIds" to strings(model.allowedCandidateRevisionIds),
            "allowedMutationCandidateRevisionIds" to strings(model.allowedMutationCandidateRevisionIds),
            "allowedProbeIds" to strings(model.allowedProbeIds),
            "budgetMs" to JsonInteger(model.budgetMs),
            "coverageFileSha256" to JsonText(ExpansionHashing.sha256(coverageBytes)),
            "evidenceMapFileSha256" to JsonText(ExpansionHashing.sha256(evidenceBytes)),
            "identityRequirement" to identity(),
            "packSha256" to JsonText(packSha256),
            "registryFileSha256" to JsonText(ExpansionHashing.sha256(registryBytes)),
            "revision" to JsonInteger(model.revision.toLong()),
            "rows" to CanonicalJson.array(model.rows.map(SessionRowTemplate::json)),
        )
        fun root(packSha256: String, selfSha256: String?) = JsonObject(buildList {
            add("schemaId" to JsonText("clusternav.expansion-vehicle-session-plan/v1"))
            if (selfSha256 != null) add("selfSha256" to JsonText(selfSha256))
            add("template" to template(packSha256))
        })
        val pack = CanonicalJson.digest(root(ZERO_SHA256, null))
        val self = CanonicalJson.digest(root(pack, null))
        return PlanDocument(CanonicalJson.bytes(root(pack, self)), pack)
    }
    private fun resultLedgerSchema(): ByteArray {
        val source = readNoFollow(ExpansionPathFence.requireRegularInput(root, root.resolve("offcar-planner/src/main/resources/expansion-contracts.schema.json").normalize())).toString(StandardCharsets.UTF_8)
        val marker = "\"${'$'}defs\":"
        require(source.startsWith("{$marker"))
        val start = marker.length + 1
        val end = matchingObjectEnd(source, start)
        val defs = source.substring(start, end)
        val rendered = "{\"${'$'}defs\":$defs,\"${'$'}id\":\"https://clusternav.invalid/schema/result-ledger.schema.json\"," +
            "\"${'$'}ref\":\"#/${'$'}defs/ResultLedgerSchemaRoot\",\"${'$'}schema\":\"https://json-schema.org/draft/2020-12/schema\"," +
            "\"title\":\"ClusterNav expansion result ledger schema\"}"
        return rendered.toByteArray(StandardCharsets.UTF_8)
    }
    private fun report(registry: CandidateRegistryRoot, coverage: CoverageMetadata, diffBytes: ByteArray,
        registryBytes: ByteArray, coverageBytes: ByteArray): ByteArray {
        val composite = CanonicalJson.digest(CanonicalJson.obj(
            "candidateDiffFileSha256" to JsonText(ExpansionHashing.sha256(diffBytes)),
            "coverageFileSha256" to JsonText(ExpansionHashing.sha256(coverageBytes)),
            "registryFileSha256" to JsonText(ExpansionHashing.sha256(registryBytes)),
        ))
        val envelope = CanonicalJson.render(CanonicalJson.obj(
            "renderSchemaId" to JsonText("clusternav.expansion-render-report/v1"),
            "rendererId" to JsonText(RENDERER_ID), "sourceCompositeSha256" to JsonText(composite),
        ))
        val summary = CanonicalJson.render(CanonicalJson.obj(
            "addedCandidateRevisionIds" to strings(addedCandidateIds(diffBytes)),
            "candidateRevisionIds" to strings(registry.candidates.map(CandidateRevision::candidateRevisionId)),
            "coverage" to CanonicalJson.array(coverage.entries.map { CanonicalJson.obj("corpusId" to JsonText(it.first), "status" to JsonText(it.second)) }),
        ))
        val candidateRows = registry.candidates.joinToString("") { candidate ->
            "<tr data-candidate-id=\"${candidate.candidateRevisionId}\"><td><code>${candidate.candidateRevisionId}</code></td>" +
                "<td>${candidate.input.milestone}</td><td>${candidate.input.mode}</td><td>${candidate.state}</td></tr>"
        }
        val coverageRows = coverage.entries.joinToString("") { (corpus, status) ->
            "<tr data-corpus-id=\"$corpus\"><td><code>$corpus</code></td><td>$status</td></tr>"
        }
        val html = """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ClusterNav Candidate Expansion</title><script>(function(){var k='clusternav-expansion-report-theme';var t=localStorage.getItem(k);var d=t?t==='dark':matchMedia('(prefers-color-scheme:dark)').matches;if(d)document.documentElement.classList.add('dark')})()</script><style>:root{color-scheme:light;--bg:#f5f5f7;--paper:#fff;--ink:#1d1d1f;--muted:#6e6e73;--line:#d2d2d7;--blue:#0071e3}html.dark{color-scheme:dark;--bg:#09090b;--paper:#171719;--ink:#f5f5f7;--muted:#a1a1a6;--line:#38383c;--blue:#5ac8fa}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.55 -apple-system,BlinkMacSystemFont,"SF Pro Display",sans-serif}.wrap{max-width:1100px;margin:auto;padding:64px 24px}h1{font-size:clamp(38px,6vw,62px);line-height:1.03;letter-spacing:-.04em}.lead{color:var(--muted);font-size:18px}.card,.table{background:var(--paper);border:1px solid var(--line);border-radius:18px}.card{padding:18px;margin:20px 0}.table{overflow:auto;margin:16px 0}table{border-collapse:collapse;width:100%}th,td{padding:11px 13px;text-align:left;border-bottom:1px solid var(--line)}th{color:var(--muted);font-size:11px;text-transform:uppercase}code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}.theme{float:right;border:1px solid var(--line);background:var(--paper);color:var(--ink);border-radius:999px;padding:8px 12px;cursor:pointer}</style></head><body><main class="wrap"><button class="theme" onclick="document.documentElement.classList.toggle('dark');localStorage.setItem('clusternav-expansion-report-theme',document.documentElement.classList.contains('dark')?'dark':'light')">◐ Theme</button><p>ClusterNav · deterministic metadata pack</p><h1>Candidate expansion</h1><p class="lead">Inert planning data only. Identity authorization is absent, so no session can be instantiated and no field result is claimed.</p><div class="card"><strong>Verdict: ${coverage.verdict}</strong><br>Registry revision ${registry.revision} · ${registry.candidates.size} immutable candidate revisions.</div><h2>Candidates</h2><div class="table"><table><thead><tr><th>ID</th><th>Milestone</th><th>Mode</th><th>State</th></tr></thead><tbody>$candidateRows</tbody></table></div><h2>Corpus coverage</h2><div class="table"><table><thead><tr><th>Corpus</th><th>Status</th></tr></thead><tbody>$coverageRows</tbody></table></div><script id="render-envelope" type="application/json">$envelope</script><script id="render-source-summary" type="application/json">$summary</script></main></body></html>"""
        return html.toByteArray(StandardCharsets.UTF_8)
    }
    private fun planText(model: SessionTemplateModel, packSha256: String, sourcePlanHash: String): ByteArray {
        val envelope = CanonicalJson.render(CanonicalJson.obj(
            "renderSchemaId" to JsonText("clusternav.expansion-render-plan-text/v1"),
            "rendererId" to JsonText(RENDERER_ID), "sourcePlanFileSha256" to JsonText(sourcePlanHash),
        ))
        val text = buildString {
            append("ENVELOPE ").append(envelope).append('\n')
            append("STATE INERT_IDENTITY_BLOCKED\nPACK ").append(packSha256).append('\n')
            append("BUDGET_MS ").append(model.budgetMs)
            model.rows.forEach { append('\n').append("ROW ").append(CanonicalJson.render(it.json())) }
        }
        return text.toByteArray(StandardCharsets.UTF_8)
    }
    private fun checklist(model: SessionTemplateModel, planJson: String, sourcePlanHash: String): ByteArray {
        val envelope = CanonicalJson.render(CanonicalJson.obj(
            "renderSchemaId" to JsonText("clusternav.expansion-render-checklist/v1"),
            "rendererId" to JsonText(RENDERER_ID), "sourcePlanFileSha256" to JsonText(sourcePlanHash),
        ))
        val rows = model.rows.joinToString("") { row ->
            "<tr data-row-id=\"${row.rowId}\"><td><code>${row.rowId}</code></td><td>${row.kind}</td><td>${row.candidateRevisionId ?: row.resultIdentityId ?: "DISCOVERY"}</td><td>${row.requiredSurfaces.joinToString(",")}</td></tr>"
        }
        val html = """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Vehicle Session Checklist</title><script>(function(){var k='clusternav-expansion-checklist-theme';var t=localStorage.getItem(k);var d=t?t==='dark':matchMedia('(prefers-color-scheme:dark)').matches;if(d)document.documentElement.classList.add('dark')})()</script><style>:root{color-scheme:light;--bg:#fff;--ink:#1d1d1f;--line:#d2d2d7}html.dark{color-scheme:dark;--bg:#171719;--ink:#f5f5f7;--line:#38383c}body{background:var(--bg);color:var(--ink);font:15px/1.5 -apple-system,BlinkMacSystemFont,"SF Pro Display",sans-serif;max-width:1100px;margin:auto;padding:48px 22px}table{border-collapse:collapse;width:100%}th,td{padding:10px;border-bottom:1px solid var(--line);text-align:left}code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}.blocked{padding:16px;border:1px solid #c66;border-radius:14px}.theme{float:right;border:1px solid var(--line);background:var(--bg);color:var(--ink);border-radius:999px;padding:8px 12px;cursor:pointer}</style></head><body><button class="theme" onclick="document.documentElement.classList.toggle('dark');localStorage.setItem('clusternav-expansion-checklist-theme',document.documentElement.classList.contains('dark')?'dark':'light')">◐ Theme</button><h1>Inert vehicle-session checklist</h1><p class="blocked"><strong>INERT_IDENTITY_BLOCKED.</strong> This checklist is metadata only and cannot instantiate a session.</p><table><thead><tr><th>Row</th><th>Kind</th><th>Binding</th><th>Surface</th></tr></thead><tbody>$rows</tbody></table><script id="render-envelope" type="application/json">$envelope</script><script id="source-plan" type="application/json">$planJson</script></body></html>"""
        return html.toByteArray(StandardCharsets.UTF_8)
    }
    private fun traceability(files: Map<String, ByteArray>): ByteArray {
        fun hash(name: String) = JsonText(ExpansionHashing.sha256(files.getValue(name)))
        val links = traceSpecs().map { spec -> CanonicalJson.obj(
            "artifactPaths" to strings(spec.artifacts.sorted()),
            "commands" to strings(spec.gates.map(::gateCommand).distinct().sorted()),
            "gateIds" to strings(spec.gates), "requirementId" to JsonText(spec.requirement),
            "taskIds" to strings(spec.tasks),
        ) }
        return rootWithSelf(
            "candidateDiffFileSha256" to hash(DIFF), "checklistFileSha256" to hash(CHECKLIST),
            "coverageFileSha256" to hash(COVERAGE), "evidenceMapFileSha256" to hash(EVIDENCE),
            "ledgerSchemaFileSha256" to hash(LEDGER_SCHEMA), "legacyBaselineFileSha256" to hash(BASELINE),
            "links" to CanonicalJson.array(links), "planFileSha256" to hash(PLAN),
            "planTextFileSha256" to hash(PLAN_TEXT), "registryFileSha256" to hash(REGISTRY),
            "reportFileSha256" to hash(REPORT), "schemaId" to JsonText("clusternav.expansion-traceability/v1"),
        )
    }
    private data class SealedDocument(val bytes: ByteArray, val selfSha256: String)
    private fun manifest(files: Map<String, ByteArray>): SealedDocument {
        require(MANIFEST !in files)
        val schemaIds = mapOf(
            BASELINE to "clusternav.expansion-legacy-baseline/v1", COVERAGE to "clusternav.expansion-corpus-coverage/v1",
            EVIDENCE to "clusternav.expansion-evidence-map/v1", REGISTRY to "clusternav.expansion-candidate-registry/v1",
            DIFF to "clusternav.expansion-candidate-diff/v1", REPORT to "clusternav.expansion-render-report/v1",
            PLAN to "clusternav.expansion-vehicle-session-plan/v1", PLAN_TEXT to "clusternav.expansion-render-plan-text/v1",
            CHECKLIST to "clusternav.expansion-render-checklist/v1", LEDGER_SCHEMA to "clusternav.expansion-result-ledger-schema/v1",
            TRACE to "clusternav.expansion-traceability/v1",
        )
        val entries = files.keys.sortedBy { outputRelative + it }.map { name -> CanonicalJson.obj(
            "fullSha256" to JsonText(ExpansionHashing.sha256(files.getValue(name))),
            "path" to JsonText(outputRelative + name), "schemaId" to JsonText(schemaIds.getValue(name)),
        ) }
        val fields = listOf(
            "entries" to CanonicalJson.array(entries), "schemaId" to JsonText("clusternav.expansion-pack-manifest/v1"),
            "selfPath" to JsonText(outputRelative + MANIFEST),
        )
        val self = CanonicalJson.digest(JsonObject(fields))
        return SealedDocument(CanonicalJson.bytes(JsonObject(fields + ("selfSha256" to JsonText(self)))), self)
    }
    private fun rootWithSelf(vararg fields: Pair<String, CanonicalValue>): ByteArray {
        val self = CanonicalJson.digest(JsonObject(fields.toList()))
        return CanonicalJson.bytes(JsonObject(fields.toList() + ("selfSha256" to JsonText(self))))
    }
    private fun prepareOutput(requested: Path): Path {
        val requestedAbsolute = requested.toAbsolutePath().normalize()
        val logicalTemporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
        val temporaryRoot = logicalTemporaryRoot.toRealPath()
        val output = if (requestedAbsolute.startsWith(logicalTemporaryRoot))
            temporaryRoot.resolve(logicalTemporaryRoot.relativize(requestedAbsolute)).normalize() else requestedAbsolute
        val trusted = when { output.startsWith(root) -> root.also { require(output == root.resolve(OUTPUT_DIRECTORY)) { "project output must use the canonical expansion directory" } }
            output != temporaryRoot && output.startsWith(temporaryRoot) -> temporaryRoot
            else -> throw IllegalArgumentException("expansion output must stay under the project or JVM temporary root") }
        ExpansionPathFence.validate(trusted, output); Files.createDirectories(output); ExpansionPathFence.validate(trusted, output)
        require(Files.isDirectory(output, NOFOLLOW_LINKS)) { "expansion output must be a regular directory" }; return output
    }
    private fun readNoFollow(path: Path): ByteArray = Files.newInputStream(path, StandardOpenOption.READ, NOFOLLOW_LINKS).use { it.readBytes() }
    private fun write(path: Path, bytes: ByteArray) {
        val options = arrayOf<OpenOption>(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, NOFOLLOW_LINKS)
        Files.newOutputStream(path, *options).use { it.write(bytes) }
    }
    private fun strings(values: Iterable<String>) = CanonicalJson.array(values.map(::JsonText))
    private fun matchingObjectEnd(text: String, start: Int): Int {
        require(text[start] == '{')
        var depth = 0; var quoted = false; var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quoted) { if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false }
            else when (char) { '"' -> quoted = true; '{' -> depth++; '}' -> if (--depth == 0) return index + 1 }
        }
        error("unterminated schema definitions")
    }
    private fun addedCandidateIds(diff: ByteArray): List<String> =
        Regex("\\\"candidateRevisionId\\\":\\\"([^\\\"]+)\\\"").findAll(diff.decodeToString()).map { it.groupValues[1] }.toList()
    private data class TraceSpec(val requirement: String, val tasks: List<String>, val gates: List<String>, val artifacts: List<String>)
    private fun traceSpecs(): List<TraceSpec> {
        fun s(n: Int, tasks: String, gates: String, vararg artifacts: String) = TraceSpec("REQ-X$n", tasks.split(','), gates.split(','), artifacts.toList())
        return listOf(
            s(1,"TASK-X0,TASK-X2","GATE-X-O1,GATE-X-O2,GATE-X-O9","$outputRelative$BASELINE"),
            s(2,"TASK-X1,TASK-X2","GATE-X-O3,GATE-X-O4","$outputRelative$COVERAGE"),
            s(3,"TASK-X1,TASK-X3","GATE-X-O5,GATE-X-O8","offcar-planner/src/main/kotlin/com/byd/clusternav/offcar/DiscoveryProbe.kt"),
            s(4,"TASK-X2,TASK-X3","GATE-X-O4,GATE-X-O8","offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionPromotionTest.kt"),
            s(5,"TASK-X2,TASK-X3","GATE-X-O6,GATE-X-O8","offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionPromotionTest.kt"),
            s(6,"TASK-X2,TASK-X4","GATE-X-O4,GATE-X-O6,GATE-X-O10","offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionPromotionTest.kt"),
            s(7,"TASK-X0,TASK-X2,TASK-X4","GATE-X-O1,GATE-X-O2,GATE-X-O9","$outputRelative$REGISTRY"),
            s(8,"TASK-X2,TASK-X3,TASK-X4","GATE-X-O1,GATE-X-O8,GATE-X-O9","$outputRelative$LEDGER_SCHEMA"),
            s(9,"TASK-X3,TASK-X4","GATE-X-O7,GATE-X-O9","offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/AdaptivePruningTest.kt"),
            s(10,"TASK-X3,TASK-X4","GATE-X-O4,GATE-X-O5,GATE-X-O7,GATE-X-O8","offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/SameSessionQuarantineTest.kt"),
            s(11,"TASK-X2,TASK-X3,TASK-X4","GATE-X-O4,GATE-X-O8","$outputRelative$PLAN"),
            s(12,"TASK-X3,TASK-X4","GATE-X-O7,GATE-X-O8","offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/AdaptivePruningTest.kt"),
            s(13,"TASK-X3,TASK-X4","GATE-X-O8,GATE-X-O9","$outputRelative$MANIFEST"),
            s(14,"TASK-X3,TASK-X4","GATE-X-O7,GATE-X-O9","$outputRelative$PLAN"),
            s(15,"TASK-X0,TASK-X1,TASK-X2,TASK-X3,TASK-X4,TASK-X5","GATE-X-O10","offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionTransportFenceTest.kt"),
            s(16,"TASK-X0,TASK-X1,TASK-X4,TASK-X5","GATE-X-O3,GATE-X-O5,GATE-X-O12","$outputRelative$COVERAGE"),
            s(17,"TASK-X1,TASK-X3,TASK-X4,TASK-X5","GATE-X-O11,GATE-X-O12","$outputRelative$LEDGER_SCHEMA","scripts/verify-hud-sign-candidate-expansion.sh"),
            s(18,"TASK-X4,TASK-X5","GATE-X-O1,GATE-X-O2,GATE-X-O3,GATE-X-O4,GATE-X-O5,GATE-X-O6,GATE-X-O7,GATE-X-O8,GATE-X-O9,GATE-X-O10,GATE-X-O11,GATE-X-O12","$outputRelative$TRACE","offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionTraceabilityTest.kt"),
        )
    }
    private fun gateCommand(id: String): String = when (id) {
        "GATE-X-O11" -> "scripts/verify-hud-sign-candidate-expansion.sh"
        "GATE-X-O1", "GATE-X-O2", "GATE-X-O3", "GATE-X-O4", "GATE-X-O5", "GATE-X-O6",
        "GATE-X-O7", "GATE-X-O8", "GATE-X-O9", "GATE-X-O10", "GATE-X-O12" ->
            "CLUSTERNAV_EXPANSION_GATE=$id scripts/verify-hud-sign-candidate-expansion.sh"
        else -> error("unknown gate ID")
    }
}
internal data class CoverageCandidateDerivation(val candidateRevisionId: String, val state: CandidateState,
    val normalizedFactIds: List<String>, val claim: String)
internal data class CoverageMetadata(val entries: List<Pair<String, String>>, val verdict: String,
    val factHits: Map<String, List<String>>, val candidateDerivations: List<CoverageCandidateDerivation>) {
    fun validateRegistry(candidates: List<CandidateRevision>) {
        CandidateRevisionChains.validate(candidates)
        val byId = candidates.associateBy(CandidateRevision::candidateRevisionId)
        candidateDerivations.forEach { derivation ->
            val candidate = requireNotNull(byId[derivation.candidateRevisionId]) { "coverage references an unknown candidate" }
            require(candidate.state == derivation.state) { "coverage candidate state mismatch" }
            val proof = candidate.input.proof
            require(proof.evidenceIds.all { it in factHits }) { "candidate evidence is unresolved" }
            require(proof.evidenceIds == derivation.normalizedFactIds) { "candidate evidence must equal its derivation fact union" }
            val expected = CanonicalJson.render(JsonObject(proof.json().fields.filterNot { it.first == "evidenceIds" }))
            require(derivation.claim == expected) { "coverage promotion proof claim mismatch" }
        }
        candidates.flatMap { it.input.proof.evidenceIds }.forEach {
            require(it.startsWith("FACT-") && it in factHits) { "candidate evidence is unresolved: $it" }
        }
        val latest = candidates.groupBy { ExpansionIds.candidate(it.candidateRevisionId).baseId }
            .values.map { it.maxBy(CandidateRevision::revision) }
        require(candidateDerivations.map { it.candidateRevisionId }.toSet() ==
            latest.filter { it.state != CandidateState.DISCOVERED }.map { it.candidateRevisionId }.toSet()) {
            "latest reviewed candidate derivations are not closed by coverage"
        }
        val registryVerdict = if (entries.all { it.second == "AVAILABLE" } && latest.none { it.state in OPEN }) "READY_DATA" else "NOT_EXHAUSTIVE"
        require(verdict == registryVerdict) { "coverage verdict does not reflect latest registry candidates" }
    }
    companion object {
        private val CORPORA = (1..12).map { "C${it.toString().padStart(2, '0')}" }
        private val ALIASES = listOf("ALIAS-PARENT-EVIDENCE-INDEX", "ALIAS-PARENT-EVIDENCE-INDEX", "ALIAS-PARENT-EVIDENCE-INDEX", "ALIAS-PARENT-NATIVE-REPORT", "ALIAS-PARENT-EVIDENCE-INDEX", "ALIAS-C06-VENDOR-HAL-CORPUS", "ALIAS-C07-PARTITION-CORPUS", "ALIAS-C08-PROVIDER-LIB-CORPUS", "ALIAS-C09-PROPERTY-REGISTRY-CORPUS", "ALIAS-C10-SERVICE-SELINUX-CORPUS", "ALIAS-C11-ISA-PROVIDER-CORPUS", "ALIAS-C12-QML-RCC-CORPUS")
        private val QUERIES = listOf("QRY-C01-AMAP-NAVI-METADATA", "QRY-C02-SETTINGS-DICAR-METADATA", "QRY-C03-FRAMEWORK-HUD-SIGN-METADATA", "QRY-C04-NATIVE-CLUSTER-METADATA", "QRY-C05-REFERENCE-APPS-METADATA", "QRY-C06-VENDOR-HAL-INVENTORY", "QRY-C07-PARTITION-INVENTORY", "QRY-C08-PROVIDER-LIB-INVENTORY", "QRY-C09-PROPERTY-REGISTRY-INVENTORY", "QRY-C10-SERVICE-SELINUX-INVENTORY", "QRY-C11-ISA-PROVIDER-INVENTORY", "QRY-C12-QML-RCC-INVENTORY")
        private val SHA = Regex("^[0-9a-f]{64}$"); private val ALIAS = Regex("^ALIAS-[A-Z0-9][A-Z0-9-]{0,63}$")
        private val TOKEN = Regex("^TOKEN-[A-Z0-9][A-Z0-9-]{0,63}$"); private val FACT = Regex(ExpansionIds.FACT_PATTERN)
        private val QUERY = Regex(ExpansionIds.QUERY_PATTERN); private val SELECTOR = Regex(ExpansionIds.SELECTOR_PATTERN)
        private val CONSUMER = Regex("^CONSUMER-[A-Z0-9][A-Z0-9-]{0,63}$")
        private val HIT = Regex("^HIT-(C(?:0[1-9]|1[0-2]))-(QRY-C(?:0[1-9]|1[0-2])-[A-Z0-9-]+)-A([0-9a-f]{12})-S([0-9a-f]{12})-Q([0-9a-f]{12})-L([0-9a-f]{12})-T([0-9a-f]{12})$")
        private val ROOT = setOf("entries", "expansionVerdict", "schemaId", "selfSha256")
        private val ENTRY = setOf("artifactAlias", "artifactSha256", "corpusId", "hits", "query", "scanner", "status", "zeroHit", "zeroHitOutcome")
        private val SCANNER = setOf("binaryFileSha256", "configFileSha256", "scannerIdentitySha256", "toolId", "versionId")
        private val QUERY_FIELDS = setOf("parameters", "queryDefinitionSha256", "queryId", "scannerIdentitySha256", "tokenIds")
        private val HIT_FIELDS = setOf("consumerIds", "disposition", "duplicateEquivalenceSha256", "hitId", "locationAlias", "locationSha256", "normalizedFactIds", "promotionProofClaim", "selectorIds", "tokenIds", "tokenSetSha256")
        private val CLAIM = setOf("absoluteRejects", "access", "boundedDomainValueIds", "clearOperationId", "clearPolicy", "configId", "consumerId", "inverseOperationIds", "javaType", "mutationOperationId", "ownership", "permissionId", "priorReadOperationId", "providerId", "readBackOperationId", "readProbeId", "risk", "selectorId", "transportId")
        private val REJECTS = listOf("RAW_SELECTOR", "FREE_FORM_SELECTOR", "MASS_MUTATION", "GUESSED_ENUM", "RETAINED_STATE_DEPENDENCY", "WEAK_EVIDENCE_ONLY")
        private val STATUSES = setOf("AVAILABLE", "UNAVAILABLE", "UNSEARCHED", "BUDGET_STOPPED", "ACQUIRED_UNREVIEWED")
        private val OPEN = setOf(CandidateState.DISCOVERED, CandidateState.SOURCE_BACKED, CandidateState.MUTATION_REVIEW)
        private const val EMPTY_SHA = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        fun parse(bytes: ByteArray): CoverageMetadata {
            val root = closed(StrictCoverageJson.parse(bytes), ROOT, "coverage root")
            require(text(root["schemaId"]) == "clusternav.expansion-corpus-coverage/v1") { "unknown coverage schema" }
            val declaredSelf = sha(root["selfSha256"])
            require(declaredSelf == StrictCoverageJson.hash(root.filterKeys { it != "selfSha256" })) { "coverage self hash mismatch" }
            val rawEntries = array(root["entries"]); require(rawEntries.size == 12) { "coverage requires exactly C01-C12" }
            val facts = sortedMapOf<String, MutableSet<String>>(); val allHits = linkedMapOf<String, Hit>()
            val candidates = linkedMapOf<String, CandidateAccumulator>(); val uniqueness = mutableSetOf<List<String?>>()
            val pairs = rawEntries.mapIndexed { index, raw ->
                val entry = closed(raw, ENTRY, "coverage entry"); val corpus = text(entry["corpusId"])
                require(corpus == CORPORA[index]) { "coverage entries must be C01-C12 in order" }
                val alias = text(entry["artifactAlias"]); require(alias == ALIASES[index] && ALIAS.matches(alias)) { "invalid artifact alias" }
                val artifact = nullableSha(entry["artifactSha256"]); val scannerHash = scanner(entry["scanner"])
                val (queryId, queryHash) = query(entry["query"], index, scannerHash)
                val rawHits = array(entry["hits"]); require(rawHits.size <= 65535) { "too many coverage hits" }
                val status = text(entry["status"]); require(status in STATUSES) { "unknown coverage status" }
                val zero = nullableBoolean(entry["zeroHit"]); val outcome = nullableText(entry["zeroHitOutcome"])
                require(outcome == null || outcome == "NO_MATCH") { "unknown zero-hit outcome" }
                when (status) {
                    "AVAILABLE" -> require(artifact != null && zero == rawHits.isEmpty() && outcome == if (rawHits.isEmpty()) "NO_MATCH" else null) { "AVAILABLE cardinality mismatch" }
                    "UNAVAILABLE", "UNSEARCHED" -> require(artifact == null && rawHits.isEmpty() && zero == null && outcome == null) { "$status cardinality mismatch" }
                    "BUDGET_STOPPED" -> require(artifact != null && zero == null && outcome == null) { "BUDGET_STOPPED cardinality mismatch" }
                    else -> require(artifact != null && rawHits.isEmpty() && zero == null && outcome == null) { "ACQUIRED_UNREVIEWED cardinality mismatch" }
                }
                require(uniqueness.add(listOf(corpus, alias, artifact, queryId, queryHash, scannerHash))) { "duplicate coverage tuple" }
                val hits = rawHits.map { hit(it, corpus, alias, artifact, scannerHash, queryId, queryHash) }
                require(hits.map(Hit::id) == hits.map(Hit::id).sorted()) { "hits are not canonically sorted" }
                hits.forEach { parsed ->
                    require(allHits.put(parsed.id, parsed) == null) { "duplicate global hit ID" }
                    parsed.facts.forEach { facts.getOrPut(it) { sortedSetOf() }.add(parsed.id) }
                    if (parsed.disposition.kind == "CANDIDATE_DERIVATION") {
                        val id = requireNotNull(parsed.disposition.candidateId); val claim = requireNotNull(parsed.claim)
                        val old = candidates[id]
                        if (old == null) candidates[id] = CandidateAccumulator(requireNotNull(parsed.disposition.state), claim, parsed.facts.toSortedSet())
                        else { require(old.state == parsed.disposition.state && old.claim == claim) { "conflicting candidate promotion proof claims" }; old.facts += parsed.facts }
                    }
                }
                corpus to status
            }
            allHits.values.groupBy(Hit::equivalence).values.forEach { members ->
                val ordered = members.sortedBy(Hit::id); require(ordered.map(Hit::claim).toSet().size == 1) { "duplicate class claims conflict" }
                require(ordered.first().disposition.kind != "DUPLICATE_OF") { "minimal duplicate representative is not canonical" }
                ordered.drop(1).forEach { require(it.disposition.raw == mapOf("canonicalHitId" to ordered.first().id, "kind" to "DUPLICATE_OF")) { "invalid duplicate representative" } }
            }
            val derivations = candidates.map { (id, value) -> CoverageCandidateDerivation(id, value.state, value.facts.toList(), value.claim) }
            val verdict = text(root["expansionVerdict"]); require(verdict == "READY_DATA" || verdict == "NOT_EXHAUSTIVE") { "unknown expansion verdict" }
            val expected = if (pairs.all { it.second == "AVAILABLE" } && derivations.none { it.state in OPEN }) "READY_DATA" else "NOT_EXHAUSTIVE"
            require(verdict == expected) { "coverage verdict is not derived" }
            return CoverageMetadata(pairs, verdict, facts.mapValues { it.value.toList() }, derivations)
        }
        private fun scanner(value: Any?): String {
            val scanner = closed(value, SCANNER, "scanner"); val binary = sha(scanner["binaryFileSha256"])
            require(sha(scanner["configFileSha256"]) == EMPTY_SHA && text(scanner["toolId"]) == "TOOL-EXPANSION-COVERAGE-SCANNER" && text(scanner["versionId"]) == "VERSION-X1-2") { "fixed scanner identity mismatch" }
            val identity = sha(scanner["scannerIdentitySha256"])
            require(identity == StrictCoverageJson.hash(mapOf("binaryFileSha256" to binary, "configFileSha256" to EMPTY_SHA, "toolId" to "TOOL-EXPANSION-COVERAGE-SCANNER", "versionId" to "VERSION-X1-2"))) { "scanner identity hash mismatch" }
            return identity
        }
        private fun query(value: Any?, index: Int, scannerHash: String): Pair<String, String> {
            val query = closed(value, QUERY_FIELDS, "query"); require(array(query["parameters"]).isEmpty()) { "fixed query parameters must be empty" }
            val id = text(query["queryId"]); require(id == QUERIES[index] && QUERY.matches(id)) { "fixed query ID mismatch" }
            val tokens = ids(query["tokenIds"], TOKEN, 1, 256); require(tokens == listOf("TOKEN-${CORPORA[index]}-QUERY")) { "fixed query tokens mismatch" }
            require(sha(query["scannerIdentitySha256"]) == scannerHash) { "query scanner mismatch" }
            val digest = sha(query["queryDefinitionSha256"])
            require(digest == StrictCoverageJson.hash(mapOf("parameters" to emptyList<Any?>(), "queryId" to id, "scannerIdentitySha256" to scannerHash, "tokenIds" to tokens))) { "query definition hash mismatch" }
            return id to digest
        }
        private fun hit(value: Any?, corpus: String, alias: String, artifact: String?, scanner: String, queryId: String, queryHash: String): Hit {
            val hit = closed(value, HIT_FIELDS, "coverage hit"); val id = text(hit["hitId"]); val match = requireNotNull(HIT.matchEntire(id)) { "invalid hit ID" }
            val tokens = ids(hit["tokenIds"], TOKEN, 1, 64); val facts = ids(hit["normalizedFactIds"], FACT, 0, 64)
            val selectors = ids(hit["selectorIds"], SELECTOR, 0, 64); val consumers = ids(hit["consumerIds"], CONSUMER, 0, 64)
            val locationAlias = text(hit["locationAlias"]); require(ALIAS.matches(locationAlias)) { "invalid location alias" }
            val disposition = disposition(hit["disposition"]); val claimMap = hit["promotionProofClaim"]?.let(::claim)
            require((disposition.kind == "CANDIDATE_DERIVATION") == (claimMap != null)) { "candidate claim cardinality mismatch" }
            require(selectors == listOfNotNull(claimMap?.get("selectorId") as String?) && consumers == listOfNotNull(claimMap?.get("consumerId") as String?)) { "selector/consumer claim mismatch" }
            val locationHash = StrictCoverageJson.hash(mapOf("artifactAlias" to alias, "locationAlias" to locationAlias))
            val tokenHash = StrictCoverageJson.hash(tokens); val equivalence = StrictCoverageJson.hash(mapOf("consumerIds" to consumers, "corpusId" to corpus, "normalizedFactIds" to facts, "queryDefinitionSha256" to queryHash, "selectorIds" to selectors))
            require(sha(hit["locationSha256"]) == locationHash && sha(hit["tokenSetSha256"]) == tokenHash && sha(hit["duplicateEquivalenceSha256"]) == equivalence) { "hit derived hash mismatch" }
            require(artifact != null && match.groupValues[1] == corpus && match.groupValues[2] == queryId && match.groupValues.subList(3, 8) == listOf(artifact.take(12), scanner.take(12), queryHash.take(12), locationHash.take(12), tokenHash.take(12))) { "hit identity preimage mismatch" }
            return Hit(id, facts, disposition, claimMap?.let(StrictCoverageJson::render), equivalence)
        }
        private fun disposition(value: Any?): Disposition {
            val raw = value as? Map<*, *> ?: throw IllegalArgumentException("disposition must be an object")
            val kind = text(raw["kind"]); val fields = when (kind) {
                "CANDIDATE_DERIVATION" -> setOf("candidateRevisionId", "candidateState", "kind")
                "READ_ONLY_PROBE" -> setOf("kind", "probeId"); "BLOCKER" -> setOf("blockerId", "kind")
                "DUPLICATE_OF" -> setOf("canonicalHitId", "kind"); "OUT_OF_SCOPE" -> setOf("kind", "ruleId")
                else -> throw IllegalArgumentException("unknown hit disposition")
            }
            val closed = closed(raw, fields, "disposition")
            return when (kind) {
                "CANDIDATE_DERIVATION" -> { val id = text(closed["candidateRevisionId"]); ExpansionIds.candidate(id); val state = enumValue<CandidateState>(closed["candidateState"]); Disposition(closed, kind, id, state) }
                "READ_ONLY_PROBE" -> Disposition(closed.also { require(Regex(ExpansionIds.PROBE_PATTERN).matches(text(it["probeId"]))) }, kind)
                "BLOCKER" -> Disposition(closed.also { require(Regex("^BLOCKER-[A-Z0-9][A-Z0-9-]{0,63}$").matches(text(it["blockerId"]))) }, kind)
                "DUPLICATE_OF" -> Disposition(closed.also { require(HIT.matches(text(it["canonicalHitId"]))) }, kind)
                else -> Disposition(closed.also { require(Regex("^RULE-[A-Z0-9][A-Z0-9-]{0,63}$").matches(text(it["ruleId"]))) }, kind)
            }
        }
        private fun claim(value: Any?): Map<String, Any?> {
            val claim = closed(value, CLAIM, "promotionProofClaim"); val rejects = strings(claim["absoluteRejects"], 0, 6)
            require(rejects.distinct().size == rejects.size && rejects == rejects.sortedBy(REJECTS::indexOf) && rejects.all { it in REJECTS }) { "invalid absolute rejects" }
            enumText(claim["access"], setOf("READ_ONLY", "READ_WRITE", "WRITE"), true); enumText(claim["javaType"], setOf("INT", "BOOLEAN", "DOUBLE", "STRING", "BYTES"), true)
            enumText(claim["ownership"], setOf("APP_OWNED", "PHYSICAL_DURABLE", "DIAGNOSTIC_TEMP"), true); enumText(claim["clearPolicy"], setOf("REQUIRED", "NOT_APPLICABLE"), false)
            optionalId(claim, "selectorId", ExpansionIds.SELECTOR_PATTERN); optionalId(claim, "readProbeId", ExpansionIds.PROBE_PATTERN)
            optionalId(claim, "mutationOperationId", "^OP-MUTATE-[A-Z0-9][A-Z0-9-]{0,63}$"); optionalId(claim, "configId", "^CONFIG-[A-Z0-9][A-Z0-9-]{0,63}$")
            optionalId(claim, "providerId", "^PROVIDER-[A-Z0-9][A-Z0-9-]{0,63}$"); optionalId(claim, "permissionId", "^PERMISSION-[A-Z0-9][A-Z0-9-]{0,63}$")
            optionalId(claim, "transportId", "^TRANSPORT-[A-Z0-9][A-Z0-9-]{0,63}$"); optionalId(claim, "consumerId", "^CONSUMER-[A-Z0-9][A-Z0-9-]{0,63}$")
            optionalId(claim, "priorReadOperationId", "^OP-READ-[A-Z0-9][A-Z0-9-]{0,63}$"); optionalId(claim, "readBackOperationId", "^OP-READ-[A-Z0-9][A-Z0-9-]{0,63}$"); optionalId(claim, "clearOperationId", "^OP-CLEAR-[A-Z0-9][A-Z0-9-]{0,63}$")
            ids(claim["boundedDomainValueIds"], Regex("^VALUE-[A-Z0-9][A-Z0-9-]{0,63}$"), 0, 256)
            ids(claim["inverseOperationIds"], Regex("^OP-(?:INVERSE|RESTORE)-[A-Z0-9][A-Z0-9-]{0,63}$"), 0, 64, false)
            val risk = claim["risk"]; require(risk == null || risk is Long && risk in 0L..100L) { "invalid promotion proof risk" }
            return claim
        }
        @Suppress("UNCHECKED_CAST") private fun closed(value: Any?, fields: Set<String>, label: String): Map<String, Any?> {
            val result = value as? Map<*, *> ?: throw IllegalArgumentException("$label must be an object")
            require(result.keys.all { it is String } && result.keys == fields) { "$label has extra or missing keys" }
            return result as Map<String, Any?>
        }
        private fun array(value: Any?): List<Any?> = value as? List<Any?> ?: throw IllegalArgumentException("expected array")
        private fun text(value: Any?): String = value as? String ?: throw IllegalArgumentException("expected string")
        private fun nullableText(value: Any?): String? = if (value == null) null else text(value)
        private fun nullableBoolean(value: Any?): Boolean? { require(value == null || value is Boolean) { "expected nullable boolean" }; return value }
        private fun sha(value: Any?): String = text(value).also { require(SHA.matches(it)) { "invalid SHA-256" } }
        private fun nullableSha(value: Any?): String? = if (value == null) null else sha(value)
        private fun strings(value: Any?, min: Int, max: Int): List<String> = array(value).also { require(it.size in min..max) }.map(::text)
        private fun ids(value: Any?, pattern: Regex, min: Int, max: Int, sorted: Boolean = true): List<String> = strings(value, min, max).also { require(it.distinct().size == it.size && (!sorted || it == it.sorted()) && it.all(pattern::matches)) { "invalid canonical ID array" } }
        private fun optionalId(claim: Map<String, Any?>, field: String, pattern: String) { nullableText(claim[field])?.let { require(Regex(pattern).matches(it)) { "invalid $field" } } }
        private fun enumText(value: Any?, allowed: Set<String>, nullable: Boolean): String? { if (value == null) { require(nullable); return null }; return text(value).also { require(it in allowed) { "unknown enum value" } } }
        private inline fun <reified T : Enum<T>> enumValue(value: Any?): T = enumValues<T>().singleOrNull { it.name == text(value) } ?: throw IllegalArgumentException("unknown enum value")
        private data class Disposition(val raw: Map<String, Any?>, val kind: String, val candidateId: String? = null, val state: CandidateState? = null)
        private data class Hit(val id: String, val facts: List<String>, val disposition: Disposition, val claim: String?, val equivalence: String)
        private data class CandidateAccumulator(val state: CandidateState, val claim: String, val facts: java.util.SortedSet<String>)
    }
}
private object StrictCoverageJson {
    fun parse(bytes: ByteArray): Any? {
        require(bytes.isNotEmpty()) { "empty JSON" }
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try { decoder.decode(ByteBuffer.wrap(bytes)).toString() } catch (error: CharacterCodingException) { throw IllegalArgumentException("invalid UTF-8", error) }
        val value = Reader(text).read(); require(bytes.contentEquals(canonical(value))) { "JSON is not canonical" }; return value
    }
    fun render(value: Any?): String = canonical(value).toString(StandardCharsets.UTF_8)
    fun canonical(value: Any?): ByteArray = CanonicalJson.bytes(model(value))
    fun hash(value: Any?): String = ExpansionHashing.sha256(canonical(value))
    private fun model(value: Any?): CanonicalValue = when (value) {
        null -> JsonNull; is Boolean -> JsonBoolean(value); is Long -> JsonInteger(value); is String -> JsonText(value)
        is List<*> -> JsonArray(value.map(::model)); is Map<*, *> -> JsonObject(value.entries.map { (it.key as? String ?: throw IllegalArgumentException("non-string key")) to model(it.value) })
        else -> throw IllegalArgumentException("unsupported JSON value")
    }
    private class Reader(private val source: String) {
        private var index = 0
        fun read(): Any? = value().also { require(index == source.length) { "trailing JSON" } }
        private fun value(): Any? = when (source.getOrNull(index)) {
            '{' -> objectValue(); '[' -> arrayValue(); '"' -> stringValue(); 't' -> literal("true", true); 'f' -> literal("false", false); 'n' -> literal("null", null)
            '-', in '0'..'9' -> numberValue(); else -> throw IllegalArgumentException("invalid JSON at $index")
        }
        private fun objectValue(): Map<String, Any?> { expect('{'); val result = linkedMapOf<String, Any?>(); if (take('}')) return result
            while (true) { val key = stringValue(); expect(':'); require(!result.containsKey(key)) { "duplicate JSON key" }; result[key] = value(); if (take('}')) return result; expect(',') } }
        private fun arrayValue(): List<Any?> { expect('['); val result = mutableListOf<Any?>(); if (take(']')) return result
            while (true) { result += value(); if (take(']')) return result; expect(',') } }
        private fun stringValue(): String { expect('"'); val result = StringBuilder(); while (true) { val char = next(); when (char) {
            '"' -> return result.toString(); '\\' -> result.append(escape()); else -> { require(char.code >= 0x20) { "unescaped control" }; result.append(char) }
        } } }
        private fun escape(): String = when (val char = next()) { '"', '\\', '/' -> char.toString(); 'b' -> "\b"; 'f' -> "\u000c"; 'n' -> "\n"; 'r' -> "\r"; 't' -> "\t"; 'u' -> unicode(); else -> throw IllegalArgumentException("invalid escape") }
        private fun unicode(): String { val first = hex(); if (first.isHighSurrogate()) { require(source.startsWith("\\u", index)) { "missing low surrogate" }; index += 2; val second = hex(); require(second.isLowSurrogate()) { "malformed surrogate pair" }; return "$first$second" }; require(!first.isLowSurrogate()) { "lone low surrogate" }; return first.toString() }
        private fun hex(): Char { require(index + 4 <= source.length); val raw = source.substring(index, index + 4); require(raw.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "invalid Unicode escape" }; index += 4; return raw.toInt(16).toChar() }
        private fun numberValue(): Long { val start = index; take('-'); require(source.getOrNull(index) in '0'..'9'); if (source[index] == '0') index++ else while (source.getOrNull(index) in '0'..'9') index++; require(source.getOrNull(index) !in listOf('.', 'e', 'E')) { "floating-point values are forbidden" }; return source.substring(start, index).toLongOrNull() ?: throw IllegalArgumentException("integer outside signed-64 range") }
        private fun <T> literal(expected: String, value: T): T { require(source.startsWith(expected, index)); index += expected.length; return value }
        private fun next(): Char = source.getOrNull(index++) ?: throw IllegalArgumentException("unexpected end of JSON")
        private fun expect(char: Char) { require(take(char)) { "expected $char at $index" } }
        private fun take(char: Char): Boolean = if (source.getOrNull(index) == char) { index++; true } else false
    }
}
