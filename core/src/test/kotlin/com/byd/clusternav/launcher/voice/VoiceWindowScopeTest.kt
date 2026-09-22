package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ═══ PHẠM VI của một câu nói về KÍNH — mơ hồ vs tường minh, và mức Nửa ════════════════════════════════════════
 *
 * Tách khỏi `VoiceIntentParserTest` ngày 2026-09-19 (lượt D) vì tệp kia đứng sẵn ở **499** dòng, tức ngay dưới trần
 * 500 (CLAUDE.md §4.1). Tách theo **CHỦ ĐỀ** như `LauncherLocaleContractTest` đã làm, không cắt cho đủ số: cả tệp
 * này trả lời đúng một câu hỏi — *"câu này nói về MẤY cửa kính, và mở tới mức nào"*.
 *
 * ## D (owner test xe 2026-09-19) — cụm MƠ HỒ trỏ về **MỘT** kính, không phải cả bốn
 * Owner nói *"mở kính"* và xe hạ **cả 4**. Gốc: bản vá [SOÁT P2] trước đó gắn bốn cụm mơ hồ (`kinh` · `cua so` ·
 * `cac cua so` · `windows`) vào nút GỘP `windows_all`, với lập luận *"nó thuộc diện CONFIRM nên người lái còn thấy
 * hộp hỏi lại"*. Lập luận đó **sập** ở mặc định thật: `voice_confirm_ids` mặc định **RỖNG** (owner chốt *"không hỏi
 * xác nhận gì cả"*) ⇒ không hộp nào hiện ⇒ câu mơ hồ nhất đi thẳng tới việc rộng nhất.
 *
 * Nguyên tắc nay: cụm **mơ hồ** ⇒ phạm vi **HẸP NHẤT** hợp lý (kính lái — chỗ người nói đang ngồi); muốn cả bốn thì
 * phải nói TƯỜNG MINH. Hẹp đoán sai thì thiếu một việc; rộng đoán sai thì **hạ ba cửa kính không ai xin**.
 */
class VoiceWindowScopeTest {

    private val profiles = listOf("Mặc định", "Vợ")
    private val apps = listOf("VTV Go", "YouTube", "Zing MP3", "VietMap Live")

    private fun one(s: String): VoiceIntent = VoiceIntentParser.parseOne(s, profiles, apps)

    /** Bảng ca: câu → ý định mong đợi. Thông báo lỗi kèm nguyên văn câu để đọc là biết ca nào đỏ. */
    private fun expect(vararg cases: Pair<String, VoiceIntent>) =
        cases.forEach { (s, want) -> assertEquals(want, one(s), "câu: \"$s\"") }

    /**
     * Cụm dài vẫn thắng cụm ngắn — một kính cụ thể KHÔNG được rơi vào nút gộp, và cụm mơ hồ KHÔNG được thành cả bốn.
     *
     * Đây là đổi kỳ vọng theo **quyết định của owner**, không phải sửa test cho hết đỏ — xem khối ghi chú D ở
     * `VoiceSynonyms.CONTROL` và KDoc lớp này.
     */
    @Test fun `cum kinh ngan khong nuot cum kinh dai`() = expect(
        "mở kính" to VoiceIntent.Control("win_lf", 1),
        "đóng kính" to VoiceIntent.Control("win_lf", 0),
        "mở cửa sổ" to VoiceIntent.Control("win_lf", 1),
        "mở kính lái" to VoiceIntent.Control("win_lf", 1),
        "mở kính trước trái" to VoiceIntent.Control("win_lf", 1),
        "xem kính trước trái" to VoiceIntent.Read("window_lf"),
        "mở hết kính" to VoiceIntent.Macro("mac_win_open_all"),
        "đóng hết kính" to VoiceIntent.Macro("mac_win_close_all"),
        // …và cụm TƯỜNG MINH-tất-cả vẫn tới được nút gộp (chống hồi quy NGƯỢC của lượt D).
        "mở toàn bộ kính" to VoiceIntent.Control("windows_all", 1),
        "mở bốn kính" to VoiceIntent.Control("windows_all", 1),
    )

    /**
     * ⚠ [SOÁT lượt D · P2] **SỐ NHIỀU tường minh vẫn là cả bốn** — *"các cửa sổ"* / *"windows"*.
     *
     * Nguyên tắc của lượt D là *"cụm **MƠ HỒ** ⇒ phạm vi hẹp nhất"*, và chính nó khoanh lại phạm vi lượt dời: `các`
     * là dấu hiệu số nhiều của tiếng Việt, `windows` là số nhiều tiếng Anh ⇒ cả hai **tường minh-nhiều-cửa** y như
     * *"bốn kính"*, không có gì để đoán. Bản vá D đầu dời cả bốn cụm sang `window`, nên *"mở các cửa sổ"* chỉ hạ MỘT
     * cửa — không phải cái giá rẻ hơn, chỉ là cái sai đổi chiều.
     *
     * Bài này cũng khoá **giao điểm** của luật dãy-dài-nhất: `cac cua so` (3 từ) phải thắng `cua so` (2 từ) nằm ngay
     * trong nó, nếu không thì hai cụm này cướp nhau và một trong hai dòng dưới sẽ đỏ.
     */
    @Test fun `so nhieu tuong minh van la ca bon kinh`() = expect(
        "mở các cửa sổ" to VoiceIntent.Control("windows_all", 1),
        "đóng các cửa sổ" to VoiceIntent.Control("windows_all", 0),
        // …trong khi dạng SỐ ÍT ngay bên trong nó vẫn là kính lái (hai chiều của cùng một luật).
        "mở cửa sổ" to VoiceIntent.Control("win_lf", 1),
    )

    /**
     * ⚠⚠ 1.91 (owner 2026-09-21) — *"MỞ HẾT CỬA SỔ"* PHẢI LÀ **CẢ BỐN**, không phải mỗi bên lái.
     *
     * Bài này khoá bug owner báo trên xe: nói *"mở hết cửa sổ"* thì chỉ **một** cửa hạ. Gốc là chỗ hụt **đối
     * xứng** trong [VoiceSynonyms.CONTROL], không phải một luật sai — mọi cụm tường minh-tất-cả dựng trên chữ
     * *"kính"* (`het kinh` · `toan bo kinh` · `moi kinh` · `bon kinh`), còn chữ *"cửa sổ"* chỉ có đúng một dạng số
     * nhiều (`cac cua so`). Nên *"hết cửa sổ"* không khớp cụm nào và luật dãy-dài-nhất lùi xuống `cua so` (2 từ) ở
     * vị trí sau = **kính LÁI** theo đúng lượt D. Hai quyết định trước đó đều đúng; chỉ là nhánh *"tất cả"* chưa
     * bao giờ được viết bằng thứ tiếng người lái đang dùng.
     *
     * Bài cũng khoá **giao điểm** của luật dãy-dài-nhất ở chỗ khó nhất: `het cua so` (3 từ, vị trí 1) phải thắng
     * `cua so` (2 từ, vị trí 2) — hai cụm LỒNG NHAU, khác vị trí. Gỡ một dòng khỏi nhánh mới là bài này đỏ.
     */
    @Test fun `1_91 · luong tu tuong minh + cua so = CA BON kinh`() = expect(
        "mở hết cửa sổ" to VoiceIntent.Control("windows_all", 1),
        "mở tất cả cửa sổ" to VoiceIntent.Control("windows_all", 1),
        "mở toàn bộ cửa sổ" to VoiceIntent.Control("windows_all", 1),
        "mở mọi cửa sổ" to VoiceIntent.Control("windows_all", 1),
        "mở bốn cửa sổ" to VoiceIntent.Control("windows_all", 1),
        // …và chiều ĐÓNG, để cụm mới không chỉ đúng một nửa.
        "đóng hết cửa sổ" to VoiceIntent.Control("windows_all", 0),
        "đóng tất cả cửa sổ" to VoiceIntent.Control("windows_all", 0),
        "đóng toàn bộ cửa sổ" to VoiceIntent.Control("windows_all", 0),
        // Nhánh *"cửa kính"* + dạng miền Nam *"kiếng"*.
        "mở hết cửa kính" to VoiceIntent.Control("windows_all", 1),
        "mở tất cả cửa kính" to VoiceIntent.Control("windows_all", 1),
        "mở toàn bộ cửa kính" to VoiceIntent.Control("windows_all", 1),
        "mở bốn cửa kính" to VoiceIntent.Control("windows_all", 1),
        "mở tất cả kiếng" to VoiceIntent.Control("windows_all", 1),
        // Dạng viết bằng CHỮ SỐ (gõ trên xe) — cùng nút, dù không bias được ở tầng âm.
        "mở 4 cửa sổ" to VoiceIntent.Control("windows_all", 1),
        "mở 4 kính" to VoiceIntent.Control("windows_all", 1),
    )

    /**
     * ⚠⚠ Chiều NGƯỢC của bài trên — **cụm mơ hồ KHÔNG được kéo theo**. Đây là phép chống hồi quy của lượt D.
     *
     * Nhánh mới của 1.91 chỉ nhận cụm có **lượng từ tường minh đứng TRƯỚC** (hết · toàn bộ · tất cả · mọi · bốn).
     * Nếu ai đó "đơn giản hoá" bằng cách đẩy `cua so` trần sang nút gộp thì hai dòng dưới đỏ ngay — và đó chính là
     * bug owner đã báo ở 1.80 (*"mở kính"* hạ cả 4).
     */
    @Test fun `1_91 · cum mo ho van la MOT kinh lai`() = expect(
        "mở cửa sổ" to VoiceIntent.Control("win_lf", 1),
        "mở kính" to VoiceIntent.Control("win_lf", 1),
        "đóng cửa sổ" to VoiceIntent.Control("win_lf", 0),
        "đóng kính" to VoiceIntent.Control("win_lf", 0),
    )

    /**
     * «nửa / 50%» ⇒ mức NỬA (COVER value 2 → HAL state 4). Không "nửa" ⇒ mở (value 1).
     *
     * ## ⚠⚠ HỆ QUẢ ĐO ĐƯỢC của lượt D — cụm MƠ HỒ mất mức Nửa (câu hỏi cho owner)
     * [ĐO off-car 2026-09-19] ba câu *"mở một nửa kính"* / *"mở kính một nửa"* / *"mở kính 50%"* trước lượt D ra
     * `Control(windows_all, 2)`; nay ra **`Control(window, 1)`** — mở TRỌN kính lái. Cơ chế: cờ `half` chỉ có tác
     * dụng với [com.byd.clusternav.launcher.ControlKind.COVER] (`VoiceControlParse.control`), mà `window` khai là
     * **TOGGLE** — và đó là một quyết định **có chủ ý đang bị khoá bằng test**: `ControlWriteArgsTest` liệt kê
     * `{window, win_lf}` là *"cùng cửa kính lái, khác kiểu ô (TOGGLE vs COVER)"*. Nên lượt D **không** tự đổi
     * registry để chữa chỗ này.
     *
     * Không có lời nói dối nào: `VoiceReply.preview` cho TOGGLE đọc *"Mở Kính cửa lái"*, không hề nhắc *"Nửa"*, nên
     * người lái nghe ra đúng việc đã xảy ra. Mức Nửa vẫn tới được bằng hai đường **tường minh** (ba dòng đầu) ⇒
     * tính năng 1.74 không mất.
     *
     * ⇒ Hai đường chữa cho cụm mơ hồ là **quyết định của owner**: (a) trỏ cụm mơ hồ sang `win_lf` (cùng cửa kính
     * ấy, đã là COVER + có `readKey` ⇒ được cả lượt đọc-lại E); (b) cho `window` mức Nửa (bỏ phân biệt TOGGLE/COVER
     * đang khoá ở `ControlWriteArgsTest`). Ghi ở `docs/_handoff/cde-done.md`.
     */
    @Test fun `log xe · mo mot nua kinh = nut 50 phan tram rieng`() = expect(
        // 1.94 (owner 2026-09-22): 50% nay là nút RIÊNG `win_half_*` (TOGGLE mở-50%↔đóng), giá trị 1 = mở 50%.
        "mở nửa kính trước trái" to VoiceIntent.Control("win_half_lf", 1),
        "mở nửa kính lái" to VoiceIntent.Control("win_half_lf", 1),
        "mở một nửa tất cả kính" to VoiceIntent.Control("win_half_all", 1),
        "mở nửa hết kính" to VoiceIntent.Control("win_half_all", 1),
        // "mở kính" (không "nửa") ⇒ nút mở/đóng thường (win_lf), mở trọn — chống hồi quy.
        "mở kính" to VoiceIntent.Control("win_lf", 1),
    )
}
