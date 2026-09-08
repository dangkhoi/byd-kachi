package com.byd.clusternav.offcar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptivePruningTest {
    @Test
    fun `score ordering deadline and fit arithmetic remain total and overflow safe`() {
        val profile = ExpansionTestFixtures.s11ReadyForField.input.planningProfile
        val expected = 90L * 100 + 80L * 40 + 100L * 30 - 10L * 50 - 1L * 10 - 10L * 25
        assertEquals(expected, profile.score)
        assertEquals(expected, checkedScore(90, 80, 100, 10, 1_000, 10))
        val model = SemanticLedgerFixtures.sourceModel()
        assertEquals(model.rows.sortedWith(SessionRowOrdering.comparator), model.rows)
        val read = model.rows.single { it.candidateRevisionId == ExpansionTestFixtures.h8ReadOnlyReady.candidateRevisionId }
        val nullCandidate = read.copy(rowId = "ROW-9000-NULL-CANDIDATE", candidateRevisionId = null, requiredSurfaces = emptyList())
        assertTrue(SessionRowOrdering.comparator.compare(nullCandidate, read) < 0)
        assertEquals(1_000, OverflowSafeBudgetScheduler.checkedDeadline(750, 250))
        assertTrue(OverflowSafeBudgetScheduler.fits(750, 1_000, 250))
        assertFalse(OverflowSafeBudgetScheduler.fits(751, 1_000, 250))
        assertEquals(0, OverflowSafeBudgetScheduler.elapsedOffset(10, 20))
        assertThrows(ArithmeticException::class.java) {
            OverflowSafeBudgetScheduler.checkedDeadline(Long.MAX_VALUE - 10, 20)
        }
    }

    @Test
    fun `scheduler accepts only a validated snapshot and preserves truncation causal hashes`() {
        val snapshot = LedgerSemanticValidator.validate(SemanticLedgerFixtures.complete())
        val samples = List(snapshot.rows.size) { snapshot.freeze.budgetMs }
        val decisions = OverflowSafeBudgetScheduler.schedule(snapshot, samples)
        assertEquals(SchedulerDisposition.SKIPPED_FIRST_NONFIT, decisions.first().disposition)
        assertTrue(decisions.drop(1).all { it.disposition == SchedulerDisposition.SKIPPED_AFTER_NONFIT })
        assertTrue(decisions.all { it.causalEventHash != null })
        assertEquals(samples, decisions.mapNotNull { it.elapsedOffsetMs })
        assertThrows(IllegalStateException::class.java) {
            OverflowSafeBudgetScheduler.schedule(snapshot, samples.dropLast(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverflowSafeBudgetScheduler.schedule(snapshot, samples.dropLast(1) + -1L)
        }
    }


    @Test
    fun `validated ledger cannot resume after first budget nonfit and uses exact truncation rules`() {
        val model = SemanticLedgerFixtures.sourceModel(budgetMs = 0)
        val allSkipped = model.rows.associate { it.rowId to FixtureTerminal.SKIPPED }
        val fixture = SemanticLedgerFixtures.complete(model = model, terminals = allSkipped)
        val snapshot = LedgerSemanticValidator.validate(fixture)
        val skipped = snapshot.events.filter { it.eventType == LedgerEventType.SKIPPED }
        assertEquals(model.rows.size, skipped.size)
        assertEquals("RULE-BUDGET-FIRST-NONFIT", skipped.first().ruleId)
        assertTrue(skipped.drop(1).all { it.ruleId == "RULE-BUDGET-AFTER-FIRST-NONFIT" })

        val resumed = SemanticLedgerFixtures.complete(
            model = model, terminals = mapOf(model.rows.first().rowId to FixtureTerminal.SKIPPED),
        )
        assertThrows(IllegalArgumentException::class.java) { LedgerSemanticValidator.validate(resumed) }
        val wrongFirstRule = fixture.events.toMutableList().also { events ->
            val firstSkip = events.indexOfFirst { it.eventType == LedgerEventType.SKIPPED }
            events[firstSkip] = events[firstSkip].copy(ruleId = "RULE-BUDGET-AFTER-FIRST-NONFIT")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(fixture, wrongFirstRule))
        }
    }
    @Test
    fun `validated mutation pass yields exact direct and transitive causal prune marks`() {
        val candidates = pruneCandidates(Shape.FOREST)
        val model = model(candidates)
        val mutationRows = model.rows.filter { it.kind == SessionRowKind.MUTATION }
        val missingPrunes = SemanticLedgerFixtures.complete(candidates, model)
        assertThrows(IllegalArgumentException::class.java) { LedgerSemanticValidator.validate(missingPrunes) }
        val fixture = SemanticLedgerFixtures.complete(
            candidates, model, prunedByRow = mutationRows.drop(1).associate { it.rowId to mutationRows.first().rowId },
        )
        val snapshot = LedgerSemanticValidator.validate(fixture)
        val pass = snapshot.passRecord(mutationRows.first().rowId)
        val marks = ForwardPruneForest.create(snapshot).qualifyingClosure(pass)
        assertEquals(mutationRows.drop(1).map { it.rowId }, marks.map { it.rowId })
        assertTrue(marks.all { it.causalEventHash == pass.causalEventHash })

        val decisions = OverflowSafeBudgetScheduler.schedule(snapshot, List(snapshot.rows.size - marks.size) { 0L })
        assertEquals(marks.map { it.rowId }, decisions.filter { it.disposition == SchedulerDisposition.PRUNED }.map { it.rowId })
        decisions.filter { it.disposition == SchedulerDisposition.PRUNED }.forEach { decision ->
            assertEquals(pass.causalEventHash, decision.causalEventHash)
        }
    }

    @Test
    fun `pruned terminal rejects non-qualifying causal event and non-forest shapes`() {
        val candidates = pruneCandidates(Shape.FOREST)
        val model = model(candidates)
        val mutations = model.rows.filter { it.kind == SessionRowKind.MUTATION }
        val fixture = SemanticLedgerFixtures.complete(
            candidates, model, prunedByRow = mutations.drop(1).associate { it.rowId to mutations.first().rowId },
        )
        val bad = fixture.events.toMutableList()
        val pruned = bad.indexOfFirst { it.eventType == LedgerEventType.PRUNED }
        bad[pruned] = bad[pruned].copy(causalEventHash = fixture.events.first().eventSha256)
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.rechain(fixture, bad))
        }
        listOf(Shape.BACKWARD, Shape.CONVERGENT, Shape.CROSS_GROUP).forEach { shape ->
            val malformed = pruneCandidates(shape)
            assertThrows(IllegalArgumentException::class.java) {
                ForwardPruneForest.create(malformed, model(malformed).rows)
            }
        }
    }

    @Test
    fun `dependency scheduler decision derives pass state and causal terminal from validated ledger`() {
        val candidates = dependencyCandidates()
        val model = model(candidates)
        val mutations = model.rows.filter { it.kind == SessionRowKind.MUTATION }
        val illegallyStarted = SemanticLedgerFixtures.complete(
            candidates, model, terminals = mapOf(mutations[0].rowId to FixtureTerminal.FAIL),
        )
        assertThrows(IllegalArgumentException::class.java) { LedgerSemanticValidator.validate(illegallyStarted) }
        val fixture = SemanticLedgerFixtures.complete(
            candidates, model,
            terminals = mapOf(
                mutations[0].rowId to FixtureTerminal.FAIL,
                mutations[1].rowId to FixtureTerminal.BLOCKED_DEPENDENCY,
            ),
        )
        val snapshot = LedgerSemanticValidator.validate(fixture)
        val decisions = OverflowSafeBudgetScheduler.schedule(snapshot, List(snapshot.rows.size - 1) { 0L })
        val blocked = decisions.single { it.rowId == mutations[1].rowId }
        assertEquals(SchedulerDisposition.BLOCKED_DEPENDENCY, blocked.disposition)
        assertEquals(snapshot.terminalEvent(mutations[0].rowId).eventSha256, blocked.causalEventHash)
        assertEquals(null, blocked.elapsedOffsetMs)
    }

    @Test
    fun `template generator rejects a dependency placed after its dependent by phase order`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            model(dependencyCandidates(invertedPhaseOrder = true))
        }
        assertTrue(error.message.orEmpty().contains("dependency must precede"))
    }

    @Test
    fun `emergency recovery descends stack indices while preserving each inverse order`() {
        val candidates = pruneCandidates(Shape.FOREST)
        val rows = model(candidates).rows.filter { it.kind == SessionRowKind.MUTATION }
        var stack = RollbackStack.empty().capture(rows[0]).arm().mutation(MutationAttemptOutcome.PASS)
        stack = stack.capture(rows[1], exceptional = true).arm().mutation(MutationAttemptOutcome.THROW).clear()
        stack = stack.capture(rows[2], exceptional = true).arm().mutation(MutationAttemptOutcome.PARTIAL)
        val outcomes = mapOf(
            rows[0].rollbackStackIndex!! to RestoreAttemptOutcome.PASS,
            rows[1].rollbackStackIndex!! to RestoreAttemptOutcome.FAIL,
            rows[2].rollbackStackIndex!! to RestoreAttemptOutcome.PASS,
        )
        val recovery = stack.emergencyRecover(outcomes)
        assertEquals(listOf(2, 1, 0), recovery.steps.map { it.stackIndex })
        recovery.steps.forEach { step ->
            assertEquals(rows.single { it.rollbackStackIndex == step.stackIndex }.inverseOperationIds, step.inverseOperationIds)
        }
        assertEquals(listOf(1), recovery.stack.frames.map { it.stackIndex })
        assertEquals(RollbackFrameState.RESTORE_FAILED, recovery.stack.frames.single().state)
    }

    @Test
    fun `four D milestones remain independent non-prunable terminals`() {
        val model = SemanticLedgerFixtures.sourceModel()
        val milestones = model.rows.filter { it.kind == SessionRowKind.MILESTONE }
        assertEquals(
            listOf("RESULT-D-M1-0001", "RESULT-D-M2-0001", "RESULT-D-M3-0001", "RESULT-D-M4-0001"),
            milestones.map { it.resultIdentityId },
        )
        assertTrue(milestones.all { it.candidateRevisionId == null && it.dependsOnRowIds.isEmpty() })
        RollbackContracts.validate(model.rows)
        val mutations = model.rows.filter { it.kind == SessionRowKind.MUTATION }
        assertEquals(mutations.indices.toList(), mutations.map { it.rollbackStackIndex })
    }

    @Test
    fun `optional D H0 milestone projects through freeze and ledger at most once`() {
        val base = SemanticLedgerFixtures.sourceModel()
        val h0 = base.rows.first { it.kind == SessionRowKind.MILESTONE }.copy(
            rowId = "ROW-8999-D-H0-SURFACE", resultIdentityId = "RESULT-D-H0-0001",
            observations = listOf("OBS-D-H0-SURFACE-RESULT"),
            requiredSurfaces = listOf(RequiredSurface.HUD_NAV_MAP), phaseRank = 8999,
        )
        val model = base.copy(rows = (base.rows + h0).sortedWith(SessionRowOrdering.comparator))
        val snapshot = LedgerSemanticValidator.validate(SemanticLedgerFixtures.complete(model = model))
        assertEquals(1, snapshot.rows.count { it.resultIdentityId?.startsWith("RESULT-D-H0-") == true })

        val duplicate = h0.copy(
            rowId = "ROW-8998-D-H0-SURFACE", resultIdentityId = "RESULT-D-H0-0002", phaseRank = 8998,
        )
        val duplicateModel = base.copy(rows = (base.rows + h0 + duplicate).sortedWith(SessionRowOrdering.comparator))
        assertThrows(IllegalArgumentException::class.java) {
            LedgerSemanticValidator.validate(SemanticLedgerFixtures.complete(model = duplicateModel))
        }
        assertThrows(IllegalArgumentException::class.java) {
            h0.copy(observations = listOf("OBS-M1-WRONG-H0-TOKEN"))
        }
    }

    private fun model(candidates: List<CandidateRevision>): SessionTemplateModel =
        SessionTemplateGenerator.generate(candidates, listOf(RegistryRevision.create(1, null, candidates)))

    private enum class Shape { FOREST, BACKWARD, CONVERGENT, CROSS_GROUP }

    private fun pruneCandidates(shape: Shape): List<CandidateRevision> {
        val ids = listOf("CAND-H-101-PRUNE-A@1", "CAND-H-102-PRUNE-B@1", "CAND-H-103-PRUNE-C@1")
        val edges = when (shape) {
            Shape.FOREST -> listOf(listOf(ids[1]), listOf(ids[2]), emptyList())
            Shape.BACKWARD -> listOf(emptyList(), emptyList(), listOf(ids[0]))
            Shape.CONVERGENT -> listOf(listOf(ids[2]), listOf(ids[2]), emptyList())
            Shape.CROSS_GROUP -> listOf(listOf(ids[1]), emptyList(), emptyList())
        }
        return ids.mapIndexed { index, id ->
            mutationCandidate(
                id = id,
                group = if (shape == Shape.CROSS_GROUP && index == 1) "PRUNE-M3-OTHER" else "PRUNE-M3-SIGN",
                subsumes = edges[index], dependencies = emptyList(),
            )
        }.sortedWith(compareBy(ExpansionIds.candidateComparator) { it.candidateRevisionId })
    }

    private fun dependencyCandidates(invertedPhaseOrder: Boolean = false): List<CandidateRevision> {
        val first = "CAND-H-201-DEPENDENCY-A@1"; val second = "CAND-H-202-DEPENDENCY-B@1"
        return listOf(
            mutationCandidate(first, null, emptyList(), emptyList(), if (invertedPhaseOrder) 200 else 100),
            mutationCandidate(second, null, emptyList(), listOf(first), 100),
        )
    }

    private fun mutationCandidate(
        id: String, group: String?, subsumes: List<String>, dependencies: List<String>, phaseRank: Int = 100,
    ) = CandidateRevision.create(CandidateRevisionInput(
        candidateRevisionId = id, milestone = Milestone.M3, mode = CandidateMode.MUTATION,
        proof = ExpansionTestFixtures.s11ReadyProof, hypothesisId = "HYP-PRUNE-SIGN",
        mutationDimension = "DIMENSION-PRUNE-SIGN",
        requiredSurfaces = listOf(RequiredSurface.CLUSTER_SPEED_SIGN),
        requiredObservationIds = listOf("OBS-M3-PRUNE-SIGN"),
        restoreScope = listOf(RestoreScope.CURRENT_PROPERTY),
        invalidationTriggers = listOf("REASON-INVALIDATED-PRUNE-SIGN"),
        planningProfile = PlanningProfile(phaseRank, 90, 80, 100, 10, 10, 1_000),
        dependsOnRevisionIds = dependencies, pruneGroup = group, subsumes = subsumes,
        invalidatesOn = listOf("REASON-INVALIDATED-PRUNE-SIGN"), predecessorCandidateSha256 = null,
    ))
}
