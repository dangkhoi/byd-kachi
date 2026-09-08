package com.byd.clusternav.offcar

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ExpansionDeterminismTest {
    @TempDir
    lateinit var temp: Path

    private val root: Path get() = Path.of(System.getProperty("clusternav.root")).toAbsolutePath().normalize()

    @Test
    fun `two fresh directories are byte identical with exact output set`() {
        val renderer = renderer()
        val first = temp.resolve("first")
        val second = temp.resolve("second")
        val firstResult = renderer.writePack(first)
        val secondResult = renderer.writePack(second)

        assertEquals(ExpansionPackRenderer.OUTPUT_NAMES, firstResult.files.keys)
        assertEquals(ExpansionPackRenderer.OUTPUT_NAMES, directoryNames(first))
        assertEquals(ExpansionPackRenderer.OUTPUT_NAMES, directoryNames(second))
        ExpansionPackRenderer.OUTPUT_NAMES.forEach { name ->
            val firstBytes = Files.readAllBytes(first.resolve(name))
            val secondBytes = Files.readAllBytes(second.resolve(name))
            assertArrayEquals(firstBytes, secondBytes, name)
            assertFalse(firstBytes.lastOrNull() == '\n'.code.toByte(), name)
        }
        assertEquals(firstResult.packSha256, secondResult.packSha256)
        assertEquals(firstResult.manifestSelfSha256, secondResult.manifestSelfSha256)
    }

    @Test
    fun `root self pack and manifest hashes independently reproduce finalized bytes`() {
        val output = temp.resolve("hashes")
        val result = renderer().writePack(output)
        val selfHashed = listOf(
            ExpansionPackRenderer.BASELINE, ExpansionPackRenderer.COVERAGE, ExpansionPackRenderer.EVIDENCE,
            ExpansionPackRenderer.REGISTRY, ExpansionPackRenderer.DIFF, ExpansionPackRenderer.PLAN,
            ExpansionPackRenderer.TRACE, ExpansionPackRenderer.MANIFEST,
        )
        selfHashed.forEach { name ->
            val text = Files.readString(output.resolve(name))
            val declared = stringField(text, "selfSha256")
            assertEquals(declared, sha256(removeRootStringField(text, "selfSha256").encodeToByteArray()), name)
        }

        val planText = Files.readString(output.resolve(ExpansionPackRenderer.PLAN))
        val declaredPack = stringField(planText, "packSha256")
        val packProjection = removeRootStringField(planText, "selfSha256")
            .replace(Regex("\\\"packSha256\\\":\\\"[0-9a-f]{64}\\\""),
                "\"packSha256\":\"${"0".repeat(64)}\"")
        assertEquals(declaredPack, sha256(packProjection.encodeToByteArray()))
        assertEquals(result.packSha256, declaredPack)

        val manifestText = Files.readString(output.resolve(ExpansionPackRenderer.MANIFEST))
        val entries = manifestEntries(manifestText)
        assertEquals(11, entries.size)
        assertEquals(entries.map(ManifestEntry::path).sorted(), entries.map(ManifestEntry::path))
        assertEquals(
            ExpansionPackRenderer.OUTPUT_NAMES - ExpansionPackRenderer.MANIFEST,
            entries.map { Path.of(it.path).fileName.toString() }.toSet(),
        )
        entries.forEach { entry ->
            val name = Path.of(entry.path).fileName.toString()
            assertEquals(sha256(Files.readAllBytes(output.resolve(name))), entry.fullSha256, entry.path)
        }
        assertFalse(entries.any { it.path.endsWith("/${ExpansionPackRenderer.MANIFEST}") })
        result.files.filterKeys { it != ExpansionPackRenderer.MANIFEST }.forEach { (name, bytes) ->
            assertFalse(bytes.decodeToString().contains(result.manifestSelfSha256), name)
        }
    }

    @Test
    fun `template is inert has total eligible projection and contains no runtime session values`() {
        val output = temp.resolve("template")
        renderer().writePack(output)
        val plan = Files.readString(output.resolve(ExpansionPackRenderer.PLAN))

        assertTrue(plan.contains("\"state\":\"INERT_IDENTITY_BLOCKED\""))
        assertTrue(plan.contains("\"resolvedExactIdentity\":null"))
        listOf("sessionId", "sessionInstanceSha256", "sessionStartElapsedMs", "deadlineElapsedMs", "exactIdentity")
            .forEach { assertFalse(plan.contains("\"$it\""), it) }
        val rowIds = Regex("\\\"rowId\\\":\\\"(ROW-[^\\\"]+)\\\"").findAll(plan).map { it.groupValues[1] }.toList()
        assertEquals(11, rowIds.size)
        assertEquals(rowIds.distinct(), rowIds)
        assertEquals(4, Regex("\\\"kind\\\":\\\"MILESTONE\\\"").findAll(plan).count())
        assertEquals(0, Regex("\\\"kind\\\":\\\"MUTATION\\\"").findAll(plan).count())
        assertEquals(7, Regex("\\\"kind\\\":\\\"READ_ONLY\\\"").findAll(plan).count())
        listOf("RESULT-D-M1-0001", "RESULT-D-M2-0001", "RESULT-D-M3-0001", "RESULT-D-M4-0001")
            .forEach { assertEquals(1, Regex(it).findAll(plan).count(), it) }
        assertFalse(plan.contains("RESULT-P-"))
        assertFalse(plan.contains("FIELD_PROVEN"))
    }

    @Test
    fun `JSON text and HTML render envelopes bind sources and preserve exact row order`() {
        val output = temp.resolve("parity")
        renderer().writePack(output)
        val planBytes = Files.readAllBytes(output.resolve(ExpansionPackRenderer.PLAN))
        val plan = planBytes.decodeToString()
        val planHash = sha256(planBytes)
        val rowIds = Regex("\\\"rowId\\\":\\\"(ROW-[^\\\"]+)\\\"").findAll(plan).map { it.groupValues[1] }.toList()

        val text = Files.readString(output.resolve(ExpansionPackRenderer.PLAN_TEXT))
        val textEnvelope = text.lineSequence().first().removePrefix("ENVELOPE ")
        assertEquals("clusternav.expansion-render-plan-text/v1", stringField(textEnvelope, "renderSchemaId"))
        assertEquals(planHash, stringField(textEnvelope, "sourcePlanFileSha256"))
        val textRows = text.lineSequence().filter { it.startsWith("ROW ") }.map {
            stringField(it.removePrefix("ROW "), "rowId")
        }.toList()
        assertEquals(rowIds, textRows)

        val checklist = Files.readString(output.resolve(ExpansionPackRenderer.CHECKLIST))
        val checklistEnvelope = script(checklist, "render-envelope")
        assertEquals("clusternav.expansion-render-checklist/v1", stringField(checklistEnvelope, "renderSchemaId"))
        assertEquals(planHash, stringField(checklistEnvelope, "sourcePlanFileSha256"))
        assertEquals(plan, script(checklist, "source-plan"))
        val checklistRows = Regex("data-row-id=\\\"([^\\\"]+)\\\"").findAll(checklist).map { it.groupValues[1] }.toList()
        assertEquals(rowIds, checklistRows)

        val report = Files.readString(output.resolve(ExpansionPackRenderer.REPORT))
        val reportEnvelope = script(report, "render-envelope")
        val expectedComposite = CanonicalJson.digest(CanonicalJson.obj(
            "candidateDiffFileSha256" to JsonText(sha256(Files.readAllBytes(output.resolve(ExpansionPackRenderer.DIFF)))),
            "coverageFileSha256" to JsonText(sha256(Files.readAllBytes(output.resolve(ExpansionPackRenderer.COVERAGE)))),
            "registryFileSha256" to JsonText(sha256(Files.readAllBytes(output.resolve(ExpansionPackRenderer.REGISTRY)))),
        ))
        assertEquals(expectedComposite, stringField(reportEnvelope, "sourceCompositeSha256"))
        assertEquals(
            SourceBackedExpansionCatalog.publishedCandidates.map(CandidateRevision::candidateRevisionId),
            Regex("data-candidate-id=\\\"([^\\\"]+)\\\"").findAll(report).map { it.groupValues[1] }.toList(),
        )
        assertEquals(
            (1..12).map { "C${it.toString().padStart(2, '0')}" },
            Regex("data-corpus-id=\\\"([^\\\"]+)\\\"").findAll(report).map { it.groupValues[1] }.toList(),
        )
    }

    @Test
    fun `coverage hit closes through evidence registry row renders and manifest`() {
        val generated = temp.resolve("boundary")
        renderer().writePack(generated)
        fun document(name: String) = X4Json.asObject(X4Json.parse(Files.readAllBytes(generated.resolve(name))))

        val coverage = document(ExpansionPackRenderer.COVERAGE)
        val hits = X4Json.array(coverage.getValue("entries")).flatMap { entry ->
            X4Json.array(X4Json.asObject(entry).getValue("hits")).map(X4Json::asObject)
        }
        val derivations = hits.mapNotNull { hit ->
            val disposition = X4Json.asObject(hit.getValue("disposition"))
            if (disposition["kind"] == "CANDIDATE_DERIVATION") {
                X4Json.string(disposition.getValue("candidateRevisionId")) to X4Json.string(disposition.getValue("candidateState"))
            } else null
        }.toSet()
        assertEquals(
            setOf(
                "CAND-H-008-PROPERTY-CONFIG-METADATA@3" to "READ_ONLY_READY",
                "CAND-S-011-SOURCE-DOMAIN@1" to "MUTATION_REVIEW",
                "CAND-S-012-REJECTED-SHAPE@1" to "REJECTED",
            ),
            derivations,
        )

        val evidenceFacts = X4Json.array(document(ExpansionPackRenderer.EVIDENCE).getValue("facts"))
            .map(X4Json::asObject).associateBy { X4Json.string(it.getValue("factId")) }
        val registryCandidates = X4Json.array(document(ExpansionPackRenderer.REGISTRY).getValue("candidates"))
            .map(X4Json::asObject).associateBy { X4Json.string(it.getValue("candidateRevisionId")) }
        registryCandidates.values.forEach { candidate ->
            val proof = X4Json.asObject(candidate.getValue("proof"))
            X4Json.strings(proof.getValue("evidenceIds")).forEach { assertTrue(it in evidenceFacts, it) }
        }
        derivations.forEach { (candidateId, state) ->
            assertEquals(state, X4Json.string(registryCandidates.getValue(candidateId).getValue("state")), candidateId)
        }

        val candidateId = "CAND-H-008-PROPERTY-CONFIG-METADATA@3"
        val sourceHit = hits.single { hit ->
            val disposition = X4Json.asObject(hit.getValue("disposition"))
            disposition["kind"] == "CANDIDATE_DERIVATION" && disposition["candidateRevisionId"] == candidateId
        }
        val hitId = X4Json.string(sourceHit.getValue("hitId"))
        val factId = X4Json.strings(sourceHit.getValue("normalizedFactIds")).single()
        assertTrue(hitId in X4Json.strings(evidenceFacts.getValue(factId).getValue("sourceHitIds")))
        val candidate = registryCandidates.getValue(candidateId)
        val proof = X4Json.asObject(candidate.getValue("proof"))
        assertTrue(factId in X4Json.strings(proof.getValue("evidenceIds")))

        val plan = document(ExpansionPackRenderer.PLAN)
        val template = X4Json.asObject(plan.getValue("template"))
        assertEquals(sha256(Files.readAllBytes(generated.resolve(ExpansionPackRenderer.COVERAGE))), template["coverageFileSha256"])
        assertEquals(sha256(Files.readAllBytes(generated.resolve(ExpansionPackRenderer.EVIDENCE))), template["evidenceMapFileSha256"])
        assertEquals(sha256(Files.readAllBytes(generated.resolve(ExpansionPackRenderer.REGISTRY))), template["registryFileSha256"])
        assertEquals(emptyList<String>(), X4Json.strings(template.getValue("allowedMutationCandidateRevisionIds")))
        val row = X4Json.array(template.getValue("rows")).map(X4Json::asObject)
            .single { it["candidateRevisionId"] == candidateId }
        val profile = X4Json.asObject(candidate.getValue("planningProfile"))
        assertEquals(proof["readProbeId"], X4Json.strings(row.getValue("probeIds")).single())
        assertEquals(candidate["requiredSurfaces"], row["requiredSurfaces"])
        assertEquals(emptyList<String>(), X4Json.strings(row.getValue("restoreScope")))
        assertEquals(emptyList<String>(), X4Json.strings(row.getValue("invalidationTriggers")))
        assertEquals(profile["dependencyUncertainty"], row["dependencyUncertainty"])
        assertEquals(profile["estimatedTimeMs"], row["estimatedTimeMs"])
        assertEquals(
            checkedScore(
                (profile.getValue("evidenceStrength") as Long).toInt(),
                (profile.getValue("informationGain") as Long).toInt(),
                (profile.getValue("reversibility") as Long).toInt(),
                (profile.getValue("mutationRisk") as Long).toInt(),
                profile.getValue("estimatedTimeMs") as Long,
                (profile.getValue("dependencyUncertainty") as Long).toInt(),
            ),
            row["score"],
        )
        val rowJson = X4Json.canonical(row).decodeToString()
        assertTrue(Files.readString(generated.resolve(ExpansionPackRenderer.PLAN_TEXT)).contains("ROW $rowJson"))
        assertTrue(Files.readString(generated.resolve(ExpansionPackRenderer.CHECKLIST)).contains("data-row-id=\"${row.getValue("rowId")}\""))
        val manifest = document(ExpansionPackRenderer.MANIFEST)
        val planEntry = X4Json.array(manifest.getValue("entries")).map(X4Json::asObject)
            .single { X4Json.string(it.getValue("path")).endsWith("/${ExpansionPackRenderer.PLAN}") }
        assertEquals(sha256(Files.readAllBytes(generated.resolve(ExpansionPackRenderer.PLAN))), planEntry["fullSha256"])
    }

    @Test
    fun `output DAG trace hashes ledger schema and metadata privacy fence are exact`() {
        val output = temp.resolve("dag")
        renderer().writePack(output)
        val trace = Files.readString(output.resolve(ExpansionPackRenderer.TRACE))
        val traceFields = mapOf(
            "candidateDiffFileSha256" to ExpansionPackRenderer.DIFF,
            "checklistFileSha256" to ExpansionPackRenderer.CHECKLIST,
            "coverageFileSha256" to ExpansionPackRenderer.COVERAGE,
            "evidenceMapFileSha256" to ExpansionPackRenderer.EVIDENCE,
            "ledgerSchemaFileSha256" to ExpansionPackRenderer.LEDGER_SCHEMA,
            "legacyBaselineFileSha256" to ExpansionPackRenderer.BASELINE,
            "planFileSha256" to ExpansionPackRenderer.PLAN,
            "planTextFileSha256" to ExpansionPackRenderer.PLAN_TEXT,
            "registryFileSha256" to ExpansionPackRenderer.REGISTRY,
            "reportFileSha256" to ExpansionPackRenderer.REPORT,
        )
        traceFields.forEach { (field, name) ->
            assertEquals(sha256(Files.readAllBytes(output.resolve(name))), stringField(trace, field), field)
        }
        assertEquals(18, Regex("\\\"requirementId\\\":\\\"REQ-X(?:[1-9]|1[0-8])\\\"").findAll(trace).count())
        val ledger = Files.readString(output.resolve(ExpansionPackRenderer.LEDGER_SCHEMA))
        assertTrue(ledger.contains("\"${'$'}ref\":\"#/${'$'}defs/ResultLedgerSchemaRoot\""))
        assertTrue(ledger.contains("\"SessionPackFreeze\""))
        assertTrue(ledger.contains("\"LedgerEvent\""))

        val publication = ExpansionPackRenderer.OUTPUT_NAMES.joinToString("\n") { Files.readString(output.resolve(it)) }
        assertFalse(Regex("/(?:Users|home)/[^/\\s]+|[A-Za-z]:\\\\Users\\\\").containsMatchIn(publication))
        assertFalse(Regex("\\b(?:10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2})\\b").containsMatchIn(publication))
        listOf("rawDump", "sourceLine", "decompiledBody", "sessionNonceSha256")
            .forEach { assertFalse(publication.contains("\"$it\""), it) }
    }

    /**
     * E6b — đích ghi của test không bao giờ được là project-root THẬT.
     *
     * **Cơ chế (đo 2026-08-23)** — `ExpansionMain.main` không nhận đích, nó tự suy ra bằng
     * `findProjectRoot(Path.of("").toAbsolutePath())`, tức LEO NGƯỢC từ thư mục làm việc của test worker.
     * Thư mục đó là `<repo>/offcar-planner` (mặc định `Test.workingDir` = `project.projectDir`;
     * `offcar-planner/build.gradle.kts` KHÔNG đặt `workingDir` cho `Test` — dòng
     * `workingDir = rootProject.projectDir` chỉ thuộc task JavaExec `renderExpansionPack`), và tổ tiên đầu
     * tiên có `settings.gradle.kts` chính là repo root. ⇒ mọi lời gọi `ExpansionMain.main(emptyArray())`
     * trong test đều ghi đè 11/12 file niêm phong TRACKED (`corpus-coverage.json` là đầu vào, không tái
     * xuất bản, nên giữ nguyên). Hôm phát hiện, bytes trùng nên `git status` im lặng — nhưng chỉ cần một
     * byte nguồn lệch là repo bẩn mà test vẫn XANH, và tệ hơn: bộ niêm phong đang lệch sẽ bị ghi đè cho
     * khớp lại, che mất chính test canh lệch (`LegacyBaselineIdentityTest`). Lời gọi đó đã bị gỡ.
     *
     * Đính chính: bản ghi đầu của E6b quy cho "thư mục làm việc = `rootProject.projectDir`
     * (`build.gradle.kts:33`)" — SAI. Hai cơ chế cho ra CÙNG một đích ghi nên nhầm lẫn không lộ ra; sự
     * thật đo bằng init-script in `workingDir` của task `Test`, và nay được khoá bằng assert (0) bên dưới.
     *
     * **Bất biến giữ nguyên**: entrypoint từ chối MỌI tham số và chỉ có MỘT đích canonical. Nay chứng minh
     * theo thứ tự FAIL-FAST, để không vế nào có thể ghi vào cây repo:
     *  0. đo hiểm hoạ tại chỗ — `findProjectRoot(<thư mục làm việc>)` ra đúng repo root;
     *  1. khoá trên NGUỒN `ExpansionMain.kt`: thân `main` khớp chuỗi cố định (trong đó
     *     `require(args.isEmpty())` là câu lệnh ĐẦU TIÊN), trong `object ExpansionMain` có đúng MỘT
     *     `writePack(`, `declaredMethods` = {`main`, `findProjectRoot`}. Vế này PHẢI chạy trước vế 2: ai gỡ
     *     `require` thì test đỏ ngay ở đây và dừng **trước khi** lời gọi ở vế 2 kịp ghi một byte nào;
     *  2. hành vi: `main(arrayOf("--output", …))` ném `IllegalArgumentException`. Assert `temp` rỗng đi kèm
     *     chỉ là GUARD cho một `main` TƯƠNG LAI kiểu ghi-rồi-mới-ném — với `main` hiện tại (bỏ qua hoàn
     *     toàn `args`) nó không thể đỏ, nên không tính là bằng chứng;
     *  3. hành vi đầy đủ: chính biểu thức của `main` chạy trên project-root GIẢ trong `@TempDir` — đúng 12
     *     file, chạy lần 2 byte-identical, `parentCombinedSha256` không đổi, đích khác bị từ chối.
     *
     * "Pack đã commit == pack dựng lại" vẫn được khoá read-only ở `ExpansionTransportFenceTest`
     * (`generation has no side effects outside the 12 expansion outputs`) và `ExpansionTraceabilityTest`
     * (`two fresh generations and checked pack are byte identical`) ⇒ không mất bảo đảm nào.
     */
    @Test
    fun `entrypoint rejects options and has only the fixed expansion destination`() {
        // (0) Hiểm hoạ, đo tại chỗ: thư mục làm việc của test worker leo ngược ra ĐÚNG repo root ⇒ bất kỳ
        //     lời gọi `ExpansionMain.main(emptyArray())` nào trong test cũng ghi vào cây tracked.
        assertEquals(
            root,
            ExpansionMain.findProjectRoot(Path.of("").toAbsolutePath().normalize()),
            "HIỂM HOẠ vẫn còn: thư mục làm việc của test worker leo ngược ra ĐÚNG repo root, nên mọi lời gọi " +
                "`ExpansionMain.main(emptyArray())` in-process sẽ ghi vào cây tracked — thứ tự FAIL-FAST bên dưới " +
                "(khoá NGUỒN trước, gọi `main` sau) là bắt buộc. Nếu assert này đỏ vì bạn vừa trỏ `Test.workingDir` ra " +
                "NGOÀI repo thì hiểm hoạ đã hết — cập nhật assert + KDoc E6b, đừng chỉ xoá.",
        )
        assertEquals(root, ExpansionMain.findProjectRoot(root.resolve("offcar-planner/src/main")))
        assertEquals(
            "docs/diagnostics/hud-sign-re/expansion",
            ExpansionPackRenderer.OUTPUT_DIRECTORY,
        )
        assertEquals(ExpansionPackRenderer.OUTPUT_NAMES, directoryNames(root.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY)))

        // (1) Khoá NGUỒN — bắt buộc đứng TRƯỚC mọi lời gọi `main`, xem KDoc.
        val entrypointSource = root.resolve("offcar-planner/src/main/kotlin")
            .resolve(ExpansionMain::class.java.name.replace('.', '/') + ".kt")
        assertTrue(Files.isRegularFile(entrypointSource), entrypointSource.toString())
        val entrypointObject = Files.readString(entrypointSource).replace(Regex("\\s+"), " ")
            .substringAfter("object ExpansionMain {", "")
        assertTrue(entrypointObject.isNotEmpty(), "không tìm thấy `object ExpansionMain` trong $entrypointSource")
        val fixedBody = "fun main(args: Array<String>) { require(args.isEmpty()); " +
            "val root = findProjectRoot(Path.of(\"\").toAbsolutePath().normalize()); " +
            "ExpansionPackRenderer(root).writePack(root.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY)) }"
        assertTrue(
            entrypointObject.contains(fixedBody),
            "Thân `ExpansionMain.main` phải giữ NGUYÊN chuỗi cố định (so sánh sau khi gộp khoảng trắng). " +
                "Đây là khoá DUY NHẤT chứng minh `require(args.isEmpty())` là câu lệnh đầu tiên, và lời gọi " +
                "`main` ngay bên dưới dựa vào nó để không ghi vào repo — xem KDoc E6b. Reformat lại `main` " +
                "cũng làm đỏ assert này: sửa `fixedBody` cho khớp, ĐỪNG gỡ assert.\nMong đợi: $fixedBody\n" +
                "Thực tế: $entrypointObject",
        )
        assertEquals(
            1,
            Regex("writePack\\(").findAll(entrypointObject).count(),
            "`object ExpansionMain` chỉ được có ĐÚNG MỘT lời gọi writePack: $entrypointObject",
        )
        assertEquals(
            setOf("main", "findProjectRoot"),
            ExpansionMain::class.java.declaredMethods.map { it.name.substringBefore('$') }.toSet(),
        )
        assertTrue(ExpansionMain::class.java.declaredMethods.none { it.name.contains("execute", ignoreCase = true) })

        // (2) Hành vi — an toàn vì vế (1) vừa chứng minh `require(args.isEmpty())` chặn trước mọi lệnh ghi.
        assertThrows(IllegalArgumentException::class.java) { ExpansionMain.main(arrayOf("--output", temp.toString())) }
        assertEquals(emptyList<String>(), Files.list(temp).use { stream -> stream.map { it.fileName.toString() }.toList() })

        // (3) Hành vi đầy đủ trên project-root GIẢ (`strictProjectRoot` tạo thư mục con trong `temp`,
        //     nên phải chạy SAU assert `temp` rỗng ở trên).
        val fakeRoot = strictProjectRoot()
        assertEquals(fakeRoot, ExpansionMain.findProjectRoot(fakeRoot.resolve("offcar-planner/src/main")))
        val output = fakeRoot.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY)
        ExpansionPackRenderer(fakeRoot).writePack(output)
        val checked = ExpansionPackRenderer.OUTPUT_NAMES.associateWith { Files.readAllBytes(output.resolve(it)) }
        val parentBefore = LegacyBaselineIdentity.parentCombinedSha256(fakeRoot)
        ExpansionPackRenderer(fakeRoot).writePack(output)
        checked.forEach { (name, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(output.resolve(name)), name) }
        assertEquals(parentBefore, LegacyBaselineIdentity.parentCombinedSha256(fakeRoot))
        assertEquals(ExpansionPackRenderer.OUTPUT_NAMES, directoryNames(output))
        assertThrows(IllegalArgumentException::class.java) {
            ExpansionPackRenderer(fakeRoot).writePack(fakeRoot.resolve("other-output"))
        }
    }

    private fun directoryNames(directory: Path): Set<String> = Files.list(directory).use { stream ->
        stream.filter(Files::isRegularFile).map { it.fileName.toString() }.toList().toSet()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun stringField(text: String, name: String): String {
        val match = Regex("\\\"${Regex.escape(name)}\\\":\\\"([^\\\"]+)\\\"").find(text)
        assertNotNull(match, name)
        return match!!.groupValues[1]
    }

    @Test
    fun `coverage closed objects reject extra and missing fields at every contract level`() {
        repeat(7) { index ->
            assertThrows(IllegalArgumentException::class.java) {
                CoverageMetadata.parse(resealedCoverage { coverageNodes(it)[index]["unexpected"] = null })
            }
        }
        val required = listOf("schemaId", "corpusId", "toolId", "queryId", "hitId", "kind", "risk")
        required.indices.forEach { index ->
            assertThrows(IllegalArgumentException::class.java) {
                CoverageMetadata.parse(resealedCoverage { coverageNodes(it)[index].remove(required[index]) })
            }
        }
    }

    @Test
    fun `coverage parser rejects duplicate float malformed unicode types self hash and noncanonical bytes`() {
        val text = sourceCoverage().decodeToString()
        val attacks = listOf(
            text.replaceFirst("{", "{\"schemaId\":\"clusternav.expansion-corpus-coverage/v1\",").encodeToByteArray(),
            text.replaceFirst("\"risk\":0", "\"risk\":0.0").encodeToByteArray(),
            text.replaceFirst("\"clusternav.expansion-corpus-coverage/v1\"", "\"bad\\q\"").encodeToByteArray(),
            text.replaceFirst("\"clusternav.expansion-corpus-coverage/v1\"", "\"\\uD800\"").encodeToByteArray(),
            text.replace(Regex("\"selfSha256\":\"[0-9a-f]{64}\""), "\"selfSha256\":\"${"0".repeat(64)}\"").encodeToByteArray(),
            (text + "\n").encodeToByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
            resealedCoverage { mutableObject(mutableArray(it.getValue("entries"))[0])["status"] = 1L },
        )
        attacks.forEach { bytes -> assertThrows(IllegalArgumentException::class.java) { CoverageMetadata.parse(bytes) } }
    }

    @Test
    fun `candidate claims bind registry proofs reject conflicts and enforce selector coherence`() {
        val mismatch = resealedCoverage { document ->
            mutableObject(candidateHit(document, "CAND-H-008-PROPERTY-CONFIG-METADATA@3").getValue("promotionProofClaim"))["risk"] = 1L
        }
        val metadata = CoverageMetadata.parse(mismatch)
        assertThrows(IllegalArgumentException::class.java) {
            metadata.validateRegistry(SourceBackedExpansionCatalog.publishedCandidates)
        }
        val valid = CoverageMetadata.parse(sourceCoverage())
        val unrelated = valid.copy(candidateDerivations = valid.candidateDerivations.map {
            if (it.candidateRevisionId == "CAND-H-008-PROPERTY-CONFIG-METADATA@3") it.copy(normalizedFactIds = listOf("FACT-PARENT-S5")) else it
        })
        assertThrows(IllegalArgumentException::class.java) { unrelated.validateRegistry(SourceBackedExpansionCatalog.publishedCandidates) }
        val conflict = resealedCoverage { document ->
            val disposition = mutableObject(candidateHit(document, "CAND-S-012-REJECTED-SHAPE@1").getValue("disposition"))
            disposition["candidateRevisionId"] = "CAND-H-008-PROPERTY-CONFIG-METADATA@3"
            disposition["candidateState"] = "READ_ONLY_READY"
        }
        assertThrows(IllegalArgumentException::class.java) { CoverageMetadata.parse(conflict) }
        val incoherent = resealedCoverage { document ->
            mutableObject(candidateHit(document, "CAND-H-008-PROPERTY-CONFIG-METADATA@3").getValue("promotionProofClaim"))["selectorId"] = "SEL-DIFFERENT"
        }
        assertThrows(IllegalArgumentException::class.java) { CoverageMetadata.parse(incoherent) }
    }

    @Test
    fun `all available coverage remains not exhaustive when an actual derivation is open`() {
        val dishonest = resealedCoverage { document ->
            mutableArray(document.getValue("entries")).map(::mutableObject).forEach { entry ->
                val empty = mutableArray(entry.getValue("hits")).isEmpty()
                entry["artifactSha256"] = entry["artifactSha256"] ?: "0".repeat(64)
                entry["status"] = "AVAILABLE"; entry["zeroHit"] = empty
                entry["zeroHitOutcome"] = if (empty) "NO_MATCH" else null
            }
            document["expansionVerdict"] = "READY_DATA"
        }
        assertThrows(IllegalArgumentException::class.java) { CoverageMetadata.parse(dishonest) }
        val metadata = CoverageMetadata.parse(sourceCoverage())
        val omittedDiscovery = metadata.copy(
            entries = metadata.entries.map { it.first to "AVAILABLE" }, verdict = "READY_DATA", candidateDerivations = emptyList(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            omittedDiscovery.validateRegistry(listOf(ExpansionTestFixtures.h8Discovered))
        }
    }

    @Test
    fun `output and coverage fences reject ancestor and final symlinks plus non-descendant roots`() {
        val renderer = renderer()
        val real = temp.resolve("real-output"); Files.createDirectories(real)
        val ancestor = temp.resolve("output-link"); Files.createSymbolicLink(ancestor, real)
        assertThrows(IllegalArgumentException::class.java) { renderer.writePack(ancestor.resolve("nested")) }
        val output = temp.resolve("final-link-output"); Files.createDirectories(output)
        val victim = temp.resolve("victim.txt"); Files.writeString(victim, "unchanged")
        Files.createSymbolicLink(output.resolve(ExpansionPackRenderer.BASELINE), victim)
        assertThrows(IllegalArgumentException::class.java) { renderer.writePack(output) }
        assertEquals("unchanged", Files.readString(victim))
        assertThrows(IllegalArgumentException::class.java) { renderer.writePack(root) }
        assertThrows(IllegalArgumentException::class.java) { renderer.writePack(strictProjectRoot().resolve("arbitrary-output")) }

        val fakeRoot = temp.toRealPath().resolve("project"); Files.createDirectories(fakeRoot)
        LegacyBaselineIdentity.PARENT_PATHS.forEach { relative ->
            val target = fakeRoot.resolve(relative); Files.createDirectories(target.parent)
            Files.copy(root.resolve(relative), target)
        }
        val expansion = fakeRoot.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY); Files.createDirectories(expansion)
        Files.createSymbolicLink(expansion.resolve(ExpansionPackRenderer.COVERAGE),
            root.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY).resolve(ExpansionPackRenderer.COVERAGE))
        assertThrows(IllegalArgumentException::class.java) {
            ExpansionPackRenderer(fakeRoot).writePack(temp.resolve("symlink-coverage-output"))
        }

        val schemaRoot = strictProjectRoot()
        val schema = schemaRoot.resolve("offcar-planner/src/main/resources/expansion-contracts.schema.json")
        val schemaTarget = temp.resolve("schema-target.json"); Files.copy(schema, schemaTarget); Files.delete(schema)
        Files.createSymbolicLink(schema, schemaTarget)
        assertThrows(IllegalArgumentException::class.java) {
            ExpansionPackRenderer(schemaRoot).writePack(temp.resolve("symlink-schema-output"))
        }
        assertThrows(java.io.IOException::class.java) { ExpansionHashing.sha256File(schema) }
    }

    private fun renderer() = ExpansionPackRenderer(strictProjectRoot())

    private fun strictProjectRoot(): Path {
        val fixture = temp.resolve("strict-project")
        val coverage = fixture.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY).resolve(ExpansionPackRenderer.COVERAGE)
        if (!Files.exists(coverage)) {
            LegacyBaselineIdentity.PARENT_PATHS.forEach { relative ->
                val target = fixture.resolve(relative); Files.createDirectories(target.parent); Files.copy(root.resolve(relative), target)
            }
            val schema = "offcar-planner/src/main/resources/expansion-contracts.schema.json"
            val schemaTarget = fixture.resolve(schema); Files.createDirectories(schemaTarget.parent); Files.copy(root.resolve(schema), schemaTarget)
            Files.createDirectories(coverage.parent); Files.write(coverage, sourceCoverage())
            // E6b: fixture phải là project-root THẬT SỰ để `ExpansionMain.findProjectRoot` neo vào đây,
            // không leo ngược lên repo thật.
            Files.writeString(fixture.resolve("settings.gradle.kts"), "")
        }
        return fixture.toRealPath()
    }

    private fun sourceCoverage(): ByteArray {
        val path = root.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY).resolve(ExpansionPackRenderer.COVERAGE)
        val canonical = Files.readAllBytes(path)
        if (runCatching { CoverageMetadata.parse(canonical) }.isSuccess) return canonical
        val document = mutableObject(X4Json.parse(canonical)); document.remove("selfSha256")
        val candidates = SourceBackedExpansionCatalog.publishedCandidates.associateBy(CandidateRevision::candidateRevisionId)
        mutableArray(document.getValue("entries")).map(::mutableObject).forEach { entry ->
            val corpus = entry.getValue("corpusId"); val queryHash = mutableObject(entry.getValue("query")).getValue("queryDefinitionSha256")
            mutableArray(entry.getValue("hits")).map(::mutableObject).forEach { hit ->
                val disposition = mutableObject(hit.getValue("disposition")); val candidate = (disposition["candidateRevisionId"] as? String)?.let(candidates::get)
                val proof = candidate?.input?.proof; hit["selectorIds"] = listOfNotNull(proof?.selectorId); hit["consumerIds"] = listOfNotNull(proof?.consumerId)
                hit["duplicateEquivalenceSha256"] = sha256(X4Json.canonical(mapOf("consumerIds" to hit["consumerIds"], "corpusId" to corpus,
                    "normalizedFactIds" to hit["normalizedFactIds"], "queryDefinitionSha256" to queryHash, "selectorIds" to hit["selectorIds"])))
                hit["promotionProofClaim"] = proof?.let {
                    X4Json.parse(CanonicalJson.bytes(JsonObject(it.json().fields.filterNot { field -> field.first == "evidenceIds" })))
                }
            }
        }
        document["selfSha256"] = sha256(X4Json.canonical(document)); return X4Json.canonical(document)
    }

    private fun resealedCoverage(mutate: (MutableMap<String, Any?>) -> Unit): ByteArray {
        val document = mutableObject(X4Json.parse(sourceCoverage())); document.remove("selfSha256"); mutate(document)
        document["selfSha256"] = sha256(X4Json.canonical(document)); return X4Json.canonical(document)
    }

    private fun coverageNodes(document: MutableMap<String, Any?>): List<MutableMap<String, Any?>> {
        val entry = mutableObject(mutableArray(document.getValue("entries"))[1])
        val hit = candidateHit(document, "CAND-H-008-PROPERTY-CONFIG-METADATA@3")
        return listOf(document, entry, mutableObject(entry.getValue("scanner")), mutableObject(entry.getValue("query")),
            hit, mutableObject(hit.getValue("disposition")), mutableObject(hit.getValue("promotionProofClaim")))
    }

    private fun candidateHit(document: MutableMap<String, Any?>, candidateId: String): MutableMap<String, Any?> =
        mutableArray(document.getValue("entries")).map(::mutableObject).flatMap { mutableArray(it.getValue("hits")).map(::mutableObject) }
            .single { mutableObject(it.getValue("disposition"))["candidateRevisionId"] == candidateId }

    @Suppress("UNCHECKED_CAST")
    private fun mutableObject(value: Any?): MutableMap<String, Any?> =
        value as? MutableMap<String, Any?> ?: error("expected mutable JSON object")
    @Suppress("UNCHECKED_CAST")
    private fun mutableArray(value: Any?): MutableList<Any?> = value as? MutableList<Any?> ?: error("expected mutable JSON array")

    private fun removeRootStringField(text: String, name: String): String {
        val token = "\"$name\":\""
        val tokenIndex = text.indexOf(token)
        require(tokenIndex >= 0 && text.indexOf(token, tokenIndex + token.length) < 0)
        val valueStart = tokenIndex + token.length
        val valueEnd = text.indexOf('"', valueStart) + 1
        var start = tokenIndex
        var end = valueEnd
        if (start > 0 && text[start - 1] == ',') start-- else if (end < text.length && text[end] == ',') end++
        return text.removeRange(start, end)
    }

    private data class ManifestEntry(val fullSha256: String, val path: String, val schemaId: String)
    private fun manifestEntries(text: String): List<ManifestEntry> = Regex(
        "\\{\\\"fullSha256\\\":\\\"([0-9a-f]{64})\\\",\\\"path\\\":\\\"([^\\\"]+)\\\",\\\"schemaId\\\":\\\"([^\\\"]+)\\\"}",
    ).findAll(text).map { ManifestEntry(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()

    private fun script(html: String, id: String): String {
        val match = Regex("<script id=\\\"${Regex.escape(id)}\\\" type=\\\"application/json\\\">(.*?)</script>").find(html)
        assertNotNull(match, id)
        return match!!.groupValues[1]
    }
}
