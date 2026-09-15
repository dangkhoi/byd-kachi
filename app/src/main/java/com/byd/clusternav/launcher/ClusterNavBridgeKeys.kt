package com.byd.clusternav.launcher

import com.byd.clusternav.NavConnect
import com.byd.clusternav.Prefs
import com.byd.clusternav.modules.voicekey.AssistantLauncher
import com.byd.clusternav.modules.voicekey.VoiceKeyLearnBus
import com.byd.clusternav.voicekey.VoiceKeyBinding

/**
 * ═══ Nửa "Phím vô-lăng → app/trợ lý" của [ClusterNavBridge] ═════════════════════════════════════
 *
 * Hàm mở rộng của [ClusterNavBridge] (lý do tách tệp + vì sao không tách lớp: xem đầu
 *
 * ⚠ **2026-09-13 — màn cũ đã GỠ HẲN** (`docs/specs/kachi-remove-legacy-screen.html` R1/R3). Mọi chỉ dẫn
 * `MainActivity.kt:<dòng>` dưới đây là **vết lịch sử**, không phải một tệp còn đọc được: chúng trỏ vào bản trước
 * commit gỡ màn (tra bằng `git log -- app/src/main/java/com/byd/clusternav/MainActivity.kt`). Giữ số dòng vì đó là
 * cách duy nhất còn lại để so hành vi của cầu với bản gốc; cầu nay là **nguồn duy nhất** của những hành vi đó.
 * `ClusterNavBridgeCast.kt`). Lặp lại `MainActivity.kt:707–975` — từng hàm ghi dòng gốc.
 */

// ── Công tắc + trạng thái dịch vụ Hỗ trợ ────────────────────────────────────────────────────────

/** `MainActivity.kt:834`. */
fun ClusterNavBridge.voiceKeyEnabled(): Boolean = Prefs.voiceKeyEnabled(app)

/**
 * Bật/tắt "Nút vật lý → app/trợ lý" — lặp lại `MainActivity.kt:835–858`.
 *
 * Bật ⇒ toast + [NavConnect.grantAccessibility] với **`reset = true`**: toggle OFF→ON là nơi owner
 * yêu cầu xoá single-flight đang kẹt rồi cấp lại + force-rebind, để phím-thoại tự lành sau reboot mà
 * không phải khởi động lại app. KHÔNG gate bằng [ClusterNavBridge.accessibilityBoosterGranted]: sau
 * reboot service vẫn ENABLED-trong-setting nhưng CHƯA bound, gate kiểu đó sẽ bỏ qua đúng ca cần chữa.
 * Tắt ⇒ chỉ persist (không đụng dadb).
 */
fun ClusterNavBridge.setVoiceKeyEnabled(on: Boolean, onDone: (Boolean) -> Unit = {}) {
    Prefs.setVoiceKeyEnabled(app, on)
    if (!on) {
        ui(Runnable { onDone(true) })
        return
    }
    toast(BridgeMsg.ENABLING_ACCESSIBILITY)
    NavConnect.grantAccessibility(app, reset = true) { ok ->
        ui(
            Runnable {
                toast(if (ok) BridgeMsg.ACCESSIBILITY_ENABLED else BridgeMsg.ACCESSIBILITY_FAILED)
                onDone(ok)
            },
        )
    }
}

/**
 * Nút "Kiểm tra / Sửa ngay" — lặp lại `MainActivity.kt:869–885`: CÙNG đường heal của toggle OFF→ON
 * (`grantAccessibility(reset = true)`), KHÔNG đổi logic cấp quyền.
 *
 * ⚠ Màn cũ còn đọc lại trạng thái TRỄ +2 s/+5 s (`scheduleVoiceKeyStatusRecheck`,
 * `MainActivity.kt:948–952`) vì `onServiceConnected` chạy bất đồng bộ vài giây sau force-rebind.
 * Bridge KHÔNG tự hẹn giờ (không giữ vòng đời màn hình) — tầng UI gọi lại [voiceKeyStatus] ở
 * `onResume`/nhịp làm tươi của nó; [onDone] nổ ngay khi dadb trả kết quả.
 */
fun ClusterNavBridge.checkFix(onDone: (Boolean) -> Unit = {}) {
    toast(BridgeMsg.CHECKING)
    reapplyGeminiAssistant()
    NavConnect.grantAccessibility(app, reset = true) { ok ->
        ui(
            Runnable {
                toast(if (ok) BridgeMsg.VOICE_KEY_READY else BridgeMsg.ACCESSIBILITY_FAILED)
                onDone(ok)
            },
        )
    }
}

/**
 * Ba trạng thái của dòng chữ phím-thoại — lặp lại `MainActivity.kt:928–945`, nhưng trả **mã**
 * [VoiceKeyStatus] (nguyên văn VI/EN + màu ghi ở KDoc của enum đó) chứ không trả câu.
 *
 * ⚠ [SOÁT SENIOR 2026-09-13] Cầu **đọc hai sự thật** rồi giao LUẬT ba-ca cho hàm thuần
 * [ClusterNavSettingsModel.voiceKeyStatus] ở `:core`, không tự viết lại `when`. Bản trước chép lại nguyên cái
 * `when` đó ⇒ có **hai bản của một luật** (đúng thứ KDoc `ClusterNavSettingsModel` nói là nó sinh ra để chặn),
 * và bản ở `:core` thành mã chết — nên thứ tự "tắt thì không hỏi tiếp" chỉ còn được test ở bản KHÔNG chạy.
 * Cầu vẫn là chỗ duy nhất biết `Prefs`/`NavAccessibilitySource`; `:core` vẫn là chỗ duy nhất giữ luật.
 */
fun ClusterNavBridge.voiceKeyStatus(): VoiceKeyStatus =
    when (ClusterNavSettingsModel.voiceKeyStatus(Prefs.voiceKeyEnabled(app), accessibilityBound())) {
        ClusterNavSettingsModel.VkStatus.OFF -> VoiceKeyStatus.OFF
        ClusterNavSettingsModel.VkStatus.ACTIVE -> VoiceKeyStatus.ACTIVE
        ClusterNavSettingsModel.VkStatus.DISCONNECTED -> VoiceKeyStatus.DISCONNECTED
    }

// ── Danh sách gán (nguồn sự thật mà NavAccessibilityService.onKeyEvent nghe theo) ────────────────

/** `MainActivity.kt:782` (`rebuildVoiceKeyBindingList`). */
fun ClusterNavBridge.bindings(): List<VoiceKeyBinding> = Prefs.voiceKeyBindings(app)

/**
 * "Thêm gán" — lặp lại `MainActivity.kt:938–975` (NƠI DUY NHẤT ghi cấu hình gán):
 *  1. [Prefs.addVoiceKeyBinding] trả về spec CŨ của cùng mã phím (null = mới, = spec mới ⇒ đã có sẵn,
 *     khác ⇒ vừa GHI ĐÈ) — bridge trả nguyên giá trị đó để tầng UI nói đúng việc đã xảy ra.
 *  2. Chỉ khi cấu hình **thật sự đổi** (`replaced != targetSpec`) và đích là Gemini
 *     ([AssistantLauncher.isGeminiVoiceSpec]) mới chạy công thức đặt trợ lý hệ thống = Google/Gemini
 *     trên thread nền — để `keyevent 231` route tới TRỢ LÝ chứ không mở app. Bấm lại đúng cặp đang có
 *     thì KHÔNG bung thread + phiên dadb + toast (đúng tác dụng phụ mà F3 đã dời khỏi listener).
 */
fun ClusterNavBridge.addBinding(keyCode: Int, targetSpec: String): String? {
    val replaced = Prefs.addVoiceKeyBinding(app, keyCode, targetSpec)
    if (replaced != targetSpec && AssistantLauncher.isGeminiVoiceSpec(targetSpec)) {
        toast(BridgeMsg.SETTING_GEMINI_ASSISTANT)
        Thread({
            val err = runCatching { AssistantLauncher.setSystemAssistant(app) }.getOrElse { "" }
            // Màn cũ hiện NGUYÊN chuỗi lỗi của AssistantLauncher (MainActivity.kt:972). Cầu không mang
            // chữ qua, nên chuỗi gốc đi vào Log.w để còn grep được trên xe, còn người dùng nhận một mã.
            if (err.isNotEmpty()) android.util.Log.w("ClusterNavBridge", "setSystemAssistant: " + err)
            ui(
                Runnable {
                    toast(if (err.isEmpty()) BridgeMsg.GEMINI_ASSISTANT_SET else BridgeMsg.GEMINI_ASSISTANT_FAILED)
                },
            )
        }, "bridge-set-assistant").start()
    }
    return replaced
}

/** Nút "Xoá" của một dòng gán — lặp lại `MainActivity.kt:795–799`. */
fun ClusterNavBridge.removeBinding(keyCode: Int) {
    Prefs.removeVoiceKeyBinding(app, keyCode)
    toast(BridgeMsg.BINDING_REMOVED)
}

// ── Dropdown NÚT: preset + nút tự học ───────────────────────────────────────────────────────────

/**
 * Preset MÃ PHÍM ứng viên — chép NGUYÊN **thứ tự và mã** của `MainActivity.kt:707–717`
 * (`voiceKeyPresets`), nhưng chỉ mang con số: tên của chúng là chữ cho người đọc nên thuộc tài nguyên
 * của tầng Settings, không thuộc cầu.
 *
 * Bảng gốc (để T4 chép nhãn, VI · EN):
 *  - `328` "Nút mic vô-lăng — giữ (328)" · "Steering-wheel mic — hold (328)" — nút mic vô-lăng xe này
 *    NHẤN-GIỮ ([ĐO] on-car 2026-08-13) nên đứng đầu; nhấn ngắn phát mã KHÁC ⇒ trợ lý gốc giữ nguyên;
 *  - `231` "Trợ lý giọng nói (VOICE_ASSIST · 231)" · "Voice assistant (VOICE_ASSIST · 231)";
 *  - `219` "Trợ lý (ASSIST · 219)" · "Assistant (ASSIST · 219)";
 *  - `85` "Play/Pause (85)" (không dịch);
 *  - `88` "Bài trước (PREVIOUS · 88)" · "Previous track (PREVIOUS · 88)";
 *  - `87` "Bài sau (NEXT · 87)" · "Next track (NEXT · 87)";
 *  - `79` "Headset hook (79)" (không dịch);
 *  - `5` "Gọi (CALL · 5)" · "Call (CALL · 5)";
 *  - `84` "Tìm kiếm (SEARCH · 84)" · "Search (SEARCH · 84)".
 */
fun ClusterNavBridge.buttonPresetCodes(): List<Int> = listOf(328, 231, 219, 85, 88, 87, 79, 5, 84)

/**
 * Danh sách NÚT đầy đủ = preset + nút tự học — lặp lại `MainActivity.kt:719` (`voiceKeyButtonList`),
 * đúng thứ tự đó. Mục preset có [ButtonOption.customName] = `null` (tầng Settings tra tên theo mã);
 * mục tự học mang tên **người dùng tự đặt**, không phải chữ của dự án.
 */
fun ClusterNavBridge.buttonOptions(): List<ButtonOption> =
    buttonPresetCodes().map { ButtonOption(it) } +
        Prefs.voiceKeyCustomButtons(app).map { ButtonOption(it.second, it.first) }

/** Chỉ các nút TỰ HỌC (preset không xoá được) — `MainActivity.kt:900`; cặp (tên người dùng đặt, mã). */
fun ClusterNavBridge.customButtons(): List<Pair<String, Int>> = Prefs.voiceKeyCustomButtons(app)

/**
 * "Học phím mới" — lặp lại `MainActivity.kt:909–914`: bật cờ `voicekey_learn`, cấp Hỗ trợ nếu còn
 * thiếu, rồi nhắc người dùng bấm nút vật lý.
 *
 * **Cơ chế nhận mã đã học ([ĐO] `VoiceKeyLearnBus.kt`)**: `NavAccessibilityService` bắt `KeyEvent` và
 * gọi `VoiceKeyLearnBus.publish(code)`; bus là singleton TRONG TIẾN TRÌNH giữ **đúng MỘT** listener và
 * nhớ mã `pending` nếu chưa ai đăng ký. Nên đây là callback thật, KHÔNG phải poll pref —
 * `Prefs.voiceKeyLearn` chỉ là cờ "đang ở chế độ học", không mang mã. Bus đã post sẵn về luồng chính.
 *
 * ⚠ Một listener duy nhất ⇒ khi Kachi Settings đang mở thì màn cũ (nếu cũng mở) sẽ mất dialog đặt tên
 * — đúng hành vi đã có giữa các Activity của màn cũ. [stopLearn] gỡ listener để trả bus lại.
 */
fun ClusterNavBridge.startLearn(onLearned: (Int) -> Unit) {
    Prefs.setVoiceKeyLearn(app, true)
    if (!accessibilityBoosterGranted()) NavConnect.grantAccessibility(app)
    VoiceKeyLearnBus.setListener { code -> ui(Runnable { onLearned(code) }) }
    toast(BridgeMsg.LEARN_PRESS_BUTTON)
}

/**
 * Kết thúc phiên học — gỡ listener (chống rò như `MainActivity.onDestroy`, dòng 379) và hạ cờ học.
 * Màn cũ KHÔNG hạ cờ vì nó đặt lại mỗi lần bấm nút; bridge hạ để phiên Settings đóng lại là hết học.
 */
fun ClusterNavBridge.stopLearn() {
    VoiceKeyLearnBus.setListener(null)
    Prefs.setVoiceKeyLearn(app, false)
}

/**
 * Lưu nút vừa học.
 *
 * ⚠ Khác màn cũ đúng MỘT điểm, có chủ ý: `MainActivity.kt:820` tự ghép nhãn
 * **`"<tên> (mã <code>)"`** rồi mới lưu. Cụm "mã" là chữ tiếng Việt, mà cầu không được mang chữ, nên
 * **tầng Settings dựng nhãn đó từ tài nguyên** rồi truyền vào [displayName]; bridge lưu NGUYÊN chuỗi
 * nhận được — y như màn cũ lưu nguyên chuỗi nó vừa dựng. Giữ đúng khuôn `<tên> (mã <code>)` ở bản VI
 * thì hai màn vẫn hiện cùng một nhãn cho cùng một nút.
 *
 * Học phím CHƯA gán gì — người dùng còn phải chọn app rồi [addBinding] (`MainActivity.kt:817–819`).
 */
fun ClusterNavBridge.addCustomButton(displayName: String, code: Int) {
    Prefs.addVoiceKeyCustomButton(app, displayName, code)
    toast(BridgeMsg.BUTTON_SAVED)
}

/**
 * Xoá một nút tự học — lặp lại `MainActivity.kt:900–906`.
 *
 * ⚠ [ĐO 2026-08-24] Ở màn cũ khối này là **CODE CHẾT**: nó treo vào `Spinner.onItemLongClickListener`
 * mà AOSP `android-10.0.0_r47` không bao giờ phát (`Spinner` không kế thừa `AbsListView`) ⇒ owner
 * chưa từng có đường xoá nút tự học (backlog F4). Bridge phơi hàm ra để Settings mới nối được nút
 * "Xoá" thật — đây là **sửa một lỗi đã biết**, không phải đổi hành vi đang chạy.
 */
fun ClusterNavBridge.removeCustomButton(code: Int) {
    Prefs.removeVoiceKeyCustomButton(app, code)
    toast(BridgeMsg.BUTTON_REMOVED)
}

// ── Dropdown ĐÍCH: 3 mục đặc biệt + mọi app có launcher ─────────────────────────────────────────

/**
 * Lặp lại `MainActivity.kt:726–734` (`voiceKeyTargetSpecs`): ba mục đặc biệt ghim đầu rồi toàn bộ app
 * có launcher ([InstalledApps.launchable]). Một bảng duy nhất cho cả danh sách chọn lẫn nhãn từng
 * dòng đã gán — hai nơi đọc khác bảng thì dòng đã gán hiện tên khác lúc chọn.
 *
 * Ba sentinel trả [TargetOption.appLabel] = `null`; tầng Settings tra tên theo [TargetOption.spec]
 * (nguyên văn VI · EN của màn cũ):
 *  - `Prefs.VK_TARGET_ASSIST` "Trợ lý mặc định hệ thống" · "System default assistant";
 *  - `Prefs.VK_TARGET_GEMINI_KEY` "Trợ lý qua phím cứng (Gemini · 231)" · "System assistant via hard
 *    key (Gemini · 231)";
 *  - `Prefs.VK_TARGET_RECOGNIZER` "Nhận dạng giọng nói" · "Speech recognizer";
 *  - `Prefs.VK_TARGET_KACHI_VOICE` "Kachi nghe" · "Kachi listens" (V1 pha NGHE — phiên nghe tại máy).
 *
 * App thường mang `appLabel` = tên do **hệ thống** dịch (`PackageManager`), không phải chữ của dự án.
 */
fun ClusterNavBridge.targetOptions(): List<TargetOption> = listOf(
    TargetOption(Prefs.VK_TARGET_ASSIST),
    TargetOption(Prefs.VK_TARGET_GEMINI_KEY),
    TargetOption(Prefs.VK_TARGET_RECOGNIZER),
    // V1 pha NGHE — đích THỨ TƯ: phiên nghe của chính Kachi. Đặt **cuối** khối sentinel có chủ ý: thứ tự khai
    // là thứ `SettingsSectionsKeys.sentinelLabel` tra nhãn theo (xem KDoc hàm đó), nên chèn vào giữa sẽ đổi
    // nhãn của ba dòng đang chạy. Thêm vào cuối thì ba dòng cũ giữ nguyên chỉ số — và giữ nguyên nhãn.
    TargetOption(Prefs.VK_TARGET_KACHI_VOICE),
) + InstalledApps.launchable(app).map { TargetOption(it.pkg, it.name) }

/**
 * Tên gợi ý cho nút vừa học, trước khi người dùng sửa — lặp lại `MainActivity.kt:812`
 * (`KEYCODE_VOICE_ASSIST` → "VOICE ASSIST"). Đây là **định danh của framework**, không phải câu chữ
 * nên không dịch.
 */
fun ClusterNavBridge.defaultLearnName(code: Int): String =
    android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')

/**
 * Đặt lại trợ lý hệ thống = Google/Gemini nếu đang có gán trỏ Gemini — **F4e**, chuyển từ
 * `MainActivity.maybeReapplyGeminiAssistant()` (màn cũ đã gỡ 2026-09-13).
 *
 * ## Vì sao nó phải còn tồn tại ở đâu đó
 * [ĐO] owner 2026-08-25: *"mở app lên, nhấn hold mic không work, xoá record binding gemini đi add lại thì nó
 * mới active lại assistant default rồi mới work"*. Đích Gemini đi `keyevent 231` — mã đó route tới **trợ lý hệ
 * thống**, nên chỉ ra Gemini khi `setSystemAssistant` đã chạy. Recipe đó chạy ở nút "Thêm gán" (khi cấu hình
 * đổi) và ở boot với retry `NONE`; nếu boot hỏng vì chưa ai bấm "Cho phép gỡ lỗi USB" thì phải có một đường
 * thứ hai, có mặt người dùng.
 *
 * ## Vì sao gắn vào [checkFix] chứ không vào lúc mở màn chính
 * Màn cũ chạy nó trong `onCreate`. Kachi là **launcher**: `onCreate` của nó chạy mỗi lần về màn chính, và
 * recipe này đi dadb chờ tới ~31 s (`AWAIT_ADB_APPROVAL`) — nối vào đó là một lời gọi dadb bất ngờ mỗi lần bấm
 * Home. [checkFix] là nút *"Kiểm tra / Sửa ngay"* của nhóm *Phím vô-lăng*: đúng lúc người dùng đang nói "nút
 * của tôi không chạy", và họ đang đứng trước màn xe để bấm hộp thoại cấp quyền.
 *
 * Chạy NỀN (không chặn luồng vẽ) và chỉ khi có gán Gemini — owner chỉ dùng Kiki thì KHÔNG đụng dadb.
 */
internal fun ClusterNavBridge.reapplyGeminiAssistant() {
    if (!AssistantLauncher.hasGeminiBinding(app)) return
    Thread({
        val err = runCatching {
            AssistantLauncher.setSystemAssistant(app, com.byd.clusternav.carexec.LocalShellRetry.AWAIT_ADB_APPROVAL)
        }.getOrElse { it.message.orEmpty() }
        if (err.isNotEmpty()) android.util.Log.w("ClusterNavBridge", "re-apply Gemini assistant: $err")
    }, "bridge-gemini-reapply").start()
}

/**
 * V1 pha NGHE — có vẽ **nút mic** trên thanh trạng thái không.
 *
 * Đi qua cầu này (chứ không để `SettingsBarsSection` gọi thẳng [Prefs]) vì đúng một lý do kiến trúc: tầng vẽ của
 * launcher **không mở cửa riêng vào nơi lưu bền** — mọi khoá THEO XE đã đi qua cầu từ `recircOnStart` tới
 * `headlessAutostart`, và một ngoại lệ là chỗ ngoại lệ thứ hai bắt đầu.
 *
 * Khoá theo XE, không theo hồ sơ: nó phụ thuộc **mô hình đã tải hay chưa** — thứ thuộc về máy, không thuộc về
 * người đang lái (xem KDoc [Prefs.voiceMicPill]).
 *
 * ⚠ [SOÁT Pass 2 · P2] Khai ở tệp mở rộng này chứ không ở `ClusterNavBridge.kt`: tệp đó đã 491 dòng trước pha
 * NGHE, thêm hai hàm vào là vượt trần 500 (CLAUDE.md §4.1 · spec R-nf4). Hành vi không đổi — vẫn là đúng hai lời
 * gọi [Prefs] qua `app` của cầu.
 */
internal fun ClusterNavBridge.voiceMicPill(): Boolean = Prefs.voiceMicPill(app)

/** Xem [voiceMicPill]. */
internal fun ClusterNavBridge.setVoiceMicPill(on: Boolean) = Prefs.setVoiceMicPill(app, on)
