package com.byd.clusternav.voicekey

/**
 * Một dòng nhật ký phím: chữ ký ([sample]) ghi NGAY trên luồng nhận phím, phần đo ([device] · [reading]) điền sau ở
 * luồng đo. `reading == null` ⇔ lượt đo chưa xong.
 *
 * @property learned lần bấm này là phím vừa được HỌC (Cài đặt › Học phím mới) — hộp đặt tên tìm đúng dòng này.
 */
data class KeySourceEntry(
    val seq: Long,
    val sample: KeySample,
    val learned: Boolean,
    val device: KeyDeviceInfo? = null,
    val reading: KeySourceReading? = null,
)

/**
 * ═══ L7 · VÒNG ĐỆM trong RAM — [capacity] lần bấm DOWN gần nhất ═════════════════════════════════════════════════
 *
 * Hai luồng chạm: luồng nhận phím gọi [begin] (O(1), không I/O), luồng đo gọi [complete]. Đồng bộ bằng khoá của chính
 * đối tượng — mỗi thao tác chỉ vài phép gán, không giữ khoá qua I/O.
 *
 * Không bền (RAM) có chủ ý: bản bền là dòng `KachiKey` trong `usage-*.log` ([KeySourceLog.line]). Vòng đệm chỉ để màn
 * hình (hộp học phím) đọc lại số đo của đúng lần bấm vừa rồi.
 */
class KeySourceJournal(val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity phải > 0" }
    }

    private val ring = ArrayDeque<KeySourceEntry>(capacity)
    private var nextSeq = 1L

    /** Ghi chữ ký một lần bấm; trả số thứ tự để [complete] sau. */
    @Synchronized
    fun begin(sample: KeySample, learned: Boolean): Long {
        val seq = nextSeq++
        if (ring.size == capacity) ring.removeFirst()
        ring.addLast(KeySourceEntry(seq, sample, learned))
        return seq
    }

    /**
     * Điền phần đo cho dòng [seq]. Dòng đã bị đẩy khỏi vòng (≥ [capacity] lần bấm mới chen vào trước khi đo xong) vẫn
     * được dựng lại và TRẢ VỀ để chỗ gọi ghi log — chỉ không nằm lại trong vòng đệm.
     */
    @Synchronized
    fun complete(seq: Long, device: KeyDeviceInfo?, reading: KeySourceReading, fallbackSample: KeySample): KeySourceEntry {
        val i = ring.indexOfFirst { it.seq == seq }
        if (i < 0) return KeySourceEntry(seq, fallbackSample, learned = false, device = device, reading = reading)
        val done = ring[i].copy(device = device, reading = reading)
        ring[i] = done
        return done
    }

    /** Test-only: bản sao, cũ → mới (cùng khuôn `BydHal.resetFeatureIdCacheForTest` — không phải API cho tầng app). */
    @Synchronized
    internal fun snapshotForTest(): List<KeySourceEntry> = ring.toList()

    /** Dòng HỌC phím gần nhất của [keyCode] (hộp đặt tên đọc), hoặc `null`. */
    @Synchronized
    fun lastLearned(keyCode: Int): KeySourceEntry? = ring.lastOrNull { it.learned && it.sample.keyCode == keyCode }

    companion object {
        /** "~50 lần bấm gần nhất" — đủ phủ một lượt thử của anh em (vài lần vô-lăng + vài nấc núm). */
        const val DEFAULT_CAPACITY = 50
    }
}

/**
 * ═══ Dòng nhật ký `KachiKey` — MỘT dòng mỗi DOWN, nằm trong `usage-*.log` (KachiLog chụp logcat theo pid) ══════════
 *
 * Khuôn (ASCII, grep được):
 * ```
 * down k=291 scan=115 dev="simulate-keys"#7 virt=1 vp=0000:0000 desc=<descriptor> src=0x101 fl=0x8 rep=0 t=123456 tag=AUDIO_VOLUME_CTRL_MODE=1 (read 3ms, +5ms) seq=12
 * ```
 * `t=` và `seq=` làm mỗi dòng KHÁC nhau: `KachiLog.throttled` bỏ dòng TRÙNG nguyên văn trong cửa sổ tiết chế, mà một
 * lượt xoay núm 4 nấc ra 4 dòng y hệt nếu không có hai trường này — đúng phần dữ liệu cần đếm.
 */
object KeySourceLog {

    /** Tag logcat của dòng nhật ký (`I/KachiKey(pid): down k=…`). */
    const val TAG = "KachiKey"

    fun line(e: KeySourceEntry): String {
        val s = e.sample
        val d = e.device
        val b = StringBuilder(160)
        b.append("down k=").append(s.keyCode).append(" scan=").append(s.scanCode)
        b.append(" dev=").append(deviceLabel(e))
        if (d != null) {
            b.append(" virt=").append(if (d.isVirtual) 1 else 0)
            b.append(" vp=").append(hex4(d.vendorId)).append(':').append(hex4(d.productId))
            b.append(" desc=").append(d.descriptor.ifEmpty { "-" })
        }
        b.append(" src=0x").append(Integer.toHexString(s.source))
        b.append(" fl=0x").append(Integer.toHexString(s.flags))
        b.append(" rep=").append(s.repeatCount)
        b.append(" t=").append(s.eventTime)
        b.append(" tag=").append(tag(e.reading))
        // 2.87 · SOÁT vòng 1 · P3: chỉ in khi CÓ lượt đọc (`readMs ≥ 0`) — `busy`/`not_running` không đọc gì, in
        // "(read 0ms" là bịa một phép đo; tuổi `+Nms` chỉ có khi lượt đọc xong (quá hạn thì không có, không in "+-1ms").
        e.reading?.takeIf { it.probe != null && it.readMs >= 0 }?.let { r ->
            b.append(" (read ").append(r.readMs).append("ms")
            if (r.ageMs >= 0) b.append(", +").append(r.ageMs).append("ms")
            b.append(')')
        }
        b.append(" seq=").append(e.seq)
        if (e.learned) b.append(" learn")
        return b.toString()
    }

    /** `"simulate-keys"#7`, hoặc `?#7` khi chưa tra được thiết bị. */
    fun deviceLabel(e: KeySourceEntry): String {
        val name = e.device?.name?.takeIf { it.isNotEmpty() }?.let { "\"$it\"" } ?: "?"
        return "$name#${e.sample.deviceId}"
    }

    /**
     * Phần nhãn nguồn: `AUDIO_VOLUME_CTRL_MODE=1` · `AUDIO_VOLUME_CTRL_MODE=!no_device` ·
     * `AUDIO_VOLUME_CTRL_MODE=!read_error:SecurityException` · `-` (phím không đo) · `?` (chưa đo xong).
     */
    fun tag(reading: KeySourceReading?): String {
        reading ?: return "?"
        val probe = reading.probe ?: return "-"
        return probe.featureName + "=" + (reading.value?.toString() ?: ("!" + reason(reading)))
    }

    /** Mã lý do hụt: `no_device` · `read_error:SecurityException` · `bad_value:<thô>`; rỗng khi đọc được. */
    fun reason(reading: KeySourceReading): String {
        val f = reading.failure ?: return if (reading.value == null) KeySourceFailure.EMPTY.code else ""
        return reading.errorClass?.takeIf { it.isNotBlank() }?.let { "${f.code}:$it" } ?: f.code
    }

    private fun hex4(v: Int): String = Integer.toHexString(v).padStart(4, '0')
}
