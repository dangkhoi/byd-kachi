package com.byd.clusternav.offcar
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.text.Normalizer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
class ExpansionTraceabilityTest {
    @TempDir
    lateinit var temp: Path
    private val root: Path get() = Path.of(System.getProperty("clusternav.root")).toAbsolutePath().normalize()
    private val output: Path get() = root.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY)
    @Test
    fun `18 requirements 6 tasks and 12 gates have exact bidirectional closure and commands`() {
        val trace = json("traceability.json")
        val links = X4Json.array(trace.getValue("links")).map(X4Json::asObject)
        assertEquals(18, links.size)
        assertEquals((1..18).map { "REQ-X$it" }, links.map { X4Json.string(it.getValue("requirementId")) })
        links.zip(EXPECTED_LINKS).forEachIndexed { index, (actual, expected) ->
            val requirement = "REQ-X${index + 1}"
            val tasks = X4Json.strings(actual.getValue("taskIds"))
            val gates = X4Json.strings(actual.getValue("gateIds"))
            val artifacts = X4Json.strings(actual.getValue("artifactPaths"))
            val commands = X4Json.strings(actual.getValue("commands"))
            assertEquals(expected.tasks, tasks, "$requirement tasks")
            assertEquals(expected.gates, gates, "$requirement gates")
            assertEquals(expected.artifacts.sorted(), artifacts, "$requirement artifacts")
            assertEquals(gates.map(GATE_COMMANDS::getValue).distinct().sorted(), commands, "$requirement commands")
            listOf(tasks, gates, artifacts, commands).forEach { assertEquals(it.distinct(), it, requirement) }
            artifacts.forEach { relative ->
                assertTrue(relative.contains('/') && !relative.contains("..") && !relative.contains('*'), relative)
                val path = root.resolve(relative).normalize()
                assertTrue(path.startsWith(root) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), relative)
            }
        }
        val requirements = (1..18).map { "REQ-X$it" }.toSet()
        val taskReverse = reverse(links, "taskIds")
        val gateReverse = reverse(links, "gateIds")
        assertEquals((0..5).map { "TASK-X$it" }.toSet(), taskReverse.keys)
        assertEquals((1..12).map { "GATE-X-O$it" }.toSet(), gateReverse.keys)
        assertTrue(taskReverse.values.all { it.isNotEmpty() && requirements.containsAll(it) })
        assertTrue(gateReverse.values.all { it.isNotEmpty() && requirements.containsAll(it) })
        assertEquals(expectedReverse { it.tasks }, taskReverse)
        assertEquals(expectedReverse { it.gates }, gateReverse)
        val spec = Files.readString(root.resolve(SPEC_PATH))
        val specLinks = Regex(
            "<tr><td>(REQ-X(?:[1-9]|1[0-8]))</td><td>((?:TASK-X[0-5],?)+)</td><td>((?:GATE-X-O(?:[1-9]|1[0-2]),?)+)</td><td>",
        ).findAll(spec).associate { match ->
            match.groupValues[1] to (match.groupValues[2].split(',') to match.groupValues[3].split(','))
        }
        assertEquals(18, specLinks.size)
        EXPECTED_LINKS.forEachIndexed { index, expected ->
            assertEquals(expected.tasks to expected.gates, specLinks.getValue("REQ-X${index + 1}"))
        }
        val specCommands = Regex(
            "<tr><td>(GATE-X-O(?:[1-9]|1[0-2]))</td><td>.*?</td><td><code>(.*?)</code></td></tr>",
        ).findAll(spec).associate { it.groupValues[1] to it.groupValues[2].replace("&amp;", "&") }
        assertEquals(GATE_COMMANDS, specCommands)
        links.flatMap { X4Json.strings(it.getValue("commands")) }.forEach { command ->
            assertTrue(command.all { it.code in 0x20..0x7e }, command)
            assertFalse(Regex("""(?i)(?:^|[ ,])(?:all|n/a)(?:$|[ ,])|\.\.|\*|GATE-X-O[0-9]+-[0-9]+""").containsMatchIn(command), command)
        }
    }
    @Test
    fun `renderer gate commands use canonical fixed selectors without O12 recursion`() {
        val method = ExpansionPackRenderer::class.java.getDeclaredMethod("gateCommand", String::class.java)
            .also { it.isAccessible = true }
        val commands = (1..12).associate { number ->
            val gate = "GATE-X-O$number"
            gate to method.invoke(ExpansionPackRenderer(root), gate) as String
        }
        assertEquals(GATE_COMMANDS, commands)
        val specCommands = Regex(
            "<tr><td>(GATE-X-O(?:[1-9]|1[0-2]))</td><td>.*?</td><td><code>(.*?)</code></td></tr>",
        ).findAll(Files.readString(root.resolve(SPEC_PATH))).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(GATE_COMMANDS, specCommands)
        assertEquals("scripts/verify-hud-sign-candidate-expansion.sh", commands.getValue("GATE-X-O11"))
        assertTrue(commands.values.none { it.contains("&&") || it.contains("./gradlew") || it.contains("python3") })
    }
    @Test
    fun `recursive semantic privacy fixtures reject non-hash leaks and permit fixed metadata`() {
        val negative = listOf(
            "password=CorrectHorseBatteryStaple", "VIN 1HGCM82633A004352", "serialNumber=42",
            "rawDump payload", "sourceLine 42", "decompiledBody text", "GPS 21.0285,105.8542",
            "8.8.8.8", "2001:db8::1", "/Users/example/private.txt", "https://example.com/private",
            "person@example.com", "+84912345678", "build.internal.example",
            "PASSWORD-CORRECTHORSEBATTERYSTAPLE", "FACT-PASSWORD-CORRECTHORSEBATTERYSTAPLE",
            "FACT-8.8.8.8", "VERSION-8.8.8.8", "FACT-1HGCM82633A004352",
            "FACT-RAW-DUMP-PAYLOAD", "FACT-SOURCE-LINE-42", "FACT-SERIAL-NUMBER-42", "FACT-GPS-21.0285",
        )
        negative.forEach { fixture -> assertTrue(semanticPrivacyViolation(mapOf("outer" to listOf(mapOf("value" to fixture)))), fixture) }
        assertTrue(semanticPrivacyViolation(mapOf("outer" to mapOf("password" to "PASSWORD-CORRECTHORSEBATTERYSTAPLE"))))
        assertTrue(semanticPrivacyViolation(mapOf("outer" to mapOf("passwordSha256" to "password=CorrectHorseBatteryStaple"))))
        assertTrue(semanticPrivacyViolation(mapOf("passwordSha256" to null)))
        assertFalse(semanticPrivacyViolation(mapOf("candidates" to listOf(mapOf("predecessorCandidateSha256" to null))), "candidate-registry.json"))
        assertFalse(semanticPrivacyViolation(mapOf("entries" to listOf(mapOf("artifactSha256" to null))), "corpus-coverage.json"))
        assertFalse(semanticPrivacyViolation(mapOf(
            "selfSha256" to "a".repeat(64), "factId" to "FACT-SAFE-EVIDENCE", "tokenId" to "TOKEN-C01-QUERY",
            "schema" to "https://json-schema.org/draft/2020-12/schema", "owner" to "Đăng Khôi · dangkhoi",
        )))
    }
    @Test
    fun `normative and ledger schemas are recursively closed local and privacy safe`() {
        val schemaPath = root.resolve("offcar-planner/src/main/resources/expansion-contracts.schema.json")
        val schemaBytes = Files.readAllBytes(schemaPath)
        val schema = X4Json.asObject(X4Json.parse(schemaBytes))
        val defs = X4Json.asObject(schema.getValue("\$defs"))
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema["\$schema"])
        assertArrayEquals(X4Json.canonical(schema), schemaBytes)
        assertEquals(ROOT_DEFINITIONS, X4Json.array(schema.getValue("oneOf")).map {
            X4Json.string(X4Json.asObject(it).getValue("\$ref"))
        })
        var closedObjects = 0
        var localRefs = 0
        X4Json.walk(schema) { node ->
            assertFalse(node["additionalProperties"] == true, "open schema node")
            assertFalse(node.containsKey("anyOf"), "anyOf is forbidden")
            if (node["type"] == "object") {
                closedObjects++
                assertEquals(false, node["additionalProperties"], "object is not closed: ${node.keys}")
                val properties = X4Json.asObject(node.getValue("properties")).keys
                val required = X4Json.strings(node.getValue("required"))
                assertEquals(properties, required.toSet(), "required/property mismatch")
                assertEquals(required.distinct(), required, "duplicate required property")
            }
            node["\$ref"]?.let { raw ->
                localRefs++
                val reference = X4Json.string(raw)
                assertTrue(reference.startsWith("#/\$defs/") && reference.count { it == '/' } == 2, reference)
                assertTrue(defs.containsKey(reference.substringAfterLast('/')), reference)
            }
            node.keys.forEach { key ->
                val normalized = key.lowercase().filter(Char::isLetterOrDigit)
                assertFalse(normalized in PRIVATE_KEYS, "privacy-forbidden schema key: $key")
            }
        }
        assertTrue(closedObjects >= 35, "recursive object closure was incomplete: $closedObjects")
        assertTrue(localRefs >= 100, "recursive local-reference closure was incomplete: $localRefs")
        val ledger = json("result-ledger.schema.json")
        assertEquals(defs, X4Json.asObject(ledger.getValue("\$defs")))
        assertEquals("#/\$defs/ResultLedgerSchemaRoot", ledger["\$ref"])
        assertEquals("https://json-schema.org/draft/2020-12/schema", ledger["\$schema"])
        val publication = ExpansionPackRenderer.OUTPUT_NAMES.associateWith { Files.readAllBytes(output.resolve(it)) }
        publication.forEach { (name, bytes) ->
            assertFalse(bytes.isEmpty(), name)
            assertFalse(bytes.last() == '\n'.code.toByte(), name)
            val text = bytes.toString(StandardCharsets.UTF_8)
            assertFalse(PRIVATE_PATH.containsMatchIn(text), name)
            assertFalse(PRIVATE_IP.containsMatchIn(text), name)
            assertFalse(Regex("(?i)\\\"(?:vin|serial(?:number)?|gps|ipAddress|rawDump|sourceLine|decompiledBody|blob)\\\"\\s*:").containsMatchIn(text), name)
        }
        listOf("sessionNonceSha256", "FIELD_PROVEN").forEach { token ->
            assertTrue(publication.values.none { it.toString(StandardCharsets.UTF_8).contains(token) }, token)
        }
    }
    @Test
    fun `exact 12 outputs canonical self pack manifest and forward hashes reproduce`() {
        val actualNames = Files.list(output).use { stream ->
            stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.map { it.fileName.toString() }.toList().toSet()
        }
        assertEquals(OUTPUT_NAMES, actualNames)
        JSON_OUTPUTS.forEach { name ->
            val bytes = Files.readAllBytes(output.resolve(name))
            assertArrayEquals(bytes, X4Json.canonical(X4Json.parse(bytes)), "$name canonical bytes")
        }
        SELF_HASHED_OUTPUTS.forEach { name ->
            val document = json(name).toMutableMap()
            val declared = X4Json.string(document.remove("selfSha256"))
            assertEquals(declared, X4Json.sha256(X4Json.canonical(document)), "$name self hash")
        }
        val manifest = json("pack-manifest.json")
        val manifestSelf = X4Json.string(manifest.getValue("selfSha256"))
        assertEquals(EXPECTED_MANIFEST_SELF, manifestSelf)
        val entries = X4Json.array(manifest.getValue("entries")).map(X4Json::asObject)
        assertEquals(11, entries.size)
        val paths = entries.map { X4Json.string(it.getValue("path")) }
        assertEquals(paths.sorted(), paths)
        assertEquals(OUTPUT_NAMES - "pack-manifest.json", paths.map { Path.of(it).fileName.toString() }.toSet())
        entries.forEach { entry ->
            val relative = X4Json.string(entry.getValue("path"))
            assertEquals(X4Json.string(entry.getValue("fullSha256")), X4Json.sha256(Files.readAllBytes(root.resolve(relative))), relative)
        }
        (OUTPUT_NAMES - "pack-manifest.json").forEach { name ->
            assertFalse(Files.readString(output.resolve(name)).contains(manifestSelf), name)
        }
        val planBytes = Files.readAllBytes(output.resolve("vehicle-session-plan.json"))
        assertEquals(EXPECTED_PLAN_FULL, X4Json.sha256(planBytes))
        val plan = X4Json.asObject(X4Json.parse(planBytes))
        val declaredPack = X4Json.string(X4Json.asObject(plan.getValue("template")).getValue("packSha256"))
        assertEquals(EXPECTED_PACK, declaredPack)
        val planWithoutSelf = plan.toMutableMap().also { it.remove("selfSha256") }
        assertEquals(declaredPack, X4Json.sha256(X4Json.canonical(zeroPackHashes(planWithoutSelf))))
        assertEquals(EXPECTED_COVERAGE_FULL, X4Json.sha256(Files.readAllBytes(output.resolve("corpus-coverage.json"))))
        val trace = json("traceability.json")
        TRACE_FILE_FIELDS.forEach { (field, name) ->
            assertEquals(X4Json.sha256(Files.readAllBytes(output.resolve(name))), trace[field], field)
        }
    }
    @Test
    fun `two fresh generations and checked pack are byte identical`() {
        LegacyBaselineIdentityTest.assertSealedParentFilesOnDisk(
            root, LegacyBaselineIdentityTest.PARENT_ARTIFACT_SHA256,
        )
        val parentBefore = LegacyBaselineIdentity.parentCombinedSha256(root)
        val first = temp.resolve("first")
        val second = temp.resolve("second")
        val renderer = ExpansionPackRenderer(root)
        val firstResult = renderer.writePack(first)
        val secondResult = renderer.writePack(second)
        assertEquals(OUTPUT_NAMES, firstResult.files.keys)
        assertEquals(OUTPUT_NAMES, secondResult.files.keys)
        OUTPUT_NAMES.forEach { name ->
            val checked = Files.readAllBytes(output.resolve(name))
            val firstBytes = Files.readAllBytes(first.resolve(name))
            val secondBytes = Files.readAllBytes(second.resolve(name))
            assertArrayEquals(firstBytes, secondBytes, "$name fresh runs")
            assertArrayEquals(checked, firstBytes, "$name checked output")
        }
        assertEquals(EXPECTED_PACK, firstResult.packSha256)
        assertEquals(firstResult.packSha256, secondResult.packSha256)
        assertEquals(EXPECTED_MANIFEST_SELF, firstResult.manifestSelfSha256)
        assertEquals(firstResult.manifestSelfSha256, secondResult.manifestSelfSha256)
        assertEquals(parentBefore, LegacyBaselineIdentity.parentCombinedSha256(root))
    }
    @Test
    fun `historical reviewer certification is preserved and stage A pass is append only`() {
        val spec = Files.readString(root.resolve(SPEC_PATH))
        val certificationPattern = Regex("<div class=\"review certification\">.*?</div>", RegexOption.DOT_MATCHES_ALL)
        val certifications = certificationPattern.findAll(spec).toList()
        assertTrue(certifications.size >= 3, "append-only reviewer certifications are required")
        val certification = certifications.last().value
        val declared = requireNotNull(
            Regex("reviewedContentProjectionSha256:</strong> <code>([0-9a-f]{64})</code>").find(certification),
        ).groupValues[1]
        assertEquals("435147484b7d9b382f5b8cb63409ab67a636dbff1300342b327dd3b07aff99a9", declared)
        assertTrue(Regex("reviewerId:</strong> <code>[A-Za-z0-9_-]+</code>").containsMatchIn(certification))
        listOf("P0", "P1", "P2", "P3").forEach { severity -> assertTrue(certification.contains("<strong>$severity:</strong> 0"), severity) }
        assertTrue(certification.contains("<strong>verdict:</strong> APPROVED"))
        assertTrue(spec.contains("Pass 25 — 2026-08-10 · Stage A boundary and trust-anchor transition"))
        assertTrue(spec.contains("7 READ_ONLY + 4 MILESTONE + 0 MUTATION") && spec.contains("INERT_IDENTITY_BLOCKED"))
    }
    private fun semanticPrivacyViolation(value: Any?, document: String? = null, path: List<Any> = emptyList()): Boolean = when (value) {
        is Map<*, *> -> value.entries.any { (childKey, child) ->
            val name = childKey as String; val childPath = path + name; val normalized = name.lowercase().filter(Char::isLetterOrDigit)
            PRIVACY_MARKER_KEYS.contains(normalized) || invalidHash(name, child, document, childPath) ||
                (SENSITIVE_KEYS.contains(normalized) && (child !is String || !SHA256.matches(child))) ||
                semanticPrivacyViolation(child, document, childPath)
        }
        is List<*> -> value.mapIndexed { index, child -> semanticPrivacyViolation(child, document, path + index) }.any { it }
        is String -> if (SHA256.matches(value) || value in FIXED_PUBLIC_VALUES) false else if (ID_TOKEN.matches(value))
            sensitiveTypedId(value) || SEMANTIC_PRIVACY_PATTERNS.any { it.containsMatchIn(typedPayload(value)) }
        else SEMANTIC_PRIVACY_PATTERNS.any { it.containsMatchIn(value) }
        else -> false
    }
    private fun invalidHash(name: String, value: Any?, document: String?, path: List<Any>) = name.lowercase().endsWith("sha256") && !(value is String && SHA256.matches(value) || value == null && nullableHashPath(document, path))
    private fun nullableHashPath(document: String?, path: List<Any>) = path.size == 3 && path[1] is Int &&
        (document == "corpus-coverage.json" && path[0] == "entries" && path[2] == "artifactSha256" ||
            document == "candidate-registry.json" && (path[0] == "candidates" && path[2] == "predecessorCandidateSha256" || path[0] == "history" && path[2] == "predecessorRegistryRevisionSha256"))
    private fun typedPayload(value: String): String = CANDIDATE_ID.matchEntire(value)?.groupValues?.get(1) ?: value.substringAfter('-', value)
    private fun sensitiveTypedId(value: String): Boolean {
        val parts = value.split('-', '.', '@').filter(String::isNotEmpty).drop(1).map(String::uppercase)
        return parts.indices.any { index -> SENSITIVE_ID_MARKERS.any { parts.drop(index).take(it.size) == it && index + it.size < parts.size } || SENSITIVE_ID_PREFIXES.any { parts[index].startsWith(it) && parts[index].length > it.length } }
    }
    private fun json(name: String): Map<String, Any?> = X4Json.asObject(X4Json.parse(Files.readAllBytes(output.resolve(name))))
    private fun reverse(links: List<Map<String, Any?>>, field: String): Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        links.forEach { link ->
            val requirement = X4Json.string(link.getValue("requirementId"))
            X4Json.strings(link.getValue(field)).forEach { id -> getOrPut(id) { sortedSetOf() }.add(requirement) }
        }
    }.mapValues { it.value.toSet() }.toSortedMap()
    private fun expectedReverse(selector: (ExpectedLink) -> List<String>): Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        EXPECTED_LINKS.forEachIndexed { index, link ->
            selector(link).forEach { id -> getOrPut(id) { sortedSetOf() }.add("REQ-X${index + 1}") }
        }
    }.mapValues { it.value.toSet() }.toSortedMap()
    private fun zeroPackHashes(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associate { (key, child) ->
            val name = key as String
            name to if (name == "packSha256") "0".repeat(64) else zeroPackHashes(child)
        }
        is List<*> -> value.map(::zeroPackHashes)
        else -> value
    }
    private data class ExpectedLink(val tasks: List<String>, val gates: List<String>, val artifacts: List<String>)
    companion object {
        private const val SPEC_PATH = "docs/specs/seal-hud-sign-candidate-expansion.html"
        private const val EXPECTED_PACK = "d99d602705f0da99dceb0ac43abe9c1a880b0ee817a03ef6aa7a19a2e10d68f3"
        private const val EXPECTED_PLAN_FULL = "51674404c6d93791c44198007d59f0fab05364cb6a0315bcb57d3de8446213b4"
        private const val EXPECTED_MANIFEST_SELF = "812dc0c76a1fd0d0ba760055b9ed996d676811eb5a0e7f3be460510313f9c714"
        private const val EXPECTED_COVERAGE_FULL = "d7b99563ee4710c121620904301d50a1f5a0cd479683c6c453715617bde9def4"
        private const val OUT = "docs/diagnostics/hud-sign-re/expansion/"
        private fun ids(value: String) = value.split(',')
        private fun link(tasks: String, gates: String, vararg artifacts: String) = ExpectedLink(ids(tasks), ids(gates), artifacts.toList())
        private val GATE_COMMANDS = (1..12).associate { number ->
            val gate = "GATE-X-O$number"
            gate to if (number == 11) "scripts/verify-hud-sign-candidate-expansion.sh"
            else "CLUSTERNAV_EXPANSION_GATE=$gate scripts/verify-hud-sign-candidate-expansion.sh"
        }
        private val EXPECTED_LINKS = listOf(
            link("TASK-X0,TASK-X2", "GATE-X-O1,GATE-X-O2,GATE-X-O9", "${OUT}legacy-baseline.json"),
            link("TASK-X1,TASK-X2", "GATE-X-O3,GATE-X-O4", "${OUT}corpus-coverage.json"),
            link("TASK-X1,TASK-X3", "GATE-X-O5,GATE-X-O8", "offcar-planner/src/main/kotlin/com/byd/clusternav/offcar/DiscoveryProbe.kt"),
            link("TASK-X2,TASK-X3", "GATE-X-O4,GATE-X-O8", "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionPromotionTest.kt"),
            link("TASK-X2,TASK-X3", "GATE-X-O6,GATE-X-O8", "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionPromotionTest.kt"),
            link("TASK-X2,TASK-X4", "GATE-X-O4,GATE-X-O6,GATE-X-O10", "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionPromotionTest.kt"),
            link("TASK-X0,TASK-X2,TASK-X4", "GATE-X-O1,GATE-X-O2,GATE-X-O9", "${OUT}candidate-registry.json"),
            link("TASK-X2,TASK-X3,TASK-X4", "GATE-X-O1,GATE-X-O8,GATE-X-O9", "${OUT}result-ledger.schema.json"),
            link("TASK-X3,TASK-X4", "GATE-X-O7,GATE-X-O9", "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/AdaptivePruningTest.kt"),
            link("TASK-X3,TASK-X4", "GATE-X-O4,GATE-X-O5,GATE-X-O7,GATE-X-O8", "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/SameSessionQuarantineTest.kt"),
            link("TASK-X2,TASK-X3,TASK-X4", "GATE-X-O4,GATE-X-O8", "${OUT}vehicle-session-plan.json"),
            link("TASK-X3,TASK-X4", "GATE-X-O7,GATE-X-O8", "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/AdaptivePruningTest.kt"),
            link("TASK-X3,TASK-X4", "GATE-X-O8,GATE-X-O9", "${OUT}pack-manifest.json"),
            link("TASK-X3,TASK-X4", "GATE-X-O7,GATE-X-O9", "${OUT}vehicle-session-plan.json"),
            link("TASK-X0,TASK-X1,TASK-X2,TASK-X3,TASK-X4,TASK-X5", "GATE-X-O10", "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionTransportFenceTest.kt"),
            link("TASK-X0,TASK-X1,TASK-X4,TASK-X5", "GATE-X-O3,GATE-X-O5,GATE-X-O12", "${OUT}corpus-coverage.json"),
            link("TASK-X1,TASK-X3,TASK-X4,TASK-X5", "GATE-X-O11,GATE-X-O12", "${OUT}result-ledger.schema.json", "scripts/verify-hud-sign-candidate-expansion.sh"),
            link("TASK-X4,TASK-X5", (1..12).joinToString(",") { "GATE-X-O$it" }, "${OUT}traceability.json", "offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionTraceabilityTest.kt"),
        )
        private val OUTPUT_NAMES = setOf(
            "legacy-baseline.json", "corpus-coverage.json", "evidence-map.json", "candidate-registry.json",
            "candidate-diff.json", "candidate-expansion-report.html", "vehicle-session-plan.json",
            "vehicle-session-plan.txt", "vehicle-session-checklist.html", "result-ledger.schema.json",
            "traceability.json", "pack-manifest.json",
        )
        private val JSON_OUTPUTS = setOf(
            "legacy-baseline.json", "corpus-coverage.json", "evidence-map.json", "candidate-registry.json",
            "candidate-diff.json", "vehicle-session-plan.json", "result-ledger.schema.json", "traceability.json", "pack-manifest.json",
        )
        private val SELF_HASHED_OUTPUTS = JSON_OUTPUTS - "result-ledger.schema.json"
        private val ROOT_DEFINITIONS = listOf(
            "LegacyBaselineRoot", "CorpusCoverageRoot", "EvidenceMapRoot", "CandidateRegistryRoot", "CandidateDiffRoot",
            "VehicleSessionPlanRoot", "ResultLedgerSchemaRoot", "TraceabilityRoot", "PackManifestRoot", "RenderEnvelope",
        ).map { "#/\$defs/$it" }
        private val PRIVATE_KEYS = setOf("vin", "serial", "serialnumber", "gps", "ip", "ipaddress", "rawdump", "sourceline", "decompiledbody", "blob")
        private val PRIVATE_PATH = Regex("(?i)/(?:Users|home)/[^/\\s\\\"'<>]+|[A-Za-z]:\\\\Users\\\\[^\\\\\\r\\n\\\"'<>]+")
        private val PRIVATE_IP = Regex("\\b(?:10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2})\\b")
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private const val REVISION_DIGITS = "(?:[1-9][0-9]{0,8}|1[0-9]{9}|20[0-9]{8}|21[0-3][0-9]{7}|214[0-6][0-9]{6}|2147[0-3][0-9]{5}|21474[0-7][0-9]{4}|214748[0-2][0-9]{3}|2147483[0-5][0-9]{2}|21474836[0-3][0-9]|214748364[0-7])"
        private val CANDIDATE_ID = Regex("^CAND-(?:H|S|NATIVE|PROVIDER)-[0-9]{3}-([A-Z0-9][A-Z0-9-]{0,63})@(?:$REVISION_DIGITS)$")
        private val ID_TOKEN = Regex("^(?:(?:ALIAS|ARTIFACT|BLOCKER|COMPONENT|CONFIG|CONSUMER|DIMENSION|FACT|HYP|PARAM|PERMISSION|PROFILE|PROVIDER|PRUNE|REASON|RENDERER|RULE|SEL|SENDER|TOKEN|TOOL|TRANSPORT|VALUE)-[A-Z0-9][A-Z0-9-]{0,63}|EVENT-[0-9]{6}|GATE-X-O(?:[1-9]|1[0-2])|OBS-(?:D-H0|M[1-4])-[A-Z0-9][A-Z0-9-]{0,63}|OP-(?:READ|MUTATE|CLEAR|INVERSE|RESTORE)-[A-Z0-9][A-Z0-9-]{0,63}|PROBE-(?:READ|LIST)-[A-Z0-9][A-Z0-9-]{0,63}|QRY-C(?:0[1-9]|1[0-2])-[A-Z0-9][A-Z0-9-]{0,63}|REQ-X(?:[1-9]|1[0-8])|RESULT-(?:D-H0|D-M[1-4]|P-M[1-4])-[0-9]{4}|ROW-[0-9]{4}-[A-Z0-9][A-Z0-9-]{0,63}|SESSION-[0-9A-F]{16}|TASK-X[0-5]|VERSION-[A-Z0-9][A-Z0-9.-]{0,63}|CAND-(?:H|S|NATIVE|PROVIDER)-[0-9]{3}-[A-Z0-9][A-Z0-9-]{0,63}@(?:$REVISION_DIGITS)|HIT-C(?:0[1-9]|1[0-2])-QRY-C(?:0[1-9]|1[0-2])-[A-Z0-9][A-Z0-9-]{0,63}-A[0-9a-f]{12}-S[0-9a-f]{12}-Q[0-9a-f]{12}-L[0-9a-f]{12}-T[0-9a-f]{12}|H[0-9]{1,3}|S[0-9]{1,3})$")
        private val FIXED_PUBLIC_VALUES = setOf(
            "https://clusternav.invalid/schema/result-ledger.schema.json",
            "https://json-schema.org/draft/2020-12/schema", "Đăng Khôi · dangkhoi",
        )
        private val PRIVACY_MARKER_KEYS = setOf(
            "vin", "vehicleidentificationnumber", "serial", "serialnumber", "gps", "latitude", "longitude",
            "coordinates", "ip", "ipaddress", "raw", "rawdump", "rawdata", "sourceline", "sourcepath",
            "sourcecode", "sourcebody", "decompiled", "decompiledbody", "decompiledsource", "blob",
        )
        private val SENSITIVE_KEYS = setOf(
            "apikey", "secret", "token", "password", "passwd", "credential", "authorization",
            "accesstoken", "refreshtoken", "privatekey",
        )
        private val SENSITIVE_ID_MARKERS = listOf("PASSWORD", "PASSWD", "SECRET", "CREDENTIAL", "AUTHORIZATION", "BEARER", "TOKEN", "API-KEY", "PRIVATE-KEY", "ACCESS-TOKEN", "REFRESH-TOKEN").map { it.split('-') }
        private val SENSITIVE_ID_PREFIXES = listOf("PASSWORD", "PASSWD", "SECRET", "CREDENTIAL", "AUTHORIZATION", "BEARER", "APIKEY", "PRIVATEKEY", "ACCESSTOKEN", "REFRESHTOKEN")
        private val SEMANTIC_PRIVACY_PATTERNS = listOf(
            Regex("""(?i)\b(?:api[_-]?key|secret|token|password|passwd|credential|authorization|bearer)(?:\s*[:=]\s*|[-_])["']?[A-Za-z0-9_./+=-]{8,}"""),
            Regex("""(?<![A-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-Z0-9])"""),
            Regex("""(?i)\b(?:serial(?:number|no)?|raw(?:dump|data|payload|bytes)?|source(?:[-_ ]?(?:line|path|code|text|body|dump))|decompiled(?:body|source|code|text)?|gps|latitude|longitude|ipaddress|blob)\b"""),
            Regex("""(?<![0-9])[-+]?(?:[0-8]?[0-9](?:\.[0-9]+)|90(?:\.0+)?)\s*[,;]\s*[-+]?(?:1[0-7][0-9](?:\.[0-9]+)|[0-9]?[0-9](?:\.[0-9]+)|180(?:\.0+)?)(?![0-9])"""),
            Regex("""(?<![0-9.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![0-9.])"""),
            Regex("""(?i)(?<![0-9a-f:])(?:[0-9a-f]{0,4}:){2,}[0-9a-f]{0,4}(?![0-9a-f:])"""),
            PRIVATE_PATH, Regex("""(?i)\b[a-z][a-z0-9+.-]{1,20}://[^\s"'<>]+"""),
            Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"""),
            Regex("""(?<![A-Za-z0-9])\+?[0-9][0-9 ()-]{7,14}(?![A-Za-z0-9])"""),
            Regex("""(?i)\b(?:[A-Za-z0-9-]+\.)*(?:internal|intranet|corp|private|cluster\.local|svc\.local)(?:\.[A-Za-z0-9-]+)+\b"""),
        )
        private val TRACE_FILE_FIELDS = mapOf(
            "candidateDiffFileSha256" to "candidate-diff.json", "checklistFileSha256" to "vehicle-session-checklist.html",
            "coverageFileSha256" to "corpus-coverage.json", "evidenceMapFileSha256" to "evidence-map.json",
            "ledgerSchemaFileSha256" to "result-ledger.schema.json", "legacyBaselineFileSha256" to "legacy-baseline.json",
            "planFileSha256" to "vehicle-session-plan.json", "planTextFileSha256" to "vehicle-session-plan.txt",
            "registryFileSha256" to "candidate-registry.json", "reportFileSha256" to "candidate-expansion-report.html",
        )
    }
}
internal object X4Json {
    fun parse(bytes: ByteArray): Any? = Reader(bytes.toString(StandardCharsets.UTF_8)).read()
    @Suppress("UNCHECKED_CAST")
    fun asObject(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: error("expected JSON object")
    fun array(value: Any?): List<Any?> = value as? List<*> ?: error("expected JSON array")
    fun string(value: Any?): String = value as? String ?: error("expected JSON string")
    fun strings(value: Any?): List<String> = array(value).map(::string)
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun canonical(value: Any?): ByteArray = render(value).toByteArray(StandardCharsets.UTF_8)
    private fun render(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Long -> value.toString()
        is Int -> value.toString()
        is String -> quote(value)
        is List<*> -> value.joinToString(",", "[", "]") { render(it) }
        is Map<*, *> -> value.entries.map { string(it.key) to it.value }.sortedBy { it.first }
            .joinToString(",", "{", "}") { quote(it.first) + ":" + render(it.second) }
        else -> error("unsupported JSON value ${value::class.java.name}")
    }
    private fun quote(raw: String): String = buildString {
        val value = Normalizer.normalize(raw, Normalizer.Form.NFC)
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u00${char.code.toString(16).padStart(2, '0')}") else append(char)
            }
        }
        append('"')
    }
    fun walk(value: Any?, visitor: (Map<String, Any?>) -> Unit) {
        when (value) {
            is Map<*, *> -> asObject(value).also(visitor).values.forEach { walk(it, visitor) }
            is List<*> -> value.forEach { walk(it, visitor) }
        }
    }
    private class Reader(private val source: String) {
        private var index = 0
        fun read(): Any? = value().also { require(index == source.length) { "trailing JSON at $index" } }
        private fun value(): Any? = when (source.getOrNull(index)) {
            '{' -> objectValue()
            '[' -> arrayValue()
            '"' -> stringValue()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            '-', in '0'..'9' -> numberValue()
            else -> error("invalid JSON at $index")
        }
        private fun objectValue(): Map<String, Any?> {
            expect('{')
            val result = linkedMapOf<String, Any?>()
            if (take('}')) return result
            while (true) {
                val key = stringValue()
                expect(':')
                require(!result.containsKey(key)) { "duplicate key $key" }
                result[key] = value()
                if (take('}')) return result
                expect(',')
            }
        }
        private fun arrayValue(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            if (take(']')) return result
            while (true) {
                result += value()
                if (take(']')) return result
                expect(',')
            }
        }
        private fun stringValue(): String {
            expect('"')
            return buildString {
                while (true) {
                    val char = source.getOrNull(index++) ?: error("unterminated string")
                    when (char) {
                        '"' -> return@buildString
                        '\\' -> append(escape())
                        else -> { require(char.code >= 0x20); append(char) }
                    }
                }
            }
        }
        private fun escape(): Char = when (val escaped = source.getOrNull(index++) ?: error("unterminated escape")) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> source.substring(index, index + 4).also { index += 4 }.toInt(16).toChar()
            else -> error("invalid escape $escaped")
        }
        private fun numberValue(): Long {
            val start = index
            take('-')
            require(source.getOrNull(index) in '0'..'9')
            if (source[index] == '0') index++ else while (source.getOrNull(index) in '0'..'9') index++
            require(source.getOrNull(index) !in listOf('.', 'e', 'E'))
            return source.substring(start, index).toLong()
        }
        private fun <T> literal(text: String, result: T): T {
            require(source.startsWith(text, index)); index += text.length; return result
        }
        private fun expect(char: Char) { require(take(char)) { "expected $char at $index" } }
        private fun take(char: Char): Boolean = if (source.getOrNull(index) == char) { index++; true } else false
    }
}
