package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
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
        assertTrue(picker.contains("TopStripConfig.choices()"), "màn chọn phải lấy từ `:core`, không tự liệt kê")
        // ⚠⚠ T4 · R-UI (m) — ĐƯỜNG ĐẶT DATUM BẤT KỲ ĐÃ ĐỔI HÌNH, KHÔNG BIẾN MẤT.
        // Trước đây nó là **GIỮ một ô ở lưới 123 ô** trong màn Cài đặt (`CapabilityGridSection.setOnLongClickListener`
        // → `onChipToggle` → bộ chọn). Lưới đó nay bỏ khỏi Settings (bộ chọn của ngăn kéo thay thế) nên lối GIỮ mất
        // theo. Nếu không có đường thay thế thì người dùng **mất hẳn** khả năng đưa một datum bất kỳ lên thanh trạng
        // thái (R8 *"không tính năng nào mất"*) — đúng loại hụt mà chỉ bài canh này thấy được.
        // Đường mới: nút "Thêm chip khác…" mở hộp thoại danh sách trên CHÍNH `TopStripConfig.choices()`, rồi đi qua
        // CÙNG `toggle` với chạm ô (kể cả câu nhắc khi đã đầy trần).
        assertTrue(picker.contains("fun openMore()"), "phải có đường đưa datum BẤT KỲ lên thanh trên")
        assertTrue(
            SourceRoots.body(picker, "private fun openMore()").contains("toggle(all[index].id)"),
            "đường đó phải đi qua CÙNG một `toggle` với chạm ô — hai đường riêng thì luật trần/loại mục sẽ lệch",
        )
        assertTrue(
            SourceRoots.body(picker, "private fun openMore()").contains("TopStripConfig.choices()"),
            "và phải bày TOÀN BỘ danh sách của `:core`, không phải một danh sách rút gọn viết tay",
        )
    }

    @Test
    fun `bo chon chip khong tu ghi ben`() {
        // Nguồn sự thật là `HomeUiState.topStrip`. Bộ chọn chỉ báo ra — nếu nó tự ghi thì có hai đường ghi và chúng
        // sẽ lệch nhau (đúng bẫy hai-bản-sao dự án vừa dọn).
        assertTrue(!picker.contains("WorkspacePrefs"), "bộ chọn không được chạm prefs")
        assertTrue(!picker.contains("setTopStrip("), "bộ chọn không được tự ghi bền")
    }
}
