package com.byd.clusternav.navigation

/**
 * Durable boundary supplied by a later integration stage. Implementations must atomically replace the
 * complete immutable value. It intentionally has no Android View or lifecycle dependency.
 */
interface NavigationFramePersistence {
    fun load(): StoredNavigationSession?
    fun save(session: StoredNavigationSession)
    fun clear()
}

interface NavigationFrameStore {
    fun snapshot(): StoredNavigationSession?
    fun rehydrate(): StoredNavigationSession?
    fun start(session: StoredNavigationSession)
    fun append(frame: NavigationFrame): StoredNavigationSession
    fun clear()
}

/** In-process immutable snapshot backed by an explicit process-durable persistence contract. */
class PersistentNavigationFrameStore(
    private val persistence: NavigationFramePersistence
) : NavigationFrameStore {
    private val lock = Any()
    @Volatile private var current: StoredNavigationSession? = null

    override fun snapshot(): StoredNavigationSession? = current

    override fun rehydrate(): StoredNavigationSession? = synchronized(lock) {
        persistence.load()?.also { validate(it) }.also { current = it }
    }

    override fun start(session: StoredNavigationSession) = synchronized(lock) {
        validate(session)
        require(session.latestFrame == null) { "a new session cannot begin with an imported frame" }
        persistence.save(session)
        current = session
    }

    override fun append(frame: NavigationFrame): StoredNavigationSession = synchronized(lock) {
        val active = checkNotNull(current) { "no active navigation session" }
        require(frame.sessionId == active.sessionId) { "frame session does not match active session" }
        require(frame.source == active.source) { "frame source does not match active source" }
        require(frame.sequence > (active.latestFrame?.sequence ?: 0L)) { "frame sequence must increase" }
        require(frame.receivedAtEpochMs >= (active.latestFrame?.receivedAtEpochMs ?: active.startedAtEpochMs)) {
            "frame time must not move backwards"
        }
        active.copy(latestFrame = frame).also {
            persistence.save(it)
            current = it
        }
    }

    override fun clear() = synchronized(lock) {
        persistence.clear()
        current = null
    }

    private fun validate(session: StoredNavigationSession) {
        session.latestFrame?.let { frame ->
            require(frame.receivedAtEpochMs >= session.startedAtEpochMs) { "latest frame predates session" }
        }
    }
}
