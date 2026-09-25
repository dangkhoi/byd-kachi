package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ U5 · T2 — BÀI CANH NHÃN TIẾNG ANH ════════════════════════════════════════════════════════════════════════
 *
 * ## Bệnh nó chữa
 * Nhãn tiếng Anh là **dữ liệu gắn vào từng dòng registry** ([Strings] KDoc giải thích vì sao). Dữ liệu thì **quên
 * được**: thêm một datum mới mà không điền `labelEn` sẽ không làm gì đỏ, launcher vẫn chạy, và cái sai chỉ lộ ra
 * dưới dạng *"một ô nói tiếng Việt giữa màn tiếng Anh"* — mà **chỉ người dùng English gặp**, tức owner sẽ không
 * thấy. Bài này biến chuyện quên đó thành **đỏ off-car**.
 *
 * ## Vì sao ĐẾM TUYỆT ĐỐI, không chỉ `forEach { assertNotNull }`
 * Vòng lặp trên một danh sách chỉ chứng minh *"những gì đang có đều có nhãn"*. Nó **không** bắt được ca danh sách bị
 * co lại (ai đó bỏ một bộ đăng ký khỏi phép quét, hoặc [Localized] bị tháo khỏi một lớp) — lúc đó vòng lặp quét ít
 * hơn và vẫn xanh. Ghim số là cách duy nhất để *"quét thiếu"* cũng đỏ. Đây đúng bài học của dự án về **dấu xanh
 * giả**: thêm tệp MỚI thì đỏ đúng, còn thứ **mất đi** thì im lặng.
 *
 * ## Ba luật về CHẤT bản dịch (không chỉ "có hay không")
 *  1. **Không được trùng y nguyên nhãn Việt** — vì chép nguyên là cách "điền cho xong". Trừ [SAME_ON_PURPOSE]: ký
 *     hiệu ngành (VIN · PM2.5 · EV/HEV) thì dịch mới là sai (spec §6 OQ2).
 *  2. **Không được chứa dấu tiếng Việt** — bắt ca dịch nửa vời (*"Tyre pressure trước-trái"*).
 *  3. **Nhãn ngắn phải THẬT ngắn** — chip thanh trạng thái cao ~24dp; nhãn ngắn dài bằng nhãn đầy thì `shortEn` vô
 *     nghĩa và chữ bị cắt (đúng lỗi [ĐO] 2026-09-10: *"Áp lốp trước-t…"* × 2 không phân biệt được).
 *
 * ## ⚠ TỰ DỌN [Strings.current] — bắt buộc
 * [Strings.current] là `var` toàn cục. Bài nào đổi nó mà không dọn sẽ làm **bài chạy sau** đọc nhãn tiếng Anh trong
 * khi nó assert tiếng Việt ⇒ đỏ ở một tệp **không liên quan**, và chạy riêng lẻ thì lại xanh. Đó là loại lỗi rất tốn
 * thời gian để lần ra, nên [dọn] chạy sau MỌI bài ở đây (kể cả bài không đổi gì) và có một bài riêng chứng minh việc
 * đổi-rồi-trả-về không để lại vết.
 */
class LangCoverageTest {

    /** Chạy sau MỌI bài — kể cả bài không chạm [Strings.current], để không phải nhớ bài nào có chạm. */
    @AfterEach
    fun `dọn`() {
        Strings.current = Lang.VI
    }

    // ── 1 · ĐẾM TUYỆT ĐỐI: không mã nào thiếu nhãn EN ────────────────────────────────────────────

    @Test
    fun `moi datum co nhan EN, dung 73 dong`() {
        // 106 (2026-09-16 owner gỡ ADAS/an toàn — trước đó 123).
        // 100 ((V) FEATURE-FILTER 2026-09-17 owner gỡ 12 datum NO — trước đó 112).
        // 1.85: +1 `ac_wind_auto` (đã có nhãn + nhãn ngắn ở CẢ hai thứ tiếng — chính bài này ép điều đó).
        // WP8 2026-09-20: 102 → 73 (purge 29 datum BỎ) → 1.90: 71 (gỡ `op_mode` + `energy_mode`, xe thuần điện).
        // 2026-09-25: 71 → 64 (owner gỡ 7 datum CHẾT — nhật ký ở TelemetryRegistry).
        assertEquals(64, TelemetryRegistry.ALL.size, "số datum đổi ⇒ xem lại bản dịch trước khi ghim số mới")
        val missing = TelemetryRegistry.ALL.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "datum thiếu nhãn tiếng Anh: $missing")
    }

    @Test
    fun `moi nut co nhan EN, dung 39 nut`() {
        // 39 (UX-OVERHAUL WP8 2026-09-20 owner purge 8 nút BỎ: gạt mưa · 4 đèn viền · mức tái tạo · 2 HUD).
        // 38 (gỡ `seat_memory`) → 29 (1.90: 9 nút xe-thuần-điện; danh sách ở `WorkspaceStateTest`).
        assertEquals(33, ControlRegistry.ALL.size, "số nút đổi ⇒ xem lại bản dịch trước khi ghim số mới")
        val missing = ControlRegistry.ALL.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "nút thiếu nhãn tiếng Anh: $missing")
    }

    @Test
    fun `moi nhom co nhan va dong phu EN, dung 8 nhom`() {
        // 9 (2026-09-16 owner gỡ ADAS/an toàn — trước đó 12 — bỏ g_adas · g_occupants · g_parking).
        // 8 (WP8 2026-09-20 gỡ g_ambient — hết thành viên sau khi purge 5 datum + 4 nút đèn viền).
        assertEquals(8, CapabilityGroups.ALL.size)
        val missing = CapabilityGroups.ALL
            .filter { it.labelEn.isNullOrBlank() || it.subEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "nhóm thiếu labelEn/subEn: $missing")
    }

    @Test
    fun `moi widget co nhan EN, dung 9 widget`() {
        assertEquals(9, WidgetRegistry.ALL.size)
        val missing = WidgetRegistry.ALL.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "widget thiếu nhãn tiếng Anh: $missing")
    }

    @Test
    fun `moi goi lenh co nhan EN, dung 4 goi`() {
        assertEquals(2, ActionMacros.ALL.size)
        val missing = ActionMacros.ALL.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(missing.isEmpty(), "gói lệnh thiếu nhãn tiếng Anh: $missing")
    }

    @Test
    fun `moi muc cai dat co nhan EN — 11 nhom va 79 muc`() {
        assertEquals(11, SettingsCatalog.GROUPS.size)   // +VOICE (owner 2026-09-21 tách menu Giọng nói riêng)
        // 54 = 20 (IA v1) + 36 mục dựng lại từ màn ClusterNav (IA v2 §4.3: nav 11 · cast 9 · keys 5 · car 5 thêm ·
        // system 6 thêm · about 1 thêm), trừ `clusternav_open` (IA v2), trừ `system_advanced_screen` (S3 2026-09-13:
        // màn cũ gỡ hẳn), rồi S4 · R1/R6: **−3** mục cảnh (`home_scenes` · `home_scene_boot` · `home_scene_save`)
        // **+2** mục hồ sơ (`profiles_boot` · `profiles_add`), rồi T-BRIDGE **+1** (`system_test_bridge` — công
        // tắc chế độ kiểm thử qua adb, docs/specs/kachi-test-bridge.html), rồi S5 **+2** (`system_default_home` —
        // nút Đặt Kachi làm màn hình chính, và `system_keep_home_on_boot` — công tắc giữ khi nổ máy).
        // S1b (2026-09-14): **+1** (`bars_dock_visible` — công tắc ẩn/hiện thanh nút xe).
        // A1 (2026-09-15, docs/specs/kachi-voice-addresses.html): **+2** — `places_list` (khoá `saved_places`) và
        // `places_add` (nút, không khoá). Cả hai nằm ở nhóm NAV dù khoá thuộc phía launcher: nhóm chia theo thứ
        // người dùng nghĩ tới, không theo tệp lưu (xem KDoc `SettingsGroup`).
        // Voice pha 2 (2026-09-16, docs/specs/kachi-voice-feedback.html R4/T8): **+3** — `voice_speak_replies` và
        // `voice_prefer_offline` (hai khoá của `Prefs`, theo XE) + `voice_tts_pack` (nút tải gói giọng, không khoá).
        // V3 (1.66): +4 mục — `voice_confirm_ids` · `voice_ask_aloud` · `voice_mic_source` · `bars_top_strip_labels`.
        // H2/H6 (1.69): **+3** — `voice_keep_log` (ô tích giữ nhật ký lượt nói, khoá THEO XE), `voice_log_export`
        // (nút nén `voice-log/` ra `Download/`, không khoá) và `voice_model_light` (hai nút đổi/gỡ mô hình nghe,
        // không khoá — lựa chọn mô hình lưu ở tệp prefs riêng `kachi_voice` của `VoiceModelStore`).
        // VISUAL-REFRESH P1b · R8 (2026-09-17): **+1** — `display_color` (màu nhấn + tông thẻ, khoá `color_choice`
        // theo hồ sơ; docs/specs/kachi-visual-refresh.html §R8).
        // voice-clone T7/T8 (2026-09-17): **+1** — `voice_feedback_voice` (ô tích chọn giọng phản hồi Piper/giọng bé).
        // AUTOMATION (1.85, spec kachi-automation): **+2** — `car_rain_defrost` (công tắc tự sấy kính khi mưa,
        // khoá `rain_defrost_enabled`) + `nav_automation` (sổ luật tự dẫn đường theo lịch, khoá
        // `nav_automation_rules`). Cả hai theo XE.
        // UX-OVERHAUL WP1 (2026-09-20): **+1** — `display_glass_real` (công tắc "Kính thật (làm mờ nền)", khoá
        // `ui_glass_real`, nhóm Hiển thị, theo XE) ⇒ 75 → 76.
        // Release production (owner 2026-09-21): **81 → 77 (−4)** = dọn hết dev/debug UI khỏi màn Cài đặt, chỉ giữ
        // công tắc `system_test_bridge`. Bốn mục rời danh mục vì chúng không còn hàng nào trên màn:
        // `system_vietmap_data` · `system_diagnostics` (hai màn chẩn đoán) · `voice_keep_log` · `voice_log_export`
        // (ô tích + nút xuất nhật ký lượt nói). KHẢ NĂNG không mất — cả bốn vẫn chạy qua adb (`am start` cho hai màn,
        // `prefs_set`/`voice_dump` cho hai cái kia); khoá `voice_keep_log` chuyển sang HIDDEN_KEYS kèm lý do.
        // Một-mô-hình-nghe (owner 2026-09-21, cùng bản): **77 → 76 (−1)** = `voice_model_light` (hai nút *chuyển
        // sang mô hình nhẹ* / *gỡ bản nặng*). Danh mục mô hình nghe thu về ĐÚNG MỘT gói (`SherpaModelCatalog.ALL`,
        // owner: *"chỉ giữ model đang OK trên xe, không thử nghiệm gì nữa"*) ⇒ bề mặt chọn-mô-hình không còn gì để
        // chọn giữa, `VoiceModelSettings.lightModelRows` gỡ. Hàng *trạng thái + Tải/Gỡ* của gói duy nhất Ở LẠI
        // (nó chưa bao giờ là một mục danh mục — nó thuộc khối dựng tay cùng `voice_tts_pack`).
        assertEquals(75, SettingsCatalog.ENTRIES.size)
        val badGroups = SettingsCatalog.GROUPS.filter { it.labelEn.isBlank() || it.subEn.isBlank() }.map { it.id }
        assertTrue(badGroups.isEmpty(), "nhóm cài đặt thiếu labelEn/subEn: $badGroups")
        val badEntries = SettingsCatalog.ENTRIES.filter { it.labelEn.isNullOrBlank() }.map { it.id }
        assertTrue(badEntries.isEmpty(), "mục cài đặt thiếu nhãn tiếng Anh: $badEntries")
    }

    @Test
    fun `cac enum mang nhan cung co ban EN`() {
        // 8 (2026-09-16 owner gỡ ADAS/an toàn — trước đó 9 — bỏ Domain.SAFETY).
        assertEquals(8, Domain.values().size)
        assertEquals(7, Quantity.values().size)
        assertEquals(4, TyreCorner.values().size)
        // V1 pha NGHE: +1 — `microphone` (quyền micro, tự cấp bằng `pm grant`).
        // AUTOMATION #2 (1.85): +1 — `location` (quyền ĐỌC định vị cho cổng "đã ra khỏi hầm chưa", cùng đường
        // `pm grant`; CHỈ đọc — xem `DeadReckonRetirementTest.the app never writes or subscribes to location`).
        assertEquals(8, LauncherRequirements.ALL.size)
        Domain.values().forEach { assertTrue(it.labelEn.isNotBlank(), "Domain.${it.name} thiếu nhãn EN") }
        Quantity.values().forEach { assertTrue(it.labelEn.isNotBlank(), "Quantity.${it.name} thiếu nhãn EN") }
        TyreCorner.values().forEach {
            assertTrue(it.labelEn.isNotBlank(), "TyreCorner.${it.name} thiếu nhãn EN")
            assertTrue(it.shortLabelEn.isNotBlank(), "TyreCorner.${it.name} thiếu viết tắt EN")
        }
        LauncherRequirements.ALL.forEach {
            assertNotNull(it.labelEn, "điều kiện ${it.id} thiếu nhãn EN")
            assertNotNull(it.losesWhatIfMissingEn, "điều kiện ${it.id} thiếu câu 'mất gì' bằng EN")
            // Chỉ ca cần người dùng mới có việc-cần-làm; ca đó thì bản EN là bắt buộc.
            if (it.userAction != null) assertNotNull(it.userActionEn, "điều kiện ${it.id} thiếu việc-cần-làm EN")
        }
    }

    /**
     * Phép quét GỘP: mọi thứ mang [Localized] phải có nhãn EN, và **tổng số phải khớp**.
     *
     * Đây là bài bắt ca *"một bộ đăng ký lặng lẽ rơi khỏi tầm quét"*: sáu bài trên mỗi bài canh một bộ, nên bỏ hẳn
     * `Localized` khỏi một lớp sẽ làm lớp đó không còn ở đây mà **không bài nào đỏ** nếu không ghim tổng.
     */
    @Test
    fun `tong so nhan co ban EN dung 255`() {
        val all: List<Localized> = TelemetryRegistry.ALL + ControlRegistry.ALL + CapabilityGroups.ALL +
            WidgetRegistry.ALL + ActionMacros.ALL + SettingsCatalog.GROUPS + SettingsCatalog.ENTRIES +
            Domain.values().toList() + Quantity.values().toList() + TyreCorner.values().toList() +
            LauncherRequirements.ALL + LauncherActions.ALL + HeaderItem.values().toList()
        // 302 = 265 + 3 nhóm mới (nav · cast · keys — `bars` bù cho `clusternav` bị bỏ) + 36 mục mới của IA v2,
        // trừ 1 mục `system_advanced_screen` gỡ cùng màn ClusterNav cũ (S3 · 2026-09-13), rồi S4 · R1/R6 −3 mục
        // cảnh +2 mục hồ sơ.
        // S4 · R12: +2 hành động launcher ([LauncherActions]) — chúng mang [Localized] nên PHẢI nằm trong tầm quét
        // này, không thì một bộ đăng ký mới có nhãn chưa dịch mà không bài nào thấy.
        // V1 pha NGHE: +2 — `launcher_voice` *"Nói với xe" · "Talk to car"* và điều kiện `microphone`.
        // T-BRIDGE: +1 — mục `system_test_bridge` (công tắc "Chế độ kiểm thử qua adb").
        // S5: +2 — `system_default_home` (nút Đặt Kachi làm màn hình chính) + `system_keep_home_on_boot` (công tắc).
        // A1 (2026-09-15): +2 — `places_list` + `places_add` (sổ địa chỉ, docs/specs/kachi-voice-addresses.html).
        // ⚠ TÊN BÀI đã lệch số từ trước lượt này (tên nói 307 trong khi ghim 310); nay đặt lại cho khớp —
        // một cái tên nói sai con số nó đang canh là chỗ người sau đọc rồi tin nhầm.
        // Voice pha 2 (2026-09-16): +3 — `voice_speak_replies` · `voice_prefer_offline` · `voice_tts_pack`.
        // 288 (2026-09-16 owner gỡ ADAS/an toàn — trước đó 319).
        // VOICE-HOTFIX 1.69: **288 → 291 (+3)** = ba mục Cài đặt mới của đường giọng nói (`voice_keep_log` giữ
        // nhật ký lượt nói · `voice_log_export` xuất zip · `voice_model_light` đổi sang mô hình int8). Cả ba đã
        // có nhãn ở CẢ hai thứ tiếng — chính bài này là thứ ép điều đó, nên con số chỉ được ghim SAU khi dịch.
        // H1 · T2 (2026-09-16): **291 → 297 (+6)** = sáu datum mới cho đường ĐỌC của nút (`seat_vent_state`
        // `seat_heat_state` `defrost_front_state` `defrost_rear_state` `ac_mode_auto` `media_vol`). Cả sáu có nhãn
        // ở CẢ hai thứ tiếng + nhãn ngắn — chính bài này ép điều đó, nên số chỉ được ghim SAU khi đã dịch.
        // (V) FEATURE-FILTER (2026-09-17): **297 → 278 (−19)** = đúng 19 mã owner chấm NO (12 datum + 7 nút).
        // VISUAL-REFRESH P1b · R8 (2026-09-17): **278 → 279 (+1)** = mục Cài đặt `display_color` ("Màu sắc" / "Colours").
        // voice-clone T7/T8 (2026-09-17): **279 → 280 (+1)** = mục Cài đặt `voice_feedback_voice` ("Giọng phản hồi" / "Feedback voice").
        // nav-default-app (owner 2026-09-18): **280 → 281 (+1)** = mục `nav_default_app` ("App dẫn đường mặc định" / "Default navigation app").
        // 1.85 (2026-09-20): **282 → 283 (+1)** = +1 datum `ac_wind_auto`, +1 nút `child_lock_r`, −1 nút `hood`.
        // 1.85 AUTOMATION (2026-09-20): **283 → 286 (+3)** = +2 mục cài đặt (`car_rain_defrost` ·
        // `nav_automation`) +1 điều kiện quyền (`location`). Ba nhãn mới đều đã có bản EN tại chỗ khai.
        // UX-OVERHAUL WP1 (2026-09-20): **286 → 287 (+1)** = mục `display_glass_real` ("Kính thật (làm mờ nền)" /
        // "Real glass (blur the backdrop)"), đã có bản EN tại chỗ khai.
        // UX-OVERHAUL WP4 (2026-09-20): **287 → 295 (+8)** = +2 mục cài đặt (`bars_header_order` ·
        // `bars_dock_order`) **và +6 [HeaderItem]** — bộ đăng ký MỚI mang [Localized]. Đưa nó vào danh sách quét
        // ngay lượt này là đúng việc bài này sinh ra để làm (KDoc trên): một bộ mang nhãn mà không nằm trong tầm
        // quét thì nó có thể thiếu bản EN mà không bài nào đỏ.
        // UX-OVERHAUL WP6 (2026-09-20): **295 → 296 (+1)** = mục `cast_bubble` ("Hiện nút nổi chiếu cụm" / "Show
        // the floating cast button"), khoá `cast_bubble_visible` theo XE; đã có bản EN tại chỗ khai.
        // UX-OVERHAUL WP8 (2026-09-20): **296 → 258 (−38)** = −29 datum −8 nút (owner purge 37 mã BỎ) −1 nhóm
        // (`g_ambient` hết thành viên). Đây là lượt duy nhất con số này GIẢM; mọi lượt trước đều cộng.
        // Release production (owner 2026-09-21): **260 → 256 (−4)** = bốn mục Cài đặt rời danh mục cùng lượt dọn
        // dev/debug UI (`system_vietmap_data` · `system_diagnostics` · `voice_keep_log` · `voice_log_export`) — xem
        // lý do đầy đủ ở `tong so muc Cai dat…` phía trên. Lượt GIẢM thứ hai của con số này.
        // Một-mô-hình-nghe (owner 2026-09-21, cùng bản): **256 → 255 (−1)** = mục `voice_model_light`. Danh mục mô
        // hình nghe thu về đúng một gói ⇒ bề mặt chọn-mô-hình gỡ khỏi Cài đặt. Lượt GIẢM thứ ba.
        // 1.90: **255 → 244 (−11)** = −9 nút −2 datum (xe thuần điện). Lượt GIẢM thứ tư.
        // 2026-09-25: **245 → 238 (−7)** = 7 datum CHẾT bị gỡ. Lượt GIẢM thứ năm.
        assertEquals(238, all.size, "số nhãn đổi — thêm mã mới thì phải dịch, rồi mới ghim số mới")
        val missing = all.filter { it.labelEn.isNullOrBlank() }.map { it.label }
        assertTrue(missing.isEmpty(), "còn nhãn chưa có bản EN: $missing")
    }

    // ── 2 · CHẤT bản dịch ───────────────────────────────────────────────────────────────────────

    @Test
    fun `nhan EN khong trung y nguyen nhan VI`() {
        val copied = localizedRows()
            .filter { it.labelEn == it.label && it.label !in SAME_ON_PURPOSE }
            .map { it.label }
        assertTrue(
            copied.isEmpty(),
            "nhãn EN chép y nguyên nhãn VI (điền cho xong?) — nếu là ký hiệu ngành thì khai vào SAME_ON_PURPOSE " +
                "kèm lý do: $copied",
        )
    }

    @Test
    fun `moi muc trong danh sach cho phep trung deu co ly do, va deu dung toi`() {
        SAME_ON_PURPOSE.forEach { (term, why) ->
            assertTrue(why.isNotBlank(), "'$term' được phép trùng thì phải nói LÝ DO, không thì đây là chỗ làm im bài")
        }
        // Danh sách cho phép mà không còn ai dùng = rác tích lại, và nó nới lỏng bài canh cho lần sau.
        val everyString = localizedRows().flatMap { listOf(it.label, it.labelEn ?: "") } +
            ControlRegistry.ALL.flatMap { it.args + it.argsEn } +
            TelemetryRegistry.ALL.flatMap { listOfNotNull(it.short, it.shortEn) }
        val unused = SAME_ON_PURPOSE.keys.filterNot { it in everyString }
        assertTrue(unused.isEmpty(), "mục cho phép trùng không còn ai dùng ⇒ xoá đi: $unused")
    }

    @Test
    fun `nhan EN khong chua dau tieng Viet`() {
        val dirty = ArrayList<String>()
        localizedRows().forEach { row ->
            row.labelEn?.let { if (hasVietnameseMark(it)) dirty.add("${row.label} → $it") }
        }
        CapabilityGroups.ALL.forEach { g ->
            g.subEn?.let { if (hasVietnameseMark(it)) dirty.add("${g.id}.subEn → $it") }
        }
        SettingsCatalog.GROUPS.forEach { g ->
            if (hasVietnameseMark(g.subEn)) dirty.add("${g.id}.subEn → ${g.subEn}")
        }
        TelemetryRegistry.ALL.forEach { s ->
            s.shortEn?.let { if (hasVietnameseMark(it)) dirty.add("${s.id}.shortEn → $it") }
        }
        ControlRegistry.ALL.forEach { c ->
            c.argsEn.filter { hasVietnameseMark(it) }.forEach { dirty.add("${c.id}.argsEn → $it") }
        }
        LauncherRequirements.ALL.forEach { r ->
            listOfNotNull(r.losesWhatIfMissingEn, r.userActionEn)
                .filter { hasVietnameseMark(it) }.forEach { dirty.add("${r.id} → $it") }
        }
        TyreCorner.values().forEach {
            if (hasVietnameseMark(it.labelEn) || hasVietnameseMark(it.shortLabelEn)) dirty.add("TyreCorner.${it.name}")
        }
        assertTrue(dirty.isEmpty(), "bản EN còn dấu tiếng Việt (dịch nửa vời): $dirty")
    }

    @Test
    fun `lua chon cua nut co ban EN dung so phan tu`() {
        val withArgs = ControlRegistry.ALL.filter { it.args.isNotEmpty() }
        // 13 = 5 nút COVER kính/rèm (mỗi cái "Đóng"/"Mở") + 8 nút SELECT. ⚠ [ĐO] con số tôi ĐOÁN lúc viết bài này là
        // 11 và bài đỏ ngay — đúng việc nó sinh ra để làm, và là lời nhắc rằng đếm bằng mắt qua một tệp 355 dòng thì
        // sai. Giữ số đo, không giữ số đoán. 13 (2026-09-16 owner gỡ ADAS/an toàn — trước đó 14 — nút SELECT `adas_lane` đã xoá).
        // 12 ((V) 2026-09-17: nút SELECT `drive_mode` đã xoá — trước đó 13).
        // 12 (WP8 2026-09-20: hai nút SELECT `ambient_color` + `regen_level` đã purge — trước đó 14).
        // 8 (1.90: 4 SELECT headlight_mode/powertrain_mode/screen_rotation/camera_view xoá)
        assertEquals(5, withArgs.size, "số nút có lựa chọn đổi ⇒ xem lại bản dịch")
        val bad = withArgs.filter { it.argsEn.size != it.args.size }.map { "${it.id}(${it.args.size}≠${it.argsEn.size})" }
        assertTrue(bad.isEmpty(), "lựa chọn EN thiếu/lệch số phần tử — sẽ lùi về CẢ danh sách tiếng Việt: $bad")
    }

    @Test
    fun `nhan ngan EN phai that ngan`() {
        val tooLong = ArrayList<String>()
        TelemetryRegistry.ALL.forEach { s ->
            // (a) `shortEn` đã khai thì phải ngắn.
            s.shortEn?.let { if (it.length > SHORT_CAP) tooLong.add("${s.id}.shortEn='$it'(${it.length})") }
            // (b) Datum nào cần viết tắt ở tiếng Việt thì bản EN **thực dùng** (kể cả khi lùi về `labelEn`) cũng phải
            //     ngắn — không thì chip tiếng Anh bị cắt đúng chỗ chip tiếng Việt vừa được chữa.
            if (s.short != null) {
                val en = s.shortLabelIn(Lang.EN)
                if (en.length > SHORT_CAP) tooLong.add("${s.id} EN short='$en'(${en.length})")
            }
        }
        TyreCorner.values().forEach {
            if (it.shortLabelEn.length > 3) tooLong.add("TyreCorner.${it.name}='${it.shortLabelEn}'")
        }
        assertTrue(tooLong.isEmpty(), "nhãn ngắn EN dài quá $SHORT_CAP ký tự (chip ~24dp sẽ cắt chữ): $tooLong")
    }

    /**
     * Bản dịch KHÔNG được sinh ra cặp nhãn trùng MỚI.
     *
     * [ĐO] khi làm T2: ba nhãn `Đèn viền` (nhóm) / `Đèn viền cabin` (datum) / `Đèn viền cabin` (nút) — bản dịch đầu
     * của tôi cho **cả ba** thành `"Ambient lighting"`. Bản Việt chỉ trùng MỘT cặp (datum↔nút, và cặp đó đã có gợi ý
     * loại `· xem`/`· bấm`), nên tiếng Anh sinh ra một cặp trùng thứ hai **không ai gợi ý** — vì phép phát hiện trùng
     * chạy trên nhãn Việt (xem KDoc [CapabilityPick.displayLabel]). Hậu quả: hai ô chữ y hệt nhau, chỉ người dùng
     * English gặp.
     *
     * ## ⚠⚠ [ĐO] BÀI NÀY TỪNG KHÔNG BẮT ĐƯỢC CHÍNH CA NÓ SINH RA ĐỂ BẮT
     * Bản đầu hỏi *"trong nhóm trùng ở EN, có mục nào ĐÃ trùng ở VI không"* — nếu có thì tha cả nhóm. Dựng lại đúng
     * lỗi cũ (nhóm `g_ambient` mang nhãn của datum) thì bài **VẪN XANH**, vì hai mục kia đúng là đã trùng ở VI.
     * Phép đúng phải là **theo TỪNG CẶP**: trong một nhóm trùng ở EN thì **mọi** nhãn VI phải giống nhau — tức chúng
     * vốn đã là cùng một cặp trùng, không phải "có họ hàng với một cặp trùng". Đây là lời nhắc rằng thử-phá không chỉ
     * kiểm mã sản phẩm, nó kiểm cả bài canh.
     */
    @Test
    fun `ban dich khong sinh ra nhan trung MOI`() {
        val rows = CapabilityGroups.ALL.map { it.id to (it.label to it.labelEn) } +
            WidgetRegistry.ALL.map { it.id to (it.label to it.labelEn) } +
            TelemetryRegistry.ALL.map { it.id to (it.label to it.labelEn) } +
            ControlRegistry.ALL.map { it.id to (it.label to it.labelEn) } +
            ActionMacros.ALL.map { it.id to (it.label to it.labelEn) } +
            LauncherActions.ALL.map { it.id to (it.label to it.labelEn) }
        val newlyColliding = rows
            .groupBy { it.second.second ?: it.second.first }         // gom theo nhãn EN thực dùng
            .filterValues { group -> group.size > 1 && group.map { it.second.first }.distinct().size > 1 }
            .map { (en, group) -> "$en ← ${group.map { it.first }} (VI: ${group.map { it.second.first }.distinct()})" }
        assertTrue(
            newlyColliding.isEmpty(),
            "bản dịch làm hai mục KHÁC NHAU thành cùng một chữ (tiếng Việt chúng khác nhau ⇒ không có gợi ý loại " +
                "nào phân biệt): $newlyColliding",
        )
    }

    // ── 3 · Cơ chế chọn ngôn ngữ ─────────────────────────────────────────────────────────────────

    @Test
    fun `t tra ve theo ngon ngu dang chon`() {
        Strings.current = Lang.VI
        assertEquals("xin chào", Strings.t("xin chào", "hello"))
        Strings.current = Lang.EN
        assertEquals("hello", Strings.t("xin chào", "hello"))
        // Dạng có tham số tường minh KHÔNG phụ thuộc trạng thái toàn cục (đây là dạng test nên dùng).
        assertEquals("xin chào", Strings.t("xin chào", "hello", Lang.VI))
    }

    @Test
    fun `pick tu lui ve tieng Viet khi chua dich`() {
        Strings.current = Lang.EN
        assertEquals("gốc", Strings.pick("gốc", null))
        assertEquals("gốc", Strings.pick("gốc", ""), "chuỗi rỗng = CHƯA dịch, không phải 'nhãn trống'")
        assertEquals("gốc", Strings.pick("gốc", "   "))
        assertEquals("root", Strings.pick("gốc", "root"))
        // Một dòng chưa dịch thì hiện tiếng Việt — KHÔNG ném, vì launcher trên xe không được sập vì một nhãn.
        val undone = TelemetrySpec("x", "Nhãn Việt", "", Domain.BODY, WidgetShape.VALUE, EvidenceTier.PROVEN, "k")
        assertEquals("Nhãn Việt", undone.displayLabel)
    }

    @Test
    fun `doi ngon ngu roi tra ve VI thi moi nhan y nhu cu`() {
        Strings.current = Lang.VI
        val before = snapshot()
        Strings.current = Lang.EN
        val english = snapshot()
        assertNotEquals(before, english)
        Strings.current = Lang.VI
        assertEquals(before, snapshot(), "đổi ngôn ngữ để lại vết ⇒ bài chạy sau sẽ đỏ ở tệp không liên quan")
    }

    @Test
    fun `nhan ngan tu lui theo bac shortEn roi labelEn`() {
        val full = TelemetryRegistry.byId("tyre_p_fl")!!
        assertEquals("Tyre FL", full.shortLabelIn(Lang.EN))
        assertEquals("Lốp TT", full.shortLabelIn(Lang.VI))
        // Có `labelEn` nhưng KHÔNG có `shortEn` ⇒ lùi về nhãn đầy tiếng Anh, chứ không rơi về tiếng Việt: một chip
        // hơi dài vẫn hơn một chip đột ngột đổi thứ tiếng.
        val noShortEn = TelemetryRegistry.byId("ev_range_km")!!
        assertEquals(null, noShortEn.shortEn)
        assertEquals("EV range", noShortEn.shortLabelIn(Lang.EN))
        // Không có gì tiếng Anh ⇒ lùi hẳn về nhãn ngắn tiếng Việt.
        val nothing = TelemetrySpec("x", "Nhãn", "", Domain.BODY, WidgetShape.VALUE, EvidenceTier.PROVEN, "k", short = "N")
        assertEquals("N", nothing.shortLabelIn(Lang.EN))
    }

    // ── 4 · Bề mặt THẬT đổi theo ngôn ngữ (không chỉ trường dữ liệu) ─────────────────────────────

    @Test
    fun `o nhom, chip, ket luan lop va goi lenh deu doi theo ngon ngu`() {
        val status = CarStatus(tyres = CarStatus.Tyres(pFlKpa = 150.0))

        Strings.current = Lang.VI
        assertEquals("Lốp", GroupBoard.of("g_tyres", status)!!.label)
        assertEquals("Lốp TT", GroupBoard.of("g_tyres", status)!!.cells.first().label)
        assertTrue(TyreBoard.verdict(TyreBoard.readings(status.tyres)).contains("bánh non"))
        assertEquals("Bật", TelemetryReadout.of("light_low_beam", CarStatus(lights = CarStatus.Lights(lowBeam = true)))!!.display)

        Strings.current = Lang.EN
        assertEquals("Tyres", GroupBoard.of("g_tyres", status)!!.label)
        assertEquals("Tyre FL", GroupBoard.of("g_tyres", status)!!.cells.first().label)
        assertTrue(TyreBoard.verdict(TyreBoard.readings(status.tyres)).contains("low"))
        assertEquals("On", TelemetryReadout.of("light_low_beam", CarStatus(lights = CarStatus.Lights(lowBeam = true)))!!.display)
        // Gói lệnh: câu báo cho người dùng gọi tên bước hỏng bằng nhãn EN.
        val res = MacroResult("mac_win_close_all", listOf(MacroStepResult("win_lf", false)))
        assertEquals("Close all: the car took no command", res.notice(ActionMacros.byId("mac_win_close_all")!!.displayLabel))
        // Lựa chọn của nút SELECT cũng theo ngôn ngữ.
        // ⚠ Mốc đổi 3 lần theo 3 lượt xoá nút: `drive_mode`→`regen_level`→`headlight_mode` ⇒ nay `seatc` ("Level 2").
        assertEquals("Level 2", ControlTileLogic.selectLabel(ControlRegistry.byId("seatc")!!, 2))
    }

    @Test
    fun `chip thanh tren doi theo ngon ngu`() {
        val status = CarStatus(climate = CarStatus.Climate(pm25Level = 1))
        val cfg = TopStripConfig(listOf(TopStripConfig.PM25, "tyre_p_fl"))

        Strings.current = Lang.VI
        val vi = TopStripChips.render(cfg, status)
        assertEquals("PM2.5 · Tốt", vi[0].text)
        assertTrue(vi[1].text.startsWith("Lốp TT · "))

        Strings.current = Lang.EN
        val en = TopStripChips.render(cfg, status)
        assertEquals("PM2.5 · Good", en[0].text)
        assertTrue(en[1].text.startsWith("Tyre FL · "), "chip phải dùng nhãn NGẮN tiếng Anh, thấy: ${en[1].text}")
    }

    @Test
    fun `dong phu cua nhom giu so dem tu du lieu o ca hai thu tieng`() {
        val g = CapabilityGroups.WINDOWS
        Strings.current = Lang.VI
        assertEquals("4 mục · 11 nút · mở/đóng và mở nửa từng kính · đóng cả cụm", g.contentLine)
        Strings.current = Lang.EN
        assertEquals("4 items · 11 buttons · open/close and half-open each window · close all", g.contentLine)
    }

    private companion object {

        /** Trần ký tự cho nhãn NGẮN tiếng Anh — chip thanh trạng thái cao ~24dp. */
        const val SHORT_CAP = 14

        /**
         * Nhãn/lựa chọn **được phép** giống nhau ở hai thứ tiếng → lý do.
         *
         * Mỗi mục là một quyết định phải giải thích được (cùng khuôn [SettingsCatalog.NOT_SETTINGS]): nếu danh sách
         * này chỉ là một tập chuỗi thì nó sẽ thành chỗ nhét mã vào cho bài canh im.
         */
        val SAME_ON_PURPOSE: Map<String, String> = mapOf(
            "PM2.5" to "ký hiệu ngành cho bụi mịn 2.5µm — dịch thành câu dài là sai chuẩn (spec §6 OQ2)",
            // ⚠ Ba mục "ESP" · "LDW" · "LDP" đã gỡ 2026-09-16 cùng toàn bộ ADAS/an toàn, và mục "Eco" đã gỡ
            // (V) 2026-09-17 cùng nút `drive_mode` — bài
            // `moi muc trong danh sach cho phep trung deu co ly do, va deu dung toi` bắt ngay nếu để lại.
            // ⚠⚠ 1.90 — 4 mục nữa gỡ cùng luật: "EV / HEV"/"EV"/"HEV" (`powertrain_mode`) + "Auto" (`headlight_mode`).
        )

        /** Mọi dòng có nhãn, ở đúng một chỗ để các bài không lệch phạm vi quét. */
        fun localizedRows(): List<Localized> =
            TelemetryRegistry.ALL + ControlRegistry.ALL + CapabilityGroups.ALL + WidgetRegistry.ALL +
                ActionMacros.ALL + SettingsCatalog.GROUPS + SettingsCatalog.ENTRIES +
                Domain.values().toList() + Quantity.values().toList() + TyreCorner.values().toList() +
                LauncherRequirements.ALL

        /**
         * Dấu tiếng Việt — chữ có mặt trong tiếng Việt mà **không** có trong tiếng Anh.
         *
         * Liệt kê tường minh thay vì dùng phép chuẩn hoá Unicode: bảng này đọc được, và nó cũng chính là bảng để
         * người sau thêm chữ nếu phát hiện sót. Không gồm dấu `–`/`·`/`°` — chúng là dấu câu/ký hiệu, dùng chung.
         */
        const val VIETNAMESE_MARKS =
            "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" +
                "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ"

        fun hasVietnameseMark(s: String): Boolean = s.any { it in VIETNAMESE_MARKS }

        /** Ảnh chụp MỌI nhãn hiện ra — dùng để chứng minh đổi ngôn ngữ không để lại vết. */
        fun snapshot(): List<String> =
            localizedRows().map { it.displayLabel } +
                TelemetryRegistry.ALL.map { it.displayShortLabel } +
                ControlRegistry.ALL.flatMap { it.displayArgs } +
                CapabilityGroups.ALL.map { it.contentLine } +
                listOf(
                    CapabilityPicker.GROUPS_TITLE, CapabilityPicker.GROUPS_NOTE,
                    CapabilityPicker.SINGLES_TITLE, CapabilityPicker.HINT_PREFIX,
                ) +
                ThemeMode.values().map { it.label() } +
                DockEdge.values().map { it.label } +
                LayoutPreset.values().map { it.label } +
                ImageFit.values().map { it.label } +
                TyreStatus.values().mapNotNull { it.reason }

        fun assertNotEquals(a: Any?, b: Any?) =
            assertFalse(a == b, "hai bên phải khác nhau — nếu giống thì phép đo này không chứng minh gì")
    }
}
