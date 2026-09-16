package com.byd.clusternav.launcher

/**
 * ═══ T1 — BẢNG MÀU HAI CHỦ ĐỀ ═══════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-and-light-theme.html` §3.2. **Một vai màu = một thuộc tính ở đây**, và mỗi vai
 * PHẢI có đủ hai bản ([DARK] + [LIGHT]). Đây là chỗ duy nhất trong dự án được viết mã màu hex.
 *
 * ## Vì sao tệp này tồn tại
 * [ĐO] trước T1: `KachiTheme` khai 13 `const val` = **hằng biên dịch**, và có **82 mã hex viết cứng ở 21 tệp**.
 * Hằng biên dịch không đổi được lúc chạy ⇒ phiên S1 phải BỎ nút gạt chủ đề vì nó sẽ là **nút chết** (bấm xong
 * lưu bền đúng mà màn hình không đổi một pixel — xem `kachi-settings-screen.html` §4.5). Tệp này bỏ tiền đề đó:
 * `KachiTheme` nay **tra** vai màu từ bảng đang chọn, nên đổi bảng là đổi mọi thứ vẽ sau đó.
 *
 * ## ⚠ BẢNG SÁNG KHÔNG PHẢI BẢNG TỐI ĐẢO NGƯỢC
 * Ba chỗ khác nhau về BẢN CHẤT, không phải về con số:
 *  1. **Mực phải đậm hơn mức "đảo ngược"** — nền sáng phản xạ nhiều, mực xám nhạt trên nền sáng đọc kém hơn
 *     mực xám nhạt trên nền tối ở cùng một tỉ số danh nghĩa (mắt nhạy hơn với mực nhạt trên nền sáng).
 *  2. **Thẻ phân biệt bằng VIỀN, không bằng độ sáng.** Trong bảng tối, thẻ (`#141922`) SÁNG hơn nền (`#0a0d13`)
 *     nên tự nổi lên. Trong bảng sáng, thẻ là **trắng** trên nền sáng ⇒ bước sáng chỉ còn 1.13× (gần như không
 *     thấy) ⇒ [LIGHT] bắt buộc phải có [line] đạt ≥ 3:1 (đo được: 3.71 trên thẻ, 3.28 trên nền), còn [DARK] thì
 *     không cần (xem chú thích [line]).
 *  3. **Màu nhấn và màu-mang-nghĩa phải TỐI ĐI.** `#34d399` (xanh "ổn") trên trắng chỉ 1.8:1 = không đọc được.
 *     Bảng sáng dùng `#04684c` (6.8:1). Đây là lý do vai màu mang nghĩa **không được dùng chung một mã** cho hai
 *     bảng — đúng yêu cầu #3 của T1.
 *
 * ## Bài canh
 * [com.byd.clusternav.launcher.ThemePaletteContractTest] tính tương phản WCAG **từ chính hai bảng này** và đỏ khi
 * một cặp mực/nền tụt dưới 4.5:1 hoặc một viền kết cấu tụt dưới 3:1. Các vai KHÔNG kiểm được bằng luật đó phải
 * khai trong `CONTRAST_EXEMPT` **kèm lý do tại chỗ** (lệ `SettingsCatalog.NOT_SETTINGS`) — không có đường im lặng.
 *
 * @property bg nền màn.
 * @property ink mực chính.
 * @property ink2 mực phụ đậm (nhãn ô điều khiển, chữ chip) — vẫn phải đọc được, khác [mut] ở chỗ nó là nhãn chứ
 *   không phải câu giải thích.
 * @property mut mực mờ (câu phụ, chú thích).
 * @property mut2 mực mờ nhất còn phải đọc được.
 * @property icon màu tô icon lúc KHÔNG bật.
 * @property card nền thẻ.
 * @property card2 nền thẻ biến thể (nhấc thêm một bậc).
 * @property cardFill nền thẻ MẶC ĐỊNH của [KachiTheme.card] — bản tối là trắng-trong-suốt để thẻ ăn theo nền
 *   dưới nó (hình nền ảnh), bản sáng là màu đặc vì trắng-trong-suốt trên nền sáng thì mất hẳn.
 * @property panel nền bảng phủ (Cài đặt, ngăn kéo).
 * @property field nền dòng/ô nhập lõm trong bảng.
 * @property cell nền ô con nhỏ trong widget.
 * @property tile nền ô điều khiển lúc tắt.
 * @property chipOff nền chip lúc không chọn.
 * @property dim nền vùng "không có dữ liệu / đang tắt" (ô giá trị chưa đọc được, nút bước).
 * @property track vành rỗng của vòng đo.
 * @property slot nền thẻ ô làm việc ở màn chính.
 * @property bar nền thanh nút xe (có kênh trong suốt để thấy nền sau nó).
 * @property barTop nền thanh trạng thái trên.
 * @property line viền mảnh trang trí.
 * @property lineStrong viền KẾT CẤU (thanh, thẻ ô làm việc) — vai duy nhất bắt buộc ≥ 3:1 ở CẢ hai bảng.
 * @property gridLine lưới của trình vẽ bố cục.
 * @property emptyFill nền ô trống.
 * @property emptyLine viền gạch đứt của ô trống — kết cấu, ≥ 3:1 cả hai bảng.
 * @property wash lớp tô rất nhạt (thân xe trong sơ đồ).
 * @property overlay lớp tô nhạt (viền thân xe, nền thanh tiến độ nhạc).
 * @property accent màu nhấn NHẬN DIỆN (chấm, viền, lớp tô nhạt) — KHÔNG dùng làm nền của chữ.
 * @property accentInk màu nhấn dùng làm **CHỮ** (nhãn giá trị của ô SELECT). Vai riêng vì [ĐO] `#4c7dff` làm chữ
 *   trên nền ô điều khiển `#242a34` chỉ **3.90:1** — cùng một mã màu nhấn không phục vụ được cả hai việc "làm nền"
 *   và "làm chữ" trên bảng tối.
 * @property accent2 màu nhấn thứ hai (nhận diện).
 * @property gradFrom / @property gradTo nền GRADIENT của nút/pill đang chọn — chữ [onAccent] nằm TRÊN nó nên hai
 *   đầu này bắt buộc đạt 4.5:1 với [onAccent]; vì thế chúng TỐI hơn [accent]/[accent2] một bậc.
 * @property onAccent chữ/icon trên nền gradient đặc.
 * @property inkOnAccent chữ trên nền nhấn BÁN TRONG SUỐT (ô điều khiển đang bật) — bản sáng phải là mực ĐẬM vì
 *   nền đó sau khi trộn ra màu nhạt.
 * @property accentSoft nền tô nhấn nhạt (ô đang chọn ở lưới).
 * @property accentLine viền nhấn nhạt.
 * @property accentWash tô nhấn rất nhạt (vùng kính trước trong sơ đồ xe).
 * @property tileOnFrom / @property tileOnTo / @property tileOnLine ô điều khiển đang BẬT.
 * @property scrimPanel màn che sau bảng phủ.
 * @property scrimBtn nền nút tròn ⇄ trên đầu ô — **KHÔNG theo chủ đề, và đó là chủ ý**: nó nằm trên **pixel của
 *   app đang chiếu**, tức là trên nội dung mà launcher không biết trước. Nên nó phải một mình bảo đảm rằng glyph
 *   [onAccent] (trắng) còn thấy được kể cả khi app phía dưới là **trắng tinh**. [ĐO] bản đầu của T1 đặt bản sáng
 *   `#26000000` (15% đen) theo phản xạ "bảng sáng thì scrim nhạt" ⇒ trắng trên (217,217,217) = **1.41:1**, glyph
 *   gần như biến mất. `#80000000` (50% đen) đo được **3.95:1** trên nền trắng và ~19:1 trên ô tối.
 * @property widgetBacking nền phía SAU widget Android của app khác — **KHÔNG theo chủ đề, cùng lý do [scrimBtn]**:
 *   nội dung ô đó là RemoteViews do app KHÁC vẽ, và quy ước của widget Android là *"nền tối"* nên phần lớn widget
 *   dùng chữ TRẮNG. [ĐO] 2026-09-12 `emulator-5554`, widget đồng hồ trên bảng SÁNG: ô chỉ còn **0.15%** điểm mực tối,
 *   chữ giờ gần như biến mất (trắng trên nền sáng). Launcher **không thể** sửa màu RemoteViews của app khác, nên cách
 *   duy nhất là tự bảo đảm một nền tối phía sau. Widget nào tự vẽ nền đục thì lớp này bị che — không ảnh hưởng gì.
 * @property wallScrim màu lớp **làm tối ảnh nền** (U4) — **KHÔNG theo chủ đề, và đó là một quyết định, không phải
 *   bỏ sót.** Ba lý do, xếp theo sức nặng:
 *   1. **Nhãn nói "Làm tối ảnh" / "Dim the photo".** Cho nó hoá trắng ở bảng sáng là nhãn hứa một việc mà mã làm
 *      việc khác — đúng họ lỗi *"Kính 50%"* mà dự án đã phải đổi nhãn để dọn. Đổi CHIỀU của thanh trượt theo chủ đề
 *      thì phải đổi cả nhãn, và đó là quyết định của owner chứ không phải của lượt vá.
 *   2. **[ĐO] 2026-09-12 bảng SÁNG: bật/tắt lớp này chỉ đổi 0.01% điểm** — thẻ và ô trống cho **0%** nền lọt qua
 *      (đã ghi ở G1/OQ5), nên ảnh nền gần như chỉ thấy ở lề. Đổi hành vi một bề mặt gần như không nhìn thấy được,
 *      trên một thanh trượt người dùng đã đặt, là rủi ro không đổi lấy gì.
 *   3. Chú thích cũ tại chỗ vẽ ghi *"chữ và ô của launcher là màu sáng"* — **U5 đã bác** (nay có bảng SÁNG). Câu đó
 *      đã được sửa; giữ lại thì lần sửa sau sẽ suy luận từ một tiền đề sai.
 *   ⚠ Còn tồn (ghi ra, không che): trên bảng sáng, làm tối ảnh là **sai chiều** cho chữ đậm nằm trên nó. Ngày nào
 *   nền lọt qua nhiều hơn thì vai này là chỗ để thành theo-chủ-đề — cùng lúc với việc đổi nhãn.
 * @property scrimHead lớp mờ dưới nhãn app (bản sáng phải là mờ TRẮNG, vì mực trên nó là mực đậm).
 * @property green / @property amber / @property red / @property cyan / @property orange / @property slate
 *   **màu MANG NGHĨA DỮ LIỆU** (ổn · chưa kiểm · cảnh báo · không khí · nhạc · trung tính). Đây là nhóm mà yêu
 *   cầu #3 của T1 nói rõ: không được dùng chung một mã cho hai bảng.
 * @property amberSoft nền badge "chưa kiểm trên xe".
 * @property artTo đầu thứ hai của gradient ảnh bìa nhạc.
 * @property glow1 / @property glow2 hai vệt sáng của nền vẽ sẵn.
 * @property clear trong suốt hoàn toàn (một vai riêng để chỗ gọi không phải viết `#00000000`).
 */
data class KachiPalette(
    val bg: String,
    val ink: String,
    val ink2: String,
    val mut: String,
    val mut2: String,
    val icon: String,
    val card: String,
    val card2: String,
    val cardFill: String,
    val panel: String,
    val field: String,
    val cell: String,
    val tile: String,
    val chipOff: String,
    val dim: String,
    val track: String,
    val slot: String,
    val bar: String,
    val barTop: String,
    val line: String,
    val lineStrong: String,
    val gridLine: String,
    val emptyFill: String,
    val emptyLine: String,
    val wash: String,
    val overlay: String,
    val accent: String,
    val accentInk: String,
    val accent2: String,
    val gradFrom: String,
    val gradTo: String,
    val onAccent: String,
    val inkOnAccent: String,
    val accentSoft: String,
    val accentLine: String,
    val accentWash: String,
    val tileOnFrom: String,
    val tileOnTo: String,
    val tileOnLine: String,
    val scrimPanel: String,
    val scrimBtn: String,
    val widgetBacking: String,
    val wallScrim: String,
    val scrimHead: String,
    val green: String,
    val amber: String,
    val red: String,
    val cyan: String,
    val orange: String,
    val slate: String,
    val amberSoft: String,
    val artTo: String,
    val glow1: String,
    val glow2: String,
    val clear: String = "#00000000",
) {
    companion object {

        /**
         * Bảng TỐI — giữ **byte-y-nguyên** các mã của prototype đã được owner duyệt, trừ đúng **hai** vai kết cấu:
         *
         *  - [lineStrong] `#26ffffff` → `#59ffffff`: mã cũ chỉ đạt **1.49:1** trên nền, tức là viền của thanh nút
         *    và của thẻ ô làm việc gần như không tồn tại. `#59ffffff` là mức **THẤP NHẤT** vượt 3:1 ([ĐO] 3.14 trên
         *    nền · 3.20 trên thẻ) — cố ý chọn mức tối thiểu để không biến hairline thành đường kẻ xám đậm.
         *  - [emptyLine] `#42506a` → `#5b6d8f`: mã cũ 2.36:1 trên nền ô trống, mã mới 3.68:1. Gạch đứt của ô trống
         *    là thứ nói *"chỗ này đặt được app"* ⇒ nó là kết cấu, không phải trang trí.
         *
         * Hai đổi này là **vá lỗi đọc được**, không phải đổi thẩm mỹ: chúng nằm đúng trong nhóm mà yêu cầu #8 của
         * T1 dặn *"tìm ra chỗ nào khó đọc thì vá luôn"*.
         */
        val DARK = KachiPalette(
            bg = "#0a0d13",
            ink = "#eaf0f8",
            ink2 = "#c3cee0",
            mut = "#93a0b4",
            mut2 = "#8b95a7",
            icon = "#aeb8c8",
            card = "#141922",
            card2 = "#1a1e28",
            cardFill = "#14ffffff",
            panel = "#12141c",
            field = "#161b24",
            cell = "#1c212b",
            tile = "#242a34",
            chipOff = "#1a1f29",
            dim = "#2a2f3a",
            track = "#3a3f45",
            slot = "#171a20",
            bar = "#d915191f",
            barTop = "#990a0d13",
            line = "#17ffffff",
            lineStrong = "#59ffffff",
            gridLine = "#22ffffff",
            emptyFill = "#0b0f16",
            emptyLine = "#5b6d8f",
            wash = "#0dffffff",
            overlay = "#29ffffff",
            accent = "#4c7dff",
            accentInk = "#7ba0ff",
            accent2 = "#7b5cff",
            gradFrom = "#3f6ae0",
            gradTo = "#6b4ce6",
            onAccent = "#ffffff",
            inkOnAccent = "#e7ecff",
            accentSoft = "#264c7dff",
            accentLine = "#8078a0ff",
            accentWash = "#2e4c7dff",
            tileOnFrom = "#5c4c7dff",
            tileOnTo = "#527b5cff",
            tileOnLine = "#8c4c7dff",
            scrimPanel = "#ff070a11",
            scrimBtn = "#80000000",
            widgetBacking = "#171a20",
            wallScrim = "#000000",
            scrimHead = "#8c000000",
            green = "#34d399",
            amber = "#fbbf24",
            red = "#fb7185",
            cyan = "#29d3ee",
            orange = "#f59e0b",
            slate = "#94a3b8",
            amberSoft = "#33fbbf24",
            artTo = "#ef4444",
            glow1 = "#112036",
            glow2 = "#160f28",
        )

        /**
         * Bảng SÁNG — dựng từ đầu theo §3.2, KHÔNG phải nghịch đảo của [DARK].
         *
         * Ba quyết định đáng ghi lại:
         *  - [bg] `#eef1f6` lệch xanh nhẹ thay vì trắng tinh: trắng tinh trên màn 1920 trong cabin ban ngày là
         *    chói, và nó cũng làm thẻ trắng biến mất hoàn toàn.
         *  - [card] **trắng** + [line] `#788698` (3.71:1): đúng luật §3.2 *"phân biệt bằng viền, không bằng độ
         *    sáng"*. Bước sáng thẻ↔nền chỉ 1.13× nên nếu viền yếu thì thẻ không còn là thẻ.
         *  - [inkOnAccent] là mực ĐẬM `#14224d`, không phải trắng: ô điều khiển đang bật dùng nền nhấn **bán trong
         *    suốt**, trộn trên nền sáng ra `#c7d3f4` — chữ trắng trên đó chỉ **1.49:1**. Đây là cái bẫy chính khi
         *    làm bảng sáng: cùng một vai, cùng một nền danh nghĩa, mà hướng mực phải ĐẢO.
         */
        val LIGHT = KachiPalette(
            bg = "#eef1f6",
            ink = "#0f1620",
            ink2 = "#26303f",
            mut = "#556174",
            mut2 = "#5b6879",
            icon = "#4f5b6d",
            card = "#ffffff",
            card2 = "#f7f9fc",
            cardFill = "#ffffff",
            panel = "#ffffff",
            field = "#e8ecf3",
            cell = "#f2f5fa",
            tile = "#f2f5fa",
            chipOff = "#e4e9f1",
            dim = "#cfd6e2",
            track = "#d5dbe6",
            slot = "#ffffff",
            bar = "#d9ffffff",
            barTop = "#e6ffffff",
            line = "#788698",
            lineStrong = "#667487",
            gridLine = "#aab5c6",
            emptyFill = "#e6eaf1",
            emptyLine = "#788698",
            wash = "#0a000000",
            overlay = "#14000000",
            accent = "#2f5ae0",
            accentInk = "#2f5ae0",
            accent2 = "#5b3ee0",
            gradFrom = "#2f5ae0",
            gradTo = "#5b3ee0",
            onAccent = "#ffffff",
            inkOnAccent = "#14224d",
            accentSoft = "#1f2f5ae0",
            accentLine = "#992f5ae0",
            accentWash = "#1a2f5ae0",
            tileOnFrom = "#382f5ae0",
            tileOnTo = "#305b3ee0",
            tileOnLine = "#b32f5ae0",
            scrimPanel = "#ff10151d",
            scrimBtn = "#80000000",
            widgetBacking = "#171a20",
            wallScrim = "#000000",
            scrimHead = "#d9ffffff",
            green = "#04684c",
            amber = "#7d5200",
            red = "#c02640",
            cyan = "#026e83",
            orange = "#a5480a",
            slate = "#5a6779",
            amberSoft = "#337d5200",
            artTo = "#b91c37",
            glow1 = "#dbe6f7",
            glow2 = "#ece0f8",
        )
    }
}
