package com.byd.clusternav

import com.byd.clusternav.modules.voicekey.VoiceKeyBindingStore
import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import com.byd.clusternav.voicekey.VoiceKeyAction
import com.byd.clusternav.voicekey.VoiceKeyBinding
import com.byd.clusternav.voicekey.VoiceKeyBindings
import com.byd.clusternav.voicekey.VoiceKeyConfig
import com.byd.clusternav.voicekey.VoiceKeyMatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * **F3** — gán NHIỀU phím cho NHIỀU app (owner 2026-08-24: *"chọn nút + chọn app xong → add, thì ra 1 dòng
 * đã binding nút và app… mình listen thì listen theo cái danh sách đã save đó thôi"*).
 *
 * ── VÌ SAO TEST ĐI QUA CẢ CHUỖI, KHÔNG CHỈ TEST TỪNG MẢNH ────────────────────────────────────────
 * Bài học phải trả giá sáng nay (F1 + `debugForceBadge`): **nút thử ≠ đường thật**. Một hàm xanh không
 * chứng minh dữ liệu chạy hết được đoạn đường mà người dùng đi. Ở đây đường thật là:
 *
 *   bấm "Thêm gán" → `VoiceKeyBindings.put` → `VoiceKeyBindingStore.encode` → SharedPreferences
 *   → (app khởi động lại) → `VoiceKeyBindingStore.decode` → `VoiceKeyConfig`
 *   → `VoiceKeyMatcher.onKey` → `AssistantLauncher.launch(spec)`
 *
 * Off-device không dựng được `SharedPreferences` (repo không dùng Robolectric — xem `app/build.gradle.kts`),
 * nên test này chia hai vế và khoá cả hai:
 *  1. **Hành vi**: chạy nguyên chuỗi trên với một ô nhớ giả thay đúng vị trí SharedPreferences.
 *  2. **Nối dây**: quét source để chắc `Prefs`/`NavAccessibilityService`/`MainActivity` thật sự gọi đúng
 *     chuỗi đó — không có vế này thì vế 1 chỉ là mô hình đẹp bên cạnh một đường thật đã đứt (đúng ca F1).
 */
class VoiceKeyBindingListTest {

    private val KIKI = "ai.zalo.kiki.car"
    private val GEMINI = "__VOICEKEY231__"
    private val VIETMAP = "com.vietmap.s1"

    private val prefsSrc: String by lazy {
        KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/Prefs.kt"))
    }
    private val serviceSrc: String by lazy {
        KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/modules/navaccess/NavAccessibilityService.kt"))
    }
    /**
     * Bề mặt "gán phím" nay là nhóm *Phím vô-lăng* của Kachi Settings + cầu — màn ClusterNav cũ (và cả hai
     * biến thể `activity_main.xml` mang dropdown/`btn_voicekey_add`) đã gỡ 2026-09-13 (S3 · R1).
     */
    private val keysSection: String by lazy {
        KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/launcher/SettingsSectionsKeys.kt"))
    }
    private val keysBridge: String by lazy {
        KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeKeys.kt"))
    }

    // ── Ô NHỚ GIẢ: đúng một chuỗi JSON, y như khoá `voicekey_bindings` trong SharedPreferences ──────
    private class FakeStore(var json: String? = null) {
        fun read(): List<VoiceKeyBinding> = VoiceKeyBindingStore.decode(json)
        /** Bản sao đúng của `Prefs.addVoiceKeyBinding`: put → ghi → trả đích cũ. */
        fun add(keyCode: Int, spec: String): String? {
            val r = VoiceKeyBindings.put(read(), keyCode, spec)
            json = VoiceKeyBindingStore.encode(r.bindings)
            return r.replaced
        }
        /** Bản sao đúng của `Prefs.removeVoiceKeyBinding`. */
        fun remove(keyCode: Int) {
            json = VoiceKeyBindingStore.encode(VoiceKeyBindings.remove(read(), keyCode))
        }
    }

    /** Bấm một nút vật lý đúng như `onKeyEvent`: DOWN rồi UP. Trả đích được mở, null nếu không mở gì. */
    private fun press(matcher: VoiceKeyMatcher, bindings: List<VoiceKeyBinding>, keyCode: Int, downTime: Long): String? {
        val cfg = VoiceKeyConfig(enabled = true, bindings = bindings)
        val down = matcher.onKey(cfg, VoiceKeyAction.DOWN, keyCode, downTime)
        matcher.onKey(cfg, VoiceKeyAction.UP, keyCode, downTime)
        return if (down.fire) down.targetSpec else null
    }

    // ─── VẾ 1 — HÀNH VI TRÊN ĐÚNG ĐƯỜNG NGƯỜI DÙNG ĐI ───────────────────────────────────────────

    /** Đường chính: gán 3 nút cho 3 app, khởi động lại, bấm từng nút → mở đúng app của nút đó. */
    @Test
    fun `gan ba nut ba app roi khoi dong lai — moi nut mo dung app`() {
        val store = FakeStore()
        assertNull(store.add(328, KIKI))
        assertNull(store.add(231, GEMINI))
        assertNull(store.add(87, VIETMAP))

        // "Khởi động lại app": chỉ còn chuỗi JSON, mọi thứ trong RAM đã mất.
        val afterRestart = FakeStore(store.json).read()
        assertEquals(3, afterRestart.size)

        val m = VoiceKeyMatcher()
        assertEquals(KIKI, press(m, afterRestart, 328, 10))
        assertEquals(GEMINI, press(m, afterRestart, 231, 20))
        assertEquals(VIETMAP, press(m, afterRestart, 87, 30))
    }

    /** Phím chưa gán ⇒ không mở gì (và ở tầng matcher cũng không nuốt — xem VoiceKeyMatcherTest). */
    @Test
    fun `phim khong co trong danh sach thi khong lam gi`() {
        val store = FakeStore()
        store.add(328, KIKI)
        val m = VoiceKeyMatcher()
        assertNull(press(m, FakeStore(store.json).read(), 25, 10))
    }

    /** Danh sách rỗng (máy mới, hoặc owner đã xoá hết) ⇒ không nút nào mở gì. */
    @Test
    fun `danh sach rong thi khong nut nao chay`() {
        val m = VoiceKeyMatcher()
        val empty = FakeStore().read()
        assertTrue(empty.isEmpty())
        assertNull(press(m, empty, 328, 10))
        assertNull(press(m, empty, 231, 20))
    }

    /** Thêm trùng mã phím ⇒ GHI ĐÈ + trả đích cũ (UI dùng để báo), và nút đó mở app MỚI. */
    @Test
    fun `them trung ma phim thi ghi de va nut mo app moi`() {
        val store = FakeStore()
        store.add(328, KIKI)
        val replaced = store.add(328, VIETMAP)
        assertEquals(KIKI, replaced, "phải trả đích CŨ để UI báo cho owner biết vừa thay cái gì")

        val list = FakeStore(store.json).read()
        assertEquals(1, list.size, "một mã phím chỉ được gán một app")
        assertEquals(VIETMAP, press(VoiceKeyMatcher(), list, 328, 10))
    }

    /** Xoá một dòng ⇒ nút đó trả về pass-through, các nút còn lại KHÔNG bị ảnh hưởng. */
    @Test
    fun `xoa mot dong thi nut do het chay, nut khac giu nguyen`() {
        val store = FakeStore()
        store.add(328, KIKI)
        store.add(231, GEMINI)
        store.remove(328)

        val list = FakeStore(store.json).read()
        val m = VoiceKeyMatcher()
        assertNull(press(m, list, 328, 10), "đã xoá thì phím phải trả lại chức năng gốc")
        assertEquals(GEMINI, press(m, list, 231, 20))
    }

    /** Xoá HẾT ⇒ ô nhớ là `"[]"` (khoá TỒN TẠI), nên lần đọc sau KHÔNG migrate lại làm dòng cũ sống dậy. */
    @Test
    fun `xoa het thi ghi rong chu khong xoa khoa`() {
        val store = FakeStore()
        store.add(328, KIKI)
        store.remove(328)
        assertEquals("[]", store.json)
        assertTrue(FakeStore(store.json).read().isEmpty())
    }

    // ─── VẾ 1b — CODEC chịu được dữ liệu bẩn ────────────────────────────────────────────────────

    @Test
    fun `ma hoa roi giai ma tra ve dung danh sach`() {
        val list = listOf(VoiceKeyBinding(328, KIKI), VoiceKeyBinding(231, GEMINI))
        assertEquals(list, VoiceKeyBindingStore.decode(VoiceKeyBindingStore.encode(list)))
    }

    @Test
    fun `json hong hoac rong thi ra danh sach RONG chu khong no`() {
        listOf(null, "", "   ", "{", "khong-phai-json", "{\"k\":1}", "[1,2,3]").forEach {
            assertTrue(VoiceKeyBindingStore.decode(it).isEmpty(), "đầu vào bẩn phải ra rỗng: $it")
        }
    }

    @Test
    fun `json thieu truong thi bo dong do, giu dong con lai`() {
        val raw = """[{"k":328,"t":"$KIKI"},{"k":231},{"t":"$GEMINI"},{"k":87,"t":"$VIETMAP"}]"""
        assertEquals(listOf(VoiceKeyBinding(328, KIKI), VoiceKeyBinding(87, VIETMAP)), VoiceKeyBindingStore.decode(raw))
    }

    /** Prefs bị sửa tay thành hai dòng cùng mã ⇒ đọc ra phải TẤT ĐỊNH (giữ dòng đầu), không tuỳ may rủi. */
    @Test
    fun `json co hai dong trung ma thi doc ra tat dinh`() {
        val raw = """[{"k":328,"t":"$KIKI"},{"k":328,"t":"$VIETMAP"}]"""
        assertEquals(listOf(VoiceKeyBinding(328, KIKI)), VoiceKeyBindingStore.decode(raw))
        assertEquals(KIKI, press(VoiceKeyMatcher(), VoiceKeyBindingStore.decode(raw), 328, 10))
    }

    // ─── VẾ 2 — NỐI DÂY: đường thật có đi qua đúng chuỗi trên không ──────────────────────────────

    /**
     * `Prefs.voiceKeyBindings` phải (a) đọc khoá danh sách trước, (b) nếu chưa có thì migrate bằng
     * **dấu vết thật** (`contains`) chứ không bằng giá trị đọc được — cả hai khoá cũ đều có mặc định —
     * và (c) GHI xuống ngay để migrate không chạy lại.
     */
    @Test
    fun `Prefs doc danh sach truoc, migrate bang dau vet that, roi ghi xuong`() {
        val body = prefsSrc.substringAfter("fun voiceKeyBindings(").substringBefore("fun addVoiceKeyBinding(")
        assertTrue(body.contains("VoiceKeyBindingStore.rawOrNull(p, K_VK_BINDINGS)"), "phải đọc khoá danh sách trước")
        assertTrue(body.contains("VoiceKeyBindings.migrateLegacy("), "phải dùng luật migrate đã test ở :core")
        assertTrue(body.contains("p.contains(K_VK_KEYCODE)"), "dấu vết mã phím cũ phải là contains(), không phải giá trị")
        assertTrue(body.contains("p.contains(K_VK_TARGET)"), "dấu vết đích cũ phải là contains()")
        assertTrue(body.contains("K_VK_ENABLED"), "phải xét cả ca owner bật công tắc mà xài cặp mặc định")
        assertTrue(body.contains("writeVoiceKeyBindings("), "không ghi xuống ⇒ migrate chạy lại ⇒ dòng đã xoá sống lại")
    }

    /** Thêm/xoá phải đi qua luật `:core` (put/remove), không tự viết lại luật ở tầng lưu trữ. */
    @Test
    fun `Prefs them xoa deu di qua luat core`() {
        assertTrue(prefsSrc.contains("VoiceKeyBindings.put(voiceKeyBindings(p), keyCode, targetSpec)"))
        assertTrue(prefsSrc.contains("VoiceKeyBindings.remove(voiceKeyBindings(p), keyCode)"))
        assertTrue(
            prefsSrc.substringAfter("fun addVoiceKeyBinding(").substringBefore("fun removeVoiceKeyBinding(")
                .contains("return result.replaced"),
            "thêm trùng phải TRẢ VỀ đích cũ, nếu không UI không thể báo 'đã thay'",
        )
    }

    /**
     * ĐƯỜNG NGHE THẬT — `onKeyEvent` phải tra DANH SÁCH và mở đích LẤY TỪ quyết định.
     * Còn đọc `Prefs.voiceKeyCode(`/`Prefs.voiceKeyTargetSpec(` ở đây nghĩa là vẫn đang khớp một-cặp:
     * owner gán 3 nút nhưng chỉ một nút chạy — đúng kiểu lỗi im lặng mà mọi test mảnh vẫn xanh.
     */
    @Test
    fun `onKeyEvent tra DANH SACH va mo dich lay tu quyet dinh`() {
        val body = serviceSrc.substringAfter("override fun onKeyEvent(").substringBefore("override fun onAccessibilityEvent(")
        assertTrue(body.contains("Prefs.voiceKeyBindings(app)"), "phải tra danh sách gán")
        assertTrue(body.contains("bindings = Prefs.voiceKeyBindings(app)"), "danh sách phải đi thẳng vào VoiceKeyConfig")
        assertTrue(body.contains("decision.targetSpec"), "đích phải lấy từ quyết định, không tra prefs lần hai")
        assertTrue(body.contains("AssistantLauncher.launch(app, spec)"), "vẫn phải mở app qua AssistantLauncher")
        assertFalse(body.contains("Prefs.voiceKeyCode("), "còn khớp một-cặp ⇒ chỉ một nút trong danh sách chạy")
        assertFalse(body.contains("Prefs.voiceKeyTargetSpec("), "còn đọc đích một-cặp ⇒ mọi nút mở CÙNG một app")
    }

    /** Công tắc chính + "học phím" giữ nguyên vị trí gác trước phần khớp danh sách. */
    @Test
    fun `cong tac chinh va hoc phim van gac truoc`() {
        val body = serviceSrc.substringAfter("override fun onKeyEvent(").substringBefore("override fun onAccessibilityEvent(")
        val learn = body.indexOf("Prefs.voiceKeyLearn(app)")
        val enabled = body.indexOf("Prefs.voiceKeyEnabled(app)")
        val bindings = body.indexOf("Prefs.voiceKeyBindings(app)")
        assertTrue(learn in 0 until enabled, "học phím phải chặn trước công tắc (đang học thì nuốt hết)")
        assertTrue(enabled in 0 until bindings, "tắt tính năng ⇒ không được đọc/khớp danh sách")
    }

    /**
     * UI phải có đủ 3 bước owner mô tả + danh sách + nút xoá từng dòng + nhắc khi rỗng.
     *
     * ⚠ So khớp **cả dấu nháy đóng** (`@+id/x"`), không phải `contains("x")`. Bản đầu của test này dùng
     * contains trần và ĐÃ BỊ BẮT bằng phép thử làm-đỏ: đổi `btn_voicekey_add` → `btn_voicekey_addnew` đồng
     * bộ ở cả layout lẫn code thì test VẪN XANH, vì tên cũ là tiền tố của tên mới. Đúng nghĩa "test mù".
     */
    @Test
    fun `man hinh co du chon nut chon app Them gan danh sach va nut xoa`() {
        // Tới 2026-09-13: sáu `@+id/...` trong `activity_main.xml` (+ `LayoutVariantIdParityTest` canh bản
        // `layout-w960dp`). Màn đó đã gỡ; cùng SÁU việc ấy nay là các hàng dựng bằng mã ở nhóm *Phím vô-lăng*.
        listOf(
            "bridge.buttonOptions()" to "chọn NÚT",
            "bridge.targetOptions()" to "chọn APP/đích",
            "bridge.addBinding(" to "Thêm gán",
            "bridge.bindings()" to "danh sách đã gán",
            "bridge.removeBinding(" to "nút Xoá của từng dòng",
            "bridge.startLearn" to "học phím mới",
        ).forEach { (token, what) ->
            assertTrue(token in keysSection, "thiếu `$token` — mất bề mặt \"$what\" ở nhóm Phím vô-lăng")
        }
    }

    /** Nút "Thêm gán" phải GHI vào đúng danh sách, và báo khi ghi đè. */
    @Test
    fun `nut Them gan ghi vao danh sach va ve lai ngay`() {
        assertTrue(
            "Prefs.addVoiceKeyBinding(app, keyCode, targetSpec)" in keysBridge,
            "nút Thêm phải ghi vào danh sách (một nguồn chân lý: `voicekey_bindings`)",
        )
        assertTrue("replaced" in keysBridge, "phải báo cho owner khi ghi đè — cấm im lặng")
        assertTrue("isGeminiVoiceSpec" in keysBridge, "công thức đặt trợ lý hệ thống phải theo sang nút Thêm")
        assertTrue("rebuildBindings" in keysSection, "thêm xong phải hiện dòng mới ngay")
    }

    /** Danh sách trên màn hình phải vẽ TỪ `Prefs.voiceKeyBindings` — cùng nguồn mà service nghe theo. */
    @Test
    fun `danh sach tren man hinh ve tu dung nguon service nghe`() {
        assertTrue("Prefs.voiceKeyBindings(app)" in keysBridge, "vẽ từ nguồn khác = màn hình nói dối")
        assertTrue("Prefs.removeVoiceKeyBinding(app, keyCode)" in keysBridge, "nút xoá trên dòng phải xoá thật")
        assertTrue(
            "kachi_keys_no_bindings" in keysSection,
            "danh sách rỗng phải nói rõ là KHÔNG có gì chạy (chuỗi qua tài nguyên, không chữ cứng)",
        )
    }

    /**
     * Chọn trong dropdown KHÔNG được ghi cấu hình nữa — chỉ "Thêm gán"/"Xoá" mới đổi. Nếu dropdown còn ghi,
     * owner lướt qua một app là đã đổi cấu hình mà không hề bấm Thêm.
     */
    @Test
    fun `chon dropdown khong con ghi cau hinh`() {
        assertFalse(keysSection.contains("Prefs.setVoiceKeyCode("), "chọn nút không được ghi cấu hình")
        assertFalse(keysBridge.contains("Prefs.setVoiceKeyTargetSpec("), "chọn app không được ghi cấu hình")
        assertFalse(prefsSrc.contains("fun setVoiceKeyCode("), "hàm ghi cặp cũ phải bỏ, tránh hai nguồn chân lý")
        assertFalse(prefsSrc.contains("fun setVoiceKeyTargetSpec("), "hàm ghi cặp cũ phải bỏ, tránh hai nguồn chân lý")
    }

    /** Đường đọc cặp cũ PHẢI còn — nó là thứ duy nhất giữ cấu hình owner qua lần cập nhật này. */
    @Test
    fun `duong doc cau hinh cu van con de migrate`() {
        assertTrue(prefsSrc.contains("fun voiceKeyCode(ctx: Context)"), "bỏ getter cũ = migrate không có gì để đọc")
        assertTrue(prefsSrc.contains("fun voiceKeyTargetSpec(ctx: Context)"), "bỏ getter cũ = mất cấu hình owner")
        assertTrue(
            prefsSrc.contains("keyCode = voiceKeyCode(p)") && prefsSrc.contains("targetSpec = voiceKeyTargetSpec(p)"),
            "migrate phải thật sự đọc hai giá trị cũ",
        )
    }
}
