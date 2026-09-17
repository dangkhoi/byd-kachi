package com.byd.clusternav.launcher.voice

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.byd.clusternav.launcher.LangHost
import com.byd.clusternav.net.HttpConn
import org.json.JSONArray

/**
 * ═══ V1.1 · TÊN ĐỊA ĐIỂM → TOẠ ĐỘ ════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R17**.
 *
 * ## Vì sao lớp này tồn tại (và vì sao nó KHÔNG phải một tính năng bản đồ)
 * [ĐO] nguồn Kiki đã decompile (`../jadx-kiki/sources/p449vq/AbstractC8122l.java:600`, `p478x/C8454u.java:226`):
 * VietMap Live nhận điểm đến qua `vietmaplive://companion/navigation?lat=…&lng=…&poiName=…` — tức **toạ độ**,
 * không phải chữ. Kiki giải tên-thành-toạ-độ ở máy chủ của họ. Kachi không có máy chủ, nên phải tự giải, và đây
 * là toàn bộ phần việc ấy: một chuỗi vào, một cặp số ra.
 *
 * ## ⚠ NGOẠI LỆ MẠNG — hẹp, cố ý, và phải đọc kỹ trước khi nới
 * Pha NGHE có một lời hứa hạng nhất: **tiếng nói không rời khỏi xe** (`VoiceCommandWiringContractTest`). Lớp này
 * là ngoại lệ thứ **hai** của lời hứa đó (thứ nhất là [VoiceModelStore] tải mô hình xuống), và nó vẫn giữ nguyên
 * lời hứa vì thứ đi ra là **một chuỗi tên địa điểm** — cùng loại chữ mà người ta gõ thẳng vào ô tìm kiếm của
 * Google Maps mười lần một ngày. **Không** một mẫu âm thanh nào, không nhận dạng trên mây, không định danh.
 *
 * Bốn chốt cứng giữ cho ngoại lệ này khỏi nở ra:
 *  1. đi qua **đúng cửa `HttpConn`** của dự án (chỉ HTTPS, có thời hạn chờ hai chiều);
 *  2. **chỉ GET**, không gửi thân yêu cầu nào;
 *  3. **chỉ chạy khi thật sự cần** — tức khi app đích không có cửa nhận chữ ([VoiceAppTarget.needsCoords]); với
 *     Google Maps/Waze thì đường chữ đã [ĐO] chạy nên không có lượt mạng nào;
 *  4. **giữ đúng nhịp điều khoản của máy chủ** (≤ 1 yêu cầu/giây — xem [MIN_GAP_MS]).
 *
 * ## Thứ tự: máy TRƯỚC, mạng SAU
 * [Geocoder] của nền tảng chạy **tại máy** trên một số ROM (có bộ dữ liệu ngoại tuyến) và đi qua dịch vụ của
 * Google trên số còn lại. `isPresent()` là cách duy nhất biết ROM này có cài dịch vụ ấy không — **[CHƯA BIẾT]**
 * trên DiLink, nên phải hỏi chứ không giả định. Không có thì mới tới Nominatim.
 */
object VoiceGeocoder {

    private const val TAG = "KachiVoiceGeo"

    /** Thời hạn cho lượt hỏi máy chủ — ngắn: người lái đang chờ, và mạng của xe hay treo (CLAUDE.md §11). */
    private const val READ_TIMEOUT_MS = 5_000

    /** Thời hạn CỨNG cho cả lượt giải (gồm cả đường on-device không có timeout) — xem [resolveBounded]. */
    private const val TOTAL_BUDGET_MS = 7_000L

    /**
     * Máy chủ tra cứu mở của OpenStreetMap.
     *
     * `countrycodes=vn` thu hẹp về Việt Nam: *"chợ Bến Thành"* có bản sao tên ở nhiều nơi, và một điểm đến sai
     * quốc gia là thứ người lái phát hiện ra sau ba mươi cây số.
     */
    private const val NOMINATIM =
        "https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=vn&q="

    /**
     * Giá trị `Accept` gửi kèm.
     *
     * ⚠ [SOÁT Pass 3 · P3] Hằng này **không phải** User-Agent (bản đầu đặt tên và chú thích như thể nó là UA).
     * Điều khoản Nominatim đòi một UA nhận dạng được, và UA đó do [HttpConn] đặt cho **mọi** lượt ra mạng của app
     * (`ClusterNav-Updater`, giữ nguyên chuỗi để nhật ký máy chủ của dự án không đứt mạch — xem KDoc ở đó). Ở đây
     * chỉ còn `Accept`, và nó vẫn cần: thiếu thì máy chủ có quyền trả HTML.
     */
    private const val ACCEPT_JSON = "application/json"

    /**
     * Khoảng cách TỐI THIỂU giữa hai lượt hỏi Nominatim — điều khoản sử dụng của họ: **≤ 1 yêu cầu/giây** cho
     * một nguồn, vượt là bị chặn IP.
     *
     * Một người lái nói một câu thì không thể vượt trần ấy; nhưng một cổng kỹ thuật không được dựa vào *"người
     * dùng chắc không làm thế"*. Đường thử bằng chữ bấm liên tiếp, hoặc một câu ghép hai vế dẫn đường, là đủ —
     * và cái giá của việc vượt trần không rơi vào lượt đó mà rơi vào **cả xe, cho mọi lượt sau**.
     */
    private const val MIN_GAP_MS = 1_000L

    /**
     * Mốc **sớm nhất** mà lượt hỏi TIẾP THEO được phép bắn (ms đồng hồ tường).
     *
     * ## [SOÁT 2026-09-16 · P3] Vì sao `AtomicLong` chứ không `@Volatile var`
     * Bản trước giữ *mốc lượt gần nhất* trong một `@Volatile Long` rồi làm **đọc → ngủ → ghi**. `@Volatile` cho
     * **thấy giá trị mới nhất**, KHÔNG cho **nguyên tử**: hai luồng nền (một vế dẫn đường + một lượt hỏi lại của
     * cùng câu, hoặc hai lượt gõ liên tiếp ở ô *"Gõ lệnh chữ"*) cùng đọc được `wait <= 0` rồi cùng bắn **trong
     * một giây** — đúng cái mà khoảng cách [MIN_GAP_MS] sinh ra để chặn, và Nominatim trả lời người vi phạm lặp
     * lại bằng **403 cho cả IP**, tức hỏng cho **mọi lượt sau**, không chỉ lượt vi phạm.
     *
     * Nay mỗi lượt **giành một chỗ** bằng CAS: người thắng biết mốc của mình và tự đẩy mốc cho người kế tiếp lên
     * thêm [MIN_GAP_MS]. Hai lượt đồng thời ⇒ hai chỗ cách nhau đúng một giây, không phải hai lượt cùng lúc.
     */
    private val nextOnlineAt = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Giải [place] thành toạ độ. **CHẶN** (đụng mạng) ⇒ gọi trên luồng NỀN.
     *
     * @return `null` khi không giải được — chỗ gọi phải **nói ra** điều đó, không được im lặng mở app trơn rồi
     *   báo dấu ✓ (xem [VoiceReply.navOpenedNoHandover]).
     */
    fun resolve(ctx: Context, place: String): VoiceAppIntents.Coords? {
        if (place.isBlank()) return null
        return onDevice(ctx, place) ?: online(place)
    }

    /**
     * [resolve] với **thời hạn CỨNG** — chống `Geocoder` của nền tảng treo vô hạn (owner báo "VietMap đơ" 09-17).
     *
     * ## Vì sao cần dù [online] đã có timeout
     * `online` (Nominatim) đi qua [HttpConn] có hạn đọc 5 s. Nhưng đường [onDevice] (`Geocoder.getFromLocationName`)
     * **không có** tham số timeout nào ở API 29, và trên một ROM mà `isPresent()` true nhưng dịch vụ đằng sau
     * không có mạng, nó có thể **chặn mãi** — luồng nền của lượt dẫn đường không bao giờ trả lời, người lái thấy
     * "đang tra điểm đến…" đứng im. Chạy trên một luồng phụ + `join(hạn)`; quá hạn ⇒ trả `null` (⇒ chỗ gọi lùi
     * về Google Maps dẫn bằng chữ). Luồng phụ là daemon: ca treo dai dẳng là điều kiện ROM cố định, sau lần đầu
     * chỗ gọi đã lùi sang GMaps nên không gọi lại — không rò luồng theo thời gian.
     */
    fun resolveBounded(ctx: Context, place: String): VoiceAppIntents.Coords? {
        if (place.isBlank()) return null
        var out: VoiceAppIntents.Coords? = null
        val worker = Thread { out = runCatching { resolve(ctx, place) }.getOrNull() }
            .apply { isDaemon = true; name = "kachi-geocode"; start() }
        worker.join(TOTAL_BUDGET_MS)
        if (worker.isAlive) Log.w(TAG, "geocode quá hạn ${TOTAL_BUDGET_MS}ms cho \"$place\" — lùi về dẫn bằng chữ")
        return out
    }

    /** Đường của nền tảng. `isPresent()` false ⇒ ROM không có dịch vụ nào đứng sau, gọi cũng chỉ trả rỗng. */
    private fun onDevice(ctx: Context, place: String): VoiceAppIntents.Coords? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            @Suppress("DEPRECATION")   // Bản `GeocodeListener` chỉ có từ API 33; app chạy từ API 29.
            // `LangHost.locale()` chứ không `Locale.getDefault()`: ngôn ngữ của **người dùng chọn trong Kachi**,
            // không phải của máy — cùng luật với mọi chỗ định dạng khác (`LauncherLocaleContractTest`).
            val hit = Geocoder(ctx, LangHost.locale()).getFromLocationName(place, 1)?.firstOrNull()
            hit?.let {
                VoiceAppIntents.Coords(it.latitude, it.longitude, it.featureName ?: place)
            }
        }.onFailure { Log.i(TAG, "Geocoder của máy không trả lời được \"$place\"", it) }.getOrNull()
    }

    /** Đường mạng — xem khối ⚠ ở KDoc lớp về vì sao nó được phép tồn tại. */
    private fun online(place: String): VoiceAppIntents.Coords? = runCatching {
        throttle()
        val conn = HttpConn.open(NOMINATIM + android.net.Uri.encode(place), READ_TIMEOUT_MS, ACCEPT_JSON)
        try {
            if (conn.responseCode != 200) {
                Log.w(TAG, "máy chủ tra cứu trả ${conn.responseCode}")
                return null
            }
            parse(conn.inputStream.bufferedReader().use { it.readText() }, place)
        } finally {
            conn.disconnect()
        }
    }.onFailure { Log.w(TAG, "không tra cứu được \"$place\"", it) }.getOrNull()

    /**
     * Giữ đúng nhịp ≤ 1 yêu cầu/giây ([MIN_GAP_MS]). **Chặn** ⇒ chỉ gọi trên luồng nền (đường duy nhất tới đây
     * đã ở luồng nền — `VoiceDispatcher.background`).
     *
     * Chờ chứ không **bỏ** lượt: bỏ thì người lái mất câu lệnh mà không hiểu vì sao; chờ tối đa một giây thì
     * cùng lắm chậm hơn một nhịp, và nhịp ấy đã có câu *"đang tra điểm đến…"* che.
     */
    private fun throttle() {
        val wait = claimSlot(System.currentTimeMillis()) - System.currentTimeMillis()
        if (wait > 0) runCatching { Thread.sleep(wait) }
    }

    /**
     * Giành **một chỗ** trong hàng ≤ 1 yêu cầu/giây; trả mốc (ms) mà lượt gọi này được phép bắn.
     *
     * `internal` + nhận [nowMs] làm tham số để bài kiểm off-car ép được ca hai luồng vào cùng một lúc mà không
     * phải dựa vào đồng hồ tường. Vòng `while` là dạng CAS chuẩn: thua thì đọc lại mốc **mới** rồi thử lại, nên
     * không lượt nào giành trùng chỗ của lượt khác.
     */
    internal fun claimSlot(nowMs: Long): Long {
        while (true) {
            val at = nextOnlineAt.get()
            val slot = maxOf(nowMs, at)
            if (nextOnlineAt.compareAndSet(at, slot + MIN_GAP_MS)) return slot
        }
    }

    /**
     * Đọc mảng JSON của Nominatim. `internal` để bài kiểm off-car chạy được **không cần mạng**.
     *
     * Tên trả về (`display_name`) bị cắt ở dấu phẩy đầu: chuỗi đầy đủ dài cả dòng (*"Chợ Bến Thành, Đường Lê
     * Lợi, Phường Bến Thành, Quận 1, …"*) và câu đọc lại cho người lái chỉ cần đoạn đầu.
     */
    internal fun parse(json: String, fallbackLabel: String): VoiceAppIntents.Coords? = runCatching {
        val first = JSONArray(json).takeIf { it.length() > 0 }?.getJSONObject(0) ?: return null
        val lat = first.optString("lat").toDoubleOrNull() ?: return null
        val lng = first.optString("lon").toDoubleOrNull() ?: return null
        val name = first.optString("display_name").substringBefore(',').ifBlank { fallbackLabel }
        VoiceAppIntents.Coords(lat, lng, name)
    }.onFailure { Log.w(TAG, "JSON tra cứu không đọc được", it) }.getOrNull()
}
