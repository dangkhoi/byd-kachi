package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacro
import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlDef
import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.Domain
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActionDef
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.Localized
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.TelemetrySpec

/**
 * Một câu **nói được** + việc Kachi sẽ làm với nó.
 *
 * [phrase] **luôn tiếng Việt** (thứ tiếng duy nhất bộ nhận dạng nghe được — spec `kachi-i18n-zh-th-ms.html` R5/OQ3);
 * [does] đã theo ngôn ngữ giao diện — tầng vẽ KHÔNG phải tra lại nhãn gốc của `:core`
 * (`LauncherI18nContractTest.tang ve khong doc nhan GOC cua core`).
 *
 * @property intent ý định mà [phrase] **phải** phân tích ra. Nó là nguồn của [does]/[asksFirst], và là thứ
 *   `VoiceCommandCatalogTest` đem đối chiếu với [VoiceIntentParser.parseOne] — tức bài canh *"màn hình không
 *   quảng cáo một câu mà bộ phân tích không hiểu"*.
 * @property asksFirst câu này sẽ hiện hộp hỏi lại trước khi chạy (người dùng đã tích nó ở *Hỏi xác nhận*).
 */
data class VoiceCommandExample(
    val phrase: String,
    val does: String,
    val asksFirst: Boolean,
    val intent: VoiceIntent,
)

/**
 * Một nhóm gập được trên màn Cài đặt › Giọng nói.
 *
 * @property id mã ỔN ĐỊNH (nhật ký + bài canh), không phải chuỗi hiển thị.
 * @property title tiêu đề ĐÃ DỊCH.
 * @property domain miền xe của nhóm, `null` với nhóm không thuộc bộ đăng ký (nhạc · dẫn đường · app · hồ sơ).
 *   Bài canh dùng nó để chứng minh **câu nằm đúng nhóm** (ý định phân tích ra phải cùng miền).
 */
data class VoiceCommandGroup(
    val id: String,
    val title: String,
    val domain: Domain?,
    val examples: List<VoiceCommandExample>,
)

/**
 * ═══ DANH SÁCH CÂU NÓI ĐƯỢC — **SINH TỪ CHÍNH NGỮ PHÁP**, một nguồn cho mọi bề mặt ════════════════════════════
 *
 * Spec `docs/specs/kachi-274-ux-voice-camera.html` R3. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bệnh nó chữa — [ĐO off-car 2026-09-26] năm bản sao, không bản nào dùng được cho người dùng
 * Tri thức *"câu nào nói được"* trước lượt này tồn tại ở **5 chỗ** và không chỗ nào có bài canh *"mọi câu bày ra
 * đều phân tích được"*: (1) [SherpaPhraseHotwords.phrases] sinh ~2000 cụm nhưng để **bias bộ nghe**, không để
 * đọc; (2+3) hai bảng mẫu câu VI/EN nằm trong `VoiceGrammarCoverageTest`; (4) bảng thứ ba + 16 dòng `generic`
 * **gõ tay** trong `FeatureCatalogDumpTest`; (5) bảng 14 nhóm **chép tay** ở `docs/HUONG-DAN-KACHI.md` §6. Thêm
 * một nút vào bộ đăng ký thì bảng (5) không đổi và **không gì đỏ** — đúng họ lỗi `unitPrefs` ×4 mà KDoc
 * [VoiceGrammar] lập ra để chặn. Tệp này là **nguồn duy nhất**; bốn người đọc nó: màn Cài đặt · dump danh mục ·
 * bài canh độ phủ · tài liệu.
 *
 * ## Không một NHÃN nào chép tay
 * Danh từ = nhãn của chính bộ đăng ký (qua [SherpaHotwords.phrasesOf], cùng phép dọn dấu câu mà tệp hotword
 * dùng). Động từ = [SherpaPhraseHotwords.CONTROL_VERBS] × [SherpaSpokenWords.VERBS] — **đúng hai bảng** mà
 * [VoiceIntentParser] chấp nhận, nên không câu nào sinh ra mà parser không hiểu. Tiêu đề nhóm xe =
 * [Domain.labelIn] (đã dịch, đã được `LangCoverageTest` đếm).
 *
 * Bốn tiêu đề [FAMILY_TITLES] là chuỗi khai tại đây — **không** bộ đăng ký nào mang chúng, và khai ở `:core`
 * song ngữ là đúng lệ [VoiceReply] (hàng chục câu trả lời khai bằng [Strings.t] ngay cạnh bộ phân tích).
 *
 * ## Ngôn ngữ: CÂU NÓI luôn tiếng Việt · tiêu đề + *"Kachi làm gì"* theo giao diện (spec `kachi-i18n-zh-th-ms.html`)
 * [ĐO] Bộ nhận dạng chỉ có một gói tiếng Việt (`SherpaModelCatalog`), hotword cố ý loại chữ Anh. Tới bản này màn EN
 * bày *"turn on X"* — parser hiểu khi GÕ, nhưng nói ra thì ASR không bao giờ nghe thấy; màn zh/th/ms còn tệ hơn
 * (động từ Việt + nhãn chữ Hán, không parse được). Nên cột câu nói dựng bằng [Lang.VI] ở **mọi** tiếng giao diện
 * (động từ/cụm phụ mặc định [Lang.VI], danh từ = nhãn GỐC [Localized.label]) — owner chốt OQ3. Tiêu đề nhóm và cột
 * [VoiceCommandExample.does] ([VoiceReply.preview]`(i, lang)`) theo `lang` của [groups] (mặc định [Strings.current]):
 * người đọc màn tiếng Trung cần biết câu tiếng Việt ấy LÀM GÌ bằng chữ họ đọc được. Bài canh lặp cả [Lang.entries]
 * truyền `lang` tường minh, không lật [Strings.current].
 * [coverageSentence] vẫn nhận `lang` (kể cả EN): nó thay hai bảng mẫu câu của bài canh độ phủ PARSER — parser hiểu
 * chữ Anh khi gõ (`VoiceTextConsole`), và bài ấy phải tiếp tục canh đường đó.
 *
 * ## Ngân sách thread giao diện
 * **Không** gọi [SherpaPhraseHotwords.phrases] (≈2000 cụm + `VoiceLexicon.tokenize`, [ĐO] 6,6 ms trên host ⇒
 * [SUY] ~130 ms trên đầu xe). Ở đây mỗi nút/datum tốn một [SherpaHotwords.phrasesOf] (vòng ký tự) + một
 * [VoiceGrammar.matchAt] trên từ vựng TĨNH đã `by lazy`; tầng vẽ thì chỉ dựng view của nhóm **đang mở**.
 */
object VoiceCommandCatalog {

    /**
     * Mọi nhóm câu nói được, theo thứ tự hiện trên màn: miền xe (thứ tự khai của [Domain]) rồi bốn họ ngoài bộ
     * đăng ký. Nhóm rỗng KHÔNG xuất hiện — không quảng cáo một nhóm không có câu nào.
     *
     * @param profiles tên hồ sơ trên máy này · @param apps nhãn app đã cài · @param places nhãn sổ địa chỉ.
     *   Rỗng ⇒ nhóm tương ứng tự vắng (cùng luật *"chỉ khai nơi ĐÃ LƯU"* của [VoicePlaces.spokenPhrases]).
     *   Phải là **đúng ba nguồn** mà `VoiceDispatcher.parse` dùng, nếu không danh sách quảng cáo một tập khác tập
     *   mà bộ phân tích thật sự nhận.
     * @param confirmIds tập mã đang bật ở *Hỏi xác nhận* (`Prefs.voiceConfirmIds`). Rỗng ⇒ [VoiceCommandExample
     *   .asksFirst] luôn `false`, đúng mặc định owner chốt 2026-09-16.
     * @param lang ngôn ngữ của tiêu đề nhóm + cột *"Kachi làm gì"* (mặc định tiếng giao diện). KHÔNG đổi câu nói —
     *   câu nói luôn tiếng Việt (KDoc tệp).
     */
    fun groups(
        profiles: List<String> = emptyList(),
        apps: List<String> = emptyList(),
        places: List<String> = emptyList(),
        confirmIds: Set<String> = emptySet(),
        lang: Lang = Strings.current,
        /** 2.91 — tên app đã dạy còn sống (nguồn động THỨ TƯ của parser) ⇒ nhóm *"Tên app đã dạy"* ([VoiceCommandCatalogTaught]). */
        aliases: List<VoiceAppAlias> = emptyList(),
    ): List<VoiceCommandGroup> {
        val out = ArrayList<VoiceCommandGroup>(Domain.entries.size + FAMILY_TITLES.size)
        Domain.entries.forEach { d ->
            val ex = domainPairs(d).map { ex(it, confirmIds, lang) }
            if (ex.isNotEmpty()) out.add(VoiceCommandGroup("dom_" + d.name.lowercase(), d.labelIn(lang), d, ex))
        }
        FAMILY_TITLES.forEach { (id, title) ->
            val ex = familyPairs(id, profiles, apps, places).map { ex(it, confirmIds, lang) }
            if (ex.isNotEmpty()) out.add(VoiceCommandGroup(id, title(lang), null, ex))
        }
        VoiceCommandCatalogTaught.group(aliases, lang) { ex(it, confirmIds, lang) }?.let { out.add(it) }
        return out
    }

    // ══ 1 · Câu cho MỘT dòng bộ đăng ký ═════════════════════════════════════════════════════════════════

    /**
     * Câu mẫu của một nút — hình dạng theo [ControlKind], động từ theo [SherpaPhraseHotwords.CONTROL_VERBS].
     *
     * Đây là bộ sinh mà cả màn Cài đặt lẫn `FeatureCatalogDumpTest` (trường `voice` của `registry.json`) đọc.
     */
    fun samples(def: ControlDef): List<String> = controlPairs(def).map { it.first }

    /** Câu mẫu của một datum — *"&lt;xem&gt; &lt;nhãn&gt;"*. Cùng vai [samples] cho nút. */
    fun samples(spec: TelemetrySpec): List<String> = listOf(readPair(spec).first)

    private fun controlPairs(def: ControlDef): List<Pair<String, VoiceIntent>> {
        val noun = nounOf(def, VoiceTermKind.CONTROL)
        val verbs = SherpaPhraseHotwords.CONTROL_VERBS[def.kind].orEmpty()
        val out = ArrayList<Pair<String, VoiceIntent>>(3)
        when (def.kind) {
            ControlKind.TOGGLE, ControlKind.COVER -> {
                verbs.take(2).forEach { v ->
                    val on = v == VoiceVerb.ON || v == VoiceVerb.OPEN
                    out.add("${verb(v)} $noun" to VoiceIntent.Control(def.id, if (on) 1 else 0))
                }
                // Mức NỬA (kính/rèm): tên mức là **dữ liệu của chính dòng đó** (`args[2]`, bản GỐC tiếng Việt — câu
                // nói luôn tiếng Việt), và câu chỉ được bày ra khi [VoiceControlParse.mentionsHalf] thật sự nhận nó.
                // Không có từ "nửa" nào viết cứng ở đây.
                def.args.getOrNull(HALF_INDEX)?.let { half ->
                    val p = "${verb(VoiceVerb.OPEN)} ${half.lowercase()} $noun"
                    if (VoiceControlParse.mentionsHalf(VoiceLexicon.tokenize(p))) {
                        out.add(p to VoiceIntent.Control(def.id, HALF_INDEX))
                    }
                }
            }
            ControlKind.STEP -> {
                verbs.forEach { v ->
                    when (v) {
                        VoiceVerb.UP -> out.add("${verb(v)} $noun" to VoiceIntent.Control(def.id, null, 1))
                        VoiceVerb.DOWN -> out.add("${verb(v)} $noun" to VoiceIntent.Control(def.id, null, -1))
                        // Mức mặc định của chính dòng đó làm ví dụ — nhưng chỉ khi nó là một mức CÓ NGHĨA: `fan`
                        // có `min = 0` mà [ĐO xe] `AC_WIND_LEVEL_SET = 0` bị xe bỏ qua, nên *"đặt gió 0"* sẽ là
                        // một câu Kachi hứa mà xe không làm.
                        else -> if (def.value > def.min) {
                            out.add("${verb(v)} $noun ${def.value}" to VoiceIntent.Control(def.id, def.clamp(def.value)))
                        }
                    }
                }
            }
            // Nút BẤM: cả câu là TÊN của việc (luật `VoiceIntentParser.headMatch`) — *"bấm Lọc ngay"* không ai nói.
            ControlKind.BUTTON -> out.add(noun to VoiceIntent.Control(def.id, null))
            // SELECT: tên nút + tên lựa chọn (đuôi là đối số thật — xem KDoc [VoiceGrammar.readsTail]).
            ControlKind.SELECT -> def.args.indices.take(2).forEach { i ->
                out.add("$noun ${def.args[i].lowercase()}" to VoiceIntent.Control(def.id, i))
            }
        }
        return out
    }

    private fun readPair(spec: TelemetrySpec): Pair<String, VoiceIntent> =
        "${verb(VoiceVerb.READ)} ${nounOf(spec, VoiceTermKind.TELEMETRY)}" to VoiceIntent.Read(spec.id)

    private fun domainPairs(d: Domain): List<Pair<String, VoiceIntent>> {
        val out = ArrayList<Pair<String, VoiceIntent>>(32)
        ControlRegistry.ALL.filter { it.domain == d }.forEach { out.addAll(controlPairs(it)) }
        ActionMacros.ALL.filter { it.domain == d }.forEach { m ->
            out.add(nounOf(m, VoiceTermKind.MACRO) to VoiceIntent.Macro(m.id))
        }
        VoiceTelemetry.SPOKEN.filter { it.domain == d }.forEach { out.add(readPair(it)) }   // 2.88 — KDoc [VoiceTelemetry]
        return out.distinctBy { it.first }
    }

    // ══ 2 · Bốn họ NGOÀI bộ đăng ký ═════════════════════════════════════════════════════════════════════

    /** Mã nhóm → tiêu đề theo ngôn ngữ (gọi trễ: ngôn ngữ là của lượt dựng, không của lúc nạp lớp). */
    private val FAMILY_TITLES: List<Pair<String, (Lang) -> String>> = listOf(
        "media" to { l -> Strings.t("Nhạc", "Music", l) },
        "nav" to { l -> Strings.t("Dẫn đường", "Navigation", l) },
        "apps" to { l -> Strings.t("Ứng dụng · ô · bố cục", "Apps · slots · layout", l) },
        "profile" to { l -> Strings.t("Hồ sơ tài xế", "Driver profiles", l) },
    )

    /**
     * Chỗ giữ chỗ ĐIỂM ĐẾN trong câu mẫu dẫn đường — chữ Việt vì câu nói luôn tiếng Việt (parser đọc nó thành
     * `Nav(query = NAV_PLACE)`). Cột *"Kachi làm gì"* đổi nó sang tiếng giao diện qua [shown].
     */
    private const val NAV_PLACE = "điểm đến"

    @Suppress("CyclomaticComplexMethod")
    private fun familyPairs(
        id: String,
        profiles: List<String>,
        apps: List<String>,
        places: List<String>,
    ): List<Pair<String, VoiceIntent>> = when (id) {
        // PLAY/PAUSE cần đối tượng, NEXT/PREV tự đứng một mình — **cùng luật** mà bộ sinh hotword đang chạy ngoài
        // hiện trường dùng ([SherpaPhraseHotwords.phrases], khối *"Nhạc"*), nên không có cách nói thứ hai ở đây.
        "media" -> listOf(
            "${verb(VoiceVerb.PLAY)} ${mediaNoun()}" to VoiceIntent.Media(VoiceMediaOp.PLAY),
            "${verb(VoiceVerb.PAUSE)} ${mediaNoun()}" to VoiceIntent.Media(VoiceMediaOp.PAUSE),
            longVerb(VoiceVerb.NEXT) to VoiceIntent.Media(VoiceMediaOp.NEXT),
            longVerb(VoiceVerb.PREV) to VoiceIntent.Media(VoiceMediaOp.PREV),
        )
        "nav" -> buildList {
            add("${longVerb(VoiceVerb.NAV)} $NAV_PLACE" to VoiceIntent.Nav(NAV_PLACE))
            // Sổ địa chỉ: động từ có ĐIỀU KIỆN của [VoicePlaces] (*"đi đến Nhà"*) — chỉ hiện nơi ĐÃ LƯU.
            places.take(SHOWN_PER_LIST).forEach { p ->
                add("${VoicePlaces.PLACE_VERBS.first()} $p" to VoiceIntent.NavigateSaved(p))
            }
        }
        "apps" -> buildList {
            apps.take(SHOWN_PER_LIST).forEach { a -> add("${verb(VoiceVerb.OPEN)} $a" to VoiceIntent.OpenApp(a)) }
            // Mệnh đề chỉ ô: lấy cụm ĐẦU của [VoiceSlotPhrases.SPOKEN] và đọc số ô bằng CHÍNH hàm mà bộ phân tích
            // dùng ([VoiceTailClause.slotAt]) — không đếm lại thứ tự bảng ở đây.
            apps.firstOrNull()?.let { a ->
                val tail = slotTail()
                val slot = VoiceTailClause.slotAt(VoiceLexicon.tokenize(tail))
                if (slot != null) add("${verb(VoiceVerb.OPEN)} $a $tail" to VoiceIntent.OpenApp(a, slot))
            }
            LauncherActions.ALL.forEach { a ->
                add("${verb(VoiceVerb.OPEN)} ${nounOf(a, VoiceTermKind.LAUNCHER)}" to VoiceIntent.Launcher(a.id))
            }
            // 2.93 — camera theo yêu cầu TẮT bằng lời (*"tắt camera sau"*): một câu mẫu cho đúng việc ấy.
            LauncherActions.ALL.filter { it.switchable }.forEach { a ->
                add("${verb(VoiceVerb.OFF)} ${nounOf(a, VoiceTermKind.LAUNCHER)}" to VoiceIntent.Launcher(a.id, off = true))
            }
            // Bố cục: giữ đúng những cụm mà [VoiceLayouts.match] THẬT SỰ nhận — cụm *"bố cục"* trần và *"đổi bố
            // cục"* nằm trong bảng vì tầng NGHE cần chúng, nhưng chúng không nêu bố cục nào nên không phải lệnh.
            VoiceLayouts.SPOKEN.forEach { s ->
                VoiceLayouts.match(VoiceLexicon.tokenize(s))?.let { add(s to VoiceIntent.Layout(it)) }
            }
        }
        "profile" -> profiles.take(SHOWN_PER_LIST).map { p ->
            "${longVerb(VoiceVerb.SWITCH)} ${profileMarker()} $p" to VoiceIntent.Profile(p)
        }
        else -> emptyList()
    }

    /**
     * Câu mẫu của **họ câu** mà [intent] đại diện — rỗng khi danh mục không có họ ấy.
     *
     * Cửa cho `FeatureCatalogDumpTest`: nó giữ nguyên lược đồ `registry.json` (`risk` · `replyPreview` ·
     * `replyFailed` …, mà một [VoiceCommandGroup] không mang nổi) nhưng lấy **câu** từ đây, nên bảng
     * `docs/kachi-feature-catalog.html` và màn Cài đặt không thể còn quảng cáo hai bộ câu khác nhau.
     */
    fun samplesFor(
        intent: VoiceIntent,
        profiles: List<String> = emptyList(),
        apps: List<String> = emptyList(),
        places: List<String> = emptyList(),
    ): List<String> {
        val want = keyOf(intent)
        return groups(profiles, apps, places).flatMap { it.examples }
            .filter { it.intent::class == intent::class && (keyOf(it.intent) == null || keyOf(it.intent) == want) }
            .map { it.phrase }
    }

    /**
     * Khoá nhận dạng *"cùng một VIỆC"* của một ý định; `null` = **cả họ tính là một** hoặc ý định không mang mã.
     *
     * [VoiceIntent.Nav] và [VoiceIntent.Layout] cố ý trả `null`: điểm đến là từ vựng MỞ (không có tập mã nào), và
     * năm bố cục dùng chung **một** câu trả lời nên tài liệu ghi một dòng cho cả họ (xem KDoc
     * `FeatureCatalogDumpTest`) — khoá theo giá trị ở đây sẽ chẻ chúng thành năm dòng gần giống nhau.
     */
    internal fun keyOf(i: VoiceIntent): String? = when (i) {
        is VoiceIntent.Control -> i.id
        is VoiceIntent.Read -> i.datumId
        is VoiceIntent.Macro -> i.id
        is VoiceIntent.Launcher -> i.id
        is VoiceIntent.Profile -> i.name
        is VoiceIntent.Media -> i.op.name
        is VoiceIntent.OpenApp -> i.appName + if (i.slot != null) "#slot" else ""
        is VoiceIntent.NavigateSaved -> i.placeName
        else -> null
    }

    // ══ 3 · Câu cho BÀI CANH ĐỘ PHỦ (giữ nguyên nghĩa bản cũ trong test) ════════════════════════════════

    /**
     * Câu canh độ phủ của một nút: **đúng một** câu, dựng từ nhãn ĐẦY ĐỦ theo `lang`.
     *
     * Tách khỏi [samples] có chủ ý: bài canh độ phủ đòi một câu ra **đúng giá trị** mong đợi theo [ControlKind]
     * (`VoiceGrammarCoverageTest.moi nut deu co dung gia tri mong doi`), còn danh sách bày cho người dùng thì cần
     * nhiều câu (bật *và* tắt) và dùng nhãn đã dọn dấu câu. Gộp hai vai vào một hàm là làm bài canh kia đỏ —
     * *"tắt X"* trả 0 trong khi nó chờ 1.
     */
    fun coverageSentence(def: ControlDef, lang: Lang): String {
        val name = def.labelIn(lang)
        val arg = (if (lang == Lang.EN) def.argsEn.firstOrNull() else null) ?: def.args.firstOrNull().orEmpty()
        return when (def.kind) {
            ControlKind.TOGGLE -> "${verb(VoiceVerb.ON, lang)} $name"
            ControlKind.COVER -> "${verb(VoiceVerb.OPEN, lang)} $name"
            ControlKind.BUTTON -> name
            ControlKind.SELECT -> "${verb(VoiceVerb.SET, lang)} $name $arg"
            ControlKind.STEP -> "${verb(VoiceVerb.SET, lang)} $name ${def.value}"
        }
    }

    /** Câu canh độ phủ của một datum — *"xem &lt;nhãn đầy đủ&gt;"* / *"show &lt;full label&gt;"*. */
    fun coverageSentence(spec: TelemetrySpec, lang: Lang): String =
        "${verb(VoiceVerb.READ, lang)} ${spec.labelIn(lang)}"

    // ══ 4 · Động từ · danh từ ═══════════════════════════════════════════════════════════════════════════

    /**
     * Dạng tiếng Anh của mỗi [VoiceVerb] — mỗi chuỗi phải có thật trong [VoiceGrammar.VERBS] và map về ĐÚNG
     * [VoiceVerb] đó (`VoiceCommandCatalogTest` canh hai chiều, cùng khuôn `SherpaBiasingCoverageTest`).
     *
     * Vì sao phải khai: [VoiceGrammar.VERBS] trộn cả hai thứ tiếng trong một danh sách phẳng và **không có** dấu
     * hiệu nào phân biệt (*"bat"* · *"on"* đều là ASCII sau khi bỏ dấu), nên không có phép suy ra dạng Anh nào
     * đúng được. Bảng này vì thế là **dữ liệu bị canh**, không phải một bộ từ vựng thứ hai: thêm một dòng ở đây
     * mà không có trong ngữ pháp là bài canh ĐỎ.
     */
    val EN_VERBS: Map<VoiceVerb, String> = mapOf(
        VoiceVerb.ON to "turn on",
        VoiceVerb.OFF to "turn off",
        VoiceVerb.OPEN to "open",
        VoiceVerb.CLOSE to "close",
        VoiceVerb.UP to "increase",
        VoiceVerb.DOWN to "decrease",
        VoiceVerb.SET to "set",
        VoiceVerb.READ to "show",
        VoiceVerb.SWITCH to "switch to",
        VoiceVerb.NAV to "navigate to",
        VoiceVerb.PLAY to "play",
        VoiceVerb.PAUSE to "pause",
        VoiceVerb.NEXT to "next track",
        VoiceVerb.PREV to "previous track",
    )

    /**
     * Dạng NGẮN NHẤT của một động từ (dạng đi cùng một đối tượng). Mặc định [Lang.VI] — câu bày cho người dùng luôn
     * tiếng Việt (KDoc tệp); chỉ [coverageSentence] truyền EN.
     */
    private fun verb(v: VoiceVerb, lang: Lang = Lang.VI): String =
        if (lang == Lang.EN) EN_VERBS.getValue(v) else SherpaSpokenWords.VERBS.getValue(v).first()

    /**
     * Dạng DÀI NHẤT (dạng tự đứng được thành cả một lệnh: *"bài tiếp theo"*, *"dẫn đường đến"*).
     *
     * ## ⚠ [SOÁT 2.74] Bỏ dạng kết thúc bằng **từ chỉ đối tượng** — nó là một câu DỞ
     * Chỉ lấy "nhiều khoảng trắng nhất" thì [VoiceVerb.PREV] ra *"quay lại bài"*: một cụm parser NHẬN, nhưng người
     * đọc nó lên sẽ tự nối thêm (*"quay lại bài hát trước"*) và câu nối thêm thì **không** parse được — danh sách
     * dạy sai đúng chỗ nó ra đời để dạy đúng. Từ cuối là một mục của [VoiceSynonyms.MEDIA_WORDS] ⇒ vế đối tượng
     * còn thiếu ⇒ lùi sang dạng dài kế tiếp (*"bài trước"*, trọn nghĩa). Phép ĐO trên bảng thật, không danh sách
     * ngoại lệ (CLAUDE.md §7): [VoiceVerb.NEXT] (*"bài tiếp theo"*) · [VoiceVerb.NAV] · [VoiceVerb.SWITCH] không
     * đổi vì từ cuối của chúng không phải từ chỉ đối tượng.
     */
    private fun longVerb(v: VoiceVerb, lang: Lang = Lang.VI): String {
        if (lang == Lang.EN) return EN_VERBS.getValue(v)
        val forms = SherpaSpokenWords.VERBS.getValue(v).sortedByDescending { it.count { c -> c == ' ' } }
        return forms.firstOrNull { !endsWithObjectWord(it) } ?: forms.first()
    }

    /** Cụm có kết thúc bằng một từ chỉ **đối tượng** (từ đáng lẽ phải đứng SAU động từ) không. */
    private fun endsWithObjectWord(form: String): Boolean {
        val last = VoiceLexicon.tokenize(form).lastOrNull()?.norm ?: return false
        return last in VoiceSynonyms.MEDIA_WORDS
    }

    /**
     * Cụm đánh dấu *"hồ sơ"* / *"profile"* ở dạng NGƯỜI ĐỌC ĐƯỢC — [VoiceProfileNames.MARKERS] khai không dấu
     * (hợp đồng của `VoiceLexicon.Token.norm`) và dự án **cấm** khôi phục dấu bằng máy (KDoc [SherpaSpokenWords]),
     * nên hai dạng này phải được **khai**. Bài canh ép cụm sinh ra mang một [VoiceProfileNames.MARKERS] thật ⇒
     * không lệch được.
     */
    private fun profileMarker(lang: Lang = Lang.VI): String =
        if (lang == Lang.EN) "profile" else "hồ sơ"

    /**
     * Mệnh đề chỉ ô cho câu mở app. Tiếng Việt lấy cụm ĐẦU của [VoiceSlotPhrases.SPOKEN] (bảng đang chạy ngoài
     * hiện trường); tiếng Anh thì bảng ấy **không có** dạng nào, nên khai tại đây — và cả hai chỉ được dùng khi
     * [VoiceTailClause.slotAt] thật sự đọc ra số ô (xem chỗ gọi), tức không có cụm nào bày ra mà máy không hiểu.
     */
    private fun slotTail(lang: Lang = Lang.VI): String =
        if (lang == Lang.EN) "in slot 1" else VoiceSlotPhrases.SPOKEN.first()

    /** Mức NỬA của một nút COVER = chỉ số 2 của `args` (xem `VoiceReply.controlPreview`, nhánh COVER ≥ 2). */
    private const val HALF_INDEX = 2

    /** Bao nhiêu ví dụ cho một danh sách ĐỘNG (hồ sơ · app · sổ địa chỉ) — đủ để thấy khuôn câu, không tràn trang. */
    private const val SHOWN_PER_LIST = 3

    /**
     * Khoá (KHÔNG dấu) của từ chỉ NHẠC dùng trong câu mẫu tiếng Việt — một mục THẬT của
     * [VoiceSynonyms.MEDIA_WORDS], bản có dấu tra qua [SherpaSpokenWords.ACCENTED].
     *
     * ## ⚠ [SOÁT 2.74] Vì sao KHAI, không lấy phần tử đầu bảng
     * Thứ tự [VoiceSynonyms.MEDIA_WORDS] là thứ tự **so khớp** của bộ phân tích (cụm dài trước cụm ngắn), không
     * phải thứ tự tự nhiên khi người ta NÓI: phần tử đầu là *"bài hát"* nên câu mẫu thành *"phát bài hát"* /
     * *"dừng bài hát"*, trong khi câu người lái thật sự nói — và câu `docs/HUONG-DAN-KACHI.md` §6 dạy suốt từ V1 —
     * là *"phát nhạc"* / *"dừng nhạc"*. Đảo bảng kia là đảo đường parse đã chạy ngoài hiện trường (CLAUDE.md §6)
     * ⇒ chọn ở ĐÂY. Cùng lối [profileMarker]/[slotTail]: một chuỗi khai tay nhưng **bị bài canh ép** phải có thật
     * trong bảng của bộ phân tích, nên nó không thể thành một từ vựng thứ hai.
     */
    internal const val MEDIA_NOUN_VI = "nhac"

    /** Từ chỉ NHẠC: bản có dấu cho tiếng Việt, bản KHÔNG có dạng Việt (tiếng Anh trong bảng) cho tiếng Anh. */
    private fun mediaNoun(lang: Lang = Lang.VI): String =
        if (lang == Lang.EN) {
            VoiceSynonyms.MEDIA_WORDS.firstOrNull { SherpaSpokenWords.ACCENTED[it] == null }
                ?: VoiceSynonyms.MEDIA_WORDS.first()
        } else {
            SherpaSpokenWords.ACCENTED[MEDIA_NOUN_VI]
                ?: VoiceSynonyms.MEDIA_WORDS.firstNotNullOf { SherpaSpokenWords.ACCENTED[it] }
        }

    /**
     * Danh từ để gọi một dòng bộ đăng ký: nhãn đã **dọn dấu câu** (*"Pin (SOC)"* → *"pin"*), nhưng chỉ khi cụm
     * dọn rồi vẫn trỏ về ĐÚNG dòng ấy; không thì lùi về nhãn đầy đủ.
     *
     * Đây là một phép **ĐO**, không phải một danh sách ngoại lệ (CLAUDE.md §7): nhãn *"Khoá / mở khoá"* dọn ra
     * *"khoá"* — dùng được hay không phụ thuộc từ vựng đang có, mà từ vựng thì đổi mỗi lần thêm cách nói. Hỏi
     * thẳng [VoiceGrammar] thì câu sinh ra tự đúng, và nhãn nào bị một dòng KHÁC cùng loại giành mất cụm ngắn sẽ
     * tự dùng nhãn dài (đúng luật *"dãy dài nhất thắng"* của bộ phân tích).
     *
     * Nhãn GỐC tiếng Việt ([Localized.label]) ở MỌI tiếng giao diện: câu nói luôn tiếng Việt (KDoc tệp). Trước bản này
     * là `displayLabel` ⇒ giao diện zh ra *"bật &lt;nhãn chữ Hán&gt;"* — `resolves` hỏng, lùi về nhãn đầy đủ, câu
     * không parse được.
     */
    private fun nounOf(row: Localized, kind: VoiceTermKind): String {
        val full = row.label
        val terms = VoiceGrammar.terms()
        val id = idOf(row)
        val cleaned = SherpaHotwords.phrasesOf(full)
        // Thứ tự thử: đoạn ĐẦU đã dọn (*"Pin (SOC)"* → *"pin"*) → mọi đoạn NỐI LẠI (*"Battery health (SOH)"* →
        // *"battery health soh"*, cụm đầu không đủ để khớp) → nhãn đầy đủ. Đoạn nối lại là thứ giữ cho bản tiếng
        // Anh khỏi bày ra dấu ngoặc trong một câu người ta phải ĐỌC.
        val cands = listOfNotNull(cleaned.firstOrNull(), cleaned.takeIf { it.size > 1 }?.joinToString(" "))
        // 2.93: tên camera TRẦN (*"camera"*) nay là của camera theo yêu cầu khi đi với TẮT (*"tắt camera"*) ⇒ lấy nhãn đầy đủ
        // (*"camera 360"*) để câu mẫu của nút Camera 360 vẫn ra đúng nút ([VoiceCameraPhrases]).
        return cands.firstOrNull { resolves(it, kind, id, terms) && !VoiceCameraPhrases.isCameraNoun(it) }?.lowercase()
            ?: full.lowercase()
    }

    /** Mã của một dòng bộ đăng ký — bốn lớp khai `id` nhưng [Localized] không đòi nó, nên tra tại đây. */
    private fun idOf(row: Localized): String = when (row) {
        is ControlDef -> row.id
        is TelemetrySpec -> row.id
        is ActionMacro -> row.id
        is LauncherActionDef -> row.id
        else -> ""
    }

    /**
     * Cụm [noun] có khớp TRỌN một cụm của đúng `(kind, id)` không — và **không** bị một dòng cùng loại khác
     * giành ở cùng độ dài (ca hai nút chung nhãn ngắn).
     *
     * Chỉ loại xung đột CÙNG loại: 18 nhãn trùng giữa mục ĐỌC và mục BẤM là chuyện bình thường, động từ giải nó
     * (`VoiceIntentParser.choose`).
     */
    private fun resolves(noun: String, kind: VoiceTermKind, id: String, terms: List<VoiceTerm>): Boolean {
        val t = VoiceLexicon.tokenize(noun)
        if (t.isEmpty()) return false
        val hits = VoiceGrammar.matchAt(t, 0, terms).filter { it.words.size == t.size && it.kind == kind }
        return hits.isNotEmpty() && hits.all { it.id == id }
    }

    private fun ex(pair: Pair<String, VoiceIntent>, confirmIds: Set<String>, lang: Lang) = VoiceCommandExample(
        phrase = pair.first,
        does = VoiceReply.preview(shown(pair.second, lang), lang),
        asksFirst = VoiceRiskTable.of(pair.second, confirmIds) == VoiceRisk.CONFIRM,
        intent = pair.second,
    )

    /**
     * Ý định để dựng cột *"Kachi làm gì"*: chỗ giữ chỗ [NAV_PLACE] (chữ Việt của câu nói) đổi sang [lang] — không thì
     * màn EN đọc *"Navigate to điểm đến"*. VI ra y nguyên câu cũ. Mọi ý định khác giữ nguyên ([intent] vẫn là ý định
     * THẬT mà câu nói phân tích ra — bài canh đối chiếu bằng nó).
     */
    private fun shown(i: VoiceIntent, lang: Lang): VoiceIntent =
        if (i is VoiceIntent.Nav && i.query == NAV_PLACE) i.copy(query = Strings.t("điểm đến", "a place", lang)) else i
}
