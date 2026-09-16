package com.byd.clusternav.launcher

import android.content.Context
import android.media.AudioManager
import com.byd.clusternav.modules.hal.BydHal

/**
 * [HalGateway] THẬT — bọc hạ tầng reflection SẴN CÓ [BydHal] (TÁI DÙNG, KHÔNG mở reflection mới) + local Android
 * (AudioManager). Đúng công thức proven ở [com.byd.clusternav.comfort.SeatComfortApplier] /
 * [com.byd.clusternav.body.BodyworkControl]: `device(FQN, systemBypassContext, bypass(app))` → `callGetter` /
 * `callNamedInt` / `setInt` / `tryGet`.
 *
 * Degrade-safe (R9): off-car → `systemBypassContext()` null + `device()` null (BydHal tự bọc runCatching) ⇒ mọi
 * đường trả null/false ⇒ UI "—". KHÔNG BAO GIỜ ném ra ngoài. KHÔNG gate an toàn.
 *
 * ⚠ **Cast (AutoContainer) KHÔNG wire ở đây** — cluster-cast do `SimpleCastRuntime` sở hữu (ràng buộc: không đụng
 * logic cast). [localSet] cho `AutoContainer` trả false (no-op). ⚠ **car-setting** (`settingGet/settingSet`) trả
 * null (đường setting chưa proven — grab-list §9); off-car null nên vô hại.
 */
class BydHalGateway(context: Context) : HalGateway {

    private val app: Context = context.applicationContext

    /**
     * ═══ H1 (PERF 2026-09-16) — NHỚ tay cầm device thay vì dựng lại mỗi lượt đọc ═════════════════════════════
     *
     * [ĐO máy ảo 2026-09-16] bản 1.66: **1 404 lượt đọc/phút**, và MỖI lượt chạy trọn công thức resolve:
     * `systemBypassContext()` (reflection `ActivityThread.currentActivityThread` + `getSystemContext` + dựng một
     * `ContextWrapper` bọc quyền) → `bypass(app)` (một `ContextWrapper` nữa) → `Class.forName(fqn).getMethod(
     * "getInstance", Context).invoke(...)`. Tức ≈4 200 lượt tra reflection + ≈2 800 vật tạm **mỗi phút**, chỉ để
     * lấy lại đúng cái đối tượng vừa lấy một mili-giây trước.
     *
     * Tay cầm device là **process-singleton phía framework** (`getInstance(Context)`), nên nhớ nó là đúng ngữ
     * nghĩa, không phải một mẹo.
     *
     * ## Vì sao lần HỤT được nhớ CÓ HẠN (không nhớ vĩnh viễn)
     * Off-car `getInstance` luôn hụt — nhớ vĩnh viễn thì rẻ. Nhưng trên xe, launcher có thể lên **trước** khi
     * service HAL sẵn sàng (nó là HOME, chạy rất sớm sau khi nổ máy): nhớ "không có" vĩnh viễn ở đúng cửa sổ ấy
     * là mọi ô câm cho tới khi khởi động lại app — một lỗi chức năng đổi lấy một chút CPU. Vì thế lần hụt chỉ
     * được nhớ [MISS_TTL_MS]; sau đó thử lại một lần.
     *
     * ## ⚠ [SOÁT P2-2 · 2026-09-16] Lần TRÚNG cũng có hạn — vì tiền đề "singleton" mới ở mức [SUY]
     * Lý lẽ *"tay cầm là process-singleton phía framework nên nhớ nó không đổi ngữ nghĩa gì"* dựa trên chữ ký
     * `public static synchronized BYDAutoXDevice getInstance(Context)` trong **stub SDK** đã decompile
     * (`../jadx-tmap/sources/android/hardware/bydauto/pm2p5/BYDAutoPM2p5Device.java:47`) — thân hàm ở đó là
     * `throw new RuntimeException("Stub!")`, tức **chưa ai đọc được mã thật**. Nếu tiền đề SAI ở một điểm (ví dụ
     * `getInstance` trả vật mới sau khi service HAL khởi động lại) thì `BydHal.callGetter` — vốn nuốt mọi ngoại
     * lệ và trả `null` — sẽ biến một binder chết thành *"mọi ô hiện —"* **vĩnh viễn**, không có đường phục hồi
     * nào ngoài khởi động lại app. Đúng họ lỗi CLAUDE.md §3 (tin trí nhớ về framework) + §5 (đổi ra ngoài thì
     * phải có đường trả lại chạy được cả khi không ai gọi).
     *
     * Vì thế lần trúng cũng hết hạn sau [HIT_TTL_MS]. Giá: **12 device × 1 lượt resolve / 5 phút ≈ 2,4 lượt
     * reflection/phút** — so với ≈4 200/phút của 1.66 thì nằm dưới mức nhiễu, và nó mua lại một trần phục hồi
     * hữu hạn cho một tiền đề chưa kiểm được trên xe.
     */
    private class Handle(val device: Any, val bornAt: Long)

    private val deviceCache = java.util.concurrent.ConcurrentHashMap<String, Handle>()
    private val deviceMissAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Resolve device BYDAuto qua reflection proven (system-ctx rồi app-ctx, đều bọc bypass). null nếu off-car. */
    private fun device(fqn: String): Any? {
        val now = android.os.SystemClock.elapsedRealtime()
        deviceCache[fqn]?.let { h ->
            // Đồng hồ lùi (`elapsedRealtime` không lùi, nhưng chỗ gọi test/giả thì có) ⇒ coi như hết hạn, không
            // để một hiệu âm khoá tay cầm lại mãi mãi.
            if (now - h.bornAt in 0 until HIT_TTL_MS) return h.device
            deviceCache.remove(fqn, h)
        }
        val missAt = deviceMissAt[fqn]
        if (missAt != null && now - missAt in 0 until MISS_TTL_MS) return null
        val d = runCatching { BydHal.device(fqn, BydHal.systemBypassContext(), BydHal.bypass(app)) }.getOrNull()
        if (d != null) { deviceCache[fqn] = Handle(d, now); deviceMissAt.remove(fqn) } else deviceMissAt[fqn] = now
        return d
    }

    override fun getter(deviceFqn: String, method: String, arg: Int?): String? {
        KachiPerf.add(KachiPerf.Counter.HAL_READ)
        return runCatching { device(deviceFqn)?.let { BydHal.callGetter(it, method, arg) } }.getOrNull()
    }

    /**
     * Ghi named-method + **ghi trộm** câu chữ THẬT vào [HalWriteProbe] cho cầu kiểm thử (spec §9).
     *
     * `callNamedInt` trả `"rc=<v>"` khi gọi được, hoặc chuỗi ngoại lệ ([BydHal.root]) khi ROM thiếu method / HAL
     * chặn — chính chuỗi này bị [parseRc] nuốt (trả null) nên phải chụp TRƯỚC. Off-car (`device()` null) ghi
     * `"off_car"`. Đây là quan sát bị động trên cùng đường ghi, KHÔNG mở đường thứ hai (xem KDoc [HalWriteProbe]).
     */
    override fun namedInt(deviceFqn: String, method: String, args: IntArray): Long? = runCatching {
        val dev = device(deviceFqn) ?: run { HalWriteProbe.record(deviceFqn, method, OFF_CAR); return null }
        val raw = BydHal.callNamedInt(dev, method, *args)
        HalWriteProbe.record(deviceFqn, method, raw)
        parseRc(raw)
    }.getOrNull()

    /**
     * Đọc feature-id qua `get(int[], Class)` 2-arg [ĐO `AbsBYDAutoDevice.java:84`; OpenBYD `CarControlImpl.java:239-240`]
     * — toàn bộ cơ chế (dò method, sentinel `INVAILD_INT`, rút `int=/float=`) nằm ở [BydHal.readFeature] để test
     * off-car; đây chỉ resolve device. null = off-car / device không có get / HAL từ chối / giá trị sentinel.
     */
    override fun featureGet(deviceFqn: String, id: Int): String? = runCatching {
        KachiPerf.add(KachiPerf.Counter.HAL_READ)
        val dev = device(deviceFqn) ?: return null
        BydHal.readFeature(dev, id)
    }.getOrNull()

    /** Ghi feature-id + ghi trộm kết quả vào [HalWriteProbe] (bắt cả `rc=…` lẫn ngoại lệ "no permission …"). */
    override fun featureSet(deviceFqn: String, id: Int, value: Int): Long? = runCatching {
        val label = "0x%08x".format(id)
        val dev = device(deviceFqn) ?: run { HalWriteProbe.record(deviceFqn, label, OFF_CAR); return null }
        val raw = runCatching { "rc=${BydHal.setInt(dev, id, value)}" }.getOrElse { BydHal.root(it) }
        HalWriteProbe.record(deviceFqn, label, raw)
        parseRc(raw)
    }.getOrNull()

    // Car-setting: đường ghi/đọc setting BYD chưa proven trên trim → để null (grab-list §9). Off-car null anyway.
    override fun settingGet(key: String): String? = null
    override fun settingSet(key: String, value: Int): Long? {
        HalWriteProbe.record("", "setting:$key", SETTING_UNSUPPORTED)
        return null
    }

    override fun localGet(target: String, method: String, arg: Int?): String? = runCatching {
        when (target) {
            "AudioManager" -> when (method) {
                "getStreamVolume" -> audio()?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toString()
                else -> null
            }
            // GPS (gps_lat/lon/altitude/heading): HAL BYD KHÔNG có getter toạ độ (`BYDAutoLocationDevice` chỉ setter —
            // RE 2026-09-15). Đường duy nhất là Android LocationManager, nhưng `DeadReckonRetirementTest` PIN "manifest
            // không xin quyền location nào" — quyết định an toàn sau sự cố suýt ghim GPS toàn xe (CLAUDE.md §3). ⇒ KHÔNG
            // wire ở đây; 4 mục GPS = BLOCKED-BY-DESIGN, mở lại là quyết định của owner (ghi ở spec §9).
            else -> null
        }
    }.getOrNull()

    override fun localSet(target: String, method: String, args: IntArray): Boolean {
        val ok = runCatching {
            when (target) {
                "AudioManager" -> when (method) {
                    "setStreamVolume" -> {
                        val am = audio() ?: return@runCatching false
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, args.firstOrNull() ?: 0, 0)
                        true
                    }
                    else -> false
                }
                // AutoContainer = cast/HUD → do SimpleCastRuntime sở hữu, KHÔNG wire ở đây.
                else -> false
            }
        }.getOrDefault(false)
        HalWriteProbe.record(target, method, if (ok) "rc=0" else OFF_CAR)
        return ok
    }

    /**
     * V3 · R11(a) — tên hằng → số, tra trên framework của chính xe. Uỷ quyền thẳng xuống [BydFeatureIds] (cache +
     * log-một-lần nằm ở đó, cùng một chỗ với phép tra device).
     */
    override fun featureIdByName(constName: String): Int? = BydFeatureIds.idByName(constName)

    /** V3 · R11(b) — id → device chứa nó, theo `BYDAutoDeviceFeaturesMap` của xe. `null` ⇒ chỗ gọi lùi về đoán. */
    override fun deviceForFeature(featureId: Int): String? = BydFeatureIds.deviceFqnForFeature(featureId)

    private fun audio(): AudioManager? =
        runCatching { app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    /** BydHal.callNamedInt trả "rc=<v>" (kể cả rc lỗi) hoặc chuỗi lỗi (không có "rc="). → Long? */
    private fun parseRc(s: String): Long? =
        if (s.startsWith("rc=")) s.removePrefix("rc=").trim().toLongOrNull() else null

    private companion object {
        /** [HalWriteProbe] ghi khi `device()` null (emulator / ngoài xe) — cầu kiểm thử đọc thành `hal_line`. */
        const val OFF_CAR = "off_car"

        /** Đường car-setting chưa proven trên trim (grab-list §9). */
        const val SETTING_UNSUPPORTED = "setting_unsupported"

        /**
         * Nhớ một lần resolve device HỤT trong bao lâu (xem KDoc [device]). 30 s: đủ dài để cắt sạch 1 400 lượt
         * reflection/phút off-car, đủ ngắn để HAL lên muộn lúc nổ máy vẫn được bắt trong một nhịp poll chậm.
         */
        const val MISS_TTL_MS = 30_000L

        /**
         * Nhớ một tay cầm ĐÃ resolve được trong bao lâu (xem ⚠ [SOÁT P2-2] ở KDoc [deviceCache]). 5 phút: trần
         * phục hồi hữu hạn nếu tiền đề "singleton" sai, mà chi phí (≈2,4 lượt reflection/phút cho 12 device) vẫn
         * nằm dưới mức nhiễu so với ≈4 200/phút của 1.66.
         */
        const val HIT_TTL_MS = 300_000L
    }
}
