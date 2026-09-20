package com.byd.clusternav.automation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.SavedPlace
import com.byd.clusternav.launcher.SavedPlaces
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.automation.NavAutomationBook
import com.byd.clusternav.launcher.automation.NavAutomationFired
import com.byd.clusternav.launcher.automation.ScheduledNavPolicy
import com.byd.clusternav.launcher.automation.ScheduledNavRule
import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTarget
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.navAutomationFired
import com.byd.clusternav.navAutomationRules
import com.byd.clusternav.setNavAutomationFired
import com.byd.clusternav.system.PackageQueries
import java.time.LocalDate
import java.time.LocalTime

/**
 * ═══ AUTOMATION #2 · DẪN ĐƯỜNG THEO LỊCH · PHẦN CHẠM ANDROID ═════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2 (§Design › :app). Mọi **luật** ở `:core` ([ScheduledNavPolicy] ·
 * [NavAutomationBook] · [NavAutomationFired]); lớp này chỉ: đọc đồng hồ · hỏi GPS · tra sổ địa chỉ · **bắn một
 * ý-định dẫn đường** · đóng dấu đã-dẫn.
 *
 * ## Đi đúng đường mà một câu lệnh giọng nói đi
 * `VoiceTargetDispatch.runNavSaved` đã giải xong bài *"giao một điểm đến đã lưu cho app dẫn đường"*, và nó bắn
 * qua [VoiceAppIntents.send]. Lớp này dùng **chính** hàm đó — không dựng `Intent` thứ hai. Nếu dựng riêng thì
 * bản thứ hai sẽ lệch ở đúng chỗ bản đầu đã trả giá để học (phải `setPackage`, phải `resolveActivity` trước, phải
 * `NEW_TASK` mà **không** `CLEAR_TOP` — xem KDoc [VoiceAppIntents]).
 *
 * ## ⚠ `null` của GPS nghĩa là **CHỜ**, không phải "đi luôn"
 * [GpsAvailability.isAvailable] trả `null` khi chưa cấp quyền / ROM thiếu API. Coi `null` là *"có GPS"* thì luật
 * `requireGps` mất hiệu lực **im lặng** đúng trên chiếc xe chưa cấp quyền — tức nó sẽ dẫn trong hầm, đúng ca mà
 * R2.4 sinh ra để chặn. Nên `gpsOk = (isAvailable == true)`: chưa biết ⇒ chờ. Người dùng thấy được lý do vì hàng
 * quyền *"Định vị"* trong Cài đặt nói ra (xem `LauncherRequirements.LOCATION`).
 *
 * ## ⚠ Sổ địa chỉ theo HỒ SƠ, sổ luật theo XE — giao nhau thì bỏ lượt, không nổ
 * Luật lưu theo XE (R5) nhưng [SavedPlace] lưu theo **hồ sơ** (`<hồ sơ>__saved_places`). Đổi sang một hồ sơ
 * không có mục *"công ty"* ⇒ [SavedPlaces.find] trả `null` ⇒ **không dẫn**, và **không** đóng dấu đã-dẫn (để nếu
 * người dùng đổi lại hồ sơ trong khung giờ thì lượt đi vẫn còn). Ghi log ở mức I vì đây là ca giải thích được
 * chứ không phải lỗi.
 */
object ScheduledNavApplier {

    const val TAG = "KachiAutoNav"

    /**
     * Một nhịp đánh giá. GỌI TỪ THREAD NỀN (đọc prefs + `PackageManager`); lượt bắn ý-định đi qua
     * [VoiceAppIntents.send] và **không** cần luồng vẽ (`NEW_TASK` đã có sẵn trong mọi `Intent` nó dựng).
     *
     * @return luật đã dẫn, hoặc `null` (không luật nào tới lượt / không giao được).
     */
    fun tick(ctx: Context, nowMs: Long = System.currentTimeMillis()): ScheduledNavRule? {
        val app = ctx.applicationContext
        return runCatching {
            val rules = NavAutomationBook.decode(Prefs.navAutomationRules(app))
            if (rules.none { it.enabled }) return@runCatching null

            val today = todayKey()
            val fired = NavAutomationFired.decode(Prefs.navAutomationFired(app))
            val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            val dow = LocalDate.now().dayOfWeek.value          // ISO-8601: 1=T2 … 7=CN, khớp `ScheduledNavRules`
            // Hỏi GPS **chỉ khi** có luật cần nó — off-car/thiếu quyền thì lời gọi này còn ghi một dòng log, và
            // ghi nó mỗi phút cho một cấu hình không dùng GPS là nhiễu.
            val gpsOk = if (rules.any { it.enabled && it.requireGps }) {
                GpsAvailability.isAvailable(app, nowMs) == true
            } else {
                false
            }

            val rule = ScheduledNavPolicy.firstToLaunch(rules, nowMin, dow, gpsOk, fired, today)
                ?: return@runCatching null
            Log.i(TAG, "luật ${rule.id} tới lượt (giờ=$nowMin thứ=$dow gps=$gpsOk ngày=$today)")

            val launched = launch(app, rule)
            if (launched) {
                // Đóng dấu **NGAY** sau khi giao được (R2.4). Thiếu bước này thì nhịp sau (60 s) lại thấy "chưa
                // dẫn hôm nay" và mở app dẫn đường lần thứ hai — suốt cả khung giờ.
                val next = NavAutomationFired.put(fired, rule.id, today)
                Prefs.setNavAutomationFired(app, NavAutomationFired.encode(next))
                rule
            } else {
                // KHÔNG đóng dấu khi chưa giao được: app chưa cài / chưa mở được là ca **tạm thời**, nhịp sau thử
                // lại là đúng. Đóng dấu ở đây sẽ biến một lần hỏng thành mất cả lượt đi của ngày hôm đó.
                null
            }
        }.getOrElse {
            Log.w(TAG, "nhịp dẫn-theo-lịch lỗi (degrade-safe, bỏ qua)", it)
            null
        }
    }

    /**
     * Dọn dấu đã-dẫn của những luật **không còn tồn tại** — gọi sau mỗi lượt người dùng sửa sổ luật.
     *
     * Lý do đầy đủ ở KDoc [NavAutomationFired.prune]: `newId` cấp lại `id` đã rảnh, nên một dấu mồ côi sẽ chặn
     * đúng luật vừa được tạo.
     */
    fun pruneFired(ctx: Context) {
        val app = ctx.applicationContext
        runCatching {
            val live = NavAutomationBook.decode(Prefs.navAutomationRules(app)).map { it.id }.toSet()
            val fired = NavAutomationFired.decode(Prefs.navAutomationFired(app))
            val pruned = NavAutomationFired.prune(fired, live)
            if (pruned.size != fired.size) {
                Prefs.setNavAutomationFired(app, NavAutomationFired.encode(pruned))
                Log.i(TAG, "dọn ${fired.size - pruned.size} dấu đã-dẫn mồ côi")
            }
        }
    }

    /** Khoá ngày hôm nay, `yyyy-MM-dd` — quy ước của `:app` (xem KDoc [ScheduledNavPolicy]). */
    fun todayKey(): String = LocalDate.now().toString()

    /**
     * Tra điểm đến rồi giao cho app đã chọn. `false` = chưa giao được (**đã ghi log lý do**).
     *
     * Ba ca trả `false`, mỗi ca một lý do khác nhau và đều có thật: mục địa chỉ không còn (đổi hồ sơ / người dùng
     * xoá) · app dẫn đường chưa cài · app chỉ nhận toạ độ mà mục chỉ có chữ.
     */
    private fun launch(app: Context, rule: ScheduledNavRule): Boolean {
        val place = SavedPlaces.find(WorkspacePrefs(app).savedPlaces(), rule.placeId)
        if (place == null) {
            Log.i(TAG, "luật ${rule.id}: hồ sơ đang dùng không có địa chỉ \"${rule.placeId}\" ⇒ bỏ lượt")
            return false
        }
        val installed = installedPackages(app)
        val target = VoiceAppTargets.byKey(rule.navApp)
        if (target == null) {
            Log.i(TAG, "luật ${rule.id}: mã app \"${rule.navApp}\" không còn trong bảng đích ⇒ bỏ lượt")
            return false
        }
        val pkg = target.packageIn(installed)
        if (pkg == null) {
            Log.i(TAG, "luật ${rule.id}: ${target.label} chưa cài ⇒ bỏ lượt")
            return false
        }
        // Mục KHÔNG toạ độ + app chỉ nhận toạ độ (VietMap) ⇒ mở app trơn là vô nghĩa ở đây: automation không có ai
        // đang nhìn màn để gõ tay điểm đến. Bỏ lượt và nói ra trong log — cùng kết luận với
        // `VoiceTargetDispatch.runNavSaved`, chỉ khác cách báo (ở đó có người nghe được câu trả lời).
        if (!place.hasCoords && target.needsCoords) {
            Log.i(TAG, "luật ${rule.id}: \"${place.name}\" chưa có toạ độ mà ${target.label} chỉ nhận toạ độ ⇒ bỏ lượt")
            return false
        }
        val coords = if (place.hasCoords) {
            VoiceAppIntents.Coords(place.lat!!, place.lng!!, place.query)
        } else {
            null
        }
        val handoff = handoffOf(target, pkg, place, coords) ?: run {
            Log.i(TAG, "luật ${rule.id}: ${target.label} không có đường giao điểm đến ⇒ bỏ lượt")
            return false
        }
        val ok = runCatching { VoiceAppIntents.send(app, handoff) }.getOrDefault(false)
        Log.i(TAG, "luật ${rule.id}: giao \"${place.name}\" cho ${target.label} ($pkg) ⇒ $ok")
        return ok
    }

    /** Lượt giao việc cho [target]; `null` khi app không có đường nhận điểm đến với dữ liệu đang có. */
    private fun handoffOf(
        target: VoiceAppTarget,
        pkg: String,
        place: SavedPlace,
        coords: VoiceAppIntents.Coords?,
    ): VoiceAppIntents.Handoff? {
        val launch = target.destinationLaunch(coords != null) ?: return null
        return VoiceAppIntents.Handoff(pkg, launch, place.query, coords, target.fallback)
    }

    /**
     * Gói có mặt trên máy — qua [PackageQueries], **cửa duy nhất** của dự án tới `PackageManager`.
     *
     * Lấy theo activity LAUNCHER (cùng tập mà `VoiceWiring.appsByLabel` dùng) để hai đường trả lời cùng một câu
     * *"app này có trên xe không"* bằng cùng một phép đo.
     */
    private fun installedPackages(app: Context): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        PackageQueries.queryActivities(app.packageManager, intent)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())
}
