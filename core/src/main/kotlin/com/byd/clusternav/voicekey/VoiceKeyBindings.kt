package com.byd.clusternav.voicekey

/**
 * Một dòng gán: **mã phím vật lý (+ nguồn, nếu có) → đích mở** (package name của app, hoặc một sentinel như
 * `__ASSIST__` / `__RECOGNIZER__` / `__VOICEKEY231__` — ý nghĩa sentinel do tầng app định nghĩa,
 * xem `Prefs.VK_TARGET_*` và `AssistantLauncher`).
 *
 * F3 (owner 2026-08-24): *"binding nhiều nút vào nhiều app… chọn nút + chọn app xong → add, thì ra 1 dòng
 * đã binding nút và app… mình listen thì listen theo cái danh sách đã save đó thôi"*.
 *
 * 2.88 · KEY-SOURCE-SPLIT tầng 2 (spec `kachi-288-key-source-split` R2): [source] = nút vật lý mà dòng này chỉ bắt
 * ([KeySourceKind] — núm bệ giữa / vô-lăng). `null` = dòng KHÔNG nguồn, bắt mọi nút ra mã đó — đúng nghĩa mọi dòng gán
 * trước 2.88, nên tham số mặc định giữ nguyên mọi call site cũ.
 */
data class VoiceKeyBinding(val keyCode: Int, val targetSpec: String, val source: KeySourceKind? = null)

/**
 * LOGIC THUẦN (không Android) cho **danh sách gán phím**. Tách khỏi lưu trữ: `:app` lo JSON +
 * SharedPreferences (`Prefs.voiceKeyBindings`), còn mọi luật về nội dung danh sách nằm ở đây để test
 * off-device được (`CLAUDE.md §10`).
 *
 * BẤT BIẾN CỐT LÕI — **một (mã phím, nguồn) chỉ gán MỘT đích** (trước 2.88: một mã phím một đích; dòng cũ có nguồn
 * `null` nên bất biến cũ là trường hợp riêng của bất biến mới). Không có bất biến này thì [targetFor] phải chọn giữa
 * nhiều dòng cùng khoá ⇒ tra bảng không còn tất định ⇒ cùng một nút bấm có thể mở app khác nhau tuỳ thứ tự lưu.
 * [put] cưỡng chế bất biến khi ghi; [sanitize] cưỡng chế lại khi đọc từ bộ nhớ bền (file prefs có thể bị sửa tay /
 * hỏng / đến từ bản cũ). Dòng không nguồn và dòng có nguồn CÙNG mã được phép cùng tồn tại (R2).
 */
object VoiceKeyBindings {

    /**
     * @property bindings danh sách sau khi thêm/ghi đè.
     * @property replaced đích CŨ của CÙNG (keyCode, source) mà [put] vừa ghi đè, `null` nếu đây là dòng mới. Tầng UI
     *   dùng để **báo cho owner biết đã thay cái gì** — owner yêu cầu *"thêm trùng ⇒ ghi đè + báo, không im lặng"*.
     */
    data class PutResult(val bindings: List<VoiceKeyBinding>, val replaced: String?)

    /**
     * Thêm dòng gán, hoặc **ghi đè** nếu (mã phím, nguồn) đã có. Ghi đè giữ NGUYÊN VỊ TRÍ dòng cũ (không đẩy xuống
     * cuối) để danh sách trên màn hình không nhảy chỗ dưới tay owner. Cùng mã KHÁC nguồn ⇒ dòng mới, không đụng dòng kia.
     */
    fun put(current: List<VoiceKeyBinding>, keyCode: Int, targetSpec: String, source: KeySourceKind? = null): PutResult {
        val replaced = current.firstOrNull { it.matches(keyCode, source) }?.targetSpec
        val row = VoiceKeyBinding(keyCode, targetSpec, source)
        val next =
            if (replaced == null) current + row
            else current.map { if (it.matches(keyCode, source)) row else it }
        return PutResult(next, replaced)
    }

    /**
     * Xoá dòng gán của ĐÚNG (mã phím, nguồn) — dòng cùng mã khác nguồn giữ nguyên (R4). Không có ⇒ trả nguyên danh sách.
     */
    fun remove(current: List<VoiceKeyBinding>, keyCode: Int, source: KeySourceKind? = null): List<VoiceKeyBinding> =
        current.filterNot { it.matches(keyCode, source) }

    /**
     * Tra đích cho một lần nhấn mã [keyCode] đến từ nút [source] (R3): dòng (mã, nguồn) trước, không có thì dòng
     * (mã, không nguồn). [source] `null` (không biết nguồn / phím không cần nguồn) ⇒ CHỈ dòng không nguồn — dòng có nguồn
     * không bao giờ bắt một lần nhấn chưa rõ nút. `null` ⇒ phím KHÔNG được gán ⇒ tầng trên để phím đi tiếp (pass-through).
     */
    fun targetFor(bindings: List<VoiceKeyBinding>, keyCode: Int, source: KeySourceKind? = null): String? =
        (source?.let { s -> bindings.firstOrNull { it.matches(keyCode, s) } } ?: bindings.firstOrNull { it.matches(keyCode, null) })
            ?.targetSpec

    /**
     * Mã [keyCode] có dòng gán THEO NGUỒN nào không — chỉ khi có thì lần nhấn mới tốn một lượt đọc nguồn (R3, R-nf1).
     * Phím nào cần nguồn do chính danh sách gán quyết định (R-nf3), không do một bảng mã viết tay.
     */
    fun needsSource(bindings: List<VoiceKeyBinding>, keyCode: Int): Boolean =
        bindings.any { it.keyCode == keyCode && it.source != null }

    /** Danh sách có dòng gán theo nguồn nào không (đọc mồi lúc dịch vụ nối — spec §4.3). */
    fun anySource(bindings: List<VoiceKeyBinding>): Boolean = bindings.any { it.source != null }

    /**
     * Dọn danh sách đọc từ bộ nhớ bền: bỏ dòng có đích rỗng, khử trùng (mã phím, nguồn) (**giữ dòng ĐẦU** — cùng
     * quy ước với [targetFor] nên đọc-rồi-dọn không đổi kết quả tra bảng).
     */
    fun sanitize(raw: List<VoiceKeyBinding>): List<VoiceKeyBinding> {
        val seen = HashSet<Pair<Int, KeySourceKind?>>()
        return raw.filter { it.targetSpec.isNotBlank() && seen.add(it.keyCode to it.source) }
    }

    private fun VoiceKeyBinding.matches(keyCode: Int, source: KeySourceKind?): Boolean =
        this.keyCode == keyCode && this.source == source

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
