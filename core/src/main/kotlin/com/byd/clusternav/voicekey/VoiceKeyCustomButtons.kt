package com.byd.clusternav.voicekey

/**
 * Một **nút tự học** (Cài đặt › Phím vô-lăng › Học phím mới): tên người dùng đặt + mã phím + nguồn (2.88).
 *
 * [name] là chữ của NGƯỜI DÙNG (kèm hậu tố mã/nguồn do tầng Settings ghép từ tài nguyên lúc lưu) — không dịch.
 * [source] `null` = nút học không rõ nguồn (mọi nút học trước 2.88, hoặc phím không thuộc bảng đầu dò, hoặc lượt đọc
 * nguồn hụt lúc bấm Lưu — spec `kachi-288-key-source-split` R1).
 */
data class VoiceKeyCustomButton(val name: String, val keyCode: Int, val source: KeySourceKind? = null)

/**
 * LUẬT THUẦN của danh sách nút tự học (`:app` lo JSON — `VoiceKeyCustomButtonStore`).
 *
 * Khoá của một nút là **(mã, nguồn)** (R1): học lại cùng (mã, nguồn) ⇒ thay tên; cùng mã khác nguồn ⇒ hai nút riêng.
 * Trước 2.88 khoá là mã phím đơn lẻ, mọi nút cũ có nguồn `null` ⇒ luật cũ là trường hợp riêng, hành vi y nguyên.
 */
object VoiceKeyCustomButtons {

    /**
     * Thêm nút, thay nút cùng (mã, nguồn) nếu có. Nút mới/được thay xuống CUỐI danh sách — đúng hành vi trước 2.88
     * (`filterNot { mã } + mới`), để danh sách hiện như owner đã quen.
     */
    fun put(current: List<VoiceKeyCustomButton>, button: VoiceKeyCustomButton): List<VoiceKeyCustomButton> =
        current.filterNot { it.keyCode == button.keyCode && it.source == button.source } + button

    /** Xoá đúng nút (mã, nguồn) — nút cùng mã khác nguồn giữ nguyên (R4). */
    fun remove(current: List<VoiceKeyCustomButton>, keyCode: Int, source: KeySourceKind? = null): List<VoiceKeyCustomButton> =
        current.filterNot { it.keyCode == keyCode && it.source == source }
}
