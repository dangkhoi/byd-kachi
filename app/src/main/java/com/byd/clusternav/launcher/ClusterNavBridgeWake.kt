package com.byd.clusternav.launcher

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.Lang
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

    /** % tải gần nhất (−1 = chưa tải lượt nào). UI đọc để hiện "đang tải …%". */
    @Volatile var lastPercent: Int = -1
        private set
    /** Đang có lượt tải chạy không (UI). */
    val downloading: Boolean get() = fetching.get()

    fun ensure(app: Context) {
        // `isReady` = 5 lần `stat`; rẻ, và chạy ở đây để ca thường (đã có model) không dựng luồng nào.
        // ⚠ [ĐO xe 2026-09-21] `isReady` chỉ kiểm CÓ tệp + độ dài > 0, KHÔNG so sha. Nếu `keywords.txt` cũ (token
        // sai vocab ⇒ KWS encode fail, câu gọi không bao giờ nổ) còn nằm trên đĩa, `isReady=true` ⇒ bản mới không
        // bao giờ về. Nên: nếu ĐỦ tệp NHƯNG `keywords.txt` KHÔNG khớp ghim ⇒ xoá gói + tải lại.
        val ready = runCatching { VoiceModelStore.isReady(app, WakeModelCatalog) }.getOrDefault(false)
        if (ready && keywordsMatchPin(app)) return
        if (ready) {
            Log.i(TAG, "keywords.txt trên đĩa KHÁC bản ghim (token cũ sai vocab) — xoá gói + tải lại")
            runCatching { VoiceModelStore.remove(app, WakeModelCatalog) }
        }
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
            Log.i(TAG, "chép model câu gọi từ APK (${WakeModelCatalog.totalBytes / 1024} KB, ${WakeModelCatalog.files.size} tệp)")
            copyFromAssets(app)
            // Chép xong nhưng người dùng có thể đã TẮT công tắc trong lúc chép ⇒ đọc lại, đừng dựng bộ nghe sau lưng họ.
            if (!runCatching { Prefs.wakeEnabled(app) }.getOrDefault(false)) {
                Log.i(TAG, "công tắc đã tắt trong lúc chép — gói đã lưu, không bật bộ nghe")
                return
            }
            if (runCatching { VoiceModelStore.isReady(app, WakeModelCatalog) }.getOrDefault(false)) {
                Log.i(TAG, "model câu gọi đã sẵn sàng — dựng lại bộ nghe để nạp")
                VoiceWakeService.sync(app, reloadModel = true)
            }
        } catch (t: Throwable) {
            // Chép model KHÔNG được phép giết tiến trình: thiếu nó chỉ là degrade chỉ-RMS.
            Log.w(TAG, "chép model câu gọi lỗi — giữ chế độ chỉ-RMS", t)
        } finally {
            lastPercent = 100
            fetching.set(false)
        }
    }

    /**
     * Chép 5 tệp KWS từ `assets/voice/kws/` (đóng THEO APK, ~5 MB) sang `filesDir/kws/` — sherpa cần đường dẫn
     * tệp thật, không đọc được thẳng từ asset stream. Owner 2026-09-21: *"5MB nhét luôn vô APK, update app là
     * update luôn model"* ⇒ 0 mạng, 0 CDN, model luôn khớp phiên bản app. Chỉ chép tệp thiếu/lệch cỡ.
     */
    private fun copyFromAssets(app: Context) {
        val dest = VoiceModelStore.dir(app, WakeModelCatalog).apply { mkdirs() }
        val names = WakeModelCatalog.files.map { it.name }
        val total = names.size
        names.forEachIndexed { i, name ->
            val out = java.io.File(dest, name)
            app.assets.open("${WakeModelCatalog.ASSET_DIR}/$name").use { ins ->
                out.outputStream().use { os -> ins.copyTo(os, 64 * 1024) }
            }
            lastPercent = ((i + 1) * 100) / total
            Log.i(TAG, "chép $name (${out.length()} B) — $lastPercent%")
        }
    }

    /**
     * `keywords.txt` trên đĩa có khớp bản GHIM không (so sha256). Dùng để bắt ca "đủ tệp nhưng keywords.txt cũ"
     * mà `isReady` (chỉ check presence) bỏ sót. Đọc lỗi / chưa ghim ⇒ coi như KHÔNG khớp (an toàn: tải lại).
     */
    private fun keywordsMatchPin(app: Context): Boolean {
        val pin = WakeModelCatalog.files.firstOrNull { it.name == WakeModelCatalog.KEYWORDS } ?: return true
        if (!pin.pinned) return true
        return runCatching {
            val f = java.io.File(VoiceModelStore.dir(app, WakeModelCatalog), WakeModelCatalog.KEYWORDS)
            if (!f.isFile) return false
            val md = java.security.MessageDigest.getInstance("SHA-256")
            f.inputStream().use { ins -> val b = ByteArray(8192); var n = ins.read(b); while (n > 0) { md.update(b, 0, n); n = ins.read(b) } }
            md.digest().joinToString("") { "%02x".format(it) }.equals(pin.sha256, ignoreCase = true)
        }.getOrDefault(false)
    }
}

/** Trạng thái model câu gọi cho UI. */
enum class WakeModelState { NOT_DOWNLOADED, DOWNLOADING, READY }

/**
 * Trạng thái model câu gọi cho UI. Model đóng THEO APK (assets), nên thường là "Sẵn sàng"; "Đang chuẩn bị"
 * chỉ thoáng qua lần đầu bật (chép 5 MB từ APK ra filesDir). Đọc rẻ (stat), gọi được từ luồng UI.
 */
fun ClusterNavBridge.wakeModelStatus(): Pair<WakeModelState, String> = when {
    WakeModelFetch.downloading -> {
        val p = WakeModelFetch.lastPercent
        WakeModelState.DOWNLOADING to
            if (p in 1..99 || p == 0) Lang.t("Đang chuẩn bị model câu gọi… $p%", "Preparing wake model… $p%")
            else Lang.t("Đang chuẩn bị model câu gọi…", "Preparing wake model…")
    }
    runCatching { VoiceModelStore.isReady(app, WakeModelCatalog) }.getOrDefault(false) ->
        WakeModelState.READY to Lang.t("Model câu gọi đã sẵn sàng", "Wake model ready")
    else -> WakeModelState.NOT_DOWNLOADED to
        Lang.t("Chưa nạp model câu gọi (bật công tắc để chuẩn bị)", "Wake model not loaded (turn on to prepare)")
}
