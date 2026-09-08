package com.byd.clusternav

import android.content.SharedPreferences
import com.byd.clusternav.voicekey.VoiceKeyBinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Chạy THẬT `Prefs.voiceKeyBindings` — mối nối giữa luật migrate thuần (`VoiceKeyBindings.migrateLegacy`,
 * đã có test riêng ở `:core`) và ô nhớ SharedPreferences.
 *
 * ── VÌ SAO CẦN, DÙ ĐÃ CÓ `VoiceKeyBindingListTest` ─────────────────────────────────────────────────
 * `VoiceKeyBindingListTest` khoá mối nối này bằng **quét chuỗi** (`body.contains("writeVoiceKeyBindings(")`).
 * Quét chuỗi vẫn XANH nếu thân hàm bị đổi thành `writeVoiceKeyBindings(p, emptyList())` — tức đúng ca
 * **owner nâng cấp từ 1.19 và mất sạch cấu hình gán**, ca mà chính spec gọi là nguy hiểm nhất (migrate chỉ
 * chạy ĐÚNG MỘT LẦN; sai là mất vĩnh viễn, không có đường lùi).
 *
 * Test này đi đường thật: cùng một hàm production mà `NavAccessibilityService.onKeyEvent` và
 * `MainActivity.rebuildVoiceKeyBindingList` gọi, chỉ thay ô nhớ bằng bản giả trong RAM.
 *
 * ── PHÉP THỬ LÀM-ĐỎ (`CLAUDE.md §10` · P5.3) ──────────────────────────────────────────────────────
 * Kết quả đo 2026-08-24 (mỗi đột biến khôi phục ngay sau khi đo):
 *  • `writeVoiceKeyBindings(p, migrated)` → `writeVoiceKeyBindings(p, emptyList())`
 *      ⇒ ĐỎ `nang cap tu 1_19 — cau hinh mot-cap cua owner duoc giu nguyen`
 *  • bỏ `VoiceKeyBindingStore.rawOrNull(...)?.let { return ... }` (luôn migrate)
 *      ⇒ ĐỎ `da migrate roi thi KHONG migrate lai — owner xoa het van phai o trang thai rong`
 *  • `hasLegacyKeyCode = false` cứng ⇒ ĐỎ `nang cap tu 1_19 — cau hinh mot-cap cua owner duoc giu nguyen`
 */
class VoiceKeyBindingMigrationTest {

    private val KIKI = "ai.zalo.kiki.car"
    private val VIETMAP = "com.vietmap.s1"

    /**
     * Ô nhớ giả đúng ngữ nghĩa của SharedPreferences: `getString(k, null)` trả `null` khi khoá CHƯA có —
     * đây chính là tín hiệu "chưa migrate bao giờ" mà `Prefs` dựa vào, nên bản giả phải giữ đúng phân biệt
     * `khoá vắng mặt` ↔ `khoá có giá trị "[]"`.
     *
     * `apply()` ghi NGAY (không hoãn) để test quan sát được như trên máy thật sau khi tiến trình ghi xong.
     */
    private class FakePrefs(seed: Map<String, Any> = emptyMap()) : SharedPreferences {
        val store: MutableMap<String, Any> = LinkedHashMap(seed)
        var writes = 0
            private set

        override fun getAll(): MutableMap<String, Any> = LinkedHashMap(store)
        override fun getString(key: String?, defValue: String?): String? = store[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = store[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = store[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = store[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = store[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = store.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { store.keys.toList().forEach { pending[it] = null } }
            override fun commit(): Boolean { flush(); return true }
            override fun apply() = flush()
            private fun apply(block: () -> Unit): SharedPreferences.Editor { block(); return this }
            private fun flush() {
                pending.forEach { (k, v) -> if (v == null) store.remove(k) else store[k] = v }
                pending.clear()
                writes++
            }
        }
    }

    private val K_ENABLED = "voicekey_enabled"
    private val K_KEYCODE = "voicekey_keycode"
    private val K_TARGET = "voicekey_target"
    private val K_BINDINGS = "voicekey_bindings"

    // ── CA 1 — máy VỪA CÀI: không dấu vết, công tắc tắt ⇒ danh sách RỖNG ────────────────────────────

    /**
     * Cả `voicekey_keycode` lẫn `voicekey_target` đều có **giá trị mặc định** trong code (328 / Kiki), nên
     * "đọc ra được một cặp" KHÔNG chứng minh owner từng cấu hình. Máy mới phải ra rỗng, đúng ý owner
     * *"rỗng thì không có gì chạy"* — nếu không, mọi máy vừa cài tự nhiên nuốt phím 328.
     */
    @Test
    fun `may vua cai — khong dau vet, cong tac tat ⇒ danh sach RONG`() {
        val p = FakePrefs()
        assertEquals(emptyList<VoiceKeyBinding>(), Prefs.voiceKeyBindings(p))
        // Đã ghi "[]" xuống ⇒ lần sau không migrate lại nữa.
        assertEquals("[]", p.store[K_BINDINGS])
    }

    // ── CA 2 — NÂNG CẤP TỪ 1.19: cấu hình một-cặp của owner KHÔNG được mất ──────────────────────────

    /** Owner đã đổi nút + đổi app ⇒ có dấu vết thật cả hai khoá. Phải chuyển nguyên vẹn thành 1 dòng. */
    @Test
    fun `nang cap tu 1_19 — cau hinh mot-cap cua owner duoc giu nguyen`() {
        val p = FakePrefs(mapOf(K_ENABLED to true, K_KEYCODE to 220, K_TARGET to VIETMAP))

        val got = Prefs.voiceKeyBindings(p)

        assertEquals(listOf(VoiceKeyBinding(keyCode = 220, targetSpec = VIETMAP)), got)
        // Đã ghi xuống ngay trong lần đọc đầu — không chờ owner bấm gì.
        assertTrue((p.store[K_BINDINGS] as String).contains(VIETMAP), "chưa ghi danh sách: ${p.store[K_BINDINGS]}")
        // Đọc lại (mô phỏng khởi động lại app) ra đúng thứ đó.
        assertEquals(got, Prefs.voiceKeyBindings(FakePrefs(p.store)))
    }

    /**
     * Ca DỄ MẤT NHẤT (mutation M4 của phiên thi công): owner **bật công tắc rồi xài thẳng cặp mặc định**
     * (nút 328 → Kiki). File prefs không có khoá cũ nào, chỉ có mỗi cờ `voicekey_enabled`. Bỏ vế `enabled`
     * khỏi điều kiện migrate ⇒ nút vô-lăng câm sau khi cập nhật, mà mọi test khác vẫn xanh.
     */
    @Test
    fun `bat cong tac nhung xai cap mac dinh — van phai migrate ra mot dong`() {
        val p = FakePrefs(mapOf(K_ENABLED to true))

        assertEquals(
            listOf(VoiceKeyBinding(keyCode = Prefs.VK_KEYCODE_DEFAULT, targetSpec = KIKI)),
            Prefs.voiceKeyBindings(p),
        )
    }

    // ── CA 3 — ĐÃ MIGRATE RỒI: không được chạy lại ─────────────────────────────────────────────────

    /**
     * Owner xoá hết dòng gán ⇒ khoá danh sách = `"[]"`. Nếu migrate chạy lại vì thấy "rỗng", dòng cũ sẽ
     * **sống lại** sau mỗi lần mở app — owner không bao giờ xoá được. Khoá `"[]"` là "đã migrate", khác hẳn
     * "khoá vắng mặt".
     */
    @Test
    fun `da migrate roi thi KHONG migrate lai — owner xoa het van phai o trang thai rong`() {
        val p = FakePrefs(mapOf(K_ENABLED to true, K_KEYCODE to 220, K_TARGET to VIETMAP, K_BINDINGS to "[]"))

        assertEquals(emptyList<VoiceKeyBinding>(), Prefs.voiceKeyBindings(p))
        assertEquals("[]", p.store[K_BINDINGS])
    }

    /** Đã có danh sách thật ⇒ đọc nguyên danh sách đó, không đụng tới khoá cũ. */
    @Test
    fun `da co danh sach that thi doc thang, khong migrate`() {
        val json = """[{"k":220,"t":"$VIETMAP"},{"k":231,"t":"$KIKI"}]"""
        val p = FakePrefs(mapOf(K_KEYCODE to 328, K_TARGET to KIKI, K_BINDINGS to json))

        assertEquals(
            listOf(VoiceKeyBinding(220, VIETMAP), VoiceKeyBinding(231, KIKI)),
            Prefs.voiceKeyBindings(p),
        )
        assertEquals(0, p.writes, "đọc danh sách đã có mà vẫn ghi ⇒ migrate chạy oan")
    }

    // ── CA 4 — THÊM / XOÁ đi qua đúng ô nhớ, sống qua khởi động lại ────────────────────────────────

    /**
     * Đường người dùng đi trọn vẹn: gán 2 nút cho 2 app → khởi động lại → vẫn còn → xoá 1 → còn đúng 1.
     * Cố ý dùng chính `Prefs.addVoiceKeyBinding` / `removeVoiceKeyBinding` (hàm mà nút "Thêm gán" và nút
     * "Xoá" trên từng dòng gọi), không dựng lại logic tương đương trong test.
     */
    @Test
    fun `them hai gan roi khoi dong lai roi xoa mot — di dung ham ma nut bam goi`() {
        val p = FakePrefs()

        assertNull(Prefs.addVoiceKeyBinding(p, 220, VIETMAP))
        assertNull(Prefs.addVoiceKeyBinding(p, 231, KIKI))

        val afterRestart = Prefs.voiceKeyBindings(FakePrefs(p.store))
        assertEquals(listOf(VoiceKeyBinding(220, VIETMAP), VoiceKeyBinding(231, KIKI)), afterRestart)

        Prefs.removeVoiceKeyBinding(p, 220)
        assertEquals(listOf(VoiceKeyBinding(231, KIKI)), Prefs.voiceKeyBindings(p))
    }

    /** Gán trùng mã phím ⇒ ghi đè và **trả về đích cũ** để UI báo "đã THAY bằng…" (cấm im lặng). */
    @Test
    fun `gan trung ma phim thi ghi de va tra ve dich cu`() {
        val p = FakePrefs()
        Prefs.addVoiceKeyBinding(p, 220, VIETMAP)

        assertEquals(VIETMAP, Prefs.addVoiceKeyBinding(p, 220, KIKI))
        assertEquals(listOf(VoiceKeyBinding(220, KIKI)), Prefs.voiceKeyBindings(p))
    }

    /** JSON hỏng (sửa tay / bản trước ghi sai) ⇒ RỖNG, và **không** rơi ngược về migrate. */
    @Test
    fun `json hong thi ra rong, khong migrate lai cau hinh cu`() {
        val p = FakePrefs(mapOf(K_ENABLED to true, K_KEYCODE to 220, K_TARGET to VIETMAP, K_BINDINGS to "{khong-phai-json"))

        assertEquals(emptyList<VoiceKeyBinding>(), Prefs.voiceKeyBindings(p))
    }

    /** Cấu hình ĐỜI ĐẦU: `voicekey_target` còn là số thứ tự (Int) ⇒ đổi sang chuỗi rồi mới migrate. */
    @Test
    fun `cau hinh doi dau target la so thu tu — van migrate duoc`() {
        val p = FakePrefs(mapOf(K_ENABLED to true, K_KEYCODE to 220, K_TARGET to 3))

        assertEquals(
            listOf(VoiceKeyBinding(220, Prefs.VK_TARGET_ASSIST)),
            Prefs.voiceKeyBindings(p),
        )
    }
}
