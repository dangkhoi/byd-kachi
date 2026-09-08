package com.byd.clusternav.offcar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal object ExpansionTestFixtures {
    private val source = SourceBackedExpansionCatalog
    val h8Discovered = source.h8Discovered
    val h8SourceBacked = source.h8SourceBacked
    val h8ReadOnlyReady = source.h8ReadOnlyReady
    val h8ReadOnlyProof = source.h8ReadOnlyProof
    val s11MutationReview = source.s11MutationReview
    val s11MutationReviewProof = source.s11MutationReviewProof
    val s12Rejected = source.s12Rejected
    val publishedCandidates = source.publishedCandidates
    val publishedRegistryHistory = source.publishedRegistryHistory
    val s11ReadyProof = s11MutationReviewProof.copy(
        boundedDomainValueIds = listOf("VALUE-S11-SOURCE-A", "VALUE-S11-SOURCE-B"),
        priorReadOperationId = "OP-READ-S11-PRIOR", readBackOperationId = "OP-READ-S11-BACK",
        clearPolicy = ClearPolicy.REQUIRED, clearOperationId = "OP-CLEAR-S11-SOURCE-DOMAIN",
        inverseOperationIds = listOf("OP-INVERSE-S11-RESTORE-PRIOR"),
    )
    val s11ReadyForField = CandidateRevision.create(s11MutationReview.input.copy(
        candidateRevisionId = "CAND-S-011-SOURCE-DOMAIN@2", proof = s11ReadyProof,
        predecessorCandidateSha256 = s11MutationReview.revisionSha256,
    ))
    val currentCandidates = (publishedCandidates + s11ReadyForField)
        .sortedWith(compareBy(ExpansionIds.candidateComparator) { it.candidateRevisionId })
        .also(CandidateRevisionChains::validate)
    val registryHistory = (publishedRegistryHistory + RegistryRevision.create(
        5, publishedRegistryHistory.last().registryRevisionSha256, currentCandidates,
    )).also(RegistryHistory::validate)
    val frozenEligibility = FrozenEligibility.freeze(currentCandidates)
    val s11ProposedTombstoneCandidate = CandidateRevision.create(s11ReadyForField.input.copy(
        candidateRevisionId = "CAND-S-011-SOURCE-DOMAIN@3",
        proof = s11ReadyProof.copy(absoluteRejects = listOf(AbsoluteReject.WEAK_EVIDENCE_ONLY)),
        predecessorCandidateSha256 = s11ReadyForField.revisionSha256,
    ))
    val proposedTombstone = ProposedTombstone.create(
        s11ReadyForField, s11ProposedTombstoneCandidate, "REASON-INVALIDATED-SOURCE-CONTRACT",
        currentCandidates, frozenEligibility,
    )
}

internal enum class FixtureTerminal { PASS, FAIL, SKIPPED, BLOCKED_DEPENDENCY }
internal data class InvalidationFixture(val proposal: ProposedTombstone, val decision: ProposalDecision, val rejectionReasonId: String? = null)

internal object SemanticLedgerFixtures {
    fun sourceModel(budgetMs: Long = 3_600_000) = SessionTemplateGenerator.generate(
        ExpansionTestFixtures.currentCandidates, ExpansionTestFixtures.registryHistory, budgetMs = budgetMs,
    )

    fun complete(
        candidates: List<CandidateRevision> = ExpansionTestFixtures.currentCandidates,
        model: SessionTemplateModel = sourceModel(),
        terminals: Map<String, FixtureTerminal> = emptyMap(),
        prunedByRow: Map<String, String> = emptyMap(),
        invalidation: InvalidationFixture? = null,
    ): LedgerFixture {
        val registry = "5".repeat(64); val pack = "6".repeat(64)
        val candidateSet = LedgerSemanticValidator.candidateSetSha256(candidates, model.allowedCandidateRevisionIds)
        val identity = ExactIdentity(
            "1".repeat(64), "2".repeat(64), "3".repeat(64), "4".repeat(64), registry, pack,
            candidateSet, IdentityVariant.VEHICLE_TEST, IdentityComponent.COMPONENT_PROBE_RECEIVER,
            IdentityPermission.PERMISSION_VENDOR_CAR, IdentityProfile.PROFILE_SEAL_T10,
        )
        val template = SessionPackTemplateFixture(model, "7".repeat(64), "8".repeat(64), "9".repeat(64), registry, pack, identity)
        val nonce = "a".repeat(64); val start = 1_000L
        val sessionId = LedgerSemanticValidator.checkedSessionId(pack, nonce, start)
        var freeze = SessionPackFreeze(
            sessionId, "0".repeat(64), template.templateFileSha256, identity, model.revision,
            model.allowedProbeIds, model.allowedCandidateRevisionIds, model.allowedMutationCandidateRevisionIds,
            start, model.budgetMs, OverflowSafeBudgetScheduler.checkedDeadline(start, model.budgetMs),
        )
        freeze = freeze.copy(sessionInstanceSha256 = LedgerSemanticValidator.checkedSessionInstanceSha256(freeze))
        val rows = model.rows.map { SessionRow.from(it, identity) }
        val events = mutableListOf<LedgerEvent>(); val terminalHashes = mutableMapOf<String, String>()
        var invalidationDiscoveryHash: String? = null
        var truncated = false
        fun append(draft: LedgerEvent): LedgerEvent {
            val sequence = events.size + 1
            val sealed = draft.copy(
                sequence = sequence, eventId = "EVENT-${sequence.toString().padStart(6, '0')}",
                previousEventHash = events.lastOrNull()?.eventSha256, eventSha256 = "0".repeat(64),
            ).seal(events.lastOrNull()?.eventSha256)
            events += sealed
            return sealed
        }
        fun event(type: LedgerEventType, row: SessionRow? = null, offset: Long = events.size.toLong(), block: LedgerEvent.() -> LedgerEvent = { this }): LedgerEvent {
            val base = LedgerEvent(type, sessionId, events.size + 1, identity, offset, rowId = row?.rowId, rowKind = row?.kind)
            return append(base.block())
        }
        event(LedgerEventType.SESSION_START, offset = 0)
        rows.forEachIndexed { index, row ->
            prunedByRow[row.rowId]?.let { source ->
                val terminal = event(LedgerEventType.PRUNED, row) { copy(
                    candidateRevisionId = row.candidateRevisionId, reasonId = "REASON-PRUNED-SUBSUMED",
                    ruleId = "RULE-PRUNE-FORWARD-FOREST", causalEventHash = terminalHashes.getValue(source),
                ) }
                terminalHashes[row.rowId] = terminal.eventSha256
                return@forEachIndexed
            }
            when (terminals[row.rowId]) {
                FixtureTerminal.SKIPPED -> {
                    val terminal = event(LedgerEventType.SKIPPED, row) { copy(
                        candidateRevisionId = row.candidateRevisionId, reasonId = "REASON-SKIPPED-TIME-BUDGET",
                        ruleId = if (truncated) "RULE-BUDGET-AFTER-FIRST-NONFIT" else "RULE-BUDGET-FIRST-NONFIT",
                        causalEventHash = events.last().eventSha256,
                    ) }
                    truncated = true
                    terminalHashes[row.rowId] = terminal.eventSha256
                    return@forEachIndexed
                }
                FixtureTerminal.BLOCKED_DEPENDENCY -> {
                    val failed = row.dependsOnRowIds.first { terminalHashes.containsKey(it) }
                    val terminal = event(LedgerEventType.BLOCKED, row) { copy(
                        reasonId = "REASON-BLOCKED-DEPENDENCY", ruleId = "RULE-BLOCKED-DEPENDENCY",
                        causalEventHash = terminalHashes.getValue(failed),
                    ) }
                    terminalHashes[row.rowId] = terminal.eventSha256
                    return@forEachIndexed
                }
                else -> Unit
            }
            val pre = event(LedgerEventType.PRECONDITION, row) { copy(
                probeId = row.probeIds.singleOrNull(), preconditionOutcome = LedgerPhaseOutcome.PASS,
            ) }
            val requested = terminals[row.rowId] ?: FixtureTerminal.PASS
            val terminal = when (row.kind) {
                SessionRowKind.READ_ONLY -> {
                    val evidence = mutableListOf(LedgerEvidenceRef("FACT-DISCOVERY-${(index + 1).toString().padStart(4, '0')}"))
                    if (invalidation != null && invalidationDiscoveryHash == null) {
                        evidence += LedgerEvidenceRef(
                            "FACT-INVALIDATION-REVIEW", targetCandidateRevisionId = invalidation.proposal.targetCandidateRevisionId,
                            suggestedInvalidationReasonId = invalidation.proposal.invalidationReasonId,
                        )
                    }
                    val discovery = event(LedgerEventType.DISCOVERY_ONLY, row) { copy(
                        probeId = row.probeIds.single(), discoveryEvidenceRefs = evidence, causalEventHash = pre.eventSha256,
                    ) }
                    if (evidence.size > 1) invalidationDiscoveryHash = discovery.eventSha256
                    normalTerminal(event = requested, row = row, append = ::append, events = events, identity = identity, sessionId = sessionId)
                }
                SessionRowKind.MILESTONE -> {
                    event(LedgerEventType.OBSERVATION, row) { copy(resultIdentityId = row.resultIdentityId, observations = row.observations) }
                    normalTerminal(event = requested, row = row, append = ::append, events = events, identity = identity, sessionId = sessionId)
                }
                SessionRowKind.MUTATION -> {
                    event(LedgerEventType.MUTATION, row) { copy(
                        candidateRevisionId = row.candidateRevisionId, mutationOperationId = row.mutationOperationId,
                        mutationOutcome = if (requested == FixtureTerminal.FAIL) LedgerPhaseOutcome.FAIL else LedgerPhaseOutcome.PASS,
                    ) }
                    event(LedgerEventType.OBSERVATION, row) { copy(candidateRevisionId = row.candidateRevisionId, observations = row.observations) }
                    val clearRefs = listOf(LedgerEvidenceRef("FACT-CLEAR-${index.toString().padStart(4, '0')}", row.clearOperationId))
                    event(LedgerEventType.CLEAR, row) { copy(candidateRevisionId = row.candidateRevisionId, clearEvidenceRefs = clearRefs, clearOutcome = LedgerPhaseOutcome.PASS) }
                    val restoreRefs = row.inverseOperationIds.mapIndexed { n, operation -> LedgerEvidenceRef("FACT-RESTORE-${index.toString().padStart(4, '0')}-$n", operation) }
                    event(LedgerEventType.RESTORE, row) { copy(candidateRevisionId = row.candidateRevisionId, restoreEvidenceRefs = restoreRefs, restoreOutcome = LedgerPhaseOutcome.PASS) }
                    normalTerminal(requested, row, ::append, events, identity, sessionId, clearRefs, restoreRefs)
                }
            }
            terminalHashes[row.rowId] = terminal.eventSha256
        }
        val dispositions = mutableListOf<ProposalDispositionRecord>(); val next = candidates.toMutableList()
        invalidation?.let { review ->
            val proposal = review.proposal
            val invalidated = event(LedgerEventType.INVALIDATED) { copy(
                candidateRevisionId = proposal.targetCandidateRevisionId,
                tombstoneRevisionId = proposal.candidate.candidateRevisionId,
                proposedTombstone = proposal, invalidationReasonId = proposal.invalidationReasonId,
                causalEventHash = requireNotNull(invalidationDiscoveryHash),
            ) }
            dispositions += ProposalDispositionRecord(
                invalidated.eventSha256, proposal.candidate.revisionSha256, review.decision,
                if (review.decision == ProposalDecision.ACCEPTED) null else review.rejectionReasonId ?: "REASON-PROPOSAL-REJECTED",
            )
            if (review.decision == ProposalDecision.ACCEPTED) next += proposal.candidate
        }
        return LedgerFixture(
            template, freeze, rows, events, candidates, nonce, dispositions.sortedBy { it.proposalEventSha256 },
            next.sortedWith(compareBy(ExpansionIds.candidateComparator) { it.candidateRevisionId }),
        )
    }

    private fun normalTerminal(
        event: FixtureTerminal, row: SessionRow, append: (LedgerEvent) -> LedgerEvent, events: List<LedgerEvent>,
        identity: ExactIdentity, sessionId: String, clear: List<LedgerEvidenceRef> = emptyList(),
        restore: List<LedgerEvidenceRef> = emptyList(),
    ): LedgerEvent {
        val type = if (event == FixtureTerminal.FAIL) LedgerEventType.FAIL else LedgerEventType.PASS
        return append(LedgerEvent(
            type, sessionId, events.size + 1, identity, events.size.toLong(), rowId = row.rowId, rowKind = row.kind,
            candidateRevisionId = row.candidateRevisionId, resultIdentityId = row.resultIdentityId,
            observations = if (row.kind == SessionRowKind.READ_ONLY) emptyList() else row.observations,
            clearEvidenceRefs = clear, restoreEvidenceRefs = restore, result = LedgerResult.valueOf(type.name),
            reasonId = if (type == LedgerEventType.FAIL) "REASON-FIXTURE-FAIL" else null,
        ))
    }

    fun rechain(fixture: LedgerFixture, events: List<LedgerEvent>): LedgerFixture {
        val hashes = mutableMapOf<String, String>(); var previous: String? = null
        val sealed = events.mapIndexed { index, original ->
            val draft = original.copy(
                sequence = index + 1, eventId = "EVENT-${(index + 1).toString().padStart(6, '0')}",
                previousEventHash = previous, causalEventHash = original.causalEventHash?.let { hashes[it] ?: it },
                eventSha256 = "0".repeat(64),
            ).seal(previous)
            hashes[original.eventSha256] = draft.eventSha256; previous = draft.eventSha256; draft
        }
        val dispositions = fixture.proposalDispositions.map { it.copy(proposalEventSha256 = hashes[it.proposalEventSha256] ?: it.proposalEventSha256) }.sortedBy { it.proposalEventSha256 }
        return fixture.copy(events = sealed, proposalDispositions = dispositions)
    }
}

class ExpansionPromotionTest {
    @Test
    fun `promotion derivation is total and absolute rejects win`() {
        data class Case(val mode: CandidateMode, val proof: PromotionProof, val expected: CandidateState)
        val f = ExpansionTestFixtures
        val cases = listOf(
            Case(CandidateMode.READ_ONLY, f.h8Discovered.input.proof, CandidateState.DISCOVERED),
            Case(CandidateMode.MUTATION, f.h8SourceBacked.input.proof, CandidateState.SOURCE_BACKED),
            Case(CandidateMode.READ_ONLY, f.h8ReadOnlyProof, CandidateState.READ_ONLY_READY),
            Case(CandidateMode.MUTATION, f.h8ReadOnlyProof, CandidateState.MUTATION_REVIEW),
            Case(CandidateMode.MUTATION, f.s11ReadyProof, CandidateState.READY_FOR_FIELD),
            Case(CandidateMode.READ_ONLY, f.s12Rejected.input.proof, CandidateState.REJECTED),
        )
        cases.forEach { assertEquals(it.expected, CandidatePromotion.review(it.mode, it.proof).state) }
        assertEquals(CandidateState.entries.toSet(), cases.map { it.expected }.toSet())
        AbsoluteReject.entries.forEach { reject ->
            val decision = CandidatePromotion.review(CandidateMode.MUTATION, f.s11ReadyProof.copy(absoluteRejects = listOf(reject)))
            assertEquals(CandidateState.REJECTED, decision.state); assertFalse(decision.frozenEligible)
        }
    }

    @Test
    fun `mutation proof requires write prior read-back clear and inverse independently`() {
        val p = ExpansionTestFixtures.s11ReadyProof
        val cases = listOf(
            p.copy(mutationOperationId = null) to PromotionRequirement.MUTATION_OPERATION,
            p.copy(priorReadOperationId = null) to PromotionRequirement.PRIOR_READ,
            p.copy(readBackOperationId = null) to PromotionRequirement.READ_BACK,
            p.copy(clearPolicy = ClearPolicy.NOT_APPLICABLE) to PromotionRequirement.CLEAR_REQUIRED,
            p.copy(clearOperationId = null) to PromotionRequirement.CLEAR_OPERATION,
            p.copy(inverseOperationIds = emptyList()) to PromotionRequirement.INVERSE_OPERATION,
            p.copy(access = ConfigAccess.READ_ONLY) to PromotionRequirement.WRITE_ACCESS,
        )
        cases.forEach { (proof, missing) ->
            val decision = CandidatePromotion.review(CandidateMode.MUTATION, proof)
            assertEquals(CandidateState.MUTATION_REVIEW, decision.state); assertTrue(missing in decision.unmetRequirements)
        }
    }

    @Test
    fun `row inverse operations are kind restricted and unique`() {
        val row = SemanticLedgerFixtures.sourceModel().rows.single { it.kind == SessionRowKind.MUTATION }
        assertThrows(IllegalArgumentException::class.java) {
            row.copy(inverseOperationIds = listOf("OP-READ-NOT-AN-INVERSE"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            row.copy(inverseOperationIds = listOf("OP-INVERSE-DUPLICATE", "OP-INVERSE-DUPLICATE"))
        }
    }

    @Test
    fun `rollback stack captures then arms and retains every mutation and clear outcome`() {
        val row = SemanticLedgerFixtures.sourceModel().rows.single { it.kind == SessionRowKind.MUTATION }
        assertThrows(IllegalArgumentException::class.java) { RollbackStack.empty().mutation(MutationAttemptOutcome.PASS) }
        MutationAttemptOutcome.entries.forEach { outcome ->
            val armed = RollbackStack.empty().capture(row).arm()
            val attempted = armed.mutation(outcome)
            assertEquals(1, attempted.frames.size); assertEquals(outcome, attempted.frames.single().mutationOutcome)
            val cleared = attempted.clear(); assertEquals(RollbackFrameState.CLEARED, cleared.frames.single().state)
            assertEquals(1, cleared.frames.size)
            assertThrows(IllegalArgumentException::class.java) { cleared.clear() }
        }
    }

    @Test
    fun `restore is top-only pass pops fail retains and armed stack rejects next normal mutation`() {
        val row = SemanticLedgerFixtures.sourceModel().rows.single { it.kind == SessionRowKind.MUTATION }
        val attempted = RollbackStack.empty().capture(row).arm().mutation(MutationAttemptOutcome.PARTIAL)
        assertThrows(IllegalArgumentException::class.java) { attempted.capture(row) }
        assertThrows(IllegalArgumentException::class.java) { attempted.restore(row.rollbackStackIndex!! + 1, RestoreAttemptOutcome.PASS) }
        val failed = attempted.restore(row.rollbackStackIndex!!, RestoreAttemptOutcome.FAIL)
        assertEquals(RollbackFrameState.RESTORE_FAILED, failed.frames.single().state)
        assertTrue(failed.restore(row.rollbackStackIndex, RestoreAttemptOutcome.PASS).frames.isEmpty())
    }

    @Test
    fun `complete synthetic fixture validates and candidate evidence equals its derivation union`() {
        val snapshot = LedgerSemanticValidator.validate(SemanticLedgerFixtures.complete())
        assertEquals(snapshot.rows.size, snapshot.passRecords.size)
        assertEquals(snapshot.freeze.allowedCandidateRevisionIds, snapshot.rows.mapNotNull { it.candidateRevisionId }.sortedWith(ExpansionIds.candidateComparator))
        assertTrue(snapshot.events.all { it.exactIdentity == snapshot.freeze.exactIdentity })
        val root = java.nio.file.Path.of(System.getProperty("clusternav.root"))
        val coverage = CoverageMetadata.parse(java.nio.file.Files.readAllBytes(root.resolve(
            "docs/diagnostics/hud-sign-re/expansion/corpus-coverage.json",
        )))
        coverage.validateRegistry(SourceBackedExpansionCatalog.publishedCandidates)
        assertTrue("FACT-PARENT-S5" in coverage.factHits)
        val extra = SourceBackedExpansionCatalog.publishedCandidates.map { candidate ->
            if (candidate.candidateRevisionId == SourceBackedExpansionCatalog.s11MutationReview.candidateRevisionId)
                CandidateRevision.create(candidate.input.copy(proof = candidate.input.proof.copy(
                    evidenceIds = listOf("FACT-PARENT-S5", "FACT-PARENT-S6"),
                ))) else candidate
        }
        assertThrows(IllegalArgumentException::class.java) { coverage.validateRegistry(extra) }
    }
}
