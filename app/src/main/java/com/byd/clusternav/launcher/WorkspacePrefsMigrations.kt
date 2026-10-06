package com.byd.clusternav.launcher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * ═══ DI TRÚ MỘT LẦN khi khoá ClusterNav đổi phạm vi XE → HỒ SƠ — phần của [WorkspacePrefs] ════════════════════════
 *
 * Tách khỏi `WorkspacePrefsProfile.kt` ở V-CLUSTER (2026-09-30, spec `kachi-profiles-are-everything.html` §11.4.9) vì
 * trần 500 dòng (CLAUDE.md §4.1). Cùng khuôn hàm mở rộng của CHÍNH [WorkspacePrefs] (dùng lại `sp`, không mở cửa thứ
 * hai vào `kachi_workspace`).
 *
 * Ba lượt, cùng một hợp đồng *"rót xuống, chỉ điền chỗ trống, dấu ghi CÙNG một Editor với dữ liệu"*:
 *  • [migrateNavScheduleOnce] — lịch tự dẫn đường (2026-09-28), dời NGUYÊN VĂN (bài canh
 *    `NavScheduleProfileScopeWiringTest` khoá thân hàm; CLAUDE.md §6: không viết lại đường đang chạy tốt) — trừ đúng MỘT
 *    lượt đọc ảnh đổi sang [storedSnapshot] (senior review 2.84: ảnh sai kiểu không được làm `init` ném);
 *  • [migrateClusterProfileOnce] — cụm/chiếu/camera/nút nổi (V-CLUSTER), phép tính thuần ở `:core`
 *    [ProfileScopeMigration.rotDown] (có test chạy thật, không chỉ đọc chữ);
 *  • [fillNewProfileKeysOnce] — 2.92 PROFILE-NEW-KEYS: khoá vào phạm vi hồ sơ ở bản SAU, theo sổ đã-rót.
 *
 * Lối mở tệp prefs ClusterNav ([clusterNavPrefs]) cũng nằm ở đây: tệp có lời gọi mở prefs là tệp mà
 * `SettingsCoverageContractTest` quét hằng khoá, nên hai dấu boolean bên dưới vẫn bị đòi lý do ở `NOT_SETTINGS`; sổ đã-rót
 * (StringSet — bộ quét chưa đọc `putStringSet`) có lý do ở đó và ở `ProfileScopeCluster.DEVICE_KEYS` bằng tay.
 */

/**
 * DI TRÚ MỘT LẦN: *tự dẫn đường theo lịch* từ phạm vi **theo XE** sang **theo HỒ SƠ** (owner 2026-09-28).
 *
 * ## Vì sao cần, và vì sao làm sai là mất dữ liệu của người dùng
 * Trước bản này, luật lịch và sổ đã-dẫn nằm chung cả máy. Đổi phạm vi mà không làm gì thì [applyClusterNav] gặp
 * ảnh chụp CŨ (chụp hồi hai khoá còn theo xe nên KHÔNG chứa chúng), `filterKeys` loại chúng ra, và giá trị đang
 * sống được giữ nguyên. Nghe thì có vẻ lành, nhưng hệ quả là **hồ sơ nào được chụp trước thì chiếm luật**, các hồ
 * sơ còn lại ăn theo cái đang sống rồi lệch dần — kiểu hỏng không ai thấy cho tới lúc một buổi sáng lịch không nổ.
 *
 * ## Cách làm: rót xuống, không bốc lên
 * Chép giá trị ĐANG SỐNG vào ảnh chụp của **mọi** hồ sơ hiện có. Sau lượt này ai cũng bắt đầu bằng đúng cái lịch
 * người dùng đang có, rồi mới tách ra khi họ sửa. Không ai mất gì, và không hồ sơ nào bỗng dưng trống.
 *
 * ⚠ Chỉ ĐIỀN VÀO CHỖ TRỐNG (`putIfAbsent`): một hồ sơ đã có sẵn khoá trong ảnh chụp — vì người dùng đã đổi hồ sơ
 * sau khi nâng cấp — thì giữ nguyên của nó. Ghi đè ở đây là xoá lựa chọn vừa mới đặt.
 *
 * ⚠ Dấu đã-di-trú ghi **CÙNG một lượt** với dữ liệu, đúng bài học của [migrateScenesOnce]: tách hai lượt thì một
 * lần chết máy giữa chừng cho lượt sau chạy lại trên dữ liệu đã chuyển.
 *
 * ⚠ Khoá đang VẮNG ở tệp sống vẫn được rót — dưới dạng `null` tường minh (thẻ `n` của [PrefSnapshot]), cùng hợp
 * đồng với [snapshotClusterNav]. Bỏ nó đi là để sổ ĐÃ-DẪN của hồ sơ vừa rời **tràn sang** hồ sơ mới; xem chú
 * thích tại chỗ.
 *
 * Chạy xong là **đặt dấu** rồi thôi, kể cả khi không có gì để rót — khỏi quét lại mỗi lần mở.
 */
internal fun WorkspacePrefs.migrateNavScheduleOnce() {
    if (sp.getBoolean(K_MIGRATED_NAV_SCHEDULE, false)) return
    val e = sp.edit()
    // Senior review 2.84: đọc ảnh qua [storedSnapshot], KHÔNG `sp.getString` — cùng lỗ [migrateClusterProfileOnce] đã vá
    // (Pass 3): máy nâng thẳng từ ≤ 2.81 (lượt này chưa từng chạy) mà có ảnh sai kiểu từ tệp nhập thì `getString` NÉM
    // ngay trong `init` của `PrefsWorkspaceRepository` ⇒ HOME sập mỗi lần mở, không bao giờ tới được dấu chạy-một-lần.
    val stored = sp.all
    ProfileScope.CLUSTERNAV_PROFILE_STATE_KEYS.entries
        .groupBy({ it.value }, { it.key })
        .forEach { (file, stateKeys) ->
            // Hai khoá của cùng một tính năng phải đi cùng nhau: luật (đã có trong danh mục) + sổ đã-dẫn (không).
            val keys = (ProfileScope.CLUSTERNAV_KEYS[file].orEmpty().toSet() + stateKeys)
                .filter { it.startsWith("nav_automation") }
            if (keys.isEmpty()) return@forEach
            val live = clusterNavPrefs(file).all
            // ⚠⚠ Khoá VẮNG ở tệp sống cũng phải vào ảnh, dưới dạng `null` TƯỜNG MINH — đúng hợp đồng đã ghi ở
            // KDoc [snapshotClusterNav] và thẻ `n` của [PrefSnapshot]. Lọc `null` ra (bản đầu của hàm này) là bỏ
            // khoá ấy khỏi ảnh của mọi hồ sơ, và lượt [applyClusterNav] khi đó **giữ nguyên giá trị của hồ sơ vừa
            // rời**. Ca thật: lúc di trú, `nav_automation_fired` thường CHƯA có (chưa lịch nào bắn) ⇒ vắng ở mọi
            // ảnh ⇒ hồ sơ A bắn xong, đổi sang B thì sổ đã-dẫn của A vẫn còn sống, mà luật của B là BẢN SAO cùng
            // id (chính lượt di trú này rót xuống) ⇒ B bị coi là **đã bắn** và bỏ đúng một lượt, im lặng. Đó là
            // R5 của spec `kachi-profile-scope-nav-schedule`.
            val carry: Map<String, Any?> = keys.associateWith { live[it] }
            val suffix = ProfileScope.snapshotSuffix(file)
            profiles().forEach { p ->
                val shot = PrefSnapshot.decode(storedSnapshot(stored, keyOf(p, suffix))).toMutableMap()
                var touched = false
                carry.forEach { (k, v) -> if (k !in shot) { shot[k] = v; touched = true } }
                if (touched) e.putString(keyOf(p, suffix), PrefSnapshot.encode(shot))
            }
        }
    e.putBoolean(K_MIGRATED_NAV_SCHEDULE, true).apply()
}

/**
 * Dấu đã chuyển *tự dẫn đường theo lịch* từ theo-XE sang theo-HỒ-SƠ — xem [migrateNavScheduleOnce].
 *
 * Đặt ở ĐÂY chứ không trong `WorkspacePrefs` vì đó là nơi DUY NHẤT đọc nó, và vì tệp kia đã sát trần 500 dòng
 * (CLAUDE.md §4.1) — thêm vào đấy là đẩy nó qua trần, bài canh kích thước đỏ ngay. Hằng ở cạnh chỗ dùng cũng
 * đúng hơn: dấu di trú thuộc về phép di trú.
 */
private const val K_MIGRATED_NAV_SCHEDULE = "migrated_nav_schedule_v1"


/**
 * DI TRÚ MỘT LẦN — V-CLUSTER (owner 2026-09-30: *"Phần cụm lưu hết thành profile nhé"*).
 *
 * Khoá đổi phạm vi ở bản này ([ProfileScopeCluster.MOVED_TO_PROFILE]): `cast_enabled` · `cast_bubble_visible` · 6 khoá
 * camera sở thích · `bubbleX/bubbleY` · cả họ `cast_geometry` (DPI/khung từng app). Không di trú thì ảnh chụp CŨ của mọi
 * hồ sơ không có chúng ⇒ lượt áp không chạm ⇒ hồ sơ nào được chụp trước thì chiếm cấu hình, các hồ sơ còn lại lệch dần.
 *
 * Rót giá trị ĐANG SỐNG vào ảnh của **mọi** hồ sơ, chỉ điền chỗ trống — phép tính ở [ProfileScopeMigration.rotDown]
 * (thuần, `ProfileScopeMigrationTest`). `cast_enabled` rót đúng giá trị HIỆU LỰC (trước bản này chưa có bản chờ).
 *
 * ⚠ Dấu ghi CÙNG một `Editor` với mọi ảnh (bài học [migrateScenesOnce]); chạy sau [migrateNavScheduleOnce] ở `init`
 * của `PrefsWorkspaceRepository`, tức TRƯỚC lượt `load()` đầu tiên (lượt đó kéo theo `applyClusterNav`).
 */
internal fun WorkspacePrefs.migrateClusterProfileOnce() {
    if (sp.getBoolean(K_MIGRATED_CLUSTER, false)) return
    val e = sp.edit()
    val names = profiles()
    // Đọc ảnh qua [storedSnapshot], KHÔNG `sp.getString`: ảnh sai kiểu (tệp nhập ≤ 2.83) mà ném ở đây là launcher sập
    // mỗi lần mở. Ảnh sai kiểu = "chưa có ảnh" ⇒ `rotDown` rót một ảnh sạch đè lên — lượt này đồng thời CHỮA nó.
    val stored = sp.all
    ProfileScopeCluster.MOVED_TO_PROFILE.forEach { (file, newKeys) ->
        val suffix = ProfileScope.snapshotSuffix(file)
        val shots = names.associateWith { PrefSnapshot.decode(storedSnapshot(stored, keyOf(it, suffix))) }
        val live = clusterNavPrefs(file).all
        ProfileScopeMigration
            .rotDown(shots, live, newKeys, ProfileScopeCluster.familiesOf(file), ProfileScopeCluster.DEFERRED)
            .forEach { (p, shot) -> e.putString(keyOf(p, suffix), PrefSnapshot.encode(shot)) }
    }
    e.putBoolean(K_MIGRATED_CLUSTER, true).apply()
}

/**
 * Dấu đã rót cấu hình cụm xuống mọi hồ sơ — xem [migrateClusterProfileOnce]. Literal (không trỏ hằng `:core`) để
 * `SettingsCoverageContractTest` đọc được và đòi lý do ở `SettingsCatalog.NOT_SETTINGS`; bài
 * `ClusterProfileScopeCoverageTest` khoá nó BẰNG [ProfileScopeCluster.MIGRATED_KEY].
 */
private const val K_MIGRATED_CLUSTER = "migrated_cluster_profile_v1"

/**
 * 2.92 · PROFILE-NEW-KEYS — rót mọi khoá ClusterNav theo hồ sơ CHƯA có trong sổ đã-rót xuống ảnh đã có của mọi hồ sơ.
 *
 * Hai lượt trên chạy MỘT lần cho một bảng khoá cố định, nên khoá vào phạm vi hồ sơ ở bản sau (2.89 `cast_style`, 2.90
 * `vm_bubble_hidden`, 2.92 `camera_projection`/`camera_zoom`) chưa từng được rót: [ĐO máy ảo 2.92] chọn *Thẳng rộng*
 * 120 % ở hồ sơ A rồi đổi sang hồ sơ lưu bằng 2.91 ⇒ hồ sơ đó cũng *Thẳng rộng* 120 %, và lượt rời nó chụp luôn giá trị
 * lạc. Sổ `tệp/khoá` thay cho cờ boolean ⇒ bản sau thêm khoá là tự rót, không phải nhớ viết thêm một lượt di trú.
 *
 * Phép tính ở [ProfileScopeMigration.fillNewKeys] (thuần, `ProfileScopeMigrationTest`): chỉ điền chỗ trống, chỉ vào ảnh
 * ĐÃ có, giá trị = đang sống (vắng ⇒ `null` tường minh ⇒ lượt áp trả về mặc định). Lần đầu (sổ rỗng) duyệt mọi khoá —
 * ảnh đủ khoá không đổi byte.
 *
 * ⚠ Sổ ghi CÙNG một `Editor` với mọi ảnh (bài học [migrateScenesOnce]); chạy SAU [migrateClusterProfileOnce] ở `init`
 * của `PrefsWorkspaceRepository`, TRƯỚC lượt `load()` đầu tiên — tức trước khi người lái kịp chọn giá trị cho khoá mới.
 */
internal fun WorkspacePrefs.fillNewProfileKeysOnce() {
    // Đọc qua `sp.all` + ép kiểu an toàn, KHÔNG `getStringSet`: giá trị sai kiểu mà ném ở `init` là HOME sập mỗi lần mở.
    val stored = sp.all
    val done = (stored[K_PROFILE_KEYS_FILLED] as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
    val pending = ProfileScopeMigration.pendingKeys(ProfileScope.CLUSTERNAV_KEYS, done)
    if (pending.isEmpty()) return
    val e = sp.edit()
    val names = profiles()
    var filled = 0
    pending.forEach { (file, newKeys) ->
        val suffix = ProfileScope.snapshotSuffix(file)
        val shots = names.associateWith { PrefSnapshot.decode(storedSnapshot(stored, keyOf(it, suffix))) }
        ProfileScopeMigration
            .fillNewKeys(shots, clusterNavPrefs(file).all, newKeys, ProfileScopeCluster.DEFERRED)
            .forEach { (p, shot) -> e.putString(keyOf(p, suffix), PrefSnapshot.encode(shot)); filled++ }
    }
    e.putStringSet(K_PROFILE_KEYS_FILLED, ProfileScopeMigration.ledgerOf(ProfileScope.CLUSTERNAV_KEYS)).apply()
    // Một dòng cho log phiên / `ClusterDiag` (CLAUDE.md §11): xe ngoài đường không có adb — đọc log là biết lượt rót đã chạy.
    Log.i("KachiProfile", "rót khoá mới theo hồ sơ: ${pending.values.sumOf { it.size }} khoá · $filled ảnh")
}

/**
 * Sổ đã-rót của [fillNewProfileKeysOnce]. Literal (không trỏ hằng `:core`), cùng khuôn hai dấu trên; `ClusterProfileScopeCoverageTest`
 * khoá nó BẰNG [ProfileScopeMigration.FILLED_LEDGER_KEY]. Xếp loại theo XE: `ProfileScopeCluster.DEVICE_KEYS` + `SettingsCatalog.NOT_SETTINGS`.
 */
private const val K_PROFILE_KEYS_FILLED = "profile_keys_filled_v1"

/**
 * Tệp prefs của phía ClusterNav theo tên. Mở qua `Context` (mỗi tên là một `SharedPreferences` riêng) — Android
 * cache theo tên trong cùng tiến trình, nên đây **vẫn là** đúng đối tượng mà dịch vụ đang giữ, và mọi listener đã
 * đăng ký (`registerOnSharedPreferenceChangeListener`) đều nhận được lượt ghi này.
 */
internal fun WorkspacePrefs.clusterNavPrefs(file: String): SharedPreferences =
    appCtx.getSharedPreferences(file, Context.MODE_PRIVATE)

