package com.byd.clusternav.launcher

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * ═══ FIX286 · R-SR — NHỊP MỘT LỆNH GHI ở tầng `HalBindingTable.write` (thuần, `:core`) ═══════════════════════════
 *
 * Tách khỏi `HalBindingTable.kt` vì trần 500 dòng (CLAUDE.md §4.1) và theo **vai**: tệp kia *định tuyến* một lệnh
 * xuống đúng đường HAL; tệp này lo **những việc bao quanh** một lệnh mà 2.86 thêm vào — và cả bốn đều là DỮ LIỆU của
 * dòng registry, không nhánh nào rẽ theo mã nút (CLAUDE.md §7):
 *
 *  ① **cổng có-mặt** ([ControlDef.presence], SR4) — xe nói *"không có bộ phận này"* ⇒ từ chối, 0 lượt gọi HAL;
 *  ② **huỷ lượt nhả đang chờ** của cùng nút (OEM `removeMessages(0)`);
 *  ③ gửi lệnh (đường của `HalBindingTable`, truyền vào là [send]);
 *  ④ **hẹn nhả** ([ControlDef.release], SR2) — khi rc hợp lệ (khác `null`, khác hai sentinel), hoặc khi bước ② vừa
 *     huỷ lượt nhả của một lệnh hợp lệ trước đó (lệnh này hỏng thì yêu cầu cũ vẫn đang giữ trên bus); lệnh hỏng
 *     ĐẦU TIÊN thì không có gì để nhả;
 *  ⑤ **nhật ký bền** ([CtlJournal], SR6) — một dòng mỗi lượt gửi, đọc trước/sau 3 s cho nút đọc được, và ảnh chụp
 *     [ControlDef.probe] MỘT lần mỗi tiến trình.
 *
 * Thứ tự theo đúng app OEM (`SunRoofFragment.java:834-836`): huỷ → ghi → hẹn. Ảnh chụp chẩn đoán được HẸN trước cổng
 * (chạy trên luồng bộ hẹn, xem [probeOnce]): trên một chiếc xe bị cổng từ chối, dòng ảnh chụp chính là thứ giải thích
 * vì sao.
 *
 * ## Ranh giới (CLAUDE.md §4)
 * Lệnh HAL mới duy nhất là lượt nhả — cùng method/feature với lệnh vừa ghi, cùng nút, chỉ cho nút khai [WriteRelease].
 * Hoàn tác: lệnh ngược của chính nút (đóng = 0). Tiến trình chết trong 200 ms ⇒ không nhả — hậu quả [CHƯA BIẾT], app
 * OEM có đúng khe hở đó (`Handler` cũng chết theo tiến trình).
 *
 * Lượt đọc chẩn đoán (trước · sau · ảnh chụp) CHỈ chạy khi có người nghe ([journal] khác [CtlJournal.NONE]) ⇒ mọi
 * chỗ dựng không có nhật ký đi đúng số lượt HAL như 2.85.
 */
class ControlWriteFlow(
    private val releaser: WriteReleaseScheduler,
    private val journal: CtlJournal,
    /** Đọc MỘT getter named-method thành số (sentinel/không đọc được ⇒ `null`) — cho cổng có-mặt + ảnh chụp. */
    private val getterInt: (key: String, arg: Int?) -> Int?,
    /** Giá trị thật của nút qua đường đọc của nó (`HalBindingTable.readState`). */
    private val readState: (id: String) -> Int?,
    /** Gửi [IntArray] tham số cuối xuống đúng đường ghi của nút; `primary` là đường lùi cho đường một-số. */
    private val send: (def: ControlDef, args: IntArray, primary: Int) -> Long?,
    /** Nút có đường đọc không (`readPathOf != null`) — chỉ nút đọc được mới có `before`/`after`. */
    private val readable: (id: String) -> Boolean,
) {
    private val seq = AtomicLong(0)
    private val listening = journal !== CtlJournal.NONE

    /**
     * Cấu hình đã đọc ĐƯỢC và nói **CÓ** của cổng có-mặt (mỗi tiến trình một lần). Đọc hỏng, hoặc đọc ra mã "vắng",
     * KHÔNG cất ⇒ lệnh sau hỏi lại — xem [presenceAbsent].
     */
    private val presenceSeen = ConcurrentHashMap<String, Int>()

    /** Nút đã chụp [ControlDef.probe] trong tiến trình này. */
    private val probed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Khoá MỘT nhịp *"huỷ → gửi → hẹn"* của lệnh và thân lượt nhả — xem [releaseOwner]. */
    private val gate = Any()

    /**
     * Lượt nhả đang chờ của từng nút: mã nút → `seq` của lệnh đã hẹn nó. CHỈ đọc/ghi dưới [gate].
     *
     * [Senior review FIX286 Pass 1 · P2] `releaser.cancel` chỉ huỷ được việc CÒN trong hàng đợi. Lượt nhả đã rời hàng
     * đợi (luồng bộ hẹn đã nhận việc, sắp gọi HAL) mà lệnh mới tới đúng lúc ấy thì `cancel` trả `false`, lệnh mới gửi,
     * rồi 255 lên bus NGAY SAU nó — nhả luôn yêu cầu vừa gửi. App OEM không có ca này vì mọi thứ chạy trên MỘT
     * `Handler` (`SunRoofFragment.java:834-836`). Ở đây lệnh mới gỡ chủ dưới [gate], còn lượt nhả chỉ gửi khi dưới [gate]
     * nó VẪN là chủ ⇒ chỉ còn đúng hai thứ tự của OEM: `255 → lệnh mới → 255`, hoặc `lệnh mới → 255` (lượt nhả cũ bỏ,
     * lệnh mới tự hẹn lượt của nó vì `cancelled`).
     */
    private val releaseOwner = HashMap<String, Long>()

    /**
     * Giá trị cấu hình nếu nó nói *"xe này KHÔNG có bộ phận"* ([Presence.absentWhen]); `null` = có / không biết.
     * Fail-open có chủ ý: chỉ khi chính xe trả một mã "vắng" thì cổng mới đóng.
     *
     * [Senior review FIX286 Pass 3 · P3] Chỉ cất câu trả lời **CÓ**; mã "vắng" thì lệnh sau đọc lại. Bản luồng R-SR cất
     * mọi số đọc được ⇒ MỘT lượt đọc ra 0 (getter BYD trả mặc định trước khi khung báo cấu hình — [ĐOÁN], chưa đo lúc vừa nổ
     * máy) là khoá cửa sổ trời cả chuyến, chỉ tiến trình chết mới gỡ — đúng họ *"gate một đường bằng dữ liệu mà chỉ
     * chính đường ấy mới làm mới được"* (CLAUDE.md §3), và lượt thử OTA của anh em (OC-SR) sẽ ra một kết luận sai
     * ("xe tự báo không có nóc"). Giá của việc đọc lại: một getter mỗi lệnh — chỉ trên xe thật sự nói "vắng", nơi lệnh
     * vốn bị từ chối.
     */
    fun presenceAbsent(def: ControlDef): Int? {
        val p = def.presence ?: return null
        if (presenceSeen.containsKey(def.id)) return null
        val cfg = getterInt(p.getter, null) ?: return null
        if (cfg in p.absentWhen) return cfg
        presenceSeen[def.id] = cfg
        return null
    }

    /** Ghi [def] với tham số chính [primary] (đã quy ra [args]) — xem KDoc lớp về năm bước. */
    fun write(def: ControlDef, primary: Int, args: IntArray, route: String): Long? {
        val n = seq.incrementAndGet()
        probeOnce(def)
        presenceAbsent(def)?.let { cfg ->
            log { CtlWriteJournal.writeLine(n, def.id, route, args, null, null, CtlWriteJournal.WHY_CMD, false, null, cfg) }
            return null
        }
        val withReads = listening && readable(def.id)
        val before = if (withReads) quietRead(def.id) else null
        var cancelled = false
        var rel: WriteRelease? = null
        val declared = def.release
        // Nút không khai nhả ⇒ gửi thẳng, KHÔNG qua [gate]: khoá chỉ tuần tự hoá lệnh với lượt nhả của CHÍNH nút ấy,
        // không được biến mọi lệnh ghi xe (ô · giọng nói · gói) thành một hàng đợi chung như 2.85 không có.
        val rc = if (declared == null) send(def, args, primary) else synchronized(gate) {
            releaser.cancel(def.id)   // bỏ việc còn trong hàng đợi; lượt đã rời hàng đợi thì [releaseOwner] chặn
            cancelled = releaseOwner.remove(def.id) != null
            val sent = send(def, args, primary)
            val ok = sent != null && !HalBindingTable.isSentinelRc(sent)
            // Hẹn nhả khi lệnh này hợp lệ, HOẶC khi vừa huỷ lượt nhả của một lệnh hợp lệ trước đó: lệnh sau hỏng thì
            // yêu cầu cũ (vd 100) vẫn đang được giữ trên bus — không ai nhả nó nữa là để nóc "bị giữ" vô hạn. App OEM
            // nhả sau MỌI lần bấm (không xét kết quả), nên luật này không bao giờ nhả ÍT hơn OEM ở ca có lệnh hợp lệ.
            rel = declared.takeIf { ok || cancelled }?.also { r ->
                releaseOwner[def.id] = n
                releaser.schedule(def.id, r.delayMs) { release(def, r, n, route) }
            }
            sent
        }
        val valid = rc != null && !HalBindingTable.isSentinelRc(rc)
        log { CtlWriteJournal.writeLine(n, def.id, route, args, rc, before, CtlWriteJournal.WHY_CMD, cancelled, rel) }
        if (withReads && valid) {
            releaser.schedule("${def.id}$AFTER_KEY$n", CtlWriteJournal.AFTER_MS) {
                log { CtlWriteJournal.afterLine(n, def.id, quietRead(def.id)) }
            }
        }
        return rc
    }

    /** Lượt nhả hẹn từ lệnh [of]: cùng đường ghi, tham số = [WriteRelease.value]. */
    private fun release(def: ControlDef, r: WriteRelease, of: Long, route: String) {
        val args = intArrayOf(r.value)
        val rc = synchronized(gate) {
            // Một lệnh mới đã tới trong lúc lượt này rời hàng đợi ⇒ nó đã gỡ chủ (và tự hẹn lượt nhả của nó): BỎ — gửi
            // 255 lúc này là nhả ngay yêu cầu vừa gửi (xem [releaseOwner]).
            if (releaseOwner[def.id] != of) return
            releaseOwner.remove(def.id)
            send(def, args, r.value)
        }
        val n = seq.incrementAndGet()
        log { CtlWriteJournal.writeLine(n, def.id, route, args, rc, null, CtlWriteJournal.WHY_RELEASE + of, false, null) }
    }

    /**
     * Ảnh chụp [ControlDef.probe] chạy trên luồng của bộ hẹn (trễ 0), KHÔNG trên luồng đang ghi: [ĐO máy ảo 02/10]
     * lệnh giọng nói và cầu `ctl` ghi xe ngay trên luồng CHÍNH (logcat `KachiCtl` tid = pid), và bảy getter ×
     * ~23 ms/lượt ([ĐO xe 09-16]) là ~160 ms chặn luồng vẽ ở lệnh nóc đầu tiên mỗi tiến trình. Chạy song song với lệnh
     * vẫn chụp được trạng thái "trước": nóc chạy bằng mô-tơ, bus đổi chậm hơn lượt đọc hàng trăm lần.
     */
    private fun probeOnce(def: ControlDef) {
        if (!listening || def.probe.isEmpty() || !probed.add(def.id)) return
        releaser.schedule("${def.id}$PROBE_KEY", 0L) {
            log { CtlWriteJournal.probeLine(def.id, def.probe.map { it.label to getterInt(it.getter, it.arg) }) }
        }
    }

    /**
     * Lượt đọc CHỈ cho nhật ký: nó **không bao giờ** được làm hỏng lệnh ghi đang chạy. Đường HAL đi qua reflection
     * (gateway thật đã nuốt lỗi thành `null`, nhưng một gateway khác có thể ném) ⇒ bắt `RuntimeException` và ghi
     * **tên lỗi** vào ô của dòng (`before=!IllegalStateException`) — không nuốt im: người đọc nhật ký thấy lượt đọc
     * đã ném, còn lệnh mở nóc thì không bị huỷ vì một dòng nhật ký.
     */
    private fun quietRead(id: String): Any? = try {
        readState(id)
    } catch (e: RuntimeException) {
        "!" + e.javaClass.simpleName
    }

    private inline fun log(line: () -> String) {
        if (listening) journal.write(line())
    }

    private companion object {
        /** Khoá bộ hẹn của lượt đọc-sau: `<id>@after#<seq>` — mỗi lệnh một khoá, lệnh sau không huỷ lượt đọc của lệnh trước. */
        const val AFTER_KEY = "@after#"

        /** Khoá bộ hẹn của ảnh chụp chẩn đoán (một lần mỗi tiến trình mỗi nút). */
        const val PROBE_KEY = "@probe"
    }
}
