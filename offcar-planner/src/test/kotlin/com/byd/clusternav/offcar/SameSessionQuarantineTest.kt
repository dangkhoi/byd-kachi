package com.byd.clusternav.offcar

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SameSessionQuarantineTest {
    @Test
    fun `same-session discoveries remain DISCOVERY_ONLY and cannot mutate frozen eligibility`() {
        val frozen = ExpansionTestFixtures.frozenEligibility
        val candidateSnapshot = frozen.allowedCandidateRevisionIds.toList()
        var quarantine = SameSessionQuarantine.start(frozen)
        DiscoveryProbeCatalog.all.forEachIndexed { index, probe ->
            quarantine = quarantine.record(probe, listOf(if (index % 2 == 0) "H8" else "S11"))
            assertSame(frozen, quarantine.frozenEligibility)
        }
        assertTrue(quarantine.records.all { it.eventType == LedgerEventType.DISCOVERY_ONLY })
        assertEquals(candidateSnapshot, quarantine.frozenEligibility.allowedCandidateRevisionIds)

        val futureProof = ExpansionTestFixtures.h8ReadOnlyProof.copy(
            selectorId = "SEL-H9-PACKAGE-METADATA", readProbeId = DiscoveryProbe.READ_PACKAGE_METADATA.id,
            configId = "CONFIG-H9-PACKAGE-METADATA", boundedDomainValueIds = listOf("VALUE-H9-METADATA-AVAILABLE"),
            evidenceIds = listOf("H9"),
        )
        val future = CandidateRevision.create(ExpansionTestFixtures.h8ReadOnlyReady.input.copy(
            candidateRevisionId = "CAND-H-009-PACKAGE-METADATA@1", proof = futureProof,
            hypothesisId = "HYP-H9-PACKAGE-METADATA", mutationDimension = "DIMENSION-H9-PACKAGE-METADATA",
            requiredObservationIds = listOf("OBS-M1-H9-PACKAGE-METADATA"), predecessorCandidateSha256 = null,
        ))
        assertFalse(quarantine.frozenEligibility.isCandidateEligible(future.candidateRevisionId))
        val next = FrozenEligibility.freeze(
            (ExpansionTestFixtures.currentCandidates + future).sortedWith(compareBy(ExpansionIds.candidateComparator) { it.candidateRevisionId }),
        )
        assertTrue(next.isCandidateEligible(future.candidateRevisionId))
    }

    @Test
    fun `only frozen probes and canonical sorted evidence can be quarantined`() {
        val freeze = FrozenEligibility.freeze(ExpansionTestFixtures.currentCandidates, listOf(DiscoveryProbe.READ_PROPERTY_CONFIG))
        val quarantine = SameSessionQuarantine.start(freeze)
        assertEquals(LedgerEventType.DISCOVERY_ONLY, quarantine.record(DiscoveryProbe.READ_PROPERTY_CONFIG, listOf("H8", "S11")).records.single().eventType)
        assertThrows(IllegalArgumentException::class.java) { quarantine.record(DiscoveryProbe.LIST_SERVICE_METADATA, listOf("H8")) }
        assertThrows(IllegalArgumentException::class.java) { quarantine.record(DiscoveryProbe.READ_PROPERTY_CONFIG, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { quarantine.record(DiscoveryProbe.READ_PROPERTY_CONFIG, listOf("S11", "H8")) }
        assertThrows(IllegalArgumentException::class.java) { quarantine.record(DiscoveryProbe.READ_PROPERTY_CONFIG, listOf("H8", "H8")) }
    }

    @Test
    fun `ledger schema remains the exact 26 branch closed matrix`() {
        val root = Path.of(System.getProperty("clusternav.root")).toAbsolutePath().normalize()
        val schema = X4Json.asObject(X4Json.parse(Files.readAllBytes(root.resolve(
            "offcar-planner/src/main/resources/expansion-contracts.schema.json",
        ))))
        val definitions = X4Json.asObject(schema.getValue("\$defs"))
        val inverseOperation = X4Json.asObject(definitions.getValue("inverseOperationId"))
        assertEquals("^OP-(INVERSE|RESTORE)-[A-Z0-9][A-Z0-9-]{0,63}$", inverseOperation["pattern"])
        listOf("SessionRowTemplate", "SessionRow").forEach { definition ->
            val properties = X4Json.asObject(X4Json.asObject(definitions.getValue(definition)).getValue("properties"))
            val inverseIds = X4Json.asObject(properties.getValue("inverseOperationIds"))
            assertEquals(true, inverseIds["uniqueItems"])
            assertEquals("#/\$defs/inverseOperationId", X4Json.asObject(inverseIds.getValue("items"))["\$ref"])
        }
        val ledger = X4Json.asObject(definitions.getValue("LedgerEvent"))
        val branches = X4Json.array(ledger.getValue("oneOf")).map(X4Json::asObject)
        assertEquals(LedgerSemanticValidator.BRANCH_COUNT, branches.size)
        assertEquals(
            linkedMapOf(
                "SESSION_START" to 1, "DISCOVERY_ONLY" to 1, "PRECONDITION" to 3,
                "MUTATION" to 1, "OBSERVATION" to 2, "CLEAR" to 1, "RESTORE" to 1,
                "PASS" to 3, "FAIL" to 3, "INCONCLUSIVE" to 3, "SKIPPED" to 1,
                "PRUNED" to 1, "BLOCKED" to 4, "INVALIDATED" to 1,
            ),
            branches.groupingBy { branch ->
                val properties = X4Json.asObject(branch.getValue("properties"))
                X4Json.string(X4Json.asObject(properties.getValue("eventType")).getValue("const"))
            }.eachCount(),
        )
        val declared = X4Json.asObject(ledger.getValue("properties")).keys
        branches.forEach { branch ->
            val required = X4Json.strings(branch.getValue("required")).toSet()
            assertEquals(X4Json.asObject(branch.getValue("properties")).keys, required)
            assertTrue(declared.containsAll(required + setOf("eventId", "sessionId", "exactIdentity", "elapsedOffsetMs", "eventSha256")))
        }
    }
}

class LedgerSemanticValidationTest {
    @Test
    fun `complete fixture validates every event hash chain row automaton and terminal`() {
        val snapshot = LedgerSemanticValidator.validate(SemanticLedgerFixtures.complete())
        assertEquals((1..snapshot.events.size).toList(), snapshot.events.map { it.sequence })
        assertEquals(snapshot.events.dropLast(1).map { it.eventSha256 }, snapshot.events.drop(1).map { it.previousEventHash })
        assertEquals(snapshot.rows.map { it.rowId }, snapshot.events.drop(1).filter {
            it.eventType in setOf(LedgerEventType.PASS, LedgerEventType.FAIL, LedgerEventType.INCONCLUSIVE, LedgerEventType.SKIPPED, LedgerEventType.PRUNED, LedgerEventType.BLOCKED)
        }.mapNotNull { it.rowId })
        snapshot.events.forEach { assertTrue(LedgerSemanticValidator.branchIndex(it) in 1..26) }
    }

    @Test
    fun `validator rejects wrong branch shape hash and deep identity`() {
        val fixture = SemanticLedgerFixtures.complete()
        val passIndex = fixture.events.indexOfFirst { it.eventType == LedgerEventType.PASS && it.rowKind == SessionRowKind.READ_ONLY }
        val wrongShape = fixture.events.toMutableList().also {
            it[passIndex] = it[passIndex].copy(mutationOperationId = "OP-MUTATE-FORBIDDEN-SHAPE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(fixture, wrongShape))
        }

        val badHash = fixture.copy(events = fixture.events.toMutableList().also { it[2] = it[2].copy(eventSha256 = "f".repeat(64)) })
        assertThrows(IllegalArgumentException::class.java) { LedgerSemanticValidator.validate(badHash) }

        val foreignIdentity = fixture.freeze.exactIdentity.copy(diffFileSha256 = "b".repeat(64))
        val identityEvents = fixture.events.toMutableList().also { it[2] = it[2].copy(exactIdentity = foreignIdentity) }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(fixture, identityEvents))
        }
    }

    @Test
    fun `template freeze formulas and runtime projection are checked not caller asserted`() {
        val fixture = SemanticLedgerFixtures.complete()
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(fixture.copy(freeze = fixture.freeze.copy(deadlineElapsedMs = fixture.freeze.deadlineElapsedMs + 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(fixture.copy(freeze = fixture.freeze.copy(sessionId = "SESSION-0000000000000000")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(fixture.copy(freeze = fixture.freeze.copy(sessionInstanceSha256 = "c".repeat(64))))
        }
        val rows = fixture.rows.toMutableList().also { rows -> rows[0] = rows[0].copy(score = rows[0].score + 1) }
        assertThrows(IllegalArgumentException::class.java) { LedgerSemanticValidator.validate(fixture.copy(rows = rows)) }
    }

    @Test
    fun `candidate result probe operation and evidence foreign keys are exact`() {
        val fixture = SemanticLedgerFixtures.complete()
        val mutation = fixture.events.indexOfFirst { it.eventType == LedgerEventType.MUTATION }
        val milestone = fixture.events.indexOfFirst { it.eventType == LedgerEventType.PASS && it.rowKind == SessionRowKind.MILESTONE }
        val readPre = fixture.events.indexOfFirst { it.eventType == LedgerEventType.PRECONDITION && it.rowKind == SessionRowKind.READ_ONLY }
        val clear = fixture.events.indexOfFirst { it.eventType == LedgerEventType.CLEAR }
        val discovery = fixture.events.indexOfFirst { it.eventType == LedgerEventType.DISCOVERY_ONLY }
        val alternatives = listOf(
            fixture.events.toMutableList().also { it[mutation] = it[mutation].copy(candidateRevisionId = ExpansionTestFixtures.h8ReadOnlyReady.candidateRevisionId) },
            fixture.events.toMutableList().also { it[milestone] = it[milestone].copy(resultIdentityId = "RESULT-D-M4-9999") },
            fixture.events.toMutableList().also { it[readPre] = it[readPre].copy(probeId = DiscoveryProbe.LIST_SERVICE_METADATA.id) },
            fixture.events.toMutableList().also { it[mutation] = it[mutation].copy(mutationOperationId = "OP-MUTATE-WRONG-OPERATION") },
            fixture.events.toMutableList().also { events -> events[clear] = events[clear].copy(clearEvidenceRefs = events[clear].clearEvidenceRefs.map { it.copy(operationId = "OP-CLEAR-WRONG-EVIDENCE") }) },
            fixture.events.toMutableList().also { events -> events[discovery] = events[discovery].copy(discoveryEvidenceRefs = events[discovery].discoveryEvidenceRefs.mapIndexed { n, ref -> if (n == 0) ref.copy(targetCandidateRevisionId = "CAND-H-999-UNKNOWN@1", suggestedInvalidationReasonId = "REASON-INVALIDATED-UNKNOWN") else ref }) },
        )
        alternatives.forEach { changed ->
            assertThrows(IllegalArgumentException::class.java) {
                LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(fixture, changed))
            }
        }
    }

    @Test
    fun `row order contiguity offsets causality and all terminals are mandatory`() {
        val fixture = SemanticLedgerFixtures.complete()
        val first = fixture.rows[0].rowId; val second = fixture.rows[1].rowId
        val firstGroup = fixture.events.filter { it.rowId == first }; val secondGroup = fixture.events.filter { it.rowId == second }
        val tail = fixture.events.filter { it.rowId != first && it.rowId != second && it.eventType != LedgerEventType.SESSION_START }
        val reordered = listOf(fixture.events.first()) + secondGroup + firstGroup + tail
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(fixture, reordered))
        }

        val discovery = fixture.events.indexOfFirst { it.eventType == LedgerEventType.DISCOVERY_ONLY }
        val wrongCausal = fixture.events.toMutableList().also {
            it[discovery] = it[discovery].copy(causalEventHash = fixture.events.first().eventSha256)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(fixture, wrongCausal))
        }

        val decreasing = fixture.events.toMutableList().also { it[3] = it[3].copy(elapsedOffsetMs = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(fixture, decreasing))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(fixture, fixture.events.dropLast(1)))
        }
    }

    @Test
    fun `invalidation is a sorted review tail causally linked to discovery and disposition`() {
        val proposal = ExpansionTestFixtures.proposedTombstone
        val accepted = SemanticLedgerFixtures.complete(
            invalidation = InvalidationFixture(proposal, ProposalDecision.ACCEPTED),
        )
        val acceptedSnapshot = LedgerSemanticValidator.validate(accepted)
        assertEquals(LedgerEventType.INVALIDATED, acceptedSnapshot.events.last().eventType)
        assertTrue(accepted.nextRegistryCandidates.any { it.candidateRevisionId == proposal.candidate.candidateRevisionId })

        val rejected = SemanticLedgerFixtures.complete(
            invalidation = InvalidationFixture(proposal, ProposalDecision.REJECTED, "REASON-PROPOSAL-INSUFFICIENT-EVIDENCE"),
        )
        LedgerSemanticValidator.validate(rejected)
        assertFalse(rejected.nextRegistryCandidates.any { it.candidateRevisionId == proposal.candidate.candidateRevisionId })

        val invalidIndex = accepted.events.lastIndex
        val wrongCause = accepted.events.toMutableList().also {
            it[invalidIndex] = it[invalidIndex].copy(causalEventHash = accepted.events.first().eventSha256)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(accepted, wrongCause))
        }
        val wrongDisposition = accepted.copy(proposalDispositions = accepted.proposalDispositions.map {
            it.copy(proposedTombstoneRevisionSha256 = "f".repeat(64))
        })
        assertThrows(IllegalArgumentException::class.java) { LedgerSemanticValidator.validate(wrongDisposition) }
        val earlyReview = accepted.events.dropLast(1).toMutableList().also { it.add(2, accepted.events.last()) }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(accepted, earlyReview))
        }
    }
}
