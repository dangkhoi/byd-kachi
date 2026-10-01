package com.byd.clusternav.launcher

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * ═══ PROFILE-IMPORT-TYPES (lớp ĐỌC) — đọc khoá theo hồ sơ của `kachi_workspace` mà KHÔNG ném khi giá trị sai kiểu ══════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §12.4.4 · backlog `PROFILE-IMPORT-TYPES`. Lớp nhập/xuất
 * ([ProfileScopeLauncher.check] trong [ProfileTransfer]) chặn tệp MỚI; lớp này lo thứ ĐÃ nằm trên đĩa.
 *
 * ## Bệnh [ĐO source]
 * `SharedPreferencesImpl.getString` ép kiểu thẳng — `String v = (String)mMap.get(key);` (android-10.0.0_r47
 * `core/java/android/app/SharedPreferencesImpl.java:288`, `getBoolean` :331; android-12.0.0_r34 :302/:345) — trong
 * `synchronized (mLock)` và KHÔNG sửa `mMap` ⇒ một giá trị sai kiểu NÉM `ClassCastException`, khoá nhả theo khối, bắt lại
 * là an toàn. Lượt nhập hồ sơ tới 2.84 chép nguyên kiểu đã giải mã từ tệp ⇒ máy đã nhập một tệp `b|slot_0|true` mang
 * `<hồ sơ>__slot_0` = Boolean; `load()` đọc `slot_*` của MỌI hồ sơ khác (`widgetIdsOtherProfiles`) ⇒ màn chính sập ở MỌI
 * lần mở, kể cả sau khi cài bản có lớp nhập mới.
 *
 * ## Hợp đồng
 * Sai kiểu = VẮNG: trả `null`, chỗ gọi lấy đúng mặc định nó dùng cho khoá chưa từng ghi. Log MỘT lần mỗi khoá mỗi tiến
 * trình (`load()` chạy ở mọi lượt nạp — log mỗi lượt là phun rác vào logcat). KHÔNG xoá khoá ở lượt đọc: lượt GHI kế tiếp
 * của chính tính năng đó (lưu ô, đổi chip…) ghi đè đúng kiểu ⇒ tự lành, đường đọc không có lượt ghi ẩn nào.
 *
 * Chỉ bắt `ClassCastException` (đúng thứ AOSP ném ở đây) — lỗi khác vẫn nổi lên. [onMistyped] là cửa cho test JVM
 * (`android.util.Log` không chạy được ngoài máy); mã thật dùng mặc định.
 *
 * Bài canh: `LauncherProfileTypesCoverageTest` đòi MỌI lượt đọc khoá theo hồ sơ trong `WorkspacePrefs*.kt` đi qua hai
 * hàm này và kiểu đọc/ghi khớp [ProfileScopeLauncher.DECLARED_TYPES]; `PrefsTypedReadTest` chạy thật chúng trên một
 * `SharedPreferences` giả ép kiểu y như AOSP.
 */

/** Chuỗi ở [key]; `null` = vắng HOẶC sai kiểu (khi đó [onMistyped] được gọi). */
internal fun SharedPreferences.stringOrNull(key: String, onMistyped: (String) -> Unit = ::reportMistyped): String? {
    // Thân KHỐI, không `= try {…}`: thân biểu thức có `} catch` thụt 4 — `SourceRoots.body` cắt ở đó và bài canh
    // `LauncherProfileTypesCoverageTest` không còn thấy nhánh `catch` ([ĐO] đỏ 2026-10-01).
    return try {
        getString(key, null)
    } catch (e: ClassCastException) {
        onMistyped(key)
        null
    }
}

/**
 * Cờ ở [key]; `null` = vắng HOẶC sai kiểu. Hỏi `contains` trước vì `getBoolean` không phân biệt "vắng" với "bằng mặc
 * định" — chỗ gọi cần `null` để tự chọn mặc định của nó (`dock_visible` vắng = hiện, `top_strip_migrated_ux5b` vắng = chưa).
 */
internal fun SharedPreferences.booleanOrNull(key: String, onMistyped: (String) -> Unit = ::reportMistyped): Boolean? {
    if (!contains(key)) return null
    return try {
        getBoolean(key, false)
    } catch (e: ClassCastException) {
        onMistyped(key)
        null
    }
}

/** Khoá đã báo trong tiến trình này. Chặn trên bởi số khoá sai kiểu đang nằm trên đĩa — không lớn dần theo lượt đọc. */
private val reported: MutableSet<String> = ConcurrentHashMap.newKeySet()

/**
 * Nhãn log ASCII, cùng lối nhãn `"import launcher"` của lượt nhập: [logDropped] không phải lời gọi `Log.*` trần nên
 * `LauncherI18nContractTest` coi chuỗi có dấu ở đây là chữ trên màn ([ĐO] đỏ lượt full 2026-10-01) — và log thì dự án
 * cố ý không dịch, nên nhãn không cần tiếng Việt.
 */
private fun reportMistyped(key: String) {
    if (reported.add(key)) logDropped("read kachi_workspace (mistyped = absent)", listOf(key))
}
