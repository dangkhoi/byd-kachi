package com.byd.clusternav.launcher.voice

import android.app.Application
import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.LangHost
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.voiceAppNames
import java.io.File
import java.io.IOException

/**
 * ═══ TỆP ẢNH CHỤP NGỮ PHÁP — tiến trình CHÍNH ghi, `:wake` đọc ═══════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-rearchitecture-and-remaining.html` §8.2 (A). Dữ liệu + mã hoá ở `:core`
 * ([VoiceGrammarSnapshot]); tệp này chỉ **thi hành I/O**: `filesDir/voice/grammar-snapshot.tsv`.
 *
 * ## Ba tính chất phải giữ
 *  1. **Ghi nguyên tử**: viết vào tệp tạm cùng thư mục rồi `renameTo` (khuôn `VoiceModelStore` staging / `AdbKeys`).
 *     `:wake` đọc đúng lúc đang ghi thì thấy bản cũ trọn vẹn hoặc bản mới trọn vẹn, không bao giờ nửa tệp.
 *  2. **Chỉ tiến trình CHÍNH được ghi** ([isMainProcess]). `:wake`/`:tts` mang cache `SharedPreferences` CŨ của
 *     chúng — ghi từ đó là chép dữ liệu cũ đè lên ảnh chụp mới (đúng cái lỗi tệp này sinh ra để chữa). Bài canh
 *     `VoiceGrammarSnapshotWiringContractTest` khoá `VoiceWakeService.kt` không gọi [write].
 *  3. **Ghi ĐỒNG BỘ trên luồng gọi**, không đẩy sang executor: hai lượt ghi liên tiếp (đổi hồ sơ A rồi B) mà chạy
 *     nền có thể **đảo thứ tự** ⇒ ảnh chụp cuối là A. Tệp ≤ 2 KB (2.91: + tên đã dạy, ≤ 120 × ~50 B ⇒ ≤ ~8 KB), một
 *     `renameTo` — vài ms, cùng mức `apply()` của chính `SharedPreferences` mà chỗ gọi vừa làm.
 *
 * ## Vì sao gắn ở tầng LƯU (`WorkspacePrefs`), không ở UI/ViewModel
 * Hồ sơ đổi từ ≥ 5 đường (Cài đặt · voice · autostart hồ sơ lúc nổ máy · nhập tệp · chuyển cảnh S4). Gắn ở từng UI là
 * để quên một đường; gắn ở **mỗi hàm ghi** của `WorkspacePrefs`/`WorkspacePrefsProfile.kt` thì đường nào cũng qua.
 * Bài canh quét source từng hàm ghi ấy phải gọi [write].
 *
 * Đọc: [read] đi **thẳng tệp** (không `SharedPreferences`, không `WorkspacePrefs`) — đó là toàn bộ lý do tồn tại.
 *
 * FIX286 · VK4 (2.86): tệp mang thêm prefs tươi cho `:wake` ([VoiceWakePrefs] — công tắc wake · phím gán Kachi nghe ·
 * tập hỏi xác nhận · app dẫn đường/nhạc). Ngoài các đường ghi hồ sơ ở trên, setter của các khoá ấy và
 * `VoiceWakeService.sync` gọi [write] qua `VoiceWakePrefsMain.publish`.
 *
 * spec `kachi-i18n-zh-th-ms.html` R6 (2.87): tệp mang thêm tiếng giao diện ĐÃ GIẢI NGHĨA (`uiLang`, qua
 * `LangHost.resolved`) — `:wake` suy ra tiếng giọng nói + tài nguyên của nó. Đường đổi tiếng (`setLangMode`) gọi [write];
 * đổi hồ sơ đi qua `setActiveProfile` (đã gọi) nên tiếng của hồ sơ mới theo cùng lượt.
 */
object VoiceGrammarSnapshotStore {

    private const val TAG = "KachiGrammarSnap"
    internal const val DIR = "voice"
    internal const val FILE = "grammar-snapshot.tsv"
    private const val TMP = ".grammar-snapshot.tmp"

    fun file(ctx: Context): File = File(File(ctx.applicationContext.filesDir, DIR), FILE)

    /**
     * Chụp hồ sơ + sổ địa chỉ **hiện tại** của [prefs] ra tệp. Gọi ở MỌI đường ghi hồ sơ/sổ địa chỉ và một lần khi
     * màn chính mở (để máy vừa nâng cấp có ảnh chụp ngay, không phải chờ lần đổi hồ sơ đầu).
     *
     * Không ném: một tệp phụ cho voice không được làm hỏng lượt đổi hồ sơ của người dùng.
     */
    fun write(prefs: WorkspacePrefs) {
        val ctx = prefs.appCtx
        if (!isMainProcess(ctx)) {
            Log.w(TAG, "bỏ ghi ảnh chụp ngữ pháp: không phải tiến trình chính (cache prefs của tiến trình này có thể cũ)")
            return
        }
        // [SOÁT 2.68 · Pass 2 · P3] Một tên tệp tạm dùng chung cho mọi lượt ghi ⇒ hai luồng của **cùng** tiến trình
        // chính (mọi đường hiện tại đi luồng vẽ, nhưng cầu kiểm thử `KachiTestBridge` gọi từ luồng binder) sẽ
        // `writeText` xen nhau vào một tệp rồi `renameTo` ⇒ công bố một tệp lẫn hai bản. Khoá ở đây là tuần tự hoá
        // đúng cặp "so–ghi": tệp ≤ 2 KB nên lượt chờ là vài ms, và đường ghi nào cũng đã nằm trên luồng vẽ.
        // [Senior review FIX286 Pass 1 · P3] ĐỌC prefs cũng phải nằm TRONG khoá: từ 2.86 có thêm người ghi ở luồng nền
        // (`VoiceEngine.preload` → `VoiceWakeService.sync` → publish). Đọc ngoài khoá thì luồng A đọc state CŨ, luồng B
        // đọc state MỚI và ghi trước, rồi A ghi đè bản cũ lên (body khác `lastBody`) ⇒ `:wake` đọc ảnh chụp lỗi thời
        // tới lượt ghi kế. Tệp ≤ 2 KB, đọc prefs là bộ nhớ ⇒ giữ khoá thêm vài µs.
        synchronized(lock) {
            val snap = VoiceGrammarSnapshot(
                profiles = prefs.profiles(),
                activeProfile = prefs.activeProfile(),
                places = prefs.savedPlaces(),
                writtenAtMs = System.currentTimeMillis(),
                // FIX286 · VK4 — prefs tươi cho `:wake` (chế độ HOLD/WAKE · tập hỏi · app dẫn đường/nhạc), chung tệp. Đọc
                // hỏng ⇒ không mang (`:wake` lùi về đường cũ), không làm hỏng phần hồ sơ/sổ địa chỉ của ảnh chụp.
                wake = runCatching { VoiceWakePrefsMain.collect(ctx) }
                    .onFailure { Log.w(TAG, "không đọc được prefs cho `:wake` — ảnh chụp không mang phần ấy", it) }
                    .getOrDefault(VoiceWakePrefs.EMPTY),
                // i18n R6 — tiếng giao diện ĐÃ GIẢI NGHĨA (cùng phép `LangHost.wrap`): `:wake` không có `Strings.current`
                // và không được gọi `langMode()`. Đọc hỏng ⇒ không mang (`:wake` lùi về VI như trước bản này).
                uiLang = runCatching { LangHost.resolved(prefs) }
                    .onFailure { Log.w(TAG, "không đọc được ngôn ngữ cho `:wake` — ảnh chụp không mang phần ấy", it) }
                    .getOrNull(),
                // 2.91 VOICE-APP-NAMES — tên app tự dạy của hồ sơ đang dùng (đổi hồ sơ đã gọi `write` ⇒ tự mang bộ mới).
                aliases = runCatching { prefs.voiceAppNames() }
                    .onFailure { Log.w(TAG, "không đọc được tên đã dạy cho `:wake` — ảnh chụp không mang phần ấy", it) }
                    .getOrDefault(emptyList()),
            )
            // `PrefsWorkspaceRepository.persist` gọi `setActiveProfile` ở MỌI lượt lưu (đổi ô, đổi chủ đề…) ⇒ nội dung
            // thường không đổi. So với bản tiến trình này vừa ghi (bỏ mốc giờ) — chỉ tiến trình chính ghi, nên bản
            // nhớ ấy là sự thật về tệp; trùng thì không chạm đĩa.
            val body = snap.copy(writtenAtMs = 0L).encode()
            if (body == lastBody) return
            if (writeAtomic(ctx, snap.encode())) lastBody = body   // ghi hỏng ⇒ không nhớ, lượt sau thử lại
        }
    }

    private val lock = Any()

    /** Nội dung (không mốc giờ) của lần ghi gần nhất trong tiến trình này — xem [write]. */
    @Volatile private var lastBody: String? = null

    /**
     * Đọc ảnh chụp **mới nhất trên đĩa** — gọi ở mỗi phiên của `:wake`, KHÔNG cache trong tiến trình (cache là
     * đúng cái bệnh của `SharedPreferences`). Thiếu/hỏng ⇒ [VoiceGrammarSnapshot.EMPTY] + một dòng log.
     */
    fun read(ctx: Context): VoiceGrammarSnapshot {
        val f = file(ctx)
        val raw = try {
            if (f.isFile) f.readText() else null
        } catch (e: IOException) {
            Log.w(TAG, "đọc ảnh chụp ngữ pháp lỗi: ${f.path}", e); null
        } catch (e: SecurityException) {
            Log.w(TAG, "đọc ảnh chụp ngữ pháp bị chặn: ${f.path}", e); null
        }
        val d = VoiceGrammarSnapshot.decode(raw)
        if (d.problem != null) Log.i(TAG, "ảnh chụp ngữ pháp: ${d.problem} (${f.path})")
        return d.snapshot
    }

    /** @return `true` khi tệp đích đã là bản mới (đổi tên xong). */
    private fun writeAtomic(ctx: Context, text: String): Boolean {
        val dest = file(ctx)
        val dir = dest.parentFile ?: return false
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) { Log.w(TAG, "không tạo được ${dir.path}"); return false }
            val tmp = File(dir, TMP)
            tmp.writeText(text)
            if (tmp.renameTo(dest)) true
            else { tmp.delete(); Log.w(TAG, "renameTo thất bại: ${tmp.path} → ${dest.path}"); false }
        } catch (e: IOException) {
            Log.w(TAG, "ghi ảnh chụp ngữ pháp lỗi: ${dest.path}", e); false
        } catch (e: SecurityException) {
            Log.w(TAG, "ghi ảnh chụp ngữ pháp bị chặn: ${dest.path}", e); false
        }
    }

    /**
     * Tiến trình chính = tên tiến trình **bằng** tên gói (các tiến trình phụ mang hậu tố `:wake`/`:tts`). API 28
     * static, minSdk 29 ⇒ luôn có (cùng ghi chú `KachiApplication.isBackgroundVoiceProcess`). Không đọc được ⇒
     * `false` = KHÔNG ghi: bỏ một lượt ghi chỉ làm ảnh chụp trễ tới lượt sau, còn ghi nhầm từ tiến trình cache cũ
     * là dữ liệu sai.
     */
    internal fun isMainProcess(ctx: Context): Boolean =
        runCatching { Application.getProcessName() == ctx.packageName }.getOrDefault(false)
}
