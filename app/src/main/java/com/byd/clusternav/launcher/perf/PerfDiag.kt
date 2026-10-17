package com.byd.clusternav.launcher.perf

import android.app.ActivityManager
import android.content.Context
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.KachiPerf
import com.byd.clusternav.modules.clustercast.ClusterProfile
import com.byd.clusternav.system.PackageQueries

/**
 * 3.1.14 `PERF-SL6-3113` — khối "hiệu năng" của màn Chẩn đoán (CLAUDE.md §11: anh em chỉ CHỤP MÀN HÌNH gửi về, không gõ adb).
 * Gom đúng những thứ cần để phân biệt các nguồn giật đã xếp hạng ở `docs/diagnostics/perf-sl6-3113-2026-10-17.md`:
 * đời xe (`car.type` — quyết định có mô hình 3D không), cấu hình máy (lõi · RAM), gói AVM, công tắc Hey Kachi / phím giọng,
 * và dòng `KachiPerf` gần nhất (gồm `3D-dựng=N (luồng-chính M ms)`). Chỉ đọc — 0 lệnh shell, 0 lệnh ghi. Nhãn là khoá kỹ thuật ASCII
 * (cùng kiểu `state=`/`target=` của báo cáo Chẩn đoán) — không phải chữ giao diện cần dịch.
 */
object PerfDiag {
    fun describe(ctx: Context): String = buildString {
        val pm = ctx.packageManager
        val me = PackageQueries.packageInfo(pm, ctx.packageName)
        appendLine("Kachi ${me?.versionName ?: "?"} (${me?.longVersionCode ?: "?"}) · car.type=${runCatching { ClusterProfile.carType(ctx) }.getOrNull() ?: "—"}")
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        appendLine("cores=${Runtime.getRuntime().availableProcessors()} · ramMB=${mi.totalMem shr MB} · availMB=${mi.availMem shr MB}" +
            " · lowMemory=${mi.lowMemory} · lowRamDevice=${am?.isLowRamDevice}")
        appendLine("avm com.byd.avc=${PackageQueries.packageInfo(pm, "com.byd.avc")?.longVersionCode ?: "-"}")
        appendLine("heyKachi=${Prefs.wakeEnabled(ctx)} · voiceKey=${Prefs.voiceKeyEnabled(ctx)}")
        // WAKE-WEAK-UNIT — dòng trạng thái mỗi phút của `:wake` (CPU % · máy bận/rảnh · trần · ms nạp · ms chờ nạp); "-" = wake chưa nghe đủ 1 phút.
        appendLine("wake: ${com.byd.clusternav.launcher.voice.VoiceWakePacing.lastStatus(ctx) ?: "-"}")
        appendLine("KachiPerf: ${KachiPerf.lastLine ?: "-"}")   // "-" = chưa đủ 1 phút ở màn chính
    }

    private const val MB = 20
}
