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

    /** Chiều cao thanh đầu ô (che caption cửa sổ freeform + chứa nút ⇄/✕). */
    const val HEAD_BAR = 34

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
     * Giá trị = [M]: nó là *khe giữa hai thẻ*, đúng vai trò của bậc đó.
     */
    const val SLOT_GAP = M

    /**
     * Thụt cửa sổ app vào trong ô (trái/phải/dưới).
     *
     * Bằng [SLOT_GAP] là **cố ý**: nhờ vậy rãnh quanh cửa sổ app trông liền một nhịp với rãnh giữa các ô. Nhưng
     * đây là **vai trò khác** ([SLOT_GAP] là khoảng cách *giữa hai ô*, còn đây là lề *bên trong một ô*) nên có
     * tên riêng — để sau này muốn app sát viền hơn thì sửa được mà không xê dịch cả lưới.
     */
    const val SLOT_APP_INSET = M

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

    // ══ Tiện ích ════════════════════════════════════════════════════════════════════════════════════════

    /** dp → pixel (số nguyên). */
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** dp → pixel (số thực) — cho bán kính, vốn nhận `Float`. */
    fun dpf(ctx: Context, v: Int): Float = v * ctx.resources.displayMetrics.density
}
