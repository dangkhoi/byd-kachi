package com.byd.clusternav.launcher.voice

/**
 * ═══ V1 pha NÓI · GÓI GIỌNG ĐỌC sherpa-onnx — GHIM BẰNG SỐ ĐO THẬT, KHÔNG BỊA ═════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R2b · T8**. Thuần Kotlin (`:core`) ⇒ luật ghim kiểm off-car;
 * phần chạm đĩa/mạng nằm ở `:app` (`VoiceModelStore`, qua hợp đồng [VoicePack]).
 *
 * ## Vì sao một danh mục RIÊNG, không thêm vào [SherpaModelCatalog]
 * [SherpaModelCatalog] mô tả mô hình **NGHE** (transducer: encoder/decoder/joiner + tokens), và `ALL` của nó là
 * danh sách hiện trong *Cài đặt › Mô hình nhận dạng*. Gói **ĐỌC** là một họ khác hẳn (VITS: một tệp `.onnx` +
 * tokens + một **cây thư mục** dữ liệu ngôn ngữ), và nó không được phép rơi vào danh sách chọn mô hình nghe. Hai
 * danh mục, hai vòng đời, cùng một **luật ghim** ([SherpaModelCatalog.ModelFile]) và cùng một **hợp đồng cài**
 * ([VoicePack]) — thứ phải dùng chung là luật, không phải cái bảng.
 *
 * ## ⚠ Lịch sử: vì sao 1.63–1.64 chưa tải được, và vì sao 1.65 tải được — không phải nhờ tầng giải nén
 * [ĐO 2026-09-15] gói Piper vi_VN phát hành **chỉ** dạng `tar.bz2` **397 mục**, 393 trong đó là `espeak-ng-data/`
 * (18 MB) — bắt buộc, vì Piper chuyển chữ thành âm vị bằng espeak-ng (`…onnx.json` khai `"espeak":{"voice":"vi"}`).
 * Kết luận lúc đó: phải viết một tầng giải nén `.tar.bz2` ⇒ `downloadable = false`, bảng ghim cả gói nén.
 *
 * [ĐO 2026-09-16, host, sherpa-onnx 1.13.8 `OfflineTts`] kết luận ấy **bị bác**: `espeak-ng-data` tỉa còn **11
 * mục / 728 KB** (thay vì 393 mục / 18 MB) cho audio **bit-identical** với gói đầy đủ — câu *"Đã đặt nhiệt độ hai
 * mươi bốn, gió mức ba"*: nạp 203 ms, tổng hợp 62 ms, 2,11 s audio @22050 Hz, hai tệp WAV giống nhau từng byte.
 * ⇒ gói tỉa còn **13 tệp**, tải **từng tệp có ghim sha256** được, đúng đường mà `VoiceModelStore` đã có. Không
 * cần tầng `.tar.bz2` nào — và một tầng giải nén không phải viết là một tầng không thể hỏng trên xe.
 *
 * Ba trường cũ (`archiveUrl`/`archiveSha256`/`archiveBytes`/`rootInArchive`/`needsArchiveExtract`) đã **gỡ hẳn**:
 * giữ lại một trường không ai đọc là mời người sau tưởng còn một đường thứ hai. Số ghim của gói nén nằm trong
 * §9 của spec nếu cần tra lịch sử.
 *
 * ## ⚠ TODO(owner) — asset CHƯA được đăng, và đó là một sự thật, không phải một hạng mục bỏ quên
 * [URLS] trỏ tới thư mục `voice/tts/` [RELEASE_TAG] trong repo `dangkhoi/byd-kachi` qua raw URL (cùng kênh OTA — xem nguồn 2 của
 * [VoiceModelManifest.URLS]). Máy soạn thảo **không có `gh`**, nên owner phải tự tải 13 tệp lên; tên asset =
 * đường dẫn tương đối giữ nguyên (`voice/tts/piper-vi_VN-vais1000-medium.sha256.tsv` là bảng ghim ngoài mã). Tới lúc đó:
 *  • **tải qua mạng** sẽ hỏng fail-safe (404 hoặc sha không khớp ⇒ câu lỗi nói rõ tệp nào), KHÔNG tải mù;
 *  • **side-load USB** chạy ngay: đặt cây thư mục vào `<ext>/sherpa/import/<id>/…` (xem playbook §6f).
 *
 * ## Giấy phép (đọc `MODEL_CARD` thật trong gói đã tải, 2026-09-15 — rule global §1.1)
 *  • Dữ liệu VAIS-1000 = **CC-BY-4.0** ([ĐO] `MODEL_CARD`) ⇒ dùng được, **phải ghi công** ([attribution]).
 *  • Engine sherpa-onnx (AAR) = Apache-2.0.
 *  • Hai gói vi_VN còn lại trên cùng bản phát hành (`25hours_single-low`, `vivos-x_low`) chưa tải nên
 *    **[CHƯA BIẾT]** sha, không khai ở đây.
 */
object SherpaTtsCatalog {

    /** Gốc thư mục chứa gói giọng đọc — song song `sherpa/` của [SherpaModelCatalog.DIR_ROOT]. */
    const val DIR_ROOT = "sherpa-tts"

    /**
     * Một gói giọng đọc VITS (họ Piper).
     *
     * @param id nhãn ổn định, cũng là tên thư mục con của `filesDir/sherpa-tts/<id>`.
     * @param files 13 tệp của gói **đã tỉa**, mỗi tệp ghim sha256 + bytes; `name` là đường dẫn tương đối có thể
     *   nhiều đoạn (xem [SherpaModelCatalog.ModelFile]).
     * @param model tệp `.onnx` (đường dẫn TƯƠNG ĐỐI so với thư mục gói).
     * @param tokens bảng token.
     * @param dataDir thư mục dữ liệu espeak-ng — **bắt buộc** với họ Piper, xem KDoc lớp.
     * @param sampleRate tần số lấy mẫu giọng ([ĐO] `MODEL_CARD` + `onnx.json`) — `AudioTrack` cần biết trước.
     * @param attribution câu ghi công bắt buộc theo giấy phép; hiện ở màn CREDITS.
     */
    @Suppress("LongParameterList")
    data class TtsVoice(
        override val id: String,
        override val label: String,
        val license: String,
        override val files: List<SherpaModelCatalog.ModelFile>,
        val model: String,
        val tokens: String,
        val dataDir: String,
        val sampleRate: Int,
        val attribution: String,
    ) : VoicePack {

        /** Thư mục con của `filesDir` chứa gói này sau khi đã lắp. */
        override val dir: String get() = "$DIR_ROOT/$id"

        /** Tổng byte của cả gói tỉa. */
        override val totalBytes: Long get() = files.sumOf { it.bytes }

        /**
         * Được phép tải **qua mạng** chưa — mọi tệp ghim đủ sha256 + bytes, đúng luật của [VoicePack].
         *
         * ⚠ `true` ở đây **không** hứa rằng máy chủ đã có tệp: asset còn chờ owner đăng (xem KDoc lớp). Đó là
         * đúng ranh giới — bảng này khai *"đã ghim an toàn để tải"*, còn *"máy chủ có trả về không"* là câu chỉ
         * trả lời được lúc chạy, và lúc đó phép so sha256 của `VoiceModelStore` bắt được mọi thứ khác bản ghim.
         */
        override val downloadable: Boolean get() = files.isNotEmpty() && files.all { it.pinned }

        /** Tìm một tệp theo đường dẫn tương đối. */
        fun file(name: String): SherpaModelCatalog.ModelFile? = files.firstOrNull { it.name == name }
    }

    /** Tag của GitHub Release chứa 13 asset — xem TODO(owner) ở KDoc lớp. */
    const val RELEASE_TAG = "piper-vi_VN-vais1000-medium"

    /** Gốc URL asset. Tách hằng để 13 dòng dưới không lặp một chuỗi dài, và để đổi kênh chỉ sửa một chỗ. */
    // Owner 2026-09-16: gói phải TẢI TRONG APP (không USB). Máy soạn thảo không có `gh`/release ⇒ 13 tệp đăng NGAY
    // TRONG repo `voice/tts/<id>/` (cùng kênh raw như OTA `apk/`), đường dẫn tương đối giữ nguyên `/`.
    private const val RELEASE_BASE =
        "https://raw.githubusercontent.com/dangkhoi/byd-kachi/main/voice/tts/$RELEASE_TAG/"

    /**
     * Tên asset của một đường dẫn tương đối — bản đầu thay `/` bằng `__` cho GitHub Release; nay tệp nằm trong
     * cây thư mục của repo nên đường dẫn giữ NGUYÊN (hàm giữ lại làm một chỗ đổi kênh duy nhất).
     *
     * Là một hàm chứ không phải 13 chuỗi chép tay: chép tay thì một dấu gạch sai chính tả chỉ lộ ra ở chiếc xe
     * đang tải, dưới dạng 404 cho đúng một tệp trong mười ba.
     */
    fun assetName(relativePath: String): String = relativePath

    /** Một dòng của bảng ghim: URL sinh từ [assetName], chỉ phải viết đường dẫn + bytes + sha một lần. */
    private fun pinned(name: String, bytes: Long, sha256: String) =
        SherpaModelCatalog.ModelFile(name, RELEASE_BASE + assetName(name), sha256, bytes)

    /**
     * Giọng Việt duy nhất đã **tải thật + tỉa thật + băm thật**.
     *
     * 13 dòng dưới là số [ĐO] 2026-09-16 (`stat -f%z` + `shasum -a 256` trên chính cây thư mục đã dựng bản WAV
     * bit-identical). Tổng: **63 877 499 byte ≈ 61 MB** — so với 67 MB của gói nén đầy đủ, và 18 MB trong đó là
     * `espeak-ng-data` đã bị tỉa còn 728 KB.
     */
    val PIPER_VI_VAIS1000 = TtsVoice(
        id = "piper-vi_VN-vais1000-medium",
        label = "Piper VN — VAIS-1000 (medium)",
        license = "CC-BY-4.0",
        files = listOf(
            // ── espeak-ng-data đã TỈA: 11 mục thay cho 393 (xem KDoc lớp về phép đo bit-identical) ──
            pinned("espeak-ng-data/intonations", 2_040L,
                "3f8af65fd3eda9759a10f021d61361c120871f463515229c925995c7f90918cc"),
            pinned("espeak-ng-data/lang/aav/vi", 111L,
                "3199c980f9e23a88a2aa693cd631bf4fcb0f3408c4272bc01b7ac0ff8e79d778"),
            pinned("espeak-ng-data/lang/aav/vi-VN-x-central", 143L,
                "b351f13c6ed0da37561df25f45579872ff9863920ad66b94e27327a2c611755b"),
            pinned("espeak-ng-data/lang/aav/vi-VN-x-south", 142L,
                "c0a0a2c894cd57b0bbacf9ef4efb8d89bfaf9feb164830e1539f599e7cc4e021"),
            pinned("espeak-ng-data/phondata", 550_424L,
                "4e0288957874029a8c3c9f41a8f517ad4bf18127046decbdd4b9d1d6807ce3a3"),
            pinned("espeak-ng-data/phondata-manifest", 21_821L,
                "7b387af0702c7cf0b61f0bead68feded0bd8e1620729b0b252e76acbc30d3813"),
            pinned("espeak-ng-data/phonindex", 39_074L,
                "3ca7b8fa3b42624e4b0f152707e7a39245fce569aa99ea47c055d9e622fcf0c4"),
            pinned("espeak-ng-data/phontab", 55_796L,
                "886f3fa402cb0ba73d483aa8ad000af47a6b7cc06293c75a97913fba68a530f6"),
            pinned("espeak-ng-data/vi_dict", 52_608L,
                "bbf7cab4ba733b2f5d9d3b61eb1194ef745232fa3adebb4bc92456e948cb3722"),
            // ── Giấy phép + bảng token + mô hình ──
            pinned("MODEL_CARD", 361L,
                "302db8a930ffc2b1c2181db26deaf8272116eca2b08b8f80da6375b0a994af7b"),
            pinned("tokens.txt", 921L,
                "87c8ef66eae5473ed0cc0366b3964c736ca6c5f676c979522ea31234e47430b9"),
            pinned("vi_VN-vais1000-medium.onnx", 63_149_198L,
                "df1512ef3265609f147ae23726b8c8867c6d28e60acb9ffca3545e11783b809f"),
            pinned("vi_VN-vais1000-medium.onnx.json", 4_860L,
                "fafb9da1354ed4b77c31af228ed41fb41cd825c14cffa105454b25e6ae751ee0"),
        ),
        model = "vi_VN-vais1000-medium.onnx",
        tokens = "tokens.txt",
        dataDir = "espeak-ng-data",
        sampleRate = 22_050,
        attribution = "VAIS-1000 Vietnamese speech synthesis corpus (CC-BY-4.0) · Piper · sherpa-onnx",
    )

    /** Mọi gói giọng đọc, thứ tự hiện trong Cài đặt. */
    val ALL: List<TtsVoice> = listOf(PIPER_VI_VAIS1000)

    /** Gói mặc định cho tiếng Việt. */
    const val DEFAULT_ID = "piper-vi_VN-vais1000-medium"

    /** Tra theo id; id lạ/rỗng ⇒ gói mặc định (fail-safe cho pref cũ) — cùng nếp [SherpaModelCatalog.byId]. */
    fun byId(id: String?): TtsVoice = ALL.firstOrNull { it.id == id } ?: PIPER_VI_VAIS1000

    /**
     * Tốc độ đọc mặc định cho `OfflineTts.generate(text, sid, speed)`.
     *
     * `1.0` = tốc độ gốc của gói. Chưa có phép đo trên xe nên **không** tự ý đẩy nhanh: một câu đọc nhanh trong
     * phòng yên là một câu không nghe ra trong ca-bin 80 km/h. Số này lên/xuống sau phép đo V-oncar (spec §6).
     */
    const val DEFAULT_SPEED = 1.0f

    /** Số luồng cho phiên suy diễn ONNX — cùng số với đường NGHE, ca-bin chỉ có một việc chạy tại một lúc. */
    const val NUM_THREADS = 2
}
