package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.byd.clusternav.Lang
import com.byd.clusternav.Prefs
import com.byd.clusternav.badgeSizeDp
import com.byd.clusternav.setBadgeSizeDp
import com.byd.clusternav.badgeCenterX
import com.byd.clusternav.badgeCenterY
import com.byd.clusternav.setBadgeCenterX
import com.byd.clusternav.setBadgeCenterY
import com.byd.clusternav.ThemeMode
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.speedbadge.BadgeLayout

/**
 * Read-only Cast diagnostics screen.
 *
 * V2 (CastAndroidRuntime/CastFacade) removed — reads directly from SimpleCastRuntime state.
 * Shows current state, coordinator info, and last mutation outcome if available.
 */
class DiagActivity : Activity() {
    private lateinit var report: TextView
    private lateinit var status: TextView
    private lateinit var badgeInfo: TextView
    /** 2.89 · B2 — điều kiện nền app (miễn pin · vẽ nổi): SỰ THẬT đọc từ máy, không phải cờ. */
    private lateinit var prereqInfo: TextView

    override fun attachBaseContext(newBase: Context) {
        // Locale của NGƯỜI DÙNG (không phải của máy) cho tài nguyên — spec kachi-i18n-zh-th-ms R9.
        super.attachBaseContext(com.byd.clusternav.launcher.LangHost.localized(ThemeMode.wrap(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = { value: Int -> (value * resources.displayMetrics.density + .5f).toInt() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(0xFFF4F6F8.toInt())
        }
        root.addView(TextView(this).apply {
            text = "Cluster Cast · Chẩn đoán"
            textSize = 22f
            setTextColor(Color.rgb(26, 31, 36))
        })
        root.addView(Button(this).apply {
            text = "Làm mới"
            isAllCaps = false
            minimumHeight = dp(48)
            setOnClickListener { refresh() }
        })
        root.addView(Button(this).apply {
            text = "Sao chép báo cáo"
            isAllCaps = false
            minimumHeight = dp(48)
            setOnClickListener { copyReport() }
        })
        // CLUSTER-THEME-SAFE B1a (CLAUDE.md §11): chạy các lượt ĐỌC của cổng theme + quyết định NGAY BÂY GIỜ, in kết quả — 0 lệnh
        // ghi (kênh chỉ đọc ở `ThemeGatePreview`). Shell dadb ⇒ luồng nền, không luồng chính.
        root.addView(Button(this).apply {
            text = Lang.t("Cổng theme (chỉ đọc)", "Theme gate (read-only)")
            isAllCaps = false
            minimumHeight = dp(48)
            setOnClickListener { runThemeGate() }
        })
        root.addView(Button(this).apply {
            text = Lang.t("⬇ Kiểm tra cập nhật", "⬇ Check update")
            isAllCaps = false
            minimumHeight = dp(48)
            setOnClickListener { com.byd.clusternav.UpdateFlow.start(this@DiagActivity) { t, warn -> setStatus(t, warn) } }
        })
        // T5 (telemetry): force-render the speed-limit badge on the cluster (display 1) with a fixed 50 so the
        // driver can confirm ON-CAR whether the overlay draws over cast GMaps at all — without needing any
        // VietMap/Waze speed data. Uses NavigationSpeedSignOwner's independent debug overlay (the real
        // coordinator pipeline is untouched). A second button clears it.
        root.addView(Button(this).apply {
            text = Lang.t("Thử badge 50", "TEST BADGE 50")
            isAllCaps = false
            minimumHeight = dp(48)
            setOnClickListener {
                com.byd.clusternav.NavigationSpeedSignOwner.get(applicationContext).debugForceBadge(50)
                setStatus(Lang.t("Đã buộc badge 50 trên cụm (display 1)", "Forced badge 50 on cluster (display 1)"))
            }
        })
        root.addView(Button(this).apply {
            text = Lang.t("Ẩn badge", "HIDE badge")
            isAllCaps = false
            minimumHeight = dp(48)
            setOnClickListener {
                com.byd.clusternav.NavigationSpeedSignOwner.get(applicationContext).debugHideBadge()
                setStatus(Lang.t("Đã ẩn badge", "Badge hidden"))
            }
        })
        // Badge POSITION + SIZE controls: with the badge force-shown above, these persist to Prefs and call
        // debugRefreshBadgeLayout() so the driver watches it move/resize LIVE on the cluster and can park it
        // where the cast app doesn't cover it. The REAL speed-limit badge reads the same Prefs on its next
        // show(), so what you tune here is what the live badge uses.
        root.addView(TextView(this).apply {
            text = Lang.t("Chỉnh vị trí / cỡ badge", "Badge position / size")
            textSize = 15f
            setTextColor(Color.rgb(26, 31, 36))
            setPadding(0, dp(14), 0, dp(2))
        })
        root.addView(buttonRow(
            controlButton(Lang.t("↖ Trên-trái", "↖ Top-left")) { setBadgeCornerPreset(left = true, top = true) },
            controlButton(Lang.t("↗ Trên-phải", "↗ Top-right")) { setBadgeCornerPreset(left = false, top = true) },
        ))
        root.addView(buttonRow(
            controlButton(Lang.t("↙ Dưới-trái", "↙ Bottom-left")) { setBadgeCornerPreset(left = true, top = false) },
            controlButton(Lang.t("↘ Dưới-phải", "↘ Bottom-right")) { setBadgeCornerPreset(left = false, top = false) },
        ))
        root.addView(buttonRow(
            controlButton(Lang.t("Cỡ −10", "Size −10")) { nudgeBadgeSize(-BADGE_SIZE_STEP_DP) },
            controlButton(Lang.t("Cỡ +10", "Size +10")) { nudgeBadgeSize(BADGE_SIZE_STEP_DP) },
        ))
        root.addView(buttonRow(
            controlButton("X −$BADGE_MOVE_STEP_PX") { nudgeBadgeCenter(-BADGE_MOVE_STEP_PX, 0) },
            controlButton("X +$BADGE_MOVE_STEP_PX") { nudgeBadgeCenter(BADGE_MOVE_STEP_PX, 0) },
        ))
        root.addView(buttonRow(
            controlButton("Y −$BADGE_MOVE_STEP_PX") { nudgeBadgeCenter(0, -BADGE_MOVE_STEP_PX) },
            controlButton("Y +$BADGE_MOVE_STEP_PX") { nudgeBadgeCenter(0, BADGE_MOVE_STEP_PX) },
        ))
        badgeInfo = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(0, 90, 120))
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(badgeInfo)
        refreshBadgeInfo()
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(0, 105, 92))
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(status)
        report = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
        }
        prereqInfo = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(26, 31, 36))
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(prereqInfo)
        root.addView(report)
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    override fun onDestroy() { super.onDestroy() }

    private fun refresh() {
        val coordinator = SimpleCastRuntime.coordinator(applicationContext)
        val state = coordinator.state
        val value = buildString {
            appendLine("── Simplified Cast Diagnostics ──")
            appendLine()
            appendLine("state=$state")
            appendLine("architecture=SimplifiedV1 (single coordinator, no V2)")
            appendLine()

            // Display coordinator details
            when (state) {
                is SimpleCastState.CastingFull -> {
                    appendLine("target=${state.targetPkg}")
                    appendLine("appType=${state.appType}")
                    appendLine("displayConfig.wmSize=${state.displayConfig.wmSize}")
                    appendLine("displayConfig.overscan=${state.displayConfig.overscan}")
                    appendLine("displayConfig.density=${state.displayConfig.density}")
                    state.taskId?.let { appendLine("taskId=$it") }
                }
                is SimpleCastState.CastingSplit -> {
                    state.left?.let {
                        appendLine("left.pkg=${it.pkg}")
                        appendLine("left.wmSize=${it.displayConfig.wmSize}")
                    }
                    state.right?.let {
                        appendLine("right.pkg=${it.pkg}")
                        appendLine("right.wmSize=${it.displayConfig.wmSize}")
                    }
                }
                is SimpleCastState.Error -> {
                    appendLine("error=${state.message}")
                }
                else -> { /* no extra details for Off/Opening/Idle/Stopping/Closing */ }
            }

            appendLine()
            appendLine("── prefs ──")
            val prefs = coordinator.prefs
            appendLine("autoStart=${prefs.autoStartEnabled()}")
            appendLine("autoStartPkg=${prefs.autoStartPackage() ?: "(none)"}")
            appendLine("autoStartSplit=${prefs.autoStartSplitEnabled()}")
            appendLine("splitRatioLeft=${prefs.splitRatioLeftPercent()}%")
            // 2.89 · B2: dòng `dozeWhitelist=` (in CỜ một-lần — lý do màn này không bắt được lỗi hộp "IVI không hỗ trợ") đã bỏ;
            // sự thật miễn pin / appop đọc từ máy nằm ở khối "điều kiện nền app" phía trên ([refreshPrereqs]).
            // V-CLUSTER (OC-5): giá trị HIỆU LỰC + bản chờ của hồ sơ (chốt lần nổ máy kế). Bản ghim hình học + tỉ lệ của
            // phiên nằm sẵn trong dòng `state=` ở trên (`pinned=…`, `leftPercent=…`) — chụp màn này là đủ, không gõ adb.
            appendLine("castEnabled=${prefs.castEnabled()} pending=${prefs.castEnabledPending() ?: "-"}")

            // R7 (spec kachi-a11y-bind-stuck-autofix) — NHẬT KÝ BỀN của trạng thái gắn dịch vụ Hỗ trợ (phím
            // vô-lăng). Đây là ĐƯỜNG ĐỌC của nhật ký: [ĐO xe 2026-09-28] vòng đệm logcat chỉ còn 32 phút nên
            // sáng hôm sau khoảnh khắc đứt đã trôi mất. Hiện ở màn Chẩn đoán để anh em chỉ cần CHỤP MÀN HÌNH
            // gửi về, không phải gõ adb (CLAUDE.md §11). Tệp chỉ chứa mốc giờ + hai đồng hồ + pid + trạng thái.
            // READY-AT-HOME §4.10 (CLAUDE.md §11) — kênh điều khiển cửa sổ: trạng thái đo được trong tiến trình, dấu
            // duyệt theo xe, và dòng summary "màn bật → ô có app" gần nhất. Chụp màn này là đủ, không gõ adb.
            appendLine()
            appendLine("── kênh điều khiển cửa sổ · sẵn sàng ──")
            appendLine(com.byd.clusternav.EarlyShellChannel.describe())
            appendLine("dấu duyệt: ${com.byd.clusternav.ShellReadiness.ledger().javaClass.simpleName} · chặn nền: ${com.byd.clusternav.KachiReadyLog.denyCount()}")
            appendLine(com.byd.clusternav.KachiReadyLog.summaryForDiag())
            appendLine(com.byd.clusternav.launcher.trip.TripStart.describe(applicationContext))   // F2/F3 — chuyến lên xe gần nhất

            appendLine()
            appendLine("── phím vô-lăng · nhật ký gắn dịch vụ Hỗ trợ ──")
            val escalatedAt = Prefs.a11yEscalatedAt(applicationContext)
            appendLine("đã tự khởi động lại để chữa (mốc elapsed): ${if (escalatedAt < 0) "chưa lần nào" else "${escalatedAt / 1000}s"}")
            val journal = com.byd.clusternav.modules.navaccess.A11yBindJournalStore.read(applicationContext)
            if (journal.isEmpty()) {
                appendLine("(chưa có bản ghi — nhật ký ghi khi trạng thái ĐỔI, mỗi giờ một nhịp tim)")
            } else {
                journal.takeLast(JOURNAL_TAIL_LINES).forEach { appendLine(it) }
            }
        }
        report.text = value
        refreshPrereqs()
    }

    /**
     * 2.89 · B2 VM-PREREQ-TRUTH (CLAUDE.md §5 · §11): đọc NGAY `cmd deviceidle whitelist` + `appops get … SYSTEM_ALERT_WINDOW`
     * (0 lệnh ghi) cho VietMap + app trong phạm vi, kèm lượt chữa gần nhất và lượt chờ bóng gần nhất. Shell ⇒ luồng nền.
     */
    private fun refreshPrereqs() {
        prereqInfo.text = "── điều kiện nền app ── đang đọc…"   // cùng kiểu các tiêu đề khối kỹ thuật khác của màn này
        Thread({
            val text = buildString {
                appendLine("── điều kiện nền app (sự thật, không cờ) ──")
                appendLine(com.byd.clusternav.AppPrereqs.diagnose(applicationContext))
                append("chờ bóng VietMap gần nhất: ").append(com.byd.clusternav.VietMapAutostart.lastBubbleWait ?: "chưa có trong tiến trình này")
            }
            runOnUiThread { if (!isFinishing && !isDestroyed) prereqInfo.text = text }
        }, "KachiPrereqDiag").start()
    }

    /** B1a — "Cổng theme (chỉ đọc)": nền → `ClusterDiag.themeGate` (0 lệnh ghi) → đầu báo cáo, chụp màn hình gửi về. */
    private fun runThemeGate() {
        setStatus(Lang.t("Đang đọc cổng theme…", "Reading theme gate…"))
        Thread({
            val text = ClusterDiag.themeGate(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                report.text = text + "\n\n" + report.text
                setStatus(Lang.t("Cổng theme: đã đọc (không gửi lệnh nào)", "Theme gate: read (no command sent)"))
            }
        }, "KachiThemeGateDiag").start()
    }

    private fun copyReport() {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Cluster Cast diagnostics", "${prereqInfo.text}\n\n${report.text}"))
        android.widget.Toast.makeText(this, "Đã sao chép", android.widget.Toast.LENGTH_SHORT).show()
    }

    // ─── Badge position/size controls ───────────────────────────────────────
    // Each control persists the absolute centre / size to Prefs then re-applies the SHARED badge overlay LIVE
    // via debugRefreshBadgeLayout(). Since BUG-1 unified the overlays, the debug force-show and the real
    // ClusterSpeedBadgePort are one window reading the same Prefs — so tuning here is exactly what the live
    // speed-limit badge uses. (The full drag-to-place UI is BadgePlacementView, added in the UI stage.)
    private fun setBadgeCornerPreset(left: Boolean, top: Boolean) {
        val sizePx = Prefs.badgeSizeDp(applicationContext)   // dp≈px approximation; overlay re-clamps exactly
        val half = sizePx / 2
        val cx = if (left) BADGE_EDGE_MARGIN_PX + half else BADGE_CLUSTER_W - BADGE_EDGE_MARGIN_PX - half
        val cy = if (top) BADGE_EDGE_MARGIN_PX + half else BADGE_CLUSTER_H - BADGE_EDGE_MARGIN_PX - half
        saveBadgeCenter(cx, cy, sizePx)
    }

    private fun nudgeBadgeSize(deltaDp: Int) {
        val ctx = applicationContext
        Prefs.setBadgeSizeDp(ctx, Prefs.badgeSizeDp(ctx) + deltaDp)   // Prefs clamps 60..240 via BadgeLayout
        applyBadge()
    }

    private fun nudgeBadgeCenter(dxPx: Int, dyPx: Int) {
        val ctx = applicationContext
        saveBadgeCenter(Prefs.badgeCenterX(ctx) + dxPx, Prefs.badgeCenterY(ctx) + dyPx, Prefs.badgeSizeDp(ctx))
    }

    /** Clamp on the default cluster (BadgeLayout), persist the badge centre, then push it to the live overlay. */
    private fun saveBadgeCenter(cx: Int, cy: Int, sizePx: Int) {
        val ctx = applicationContext
        val (ccx, ccy) = BadgeLayout.clampCenter(cx, cy, sizePx, BADGE_CLUSTER_W, BADGE_CLUSTER_H)
        Prefs.setBadgeCenterX(ctx, ccx)
        Prefs.setBadgeCenterY(ctx, ccy)
        applyBadge()
    }

    /** Push the freshly-saved Prefs to the shared badge and refresh the on-screen readout. */
    private fun applyBadge() {
        com.byd.clusternav.NavigationSpeedSignOwner.get(applicationContext).debugRefreshBadgeLayout()
        refreshBadgeInfo()
    }

    private fun refreshBadgeInfo() {
        if (!::badgeInfo.isInitialized) return
        val ctx = applicationContext
        badgeInfo.text = Lang.f(
            "Tâm badge: X={0} Y={1} (px cụm) · cỡ {2}dp",
            "Badge centre: X={0} Y={1} (cluster px) · size {2}dp",
            Prefs.badgeCenterX(ctx), Prefs.badgeCenterY(ctx), Prefs.badgeSizeDp(ctx),
        )
    }

    private fun buttonRow(vararg buttons: Button): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.forEach { row.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        return row
    }

    private fun controlButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        minimumHeight = (44 * resources.displayMetrics.density + .5f).toInt()
        setOnClickListener { onClick() }
    }

    // ─── Check-for-update status sink ────────────────────────────────────────
    // The interactive check → confirm → download → install flow lives in the shared [UpdateFlow]
    // helper (invoked by the button above; the old ClusterNav screen, its second caller, was removed
    // 2026-09-13). This screen only renders status.
    private fun setStatus(text: String, warn: Boolean = false) {
        status.text = text
        status.setTextColor(if (warn) Color.rgb(176, 0, 32) else Color.rgb(0, 105, 92))
    }

    private companion object {
        /** Bao nhiêu dòng nhật ký gắn a11y hiện trên màn (đủ một ảnh chụp; tệp giữ tới 200 dòng). */
        const val JOURNAL_TAIL_LINES = 20
        const val BADGE_SIZE_STEP_DP = 10
        const val BADGE_MOVE_STEP_PX = 20         // centre nudge step in cluster px
        const val BADGE_EDGE_MARGIN_PX = 24       // gap from the cluster edge for the corner presets
        // Default cluster dims for this diagnostic tuner's presets/clamp only. The overlay uses the REAL
        // display-1 size + density on show(), so these fallbacks don't affect the live badge — the full
        // drag-to-place UI is BadgePlacementView (UI stage).
        const val BADGE_CLUSTER_W = 1920
        const val BADGE_CLUSTER_H = 720
    }
}
