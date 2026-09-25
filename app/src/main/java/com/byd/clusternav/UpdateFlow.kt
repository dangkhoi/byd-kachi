package com.byd.clusternav

import android.app.Activity
import android.app.AlertDialog
import java.lang.ref.WeakReference

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
                    else -> {
                        // ⚠ `hasUpdate == true` KHÔNG kéo theo `downloadUrl != null`: [UpdateChecker.check] đặt
                        // `bestUrl = o.optString("download_url").takeIf { it.isNotBlank() }` (⇒ có thể null) trong
                        // khi `hasUpdate` chỉ so PHIÊN BẢN. Một mục `apk/` mà GitHub trả `download_url` rỗng (submodule,
                        // LFS pointer, file > 100 MB) làm `r.downloadUrl!!` ném NPE NGAY trên main thread — tức sập màn
                        // đang mở trên xe đang chạy. Nói ra sự thật "kênh thiếu link tải" thay vì đoán một URL.
                        val latest = r.latest
                        val url = r.downloadUrl
                        if (latest == null || url.isNullOrBlank()) {
                            setStatus(
                                Lang.t(
                                    "có bản mới nhưng kênh không có link tải — thử lại sau",
                                    "a new version exists but the channel has no download link — try again later",
                                ),
                                true,
                            )
                        } else {
                            confirm(activity, r.current, latest, url, setStatus)
                        }
                    }
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
        // Hardening 2026-09-25 (audit F13): luồng tải sống hàng phút (40 MB) — giữ Activity qua WeakReference và
        // bỏ qua `setStatus` khi màn đã huỷ (cùng khuôn `start()` ở trên), không vẽ lên view đã tháo.
        val app = activity.applicationContext
        val ref = WeakReference(activity)
        fun ui(text: String, warn: Boolean) {
            val a = ref.get() ?: return
            a.runOnUiThread { if (!a.isFinishing && !a.isDestroyed) setStatus(text, warn) }
        }
        Thread({
            val f = UpdateChecker.download(app, url) { pct ->
                ui(if (pct < 0) Lang.t("đang tải…", "downloading…") else Lang.t("đang tải… $pct%", "downloading… $pct%"), false)
            }
            if (f == null) {
                ui(Lang.t("tải thất bại", "download failed"), true)
                return@Thread
            }
            ui(Lang.t("đang cài…", "installing…"), false)
            val msg = UpdateChecker.install(app, f)
            ui(msg, false)
        }, "update-download").start()
    }
}
