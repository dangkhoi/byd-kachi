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
        order(tick, "SystemClock.elapsedRealtime() < holdUntil", "musicActive(app)", "YoutubeResume.wanted(WorkspacePrefs(app).tripConfig().music)",
            "b.lives()", "YoutubeResume.pick(lives, YoutubeResume.watchedTargets())", "YoutubeResume.sample(", "store.write(s)",
            "YoutubeResume.shouldLog(prev, s, lastLogAt)")
        assertTrue(sampler.contains("Thread(r, \"kachi-yt-resume\").apply { isDaemon = true }"), "luồng riêng, daemon")
        assertTrue(sampler.contains("scheduleWithFixedDelay("), "nhịp đều, không chồng lượt")
        assertFalse(sampler.contains("MediaSessionManager") || resume.contains("MediaSessionManager"), "một đường đọc phiên: MediaBridge")
        val log = sampler.lines().filter { "Log.i(" in it }
        assertEquals(1, log.size, "một dòng nhật ký lưu")
        assertFalse(log.single().contains("s.title}") || log.single().contains("s.channel"), "nhật ký không mang nội dung người dùng nghe")
    }

    @Test
    fun `len xe - phat tiep chi thay URL cua duong cu, sau phien co truoc, truoc khi dua app len`() {
        val fn = SourceRoots.body(music, "slotsAtStart: Map<String, Int> = emptyMap(),\n    ): Done {")
        order(fn, "TripMusicPlan.preexisting(pkg, before, inSlot = inSlot)", "resumer.prepare(target, music)",
            "resume.ready?.url ?: urlFor(target, pkg, music.query)", "start(pkg, id, slot0, deadlineAt, progress)",
            "TripMusicPlan.play(url, session)", "bridge.playFromUri(pkg, p.url)", "resume.ready)",
            // 2.96 · R9 — ĐỔI GHIM có lý do: K4-VIEW đi qua `viewWhenReady` (chờ ô sống lại khi ô chưa sẵn — log xe 07/10 21:05:14).
            "viewWhenReady(pkg, p.url, slot, deadlineAt, progress, resume.ready, target.watchFullscreenExtra)", "resume.ready)",
            // 2.96 · R9 — cờ giữ bên lưu nhả trên MỌI lối ra (lối NOOP / dừng sớm không tới `finish`).
            "} finally {", "resumer.close(resume)")
        val vw = SourceRoots.body(music, "): TripMusicPlace.ViewTry {")
        // 2.96 · R10 — toàn màn CHỈ khi phát tiếp (`ready` ≠ null); link của ô "Phát gì" đi như cũ.
        order(vw, "val fs = if (ready != null) fullscreenExtra else null", "if (ready != null) resumer.keep()", "TripMusicPlace.viewWhenReady(", "TripMusicPlace.until(SystemClock.elapsedRealtime(), deadlineAt)",
            "read = { ports.where(pkg) }", "stacks = ports::stacks", "ports.view(pkg, url, inSlot, fs)", "finally {", "progress(null)")
        val close = SourceRoots.body(resume, "fun close(p: Prep) {")
        assertTrue(close.contains("if (p.ready != null) YoutubeResumeSampler.release()"), "chỉ nhả khi prepare đã giữ")
        val v = SourceRoots.body(music, "private fun verified(")
        order(v, "if (resume != null) {", "resumer.finish(pkg, resume)", "TripStepCode.PLAYING else TripStepCode.SENT")
    }

    @Test
    fun `tieu de khop TRUOC khi co URL, tua chi sau khi phat DUNG bai, giu ben luu`() {
        val prep = SourceRoots.body(resume, "fun prepare(target: VoiceAppTarget, music: TripMusic): Prep {")
        order(prep, "YoutubeResume.wanted(music)", "YoutubeResume.resumePlan(YoutubeResumeStore(app).read(), target.key",
            "YoutubeResumeSampler.hold()", "VoiceYoutubeResolver.firstVideoBounded(plan.query)", "YoutubeResume.matches(hit.title, plan.title)",
            "TripMusicPlan.watchUrl(target, hit.id)", "Prep(Ready(url, plan)")
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
        assertTrue(resolver.contains("search(query, quiet = true) { YoutubeSearchParse.firstVideo(it, MAX_CHARS) }"))
        assertTrue(resolver.contains("fun firstVideoBounded(query: String): YoutubeSearchParse.Hit? = bounded(query, quiet = true, ::firstVideo)"))
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
            "VoiceYoutubeResolver.firstVideoBounded(" to "TripMusicResume.kt",
            "YoutubeResumeStore(app).read()" to "TripMusicResume.kt",
            "YoutubeResume.resumePlan(" to "TripMusicResume.kt",
            "YoutubeResume.sample(" to "YoutubeResumeSampler.kt",
            "YoutubeSearchParse.firstVideo(" to "VoiceYoutubeResolver.kt",
            "YoutubeResume.KEY to" to "TripGate.kt",
        ).forEach { (call, file) ->
            assertTrue(all.any { it.first == file && it.second.contains(call) }, "'$call' phải được gọi trong $file")
        }
    }
}
