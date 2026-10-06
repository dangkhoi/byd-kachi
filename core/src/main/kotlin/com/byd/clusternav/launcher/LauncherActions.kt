package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.camera.CameraDemand
import com.byd.clusternav.launcher.camera.CameraWhich

/**
 * ═══ S4 · R12 — HÀNH ĐỘNG CỦA CHÍNH LAUNCHER, ĐẶT ĐƯỢC NHƯ MỘT KHẢ NĂNG ══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` **R12 (b)**. Owner 2026-09-14: *"thêm cho chọn Ứng Dụng ở
 * chỗ chọn nút cho Thanh"*.
 *
 * ## Vì sao là một BỘ ĐĂNG KÝ THỨ SÁU chứ không phải một nút trong [ControlRegistry]
 * Mọi mã trong [ControlRegistry] đều **bắn lệnh xuống xe** qua `CarControlPort` (`toggle`/`step`/`press`…) và mang
 * một [EvidenceTier] nói *"lệnh này đã chạy thật trên xe chưa"*. *Ứng dụng* và *Cài đặt* không chạm vào xe một
 * chút nào — chúng mở ngăn kéo và mở màn Cài đặt của chính launcher. Nhét chúng vào [ControlRegistry] sẽ:
 *  • cho chúng một `bindingKey` HAL không tồn tại (bảng ràng buộc HAL có bài canh — nó sẽ đỏ, đúng),
 *  • và làm `ControlDockView` bắn `control().press("launcher_apps")` xuống cổng xe — một lệnh vô nghĩa gửi tới
 *    phần cứng, đúng loại "nối chéo âm thầm" mà [CapabilityCatalog] dựng ra để chặn.
 * Nên đây là **loại khả năng thứ ba** ([CapabilityKind.LAUNCHER]), không phải một biến thể của nút.
 *
 * ## Ba tính chất chốt ở đây (test khoá từng cái)
 *  1. **Tier [EvidenceTier.PROVEN] ⇒ KHÔNG chấm "chưa kiểm"**: đường mở ngăn kéo / mở Cài đặt là đường mà thanh
 *     trên đã dùng hằng ngày; treo dấu chưa-kiểm lên nó là nói sai, và làm dấu đó mất giá trị ở chỗ nó đúng.
 *  2. **KHÔNG chippable**: [TopStripConfig.isChippable] chỉ nhận mục ĐỌC, nên loại này bị từ chối **do cấu tạo** —
 *     chip 24dp là chỗ HIỂN THỊ, không phải chỗ bấm (xem KDoc [TopStripConfig]).
 *  3. **`domain = null`**: chúng không thuộc lĩnh vực nào của xe. Hệ quả cố ý: [CapabilityCatalog.byDomain] không
 *     bày chúng ⇒ bộ chọn nút phải có **khối riêng** ([CapabilityPicker.launcherPicks], nơi ghi vì sao khối ấy
 *     đứng đầu), và ngăn kéo gán-ô KHÔNG bày — một ô giữa màn chỉ để mở ngăn kéo là đổi chỗ đắt lấy việc rẻ.
 */

/**
 * Một hành động của launcher.
 *
 * @property id mã — cùng không gian mã PHẲNG với datum/nút/widget/nhóm/gói lệnh (xem [CapabilityCatalog]); tiền tố
 *   `launcher_` để đọc mã là biết ngay nó không chạm vào xe, và để bài canh xung đột chỉ đích danh được.
 * @property icon tên icon (bảng tra ở `:app` `KachiTheme.iconRes`) — **dùng lại đúng hình** mà thanh trên đang
 *   dùng cho cùng việc đó: hai bề mặt cùng một việc thì phải cùng một hình (luật U6).
 */
data class LauncherActionDef(
    val id: String,
    override val label: String,
    val icon: String,
    override val labelEn: String? = null,
    /**
     * 2.93 · CAMERA-ON-DEMAND — việc có trạng thái BẬT/TẮT (camera theo yêu cầu). Câu nói có động từ TẮT/ĐÓNG cho việc
     * này ⇒ `VoiceIntent.Launcher(off = true)`; việc khác (mở ngăn kéo · Cài đặt · phiên nghe) không có chiều "tắt" nên
     * động từ không đổi nghĩa (y như ≤ 2.92). DỮ LIỆU của dòng, không `if (id == …)` trong bộ phân tích (CLAUDE.md §7).
     */
    val switchable: Boolean = false,
    /**
     * 2.93 wave 2B · VOICE-CAM-OFF-360-FALLBACK (spec `kachi-293-cam.html` §10 D1 · OQ6) — mã NÚT XE ([ControlRegistry]) mà
     * câu TẮT của việc này mang nghĩa khi KHÔNG có gì để tắt (`CameraDemand.Outcome.NOTHING_TO_CLOSE`). *"Tắt camera"* trần
     * ≤ 2.92 là nút Camera 360 (`cam`, TẮT) ⇒ không camera theo yêu cầu nào đang mở thì câu ấy làm ĐÚNG việc cũ, qua đường
     * nút xe sẵn có của giọng nói (cổng hỏi lại + làn ghi) — không bao giờ bắn từ receiver camera. DỮ LIỆU của dòng (§7).
     */
    val offFallback: String? = null,
) : Localized

/** Bộ đăng ký hành động launcher — thuần Kotlin, kiểm off-car. */
object LauncherActions {

    /** Mở NGĂN KÉO ứng dụng (chế độ mở-thường, U3) — cùng đường với nút *Ứng dụng* ở thanh trên. */
    const val APPS = "launcher_apps"

    /** Mở màn **Cài đặt** của launcher — cùng đường với nút *Cài đặt* ở thanh trên (S1: MỘT cửa vào cấu hình). */
    const val SETTINGS = "launcher_settings"

    /**
     * V1 pha NGHE (R12) — mở **phiên nghe** của Kachi: bấm-để-nói, nhận dạng tại máy, không gửi gì ra mạng.
     *
     * ## Vì sao nó là một hành động LAUNCHER, không phải một nút xe
     * Cùng lý do với hai mã trên (xem KDoc lớp): nó không chạm `CarControlPort` một chút nào — nó bật micro của
     * chính đầu xe rồi đẩy câu nghe được vào **đúng đường mà một cú chạm đang đi**. Cho nó một `bindingKey` HAL
     * là khai một thứ không tồn tại.
     *
     * ## Vì sao mã tách rời khỏi đích phím vô-lăng
     * Cùng một việc, hai lối vào, nhưng **hai không gian mã khác nhau**: mã này sống trong danh mục khả năng
     * (đặt được lên thanh nút), còn `Prefs.VK_TARGET_KACHI_VOICE` sống trong bảng gán phím cùng chỗ với tên gói
     * app. Gộp chúng thành một chuỗi sẽ bắt một trong hai bảng phải hiểu quy ước của bảng kia.
     */
    const val VOICE = "launcher_voice"

    /**
     * F1 (owner 01/10, spec `kachi-launcher-shortcuts-autostart.html` R1.2) — **khối lối tắt ứng dụng** trên thanh nút:
     * *"có thể add vào taskbar của launcher nhé, như vậy size của widget đấy phải động"*. Một mã, nhưng ô của nó là
     * một KHỐI icon dài theo số app (`ShortcutIconsView`), không phải một nút.
     */
    const val SHORTCUTS = "launcher_shortcuts"

    // ── 2.93 · CAMERA-ON-DEMAND (spec `kachi-293-cam.html` R1) — bốn camera + *"Tắt camera"* ──────────────────────────
    //
    // Owner 06/10: *"đưa vào widget action, hoặc voice … trigger từ bind phím vật lý, hoặc widget action button, hoặc
    // voice"*. Một mã cho MỘT camera, dùng chung ba bề mặt: nút trên thanh nút (bật/tắt), câu nói (*"mở/tắt camera sau"*,
    // từ vựng sinh từ chính nhãn ở [ALL]), và cùng đường thi hành với phím vật lý (`cam:<mã>` — `CameraDemand`). Không
    // chạm `CarControlPort` (overlay camera là cửa sổ của chính Kachi) ⇒ đúng loại khả năng LAUNCHER, không phải nút xe.

    /** Camera SAU (dải 0 của khung ghép). */
    const val CAM_REAR = "launcher_cam_rear"

    /** Camera TRÁI (gương trái — cùng camera của xi-nhan trái). */
    const val CAM_LEFT = "launcher_cam_left"

    /** Camera PHẢI. */
    const val CAM_RIGHT = "launcher_cam_right"

    /** Camera TRƯỚC (dải 3). */
    const val CAM_FRONT = "launcher_cam_front"

    /** *"Tắt camera"* — tắt camera theo yêu cầu đang mở (nút trên thanh nút). Xem [BLOCKS] về vì sao không vào [ALL]. */
    const val CAM_OFF = "launcher_cam_off"

    /** Mã việc của một camera — một bảng, hai chiều ([cameraOf]). */
    private val CAMERA_IDS: Map<CameraWhich, String> = mapOf(
        CameraWhich.REAR to CAM_REAR,
        CameraWhich.LEFT to CAM_LEFT,
        CameraWhich.RIGHT to CAM_RIGHT,
        CameraWhich.FRONT to CAM_FRONT,
    )

    /** Mã việc *"camera [w]"*. */
    fun cameraId(w: CameraWhich): String = CAMERA_IDS.getValue(w)

    /** Camera của mã việc [id], `null` nếu [id] không phải việc camera (kể cả [CAM_OFF]). */
    fun cameraOf(id: String): CameraWhich? = CAMERA_IDS.entries.firstOrNull { it.value == id }?.key

    /** [id] là một trong năm việc camera theo yêu cầu (bốn camera + [CAM_OFF]). */
    fun isCamera(id: String): Boolean = id == CAM_OFF || cameraOf(id) != null

    /**
     * Lệnh camera của một CÂU NÓI về việc [id] — [CAM_OFF] ⇒ tắt camera đang mở; camera ⇒ [CameraDemand.spoken] (TẮT/ĐÓNG
     * = [off]); việc khác ⇒ `null`. Tầng thi hành (`VoiceDispatcher`) chỉ gọi hàm này, không tự rẽ nhánh theo mã.
     */
    fun cameraOp(id: String, off: Boolean): CameraDemand.Op? =
        if (id == CAM_OFF) CameraDemand.Op.CloseAll else cameraOf(id)?.let { CameraDemand.spoken(it, off) }

    /** Việc của launcher GỌI ĐƯỢC BẰNG LỜI — nguồn của từ vựng giọng nói (`VoiceGrammar`/hotword/danh mục câu). */
    val ALL: List<LauncherActionDef> = listOf(
        LauncherActionDef(APPS, "Ứng dụng", "ic-apps", labelEn = "Apps"),
        LauncherActionDef(SETTINGS, "Cài đặt", "ic-settings", labelEn = "Settings"),
        LauncherActionDef(VOICE, "Nói với xe", "ic-mic", labelEn = "Talk to car"),
        // 2.93 — bốn camera theo yêu cầu. Nhãn HAI từ (*"camera sau"*) thắng từ đồng nghĩa MỘT từ *"camera"* của nút
        // Camera 360 (`VoiceSynonyms.CONTROL["cam"]`) nhờ luật *"dãy dài nhất thắng"*. ⚠ *"tắt camera"* trần từ 2.93 là
        // [CAM_OFF] (`VoiceCameraPhrases`, spec §4.4 · OQ6) — tắt camera theo yêu cầu đang mở; KHÔNG có camera nào mở thì
        // câu ấy rơi về Camera 360 TẮT như ≤ 2.92 ([LauncherActionDef.offFallback], wave 2B). Camera 360 còn *"mở camera"* ·
        // *"tắt camera 360/toàn cảnh/quanh xe"* (bài `CameraDemandVoiceTest`). Hình mang VỊ TRÍ (`ic-cam-view-*`, luật U7 — bài
        // `CapabilityIconPositionTest`): quạt tầm nhìn ở đúng phía của thân xe nhìn từ trên.
        LauncherActionDef(CAM_REAR, "Camera sau", "ic-cam-view-rear", labelEn = "Rear camera", switchable = true),
        LauncherActionDef(CAM_LEFT, "Camera trái", "ic-cam-view-lf", labelEn = "Left camera", switchable = true),
        LauncherActionDef(CAM_RIGHT, "Camera phải", "ic-cam-view-rf", labelEn = "Right camera", switchable = true),
        LauncherActionDef(CAM_FRONT, "Camera trước", "ic-cam-view-front", labelEn = "Front camera", switchable = true),
    )

    /**
     * Mã đặt được trên thanh nút nhưng **không** phải một việc để gọi bằng lời, nên KHÔNG nằm trong [ALL]. Hai lý do:
     *
     * **(1) Khối** (F1 — lối tắt ứng dụng). Vì sao tách (lệch spec §4.4.2 *"thêm vào ALL"*, ghi ở §9): [ALL] là nguồn sinh
     * từ vựng giọng nói, câu mẫu, hotword và danh mục tính năng. *"Mở lối tắt ứng dụng"* không có nghĩa nào để thi hành —
     * khối là một CHỖ CHỨA icon, việc thật là chạm từng icon. Thêm vào [ALL] thì `VoiceDispatcher.runLauncher` nhận một mã
     * nó chỉ trả lời được *"chưa làm được"*, và tệp hotword phải dựng lại cho một câu vô nghĩa. Danh mục khả năng
     * ([CapabilityCatalog]) vẫn thấy khối này qua [byId]/[placeable] ⇒ bộ chọn nút thanh xe bày nó trong khối Launcher.
     *
     * **(2) [CAM_OFF] *"Tắt camera"*** (2.93): nhãn ấy MỞ ĐẦU bằng động từ, nên vào từ vựng thì luật *"cả câu là tên một
     * việc"* (`VoiceIntentParser.headMatch`) bắt luôn *"tắt camera 360"* (cụm 2 từ khớp ở vị trí 0) ⇒ tắt nhầm overlay
     * thay vì Camera 360 của xe. Bằng lời đã có *"tắt camera sau/trái/phải/trước"* (động từ + nhãn ở [ALL]).
     *
     * Hình của [CAM_OFF] là `ic-cam-off` (thân máy ảnh + gạch chéo một nét CHÍNH — khuôn `ic-sensor-off`), KHÁC `ic-cam` của
     * nút Camera 360: wave 2B · D4 — hai ô cạnh nhau trên thanh nút từng y hệt nhau.
     */
    val BLOCKS: List<LauncherActionDef> = listOf(
        LauncherActionDef(SHORTCUTS, "Lối tắt ứng dụng", "ic-apps", labelEn = "App shortcuts"),
        LauncherActionDef(CAM_OFF, "Tắt camera", "ic-cam-off", labelEn = "Camera off", offFallback = "cam"),
    )

    /** Mọi mã đặt được lên thanh nút: việc gọi bằng lời ([ALL]) rồi tới khối ([BLOCKS]). */
    val placeable: List<LauncherActionDef> get() = ALL + BLOCKS

    fun byId(id: String): LauncherActionDef? = ALL.firstOrNull { it.id == id } ?: BLOCKS.firstOrNull { it.id == id }

    /** Việc [id] có trạng thái BẬT/TẮT không ([LauncherActionDef.switchable]); mã lạ ⇒ `false`. */
    fun switchable(id: String): Boolean = byId(id)?.switchable == true

    /** Nút xe mà câu TẮT của việc [id] mang nghĩa khi không có gì để tắt ([LauncherActionDef.offFallback]); `null` = không. */
    fun offFallback(id: String): String? = byId(id)?.offFallback
}
