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
 * ⚠ **Bài này đã ĐỔI CHIỀU ở 1.65 (T8).** Bản trước khoá trạng thái *"chưa tải được"* (`needsArchiveExtract` ·
 * `downloadable = false`) vì gói chỉ phát hành dạng `tar.bz2` 397 mục. [ĐO] 2026-09-16 bác tiền đề ấy: gói **tỉa**
 * còn 13 tệp cho audio bit-identical, tải từng tệp có ghim sha256 được — nên nay bài khoá đúng bản tỉa. Đây là
 * *"số đo mới thay số đo cũ"*, không phải *"sửa test cho hết đỏ"*; lý do đầy đủ ở KDoc [SherpaTtsCatalog].
 */
class SherpaTtsCatalogTest {

    @Test
    fun `goi mac dinh la Piper vi_VN va ghim TUNG TEP bang so DO that`() {
        val v = SherpaTtsCatalog.byId(SherpaTtsCatalog.DEFAULT_ID)
        assertEquals("piper-vi_VN-vais1000-medium", v.id)
        assertEquals(13, v.files.size, "[ĐO] 2026-09-16 gói tỉa đúng 13 tệp — xem KDoc SherpaTtsCatalog")
        assertEquals(63_877_499L, v.totalBytes, "[ĐO] tổng byte của cây thư mục đã dựng WAV bit-identical")
        v.files.forEach { f ->
            assertTrue(f.pinned, "${f.name}: phải ghim cả sha256 lẫn kích thước")
            assertEquals(64, f.sha256.length, "${f.name}: sha256 phải đủ 64 hex")
            assertTrue(f.sha256.all { it.isDigit() || it in 'a'..'f' }, "${f.name}: sha256 phải là hex CHỮ THƯỜNG")
            assertTrue(f.url.startsWith("https://"), "${f.name}: URL phải HTTPS")
        }
    }

    /**
     * Ba tệp mà engine đọc trỏ thẳng tới ([SherpaTtsSpeaker]) phải CÓ trong bảng ghim.
     *
     * Không có bài này thì một lượt sửa bảng làm rơi `tokens.txt` vẫn xanh: `install` báo xong, `filesPresent`
     * trả `false`, và máy đọc **im lặng không phát tiếng nào** — đúng loại hỏng khó lần nhất (KDoc lớp).
     */
    @Test
    fun `ba duong dan engine doc deu nam trong bang ghim`() {
        val v = SherpaTtsCatalog.PIPER_VI_VAIS1000
        assertTrue(v.file(v.model) != null, "thiếu tệp .onnx trong bảng ghim")
        assertTrue(v.file(v.tokens) != null, "thiếu tokens.txt trong bảng ghim")
        assertTrue(
            v.files.any { it.name.startsWith(v.dataDir + "/") },
            "không tệp nào nằm dưới ${v.dataDir} — Piper KHÔNG đọc được nếu thiếu espeak-ng-data",
        )
    }

    /** Đường dẫn tương đối giữ nguyên trong URL raw của repo (owner 2026-09-16: tải trong app, không release/USB). */
    @Test
    fun `ten asset doi gach cheo thanh hai gach duoi`() {
        assertEquals("espeak-ng-data/lang/aav/vi", SherpaTtsCatalog.assetName("espeak-ng-data/lang/aav/vi"))
        assertEquals("tokens.txt", SherpaTtsCatalog.assetName("tokens.txt"))
        SherpaTtsCatalog.PIPER_VI_VAIS1000.files.forEach { f ->
            assertTrue(
                f.url.endsWith("/" + SherpaTtsCatalog.assetName(f.name)),
                "${f.name}: URL không kết thúc bằng tên asset đã quy ước",
            )
            assertFalse(
                f.url.substringAfterLast('/').contains('/'),
                "${f.name}: tên asset còn dấu `/`",
            )
        }
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

    /** Gói đã ghim đủ 13 tệp ⇒ được phép mở lối tải (fail-safe vẫn nằm ở phép so sha256 lúc tải). */
    @Test
    fun `goi tia da ghim du nen tai duoc tung tep`() {
        assertTrue(
            SherpaTtsCatalog.PIPER_VI_VAIS1000.downloadable,
            "13 tệp đều ghim ⇒ downloadable; nếu đỏ thì có tệp rơi mất sha/bytes",
        )
    }

    /**
     * **Mọi** đường dẫn trong gói phải TƯƠNG ĐỐI và không leo thư mục.
     *
     * Từ T8 `name` có thể **nhiều đoạn** (`espeak-ng-data/lang/aav/vi`), nên bài này kiểm từng đoạn đúng luật mà
     * `VoiceModelStore.requireSafe` thi hành (CLAUDE.md §4.1 — user input → file path).
     */
    @Test
    fun `moi duong dan trong goi deu TUONG DOI va khong leo thu muc`() {
        SherpaTtsCatalog.ALL.forEach { v ->
            (listOf(v.model, v.tokens, v.dataDir) + v.files.map { it.name }).forEach { p ->
                assertFalse(p.startsWith("/"), "${v.id}: \"$p\" phải tương đối so với thư mục gói")
                assertFalse(p.contains('\\') || p.contains(':'), "${v.id}: \"$p\" chứa ký tự đường dẫn của HĐH khác")
                p.split('/').forEach { seg ->
                    assertTrue(seg.isNotBlank(), "${v.id}: \"$p\" có đoạn rỗng")
                    assertFalse(seg == "." || seg == "..", "${v.id}: \"$p\" chứa `.`/`..` — đường thoát khỏi thư mục app")
                }
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
