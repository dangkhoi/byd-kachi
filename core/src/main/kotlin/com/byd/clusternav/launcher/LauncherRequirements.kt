package com.byd.clusternav.launcher

/**
 * VÒNG KIỂM QUYỀN lúc mở launcher (P8) — phần QUYẾT ĐỊNH, thuần Kotlin (`:core`, cấm `android.*`) ⇒ test off-car.
 * Spec: `docs/specs/kachi-permission-preflight.html`.
 *
 * ## Bệnh nó chữa
 * [ĐO] 2026-09-11: phép kiểm quyền đang **rải rác ở 5 chỗ** (màn Cài đặt cũ · bộ mở ngăn kéo · dải header nổi ·
 * bộ tự-mở VietMap · màn chính launcher) và **không có nơi tập trung**. Hệ quả: người dùng chỉ biết thiếu quyền khi
 * một tính năng **im lặng không chạy** — rồi mỗi lần lại chẩn đoán từ đầu. Backlog cũng ghi quyền **hay mất sau khi
 * khởi động lại**.
 *
 * ## Vì sao lớp này không chạm Android
 * Nó chỉ trả lời: *với bộ trạng thái này thì thiếu gì · cái nào tự sửa được · cần nói gì*. Việc **đọc trạng thái
 * thật** và **cấp quyền** nằm ở `:app`. Nhờ vậy test off-car chạy được **đủ mọi tổ hợp thiếu/đủ** — quan trọng vì
 * ca thiếu quyền là ca **khó tái hiện nhất** trên máy thật.
 *
 * ## ⚠ Sự thật định hình thiết kế
 * **Màn cài đặt hệ thống trên xe BỊ KHOÁ** (mở ra chỉ nhận *"Hệ thống IVI không hỗ trợ hoạt động này"*). Vì vậy
 * KHÔNG mục nào được khai cách sửa là "mở màn cài đặt" — có test khoá điều đó.
 */

/** Ai sửa được một điều kiện. Quyết định trải nghiệm: người dùng chỉ nên bị hỏi khi thật sự phải hỏi. */
enum class FixBy {
    /** Launcher tự cấp **ngay tại vòng kiểm** qua kênh shell (công thức đã chạy thật trên xe từ 1.13) ⇒ tự làm, không hỏi. */
    SELF,

    /**
     * App tự lo, nhưng **ở đường KHỞI ĐỘNG**, không phải ở màn chính.
     *
     * ⚠ Vì sao phải có loại riêng này: cờ cửa sổ tự do là **trạng thái BỀN**, và dự án có hai luật kiến trúc —
     * (1) chỉ **một nơi** được ghi trạng thái bền, (2) **màn chính phải giữ mỏng**, không chứa việc điều phối khởi
     * động. [ĐO] 2026-09-11: tôi thử cho vòng kiểm tự gieo cờ và **cả hai guard đều bắt** (`PersistentWindowState
     * WriterGuardTest` rồi `KachiAutostartServiceWiringTest`). Kết luận đúng: vòng kiểm chỉ **BÁO**, đường khởi động
     * mới **SỬA**. Nếu tới màn chính mà vẫn thiếu �⇒ việc gieo lúc khởi động đã không ăn, và đó là tin đáng nói.
     */
    SELF_AT_BOOT,

    /** Chỉ người dùng đổi được (vd chọn màn hình chính) ⇒ nói rõ việc cần làm, KHÔNG chỉ tới màn cài đặt hệ thống. */
    USER,

    /** Không ai trong app sửa được (vd kênh shell chưa nối trên máy ảo) ⇒ nói rõ là hạn chế MÔI TRƯỜNG, không phải lỗi. */
    ENVIRONMENT,
}

/**
 * Một điều kiện launcher phụ thuộc.
 *
 * @property id mã ổn định để test/nhật ký chỉ đích danh.
 * @property label tên cho người đọc.
 * @property losesWhatIfMissing thiếu thì **mất gì** — bắt buộc, vì câu chung "thiếu quyền" không giúp ai (R3).
 * @property fixBy ai sửa được.
 * @property userAction việc người dùng cần làm; CHỈ dùng khi [fixBy] = [FixBy.USER]. Không được là "mở màn cài đặt".
 * @property coreFeature `true` = thiếu là **mất tính năng lõi** của launcher (app vào ô) ⇒ đáng hiện ra màn chính.
 */
data class LauncherRequirement(
    val id: String,
    override val label: String,
    val losesWhatIfMissing: String,
    val fixBy: FixBy,
    val userAction: String? = null,
    val coreFeature: Boolean = false,
    /** Nhãn tiếng Anh (U5 · T2). */
    override val labelEn: String? = null,
    /**
     * [losesWhatIfMissing] bằng tiếng Anh.
     *
     * Phải có, không được để trống rồi lùi: đây là **nửa mang thông tin** của câu báo ([PermissionReport.notice] ghép
     * `"nhãn: mất gì"`). Lùi nửa sau về tiếng Việt sẽ ra một câu **trộn hai thứ tiếng trong cùng một dòng** — tệ hơn
     * hẳn cả hai lựa chọn thuần. `LangCoverageTest` đòi đủ 6 điều kiện.
     */
    val losesWhatIfMissingEn: String? = null,
    /** [userAction] bằng tiếng Anh — chỉ điều kiện [FixBy.USER] mới có. */
    val userActionEn: String? = null,
) : Localized {
    /** "Mất gì nếu thiếu" theo [Strings.current]. */
    val displayLoses: String get() = Strings.pick(losesWhatIfMissing, losesWhatIfMissingEn)

    /** Việc người dùng cần làm, theo [Strings.current]; `null` khi điều kiện không cần người dùng. */
    val displayUserAction: String? get() = userAction?.let { Strings.pick(it, userActionEn) }
}

/** Trạng thái đọc được của một điều kiện. */
enum class RequirementState {
    /** Đủ. */ OK,
    /** Thiếu. */ MISSING,
    /** Không đọc được (ROM thiếu API / lỗi khi đọc) ⇒ **không kết luận là thiếu**, xem [PermissionReport]. */ UNKNOWN,
}

/** Kết quả của một điều kiện sau khi đọc. */
data class RequirementResult(val requirement: LauncherRequirement, val state: RequirementState)

/**
 * Báo cáo cả vòng kiểm.
 *
 * ⚠ [UNKNOWN][RequirementState.UNKNOWN] **KHÔNG** bị tính là thiếu: một số ROM không có API để đọc (code cũ đã bọc
 * `runCatching` đúng vì lý do đó). Đoán là thiếu rồi đi xin lại sẽ tạo nhiễu đúng vào lúc đang test trên xe — trái
 * hẳn mục đích của việc này.
 */
data class PermissionReport(val results: List<RequirementResult>) {

    val missing: List<LauncherRequirement> get() = results.filter { it.state == RequirementState.MISSING }.map { it.requirement }
    val unknown: List<LauncherRequirement> get() = results.filter { it.state == RequirementState.UNKNOWN }.map { it.requirement }

    /** Đủ hết (không thiếu gì; chưa đọc được thì KHÔNG tính là thiếu). */
    val allOk: Boolean get() = missing.isEmpty()

    /** Thiếu mà vòng kiểm **tự xin lại NGAY được** — chỗ gọi ở `:app` chỉ cần lặp trên danh sách này. */
    val selfFixable: List<LauncherRequirement> get() = missing.filter { it.fixBy == FixBy.SELF }

    /**
     * Thiếu mà **đường khởi động** lo, không phải vòng kiểm. Tới màn chính mà vẫn thiếu nghĩa là việc lúc khởi động
     * **đã không ăn** ⇒ đáng nói ra, vì người dùng không có cách nào tự biết.
     */
    val fixedAtBoot: List<LauncherRequirement> get() = missing.filter { it.fixBy == FixBy.SELF_AT_BOOT }

    /** Thiếu mà **phải người dùng** làm. */
    val needsUser: List<LauncherRequirement> get() = missing.filter { it.fixBy == FixBy.USER }

    /** Thiếu do **môi trường** — không phải lỗi, nói ra để người test khỏi đi tìm bug không tồn tại. */
    val environment: List<LauncherRequirement> get() = missing.filter { it.fixBy == FixBy.ENVIRONMENT }

    /** Thiếu và **mất tính năng lõi** (app vào ô) ⇒ đáng hiện ra màn chính. */
    val missingCore: List<LauncherRequirement> get() = missing.filter { it.coreFeature }

    /**
     * Câu báo cho người dùng, hoặc `null` khi **đủ hết** — *đủ thì IM LẶNG* (R2). Không ai muốn bị thông báo về
     * chuyện đang hoạt động bình thường.
     *
     * Nêu **đích danh** mục thiếu kèm **mất gì** (R3), không phải câu chung "thiếu quyền".
     *
     * @param coreOnly `true` ⇒ chỉ nói mục làm **mất tính năng lõi** (app vào ô). Đây là chế độ màn HOME dùng: thiếu
     *   mục nhỏ mà báo mỗi lần mở launcher là nhiễu — đúng thứ vòng kiểm này đi dọn. `false` (mặc định) ⇒ nói đủ
     *   bức tranh, cho bề mặt xem chi tiết.
     *
     * ⚠ [SOÁT P3] Trước bản này màn HOME **tự ghép chuỗi** từ `missingCore` nên `notice()` không ai gọi (mã chết ở
     * sản phẩm) và câu chữ người dùng đọc lại nằm ở tầng UI — hai nơi có thể nói khác nhau. Tôi đã thử cho HOME gọi
     * thẳng `notice()`, nhưng [ĐO] test bắt được ngay: `notice()` nói RỘNG hơn `missingCore` ⇒ launcher sẽ ồn hơn
     * thiết kế. Vậy nên thêm tham số thay vì đổi ngữ nghĩa: một hàm, hai chế độ, không còn mã chết.
     */
    fun notice(coreOnly: Boolean = false): String? {
        // Chỉ nói về phần người dùng cần biết: cái tự sửa được thì launcher tự làm, nói ra chỉ gây lo.
        // (Không cần kiểm `allOk` riêng: đủ hết ⇒ hai danh sách dưới đều rỗng. [ĐO] thử phá cho thấy dòng kiểm đó
        // là DÒNG CHẾT — bỏ nó không test nào đỏ, nên bỏ luôn thay vì giữ code không ai chạy tới.)
        // SELF_AT_BOOT có mặt ở đây vì tới màn chính mà còn thiếu = việc lúc khởi động đã không ăn.
        val speak = if (coreOnly) missingCore else needsUser + environment + fixedAtBoot
        if (speak.isEmpty()) return null
        return speak.joinToString(" · ") { "${it.displayLabel}: ${it.displayLoses}" }
    }

    /**
     * Một dòng cho nhật ký (kể cả phần tự sửa) — để buổi test trên xe đọc log là biết ngay.
     *
     * ⚠ **CỐ Ý KHÔNG dịch** (U5 · T2): đây là dòng cho **người phát triển**, không phải cho người lái. Nhật ký của
     * dự án đang được đọc/grep bằng chính những chữ này ở nhiều tài liệu chẩn đoán; đổi theo ngôn ngữ máy sẽ làm log
     * của hai lần đo không so được với nhau. Cũng vì thế nó gọi mã (`it.id`) chứ không gọi nhãn.
     */
    fun logLine(): String = when {
        allOk && unknown.isEmpty() -> "đủ quyền"
        allOk -> "đủ quyền (chưa đọc được: ${unknown.joinToString(", ") { it.id }})"
        else -> "thiếu: ${missing.joinToString(", ") { it.id }}" +
            (if (selfFixable.isNotEmpty()) " | tự xin: ${selfFixable.joinToString(", ") { it.id }}" else "") +
            (if (unknown.isNotEmpty()) " | chưa đọc được: ${unknown.joinToString(", ") { it.id }}" else "")
    }
}

/**
 * DANH MỤC điều kiện — **một chỗ duy nhất** biết launcher cần gì (R1).
 *
 * Chỉ gồm thứ launcher **thật sự dùng** (R7): không gom quyền của phần ClusterNav cũ vào đây.
 */
object LauncherRequirements {

    /** Đọc thông báo — nguồn dữ liệu dẫn đường. Quyền kiểu ADB ⇒ tự cấp được. */
    val NOTIFICATION_LISTENER = LauncherRequirement(
        id = "notif_listener",
        label = "Đọc thông báo",
        losesWhatIfMissing = "dẫn đường trên cụm mất nguồn dữ liệu",
        fixBy = FixBy.SELF,
        labelEn = "Notification access",
        losesWhatIfMissingEn = "cluster navigation loses its data source",
    )

    /** Trợ năng — bắt phím vô-lăng + đọc màn hình dẫn đường. Quyền kiểu ADB ⇒ tự cấp được. */
    val ACCESSIBILITY = LauncherRequirement(
        id = "accessibility",
        label = "Trợ năng",
        losesWhatIfMissing = "gán phím vô-lăng và đọc màn hình dẫn đường không chạy",
        fixBy = FixBy.SELF,
        labelEn = "Accessibility",
        losesWhatIfMissingEn = "steering-wheel key mapping and navigation screen reading stop working",
    )

    /** Vẽ trên màn khác — dải tiêu đề ô + ngăn kéo app nổi lên trên app đang chiếu trong ô. */
    val OVERLAY = LauncherRequirement(
        id = "overlay",
        label = "Vẽ trên màn khác",
        losesWhatIfMissing = "dải tiêu đề ô và ngăn kéo app không nổi lên được",
        fixBy = FixBy.SELF,
        labelEn = "Draw over other apps",
        losesWhatIfMissingEn = "slot title bars and the app drawer cannot float on top",
    )

    /** Cho phép cửa sổ tự do — đường đưa app vào ô. */
    val FREEFORM = LauncherRequirement(
        id = "freeform",
        label = "Cho phép cửa sổ tự do",
        losesWhatIfMissing = "app không vào được ô theo đường cửa sổ tự do",
        // KHÔNG phải SELF: cờ này do đường khởi động gieo (một-nơi-ghi-duy-nhất), màn chính không được tự gieo.
        fixBy = FixBy.SELF_AT_BOOT,
        coreFeature = true,
        labelEn = "Freeform windows enabled",
        losesWhatIfMissingEn = "apps cannot go into a slot over the freeform-window path",
    )

    /**
     * Kênh shell — **tính năng lõi**. Trên xe có sẵn; trên máy ảo cần nối một lần mỗi lần khởi động máy.
     * Không tự sửa được từ trong app ⇒ [FixBy.ENVIRONMENT].
     */
    val SHELL_CHANNEL = LauncherRequirement(
        id = "shell",
        label = "Kênh điều khiển cửa sổ",
        losesWhatIfMissing = "app không vào được ô — đây là tính năng lõi của launcher",
        fixBy = FixBy.ENVIRONMENT,
        coreFeature = true,
        labelEn = "Window control channel",
        losesWhatIfMissingEn = "apps cannot go into a slot — this is the launcher's core feature",
    )

    /**
     * Là màn hình chính. **KHÔNG** khai cách sửa là "mở màn cài đặt" — [ĐO] màn cài đặt hệ thống trên xe bị khoá
     * (chỉ nhận *"Hệ thống IVI không hỗ trợ hoạt động này"*). Cách dùng được: bấm nút HOME rồi chọn Kachi ở hộp
     * chọn của hệ thống.
     */
    val DEFAULT_HOME = LauncherRequirement(
        id = "default_home",
        label = "Là màn hình chính",
        losesWhatIfMissing = "bấm HOME không về Kachi",
        fixBy = FixBy.USER,
        userAction = "Bấm nút HOME rồi chọn Kachi trong hộp chọn của hệ thống",
        labelEn = "Set as home screen",
        losesWhatIfMissingEn = "pressing HOME does not come back to Kachi",
        userActionEn = "Press the HOME button, then pick Kachi in the system chooser",
    )

    /**
     * V1 pha NGHE — **micro**. Quyền RUNTIME, nhưng cấp được y như ba quyền kiểu ADB kia: `pm grant` qua kênh
     * dadb loopback (uid shell) ⇒ [FixBy.SELF], không phải hộp hỏi quyền.
     *
     * ## Vì sao KHÔNG phải `coreFeature`
     * Thiếu micro thì Kachi **vẫn là một launcher đầy đủ**: mọi nút vẫn bấm được, mọi app vẫn vào ô. Chỉ mất một
     * lối tắt. Gắn `coreFeature = true` sẽ làm toast ở màn chính nổ trên mọi đầu xe không có micro (và trên máy
     * ảo) — đúng loại nhiễu mà vòng kiểm P8 sinh ra để dọn (xem KDoc `PermissionPreflight.noticeShown`).
     */
    val MICROPHONE = LauncherRequirement(
        id = "microphone",
        label = "Micro",
        losesWhatIfMissing = "không nói được với xe (ô \"Nói với xe\" và nút mic)",
        fixBy = FixBy.SELF,
        labelEn = "Microphone",
        losesWhatIfMissingEn = "you cannot talk to the car (the \"Talk to car\" tile and the mic button)",
    )

    /** Thứ tự khai = thứ tự hiện cho người dùng. */
    val ALL: List<LauncherRequirement> = listOf(
        SHELL_CHANNEL, FREEFORM, DEFAULT_HOME, OVERLAY, NOTIFICATION_LISTENER, ACCESSIBILITY, MICROPHONE,
    )

    fun byId(id: String): LauncherRequirement? = ALL.firstOrNull { it.id == id }

    /**
     * Dựng báo cáo từ hàm đọc trạng thái. [read] do `:app` cung cấp (đọc cấu hình hệ thống thật); trả `null` nghĩa là
     * **không đọc được** ⇒ [RequirementState.UNKNOWN], KHÔNG suy ra là thiếu.
     */
    fun check(read: (LauncherRequirement) -> Boolean?): PermissionReport =
        PermissionReport(
            ALL.map { req ->
                val v = runCatching { read(req) }.getOrNull()
                RequirementResult(
                    req,
                    when (v) {
                        true -> RequirementState.OK
                        false -> RequirementState.MISSING
                        null -> RequirementState.UNKNOWN
                    },
                )
            },
        )
}
