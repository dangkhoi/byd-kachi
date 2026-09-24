package com.byd.clusternav.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * ═══ S4 · R2/R5/R8 — phần **theo hồ sơ** của [WorkspacePrefs]: chụp–áp · nhân bản · chuyển đổi một-lần ═════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` R2 · R5 · R8. Đây là **hàm mở rộng của chính
 * [WorkspacePrefs]** chứ không phải một lớp mới — cùng cách `ClusterNavBridge` tách phần Cast/Phím ra
 * `ClusterNavBridgeCast.kt`/`ClusterNavBridgeKeys.kt`: bề mặt gọi vẫn phẳng (`prefs.snapshotClusterNav(...)`), mà mỗi
 * tệp vẫn dưới trần 500 dòng của dự án (CLAUDE.md §4.1).
 *
 * ## ⚠ Vì sao KHÔNG dựng một lớp riêng tự mở `kachi_workspace`
 * Lớp đó sẽ là **cửa thứ hai** vào cùng chỗ lưu. Dự án đã trả giá cho hình dạng đó ở [SOÁT P1-1] (*"đường đọc bền nằm
 * trong tầng UI"*) và ở bốn lần bẫy hai-bản-sao. Hàm mở rộng dùng lại đúng `sp` của [WorkspacePrefs] nên không thể
 * lệch, và không ai phải nhớ "dựng đúng thứ tự hai đối tượng".
 *
 * ## Phép chụp–áp, nói thẳng cái được và cái mất
 * **Được**: đổi hồ sơ áp được cả cấu hình ClusterNav (dẫn đường · biển báo · bong bóng · phím vô-lăng · tiện nghi ·
 * tự chiếu) mà **không phải viết lại một dòng nào** của runtime đang chạy trên xe — chúng vẫn đọc đúng tệp prefs cũ,
 * đúng khoá cũ, đúng kiểu cũ.
 *
 * **Mất**: ảnh chụp là một **bản sao** của giá trị, nên nó chỉ đúng nếu được chụp **ngay trước khi rời hồ sơ**. Đó là
 * lý do `PrefsWorkspaceRepository.switchProfile` chụp A **trước** khi đổi con trỏ, và là lý do không có đường thứ hai
 * nào được phép ghi thẳng vào `<hồ sơ>__cn__*`.
 */

/**
 * Chụp giá trị ĐANG có của mọi khoá ClusterNav theo hồ sơ vào ảnh chụp của [profile].
 *
 * Một chuỗi cho **một tệp** prefs (khoá `<hồ sơ>__cn__<tên tệp>`, hậu tố sinh bởi [ProfileScope.snapshotSuffix] nên
 * nó nằm sẵn trong [ProfileScope.LAUNCHER_SUFFIXES] ⇒ xoá hồ sơ là xoá cả ảnh chụp, không phải nhớ thêm gì).
 *
 * ## ⚠⚠ Khoá VẮNG MẶT cũng phải được chụp — dưới dạng `null` tường minh
 * `sp.all` chỉ trả khoá **có mặt**. Nếu ảnh chụp bỏ qua khoá vắng thì lượt áp sau này cũng bỏ qua nó, tức nó **giữ
 * nguyên giá trị của hồ sơ vừa rời**: A chưa từng bật bong bóng, B bật ⇒ A → B → A và A tự nhiên có bong bóng, vĩnh
 * viễn, không có đường quay lại. [PrefSnapshot] có thẻ kiểu `n` đúng cho ca này, và [applyClusterNav] dịch nó thành
 * `remove(key)` — tức "trả khoá về đúng trạng thái chưa-ai-đặt".
 */
internal fun WorkspacePrefs.snapshotClusterNav(profile: String) {
    val e = sp.edit()
    ProfileScope.CLUSTERNAV_KEYS.forEach { (file, keys) ->
        val all = clusterNavPrefs(file).all
        // `associateWith` giữ CẢ khoá vắng (giá trị `null`) — xem KDoc ở trên, đây là nửa dễ quên nhất của phép chụp.
        val values: Map<String, Any?> = keys.associateWith { all[it] }
        e.putString(keyOf(profile, ProfileScope.snapshotSuffix(file)), PrefSnapshot.encode(values))
    }
    e.apply()
}

/**
 * Ghi ảnh chụp của [profile] trở lại đúng tệp prefs mà dịch vụ ClusterNav đang đọc.
 *
 * **Hồ sơ chưa có ảnh chụp ⇒ KHÔNG làm gì với tệp đó** (giữ nguyên giá trị hiện tại), đúng R5: *"hồ sơ mới = bản sao
 * của hiện tại"*. Đây là phân biệt giữa *"chưa có ảnh"* (khoá vắng) và *"ảnh rỗng"* (khoá có, chuỗi rỗng) — ảnh rỗng
 * chỉ xảy ra khi tệp đó thật sự không có khoá nào, và lúc đó **không có gì để áp** nên hai ca ra cùng kết quả.
 *
 * ⚠ Ghi **đúng kiểu**: `getBoolean` trên giá trị ghi bằng `putString` **ném** `ClassCastException`, và chỗ đọc là dịch
 * vụ đang chạy trên xe (`FloatingBubbleService`, `NavAccessibilityService`) chứ không phải màn Cài đặt — mất kiểu thì
 * không hỏng lúc đổi hồ sơ mà hỏng **trên đường** (xem KDoc [PrefSnapshot]).
 *
 * ⚠ Ghi bằng `apply()` chứ không `commit()`: `apply()` cập nhật bản đồ **trong RAM ngay lập tức** (lượt đọc kế tiếp
 * của dịch vụ thấy ngay) và đẩy xuống đĩa ở thread nền — còn `commit()` chặn luồng vẽ để chờ I/O của **29 khoá trên 3
 * tệp** đúng lúc người dùng vừa chạm đổi hồ sơ.
 */
internal fun WorkspacePrefs.applyClusterNav(profile: String) {
    ProfileScope.CLUSTERNAV_KEYS.forEach { (file, keys) ->
        val raw = sp.getString(keyOf(profile, ProfileScope.snapshotSuffix(file)), null) ?: return@forEach
        // ⚠⚠ Lọc theo [ProfileScope] NGAY LÚC ÁP, không chỉ lúc chụp: ảnh chụp nằm trên đĩa của xe **lâu hơn** bản
        // phân loại đã sinh ra nó. Một khoá bị xếp lại sang "theo XE" ở bản sau (đã xảy ra: `cast_enabled`, S4 OQ2)
        // vẫn còn nguyên trong ảnh chụp cũ, và không có phép lọc này thì lượt đổi hồ sơ **vẫn** ghi đè nó — tức
        // quyết định "khoá này thuộc cả xe" chỉ có hiệu lực trên máy mới cài. [ProfileScope] phải là nguồn duy nhất
        // ở CẢ hai đầu, không thì nó chỉ là nguồn duy nhất của một nửa.
        val values = PrefSnapshot.decode(raw).filterKeys { it in keys }
        if (values.isEmpty()) return@forEach
        val e = clusterNavPrefs(file).edit()
        values.forEach { (k, v) ->
            when (v) {
                // Khoá vắng lúc chụp ⇒ trả nó về "chưa ai đặt" thay vì để nguyên giá trị của hồ sơ vừa rời.
                null -> e.remove(k)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is String -> e.putString(k, v)
                // `PrefSnapshot` chỉ sinh `Set<String>`; lọc lại vì kiểu tĩnh là `Set<*>` (mất kiểu qua `Any?`).
                is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
                else -> Unit
            }
        }
        e.apply()
    }
}

/**
 * S4 · R8 — **thêm hồ sơ = BẢN SAO của hồ sơ đang dùng**, rồi chuyển sang nó.
 *
 * Chép **mọi** hậu tố trong [ProfileScope.LAUNCHER_SUFFIXES] (đã gồm cả hậu tố ảnh chụp ClusterNav), nên hồ sơ mới
 * giống hệt hồ sơ nguồn ở mọi thứ người dùng thấy được. Tên trùng ⇒ chỉ chuyển sang, **không chép đè** (đó là lượt
 * "chuyển sang", và chép đè sẽ xoá cấu hình của hồ sơ đó).
 *
 * ## ⚠ Vì sao chụp ClusterNav TRƯỚC khi chép
 * Ảnh chụp của hồ sơ nguồn có thể đã cũ: nó chỉ được làm mới ở lượt **rời** hồ sơ, còn từ đó tới giờ người dùng đã
 * chỉnh bao nhiêu công tắc ClusterNav thì chừng ấy thay đổi chỉ nằm ở tệp prefs thật. Chép ảnh cũ nghĩa là bản sao ra
 * đời đã **khác** bản gốc ngay lúc tạo — và khác một cách không ai giải thích được.
 *
 * ## ⚠ Một `Editor`, một `apply()`
 * Toàn bộ phép chép + hai khoá danh sách đi trong **một** lượt ghi: nửa chừng chết máy mà chỉ có `profiles` được ghi
 * thì trên màn có một hồ sơ rỗng mang tên "bản sao của X".
 */
internal fun WorkspacePrefs.duplicateActiveProfile(name: String) {
    val clean = name.trim().replace(Regex("[\\r\\n]"), " ")
    if (clean.isEmpty()) return
    val source = activeProfile()
    val list = profiles().toMutableList()
    if (clean in list) { setActiveProfile(clean); return }
    snapshotClusterNav(source)
    list.add(clean)
    val e = sp.edit().putString(WorkspacePrefs.K_PROFILES, list.joinToString("\n")).putString(WorkspacePrefs.K_ACTIVE, clean)
    WorkspacePrefs.PROFILE_SUFFIXES.forEach { suffix ->
        val from = keyOf(source, suffix)
        val to = keyOf(clean, suffix)
        val value = sp.all[from]
        copyValue(e, to, value)
    }
    e.apply()
}

/**
 * S4 · R2 — chuyển **cảnh đời cũ** thành **hồ sơ**, đúng MỘT lần cho cả máy.
 *
 * Kế hoạch do `:core` gấp ([ScenesMigration.plan], thuần, có test off-car); ở đây chỉ **áp** nó: mỗi cảnh thành một
 * hồ sơ mang bố cục/ô/thanh nút của cảnh, **các phần còn lại chép từ hồ sơ đang dùng** (R2), rồi đặt `boot_profile`
 * và dấu [WorkspacePrefs.K_MIGRATED_SCENES].
 *
 * ## ⚠⚠ Phải chạy TRƯỚC lượt dọn rác widget đầu tiên
 * Id widget bên thứ ba của một cảnh chỉ nằm trong chuỗi `<hồ sơ>__scenes` cho tới khi lượt này chạy xong. Lượt dọn
 * rác chạy ở nhịp render đầu tiên (`AppWidgetIds.orphaned` → `AppWidgetSlotHost.reclaim`), nên nếu nó đi trước thì
 * chính lượt nâng cấp là thứ **xoá vĩnh viễn** widget của người dùng. Vì vậy chỗ gọi là `init` của
 * `PrefsWorkspaceRepository` — trước cả lượt `load()` đầu tiên.
 *
 * ## ⚠⚠ MỘT `Editor`, một `apply()` — vì lượt này KHÔNG được chạy hai lần
 * Dấu đặt trong cùng lượt ghi với dữ liệu. Tách ra hai lượt thì một lần chết máy giữa chừng sẽ cho lượt sau chạy lại
 * trên dữ liệu **đã chuyển**, và vì tên đã bị chiếm nên nó dựng thêm `"Đi làm 2"`, `"Đi làm 3"`… mỗi lần khởi động.
 * `SharedPreferences` áp cả `Editor` như một khối, nên "chuyển xong" và "đã đánh dấu" không thể lệch nhau.
 *
 * Không có cảnh nào ⇒ **vẫn đặt dấu** rồi thôi: không đặt thì mọi lượt khởi động sau đều quét lại vô ích.
 */
internal fun WorkspacePrefs.migrateScenesOnce() {
    if (sp.getBoolean(WorkspacePrefs.K_MIGRATED_SCENES, false)) return
    val active = activeProfile()
    val existing = profiles()
    // ⚠⚠ Tên đã lấy phải DỒN qua từng hồ sơ, không truyền `existing` cho mọi lượt gấp kế hoạch. Cảnh là dữ liệu
    // theo hồ sơ ở bản cũ, nên hai hồ sơ **đều** có thể có một cảnh tên "Đi làm" — mà `plan()` chỉ tránh trùng với
    // danh sách nó được đưa. Đưa cùng một `existing` cho cả hai thì cả hai gấp ra đúng tên "Đi làm", và vòng ghi
    // dưới đây bỏ bản thứ hai (`record.name in names`) ⇒ **mất hẳn một cảnh** ở đúng lượt nâng cấp, im lặng. Dồn
    // tên vào thì bản thứ hai thành "Đi làm 2" — đúng chữ R2 (*"trùng tên thì thêm hậu tố"*).
    val taken = existing.toMutableList()
    val plans = existing.map { p ->
        val plan = ScenesMigration.plan(
            scenesRaw = sp.getString(keyOf(p, WorkspacePrefs.LEGACY_SCENES), null),
            bootSceneId = sp.getString(keyOf(p, WorkspacePrefs.LEGACY_BOOT_SCENE), null),
            activeProfile = active,
            existingNames = taken,
        )
        taken += plan.newProfiles.map { it.name }
        p to plan
    }
    // Ảnh chụp ClusterNav của hồ sơ ĐANG DÙNG chỉ được làm mới ở lượt **rời** hồ sơ ⇒ ở đây nó có thể đã cũ hàng
    // tuần, trong khi tệp prefs thật mới là thứ đúng lúc này. `writeRecord` chép ảnh đó sang hồ sơ mới, nên chụp
    // lại TRƯỚC là điều kiện để hồ sơ vừa chuyển ra đời **giống hiện tại** (R2: *"các phần khác chép từ hồ sơ đang
    // dùng"*) thay vì giống một lần đổi hồ sơ nào đó trong quá khứ. Cùng lý do `duplicateActiveProfile` chụp trước
    // khi chép. Chỉ chụp khi thật sự có hồ sơ mới: không có cảnh nào thì đây chỉ là lượt đặt dấu.
    if (plans.any { it.second.newProfiles.isNotEmpty() }) snapshotClusterNav(active)
    val e = sp.edit().putBoolean(WorkspacePrefs.K_MIGRATED_SCENES, true)
    // Khoá đời cũ ra khỏi đĩa trong CÙNG lượt ghi: giữ lại thì `widgetIdsOtherProfiles` bảo vệ mãi những id nay đã
    // nằm trong ô của hồ sơ mới (thừa), và lần sửa tay dấu `migrated_scenes_v1` sẽ nhân bản cảnh lần nữa.
    existing.forEach { p ->
        e.remove(keyOf(p, WorkspacePrefs.LEGACY_SCENES))
        e.remove(keyOf(p, WorkspacePrefs.LEGACY_BOOT_SCENE))
    }
    val names = existing.toMutableList()
    var boot: String? = null
    plans.forEach { (source, plan) ->
        plan.newProfiles.forEach { record ->
            if (record.name in names) return@forEach
            names += record.name
            writeRecord(e, source, record)
        }
        // Cảnh-lúc-nổ-máy của hồ sơ ĐANG DÙNG thắng: nó là thứ người dùng vừa thấy hiệu lực. Hồ sơ khác cũng có thể
        // có dấu, nhưng chỉ có MỘT `boot_profile` cho cả xe (R4) nên phải chọn, và chọn cái đang hiệu lực là chọn thứ
        // không làm ai ngạc nhiên.
        if (plan.bootProfile != null && (boot == null || source == active)) boot = plan.bootProfile
    }
    if (names.size > existing.size) e.putString(WorkspacePrefs.K_PROFILES, names.joinToString("\n"))
    boot?.let { e.putString(WorkspacePrefs.K_BOOT_PROFILE, it) }
    e.apply()
}

/** Ghi một hồ sơ do lượt chuyển đổi dựng ra: ba thứ của cảnh + **chép phần còn lại** từ [source] (R2). */
private fun WorkspacePrefs.writeRecord(e: SharedPreferences.Editor, source: String, record: ProfileRecord) {
    val name = record.name
    // Phần còn lại (chủ đề · đơn vị · hình nền · ngôn ngữ · tự-mở · chip · ảnh chụp ClusterNav) chép từ hồ sơ nguồn
    // TRƯỚC, rồi ba thứ của cảnh mới ghi đè lên — thứ tự này để một hậu tố mai sau thuộc cả hai nhóm vẫn ra đúng
    // giá trị của cảnh, không phải của hồ sơ nguồn.
    WorkspacePrefs.PROFILE_SUFFIXES.forEach { suffix -> copyValue(e, keyOf(name, suffix), sp.all[keyOf(source, suffix)]) }
    e.putString(keyOf(name, "preset"), record.workspace.preset.name)
    record.workspace.slots.forEachIndexed { i, c -> e.putString(keyOf(name, "slot_$i"), SlotCodec.encode(c)) }
    e.putString(keyOf(name, "dock_edge"), record.dock.edge.name)
    e.putString(keyOf(name, "dock_enabled"), record.dock.enabled.joinToString(","))
    e.putBoolean(keyOf(name, "dock_visible"), record.dock.visible)   // S1b — ẩn/hiện thanh theo hồ sơ
    val grid = record.grid
    if (grid == null || grid.frames.isEmpty()) e.remove(keyOf(name, "grid_layout"))
    else e.putString(keyOf(name, "grid_layout"), WorkspaceGrid.encode(grid))
}

/**
 * Chép một giá trị `sp.all` sang khoá khác, **giữ kiểu**. `null` (khoá nguồn vắng) ⇒ `remove` để khoá đích không giữ
 * lại rác của một hồ sơ cùng tên đã bị xoá bằng bản cũ (xem KDoc [WorkspacePrefs.addProfile]).
 */
private fun copyValue(e: SharedPreferences.Editor, to: String, value: Any?) {
    when (value) {
        null -> e.remove(to)
        is Boolean -> e.putBoolean(to, value)
        is Int -> e.putInt(to, value)
        is Long -> e.putLong(to, value)
        is Float -> e.putFloat(to, value)
        is String -> e.putString(to, value)
        is Set<*> -> e.putStringSet(to, value.filterIsInstance<String>().toSet())
        else -> Unit
    }
}

/**
 * Tệp prefs của phía ClusterNav theo tên. Mở qua `Context` (mỗi tên là một `SharedPreferences` riêng) — Android
 * cache theo tên trong cùng tiến trình, nên đây **vẫn là** đúng đối tượng mà dịch vụ đang giữ, và mọi listener đã
 * đăng ký (`registerOnSharedPreferenceChangeListener`) đều nhận được lượt ghi này.
 */
private fun WorkspacePrefs.clusterNavPrefs(file: String): SharedPreferences =
    appCtx.getSharedPreferences(file, Context.MODE_PRIVATE)

/**
 * ═══ S4 · ĐỔI TÊN một hồ sơ (owner 2026-09-16 · E5) ══════════════════════════════════════════════════
 *
 * Trả `true` khi đã đổi; `false` = bị [ProfileRename] từ chối (tên rỗng / trùng / hồ sơ không tồn tại) — chỗ
 * gọi tự nói ra lý do, ở đây không đoán hộ.
 *
 * ## Vì sao **DỜI** từng khoá chứ không tạo mới rồi xoá cũ
 * Tên hồ sơ là tiền tố của mọi khoá của nó. Xem KDoc [ProfileRename]: tạo-mới-rồi-xoá là mất trắng bố cục.
 *
 * ## Ba khoá **con trỏ** phải đi theo, và chúng là ba khoá THEO XE
 * `active_profile` · `boot_profile` đều trỏ tới hồ sơ **bằng tên**; quên một cái là một con trỏ treo — hoặc
 * launcher nạp một hồ sơ không tồn tại (màn trắng), hoặc lần nổ máy sau rơi về hồ sơ đầu danh sách mà không
 * ai hiểu vì sao. Cả hai nằm ngoài [profileKeys] đúng theo [ProfileScope.DEVICE_KEYS], nên phải xử ở đây.
 *
 * Một `edit()` duy nhất ⇒ hoặc đổi trọn, hoặc không đổi gì. Hai lượt `apply()` là một khe mà một lần tắt máy
 * giữa chừng để lại nửa bộ khoá mang tên cũ, nửa mang tên mới.
 */
fun WorkspacePrefs.renameProfile(old: String, new: String): Boolean {
    val plan = (ProfileRename.plan(old, new, profiles()) as? ProfileRename.Result.Ok)?.plan ?: return false
    if (plan.from == plan.to) return true
    val e = sp.edit().putString(WorkspacePrefs.K_PROFILES, plan.profiles.joinToString("\n"))
    plan.suffixes.forEach { suffix ->
        val from = keyOf(plan.from, suffix)
        val to = keyOf(plan.to, suffix)
        // `all` là một ảnh chụp `Map<String, *>`; đọc kiểu qua nó rồi ghi lại đúng kiểu ấy là cách DUY NHẤT
        // dời được một khoá mà không phải biết trước nó là chuỗi hay boolean (hậu tố nay có cả hai —
        // `top_strip` là chuỗi, `top_strip_labels` là boolean, `dock_visible` cũng vậy).
        when (val v = sp.all[from]) {
            null -> e.remove(to)              // hồ sơ cũ chưa từng đặt khoá này ⇒ tên mới cũng không được có
            is String -> e.putString(to, v)
            is Boolean -> e.putBoolean(to, v)
            is Int -> e.putInt(to, v)
            is Long -> e.putLong(to, v)
            is Float -> e.putFloat(to, v)
            is Set<*> -> e.putStringSet(to, v.filterIsInstance<String>().toSet())
            else -> Unit                      // kiểu lạ (không có trong mã hôm nay) ⇒ bỏ, không đoán
        }
        e.remove(from)
    }
    if (activeProfile() == plan.from) e.putString(WorkspacePrefs.K_ACTIVE, plan.to)
    if (sp.getString(WorkspacePrefs.K_BOOT_PROFILE, null) == plan.from) e.putString(WorkspacePrefs.K_BOOT_PROFILE, plan.to)
    e.apply()
    return true
}


// ═══ #4 EXPORT / IMPORT HỒ SƠ (owner 2026-09-24: backup + chia sẻ) ═══════════════════════════════════════════
//
// Serialize TOÀN BỘ hồ sơ (bố cục · chip · đơn vị · theme · ảnh chụp ClusterNav) qua [PrefSnapshot] (đã typed,
// đã test) — KHÔNG nghĩ format thứ hai. Một dòng đầu = header "kachi-profile\tv1\t<tên>" để nhận dạng + đặt tên khi
// nhập. KHÔNG xuất khoá nhạy cảm: PROFILE_SUFFIXES chỉ là cấu hình launcher (không có IP xe/token — những thứ đó
// nằm ở tệp prefs khác, không theo hồ sơ).

private const val PROFILE_IO_HEADER = "kachi-profile"
private const val PROFILE_IO_VERSION = "v1"

/** Xuất hồ sơ [profile] ra chuỗi (ghi file để backup/chia sẻ). Chụp ClusterNav trước cho tươi (như duplicate). */
internal fun WorkspacePrefs.exportProfile(profile: String): String {
    snapshotClusterNav(profile)
    val values: Map<String, Any?> = WorkspacePrefs.PROFILE_SUFFIXES.associateWith { sp.all[keyOf(profile, it)] }
        .filterValues { it != null }
    val header = listOf(PROFILE_IO_HEADER, PROFILE_IO_VERSION, profile).joinToString("\t")
    return header + "\n" + PrefSnapshot.encode(values)
}

/**
 * Nhập hồ sơ từ chuỗi [data] thành hồ sơ tên [name] (mặc định lấy tên trong header). Trả `true` nếu nhập được.
 * Tên trùng ⇒ KHÔNG đè (trả false) — nhập là tạo mới, đè sẽ xoá cấu hình hồ sơ đang có. Header sai ⇒ false.
 */
internal fun WorkspacePrefs.importProfile(data: String, name: String? = null): Boolean {
    val lines = data.trim().split("\n", limit = 2)
    if (lines.size < 2) return false
    val head = lines[0].split("\t")
    if (head.getOrNull(0) != PROFILE_IO_HEADER) return false
    val target = (name ?: head.getOrNull(2))?.trim()?.replace(Regex("[\\r\\n]"), " ").orEmpty()
    if (target.isEmpty()) return false
    val list = profiles().toMutableList()
    if (target in list) return false                                  // không đè hồ sơ đang có
    val values = PrefSnapshot.decode(lines[1])
    val e = sp.edit()
    list.add(target)
    e.putString(WorkspacePrefs.K_PROFILES, list.joinToString("\n"))
    // Chỉ ghi các hậu tố HỢP LỆ (PROFILE_SUFFIXES) — chống chuỗi lạ nhét khoá ngoài phạm vi hồ sơ.
    values.filterKeys { it in WorkspacePrefs.PROFILE_SUFFIXES }.forEach { (suffix, v) -> copyValue(e, keyOf(target, suffix), v) }
    e.apply()
    return true
}
