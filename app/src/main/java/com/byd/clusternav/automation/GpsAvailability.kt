package com.byd.clusternav.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log

/**
 * ═══ AUTOMATION #2 · "CÓ ĐỊNH VỊ DÙNG ĐƯỢC KHÔNG" ════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R3. Trả lời **một** câu cho [ScheduledNavApplier]: usecase hầm (R2.4) —
 * *"vẫn trong khung 7–9h, chờ ra khỏi hầm có GPS mới dẫn"*.
 *
 * ## ⚠⚠ ĐỌC-CHỈ-ĐỌC. Đây là bất biến an toàn, không phải một lựa chọn phong cách
 * Ngày 2026-07-27 owner bỏ hẳn Dead Reckon + mock-location vì nó **ghim GPS của cả xe** (README: *"Đừng chọn
 * ClusterNav làm app mock-location"*). `DeadReckonRetirementTest` khoá lại hậu quả đó, và nó phải tiếp tục khoá:
 * lớp này dùng **đúng hai** lời gọi — [LocationManager.isProviderEnabled] và [LocationManager.getLastKnownLocation]
 * — và **tuyệt đối không**:
 *  • `addTestProvider` / `setTestProviderLocation` / `setTestProviderEnabled` (ghi vị trí giả cho **cả máy**);
 *  • `requestLocationUpdates` / `LocationListener` (bám định vị liên tục — tốn pin, và biến một phép hỏi thành
 *    một đường theo dõi; `getLastKnownLocation` đọc cái fix mà **app dẫn đường đang chạy** đã tạo ra rồi).
 * `DeadReckonRetirementTest.the app never writes or subscribes to location` quét **toàn bộ** `app/src/main` và
 * đỏ nếu bất kỳ dòng MÃ nào trong số đó xuất hiện (chú thích được phép nhắc tên — KDoc này là ca đó).
 *
 * ## Vì sao KHÔNG đủ khi chỉ hỏi "provider có bật không"
 * Trong hầm, GPS provider **vẫn bật** — chỉ là không có fix. Nên `isProviderEnabled` một mình trả lời `true` ở
 * đúng ca mà R2.4 sinh ra để chờ ⇒ luật sẽ nổ trong hầm, app dẫn đường mở lên và đứng ở một vị trí trống. Phải
 * hỏi **tuổi của lần fix gần nhất**, và đó là thứ cần quyền [android.Manifest.permission.ACCESS_FINE_LOCATION].
 *
 * ## Thiếu quyền ⇒ "KHÔNG BIẾT", không phải "không có GPS"
 * [isAvailable] trả `null` khi không đọc được (chưa cấp quyền · ROM thiếu API · `LocationManager` null). Chỗ gọi
 * ([ScheduledNavApplier]) phải quyết định nghĩa của `null`, và nó chọn **chờ** — xem KDoc ở đó. Trả `false` từ
 * đây sẽ trộn *"đang trong hầm"* với *"app chưa được cấp quyền"*, hai việc cần hai cách xử lý khác nhau.
 */
object GpsAvailability {

    private const val TAG = "KachiAutoGps"

    /**
     * Fix cũ hơn ngần này thì coi như **chưa có định vị** (spec R3: *"last-known trong ≤ N giây, N ~120s"*).
     *
     * 120 s là con số của spec, và nó rộng có chủ ý: mục đích không phải đo độ chính xác mà là phân biệt *"vừa ra
     * khỏi hầm, máy đã bắt lại vệ tinh"* với *"cái fix từ bãi đỗ tối qua"*. Hẹp hơn (vd 15 s) thì một nhịp poll
     * 60 s có thể rơi đúng vào khe giữa hai lần cập nhật của app dẫn đường ⇒ chờ thêm một phút vô cớ.
     */
    const val MAX_FIX_AGE_MS = 120_000L

    /**
     * Có định vị dùng được không. `null` = **không đọc được** (xem KDoc lớp).
     *
     * GỌI TỪ THREAD NỀN cho đồng nhất với hai applier còn lại (bản thân hai lời gọi này rẻ và không chặn, nhưng
     * [AutomationService] gọi cả ba trong cùng một lượt nên không có lý do để một cái chạy ở chỗ khác).
     */
    fun isAvailable(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean? = runCatching {
        val lm = ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@runCatching null
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d(TAG, "GPS provider đang TẮT ⇒ chưa có định vị")
            return@runCatching false
        }
        // Quyền RUNTIME có thể bị thu hồi giữa chuyến (Cài đặt hệ thống) ⇒ hỏi tường minh TRƯỚC khi gọi, trả
        // null = "không đọc được" (ĐÚNG, không phải "không có GPS"). `SecurityException` bên dưới vẫn được
        // `runCatching` ngoài đỡ cho ROM lệch — hai lớp, cùng một nghĩa.
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "chưa có quyền ACCESS_FINE_LOCATION ⇒ chưa biết")
            return@runCatching null
        }
        val fix = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: run {
                Log.d(TAG, "chưa có fix nào (provider bật) ⇒ chưa có định vị")
                return@runCatching false
            }
        val age = ageMs(fix.time, fix.elapsedRealtimeNanos, nowMs)
        val ok = age in 0 until MAX_FIX_AGE_MS
        Log.d(TAG, "fix tuổi ${age}ms (trần $MAX_FIX_AGE_MS) ⇒ ${if (ok) "CÓ" else "chưa có"} định vị")
        ok
    }.getOrElse {
        Log.i(TAG, "không đọc được định vị (${it.javaClass.simpleName}) ⇒ chưa biết")
        null
    }

    /**
     * Tuổi của một fix, ms. Ưu tiên **đồng hồ đơn điệu** (`elapsedRealtimeNanos`) rồi mới tới đồng hồ tường
     * (`Location.time`).
     *
     * ## Vì sao không chỉ lấy `now - fix.time`
     * Đầu xe **vặn đồng hồ tường** khi bắt được thời gian từ GPS/mạng lúc nổ máy. Một cú vặn về phía trước làm
     * `now - fix.time` phình ra hàng giờ ⇒ fix vừa lấy xong bị coi là cũ (chờ vô cớ); vặn lùi làm hiệu **âm** ⇒
     * nếu chỉ so `< trần` thì một hiệu âm lọt qua thành *"rất mới"* (dẫn trong hầm). `elapsedRealtimeNanos` tính
     * từ lúc khởi động máy nên nó không bị vặn; dải `0 until trần` ở [isAvailable] chặn nốt hiệu âm còn lại.
     */
    private fun ageMs(fixTimeMs: Long, fixElapsedNanos: Long, nowMs: Long): Long {
        if (fixElapsedNanos > 0L) return SystemClock.elapsedRealtime() - fixElapsedNanos / 1_000_000L
        return nowMs - fixTimeMs
    }
}
