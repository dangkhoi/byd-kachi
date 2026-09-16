package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ NHÓM **HỒ SƠ TÀI XẾ** của màn Cài đặt (S4 · R6/R8 · V3 · R13) ══════════════════════════════════════════
 *
 * Tách khỏi [SettingsSections] ở 1.66 vì hai lẽ, và lẽ thứ hai mới là lẽ thật:
 *  1. trần 500 dòng (CLAUDE.md §4.1) — thêm hàng *"Đổi tên"* đẩy tệp kia qua trần;
 *  2. **một tệp cho một nhóm** — đúng cách `nav` · `cast` · `keys` · `bars` · `car` · `places` đã tách. Nhóm này
 *     nay mang bốn việc (chuyển · tạo bản sao · đổi tên · xoá) cộng con trỏ *hồ sơ lúc nổ máy*, tức nó không còn
 *     là "một danh sách" nữa.
 *
 * Lớp này **không biết** vỏ bảng: nhận [SettingsDeps], đổ view vào một `LinearLayout` — cùng hợp đồng với mọi
 * `SettingsSections*` khác.
 */
class SettingsProfilesSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    fun build(body: LinearLayout) = profiles(body)

    /**
     * Nhóm **Hồ sơ tài xế** (S4 · R8): thẻ từng hồ sơ → **hồ sơ lúc nổ máy** → **thêm hồ sơ (bản sao)**.
     *
     * Thứ tự đó là thứ tự khai ở [SettingsCatalogEntries] (`profiles_list` · `profiles_active` · `profiles_boot` ·
     * `profiles_add`) — thứ tự danh mục phải là thứ tự dùng được: đổi hồ sơ là việc hằng ngày, chọn hồ sơ lúc nổ máy
     * là việc đặt-một-lần, tạo hồ sơ mới thì hiếm hơn nữa.
     *
     * ⚠ [SOÁT ẢNH 2026-09-12 · finding #21] KHÔNG có `sectionLabel` cho phần danh sách: rail bên trái đã ghi
     * "Hồ sơ tài xế" ở bậc SECTION, lặp đúng chữ đó làm tiêu đề đầu trang là hai lần trả lời cùng một câu hỏi. Hai
     * mục *dưới* danh sách thì CÓ tiêu đề phụ — từ S4 trang này có ba phần, nên chúng thật sự chia trang.
     */
    private fun profiles(body: LinearLayout) {
        val s = deps.state()
        body.addView(rows.note(context.getString(R.string.kachi_profiles_note)))
        s.profiles.forEach { name ->
            body.addView(
                profileRow(name, active = name == s.activeProfile, total = s.profiles.size),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { it.bottomMargin = dpi(context, Sp.S) },
            )
        }
        bootProfile(body, s)
        addProfile(body, s)
    }

    /**
     * S4 · R6 — **hồ sơ lúc nổ máy**: "Gần nhất" + từng hồ sơ.
     *
     * Thay cho *"cảnh lúc nổ máy"* của P7 (R1 bỏ hẳn khái niệm cảnh). Mặc định là [BOOT_LAST_CODE] — *"hồ sơ dùng
     * gần nhất"*, tức **giữ nguyên hành vi cũ**: tắt máy ở hồ sơ nào thì nổ máy lên bằng hồ sơ đó. Một mặc định
     * "hồ sơ X" sẽ âm thầm vứt bỏ lựa chọn của chuyến trước.
     *
     * `null` ở tầng dữ liệu ↔ sentinel [BOOT_LAST_CODE] ở tầng chip: [SettingsRows.chipRow] so **mã chuỗi** để biết
     * chip nào sáng nên nó không nhận `null` được, mà một chuỗi rỗng thì va với "chưa đặt gì". Sentinel hai gạch
     * dưới — cùng khuôn `__CUSTOM__` của [SettingsHomeSection] và `Prefs.VK_TARGET_*`, không phát minh khuôn mới.
     */
    private fun bootProfile(body: LinearLayout, s: HomeUiState) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sec_boot_profile)))
        val options = listOf(BOOT_LAST_CODE to context.getString(R.string.kachi_profile_boot_last)) +
            s.profiles.map { it to ProfileNames.display(it) }
        body.addView(rows.chipRow(
            label = context.getString(R.string.kachi_row_boot_profile),
            options = options,
            current = deps.bootProfile() ?: BOOT_LAST_CODE,
        ) { code -> deps.onBootProfile(if (code == BOOT_LAST_CODE) null else code) })
        body.addView(rows.note(context.getString(R.string.kachi_boot_profile_note)))
    }

    /**
     * S4 · R8 — **thêm hồ sơ = BẢN SAO của hồ sơ đang dùng**, và nhãn nút nói thẳng điều đó.
     *
     * Nút cũ ghi "Thêm hồ sơ…" rồi mở một hồ sơ TRẮNG. Từ R3 hồ sơ giữ tất cả lựa chọn, nên hồ sơ trắng nghĩa là
     * người dùng vừa bấm một nút và nhận về một màn hình mặc định hoàn toàn — phải chỉnh lại từ đầu chỉ để đổi một
     * chi tiết. Bản sao là điểm xuất phát đúng, và cái tên đề sẵn *"Bản sao của «X»"* nói ra nó vừa chép từ đâu.
     *
     * Hộp thoại hỏi tên dùng [SettingsDialogs.askName] — khuôn "hỏi một cái tên" dùng chung của dự án, không dựng
     * bản thứ hai (KDoc [SettingsDialogs] nói vì sao).
     */
    private fun addProfile(body: LinearLayout, s: HomeUiState) {
        val active = ProfileNames.display(s.activeProfile)
        body.addView(rows.button(context.getString(R.string.kachi_profiles_add, active)) {
            SettingsDialogs.askName(
                context,
                context.getString(R.string.kachi_profile_new_title),
                context.getString(R.string.kachi_profile_copy_of, active),
            ) { name -> deps.onDuplicateProfile(name) }
        })
    }

    /**
     * Một hồ sơ: tên + dấu "Đang dùng" + nút Xoá.
     *
     * ## Hai lối chặn xoá — và vì sao phải chặn ở UI
     * [ĐO] `WorkspacePrefs.deleteProfile` mở đầu bằng `if (list.size <= 1 || name !in list) return` ⇒ **hồ sơ cuối
     * cùng đã được chặn ở nơi lưu**. Nhưng xoá **hồ sơ đang dùng** thì nó *cho phép*, rồi âm thầm đổi hồ sơ đang
     * dùng sang phần tử đầu danh sách — nghĩa là một cú chạm "Xoá" làm đổi luôn cả bố cục/thanh nút/chip đang thấy,
     * mà không câu nào báo trước. Nên chặn ở đây.
     *
     * Cả hai ca đều **NÓI LÝ DO** chứ không làm mờ nút rồi im: bài học từ nút bố cục sẵn ở P9 — cú bấm không có tác
     * dụng mà không giải thích thì người dùng tưởng app hỏng. **Thứ tự hai ca cũng quan trọng** — xem chú thích tại
     * chỗ rẽ nhánh.
     */
    private fun profileRow(name: String, active: Boolean, total: Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.FIELD)
            val p = dpi(context, Sp.M)
            setPadding(p, p, p, p)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        // [SOÁT P3-4] NHÃN dịch được, KHOÁ giữ nguyên: `name` vẫn là tên gốc và vẫn là thứ đi vào
                        // `switchProfile`/`onDeleteProfile` bên dưới. Xem KDoc [ProfileNames].
                        text = ProfileNames.display(name); setTextColor(c(KachiTheme.INK)); typeface = Typeface.DEFAULT_BOLD
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
                    })
                    addView(TextView(context).apply {
                        // Owner 2026-09-14: hồ sơ phải NÓI RA nó giữ bố cục gì — trước đây chỉ có "Đang dùng"/"Chạm để đổi".
                        val sum = deps.profileSummary(name)
                        text = context.getString(if (active) R.string.kachi_profile_sub_active else R.string.kachi_profile_sub_switch, sum)
                        setTextColor(c(if (active) KachiTheme.GREEN else KachiTheme.MUT))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
                    })
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            // ⚠ [SOÁT UI 2026-09-12] Nút "Xoá" CHỈ dựng khi thực sự xoá được: KHÔNG phải hồ sơ đang dùng VÀ còn hồ
            // sơ khác. Trước đây nút luôn hiện (chữ đỏ trần, cách tên ~1300px ở mép phải) rồi bấm ra toast "không xoá
            // được" — một hành động nguy hiểm lại mời bấm nhầm trên màn xe. Ẩn hẳn khi không xoá được thì KHÔNG còn
            // affordance để hiểu nhầm, nên không cần toast giải thích nữa (khác ca P9: ở đây không có kỳ vọng bị chặn
            // im lặng — người dùng đơn giản không thấy nút). Lưới an toàn thật vẫn nằm ở `WorkspacePrefs.deleteProfile`.
            // V3 · R13 (owner 2026-09-16 · E5) — **Đổi tên**. Hiện cho MỌI hồ sơ: đổi tên không đụng tới thứ hồ
            // sơ đang mang (`WorkspacePrefs.renameProfile` DỜI khoá, không tạo/xoá), nên không có ca nào để chặn
            // — khác hẳn nút Xoá ngay dưới. Hộp hỏi tên dùng chung `SettingsDialogs.askName`, không dựng bản thứ hai.
            addView(rows.button(context.getString(R.string.kachi_profile_rename)) {
                SettingsDialogs.askName(
                    context,
                    context.getString(R.string.kachi_profile_rename),
                    ProfileNames.display(name),
                ) { newName -> rename(name, newName) }
            })
            if (!active && total > 1) addView(TextView(context).apply {
                text = context.getString(R.string.kachi_delete); setTextColor(c(KachiTheme.RED)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
                background = KachiTheme.card(context, Sp.RADIUS_PILL, KachiTheme.CLEAR, KachiTheme.RED)
                setOnClickListener { deps.onDeleteProfile(name) }
            })
            if (!active) setOnClickListener { deps.onSwitchProfile(name) }
        }

    /**
     * V3 · R13 — đổi tên, và **NÓI RA khi bị từ chối**.
     *
     * Phép kiểm là hàm thuần ở `:core` ([ProfileRename.plan]) nên hai bề mặt (Cài đặt và nơi lưu bền) dùng đúng
     * một luật. Gọi thẳng `onRenameProfile` rồi thôi sẽ cho ra một cú chạm **không có tác dụng và không giải
     * thích** — đúng bài học nút bố cục sẵn ở P9 (người dùng tưởng app hỏng).
     */
    private fun rename(old: String, new: String) {
        // ⚠ [SOÁT Pass 1 · P3 · 2026-09-16] Hộp hỏi tên **điền sẵn nhãn ĐÃ DỊCH** ([ProfileNames.display]), nên
        // trên máy tiếng Anh người dùng mở hộp của hồ sơ dựng sẵn và bấm OK mà không sửa gì sẽ gửi về đúng chuỗi
        // `"Default"` — tức một lượt đổi tên THẬT từ khoá gốc `"Mặc định"`, và từ đó hồ sơ ấy mất bản dịch vĩnh
        // viễn. Đó đúng là ca mà KDoc [ProfileNames.display] cấm (*"truyền kết quả của nó trở lại đường ghi"*),
        // chỉ khác là nó đi vòng qua một cú bấm OK. Không sửa gì ⇒ không làm gì.
        if (ProfileRename.clean(new) == ProfileNames.display(old)) return
        if (ProfileRename.plan(old, new, deps.state().profiles) is ProfileRename.Result.No) {
            runCatching {
                Toast.makeText(context, R.string.kachi_profile_rename_failed, Toast.LENGTH_LONG).show()
            }
            return
        }
        deps.onRenameProfile(old, new)
    }

    private companion object {
        /**
         * Mã của chip **"Gần nhất"** (không ghim hồ sơ nào lúc nổ máy).
         *
         * Ở tầng dữ liệu ca này là `null` ([WorkspaceRepository.bootProfile]) — nơi lưu cần phân biệt *"chưa
         * chọn"* với *"chọn hồ sơ tên X"*, và tên hồ sơ do người dùng đặt nên không được đụng phải một tên dành
         * riêng. [SettingsRows.chipRow] thì so **mã chuỗi** nên nó không nhận `null` được. Hai lớp, hai cách
         * biểu diễn, quy đổi ở ĐÚNG một chỗ ([bootProfile]).
         */
        const val BOOT_LAST_CODE = "__LAST__"
    }
}
