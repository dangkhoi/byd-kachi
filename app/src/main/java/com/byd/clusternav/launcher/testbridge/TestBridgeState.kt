package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.hardware.display.DisplayManager
import com.byd.clusternav.BuildConfig
import com.byd.clusternav.launcher.EffectiveLayout
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.PermissionPreflight
import com.byd.clusternav.launcher.SettingsCatalog
import com.byd.clusternav.launcher.SlotCodec
import com.byd.clusternav.launcher.UnitFormat
import com.byd.clusternav.launcher.voice.VoiceModelStore

/**
 * ═══ T-BRIDGE · ẢNH CHỤP TRẠNG THÁI LAUNCHER, DẠNG JSON ══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` R4 (lệnh `state`). Tách khỏi [KachiTestBridge] vì trần 500 dòng, và
 * vì nó là một việc khác hẳn: receiver lo **cổng + vòng đời**, tệp này lo **đọc**.
 *
 * ## Ràng buộc: CHỈ ĐỌC, và đọc từ ĐÚNG nguồn mà màn hình đang vẽ
 * Mọi trường ở đây hoặc đến từ [HomeUiState] (nguồn sự thật duy nhất), hoặc từ một phép ĐO của hệ thống
 * (`DisplayManager`, `PermissionPreflight`). Không trường nào được suy ra bằng cách đọc lại đĩa: đọc đĩa là dựng
 * một cửa thứ hai, và lượt đo sẽ nói về một giá trị **khác** với thứ người dùng đang thấy — đúng bệnh mà
 * `HomeUiState` sinh ra để chữa.
 *
 * ⚠ Danh sách màn ảo đọc từ `DisplayManager` chứ không từ một cờ RAM: CLAUDE.md §5 — *"cấm quyết định bằng cờ
 * RAM, kiểm bằng sự thật"*. Đây đúng câu hỏi mà cờ RAM đã trả lời sai một lần ([ĐO] `vd=-1` trong bộ log 22/07).
 */
internal object TestBridgeState {

    /**
     * Tiền tố tên màn ảo của ô — **gương** của `VdAppHost` (`"kachi-slot-$slot-$stamp"`).
     *
     * Nó là một bản sao có chủ ý và có lưới an toàn: `TestBridgeSafetyContractTest` đỏ nếu `VdAppHost` không còn
     * đặt tên theo tiền tố này. Không gộp về một hằng dùng chung ngay bây giờ vì `VdAppHost` đang có người khác
     * sửa; việc gộp ghi ở §Open Questions của spec.
     */
    const val VD_NAME_PREFIX = "kachi-slot-"

    /**
     * Toàn bộ ảnh chụp. Chạy trên luồng gọi (receiver, luồng chính) — mọi phép đọc ở đây đều rẻ và **không** mở
     * kênh shell (ràng buộc C4 của vòng kiểm quyền: không mở phiên dadb chỉ để ĐỌC).
     */
    fun build(ctx: Context, hooks: TestBridgeHooks): TestBridgeJson.Raw {
        val s = hooks.state()
        val shell = hooks.shellUsable()
        return TestBridgeJson.Raw(
            TestBridgeJson.obj(
                "app" to TestBridgeJson.Raw(
                    TestBridgeJson.obj(
                        "package" to BuildConfig.APPLICATION_ID,
                        "version_name" to BuildConfig.VERSION_NAME,
                        "version_code" to BuildConfig.VERSION_CODE,
                    ),
                ),
                "profile" to TestBridgeJson.Raw(
                    TestBridgeJson.obj(
                        "active" to s.activeProfile,
                        "all" to TestBridgeJson.Raw(TestBridgeJson.arr(s.profiles)),
                        "boot" to s.bootProfile,
                    ),
                ),
                "layout" to TestBridgeJson.Raw(layout(s)),
                "bars" to TestBridgeJson.Raw(
                    TestBridgeJson.obj(
                        "dock_edge" to s.dock.edge.name,
                        "dock" to TestBridgeJson.Raw(TestBridgeJson.arr(s.dock.enabled)),
                        "chips" to TestBridgeJson.Raw(TestBridgeJson.arr(s.topStrip.ids)),
                    ),
                ),
                "look" to TestBridgeJson.Raw(
                    TestBridgeJson.obj(
                        "theme" to s.themeMode.name,
                        "lang" to s.langMode.name,
                        "units" to s.unitPrefs.encode(),
                        "units_in_use" to TestBridgeJson.Raw(
                            TestBridgeJson.arr(UnitFormat.quantitiesInUse().map { q -> q.name + "=" + s.unitPrefs.unitFor(q) }),
                        ),
                    ),
                ),
                "hosting" to TestBridgeJson.Raw(
                    TestBridgeJson.obj(
                        "embedded" to s.embedded,
                        "shell_usable" to shell,
                        "slot_displays" to TestBridgeJson.Raw(TestBridgeJson.arr(slotDisplays(ctx))),
                    ),
                ),
                "permissions" to TestBridgeJson.Raw(permissions(ctx, shell)),
                "voice_model" to TestBridgeJson.Raw(
                    TestBridgeJson.obj(
                        "ready" to VoiceModelStore.isReady(ctx),
                        "bytes" to VoiceModelStore.sizeOnDisk(ctx),
                    ),
                ),
                "cast_enabled" to castEnabled(ctx),
                "test_mode_minutes_left" to TestBridgeStore.remainingMinutes(ctx),
            ),
        )
    }

    /**
     * Bố cục + nội dung từng ô.
     *
     * Nội dung ô dùng [SlotCodec.encode] — **đúng chuỗi nằm trên đĩa** (`app:<gói>` · `widget:a,b` · `aw:<id>@…`).
     * Dựng một cách mô tả khác cho JSON là dựng bộ mã hoá thứ hai, và lúc hai bộ lệch nhau thì lượt đo sẽ nói về
     * một ô khác với ô thật (đúng bẫy mà KDoc [SlotCodec] mô tả).
     */
    private fun layout(s: HomeUiState): String {
        val count = EffectiveLayout.slotCount(s.workspace.preset, s.customLayout)
        return TestBridgeJson.obj(
            "preset" to s.workspace.preset.name,
            "custom" to (s.customLayout != null),
            "slot_count" to count,
            "slots" to TestBridgeJson.Raw(
                TestBridgeJson.arr(
                    s.slots.mapIndexed { i, c ->
                        TestBridgeJson.Raw(
                            TestBridgeJson.obj("n" to i + 1, "content" to SlotCodec.encode(c)),
                        )
                    },
                ),
            ),
        )
    }

    /**
     * Tên các màn ảo của ô **đang sống thật** — trả lời câu *"ô có bao nhiêu màn ảo"* bằng phép đo của nền tảng.
     *
     * [ĐO] 2026-09-14 (H2·1) `dumpsys display` từng có **4** `kachi-slot-*` cho **2** ô. Đây là chỗ một script
     * kiểm thử đọc được đúng con số ấy mà không phải mở kênh shell.
     */
    private fun slotDisplays(ctx: Context): List<String> {
        val dm = ctx.getSystemService(DisplayManager::class.java) ?: return emptyList()
        return runCatching {
            (dm.displays ?: emptyArray()).map { it.name.orEmpty() }
                .filter { it.startsWith(VD_NAME_PREFIX) }
                .sorted()
        }.getOrDefault(emptyList())
    }

    /** Vòng kiểm quyền — cùng báo cáo mà trang *Hệ thống & quyền* đang vẽ, KHÔNG đọc lại theo đường riêng. */
    private fun permissions(ctx: Context, shellUsable: Boolean): String {
        val rep = PermissionPreflight.check(ctx, shellUsable)
        return TestBridgeJson.obj(
            "all_ok" to rep.allOk,
            "log_line" to rep.logLine(),
            "missing" to TestBridgeJson.Raw(TestBridgeJson.arr(rep.missing.map { it.id })),
            "unknown" to TestBridgeJson.Raw(TestBridgeJson.arr(rep.unknown.map { it.id })),
        )
    }

    /**
     * Công tắc CHÍNH của phiên chiếu cụm.
     *
     * Đọc thẳng tệp prefs của Cluster Cast (chỉ ĐỌC) thay vì dựng một `SimpleCastRuntime`: dựng runtime là khởi
     * động cả một nhánh dịch vụ chỉ để hỏi một `boolean`. Tên tệp + tên khoá lấy từ danh mục ở `:core`, không
     * viết cứng ở đây — xem [prefsSnapshot].
     */
    private fun castEnabled(ctx: Context): Boolean {
        val file = SettingsCatalog.CLUSTERNAV_KEYS[CAST_ENABLED_KEY] ?: return false
        return runCatching {
            ctx.getSharedPreferences(file, Context.MODE_PRIVATE).getBoolean(CAST_ENABLED_KEY, false)
        }.getOrDefault(false)
    }

    private const val CAST_ENABLED_KEY = "cast_enabled"

    /**
     * Toàn bộ khoá/giá trị của MỘT tệp prefs (lệnh `prefs`) — **chỉ đọc**, và **lọc tên nhạy cảm**.
     *
     * ## Vì sao lọc dù hôm nay chưa có khoá nào nhạy cảm
     * Đầu ra của lệnh này đi vào tệp log rồi vào ảnh chụp màn hình rồi vào một issue — tức nó rời khỏi chiếc xe.
     * Danh mục hôm nay không có khoá nào chứa mã/khoá bí mật, nhưng bộ lọc không phải để chữa hiện tại: nó để
     * ngày ai đó thêm một `*_token` vào `clusternav_prefs` thì cầu này **không** in nó ra. Lọc theo TÊN vì đó là
     * thứ duy nhất biết trước được; giá trị bị che thay bằng [REDACTED] để lượt đo vẫn thấy *"khoá có tồn tại"*.
     */
    fun prefsSnapshot(ctx: Context, file: String): TestBridgeJson.Raw {
        val sp = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
        val fields = sp.all.toSortedMap().map { (k, v) ->
            k to if (isSensitive(k)) REDACTED else v
        }
        return TestBridgeJson.Raw(TestBridgeJson.obj(fields))
    }

    /**
     * Tên khoá nghi mang bí mật — so theo **ĐOẠN** (`api_key` ⇒ che), không so chuỗi con.
     *
     * ⚠ So chuỗi con là bản đầu, và nó **sai**: `voicekey_enabled` / `voicekey_bindings` chứa `key` nên bị che
     * sạch — tức đúng hai khoá mà một buổi test phím vô-lăng cần đọc lại là hai khoá không đọc được, và người đo
     * sẽ tưởng chúng chưa được đặt. Che nhầm không "an toàn hơn": nó làm hỏng phép đo một cách im lặng.
     */
    private fun isSensitive(key: String): Boolean =
        key.lowercase().split('_', '.', '-').any { it in SENSITIVE_PARTS }

    private val SENSITIVE_PARTS =
        setOf("key", "keys", "token", "secret", "password", "passwd", "credential", "credentials", "auth")

    /** Giá trị thay thế — ASCII, cùng chữ với mọi công cụ khác, để grep được trong log. */
    private const val REDACTED = "[redacted]"

    /**
     * Tệp prefs mà lệnh `prefs` được phép đọc: danh mục của `:core`, KHÔNG viết cứng.
     *
     * Hai danh mục gộp lại vì cầu kiểm thử nhìn một chiếc xe, không nhìn hai nhánh mã: `PREFS_FILES` là phía
     * launcher, `CLUSTERNAV_PREFS_FILES` là phía ClusterNav. Thêm một tệp ở bất kỳ bên nào thì lệnh này đọc được
     * ngay, không phải sửa ở đây — và một tên lạ vẫn bị từ chối (`bad_prefs_file`).
     */
    val READABLE_PREFS_FILES: Set<String> =
        SettingsCatalog.PREFS_FILES.keys + SettingsCatalog.CLUSTERNAV_PREFS_FILES.keys
}
