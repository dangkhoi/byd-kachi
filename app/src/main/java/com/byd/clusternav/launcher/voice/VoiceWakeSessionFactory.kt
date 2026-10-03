package com.byd.clusternav.launcher.voice

import android.content.Intent

/**
 * Dựng [VoiceSession] service-an-toàn (R7) cho `:wake` — tách khỏi `VoiceWakeService.kt` (trần 500 dòng, CLAUDE.md
 * §4.1) theo VAI *"bộ dây của phiên `:wake`"*; service chỉ còn vòng đời.
 *
 * Lambda dùng `applicationContext`:
 *  • điều khiển xe / đọc / nav-generic / nhạc — chạy thẳng, KHÔNG cần Activity.
 *  • mở app đích — `startActivity(NEW_TASK)` (launch app kia, không phải Kachi).
 *  • mở Cài đặt / ngăn kéo / quyền / đổi hồ sơ — đưa Kachi lên kèm **đúng việc** qua `EXTRA_VOICE_HOME_ACTION`
 *    ([VoiceHomeAction], fire-and-forget: [VoiceWakeHomeRelay.send]). Trước CLOSE-3 chỗ này gửi `EXTRA_START_VOICE` —
 *    tức "mở Kachi rồi mở một phiên nghe MỚI", không phải việc vừa nói; và từ khi EXTRA ấy cũng đi route `:wake` thì
 *    thành vòng lặp. KHÔNG dùng lại `EXTRA_START_VOICE` ở đây (bài canh `VoiceEntryRouteWiringContractTest`).
 *  • **gắn app vào ô · đổi bố cục** (2.69, VOICE-WAKE-SLOT-LAYOUT) — cần kết quả thật ⇒ [VoiceWakeHomeRelay]
 *    chờ Activity ack ≤ `VoiceHomeRelay.ACK_MS`; hết hạn = từ chối thật, không lạc quan. Trước 2.69 hai lambda này ở
 *    mặc định `false` của `VoiceWiring.dispatcher` ⇒ wake ON thì nút mic mất *"mở YouTube vào ô 2"*. Từ
 *    VOICE-WAKE-SLOTCOUNT gắn ô đi `placeInSlot` → [VoiceWakeHomeRelay.performSlot]: số ô do Activity đo (state thật).
 *  • `onListen` (*"Kachi nghe"* giữa phiên) — cùng nghĩa in-process (`KachiHomeWiring.voiceSession`: `session.start()`
 *    no-op khi đang chạy): xin lại phiên đang giữ của tiến trình ([VoiceWakeSessions.acquire]) rồi `start()`.
 *
 * §8.2 (A) — hồ sơ + sổ địa chỉ đọc từ TỆP ảnh chụp (tiến trình chính ghi ở mỗi đường ghi), đọc lại MỖI lần gọi —
 * không cache trong `:wake` (cache theo tiến trình chính là cái bệnh của SharedPreferences ở đây). FIX286 · VK4: cùng
 * tệp mang thêm tập hỏi xác nhận + app dẫn đường/nhạc mặc định (`fresh`); VK6: mốc phiên cho nhật ký bền (`marks`).
 *
 * spec `kachi-i18n-zh-th-ms.html` R6 — tiếng GIỌNG NÓI cũng đọc từ ảnh chụp ấy (`voiceLang` = `voiceLangOf(uiLang)`,
 * thiếu ⇒ VI), và CÙNG một lambda đi vào cả phiên (gộp câu đọc · hỏi lại · máy đọc) lẫn dispatcher (câu trả lời ·
 * geocoder). Trước bản này `:wake` không có ngôn ngữ: người dùng English bấm phím vô-lăng nghe trả lời tiếng Việt.
 */
internal fun VoiceWakeService.buildSession(): VoiceSession {
    val app = applicationContext
    val relay = VoiceWakeHomeRelay(app)
    val openHome = { action: VoiceHomeAction, arg: String? -> relay.send(action, arg) }
    val grammar = { VoiceGrammarSnapshotStore.read(app) }
    val voiceLang = { grammar().voiceLang }
    return VoiceSession(
        ctx = app,
        profiles = { grammar().profiles },
        appsByLabel = { VoiceWiring.appsByLabel(app) },
        places = { grammar().placeLabels() },
        dispatcher = { say, confirm ->
            VoiceWiring.dispatcher(
                ctx = app,
                state = { grammar().homeState() },
                appsByLabel = { VoiceWiring.appsByLabel(app) },
                openApp = { pkg ->
                    runCatching {
                        val i = packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (i != null) { startActivity(i); true } else false
                    }.getOrDefault(false)
                },
                openAppList = { openHome(VoiceHomeAction.APP_LIST, null) },
                openSettings = { openHome(VoiceHomeAction.SETTINGS, null) },
                // Trước 2.68 là `{ }` — *"đổi hồ sơ X"* qua wake im lặng không làm gì. Nay parse được (hồ sơ từ ảnh
                // chụp) và trả về Activity: `startVoiceIfRequested` → `VoiceHomeActions.switchProfile` → ViewModel.
                onSwitchProfile = { name -> openHome(VoiceHomeAction.SWITCH_PROFILE, name) },
                onListen = { VoiceWakeSessions.acquire { buildSession() }.start() },
                confirm = confirm,
                say = say,
                // Hai đường có KẾT QUẢ: chờ Activity thật, ô 0-based đúng như `KachiHomeSlots.assignApp` nhận.
                // VOICE-WAKE-SLOTCOUNT — gắn ô KHÔNG đi `assignAppToSlot` (đường ấy kiểm dải bằng `state()`, mà `state()` ở
                // đây là bố cục mặc định 3 ô): giao NGUYÊN lệnh, Activity kiểm bằng bố cục thật và trả số ô thật.
                placeInSlot = { idx, pkg -> relay.performSlot(idx, pkg) },
                onLayout = { preset -> relay.perform(VoiceHomeAction.SET_LAYOUT, VoiceHomeRelay.encodeLayout(preset)) },
                // VOICE-WAKE-SLOTCOUNT (dữ liệu xe) — `:wake` không có màn, không vòng poll: `state().carStatus` ở đây là
                // `CarStatus()` rỗng của `homeState()` ⇒ câu hỏi số liệu + cổng tốc độ cốp/ca-pô phải đọc TƯƠI.
                screenless = true,
                fresh = { VoiceWakeHold.prefs(app) },
                lang = voiceLang(),
            )
        },
        openPermissions = { openHome(VoiceHomeAction.PERMISSIONS, null) },
        marks = WakeSessionLog.Marks(app),
        voiceLang = voiceLang,
    )
}
