package com.byd.clusternav.launcher.camera

/**
 * ═══ NGUỒN **MỘT KÊNH** (`camera_source = CHANNEL`) — phép hợp kênh + máy trạng thái LÙI VỀ TOÀN CẢNH ═══════════
 *
 * Phần mở rộng của [CameraSignalPolicy] (tệp kia đã 490/500 dòng — CLAUDE.md §4.1; cùng lối `CameraDewarpPrefs`
 * đứng cạnh `CameraDewarp`). Hai vai trong một tệp vì chúng là **một** đường: hợp ra kênh nào để gọi HAL, rồi khi
 * HAL nói *không* thì lùi về khung ghép — đúng một lần.
 *
 * ## Kênh đến từ HỒ SƠ xe, không từ enum (2.76 · R2)
 * 2.75 ghim `MIRROR_LEFT.channel = 2 / MIRROR_RIGHT.channel = 3` trên `CamView` — số đo của **Seal** viết vào một
 * enum dùng cho mọi xe ([P3] review Pass 2). Nay kênh đi [CameraProfileDefaults.channel] ⇒ `ClusterProfile` của đời
 * xe nào khai số đo của đời ấy; đời chưa đo trả [CameraProfileDefaults.CHANNEL_UNKNOWN] ⇒ [channelFor] về AUTO và
 * [channelActive] là `false` — tức **không** có tầng hình học nào đo theo cỡ nội dung `/4` trên một khung thật ra
 * là khung ghép (ca sai im lặng của 2.75 khi owner chọn CHANNEL trên xe chưa có bản đồ kênh).
 */

/** Đang lấy MỘT kênh camera (khung bị kéo ngang) hay khung ghép? Mã lạ ⇒ [CameraSignalPolicy.defaultSource]. */
fun CameraSignalPolicy.usesChannel(source: String): Boolean =
    (if (isSource(source)) source else defaultSource()) == SOURCE_CHANNEL

/**
 * Kênh HAL thật sự truyền cho `addPreviewSurface(surface, int)` ở lượt mở.
 *
 * @param source [CameraSignalPolicy.SOURCE_PANO] ⇒ giữ nguyên [halModePref] (mặc định AUTO = đường 2.73).
 * @param halModePref pref `camera_hal_mode`. Là một kênh thật (`1..HAL_MODE_MAX`) ⇒ **owner đè**, dùng nó.
 * @param profileChannel kênh của gương đang hiện theo **hồ sơ xe** ([CameraProfileDefaults.channel]);
 *   [CameraProfileDefaults.CHANNEL_UNKNOWN] ⇒ AUTO, không bịa một kênh nào.
 */
fun CameraSignalPolicy.channelFor(source: String, halModePref: Int, profileChannel: Int): Int {
    if (!usesChannel(source)) return halModePref
    if (CameraProfileDefaults.isChannel(halModePref)) return halModePref
    return if (CameraProfileDefaults.isChannel(profileChannel)) profileChannel else HAL_MODE_AUTO
}

/**
 * Phiên này có **thật sự** chạy ở chế độ một kênh không — điều kiện để mọi tầng hình học đo theo cỡ NỘI DUNG
 * ([CameraPanoCrop.contentWidth]) và để [CameraChannelFallback] có việc để làm.
 *
 * `usesChannel` một mình **không đủ**: owner chọn CHANNEL trên xe chưa có bản đồ kênh ⇒ [channelFor] trả AUTO ⇒ HAL
 * dò `0..3` và trả khung **ghép**, mà tầng vẽ lại chia bề ngang cho 4 ⇒ `aspect` sai, `K` sai, ảnh vẫn "ra hình".
 * Một chỗ hợp, mọi tầng hỏi đúng một câu.
 */
fun CameraSignalPolicy.channelActive(source: String, halMode: Int): Boolean =
    usesChannel(source) && CameraProfileDefaults.isChannel(halMode)

/**
 * Ghi chú cho lời đáp `camera_frame` (R3): khung của nguồn một kênh bị HAL **kéo ngang** đầy buffer — ai đo bán kính/
 * tâm vòng ảnh trên PNG ấy mà không biết điều này sẽ ra một ellipse. Rỗng ở nguồn khung ghép.
 */
fun CameraSignalPolicy.frameNote(channel: Boolean): String =
    if (channel) "anamorphic x${CameraPanoCrop.STRIPS}" else ""

/**
 * ═══ Máy trạng thái LÙI VỀ TOÀN CẢNH — thuần, một phiên camera, **đúng một lần** ═══════════════════════════════
 *
 * R3 (2.76). Nguồn CHANNEL có hai cách hỏng mà xe của owner không nhìn thấy: HAL **từ chối** kênh
 * (`addPreviewSurface(surface, n)` trả `rc = false` — 2.75 rơi im về `addPreviewSurface(surface)` một tham số, tức
 * khung ghép, trong khi tầng vẽ vẫn tin là một kênh), hoặc HAL **nhận** nhưng **không đẩy khung nào** trong ngân
 * sách [FIRST_FRAME_BUDGET_MS]. Cả hai ⇒ dựng lại phiên ở PANO, theo **đúng đường** "none → left" mà controller đã
 * có (bộ uniform là bất biến theo phiên — đổi giữa hai khung không có tác dụng), ghi **một** dòng log, và để lại một
 * dấu cho Cài đặt nói *"đã lùi về toàn cảnh"*.
 *
 * ## Vì sao "đúng một lần" là bất biến quan trọng nhất
 * Nếu phiên PANO dựng lại cũng không có khung (camera hỏng thật) mà máy vẫn *lùi* lần nữa thì controller dựng
 * phiên vô hạn trên xe đang lăn bánh. [fellBack] chốt sau lượt đầu và chỉ mở lại ở [onSessionStart] của một phiên
 * do **xi-nhan** mở (không phải phiên do chính phép lùi mở) — bài `CameraChannelFallbackTest` ghim từng nhánh.
 *
 * Không có `android.*`: controller chỉ đọc [Action] rồi làm; số học/ngưỡng đo được off-car.
 */
class CameraChannelFallback {

    /** Việc controller phải làm sau một sự kiện. */
    enum class Action { NONE, REBUILD_PANO }

    /** Phiên hiện tại đã lùi về PANO chưa — cờ RAM, chết theo phiên, **chỉ để quyết trong phiên này**. */
    var fellBack: Boolean = false
        private set

    /**
     * Một phiên vừa mở. [forcedPano] = phiên do chính phép lùi mở ⇒ **giữ** [fellBack] (nếu không thì phiên PANO
     * hỏng lại được "lùi" lần hai); phiên do xi-nhan mở ⇒ đặt lại, cho phép owner thử CHANNEL ở lượt rẽ sau.
     */
    fun onSessionStart(forcedPano: Boolean) {
        if (!forcedPano) fellBack = false
    }

    /**
     * HAL vừa trả lời `addPreviewSurface`. [channel] = phiên đang ở chế độ một kênh ([channelActive]);
     * [channelRefused] = kênh đơn được xin mà `rc != true` (`AvmCamera.channelRefused`).
     */
    fun onHalResult(channel: Boolean, channelRefused: Boolean): Action =
        decide(channel && channelRefused)

    /**
     * Ngân sách khung đầu đã hết. [frames] = số khung luồng vẽ đã nhận ([framesOf]); `null` = **không đếm được**
     * (đường `TV`/`SV` không có bộ đếm) ⇒ [Action.NONE] — không lùi theo một số liệu không có (CLAUDE.md §2).
     */
    fun onFirstFrameBudget(channel: Boolean, frames: Long?): Action =
        decide(channel && frames == 0L)

    /** Nguồn thật cho lượt dựng: đã lùi ⇒ PANO bất kể pref; chưa ⇒ đúng pref. */
    fun sourceFor(pref: String): String =
        if (fellBack) CameraSignalPolicy.SOURCE_PANO else pref

    private fun decide(shouldFallBack: Boolean): Action {
        if (!shouldFallBack || fellBack) return Action.NONE
        fellBack = true
        return Action.REBUILD_PANO
    }

    companion object {
        /**
         * Ngân sách chờ **khung đầu** của một phiên CHANNEL (ms) — **[SUY]** từ hai số đo, chưa đo trực tiếp:
         * [ĐO CAM-B4 xe 27/09] HAL đẩy ~34 khung/giây khi đã chạy (chu kỳ 29 ms) ⇒ khung đầu tới trong vài chục ms
         * sau `startPreview`; [ĐO E9 27/09] helper HAL đứt và nối lại mất 1,7 s. Ba giây = gấp ~1,7 lần ca chậm đã
         * thấy, và ngắn hơn một lượt rẽ/chuyển làn thường (xi-nhan giữ ≥ 3–5 s [ĐO log 27/09: phiên 09:58:15 →
         * 09:58:26]) để phép lùi kịp cho **một khung có hình** ngay trong lượt rẽ ấy. (`CameraHold.HOLD_MS` = 1,2 s
         * là khoảng nối pha TẮT của đèn nháy SAU một OFF — không liên quan tới ngân sách này.) 🚗 CAM-F2 đo khung
         * đầu thật rồi siết.
         */
        const val FIRST_FRAME_BUDGET_MS = 3_000L

        private val FRAMES = Regex("""\bframes=(\d+)""")

        /**
         * Số khung trong chuỗi thống kê của luồng vẽ (`"frames=N busySkip=M …"` — `CameraGlRenderer.stats`), `null`
         * khi chuỗi không có bộ đếm (đường không phải GL trả rỗng). Đọc từ chuỗi vì tầng vẽ đã phơi đúng chuỗi ấy
         * cho `camera_frame`; không mở một cửa đếm thứ hai.
         */
        fun framesOf(stats: String): Long? = FRAMES.find(stats)?.groupValues?.get(1)?.toLongOrNull()
    }
}
