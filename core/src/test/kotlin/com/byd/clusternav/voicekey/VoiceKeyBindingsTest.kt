package com.byd.clusternav.voicekey

import org.junit.jupiter.api.Assertions.assertEquals
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
}
