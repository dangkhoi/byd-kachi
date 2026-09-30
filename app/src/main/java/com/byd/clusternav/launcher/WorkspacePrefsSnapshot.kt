package com.byd.clusternav.launcher

import android.util.Log

/**
 * ═══ S4 · R5 + V-CLUSTER — CHỤP / ÁP ảnh cấu hình ClusterNav theo hồ sơ — phần của [WorkspacePrefs] ════════════════
 *
 * Tách khỏi `WorkspacePrefsProfile.kt` ở V-CLUSTER (2026-09-30, spec `kachi-profiles-are-everything.html` §11.4.9 — trần
 * 500 dòng). Tầng này chỉ **đọc tệp sống → gọi phép thuần → đổ kết quả vào `Editor`**: logic (họ tiền tố có mốc, khoá
 * hoãn `cast_enabled`, kiểm kiểu, bộ kiểm hình học) là [ClusterSnapshotPlan] ở `:core`, có test chạy thật.
 *
 * ## Ba thứ V-CLUSTER thêm vào hợp đồng S4 (hợp đồng cũ giữ nguyên)
 *  1. **Họ tiền tố** `cast_geometry` (DPI/khung từng app khi chiếu) vào ảnh kèm **mốc có mặt**: ảnh có mốc ⇒ tệp sống
 *     sau khi áp bằng đúng họ của hồ sơ; ảnh cũ không mốc ⇒ không chạm (spec §11.4.2).
 *  2. **`cast_enabled` hoãn áp**: lượt áp KHÔNG BAO GIỜ ghi khoá sống (mọi cổng đọc là LIVE ⇒ cụm hai chủ) — chỉ ghi
 *     bản chờ `cast_enabled_pending`, chốt ở lần khởi động tiến trình kế (spec §11.4.3).
 *  3. **Kiểm kiểu + giá trị** trước khi ghi: ảnh trên đĩa sửa tay được và tệp nhập là dữ liệu người khác gửi.
 */

private const val TAG = "KachiProfile"

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
 *
 * V-CLUSTER: họ + mốc + lựa chọn `cast_enabled` của hồ sơ (bản chờ nếu có) do [ClusterSnapshotPlan.snapshot] thêm vào.
 */
internal fun WorkspacePrefs.snapshotClusterNav(profile: String) {
    val e = sp.edit()
    ProfileScope.CLUSTERNAV_KEYS.forEach { (file, keys) ->
        val all = clusterNavPrefs(file).all
        // `associateWith` giữ CẢ khoá vắng (giá trị `null`) — xem KDoc ở trên, đây là nửa dễ quên nhất của phép chụp.
        val values: Map<String, Any?> = keys.associateWith { all[it] }
        val shot = ClusterSnapshotPlan.snapshot(values, all, ProfileScopeCluster.familiesOf(file), ProfileScopeCluster.DEFERRED)
        logDropped("snapshot $file/$profile", shot.dropped)
        e.putString(keyOf(profile, ProfileScope.snapshotSuffix(file)), PrefSnapshot.encode(shot.values))
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
 * không hỏng lúc đổi hồ sơ mà hỏng **trên đường** (xem KDoc [PrefSnapshot]). V-CLUSTER: giá trị SAI kiểu (khai sẵn ở
 * [ProfileScopeCluster.DECLARED_TYPES], hoặc khác kiểu của giá trị sống) bị bỏ và ghi log thay vì ghi.
 *
 * ⚠ Ghi bằng `apply()` chứ không `commit()`: `apply()` cập nhật bản đồ **trong RAM ngay lập tức** (lượt đọc kế tiếp
 * của dịch vụ thấy ngay) và đẩy xuống đĩa ở thread nền — còn `commit()` chặn luồng vẽ để chờ I/O đúng lúc người dùng
 * vừa chạm đổi hồ sơ.
 */
internal fun WorkspacePrefs.applyClusterNav(profile: String) {
    val stored = sp.all
    ProfileScope.CLUSTERNAV_KEYS.forEach { (file, keys) ->
        val raw = storedSnapshot(stored, keyOf(profile, ProfileScope.snapshotSuffix(file))) ?: return@forEach
        val families = ProfileScopeCluster.familiesOf(file)
        // ⚠⚠ Lọc theo [ProfileScope] NGAY LÚC ÁP, không chỉ lúc chụp: ảnh chụp nằm trên đĩa của xe **lâu hơn** bản
        // phân loại đã sinh ra nó. Một khoá bị xếp lại phạm vi ở bản sau vẫn còn nguyên trong ảnh chụp cũ, và không có
        // phép lọc này thì lượt đổi hồ sơ **vẫn** ghi đè nó. [ProfileScope] phải là nguồn duy nhất ở CẢ hai đầu.
        val values = PrefSnapshot.decode(raw).filterKeys { ClusterSnapshotPlan.inScope(it, keys, families) }
        if (values.isEmpty()) return@forEach
        val target = clusterNavPrefs(file)
        val plan = ClusterSnapshotPlan.apply(
            target.all, values, keys, families, ProfileScopeCluster.DECLARED_TYPES, ProfileScopeCluster.DEFERRED,
        )
        logDropped("apply $file/$profile", plan.dropped)
        if (plan.writes.isEmpty()) return@forEach
        val e = target.edit()
        plan.writes.forEach { (k, v) ->
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
 * V-CLUSTER · VC-R8 lớp 1 — làm sạch giá trị của MỘT hậu tố hồ sơ trong **tệp nhập** trước khi nó chạm đĩa.
 *
 * Hậu tố không phải ảnh chụp ClusterNav ⇒ trả nguyên [value] (bố cục/chip… có bộ giải mã tự chữa riêng). Hậu tố ảnh
 * chụp ⇒ chỉ giữ khoá trong phạm vi, đúng kiểu khai sẵn, mốc đúng `true`, khoá họ + giá trị qua bộ kiểm hình học;
 * giá trị không phải chuỗi ⇒ `null` (chỗ gọi xoá khoá đích). Số khoá bị bỏ được ghi log — không ném, không im lặng.
 */
internal fun cleanImportedSnapshot(suffix: String, value: Any?): Any? {
    val file = ProfileScope.CLUSTERNAV_KEYS.keys.firstOrNull { ProfileScope.snapshotSuffix(it) == suffix } ?: return value
    val raw = value as? String ?: run {
        Log.w(TAG, "nhập hồ sơ: bỏ ảnh $file — kiểu ${value?.let { it::class.simpleName }}, cần chuỗi")
        return null
    }
    val clean = ClusterSnapshotPlan.sanitize(
        PrefSnapshot.decode(raw), ProfileScope.CLUSTERNAV_KEYS.getValue(file), ProfileScopeCluster.familiesOf(file),
        ProfileScopeCluster.DECLARED_TYPES,
    )
    logDropped("import $file", clean.dropped)
    return PrefSnapshot.encode(clean.values)
}

/**
 * Chuỗi ảnh chụp ĐÃ LƯU ở khoá [key] của `kachi_workspace` ([stored] = `sp.all` chụp một lần) — `null` khi vắng HOẶC
 * sai kiểu (ghi log, coi như hồ sơ chưa có ảnh cho tệp đó).
 *
 * Senior review V-CLUSTER Pass 3 — không đọc bằng `sp.getString`: [ĐO code b5c0e87] lượt nhập hồ sơ tới 2.83 chép thẳng
 * giá trị đã giải mã (`copyValue`, chưa có [cleanImportedSnapshot]), nên một tệp nhập đặt `<hồ sơ>__cn__<tệp>` thành
 * Boolean/Int là nằm yên trên đĩa. `getString` trên nó NÉM `ClassCastException` — ở [migrateClusterProfileOnce] (chạy
 * trong `init` của `PrefsWorkspaceRepository`, trước màn nhà) đó là launcher sập ở MỌI lần mở, không bao giờ tới được
 * dấu chạy-một-lần; ở [applyClusterNav] là sập đúng lúc chạm chip hồ sơ.
 */
internal fun storedSnapshot(stored: Map<String, *>, key: String): String? {
    val v = stored[key] ?: return null
    if (v is String) return v
    Log.w(TAG, "ảnh chụp đã lưu ${key.takeLast(40)} có kiểu ${v::class.simpleName}, cần chuỗi — coi như chưa có ảnh")
    return null
}

/** Một dòng log cho các khoá bị bỏ (giá trị hỏng / sai kiểu) — cắt ngắn, để một tệp độc không phun rác vào logcat. */
private fun logDropped(what: String, dropped: List<String>) {
    if (dropped.isEmpty()) return
    val shown = dropped.take(5).joinToString { k -> k.take(60).map { c -> if (c < ' ') '?' else c }.joinToString("") }
    Log.w(TAG, "$what: bỏ ${dropped.size} khoá hỏng/sai kiểu: $shown")
}
