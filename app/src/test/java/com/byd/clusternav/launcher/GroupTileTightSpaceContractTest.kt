package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KIỂM TOÁN 2026-09-12 — Ô NHÓM Ở CHỖ HẸP: chữ có SÀN, chỗ thì CO ═════════════════════════════════════════
 *
 * Tách khỏi [GroupTileWiringContractTest] vì tệp đó vượt trần 500 dòng. Đường cắt theo **chủ đề**: bài kia canh *dây
 * nối* của ô nhóm (làm mới tại chỗ · một ngưỡng · một lớp đơn vị · một danh sách thành viên), bài này canh riêng ba
 * lỗi **nhìn-thấy-được** mà lượt kiểm toán 2026-09-12 đo được trên ảnh chụp máy ảo:
 *
 *  1. nhãn bảng sơ đồ ADAS **nét 13px** (dưới chuẩn G1 15–16px) vì ô vẽ bóp chữ để nhồi đủ 4 hàng × 2 dòng;
 *  2. hàng nút của nhóm *Kính* **bị cắt đáy** vì `LinearLayout` đo con theo thứ tự xếp — lưới đọc lấy trọn trần trước;
 *  3. nhãn nút bị cắt (`"Window front-ri…"` / `"Kính trước-p…"`) vì ô BẤM chưa có khái niệm nhãn ngắn.
 *
 * Cả ba **không có bài canh nào** trước đó, và cả ba chỉ lộ ra khi mở ảnh chụp ra xem — đúng luật G1: *"test xanh +
 * mã đúng KHÔNG chứng minh đã giao được thứ owner hỏi"*.
 */
class GroupTileTightSpaceContractTest {

    private val tileFiles = listOf(
        "src/main/java/com/byd/clusternav/launcher/GroupTiles.kt",
        "src/main/java/com/byd/clusternav/launcher/GroupTileViews.kt",
        "src/main/java/com/byd/clusternav/launcher/GroupTileParts.kt",
    )
    private val tiles by lazy { tileFiles.joinToString("\n") { code(it) } }
    private val side by lazy { code("src/main/java/com/byd/clusternav/launcher/SideBoardView.kt") }

    private fun code(relative: String): String = SourceRoots.codeOf(relative)


    // ── 7 · [KIỂM TOÁN 2026-09-12] Chỗ hẹp: chữ có SÀN, chỗ thì CO ────────────────────────────────

    /**
     * ⚠⚠ **Bảng sơ đồ bên không được tự bóp chữ để nhồi đủ hàng.**
     *
     * [ĐO] ảnh máy ảo 2026-09-12: bảng ADAS ở khung 4/12 màn có `m = 231px`, cỡ nhãn `231 × 0.058 = 13.4px` ⇒ **nét
     * cao 13px**, dưới chuẩn G1 (15–16px). Tỉ lệ vẫn "đúng" mà chữ không đọc được — vì tỉ lệ không biết ngưỡng của
     * mắt. Bài này chốt hai nửa của cách chữa:
     *  (a) cỡ chữ = `max(tỉ lệ, SÀN)` — sàn khai trong thang [KachiSpace], không phải số trần ở tầng vẽ;
     *  (b) **số hàng** là thứ co, và ai co thì phải HỎI `:core` (`GroupBoard.sidePlan`) chứ không tự chọn bỏ ô nào —
     *      chọn bỏ là một quyết định về DỮ LIỆU (cảnh báo nào quan trọng), không phải về vẽ.
     */
    @Test
    fun `bang so do ben co san chu doc duoc va hoi core khi khong du cho`() {
        assertTrue(side.contains("Sp.BOARD_LABEL_MIN"), "cỡ nhãn phải có SÀN lấy từ thang KachiSpace")
        assertTrue(side.contains("Sp.BOARD_VALUE_MIN"), "cỡ giá trị cũng phải có SÀN")
        assertTrue(
            Regex("""maxOf\(\s*min \* LABEL_RATIO,\s*labelFloorPx\s*\)""").containsMatchIn(side),
            "cỡ nhãn phải là max(tỉ lệ, sàn) — chỉ dùng tỉ lệ là quay lại đúng lỗi 13px",
        )
        assertTrue(side.contains("GroupBoard.sidePlan("), "không đủ chỗ thì HỎI :core hiện cái gì")
        assertTrue(
            side.contains("rowFloorPx"),
            "số hàng phải suy từ SÀN chiều cao hàng (Sp.BOARD_ROW_MIN), không phải chia đều cho số ô con",
        )
        // Và phần bị ẩn phải NÓI RA con số — bỏ bớt im lặng là kênh im lặng thứ tư của dự án.
        assertTrue(side.contains("kachi_board_hidden_n"), "ô con bị ẩn phải được ĐẾM ra trên màn")
    }


    /**
     * ⚠⚠ **Hàng nút là chi phí CỐ ĐỊNH; lưới đọc là phần NHƯỜNG được.**
     *
     * [ĐO] ảnh máy ảo 2026-09-12, nhóm *Kính* ở khung 4/12 màn: hộp nội dung 267px = đầu ô 26 + lưới đọc **108** +
     * hàng nút cần **148** ⇒ thiếu 15px ⇒ bốn ô kính bị cắt phẳng ở y=464 (nút *Đóng/Mở* mất vành dưới + trọn lề dưới
     * 12px). Nguyên nhân: `LinearLayout` đo con **theo thứ tự xếp**, nên lưới đọc (đứng trước) lấy trọn trần trước khi
     * hàng nút được hỏi tới; hàng nút tràn ra ngoài rồi bị cắt — **không ném, không log**.
     *
     * Bài này canh cách chữa (đo hai lượt, lưới nhường) chứ không canh pixel — pixel chỉ đo được trên máy ảo.
     */
    @Test
    fun `o nhom do hai luot de hang nut khong bi cat`() {
        val fn = SourceRoots.body(tiles, "override fun onMeasure(")
        assertTrue(fn.contains("resetTrim()"), "phải bỏ phần đã bớt ở ĐẦU mỗi lượt — không thì lưới co dần vĩnh viễn")
        assertTrue(fn.contains("trimBy("), "thấy tràn thì bảo lưới đọc nhường đúng phần tràn")
        assertTrue(fn.contains("super.onMeasure("), "rồi đo LẠI (một lượt nữa, có chặn bởi sàn)")
        // ⚠⚠ SÀN phải là PHÉP ĐO, không phải hằng dp — tôi đã đoán sai hằng đó **hai lần**, theo hai chiều ngược
        // nhau: 52dp ⇒ bản tiếng Anh vẫn cắt hàng nút 11px; 27dp ⇒ hàng nút hết cắt nhưng ô ĐỌC bị cắt chữ (`"Window"`
        // / `"FL"` mất nửa dưới). Chiều cao ô con phụ thuộc *nhãn có xuống dòng hay không* ⇒ phụ thuộc bề ngang VÀ
        // ngôn ngữ. Bài này chặn việc quay lại đoán bằng hằng.
        val parts = code("src/main/java/com/byd/clusternav/launcher/GroupTileParts.kt")
        assertTrue(
            SourceRoots.body(parts, "fun trimBy(").contains("contentPx"),
            "sàn nhường phải là chỗ nội dung THẬT cần",
        )
        val measure = SourceRoots.body(parts, "override fun onMeasure(")
        assertTrue(
            measure.contains("MeasureSpec.UNSPECIFIED)") && measure.contains("contentPx = measuredHeight"),
            "chỗ nội dung thật cần phải ĐO (một lượt `UNSPECIFIED`), không được suy từ hằng dp",
        )
        assertTrue(
            measure.contains("coerceAtLeast(contentPx)"),
            "chiều cao cuối không được xuống dưới mức đó — không thì lỗi chỉ DỜI từ hàng nút sang ô đọc",
        )
        assertFalse(
            parts.contains("READ_ROW_MIN"),
            "quay lại sàn bằng hằng dp = quay lại đúng hai lần đoán sai đã đo được",
        )
    }

    /**
     * ⚠⚠ **Bảng `BOARD` CÓ NÚT: thân ô phải lấy `weight`, và khoảng thở phải biến mất.**
     *
     * [SOÁT U9 pha 2] Cùng một bệnh với bài trên (*hàng nút bị cắt đáy, im lặng*), nhưng đi **đường khác** nên bài
     * trên không phủ: ở đó thứ tranh chỗ là lưới đọc (co được ⇒ chữa bằng đo hai lượt + [ReadGrid.trimBy]); ở đây
     * thứ tranh chỗ là một **ô vẽ Canvas** — nó không có `trimBy`, và `View.getDefaultSize` trả TRỌN `specSize` khi
     * spec là `AT_MOST`, nên một bảng khai `MATCH_PARENT` trong thân `WRAP` báo cao **hết phần còn lại** và đẩy hàng
     * nút ra ngoài lề rồi bị cha cắt — không ném, không log. Đúng cái bẫy `View` trơ đã ăn mất cả dải mục đọc
     * (`o chen cho trong khong duoc khai WRAP`, `GroupTileWiringContractTest`).
     *
     * Cách chữa duy nhất đo được: cho thân ô `weight` ⇒ `LinearLayout` đo hàng nút (chi phí **CỐ ĐỊNH**) trước rồi
     * chỉ chia **phần dư** cho bảng. Bảng Canvas co tới 0 mà không mất gì phải bấm; hàng nút thì không co được.
     *
     * Ba nửa của cách chữa, cả ba phải còn: (a) nhận diện đúng ca (`BOARD` **và** có nút — nhóm *Cửa & khoang*, nhóm
     * `BOARD` đầu tiên có nút); (b) thân ô lấy `weight` **thay cho** `WRAP`; (c) khoảng thở `weight` **không** được
     * thêm nữa ở ca đó (hai `weight` cùng lúc ⇒ bảng chỉ còn một nửa chỗ mà chẳng để làm gì).
     *
     * [ĐO] `u9b_open_two4.png` (2 cột, có dữ liệu): viền dưới thẻ `y = 869`, ô nút cao nhất (*Rèm*, hai nút phụ) hết
     * mực ở `y = 851` ⇒ còn **18 px** lề dưới. Gỡ `weight` ⇒ quay lại đúng bệnh cắt-đáy của nhóm *Kính*.
     */
    @Test
    fun `bang BOARD co nut thi than o lay weight, khong lay WRAP`() {
        val fn = SourceRoots.body(tiles, "fun bind(")
        assertTrue(
            Regex("""val boardWithActions = model\.shape == WidgetShape\.BOARD && model\.hasActions""")
                .containsMatchIn(fn),
            "phải nhận diện đúng ca BOARD-có-nút — hình thôi chưa đủ, và có-nút thôi cũng chưa đủ",
        )
        assertTrue(
            Regex("""addView\(bodyHolder, if \(boardWithActions\) LayoutParams\(MATCH, 0, 1f\)""")
                .containsMatchIn(fn),
            "thân ô BOARD-có-nút phải lấy `weight` (cao 0 + 1f); để `WRAP` là ô vẽ Canvas nuốt trọn chỗ còn lại " +
                "rồi hàng nút bị cắt IM LẶNG",
        )
        assertTrue(
            Regex("""if \(!boardWithActions\) addView\(View\(context\), LayoutParams\(MATCH, 0, 1f\)\)""")
                .containsMatchIn(fn),
            "khoảng thở chỉ dành cho ca thân `WRAP` — thêm `weight` thứ hai là chia đôi chỗ của bảng mà không để " +
                "làm gì",
        )
        assertTrue(
            fn.contains("if (model.hasActions) addView(actionsRow("),
            "hàng nút vẫn dựng theo `hasActions` — nó CHƯA BAO GIỜ phụ thuộc vào hình, thứ từng thiếu là CHỖ",
        )
    }

    /**
     * Ô trong hàng nút của nhóm dùng cỡ **hẹp** ([TileSize.GROUP]) — nhãn NGẮN + nút phụ xếp DỌC.
     *
     * [ĐO] cùng ảnh: nhãn đầy bị cắt `"Window front-ri…"` / `"Kính trước-p…"`, và hai nút *Đóng/Mở* xếp ngang trong
     * 82px còn ~32px mỗi nút ⇒ chữ bị cắt CỨNG thành `"Đ"`/`"C"` (không cả dấu `…`).
     */
    @Test
    fun `hang nut cua nhom dung co o HEP`() {
        val fn = SourceRoots.body(tiles, "internal fun actionsRow(")
        assertTrue(fn.contains("TileSize.GROUP"), "ô hàng nút nhóm hẹp hơn ô thanh nút ⇒ phải dùng cỡ riêng")
        val factory = code("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt")
        assertTrue(
            SourceRoots.body(factory, "fun actionTile(").contains("size.narrow) def.displayShortLabel"),
            "ô hẹp phải dùng nhãn NGẮN của bộ đăng ký (không tự viết tắt ở tầng vẽ)",
        )
        assertTrue(
            SourceRoots.body(factory, "private fun tileCover(").contains("size.narrow) LinearLayout.VERTICAL"),
            "ô hẹp phải xếp DỌC hai nút phụ — xếp ngang thì chữ `Đóng`/`Close` không thể vừa 32px",
        )
    }


    /**
     * ⚠⚠ **[THỬ PHÁ tìm ra] Luật "icon hàng nút không phân biệt được thì bỏ" phải được HỎI, không chỉ được KHAI.**
     *
     * [ĐO] đổi `GroupBoardModel.actionIconsDistinguish` thành `get() = true` rồi chạy cả hai module: **0 bài đỏ** —
     * dù đó chính là luật lấy lại 30px bề cao để hàng nút hết bị cắt. Một thuộc tính ở `:core` mà tầng vẽ không đọc
     * là **công tắc chết**, đúng thứ dự án cấm (`themeMode` từng như vậy suốt S1).
     *
     * Bài `luat icon cua hang nut theo dung du lieu that cua 3 nhom co nut` ở `:core` canh phần *quyết định*; bài này
     * canh phần *dây nối*.
     */
    @Test
    fun `hang nut hoi core xem icon co phan biet duoc khong`() {
        val fn = SourceRoots.body(tiles, "internal fun actionsRow(")
        assertTrue(
            fn.contains("icons = m.actionIconsDistinguish"),
            "hàng nút phải HỎI :core; tự quyết ở tầng vẽ là chép lại quy ước icon lần thứ hai",
        )
        val factory = code("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt")
        assertTrue(
            SourceRoots.body(factory, "fun actionTile(").contains("if (icons) content.addView(icon)"),
            "và bộ dựng ô phải thật sự BỎ icon khi được bảo là icon vô nghĩa",
        )
        assertTrue(
            SourceRoots.body(factory, "fun macroTile(").contains("if (icons) tile.addView(icon"),
            "ô gói lệnh trong CÙNG hàng cũng theo cùng luật — không thì một hàng có hai kiểu ô",
        )
    }
}
