package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.TelemetryRegistry

/**
 * ═══ V2 pha NGHE · NGUỒN HOTWORDS — CỤM CONTROL **CÓ DẤU** TỪ CHÍNH DANH MỤC ═════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html` §Design. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Vì sao tệp riêng, không dùng [VoiceGrammar.terms]
 * [VoiceGrammar.terms] trả chữ **đã bỏ dấu** (phục vụ tầng so khớp chữ). Hotwords của sherpa cần chữ **HOA CÓ
 * DẤU** để bộ mã hoá BPE khớp bảng token của mô hình VN ([SherpaHotwords] giải thích). Nên ở đây đi thẳng vào 4
 * bộ đăng ký lấy **nhãn gốc có dấu** — cùng NGUỒN với [VoicePhrases.build] (Vosk), khác cách trình bày.
 *
 * ## Chỉ tập TĨNH (không hồ sơ/app)
 * Biasing chỉ giúp các **lệnh control tiếng Việt** (đèn/kính/nhiệt độ/âm lượng…). Tên hồ sơ + tên app do người
 * dùng đặt (thường là danh từ riêng / tiếng Anh) — mô hình VN không phát ra được token đó nên biasing vô nghĩa;
 * [VoiceIntentParser] khớp nhãn app lo phần ấy ([ĐO] evidence: `youtube` → "ô tường", biasing không cứu được).
 */
object SherpaBiasing {

    /**
     * Nội dung tệp hotwords (mỗi dòng một cụm HOA có dấu), đã lọc + khử trùng qua [SherpaHotwords].
     * Rỗng ⇒ chạy không biasing.
     */
    fun hotwordsFile(): String = SherpaHotwords.fileContent(accentedControlPhrases())

    /** Mọi nhãn control **có dấu** đáng bias — nhãn nút/datum/macro/hành động launcher + từ khoá nhạc/dẫn đường. */
    fun accentedControlPhrases(): List<String> {
        val out = ArrayList<String>(768)
        ControlRegistry.ALL.forEach { c ->
            out.add(c.label); c.labelEn?.let(out::add); c.short?.let(out::add); c.shortEn?.let(out::add)
            out.addAll(c.args); out.addAll(c.argsEn)
        }
        TelemetryRegistry.ALL.forEach { t ->
            out.add(t.label); t.labelEn?.let(out::add); t.short?.let(out::add); t.shortEn?.let(out::add)
        }
        ActionMacros.ALL.forEach { m -> out.add(m.label); m.labelEn?.let(out::add) }
        LauncherActions.ALL.forEach { a -> out.add(a.label); a.labelEn?.let(out::add) }
        out.addAll(VoiceSynonyms.MEDIA_WORDS)
        out.addAll(VoiceSynonyms.NAV_WORDS)
        return out
    }
}
