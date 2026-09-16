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

    /**
     * ⚠ [SOÁT 1.69 · P2] Icon bung trên luồng nền phải về luồng vẽ qua **Handler của main looper**, KHÔNG qua
     * `View.post` của chính ô.
     *
     * Bản vá "bung icon ngoài luồng vẽ" (soát OCR #42) xếp hàng lượt bung **ngay khi dựng ô**, lúc ô còn chưa có
     * cha — cả lưới chỉ gắn vào cửa sổ ở cuối lượt dựng ngăn kéo. `View.post` trên một view **chưa gắn** không đi
     * qua Handler mà đẩy vào `mRunQueue` của view: một hàng đợi KHÔNG đồng bộ, do luồng vẽ rút ra ở
     * `dispatchAttachedToWindow`. Ghi vào nó từ luồng nền là đua với chính lượt gắn ấy — nhẹ thì mất icon, nặng
     * thì ném giữa lượt mở ngăn kéo. Đổi `MAIN.post` về `post` thì ca này ĐỎ.
     */
    @Test
    fun `icon bung o luong nen ve luong ve bang Handler chu khong bang View post`() {
        val src = code("AppDrawerApps.kt")
        assertTrue(src.contains("Looper.getMainLooper()"), "phải có đường về luồng vẽ bằng Handler của main looper")
        val exec = SourceRoots.body(src, "ICONS.execute {")
        assertTrue(exec.contains("MAIN.post"), "khối bung icon phải gửi kết quả qua MAIN.post — thấy: $exec")
        assertFalse(
            Regex("""(?<![A-Za-z.])post\s*\{""").containsMatchIn(exec),
            "`View.post` trên ô CHƯA GẮN đẩy vào mRunQueue (không đồng bộ) ⇒ đua với lượt gắn: $exec",
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

    /**
     * ⚠ **VISUAL-REFRESH P1 · T3 (2026-09-16) — bài này đổi CÁCH KIỂM, không đổi TÍNH CHẤT được kiểm.**
     *
     * Trước P1 nó khoá đúng một chuỗi: `KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.FIELD)`. Tính chất thật
     * mà R-UI (g) cần là *"ô chưa chọn CÓ nền, và nền ấy là **cùng một bề mặt** với lưới chọn trong Cài đặt"* —
     * chuỗi kia chỉ là **cách hiện thời** để đạt nó. P1 đổi cả hai lưới sang [KachiTheme.surface], nên khoá chuỗi
     * cũ sẽ đỏ đúng ở lượt làm cả hai lưới *khớp nhau hơn* — tức là bài canh chống lại chính mục đích của nó.
     *
     * Bản mới khoá thẳng tính chất: (a) không nhánh `else null`; (b) cả [AppDrawer] lẫn [TopStripPicker] đều dựng
     * nền qua **cùng một hàm**. Nó chặt hơn bản cũ ở chỗ nó so **hai** tệp với nhau thay vì tin một chuỗi.
     */
    @Test
    fun `o chua chon co nen mo de lo ranh gioi`() {
        val fn = SourceRoots.body(drawer, "private fun applyTileState(")
        assertFalse(
            Regex("""\}\s*else null""").containsMatchIn(fn),
            "ô chưa chọn để nền `null` ⇒ hai ô cạnh nhau đọc thành một khối liền mạch (soát ảnh Pass 2)",
        )
        assertTrue(
            fn.contains("KachiTheme.surface("),
            "ô chưa chọn phải có nền dựng từ hệ thiết kế (KachiTheme.surface)",
        )
        val stripPaint = SourceRoots.body(code("TopStripPicker.kt"), "private fun paint(")
        assertTrue(
            stripPaint.contains("KachiTheme.surface("),
            "lưới chọn chip của Cài đặt phải dùng CÙNG hàm dựng bề mặt với ngăn kéo — hai lưới bày cùng một tập ô " +
                "thì chúng phải đọc như một; hai cách vẽ là cách chúng trôi khỏi nhau (đã lệch 3 lần: cột 4-vs-5, " +
                "thụt 6px, cỡ chữ ngoài thang).",
        )
        assertTrue(
            "SurfaceTone.ACTIVE" in fn && "SurfaceTone.ACTIVE" in stripPaint,
            "trạng thái ĐANG CHỌN của cả hai lưới cũng phải đi qua cùng một `tone`, không ai dựng Drawable tại chỗ",
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
        // BUG (O) 2026-09-16: đệm đáy nay là VIEW ĐỆM cuối thân cuộn (không còn `setPadding` trên ScrollView — xem
        // `PickerCapNoticeContractTest.mep vung cuon mo dan khong cat chu`); quan hệ "đệm đáy > dải mờ" giữ nguyên.
        assertTrue(
            Regex("""body\.addView\(View\(context\), LinearLayout\.LayoutParams\(LinearLayout\.LayoutParams\.MATCH_PARENT, dpi\(context, Sp\.TOUCH\) \+ dpi\(context, Sp\.S\)\)\)""")
                .containsMatchIn(drawer),
            "đệm đáy = view đệm cao nút áp (Sp.TOUCH) + một nhịp (Sp.S) ở CUỐI thân cuộn ⇒ hàng cuối cuộn tới được đầy đủ",
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
