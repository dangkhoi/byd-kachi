package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings

/**
 * FIX286 · SR4 — câu khi xe **tự báo** không có bộ phận của nút (cổng có-mặt `ControlDef.presence`, vd
 * `getMoonRoofConfig` = 0/2 với cửa sổ trời).
 *
 * Khác [VoiceReply.notOnThisCar] (*"chưa điều khiển được trên xe này"* — Kachi chưa nối được nút đó): đây là một
 * sự thật CỦA XE, nên người lái biết là khỏi thử lại. Một chữ chung cho mọi nút khai cổng (CLAUDE.md §7) — tên bộ
 * phận đã nằm ở vế đầu của câu ([VoiceReply.failed] dựng *"✗ Bật Cửa sổ trời — …"*).
 *
 * Nằm ở tệp riêng (hàm mở rộng của chính [VoiceReply], gọi y như một thành viên) vì `VoiceReply.kt` đã chạm trần
 * 500 dòng (CLAUDE.md §4.1 — [ĐO `wc -l` 02/10] 498). [lang] = ngôn ngữ GIỌNG NÓI, cùng luật mọi câu của [VoiceReply].
 */
fun VoiceReply.partNotOnThisCar(i: VoiceIntent, lang: Lang = Strings.current): String = failed(
    i,
    Strings.t("xe này không có bộ phận này (xe tự báo)", "this car reports it doesn't have this part", lang),
    lang,
)
