package com.byd.clusternav.launcher

/**
 * ═══ FIX286 · R-KC — PHÍM VẬT LÝ → **MỌI NÚT XE** của Kachi (owner 03/10) ════════════════════════════════════════
 *
 * Owner 03/10: *"set phím 305 cho mở cửa kính trái cũng đc … phím set overwrite chức năng cũ Ok, tuỳ user … nút vặn
 * volume … dùng nút đó để vặn gió"* · *"Ý là cover hết các chức năng mình có, user chọn gì tuỳ họ thôi"*. Spec
 * `docs/specs/kachi-286-field-fixes.html` §3.11 R-KC.
 *
 * ## Mã đích bền: `ctl:<controlId>:<hành động>`
 * Một dòng gán phím (`voicekey_bindings`, JSON `{k,t}`) mang một CHUỖI đích. Trước R-KC chuỗi đó là tên gói app hoặc
 * một sentinel `__X__` ([com.byd.clusternav.voicekey.VoiceKeyBinding]). Tên gói Android **không bao giờ chứa `:`**, và
 * sentinel bọc `__` ⇒ tiền tố [PREFIX] không thể trùng một đích cũ nào: dữ liệu ≤ 2.85 đọc lên nguyên vẹn, không cần
 * migrate. Chuỗi chỉ mang **mã nút của registry + mã hành động** — không vị trí, không chữ của người dùng — nên bản
 * chia sẻ hồ sơ mang theo được ([ProfileSharePolicy] `R_KEYS`).
 *
 * ## Danh sách đích SINH TỪ [ControlRegistry], không chép tay
 * [actionsFor] suy hành động từ [ControlDef.kind] + dữ liệu của chính dòng nút: thêm một nút vào registry ⇒ nút đó tự
 * có đích phím, không ai phải nhớ sửa tệp này (`KeyCtlTargetTest` canh). Luật suy là DỮ LIỆU (CLAUDE.md §7), không
 * một `if (id == …)` nào:
 *  - [ControlKind.TOGGLE] ⇒ Bật · Tắt · Đảo;
 *  - [ControlKind.STEP] ⇒ +1 · −1 nấc (kẹp/AUTO theo [ClimateAuto] ở tầng thi hành, cùng đường cú chạm −/+);
 *  - [ControlKind.COVER] ⇒ Mở · Đóng · từng mức thêm trong [ControlDef.args] (rèm: Nửa) · Đảo;
 *  - [ControlKind.SELECT] ⇒ Kế tiếp (khi có ≥ 2 lựa chọn — vòng như cú chạm ô) · từng lựa chọn trong [ControlDef.args];
 *  - [ControlKind.BUTTON] ⇒ Bấm.
 *
 * ## Đảo / Kế tiếp cho MỌI nút đảo được — luật 2.87 (R-FL1, owner 03/10 *"cái nào đảo đc phải làm đảo hết nhé, chứ hao
 * phím lắm"*)
 * Tới 2.86 hai hành động này chỉ sinh khi nút có đường ĐỌC [ControlDef.readKey] (*"quyết bằng sự thật, không cờ RAM"*)
 * ⇒ cốp — readKey RỖNG từ 25/09 vì `getHatchDoorStatus` rỗng mọi arg trên xe owner — phải tốn HAI phím Mở + Đóng,
 * trong khi chính ô cốp trên màn đảo được bằng một cú chạm. Từ 2.87 luật sinh đích KHÔNG còn hỏi readKey; nguồn trạng
 * thái được quyết lúc CHẠY ở [KeyCtlPlan]:
 *  1. nút có readKey **và** đọc ra số hợp lệ ⇒ quyết bằng XE (y như 2.86);
 *  2. còn lại (không readKey · HAL hỏng · máy ảo) ⇒ quyết bằng **lệnh cuối Kachi đã gửi** ([ControlLastSent] — CÙNG
 *     bảng mà ô trên màn đọc/ghi, nên mở bằng ô rồi bấm phím Đảo là ĐÓNG);
 * rồi giải ra hành động CỤ THỂ (Bật/Tắt · Mở/Đóng · mức n) TRƯỚC khi thi hành ⇒ mọi cổng an toàn áp lên hành động đã
 * giải (MỞ cốp chỉ khi đứng yên — `CtlSafetyPolicy`).
 *
 * ## Giới hạn (owner chấp nhận — spec §4.3)
 * Nút đổi bằng đường khác (chìa, công tắc cửa, app BYD) mà KHÔNG đọc được ⇒ bảng lệnh cuối không biết ⇒ lần Đảo đầu có
 * thể trùng trạng thái (không tác dụng), bấm lại là được — riêng ca MỞ-bị-cổng-tốc-độ (cốp lúc xe chạy) thì Đảo từ trí
 * nhớ lùi về ĐÓNG ngay, không kẹt (KDoc [KeyCtlPlan], SOÁT vòng 1 · P1). Trạng thái giả định khi tiến trình bật = tắt/đóng (BYD giết
 * Kachi mỗi lần tắt máy) — chi tiết + giới hạn cấp tiến trình (`:wake`) ở KDoc [ControlLastSent]. Đích Mở / Đóng · Bật /
 * Tắt riêng VẪN còn cho ai muốn chắc chắn một chiều; mã đích đã lưu ≤ 2.86 đọc lên nguyên vẹn (cú pháp không đổi).
 */
enum class KeyCtlAction(val token: String) {
    ON("on"), OFF("off"), FLIP("flip"),
    UP("+1"), DOWN("-1"),
    OPEN("open"), CLOSE("close"),
    NEXT("next"),
    PRESS("press"),

    /** Đặt thẳng một mức/lựa chọn — token `=<n>` (mức COVER ≥ 2 · chỉ số lựa chọn SELECT). */
    SET("=");

    companion object {
        fun ofToken(token: String): KeyCtlAction? = entries.firstOrNull { it != SET && it.token == token }
    }
}

/** Một đích phím trỏ vào nút xe. [level] chỉ có nghĩa với [KeyCtlAction.SET]. */
data class KeyCtlTarget(val controlId: String, val action: KeyCtlAction, val level: Int = 0) {
    /** Chuỗi bền ghi vào `voicekey_bindings`. */
    val spec: String
        get() = KeyCtlTargets.PREFIX + controlId + SEP + (if (action == KeyCtlAction.SET) "=$level" else action.token)

    private companion object { const val SEP = ':' }
}

/**
 * Một NHÓM trong hộp chọn của Cài đặt › Phím vô-lăng. Thứ tự + nhãn suy từ [CapabilityGroups] (nhóm có hàng nút) rồi
 * [Domain] cho phần còn lại — xem [KeyCtlTargets.groups].
 */
data class KeyCtlGroup(
    val id: String,
    override val label: String,
    override val labelEn: String?,
    val targets: List<KeyCtlTarget>,
) : Localized

object KeyCtlTargets {

    /** Tiền tố mã đích nút xe. Tên gói Android không chứa `:` ⇒ không trùng đích cũ nào. */
    const val PREFIX = "ctl:"

    /** Tiền tố mã nhóm suy từ [Domain] (nhóm của [CapabilityGroups] giữ mã `g_…` của nó). */
    const val DOMAIN_GROUP_PREFIX = "d_"

    private val CONTROL_ID = Regex("^[a-z0-9_]+$")

    fun isCtl(spec: String): Boolean = spec.startsWith(PREFIX)

    /**
     * Đọc mã đích — **chỉ cú pháp**, không hỏi registry. `null` = không phải mã `ctl:` hợp cú pháp. Tách khỏi [decode]
     * để tầng thi hành phân biệt được *"chuỗi hỏng"* với *"nút này không còn trên bản Kachi đang chạy"*.
     */
    fun parse(spec: String): KeyCtlTarget? {
        if (!isCtl(spec)) return null
        val parts = spec.removePrefix(PREFIX).split(':')
        if (parts.size != 2) return null
        val (id, tok) = parts
        if (!CONTROL_ID.matches(id)) return null
        if (tok.startsWith("=")) {
            val n = tok.drop(1).toIntOrNull()?.takeIf { it >= 0 && tok.drop(1) == it.toString() } ?: return null
            return KeyCtlTarget(id, KeyCtlAction.SET, n)
        }
        return KeyCtlAction.ofToken(tok)?.let { KeyCtlTarget(id, it) }
    }

    /**
     * Mã đích HỢP LỆ với registry: cú pháp đúng **và** nút còn tồn tại **và** hành động nằm trong [actionsFor] của nút
     * đó. Mọi thứ khác ⇒ `null` (tầng thi hành báo, không bắn).
     */
    fun decode(spec: String, resolve: (String) -> ControlDef? = ControlRegistry::byId): KeyCtlTarget? {
        val t = parse(spec) ?: return null
        val def = resolve(t.controlId) ?: return null
        return t.takeIf { it in actionsFor(def) }
    }

    /** Hành động của MỘT nút — luật ở KDoc tệp. Thứ tự = thứ tự hiện trong hộp chọn. */
    fun actionsFor(def: ControlDef): List<KeyCtlTarget> {
        val id = def.id
        fun t(a: KeyCtlAction, n: Int = 0) = KeyCtlTarget(id, a, n)
        return when (def.kind) {
            ControlKind.TOGGLE -> listOf(t(KeyCtlAction.ON), t(KeyCtlAction.OFF), t(KeyCtlAction.FLIP))
            ControlKind.STEP -> listOf(t(KeyCtlAction.UP), t(KeyCtlAction.DOWN))
            ControlKind.COVER -> buildList {
                add(t(KeyCtlAction.OPEN)); add(t(KeyCtlAction.CLOSE))
                // Mức thêm (≥ 2) đọc từ chính `args` — cùng số mức mà ô COVER bấm vòng (`ControlTileFactory.tileCover`).
                for (n in 2 until def.args.size) add(t(KeyCtlAction.SET, n))
                add(t(KeyCtlAction.FLIP))
            }
            ControlKind.SELECT -> buildList {
                // < 2 lựa chọn thì "kế tiếp" là chính nó — không sinh một đích vô nghĩa.
                if (def.args.size >= 2) add(t(KeyCtlAction.NEXT))
                def.args.indices.forEach { add(t(KeyCtlAction.SET, it)) }
            }
            ControlKind.BUTTON -> listOf(t(KeyCtlAction.PRESS))
        }
    }

    /**
     * Nhóm cho hộp chọn — **mỗi nút đúng MỘT nhóm**: nhóm đầu tiên của [CapabilityGroups.ALL] khai nút trong `writes`
     * (Kính · Cửa & khoang · Đèn), còn lại theo [ControlDef.domain] (Khí hậu & không khí gồm cả ghế mát/sưởi,
     * Thân xe, Năng lượng, Giải trí…). Domain là trường BẮT BUỘC của mọi dòng nút ⇒ không nút nào rơi ra ngoài.
     */
    fun groups(
        registry: List<ControlDef> = ControlRegistry.ALL,
        capGroups: List<CapabilityGroup> = CapabilityGroups.ALL,
    ): List<KeyCtlGroup> {
        val byGroup = LinkedHashMap<String, MutableList<KeyCtlTarget>>()
        val meta = LinkedHashMap<String, Pair<String, String?>>()
        capGroups.filter { it.writes.isNotEmpty() }.forEach { g -> meta[g.id] = g.label to g.labelEn }
        Domain.entries.forEach { d -> meta[DOMAIN_GROUP_PREFIX + d.name.lowercase()] = d.label to d.labelEn }
        registry.forEach { def ->
            val gid = capGroups.firstOrNull { def.id in it.writes }?.id ?: (DOMAIN_GROUP_PREFIX + def.domain.name.lowercase())
            byGroup.getOrPut(gid) { mutableListOf() } += actionsFor(def)
        }
        return meta.mapNotNull { (gid, names) ->
            byGroup[gid]?.takeIf { it.isNotEmpty() }?.let { KeyCtlGroup(gid, names.first, names.second, it) }
        }
    }

    /**
     * Nhãn ĐÃ DỊCH hiện cho người dùng (vd *"Gió +1"*, *"Bật Kính lái"*) theo [lang] (mặc định [Strings.current]). Tên
     * `display…` theo quy ước [Localized.displayLabel] — `label` trần của `:core` là nhãn GỐC tiếng Việt
     * (`LauncherI18nContractTest` canh tầng vẽ không đọc nhãn gốc).
     */
    fun displayLabel(t: KeyCtlTarget, lang: Lang = Strings.current, resolve: (String) -> ControlDef? = ControlRegistry::byId): String {
        val def = resolve(t.controlId) ?: return t.spec
        val n = def.labelIn(lang)
        fun s(vi: String, en: String) = Strings.t(vi, en, lang)
        val arg = def.argsIn(lang).getOrNull(t.level)
        return when (t.action) {
            KeyCtlAction.ON -> s("Bật ", "Turn on ") + n
            KeyCtlAction.OFF -> s("Tắt ", "Turn off ") + n
            KeyCtlAction.FLIP ->
                if (def.kind == ControlKind.COVER) Strings.fIn(lang, "Mở/đóng {0} (đảo)", "Open/close {0} (toggle)", n)
                else Strings.fIn(lang, "Bật/tắt {0} (đảo)", "Toggle {0}", n)
            KeyCtlAction.UP -> "$n +1"
            KeyCtlAction.DOWN -> "$n −1"
            KeyCtlAction.OPEN -> s("Mở ", "Open ") + n
            KeyCtlAction.CLOSE -> s("Đóng ", "Close ") + n
            KeyCtlAction.NEXT -> n + s(": mức kế tiếp", ": next")
            KeyCtlAction.SET -> "$n: " + (arg ?: t.level.toString())
            KeyCtlAction.PRESS -> s("Bấm ", "Press ") + n
        }
    }

    /** Nhãn của một chuỗi đích bất kỳ: mã `ctl:` hợp lệ ⇒ [displayLabel]; hỏng/không còn ⇒ nguyên chuỗi (vẫn nhận ra để xoá). */
    fun displayLabelOf(spec: String, lang: Lang = Strings.current): String = decode(spec)?.let { displayLabel(it, lang) } ?: spec
}
