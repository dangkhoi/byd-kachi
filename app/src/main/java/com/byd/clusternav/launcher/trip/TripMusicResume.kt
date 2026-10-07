package com.byd.clusternav.launcher.trip

import android.content.Context
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.launcher.voice.VoiceAppTarget
import com.byd.clusternav.launcher.voice.VoiceYoutubeResolver

/**
 * ═══ 2.94 · R3 — YOUTUBE PHÁT TIẾP lúc lên xe: bên thi hành (luồng `kachi-trip`) ════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-294-plan.html` R3 · §4.3. Mọi quyết định ở [YoutubeResume] (`:core`). [TripMusicRun] gọi hai chỗ:
 *  1. [prepare] — CHỈ khi kiểu nhạc không tự phát tiếp + ô "Phát gì" trống ([YoutubeResume.wanted]) + có bài lưu còn tươi
 *     ([YoutubeResume.resumePlan]): tìm `"<tiêu đề> <kênh>"` bằng CHÍNH bộ tìm của ô "Phát gì" ([VoiceYoutubeResolver], hạn
 *     cứng 7 s) ⇒ tiêu đề bài đầu phải khớp ([YoutubeResume.matches]) ⇒ URL xem dựng từ khuôn của bảng
 *     ([TripMusicPlan.watchUrl], qua `safeWatchUrl`). Hỏng bất kỳ bước nào ⇒ `ready = null` ⇒ bước nhạc đi đúng đường cũ
 *     (không link ⇒ phiên có thì `play()`, không thì chỉ mở) — KHÔNG BAO GIỜ phát một bài chưa khớp tiêu đề.
 *  2. [finish] — SAU lệnh phát của đường cũ (phiên nhận URI ⇒ `playFromUri`; không ⇒ K4-VIEW): chờ phiên của app ĐANG PHÁT
 *     ĐÚNG bài đó (quảng cáo đầu video mang tiêu đề khác ⇒ chờ tiếp) rồi `seekTo` ([MediaBridge.seekPackage]); đọc lại vị trí
 *     để sổ nói thật. [ĐO máy ảo 07/10] `&t=` không tua, `seekTo` thì tua.
 * Bên lưu ([YoutubeResumeSampler]) bị GIỮ từ [prepare] tới hết [finish]: mẫu ở giây 0 của bài vừa mở không được đè vị trí.
 */
internal class TripMusicResume(
    private val app: Context,
    private val bridge: MediaBridge,
    private val sleep: (Long) -> Unit,
) {
    /** Bài đã tìm và khớp: [url] để phát, [plan] để chờ đúng bài + tua. */
    data class Ready(val url: String, val plan: YoutubeResume.Plan)

    /** Kết quả [prepare]: [note] ASCII cho nhật ký `KachiTrip` (rỗng = tính năng không áp cho cấu hình này). */
    data class Prep(val ready: Ready?, val note: String)

    fun prepare(target: VoiceAppTarget, music: TripMusic): Prep {
        if (!YoutubeResume.wanted(music)) return Prep(null, "")
        val plan = YoutubeResume.resumePlan(YoutubeResumeStore(app).read(), target.key, System.currentTimeMillis())
            ?: return Prep(null, "resume:none")
        YoutubeResumeSampler.hold()
        val hit = VoiceYoutubeResolver.firstVideoBounded(plan.query)
            ?: return released(Prep(null, "resume:search-fail"))
        if (!YoutubeResume.matches(hit.title, plan.title)) return released(Prep(null, "resume:mismatch"))
        val url = TripMusicPlan.watchUrl(target, hit.id) ?: return released(Prep(null, "resume:bad-url"))
        return Prep(Ready(url, plan), "resume:found")
    }

    /**
     * Sau lệnh phát: tối đa [tries] nhịp [TripMusicPlan.SESSION_POLL_MS] chờ phiên [pkg] PHÁT đúng [Ready.plan] ⇒ tua. Trả
     * `true` = đang phát đúng bài (sổ: `PLAYING`), kèm ghi chú ASCII.
     */
    fun finish(pkg: String, ready: Ready, tries: Int = RESUME_WAIT_TRIES): Pair<Boolean, String> {
        // Soát 2.94 Pass 1 [P2]: giữ LẠI từ đây — từ [prepare] tới đây có thể đã trôi chờ ô (≤ 90 s) + dàn app + chờ phiên
        // (15 s) + VIEW, cộng vòng dưới (≤ 51 s) là vượt trần [YoutubeResumeSampler.HOLD_MAX_MS] ⇒ mẫu giây 0 đè vị trí.
        YoutubeResumeSampler.hold()
        try {
            repeat(tries) {
                sleep(TripMusicPlan.SESSION_POLL_MS)
                if (playingRight(pkg, ready)) return true to seek(pkg, ready.plan.seekMs)
            }
            return false to "resume-wait-timeout"
        } finally {
            YoutubeResumeSampler.release()
        }
    }

    private fun playingRight(pkg: String, ready: Ready): Boolean =
        bridge.lives().orEmpty().any { it.pkg == pkg && it.playing && YoutubeResume.matches(it.title, ready.plan.title) }

    private fun seek(pkg: String, ms: Long): String {
        if (ms <= 0L) return "resume-noseek"
        if (!bridge.seekPackage(pkg, ms)) return "resume-seek=$ms:no-session"
        repeat(SEEK_CHECK_TRIES) {
            sleep(TripMusicPlan.SESSION_POLL_MS)
            val at = bridge.lives().orEmpty().firstOrNull { it.pkg == pkg }?.positionMs ?: return@repeat
            if (at >= ms - SEEK_SLACK_MS) return "resume-seek=$ms:ok@$at"
        }
        return "resume-seek=$ms:sent"
    }

    private fun released(p: Prep): Prep = p.also { YoutubeResumeSampler.release() }

    private companion object {
        /** [ĐOÁN] ≈ 45 s: tải trang + video từ mạng xe (đường VIEW đã chờ 20 s) + một quảng cáo đầu video không bỏ qua được. */
        const val RESUME_WAIT_TRIES = 45

        /** Đọc lại vị trí sau `seekTo` ([ĐO máy ảo] tới đích trong ≈ 4 s). */
        const val SEEK_CHECK_TRIES = 6

        /** Vị trí đọc lại có thể trễ một nhịp cập nhật phiên. */
        const val SEEK_SLACK_MS = 5_000L
    }
}
