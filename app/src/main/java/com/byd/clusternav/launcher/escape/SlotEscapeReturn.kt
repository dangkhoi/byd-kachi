package com.byd.clusternav.launcher.escape

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import com.byd.clusternav.BuildConfig
import com.byd.clusternav.Prefs
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
 * Bật khi: hồ sơ đời xe có bảng tên hàm ([ClusterProfile.escapeReturn]) đúng đời API đang chạy VÀ bản cài này chưa bị ngắt bền
 * VÀ người dùng bật công tắc *Cài đặt › Màn hình chính › Kéo app thoát ô về lại ô (thử nghiệm)* (MẶC ĐỊNH TẮT — owner 10/10,
 * [EscapeReturnSwitch]). Không bật ⇒ bảng gửi đi là [EscapeReturnConfig.OFF] (không api, không ô — bảng hằng nên đổi ô cũng không
 * sinh khung nào) ⇒ daemon không đăng ký bộ nghe ⇒ app thoát ô đi đúng đường 2.93 (nhịp đo ô ⇒ `APP_ELSEWHERE`). Đổi công tắc ⇒
 * [refresh] gửi lại bảng ngay (daemon đăng ký / gỡ bộ nghe tại chỗ, không cần khởi động lại).
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

    /** Công tắc / cầu chì vừa đổi ⇒ tính lại bảng và gửi nếu khác (bật ⇒ daemon đăng ký bộ nghe; tắt ⇒ gỡ). Không chặn. */
    fun refresh(ctx: Context) {
        app = ctx.applicationContext
        push()
    }

    /** Trạng thái hiện hành cho dòng mô tả ở màn Cài đặt — CÙNG phép quyết với bảng gửi daemon ([EscapeReturnSwitch.resolve]). */
    fun status(ctx: Context): EscapeReturnSwitch.Status = EscapeReturnSwitch.resolve(
        switchOn = Prefs.escapeReturnEnabled(ctx),
        profileApi = ClusterProfile.resolveCached(ctx).escapeReturn,
        sdkInt = Build.VERSION.SDK_INT,
        tripStored = prefs(ctx).getString(TRIP_KEY, null),
        versionCode = BuildConfig.VERSION_CODE,
    )

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

    private fun push() {
        val ctx = app ?: return
        val c = client ?: return
        val status = runCatching { status(ctx) }
            .onFailure { Log.w(TAG, "đọc công tắc/bảng tên hàm/dấu ngắt hỏng ⇒ coi như tắt: ${it.javaClass.simpleName}") }
            .getOrNull() ?: EscapeReturnSwitch.Status.Off
        // Soát R18 Pass 11 [P2]: mã hoá VÀ gửi trong cùng khoá — hai lượt đổi bảng từ hai luồng (`launchInto` nền · `release` luồng chính)
        // mà gửi ngoài khoá có thể tới daemon NGƯỢC thứ tự ⇒ daemon giữ bảng cũ tới lần đổi kế. `setControl` chỉ xếp hàng (không chặn).
        synchronized(lock) {
            val cfg = EscapeReturnSwitch.config(status, slots.values.associate { (vd, pkg) -> vd to pkg })
            val text = EscapeReturnWire.encodeConfig(cfg)
            if (text == lastSent) return
            lastSent = text
            Log.i(TAG, "bảng → daemon: ${text.trim().replace("\n", " · ")}")
            c.setControl(InputWireProtocol.controlFrame(text.toByteArray(Charsets.UTF_8)), wantsDaemon = cfg.enabled && cfg.slots.isNotEmpty())
        }
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)

    @SuppressLint("ApplySharedPref")   // commit() đồng bộ: dấu ngắt phải nằm trên đĩa trước lượt khởi động lại kế (CLAUDE.md §5)
    private fun persistTrip(ctx: Context, why: String) {
        prefs(ctx).edit().putString(TRIP_KEY, EscapeReturnBreaker.persistValue(BuildConfig.VERSION_CODE, why)).commit()
        Log.w(TAG, "cầu chì BỀN: thôi đưa app thoát ô về màn ảo cho bản cài ${BuildConfig.VERSION_CODE} ($why) ⇒ đường 2.93")
    }
}
