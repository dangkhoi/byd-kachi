package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.WallSourcePolicy.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 3.1.5 — bộ ảnh nền đóng gói (owner 14/10) chỉ là MẶC ĐỊNH: không bao giờ đè lựa chọn của người dùng, và bản dựng
 * thiếu thư mục asset (clone sạch — ảnh gitignored) lùi về nền vẽ bằng mã.
 */
class WallSourcePolicyTest {

    private val user = listOf("/x/wallpapers/a.jpg", "/x/wallpapers/b.jpg")
    private val bundled = listOf("asset-1", "asset-2")

    @Test
    fun `nguoi dung bat va co anh - anh nguoi dung thang, ke ca khi co bo dong goi`() {
        assertEquals(WallSourcePolicy.Resolved(Source.USER, user), WallSourcePolicy.resolve(true, user, bundled))
        assertEquals(Source.USER, WallSourcePolicy.resolve(true, user, emptyList()).source)
    }

    @Test
    fun `chua co lua chon - dung bo dong goi`() {
        assertEquals(WallSourcePolicy.Resolved(Source.BUNDLED, bundled), WallSourcePolicy.resolve(false, emptyList(), bundled))
        assertEquals(Source.BUNDLED, WallSourcePolicy.resolve(true, emptyList(), bundled).source, "bật mà thư mục trống")
        // Tắt thì ảnh trong thư mục KHÔNG được chiếu (tắt là tắt ảnh người dùng).
        assertEquals(WallSourcePolicy.Resolved(Source.BUNDLED, bundled), WallSourcePolicy.resolve(false, user, bundled))
    }

    @Test
    fun `thieu hoac rong thu muc asset - nen ve bang ma`() {
        val drawn = WallSourcePolicy.Resolved(Source.DRAWN, emptyList())
        assertEquals(drawn, WallSourcePolicy.resolve(false, emptyList(), WallSourcePolicy.bundledFrom(null)))
        assertEquals(drawn, WallSourcePolicy.resolve(true, emptyList(), WallSourcePolicy.bundledFrom(emptyList())))
        assertEquals(drawn, WallSourcePolicy.resolve(false, user, WallSourcePolicy.bundledFrom(listOf("README", ".keep"))))
    }

    @Test
    fun `danh sach asset loc anh va sap on dinh - khong danh sach cung`() {
        assertEquals(
            listOf("wall-01.jpg", "wall-02.jpg", "wall-10.webp"),
            WallSourcePolicy.bundledFrom(listOf("wall-10.webp", "notes.txt", "wall-02.jpg", "wall-01.jpg")),
        )
    }
}
