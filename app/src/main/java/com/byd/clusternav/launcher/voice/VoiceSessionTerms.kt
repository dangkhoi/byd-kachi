package com.byd.clusternav.launcher.voice

/**
 * ═══ TỪ VỰNG **CỦA PHIÊN** — một chỗ dựng, cho mọi thứ trong phiên hỏi tới ═══════════════════════════════════
 *
 * ## Vì sao một hàm riêng, và vì sao nó không được là mặc định
 * [VoiceClarify.ask] nhận `terms` với **mặc định** `VoiceGrammar.terms()` — tức **chỉ tập TĨNH**: 4 bộ đăng ký +
 * [VoiceSynonyms], **không** có tên hồ sơ, **không** có nhãn app của máy này. Mặc định ấy đúng cho bài kiểm
 * (`:core` không có `PackageManager`), nhưng ở phiên thật nó là một chỗ hỏng **im lặng**: câu hỏi lại nào cần
 * biết máy có những hồ sơ nào sẽ **không bao giờ nổ**, mà không một dòng log nào nói vì sao.
 *
 * [ĐO xe 2026-09-26] đúng ca ấy xảy ra: 8/8 lượt *"chuyển sang hồ sơ Test"* nghe thành *"chuyển sang hồ sơ"*
 * (rụng tên) ⇒ [VoiceProfileNames.missingName] mới sinh ra để hỏi *"Hồ sơ nào — A hay B?"*, và nếu chỗ gọi vẫn
 * dùng mặc định thì nó **compile xanh, test `:core` xanh, xe vẫn im** — đúng bẫy CLAUDE.md §8.
 *
 * ⇒ Mọi chỗ trong phiên cần từ vựng thì gọi [sessionTerms]. Tệp riêng vì `VoiceSession.kt` và
 * `VoiceSessionTurns.kt` đều đã **499/500 dòng** (CLAUDE.md §4.1 · `VoiceListenWiringContractTest`).
 *
 * ⚠ Dựng **mỗi lần gọi**, không cache: hồ sơ và danh sách app đổi được giữa hai lượt nói (đó chính là lý do
 * `VoiceSession.profiles`/`appsByLabel` là lambda chứ không phải danh sách chụp sẵn). Phần tĩnh bên trong
 * [VoiceGrammar.terms] đã `by lazy` nên giá mỗi lượt chỉ là vài cụm động.
 */
internal fun VoiceSession.sessionTerms(): List<VoiceTerm> =
    VoiceGrammar.terms(profiles(), appsByLabel().keys.toList())
