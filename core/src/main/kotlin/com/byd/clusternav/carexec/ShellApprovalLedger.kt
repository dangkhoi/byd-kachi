package com.byd.clusternav.carexec

/**
 * ═══ READY-AT-HOME — DẤU BỀN "xe này đã duyệt khoá adb này" (spec §4.4.1) ═══════════════════════════════════════
 *
 * Ba dạng, không hơn:
 *  • [None] — chưa từng có dấu (xe CHƯA TỪNG duyệt khoá này — hoặc bản cài vừa nâng cấp lên, chưa đo lần nào);
 *  • [Approved] — một phiên đã bắt tay xong bằng khoá có vân tay [Approved.fp] lúc [Approved.okWallMs] (giờ tường), hạn
 *    duyệt của máy là [Approved.windowMs] (`adb_allowed_connection_time`; `0` = không bao giờ hết);
 *  • [Lost] — BIA MỘ: từng có dấu, rồi một phiên hỏi bị adbd hỏi lại / từ chối ⇒ MẤT DUYỆT (thẻ nói đúng câu "xe không
 *    còn nhận quyền" thay vì "chưa từng"). Không bao giờ quay lại [None].
 *
 * Mã hoá `key=value;…` (không JSON: `:car-integration` là JVM thuần, không có `org.json` của Android; một khuôn phẳng
 * đủ cho bốn trường và test bằng chuỗi được). Giá trị hỏng / lạ phiên bản ⇒ [None] — không đoán là đã duyệt.
 */
sealed interface ShellApprovalLedger {

    object None : ShellApprovalLedger

    data class Approved(val fp: String, val okWallMs: Long, val windowMs: Long) : ShellApprovalLedger

    data class Lost(val lostWallMs: Long) : ShellApprovalLedger

    companion object {
        private const val VERSION = "1"
        private val FP = Regex("^[0-9a-f]{16,64}$")

        fun encode(ledger: ShellApprovalLedger): String? = when (ledger) {
            None -> null
            is Approved -> "v=$VERSION;fp=${ledger.fp};ok=${ledger.okWallMs};win=${ledger.windowMs}"
            is Lost -> "v=$VERSION;lost=${ledger.lostWallMs}"
        }

        fun decode(raw: String?): ShellApprovalLedger {
            if (raw.isNullOrBlank()) return None
            val m = raw.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            if (m["v"] != VERSION) return None
            m["lost"]?.toLongOrNull()?.let { return Lost(it) }
            val fp = m["fp"]?.takeIf { FP.matches(it) } ?: return None
            val ok = m["ok"]?.toLongOrNull() ?: return None
            val win = m["win"]?.toLongOrNull() ?: return None
            return Approved(fp, ok, win)
        }

        /** Xe đã từng có dấu (sống hoặc bia mộ) — quyết CHƯA TỪNG vs MẤT DUYỆT. */
        fun hadRecord(ledger: ShellApprovalLedger): Boolean = ledger !is None

        /** Ghi đè khi xoá: có dấu ⇒ bia mộ; chưa từng có ⇒ vẫn chưa từng (không bịa ra một lần duyệt). */
        fun forgotten(ledger: ShellApprovalLedger, nowWallMs: Long): ShellApprovalLedger =
            if (ledger is None) None else Lost(nowWallMs)
    }
}
