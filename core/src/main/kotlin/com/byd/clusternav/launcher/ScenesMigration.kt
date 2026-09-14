package com.byd.clusternav.launcher

/**
 * Nội dung một hồ sơ tài xế **mà lượt chuyển đổi dựng ra** — đúng ba thứ mà một "cảnh" đời cũ mang.
 *
 * ⚠ Cố ý KHÔNG mang chủ đề/đơn vị/hình nền/ngôn ngữ/cấu hình ClusterNav: R2 nói *"các phần khác **chép từ hồ sơ đang
 * dùng**"*. Nếu dựng chúng ở đây thì lớp thuần này phải đoán giá trị mặc định của từng khoá, mà mặc định đúng chỉ có
 * nơi lưu bền biết — và đoán sai nghĩa là người dùng mở lên thấy hồ sơ mới "trắng" thay vì giống hồ sơ họ đang dùng.
 *
 * @property grid bố cục tự vẽ đã giải mã, `null` = dùng bố cục sẵn (khớp giao kèo [HomeUiState.customLayout]).
 */
data class ProfileRecord(
    val name: String,
    val workspace: WorkspaceState,
    val dock: DockConfig,
    val grid: GridLayout?,
)

/**
 * ═══ S4 · R2 — CHUYỂN "cảnh" đời cũ thành "hồ sơ tài xế", MỘT lần, không mất gì ═══════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Spec `docs/specs/kachi-profiles-are-everything.html` R2.
 *
 * ## ⚠⚠ Vì sao lớp này TỰ giải mã, không gọi `SceneBook.decode`
 * `SceneBook` **bị xoá** ở T2 (R1: bỏ hẳn khái niệm cảnh). Một lượt chuyển đổi mà phụ thuộc vào lớp sắp bị xoá thì
 * hoặc nó chết theo, hoặc `SceneBook` phải sống mãi như mã mồ côi — đúng thứ CLAUDE.md §8 cấm. Dữ liệu **trên đĩa
 * của xe đang chạy** thì vẫn ở dạng cũ mãi mãi, nên phép đọc nó phải sống ở chỗ còn lại: **đây**.
 *
 * Phép giải mã dưới đây chép **đúng hành vi** của `SceneBook.decode` bản 2026-09-14, kể cả bốn ca tự-chữa đã trả giá
 * bằng test: (1) bản ghi sai số trường ⇒ bỏ riêng bản ghi đó; (2) enum lạ ⇒ **lùi về mặc định**, không bỏ cả cảnh
 * (nội dung ô là phần người dùng bỏ công nhất); (3) mã trùng ⇒ giữ bản đầu; (4) trường danh sách nút **rỗng được giữ
 * rỗng** (thanh nút rỗng là trạng thái đạt được thật — `DockConfig.setEnabled` chỉ `remove`, không có sàn).
 * `ScenesMigrationTest` giữ **chuỗi fixture nguyên văn** do `SceneBook.encode` sinh ra, nên sau khi `SceneBook` bị
 * xoá bài canh vẫn chứng minh được hai phép đọc khớp nhau.
 */
object ScenesMigration {

    /** Kế hoạch chuyển đổi. `:app` chỉ **áp** nó rồi đặt dấu `migrated_scenes_v1` — không tự quyết gì thêm. */
    data class Plan(
        val newProfiles: List<ProfileRecord>,
        /** Tên hồ sơ sẽ dùng lúc nổ máy, `null` = **hồ sơ dùng gần nhất** (R6). */
        val bootProfile: String?,
    ) {
        /** Không có gì để làm ⇒ `:app` chỉ đặt dấu và đi tiếp (vẫn phải đặt dấu, không thì lượt sau quét lại). */
        val empty: Boolean get() = newProfiles.isEmpty() && bootProfile == null

        companion object {
            val NOTHING = Plan(emptyList(), null)
        }
    }

    /** Trần số cảnh của bản cũ — chép lại ở đây vì chuỗi trên đĩa đã bị cắt theo đúng số này. */
    const val LEGACY_CAP = 8

    /** Trần độ dài tên (giống `Scene.NAME_MAX`): tên dài hơn bị cắt để thẻ hồ sơ không phải hiện "…". */
    const val NAME_MAX = 24

    private const val REC = "\n"
    private const val FLD = "|"
    private const val SLOT = ";"
    private const val FIELDS = 7

    /**
     * Ba ký tự **ngăn cấu trúc** của chuỗi đời cũ: bản ghi · trường · ô.
     *
     * ⚠ Đây là luật cuối cùng còn lại của định dạng đó sau khi `SceneBook` bị xoá (S4 · T2), và nó vẫn phải sống: đầu
     * ra của [SlotCodec.encode] được **nhúng vào** chuỗi này, nên một loại ô mai sau mã hoá ra ký tự nằm trong đây sẽ
     * làm bản ghi sai số trường ⇒ [parse] bỏ nguyên bản ghi **trong im lặng**, và lượt chuyển đổi một-lần mất đúng
     * cảnh đó vĩnh viễn. `ScenesMigrationTest` quét **mọi** loại [SlotContent] theo danh sách này (bài chuyển từ
     * `SceneBookTest` sang, không mất).
     */
    val RESERVED: List<String> = listOf(REC, FLD, SLOT)

    /**
     * Làm sạch tên — **bắt buộc, không phải cho đẹp**: danh sách hồ sơ ngăn bằng `\n`, nên một tên chứa ký tự đó sẽ
     * **cắt đôi** danh sách ⇒ lượt đọc sau thấy cấu trúc sai. Giữ nguyên bộ lọc của `Scene.sanitiseName` (`\r\n|;`)
     * để tên đã nằm trên đĩa không đổi nghĩa khi đi qua lượt chuyển đổi.
     */
    fun sanitiseName(raw: String): String =
        raw.replace(Regex("""[\r\n|;]"""), " ").trim().replace(Regex("""\s+"""), " ").take(NAME_MAX)

    /**
     * Đọc chuỗi sổ cảnh đời cũ ra danh sách hồ sơ, **theo đúng thứ tự đã lưu**, chưa khử trùng tên.
     *
     * Công khai vì [AppWidgetIds.idsInStored] cần nó: id widget bên thứ ba nằm **trong** nội dung ô của cảnh, và
     * trước khi lượt chuyển đổi chạy xong thì những id đó vẫn phải được tính là *"còn dùng"* — không thì lượt khởi
     * động đầu tiên của bản mới sẽ thu hồi chúng và widget trong cảnh chết **vĩnh viễn** trước khi kịp thành hồ sơ
     * (xem KDoc [AppWidgetIds.idsIn] về ca đã đo trên `emulator-5554`).
     */
    fun parse(scenesRaw: String?): List<ProfileRecord> = parseWithIds(scenesRaw).map { it.second }

    /**
     * Kế hoạch chuyển đổi.
     *
     * @param scenesRaw chuỗi khoá `<hồ sơ>__scenes` đời cũ (dạng `SceneBook.encode`); `null`/rỗng ⇒ không có gì.
     * @param bootSceneId chuỗi khoá `<hồ sơ>__boot_scene` đời cũ — **mã** cảnh (`s1`..`s8`), không phải tên.
     * @param activeProfile hồ sơ đang dùng lúc chuyển đổi. Mọi hồ sơ mới **chép phần còn lại** từ nó (R2), và nó
     *   cũng là thứ giữ chỗ trong [existingNames] nếu chỗ gọi quên truyền.
     * @param existingNames tên hồ sơ ĐÃ CÓ — trùng thì thêm hậu tố `" 2"`, `" 3"`… (R2).
     */
    fun plan(
        scenesRaw: String?,
        bootSceneId: String?,
        activeProfile: String,
        existingNames: List<String>,
    ): Plan {
        val scenes = parseWithIds(scenesRaw)
        if (scenes.isEmpty()) return Plan.NOTHING
        val taken = (existingNames + activeProfile).filter { it.isNotBlank() }.toMutableSet()
        val records = ArrayList<ProfileRecord>(scenes.size)
        val nameById = LinkedHashMap<String, String>()
        for ((id, record) in scenes) {
            val name = uniqueName(record.name, taken)
            taken += name
            nameById[id] = name
            records += record.copy(name = name)
        }
        // Con trỏ TREO (trỏ tới cảnh đã xoá) ⇒ `null` = "hồ sơ dùng gần nhất". Không bao giờ nhận một tên không có
        // thật: cùng luật `SceneBook.normalised()` đã giữ, và một `boot_profile` treo thì launcher nổ máy lên với
        // hồ sơ mặc định mà không ai hiểu vì sao.
        val boot = bootSceneId?.takeIf { it.isNotBlank() }?.let { nameById[it] }
        return Plan(records, boot)
    }

    /**
     * Tên chưa ai dùng, dựa trên [base]. Trùng ⇒ `" 2"`, `" 3"`…
     *
     * ⚠ Phần gốc bị **cắt bớt** để tên + hậu tố vẫn ≤ [NAME_MAX]: nối thẳng thì một cảnh tên đủ 24 ký tự sẽ ra hồ sơ
     * 26 ký tự — dài hơn thứ mọi chỗ khác trong app chấp nhận, và khoá lưu bền thì ghép thẳng tên vào nên nó im lặng
     * trôi vào đĩa.
     */
    private fun uniqueName(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var n = 2
        while (true) {
            val suffix = " $n"
            val candidate = base.take((NAME_MAX - suffix.length).coerceAtLeast(1)).trimEnd() + suffix
            if (candidate !in taken) return candidate
            n++
        }
    }

    /** Giải mã ra cặp `mã cảnh → hồ sơ`; mã chỉ dùng để nối lại con trỏ cảnh-lúc-nổ-máy. */
    private fun parseWithIds(scenesRaw: String?): List<Pair<String, ProfileRecord>> {
        if (scenesRaw.isNullOrBlank()) return emptyList()
        val seen = mutableSetOf<String>()
        return scenesRaw.split(REC).mapNotNull { line -> record(line) }
            .filter { seen.add(it.first) }   // mã trùng ⇒ giữ bản ĐẦU (một mã, một cảnh)
            .take(LEGACY_CAP)
    }

    private fun record(line: String): Pair<String, ProfileRecord>? {
        if (line.isBlank()) return null
        val f = line.split(FLD)
        if (f.size != FIELDS) return null
        val id = f[0].trim()
        val name = sanitiseName(f[1])
        if (id.isEmpty() || name.isEmpty()) return null
        val slots = f[4].split(SLOT).map { SlotCodec.decode(it) }
        val record = ProfileRecord(
            name = name,
            // ⚠ Tự đệm/cắt về đúng SLOT_CAP thay vì `WorkspaceState.of` (hàm đó **ném** khi quá trần). Ca quá trần
            // có thật: bản sau nới trần ô rồi người dùng HẠ CẤP bản ⇒ chuỗi đã lưu có nhiều ô hơn trần hiện tại.
            workspace = WorkspaceState(
                runCatching { LayoutPreset.valueOf(f[2]) }.getOrDefault(LayoutPreset.THREE),
                List(WorkspaceState.SLOT_CAP) { slots.getOrElse(it) { SlotContent.Empty } },
            ),
            dock = DockConfig(
                edge = runCatching { DockEdge.valueOf(f[5]) }.getOrDefault(DockEdge.BOTTOM),
                // Trường RỖNG = thanh nút rỗng THẬT, không phải "chưa lưu" — xem KDoc lớp, ca (4).
                enabled = f[6].split(",").filter { it.isNotBlank() },
            ),
            grid = WorkspaceGrid.decode(f[3].takeIf { it.isNotBlank() }).takeIf { it.frames.isNotEmpty() },
        )
        return id to record
    }
}
