package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 pha NÓI · BÀI KHOÁ BẢNG GÓI GIỌNG ════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R2b**. Cùng họ với [SherpaModelCatalogTest] và cùng lý do: một
 * số ghim sai không bao giờ kêu ở máy soạn thảo — nó chỉ kêu ở chiếc xe tải về một gói 67 MB rồi không đọc được.
 *
 * ⚠ Bài này **cố ý khoá cả trạng thái "chưa tải được"**. Ngày ai đó thêm tầng giải nén `.tar.bz2`, bài sẽ đỏ ở
 * `needsArchiveExtract` — và đó là lúc phải xem lại `VoiceModelStore` chứ không phải lúc sửa con số cho hết đỏ.
 */
class SherpaTtsCatalogTest {

    @Test
    fun `goi mac dinh la Piper vi_VN va ghim bang so DO that`() {
        val v = SherpaTtsCatalog.byId(SherpaTtsCatalog.DEFAULT_ID)
        assertEquals("piper-vi_VN-vais1000-medium", v.id)
        assertTrue(v.pinned, "gói phải ghim cả sha256 lẫn kích thước")
        assertEquals(64, v.archiveSha256.length, "sha256 phải đủ 64 hex")
        assertTrue(v.archiveSha256.all { it.isDigit() || it in 'a'..'f' }, "sha256 phải là hex CHỮ THƯỜNG")
        assertEquals(67_154_040L, v.archiveBytes, "[ĐO] 2026-09-15 `curl` + `ls -la`")
        assertTrue(v.archiveUrl.startsWith("https://"), "URL phải HTTPS")
    }

    /**
     * Giấy phép: dự án đã từ chối CC-BY-**NC**-ND một lần (xem KDoc [SherpaModelCatalog]). Bài này chặn một gói
     * phi thương mại / cấm phái sinh lọt vào bản phát hành — thứ không có cách nào phát hiện lúc chạy.
     */
    @Test
    fun `khong goi nao mang giay phep NC hoac ND`() {
        SherpaTtsCatalog.ALL.forEach { v ->
            assertFalse(v.license.contains("NC"), "${v.id}: giấy phép phi thương mại")
            assertFalse(v.license.contains("ND"), "${v.id}: giấy phép cấm phái sinh")
            assertTrue(v.attribution.isNotBlank(), "${v.id}: CC-BY đòi ghi công — câu ghi công không được rỗng")
        }
    }

    /**
     * Fail-safe của cổng tải — gương của `SherpaModelCatalogTest` cho [SherpaModelCatalog.HATAPHU_VI].
     *
     * [ĐO] 2026-09-15: gói chỉ phát hành dạng `tar.bz2` 397 mục (393 mục là `espeak-ng-data/`), mà tầng tải hiện
     * có chỉ biết tải **từng tệp** đã ghim ⇒ `downloadable` PHẢI là `false`. Cho `true` ở đây nghĩa là mở một
     * nút *Tải* dẫn tới một thư mục thiếu `espeak-ng-data` — máy đọc sẽ **im lặng không phát tiếng nào**.
     */
    @Test
    fun `goi chua tai tu dong duoc vi con can tang giai nen`() {
        val v = SherpaTtsCatalog.PIPER_VI_VAIS1000
        assertTrue(v.needsArchiveExtract, "gói phát hành dạng tar.bz2 — xem KDoc SherpaTtsCatalog")
        assertFalse(v.downloadable, "chưa có tầng giải nén ⇒ KHÔNG được mở lối tải tự động")
    }

    @Test
    fun `ba duong dan trong goi deu la duong TUONG DOI`() {
        SherpaTtsCatalog.ALL.forEach { v ->
            listOf(v.model, v.tokens, v.dataDir).forEach { p ->
                assertFalse(p.startsWith("/"), "${v.id}: \"$p\" phải tương đối so với thư mục mô hình")
                assertFalse(p.contains(".."), "${v.id}: \"$p\" chứa `..` — đường thoát khỏi thư mục app")
            }
        }
    }

    @Test
    fun `thu muc goi nam duoi goc rieng, khong lan vao thu muc mo hinh NGHE`() {
        val v = SherpaTtsCatalog.PIPER_VI_VAIS1000
        assertTrue(v.dir.startsWith(SherpaTtsCatalog.DIR_ROOT + "/"))
        assertFalse(
            v.dir.startsWith(SherpaModelCatalog.DIR_ROOT + "/"),
            "gói ĐỌC nằm chung thư mục với mô hình NGHE thì một lượt dọn của bên này xoá mất bên kia",
        )
    }

    @Test
    fun `id la khoa duy nhat va byId luon lui ve mac dinh`() {
        assertEquals(SherpaTtsCatalog.ALL.size, SherpaTtsCatalog.ALL.map { it.id }.toSet().size)
        assertEquals(SherpaTtsCatalog.DEFAULT_ID, SherpaTtsCatalog.byId(null).id)
        assertEquals(SherpaTtsCatalog.DEFAULT_ID, SherpaTtsCatalog.byId("gói-không-có-thật").id)
    }

    /** Tần số mẫu đi thẳng vào `AudioTrack`; sai số này thì giọng nghe nhanh/chậm bất thường, không báo lỗi. */
    @Test
    fun `tan so mau khop MODEL_CARD`() {
        assertEquals(22_050, SherpaTtsCatalog.PIPER_VI_VAIS1000.sampleRate)
    }
}
