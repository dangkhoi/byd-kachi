package com.byd.clusternav.launcher

/**
 * CẢNH (P7 + P6) — một bộ *(bố cục + nội dung từng ô + thanh nút)* có tên, gọi lại được, và một trong số đó được
 * chọn làm **cảnh lúc nổ máy**. Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car.
 *
 * ## Vì sao P7 và P6 là MỘT khái niệm
 * Backlog ghi hai mục riêng: P7 *"chọn bố cục + app từng ô cho lúc nổ máy"* và P6 *"cảnh (scenes)"*. Đọc kỹ thì
 * chúng là **cùng một thứ** — một bộ bố trí có tên. Làm hai bộ lưu song song ("cấu hình khởi động" riêng, "cảnh"
 * riêng) là đúng **bẫy hai-bản-sao** mà dự án đã trả giá bốn lần (`customLayout` · `unitPrefs` ×4 · `wallpaper` ·
 * `themeMode`). Nên có đúng một khái niệm: cảnh gọi được **bằng tay** (P6), và **một** cảnh được đánh dấu là cảnh
 * lúc nổ máy (P7).
 *
 * ## Cảnh mang ĐÚNG những gì `WorkspaceState` + `DockConfig` mang — không hơn
 * Ba thứ: bố cục ([preset] + [gridLayout]) · nội dung ô ([slots]) · thanh nút ([dock]). **KHÔNG** mang hình nền và
 * **KHÔNG** mang chip thanh trạng thái — spec §6 OQ1, và lý do đáng ghi lại: cảnh là cách bố trí *vùng làm việc*.
 * Kéo hình nền vào thì cú chạm "gọi cảnh" biến thành "đổi cả giao diện", khó lường hơn hẳn; còn chip thanh trạng thái
 * thì nằm ở thanh trên — một dải luôn hiện, không thuộc vùng làm việc, và đổi nó theo cảnh sẽ làm hai vùng độc lập
 * dính vào nhau. Đơn vị đo, sáng/tối, ngôn ngữ, hồ sơ cũng không vào cảnh vì chúng là *cách trình bày*, đúng theo
 * đường biên mà [SettingsGroup.DISPLAY] đã dựng.
 *
 * @property id mã ổn định (`s1`..`s8`) — [SceneBook.bootSceneId] trỏ vào đây, nên nó KHÔNG được đổi khi đổi tên.
 * @property name tên do NGƯỜI DÙNG đặt. ⚠ Không phải nhãn của dự án ⇒ **không** đi qua lớp dịch (`Localized`): dịch
 *   dữ liệu của người dùng là làm sai. Đã được [sanitiseName] làm sạch trước khi vào đây.
 * @property gridLayout bố cục tự vẽ ở **dạng đã mã hoá** ([WorkspaceGrid.encode]), `null` = dùng bố cục sẵn. Giữ
 *   nguyên dạng chuỗi (không phải [GridLayout]) vì đó đúng là dạng chỗ lưu bền đang dùng — cảnh chỉ chép lại, không
 *   mã hoá lần thứ hai theo một luật khác.
 * @property slots nội dung từng ô, cùng thứ tự với [WorkspaceState.slots].
 */
data class Scene(
    val id: String,
    val name: String,
    val preset: LayoutPreset,
    val gridLayout: String?,
    val slots: List<SlotContent>,
    val dock: DockConfig,
) {

    /**
     * Trạng thái workspace mà cảnh này áp ra.
     *
     * ⚠ Tự đệm/cắt về đúng [WorkspaceState.SLOT_CAP] thay vì gọi [WorkspaceState.of] (hàm đó **ném** khi quá trần).
     * Ca quá trần có thật: bản sau nới trần ô rồi người dùng **hạ cấp bản** ⇒ chuỗi đã lưu có nhiều ô hơn trần hiện
     * tại. Ném ở đây nghĩa là launcher sập lúc nạp — đúng loại lỗi P9 bước 3 đã gặp với bố cục mặc định 4 phần tử.
     */
    fun workspaceState(): WorkspaceState = WorkspaceState(
        preset,
        List(WorkspaceState.SLOT_CAP) { slots.getOrElse(it) { SlotContent.Empty } },
    )

    /** Bố cục tự vẽ đã giải mã, `null` = dùng bố cục sẵn (khớp giao kèo của [HomeUiState.customLayout]). */
    fun grid(): GridLayout? = WorkspaceGrid.decode(gridLayout).takeIf { it.frames.isNotEmpty() }

    companion object {
        /**
         * Trần độ dài tên. Cảnh hiện thành một hàng có thêm ba nút (nổ máy · đổi tên · xoá) nên tên dài sẽ bị cắt
         * bằng "…" — cắt ở nguồn thì người dùng thấy ngay điều đó lúc đặt tên, thay vì tưởng mình đặt được rồi mới
         * phát hiện màn hình không hiện đủ.
         */
        const val NAME_MAX = 24

        /**
         * Làm sạch tên do người dùng nhập.
         *
         * ⚠⚠ **Bắt buộc, không phải cho đẹp.** Chuỗi lưu ngăn cảnh bằng `\n` và ngăn trường bằng `|`, nên một tên
         * chứa các ký tự đó sẽ **cắt đôi bản ghi** ⇒ lượt đọc sau thấy cấu trúc sai và bỏ cả cảnh đó đi (im lặng).
         * Dự án đã có đúng tiền lệ này: `WorkspacePrefs.addProfile` phải lọc `[\r\n]` vì danh sách hồ sơ ngăn bằng
         * `\n`. Ở đây nhiều ký tự phân tách hơn nên danh sách lọc dài hơn — và `;` cũng phải lọc vì nó ngăn các ô
         * trong trường [Scene.slots].
         */
        fun sanitiseName(raw: String): String =
            raw.replace(Regex("""[\r\n|;]"""), " ").trim().replace(Regex("""\s+"""), " ").take(NAME_MAX)

        /** Chụp trạng thái ĐANG DÙNG thành một cảnh. Đây là chỗ duy nhất quyết định *"cảnh gồm những gì"*. */
        fun capture(id: String, name: String, state: HomeUiState): Scene = Scene(
            id = id,
            name = sanitiseName(name),
            preset = state.workspace.preset,
            gridLayout = state.customLayout
                ?.takeIf { it.frames.isNotEmpty() }
                ?.let { WorkspaceGrid.encode(it) },
            slots = state.workspace.slots,
            dock = state.dock,
        )
    }
}

/**
 * ÁP một cảnh lên trạng thái HOME — **chỗ duy nhất** quyết định *"gọi cảnh thì đổi những gì"*.
 *
 * ## Vì sao là một hàm thuần, và vì sao chỉ có một
 * Hai đường gọi cảnh: người dùng **chạm** một cảnh (`HomeViewModel.applyScene`) và launcher **khởi động nguội** với
 * cảnh nổ máy (`PrefsWorkspaceRepository.load`). Hai đường phải cho **cùng** kết quả, không thì "cảnh lúc nổ máy" và
 * "cảnh gọi bằng tay" là hai tính năng khác nhau mang cùng một cái tên. Một hàm thuần ở `:core` là cách chắc chắn:
 * cả hai gọi đúng nó, và test off-car chứng minh được kết quả.
 *
 * ## Đây cũng là chỗ đạt **R4** (C5 của dự án)
 * Nó chỉ ghi ba trường ([HomeUiState.workspace] · [HomeUiState.dock] · [HomeUiState.customLayout]) trong **một** phép
 * `copy`, tức một lần state đổi ⇒ một lượt render. [WorkspaceRenderPlanner] rồi so **nội dung từng ô** như mọi lần,
 * nên ô nào không đổi thì không bị dựng lại ⇒ app đang chiếu trong ô đó **không bị nhả/gắn lại**. Chia thành ba
 * intent riêng (`setPreset` + `assignWidgets` + `setCustomLayout`) sẽ là ba lượt render với các trạng thái trung gian
 * — và một trạng thái trung gian có số ô khác là đủ để bộ quyết định trả "dựng lại tất cả".
 */
fun HomeUiState.withScene(scene: Scene): HomeUiState = copy(
    workspace = scene.workspaceState(),
    dock = scene.dock,
    customLayout = scene.grid(),
)
/**
 * SỔ CẢNH của một hồ sơ tài xế: danh sách cảnh + **cảnh lúc nổ máy**.
 *
 * Lưu **theo hồ sơ** (như bố cục và thanh nút), vì cảnh là cách bố trí của *một người*. Hai tài xế dùng chung một
 * xe thì mỗi người có bộ cảnh riêng và cảnh khởi động riêng.
 *
 * ## ⚠ CỐ Ý KHÔNG có `init { require(...) }`
 * Dự án thường chốt bất biến ngay lúc dựng (xem [SettingsCatalog] · [WorkspaceState]), nhưng ở đây làm vậy là **sai
 * hướng**: nguồn dữ liệu là một chuỗi trên đĩa mà người dùng có thể sửa tay (định dạng cố ý đọc được để cứu tay).
 * `require` biến một ký tự sai thành **launcher sập lúc nạp**. Bất biến ở đây được giữ bằng **tự chữa**: [decode] cắt
 * về trần và bỏ bản ghi hỏng, [normalised] gỡ con trỏ khởi động treo. Đó cũng là lý do mọi chỗ đọc nên dùng
 * [bootScene] chứ không đọc thẳng [bootSceneId].
 *
 * @property bootSceneId mã cảnh sẽ áp lúc launcher khởi động, `null` = không có. Có thể **treo** (trỏ tới cảnh đã
 *   xoá) nếu ai đó dựng trực tiếp bằng `copy` — [bootScene] và [normalised] xử lý ca đó.
 */
data class SceneBook(
    val scenes: List<Scene> = emptyList(),
    val bootSceneId: String? = null,
) {

    /** Đã đủ trần chưa — chỗ gọi PHẢI hỏi câu này và **nói ra** khi đủ (luật "không chặn im lặng"). */
    val full: Boolean get() = scenes.size >= CAP

    fun byId(id: String?): Scene? = scenes.firstOrNull { it.id == id }

    fun byName(name: String): Scene? {
        val clean = Scene.sanitiseName(name)
        return scenes.firstOrNull { it.name == clean }
    }

    /** Cảnh lúc nổ máy, `null` = chưa chọn **hoặc** con trỏ đang treo. Không bao giờ ném. */
    fun bootScene(): Scene? = byId(bootSceneId)

    /**
     * Lưu trạng thái đang dùng thành cảnh tên [name].
     *
     * Trùng tên ⇒ **ghi đè cảnh đó, GIỮ NGUYÊN `id`**. Hai điều đi kèm, cả hai đều là điều người dùng mong: (a) "tôi
     * vừa chỉnh lại chút, lưu lại cảnh *Đi làm*" không sinh ra cảnh thứ hai cùng tên; (b) nếu cảnh đó **đang là cảnh
     * nổ máy** thì nó vẫn là cảnh nổ máy sau khi lưu lại — vì dấu nổ máy trỏ theo `id`, không theo tên.
     *
     * Đủ trần + tên mới ⇒ trả về **chính nó** (không thêm). Chỗ gọi phải kiểm [full] trước và nói ra; đây chỉ là lớp
     * chặn thứ hai để không có đường nào vượt trần.
     */
    fun saved(name: String, state: HomeUiState): SceneBook {
        val clean = Scene.sanitiseName(name)
        if (clean.isEmpty()) return this
        val existing = byName(clean)
        if (existing != null) {
            val next = Scene.capture(existing.id, clean, state)
            return copy(scenes = scenes.map { if (it.id == existing.id) next else it })
        }
        if (full) return this
        val id = nextId() ?: return this
        return copy(scenes = scenes + Scene.capture(id, clean, state))
    }

    /**
     * Đổi tên cảnh [id].
     *
     * Từ chối (trả về chính nó) khi tên đã thuộc **cảnh khác**: [saved] phân biệt cảnh theo tên, nên hai cảnh cùng
     * tên sẽ làm "lưu lại cảnh Đi làm" ghi đè một cảnh mà người dùng không chỉ định. Đổi tên thành chính tên cũ thì
     * không sao. Chỗ gọi phải nói ra khi bị từ chối.
     */
    fun renamed(id: String, name: String): SceneBook {
        val clean = Scene.sanitiseName(name)
        if (clean.isEmpty() || byId(id) == null) return this
        val taken = byName(clean)
        if (taken != null && taken.id != id) return this
        return copy(scenes = scenes.map { if (it.id == id) it.copy(name = clean) else it })
    }

    /** Xoá cảnh [id]. Nó đang là cảnh nổ máy ⇒ dấu đó **tự bỏ** (không để lại con trỏ treo). */
    fun removed(id: String): SceneBook =
        copy(scenes = scenes.filterNot { it.id == id }).normalised()

    /**
     * Đặt/bỏ cảnh lúc nổ máy. `null` = bỏ dấu. Mã không thuộc sổ ⇒ **giữ nguyên** (không nhận con trỏ treo).
     *
     * Có đường **bỏ dấu** là cố ý: không có nó thì người dùng đã chọn một cảnh khởi động sẽ không thể quay về trạng
     * thái "launcher lên như lúc tôi tắt máy" mà không phải xoá hẳn cảnh đó.
     */
    fun withBootScene(id: String?): SceneBook = when {
        id == null -> copy(bootSceneId = null)
        byId(id) == null -> this
        else -> copy(bootSceneId = id)
    }

    /** Cắt về trần + gỡ con trỏ khởi động treo. [decode] gọi nó; [removed] cũng gọi để dấu nổ máy tự bỏ. */
    fun normalised(): SceneBook {
        val kept = scenes.distinctBy { it.id }.take(CAP)
        val boot = bootSceneId?.takeIf { id -> kept.any { it.id == id } }
        return if (kept == scenes && boot == bootSceneId) this else SceneBook(kept, boot)
    }

    /** Mã trống nhỏ nhất, hoặc `null` khi đã dùng hết CAP mã. */
    private fun nextId(): String? =
        (1..CAP).map { "s$it" }.firstOrNull { id -> scenes.none { it.id == id } }

    companion object {
        /**
         * Trần số cảnh: **8**.
         *
         * Đủ dùng cho các ca thật (đi làm · đi xa · đỗ xe · người thứ hai) và bị chặn bởi chỗ **hiện ra**: danh sách
         * cảnh nằm trong một trang cài đặt cuộn, hơn 8 hàng thì việc "gọi lại nhanh" mất nghĩa vì phải đi tìm. Đổi số
         * này thì [nextId] tự theo (mã `s1`..`s{CAP}`), và [decode] tự cắt dữ liệu cũ dài hơn.
         *
         * `const` (không phải `val`): hằng biên dịch thì miễn nhiễm với bẫy thứ-tự-khởi-tạo mà dự án đã trả giá ở
         * `TopStripConfig.BUILT_IN` ([ĐO] 27 bài đỏ vì `DEFAULT` dựng trước khi `BUILT_IN` có giá trị).
         */
        const val CAP = 8

        /** Sổ rỗng — khai SAU [CAP] (nó đọc `emptyList()` nên không phụ thuộc, nhưng thứ tự khai là thói quen tốt). */
        val EMPTY = SceneBook()

        /** Ngăn giữa các cảnh. Cùng ký tự với danh sách hồ sơ ⇒ một luật lọc tên, không phải hai. */
        private const val REC = "\n"

        /** Ngăn giữa các trường của một cảnh. */
        private const val FLD = "|"

        /** Ngăn giữa các ô trong trường nội dung ô. */
        private const val SLOT = ";"

        /** Số trường của một bản ghi. Lệch ⇒ bản ghi hỏng ⇒ **bỏ nó**, giữ các cảnh còn lại. */
        private const val FIELDS = 7

        /**
         * Ba ký tự mà chuỗi lưu này dùng để **ngăn cấu trúc** — cấm xuất hiện trong bất kỳ thứ được nhúng vào nó.
         *
         * ## ⚠⚠ Vì sao phải công khai, không để private
         * [SlotCodec.encode] sinh ra một phần **nằm bên trong** chuỗi này, mà nó là một object KHÁC. Khi ràng buộc đó
         * chỉ nằm trong đầu người viết thì nó đã bị phá ngay lần đầu: bản đầu của T4 chọn `|` làm dấu ngăn
         * id/provider — trùng [FLD] — và [ĐO] trên `emulator-5554` cho thấy hậu quả là **mất cảnh trong im lặng**
         * (bản ghi ra 8 trường ⇒ [decode] bỏ nó ⇒ màn Cài đặt hiện "Chưa có cảnh nào" sau khi khởi động lại).
         *
         * Công khai ⇒ có một chỗ **duy nhất** khai luật, và bài canh của `SceneBookTest` quét được **mọi** loại ô
         * thay vì phải nhớ thêm ca mới. Đây là chốt chặn **nguyên nhân**, không phải hiện tượng: loại ô thứ năm mai
         * sau chọn sai ký tự thì test đỏ tại chỗ khai, chứ không đợi tới lúc người dùng mất cảnh.
         *
         * `,` KHÔNG có trong danh sách vì nó không ngăn cấu trúc của *chuỗi cảnh* — nó ngăn danh sách thẻ dựng tay
         * BÊN TRONG một ô, tức đã nằm trong phần mà [SlotCodec] tự chịu trách nhiệm.
         */
        val RESERVED: List<String> = listOf(REC, FLD, SLOT)

        /**
         * Chuỗi lưu danh sách cảnh — **chỉ danh sách**, không gồm [bootSceneId] (nó là khoá lưu riêng, xem
         * `WorkspacePrefs`).
         *
         * Định dạng **tự đọc được** (`s1|Đi làm|QUAD||widget:w_board;app:com.waze||lock,window`), cùng lý do với
         * [WorkspaceGrid.encode]: cảnh là thứ người dùng bỏ công dựng, và khi cần cứu dữ liệu bằng tay thì đọc được
         * quan trọng hơn tiết kiệm byte.
         */
        fun encode(book: SceneBook): String = book.scenes.joinToString(REC) { s ->
            listOf(
                s.id,
                s.name,
                s.preset.name,
                s.gridLayout ?: "",
                s.slots.joinToString(SLOT) { SlotCodec.encode(it) },
                s.dock.edge.name,
                s.dock.enabled.joinToString(","),
            ).joinToString(FLD)
        }

        /**
         * Giải mã. **Tự chữa**: bản ghi sai cấu trúc bị bỏ, mã trùng giữ bản đầu, quá trần thì cắt, và con trỏ khởi
         * động trỏ tới cảnh không tồn tại thì **tự bỏ**. Không bao giờ ném.
         *
         * Giá trị enum lạ (`preset`/`dock edge`) thì **lùi về mặc định** chứ không bỏ cả cảnh: nội dung ô là phần
         * người dùng bỏ công nhất, mất nó vì một tên enum đổi ở bản sau là thiệt hại lớn hơn hẳn việc bố cục về
         * mặc định. Bản ghi chỉ bị bỏ khi **không đọc ra nổi** một cảnh (thiếu trường, thiếu mã, thiếu tên).
         *
         * ⚠ [SOÁT P2-1] Danh sách nút **RỖNG được giữ rỗng** — xem chú thích tại chỗ ở nhánh `dock`. Đừng "khớp lại"
         * với `WorkspacePrefs.loadDock` bằng `ifEmpty { defaultEnabledIds() }`: `loadDock` dùng `?:` nên nó chỉ bù mặc
         * định khi **khoá thiếu**, còn chuỗi rỗng thì nó cũng giữ rỗng. Hai lối đọc đã khớp; bản cũ mới là lối lệch.
         */
        fun decode(scenesRaw: String?, bootRaw: String?): SceneBook {
            if (scenesRaw.isNullOrBlank()) {
                // Không có cảnh nào thì con trỏ khởi động chắc chắn treo ⇒ normalised() gỡ nó.
                return SceneBook(emptyList(), bootRaw?.takeIf { it.isNotBlank() }).normalised()
            }
            val scenes = scenesRaw.split(REC).mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val f = line.split(FLD)
                if (f.size != FIELDS) return@mapNotNull null
                val id = f[0].trim()
                val name = Scene.sanitiseName(f[1])
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                Scene(
                    id = id,
                    name = name,
                    preset = runCatching { LayoutPreset.valueOf(f[2]) }.getOrDefault(LayoutPreset.THREE),
                    gridLayout = f[3].takeIf { it.isNotBlank() },
                    slots = f[4].split(SLOT).map { SlotCodec.decode(it) },
                    dock = DockConfig(
                        edge = runCatching { DockEdge.valueOf(f[5]) }.getOrDefault(DockEdge.BOTTOM),
                        // ⚠⚠ [SOÁT P2-1] Trường RỖNG = **thanh nút rỗng thật**, KHÔNG phải "chưa lưu" ⇒ giữ nguyên
                        // danh sách rỗng.
                        //
                        // Bản cũ viết `.ifEmpty { ControlRegistry.defaultEnabledIds() }` kèm chú thích *"khớp
                        // WorkspacePrefs.loadDock (cùng cách đọc, cùng ca rỗng)"*. Câu đó **SAI**: `loadDock` dùng `?:`
                        // nên nó chỉ bù mặc định khi **khoá THIẾU**; chuỗi rỗng ở đó cho `[""]` → lọc → `[]`, tức thanh
                        // nút rỗng ĐƯỢC GIỮ. Và thanh nút rỗng là trạng thái **đạt được thật** —
                        // `DockConfig.setEnabled` chỉ `remove`, không hề có sàn.
                        //
                        // Hậu quả của bản cũ: tắt hết nút → lưu cảnh → gọi lại thì **9 nút mặc định quay về**, im lặng.
                        // Ở đây phân biệt được vì bản ghi luôn có đủ [FIELDS] trường: thiếu trường ⇒ bản ghi bị bỏ
                        // (không tới được dòng này), nên trường có mặt mà rỗng chỉ có một nghĩa.
                        enabled = f[6].split(",").filter { it.isNotBlank() },
                    ),
                )
            }
            return SceneBook(scenes, bootRaw?.takeIf { it.isNotBlank() }).normalised()
        }
    }
}
