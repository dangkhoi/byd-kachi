package com.byd.clusternav.offcar

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.SPEC_PATH
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.OUTPUT_DIRECTORY
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.VERIFIER_PATH
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.IMPLEMENTATION_SOURCES
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.SOURCE_ARTIFACT_PATHS
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.CURRENT_PATHS
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.BOUNDARY_PATH
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.REVISION_ONE_SHA256
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.HISTORICAL_PARENT_BASELINE
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.PARENT_ARTIFACT_HASHES
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.T10_SOURCE_PATHS
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.POST_BUILD_PATHS
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.T11_PATHS
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.T11_HASHES
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.isT10InventoryPath
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.IGNORED_DIRECTORY_NAMES
import com.byd.clusternav.offcar.ExpansionTransportFenceFixtures.COMPILED_BANS

class ExpansionTransportFenceTest {
    @TempDir
    lateinit var temp: Path

    private val root: Path get() = Path.of(System.getProperty("clusternav.root")).toAbsolutePath().normalize()

    @Test
    fun `all 29 expansion current paths remain exact authorized and materialized`() {
        val activeSource = classPaths(authorityRevisions().last(), "SOURCE_SEAL_INPUT").toSet()
        assertEquals(29, CURRENT_PATHS.size)
        assertEquals(CURRENT_PATHS.size, CURRENT_PATHS.distinct().size)
        CURRENT_PATHS.forEach { relative ->
            assertFalse(relative.startsWith('/') || relative.contains("..") || relative.contains('*'), relative)
            val path = root.resolve(relative).normalize()
            assertTrue(path.startsWith(root), relative)
            assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), relative)
            assertTrue(relative in activeSource, "expansion CURRENT is not authorized: $relative")
        }

        val spec = Files.readString(root.resolve(SPEC_PATH))
        val currentBlock = requireNotNull(
            Regex("<h4>CURRENT X0–X5</h4><pre><code>(.*?)</code></pre>", RegexOption.DOT_MATCHES_ALL).find(spec),
        ).groupValues[1].lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        assertEquals(CURRENT_PATHS, currentBlock)
        assertEquals(SOURCE_ARTIFACT_PATHS, CURRENT_PATHS.filter { it in SOURCE_ARTIFACT_PATHS })

        val output = root.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY)
        val outputPaths = Files.list(output).use { stream ->
            stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .map { root.relativize(it).toString() }.toList().toSet()
        }
        assertEquals(CURRENT_PATHS.filter { it.startsWith("$OUTPUT_DIRECTORY/") }.toSet(), outputPaths)
        assertEquals(ExpansionPackRenderer.OUTPUT_NAMES, outputPaths.map { Path.of(it).fileName.toString() }.toSet())
    }

    @Test
    fun `boundary authority is canonical append only exact and independently hashed`() {
        val path = root.resolve(BOUNDARY_PATH)
        val bytes = Files.readAllBytes(path)
        val authority = X4Json.asObject(X4Json.parse(bytes))
        assertArrayEquals(bytes, X4Json.canonical(authority))
        assertEquals(setOf("activeRevision", "revisions", "schemaId", "selfSha256"), authority.keys)
        assertEquals(2L, authority["activeRevision"])
        assertEquals("clusternav.offcar-boundary-revisions/v1", authority["schemaId"])
        val rootProjection = authority.toMutableMap().also { it.remove("selfSha256") }
        assertEquals(authority["selfSha256"], X4Json.sha256(X4Json.canonical(rootProjection)))

        val revisions = authorityRevisions()
        assertEquals(listOf(1L, 2L), revisions.map { it["revision"] })
        var predecessor: String? = null
        revisions.forEachIndexed { index, revision ->
            assertEquals(
                setOf("legacyParentBaselineSha256", "pathClasses", "predecessorRevisionSha256", "revision", "revisionSha256"),
                revision.keys,
            )
            assertEquals(predecessor, revision["predecessorRevisionSha256"])
            val projection = revision.toMutableMap().also { it.remove("revisionSha256") }
            val declared = X4Json.string(revision.getValue("revisionSha256"))
            assertEquals(declared, X4Json.sha256(X4Json.canonical(projection)))
            predecessor = declared
            val classes = X4Json.asObject(revision.getValue("pathClasses"))
            classes.forEach { (name, raw) ->
                val value = X4Json.asObject(raw)
                assertEquals(setOf("paths", "policyTokens"), value.keys, name)
                listOf("paths", "policyTokens").forEach { field ->
                    val items = X4Json.strings(value.getValue(field))
                    assertEquals(items.distinct().sorted(), items, "$name.$field")
                }
                classPaths(revision, name).forEach { relative ->
                    assertFalse(relative.startsWith('/') || relative.contains('\\') || relative.split('/').any { it.isEmpty() || it == "." || it == ".." } || relative.any { it in "*?[]" }, relative)
                }
            }
            val expected = if (index == 0) setOf("CURRENT", "FUTURE_T10", "FUTURE_T11")
                else setOf("FUTURE_FORBIDDEN", "LOCAL_IGNORED", "POST_BUILD_ATTESTATION", "SOURCE_SEAL_INPUT")
            assertEquals(expected, classes.keys)
        }
        assertEquals(REVISION_ONE_SHA256, revisions.first()["revisionSha256"])
        assertEquals(HISTORICAL_PARENT_BASELINE, revisions.first()["legacyParentBaselineSha256"])
        assertEquals(LegacyBaselineIdentity.PARENT_BASELINE_SHA256, revisions.last()["legacyParentBaselineSha256"])

        val source = classPaths(revisions.last(), "SOURCE_SEAL_INPUT").toSet()
        val post = classPaths(revisions.last(), "POST_BUILD_ATTESTATION").toSet()
        val local = classPaths(revisions.last(), "LOCAL_IGNORED").toSet()
        val forbidden = classPaths(revisions.last(), "FUTURE_FORBIDDEN").toSet()
        assertEquals(155, source.size)
        assertEquals(POST_BUILD_PATHS, post)
        assertEquals(setOf(".authorized-build", ".t10-local", "keystore.properties", "release.keystore"), local)
        assertEquals(setOf("POLICY-T10-APK-ARTIFACT-NAME"), classTokens(revisions.last(), "LOCAL_IGNORED").toSet())
        assertEquals(T11_PATHS, forbidden)
        assertTrue(source.intersect(post + forbidden + local).isEmpty())
        assertTrue(post.intersect(forbidden + local).isEmpty())
        assertTrue(forbidden.intersect(local).isEmpty())
        assertEquals(T10_SOURCE_PATHS, source.filter(::isT10InventoryPath).toSet())
        assertTrue(classPaths(revisions.first(), "CURRENT").all(source::contains))
        assertTrue(CURRENT_PATHS.all(source::contains))
    }

    @Test
    fun `parent baseline and T11 retain exact hashes while authorized T10 may be absent`() {
        LegacyBaselineIdentityTest.assertSealedParentFilesOnDisk(root, PARENT_ARTIFACT_HASHES)
        val baseline = LegacyBaselineIdentity.capture(root)
        assertEquals(LegacyBaselineIdentity.PARENT_BASELINE_SHA256, baseline.parentCombinedSha256)
        assertEquals(PARENT_ARTIFACT_HASHES, baseline.artifacts.associate { it.path to it.fullSha256 })

        val checkedBaseline = X4Json.asObject(
            X4Json.parse(Files.readAllBytes(root.resolve("$OUTPUT_DIRECTORY/legacy-baseline.json"))),
        )
        val checkedArtifacts = X4Json.array(checkedBaseline.getValue("artifacts")).associate { value ->
            val artifact = X4Json.asObject(value)
            X4Json.string(artifact.getValue("path")) to X4Json.string(artifact.getValue("fullSha256"))
        }
        assertEquals(PARENT_ARTIFACT_HASHES, checkedArtifacts)
        assertEquals(LegacyBaselineIdentity.PARENT_BASELINE_SHA256, checkedBaseline["parentCombinedSha256"])

        assertEquals(13, T11_PATHS.size)
        assertEquals(2, T11_HASHES.size)
        T11_PATHS.forEach { relative ->
            val path = root.resolve(relative).normalize()
            assertTrue(path.startsWith(root), relative)
            val expected = T11_HASHES[relative]
            if (expected == null) assertFalse(Files.exists(path, LinkOption.NOFOLLOW_LINKS), "T11 path must remain absent: $relative")
            else {
                assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), relative)
                assertEquals(expected, sha256(Files.readAllBytes(path)), relative)
            }
        }
        (T10_SOURCE_PATHS + POST_BUILD_PATHS).forEach { relative ->
            val candidate = root.resolve(relative).normalize()
            assertTrue(candidate.startsWith(root), relative)
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                assertTrue(Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS), "authorized T10 path is not a regular file: $relative")
            }
        }
    }

    @Test
    fun `generation has no side effects outside the 12 expansion outputs`() {
        LegacyBaselineIdentityTest.assertSealedParentFilesOnDisk(root, PARENT_ARTIFACT_HASHES)
        val before = repositorySnapshotOutsideOutputs()
        val parentBefore = LegacyBaselineIdentity.parentCombinedSha256(root)
        val t11Before = T11_HASHES.mapValues { (relative, _) -> sha256(Files.readAllBytes(root.resolve(relative))) }
        val result = ExpansionPackRenderer(root).writePack(temp.resolve("generated"))
        val after = repositorySnapshotOutsideOutputs()

        assertEquals(before, after)
        assertEquals(parentBefore, LegacyBaselineIdentity.parentCombinedSha256(root))
        assertEquals(t11Before, T11_HASHES.mapValues { (relative, _) -> sha256(Files.readAllBytes(root.resolve(relative))) })
        assertEquals(ExpansionPackRenderer.OUTPUT_NAMES, result.files.keys)
        CURRENT_PATHS.filter { it.startsWith("$OUTPUT_DIRECTORY/") }.forEach { relative ->
            val name = Path.of(relative).fileName.toString()
            assertArrayEquals(Files.readAllBytes(root.resolve(relative)), result.files.getValue(name), relative)
        }
    }

    @Test
    fun `expansion planner source and compiled symbols contain no transport surface`() {
        val sourceBan = Regex(
            "(?i)(?<![A-Za-z0-9_])(?:Process|Runtime|network|socket|DADB|CarExec|Android|device|callback|execute)[A-Za-z0-9_]*(?![A-Za-z0-9_])|(?<![A-Za-z0-9_])ADB(?![A-Za-z0-9_])",
        )
        IMPLEMENTATION_SOURCES.forEach { relative ->
            val source = Files.readString(root.resolve(relative)).replace("device-width", "")
            val match = sourceBan.find(source)
            assertTrue(match == null, "$relative contains forbidden source symbol ${match?.value}")
        }

        val classesRoot = root.resolve("offcar-planner/build/classes/kotlin/main/com/byd/clusternav/offcar")
        assertTrue(Files.isDirectory(classesRoot), "compiled planner classes are required")
        val sourceNames = IMPLEMENTATION_SOURCES.map { Path.of(it).fileName.toString() }.toSet()
        val coveredSources = mutableSetOf<String>()
        var compiledClasses = 0
        Files.walk(classesRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") }.forEach { path ->
                val bytes = Files.readAllBytes(path)
                val sourceName = sourceNames.singleOrNull { bytes.containsUtf8(it) } ?: return@forEach
                coveredSources += sourceName
                compiledClasses++
                val symbols = String(bytes, StandardCharsets.ISO_8859_1).replace("device-width", "")
                COMPILED_BANS.forEach { ban ->
                    assertFalse(ban.containsMatchIn(symbols), "$path contains forbidden compiled symbol ${ban.pattern}")
                }
            }
        }
        assertEquals(sourceNames, coveredSources)
        assertTrue(compiledClasses >= sourceNames.size, "every implementation source must produce a scanned class")
    }

    @Test
    fun `verifier is argument-free hardened offline and has exact fixed gate mappings`() {
        val verifier = root.resolve(VERIFIER_PATH)
        assertTrue(Files.isExecutable(verifier), "verifier must be executable")
        val text = Files.readString(verifier)
        val lines = text.lineSequence().toList()
        assertEquals("#!/bin/bash -p", lines.first())
        assertEquals("set -euo pipefail", lines[1])
        listOf(
            "if (( $# != 0 )); then", "CLUSTERNAV_EXPANSION_GATE+set", "unset CLUSTERNAV_EXPANSION_GATE",
            "Kernel-dispatched privileged startup ignores BASH_ENV/ENV, imported shell functions",
            "SHELLOPTS/BASHOPTS/CDPATH/GLOBIGNORE before line 1",
            "ignored BASH_FUNC_* and option entries cannot propagate to any verifier child",
            "BOOTSTRAP_ENV=(", "/usr/bin/env -i", "/bin/bash -p -s", "CLUSTERNAV_VERIFIER_BODY",
            "GATE-X-O11 is the plain no-selector full verifier", "BASH_SOURCE[0]", "symbolic link component is forbidden",
            "PATH=\"/usr/bin:/bin\"", "unset TMPDIR TEMP TMP", "TEMP_BASE_LOGICAL=\"/tmp\"", "pwd -P",
            "CLUSTERNAV_OFFCAR_ONLY=1", "CLUSTERNAV_ALLOW_VEHICLE=0", "CLUSTERNAV_ALLOW_NETWORK=0",
            "GRADLE_OFFLINE=true", "--no-daemon", "--rerun-tasks", "--no-build-cache", "--console=plain",
            "Gradle test task was not freshly executed", "java\\.specification\\.version", "sys.version_info >= (3, 9)", "O_NOFOLLOW",
            "a5a5c199ba02189ae8c46a334223371a20599d9c298ef65e7540ede4a3f72d59",
            "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7",
            "556f4aa5f360e35fca77b010b306307765d4f4915a82a612035cd5cfb7a587cb",
            "semantic privacy fence failed", "output allowlist/no-follow mismatch", "/usr/bin/diff -ru",
        ).forEach { token -> assertTrue(text.contains(token), token) }
        assertTrue(text.indexOf("unset CLUSTERNAV_EXPANSION_GATE") < text.indexOf("run_selected_gate"))
        assertFalse(Regex("\\$(?:\\{)?(?:PATH|TMPDIR)").containsMatchIn(text), "inherited PATH/TMPDIR expansion")

        val mappings = linkedMapOf(
            "GATE-X-O1" to "LegacyBaselineIdentityTest", "GATE-X-O2" to "ExpansionDeterminismTest",
            "GATE-X-O4" to "ExpansionPromotionTest", "GATE-X-O5" to "DerivationClosureTest",
            "GATE-X-O6" to "ExpansionPromotionTest", "GATE-X-O7" to "AdaptivePruningTest",
            "GATE-X-O9" to "ExpansionDeterminismTest",
            "GATE-X-O10" to "ExpansionTransportFenceTest", "GATE-X-O12" to "ExpansionTraceabilityTest",
        )
        mappings.forEach { (gate, test) ->
            val dispatch = "$gate) run_gradle_test \"com.byd.clusternav.offcar.$test\" ;;"
            assertTrue(text.contains(dispatch), dispatch)
        }
        val o8 = "GATE-X-O8) run_gradle_test \"com.byd.clusternav.offcar.SameSessionQuarantineTest\" " +
            "\"com.byd.clusternav.offcar.LedgerSemanticValidationTest\"; verify_o8_test_count ;;"
        assertTrue(text.contains(o8), o8)
        assertTrue(text.contains("GATE-X-O8 must select exactly 9 tests"))
        assertTrue(text.contains("SameSessionQuarantineTest\": 3") && text.contains("LedgerSemanticValidationTest\": 6"))
        assertTrue(text.contains("GATE-X-O3) run_python_coverage_test ;;"))
        assertTrue(text.contains("if [[ \"\$SELECTED_GATE\" == \"GATE-X-O12\" ]]; then begin_output_checks; fi"))
        assertFalse(text.contains("GATE-X-O12) scripts/"), "O12 must not recurse")
        Regex("run_gradle_test \\\"([^\\\"]+)\\\"").findAll(text).forEach { match ->
            val command = ":offcar-planner:test --tests ${match.groupValues[1]}"
            assertFalse(Regex("(?i):(?:app|core|car-integration):|assemble|build|install|connected|device").containsMatchIn(command), command)
        }
        listOf("eval ", "bash -c", "sh -c", "curl ", "wget ", "ssh ", "nc ", "adb ", "dadb ", "command -v", "readlink -f")
            .forEach { assertFalse(text.contains(it, ignoreCase = true), it) }
    }

    @Test
    fun `verifier privileged startup rejects BASH_ENV functions options and invalid selectors`() {
        val attackerBin = temp.resolve("attacker-bin")
        Files.createDirectories(attackerBin)
        val fakeBash = attackerBin.resolve("bash")
        Files.writeString(fakeBash, "#!/bin/sh\nprintf 'HIJACKED_INTERPRETER\\n'\n")
        assertTrue(fakeBash.toFile().setExecutable(true))
        val startupMarker = temp.resolve("prestart-bypass.marker")
        val bashEnv = temp.resolve("malicious-bash-env.sh")
        Files.writeString(bashEnv, "printf 'BASH_ENV_PRESTART_BYPASS\\n' >>\"\$STARTUP_MARKER\"\nexit 0\n")
        val poisoned = mapOf(
            "PATH" to attackerBin.toString(), "TMPDIR" to temp.resolve("attacker-tmp").toString(),
            "CLASSPATH" to "attacker.jar", "JAVA_TOOL_OPTIONS" to "-javaagent:attacker.jar",
            "HTTP_PROXY" to "http://proxy.internal.example", "BASH_ENV" to bashEnv.toString(),
            "BASH_FUNC_printf%%" to "() { /bin/echo IMPORTED_FUNCTION_BYPASS >> \"\$STARTUP_MARKER\"; }",
            "SHELLOPTS" to "xtrace", "BASHOPTS" to "extdebug",
            "PS4" to "\$(/bin/echo SHELLOPTS_PRESTART_BYPASS >> \"\$STARTUP_MARKER\")",
            "STARTUP_MARKER" to startupMarker.toString(), "CDPATH" to temp.toString(), "GLOBIGNORE" to "*",
        )
        val expected = linkedMapOf(
            "" to "must not be empty", "GATE-X-O99" to "unknown CLUSTERNAV_EXPANSION_GATE",
            " GATE-X-O1" to "unknown CLUSTERNAV_EXPANSION_GATE", "GATE-X-O11" to "plain no-selector full verifier",
        )
        expected.forEach { (selector, message) ->
            Files.deleteIfExists(startupMarker)
            val result = runProcess(
                listOf(root.resolve(VERIFIER_PATH).toString()),
                poisoned + ("CLUSTERNAV_EXPANSION_GATE" to selector),
            )
            assertTrue(result.exitCode != 0, selector)
            assertTrue(result.output.contains(message), result.output)
            assertFalse(result.output.contains("HIJACKED_INTERPRETER"), result.output)
            assertFalse(result.output.contains("PRESTART_BYPASS") || result.output.contains("IMPORTED_FUNCTION_BYPASS"), result.output)
            assertFalse(Files.exists(startupMarker, LinkOption.NOFOLLOW_LINKS), "startup payload ran for $selector")
        }
    }

    @Test
    fun `verifier rejects invocation through a symbolic link ancestor`() {
        val linkedRoot = temp.resolve("linked-repository")
        Files.createSymbolicLink(linkedRoot, root)
        val result = runProcess(
            listOf("/bin/bash", linkedRoot.resolve(VERIFIER_PATH).toString()),
            mapOf("CLUSTERNAV_EXPANSION_GATE" to "GATE-X-O3", "PATH" to temp.resolve("poison").toString()),
        )
        assertTrue(result.exitCode != 0)
        assertTrue(result.output.contains("symbolic link component is forbidden"), result.output)
    }

    @Test
    fun `renderer rejects symbolic link ancestors and never follows an output leaf`() {
        val physical = temp.resolve("physical")
        Files.createDirectories(physical)
        val linked = temp.resolve("linked")
        Files.createSymbolicLink(linked, physical)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            ExpansionPackRenderer(root).writePack(linked.resolve("pack"))
        }

        val physicalParent = temp.toRealPath().resolve("physical-parent")
        val physicalProject = physicalParent.resolve("project")
        Files.createDirectories(physicalProject)
        val linkedParent = temp.toRealPath().resolve("linked-parent")
        Files.createSymbolicLink(linkedParent, physicalParent)
        val linkedProject = linkedParent.resolve("project")
        assertFalse(Files.isSymbolicLink(linkedProject))
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            ExpansionPackRenderer(linkedProject).writePack(linkedProject.resolve(OUTPUT_DIRECTORY))
        }
        assertFalse(Files.exists(physicalProject.resolve("docs"), LinkOption.NOFOLLOW_LINKS))

        val output = temp.resolve("nofollow")
        Files.createDirectories(output)
        val protected = temp.resolve("protected.txt")
        Files.writeString(protected, "must remain unchanged")
        Files.createSymbolicLink(output.resolve(ExpansionPackRenderer.BASELINE), protected)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            ExpansionPackRenderer(root).writePack(output)
        }
        assertEquals("must remain unchanged", Files.readString(protected))
    }

    @Test
    fun `semantic privacy scanner rejects negative fixtures permits fixed metadata and refuses links`() {
        val verifier = Files.readString(root.resolve(VERIFIER_PATH))
        val program = verifier.substringAfter("# PRIVACY_SCANNER_PROGRAM_BEGIN\n")
            .substringBefore("\n# PRIVACY_SCANNER_PROGRAM_END")
        assertTrue(program.startsWith("import ipaddress"))
        val scanner = temp.resolve("privacy-scanner.py")
        Files.writeString(scanner, program)
        val canonicalResult = runPrivacyScanner(scanner, root.resolve(OUTPUT_DIRECTORY))
        assertEquals(0, canonicalResult.exitCode, canonicalResult.output)
        val fixture = temp.resolve("privacy-fixture")
        Files.createDirectories(fixture)
        val file = fixture.resolve("rendered.txt")
        val negative = listOf(
            "api_key=sk-proj-abcdef", "VIN 1HGCM82633A004352",
            "serialNumber=SERIAL-42", "rawDump=payload", "sourceLine=42", "decompiledBody=text",
            "GPS 21.0285,105.8542", "public 8.8.8.8", "ipv6 2001:db8::1", "/Users/example/private.txt",
            "https://example.com/private", "person@example.com", "+84912345678", "name=Jane Citizen",
            "governmentId=123456789", "card 4111 1111 1111 1111", "build.internal.example",
            "FACT-8.8.8.8", "VERSION-8.8.8.8", "FACT-1HGCM82633A004352",
            "FACT-RAW-DUMP-PAYLOAD", "FACT-SOURCE-LINE-42", "FACT-SERIAL-NUMBER-42", "FACT-GPS-21.0285",
        )
        negative.forEach { value ->
            Files.writeString(file, value)
            val result = runPrivacyScanner(scanner, fixture)
            assertTrue(result.exitCode != 0, "fixture escaped scanner: $value")
        }
        val nested = fixture.resolve("nested.json")
        val nestedLeaks = listOf(
            "{\"outer\":{\"token\":\"opaque-private-credential\"}}",
            "{\"outer\":{\"passwordSha256\":\"password=CorrectHorseBatteryStaple\"}}",
            "{\"outer\":{\"passwordSha256\":null}}",
            "{\"outer\":{\"value\":\"PASSWORD-CORRECTHORSEBATTERYSTAPLE\"}}",
            "{\"outer\":{\"password\":\"PASSWORD-CORRECTHORSEBATTERYSTAPLE\"}}",
            "{\"outer\":{\"factId\":\"FACT-PASSWORD-CORRECTHORSEBATTERYSTAPLE\"}}",
        )
        nestedLeaks.forEach { value ->
            Files.writeString(nested, value)
            assertTrue(runPrivacyScanner(scanner, fixture).exitCode != 0, "nested leak escaped scanner: $value")
        }
        Files.delete(nested)

        Files.writeString(
            file,
            "https://clusternav.invalid/schema/result-ledger.schema.json " +
                "https://json-schema.org/draft/2020-12/schema ${"a".repeat(64)} FACT-SAFE-EVIDENCE TOKEN-C01-QUERY dangkhoi · dangkhoi",
        )
        assertEquals(0, runPrivacyScanner(scanner, fixture).exitCode)
        Files.delete(file)
        val outside = temp.resolve("outside.txt")
        Files.writeString(outside, "clean")
        Files.createSymbolicLink(fixture.resolve("linked.txt"), outside)
        val linkedResult = runPrivacyScanner(scanner, fixture)
        assertTrue(linkedResult.exitCode != 0)
        assertTrue(linkedResult.output.contains("symbolic link output"), linkedResult.output)
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    private fun runPrivacyScanner(scanner: Path, fixture: Path): ProcessResult {
        val python = listOf(
            Path.of("/usr/bin/python3"), Path.of("/opt/homebrew/opt/python@3.14/bin/python3.14"),
            Path.of("/usr/local/bin/python3"),
        ).firstOrNull(Files::isExecutable)?.toRealPath() ?: error("explicit Python 3 candidate is unavailable")
        return runProcess(listOf(python.toString(), "-I", scanner.toString(), fixture.toString()))
    }

    private fun runProcess(command: List<String>, environment: Map<String, String> = emptyMap()): ProcessResult {
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().putAll(environment)
        val process = builder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return ProcessResult(process.waitFor(), output)
    }

    private fun authorityRevisions(): List<Map<String, Any?>> = X4Json.array(X4Json.asObject(
        X4Json.parse(Files.readAllBytes(root.resolve(BOUNDARY_PATH)))).getValue("revisions")).map(X4Json::asObject)
    private fun classPaths(revision: Map<String, Any?>, name: String) = X4Json.strings(
        X4Json.asObject(X4Json.asObject(revision.getValue("pathClasses")).getValue(name)).getValue("paths"))
    private fun classTokens(revision: Map<String, Any?>, name: String) = X4Json.strings(
        X4Json.asObject(X4Json.asObject(revision.getValue("pathClasses")).getValue(name)).getValue("policyTokens"))

    private fun repositorySnapshotOutsideOutputs(): Map<String, String> = buildMap {
        Files.walk(root).use { paths ->
            paths.filter { path ->
                val relative = root.relativize(path)
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    !relative.toString().startsWith("$OUTPUT_DIRECTORY/") &&
                    relative.none { it.toString() in IGNORED_DIRECTORY_NAMES }
            }.forEach { path -> put(root.relativize(path).toString(), sha256(Files.readAllBytes(path))) }
        }
    }.toSortedMap()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun ByteArray.containsUtf8(value: String): Boolean { val target = value.toByteArray(StandardCharsets.UTF_8)
        return indices.any { start -> start + target.size <= size && target.indices.all { this[start + it] == target[it] } } }
}
