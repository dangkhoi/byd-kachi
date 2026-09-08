package com.byd.clusternav

import android.app.Activity
import android.app.AlertDialog

/**
 * Interactive check-for-update flow: GitHub `apk/` → confirm → download → install via dadb loopback.
 *
 * Extracted from DiagActivity so the main screen can trigger it too, without duplicating the
 * threading/dialog logic. All heavy I/O runs off the main thread; [setStatus] is always invoked on
 * the UI thread with (message, isError). Pure orchestration — the real work lives in [UpdateChecker]
 * (unit-tested). Version-compare, download and dadb install are unchanged from the previous flow.
 */
object UpdateFlow {

    fun start(activity: Activity, setStatus: (text: String, warn: Boolean) -> Unit) {
        setStatus(Lang.t("đang kiểm tra…", "checking…"), false)
        Thread({
            val r = UpdateChecker.check(activity.applicationContext)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                when {
                    r.error != null -> setStatus(Lang.t("lỗi: ${r.error}", "error: ${r.error}"), true)
                    !r.hasUpdate -> setStatus(Lang.t("đang ở bản mới nhất (v${r.current})", "up to date (v${r.current})"), false)
                    else -> confirm(activity, r.current, r.latest!!, r.downloadUrl!!, setStatus)
                }
            }
        }, "update-check").start()
    }

    private fun confirm(
        activity: Activity,
        cur: String,
        latest: String,
        url: String,
        setStatus: (String, Boolean) -> Unit,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(Lang.t("Có bản mới: v$latest", "New version: v$latest"))
            .setMessage(Lang.t(
                "Đang dùng v$cur. Tải v$latest và cài đè? App sẽ tự khởi động lại.",
                "You have v$cur. Download v$latest and install? The app will restart.",
            ))
            .setPositiveButton(Lang.t("Tải & cài", "Download & install")) { _, _ -> doUpdate(activity, url, setStatus) }
            .setNegativeButton(Lang.t("Để sau", "Later"), null)
            .show()
    }

    private fun doUpdate(activity: Activity, url: String, setStatus: (String, Boolean) -> Unit) {
        setStatus(Lang.t("đang tải… 0%", "downloading… 0%"), false)
        Thread({
            val f = UpdateChecker.download(activity.applicationContext, url) { pct ->
                activity.runOnUiThread {
                    setStatus(
                        if (pct < 0) Lang.t("đang tải…", "downloading…") else Lang.t("đang tải… $pct%", "downloading… $pct%"),
                        false,
                    )
                }
            }
            if (f == null) {
                activity.runOnUiThread { setStatus(Lang.t("tải thất bại", "download failed"), true) }
                return@Thread
            }
            activity.runOnUiThread { setStatus(Lang.t("đang cài…", "installing…"), false) }
            val msg = UpdateChecker.install(activity.applicationContext, f)
            activity.runOnUiThread { setStatus(msg, false) }
        }, "update-download").start()
    }
}
