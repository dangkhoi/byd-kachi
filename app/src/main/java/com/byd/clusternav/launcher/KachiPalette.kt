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
 * @property slot ĐỈNH chuyển sắc của thẻ ô làm việc ở màn chính — **cái khay** mà mọi thẻ nội dung đứng lên.
 *   ⚠ [SOÁT Pass 4] Trước 1.69 vai này là một tô ĐẶC và ô làm việc là **bề mặt lớn nhất màn hình** mà lượt P1
 *   không chạm tới: bảng rà 26 chỗ gọi của §9 đi từ `KachiTheme.card(`, còn ô làm việc dựng `GradientDrawable`
 *   thẳng tại chỗ (`WorkspaceView.kt:274`) nên nó **không nằm trong bảng rà**. Kết quả đo được trên ảnh máy ảo:
 *   điểm ảnh của ô làm việc TRƯỚC và SAU P1 giống nhau **từng byte** — đúng lý do owner/parent thấy “đổi mà
 *   không nhận ra”. Nay nó đi qua [KachiTheme.surface] với [SurfaceTone.WELL].
 * @property slotTo ĐÁY chuyển sắc của khay ấy. Khay **tối hơn** thẻ nội dung một bậc ([ĐO] thẻ/khay 1.19× ở bảng
 *   tối) — nếu khay và thẻ cùng một sắc độ thì thẻ không còn chỗ nào để nổi lên, và đó đúng là cái đang xảy ra ở
 *   bảng SÁNG trước lượt này: khay `#ffffff` + thẻ `#ffffff` = thẻ chỉ còn tồn tại nhờ hairline.
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
 *
 * ## ═══ VISUAL-REFRESH · P1 — chín vai CHẤT LIỆU BỀ MẶT (spec kachi-visual-refresh §4.1/§4.5) ═══
 * Owner 2026-09-16: *"các widget chạy trên nền xám nhìn hơi chán quá"* → *"cho gradient hay làm sao cho đẹp được
 * thì làm, không cần rule cấm gì đâu"*. Chín vai dưới đây là **thêm**, không thay: [card]/[cardFill] vẫn là bề
 * mặt của ô lõm và của thẻ mang màu-trạng-thái (bảng chuyển/giữ 26 chỗ gọi ở §9 của spec).
 *
 * @property surfFrom ĐỈNH chuyển sắc dọc của thẻ nội dung. Bản tối sáng hơn [bg] một bậc ([ĐO] 1.23×) nên thẻ tự
 *   lồi lên **không cần bóng đổ** — đúng ràng buộc 0 blur / 0 shadow / 0 elevation của R5 (GPU TRINKET).
 * @property surfTo ĐÁY chuyển sắc. Nguồn sáng đặt ở TRÊN, nhất quán với mép sáng [surfEdge] và (sau này) với hình
 *   xe ở P3 — một hướng sáng cho cả hệ thì mắt đọc ra "chất liệu", nhiều hướng thì đọc ra "lỗi".
 * @property surfEdge mép sáng 1dp ở **đỉnh** thẻ. Đây KHÔNG phải bóng đổ: nó là mặt vát hắt sáng, vẽ bằng một
 *   `GradientDrawable` mờ dần — 0 chi phí GPU thêm.
 *   ⚠ Bản SÁNG gần như vô hình (trắng trên trắng, [ĐO] 1.00:1) và **đó là đúng**: đỉnh thẻ trắng đã là chỗ sáng
 *   nhất rồi, không còn chỗ nào sáng hơn để hắt. Ở bản sáng việc "tách thẻ" do [surfLine] gánh (3.28:1).
 * @property surfLine hairline viền ngoài của thẻ chất liệu. Hai bảng dùng **hai cơ chế khác nhau**, y như [line]:
 *   bản TỐI tách thẻ bằng bước sáng (1.23×) nên hairline chỉ là nét trang trí; bản SÁNG bước sáng chỉ 1.13× ⇒
 *   hairline **bắt buộc** là viền thật (đo được 3.28:1 trên nền, 3.71:1 trên thẻ trắng).
 * @property surfOnFrom / @property surfOnTo thẻ/ô đang BẬT — cùng trục nhấn xanh→tím của Kachi, bán trong suốt để
 *   ăn theo nền dưới nó. [ĐO] bước sáng so với thẻ thường: 1.28× (tối) · 1.75× (sáng) ⇒ trạng thái chọn nhìn ra
 *   được mà không phải đổi kích thước hay thêm hiệu ứng.
 *   ⚠ Chữ trên thẻ BẬT là [ink] (tối 10.76:1 · sáng 10.40:1), **không phải** [onAccent] — bản sáng trộn ra
 *   `#b5c3ef` nên chữ trắng ở đó chỉ 1.75:1. Đúng cái bẫy đã ghi ở [inkOnAccent].
 * @property surfOnEdge mép sáng của thẻ BẬT — **mang sắc nhấn**, nhưng vẫn phải SÁNG hơn nền nó nằm trên.
 *   [ĐO] bản đầu đặt bản sáng `#cc2f5ae0` (xanh nhấn đặc) theo phản xạ *"thẻ bật thì mép cũng nhấn"* ⇒ trên nền
 *   `#b5c3ef` nó **tối đi**, tức là một vệt bóng ở ĐỈNH thẻ — ngược hẳn nguồn sáng của cả hệ. Bản sáng nay dùng
 *   `#cce8eeff` (trắng ngả xanh): vẫn cùng họ nhấn, vẫn sáng hơn nền. Màu nhấn của trạng thái bật do **viền**
 *   ([accentLine]) và cả mặt gradient gánh, không phải do mép gánh.
 * @property fieldSunken ô LÕM (ô nhập, rãnh, đoạn phân đoạn) — tối hơn mặt chứa nó một bậc và **giữ phẳng** (một
 *   tô đặc, không gradient, không mép sáng). Lõm và lồi phải khác nhau ở CƠ CHẾ, không chỉ ở con số.
 * @property surfFromOverArt / @property surfToOverArt bản **BÁN TRONG SUỐT 80 %** của [surfFrom]/[surfTo], dành
 *   cho thẻ nằm TRÊN ẢNH NỀN (P1b — xem spec §4.10).
 *   Owner 2026-09-16 (kèm ảnh chụp trên xe): *"cái màu đen, xám của mình, khi nhét thêm hình nền vào, nó lại không
 *   đẹp nữa"* — thẻ đục đặt trên ảnh đọc ra thành **miếng vá**, không thành cửa sổ. Hai vai này để lớp ảnh (đã làm
 *   mờ sẵn một lần lúc chọn ảnh) lọt qua ~20 %.
 *   ⚠ **Chúng nằm trên nền KHÔNG BIẾT TRƯỚC** nên không đo được bằng luật mực-trên-nền thường. [ĐO] trộn lên hai
 *   nền tệ nhất có thể (trắng tinh và đen tuyền): [ink] giữ **7.27:1** (tối) / **10.17:1** (sáng) — đạt; nhưng
 *   [mut] chỉ còn **3.15–3.91:1** ⇒ **P1b BẮT BUỘC** phải thêm lớp che (scrim 35–50 %) hoặc chọn mực theo độ chói
 *   đo được của chính vùng ảnh dưới thẻ. Ghi ra đây, không giấu: đây là ràng buộc của pha sau, không phải một chỗ
 *   đã xong.
 * @property domainTints lớp sắc LĨNH VỰC phủ lên gradient thẻ (4.7–7 % — [ĐO] bước sáng 1.07–1.15× so với thẻ
 *   không tint, tức nhìn ra được mà không đánh nhau với chữ: mọi mực vẫn ≥ 4.5:1 trên **cả hai** đầu gradient).
 *   Khoá là **tên `Domain`** (chuỗi, không phải kiểu enum) để tệp này giữ nguyên tính chất *không phụ thuộc mô
 *   hình khả năng* — nó là bảng màu, không phải chỗ biết về `:core`. Tra qua [domainTint].
 *   Vì sao cần: [ĐO ảnh máy ảo 2026-09-16] màn chính bày 2–4 thẻ nhóm cùng lúc, tất cả cùng một xám ⇒ muốn biết
 *   thẻ nào là Khí hậu phải **đọc chữ**. Sắc lĩnh vực cho mắt tìm vùng trước khi đọc.
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
    val slotTo: String,
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
    val surfFrom: String,
    val surfTo: String,
    val surfEdge: String,
    val surfLine: String,
    val surfOnFrom: String,
    val surfOnTo: String,
    val surfOnEdge: String,
    val fieldSunken: String,
    val surfFromOverArt: String,
    val surfToOverArt: String,
    val domainTints: Map<String, String>,
    val clear: String = "#00000000",
) {

    /**
     * Sắc lĩnh vực cho một `Domain` — **chỗ tra DUY NHẤT** (CLAUDE.md §7: khác biệt phải lộ ra qua một bảng, không
     * rải `if` theo tên nhóm/tên gói ở chỗ vẽ).
     *
     * @param domain tên hằng của `Domain` (`domain?.name`). `null` hoặc tên lạ ⇒ [clear] = **không tint**, chứ
     *   không phải một màu mặc định: thẻ không thuộc lĩnh vực nào thì phải trông trung tính, không mượn sắc của
     *   lĩnh vực khác.
     */
    fun domainTint(domain: String?): String = domainTints[domain] ?: clear

    companion object {

        /**
         * Sắc lĩnh vực bản TỐI — 7 % (`0x12`) của **màu mang nghĩa đã có**, không phải tám màu mới.
         *
         * Vì sao dùng lại `green`/`cyan`/`amber`…: một lĩnh vực phải mang **một** sắc ở mọi chỗ nó xuất hiện (chấm
         * trạng thái, icon, nền thẻ). Đặt một dải màu thứ hai chỉ để tô nền là mời hai bảng lệch nhau — đúng bệnh
         * `ChipTone` mà bài canh đã ghi (bản nháp viết `#37d67a` trong khi bảng là `#34d399`).
         *
         * Lục = pin/sạc · tím = chuyển động (cùng trục nhấn) · lam-xanh = không khí · xám-lam = lốp · bạc = thân xe ·
         * vàng ấm = đèn · xanh nhấn = danh tính · cam = giải trí.
         *
         * ⚠ [SOÁT Pass 4] Alpha nâng **7 % → 10.2 %** (`0x12` → `0x1a`). Lý do đo được, không phải sở thích: ở 7 %
         * bước sáng của thẻ đã tint so với thẻ trơ chỉ **1.07–1.15×**, tức là dưới đúng cái ngưỡng 1.15× mà bài
         * `the chat lieu tach duoc khoi nen` dùng để nói *"mắt đọc ra được"* — sắc lĩnh vực khi ấy tồn tại trong
         * bảng màu mà không tồn tại trên màn. Ở 10.2 % bước sáng là **1.11–1.24×**, và mực tệ nhất vẫn **4.59:1**
         * (trên sàn 4.5) sau khi bốn vai mực của bảng tối đã sáng lên một bậc — xem [DARK].
         */
        private val DARK_TINTS: Map<String, String> = mapOf(
            "ENERGY" to "#1a34d399",
            "DRIVETRAIN" to "#1a7b5cff",
            "CLIMATE" to "#1a29d3ee",
            "TYRES" to "#1a94a3b8",
            "BODY" to "#1aaeb8c8",
            "LIGHTS" to "#1afbbf24",
            "IDENTITY" to "#1a4c7dff",
            "INFOTAINMENT" to "#1af59e0b",
        )

        /**
         * Sắc lĩnh vực bản SÁNG — 4.7 % (`0x0c`) của màu mang nghĩa **bản sáng** (đã tối đi sẵn, §3.2 mục 3).
         *
         * Alpha thấp hơn bản tối vì chiều tác dụng **ngược**: trên thẻ trắng, tint làm nền TỐI đi ⇒ nó ăn vào
         * tương phản của chữ. [ĐO] ở 7 % thì `mut2`/`accentInk`/`slate` tụt xuống 4.25–4.35:1 (dưới sàn); ở 4.7 %
         * mọi mực vẫn ≥ 4.5:1 mà bước sáng so với thẻ không tint vẫn 1.07–1.08× — vẫn nhìn ra được.
         *
         * ⚠ [SOÁT Pass 4] Nâng **4.7 % → 7.8 %** (`0x0c` → `0x14`) — vẫn thấp hơn bản tối (10.2 %) vì chiều tác
         * dụng ngược vẫn đúng, nhưng đủ để sắc lĩnh vực **tồn tại trên màn**. [ĐO] mực tệ nhất trên thẻ trắng đã
         * tint: **5.13:1** sau khi sáu vai mực của bảng sáng đậm lên một bậc (xem [LIGHT]), còn dư sàn; mốc chặn thật của bảng sáng là thẻ LÕM và khay, không phải tint.
         */
        private val LIGHT_TINTS: Map<String, String> = mapOf(
            "ENERGY" to "#1404684c",
            "DRIVETRAIN" to "#145b3ee0",
            "CLIMATE" to "#14026e83",
            "TYRES" to "#145a6779",
            "BODY" to "#144f5b6d",
            "LIGHTS" to "#147d5200",
            "IDENTITY" to "#142f5ae0",
            "INFOTAINMENT" to "#14a5480a",
        )

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
            // ⚠ [SOÁT Pass 4] Bốn vai mực dưới đây SÁNG LÊN một bậc — hệ quả BẮT BUỘC của việc thẻ sáng lên
            // (1.23× → 1.35× so với nền) và sắc lĩnh vực đậm lên (7 % → 10.2 %). Giữ mã cũ thì [mut2] tụt xuống
            // **4.10:1** trên thẻ đã tint, tức là đổi thẩm mỹ bằng cách mượn của người đọc. Ở bảng TỐI mực sáng
            // hơn luôn luôn làm tương phản TỐT hơn trên MỌI nền, nên bốn đổi này không có mặt trái ở đâu khác.
            mut = "#9daabe",
            mut2 = "#99a4b6",
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
            slot = "#161c26",
            slotTo = "#0d1118",
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
            // ⚠ [SOÁT Pass 4] Nút TẮT nay sáng lên (nền thẻ 1.23× → 1.35×) ⇒ bậc BẬT↔TẮT tụt từ ~1.35× xuống
            // **1.22×**, sát sàn 1.20×. Nâng alpha 36 % → 50 % kéo bậc lên **1.59×** mà chữ [inkOnAccent] vẫn
            // **7.71:1**. Đây là cái bẫy kinh điển của việc đổi một bậc trong thang: bậc bên cạnh im lặng hẹp lại.
            tileOnFrom = "#804c7dff",
            tileOnTo = "#667b5cff",
            tileOnLine = "#b34c7dff",
            scrimPanel = "#ff070a11",
            scrimBtn = "#80000000",
            widgetBacking = "#171a20",
            wallScrim = "#000000",
            scrimHead = "#8c000000",
            green = "#34d399",
            amber = "#fbbf24",
            red = "#ff8fa0",
            cyan = "#29d3ee",
            orange = "#f59e0b",
            slate = "#a4b1c5",
            amberSoft = "#33fbbf24",
            artTo = "#ef4444",
            glow1 = "#112036",
            glow2 = "#160f28",
            // ── VISUAL-REFRESH P1 · chất liệu bề mặt. Mọi con số đi qua bảng tương phản sinh bằng máy ở
            //    `ThemePaletteContractTest` (§6.4 của spec) — không mã nào ở đây là "ước chừng cho đẹp".
            // ⚠ [SOÁT Pass 4 — lượt ĐẬM TAY] Bộ số cũ đo đúng nhưng **nhìn không ra** ở khoảng cách lái xe: bước
            //    sáng 1.23×, mép sáng 18 % và tint 7 % cộng lại cho một thay đổi mà chính người đặt hàng nó phải
            //    soi hai ảnh cạnh nhau mới thấy. Owner đã bỏ mọi luật cấm thẩm mỹ ⇒ lượt này đẩy từng lực một lên
            //    tới sát sàn tương phản, và ghi luôn con số để lần sau biết còn bao nhiêu chỗ:
            //      · bước sáng thẻ/nền  1.23× → **1.35×**   (sàn của bài canh: 1.15×)
            //      · chênh trong thân gradient 1.23× → **1.29×** (đỉnh sáng hơn, đáy tối hơn)
            //      · mép sáng 18 % → **35 %** ⇒ đỉnh thẻ sáng gấp **3.08×** mặt thẻ — đọc ra là mặt vát kim loại
            //      · thẻ BẬT so với thẻ thường 1.28× → **1.61×**
            //      · ô LÕM so với thẻ 1.23× → **1.39×** (và nay TỐI hơn cả nền màn)
            surfFrom = "#232a37",
            surfTo = "#0f131a",
            surfEdge = "#59ffffff",
            surfLine = "#3dffffff",
            surfOnFrom = "#993f6ae0",
            surfOnTo = "#596b4ce6",
            surfOnEdge = "#b34c7dff",
            fieldSunken = "#05080d",
            surfFromOverArt = "#cc232a37",
            surfToOverArt = "#cc0f131a",
            domainTints = DARK_TINTS,
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
            // ⚠ [SOÁT Pass 4] Sáu vai mực của bảng SÁNG ĐẬM LÊN một bậc — đối xứng với việc bốn vai của bảng TỐI
            // sáng lên, và vì cùng một lý do: sắc lĩnh vực đậm lên (4.7 % → 7.8 %) ăn vào tương phản, mà ở bảng
            // sáng tint làm nền TỐI đi nên nó ăn trực tiếp. Giữ mã cũ thì `mut2` còn **4.54:1** — qua sàn đúng
            // 0.04, tức là không còn chỗ cho bất kỳ lượt chỉnh nào sau này. Đậm hơn ⇒ tốt hơn trên MỌI nền sáng.
            mut = "#4c5869",
            mut2 = "#54606f",
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
            // ⚠ [SOÁT Pass 4] Khay của ô làm việc KHÔNG còn là trắng. Trắng + thẻ trắng = [ĐO] ảnh máy ảo
            // `after/home-4o-sang.png`: ô con chỉ còn tồn tại nhờ hairline, không còn bậc nào. Khay nay xám nhạt
            // hơn nền màn một chút để thẻ trắng có chỗ nổi lên (thẻ/khay **1.16×**), và mực tệ nhất trên khay vẫn
            // **4.66:1**. Không thể xám hơn nữa: [mut2] của bảng sáng chạm sàn ở `#dfe5ee`.
            slot = "#eaeff6",
            slotTo = "#e4e9f2",
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
            accentInk = "#2b52d1",
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
            red = "#b32439",
            cyan = "#01606f",
            orange = "#9b430a",
            slate = "#535f6e",
            amberSoft = "#337d5200",
            artTo = "#b91c37",
            glow1 = "#dbe6f7",
            glow2 = "#ece0f8",
            // ── VISUAL-REFRESH P1. KHÔNG phải nghịch đảo của DARK: xem KDoc [surfEdge] (mép sáng vô hình ở bảng
            //    sáng — và đó là ĐÚNG) và [surfLine] (bảng sáng bắt buộc có viền THẬT ≥ 3:1).
            surfFrom = "#ffffff",
            surfTo = "#eff3f9",
            surfEdge = "#ccffffff",
            surfLine = "#788698",
            surfOnFrom = "#4c2f5ae0",
            surfOnTo = "#335b3ee0",
            surfOnEdge = "#cce8eeff",
            fieldSunken = "#dfe5ee",
            surfFromOverArt = "#ccffffff",
            surfToOverArt = "#cceff3f9",
            domainTints = LIGHT_TINTS,
        )
    }
}
