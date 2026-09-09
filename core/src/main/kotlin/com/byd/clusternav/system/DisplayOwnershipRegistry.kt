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
 *  - [DisplayOwner.LAUNCHER] sở hữu display [MAIN_DISPLAY] (`0`, màn chính) + mọi VirtualDisplay ĐÃ ĐĂNG KÝ
 *    (ô app, id ≥ 2 do launcher host tạo).
 *  - [DisplayOwner.CAST] sở hữu display [CAST_DISPLAY] (`1`, cụm tài xế).
 *
 * [validate] chặn TRƯỚC khi dispatch: một mutation của LAUNCHER nhắm cụm (display 1), hay của CAST nhắm một
 * VirtualDisplay của launcher, bị REJECT — đúng lớp lỗi mà two-track split cấm. Mutation không nhắm display
 * ([WindowMutation.NO_DISPLAY], vd force-stop) luôn ALLOW cho cả hai nhánh.
 *
 * Thread-safe: tập VirtualDisplay giữ dưới SNAPSHOT bất biến `@Volatile`, cập nhật copy-on-write dưới lock;
 * đọc (`ownerOf`/`validate`) không cần khoá.
 */
class DisplayOwnershipRegistry {
    private val lock = Any()

    @Volatile
    private var virtualDisplays: Set<Int> = emptySet()

    /** Đăng ký VirtualDisplay [id] (≥ 2) do launcher tạo để render app trong ô → thuộc [DisplayOwner.LAUNCHER]. */
    fun registerVirtualDisplay(id: Int) {
        require(id > CAST_DISPLAY) { "virtual display id phải ≥ 2 (0 = màn chính, 1 = cụm): $id" }
        synchronized(lock) { virtualDisplays = virtualDisplays + id }
    }

    /** Gỡ đăng ký VirtualDisplay [id] (khi ô đóng / host release). Idempotent. */
    fun unregisterVirtualDisplay(id: Int) {
        synchronized(lock) { virtualDisplays = virtualDisplays - id }
    }

    /** Snapshot các VirtualDisplay đang đăng ký (đọc-only). */
    fun registeredVirtualDisplays(): Set<Int> = virtualDisplays

    /** Chủ của [displayId], hoặc `null` nếu không có chủ đã đăng ký. */
    fun ownerOf(displayId: Int): DisplayOwner? = when {
        displayId == MAIN_DISPLAY -> DisplayOwner.LAUNCHER
        displayId == CAST_DISPLAY -> DisplayOwner.CAST
        displayId in virtualDisplays -> DisplayOwner.LAUNCHER
        else -> null
    }

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
        /** Màn chính launcher. */
        const val MAIN_DISPLAY: Int = 0

        /** Cụm tài xế (cast-only). */
        const val CAST_DISPLAY: Int = 1
    }
}
