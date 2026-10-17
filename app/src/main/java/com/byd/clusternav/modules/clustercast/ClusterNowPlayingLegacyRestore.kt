package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.clearLegacyNowPlayingMarker
import com.byd.clusternav.hasLegacyNowPlayingSwitch
import com.byd.clusternav.launcher.CtlJournalStore
import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.legacyNowPlayingMarker
import com.byd.clusternav.modules.clustercast.nowplaying.ClusterNowPlayingLegacy
import com.byd.clusternav.modules.hal.BydHal
import com.byd.clusternav.modules.hal.BydHalContentPush
import com.byd.clusternav.removeLegacyNowPlayingSwitch

/**
 * ═══ 3.1.12 — lượt DỌN của bộ ghi nguồn 11 đã gỡ (3.1.11). Một bản, gỡ ở 3.1.13 ═══════════════════════════════════════════
 *
 * Luật ở `:core` [ClusterNowPlayingLegacy]. `KachiApplication.onCreate` (tiến trình chính):
 *  • không dấu bền ⇒ chỉ đọc prefs (+ xoá khoá công tắc cũ nếu còn) — 0 luồng, 0 HAL;
 *  • còn dấu ⇒ MỘT luồng nền một-lượt đọc nguồn cụm, trả nguồn cũ nếu vẫn là nguồn ghi đè, xoá dấu, ghi một dòng `np …` vào
 *    nhật ký lệnh xe ([CtlJournalStore]).
 * CLAUDE.md §4: không display, không app, không stack — một tín hiệu HAL device 1007, hoàn tác chính là việc của hàm này.
 */
object ClusterNowPlayingLegacyRestore {
    private const val TAG = "KachiNowPlaying"

    fun install(ctx: Context) {
        val a = ctx.applicationContext
        if (Prefs.hasLegacyNowPlayingSwitch(a)) Prefs.removeLegacyNowPlayingSwitch(a)
        val marker = Prefs.legacyNowPlayingMarker(a) ?: return
        Thread({
            try {
                ClusterNowPlayingLegacy.restoreAtStartup(marker, sink(a))?.let { Log.i(TAG, it) }
            } catch (e: RuntimeException) {
                Log.e(TAG, "np startup restore loi", e)
            }
        }, "kachi-np-legacy").apply { isDaemon = true; start() }
    }

    private fun sink(a: Context): ClusterNowPlayingLegacy.Sink = object : ClusterNowPlayingLegacy.Sink {
        private val dev: Any? by lazy {
            runCatching { BydHal.device(BydHal.INSTRUMENT, BydHal.systemBypassContext(), BydHal.bypass(a)) }.getOrNull()
        }

        override fun readInt(id: Int): Int? = runCatching { dev?.let { HalBindingTable.coerceInt(BydHal.readFeature(it, id)) } }.getOrNull()

        override fun writeInt(id: Int, value: Int): String = dev?.let { BydHalContentPush.pushMusicInt(it, id, value) } ?: "no-device"

        override fun clearMarker() { runCatching { Prefs.clearLegacyNowPlayingMarker(a) } }

        override fun log(line: String) = CtlJournalStore.journal(a).write(line)
    }
}
