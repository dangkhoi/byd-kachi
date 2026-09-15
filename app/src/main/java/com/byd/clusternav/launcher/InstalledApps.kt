package com.byd.clusternav.launcher

import android.content.Context
import android.content.Intent
import com.byd.clusternav.system.PackageQueries

/**
 * Liệt kê app CÓ LAUNCHER (nhãn + gói), sắp theo nhãn. Tiện ích PackageManager THUẦN — không dính cast.
 *
 * Tách ra khỏi `ClusterCast.listInstalledApps` (quality-review 2026-09-15, Pha 3: gộp cast về SimpleCast + xoá
 * `ClusterCast`/`CastShell` chết). Đây là nhánh SỐNG duy nhất từng gọi vào `ClusterCast` — chuyển ra đây để xoá
 * được 1286+ dòng orchestrator cast chết mà không mất chức năng.
 */
object InstalledApps {

    /** Một app có màn LAUNCHER: nhãn hiển thị + gói. */
    data class Entry(val name: String, val pkg: String)

    /** App có `CATEGORY_LAUNCHER`, khử trùng theo gói, sắp theo nhãn (không phân biệt hoa/thường). */
    fun launchable(ctx: Context): List<Entry> = runCatching {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        PackageQueries.queryActivities(pm, intent)
            .map { Entry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())
}
