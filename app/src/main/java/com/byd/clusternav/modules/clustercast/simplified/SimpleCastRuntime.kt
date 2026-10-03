package com.byd.clusternav.modules.clustercast.simplified

import android.content.Context
import android.content.SharedPreferences
import com.byd.clusternav.system.ShellTransport
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastShell
import com.byd.clusternav.modules.clustercast.simplified.ShellResult
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastPrefs
import com.byd.clusternav.modules.clustercast.simplified.DisplayConfig
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.ProjectionManager
import com.byd.clusternav.modules.clustercast.simplified.DisplayConfigurator
import com.byd.clusternav.modules.clustercast.simplified.AppMover
import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral
import com.byd.clusternav.modules.clustercast.simplified.CastGeometryGuard

/**
 * Android-side runtime for the simplified Cluster Cast coordinator.
 *
 * Bridges the pure-Kotlin core coordinator to the Android platform:
 * - Shell execution via dadb (localhost:5555, same as V2)
 * - Preferences via SharedPreferences
 * - Lifecycle tied to app process
 *
 * Process-singleton: all activities/services share one instance.
 */
object SimpleCastRuntime {

    @Volatile private var instance: SimpleCastCoordinator? = null

    /** Get or create the process-wide coordinator. Thread-safe. */
    fun coordinator(context: Context): SimpleCastCoordinator {
        return instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
    }

    private fun create(app: Context): SimpleCastCoordinator {
        android.util.Log.i("SimpleCast", "Creating SimpleCastCoordinator")
        val shell = DadbSimpleCastShell(app)
        val prefs = SharedPrefsSimpleCastPrefs(app)
        // V-CLUSTER · VC-R7 — CHỐT bản chờ `cast_enabled_pending` (lựa chọn Cast của hồ sơ, ghi lúc đổi hồ sơ) vào khoá
        // sống TRƯỚC khi dựng coordinator: mọi chỗ đọc `castEnabled()` đều đi qua `coordinator(...).prefs` (spec K1), nên
        // chốt ở đây là mọi chỗ đọc của tiến trình thấy CÙNG một giá trị ngay từ đầu. Chỉ prefs (commit đồng bộ), không
        // shell ⇒ an toàn cả khi luồng gọi đầu tiên là luồng chính. [ĐO 09-29] BYD giết Kachi mỗi lần tắt máy ⇒ trên thực
        // tế đây là lần nổ máy kế.
        val atStart = prefs.commitCastEnabledPending()
        val projection = ProjectionManager(shell)
        val configurator = DisplayConfigurator(shell) { message -> android.util.Log.w("SimpleCast", "DisplayConfigurator: $message") }
        val mover = AppMover(
            shell = shell,
            log = { message -> android.util.Log.i("SimpleCast", "AppMover: $message") },
        )
        // Cluster display id — SEED only (X2). KHÔNG dò shell ở đây: create() chạy lười trên luồng GỌI ĐẦU
        // TIÊN (có thể là main thread, vd bridge.castEnabled()), nên shell dadb ở đây = NetworkOnMainThread/ANR.
        // Seed = saved (prefs) hoặc 1 (phao) — CHỈ để đọc/geometry. R1 (spec kachi-hal187-cast-remediation):
        // KHÔNG lệnh đặt nào (`am start --display`, `wm … -d`, dọn VD) được dùng seed; coordinator chỉ đặt theo
        // id dò LIVE sau khi mở projection ([SimpleCastCoordinator.openProjection] → ClusterDisplayResolver), dò
        // hụt thì không đặt. [ĐO] 2026-09-15: seed 1 = kachi-slot-0 (VD của chính launcher), cụm thật = 2.
        val savedDisplayId = prefs.lastDisplayId()
        val displayId = savedDisplayId ?: 1
        val displaySource = if (savedDisplayId != null) "saved" else "seed(1)"
        android.util.Log.i(
            "SimpleCast",
            "Cluster display seed = $displayId (source=$displaySource) — NOT used for placement; " +
                "live-resolved after projection open",
        )
        val coordinator = SimpleCastCoordinator(projection, configurator, mover, prefs, shell, displayId, selfPackage = com.byd.clusternav.BuildConfig.APPLICATION_ID)
        // Chốt BẬT→TẮT ⇒ tiến trình trước có thể đã để projection mở trên cụm: không dọn là cụm HAI CHỦ (HUD thấy TẮT nên
        // ghi op 39 trong khi mặt chiếu cũ vẫn đứng). Xếp lên executor của coordinator (shell ở nền, không ở luồng gọi).
        // Chốt TẮT→BẬT thì không làm gì thêm: các đường khởi động sẵn có tự đọc BẬT và mở chiếu (RebindReceiver,
        // KachiHomeWiring.ensureCastBubble) như với một người đang bật Cast.
        if (atStart is CastEnableDeferral.AtStart.Commit && atStart.closeOrphan) coordinator.closeOrphanProjection()
        return coordinator
    }

    /** Shutdown the coordinator. Call from Application.onTerminate or process exit. */
    fun shutdown() {
        instance?.shutdown()
        instance = null
    }
}

/**
 * SimpleCastShell implementation backed by the single-owner [ShellTransport].
 *
 * B1: was a FRESH `Dadb.create(...).use { }` per command; now every command goes through the one shared
 * connection, serialized on the transport's single owner thread (so cast commands can never interleave their
 * streams with the launcher's window commands). The transport retries once on failure, which self-heals a
 * stale connection exactly like the old fresh-conn-per-command path did. Command strings are unchanged.
 */
private class DadbSimpleCastShell(private val app: Context) : SimpleCastShell {

    override fun execute(command: String): ShellResult {
        return try {
            android.util.Log.i("SimpleCast", "shell: $command")
            val result = ShellTransport.get(app).exec(command)
            val shellResult = ShellResult(
                exitCode = result.exitCode,
                stdout = result.stdout,
                stderr = result.stderr,
            )
            if (isPlacementEvidenceCommand(command)) {
                logPlacementEvidence(command, shellResult)
            } else {
                android.util.Log.i("SimpleCast", "shell OK: exit=${result.exitCode}")
            }
            shellResult
        } catch (e: Exception) {
            val shellResult = ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: e.javaClass.simpleName,
            )
            if (isPlacementEvidenceCommand(command)) {
                logPlacementEvidence(command, shellResult)
            }
            android.util.Log.e("SimpleCast", "shell FAIL: command=$command error=${e.message}", e)
            shellResult
        }
    }

    private fun logPlacementEvidence(command: String, result: ShellResult) {
        val evidenceId = EVIDENCE_SEQUENCE.incrementAndGet()
        val payload = "command=${encodeLogValue(command)} exit=${result.exitCode} " +
            "stdout=${encodeLogValue(result.stdout)} stderr=${encodeLogValue(result.stderr)}"
        val chunks = payload.chunked(EVIDENCE_CHUNK_CHARS)
        android.util.Log.i(
            "SimpleCast",
            "shell evidence id=$evidenceId: chars=${payload.length} " +
                "utf8Bytes=${payload.toByteArray(Charsets.UTF_8).size} chunks=${chunks.size}",
        )
        chunks.forEachIndexed { index, chunk ->
            android.util.Log.i(
                "SimpleCast",
                "shell evidence id=$evidenceId ${index + 1}/${chunks.size}: $chunk",
            )
        }
    }

    private fun isPlacementEvidenceCommand(command: String): Boolean =
        command.startsWith("am start ") ||
            command.startsWith("am task resize ") ||
            command.startsWith("wm size -d ")

    private fun encodeLogValue(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20 || character.code > 0x7e) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }

    private companion object {
        // 768 ASCII code units plus the record prefix remain below Android's log-entry limit.
        const val EVIDENCE_CHUNK_CHARS = 768
        val EVIDENCE_SEQUENCE = java.util.concurrent.atomic.AtomicLong()
    }
}

/**
 * SharedPreferences-based implementation of SimpleCastPrefs.
 */
private class SharedPrefsSimpleCastPrefs(context: Context) : SimpleCastPrefs {
    private val sp: SharedPreferences =
        context.getSharedPreferences("simple_cast_prefs", Context.MODE_PRIVATE)

    override fun displayConfigFor(pkg: String): DisplayConfig? = displayConfigFor(pkg, CastProfile.FULL)

    /**
     * V-CLUSTER · VC-R4 lớp 3 — đọc bản ghi hình học QUA BỘ KIỂM [CastGeometryGuard.readConfig], không bao giờ ném.
     *
     * Trước đây `bounds` đọc bằng `toInt()` trần (chuỗi hỏng ⇒ `NumberFormatException` trong dịch vụ đang chạy) và ba
     * chuỗi còn lại đi NGUYÊN VĂN vào `wm size/overscan/density`. Từ V-CLUSTER họ `config_*` theo hồ sơ ⇒ đi qua tệp
     * xuất/nhập ⇒ là dữ liệu người khác gửi. Đọc bằng `all[...] as? String` (không `getString`): một giá trị sai KIỂU
     * trên đĩa là `ClassCastException` với `getString`, còn ở đây nó chỉ là "vắng".
     */
    override fun displayConfigFor(pkg: String, profile: CastProfile): DisplayConfig? {
        val key = profileKey(pkg, profile)
        val all = sp.all
        return CastGeometryGuard.readConfig(
            size = all["config_size_$key"] as? String,
            overscan = all["config_overscan_$key"] as? String,
            density = all["config_density_$key"] as? String,
            bounds = all["config_bounds_$key"] as? String,
        ) { msg -> android.util.Log.w("SimpleCast", "displayConfigFor($key): $msg") }
    }

    override fun saveDisplayConfig(pkg: String, config: DisplayConfig) =
        saveDisplayConfig(pkg, CastProfile.FULL, config)

    /**
     * Ghi bản ghi hình học — CHUẨN HOÁ trước ([CastGeometryGuard.sanitizeForSave]): lưu đúng thứ đã áp (DPI ngoài dải ⇒
     * lệnh đã chạy là `wm density reset` ⇒ lưu `"reset"`, không lưu `"700"`), và không bao giờ lưu một chuỗi mà lượt đọc
     * sau sẽ phải bỏ. `wmSize` hỏng ⇒ KHÔNG ghi (thiếu kích thước thì cả bản ghi vô nghĩa) + log.
     */
    override fun saveDisplayConfig(pkg: String, profile: CastProfile, config: DisplayConfig) {
        val key = profileKey(pkg, profile)
        val clean = CastGeometryGuard.sanitizeForSave(config) ?: run {
            android.util.Log.w("SimpleCast", "saveDisplayConfig($key): bỏ — wmSize hỏng '${config.wmSize.take(40)}'")
            return
        }
        sp.edit()
            .putString("config_size_$key", clean.wmSize)
            .putString("config_overscan_$key", clean.overscan)
            .putString("config_density_$key", clean.density)
            .putString("config_bounds_$key", clean.bounds?.toString())
            .apply()
    }

    /**
     * Prefs key namespace for a profile. [CastProfile.FULL] keeps the legacy no-suffix key
     * (`config_<field>_<pkg>`) so previously-saved full configs survive; every other profile
     * appends `__<profile.key>` (e.g. `config_size_<pkg>__L30`) — distinct, non-colliding, and
     * byte-identical to the predecessor's keys for the {50,30,70} set (R3/R4/R8 backward compat).
     */
    private fun profileKey(pkg: String, profile: CastProfile): String =
        if (profile.isFull) pkg else "${pkg}__${profile.key}"

    override fun lastDisplayId(): Int? {
        val v = sp.getInt("last_display_id", -1)
        return if (v >= 0) v else null
    }

    override fun saveLastDisplayId(id: Int) {
        sp.edit().putInt("last_display_id", id).apply()
    }

    // Autostart
    override fun autoStartPackage(): String? = sp.getString("autostart_package", null)

    override fun setAutoStartPackage(pkg: String?) {
        sp.edit().putString("autostart_package", pkg).apply()
    }

    override fun autoStartEnabled(): Boolean = sp.getBoolean("autostart_enabled", false)

    override fun setAutoStartEnabled(enabled: Boolean) {
        sp.edit().putBoolean("autostart_enabled", enabled).apply()
    }

    // Split ratio
    override fun splitRatioLeftPercent(): Int = sp.getInt("split_ratio_left_pct", 50)

    override fun setSplitRatioLeftPercent(pct: Int) {
        sp.edit().putInt("split_ratio_left_pct", pct).apply()
    }

    // One-time setup flags
    override fun dozeWhitelistApplied(): Boolean = sp.getBoolean("doze_whitelist_applied", false)

    override fun setDozeWhitelistApplied(applied: Boolean) {
        sp.edit().putBoolean("doze_whitelist_applied", applied).apply()
    }

    // Autostart split
    override fun autoStartLeftPackage(): String? = sp.getString("autostart_left_package", null)

    override fun setAutoStartLeftPackage(pkg: String?) {
        sp.edit().putString("autostart_left_package", pkg).apply()
    }

    override fun autoStartRightPackage(): String? = sp.getString("autostart_right_package", null)

    override fun setAutoStartRightPackage(pkg: String?) {
        sp.edit().putString("autostart_right_package", pkg).apply()
    }

    override fun autoStartSplitEnabled(): Boolean = sp.getBoolean("autostart_split_enabled", false)

    override fun setAutoStartSplitEnabled(enabled: Boolean) {
        sp.edit().putBoolean("autostart_split_enabled", enabled).apply()
    }

    // Master enable — default FALSE (owner 2026-08-11): nav-only is the common case, so a fresh
    // install keeps the cluster native (no projection/curve/black surface) and navigation shows on
    // the cluster immediately. Users who want to cast apps turn Cast on explicitly (persisted).
    override fun castEnabled(): Boolean = sp.getBoolean("cast_enabled", false)

    /**
     * Cú bật/tắt TƯỜNG MINH (công tắc Cài đặt, nút *Áp ngay*, lệnh giọng nói — mọi đường đều qua cầu `setCastEnabled`).
     *
     * V-CLUSTER · VC-R7: ý người lái vừa bày tỏ THẮNG mọi bản chờ của lượt đổi hồ sơ ⇒ xoá khoá chờ trong CÙNG lượt
     * ghi. Ghim ở tầng THI HÀNH này (không ở tầng UI, CLAUDE.md §5): quên xoá thì lần nổ máy sau bản chờ cũ chốt đè
     * lên đúng lựa chọn người lái vừa bấm.
     */
    override fun setCastEnabled(enabled: Boolean) {
        sp.edit().putBoolean("cast_enabled", enabled).remove(CastEnableDeferral.PENDING_KEY).apply()
    }

    override fun castEnabledPending(): Boolean? = sp.all[CastEnableDeferral.PENDING_KEY] as? Boolean

    /**
     * Chốt bản chờ (V-CLUSTER · VC-R7): quyết định thuần ở [CastEnableDeferral.onProcessStart]; ghi khoá sống + xoá khoá
     * chờ trong MỘT `commit()` đồng bộ — lượt đọc `castEnabled()` ngay sau đó (cùng tiến trình, cùng lượt dựng) phải
     * thấy giá trị đã chốt. Không shell ⇒ an toàn cả khi luồng gọi là luồng chính.
     */
    override fun commitCastEnabledPending(): CastEnableDeferral.AtStart {
        val all = sp.all
        val decision = CastEnableDeferral.onProcessStart(all[CastEnableDeferral.LIVE_KEY], all[CastEnableDeferral.PENDING_KEY])
        when (decision) {
            // FIX286 · PI5 — mốc bền (chốt gì · lúc nào · bản nào) trong CÙNG lượt `commit()`: màn Chiếu cụm nói được
            // "lần khởi động lúc … đã áp lựa chọn của hồ sơ" kể cả khi log của tiến trình dựng lại sau tắt máy đã mất.
            is CastEnableDeferral.AtStart.Commit -> sp.edit().putBoolean("cast_enabled", decision.on)
                .putString(
                    CastEnableDeferral.COMMIT_MARK_KEY,
                    CastEnableDeferral.CommitMark(
                        decision.on, System.currentTimeMillis(), com.byd.clusternav.BuildConfig.VERSION_CODE,
                        com.byd.clusternav.BuildConfig.VERSION_NAME,
                    ).encode(),
                )
                .remove(CastEnableDeferral.PENDING_KEY).commit()
            is CastEnableDeferral.AtStart.Discard -> {
                android.util.Log.w("SimpleCast", "cast_enabled_pending: ${decision.reason} — xoá bản chờ")
                sp.edit().remove(CastEnableDeferral.PENDING_KEY).commit()
            }
            CastEnableDeferral.AtStart.NoPending -> Unit
        }
        if (decision != CastEnableDeferral.AtStart.NoPending) android.util.Log.i("SimpleCast", "cast_enabled chốt lúc khởi động: $decision")
        return decision
    }

    override fun castCommitMark(): CastEnableDeferral.CommitMark? =
        CastEnableDeferral.CommitMark.decode(sp.all[CastEnableDeferral.COMMIT_MARK_KEY])

    // WP6 · R6.1 — HIỆN nút nổi hay không. Mặc định TRUE, **ngược** với `cast_enabled` ngay trên, và có lý do:
    // `cast_enabled` mặc định TẮT vì bật nó là đi giành mặt cụm trước mặt người lái (một việc ngoài app), còn khoá
    // này chỉ quyết một cửa sổ nhỏ BÊN TRONG phiên chiếu mà owner vừa tự bật — ẩn nó mặc định thì người vừa bật
    // Cast không còn lối vào nào để chiếu, tức tính năng trông như hỏng. Ba nhánh hậu quả ở [BubblePresence].
    override fun bubbleVisible(): Boolean = sp.getBoolean("cast_bubble_visible", true)

    override fun setBubbleVisible(visible: Boolean) {
        sp.edit().putBoolean("cast_bubble_visible", visible).apply()
    }
}
