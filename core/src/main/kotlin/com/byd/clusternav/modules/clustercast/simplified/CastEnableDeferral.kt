package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ V-CLUSTER · VC-R7 — `cast_enabled` THEO HỒ SƠ nhưng HOÃN ÁP: khoá chờ `cast_enabled_pending` ════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §11.4.3. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Ràng buộc buộc phải có lớp này (spec K1 — lý do của OQ2 cũ VẪN ĐÚNG, chỉ kết luận đổi)
 * [ĐO code b5c0e87] mọi chỗ đọc `cast_enabled` đều LIVE và đi qua MỘT cổng (`SimpleCastPrefs.castEnabled()`):
 * `NavRepository` mỗi khung HUD, `ClusterNavLaneWidget` mỗi nhịp thông báo, `FloatingBubbleService` mỗi lượt start.
 * Ghi thẳng giá trị của hồ sơ mới vào khoá đó mà không mở/đóng projection là để cụm có **hai chủ**: bật→tắt ⇒ HUD
 * ghi đè op 39 lên cụm trong khi phiên chiếu vẫn chạy; tắt→bật ⇒ HUD thôi ghi mà không phiên nào thay chỗ ⇒ cụm trống.
 *
 * ⇒ Bất biến: khoá sống [LIVE_KEY] luôn là giá trị **HIỆU LỰC** khớp với projection thật. Lượt đổi hồ sơ **không bao
 * giờ** ghi nó — lựa chọn của hồ sơ nằm chờ ở [PENDING_KEY], và chỉ được chốt ở lần tiến trình khởi động kế
 * ([onProcessStart]) — [ĐO 09-29] BYD giết Kachi mỗi lần tắt máy, nên trên thực tế đó là lần nổ máy kế.
 *
 * ## Ba đường, ba hàm
 *  • chụp hồ sơ ⇒ [desiredForSnapshot]: lựa chọn của hồ sơ = bản chờ nếu có, không thì giá trị sống;
 *  • áp hồ sơ ⇒ [onApply]: bằng giá trị hiệu lực thì XOÁ bản chờ, khác thì GHI bản chờ, sai kiểu thì BỎ;
 *  • khởi động tiến trình ⇒ [onProcessStart]: chốt bản chờ vào khoá sống; chốt BẬT→TẮT thì chỗ gọi phải dọn
 *    projection mồ côi (nếu VD cụm còn sống qua lần tắt máy — [CHƯA BIẾT], OC-7).
 *
 * Cú chạm TƯỜNG MINH (công tắc Cài đặt / nút *Áp ngay*) đi đường thật `setCastEnabled` và **xoá bản chờ trong cùng
 * lượt ghi** — ý người lái vừa bày tỏ thắng mọi bản chờ cũ (ghim ở tầng THI HÀNH: `SharedPrefsSimpleCastPrefs`).
 */
object CastEnableDeferral {

    /** Khoá sống — giá trị HIỆU LỰC của phiên chiếu. */
    const val LIVE_KEY = "cast_enabled"

    /** Khoá chờ — theo XE (spec §11.4.1): nếu nó đi theo hồ sơ thì đổi hồ sơ lại tự đẻ ra bản chờ giả. */
    const val PENDING_KEY = "cast_enabled_pending"

    /** Mặc định của [LIVE_KEY] khi vắng — PHẢI trùng `SharedPrefsSimpleCastPrefs.castEnabled()` (owner 2026-08-11). */
    const val DEFAULT = false

    /** Giá trị ghi vào ảnh chụp của hồ sơ đang rời. `null` = cả hai vắng (hồ sơ dùng mặc định). */
    fun desiredForSnapshot(live: Any?, pending: Any?): Boolean? = (pending as? Boolean) ?: (live as? Boolean)

    /** Việc cần làm với [PENDING_KEY] khi áp ảnh của một hồ sơ. KHÔNG có nhánh nào chạm [LIVE_KEY]. */
    sealed interface OnApply {
        /** Hồ sơ muốn khác giá trị hiệu lực ⇒ ghi bản chờ. */
        data class SetPending(val on: Boolean) : OnApply

        /** Hồ sơ muốn đúng giá trị hiệu lực ⇒ xoá bản chờ (nếu có) — không còn gì để chốt. */
        object ClearPending : OnApply { override fun toString() = "ClearPending" }

        /** Giá trị trong ảnh sai kiểu ⇒ không đụng gì, chỗ gọi ghi log [reason]. */
        data class Drop(val reason: String) : OnApply
    }

    /**
     * [live] = giá trị đang có ở [LIVE_KEY] (vắng ⇒ [DEFAULT]); [desired] = giá trị trong ảnh của hồ sơ đích (`null` =
     * hồ sơ dùng mặc định). Giá trị sống sai kiểu được coi như vắng — đúng như `getBoolean` sẽ KHÔNG làm (nó ném), nên
     * ở đây không được ném theo.
     */
    fun onApply(live: Any?, desired: Any?): OnApply {
        val effective = live as? Boolean ?: DEFAULT
        val want = when (desired) {
            null -> DEFAULT
            is Boolean -> desired
            else -> return OnApply.Drop("$LIVE_KEY trong ảnh chụp có kiểu ${desired::class.simpleName}, cần Boolean")
        }
        return if (want == effective) OnApply.ClearPending else OnApply.SetPending(want)
    }

    /** Kết quả của lượt chốt lúc khởi động tiến trình. */
    sealed interface AtStart {
        /** Không có bản chờ ⇒ không làm gì. */
        object NoPending : AtStart { override fun toString() = "NoPending" }

        /**
         * Ghi [on] vào [LIVE_KEY] và xoá [PENDING_KEY] trong CÙNG một lượt ghi. [closeOrphan] = vừa chốt BẬT→TẮT ⇒
         * chỗ gọi phải xếp lượt dọn projection mồ côi (chỉ khi dò thấy VD cụm thật, spec §11.4.3 bốn câu CLAUDE §4).
         */
        data class Commit(val on: Boolean, val closeOrphan: Boolean) : AtStart

        /** Bản chờ sai kiểu (sửa tay / cầu kiểm thử) ⇒ xoá nó, KHÔNG đổi khoá sống, ghi log [reason]. */
        data class Discard(val reason: String) : AtStart
    }

    /** Quyết định lượt chốt từ hai giá trị đọc ở tệp sống, trước khi dựng coordinator. Không shell, không ném. */
    fun onProcessStart(live: Any?, pending: Any?): AtStart {
        if (pending == null) return AtStart.NoPending
        val want = pending as? Boolean
            ?: return AtStart.Discard("$PENDING_KEY có kiểu ${pending::class.simpleName}, cần Boolean")
        val effective = live as? Boolean ?: DEFAULT
        return AtStart.Commit(on = want, closeOrphan = effective && !want)
    }

    /**
     * FIX286 · PI3 — nút **"Dùng hồ sơ này ngay"** sau khi nhập (người lái CHẠM, không bao giờ tự chạy): sau lượt đổi hồ
     * sơ, bản chờ [pending] được áp NGAY bằng đúng đường của nút *Áp ngay* — nhưng CHỈ khi cụm không đang chiếu app
     * ([SimpleCastState.Off] / [SimpleCastState.Idle]). Đang chiếu / đang chuyển trạng thái ⇒ `null` = để bản chờ cho lần
     * nổ máy sau (bật/tắt chiếu lúc đang có app trên cụm là đổi mặt cụm trước mặt người lái — CLAUDE.md §4).
     *
     * Trả giá trị cần áp, `null` = không áp gì.
     */
    fun applyOnUse(pending: Boolean?, state: SimpleCastState): Boolean? =
        pending.takeIf { state == SimpleCastState.Off || state == SimpleCastState.Idle }

    /** Khoá theo XE: mốc bền của lượt chốt gần nhất lúc khởi động ([CommitMark]). */
    const val COMMIT_MARK_KEY = "cast_enabled_committed"

    /**
     * FIX286 · PI5 — **mốc bền** của lượt chốt bản chờ lúc khởi động: chốt gì · lúc nào · bản nào. Ghi trong CÙNG lượt
     * `commit()` với khoá sống; màn Cài đặt › Chiếu cụm đọc để nói thật *"lần khởi động lúc … đã áp lựa chọn của hồ
     * sơ"* — màn Chẩn đoán không mở được trên bản phát hành, còn `usage-*.log` chỉ chụp pid của tiến trình hiện tại nên
     * dòng log của lượt dựng lại 0,3 s sau khi tắt máy có thể đã mất (phản biện FIX286 BÁC BỎ 4).
     *
     * Định dạng `"<1|0>|<epoch ms>|<versionCode>|<versionName>"`; đọc hỏng ⇒ `null`, không ném.
     */
    data class CommitMark(val on: Boolean, val atMs: Long, val versionCode: Int, val versionName: String) {
        fun encode(): String = "${if (on) 1 else 0}|$atMs|$versionCode|${versionName.filter { it != '|' && it >= ' ' }.take(24)}"

        companion object {
            fun decode(raw: Any?): CommitMark? {
                val f = (raw as? String)?.split('|') ?: return null
                if (f.size != 4) return null
                val on = when (f[0]) { "1" -> true; "0" -> false; else -> return null }
                val at = f[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
                val code = f[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
                return CommitMark(on, at, code, f[3])
            }
        }
    }
}
