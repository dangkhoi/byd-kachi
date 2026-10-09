package com.byd.clusternav.launcher

import android.hardware.display.VirtualDisplay
import android.util.Log

/**
 * Tay cầm một màn ảo của ô: bản thân [vd], [displayId] của nó, và đường **gỡ đăng ký** khỏi
 * `DisplayOwnershipRegistry` (qua `WindowCommandDispatcher`). Ba thứ phải đi cùng nhau vì giải phóng mà quên gỡ
 * đăng ký là để lại một id display "thuộc LAUNCHER" trỏ vào hư không — cổng ownership sẽ cho qua một lệnh
 * `am start --display <id đã chết>`.
 */
class VdLease(
    val vd: VirtualDisplay,
    val displayId: Int,
    private val unregister: (Int) -> Unit,
) {
    internal fun free() {
        com.byd.clusternav.launcher.escape.SlotEscapeReturn.revokeDisplay(displayId)   // 2.98 · R18: màn chết ⇒ không còn là ô
        runCatching { unregister(displayId) }
            .onFailure { Log.w(TAG, "gỡ đăng ký display $displayId hỏng: ${it.javaClass.simpleName}") }
        runCatching { vd.release() }
            .onFailure { Log.w(TAG, "release màn ảo display $displayId hỏng: ${it.javaClass.simpleName}") }
    }

    internal companion object { const val TAG = "KachiVd" }
}

/**
 * ═══ CHỦ SỞ HỮU MÀN ẢO Ô — MỘT BẢN DUY NHẤT CHO CẢ TIẾN TRÌNH (H2·1) ══════════════════════════════════════════
 *
 * Vì sao là `object` (sống theo tiến trình) chứ không phải một field của màn Kachi: cái rò [ĐO] được nằm ĐÚNG ở
 * chỗ hai màn Kachi cùng sống — màn cũ mang cờ "đang kết thúc" nhưng view chưa tháo nên màn ảo của nó chưa chết,
 * màn mới đã tạo màn ảo của riêng nó (xem KDoc [SlotVdLedger]). Một chủ sở hữu nằm TRONG màn Kachi thì theo định
 * nghĩa không thấy được màn ảo của màn Kachi kia ⇒ không thể dọn. Bản duy nhất cho cả tiến trình thấy cả hai.
 *
 * Bất biến: **mỗi ô nhiều nhất một màn ảo sống**. Ba lối vào, đều idempotent:
 *  - [adopt] — host mới nhận ô ⇒ màn ảo cũ của ô đó bị giải phóng ngay (kể cả của chủ khác);
 *  - [release] — ô bị thay/đóng/dựng lại ⇒ nhả đúng ô đó;
 *  - [releaseOwner] — màn Kachi huỷ / workspace tháo ⇒ nhả mọi ô của chính nó, không đụng ô của chủ khác.
 *
 * Nhật ký (`KachiVd`) ghi tên VD ở cả hai chiều tạo/giải phóng để `adb logcat -s KachiVd` đối chiếu được với
 * `dumpsys display | grep -o 'kachi-slot-[0-9]*' | sort -u`.
 */
object SlotVdOwner {

    private const val TAG = VdLease.TAG
    private val ledger = SlotVdLedger<VdLease>()

    /** Host của [owner]/[slot] nhận màn ảo [name]. Màn ảo cũ CÙNG Ô (bất kỳ chủ nào) được giải phóng ngay. */
    fun adopt(owner: String, slot: Int, name: String, lease: VdLease) {
        val stale = ledger.adopt(owner, slot, name, lease)
        Log.i(TAG, "tạo màn ảo $name — ô $slot · display ${lease.displayId} · chủ $owner · đang sống ${ledger.live().size}")
        stale.forEach { taken(it) }
    }

    /**
     * 2.98 · R14 (OQ4 `SLOT-ADOPT-NOTIFY-OLD-HOST`) — màn ảo của host CŨ bị lấy: thôi nhịp đo của host đó TRƯỚC khi nhả. [ĐO máy ảo
     * 09/10 15:48:57] HOME dựng màn chính MỚI ⇒ `slot-taken` nhả màn ảo màn cũ, 1,6 s sau bộ đo màn cũ (`ws@…#0`) kết luận "app đã
     * đóng" ⇒ `APP_DIED -> Clear` trên màn đang ẩn (lớp tạm của màn cũ bị xoá, câu/Toast có thể bắn từ màn khuất). Khoá trùng chủ+ô
     * của host mới (cùng cây dựng lại ô) cũng gỡ — host mới chỉ `watch` SAU khi nhận màn ảo (lượt mở chạy sau `adopt`/[move]).
     */
    private fun taken(e: SlotVdLedger.Entry<VdLease>) {
        SlotLiveProbe.unwatch(SlotVdLedger.keyOf(e.owner, e.slot))
        free(e, WHY_SLOT_TAKEN)
    }

    /**
     * Ô 7 (2.89-thử1, `ParkedApps`) — CHUYỂN màn ảo [name] đang sống sang khoá [owner]/[slot] mà KHÔNG giải phóng nó
     * ([SlotVdLedger.adopt] cùng tên chỉ đổi khoá). Màn ảo khác đang giữ CÙNG ô bị giải phóng như [adopt] (bất biến giữ).
     */
    fun move(owner: String, slot: Int, name: String, lease: VdLease) {
        val stale = ledger.adopt(owner, slot, name, lease)
        Log.i(TAG, "chuyển màn ảo $name — display ${lease.displayId} → $owner#$slot · đang sống ${ledger.live().size}")
        stale.forEach { taken(it) }
    }

    /** 2.98 · R4: tên các màn ảo ô Kachi đang sống (kể cả app đỗ ô 7) — [SlotVdName.pick] né chúng để sổ không nhầm hai màn là một. */
    fun liveNames(): Set<String> = ledger.live().mapTo(HashSet()) { it.name }

    /** Nhả màn ảo của [owner] ở [slot] (ô bị thay nội dung / dựng lại / đóng). Gọi lại lần hai ⇒ không làm gì. */
    fun release(owner: String, slot: Int) {
        ledger.release(owner, slot)?.let { free(it, WHY_SLOT_RELEASED) }
    }

    /** Nhả MỌI màn ảo của [owner] (workspace tháo / màn Kachi huỷ). */
    fun releaseOwner(owner: String) {
        ledger.releaseOwner(owner).forEach { free(it, WHY_OWNER_GONE) }
    }

    /**
     * 2.98 · R3 (`SlotProbeScope`) — ảnh chụp (chủ, display) của mọi màn ảo đang sống. Chỉ ĐỌC sổ trong RAM (dưới khoá của sổ),
     * 0 lệnh; `SlotLiveProbe` gọi tối đa MỘT lần mỗi nhịp đo và chỉ khi có ô vắng app.
     */
    fun held(): List<SlotProbeScope.Held> = ledger.live().map { SlotProbeScope.Held(it.owner, it.handle.displayId) }

    /**
     * Lý do giải phóng — **mã ASCII ngắn**, không phải câu tiếng Việt. Cùng lý do mà dự án không dịch nhật ký
     * (xem KDoc `PermissionReport.logLine`): hai lượt đo trên hai máy khác ngôn ngữ phải grep được bằng MỘT chuỗi.
     */
    private const val WHY_SLOT_TAKEN = "slot-taken"      // ô có chủ mới (màn Kachi đời sau dựng lại ô này)
    private const val WHY_SLOT_RELEASED = "slot-released" // ô bị thay nội dung / đóng / dựng lại
    private const val WHY_OWNER_GONE = "owner-gone"       // cây workspace tháo / màn chính huỷ

    private fun free(entry: SlotVdLedger.Entry<VdLease>, why: String) {
        Log.i(TAG, "giải phóng màn ảo ${entry.name} — display ${entry.handle.displayId} · $why · còn lại ${ledger.live().size}")
        entry.handle.free()
    }
}
