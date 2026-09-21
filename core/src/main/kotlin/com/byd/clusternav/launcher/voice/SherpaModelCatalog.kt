package com.byd.clusternav.launcher.voice

/**
 * ═══ MÔ HÌNH NGHE sherpa-onnx — **ĐÚNG MỘT GÓI**, khai MỘT CHỖ, ghim từng tệp bằng số đo thật ════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html`. Thuần Kotlin (`:core`) ⇒ mọi luật kiểm off-car; phần chạm
 * đĩa/mạng nằm ở `:app` ([VoiceModelStore]).
 *
 * ## Vì sao bảng NHIỀU tệp (giữ từ V2, không đổi)
 * Mô hình Vosk là **một** gói zip (mở ra thành cây thư mục Kaldi). Mô hình sherpa-onnx là **rời từng tệp ONNX**
 * (encoder/decoder/joiner + tokens + bpe). Không có gói nén chuẩn để ghim một sha duy nhất, nên mỗi tệp phải tự
 * mang sha256 + kích thước — tải cụt hay proxy chèn trang lỗi bị bắt **từng tệp một**.
 *
 * ## ⚠ BẢN RELEASE PRODUCTION (owner 2026-09-21) — danh mục thu về **một** gói, bề mặt chọn mô hình GỠ HẲN
 * Tới 1.87 danh mục giữ **năm** gói để A/B trên xe (fp32 · int8 · hataphu gated · 30M · fine-tune G) và màn Cài
 * đặt có hai nút *"chuyển sang mô hình nhẹ"* / *"gỡ bản nặng"*. Owner chốt dừng thử nghiệm: **chỉ giữ gói đang
 * chạy tốt trên xe** = [ZIPFORMER_VI_INT8] ([DEFAULT_ID]), bốn gói kia xoá khỏi mã.
 *
 * Bốn gói ấy **không bị đánh giá lại** ở lượt này — chúng bị bỏ vì không còn ai thử nghiệm, và số đo của chúng
 * vẫn đọc được ở nơi số đo thuộc về:
 *  • `zipformer-vi-2025-04-20` (fp32 266 MB, Apache-2.0) — [ĐO xe 2026-09-17] nặng gấp 3,6× mà cùng kết quả
 *    giải mã với bản int8 ⇒ int8 đã thay nó từ 1.66; `docs/diagnostics/oncar-trace-2026-09-16.md` §1.
 *  • `zipformer-hataphu-vi` (MIT, repo HF **có cổng**) — chưa bao giờ tải được (sha256 chưa ghim).
 *  • `zipformer-vi-30M-int8-2026-02-09` (CC BY-NC-ND) — [ĐO giọng THẬT owner 2026-09-16] sai **~7/30** câu so với
 *    ≈2/30 của gói đang ship; `docs/diagnostics/voice-stream-eval-2026-09-16.md`.
 *  • `gipformer-vi-ft-ep2` (fine-tune, MIT) — +10 điểm trên **host**, chưa bao giờ đo trên mic cabin;
 *    `docs/diagnostics/voice-bench-ship-vs-ft-2026-09-17.md`. sha256 + URL còn trong git history nếu cần dựng lại.
 *
 * Hệ quả cần biết, KHÔNG phải tác dụng phụ ngoài ý: xe nào từng bấm *"chuyển sang mô hình nhẹ"* và đang trỏ pref
 * vào một id nay không còn thì [byId] **lùi về gói duy nhất** (fail-safe, không ném) — xem `VoiceModelStore.selected`.
 *
 * ## Giấy phép gói đang ship (đọc LICENSE thật 2026-09-14 — CLAUDE.md §1.1)
 * Nguồn `zzasdf/viet_iter3_pseudo_label` = **Apache-2.0** ([ĐO] HF metadata); gói sherpa
 * `csukuangfj/sherpa-onnx-zipformer-vi-int8-2025-04-20` tải trực tiếp (không cổng). Engine sherpa-onnx (AAR) =
 * Apache-2.0. ⇒ bản phát hành hiện tại **không còn gói nào mang nghĩa vụ ghi công** (xem [attributions]).
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
         *
         * ⚠ Gói duy nhất đang ship là Apache-2.0 ⇒ trường này đang rỗng và [attributions] đang **trả danh sách
         * rỗng**. Cơ chế ở lại vì nghĩa vụ này là nghĩa vụ của **dữ liệu**: thêm một gói CC BY vào [ALL] là ba bề
         * mặt kia tự hiện lời ghi công, không phải nhớ dựng lại.
         */
        val attribution: String = "",
        /** URL nguồn để người dùng tự kiểm — đi kèm [attribution]. */
        val sourceUrl: String = "",
        /**
         * Gói **THỬ NGHIỆM** — có mặt trong danh mục nhưng KHÔNG được tự đề nghị / tự thành mặc định.
         *
         * ⚠ Sau lượt dọn 2026-09-21 **không gói nào** mang cờ này (danh mục chỉ còn một gói đã chứng minh trên
         * xe). Cờ + cổng đọc nó ([lighterThan]) vẫn ở lại vì đó là chỗ duy nhất chặn một gói chưa chứng minh
         * **lặng lẽ** được đem ra đề nghị ở lượt sau: bài học [ĐO 2026-09-16] là một gói nhẹ hơn + WER công bố tốt
         * hơn vẫn có thể nghe **kém hơn** trên giọng thật, và lúc đó thứ giữ người dùng lại chính là một cờ chứ
         * không phải một lời nhắc trong review.
         */
        val experimental: Boolean = false,
    ) : VoicePack {
        /** Thư mục con của `filesDir` chứa mô hình. */
        override val dir: String get() = "$DIR_ROOT/$id"

        /** Mọi tệp đã ghim sha256+size ⇒ được phép tải. Gói chưa ghim (chưa mirror) trả `false`. */
        override val downloadable: Boolean get() = files.isNotEmpty() && files.all { it.pinned }

        /** Tổng byte phải tải — nói trước cho người dùng cần bao nhiêu chỗ + bao nhiêu 4G. */
        override val totalBytes: Long get() = files.sumOf { it.bytes }

        /** Tìm một tệp theo tên. */
        fun file(name: String): ModelFile? = files.firstOrNull { it.name == name }

        // ⚠ `val isInt8` (encoder có `.int8.`) đã XOÁ 2026-09-21: chỗ đọc duy nhất là nhánh *"ưu tiên bản int8 đã
        // nằm trên đĩa"* của `VoiceModelStore.selected`, và nhánh ấy chỉ có nghĩa khi danh mục có **cả hai** bản
        // của cùng một bản huấn luyện. Giữ một thuộc tính không ai đọc kèm KDoc kể tên một chỗ gọi đã biến mất là
        // cách chắc chắn để người sau tin rằng lá chắn ấy còn sống.
    }

    /** Gốc thư mục cho mọi mô hình sherpa (song song `vosk/` của [VoiceModelManifest]). */
    const val DIR_ROOT = "sherpa"

    /**
     * ═══ Gói DUY NHẤT: Zipformer-vi 70k giờ, bản **int8** (Apache-2.0) ════════════════════════════════════════
     *
     * ## Vì sao int8 chứ không phải fp32 của cùng bản huấn luyện
     * [ĐO xe 2026-09-16] `docs/diagnostics/oncar-trace-2026-09-16.md` §1: với fp32, RSS của Kachi **537 MB**
     * (native heap 477 MB = encoder), máy chỉ còn **56–94 MB** trống, và lần bật mic đầu mất **15 s** để nạp.
     * [ĐO host] 25 tệp WAV: int8 ra **21/25 đúng ý định = y hệt fp32** ⇒ không đánh đổi độ chính xác nào đo được.
     * Encoder 249 MB → **67,6 MB** (tổng tải 266 MB → **74 MB**).
     *
     * [ĐO giọng THẬT owner 2026-09-16, 30 câu × 3 lượt] gói này đúng **≈ 28/30** ở lượt nói thường — nó là mốc mà
     * mọi gói ứng viên khác đã được đo **so với**, và là lý do nó là gói duy nhất ở lại.
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
        // ⚠ Tên asset mang id của bản **fp32** (`zipformer-vi-2025-04-20`) là CỐ Ý, không phải sót một lượt đổi
        // tên: hai bản là một bản huấn luyện, `tokens.txt` giống nhau tới từng byte, nên bảng BPE dùng chung.
        // [VoiceEngine] tra asset theo [SherpaModel.bpeVocab] (KHÔNG theo [SherpaModel.id]) chính vì thế; đổi tên
        // tệp asset ở đây là một lượt sửa **không cần thiết** mà làm sai đường tra của mọi script host đang trỏ
        // vào đúng tên đó (`scripts/voice/*.py`).
        bpeVocab = "zipformer-vi-2025-04-20.bpe_vocab.txt",
    )

    /**
     * Mọi mô hình NGHE — **đúng một** kể từ bản release production (owner 2026-09-21).
     *
     * Vẫn là một `List` chứ không phải một hằng đơn: [byId] · [lighterThan] · [attributions] ·
     * `VoiceModelStore.selected` đều hỏi *danh mục* chứ không hỏi tên một gói, nên thêm lại một gói nào đó chỉ là
     * thêm một phần tử vào đây (CLAUDE.md §7 — không viết cứng tên mô hình ở tầng nào khác).
     */
    val ALL: List<SherpaModel> = listOf(ZIPFORMER_VI_INT8)

    /**
     * Mô hình mặc định cho **máy cài mới**.
     *
     * Lịch sử các lượt đổi — ghi lại vì lượt sau rất dễ tưởng đây là một hằng chưa ai soát:
     *  • **1.66** fp32 → int8 (owner **D4**: *"thử int8 và mọi cách tới khi ngon"*) — lý do ở [ZIPFORMER_VI_INT8].
     *  • **1.69 (lượt đầu)** int8 → `vi-30M-int8` sau khi owner chốt về giấy phép — chấm bằng **corpus TTS**.
     *  • **1.69 (ĐẢO LẠI, cùng ngày)** → quay về int8. [ĐO giọng THẬT owner, 30 câu × 3 lượt]: gói int8 đúng
     *    **≈ 28/30**, còn 30M sai **~7 câu** (*"xem pin"* → *"xem binh"* · *"bật ghế sưởi"* → *"bọc ghế sửi"* ·
     *    *"lọc bụi mịn"* → *"bộ mệnh"*). Corpus TTS chấm 30M **cao hơn +1,8 điểm** ⇒ nó **đã đánh lừa**.
     *  • **release production (2026-09-21)** — hằng **KHÔNG đổi**; bốn gói còn lại rời danh mục, nên từ đây
     *    "mặc định" và "gói duy nhất" là một thứ.
     */
    const val DEFAULT_ID = "zipformer-vi-int8-2025-04-20"

    /**
     * Mô hình có đòi **ghi công** không — tức giấy phép của nó thuộc họ `BY`.
     *
     * Suy từ chính chuỗi giấy phép, không phải một danh sách tên gói viết tay: thêm một gói CC BY vào [ALL] là
     * hàng ghi công tự hiện, không phải nhớ sửa thêm chỗ nào (CLAUDE.md §7).
     */
    fun requiresAttribution(model: SherpaModel): Boolean =
        model.license.contains("BY", ignoreCase = true) && model.attribution.isNotBlank()

    /**
     * Mọi gói đang đòi ghi công — nguồn DUY NHẤT cho cả ba chỗ hiển thị (Cài đặt · `state.voice_model` · README).
     *
     * ⚠ Đang **rỗng**: gói duy nhất trong danh mục là Apache-2.0. Ba bề mặt kia vì thế tự ẩn phần ghi công thay
     * vì hiện một tiêu đề trống.
     */
    fun attributions(): List<SherpaModel> = ALL.filter { requiresAttribution(it) }

    /** Mô hình mặc định (đối tượng). Một chỗ trả lời *"mặc định là cái nào"* — [byId] cũng lùi về đây. */
    fun default(): SherpaModel = pickDefault(ALL, DEFAULT_ID)

    /**
     * [SOÁT 2026-09-16 · P3] Chọn mặc định **fail-safe**, cùng lối lùi với [byId].
     *
     * Bản trước là `ALL.first { it.id == DEFAULT_ID }` — `first{}` **ném** `NoSuchElementException`, trong khi
     * [byId] ngay dưới lại `firstOrNull{} ?: default()`. Hai hàm cạnh nhau, cùng một câu hỏi, hai hành vi hỏng
     * khác nhau. Hậu quả không nằm ở hôm nay (id đang khớp) mà ở ngày [DEFAULT_ID] hoặc [ALL] được sửa — và cả
     * hai **đã** được sửa nhiều lượt: một ký tự gõ nhầm thành **crash ngay lượt mở voice đầu tiên, trên xe**.
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
     * ⚠ Đường này nay gánh thêm một ca THẬT: xe từng bấm *"chuyển sang mô hình nhẹ"* (bề mặt đã gỡ 2026-09-21)
     * còn giữ pref trỏ vào một id không còn trong [ALL] ⇒ lùi về gói duy nhất thay vì làm câm đường nghe.
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
     * Bản **nhẹ hơn** gói đang cài, hoặc `null` khi không có bản nào nhẹ hơn.
     *
     * ⚠ Từ bản release production (owner 2026-09-21) hàm này **luôn trả `null`**: danh mục chỉ còn một gói, và
     * hai nút *"chuyển sang mô hình nhẹ"* / *"gỡ bản nặng"* trong Cài đặt đã gỡ cùng lượt. Nó ở lại vì đây vẫn là
     * chỗ ĐÚNG để trả lời câu hỏi *"có bản nào nhẹ hơn không"* — một câu hỏi về **dữ liệu của danh mục** (cỡ tệp ·
     * đã ghim chưa · có phải gói thử nghiệm không), và chỗ gọi duy nhất còn lại là trường máy-đọc
     * `state.voice_model.alt_available` của cầu kiểm thử, nơi `null` là câu trả lời **đúng** chứ không phải một
     * chỗ trống. Trả lời nó bằng `if (id == "…int8…")` ở tầng vẽ là viết cứng tên một mô hình vào chỗ không được
     * biết tên mô hình nào.
     */
    fun lighterThan(current: SherpaModel): SherpaModel? =
        ALL.filter { it.id != current.id && it.downloadable && !it.experimental && it.totalBytes in 1 until current.totalBytes }
            .minByOrNull { it.totalBytes }
}
