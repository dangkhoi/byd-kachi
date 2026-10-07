package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.CtlSafetyPolicy
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.Strings

/**
 * ═══ THÂN CÂU XEM-TRƯỚC của nút xe + nhạc — phần của [VoiceReply.preview], tách khỏi `VoiceReply.kt` ════════════
 *
 * Tách ở spec `kachi-i18n-zh-th-ms.html` T2 vì trần 500 dòng của mọi `Voice*.kt` (`VoiceListenWiringContractTest`):
 * mọi hàm dựng câu NÓI nay nhận `lang` tường minh (giao diện ZH/TH/MS ⇒ giọng nói tiếng Việt, xem `voiceLangOf`), và
 * `VoiceReply.kt` (498 dòng) không còn chỗ cho các tham số ấy. Tách theo VAI *"gọi tên một việc"* (động từ + nhãn +
 * giá trị); API công khai vẫn nằm ở [VoiceReply] nên không chỗ gọi nào đổi một ký tự.
 *
 * `internal` và [lang] **không có mặc định**: chỉ [VoiceReply] gọi tới, và tầng này không bao giờ tự đọc
 * [Strings.current] — một mặc định ở đây là đường để câu nói lặng lẽ lấy tiếng giao diện (chữ Hán cho giọng Việt).
 *
 * VI/EN y từng byte như trước khi tách: [lang] = [Strings.current] cho ra đúng `displayLabel`/`displayArgs`/`Strings.t`
 * cũ (`labelIn`/`argsIn` là chính phép đọc mà hai thuộc tính kia uỷ quyền).
 */
internal object VoiceReplyPreview {

    /**
     * 2.93 — câu xem-trước của một việc launcher. Camera theo yêu cầu: câu MỞ là BẬT/TẮT theo thứ đang hiện (nói lại lần
     * hai thì tắt — `CameraDemand.spoken`), mà phiên `:wake` không biết camera nào đang hiện ⇒ chỉ nói TÊN camera, không
     * hứa *"Mở"* cho một lượt có thể là tắt; câu TẮT nói *"Tắt …"*; *Tắt camera* — nhãn đã là câu lệnh. Việc khác: *"Mở …"*.
     */
    fun launcher(i: VoiceIntent.Launcher, lang: Lang): String {
        val name = VoiceReply.labelOf(i.id, lang)
        return when {
            i.id == LauncherActions.CAM_OFF -> name
            i.off -> Strings.t("Tắt ", "Turn off ", lang) + mid(name)
            LauncherActions.switchable(i.id) -> name
            else -> Strings.t("Mở ", "Open ", lang) + mid(name)
        }
    }

    /**
     * 2.96 R12 — nhãn ĐỨNG SAU động từ: hạ chữ đầu (*"Đóng kính lái"*, không *"Đóng Kính lái"*). Chữ viết tắt giữ nguyên
     * ([VoiceFeedbackPhrase.decap] — *"EV / HEV"*); chữ Hán/Thái không có hoa/thường nên không đổi gì.
     */
    fun mid(label: String): String = VoiceFeedbackPhrase.decap(label)

    /**
     * Câu xem-trước của một nút xe — xem KDoc [VoiceReply.preview].
     *
     * 2.96 R12 (owner 07/10) — động từ theo NGHĨA của nút, không theo loại ô:
     *  • TOGGLE của bộ phận chạy bằng mô-tơ ([CtlSafetyPolicy.MOVES_SLOWLY] — kính, cửa sổ trời) đọc **Mở/Đóng**: *"Đóng
     *    kính lái"*, không *"Tắt Kính lái"*. Dữ liệu sẵn có của chính bộ đăng ký, không rẽ theo tên nút (CLAUDE.md §7).
     *  • SELECT có lựa chọn 0 là *"Tắt"* (sưởi/mát ghế) đọc thành câu bật/tắt: *"Tắt sưởi ghế phụ"* ·
     *    *"Bật sưởi ghế phụ mức 1"*, không *"Sưởi ghế phụ: Tắt"*.
     *  • STEP bỏ dấu *"="*; thang ngắn (≤ 10 nấc, vd gió 0–7) đọc *"mức N"*.
     *  • BUTTON có nhãn đã là câu lệnh (*"Đóng tất cả kính"*) không chồng thêm *"Bấm"*.
     */
    fun control(i: VoiceIntent.Control, lang: Lang): String {
        val def = ControlRegistry.byId(i.id) ?: return VoiceReply.labelOf(i.id, lang)
        val label = def.labelIn(lang)
        val name = mid(label)
        if (i.relative != 0) {
            val dir = if (i.relative > 0) Strings.t("Tăng ", "Increase ", lang) else Strings.t("Giảm ", "Decrease ", lang)
            val n = kotlin.math.abs(i.relative)
            return dir + name + " " + Strings.fIn(lang, "{0} nấc", "by {0}", n)
        }
        val args = def.argsIn(lang)
        val openClose = CtlSafetyPolicy.movesSlowly(def.id)
        return when (def.kind) {
            ControlKind.TOGGLE -> when {
                i.value == 1 && openClose -> Strings.t("Mở ", "Open ", lang) + name
                i.value == 1 -> Strings.t("Bật ", "Turn on ", lang) + name
                openClose -> Strings.t("Đóng ", "Close ", lang) + name
                else -> Strings.t("Tắt ", "Turn off ", lang) + name
            }
            ControlKind.COVER -> when {
                i.value == 1 -> Strings.t("Mở ", "Open ", lang) + name
                // T7: mức ≥2 (Nửa…) đọc nhãn từ registry — giọng nói hiện không phát mức này, nhưng câu xem-trước
                // (CapTest/dock) phải nói đúng thứ sẽ gửi, không nói "Mở" cho một lệnh "Nửa".
                (i.value ?: 0) >= 2 && args.getOrNull(i.value!!) != null ->
                    Strings.t("Mở ", "Open ", lang) + mid(args[i.value!!]) + " " + name
                else -> Strings.t("Đóng ", "Close ", lang) + name
            }
            ControlKind.BUTTON -> if (VoiceReplyDone.verbLed(def.label)) label else Strings.t("Bấm ", "Press ", lang) + name
            ControlKind.SELECT -> select(def.args.firstOrNull() == OFF_ARG, i.value, args, label, name, lang)
            ControlKind.STEP -> {
                val v: Any = i.value ?: "?"
                if (def.max - def.min in 1..LEVEL_SCALE_MAX) Strings.fIn(lang, "Đặt {0} mức {1}", "Set {0} to level {1}", name, v)
                else Strings.fIn(lang, "Đặt {0} {1}", "Set {0} to {1}", name, v)
            }
        }
    }

    /** SELECT — [hasOff] = lựa chọn 0 của nút là *"Tắt"* (dữ liệu tiếng Việt gốc, không phụ thuộc [lang]). */
    @Suppress("LongParameterList")
    private fun select(hasOff: Boolean, value: Int?, args: List<String>, label: String, name: String, lang: Lang): String {
        val arg = value?.let { args.getOrNull(it) } ?: return label
        return when {
            hasOff && value == 0 -> Strings.t("Tắt ", "Turn off ", lang) + name
            hasOff -> Strings.t("Bật ", "Turn on ", lang) + name + " " + mid(arg)
            else -> "$label: $arg"
        }
    }

    /** Chữ của lựa chọn TẮT trong `ControlDef.args` (tiếng Việt gốc). */
    private const val OFF_ARG = "Tắt"

    /** Thang STEP có ≤ chừng này nấc thì đọc *"mức N"* (gió 0–7); dài hơn (nhiệt độ 17–33) đọc trơn con số. */
    private const val LEVEL_SCALE_MAX = 10

    /** Câu xem-trước của một lệnh nhạc. */
    fun media(i: VoiceIntent.Media, lang: Lang): String = when (i.op) {
        VoiceMediaOp.PLAY -> Strings.t("Phát nhạc", "Play", lang) + by(i.app, lang)
        VoiceMediaOp.PAUSE -> Strings.t("Dừng nhạc", "Pause", lang)
        // ⚠ [SOÁT chuỗi-lời-đáp 2026-09-17] Hai dòng này PHẢI là **cụm động từ**, không phải cụm danh từ.
        // `VoiceFeedbackPhrase.merge` dựng câu đọc bằng cách ghép *"Đã "*/*"Chưa "* + thân dòng — cụm danh từ
        // *"Bài tiếp theo"* vì thế ra *"Chưa bài tiếp theo, chưa có phiên nhạc nào"*, một câu không phải tiếng
        // Việt. Mọi vai khác (PLAY · PAUSE · QUERY) vốn đã là động từ; hai vai này là ngoại lệ duy nhất, và sửa
        // **tại nguồn** đúng hơn là dạy tầng đọc nhận diện cụm danh từ (CLAUDE.md §7).
        VoiceMediaOp.NEXT -> Strings.t("Chuyển bài tiếp theo", "Skip to the next track", lang)
        VoiceMediaOp.PREV -> Strings.t("Quay lại bài trước", "Go back to the previous track", lang)
        // V1.1 — đọc lại tên bài trong ngoặc kép nhọn. Phần này do nhận dạng **tự do** đọc ra (R16), tức chỗ dễ
        // sai nhất trong cả câu; để nó lẫn vào câu trơn thì người nghe không biết máy đang hỏi về đoạn nào.
        VoiceMediaOp.QUERY -> Strings.t("Tìm bài ", "Search ", lang) + "«" + i.query + "»" + by(i.app, lang)
    }

    /** Đuôi *"bằng &lt;app&gt;"* — rỗng khi câu không nêu app. */
    fun by(appKey: String?, lang: Lang): String =
        appKey?.let { Strings.t(" trên ", " on ", lang) + VoiceAppTargets.labelOf(it) } ?: ""

    /** Đuôi *"vào ô N"* — rỗng khi câu không nêu ô. Số giữ **đúng như người ta nói** (1-based). */
    fun inSlot(slot: Int?, lang: Lang): String =
        slot?.let { Strings.fIn(lang, " vào ô {0}", " in slot {0}", it) } ?: ""
}
