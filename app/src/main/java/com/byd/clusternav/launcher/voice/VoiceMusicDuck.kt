package com.byd.clusternav.launcher.voice

import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * ═══ 3.0 R4 `VOICE-DUCK-MUSIC` — hạ nhạc xuống 20 % trong lúc NGHE, trả mức cũ khi thôi nghe (thay `VoiceAudioFocus`) ══
 *
 * Owner 11/10 (chốt, một hành vi): *"làm hạ nhạc là được… chốt mức 20% là ok, xong việc thì trả về mức cũ, 1 option thôi"*.
 * Bản 10/10 xin `GAIN_TRANSIENT_EXCLUSIVE` (TẠM DỪNG nhạc) — bỏ. Lượt nghe nay **không xin tiêu điểm nào**: chỉ hạ chỉ số
 * `STREAM_MUSIC` (`flags = 0` ⇒ không hiện thanh âm lượng). Không xin `MAY_DUCK` kèm theo: nền tảng sẽ nhân thêm hệ số hạ
 * ≈ 0,2 lên mức đã hạ (hạ hai lần ⇒ gần câm). Câu ĐỌC trả lời vẫn xin `MAY_DUCK` như cũ (`AndroidTtsSpeaker`/`SherpaTtsSpeaker`).
 *
 * ## Vì sao hạ theo LƯỢT nghe (mic mở → mic đóng), không theo cả PHIÊN
 * [ĐỌC AOSP `android-10.0.0_r47` `media/java/android/media/AudioAttributes.java:1221-1222`] `USAGE_ASSISTANT` → `STREAM_MUSIC`:
 * giọng ĐỌC của Kachi (cả hai máy đọc dùng `USAGE_ASSISTANT`) đi chung núm âm lượng với nhạc. Giữ mức 20 % qua cả phiên
 * thì câu trả lời giữa hai lượt cũng chỉ còn 20 % ⇒ người lái không nghe Kachi nói gì. Nên: hạ khi mic mở, trả lại ĐỒNG BỘ
 * ở `VoiceCapture.closeRecord` — trước khi câu nghe được đi thi hành/đọc trả lời. Đồng bộ còn giữ đúng thứ tự với lệnh
 * giọng nói đổi âm lượng (`BydHalGateway.localSet setStreamVolume`): lệnh luôn chạy SAU khi đã trả mức gốc. Giữa hai lượt
 * nối, nhạc về mức cũ dưới câu đọc (bị `MAY_DUCK` của câu đọc hạ như trước).
 *
 * ## Bảo đảm trả lại (CLAUDE.md §5 — âm lượng là trạng thái ngoài, sống qua tiến trình)
 *  1. Ghi DẤU bền {mức gốc, mức đã đặt, mốc, pid} bằng [AtomicFile] (fsync) **trước** khi hạ; không ghi được dấu ⇒ không hạ.
 *  2. Trả lại CHỈ khi mức hiện tại còn đúng mức Kachi đặt — người dùng đã vặn núm thì giữ mức của người dùng
 *     ([VoiceMusicDuckPolicy.decide]). Xong thì xoá dấu.
 *  3. Đếm lượt thuê ([leases]) trong tiến trình: lượt chồng lượt chỉ hạ một lần, trả khi lượt cuối nhả.
 *  4. Tiến trình chết giữa lượt (BYD giết Kachi lúc tắt máy) ⇒ lần khởi động sau [recoverAtStartup] (gọi ở
 *     `KachiApplication`), hoặc lượt hạ kế tiếp ở bất kỳ tiến trình nào, gặp dấu của pid đã chết ⇒ trả lại như mục 2.
 *  5. Main và `:wake` đều chạy phiên nghe ⇒ đoạn đọc-quyết-ghi dấu khoá bằng `FileLock` liên tiến trình; dấu của tiến
 *     trình kia còn sống ⇒ để yên (nó đang nghe, nó sẽ tự trả).
 *
 * ## An toàn hỏng (fail-safe) — nguyên tắc: **dấu chỉ bị xoá khi đã biết chắc mức thật**
 *  - Lỗi TRƯỚC khi ghi dấu (không đọc được mức, âm lượng cố định) ⇒ chưa đụng gì ⇒ không dấu, nghe tiếp.
 *  - Lỗi SAU khi ghi dấu (`setStreamVolume`/đọc lại ném — DND, AudioService) ⇒ mơ hồ đã hạ hay chưa ⇒ **giữ dấu**, lúc
 *    trả đọc mức thật rồi `decide` (chưa đổi ⇒ chỉ xoá dấu; đã hạ ⇒ trả). Đọc lại thấy KHÔNG đổi ⇒ xoá dấu. Hạ ra mức
 *    khác đích mà không ghi lại được dấu ⇒ hoàn tác ngay.
 *  - Trả lại mà AudioService ném ⇒ **giữ dấu**, không hạ chồng lên (sẽ mất mức gốc thật), lượt sau/khởi động thử lại.
 *  - [acquire]/[release]/[recoverAtStartup] không bao giờ ném ra ngoài: ném trước `try` của lượt nghe là rò `AudioRecord`;
 *    ném trên luồng khởi động là Android giết tiến trình.
 * [CHƯA BIẾT] núm âm lượng media của BYD có đi theo `STREAM_MUSIC` không (DiLink có thể đi qua HAL riêng).
 *
 * Chờ từ đánh thức (`VoiceWakeListener`) KHÔNG đi qua đây ⇒ "Hey Kachi" vẫn nghe trong lúc nhạc chạy (owner 09-16).
 *
 * Chi phí: 0 luồng · 0 nhịp · 0 listener · 0 lệnh shell; mỗi lượt nghe 2–3 binder `AudioService` + 1 tệp ~30 byte fsync.
 */
internal object VoiceMusicDuck {

    private const val TAG = "KachiVoiceDuck"
    private const val DIR = "voice"
    private const val FILE = "music-duck.marker"
    private const val LOCK = "music-duck.lock"

    private val lock = Any()

    /** Số lượt nghe đang thuê trong tiến trình này. Chỉ đụng dưới [lock]. */
    private var leases = 0

    /** Tiến trình NÀY đã hạ và đang giữ dấu. Chỉ đụng dưới [lock]. Chỉ để biết có cần trả — quyết định trả dựa vào dấu. */
    private var lowered = false

    /**
     * Lượt nghe bắt đầu (mic vừa mở). Luôn ghép với đúng một [release] trong `finally`. **Không bao giờ ném**: đứng ngay
     * trước `try` của `VoiceCapture.listenGranted` — ném ở đây là rò `AudioRecord` (phiên sau không mở được micro), tệ hơn
     * mọi lỗi âm lượng. Lỗi ⇒ log, lượt nghe chạy tiếp với nhạc ở mức đang có.
     */
    fun acquire(ctx: Context) {
        val t0 = System.currentTimeMillis()
        synchronized(lock) {
            leases++
            if (leases > 1) return
            try {
                withFileLock(ctx) { lowerLocked(ctx) }
            } catch (e: RuntimeException) {
                Log.w(TAG, "hạ nhạc lỗi ngoài dự kiến — nghe tiếp (dấu nếu có sẽ được xử lý ở lượt sau/khởi động)", e)
            }
        }
        Log.i(VoiceEngine.TIMING_TAG, "hạ nhạc ${System.currentTimeMillis() - t0} ms")
    }

    /**
     * Lượt nghe kết thúc — mọi đường (xong · hết giờ · lỗi · huỷ). Đồng bộ: trả mức gốc TRƯỚC khi câu đi thi hành.
     * Không bao giờ ném (chạy trong `finally` của lượt nghe — ném ở đây nuốt mất lỗi gốc của lượt).
     */
    fun release(ctx: Context) {
        val t0 = System.currentTimeMillis()
        synchronized(lock) {
            if (leases == 0) { Log.w(TAG, "release không có acquire tương ứng — bỏ qua"); return }
            leases--
            if (leases > 0 || !lowered) return
            lowered = false
            try {
                withFileLock(ctx) {
                    val m = read(ctx) ?: return@withFileLock
                    if (m.pid == Process.myPid()) settle(ctx, m, "release")
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "trả nhạc lỗi ngoài dự kiến — dấu còn đó, lượt sau/khởi động sẽ trả", e)
            }
        }
        Log.i(VoiceEngine.TIMING_TAG, "trả nhạc ${System.currentTimeMillis() - t0} ms")
    }

    /**
     * Khởi động tiến trình: dấu do một tiến trình ĐÃ CHẾT để lại ⇒ trả lại theo [VoiceMusicDuckPolicy.decide]. Chạy nền
     * (binder `ActivityManager` + đọc tệp); không có dấu ⇒ một lần `File.exists` rồi thôi.
     */
    fun recoverAtStartup(ctx: Context) {
        val app = ctx.applicationContext
        Thread({
            // Ném không bắt trên luồng này = trình xử lý mặc định của Android GIẾT cả tiến trình lúc khởi động ⇒ bắt hết.
            try {
                if (!hasMarker(app)) return@Thread
                synchronized(lock) {
                    if (leases > 0) return@synchronized   // tiến trình này đã kịp mở lượt nghe ⇒ lượt đó tự xử lý dấu cũ
                    withFileLock(app) { recoverLocked(app, "startup") }
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "khôi phục dấu hạ nhạc lúc khởi động lỗi — để lượt nghe sau xử lý", e)
            }
        }, "KachiDuckRecover").apply { isDaemon = true }.start()
    }

    // ── dưới khoá ────────────────────────────────────────────────────────────────────────────────────────────

    private fun lowerLocked(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        if (!recoverLocked(ctx, "pre-lower")) return
        // Pha 1 — chưa đụng âm lượng: lỗi ở đây ⇒ chắc chắn chưa hạ ⇒ không dấu, nghe tiếp.
        val cur: Int
        val tgt: Int
        try {
            if (am.isVolumeFixed) { Log.i(TAG, "âm lượng cố định trên máy này ⇒ không hạ"); return }
            cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            tgt = VoiceMusicDuckPolicy.target(cur, am.getStreamMinVolume(AudioManager.STREAM_MUSIC))
        } catch (e: RuntimeException) {   // `AudioManager` bọc `RemoteException` thành RuntimeException (rethrowFromSystemServer)
            Log.w(TAG, "không đọc được âm lượng — nghe tiếp", e); return
        }
        if (tgt >= cur) return   // đang 0/1 ⇒ không có gì để hạ, không dấu
        val marker = VoiceMusicDuckPolicy.Marker(cur, tgt, System.currentTimeMillis(), Process.myPid())
        if (!write(ctx, marker)) { Log.w(TAG, "không ghi được dấu ⇒ KHÔNG hạ (không có đường trả lại)"); return }
        // Pha 2 — dấu đã bền, bắt đầu đụng âm lượng. Từ đây mọi lỗi đều MƠ HỒ (đã hạ hay chưa?) ⇒ KHÔNG xoá dấu, đánh
        // `lowered` để [release] trả theo SỰ THẬT đọc lại ([VoiceMusicDuckPolicy.decide]: mức chưa đổi ⇒ KEEP_USER chỉ xoá
        // dấu; đã hạ ⇒ RESTORE). Xoá dấu ở đây là mất đường trả lại duy nhất khi `setStreamVolume` đã ăn mà đọc lại ném.
        lowered = true
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, tgt, 0)
            val after = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            when {
                after == cur -> {
                    lowered = false; clear(ctx)
                    Log.w(TAG, "setStreamVolume $cur→$tgt không có tác dụng (đọc lại $after) — nghe tiếp")
                }
                after != tgt && !write(ctx, marker.copy(set = after)) -> {
                    // Hệ thống kẹp về mức khác đích mà không cập nhật được dấu ⇒ dấu trên đĩa (set=tgt) sẽ không khớp lúc trả
                    // (KEEP_USER) và nhạc kẹt ở `after`. Hoàn tác NGAY trong lúc còn biết mức gốc.
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, cur, 0)
                    lowered = false; clear(ctx)
                    Log.w(TAG, "hạ ra $after (đích $tgt) mà không ghi lại được dấu ⇒ hoàn tác về $cur — nghe tiếp")
                }
                else -> Log.i(TAG, "hạ nhạc khi nghe: $cur → $after (đích $tgt)")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "không được đổi âm lượng (DND/chính sách) — nghe tiếp, dấu giữ để trả theo mức thật", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "AudioService lỗi khi hạ nhạc — nghe tiếp, dấu giữ để trả theo mức thật", e)
        }
    }

    /**
     * Có dấu cũ ⇒ của tiến trình khác còn sống: để yên, trả `false` (đừng hạ chồng). Của tiến trình đã chết (hoặc của
     * chính pid này mà không lượt nào đang giữ — tức sót từ trước): trả/xoá theo chính sách, trả `true`.
     */
    private fun recoverLocked(ctx: Context, why: String): Boolean {
        if (!hasMarker(ctx)) return true
        val m = read(ctx)
        if (m == null) { clear(ctx); Log.w(TAG, "dấu hạ nhạc hỏng ⇒ xoá ($why)"); return true }
        val me = Process.myPid()
        if (VoiceMusicDuckPolicy.ownedElsewhere(m, me, ownerAlive(ctx, m.pid))) {
            Log.i(TAG, "dấu của pid ${m.pid} còn sống (đang nghe) ⇒ để yên ($why)"); return false
        }
        if (m.pid == me && lowered) return true   // của chính lượt đang giữ (không xảy ra khi leases==1, phòng thủ)
        // Dấu sót chưa trả được (AudioService ném) ⇒ KHÔNG hạ chồng: hạ lúc này sẽ ghi dấu mới với "gốc" = mức đã hạ và
        // mất mức gốc thật vĩnh viễn. Để dấu cũ đó, lượt sau/khởi động thử lại.
        return settle(ctx, m, "orphan pid=${m.pid} $why")
    }

    /**
     * Đọc mức thật, quyết theo chính sách, (có thể) trả lại, xoá dấu. Trả `true` khi dấu đã được giải quyết (xoá);
     * `false` khi AudioService ném — khi ấy **giữ dấu** (xoá là mất đường trả lại duy nhất), lượt sau/khởi động thử lại.
     */
    private fun settle(ctx: Context, m: VoiceMusicDuckPolicy.Marker, why: String): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java)
        return try {
            val cur = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
            when (val d = VoiceMusicDuckPolicy.decide(m, cur, System.currentTimeMillis())) {
                VoiceMusicDuckPolicy.Decision.RESTORE -> {
                    am?.setStreamVolume(AudioManager.STREAM_MUSIC, m.original, 0)
                    Log.i(TAG, "trả nhạc ($why): $cur → ${m.original}")
                }
                else -> Log.i(TAG, "không trả nhạc ($why): $d — hiện $cur, đã đặt ${m.set}, gốc ${m.original}")
            }
            clear(ctx); true
        } catch (e: SecurityException) {
            Log.w(TAG, "không được trả âm lượng ($why) — giữ dấu, thử lại lượt sau", e); false
        } catch (e: RuntimeException) {
            Log.w(TAG, "AudioService lỗi khi trả nhạc ($why) — giữ dấu, thử lại lượt sau", e); false   // không làm hỏng luồng gọi
        }
    }

    /** pid còn là một tiến trình của app này không (`runningAppProcesses` chỉ trả tiến trình cùng uid). Lỗi ⇒ coi là chết. */
    private fun ownerAlive(ctx: Context, pid: Int): Boolean = try {
        ctx.getSystemService(ActivityManager::class.java)?.runningAppProcesses?.any { it.pid == pid } == true
    } catch (e: SecurityException) {
        Log.w(TAG, "không đọc được danh sách tiến trình", e); false
    }

    // ── tệp dấu ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun dir(ctx: Context): File = File(ctx.applicationContext.noBackupFilesDir, DIR)
    private fun markerFile(ctx: Context): AtomicFile = AtomicFile(File(dir(ctx), FILE))

    /** Tệp dấu, hoặc bản `.bak` mà `AtomicFile` (Android 10) để lại khi tiến trình chết giữa lượt ghi lại dấu. */
    private fun hasMarker(ctx: Context): Boolean = File(dir(ctx), FILE).exists() || File(dir(ctx), "$FILE.bak").exists()

    /** Khoá liên tiến trình (main ↔ `:wake`). Không mở được khoá ⇒ vẫn chạy [block]: khoá chỉ chống đua hiếm, không gác an toàn. */
    private inline fun withFileLock(ctx: Context, block: () -> Unit) {
        val d = dir(ctx)
        val raf = try {
            if (!d.isDirectory) d.mkdirs()
            RandomAccessFile(File(d, LOCK), "rw")
        } catch (e: IOException) {
            Log.w(TAG, "không mở được khoá tệp — chạy không khoá", e); null
        }
        val fl = try { raf?.channel?.lock() } catch (e: IOException) { Log.w(TAG, "lock() lỗi", e); null }
        try { block() } finally {
            try { fl?.release() } catch (e: IOException) { Log.w(TAG, "nhả khoá lỗi", e) }
            try { raf?.close() } catch (e: IOException) { Log.w(TAG, "đóng khoá lỗi", e) }
        }
    }

    private fun read(ctx: Context): VoiceMusicDuckPolicy.Marker? = try {
        VoiceMusicDuckPolicy.decode(String(markerFile(ctx).readFully(), Charsets.UTF_8))
    } catch (e: IOException) {
        null   // không có tệp/không đọc được ⇒ coi như không dấu (bên gọi đã kiểm `exists` khi cần phân biệt)
    }

    private fun write(ctx: Context, m: VoiceMusicDuckPolicy.Marker): Boolean {
        val f = markerFile(ctx)
        val d = dir(ctx)
        if (!d.isDirectory && !d.mkdirs()) return false
        val out = try { f.startWrite() } catch (e: IOException) { Log.w(TAG, "mở ghi dấu lỗi", e); return false }
        return try {
            out.write(VoiceMusicDuckPolicy.encode(m).toByteArray(Charsets.UTF_8))
            f.finishWrite(out)   // fsync + đổi tên: dấu sống qua mất điện lúc tắt máy
            true
        } catch (e: IOException) {
            f.failWrite(out); Log.w(TAG, "ghi dấu lỗi", e); false
        }
    }

    private fun clear(ctx: Context) = markerFile(ctx).delete()
}
