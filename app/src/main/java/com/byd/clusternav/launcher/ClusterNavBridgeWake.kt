package com.byd.clusternav.launcher

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.voice.VoiceModelStore
import com.byd.clusternav.launcher.voice.VoiceWakeService
import com.byd.clusternav.launcher.voice.WakeModelCatalog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ W-WAKE — "Hey Kachi" trên cầu Settings (hàm mở rộng của [ClusterNavBridge]) ═════════════════════════════
 *
 * Cùng khuôn `ClusterNavBridgeHome`/`ClusterNavBridgeKeys`: màn Cài đặt KHÔNG ghi `Prefs.set` trực tiếp
 * (`SettingsScreenWiringContractTest` cấm — state trên màn và state bền phải đi qua một cửa). Công tắc "Hey
 * Kachi" đọc/ghi qua đây; ghi xong **đồng bộ luôn FGS** ([VoiceWakeService.sync]) để bật/tắt bộ nghe ngay.
 *
 * ## Bật công tắc cũng là lúc LẤY MODEL (crowd-test 2026-09-19)
 * Model KWS (~5 MB, [WakeModelCatalog]) **không** đóng theo APK: nó chỉ có nghĩa với ai bật tính năng này, mà
 * tính năng mặc định TẮT. Nên đường tải nằm đúng ở đây — cú chạm công tắc là lần duy nhất ta biết chắc người
 * dùng muốn nó. Ba tính chất của đường này, mỗi cái vá một cách hỏng cụ thể:
 *  • **Luồng nền, fire-and-forget.** `VoiceModelStore.install` CHẶN (mạng + sha256 5 MB); gọi trên luồng chính là
 *    treo màn Cài đặt vài giây và ANR nếu mạng xe chậm.
 *  • **Không chặn việc bật.** FGS lên NGAY; thiếu model thì `VoiceWakeKws.build` trả `null` ⇒ bộ nghe chạy
 *    **degrade chỉ-RMS** (đo được baseline CPU, không crash, không spam) tới khi gói về.
 *  • **Về rồi phải DỰNG LẠI bộ nghe.** Luồng nghe chỉ thử nạp model **một lần** cho cả vòng đời của nó, nên
 *    `sync(reloadModel = true)` là bước bắt buộc — thiếu nó thì gói vừa tải chỉ có tác dụng sau lần nổ máy sau.
 */
fun ClusterNavBridge.wakeEnabled(): Boolean = Prefs.wakeEnabled(app)

fun ClusterNavBridge.setWakeEnabled(on: Boolean) {
    Prefs.setWakeEnabled(app, on)
    VoiceWakeService.sync(app)
    if (on) WakeModelFetch.ensure(app)
}

/**
 * Thử lại lượt tải model KWS ở **mỗi lần nổ máy**, nếu công tắc đang BẬT mà gói chưa có — gọi từ `KachiAutostart`.
 *
 * ## ⚠ Vì sao một cú chạm công tắc là KHÔNG ĐỦ (soát 2026-09-19)
 * Cú chạm công tắc là lần duy nhất ta biết chắc người dùng muốn tính năng này, nhưng nó **không** phải lúc chắc
 * chắn có mạng — và trên xe thì thường là **không**: người ta gạt công tắc trong garage, hoặc giữa chuyến với 4G
 * chập chờn. Lượt tải hỏng ⇒ `VoiceWakeKws.build` trả `null` ⇒ bộ nghe chạy **degrade chỉ-RMS**, tức "Hey Kachi"
 * bật mà **không bao giờ nhận**, **không câu nào nói vì sao**, và không đường nào thử lại cho tới khi người dùng
 * tình cờ gạt tắt–bật. Với một lượt crowd-test mà mục đích DUY NHẤT là đo xem KWS có nhận "Hey Kachi" không, đó là
 * khác biệt giữa "đo được tỉ lệ nhận" và "không ai chạy được, mà không ai biết tại sao".
 *
 * Rẻ và an toàn: [WakeModelFetch.ensure] tự thoát sớm khi gói đã đủ (5 lần `stat`), tự chốt một-lượt-một-lần, chạy
 * trên luồng daemon `MIN_PRIORITY`, và bắt `Throwable` — nên nó **không** làm chậm và **không** làm hỏng lượt boot.
 */
internal fun ensureWakeModelIfEnabled(app: Context) {
    if (runCatching { Prefs.wakeEnabled(app) }.getOrDefault(false)) WakeModelFetch.ensure(app)
}

/**
 * Lấy gói model KWS về nếu chưa có — một lượt tại một thời điểm, trên luồng nền.
 *
 * Tách thành `object` (không phải một lambda trong hàm mở rộng) vì nó cần **state sống lâu hơn một cú chạm**:
 * cái chốt [fetching]. Không có chốt thì gạt công tắc tắt–bật vài lần là mở vài lượt tải 5 MB song song, và
 * `VoiceModelStore.install` sẽ từ chối những lượt sau bằng một câu lỗi ("đang cài rồi") mà người dùng đọc thành
 * *"tải thất bại"*.
 */
private object WakeModelFetch {

    private const val TAG = "KachiWakeModel"

    /** Chốt một-lượt-tải cho cả tiến trình. */
    private val fetching = AtomicBoolean(false)

    fun ensure(app: Context) {
        // `isReady` = 5 lần `stat`; rẻ, và chạy ở đây để ca thường (đã có model) không dựng luồng nào.
        if (runCatching { VoiceModelStore.isReady(app, WakeModelCatalog) }.getOrDefault(false)) return
        if (!fetching.compareAndSet(false, true)) {
            Log.i(TAG, "đã có một lượt tải model câu gọi đang chạy — bỏ qua lượt này")
            return
        }
        Thread({ run(app) }, "kachi-kws-fetch").apply {
            isDaemon = true            // luồng nền không được giữ tiến trình sống
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private fun run(app: Context) {
        try {
            Log.i(TAG, "tải model câu gọi (${WakeModelCatalog.totalBytes / 1024} KB, ${WakeModelCatalog.files.size} tệp)")
            VoiceModelStore.install(app, WakeModelCatalog) { step -> log(step) }
            // Tải xong nhưng người dùng có thể đã TẮT công tắc trong lúc chờ ⇒ đọc lại, đừng dựng bộ nghe sau lưng
            // họ. `sync` tự stopService khi công tắc tắt, nhưng gọi nó với `reloadModel` ở đây là vô nghĩa.
            if (!runCatching { Prefs.wakeEnabled(app) }.getOrDefault(false)) {
                Log.i(TAG, "công tắc đã tắt trong lúc tải — gói đã lưu, không bật bộ nghe")
                return
            }
            if (runCatching { VoiceModelStore.isReady(app, WakeModelCatalog) }.getOrDefault(false)) {
                Log.i(TAG, "model câu gọi đã sẵn sàng — dựng lại bộ nghe để nạp")
                VoiceWakeService.sync(app, reloadModel = true)
            }
        } catch (t: Throwable) {
            // Tải model KHÔNG được phép giết tiến trình: thiếu nó chỉ là degrade chỉ-RMS.
            Log.w(TAG, "tải model câu gọi lỗi — giữ chế độ chỉ-RMS", t)
        } finally {
            fetching.set(false)
        }
    }

    /** Một dòng nhật ký cho mỗi mốc; `Downloading` thì thưa ra (mỗi 20 %) để không nhận chìm logcat. */
    private fun log(step: VoiceModelStore.Step) {
        when (step) {
            is VoiceModelStore.Step.Downloading ->
                if (step.percent >= 0 && step.percent % 20 == 0) Log.i(TAG, "đang tải ${step.percent}%")
            is VoiceModelStore.Step.Done -> Log.i(TAG, "xong ${step.files} tệp")
            is VoiceModelStore.Step.Failed -> Log.w(TAG, "hỏng: ${step.reason}")
            else -> Log.i(TAG, "bước: ${step::class.simpleName}")
        }
    }
}
