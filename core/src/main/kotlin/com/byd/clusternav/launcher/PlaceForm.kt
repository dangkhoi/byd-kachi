package com.byd.clusternav.launcher

/**
 * ═══ KACHI-301 R11 — LUẬT KIỂM của hộp *Thêm/Sửa địa chỉ*, thuần (`:core`) ═══════════════════════════════════
 *
 * Hộp mới kiểm TẠI CHỖ (ô đỏ + câu lỗi, nút Lưu khoá) thay vì báo lỗi SAU khi bấm Lưu. Luật kiểm phải là **đúng
 * luật** mà đường lưu ([SavedPlaces.of]) dùng — không phải bản chép tay: bản chép thì một ngày nút Lưu sáng mà lưu
 * vẫn bị từ chối (hoặc ngược lại). Nên mọi vế ở đây gọi thẳng các mảnh của [SavedPlaces]:
 *  - tên / địa chỉ "thiếu" = rỗng sau [SavedPlaces.sanitize] — đúng phép [SavedPlaces.of] dùng để trả `null`;
 *  - toạ độ "hỏng" = có chữ mà [SavedPlaces.parseCoords] không đọc được. Rỗng ⇒ hợp lệ (toạ độ TUỲ CHỌN,
 *    `kachi-voice-addresses` R1).
 *
 * ⚠ Khác biệt có chủ ý với [SavedPlaces.of]: `of` nhận toạ độ hỏng và **lặng lẽ** lưu mục không toạ độ (đường
 * giọng nói / nhập tệp cần thế). Hộp sửa thì không được lặng lẽ: người dùng vừa dán một chuỗi, chuỗi đó hỏng ⇒
 * nói ra và khoá Lưu. `PlaceFormTest` khoá cả hai chiều (ok ⇔ `of` != null khi toạ độ trống/đúng).
 */
object PlaceForm {

    /** Kết quả kiểm — từng vế riêng để tầng vẽ đặt lỗi đúng ô. */
    data class Check(val nameMissing: Boolean, val addressMissing: Boolean, val coordsBad: Boolean) {
        val ok: Boolean get() = !nameMissing && !addressMissing && !coordsBad
    }

    fun check(name: String, address: String, coords: String): Check = Check(
        nameMissing = SavedPlaces.sanitize(name).isEmpty(),
        addressMissing = SavedPlaces.sanitize(address).isEmpty(),
        coordsBad = coords.isNotBlank() && SavedPlaces.parseCoords(coords) == null,
    )

    /**
     * Chỉ số chip nhãn nhanh khớp [name] (so bằng [SavedPlaces.keyOf] — cùng định nghĩa *"cùng một nơi"* của sổ),
     * hoặc `-1` ⇒ chip *"Khác"* + ô tên tự do. Sửa mục *"nhà"* (chữ thường) vẫn chọn đúng chip *"Nhà"*.
     */
    fun presetIndex(name: String, presets: List<String>): Int {
        val k = SavedPlaces.keyOf(name)
        if (k.isEmpty()) return -1
        return presets.indexOfFirst { SavedPlaces.keyOf(it) == k }
    }
}
