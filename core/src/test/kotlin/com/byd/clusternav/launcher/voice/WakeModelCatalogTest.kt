package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ "Hey Kachi" · BÀI KHOÁ BẢNG GHIM GÓI KWS ═════════════════════════════════════════════════════════════════
 *
 * Cùng họ với [SherpaModelCatalogTest] / [SherpaTtsCatalogTest], và cùng một lẽ: **một số ghim sai không bao giờ
 * kêu ở máy soạn thảo**. Nó chỉ kêu trên chiếc xe tải về 5 MB rồi `VoiceWakeKws.build` trả `null`, và triệu chứng
 * người dùng thấy là *"gọi mãi không nhận"* — không có lỗi, không có toast, chỉ là một tính năng im lặng.
 *
 * Số ghim là [ĐO] 2026-09-19 (`shasum -a 256` + `stat -f%z`) trên chính 5 tệp trong `voice/kws/` của repo.
 */
class WakeModelCatalogTest {

    private val pack = WakeModelCatalog

    @Test
    fun `dung 5 tep, ghim TUNG TEP bang so DO that`() {
        assertEquals("kws-gigaspeech-3.3M", pack.id)
        assertEquals(5, pack.files.size, "[ĐO] 2026-09-19 gói KWS int8 đúng 5 tệp")
        pack.files.forEach { f ->
            assertTrue(f.pinned, "${f.name}: phải ghim cả sha256 lẫn kích thước")
            assertEquals(64, f.sha256.length, "${f.name}: sha256 phải đủ 64 hex")
            assertTrue(f.sha256.all { it.isDigit() || it in 'a'..'f' }, "${f.name}: sha256 phải là hex CHỮ THƯỜNG")
            assertTrue(f.url.startsWith("https://"), "${f.name}: URL phải HTTPS")
            assertTrue(f.bytes > 0L, "${f.name}: cỡ phải dương")
        }
    }

    /**
     * [WakeModelCatalog.totalBytes] ghim tường minh **phải khớp** tổng của bảng.
     *
     * Đây là lý do duy nhất để con số ấy tồn tại hai lần: hai nguồn độc lập cho cùng một giá trị thì một dòng
     * ghim bị sửa nửa vời (đổi `bytes`, quên `sha256`; dán lẫn hai tệp) làm **đỏ bài này** thay vì lặng lẽ tải
     * về một gói khác rồi báo "không khớp bản ghim" cho đúng một tệp trong năm.
     */
    @Test
    fun `totalBytes ghim tuong minh khop tong cua bang`() {
        assertEquals(5_253_782L, pack.totalBytes, "[ĐO] tổng byte của 5 tệp trong voice/kws/")
        assertEquals(
            pack.files.sumOf { it.bytes }, pack.totalBytes,
            "tổng ghim lệch tổng thật ⇒ có dòng ghim bị sửa nửa vời",
        )
    }

    /** Gói ~5 MB, KHÔNG phải ~74 MB của mô hình NGHE — con số này là cả lý do nó tải được qua 4G trong cabin. */
    @Test
    fun `goi du nho de tai qua mang di dong`() {
        assertTrue(pack.totalBytes < 8L * 1024 * 1024, "gói KWS phải dưới 8 MB; thấy ${pack.totalBytes} byte")
    }

    /** Ghim đủ ⇒ mở lối tải (fail-safe vẫn nằm ở phép so sha256 lúc tải, và ở degrade chỉ-RMS khi thiếu). */
    @Test
    fun `da ghim du nen tai duoc tung tep`() {
        assertTrue(pack.downloadable, "5 tệp đều ghim ⇒ downloadable; nếu đỏ thì có tệp rơi mất sha/bytes")
    }

    /**
     * **Năm tên tệp mà engine trỏ tới đều phải CÓ trong bảng ghim.**
     *
     * Không có bài này thì một lượt sửa bảng làm rơi `keywords.txt` vẫn xanh: `install` báo xong, `isReady` trả
     * `false` (hoặc tệ hơn: `true` mà engine không dựng được), và "Hey Kachi" **im lặng không nhận gì** — đúng
     * loại hỏng khó lần nhất.
     */
    @Test
    fun `nam duong dan engine doc deu nam trong bang ghim`() {
        listOf(
            WakeModelCatalog.ENCODER, WakeModelCatalog.DECODER, WakeModelCatalog.JOINER,
            WakeModelCatalog.TOKENS, WakeModelCatalog.KEYWORDS,
        ).forEach { name ->
            assertTrue(pack.file(name) != null, "thiếu `$name` trong bảng ghim")
        }
        assertEquals(
            5, setOf(
                WakeModelCatalog.ENCODER, WakeModelCatalog.DECODER, WakeModelCatalog.JOINER,
                WakeModelCatalog.TOKENS, WakeModelCatalog.KEYWORDS,
            ).size,
            "năm hằng tên tệp phải khác nhau",
        )
    }

    /**
     * **Tên tệp PHẲNG** — `VoiceWakeKws` đọc `filesDir/kws/<tên>`, không có tầng thư mục nào.
     *
     * Gói Piper có tên nhiều đoạn (`espeak-ng-data/lang/aav/vi`) nên rất dễ bị chép nếp ấy sang đây; một tên như
     * `model/encoder.int8.onnx` vẫn tải về đúng, chỉ là engine soi sai chỗ.
     */
    @Test
    fun `ten tep phang, khong co tang thu muc`() {
        pack.files.forEach { f ->
            assertFalse(f.name.contains('/'), "${f.name}: KWS đọc tệp phẳng ở `${pack.dir}/` — không được có `/`")
            assertTrue(f.url.endsWith("/" + f.name), "${f.name}: URL không kết thúc bằng chính tên tệp")
        }
    }

    /**
     * Thư mục gói là `kws` **PHẲNG** (khớp `VoiceWakeListener.defaultKws`), và KHÔNG lẫn vào hai họ gói kia.
     *
     * Hai vế đều cần: lẫn vào `sherpa/` thì một lượt gỡ mô hình NGHE xoá mất model câu gọi; đổi thành `kws/<id>`
     * thì bộ nghe không bao giờ soi tới chỗ gói vừa tải về.
     */
    @Test
    fun `thu muc goi la kws phang va khong lan vao hai ho goi kia`() {
        assertEquals("kws", pack.dir, "`VoiceWakeListener.defaultKws` đọc `filesDir/kws` — đổi là hỏng im lặng")
        assertFalse(pack.dir.startsWith(SherpaModelCatalog.DIR_ROOT + "/"), "không được nằm trong thư mục mô hình NGHE")
        assertFalse(pack.dir.startsWith(SherpaTtsCatalog.DIR_ROOT + "/"), "không được nằm trong thư mục gói ĐỌC")
    }

    /**
     * Gói KWS **KHÔNG** được có mặt trong danh sách chọn mô hình NGHE.
     *
     * Chọn nó ở đó là chọn một mô hình không giải mã được câu lệnh nào — và bề mặt ấy không có cách nào nói ra
     * điều đó.
     */
    @Test
    fun `khong lot vao danh sach chon mo hinh nghe`() {
        assertFalse(
            SherpaModelCatalog.ALL.any { it.id == pack.id },
            "gói KWS lọt vào danh mục mô hình NGHE — người dùng chọn được một mô hình không nghe được câu nào",
        )
    }

    /** Mọi đường dẫn TƯƠNG ĐỐI, không leo thư mục (cùng luật `VoiceModelStore.requireSafe` thi hành). */
    @Test
    fun `moi duong dan TUONG DOI va khong leo thu muc`() {
        (pack.files.map { it.name } + pack.dir).forEach { p ->
            assertFalse(p.startsWith("/"), "\"$p\" phải tương đối")
            assertFalse(p.contains('\\') || p.contains(':'), "\"$p\" chứa ký tự đường dẫn của HĐH khác")
            p.split('/').forEach { seg ->
                assertTrue(seg.isNotBlank(), "\"$p\" có đoạn rỗng")
                assertFalse(seg == "." || seg == "..", "\"$p\" chứa `.`/`..` — đường thoát khỏi thư mục app")
            }
        }
    }

    /** Gói tải từ CÙNG kênh raw của repo với `apk/` (OTA) và gói giọng đọc — một kênh, một chỗ đổi. */
    @Test
    fun `tai tu cung kenh raw voi OTA`() {
        pack.files.forEach { f ->
            assertTrue(
                f.url.startsWith("https://raw.githubusercontent.com/dangkhoi/byd-kachi/main/voice/kws/"),
                "${f.name}: URL không trỏ thư mục `voice/kws/` của repo; thấy ${f.url}",
            )
        }
    }

    /** Nhãn hiện trong Cài đặt không được rỗng (hàng tải gói đọc chữ này). */
    @Test
    fun `nhan khong rong`() {
        assertTrue(pack.label.isNotBlank())
    }

    // ══ Chỗ đặt thư mục dựng dở — bug bắt được khi nối gói này (xem KDoc VoicePackPaths) ══════════════════

    /**
     * **Thư mục dựng dở KHÔNG được nằm TRONG thư mục gói** — với gói KWS (`dir` một đoạn) đó là toàn bộ sự khác
     * nhau giữa "cài được" và "không bao giờ cài được".
     *
     * Bước cuối của lượt cài xoá sạch thư mục đích rồi đổi tên thư mục dựng dở vào đó. Nếu dựng dở nằm bên trong
     * đích thì lượt xoá ấy **tự xoá thứ vừa tải xong**, và người dùng nhận câu *"không chuyển được thư mục gói"*.
     */
    @Test
    fun `thu muc dung do khong nam trong thu muc goi`() {
        assertEquals(".staging", VoicePackPaths.stagingDir("kws"), "dir một đoạn ⇒ staging phải nằm NGOÀI nó")
        listOf(WakeModelCatalog.dir, SherpaTtsCatalog.PIPER_VI_VAIS1000.dir, SherpaModelCatalog.default().dir)
            .forEach { d ->
                val staging = VoicePackPaths.stagingDir(d)
                assertFalse(
                    staging.startsWith("$d/") || staging == d,
                    "gói `$d`: thư mục dựng dở `$staging` nằm trong đích ⇒ bước đổi tên cuối sẽ tự xoá nó",
                )
            }
    }

    /** Hai gói cũ (`dir` hai đoạn) phải giữ **nguyên** đường dẫn dựng dở — lượt vá không được đổi hành vi của chúng. */
    @Test
    fun `hai goi cu giu nguyen duong dan dung do`() {
        assertEquals("sherpa/.staging", VoicePackPaths.stagingDir("sherpa/zipformer-vi-int8-2025-04-20"))
        assertEquals("sherpa-tts/.staging", VoicePackPaths.stagingDir("sherpa-tts/piper-vi_VN-vais1000-medium"))
        assertEquals(
            SherpaModelCatalog.DIR_ROOT + "/.staging",
            VoicePackPaths.stagingDir(SherpaModelCatalog.default().dir),
        )
        assertEquals(
            SherpaTtsCatalog.DIR_ROOT + "/.staging",
            VoicePackPaths.stagingDir(SherpaTtsCatalog.PIPER_VI_VAIS1000.dir),
        )
    }
}
