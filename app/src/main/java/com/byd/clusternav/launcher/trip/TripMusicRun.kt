package com.byd.clusternav.launcher.trip

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import com.byd.clusternav.modules.clustercast.StackEntry
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
 *  4. chưa có: app nằm trong một ô đang hiện (đọc MỚI — [Ports.where]) ⇒ A2 · 2.89: CHỜ chính ô đó có app sống
 *     ([TripMusicPlace.await]: nhịp đo ô / đọc thẳng `am stack list`, trần 90 s, không quá hạn chuyến) — không `force-stop`,
 *     không BEHIND-HOME, không task thứ hai; quá trần ⇒ `SLOT_WAIT`, 0 lệnh phát. App hệ thống ngoài ô ⇒ `SYSTEM_APP` (R0.6);
 *     còn lại ⇒ A2 (2) ô 7 ([Ports.park]: màn ảo ẩn, để yên) TRƯỚC, đường cũ ([Ports.behind]: ô sống / màn ảo ẩn + giữ chỗ)
 *     chỉ khi ô 7 không chạy được ([TripMusicPlace.fallBack]) — ghi cả hai mã vào nhật ký;
 *  5. chờ phiên của app → `playFromUri` (phiên nhận URI) · K4-VIEW ([Ports.view], có link mà không phiên nhận — L4 · D3(ii);
 *     app ở ô ⇒ `--display <màn ảo ô>`, app ở ô 7 ⇒ `--display <màn ảo đỗ>` — cùng display thì không `reparentToDisplay`)
 *     · `play()` (không link) · chỉ mở (`NO_SESSION` — Cài đặt nói rõ: đặt link để tự phát).
 *  6. 2.94 · R3 — "Phát gì" trống với kiểu không tự phát tiếp (YouTube): bài lưu khớp tiêu đề ⇒ link của nó đi bước 5, rồi
 *     chờ đúng bài + `seekTo` ([TripMusicResume]). Không có / không khớp ⇒ bước 5 như cũ.
 *
 * Trả MỘT [Done]: mã bước cho sổ (Cài đặt dịch thành câu; app ở ô ⇒ bước mang số ô — *"Nhạc (YouTube ở ô 1)"*) + một ghi chú
 * ASCII cho nhật ký `KachiTrip`.
 */
internal class TripMusicRun(
    private val app: Context,
    private val sleep: (Long) -> Unit,
    private val ports: Ports,
) {
    /** Phần màn chính bước nhạc cần — `TripRun` cấp; đều CHẶN, đều đi qua mutex `kachi-behind` của màn. */
    interface Ports {
        /** R0.3 / L4 · D2 — chạy [pkg] phía sau màn nhà (ô sống trước, không thì màn ảo ẩn). A2: đường CŨ, đứng sau [park]. */
        fun behind(pkg: String): BehindHomeSequence.Outcome

        /** A2 (2) — ô 7: mở [pkg] lên màn ảo ẩn của Kachi rồi để yên ở đó (`HiddenPark`) — không giữ chỗ, không move-task. */
        fun park(pkg: String): BehindHomeSequence.Outcome

        /** A2 (1) — chỗ của [pkg] đọc MỚI từ màn chính (ô 0-based + host ô); `null` = màn chưa trả lời. */
        fun where(pkg: String): TripMusicPlace.Where?

        /** A2 (1) — một bản `am stack list` (CHỈ ĐỌC); `null` = đọc hỏng / chưa có kênh. Chỉ để đọc thẳng ô của app nhạc. */
        fun stacks(): List<StackEntry>?

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
    private val resumer = TripMusicResume(app, bridge, sleep)
    private val audio: AudioManager? = app.getSystemService(AudioManager::class.java)

    private fun musicActive(): Boolean = runCatching { audio?.isMusicActive == true }.getOrDefault(false)

    /**
     * Một lượt bước nhạc. [deadlineAt] = mốc `elapsedRealtime` hết hạn chuyến (chờ ô không bao giờ vượt nó); [progress] = báo thứ
     * đang chờ cho Cài đặt (`TripStart.progress` — cờ RAM CHỈ để hiển thị, CLAUDE.md §5), `null` = thôi chờ. [slotsAtStart] =
     * gói → ô của ảnh chụp chuyến vừa đọc — CHỈ dùng khi lượt đọc mới không có trả lời ([TripMusicPlace.entrySlot]).
     */
    fun run(
        music: TripMusic,
        installed: Set<String>,
        deadlineAt: Long,
        progress: (TripWaitMark?) -> Unit = {},
        slotsAtStart: Map<String, Int> = emptyMap(),
    ): Done {
        val target = VoiceAppTargets.byKey(music.mode.targetKey) ?: return done(music.mode.code, TripStepCode.NOT_INSTALLED, "music:off")
        val pkg = target.packageIn(installed)
        val id = pkg ?: music.mode.code
        val before = bridge.sessions()
        val active = musicActive()
        // A2: ô của app nhạc đọc MỚI lúc bước bắt đầu (không ảnh chụp đầu chuyến — ô của nó có thể chưa hiện trong ảnh đó).
        // Review 2.89 Pass 1 · behaviour-1: màn chưa trả lời (`null`) ≠ "ngoài ô" ⇒ ô của ảnh chụp, không phải ô 7.
        val slot0 = pkg?.let { TripMusicPlace.entrySlot(ports.where(it), slotsAtStart[it]) }
        Log.i(TripStart.TAG, "music facts pkg=$id system=${pkg?.let(ports::isSystem)} ${pkg?.let(ports::facts) ?: "-"} " +
            "sessions=${describe(before)} musicActive=$active slot=${slot0 ?: "-"} query=${music.query.isNotEmpty()}")
        val gate = TripMusicPlan.gate(music.mode, pkg, before)
        TripOutcome.ofGate(gate)?.let { return done(id, it, "music:$id:$gate", slot0) }
        if (pkg == null) return done(id, TripStepCode.NOT_INSTALLED, "music:$id:NOT_INSTALLED")
        val over = if (TripMusicPlan.otherPlaying(pkg, before, active)) " override" else ""
        // R3.4 bước 1 — phiên có TRƯỚC khi Kachi đụng gì (app sống qua lần tắt máy): tiếp tục đúng nó, bỏ qua "Phát gì".
        // App trong ô KHÔNG tính: ô vừa force-stop + mở lại nó, phiên là của Kachi (KDoc [TripMusicPlan.preexisting]).
        val inSlot = slot0 != null
        if (TripMusicPlan.preexisting(pkg, before, inSlot = inSlot)) {
            return verified(id, pkg, "music:$pkg:resume-existing=${bridge.playPackage(pkg)}$over", VERIFY_TRIES, slot0)
        }
        // 2.94 · R3 — "Phát gì" trống + kiểu không tự phát tiếp ⇒ tìm lại bài YouTube đã lưu (tiêu đề phải khớp). Không có / không
        // khớp ⇒ `null` ⇒ đúng đường cũ bên dưới. Có ⇒ link đi CHÍNH đường link của ô "Phát gì" (phiên nhận URI · K4-VIEW).
        val resume = resumer.prepare(target, music)
        val url = resume.ready?.url ?: urlFor(target, pkg, music.query)
        val start = when (val st = start(pkg, id, slot0, deadlineAt, progress)) {
            is Start.Stop -> return st.done
            is Start.Go -> st
        }
        val slot = start.slot
        val session = awaitSession(pkg)
        when (TripMusicPlan.recheck(pkg, bridge.sessions())) {
            TripMusicPlan.Recheck.CLEAR -> Unit
            TripMusicPlan.Recheck.SELF_PLAYING -> return done(id, TripStepCode.PLAYING, "music:$pkg:${start.note}:self-playing$over", slot)
            TripMusicPlan.Recheck.UNKNOWN_MEDIA -> return done(id, TripStepCode.UNKNOWN_MEDIA, "music:$pkg:${start.note}:recheck-UNKNOWN_MEDIA", slot)
        }
        val base = "music:$pkg:${start.note}$over:" + if (resume.note.isEmpty()) "" else "${resume.note}:"
        return when (val p = TripMusicPlan.play(url, session)) {
            is TripMusicPlan.Play.FromUri -> verified(id, pkg, base + "uri=${bridge.playFromUri(pkg, p.url)}", VERIFY_TRIES, slot, resume.ready)
            TripMusicPlan.Play.Resume -> verified(id, pkg, base + "resume=${bridge.playPackage(pkg)}", VERIFY_TRIES, slot)
            // Review 287 [P2]: không dùng ảnh chụp ô ĐẦU chuyến — cổng đọc lại lúc giao. Không lệnh nào đi ⇒ mã NOOP, không "đã gửi".
            is TripMusicPlan.Play.View -> ports.view(pkg, p.url, slot != null).let { o ->
                val code = TripOutcome.ofView(slot != null, o?.result)
                if (code.result == TripStepCode.Result.NOOP) done(id, code, base + "view:${o?.result ?: code}", slot)
                else verified(id, pkg, base + "view(${o?.result})", VIEW_VERIFY_TRIES, slot, resume.ready)
            }
            TripMusicPlan.Play.OpenOnly -> done(id, TripStepCode.NO_SESSION, base + "open-only (no session)", slot)
        }
    }

    /** Kết quả bước "đưa app lên": đi tiếp với ghi chú + ô (nếu app ở ô), hoặc dừng với một [Done]. */
    private sealed interface Start {
        data class Go(val note: String, val slot: Int?) : Start
        data class Stop(val done: Done) : Start
    }

    /**
     * A2 (1)(2) — đưa app nhạc lên ĐÚNG chỗ của nó. Ở ô [slot0] ⇒ CHỜ chính ô đó sống (ô tự mở app — không lệnh nào ở đây);
     * rời bố cục giữa lúc chờ ⇒ như app ngoài ô. Ngoài ô ⇒ app hệ thống từ chối (R0.6) · ô 7 trước · đường cũ chỉ khi ô 7
     * không chạy được ([TripMusicPlace.fallBack], CLAUDE.md §6 — ngoại lệ có đo, KDoc `HiddenPark`).
     */
    private fun start(pkg: String, id: String, slot0: Int?, deadlineAt: Long, progress: (TripWaitMark?) -> Unit): Start {
        if (slot0 != null) {
            progress(TripWaitMark.slotApp(pkg, slot0))
            val w = try {
                TripMusicPlace.await(
                    pkg, slot0, TripMusicPlace.until(SystemClock.elapsedRealtime(), deadlineAt), SystemClock::elapsedRealtime, sleep,
                    read = { ports.where(pkg) }, stacks = ports::stacks, onSlot = { progress(TripWaitMark.slotApp(pkg, it)) },
                )
            } finally {
                progress(null)
            }
            when (w) {
                is TripMusicPlace.Waited.Alive -> return Start.Go("in-slot:${w.slot}:alive+${w.ms}ms", w.slot)
                is TripMusicPlace.Waited.Timeout ->
                    return Start.Stop(done(id, TripStepCode.SLOT_WAIT, "music:$pkg:slot-wait:${w.slot}:${w.ms}ms", w.slot))
                is TripMusicPlace.Waited.Left -> Log.i(TripStart.TAG, "music $pkg left slot $slot0 after ${w.ms}ms -> outside path")
            }
        }
        if (ports.isSystem(pkg)) return Start.Stop(done(id, TripStepCode.SYSTEM_APP, "music:$pkg:SYSTEM_APP"))
        val parked = ports.park(pkg)
        val out = if (TripMusicPlace.fallBack(parked.result)) ports.behind(pkg) else parked
        val trail = if (out === parked) "park=${parked.result}" else "park=${parked.result}->behind=${out.result}"
        val code = TripOutcome.ofBehind(out.result)
        if (code.result == TripStepCode.Result.NOOP) return Start.Stop(done(id, code, "music:$pkg:$trail"))
        return Start.Go(trail, null)
    }

    private fun done(pkg: String, code: TripStepCode, note: String, slot: Int? = null) = Done(TripStep(pkg, TripStepKind.MUSIC, code, slot), note)

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

    /**
     * Đọc lại sau lệnh (không quyết gì — để sổ nói thật: ĐANG PHÁT hay mới chỉ gửi). [slot] = ô của app (nếu có) cho câu Cài đặt.
     * [resume] ≠ `null` (2.94 · R3) ⇒ chờ ĐÚNG bài đã lưu phát rồi tua ([TripMusicResume.finish]) thay cho vòng chờ "đang phát".
     */
    private fun verified(id: String, pkg: String, note: String, tries: Int, slot: Int?, resume: TripMusicResume.Ready? = null): Done {
        if (resume != null) {
            val (playing, how) = resumer.finish(pkg, resume)
            return done(id, if (playing) TripStepCode.PLAYING else TripStepCode.SENT, "$note $how", slot)
        }
        repeat(tries) {
            sleep(TripMusicPlan.SESSION_POLL_MS)
            if (bridge.sessions().orEmpty().any { it.pkg == pkg && it.playing }) return done(id, TripStepCode.PLAYING, "$note playing", slot)
        }
        return done(id, TripStepCode.SENT, "$note sent", slot)
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
