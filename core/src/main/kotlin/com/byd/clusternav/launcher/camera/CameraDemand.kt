package com.byd.clusternav.launcher.camera

/**
 * ═══ 2.93 · CAMERA THEO YÊU CẦU — máy trạng thái + mã đích, THUẦN (test off-car) ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-293-cam.html` R1. Owner 06/10 (nguyên văn):
 *  • *"mở overlay lên như là xinhan, nhưng mà không trigger từ xinhan mà trigger từ bind phím vật lý, hoặc widget action
 *    button, hoặc voice"*;
 *  • *"tắt bằng cách bấm nút vật lý, voice, nút bấm trên widget cũng OK mà hả? không nên timeout, các nút đều là toggle
 *    on/off là được"*;
 *  • *"(a) đi, (b) rối lắm"* ⇒ MỘT camera theo yêu cầu tại một lúc: mở camera khác ⇒ THAY; bấm lại đúng camera ⇒ TẮT;
 *  • *"R không sao, mình đã test overlay nó đè lên cam360 rồi"* ⇒ số R KHÔNG có nhánh riêng (không có hằng nào ở đây).
 *
 * ## Ba bề mặt, một luật toggle — và động từ TẮT luôn thắng
 *  • **Nút** (phím vật lý · nút trên thanh nút): [Op.Toggle] — đúng lời owner *"các nút đều là toggle"*.
 *  • **Giọng nói** ([spoken]): câu MỞ (*"mở cam trái"* · *"cam trái"*) ⇒ [Op.Toggle] — điều phối 2.93 theo owner 06/10:
 *    *nói lại câu mở lần hai thì tắt*; câu TẮT (*"tắt cam trái"*) ⇒ [Op.Close] (chỉ tắt đúng camera ấy, không bao giờ mở);
 *    *"tắt cam"* trần ⇒ [Op.CloseAll]. Phiên nghe ở `:wake` không biết camera nào đang hiện ⇒ câu trả lời của câu MỞ chỉ
 *    nói TÊN camera, không hứa *"Mở"* (`VoiceReplyPreview.launcher`). [Op.Open] còn lại cho cầu kiểm thử (`open:<camera>`).
 *  • **Không hẹn giờ tắt** — không có hằng thời gian nào trong lớp này (owner *"không nên timeout"*).
 *
 * ## Camera xi-nhan vẫn chạy như cũ — luật *"sự kiện MỚI NHẤT thắng"* ([shown])
 * Overlay chỉ có MỘT cửa sổ + MỘT luồng `AVMCamera` ([CameraSignalController]) ⇒ hai nguồn phải chia một chỗ:
 *  • xi-nhan BẬT/đổi bên khi camera theo yêu cầu đang mở ⇒ camera xi-nhan hiện (owner: *"camera xi-nhan vẫn hiện như cũ"*);
 *  • hết xi-nhan (sau HOLD) ⇒ camera theo yêu cầu quay lại — vì không có giờ tắt, nó vẫn đang "bật";
 *  • bấm nút/nói khi xi-nhan đang hiện ⇒ lệnh vừa bấm thắng (bấm mà không thấy gì đổi là nút "chết").
 */
object CameraDemand {

    /** Một lệnh cho camera theo yêu cầu. */
    sealed interface Op {
        /**
         * NÚT (phím vật lý · thanh nút): đúng camera đang mở theo yêu cầu VÀ đang hiện ⇒ tắt; khác/không có/đang bị camera
         * xi-nhan che ⇒ mở (thay, đưa lên) — xem [next].
         */
        data class Toggle(val which: CameraWhich) : Op

        /** MỞ chắc chắn (cầu kiểm thử `open:<camera>`): luôn ra X (thay camera đang mở). Lặp lại = không đổi. */
        data class Open(val which: CameraWhich) : Op

        /** GIỌNG *"tắt/đóng/ẩn camera X"*: chỉ tắt khi X đang mở theo yêu cầu — không tắt nhầm camera khác đang xem. */
        data class Close(val which: CameraWhich) : Op

        /** *"Tắt camera"* (phím/nút): tắt camera theo yêu cầu, bất kể camera nào. */
        object CloseAll : Op {
            override fun toString(): String = "CloseAll"
        }
    }

    /** Lệnh của một cú bấm NÚT camera [which] (`null` = nút *Tắt camera*) — nút luôn là toggle. */
    fun tap(which: CameraWhich?): Op = which?.let { Op.Toggle(it) } ?: Op.CloseAll

    /**
     * Lệnh của một câu nói về camera [which]: động từ TẮT/ĐÓNG ([off]) ⇒ [Op.Close] (chỉ tắt đúng camera ấy); còn lại ⇒
     * [Op.Toggle] — điều phối 2.93 theo owner 06/10: *nói lại câu mở lần hai thì tắt* (cùng luật nút: toggle theo thứ ĐANG
     * HIỆN, nên camera đang bị xi-nhan che thì câu mở đưa nó lên chứ không tắt — [next]).
     */
    fun spoken(which: CameraWhich, off: Boolean): Op = if (off) Op.Close(which) else Op.Toggle(which)

    /**
     * Camera theo yêu cầu SAU lệnh [op], khi đang là [current] (`null` = không mở camera nào) và overlay đang hiện [visible].
     *
     * NÚT là toggle theo thứ người lái NHÌN THẤY: chỉ TẮT khi chính camera ấy đang mở theo yêu cầu **và** đang hiện. Nó
     * đang bị camera xi-nhan che ⇒ bấm là ĐƯA LÊN (bấm mà màn không đổi gì là nút "chết"; tắt một camera đang bị che là
     * tắt thứ người lái không thấy — soát 2.93). [visible] mặc định = [current] (không xi-nhan che) — toggle trần.
     */
    fun next(current: CameraWhich?, op: Op, visible: CameraWhich? = current): CameraWhich? = when (op) {
        is Op.Toggle -> if (current == op.which && visible == op.which) null else op.which
        is Op.Open -> op.which
        is Op.Close -> if (current == op.which) null else current
        Op.CloseAll -> null
    }

    /**
     * Camera THẬT SỰ hiện trên overlay.
     *
     * @param blinker camera của xi-nhan đang giữ ([CameraHold] — kể cả pha HOLD), `null` = không xi-nhan / tính năng TẮT.
     * @param demand camera theo yêu cầu đang bật, `null` = không.
     * @param demandNewer lệnh theo yêu cầu MỚI HƠN lần xi-nhan bật/đổi bên gần nhất.
     */
    fun shown(blinker: CameraWhich?, demand: CameraWhich?, demandNewer: Boolean): CameraWhich? =
        if (demandNewer) demand ?: blinker else blinker ?: demand

    /**
     * Ô/nút camera [which] có đang SÁNG không (2.93 wave 2B · CAMERA-DOCK-ACTIVE-STATE): sáng ⇔ chạm là TẮT — CHÍNH luật
     * toggle [next] (bị camera xi-nhan che thì chạm là ĐƯA LÊN ⇒ không sáng). Một luật cho ô thanh nút, ô widget lưới và
     * chữ nút *Xem thử* ở Cài đặt — không có bản thứ hai để lệch.
     */
    fun isOn(which: CameraWhich, demanded: CameraWhich?, showing: CameraWhich?): Boolean =
        next(demanded, Op.Toggle(which), showing) == null

    // ── ĐỔI HỒ SƠ khi khung đang hiện (wave 2B · D6 + wave 2C · CAM-D6-SAME-CAMERA-EDGE) ─────────────────────────────────

    /** Lượt đổi hồ sơ làm gì với khung đang hiện — xem [profileReapply]. */
    enum class ProfileReapply {
        /** Dựng lại NGAY: khung là camera theo yêu cầu và KHÔNG phải camera xi-nhan đang giữ. */
        NOW,

        /** Xi-nhan đang giữ ĐÚNG camera đang hiện (cũng là camera theo yêu cầu) ⇒ HẸN dựng lại lúc xi-nhan nhả. */
        AT_RELEASE,

        /** Để yên: không gì hiện · không camera theo yêu cầu · khung là camera xi-nhan và camera theo yêu cầu đang bị che. */
        NONE,
    }

    /**
     * Đổi hồ sơ khi xi-nhan giữ [blinker] (`null` = không), camera theo yêu cầu là [demand], khung ĐANG hiện [showing] (sự
     * thật của cửa sổ — nó đã gói luật *"mới nhất thắng"* [shown], nên không cần cờ mới-hơn).
     *
     * D6 (wave 2B): camera THEO YÊU CẦU đang hiện nhận cấu hình mới NGAY; camera xi-nhan đang giữ để yên (không dỡ camera điểm
     * mù giữa lúc rẽ) — camera theo yêu cầu bị che sẽ được MỞ (bằng cấu hình mới) khi xi-nhan nhả. Cạnh wave 2C: hai nguồn
     * TRÙNG camera (theo yêu cầu trái + xi-nhan trái) — MỘT phiên, xi-nhan đang dùng nó ⇒ không dựng lại giữa lúc rẽ (kể cả khi
     * lệnh theo yêu cầu mới hơn: dỡ là chớp đen đúng camera điểm mù); lúc nhả, `show` thấy CÙNG camera nên không dựng lại ⇒
     * phải [ProfileReapply.AT_RELEASE], không thì khung giữ cấu hình hồ sơ cũ tới lượt mở kế (không hẹn giờ tắt ⇒ có thể cả
     * chuyến). Thuần ⇒ test ở `CameraDemandProfileReapplyTest`.
     */
    fun profileReapply(blinker: CameraWhich?, demand: CameraWhich?, showing: CameraWhich?): ProfileReapply =
        when {
            showing == null || demand == null || showing != demand -> ProfileReapply.NONE
            blinker == showing -> ProfileReapply.AT_RELEASE
            else -> ProfileReapply.NOW
        }

    // ── KẾT QUẢ một lệnh — lời đáp NÓI ĐÚNG việc đã xảy ra (2.93 wave 2B · VOICE-CAM-OFF-360-FALLBACK, spec §10 D1 · OQ6) ─

    /**
     * Điều THẬT SỰ xảy ra với camera theo yêu cầu sau MỘT lệnh. [code] là `resultCode` của cầu `:wake` → chính (broadcast
     * CÓ THỨ TỰ): [UNREACHABLE] = 0 = mã khởi đầu bên gửi đặt ⇒ không receiver nào trả lời (tiến trình chính vừa chết,
     * receiver chưa đăng ký) thì bên gửi tự đọc ra [UNREACHABLE] — không cần nhánh riêng, không đoán.
     */
    enum class Outcome(val code: Int) {
        /** Camera của lệnh đang mở theo yêu cầu (mới mở · thay camera khác · đưa lên khỏi camera xi-nhan). */
        OPENED(1),

        /** Camera theo yêu cầu vừa TẮT do lệnh này. */
        CLOSED(2),

        /** Lệnh TẮT mà không có camera theo yêu cầu nào (đúng camera ấy) đang mở — không đổi gì. */
        NOTHING_TO_CLOSE(3),

        /** Không tới được controller (tiến trình chính không trả lời kịp) — KHÔNG biết đã xảy ra gì. */
        UNREACHABLE(0),
    }

    /** Kết quả của [op] khi camera theo yêu cầu đổi [before] → [after] trong CÙNG một lượt áp ([CameraDemandState.apply]). */
    fun outcome(op: Op, before: CameraWhich?, after: CameraWhich?): Outcome = when (op) {
        is Op.Toggle, is Op.Open -> if (after == null) Outcome.CLOSED else Outcome.OPENED
        is Op.Close, Op.CloseAll -> if (after != before) Outcome.CLOSED else Outcome.NOTHING_TO_CLOSE
    }

    /**
     * Kết quả khi tiến trình CHÍNH không sống (phiên `:wake` hỏi trước khi gửi): camera theo yêu cầu chỉ sống trong RAM của
     * tiến trình chính (KDoc [CameraDemandState]) ⇒ lệnh TẮT chắc chắn [Outcome.NOTHING_TO_CLOSE] (để *"tắt camera"* trần rơi
     * về Camera 360 như ≤ 2.92), lệnh MỞ thì [Outcome.UNREACHABLE] (không có ai để mở camera).
     */
    fun withoutMain(op: Op): Outcome = when (op) {
        is Op.Close, Op.CloseAll -> Outcome.NOTHING_TO_CLOSE
        is Op.Toggle, is Op.Open -> Outcome.UNREACHABLE
    }

    /** `resultCode` của cầu → kết quả; mã lạ ⇒ [Outcome.UNREACHABLE] (không đoán). */
    fun outcomeOf(code: Int): Outcome = Outcome.entries.firstOrNull { it.code == code } ?: Outcome.UNREACHABLE

    // ── MÃ ĐÍCH PHÍM VẬT LÝ (`voicekey_bindings`): `cam:<mã camera>` = bật/tắt · `cam:off` = tắt camera ───────────────
    //
    // Cùng không gian chuỗi với tên gói app, sentinel `__X__` và `ctl:<nút>:<việc>` (`KeyCtlTargets`). Tên gói Android
    // KHÔNG chứa `:` và sentinel bọc `__` ⇒ tiền tố [KEY_PREFIX] không trùng đích cũ nào; `ctl:` khác bốn ký tự đầu.
    // Chuỗi chỉ mang mã camera — không vị trí, không chữ người dùng ⇒ bản chia sẻ hồ sơ mang theo được (`R_KEYS`).

    /** Tiền tố mã đích camera trong bảng gán phím. */
    const val KEY_PREFIX = "cam:"

    /** Đuôi của mã *"tắt camera"* — không trùng [CameraWhich.code] nào (bài canh). */
    const val KEY_OFF = "off"

    /** Mọi đích phím, theo thứ tự hiện trong hộp chọn: bốn camera (bật/tắt) rồi *"Tắt camera"*. */
    val KEY_OPS: List<Op> = CameraWhich.ALL.map { Op.Toggle(it) } + Op.CloseAll

    /** Chuỗi có phải mã đích camera không (chỉ cú pháp tiền tố — giải trọn bằng [parseKey]). */
    fun isKey(spec: String): Boolean = spec.startsWith(KEY_PREFIX)

    /** Mã bền của một đích phím; chỉ [Op.Toggle] và [Op.CloseAll] là đích phím (nút = toggle) — còn lại `null`. */
    fun keySpec(op: Op): String? = when (op) {
        is Op.Toggle -> KEY_PREFIX + op.which.code
        Op.CloseAll -> KEY_PREFIX + KEY_OFF
        else -> null
    }

    /** Giải mã đích phím. Cú pháp sai / camera lạ ⇒ `null` (tầng thi hành báo, không bắn). Chịu hoa/thường. */
    fun parseKey(spec: String): Op? {
        if (!isKey(spec)) return null
        val tail = spec.removePrefix(KEY_PREFIX).trim()
        if (tail.equals(KEY_OFF, ignoreCase = true)) return Op.CloseAll
        return CameraWhich.ofCode(tail)?.let { Op.Toggle(it) }
    }

    // ── CẦU KIỂM THỬ: `camera --es name <động từ>:<camera>` ─────────────────────────────────────────────────────────
    //
    // Lệnh `camera` cũ (`left`/`right`/`none` = xi-nhan GIẢ) giữ nguyên nghĩa; chỉ chuỗi CÓ dấu `:` mới vào đây ⇒ hai
    // không gian không thể lẫn. Ba động từ = ba nghĩa ở trên, để QA máy ảo đo được cả nút lẫn giọng nói.

    /** `demand:<camera>` = như bấm NÚT ([Op.Toggle]); `demand:off` = [Op.CloseAll]. */
    const val BRIDGE_TOGGLE = "demand"

    /**
     * `open:<camera>` = MỞ chắc chắn ([Op.Open]) — chỉ QA. ⚠ Câu nói *"mở camera …"* từ Pass 1 là [Op.Toggle] (= `demand:`),
     * không phải lệnh này ([spoken]).
     */
    const val BRIDGE_OPEN = "open"

    /** `close:<camera>` = như nói *"tắt camera …"* ([Op.Close]). */
    const val BRIDGE_CLOSE = "close"

    /** Giải đối số cầu kiểm thử; không phải dạng `<động từ>:<đích>` hợp lệ ⇒ `null` (lệnh cũ đi tiếp). */
    fun parseBridge(arg: String): Op? {
        val a = arg.trim().lowercase()
        val verb = a.substringBefore(':', "")
        val target = a.substringAfter(':', "")
        if (verb.isEmpty() || target.isEmpty()) return null
        if (verb == BRIDGE_TOGGLE && target == KEY_OFF) return Op.CloseAll
        val which = CameraWhich.ofCode(target) ?: return null
        return when (verb) {
            BRIDGE_TOGGLE -> Op.Toggle(which)
            BRIDGE_OPEN -> Op.Open(which)
            BRIDGE_CLOSE -> Op.Close(which)
            else -> null
        }
    }

    // ── Chuỗi qua cầu `:wake` → tiến trình chính (broadcast trong gói, `CameraDemandDispatch` ở `:app`) ───────────────

    /**
     * Mã hoá một lệnh thành MỘT chuỗi ASCII — CÙNG cú pháp với cầu kiểm thử (`demand:rear` · `open:left` · `close:front` ·
     * `demand:off`), để một lượt đọc `logcat` của cầu `:wake` và một lệnh `adb` QA gõ tay là cùng một chuỗi.
     */
    fun encode(op: Op): String = when (op) {
        is Op.Toggle -> "$BRIDGE_TOGGLE:${op.which.code}"
        is Op.Open -> "$BRIDGE_OPEN:${op.which.code}"
        is Op.Close -> "$BRIDGE_CLOSE:${op.which.code}"
        Op.CloseAll -> "$BRIDGE_TOGGLE:$KEY_OFF"
    }

    /** Ngược [encode] (= [parseBridge]); chuỗi lạ / `null` ⇒ `null` (bên nhận bỏ, không đoán). */
    fun decode(s: String?): Op? = s?.let { parseBridge(it) }
}
