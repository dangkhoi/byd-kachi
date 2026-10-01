package com.byd.clusternav.launcher

import android.content.SharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * ═══ PROFILE-IMPORT-TYPES (lớp ĐỌC) — chạy THẬT `stringOrNull`/`booleanOrNull` trên giá trị sai kiểu ═══════════════════
 *
 * `:app` không dựng được `WorkspacePrefs` trong JVM (cần `Context`), nên đường đọc được khoá bằng hai mảnh ghép nhau:
 * bài này chạy thật hai cửa đọc; `LauncherProfileTypesCoverageTest` đòi MỌI lượt đọc khoá theo hồ sơ của
 * `WorkspacePrefs*.kt` đi qua chúng.
 *
 * Bản giả ÉP KIỂU Y NHƯ AOSP (`(String)mMap.get(key)` — android-10.0.0_r47 `SharedPreferencesImpl.java:288`, `getBoolean`
 * :331) ⇒ sai kiểu NÉM. Bài đầu tiên chứng minh điều đó: không có nó thì một bản giả "dễ dãi" (`as?`, như
 * `VoiceKeyBindingMigrationTest`) làm mọi ca dưới đây xanh kể cả khi cửa đọc không bắt gì.
 */
class PrefsTypedReadTest {

    /** Chỉ phần ĐỌC. Ghi không thuộc phạm vi bài (cửa đọc không được ghi — xem KDoc `PrefsTypedRead.kt`). */
    private class AospCastPrefs(private val store: Map<String, Any>) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = LinkedHashMap(store)
        override fun getString(key: String?, defValue: String?): String? = store[key] as String? ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = store[key] as Int? ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = store[key] as Long? ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = store[key] as Float? ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = store[key] as Boolean? ?: defValue
        override fun contains(key: String?): Boolean = store.containsKey(key)
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException("cửa đọc không được ghi")
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    /** Đĩa của một máy đã nhập tệp độc bằng bản ≤ 2.84 (hồ sơ `B`), cạnh hồ sơ `A` lành. */
    private val disk = AospCastPrefs(
        mapOf(
            "B__slot_0" to true, "B__preset" to 3, "B__top_strip" to 7L, "B__grid_layout" to 1.5f,
            "B__dock_visible" to "yes", "B__top_strip_labels" to 1,
            "A__slot_0" to "app:x", "A__preset" to "TWO", "A__dock_visible" to false, "A__top_strip_labels" to true,
        ),
    )

    private val seen = mutableListOf<String>()

    @Test
    fun `ban gia nem dung nhu AOSP - khong co thi cac bai duoi xanh gia`() {
        assertThrows(ClassCastException::class.java) { disk.getString("B__slot_0", null) }
        assertThrows(ClassCastException::class.java) { disk.getBoolean("B__dock_visible", true) }
    }

    @Test
    fun `chuoi sai kieu - Boolean Int Long Float - doc thanh vang va duoc bao`() {
        listOf("B__slot_0", "B__preset", "B__top_strip", "B__grid_layout").forEach {
            assertNull(disk.stringOrNull(it) { k -> seen += k }, it)
        }
        assertEquals(listOf("B__slot_0", "B__preset", "B__top_strip", "B__grid_layout"), seen)
    }

    @Test
    fun `co sai kieu - String Int - doc thanh vang va duoc bao`() {
        assertNull(disk.booleanOrNull("B__dock_visible") { seen += it })
        assertNull(disk.booleanOrNull("B__top_strip_labels") { seen += it })
        assertEquals(listOf("B__dock_visible", "B__top_strip_labels"), seen)
    }

    @Test
    fun `dung kieu va vang - tra nguyen, khong bao`() {
        assertEquals("app:x", disk.stringOrNull("A__slot_0") { seen += it })
        assertEquals("TWO", disk.stringOrNull("A__preset") { seen += it })
        assertNull(disk.stringOrNull("A__top_strip") { seen += it }, "vắng ⇒ null")
        // `false` đã lưu KHÁC vắng: chỗ gọi `dock_visible` lấy mặc định `true` khi vắng — trả null ở đây là bật lại thanh
        // người lái đã tắt.
        assertEquals(false, disk.booleanOrNull("A__dock_visible") { seen += it })
        assertEquals(true, disk.booleanOrNull("A__top_strip_labels") { seen += it })
        assertNull(disk.booleanOrNull("A__nothing") { seen += it })
        assertEquals(emptyList<String>(), seen, "giá trị lành không được ghi log")
    }

    /** Mặc định của chỗ gọi (`load()`/`loadDock()`) áp đúng như với khoá vắng — y hệt dạng biểu thức trong `WorkspacePrefs`. */
    @Test
    fun `mac dinh cua cho goi ap len gia tri sai kieu`() {
        val preset = runCatching { LayoutPreset.valueOf(disk.stringOrNull("B__preset") { } ?: LayoutPreset.THREE.name) }
            .getOrDefault(LayoutPreset.THREE)
        assertEquals(LayoutPreset.THREE, preset)
        assertEquals(true, disk.booleanOrNull("B__dock_visible") { } ?: true, "dock_visible sai kiểu = vắng = hiện")
        assertEquals("", disk.stringOrNull("B__slot_0") { } ?: "", "ô sai kiểu = ô trống, không sập")
    }
}
