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

    /**
     * Nhóm "Phím vô-lăng" của lượt dựng gần nhất — giữ **chỉ** để còn đóng được phiên học lúc bảng đóng.
     *
     * ⚠ Đây KHÔNG phải một bảng tra `nhóm → section` (thứ mà KDoc [SettingsRows] cấm): đúng một nhóm có tài
     * nguyên sống ngoài cây view (listener của `VoiceKeyLearnBus`), và chỉ nhóm đó cần đường dọn. Trang được
     * **nhớ lại** ([SettingsPanel.pages]) nên thực thể ở đây chính là thực thể người dùng đang thấy.
     */
    private var keysSection: SettingsKeysSection? = null

    /**
     * Bảng đã rời khỏi cây view ⇒ trả lại mọi tài nguyên sống NGOÀI cây view.
     *
     * Hôm nay đúng một thứ: phiên "học phím mới" giữ listener của bus dùng chung (xem
     * [SettingsKeysSection.dispose]). View thì tự rụng theo `removeView`, nên không có gì khác phải dọn — và
     * chỗ này cố ý không làm gì hơn thế.
     */
    fun dispose() {
        keysSection?.dispose()
    }

    /** Trang của một nhóm. Mỗi nhóm **cuộn riêng** (R1) vì vỏ bảng giữ lại chính thực thể này. */
    fun build(group: SettingsGroup): View {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dpi(context, Sp.XS), dpi(context, Sp.L))
        }
        // ⚠ `when` **tường minh 10 nhánh, KHÔNG `else`**: thêm một nhóm vào `:core` mà quên dựng nội dung thì
        // **không biên dịch được**. Bản trước có `else -> clusterNav(body)` và [ĐO] nó nuốt gọn ba nhóm mới
        // (nav · cast · keys) — cả ba hiện ra một trang "mở màn ClusterNav" giống hệt nhau mà không gì báo lỗi.
        // Đó chính là "trang trắng im lặng" mà KDoc lớp này nói là phải chặn, chỉ khác màu.
        when (group) {
            SettingsGroup.HOME -> SettingsHomeSection(context, rows, deps).build(body)
            SettingsGroup.BARS -> SettingsBarsSection(context, rows, deps).build(body)
            SettingsGroup.DISPLAY -> display(body)
            SettingsGroup.PROFILES -> profiles(body)
            SettingsGroup.NAV -> SettingsNavSection(context, rows, deps).build(body)
            SettingsGroup.CAST -> SettingsCastSection(context, rows, deps).build(body)
            SettingsGroup.KEYS -> SettingsKeysSection(context, rows, deps).also { keysSection = it }.build(body)
            SettingsGroup.CAR -> SettingsCarSection(context, rows, deps).build(body)
            SettingsGroup.SYSTEM -> system(body)
            SettingsGroup.ABOUT -> about(body)
        }
        // ⚠ [SOÁT ẢNH 2026-09-12 · P1 #2] Thanh cuộn BẬT. Trang dài nhất đo được **10.5 màn** mà không có một chỉ
        // báo nào ⇒ trên xe, thứ không thấy là thứ không tồn tại: người dùng không biết dưới còn gì. Thân trang đã
        // chừa sẵn `paddingRight = Sp.XS` cho đúng thanh này (xem [SettingsPanel.head] — hai khối phải cùng một cột).
        return ScrollView(context).apply { addView(body); isVerticalScrollBarEnabled = true }
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
        // ⚠ IA v2 · R3 — dòng "màn ClusterNav có lựa chọn Sáng/Tối RIÊNG" đã XOÁ: từ nay một chip ghi CẢ HAI
        // store (`PrefsWorkspaceRepository.persist` gương sang `ThemeMode.setChoice`), nên câu đó nói SAI với
        // người dùng. `ThemeMirrorWiringContractTest` canh chuỗi `kachi_theme_note_clusternav` không còn tồn tại.
        body.addView(rows.note(context.getString(R.string.kachi_theme_note)))
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
        // Dòng "màn ClusterNav dùng chung lựa chọn này" cũng xoá: sau IA v2 chỉ còn MỘT màn cấu hình, nên không
        // còn "màn kia" nào để so — câu nhắc chỉ làm người đọc đi tìm một bề mặt đã biến mất.
        body.addView(rows.note(context.getString(R.string.kachi_lang_note)))
    }

    // ── Hồ sơ tài xế ─────────────────────────────────────────────────────────────────────────────

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
            if (!active && total > 1) addView(TextView(context).apply {
                text = context.getString(R.string.kachi_delete); setTextColor(c(KachiTheme.RED)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
                background = KachiTheme.card(context, Sp.RADIUS_PILL, KachiTheme.CLEAR, KachiTheme.RED)
                setOnClickListener { deps.onDeleteProfile(name) }
            })
            if (!active) setOnClickListener { deps.onSwitchProfile(name) }
        }

    // ── Tiện nghi xe ─────────────────────────────────────────────────────────────────────────────
    //
    // ⚠ Chuyển sang [SettingsCarSection] (T4): nhóm này nhận thêm ghế mát/sưởi + lọc bụi mịn từ màn ClusterNav
    // nên nó không còn là "một ô tick" nữa. Một tệp cho một nhóm — cùng lẽ với nav · cast · keys · bars.

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

        // ── Khởi động: HAI công tắc, hai NGHĨA khác nhau (IA v2 · R3) ──
        // [ĐO] kiểm kê 2026-09-12: `launcher_autostart` mở **màn hình** Kachi làm home, còn `headless_autostart`
        // chạy **dịch vụ** dẫn đường/cụm ở nền. Hai màn cũ đặt chúng ở hai nơi với câu chữ gần giống nhau ⇒ trông
        // như một cái bị lặp. Giữ cả hai (chúng làm hai việc thật), nhưng đứng CẠNH NHAU với nhãn nói đúng việc —
        // đó là cách duy nhất để người đọc thấy chúng khác nhau ở đâu.
        body.addView(rows.subHeader(context.getString(R.string.kachi_sec_boot)))
        body.addView(rows.checkRow(
            on = deps.state().autostart,
            title = context.getString(R.string.kachi_autostart_title),
            sub = context.getString(R.string.kachi_autostart_sub),
        ) { on -> deps.onAutostart(on) })
        body.addView(rows.checkRow(
            on = deps.bridge.headlessAutostart(),
            title = context.getString(R.string.kachi_headless_title),
            sub = context.getString(R.string.kachi_headless_sub),
        ) { on -> deps.bridge.setHeadlessAutostart(on) })

        // ── Bảo trì ──
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_maint)))
        // Nút kiểm tra cập nhật ĐỔI CHỮ theo kết quả (đang kiểm… / đã mới nhất / có bản mới): luồng này mất vài
        // giây trên mạng xe, và một nút im lặng vài giây thì người dùng bấm lại lần hai.
        val update = rows.button(context.getString(R.string.kachi_check_update)) {} as TextView
        update.setOnClickListener { deps.bridge.checkUpdate { text -> update.text = text } }
        body.addView(update)
        body.addView(rows.button(context.getString(R.string.kachi_nav_stop)) { deps.bridge.navStop() })

        // ── Nâng cao ──
        // ⚠ Dòng "Màn nâng cao (ClusterNav)" đã XOÁ 2026-09-13: màn cũ bị gỡ hẳn (S3 · R1 —
        // docs/specs/kachi-remove-legacy-screen.html). Hai mục còn lại là hai màn CHẨN ĐOÁN thật, không phải
        // hai bề mặt cấu hình song song — nên mục này vẫn có nghĩa.
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_advanced)))
        body.addView(rows.button(context.getString(R.string.kachi_vietmap_data)) { deps.bridge.openVietMapData() })
        body.addView(rows.button(context.getString(R.string.kachi_diagnostics)) { deps.bridge.openDiagnostics() })
        // V1 · R6 — đường thử lệnh bằng CHỮ. Đặt ở "Nâng cao" cạnh hai màn chẩn đoán kia vì nó cùng loại: một chỗ
        // ĐO, không phải một bề mặt cấu hình (xem KDoc [VoiceTextConsole] về vì sao không cho nó một nhóm riêng).
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_voice_title)))
        VoiceTextConsole(context, rows, deps).build(body)
    }

    // ── Dẫn đường · Cụm · Phím ───────────────────────────────────────────────────────────────────
    //
    // ⚠ `private fun clusterNav(body)` đã XOÁ (IA v2 đảo R5 của `kachi-settings-screen.html`). Nó dựng một trang
    // "màn ClusterNav đang niêm phong, Cài đặt chỉ mở nó ra" — câu đó nay SAI ở cả hai vế: ba nhóm nav · cast ·
    // keys đã dựng lại đủ điều khiển (ghi cùng khoá), còn đường mở màn cũ thì nằm ở mục "Nâng cao" của nhóm Hệ
    // thống. Ba chuỗi của nó (`kachi_sec_clusternav`, `kachi_clusternav_note1/note2`, `kachi_clusternav_open`) xoá
    // khỏi cả hai tệp tài nguyên — `LauncherI18nContractTest.khong co khoa tai nguyen mo coi` canh hai chiều.

    // ── Giới thiệu ───────────────────────────────────────────────────────────────────────────────

    private fun about(body: LinearLayout) {
        // Không `sectionLabel` — cùng lý do với nhóm Hồ sơ, xem chú thích ở `profiles` (finding #21).
        body.addView(rows.note(context.getString(R.string.kachi_about_tagline)))
        body.addView(rows.note(context.getString(
            R.string.kachi_about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
        )))
        body.addView(rows.note(context.getString(R.string.kachi_about_package, BuildConfig.APPLICATION_ID)))
        body.addView(rows.note(context.getString(R.string.kachi_about_licence)))
        // Tuyên bố miễn trừ — chuyển từ hộp thoại MỘT LẦN của màn cũ (`MainActivity.maybeShowDisclaimer`, gác bằng
        // `disclaimer_shown`) thành một dòng ĐỌC LẠI ĐƯỢC BẤT CỨ LÚC NÀO. Hộp thoại một-lần trả lời đúng câu hỏi
        // pháp lý *"đã báo chưa"* nhưng sai câu hỏi của người dùng *"cái này là gì, ai chịu trách nhiệm"* — hỏi
        // vào tháng thứ ba thì không còn chỗ nào để đọc lại. Cờ `disclaimer_shown` vẫn thuộc màn cũ (nó là trạng
        // thái "đã hiện chưa", không phải một lựa chọn) nên mục này KHÔNG nhận khoá.
        body.addView(rows.note(context.getString(R.string.kachi_about_disclaimer)))
    }

    // ── Dùng chung ───────────────────────────────────────────────────────────────────────────────

    // ⚠ [SOÁT ĐỘC LẬP 2026-09-12] `private fun toast(...)` đã XOÁ ở đây: chỗ gọi DUY NHẤT của nó là nhánh chặn xoá
    // hồ sơ, và nhánh đó biến mất khi nút Xoá chuyển sang "chỉ dựng khi xoá được thật". Kotlin không báo lỗi cho hàm
    // private không ai gọi, nên nó sẽ ở lại im lặng — đúng loại nợ mà dự án đã phải đi dọn (`cycleDockEdge`,
    // `LauncherRequirements.notice`). Cần nói một câu ở màn Cài đặt thì dựng lại một dòng, KHÔNG để hàm chờ sẵn.

    private companion object {
        /**
         * Mã của chip **"Gần nhất"** (không ghim hồ sơ nào lúc nổ máy).
         *
         * Ở tầng dữ liệu ca này là `null` ([WorkspaceRepository.bootProfile]) — nơi lưu cần phân biệt *"chưa chọn"*
         * với *"chọn hồ sơ tên X"*, và tên hồ sơ do người dùng đặt nên không được đụng phải một tên dành riêng.
         * [SettingsRows.chipRow] thì so **mã chuỗi** nên nó không nhận `null` được. Hai lớp, hai cách biểu diễn,
         * quy đổi ở ĐÚNG một chỗ ([bootProfile]) — không để `null` và sentinel cùng chạy qua nhiều tầng.
         */
        const val BOOT_LAST_CODE = "__LAST__"
    }
}
