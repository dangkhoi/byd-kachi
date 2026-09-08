package com.byd.clusternav.offcar

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.text.Normalizer

enum class CandidateFamily { H, S, NATIVE, PROVIDER }
enum class CandidateMode { READ_ONLY, MUTATION }
enum class CandidateState { DISCOVERED, SOURCE_BACKED, READ_ONLY_READY, MUTATION_REVIEW, READY_FOR_FIELD, REJECTED }
enum class ExpansionVerdict { READY_DATA, NOT_EXHAUSTIVE }
enum class LedgerEventType {
    SESSION_START, DISCOVERY_ONLY, PRECONDITION, MUTATION, OBSERVATION, CLEAR, RESTORE,
    PASS, FAIL, INCONCLUSIVE, SKIPPED, PRUNED, BLOCKED, INVALIDATED,
}
enum class Milestone { M1, M2, M3, M4 }
enum class RequiredSurface { CLUSTER_LANE, HUD_NAV_MAP, HUD_ROAD_NAME, CLUSTER_SPEED_SIGN, HUD_SPEED_SIGN }
enum class ConfigAccess { READ_ONLY, READ_WRITE, WRITE }
enum class JavaType { INT, BOOLEAN, DOUBLE, STRING, BYTES }
enum class ClearPolicy { REQUIRED, NOT_APPLICABLE }
enum class Ownership { APP_OWNED, PHYSICAL_DURABLE, DIAGNOSTIC_TEMP }
enum class RestoreScope { CURRENT_PROPERTY, CURRENT_PROVIDER_ROW, CURRENT_SERVICE_STATE }
enum class RiskClass { LOW, MEDIUM, HIGH }
enum class AbsoluteReject { RAW_SELECTOR, FREE_FORM_SELECTOR, MASS_MUTATION, GUESSED_ENUM, RETAINED_STATE_DEPENDENCY, WEAK_EVIDENCE_ONLY }

data class CandidateRevisionKey(
    val family: CandidateFamily,
    val number: Int,
    val slug: String,
    val revision: Int,
) {
    val baseId: String = "CAND-${family.name}-${number.toString().padStart(3, '0')}-$slug"
    val value: String = "$baseId@$revision"
}

object ExpansionIds {
    const val SHA256_PATTERN = "^[0-9a-f]{64}$"
    const val CANDIDATE_PATTERN = "^CAND-(H|S|NATIVE|PROVIDER)-([0-9]{3})-([A-Z0-9][A-Z0-9-]{0,63})@((?:[1-9][0-9]{0,8}|1[0-9]{9}|20[0-9]{8}|21[0-3][0-9]{7}|214[0-6][0-9]{6}|2147[0-3][0-9]{5}|21474[0-7][0-9]{4}|214748[0-2][0-9]{3}|2147483[0-5][0-9]{2}|21474836[0-3][0-9]|214748364[0-7]))$"
    const val ROW_PATTERN = "^ROW-[0-9]{4}-[A-Z0-9][A-Z0-9-]{0,63}$"
    const val SESSION_PATTERN = "^SESSION-[0-9A-F]{16}$"
    const val EVENT_PATTERN = "^EVENT-([0-9]{6})$"
    const val FACT_PATTERN = "^FACT-[A-Z0-9][A-Z0-9-]{0,63}$"; const val EVIDENCE_PATTERN = "^(FACT-[A-Z0-9][A-Z0-9-]{0,63}|H[0-9]{1,3}|S[0-9]{1,3})$"
    const val SELECTOR_PATTERN = "^SEL-[A-Z0-9][A-Z0-9-]{0,63}$"
    const val OPERATION_PATTERN = "^OP-(READ|MUTATE|CLEAR|INVERSE|RESTORE)-[A-Z0-9][A-Z0-9-]{0,63}$"
    const val PROBE_PATTERN = "^PROBE-(READ|LIST)-[A-Z0-9][A-Z0-9-]{0,63}$"
    const val QUERY_PATTERN = "^QRY-C(0[1-9]|1[0-2])-[A-Z0-9][A-Z0-9-]{0,63}$"
    const val RESULT_PATTERN = "^RESULT-(D-H0|D-M[1-4]|P-M[1-4])-[0-9]{4}$"
    const val OBSERVATION_PATTERN = "^OBS-(D-H0|M[1-4])-[A-Z0-9][A-Z0-9-]{0,63}$"; const val PROPOSAL_REASON_PATTERN = "^REASON-PROPOSAL-[A-Z0-9][A-Z0-9-]{0,54}$"

    private val candidateRegex = Regex(CANDIDATE_PATTERN)
    private fun checked(value: String, pattern: String, label: String): String {
        require(Regex(pattern).matches(value)) { "invalid $label: $value" }
        return value
    }

    fun sha256(value: String): String = checked(value, SHA256_PATTERN, "SHA-256")
    fun candidate(value: String): CandidateRevisionKey {
        val match = requireNotNull(candidateRegex.matchEntire(value)) { "invalid candidate revision ID: $value" }
        val revision = match.groupValues[4].toLongOrNull()
        require(revision != null && revision in 1..Int.MAX_VALUE) { "candidate revision is out of range: $value" }
        return CandidateRevisionKey(
            CandidateFamily.valueOf(match.groupValues[1]),
            match.groupValues[2].toInt(),
            match.groupValues[3],
            revision.toInt(),
        )
    }
    fun fact(value: String) = checked(value, FACT_PATTERN, "fact ID")
    fun selector(value: String) = checked(value, SELECTOR_PATTERN, "selector ID")
    fun operation(value: String) = checked(value, OPERATION_PATTERN, "operation ID")
    fun probe(value: String) = checked(value, PROBE_PATTERN, "probe ID")
    fun observation(value: String) = checked(value, OBSERVATION_PATTERN, "observation ID")
    fun hypothesis(value: String) = checked(value, "^HYP-[A-Z0-9][A-Z0-9-]{0,63}$", "hypothesis ID")
    fun prune(value: String) = checked(value, "^PRUNE-[A-Z0-9][A-Z0-9-]{0,63}$", "prune-group ID")
    fun reason(value: String) = checked(value, "^REASON-[A-Z0-9][A-Z0-9-]{0,63}$", "reason ID")
    fun proposalReason(value: String) = checked(reason(value), PROPOSAL_REASON_PATTERN, "proposal reason ID")
    fun dimension(value: String) = checked(value, "^DIMENSION-[A-Z0-9][A-Z0-9-]{0,63}$", "mutation-dimension ID")
    fun config(value: String) = checked(value, "^CONFIG-[A-Z0-9][A-Z0-9-]{0,63}$", "config ID")
    fun provider(value: String) = checked(value, "^PROVIDER-[A-Z0-9][A-Z0-9-]{0,63}$", "provider ID")
    fun permission(value: String) = checked(value, "^PERMISSION-[A-Z0-9][A-Z0-9-]{0,63}$", "permission ID")
    fun transport(value: String) = checked(value, "^TRANSPORT-[A-Z0-9][A-Z0-9-]{0,63}$", "transport ID")
    fun consumer(value: String) = checked(value, "^CONSUMER-[A-Z0-9][A-Z0-9-]{0,63}$", "consumer ID")
    fun value(value: String) = checked(value, "^VALUE-[A-Z0-9][A-Z0-9-]{0,63}$", "value ID")
    fun evidence(value: String) = checked(value, EVIDENCE_PATTERN, "evidence ID")

    val candidateComparator: Comparator<String> = Comparator { left, right ->
        val a = candidate(left)
        val b = candidate(right)
        compareValuesBy(a, b, { it.family.ordinal }, { it.number }, { it.slug }, { it.revision })
    }
}

internal sealed interface CanonicalValue
internal data object JsonNull : CanonicalValue
internal data class JsonBoolean(val value: Boolean) : CanonicalValue
internal data class JsonInteger(val value: Long) : CanonicalValue
internal data class JsonText(val value: String) : CanonicalValue
internal data class JsonArray(val values: List<CanonicalValue>) : CanonicalValue
internal data class JsonObject(val fields: List<Pair<String, CanonicalValue>>) : CanonicalValue

internal object CanonicalJson {
    fun render(value: CanonicalValue): String = buildString { appendValue(value) }
    fun bytes(value: CanonicalValue): ByteArray = render(value).toByteArray(StandardCharsets.UTF_8)
    fun digest(value: CanonicalValue): String = ExpansionHashing.sha256(bytes(value))
    fun obj(vararg fields: Pair<String, CanonicalValue>) = JsonObject(fields.toList())
    fun array(values: Iterable<CanonicalValue>) = JsonArray(values.toList())
    fun text(value: String?): CanonicalValue = value?.let(::JsonText) ?: JsonNull
    fun integer(value: Int?) = value?.let { JsonInteger(it.toLong()) } ?: JsonNull
    fun enum(value: Enum<*>?): CanonicalValue = text(value?.name)

    private fun StringBuilder.appendValue(value: CanonicalValue) {
        when (value) {
            JsonNull -> append("null")
            is JsonBoolean -> append(if (value.value) "true" else "false")
            is JsonInteger -> append(value.value)
            is JsonText -> appendQuoted(value.value)
            is JsonArray -> value.values.joinTo(this, prefix = "[", postfix = "]", separator = ",") { render(it) }
            is JsonObject -> {
                val normalized = value.fields.map { (key, fieldValue) -> normalize(key) to fieldValue }
                require(normalized.map { it.first }.distinct().size == normalized.size) { "duplicate canonical object key" }
                require(normalized.all { (key, _) -> key.all { it.code in 0x20..0x7e } }) { "object keys must be ASCII" }
                normalized.sortedBy { it.first }.joinTo(this, prefix = "{", postfix = "}", separator = ",") {
                    buildString { appendQuoted(it.first); append(':'); appendValue(it.second) }
                }
            }
        }
    }

    private fun StringBuilder.appendQuoted(raw: String) {
        val value = normalize(raw)
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

    private fun normalize(value: String): String {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.isHighSurrogate() -> {
                    require(index + 1 < value.length && value[index + 1].isLowSurrogate()) { "lone UTF-16 surrogate" }
                    index += 2
                }
                char.isLowSurrogate() -> error("lone UTF-16 surrogate")
                else -> index++
            }
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC)
    }
}

object ExpansionHashing {
    private const val HEX = "0123456789abcdef"
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    fun sha256Utf8(value: String): String = sha256(value.toByteArray(StandardCharsets.UTF_8))
    fun sha256File(path: Path): String = Files.newInputStream(path, java.nio.file.StandardOpenOption.READ, java.nio.file.LinkOption.NOFOLLOW_LINKS).use { sha256(it.readBytes()) }
    internal fun ByteArray.toHex(): String = buildString(size * 2) {
        this@toHex.forEach { byte ->
            append(HEX[(byte.toInt() ushr 4) and 15])
            append(HEX[byte.toInt() and 15])
        }
    }
    internal fun decodeSha256(value: String): ByteArray {
        ExpansionIds.sha256(value)
        return ByteArray(32) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }
}

private fun requireSortedUnique(values: List<String>, comparator: Comparator<String> = Comparator.naturalOrder()) {
    require(values.distinct().size == values.size) { "values must be unique" }
    require(values == values.sortedWith(comparator)) { "values must be canonically sorted" }
}
private fun strings(values: Iterable<String>) = CanonicalJson.array(values.map(::JsonText))
private fun enums(values: Iterable<Enum<*>>) = CanonicalJson.array(values.map { JsonText(it.name) })

data class PromotionProof(
    val absoluteRejects: List<AbsoluteReject>, val selectorId: String?, val readProbeId: String?,
    val mutationOperationId: String?, val configId: String?, val access: ConfigAccess?, val javaType: JavaType?,
    val providerId: String?, val permissionId: String?, val transportId: String?, val boundedDomainValueIds: List<String>,
    val priorReadOperationId: String?, val readBackOperationId: String?, val clearPolicy: ClearPolicy,
    val clearOperationId: String?, val inverseOperationIds: List<String>, val consumerId: String?,
    val ownership: Ownership?, val risk: Int?, val evidenceIds: List<String>,
) {
    init {
        require(absoluteRejects == absoluteRejects.distinct().sortedBy { it.ordinal }) { "absolute rejects must be ordered and unique" }
        selectorId?.let(ExpansionIds::selector); readProbeId?.let(ExpansionIds::probe)
        mutationOperationId?.let { ExpansionIds.operation(it); require(it.startsWith("OP-MUTATE-")) }
        listOfNotNull(priorReadOperationId, readBackOperationId).forEach { ExpansionIds.operation(it); require(it.startsWith("OP-READ-")) }
        clearOperationId?.let { ExpansionIds.operation(it); require(it.startsWith("OP-CLEAR-")) }
        require(inverseOperationIds.distinct().size == inverseOperationIds.size) { "inverse operations must be unique" }
        inverseOperationIds.forEach { ExpansionIds.operation(it); require(it.startsWith("OP-INVERSE-") || it.startsWith("OP-RESTORE-")) }
        configId?.let(ExpansionIds::config)
        providerId?.let(ExpansionIds::provider); permissionId?.let(ExpansionIds::permission)
        transportId?.let(ExpansionIds::transport); consumerId?.let(ExpansionIds::consumer)
        boundedDomainValueIds.forEach(ExpansionIds::value); evidenceIds.forEach(ExpansionIds::evidence)
        requireSortedUnique(boundedDomainValueIds); requireSortedUnique(evidenceIds)
        require(risk == null || risk in 0..100) { "risk must be 0..100" }
    }

    fun deriveState(mode: CandidateMode): CandidateState = when {
        absoluteRejects.isNotEmpty() -> CandidateState.REJECTED
        evidenceIds.isEmpty() -> CandidateState.DISCOVERED
        selectorId == null -> CandidateState.SOURCE_BACKED
        mode == CandidateMode.READ_ONLY && readOnlyComplete() -> CandidateState.READ_ONLY_READY
        mode == CandidateMode.READ_ONLY -> CandidateState.SOURCE_BACKED
        mutationComplete() -> CandidateState.READY_FOR_FIELD
        else -> CandidateState.MUTATION_REVIEW
    }

    private fun commonComplete() = configId != null && access != null && javaType != null && providerId != null &&
        permissionId != null && transportId != null && boundedDomainValueIds.isNotEmpty() && consumerId != null &&
        ownership != null && risk != null
    private fun readOnlyComplete() = commonComplete() && readProbeId != null && mutationOperationId == null &&
        clearPolicy == ClearPolicy.NOT_APPLICABLE && clearOperationId == null && inverseOperationIds.isEmpty()
    private fun mutationComplete() = commonComplete() && access in setOf(ConfigAccess.READ_WRITE, ConfigAccess.WRITE) &&
        mutationOperationId != null && priorReadOperationId != null && readBackOperationId != null &&
        clearPolicy == ClearPolicy.REQUIRED && clearOperationId != null && inverseOperationIds.isNotEmpty()

    internal fun json() = CanonicalJson.obj(
        "absoluteRejects" to enums(absoluteRejects), "access" to CanonicalJson.enum(access),
        "boundedDomainValueIds" to strings(boundedDomainValueIds), "clearOperationId" to CanonicalJson.text(clearOperationId),
        "clearPolicy" to CanonicalJson.enum(clearPolicy), "configId" to CanonicalJson.text(configId),
        "consumerId" to CanonicalJson.text(consumerId), "evidenceIds" to strings(evidenceIds),
        "inverseOperationIds" to strings(inverseOperationIds), "javaType" to CanonicalJson.enum(javaType),
        "mutationOperationId" to CanonicalJson.text(mutationOperationId), "ownership" to CanonicalJson.enum(ownership),
        "permissionId" to CanonicalJson.text(permissionId), "priorReadOperationId" to CanonicalJson.text(priorReadOperationId),
        "providerId" to CanonicalJson.text(providerId), "readBackOperationId" to CanonicalJson.text(readBackOperationId),
        "readProbeId" to CanonicalJson.text(readProbeId), "risk" to CanonicalJson.integer(risk),
        "selectorId" to CanonicalJson.text(selectorId), "transportId" to CanonicalJson.text(transportId),
    )
}

data class PlanningProfile(
    val phaseRank: Int, val evidenceStrength: Int, val informationGain: Int, val reversibility: Int,
    val mutationRisk: Int, val dependencyUncertainty: Int, val estimatedTimeMs: Long,
) {
    init {
        require(phaseRank in 0..9999); listOf(evidenceStrength, informationGain, reversibility, mutationRisk, dependencyUncertainty)
            .forEach { require(it in 0..100) }; require(estimatedTimeMs in 0..86_400_000)
    }
    val score: Long get() = Math.subtractExact(
        Math.addExact(Math.addExact(evidenceStrength * 100L, informationGain * 40L), reversibility * 30L),
        Math.addExact(Math.addExact(mutationRisk * 50L, (estimatedTimeMs / 1000L) * 10L), dependencyUncertainty * 25L),
    )
    val riskClass: RiskClass get() = when (mutationRisk) { in 0..33 -> RiskClass.LOW; in 34..66 -> RiskClass.MEDIUM; else -> RiskClass.HIGH }
    internal fun json() = CanonicalJson.obj(
        "dependencyUncertainty" to JsonInteger(dependencyUncertainty.toLong()), "estimatedTimeMs" to JsonInteger(estimatedTimeMs),
        "evidenceStrength" to JsonInteger(evidenceStrength.toLong()), "informationGain" to JsonInteger(informationGain.toLong()),
        "mutationRisk" to JsonInteger(mutationRisk.toLong()), "phaseRank" to JsonInteger(phaseRank.toLong()),
        "reversibility" to JsonInteger(reversibility.toLong()),
    )
}

data class CandidateRevisionInput(
    val candidateRevisionId: String, val milestone: Milestone, val mode: CandidateMode, val proof: PromotionProof,
    val hypothesisId: String, val mutationDimension: String, val requiredSurfaces: List<RequiredSurface>,
    val requiredObservationIds: List<String>, val restoreScope: List<RestoreScope>, val invalidationTriggers: List<String>,
    val planningProfile: PlanningProfile, val dependsOnRevisionIds: List<String>, val pruneGroup: String?,
    val subsumes: List<String>, val invalidatesOn: List<String>, val predecessorCandidateSha256: String?,
)

class CandidateRevision private constructor(val input: CandidateRevisionInput, val state: CandidateState, val revision: Int, val revisionSha256: String) {
    val candidateRevisionId get() = input.candidateRevisionId
    fun canonicalJson(): String = CanonicalJson.render(json(includeHash = true))
    fun canonicalBytes(): ByteArray = canonicalJson().toByteArray(StandardCharsets.UTF_8)

    internal fun json(includeHash: Boolean): JsonObject {
        val fields = mutableListOf<Pair<String, CanonicalValue>>(
            "candidateRevisionId" to JsonText(input.candidateRevisionId), "dependsOnRevisionIds" to strings(input.dependsOnRevisionIds),
            "hypothesisId" to JsonText(input.hypothesisId), "invalidatesOn" to strings(input.invalidatesOn),
            "invalidationTriggers" to strings(input.invalidationTriggers), "milestone" to CanonicalJson.enum(input.milestone),
            "mode" to CanonicalJson.enum(input.mode), "mutationDimension" to JsonText(input.mutationDimension),
            "planningProfile" to input.planningProfile.json(), "predecessorCandidateSha256" to CanonicalJson.text(input.predecessorCandidateSha256),
            "proof" to input.proof.json(), "pruneGroup" to CanonicalJson.text(input.pruneGroup),
            "requiredObservationIds" to strings(input.requiredObservationIds), "requiredSurfaces" to enums(input.requiredSurfaces),
            "restoreScope" to enums(input.restoreScope), "revision" to JsonInteger(revision.toLong()),
            "state" to CanonicalJson.enum(state), "subsumes" to strings(input.subsumes),
        )
        if (includeHash) fields += "revisionSha256" to JsonText(revisionSha256)
        return JsonObject(fields)
    }

    companion object {
        private val milestoneSurfaces = mapOf(
            Milestone.M1 to listOf(RequiredSurface.HUD_NAV_MAP), Milestone.M2 to listOf(RequiredSurface.HUD_ROAD_NAME),
            Milestone.M3 to listOf(RequiredSurface.CLUSTER_SPEED_SIGN), Milestone.M4 to listOf(RequiredSurface.HUD_SPEED_SIGN),
        )
        fun create(input: CandidateRevisionInput, declaredRevision: Int = ExpansionIds.candidate(input.candidateRevisionId).revision): CandidateRevision {
            val key = ExpansionIds.candidate(input.candidateRevisionId)
            require(key.revision == declaredRevision) { "candidate revision ID suffix must equal revision" }
            require((declaredRevision == 1) == (input.predecessorCandidateSha256 == null)) { "predecessor is null iff revision is one" }
            input.predecessorCandidateSha256?.let(ExpansionIds::sha256)
            ExpansionIds.hypothesis(input.hypothesisId); ExpansionIds.dimension(input.mutationDimension)
            require(input.requiredSurfaces == milestoneSurfaces.getValue(input.milestone)) { "milestone surfaces are fixed" }
            input.requiredObservationIds.forEach { id ->
                ExpansionIds.observation(id); require(id.startsWith("OBS-${input.milestone.name}-")) { "observation milestone mismatch" }
            }
            requireSortedUnique(input.requiredObservationIds); require(input.restoreScope == input.restoreScope.distinct().sortedBy { it.ordinal })
            input.invalidationTriggers.forEach(ExpansionIds::reason); requireSortedUnique(input.invalidationTriggers)
            input.dependsOnRevisionIds.forEach { ExpansionIds.candidate(it) }
            require(input.dependsOnRevisionIds.distinct().size == input.dependsOnRevisionIds.size)
            input.pruneGroup?.let(ExpansionIds::prune); input.subsumes.forEach { ExpansionIds.candidate(it) }
            requireSortedUnique(input.subsumes, ExpansionIds.candidateComparator)
            require((input.pruneGroup != null) || input.subsumes.isEmpty()) { "subsumes requires a prune group" }
            input.invalidatesOn.forEach(ExpansionIds::reason); requireSortedUnique(input.invalidatesOn)
            val frozenProof = input.proof.copy(
                absoluteRejects = immutableListSnapshot(input.proof.absoluteRejects), boundedDomainValueIds = immutableListSnapshot(input.proof.boundedDomainValueIds),
                inverseOperationIds = immutableListSnapshot(input.proof.inverseOperationIds), evidenceIds = immutableListSnapshot(input.proof.evidenceIds),
            )
            val frozen = input.copy(
                proof = frozenProof, requiredSurfaces = immutableListSnapshot(input.requiredSurfaces), requiredObservationIds = immutableListSnapshot(input.requiredObservationIds),
                restoreScope = immutableListSnapshot(input.restoreScope), invalidationTriggers = immutableListSnapshot(input.invalidationTriggers),
                dependsOnRevisionIds = immutableListSnapshot(input.dependsOnRevisionIds), subsumes = immutableListSnapshot(input.subsumes), invalidatesOn = immutableListSnapshot(input.invalidatesOn),
            )
            val state = frozen.proof.deriveState(frozen.mode)
            if (frozen.mode == CandidateMode.READ_ONLY) {
                require(frozen.restoreScope.isEmpty() && frozen.invalidationTriggers.isEmpty()) {
                    "read-only candidates cannot project mutation recovery fields"
                }
            }
            if (state == CandidateState.READY_FOR_FIELD) {
                require(frozen.restoreScope.isNotEmpty() && frozen.invalidationTriggers.isNotEmpty()) {
                    "field-ready mutations require restore scope and invalidation triggers"
                }
            }
            val draft = CandidateRevision(frozen, state, declaredRevision, "0".repeat(64))
            return CandidateRevision(frozen, state, declaredRevision, CanonicalJson.digest(draft.json(includeHash = false)))
        }
    }
}

data class CandidateRevisionRef(val candidateRevisionId: String, val revisionSha256: String) {
    init { ExpansionIds.candidate(candidateRevisionId); ExpansionIds.sha256(revisionSha256) }
    internal fun json() = CanonicalJson.obj("candidateRevisionId" to JsonText(candidateRevisionId), "revisionSha256" to JsonText(revisionSha256))
}

data class CandidateStateCounts(val discovered: Int, val sourceBacked: Int, val readOnlyReady: Int, val mutationReview: Int, val readyForField: Int, val rejected: Int) {
    init { listOf(discovered, sourceBacked, readOnlyReady, mutationReview, readyForField, rejected).forEach { require(it >= 0) } }
    internal fun json() = CanonicalJson.obj(
        "discovered" to JsonInteger(discovered.toLong()), "mutationReview" to JsonInteger(mutationReview.toLong()),
        "readOnlyReady" to JsonInteger(readOnlyReady.toLong()), "readyForField" to JsonInteger(readyForField.toLong()),
        "rejected" to JsonInteger(rejected.toLong()), "sourceBacked" to JsonInteger(sourceBacked.toLong()),
    )
    companion object { fun from(candidates: List<CandidateRevision>): CandidateStateCounts {
        val latest = candidates.groupBy { ExpansionIds.candidate(it.candidateRevisionId).baseId }.values.map { it.maxBy(CandidateRevision::revision) }
        fun count(state: CandidateState) = latest.count { it.state == state }
        return CandidateStateCounts(count(CandidateState.DISCOVERED), count(CandidateState.SOURCE_BACKED), count(CandidateState.READ_ONLY_READY), count(CandidateState.MUTATION_REVIEW), count(CandidateState.READY_FOR_FIELD), count(CandidateState.REJECTED))
    } }
}

class RegistryRevision private constructor(
    val revision: Int, val predecessorRegistryRevisionSha256: String?, val candidateRefs: List<CandidateRevisionRef>,
    val stateCounts: CandidateStateCounts, val registryRevisionSha256: String,
) {
    internal fun json(includeHash: Boolean): JsonObject {
        val fields = mutableListOf<Pair<String, CanonicalValue>>(
            "candidateRefs" to CanonicalJson.array(candidateRefs.map { it.json() }),
            "predecessorRegistryRevisionSha256" to CanonicalJson.text(predecessorRegistryRevisionSha256),
            "revision" to JsonInteger(revision.toLong()), "stateCounts" to stateCounts.json(),
        )
        if (includeHash) fields += "registryRevisionSha256" to JsonText(registryRevisionSha256)
        return JsonObject(fields)
    }
    companion object { fun create(revision: Int, predecessor: String?, candidates: List<CandidateRevision>): RegistryRevision {
        require(revision >= 1); require((revision == 1) == (predecessor == null)); predecessor?.let(ExpansionIds::sha256)
        val ordered = candidates.sortedWith(compareBy(ExpansionIds.candidateComparator) { it.candidateRevisionId })
        require(ordered == candidates) { "candidate revisions must be canonically sorted" }
        val refs = immutableListSnapshot(candidates.map { CandidateRevisionRef(it.candidateRevisionId, it.revisionSha256) })
        val draft = RegistryRevision(revision, predecessor, refs, CandidateStateCounts.from(candidates), "0".repeat(64))
        return RegistryRevision(revision, predecessor, refs, draft.stateCounts, CanonicalJson.digest(draft.json(false)))
    } }
}

object RegistryHistory {
    fun validate(history: List<RegistryRevision>) {
        require(history.isNotEmpty()) { "registry history is required" }
        history.forEachIndexed { index, current ->
            require(current.revision == index + 1) { "registry revisions must be gapless" }
            val previous = history.getOrNull(index - 1)
            require(current.predecessorRegistryRevisionSha256 == previous?.registryRevisionSha256) { "registry predecessor mismatch" }
            if (previous != null) {
                val currentRefs = current.candidateRefs.associateBy(CandidateRevisionRef::candidateRevisionId)
                previous.candidateRefs.forEach { require(currentRefs[it.candidateRevisionId] == it) { "registry history rewrites a candidate revision" } }
            }
        }
    }
}

class CandidateRegistryRoot private constructor(
    val revision: Int, val legacyBaselineFileSha256: String, val coverageFileSha256: String,
    val evidenceMapFileSha256: String, val candidates: List<CandidateRevision>, val history: List<RegistryRevision>,
    val selfSha256: String,
) {
    val schemaId = SCHEMA_ID
    fun canonicalJson(): String = CanonicalJson.render(json(true))
    private fun json(includeSelf: Boolean): JsonObject {
        val fields = mutableListOf<Pair<String, CanonicalValue>>(
            "candidates" to CanonicalJson.array(candidates.map { it.json(true) }), "coverageFileSha256" to JsonText(coverageFileSha256),
            "evidenceMapFileSha256" to JsonText(evidenceMapFileSha256), "history" to CanonicalJson.array(history.map { it.json(true) }),
            "legacyBaselineFileSha256" to JsonText(legacyBaselineFileSha256), "revision" to JsonInteger(revision.toLong()), "schemaId" to JsonText(schemaId),
        )
        if (includeSelf) fields += "selfSha256" to JsonText(selfSha256)
        return JsonObject(fields)
    }
    companion object {
        const val SCHEMA_ID = "clusternav.expansion-candidate-registry/v1"
        fun create(legacyBaselineFileSha256: String, coverageFileSha256: String, evidenceMapFileSha256: String, candidates: List<CandidateRevision>, history: List<RegistryRevision>): CandidateRegistryRoot {
            listOf(legacyBaselineFileSha256, coverageFileSha256, evidenceMapFileSha256).forEach(ExpansionIds::sha256)
            CandidateRevisionChains.validate(candidates)
            RegistryHistory.validate(history); require(history.last().candidateRefs == candidates.map { CandidateRevisionRef(it.candidateRevisionId, it.revisionSha256) })
            candidates.groupBy { ExpansionIds.candidate(it.candidateRevisionId).baseId }.values.forEach { chain ->
                chain.sortedBy(CandidateRevision::revision).forEachIndexed { index, candidate ->
                    require(candidate.revision == index + 1); require(candidate.input.predecessorCandidateSha256 == chain.getOrNull(index - 1)?.revisionSha256)
                }
            }
            val draft = CandidateRegistryRoot(history.last().revision, legacyBaselineFileSha256, coverageFileSha256,
                evidenceMapFileSha256, immutableListSnapshot(candidates), immutableListSnapshot(history), "0".repeat(64))
            return CandidateRegistryRoot(draft.revision, legacyBaselineFileSha256, coverageFileSha256, evidenceMapFileSha256, draft.candidates, draft.history, CanonicalJson.digest(draft.json(false)))
        }
    }
}

data class LegacyBaselineArtifact(val path: String, val fullSha256: String) {
    init { require(path in LegacyBaselineIdentity.PARENT_PATHS); ExpansionIds.sha256(fullSha256) }
    internal fun json() = CanonicalJson.obj("fullSha256" to JsonText(fullSha256), "path" to JsonText(path))
}

class LegacyBaselineRoot private constructor(
    val artifacts: List<LegacyBaselineArtifact>, val parentCombinedSha256: String, val selfSha256: String,
) {
    val schemaId = SCHEMA_ID
    fun canonicalJson(): String = CanonicalJson.render(json(true))
    fun canonicalBytes(): ByteArray = canonicalJson().toByteArray(StandardCharsets.UTF_8)
    fun finalizedFileSha256(): String = ExpansionHashing.sha256(canonicalBytes())
    private fun json(includeSelf: Boolean): JsonObject {
        val fields = mutableListOf<Pair<String, CanonicalValue>>(
            "artifacts" to CanonicalJson.array(artifacts.map { it.json() }), "parentCombinedSha256" to JsonText(parentCombinedSha256), "schemaId" to JsonText(schemaId),
        )
        if (includeSelf) fields += "selfSha256" to JsonText(selfSha256)
        return JsonObject(fields)
    }
    companion object {
        const val SCHEMA_ID = "clusternav.expansion-legacy-baseline/v1"
        internal fun create(artifacts: List<LegacyBaselineArtifact>, combined: String): LegacyBaselineRoot {
            require(artifacts.map { it.path } == LegacyBaselineIdentity.PARENT_PATHS); ExpansionIds.sha256(combined)
            val draft = LegacyBaselineRoot(immutableListSnapshot(artifacts), combined, "0".repeat(64))
            return LegacyBaselineRoot(draft.artifacts, combined, CanonicalJson.digest(draft.json(false)))
        }
    }
}

object LegacyBaselineIdentity {
    const val PARENT_BASELINE_SHA256 = "5b49a5ea9c23950dfd3d3112285db1501d85ec30dd26bbac03f5e96398791513"
    val PARENT_PATHS = immutableListSnapshot(listOf(
        "docs/diagnostics/hud-sign-re/README.md", "docs/diagnostics/hud-sign-re/candidate-report.html",
        "docs/diagnostics/hud-sign-re/corpus-completeness.json", "docs/diagnostics/hud-sign-re/evidence-index.json",
        "docs/diagnostics/hud-sign-re/first-launch-emulator-result.json", "docs/diagnostics/hud-sign-re/m1-nav-hud-plan.json",
        "docs/diagnostics/hud-sign-re/m2-hud-road-plan.json", "docs/diagnostics/hud-sign-re/m3-cluster-sign-plan.json",
        "docs/diagnostics/hud-sign-re/m4-hud-sign-plan.json", "docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json",
        "docs/diagnostics/hud-sign-re/traceability.json", "docs/diagnostics/hud-sign-re/zero-hit-report.txt",
        "docs/specs/seal-nav-hud-speed-sign-offcar.html",
    ))

    fun capture(projectRoot: Path): LegacyBaselineRoot {
        val root = projectRoot.toAbsolutePath().normalize()
        val artifacts = PARENT_PATHS.map { relative ->
            val path = ExpansionPathFence.requireRegularInput(root, root.resolve(relative).normalize())
            LegacyBaselineArtifact(relative, ExpansionHashing.sha256File(path))
        }
        return LegacyBaselineRoot.create(artifacts, combinedSha256(artifacts))
    }

    fun parentCombinedSha256(projectRoot: Path): String = capture(projectRoot).parentCombinedSha256
    fun combinedSha256(artifacts: List<LegacyBaselineArtifact>): String {
        require(artifacts.map { it.path } == PARENT_PATHS) { "the exact 13 sorted parent paths are required" }
        val digest = MessageDigest.getInstance("SHA-256")
        artifacts.forEach { artifact ->
            val pathBytes = artifact.path.toByteArray(StandardCharsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(pathBytes.size).array())
            digest.update(pathBytes)
            digest.update(ExpansionHashing.decodeSha256(artifact.fullSha256))
        }
        return with(ExpansionHashing) { digest.digest().toHex() }
    }
}
