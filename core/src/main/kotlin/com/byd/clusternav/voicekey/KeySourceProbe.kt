package com.byd.clusternav.voicekey

import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.launcher.HalFeatureRead
import com.byd.clusternav.launcher.HalGateway

/**
 * ═══ L7 · KEY-SOURCE-SPLIT tầng 1 — ĐẦU DÒ "phím này đến từ NÚT NÀO", khai bằng DỮ LIỆU ══════════════════════════
 *
 * ## Vì sao cần đầu dò ngoài KeyEvent — mức bằng chứng
 *  - [ĐO tĩnh firmware 2602030, `auto.default.so`] cả hai nút âm lượng đổ vào CÙNG thiết bị ảo `simulate-keys` với
 *    CÙNG scancode 115/114 (`FunctionTable::keycode` vô-lăng, `FunctionTable::boardkeycode` núm yên ngựa) ⇒ KeyEvent
 *    gần như chắc giống hệt [SUY mạnh — tầng 1 đo lại bằng [KeySample]].
 *  - [ĐO tĩnh] NGAY TRƯỚC khi ghi phím, HAL đặt feature `AUDIO_VOLUME_CTRL_MODE` (device `BYDAutoAudioDevice`) =
 *    **1** (núm/bảng điều khiển giữa) hoặc **2** (vô-lăng). Bảng feature của xe owner có feature này [ĐO 16/09].
 *  - Đọc nó từ trong Kachi: **[CHƯA BIẾT]** — đó chính là thứ tầng 1 đo.
 *
 * ## Vì sao là DỮ LIỆU (CLAUDE.md §7)
 * Không `if (xe == …)`, không hằng số feature-id: [ĐO nguồn fw] `BYDAutoFeatureIds` gán nhiều hằng theo `isCanFD`,
 * nên id phân giải theo TÊN trên chính xe đang chạy ([HalGateway.featureIdByName]). Bảng mã phím áp dụng, giá trị hợp
 * lệ và nghĩa của từng giá trị đều nằm trong [KeySourceProbeSpec] — xe đời khác có đầu dò khác thì THÊM một dòng vào
 * [KeySourceProbes.ALL], không sửa code đọc.
 *
 * ## Mã bền [code] (2.88 · KEY-SOURCE-SPLIT tầng 2, spec `kachi-288-key-source-split` §4.2)
 * Dòng gán / nút tự học lưu nguồn bằng [code] (`"knob"` / `"wheel"`), KHÔNG bằng tên enum: đổi tên hằng trong code
 * không được làm mất cấu hình đã lưu trên xe. [fromCode] trả `null` cho mã lạ (bản tương lai) — tầng lưu trữ BỎ dòng đó
 * chứ không hạ thành dòng không nguồn (R5: hạ xuống thì nó bắt luôn nút còn lại).
 */
enum class KeySourceKind(val code: String) {
    CONSOLE_KNOB("knob"),
    STEERING_WHEEL("wheel"),
    ;

    companion object {
        /** Mã bền → nguồn; `null` khi [code] rỗng/lạ. */
        fun fromCode(code: String?): KeySourceKind? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Một đầu dò nguồn phím.
 *
 * @property deviceClass tên lớp device BYDAuto (FQN dựng bằng [HalBindingTable.deviceFqn]).
 * @property featureName tên hằng `BYDAutoFeatureIds` — phân giải ra số TRÊN XE.
 * @property values giá trị đọc được → nút vật lý. Giá trị ngoài bảng ⇒ [KeySourceVerdict.UnknownValue] (hiện nguyên số).
 * @property keyCodes mã phím mà đầu dò này áp dụng (chỉ những phím này mới tốn một lượt đọc HAL).
 * @property budgetMs trần thời gian một lượt đọc; quá trần ⇒ [KeySourceFailure.TIMEOUT], không chờ thêm.
 */
data class KeySourceProbeSpec(
    val deviceClass: String,
    val featureName: String,
    val values: Map<Int, KeySourceKind>,
    val keyCodes: Set<Int>,
    val budgetMs: Long = KeySourceProbes.DEFAULT_BUDGET_MS,
)

/**
 * Lý do một lượt đo KHÔNG ra giá trị — mã ASCII cố định để đọc được trên ảnh chụp và grep được trong `usage-*.log`.
 */
enum class KeySourceFailure(val code: String) {
    /** Máy không có framework BYDAuto (máy ảo / ngoài xe). */
    NO_FRAMEWORK("no_framework"),
    /** Framework có, nhưng KHÔNG có hằng [KeySourceProbeSpec.featureName] trên xe này. */
    NO_FEATURE("no_feature"),
    /** `getInstance` của device trả null / ném. */
    NO_DEVICE("no_device"),
    /** `get(int[], Class)` ném (vd SecurityException "no permission") — kèm lớp ngoại lệ. */
    READ_ERROR("read_error"),
    /** HAL trả rỗng / sentinel của `BYDAutoEventValue`. */
    EMPTY("empty"),
    /** HAL trả mã "feature không provision" (`-2147482648` / `-2147482645`, `AbsBYDAutoDevice.get` :320-336). */
    NOT_PROVISIONED("not_provisioned"),
    /** Có chuỗi nhưng không rút ra số. */
    BAD_VALUE("bad_value"),
    /** Quá [KeySourceProbeSpec.budgetMs]. */
    TIMEOUT("timeout"),
    /** Lượt đọc trước vẫn còn treo ⇒ KHÔNG chồng thêm lượt mới (chặn bão thử lại). */
    BUSY("busy"),
    /** Bộ đo không chạy (dịch vụ Hỗ trợ đang dừng). */
    NOT_RUNNING("not_running"),
}

/**
 * Kết quả đo cho MỘT lần bấm.
 *
 * @property probe đầu dò đã dùng; `null` = phím này không thuộc bảng nào (không đo, không tốn HAL).
 * @property readMs thời lượng lượt đọc HAL (−1 = không đọc).
 * @property ageMs từ `eventTime` của phím tới lúc đọc xong — đo xem nhãn có kịp "dính" vào đúng phím không (−1 = không đọc).
 */
data class KeySourceReading(
    val probe: KeySourceProbeSpec?,
    val value: Int? = null,
    val failure: KeySourceFailure? = null,
    val errorClass: String? = null,
    val readMs: Long = -1,
    val ageMs: Long = -1,
) {
    companion object {
        /** Phím không thuộc đầu dò nào. */
        val NOT_MEASURED = KeySourceReading(probe = null)

        fun failed(probe: KeySourceProbeSpec, failure: KeySourceFailure, readMs: Long = -1, errorClass: String? = null) =
            KeySourceReading(probe, failure = failure, errorClass = errorClass, readMs = readMs)
    }
}

/** Kết luận để HIỆN (nhãn người đọc) — tầng app dịch sang chữ theo ngôn ngữ. */
sealed class KeySourceVerdict {
    /** Lượt đọc chưa xong. */
    object Pending : KeySourceVerdict()
    /** Phím không thuộc đầu dò nào. */
    object NotMeasured : KeySourceVerdict()
    data class Source(val kind: KeySourceKind, val value: Int) : KeySourceVerdict()
    data class UnknownValue(val value: Int) : KeySourceVerdict()
    data class Failed(val failure: KeySourceFailure, val errorClass: String?) : KeySourceVerdict()
}

object KeySourceProbes {

    /**
     * Trần một lượt đọc. Luồng nhận phím KHÔNG chờ trần này (đọc ở luồng riêng) — nó chỉ chặn một HAL treo làm dòng
     * nhật ký/hộp học phím chờ mãi. 250 ms ≪ hạn 500 ms của `KeyEventDispatcher` nên dù ai đó lỡ dời lượt đọc về
     * luồng phím thì phím vẫn không lọt.
     */
    const val DEFAULT_BUDGET_MS = 250L

    /**
     * [ĐO tĩnh fw 2602030] `AUDIO_VOLUME_CTRL_MODE` trên `BYDAutoAudioDevice` (device 1002): 1 = núm/bảng điều khiển
     * giữa (`boardkeycode`), 2 = vô-lăng (`keycode`). Mã phím: `KEYCODE_AUTO_VOLUME_UP/DOWN` 291/292 và bản nhấn-giữ
     * `_LP` 307/308 (`framework.jar` BYD `KeyEvent`). Chỉ hai giá trị 1/2 có nghĩa — đúng bộ lọc của chính framework
     * (`AbsBYDAutoAudioListener.onDataChanged` chỉ chuyển tiếp 1|2).
     */
    val AUDIO_VOLUME_CTRL_MODE = KeySourceProbeSpec(
        deviceClass = "BYDAutoAudioDevice",
        featureName = "AUDIO_VOLUME_CTRL_MODE",
        values = mapOf(1 to KeySourceKind.CONSOLE_KNOB, 2 to KeySourceKind.STEERING_WHEEL),
        keyCodes = setOf(291, 292, 307, 308),
    )

    /** Bảng đầu dò. Thêm đời xe ⇒ thêm dòng, không thêm nhánh. */
    val ALL: List<KeySourceProbeSpec> = listOf(AUDIO_VOLUME_CTRL_MODE)

    /** Đầu dò cho [keyCode], hoặc `null` (phím này không đo). */
    fun forKey(keyCode: Int, table: List<KeySourceProbeSpec> = ALL): KeySourceProbeSpec? =
        table.firstOrNull { keyCode in it.keyCodes }

    /**
     * MỘT lượt đọc đồng bộ — gọi trên luồng đo (KHÔNG trên luồng nhận phím). Thuần: mọi chạm xe đi qua [gateway],
     * đồng hồ truyền vào ⇒ kiểm off-device đủ mọi nhánh lỗi.
     *
     * @param clockMs đồng hồ cùng gốc với [KeySample.eventTime] (`SystemClock.uptimeMillis` ở tầng app).
     */
    fun read(spec: KeySourceProbeSpec, gateway: HalGateway, clockMs: () -> Long, keyEventTime: Long): KeySourceReading {
        val t0 = clockMs()
        fun done(value: Int?, failure: KeySourceFailure?, err: String? = null): KeySourceReading {
            val t1 = clockMs()
            return KeySourceReading(spec, value, failure, err, readMs = t1 - t0, ageMs = t1 - keyEventTime)
        }
        val id = gateway.featureIdByName(spec.featureName)
            ?: return done(null, if (gateway.featureMapAvailable()) KeySourceFailure.NO_FEATURE else KeySourceFailure.NO_FRAMEWORK)
        return when (val r = gateway.featureRead(HalBindingTable.deviceFqn(spec.deviceClass), id)) {
            is HalFeatureRead.Value -> {
                val v = HalBindingTable.coerceInt(r.raw)
                when {
                    v == null -> done(null, KeySourceFailure.BAD_VALUE, r.raw.take(MAX_RAW))
                    HalBindingTable.isSentinelRc(v.toLong()) -> done(null, KeySourceFailure.NOT_PROVISIONED)
                    else -> done(v, null)
                }
            }
            HalFeatureRead.NoDevice -> done(null, KeySourceFailure.NO_DEVICE)
            is HalFeatureRead.Failed -> done(null, KeySourceFailure.READ_ERROR, r.errorClass)
            HalFeatureRead.Empty -> done(null, KeySourceFailure.EMPTY)
        }
    }

    /** Kết luận hiển thị. `null` = lượt đọc chưa xong. */
    fun verdict(reading: KeySourceReading?): KeySourceVerdict {
        reading ?: return KeySourceVerdict.Pending
        val probe = reading.probe ?: return KeySourceVerdict.NotMeasured
        reading.failure?.let { return KeySourceVerdict.Failed(it, reading.errorClass) }
        val v = reading.value ?: return KeySourceVerdict.Failed(KeySourceFailure.EMPTY, null)
        return probe.values[v]?.let { KeySourceVerdict.Source(it, v) } ?: KeySourceVerdict.UnknownValue(v)
    }

    /** Chuỗi thô dài nhất được chép vào [KeySourceReading.errorClass] khi không rút ra số. */
    private const val MAX_RAW = 40
}
