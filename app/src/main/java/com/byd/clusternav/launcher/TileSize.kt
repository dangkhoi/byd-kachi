package com.byd.clusternav.launcher

import android.view.View
import android.widget.TextView
import com.byd.clusternav.launcher.KachiSpace as Sp

// ⚠ [WP2 · 2026-09-20] Tách khỏi `ControlTileFactory.kt` vì tệp đó đã **539 dòng**, quá trần 500 của dự án
// (CLAUDE.md §4.1) TRƯỚC khi WP2 thêm một dòng nào. Đường cắt theo VAI, không cắt cho vừa số dòng — cùng lệ
// `ReadTile.kt` (ô đã dựng) và `ControlTileState.kt` (trạng thái dùng chung):
//   • `ControlTileFactory` = **bộ dựng** ô;
//   • ở đây là **hai kiểu dữ liệu** mà bộ dựng nhận/trả — một tay cầm (`ActionTile`) và một bảng cỡ theo vùng
//     (`TileSize`). Cả hai không biết gì về `ControlDef`, không gọi bộ dựng, và được đọc từ cả ba vùng đặt ô
//     (thanh nút · ô giữa màn · hàng nút của ô nhóm).
//
// Chuyển NGUYÊN VĂN: cùng package, cùng tên, cùng thứ tự thuộc tính, cùng KDoc ⇒ 0 chỗ gọi phải sửa.

/**
 * Ô HÀNH ĐỘNG đã dựng + cách cập nhật số từ xe.
 *
 * [refresh] nhận [CarStatus] và cập nhật hiển thị TẠI CHỖ từ [CarStatus.controls] (không dựng lại ô — ràng buộc
 * C5). No-op cho COVER/BUTTON. Chỗ đặt ô (thanh nút · ô giữa màn) giữ [refresh] rồi gọi mỗi lần trạng thái xe đổi.
 */
class ActionTile(val view: View, val refresh: (CarStatus) -> Unit)

/**
 * Cỡ ô theo VÙNG. [BIG] cho ô giữa màn (khung to hơn nhiều nên chữ/icon phải to theo, không thì ô trông hụt).
 *
 * ## ⚠ T5 — số của [DOCK] KHÔNG còn "y hệt bản cũ"
 * KDoc trước ghi *"con số của DOCK là y hệt bản cũ nằm trong `ControlDockView` (22dp icon · đệm 8dp · bo 14dp)
 * ⇒ thanh nút không đổi một pixel"*. Từ T5 điều đó **hết đúng** và cố ý: icon 22 → [Sp.ICON_S] (20),
 * bo 14 → [Sp.RADIUS_L] (16), đệm 8 giữ nguyên ([Sp.S]). Lý do là chính bệnh T5 đi dọn — 22 và
 * 14 nằm ngoài mọi nhịp, nên thanh nút lệch nhịp với phần còn lại của màn.
 *
 * Ghi lại ở đây thay vì xoá câu cũ: bất biến "không đổi một pixel" từng là **có thật** và là lý do bộ dựng ô
 * được rút ra khỏi [ControlDockView] an toàn. Ai đọc sau cần biết nó đã được cố ý bỏ, chứ không phải bị quên.
 *
 * ## ⚠ WP5 (2026-09-20) — lề trong của [DOCK] còn [KachiBars.DOCK_PAD] (4dp, trước là [Sp.S] = 8dp)
 * Không phải chuyện thẩm mỹ: ô thanh nút vừa hạ còn 85 % (`71×73` ngang · `83×60` dọc) trong khi bề dày thanh hạ
 * còn 80 %, nên [ĐO số học] ô **dọc** chỉ chứa nổi nội dung nếu lề trong hạ cùng — xem KDoc
 * [KachiBars.DOCK_TILE_H_VERTICAL] cho phép cộng đầy đủ. Ô GROUP giữ [Sp.XS] như cũ (nó đã ở mức đó), ô BIG
 * không đụng.
 */
enum class TileSize(
    val iconDp: Int,
    val labelSp: Float,
    val valueSp: Float,
    val optionSp: Float,
    val padDp: Int,
    /** Bo góc, **dp dạng Int** từ T5 (họ `Sp.RADIUS_*`) — trước đây là `Float` với số trần 14f/16f. */
    val radius: Int,
    /**
     * Ô **HẸP** — bề ngang do vùng chia ra, không phải cỡ cố định.
     *
     * Ba thứ đổi theo, và cả ba đều là [ĐO] từ ảnh máy ảo 2026-09-12 (ô 82px ở khung 4/12 màn):
     *  1. **Nhãn dùng bản NGẮN** ([ControlDef.displayShortLabel]) — nhãn đầy bị cắt `"Window front-ri…"` /
     *     `"Kính trước-p…"`, làm hai ô kính trước đọc ra y hệt nhau.
     *  2. **Nút phụ của ô COVER xếp DỌC** — xếp ngang thì mỗi nút còn ~32px, chữ `"Đóng"`/`"Close"` bị cắt cứng thành
     *     `"Đ"`/`"C"` (không cả dấu `…`). Xếp dọc thì mỗi nút được TRỌN bề ngang ô ⇒ chữ nguyên vẹn. Giá phải trả là
     *     bề cao, và đó là thứ ô nhóm **có** sau khi lưới đọc biết nhường (xem `GroupTileView.onMeasure`).
     *  3. **Lề trong nhỏ hơn** ([KachiSpace.XS] thay vì [KachiSpace.S]) — lấy lại 12px bề cao cho chính việc trên.
     */
    val narrow: Boolean = false,
) {
    DOCK(Sp.ICON_S, 11.5f, 15f, 12.5f, KachiBars.DOCK_PAD, Sp.RADIUS_L),
    BIG(Sp.ICON_L, 15f, 26f, 16f, Sp.L, Sp.RADIUS_L),

    /**
     * Ô trong **hàng nút của ô nhóm** — hẹp nhất trong ba vùng: bề ngang = bề ngang ô nhóm ÷ số nút mỗi hàng.
     *
     * Icon nhỏ hơn [DOCK] một bậc ([Sp.ICON_XS]) vì ô này còn phải chứa hàng nút phụ xếp dọc; cỡ chữ giữ **y như**
     * [DOCK] — thu chữ ở một ô còn hẹp hơn thanh nút là đi ngược chuẩn đọc được của G1.
     */
    GROUP(Sp.ICON_XS, 11.5f, 15f, 12.5f, Sp.XS, Sp.RADIUS_M, narrow = true),
}

/**
 * Nhãn của ô **CHỈ-BẬT-TẮT / BẤM-MỘT-PHÁT** luôn chiếm ĐÚNG hai dòng, dù chữ chỉ có một dòng.
 *
 * ## ⚠⚠ [KIỂM TOÁN UX mục 6] Vì sao phải cố định, không phải rút ngắn nhãn
 * [ĐO] trong 9 ô của thanh nút, *"Khoá / mở khoá"* là nhãn **duy nhất** xuống hai dòng ⇒ nội dung ô đó cao hơn
 * các ô khác một dòng, mà ô căn giữa dọc ⇒ **icon của nó lệch trục 4–7px** so với tám ô còn lại. Rút ngắn nhãn
 * chữa được ĐÚNG ô này và **không chữa nguyên nhân**: 64 nút, nhãn nào cũng có thể xuống dòng ở cỡ ô khác
 * (thanh dọc rộng 100dp vs ngang 84dp), và lần sau sẽ không ai nhớ luật này.
 *
 * Chốt chỗ cho hai dòng thì chiều cao nội dung **không còn phụ thuộc độ dài chữ** ⇒ icon nằm cùng trục do cấu
 * tạo. Chỉ áp cho hai kiểu ô mà nhãn là phần TỬ CUỐI: ô có thêm hàng giá trị (STEP/COVER/SELECT) thì thêm một
 * dòng nữa sẽ đẩy hàng giá trị ra ngoài trần 86dp của ô — đúng bẫy [KachiSpace.TOUCH_TIGHT] đã đo.
 */
internal fun reserveTwoLines(label: TextView): TextView = label.apply { minLines = 2 }

/**
 * Hình của một nút ở cỡ [sizeDp]: theo [ControlDef.icon]; tên chưa map (`ic-adas`/`ic-drive`/`ic-mirror`…) ⇒ lùi
 * về hình đại diện của [Domain]. Tra qua [KachiIcons.res] nên mặt lớn tự lấy biến thể 32/48dp.
 */
internal fun controlIconRes(def: ControlDef, sizeDp: Int): Int {
    val r = KachiIcons.res(def.icon, sizeDp)
    return if (r != 0) r else KachiIcons.res(WidgetCatalog.iconFor(def.domain), sizeDp)
}
