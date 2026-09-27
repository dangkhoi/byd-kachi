package com.byd.clusternav.offcar

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertNotNull

/**
 * ═══ Bộ trợ giúp của [ExpansionDeterminismTest] — tách THUẦN (581 dòng → trần 500) ═══
 *
 * L6-debt 2026-09-27: thân hàm giữ nguyên byte, `private fun` → hàm mở rộng `internal` của chính lớp test (cùng package;
 * `temp`/`root` là thuộc tính của lớp). Mọi `@Test` vẫn nằm trong `ExpansionDeterminismTest` để cổng `GATE-X-O2`/`O9` chạy
 * đúng lớp. Tệp KHÔNG trong `SOURCE_SEAL_INPUT` (xem ghi chú ở `ExpansionTransportFenceFixtures`).
 */
internal fun ExpansionDeterminismTest.directoryNames(directory: Path): Set<String> = Files.list(directory).use { stream ->
    stream.filter(Files::isRegularFile).map { it.fileName.toString() }.toList().toSet()
}

internal fun ExpansionDeterminismTest.sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }

internal fun ExpansionDeterminismTest.stringField(text: String, name: String): String {
    val match = Regex("\\\"${Regex.escape(name)}\\\":\\\"([^\\\"]+)\\\"").find(text)
    assertNotNull(match, name)
    return match!!.groupValues[1]
}


internal fun ExpansionDeterminismTest.renderer() = ExpansionPackRenderer(strictProjectRoot())

internal fun ExpansionDeterminismTest.strictProjectRoot(): Path {
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

internal fun ExpansionDeterminismTest.sourceCoverage(): ByteArray {
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

internal fun ExpansionDeterminismTest.resealedCoverage(mutate: (MutableMap<String, Any?>) -> Unit): ByteArray {
    val document = mutableObject(X4Json.parse(sourceCoverage())); document.remove("selfSha256"); mutate(document)
    document["selfSha256"] = sha256(X4Json.canonical(document)); return X4Json.canonical(document)
}

internal fun ExpansionDeterminismTest.coverageNodes(document: MutableMap<String, Any?>): List<MutableMap<String, Any?>> {
    val entry = mutableObject(mutableArray(document.getValue("entries"))[1])
    val hit = candidateHit(document, "CAND-H-008-PROPERTY-CONFIG-METADATA@3")
    return listOf(document, entry, mutableObject(entry.getValue("scanner")), mutableObject(entry.getValue("query")),
        hit, mutableObject(hit.getValue("disposition")), mutableObject(hit.getValue("promotionProofClaim")))
}

internal fun ExpansionDeterminismTest.candidateHit(document: MutableMap<String, Any?>, candidateId: String): MutableMap<String, Any?> =
    mutableArray(document.getValue("entries")).map(::mutableObject).flatMap { mutableArray(it.getValue("hits")).map(::mutableObject) }
        .single { mutableObject(it.getValue("disposition"))["candidateRevisionId"] == candidateId }

@Suppress("UNCHECKED_CAST")
internal fun ExpansionDeterminismTest.mutableObject(value: Any?): MutableMap<String, Any?> =
    value as? MutableMap<String, Any?> ?: error("expected mutable JSON object")
@Suppress("UNCHECKED_CAST")
internal fun ExpansionDeterminismTest.mutableArray(value: Any?): MutableList<Any?> = value as? MutableList<Any?> ?: error("expected mutable JSON array")

internal fun ExpansionDeterminismTest.removeRootStringField(text: String, name: String): String {
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

internal data class ManifestEntry(val fullSha256: String, val path: String, val schemaId: String)
internal fun ExpansionDeterminismTest.manifestEntries(text: String): List<ManifestEntry> = Regex(
    "\\{\\\"fullSha256\\\":\\\"([0-9a-f]{64})\\\",\\\"path\\\":\\\"([^\\\"]+)\\\",\\\"schemaId\\\":\\\"([^\\\"]+)\\\"}",
).findAll(text).map { ManifestEntry(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()

internal fun ExpansionDeterminismTest.script(html: String, id: String): String {
    val match = Regex("<script id=\\\"${Regex.escape(id)}\\\" type=\\\"application/json\\\">(.*?)</script>").find(html)
    assertNotNull(match, id)
    return match!!.groupValues[1]
}
