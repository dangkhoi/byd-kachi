package com.byd.clusternav.launcher

import android.content.Context

/**
 * ═══ G1 · T5 — THANG KHOẢNG CÁCH ════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-capability-groups.html` §4.4. **Một nguồn duy nhất** cho mọi con số dp của tầng vẽ
 * launcher.
 *
 * ## Vì sao tệp này tồn tại — [ĐO] hiện trạng trước T5
 * **369 con số viết tại chỗ** trong 19 tệp: 344 lời gọi `dp(...)`/`dpi(...)` + 25 bán kính truyền dạng `Float`
 * cho [KachiTheme.card]/[KachiTheme.gradient]. **30 giá trị phân biệt** (1·2·3·4·5·6·7·8·9·10·11·12·14·16·18·
 * 20·22·24·26·30·34·36·40·44·56·78·116·124·150·999). Riêng vai trò *"lề trong thẻ"* đã dùng **18 giá trị khác
 * nhau** cho cùng một việc. Đó là lý do owner nói *"margin, padding đang chưa OK"*: không có nhịp, nên mắt
 * đọc ra sự lệch dù từng chỗ nhìn riêng thì đều "hợp lý".
 *
 * ## ⚠ CHỌN BẬC THEO VAI TRÒ, KHÔNG THEO SỐ GẦN NHẤT
 * Đây là điều dễ làm sai nhất khi chuyển sang thang. `dp(5)` ở vai trò *khe icon–chữ* thì đúng là [XS]; nhưng
 * `dp(5)` ở vai trò *lề trong thẻ* thì **không** phải [XS] — 5dp là quá chật, và chính chỗ đó là thứ owner
 * thấy. Nó phải lên [M]. Ánh xạ đúng là *vai trò → bậc*, không phải *số cũ → số gần nhất*.
 *
 * ## Vì sao KHÔNG để số dp ở `:core`
 * Khoảng cách là việc của tầng vẽ. `:core` nói *"đang cảnh báo"* / *"nhóm này có 4 thành viên"*; chỉ `:app`
 * mới biết một thành viên cách nhau bao nhiêu. Cùng bài học với `ChipTone` của RW0 (sắc thái ở `:core`, mã màu
 * ở `:app`) — nếu `:core` giữ dp thì dự án có **hai** thang và chúng sẽ lệch ngay dòng đầu.
 *
 * ## Bài canh
 * [com.byd.clusternav.launcher.SpacingScaleContractTest] quét mã nguồn của gói `launcher` và **đỏ** khi có
 * `dp(7)` viết tại chỗ. Ngoại lệ phải khai tường minh **kèm lý do**, theo lệ `SettingsCatalog.NOT_SETTINGS`.
 */
object KachiSpace {

    // ══ THANG KHOẢNG CÁCH — lề · khe · giãn cách. Nhịp 4dp (spec §4.4) ═══════════════════════════════════
    //
    // Sáu bậc: ít hơn thì không đủ diễn đạt (nhãn sát icon vs thẻ cách thẻ), nhiều hơn thì lại thành "tự chọn
    // số" như trước T5. 4/8/12/16 là nhịp 4dp quen thuộc; 20/28 cho khoảng thở của thẻ lớn trên màn 1920.

    /** Khe hẹp nhất: icon–chữ, ô con–ô con trong cùng một lưới. Dưới mức này thì hai thứ **dính** vào nhau. */
    const val XS = 4

    /** Khe/lề nhỏ: lề trong ô con, khe giữa nút trong cùng một hàng. */
    const val S = 8

    /** **Lề trong thẻ mặc định.** Bậc dùng nhiều nhất — chọn bậc nào khi không rõ thì chọn bậc này. */
    const val M = 12

    /** Lề trong thẻ lớn, khe giữa hai thẻ. */
    const val L = 16

    /** Lề trong bảng phủ, khe giữa hai nhóm nội dung khác nhau. */
    const val XL = 20

    /** Lề ngoài cùng của bảng phủ toàn màn; khoảng nghỉ giữa hai khối lớn. */
    const val XXL = 28

    // ══ HẰNG RIÊNG — vai trò KHÔNG biểu diễn được bằng sáu bậc trên ══════════════════════════════════════
    //
    // Luật: mỗi hằng dưới đây phải nói ĐƯỢC vì sao nó không thể là một bậc của thang. Thiếu lý do thì nó chỉ
    // là "số trần có tên", tức là đúng cái bệnh T5 đi dọn.

    /**
     * Nét viền mảnh (1dp).
     *
     * **Không thể là [XS]**: 4dp không còn là *viền* mà là một cái *khung*. Nét là đường phân giới, không phải
     * khoảng cách — nó thuộc họ khác và phải mỏng nhất mà màn còn vẽ được.
     */
    const val HAIRLINE = 1

    /**
     * Nét viền nhấn (2dp) — vành ô tick khi CHƯA tích.
     *
     * **Không thể là [XS]**: cùng lý do [HAIRLINE]. Cần dày hơn [HAIRLINE] để ô tick trống còn thấy rõ trên
     * nền tối, nhưng 4dp thì thành khối đặc.
     */
    const val STROKE = 2

    // ── Bán kính góc ─────────────────────────────────────────────────────────────────────────────────────
    //
    // Bán kính KHÔNG phải khoảng cách: 12dp bán kính đứng cạnh 12dp lề là **trùng hợp**, không phải nhịp. Gộp
    // hai họ vào một thang sẽ khoá chúng vào nhau — đổi độ bo của thẻ là đổi luôn lề trong của nó.

    /** Bo góc ô con nhỏ, nút mini. */
    const val RADIUS_S = 8

    /** Bo góc ô con của lưới, hàng cài đặt. */
    const val RADIUS_M = 12

    /** **Bo góc thẻ mặc định** — ô widget, thẻ nội dung, hàng chọn. */
    const val RADIUS_L = 16

    /** Bo góc thẻ lớn / khung ô làm việc. */
    const val RADIUS_XL = 20

    /** Bo góc bảng phủ toàn màn (ngăn kéo, Cài đặt) — mềm hơn để khối lớn không "nặng". */
    const val RADIUS_XXL = 24

    /**
     * Bo tròn hết cỡ (viên thuốc).
     *
     * Không phải một khoảng cách mà là **quy ước "bo tối đa"**: [android.graphics.drawable.GradientDrawable]
     * kẹp bán kính về nửa cạnh ngắn, nên mọi số lớn hơn nửa chiều cao đều cho cùng kết quả. 999 là cách nói
     * *"bo hết"* mà không cần biết chiều cao lúc dựng.
     */
    const val RADIUS_PILL = 999

    // ── Đích chạm ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * **Đích chạm tối thiểu (48dp).**
     *
     * Cũng chính là con số đã dùng để quyết định *"chip thanh trên không nhận nút"* ở RW0: chip ~24dp, dưới
     * xa mức này, mà chạm lệch rồi bắn lệnh xe (mở khoá cửa) là việc **không hoàn lại được**.
     */
    const val TOUCH = 48

    /**
     * Đích chạm trong ô thanh nút **compact** (32dp) — CHỈ dùng ở nút −/+ bên trong ô đó.
     *
     * **Con số này được ĐO từ ràng buộc thật, không phải chọn cho đẹp.** Ô thanh nút ngang là
     * [DOCK_TILE_W]×[DOCK_TILE_H] = 84×86dp, trừ lề trong [S] hai bên còn **68×70dp**. Bề ngang phải chứa
     * `[−] [giá trị] [+]` mà giá trị kiểu "22°" đã chiếm ~28dp ⇒ mỗi nút còn ~20dp bề ngang. Bề dọc phải chứa
     * icon ([ICON_S]) + nhãn + hàng nút ⇒ hàng nút còn ~32dp. Nên đích chạm thật ở đây là **~20×32dp**, nới bề
     * dọc là phần duy nhất còn nới được.
     *
     * ⚠ **[ĐO] trên máy ảo — lần đầu tôi đặt 36dp và nó LÀM HỎNG ô**: 2×36 = 72 > 68 ⇒ chữ giá trị bị bóp,
     * `"22°"` **xuống hai dòng** ("2" / "2°"). Ghi lại vì đây là bằng chứng rằng "nới đích chạm" không phải luôn
     * an toàn: nới quá trần vật lý của ô thì đổi luôn bố cục bên trong nó.
     */
    const val TOUCH_TIGHT = 32

    // ── Cỡ icon ──────────────────────────────────────────────────────────────────────────────────────────
    //
    // Cỡ là *kích thước một vật*, không phải *khoảng cách giữa hai vật*. Một icon 16dp cạnh lề 16dp không nói
    // lên điều gì chung.

    /** Icon phụ trong một dòng chữ, icon đầu thẻ. */
    const val ICON_XS = 16

    /** Icon ô con của lưới/dải. */
    const val ICON_S = 20

    /** Icon nút trong thanh đầu ô. */
    const val ICON_M = 24

    /** Icon ô nút thanh dưới / ô chọn trong ngăn kéo. */
    const val ICON_L = 32

    /** Icon ô lớn (ngăn kéo, lưới chọn khả năng). */
    const val ICON_XL = 44

    /** Icon ứng dụng trên thẻ app chiếm cả ô — to nhất, vì nó là nội dung chính của thẻ đó. */
    const val ICON_XXL = 56

    /** Chấm chỉ báo tròn (badge "chưa kiểm trên xe", chấm màu ô). Trước T5 rải 6·7·9dp cho cùng một việc. */
    const val DOT = 8

    // ── Vạch mảnh & nét đứt ──────────────────────────────────────────────────────────────────────────────

    /**
     * Chiều cao thanh tiến trình (4dp).
     *
     * Trùng số với [XS] nhưng **khác vai trò**: đây là chiều cao một *vật vẽ được*, không phải khoảng cách.
     * Tách tên ra để sau này nới nhịp khoảng cách không âm thầm làm dày thanh tiến trình.
     */
    const val BAR_THIN = 4

    /** Đoạn nét của viền đứt (ô trống). */
    const val DASH_ON = 8

    /** Đoạn trống của viền đứt. Ngắn hơn [DASH_ON] để viền còn đọc ra là một đường liền mạch. */
    const val DASH_OFF = 4

    // ── Cỡ thành phần một-lần ────────────────────────────────────────────────────────────────────────────
    //
    // Đây là cỡ của những khối CỤ THỂ, không tái sử dụng. Khai ở đây (chứ không để số trần tại chỗ) để mọi
    // con số dp của launcher nằm đúng một tệp — đó mới là "một nguồn duy nhất".

    /**
     * **CHIỀU CAO MỘT DÒNG DỮ LIỆU ĐỌC** trong ô nhóm (72dp = 108px @1.5×).
     *
     * ## [ĐO] bệnh nó chữa — dòng dữ liệu phình 474px chứa 0.31% mực
     * Trước con số này, dải ô con nhận `weight = 1` nên nó **ăn toàn bộ** phần còn lại của ô. [ĐO] ảnh máy ảo
     * 2026-09-12, nhóm *Kính* ở khung to: hàng đọc **1162×474px** mà bên trong chỉ có 4 nhãn + 4 dấu gạch =
     * **0.31% mực**, chiếm **64%** chiều cao ô; trong khi cùng loại "dòng dữ liệu đọc" ở chỗ khác cao **74px**
     * (Kính ở khung nhỏ) và **107px** (ADAS) ⇒ ba chiều cao cho một loại nội dung.
     *
     * 72dp = 108px nằm trong khoảng kiểm toán đề nghị (96–120px) và vừa đủ cho `nhãn` + `số` xếp dọc ở cỡ chữ mới
     * (nhãn 12sp + số 17sp + lề [XS] hai đầu ≈ 68dp). Phần dư của ô **không** vào đây nữa mà đi xuống hàng nút /
     * khoảng thở — đó là ý của con số này.
     *
     * **Không thể là một bậc của thang**: đây là *chiều cao một khối bố cục* (cùng họ [LABEL_COL] / [DOCK_TILE_H]),
     * không phải khoảng cách giữa hai vật; bậc lớn nhất của thang là [XXL] = 28dp, khác hẳn bậc độ lớn.
     */
    const val READ_ROW = 72

    /**
     * **SÀN cỡ chữ NHÃN của ô vẽ Canvas** (13dp = 19.5px @1.5× ⇒ **nét cao ~15px**, mực đủ ~19px).
     *
     * ## Vì sao một ô vẽ theo tỉ lệ lại cần SÀN theo dp
     * Ô vẽ Canvas của dự án tính mọi cỡ theo tỉ lệ cạnh (`m * 0.058f`) — đúng để bất biến với dpi/cỡ ô, nhưng tỉ lệ
     * **không biết ngưỡng đọc được của mắt**. [ĐO] ảnh máy ảo 2026-09-12: bảng sơ đồ ADAS ở khung 4/12 màn có
     * `m = 231px` ⇒ nhãn 13.4px ⇒ **nét cao 10px / mực 13px**, dưới chuẩn G1 (15–16px). Tỉ lệ vẫn "đúng", chữ vẫn
     * không đọc được.
     *
     * ## Con số suy từ CHÍNH chuẩn G1, không tự chọn
     * G1 chốt nhãn ô con của nhóm ở **13.5sp** và ghi *"[ĐO] ở density 1.5: 13.5sp cho nét cao 16px"*. Ô vẽ này là
     * cùng loại nội dung (nhãn của một ô con) nên phải cùng bậc ⇒ sàn 13dp ≈ 13.5sp. Đặt thấp hơn là để một bảng
     * Canvas có chuẩn đọc riêng, thấp hơn phần còn lại của cùng một màn.
     *
     * ⚠ Bảng đã đo ở trên (*sơ đồ hai bên xe*, nhóm ADAS) **đã xoá 2026-09-16** cùng toàn bộ ADAS/an toàn (owner).
     * Phép đo giữ nguyên làm bằng chứng cho con số — sàn này nay áp cho [DoorBoardView] và [TyreBoardView].
     *
     * Nên: cỡ = `max(tỉ lệ, sàn)`, và **số HÀNG** mới là thứ co theo chỗ. Đảo lại — bóp chữ để nhồi đủ hàng — là
     * chính cái bệnh đang chữa.
     *
     * **Không thể là một bậc của thang**: đây là cỡ CHỮ, không phải khoảng cách; và thang cố ý không quản typography
     * (xem KDoc `SpacingScaleContractTest`) — nhưng một cái SÀN thì phải sống cùng chỗ với mọi con số dp khác, không
     * thì nó thành hằng trần ở tầng vẽ (đúng lỗ `SettingsPanel.RAIL_DP` đã bị bắt).
     */
    const val BOARD_LABEL_MIN = 13

    /**
     * **SÀN cỡ chữ GIÁ TRỊ của ô vẽ Canvas** (16dp = 24px @1.5×).
     *
     * Lớn hơn [BOARD_LABEL_MIN] một bậc rõ rệt (1.23×) để giữ **thứ bậc** mà kiểm toán G1 đòi: *"giá trị là thứ to
     * nhất trong ô con"*. Hai sàn bằng nhau sẽ đạt "đọc được" mà mất "đọc ra ngay đâu là số".
     */
    const val BOARD_VALUE_MIN = 16

    // ⚠ 2026-09-16 — `BOARD_ROW_MIN` (sàn chiều cao một HÀNG của bảng sơ đồ hai bên) đã XOÁ cùng `SideBoardView`:
    // nó là hằng của **riêng** bảng đó và sau lượt gỡ ADAS/an toàn (owner) không còn một chỗ gọi nào. Nếu mai có
    // bảng nhiều-hàng mới, suy lại từ [BOARD_VALUE_MIN] + [S] như cũ — đừng chép lại con số.

    /**
     * Chiều cao **số chính** của thẻ CARD = 1.5 × [READ_ROW].
     *
     * Suy ra từ [READ_ROW] chứ không tự chọn: số chính là một dòng dữ liệu **được ưu tiên**, nên nó phải to hơn một
     * dòng thường một cách có tỉ lệ. Viết dạng phép tính để đổi [READ_ROW] là nó tự theo — nếu gõ số riêng thì hai
     * con số sẽ lệch nhau đúng lúc ai đó sửa một chỗ (bẫy hai-bản-sao).
     */
    const val LEAD_ROW = READ_ROW * 3 / 2

    /**
     * Độ hở phía trên cho nội dung widget, để không bị nút ⇄ **nổi** ở đầu ô đè lên.
     *
     * **Suy ra, không tự chọn**: `XS` (lề trên của nút) + `ICON_L` (cỡ nút) + `XS` (khoảng thở). Viết dạng phép
     * cộng để đổi cỡ nút là độ hở tự đúng theo. ⚠ Trước T5 chỗ này là `dp(30)` trong khi nút chiếm 34dp ⇒ nội
     * dung widget **đã bị đè 4dp** — một lỗi nhìn-thấy-được mà không bài test nào bắt, vì nó là số trần ở một
     * tệp khác với số trần quyết định cỡ nút.
     */
    const val SLOT_HEAD_CLEAR = XS + ICON_L + XS

    /**
     * **KHE GIỮA CÁC Ô LÀM VIỆC** — và giữa vùng ô với thanh nút.
     *
     * ⚠⚠ **BỐN chỗ đọc con số này và chúng PHẢI bằng nhau**: [WorkspaceView] (vẽ khung ô) · [LauncherWindows]
     * `absoluteSlotRect` (đặt cửa sổ app on-car) · [DockAreaLayout] (khe vùng ô ↔ thanh nút) · và qua đó là vị
     * trí dải đầu ô. Trước T5 mỗi chỗ giữ **một bản sao** `dp(10)` riêng — đúng cái bẫy hai-bản-sao dự án đã
     * mắc nhiều lần. Lệch một chỗ thì **màn hình vẽ ô theo lưới mới trong khi cửa sổ app đặt theo lưới cũ** =
     * hình dạng P-bug2 (app nằm lệch khỏi ô). Đặt tên riêng để không ai sửa lẻ một chỗ.
     *
     * Giá trị = **9** (ngoài thang): owner 2026-09-14 *"chỉnh margin giữa các khung bé lại chút, tầm 75% hiện tại"*.
     * 75% của [M] (12) = 9 — khe giữa các ô hẹp lại một nhịp mà vẫn thấy rõ đường chia. Ngoài thang có chủ đích: đây
     * là con số owner chốt theo cảm nhận trên xe, không phải một bậc của thang dp.
     */
    const val SLOT_GAP = 9

    /**
     * Thụt cửa sổ app vào trong ô (trái/phải/dưới).
     *
     * Bằng [SLOT_GAP] là **cố ý**: nhờ vậy rãnh quanh cửa sổ app trông liền một nhịp với rãnh giữa các ô. Nhưng
     * đây là **vai trò khác** ([SLOT_GAP] là khoảng cách *giữa hai ô*, còn đây là lề *bên trong một ô*) nên có
     * tên riêng — để sau này muốn app sát viền hơn thì sửa được mà không xê dịch cả lưới.
     *
     * Từ 2026-09-14 bám thẳng [SLOT_GAP] (nay = 9) thay vì chép giá trị [M]: giữ đúng lời hứa "rãnh quanh app
     * trông liền một nhịp với rãnh giữa các ô" khi owner kéo khe ô về 75%.
     */
    const val SLOT_APP_INSET = SLOT_GAP

    /**
     * **Bề rộng TỐI THIỂU của cột nhãn** trong một hàng cài đặt (nhãn bên trái · điều khiển bên phải).
     *
     * ## [ĐO] bệnh nó chữa — nhãn và điều khiển cách nhau gần một mét màn hình
     * Trước đây nhãn nhận `weight = 1f`, tức nó **ăn hết** chỗ trống của hàng và đẩy dãy chip sang mép phải. Khung
     * nội dung của màn Cài đặt rộng ~950dp (1920×1080, rail 230dp), nên [ĐO] trên máy ảo: hàng *"Bố cục sẵn"* có
     * nhãn kết ở x=547 và chip đầu bắt đầu ở x=1437 — **890px (593dp) trống ở giữa**; hàng *"Cách phủ"* (2 chip)
     * còn tệ hơn: **1064px (709dp)**. Mắt không ghép được điều khiển nào thuộc nhãn nào, phải đưa ngón tay dò
     * ngang. Đây chính là thứ owner gọi là *"margin padding chưa OK"*.
     *
     * ## Vì sao là `minWidth` chứ không phải bề rộng CỐ ĐỊNH
     * Cố định thì nhãn dài hơn sẽ **bị cắt âm thầm** (`ellipsize`), và không bài test nào bắt được điều đó. Với
     * `minWidth` thì mọi nhãn hiện nay xếp thẳng một cột (điều khiển của các hàng thẳng hàng nhau — thứ khiến một
     * danh sách 7 hàng đơn vị đọc được), còn nhãn dài hơn trong tương lai thì **đẩy** điều khiển sang phải chứ
     * không mất chữ. Suy giảm an toàn thay vì mất dữ liệu.
     *
     * ## Vì sao KHÔNG thể là một bậc của thang
     * Đây là **bề rộng một cột bố cục**, cùng họ với [ART] / [PROGRESS_W] / [DOCK_TILE_W] — không phải khoảng cách
     * giữa hai vật. Bậc lớn nhất của thang là [XXL] = 28dp, không cùng bậc độ lớn.
     *
     * Giá trị 150dp = 225px @1.5×: nhãn dài nhất đang dùng (*"Viền đặt thanh"*, 14 ký tự @13.5sp ≈ 140px) nằm gọn
     * một dòng và còn ~85px dư, trong khi vẫn chừa ~800dp cho dãy chip dài nhất (5 lựa chọn bố cục sẵn).
     */
    const val LABEL_COL = 150

    /**
     * **Bề rộng cột rail nhóm** của màn Cài đặt.
     *
     * 230dp ở 1920×1080 (density 1.5 ⇒ 1280dp ngang) chừa ~950dp cho khung nội dung — đủ cho lưới 5 ô ngang của
     * nhóm *"Màn hình chính"* mà nhãn nhóm dài nhất (*"Dẫn đường · Cụm · Phím"*) vẫn nằm trên một dòng.
     *
     * ⚠ [SOÁT G1] Trước lượt soát này nó là `SettingsPanel.RAIL_DP` — một **hằng cỡ nằm ngoài thang**. Bài canh
     * không thấy nó (nó là định danh, không phải số trần), nên "một thang, một chỗ" chỉ đúng trên giấy: cùng một
     * màn Cài đặt có cột nhãn khai trong [KachiSpace] mà cột rail khai ở tầng vẽ. Nay cả hai cùng chỗ, và có bài
     * canh [khong duoc truyen hang co ngoai thang vao dp] chặn hằng mới mọc ra ngoài.
     *
     * **Không thể là một bậc của thang**: cùng họ [LABEL_COL] — bề rộng một cột bố cục, không phải khoảng cách.
     */
    const val RAIL_COL = 230

    /**
     * **Lề NGANG của nội dung một bảng phủ toàn màn** ([XXL] + [XXL] = 56dp = 84px @1.5×).
     *
     * ## Suy ra từ bảng Cài đặt, không tự chọn
     * [SettingsPanel] là bảng phủ toàn màn duy nhất đã được owner duyệt bằng ảnh: nó là một thẻ có **lề ngoài**
     * [XXL] rồi **lề trong** [XXL] ⇒ nội dung bắt đầu ở x = 28 + 28 = 56dp ([ĐO] ảnh: **x = 84px**). Bảng vẽ bố
     * cục ([LayoutEditorPanel]) là bảng phủ toàn màn ĐỤC (không thẻ, không scrim) nên không có hai lớp lề để cộng
     * — nó phải khai thẳng cột nội dung, và cột đó phải TRÙNG với bảng kia, nếu không thì hai bề mặt toàn màn của
     * cùng một app bắt đầu ở hai cột khác nhau (R-UI (h): *"bảng vẽ bố cục dùng cùng lề panel (x84)"*; [ĐO] trước
     * bản vá nó ở **x = 30px**).
     *
     * Viết dạng **phép cộng** chứ không gõ 56: đổi [XXL] thì cả hai bề mặt đi cùng nhau. Đây chính là chỗ mà bẫy
     * hai-bản-sao sẽ xuất hiện nếu gõ số.
     *
     * **Không thể là một bậc của thang**: cùng họ [LABEL_COL] / [RAIL_COL] — bề rộng một khối bố cục.
     */
    const val PANEL_INSET = XXL + XXL

    /**
     * **Bề rộng TỐI THIỂU của một chip** trong dãy segmented (66dp).
     *
     * ## [ĐO] bệnh nó chữa — chip 1–2 ký tự trông như HÌNH TRÒN
     * Chip bo [RADIUS_PILL] (bo hết cỡ) nên dáng của nó do **tỉ lệ rộng/cao** quyết định. [ĐO] ảnh máy ảo
     * 2026-09-12: chip *"m"* / *"ft"* / *"°C"* đo **72×66px** ⇒ tỉ lệ **1.09** — mắt đọc ra một hình tròn, lạc
     * khỏi họ viên thuốc của các chip dài cùng hàng. Trước đó `minWidth` lấy [TOUCH] (48dp) vì lý do *đích chạm*,
     * nhưng đích chạm và **dáng** là hai ràng buộc khác nhau và 48dp chỉ thoả cái thứ nhất.
     *
     * Suy từ chiều cao chip chứ không tự chọn: chip cao [ICON_XL] = 44dp, tỉ lệ tối thiểu để còn đọc ra viên
     * thuốc là 1.5 ⇒ 44 × 1.5 = **66dp**. Đổi chiều cao chip thì con số này phải tính lại theo cùng tỉ lệ.
     *
     * **Không thể là một bậc của thang**: đây là bề rộng của MỘT VẬT (cùng họ [LABEL_COL] / [DOCK_TILE_W]),
     * không phải khoảng cách giữa hai vật; bậc lớn nhất của thang là [XXL] = 28dp.
     */
    const val CHIP_MIN_W = 66

    /**
     * **Bề rộng TỐI ĐA của một dòng chú thích** (`SettingsRows.note`, 600dp).
     *
     * ## [ĐO] bệnh nó chữa — dòng chú thích dài 1358px
     * Khung nội dung của màn Cài đặt rộng ~950dp (1920×1080, rail [RAIL_COL]), và `note()` là `MATCH_PARENT` nên
     * [ĐO] ảnh máy ảo 2026-09-12: một dòng chú thích trải **1358px ≈ 150 ký tự/dòng**. Chuẩn sắp chữ là 45–90 ký
     * tự/dòng — quá ngưỡng thì mắt **trượt dòng** khi xuống hàng (mất mốc quay về đầu dòng).
     *
     * 600dp = 900px @1.5×, ở [KachiType.CAPTION] 12sp (bề rộng trung bình ~7px/ký tự) cho **~90 ký tự/dòng** —
     * đúng cận trên của khoảng dễ đọc, và vẫn là `maxWidth` (không phải bề rộng cố định) nên màn hẹp hơn thì dòng
     * tự co, không có chỗ nào bị cắt.
     *
     * **Không thể là một bậc của thang**: cùng họ [LABEL_COL] — bề rộng một khối bố cục, không phải khoảng cách.
     */
    const val NOTE_MAX_W = 600

    // ── Chiều cao của view TỰ VẼ nhúng vào Cài đặt (`SettingsRows.embed`) ───────────────────────────────
    //
    // ⚠ Ba số này là **bề cao một khối bố cục**, cùng họ [LABEL_COL]/[RAIL_COL]/[NOTE_MAX_W] — KHÔNG phải một
    // bậc khoảng cách. Chúng phải nằm ở đây chứ không viết tại chỗ gọi vì `embed(view, heightDp)` nhận dp thô:
    // một số trần ở chỗ gọi **không** bị `SpacingScaleContractTest` bắt (nó chỉ soi đối số của `dp(`/`dpi(`),
    // tức đúng cái lỗ mà `px()` đã lách qua một lần (xem KDoc `dpHelperNames` của bài canh đó).

    /**
     * Sơ đồ ghế **và** khung KÉO-THẢ vị trí trên cụm (biển báo tốc độ · bong bóng VietMap).
     *
     * ⚠ Khung kéo-thả TRƯỚC ĐÂY có bậc riêng `EMBED_TALL = 200`. Bỏ đi ở lượt soát ảnh v2 (R4 — nhóm *Dẫn đường*
     * đo được **2.68 màn cuộn**, trần là 2): khung đó **letterbox** cụm 1920×720 nên hạ chiều cao chỉ làm hình
     * chiếu nhỏ lại, KHÔNG cắt mất phần nào của cụm — [ĐO] sau khi hạ, marker vẫn tròn, vẫn kéo được, biên khung
     * vẫn thấy. Một bậc ít hơn cũng là một chỗ ít hơn để hai khung cùng loại trôi khỏi nhau.
     */
    const val EMBED_M = 160

    /** Đồng hồ PM2.5 — một cung tròn + một con số; cao hơn nữa chỉ là chỗ trống. */
    const val EMBED_S = 120

    /**
     * Ô xem trước cụm ở nhóm *Chiếu cụm* (S3 · R2b).
     *
     * Thấp hơn hẳn hai bậc trên vì nó **không có gì bên trong để nhìn**: một mặt cụm bo góc, một vạch chia, một
     * dòng tên app. [ĐO] ảnh máy ảo 2026-09-13 ở bậc `EMBED_S`: ô cao 120dp trải hết bề rộng thẻ đọc ra như một
     * mảng màu trống, không như một cụm đồng hồ thu nhỏ. Tỉ lệ cụm thật là 1920×720 (2.67:1) nên ở bề rộng thẻ
     * ~1350px thì 72dp đã cao hơn tỉ lệ đó — đủ để nhận ra hình, không thừa chỗ.
     */
    const val EMBED_PREVIEW = 72

    /** Ảnh bìa nhạc (vuông). */
    const val ART = 80

    /** Bề rộng thanh tiến trình của widget nhạc. */
    const val PROGRESS_W = 152

    /** Bề dày thanh nút khi nằm ngang (trên/dưới). */
    const val DOCK_THICK = 116

    /** Bề rộng thanh nút khi nằm dọc (trái/phải). */
    const val DOCK_WIDE = 124

    // ── Cỡ Ô của thanh nút ───────────────────────────────────────────────────────────────────────────────
    //
    // ⚠ Bốn số này trước T5 nằm trong một biểu thức `dpi(ctx, if (dọc) 100 else 84)` nên **bộ đếm số trần đầu
    // tiên của T5 KHÔNG thấy chúng** (mẫu tìm chỉ khớp số đứng một mình trong ngoặc). Tìm ra khi đọc mã để sửa
    // ô stepper. Bài canh đã được sửa để quét *mọi* số trong đối số của `dp(...)`, kể cả trong biểu thức.

    /** Bề rộng ô thanh nút khi thanh nằm NGANG. Trần vật lý cho mọi thứ bên trong ô — xem [TOUCH_TIGHT]. */
    const val DOCK_TILE_W = 84

    /** Bề cao ô thanh nút khi thanh nằm NGANG. */
    const val DOCK_TILE_H = 86

    /** Bề rộng ô thanh nút khi thanh nằm DỌC (ô rộng hơn vì cột hẹp nên chữ cần chỗ). */
    const val DOCK_TILE_W_VERTICAL = 100

    /** Bề cao ô thanh nút khi thanh nằm DỌC (thấp hơn để xếp được nhiều ô trong một cột). */
    const val DOCK_TILE_H_VERTICAL = 70

    /**
     * Thụt TRÊN cho caption cửa sổ freeform (24dp ≈ 36px @1.5×).
     *
     * **Đây là số ĐO của nền tảng, không phải lựa chọn thiết kế**: `DecorCaptionView` của AOSP cao chừng đó và
     * app thường KHÔNG gỡ được. Nó phải đứng riêng vì nếu ai nới nhịp khoảng cách của thang thì **không** được
     * kéo theo con số này — đổi nó là đổi một cách lách nền tảng đã đo trên máy thật.
     */
    const val CAPTION_INSET = 24

    /**
     * Cao của dải phủ che caption freeform, tính từ MÉP TRÊN cửa sổ app (dp). Caption do hệ vẽ, cao khác nhau theo
     * ROM: [ĐO 2026-09-13 máy ảo google_apis 240dpi] cửa sổ app top=124px, caption xám tới y=187 ⇒ 63px = **42dp**;
     * trên xe [CAPTION_INSET] = 24dp là số đã đo. Lấy trần 44dp để phủ hết cả hai mà không phải đo lại từng ROM;
     * [OverlayHeads] cộng thêm khoảng từ mép trên ô tới mép trên cửa sổ app. Đổi số này là đổi một phép đo, không
     * phải nhịp thang — nên nó đứng riêng như [CAPTION_INSET].
     */
    const val CAPTION_COVER = 44

    // ══ Tiện ích ════════════════════════════════════════════════════════════════════════════════════════

    /** dp → pixel (số nguyên). */
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** dp → pixel (số thực) — cho bán kính, vốn nhận `Float`. */
    fun dpf(ctx: Context, v: Int): Float = v * ctx.resources.displayMetrics.density
}
