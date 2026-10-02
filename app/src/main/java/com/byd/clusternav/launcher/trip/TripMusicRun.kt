package com.byd.clusternav.launcher.trip

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTarget
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceYoutubeResolver

/**
 * ═══ F3 — TỰ MỞ NHẠC KHI LÊN XE: bên thi hành (luồng `kachi-trip`) ════════════════════════════════════════════════
 *
 * Spec R3 · §4.6 (R2 của Tasks); mọi quyết định ở [TripMusicPlan] (`:core`, KDoc ở đó có bằng chứng đo). Lớp này chỉ:
 *  1. đọc phiên nhạc qua [MediaBridge.sessions] (`null` = không biết ⇒ bỏ lượt) + `AudioManager.isMusicActive`;
 *  2. phiên của app ĐÃ có trước ⇒ `play()` đúng phiên đó (tiếp tục thứ người lái đang nghe) — 0 lệnh cửa sổ;
 *  3. chưa có: app nằm trong một ô đang hiện ⇒ ô tự mở nó (đường ô sẵn có), KHÔNG lệnh nào thêm; không ⇒ chạy nó phía sau
 *     màn nhà ([behind] = R0.3 qua MAIN/LAUNCHER — [ĐO] ở lại màn ảo, không che màn nhà);
 *  4. chờ phiên của app hiện ra → kiểm lại "không đè" NGAY trước lệnh → `playFromUri` (link / từ khoá giải bằng lõi
 *     chung của giọng nói) hoặc `play()` (bài cũ của app) hoặc chỉ để app mở.
 *
 * Không có ý-định VIEW nào: [ĐO máy ảo 02/10 `trip/tm3-ytmusic.txt`] ý-định VIEW vào màn ảo ô bị app trung chuyển lên
 * display 0 ⇒ che màn nhà. Trả MỘT dòng ASCII cho sổ kết quả (`music:<bước>`).
 */
internal class TripMusicRun(
    private val app: Context,
    private val sleep: (Long) -> Unit,
    /** R0.3 qua màn chính (`TripRun.behind`) — `null` = không có ô sống. */
    private val behind: (String) -> BehindHomeSequence.Outcome?,
) {
    private val bridge = MediaBridge(app)
    private val audio: AudioManager? = app.getSystemService(AudioManager::class.java)

    private fun musicActive(): Boolean = runCatching { audio?.isMusicActive == true }.getOrDefault(false)

    fun run(music: TripMusic, installed: Set<String>, view: TripHub.HomeView): String {
        val target = VoiceAppTargets.byKey(music.mode.targetKey) ?: return "music:off"
        val pkg = target.packageIn(installed)
        val before = bridge.sessions()
        val gate = TripMusicPlan.gate(music.mode, pkg, before, musicActive())
        if (gate != TripMusicPlan.Gate.GO || pkg == null) return "music:$gate"
        // R3.4 bước 1 — phiên có TRƯỚC khi Kachi đụng gì (app sống qua lần tắt máy): tiếp tục đúng nó, bỏ qua "Phát gì".
        // App trong ô KHÔNG tính: ô vừa force-stop + mở lại nó, phiên là của Kachi (KDoc [TripMusicPlan.preexisting]).
        if (TripMusicPlan.preexisting(pkg, before, inSlot = pkg in view.appSlots)) {
            return "music:$pkg:resume-existing=${bridge.playPackage(pkg)}${verify(pkg)}"
        }
        val url = urlFor(target, pkg, music.query)
        val start = if (pkg in view.appSlots) "in-slot" else behind(pkg)?.result?.name ?: return "music:$pkg:NO_STAGE"
        if (start == BehindHomeSequence.Result.X_NOT_STAGED.name) return "music:$pkg:not-staged"
        val hasSession = awaitSession(pkg)
        when (val r = TripMusicPlan.recheck(pkg, bridge.sessions(), musicActive())) {
            TripMusicPlan.Recheck.CLEAR -> Unit
            else -> return "music:$pkg:$start:recheck-$r"
        }
        return "music:$pkg:$start:" + when (val p = TripMusicPlan.play(url, hasSession)) {
            is TripMusicPlan.Play.FromUri -> "uri=${bridge.playFromUri(pkg, p.url)}${verify(pkg)}"
            TripMusicPlan.Play.Resume -> "resume=${bridge.playPackage(pkg)}${verify(pkg)}"
            TripMusicPlan.Play.OpenOnly -> "open-only (no session)"
        }
    }

    /**
     * Ô "Phát gì" → URL xem chuẩn, hoặc `null`. Link ⇒ bóc `video_id` rồi dựng lại từ khuôn của bảng (chuỗi người dùng
     * không đi đâu cả). Từ khoá ⇒ LÕI CHUNG của giọng nói ([VoiceAppIntents.watchHandoff] + [VoiceYoutubeResolver] có hạn
     * cứng 7 s) — không có đường giải bài thứ hai. Kết quả nào cũng qua [TripMusicPlan.safeWatchUrl] trước khi dùng.
     */
    private fun urlFor(target: VoiceAppTarget, pkg: String, query: String): String? = when (val s = TripMusicPlan.source(query)) {
        is TripMusicPlan.Source.Video -> TripMusicPlan.watchUrl(target, s.id)
        is TripMusicPlan.Source.Keyword -> VoiceAppIntents.watchHandoff(target, pkg, s.q, VoiceYoutubeResolver::firstVideoIdBounded)
            ?.let { VoiceAppIntents.urlOf(it) }?.takeIf { TripMusicPlan.safeWatchUrl(it) }
        TripMusicPlan.Source.None, TripMusicPlan.Source.BadLink -> null
    }

    /** Chờ phiên của [pkg] hiện ra (app vừa mở) tối đa [TripMusicPlan.SESSION_WAIT_MS]. */
    private fun awaitSession(pkg: String): Boolean {
        val until = SystemClock.elapsedRealtime() + TripMusicPlan.SESSION_WAIT_MS
        while (true) {
            if (bridge.sessions().orEmpty().any { it.pkg == pkg }) return true
            if (SystemClock.elapsedRealtime() >= until) return false
            sleep(TripMusicPlan.SESSION_POLL_MS)
        }
    }

    /** Đọc lại sau lệnh (không quyết gì — chỉ để sổ kết quả nói thật: đã phát hay mới chỉ gửi). */
    private fun verify(pkg: String): String {
        repeat(VERIFY_TRIES) {
            sleep(TripMusicPlan.SESSION_POLL_MS)
            if (bridge.sessions().orEmpty().any { it.pkg == pkg && it.playing }) return " playing"
        }
        return " sent"
    }

    private companion object {
        const val VERIFY_TRIES = 8
    }
}
