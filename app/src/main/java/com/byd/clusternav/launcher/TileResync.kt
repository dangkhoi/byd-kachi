package com.byd.clusternav.launcher

import android.view.View
import java.util.WeakHashMap

/**
 * ═══ 2.87 · R-FL2 — ô nút VẼ LẠI khi bảng lệnh cuối đổi bởi PHÍM / GIỌNG NÓI ═══════════════════════════════════
 *
 * Bật/tắt + lựa chọn của ô nay nằm ở [ControlLastSent] — CÙNG bảng mà phím Đảo + giọng nói ghi (`ControlTileState`).
 * Nhưng ô chỉ vẽ lại ở ba lúc: lúc dựng, cú chạm của chính nó, và nhịp đọc xe (`refresh`); với nút KHÔNG có `readKey`
 * (cốp · rèm · đèn đọc · lọc bụi…) nhịp đọc xe không có số nào ⇒ tới 2.86 `refresh` trả về sớm. Hệ quả nếu để nguyên:
 * phím Đảo đóng cốp xong ô vẫn tô "đang mở", và cú chạm kế (đọc bảng: đóng ⇒ MỞ) làm đúng điều NGƯỢC với hình — ô nói
 * một đằng, làm một nẻo.
 *
 * Chữa, không thêm dòng nào vào `ControlTileFactory` (trần 500 dòng — spec §3 *"sửa cũ chỉ là bọc cùng dòng"*):
 *  - `look` (cửa DUY NHẤT áp trạng thái lên ô) ghi chỉ số vừa vẽ của từng ô vào đây ([drew]);
 *  - `refresh` của TOGGLE / COVER / SELECT hỏi [stale]: hình ≠ bảng (hoặc ≠ số xe vừa đọc) ⇒ vẽ lại.
 * Ô nút có readKey đọc được thì nhịp đọc xe vẫn là sự thật như cũ; dòng này chỉ thêm *"hình phải khớp số đã quyết"*.
 *
 * Bảng YẾU theo View (`WeakHashMap`): ô bị gỡ thì mục tự rơi, không giữ cây view nào sống. CHỈ luồng chính chạm —
 * `look` và `refresh` đều chạy trên luồng vẽ (đường ghi nền của ô chỉ `post` về).
 *
 * Nhịp: `refresh` chạy mỗi lần trạng thái xe ĐỔI (`ControlDockView.setCarStatus` · `WidgetViews.refreshRead`) — [SUY]
 * trên xe đang nổ máy là vài giây một lần; [CHƯA BIẾT] khi mọi số liệu xe đứng yên hoàn toàn (luồng trạng thái gộp
 * giá trị trùng) — khi ấy ô theo kịp ở lần đổi kế tiếp hoặc lần dựng lại. Soát vòng 2: dòng từ `:wake` (cầu
 * `ControlSentRelay`) thì KHÔNG phải chờ — `ControlLastSent.relayed` đổi ⇒ `resyncTiles` (màn chính) chạy lại `refresh` ngay.
 * Lệnh ghi TRONG tiến trình chính bằng phím/giọng nói vẫn theo nhịp trên (giới hạn có từ trước, ngoài phạm vi bản vá này).
 */
internal object TileResync {

    private val drawn = WeakHashMap<View, Int>()

    /** `look` vừa vẽ [tile] ở chỉ số [index] (`null` — ô không mang chỉ số — bỏ qua). */
    fun drew(tile: View, index: Int?) {
        if (index != null) drawn[tile] = index
    }

    /** [now] nếu hình đang vẽ của [tile] KHÁC [now] (cần vẽ lại), `null` nếu đã khớp hoặc ô chưa từng vẽ. */
    fun stale(tile: View, now: Int): Int? = now.takeIf { drawn[tile]?.let { d -> d != now } == true }
}
