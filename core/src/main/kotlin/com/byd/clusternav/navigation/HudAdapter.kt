package com.byd.clusternav.navigation

/** HUD endpoint. Its queue, delivery executor, deadline scheduler, cache and health are never shared. */
class HudAdapter(
    delivery: NavigationFrameDelivery,
    config: OutputAdapterConfig = OutputAdapterConfig(),
    nowEpochMs: () -> Long = System::currentTimeMillis,
    initiallyEnabled: Boolean = false
) : NavigationOutputPort {
    private val worker = BoundedNavigationOutputWorker(
        NavigationOutputTarget.HUD,
        "navigation-hud",
        delivery,
        config,
        nowEpochMs,
        initiallyEnabled
    )

    override val target = NavigationOutputTarget.HUD
    override fun setEnabled(enabled: Boolean) = worker.setEnabled(enabled)
    override fun submit(frame: NavigationFrame) = worker.submit(frame)
    override fun markDisplayVerified(sequence: Long, observedAtEpochMs: Long) =
        worker.markDisplayVerified(sequence, observedAtEpochMs)
    override fun markStale() = worker.markStale()
    override fun recordFault(reason: NavigationOutputFailureReason, detail: String?) = worker.recordFault(reason, detail)
    override fun stopSession() = worker.stopSession()
    override fun health() = worker.health()
    override fun close() = worker.close()
}
