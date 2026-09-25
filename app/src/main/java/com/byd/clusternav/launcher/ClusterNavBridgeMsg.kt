package com.byd.clusternav.launcher

import com.byd.clusternav.navigation.NavReadChannel

/**
 * ═══ TỪ VỰNG của [ClusterNavBridge] — mã thông điệp, KHÔNG phải câu chữ ═════════════════════════
 *
 * ## Vì sao cầu không mang chữ
 * Tầng `launcher/` có luật cứng: **mọi chữ trên màn phải đi qua tài nguyên** (`strings_kachi.xml` +
 * `values-en/`), canh bởi `LauncherI18nContractTest`. Nhánh ClusterNav thì ngược lại — nó đặt nhãn
 * lúc chạy bằng `Lang.t(vi, en)` vì `res/values/strings.xml` đang **byte-seal** (T11) nên không thêm
 * khoá vào đó được.
 *
 * Cầu nằm ở giữa hai luật, nên nó **không chọn bên nào**: nó chỉ trả về **mã** ([BridgeMsg]) và
 * **dữ liệu thô** (tên gói, mã phím, trạng thái `:core`). Tầng Settings dịch mã sang câu bằng tài
 * nguyên của launcher. Nhờ vậy cầu không giữ một chuỗi ngôn ngữ nào, và khi màn cũ bị gỡ (OQ1) thì
 * không có câu nào phải dời chỗ.
 *
 * ## KDoc = hợp đồng dịch
 *
 * ⚠ **2026-09-13 — màn cũ đã GỠ HẲN** (`docs/specs/kachi-remove-legacy-screen.html` R1/R3). Mọi chỉ dẫn
 * `MainActivity.kt:<dòng>` dưới đây là **vết lịch sử**, không phải một tệp còn đọc được: chúng trỏ vào bản trước
 * commit gỡ màn (tra bằng `git log -- app/src/main/java/com/byd/clusternav/MainActivity.kt`). Giữ số dòng vì đó là
 * cách duy nhất còn lại để so hành vi của cầu với bản gốc; cầu nay là **nguồn duy nhất** của những hành vi đó.
 * Mỗi giá trị dưới đây ghi **nguyên văn VI/EN của màn cũ** kèm dòng gốc. T4 chép đúng hai câu đó vào
 * `strings_kachi.xml` / `values-en/strings_kachi.xml` — hai màn phải nói **cùng một lời**, nếu không
 * người dùng thấy hai câu khác nhau cho cùng một việc.
 */
enum class BridgeMsg {
    // ── Dẫn đường ───────────────────────────────────────────────────────────────────────────────

    /** VI "Đang cấp quyền đọc thông báo…" · EN "Granting notification access…" — `MainActivity.kt:107`. */
    GRANTING_NOTIFICATION,

    /**
     * VI "Đã cấp quyền — đã kết nối nguồn dẫn đường." · EN "Access granted — navigation connected."
     * — `MainActivity.kt:268`.
     */
    NOTIFICATION_GRANTED,

    /**
     * VI "Không mở được cài đặt. Vào Cài đặt → Ứng dụng → Truy cập đặc biệt → Truy cập thông báo →
     * bật ClusterNav." · EN "Couldn't open settings. Go to Settings → Apps → Special access →
     * Notification access → enable ClusterNav." — `MainActivity.kt:695–698`.
     */
    NOTIFICATION_FALLBACK,

    /** VI "Đang kết nối lại nguồn dẫn đường…" · EN "Reconnecting navigation source…" — `MainActivity.kt:258`. */
    RECONNECTING,

    // ── Chiếu cụm ───────────────────────────────────────────────────────────────────────────────

    /** VI "Đã bật Cluster Cast" · EN "Cluster Cast on" — `CastEnableSwitch.kt:68`. */
    CAST_ON,

    /**
     * VI "Đã tắt Cluster Cast · cụm về đồng hồ" · EN "Cluster Cast off · cluster back to gauges"
     * — `CastEnableSwitch.kt:76–78`.
     */
    CAST_OFF,

    /** VI "Đang trả app về…" · EN "Returning app…" — `MainActivityCastController.kt:190`. */
    CAST_RETURNING,

    /**
     * VI "Đã trả cụm về đồng hồ · đang mở lại…" · EN "Cluster reset · reopening…"
     * — `MainActivityCastController.kt:87`.
     */
    CLUSTER_RESET_REOPENING,

    /** VI "Đang dọn sạch cụm…" · EN "Deep-cleaning cluster…" — `CastDeepRescueAction.kt:71`. */
    DEEP_RESCUE_RUNNING,

    // ── Phím vô-lăng ────────────────────────────────────────────────────────────────────────────

    /** VI "Đang bật dịch vụ Hỗ trợ…" · EN "Enabling accessibility service…" — `MainActivity.kt:848`. */
    ENABLING_ACCESSIBILITY,

    /**
     * VI "Đã bật. Bấm nút đã gán để mở app." · EN "Enabled. Press the mapped button to open the app."
     * — `MainActivity.kt:852`.
     */
    ACCESSIBILITY_ENABLED,

    /**
     * VI "Chưa bật được Hỗ trợ — bấm Allow USB debugging trên xe rồi thử lại, hoặc bật tay ở Cài đặt >
     * Hỗ trợ." · EN "Couldn't enable accessibility — tap Allow USB debugging on the car and retry, or
     * enable it in Settings > Accessibility." — `MainActivity.kt:853`.
     */
    ACCESSIBILITY_FAILED,

    /** dadb CHẠY nhưng service chưa BIND (xe tải cao/ROM) — KHÔNG phải lỗi USB debugging. */
    ACCESSIBILITY_NOT_BOUND,

    /** VI "Đang kiểm tra…" · EN "Checking…" — `MainActivity.kt:873`. */
    CHECKING,

    /** VI "Phím-thoại đã sẵn sàng." · EN "Voice key ready." — `MainActivity.kt:880`. */
    VOICE_KEY_READY,

    /** VI "Đã xoá gán" · EN "Binding removed" — `MainActivity.kt:798`. */
    BINDING_REMOVED,

    /**
     * VI "Đã lưu nút. Chọn app rồi bấm “Thêm gán”." · EN "Button saved. Pick an app, then tap
     * “Add binding”." — `MainActivity.kt:824–827`.
     */
    BUTTON_SAVED,

    /** VI "Đã xoá nút" · EN "Button removed" — `MainActivity.kt:903`. */
    BUTTON_REMOVED,

    /**
     * VI "Giữ màn hình này mở rồi bấm nút vật lý muốn dùng…" · EN "Keep this screen open, then press
     * the physical button…" — `MainActivity.kt:913`.
     */
    LEARN_PRESS_BUTTON,

    /**
     * VI "Đang đặt Gemini làm trợ lý hệ thống…" · EN "Setting Gemini as system assistant…"
     * — `MainActivity.kt:966`.
     */
    SETTING_GEMINI_ASSISTANT,

    /**
     * VI "Đã đặt trợ lý = Google/Gemini. Giữ nút mic để NÓI (không mở app)." · EN "Assistant set to
     * Google/Gemini. Long-press mic to TALK (not open app)." — `MainActivity.kt:971`.
     */
    GEMINI_ASSISTANT_SET,

    /**
     * Màn cũ hiện **nguyên chuỗi lỗi** mà `AssistantLauncher.setSystemAssistant` trả về
     * (`MainActivity.kt:972`). Chuỗi đó do module khác dựng bằng `Lang.t`, cầu KHÔNG mang nó qua —
     * xem `ClusterNavBridgeKeys.addBinding`: bridge báo mã này và ghi chuỗi gốc vào `Log.w` để còn
     * grep được trên xe. VI "Chưa đặt được trợ lý hệ thống." · EN "Couldn't set the system assistant."
     */
    GEMINI_ASSISTANT_FAILED,

    // ── Tiện nghi xe · Hệ thống ─────────────────────────────────────────────────────────────────

    /** VI "Đang lọc bụi mịn…" · EN "Cleaning the air…" — `MainActivity.kt:1305`. */
    CLEANING_AIR,

    /**
     * Không có Activity để chạy luồng cập nhật (xem `ClusterNavBridge.checkUpdate`).
     * VI "Mở màn hình để kiểm tra cập nhật." · EN "Open the screen to check for updates."
     */
    UPDATE_NEEDS_SCREEN,

    /** Không mở được một màn nội bộ. VI "Không mở được màn hình." · EN "Couldn't open the screen." */
    SCREEN_OPEN_FAILED,
}

/**
 * Ba trạng thái của dòng chữ phím-thoại — lặp lại `MainActivity.kt:928–945`, nhưng trả **mã**:
 *  - [OFF] VI "Phím-thoại: đang tắt" · EN "Voice key: off" (xám);
 *  - [ACTIVE] VI "Phím-thoại: ĐANG HOẠT ĐỘNG ✓" · EN "Voice key: ACTIVE ✓" (xanh);
 *  - [DISCONNECTED] VI "Phím-thoại: MẤT KẾT NỐI — bấm Sửa ngay" · EN "Voice key: DISCONNECTED — tap
 *    Fix now" (đỏ).
 */
enum class VoiceKeyStatus { OFF, ACTIVE, DISCONNECTED }

/**
 * Nguồn dẫn đường đang chạy — dữ liệu THÔ của dòng "Đang dẫn: …" (`MainActivity.kt:429–439`).
 *
 * [brand] là **tên thương hiệu** (`NavSourceLabels.sourceLabel`, vd "Google Maps") — danh từ riêng,
 * không dịch. [channel] là enum `:core`; [stale] = quá `SourceArbiter.STALE_MS`. Câu hoàn chỉnh do
 * tầng Settings ghép từ tài nguyên.
 */
data class NavSourceView(
    val packageName: String,
    val brand: String,
    val channel: NavReadChannel,
    val stale: Boolean,
)

/**
 * Một mục trong danh sách NÚT (preset + nút tự học).
 *
 * [customName] = tên **người dùng tự đặt** lúc học phím (`Prefs.voiceKeyCustomButtons`) — chuỗi của
 * chính họ, không phải chữ của dự án nên không dịch. `null` ⇒ đây là **preset**: tầng Settings tra
 * tên theo [code] trong tài nguyên (bảng preset gốc ở `MainActivity.kt:707–717`).
 */
data class ButtonOption(val code: Int, val customName: String? = null) {
    val isPreset: Boolean get() = customName == null
}

/**
 * Một ĐÍCH gán được cho phím.
 *
 * [spec] là chuỗi bền ghi vào `voicekey_bindings`: tên gói, hoặc một trong ba sentinel
 * `Prefs.VK_TARGET_ASSIST` / `VK_TARGET_GEMINI_KEY` / `VK_TARGET_RECOGNIZER`. [appLabel] là tên ứng
 * dụng do **hệ thống** dịch (`PackageManager`); `null` ⇒ sentinel, tầng Settings tra tên trong tài
 * nguyên theo [spec].
 */
data class TargetOption(val spec: String, val appLabel: String? = null)
