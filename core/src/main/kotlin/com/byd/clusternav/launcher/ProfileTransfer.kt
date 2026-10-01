package com.byd.clusternav.launcher

/**
 * ═══ PROFILE-IO-0930 — ĐỊNH DẠNG + KẾ HOẠCH xuất/nhập một hồ sơ (phần thuần của #4, owner 2026-09-24) ═════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §12. Thuần Kotlin ⇒ test off-device. `WorkspacePrefs` (`:app`) chỉ
 * cấp [Source] và đổ [ImportPlan.writes] vào một `Editor`; nó không biết header, không biết lọc chia sẻ.
 *
 * ## Định dạng tệp (không đổi từ #4, thêm MỘT trường)
 * Dòng đầu `kachi-profile\tv1\t<tên>\t<kiểu>`, phần còn lại là [PrefSnapshot] của `hậu tố → giá trị`. Tệp v1 cũ chỉ
 * có ba trường ⇒ đọc như [Kind.FULL]. Trường thứ tư LẠ ⇒ từ chối — không đoán một tệp có riêng tư hay không.
 * Bản 2.84 trở về trước đọc tệp mới vẫn được (chúng bỏ qua trường thứ tư).
 */
object ProfileTransfer {

    const val MAGIC = "kachi-profile"
    const val VERSION = "v1"

    /** Kiểu tệp — [code] là chữ ghi ở trường thứ tư của header. */
    enum class Kind(val code: String) { FULL("full"), SHARE("share") }

    data class Header(val name: String, val kind: Kind)

    /**
     * Kế hoạch nhập: tên hồ sơ mới [target] + `hậu tố → giá trị` cho MỌI hậu tố (`null` = chỗ gọi `remove`). [dropped] =
     * hậu tố có trong tệp nhưng SAI kiểu khai sẵn (PROFILE-IMPORT-TYPES, [ProfileScopeLauncher]) — đã thành `null` trong
     * [writes]; chỗ gọi ghi log, không ném.
     */
    data class ImportPlan(
        val target: String,
        val kind: Kind,
        val writes: Map<String, Any?>,
        val dropped: List<String> = emptyList(),
    )

    /** Cửa đọc của nơi lưu (`WorkspacePrefs` ở `:app`). */
    interface Source {
        /** `sp.all` của tệp `kachi_workspace` — đọc SAU [snapshotLive] nếu nó được gọi. */
        fun stored(): Map<String, *>

        fun keyOf(profile: String, suffix: String): String

        /** Chụp tệp sống vào ảnh của [profile]. Chỉ đúng cho hồ sơ ĐANG DÙNG — xem [export]. */
        fun snapshotLive(profile: String)
    }

    /**
     * Trần độ dài tên hồ sơ đi qua tệp (ký tự UTF-16). Senior review PROFILE-IO-0930 lượt 1 [P2]: tên hồ sơ là TIỀN TỐ
     * của mọi khoá của nó (`<tên>__<hậu tố>`), nên một tệp độc có header dài cỡ 1 MB (dưới trần [ProfileFiles.MAX_READ_BYTES])
     * nhân lên thành hàng chục MB trong `kachi_workspace.xml` — tệp prefs mà launcher nạp trọn vào RAM ở MỌI lần mở.
     * Tên dài hơn bị CẮT (không từ chối): bản sao lưu của một hồ sơ tên dài vẫn nhập được.
     */
    const val MAX_NAME_CHARS = 100

    /** Mọi ký tự điều khiển (C0 · DEL · C1 — gồm tab/CR/LF): tab là dấu ngăn trường, `\n` là dấu ngăn danh sách hồ sơ. */
    private val NAME_BREAKS = Regex("\\p{Cc}")
    private const val BOM = "\uFEFF"

    /** Mốc có mặt của mọi họ — khớp NGUYÊN chuỗi (không theo tiền tố `@family:`): chỉ mốc thật đi vào bản chia sẻ. */
    private val FAMILY_MARKERS: Set<String> = ProfileScopeCluster.FAMILIES.mapTo(HashSet()) { it.marker }

    /**
     * Tên hồ sơ an toàn cho một trường header: ký tự điều khiển thành khoảng trắng, cắt ở [MAX_NAME_CHARS]. Ô nhập tên
     * trong app không gõ được ký tự điều khiển, nên chỉ tên đến từ TỆP (sửa tay / tệp độc) bị đổi.
     */
    fun cleanName(name: String): String = clip(name.replace(NAME_BREAKS, " ").trim(), MAX_NAME_CHARS).trim()

    /** Cắt [s] còn tối đa [max] ký tự UTF-16 mà KHÔNG để lại nửa cặp surrogate (nửa cặp không vẽ được, không mã hoá được). */
    fun clip(s: String, max: Int): String {
        if (s.length <= max) return s
        if (max <= 0) return ""
        val t = s.take(max)
        return if (t.last().isHighSurrogate()) t.dropLast(1) else t
    }

    fun encodeHeader(name: String, kind: Kind): String = listOf(MAGIC, VERSION, cleanName(name), kind.code).joinToString("\t")

    /**
     * Đọc dòng đầu; `null` = không phải tệp hồ sơ Kachi / phiên bản lạ / tên rỗng / kiểu lạ.
     *
     * Phiên bản khác [VERSION] ⇒ từ chối (fail-closed): một định dạng sau này có thể đổi nghĩa thân tệp, và đoán sai ở
     * đây là ghi rác vào hồ sơ. BOM UTF-8 đầu tệp (trình soạn thảo Windows tự thêm) được bỏ trước khi so.
     */
    fun parseHeader(line: String): Header? {
        val f = line.removePrefix(BOM).trimEnd('\r').split("\t")
        if (f.getOrNull(0) != MAGIC || f.getOrNull(1) != VERSION) return null
        val name = cleanName(f.getOrNull(2).orEmpty())
        if (name.isEmpty()) return null
        val kind = when (val code = f.getOrNull(3)) {
            null -> Kind.FULL                                  // tệp v1 cũ (#4, 2026-09-24): ba trường
            else -> Kind.values().firstOrNull { it.code == code } ?: return null
        }
        return Header(name, kind)
    }

    /**
     * IO-R5 — chuỗi tệp của [profile].
     *
     * ⚠⚠ Chỉ chụp tệp sống khi [profile] LÀ hồ sơ đang dùng ([active]). Tệp prefs ClusterNav chỉ chứa cấu hình của hồ sơ
     * đang dùng; chụp nó vào ảnh của một hồ sơ khác là xuất cấu hình của hồ sơ đang dùng dưới tên hồ sơ kia **và** ghi đè
     * vĩnh viễn ảnh đã lưu của hồ sơ kia (lỗi ẩn có từ #4). Hồ sơ không đang dùng ⇒ ảnh ĐÃ LƯU là sự thật của nó.
     */
    fun export(source: Source, profile: String, active: String, kind: Kind): String {
        if (profile == active) source.snapshotLive(profile)
        val stored = source.stored()
        val raw: Map<String, Any?> = ProfileScope.LAUNCHER_SUFFIXES
            .associateWith { stored[source.keyOf(profile, it)] }
            .filterValues { it != null }
        // PROFILE-IMPORT-TYPES: giá trị sai kiểu đang nằm trên đĩa (tệp nhập ≤ 2.84) KHÔNG đi ra tệp — máy nhận chạy bản cũ
        // chưa có lớp kiểm kiểu thì chính tệp này làm màn chính của họ sập. Lượt đọc ở máy này đã coi nó là vắng.
        val values = ProfileScopeLauncher.check(raw).values
        val out = if (kind == Kind.SHARE) forShare(values) else values
        return encodeHeader(profile, kind) + "\n" + PrefSnapshot.encode(out)
    }

    /**
     * IO-R3/R4 — bỏ mọi thứ chưa được [ProfileSharePolicy] soát là chia sẻ được (danh sách trắng).
     *
     * Hậu tố launcher không chia sẻ được ⇒ bỏ hẳn (lượt nhập `remove`). Khoá trong ảnh ClusterNav không chia sẻ được ⇒
     * `null` TƯỜNG MINH, không được bỏ: [ClusterSnapshotPlan.apply] chỉ duyệt khoá CÓ trong ảnh, nên khoá vắng nghĩa là
     * "không chạm tệp sống" ⇒ đổi sang hồ sơ nhập sẽ giữ nguyên lịch đang sống của hồ sơ vừa rời, rồi lượt rời hồ sơ nhập
     * chụp lịch đó vào ảnh của nó. `null` = `remove` ⇒ hồ sơ nhập thật sự không có lịch; lịch của hồ sơ vừa rời đã nằm
     * trong ảnh của chính nó (switchProfile chụp TRƯỚC khi áp). Không có ảnh ở nguồn mà tệp có khoá riêng tư ⇒ vẫn tạo
     * một ảnh chỉ gồm các `null` đó (khoá khác vắng ⇒ không chạm, đúng nghĩa "chưa có ảnh").
     */
    fun forShare(values: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        values.forEach { (suffix, v) ->
            if (suffix !in ProfileScope.SNAPSHOT_SUFFIXES && ProfileSharePolicy.shareable(suffix)) out[suffix] = v
        }
        ProfileScope.CLUSTERNAV_KEYS.forEach { (file, keys) ->
            val suffix = ProfileScope.snapshotSuffix(file)
            val hidden = keys.filterNot(ProfileSharePolicy::shareable)
            if (suffix !in values && hidden.isEmpty()) return@forEach
            val shot = LinkedHashMap<String, Any?>()
            PrefSnapshot.decode(values[suffix] as? String).forEach { (k, v) ->
                if (k in FAMILY_MARKERS || ProfileSharePolicy.shareable(k)) shot[k] = v
            }
            hidden.forEach { shot[it] = null }
            out[suffix] = PrefSnapshot.encode(shot)
        }
        return out
    }

    /**
     * Kế hoạch nhập [data] thành một hồ sơ MỚI; `null` = tệp không hợp lệ (header sai, tên rỗng, không có thân).
     *
     * Tên: [name] nếu có, không thì tên trong header; trùng [existing] ⇒ `"<tên> 2"`, `"<tên> 3"`… (owner 2026-09-25 —
     * không đè hồ sơ đang có). Bản [Kind.SHARE] qua [forShare] lần nữa: tệp sửa tay không lách được. Mọi hậu tố VẮNG
     * trong tệp thành `null` ⇒ chỗ gọi xoá rác của một hồ sơ cùng tên đã bị xoá bằng bản cũ.
     *
     * PROFILE-IMPORT-TYPES: kiểm kiểu ([ProfileScopeLauncher.check]) chạy TRƯỚC [forShare] — hậu tố ảnh chụp sai kiểu phải
     * được báo trong [ImportPlan.dropped], không lặng lẽ thành một ảnh rỗng dựng lại ở [forShare].
     */
    fun planImport(data: String, name: String?, existing: Collection<String>): ImportPlan? {
        // CRLF (tệp mở/lưu lại trên Windows) ⇒ LF: [PrefSnapshot] thoát `\r` trong giá trị thành `\\r`, nên một `\r` trần
        // chỉ có thể là dấu xuống dòng kiểu Windows — để nguyên thì mọi dòng `b|…|true\r` bị bỏ im lặng.
        val lines = data.removePrefix(BOM).replace("\r\n", "\n").trim().split("\n", limit = 2)
        if (lines.size < 2) return null
        val header = parseHeader(lines[0]) ?: return null
        val base = cleanName(name ?: header.name)
        if (base.isEmpty()) return null
        var target = base
        var n = 2
        while (target in existing) { target = "$base $n"; n++ }
        // Chỉ hậu tố HỢP LỆ — chống chuỗi lạ nhét khoá ngoài phạm vi hồ sơ.
        val values = PrefSnapshot.decode(lines[1]).filterKeys { it in ProfileScope.LAUNCHER_SUFFIXES }
        val typed = ProfileScopeLauncher.check(values)
        val kept = if (header.kind == Kind.SHARE) forShare(typed.values) else typed.values
        return ImportPlan(target, header.kind, ProfileScope.LAUNCHER_SUFFIXES.associateWith { kept[it] }, typed.dropped)
    }
}
