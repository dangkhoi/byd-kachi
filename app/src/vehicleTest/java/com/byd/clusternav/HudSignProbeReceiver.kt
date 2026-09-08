package com.byd.clusternav

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.clusternav.vehicle.t10.BindingBlockReason
import com.byd.clusternav.vehicle.t10.FixedBinding
import com.byd.clusternav.vehicle.t10.T10FixedOperationCatalog
import com.byd.clusternav.vehicle.t10.T10ProbeId
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Inert vehicleTest-only boundary for the six closed T10 read probes.
 *
 * The manifest keeps this receiver non-exported. Calls must also use this exact same-package
 * component and carry no runtime payload. Resolving the catalog can only produce a sanitized
 * status here; this class deliberately has no transport or operation-dispatch path.
 */
class HudSignProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val probeId = acceptedProbe(context, intent) ?: return
        val status = sanitize(T10FixedOperationCatalog.resolve(probeId))
        val pending = goAsync()
        try {
            EXECUTOR.execute {
                try {
                    Log.i(TAG, "probe=${probeId.wireName} outcome=${status.outcome} reason=${status.reason.name}")
                } finally {
                    pending.finish()
                }
            }
        } catch (_: RejectedExecutionException) {
            pending.finish()
        }
    }

    private fun acceptedProbe(context: Context, intent: Intent?): T10ProbeId? {
        intent ?: return null
        val expectedComponent = ComponentName(context.packageName, HudSignProbeReceiver::class.java.name)
        if (intent.component != expectedComponent) return null
        if (intent.`package` != null || intent.selector != null) return null
        if (intent.data != null || intent.type != null || intent.identifier != null) return null
        if (intent.clipData != null || intent.categories != null || intent.extras != null) return null
        return ACTION_TO_PROBE[intent.action]
    }

    private fun sanitize(binding: FixedBinding): SanitizedStatus = when (binding) {
        is FixedBinding.Blocked -> SanitizedStatus(BLOCKED, binding.reason)
        is FixedBinding.Supported -> SanitizedStatus(
            BLOCKED,
            BindingBlockReason.MISSING_OPERATIONAL_AUTHORIZATION,
        )
    }

    private data class SanitizedStatus(
        val outcome: String,
        val reason: BindingBlockReason,
    )

    internal data class FixedProbeAction(
        val action: String,
        val probeId: T10ProbeId,
    )

    companion object {
        internal const val ACTION_LIST_PACKAGE_METADATA =
            "com.byd.clusternav.vehicleTest.T10_LIST_PACKAGE_METADATA"
        internal const val ACTION_LIST_PROPERTY_CONFIGS =
            "com.byd.clusternav.vehicleTest.T10_LIST_PROPERTY_CONFIGS"
        internal const val ACTION_LIST_SERVICE_METADATA =
            "com.byd.clusternav.vehicleTest.T10_LIST_SERVICE_METADATA"
        internal const val ACTION_READ_PACKAGE_METADATA =
            "com.byd.clusternav.vehicleTest.T10_READ_PACKAGE_METADATA"
        internal const val ACTION_READ_PROPERTY_CONFIG =
            "com.byd.clusternav.vehicleTest.T10_READ_PROPERTY_CONFIG"
        internal const val ACTION_READ_SERVICE_METADATA =
            "com.byd.clusternav.vehicleTest.T10_READ_SERVICE_METADATA"

        internal val FIXED_PROBE_ACTIONS: List<FixedProbeAction> = listOf(
            FixedProbeAction(ACTION_LIST_PACKAGE_METADATA, T10ProbeId.LIST_PACKAGE_METADATA),
            FixedProbeAction(ACTION_LIST_PROPERTY_CONFIGS, T10ProbeId.LIST_PROPERTY_CONFIGS),
            FixedProbeAction(ACTION_LIST_SERVICE_METADATA, T10ProbeId.LIST_SERVICE_METADATA),
            FixedProbeAction(ACTION_READ_PACKAGE_METADATA, T10ProbeId.READ_PACKAGE_METADATA),
            FixedProbeAction(ACTION_READ_PROPERTY_CONFIG, T10ProbeId.READ_PROPERTY_CONFIG),
            FixedProbeAction(ACTION_READ_SERVICE_METADATA, T10ProbeId.READ_SERVICE_METADATA),
        )

        private val ACTION_TO_PROBE: Map<String, T10ProbeId> =
            FIXED_PROBE_ACTIONS.associate { it.action to it.probeId }

        private const val TAG = "T10InertProbe"
        private const val BLOCKED = "BLOCKED"
        private val EXECUTOR = ThreadPoolExecutor(
            1,
            1,
            10L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(1),
            { task -> Thread(task, "T10InertProbe").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        ).apply {
            allowCoreThreadTimeOut(true)
        }
    }
}
