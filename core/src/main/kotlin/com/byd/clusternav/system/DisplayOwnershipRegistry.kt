package com.byd.clusternav.system

/** Chủ sở hữu một display trong mô hình 2-nhánh (Navigation/Launcher vs Cluster Cast). */
enum class DisplayOwner { LAUNCHER, CAST }

/** Kết quả [DisplayOwnershipRegistry.validate]. */
sealed class ValidationResult(val allowed: Boolean) {
    /** Cho phép dispatch. */
    object Allow : ValidationResult(true)

    /** Từ chối (cross-boundary / display không chủ) kèm [reason] để log. */
    data class Reject(val reason: String) : ValidationResult(false)
}

/**
 * Registry SỞ HỮU DISPLAY (thuần JVM :core — KHÔNG android.*, KHÔNG dadb). Cưỡng chế ranh giới 2-nhánh mà
 * two-track split đặt ra:
 *  - [DisplayOwner.LAUNCHER] sở hữu display [MAIN_DISPLAY] (`0`, màn chính) + mọi VirtualDisplay ĐÃ ĐĂNG KÝ (ô app,
 *    màn ảo giữ chỗ — id ≥ 1 bất kỳ, do CHÍNH launcher tạo).
 *  - [DisplayOwner.CAST] sở hữu màn ảo cụm mà đường cast DÒ LIVE được ([setCastDisplay] — `SimpleCastCoordinator`
 *    báo mỗi lượt dò `ClusterDisplayResolver` / khi đóng chiếu). Chưa dò được ⇒ không display nào thuộc CAST.
 *
 * ## B4 · DISPLAY-OWNER-DYNAMIC (2.89) — bỏ hằng `CAST_DISPLAY = 1`
 * Bản cũ ghi cứng `ownerOf(1) = CAST` và `registerVirtualDisplay` đòi `id > 1`. Sai cơ chế:
 *  - [ĐO nguồn AOSP fetch 05/10] id display logic là bộ đếm tăng dần, display phụ ĐẦU TIÊN sau khi khởi động nhận `1`, không
 *    tái dùng trong một lượt khởi động — A10 r47 `DisplayManagerService.java:218` (`mNextNonDefaultDisplayId = DEFAULT_DISPLAY + 1`)
 *    + `:1004,1033-1034` (`assignDisplayIdLocked` → `mNextNonDefaultDisplayId++`); A12 r34 `Layout.java:37,44-45` (gọi từ
 *    `LogicalDisplayMapper.java:357`). Tức `1` thuộc về AI TẠO MÀN PHỤ TRƯỚC — không phải "cụm".
 *  - [ĐO xe 15/09] `perf-oncar-2026-09-26/kachi-logs/usage-1789473430976.log`: `tạo màn ảo kachi-slot-0-… — ô 0 · display 1`
 *    rồi 1,5 s sau `REJECT LAUNCHER Raw @display=1: cross-boundary: LAUNCHER nhắm display 1 thuộc CAST`; cụm thật là
 *    display 2 (fixture `CastDisplayFixtures2026_09_15`). [ĐO máy ảo 05/10] cùng dòng REJECT ×3 ⇒ VietMap không vào được ô 1,
 *    ô đen, `VietMapAutostart` mở nó toàn màn ở display 0.
 * Từ nay (CLAUDE.md §5 sự thật, §7 generic): VD launcher đã đăng ký LUÔN thắng (Kachi tạo nó — sự thật mạnh nhất); CAST =
 * đúng id đường cast dò được; không biết ⇒ `null` ⇒ [validate] TỪ CHỐI (fail-safe deny, như mọi display không chủ).
 * Hai id không thể trùng nhau khi cùng sống (bộ đếm trên); trùng chỉ có thể là lỗi ⇒ log, LAUNCHER giữ quyền.
 *
 * [validate] chặn TRƯỚC khi dispatch: một mutation của LAUNCHER nhắm cụm (hay display không chủ), hoặc của CAST nhắm một
 * VirtualDisplay của launcher, bị REJECT. Mutation không nhắm display ([WindowMutation.NO_DISPLAY], vd force-stop) luôn
 * ALLOW cho cả hai nhánh.
 *
 * Thread-safe: hai tập giữ dưới SNAPSHOT bất biến `@Volatile`, cập nhật copy-on-write dưới lock; đọc (`ownerOf`/`validate`)
 * không cần khoá.
 *
 * @param log kênh log (`:app` — `Kachi/WinDispatch`); chỉ dùng cho ca trùng id (không bao giờ được xảy ra).
 */
class DisplayOwnershipRegistry(private val log: (String) -> Unit = {}) {
    private val lock = Any()

    @Volatile
    private var virtualDisplays: Set<Int> = emptySet()

    /** Id màn ảo cụm đường cast dò LIVE gần nhất; `null` = chưa biết / đã đóng chiếu. */
    @Volatile
    private var castDisplay: Int? = null

    /**
     * Đăng ký VirtualDisplay [id] do launcher tạo (ô app, màn giữ chỗ) → thuộc [DisplayOwner.LAUNCHER]. Nhận MỌI id ≥ 1
     * (sau khởi động nguội ô đầu tiên có thể là display 1 — KDoc lớp). Trùng id cụm đang dò ⇒ log, launcher vẫn giữ.
     */
    fun registerVirtualDisplay(id: Int) {
        require(id > MAIN_DISPLAY) { "virtual display id phải ≥ 1 (0 = màn chính): $id" }
        val clash = synchronized(lock) {
            virtualDisplays = virtualDisplays + id
            castDisplay == id
        }
        if (clash) log("ownership: VD launcher $id TRÙNG id cụm đường cast vừa dò — giữ LAUNCHER (Kachi tạo nó)")
    }

    /** Gỡ đăng ký VirtualDisplay [id] (khi ô đóng / host release). Idempotent. */
    fun unregisterVirtualDisplay(id: Int) {
        synchronized(lock) { virtualDisplays = virtualDisplays - id }
    }

    /** Snapshot các VirtualDisplay đang đăng ký (đọc-only). */
    fun registeredVirtualDisplays(): Set<Int> = virtualDisplays

    /**
     * Đường cast báo id màn ảo cụm vừa DÒ LIVE ([id] ≥ 1), hoặc `null`/`< 1` khi dò hụt / đã đóng chiếu ⇒ không display nào
     * thuộc CAST. Ghi đè lượt trước (một phiên chiếu = một màn ảo cụm). Trùng một VD launcher ⇒ log; [ownerOf] vẫn trả
     * LAUNCHER cho id đó.
     */
    fun setCastDisplay(id: Int?) {
        val next = id?.takeIf { it > MAIN_DISPLAY }
        val clash = synchronized(lock) {
            castDisplay = next
            next != null && next in virtualDisplays
        }
        if (clash) log("ownership: id cụm $next TRÙNG VD launcher đã đăng ký — giữ LAUNCHER (Kachi tạo nó)")
    }

    /** Id cụm đang ghi nhận (đọc-only, cho chẩn đoán/test); `null` = chưa biết. */
    fun castDisplay(): Int? = castDisplay

    /** Chủ của [displayId], hoặc `null` nếu không có chủ đã biết. VD launcher đã đăng ký đứng TRƯỚC id cụm. */
    fun ownerOf(displayId: Int): DisplayOwner? = when {
        displayId == MAIN_DISPLAY -> DisplayOwner.LAUNCHER
        displayId in virtualDisplays -> DisplayOwner.LAUNCHER
        displayId == castDisplay -> DisplayOwner.CAST
        else -> null
    }

    /** `true` nếu [displayId] hiện thuộc [DisplayOwner.CAST] (nguồn cho `AppLocationRegistry.isCastable`). */
    fun isCastDisplay(displayId: Int): Boolean = ownerOf(displayId) == DisplayOwner.CAST

    /**
     * Validate [mutation] do [issuer] phát. ALLOW nếu:
     *  - mutation không nhắm display ([WindowMutation.NO_DISPLAY], vd force-stop), HOẶC
     *  - display đích do CHÍNH [issuer] sở hữu.
     * REJECT (kèm lý do) nếu cross-boundary (đích do bên kia sở hữu) hoặc display không có chủ (fail-safe: deny).
     */
    fun validate(mutation: WindowMutation, issuer: DisplayOwner): ValidationResult {
        val target = mutation.targetDisplayId
        if (target == WindowMutation.NO_DISPLAY) return ValidationResult.Allow
        val owner = ownerOf(target)
            ?: return ValidationResult.Reject(
                "display $target không có chủ đã đăng ký (issuer=$issuer, mutation=${mutation::class.simpleName})",
            )
        return if (owner == issuer) {
            ValidationResult.Allow
        } else {
            ValidationResult.Reject(
                "cross-boundary: $issuer nhắm display $target thuộc $owner (mutation=${mutation::class.simpleName})",
            )
        }
    }

    companion object {
        /** Màn chính launcher. Id cụm KHÔNG có hằng — nó là sự thật dò được ([setCastDisplay]). */
        const val MAIN_DISPLAY: Int = 0
    }
}
