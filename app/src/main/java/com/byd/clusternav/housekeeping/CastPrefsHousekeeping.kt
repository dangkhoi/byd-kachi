package com.byd.clusternav.housekeeping

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.byd.clusternav.launcher.ProfileScopeCluster
import com.byd.clusternav.modules.clustercast.simplified.CastPrefsPrune
import com.byd.clusternav.system.PackageQueries

/**
 * 2.98 · R6-E — gỡ khoá hình học chiếu (`config_*_<gói>`, `scale-*:<gói>`, `dpi:<gói>`) của app đã gỡ khỏi xe ≥ 30 ngày.
 * Luật + fail-safe ở `:core` [CastPrefsPrune]; ở đây chỉ đọc sự thật và thi hành.
 *
 * "Còn cài?" hỏi bằng `getPackageInfo(…, MATCH_UNINSTALLED_PACKAGES)` (manifest có `QUERY_ALL_PACKAGES` ⇒ không bị lọc
 * hiển thị trên DL5/Android 12): gói gỡ kiểu `pm uninstall -k` (giữ dữ liệu) hay bị `pm hide` vẫn tính là CÒN. App bị
 * tắt (disabled) vẫn trả `PackageInfo` ⇒ CÒN. Chỉ `NameNotFoundException` mới là "không cài"; ngoại lệ khác ⇒ `null`
 * ⇒ cả lượt không gỡ gì.
 *
 * Sổ "vắng từ lúc" ở tệp prefs RIÊNG [LEDGER_FILE] — trạng thái dọn của chính máy này, không thuộc hồ sơ (không nằm
 * trong tệp nào mà ảnh chụp hồ sơ quét: `simple_cast_prefs` / `clusternav_prefs` / `cast-v2-app-catalog`).
 */
internal object CastPrefsHousekeeping {
    private const val TAG = "KachiHousekeeping"
    private const val LEDGER_FILE = "kachi_housekeeping"
    private const val LEDGER_KEY = "cast_prefs_missing_since"

    /** Hai tệp mang khoá theo app — tên lấy từ MỘT nguồn ([ProfileScopeCluster]), không chép tay. */
    private val FILES = listOf(ProfileScopeCluster.SIMPLE_CAST_FILE, ProfileScopeCluster.CAST_CATALOG_FILE)

    fun prune(app: Context) {
        val pm = app.packageManager
        val answers = HashMap<String, Boolean?>()
        val installed: (String) -> Boolean? = { p ->
            answers.getOrPut(p) {
                try {
                    PackageQueries.packageInfo(pm, p, PackageManager.MATCH_UNINSTALLED_PACKAGES) != null
                } catch (e: RuntimeException) {
                    Log.w(TAG, "cast-prefs: hỏi gói $p hỏng (${e.javaClass.simpleName}) ⇒ lượt này không gỡ gì")
                    null
                }
            }
        }
        val prefs = FILES.map { app.getSharedPreferences(it, Context.MODE_PRIVATE) }
        val keysByFile = prefs.map { it.all.keys.toSet() }
        val ledgerSp = app.getSharedPreferences(LEDGER_FILE, Context.MODE_PRIVATE)
        val raw = ledgerSp.getString(LEDGER_KEY, null)
        val ledger = CastPrefsPrune.decode(raw)
        val plan = CastPrefsPrune.plan(keysByFile.flatten(), installed, ledger, System.currentTimeMillis())
        if (plan.aborted) return
        if (plan.removeKeys.isNotEmpty()) {
            val remove = plan.removeKeys.toSet()
            prefs.forEachIndexed { i, sp ->
                val mine = keysByFile[i].filter { it in remove }
                if (mine.isEmpty()) return@forEachIndexed
                val ed = sp.edit()
                mine.forEach { ed.remove(it) }
                ed.commit()
            }
            Log.i(TAG, "cast-prefs: gỡ ${plan.removeKeys.size} khoá của app đã gỡ ≥ 30 ngày")
        }
        val encoded = CastPrefsPrune.encode(plan.missingSince)
        if (encoded != (raw ?: "")) {
            val ed = ledgerSp.edit()
            if (encoded.isEmpty()) ed.remove(LEDGER_KEY) else ed.putString(LEDGER_KEY, encoded)
            ed.commit()
        }
    }
}
