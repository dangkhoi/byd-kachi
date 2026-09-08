package com.byd.clusternav.speedbadge

import android.content.Context
import android.util.Log
import com.byd.clusternav.contracts.SpeedLimitFrame
import com.byd.clusternav.modules.hal.BydHal
import com.byd.clusternav.navigation.SpeedSignOutput
import com.byd.clusternav.navigation.SpeedSignPort
import com.byd.clusternav.navigation.SpeedSignSubmission

/**
 * SpeedSignPort for HUD output: writes STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET (0x4B40001C)
 * to BYDAutoStatisticDevice via BydHal reflection.
 *
 * If the HAL device is unavailable (off-car / ROM lacks the class), degrades to no-op after one log.
 * Generation fencing matches NoopSpeedSignPort semantics exactly.
 */
class HalSpeedSignPort(context: Context) : SpeedSignPort {

    companion object {
        private const val TAG = "HalSpeedSign"
        private const val FEATURE_NAME = "STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET"
        private const val FEATURE_RAW_ID = 0x4B40001C
    }

    override val output: SpeedSignOutput = SpeedSignOutput.HUD

    private val lock = Any()
    private var acceptedGeneration = 0L
    private val device: Any?
    private val featureId: Int

    init {
        device = runCatching {
            BydHal.device(BydHal.STATISTIC, BydHal.systemBypassContext(), BydHal.bypass(context))
        }.getOrNull()
        featureId = BydHal.featureId(FEATURE_NAME) ?: FEATURE_RAW_ID
        if (device == null) {
            Log.w(TAG, "StatisticDevice unavailable — HUD speed sign disabled (off-car or ROM mismatch)")
        } else {
            Log.i(TAG, "StatisticDevice ready, featureId=0x${featureId.toString(16)}")
        }
    }

    override fun publish(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission = synchronized(lock) {
        require(frame.value != null) { "publish requires an active frame" }
        if (generation < acceptedGeneration) return SpeedSignSubmission.STALE_DROPPED
        acceptedGeneration = generation
        val v = frame.value ?: return SpeedSignSubmission.STALE_DROPPED
        writeValue(v)
        SpeedSignSubmission.ACCEPTED
    }

    override fun replaceWithClear(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission = synchronized(lock) {
        require(frame.value == null) { "replaceWithClear requires a clear frame" }
        if (generation < acceptedGeneration) return SpeedSignSubmission.STALE_DROPPED
        acceptedGeneration = generation
        writeValue(0)
        SpeedSignSubmission.ACCEPTED
    }

    override fun close() = Unit

    private fun writeValue(value: Int) {
        val dev = device ?: return
        val rc = runCatching { BydHal.setInt(dev, featureId, value) }.getOrElse { e ->
            Log.w(TAG, "setInt failed: ${BydHal.root(e)}")
            return
        }
        Log.d(TAG, "ISA 0x${featureId.toString(16)}=$value rc=$rc")
    }
}
