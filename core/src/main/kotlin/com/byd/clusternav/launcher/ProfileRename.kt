package com.byd.clusternav.launcher

/**
 * ═══ S4 · ĐỔI TÊN MỘT HỒ SƠ — phần **thuần** của việc ═══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` **R13** (owner 2026-09-16 · **E5**: *"nên cho đổi tên hồ sơ"*).
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car; `:app` ([WorkspacePrefs]) chỉ còn việc chạm đĩa.
 *
 * ## Vì sao đổi tên KHÔNG phải "tạo mới rồi xoá cũ"
 * Tên hồ sơ là **tiền tố của mọi khoá lưu bền** của nó (`<tên>__preset`, `<tên>__slot_0`, `<tên>__cn__…` — xem
 * [ProfileScope.LAUNCHER_SUFFIXES]). Tạo-mới-rồi-xoá-cũ có nghĩa là một hồ sơ **trống** cộng với một lượt
 * `deleteProfile` xoá sạch khoá cũ ⇒ người dùng đổi tên và mất toàn bộ bố cục. Đổi tên phải là một phép **dời
 * khoá**, và danh sách hậu tố phải lấy từ đúng một chỗ đã có, không chép tay.
 *
 * ## Vì sao phần kiểm nằm ở đây chứ không ở màn Cài đặt
 * Bốn ca hỏng đều **im lặng** nếu không ai chặn: tên rỗng (một hồ sơ không có tên trên chip), tên trùng (hai dòng
 * y hệt trong bộ chọn, và khoá của hai hồ sơ trộn vào nhau), tên có xuống dòng (danh sách lưu bằng `\n` ⇒ một hồ
 * sơ tách làm hai), và đổi tên một hồ sơ không tồn tại. Kiểm ở `:core` thì mọi ca ấy có bài test, không phải gõ
 * tay trên xe.
 */
object ProfileRename {

    /** Vì sao một lượt đổi tên bị từ chối. */
    enum class Err {
        /** Tên mới rỗng sau khi dọn. */
        EMPTY,

        /** Không có hồ sơ nào tên như [Plan] yêu cầu. */
        UNKNOWN,

        /** Tên mới trùng một hồ sơ KHÁC đang có. */
        DUPLICATE,
    }

    /**
     * Kết quả một lượt lập kế hoạch.
     *
     * @property profiles danh sách hồ sơ sau khi đổi (giữ nguyên **thứ tự** — thứ tự là thứ người dùng thấy trong
     *   bộ chọn, đảo nó là một thay đổi không ai xin).
     * @property from tên cũ (đã dọn) · @property to tên mới (đã dọn).
     * @property suffixes mọi hậu tố khoá phải dời, lấy từ [ProfileScope.LAUNCHER_SUFFIXES].
     */
    data class Plan(
        val profiles: List<String>,
        val from: String,
        val to: String,
        val suffixes: List<String> = ProfileScope.LAUNCHER_SUFFIXES,
    )

    /** Hoặc một kế hoạch dùng được, hoặc một lý do từ chối. */
    sealed interface Result {
        data class Ok(val plan: Plan) : Result
        data class No(val err: Err) : Result
    }

    /**
     * Dọn tên: bỏ khoảng trắng hai đầu và **thay** mọi ký tự xuống dòng bằng dấu cách.
     *
     * Cùng phép dọn với [WorkspacePrefs.addProfile] — và đó là chủ ý: hai đường vào cùng một danh sách mà dọn
     * khác nhau thì sớm muộn có một tên chỉ một bên chấp nhận.
     */
    fun clean(name: String): String = name.trim().replace(Regex("[\\r\\n]"), " ")

    /**
     * Lập kế hoạch đổi [old] → [new] trên [profiles].
     *
     * Đổi tên thành **chính nó** (sau khi dọn) là hợp lệ và trả một kế hoạch rỗng-hiệu-lực: chỗ gọi vẫn ghi lại
     * đúng những gì đang có. Từ chối nó sẽ bắt màn Cài đặt phải tự so chuỗi — tức luật nằm ở hai nơi.
     */
    fun plan(old: String, new: String, profiles: List<String>): Result {
        val from = clean(old)
        val to = clean(new)
        if (to.isEmpty()) return Result.No(Err.EMPTY)
        if (from !in profiles) return Result.No(Err.UNKNOWN)
        if (to != from && to in profiles) return Result.No(Err.DUPLICATE)
        return Result.Ok(Plan(profiles.map { if (it == from) to else it }, from, to))
    }
}
