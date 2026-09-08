package com.byd.clusternav.navigation

import java.util.UUID

/**
 * Sole owner of Pipeline-A source identity, session ID, frame sequence and freshness.
 * This Stage 2 contract is intentionally not connected to legacy runtime integrations or views.
 */
class NavigationSessionCoordinator(
    private val store: NavigationFrameStore,
    private val clusterLane: NavigationOutputPort,
    private val hud: NavigationOutputPort,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val nextSessionId: () -> String = { UUID.randomUUID().toString() },
    private val staleAfterMs: Long = 180_000L
) : AutoCloseable {
    private val lock = Any()
    private var permission = NavigationPermission.UNKNOWN
    private var session: StoredNavigationSession? = null
    private var rehydratedUnverified = false

    init {
        require(clusterLane.target == NavigationOutputTarget.CLUSTER_LANE)
        require(hud.target == NavigationOutputTarget.HUD)
        require(staleAfterMs > 0)
    }

    fun setPermission(value: NavigationPermission) = synchronized(lock) {
        permission = value
        when (value) {
            NavigationPermission.MISSING -> stopSessionLocked()
            NavigationPermission.UNKNOWN -> if (session != null) {
                invokeWithoutPeerImpact(clusterLane) { markStale() }
                invokeWithoutPeerImpact(hud) { markStale() }
            }
            NavigationPermission.GRANTED -> Unit
        }
    }

    fun startSession(source: NavigationSourceIdentity): String = synchronized(lock) {
        check(permission == NavigationPermission.GRANTED) { "notification permission is not granted" }
        if (session != null || store.snapshot() != null) stopSessionLocked()
        val id = nextSessionId().also { require(it.isNotBlank()) { "session ID must not be blank" } }
        val started = StoredNavigationSession(id, source, nowEpochMs(), null)
        store.start(started)
        session = started
        rehydratedUnverified = false
        id
    }

    /** Loads data only. Rehydration never emits, starts, retries or verifies either output. */
    fun rehydrate(): Boolean = synchronized(lock) {
        val restored = store.rehydrate() ?: return false
        session = restored
        rehydratedUnverified = true
        true
    }

    fun acceptFrame(source: NavigationSourceIdentity, content: NavigationFrameContent): NavigationFrame = synchronized(lock) {
        check(permission == NavigationPermission.GRANTED) { "notification permission is not granted" }
        val active = checkNotNull(session) { "no active navigation session" }
        require(active.source == source) { "frame source differs from the authoritative session source" }
        val frame = NavigationFrame(
            sessionId = active.sessionId,
            source = source,
            sequence = (active.latestFrame?.sequence ?: 0L) + 1L,
            receivedAtEpochMs = nowEpochMs(),
            content = content
        )
        session = store.append(frame)
        rehydratedUnverified = false
        submitWithoutCrossBackpressure(clusterLane, frame)
        submitWithoutCrossBackpressure(hud, frame)
        frame
    }

    fun setOutputEnabled(target: NavigationOutputTarget, enabled: Boolean) {
        output(target).setEnabled(enabled)
    }

    fun markDisplayVerified(target: NavigationOutputTarget, sequence: Long, observedAtEpochMs: Long = nowEpochMs()): Boolean =
        output(target).markDisplayVerified(sequence, observedAtEpochMs)

    fun refreshFreshness() = synchronized(lock) {
        val frame = session?.latestFrame ?: return
        if (rehydratedUnverified) return
        val age = (nowEpochMs() - frame.receivedAtEpochMs).coerceAtLeast(0L)
        if (age > staleAfterMs) {
            invokeWithoutPeerImpact(clusterLane) { markStale() }
            invokeWithoutPeerImpact(hud) { markStale() }
        }
    }

    /** Whole-session Stop is the only operation that clears both Pipeline-A outputs. */
    fun stopSession() = synchronized(lock) { stopSessionLocked() }

    fun snapshot(interactionContext: InteractionContext = InteractionContext.UNKNOWN): NavigationUiState = synchronized(lock) {
        val source = sourceState()
        val laneHealth = clusterLane.health()
        val hudHealth = hud.health()
        val allowed = linkedSetOf<NavigationAction>()
        val disabled = linkedMapOf<NavigationAction, NavigationActionDisabledReason>()

        when (permission) {
            NavigationPermission.UNKNOWN, NavigationPermission.MISSING -> allowed += NavigationAction.GRANT_NOTIFICATION_ACCESS
            NavigationPermission.GRANTED -> if (session == null) allowed += NavigationAction.START_NAVIGATION
        }
        if (session != null) allowed += NavigationAction.STOP_NAVIGATION
        // Nguồn không còn tươi thì phải có đường nối lại. Trước 2026-07-27 `RECONNECT_SOURCE` được khai
        // trong enum mà KHÔNG nơi nào cấp — trình dịch chỉ ra khi bắt buộc `when` phải vét cạn. Nghĩa là
        // notification ngừng cập nhật thì màn hình không có nút nào để người dùng bấm, chỉ còn tắt/bật lại
        // toàn bộ. Cùng họ với ngõ cụt đã khoá Cluster Cast sáng cùng ngày.
        if (session != null && source.freshness !is NavigationFreshness.Fresh) {
            allowed += NavigationAction.RECONNECT_SOURCE
        }
        addOutputActions(laneHealth, NavigationAction.ENABLE_CLUSTER_LANE, NavigationAction.DISABLE_CLUSTER_LANE, allowed)
        addOutputActions(hudHealth, NavigationAction.ENABLE_HUD, NavigationAction.DISABLE_HUD, allowed)
        explainUnavailable(permission, session != null, laneHealth, hudHealth, allowed, disabled)
        NavigationUiState(interactionContext, source, laneHealth, hudHealth, allowed.toSet(), disabled.toMap())
    }

    override fun close() {
        clusterLane.close()
        hud.close()
    }

    private fun sourceState(): NavigationSourceState {
        if (permission == NavigationPermission.MISSING) {
            return NavigationSourceState(
                permission, null, null,
                NavigationFreshness.Unknown(NavigationSourceReason.PERMISSION_MISSING),
                null, NavigationSourceReason.PERMISSION_MISSING
            )
        }
        val active = session
        if (active == null) {
            val reason = if (permission == NavigationPermission.UNKNOWN) {
                NavigationSourceReason.PERMISSION_UNKNOWN
            } else NavigationSourceReason.NO_ACTIVE_SESSION
            return NavigationSourceState(permission, null, null, NavigationFreshness.Unknown(reason), null, reason)
        }
        val frame = active.latestFrame
        if (frame == null) {
            return NavigationSourceState(
                permission, active.source, active.sessionId,
                NavigationFreshness.Unknown(NavigationSourceReason.WAITING_FOR_FRAME),
                null, NavigationSourceReason.WAITING_FOR_FRAME
            )
        }
        if (permission == NavigationPermission.UNKNOWN) {
            val age = (nowEpochMs() - frame.receivedAtEpochMs).coerceAtLeast(0L)
            return NavigationSourceState(
                permission, active.source, active.sessionId,
                NavigationFreshness.Stale(age, NavigationSourceReason.SOURCE_DISCONNECTED),
                frame.receivedAtEpochMs, NavigationSourceReason.SOURCE_DISCONNECTED,
            )
        }
        if (rehydratedUnverified) {
            return NavigationSourceState(
                permission, active.source, active.sessionId,
                NavigationFreshness.Unknown(NavigationSourceReason.PROCESS_REHYDRATED_UNVERIFIED),
                frame.receivedAtEpochMs, NavigationSourceReason.PROCESS_REHYDRATED_UNVERIFIED
            )
        }
        val age = (nowEpochMs() - frame.receivedAtEpochMs).coerceAtLeast(0L)
        return if (age > staleAfterMs) {
            NavigationSourceState(
                permission, active.source, active.sessionId,
                NavigationFreshness.Stale(age, NavigationSourceReason.FRAME_EXPIRED),
                frame.receivedAtEpochMs, NavigationSourceReason.FRAME_EXPIRED
            )
        } else {
            NavigationSourceState(
                permission, active.source, active.sessionId,
                NavigationFreshness.Fresh(age), frame.receivedAtEpochMs, null
            )
        }
    }

    private fun stopSessionLocked() {
        store.clear()
        session = null
        rehydratedUnverified = false
        invokeWithoutPeerImpact(clusterLane) { stopSession() }
        invokeWithoutPeerImpact(hud) { stopSession() }
    }

    private fun submitWithoutCrossBackpressure(port: NavigationOutputPort, frame: NavigationFrame) {
        try {
            port.submit(frame)
        } catch (error: RuntimeException) {
            try {
                port.recordFault(NavigationOutputFailureReason.INTERNAL_CONTRACT_ERROR, error.message)
            } catch (_: RuntimeException) {
                // Fault reporting belongs to this output and must not suppress its peer.
            }
        }
    }

    private fun invokeWithoutPeerImpact(port: NavigationOutputPort, action: NavigationOutputPort.() -> Unit) {
        try {
            port.action()
        } catch (error: RuntimeException) {
            try { port.recordFault(NavigationOutputFailureReason.INTERNAL_CONTRACT_ERROR, error.message) } catch (_: RuntimeException) { }
        }
    }

    private fun output(target: NavigationOutputTarget): NavigationOutputPort = when (target) {
        NavigationOutputTarget.CLUSTER_LANE -> clusterLane
        NavigationOutputTarget.HUD -> hud
        NavigationOutputTarget.SPEED_SIGN -> throw IllegalArgumentException("SPEED_SIGN has no NavigationOutputPort in coordinator")
    }

    /**
     * Ghi lý do cho MỌI phép không được cấp.
     *
     * Bản trước tạo map rồi truyền đi mà không ghi gì, nên màn hình không có gì để nói khi người dùng bấm
     * vào một nút mờ. Đây là cùng một lỗi đã khoá người dùng ở Cluster Cast sáng 2026-07-27, chỉ khác là ở
     * Navigation nó chưa gây hậu quả thấy được — chưa gây không có nghĩa là đúng.
     *
     * Cố ý KHÔNG thêm luật chặn mới: hàm này chỉ giải thích trạng thái đang có, không đổi phép nào.
     */
    private fun explainUnavailable(
        permission: NavigationPermission,
        hasSession: Boolean,
        laneHealth: NavigationOutputHealth,
        hudHealth: NavigationOutputHealth,
        allowed: Set<NavigationAction>,
        disabled: MutableMap<NavigationAction, NavigationActionDisabledReason>,
    ) {
        NavigationAction.entries.filterNot { it in allowed }.forEach { action ->
            disabled[action] = when (action) {
                NavigationAction.GRANT_NOTIFICATION_ACCESS ->
                    NavigationActionDisabledReason.PERMISSION_ALREADY_GRANTED
                NavigationAction.START_NAVIGATION ->
                    if (permission != NavigationPermission.GRANTED) {
                        NavigationActionDisabledReason.PERMISSION_REQUIRED
                    } else NavigationActionDisabledReason.SESSION_ALREADY_RUNNING
                NavigationAction.STOP_NAVIGATION -> NavigationActionDisabledReason.NO_ACTIVE_SESSION
                NavigationAction.RECONNECT_SOURCE ->
                    if (!hasSession) NavigationActionDisabledReason.NO_ACTIVE_SESSION
                    else NavigationActionDisabledReason.SOURCE_IS_FRESH
                NavigationAction.ENABLE_CLUSTER_LANE -> NavigationActionDisabledReason.OUTPUT_ALREADY_ENABLED
                NavigationAction.DISABLE_CLUSTER_LANE -> NavigationActionDisabledReason.OUTPUT_ALREADY_DISABLED
                NavigationAction.ENABLE_HUD -> NavigationActionDisabledReason.OUTPUT_ALREADY_ENABLED
                NavigationAction.DISABLE_HUD -> NavigationActionDisabledReason.OUTPUT_ALREADY_DISABLED
                NavigationAction.RETRY_CLUSTER_LANE, NavigationAction.RETRY_HUD ->
                    NavigationActionDisabledReason.OUTPUT_HAS_NO_FAULT
            }
        }
    }

    private fun addOutputActions(
        health: NavigationOutputHealth,
        enable: NavigationAction,
        disable: NavigationAction,
        allowed: MutableSet<NavigationAction>
    ) {
        allowed += if (health.enabled) disable else enable
        // Giới hạn đã biết (đo 2026-07-27, NavigationWedgedWorkerTest): nếu luồng giao duy nhất kẹt ở I/O
        // bỏ qua interrupt thì thử lại KHÔNG thể thành công — hàng vẫn đầy, luồng vẫn kẹt. Vẫn cấp RETRY vì
        // phần lớn FAULT là do một lần giao lỗi và thử lại chữa được; nhưng đừng coi RETRY là bảo đảm.
        // Muốn chữa ca kẹt thật thì phải dựng lại executor, và chưa có bằng chứng ngoài đời rằng nó xảy ra.
        if (health.status is NavigationOutputStatus.FAULT) {
            allowed += if (health.target == NavigationOutputTarget.CLUSTER_LANE) {
                NavigationAction.RETRY_CLUSTER_LANE
            } else NavigationAction.RETRY_HUD
        }
    }
}
