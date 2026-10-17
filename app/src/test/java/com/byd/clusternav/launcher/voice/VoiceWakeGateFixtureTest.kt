package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ═══ WAKE-INTERMITTENT-1017 — hai cổng trước ASR của "Hey Kachi" trên ĐẶC TRƯNG ĐÃ GHI ═════════════════════════════════════
 *
 * [ĐO xe 17/10] micro phiên lệnh rms 0,013–0,029 trên cả khúc ⇒ câu gọi nói nhẹ nằm quanh cổng RMS cũ 0,010, và cửa sổ bị cổng ấy
 * bỏ thì không để lại dấu gì. Fixture `voice/wake-gate-features-2026-10-17.tsv` (sinh bằng `scripts/voice/wake-harness/gate-sweep.py
 * DUMP=`, Silero asset thật): 20 câu gọi giọng 0,007 cabin yên + 20 câu gọi giọng 0,03 dưới nhạc có lời −3 dB. Mỗi tệp có mọi ô
 * giải mã (RMS cửa sổ, vị trí) + xác suất VAD từng cửa sổ 512 mẫu theo đúng thứ tự engine nhận.
 *
 * Bài phát lại xác suất qua ĐÚNG [VoiceWakeSpeechGate] (ngưỡng/hold mặc định) và so RMS với ĐÚNG [VoiceWakeAsr.RMS_GATE]: mỗi câu
 * gọi phải có ít nhất một ô phủ ≥ 60 % câu mà qua CẢ HAI cổng (= tới được ASR).
 * Red-proof: `RMS_GATE` về 0,010 ⇒ 20 tệp giọng nhỏ đỏ; ngưỡng VAD 0,95 ⇒ tệp nhạc có lời đỏ.
 */
class VoiceWakeGateFixtureTest {

    private class Slot(val fedEnd: Int, val from: Int, val to: Int, val rms: Double)

    private fun reaches(line: String): Boolean {
        val c = line.split("\t")
        val (a, b) = c[1].split(":").map(String::toInt)
        val slots = c[2].split(";").filter { it.isNotBlank() }.map { s ->
            val f = s.split(":"); Slot(f[0].toInt(), f[1].toInt(), f[2].toInt(), f[3].toDouble())
        }
        val vads = c[3].split(";").filter { it.isNotBlank() }.map { v -> v.split(":").let { it[0].toInt() to it[1].toFloat() } }
        val gate = VoiceWakeSpeechGate()
        val win = FloatArray(VoiceVadTrim.WINDOW_SIZE)
        var vi = 0
        val len = b - a
        return slots.any { s ->
            while (vi < vads.size && vads[vi].first <= s.fedEnd) { val p = vads[vi++].second; gate.accept(win, win.size) { p } }
            val cover = maxOf(0, minOf(b, s.to) - maxOf(a, s.from))
            cover >= 0.6 * len && s.rms >= VoiceWakeAsr.RMS_GATE && gate.open()
        }
    }

    @Test fun `cau goi giong nho va duoi nhac co loi deu toi duoc ASR`() {
        val rows = SourceRoots.text("src/test/resources/voice/wake-gate-features-2026-10-17.tsv")
            .lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(40, rows.size)
        val lost = rows.filterNot(::reaches).map { it.substringBefore("\t") }
        assertEquals(emptyList<String>(), lost, "câu gọi bị cổng trước ASR bỏ (RMS_GATE=${VoiceWakeAsr.RMS_GATE})")
    }
}
