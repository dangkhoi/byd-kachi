package com.byd.clusternav.voicekey

import com.byd.clusternav.launcher.LogLineThrottle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * L7 · KEY-SOURCE-SPLIT tầng 1 — vòng đệm RAM + dòng nhật ký `KachiKey`.
 *
 * Khoá hai bài học:
 *  1. Hộp học phím phải tìm ĐÚNG lần bấm vừa học (không phải lần bấm sau đó của một nút khác).
 *  2. [ĐO xe 17/09, fixture `diagnostics/keysrc-0917/usage-292-burst.txt` chép nguyên văn từ
 *     `perf-oncar-2026-09-26/kachi-logs/usage-1789605471918.log:7307-7320`] một lượt xoay núm ra nhiều DOWN cách nhau
 *     vài ms, và dòng log Y HỆT nhau bị `chatty` của logd gộp (*"identical 2 lines"*) — mất đúng dữ liệu cần đếm. Mỗi
 *     dòng `KachiKey` vì thế phải KHÁC nhau (có `t=` + `seq=`), qua được cả chatty lẫn `LogLineThrottle` của KachiLog.
 */
class KeySourceJournalTest {

    private val spec = KeySourceProbes.AUDIO_VOLUME_CTRL_MODE

    private fun sample(code: Int = 291, t: Long = 1_000, scan: Int = 115) = KeySample(
        keyCode = code, action = 0, downTime = t, eventTime = t, scanCode = scan, deviceId = 7,
        source = 0x101, flags = 0x8, repeatCount = 0,
    )

    private val simulateKeys = KeyDeviceInfo("simulate-keys", "a1b2c3d4", isVirtual = false, vendorId = 0, productId = 1)

    // ── Vòng đệm ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `vong dem giu dung N lan bam gan nhat, cu den moi`() {
        val j = KeySourceJournal(capacity = 3)
        (1..5).forEach { j.begin(sample(t = it.toLong()), learned = false) }
        assertEquals(listOf(3L, 4L, 5L), j.snapshotForTest().map { it.sample.eventTime })
        assertEquals(listOf(3L, 4L, 5L), j.snapshotForTest().map { it.seq })
    }

    @Test
    fun `mac dinh 50 dong va khong nhan dung luong 0`() {
        assertEquals(50, KeySourceJournal().capacity)
        assertThrows<IllegalArgumentException> { KeySourceJournal(0) }
    }

    @Test
    fun `complete dien phan do vao dung dong, dong bi day ra van tra ve de ghi log`() {
        val j = KeySourceJournal(capacity = 2)
        val s1 = sample(t = 1)
        val seq1 = j.begin(s1, learned = false)
        assertNull(j.snapshotForTest().single().reading, "vừa begin ⇒ đang đo")
        val r = KeySourceReading(spec, value = 2, readMs = 1, ageMs = 4)
        val done = j.complete(seq1, simulateKeys, r, s1)
        assertEquals(r, done.reading)
        assertEquals(simulateKeys, j.snapshotForTest().single().device)
        // Hai lần bấm mới đẩy dòng cũ ra trước khi đo xong.
        val s2 = sample(t = 2); val seq2 = j.begin(s2, false); j.begin(sample(t = 3), false); j.begin(sample(t = 4), false)
        val evicted = j.complete(seq2, null, KeySourceReading.NOT_MEASURED, s2)
        assertEquals(seq2, evicted.seq)
        assertEquals(s2, evicted.sample)
        assertTrue(j.snapshotForTest().none { it.seq == seq2 })
    }

    @Test
    fun `hop hoc phim tim dung lan bam DUOC HOC, khong phai lan bam sau`() {
        val j = KeySourceJournal()
        j.begin(sample(code = 291, t = 1), learned = false)
        val learnedSeq = j.begin(sample(code = 292, t = 2), learned = true)
        j.begin(sample(code = 292, t = 3), learned = false)   // nấc núm tiếp theo — KHÔNG phải lần học
        j.begin(sample(code = 291, t = 4), learned = true)    // lần học của mã khác
        assertEquals(learnedSeq, j.lastLearned(292)?.seq)
        assertNull(j.lastLearned(328))
    }

    @Test
    fun `hai luong ghi dong thoi khong mat dong nao`() {
        val j = KeySourceJournal(capacity = 1_000)
        val threads = (0 until 4).map { k ->
            Thread { repeat(200) { i -> val s = sample(t = (k * 1_000 + i).toLong()); j.complete(j.begin(s, false), null, KeySourceReading.NOT_MEASURED, s) } }
        }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        val snap = j.snapshotForTest()
        assertEquals(800, snap.size)
        assertEquals(800, snap.map { it.seq }.toSet().size)
        assertTrue(snap.all { it.reading != null })
    }

    // ── Dòng nhật ký ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dong KachiKey day du chu ky + nhan nguon + do tre`() {
        val e = KeySourceEntry(12, sample(t = 123_456), learned = false, simulateKeys, KeySourceReading(spec, 1, readMs = 3, ageMs = 5))
        assertEquals(
            "down k=291 scan=115 dev=\"simulate-keys\"#7 virt=0 vp=0000:0001 desc=a1b2c3d4 src=0x101 fl=0x8 rep=0 " +
                "t=123456 tag=AUDIO_VOLUME_CTRL_MODE=1 (read 3ms, +5ms) seq=12",
            KeySourceLog.line(e),
        )
        assertEquals("KachiKey", KeySourceLog.TAG)
    }

    @Test
    fun `dong KachiKey khi hong, khong do, chua xong, va lan hoc`() {
        val fail = KeySourceEntry(1, sample(), false, null, KeySourceReading.failed(spec, KeySourceFailure.NO_DEVICE, readMs = 0))
        assertTrue(KeySourceLog.line(fail).contains(" dev=?#7 src=0x101"), "chưa tra được thiết bị ⇒ vẫn có deviceId")
        assertTrue(KeySourceLog.line(fail).contains(" tag=AUDIO_VOLUME_CTRL_MODE=!no_device (read 0ms"))
        val denied = KeySourceReading.failed(spec, KeySourceFailure.READ_ERROR, errorClass = "SecurityException")
        assertEquals("AUDIO_VOLUME_CTRL_MODE=!read_error:SecurityException", KeySourceLog.tag(denied))
        val na = KeySourceEntry(2, sample(code = 328), false, simulateKeys, KeySourceReading.NOT_MEASURED)
        assertTrue(KeySourceLog.line(na).contains(" tag=- seq=2"), "phím ngoài bảng: không có phần (read …)")
        val pending = KeySourceEntry(3, sample(), learned = true)
        assertTrue(KeySourceLog.line(pending).endsWith(" tag=? seq=3 learn"))
    }

    @Test
    fun `fixture xe 17-09 - luot xoay num ra nhieu DOWN sat nhau, dong cu bi chatty gop`() {
        val fixture = javaClass.getResourceAsStream("/diagnostics/keysrc-0917/usage-292-burst.txt")!!.bufferedReader().readLines()
        assertTrue(fixture.any { it.contains("I/chatty") && it.contains("identical 2 lines") }, "fixture phải giữ dấu chatty gộp dòng")
        val downs = fixture.filter { "onKeyEvent DOWN keycode=292" in it }
        // Mốc thời gian thật (ms trong ngày) của từng DOWN còn sống sót trong log.
        val times = downs.map { l ->
            val (h, m, sms) = l.substring(6, 18).split(':')
            val (s, ms) = sms.split('.')
            ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms.toLong()
        }
        assertEquals(11, times.size)
        assertTrue(times.zipWithNext().any { (a, b) -> b - a <= 4 }, "có cặp DOWN cách nhau ≤ 4 ms (núm)")

        val j = KeySourceJournal()
        val lines = times.map { t ->
            val s = sample(code = 292, t = t, scan = 114)
            KeySourceLog.line(j.complete(j.begin(s, false), simulateKeys, KeySourceReading(spec, 1, readMs = 2, ageMs = 3), s))
        }
        assertEquals(lines.size, lines.toSet().size, "mỗi dòng KachiKey phải KHÁC nhau ⇒ chatty không gộp được")
        val throttle = LogLineThrottle()
        lines.forEachIndexed { i, l ->
            assertNotNull(throttle.suppressedBefore(l, nowMs = times[i]), "KachiLog không được bỏ dòng ${i + 1}")
        }
        // Đối chứng: dòng cũ (chỉ keycode) thì y hệt nhau ⇒ bị tiết chế.
        val old = LogLineThrottle()
        val kept = downs.indices.count { i -> old.suppressedBefore("I/NavAccess: onKeyEvent DOWN keycode=292", times[i]) != null }
        assertEquals(1, kept)
    }
}
