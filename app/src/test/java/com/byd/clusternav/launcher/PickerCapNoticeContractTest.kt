package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KIỂM TOÁN UX mục 5 — BẢNG CHỌN KHÔNG ĐƯỢC CHẶN IM LẶNG ══════════════════════════════════════════════════
 *
 * ## Bệnh nó chữa — [ĐO] máy ảo 2026-09-12
 * Ngăn kéo chọn nội dung ô có trần **8 mục**. Đang chọn 8 rồi bấm thêm *Lốp* / *Kính* / *Khí hậu*: vẫn 8 mục,
 * **không một lời nào** — không thông báo, không đổi màu ô, không câu nhắc. Bỏ một mục xuống 7 thì lại bấm được. Tức
 * cú bấm của người dùng **biến mất** và không có gì giải thích.
 *
 * Đây đúng họ lỗi mà dự án đã trả giá ở `DockConfig.setEnabled` (mã không phải nút ⇒ `return this`, bỏ qua im lặng —
 * RW0 phải nới nó ra mới đặt được ô đọc lên thanh nút). Bài này canh **nguyên nhân**: nhánh *"quá trần thì thôi"*
 * không được tồn tại mà không có đường nói cho người dùng.
 *
 * ## Vì sao bài nằm ở `:app`
 * Nó quét **mã nguồn của `:app`**. Bài quét mã module X mà đặt ở module Y thì Gradle không coi tệp của X là đầu vào
 * của `Y:test` ⇒ báo `UP-TO-DATE` và **không bao giờ chạy lại** — cái bẫy đã cho một dấu xanh sai ở S1.
 */
class PickerCapNoticeContractTest {

    private val drawer by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/AppDrawer.kt") }

    /**
     * Nhánh *"đầy trần thì bỏ qua"* phải **nói ra**, và phải nằm ở ĐÚNG MỘT chỗ.
     *
     * Mẫu bị cấm là chính mẫu cũ: `else if (selected.size < MAX) selected.add(...)` — một `if` không có `else`, tức
     * mọi cú bấm quá trần rơi vào hư không.
     */
    @Test
    fun `tran o khong duoc chan im lang`() {
        assertFalse(
            Regex("""else if \(selected\.size < (MAX|cap)\)""").containsMatchIn(drawer),
            "nhánh 'quá trần thì thôi' không có else = bỏ qua IM LẶNG. Đi qua toggleSelection() để có câu nói.",
        )
        val fn = SourceRoots.body(drawer, "private fun toggleSelection(")
        assertTrue(fn.contains("notice("), "quá trần phải NÓI ra cho người vừa bấm")
        assertTrue(fn.contains("capNote()"), "và nói bằng đúng một câu dùng chung (không viết hai bản chữ)")
        assertTrue(fn.contains("return"), "và KHÔNG âm thầm đi tiếp như thể đã thêm")
    }

    /**
     * ⚠ T6 — TRẦN LÀ **CỦA MỘT CHẾ ĐỘ**, KHÔNG PHẢI MỘT HẰNG TOÀN CỤC.
     *
     * Ngăn kéo nay có chế độ thứ ba (chọn nút cho **thanh nút xe**, R-UI (m)) và thanh nút **không có trần**:
     * `DockConfig.enabled` là `List<String>` dài tuỳ ý, mặc định đã 8 mục. Nếu bảng vẫn dùng thẳng hằng `MAX` = 8
     * thì mở bộ chọn cho một cấu hình 10 nút sẽ **cắt mất 2 nút mà không nói gì** — đúng bệnh mà cả tệp test này
     * sinh ra để chống, chỉ đi vào bằng cửa khác (`initialWidgets.take(...)`).
     *
     * Nên các bài dưới hỏi `cap` (trần **của bảng đang mở**) thay cho `MAX`; bài này chốt rằng `cap` vẫn **bắt
     * nguồn từ** `MAX` cho hai chế độ gán-ô, để không ai lặng lẽ nới trần ô giữa màn bằng cách sửa một dòng.
     */
    @Test
    fun `tran la cua che do, va che do gan o van lay tu MAX`() {
        assertTrue(
            Regex("""val cap: Int = if \(mode == Mode\.PICK_DOCK\) NO_CAP else MAX""").containsMatchIn(drawer),
            "trần phải tính theo CHẾ ĐỘ: thanh nút xe không trần, ô giữa màn vẫn đúng MAX",
        )
        assertTrue(
            Regex("""initialWidgets\.take\(cap\)""").containsMatchIn(drawer),
            "tập chọn sẵn phải cắt theo `cap`, không theo MAX — cắt theo MAX là bỏ im lặng nút thứ 9 của thanh",
        )
        assertTrue(drawer.contains("const val NO_CAP = Int.MAX_VALUE"), "\"không trần\" phải là một con số có tên")
    }

    /**
     * ⚠⚠ **[KIỂM TOÁN 2026-09-12 mục 1] KÊNH NÓI CỦA MỘT CỬA SỔ PHỦ KHÔNG ĐƯỢC LÀ TOAST.**
     *
     * ## [ĐO] bằng số — máy ảo 2026-09-12
     * `dumpsys window` lúc toast đang lên: toast `ty=TOAST mBaseLayer=81000` khung `[655,969][1264,1044]`; ngăn kéo
     * `ty=APPLICATION_OVERLAY mBaseLayer=121000` khung `[0,0][1920,1080]`. 121000 > 81000 và cửa sổ phủ **kín màn** ⇒
     * so hai ảnh chụp trước/sau cú bấm bị từ chối: dải CHỮ của toast (`y 969..1037`) đổi **0 pixel**; chỉ 7px lọt ra
     * dưới đáy bảng (`y 1037..1044`) là đổi. Tức mã "đã nói" mà người dùng **không nghe được gì** — kênh im lặng, đúng
     * họ lỗi bài này sinh ra để chặn, chỉ ở một tầng thấp hơn (bài cũ chặn *thiếu lời nói*, ca này là *nói vào chỗ
     * không ai thấy*).
     *
     * Luật chỉ áp cho **bề mặt phủ** (`TYPE_APPLICATION_OVERLAY`): toast ở màn Cài đặt vẫn hiện đủ chữ vì bảng đó là
     * con của cửa sổ Activity (`mBaseLayer` ~21000 < 81000). Nên đây KHÔNG phải "bỏ toast trong toàn dự án".
     */
    @Test
    fun `be mat phu khong duoc noi bang Toast`() {
        listOf("AppDrawer.kt", "OverlayHeads.kt").forEach { file ->
            val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")
            assertFalse(
                src.contains("Toast"),
                "$file mở bằng TYPE_APPLICATION_OVERLAY (mBaseLayer 121000) nên toast (81000) nằm DƯỚI nó và bị che " +
                    "hoàn toàn — [ĐO] 0 pixel chữ. Nói bằng một view NẰM TRONG chính bảng đó.",
            )
        }
    }

    /** Câu nói phải đi vào một view nằm trong bảng, và phải ĐỔI HÌNH (không chỉ đổi chữ). */
    @Test
    fun `cau noi nam trong bang va nhin ra duoc`() {
        val fn = SourceRoots.body(drawer, "private fun notice(")
        assertTrue(fn.contains("capHint"), "câu nói phải đi vào dòng chữ ghim ở thanh đáy của CHÍNH bảng này")
        assertTrue(
            fn.contains("AMBER"),
            "phải đổi màu/nền: khi đã đủ trần thì dòng đó VỐN ĐÃ hiện sẵn câu nhắc, nên đặt lại cùng một chữ là " +
                "trên màn không có gì đổi — vẫn là im lặng, chỉ khó thấy hơn",
        )
        // Và phải trả về dáng THÔNG TIN khi người dùng đã bỏ một mục ra (không thì nền hổ phách nói một điều đã sai).
        val reset = SourceRoots.body(drawer, "private fun refreshPlaceBtn(")
        assertTrue(reset.contains("background = null"), "bỏ mục ra ⇒ dòng nhắc phải về dáng thường")
    }

    /** Câu nói phải nêu **cả trần lẫn đường đi tiếp** — "đã đủ 8" một mình không cho người dùng biết làm gì. */
    @Test
    fun `cau nhac tran neu ca so va cach di tiep`() {
        // U5·T3 — câu chữ dời sang tài nguyên (`kachi_drawer_cap_note`), nên bài này kiểm HAI nửa:
        //   (a) NỘI DUNG câu — đọc thẳng tệp tài nguyên bản Việt (không kiểm được ở `.kt` nữa);
        //   (b) SỐ TRẦN vẫn chảy từ hằng `MAX` chứ không bị gõ lại trong bản dịch.
        // Cả hai tính chất y như trước, chỉ đổi chỗ đọc.
        val note = res("kachi_drawer_cap_note")
        assertTrue(note.contains("%1\$d"), "câu nhắc phải NHẬN số trần làm tham số, không viết số vào bản dịch")
        assertTrue(note.contains("bỏ"), "phải nói cách đi tiếp: bỏ một mục ra")
        assertTrue(
            drawer.contains("R.string.kachi_drawer_cap_note, MAX)"),
            "số trần phải lấy từ hằng MAX, không gõ lại (gõ lại là hai bản sao)",
        )
        assertFalse(
            Regex("""tối đa 8|at most 8""").containsMatchIn(note), "KHÔNG gõ số 8 vào chữ — nó phải theo hằng MAX",
        )
    }

    /**
     * Cả HAI loại ô (widget dựng tay + mục khả năng) phải đi qua **cùng** đường chọn.
     *
     * Hai bản sao của một luật là cách chắc chắn để một bản được sửa và bản kia không — lỗi cũ chính là hai chỗ viết
     * cùng một nhánh trần.
     */
    @Test
    fun `hai loai o dung chung mot duong chon`() {
        assertEquals(
            2, Regex("""toggleSelection\(""").findAll(SourceRoots.codeOf(
                "src/main/java/com/byd/clusternav/launcher/AppDrawer.kt",
            )).count() - 1,
            "đúng hai chỗ GỌI toggleSelection (ô widget + ô khả năng), ngoài chính chỗ khai",
        )
    }

    /**
     * Trạng thái *"không còn chọn được"* phải nhìn ra được **trước khi bấm**.
     *
     * Toast là lớp thứ hai (cho người đã bấm); lớp thứ nhất là ô mờ đi. Không có lớp thứ nhất thì người dùng vẫn
     * phải bấm-rồi-đọc mới biết, tức vẫn là "thử xem có được không".
     */
    @Test
    fun `o het cho phai mo di truoc khi bam`() {
        val fn = SourceRoots.body(drawer, "private fun applyTileState(")
        assertTrue(fn.contains("alpha"), "ô không còn chọn được phải mờ đi")
        // `cap` = trần của CHẾ ĐỘ đang mở (xem bài `tran la cua che do...`), không phải hằng MAX.
        assertTrue(fn.contains("selected.size < cap"), "và điều kiện mờ phải là chính cái trần")
    }

    /**
     * ⚠⚠ Nút áp cấu hình phải nằm **NGOÀI** vùng cuộn (mục 5a).
     *
     * [ĐO] trước bản vá nó nằm trong thân cuộn: cuộn xuống là mất nút (điểm sáng vùng nút 7242 → 83 → 0). Bài này
     * canh **thứ tự dựng**: nút được thêm vào `panel` SAU `ScrollView`, nên nó là một hàng riêng ở đáy bảng.
     */
    @Test
    fun `nut ap cau hinh ghim ngoai vung cuon`() {
        val scrollAt = drawer.indexOf("ScrollView(context)")
        val barAt = drawer.indexOf("panel.addView(placeBar()")
        assertTrue(scrollAt > 0, "bảng phải có vùng cuộn")
        assertTrue(barAt > scrollAt, "thanh nút phải được thêm SAU vùng cuộn ⇒ nằm ngoài nó, ghim ở đáy")
        val bar = SourceRoots.body(drawer, "private fun placeBar()")
        assertTrue(bar.contains("onPickWidgets("), "vẫn là MỘT đường ghi cấu hình như trước")
        // Và thân cuộn KHÔNG được chứa nút nữa.
        assertFalse(
            drawer.substring(0, scrollAt).contains("body.addView(head)"),
            "nút không được nằm trong thân cuộn nữa",
        )
    }

    /** Mép vùng cuộn phải MỜ dần, không cắt ngang chữ (mục 5c). */
    @Test
    fun `mep vung cuon mo dan khong cat chu`() {
        assertTrue(drawer.contains("isVerticalFadingEdgeEnabled = true"), "mép cuộn phải mờ dần")
        assertTrue(drawer.contains("setFadingEdgeLength("), "và phải khai độ dài dải mờ")
        // ⚠ BUG (O) 2026-09-16 [ĐÃ CHỨNG MINH View.java r47 :20893-20897]: mép mờ tính theo HỘP ĐỆM của ScrollView,
        // nên `setPadding` + `clipToPadding = false` đặt dải mờ vào GIỮA nội dung ⇒ một hàng ô bị cắt ngang. Đệm phải
        // là view đệm TRONG thân cuộn; hai dòng dưới chặn cách cũ mọc lại.
        assertFalse(drawer.contains("clipToPadding = false"), "cấm `clipToPadding = false` trên vùng cuộn: dải mờ sẽ rơi vào giữa nội dung (BUG (O))")
        assertFalse(
            Regex("""ScrollView\(context\)\.apply \{[^}]*setPadding\(""").containsMatchIn(drawer),
            "cấm `setPadding` trên ScrollView: đệm phải là view đệm trong thân cuộn (BUG (O))",
        )
    }

    /**
     * Chữ THẬT sẽ hiện trên màn, đọc từ tệp tài nguyên bản Việt.
     *
     * ⚠ U5·T3 — trước đây bài này đọc chuỗi viết cứng trong `.kt`. Chữ nay nằm trong `res/values/strings_kachi.xml`,
     * nên phép kiểm phải đi tới đó: nếu chỉ kiểm *"mã có gọi khoá này không"* thì ai xoá nội dung câu cảnh báo vẫn
     * xanh. `app/build.gradle.kts` đã khai `inputs.dir("src/main/res")` nên đổi tệp đó là task chạy lại.
     */
    private fun res(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(SourceRoots.text("src/main/res/values/strings_kachi.xml"))
            ?.groupValues?.get(1)
            ?: error("không có chuỗi '$name' trong values/strings_kachi.xml — bài test đang quét vùng không tồn tại")

}
