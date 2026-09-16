package com.byd.clusternav.launcher.voice

/**
 * ═══ V2 pha NGHE · GÓI MÔ HÌNH sherpa-onnx — KHAI **MỘT CHỖ**, GHIM TỪNG TỆP BẰNG SỐ ĐO THẬT ════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html`. Thuần Kotlin (`:core`) ⇒ mọi luật kiểm off-car; phần chạm
 * đĩa/mạng nằm ở `:app` ([VoiceModelStore]).
 *
 * ## Vì sao thay [VoiceModelManifest] (Vosk) bằng bảng NHIỀU tệp
 * Mô hình Vosk là **một** gói zip (mở ra thành cây thư mục Kaldi). Mô hình sherpa-onnx là **rời từng tệp ONNX**
 * (encoder/decoder/joiner + tokens + bpe). Không có gói nén chuẩn để ghim một sha duy nhất, nên mỗi tệp phải tự
 * mang sha256 + kích thước — tải cụt hay proxy chèn trang lỗi bị bắt **từng tệp một**, đúng tinh thần R9 cũ.
 *
 * ## Vì sao có HAI mô hình (A/B trên xe)
 * WER công bố đo trên tập sạch, KHÔNG phải giọng thật + mic 4 kênh + ồn đường (CLAUDE.md §2). Muốn biết cái nào
 * thắng trên xe THẬT thì phải nghe cả hai trên xe. [VoiceModelSettings] cho chọn, mặc định [DEFAULT_ID].
 *
 * ## Giấy phép (đọc LICENSE thật 2026-09-14 — CLAUDE.md §1.1)
 *  • [ZIPFORMER_VI] — nguồn `zzasdf/viet_iter3_pseudo_label` = **Apache-2.0** ([ĐO] HF metadata). Gói sherpa
 *    `csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20` tải trực tiếp (không cổng), 70k giờ pseudo-label.
 *  • [HATAPHU_VI] — `hataphu/zipformer-k2-rnn-lm-vi` = **MIT** ([ĐO]), fine-tune telephony (WER 5.73% VLSP2020)
 *    ⇒ hợp mic xe ồn. NHƯNG repo HF **có cổng** (HTTP 401 GatedRepo 2026-09-14) ⇒ URL trực tiếp KHÔNG tải được;
 *    phải **mirror** lên kênh OTA của dự án trước. sha256 để `""` = **[CHƯA BIẾT]**, [VoiceModelStore] từ chối
 *    tải khi chưa ghim (fail-safe), tới khi owner mirror + ghim.
 *  • Engine sherpa-onnx (AAR) = Apache-2.0 — an toàn.
 *  • KHÔNG dùng `hynt/Zipformer-30M` (CC-BY-NC-ND-4.0, cấm phái sinh + phi thương mại).
 */
object SherpaModelCatalog {

    /**
     * Một tệp cần tải, ghim bằng sha256 + kích thước.
     *
     * @param name **đường dẫn TƯƠNG ĐỐI** của tệp trong thư mục gói sau khi lắp. Thường là một đoạn
     *   (`encoder.onnx`), nhưng từ pha 2 (gói ĐỌC) có thể **nhiều đoạn** ngăn bằng `/`
     *   (`espeak-ng-data/lang/aav/vi`) — gói Piper mang một cây thư mục, không phải một rổ tệp phẳng.
     *   ⚠ Tầng cài (`VoiceModelStore.requireSafe`) kiểm từng đoạn: không đoạn nào được rỗng/`.`/`..`, không
     *   `\`/`:`, không bắt đầu bằng `/` (CLAUDE.md §4.1 — user input → file path).
     * @param url  URL trần (HTTPS). `""` = chưa có nguồn tải ⇒ chỉ side-load được.
     * @param sha256 sha256 chữ thường; `""` = CHƯA GHIM ⇒ [VoiceModelStore] từ chối tải (fail-safe).
     * @param bytes kích thước byte; `0` = chưa ghim.
     */
    data class ModelFile(
        val name: String,
        val url: String,
        val sha256: String,
        val bytes: Long,
    ) {
        /** Tệp đã ghim đủ để tải an toàn chưa (cả sha256 lẫn kích thước). */
        val pinned: Boolean get() = sha256.isNotBlank() && bytes > 0L
    }

    /**
     * Một mô hình sherpa-onnx offline transducer (Zipformer).
     *
     * @param id nhãn ổn định (tên thư mục con của `filesDir/sherpa/<id>`), cũng là khoá lưu lựa chọn.
     * @param label tên hiện trong Cài đặt.
     * @param license SPDX rút gọn — hiện trong Cài đặt + CREDITS.
     * @param files các tệp thành phần; [encoder]/[decoder]/[joiner]/[tokens] phải trỏ đúng tên tệp trong [files].
     * @param bpeVocab tên tệp BPE piece+score cho biasing. ⚠ KHÔNG phải `bpe.model` (sentencepiece nhị phân —
     *   sherpa từ chối); là bảng **piece score** xuất lúc build, đóng theo APK làm asset `voice/<id>.bpe_vocab.txt`.
     * @param decodingMethod "modified_beam_search" (cần cho hotwords biasing) hoặc "greedy_search".
     */
    data class SherpaModel(
        override val id: String,
        override val label: String,
        val license: String,
        override val files: List<ModelFile>,
        val encoder: String,
        val decoder: String,
        val joiner: String,
        val tokens: String,
        val bpeVocab: String,
        val decodingMethod: String = "modified_beam_search",
    ) : VoicePack {
        /** Thư mục con của `filesDir` chứa mô hình. */
        override val dir: String get() = "$DIR_ROOT/$id"

        /** Mọi tệp đã ghim sha256+size ⇒ được phép tải. Mô hình chưa mirror ([HATAPHU_VI]) trả `false`. */
        override val downloadable: Boolean get() = files.isNotEmpty() && files.all { it.pinned }

        /** Tổng byte phải tải — nói trước cho người dùng cần bao nhiêu chỗ + bao nhiêu 4G. */
        override val totalBytes: Long get() = files.sumOf { it.bytes }

        /** Tìm một tệp theo tên. */
        fun file(name: String): ModelFile? = files.firstOrNull { it.name == name }
    }

    /** Gốc thư mục cho mọi mô hình sherpa (song song `vosk/` của [VoiceModelManifest]). */
    const val DIR_ROOT = "sherpa"

    /**
     * A — Zipformer-vi 70k giờ (Apache-2.0). **Mặc định**: tải được ngay, đã proo off-car 87% intent với biasing
     * ([ĐO] `docs/diagnostics/vn-stt-sherpa-emulator-eval-2026-09-14.md`).
     *
     * ⚠ Bản này là **fp32** (encoder 249 MB) ⇒ tải ~266 MB. Lượng hoá int8 là hạng mục mở (spec §Open Questions).
     * sha256 [ĐO] 2026-09-14 (`shasum -a 256` trên máy soạn thảo).
     */
    val ZIPFORMER_VI = SherpaModel(
        id = "zipformer-vi-2025-04-20",
        label = "Zipformer VN (Apache-2.0, 70k h)",
        license = "Apache-2.0",
        files = listOf(
            ModelFile(
                "encoder.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20/resolve/main/encoder-epoch-12-avg-8.onnx",
                "d56645616305ceee63a1fa63a4da32e688130e937e67b11f69adf79712377717",
                261_057_692L,
            ),
            ModelFile(
                "decoder.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20/resolve/main/decoder-epoch-12-avg-8.onnx",
                "d1d27cca84c824a8acf5ce6edf0f2c0880cfe295d2e69b95134de1707e1d9998",
                5_165_084L,
            ),
            ModelFile(
                "joiner.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20/resolve/main/joiner-epoch-12-avg-8.onnx",
                "a186d4ddf04cac3ddfb095dc6e7f705dcd08bd79d4c67334f43c3a7337bf8d9a",
                4_104_465L,
            ),
            ModelFile(
                "tokens.txt",
                "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-2025-04-20/resolve/main/tokens.txt",
                "f536d03c2e95ebd2930cf0abec88e823bd17d3c1933da7ae6a82db3b80605e15",
                25_847L,
            ),
        ),
        encoder = "encoder.onnx",
        decoder = "decoder.onnx",
        joiner = "joiner.onnx",
        tokens = "tokens.txt",
        // BPE piece+score xuất từ bpe.model (sentencepiece) lúc build — đóng theo APK làm asset (~55 KB), không tải.
        bpeVocab = "zipformer-vi-2025-04-20.bpe_vocab.txt",
    )

    /**
     * B — hataphu Zipformer telephony (MIT). Owner ưu tiên (WER 5.73% VLSP2020, fine-tune telephony ⇒ hợp mic xe).
     *
     * ⚠ Repo HF **có cổng** ([ĐO] 401 GatedRepo 2026-09-14) ⇒ URL để rỗng, sha256 rỗng ⇒ [SherpaModel.downloadable]
     * = `false`, [VoiceModelStore] không tải cho tới khi owner mirror lên kênh OTA (`byd-kachi`) rồi ghim sha256.
     */
    val HATAPHU_VI = SherpaModel(
        id = "zipformer-hataphu-vi",
        label = "Zipformer VN telephony (MIT, hataphu)",
        license = "MIT",
        files = listOf(
            ModelFile("encoder.int8.onnx", "", "", 0L),
            ModelFile("decoder.int8.onnx", "", "", 0L),
            ModelFile("joiner.int8.onnx", "", "", 0L),
            ModelFile("tokens.txt", "", "", 0L),
        ),
        encoder = "encoder.int8.onnx",
        decoder = "decoder.int8.onnx",
        joiner = "joiner.int8.onnx",
        tokens = "tokens.txt",
        bpeVocab = "zipformer-hataphu-vi.bpe_vocab.txt",
    )

    /**
     * A-int8 — **cùng mô hình [ZIPFORMER_VI], bản lượng hoá int8** (Apache-2.0, cùng tác giả, cùng tokens).
     *
     * ## Vì sao nó thành MẶC ĐỊNH của máy cài mới (V3 · R6)
     * [ĐO xe 2026-09-16] `docs/diagnostics/oncar-trace-2026-09-16.md` §1: Kachi RSS **537 MB** (native heap 477 MB
     * = encoder fp32), máy chỉ còn **56–94 MB** trống, và lần bật mic đầu mất **15 s** để nạp. [ĐO host] 25 tệp
     * WAV: int8 ra **21/25 đúng ý định = y hệt fp32** ⇒ không đánh đổi độ chính xác nào đo được. Encoder 249 MB →
     * **67,6 MB** (tổng tải 266 MB → **74 MB**).
     *
     * ⚠ **[CHƯA BIẾT]** tốc độ giải mã int8 trên ARM của đầu xe: int8 nhỏ hơn nhưng vài nhân onnxruntime chạy
     * quantize/dequantize chậm hơn fp32 trên CPU không có dot-product 8-bit. Phép chốt nằm ở playbook §6g (một
     * lượt `wav` cùng tệp trên cả hai mô hình, đọc mốc `sherpa ra:` trong logcat).
     *
     * ⚠ Máy **đã** cài fp32 thì GIỮ NGUYÊN fp32 cho tới khi owner tự chọn — xem `VoiceModelStore.selected`. Đổi
     * mặc định mà kéo theo một lượt tải 74 MB qua 4G trên xe đang chạy là một quyết định thay người dùng.
     *
     * sha256 + kích thước [ĐO] 2026-09-16 (HF API `resolve/main` + `shasum -a 256` trên máy soạn thảo).
     */
    val ZIPFORMER_VI_INT8 = SherpaModel(
        id = "zipformer-vi-int8-2025-04-20",
        label = "Zipformer VN int8 (Apache-2.0, 74 MB)",
        license = "Apache-2.0",
        files = listOf(
            ModelFile(
                "encoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-int8-2025-04-20/resolve/main/" +
                    "encoder-epoch-12-avg-8.int8.onnx",
                "b3abdef7a660fea7faf5e076b3c7613b0fc98406707103784d018189bb522124",
                70_876_129L,
            ),
            ModelFile(
                "decoder.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-int8-2025-04-20/resolve/main/" +
                    "decoder-epoch-12-avg-8.onnx",
                "d1d27cca84c824a8acf5ce6edf0f2c0880cfe295d2e69b95134de1707e1d9998",
                5_165_084L,
            ),
            ModelFile(
                "joiner.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-int8-2025-04-20/resolve/main/" +
                    "joiner-epoch-12-avg-8.int8.onnx",
                "38ec49e1c18e4feb0cad4de13e25c83a866cf56f4a66f22e8ff579d591a69a46",
                1_033_417L,
            ),
            ModelFile(
                "tokens.txt",
                "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-vi-int8-2025-04-20/resolve/main/tokens.txt",
                "f536d03c2e95ebd2930cf0abec88e823bd17d3c1933da7ae6a82db3b80605e15",
                25_847L,
            ),
        ),
        encoder = "encoder.int8.onnx",
        decoder = "decoder.onnx",
        joiner = "joiner.int8.onnx",
        tokens = "tokens.txt",
        // ⚠ **CÙNG** asset BPE với [ZIPFORMER_VI]: hai mô hình là một bản huấn luyện, `tokens.txt` giống hệt tới
        // từng byte (cùng sha256 ở trên). Đóng thêm một bản 55 KB thứ hai vào APK là dựng bản sao thứ hai của
        // một bảng — đúng bẫy hai-bản-sao. Vì thế [VoiceEngine] tra asset theo [SherpaModel.bpeVocab], KHÔNG
        // theo [SherpaModel.id].
        bpeVocab = "zipformer-vi-2025-04-20.bpe_vocab.txt",
    )

    /** Mọi mô hình, thứ tự hiện trong Cài đặt — bản mặc định đứng đầu. */
    val ALL: List<SherpaModel> = listOf(ZIPFORMER_VI_INT8, ZIPFORMER_VI, HATAPHU_VI)

    /**
     * Mô hình mặc định cho **máy cài mới** — V3 · R6 đổi fp32 → int8 (owner **D4** 2026-09-16: *"thử int8 và mọi
     * cách tới khi ngon"*). Lý do + số đo ở KDoc [ZIPFORMER_VI_INT8].
     */
    const val DEFAULT_ID = "zipformer-vi-int8-2025-04-20"

    /** Mô hình mặc định (đối tượng). Một chỗ trả lời *"mặc định là cái nào"* — [byId] cũng lùi về đây. */
    fun default(): SherpaModel = ALL.first { it.id == DEFAULT_ID }

    /**
     * Tra mô hình theo id, hoặc **mặc định** nếu id lạ / rỗng (fail-safe cho pref cũ).
     *
     * ⚠ Trước 1.66 hàm này lùi về [ZIPFORMER_VI] viết cứng, tức [DEFAULT_ID] là một hằng **không ai đọc** — đổi
     * nó không đổi hành vi một dòng nào. Nay hai thứ là một.
     */
    fun byId(id: String?): SherpaModel = ALL.firstOrNull { it.id == id } ?: default()

    /**
     * Điểm biasing hotwords — [ĐO] off-car: score 3.0 sửa được `pin`/`tắt`/`âm lượng` mà KHÔNG chèn nhầm lệnh vào
     * câu tự do ("hôm nay trời đẹp quá" giữ nguyên). Xem evidence doc.
     */
    const val HOTWORDS_SCORE = 3.0f

    /** Đơn vị mô hình cho hotwords — mô hình VN của sherpa là BPE (sentencepiece, 2000 token). */
    const val MODELING_UNIT = "bpe"
}
