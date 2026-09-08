package com.byd.clusternav.vietmapwidget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.AdbKeys
import com.byd.clusternav.Lang
import com.byd.clusternav.R
import com.byd.clusternav.ThemeMode
import com.byd.clusternav.carexec.LocalDeviceShell

class VietMapWidgetDiagActivity : Activity() {
    private lateinit var bridge: VietMapWidgetBridge
    private lateinit var bindingText: TextView
    private lateinit var valuesText: TextView
    private var pendingSlot: VietMapWidgetSlot? = null
    private var pendingId: Int? = null

    private val snapshotListener: (VietMapWidgetSnapshot) -> Unit = { snapshot ->
        runOnUiThread { render(snapshot) }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeMode.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = VietMapWidgetBridge.get(applicationContext)
        pendingSlot = savedInstanceState?.getString(STATE_PENDING_SLOT)?.let(VietMapWidgetSlot::valueOf)
        pendingId = savedInstanceState?.getInt(STATE_PENDING_ID)?.takeIf { it >= 0 }
        setContentView(buildContent())
        render(bridge.snapshot())
    }

    override fun onStart() {
        super.onStart()
        bridge.start(VietMapWidgetOwner.DIAGNOSTICS)
        bridge.addListener(snapshotListener)
        render(bridge.snapshot())
    }

    override fun onStop() {
        bridge.removeListener(snapshotListener)
        bridge.stop(VietMapWidgetOwner.DIAGNOSTICS)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingSlot?.let { outState.putString(STATE_PENDING_SLOT, it.name) }
        pendingId?.let { outState.putInt(STATE_PENDING_ID, it) }
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_BIND_WIDGET) return
        val slot = pendingSlot
        val id = pendingId
        pendingSlot = null
        pendingId = null
        if (slot == null || id == null) {
            toast("Không tìm thấy yêu cầu bind đang chờ")
            return
        }
        val bound = bridge.completeBinding(slot, id, resultCode == RESULT_OK)
        if (bound) {
            bindNextMissing()
        } else {
            toast("Đã huỷ bind ${slot.displayName}")
            render(bridge.snapshot())
        }
    }

    private fun buildContent(): ScrollView {
        val pad = dp(20)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(text("VietMap data bridge", 24f, true))
        column.addView(text(
            "Các widget VietMap được bind như nguồn dữ liệu nội bộ. ClusterNav không hiển thị giao diện widget VietMap.",
            14f,
        ).withTop(dp(6)))
        bindingText = text("Đang kiểm tra binding…", 14f).withTop(dp(18))
        column.addView(bindingText)
        valuesText = text("Chưa có dữ liệu", 15f).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }.withTop(dp(18))
        column.addView(valuesText)

        column.addView(Button(this).apply {
            text = "Bind các nguồn VietMap"
            isAllCaps = false
            minHeight = dp(52)
            setTextColor(getColor(android.R.color.white))
            setBackgroundResource(R.drawable.btn_primary)
            setOnClickListener { bindNextMissing() }
        }.withTop(dp(22)))
        column.addView(Button(this).apply {
            text = "Xoá binding và bind lại"
            isAllCaps = false
            minHeight = dp(52)
            setTextColor(getColor(R.color.warn_amber))
            setBackgroundResource(R.drawable.btn_warning_outline)
            setOnClickListener {
                pendingSlot = null
                pendingId = null
                bridge.unbindAll()
                render(bridge.snapshot())
            }
        }.withTop(dp(10)))
        column.addView(text(
            "Fresh ≤ 5 giây · stale 5–30 giây · unavailable > 30 giây. Dữ liệu stale luôn bị xoá khỏi snapshot.",
            12f,
        ).withTop(dp(18)))

        return ScrollView(this).apply { addView(column) }
    }

    private fun bindNextMissing(allowGrantRetry: Boolean = true) {
        val next = bridge.bindingStatuses().firstOrNull { !it.bound }
        if (next == null) {
            toast("Các nguồn VietMap đã được bind")
            render(bridge.snapshot())
            return
        }
        when (val result = bridge.beginBinding(next.slot)) {
            is VietMapWidgetBindResult.Bound -> bindNextMissing()
            is VietMapWidgetBindResult.ConsentRequired -> {
                pendingSlot = result.slot
                pendingId = result.appWidgetId
                @Suppress("DEPRECATION")
                startActivityForResult(result.intent, REQUEST_BIND_WIDGET)
            }
            is VietMapWidgetBindResult.Failed -> {
                // No consent UI on the head unit → self-grant the bind permission over the adb
                // loopback (this app is already an adb-mode tool) and retry ONCE, instead of asking
                // the driver to type `appwidget grantbind` by hand. The manual message stays as the
                // last-resort fallback (grant only helps the missing-permission case).
                if (allowGrantRetry && result.reason == VietMapWidgetUnavailableReason.BIND_UI_UNAVAILABLE) {
                    toast(Lang.t("Đang tự cấp quyền bind…", "Granting bind permission…"))
                    Thread({
                        val granted = LocalDeviceShell.grantAppWidgetBind(
                            AdbKeys.ensure(applicationContext), packageName,
                        )
                        runOnUiThread {
                            if (granted) {
                                bindNextMissing(allowGrantRetry = false)
                            } else {
                                toast(result.detail)
                                render(bridge.snapshot())
                            }
                        }
                    }, "widget-grantbind").start()
                } else {
                    toast(result.detail)
                    render(bridge.snapshot())
                }
            }
        }
    }

    private fun render(snapshot: VietMapWidgetSnapshot) {
        bindingText.text = bridge.bindingStatuses().joinToString("\n") { status ->
            val state = when {
                !status.providerAvailable -> "provider missing"
                status.bound -> "bound · id=${status.appWidgetId}"
                else -> "needs bind"
            }
            "${status.slot.displayName}: $state"
        }
        val age = snapshot.updatedAtElapsedMs?.let {
            "${(SystemClock.elapsedRealtime() - it).coerceAtLeast(0L)} ms"
        } ?: "—"
        valuesText.text = buildString {
            appendLine("State       : ${snapshot.freshness}")
            appendLine("Reason      : ${snapshot.reason ?: "—"}")
            appendLine("VietMap     : ${snapshot.providerVersion ?: "—"}")
            appendLine("Update age  : $age")
            appendLine("Speed       : ${snapshot.currentSpeedKph?.let { "$it km/h" } ?: "—"}")
            appendLine("Speed limit : ${snapshot.speedLimitKph?.let { "$it km/h" } ?: "—"}")
            appendLine(
                "Upcoming    : ${snapshot.upcomingLimitKph?.let { "$it km/h" } ?: "—"} " +
                    "@ ${snapshot.upcomingDistanceText ?: "—"} (${snapshot.alertFullFreshness})",
            )
            if (snapshot.secondUpcomingLimitKph != null || snapshot.secondUpcomingDistanceText != null) {
                appendLine(
                    "Upcoming 2  : ${snapshot.secondUpcomingLimitKph?.let { "$it km/h" } ?: "—"} " +
                        "@ ${snapshot.secondUpcomingDistanceText ?: "—"}",
                )
            }
            if (snapshot.alerts.isEmpty()) {
                append("Alerts      : —")
            } else {
                snapshot.alerts.forEachIndexed { index, alert ->
                    val image = alert.imageHash?.take(12)?.let { " image=$it" }.orEmpty()
                    appendLine(
                        "Alert ${index + 1}     : limit=${alert.speedLimitKph ?: "—"} " +
                            "distance=${alert.distanceText ?: "—"}$image",
                    )
                }
            }
        }
    }

    private fun text(value: String, sizeSp: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(getColor(if (bold) R.color.text_primary else R.color.text_secondary))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun <T : android.view.View> T.withTop(top: Int): T = apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = top }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val REQUEST_BIND_WIDGET = 0x564D
        private const val STATE_PENDING_SLOT = "pending_widget_slot"
        private const val STATE_PENDING_ID = "pending_widget_id"
    }
}
