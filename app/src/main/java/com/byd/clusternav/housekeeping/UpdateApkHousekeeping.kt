package com.byd.clusternav.housekeeping

import android.content.Context
import android.util.Log
import com.byd.clusternav.UpdateChecker
import com.byd.clusternav.core.UpdateApkSweep
import com.byd.clusternav.system.PackageQueries
import java.io.File

/**
 * 2.98 · R6-H — xoá APK OTA (~45 MB) đã cài xong khỏi `filesDir/update/`. Luật quyết ở `:core` [UpdateApkSweep]; ở đây
 * chỉ đọc sự thật (versionCode đang cài, gói + versionCode trong từng tệp, tuổi tệp) rồi xoá theo phán quyết.
 *
 * Không bao giờ xoá trước khi biết đã cài: versionCode đang cài đọc không ra ⇒ [UpdateApkSweep] giữ mọi tệp; đọc
 * PackageManager ném ⇒ ngoại lệ đi lên [StartupHousekeeping] (bỏ cả bước, không xoá gì).
 */
internal object UpdateApkHousekeeping {
    private const val TAG = "KachiHousekeeping"

    fun sweep(app: Context) {
        val dir = File(app.filesDir, UpdateChecker.UPDATE_DIR)
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.isEmpty()) return
        val pm = app.packageManager
        val installed = PackageQueries.packageInfo(pm, app.packageName)?.longVersionCode
        val now = System.currentTimeMillis()
        var freed = 0L
        files.forEach { f ->
            val info = PackageQueries.archiveInfo(pm, f.absolutePath)
            val v = UpdateApkSweep.verdict(
                ageMs = now - f.lastModified(),
                archivePackage = info?.packageName,
                archiveVersionCode = info?.longVersionCode ?: 0L,
                ownPackage = app.packageName,
                installedVersionCode = installed,
            )
            if (v == UpdateApkSweep.Verdict.DELETE) {
                val len = f.length()
                if (f.delete()) {
                    freed += len
                    Log.i(TAG, "OTA: xoá ${f.name} (tệp ${info?.longVersionCode ?: "hỏng/tải dở"}, đang cài $installed)")
                }
            }
        }
        if (freed > 0) Log.i(TAG, "OTA: giải phóng ${freed / 1024} KiB trong filesDir/${UpdateChecker.UPDATE_DIR}")
    }
}
