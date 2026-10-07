package com.byd.clusternav.modules.navaccess

/**
 * NHẬT KÝ BỀN của trạng thái gắn dịch vụ Hỗ trợ (R7 · spec `kachi-a11y-bind-stuck-autofix.html`).
 *
 * VÌ SAO CẦN — [ĐO owner, lặp lại nhiều lần] phím chết sau khi **để xe qua đêm standby**, không phải sau khi
 * cài bản mới. Nhưng vòng đệm `logcat` trên xe chỉ giữ được vài chục phút ([ĐO 2026-09-28]: vòng đệm sự kiện
 * còn đúng 32 phút), nên đến sáng thì khoảnh khắc đứt đã trôi mất — không thể biết nó đứt lúc nào và đứt cùng
 * với cái gì. Ghi vào tệp của chính app thì sống qua cả standby lẫn khởi động lại, và người dùng **không phải
 * gõ adb** (CLAUDE.md §11: app tự chụp lấy dữ liệu của nó, anh em chỉ gửi ảnh màn hình).
 *
 * Thuần, không chạm hệ thống, không lấy giờ — mọi mốc do caller đưa vào để test off-device.
 *
 * RIÊNG TƯ: dòng ghi CHỈ có mốc giờ, hai đồng hồ, pid và trạng thái. KHÔNG tên app, KHÔNG vị trí, KHÔNG nội
 * dung màn hình — nhật ký này có thể bị gửi ra ngoài dưới dạng ảnh chụp nên phải vô hại theo thiết kế.
 */
object A11yBindJournal {

    /** Ba trạng thái phân biệt được, tương ứng ba nhánh chữa. */
    enum class State {
        /** Đã gắn thật (hỏi `AccessibilityManager`). */
        BOUND,

        /** Chưa gắn, và KHÔNG kẹt — ca thường, đường toggle chữa được. */
        NOT_BOUND,

        /** Chưa gắn, ĐANG kẹt trong `Binding services` — đường toggle vô hiệu, phải leo force-stop. */
        STUCK,
    }

    /** Giữ tệp bé: quá số này thì bỏ bớt dòng CŨ NHẤT. 200 dòng ≈ vài tháng vì chỉ ghi khi ĐỔI. */
    const val MAX_LINES = 200

    /** 2.83 lớp 1 — dòng ghi bởi lượt kiểm lúc TẮT MÁY (tiến trình khởi động khi màn không tương tác). */
    const val NOTE_TAT_MAY = "tat-may"

    /** 2.83 lớp 2 — dòng ghi bởi lượt kiểm lúc MỞ XE (trong ân hạn sau `ACTION_SCREEN_ON`). */
    const val NOTE_MO_XE = "mo-xe"

    /**
     * READY-AT-HOME (02/10) — lớp 2 mở rộng: dòng ghi bởi lượt kiểm trong ÂN HẠN KHỞI ĐỘNG (tiến trình DỰNG LẠI lúc màn
     * đang bật — [AccessibilityHealGates.HealPhase.KHOI_DONG]).
     */
    const val NOTE_KHOI_DONG = "khoi-dong"

    /**
     * 2.83 lớp 3 — dòng ghi bởi lượt KIỂM LẠI một ca kẹt đã đo (mỗi [AccessibilityHealGates.STUCK_RECHECK_MS], chỉ
     * đọc `dumpsys`). Vẫn kẹt thì cùng trạng thái ⇒ không thêm dòng; chỉ bước ĐỔI (hết kẹt) mới hiện chữ này.
     */
    const val NOTE_RECHECK = "kiem-lai"

    /**
     * Ghi chú cho dòng nhật ký của nấc leo thang: bấm tay (`grant-tay`), lớp 1/2 (`tat-may`/`mo-xe`/`khoi-dong`), hay đường tự
     * động lúc đang chạy (`grant-tu-dong`, nay KHÔNG còn tự giết — chỉ ghi nhận kẹt để người dùng bấm nút).
     */
    fun grantNote(userAsked: Boolean, phase: AccessibilityHealGates.HealPhase): String = when {
        userAsked -> "grant-tay"
        phase == AccessibilityHealGates.HealPhase.TAT_MAY -> NOTE_TAT_MAY
        phase == AccessibilityHealGates.HealPhase.MO_XE -> NOTE_MO_XE
        phase == AccessibilityHealGates.HealPhase.KHOI_DONG -> NOTE_KHOI_DONG
        else -> "grant-tu-dong"
    }

    /** Chấm điểm lượt chữa vừa giết chính mình — cùng hai chữ mà watchdog 30 s ghi từ 2.81. */
    fun afterHealNote(bound: Boolean): String = if (bound) "sau-chua-ON" else "sau-chua-VAN-TAT"

    /**
     * Tổng thời gian máy đã NGỦ SÂU tính từ lúc bật máy, bằng hiệu hai đồng hồ của Android:
     * `elapsedRealtime()` đếm cả lúc ngủ, `uptimeMillis()` thì không.
     *
     * Đây là chìa khoá phân biệt **"mười tiếng đứng qua đêm"** với **"mười tiếng chạy đường dài"** — đúng ranh
     * giới owner vạch ra 2026-09-28 (*"nếu xe đang chạy OK, thì tôi lại không nghĩ nó gây hang như thế này"*).
     * Không cần shell, không cần đoán theo thời gian sống của tiến trình.
     *
     * [SUY] hành vi hai đồng hồ là chuẩn Android; CHƯA đo trên ROM DiLink — đo bằng chính nhật ký này.
     */
    fun deepSleepMs(elapsedMs: Long, uptimeMs: Long): Long = (elapsedMs - uptimeMs).coerceAtLeast(0L)

    /**
     * Xe vừa ra khỏi một đợt ngủ DÀI chưa: lượng ngủ tích luỹ tăng thêm ít nhất [thresholdMs] kể từ lần đo
     * trước. [prevDeepSleepMs] `< 0` = chưa có mốc trước ⇒ `false` (không kết luận ở lần đo đầu tiên).
     *
     * Dùng làm ngòi nổ cho lượt tự chữa: đó là lúc khởi động lại giao diện gần như miễn phí, vì trên màn chính
     * mới chỉ có nhà, chưa app nào trong ô, chưa nhạc.
     */
    fun wokeFromLongSleep(prevDeepSleepMs: Long, nowDeepSleepMs: Long, thresholdMs: Long): Boolean =
        prevDeepSleepMs >= 0L && nowDeepSleepMs - prevDeepSleepMs >= thresholdMs

    /**
     * 2.96 · R18 — có cần GHI lại mốc ngủ tích luỹ không. [ĐO mã] nhịp 30 s của `VoiceKeyKeepAliveService` từng ghi
     * `SharedPreferences` MỖI nhịp (≈120 lần ghi tệp XML / giờ lái) dù giá trị chỉ đổi khi SoC vừa ngủ. Ghi khi chưa có mốc,
     * hoặc lệch ≥ [PERSIST_STEP_MS] (lệch vài ms là nhiễu giữa hai lần đọc đồng hồ; ngưỡng ngủ dài là GIỜ ⇒ không đổi kết luận).
     */
    fun shouldPersistDeepSleep(prevDeepSleepMs: Long, nowDeepSleepMs: Long): Boolean =
        prevDeepSleepMs < 0L || kotlin.math.abs(nowDeepSleepMs - prevDeepSleepMs) >= PERSIST_STEP_MS

    /** Bước lệch tối thiểu để ghi lại mốc ngủ — xem [shouldPersistDeepSleep]. */
    const val PERSIST_STEP_MS = 1_000L

    /**
     * Có ghi thêm dòng mới không. Ghi khi **ĐỔI trạng thái** (đó mới là thông tin), hoặc khi đã quá
     * [heartbeatMs] kể từ dòng trước (nhịp tim, để biết máy vẫn đang theo dõi chứ không phải nhật ký chết).
     * [prevState] `null` = tệp rỗng ⇒ luôn ghi dòng đầu.
     */
    fun shouldAppend(
        prevState: State?,
        now: State,
        sinceLastMs: Long,
        heartbeatMs: Long,
        binderOnly: Boolean = false,
    ): Boolean = prevState == null || !sameState(prevState, now, binderOnly) || sinceLastMs >= heartbeatMs

    /**
     * Hai trạng thái có phải CÙNG một sự thật không.
     *
     * [binderOnly] = quan sát chỉ hỏi binder `AccessibilityManager` (watchdog 30 s): nó chỉ biết "đã gắn / chưa gắn",
     * KHÔNG phân biệt được [State.NOT_BOUND] với [State.STUCK] (cần bản `dumpsys`). Nên "chưa gắn" của nó đứng sau
     * một dòng STUCK là CÙNG sự thật, không phải một bước đổi.
     *
     * Bài học [ĐO máy ảo 29/09, E2E 2.83 ca 4]: đang kẹt lúc xe chạy (lớp 3 — 2.83 không tự chữa nữa, chờ người
     * dùng bấm), watchdog ghi NOT_BOUND rồi lượt grant (có dump) ghi STUCK, **mỗi 30 s một cặp** ⇒ trần
     * [MAX_LINES] đầy sau ~50 phút và đẩy mất đúng các dòng `tat-may`/`mo-xe` cần để chốt vì sao lớp 1/2 không chữa.
     */
    fun sameState(prev: State, now: State, binderOnly: Boolean): Boolean =
        prev == now || (binderOnly && prev == State.STUCK && now == State.NOT_BOUND)

    /**
     * Một dòng nhật ký, cố ý dễ grep và đọc được bằng mắt trên ảnh chụp màn hình.
     *
     * `2026-09-28T10:20:00 state=STUCK up=3600s sleep=32400s pid=12738 note=watchdog`
     *
     * [note] bị cắt còn tối đa 40 ký tự và lọc bỏ khoảng trắng lạ để một dòng luôn là một dòng.
     */
    fun line(wallIso: String, elapsedMs: Long, uptimeMs: Long, state: State, pid: Int, note: String): String {
        val safe = note.replace(Regex("[\\r\\n\\t ]+"), "-").take(40).ifBlank { "-" }
        return "$wallIso state=$state up=${uptimeMs / 1000}s sleep=${deepSleepMs(elapsedMs, uptimeMs) / 1000}s " +
            "pid=$pid note=$safe"
    }

    /**
     * Bản logcat của MỘT dòng vừa ghi — **mọi** dòng, kể cả nhịp tim (2.83).
     *
     * VÌ SAO — [ĐO xe 29/09] trên bản phát hành tệp nhật ký KHÔNG đọc được: màn Chẩn đoán đã gỡ nút (21/09) và
     * `exported=false` nên `am start` bị từ chối, `run-as` không có vì bản phát hành không debuggable. Còn
     * `kachi-logs/usage-*.log` (logcat của chính tiến trình, `adb pull` được không cần root) thì đọc được. Trước
     * 2.83 chỉ dòng ĐỔI trạng thái ra logcat, và dưới dạng khác dòng tệp ⇒ nhịp tim (bằng chứng "nhật ký còn sống
     * lúc đó") không bao giờ tới được usage log.
     *
     * Hình dạng: `journal <dòng tệp NGUYÊN VĂN> prev=<trạng thái trước | ->`. Nguyên văn để một lệnh grep
     * (`state=STUCK`) chạy được trên cả tệp lẫn usage log; `prev=` đứng CUỐI để [stateOf] vẫn đọc đúng dòng này,
     * và để usage log của một phiên mới vẫn thấy đây là bước ĐỔI hay chỉ là nhịp tim (dòng trước nằm ở phiên cũ).
     */
    fun logcatLine(prev: State?, line: String): String = "$LOGCAT_PREFIX$line prev=${prev?.name ?: "-"}"

    /** Tiền tố của [logcatLine] — tách dòng nhật ký khỏi mọi câu khác cùng tag (vd "ghi nhật ký lỗi"). */
    const val LOGCAT_PREFIX = "journal "

    /** Đọc trạng thái từ một dòng đã ghi; `null` nếu dòng lạ (nhật ký cũ / tệp hỏng) — không làm vỡ luồng. */
    fun stateOf(line: String?): State? {
        val token = line?.substringAfter("state=", "")?.substringBefore(' ')?.trim().orEmpty()
        return State.entries.firstOrNull { it.name == token }
    }

    /** Giữ [max] dòng CUỐI. Nhật ký là để đọc đoạn gần đây; đoạn xa đã hết giá trị chẩn đoán. */
    fun trim(lines: List<String>, max: Int = MAX_LINES): List<String> =
        if (lines.size <= max) lines else lines.subList(lines.size - max, lines.size)
}
