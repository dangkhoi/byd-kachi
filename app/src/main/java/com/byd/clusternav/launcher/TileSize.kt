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
class ActionTile(
    val view: View, val refresh: (CarStatus) -> Unit,
    /** 3.01 R22·C — đặt TƯỜNG MINH (TOGGLE: `> 0` bật · `0` tắt) qua CÙNG đường ghi với cú chạm ô; `null` = kiểu nút khác. */
    val set: ((Int) -> Unit)? = null,
    /** 3.01 R28 — STEP: một bước ±n qua CÙNG đường ghi với nút −/+ của ô (đọc mức thật · [ClimateAuto] · làn nền); khác ⇒ `null`. */
    val nudge: ((Int) -> Unit)? = null,
    /** 3.01 R28 — STEP: ô chữ GIÁ TRỊ của chính ô (xe 3D soi theo — một bộ chữ, không tính lại `stepText` ở chỗ khác). */
    val value: TextView? = null,
)

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
    /**
     * R22·R (owner 13/10: *"giảm text đi em, làm các icon … chữ nó làm xấu lắm"*) — ô CHỈ icon: nhãn ẩn, tên đầy đủ đi
     * vào `contentDescription` (trợ năng). Giá trị (nhiệt `22°`, quạt `4`, chấm mức) vẫn hiện — đó là số cần đọc.
     */
    val iconOnly: Boolean = false,
    /**
     * 3.01 R23 (sửa đỏ `BarScaleBudgetContractTest` c): icon của ô STEP — ô này còn hàng [−][giá trị][+] ([KachiSpace.TOUCH_TIGHT])
     * dưới icon, nên icon to [Sp.ICON_M] của R22·R làm ô STEP 63 dp tràn ô thanh DỌC 60 dp ([KachiBars.DOCK_TILE_H_VERTICAL]).
     * Ô không có hàng phụ giữ icon to (nhãn đã ẩn ⇒ dư chỗ).
     */
    val stepIconDp: Int = iconDp,
) {
    /**
     * Ô của THANH NÚT XE (`ControlDockView`). 3.1.11 DOCK-LABELS-310 — ĐÃ CÓ NHÃN lại, đúng hình 3.0 (204) đã chạy ngoài hiện trường
     * (icon [Sp.ICON_S] + nhãn hai dòng). 3.1.1 R22·R gắn `iconOnly` cho cả thanh; 3.1.1–3.1.9 là bản thử nên người dùng OTA thấy lần
     * đầu ở 3.1.10 (3.0 → 3.1.10) và owner 16/10 hỏi lại: *"taskbar … mất hết chữ rồi? trước có chữ mà"*. Lối chỉ-icon của R22·R
     * giữ cho ô nút TRONG widget ([MINI] · [GROUP]). Doc `docs/diagnostics/dock-labels-310-2026-10-16.md`.
     */
    DOCK(Sp.ICON_S, 11.5f, 15f, 12.5f, KachiBars.DOCK_PAD, Sp.RADIUS_L),

    /**
     * Ô nút trong LƯỚI NÉN của ô widget (`WidgetViews.mini`). Tách khỏi [DOCK] ở 3.1.11 (DOCK-LABELS-310): đúng số của [DOCK] 3.1.1–3.1.10
     * — R22·R chỉ icon ([Sp.ICON_M]), nhãn ở `contentDescription`; ô STEP giữ icon [Sp.ICON_S] (R23).
     */
    MINI(Sp.ICON_M, 11.5f, 15f, 12.5f, KachiBars.DOCK_PAD, Sp.RADIUS_L, iconOnly = true, stepIconDp = Sp.ICON_S),
    BIG(Sp.ICON_L, 15f, 26f, 16f, Sp.L, Sp.RADIUS_L),

    /**
     * Ô trong **hàng nút của ô nhóm** — hẹp nhất trong ba vùng: bề ngang = bề ngang ô nhóm ÷ số nút mỗi hàng.
     *
     * Icon nhỏ hơn [DOCK] một bậc ([Sp.ICON_XS]) vì ô này còn phải chứa hàng nút phụ xếp dọc; cỡ chữ giữ **y như**
     * [DOCK] — thu chữ ở một ô còn hẹp hơn thanh nút là đi ngược chuẩn đọc được của G1.
     */
    // R22·R (owner 13/10 "giảm text"): hàng nút ô nhóm CHỈ icon (tên ở contentDescription) ⇒ icon lên [Sp.ICON_M]
    // (chỗ của nhãn hai dòng nay dành cho hình).
    GROUP(Sp.ICON_M, 11.5f, 15f, 12.5f, Sp.XS, Sp.RADIUS_M, narrow = true, iconOnly = true),
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
