package com.byd.clusternav.launcher

/**
 * ═══ HÌNH HỌC CỦA HAI THANH KHUNG MÀN CHÍNH — thanh trên + thanh nút xe ══════════════════════════════════════
 *
 * **Phần tách ra của [KachiSpace]** (cùng vai, cùng luật) — spec `docs/specs/kachi-ux-overhaul.html` §WP5.
 *
 * ## Vì sao tách khỏi [KachiSpace] (cắt theo VAI, không cắt cho vừa số dòng)
 * [KachiSpace] là **thang dùng chung cho mọi bề mặt** (lề · khe · bán kính · cỡ icon). Ở đây là **cỡ của hai khối
 * bố cục cụ thể**, và chúng có một tính chất mà phần còn lại của thang không có: **chúng suy lẫn nhau** — bề dày
 * thanh = ô + lề ô + lề thanh, bề cao thanh trên = nút + lề dọc. Đứng cạnh nhau thì không ai đổi được một nửa của
 * một phép trừ. Lý do PHẢI tách bây giờ: [KachiSpace] đã **496 dòng** trước khi WP5 thêm một dòng nào (trần 500,
 * CLAUDE.md §4.1). Cùng lệ `ControlTileFactory` → `TileSize.kt`/`ReadTile.kt`/`ControlLevelBar.kt` ở WP2.
 *
 * ## Đây VẪN là thang, không phải tầng vẽ
 * Tệp này **chỉ khai hằng**: 0 lời gọi `dp(`/`dpi(`, 0 import Android, 0 phép vẽ. Đó là điều kiện để
 * `SpacingScaleContractTest` nhận nó cùng hạng với [KachiSpace] thay vì coi nó là *"hằng cỡ khai ở tầng vẽ"* —
 * và bài canh có phép kiểm riêng ép đúng tính chất này (nếu tệp này mọc ra một lời gọi `dp(` thì nó đã thành tầng
 * vẽ và lối nhận ngoại lệ phải đóng lại).
 *
 * ## ⚠⚠ WP5 hạ tỉ lệ theo lời owner — hai bộ số, hai mốc gốc khác nhau
 * Owner 2026-09-20: *"taskbar kích thước 80 % / nội dung 85 %; header cao 75 % / nội dung 70 % / nút App+Voice+
 * profile 70 % (thông tin giữ nguyên)"*. Mọi hằng dưới đây ghi **số gốc trước WP5** để phần trăm kiểm lại được
 * bằng số học, không phải bằng lời.
 */
object KachiBars {

    // ══ THANH TRÊN (header) ═════════════════════════════════════════════════════════════════════════════
    //
    // Mốc gốc: trước WP5 thanh trên **không khai chiều cao** — nó là `WRAP_CONTENT`, nên bề cao thật =
    // (vật cao nhất) + lề dọc. Vật cao nhất là ba pill + chip hồ sơ, cả bốn khai `minimumHeight =
    // KachiSpace.TOUCH` (48) ⇒ bề cao gốc = 48 + 2 × [KachiSpace.XS] = **56dp**. Đó là con số mà 75 % dưới
    // đây quy về, và nó suy ra được từ mã (không phải một phép đo ảnh).

    /**
     * **ĐÍCH CHẠM của nút trên thanh trên** (Nói · Ứng dụng · Cài đặt · chip hồ sơ) — 70 % của [KachiSpace.TOUCH].
     *
     * `48 × 0.7 = 33.6` → **34** (làm tròn LÊN: ở đây mỗi dp là đích chạm, và 34 còn khớp đúng phép cộng của
     * [HEADER_H] bên dưới — 33 thì bề cao thanh ra 41dp = 73.2 % chứ không phải 75 %).
     *
     * ## ⚠⚠ ĐÁNH ĐỔI AN TOÀN — owner cần biết, và nó KHÔNG phải chỗ quên
     * 34dp **nhỏ hơn** mức tối thiểu 48dp mà chính dự án dùng làm lý do cấm chip thanh trên bắn lệnh xe (xem KDoc
     * [TopStripConfig]). Giảm nhẹ ở ba điểm, và cả ba đều là sự thật đo được chứ không phải lời an ủi:
     *  1. **Không nút nào ở đây bắn lệnh xe.** Ba pill mở một bề mặt (phiên nghe · danh sách app · màn Cài đặt) và
     *     chip hồ sơ mở một bộ chọn. Bấm nhầm = mở sai một bảng rồi bấm Back — hoàn lại được ngay, khác hẳn *"mở
     *     khoá cửa"*.
     *  2. **34dp vẫn lớn hơn cỡ hình** (box icon 18dp): đích chạm không co theo icon, vẫn khai TƯỜNG MINH cả hai
     *     chiều — đó là tính chất mà `TopStripSurfaceContractTest` canh, và nó không đổi.
     *  3. **Bốn nút cùng hộp 34 × 34** (2.76 · R11): trước đó pill là `WRAP_CONTENT` nên bề ngang bị hình gốc 24dp
     *     đẩy lên 40 [ĐO máy ảo 27/09: 60×51 px vs chip hồ sơ 51×51] — nay `KachiTopStrip.pillLp` khai cả hai chiều.
     * Muốn trả về 48dp thì đổi đúng hằng này (thanh sẽ cao lại 56dp) — không có chỗ thứ hai nào phải sửa.
     */
    const val HEADER_BTN = 34

    /**
     * **LỀ TRONG của nút thanh trên** — quyết cỡ hình được vẽ.
     *
     * Hình lấy `ScaleType.FIT_CENTER` nên nó co về đúng hộp nội dung = [HEADER_BTN] − 2 × giá trị này =
     * `34 − 16` = **18dp** (so với 24dp gốc = **75 %** của [KachiSpace.ICON_M]). Cố ý KHÔNG đặt một hằng
     * `HEADER_ICON` riêng: hai con số nói về cùng một cái hộp thì chúng sẽ lệch nhau ở đúng lần ai đó sửa một
     * bên (bẫy hai-bản-sao). Một lề, một phép trừ, một sự thật.
     *
     * Bằng [KachiSpace.S] là **trùng hợp có kiểm**: nếu sau này lề trong đổi vai thì sửa ở đây, đừng sửa bậc thang.
     */
    const val HEADER_BTN_PAD = KachiSpace.S

    /**
     * **BỀ CAO THANH TRÊN** = nút + lề dọc [KachiSpace.XS] hai đầu = `34 + 8` = **42dp** = đúng **75 %** của 56dp
     * gốc (R5.2).
     *
     * ## Viết dạng phép CỘNG, và khai chiều cao TƯỜNG MINH thay vì để `WRAP_CONTENT`
     * Hai lý do:
     *  1. **Phép cộng** ⇒ đổi [HEADER_BTN] là bề cao tự theo. Gõ `42` thì hai con số rời nhau và *"nút 70 %"* với
     *     *"thanh 75 %"* sẽ mâu thuẫn im lặng ngay lần đầu ai đó chỉnh một cái.
     *  2. **Tường minh** ⇒ *"cao 75 %"* là một tính chất **đo được trên ảnh chụp**, không phải hệ quả may mắn của
     *     việc vật nào tình cờ cao nhất. [ĐO số học] nội dung vừa khít: nút 34 là vật cao nhất (chữ đồng hồ
     *     [KachiType.SECTION] 16sp ≈ 19dp nét, chip [KachiType.BODY] 13.5sp ≈ 16dp, hình 18dp) ⇒ 34 + 8 = 42 =
     *     trần này, **không cắt gì**. Nếu sau này thêm một vật cao hơn 34dp vào thanh thì nó sẽ bị cắt — và đó là
     *     điều đúng để xảy ra, vì nó phá lời hứa *"thanh cao 42dp"* mà owner vừa chốt.
     */
    const val HEADER_H = HEADER_BTN + 2 * KachiSpace.XS

    /**
     * **Đĩa chữ-cái-đầu của chip hồ sơ** — 70 % của [KachiSpace.ICON_L] (`32 × 0.7 = 22.4` → **22**).
     *
     * Thuộc nhóm *"nút … profile 70 %"* của R5.2: chip hồ sơ là một nút, và đĩa là phần vẽ to nhất trong nó. Giữ
     * 32dp trong một chip cao 34dp sẽ làm đĩa ăn gần trọn bề cao ⇒ chip trông như một cái nút tròn dính hai mép.
     *
     * ⚠ Con số này đi kèm một **ràng buộc hình học** không nằm ở đây: đĩa phải ĐỒNG TÂM với nút [HEADER_BTN], nên
     * `HEADER_AVATAR + 2 × KachiSpace.XS ≤ HEADER_BTN` (UX1 · R1). Luật được thi hành ở `KachiTopStrip.profileChip`
     * và canh bằng số học ở `BarOrderWiringContractTest.dia ho so va le doi xung nam trong dich cham` — đổi con số
     * này thì phải đọc cả hai chỗ đó.
     */
    const val HEADER_AVATAR = 22

    /**
     * **Khe ICON ↔ CHỮ bên trong MỘT chip dữ liệu** của thanh trên (UX6).
     *
     * ## [ĐO máy ảo 2026-09-27] khe này đang là **0**, và đó là một lỗi chứ không phải một lựa chọn
     * `uiautomator dump` ở 1920×1080 · density 240 (1.5 px/dp): chip *"Bụi mịn trong xe"* rộng **52px**, chip
     * chỉ-icon (sấy trước) rộng **36px**. Phép cộng của `TextView`: `lề trái + hộp icon + khe + chữ + lề phải`
     * ⇒ chip chỉ-icon `6 + 24 + 0 + 0 + 6 = 36` ✓, còn chip có chữ `6 + 24 + khe + chữ + 6 = 52`. Chip không-icon
     * *"—°C"* rộng 48px ⇒ chữ `"—°C"` = 36px ⇒ chữ `"—"` = 16px ⇒ **khe = 0**. Owner: *"khi có label, label nó
     * sát icon quá"*.
     *
     * Nguyên nhân nằm ở **thứ tự**, không ở con số: `KachiTopStrip.applyChipFace` đọc `v.text` để quyết có đệm
     * hay không, mà lượt làm mới đặt CHỮ **sau** đó ⇒ lượt đầu chữ còn rỗng ⇒ đệm 0, rồi khoá `tag` giữ nguyên
     * mãi. Vì thế khe phải được truyền VÀO theo chữ sắp hiện (xem KDoc `applyChipFace`), không đọc lại từ view.
     *
     * Bằng [KachiSpace.S] là **trùng hợp có kiểm** (lệ [HEADER_BTN_PAD]): đây là vai *"khe trong một nhóm"*,
     * đổi vai thì sửa ở đây chứ đừng sửa bậc thang.
     */
    const val CHIP_ICON_GAP = KachiSpace.S

    /**
     * **Khe giữa HAI chip** của thanh trên (UX6) — và là chủ DUY NHẤT của khe đó.
     *
     * ## Vì sao một hằng, trong khi trước đây là ba
     * Chip dữ liệu **không có nền riêng** (không pill, không viền — xem `KachiTopStrip.chip`), nên lề TRONG của nó
     * cộng thẳng vào khe mắt người nhìn thấy. [ĐO máy ảo 2026-09-27] khe thật = `lề ngoài 8dp + lề trong phải 4dp
     * + lề trong trái 4dp` = **16dp** (18px đo được giữa hai hộp chip là phần lề ngoài; 24px là khe VẼ giữa hai
     * mép mực). Ba hằng cho một khoảng cách ⇒ không ai đọc ra con số thật, và owner thấy *"vị trí các icon với
     * nhau có vẻ hơi rộng"*. Nay lề trong = 0 và khe đi qua đúng hằng này.
     *
     * ## Ràng buộc KHÔNG được phá: khe NGOÀI phải rộng hơn khe TRONG
     * Chip chỉ đọc ra là *một vật* khi `CHIP_GAP ≥ 1.5 × CHIP_ICON_GAP` (12 ≥ 12 ✓). Hạ hằng này xuống
     * [KachiSpace.S] hoặc nâng [CHIP_ICON_GAP] lên [KachiSpace.M] là icon của chip sau dính vào chữ của chip
     * trước — `TopStripWiringContractTest.khe ngoai chip phai rong hon khe icon-chu` canh đúng phép chia này.
     */
    const val CHIP_GAP = KachiSpace.M

    // ══ THANH NÚT XE (taskbar) ══════════════════════════════════════════════════════════════════════════
    //
    // R5.1: **thanh 80 %, nội dung (ô) 85 %**. Hai tỉ lệ khác nhau là có chủ ý của owner — thanh mỏng đi nhiều
    // hơn ô, tức phần *khung* nhường chỗ trước phần *nội dung*. Hệ quả: lề trong của thanh phải hẹp lại, nếu
    // không thì ô 85 % không còn nằm trong thanh 80 % ([ĐO số học] ở từng hằng dưới).

    /**
     * **LỀ TRONG của thanh nút** — [KachiSpace.XS] thay cho [KachiSpace.S] trước WP5.
     *
     * ## [ĐO số học] vì sao BẮT BUỘC hạ, không phải cho gọn
     * Bề dày thanh phải chứa: ô + lề ngoài ô ([KachiSpace.XS] mỗi phía, do `ControlDockView.sized` đặt) + lề trong
     * thanh. Với lề trong cũ ([KachiSpace.S]): ô ngang `73 + 8 + 16 = 97` > [DOCK_THICK] (93) ⇒ **ô bị cắt**; ô dọc
     * `83 + 8 + 16 = 107` > [DOCK_WIDE] (99) ⇒ cắt nặng hơn. Với [KachiSpace.XS]: `89 ≤ 93` và `99 ≤ 99`. Đó là
     * lý do duy nhất, và nó kiểm lại được bằng số.
     */
    const val DOCK_PAD = KachiSpace.XS

    /**
     * **Bề dày thanh nút khi nằm NGANG** (trên/dưới) — 80 % của 116dp gốc (`116 × 0.8 = 92.8` → **93**).
     *
     * [ĐO số học] còn dư 4dp so với nội dung (`[DOCK_TILE_H] 73 + 2×[KachiSpace.XS] + 2×[DOCK_PAD] = 89`), đúng
     * bằng khoảng dư mà bản trước WP5 có (110 trong 116 = dư 6) ⇒ ô vẫn không dính mép thanh.
     */
    const val DOCK_THICK = 93

    /**
     * **Bề rộng thanh nút khi nằm DỌC** (trái/phải) — 80 % của 124dp gốc (`124 × 0.8 = 99.2` → **99**).
     *
     * ⚠ Ở chiều này bản trước WP5 có **0 dp dư** (`100 + 8 + 16 = 124` = đúng bề rộng thanh), nên nó là chiều
     * quyết định — xem [DOCK_TILE_W_VERTICAL], hằng duy nhất của WP5 phải **suy từ thanh** thay vì nhân 85 %.
     */
    const val DOCK_WIDE = 99

    // ── Cỡ Ô của thanh nút ───────────────────────────────────────────────────────────────────────────────
    //
    // ⚠ Bốn số này trước T5 nằm trong một biểu thức `dpi(ctx, if (dọc) 100 else 84)` nên **bộ đếm số trần đầu
    // tiên của T5 KHÔNG thấy chúng** (mẫu tìm chỉ khớp số đứng một mình trong ngoặc). Tìm ra khi đọc mã để sửa
    // ô stepper. Bài canh đã được sửa để quét *mọi* số trong đối số của `dp(...)`, kể cả trong biểu thức.

    /**
     * Bề rộng ô thanh nút khi thanh nằm NGANG — 85 % của 84dp (`= 71.4` → **71**). Trần vật lý cho mọi thứ bên
     * trong ô; xem [KachiSpace.TOUCH_TIGHT] về hệ quả lên hàng `[−] [giá trị] [+]`.
     */
    const val DOCK_TILE_W = 71

    /** Bề cao ô thanh nút khi thanh nằm NGANG — 85 % của 86dp (`= 73.1` → **73**). */
    const val DOCK_TILE_H = 73

    /**
     * Bề rộng ô thanh nút khi thanh nằm DỌC — **SUY TỪ THANH**, không nhân 85 %.
     *
     * `[DOCK_WIDE] − 2×[KachiSpace.XS] (lề ngoài ô) − 2×[DOCK_PAD] (lề trong thanh)` = `99 − 8 − 8` = **83dp**
     * (83 % của 100dp gốc, lệch 2 điểm so với mốc 85 %).
     *
     * ## ⚠ Vì sao chiều này lệch mốc — và vì sao chọn lệch về phía NÀY
     * Bản trước WP5 khít tuyệt đối ở chiều này (`100 + 8 + 16 = 124` = [DOCK_WIDE] cũ), nên hai mốc của owner
     * **không thể đúng cùng lúc**: 85 % của ô (85dp) đòi thanh ≥ 101dp = 81.5 % (lệch mốc *thanh*), còn 80 % của
     * thanh (99dp) đòi ô ≤ 83dp = 83 % (lệch mốc *ô*). Chọn giữ đúng mốc **thanh** vì đó là thứ owner nhìn thấy
     * và nói ra (*"taskbar kích thước 80 %"* — bề rộng cột chiếm chỗ trên màn), còn 2 điểm trên bề rộng một ô thì
     * không ai đọc ra bằng mắt.
     *
     * Viết dạng **phép trừ** để ô không bao giờ có thể tràn khỏi thanh: đổi [DOCK_WIDE] hay [DOCK_PAD] thì ô tự
     * theo. Đây chính là chỗ mà một con số gõ tay sẽ thành "ô bị cắt" im lặng.
     */
    const val DOCK_TILE_W_VERTICAL = DOCK_WIDE - 2 * KachiSpace.XS - 2 * DOCK_PAD

    /**
     * Bề cao ô thanh nút khi thanh nằm DỌC — 85 % của 70dp (`= 59.5` → **60**).
     *
     * ⚠ Đây là ô **chật nhất** của cả launcher sau WP5, và nó chỉ vừa nhờ hai thứ khác cùng hạ trong lượt này:
     * [ĐO số học] ô STEP cần `2×[DOCK_PAD] + ([KachiSpace.ICON_S] + [KachiSpace.XS]) + [KachiSpace.TOUCH_TIGHT]`
     * = `8 + 24 + 27` = **59** ≤ 60. Với lề trong cũ ([KachiSpace.S]) hoặc nút −/+ cũ (32dp) thì nó **tràn** (67
     * hoặc 64 > 60). Nói cách khác: đổi một trong ba hằng đó mà không tính lại chỗ này là làm ô dọc bị cắt, và
     * việc cắt đó **im lặng** (`LinearLayout` gravity CENTER không báo gì).
     */
    const val DOCK_TILE_H_VERTICAL = 60

    /**
     * F1 · R1.2 (owner 01/10: *"icon 52 dp"*) — KHE của MỘT app trong khối lối tắt trên thanh nút. Khối dài
     * `n × SHORTCUT_CELL + 2 × SHORTCUT_PAD` theo trục thanh (n = `ShortcutStrip.cells`). 52 ≥ [KachiSpace.TOUCH] nên
     * khe là đích chạm đủ; vừa bề dày ô thanh ở cả hai hướng (73 ngang · [DOCK_TILE_W_VERTICAL] = 83 dọc).
     */
    const val SHORTCUT_CELL = 52

    /** Icon trong khe lối tắt = [KachiSpace.ICON_XL] (44): chừa 4 dp mỗi bên để hai icon cạnh nhau không dính. */
    const val SHORTCUT_ICON = KachiSpace.ICON_XL

    /** Lề hai đầu khối lối tắt — cùng bậc lề trong của thanh ([DOCK_PAD]). */
    const val SHORTCUT_PAD = KachiSpace.XS

    /**
     * Widget `w_apps` ở ô TO: cỡ icon GỐC 52 — chỉ còn cho ô "chưa có lối tắt" và cho icon app đã gỡ trước lượt đo đầu.
     * Từ 2.87 (R-SI1) icon app trong lưới KHÔNG còn khe cố định (64 dp cũ): cỡ do `ShortcutGridFit` khớp theo khung
     * thật, kẹp [SHORTCUT_GRID_MIN_ICON]…[SHORTCUT_GRID_MAX_ICON]. Ô NÉN dùng cỡ gốc của thanh ([SHORTCUT_ICON]).
     */
    const val SHORTCUT_GRID_ICON = 52

    /**
     * R-SI1 (spec 2.87 §4.4) — icon lưới lối tắt KHÔNG nhỏ hơn 28 dp dù khung bé: ≤ 8 app nên hiếm khi chạm; khung bé hơn
     * nữa thì giữ 28 dp và để khung cắt mép (icon không nhìn/bấm được còn tệ hơn icon bị cắt). Vùng chạm nới thêm nửa
     * khe mỗi bên (`ShortcutGridLayout`), nên đích chạm vẫn ≥ cỡ icon + khe.
     */
    const val SHORTCUT_GRID_MIN_ICON = 28

    /** R-SI1 — icon lưới lối tắt không lớn hơn 120 dp: khung to với 1 app không thành một icon khổng lồ; phần dư chia đều. */
    const val SHORTCUT_GRID_MAX_ICON = 120

    /**
     * L5 WIDGET-FIT-ALL — SÀN cỡ chữ (đơn vị **sp**) khi lưới widget CO nội dung theo khung (`FitGridLayout`): chữ không
     * bao giờ bị co dưới 10sp; khung nhỏ hơn mức đó thì đổi dạng (ngang · chỉ-icon) hoặc báo sức chứa, KHÔNG bóp chữ.
     * Số SUY từ chữ đang ship, không tự chọn: 10 = nhãn nhỏ nhất trong một ô 2.86 ([ĐO mã] nhãn ô SELECT ở `DOCK` =
     * 11.5 − 1.5sp, `ControlTileFactory.tileSelect`). Sống ở đây chứ không ở `KachiType` vì thang chữ khoá đúng 5 bậc
     * (`TypeScaleContractTest`) và một SÀN không phải bậc — cùng lệ `KachiSpace.BOARD_LABEL_MIN`.
     */
    const val FIT_TEXT_MIN = 10
}
