package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bất biến MỘT-PACKAGE-MỘT-KHUNG ở mức nguyên thuỷ nhất (§R-BI).
 */
class NavFrameIdentityTest {

    /**
     * KHOÁ: thiếu dữ liệu ⇒ IM LẶNG — không khung nào được dựng từ một package rỗng/không biết là ai.
     * Nếu ai đó "nới" hàm này cho null==null = true thì hai kênh vô chủ sẽ ghép được với nhau.
     */
    @Test fun `rong va null khong bao gio la danh tinh`() {
        assertFalse(NavFrameIdentity.sameFrame(null, null))
        assertFalse(NavFrameIdentity.sameFrame("", ""))
        assertFalse(NavFrameIdentity.sameFrame(null, "vn.vietmap.live"))
        assertFalse(NavFrameIdentity.sameFrame("vn.vietmap.live", null))
        assertFalse(NavFrameIdentity.sameFrame("", "vn.vietmap.live"))
        assertFalse(NavFrameIdentity.sameFrame("vn.vietmap.live", ""))
    }

    /**
     * KHOÁ: so khớp NGUYÊN chuỗi, KHÔNG gộp theo họ [NavApps]. Ca thật 08-22: cài SONG SONG Waze zin và
     * WazeMod, chỉ một bản đang dẫn (đúng lý do `NavWindowPicker.rank` ra đời) — coi hai bản là một app thì
     * mũi tên bản này ghép với làn bản kia.
     */
    @Test fun `so khop NGUYEN chuoi, KHONG gop theo ho NavApps`() {
        val zin = NavApps.WAZE_RES_PREFIX               // "com.waze"
        val mod = "com.chisadin.wazemod"
        assertTrue(zin in NavApps.WAZE && mod in NavApps.WAZE, "cả hai đều thuộc họ Waze")
        assertFalse(NavFrameIdentity.sameFrame(zin, mod), "cùng họ KHÔNG có nghĩa là cùng khung")
        assertFalse(NavFrameIdentity.sameFrame(mod, zin))
        assertTrue(NavFrameIdentity.sameFrame(mod, mod))
    }

    @Test fun `cung package thi khop`() {
        assertTrue(NavFrameIdentity.sameFrame(NavApps.VIETMAP_LIVE, NavApps.VIETMAP_LIVE))
    }
}
