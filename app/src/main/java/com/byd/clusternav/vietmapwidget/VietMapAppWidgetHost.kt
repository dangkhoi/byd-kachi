package com.byd.clusternav.vietmapwidget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

internal class VietMapAppWidgetHost(
    context: Context,
    hostId: Int,
    private val onViewUpdated: (Int, AppWidgetHostView) -> Unit,
) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo,
    ): AppWidgetHostView = VietMapAppWidgetHostView(context, onViewUpdated)
}

/**
 * Retained in memory only. It is never added to ClusterNav's visible hierarchy.
 * A main-handler callback runs after RemoteViews has synchronously applied/reapplied its actions.
 */
private class VietMapAppWidgetHostView(
    context: Context,
    private val onViewUpdated: (Int, AppWidgetHostView) -> Unit,
) : AppWidgetHostView(context) {
    private val main = Handler(Looper.getMainLooper())

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        main.post { onViewUpdated(appWidgetId, this) }
    }
}
