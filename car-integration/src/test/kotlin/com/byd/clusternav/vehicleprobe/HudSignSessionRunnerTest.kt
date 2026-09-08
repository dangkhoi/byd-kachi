package com.byd.clusternav.vehicleprobe

import com.byd.clusternav.vehicle.t10.BindingBlockReason
import com.byd.clusternav.vehicle.t10.DispatchOutcome
import com.byd.clusternav.vehicle.t10.FixedReadOperation
import com.byd.clusternav.vehicle.t10.LocalEvidenceId
import com.byd.clusternav.vehicle.t10.MonotonicClock
import com.byd.clusternav.vehicle.t10.OperationOutcome
import com.byd.clusternav.vehicle.t10.RepoRelativePath
import com.byd.clusternav.vehicle.t10.SessionId
import com.byd.clusternav.vehicle.t10.T10ArtifactHandoff
import com.byd.clusternav.vehicle.t10.T10AuthorizedTarget
import com.byd.clusternav.vehicle.t10.T10BlockerId
import com.byd.clusternav.vehicle.t10.T10DispatchResultLoader
import com.byd.clusternav.vehicle.t10.T10NonceSource
import com.byd.clusternav.vehicle.t10.T10SessionLedgerSink
import com.byd.clusternav.vehicle.t10.T10SessionResultCode
import com.byd.clusternav.vehicle.t10.T10SessionRuntime
import com.byd.clusternav.vehicle.t10.T10SessionTransportFactory
import com.byd.clusternav.vehicle.t10.TrackedDispatchResult
import com.byd.clusternav.vehicle.t10.VehicleTransport
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class HudSignSessionRunnerTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `canonical handoff and plan return 23 before every runtime effect and SESSION_START`() {
        prepareFixedRunnerInputs(temporary)
        val effects = RuntimeEffects()
        val runner = HudSignSessionRunner(temporary, T10RuntimeFactory { _, _ -> effects.runtime() })
        assertEquals(T10SessionResultCode.BINDING_BLOCKED.processCode, runner.run(emptyArray()))
        effects.assertZero()
        assertFalse(Files.exists(temporary.resolve(".t10-local")))

        assertEquals(23, HudSignSessionRunner(temporary).run(emptyArray()))
        assertFalse(Files.exists(temporary.resolve(".t10-local")))
    }

    @Test
    fun `runner accepts no arguments and maps strict fixed input failures to stable exits`() {
        assertEquals(20, HudSignSessionRunner(temporary).run(arrayOf("override")))
        prepareFixedRunnerInputs(temporary)
        Files.writeString(temporary.resolve(PLAN_PATH), "{}")
        assertEquals(20, HudSignSessionRunner(temporary).run(emptyArray()))

        prepareFixedRunnerInputs(temporary)
        Files.writeString(temporary.resolve(HANDOFF_PATH), "{}")
        assertEquals(22, HudSignSessionRunner(temporary).run(emptyArray()))
        assertEquals(listOf(0) + (20..28), T10SessionResultCode.entries.map { it.processCode })
    }

    @Test
    fun `local authorization is canonical single-target numeric-expiry and exact binding`() {
        val plan = testPlan()
        val identity = testIdentity(plan)
        val target = testTarget(identity)
        val authFile = authorizationFile(temporary)
        val bytes = T10LocalAuthorization.canonicalBytesForVerification(target, plan, 2_000)
        Files.write(authFile, bytes)
        val loader = T10LocalAuthorization(temporary, plan.fileSha256, T10EpochClock { 1_000 })
        val loaded = loader.loadApprovedTarget(identity)
        assertEquals("host:transport:SERIAL-TEST", loaded.deviceQuery())
        assertEquals(identity, loader.load(identity).exactIdentity)
        assertEquals(loaded.exactIdentity, loader.approvedFor(T10AuthorizedTarget(identity)).exactIdentity)
        assertArrayEquals(bytes, Files.readAllBytes(authFile))

        Files.write(authFile, bytes + '\n'.code.toByte())
        assertThrows(IllegalArgumentException::class.java) { loader.loadApprovedTarget(identity) }

        Files.write(authFile, replaceField(bytes, "expiresAtEpochMs", "2000", "1000"))
        assertThrows(IllegalArgumentException::class.java) { loader.loadApprovedTarget(identity) }

        Files.write(authFile, replaceField(bytes, "approvedTargets", targetObject(bytes), "[]"))
        assertThrows(IllegalArgumentException::class.java) { loader.loadApprovedTarget(identity) }
    }

    @Test
    fun `every session candidate APK signer profile permission and identity drift is rejected`() {
        val plan = testPlan()
        val identity = testIdentity(plan)
        val target = testTarget(identity)
        val original = T10LocalAuthorization.canonicalBytesForVerification(target, plan, 9_000)
        val wrong = testHash("wrong").value
        val changes = listOf(
            Triple("apkFileSha256", identity.apkFileSha256.value, wrong),
            Triple("candidateSetSha256", identity.candidateSetSha256.value, wrong),
            Triple("exactIdentitySha256", identity.canonicalSha256().value, wrong),
            Triple("packSha256", identity.packSha256.value, wrong),
            Triple("sessionPlanFileSha256", plan.fileSha256.value, wrong),
            Triple("signerSha256", identity.signerSha256.value, wrong),
            Triple("mode", "SESSION_N_READ_ONLY", "UNBOUNDED"),
            Triple("profileId", identity.profileId.name, "PROFILE_UNASSIGNED"),
            Triple("permissionId", identity.permissionId.name, "PERMISSION_VENDOR_CAR"),
        )
        val loader = T10LocalAuthorization(temporary, plan.fileSha256, T10EpochClock { 1_000 })
        val file = authorizationFile(temporary)
        changes.forEach { (field, before, after) ->
            Files.write(file, replaceStringField(original, field, before, after))
            assertThrows(IllegalArgumentException::class.java) {
                loader.loadApprovedTarget(identity)
            }
        }
        Files.write(file, replaceStringField(original, "adbServerHost", "127.0.0.1", "/tmp/escape"))
        assertThrows(IllegalArgumentException::class.java) { loader.loadApprovedTarget(identity) }
        Files.write(file, replaceStringField(original, "deviceSerial", "SERIAL-TEST", "../escape"))
        assertThrows(IllegalArgumentException::class.java) { loader.loadApprovedTarget(identity) }
    }

    @Test
    fun `local authorization rejects symlink ancestor and leaf without following either`() {
        val plan = testPlan()
        val identity = testIdentity(plan)
        val bytes = T10LocalAuthorization.canonicalBytesForVerification(testTarget(identity), plan, 9_000)

        val ancestorRoot = Files.createDirectory(temporary.resolve("auth-ancestor"))
        val externalDirectory = Files.createDirectory(temporary.resolve("external-auth"))
        Files.write(externalDirectory.resolve("session-n-read-only-authorization.json"), bytes)
        Files.createSymbolicLink(ancestorRoot.resolve(".t10-local"), externalDirectory)
        assertThrows(IllegalArgumentException::class.java) {
            T10LocalAuthorization(ancestorRoot, plan.fileSha256, T10EpochClock { 1_000 })
                .loadApprovedTarget(identity)
        }

        val leafRoot = Files.createDirectory(temporary.resolve("auth-leaf"))
        val local = Files.createDirectory(leafRoot.resolve(".t10-local"))
        val externalFile = temporary.resolve("external-authorization.json")
        Files.write(externalFile, bytes)
        Files.createSymbolicLink(local.resolve("session-n-read-only-authorization.json"), externalFile)
        assertThrows(IllegalArgumentException::class.java) {
            T10LocalAuthorization(leafRoot, plan.fileSha256, T10EpochClock { 1_000 })
                .loadApprovedTarget(identity)
        }
    }

    @Test
    fun `raw evidence is bounded content-addressed private atomic and collision-safe`() {
        val root = Files.createDirectory(temporary.resolve("evidence-root"))
        val session = SessionId.parse("SESSION-0123456789ABCDEF")
        val writer = LocalEvidenceWriter(root, session)
        val id = writer.write("private stdout", "private stderr", 0)
        val leaf = root.resolve(".t10-local/evidence/${session.value}/${id.value}")
        assertTrue(Files.isRegularFile(leaf))
        val bytes = Files.readAllBytes(leaf)
        assertEquals(id, LocalEvidenceId.fromContentSha256(com.byd.clusternav.vehicle.t10.T10Canonical.sha256(bytes)))
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("private stdout"))
        assertThrows(FileAlreadyExistsException::class.java) {
            writer.write("private stdout", "private stderr", 0)
        }
        assertArrayEquals(bytes, Files.readAllBytes(leaf))
        if (Files.getFileStore(leaf).supportsFileAttributeView("posix")) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(leaf),
            )
        }
    }

    @Test
    fun `evidence writer rejects symlink ancestors and leaves`() {
        val session = SessionId.parse("SESSION-FEDCBA9876543210")
        val external = Files.createDirectory(temporary.resolve("external-evidence"))
        val ancestorRoot = Files.createDirectory(temporary.resolve("evidence-ancestor"))
        Files.createSymbolicLink(ancestorRoot.resolve(".t10-local"), external)
        assertThrows(IllegalArgumentException::class.java) {
            LocalEvidenceWriter(ancestorRoot, session).write("a", "b", 0)
        }

        val leafRoot = Files.createDirectory(temporary.resolve("evidence-leaf"))
        val writer = LocalEvidenceWriter(leafRoot, session)
        val id = writer.write("same", "payload", 0)
        val leaf = leafRoot.resolve(".t10-local/evidence/${session.value}/${id.value}")
        Files.delete(leaf)
        val externalFile = temporary.resolve("external-evidence-file")
        Files.writeString(externalFile, "do not overwrite")
        Files.createSymbolicLink(leaf, externalFile)
        assertThrows(IllegalArgumentException::class.java) { writer.write("same", "payload", 0) }
        assertEquals("do not overwrite", Files.readString(externalFile))
    }

    @Test
    fun `tracked terminal result contains only closed outcome and evidence hash and cannot be revised`() {
        val root = Files.createDirectory(temporary.resolve("result-root"))
        Files.createDirectory(root.resolve("results"))
        val evidence = LocalEvidenceId.fromContentSha256(testHash("private raw never tracked"))
        val result = TrackedDispatchResult.from(
            FixedReadOperation.LIST_PACKAGE_METADATA,
            DispatchOutcome.Completed(OperationOutcome.Success(evidence)),
        )
        val relative = RepoRelativePath.parse("results/terminal.json")
        val store = T10ResultStore(root, relative)
        val path = store.writeTerminal(result)
        val bytes = Files.readAllBytes(path)
        val parsed = T10DispatchResultLoader.load(bytes)
        assertEquals(result.selfSha256, parsed.selfSha256)
        assertEquals(evidence, parsed.localEvidenceId)
        val text = bytes.toString(StandardCharsets.UTF_8)
        assertFalse(text.contains("private raw never tracked"))
        assertFalse(text.contains(root.toString()))
        assertFalse(text.contains("message"))
        assertThrows(FileAlreadyExistsException::class.java) { store.writeTerminal(result) }

        val symlinkRoot = Files.createDirectory(temporary.resolve("result-symlink"))
        Files.createDirectory(symlinkRoot.resolve("results"))
        val external = temporary.resolve("external-result")
        Files.writeString(external, "unchanged")
        Files.createSymbolicLink(symlinkRoot.resolve("results/terminal.json"), external)
        assertThrows(IllegalArgumentException::class.java) {
            T10ResultStore(symlinkRoot, relative).writeTerminal(result)
        }
        assertEquals("unchanged", Files.readString(external))
    }

    private fun prepareFixedRunnerInputs(root: Path) {
        val plan = testPlan()
        val planFile = root.resolve(PLAN_PATH)
        Files.createDirectories(planFile.parent)
        Files.write(planFile, plan.toCanonicalBytes())
        val handoffFile = root.resolve(HANDOFF_PATH)
        Files.createDirectories(handoffFile.parent)
        val handoff = T10ArtifactHandoff.create(
            testIdentity(plan),
            listOf(T10BlockerId.UNPROVEN_APP_REACHABILITY),
        )
        Files.write(handoffFile, handoff.toCanonicalBytes())
    }

    private fun authorizationFile(root: Path): Path {
        val directory = root.resolve(".t10-local")
        if (!Files.exists(directory)) Files.createDirectory(directory)
        return directory.resolve("session-n-read-only-authorization.json")
    }

    private fun replaceStringField(bytes: ByteArray, field: String, before: String, after: String): ByteArray =
        replaceField(bytes, field, "\"$before\"", "\"$after\"")

    private fun replaceField(bytes: ByteArray, field: String, before: String, after: String): ByteArray {
        val text = bytes.toString(StandardCharsets.UTF_8)
        val needle = "\"$field\":$before"
        require(text.contains(needle)) { "missing fixture field $field" }
        return text.replace(needle, "\"$field\":$after").toByteArray()
    }

    private fun targetObject(bytes: ByteArray): String {
        val text = bytes.toString(StandardCharsets.UTF_8)
        val prefix = "\"approvedTargets\":"
        val start = text.indexOf(prefix) + prefix.length
        val end = text.indexOf("]", start) + 1
        return text.substring(start, end)
    }

    companion object {
        private const val HANDOFF_PATH = "docs/_handoff/hud-sign-vehicle-test-candidate.json"
        private const val PLAN_PATH = "docs/diagnostics/hud-sign-re/expansion/vehicle-session-plan.json"
    }
}

private class RuntimeEffects {
    private val targetLoads = AtomicInteger()
    private val factories = AtomicInteger()
    private val ledgers = AtomicInteger()
    private val clocks = AtomicInteger()
    private val nonces = AtomicInteger()

    fun runtime(): T10SessionRuntime = T10SessionRuntime(
        targetAuthorizationLoader = {
            targetLoads.incrementAndGet()
            T10AuthorizedTarget(testIdentity())
        },
        transportFactory = T10SessionTransportFactory { _, _ ->
            factories.incrementAndGet()
            VehicleTransport { _, _ -> DispatchOutcome.Blocked(BindingBlockReason.UNPROVEN_APP_REACHABILITY) }
        },
        ledgerSink = T10SessionLedgerSink { ledgers.incrementAndGet() },
        clock = MonotonicClock { clocks.incrementAndGet(); 0 },
        nonceSource = T10NonceSource { nonces.incrementAndGet(); testHash("nonce") },
    )

    fun assertZero() {
        assertEquals(0, targetLoads.get(), "local target authorization")
        assertEquals(0, factories.get(), "transport/evidence factory")
        assertEquals(0, ledgers.get(), "SESSION_START")
        assertEquals(0, clocks.get(), "freeze clock")
        assertEquals(0, nonces.get(), "freeze nonce")
    }
}
