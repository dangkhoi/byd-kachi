package com.byd.clusternav.vehicle.t10

/** Fixed artifact aliases; callers cannot substitute a path or arbitrary alias. */
enum class T10ArtifactId(val wireName: String) {
    EXPANSION_SOURCE_SET("ARTIFACT-EXPANSION-SOURCE-SET"),
    CANDIDATE_DIFF("ARTIFACT-CANDIDATE-DIFF"),
    T10_APK("ARTIFACT-T10-APK");

    companion object {
        fun parse(value: String) = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown T10 artifact ID")
    }
}

enum class T10Variant { DEBUG, VEHICLE_TEST, RELEASE }
enum class T10SenderId { SENDER_CLUSTER_NAV }
enum class T10ComponentId { COMPONENT_PROBE_RECEIVER, COMPONENT_PROBE_ACTIVITY }
enum class T10PermissionId { PERMISSION_VENDOR_CAR, PERMISSION_NONE }
enum class T10ProfileId { PROFILE_SEAL_T10, PROFILE_UNASSIGNED }

enum class T10BlockerId(val wireName: String) {
    MISSING_AUTHORIZED_T10_HANDOFF("BLOCKER-MISSING-AUTHORIZED-T10-HANDOFF"),
    UNPROVEN_APP_REACHABILITY("BLOCKER-UNPROVEN-APP-REACHABILITY"),
    UNPROVEN_PERMISSION_BINDING("BLOCKER-UNPROVEN-PERMISSION-BINDING"),
    UNRESOLVED_EXACT_PROFILE("BLOCKER-UNRESOLVED-EXACT-PROFILE"),
    EXACT_IDENTITY_MISMATCH("BLOCKER-EXACT-IDENTITY-MISMATCH"),
    AMBIGUOUS_TRANSPORT("BLOCKER-AMBIGUOUS-TRANSPORT"),
    MISSING_OPERATIONAL_AUTHORIZATION("BLOCKER-MISSING-OPERATIONAL-AUTHORIZATION");

    companion object {
        fun parse(value: String) = entries.singleOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("unknown T10 blocker ID")
    }
}

enum class ArtifactHandoffState { ARTIFACT_READY_OPERATIONALLY_BLOCKED }

/** Sorted, unique signer certificate hashes and their canonical aggregate. */
class SignerIdentity private constructor(
    certificateSha256s: List<Sha256>,
    val aggregateSha256: Sha256,
) {
    val certificateSha256s: List<Sha256> = immutableList(certificateSha256s)

    override fun equals(other: Any?) = other is SignerIdentity &&
        certificateSha256s == other.certificateSha256s && aggregateSha256 == other.aggregateSha256
    override fun hashCode() = 31 * certificateSha256s.hashCode() + aggregateSha256.hashCode()

    companion object {
        fun fromCertificateHashes(hashes: Collection<Sha256>): SignerIdentity {
            require(hashes.isNotEmpty()) { "at least one signer certificate hash is required" }
            require(hashes.distinct().size == hashes.size) { "signer certificate hashes must be unique" }
            val sorted = hashes.sorted()
            return SignerIdentity(sorted, aggregate(sorted))
        }

        fun verified(hashes: List<Sha256>, aggregateSha256: Sha256): SignerIdentity {
            require(hashes.isNotEmpty() && hashes == hashes.distinct().sorted()) {
                "signer certificate hash set must be sorted and unique"
            }
            require(aggregate(hashes) == aggregateSha256) { "signer aggregate SHA-256 mismatch" }
            return SignerIdentity(hashes, aggregateSha256)
        }

        private fun aggregate(hashes: List<Sha256>): Sha256 = T10Canonical.sha256(
            T10Canonical.array(hashes.map { T10Canonical.text(it.value) }),
        )
    }
}

/** Exact identity equality is intentionally the generated field-for-field equality. */
data class ExactIdentity(
    val sourceSnapshotSha256: Sha256,
    val diffFileSha256: Sha256,
    val apkFileSha256: Sha256,
    val signer: SignerIdentity,
    val registryFileSha256: Sha256,
    val packSha256: Sha256,
    val candidateSetSha256: Sha256,
    val variant: T10Variant,
    val senderId: T10SenderId,
    val componentId: T10ComponentId,
    val permissionId: T10PermissionId,
    val profileId: T10ProfileId,
    val sourceArtifactId: T10ArtifactId = T10ArtifactId.EXPANSION_SOURCE_SET,
    val diffArtifactId: T10ArtifactId = T10ArtifactId.CANDIDATE_DIFF,
    val apkArtifactId: T10ArtifactId = T10ArtifactId.T10_APK,
) {
    init {
        require(sourceArtifactId == T10ArtifactId.EXPANSION_SOURCE_SET)
        require(diffArtifactId == T10ArtifactId.CANDIDATE_DIFF)
        require(apkArtifactId == T10ArtifactId.T10_APK)
    }

    val signerSha256: Sha256 get() = signer.aggregateSha256
    val signerCertificateSha256s: List<Sha256> get() = signer.certificateSha256s
    val logicalPackSha256: Sha256 get() = packSha256
    fun canonicalSha256(): Sha256 = T10Canonical.sha256(canonicalValue())

    internal fun canonicalValue(): T10JsonValue = T10Canonical.obj(
        "apkArtifactId" to T10Canonical.text(apkArtifactId.wireName),
        "apkFileSha256" to T10Canonical.text(apkFileSha256.value),
        "candidateSetSha256" to T10Canonical.text(candidateSetSha256.value),
        "componentId" to T10Canonical.text(componentId.name),
        "diffArtifactId" to T10Canonical.text(diffArtifactId.wireName),
        "diffFileSha256" to T10Canonical.text(diffFileSha256.value),
        "packSha256" to T10Canonical.text(packSha256.value),
        "permissionId" to T10Canonical.text(permissionId.name),
        "profileId" to T10Canonical.text(profileId.name),
        "registryFileSha256" to T10Canonical.text(registryFileSha256.value),
        "senderId" to T10Canonical.text(senderId.name),
        "signerCertificateSha256s" to T10Canonical.array(
            signerCertificateSha256s.map { T10Canonical.text(it.value) },
        ),
        "signerSha256" to T10Canonical.text(signerSha256.value),
        "sourceArtifactId" to T10Canonical.text(sourceArtifactId.wireName),
        "sourceSnapshotSha256" to T10Canonical.text(sourceSnapshotSha256.value),
        "variant" to T10Canonical.text(variant.name),
    )
}

sealed interface T10IdentityRequirement {
    val state: State
    enum class State { RESOLVED, INERT_IDENTITY_BLOCKED }

    data class Resolved(val exactIdentity: ExactIdentity) : T10IdentityRequirement {
        override val state = State.RESOLVED
    }

    class Inert private constructor(blockerIds: List<T10BlockerId>) : T10IdentityRequirement {
        val blockerIds: List<T10BlockerId> = immutableList(blockerIds)
        override val state = State.INERT_IDENTITY_BLOCKED
        override fun equals(other: Any?) = other is Inert && blockerIds == other.blockerIds
        override fun hashCode() = blockerIds.hashCode()

        companion object {
            fun of(blockerIds: Collection<T10BlockerId>): Inert {
                val values = blockerIds.toList()
                require(values.isNotEmpty() && values == values.distinct().sortedBy { it.wireName }) {
                    "identity blockers must be non-empty, sorted, and unique"
                }
                return Inert(values)
            }
        }
    }
}

/** Metadata-only handoff; this state can never itself authorize dispatch. */
class T10ArtifactHandoff private constructor(
    val exactIdentity: ExactIdentity,
    blockerIds: List<T10BlockerId>,
    val selfSha256: Sha256,
) {
    val schemaId = SCHEMA_ID
    val state = ArtifactHandoffState.ARTIFACT_READY_OPERATIONALLY_BLOCKED
    val blockerIds: List<T10BlockerId> = immutableList(blockerIds)

    fun toCanonicalBytes(): ByteArray = T10Canonical.render(canonicalValue(includeSelf = true))

    internal fun canonicalValue(includeSelf: Boolean): T10JsonValue = T10Canonical.obj(
        *buildList {
            add("blockerIds" to T10Canonical.array(blockerIds.map { T10Canonical.text(it.wireName) }))
            add("exactIdentity" to exactIdentity.canonicalValue())
            add("schemaId" to T10Canonical.text(schemaId))
            if (includeSelf) add("selfSha256" to T10Canonical.text(selfSha256.value))
            add("state" to T10Canonical.text(state.name))
        }.toTypedArray(),
    )

    companion object {
        const val SCHEMA_ID = "clusternav.t10-artifact-handoff/v1"

        fun create(exactIdentity: ExactIdentity, blockerIds: Collection<T10BlockerId>): T10ArtifactHandoff {
            val values = blockerIds.toList()
            require(values.isNotEmpty() && values == values.distinct().sortedBy { it.wireName }) {
                "handoff blockers must be non-empty, sorted, and unique"
            }
            require(T10BlockerId.UNPROVEN_APP_REACHABILITY in values) {
                "T10 remains operationally blocked until app reachability is proven"
            }
            val draft = T10ArtifactHandoff(exactIdentity, values, ZERO_SHA256)
            return T10ArtifactHandoff(exactIdentity, values, T10Canonical.sha256(draft.canonicalValue(false)))
        }
    }
}

object T10IdentityLoader {
    fun loadHandoff(bytes: ByteArray): T10ArtifactHandoff {
        val root = T10Canonical.parse(bytes).objectFields("handoff")
        root.requireExactKeys("handoff", "blockerIds", "exactIdentity", "schemaId", "selfSha256", "state")
        require(root.getValue("schemaId").stringValue("schemaId") == T10ArtifactHandoff.SCHEMA_ID)
        require(root.getValue("state").stringValue("state") == ArtifactHandoffState.ARTIFACT_READY_OPERATIONALLY_BLOCKED.name)
        val blockers = parseBlockers(root.getValue("blockerIds"), "blockerIds")
        val handoff = T10ArtifactHandoff.create(parseExactIdentity(root.getValue("exactIdentity")), blockers)
        require(handoff.selfSha256 == Sha256.parse(root.getValue("selfSha256").stringValue("selfSha256"))) {
            "handoff self SHA-256 mismatch"
        }
        return handoff
    }
}

internal fun T10IdentityRequirement.canonicalValue(): T10JsonValue = when (this) {
    is T10IdentityRequirement.Resolved -> T10Canonical.obj(
        "blockerIds" to T10Canonical.array(emptyList()),
        "resolvedExactIdentity" to exactIdentity.canonicalValue(),
        "state" to T10Canonical.text(state.name),
    )
    is T10IdentityRequirement.Inert -> T10Canonical.obj(
        "blockerIds" to T10Canonical.array(blockerIds.map { T10Canonical.text(it.wireName) }),
        "resolvedExactIdentity" to T10Canonical.nullValue(),
        "state" to T10Canonical.text(state.name),
    )
}

internal fun parseIdentityRequirement(value: T10JsonValue): T10IdentityRequirement {
    val fields = value.objectFields("identityRequirement")
    fields.requireExactKeys("identityRequirement", "blockerIds", "resolvedExactIdentity", "state")
    return when (fields.getValue("state").stringValue("identityRequirement.state")) {
        T10IdentityRequirement.State.RESOLVED.name -> {
            require(fields.getValue("blockerIds").arrayValues("blockerIds").isEmpty())
            T10IdentityRequirement.Resolved(parseExactIdentity(fields.getValue("resolvedExactIdentity")))
        }
        T10IdentityRequirement.State.INERT_IDENTITY_BLOCKED.name -> {
            require(fields.getValue("resolvedExactIdentity").isNull())
            T10IdentityRequirement.Inert.of(parseBlockers(fields.getValue("blockerIds"), "blockerIds"))
        }
        else -> throw IllegalArgumentException("unknown identity requirement state")
    }
}

internal fun parseExactIdentity(value: T10JsonValue): ExactIdentity {
    val fields = value.objectFields("exactIdentity")
    fields.requireExactKeys(
        "exactIdentity", "apkArtifactId", "apkFileSha256", "candidateSetSha256", "componentId",
        "diffArtifactId", "diffFileSha256", "packSha256", "permissionId", "profileId",
        "registryFileSha256", "senderId", "signerCertificateSha256s", "signerSha256",
        "sourceArtifactId", "sourceSnapshotSha256", "variant",
    )
    fun string(name: String) = fields.getValue(name).stringValue("exactIdentity.$name")
    val certificates = fields.getValue("signerCertificateSha256s")
        .arrayValues("signerCertificateSha256s").map { Sha256.parse(it.stringValue("signer certificate hash")) }
    return ExactIdentity(
        sourceSnapshotSha256 = Sha256.parse(string("sourceSnapshotSha256")),
        diffFileSha256 = Sha256.parse(string("diffFileSha256")),
        apkFileSha256 = Sha256.parse(string("apkFileSha256")),
        signer = SignerIdentity.verified(certificates, Sha256.parse(string("signerSha256"))),
        registryFileSha256 = Sha256.parse(string("registryFileSha256")),
        packSha256 = Sha256.parse(string("packSha256")),
        candidateSetSha256 = Sha256.parse(string("candidateSetSha256")),
        variant = enumValue<T10Variant>(string("variant"), "variant"),
        senderId = enumValue<T10SenderId>(string("senderId"), "sender"),
        componentId = enumValue<T10ComponentId>(string("componentId"), "component"),
        permissionId = enumValue<T10PermissionId>(string("permissionId"), "permission"),
        profileId = enumValue<T10ProfileId>(string("profileId"), "profile"),
        sourceArtifactId = T10ArtifactId.parse(string("sourceArtifactId")),
        diffArtifactId = T10ArtifactId.parse(string("diffArtifactId")),
        apkArtifactId = T10ArtifactId.parse(string("apkArtifactId")),
    )
}

private fun parseBlockers(value: T10JsonValue, label: String): List<T10BlockerId> =
    value.arrayValues(label).map { T10BlockerId.parse(it.stringValue(label)) }.also { blockers ->
        require(blockers == blockers.distinct().sortedBy { it.wireName }) { "$label must be sorted and unique" }
    }

internal inline fun <reified E : Enum<E>> enumValue(value: String, label: String): E =
    enumValues<E>().singleOrNull { it.name == value } ?: throw IllegalArgumentException("unknown $label")

private val ZERO_SHA256 = Sha256.parse("0".repeat(64))
