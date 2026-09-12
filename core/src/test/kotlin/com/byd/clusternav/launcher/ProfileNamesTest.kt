package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [SOÁT P3-4] **Tên hồ sơ: KHOÁ giữ nguyên, NHÃN dịch được** — xem KDoc [ProfileNames].
 *
 * Hai hướng sai đều tệ, và bài này khoá cả hai:
 *  - **không dịch nhãn** ⇒ người dùng English thấy `"Mặc định"` giữa màn ([ĐO] 179 nút chữ trên 7 trang Cài đặt EN,
 *    còn đúng hai chuỗi Việt: `"Tiếng Việt"` — đúng — và `"Mặc định"` — sai);
 *  - **dịch cả khoá** ⇒ mọi khoá `Mặc định__*` thành mồ côi ⇒ người dùng mất sạch cấu hình, **im lặng**.
 */
class ProfileNamesTest {

    @AfterEach
    fun reset() {
        Strings.current = Lang.VI
    }

    /** ⚠⚠ KHOÁ là hằng, KHÔNG được đổi theo ngôn ngữ — nó là tiền tố của mọi khoá lưu bền theo hồ sơ. */
    @Test
    fun `khoa luu ben KHONG doi theo ngon ngu`() {
        Strings.current = Lang.EN
        assertEquals(
            "Mặc định", HomeUiState.DEFAULT_PROFILE,
            "dịch hằng này = mọi khoá `Mặc định__preset/__slot_0/__scenes…` thành mồ côi ⇒ mất sạch cấu hình",
        )
    }

    @Test
    fun `nhan hien thi doi theo ngon ngu`() {
        Strings.current = Lang.VI
        assertEquals("Mặc định", ProfileNames.display(HomeUiState.DEFAULT_PROFILE))
        Strings.current = Lang.EN
        assertEquals("Default", ProfileNames.display(HomeUiState.DEFAULT_PROFILE))
    }

    /** Tên do NGƯỜI DÙNG đặt là dữ liệu của họ ⇒ không bao giờ dịch, không bao giờ đổi (kể cả tên tiếng Việt). */
    @Test
    fun `ten nguoi dung dat giu nguyen o moi ngon ngu`() {
        listOf("Vợ", "Đường trường", "Default", "Mặc định 2", "Wife").forEach { name ->
            Strings.current = Lang.VI
            assertEquals(name, ProfileNames.display(name))
            Strings.current = Lang.EN
            assertEquals(name, ProfileNames.display(name), "tên người dùng đặt KHÔNG được dịch: $name")
        }
    }

    /** Chữ đầu của avatar theo NHÃN ⇒ máy tiếng Anh hiện `D`, máy tiếng Việt hiện `M`. */
    @Test
    fun `chu dau avatar theo nhan da dich`() {
        Strings.current = Lang.VI
        assertEquals("M", ProfileNames.initial(HomeUiState.DEFAULT_PROFILE))
        Strings.current = Lang.EN
        assertEquals("D", ProfileNames.initial(HomeUiState.DEFAULT_PROFILE))
        assertEquals("V", ProfileNames.initial("Vợ"))
    }

    /**
     * ⚠ **Phép chiếu chỉ đi MỘT CHIỀU**: nhãn không được quay lại thành khoá.
     *
     * Ở máy tiếng Anh, `display("Mặc định")` = `"Default"`; nếu ai đó truyền chuỗi đó vào `switchProfile`/`key()` thì
     * launcher đi đọc `Default__preset` — không tồn tại ⇒ bố cục trắng. Bài này chốt rằng hai chuỗi đó **khác nhau**,
     * để phép chiếu không bao giờ bị coi là "vô hại nếu gọi hai lần".
     */
    @Test
    fun `nhan da dich KHONG the dung lam khoa`() {
        Strings.current = Lang.EN
        val label = ProfileNames.display(HomeUiState.DEFAULT_PROFILE)
        assertTrue(
            label != HomeUiState.DEFAULT_PROFILE,
            "ở tiếng Anh nhãn phải KHÁC khoá — nếu bằng nhau thì phép dịch không xảy ra",
        )
        // Và chiếu lần hai không đưa nó về khoá được (nhãn đã dịch là "tên lạ" ⇒ giữ nguyên).
        assertEquals(label, ProfileNames.display(label), "chiếu hai lần vẫn ra nhãn, KHÔNG quay về khoá")
    }
}
