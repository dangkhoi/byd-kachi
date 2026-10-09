package com.byd.clusternav.launcher.escape

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import com.byd.clusternav.BuildConfig
import com.byd.clusternav.modules.clustercast.ClusterProfile
import com.byd.clusternav.system.FreeformSeedStore
import com.byd.clusternav.system.inputd.InputDaemonClient
import com.byd.clusternav.system.inputd.InputWireProtocol

/**
 * ═══ 2.98 · R18 (SLOT-ESCAPE-VD-RETURN) — phía KACHI của "app thoát ô ⇒ stack về lại màn ảo ô" ════════════════════════════════
 *
 * Spec `docs/specs/kachi-298-plan.html` R18. Kachi là nguồn sự thật DUY NHẤT về "màn ảo nào là ô nào, đang hiện gói nào" (daemon
 * uid 2000 không đọc được màn `FLAG_PRIVATE` của Kachi — [ĐO máy ảo 09/10]). Tệp này:
 *  - giữ bảng {khoá host ô → (màn ảo, gói)} do `VdAppHost` báo ([allow] TRƯỚC `am start` — app có thể thoát ~1–3 s sau khi mở;
 *    [revoke] khi ô nhả / đỗ / thôi giữ / app đã rời);
 *  - gửi bảng (đã mã hoá, [com.byd.clusternav.launcher.escape.EscapeReturnWire]) qua kênh daemon bơm chạm sẵn có
 *    ([InputDaemonClient.setControl]) — chỉ khi bảng ĐỔI;
 *  - đọc báo cáo daemon ([onReport]) ⇒ một dòng log `KachiEscape` (vào `usage-*.log`) và, khi daemon báo cầu chì BỀN (dấu hiệu NPE
 *    08-01 / task mất), ghi `kachi_escape_return_trip` = versionCode rồi gửi bảng TẮT (daemon gỡ bộ nghe).
 *
 * Bật khi: hồ sơ đời xe có bảng tên hàm ([ClusterProfile.escapeReturn]) đúng đời API đang chạy VÀ bản cài này chưa bị ngắt bền.
 * Không bật ⇒ bảng gửi đi không có api ⇒ daemon không nghe ⇒ app thoát ô đi đúng đường 2.93 (nhịp đo ô ⇒ `APP_ELSEWHERE`).
 * 0 nhịp hỏi, 0 lệnh shell: chỉ một khung điều khiển khi bảng đổi.
 */
internal object SlotEscapeReturn {

    const val TAG = "KachiEscape"

    /** Khoá dấu ngắt bền (tệp theo XE `clusternav_state`) — `EscapeReturnBreaker.persistValue`. */
    const val TRIP_KEY = "kachi_escape_return_trip"

    private val lock = Any()
    private val slots = LinkedHashMap<String, Pair<Int, String>>()
    private var lastSent: String? = null
    @Volatile private var client: InputDaemonClient? = null
    @Volatile private var app: Context? = null

    /** Host ô [key] sắp mở / đang giữ [pkg] trên màn ảo [vd]. Gọi được từ mọi luồng; không chặn. */
    fun allow(ctx: Context, inputClient: InputDaemonClient?, key: String, vd: Int, pkg: String) {
        if (vd < 1) return   // CLAUDE.md §4: màn ảo ô luôn ≥ 1; display 0 không bao giờ là đích
        app = ctx.applicationContext
        if (inputClient != null) client = inputClient
        synchronized(lock) { slots[key] = vd to pkg }
        push()
    }

    /** Host ô [key] thôi giữ app (nhả / đỗ / thôi giữ / app đã rời). Không có ⇒ không làm gì. */
    fun revoke(key: String) {
        val changed = synchronized(lock) { slots.remove(key) != null }
        if (changed) push()
    }

    /**
     * Màn ảo [vd] vừa được nhả (`VdLease.free` — MỌI đường nhả: ô nhả, ô bị màn chính khác lấy, đổi hồ sơ, huỷ màn). Gốc của bảng là
     * MÀN ẢO, không phải host: [ĐO máy ảo 09/10 22:52] màn chính dựng lại ⇒ `slot-taken` nhả màn ảo của host cũ mà host cũ chưa kịp
     * [revoke] ⇒ bảng còn một màn đã chết. Gỡ theo màn ở đúng chỗ màn chết thì không đường nhả nào sót.
     */
    fun revokeDisplay(vd: Int) {
        val changed = synchronized(lock) { slots.values.removeAll { it.first == vd } }
        if (changed) push()
    }

    /** Một dòng daemon báo về (luồng đọc của `InputDaemonClient`). */
    fun onReport(ctx: Context, line: String) {
        val r = EscapeReturnWire.parseReport(line) ?: return
        Log.i(TAG, "daemon: $line")
        if (r is EscapeReport.Tripped && r.persist) {
            persistTrip(ctx, r.why)
            synchronized(lock) { lastSent = null }
            push()
        }
    }

    /** Bảng tên hàm dùng được trên máy này lúc này (`null` = tắt). Đọc trong tiến trình, rẻ. */
    private fun api(ctx: Context): EscapeReturnApi? {
        val api = ClusterProfile.resolveCached(ctx).escapeReturn ?: return null
        if (!api.usableOn(Build.VERSION.SDK_INT)) return null
        return api.takeUnless { tripped(ctx) }
    }

    private fun push() {
        val ctx = app ?: return
        val c = client ?: return
        val api = runCatching { api(ctx) }
            .onFailure { Log.w(TAG, "đọc bảng tên hàm/dấu ngắt hỏng ⇒ coi như tắt: ${it.javaClass.simpleName}") }
            .getOrNull()
        // Soát R18 Pass 11 [P2]: mã hoá VÀ gửi trong cùng khoá — hai lượt đổi bảng từ hai luồng (`launchInto` nền · `release` luồng chính)
        // mà gửi ngoài khoá có thể tới daemon NGƯỢC thứ tự ⇒ daemon giữ bảng cũ tới lần đổi kế. `setControl` chỉ xếp hàng (không chặn).
        synchronized(lock) {
            val cfg = EscapeReturnConfig(api, slots.values.associate { (vd, pkg) -> vd to pkg })
            val text = EscapeReturnWire.encodeConfig(cfg)
            if (text == lastSent) return
            lastSent = text
            Log.i(TAG, "bảng → daemon: ${text.trim().replace("\n", " · ")}")
            c.setControl(InputWireProtocol.controlFrame(text.toByteArray(Charsets.UTF_8)), wantsDaemon = api != null && cfg.slots.isNotEmpty())
        }
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)

    private fun tripped(ctx: Context): Boolean =
        EscapeReturnBreaker.persistActive(prefs(ctx).getString(TRIP_KEY, null), BuildConfig.VERSION_CODE)

    @SuppressLint("ApplySharedPref")   // commit() đồng bộ: dấu ngắt phải nằm trên đĩa trước lượt khởi động lại kế (CLAUDE.md §5)
    private fun persistTrip(ctx: Context, why: String) {
        prefs(ctx).edit().putString(TRIP_KEY, EscapeReturnBreaker.persistValue(BuildConfig.VERSION_CODE, why)).commit()
        Log.w(TAG, "cầu chì BỀN: thôi đưa app thoát ô về màn ảo cho bản cài ${BuildConfig.VERSION_CODE} ($why) ⇒ đường 2.93")
    }
}
