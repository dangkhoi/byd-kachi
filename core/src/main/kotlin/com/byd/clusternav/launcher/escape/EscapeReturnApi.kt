package com.byd.clusternav.launcher.escape

/**
 * ═══ 2.98 · R18 (SLOT-ESCAPE-VD-RETURN) — tên hàm framework theo đời ROM cho "app thoát ô ⇒ stack về lại màn ảo ô" ═══════
 *
 * Daemon uid 2000 (`EscapeReturnDaemon`) gọi các hàm ẩn của `IActivityTaskManager` bằng phản chiếu. Tên hàm + hằng mã giao dịch
 * đổi theo đời Android (A12 đổi `getAllStackInfos` → `getAllRootTaskInfos`, `moveStackToDisplay` → `moveRootTaskToDisplay`), nên
 * khác biệt nằm ở BẢNG này, được chọn qua `ClusterProfile.escapeReturn` (CLAUDE.md §7) — không rải trong mã.
 *
 * Daemon chỉ nhận [id] qua socket rồi tra [byId] ⇒ không bao giờ phản chiếu một tên hàm tuỳ ý đọc từ mạng (cổng loopback có
 * token, nhưng vẫn không cho kênh đó chọn hàm).
 *
 * [ĐO nguồn `android-10.0.0_r47`]: `ActivityTaskManagerService.getAllStackInfos` (`:2763`, `MANAGE_ACTIVITY_STACKS`) ·
 * `moveStackToDisplay` (`:3395-3408`, `INTERNAL_SYSTEM_WINDOW`) · `registerTaskStackListener` (`:3452`) · `ITaskStackListener.aidl:80`
 * (`onActivityLaunchOnSecondaryDisplayFailed(in RunningTaskInfo, int)`, oneway). [ĐO máy ảo A10 09/10] nguyên mẫu uid 2000 gọi
 * đúng ba tên này 38/38 lần. A12 [CHƯA ĐO] ⇒ không có bảng, DL5 tắt.
 */
data class EscapeReturnApi(
    val id: String,
    /** Đời API duy nhất bảng đúng — đời khác ⇒ không bật (phía Kachi VÀ phía daemon đều kiểm). */
    val sdk: Int,
    val stackInfos: String,
    val moveStack: String,
    /** Tên hằng `TRANSACTION_…` trong `android.app.ITaskStackListener$Stub` — mã đọc lúc chạy, không ghi cứng số. */
    val failedTxnField: String,
) {
    fun usableOn(sdkInt: Int): Boolean = sdkInt == sdk

    companion object {
        val ANDROID_10_R47 = EscapeReturnApi(
            id = "a10r47",
            sdk = 29,
            stackInfos = "getAllStackInfos",
            moveStack = "moveStackToDisplay",
            failedTxnField = "TRANSACTION_onActivityLaunchOnSecondaryDisplayFailed",
        )

        private val KNOWN = listOf(ANDROID_10_R47)

        /** Bảng theo [id] đọc từ khung điều khiển; lạ ⇒ `null` (daemon không bật). */
        fun byId(id: String?): EscapeReturnApi? = KNOWN.firstOrNull { it.id == id }
    }
}
