package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T6 — NHỊP LƯỚI Ô CHỌN + ĐÍCH CHẠM, HAI THỨ "NHÌN THẤY BẰNG MẮT" ĐÃ CÓ MÁY CANH ══════════════════════════
 *
 * Nguồn: `kachi-settings-ia-v2.html` §3 **R5 · R7 · R-UI (e)(f)(g)**; `kachi-design-system.html` §10 Pass 2 (soát
 * ảnh độc lập) `[P1]` + `[P2]`.
 *
 * ## Vì sao phải có bài canh, không chỉ "đã sửa rồi"
 * Bốn phát hiện dưới đây đều từng được vá ở **một** bề mặt rồi lỗi mọc lại ở bề mặt kia:
 *  • khe/đồng cao vá ở lưới Cài đặt (pha 1) — ba lưới của ngăn kéo vẫn dính 0px (Pass 2 `[P1]`);
 *  • `PickGridColumnContractTest` đã ghi đúng bài học đó cho **số cột**: *vá xong phải chặn NGUYÊN NHÂN bằng một
 *    bài canh*, không thì bản vá chỉ sống tới lần sửa giao diện kế tiếp.
 * Nên bài này canh **nguồn duy nhất** (mọi lưới đi qua [CapabilityTileGrid]) chứ không canh từng con số ở từng chỗ.
 */
class DrawerGridSeamContractTest {

    private fun code(f: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$f")

    private val grid by lazy { code("CapabilityTileGrid.kt") }
    private val drawer by lazy { code("AppDrawer.kt") }

    /**
     * Mọi tệp dựng lưới ô chọn — ngăn kéo (3 lưới) + lưới app + bộ chọn chip thanh trạng thái.
     *
     * ⚠ T4 · R-UI (m): `CapabilityGridSection.kt` đã **XOÁ** (lưới 123 ô rời khỏi Settings, thay bằng bộ chọn của
     * ngăn kéo ở chế độ `PICK_DOCK`). Lưới còn lại trong Cài đặt là bộ chọn chip ([TopStripPicker]) — nó nay cũng
     * đi qua [CapabilityTileGrid] nên phải nằm trong bài canh này, không thì một bề mặt đã vá tự rơi ra khỏi phạm vi.
     */
    private val gridUsers = listOf(
        "AppDrawer.kt", "AppDrawerApps.kt", "TopStripPicker.kt",
    )

    // ══ (1) R5 — MỘT nguồn cho khe + đồng cao ═════════════════════════════════════════════════════════════

    @Test
    fun `moi luoi o chon di qua mot nguon duy nhat`() {
        gridUsers.forEach { f ->
            assertTrue(
                code(f).contains("CapabilityTileGrid.rows("),
                "$f phải xếp hàng qua CapabilityTileGrid — tự dựng hàng là bản sao thứ hai của kỷ luật khoảng cách, " +
                    "và bản sao đó chính là lý do ngăn kéo còn dính 0px sau khi Cài đặt đã được vá",
            )
        }
    }

    /**
     * Chặn **cách lỗi mọc lại**: dựng `LinearLayout` HÀNG NGANG ngay tại chỗ rồi tự `addView` ô vào đó.
     *
     * Đếm chứ không chỉ `assertFalse(contains)`: ngăn kéo vẫn có `LinearLayout` hàng ngang hợp lệ (thanh đáy ghim
     * nút áp), nên bài phải nói đúng con số thay vì cấm sạch.
     */
    @Test
    fun `khong ai tu dung hang ngang de xep o nua`() {
        gridUsers.forEach { f ->
            val n = Regex("""orientation = LinearLayout\.HORIZONTAL""").findAll(code(f)).count()
            val allowed = if (f == "AppDrawer.kt") 1 else 0     // 1 = thanh đáy (câu nhắc + nút áp)
            assertEquals(
                allowed, n,
                "$f có $n hàng ngang dựng tay (cho phép $allowed) — hàng của LƯỚI phải do CapabilityTileGrid dựng",
            )
        }
    }

    /** Ba tính chất hình học nằm ở đúng một chỗ, và là hằng của thang — không phải số trần. */
    @Test
    fun `khe doc S khe ngang XS o dong cao hai pha`() {
        assertTrue(
            Regex("""bottomMargin = dpi\(context, Sp\.S\)""").containsMatchIn(grid),
            "khe DỌC giữa hai hàng = Sp.S (trước đây 0px ⇒ ô hàng trên chạm ô hàng dưới)",
        )
        assertEquals(
            2, Regex("""dpi\(context, Sp\.XS\)""").findAll(grid).count(),
            "khe NGANG = Sp.XS ở CẢ HAI mép TRONG của ô (một bên thôi là lưới lệch về một phía)",
        )
        assertTrue(
            Regex("""marginStart = if \(col == 0\) 0 else dpi\(context, Sp\.XS\)""").containsMatchIn(grid) &&
                Regex("""marginEnd = if \(col == cols - 1\) 0 else dpi\(context, Sp\.XS\)""").containsMatchIn(grid),
            "mép NGOÀI của hàng phải PHẲNG (ô đầu/cuối bỏ lề): [ĐO] soát ảnh v2 lưới bắt đầu ở x=96 còn tiêu đề " +
                "ở x=85 — đúng một Sp.XS, mắt đọc ra 'lưới lệch khỏi cột chữ'",
        )
        assertTrue(
            Regex("""View\(context\), cellLp\(context, 0,""").containsMatchIn(grid),
            "ô CHÈN cao đúng 0: View trơ WRAP_CONTENT giãn hết dưới spec AT_MOST và kéo cả hàng phình theo",
        )
    }

    /**
     * ĐỒNG CAO phải do HÀNG tự đo, KHÔNG nhờ `MATCH_PARENT` + `forceUniformHeight` của [android.widget.LinearLayout].
     *
     * [ĐO] 2026-09-13: hàng ô chip thanh trạng thái (3 ô / 4 cột ⇒ có ô chèn) vẽ **cao 0** —
     * `LinearLayout.java:1408` tắt `allFillParent` vì ô chèn không `MATCH_PARENT`, `:1470` hạ `maxHeight` về
     * `alternativeMaxHeight` = 0 dưới `ScrollView` (`heightMode = UNSPECIFIED`), rồi `:1492` ép mọi ô về
     * `EXACTLY 0`. Tức cách cũ để chiều cao ô phụ thuộc **spec của cha** và **thành phần của hàng**. Bài này khoá
     * đường mới (đo hai pha) để bản vá không bị "dọn" ngược lại thành `MATCH_PARENT` cho gọn.
     */
    @Test
    fun `hang tu do hai pha, khong nho MATCH_PARENT`() {
        assertFalse(
            Regex("""cellLp\(context, MATCH""").containsMatchIn(grid),
            "ô cao MATCH_PARENT = quay lại forceUniformHeight ⇒ hàng thiếu ô lại vẽ cao 0",
        )
        assertTrue(
            grid.contains("cellLp(context, WRAP, col, cols)"),
            "ô THẬT khai WRAP_CONTENT — chiều cao tự nhiên là dữ liệu vào của pha 2",
        )
        val fn = SourceRoots.body(grid, "override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)")
        assertTrue(fn.contains("super.onMeasure(widthMeasureSpec, heightMeasureSpec)"), "pha 1 = đo tự nhiên")
        assertTrue(
            fn.contains("MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY)"),
            "pha 2 = đo lại ô thấp bằng EXACTLY(cao nhất) — đó là chỗ 'đồng cao' thật sự xảy ra",
        )
        assertFalse(
            fn.contains("minimumHeight"),
            "KHÔNG dùng minimumHeight để đồng cao: nó gọi requestLayout() giữa lượt đo (vòng lặp bố cục)",
        )
    }

    /** Ô trong lưới căn DỌC-TRÊN — căn giữa dọc làm icon ô nhãn ngắn tụt xuống lệch với ô cùng hàng. */
    @Test
    fun `o trong luoi can tren, khong can giua doc`() {
        listOf("AppDrawer.kt", "AppDrawerApps.kt", "TopStripPicker.kt").forEach { f ->
            val src = code(f)
            val topAligned = Regex("""Gravity\.CENTER_HORIZONTAL or Gravity\.TOP""").findAll(src).count()
            assertTrue(topAligned >= 1, "$f: ô lưới phải căn NGANG-giữa + DỌC-TRÊN")
        }
        // Và không còn ô lưới nào căn giữa cả hai chiều trong ngăn kéo.
        assertEquals(
            0, Regex("""orientation = LinearLayout\.VERTICAL; gravity = Gravity\.CENTER\b""").findAll(drawer).count(),
            "ô lưới căn giữa DỌC ⇒ lệch với ô hai dòng cùng hàng; đó đúng lỗi 'không đồng cao' vừa vá",
        )
    }

    // ══ (2) R-UI (g) — ô CHƯA CHỌN phải có nền ════════════════════════════════════════════════════════════

    @Test
    fun `o chua chon co nen mo de lo ranh gioi`() {
        val fn = SourceRoots.body(drawer, "private fun applyTileState(")
        assertFalse(
            Regex("""\}\s*else null""").containsMatchIn(fn),
            "ô chưa chọn để nền `null` ⇒ hai ô cạnh nhau đọc thành một khối liền mạch (soát ảnh Pass 2)",
        )
        assertTrue(
            fn.contains("KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.FIELD)"),
            "phải dùng CÙNG nền FIELD mà lưới trong Cài đặt đã dùng — hai bề mặt phải đọc như một",
        )
    }

    // ══ (3) R-UI (e) — đáy vùng cuộn không cắt chữ ════════════════════════════════════════════════════════

    /**
     * Đệm đáy phải LỚN HƠN dải mờ, không thì hàng cuối không bao giờ ra khỏi vùng bị fade.
     *
     * Canh **quan hệ giữa hai số** chứ không canh trị số: khoá trị số sẽ chặn cả việc chỉnh hợp lệ về sau, mà tính
     * chất cần giữ chỉ là *"đệm > dải mờ"*.
     */
    @Test
    fun `day vung cuon cao hon dai mo`() {
        assertTrue(
            Regex("""setPadding\(0, dpi\(context, Sp\.XS\), 0, dpi\(context, Sp\.TOUCH\) \+ dpi\(context, Sp\.S\)\)""")
                .containsMatchIn(drawer),
            "đệm đáy = cao nút áp (Sp.TOUCH) + một nhịp (Sp.S) ⇒ hàng cuối cuộn tới được đầy đủ",
        )
        assertTrue(
            Regex("""setFadingEdgeLength\(dpi\(context, Sp\.M\)\)""").containsMatchIn(drawer),
            "dải mờ = Sp.M: [ĐO] soát ảnh v2 dải Sp.XL (20dp) vẫn làm mờ NHÃN của hàng cuối, không chỉ làm mờ khe",
        )
        assertTrue(
            KachiSpace.TOUCH + KachiSpace.S > KachiSpace.M,
            "đệm đáy (${KachiSpace.TOUCH + KachiSpace.S}dp) phải > dải mờ (${KachiSpace.M}dp), không thì nhãn " +
                "hàng cuối vẫn nằm trong dải fade dù đã cuộn hết cỡ",
        )
    }

    // ══ (4) R-UI (f) — nút ⇄ nổi phải nhìn ra được ════════════════════════════════════════════════════════

    @Test
    fun `nut swap noi khong con vien - kin dao theo owner 2026-09-14`() {
        // 2026-09-13: viền Sp.STROKE (2dp) từng là câu trả lời cho "ruột nút chỉ hơn nền 1.19:1". 2026-09-14 owner
        // đảo chiều: *"nút switch app trên khung làm kín đáo, nhỏ gọn, không cần khung viền, border gì"* ⇒ hình là
        // CHỈ icon nhỏ tô màu mờ; tách khỏi nền bằng chính nét icon, không bằng viền. Đích chạm 48dp không đổi
        // (SlotHeadParityContractTest.`dich cham nut swap la TOUCH…`).
        val fn = SourceRoots.body(code("SlotSwapButton.kt"), "fun build(context: Context, onTap: () -> Unit): ImageView")
        assertFalse(fn.contains("setStroke("), "nút ⇄ không còn viền — owner 2026-09-14")
        assertFalse(fn.contains("GradientDrawable.OVAL"), "nút ⇄ không còn nền oval — owner 2026-09-14")
        assertTrue(fn.contains("background = null"), "nền phải TƯỜNG MINH là null, không để ImageView tự kế thừa nền nào")
        assertTrue(fn.contains("KachiTheme.MUT"), "icon tô màu mờ (MUT) — kín đáo, không phải ON_ACCENT trắng nổi")
    }
}
