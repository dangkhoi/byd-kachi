package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 2.93 — *"mở kính trước trái một nửa"* bấm nút 50% của kính ấy ([VoiceHalfButton]; spec `kachi-293-voice.html` §10).
 *
 * [ĐO off-car 07/10] trước bản này cờ nửa chỉ được đọc cho nút COVER, nên mọi cách nói không trùng nguyên một cách gọi đã khai ra
 * nút MỞ HẾT (`Control(win_lf, 1)`). Quan hệ nút → nút nửa SUY từ từ vựng (không mã cứng trong mã chạy); bài này ghim kết quả đo
 * và soát nó khớp cấu trúc bộ đăng ký (cùng icon · cùng hàm ghi · cùng nhóm).
 */
class VoiceHalfButtonTest {

    private fun one(s: String) = VoiceIntentParser.parse(s).single()
    private fun c(id: String, v: Int?) = VoiceIntent.Control(id, v)

    @Test
    fun `quan he suy tu tu vung dung nam cap va khop cau truc bo dang ky`() {
        assertEquals(
            mapOf(
                "win_lf" to "win_half_lf", "win_rf" to "win_half_rf", "win_lr" to "win_half_lr", "win_rr" to "win_half_rr",
                "windows_all" to "win_half_all",
            ),
            VoiceHalfButton.HALF_OF,
        )
        VoiceHalfButton.HALF_OF.forEach { (base, half) ->
            val b = requireNotNull(ControlRegistry.byId(base)); val h = requireNotNull(ControlRegistry.byId(half))
            assertEquals(b.icon, h.icon, "$base/$half cùng icon")
            assertEquals(b.bindingKey, h.bindingKey, "$base/$half cùng hàm ghi")
            assertEquals(b.domain, h.domain, "$base/$half cùng nhóm")
        }
    }

    @Test
    fun `noi nua canh ten kinh thi bam nut nua cua kinh ay`() {
        listOf(
            "mở kính trước trái một nửa" to c("win_half_lf", 1),
            "mở kính trước trái một nửa thôi" to c("win_half_lf", 1),
            "mở một nửa kính lái" to c("win_half_lf", 1),
            "mở kính một nửa" to c("win_half_lf", 1),
            "mở kính trước trái năm mươi phần trăm" to c("win_half_lf", 1),
            "mở kính phụ 50%" to c("win_half_rf", 1),
            "mở kính sau trái một nửa" to c("win_half_lr", 1),
            "mở kính sau phải một nửa" to c("win_half_rr", 1),
            "mở 4 kính một nửa" to c("win_half_all", 1),
            "mở tất cả kính một nửa" to c("win_half_all", 1),
            "mở hết kính một nửa" to c("win_half_all", 1),   // tên gói "mở hết kính" không đọc đuôi ⇒ đường động từ hiểu
            "mở hết kính 50%" to c("win_half_all", 1),
            // Cách gọi đã khai sẵn: như cũ.
            "mở kính lái một nửa" to c("win_half_lf", 1),
            "mở 50% kính lái" to c("win_half_lf", 1),
        ).forEach { (s, want) -> assertEquals(want, one(s), "«$s»") }
    }

    @Test
    fun `khong co nut nua hay khong noi nua thi nhu cu`() {
        listOf(
            "mở kính trước trái" to c("win_lf", 1),
            "đóng kính trước trái một nửa" to c("win_lf", 0),     // đóng là đóng — nút gốc như cũ
            "mở kính lái 30 phần trăm" to c("win_lf", 1),         // không phải nửa
            "mở cốp một nửa" to c("trunk", 2),                   // COVER: mức nửa của chính nó, như cũ
            "mở rèm che nắng một nửa" to c("sunshade", 2),
            "mở cửa sổ trời một nửa" to c("sunroof", 1),         // không có nút nửa
            "bật đèn đọc một nửa" to c("readl", 1),
        ).forEach { (s, want) -> assertEquals(want, one(s), "«$s»") }
        assertEquals(VoiceIntent.Macro("mac_win_open_all"), one("mở hết kính ra"))
        assertEquals(VoiceIntent.Macro("mac_win_close_all"), one("đóng hết kính một nửa"))
    }

    /** Dấu nửa phải đứng NGAY cạnh tên: một vế mang hai lệnh không được đẩy cờ nửa sang kính kia. */
    @Test
    fun `dau nua phai sat ten kinh`() {
        assertEquals(listOf(c("win_rf", 1), c("win_half_lf", 1)), VoiceIntentParser.parse("hạ kiếng phải xong mở nửa kính lái"))
        assertEquals(c("win_rr", 1), VoiceIntentParser.parse("mở kính sau phải, mở nửa kính lái và tắt máy nạnh").first())
        assertEquals(VoiceIntent.Macro("mac_win_open_all"), VoiceIntentParser.parse("hạ hết kính, mở nửa kính lái và bật đèn trần").first())
    }
}
