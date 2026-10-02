package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.ShellAppLauncher
import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.camera.CameraGuard
import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * Một app tự mở khi nổ máy (F2, owner 01/10: *"Tự khởi động chọn app trong list app đang có trong xe, cho chọn là khởi
 * động nền hay không"*). [background] `true` = **Chạy nền** (R0.3: mở qua một ô sống rồi ra sau màn nhà); `false` =
 * **Mở bình thường** (lên trước, CUỐI chuỗi, qua rào camera — R2.5).
 */
data class TripApp(val pkg: String, val background: Boolean)

/** Cấu hình chuyến của MỘT hồ sơ: hai khoá theo hồ sơ `ignition_apps` + `ignition_music` (spec §4.7). */
data class TripConfig(val apps: List<TripApp> = emptyList(), val music: TripMusic = TripMusic.OFF) {
    /** Có việc gì để chạy không — rỗng thì chuyến vẫn được CLAIM (R2.2: cấu hình giữa chuyến không bắn ngay). */
    val empty: Boolean get() = apps.isEmpty() && !music.mode.plays
}

/**
 * Mã hoá `ignition_apps`: `pkg|B,pkg|N` — cùng khuôn [com.byd.clusternav.launcher.AppShortcutCodec] (đọc dễ dãi, ghi chặt).
 * Tối đa [MAX] app, tối đa MỘT app *Mở bình thường* (lên trước màn nhà là việc của đúng một app — hai app cùng giành
 * màn trước là hai lượt che nhau). Chuỗi không bao giờ chứa tab/xuống dòng (dấu ngăn của `ProfileTransfer`).
 */
object TripAppCodec {
    const val MAX = 6
    private const val BG = "B"
    private const val NORMAL = "N"

    fun encode(items: List<TripApp>): String =
        sanitize(items).joinToString(",") { "${it.pkg}|${if (it.background) BG else NORMAL}" }

    /** Gói lạ ⇒ bỏ; kiểu lạ ⇒ *Chạy nền* (không lên trước màn nhà là chỗ lùi an toàn); trùng ⇒ giữ lần đầu; quá trần ⇒ cắt. */
    fun decode(raw: String?): List<TripApp> {
        if (raw.isNullOrBlank()) return emptyList()
        return sanitize(
            raw.split(',').mapNotNull { item ->
                val cut = item.indexOf('|')
                val pkg = (if (cut < 0) item else item.substring(0, cut)).trim()
                TripApp(pkg, cut < 0 || item.substring(cut + 1).trim() != NORMAL)
            },
        )
    }

    /** Hợp lệ để GHI: gói khớp [ShellAppLauncher.PKG], không trùng, ≤ [MAX], ≤ 1 app *Mở bình thường* (cái đầu thắng). */
    fun sanitize(items: List<TripApp>): List<TripApp> {
        var normalSeen = false
        return items.filter { it.pkg.isNotEmpty() && it.pkg.matches(ShellAppLauncher.PKG) }.distinctBy { it.pkg }.take(MAX)
            .map { if (!it.background && normalSeen) it.copy(background = true) else { if (!it.background) normalSeen = true; it } }
    }

    /** Ngăn kéo vừa chốt [picked] (thứ tự chạm): giữ kiểu cũ của app còn được chọn, app mới = *Chạy nền*. */
    fun apply(cur: List<TripApp>, picked: List<String>): List<TripApp> =
        sanitize(picked.map { p -> cur.firstOrNull { it.pkg == p } ?: TripApp(p, background = true) })

    /** Đổi kiểu của [pkg]; chọn *Mở bình thường* thì app *Mở bình thường* cũ (nếu có) về *Chạy nền* — một lựa chọn duy nhất. */
    fun setMode(cur: List<TripApp>, pkg: String, background: Boolean): List<TripApp> =
        sanitize(cur.map { if (it.pkg == pkg) it.copy(background = background) else if (!background) it.copy(background = true) else it })

    fun remove(cur: List<TripApp>, pkg: String): List<TripApp> = cur.filterNot { it.pkg == pkg }
}

/**
 * ═══ F2/F3 — KẾ HOẠCH CHUYẾN: thứ tự + loại trừ + chờ + "mở bình thường" (thuần, `:core`) ═════════════════════════
 *
 * Spec R2.3–R2.5 · R3.6 · §4.5 (C6). Thứ tự cố định: **mọi app Chạy nền → nhạc → app Mở bình thường** (cuối cùng, vì
 * nó là thứ duy nhất lên TRƯỚC màn nhà; chạy sớm hơn thì các bước sau phải làm việc khi HOME không còn ở trước).
 *
 * Loại trừ ở bước LẬP KẾ HOẠCH chỉ là thứ đo được trong tiến trình (không shell): chính Kachi · chưa cài · app hệ thống
 * (`FLAG_SYSTEM`, R0.6 — tiền lệ CarPlay move-task làm sập surfaceflinger) · app đang nằm trong một ô của bố cục đang
 * hiện (đã mở rồi) · *Mở bình thường* trên đời xe chưa biết màn camera (R2.5). Phần cần shell (app đang chạy / đang chiếu
 * cụm / không có ô sống) do chuỗi chạy ngầm tự đo ngay trước lệnh (`BehindHomeSequence.startBehind`: có task ⇒
 * `ALREADY_RUNNING`, không ô sống ⇒ `NO_STAGE`) — không đoán trước ở đây.
 */
object TripPlan {

    /** R2.3(c) — HOME phải đứng yên ở đỉnh display 0 qua chừng này lần đọc liên tiếp, cách nhau [HOME_READ_GAP_MS]. */
    const val HOME_STEADY_READS = 3
    const val HOME_READ_GAP_MS = 1_000L

    /** R2.5 — *Mở bình thường*: hạn riêng + nhịp thử lại khi đang thấy màn camera. */
    const val NORMAL_DEADLINE_MS = 60_000L
    const val CAMERA_RETRY_MS = 3_000L

    enum class Why { SELF, NOT_INSTALLED, SYSTEM_APP, IN_SLOT, CAMERA_UNKNOWN }

    sealed interface Step {
        data class Background(val pkg: String) : Step
        data class Music(val music: TripMusic) : Step
        data class Normal(val pkg: String) : Step
        data class Skip(val pkg: String, val why: Why) : Step
    }

    /** Sự thật đo trong tiến trình lúc lập kế hoạch. [inSlots] = gói của các ô app ĐANG HIỆN (lớp lưu + lớp tạm). */
    data class Facts(
        val selfPkg: String,
        val installed: Set<String>,
        val system: Set<String>,
        val inSlots: Set<String>,
        val cameraKnown: Boolean,
    )

    fun steps(cfg: TripConfig, f: Facts): List<Step> {
        val bg = cfg.apps.filter { it.background }.map { a -> exclusion(a, f)?.let { Step.Skip(a.pkg, it) } ?: Step.Background(a.pkg) }
        val music = if (cfg.music.mode.plays) listOf(Step.Music(cfg.music)) else emptyList()
        val normal = cfg.apps.filter { !it.background }.map { a -> exclusion(a, f)?.let { Step.Skip(a.pkg, it) } ?: Step.Normal(a.pkg) }
        return bg + music + normal
    }

    private fun exclusion(a: TripApp, f: Facts): Why? = when {
        a.pkg == f.selfPkg -> Why.SELF
        a.pkg !in f.installed -> Why.NOT_INSTALLED
        a.pkg in f.inSlots -> Why.IN_SLOT
        a.background && a.pkg in f.system -> Why.SYSTEM_APP
        !a.background && !f.cameraKnown -> Why.CAMERA_UNKNOWN
        else -> null
    }

    // ── R2.3 — chờ bằng sự thật ────────────────────────────────────────────────────────────────────────────────

    enum class Wait { BOOT, HOME_SCREEN, SLOTS, HOME_STEADY }

    /**
     * Còn phải chờ gì (theo thứ tự), `null` = đủ. [homeBound] = màn chính đã dựng và có kênh; [appSlots] = số ô app của
     * bố cục đang hiện; [liveStages] = số ô app đã thấy app sống (`SlotLiveProbe`); [homeStreak] = số lần đọc liên tiếp
     * thấy HOME của Kachi ở đỉnh display 0 ([homeTopVisible]).
     */
    fun waitFor(bootReady: Boolean, homeBound: Boolean, appSlots: Int, liveStages: Int, homeStreak: Int): Wait? = when {
        !bootReady -> Wait.BOOT
        !homeBound -> Wait.HOME_SCREEN
        appSlots > 0 && liveStages == 0 -> Wait.SLOTS
        homeStreak < HOME_STEADY_READS -> Wait.HOME_STEADY
        else -> null
    }

    /**
     * Stack ĐANG HIỆN trên cùng của display 0 chứa HOME của Kachi — một trong [homeComps] (`pkg/cls` như `am stack list`
     * in: alias `…KachiHome` khi hệ mở màn nhà bằng ý-định HOME, `…KachiHomeActivity` khi task dựng bằng `am start -n`).
     * "Đang hiện", không phải "đầu danh sách": sau lần BYD giết Kachi, stack rỗng của `KachiHomeActivity` có thể nằm trên
     * cùng mà không hiện ([ĐO] fixture `tm1-killed-surfaced`). Đọc hỏng / rỗng ⇒ `false` (không bao giờ coi là yên).
     *
     * Vì sao nhận CẢ HAI dạng [ĐO máy ảo 02/10, E2E `c5a-trip-generic`, fixture `e2e-standard-home`]: sau lượt
     * `MY_PACKAGE_REPLACED` (`KachiAutostart` → `am start -n …KachiHomeActivity`) màn nhà đang hiện là task
     * `…KachiHomeActivity` trong stack `standard` (stack `home` rỗng), sống qua nhiều lần BYD giết Kachi; chỉ nhận alias
     * thì chuyến chờ `HOME_STEADY` (streak = 0) tới hết hạn — mọi chuyến sau một lần OTA.
     *
     * Cửa sổ PIP (stack `pinned`, luôn trên cùng) KHÔNG tính ([BehindHomePlan.topVisibleStackId]): GMaps dẫn đường thu về
     * PIP trên màn nhà thì màn nhà vẫn là thứ đang hiện — tính PIP là streak = 0 tới hết hạn chuyến (cùng hậu quả lỗi E2E
     * ở trên), trong khi K10 ([normalCmd]) nhận "một dòng đang hiện có HOME" và vẫn mở được.
     */
    fun homeTopVisible(entries: List<StackEntry>, homeComps: Collection<String>): Boolean {
        val top = BehindHomePlan.topVisibleStackId(entries, BehindHomePlan.MAIN_DISPLAY) ?: return false
        return entries.any { it.stackId == top && it.comp in homeComps }
    }

    // ── R2.5 — Mở bình thường (K10) ──────────────────────────────────────────────────────────────────────────────

    /**
     * Lệnh mở [comp] toàn màn trên display 0. KHÔNG nháy đơn (rào [CameraGuard] cấm `'` vì chuỗi có thể nằm trong
     * `sh -c '…'`): component đã lọc bằng [BehindHomePlan.safeComponent] (chữ/số/`_`/`.`/`$`/một `/`), `$` của lớp lồng
     * thoát thành `\$` ⇒ shell đọc đúng chữ `$`, không mở rộng biến.
     */
    fun launchCmd(comp: String): String {
        require(BehindHomePlan.safeComponent(comp)) { "component không hợp lệ: $comp" }
        return "am start --display ${BehindHomePlan.MAIN_DISPLAY} --windowingMode 1 -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER -n ${comp.replace("$", "\\$")}"
    }

    /**
     * K10 — một chuỗi: đọc display 0 → camera ⇒ không; HOME của Kachi (một trong [homeComps], xem [homeTopVisible]) đang
     * hiện ⇒ mở; khác (app khác ở trước) ⇒ không.
     */
    fun normalCmd(cameraSig: String, homeComps: List<String>, comp: String): String =
        CameraGuard.unlessCameraOnHome(cameraSig, homeComps, launchCmd(comp))

    enum class Normal { OPENED, CAMERA, OTHER_FRONT, UNREAD }

    /**
     * Kết quả một lượt K10. `am start` in `Starting: Intent` khi nhánh mở đã chạy ⇒ [Normal.OPENED]. Không ⇒ đọc lại
     * [after]: màn camera đang hiện ⇒ [Normal.CAMERA] (thử lại mỗi [CAMERA_RETRY_MS] trong hạn); HOME của Kachi không ở
     * đỉnh ⇒ [Normal.OTHER_FRONT] (app khác vừa lên — không giành màn hình, bỏ); đọc hỏng / HOME vẫn ở đỉnh ⇒
     * [Normal.UNREAD] (thử lại).
     */
    fun normalOutcome(out: String, after: List<StackEntry>, cameraSig: String, homeComps: Collection<String>): Normal {
        if (out.contains("Starting: Intent") && !out.contains("Error")) return Normal.OPENED
        if (after.isEmpty()) return Normal.UNREAD
        if (after.any { it.displayId == BehindHomePlan.MAIN_DISPLAY && it.visible && it.comp.startsWith(cameraSig) }) return Normal.CAMERA
        return if (homeTopVisible(after, homeComps)) Normal.UNREAD else Normal.OTHER_FRONT
    }
}
