package com.byd.clusternav.voicekey

/**
 * Một dòng gán: **mã phím vật lý → đích mở** (package name của app, hoặc một sentinel như
 * `__ASSIST__` / `__RECOGNIZER__` / `__VOICEKEY231__` — ý nghĩa sentinel do tầng app định nghĩa,
 * xem `Prefs.VK_TARGET_*` và `AssistantLauncher`).
 *
 * F3 (owner 2026-08-24): *"binding nhiều nút vào nhiều app… chọn nút + chọn app xong → add, thì ra 1 dòng
 * đã binding nút và app… mình listen thì listen theo cái danh sách đã save đó thôi"*.
 */
data class VoiceKeyBinding(val keyCode: Int, val targetSpec: String)

/**
 * LOGIC THUẦN (không Android) cho **danh sách gán phím**. Tách khỏi lưu trữ: `:app` lo JSON +
 * SharedPreferences (`Prefs.voiceKeyBindings`), còn mọi luật về nội dung danh sách nằm ở đây để test
 * off-device được (`CLAUDE.md §10`).
 *
 * BẤT BIẾN CỐT LÕI — **một mã phím chỉ gán MỘT đích**. Không có bất biến này thì [targetFor] phải chọn
 * giữa nhiều dòng cùng mã ⇒ tra bảng không còn tất định ⇒ cùng một nút bấm có thể mở app khác nhau tuỳ
 * thứ tự lưu. [put] cưỡng chế bất biến khi ghi; [sanitize] cưỡng chế lại khi đọc từ bộ nhớ bền (file
 * prefs có thể bị sửa tay / hỏng / đến từ bản cũ).
 */
object VoiceKeyBindings {

    /**
     * @property bindings danh sách sau khi thêm/ghi đè.
     * @property replaced đích CŨ của [put]'s keyCode nếu mã phím đó ĐÃ được gán (⇒ vừa bị ghi đè),
     *   `null` nếu đây là dòng mới. Tầng UI dùng để **báo cho owner biết đã thay cái gì** — owner yêu cầu
     *   *"thêm trùng ⇒ ghi đè + báo, không im lặng"*.
     */
    data class PutResult(val bindings: List<VoiceKeyBinding>, val replaced: String?)

    /**
     * Thêm dòng gán, hoặc **ghi đè** nếu mã phím đã có. Ghi đè giữ NGUYÊN VỊ TRÍ dòng cũ (không đẩy xuống
     * cuối) để danh sách trên màn hình không nhảy chỗ dưới tay owner.
     */
    fun put(current: List<VoiceKeyBinding>, keyCode: Int, targetSpec: String): PutResult {
        val replaced = current.firstOrNull { it.keyCode == keyCode }?.targetSpec
        val next =
            if (replaced == null) current + VoiceKeyBinding(keyCode, targetSpec)
            else current.map { if (it.keyCode == keyCode) VoiceKeyBinding(keyCode, targetSpec) else it }
        return PutResult(next, replaced)
    }

    /** Xoá dòng gán của [keyCode]. Mã không có trong danh sách ⇒ trả nguyên danh sách (no-op). */
    fun remove(current: List<VoiceKeyBinding>, keyCode: Int): List<VoiceKeyBinding> =
        current.filterNot { it.keyCode == keyCode }

    /** Tra đích cho một mã phím. `null` ⇒ phím KHÔNG được gán ⇒ tầng trên phải để phím đi tiếp (pass-through). */
    fun targetFor(bindings: List<VoiceKeyBinding>, keyCode: Int): String? =
        bindings.firstOrNull { it.keyCode == keyCode }?.targetSpec

    /**
     * Dọn danh sách đọc từ bộ nhớ bền: bỏ dòng có đích rỗng, khử trùng mã phím (**giữ dòng ĐẦU** — cùng
     * quy ước với [targetFor] nên đọc-rồi-dọn không đổi kết quả tra bảng).
     */
    fun sanitize(raw: List<VoiceKeyBinding>): List<VoiceKeyBinding> {
        val seen = HashSet<Int>()
        return raw.filter { it.targetSpec.isNotBlank() && seen.add(it.keyCode) }
    }

    /**
     * NÂNG CẤP KHÔNG MẤT CẤU HÌNH (bắt buộc — máy owner đang chạy cấu hình MỘT-cặp của 1.19).
     *
     * Trước F3 lưu trữ là một cặp duy nhất: `voicekey_keycode` (Int) + `voicekey_target` (String).
     * Cả hai đều CÓ GIÁ TRỊ MẶC ĐỊNH, nên "đọc ra được một cặp" KHÔNG chứng minh owner từng cấu hình gì —
     * máy vừa cài mới cũng đọc ra (328 → Kiki). Vì vậy điều kiện dựng dòng gán đầu tiên phải dựa vào
     * **dấu vết thật** trong file prefs, không dựa vào giá trị đọc được:
     *
     * - [hasLegacyKeyCode] / [hasLegacyTarget] — owner ĐÃ từng chọn nút / chọn app (khoá tồn tại thật).
     * - [enabled] — owner ĐÃ bật công tắc tính năng, tức đang xài cặp mặc định mà không đụng dropdown.
     *
     * Không dấu vết nào ⇒ trả **danh sách RỖNG** (máy mới ⇒ không tự gán gì; đúng yêu cầu owner
     * *"danh sách rỗng thì không có gì chạy"*).
     *
     * Hàm này chỉ chạy MỘT lần cho mỗi máy: tầng lưu trữ ghi kết quả xuống khoá danh sách ngay sau đó, nên
     * lần đọc sau đã có khoá danh sách và không migrate lại (nếu migrate lại, owner xoá hết dòng gán rồi
     * mở lại app sẽ thấy dòng cũ **sống lại** — chính là bẫy này).
     */
    fun migrateLegacy(
        hasLegacyKeyCode: Boolean,
        hasLegacyTarget: Boolean,
        enabled: Boolean,
        keyCode: Int,
        targetSpec: String,
    ): List<VoiceKeyBinding> =
        if (hasLegacyKeyCode || hasLegacyTarget || enabled) sanitize(listOf(VoiceKeyBinding(keyCode, targetSpec)))
        else emptyList()
}
