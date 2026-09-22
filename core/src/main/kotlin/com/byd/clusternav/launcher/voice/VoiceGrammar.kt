package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.Localized
import com.byd.clusternav.launcher.TelemetryRegistry

/** Động từ mà bộ phân tích nhận ra. Bộ **nhỏ và đều** — đúng hình dạng đã đo ở Kiki (RE §7c). */
enum class VoiceVerb { ON, OFF, OPEN, CLOSE, UP, DOWN, SET, READ, SWITCH, NAV, PLAY, PAUSE, NEXT, PREV }

/** Loại đích của một cụm từ trong từ vựng — quyết định ý định nào được dựng ra. */
enum class VoiceTermKind { CONTROL, TELEMETRY, MACRO, LAUNCHER, PROFILE, APP, MEDIA, NAV }

/**
 * Một cụm từ **đã chuẩn hoá** trỏ tới một đích.
 * @property words các từ đã bỏ dấu (khớp theo dãy); dài hơn ⇒ được xét trước (xem [VoiceGrammar.matchAt]).
 */
data class VoiceTerm(val words: List<String>, val kind: VoiceTermKind, val id: String)

/**
 * ═══ V1 · TỪ VỰNG **SINH TỪ BỘ ĐĂNG KÝ** ══════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R2. Thuần Kotlin (`:core`).
 *
 * ## Ràng buộc số một: KHÔNG chép tay nhãn
 * 65 nút + 123 datum + 4 gói lệnh + 2 hành động launcher, mỗi cái đã có nhãn VI, nhãn EN và (một số) nhãn ngắn hai
 * thứ tiếng. Chép chúng sang một bảng "câu lệnh" là dựng **bản sao thứ hai của nhãn** — đúng họ lỗi mà dự án đã
 * trả giá bốn lần (`unitPrefs` ×4, `customLayout` ×2): ai đó đổi nhãn nút, màn hình đổi, còn câu lệnh thì không, và
 * **không gì báo lỗi**. Ở đây từ vựng được **sinh** mỗi lần gọi từ chính bộ đăng ký ⇒ thêm một dòng registry là
 * tự nhiên nói được, xoá một dòng là tự nhiên hết nói.
 *
 * ## Vì sao khớp theo DÃY TỪ, dài trước ngắn
 * [ĐO] `docs/PROJECT-BACKLOG.md` L-RE2 — có ba cặp nút mà nhãn cái này **chứa** nhãn cái kia: *"Chế độ đèn pha"* ⊃
 * *"Đèn pha"*, *"Độ sáng HUD"* vs *"Độ sáng màn"*, *"Góc camera"* vs *"Camera 360"*. Khớp từ-đơn hoặc khớp ngắn
 * trước thì *"chế độ đèn pha auto"* sẽ bật/tắt đèn pha — sai nút, và là nút ảnh hưởng tầm nhìn ban đêm. Luật
 * **dãy dài nhất thắng** giải cả ba cặp mà không cần một dòng `if` nào cho từng cặp (CLAUDE.md §7).
 */
object VoiceGrammar {

    /**
     * Bảng ĐỘNG TỪ — cụm dài đặt trước (khớp dài nhất thắng, cùng luật với [matchAt]).
     *
     * *"Mở"* cố ý **không** có nghĩa cố định: nó vào đây là [VoiceVerb.OPEN], rồi [VoiceIntentParser] mới quyết
     * theo **kiểu đối tượng** (cửa/kính = COVER · app = mở app · Cài đặt = launcher · nhạc = phát). Đây là ràng
     * buộc thiết kế lấy thẳng từ RE Kiki §7(c): *"Mở bị quá tải nặng ⇒ phân biệt bằng KIỂU ĐỐI TƯỢNG, không bằng
     * động từ"*.
     */
    val VERBS: List<Pair<List<String>, VoiceVerb>> = listOf(
        listOf("dan", "duong", "den") to VoiceVerb.NAV,
        listOf("dan", "duong", "toi") to VoiceVerb.NAV,
        listOf("chi", "duong", "den") to VoiceVerb.NAV,
        listOf("chi", "duong", "toi") to VoiceVerb.NAV,
        listOf("tim", "duong", "den") to VoiceVerb.NAV,
        listOf("dan", "duong") to VoiceVerb.NAV,
        listOf("chi", "duong") to VoiceVerb.NAV,
        listOf("navigate", "to") to VoiceVerb.NAV,
        listOf("directions", "to") to VoiceVerb.NAV,
        // ⚠⚠ *"về nhà"* · *"đi làm"* · *"đến công ty"* CỐ Ý **không** có mặt trong bảng này — xem
        // [VoicePlaces.PLACE_VERBS]. Thêm `ve`/`di`/`den` vào đây là biến chúng thành động từ dẫn đường **vô điều
        // kiện**, và [ĐO] ngay trong bộ test đang có: *"về bố cục 2 cột"* (một câu cố ý để NO_VERB, spec §7 OQ1)
        // sẽ thành *"dẫn đường tới «bố cục 2 cột»"* — máy mở app bản đồ tìm một cái tên vô nghĩa thay vì nói thẳng
        // là chưa làm được. Ba từ ấy quá thường để mang một nghĩa cố định; chúng chỉ có nghĩa dẫn đường khi phần
        // đuôi **thật sự là một nơi trong sổ**, và đó đúng là điều kiện mà [VoicePlaces.PLACE_VERBS] gác.
        listOf("bai", "tiep", "theo") to VoiceVerb.NEXT,
        listOf("bai", "ke", "tiep") to VoiceVerb.NEXT,
        listOf("chuyen", "bai") to VoiceVerb.NEXT,
        // [SOÁT P2] *"bài tiếp"* (không có chữ *"theo"*) rơi vào NO_VERB: cụm *"bai"* là một từ khoá NHẠC, mà
        // `headMatch` cố ý chỉ nhận MACRO/CONTROL/LAUNCHER ⇒ không ai đỡ. Nó là cách nói ngắn phổ biến nhất.
        listOf("bai", "tiep") to VoiceVerb.NEXT,
        listOf("tiep", "theo") to VoiceVerb.NEXT,
        listOf("next", "track") to VoiceVerb.NEXT,
        listOf("bai", "truoc") to VoiceVerb.PREV,
        listOf("quay", "lai", "bai") to VoiceVerb.PREV,
        listOf("previous", "track") to VoiceVerb.PREV,
        listOf("tam", "dung") to VoiceVerb.PAUSE,
        listOf("doi", "sang") to VoiceVerb.SWITCH,
        listOf("chuyen", "sang") to VoiceVerb.SWITCH,
        listOf("kiem", "tra") to VoiceVerb.READ,
        listOf("cho", "xem") to VoiceVerb.READ,
        listOf("doc", "to") to VoiceVerb.READ,
        listOf("turn", "on") to VoiceVerb.ON,
        listOf("turn", "off") to VoiceVerb.OFF,
        listOf("switch", "to") to VoiceVerb.SWITCH,
        listOf("bat") to VoiceVerb.ON,
        listOf("on") to VoiceVerb.ON,
        listOf("enable") to VoiceVerb.ON,
        listOf("tat") to VoiceVerb.OFF,
        listOf("off") to VoiceVerb.OFF,
        listOf("disable") to VoiceVerb.OFF,
        listOf("mo") to VoiceVerb.OPEN,
        listOf("open") to VoiceVerb.OPEN,
        // V1.1 — *"đưa YouTube vào ô số 2"*. Người ta nói *"đưa … vào …"* nhiều hơn *"mở … vào …"* khi ý là
        // GẮN chứ không phải MỞ. Không đụng nhãn nào: không cụm nào trong từ vựng bắt đầu bằng `dua`.
        listOf("dua") to VoiceVerb.OPEN,
        listOf("dong") to VoiceVerb.CLOSE,
        listOf("close") to VoiceVerb.CLOSE,
        listOf("tang") to VoiceVerb.UP,
        listOf("increase") to VoiceVerb.UP,
        listOf("raise") to VoiceVerb.UP,
        listOf("giam") to VoiceVerb.DOWN,
        listOf("decrease") to VoiceVerb.DOWN,
        listOf("lower") to VoiceVerb.DOWN,
        listOf("dat") to VoiceVerb.SET,
        listOf("chinh") to VoiceVerb.SET,
        listOf("set") to VoiceVerb.SET,
        listOf("xem") to VoiceVerb.READ,
        listOf("coi") to VoiceVerb.READ,
        listOf("coi", "thu") to VoiceVerb.READ,
        listOf("doc") to VoiceVerb.READ,
        listOf("hien") to VoiceVerb.READ,
        listOf("show") to VoiceVerb.READ,
        listOf("read") to VoiceVerb.READ,
        listOf("check") to VoiceVerb.READ,
        listOf("chuyen") to VoiceVerb.SWITCH,
        listOf("doi") to VoiceVerb.SWITCH,
        listOf("switch") to VoiceVerb.SWITCH,
        listOf("phat") to VoiceVerb.PLAY,
        listOf("nghe") to VoiceVerb.PLAY,
        listOf("play") to VoiceVerb.PLAY,
        listOf("dung") to VoiceVerb.PAUSE,
        listOf("pause") to VoiceVerb.PAUSE,
        listOf("stop") to VoiceVerb.PAUSE,
        listOf("tiep") to VoiceVerb.NEXT,
        listOf("next") to VoiceVerb.NEXT,
        listOf("truoc") to VoiceVerb.PREV,
        listOf("previous") to VoiceVerb.PREV,
    ).sortedByDescending { it.first.size }

    /** Động từ nào là *"đọc thông tin"* — dùng để chọn ứng viên khi một cụm trỏ tới CẢ datum lẫn nút. */
    fun isRead(v: VoiceVerb): Boolean = v == VoiceVerb.READ

    /** Động từ nào là *"làm gì đó với xe/app"*. */
    fun isAction(v: VoiceVerb): Boolean = !isRead(v)

    /**
     * ═══ [SOÁT 1.69 · P1] Cụm này có ĐỌC phần đuôi câu không ═════════════════════════════════════════════
     *
     * Trả lời đúng một câu hỏi: khi [VoiceIntentParser] dựng ý định cho cụm này, **phần câu còn lại phía sau
     * nó có được nhìn tới không**. Đọc thẳng các nhánh của `VoiceIntentParser.build`/`.control`, không khai
     * lại một bảng thứ hai (hai bản sao là hai bản sẽ lệch — CLAUDE.md §4.1).
     *
     * ## Bệnh nó chữa — [ĐO off-car 2026-09-17]
     * Nhánh *"cả câu chính là TÊN của một việc"* gắn một **động từ ngầm** cho cụm khớp tại vị trí 0. Nhưng mã
     * không hề đòi cụm ấy chiếm cả câu: chỉ cần câu **bắt đầu** bằng một cụm của bộ đăng ký là phần đuôi bị bỏ
     * đi và một HÀNH ĐỘNG được bắn ra. Bộ đăng ký lại có **29** cụm MỘT từ hạng CONTROL/MACRO/LAUNCHER mà sau
     * khi bỏ dấu trùng đúng tiếng Việt đời thường (`cop` · `kinh` · `gio` · `chieu` · `quat` · `tieng` · `ac`),
     * cộng những nhãn hai từ vốn cũng là danh ngữ (*"cốp xe"*). Ba câu đo được, trên xe đang chạy:
     *  • *"chiều nay mấy giờ về"* ⇒ `Control(cast, 1)` — một câu hỏi người nhà thành lệnh chiếu cụm;
     *  • *"cốp xe bẩn quá"* ⇒ `Control(trunk, 1)` — **mở cốp**;
     *  • *"kính bẩn quá"* ⇒ `Control(windows_all, 1)` — **hạ hết kính**.
     *
     * ## Vì sao cổng đóng ở ĐÚNG giao của hai điều kiện
     * Chỗ gọi chỉ từ chối khi **không có động từ** *và* cụm **không đọc đuôi**. Có động từ thì người lái đã nói
     * rõ mình muốn làm gì, đuôi thừa không đổi ý định ấy (*"mở hết kính ra"* vẫn chạy). Cụm có đọc đuôi thì
     * đuôi đã là **đối số thật** và tự nó chứng minh câu là một lệnh — *"nhiệt độ hai mươi bốn độ"* (STEP tra
     * số) · *"chế độ lái thể thao"* (SELECT tra nhãn) · *"ứng dụng VTV Go"* (LAUNCHER tra tên app). Siết rộng
     * hơn thì ba họ câu ấy chết theo; siết hẹp hơn thì ba câu đo được ở trên vẫn bắn lệnh.
     */
    fun readsTail(term: VoiceTerm): Boolean = when (term.kind) {
        // *"Mở ứng dụng VTV Go"* — đuôi quyết định app nào (nhánh LAUNCHER của `build`).
        VoiceTermKind.LAUNCHER -> true
        VoiceTermKind.CONTROL -> when (ControlRegistry.byId(term.id)?.kind) {
            // SELECT tra nhãn lựa chọn trong đuôi; STEP tra con số. Cả hai tự trả MISMATCH khi đuôi không cho
            // gì dùng được, nên chúng không bao giờ lặng lẽ bắn một hành động.
            ControlKind.SELECT, ControlKind.STEP -> true
            // TOGGLE · COVER · BUTTON: `control()` trả thẳng `Control(id, 1)` / `Control(id, null)` — `after`
            // không xuất hiện một lần nào. Đây đúng là họ nút đụng THÂN XE (cốp · kính · khoá · nắp ca-pô).
            else -> false
        }
        // MACRO: `build` trả thẳng `Macro(id)`, không nhìn `after`.
        else -> false
    }

    /**
     * TOÀN BỘ từ vựng đối tượng, **sinh ra** từ 4 bộ đăng ký + [VoiceSynonyms] + danh sách động (hồ sơ, app).
     *
     * @param profiles tên hồ sơ tài xế đang có (`HomeUiState.profiles`) — danh sách **động**, không thể sinh từ
     *   registry vì người dùng tự đặt tên.
     * @param apps nhãn ứng dụng đã cài. Cũng động, và cũng là lý do bộ phân tích **không** hardcode tên gói nào
     *   (CLAUDE.md §7): app nào có mặt thì gọi được app đó, không app nào được viết cứng vào mã.
     */
    fun terms(profiles: List<String> = emptyList(), apps: List<String> = emptyList()): List<VoiceTerm> {
        if (profiles.isEmpty() && apps.isEmpty()) return STATIC_SORTED
        val dyn = ArrayList<VoiceTerm>(profiles.size + apps.size)
        profiles.forEach { p -> term(p, VoiceTermKind.PROFILE, p)?.let { dyn.add(it) } }
        apps.forEach { a -> term(a, VoiceTermKind.APP, a)?.let { dyn.add(it) } }
        // Thứ tự ghép giữ NGUYÊN như bản dựng-mỗi-lần: …registry → hồ sơ → app → từ khoá nhạc/dẫn đường. Nó là
        // thứ tự phân xử khi hai cụm **bằng nhau về độ dài** (`VoiceIntentParser.choose` lấy phần tử đầu), nên
        // đảo nó là lặng lẽ đổi cách hiểu của một câu.
        return (STATIC_HEAD + dyn + STATIC_TAIL).distinct().sortedByDescending { it.words.size }
    }

    /** Một cụm đã chuẩn hoá, hoặc `null` nếu chuỗi không còn từ nào sau khi tách (vd tên app chỉ có ký hiệu). */
    private fun term(phrase: String, kind: VoiceTermKind, id: String): VoiceTerm? =
        VoiceLexicon.tokenize(phrase).map { it.norm }.takeIf { it.isNotEmpty() }?.let { VoiceTerm(it, kind, id) }

    /**
     * Phần từ vựng **không đổi trong suốt đời tiến trình** — 4 bộ đăng ký + [VoiceSynonyms], dựng MỘT lần.
     *
     * ## [SOÁT P1] Vì sao phải `lazy`, không dựng lại mỗi câu
     * Mỗi lần dựng là ~600 cụm × (`Regex.split` + `Normalizer.normalize` NFD + duyệt ký tự). Bản đầu gọi nó
     * **trong** [VoiceIntentParser.parse], tức mỗi câu gõ vào là một lần dựng lại toàn bộ — và chỗ gọi là **thread
     * giao diện** (nút *"Chạy câu lệnh"*). Danh mục thì đứng yên (`ControlRegistry.ALL` là hằng), nên đó là công
     * làm lại y nguyên. Phần THẬT SỰ động (hồ sơ, app đã cài) vẫn dựng mỗi lần — nó rẻ (vài cụm) và nó phải mới.
     *
     * `by lazy` mặc định là `SYNCHRONIZED` ⇒ an toàn khi bộ phân tích bị gọi từ nhiều thread (màn thử gọi trên
     * thread giao diện, gói lệnh chạy trên thread nền).
     */
    private val STATIC_HEAD: List<VoiceTerm> by lazy {
        val out = ArrayList<VoiceTerm>(600)
        fun add(phrase: String, kind: VoiceTermKind, id: String) { term(phrase, kind, id)?.let { out.add(it) } }
        fun addLocalized(l: Localized, id: String, kind: VoiceTermKind, extraShort: List<String?> = emptyList()) {
            add(l.label, kind, id)
            l.labelEn?.let { add(it, kind, id) }
            extraShort.filterNotNull().forEach { add(it, kind, id) }
        }

        ControlRegistry.ALL.forEach { c ->
            addLocalized(c, c.id, VoiceTermKind.CONTROL, listOf(c.short, c.shortEn))
            VoiceSynonyms.CONTROL[c.id]?.forEach { add(it, VoiceTermKind.CONTROL, c.id) }
        }
        TelemetryRegistry.ALL.forEach { t ->
            addLocalized(t, t.id, VoiceTermKind.TELEMETRY, listOf(t.short, t.shortEn))
            VoiceSynonyms.TELEMETRY[t.id]?.forEach { add(it, VoiceTermKind.TELEMETRY, t.id) }
        }
        ActionMacros.ALL.forEach { m -> addLocalized(m, m.id, VoiceTermKind.MACRO) }
        LauncherActions.ALL.forEach { a -> addLocalized(a, a.id, VoiceTermKind.LAUNCHER) }
        out
    }

    /** Từ khoá LOẠI (nhạc / điểm đến) — đứng CUỐI như bản dựng-mỗi-lần, xem ghi chú thứ tự ở [terms]. */
    private val STATIC_TAIL: List<VoiceTerm> by lazy {
        val out = ArrayList<VoiceTerm>(VoiceSynonyms.MEDIA_WORDS.size + VoiceSynonyms.NAV_WORDS.size)
        VoiceSynonyms.MEDIA_WORDS.forEach { w -> term(w, VoiceTermKind.MEDIA, w)?.let { out.add(it) } }
        VoiceSynonyms.NAV_WORDS.forEach { w -> term(w, VoiceTermKind.NAV, w)?.let { out.add(it) } }
        out
    }

    /**
     * Từ vựng khi KHÔNG có hồ sơ/app nào (mọi bài kiểm thuần, và ca xe chưa nạp xong danh sách app).
     *
     * Dài trước ngắn — xem KDoc lớp (L-RE2). `distinct()` vì nhãn EN có thể trùng nhãn VI (vd "EV / HEV").
     */
    private val STATIC_SORTED: List<VoiceTerm> by lazy {
        (STATIC_HEAD + STATIC_TAIL).distinct().sortedByDescending { it.words.size }
    }

    /**
     * Mọi cụm khớp tại đúng vị trí [i] của [t], **theo thứ tự dài → ngắn**.
     *
     * Trả về cả danh sách (không chỉ cái đầu) vì một cụm có thể trỏ tới nhiều đích cùng lúc — [ĐO] 18 nhãn trùng
     * giữa mục ĐỌC và HÀNH ĐỘNG (`CapabilityCatalog.collidingLabels`, vd *"Kính trước-trái"* vừa là datum % mở vừa
     * là nút đóng/mở). Ai chọn trong số đó là việc của [VoiceIntentParser], vì chỉ nó mới biết động từ.
     */
    fun matchAt(t: List<VoiceLexicon.Token>, i: Int, terms: List<VoiceTerm>): List<VoiceTerm> =
        terms.filter { VoiceLexicon.phraseAt(t, i, it.words) }

    /** Động từ (hoặc từ mở đầu một cụm lệnh) đi cùng multi-command split — xem [ACTION_VERB_HEADS]. */
    private val ACTION_VERBS = setOf(
        VoiceVerb.ON, VoiceVerb.OFF, VoiceVerb.OPEN, VoiceVerb.CLOSE, VoiceVerb.UP, VoiceVerb.DOWN, VoiceVerb.SET,
    )

    /**
     * Từ MỞ ĐẦU một cụm lệnh mà [VERBS] không khai (chúng resolve qua luật scoped / synonym): "hạ"/"kéo"/"nâng"
     * (hướng kính) · "lấy" ("lấy gió ngoài/trong"). Dùng cho [actionVerbAt] để tách câu MIX không liên từ.
     */
    private val ACTION_VERB_HEADS = setOf("ha", "keo", "nang", "lay", "chuyen")

    /**
     * Vị trí [i] có phải ĐẦU một cụm lệnh HÀNH ĐỘNG không — cho `VoiceIntentParser.multiVerbSplit` tách câu MIX
     * không liên từ ("hạ kính lấy gió ngoài tắt máy lạnh"). Nhận [VERBS] có [VoiceVerb] hành động (không NAV/READ/
     * PLAY — những cái ấy hiếm ghép kiểu này và dễ cắt nhầm tên bài/điểm đến), cộng [ACTION_VERB_HEADS].
     *
     * ⚠ An toàn nằm ở CHỖ GỌI (mọi vế phải parse hiểu được), nên ở đây được phép rộng tay.
     */
    fun actionVerbAt(t: List<VoiceLexicon.Token>, i: Int): Boolean {
        // ⚠ "tất cả" bỏ dấu = "tat ca" — "tat" TRÙNG "tắt" (OFF). "mở TẤT CẢ kính" không được coi "tất" là ranh
        // giới lệnh, nếu không "mở tất cả kính" bị cắt thành "mở" + "tất cả kính" (vế "mở" rỗng nghĩa ⇒ hỏng).
        if (t.getOrNull(i)?.norm == "tat" && t.getOrNull(i + 1)?.norm == "ca") return false
        // ACTION_VERB_HEADS xét TRƯỚC: "chuyen" cũng là VERB SWITCH ("chuyển hồ sơ") nên nếu hỏi VERBS trước thì
        // "chuyển gió ngoài" không bao giờ thành ranh giới. An toàn nhờ cổng chỗ gọi (mọi vế phải hiểu được).
        if (t.getOrNull(i)?.norm in ACTION_VERB_HEADS) return true
        VERBS.firstOrNull { VoiceLexicon.phraseAt(t, i, it.first) }?.let { return it.second in ACTION_VERBS }
        return false
    }

    /**
     * H4 — [terms] cộng thêm các cụm **nghe nhầm** mà câu [tokens] đủ NGỮ CẢNH để bật ([VoiceSynonyms.MISHEARD]).
     *
     * Điều kiện đọc trên **cả câu**, không đọc trên từng vị trí: nó trả lời câu hỏi *"câu này đang nói về lọc bụi
     * chứ?"*, mà câu hỏi ấy không thể trả lời bằng một cửa sổ hai từ. Câu không có từ ngữ cảnh nào thì trả về
     * **đúng** danh sách cũ (không cấp phát, không sắp xếp lại) — tức mọi câu đang chạy tốt không đụng tới.
     */
    fun plusMisheard(terms: List<VoiceTerm>, tokens: List<VoiceLexicon.Token>): List<VoiceTerm> {
        val norms = tokens.mapTo(HashSet(tokens.size)) { it.norm }
        val extra = VoiceSynonyms.MISHEARD
            .filter { m -> m.context.any { it in norms } }
            .map { VoiceTerm(it.words, kindOf(it.id), it.id) }
        if (extra.isEmpty()) return terms
        return (terms + extra).distinct().sortedByDescending { it.words.size }
    }

    /** Mã của [VoiceSynonyms.MISHEARD] là mã nút hay mã datum — hỏi thẳng bộ đăng ký, không khai loại hai lần. */
    private fun kindOf(id: String): VoiceTermKind =
        if (ControlRegistry.byId(id) != null) VoiceTermKind.CONTROL else VoiceTermKind.TELEMETRY

    /**
     * ═══ PHA NGHE · cùng danh mục này, nhưng viết cho **bộ nhận dạng** ════════════════════════════════════════
     *
     * [terms] trả từ vựng **đã bỏ dấu** để so khớp chữ; [phrases] trả cùng nội dung đó ở dạng **có dấu** mà mô
     * hình nhận dạng hiểu được. Hai đầu ra, **một nguồn** (4 bộ đăng ký + [VoiceSynonyms] + danh sách động) — nên
     * thêm một dòng registry là vừa gõ được vừa nói được, không phải sửa hai chỗ.
     *
     * Cách biến không-dấu thành có-dấu **không** phải một bảng chép tay: nó tra ngược qua chính từ điển của mô
     * hình. Xem [VoicePhrases] — đó là chỗ giải thích đầy đủ, và là chỗ có bài canh.
     *
     * @param vocabulary từ điển mô hình ([VoskWordList.readOutputSymbols]).
     * @param installed TÊN GÓI đang có trên máy — quyết định tên [VoiceAppTargets] nào được khai với bộ nhận
     *   dạng. Rỗng ⇒ không khai tên app đích nào (xem `VoicePhrases.labelPhrases`).
     * @param places nhãn trong **sổ địa chỉ** của hồ sơ đang dùng (spec `kachi-voice-addresses.html` R6). Rỗng ⇒
     *   không khai cách nói nào của sổ — cùng luật với [installed]: ngữ pháp chỉ khai thứ gọi được thật.
     */
    fun phrases(
        vocabulary: Set<String>,
        profiles: List<String> = emptyList(),
        apps: List<String> = emptyList(),
        installed: Set<String> = emptySet(),
        places: List<String> = emptyList(),
    ): VoicePhraseSet = VoicePhrases.build(vocabulary, profiles, apps, installed, places)
}
