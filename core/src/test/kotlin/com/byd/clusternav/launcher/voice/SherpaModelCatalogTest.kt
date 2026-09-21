package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ MÔ HÌNH NGHE — GHIM SỐ + GIẤY PHÉP + FAIL-SAFE CỔNG (một gói duy nhất từ 2026-09-21) ═════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html`. Khoá lại những thứ **hỏng-thì-không-ai-thấy**: sha256 bị sửa
 * nhầm cho gói giả đi lọt; một gói chưa ghim mà vẫn cho tải; giấy phép NC/ND lọt vào bản phát hành; bẫy
 * `bpeVocab` (đổi lại thành `bpe.model` thô ⇒ biasing chết im lặng); và — mới từ lượt thu danh mục — **pref cũ
 * trỏ vào một gói đã bỏ không được làm câm đường nghe**. Không cần xe.
 */
class SherpaModelCatalogTest {

    /** Một gói bịa để kiểm **phép**, không kiểm dữ liệu: nó không nằm trong danh mục và không được nằm. */
    private fun fake(
        id: String,
        license: String = "Apache-2.0",
        bytes: Long = 999_000_000L,
        attribution: String = "",
        experimental: Boolean = false,
    ) = SherpaModelCatalog.SherpaModel(
        id = id,
        label = "gói bịa $id",
        license = license,
        files = listOf(SherpaModelCatalog.ModelFile("encoder.onnx", "https://x/y", "a".repeat(64), bytes)),
        encoder = "encoder.onnx",
        decoder = "decoder.onnx",
        joiner = "joiner.onnx",
        tokens = "tokens.txt",
        bpeVocab = "$id.bpe_vocab.txt",
        attribution = attribution,
        sourceUrl = if (attribution.isBlank()) "" else "https://example.invalid/$id",
        experimental = experimental,
    )

    // ══ (1) Gói duy nhất = gói mặc định, tải được, ghim đủ ════════════════════════════════════════════════

    @Test
    fun `default model is the Apache Zipformer-vi int8 and is fully pinned`() {
        // V3 · R6 (1.66): mặc định đổi fp32 → **int8** (266 MB → 74 MB; [ĐO host] 25 WAV cùng kết quả). Từ bản
        // release production (2026-09-21) fp32 đã rời danh mục, nên "mặc định" và "gói duy nhất" là một thứ.
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
    fun `goi duy nhat khai du bon tep transducer va totalBytes la tong cua chung`() {
        val m = SherpaModelCatalog.ZIPFORMER_VI_INT8
        listOf(m.encoder, m.decoder, m.joiner, m.tokens).forEach { name ->
            assertNotNull(m.file(name), "thiếu tệp thành phần $name trong danh sách tải")
        }
        // 70 876 129 + 5 165 084 + 1 033 417 + 25 847 — con số này là thứ màn Cài đặt hứa với người dùng trước khi
        // họ bấm Tải trên mạng 4G của xe, nên nó phải là một TỔNG, không phải một số gõ tay ở đâu đó.
        assertEquals(70_876_129L + 5_165_084L + 1_033_417L + 25_847L, m.totalBytes)
        assertEquals(77_100_477L, m.totalBytes, "cỡ tổng đổi ⇒ nhãn '74 MB' và câu chữ Cài đặt phải đổi theo")
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

    // ══ (3) Tra id lạ / rỗng / **id của gói đã bỏ** quay về gói duy nhất ══════════════════════════════════

    /**
     * ⚠ Ca *"id của gói đã bỏ"* là ca **có thật trên xe đang chạy**, không phải một ca giả định: tới 1.87 màn Cài
     * đặt có nút *"chuyển sang mô hình nhẹ"* ghi id vào prefs `kachi_voice`, và lượt thu danh mục 2026-09-21 làm
     * bốn id trở thành lạ. Không lùi được thì người lái bấm mic ra *"chưa tải mô hình"* trên một chiếc xe có đủ
     * tệp trên đĩa.
     */
    @Test
    fun `byId lui ve goi duy nhat voi id la, rong, hoac id cua goi da bo`() {
        assertEquals(SherpaModelCatalog.default(), SherpaModelCatalog.byId(null))
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI_INT8, SherpaModelCatalog.byId(null))
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI_INT8, SherpaModelCatalog.byId(""))
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI_INT8, SherpaModelCatalog.byId("khong-ton-tai"))
        // Bốn id đã rời danh mục 2026-09-21 — viết bằng CHUỖI vì chính các hằng đã bị xoá; đây là hợp đồng với
        // **dữ liệu đã lưu trên xe**, không phải với mã hôm nay.
        listOf(
            "zipformer-vi-2025-04-20",
            "zipformer-hataphu-vi",
            "zipformer-vi-30M-int8-2026-02-09",
            "gipformer-vi-ft-ep2",
        ).forEach { old ->
            assertEquals(
                SherpaModelCatalog.ZIPFORMER_VI_INT8, SherpaModelCatalog.byId(old),
                "pref cũ '$old' phải lùi về gói duy nhất, không được làm câm đường nghe",
            )
        }
        // Và id đúng vẫn ra đúng gói (đừng "sửa" bằng cách luôn trả phần tử đầu — xem bài `pickDefault` dưới).
        assertEquals(
            SherpaModelCatalog.ZIPFORMER_VI_INT8,
            SherpaModelCatalog.byId("zipformer-vi-int8-2025-04-20"),
        )
    }

    /**
     * [SOÁT 2026-09-16 · P3] **Một `DEFAULT_ID` gõ nhầm không được phép thành crash trên xe.**
     *
     * `default()` từng là `ALL.first { it.id == DEFAULT_ID }` — `first{}` **ném** `NoSuchElementException`, trong
     * khi `byId` ngay dưới nó lại degrade. Hai hàm cạnh nhau, cùng một câu hỏi, hai kiểu hỏng. Và cả [DEFAULT_ID]
     * lẫn `ALL` **đã** được sửa nhiều lượt (1.66 → 1.69 → quay lại int8 → thu về một gói), tức đây không phải rủi
     * ro giả định: một ký tự sai = `NoSuchElementException` ở **lượt mở voice đầu tiên trên xe**.
     *
     * [DEFAULT_ID] là `const` nên không thể đặt sai trong bài kiểm ⇒ kiểm **phép chọn** (`pickDefault`) với một
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
        // Và id đúng vẫn ra đúng gói: kiểm bằng một danh mục HAI phần tử (gói bịa đứng trước) — với danh mục một
        // phần tử thì "trả phần tử đầu" và "tra đúng id" không phân biệt được, tức bài sẽ xanh cả khi phép tra hỏng.
        val two = listOf(fake("goi-bia-dung-truoc"), SherpaModelCatalog.ZIPFORMER_VI_INT8)
        assertEquals(
            SherpaModelCatalog.ZIPFORMER_VI_INT8,
            SherpaModelCatalog.pickDefault(two, SherpaModelCatalog.DEFAULT_ID),
        )
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI_INT8, SherpaModelCatalog.default())
    }

    // ══ (4) DANH MỤC MỘT GÓI — và cổng giấy phép NC/ND vẫn còn hiệu lực ═══════════════════════════════════

    /**
     * ═══ Đúng MỘT gói, không gói nào THỬ NGHIỆM, không gói nào mang NC/ND ════════════════════════════════
     *
     * Lịch sử con số: **3** (1.66: fp32 · int8 · hataphu gated) → **4** (1.69: + `vi-30M-int8` CC BY-NC-ND) →
     * **5** (1.70: + gói G fine-tune) → **1** (bản release production, owner 2026-09-21: *"chỉ giữ đúng 1 model
     * nghe = model đang OK trên xe"*, bỏ mọi gói thử nghiệm + bề mặt cho chọn mô hình).
     *
     * Luật NC/ND **giữ nguyên hình dạng** dù hôm nay không còn gói nào mang nó: *"có thì phải VÔ HẠI"* (cờ thử
     * nghiệm · không được là mặc định · phải ghi công). Nới thành *"cứ có cũng được"* là mở đường cho một gói
     * cấm-phái-sinh lặng lẽ thành mặc định ở lượt sau; xoá hẳn luật là mất chính cái chặn đó.
     */
    @Test
    fun `danh muc con dung MOT goi, khong goi nao thu nghiem hay mang NC-ND`() {
        assertEquals(1, SherpaModelCatalog.ALL.size, "danh mục đổi số ⇒ đọc KDoc bài này rồi mới ghim số mới")
        assertEquals(listOf("zipformer-vi-int8-2025-04-20"), SherpaModelCatalog.ALL.map { it.id })
        assertTrue(
            SherpaModelCatalog.ALL.none { it.experimental },
            "bản phát hành không được ship gói THỬ NGHIỆM nào — owner đã chốt dừng thử nghiệm mô hình",
        )
        SherpaModelCatalog.ALL.filter {
            it.license.contains("NC", ignoreCase = true) || it.license.contains("ND", ignoreCase = true)
        }.forEach { nc ->
            assertTrue(nc.experimental, "gói NC/ND `${nc.id}` phải mang cờ thử nghiệm")
            assertTrue(nc.id != SherpaModelCatalog.DEFAULT_ID, "gói NC/ND không được là mặc định")
            assertTrue(nc.attribution.isNotBlank(), "giấy phép họ BY đòi ghi công — `${nc.id}` đang để trống")
        }
        val d = SherpaModelCatalog.default()
        assertTrue(d.downloadable, "gói mặc định phải ghim đủ sha256+cỡ, nếu không máy cài mới không tải được gì")
        assertFalse(d.experimental, "mặc định không bao giờ được là một gói thử nghiệm")
    }

    @Test
    fun `thu muc nam duoi sherpa va rieng cho tung goi`() {
        assertEquals("sherpa/zipformer-vi-int8-2025-04-20", SherpaModelCatalog.ZIPFORMER_VI_INT8.dir)
        assertEquals(
            SherpaModelCatalog.ALL.size, SherpaModelCatalog.ALL.map { it.dir }.toSet().size,
            "hai gói dùng chung thư mục là hai gói ghi đè tệp của nhau",
        )
        SherpaModelCatalog.ALL.forEach {
            assertTrue(it.dir.startsWith(SherpaModelCatalog.DIR_ROOT + "/"), "${it.id}: phải nằm dưới `sherpa/`")
        }
    }

    @Test
    fun `default model uses modified_beam_search so biasing is available`() {
        assertEquals("modified_beam_search", SherpaModelCatalog.ZIPFORMER_VI_INT8.decodingMethod)
        assertEquals(3.0f, SherpaModelCatalog.HOTWORDS_SCORE)
        assertEquals("bpe", SherpaModelCatalog.MODELING_UNIT)
    }

    // ══ 1.69 · BẢNG BPE THEO MÔ HÌNH — cái bẫy IM LẶNG nhất của lượt thêm mô hình ═════════════════════════

    /**
     * Mỗi mô hình phải có **asset BPE thật sự tồn tại**, và hai mô hình chỉ được dùng chung một bảng khi
     * `tokens.txt` của chúng **giống hệt nhau**.
     *
     * ## Vì sao luật là "cùng tokens ⇒ được chung", không phải "mỗi mô hình một bảng"
     * [ĐO 2026-09-16] hai bản huấn luyện khác nhau cho hai bảng đúng 2 000 mảnh mà **1 997/2 000 dòng khác nhau**,
     * và dùng nhầm thì sherpa **lặng lẽ bỏ** mọi cụm hotword — biasing trông như đang bật mà không làm gì. Không
     * có lỗi, không có log, chỉ là nghe kém đi. Ngược lại, bản fp32 và int8 của **cùng** một bản huấn luyện có
     * `tokens.txt` trùng tới từng byte nên cố ý dùng chung một bảng (đóng hai bản 55 KB giống hệt vào APK là bẫy
     * hai-bản-sao).
     *
     * ⚠ Gói duy nhất còn lại vẫn trỏ vào asset mang tên bản **fp32** (`zipformer-vi-2025-04-20.bpe_vocab.txt`) —
     * đó là di sản của luật trên, không phải một lượt đổi tên bị sót; xem chú thích tại chỗ khai.
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

        // (c) ⚠ KHÔNG còn asset BPE MỒ CÔI trong APK: mỗi bảng phải có một gói trong danh mục trỏ vào nó. Lượt thu
        // danh mục 2026-09-21 bỏ bốn gói; hai bảng riêng của chúng (~55 KB mỗi bảng) phải rời APK cùng lượt, không
        // thì chúng nằm lại mãi — không ai đọc, không ai thấy, mà vẫn chiếm chỗ và vẫn trông như đang được dùng.
        val declared = SherpaModelCatalog.ALL.map { it.bpeVocab }.toSet()
        val onDisk = java.nio.file.Files.list(assets).use { s ->
            s.map { it.fileName.toString() }.filter { it.endsWith(".bpe_vocab.txt") }.sorted().toList()
        }
        assertEquals(
            declared.sorted(), onDisk,
            "bảng BPE trong assets/voice/ không khớp danh mục — bảng thừa là rác APK, bảng thiếu là biasing chết im lặng",
        )
    }

    // ══ GHI CÔNG — nghĩa vụ `BY` của giấy phép, kiểm bằng PHÉP chứ không bằng dữ liệu hôm nay ═════════════

    /**
     * ⚠ Bài này **đổi hình dạng** ở lượt 2026-09-21. Trước đó nó duyệt danh mục và đòi *"phải có ít nhất gói 30M
     * (CC BY-NC-ND)"* — một phép đo trên dữ liệu hiện thời. Gói ấy đã bỏ, và gói duy nhất còn lại là Apache-2.0,
     * nên nếu chỉ sửa con số thì bài sẽ duyệt một tập **rỗng** và xanh mãi mãi: đúng cái mà chính KDoc cũ của nó
     * gọi là *"đang đo hư không"*.
     *
     * ⇒ Nay kiểm **cơ chế**: (a) hôm nay không gói nào đòi ghi công, và ba bề mặt hiển thị vì thế không hiện gì;
     * (b) một gói CC BY **có** ghi công thì `requiresAttribution` nhận; (c) một gói CC BY **thiếu** ghi công thì
     * nó KHÔNG lặng lẽ được coi là đã ghi công — chỗ duy nhất phát hiện nghĩa vụ bị bỏ là phép này.
     */
    @Test
    fun `phep ghi cong con song du hom nay khong goi nao doi ghi cong`() {
        assertTrue(
            SherpaModelCatalog.attributions().isEmpty(),
            "gói duy nhất là Apache-2.0 ⇒ danh sách ghi công phải rỗng (hàng Cài đặt tự ẩn, không hiện tiêu đề trống)",
        )
        SherpaModelCatalog.ALL.forEach {
            assertFalse(
                it.license.contains("BY", ignoreCase = true),
                "gói '${it.id}' mang giấy phép họ BY mà lại vắng trong danh sách ghi công",
            )
        }
        // (b) + (c) — phép vẫn phân biệt đúng hai ca, nên ngày một gói CC BY quay lại danh mục thì nghĩa vụ ghi
        // công được phát hiện ở đây chứ không ở một lượt review.
        assertTrue(SherpaModelCatalog.requiresAttribution(fake("co-ghi-cong", "CC-BY-4.0", attribution = "tác giả X")))
        assertFalse(
            SherpaModelCatalog.requiresAttribution(fake("thieu-ghi-cong", "CC-BY-NC-ND-4.0")),
            "CC BY mà attribution rỗng thì KHÔNG được tính là đã ghi công — nếu không, nghĩa vụ biến mất im lặng",
        )
        assertFalse(SherpaModelCatalog.requiresAttribution(SherpaModelCatalog.ZIPFORMER_VI_INT8))
    }

    // ══ `lighterThan` — hôm nay luôn `null`, nhưng vẫn phải là một phép trên DỮ LIỆU ══════════════════════

    /**
     * Bề mặt *"chuyển sang mô hình nhẹ"* đã gỡ khỏi Cài đặt (owner 2026-09-21); chỗ gọi còn lại là trường máy-đọc
     * `state.voice_model.alt_available` của cầu kiểm thử.
     *
     * Bài này khoá **hai** vế, vì chỉ khoá vế đầu thì `return null` cứng cũng xanh — và một hàm luôn trả `null`
     * bằng cách bỏ qua dữ liệu là một hàm đã chết mà không ai thấy:
     *  • đang ở gói duy nhất ⇒ không còn gì nhẹ hơn (`null`);
     *  • hỏi từ một gói NẶNG hơn ⇒ phải trỏ đúng về gói trong danh mục (phép còn đọc [SherpaModelCatalog.ALL]).
     */
    @Test
    fun `lighterThan tra null o goi duy nhat, nhung van la phep tren du lieu`() {
        assertNull(
            SherpaModelCatalog.lighterThan(SherpaModelCatalog.ZIPFORMER_VI_INT8),
            "đang ở gói duy nhất thì không có gì để đề nghị",
        )
        val heavy = fake("goi-nang-hon", bytes = 999_000_000L)
        assertEquals(
            SherpaModelCatalog.ZIPFORMER_VI_INT8.id, SherpaModelCatalog.lighterThan(heavy)?.id,
            "hỏi từ một gói nặng hơn thì phải ra gói trong danh mục — nếu `null`, hàm đã thành hằng số",
        )
        // Gói chưa ghim sha256 không bao giờ được đề nghị (fail-safe) — dù nó nhẹ hơn.
        val unpinned = SherpaModelCatalog.SherpaModel(
            id = "chua-ghim", label = "chưa ghim", license = "MIT",
            files = listOf(SherpaModelCatalog.ModelFile("encoder.onnx", "", "", 0L)),
            encoder = "encoder.onnx", decoder = "decoder.onnx", joiner = "joiner.onnx", tokens = "tokens.txt",
            bpeVocab = "chua-ghim.bpe_vocab.txt",
        )
        assertFalse(unpinned.downloadable, "gói chưa ghim phải là `downloadable = false`")
    }
}
