package com.byd.clusternav

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.core.UpdateDot
import com.byd.clusternav.system.PackageQueries
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 2.98 · OTA-UPDATE-DOT — nơi DUY NHẤT ghi/đọc bản ghi *"kênh có bản mới"* (luật ở `:core` [UpdateDot]).
 *
 * Ghi: [UpdateFlow.start] gọi [record] sau MỖI lượt dò sẵn có (tự động một lần mỗi tiến trình + bấm tay) — không thêm lượt
 * dò mạng nào. Đọc: [shows] (view chấm — `launcher/UpdateDotBadge`) mỗi lần view gắn vào cửa sổ + mỗi khi bản ghi đổi.
 *
 * Tệp prefs riêng [PREF] (không phải cấu hình người dùng, không theo hồ sơ — là SỰ THẬT của kênh so với bản cài trên xe
 * này). Chỉ hai khoá, cỡ cố định — không phình theo thời gian.
 */
object UpdateDotStore {
    private const val TAG = "UpdateDot"
    internal const val PREF = "kachi_update_dot"
    private const val K_VERSION = "avail_version"
    private const val K_SEEN_VC = "avail_seen_vc"

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    /** View chấm đăng ký khi gắn vào cửa sổ, gỡ khi tháo ([listen]/[unlisten]) — không giữ view nào sau khi nó rời màn. */
    fun listen(l: () -> Unit) { listeners += l }
    fun unlisten(l: () -> Unit) { listeners -= l }

    /** Ghi kết quả một lượt dò ([UpdateChecker.Result]) rồi báo các chấm đang hiện (luồng chính). Không bao giờ ném. */
    fun record(ctx: Context, r: UpdateChecker.Result) {
        val app = ctx.applicationContext
        runCatching {
            val action = UpdateDot.afterCheck(r.latest, r.error != null, r.current, installedVersionCode(app))
            val p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            when (action) {
                is UpdateDot.Action.Keep -> return
                is UpdateDot.Action.Clear -> {
                    if (!p.contains(K_VERSION)) return
                    p.edit().remove(K_VERSION).remove(K_SEEN_VC).apply()
                }
                is UpdateDot.Action.Set -> p.edit()
                    .putString(K_VERSION, action.record.version)
                    .putLong(K_SEEN_VC, action.record.seenAtVersionCode)
                    .apply()
            }
            Log.i(TAG, "kênh ${r.latest} · đang cài ${r.current} ⇒ ${action.javaClass.simpleName}")
        }.onFailure { Log.w(TAG, "ghi bản ghi lỗi (${it.javaClass.simpleName}: ${it.message})"); return }
        main.post { listeners.forEach { it() } }
    }

    /**
     * Có vẽ chấm không. Đọc prefs + PackageManager (rẻ, không mạng). Bản ghi đã hết hiệu lực (đã cài bản đó / mới hơn) ⇒
     * xoá luôn. Lỗi đọc ⇒ `false` (không chấm), không ném — view này nằm trên màn chính.
     */
    fun shows(ctx: Context): Boolean {
        return runCatching { readShows(ctx) }
            .getOrElse { Log.w(TAG, "đọc bản ghi lỗi (${it.javaClass.simpleName}: ${it.message})"); false }
    }

    private fun readShows(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val ver = p.getString(K_VERSION, null) ?: return false
        val rec = UpdateDot.Record(ver, p.getLong(K_SEEN_VC, 0L))
        val name = UpdateChecker.currentVersion(app)
        val vc = installedVersionCode(app)
        if (!UpdateDot.stillValid(rec, name, vc)) {
            p.edit().remove(K_VERSION).remove(K_SEEN_VC).apply()
            return false
        }
        return UpdateDot.shows(rec, name, vc)
    }

    private fun installedVersionCode(app: Context): Long? =
        runCatching { PackageQueries.packageInfo(app.packageManager, app.packageName) }.getOrNull()?.longVersionCode
}
