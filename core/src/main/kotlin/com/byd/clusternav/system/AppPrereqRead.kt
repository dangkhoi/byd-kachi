package com.byd.clusternav.system

import com.byd.clusternav.modules.clustercast.simplified.CastGeometryGuard

/**
 * ═══ 2.89 · B2 VM-PREREQ-TRUTH — đọc SỰ THẬT "app đã được miễn pin / được vẽ nổi chưa" (thuần, `:core`) ═══════════════
 *
 * Vì sao có: hộp thoại *"Hệ thống IVI không hỗ trợ hoạt động này"* mỗi lần VietMap khởi động nguội là
 * `com.byd.carsettings/…unsupport.UnsupportActivity` — đích DUY NHẤT của ý-định `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
 * + `package:` trên ROM 2602030 [ĐO nguồn ROM — spec `kachi-289-field-fixes.html` §B2]; VietMap gọi ý-định đó trong `MainActivity.onCreate`
 * mỗi khi `isIgnoringBatteryOptimizations` = false [ĐO smali 3.4.0 = 3.4.3]. Gỡ app (đổi khoá ký ⇒ phải gỡ) xoá gói
 * khỏi danh sách miễn pin (AOSP r47 `DeviceIdleController.java:586-590`), trong khi Kachi chỉ thêm lại MỘT lần sau cờ
 * `doze_whitelist_applied` ⇒ hộp thoại quay lại mãi. Sửa = quyết bằng sự thật đọc từ máy (CLAUDE.md §5), không bằng cờ.
 *
 * Hai bộ đọc ở đây chỉ PARSE chữ; lệnh chạy ở `:app` (`AppPrereqs`) hoặc qua shell của coordinator chiếu cụm.
 * Mọi bộ đọc trả ba trạng thái: đọc hỏng / định dạng lạ ⇒ [Truth.UNKNOWN] ⇒ bên quyết KHÔNG ghi (không đoán).
 */
enum class Truth { YES, NO, UNKNOWN }

/** Tên gói đưa vào lệnh shell phải là tên gói THẬT (CLAUDE.md §4.1 — gói có thể đến từ hồ sơ nhập ngoài). */
internal fun isShellSafePackage(pkg: String): Boolean = CastGeometryGuard.PACKAGE.matches(pkg)

/**
 * `cmd deviceidle whitelist` (không đối số) — danh sách miễn tối ưu pin.
 *
 * ## Định dạng [ĐO nguồn]
 * AOSP `android-10.0.0_r47` `services/core/java/com/android/server/DeviceIdleController.java:3941-3961` và
 * `android-12.0.0_r34` `apex/jobscheduler/service/java/com/android/server/DeviceIdleController.java:4567-4587` in ĐÚNG ba
 * khối, mỗi dòng `<loại>,<gói>,<appId>`:
 * ```
 * system-excidle,<gói>,<appId>   ← mPowerSaveWhitelistAppsExceptIdle
 * system,<gói>,<appId>           ← mPowerSaveWhitelistApps
 * user,<gói>,<appId>             ← mPowerSaveWhitelistUserApps
 * ```
 * `PowerManager.isIgnoringBatteryOptimizations` → `isPowerSaveWhitelistAppInternal` (r47 `:2278-2283`, r34 `:2772-2777`)
 * chỉ hỏi `system` ∪ `user` ⇒ **`system-excidle` KHÔNG tính** (gói chỉ ở đó vẫn bị VietMap xin miễn ⇒ hộp thoại).
 * Lệnh thêm `+<gói>` in `Added: <gói>` hoặc `Unknown package: <gói>` (r47 `:3924-3929`, r34 `:4551-4555`).
 * Bản nguyên văn từ máy ảo `clusternav10` (A10) nằm ở `docs/diagnostics/vm-prereq-emulator-2026-10-05/` và khoá ở
 * `AppPrereqReadTest`.
 */
object DozeWhitelistRead {

    /** Lệnh đọc — CHỈ ĐỌC, không đối số (toàn bộ danh sách: phải thấy cả `system` lẫn `user`). */
    const val READ = "cmd deviceidle whitelist"

    /** Gói nằm ở danh sách nào (để màn Chẩn đoán nói đúng nguồn, không chỉ có/không). */
    enum class Entry(val exempt: Truth) {
        SYSTEM(Truth.YES),
        USER(Truth.YES),
        /** Chỉ có ở `system-excidle` — KHÔNG được `isIgnoringBatteryOptimizations` tính. */
        EXCEPT_IDLE_ONLY(Truth.NO),
        NONE(Truth.NO),
        /** Đọc hỏng / có dòng lạ mà không thấy gói ⇒ không chứng minh được vắng mặt. */
        UNKNOWN(Truth.UNKNOWN),
    }

    /** Lệnh thêm vào danh sách `user` (ghi `/data/system/deviceidle.xml`, sống qua khởi động lại). */
    fun addCommand(pkg: String): String {
        require(isShellSafePackage(pkg)) { "gói không hợp lệ: '$pkg'" }
        return "$READ +$pkg"
    }

    /**
     * Review 2.89 Pass 2 · vietmap-dock-r1-2 — đường TRẢ LẠI của [addCommand]: gỡ khỏi danh sách `user` [ĐO máy ảo 05/10
     * `vm-prereq-emulator-2026-10-05/02-remove-vietmap.txt` → `Removed: <gói>`; `-` rồi `+` trả danh sách về đúng từng byte].
     */
    fun removeCommand(pkg: String): String {
        require(isShellSafePackage(pkg)) { "gói không hợp lệ: '$pkg'" }
        return "$READ -$pkg"
    }

    /**
     * `null` (phiên hỏng) ⇒ UNKNOWN. Có dòng `system,<gói>,` / `user,<gói>,` ⇒ SYSTEM/USER (bằng chứng dương thắng
     * mọi dòng lạ). Không thấy gói: mọi dòng không rỗng đều đúng khuôn ⇒ EXCEPT_IDLE_ONLY / NONE (kể cả đầu ra rỗng —
     * ba danh sách rỗng là một sự thật hợp lệ); có dòng lạ (lỗi quyền, `Can't find service`) ⇒ UNKNOWN.
     */
    fun entry(output: String?, pkg: String): Entry {
        if (output == null) return Entry.UNKNOWN
        var system = false
        var user = false
        var exceptIdle = false
        var foreign = false
        for (raw in output.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val m = LINE.matchEntire(line)
            if (m == null) { foreign = true; continue }
            if (m.groupValues[2] != pkg) continue
            when (m.groupValues[1]) {
                "system" -> system = true
                "user" -> user = true
                else -> exceptIdle = true
            }
        }
        return when {
            user -> Entry.USER
            system -> Entry.SYSTEM
            foreign -> Entry.UNKNOWN
            exceptIdle -> Entry.EXCEPT_IDLE_ONLY
            else -> Entry.NONE
        }
    }

    /** Kết quả lệnh `+<gói>`: gói chưa cài (ROM từ chối) — để kết quả nói "chưa cài", không phải "hỏng". */
    fun unknownPackage(addOutput: String?, pkg: String): Boolean =
        addOutput?.lineSequence()?.any { it.trim() == "Unknown package: $pkg" } == true

    private val LINE = Regex("""^(system-excidle|system|user),([^,\s]+),-?\d+$""")
}

/**
 * `appops get <gói> SYSTEM_ALERT_WINDOW` — quyền vẽ nổi (bóng VietMap trên cụm).
 *
 * ## Định dạng [ĐO nguồn]
 * AOSP r47 `services/core/java/com/android/server/appop/AppOpsService.java:3444-3505` (r34 `:5500-5590`):
 *  • có mục: `Uid mode: SYSTEM_ALERT_WINDOW: allow` (chế độ theo uid, in TRƯỚC) và/hoặc
 *    `SYSTEM_ALERT_WINDOW: allow; time=+1m2s ago` (chế độ theo gói), tên chế độ = `AppOpsManager.modeToName`
 *    (`allow` · `ignore` · `deny` · `default` · `foreground`);
 *  • không có mục: `No operations.` + `Default mode: default`.
 * Uid thắng gói khi quyết (`checkOperationUnchecked` r47 `:1862-1866`, chú thích `:3452` "Uid mode overrides package
 * mode") ⇒ chế độ HIỆU LỰC = dòng `Uid mode: SYSTEM_ALERT_WINDOW:` nếu có, không thì dòng của gói, không thì
 * `Default mode:`.
 *
 * ⚠ Lỗi AOSP 10 [ĐO máy ảo `clusternav10` 05/10 + nguồn r47 `AppOpsService.java:1057-1063`]: `collectOps` có bộ lọc lấy
 * `uidOps.keyAt(j)` với `j` là chỉ số của MẢNG LỌC, không phải `index` ⇒ khi gói có chế độ uid cho op này, dòng in ra
 * mang tên/giá trị của op uid ĐẦU TIÊN (đo: đặt `--uid … SYSTEM_ALERT_WINDOW ignore` cho Chrome ⇒ `appops get` in
 * `Uid mode: COARSE_LOCATION: foreground`). r34 đã sửa (`collectUidOps` `:2222-2229` dùng `code`). Vậy dòng `Uid mode:`
 * mang op KHÁC = "có chế độ uid nhưng không đọc được giá trị" ⇒ hiệu lực KHÔNG BIẾT (không tin dòng của gói).
 * Owner 05/10: sau khi cài lại VietMap phải cấp tay lại quyền này — cùng bệnh cờ một-lần.
 */
object OverlayOpRead {

    const val OP = "SYSTEM_ALERT_WINDOW"
    const val ALLOW = "allow"

    fun readCommand(pkg: String): String {
        require(isShellSafePackage(pkg)) { "gói không hợp lệ: '$pkg'" }
        return "appops get $pkg $OP"
    }

    fun allowCommand(pkg: String): String {
        require(isShellSafePackage(pkg)) { "gói không hợp lệ: '$pkg'" }
        return "appops set $pkg $OP $ALLOW"
    }

    /**
     * Review 2.89 Pass 2 · vietmap-dock-r1-2 — đường TRẢ LẠI của [allowCommand]: về chế độ `default` (chế độ khi gói không có
     * mục — `No operations.` + `Default mode: default`, fixture `09-appops-maps-no-operations.txt`). Chỉ dùng cho gói mà CHÍNH
     * Kachi đã đổi từ "không allow" (dấu bền ở `AppPrereqPlan.Marks`); chế độ trước đó không lưu ⇒ trả về `default`.
     */
    fun defaultCommand(pkg: String): String {
        require(isShellSafePackage(pkg)) { "gói không hợp lệ: '$pkg'" }
        return "appops set $pkg $OP default"
    }

    /** Chế độ hiệu lực (`allow`/`ignore`/`deny`/`default`/`foreground`); `null` = không đọc được (lỗi / định dạng lạ). */
    fun effectiveMode(output: String?): String? {
        if (output.isNullOrBlank()) return null
        var uid: String? = null
        var uidMisreported = false
        var pkg: String? = null
        var noOps = false
        var default: String? = null
        for (raw in output.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith(UID_PREFIX) -> {
                    val m = modeOf(line.removePrefix(UID_PREFIX))
                    if (m == null) uidMisreported = true else uid = uid ?: m
                }
                line.startsWith("$OP: ") -> pkg = pkg ?: modeOf(line)
                line == "No operations." -> noOps = true
                line.startsWith(DEFAULT_PREFIX) -> default = line.removePrefix(DEFAULT_PREFIX).trim().takeIf(MODE::matches)
            }
        }
        if (uid != null) return uid
        if (uidMisreported) return null   // lỗi r47: có chế độ uid mà in nhầm op ⇒ không biết hiệu lực
        return pkg ?: default?.takeIf { noOps }
    }

    /** `allow` ⇒ YES · chế độ khác ⇒ NO · không đọc được ⇒ UNKNOWN. */
    fun allowed(mode: String?): Truth = when (mode) {
        null -> Truth.UNKNOWN
        ALLOW -> Truth.YES
        else -> Truth.NO
    }

    private const val UID_PREFIX = "Uid mode: "
    private const val DEFAULT_PREFIX = "Default mode: "
    private val MODE = Regex("[a-z]+")

    /** `SYSTEM_ALERT_WINDOW: allow; time=…` ⇒ `allow`. */
    private fun modeOf(entry: String): String? {
        if (!entry.startsWith("$OP: ")) return null
        return entry.removePrefix("$OP: ").substringBefore(';').substringBefore(' ').trim().takeIf(MODE::matches)
    }
}
