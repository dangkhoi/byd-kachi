package com.byd.clusternav.core

/**
 * ═══ 2.98 · OTA-UPDATE-DOT — chấm *có bản mới* trên nút Cài đặt + hàng "Kiểm tra cập nhật" ═══════════════════════════
 *
 * Backlog PHASE-2 (b) *"có dấu hiệu nhìn thấy được khi có bản mới … vì im lặng = như không có"*. Luật THUẦN; `:app`
 * `UpdateDotStore` chỉ đọc/ghi prefs và báo view.
 *
 * ## Không thêm lượt dò mạng nào
 * Chấm chỉ ăn theo lượt dò SẴN CÓ (`UpdateFlow.start`: tự động một lần mỗi tiến trình khi bật *Tự động cập nhật* — BYD
 * giết Kachi mỗi lần tắt máy [ĐO 29/09] ⇒ ≈ một lần mỗi lần nổ máy — và mỗi cú bấm tay). Kết quả được GHI BỀN ([Record])
 * ⇒ chấm sống qua khởi động lại mà không cần gọi mạng.
 *
 * ## Luật ([afterCheck] · [shows])
 *  • dò lỗi (mạng/GitHub) ⇒ GIỮ bản ghi cũ — lỗi mạng không phải bằng chứng "hết bản mới";
 *  • dò được mà kênh không mời ([OtaVersion.offers] sai) ⇒ XOÁ;
 *  • kênh mời ⇒ GHI (phiên bản kênh + versionCode đang cài lúc thấy).
 *  • hiện chấm khi: có bản ghi · đọc được tên + versionCode đang cài · versionCode đang cài KHÔNG lớn hơn lúc thấy (đã có
 *    một lượt cài mới hơn kể từ đó ⇒ bản ghi cũ, bỏ) · kênh vẫn mời so với tên đang cài (cài xong bản đó ⇒ tự tắt).
 *  • không đọc được bản đang cài ⇒ KHÔNG chấm (thà thiếu chấm còn hơn chấm giả mời một bản không mới hơn).
 */
object UpdateDot {

    /** Bản ghi bền: [version] kênh mời, [seenAtVersionCode] versionCode đang cài lúc thấy. */
    data class Record(val version: String, val seenAtVersionCode: Long)

    /** Việc với bản ghi sau một lượt dò. */
    sealed class Action {
        object Keep : Action()
        object Clear : Action()
        data class Set(val record: Record) : Action()
    }

    /**
     * @param latest phiên bản cao nhất trên kênh (`null` khi không thấy APK nào / lỗi).
     * @param errored lượt dò hỏng (mạng, phản hồi rỗng, JSON lạ…).
     * @param installedName versionName đang cài (`"?"` nếu không đọc được).
     * @param installedVersionCode versionCode đang cài (`null` nếu không đọc được).
     */
    fun afterCheck(latest: String?, errored: Boolean, installedName: String, installedVersionCode: Long?): Action {
        if (errored) return Action.Keep
        if (latest == null || !OtaVersion.readable(installedName) || installedVersionCode == null) return Action.Clear
        return if (OtaVersion.offers(latest, installedName)) Action.Set(Record(latest.trim(), installedVersionCode))
        else Action.Clear
    }

    /** Có vẽ chấm không. Xem luật ở KDoc lớp. */
    fun shows(record: Record?, installedName: String, installedVersionCode: Long?): Boolean {
        if (record == null || installedVersionCode == null) return false
        if (!OtaVersion.readable(installedName) || !OtaVersion.readable(record.version)) return false
        if (installedVersionCode > record.seenAtVersionCode) return false
        return OtaVersion.offers(record.version, installedName)
    }

    /**
     * Bản ghi còn đáng giữ không — `false` ⇒ `:app` xoá luôn (đã cài bản đó / bản mới hơn), để prefs không giữ rác.
     * Không đọc được bản đang cài ⇒ GIỮ (chưa biết thì không xoá).
     */
    fun stillValid(record: Record, installedName: String, installedVersionCode: Long?): Boolean {
        if (installedVersionCode == null || !OtaVersion.readable(installedName)) return true
        return shows(record, installedName, installedVersionCode)
    }
}
