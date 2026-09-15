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
     *
     * @param places nhãn trong **sổ địa chỉ** của hồ sơ đang dùng (spec `kachi-voice-addresses.html` R6).
     *   ⚠ Đây là NGOẠI LỆ có lý do của luật *"chỉ tập tĩnh"* ở KDoc lớp: luật đó loại tên hồ sơ/app vì chúng là
     *   danh từ riêng / tiếng Anh mà mô hình VN **không phát ra được token**. Nhãn địa chỉ thì ngược lại — đó là
     *   tiếng Việt đời thường (*"Nhà"*, *"Công ty"*, *"Nhà ngoại"*), đúng thứ biasing kéo về được. Nhãn nào có
     *   chữ số/ký tự lạ vẫn bị [SherpaHotwords.normalize] loại, nên không cần lọc thêm ở đây.
     */
    fun hotwordsFile(places: List<String> = emptyList()): String =
        SherpaHotwords.fileContent(accentedControlPhrases() + VoicePlaces.spokenPhrases(places))

    /**
     * Mọi cụm **có dấu** đáng bias — nhãn nút/datum/macro/hành động launcher, **cộng** động từ và cách nói đời
     * thường ([SherpaSpokenWords]).
     *
     * ## Vì sao phải có [SherpaSpokenWords], không đổ thẳng [VoiceSynonyms] vào đây
     * [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L3: bản trước đổ thẳng
     * `VoiceSynonyms.MEDIA_WORDS` + `NAV_WORDS` — mà hai bảng đó khai **không dấu** (`"nhac"`, `"duong den"`).
     * Mô hình VN xuất chữ HOA **có dấu**, nên những dòng ấy không mã hoá được bằng bảng BPE và bị native bỏ:
     * chúng nằm trong tệp hotwords mà **chưa bao giờ** kéo được câu nào. Tệp [SherpaSpokenWords] giữ đúng những
     * cụm đó ở dạng có dấu, và có bài canh ép nó không lệch khỏi [VoiceSynonyms]/[VoiceGrammar.VERBS].
     *
     * Ba nhóm được bias, theo đúng thứ tự này (ổn định để `diff` hai lượt đo):
     *  1. nhãn của 4 bộ đăng ký (VI + EN + bản ngắn + lựa chọn của `SELECT`);
     *  2. động từ có dấu — [ĐO] w09 `dừng nhạc` → *"rừng nhạc"*: danh từ đúng, **động từ** sai;
     *  3. cách nói đời thường có dấu (*"pin"* · *"kính"* · *"máy lạnh"* · *"cốp xe"*…).
     */
    fun accentedControlPhrases(): List<String> {
        val out = ArrayList<String>(1024)
        ControlRegistry.ALL.forEach { c ->
            out.add(c.label); c.labelEn?.let(out::add); c.short?.let(out::add); c.shortEn?.let(out::add)
            out.addAll(c.args); out.addAll(c.argsEn)
        }
        TelemetryRegistry.ALL.forEach { t ->
            out.add(t.label); t.labelEn?.let(out::add); t.short?.let(out::add); t.shortEn?.let(out::add)
        }
        ActionMacros.ALL.forEach { m -> out.add(m.label); m.labelEn?.let(out::add) }
        LauncherActions.ALL.forEach { a -> out.add(a.label); a.labelEn?.let(out::add) }
        out.addAll(SherpaSpokenWords.ALL)
        return out
    }
}
