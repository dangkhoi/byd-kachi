package com.byd.clusternav.offcar

import java.nio.file.Files
import java.nio.file.Path

enum class SessionRowKind { READ_ONLY, MUTATION, MILESTONE }

data class SessionRowTemplate(
    val rowId: String, val kind: SessionRowKind, val candidateRevisionId: String?, val resultIdentityId: String?,
    val probeIds: List<String>, val mutationOperationId: String?, val clearOperationId: String?,
    val inverseOperationIds: List<String>, val observations: List<String>, val requiredSurfaces: List<RequiredSurface>,
    val dependsOnRowIds: List<String>, val rollbackStackIndex: Int?, val restoreScope: List<RestoreScope>,
    val invalidationTriggers: List<String>, val phaseRank: Int, val dependencyReady: Boolean,
    val evidenceStrength: Int, val informationGain: Int, val reversibility: Int, val mutationRisk: Int,
    val dependencyUncertainty: Int, val estimatedTimeMs: Long,
) {
    val riskClass = when (mutationRisk) { in 0..33 -> RiskClass.LOW; in 34..66 -> RiskClass.MEDIUM; else -> RiskClass.HIGH }
    val score = checkedScore(evidenceStrength, informationGain, reversibility, mutationRisk, estimatedTimeMs, dependencyUncertainty)
    init { validateTemplateRow(this) }
    internal fun json() = CanonicalJson.obj(
        "candidateRevisionId" to CanonicalJson.text(candidateRevisionId), "clearOperationId" to CanonicalJson.text(clearOperationId),
        "dependencyReady" to JsonBoolean(dependencyReady), "dependencyUncertainty" to JsonInteger(dependencyUncertainty.toLong()),
        "dependsOnRowIds" to jsonStrings(dependsOnRowIds), "estimatedTimeMs" to JsonInteger(estimatedTimeMs),
        "evidenceStrength" to JsonInteger(evidenceStrength.toLong()), "informationGain" to JsonInteger(informationGain.toLong()),
        "invalidationTriggers" to jsonStrings(invalidationTriggers), "inverseOperationIds" to jsonStrings(inverseOperationIds),
        "kind" to CanonicalJson.enum(kind), "mutationOperationId" to CanonicalJson.text(mutationOperationId),
        "mutationRisk" to JsonInteger(mutationRisk.toLong()), "observations" to jsonStrings(observations),
        "phaseRank" to JsonInteger(phaseRank.toLong()), "probeIds" to jsonStrings(probeIds),
        "requiredSurfaces" to jsonEnums(requiredSurfaces), "restoreScope" to jsonEnums(restoreScope),
        "resultIdentityId" to CanonicalJson.text(resultIdentityId), "reversibility" to JsonInteger(reversibility.toLong()),
        "riskClass" to CanonicalJson.enum(riskClass), "rollbackStackIndex" to CanonicalJson.integer(rollbackStackIndex),
        "rowId" to JsonText(rowId), "score" to JsonInteger(score),
    )
}

private fun validateTemplateRow(r: SessionRowTemplate) {
    require(Regex(ExpansionIds.ROW_PATTERN).matches(r.rowId)); r.candidateRevisionId?.let(ExpansionIds::candidate)
    r.resultIdentityId?.let { require(Regex(ExpansionIds.RESULT_PATTERN).matches(it)) }; r.probeIds.forEach(ExpansionIds::probe)
    listOfNotNull(r.mutationOperationId, r.clearOperationId).forEach(ExpansionIds::operation)
    require(r.inverseOperationIds.distinct().size == r.inverseOperationIds.size)
    r.inverseOperationIds.forEach { operation ->
        ExpansionIds.operation(operation)
        require(operation.startsWith("OP-INVERSE-") || operation.startsWith("OP-RESTORE-"))
    }
    r.observations.forEach(ExpansionIds::observation); r.invalidationTriggers.forEach(ExpansionIds::reason)
    require(r.phaseRank in 0..9999 && r.estimatedTimeMs in 0..86_400_000); listOf(r.evidenceStrength, r.informationGain, r.reversibility, r.mutationRisk, r.dependencyUncertainty).forEach { require(it in 0..100) }
    require(r.probeIds == r.probeIds.distinct().sorted() && r.observations == r.observations.distinct().sorted())
    require(r.requiredSurfaces == r.requiredSurfaces.distinct().sortedBy { it.ordinal } && r.restoreScope == r.restoreScope.distinct().sortedBy { it.ordinal })
    require(r.invalidationTriggers == r.invalidationTriggers.distinct().sorted() && r.dependsOnRowIds.distinct().size == r.dependsOnRowIds.size)
    r.dependsOnRowIds.forEach { require(Regex(ExpansionIds.ROW_PATTERN).matches(it)) }
    when (r.kind) {
        SessionRowKind.READ_ONLY -> require(r.probeIds.size == 1 && r.resultIdentityId == null && r.mutationOperationId == null && r.clearOperationId == null && r.inverseOperationIds.isEmpty() && r.observations.isEmpty() && r.rollbackStackIndex == null && r.restoreScope.isEmpty() && r.invalidationTriggers.isEmpty() && ((r.candidateRevisionId == null && r.requiredSurfaces.isEmpty()) || (r.candidateRevisionId != null && r.requiredSurfaces.isNotEmpty())))
        SessionRowKind.MUTATION -> require(r.candidateRevisionId != null && r.resultIdentityId == null && r.probeIds.isEmpty() && r.mutationOperationId?.startsWith("OP-MUTATE-") == true && r.clearOperationId?.startsWith("OP-CLEAR-") == true && r.inverseOperationIds.isNotEmpty() && r.observations.isNotEmpty() && r.requiredSurfaces.isNotEmpty() && r.rollbackStackIndex != null && r.restoreScope.isNotEmpty() && r.invalidationTriggers.isNotEmpty())
        SessionRowKind.MILESTONE -> {
            require(r.candidateRevisionId == null && r.resultIdentityId?.matches(Regex("^RESULT-D-(H0|M[1-4])-[0-9]{4}$")) == true && r.probeIds.isEmpty() && r.mutationOperationId == null && r.clearOperationId == null && r.inverseOperationIds.isEmpty() && r.observations.isNotEmpty() && r.requiredSurfaces.size == 1 && r.rollbackStackIndex == null && r.restoreScope.isEmpty() && r.invalidationTriggers.isEmpty() && r.dependsOnRowIds.isEmpty())
            val token = Regex("^RESULT-D-(H0|M[1-4])-[0-9]{4}$").matchEntire(r.resultIdentityId)!!.groupValues[1]
            val observationPrefix = if (token == "H0") "OBS-D-H0-" else "OBS-$token-"
            val surface = if (token == "H0") RequiredSurface.HUD_NAV_MAP else milestoneSurface(Milestone.valueOf(token))
            require(r.observations.all { it.startsWith(observationPrefix) } && r.requiredSurfaces == listOf(surface))
        }
    }
}
private fun milestoneSurface(m: Milestone) = when (m) { Milestone.M1 -> RequiredSurface.HUD_NAV_MAP; Milestone.M2 -> RequiredSurface.HUD_ROAD_NAME; Milestone.M3 -> RequiredSurface.CLUSTER_SPEED_SIGN; Milestone.M4 -> RequiredSurface.HUD_SPEED_SIGN }
private fun jsonStrings(v: Iterable<String>) = CanonicalJson.array(v.map(::JsonText))
private fun jsonEnums(v: Iterable<Enum<*>>) = CanonicalJson.array(v.map { JsonText(it.name) })

fun checkedScore(e: Int, i: Int, r: Int, risk: Int, time: Long, uncertainty: Int): Long {
    listOf(e, i, r, risk, uncertainty).forEach { require(it in 0..100) }; require(time in 0..86_400_000)
    return Math.subtractExact(Math.addExact(Math.addExact(Math.multiplyExact(e.toLong(), 100), Math.multiplyExact(i.toLong(), 40)), Math.multiplyExact(r.toLong(), 30)), Math.addExact(Math.addExact(Math.multiplyExact(risk.toLong(), 50), Math.multiplyExact(time / 1000, 10)), Math.multiplyExact(uncertainty.toLong(), 25)))
}

object SessionRowOrdering {
    val comparator = Comparator<SessionRowTemplate> { a, b ->
        compareValues(a.phaseRank, b.phaseRank).takeIf { it != 0 } ?: compareValues(!a.dependencyReady, !b.dependencyReady).takeIf { it != 0 }
        ?: compareValues(b.score, a.score).takeIf { it != 0 } ?: compareValues(a.riskClass.ordinal, b.riskClass.ordinal).takeIf { it != 0 }
        ?: compareValues(a.estimatedTimeMs, b.estimatedTimeMs).takeIf { it != 0 } ?: compareCandidate(a.candidateRevisionId, b.candidateRevisionId).takeIf { it != 0 } ?: a.rowId.compareTo(b.rowId)
    }
    private fun compareCandidate(a: String?, b: String?) = when { a == null && b == null -> 0; a == null -> -1; b == null -> 1; else -> ExpansionIds.candidateComparator.compare(a, b) }
}

data class SessionTemplateModel(val revision: Int, val budgetMs: Long, val rows: List<SessionRowTemplate>, val allowedProbeIds: List<String>, val allowedCandidateRevisionIds: List<String>, val allowedMutationCandidateRevisionIds: List<String>)

object SessionTemplateGenerator {
    private data class Draft(val suffix: String, val kind: SessionRowKind, val candidate: CandidateRevision?, val resultId: String?, val probeId: String?, val milestone: Milestone?, val profile: PlanningProfile)
    fun generate(candidates: List<CandidateRevision>, history: List<RegistryRevision>, probes: List<DiscoveryProbe> = DiscoveryProbeCatalog.all, budgetMs: Long = 3_600_000): SessionTemplateModel {
        require(budgetMs in 0..86_400_000); CandidateRevisionChains.validate(candidates); RegistryHistory.validate(history)
        require(history.last().candidateRefs == candidates.map { CandidateRevisionRef(it.candidateRevisionId, it.revisionSha256) })
        val eligible = FrozenEligibility.freeze(candidates, probes); val byId = candidates.associateBy { it.candidateRevisionId }
        val drafts = buildList {
            probes.sortedBy { it.id }.forEach { add(Draft("DISCOVERY-${it.id.removePrefix("PROBE-")}", SessionRowKind.READ_ONLY, null, null, it.id, null, PlanningProfile(0, 100, 100, 100, 0, 0, 250))) }
            eligible.allowedCandidateRevisionIds.map(byId::getValue).forEach { c -> val k = ExpansionIds.candidate(c.candidateRevisionId); val mutation = c.input.mode == CandidateMode.MUTATION; add(Draft(suffix("${if (mutation) "MUTATE" else "READ"}-${k.family}-${k.number.toString().padStart(3, '0')}-${k.slug}"), if (mutation) SessionRowKind.MUTATION else SessionRowKind.READ_ONLY, c, null, if (mutation) null else c.input.proof.readProbeId, null, c.input.planningProfile)) }
            Milestone.entries.forEachIndexed { n, m -> add(Draft("D-${m.name}-SURFACE", SessionRowKind.MILESTONE, null, "RESULT-D-${m.name}-0001", null, m, PlanningProfile(9000 + n, 100, 100, 100, 0, 0, 60_000))) }
        }
        val provisional = drafts.mapIndexed { n, d -> row(d, "ROW-${(n + 1).toString().padStart(4, '0')}-${d.suffix}", emptyList(), if (d.kind == SessionRowKind.MUTATION) 0 else null, true) }
        val ordered = drafts.zip(provisional).sortedWith { a, b -> SessionRowOrdering.comparator.compare(a.second, b.second) }.map { it.first }
        val ids = ordered.mapIndexed { n, d -> d to "ROW-${(n + 1).toString().padStart(4, '0')}-${d.suffix}" }.toMap()
        val positions = ordered.mapIndexed { n, d -> ids.getValue(d) to n }.toMap()
        val candidateRows = ordered.filter { it.candidate != null }.associate { it.candidate!!.candidateRevisionId to ids.getValue(it) }; var stack = 0
        val rows = ordered.mapIndexed { index, d ->
            val deps = d.candidate?.input?.dependsOnRevisionIds?.map { requireNotNull(candidateRows[it]) }.orEmpty()
            val dependencyReady = deps.all { positions.getValue(it) < index }
            require(dependencyReady) { "candidate dependency must precede its dependent in normative row order" }
            row(d, ids.getValue(d), deps, if (d.kind == SessionRowKind.MUTATION) stack++ else null, dependencyReady)
        }
        require(rows == rows.sortedWith(SessionRowOrdering.comparator)); RollbackContracts.validate(rows)
        return SessionTemplateModel(
            history.last().revision, budgetMs, immutableListSnapshot(rows), eligible.allowedProbeIds,
            eligible.allowedCandidateRevisionIds, eligible.mutationCandidateRevisionIds,
        )
    }
    private fun row(d: Draft, id: String, deps: List<String>, stack: Int?, dependencyReady: Boolean): SessionRowTemplate {
        val c = d.candidate; val p = c?.input?.proof
        return SessionRowTemplate(
            id, d.kind, c?.candidateRevisionId, d.resultId, immutableListSnapshot(listOfNotNull(d.probeId)),
            p?.mutationOperationId, p?.clearOperationId, immutableListSnapshot(p?.inverseOperationIds.orEmpty()),
            immutableListSnapshot(if (d.kind == SessionRowKind.MUTATION) c!!.input.requiredObservationIds else d.milestone?.let { listOf("OBS-${it.name}-SURFACE-RESULT") }.orEmpty()),
            immutableListSnapshot(c?.input?.requiredSurfaces ?: listOfNotNull(d.milestone?.let(::milestoneSurface))),
            immutableListSnapshot(deps), stack,
            immutableListSnapshot(if (d.kind == SessionRowKind.MUTATION) c?.input?.restoreScope.orEmpty() else emptyList()),
            immutableListSnapshot(if (d.kind == SessionRowKind.MUTATION) c?.input?.invalidationTriggers.orEmpty() else emptyList()),
            d.profile.phaseRank, dependencyReady, d.profile.evidenceStrength, d.profile.informationGain,
            d.profile.reversibility, d.profile.mutationRisk, d.profile.dependencyUncertainty, d.profile.estimatedTimeMs,
        )
    }
    private fun suffix(v: String) = if (v.length <= 64) v else v.take(51) + "-" + ExpansionHashing.sha256Utf8(v).take(12).uppercase()
}

enum class IdentityVariant { DEBUG, VEHICLE_TEST, RELEASE }
enum class IdentityComponent { COMPONENT_PROBE_RECEIVER, COMPONENT_PROBE_ACTIVITY }
enum class IdentityPermission { PERMISSION_VENDOR_CAR, PERMISSION_NONE }
enum class IdentityProfile { PROFILE_SEAL_T10, PROFILE_UNASSIGNED }
data class ExactIdentity(
    val sourceSnapshotSha256: String, val diffFileSha256: String, val apkFileSha256: String, val signerSha256: String,
    val registryFileSha256: String, val packSha256: String, val candidateSetSha256: String, val variant: IdentityVariant,
    val componentId: IdentityComponent, val permissionId: IdentityPermission, val profileId: IdentityProfile,
    val sourceArtifactId: String = "ARTIFACT-EXPANSION-SOURCE-SET", val diffArtifactId: String = "ARTIFACT-CANDIDATE-DIFF",
    val apkArtifactId: String = "ARTIFACT-T10-APK", val senderId: String = "SENDER_CLUSTER_NAV",
) {
    init { listOf(sourceSnapshotSha256, diffFileSha256, apkFileSha256, signerSha256, registryFileSha256, packSha256, candidateSetSha256).forEach(ExpansionIds::sha256); require(sourceArtifactId == "ARTIFACT-EXPANSION-SOURCE-SET" && diffArtifactId == "ARTIFACT-CANDIDATE-DIFF" && apkArtifactId == "ARTIFACT-T10-APK" && senderId == "SENDER_CLUSTER_NAV") }
    internal fun json() = CanonicalJson.obj("apkArtifactId" to JsonText(apkArtifactId), "apkFileSha256" to JsonText(apkFileSha256), "candidateSetSha256" to JsonText(candidateSetSha256), "componentId" to CanonicalJson.enum(componentId), "diffArtifactId" to JsonText(diffArtifactId), "diffFileSha256" to JsonText(diffFileSha256), "packSha256" to JsonText(packSha256), "permissionId" to CanonicalJson.enum(permissionId), "profileId" to CanonicalJson.enum(profileId), "registryFileSha256" to JsonText(registryFileSha256), "senderId" to JsonText(senderId), "signerSha256" to JsonText(signerSha256), "sourceArtifactId" to JsonText(sourceArtifactId), "sourceSnapshotSha256" to JsonText(sourceSnapshotSha256), "variant" to CanonicalJson.enum(variant))
}

data class SessionPackTemplateFixture(val model: SessionTemplateModel, val templateFileSha256: String, val coverageFileSha256: String, val evidenceMapFileSha256: String, val registryFileSha256: String, val packSha256: String, val resolvedExactIdentity: ExactIdentity)
data class SessionPackFreeze(val sessionId: String, val sessionInstanceSha256: String, val templateFileSha256: String, val exactIdentity: ExactIdentity, val revision: Int, val allowedProbeIds: List<String>, val allowedCandidateRevisionIds: List<String>, val allowedMutationCandidateRevisionIds: List<String>, val sessionStartElapsedMs: Long, val budgetMs: Long, val deadlineElapsedMs: Long) {
    internal fun json(withInstance: Boolean) = JsonObject(buildList { add("allowedCandidateRevisionIds" to jsonStrings(allowedCandidateRevisionIds)); add("allowedMutationCandidateRevisionIds" to jsonStrings(allowedMutationCandidateRevisionIds)); add("allowedProbeIds" to jsonStrings(allowedProbeIds)); add("budgetMs" to JsonInteger(budgetMs)); add("deadlineElapsedMs" to JsonInteger(deadlineElapsedMs)); add("exactIdentity" to exactIdentity.json()); add("revision" to JsonInteger(revision.toLong())); add("sessionId" to JsonText(sessionId)); if (withInstance) add("sessionInstanceSha256" to JsonText(sessionInstanceSha256)); add("sessionStartElapsedMs" to JsonInteger(sessionStartElapsedMs)); add("templateFileSha256" to JsonText(templateFileSha256)) })
}

data class SessionRow(
    val rowId: String, val kind: SessionRowKind, val exactIdentity: ExactIdentity, val candidateRevisionId: String?, val resultIdentityId: String?, val probeIds: List<String>, val mutationOperationId: String?, val clearOperationId: String?, val inverseOperationIds: List<String>, val observations: List<String>, val requiredSurfaces: List<RequiredSurface>, val dependsOnRowIds: List<String>, val rollbackStackIndex: Int?, val restoreScope: List<RestoreScope>, val invalidationTriggers: List<String>, val phaseRank: Int, val dependencyReady: Boolean, val evidenceStrength: Int, val informationGain: Int, val reversibility: Int, val mutationRisk: Int, val dependencyUncertainty: Int, val estimatedTimeMs: Long, val riskClass: RiskClass, val score: Long,
) {
    fun template() = SessionRowTemplate(rowId, kind, candidateRevisionId, resultIdentityId, probeIds, mutationOperationId, clearOperationId, inverseOperationIds, observations, requiredSurfaces, dependsOnRowIds, rollbackStackIndex, restoreScope, invalidationTriggers, phaseRank, dependencyReady, evidenceStrength, informationGain, reversibility, mutationRisk, dependencyUncertainty, estimatedTimeMs)
    companion object {
        fun from(t: SessionRowTemplate, identity: ExactIdentity) = SessionRow(
            t.rowId, t.kind, identity, t.candidateRevisionId, t.resultIdentityId,
            immutableListSnapshot(t.probeIds), t.mutationOperationId, t.clearOperationId,
            immutableListSnapshot(t.inverseOperationIds), immutableListSnapshot(t.observations),
            immutableListSnapshot(t.requiredSurfaces), immutableListSnapshot(t.dependsOnRowIds),
            t.rollbackStackIndex, immutableListSnapshot(t.restoreScope), immutableListSnapshot(t.invalidationTriggers),
            t.phaseRank, t.dependencyReady, t.evidenceStrength, t.informationGain, t.reversibility,
            t.mutationRisk, t.dependencyUncertainty, t.estimatedTimeMs, t.riskClass, t.score,
        )
    }
}

data class LedgerEvidenceRef(val evidenceId: String, val operationId: String? = null, val targetCandidateRevisionId: String? = null, val suggestedInvalidationReasonId: String? = null) { internal fun json() = CanonicalJson.obj("evidenceId" to JsonText(evidenceId), "operationId" to CanonicalJson.text(operationId), "suggestedInvalidationReasonId" to CanonicalJson.text(suggestedInvalidationReasonId), "targetCandidateRevisionId" to CanonicalJson.text(targetCandidateRevisionId)) }
enum class LedgerPhaseOutcome { PASS, FAIL }
enum class LedgerResult { PASS, FAIL, INCONCLUSIVE }
enum class ProposalDecision { ACCEPTED, REJECTED }
data class ProposalDispositionRecord(val proposalEventSha256: String, val proposedTombstoneRevisionSha256: String, val disposition: ProposalDecision, val reasonId: String?)

data class LedgerEvent(
    val eventType: LedgerEventType, val sessionId: String, val sequence: Int, val exactIdentity: ExactIdentity, val elapsedOffsetMs: Long,
    val eventId: String = "EVENT-${sequence.toString().padStart(6, '0')}", val previousEventHash: String? = null,
    val rowId: String? = null, val rowKind: SessionRowKind? = null, val candidateRevisionId: String? = null,
    val resultIdentityId: String? = null, val mutationOperationId: String? = null, val probeId: String? = null,
    val discoveryEvidenceRefs: List<LedgerEvidenceRef> = emptyList(), val preconditionOutcome: LedgerPhaseOutcome? = null,
    val observations: List<String> = emptyList(), val clearEvidenceRefs: List<LedgerEvidenceRef> = emptyList(),
    val restoreEvidenceRefs: List<LedgerEvidenceRef> = emptyList(), val result: LedgerResult? = null, val reasonId: String? = null,
    val ruleId: String? = null, val causalEventHash: String? = null, val tombstoneRevisionId: String? = null,
    val proposedTombstone: ProposedTombstone? = null, val invalidationReasonId: String? = null,
    val mutationOutcome: LedgerPhaseOutcome? = null, val clearOutcome: LedgerPhaseOutcome? = null,
    val restoreOutcome: LedgerPhaseOutcome? = null, val eventSha256: String = "0".repeat(64),
) {
    internal fun json(withHash: Boolean) = JsonObject(buildList {
        add("candidateRevisionId" to CanonicalJson.text(candidateRevisionId)); add("causalEventHash" to CanonicalJson.text(causalEventHash)); add("clearEvidenceRefs" to CanonicalJson.array(clearEvidenceRefs.map { it.json() })); add("clearOutcome" to CanonicalJson.enum(clearOutcome)); add("discoveryEvidenceRefs" to CanonicalJson.array(discoveryEvidenceRefs.map { it.json() })); add("elapsedOffsetMs" to JsonInteger(elapsedOffsetMs)); add("eventId" to JsonText(eventId)); if (withHash) add("eventSha256" to JsonText(eventSha256)); add("eventType" to CanonicalJson.enum(eventType)); add("exactIdentity" to exactIdentity.json()); add("invalidationReasonId" to CanonicalJson.text(invalidationReasonId)); add("mutationOperationId" to CanonicalJson.text(mutationOperationId)); add("mutationOutcome" to CanonicalJson.enum(mutationOutcome)); add("observations" to jsonStrings(observations)); add("preconditionOutcome" to CanonicalJson.enum(preconditionOutcome)); add("previousEventHash" to CanonicalJson.text(previousEventHash)); add("probeId" to CanonicalJson.text(probeId)); add("proposedTombstone" to (proposedTombstone?.let { CanonicalJson.obj("candidate" to it.candidate.json(true), "invalidationReasonId" to JsonText(it.invalidationReasonId)) } ?: JsonNull)); add("reasonId" to CanonicalJson.text(reasonId)); add("restoreEvidenceRefs" to CanonicalJson.array(restoreEvidenceRefs.map { it.json() })); add("restoreOutcome" to CanonicalJson.enum(restoreOutcome)); add("result" to CanonicalJson.enum(result)); add("resultIdentityId" to CanonicalJson.text(resultIdentityId)); add("rowId" to CanonicalJson.text(rowId)); add("rowKind" to CanonicalJson.enum(rowKind)); add("ruleId" to CanonicalJson.text(ruleId)); add("sequence" to JsonInteger(sequence.toLong())); add("sessionId" to JsonText(sessionId)); add("tombstoneRevisionId" to CanonicalJson.text(tombstoneRevisionId))
    })
    fun computedSha256() = CanonicalJson.digest(json(false))
    fun seal(previous: String? = previousEventHash): LedgerEvent { val draft = copy(previousEventHash = previous, eventSha256 = "0".repeat(64)); return draft.copy(eventSha256 = draft.computedSha256()) }
}

data class LedgerFixture(val template: SessionPackTemplateFixture, val freeze: SessionPackFreeze, val rows: List<SessionRow>, val events: List<LedgerEvent>, val candidates: List<CandidateRevision>, val sessionNonceSha256: String, val proposalDispositions: List<ProposalDispositionRecord> = emptyList(), val nextRegistryCandidates: List<CandidateRevision> = candidates)

sealed interface ValidatedPassRecord { val sessionInstanceSha256: String; val rowId: String; val candidateRevisionId: String?; val observations: List<String>; val clearEvidenceRefs: List<LedgerEvidenceRef>; val restoreEvidenceRefs: List<LedgerEvidenceRef>; val causalEventHash: String }
private data class SnapshotPass(override val sessionInstanceSha256: String, override val rowId: String, override val candidateRevisionId: String?, override val observations: List<String>, override val clearEvidenceRefs: List<LedgerEvidenceRef>, override val restoreEvidenceRefs: List<LedgerEvidenceRef>, override val causalEventHash: String) : ValidatedPassRecord

class ValidatedLedgerSnapshot private constructor(f: LedgerFixture) {
    val freeze = f.freeze.copy(allowedProbeIds = immutableListSnapshot(f.freeze.allowedProbeIds), allowedCandidateRevisionIds = immutableListSnapshot(f.freeze.allowedCandidateRevisionIds), allowedMutationCandidateRevisionIds = immutableListSnapshot(f.freeze.allowedMutationCandidateRevisionIds))
    val rows = immutableListSnapshot(f.rows.map(::snapshotRow)); val events = immutableListSnapshot(f.events.map(::snapshotEvent)); internal val candidateRevisions = immutableListSnapshot(f.candidates)
    val passRecords: List<ValidatedPassRecord> = immutableListSnapshot(events.filter { it.eventType == LedgerEventType.PASS }.map { SnapshotPass(freeze.sessionInstanceSha256, it.rowId!!, it.candidateRevisionId, immutableListSnapshot(it.observations), immutableListSnapshot(it.clearEvidenceRefs), immutableListSnapshot(it.restoreEvidenceRefs), it.eventSha256) })
    fun passRecord(rowId: String) = passRecords.single { it.rowId == rowId }
    fun terminalEvent(rowId: String) = events.last { it.rowId == rowId }
    fun eventBeforeRow(rowId: String): LedgerEvent { val first = events.indexOfFirst { it.rowId == rowId }; require(first > 0); return events[first - 1] }
    companion object { internal fun validated(f: LedgerFixture): ValidatedLedgerSnapshot { validateFixture(f); return ValidatedLedgerSnapshot(f) } }
}
private fun snapshotRow(r: SessionRow) = r.copy(probeIds = immutableListSnapshot(r.probeIds), inverseOperationIds = immutableListSnapshot(r.inverseOperationIds), observations = immutableListSnapshot(r.observations), requiredSurfaces = immutableListSnapshot(r.requiredSurfaces), dependsOnRowIds = immutableListSnapshot(r.dependsOnRowIds), restoreScope = immutableListSnapshot(r.restoreScope), invalidationTriggers = immutableListSnapshot(r.invalidationTriggers))
private fun snapshotEvent(e: LedgerEvent) = e.copy(discoveryEvidenceRefs = immutableListSnapshot(e.discoveryEvidenceRefs), observations = immutableListSnapshot(e.observations), clearEvidenceRefs = immutableListSnapshot(e.clearEvidenceRefs), restoreEvidenceRefs = immutableListSnapshot(e.restoreEvidenceRefs))

object LedgerSemanticValidator {
    const val BRANCH_COUNT = 26
    fun validate(fixture: LedgerFixture) = ValidatedLedgerSnapshot.validated(fixture)
    fun checkedSessionId(packSha256: String, nonceSha256: String, startElapsedMs: Long): String { ExpansionIds.sha256(packSha256); ExpansionIds.sha256(nonceSha256); require(startElapsedMs >= 0); val hash = CanonicalJson.digest(CanonicalJson.obj("packSha256" to JsonText(packSha256), "sessionNonceSha256" to JsonText(nonceSha256), "sessionStartElapsedMs" to JsonInteger(startElapsedMs))); return "SESSION-${hash.take(16).uppercase()}" }
    fun checkedSessionInstanceSha256(freeze: SessionPackFreeze) = CanonicalJson.digest(freeze.json(false))
    fun candidateSetSha256(candidates: List<CandidateRevision>, ids: List<String>): String { val byId = candidates.associateBy { it.candidateRevisionId }; return CanonicalJson.digest(CanonicalJson.array(ids.map { id -> val c = requireNotNull(byId[id]); CanonicalJson.obj("candidateRevisionId" to JsonText(id), "revisionSha256" to JsonText(c.revisionSha256)) })) }
    internal fun branchIndex(event: LedgerEvent) = validateBranch(event)
}

private fun validateFixture(f: LedgerFixture) {
    validateTemplateAndFreeze(f); val byRow = f.rows.associateBy { it.rowId }; require(byRow.size == f.rows.size)
    require(f.events.isNotEmpty()); f.events.forEachIndexed { n, e ->
        require(e.sequence == n + 1 && e.eventId == "EVENT-${(n + 1).toString().padStart(6, '0')}" && e.sessionId == f.freeze.sessionId && e.exactIdentity == f.freeze.exactIdentity && e.elapsedOffsetMs >= 0)
        require(e.previousEventHash == f.events.getOrNull(n - 1)?.eventSha256 && e.eventSha256 == e.computedSha256()); if (n > 0) require(e.elapsedOffsetMs >= f.events[n - 1].elapsedOffsetMs)
        validateBranch(e); validateForeignKeys(e, byRow); validateEvidenceTargets(e, f)
    }
    require(f.events.map { it.eventSha256 }.distinct().size == f.events.size && f.events.first().eventType == LedgerEventType.SESSION_START)
    val terminals = linkedMapOf<String, LedgerEvent>()
    val pendingPrunes = mutableMapOf<String, String>()
    var cursor = 1
    var truncated = false
    f.rows.forEach { row ->
        val start = cursor
        while (cursor < f.events.size && f.events[cursor].rowId == row.rowId) cursor++
        require(cursor > start)
        val group = f.events.subList(start, cursor)
        val terminal = group.last()
        val pruneCause = pendingPrunes.remove(row.rowId)
        when {
            pruneCause != null -> require(group.size == 1 && terminal.eventType == LedgerEventType.PRUNED &&
                terminal.ruleId == "RULE-PRUNE-FORWARD-FOREST" && terminal.causalEventHash == pruneCause)
            truncated -> require(group.size == 1 && terminal.eventType == LedgerEventType.SKIPPED &&
                terminal.ruleId == "RULE-BUDGET-AFTER-FIRST-NONFIT")
            else -> {
                val failedDependency = row.dependsOnRowIds.firstOrNull { terminals[it]?.eventType != LedgerEventType.PASS }
                if (failedDependency != null) {
                    require(group.size == 1 && terminal.eventType == LedgerEventType.BLOCKED &&
                        terminal.reasonId == "REASON-BLOCKED-DEPENDENCY" &&
                        terminal.ruleId == "RULE-BLOCKED-DEPENDENCY" &&
                        terminal.causalEventHash == terminals.getValue(failedDependency).eventSha256)
                } else if (group.size == 1 && terminal.eventType == LedgerEventType.SKIPPED) {
                    require(terminal.ruleId == "RULE-BUDGET-FIRST-NONFIT" &&
                        !OverflowSafeBudgetScheduler.fits(terminal.elapsedOffsetMs, f.freeze.budgetMs, row.estimatedTimeMs))
                    truncated = true
                } else {
                    require(group.first().eventType == LedgerEventType.PRECONDITION &&
                        OverflowSafeBudgetScheduler.fits(group.first().elapsedOffsetMs, f.freeze.budgetMs, row.estimatedTimeMs))
                }
            }
        }
        validateLifecycle(row, group, terminals, f)
        terminals[row.rowId] = terminal
        if (terminal.eventType == LedgerEventType.PASS && row.kind == SessionRowKind.MUTATION) {
            pruneClosure(f.candidates, f.rows.map { it.template() }, row.rowId).forEach { target ->
                require(target !in terminals && pendingPrunes.put(target, terminal.eventSha256) == null)
            }
        }
    }
    require(pendingPrunes.isEmpty())
    val review = f.events.drop(cursor); require(review.all { it.eventType == LedgerEventType.INVALIDATED }); validateReviewTail(review, f)
}

private fun validateTemplateAndFreeze(f: LedgerFixture) {
    val t = f.template; val m = t.model; val z = f.freeze; listOf(t.templateFileSha256, t.coverageFileSha256, t.evidenceMapFileSha256, t.registryFileSha256, t.packSha256, f.sessionNonceSha256, z.sessionInstanceSha256).forEach(ExpansionIds::sha256)
    CandidateRevisionChains.validate(f.candidates); require(m.revision >= 1 && m.budgetMs in 0..86_400_000 && m.rows.isNotEmpty() && m.rows == m.rows.sortedWith(SessionRowOrdering.comparator)); RollbackContracts.validate(m.rows)
    val latest = f.candidates.groupBy { ExpansionIds.candidate(it.candidateRevisionId).baseId }.values.map { it.maxBy(CandidateRevision::revision) }
    val expected = latest.filter { (it.input.mode == CandidateMode.READ_ONLY && it.state == CandidateState.READ_ONLY_READY) || (it.input.mode == CandidateMode.MUTATION && it.state == CandidateState.READY_FOR_FIELD) }.map { it.candidateRevisionId }.sortedWith(ExpansionIds.candidateComparator)
    val candidateRows = m.rows.filter { it.candidateRevisionId != null }; val rowByCandidate = candidateRows.associateBy { it.candidateRevisionId!! }; require(rowByCandidate.size == candidateRows.size)
    require(m.allowedCandidateRevisionIds == expected && m.allowedCandidateRevisionIds == candidateRows.mapNotNull { it.candidateRevisionId }.sortedWith(ExpansionIds.candidateComparator))
    require(m.allowedMutationCandidateRevisionIds == m.rows.filter { it.kind == SessionRowKind.MUTATION }.map { it.candidateRevisionId!! }.sortedWith(ExpansionIds.candidateComparator))
    require(m.allowedProbeIds == m.rows.filter { it.kind == SessionRowKind.READ_ONLY }.flatMap { it.probeIds }.distinct().sorted())
    val positions = m.rows.mapIndexed { n, r -> r.rowId to n }.toMap(); require(positions.size == m.rows.size)
    m.rows.forEachIndexed { n, r -> require(r.dependencyReady && r.dependsOnRowIds.all { positions.getValue(it) < n }); if (r.candidateRevisionId == null) require(r.dependsOnRowIds.isEmpty()) else validateCandidateRow(r, f.candidates.associateBy { it.candidateRevisionId }.getValue(r.candidateRevisionId), rowByCandidate) }
    val results = m.rows.filter { it.kind == SessionRowKind.MILESTONE }.map { it.resultIdentityId!! }
    val requiredResults = listOf("RESULT-D-M1-0001", "RESULT-D-M2-0001", "RESULT-D-M3-0001", "RESULT-D-M4-0001")
    require(results.filter { it.startsWith("RESULT-D-M") } == requiredResults &&
        results.count { it.startsWith("RESULT-D-H0-") } <= 1 && results.distinct().size == results.size &&
        t.resolvedExactIdentity.variant != IdentityVariant.RELEASE)
    require(t.registryFileSha256 == t.resolvedExactIdentity.registryFileSha256 && t.packSha256 == t.resolvedExactIdentity.packSha256 && t.resolvedExactIdentity.candidateSetSha256 == LedgerSemanticValidator.candidateSetSha256(f.candidates, m.allowedCandidateRevisionIds))
    require(z.templateFileSha256 == t.templateFileSha256 && z.exactIdentity == t.resolvedExactIdentity && z.revision == m.revision && z.budgetMs == m.budgetMs && z.allowedProbeIds == m.allowedProbeIds && z.allowedCandidateRevisionIds == m.allowedCandidateRevisionIds && z.allowedMutationCandidateRevisionIds == m.allowedMutationCandidateRevisionIds)
    require(z.sessionStartElapsedMs >= 0 && z.deadlineElapsedMs == OverflowSafeBudgetScheduler.checkedDeadline(z.sessionStartElapsedMs, z.budgetMs)); require(Regex(ExpansionIds.SESSION_PATTERN).matches(z.sessionId) && z.sessionId == LedgerSemanticValidator.checkedSessionId(z.exactIdentity.packSha256, f.sessionNonceSha256, z.sessionStartElapsedMs)); require(z.sessionInstanceSha256 == LedgerSemanticValidator.checkedSessionInstanceSha256(z))
    require(f.rows.size == m.rows.size); f.rows.zip(m.rows).forEach { (r, source) -> require(r.exactIdentity == z.exactIdentity && r.template() == source && r.riskClass == source.riskClass && r.score == source.score) }
    ForwardPruneForest.create(f.candidates, m.rows)
}
private fun validateCandidateRow(r: SessionRowTemplate, c: CandidateRevision, rows: Map<String, SessionRowTemplate>) {
    val p = c.input.proof; require(r.requiredSurfaces == c.input.requiredSurfaces && r.dependsOnRowIds == c.input.dependsOnRevisionIds.map { rows.getValue(it).rowId }); val q = c.input.planningProfile
    require(listOf(r.phaseRank, r.evidenceStrength, r.informationGain, r.reversibility, r.mutationRisk, r.dependencyUncertainty) == listOf(q.phaseRank, q.evidenceStrength, q.informationGain, q.reversibility, q.mutationRisk, q.dependencyUncertainty) && r.estimatedTimeMs == q.estimatedTimeMs)
    when (r.kind) { SessionRowKind.READ_ONLY -> require(c.input.mode == CandidateMode.READ_ONLY && c.state == CandidateState.READ_ONLY_READY && r.probeIds == listOf(p.readProbeId)); SessionRowKind.MUTATION -> require(c.input.mode == CandidateMode.MUTATION && c.state == CandidateState.READY_FOR_FIELD && r.mutationOperationId == p.mutationOperationId && r.clearOperationId == p.clearOperationId && r.inverseOperationIds == p.inverseOperationIds && r.observations == c.input.requiredObservationIds && r.restoreScope == c.input.restoreScope && r.invalidationTriggers == c.input.invalidationTriggers); else -> error("candidate milestone") }
}

private val normalTerminals = setOf(LedgerEventType.PASS, LedgerEventType.FAIL, LedgerEventType.INCONCLUSIVE)
private fun validateLifecycle(row: SessionRow, g: List<LedgerEvent>, terminals: Map<String, LedgerEvent>, f: LedgerFixture) {
    if (g.size == 1) { val e = g.single(); require(e.eventType == LedgerEventType.SKIPPED || e.eventType == LedgerEventType.PRUNED || (e.eventType == LedgerEventType.BLOCKED && e.reasonId == "REASON-BLOCKED-DEPENDENCY")); validateNeverStarted(row, e, terminals, f); return }
    val pre = g.first(); require(pre.eventType == LedgerEventType.PRECONDITION)
    if (pre.preconditionOutcome == LedgerPhaseOutcome.FAIL) { require(g.size == 2 && g[1].eventType == LedgerEventType.BLOCKED && g[1].reasonId == "REASON-BLOCKED-PRECONDITION" && g[1].causalEventHash == pre.eventSha256); return }
    require(pre.preconditionOutcome == LedgerPhaseOutcome.PASS)
    when (row.kind) {
        SessionRowKind.READ_ONLY -> { require(g.last().eventType in normalTerminals && g.drop(1).dropLast(1).all { it.eventType == LedgerEventType.DISCOVERY_ONLY && it.causalEventHash == pre.eventSha256 }) }
        SessionRowKind.MILESTONE -> require(g.size == 3 && g[1].eventType == LedgerEventType.OBSERVATION && g[2].eventType in normalTerminals)
        SessionRowKind.MUTATION -> { require(g.size == 6 && g.drop(1).map { it.eventType }.take(4) == listOf(LedgerEventType.MUTATION, LedgerEventType.OBSERVATION, LedgerEventType.CLEAR, LedgerEventType.RESTORE)); val mutation = g[1]; val clear = g[3]; val restore = g[4]; val end = g[5]; require(end.observations == row.observations && end.clearEvidenceRefs == clear.clearEvidenceRefs && end.restoreEvidenceRefs == restore.restoreEvidenceRefs); if (restore.restoreOutcome == LedgerPhaseOutcome.FAIL) require(end.eventType == LedgerEventType.BLOCKED && end.reasonId == "REASON-BLOCKED-RESTORE" && end.causalEventHash == restore.eventSha256 && end.restoreEvidenceRefs == restore.restoreEvidenceRefs) else { require(restore.restoreOutcome == LedgerPhaseOutcome.PASS && end.eventType in normalTerminals); if (mutation.mutationOutcome == LedgerPhaseOutcome.FAIL || clear.clearOutcome == LedgerPhaseOutcome.FAIL) require(end.eventType == LedgerEventType.FAIL); if (end.eventType == LedgerEventType.PASS) require(mutation.mutationOutcome == LedgerPhaseOutcome.PASS && clear.clearOutcome == LedgerPhaseOutcome.PASS) } }
    }
}
private fun validateNeverStarted(row: SessionRow, e: LedgerEvent, terminals: Map<String, LedgerEvent>, f: LedgerFixture) {
    when (e.eventType) {
        LedgerEventType.SKIPPED -> require(e.causalEventHash == e.previousEventHash)
        LedgerEventType.BLOCKED -> { val failed = row.dependsOnRowIds.firstOrNull { terminals[it]?.eventType != LedgerEventType.PASS }; require(failed != null && e.causalEventHash == terminals.getValue(failed).eventSha256) }
        LedgerEventType.PRUNED -> { val cause = f.events.take(e.sequence - 1).singleOrNull { it.eventSha256 == e.causalEventHash }; require(cause?.eventType == LedgerEventType.PASS && cause.rowKind == SessionRowKind.MUTATION); val source = f.rows.single { it.rowId == cause.rowId }; require(row.rowId in pruneClosure(f.candidates, f.rows.map { it.template() }, source.rowId)) }
        else -> error("not never-started")
    }
}

private fun validateReviewTail(review: List<LedgerEvent>, f: LedgerFixture) {
    require(review.map { it.candidateRevisionId!! } == review.map { it.candidateRevisionId!! }.sortedWith(ExpansionIds.candidateComparator) && review.map { it.candidateRevisionId }.distinct().size == review.size)
    require(f.proposalDispositions == f.proposalDispositions.sortedBy { it.proposalEventSha256 } && f.proposalDispositions.size == review.size)
    val current = f.candidates.associateBy { it.candidateRevisionId }; val accepted = mutableListOf<CandidateRevision>()
    review.forEach { e -> val target = requireNotNull(current[e.candidateRevisionId]); val proposal = requireNotNull(e.proposedTombstone); val candidate = proposal.candidate; require(proposal.targetCandidateRevisionId == target.candidateRevisionId && proposal.invalidationReasonId == e.invalidationReasonId && e.invalidationReasonId in target.input.invalidatesOn && e.tombstoneRevisionId == candidate.candidateRevisionId); val key = ExpansionIds.candidate(target.candidateRevisionId); val next = ExpansionIds.candidate(candidate.candidateRevisionId); require(key.baseId == next.baseId && next.revision == key.revision + 1 && candidate.input.predecessorCandidateSha256 == target.revisionSha256 && candidate.state == CandidateState.REJECTED && candidate.candidateRevisionId !in current && candidate.candidateRevisionId !in f.freeze.allowedCandidateRevisionIds)
        val discovery = f.events.filter { it.sequence < e.sequence && it.eventType == LedgerEventType.DISCOVERY_ONLY && it.discoveryEvidenceRefs.any { ref -> ref.targetCandidateRevisionId == target.candidateRevisionId && ref.suggestedInvalidationReasonId == e.invalidationReasonId } }.minByOrNull { it.sequence }; require(discovery != null && e.causalEventHash == discovery.eventSha256)
        val d = f.proposalDispositions.single { it.proposalEventSha256 == e.eventSha256 }; ExpansionIds.sha256(d.proposedTombstoneRevisionSha256); require(d.proposedTombstoneRevisionSha256 == candidate.revisionSha256); if (d.disposition == ProposalDecision.ACCEPTED) { require(d.reasonId == null); accepted += candidate } else ExpansionIds.proposalReason(requireNotNull(d.reasonId))
    }
    val expected = (f.candidates + accepted).sortedWith(compareBy(ExpansionIds.candidateComparator) { it.candidateRevisionId }); require(f.nextRegistryCandidates.map { it.canonicalJson() } == expected.map { it.canonicalJson() }); CandidateRevisionChains.validate(f.nextRegistryCandidates)
}

private fun validateForeignKeys(e: LedgerEvent, rows: Map<String, SessionRow>) {
    if (e.eventType in setOf(LedgerEventType.SESSION_START, LedgerEventType.INVALIDATED)) return
    val r = requireNotNull(rows[e.rowId]); require(e.rowKind == r.kind)
    if (e.eventType in setOf(LedgerEventType.MUTATION, LedgerEventType.OBSERVATION, LedgerEventType.CLEAR, LedgerEventType.RESTORE, LedgerEventType.PASS, LedgerEventType.FAIL, LedgerEventType.INCONCLUSIVE, LedgerEventType.SKIPPED, LedgerEventType.PRUNED) || e.reasonId == "REASON-BLOCKED-RESTORE") require(e.candidateRevisionId == r.candidateRevisionId)
    if (e.eventType == LedgerEventType.PRECONDITION || e.eventType == LedgerEventType.DISCOVERY_ONLY) require(e.probeId == if (r.kind == SessionRowKind.READ_ONLY) r.probeIds.single() else null)
    if (e.eventType == LedgerEventType.MUTATION) require(e.mutationOperationId == r.mutationOperationId)
    if (e.eventType == LedgerEventType.OBSERVATION || (e.eventType in normalTerminals && r.kind != SessionRowKind.READ_ONLY)) require(e.observations == r.observations)
    if ((e.eventType == LedgerEventType.OBSERVATION || e.eventType in normalTerminals) && r.kind == SessionRowKind.MILESTONE) require(e.resultIdentityId == r.resultIdentityId)
    if (e.eventType == LedgerEventType.CLEAR) require(e.clearEvidenceRefs.isNotEmpty() && e.clearEvidenceRefs.all { it.operationId == r.clearOperationId && it.targetCandidateRevisionId == null && it.suggestedInvalidationReasonId == null })
    if (e.eventType == LedgerEventType.RESTORE) require(e.restoreEvidenceRefs.map { it.operationId } == r.inverseOperationIds && e.restoreEvidenceRefs.all { it.targetCandidateRevisionId == null && it.suggestedInvalidationReasonId == null })
    if (e.eventType == LedgerEventType.DISCOVERY_ONLY) require(e.probeId in r.probeIds && e.discoveryEvidenceRefs.all { it.operationId == null && ((it.targetCandidateRevisionId == null) == (it.suggestedInvalidationReasonId == null)) })
}

private fun validateBranch(e: LedgerEvent): Int {
    validateEventFormats(e); fun only(vararg allowed: String) { val a = allowed.toSet(); val present = buildSet { if (e.candidateRevisionId != null) add("candidate"); if (e.causalEventHash != null) add("causal"); if (e.clearEvidenceRefs.isNotEmpty()) add("clearRefs"); if (e.clearOutcome != null) add("clearOutcome"); if (e.discoveryEvidenceRefs.isNotEmpty()) add("discoveryRefs"); if (e.invalidationReasonId != null) add("invalidation"); if (e.mutationOperationId != null) add("mutationOp"); if (e.mutationOutcome != null) add("mutationOutcome"); if (e.observations.isNotEmpty()) add("observations"); if (e.preconditionOutcome != null) add("precondition"); if (e.probeId != null) add("probe"); if (e.proposedTombstone != null) add("proposal"); if (e.reasonId != null) add("reason"); if (e.restoreEvidenceRefs.isNotEmpty()) add("restoreRefs"); if (e.restoreOutcome != null) add("restoreOutcome"); if (e.result != null) add("result"); if (e.resultIdentityId != null) add("resultId"); if (e.rowId != null) add("row"); if (e.rowKind != null) add("kind"); if (e.ruleId != null) add("rule"); if (e.tombstoneRevisionId != null) add("tombstone") }; require(present.all { it in a }) }
    return when (e.eventType) {
        LedgerEventType.SESSION_START -> { only(); require(e.sequence == 1 && e.previousEventHash == null && e.elapsedOffsetMs == 0L); 1 }
        LedgerEventType.DISCOVERY_ONLY -> { only("row","kind","probe","discoveryRefs","causal"); require(e.rowKind == SessionRowKind.READ_ONLY && e.rowId != null && e.probeId != null && e.discoveryEvidenceRefs.isNotEmpty() && e.causalEventHash != null); 2 }
        LedgerEventType.PRECONDITION -> { only("row","kind","probe","precondition"); require(e.rowId != null && e.rowKind != null && e.preconditionOutcome != null && (e.probeId != null) == (e.rowKind == SessionRowKind.READ_ONLY)); when (e.rowKind) { SessionRowKind.READ_ONLY -> 3; SessionRowKind.MILESTONE -> 4; SessionRowKind.MUTATION -> 5 } }
        LedgerEventType.MUTATION -> { only("row","kind","candidate","mutationOp","mutationOutcome"); require(e.rowKind == SessionRowKind.MUTATION && e.rowId != null && e.candidateRevisionId != null && e.mutationOperationId != null && e.mutationOutcome != null); 6 }
        LedgerEventType.OBSERVATION -> { only("row","kind","candidate","resultId","observations"); require(e.rowId != null && e.observations.isNotEmpty()); if (e.rowKind == SessionRowKind.MUTATION) { require(e.candidateRevisionId != null && e.resultIdentityId == null); 7 } else { require(e.rowKind == SessionRowKind.MILESTONE && e.candidateRevisionId == null && e.resultIdentityId != null); 8 } }
        LedgerEventType.CLEAR -> { only("row","kind","candidate","clearRefs","clearOutcome"); require(e.rowKind == SessionRowKind.MUTATION && e.rowId != null && e.candidateRevisionId != null && e.clearEvidenceRefs.isNotEmpty() && e.clearOutcome != null); 9 }
        LedgerEventType.RESTORE -> { only("row","kind","candidate","restoreRefs","restoreOutcome"); require(e.rowKind == SessionRowKind.MUTATION && e.rowId != null && e.candidateRevisionId != null && e.restoreEvidenceRefs.isNotEmpty() && e.restoreOutcome != null); 10 }
        LedgerEventType.PASS, LedgerEventType.FAIL, LedgerEventType.INCONCLUSIVE -> { only("row","kind","candidate","resultId","observations","clearRefs","restoreRefs","result","reason"); val expected = LedgerResult.valueOf(e.eventType.name); require(e.rowId != null && e.rowKind != null && e.result == expected && (e.reasonId != null) == (e.eventType != LedgerEventType.PASS)); when (e.rowKind) { SessionRowKind.MUTATION -> { require(e.candidateRevisionId != null && e.resultIdentityId == null && e.observations.isNotEmpty() && e.clearEvidenceRefs.isNotEmpty() && e.restoreEvidenceRefs.isNotEmpty()); if (e.eventType == LedgerEventType.PASS) 11 else if (e.eventType == LedgerEventType.FAIL) 14 else 17 }; SessionRowKind.READ_ONLY -> { require(e.resultIdentityId == null && e.observations.isEmpty() && e.clearEvidenceRefs.isEmpty() && e.restoreEvidenceRefs.isEmpty()); if (e.eventType == LedgerEventType.PASS) 12 else if (e.eventType == LedgerEventType.FAIL) 15 else 18 }; SessionRowKind.MILESTONE -> { require(e.candidateRevisionId == null && e.resultIdentityId != null && e.observations.isNotEmpty() && e.clearEvidenceRefs.isEmpty() && e.restoreEvidenceRefs.isEmpty()); if (e.eventType == LedgerEventType.PASS) 13 else if (e.eventType == LedgerEventType.FAIL) 16 else 19 } } }
        LedgerEventType.SKIPPED -> { only("row","kind","candidate","reason","rule","causal"); require(e.rowId != null && e.rowKind != null && e.reasonId == "REASON-SKIPPED-TIME-BUDGET" && e.ruleId != null && e.causalEventHash != null); 20 }
        LedgerEventType.PRUNED -> { only("row","kind","candidate","reason","rule","causal"); require(e.rowId != null && e.rowKind == SessionRowKind.MUTATION && e.candidateRevisionId != null && e.reasonId == "REASON-PRUNED-SUBSUMED" && e.ruleId != null && e.causalEventHash != null); 21 }
        LedgerEventType.BLOCKED -> when (e.reasonId) { "REASON-BLOCKED-RESTORE" -> { only("row","kind","candidate","reason","rule","causal","restoreRefs"); require(e.rowId != null && e.rowKind == SessionRowKind.MUTATION && e.candidateRevisionId != null && e.ruleId != null && e.causalEventHash != null && e.restoreEvidenceRefs.isNotEmpty()); 22 }; "REASON-BLOCKED-PRECONDITION" -> { only("row","kind","probe","reason","rule","causal","precondition"); require(e.rowId != null && e.rowKind != null && e.ruleId != null && e.causalEventHash != null && e.preconditionOutcome == LedgerPhaseOutcome.FAIL && (e.probeId != null) == (e.rowKind == SessionRowKind.READ_ONLY)); if (e.rowKind == SessionRowKind.READ_ONLY) 23 else 24 }; "REASON-BLOCKED-DEPENDENCY" -> { only("row","kind","reason","rule","causal"); require(e.rowId != null && e.rowKind != null && e.ruleId != null && e.causalEventHash != null); 25 }; else -> error("blocked branch") }
        LedgerEventType.INVALIDATED -> { only("candidate","causal","invalidation","proposal","tombstone"); require(e.candidateRevisionId != null && e.causalEventHash != null && e.invalidationReasonId != null && e.proposedTombstone != null && e.tombstoneRevisionId != null); 26 }
    }
}
private fun validateEvidenceTargets(e: LedgerEvent, f: LedgerFixture) {
    if (e.eventType != LedgerEventType.DISCOVERY_ONLY) return
    val candidates = f.candidates.associateBy { it.candidateRevisionId }
    e.discoveryEvidenceRefs.forEach { ref ->
        if (ref.targetCandidateRevisionId != null) {
            require(ref.targetCandidateRevisionId in f.freeze.allowedCandidateRevisionIds)
            val target = candidates.getValue(ref.targetCandidateRevisionId)
            require(ref.suggestedInvalidationReasonId in target.input.invalidatesOn)
        }
    }
}

private fun validateEventFormats(e: LedgerEvent) { require(e.sequence >= 1 && Regex(ExpansionIds.EVENT_PATTERN).matches(e.eventId) && Regex(ExpansionIds.SESSION_PATTERN).matches(e.sessionId)); listOfNotNull(e.previousEventHash, e.causalEventHash, e.eventSha256).forEach(ExpansionIds::sha256); listOfNotNull(e.candidateRevisionId, e.tombstoneRevisionId).forEach { ExpansionIds.candidate(it) }; e.rowId?.let { require(Regex(ExpansionIds.ROW_PATTERN).matches(it)) }; e.resultIdentityId?.let { require(Regex(ExpansionIds.RESULT_PATTERN).matches(it)) }; e.mutationOperationId?.let(ExpansionIds::operation); e.probeId?.let(ExpansionIds::probe); listOfNotNull(e.reasonId, e.invalidationReasonId).forEach(ExpansionIds::reason); e.ruleId?.let { require(Regex("^RULE-[A-Z0-9][A-Z0-9-]{0,63}$").matches(it)) }; e.observations.forEach(ExpansionIds::observation); e.discoveryEvidenceRefs.plus(e.clearEvidenceRefs).plus(e.restoreEvidenceRefs).forEach { r -> ExpansionIds.fact(r.evidenceId); r.operationId?.let(ExpansionIds::operation); r.targetCandidateRevisionId?.let { ExpansionIds.candidate(it) }; r.suggestedInvalidationReasonId?.let(ExpansionIds::reason) } }

private fun pruneClosure(candidates: List<CandidateRevision>, rows: List<SessionRowTemplate>, sourceRowId: String): List<String> { val byCandidate = rows.filter { it.candidateRevisionId != null }.associateBy { it.candidateRevisionId!! }; val byId = candidates.associateBy { it.candidateRevisionId }; val source = rows.single { it.rowId == sourceRowId }.candidateRevisionId ?: return emptyList(); val seen = linkedSetOf<String>(); fun visit(id: String) { byId.getValue(id).input.subsumes.forEach { if (seen.add(it)) visit(it) } }; visit(source); val order = rows.mapIndexed { n, r -> r.rowId to n }.toMap(); return seen.map { byCandidate.getValue(it).rowId }.sortedBy { order.getValue(it) } }

enum class MutationAttemptOutcome { PASS, FAIL, PARTIAL, THROW }
enum class RestoreAttemptOutcome { PASS, FAIL }
enum class RollbackFrameState { CAPTURED, ARMED, ATTEMPTED, CLEARED, RESTORE_FAILED }
data class RollbackFrame(val stackIndex: Int, val inverseOperationIds: List<String>, val state: RollbackFrameState, val mutationOutcome: MutationAttemptOutcome? = null)
data class EmergencyRecoveryStep(val stackIndex: Int, val inverseOperationIds: List<String>, val outcome: RestoreAttemptOutcome)
data class EmergencyRecovery(val steps: List<EmergencyRecoveryStep>, val stack: RollbackStack)
class RollbackStack private constructor(val frames: List<RollbackFrame>, val captured: RollbackFrame?) {
    fun capture(row: SessionRowTemplate, exceptional: Boolean = false): RollbackStack { require(row.kind == SessionRowKind.MUTATION && captured == null); if (!exceptional) require(frames.isEmpty()) { "next normal mutation rejected while rollback is armed" }; val frame = RollbackFrame(row.rollbackStackIndex!!, immutableListSnapshot(row.inverseOperationIds), RollbackFrameState.CAPTURED); require(frames.none { it.stackIndex == frame.stackIndex } && (frames.isEmpty() || frame.stackIndex > frames.maxOf { it.stackIndex })); return RollbackStack(frames, frame) }
    fun arm(): RollbackStack { val frame = requireNotNull(captured); return RollbackStack(immutableListSnapshot(frames + frame.copy(state = RollbackFrameState.ARMED)), null) }
    fun mutation(outcome: MutationAttemptOutcome): RollbackStack = updateTop { require(it.state == RollbackFrameState.ARMED); it.copy(state = RollbackFrameState.ATTEMPTED, mutationOutcome = outcome) }
    fun clear(): RollbackStack = updateTop { require(it.state == RollbackFrameState.ATTEMPTED && it.mutationOutcome != null); it.copy(state = RollbackFrameState.CLEARED) }
    fun restore(stackIndex: Int, outcome: RestoreAttemptOutcome): RollbackStack { val top = frames.lastOrNull() ?: error("empty rollback stack"); require(captured == null && top.stackIndex == stackIndex && top.mutationOutcome != null); return if (outcome == RestoreAttemptOutcome.PASS) RollbackStack(immutableListSnapshot(frames.dropLast(1)), null) else updateTop { it.copy(state = RollbackFrameState.RESTORE_FAILED) } }
    fun emergencyRecover(outcomes: Map<Int, RestoreAttemptOutcome>): EmergencyRecovery { require(captured == null && outcomes.keys == frames.map { it.stackIndex }.toSet()); val steps = frames.sortedByDescending { it.stackIndex }.map { require(it.mutationOutcome != null); EmergencyRecoveryStep(it.stackIndex, it.inverseOperationIds, outcomes.getValue(it.stackIndex)) }; val retained = frames.filter { outcomes.getValue(it.stackIndex) == RestoreAttemptOutcome.FAIL }.map { it.copy(state = RollbackFrameState.RESTORE_FAILED) }; return EmergencyRecovery(immutableListSnapshot(steps), RollbackStack(immutableListSnapshot(retained), null)) }
    private fun updateTop(block: (RollbackFrame) -> RollbackFrame): RollbackStack { require(captured == null && frames.isNotEmpty()); return RollbackStack(immutableListSnapshot(frames.dropLast(1) + block(frames.last())), null) }
    companion object { fun empty() = RollbackStack(emptyList(), null) }
}
object RollbackContracts { fun validate(rows: List<SessionRowTemplate>) { val m = rows.filter { it.kind == SessionRowKind.MUTATION }; require(m.map { it.rollbackStackIndex } == m.indices.toList() && m.all { it.inverseOperationIds.isNotEmpty() }) } }

enum class SchedulerDisposition { START, SKIPPED_FIRST_NONFIT, SKIPPED_AFTER_NONFIT, PRUNED, BLOCKED_DEPENDENCY }
data class SchedulerDecision(val rowId: String, val disposition: SchedulerDisposition, val elapsedOffsetMs: Long?, val ruleId: String?, val causalEventHash: String?)
object OverflowSafeBudgetScheduler {
    fun checkedDeadline(startElapsedMs: Long, budgetMs: Long): Long { require(startElapsedMs >= 0 && budgetMs in 0..86_400_000); return Math.addExact(startElapsedMs, budgetMs) }
    fun elapsedOffset(now: Long, start: Long): Long { require(now >= 0 && start >= 0); return if (now <= start) 0 else Math.subtractExact(now, start) }
    fun fits(sample: Long, budget: Long, estimate: Long): Boolean { require(sample >= 0 && budget in 0..86_400_000 && estimate in 0..86_400_000); return estimate <= budget && sample <= budget - estimate }
    fun schedule(snapshot: ValidatedLedgerSnapshot, elapsedSamples: List<Long>): List<SchedulerDecision> { require(elapsedSamples.all { it >= 0 }); val passed = snapshot.passRecords.associateBy { it.rowId }; val pruned = snapshot.rows.filter { snapshot.terminalEvent(it.rowId).eventType == LedgerEventType.PRUNED }.associate { it.rowId to snapshot.terminalEvent(it.rowId) }; var sample = 0; var truncated = false
        val decisions = snapshot.rows.map { row -> when { row.rowId in pruned -> { val e = pruned.getValue(row.rowId); SchedulerDecision(row.rowId, SchedulerDisposition.PRUNED, null, "RULE-PRUNE-FORWARD-FOREST", e.causalEventHash) }; truncated -> SchedulerDecision(row.rowId, SchedulerDisposition.SKIPPED_AFTER_NONFIT, elapsedSamples.getOrElse(sample++) { error("missing elapsed sample") }, "RULE-BUDGET-AFTER-FIRST-NONFIT", snapshot.eventBeforeRow(row.rowId).eventSha256); row.dependsOnRowIds.any { it !in passed } -> { val failed = row.dependsOnRowIds.first { it !in passed }; SchedulerDecision(row.rowId, SchedulerDisposition.BLOCKED_DEPENDENCY, null, "RULE-BLOCKED-DEPENDENCY", snapshot.terminalEvent(failed).eventSha256) }; else -> { val at = elapsedSamples.getOrElse(sample++) { error("missing elapsed sample") }; if (fits(at, snapshot.freeze.budgetMs, row.estimatedTimeMs)) SchedulerDecision(row.rowId, SchedulerDisposition.START, at, null, null) else { truncated = true; SchedulerDecision(row.rowId, SchedulerDisposition.SKIPPED_FIRST_NONFIT, at, "RULE-BUDGET-FIRST-NONFIT", snapshot.eventBeforeRow(row.rowId).eventSha256) } } } }; require(sample == elapsedSamples.size); return decisions }
}

data class PruneMark(val rowId: String, val causalEventHash: String)
class ForwardPruneForest private constructor(private val rowByCandidate: Map<String, SessionRowTemplate>, private val children: Map<String, List<String>>, private val order: Map<String, Int>, private val sessionInstanceSha256: String?) {
    fun qualifyingClosure(pass: ValidatedPassRecord): List<PruneMark> { require(sessionInstanceSha256 != null && pass.sessionInstanceSha256 == sessionInstanceSha256); val row = rowByCandidate.values.singleOrNull { it.rowId == pass.rowId } ?: return emptyList(); if (row.kind != SessionRowKind.MUTATION || row.candidateRevisionId != pass.candidateRevisionId || pass.observations != row.observations) return emptyList(); val seen = linkedSetOf<String>(); fun visit(id: String) { children[id].orEmpty().forEach { if (seen.add(it)) visit(it) } }; visit(row.candidateRevisionId!!); return seen.map(rowByCandidate::getValue).sortedBy { order.getValue(it.rowId) }.map { PruneMark(it.rowId, pass.causalEventHash) } }
    companion object {
        fun create(candidates: List<CandidateRevision>, rows: List<SessionRowTemplate>) = build(candidates, rows, null)
        fun create(snapshot: ValidatedLedgerSnapshot) = build(snapshot.candidateRevisions, snapshot.rows.map { it.template() }, snapshot.freeze.sessionInstanceSha256)
        private fun build(candidates: List<CandidateRevision>, rows: List<SessionRowTemplate>, session: String?): ForwardPruneForest { val byId = candidates.associateBy { it.candidateRevisionId }; require(byId.size == candidates.size); val candidateRows = rows.filter { it.candidateRevisionId != null }; val byCandidate = candidateRows.associateBy { it.candidateRevisionId!! }; require(byCandidate.size == candidateRows.size); val frozenCandidates = byCandidate.keys.map { requireNotNull(byId[it]) }; val order = rows.mapIndexed { n, r -> r.rowId to n }.toMap(); require(order.size == rows.size); val incoming = mutableMapOf<String, Int>()
            frozenCandidates.forEach { source -> if (source.input.mode != CandidateMode.MUTATION) require(source.input.pruneGroup == null && source.input.subsumes.isEmpty()); source.input.subsumes.forEach { targetId -> val target = requireNotNull(byId[targetId]); require(source.input.mode == CandidateMode.MUTATION && target.input.mode == CandidateMode.MUTATION && source.input.pruneGroup != null && source.input.pruneGroup == target.input.pruneGroup && source.input.milestone == target.input.milestone && source.input.hypothesisId == target.input.hypothesisId && source.input.requiredSurfaces == target.input.requiredSurfaces && source.input.mutationDimension == target.input.mutationDimension); require(order.getValue(byCandidate.getValue(source.candidateRevisionId).rowId) < order.getValue(byCandidate.getValue(targetId).rowId)); incoming[targetId] = (incoming[targetId] ?: 0) + 1; require(incoming.getValue(targetId) <= 1) } }
            frozenCandidates.filter { it.input.pruneGroup != null }.groupBy { it.input.pruneGroup }.values.forEach { group -> val x = group.first().input; require(group.all { it.input.mode == CandidateMode.MUTATION && it.input.milestone == x.milestone && it.input.hypothesisId == x.hypothesisId && it.input.requiredSurfaces == x.requiredSurfaces && it.input.mutationDimension == x.mutationDimension }) }
            return ForwardPruneForest(byCandidate, frozenCandidates.associate { it.candidateRevisionId to it.input.subsumes }, order, session)
        }
    }
}

object ExpansionMain {
    @JvmStatic fun main(args: Array<String>) { require(args.isEmpty()); val root = findProjectRoot(Path.of("").toAbsolutePath().normalize()); ExpansionPackRenderer(root).writePack(root.resolve(ExpansionPackRenderer.OUTPUT_DIRECTORY)) }
    internal fun findProjectRoot(start: Path) = generateSequence(start) { it.parent }.firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) } ?: error("ClusterNav project root not found")
}
