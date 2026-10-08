package com.byd.clusternav.launcher.trip

/**
 * 2.97 · R2d (spec `docs/specs/kachi-297-plan.html`) — TUA MUỘN cho phát tiếp YouTube.
 *
 * [ĐO máy ảo QA 08/10, ca D] lượt phát tiếp HOÃN (R2c) gửi VIEW khi app thường (Maps) đang che màn nhà ⇒ YouTube trong ô KHÔNG
 * phát (không có phiên) cho tới khi màn nhà hiện lại ⇒ `resume-wait-timeout` ⇒ cờ giữ nhả ⇒ người lái về màn nhà, YouTube phát
 * đúng bài nhưng từ giây 0, rồi bộ lưu GHI ĐÈ điểm phát tiếp. Thay vì bỏ: giữ điểm tua lại, tới khi phiên đích THẬT phát đúng bài
 * thì tua. Luật thuần — bên thi hành (`YoutubeResumeSampler`) chỉ gọi khi phiên đổi (sự kiện, không dò định kỳ).
 */
object YoutubeLateSeek {

    /** Trần chờ (ms, `elapsedRealtime`): người lái quay về màn nhà trong chừng này thì vẫn tua. Quá ⇒ bỏ, bộ lưu chạy lại. */
    const val MAX_WAIT_MS = 20 * 60_000L

    /** Sau khi tua: giữ bộ lưu thêm chừng này để mẫu kế tiếp đọc vị trí ĐÃ tua ([ĐO máy ảo] tới đích ≈ 4 s). */
    const val AFTER_SEEK_HOLD_MS = 6_000L

    /**
     * Soát Pass 4 [P1] — phiên đích phát bài KHÁC chưa chắc là người lái tự chọn: [CHƯA BIẾT] phiên lúc quảng cáo đầu video trông
     * thế nào (KDoc `YoutubeResume.MIN_DURATION_MS`), và đường thường (`TripMusicResume.finish`) CHỜ qua bài khác tới 45 s
     * (`RESUME_WAIT_TRIES` × `SESSION_POLL_MS`) vì lý do đó. Cùng độ khoan dung ở đây: bài khác kéo dài quá chừng này ⇒ mới coi là
     * người lái chọn ⇒ bỏ. Trong khoảng đó ⇒ [Action.OTHER] (bên thi hành hẹn MỘT lượt xét lại sau hạn, không dò định kỳ).
     */
    const val OTHER_GRACE_MS = 45_000L

    /** [otherSinceMs] = mốc lần đầu thấy phiên đích phát bài KHÁC (0 = chưa thấy); bên thi hành đặt khi nhận [Action.OTHER]. */
    data class Pending(val pkg: String, val title: String, val seekMs: Long, val untilElapsedMs: Long, val otherSinceMs: Long = 0L)

    enum class Action { WAIT, SEEK, CANCEL, OTHER }

    /** Có đáng hẹn tua muộn không: có vị trí để tua và có tiêu đề để nhận đúng bài. */
    fun worth(title: String?, seekMs: Long): Boolean = seekMs > 0L && !title.isNullOrBlank()

    /**
     * Một lượt xét khi phiên của gói đổi. Hết hạn ⇒ CANCEL. Gói đích đang PHÁT đúng bài ⇒ SEEK. Gói đích đang phát bài KHÁC ⇒
     * OTHER trong [OTHER_GRACE_MS] kể từ lần đầu thấy ([Pending.otherSinceMs]); quá hạn đó ⇒ CANCEL (người lái tự chọn — không tua
     * bài người lái chọn, trả quyền lưu cho bộ lưu). Còn lại (chưa có phiên / chưa phát) ⇒ WAIT.
     */
    fun decide(p: Pending, lives: List<YoutubeResume.Live>?, nowElapsedMs: Long): Action {
        if (nowElapsedMs > p.untilElapsedMs) return Action.CANCEL
        val mine = lives.orEmpty().filter { it.pkg == p.pkg && it.playing }
        if (mine.any { YoutubeResume.matches(it.title, p.title) }) return Action.SEEK
        if (mine.any { !it.title.isNullOrBlank() }) {
            return if (p.otherSinceMs > 0L && nowElapsedMs - p.otherSinceMs >= OTHER_GRACE_MS) Action.CANCEL else Action.OTHER
        }
        return Action.WAIT
    }
}
