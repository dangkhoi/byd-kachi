package com.byd.clusternav.navigation

/** Closed permission truth for the authoritative navigation source. */
enum class NavigationPermission { UNKNOWN, MISSING, GRANTED }

enum class InteractionContext { UNKNOWN, MOVING, PARKED }

enum class NavigationSourceReason {
    PERMISSION_UNKNOWN,
    PERMISSION_MISSING,
    NO_ACTIVE_SESSION,
    WAITING_FOR_FRAME,
    PROCESS_REHYDRATED_UNVERIFIED,
    FRAME_EXPIRED,
    SOURCE_DISCONNECTED,
    SOURCE_CHANGED
}

data class NavigationSourceIdentity(
    val packageName: String,
    val displayName: String? = null
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(displayName == null || displayName.isNotBlank()) { "displayName must be null or non-blank" }
    }
}

sealed interface NavigationFreshness {
    data class Unknown(val reason: NavigationSourceReason) : NavigationFreshness
    data class Fresh(val ageMs: Long) : NavigationFreshness {
        init { require(ageMs >= 0) }
    }
    data class Stale(val ageMs: Long, val reason: NavigationSourceReason) : NavigationFreshness {
        init {
            require(ageMs >= 0)
            require(reason == NavigationSourceReason.FRAME_EXPIRED || reason == NavigationSourceReason.SOURCE_DISCONNECTED)
        }
    }
}

data class NavigationSourceState(
    val permission: NavigationPermission,
    val identity: NavigationSourceIdentity?,
    val sessionId: String?,
    val freshness: NavigationFreshness,
    val lastFrameAtEpochMs: Long?,
    val reason: NavigationSourceReason?
) {
    init {
        require(sessionId == null || sessionId.isNotBlank()) { "sessionId must be null or non-blank" }
        require(sessionId == null || identity != null) { "a session requires a source identity" }
        require(lastFrameAtEpochMs == null || sessionId != null) { "a frame timestamp requires a session" }
        require(permission != NavigationPermission.MISSING || (identity == null && sessionId == null)) {
            "missing permission cannot expose source or session identity"
        }
    }
}

enum class NavigationOutputTarget { CLUSTER_LANE, HUD, SPEED_SIGN }

enum class NavigationOutputFailureReason {
    DELIVERY_THROWN,
    DEADLINE_EXCEEDED,
    QUEUE_SATURATED,
    EXECUTOR_REJECTED,
    DISPLAY_ACK_REJECTED,
    INTERNAL_CONTRACT_ERROR
}

/** EMITTING means attempted delivery only; verification is always explicit. */
sealed interface NavigationOutputStatus {
    data object OFF : NavigationOutputStatus
    data object STARTING : NavigationOutputStatus
    data object EMITTING : NavigationOutputStatus
    data object DISPLAY_VERIFIED : NavigationOutputStatus
    data object STALE : NavigationOutputStatus
    data class FAULT(val reason: NavigationOutputFailureReason, val detail: String? = null) : NavigationOutputStatus
}

data class NavigationOutputHealth(
    val target: NavigationOutputTarget,
    val enabled: Boolean,
    val status: NavigationOutputStatus,
    val pendingCount: Int,
    val cachedSequence: Long?,
    val lastAttemptAtEpochMs: Long?,
    val lastCompletedAtEpochMs: Long?,
    val lastVerifiedAtEpochMs: Long?
) {
    init { require(pendingCount >= 0) }
}

enum class NavigationAction {
    GRANT_NOTIFICATION_ACCESS,
    START_NAVIGATION,
    RECONNECT_SOURCE,
    STOP_NAVIGATION,
    ENABLE_CLUSTER_LANE,
    DISABLE_CLUSTER_LANE,
    ENABLE_HUD,
    DISABLE_HUD,
    RETRY_CLUSTER_LANE,
    RETRY_HUD
}

/**
 * Vì sao một phép không dùng được.
 *
 * Trước 2026-07-27 enum này được khai, được luồn qua `NavigationUiState`, và **không nơi nào ghi vào** —
 * map `disabledReasons` luôn rỗng. Nghĩa là màn hình chỉ biết phép nào bật, không biết nói vì sao phép
 * kia tắt. Đúng lỗi mà Cluster Cast đã trả giá trong ngày: người dùng thấy nút mờ và không có gì để làm
 * tiếp. Giờ mọi phép không được cấp đều phải có một lý do đọc được.
 */
enum class NavigationActionDisabledReason {
    PERMISSION_REQUIRED,
    PERMISSION_ALREADY_GRANTED,
    SESSION_ALREADY_RUNNING,
    NO_ACTIVE_SESSION,
    SOURCE_NOT_FRESH,
    OUTPUT_ALREADY_ENABLED,
    OUTPUT_ALREADY_DISABLED,
    OUTPUT_HAS_NO_FAULT,
    SOURCE_IS_FRESH,
}

data class NavigationFrameContent(
    val maneuverCode: Int?,
    val maneuverText: String?,
    val distanceMeters: Int?,
    val roadName: String?,
    val etaEpochMs: Long?,
    val routeRemainingMeters: Int?,
    val routeRemainingSeconds: Int?,
    val arrivalClock: String?,
    /**
     * Maneuver TRUNG LẬP — quyết định hướng rẽ duy nhất của khung, độc lập nguồn/đầu ra. ĐÂY là contract
     * ngữ nghĩa mà cả hai encoder (làn cụm [Maneuver.toAmapIcon], HUD [Maneuver.toHudIcon]) đọc. Trường
     * [maneuverCode] (mã AMAP thô) giữ lại cho tương thích ngược + transport của các owner ghi-thẳng-HAL.
     */
    val maneuver: Maneuver? = null,
) {
    init {
        require(distanceMeters == null || distanceMeters >= 0) { "distanceMeters must be null or non-negative" }
        require(maneuverText == null || maneuverText.isNotBlank()) { "maneuverText must be null or non-blank" }
        require(roadName == null || roadName.isNotBlank()) { "roadName must be null or non-blank" }
        require(routeRemainingMeters == null || routeRemainingMeters >= 0) { "routeRemainingMeters must be null or non-negative" }
        require(routeRemainingSeconds == null || routeRemainingSeconds >= 0) { "routeRemainingSeconds must be null or non-negative" }
        require(arrivalClock == null || arrivalClock.matches(Regex("\\d{1,2}:\\d{2}"))) { "arrivalClock must be null or HH:mm" }
    }
}

data class NavigationFrame(
    val sessionId: String,
    val source: NavigationSourceIdentity,
    val sequence: Long,
    val receivedAtEpochMs: Long,
    val content: NavigationFrameContent
) {
    init {
        require(sessionId.isNotBlank())
        require(sequence > 0)
        require(receivedAtEpochMs >= 0)
    }
}

data class StoredNavigationSession(
    val sessionId: String,
    val source: NavigationSourceIdentity,
    val startedAtEpochMs: Long,
    val latestFrame: NavigationFrame?
) {
    init {
        require(sessionId.isNotBlank())
        require(startedAtEpochMs >= 0)
        require(latestFrame == null || latestFrame.sessionId == sessionId)
        require(latestFrame == null || latestFrame.source == source)
    }
}

class NavigationUiState(
    val interactionContext: InteractionContext,
    val source: NavigationSourceState,
    val clusterLane: NavigationOutputHealth,
    val hud: NavigationOutputHealth,
    allowedActions: Set<NavigationAction>,
    disabledReasons: Map<NavigationAction, NavigationActionDisabledReason>
) {
    val allowedActions: Set<NavigationAction> =
        java.util.Collections.unmodifiableSet(LinkedHashSet(allowedActions))
    val disabledReasons: Map<NavigationAction, NavigationActionDisabledReason> =
        java.util.Collections.unmodifiableMap(LinkedHashMap(disabledReasons))
}

enum class OutputSubmission { ACCEPTED, DISABLED, REJECTED_QUEUE_FULL, REJECTED_EXECUTOR }

interface NavigationOutputPort : AutoCloseable {
    val target: NavigationOutputTarget
    fun setEnabled(enabled: Boolean)
    fun submit(frame: NavigationFrame): OutputSubmission
    fun markDisplayVerified(sequence: Long, observedAtEpochMs: Long): Boolean
    fun markStale()
    fun recordFault(reason: NavigationOutputFailureReason, detail: String? = null)
    fun stopSession()
    fun health(): NavigationOutputHealth
}

fun interface NavigationFrameDelivery {
    fun deliver(frame: NavigationFrame)
}

data class OutputAdapterConfig(
    val queueCapacity: Int = 8,
    val deliveryDeadlineMs: Long = 250L
) {
    init {
        require(queueCapacity > 0)
        require(deliveryDeadlineMs > 0)
    }
}
