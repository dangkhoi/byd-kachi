package com.byd.clusternav.launcher

/**
 * FIX286 · SR6 — nơi nhận một dòng nhật ký `ctl` (tầng ghi `HalBindingTable` gọi, `:app` lo tệp + logcat).
 *
 * Mặc định [NONE] = không ghi gì và **không đọc thêm lượt HAL nào** cho nhật ký (`HalBindingTable` chỉ đọc
 * trước/sau khi có người nghe) — nên mọi bài kiểm và mọi chỗ dựng cũ đi đúng số lượt gọi HAL như 2.85.
 */
fun interface CtlJournal {
    fun write(line: String)

    companion object {
        val NONE: CtlJournal = CtlJournal { }
    }
}

/**
 * ═══ FIX286 · SR6 — ĐỊNH DẠNG dòng nhật ký mỗi lệnh ghi xe (thuần, `:core`) ═══════════════════════════════════
 *
 * ## Vì sao có (CLAUDE.md §11 + §14)
 * Bản vá cửa sổ trời 2.86 ship **trước** một phép đo shell thô trên xe có nóc (owner duyệt *"OTA cho anh em thử"*).
 * Cái giá của việc nhảy tầng 1 là: lượt thử ấy PHẢI tự sinh ra bằng chứng. Tới 2.85 một lệnh ghi từ ô nút **không để
 * lại gì** ngoài một dòng W khi lỗi (`BydHalGateway.kt:95-97`, tiết chế) — rc hợp lệ hay sentinel, xe có đổi trạng
 * thái không, đều không ai biết. Ba dòng dưới đây trả lời đúng ba câu đó, grep được trong `usage-*.log` và đọc được
 * qua cầu `ctllog` mà không cần `run-as`.
 *
 * ## Ba loại dòng (khoá đầu dòng cố định — grep được)
 *  • [TAG_WRITE] `ctl #<seq> id=… m=<route> args=[…] rc=<rc thô|-> before=<đọc trước|-> why=<cmd|release#N>
 *    cancel=<0|1> release=<giá trị@ms|-> [gate=absent:<cfg>]` — MỘT dòng cho mỗi lượt gửi xuống HAL (kể cả lượt nhả)
 *    và cho mỗi lượt bị cổng có-nóc từ chối (`rc=-`, `gate=`);
 *  • [TAG_AFTER] `ctl-after #<seq> id=… after=<đọc sau 3 s|->` — chỉ cho lệnh có rc hợp lệ của nút đọc được;
 *  • [TAG_PROBE] `ctl-probe id=… <getter>=<giá trị|-> …` — MỘT lần mỗi tiến trình cho nút khai [ControlDef.probe].
 *
 * `rc` là **rc thô**: `ok()` của `CarControlAdapter` chỉ loại hai sentinel, mọi rc âm khác vẫn tính là "ăn" — phân biệt
 * hai thứ đó là việc của người đọc nhật ký, không phải của dòng nhật ký.
 */
object CtlWriteJournal {

    const val TAG_WRITE = "ctl"
    const val TAG_AFTER = "ctl-after"
    const val TAG_PROBE = "ctl-probe"

    /** Lượt gửi do một lệnh (ô · giọng nói · gói · cầu) — khác lượt nhả tự động. */
    const val WHY_CMD = "cmd"

    /** Tiền tố `why` của lượt nhả: `release#<seq của lệnh đã hẹn nó>`. */
    const val WHY_RELEASE = "release#"

    /** Lượt đọc-sau chờ ngần này sau lệnh: đủ cho một tấm kính/nóc chạy một quãng rõ (nóc mở hết mất vài giây). */
    const val AFTER_MS = 3_000L

    /** Trần dòng của tệp vòng `ctl-writes.log` — cùng trần với nhật ký gắn Hỗ trợ (cầu đọc kẹp theo số này). */
    const val MAX_LINES = 200

    /** Giá trị vắng trong dòng (đọc không được / không có) — một ký tự, không phải chữ "null". */
    private const val NONE_MARK = "-"

    private fun v(x: Any?): String = x?.toString() ?: NONE_MARK

    /**
     * Dòng [TAG_WRITE]. [gateAbsent] khác `null` ⇔ cổng có-nóc đã từ chối (khi đó [rc] luôn `null`). [before] là số
     * đọc được, `null` (⇒ `-`), hoặc `"!<Lỗi>"` khi lượt đọc chẩn đoán ném (xem `ControlWriteFlow.quietRead`).
     */
    fun writeLine(
        seq: Long,
        id: String,
        route: String,
        args: IntArray,
        rc: Long?,
        before: Any?,
        why: String,
        cancelledRelease: Boolean,
        release: WriteRelease?,
        gateAbsent: Int? = null,
    ): String = buildString {
        append(TAG_WRITE).append(" #").append(seq)
        append(" id=").append(id)
        append(" m=").append(route)
        append(" args=").append(args.joinToString(",", "[", "]"))
        append(" rc=").append(v(rc))
        append(" before=").append(v(before))
        append(" why=").append(why)
        append(" cancel=").append(if (cancelledRelease) 1 else 0)
        append(" release=").append(release?.let { "${it.value}@${it.delayMs}ms" } ?: NONE_MARK)
        gateAbsent?.let { append(" gate=absent:").append(it) }
    }

    /** Dòng [TAG_AFTER] — giá trị đọc lại [AFTER_MS] sau lệnh [seq]. */
    fun afterLine(seq: Long, id: String, after: Any?): String = "$TAG_AFTER #$seq id=$id after=${v(after)}"

    /** Dòng [TAG_PROBE] — các getter chẩn đoán theo đúng thứ tự khai ở registry. */
    fun probeLine(id: String, values: List<Pair<String, Int?>>): String =
        "$TAG_PROBE id=$id " + values.joinToString(" ") { (k, x) -> "$k=${v(x)}" }
}
