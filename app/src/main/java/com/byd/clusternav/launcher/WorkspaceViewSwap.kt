package com.byd.clusternav.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.byd.clusternav.launcher.behind.BehindHomePlan

/**
 * ═══ ĐẶT TẠM — đổi app của một ô TẠI CHỖ (tách khỏi `WorkspaceView.kt`, trần 500 dòng) ═════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.4.5 (A2). Bộ quyết định ([WorkspaceRenderPlanner]) đặt
 * ô vào danh sách `swap` khi lượt render này là một lượt ĐẶT TẠM App(A) → App(B) ([HomeUiState.swapNonce] mới). Ở đây:
 * host của ô còn sống ⇒ [VdAppHost.swapApp] (giữ màn ảo, KHÔNG nhả ô, KHÔNG `am force-stop` A) + đổi thẻ icon/tên phía
 * sau mặt vẽ sang B. Mở B xong, host báo (`vd`, A, B) ⇒ [WorkspaceView.onAppSwapped] ⇒ màn chính giao
 * `BehindHomeRunner.evict` đẩy A ra sau màn nhà.
 *
 * `false` (không có host / host chưa mở app / đã nhả) ⇒ `renderInternal` dựng lại ô như đường hôm nay — an toàn, chỉ
 * mất tính "giữ app cũ sống" của lượt đó.
 */
internal fun WorkspaceView.swapInPlace(i: Int, slots: List<SlotContent>): Boolean {
    val pkg = (slots.getOrNull(i) as? SlotContent.App)?.pkg ?: return false
    val host = hostAt(i) ?: return false
    val ok = host.swapApp(pkg) { vd, old, new -> post { onAppSwapped?.invoke(i, vd, old, new) } }
    if (!ok) return false
    // Thẻ icon + tên phía sau mặt vẽ (con ĐẦU của khung ô — `makeSlot` thêm nó trước host) phải nói đúng app mới: nó lộ
    // ra khi mặt vẽ bị giấu (app trong ô chết) và là thứ người dùng thấy trong lúc B đang lên.
    val frame = host.parent as? ViewGroup ?: return true
    if (frame.childCount > 0 && frame.getChildAt(0) !is VdAppHost) {
        frame.removeViewAt(0)
        frame.addView(
            appCard(pkg),
            0,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }
    return true
}

/**
 * F1 · R1.5 dòng 4/5/12 — NHÁY khung ô [i] (app của lối tắt đã ở ô đó): không dời, không mở lại — chỉ chỉ cho người dùng
 * thấy app đang ở đâu. Ô không có khung app (không có host) ⇒ không làm gì; lời nhắc chữ vẫn nói vị trí.
 */
internal fun WorkspaceView.flashSlot(i: Int) {
    val frame = hostAt(i)?.parent as? View ?: return
    frame.animate().cancel()
    frame.animate().alpha(FLASH_ALPHA).setDuration(FLASH_IN_MS)
        .withEndAction { frame.animate().alpha(1f).setDuration(FLASH_OUT_MS).start() }.start()
}

private const val FLASH_ALPHA = 0.35f
private const val FLASH_IN_MS = 140L
private const val FLASH_OUT_MS = 260L

/**
 * ═══ A5 — ỨNG VIÊN CHỖ DÀN DỰNG cho "chạy phía sau màn nhà" (spec shortcuts-autostart R0.3 · §4.2.3–4.2.4) ═══════════
 *
 * Danh sách [BehindHomePlan.Stage] từ [count] ô ĐANG HIỆN của [slots] (lớp lưu + lớp tạm): ô có nội dung App + host màn
 * ảo còn sống ([VdAppHost.stage]: màn ảo · gói đang hiện · diện tích · `SlotLiveProbe` đã đo thấy sống). Chọn ô nào là
 * việc của [BehindHomePlan.stagingSlot] (thuần, `:core`: ô sống, app ≠ X, diện tích nhỏ nhất) — hàm này chỉ ĐỌC cây
 * view. Không ô nào qua bộ chọn ⇒ `NO_STAGE`: từ chối kèm lời nhắc, 0 lệnh shell (§4.2.4 (iii)). Luồng chính.
 *
 * Ở đây (không phải `behind/StagingHost.kt` như §4.15 ghi): nó chỉ là một phép đọc cây ô của [WorkspaceView], và một tệp
 * riêng không nhắc Android sẽ bị `LayeringRulesTest` tính là "tệp thuần nằm sai ở :app" — đúng, nếu không vì [hostAt].
 */
internal fun WorkspaceView.stagingCandidates(slots: List<SlotContent>, count: Int): List<BehindHomePlan.Stage> =
    (0 until count).mapNotNull { i -> if (slots.getOrNull(i) is SlotContent.App) hostAt(i)?.stage() else null }

/**
 * F1 · R1.5 dòng 9 — kéo app của ô [i] ra TOÀN MÀN (K7 qua rào; T-M2 [ĐO]: Intent từ HOME không tách được app khỏi màn ảo,
 * K7 tách được 4/4, pid giữ). `false` = ô không có host sẵn sàng (0 lệnh). [done] trên luồng chính: đã tách được không.
 */
internal fun WorkspaceView.detachToFull(i: Int, sig: String?, homeComps: List<String>, done: (Boolean) -> Unit): Boolean =
    hostAt(i)?.detachToFull(sig, homeComps, done) ?: false

/**
 * F1 · R1.5 dòng 9 — màn nhà hiện lại (`KachiHomeActivity.onStart`) ⇒ ô nào đang có app mở toàn màn thì đưa nó về ô (K8,
 * T-M6 [ĐO]: pid giữ, 0 sự kiện tiêu điểm trên display 0). Ô không có app toàn màn ⇒ không lệnh nào.
 */
internal fun WorkspaceView.returnDetached() {
    for (i in 0 until WorkspaceState.SLOT_CAP) hostAt(i)?.returnFromFull()
}
