package com.byd.clusternav.vehicleprobe

import com.byd.clusternav.vehicle.t10.LocalEvidenceId
import com.byd.clusternav.vehicle.t10.RepoRelativePath
import com.byd.clusternav.vehicle.t10.SessionId
import com.byd.clusternav.vehicle.t10.SessionPackFreeze
import com.byd.clusternav.vehicle.t10.T10Canonical
import com.byd.clusternav.vehicle.t10.T10DispatchResultLoader
import com.byd.clusternav.vehicle.t10.T10LedgerEvent
import com.byd.clusternav.vehicle.t10.T10LedgerEventType
import com.byd.clusternav.vehicle.t10.T10LedgerLoader
import com.byd.clusternav.vehicle.t10.T10LedgerValidator
import com.byd.clusternav.vehicle.t10.T10ResultLedger
import com.byd.clusternav.vehicle.t10.T10SessionLedgerSink
import com.byd.clusternav.vehicle.t10.TrackedDispatchResult
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

internal fun interface LocalEvidenceSink {
    @Throws(IOException::class)
    fun write(
        stdout: String,
        stderr: String,
        exitCode: Int,
        publicationAllowed: () -> Boolean,
    ): LocalEvidenceId
}

internal class EvidencePayloadTooLargeException : IllegalArgumentException("dadb evidence payload is too large")
internal class EvidencePublicationCancelledException : IOException("dadb evidence publication was cancelled")

/** Raw dadb output is local-only and content-addressed; the returned ID contains no path or payload. */
internal class LocalEvidenceWriter(
    repositoryRoot: Path,
    private val sessionId: SessionId,
) : LocalEvidenceSink {
    private val root = repositoryRoot.toAbsolutePath().normalize()

    internal fun write(stdout: String, stderr: String, exitCode: Int): LocalEvidenceId =
        write(stdout, stderr, exitCode) { true }

    override fun write(
        stdout: String,
        stderr: String,
        exitCode: Int,
        publicationAllowed: () -> Boolean,
    ): LocalEvidenceId {
        if (!publicationAllowed()) throw EvidencePublicationCancelledException()
        if (!isWithinBounds(stdout, stderr)) throw EvidencePayloadTooLargeException()
        val bytes = T10Canonical.render(
            T10Canonical.obj(
                "exitCode" to T10Canonical.integer(exitCode.toLong()),
                "stderr" to T10Canonical.text(stderr),
                "stdout" to T10Canonical.text(stdout),
            ),
        )
        val evidenceId = LocalEvidenceId.fromContentSha256(T10Canonical.sha256(bytes))
        val sessionDirectory = SecureT10Files.ensurePrivateDirectories(
            root,
            listOf(LOCAL_DIRECTORY, EVIDENCE_DIRECTORY, sessionId.value),
        )
        SecureT10Files.writeNewAtomically(
            sessionDirectory.resolve(evidenceId.value),
            bytes,
            ownerOnly = true,
            publicationAllowed = publicationAllowed,
        )
        return evidenceId
    }

    companion object {
        internal fun isWithinBounds(stdout: String, stderr: String): Boolean {
            val stdoutSize = stdout.toByteArray(StandardCharsets.UTF_8).size
            val stderrSize = stderr.toByteArray(StandardCharsets.UTF_8).size
            return stdoutSize <= MAX_STREAM_BYTES && stderrSize <= MAX_STREAM_BYTES &&
                stdoutSize + stderrSize <= MAX_COMBINED_BYTES
        }

        internal const val MAX_STREAM_BYTES = 64 * 1024
        internal const val MAX_COMBINED_BYTES = 96 * 1024
        private const val LOCAL_DIRECTORY = ".t10-local"
        private const val EVIDENCE_DIRECTORY = "evidence"
    }
}

/** One-shot canonical result store. Terminal files and SESSION_START ledgers are never revised. */
internal class T10ResultStore private constructor(
    repositoryRoot: Path,
    private val output: RepoRelativePath?,
    private val localSessionLedger: Boolean,
) : T10SessionLedgerSink {
    private val root = repositoryRoot.toAbsolutePath().normalize()

    constructor(repositoryRoot: Path, output: RepoRelativePath) : this(repositoryRoot, output, false)

    fun writeTerminal(result: TrackedDispatchResult): Path {
        require(!localSessionLedger) { "this result store is reserved for a session ledger" }
        val relative = requireNotNull(output)
        val bytes = result.toCanonicalBytes()
        val reparsed = T10DispatchResultLoader.load(bytes)
        require(reparsed.selfSha256 == result.selfSha256 && reparsed.operation == result.operation &&
            reparsed.state == result.state && reparsed.localEvidenceId == result.localEvidenceId
        ) { "dispatch result failed canonical round trip" }
        val target = relative.resolveForCreateNoFollow(root)
        SecureT10Files.writeNewAtomically(target, bytes, ownerOnly = false)
        return target
    }

    override fun appendSessionStart(freeze: SessionPackFreeze) {
        require(localSessionLedger && output == null) { "tracked dispatch store cannot accept a session ledger" }
        val start = T10LedgerEvent.seal(
            eventType = T10LedgerEventType.SESSION_START,
            freeze = freeze,
            sequence = 1,
            elapsedOffsetMs = 0,
            previousEventHash = null,
        )
        val ledger = T10ResultLedger.create(freeze, listOf(start))
        val bytes = ledger.toCanonicalBytes()
        val reparsed = T10LedgerLoader.load(bytes, freeze)
        require(T10LedgerValidator.validateSessionN(reparsed).events.single().eventSha256 == start.eventSha256) {
            "SESSION_START ledger failed canonical validation"
        }
        val directory = SecureT10Files.ensurePrivateDirectories(
            root,
            listOf(LOCAL_DIRECTORY, RESULTS_DIRECTORY, freeze.sessionId.value),
        )
        val target = directory.resolve("SESSION-START-${ledger.selfSha256.value}")
        SecureT10Files.writeNewAtomically(target, bytes, ownerOnly = true)
    }

    companion object {
        private const val LOCAL_DIRECTORY = ".t10-local"
        private const val RESULTS_DIRECTORY = "results"

        fun forLocalSession(repositoryRoot: Path): T10ResultStore = T10ResultStore(repositoryRoot, null, true)
    }
}

private object SecureT10Files {
    fun ensurePrivateDirectories(root: Path, segments: List<String>): Path {
        require(Files.isDirectory(root, NOFOLLOW_LINKS) && !Files.isSymbolicLink(root)) {
            "repository root must be a real directory"
        }
        var current = root
        segments.forEach { segment ->
            require(SAFE_SEGMENT.matches(segment) && segment != "." && segment != "..") {
                "unsafe fixed local path segment"
            }
            current = current.resolve(segment)
            if (!Files.exists(current, NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(current, *directoryAttributes(current.parent))
                } catch (_: FileAlreadyExistsException) {
                    // A racing creator is accepted only after the same no-follow validation below.
                }
            }
            require(Files.isDirectory(current, NOFOLLOW_LINKS) && !Files.isSymbolicLink(current)) {
                "local evidence path must contain only real directories"
            }
            setOwnerOnlyDirectoryIfPosix(current)
        }
        return current
    }

    @Synchronized
    fun writeNewAtomically(
        target: Path,
        bytes: ByteArray,
        ownerOnly: Boolean,
        publicationAllowed: () -> Boolean = { true },
    ) {
        validateParentAndLeaf(target)
        if (Files.exists(target, NOFOLLOW_LINKS)) throw FileAlreadyExistsException(target.toString())
        val temporary = target.parent.resolve(".${target.fileName}.tmp-${UUID.randomUUID()}")
        try {
            val options = setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS)
            FileChannel.open(temporary, options, *fileAttributes(temporary.parent, ownerOnly)).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            require(Files.isRegularFile(temporary, NOFOLLOW_LINKS) && !Files.isSymbolicLink(temporary)) {
                "atomic temporary file was replaced"
            }
            if (!publicationAllowed()) throw EvidencePublicationCancelledException()
            if (Files.exists(target, NOFOLLOW_LINKS)) throw FileAlreadyExistsException(target.toString())
            try {
                Files.move(temporary, target, ATOMIC_MOVE)
            } catch (unsupported: AtomicMoveNotSupportedException) {
                throw IOException("atomic move is required for T10 output", unsupported)
            }
            require(Files.isRegularFile(target, NOFOLLOW_LINKS) && !Files.isSymbolicLink(target)) {
                "atomic target is not a regular file"
            }
            if (ownerOnly) setOwnerOnlyFileIfPosix(target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validateParentAndLeaf(target: Path) {
        val normalized = target.toAbsolutePath().normalize()
        require(normalized == target.toAbsolutePath() && Files.isDirectory(target.parent, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(target.parent)
        ) { "T10 output parent must be a real directory" }
        require(!Files.isSymbolicLink(target)) { "T10 output leaf must not be a symlink" }
    }

    private fun directoryAttributes(parent: Path): Array<FileAttribute<*>> =
        if (supportsPosix(parent)) arrayOf(PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS)) else emptyArray()

    private fun fileAttributes(parent: Path, ownerOnly: Boolean): Array<FileAttribute<*>> =
        if (ownerOnly && supportsPosix(parent)) {
            arrayOf(PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))
        } else emptyArray()

    private fun setOwnerOnlyDirectoryIfPosix(path: Path) {
        if (supportsPosix(path)) Files.setPosixFilePermissions(path, DIRECTORY_PERMISSIONS)
    }

    private fun setOwnerOnlyFileIfPosix(path: Path) {
        if (supportsPosix(path)) Files.setPosixFilePermissions(path, FILE_PERMISSIONS)
    }

    private fun supportsPosix(path: Path): Boolean = Files.getFileStore(path).supportsFileAttributeView("posix")

    private val SAFE_SEGMENT = Regex("^[A-Za-z0-9_.-]+$")
    private val DIRECTORY_PERMISSIONS = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
    )
    private val FILE_PERMISSIONS = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
    )
}
