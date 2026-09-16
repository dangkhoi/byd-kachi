package com.byd.clusternav.launcher

import android.content.Context
import android.view.View

/**
 * CHỌN & NUÔI ô vẽ `BOARD` của ô nhóm — tách khỏi `GroupTileViews.kt` ở U9 pha 2 (tệp đó đã 492 dòng, sát trần 500).
 *
 * ## Đường cắt — theo VAI, không cắt bừa theo số dòng
 * `GroupTileView` là **bố cục** của ô nhóm (đầu ô · thân · khoảng thở · hàng nút, và phép đo hai lượt để hàng nút
 * không bị cắt). Tệp này là **sổ đăng ký bảng**: nhóm nào có bảng Canvas riêng, dựng bằng gì, và làm mới bằng cách
 * **đổ dữ liệu vào ô vẽ đã có** thay vì dựng lại. Hai vai đó thay đổi vì hai lý do khác nhau — thêm một bảng mới chỉ
 * chạm tệp này, đổi phép đo bố cục chỉ chạm tệp kia.
 *
 * ⚠⚠ Tệp này **phải có tên trong `GroupTileWiringContractTest.tileFiles`**: nó gọi `GroupBoard.` nên nó là một phần
 * tầng vẽ nhóm, và mọi `assertFalse` của bài canh đó (không tra dữ liệu · không ngưỡng · **không chép mã thành
 * viên** · một chỗ mở cổng ra xe · không mã màu hex) phải phủ nó. Có bài `tang ve nhom van la dung cac tep da khai`
 * canh chính giả định đó.
 *
 * ⚠ [SOÁT U9 pha 2] KHÔNG khai vào `GroupTileTightSpaceContractTest`: bài đó không có `assertFalse` nào quét cả
 * `tiles` — nó cắt đúng thân từng hàm (`bind` · `onMeasure` · `actionsRow`), mà cả ba đều ở `GroupTileViews.kt` /
 * `GroupTileParts.kt`. Khai thừa một tệp vào đó là để lại một lời hứa **không ai kiểm** trong KDoc, đúng thứ tệp
 * này đang cố tránh.
 *
 * ⚠ 2026-09-16 — hai bảng `RadarBoardView` (8 vùng cảm biến đỗ) và `SideBoardView` (cảnh báo hai bên xe) đã **xoá
 * hẳn** cùng toàn bộ ADAS/an toàn (owner): nhóm duy nhất dùng chúng cũng không còn. Đừng dựng lại.
 *
 * ## Vì sao "đổ dữ liệu" chứ không dựng lại ô vẽ mỗi nhịp
 * Ô nhóm được làm mới theo **nhịp trạng thái xe** (~1 lần/giây trên xe). Dựng lại một `View` Canvas mỗi nhịp là cấp
 * phát đúng thứ mà KDoc ba bảng hứa là không có, và với nhóm *Cửa & khoang* — nhóm `BOARD` **đầu tiên có hàng nút**
 * — nó còn tháo/gắn view ngay dưới ngón tay đang bấm (bệnh [SOÁT P1-1]: mất cú bấm). Vì thế mỗi bảng đều có đường
 * `set(...)`; chỉ bảng lốp là chưa (nó đến từ cổng ngoài) và đó là nợ đã ghi ở [refill].
 */
internal class GroupBoardBinder {

    private var door: DoorBoardView? = null

    /**
     * Thân ô cho nhóm `BOARD`. Nhóm chưa có bảng riêng ⇒ [strip] (thà xếp hàng ngang còn hơn ô trống).
     *
     * @param tyreBoard cổng ngoài dựng bảng lốp (bảng đó có từ W4, sống ở chỗ khác) — `null` khi chỗ gọi chưa nối.
     */
    fun build(
        context: Context,
        m: GroupBoardModel,
        data: WidgetData,
        tyreBoard: ((Context, WidgetData) -> View)?,
        strip: () -> View,
    ): View {
        door = null
        return when (m.id) {
            CapabilityGroups.TYRES.id -> tyreBoard?.invoke(context, data) ?: strip()
            // U9 pha 2 — cửa/cốp/nóc/rèm/gương đặt đúng chỗ trên hình xe (owner 2026-09-13).
            CapabilityGroups.DOORS.id -> DoorBoardView(context).also { door = it; it.set(m) }
            else -> strip()
        }
    }

    /**
     * Đổ dữ liệu mới vào ô vẽ ĐÃ dựng.
     *
     * @return `false` ⇒ chỗ gọi phải dựng lại thân ô. Hiện chỉ bảng lốp rơi vào nhánh đó: nó đến từ cổng ngoài nên ở
     *   đây không có đường đổ dữ liệu vào ô vẽ đã có. An toàn vì bộ vẽ bảng lốp là **bản vẽ thuần** (không trạng thái
     *   chạm, không vòng quay) và nhóm lốp **không có nút**. ⚠ Nợ nhỏ: cách đó cấp phát một ô vẽ mỗi nhịp — chữa được
     *   khi bộ dựng bảng lốp có đường `set(...)` như bảng cửa.
     */
    fun refill(m: GroupBoardModel): Boolean {
        door?.let { it.set(m); return true }
        return false
    }
}
