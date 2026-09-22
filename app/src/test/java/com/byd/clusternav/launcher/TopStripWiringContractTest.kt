package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RW0 vùng thứ ba — dây nối thanh trạng thái trên.
 *
 * Bài này canh **hình dạng của dây nối**, thứ mà test hành vi ở `:core` không thấy được: bộ vẽ có thật sự đi qua
 * [TopStripChips] không, cấu hình có thật sự lấy từ state không, và chuyện làm mới có còn giữ bản vá "dựng một lần,
 * đổi chữ tại chỗ" (P2-9) hay lại quay về `removeAllViews()` mỗi nhịp.
 */
class TopStripWiringContractTest {

    /** Đọc mã và BỎ chú thích — cùng cách với các bài canh khác (chú thích trích dẫn mã cũ sẽ làm bài xanh giả). */
    private fun code(relative: String): String = SourceRoots.text(relative)
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val strip by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val picker by lazy { code("src/main/java/com/byd/clusternav/launcher/TopStripPicker.kt") }
    /** T4 · R-UI (a): mục chọn chip nay ở nhóm "Thanh trạng thái & thanh nút", không còn ở "Màn hình chính". */
    private val panel by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsBars.kt") }

    private val vm by lazy { code("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt") }
    private val prefs by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt") }

    @Test
    fun `chu cua chip do core quyet dinh khong phai bo ve`() {
        val fn = SourceRoots.body(strip, "fun refreshChips(")
        assertTrue(fn.contains("TopStripChips.render("), "bộ vẽ phải hỏi `:core` chữ gì, không tự ghép chuỗi")
        // Ba chip cũ được ghép TẠI ĐÂY bằng tay — đó là lý do thanh trên từng bỏ qua lựa chọn đơn vị.
        assertTrue(!fn.contains("\"PM2.5 · "), "không được ghép chuỗi chip tại tầng vẽ nữa")
        assertTrue(!fn.contains("ngoài\""), "không được ghép chuỗi chip tại tầng vẽ nữa")
    }

    @Test
    fun `chip dung mot lan roi doi chu tai cho`() {
        // Giữ bản vá [SOÁT P2-9]: dựng lại 3 TextView + tra + tint drawable mỗi nhịp trạng thái xe = mỗi giây trên xe.
        val fn = SourceRoots.body(strip, "fun refreshChips(")
        assertTrue(fn.contains("chipViews.size != chips.size"), "chỉ dựng lại khi DANH SÁCH đổi")
        assertTrue(fn.contains("v.text = c.text"), "trường hợp thường phải là đổi chữ tại chỗ")
        assertTrue(fn.contains("v.tag"), "icon/màu chỉ đặt lại khi đổi — tra drawable mỗi giây là việc P2-9 vừa dọn")
    }

    @Test
    fun `cau hinh chip lay tu state va co duong ghi ben`() {
        assertTrue(activity.contains("state.topStrip"), "bộ vẽ phải lấy cấu hình từ state, không giữ bản sao riêng")
        assertTrue(vm.contains("fun setTopStrip("), "phải có intent ghi qua ViewModel")
        assertTrue(vm.contains("repository.setTopStrip("), "state và lưu bền phải đi trong MỘT lượt")
        assertTrue(prefs.contains("TopStripConfig.decode("), "phải nạp lại được sau khi tắt app")
        // Nạp trong `load()` ⇒ ca ĐỔI HỒ SƠ tự đúng (bài học P1-1: nạp bằng tay ở tầng UI thì sẽ có lần quên).
        val repo = code("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt")
        assertTrue(SourceRoots.body(repo, "override fun load()").contains("topStrip = prefs.topStrip()"),
            "phải nạp cùng lượt với mọi thứ khác, không nạp riêng ở tầng UI")
    }

    @Test
    fun `doi cau hinh chip thi man hinh phai doi ngay`() {
        // [ĐO] bản đầu chỉ làm mới khi `carStatus` đổi ⇒ off-car (trạng thái xe không đổi) bấm chọn chip mà màn hình
        // không đổi gì. Cùng họ lỗi với nút bố cục sẵn ở P9: hành động tường minh phải có tác dụng.
        val fn = SourceRoots.body(activity, "private fun render(")
        assertTrue(fn.contains("prev.topStrip != state.topStrip"), "điều kiện làm mới phải xét cả cấu hình chip")
    }

    @Test
    fun `nguoi dung co duong sua danh sach chip`() {
        assertTrue(panel.contains("stripPicker.section("), "màn Cài đặt phải bày mục chọn chip")
        assertTrue(picker.contains("TopStripConfig.choices()") || picker.contains("TopStripConfig.picks("),
            "màn chọn phải lấy từ `:core`, không tự liệt kê")
        // ⚠⚠ S4 · R11 (c) — ĐƯỜNG ĐẶT DATUM BẤT KỲ ĐỔI HÌNH LẦN THỨ HAI, VẪN KHÔNG BIẾN MẤT.
        // Lịch sử của đúng một tính năng (*"đặt MỘT datum bất kỳ lên thanh trên"*): T4 · R-UI (m) bỏ lưới 123 ô
        // khỏi Settings ⇒ lối GIỮ-ô mất theo ⇒ thay bằng nút "Thêm chip khác…" mở hộp thoại **phẳng**. R11 bỏ nốt
        // hộp thoại đó vì nó **giấu** danh sách (owner: *"hiện chỉ cho chọn 3 trong khi có thể chọn nhiều hơn"*) —
        // và thay bằng chính danh sách ấy bày thẳng trên trang, xếp theo lĩnh vực.
        // Phép kiểm vì thế cũng đổi: không hỏi *"có nút kia không"* nữa mà hỏi **"trang có bày HẾT không"**.
        assertTrue(
            SourceRoots.body(picker, "private fun rebuild()").contains("TopStripConfig.picks(strip)"),
            "màn chọn phải dựng từ `TopStripConfig.picks` — hàm bày ĐỦ mọi mục đọc theo lĩnh vực (bài canh phép " +
                "đếm thật nằm ở `:core`: TopStripTest.man chon bay du moi muc dat duoc…)",
        )
        assertFalse(
            picker.contains("openMore"),
            "hộp thoại 'Thêm chip khác…' phải hết hẳn, không để mã chết: danh sách nó bày nay nằm thẳng trên trang",
        )
        assertFalse(
            picker.contains("SettingsDialogs.pick("),
            "…và không được thay bằng một hộp thoại phẳng khác — 120 dòng trong một danh sách một cột là đúng thứ " +
                "R11 (c) bỏ đi",
        )
        // Gấp/mở phải đi theo LUẬT của `:core`, không phải một mặc định viết tay ở tầng vẽ (R4: nhóm ≤ 2 màn cuộn).
        assertTrue(picker.contains("s.open"), "mặc định gấp/mở của mỗi lĩnh vực phải đọc từ ChipSection.open")
    }

    /**
     * S4 · R11 (b) — **THANH TRÊN KHÔNG TRÀN**, dù có tới [TopStripConfig.CAP] chip.
     *
     * ## Vì sao canh bằng cách đọc mã chứ không đo pixel
     * Bề rộng thật chỉ có trên máy (test này chạy off-device, không có `Activity` nào để đo). Nhưng thứ quyết định
     * *có tràn hay không* là **hình dạng của bố cục**, và cái đó đọc được: (1) hàng chip là phần co giãn nên mọi
     * vật khác lấy bề rộng tự nhiên TRƯỚC, (2) mỗi chip nhận một trần bề rộng từ **phép chia** phần còn lại, (3)
     * chữ vượt trần thì cắt `…` chứ không đẩy ô rộng ra. Thiếu bất kỳ mảnh nào trong ba mảnh đó là tràn quay lại.
     * Phần [ĐO] (ảnh máy ảo 8 chip) thuộc S4 · T4 — bài này chặn đường lùi, không thay cho con mắt.
     */
    @Test
    fun `thanh tren khong tran - chip co tran be rong tu phep chia va cat duoi`() {
        // ⚠ UX-OVERHAUL · WP4 — cách ĐẶT của từng vật dời từ `build()` sang `lpFor()` (thứ tự do [HeaderLayout]
        // quyết, nên `build` chỉ còn DỰNG view). Tính chất được canh KHÔNG đổi, chỉ đổi chỗ đọc; và nó nay còn
        // mạnh hơn: `lpFor` gán weight theo LOẠI vật nên không tổ hợp thứ tự nào làm thanh có hai phần co giãn.
        val lp = SourceRoots.body(strip, "private fun lpFor(")
        assertTrue(
            lp.contains("HeaderItem.CHIPS -> LinearLayout.LayoutParams(0, WRAP, 1f)"),
            "hàng chip phải LÀ phần co giãn của thanh (0dp + weight 1): sắp kiểu cũ (đệm riêng mang weight, hàng " +
                "chip WRAP) thì LinearLayout đo hàng chip TRƯỚC ba vật bên phải ⇒ 8 chip đẩy 'Ứng dụng'/'Cài đặt'/" +
                "chip hồ sơ ra khỏi mép",
        )
        assertTrue(
            Regex("""LinearLayout\.LayoutParams\(0, WRAP, 1f\)""").findAll(lp).count() == 1,
            "ĐÚNG MỘT vật được co giãn — hai vật cùng weight thì phép chia của fitChips không còn là 'phần còn lại'",
        )
        val fit = SourceRoots.body(strip, "private fun fitChips()")
        assertTrue(fit.contains("chipRow.width"), "bề rộng còn lại phải đọc từ bố cục thật, không tự cộng trừ lại")
        // B6 (owner 2026-09-22): KHÔNG chia đều `room/n` nữa (cắt chip dài oan). Trần mỗi chip nay là mức RỘNG RÃI
        // (`room/2`) chỉ để chặn chip cá biệt khổng lồ; chip rộng theo nội dung (WRAP ở chipLp) + margin.
        assertTrue(Regex("""room\s*/\s*2""").containsMatchIn(fit), "trần mỗi chip = trần rộng rãi (room/2), không chia đều")
        assertFalse(Regex("""room\s*/\s*n""").containsMatchIn(fit), "KHÔNG chia đều room/n — cắt chip dài oan (bug B6)")
        assertTrue(fit.contains("maxWidth = cap"), "…và phải thật sự áp vào chip (`maxWidth`)")
        // Không cấp phát trong vòng tick: `fitChips` chạy theo nhịp trạng thái xe khi số chip đổi, và theo mỗi lượt
        // bố cục. Tra drawable / dựng paint ở đây là mở lại đúng việc mà bản vá P2-9 vừa dọn.
        listOf("getDrawable", "Paint(", "GradientDrawable").forEach {
            assertFalse(fit.contains(it), "fitChips cấp phát '$it' — nó chạy theo nhịp, phải là số học thuần")
        }
        val chip = SourceRoots.body(strip, "private fun chip(")
        assertTrue(chip.contains("maxLines = 1"), "chip một dòng")
        assertTrue(chip.contains("TruncateAt.END"), "chữ dài phải cắt '…' — không cắt thì trần bề rộng chỉ CẮT CỤT ô")
        // Trần số chip chỉ có MỘT nguồn (`:core`), kể cả ở tầng vẽ: câu nhắc "đầy rồi" và câu chú thích cùng đọc nó.
        assertTrue(
            Regex("""TopStripConfig\.CAP\b""").findAll(picker).count() >= 2,
            "cả câu chú thích lẫn câu nhắc-đầy của màn chọn phải đọc TopStripConfig.CAP, không viết số 8 tại chỗ",
        )
    }

    @Test
    fun `bo chon chip khong tu ghi ben`() {
        // Nguồn sự thật là `HomeUiState.topStrip`. Bộ chọn chỉ báo ra — nếu nó tự ghi thì có hai đường ghi và chúng
        // sẽ lệch nhau (đúng bẫy hai-bản-sao dự án vừa dọn).
        assertTrue(!picker.contains("WorkspacePrefs"), "bộ chọn không được chạm prefs")
        assertTrue(!picker.contains("setTopStrip("), "bộ chọn không được tự ghi bền")
    }

    // ══ ICON-STATE (2026-09-21) — sắc thái bật/tắt phải thành MÀU thật ════════════════════════════════════

    /**
     * MỌI [ChipTone] phải được tầng vẽ map sang một vai màu, và bảng map **không được có `else`**.
     *
     * Đây là chốt chống-rữa của lượt ICON-STATE ở phía `:app`: `when` trên một enum mà có `else` thì thêm sắc thái
     * mới **biên dịch xanh** rồi âm thầm vẽ ra màu của nhánh `else` — tức owner xin "icon sáng/mờ" mà nhận lại chip
     * xám như cũ, không một lỗi nào. Không `else` thì Kotlin bắt buộc khai đủ, và lỗi ấy thành lỗi biên dịch.
     */
    @Test
    fun `moi ChipTone deu co vai mau rieng, khong co nhanh else`() {
        val map = SourceRoots.body(strip, "val color = when (c.tone)")
        ChipTone.values().forEach { tone ->
            assertTrue(map.contains("ChipTone.${tone.name}"), "sắc thái ${tone.name} chưa được map sang màu")
        }
        assertFalse(
            Regex("""\belse\s*->""").containsMatchIn(map),
            "bảng map sắc thái KHÔNG được có `else` — nó biến một sắc thái mới thành lỗi im lặng",
        )
        // Trạng thái bật/tắt = màu, nên hai sắc thái này phải dùng vai KHÁC nhau và khác vai trung tính. Dùng lại
        // vai có sẵn (đã qua ContrastGuard ở CẢ hai bảng) chứ không thêm hex mới — `0 ma mau viet cung` canh phần đó.
        assertTrue(map.contains("ChipTone.ACTIVE -> KachiTheme.ACCENT_INK"), "ACTIVE phải là vai màu nhấn dùng làm CHỮ")
        assertTrue(map.contains("ChipTone.INACTIVE -> KachiTheme.MUT2"), "INACTIVE phải là vai chữ MỜ")
        assertFalse(
            map.contains("ChipTone.ACTIVE -> CHIP_INK") || map.contains("ChipTone.INACTIVE -> CHIP_INK"),
            "bật/tắt KHÔNG được dùng chung màu với chip trung tính — thế thì trạng thái lại vô hình",
        )
    }

    /**
     * Màu của chip phải chảy vào **CẢ icon lẫn chữ**.
     *
     * `applyChipFace` là chỗ duy nhất tint icon; nếu bộ vẽ chỉ `setTextColor` thì chữ đổi màu mà **icon vẫn xám** —
     * đúng thứ owner xin lại bị hụt một nửa, và là họ lỗi đã cắn một lần ở ô điều khiển (icon sai, nhãn đúng, lọt
     * qua một lượt nhìn nhanh — xem `ThemePaletteContractTest`).
     */
    @Test
    fun `mau chip to ca icon chu khong chi to chu`() {
        val face = SourceRoots.body(strip, "private fun applyChipFace(")
        assertTrue(face.contains("setTextColor"), "phải đặt màu CHỮ")
        assertTrue(face.contains("setTint"), "phải tint ICON — trạng thái bật/tắt nằm ở icon")
        // Và lượt làm mới phải đặt lại mặt chip khi MÀU đổi, không chỉ khi icon đổi: tone đổi mà icon giữ nguyên
        // (đúng ca bật→tắt) thì chip sẽ không bao giờ đổi màu.
        val refresh = SourceRoots.body(strip, "fun refreshChips(")
        assertTrue(
            Regex("""tag\s*!=\s*c\.icon\.toString\(\)\s*\+\s*color""").containsMatchIn(refresh),
            "chốt 'chỉ đặt lại khi đổi' phải tính CẢ màu vào khoá, không thì bật→tắt không đổi được màu",
        )
    }
}
