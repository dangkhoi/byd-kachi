package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.StackParse

/**
 * ═══ FIX286 · R-SC2 — APP CỦA Ô CÒN SỐNG KHÔNG, ĐO LÚC CHẠM LỐI TẮT (thuần, `:core`) ══════════════════════════════
 *
 * Spec `docs/specs/kachi-286-field-fixes.html` §3.10 R-SC. Lỗi xe owner 03/10: Google Maps đặt ở ô 1, tắt Maps ⇒ ô 1 đen;
 * chạm lại lối tắt *Ô 1* của Maps ⇒ chỉ nháy ô, Maps KHÔNG mở lại. Gốc [ĐO mã]: `ShortcutPlan` dòng 4 (`m == n ⇒ Noop`)
 * quyết bằng BỐ CỤC ("ô 1 là Maps") chứ không bằng SỰ THẬT (Maps còn task trên màn ảo của ô không) — CLAUDE.md §5.
 *
 * Phép đo là MỘT bản `am stack list` đọc lúc chạm, đối chiếu với màn ảo của ô. Bộ đọc là [StackParse] (nhận cả
 * `Stack id=` của A10 lẫn `RootTask id=` của A12 — DL5), cùng phép "đang chạy = có TASK" mà chuỗi chạy ngầm đã chốt sau
 * lượt soát shortcuts-autostart Pass 6 (đo bằng `pidof` thì tiến trình không cửa sổ của widget bị coi là đang chạy).
 *
 * Bốn kết luận, và vì sao [GONE] là kết luận DUY NHẤT cho phép mở lại (mở lại = `am force-stop` + `am start` — đường ô
 * golden): chỉ khi app không còn task ở BẤT KỲ display nào thì lệnh giết không cướp mất thứ người dùng đang thấy. App
 * còn task ở chỗ khác (toàn màn display 0, sau màn nhà, đang chiếu cụm) ⇒ [ELSEWHERE] ⇒ không đụng.
 */
enum class SlotPresence {
    /** App có task trên màn ảo của ô ⇒ đang sống trong ô. */
    IN_SLOT,

    /** Đọc được bản `am stack list` và app KHÔNG còn task nào ở display nào ⇒ đã đóng (khung cuối / ô vừa rời). */
    GONE,

    /** App còn task nhưng ở display khác màn ảo của ô (toàn màn · sau màn nhà · đang chiếu cụm) ⇒ không đụng. */
    ELSEWHERE,

    /** Không đo được: chưa kênh · ô không có màn ảo giữ app · bản đọc rỗng/lạ ⇒ bảng giữ hành vi trước FIX286. */
    UNKNOWN;

    companion object {
        /**
         * Kết luận cho [pkg] ở ô có màn ảo [vd] từ bản đọc [stackList] (nguyên văn `am stack list`).
         *
         * ⚠ Bản đọc không ra MỤC NÀO ⇒ [UNKNOWN], không phải [GONE]: display 0 luôn có stack màn nhà, nên "0 mục" chỉ có
         * thể là đọc hỏng / định dạng lạ — coi đó là "app đã đóng" thì một lần kênh trả rỗng là một lần giết app đang sống.
         *
         * 2.98 · R3 (`SLOT-ELSEWHERE-TWO-HOMES`, spec `kachi-298-plan.html`): [ignoring] = display KHÔNG tính là "chỗ khác" — màn
         * ảo ô của một màn Kachi KHÁC còn sống (`SlotProbeScope.otherHomes`; chỉ bộ đo ô truyền). App chỉ còn task ở đó ⇒ [GONE] với
         * ô này (đã rời ô, nhưng không "ra ngoài ô"). Mặc định rỗng = như trước (chạm lối tắt FIX286 không đổi).
         */
        fun of(stackList: String, pkg: String, vd: Int, ignoring: Set<Int> = emptySet()): SlotPresence {
            if (vd < 1 || pkg.isBlank()) return UNKNOWN
            val entries = StackParse.parse(stackList)
            if (entries.isEmpty()) return UNKNOWN
            val mine = StackParse.of(entries, pkg)
            return when {
                mine.any { it.displayId == vd } -> IN_SLOT
                mine.all { it.displayId in ignoring } -> GONE   // rỗng ⇒ GONE như trước
                else -> ELSEWHERE
            }
        }
    }
}
