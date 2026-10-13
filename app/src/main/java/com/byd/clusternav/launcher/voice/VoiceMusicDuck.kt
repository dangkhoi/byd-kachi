package com.byd.clusternav.launcher.voice

import android.app.ActivityManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
 * thì câu trả lời giữa hai lượt cũng chỉ còn 20 % ⇒ người lái không nghe Kachi nói gì. Nên: hạ khi mic đã thu, trả lại ở
 * `finally` của `VoiceCapture.listenGranted` (chờ có trần, R15) — trước khi câu nghe được đi thi hành/đọc trả lời. Chờ còn
 * giữ thứ tự với lệnh giọng nói đổi âm lượng (`BydHalGateway.localSet setStreamVolume`): lệnh chạy SAU việc trả. Giữa hai lượt
 * nối, nhạc về mức cũ dưới câu đọc (bị `MAY_DUCK` của câu đọc hạ như trước).
 *
 * ## Bảo đảm trả lại (CLAUDE.md §5 — âm lượng là trạng thái ngoài, sống qua tiến trình)
 *  1. Ghi DẤU bền {mức gốc, mức đã đặt, mốc, pid} bằng [AtomicFile] (fsync) **trước** khi hạ; không ghi được dấu ⇒ không hạ.
 *  2. Trả lại khi nhạc vẫn đang nhỏ do Kachi (mức hiện tại ≤ mức đã đặt, có dung sai dốc — 3.1 R16) — người dùng vặn TO
 *     lên thì giữ mức của người dùng ([VoiceMusicDuckPolicy.decide]). Xong thì xoá dấu.
 *  3. Đếm lượt thuê ([leases]) trong tiến trình: lượt chồng lượt chỉ hạ một lần, trả khi lượt cuối nhả.
 *  4. Tiến trình chết giữa lượt (BYD giết Kachi lúc tắt máy) ⇒ lần khởi động sau [recoverAtStartup] (gọi ở
 *     `KachiApplication`), hoặc lượt hạ kế tiếp ở bất kỳ tiến trình nào, gặp dấu của pid đã chết ⇒ trả lại như mục 2.
 *  5. Main và `:wake` đều chạy phiên nghe ⇒ đoạn đọc-quyết-ghi dấu khoá bằng `FileLock` liên tiến trình; dấu của tiến
 *     trình kia còn sống ⇒ để yên (nó đang nghe, nó sẽ tự trả).
 *
 * ## An toàn hỏng (fail-safe) — nguyên tắc: **dấu chỉ bị xoá khi đã biết chắc mức thật**
 *  - Lỗi TRƯỚC khi ghi dấu (không đọc được mức, âm lượng cố định) ⇒ chưa đụng gì ⇒ không dấu, nghe tiếp.
 *  - Lỗi SAU khi ghi dấu (`setStreamVolume`/đọc lại ném — DND, AudioService) ⇒ mơ hồ đã hạ hay chưa ⇒ **giữ dấu**, lúc
 *    trả đọc mức thật rồi `decide`. Đọc lại thấy KHÔNG đổi ⇒ vẫn GIỮ dấu (3.1 R16 — áp trễ). Hạ ra mức giữa dốc/khác
 *    đích ⇒ ghi lại `set`; không ghi lại được ⇒ hoàn tác ngay.
 *  - Trả lại mà AudioService ném ⇒ **giữ dấu**, không hạ chồng lên (sẽ mất mức gốc thật), lượt sau/khởi động thử lại.
 *  - [acquire]/[release]/[recoverAtStartup] không bao giờ ném ra ngoài: ném trong lượt nghe là nuốt lỗi gốc/rò `AudioRecord`;
 *    ném trên luồng nền là Android giết tiến trình.
 * [CHƯA BIẾT] núm âm lượng media của BYD có đi theo `STREAM_MUSIC` không (DiLink có thể đi qua HAL riêng).
 *
 * Chờ từ đánh thức (`VoiceWakeListener`) KHÔNG đi qua đây ⇒ "Hey Kachi" vẫn nghe trong lúc nhạc chạy (owner 09-16).
 *
 * ## 3.1 — sửa hồi quy voice của 3.0 (owner 13/10, spec `kachi-301-cast-steps-dialog-ia.html` §2.4 V1–V4 · R14–R17)
 *  - **R14** [acquire] chỉ đếm lượt + XẾP việc hạ vào [queue] rồi trả ngay (micro giây). Bên gọi (`VoiceCapture`) gọi nó
 *    SAU `startRecording` ⇒ khoá tệp + fsync + binder AudioService không còn nằm giữa bíp và lúc micro thu (V1: mất đầu câu
 *    ⇒ VAD không thấy tiếng ⇒ "Không nghe rõ").
 *  - **R15** hạ và trả chạy trên CÙNG một hàng đợi nối tiếp (một luồng) ⇒ trả không bao giờ chạy trước hạ, kể cả khi hạ
 *    chưa xong. [release] xếp việc trả rồi chờ có trần [RELEASE_WAIT_MS]; quá trần thì đi tiếp (việc trả vẫn chạy sau).
 *  - **R16** không xoá dấu vì "đọc lại chưa đổi" (âm lượng áp trễ/có dốc); dấu giữ tới lượt trả, [VoiceMusicDuckPolicy.decide]
 *    có dung sai dốc (mức hiện tại ≤ mức đã đặt ⇒ trả).
 *  - **R17** mức hạ = 20 % ĐỘ TO (−14 dB) theo đường cong thật `getStreamVolumeDb` ([VoiceMusicDuckPolicy.duckTarget]);
 *    API lỗi ⇒ lùi về luật chỉ số.
 *
 * Chi phí: **1 luồng daemon lười** (`KachiVoiceDuck`, chỉ sinh khi có việc, tự chết sau [IDLE_KEEP_S] s rảnh ⇒ 0 luồng
 * thường trực) · 0 nhịp · 0 listener · 0 lệnh shell; mỗi lượt nghe 3–5 binder `AudioService` + 1 tệp ~30 byte fsync (trên
 * luồng đó, không trên đường micro); lượt hạ ĐẦU TIÊN của tiến trình đọc thêm đường cong dB (≈ 3 binder × số nấc, ghi nhớ
 * trong RAM theo thiết bị ra).
 */
internal object VoiceMusicDuck {

    private const val TAG = "KachiVoiceDuck"
    private const val DIR = "voice"
    private const val FILE = "music-duck.marker"
    private const val LOCK = "music-duck.lock"

    /** Trần chờ của [release] (R15). Quá trần ⇒ lượt nghe đi tiếp; việc trả vẫn chạy sau việc hạ trên [queue]. */
    private const val RELEASE_WAIT_MS = 600L

    /** Luồng của [queue] rảnh quá chừng này thì tự chết (0 luồng thường trực khi không ai nghe). */
    private const val IDLE_KEEP_S = 30L

    /** Đọc lại sau `setStreamVolume`: chờ tối đa chừng này cho âm lượng áp trễ bắt đầu đổi (chỉ chặn [queue], không chặn mic). */
    private const val SETTLE_MS = 250L
    private const val SETTLE_STEP_MS = 25L

    private val lock = Any()

    /**
     * Hàng đợi NỐI TIẾP duy nhất cho mọi việc chạm âm lượng/dấu: hạ · trả · khôi phục lúc khởi động. Lõi 0 + trần 1 luồng +
     * hàng FIFO ⇒ đúng một luồng chạy việc theo thứ tự xếp, luồng chết khi rảnh [IDLE_KEEP_S] s và sinh lại khi có việc.
     */
    private val queue = ThreadPoolExecutor(0, 1, IDLE_KEEP_S, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
        Thread(r, "KachiVoiceDuck").apply { isDaemon = true }
    }

    /** Số lượt nghe đang thuê trong tiến trình này. Chỉ đụng dưới [lock] (luồng gọi) — việc xếp hàng theo đúng thứ tự đổi lượt. */
    private var leases = 0

    /** Tiến trình NÀY đã hạ và đang giữ dấu. Chỉ đụng trên luồng [queue]. Chỉ để biết có cần trả — quyết định trả dựa vào dấu. */
    private var lowered = false

    /** Đường cong dB đã đọc (R17), theo `thiết bị ra × nấc tối đa`. Chỉ đụng trên luồng [queue]. */
    private var curveKey = -1L
    private var curve: FloatArray? = null

    /**
     * Lượt nghe bắt đầu — gọi SAU khi micro đã thu (R14). Luôn ghép với đúng một [release]. **Không bao giờ ném, không
     * chặn**: chỉ đếm lượt và xếp việc hạ vào [queue]. Lỗi ⇒ log, lượt nghe chạy tiếp với nhạc ở mức đang có.
     */
    fun acquire(ctx: Context) {
        val app = ctx.applicationContext
        synchronized(lock) {
            leases++
            if (leases > 1) return
            enqueue("lower") {
                val t0 = System.currentTimeMillis()
                try {
                    withFileLock(app) { lowerLocked(app) }
                } catch (e: RuntimeException) {
                    Log.w(TAG, "hạ nhạc lỗi ngoài dự kiến — nghe tiếp (dấu nếu có sẽ được xử lý ở lượt sau/khởi động)", e)
                }
                Log.i(VoiceEngine.TIMING_TAG, "hạ nhạc ${System.currentTimeMillis() - t0} ms (luồng nền)")
            }
        }
    }

    /**
     * Lượt nghe kết thúc — mọi đường (xong · hết giờ · lỗi · huỷ). Xếp việc trả vào [queue] (SAU việc hạ) rồi chờ tối đa
     * [RELEASE_WAIT_MS]: lệnh giọng nói đổi âm lượng (`BydHalGateway.localSet setStreamVolume`) chạy sau khi hàm này trả về.
     * Không bao giờ ném (chạy trong `finally` của lượt nghe — ném ở đây nuốt mất lỗi gốc của lượt).
     */
    fun release(ctx: Context) {
        val app = ctx.applicationContext
        val t0 = System.currentTimeMillis()
        val job = synchronized(lock) {
            if (leases == 0) { Log.w(TAG, "release không có acquire tương ứng — bỏ qua"); return }
            leases--
            if (leases > 0) return
            enqueue("restore") {
                try {
                    if (!lowered) return@enqueue
                    lowered = false
                    withFileLock(app) {
                        val m = read(app) ?: return@withFileLock
                        if (m.pid == Process.myPid()) settle(app, m, "release")
                    }
                } catch (e: RuntimeException) {
                    Log.w(TAG, "trả nhạc lỗi ngoài dự kiến — dấu còn đó, lượt sau/khởi động sẽ trả", e)
                }
            }
        } ?: return
        try {
            job.get(RELEASE_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Log.w(TAG, "trả nhạc chưa xong sau $RELEASE_WAIT_MS ms — đi tiếp, việc trả vẫn chạy sau việc hạ")
        } catch (e: ExecutionException) {
            Log.w(TAG, "việc trả nhạc ném", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()   // giữ cờ ngắt cho bên gọi (huỷ phiên)
        }
        Log.i(VoiceEngine.TIMING_TAG, "trả nhạc ${System.currentTimeMillis() - t0} ms")
    }

    /**
     * Khởi động tiến trình: dấu do một tiến trình ĐÃ CHẾT để lại ⇒ trả lại theo [VoiceMusicDuckPolicy.decide]. Chạy trên
     * [queue] (binder `ActivityManager` + đọc tệp) ⇒ cùng thứ tự với mọi lượt hạ/trả; không có dấu ⇒ một `File.exists`.
     */
    fun recoverAtStartup(ctx: Context) {
        val app = ctx.applicationContext
        enqueue("recover") {
            // Ném không bắt trên luồng nền = trình xử lý mặc định của Android GIẾT cả tiến trình lúc khởi động ⇒ bắt hết.
            try {
                if (!hasMarker(app)) return@enqueue
                withFileLock(app) { recoverLocked(app, "startup") }
            } catch (e: RuntimeException) {
                Log.w(TAG, "khôi phục dấu hạ nhạc lúc khởi động lỗi — để lượt nghe sau xử lý", e)
            }
        }
    }

    /** Xếp một việc vào [queue]. Không ném: hàng đợi từ chối (không xảy ra — không bao giờ shutdown) ⇒ log, trả `null`. */
    private fun enqueue(what: String, job: () -> Unit): java.util.concurrent.Future<*>? = try {
        queue.submit(Runnable { job() })
    } catch (e: RejectedExecutionException) {
        Log.w(TAG, "không xếp được việc $what", e); null
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
            val min = am.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            tgt = VoiceMusicDuckPolicy.duckTarget(cur, min, volumeCurve(am))   // R17: −14 dB theo đường cong thật
        } catch (e: RuntimeException) {   // `AudioManager` bọc `RemoteException` thành RuntimeException (rethrowFromSystemServer)
            Log.w(TAG, "không đọc được âm lượng — nghe tiếp", e); return
        }
        if (tgt >= cur) return   // đang 0/1 ⇒ không có gì để hạ, không dấu
        val marker = VoiceMusicDuckPolicy.Marker(cur, tgt, System.currentTimeMillis(), Process.myPid())
        if (!write(ctx, marker)) { Log.w(TAG, "không ghi được dấu ⇒ KHÔNG hạ (không có đường trả lại)"); return }
        // Pha 2 — dấu đã bền, bắt đầu đụng âm lượng. Từ đây mọi lỗi đều MƠ HỒ (đã hạ hay chưa?) ⇒ KHÔNG xoá dấu, đánh
        // `lowered` để [release] trả theo SỰ THẬT đọc lại ([VoiceMusicDuckPolicy.decide]). R16: kể cả đọc lại thấy "chưa
        // đổi" cũng GIỮ dấu — âm lượng có thể áp trễ/có dốc; xoá dấu ở đây là để nhạc hạ muộn mà không ai trả (V2a).
        lowered = true
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, tgt, 0)
            val after = settledVolume(am, cur)
            when {
                after == cur -> Log.w(TAG, "setStreamVolume $cur→$tgt chưa đổi sau $SETTLE_MS ms — GIỮ dấu (có thể áp trễ)")
                // Đọc thấy mức GIỮA dốc (hoặc hệ thống kẹp trên đích) ⇒ ghi `set = after`: mọi mức ≤ after lúc trả đều là
                // "vẫn nhỏ do Kachi" ⇒ RESTORE. Không ghi lại được ⇒ dấu cũ (set=tgt) có thể làm kẹt ở `after` ⇒ hoàn tác NGAY.
                after in (tgt + 1) until cur && !write(ctx, marker.copy(set = after)) -> {
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
     * Đọc lại mức sau `setStreamVolume`: còn bằng [before] ⇒ đọc lại mỗi [SETTLE_STEP_MS] tới tối đa [SETTLE_MS] (áp trễ).
     * Dừng ở lần ĐẦU thấy đổi — có thể là mức giữa dốc, đủ dùng: mọi mức sau đó đều ≤ nó (dốc của Kachi chỉ đi xuống).
     * Chỉ chặn luồng [queue]; micro đã thu từ trước (R14).
     */
    private fun settledVolume(am: AudioManager, before: Int): Int {
        var v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        var waited = 0L
        while (v == before && waited < SETTLE_MS) {
            try { Thread.sleep(SETTLE_STEP_MS) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); break }
            waited += SETTLE_STEP_MS
            v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
        return v
    }

    /**
     * R17 — đường cong dB của `STREAM_MUSIC` trên thiết bị ra hiện hành, `[i]` = `getStreamVolumeDb(MUSIC, i, thiết bị)`
     * (API 28, có trong `android.jar` compileSdk — `minSdk` 29). [ĐỌC AOSP `android-10.0.0_r47`
     * `media/java/android/media/AudioManager.java:1053-1071`] hàm ném `IllegalArgumentException` khi chỉ số ngoài
     * `[min, max]` hoặc kiểu thiết bị không hợp lệ, và trả `-inf` khi ≤ `VOLUME_MIN_DB` ⇒ nấc lỗi ghi `NaN`, bên chọn bỏ qua;
     * cả đường cong lỗi ⇒ [VoiceMusicDuckPolicy.duckTarget] lùi về luật chỉ số. Ghi nhớ theo `thiết bị × nấc tối đa`.
     * [CHƯA BIẾT trên ROM BYD] nhạc ra thiết bị nào (loa trong / bus) và HAL có áp đúng đường cong ấy không.
     */
    private fun volumeCurve(am: AudioManager): FloatArray? = try {
        val dev = musicDeviceType(am)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val key = (dev.toLong() shl 32) or max.toLong()
        curve.takeIf { key == curveKey } ?: FloatArray(max + 1) { i ->
            try { am.getStreamVolumeDb(AudioManager.STREAM_MUSIC, i, dev) } catch (e: IllegalArgumentException) { Float.NaN }
        }.also { curve = it; curveKey = key }
    } catch (e: RuntimeException) {
        Log.w(TAG, "không đọc được đường cong dB — lùi về luật chỉ số", e); null
    }

    /** Thiết bị ra của nhạc: loa trong nếu có, không thì thiết bị ra đầu tiên, không có gì ⇒ loa trong (đường cong mặc định). */
    private fun musicDeviceType(am: AudioManager): Int {
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return (outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: outs.firstOrNull())?.type
            ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
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
        // Của chính lượt đang giữ (không xảy ra: việc trả luôn hạ `lowered` trước việc hạ kế — phòng thủ). Review 3.1 Pass 1
        // [P3]: trả `false` = KHÔNG hạ chồng — trả `true` sẽ ghi dấu mới với "gốc" = mức đã hạ ⇒ mất mức gốc thật vĩnh viễn.
        if (m.pid == me && lowered) return false
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
