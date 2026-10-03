package com.byd.clusternav.launcher

/**
 * ═══ FIX286 · ES5 — KHI NÀO NẠP BỐ CỤC MẶC ĐỊNH ═══════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-286-field-fixes.html` §3.4 ES5. Owner 03/10 (*"3 ok"*): **bố cục toàn ô trống giữ trống**,
 * chỉ nạp bố cục mặc định (3 widget, [WorkspaceState.DEFAULT]) cho hồ sơ **chưa từng lưu**.
 *
 * ## Gốc lỗi [ĐO mã, đọc lại 02/10]
 * Bản ≤ 2.85 xét *"mọi ô Empty"* ⇒ nạp 3 widget, ở MỖI lượt `load()` — tức mỗi lần nổ máy (BYD giết Kachi mỗi lần
 * tắt máy), mỗi lần đổi hồ sơ và mỗi lần nhập hồ sơ. Một bố cục *"vẽ khung, chưa gán app"* — đúng thứ owner muốn
 * thấy trong suốt — vì thế không sống qua lần nổ máy kế: ba khung đầu tự có lại `w_board`, `w_energy`, `w_pm25`.
 * Câu hỏi đúng không phải *"ô có trống không"* mà *"người dùng đã từng lưu bố cục cho hồ sơ này chưa"*.
 *
 * ## Dấu "đã từng lưu" = khoá `<hồ sơ>__slot_*` có mặt trên đĩa (không thêm khoá mốc mới)
 * `WorkspacePrefs.save` luôn ghi ĐỦ `slot_0..` (ô trống ghi chuỗi rỗng — vẫn là khoá có mặt). Nên sự có mặt của khoá
 * đã là dấu, và một khoá mốc thứ hai chỉ là chỗ để hai sự thật lệch nhau (ghi slot mà quên mốc ⇒ bố cục người dùng
 * bị thay; ghi mốc mà quên slot ⇒ hồ sơ mới trống trơn).
 *
 * ## Hồ sơ MỚI = bản sao (FIX286 · R-F2, chốt 2026-10-03 — phiên điều phối, owner có thể đổi)
 * Đường tạo hồ sơ DUY NHẤT trên UI là *"Thêm hồ sơ (bản sao của …)"* (`WorkspacePrefs.duplicateActiveProfile`): chép
 * NGUYÊN mọi khoá theo hồ sơ, kể cả `slot_*` — khoá vắng thì bản sao cũng vắng. ⇒ dấu "đã lưu" đi theo bản sao: bản
 * sao của bố cục toàn ô trống cũng TRỐNG; bản sao của hồ sơ chưa từng lưu cũng ra bố cục mặc định — bản sao luôn hiện
 * đúng thứ hồ sơ gốc đang hiện. (Bản đầu của KDoc này viết *"`addProfile` dọn sạch khoá của hồ sơ mới"* — đúng với
 * `addProfile`, nhưng hàm ấy 0 chỗ gọi từ UI [ĐO grep 03/10]; bản sao trống [ĐO máy ảo E2E F2 03/10].)
 *
 * Thuần — chỗ đọc đĩa ở `:app` (`PrefsWorkspaceRepository.load`) chỉ đưa vào một `Boolean`.
 */
object WorkspaceDefault {

    /**
     * @param everSaved hồ sơ đang dùng có ít nhất một khoá `slot_*` trên đĩa.
     * @return [WorkspaceState.DEFAULT] khi hồ sơ **chưa từng lưu** (khi đó mọi ô đều trống); còn lại trả **đúng**
     *   [ws] — kể cả khi mọi ô trống.
     */
    fun resolve(ws: WorkspaceState, everSaved: Boolean): WorkspaceState =
        if (!everSaved && ws.slots.all { it is SlotContent.Empty }) WorkspaceState.DEFAULT else ws
}
