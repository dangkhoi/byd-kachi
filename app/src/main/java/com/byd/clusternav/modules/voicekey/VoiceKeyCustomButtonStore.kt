package com.byd.clusternav.modules.voicekey

import android.content.SharedPreferences
import com.byd.clusternav.voicekey.KeySourceKind
import com.byd.clusternav.voicekey.VoiceKeyCustomButton

/**
 * LƯU TRỮ **nút tự học** (`voicekey_custom_buttons`) — bộ chuyển đổi JSON ↔ [VoiceKeyCustomButton], cùng khuôn và cùng
 * lý do đặt ở `:app` với [VoiceKeyBindingStore] (`org.json` là thư viện nền tảng Android; luật thuần — khoá (mã, nguồn),
 * thay/xoá — ở `:core` `VoiceKeyCustomButtons`). Nhận [SharedPreferences] để là đúng tầng lưu trữ (`LayeringRulesTest`).
 *
 * Tách khỏi `Prefs.kt` ở 2.88 (spec `kachi-288-key-source-split` §4.4): `Prefs.kt` đã ~490/500 dòng; khoá + lượt ghi
 * (`putString(K_VK_CUSTOM, …)`) vẫn ở `Prefs.kt` — một khoá, một chỗ ghi.
 *
 * JSON mỗi nút: `{"n":tên,"k":mã}` + `"s":"knob"|"wheel"` CHỈ khi nút có nguồn ([KeySourceKind.code]).
 *  - thiếu `"s"` ⇒ nút không nguồn (mọi nút học trước 2.88 đọc ra y nguyên);
 *  - `"s"` mang mã lạ (bản tương lai) ⇒ BỎ nút đó (R5 — cùng luật với dòng gán);
 *  - một dòng hỏng (thiếu `"n"`/`"k"`) chỉ mất dòng đó. Trước 2.88 một dòng hỏng làm mất CẢ danh sách
 *    (`runCatching` bọc cả mảng) — JSON hỏng cả mảng thì vẫn ra rỗng như cũ;
 *  - ghi: danh sách không có nút nguồn nào mã hoá ra ĐÚNG từng byte như 2.87.
 */
object VoiceKeyCustomButtonStore {

    private const val K_NAME = "n"
    private const val K_KEYCODE = "k"
    private const val K_SOURCE = "s"

    fun read(sp: SharedPreferences, key: String): List<VoiceKeyCustomButton> = decode(sp.getString(key, null))

    /** JSON hỏng / null / sai kiểu ⇒ danh sách RỖNG (như trước 2.88). */
    fun decode(raw: String?): List<VoiceKeyCustomButton> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = try {
            org.json.JSONArray(raw)
        } catch (e: org.json.JSONException) {
            return emptyList()
        }
        // org.json ném JSONException khi "n"/"k" sai kiểu ⇒ chỉ bỏ dòng đó.
        return (0 until arr.length()).mapNotNull { i ->
            try {
                row(arr.optJSONObject(i))
            } catch (e: org.json.JSONException) {
                null
            }
        }
    }

    private fun row(o: org.json.JSONObject?): VoiceKeyCustomButton? {
        if (o == null || !o.has(K_NAME) || !o.has(K_KEYCODE)) return null
        val source = if (o.has(K_SOURCE)) KeySourceKind.fromCode(o.optString(K_SOURCE)) ?: return null else null
        return VoiceKeyCustomButton(name = o.getString(K_NAME), keyCode = o.getInt(K_KEYCODE), source = source)
    }

    fun encode(items: List<VoiceKeyCustomButton>): String {
        val arr = org.json.JSONArray()
        items.forEach {
            val o = org.json.JSONObject().put(K_NAME, it.name).put(K_KEYCODE, it.keyCode)
            it.source?.let { s -> o.put(K_SOURCE, s.code) }
            arr.put(o)
        }
        return arr.toString()
    }
}
