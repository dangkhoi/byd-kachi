package com.byd.clusternav.launcher.voice

/**
 * ═══ NHỚ bảng nhãn app theo CHỮ KÝ của danh sách app — chỉ dựng lại khi có app đổi (VOICE-SESSION-READY-CACHE) ═════
 *
 * [ĐO xe 15/10 18:37:32.844 → 35.075, 3.1.8] phiên lệnh sau "Hey Kachi" mất **2,2 s** trước dòng cảnh báo nhãn trùng
 * của `VoiceWiring.appIndex` — tức trong lượt dựng bảng gọi app (hỏi `PackageManager` + `loadLabel` ~40 app), lặp lại
 * MỖI phiên, kèm dòng *"Aurora Store trùng"* mỗi phiên. [SUY] phần đắt là `loadLabel` (mở tài nguyên từng APK); mốc
 * giờ tách từng bước nay được in để buổi xe sau chốt [ĐO].
 *
 * Chữ ký do chỗ gọi dựng từ thứ RẺ mà `queryIntentActivities` đã trả (gói · activity · đường APK · mã nhãn · locale):
 * cài/gỡ/cập nhật một app hoặc đổi ngôn ngữ ⇒ chữ ký đổi ⇒ dựng lại; còn lại ⇒ dùng lại, 0 lượt `loadLabel`. Không
 * dùng hạn giờ: một app vừa cài phải gọi được ngay ở phiên kế (luật cũ của `appsByLabel`), và chữ ký bảo đảm điều đó.
 *
 * Thuần, an toàn đa luồng (một khoá) — phiên `:wake`, ô gõ lệnh và Cài đặt có thể hỏi cùng lúc.
 */
class VoiceLabelMemo<T : Any> {
    private val lock = Any()
    private var key: List<String>? = null
    private var value: T? = null

    /** Kết quả + `true` nếu vừa DỰNG LẠI (chỗ gọi chỉ ghi log cảnh báo nhãn trùng khi dựng lại). */
    fun of(signature: List<String>, build: () -> T): Pair<T, Boolean> {
        synchronized(lock) {
            val v = value
            if (v != null && key == signature) return v to false
        }
        val built = build()   // ngoài khoá: `build` chậm (PackageManager) không được chặn lượt hỏi trùng chữ ký khác
        synchronized(lock) { key = signature; value = built }
        return built to true
    }

    fun clear() = synchronized(lock) { key = null; value = null }
}
