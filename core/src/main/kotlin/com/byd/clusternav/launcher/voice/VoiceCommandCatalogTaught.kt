package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings

/**
 * 2.91 VOICE-APP-NAMES (spec §4.6 · C4) — nhóm *"Tên app đã dạy"* của danh sách *"Câu lệnh nói được"*: câu mẫu
 * *"mở &lt;tên đã dạy&gt;"* ⇒ `OpenApp(nhãn thật)`. Tệp riêng vì `VoiceCommandCatalog.kt` sát trần 500 dòng. Câu nói
 * dùng ĐÚNG dạng có dấu mô hình in ra (đó là thứ người dùng sẽ nói), cột *"Kachi làm gì"* đọc nhãn thật.
 */
internal object VoiceCommandCatalogTaught {

    const val ID = "taught"

    fun title(lang: Lang): String = Strings.t("Tên app đã dạy", "Taught app names", lang)

    fun group(
        aliases: List<VoiceAppAlias>,
        lang: Lang,
        ex: (Pair<String, VoiceIntent>) -> VoiceCommandExample,
    ): VoiceCommandGroup? {
        if (aliases.isEmpty()) return null
        val verb = SherpaSpokenWords.VERBS[VoiceVerb.OPEN].orEmpty().firstOrNull() ?: return null
        val examples = aliases.distinctBy { it.pkg to it.accented }
            .map { a -> ex("$verb ${a.accented}" to VoiceIntent.OpenApp(a.labelKey)) }
        return VoiceCommandGroup(ID, title(lang), null, examples)
    }
}
