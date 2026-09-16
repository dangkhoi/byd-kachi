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
    /**
     * Thế hệ phiên nghe HIỆN TẠI, đọc ĐỒNG BỘ ngay trong `updateAppWidget` — tức TRƯỚC lượt `post` sang
     * main-looper, đúng chỗ cửa sổ đua mở ra. Xem [VietMapAppWidgetHostView.updateAppWidget].
     */
    private val listenGeneration: () -> Long,
    private val onViewUpdated: (Int, AppWidgetHostView, Long) -> Unit,
) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo,
    ): AppWidgetHostView = VietMapAppWidgetHostView(context, listenGeneration, onViewUpdated)
}

/**
 * Retained in memory only. It is never added to ClusterNav's visible hierarchy.
 * A main-handler callback runs after RemoteViews has synchronously applied/reapplied its actions.
 */
private class VietMapAppWidgetHostView(
    context: Context,
    private val listenGeneration: () -> Long,
    private val onViewUpdated: (Int, AppWidgetHostView, Long) -> Unit,
) : AppWidgetHostView(context) {
    private val main = Handler(Looper.getMainLooper())

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        // Chụp thế hệ TẠI ĐÂY, không phải trong lambda đã post: cầu có thể `stop()` rồi `start()` lại (++thế hệ)
        // trong lúc khung này còn nằm trong hàng đợi main-looper, và đó đúng là lượt callback phải bị vứt.
        val generation = listenGeneration()
        main.post { onViewUpdated(appWidgetId, this, generation) }
    }
}
