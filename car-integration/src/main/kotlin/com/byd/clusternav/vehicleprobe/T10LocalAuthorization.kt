package com.byd.clusternav.vehicleprobe

import com.byd.clusternav.vehicle.t10.ExactIdentity
import com.byd.clusternav.vehicle.t10.Sha256
import com.byd.clusternav.vehicle.t10.T10AuthorizedTarget
import com.byd.clusternav.vehicle.t10.T10Canonical
import com.byd.clusternav.vehicle.t10.T10SessionPlan
import com.byd.clusternav.vehicle.t10.T10TargetAuthorizationLoader
import com.byd.clusternav.vehicle.t10.T10Variant
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference

/** Wall-clock time is used only for the numeric local authorization expiry. */
internal fun interface T10EpochClock {
    fun epochMillis(): Long
}

/**
 * The one target admitted by the ignored local dadb authorization. Raw endpoint and serial values are
 * intentionally package-private and never participate in tracked output or exception messages.
 */
class ApprovedDadbTarget private constructor(
    internal val adbServerHost: String,
    internal val adbServerPort: Int,
    internal val deviceSerial: String,
    val exactIdentity: ExactIdentity,
) {
    override fun toString(): String = "ApprovedDadbTarget(redacted)"

    internal fun deviceQuery(): String = "host:transport:$deviceSerial"

    companion object {
        internal fun create(
            adbServerHost: String,
            adbServerPort: Int,
            deviceSerial: String,
            exactIdentity: ExactIdentity,
        ): ApprovedDadbTarget {
            require(HOST.matches(adbServerHost)) { "invalid approved dadb host" }
            require(adbServerPort in 1..65535) { "invalid approved dadb port" }
            require(SERIAL.matches(deviceSerial)) { "invalid approved dadb serial" }
            return ApprovedDadbTarget(adbServerHost, adbServerPort, deviceSerial, exactIdentity)
        }

        private val HOST = Regex("^[A-Za-z0-9][A-Za-z0-9.:-]{0,252}$")
        private val SERIAL = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    }
}

internal enum class T10AuthorizationMode { SESSION_N_READ_ONLY }

/**
 * Strict loader for the single fixed ignored authorization file. Construction performs no I/O so the
 * core binding gate can return before this local boundary is touched.
 */
internal class T10LocalAuthorization(
    repositoryRoot: Path,
    private val expectedSessionPlanFileSha256: Sha256,
    private val epochClock: T10EpochClock = T10EpochClock(System::currentTimeMillis),
) : T10TargetAuthorizationLoader {
    private val root = repositoryRoot.toAbsolutePath().normalize()
    private val loadedTarget = AtomicReference<ApprovedDadbTarget?>()

    override fun load(expectedIdentity: ExactIdentity): T10AuthorizedTarget {
        loadedTarget.set(null)
        val approved = loadApprovedTarget(expectedIdentity)
        loadedTarget.set(approved)
        return T10AuthorizedTarget(approved.exactIdentity)
    }

    internal fun loadApprovedTarget(expectedIdentity: ExactIdentity): ApprovedDadbTarget {
        val bytes = readFixedAuthorizationNoFollow()
        T10Canonical.parse(bytes)
        val match = AUTHORIZATION.matchEntire(bytes.toString(StandardCharsets.UTF_8))
            ?: throw IllegalArgumentException("local T10 authorization has a closed-schema mismatch")
        val values = match.groupValues
        val target = ApprovedDadbTarget.create(
            adbServerHost = values[HOST_GROUP],
            adbServerPort = values[PORT_GROUP].toIntOrNull()
                ?: throw IllegalArgumentException("invalid approved dadb port"),
            deviceSerial = values[SERIAL_GROUP],
            exactIdentity = expectedIdentity,
        )
        require(expectedIdentity.variant == T10Variant.VEHICLE_TEST) { "authorization requires VEHICLE_TEST" }
        require(Sha256.parse(values[APK_GROUP]) == expectedIdentity.apkFileSha256) { "APK binding mismatch" }
        require(Sha256.parse(values[CANDIDATE_GROUP]) == expectedIdentity.candidateSetSha256) {
            "candidate binding mismatch"
        }
        require(Sha256.parse(values[IDENTITY_GROUP]) == expectedIdentity.canonicalSha256()) {
            "exact identity binding mismatch"
        }
        val expiry = values[EXPIRY_GROUP].toLongOrNull()
            ?: throw IllegalArgumentException("authorization expiry is outside signed 64-bit range")
        require(expiry > epochClock.epochMillis()) { "local T10 authorization is expired" }
        require(values[MODE_GROUP] == T10AuthorizationMode.SESSION_N_READ_ONLY.name) {
            "authorization mode mismatch"
        }
        require(Sha256.parse(values[PACK_GROUP]) == expectedIdentity.packSha256) { "pack binding mismatch" }
        require(values[PERMISSION_GROUP] == expectedIdentity.permissionId.name) { "permission binding mismatch" }
        require(values[PROFILE_GROUP] == expectedIdentity.profileId.name) { "profile binding mismatch" }
        require(values[SCHEMA_GROUP] == SCHEMA_ID) { "authorization schema mismatch" }
        require(Sha256.parse(values[SESSION_GROUP]) == expectedSessionPlanFileSha256) {
            "session plan binding mismatch"
        }
        require(Sha256.parse(values[SIGNER_GROUP]) == expectedIdentity.signerSha256) { "signer binding mismatch" }
        return target
    }

    internal fun approvedFor(target: T10AuthorizedTarget): ApprovedDadbTarget =
        loadedTarget.get()?.takeIf { it.exactIdentity == target.exactIdentity }
            ?: throw IllegalStateException("approved target was not loaded for this exact identity")

    private fun readFixedAuthorizationNoFollow(): ByteArray {
        require(Files.isDirectory(root, NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) {
            "repository root must be a real directory"
        }
        val local = root.resolve(LOCAL_DIRECTORY)
        require(Files.isDirectory(local, NOFOLLOW_LINKS) && !Files.isSymbolicLink(local)) {
            "fixed local authorization directory is unavailable"
        }
        val file = local.resolve(AUTHORIZATION_FILE_NAME)
        require(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)) {
            "fixed local authorization file is unavailable"
        }
        Files.newByteChannel(file, setOf(StandardOpenOption.READ, NOFOLLOW_LINKS)).use { channel ->
            val size = channel.size()
            require(size in 1..MAX_AUTHORIZATION_BYTES.toLong()) { "local authorization size is invalid" }
            val buffer = ByteBuffer.allocate(size.toInt())
            while (buffer.hasRemaining()) {
                require(channel.read(buffer) >= 0) { "local authorization ended unexpectedly" }
            }
            return buffer.array()
        }
    }

    companion object {
        const val SCHEMA_ID = "clusternav.t10-local-authorization/v1"
        const val FIXED_RELATIVE_PATH = ".t10-local/session-n-read-only-authorization.json"
        private const val LOCAL_DIRECTORY = ".t10-local"
        private const val AUTHORIZATION_FILE_NAME = "session-n-read-only-authorization.json"
        private const val MAX_AUTHORIZATION_BYTES = 8 * 1024

        private const val APK_GROUP = 1
        private const val HOST_GROUP = 2
        private const val PORT_GROUP = 3
        private const val SERIAL_GROUP = 4
        private const val CANDIDATE_GROUP = 5
        private const val IDENTITY_GROUP = 6
        private const val EXPIRY_GROUP = 7
        private const val MODE_GROUP = 8
        private const val PACK_GROUP = 9
        private const val PERMISSION_GROUP = 10
        private const val PROFILE_GROUP = 11
        private const val SCHEMA_GROUP = 12
        private const val SESSION_GROUP = 13
        private const val SIGNER_GROUP = 14

        private val AUTHORIZATION = Regex(
            "^\\{\"apkFileSha256\":\"([0-9a-f]{64})\",\"approvedTargets\":\\[\\{\"adbServerHost\":\"([A-Za-z0-9][A-Za-z0-9.:-]{0,252})\"," +
                "\"adbServerPort\":([1-9][0-9]{0,4}),\"deviceSerial\":\"([A-Za-z0-9][A-Za-z0-9._:-]{0,127})\"}\\]," +
                "\"candidateSetSha256\":\"([0-9a-f]{64})\"," +
                "\"exactIdentitySha256\":\"([0-9a-f]{64})\",\"expiresAtEpochMs\":(0|[1-9][0-9]{0,18})," +
                "\"mode\":\"([A-Z0-9_]+)\",\"packSha256\":\"([0-9a-f]{64})\"," +
                "\"permissionId\":\"([A-Z0-9_]+)\",\"profileId\":\"([A-Z0-9_]+)\"," +
                "\"schemaId\":\"([a-z0-9./-]+)\",\"sessionPlanFileSha256\":\"([0-9a-f]{64})\"," +
                "\"signerSha256\":\"([0-9a-f]{64})\"}$",
        )

        internal fun canonicalBytesForVerification(
            target: ApprovedDadbTarget,
            plan: T10SessionPlan,
            expiresAtEpochMs: Long,
        ): ByteArray = T10Canonical.render(
            T10Canonical.obj(
                "approvedTargets" to T10Canonical.array(
                    listOf(
                        T10Canonical.obj(
                            "adbServerHost" to T10Canonical.text(target.adbServerHost),
                            "adbServerPort" to T10Canonical.integer(target.adbServerPort.toLong()),
                            "deviceSerial" to T10Canonical.text(target.deviceSerial),
                        ),
                    ),
                ),
                "apkFileSha256" to T10Canonical.text(target.exactIdentity.apkFileSha256.value),
                "candidateSetSha256" to T10Canonical.text(target.exactIdentity.candidateSetSha256.value),
                "exactIdentitySha256" to T10Canonical.text(target.exactIdentity.canonicalSha256().value),
                "expiresAtEpochMs" to T10Canonical.integer(expiresAtEpochMs),
                "mode" to T10Canonical.text(T10AuthorizationMode.SESSION_N_READ_ONLY.name),
                "packSha256" to T10Canonical.text(target.exactIdentity.packSha256.value),
                "permissionId" to T10Canonical.text(target.exactIdentity.permissionId.name),
                "profileId" to T10Canonical.text(target.exactIdentity.profileId.name),
                "schemaId" to T10Canonical.text(SCHEMA_ID),
                "sessionPlanFileSha256" to T10Canonical.text(plan.fileSha256.value),
                "signerSha256" to T10Canonical.text(target.exactIdentity.signerSha256.value),
            ),
        )
    }
}
