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
 * (đã đo mở được) chứ không `NEEDS_CAR`, còn `BODY` gồm cả `wiper`/`mirror_fold_btn` (vô hại). Ranh giới thật là
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
}
