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
import com.byd.clusternav.R
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
     * Đơn vị (7 loại) + **nút chọn chủ đề sáng/tối** (T1).
     *
     * ## ⚠ Đây từng là DÒNG CHỮ, không phải nút — và việc đổi lại là có bằng chứng
     * S1 cố ý không làm nút gạt vì [ĐO] lúc đó: `KachiTheme` khai 13 `const val` (**hằng biên dịch**, không đổi được
     * lúc chạy) + **82 mã hex viết cứng ở 21 tệp** + **không ai đọc `HomeUiState.themeMode` để vẽ** ⇒ nút sẽ lưu bền
     * đúng mà màn hình không đổi một pixel = **nút chết**, thứ mà `product-team-workflow.md` cấm.
     *
     * T1 bỏ cả ba tiền đề: hằng → thuộc tính tra bảng ([KachiTheme]), 82 hex → 0 hex ([KachiPalette] là chỗ duy nhất),
     * và [ThemeHost.sync] là người đọc `themeMode` để vẽ. Nên nay nút là nút THẬT — và bài canh
     * `SettingsScreenWiringContractTest` đã **đảo chiều**: hôm nay nó đỏ nếu chỗ này KHÔNG có nút.
     *
     * "Theo xe" = [ThemeMode.AUTO]: **tối 18h–6h**, không phải đọc cờ `uiMode` của hệ thống. Cố ý — launcher dựng
     * view bằng mã (không qua `values-night/`) nên nó không nhận được thông báo khi xe đổi chế độ; lấy theo giờ thì
     * kiểm được off-car và không phụ thuộc firmware. Câu chữ trên màn nói đúng điều đó, không hứa nhiều hơn.
     */
    private fun display(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_units)))
        var units = deps.state().unitPrefs
        UnitFormat.quantitiesInUse().forEach { q ->
            body.addView(rows.unitRow(q, units.unitFor(q)) { code ->
                units = units.with(q, code)
                deps.onUnitPrefs(units)
            })
        }
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_theme)))
        body.addView(rows.chipRow(
            label = context.getString(R.string.kachi_row_palette),
            options = ThemeMode.values().map { it.name to it.label() },
            current = deps.state().themeMode.name,
        ) { code -> deps.onThemeMode(ThemeMode.valueOf(code)) })
        body.addView(rows.note(context.getString(R.string.kachi_theme_note)))
        body.addView(rows.note(context.getString(R.string.kachi_theme_note_clusternav)))
        lang(body)
    }

    /**
     * U5·T3 — bộ chọn NGÔN NGỮ. Ba cách: Theo xe / Tiếng Việt / English (mặc định **Theo xe**, §6 OQ1).
     *
     * Cùng khuôn một chiều với nút chủ đề ngay trên: `deps.state().langMode` đọc từ nguồn sự thật →
     * `deps.onLangMode` là intent → `HomeViewModel` ghi bền → màn dựng lại. Tầng UI **0** lần ghi bền trực tiếp.
     *
     * ⚠ Nhãn hai thứ tiếng cụ thể ("Tiếng Việt"/"English") KHÔNG dịch — xem KDoc [LangMode.label]. Đặt ở nhóm
     * *Hiển thị* chứ không mở một nhóm mới: ngôn ngữ là **cách trình bày**, đúng định nghĩa của nhóm đó trong
     * [SettingsGroup.DISPLAY] (*"cách trình bày, không phụ thuộc bố cục"*).
     *
     * Câu thứ hai nói thẳng rằng màn ClusterNav **dùng chung** lựa chọn này — cố ý khác câu của bảng màu ngay trên
     * (bảng màu thì RIÊNG). Hai câu trái nhau nằm cạnh nhau trông như lỗi, nên nếu không nói rõ thì người dùng sẽ
     * suy ra sai một trong hai.
     */
    private fun lang(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_lang)))
        body.addView(rows.chipRow(
            label = context.getString(R.string.kachi_row_lang),
            options = LangMode.entries.map { it.name to it.label() },
            current = deps.state().langMode.name,
        ) { code -> deps.onLangMode(LangMode.valueOf(code)) })
        body.addView(rows.note(context.getString(R.string.kachi_lang_note)))
        body.addView(rows.note(context.getString(R.string.kachi_lang_note_clusternav)))
    }

    // ── Hồ sơ tài xế ─────────────────────────────────────────────────────────────────────────────

    private fun profiles(body: LinearLayout) {
        val s = deps.state()
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_profiles)))
        body.addView(rows.note(context.getString(R.string.kachi_profiles_note)))
        s.profiles.forEach { name ->
            body.addView(
                profileRow(name, active = name == s.activeProfile, total = s.profiles.size),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { it.bottomMargin = dpi(context, Sp.S) },
            )
        }
        body.addView(rows.button(context.getString(R.string.kachi_profiles_add)) { deps.onAddProfile() }, wrapLp())
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
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                    })
                    addView(TextView(context).apply {
                        text = context.getString(if (active) R.string.kachi_profile_active else R.string.kachi_profile_tap_switch)
                        setTextColor(c(if (active) KachiTheme.GREEN else KachiTheme.MUT))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    })
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            // ⚠ [SOÁT UI 2026-09-12] Nút "Xoá" CHỈ dựng khi thực sự xoá được: KHÔNG phải hồ sơ đang dùng VÀ còn hồ
            // sơ khác. Trước đây nút luôn hiện (chữ đỏ trần, cách tên ~1300px ở mép phải) rồi bấm ra toast "không xoá
            // được" — một hành động nguy hiểm lại mời bấm nhầm trên màn xe. Ẩn hẳn khi không xoá được thì KHÔNG còn
            // affordance để hiểu nhầm, nên không cần toast giải thích nữa (khác ca P9: ở đây không có kỳ vọng bị chặn
            // im lặng — người dùng đơn giản không thấy nút). Lưới an toàn thật vẫn nằm ở `WorkspacePrefs.deleteProfile`.
            if (!active && total > 1) addView(TextView(context).apply {
                text = context.getString(R.string.kachi_delete); setTextColor(c(KachiTheme.RED)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
                background = KachiTheme.card(context, Sp.RADIUS_PILL, KachiTheme.CLEAR, KachiTheme.RED)
                setOnClickListener { deps.onDeleteProfile(name) }
            })
            if (!active) setOnClickListener { deps.onSwitchProfile(name) }
        }

    // ── Tiện nghi xe ─────────────────────────────────────────────────────────────────────────────

    private fun car(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_comfort)))
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
        title = context.getString(R.string.kachi_recirc_title),
        // Chuỗi `kachi_recirc_sub` mang câu "chưa kiểm trên xe" (R10) — `Goi2FeatureWiringContractTest` đọc
        // CHÍNH tệp tài nguyên để chốt, nên câu cảnh báo không thể biến mất mà bài canh vẫn xanh.
        sub = context.getString(R.string.kachi_recirc_sub),
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
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_permissions)))
        if (rep.allOk) {
            body.addView(rows.note(context.getString(R.string.kachi_perm_all_ok)))
        } else {
            body.addView(rows.note(context.getString(R.string.kachi_perm_some_missing)))
            rep.missing.forEach { body.addView(rows.permissionRow(it, rep)) }
        }
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_boot)))
        body.addView(rows.checkRow(
            on = deps.state().autostart,
            title = context.getString(R.string.kachi_autostart_title),
            sub = context.getString(R.string.kachi_autostart_sub),
        ) { on -> deps.onAutostart(on) })
    }

    // ── Dẫn đường · Cụm · Phím ───────────────────────────────────────────────────────────────────

    /** R5 — **chỉ dẫn sang**, không gom vào: màn ClusterNav (và layout XML của nó) đang niêm phong. */
    private fun clusterNav(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_clusternav)))
        body.addView(rows.note(context.getString(R.string.kachi_clusternav_note1)))
        body.addView(rows.note(context.getString(R.string.kachi_clusternav_note2)))
        body.addView(rows.button(context.getString(R.string.kachi_clusternav_open)) { deps.onOpenClusterNav() }, wrapLp())
    }

    // ── Giới thiệu ───────────────────────────────────────────────────────────────────────────────

    private fun about(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_about)))
        body.addView(rows.note(context.getString(R.string.kachi_about_tagline)))
        body.addView(rows.note(context.getString(
            R.string.kachi_about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
        )))
        body.addView(rows.note(context.getString(R.string.kachi_about_package, BuildConfig.APPLICATION_ID)))
        body.addView(rows.note(context.getString(R.string.kachi_about_licence)))
    }

    // ── Dùng chung ───────────────────────────────────────────────────────────────────────────────

    private fun wrapLp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
