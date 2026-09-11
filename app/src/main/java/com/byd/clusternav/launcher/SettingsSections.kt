package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.BuildConfig
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * NỘI DUNG từng nhóm của màn Cài đặt (S1 · T3) — trừ nhóm "Màn hình chính" nằm ở [SettingsHomeSection] (trần 500
 * dòng; nhóm đó một mình đã dài hơn cả sáu nhóm còn lại cộng lại).
 *
 * Lớp này **không biết** vỏ bảng: nó nhận [SettingsDeps] và trả về một `ScrollView` cho mỗi nhóm. Nhờ vậy [build] là
 * chỗ duy nhất ánh xạ *nhóm → nội dung*, và `when` trên [SettingsGroup] là **exhaustive** ⇒ thêm một nhóm vào
 * `:core` mà quên dựng nội dung thì **không biên dịch được**, không phải một trang trắng im lặng.
 */
class SettingsSections(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    /** Trang của một nhóm. Mỗi nhóm **cuộn riêng** (R1) vì vỏ bảng giữ lại chính thực thể này. */
    fun build(group: SettingsGroup): View {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dpi(context, Sp.XS), dpi(context, Sp.L))
        }
        when (group) {
            // Lưới 187 ô dựng trong đây ⇒ MỘT thực thể [CapabilityGridSection] mới cho mỗi lượt dựng trang (ràng
            // buộc "một lưới = một bảng tiles"). Vỏ bảng nhớ trang lại nên lượt dựng này không lặp mỗi lần đổi nhóm.
            SettingsGroup.HOME -> SettingsHomeSection(context, rows, deps).build(body)
            SettingsGroup.DISPLAY -> display(body)
            SettingsGroup.PROFILES -> profiles(body)
            SettingsGroup.CAR -> car(body)
            SettingsGroup.SYSTEM -> system(body)
            SettingsGroup.CLUSTERNAV -> clusterNav(body)
            SettingsGroup.ABOUT -> about(body)
        }
        return ScrollView(context).apply { addView(body); isVerticalScrollBarEnabled = false }
    }

    // ── Hiển thị & đơn vị ────────────────────────────────────────────────────────────────────────

    /**
     * Đơn vị (7 loại) + hiện trạng giao diện sáng/tối.
     *
     * ⚠⚠ **Giao diện sáng/tối ở đây là DÒNG CHỮ, không phải nút gạt — có chủ ý, đã ĐO.** `theme_mode` có enum
     * [ThemeMode] ở `:core`, có `HomeViewModel.setThemeMode`, có đường lưu bền, có test — nhưng [ĐO] `grep` toàn
     * `app/src/main`: **không một chỗ nào đọc `HomeUiState.themeMode` để VẼ hay để áp `uiMode`**; hai chỗ duy nhất
     * đọc nó là `PrefsWorkspaceRepository.load/persist` (nạp và ghi lại chính nó), và `ThemeMode.isNight(...)` có
     * **0** chỗ gọi. `KachiHomeActivity` cũng không override `attachBaseContext`. Cộng thêm: bảng màu launcher là
     * **hằng biên dịch** (`KachiTheme.const val`) và [ĐO] có 78 mã màu hex viết cứng ở 17 tệp.
     *
     * ⇒ Thêm nút gạt bây giờ sẽ là **nút chết**: bấm xong lưu bền đúng, mà màn hình không đổi một pixel. Luật dự án
     * (`product-team-workflow.md`) cấm nút chết, và nó là đúng bệnh mà S1 đi dọn — nói khác đi, "vẽ được ≠ đặt được"
     * sẽ thành "đặt được ≠ có tác dụng". Nên ở đây **nói thật hiện trạng**, và đường lưu bền + intent GIỮ NGUYÊN
     * (owner chưa quyết bảng màu sáng — OQ2), không xoá.
     */
    private fun display(body: LinearLayout) {
        body.addView(rows.sectionLabel("Đơn vị hiển thị"))
        var units = deps.state().unitPrefs
        UnitFormat.quantitiesInUse().forEach { q ->
            body.addView(rows.unitRow(q, units.unitFor(q)) { code ->
                units = units.with(q, code)
                deps.onUnitPrefs(units)
            })
        }
        body.addView(rows.sectionLabel("Giao diện sáng/tối"))
        body.addView(rows.note(
            "Kachi hiện chỉ có bảng màu TỐI — chọn được sáng/tối thì màn hình vẫn không đổi, nên ở đây chưa đặt " +
                "nút gạt (một nút bấm mà không thấy gì đổi thì tệ hơn là chưa có nút).",
        ))
        body.addView(rows.note(
            "Màn ClusterNav (nhóm \"Dẫn đường · Cụm · Phím\") có lựa chọn Sáng/Tối riêng và nó CHẠY cho các màn " +
                "của ClusterNav.",
        ))
    }

    // ── Hồ sơ tài xế ─────────────────────────────────────────────────────────────────────────────

    private fun profiles(body: LinearLayout) {
        val s = deps.state()
        body.addView(rows.sectionLabel("Hồ sơ tài xế"))
        body.addView(rows.note(
            "Mỗi hồ sơ giữ bố cục, nội dung ô, thanh nút và chip RIÊNG. Chạm một hồ sơ để đổi sang nó — " +
                "avatar ở thanh trên cũng đổi được, nhưng tạo và xoá thì chỉ ở đây.",
        ))
        s.profiles.forEach { name ->
            body.addView(
                profileRow(name, active = name == s.activeProfile, total = s.profiles.size),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { it.bottomMargin = dpi(context, Sp.S) },
            )
        }
        body.addView(rows.button("Thêm hồ sơ…") { deps.onAddProfile() }, wrapLp())
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
            background = KachiTheme.card(context, Sp.RADIUS_L, "#161b24")
            val p = dpi(context, Sp.M)
            setPadding(p, p, p, p)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = name; setTextColor(c(KachiTheme.INK)); typeface = Typeface.DEFAULT_BOLD
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                    })
                    addView(TextView(context).apply {
                        text = if (active) "Đang dùng" else "Chạm để đổi sang hồ sơ này"
                        setTextColor(c(if (active) KachiTheme.GREEN else KachiTheme.MUT))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    })
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(TextView(context).apply {
                text = "Xoá"; setTextColor(c(KachiTheme.RED)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.S), dpi(context, Sp.S))
                setOnClickListener {
                    when {
                        // ⚠ [SOÁT S1 · P2] Thứ tự PHẢI là "hồ sơ cuối cùng" TRƯỚC "đang dùng". Máy mới cài có ĐÚNG
                        // MỘT hồ sơ, và nó tất nhiên là hồ sơ đang dùng ⇒ xét `active` trước thì câu trả lời là
                        // "đổi sang hồ sơ khác trước khi xoá" trong khi KHÔNG có hồ sơ khác nào để đổi sang. Lời
                        // khuyên bất khả thi còn tệ hơn không nói gì, và đây là trạng thái mặc định của mọi máy.
                        total <= 1 -> toast("Phải còn ít nhất một hồ sơ")
                        active -> toast("Đang dùng \"$name\" — đổi sang hồ sơ khác trước khi xoá")
                        else -> deps.onDeleteProfile(name)
                    }
                }
            })
            if (!active) setOnClickListener { deps.onSwitchProfile(name) }
        }

    // ── Tiện nghi xe ─────────────────────────────────────────────────────────────────────────────

    private fun car(body: LinearLayout) {
        body.addView(rows.sectionLabel("Tiện nghi tự động"))
        body.addView(recircRow(deps.recircOnStart()) { deps.onRecircOnStart(it) })
    }

    /**
     * W3 — ô tick "nổ máy thì tự lấy gió trong". Chuyển **nguyên văn** từ bảng Tuỳ biến: cùng câu chữ, cùng cảnh báo.
     *
     * Giữ nguyên câu **"chưa kiểm trên xe"** (R10): mã `recirc` ở mức tier OVERDRIVE — đọc từ mã nguồn khác, chưa
     * xác nhận trên xe owner (khác ghế/lọc-bụi đã PROVEN). Không được hứa nó chạy.
     */
    private fun recircRow(on: Boolean, onChange: (Boolean) -> Unit): View = rows.checkRow(
        on = on,
        title = "Nổ máy thì tự lấy gió trong",
        sub = "Xe quên chế độ này mỗi lần khởi động. ⚠ Lệnh chưa kiểm trên xe — có thể xe không nhận.",
        onChange = onChange,
    )

    // ── Hệ thống & quyền ─────────────────────────────────────────────────────────────────────────

    /**
     * Quyền còn thiếu (P8) + tự mở khi nổ máy (S1·T4).
     *
     * ⚠ **Sai lệch có chủ ý so với bảng Tuỳ biến cũ**: bảng cũ *ẩn hẳn* mục quyền khi đủ ("đủ thì im lặng") vì nó là
     * một mục nhỏ giữa một trang dài — không ai muốn đọc danh sách những thứ đang chạy tốt. Ở đây thì khác: người
     * dùng đã **chủ động bấm vào nhóm "Hệ thống & quyền"**, nên một trang trắng trả lời sai câu họ vừa hỏi và trông
     * y như app hỏng. Luật *"đủ thì im lặng"* vẫn giữ nguyên ở chỗ nó thuộc về: thông báo lúc mở launcher
     * (`notice(coreOnly = true)`), không phải trang này.
     */
    private fun system(body: LinearLayout) {
        val rep = deps.permissions()
        body.addView(rows.sectionLabel("Quyền"))
        if (rep.allOk) {
            body.addView(rows.note("Đủ quyền — không thiếu gì. Kachi tự xin lại mỗi lần mở nếu hệ thống thu hồi."))
        } else {
            body.addView(rows.note("Kachi tự xin lại phần tự xin được; phần còn lại nói rõ việc cần làm."))
            rep.missing.forEach { body.addView(rows.permissionRow(it, rep)) }
        }
        body.addView(rows.sectionLabel("Khởi động"))
        body.addView(rows.checkRow(
            on = deps.state().autostart,
            title = "Tự mở khi nổ máy",
            sub = "Nổ máy là Kachi tự dựng lại ô và đặt mình làm màn hình chính. Tắt thì phải mở tay.",
        ) { on -> deps.onAutostart(on) })
    }

    // ── Dẫn đường · Cụm · Phím ───────────────────────────────────────────────────────────────────

    /** R5 — **chỉ dẫn sang**, không gom vào: màn ClusterNav (và layout XML của nó) đang niêm phong. */
    private fun clusterNav(body: LinearLayout) {
        body.addView(rows.sectionLabel("Dẫn đường · Cụm đồng hồ · Phím vô-lăng"))
        body.addView(rows.note(
            "Dẫn đường trên cụm, chiếu màn lên cụm, biển báo tốc độ, bóng VietMap, ghế mát/sưởi, lọc bụi PM2.5 và " +
                "gán phím vô-lăng nằm ở màn ClusterNav.",
        ))
        body.addView(rows.note(
            "Màn đó KHÔNG gom vào đây: nó đang niêm phong (không sửa một dòng), nên Cài đặt chỉ mở nó ra.",
        ))
        body.addView(rows.button("Mở màn ClusterNav") { deps.onOpenClusterNav() }, wrapLp())
    }

    // ── Giới thiệu ───────────────────────────────────────────────────────────────────────────────

    private fun about(body: LinearLayout) {
        body.addView(rows.sectionLabel("Giới thiệu"))
        body.addView(rows.note("Kachi — màn hình chính cho xe BYD DiLink."))
        body.addView(rows.note("Phiên bản ${BuildConfig.VERSION_NAME} (mã ${BuildConfig.VERSION_CODE})"))
        body.addView(rows.note("Tên gói: ${BuildConfig.APPLICATION_ID}"))
        body.addView(rows.note("Giấy phép MIT. Thử nghiệm cá nhân, KHÔNG liên kết với BYD."))
    }

    // ── Dùng chung ───────────────────────────────────────────────────────────────────────────────

    private fun wrapLp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
