package com.byd.clusternav.launcher.trip

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTarget
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceYoutubeResolver

/**
 * ═══ F3 — TỰ MỞ NHẠC KHI LÊN XE: bên thi hành (luồng `kachi-trip`) ════════════════════════════════════════════════
 *
 * Spec R3 · §4.6 (R2 của Tasks) + L4 (owner 03/10 trên xe 2.86: *"auto mở nhạc youtube không chạy? Cả để trống lẫn để
 * link"*). Mọi quyết định ở [TripMusicPlan] (`:core`, KDoc ở đó có bằng chứng đo). Lớp này chỉ:
 *  1. đọc phiên nhạc qua [MediaBridge.sessions] (`null` = không biết ⇒ bỏ lượt) + ghi MỘT dòng sự thật đo (L4 · D1(d));
 *  2. chính app chọn đang phát ⇒ xong. Nguồn KHÁC đang phát (BYD tự phát lại nguồn cuối lúc nổ máy) KHÔNG còn chặn —
 *     chọn app cụ thể là lựa chọn của người dùng (L4 · D3(i));
 *  3. phiên của app ĐÃ có trước ⇒ `play()` đúng phiên đó — 0 lệnh cửa sổ;
 *  4. chưa có: app nằm trong một ô đang hiện ⇒ ô tự mở nó; app hệ thống ngoài ô ⇒ `SYSTEM_APP` (R0.6, nói thật — L4 ·
 *     D1(e)); còn lại ⇒ chạy phía sau màn nhà ([Ports.behind]: ô sống, không thì màn ảo ẩn — L4 · D2);
 *  5. chờ phiên của app → `playFromUri` (phiên nhận URI) · K4-VIEW ([Ports.view], có link mà không phiên nhận — L4 · D3(ii))
 *     · `play()` (không link) · chỉ mở (`NO_SESSION` — Cài đặt nói rõ: đặt link để tự phát).
 *
 * Trả MỘT [Done]: mã bước cho sổ (Cài đặt dịch thành câu) + một ghi chú ASCII cho nhật ký `KachiTrip`.
 */
internal class TripMusicRun(
    private val app: Context,
    private val sleep: (Long) -> Unit,
    private val ports: Ports,
) {
    /** Phần màn chính bước nhạc cần — `TripRun` cấp; đều CHẶN, đều đi qua mutex `kachi-behind` của màn. */
    interface Ports {
        /** R0.3 / L4 · D2 — chạy [pkg] phía sau màn nhà (ô sống trước, không thì màn ảo ẩn). */
        fun behind(pkg: String): BehindHomeSequence.Outcome

        /**
         * L4 · D3(ii) — K4-VIEW. [inSlot] ⇒ CHỈ màn ảo của ô app, đọc MỚI lúc gọi (`TripMusicPlan.viewRoute`, review 287 [P2]);
         * không ở ô ⇒ dàn qua chỗ dàn dựng. `null` = app ở ô mà ô chưa có màn ảo ⇒ 0 lệnh.
         */
        fun view(pkg: String, url: String, inSlot: Boolean): BehindHomeSequence.Outcome?

        /** L4 · D1(d) — sự thật đo cho nhật ký: task / pid của [pkg] (một `am stack list` + `pidof`). */
        fun facts(pkg: String): String

        /** `ApplicationInfo.FLAG_SYSTEM` (R0.6). */
        fun isSystem(pkg: String): Boolean
    }

    data class Done(val step: TripStep, val note: String)

    private val bridge = MediaBridge(app)
    private val audio: AudioManager? = app.getSystemService(AudioManager::class.java)

    private fun musicActive(): Boolean = runCatching { audio?.isMusicActive == true }.getOrDefault(false)

    fun run(music: TripMusic, installed: Set<String>, view: TripHub.HomeView): Done {
        val target = VoiceAppTargets.byKey(music.mode.targetKey) ?: return done(music.mode.code, TripStepCode.NOT_INSTALLED, "music:off")
        val pkg = target.packageIn(installed)
        val id = pkg ?: music.mode.code
        val before = bridge.sessions()
        val active = musicActive()
        Log.i(TripStart.TAG, "music facts pkg=$id system=${pkg?.let(ports::isSystem)} ${pkg?.let(ports::facts) ?: "-"} " +
            "sessions=${describe(before)} musicActive=$active inSlot=${pkg in view.appSlots} query=${music.query.isNotEmpty()}")
        val gate = TripMusicPlan.gate(music.mode, pkg, before)
        TripOutcome.ofGate(gate)?.let { return done(id, it, "music:$id:$gate") }
        if (pkg == null) return done(id, TripStepCode.NOT_INSTALLED, "music:$id:NOT_INSTALLED")
        val over = if (TripMusicPlan.otherPlaying(pkg, before, active)) " override" else ""
        // R3.4 bước 1 — phiên có TRƯỚC khi Kachi đụng gì (app sống qua lần tắt máy): tiếp tục đúng nó, bỏ qua "Phát gì".
        // App trong ô KHÔNG tính: ô vừa force-stop + mở lại nó, phiên là của Kachi (KDoc [TripMusicPlan.preexisting]).
        val inSlot = pkg in view.appSlots
        if (TripMusicPlan.preexisting(pkg, before, inSlot = inSlot)) {
            return verified(id, pkg, "music:$pkg:resume-existing=${bridge.playPackage(pkg)}$over", VERIFY_TRIES)
        }
        val url = urlFor(target, pkg, music.query)
        val start = when {
            inSlot -> "in-slot"
            ports.isSystem(pkg) -> return done(id, TripStepCode.SYSTEM_APP, "music:$pkg:SYSTEM_APP")
            else -> ports.behind(pkg).let { o ->
                val code = TripOutcome.ofBehind(o.result)
                if (code.result == TripStepCode.Result.NOOP) return done(id, code, "music:$pkg:${o.result}")
                o.result.name
            }
        }
        val session = awaitSession(pkg)
        when (TripMusicPlan.recheck(pkg, bridge.sessions())) {
            TripMusicPlan.Recheck.CLEAR -> Unit
            TripMusicPlan.Recheck.SELF_PLAYING -> return done(id, TripStepCode.PLAYING, "music:$pkg:$start:self-playing$over")
            TripMusicPlan.Recheck.UNKNOWN_MEDIA -> return done(id, TripStepCode.UNKNOWN_MEDIA, "music:$pkg:$start:recheck-UNKNOWN_MEDIA")
        }
        val base = "music:$pkg:$start$over:"
        return when (val p = TripMusicPlan.play(url, session)) {
            is TripMusicPlan.Play.FromUri -> verified(id, pkg, base + "uri=${bridge.playFromUri(pkg, p.url)}", VERIFY_TRIES)
            TripMusicPlan.Play.Resume -> verified(id, pkg, base + "resume=${bridge.playPackage(pkg)}", VERIFY_TRIES)
            // Review 287 [P2]: không dùng ảnh chụp ô ĐẦU chuyến ([view]) — cổng đọc lại lúc giao. Không lệnh nào đi ⇒ mã NOOP, không "đã gửi".
            is TripMusicPlan.Play.View -> ports.view(pkg, p.url, inSlot).let { o ->
                val code = TripOutcome.ofView(inSlot, o?.result)
                if (code.result == TripStepCode.Result.NOOP) done(id, code, base + "view:${o?.result ?: code}")
                else verified(id, pkg, base + "view(${o?.result})", VIEW_VERIFY_TRIES)
            }
            TripMusicPlan.Play.OpenOnly -> done(id, TripStepCode.NO_SESSION, base + "open-only (no session)")
        }
    }

    private fun done(pkg: String, code: TripStepCode, note: String) = Done(TripStep(pkg, TripStepKind.MUSIC, code), note)

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

    /** Chờ phiên của [pkg] hiện ra (app vừa mở) tối đa [TripMusicPlan.SESSION_WAIT_MS]. `null` = không có. */
    private fun awaitSession(pkg: String): TripMusicPlan.Session? {
        val until = SystemClock.elapsedRealtime() + TripMusicPlan.SESSION_WAIT_MS
        while (true) {
            bridge.sessions().orEmpty().firstOrNull { it.pkg == pkg }?.let { return it }
            if (SystemClock.elapsedRealtime() >= until) return null
            sleep(TripMusicPlan.SESSION_POLL_MS)
        }
    }

    /** Đọc lại sau lệnh (không quyết gì — để sổ nói thật: ĐANG PHÁT hay mới chỉ gửi). */
    private fun verified(id: String, pkg: String, note: String, tries: Int): Done {
        repeat(tries) {
            sleep(TripMusicPlan.SESSION_POLL_MS)
            if (bridge.sessions().orEmpty().any { it.pkg == pkg && it.playing }) return done(id, TripStepCode.PLAYING, "$note playing")
        }
        return done(id, TripStepCode.SENT, "$note sent")
    }

    /** Phiên cho dòng nhật ký: `pkg:play|stop:uri` — ASCII, không tiêu đề bài (không ghi nội dung người dùng nghe). */
    private fun describe(s: List<TripMusicPlan.Session>?): String =
        s?.joinToString(",", "[", "]") { "${it.pkg}:${if (it.playing) "play" else "stop"}${if (it.acceptsUri) ":uri" else ""}" } ?: "null"

    private companion object {
        const val VERIFY_TRIES = 8

        /** K4-VIEW: app phải tải trang + video từ mạng — chờ lâu hơn (≈ 20 s). */
        const val VIEW_VERIFY_TRIES = 20
    }
}
