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
     * Gồm: khoá/mở khoá cửa (`lock`,`door`), cốp (`trunk`), ca-pô (`hood`), nóc (`sunroof`), rèm (`sunshade`),
     * kính cửa lái (`window`), kính mở-hết (`windows_all`) và từng kính (`win_lf`,`win_rf`,`win_lr`,`win_rr`).
     * Đây đúng tập DENYLIST mà task 71-hal-sweep yêu cầu ("kính mở-hết/nóc/rèm" + từng kính + khoá + cốp).
     */
    val CONFIRM_REQUIRED: Set<String> = setOf(
        "lock", "door", "trunk", "hood", "sunroof", "sunshade",
        "window", "windows_all", "win_lf", "win_rf", "win_lr", "win_rr",
    )

    /** Control [id] có cần `auto_confirm` để bắn qua cầu kiểm thử không (và bị pha WRITE của sweep bỏ qua). */
    fun needsConfirm(id: String): Boolean = id in CONFIRM_REQUIRED

    /**
     * ═══ C (owner test xe 2026-09-19) · CHỈ MỞ ĐƯỢC KHI XE ĐANG DỪNG ══════════════════════════════════════════
     *
     * Cốp và ca-pô là hai bộ phận **bung ra ngoài bao xe** khi mở. Mở lúc xe đang chạy thì cốp che trọn kính hậu
     * (mất tầm quan sát sau) và ca-pô bật lên **che kính lái** — ca thứ hai là mất tầm nhìn trước ở tốc độ, tức
     * hỏng nặng hơn hẳn mọi nút khác trong registry. Một câu nói nghe nhầm giữa lúc chạy là đủ.
     *
     * ## Vì sao ĐÚNG HAI mã này, không phải cả họ "mở thân xe"
     * [CONFIRM_REQUIRED] rộng hơn có chủ ý (nó canh *"lệnh từ ngoài"*), còn tập này canh *"vận tốc"* — hai câu hỏi
     * khác nhau nên hai danh sách. Kính và cửa sổ trời **CỐ Ý không** vào đây: mở kính lúc đang chạy là việc bình
     * thường, ai cũng làm, và gate chúng lại biến một tính năng đang dùng tốt thành một lời từ chối vô cớ. Khoá/mở
     * khoá cửa (`lock`/`door`) cũng không: xe tự khoá theo tốc độ, và mở khoá khi đang chạy không bung gì ra ngoài.
     *
     * ⇒ Tiêu chí kết nạp là **"mở ra thì bung khỏi bao xe / che tầm nhìn"**, không phải *"thuộc BODY"*.
     */
    val REQUIRES_STATIONARY: Set<String> = setOf("trunk", "hood")

    /** Control [id] chỉ được MỞ khi xe đang dừng (0 km/h) không — xem [REQUIRES_STATIONARY]. */
    fun requiresStationary(id: String): Boolean = id in REQUIRES_STATIONARY

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
     * Kê cả mã **chưa** có khoá ĐỌC (`hood` · `windows_all` · `sunshade`): tính chất được kê ở đây là của **bộ phận**
     * chứ không của việc hôm nay đọc được hay chưa — ngày chúng có khoá đọc thì không ai phải nhớ quay lại đây.
     *
     * ⚠ Nới [VoiceReadback.READBACK_SETTLE_MS] KHÔNG thay được danh sách này: chờ đủ cho một cửa kính (vài giây) là
     * bắt **mọi** câu trả lời chậm thêm vài giây. Khoảng chờ theo từng nút là việc của lượt có số ĐO trên xe.
     */
    val MOVES_SLOWLY: Set<String> = setOf(
        "trunk", "hood", "sunroof", "sunshade",
        "window", "windows_all", "win_lf", "win_rf", "win_lr", "win_rr",
    )

    /** Control [id] có phải bộ phận chạy bằng mô-tơ (mất vài giây) không — xem [MOVES_SLOWLY]. */
    fun movesSlowly(id: String): Boolean = id in MOVES_SLOWLY
}
