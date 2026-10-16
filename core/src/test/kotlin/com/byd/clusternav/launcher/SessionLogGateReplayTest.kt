package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.zip.GZIPInputStream

/**
 * ═══ LOG-VOLUME 3.1.10 — phát lại DÒNG LOG THẬT của xe qua cổng ghi, đo KB/phút trước/sau ═══════════════════════════
 *
 * Fixture `log/session-log-spikes-2026-10-16.log.gz`: dòng logcat của CHÍNH tiến trình Kachi (`logcat -v time`, đúng
 * thứ `KachiLog.startCapture` đọc) cắt từ logcat liên tục của ba chuyến xe — bốn cửa sổ 3 phút quanh phút đỉnh:
 * 16/10 09:52 · 16/10 10:02 · 15/10 18:23 · 15/10 07:56 [ĐO]. **Ẩn danh**: mọi chữ trong `'…'`/`"…"` → từ giả ổn định
 * (`w12`, giữ số/dấu câu), `bufferDataValue is [...]` (tên đường UTF-16) → dãy giả cùng độ dài, IPv4 → `10.0.0.N`.
 *
 * "Trước" = đúng luật 3.1.9 (`KachiLog.throttled` cũ: W/E/F/A đi thẳng, còn lại [LogLineThrottle] nguyên văn 10 s).
 * "Sau" = [SessionLogGate] + 3 dòng nav đã hạ xuống mức D ở nguồn (`NavigationHudOwner`, `NavNotificationListener`,
 * `ClusterBroadcaster`) — fixture là log 3.1.9 nên phép đo đổi mức của đúng 3 tag ấy; dòng `ClusterBroadcaster`
 * vẫn mang cửa sổ chạy chữ cũ (bản mới ghi tên đầy đủ) ⇒ số "sau" ở đây là cận TRÊN.
 *
 * Phút đầu mỗi cửa sổ là khởi động bộ nhớ của cổng (mọi câu đều "mới") ⇒ không tính, cho cả hai bên.
 *
 * Bài đỏ khi gỡ bản sửa: thay [SessionLogGate] bằng luật cũ ⇒ "sau" = "trước" ⇒ vế `≤ 60 %` trượt.
 */
class SessionLogGateReplayTest {

    private class Win(val lines: List<String>)

    private fun ms(l: String): Long {
        val mo = l.substring(0, 2).toLong(); val d = l.substring(3, 5).toLong()
        val h = l.substring(6, 8).toLong(); val m = l.substring(9, 11).toLong()
        val s = l.substring(12, 14).toLong(); val milli = l.substring(15, 18).toLong()
        return ((((mo * 31 + d) * 24 + h) * 60 + m) * 60 + s) * 1000 + milli
    }

    private fun windows(): List<Win> {
        val raw = requireNotNull(javaClass.getResourceAsStream("/log/session-log-spikes-2026-10-16.log.gz")) { "thiếu fixture" }
        val all = GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).readLines().filter { SessionLogGate.isRecordHead(it) }
        val out = ArrayList<Win>()
        var cur = ArrayList<String>()
        var last = Long.MIN_VALUE
        for (l in all) {
            val t = ms(l)
            if (cur.isNotEmpty() && kotlin.math.abs(t - last) > 120_000L) { out.add(Win(cur)); cur = ArrayList() }
            cur.add(l); last = t
        }
        if (cur.isNotEmpty()) out.add(Win(cur))
        return out
    }

    private val demoted = setOf("NavigationHudOwner", "NavListener", "ClusterBroadcaster")

    private fun tagOf(l: String): String = l.substring(SessionLogGate.SEVERITY_COL + 2, l.indexOf('(', SessionLogGate.SEVERITY_COL)).trim()

    private fun demote(l: String): String =
        if (l[SessionLogGate.SEVERITY_COL] == 'I' && tagOf(l) in demoted)
            l.substring(0, SessionLogGate.SEVERITY_COL) + 'D' + l.substring(SessionLogGate.SEVERITY_COL + 1)
        else l

    /** Luật 3.1.9, chép nguyên văn từ `KachiLog.throttled` cũ. */
    private fun before(w: Win): List<Pair<Long, String>> {
        val t = LogLineThrottle()
        return w.lines.mapNotNull { l ->
            val now = ms(l)
            val out = if (l[SessionLogGate.SEVERITY_COL] in "WEFA") l
            else t.suppressedBefore(l.substring(SessionLogGate.SEVERITY_COL), now)?.let { l + LogLineThrottle.repeatSuffix(it) }
            out?.let { now to it }
        }
    }

    private fun after(w: Win, withDemotion: Boolean): List<Pair<Long, String>> {
        val g = SessionLogGate()
        return w.lines.mapNotNull { l ->
            val now = ms(l)
            g.offer(if (withDemotion) demote(l) else l, now)?.let { now to it }
        }
    }

    /** KB/phút trên phần cửa sổ sau phút khởi động. */
    private fun kbPerMin(w: Win, written: List<Pair<Long, String>>): Double {
        val start = ms(w.lines.first()) + 60_000L
        val end = ms(w.lines.last())
        val bytes = written.filter { it.first >= start }.sumOf { (_, s) -> s.toByteArray(Charsets.UTF_8).size + 1 }
        return bytes / 1024.0 / ((end - start) / 60_000.0)
    }

    @Test
    fun `phat lai log that - dinh giam it nhat 40 phan tram moi cua so`() {
        val ws = windows()
        assertTrue(ws.size == 4, "fixture phải có 4 cửa sổ, thấy ${ws.size}")
        val rows = ws.map { w -> Triple(kbPerMin(w, before(w)), kbPerMin(w, after(w, withDemotion = false)), kbPerMin(w, after(w, withDemotion = true))) }
        rows.forEachIndexed { i, (b, gateOnly, a) ->
            println("LOG-VOLUME W${i + 1} ${ws[i].lines.first().substring(0, 11)}: trước %.1f KB/phút · cổng %.1f · cổng+hạ-mức-nav %.1f (%d dòng nguồn)"
                .format(b, gateOnly, a, ws[i].lines.size))
        }
        ws.forEachIndexed { i, w ->
            val start = ms(w.lines.first()) + 60_000L
            val top = after(w, withDemotion = true).filter { it.first >= start }.flatMap { it.second.split('\n') }
                .groupBy { it.substring(SessionLogGate.SEVERITY_COL, it.indexOf('(', SessionLogGate.SEVERITY_COL)).trim() }
                .mapValues { (_, v) -> v.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 } }
                .entries.sortedByDescending { it.value }.take(8)
                .joinToString(" · ") { "%s %.1f".format(it.key, it.value / 1024.0) }
            println("LOG-VOLUME W${i + 1} sau, KB theo tag: $top")
        }
        println("LOG-VOLUME trung bình 4 đỉnh: trước %.1f · sau %.1f KB/phút".format(rows.sumOf { it.first } / ws.size, rows.sumOf { it.third } / ws.size))
        rows.forEachIndexed { i, (b, _, a) ->
            // Đỉnh thật = ≥ 2× mục tiêu K5 (< 20 KB/phút, LOG-41KB) theo luật cũ, sau phút khởi động.
            assertTrue(b >= 40.0, "cửa sổ W${i + 1} phải là đỉnh thật (≥ 40 KB/phút theo luật cũ), thấy %.1f".format(b))
            assertTrue(a <= 0.6 * b, "W${i + 1}: sau %.1f phải ≤ 60 %% của trước %.1f".format(a, b))
        }
    }

    /** Mọi câu chữ KHÁC NHAU ở mức I/W/E/F/A (ctl, HAL event, xi-nhan, lỗi…) xuất hiện ít nhất một lần ở đầu ra. */
    @Test
    fun `khong mat cau chu nao o muc I W E F A`() {
        windows().forEach { w ->
            val out = after(w, withDemotion = true).flatMap { it.second.split('\n') }
                .map { it.substring(SessionLogGate.SEVERITY_COL).substringBefore("   [") }.toHashSet()
            val need = w.lines.map(::demote).filter { it[SessionLogGate.SEVERITY_COL] in "IWEFA" }
                .map { it.substring(SessionLogGate.SEVERITY_COL).substringBefore("   [") }.toHashSet()
            val missing = need - out
            assertTrue(missing.isEmpty(), "mất ${missing.size} câu: ${missing.take(3)}")
        }
    }

    /**
     * Mọi lần ĐỔI TRẠNG THÁI ở mức D (khác dòng trước cùng khuôn ở chỗ không phải số đo: số một chữ số, id, chữ) mà
     * chuỗi ấy chưa xuất hiện trong 10 s qua đều được ghi — tức không bao giờ tệ hơn luật nguyên văn 10 s của 3.1.9
     * (thang ghế OFF=1/1=2/2=3, cửa, đèn…). Lật A ↔ B trong 10 s có bài riêng ở `SessionLogGateTest`.
     */
    @Test
    fun `moi doi trang thai muc D deu duoc ghi`() {
        windows().forEach { w ->
            val written = after(w, withDemotion = true).flatMap { (t, s) -> s.split('\n').map { t to it.substringBefore("   [") } }.toHashSet()
            val lastMeasure = HashMap<String, String>()
            val seenAt = HashMap<String, Long>()
            var changes = 0
            w.lines.map(::demote).filter { it[SessionLogGate.SEVERITY_COL] in "DV" }.forEach { l ->
                val body = l.substring(SessionLogGate.SEVERITY_COL)
                val tpl = SessionLogGate.template(body)
                val m = SessionLogGate.measure(body)
                val prev = lastMeasure.put(tpl, m)
                val seen = seenAt.put(body, ms(l))
                val recent = seen != null && ms(l) - seen < SessionLogGate.REPEAT_MS
                if (prev != null && prev != m && !recent) {
                    changes++
                    assertTrue((ms(l) to l) in written, "đổi trạng thái bị nuốt: $l")
                }
            }
            assertTrue(changes > 0, "fixture phải có đổi trạng thái để bài này có nghĩa")
        }
    }
}
