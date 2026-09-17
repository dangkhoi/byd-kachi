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
        /**
         * Ghi công tác giả — **bắt buộc** với gói mang giấy phép họ BY (CC BY-*), tuỳ chọn với gói khác.
         *
         * Một trường dữ liệu chứ không phải một dòng chữ rải trong Cài đặt: cùng câu ấy phải xuất hiện ở **ba**
         * chỗ (màn Cài đặt · `state.voice_model` · `voice/README.md`), và ba bản chép tay là ba bản sẽ lệch —
         * lúc đó nghĩa vụ "ghi công" chỉ còn đúng ở chỗ không ai đọc.
         */
        val attribution: String = "",
        /** URL nguồn để người dùng tự kiểm — đi kèm [attribution]. */
        val sourceUrl: String = "",
        /**
         * Gói **THỬ NGHIỆM** — có mặt trong danh mục nhưng KHÔNG được tự đề nghị cho người dùng.
         *
         * ## Vì sao cần một cờ, thay vì xoá gói đi
         * [ĐO 2026-09-16, giọng THẬT của owner — 30 câu × 3 lượt, giải mã trên host]: gói đang ship
         * (`zipformer-vi` fp32 + hotword) đúng **≈ 28/30** ở lượt nói thường, còn `vi-30M-int8` sai **~7 câu**
         * (*"xem pin"* → *"xem binh"* · *"bật ghế sưởi"* → *"bọc ghế sửi"* · *"mở quây"* → *"mở quay"* ·
         * *"lọc bụi mịn"* → *"bộ mệnh"* · *"nhiệt độ"* → *"cuộc chiến nhiệt độ"* khi có ồn). Tức **corpus TTS đã
         * đánh lừa**: nó chấm 30M cao hơn +1,8 điểm, còn giọng người thì ngược lại — đúng bài học §0 của
         * `voice-stream-eval-2026-09-16.md` về việc giọng tổng hợp không thay được giọng thật.
         *
         * Xoá hẳn thì lần sau ai đó lại phải tải + ghim + đo lại từ đầu; giữ mà không gắn cờ thì [lighterThan]
         * sẽ **tự đề nghị** nó (34 MB < 74 MB) và người dùng nhận một gói nghe kém hơn kèm chữ *"nhẹ hơn"*.
         * Cờ này giữ được cả hai: dữ liệu còn đó, mà không ai bị đẩy vào nó.
         */
        val experimental: Boolean = false,
    ) : VoicePack {
        /** Thư mục con của `filesDir` chứa mô hình. */
        override val dir: String get() = "$DIR_ROOT/$id"

        /** Mọi tệp đã ghim sha256+size ⇒ được phép tải. Mô hình chưa mirror ([HATAPHU_VI]) trả `false`. */
        override val downloadable: Boolean get() = files.isNotEmpty() && files.all { it.pinned }

        /** Tổng byte phải tải — nói trước cho người dùng cần bao nhiêu chỗ + bao nhiêu 4G. */
        override val totalBytes: Long get() = files.sumOf { it.bytes }

        /** Tìm một tệp theo tên. */
        fun file(name: String): ModelFile? = files.firstOrNull { it.name == name }

        /**
         * Gói lượng hoá int8 (encoder `*.int8.onnx`) — 1.70: `VoiceModelStore.selected` ưu tiên gói này khi
         * người dùng chưa chọn mà cả hai bản cùng nằm trên đĩa ([ĐO xe 2026-09-17]: xe owner giải mã fp32 dù
         * int8 đã tải xong; owner đã chốt int8 từ 09-16).
         */
        val isInt8: Boolean get() = encoder.contains(".int8.")
    }

    /** Gốc thư mục cho mọi mô hình sherpa (song song `vosk/` của [VoiceModelManifest]). */
    const val DIR_ROOT = "sherpa"

    /** Gốc URL của gói 30M — một chỗ khai, bốn tệp nối tên vào (chép lại bốn lần là bốn chỗ gõ sai được). */
    private const val HF_30M =
        "https://huggingface.co/csukuangfj2/sherpa-onnx-zipformer-vi-30M-int8-2026-02-09/resolve/main/"

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

    /**
     * ═══ C — Zipformer **30M int8** (`hynt/Zipformer-30M-RNNT-6000h`) — MẶC ĐỊNH từ 1.69 ════════════════════
     *
     * ## ⚠⚠ GIẤY PHÉP: **CC BY-NC-ND 4.0** — và vì sao nó ở đây dù spec từng LOẠI nó
     * `kachi-voice-engine-v2.html` (2026-09-14) loại `hynt/Zipformer-30M` đúng vì giấy phép này. Ngày
     * **2026-09-16 owner quyết định ngược lại** cho dự án của mình: *"phi lợi nhuận, vui vẻ với anh em nên cũng
     * ko quan trọng lắm về license đâu nhỉ"*. Đây là **quyết định của owner**, không phải của agent — đúng chỗ
     * `voice-stream-eval-2026-09-16.md` §9 đã ghi là ranh giới (*"Quyết định dùng hay không là của owner"*).
     * Ghi lại nguyên văn để lượt sau không tưởng ai đó lặng lẽ bỏ qua một ràng buộc đã chốt.
     *
     * Ba nghĩa vụ của giấy phép ấy, và cách bản này giữ:
     *  • **BY (ghi công)** — tên tác giả + giấy phép + URL hiện ở *Cài đặt › Giọng nói › Về mô hình nghe*, ở
     *    lời đáp `state.voice_model` của cầu kiểm thử, và ở `voice/README.md`. Xem [attribution].
     *  • **NC (phi thương mại)** — điều kiện owner tự khẳng định cho dự án này.
     *  • **ND (cấm phái sinh)** — **KHÔNG fine-tune, KHÔNG sửa** mô hình ở bất kỳ đâu; ship đúng bộ tệp int8 đã
     *    công bố. ⚠ Việc dự án sinh `*.bpe_vocab.txt` **từ** `bpe.model` là một phép **đổi định dạng cho runtime**
     *    (sherpa không nhận `bpe.model` nhị phân — xem [VoiceEngine.copyBpeVocabAsset]), không đụng tới trọng số.
     *    Đó là **cách dự án hiểu** điều khoản ND, **không phải một kết luận pháp lý** — nếu cần chắc chắn thì hỏi
     *    tác giả, và đó là việc của owner.
     *  • Tệp mô hình **không đóng trong APK**: xe tải thẳng từ HF khi người dùng bấm, nên bản thân APK không phát
     *    tán lại trọng số.
     *
     * ## Vì sao nó thành mặc định — [ĐO host] `voice-stream-eval-2026-09-16.md`
     * WER tiếng Việt tốt hơn hẳn bản đang ship (model card: VLSP2025-PublicTest **7.97** · GigaSpeech2-Test
     * **7.56**, ~6 000 giờ, tác giả nói nhất VLSP 2025), mà **tổng chỉ ~34 MB** — nhẹ hơn cả bản int8 74 MB của
     * gói A. Tức nó thắng ở cả hai trục cùng lúc, chuyện hiếm.
     *
     * ⚠ Máy **đã** cài mô hình khác thì GIỮ NGUYÊN cho tới khi người dùng tự chọn ([VoiceModelStore.selected]) —
     * cùng luật đã dựng cho lượt đổi fp32 → int8 ở 1.66.
     *
     * sha256 + kích thước **[ĐO] 2026-09-16** (tải thật + `shasum -a 256`).
     */
    val ZIPFORMER_VI_30M_INT8 = SherpaModel(
        id = "zipformer-vi-30M-int8-2026-02-09",
        label = "Zipformer VN 30M int8 — THỬ NGHIỆM, nghe kém hơn trên giọng thật (CC BY-NC-ND, 34 MB)",
        license = "CC-BY-NC-ND-4.0",
        files = listOf(
            ModelFile(
                "encoder.int8.onnx",
                HF_30M + "encoder.int8.onnx",
                "8ef5286dd427eb108055c2ddc1982aa31e544706072d5ea228729292dacade68",
                27_699_063L,
            ),
            ModelFile(
                "decoder.onnx",
                HF_30M + "decoder.onnx",
                "cf2aa385b82c9d5d40cd29c3188af52d0249b3b78f0d4b7eb84ad502d50c7e7f",
                5_165_084L,
            ),
            ModelFile(
                "joiner.int8.onnx",
                HF_30M + "joiner.int8.onnx",
                "7311d2e17b810ecea515d79c71cc4668af8759256a06fa01d27047772320c821",
                1_033_417L,
            ),
            ModelFile(
                "tokens.txt",
                HF_30M + "tokens.txt",
                "ca8171f8bbd516c050b627582f2125c8f5f1f6ed967ab41b0fa9aae2cf61b492",
                23_238L,
            ),
        ),
        encoder = "encoder.int8.onnx",
        decoder = "decoder.onnx",
        joiner = "joiner.int8.onnx",
        tokens = "tokens.txt",
        // ⚠⚠ BẢNG BPE **RIÊNG** — đây là cái bẫy im lặng nhất của lượt thêm mô hình này.
        // [ĐO 2026-09-16] sinh bảng piece+score từ `bpe.model` của mô hình này (sentencepiece 0.2.2) rồi diff với
        // bảng đang ship: cả hai đúng 2 000 mảnh, mà **1 997/2 000 dòng KHÁC nhau**. Dùng lại bảng cũ thì mọi cụm
        // hotword bị mã hoá sai và sherpa **lặng lẽ bỏ** chúng ("Failed to encode some hotwords") — biasing trông
        // như đang bật mà không làm gì. Tệp hotword (CHỮ HOA có dấu) thì dùng chung được; **bảng BPE thì không**.
        bpeVocab = "zipformer-vi-30M-int8-2026-02-09.bpe_vocab.txt",
        attribution = "hynt — Zipformer-30M-RNNT-6000h (CC BY-NC-ND 4.0), gói sherpa bởi csukuangfj2",
        sourceUrl = "https://huggingface.co/hynt/Zipformer-30M-RNNT-6000h",
        // ⚠ THỬ NGHIỆM — [ĐO giọng thật owner 2026-09-16] nghe KÉM hơn gói đang ship (~7 câu sai/30 so với
        // ≈2). Xem KDoc [SherpaModel.experimental] và lịch sử ở [DEFAULT_ID]. Cờ này là thứ duy nhất giữ nó
        // ra khỏi hàng *"chuyển sang mô hình nhẹ"* — bỏ cờ là người dùng bị đẩy sang nó vì nó nhẹ nhất.
        experimental = true,
    )

    /** Gốc URL gói fine-tune G trên kênh OTA (`dangkhoi/byd-kachi`) — owner mirror như gói TTS. */
    private const val GIP_FT = "https://raw.githubusercontent.com/dangkhoi/byd-kachi/main/voice/asr/gipformer-vi-ft-ep2/"

    /**
     * ═══ G — Gipformer 65M fine-tune trên GIỌNG THẬT (MIT phái sinh) — THỬ NGHIỆM, ứng viên mặc-định-trên-xe ═══
     *
     * Spec `kachi-voice-finetune.html` · số `voice-ft-2026-09-16.md` · benchmark `voice-bench-ship-vs-ft-2026-09-17.md`.
     *
     * **Đáng ship kèm** — [ĐO host, bộ giữ RIÊNG giọng thật 270 câu]: ship `zipformer-vi` int8 74,8 % · base
     * `gipformer1.5-65M` (MIT, cùng tokenizer) · **G = fine-tune 2 epoch / 3,09 h giọng thật = 84,8 %** (+27
     * câu, lãi đúng chỗ thủng: nói NHANH + giọng TRẺ EM). Gói int8 **78,2 MB**, không đụng hotword/synonym/parser.
     *
     * **experimental, KHÔNG mặc định** (owner chốt 2026-09-17): +10 điểm đo trên host giọng thu SẴN, không phải
     * mic 4 kênh + ồn cabin. Corpus/host đã đánh lừa 2 lần (xem [ZIPFORMER_VI_30M_INT8]) ⇒ ship CẢ HAI, cờ
     * [experimental] + nút chọn; quyết mặc định = trên xe. VFT-2 🚗: RTF ước 0,35 (sát trần 0,30).
     *
     * **Giấy phép**: MIT phái sinh (base `gipformer1.5-65M` MIT cho phép phái sinh — khác họ ND của gói 30M).
     * **bpe_vocab RIÊNG** (cùng bẫy gói 30M): tokens.txt giống base nhưng bpe_vocab là bảng piece+score riêng
     * (55 006 B) ⇒ asset riêng `voice/gipformer-vi-ft-ep2.bpe_vocab.txt`.
     *
     * sha256 + cỡ **[ĐO] 2026-09-17** (`~/.kachi/model-gip15-ep2/`, cứu từ scratchpad). ⚠ `downloadable=false`
     * tới khi owner mirror lên `byd-kachi` (URL đã trỏ sẵn; side-load USB chạy ngay).
     */
    val GIPFORMER_VI_FT = SherpaModel(
        id = "gipformer-vi-ft-ep2",
        label = "Gipformer VN fine-tune giọng thật — THỬ NGHIỆM, nghe nhanh/trẻ em tốt hơn (MIT, 78 MB)",
        license = "MIT",
        files = listOf(
            ModelFile("encoder.int8.onnx", GIP_FT + "encoder.int8.onnx", "4003cf76645107c0c5652334a4e4ff26b44d7e5abc2d4e558e0f158ccab40de3", 72_828_389L),
            ModelFile("decoder.int8.onnx", GIP_FT + "decoder.int8.onnx", "a5742079e09807b08ce12a694623c3a2028eead3f60117f109ef1bc644b8bf6f", 4_380_352L),
            ModelFile("joiner.int8.onnx", GIP_FT + "joiner.int8.onnx", "19a251d44b6c87b83dede0010f50cc3dabd2707dcf4d5c85b3496ce84cbc4144", 1_033_417L),
            ModelFile("tokens.txt", GIP_FT + "tokens.txt", "f536d03c2e95ebd2930cf0abec88e823bd17d3c1933da7ae6a82db3b80605e15", 25_847L),
        ),
        encoder = "encoder.int8.onnx",
        decoder = "decoder.int8.onnx",
        joiner = "joiner.int8.onnx",
        tokens = "tokens.txt",
        bpeVocab = "gipformer-vi-ft-ep2.bpe_vocab.txt",
        attribution = "Fine-tune (dự án Kachi) trên gipformer1.5-65M-rnnt (MIT) · 3,09 h giọng thật",
        sourceUrl = "https://huggingface.co/hynt/gipformer1.5-65M-rnnt",
        experimental = true,
    )

    /** Mọi mô hình, thứ tự hiện trong Cài đặt — bản mặc định đứng đầu. */
    val ALL: List<SherpaModel> = listOf(ZIPFORMER_VI_INT8, GIPFORMER_VI_FT, ZIPFORMER_VI_30M_INT8, ZIPFORMER_VI, HATAPHU_VI)

    /**
     * Mô hình mặc định cho **máy cài mới**.
     *
     * Lịch sử hai lượt đổi, cùng ngày 2026-09-16 — ghi cả hai để không ai tưởng lượt sau xoá dấu lượt trước:
     *  • **1.66** fp32 → int8 (owner **D4**: *"thử int8 và mọi cách tới khi ngon"*) — lý do ở [ZIPFORMER_VI_INT8].
     *  • **1.69 (lượt đầu)** int8 → 30M int8 sau khi owner chốt về giấy phép — chấm bằng **corpus TTS**.
     *  • **1.69 (ĐẢO LẠI, cùng ngày)** → **quay về `zipformer-vi-int8-2025-04-20`**. [ĐO giọng THẬT của owner,
     *    30 câu × 3 lượt, giải mã trên host]: gói đang ship đúng **≈ 28/30** ở lượt nói thường, còn 30M sai
     *    **~7 câu** (*"xem pin"* → *"xem binh"* · *"bật ghế sưởi"* → *"bọc ghế sửi"* · *"mở quây"* → *"mở quay"* ·
     *    *"lọc bụi mịn"* → *"bộ mệnh"* · *"nhiệt độ"* → *"cuộc chiến nhiệt độ"* khi có ồn). Corpus TTS chấm 30M
     *    **cao hơn +1,8 điểm** — tức nó **đã đánh lừa**, và đây là lần thứ hai trong cùng một ngày giọng tổng hợp
     *    nói ngược với giọng người (xem §0 `voice-stream-eval-2026-09-16.md`). Gói 30M ở lại danh mục với cờ
     *    [SherpaModel.experimental] để lần sau khỏi tải + ghim + đo lại từ đầu, nhưng **không ai bị đẩy vào nó**.
     *
     * ⚠ Chỉ áp cho **máy cài mới**: máy đã có mô hình thì [VoiceModelStore.selected] giữ nguyên bản cũ tới khi
     * người dùng tự chạm hàng *"Chuyển sang mô hình nhẹ"*.
     */
    const val DEFAULT_ID = "zipformer-vi-int8-2025-04-20"

    /**
     * Mô hình có đòi **ghi công** không — tức giấy phép của nó thuộc họ `BY`.
     *
     * Suy từ chính chuỗi giấy phép, không phải một danh sách tên gói viết tay: thêm một gói CC BY nữa vào [ALL]
     * là hàng ghi công tự hiện, không phải nhớ sửa thêm chỗ nào (CLAUDE.md §7).
     */
    fun requiresAttribution(model: SherpaModel): Boolean =
        model.license.contains("BY", ignoreCase = true) && model.attribution.isNotBlank()

    /** Mọi gói đang đòi ghi công — nguồn DUY NHẤT cho cả ba chỗ hiển thị. */
    fun attributions(): List<SherpaModel> = ALL.filter { requiresAttribution(it) }

    /** Mô hình mặc định (đối tượng). Một chỗ trả lời *"mặc định là cái nào"* — [byId] cũng lùi về đây. */
    fun default(): SherpaModel = pickDefault(ALL, DEFAULT_ID)

    /**
     * [SOÁT 2026-09-16 · P3] Chọn mặc định **fail-safe**, cùng lối lùi với [byId].
     *
     * Bản trước là `ALL.first { it.id == DEFAULT_ID }` — `first{}` **ném** `NoSuchElementException` khi không
     * khớp, trong khi [byId] ngay dưới lại `firstOrNull{} ?: default()`. Hai hàm cạnh nhau, cùng một câu hỏi,
     * hai hành vi hỏng khác nhau. Hậu quả không nằm ở hôm nay (id đang khớp) mà ở ngày [DEFAULT_ID] được sửa —
     * mà nó **đang được sửa trong chính nhánh này** (xem lịch sử 1.66→1.69 ở KDoc hằng): một ký tự gõ nhầm biến
     * thành **crash ngay lượt mở voice đầu tiên, trên xe**, ở một đường mà mọi nhánh khác đều đã chọn degrade.
     *
     * Nhận [all] + [id] làm tham số chứ không đọc thẳng hằng: đó là cách duy nhất kiểm được ca "id lạ" off-car
     * ([DEFAULT_ID] là `const`, không thể đặt sai trong bài kiểm). `all.first()` ở vế lùi vẫn đòi danh mục
     * KHÔNG rỗng — một danh mục rỗng là lỗi lập trình, không phải trạng thái vận hành, và `SherpaModelCatalogTest`
     * khoá lại điều đó.
     */
    internal fun pickDefault(all: List<SherpaModel>, id: String): SherpaModel =
        all.firstOrNull { it.id == id } ?: all.first()

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

    /**
     * H5 — dải cho phép của núm `voice_hotword_score`. Mặc định vẫn là [HOTWORDS_SCORE] ⇒ **không đổi hành vi**.
     *
     * Dưới 2.0 thì biasing gần như không kéo được `pin`/`tắt`/`âm lượng` về (đúng thứ [ĐO] off-car đo được là
     * score 3.0 mới sửa); trên 4.0 thì nó bắt đầu **chèn lệnh vào câu tự do** — *"hôm nay trời đẹp quá"* ra một
     * cụm lệnh không ai nói. Hai đầu dải là hai kiểu hỏng khác nhau, nên cả hai đều phải có trần.
     */
    const val MIN_HOTWORDS_SCORE = 2.0f
    const val MAX_HOTWORDS_SCORE = 4.0f

    /**
     * Bề rộng chùm của `modified_beam_search` (`OfflineRecognizerConfig.maxActivePaths`).
     *
     * ⚠ Trước H5 con số này là một literal `4` nằm trong `VoiceEngine.build` — tức **danh mục khai mọi tham số
     * giải mã trừ đúng một cái**, và `scripts/voice/hotword-matrix.py` (chạy cùng tham số trên host) phải chép
     * lại bằng tay. Nay một chỗ khai, hai bên đọc.
     */
    const val MAX_ACTIVE_PATHS = 4

    /**
     * H5 — hai giá trị được phép cho núm `voice_beam`. Chỉ hai, không phải một dải: beam là **chi phí nhân lên**
     * ở mỗi khung giải mã, và [ĐO xe 2026-09-16] một câu 8 s đã mất 2,35 s để giải mã với beam 4. Cho một dải
     * liên tục là mời người đo gõ `16` trên một chiếc xe đang chạy rồi kết luận nhầm rằng mô hình chậm.
     */
    val BEAM_CHOICES: Set<Int> = setOf(MAX_ACTIVE_PATHS, 8)

    /** Đơn vị mô hình cho hotwords — mô hình VN của sherpa là BPE (sentencepiece, 2000 token). */
    const val MODELING_UNIT = "bpe"

    /**
     * H6 — bản **nhẹ hơn** của gói đang cài, hoặc `null` khi không có bản nào nhẹ hơn (đã là bản nhẹ nhất, hoặc
     * gói kia chưa mirror ⇒ chưa tải được).
     *
     * Đặt ở danh mục chứ không ở tầng vẽ: câu hỏi *"có bản nào nhẹ hơn không"* là câu hỏi về **dữ liệu của danh
     * mục** (cỡ tệp + đã ghim chưa), và trả lời nó bằng một `if (id == "…int8…")` trong Cài đặt là viết cứng tên
     * một mô hình vào tầng vẽ — đúng thứ CLAUDE.md §7 cấm. Thêm một gói nhẹ hơn nữa vào [ALL] là hàng Cài đặt tự
     * đề nghị nó, không phải sửa thêm chỗ nào.
     */
    fun lighterThan(current: SherpaModel): SherpaModel? =
        ALL.filter { it.id != current.id && it.downloadable && !it.experimental && it.totalBytes in 1 until current.totalBytes }
            .minByOrNull { it.totalBytes }
}
