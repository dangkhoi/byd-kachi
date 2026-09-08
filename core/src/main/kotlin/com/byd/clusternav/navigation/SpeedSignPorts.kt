package com.byd.clusternav.navigation

import com.byd.clusternav.contracts.SpeedLimitFrame

enum class SpeedSignOutput {
    CLUSTER,
    HUD,
}

enum class SpeedSignSubmission {
    ACCEPTED,
    QUEUE_SATURATED,
    STALE_DROPPED,
}

interface SpeedSignPort : AutoCloseable {
    val output: SpeedSignOutput
    fun publish(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission

    /** Must fence/drop queued older generations before accepting this clear. */
    fun replaceWithClear(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission
}

data class RecordedSpeedSignEmission(
    val frame: SpeedLimitFrame,
    val generation: Long,
    val priorityClear: Boolean,
)

class RecordingSpeedSignPort(override val output: SpeedSignOutput) : SpeedSignPort {
    private val lock = Any()
    private var acceptedGeneration = 0L
    private val emissions = mutableListOf<RecordedSpeedSignEmission>()

    override fun publish(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission = synchronized(lock) {
        require(frame.value != null) { "publish requires an active frame" }
        if (generation < acceptedGeneration) return SpeedSignSubmission.STALE_DROPPED
        acceptedGeneration = generation
        emissions += RecordedSpeedSignEmission(frame, generation, priorityClear = false)
        SpeedSignSubmission.ACCEPTED
    }

    override fun replaceWithClear(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission = synchronized(lock) {
        require(frame.value == null) { "replaceWithClear requires a clear frame" }
        if (generation < acceptedGeneration) return SpeedSignSubmission.STALE_DROPPED
        acceptedGeneration = generation
        emissions += RecordedSpeedSignEmission(frame, generation, priorityClear = true)
        SpeedSignSubmission.ACCEPTED
    }

    fun snapshot(): List<RecordedSpeedSignEmission> = synchronized(lock) { emissions.toList() }
    fun clearHistory() = synchronized(lock) { emissions.clear() }

    override fun close() = Unit
}

/** Runtime T7 sink: accepts typed lifecycle frames, fences generations, and performs no I/O. */
class NoopSpeedSignPort(override val output: SpeedSignOutput) : SpeedSignPort {
    private val lock = Any()
    private var acceptedGeneration = 0L

    override fun publish(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission = synchronized(lock) {
        require(frame.value != null) { "publish requires an active frame" }
        if (generation < acceptedGeneration) SpeedSignSubmission.STALE_DROPPED else {
            acceptedGeneration = generation
            SpeedSignSubmission.ACCEPTED
        }
    }

    override fun replaceWithClear(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission = synchronized(lock) {
        require(frame.value == null) { "replaceWithClear requires a clear frame" }
        if (generation < acceptedGeneration) SpeedSignSubmission.STALE_DROPPED else {
            acceptedGeneration = generation
            SpeedSignSubmission.ACCEPTED
        }
    }

    override fun close() = Unit
}
