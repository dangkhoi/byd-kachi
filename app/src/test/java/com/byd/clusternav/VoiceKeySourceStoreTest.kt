package com.byd.clusternav

import com.byd.clusternav.modules.voicekey.VoiceKeyBindingStore
import com.byd.clusternav.modules.voicekey.VoiceKeyCustomButtonStore
import com.byd.clusternav.voicekey.KeySourceKind
import com.byd.clusternav.voicekey.VoiceKeyAction
import com.byd.clusternav.voicekey.VoiceKeyBinding
import com.byd.clusternav.voicekey.VoiceKeyConfig
import com.byd.clusternav.voicekey.VoiceKeyCustomButton
import com.byd.clusternav.voicekey.VoiceKeyMatcher
import com.byd.clusternav.voicekey.KeySourceLookup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.88 · KEY-SOURCE-SPLIT tầng 2 — LƯU TRỮ có nguồn (spec kachi-288-key-source-split R5, §4.4, T4) ═══════════════════
 *
 * Hai khoá prefs (`voicekey_bindings` · `voicekey_custom_buttons`) mang thêm trường `"s"`. Khoá ở đây:
 *  1. **Nâng cấp không mất cấu hình**: JSON 2.87 (không `"s"`) đọc ra dòng/nút KHÔNG nguồn, hành vi y 2.87.
 *  2. **Mã lạ ⇒ BỎ dòng**, không hạ thành dòng không nguồn (hạ xuống thì dòng "núm" của bản tương lai bắt luôn vô-lăng).
 *  3. **Ghi byte-y-hệt 2.87** khi không có nguồn — so với CHÍNH bộ mã hoá 2.87 (chép nguyên văn ở [Legacy287]) chạy trên
 *     cùng `org.json`: thứ tự khoá trong chuỗi là việc của thư viện (bản Android giữ thứ tự chèn, bản JVM của test thì
 *     không), nên so với một chuỗi viết tay là so sai thứ.
 *  4. Vòng đời đủ: lưu có nguồn → đọc lại → matcher chọn đúng nút.
 */
class VoiceKeySourceStoreTest {

    private val KIKI = "ai.zalo.kiki.car"
    private val FAN = "ctl:fan_up"
    private val KNOB = KeySourceKind.CONSOLE_KNOB
    private val WHEEL = KeySourceKind.STEERING_WHEEL

    /** Bộ mã hoá 2.87 (188) chép NGUYÊN VĂN (`VoiceKeyBindingStore.encode` + `Prefs.writeCustomButtons`) — chuẩn so byte. */
    private object Legacy287 {
        fun bindings(list: List<VoiceKeyBinding>): String {
            val arr = org.json.JSONArray()
            list.forEach { arr.put(org.json.JSONObject().put("k", it.keyCode).put("t", it.targetSpec)) }
            return arr.toString()
        }
        fun buttons(items: List<Pair<String, Int>>): String {
            val arr = org.json.JSONArray()
            items.forEach { arr.put(org.json.JSONObject().put("n", it.first).put("k", it.second)) }
            return arr.toString()
        }
    }

    // ── voicekey_bindings ────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `JSON 2_87 khong co s doc ra dong khong nguon`() {
        val raw = """[{"k":328,"t":"$KIKI"},{"k":291,"t":"$FAN"}]"""
        assertEquals(listOf(VoiceKeyBinding(328, KIKI), VoiceKeyBinding(291, FAN)), VoiceKeyBindingStore.decode(raw))
    }

    @Test
    fun `co s thi doc ra dung nguon, cung ma khac nguon la hai dong`() {
        val raw = """[{"k":291,"t":"$FAN","s":"knob"},{"k":291,"t":"$KIKI","s":"wheel"},{"k":291,"t":"$KIKI"}]"""
        assertEquals(
            listOf(VoiceKeyBinding(291, FAN, KNOB), VoiceKeyBinding(291, KIKI, WHEEL), VoiceKeyBinding(291, KIKI)),
            VoiceKeyBindingStore.decode(raw),
        )
    }

    /** R5 — mã nguồn lạ (bản tương lai / sửa tay) ⇒ bỏ ĐÚNG dòng đó, giữ các dòng còn lại; KHÔNG hạ thành dòng không nguồn. */
    @Test
    fun `s la thi bo dong, khong ha thanh dong khong nguon`() {
        val raw = """[{"k":291,"t":"$FAN","s":"pedal"},{"k":292,"t":"$FAN","s":""},{"k":328,"t":"$KIKI"}]"""
        val got = VoiceKeyBindingStore.decode(raw)
        assertEquals(listOf(VoiceKeyBinding(328, KIKI)), got)
        val m = VoiceKeyMatcher()
        val d = m.onKey(VoiceKeyConfig(true, got), VoiceKeyAction.DOWN, 291, 1) { KeySourceLookup(WHEEL, "wheel") }
        assertFalse(d.consume, "dòng 'pedal' bị hạ xuống không nguồn thì vô-lăng đã bị nuốt — R5 cấm")
    }

    /** Không có nguồn ⇒ chuỗi JSON đúng TỪNG BYTE như bộ mã hoá 2.87 (không `"s"`, không `"s":null`). */
    @Test
    fun `ma hoa khong co nguon y byte 2_87`() {
        val list = listOf(VoiceKeyBinding(328, KIKI), VoiceKeyBinding(231, "__VOICEKEY231__"), VoiceKeyBinding(291, FAN))
        val now = VoiceKeyBindingStore.encode(list)
        assertEquals(Legacy287.bindings(list), now)
        assertFalse(now.contains("\"s\""), now)
        assertEquals("[]", VoiceKeyBindingStore.encode(emptyList()))
    }

    @Test
    fun `ma hoa co nguon ghi s bang ma ben va doc lai nguyen ven`() {
        val list = listOf(VoiceKeyBinding(291, FAN, KNOB), VoiceKeyBinding(291, KIKI, WHEEL), VoiceKeyBinding(292, KIKI))
        val raw = VoiceKeyBindingStore.encode(list)
        assertTrue(raw.contains(""""s":"knob"""") && raw.contains(""""s":"wheel""""), raw)
        assertFalse(raw.contains("CONSOLE_KNOB") || raw.contains("STEERING_WHEEL"), "lưu mã bền, không lưu tên enum: $raw")
        assertEquals(list, VoiceKeyBindingStore.decode(raw))
    }

    /** Sửa tay hai dòng cùng (mã, nguồn) ⇒ đọc ra tất định (dòng đầu); cùng mã khác nguồn KHÔNG bị khử. */
    @Test
    fun `json trung ma va nguon thi giu dong dau`() {
        val raw = """[{"k":291,"t":"$FAN","s":"knob"},{"k":291,"t":"$KIKI","s":"knob"},{"k":291,"t":"$KIKI"}]"""
        assertEquals(listOf(VoiceKeyBinding(291, FAN, KNOB), VoiceKeyBinding(291, KIKI)), VoiceKeyBindingStore.decode(raw))
    }

    // ── voicekey_custom_buttons ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nut tu hoc 2_87 doc ra khong nguon, ma hoa lai y byte`() {
        val raw = """[{"n":"VOLUME UP (mã 291)","k":291},{"n":"Mic","k":328}]"""
        val got = VoiceKeyCustomButtonStore.decode(raw)
        assertEquals(listOf(VoiceKeyCustomButton("VOLUME UP (mã 291)", 291), VoiceKeyCustomButton("Mic", 328)), got)
        assertEquals(
            Legacy287.buttons(listOf("VOLUME UP (mã 291)" to 291, "Mic" to 328)), VoiceKeyCustomButtonStore.encode(got),
            "không có nguồn ⇒ ghi lại đúng từng byte như bộ mã hoá 2.87",
        )
    }

    @Test
    fun `nut tu hoc co s doc dung nguon, s la thi bo nut do`() {
        val raw = """[{"n":"Núm lên","k":291,"s":"knob"},{"n":"Vô-lăng lên","k":291,"s":"wheel"},{"n":"Lạ","k":292,"s":"x"}]"""
        val got = VoiceKeyCustomButtonStore.decode(raw)
        assertEquals(listOf(VoiceKeyCustomButton("Núm lên", 291, KNOB), VoiceKeyCustomButton("Vô-lăng lên", 291, WHEEL)), got)
        assertEquals(got, VoiceKeyCustomButtonStore.decode(VoiceKeyCustomButtonStore.encode(got)))
    }

    /** Dòng hỏng chỉ mất dòng đó (trước 2.88 mất CẢ danh sách); JSON hỏng cả mảng thì rỗng như cũ. */
    @Test
    fun `nut tu hoc json hong`() {
        listOf(null, "", "{", "khong-phai-json").forEach { assertTrue(VoiceKeyCustomButtonStore.decode(it).isEmpty(), "$it") }
        val raw = """[{"n":"A","k":"abc"},{"k":291},{"n":"B","k":292},7]"""
        assertEquals(listOf(VoiceKeyCustomButton("B", 292)), VoiceKeyCustomButtonStore.decode(raw))
    }
}
