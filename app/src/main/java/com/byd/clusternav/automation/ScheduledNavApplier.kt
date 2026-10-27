package com.byd.clusternav.automation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.SavedPlace
import com.byd.clusternav.launcher.SavedPlaces
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.automation.NavAutomationBook
import com.byd.clusternav.launcher.automation.NavHoldReason
import com.byd.clusternav.launcher.automation.NavTripPlan
import com.byd.clusternav.launcher.automation.NavTripStep
import com.byd.clusternav.launcher.automation.ScheduledNavPolicy
import com.byd.clusternav.launcher.automation.ScheduledNavRule
import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.KachiReadyLog
import com.byd.clusternav.launcher.trip.TripStart
import com.byd.clusternav.navAutomationTrip
import com.byd.clusternav.navAutomationRules
import com.byd.clusternav.setNavAutomationTrip
import com.byd.clusternav.system.PackageQueries
import android.os.SystemClock
import java.time.LocalDate
import java.time.LocalTime

/**
 * ═══ AUTOMATION #2 · DẪN ĐƯỜNG THEO LỊCH · PHẦN CHẠM ANDROID ═════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2 (§Design › :app). Mọi **luật** ở `:core` ([ScheduledNavPolicy] ·
 * [NavAutomationBook]); lớp này chỉ: đọc đồng hồ + mã chuyến · hỏi GPS · tra sổ địa chỉ · **bắn một ý-định dẫn
 * đường** · lưu kế hoạch của chuyến ([NavTripPlan] — lịch quyết MỘT lần lúc nổ máy, owner 27/10).
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

            val trip = TripStart.currentId(app)
            val saved = ScheduledNavPolicy.decode(Prefs.navAutomationTrip(app))
            val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            val dow = LocalDate.now().dayOfWeek.value          // ISO-8601: 1=T2 … 7=CN, khớp `ScheduledNavRules`
            val wake = KachiReadyLog.firstWakeAt()
            val sinceStart = if (wake < 0) null else SystemClock.elapsedRealtime() - wake
            // Hỏi GPS **chỉ khi** có luật cần nó — off-car/thiếu quyền thì lời gọi này còn ghi một dòng log.
            val gpsOk = if (rules.any { it.enabled && it.requireGps }) {
                GpsAvailability.isAvailable(app, nowMs) == true
            } else {
                false
            }

            val step = ScheduledNavPolicy.step(saved, trip, rules, nowMin, dow, sinceStart, gpsOk)
            step.plan?.let { if (it != saved) savePlan(app, it, "kế hoạch chuyến mới (giờ=$nowMin thứ=$dow)") }
            when (step) {
                is NavTripStep.Hold -> {
                    if (step.reason == NavHoldReason.NO_GPS || step.plan != saved) Log.i(TAG, "lịch: ${step.reason} (chuyến $trip)")
                    null
                }
                is NavTripStep.Launch -> {
                    val rule = step.rule
                    Log.i(TAG, "luật ${rule.id} dẫn cho chuyến $trip")
                    if (launch(app, rule)) {
                        // Lưu "đã dẫn" NGAY (commit) — thiếu bước này thì nhịp sau (60 s) mở app dẫn đường lần hai.
                        savePlan(app, step.plan.copy(fired = true), "đã dẫn")
                        rule
                    } else {
                        // KHÔNG lưu "đã dẫn" khi chưa giao được (app chưa cài / chưa mở được): thử lại nhịp sau.
                        null
                    }
                }
            }
        }.getOrElse {
            Log.w(TAG, "nhịp dẫn-theo-lịch lỗi (degrade-safe, bỏ qua)", it)
            null
        }
    }

    private fun savePlan(app: Context, plan: NavTripPlan, why: String) {
        Prefs.setNavAutomationTrip(app, ScheduledNavPolicy.encode(plan))
        Log.i(TAG, "lịch: $why ⇒ chuyến ${plan.trip} luật=${plan.ruleId.ifEmpty { "-" }} đã-dẫn=${plan.fired}")
    }

    /**
     * Sổ luật vừa sửa: kế hoạch chuyến trỏ tới luật không còn ⇒ bỏ lịch của chuyến ([ScheduledNavPolicy.pruned] —
     * `newId` cấp lại `id` đã rảnh, nên một kế hoạch mồ côi có thể thi hành nhầm luật vừa tạo).
     */
    fun pruneTripPlan(ctx: Context) {
        val app = ctx.applicationContext
        runCatching {
            val live = NavAutomationBook.decode(Prefs.navAutomationRules(app)).map { it.id }.toSet()
            val plan = ScheduledNavPolicy.decode(Prefs.navAutomationTrip(app))
            val pruned = ScheduledNavPolicy.pruned(plan, live)
            if (pruned != null && pruned != plan) savePlan(app, pruned, "luật của chuyến đã xoá")
        }
    }

    /**
     * Bắn dẫn đường tới MỘT điểm đến cho DANH SÁCH app — dùng bởi điểm đến chờ (TELEGRAM-REMOTE, spec
     * `kachi-telegram-remote.html` R2). Cùng cửa bắn với lịch ([launchOne]): `VoiceAppIntents.destinationHandoff`
     * + `.send`, nên cờ của đích (vd `CLEAR_TASK` Google Maps) đi y hệt — KHÔNG mở đường ghi thứ hai.
     *
     * GỌI TỪ THREAD NỀN (đọc `PackageManager`). `true` = ít nhất một app dẫn được.
     *
     * @param logId chuỗi nhận dạng ghi log (vd `"pending"`), thay cho `rule.id` của lịch.
     */
    fun launchPlace(ctx: Context, place: SavedPlace, apps: List<String>, logId: String = "pending"): Boolean {
        val app = ctx.applicationContext
        val installed = installedPackages(app)
        var anyOk = false
        for (navApp in apps) {
            val target = VoiceAppTargets.byKey(navApp) ?: run {
                Log.i(TAG, "$logId: mã app \"$navApp\" không còn trong bảng đích ⇒ bỏ"); null
            } ?: continue
            val pkg = target.packageIn(installed) ?: run {
                Log.i(TAG, "$logId: ${target.label} chưa cài ⇒ bỏ"); null
            } ?: continue
            if (!place.hasCoords && target.needsCoords) {
                Log.i(TAG, "$logId: \"${place.name}\" chưa có toạ độ mà ${target.label} chỉ nhận toạ độ ⇒ bỏ")
                continue
            }
            val coords = if (place.hasCoords) {
                VoiceAppIntents.Coords(place.lat!!, place.lng!!, place.query)
            } else {
                null
            }
            val handoff = VoiceAppIntents.destinationHandoff(target, pkg, place.query, coords) ?: run {
                Log.i(TAG, "$logId: ${target.label} không có đường giao điểm đến ⇒ bỏ"); null
            } ?: continue
            val ok = runCatching { VoiceAppIntents.send(app, handoff) }.getOrDefault(false)
            Log.i(TAG, "$logId: giao \"${place.name}\" cho ${target.label} ($pkg) ⇒ $ok")
            if (ok) anyOk = true
        }
        return anyOk
    }

    /**
     * ƯU TIÊN điểm đến chờ (TELEGRAM-REMOTE, spec R2/R3/R4/R5). Gọi TRƯỚC [tick] mỗi nhịp.
     *
     * @return `true` = đã bắn pending (chỗ gọi BỎ [tick] nhịp này — short-circuit); `false` = không pending /
     *   đang chờ GPS ⇒ chỗ gọi chạy lịch như cũ.
     *
     * Overwrite lịch: sau khi bắn, lưu [ScheduledNavPolicy.blocked] cho CHUYẾN hiện tại ⇒ lịch của chuyến này không
     * bao giờ dẫn (owner 27/10: lịch quyết một lần mỗi chuyến). Một-lần: [com.byd.clusternav.automation.PendingNavStore.consume] xoá pending. Chờ GPS:
     * [com.byd.clusternav.launcher.automation.PendingNavPolicy.Decision.WaitForGps] ⇒ giữ nguyên, thử lại nhịp
     * sau.
     */
    fun applyPending(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val app = ctx.applicationContext
        return runCatching {
            val p = com.byd.clusternav.automation.PendingNavStore.read(app) ?: return@runCatching false
            // P2-14: điểm đến chờ quá cũ (24h) ⇒ bỏ. setAtMs là wall-clock (currentTimeMillis), KHÔNG phải nowMs
            // (= elapsedRealtime từ FGS) ⇒ so bằng wall-clock riêng.
            if (com.byd.clusternav.automation.PendingNavStore.isStale(p, System.currentTimeMillis())) {
                com.byd.clusternav.automation.PendingNavStore.consume(app)
                Log.i(TAG, "pending: quá hạn 24h ⇒ bỏ")
                return@runCatching false
            }
            // Review 26/10 P2-3: KHÔNG truyền nowMs (FGS đưa elapsedRealtime) — GpsAvailability so tuổi fix bằng wall-clock.
            val gpsOk = if (p.requireGps) GpsAvailability.isAvailable(app) else null
            val decision = com.byd.clusternav.launcher.automation.PendingNavPolicy
                .resolve(p.dest, p.requireGps, gpsOk)
            when (decision) {
                is com.byd.clusternav.launcher.automation.PendingNavPolicy.Decision.FireNav -> {
                    Log.i(TAG, "pending: ưu tiên dẫn (apps=${p.dest.appsEncoded})")   // không log tên nơi (địa chỉ nhà)
                    val ok = launchPlace(app, p.dest.place, p.dest.apps, logId = "pending")
                    if (ok) {
                        // Overwrite lịch của CHUYẾN này (R3) — các chuyến sau (nổ máy lại) quyết lịch như thường.
                        savePlan(app, ScheduledNavPolicy.blocked(TripStart.currentId(app)), "điểm đến chủ động thắng")
                        com.byd.clusternav.automation.PendingNavStore.consume(app)
                        Log.i(TAG, "pending: đã bắn + chặn lịch chuyến này + consume")
                        true
                    } else {
                        // Không app nào dẫn được (chưa cài / chỉ chữ cho app cần toạ độ): GIỮ pending, thử lại
                        // nhịp sau — không consume một lần hỏng thành mất điểm đến.
                        Log.i(TAG, "pending: chưa app nào dẫn được ⇒ giữ, thử lại")
                        false
                    }
                }
                com.byd.clusternav.launcher.automation.PendingNavPolicy.Decision.WaitForGps -> {
                    Log.i(TAG, "pending: chờ GPS")
                    false
                }
                com.byd.clusternav.launcher.automation.PendingNavPolicy.Decision.FallThroughToSchedule -> false
            }
        }.getOrElse {
            Log.w(TAG, "nhịp pending lỗi (degrade-safe, bỏ qua)", it)
            false
        }
    }

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
        // Multi-app (owner 2026-09-24): mở dẫn TỪNG app đã chọn (gmaps + vietmap…). Thành công nếu ÍT NHẤT một
        // app dẫn được — các app kia lỗi/chưa cài chỉ bỏ lượt của nó, không huỷ cả luật. Đóng dấu sổ đã-dẫn nếu
        // có ≥1 app lên (ScheduledNavPolicy đọc kết quả này).
        var anyOk = false
        for (key in rule.navApps) {
            if (launchOne(app, rule, place, installed, key)) anyOk = true
        }
        return anyOk
    }

    private fun launchOne(
        app: Context, rule: ScheduledNavRule, place: SavedPlace, installed: Set<String>, navApp: String,
    ): Boolean {
        val target = VoiceAppTargets.byKey(navApp)
        if (target == null) {
            Log.i(TAG, "luật ${rule.id}: mã app \"$navApp\" không còn trong bảng đích ⇒ bỏ lượt")
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
        // Cửa DUY NHẤT dựng lượt giao điểm đến — cùng cửa với giọng nói (`VoiceTargetDispatch.deliver`), nên cờ của
        // đích (vd `CLEAR_TASK` cho Google Maps, [ĐO xe 29/09]) đi y hệt. `null` = app không nhận điểm đến với dữ liệu này.
        val handoff = VoiceAppIntents.destinationHandoff(target, pkg, place.query, coords) ?: run {
            Log.i(TAG, "luật ${rule.id}: ${target.label} không có đường giao điểm đến ⇒ bỏ lượt")
            return false
        }
        val ok = runCatching { VoiceAppIntents.send(app, handoff) }.getOrDefault(false)
        Log.i(TAG, "luật ${rule.id}: giao \"${place.name}\" cho ${target.label} ($pkg) ⇒ $ok")
        return ok
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
