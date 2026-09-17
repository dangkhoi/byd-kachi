package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V2 pha NGHE · BẢNG MÔ HÌNH sherpa — GHIM SỐ + GIẤY PHÉP + FAIL-SAFE CỔNG ════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html`. Khoá lại những thứ **hỏng-thì-không-ai-thấy**: sha256 bị sửa
 * nhầm cho gói giả đi lọt; một mô hình chưa ghim mà vẫn cho tải; giấy phép NC/ND lọt vào bản phát hành; và bẫy
 * `bpeVocab` (§Impl Log) — nếu ai đổi lại thành `bpe.model` thô thì biasing chết im lặng. Không cần xe.
 */
class SherpaModelCatalogTest {

    // ══ (1) Mô hình mặc định tải được + đã ghim đủ ════════════════════════════════════════════════════════

    @Test
    fun `default model is the Apache Zipformer-vi int8 and is fully pinned`() {
        // V3 · R6 (1.66): mặc định đổi fp32 → **int8** (266 MB → 74 MB; [ĐO host] 25 WAV cùng kết quả). Xe đã
        // cài fp32 thì `VoiceModelStore.selected` giữ nguyên fp32 — luật ấy có bài canh ở `:app`.
        val m = SherpaModelCatalog.byId(SherpaModelCatalog.DEFAULT_ID)
        assertEquals("zipformer-vi-int8-2025-04-20", m.id)
        assertEquals("Apache-2.0", m.license)
        assertTrue(m.downloadable, "mô hình mặc định PHẢI tải được (đã ghim mọi tệp)")
        m.files.forEach { f ->
            assertTrue(f.pinned, "tệp ${f.name} phải ghim cả sha256 lẫn size")
            assertEquals(64, f.sha256.length, "sha256 của ${f.name} phải đủ 64 hex")
            assertTrue(f.url.startsWith("https://"), "URL của ${f.name} phải HTTPS")
        }
    }

    @Test
    fun `fp32 model names its four transducer files and total bytes sum them`() {
        val m = SherpaModelCatalog.ZIPFORMER_VI
        listOf(m.encoder, m.decoder, m.joiner, m.tokens).forEach { name ->
            assertNotNull(m.file(name), "thiếu tệp thành phần $name trong danh sách tải")
        }
        assertEquals(261_057_692L + 5_165_084L + 4_104_465L + 25_847L, m.totalBytes)
    }

    // ══ (2) BẪY bpeVocab — KHÔNG bao giờ là `bpe.model` thô, KHÔNG nằm trong danh sách TẢI ═════════════════

    @Test
    fun `bpeVocab is a shipped asset table, never the raw sentencepiece model`() {
        SherpaModelCatalog.ALL.forEach { m ->
            assertFalse(
                m.bpeVocab.endsWith(".model"),
                "${m.id}: bpeVocab KHÔNG được là bpe.model thô — sherpa 1.13.8 từ chối ⇒ biasing chết im lặng",
            )
            assertTrue(m.bpeVocab.endsWith(".bpe_vocab.txt"), "${m.id}: bpeVocab phải là bảng piece+score .bpe_vocab.txt")
            // Bảng BPE đóng theo APK làm asset, KHÔNG tải qua mạng ⇒ không nằm trong files.
            assertTrue(m.files.none { it.name == m.bpeVocab }, "${m.id}: bpeVocab là ASSET, không được nằm trong danh sách tải")
        }
    }

    // ══ (3) Mô hình có cổng (hataphu) — FAIL-SAFE: chưa mirror thì KHÔNG tải ══════════════════════════════

    @Test
    fun `gated hataphu model is declared MIT but not downloadable until mirrored`() {
        val m = SherpaModelCatalog.HATAPHU_VI
        assertEquals("MIT", m.license)
        assertFalse(m.downloadable, "mô hình có cổng CHƯA ghim sha256 ⇒ phải bị từ chối tải (fail-safe)")
        assertTrue(m.files.none { it.pinned }, "mọi tệp hataphu phải để trống pin cho tới khi mirror")
    }

    // ══ (4) Tra id lạ / rỗng quay về mặc định (pref cũ / hỏng không được làm câm tính năng) ════════════════

    @Test
    fun `byId falls back to default for unknown or blank id`() {
        // ⚠ 1.66: `byId` lùi về [SherpaModelCatalog.default] (= DEFAULT_ID), KHÔNG còn lùi về `ZIPFORMER_VI`
        // viết cứng — trước đó hằng DEFAULT_ID là một hằng **không ai đọc**, đổi nó không đổi hành vi dòng nào.
        assertEquals(SherpaModelCatalog.default(), SherpaModelCatalog.byId(null))
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI_INT8, SherpaModelCatalog.byId(null))
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI_INT8, SherpaModelCatalog.byId(""))
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI_INT8, SherpaModelCatalog.byId("khong-ton-tai"))
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI, SherpaModelCatalog.byId("zipformer-vi-2025-04-20"))
        assertEquals(SherpaModelCatalog.HATAPHU_VI, SherpaModelCatalog.byId("zipformer-hataphu-vi"))
    }

    /**
     * [SOÁT 2026-09-16 · P3] **Một `DEFAULT_ID` gõ nhầm không được phép thành crash trên xe.**
     *
     * `default()` từng là `ALL.first { it.id == DEFAULT_ID }` — `first{}` **ném** `NoSuchElementException`, trong
     * khi `byId` ngay dưới nó lại degrade. Hai hàm cạnh nhau, cùng một câu hỏi, hai kiểu hỏng. Và [DEFAULT_ID]
     * **đang được đổi trong chính nhánh này** (1.66 → 1.69 → quay lại int8), tức đây không phải rủi ro giả định:
     * một ký tự sai = `NoSuchElementException` ở **lượt mở voice đầu tiên trên xe**, nơi không ai debug được.
     *
     * [DEFAULT_ID] là `const` nên không thể đặt sai trong bài kiểm ⇒ kiểm **phép chọn** ([pickDefault]) với một
     * id lạ, cộng thêm vế xuôi: id thật vẫn phải nằm trong danh mục (gõ nhầm ⇒ đỏ **ở đây**, không phải trên xe).
     */
    @Test
    fun `DEFAULT_ID go nham thi degrade, KHONG duoc nem — va id that phai co trong danh muc`() {
        assertNotNull(
            SherpaModelCatalog.ALL.firstOrNull { it.id == SherpaModelCatalog.DEFAULT_ID },
            "DEFAULT_ID không khớp gói nào — sửa hằng, đừng sửa bài kiểm",
        )
        assertTrue(SherpaModelCatalog.ALL.isNotEmpty(), "danh mục rỗng là lỗi lập trình, không phải trạng thái chạy")
        // Id lạ ⇒ lùi về gói đầu danh mục, KHÔNG ném — cùng lối lùi mà `byId` đã dùng từ 1.66.
        assertEquals(
            SherpaModelCatalog.ALL.first(),
            SherpaModelCatalog.pickDefault(SherpaModelCatalog.ALL, "go-nham-mot-ky-tu"),
        )
        assertEquals(SherpaModelCatalog.ALL.first(), SherpaModelCatalog.pickDefault(SherpaModelCatalog.ALL, ""))
        // Và id đúng vẫn ra đúng gói (đừng "sửa" bằng cách luôn trả phần tử đầu).
        assertEquals(
            SherpaModelCatalog.ZIPFORMER_VI,
            SherpaModelCatalog.pickDefault(SherpaModelCatalog.ALL, SherpaModelCatalog.ZIPFORMER_VI.id),
        )
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI_INT8, SherpaModelCatalog.default())
    }

    @Test
    fun `catalog co bon goi, va goi NC-ND chi duoc ton tai duoi co THU NGHIEM`() {
        // 1.66: **3** — fp32 · int8 (mặc định) · hataphu (có cổng, chưa mirror).
        // 1.69: **4** — thêm `zipformer-vi-30M-int8` (CC BY-NC-ND). Bài này trước đây cấm thẳng mọi giấy phép
        // mang `NC`/`ND`; nay owner đã chốt chấp nhận (*"phi lợi nhuận, vui vẻ với anh em"*), nên luật đổi từ
        // *"không được có"* thành *"có thì phải VÔ HẠI"*: gói NC/ND bắt buộc mang cờ [SherpaModel.experimental],
        // KHÔNG được là mặc định, và KHÔNG được lọt vào phép đề nghị `lighterThan`. Nới thành *"cứ có cũng
        // được"* là mở đường cho nó lặng lẽ thành mặc định ở một lượt sau.
        assertEquals(5, SherpaModelCatalog.ALL.size)   // 1.70: + gói G fine-tune giọng thật (experimental)
        SherpaModelCatalog.ALL.filter {
            it.license.contains("NC", ignoreCase = true) || it.license.contains("ND", ignoreCase = true)
        }.forEach { nc ->
            assertTrue(nc.experimental, "gói NC/ND `${nc.id}` phải mang cờ thử nghiệm")
            assertTrue(nc.id != SherpaModelCatalog.DEFAULT_ID, "gói NC/ND không được là mặc định")
            assertTrue(nc.attribution.isNotBlank(), "giấy phép họ BY đòi ghi công — `${nc.id}` đang để trống")
        }
        // int8 và fp32 là CÙNG một bản huấn luyện ⇒ phải dùng CHUNG một asset BPE; hai bản 55 KB giống hệt
        // trong APK là bẫy hai-bản-sao, và `VoiceEngine` tra asset theo `bpeVocab` chính vì thế.
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI.bpeVocab, SherpaModelCatalog.ZIPFORMER_VI_INT8.bpeVocab)
        assertEquals(
            SherpaModelCatalog.ZIPFORMER_VI.file("tokens.txt")?.sha256,
            SherpaModelCatalog.ZIPFORMER_VI_INT8.file("tokens.txt")?.sha256,
            "cùng tokens ⇒ cùng sha256; lệch nghĩa là hai bản huấn luyện khác nhau và KHÔNG được chung bpe",
        )
    }

    @Test
    fun `directory is namespaced under sherpa and distinct per model`() {
        assertEquals("sherpa/zipformer-vi-2025-04-20", SherpaModelCatalog.ZIPFORMER_VI.dir)
        assertEquals("sherpa/zipformer-hataphu-vi", SherpaModelCatalog.HATAPHU_VI.dir)
    }

    @Test
    fun `default model uses modified_beam_search so biasing is available`() {
        assertEquals("modified_beam_search", SherpaModelCatalog.ZIPFORMER_VI.decodingMethod)
        assertEquals(3.0f, SherpaModelCatalog.HOTWORDS_SCORE)
        assertEquals("bpe", SherpaModelCatalog.MODELING_UNIT)
    }

    // ══ 1.69 · BẢNG BPE THEO MÔ HÌNH — cái bẫy IM LẶNG nhất của lượt thêm mô hình ═════════════════════════

    /**
     * Mỗi mô hình phải có **asset BPE thật sự tồn tại**, và hai mô hình chỉ được dùng chung một bảng khi
     * `tokens.txt` của chúng **giống hệt nhau**.
     *
     * ## Vì sao luật là "cùng tokens ⇒ được chung", không phải "mỗi mô hình một bảng"
     * [ZIPFORMER_VI] và [ZIPFORMER_VI_INT8] **cố ý** dùng chung một bảng: chúng là cùng một bản huấn luyện, khác
     * mỗi phép lượng hoá, và `tokens.txt` của chúng trùng tới từng byte (cùng sha256 trong danh mục). Ép mỗi mô
     * hình một bảng là đóng thêm 55 KB giống hệt vào APK — đúng bẫy hai-bản-sao mà KDoc 1.66 đã dựng luật để
     * tránh. Còn [ZIPFORMER_VI_30M_INT8] là một bản huấn luyện **khác**: [ĐO 2026-09-16] cả hai bảng đúng 2 000
     * mảnh mà **1 997/2 000 dòng khác nhau**, và dùng nhầm thì sherpa **lặng lẽ bỏ** mọi cụm hotword — biasing
     * trông như đang bật mà không làm gì. Không có lỗi, không có log, chỉ là nghe kém đi.
     *
     * ⇒ Điều kiện đúng là **cùng `tokens.txt`**, và bài này kiểm đúng điều đó.
     */
    @Test
    fun `moi mo hinh co asset BPE that, va chi dung chung bang khi tokens giong het`() {
        val assets = java.nio.file.Paths.get("../app/src/main/assets/voice")
            .takeIf { java.nio.file.Files.isDirectory(it) }
            ?: java.nio.file.Paths.get("app/src/main/assets/voice")
        assertTrue(java.nio.file.Files.isDirectory(assets), "không thấy thư mục asset voice/ — bộ quét sai gốc")

        // (a) Mọi gói TẢI ĐƯỢC phải có asset BPE nằm thật trên đĩa. Thiếu tệp ⇒ chạy không biasing, im lặng.
        SherpaModelCatalog.ALL.filter { it.downloadable }.forEach { m ->
            val f = assets.resolve(m.bpeVocab)
            assertTrue(
                java.nio.file.Files.isRegularFile(f),
                "mô hình '${m.id}' trỏ bảng BPE '${m.bpeVocab}' mà tệp KHÔNG tồn tại ⇒ biasing tắt im lặng",
            )
            assertEquals(
                2_000, java.nio.file.Files.readAllLines(f).count { it.isNotBlank() },
                "bảng BPE của '${m.id}' phải đúng 2 000 mảnh (cỡ từ điển của mô hình)",
            )
        }

        // (b) Dùng chung bảng ⇒ BẮT BUỘC cùng sha256 của `tokens.txt`.
        SherpaModelCatalog.ALL.groupBy { it.bpeVocab }.forEach { (vocab, models) ->
            if (models.size < 2) return@forEach
            val tokenShas = models.map { m -> m.file(m.tokens)?.sha256.orEmpty() }.toSet()
            assertEquals(
                1, tokenShas.size,
                "các mô hình ${models.map { it.id }} dùng chung bảng '$vocab' mà `tokens.txt` KHÁC nhau " +
                    "⇒ mọi cụm hotword bị mã hoá sai và sherpa bỏ chúng mà không báo gì",
            )
        }

        // (c) Mốc cụ thể: gói 30M phải có bảng RIÊNG, khác bảng của gói 2025-04-20.
        assertNotEquals(
            SherpaModelCatalog.ZIPFORMER_VI.bpeVocab,
            SherpaModelCatalog.ZIPFORMER_VI_30M_INT8.bpeVocab,
            "hai bản huấn luyện khác nhau KHÔNG được dùng chung bảng BPE (1 997/2 000 dòng khác nhau)",
        )
        // …và hai bản cùng huấn luyện thì vẫn phải dùng chung (không nhân bản 55 KB vào APK).
        assertEquals(
            SherpaModelCatalog.ZIPFORMER_VI.bpeVocab,
            SherpaModelCatalog.ZIPFORMER_VI_INT8.bpeVocab,
            "fp32 và int8 của CÙNG bản huấn luyện phải dùng chung một bảng",
        )
    }

    // ══ 1.69 · GHI CÔNG — nghĩa vụ `BY` của giấy phép ════════════════════════════════════════════════════

    /**
     * Gói mang giấy phép họ `BY` **phải** có dòng ghi công + URL. Đây là một nghĩa vụ pháp lý, không phải lịch sự,
     * nên nó cần một bài canh chứ không phải một lời nhắc trong review.
     */
    @Test
    fun `goi mang giay phep BY deu co ghi cong va URL`() {
        val by = SherpaModelCatalog.ALL.filter { it.license.contains("BY", ignoreCase = true) }
        assertTrue(by.isNotEmpty(), "danh mục phải có ít nhất gói 30M (CC BY-NC-ND) — nếu không, bài này đang đo hư không")
        by.forEach { m ->
            assertTrue(m.attribution.isNotBlank(), "gói '${m.id}' mang giấy phép ${m.license} mà KHÔNG ghi công")
            assertTrue(m.sourceUrl.startsWith("https://"), "gói '${m.id}' phải có URL nguồn để người dùng tự kiểm")
            assertTrue(SherpaModelCatalog.requiresAttribution(m), "gói '${m.id}' phải lọt vào danh sách ghi công")
        }
        // Ba chỗ hiển thị đọc CÙNG một nguồn — không chỗ nào dựng câu chữ riêng.
        assertEquals(by.size, SherpaModelCatalog.attributions().size)
        assertTrue(
            SherpaModelCatalog.attributions().all { it.license.isNotBlank() && it.sourceUrl.startsWith("https://") },
            "mỗi mục ghi công phải mang cả giấy phép lẫn URL: ${SherpaModelCatalog.attributions().map { it.id }}",
        )
    }

    /** Mặc định cho máy cài mới là gói 30M, và nó phải TẢI ĐƯỢC (ghim đủ sha256 + cỡ cho cả bốn tệp). */
    @Test
    fun `goi 30M co trong danh muc nhung KHONG duoc de nghi, va mac dinh van la int8 da chung minh`() {
        // ═══ [ĐO giọng THẬT owner 2026-09-16] — phép đo này ĐẢO một quyết định đã ship trong cùng ngày ═════
        //
        // Lượt đầu của 1.69 đặt 30M làm mặc định, dựa trên corpus TTS (nó chấm 30M cao hơn **+1,8 điểm**). Rồi
        // owner đọc 30 câu THẬT × 3 lượt và giải mã trên host: gói đang ship đúng **≈ 28/30** ở lượt nói
        // thường, còn 30M sai **~7 câu** (*"xem pin"* → *"xem binh"* · *"bật ghế sưởi"* → *"bọc ghế sửi"* ·
        // *"mở quây"* → *"mở quay"* · *"lọc bụi mịn"* → *"bộ mệnh"*). Tức **corpus TTS đã đánh lừa** — lần thứ
        // hai trong một ngày giọng tổng hợp nói ngược giọng người.
        //
        // Bài này khoá **cả ba** mặt của phép đảo, vì bỏ sót mặt nào cũng đủ để 30M lặng lẽ quay lại tay người
        // dùng: nó không phải mặc định · nó không lọt vào phép đề nghị *"nhẹ hơn"* · và gói ĐƯỢC đề nghị vẫn là
        // bản int8 cùng bản huấn luyện với gói đã chứng minh.
        assertEquals("zipformer-vi-int8-2025-04-20", SherpaModelCatalog.DEFAULT_ID)
        val d = SherpaModelCatalog.default()
        assertTrue(d.downloadable, "gói mặc định phải ghim đủ sha256+cỡ, nếu không máy cài mới không tải được gì")
        assertTrue(!d.experimental, "mặc định không bao giờ được là một gói thử nghiệm")

        val m30 = SherpaModelCatalog.ZIPFORMER_VI_30M_INT8
        assertTrue(m30.downloadable, "giữ gói 30M trong danh mục thì phải giữ CẢ sha256 — nếu không, giữ làm gì")
        assertTrue(m30.experimental, "30M nghe kém hơn trên giọng thật ⇒ phải mang cờ thử nghiệm")
        assertTrue(
            m30.label.contains("THỬ NGHIỆM"),
            "nhãn phải tự nói ra điều đó: nhãn là thứ duy nhất người dùng đọc trước khi bấm",
        )

        // `lighterThan` KHÔNG được trỏ về 30M dù nó nhẹ nhất (34 < 74 < 266 MB) — đó chính là cái bẫy: một hàng
        // Cài đặt ghi *"nhẹ hơn"* mà đưa người dùng sang một gói nghe kém hơn.
        assertEquals(
            SherpaModelCatalog.ZIPFORMER_VI_INT8.id,
            SherpaModelCatalog.lighterThan(SherpaModelCatalog.ZIPFORMER_VI)?.id,
            "gói được đề nghị phải là bản int8 CÙNG bản huấn luyện với gói đang chạy tốt",
        )
        assertNull(
            SherpaModelCatalog.lighterThan(SherpaModelCatalog.ZIPFORMER_VI_INT8),
            "đang ở gói int8 rồi thì không còn gì để đề nghị — 30M nhẹ hơn nhưng bị cờ thử nghiệm chặn",
        )
    }
}
