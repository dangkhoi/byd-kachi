package com.byd.clusternav.voicekey

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá luật của **danh sách gán phím** (F3, owner 2026-08-24).
 *
 * Hai thứ dễ hỏng nhất và đều KHÔNG lộ ra ở tầng UI:
 * 1. **Ghi đè im lặng** — thêm trùng mã phím mà không báo ⇒ owner tưởng vừa gán thêm, thực ra vừa mất một gán.
 * 2. **Migrate mất cấu hình** — máy owner đang chạy cấu hình một-cặp của 1.19; nâng cấp mà đọc nhầm điều
 *    kiện là nút vô-lăng câm ngay lần khởi động sau, y hệt ca F1 hôm nay (badge câm 2 ngày, test vẫn xanh).
 */
class VoiceKeyBindingsTest {

    private val kiki = "ai.zalo.kiki.car"
    private val gemini = "__VOICEKEY231__"
    private val vietmap = "com.vietmap.s1"

    @Test
    fun `them dong moi thi noi duoi va khong bao ghi de`() {
        val r1 = VoiceKeyBindings.put(emptyList(), 328, kiki)
        assertNull(r1.replaced, "danh sách rỗng thì không có gì để ghi đè")
        val r2 = VoiceKeyBindings.put(r1.bindings, 231, gemini)
        assertNull(r2.replaced)
        assertEquals(listOf(VoiceKeyBinding(328, kiki), VoiceKeyBinding(231, gemini)), r2.bindings)
    }

    /** Owner: "thêm trùng ⇒ ghi đè + báo cho owner biết đã thay, không im lặng". */
    @Test
    fun `them trung ma phim thi ghi de va TRA VE dich cu`() {
        val start = VoiceKeyBindings.put(emptyList(), 328, kiki).bindings
        val r = VoiceKeyBindings.put(start, 328, vietmap)
        assertEquals(kiki, r.replaced, "phải trả đích CŨ để UI báo 'đã thay X → Y'")
        assertEquals(listOf(VoiceKeyBinding(328, vietmap)), r.bindings)
        assertEquals(1, r.bindings.size, "ghi đè, KHÔNG được đẻ thêm dòng thứ hai cùng mã phím")
    }

    /** Ghi đè giữ NGUYÊN vị trí — danh sách trên màn hình không nhảy chỗ dưới tay owner. */
    @Test
    fun `ghi de giu nguyen vi tri dong`() {
        val start = listOf(VoiceKeyBinding(328, kiki), VoiceKeyBinding(231, gemini), VoiceKeyBinding(87, vietmap))
        val r = VoiceKeyBindings.put(start, 231, kiki)
        assertEquals(listOf(328, 231, 87), r.bindings.map { it.keyCode })
        assertEquals(kiki, r.bindings[1].targetSpec)
    }

    @Test
    fun `xoa dung dong con lai giu nguyen`() {
        val start = listOf(VoiceKeyBinding(328, kiki), VoiceKeyBinding(231, gemini))
        assertEquals(listOf(VoiceKeyBinding(231, gemini)), VoiceKeyBindings.remove(start, 328))
        assertEquals(start, VoiceKeyBindings.remove(start, 999), "xoá mã không có = no-op")
        assertTrue(VoiceKeyBindings.remove(VoiceKeyBindings.remove(start, 328), 231).isEmpty())
    }

    @Test
    fun `tra bang dung dich va tra null khi chua gan`() {
        val list = listOf(VoiceKeyBinding(328, kiki), VoiceKeyBinding(231, gemini))
        assertEquals(kiki, VoiceKeyBindings.targetFor(list, 328))
        assertEquals(gemini, VoiceKeyBindings.targetFor(list, 231))
        assertNull(VoiceKeyBindings.targetFor(list, 25))
        assertNull(VoiceKeyBindings.targetFor(emptyList(), 328))
    }

    /**
     * File prefs có thể hỏng / bị sửa tay / đến từ bản trước ⇒ đọc xong phải cưỡng chế lại bất biến.
     * Giữ dòng ĐẦU để trùng quy ước với [VoiceKeyBindings.targetFor] (đọc-rồi-dọn không đổi kết quả tra bảng).
     */
    @Test
    fun `don danh sach hong — bo dich rong va khu trung ma giu dong DAU`() {
        val dirty = listOf(
            VoiceKeyBinding(328, kiki),
            VoiceKeyBinding(328, vietmap),   // trùng mã → bỏ
            VoiceKeyBinding(231, ""),        // đích rỗng → bỏ
            VoiceKeyBinding(87, "   "),      // đích toàn khoảng trắng → bỏ
            VoiceKeyBinding(84, gemini),
        )
        val clean = VoiceKeyBindings.sanitize(dirty)
        assertEquals(listOf(VoiceKeyBinding(328, kiki), VoiceKeyBinding(84, gemini)), clean)
        assertEquals(
            VoiceKeyBindings.targetFor(dirty, 328), VoiceKeyBindings.targetFor(clean, 328),
            "dọn xong tra bảng phải ra CÙNG kết quả, nếu không việc dọn tự nó đổi hành vi",
        )
    }

    // ─── MIGRATE: nâng cấp KHÔNG được làm mất cấu hình owner đang có ────────────────────────────

    @Test
    fun `may dang co cau hinh mot cap — migrate giu nguyen`() {
        assertEquals(
            listOf(VoiceKeyBinding(328, kiki)),
            VoiceKeyBindings.migrateLegacy(
                hasLegacyKeyCode = true, hasLegacyTarget = true, enabled = true, keyCode = 328, targetSpec = kiki,
            ),
        )
    }

    /** Owner chỉ chọn NÚT (chưa đụng dropdown app) ⇒ vẫn phải giữ, ghép với đích mặc định đọc được. */
    @Test
    fun `chi co dau vet ma phim van migrate`() {
        assertEquals(
            listOf(VoiceKeyBinding(328, kiki)),
            VoiceKeyBindings.migrateLegacy(
                hasLegacyKeyCode = true, hasLegacyTarget = false, enabled = false, keyCode = 328, targetSpec = kiki,
            ),
        )
    }

    /** Owner chỉ chọn APP ⇒ giữ, ghép với mã phím mặc định đọc được. */
    @Test
    fun `chi co dau vet dich van migrate`() {
        assertEquals(
            listOf(VoiceKeyBinding(328, gemini)),
            VoiceKeyBindings.migrateLegacy(
                hasLegacyKeyCode = false, hasLegacyTarget = true, enabled = false, keyCode = 328, targetSpec = gemini,
            ),
        )
    }

    /**
     * Ca DỄ MẤT NHẤT: owner bật công tắc rồi xài thẳng cặp mặc định (328 → Kiki) mà không đụng dropdown nào
     * ⇒ file prefs KHÔNG có khoá nào của cặp cũ, chỉ có cờ bật. Bỏ vế `enabled` là nút vô-lăng câm sau khi
     * cập nhật — mà mọi test khác vẫn xanh.
     */
    @Test
    fun `bat cong tac nhung xai cap mac dinh — VAN phai migrate`() {
        assertEquals(
            listOf(VoiceKeyBinding(328, kiki)),
            VoiceKeyBindings.migrateLegacy(
                hasLegacyKeyCode = false, hasLegacyTarget = false, enabled = true, keyCode = 328, targetSpec = kiki,
            ),
        )
    }

    /** Máy vừa cài mới: không dấu vết, công tắc tắt ⇒ RỖNG. Không tự gán nút vô-lăng cho app nào. */
    @Test
    fun `may moi cai thi danh sach RONG`() {
        assertTrue(
            VoiceKeyBindings.migrateLegacy(
                hasLegacyKeyCode = false, hasLegacyTarget = false, enabled = false, keyCode = 328, targetSpec = kiki,
            ).isEmpty(),
        )
    }

    /** Cấu hình cũ có đích rỗng (prefs hỏng) ⇒ migrate ra RỖNG, không tạo dòng gán mở "app tên rỗng". */
    @Test
    fun `cau hinh cu hong thi migrate ra RONG`() {
        assertTrue(
            VoiceKeyBindings.migrateLegacy(
                hasLegacyKeyCode = true, hasLegacyTarget = true, enabled = true, keyCode = 328, targetSpec = "",
            ).isEmpty(),
        )
    }

    // ─── 2.88 · KEY-SOURCE-SPLIT tầng 2 — gán theo NGUỒN (spec kachi-288-key-source-split R2/R4/R5) ──────────────────
    // Núm bệ giữa và nút âm lượng vô-lăng ra CÙNG mã 291/292 [ĐO xe owner 04/10]. Khoá của một dòng nay là (mã, nguồn).

    private val fanUp = "ctl:fan_up"
    private val KNOB = KeySourceKind.CONSOLE_KNOB
    private val WHEEL = KeySourceKind.STEERING_WHEEL

    /** Mã bền trong JSON là chuỗi cố định, KHÔNG phải tên enum — đổi tên hằng không được làm mất cấu hình trên xe. */
    @Test
    fun `ma nguon ben la knob va wheel, ma la thi null`() {
        assertEquals("knob", KNOB.code)
        assertEquals("wheel", WHEEL.code)
        assertEquals(KNOB, KeySourceKind.fromCode("knob"))
        assertEquals(WHEEL, KeySourceKind.fromCode("wheel"))
        listOf(null, "", "KNOB", "CONSOLE_KNOB", "pedal").forEach { assertNull(KeySourceKind.fromCode(it), "mã lạ: $it") }
    }

    /** R2: cùng mã khác nguồn = hai dòng riêng; dòng không nguồn và dòng có nguồn cùng mã được phép cùng tồn tại. */
    @Test
    fun `cung ma khac nguon la hai dong rieng, dong khong nguon cung song chung`() {
        var list = VoiceKeyBindings.put(emptyList(), 291, fanUp, KNOB).bindings
        val r = VoiceKeyBindings.put(list, 291, kiki, WHEEL)
        assertNull(r.replaced, "khác nguồn KHÔNG phải ghi đè")
        list = VoiceKeyBindings.put(r.bindings, 291, vietmap).bindings
        assertEquals(
            listOf(VoiceKeyBinding(291, fanUp, KNOB), VoiceKeyBinding(291, kiki, WHEEL), VoiceKeyBinding(291, vietmap)),
            list,
        )
    }

    /** Ghi đè chỉ xảy ra trên ĐÚNG (mã, nguồn) và trả đích cũ của chính khoá đó; vị trí giữ nguyên. */
    @Test
    fun `ghi de theo ma va nguon tra dich cu cua dung khoa`() {
        val start = listOf(VoiceKeyBinding(291, fanUp, KNOB), VoiceKeyBinding(291, kiki), VoiceKeyBinding(292, gemini, KNOB))
        val r = VoiceKeyBindings.put(start, 291, vietmap, KNOB)
        assertEquals(fanUp, r.replaced)
        assertEquals(listOf(VoiceKeyBinding(291, vietmap, KNOB), VoiceKeyBinding(291, kiki), VoiceKeyBinding(292, gemini, KNOB)), r.bindings)
        val generic = VoiceKeyBindings.put(start, 291, gemini)
        assertEquals(kiki, generic.replaced, "dòng không nguồn ghi đè dòng không nguồn, không đụng dòng núm")
        assertEquals(fanUp, generic.bindings[0].targetSpec)
    }

    /** R4: xoá đúng (mã, nguồn) — dòng cùng mã khác nguồn KHÔNG bị kéo theo. Gọi 2 tham số = xoá dòng không nguồn. */
    @Test
    fun `xoa theo ma va nguon khong dung dong khac nguon`() {
        val start = listOf(VoiceKeyBinding(291, fanUp, KNOB), VoiceKeyBinding(291, kiki, WHEEL), VoiceKeyBinding(291, vietmap))
        assertEquals(listOf(VoiceKeyBinding(291, kiki, WHEEL), VoiceKeyBinding(291, vietmap)), VoiceKeyBindings.remove(start, 291, KNOB))
        assertEquals(start.take(2), VoiceKeyBindings.remove(start, 291))
        assertEquals(start, VoiceKeyBindings.remove(start, 292, KNOB), "không có ⇒ no-op")
    }

    /**
     * R3: dòng (mã, nguồn) trước, rồi dòng (mã, không nguồn). Không biết nguồn (`null`) ⇒ CHỈ dòng không nguồn — dòng
     * có nguồn không bao giờ bắt một lần nhấn chưa rõ nút (đó là nút KIA thì sao?).
     */
    @Test
    fun `tra dich theo nguon roi lui ve dong khong nguon`() {
        val knobOnly = listOf(VoiceKeyBinding(291, fanUp, KNOB))
        assertEquals(fanUp, VoiceKeyBindings.targetFor(knobOnly, 291, KNOB))
        assertNull(VoiceKeyBindings.targetFor(knobOnly, 291, WHEEL), "vô-lăng chưa gán ⇒ đi tiếp (âm lượng như xe gốc)")
        assertNull(VoiceKeyBindings.targetFor(knobOnly, 291, null), "không biết nguồn ⇒ không dùng dòng núm")
        assertNull(VoiceKeyBindings.targetFor(knobOnly, 291))
        val withGeneric = knobOnly + VoiceKeyBinding(291, kiki)
        assertEquals(fanUp, VoiceKeyBindings.targetFor(withGeneric, 291, KNOB))
        assertEquals(kiki, VoiceKeyBindings.targetFor(withGeneric, 291, WHEEL), "không có dòng vô-lăng ⇒ lùi dòng không nguồn")
        assertEquals(kiki, VoiceKeyBindings.targetFor(withGeneric, 291, null))
    }

    /** R-nf3: phím nào cần đọc nguồn do DANH SÁCH GÁN quyết định — không phải một bảng mã viết tay. */
    @Test
    fun `can doc nguon khi va chi khi ma co dong gan theo nguon`() {
        val list = listOf(VoiceKeyBinding(291, fanUp, KNOB), VoiceKeyBinding(292, kiki), VoiceKeyBinding(328, kiki))
        assertTrue(VoiceKeyBindings.needsSource(list, 291))
        assertFalse(VoiceKeyBindings.needsSource(list, 292), "292 chỉ có dòng không nguồn ⇒ đường 2.87, không HAL")
        assertFalse(VoiceKeyBindings.needsSource(list, 328))
        assertFalse(VoiceKeyBindings.needsSource(emptyList(), 291))
        assertTrue(VoiceKeyBindings.anySource(list))
        assertFalse(VoiceKeyBindings.anySource(listOf(VoiceKeyBinding(291, kiki), VoiceKeyBinding(328, kiki))))
    }

    /** Khử trùng theo (mã, nguồn) giữ dòng ĐẦU; cùng mã khác nguồn KHÔNG bị coi là trùng. */
    @Test
    fun `don danh sach khu trung theo ma va nguon`() {
        val dirty = listOf(
            VoiceKeyBinding(291, fanUp, KNOB),
            VoiceKeyBinding(291, kiki, KNOB),       // trùng (291, núm) → bỏ
            VoiceKeyBinding(291, vietmap, WHEEL),
            VoiceKeyBinding(291, gemini),
            VoiceKeyBinding(291, kiki),             // trùng (291, ∅) → bỏ
        )
        assertEquals(
            listOf(VoiceKeyBinding(291, fanUp, KNOB), VoiceKeyBinding(291, vietmap, WHEEL), VoiceKeyBinding(291, gemini)),
            VoiceKeyBindings.sanitize(dirty),
        )
    }

    /** Nút tự học: khoá (mã, nguồn) — học lại cùng khoá thay tên (xuống cuối như 2.87), khác nguồn là hai nút. */
    @Test
    fun `nut tu hoc khoa theo ma va nguon`() {
        var b = VoiceKeyCustomButtons.put(emptyList(), VoiceKeyCustomButton("Núm lên (mã 291 · núm)", 291, KNOB))
        b = VoiceKeyCustomButtons.put(b, VoiceKeyCustomButton("Vô-lăng lên", 291, WHEEL))
        b = VoiceKeyCustomButtons.put(b, VoiceKeyCustomButton("cũ", 291))
        assertEquals(3, b.size, "cùng mã, ba nguồn khác nhau (núm / vô-lăng / không nguồn) ⇒ ba nút")
        b = VoiceKeyCustomButtons.put(b, VoiceKeyCustomButton("Núm mới", 291, KNOB))
        assertEquals(listOf("Vô-lăng lên", "cũ", "Núm mới"), b.map { it.name }, "học lại cùng (mã, nguồn) ⇒ thay tên, xuống cuối")
        assertEquals(listOf("Vô-lăng lên", "Núm mới"), VoiceKeyCustomButtons.remove(b, 291).map { it.name })
        assertEquals(listOf("cũ", "Núm mới"), VoiceKeyCustomButtons.remove(b, 291, WHEEL).map { it.name })
    }
}
