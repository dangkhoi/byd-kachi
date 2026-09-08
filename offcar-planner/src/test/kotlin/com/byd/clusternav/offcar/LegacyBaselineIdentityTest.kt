package com.byd.clusternav.offcar

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyBaselineIdentityTest {
    companion object {
        const val PARENT_BASELINE_SHA256 = "5b49a5ea9c23950dfd3d3112285db1501d85ec30dd26bbac03f5e96398791513"

        /**
         * Per-file sealed digests of the 13 parent artifacts, restated here **on purpose**.
         *
         * Why restated and not shared with `ExpansionTransportFenceTest.PARENT_ARTIFACT_HASHES`:
         * the seal's whole value is that two independent statements of the same truth must agree —
         * a single shared constant would be re-pinnable in one edit.
         *
         * Why per-file at all (E6a, 2026-08-23): the combined digest alone reports only two opaque
         * 64-char hashes, which cost three separate investigations before anyone found that exactly
         * two files had drifted. This map turns the same failure into a file path.
         *
         * **If this map disagrees with the bytes on disk, the bytes are wrong — never this map.**
         * The fix is to restore the file (`git checkout <pre-drift-commit> -- <path>`), never to
         * re-pin the constant to whatever the file currently hashes to: re-pinning IS breaking the seal.
         */
        val PARENT_ARTIFACT_SHA256 = mapOf(
            "docs/diagnostics/hud-sign-re/README.md" to
                "f854683acbda36c69f899a5d3a3bdc326c0df966dc49b24059eb2d94ccb1ee46",
            "docs/diagnostics/hud-sign-re/candidate-report.html" to
                "af3c3db29e29b5c4cd4ebd0a0d4ef863de5a7a2d7f60c527bbe989596ce3eb36",
            "docs/diagnostics/hud-sign-re/corpus-completeness.json" to
                "87610a0e7a54e7d634dbcad8a423906a494e2add2e6e50b61828e1ee7217db79",
            "docs/diagnostics/hud-sign-re/evidence-index.json" to
                "ac3fd27701e6b05c5037594b35d314b49ddadaeb0315d8a64c3a3da0bef980b9",
            "docs/diagnostics/hud-sign-re/first-launch-emulator-result.json" to
                "e60e63dced72dbc0d742476883930088385c5812b29d176f145f42430540ae39",
            "docs/diagnostics/hud-sign-re/m1-nav-hud-plan.json" to
                "c8eff7210e093fa1b2e32328abeddf37fa51d919d524df851e4ecd634d289bf9",
            "docs/diagnostics/hud-sign-re/m2-hud-road-plan.json" to
                "dffb04d3e26beadd9fd7f813870fca6712d5f213f4145787280ddb4718437faf",
            "docs/diagnostics/hud-sign-re/m3-cluster-sign-plan.json" to
                "ac2de2631de73ab2ddd2ad4efa2f33e805fae2d8007091e2ae3497c64625fb7e",
            "docs/diagnostics/hud-sign-re/m4-hud-sign-plan.json" to
                "51fc71db1050baa816e9df2e1250a196c1d6e608a48df228e5318d6077c910d1",
            "docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json" to
                "d2d7f63ee1916905e8ef21ea58242f22f4e2c02a5a1e0b3854766341f77e9464",
            "docs/diagnostics/hud-sign-re/traceability.json" to
                "332ae311ed642441c7ec8640fc4c389e1e7865813eafef8b1442645ff3791e60",
            "docs/diagnostics/hud-sign-re/zero-hit-report.txt" to
                "55acd8bf51a0baf9f397b8765162f063fc1686d9544b3fdbc83eaa96955f273f",
            "docs/specs/seal-nav-hud-speed-sign-offcar.html" to
                "781ff2b47f38d51deec66a47464ab78f37d781970499fdc64b86386423a28f87",
        )

        /**
         * Names the drifted file(s) before any combined-digest assertion gets the chance to fail
         * with an anonymous 64-char hash, or with an "array contents differ at index [n]" byte
         * diff against a pack regenerated from those same drifted parents.
         *
         * Shared **body**, deliberately NOT shared **constants**: every caller passes its own
         * restatement of the seal ([PARENT_ARTIFACT_SHA256] here,
         * `ExpansionTransportFenceTest.PARENT_ARTIFACT_HASHES` there), so re-pinning the seal
         * still costs two independent edits — see the KDoc on [PARENT_ARTIFACT_SHA256].
         *
         * Call this FIRST in every test whose failure mode is "a sealed parent file drifted".
         * [ĐO] 2026-08-23: restoring the two files E6a fixed, then re-drifting them, turns exactly
         * five tests red — `LegacyBaselineIdentityTest` x2, `ExpansionTransportFenceTest` x2,
         * `ExpansionTraceabilityTest` x1. Until this guard reached all five, three of them reported
         * only opaque digests ("expected <5b49a5ea...> but was <2329ccc6...>", "array contents
         * differ at index [29]") and that opacity cost three separate investigations.
         */
        fun assertSealedParentFilesOnDisk(root: Path, sealed: Map<String, String>) {
            assertEquals(sealed.keys.toList(), LegacyBaselineIdentity.PARENT_PATHS)
            val drifted = LegacyBaselineIdentity.PARENT_PATHS.mapNotNull { relative ->
                val path = root.resolve(relative).normalize()
                val actual = if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                        .joinToString("") { "%02x".format(it) }
                } else {
                    "<missing file>"
                }
                val expected = sealed.getValue(relative)
                if (actual == expected) null else "  $relative\n    sealed = $expected\n    on disk = $actual"
            }
            assertTrue(
                drifted.isEmpty(),
                "SEALED PARENT FILE(S) DRIFTED — restore the bytes, do NOT re-pin the constants:\n" +
                    drifted.joinToString("\n") +
                    "\n  Fix: git checkout <commit-before-the-edit> -- <path listed above>" +
                    "\n  These files are byte-sealed (docs/README.md marks them); they take no header, " +
                    "no status line, no back-filled section.",
            )
        }
    }

    private val root: Path get() = Path.of(System.getProperty("clusternav.root")).toAbsolutePath().normalize()

    @Test
    fun `exact 13 parent files reproduce trusted full-byte digest without mutation`() {
        assertSealedParentFilesOnDisk(root, PARENT_ARTIFACT_SHA256)
        assertEquals(PARENT_BASELINE_SHA256, LegacyBaselineIdentity.PARENT_BASELINE_SHA256)
        assertEquals(13, LegacyBaselineIdentity.PARENT_PATHS.size)
        assertEquals(LegacyBaselineIdentity.PARENT_PATHS.sorted(), LegacyBaselineIdentity.PARENT_PATHS)
        val before = LegacyBaselineIdentity.PARENT_PATHS.associateWith { Files.readAllBytes(root.resolve(it)) }

        val baseline = LegacyBaselineIdentity.capture(root)
        val second = LegacyBaselineIdentity.capture(root)

        assertEquals(PARENT_BASELINE_SHA256, baseline.parentCombinedSha256)
        assertEquals(PARENT_BASELINE_SHA256, LegacyBaselineIdentity.parentCombinedSha256(root))
        assertEquals(LegacyBaselineIdentity.PARENT_PATHS, baseline.artifacts.map { it.path })
        assertEquals(13, baseline.artifacts.map { it.fullSha256 }.distinct().size)
        assertEquals(baseline.canonicalJson(), second.canonicalJson())
        assertFalse(baseline.canonicalJson().contains('\n'))
        assertEquals(64, baseline.selfSha256.length)
        assertEquals(64, baseline.finalizedFileSha256().length)
        before.forEach { (relative, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(root.resolve(relative)), relative) }
    }

    @Test
    fun `framing uses u32be path length path UTF-8 and raw inner digest`() {
        assertSealedParentFilesOnDisk(root, PARENT_ARTIFACT_SHA256)
        val artifacts = LegacyBaselineIdentity.PARENT_PATHS.map { relative ->
            LegacyBaselineArtifact(relative, ExpansionHashing.sha256(Files.readAllBytes(root.resolve(relative))))
        }
        val independent = MessageDigest.getInstance("SHA-256")
        artifacts.forEach { artifact ->
            val path = artifact.path.encodeToByteArray()
            independent.update(byteArrayOf(
                (path.size ushr 24).toByte(), (path.size ushr 16).toByte(),
                (path.size ushr 8).toByte(), path.size.toByte(),
            ))
            independent.update(path)
            independent.update(artifact.fullSha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        }
        val expected = independent.digest().joinToString("") { "%02x".format(it) }

        assertEquals(PARENT_BASELINE_SHA256, expected)
        assertEquals(expected, LegacyBaselineIdentity.combinedSha256(artifacts))
    }

    @Test
    fun `canonical renderer normalizes NFC sorts ASCII keys escapes controls and rejects lone surrogate`() {
        val value = CanonicalJson.obj(
            "z" to JsonInteger(1),
            "a" to JsonText("e\u0301\n\u0001"),
        )
        val rendered = CanonicalJson.render(value)

        assertEquals("{\"a\":\"é\\n\\u0001\",\"z\":1}", rendered)
        assertEquals(rendered.encodeToByteArray().toList(), CanonicalJson.bytes(value).toList())
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ExpansionHashing.sha256Utf8("abc"),
        )
        assertThrows(IllegalArgumentException::class.java) { CanonicalJson.render(JsonText("\uD800")) }
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalJson.render(JsonObject(listOf("a" to JsonNull, "a" to JsonNull)))
        }
    }

    @Test
    fun `candidate state and candidate plus registry hashes are derived and append-only`() {
        val readProof = completeReadProof()
        assertEquals(CandidateState.DISCOVERED, readProof.copy(selectorId = null, evidenceIds = emptyList()).deriveState(CandidateMode.READ_ONLY))
        assertEquals(CandidateState.SOURCE_BACKED, readProof.copy(selectorId = null).deriveState(CandidateMode.READ_ONLY))
        assertEquals(CandidateState.SOURCE_BACKED, readProof.copy(configId = null).deriveState(CandidateMode.READ_ONLY))
        assertEquals(CandidateState.READ_ONLY_READY, readProof.deriveState(CandidateMode.READ_ONLY))

        val mutationProof = completeMutationProof()
        assertEquals(CandidateState.MUTATION_REVIEW, mutationProof.copy(clearOperationId = null).deriveState(CandidateMode.MUTATION))
        assertEquals(CandidateState.MUTATION_REVIEW, mutationProof.copy(priorReadOperationId = null).deriveState(CandidateMode.MUTATION))
        assertEquals(CandidateState.READY_FOR_FIELD, mutationProof.deriveState(CandidateMode.MUTATION))
        assertEquals(
            CandidateState.REJECTED,
            mutationProof.copy(absoluteRejects = listOf(AbsoluteReject.MASS_MUTATION)).deriveState(CandidateMode.MUTATION),
        )
        val fieldReady = candidate(
            "CAND-S-011-NAV-GATE@1", mutationProof, null, CandidateMode.MUTATION,
            listOf(RestoreScope.CURRENT_PROPERTY), listOf("REASON-INVALIDATED-SOURCE"),
        )
        assertEquals(CandidateState.READY_FOR_FIELD, fieldReady.state)
        assertThrows(IllegalArgumentException::class.java) {
            candidate(
                "CAND-S-012-NAV-GATE@1", mutationProof, null, CandidateMode.MUTATION,
                emptyList(), listOf("REASON-INVALIDATED-SOURCE"),
            )
        }

        val first = candidate("CAND-H-008-NAV-GATE@1", readProof, null)
        val second = candidate(
            "CAND-H-008-NAV-GATE@2",
            readProof.copy(absoluteRejects = listOf(AbsoluteReject.WEAK_EVIDENCE_ONLY)),
            first.revisionSha256,
        )
        assertEquals(CandidateState.READ_ONLY_READY, first.state)
        assertEquals(CandidateState.REJECTED, second.state)
        assertNotEquals(first.revisionSha256, second.revisionSha256)
        assertEquals(first.revisionSha256, CandidateRevision.create(first.input).revisionSha256)

        val revision1 = RegistryRevision.create(1, null, listOf(first))
        val revision2 = RegistryRevision.create(2, revision1.registryRevisionSha256, listOf(first, second))
        RegistryHistory.validate(listOf(revision1, revision2))
        val registry = CandidateRegistryRoot.create(
            "1".repeat(64), "2".repeat(64), "3".repeat(64),
            listOf(first, second), listOf(revision1, revision2),
        )
        val repeated = CandidateRegistryRoot.create(
            "1".repeat(64), "2".repeat(64), "3".repeat(64),
            listOf(first, second), listOf(revision1, revision2),
        )
        assertEquals(2, registry.revision)
        assertEquals(registry.selfSha256, repeated.selfSha256)
        assertEquals(registry.canonicalJson(), repeated.canonicalJson())
        assertFalse(registry.canonicalJson().contains('\n'))
    }

    @Test
    fun `schema is valid recursive closed 2020-12 metadata authority`() {
        val schemaPath = root.resolve("offcar-planner/src/main/resources/expansion-contracts.schema.json")
        val schemaBytes = Files.readAllBytes(schemaPath)
        val schemaText = schemaBytes.decodeToString()
        val parsed = MiniJsonReader(schemaText).read() as Map<*, *>

        assertEquals("https://json-schema.org/draft/2020-12/schema", parsed["\$schema"])
        assertArrayEquals(schemaText.encodeToByteArray(), schemaBytes)
        assertFalse(schemaBytes.take(3) == listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
        assertNotEquals('\n'.code.toByte(), schemaBytes.last())
        assertFalse(schemaText.contains('\n'))
        assertFalse(schemaText.contains("\"anyOf\""))
        assertFalse(schemaText.contains("\"additionalProperties\":true"))
        assertFalse(schemaText.contains("\"type\":["))
        val defs = parsed["\$defs"] as Map<*, *>
        val roots = listOf(
            "LegacyBaselineRoot", "CorpusCoverageRoot", "EvidenceMapRoot", "CandidateRegistryRoot",
            "CandidateDiffRoot", "VehicleSessionPlanRoot", "ResultLedgerSchemaRoot", "TraceabilityRoot",
            "PackManifestRoot", "RenderEnvelope",
        )
        roots.forEach { assertTrue(defs.containsKey(it), it) }
        listOf(
            "CandidateRevision", "PromotionProof", "PromotionProofClaim", "ReadOnlyReadyPromotionProof",
            "ReadyForFieldPromotionProof", "RegistryRevision", "ExactIdentity", "IdentityRequirement",
            "SessionPackTemplate", "SessionPackFreeze", "SessionRowTemplate", "SessionRow", "LedgerEvent",
            "CorpusCoverage", "CorpusHit", "HitDisposition", "ProposedTombstone",
        ).forEach { assertTrue(defs.containsKey(it), it) }
        assertEquals(PARENT_BASELINE_SHA256, property(defs, "LegacyBaselineRoot", "parentCombinedSha256")["const"])
        assertEquals(13L, property(defs, "LegacyBaselineRoot", "artifacts")["minItems"])
        assertEquals(11L, property(defs, "PackManifestRoot", "entries")["maxItems"])
        assertTrue((defs["nullableSha256"] as Map<*, *>).containsKey("oneOf"))
        assertTrue((defs["CandidateRevision"] as Map<*, *>).containsKey("allOf"))
        assertEquals(3, ((defs["SessionRow"] as Map<*, *>)["oneOf"] as List<*>).size)
        assertEquals(3, ((defs["SessionRowTemplate"] as Map<*, *>)["oneOf"] as List<*>).size)
        val milestoneTemplate = ((defs["SessionRowTemplate"] as Map<*, *>)["oneOf"] as List<*>)[2] as Map<*, *>
        assertEquals("^RESULT-D-(H0|M[1-4])-[0-9]{4}$", field(milestoneTemplate, "resultIdentityId")["pattern"])
        assertThrows(IllegalArgumentException::class.java) {
            completeMutationProof().copy(mutationOperationId = "OP-READ-WRONG-KIND")
        }

        var objectSchemas = 0
        inspect(parsed) { node ->
            val keys = node.keys.map { it as String }
            assertEquals(keys.sorted(), keys, "schema object keys must be ASCII-sorted")
            if (node.containsKey("pattern")) {
                assertEquals("string", node["type"], "pattern schema must be a string")
                val minimum = node["minLength"] as? Long
                val maximum = node["maxLength"] as? Long
                assertTrue(minimum != null && maximum != null && minimum in 0..maximum, "pattern must have explicit lengths")
            }
            if (node["type"] == "object") {
                objectSchemas++
                assertEquals(false, node["additionalProperties"], "open object schema: ${node.keys}")
                assertTrue(node["required"] is List<*>, "object must require every declared property")
                val properties = (node["properties"] as Map<*, *>).keys
                assertEquals(properties.toSet(), (node["required"] as List<*>).toSet())
            }
        }
        assertTrue(objectSchemas >= 35, "expected recursive closed object coverage, found $objectSchemas")
        listOf("vin", "serialNumber", "gps", "ipAddress", "rawDump", "sourceLine", "decompiledBody", "blob", "freeText")
            .forEach { assertFalse(schemaText.contains("\"$it\"", ignoreCase = true), it) }
    }

    @Test
    fun `schema closes proof claims coverage ready states and verifier commands`() {
        val schema = MiniJsonReader(Files.readString(root.resolve(
            "offcar-planner/src/main/resources/expansion-contracts.schema.json",
        ))).read() as Map<*, *>
        val defs = schema["\$defs"] as Map<*, *>
        val validator = SchemaSubset(schema)

        val proofFields = ((defs["PromotionProof"] as Map<*, *>)["properties"] as Map<*, *>).keys
        val claim = defs["PromotionProofClaim"] as Map<*, *>
        val claimFields = (claim["properties"] as Map<*, *>).keys
        assertEquals(proofFields - "evidenceIds", claimFields)
        assertEquals(claimFields, (claim["required"] as List<*>).toSet())
        assertEquals(false, claim["additionalProperties"])
        val hit = defs["CorpusHit"] as Map<*, *>
        val hitFields = hit["properties"] as Map<*, *>
        assertEquals("#/\$defs/nullablePromotionProofClaim", (hitFields["promotionProofClaim"] as Map<*, *>)["\$ref"])
        assertTrue("promotionProofClaim" in hit["required"] as List<*>)
        assertEquals(true, (hitFields["selectorIds"] as Map<*, *>)["uniqueItems"])
        assertEquals(true, (hitFields["consumerIds"] as Map<*, *>)["uniqueItems"])
        val hitRules = hit["allOf"] as List<*>
        assertEquals(3, hitRules.size)
        val claimBranches = ((hitRules[0] as Map<*, *>)["oneOf"] as List<*>).map { it as Map<*, *> }
        assertEquals("CANDIDATE_DERIVATION", field(field(claimBranches[0], "disposition"), "kind")["const"])
        assertEquals("#/\$defs/PromotionProofClaim", field(claimBranches[0], "promotionProofClaim")["\$ref"])
        assertEquals("null", field(claimBranches[1], "promotionProofClaim")["type"])
        listOf(1 to "selectorIds", 2 to "consumerIds").forEach { (ruleIndex, ids) ->
            val branches = ((hitRules[ruleIndex] as Map<*, *>)["oneOf"] as List<*>).map { it as Map<*, *> }
            assertEquals(1L, field(branches[0], ids)["minItems"])
            assertEquals(1L, field(branches[0], ids)["maxItems"])
            assertEquals(0L, field(branches[1], ids)["maxItems"])
        }

        val coverage = defs["CorpusCoverage"] as Map<*, *>
        val availability = coverage["oneOf"] as List<*>
        assertEquals(listOf("AVAILABLE", "AVAILABLE", "UNAVAILABLE", "UNSEARCHED", "BUDGET_STOPPED", "ACQUIRED_UNREVIEWED"),
            availability.map { field(it as Map<*, *>, "status")["const"] })
        val coverageUnion = mapOf("oneOf" to availability)
        val zero = mapOf("artifactSha256" to "a".repeat(64), "hits" to emptyList<Any>(), "status" to "AVAILABLE",
            "zeroHit" to true, "zeroHitOutcome" to "NO_MATCH")
        assertTrue(validator.accepts(coverageUnion, zero))
        assertFalse(validator.accepts(coverageUnion, zero + ("zeroHit" to false)))
        val entries = field(defs["CorpusCoverageRoot"] as Map<*, *>, "entries")
        assertEquals(12L, entries["minItems"]); assertEquals(12L, entries["maxItems"])
        assertEquals((1..12).map { "C%02d".format(it) }, (entries["allOf"] as List<*>).map {
            val contains = (it as Map<*, *>)["contains"] as Map<*, *>
            field(contains, "corpusId")["const"]
        })

        val read = MiniJsonReader(candidate("CAND-H-099-SCHEMA@1", completeReadProof(), null).canonicalJson()).read() as Map<*, *>
        assertTrue(validator.accepts("CandidateRevision", read))
        assertFalse(validator.accepts("CandidateRevision", replaced(read, "proof",
            replaced(read["proof"] as Map<*, *>, "configId", null))))
        val mutation = candidate("CAND-S-099-SCHEMA@1", completeMutationProof(), null, CandidateMode.MUTATION,
            listOf(RestoreScope.CURRENT_PROPERTY), listOf("REASON-INVALIDATED-SOURCE"))
        val mutationJson = MiniJsonReader(mutation.canonicalJson()).read() as Map<*, *>
        assertTrue(validator.accepts("CandidateRevision", mutationJson))
        assertFalse(validator.accepts("CandidateRevision", replaced(mutationJson, "proof",
            replaced(mutationJson["proof"] as Map<*, *>, "clearOperationId", null))))
        assertFalse(validator.accepts("CandidateRevision", replaced(mutationJson, "restoreScope", emptyList<Any>())))

        val rejected = ((defs["ProposalDisposition"] as Map<*, *>)["oneOf"] as List<*>)[1] as Map<*, *>
        val proposalReason = field(rejected, "reasonId")
        assertEquals("^REASON-PROPOSAL-[A-Z0-9][A-Z0-9-]{0,54}$", proposalReason["pattern"])
        assertTrue(validator.accepts(proposalReason, "REASON-PROPOSAL-${"A".repeat(55)}"))
        assertFalse(validator.accepts(proposalReason, "REASON-PROPOSAL-${"A".repeat(56)}"))
        val candidateId = defs["candidateRevisionId"] as Map<*, *>
        assertEquals(14L, candidateId["minLength"]); assertEquals(93L, candidateId["maxLength"])
        assertTrue(validator.accepts("candidateRevisionId", "CAND-H-000-A@1"))
        assertTrue(validator.accepts("candidateRevisionId", "CAND-PROVIDER-999-${"A".repeat(64)}@2147483647"))
        assertFalse(validator.accepts("candidateRevisionId", "CAND-H-000-A@2147483648"))
        mapOf("probeId" to 75L, "operationId" to 75L, "inverseOperationId" to 75L,
            "queryId" to 72L, "observationId" to 73L, "resultIdentityId" to 16L, "hitId" to 150L)
            .forEach { (name, maximum) -> assertEquals(maximum, (defs[name] as Map<*, *>)["maxLength"], name) }
        assertEquals("^(FACT-[A-Z0-9][A-Z0-9-]{0,63}|H[0-9]{1,3}|S[0-9]{1,3})$",
            (defs["evidenceId"] as Map<*, *>)["pattern"])
        val commandItems = field(defs["TraceabilityLink"] as Map<*, *>, "commands")["items"] as Map<*, *>
        val verifier = "scripts/verify-hud-sign-candidate-expansion.sh"
        assertTrue(validator.accepts(commandItems, verifier))
        ((1..10) + 12).forEach { assertTrue(validator.accepts(commandItems,
            "CLUSTERNAV_EXPANSION_GATE=GATE-X-O$it $verifier")) }
        assertFalse(validator.accepts(commandItems, "CLUSTERNAV_EXPANSION_GATE=GATE-X-O11 $verifier"))
        listOf("$verifier --gate O1", "./gradlew test", "$verifier; echo unsafe",
            "CLUSTERNAV_EXPANSION_GATE=GATE-X-O1 $verifier\n")
            .forEach { assertFalse(validator.accepts(commandItems, it)) }
    }

    private fun completeReadProof() = PromotionProof(
        absoluteRejects = emptyList(), selectorId = "SEL-NAV-GATE", readProbeId = "PROBE-READ-NAV-GATE",
        mutationOperationId = null, configId = "CONFIG-NAV-GATE", access = ConfigAccess.READ_ONLY,
        javaType = JavaType.INT, providerId = "PROVIDER-INSTRUMENT", permissionId = "PERMISSION-VENDOR-CAR",
        transportId = "TRANSPORT-PROPERTY", boundedDomainValueIds = listOf("VALUE-OFF", "VALUE-ON"),
        priorReadOperationId = null, readBackOperationId = null, clearPolicy = ClearPolicy.NOT_APPLICABLE,
        clearOperationId = null, inverseOperationIds = emptyList(), consumerId = "CONSUMER-HUD-NAV",
        ownership = Ownership.APP_OWNED, risk = 10, evidenceIds = listOf("FACT-NAV-GATE"),
    )

    private fun completeMutationProof() = PromotionProof(
        absoluteRejects = emptyList(), selectorId = "SEL-NAV-GATE", readProbeId = null,
        mutationOperationId = "OP-MUTATE-NAV-GATE", configId = "CONFIG-NAV-GATE", access = ConfigAccess.READ_WRITE,
        javaType = JavaType.INT, providerId = "PROVIDER-INSTRUMENT", permissionId = "PERMISSION-VENDOR-CAR",
        transportId = "TRANSPORT-PROPERTY", boundedDomainValueIds = listOf("VALUE-OFF", "VALUE-ON"),
        priorReadOperationId = "OP-READ-NAV-PRIOR", readBackOperationId = "OP-READ-NAV-BACK",
        clearPolicy = ClearPolicy.REQUIRED, clearOperationId = "OP-CLEAR-NAV-GATE",
        inverseOperationIds = listOf("OP-INVERSE-NAV-GATE", "OP-RESTORE-NAV-GATE"),
        consumerId = "CONSUMER-HUD-NAV", ownership = Ownership.APP_OWNED, risk = 20,
        evidenceIds = listOf("FACT-NAV-GATE"),
    )

    private fun candidate(
        id: String, proof: PromotionProof, predecessor: String?, mode: CandidateMode = CandidateMode.READ_ONLY,
        restoreScope: List<RestoreScope> = emptyList(), invalidationTriggers: List<String> = emptyList(),
    ) = CandidateRevision.create(
        CandidateRevisionInput(
            candidateRevisionId = id, milestone = Milestone.M1, mode = mode, proof = proof,
            hypothesisId = "HYP-NAV-GATE", mutationDimension = "DIMENSION-NAV-GATE",
            requiredSurfaces = listOf(RequiredSurface.HUD_NAV_MAP),
            requiredObservationIds = listOf("OBS-M1-NAV-GATE"), restoreScope = restoreScope,
            invalidationTriggers = invalidationTriggers,
            planningProfile = PlanningProfile(10, 80, 70, 90, 10, 20, 1_000),
            dependsOnRevisionIds = emptyList(), pruneGroup = null, subsumes = emptyList(),
            invalidatesOn = listOf("REASON-INVALIDATED-SOURCE"), predecessorCandidateSha256 = predecessor,
        ),
    )

    @Suppress("UNCHECKED_CAST")
    private fun field(schema: Map<*, *>, name: String): Map<String, Any?> =
        ((schema["properties"] as Map<*, *>)[name] as Map<String, Any?>)

    private fun property(defs: Map<*, *>, definition: String, name: String): Map<String, Any?> =
        field(defs[definition] as Map<*, *>, name)

    private fun replaced(source: Map<*, *>, name: String, value: Any?): Map<String, Any?> =
        source.entries.associate { (key, old) -> key as String to if (key == name) value else old }

    private fun inspect(value: Any?, visitor: (Map<*, *>) -> Unit) {
        when (value) {
            is Map<*, *> -> { visitor(value); value.values.forEach { inspect(it, visitor) } }
            is List<*> -> value.forEach { inspect(it, visitor) }
        }
    }
}

private class SchemaSubset(root: Map<*, *>) {
    private val defs = root["\$defs"] as Map<*, *>

    fun accepts(definition: String, value: Any?): Boolean = accepts(defs[definition] as Map<*, *>, value)

    @Suppress("UNCHECKED_CAST")
    fun accepts(schema: Map<*, *>, value: Any?): Boolean {
        val ref = schema["\$ref"] as? String
        if (ref != null && !accepts(defs[ref.removePrefix("#/\$defs/")] as Map<*, *>, value)) return false
        val all = schema["allOf"] as? List<Map<*, *>>
        if (all != null && !all.all { accepts(it, value) }) return false
        val one = schema["oneOf"] as? List<Map<*, *>>
        if (one != null && one.count { accepts(it, value) } != 1) return false
        val not = schema["not"] as? Map<*, *>
        if (not != null && accepts(not, value)) return false
        if (schema.containsKey("const") && schema["const"] != value) return false
        val enum = schema["enum"] as? List<*>
        if (enum != null && value !in enum) return false
        if (!typeMatches(schema["type"] as? String, value)) return false
        when (value) {
            is Map<*, *> -> {
                val required = schema["required"] as? List<*> ?: emptyList<Any?>()
                if (required.any { !value.containsKey(it) }) return false
                val properties = schema["properties"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
                if (properties.any { (key, child) -> value.containsKey(key) &&
                        !accepts(child as Map<*, *>, value[key]) }) return false
                if (schema["additionalProperties"] == false && value.keys.any { it !in properties }) return false
            }
            is List<*> -> {
                if (!within(value.size, schema["minItems"], schema["maxItems"])) return false
                if (schema["uniqueItems"] == true && value.distinct().size != value.size) return false
                (schema["items"] as? Map<*, *>)?.let { if (!value.all { item -> accepts(it, item) }) return false }
                (schema["prefixItems"] as? List<Map<*, *>>)?.forEachIndexed { index, item ->
                    if (index < value.size && !accepts(item, value[index])) return false
                }
                (schema["contains"] as? Map<*, *>)?.let { contains ->
                    val count = value.count { accepts(contains, it) }
                    if (!within(count, schema["minContains"] ?: 1L, schema["maxContains"])) return false
                }
            }
            is String -> {
                if (!within(value.length, schema["minLength"], schema["maxLength"])) return false
                val pattern = schema["pattern"] as? String
                if (pattern != null && !Regex(pattern).containsMatchIn(value)) return false
            }
            is Number -> {
                val number = value.toLong()
                if ((schema["minimum"] as? Number)?.toLong()?.let { number < it } == true) return false
                if ((schema["maximum"] as? Number)?.toLong()?.let { number > it } == true) return false
            }
        }
        return true
    }

    private fun within(value: Int, minimum: Any?, maximum: Any?): Boolean =
        (minimum as? Number)?.toInt()?.let { value >= it } != false &&
            (maximum as? Number)?.toInt()?.let { value <= it } != false

    private fun typeMatches(type: String?, value: Any?): Boolean = type == null || when (type) {
        "array" -> value is List<*>; "boolean" -> value is Boolean; "integer" -> value is Number
        "null" -> value == null; "object" -> value is Map<*, *>; "string" -> value is String; else -> false
    }
}

private class MiniJsonReader(private val source: String) {
    private var index = 0

    fun read(): Any? {
        val value = value()
        require(index == source.length) { "trailing JSON at $index" }
        return value
    }

    private fun value(): Any? = when (source.getOrNull(index)) {
        '{' -> objectValue()
        '[' -> arrayValue()
        '"' -> stringValue()
        't' -> literal("true", true)
        'f' -> literal("false", false)
        'n' -> literal("null", null)
        '-', in '0'..'9' -> numberValue()
        else -> error("invalid JSON value at $index")
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
                    else -> { require(char.code >= 0x20) { "unescaped control" }; append(char) }
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
        if (take('-')) Unit
        require(source.getOrNull(index) in '0'..'9')
        if (source[index] == '0') index++ else while (source.getOrNull(index) in '0'..'9') index++
        require(source.getOrNull(index) !in listOf('.', 'e', 'E')) { "non-integer number" }
        return source.substring(start, index).toLong()
    }

    private fun <T> literal(text: String, result: T): T {
        require(source.startsWith(text, index))
        index += text.length
        return result
    }

    private fun expect(char: Char) { require(take(char)) { "expected $char at $index" } }
    private fun take(char: Char): Boolean = if (source.getOrNull(index) == char) { index++; true } else false
}
