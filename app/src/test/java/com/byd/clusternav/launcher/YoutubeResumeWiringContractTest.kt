package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.94 · R3 — bài canh TĨNH cho "YouTube phát tiếp" (spec `kachi-294-plan.html` §4.3). Quyết định thuần có test ở `:core`
 * (`YoutubeResumeTest`, `YoutubeSearchParseTest`); ở đây khoá DÂY NỐI:
 *  - bên lưu được cài ở tiến trình chính, trước dòng chốt cuối; dừng ở cổng rẻ nhất; đọc phiên qua CHÍNH `MediaBridge`;
 *    nhật ký không mang tiêu đề;
 *  - lên xe: phát tiếp chỉ thay URL của đường cũ (không đường phát thứ hai), tiêu đề phải khớp TRƯỚC khi có URL, tua chỉ
 *    sau khi phiên phát ĐÚNG bài; không tên gói trong logic;
 *  - mọi hàm mới có call site thật (CLAUDE.md §8).
 */
class YoutubeResumeWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    private val application by lazy { code("src/main/java/com/byd/clusternav/KachiApplication.kt") }
    private val sampler by lazy { code("src/main/java/com/byd/clusternav/launcher/trip/YoutubeResumeSampler.kt") }
    private val resume by lazy { code("src/main/java/com/byd/clusternav/launcher/trip/TripMusicResume.kt") }
    private val music by lazy { code("src/main/java/com/byd/clusternav/launcher/trip/TripMusicRun.kt") }
    private val resolver by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceYoutubeResolver.kt") }

    private fun order(src: String, vararg marks: String) {
        var at = -1
        for (m in marks) {
            val i = src.indexOf(m, at + 1)
            assertTrue(i > at, "thứ tự sai/thiếu: '$m' phải đứng sau mốc trước trong:\n$src")
            at = i
        }
    }

    @Test
    fun `ben luu cai o tien trinh chinh, truoc dong chot cuoi`() {
        val onCreate = SourceRoots.body(application, "override fun onCreate() {")
        order(onCreate, "if (isBackgroundVoiceProcess()) return", "YoutubeResumeSampler.install(this)", "EarlyShellChannel.start(this)")
    }

    @Test
    fun `ben luu dung o cong re nhat, doc phien qua MediaBridge, ghi bang ham thuan`() {
        val tick = SourceRoots.body(sampler, "private fun tick(app: Context) {")
        // 2.97 · R2d (soát Pass 4 [P2]) — ĐỔI GHIM có lý do: cổng giữ = cờ chuyến HOẶC cờ riêng của tua muộn.
        order(tick, "SystemClock.elapsedRealtime() < maxOf(holdUntil, lateHoldUntil)", "musicActive(app)", "YoutubeResume.wanted(WorkspacePrefs(app).tripConfig().music)",
            "b.lives()", "YoutubeResume.pick(lives, YoutubeResume.watchedTargets())", "YoutubeResume.sample(", "store.write(s)",
            "YoutubeResume.shouldLog(prev, s, lastLogAt)")
        assertTrue(sampler.contains("Thread(r, \"kachi-yt-resume\").apply { isDaemon = true }"), "luồng riêng, daemon")
        assertTrue(sampler.contains("scheduleWithFixedDelay("), "nhịp đều, không chồng lượt")
        assertFalse(sampler.contains("MediaSessionManager") || resume.contains("MediaSessionManager"), "một đường đọc phiên: MediaBridge")
        val log = sampler.lines().filter { "Log.i(" in it }
        // 2.97 · R2d — ĐỔI GHIM có lý do: thêm 3 dòng tua muộn (armed / fired / cancelled) — vẫn MỘT dòng "saved".
        assertEquals(1, log.count { "saved target=" in it }, "một dòng nhật ký lưu")
        log.forEach { assertFalse(it.contains("title}") || it.contains("channel"), "nhật ký không mang nội dung người dùng nghe: $it") }
    }

    @Test
    fun `len xe - phat tiep chi thay URL cua duong cu, sau phien co truoc, truoc khi dua app len`() {
        val fn = SourceRoots.body(music, "slotsAtStart: Map<String, Int> = emptyMap(),\n    ): Done {")
        order(fn, "TripMusicPlan.preexisting(pkg, before, inSlot = inSlot)", "resumer.prepare(target, music)",
            "val url0 = resume.ready?.url ?: urlFor(target, pkg, music.query)", "start(pkg, id, slot0, deadlineAt, progress)",
            "awaitSession(pkg)", "TripMusicPlan.recheck(pkg, bridge.sessions())",
            // 2.97 · R2c — ĐỔI GHIM có lý do: lượt tìm treo vì mạng ⇒ KHÔNG ngủ ở đây (chặn app mở thường xếp sau) — giao phần thử lại
            // cho [Later] chạy cuối chuyến; cờ giữ chuyển cho nó (finally không nhả khi đã giao).
            "if (resume.pending != null) {", "handedOff = true", "deferred(id, pkg, target, resume, url0, base, slot, deadlineAt, progress, otherAtStart = over.isNotEmpty())",
            "release = { resumer.close(resume) }", ":deferred", "copy(later = later)",
            "return play(id, pkg, target, resume, url0, session, base, slot, deadlineAt, progress)",
            // 2.96 · R9 — cờ giữ bên lưu nhả trên MỌI lối ra (lối NOOP / dừng sớm không tới `finish`).
            "} finally {", "if (!handedOff) resumer.close(resume)")
        assertFalse(fn.contains("resumer.retry("), "R2c: không thử lại (ngủ) trong thân bước nhạc")
        // Đường hoãn: thử lại → đọc lại phiên (không đè nhạc người lái / app tự phát) → giao như đường thường → luôn nhả cờ giữ.
        order(SourceRoots.body(music, "private fun deferred("), "resume = resumer.retry(pending, deadlineAt)",
            "val now = bridge.sessions()", "TripMusicPlan.recheck(pkg, now)", "SELF_PLAYING -> return", "UNKNOWN_MEDIA -> return",
            // Soát R2c Pass 1 [P1] (spec R2c "đã có nhạc ⇒ không đè"): app KHÁC lên trong lúc chờ ⇒ không đè — trừ khi nguồn khác đã phát
            // từ đầu chuyến (BYD tự phát lại) thì giành như đường thường. Chỉ đọc PHIÊN (không `isMusicActive` — giọng dẫn đường).
            "if (!otherAtStart && TripMusicPlan.otherPlaying(pkg, now, musicActive = false))", "later:other-playing",
            "return play(id, pkg, target, resume, resume.ready?.url ?: url0, session, base, slot, deadlineAt, progress)",
            "} finally {", "resumer.close(resume)")
        assertTrue(fn.contains("otherAtStart = over.isNotEmpty()"), "phần hoãn phải biết nguồn khác đã phát từ đầu chuyến hay chưa")
        order(SourceRoots.body(music, "private fun play("), "TripMusicPlan.play(url, session)", "bridge.playFromUri(pkg, p.url)", "resume.ready)",
            // 2.96 · R9 — ĐỔI GHIM có lý do: K4-VIEW đi qua `viewWhenReady` (chờ ô sống lại khi ô chưa sẵn — log xe 07/10 21:05:14).
            "viewWhenReady(pkg, p.url, slot, deadlineAt, progress, resume.ready, target.watchFullscreenExtra)", "resume.ready)")
        // Bên chạy chuyến: phần hoãn chạy SAU vòng bước, trong hạn chuyến; hết hạn ⇒ drop (nhả cờ giữ).
        val trip = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/trip/TripStart.kt")
        order(SourceRoots.body(trip, "private fun body("), "r.later?.let { later = steps.lastIndex to it }",
            // Soát R2c Pass 1 [P2]: bước sau nhạc ném lỗi ⇒ phần hoãn không chạy ⇒ nhả cờ giữ ngay, không treo tới trần 180 s.
            "} catch (e: RuntimeException) { later?.second?.drop(); throw e }",
            "later?.let { (at, l) -> runLater(at, l, firstWake) }", "return TripOutcome.tripCode(steps)")
        order(SourceRoots.body(trip, "private fun runLater("), "if (!TripGate.withinDeadline(firstWake, now)) { l.drop()",
            "try { l.run() } catch (e: RuntimeException) { l.drop(); throw e }", "steps[at] = r.step")
        val vw = SourceRoots.body(music, "): TripMusicPlace.ViewTry {")
        // 2.96 · R10 — toàn màn CHỈ khi phát tiếp (`ready` ≠ null); link của ô "Phát gì" đi như cũ.
        order(vw, "val fs = if (ready != null) fullscreenExtra else null", "if (ready != null) resumer.keep()", "TripMusicPlace.viewWhenReady(", "TripMusicPlace.until(SystemClock.elapsedRealtime(), deadlineAt)",
            "read = { ports.where(pkg) }", "stacks = ports::stacks", "ports.view(pkg, url, inSlot, fs)", "finally {", "progress(null)")
        val close = SourceRoots.body(resume, "fun close(p: Prep) {")
        assertTrue(close.contains("if (p.ready != null || p.pending != null) YoutubeResumeSampler.release()"), "chỉ nhả khi prepare/retry còn giữ")
        val v = SourceRoots.body(music, "private fun verified(")
        order(v, "if (resume != null) {", "resumer.finish(pkg, resume)", "TripStepCode.PLAYING else TripStepCode.SENT")
    }

    @Test
    fun `tieu de khop TRUOC khi co URL, tua chi sau khi phat DUNG bai, giu ben luu`() {
        val prep = SourceRoots.body(resume, "fun prepare(target: VoiceAppTarget, music: TripMusic): Prep {")
        order(prep, "YoutubeResume.wanted(music)", "YoutubeResume.resumePlan(YoutubeResumeStore(app).read(), target.key",
            "YoutubeResumeSampler.hold()", "VoiceYoutubeResolver.topVideosBounded(plan.query)",
            "YoutubeSearchParse.Search.Offline -> Prep(null, \"resume:offline\", Pending(target, plan))", "picked(target, plan, r, \"resume\")")
        // 2.97 · R2 — chọn bài CHỈ qua `YoutubeResume.choose` (tiêu đề phải khớp) TRƯỚC khi có URL.
        val pick = SourceRoots.body(resume, "private fun picked(")
        order(pick, "as? YoutubeSearchParse.Search.Found", "YoutubeResume.choose(hits, plan)", "TripMusicPlan.watchUrl(target, hit.id)",
            "Prep(Ready(url, plan)")
        // 2.97 · R2 — thử lại: kiểm hạn TRƯỚC khi ngủ, gia hạn cờ giữ, chỉ lặp khi vẫn mất mạng, hết hạn ⇒ nhả cờ.
        val retry = SourceRoots.body(resume, "fun retry(p: Prep, deadlineAt: Long): Prep {")
        order(retry, "val pend = p.pending ?: return p", "minOf(t0 + YoutubeResume.SEARCH_RETRY_BUDGET_MS, deadlineAt)",
            "YoutubeResume.searchRetryDelayMs(attempt, Random.nextDouble())", "return released(", "sleep(wait)", "keep()",
            "VoiceYoutubeResolver.topVideosBounded(pend.plan.query)", "if (r != YoutubeSearchParse.Search.Offline)", "picked(pend.target, pend.plan, r,")
        val fin = SourceRoots.body(resume, "fun finish(pkg: String, ready: Ready, tries: Int = RESUME_WAIT_TRIES): Pair<Boolean, String> {")
        // Soát 2.94 Pass 1 [P2]: giữ LẠI ở đầu finish — chờ ô + dàn app + chờ phiên giữa prepare và finish có thể vượt trần giữ.
        order(fin, "YoutubeResumeSampler.hold()", "try {", "playingRight(pkg, ready)", "seek(pkg, ready.plan.seekMs)", "finally {",
            "YoutubeResumeSampler.release()")
        val right = SourceRoots.body(resume, "private fun playingRight(pkg: String, ready: Ready): Boolean =")
        assertTrue(right.contains("it.playing && YoutubeResume.matches(it.title, ready.plan.title)"), "chỉ tua khi phiên phát ĐÚNG bài")
        assertTrue(SourceRoots.body(resume, "private fun seek(pkg: String, ms: Long): String {").contains("bridge.seekPackage(pkg, ms)"))
        listOf("ACTION_VIEW", "startActivity", "am start", "playFromUri").forEach {
            assertFalse(resume.contains(it), "phát tiếp không dựng đường phát riêng — cấm '$it'")
        }
    }

    @Test
    fun `khong ten goi trong logic phat tiep`() {
        listOf(sampler, resume).forEach { src ->
            assertFalse(Regex("\"com\\.[a-z]").containsMatchIn(src), "tên gói phải từ bảng VoiceAppTargets, không chữ cứng")
        }
    }

    @Test
    fun `bo tim bai kem tieu de dung CHUNG mot duong mang voi o Phat gi`() {
        assertEquals(1, Regex(Regex.escape("HttpConn.open(")).findAll(resolver).count(), "một lượt GET duy nhất")
        // Soát 2.94 Pass 1 [P2] — truy vấn phát tiếp = tiêu đề + kênh người dùng đã xem ⇒ `quiet = true`: nhật ký lỗi / quá hạn chỉ
        // ghi độ dài, không ghi chữ. Lật `quiet` thành `false` ở đây là tiêu đề lại lọt logcat (bộ chụp chẩn đoán gom logcat).
        assertTrue(resolver.contains("fetch(query, quiet = true) { YoutubeSearchParse.topVideos(it, MAX_CHARS) }"))
        assertTrue(resolver.contains("bounded(query, quiet = true, ::topVideos) ?: YoutubeSearchParse.Search.Offline"), "quá hạn = mạng treo ⇒ thử lại được")
        order(resolver, "fun topVideos(query: String): YoutubeSearchParse.Search = try {", "catch (e: IOException)", "shown(query, true)", "YoutubeSearchParse.Search.Offline", "catch (e: RuntimeException)",
            "YoutubeSearchParse.Search.Failed", "private fun <T> search(")
        assertTrue(resolver.contains("if (quiet) \"<len=${'$'}{query.length}>\" else query"), "nhãn im lặng = chỉ độ dài")
    }

    @Test
    fun `moi ham moi deu co call site`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.map { p -> p.fileName.toString() to KotlinSource.stripComments(p.toFile().readText()) }
        assertTrue(all.size > 300, "quét được quá ít tệp (${all.size})")
        mapOf(
            "YoutubeResumeSampler.install(" to "KachiApplication.kt",
            "YoutubeResumeSampler.hold()" to "TripMusicResume.kt",
            "YoutubeResumeSampler.release()" to "TripMusicResume.kt",
            "TripMusicResume(" to "TripMusicRun.kt",
            "resumer.prepare(" to "TripMusicRun.kt",
            "resumer.finish(" to "TripMusicRun.kt",
            "resumer.keep()" to "TripMusicRun.kt",          // 2.96 · R9
            "resumer.close(resume)" to "TripMusicRun.kt",   // 2.96 · R9
            "bridge.seekPackage(" to "TripMusicResume.kt",
            "bridge.lives()" to "TripMusicResume.kt",
            "b.lives()" to "YoutubeResumeSampler.kt",
            "VoiceYoutubeResolver.topVideosBounded(" to "TripMusicResume.kt",
            "resumer.retry(" to "TripMusicRun.kt",                         // 2.97 · R2
            "YoutubeResume.choose(" to "TripMusicResume.kt",              // 2.97 · R2
            "YoutubeResume.searchRetryDelayMs(" to "TripMusicResume.kt",  // 2.97 · R2
            "YoutubeResumeStore(app).read()" to "TripMusicResume.kt",
            "YoutubeResume.resumePlan(" to "TripMusicResume.kt",
            "YoutubeResume.sample(" to "YoutubeResumeSampler.kt",
            "YoutubeSearchParse.topVideos(" to "VoiceYoutubeResolver.kt",
            "YoutubeResume.KEY to" to "TripGate.kt",
        ).forEach { (call, file) ->
            assertTrue(all.any { it.first == file && it.second.contains(call) }, "'$call' phải được gọi trong $file")
        }
    }

    /**
     * 2.97 · R2d — tua muộn: hết chờ phiên ⇒ hẹn tua, bên lưu xét khi PHIÊN đổi (sự kiện, không nhịp mới). Soát Pass 4: cờ giữ của tua
     * muộn là cờ RIÊNG (`lateHoldUntil`) — `release()` của chuyến y 2.96 (đua arm → SEEK → release không nhả nhầm); lượt phát tiếp MỚI
     * (`hold()`) bỏ lượt tua muộn cũ; không nghe được phiên ⇒ không hẹn; bài khác ⇒ MỘT lượt xét lại sau hạn khoan dung (quảng cáo).
     * Thử ĐỎ: bỏ `armLateSeek` khỏi `finish`; cho `release()` chạm `lateHoldUntil`; bỏ `late = null` khỏi `hold()`.
     */
    @Test
    fun `tua muon khi phien dich that phat`() {
        val fin = SourceRoots.body(SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/trip/TripMusicResume.kt"), "fun finish(")
        assertTrue("YoutubeResumeSampler.armLateSeek(pkg, ready.plan.title, ready.plan.seekMs)" in fin, fin)
        val smp = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/trip/YoutubeResumeSampler.kt")
        assertTrue("fun release() { holdUntil = 0L }" in smp, "release của chuyến y 2.96 — không đụng cờ tua muộn")
        val hold = SourceRoots.body(smp, "fun hold() {")
        assertTrue("late = null; lateHoldUntil = 0L" in hold, "lượt phát tiếp mới bỏ tua muộn cũ:\n$hold")
        assertTrue("onPlayback = { guarded { lateSeek() } }" in smp)
        val arm = SourceRoots.body(smp, "fun armLateSeek(")
        order(arm, "YoutubeLateSeek.worth(title, seekMs)", "if (!watching)", "return false", "lateHoldUntil = until")
        assertTrue("watching = b.watchPackages(" in smp && "} != null" in smp, "biết có nghe được phiên hay không")
        val ls = SourceRoots.body(smp, "private fun lateSeek()")
        assertTrue("YoutubeLateSeek.decide(p, b.lives(), now)" in ls && "b.seekPackage(p.pkg, p.seekMs)" in ls, ls)
        order(ls, "Action.SEEK ->", "settle(p, lateHold = now + YoutubeLateSeek.AFTER_SEEK_HOLD_MS)", "b.seekPackage(",
            "Action.CANCEL ->", "settle(p, lateHold = 0L)",
            "Action.OTHER ->", "if (p.otherSinceMs != 0L) return", "late = p.copy(otherSinceMs = now)", "exec.schedule(", "YoutubeLateSeek.OTHER_GRACE_MS")
        assertFalse("scheduleWithFixedDelay" in ls || "sleep(" in ls, "tua muộn chạy theo sự kiện phiên, không dò định kỳ")
        assertTrue("if (late !== p) return false" in SourceRoots.body(smp, "private fun settle("), "chỉ lượt còn đúng [late] mới chốt")
        val mb = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/MediaBridge.kt")
        assertTrue("override fun onPlaybackStateChanged(state: PlaybackState?) = onPlayback()" in mb && "if (fresh.isNotEmpty()) onPlayback()" in mb)
        val w = SourceRoots.body(mb, "fun watchPackages(")
        assertTrue("onChange: () -> Unit): (() -> Unit)? {" in mb && !w.contains("return {}"), "không nghe được ⇒ null, không hàm gỡ rỗng")
    }
}
