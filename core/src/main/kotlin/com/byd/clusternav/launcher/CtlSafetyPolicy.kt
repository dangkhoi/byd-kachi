package com.byd.clusternav.launcher

/**
 * ═══ T-BRIDGE · CHÍNH SÁCH AN TOÀN CHO LỆNH `ctl` ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` §9 (grab-list HAL) · CLAUDE.md §4 (lệnh đổi state phải có phạm vi
 * tường minh) · §5 (guard cứng ở tầng THI HÀNH, auth mặc định DENY).
 *
 * Đây là **một nguồn duy nhất** cho tập control "mở/khoá thân xe" — thứ mà một broadcast từ ngoài KHÔNG được tự
 * bắn. Hai tầng đọc cùng danh sách này, nên chúng không thể lệch nhau:
 *  1. **Cầu kiểm thử** (`KachiTestBridge.runCtl`): control trong tập này bị **TỪ CHỐI** (`needs_confirm`) trừ khi
 *     lệnh nói rõ `--ez auto_confirm true` — CÙNG cổng mà `say` dùng cho việc mức CONFIRM. Không cờ ⇒ không chạy.
 *  2. **Script quét HAL** (`scripts/vehicle/kachi/71-hal-sweep.sh`): pha WRITE **không bao giờ tự bắn** các control
 *     này — chỉ liệt kê để owner tự bấm bằng tay và tự xác nhận bằng mắt.
 *
 * ## Vì sao là danh sách mã, không phải suy từ tier/domain
 * Có cân nhắc suy từ `tier == NEEDS_CAR` hoặc `domain == BODY`, nhưng cả hai đều **trật**: `win_lf` là `PROVEN`
 * (đã đo mở được) chứ không `NEEDS_CAR`, còn `BODY` gồm cả `wiper` (vô hại). Ranh giới thật là
 * ngữ nghĩa "mở cửa/kính/nóc/cốp hay đổi khoá" — một tính chất của TỪNG mã, không suy được từ một trường phân
 * loại nào đang có. Liệt kê thẳng, và [DenylistCoverageTest] canh mọi mã ở đây đều là control THẬT.
 *
 * ⚠ Mở rộng registry mà thêm một control mở-thân-xe mới thì phải thêm mã vào đây — đó là lý do có
 * `CtlSafetyPolicyTest.moi_control_mo_than_xe_deu_nam_trong_denylist` (khoá theo từ khoá nhãn) để một mã kính/cửa/
 * nóc/cốp mới lọt ra ngoài sẽ làm test đỏ tại chỗ khai.
 */
object CtlSafetyPolicy {

    /**
     * Control "mở/khoá thân xe" — bắn qua broadcast phải có `auto_confirm`, và script sweep không tự bắn.
     *
     * Gồm: cốp (`trunk`), nóc (`sunroof`), rèm (`sunshade`), kính mở-hết / đóng-hết (`windows_all`,
     * `windows_close_all`), từng kính (`win_lf`,`win_rf`,`win_lr`,`win_rr`) và kính nửa (`win_half_*`).
     * ⚠ KHÔNG gồm khoá/mở khoá cửa (`lock`/`door`) — tới 3.1.16 KDoc này ghi nhầm là có (senior review 26/10 P2-2).
     * Telegram dùng tập này CHỈ để đòi nói rõ `open`/`close` (không lệnh mơ hồ); cổng tốc độ của Telegram là [blocked]
     * như mọi đường khác (3.1.20, owner 26/10).
     *
     * ⚠ 1.85: `hood` rời danh sách vì **mã đã xoá khỏi registry** (xe không có ca-pô điện — [ĐO xe 2026-09-20 §4]),
     * không phải vì nó bớt nguy hiểm. Khoá trẻ em (`child_lock`/`child_lock_r`) CỐ Ý không vào đây: nó khoá *chốt
     * trong* của cửa sau, không mở/bung gì ra ngoài, và chặn nó sau một cổng xác nhận chỉ làm chậm đúng thứ người
     * lái muốn bật ngay khi có trẻ trên xe.
     */
    val CONFIRM_REQUIRED: Set<String> = setOf(
        "trunk", "sunroof", "sunshade",
        "windows_all", "windows_close_all", "win_lf", "win_rf", "win_lr", "win_rr",
        "win_half_all", "win_half_lf", "win_half_rf", "win_half_lr", "win_half_rr",
    )

    /** Control [id] có cần `auto_confirm` để bắn qua cầu kiểm thử không (và bị pha WRITE của sweep bỏ qua). */
    fun needsConfirm(id: String): Boolean = id in CONFIRM_REQUIRED

    /**
     * ═══ C (owner test xe 2026-09-19) · CHỈ MỞ ĐƯỢC KHI XE ĐANG DỪNG ══════════════════════════════════════════
     *
     * Cốp là bộ phận **bung ra ngoài bao xe** khi mở: mở lúc xe đang chạy thì nó che trọn kính hậu (mất tầm quan
     * sát sau). Một câu nói nghe nhầm giữa lúc chạy là đủ.
     *
     * ⚠ 1.85: tập này còn **một** mã. `hood` rời đi vì mã đã xoá khỏi registry (xe không có ca-pô điện — [ĐO xe
     * 2026-09-20 §4]); lý lẽ của nó (*ca-pô bật lên che kính lái*) giữ lại trong nhật ký để ngày nào một xe khác
     * có ca-pô điện thì người nối lại biết phải kết nạp nó ngay. Một tập một phần tử vẫn đúng vai: nó trả lời câu
     * *"mã nào bị gate theo vận tốc"*, và câu trả lời hôm nay là *"cốp"*.
     *
     * ## Vì sao KHÔNG phải cả họ "mở thân xe"
     * [CONFIRM_REQUIRED] rộng hơn có chủ ý (nó canh *"lệnh từ ngoài"*), còn tập này canh *"vận tốc"* — hai câu hỏi
     * khác nhau nên hai danh sách. Kính và cửa sổ trời **CỐ Ý không** vào đây: mở kính lúc đang chạy là việc bình
     * thường, ai cũng làm, và gate chúng lại biến một tính năng đang dùng tốt thành một lời từ chối vô cớ. Khoá/mở
     * khoá cửa (`lock`/`door`) cũng không: xe tự khoá theo tốc độ, và mở khoá khi đang chạy không bung gì ra ngoài.
     *
     * ⇒ Tiêu chí kết nạp là **"mở ra thì bung khỏi bao xe / che tầm nhìn"**, không phải *"thuộc BODY"*.
     */
    val REQUIRES_STATIONARY: Set<String> = setOf("trunk")

    /** Control [id] chỉ được MỞ khi xe đang dừng (0 km/h) không — xem [REQUIRES_STATIONARY]. */
    fun requiresStationary(id: String): Boolean = id in REQUIRES_STATIONARY

    // TELEGRAM-REMOTE: 3.1.15–3.1.19 có cổng riêng cho lệnh từ điện thoại (mọi bộ phận chuyển động, bắt số P). Owner
    // 26/10 trên xe: *"xe đang đậu, về P"* mà kính/cốp vẫn bị từ chối, và *"chỉ cốp mới cần 0 km/h, các cái khác đâu có
    // cần"* ⇒ GỠ (3.1.20). Telegram nay đi đúng [blocked] như chạm/giọng/phím — qua `VoiceControlDispatch.run`.

    /** Ảnh chụp chuyển động cho cổng: [speedKmh] / [gear] thô như [CarStatus.Drivetrain] (`null` = không đọc được). */
    data class Motion(val speedKmh: Int?, val gear: String?)

    /**
     * ═══ 3.01 · owner 13/10 — MỘT phép DUY NHẤT cho câu *"lệnh [arg] của nút [id] có bị CHẶN vì xe chưa chắc đứng yên?"* ═══
     *
     * Owner 13/10 *"Chặn MỞ cốp khi xe chạy (Khuyên dùng)"* (đường chạm) rồi *"vậy làm theo luật mới hết đi cho chắc ăn"*
     * ⇒ CẢ BA đường hỏi cùng phép này, không còn phép thứ hai (tới 3.01 giọng nói/phím dùng `blockedAtSpeed` fail-OPEN):
     *  1. **chạm** trên màn (ô nút · chấm trên hình xe) — `ControlTileFactory.gated`;
     *  2. **giọng nói + phím vô-lăng** — cổng thi hành `VoiceControlDispatch.run` (phím giao intent xuống đúng đó);
     *  3. **Đảo / Kế tiếp giải từ TRÍ NHỚ** ([KeyCtlPlan], [KeyCtlPlan.Basis.MEMORY]) — trí nhớ có thể CŨ ⇒ hành động nó
     *     giải ra mà bị chặn thì đổi sang [STATIONARY_SAFE_ARG] thay vì kẹt MỞ-bị-chặn mãi.
     *
     * Luật: chỉ nút thuộc [REQUIRES_STATIONARY] và chỉ lệnh MỞ (`arg > 0`) — ĐÓNG luôn đi được. **Fail-safe**: chỉ cho mở
     * khi CHỨNG MINH được xe đứng ([provenStationary]): tốc độ đọc được `== 0`, hoặc (tốc độ không đọc được mà) số đọc
     * được là P ([parked]). Tốc độ > 0 ⇒ chặn · không tốc độ + số R/N/D ⇒ chặn · không tốc độ + không số ⇒ chặn.
     * ⚠ Hệ quả có chủ ý: máy ảo/off-car không tốc độ, không số ⇒ MỞ cốp bị chặn trên cả ba đường.
     *
     * [motion] gọi LƯỜI: chỉ đọc khi nút thuộc tập và lệnh là MỞ — mọi nút khác không tốn lượt đọc nào. Nguồn đọc của
     * cả ba đường là [readMotion] (một phép đọc).
     */
    fun blocked(id: String, arg: Int, motion: () -> Motion): Boolean =
        requiresStationary(id) && arg > 0 && !provenStationary(motion())

    /**
     * MỘT phép ĐỌC chuyển động cho [blocked] (chạm · giọng nói · phím): datum TƯƠI qua [fresh] (`AppContainer.readFresh` —
     * vòng poll chỉ tươi cho datum đang hiện), hụt thì lùi [snapshot]; số giả vehicleTest ([CarStatusTap]) áp lên cả hai.
     * Số (`gear`) chỉ đọc khi KHÔNG có tốc độ — đúng nhu cầu của [provenStationary], không tốn lượt HAL thừa.
     */
    fun readMotion(fresh: (String) -> CarStatus?, snapshot: () -> CarStatus): Motion {
        fun read(id: String): CarStatus.Drivetrain =
            CarStatusTap.apply(runCatching { fresh(id) }.getOrNull() ?: snapshot()).drivetrain
        val s = read("speed")
        return if (s.speedKmh != null) Motion(s.speedKmh, s.gear) else Motion(null, read("gear").gear)
    }

    /** Xe CHỨNG MINH được là đứng yên: tốc độ đọc được bằng 0; không có tốc độ thì số đọc được là P. */
    fun provenStationary(m: Motion): Boolean = m.speedKmh?.let { it == 0 } ?: (parked(m.gear) == true)

    /**
     * Số [gear] có phải P không: `true` P · `false` R/N/D · `null` không biết. Hai dạng chuỗi cùng có mặt: mã thô HAL
     * `BYDAutoGearboxDevice.getCurrentGear` (`GEAR_P=3/R=1/N=0/D=2/INVALID=255` — `BYDAutoGearboxDevice.java:79-83`, trích
     * ở [TelemetryRegistry] dòng `gear`; [ĐO xe: =3] lúc đỗ) và chữ cái (số giả vehicleTest). `255`/lạ ⇒ không biết.
     */
    fun parked(gear: String?): Boolean? = when (gear?.trim()?.uppercase()) {
        "P", "3" -> true
        "R", "N", "D", "0", "1", "2" -> false
        else -> null
    }

    /**
     * Lệnh mà cổng KHÔNG BAO GIỜ chặn: [blocked] chỉ chặn `arg > 0` ⇒ `0` (đóng cốp) luôn đi được — *"đóng
     * cốp lúc đang chạy là việc nên làm"*. Đóng một cốp vốn đã đóng thì không có tác dụng gì (không phải lối vòng qua cổng).
     */
    const val STATIONARY_SAFE_ARG = 0

    /**
     * ═══ [SOÁT lượt E · P1] BỘ PHẬN CHẠY BẰNG MÔ-TƠ — mất **vài GIÂY** để tới mức mới ═════════════════════════
     *
     * Dùng bởi `VoiceReadback.act`: một lượt đọc lại sau ~300 ms **chứng minh được THÀNH CÔNG** (mức đã tới đích thì
     * chắc chắn lệnh đã ăn) nhưng **không chứng minh được THẤT BẠI** với các mã ở đây — chúng còn đang chạy dở. Nói
     * *"xe không nhận lệnh"* ở đó là khẳng định một điều sai về một lệnh hoàn toàn thành công.
     *
     * ## Vì sao phải có danh sách này, kèm con số
     * [ĐO off-car] `win_lf`..`win_rr` là [ControlKind.COVER] với `args` = 0/1/2, nhưng khoá ĐỌC của chúng
     * (`window_lf`… → `getWindowOpenPercent`) trả **phần trăm 0–100**. Lệnh ĐÓNG (`arg = 0`) trên một cửa đang mở
     * 100% ⇒ lượt đọc đầu thấy 100, chờ 300 ms vẫn còn ~90 (cửa kính mất vài giây) ⇒ so mức ra *lệch* ⇒ câu trả lời
     * thành *"✗ xe không nhận lệnh"* cho đúng một lệnh ở mức **PROVEN** (đã đo chạy thật trên xe owner). Hướng ĐÓNG
     * gần như **luôn** rơi vào ca này, không phải thỉnh thoảng.
     *
     * ## Tiêu chí kết nạp — *"có mô-tơ kéo, mức đổi dần theo thời gian"*
     * Khác hẳn nhóm điện/khí (`recirc` · `drl` · `anion` · `defrost` · `ac_auto` · ghế) nơi mức đổi gần như tức thì
     * và một chỗ lệch dai dẳng **đúng là** *"lệnh không ăn"* — nên nhóm ấy KHÔNG vào đây, và vẫn phải nghe được câu
     * *"xe không nhận lệnh"* (đó là nửa owner yêu cầu ở E).
     *
     * Kê cả mã **chưa** có khoá ĐỌC (`windows_all` · `sunshade`): tính chất được kê ở đây là của **bộ phận**
     * chứ không của việc hôm nay đọc được hay chưa — ngày chúng có khoá đọc thì không ai phải nhớ quay lại đây.
     * (1.85: `hood` rời danh sách cùng lượt xoá mã.)
     *
     * ⚠ Nới [VoiceReadback.READBACK_SETTLE_MS] KHÔNG thay được danh sách này: chờ đủ cho một cửa kính (vài giây) là
     * bắt **mọi** câu trả lời chậm thêm vài giây. Khoảng chờ theo từng nút là việc của lượt có số ĐO trên xe.
     */
    val MOVES_SLOWLY: Set<String> = setOf(
        "trunk", "sunroof", "sunshade",
        "windows_all", "windows_close_all", "win_lf", "win_rf", "win_lr", "win_rr",
        "win_half_all", "win_half_lf", "win_half_rf", "win_half_lr", "win_half_rr",
    )

    /** Control [id] có phải bộ phận chạy bằng mô-tơ (mất vài giây) không — xem [MOVES_SLOWLY]. */
    fun movesSlowly(id: String): Boolean = id in MOVES_SLOWLY
}
