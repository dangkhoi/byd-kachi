package com.byd.clusternav.offcar

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DerivationClosureTest {
    @Test
    fun `fixed probe catalog covers read and list for config service and package metadata`() {
        assertEquals(6, DiscoveryProbeCatalog.all.size)
        assertEquals(DiscoveryProbeCatalog.all.map(DiscoveryProbe::id).sorted(), DiscoveryProbeCatalog.all.map(DiscoveryProbe::id))
        assertEquals(DiscoveryProbeCatalog.all.size, DiscoveryProbeCatalog.byId.size)

        DiscoveryMetadataTarget.entries.forEach { target ->
            val targetProbes = DiscoveryProbeCatalog.all.filter { it.target == target }
            assertEquals(DiscoveryOperation.entries.toSet(), targetProbes.map(DiscoveryProbe::operation).toSet(), target.name)
            assertEquals(2, targetProbes.size, target.name)
        }
        DiscoveryProbeCatalog.all.forEach { probe ->
            assertEquals(probe, DiscoveryProbeCatalog.requireById(probe.id))
            assertTrue(Regex(ExpansionIds.PROBE_PATTERN).matches(probe.id), probe.id)
            assertTrue(probe.id.startsWith("PROBE-${probe.operation.name}-"), probe.id)
        }
    }

    @Test
    fun `probe requests are closed read-list types and unknown strings never become selectors`() {
        DiscoveryProbeCatalog.all.forEach { probe ->
            val request = DiscoveryProbeCatalog.request(probe.id)
            when (probe.operation) {
                DiscoveryOperation.READ -> assertTrue(request is DiscoveryRequest.Read, probe.id)
                DiscoveryOperation.LIST -> assertTrue(request is DiscoveryRequest.ListMetadata, probe.id)
            }
            assertEquals(probe, request.probe)
        }

        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryProbeCatalog.request("PROBE-READ-ARBITRARY-VEHICLE-STRING")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryProbeCatalog.request("SEL-H8-PROPERTY-CONFIG-METADATA")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryRequest.Read(DiscoveryProbe.LIST_PACKAGE_METADATA)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryRequest.ListMetadata(DiscoveryProbe.READ_PACKAGE_METADATA)
        }
        listOf(DiscoveryRequest.Read::class.java, DiscoveryRequest.ListMetadata::class.java).forEach { type ->
            assertTrue(type.declaredFields.none { it.name.contains("selector", ignoreCase = true) }, type.name)
        }
    }

    @Test
    fun `source-backed fixtures append H8 and S11 without raw IDs or guessed enum values`() {
        val fixtures = SourceBackedExpansionCatalog
        val h8Keys = listOf(fixtures.h8Discovered, fixtures.h8SourceBacked, fixtures.h8ReadOnlyReady)
            .map { ExpansionIds.candidate(it.candidateRevisionId) }
        val s11Keys = listOf(fixtures.s11MutationReview)
            .map { ExpansionIds.candidate(it.candidateRevisionId) }

        assertTrue(h8Keys.all { it.family == CandidateFamily.H && it.number == 8 })
        assertEquals(listOf(1, 2, 3), h8Keys.map(CandidateRevisionKey::revision))
        assertTrue(s11Keys.all { it.family == CandidateFamily.S && it.number == 11 })
        assertEquals(listOf(1), s11Keys.map(CandidateRevisionKey::revision))
        assertEquals(listOf("FACT-PARENT-H7"), fixtures.h8SourceBacked.input.proof.evidenceIds)
        assertEquals(listOf("FACT-PARENT-H7"), fixtures.h8ReadOnlyReady.input.proof.evidenceIds)
        assertEquals(listOf("FACT-PARENT-S6"), fixtures.s11MutationReview.input.proof.evidenceIds)
        val minimum = "CAND-H-000-A@1"
        val maximum = "CAND-PROVIDER-999-${"A".repeat(64)}@2147483647"
        assertEquals(14, minimum.length); assertEquals(93, maximum.length)
        assertEquals(1, ExpansionIds.candidate(minimum).revision)
        assertEquals(Int.MAX_VALUE, ExpansionIds.candidate(maximum).revision)
        listOf("CAND-H-000-A@0", "CAND-H-000-A@2147483648", "CAND-H-000-A@01").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { ExpansionIds.candidate(id) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CandidateRevision.create(fixtures.h8Discovered.input, declaredRevision = 2)
        }
        listOf("H0", "H999", "S0", "S999").forEach(ExpansionIds::evidence)
        listOf("H1000", "S1000").forEach { assertThrows(IllegalArgumentException::class.java) { ExpansionIds.evidence(it) } }
        ExpansionIds.probe("PROBE-READ-${"A".repeat(64)}")
        assertThrows(IllegalArgumentException::class.java) { ExpansionIds.probe("PROBE-READ-${"A".repeat(65)}") }
        ExpansionIds.proposalReason("REASON-PROPOSAL-${"A".repeat(55)}")
        assertThrows(IllegalArgumentException::class.java) { ExpansionIds.proposalReason("REASON-PROPOSAL-${"A".repeat(56)}") }
        assertTrue(fixtures.publishedCandidates.none { candidate ->
            candidate.input.proof.absoluteRejects.any {
                it == AbsoluteReject.FREE_FORM_SELECTOR || it == AbsoluteReject.GUESSED_ENUM
            }
        })
        fixtures.publishedCandidates.mapNotNull { it.input.proof.selectorId }.forEach {
            assertTrue(Regex(ExpansionIds.SELECTOR_PATTERN).matches(it), it)
        }
    }

    @Test
    fun `every reviewed derivation has exactly one typed disposition and every state is exercised once`() {
        val closure = SourceBackedExpansionCatalog.closure
        assertEquals(ReviewedDerivationId.entries, closure.reviewedIds)
        assertEquals(ReviewedDerivationId.entries.size, closure.dispositions.size)
        assertEquals(ReviewedDerivationId.entries.toSet(), closure.byReviewId.keys)

        val candidateDispositions = closure.dispositions
            .filterIsInstance<ReviewedDerivationDisposition.CandidateDerivation>()
        val productionStates = CandidateState.entries.toSet() - CandidateState.READY_FOR_FIELD
        assertEquals(productionStates, candidateDispositions.map { it.candidate.state }.toSet())
        productionStates.forEach { state ->
            assertEquals(1, candidateDispositions.count { it.candidate.state == state }, state.name)
        }
        assertEquals(0, candidateDispositions.count { it.candidate.state == CandidateState.READY_FOR_FIELD })
        assertTrue(closure.byReviewId.getValue(ReviewedDerivationId.H8_DUPLICATE) is ReviewedDerivationDisposition.DuplicateOf)
        assertTrue(closure.byReviewId.getValue(ReviewedDerivationId.S11_OUT_OF_SCOPE) is ReviewedDerivationDisposition.OutOfScope)
    }

    @Test
    fun `closure rejects missing duplicate and noncanonical dispositions`() {
        val reviewed = ReviewedDerivationId.entries.toList()
        val dispositions = SourceBackedExpansionCatalog.reviewedDispositions

        assertThrows(IllegalArgumentException::class.java) {
            DerivationClosure.close(reviewed, dispositions.dropLast(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DerivationClosure.close(reviewed, dispositions + dispositions.first())
        }
        val selfDuplicate = dispositions.map {
            if (it.reviewId == ReviewedDerivationId.H8_DUPLICATE) {
                ReviewedDerivationDisposition.DuplicateOf(
                    ReviewedDerivationId.H8_DUPLICATE,
                    ReviewedDerivationId.H8_DUPLICATE,
                )
            } else {
                it
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            DerivationClosure.close(reviewed, selfDuplicate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DerivationClosure.close(reviewed.reversed(), dispositions)
        }
    }

    @Test
    fun `promotion proof inverse operations are unique domain constrained and preserve declared order`() {
        val declared = listOf("OP-RESTORE-S11-PRIOR", "OP-INVERSE-S11-CLEAR")
        val proof = SourceBackedExpansionCatalog.s11MutationReviewProof.copy(inverseOperationIds = declared)
        val rendered = CanonicalJson.render(proof.json())

        assertEquals(declared, proof.inverseOperationIds)
        assertTrue(rendered.indexOf(declared[0]) < rendered.indexOf(declared[1]))
        assertThrows(IllegalArgumentException::class.java) {
            proof.copy(inverseOperationIds = listOf(declared[0], declared[0]))
        }
        assertThrows(IllegalArgumentException::class.java) {
            proof.copy(inverseOperationIds = listOf("OP-CLEAR-S11-NOT-INVERSE"))
        }
    }

    @Test
    fun `frozen eligibility and quarantine expose defensive immutable snapshots`() {
        val candidates = SourceBackedExpansionCatalog.publishedCandidates.toMutableList()
        val probes = DiscoveryProbeCatalog.all.toMutableList()
        val frozen = FrozenEligibility.freeze(candidates, probes)
        val candidateSnapshot = frozen.allowedCandidateRevisionIds.toList()
        val probeSnapshot = frozen.allowedProbeIds.toList()

        candidates.clear()
        probes.clear()
        assertEquals(candidateSnapshot, frozen.allowedCandidateRevisionIds)
        assertEquals(probeSnapshot, frozen.allowedProbeIds)

        @Suppress("UNCHECKED_CAST")
        fun assertImmutable(values: List<*>) {
            assertTrue(values.javaClass.name.startsWith("java.util."), values.javaClass.name)
            assertThrows(UnsupportedOperationException::class.java) {
                (values as MutableList<Any?>).clear()
            }
        }
        listOf(
            frozen.allowedProbeIds, frozen.readOnlyCandidateRevisionIds,
            frozen.mutationCandidateRevisionIds, frozen.allowedCandidateRevisionIds,
        ).forEach(::assertImmutable)

        val evidence = mutableListOf("H8", "S11")
        val initial = SameSessionQuarantine.start(frozen)
        val quarantine = initial.record(DiscoveryProbe.READ_PROPERTY_CONFIG, evidence)
        evidence.clear()

        assertTrue(initial.records.isEmpty())
        assertEquals(listOf("H8", "S11"), quarantine.records.single().evidenceIds)
        assertImmutable(initial.records)
        assertImmutable(quarantine.records)
        assertImmutable(quarantine.records.single().evidenceIds)
        assertEquals(candidateSnapshot, frozen.allowedCandidateRevisionIds)
        assertTrue(frozen.isCandidateEligible(SourceBackedExpansionCatalog.h8ReadOnlyReady.candidateRevisionId))
    }


    @Test
    fun `candidate registry and trust anchor collections remain immutable after hashing`() {
        val inverse = mutableListOf("OP-INVERSE-S11-RESTORE-PRIOR", "OP-RESTORE-S11-SECONDARY")
        val candidate = CandidateRevision.create(ExpansionTestFixtures.s11MutationReview.input.copy(
            candidateRevisionId = "CAND-S-011-SOURCE-DOMAIN@2",
            proof = ExpansionTestFixtures.s11ReadyProof.copy(inverseOperationIds = inverse),
            predecessorCandidateSha256 = ExpansionTestFixtures.s11MutationReview.revisionSha256,
        ))
        val candidateJson = candidate.canonicalJson()
        val candidateHash = candidate.revisionSha256
        inverse.clear()
        assertEquals(candidateJson, candidate.canonicalJson())
        assertEquals(candidateHash, candidate.revisionSha256)

        val candidates = SourceBackedExpansionCatalog.publishedCandidates.toMutableList()
        val history = SourceBackedExpansionCatalog.publishedRegistryHistory.toMutableList()
        val registry = CandidateRegistryRoot.create("1".repeat(64), "2".repeat(64), "3".repeat(64), candidates, history)
        val registryJson = registry.canonicalJson()
        candidates.clear(); history.clear()
        assertEquals(registryJson, registry.canonicalJson())

        @Suppress("UNCHECKED_CAST")
        fun assertImmutable(values: List<*>) {
            assertThrows(UnsupportedOperationException::class.java) { (values as MutableList<Any?>).clear() }
        }
        listOf(
            candidate.input.proof.inverseOperationIds, candidate.input.requiredObservationIds,
            registry.candidates, registry.history, registry.history.last().candidateRefs,
            SourceBackedExpansionCatalog.publishedCandidates,
            SourceBackedExpansionCatalog.publishedRegistryHistory,
            SourceBackedExpansionCatalog.reviewedDispositions,
            DiscoveryProbeCatalog.all,
            LegacyBaselineIdentity.PARENT_PATHS,
        ).forEach(::assertImmutable)
        @Suppress("UNCHECKED_CAST")
        fun assertImmutableMap(values: Map<*, *>) {
            assertThrows(UnsupportedOperationException::class.java) { (values as MutableMap<Any?, Any?>).clear() }
        }
        assertImmutableMap(DiscoveryProbeCatalog.byId)
        assertImmutableMap(SourceBackedExpansionCatalog.closure.byReviewId)
    }
    @Test
    fun `legacy baseline rejects an existing ancestor symlink even when every input resolves`(@TempDir temp: Path) {
        val root = temp.resolve("root")
        LegacyBaselineIdentity.PARENT_PATHS.forEach { relative ->
            val input = root.resolve(relative)
            Files.createDirectories(input.parent)
            Files.writeString(input, relative)
        }
        val ancestor = root.resolve("docs/diagnostics/hud-sign-re")
        val external = temp.resolve("linked-hud-sign-re")
        Files.move(ancestor, external)
        Files.createSymbolicLink(ancestor, external)

        assertTrue(LegacyBaselineIdentity.PARENT_PATHS.all { Files.isRegularFile(root.resolve(it)) })
        assertThrows(IllegalArgumentException::class.java) { LegacyBaselineIdentity.capture(root) }
    }

    @Test
    fun `published frozen eligibility contains only the latest source-backed read-only revision`() {
        val fixtures = SourceBackedExpansionCatalog
        val frozen = FrozenEligibility.freeze(fixtures.publishedCandidates)

        assertEquals(listOf(fixtures.h8ReadOnlyReady.candidateRevisionId), frozen.readOnlyCandidateRevisionIds)
        assertEquals(emptyList<String>(), frozen.mutationCandidateRevisionIds)
        assertEquals(listOf(fixtures.h8ReadOnlyReady.candidateRevisionId), frozen.allowedCandidateRevisionIds)
        listOf(fixtures.h8Discovered, fixtures.h8SourceBacked, fixtures.s11MutationReview, fixtures.s12Rejected)
            .forEach { assertFalse(frozen.isCandidateEligible(it.candidateRevisionId), it.candidateRevisionId) }
        frozen.allowedProbeIds.forEach { assertEquals(it, DiscoveryProbeCatalog.requireById(it).id) }
        assertEquals(frozen.allowedProbeIds.sorted(), frozen.allowedProbeIds)
    }
}
