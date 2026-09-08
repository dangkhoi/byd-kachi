package com.byd.clusternav

import android.os.SystemClock

enum class HudMirrorCapability {
    UNKNOWN,
    COUPLED_TO_CLUSTER,
    INDEPENDENT_GATE,
    UNSUPPORTED,
}

enum class HudRoadCapability {
    UNKNOWN,
    AMAP_PAYLOAD,
    SIDE_CHANNEL,
    UNSUPPORTED,
}

enum class PhysicalHudOwnership {
    UNOBSERVED,
    DURABLE_USER_ON,
}

enum class HudMirrorRequestResult {
    NO_OP_UNKNOWN,
}

data class HudMirrorSnapshot(
    val requested: Boolean,
    val requestGeneration: Long,
    val capability: HudMirrorCapability,
    val roadCapability: HudRoadCapability,
    val physicalOwnership: PhysicalHudOwnership,
    val latestCanonicalFrame: AmapFrameToken?,
    val lastRequestAtMonotonicNanos: Long?,
    val physicalWriteCount: Int,
)

/**
 * T7 fail-closed gate. Seal capability is UNKNOWN, so this controller records request/source truth
 * only. It deliberately has no Context, HAL gateway, property ID, or physical-OFF operation.
 */
class HudMirrorController(
    private val monotonicNowNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    private val lock = Any()
    private var requested = false
    private var requestGeneration = 0L
    private var lastRequestAtMonotonicNanos: Long? = null
    private var physicalOwnership = PhysicalHudOwnership.UNOBSERVED
    private var latestCanonicalFrame: AmapFrameToken? = null

    fun onCanonicalFrame(token: AmapFrameToken) = synchronized(lock) {
        val previous = latestCanonicalFrame
        if (previous == null || token.emissionToken > previous.emissionToken) {
            latestCanonicalFrame = token
        }
    }

    fun requestEnabled(enabled: Boolean): HudMirrorRequestResult = synchronized(lock) {
        if (requested != enabled) {
            requested = enabled
            check(requestGeneration != Long.MAX_VALUE) { "HUD request generation exhausted" }
            requestGeneration++
            lastRequestAtMonotonicNanos = monotonicNowNanos().also {
                require(it >= 0L) { "monotonic HUD request time must be non-negative" }
            }
        }
        HudMirrorRequestResult.NO_OP_UNKNOWN
    }

    /** A user-confirmed physical ON setting remains durable across all app-owned lifecycle events. */
    fun recordPhysicalHudConfirmedOn() = synchronized(lock) {
        physicalOwnership = PhysicalHudOwnership.DURABLE_USER_ON
    }

    fun onStop() = requestEnabled(false)
    fun onArrival() = requestEnabled(false)
    fun onOutputDisabled() = requestEnabled(false)

    fun snapshot(): HudMirrorSnapshot = synchronized(lock) {
        HudMirrorSnapshot(
            requested = requested,
            requestGeneration = requestGeneration,
            capability = HudMirrorCapability.UNKNOWN,
            roadCapability = HudRoadCapability.UNKNOWN,
            physicalOwnership = physicalOwnership,
            latestCanonicalFrame = latestCanonicalFrame,
            lastRequestAtMonotonicNanos = lastRequestAtMonotonicNanos,
            physicalWriteCount = 0,
        )
    }
}
