package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT P0-1 + P2-2] KHOÁ THEO HỒ SƠ TÀI XẾ — dây nối phía Android ═══════════════════════════════════════════
 *
 * Tách khỏi `AppWidgetWiringContractTest` khi tệp đó vượt trần 500 dòng: đây là **một chủ đề khác** (`WorkspacePrefs`
 * khoá theo hồ sơ) chỉ tình cờ gặp widget bên thứ ba ở một điểm.
 *
 * ## Vì sao bài này nằm ở `:app`
 * Nó quét **mã nguồn `:app`**. Dự án đã [ĐO] hai lần rằng bài quét mã của module X mà đặt ở module Y thì gradle
 * **không coi tệp của X là đầu vào** ⇒ task báo `UP-TO-DATE` và bài **không bao giờ chạy lại** (dấu xanh giả: S1 với
 * hai bài chống-rữa, rồi 11 ca của bộ niêm phong T11).
 *
 * ## Hai lỗi nó khoá, cả hai đều MẤT DỮ LIỆU và cả hai đều IM LẶNG
 *  - **P0-1**: `HomeUiState` chỉ mang dữ liệu của hồ sơ ĐANG DÙNG, mà id widget là của **HOST** (mọi hồ sơ) ⇒ lượt thu
 *    hồi id đi xoá **vĩnh viễn** widget của hồ sơ khác. [ĐO] `emulator-5554`: đổi hồ sơ ⇒ id 655 mất khỏi host; quay
 *    lại ⇒ ô ra thẻ *"app đã bị gỡ"* trong khi `pm list packages` cho thấy app **vẫn còn cài**.
 *  - **P2-2**: `deleteProfile` chỉ sửa hai khoá danh sách ⇒ mọi `<tên>__*` còn nguyên trên đĩa; `addProfile` không kiểm
 *    khoá cũ ⇒ đặt lại đúng cái tên vừa xoá nạp nguyên cấu hình của hồ sơ đã xoá.
 *
 * ⚠ Phép TÍNH thì ở `:core` (`AppWidgetIdsTest`). Bài này chỉ hỏi một câu mà bài đó **không thể** hỏi: *dữ liệu có
 * thật sự tới được phép tính chưa*. Đó đúng là nửa đã bị bỏ sót hai lần.
 */
class ProfileKeysWiringContractTest {

    private fun prefs() = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt")

    /** S4 · T2 — phần chụp–áp/nhân bản/chuyển đổi là **hàm mở rộng của cùng lớp**, ở tệp thứ hai (trần 500 dòng). */
    private fun prefsProfile() = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/WorkspacePrefsProfile.kt")

    private fun repo() = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt")

    /** MÃ đã bỏ chú thích — bắt buộc cho mọi phép "chuỗi X không được xuất hiện" (KDoc đầy đủ ở bài widget). */
    private fun code(src: String): String = src
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\n]*"""), " ")


    /**
     * ⚠⚠⚠ **P0 — phép tính đúng mà DỮ LIỆU KHÔNG TỚI thì lỗi vẫn còn nguyên.**
     *
     * `AppWidgetIds.used` nay cộng thêm [HomeUiState.widgetIdsOtherProfiles], nhưng trường đó **mặc định rỗng**. Nếu
     * `load()` không điền thì mọi bài `:core` vẫn xanh (chúng tự truyền dữ liệu vào) mà trên máy thì lỗi y như cũ —
     * đúng hình dạng đã để lọt lỗi này: bài canh hỏi *"có gọi đúng hàm không"* thay vì *"hàm có thấy đủ dữ liệu chưa"*.
     *
     * Bài này canh **đường dữ liệu**, không canh phép tính.
     */
    @Test
    fun `load dien id widget cua cac ho so KHAC vao state`() {
        val body = SourceRoots.body(code(repo()), "override fun load(): HomeUiState")
        assertTrue(
            "widgetIdsOtherProfiles = prefs.widgetIdsOtherProfiles()" in body,
            "load() PHẢI nạp id widget của các hồ sơ khác — thiếu nó thì đổi hồ sơ vẫn xoá vĩnh viễn widget của hồ " +
                "sơ kia ([ĐO] emulator: id 654 mất khỏi host, quay lại ra thẻ 'app đã bị gỡ' dù app còn cài)",
        )
    }

    /** Và phép dò đó phải quét **mọi hồ sơ**, BỎ hồ sơ đang dùng (nếu gồm cả nó thì id vừa bỏ khỏi ô sẽ rò mãi). */
    @Test
    fun `phep do quet moi ho so va bo ho so dang dung`() {
        val body = SourceRoots.body(code(prefs()), "fun widgetIdsOtherProfiles(): Set<Int>")
        assertTrue("profiles()" in body, "phải quét danh sách hồ sơ, không chỉ hồ sơ đang dùng")
        assertTrue(
            Regex("""filter\s*\{\s*it\s*!=\s*active\s*\}""").containsMatchIn(body),
            "phải BỎ hồ sơ đang dùng: nó đã nằm trong state (bản mới hơn đĩa), và ảnh chụp lúc load sẽ bảo vệ " +
                "vĩnh viễn một id mà người dùng vừa bỏ khỏi ô ⇒ id rác sống mãi",
        )
        assertTrue(
            "AppWidgetIds.idsInStored(" in body,
            "phép GIẢI MÃ phải nằm ở `:core` (kiểm được off-car); tệp này cần Context nên không bài nào chạm tới",
        )
    }

    /**
     * ⚠⚠ S4 · R3 — [WorkspacePrefs.PROFILE_SUFFIXES] phải **đọc thẳng** [ProfileScope.LAUNCHER_SUFFIXES], không
     * viết lại danh sách.
     *
     * Trước S4 danh sách là một `buildList` ngay trong `WorkspacePrefs`, và bài này phải quét **văn bản** của vùng
     * đó. Nay nguồn nằm ở `:core` nên phép kiểm mạnh hơn hẳn: bài này ở `:app` (phụ thuộc `:core`) nên nó **gọi
     * được** [ProfileScope] thật và so bằng **giá trị chạy**, không phải bằng chuỗi.
     *
     * Chép tay lại danh sách ở `:app` là mở lại đúng cái cửa mà [ProfileScope] sinh ra để đóng: thêm một khoá
     * theo-hồ-sơ ở bản sau mà chỉ sửa một nơi ⇒ hoặc khoá mồ côi sống mãi, hoặc id widget của hồ sơ khác bị xoá oan.
     */
    @Test
    fun `danh sach hau to doc THANG tu ProfileScope, khong viet lai`() {
        val src = code(prefs())
        assertEquals(
            1, Regex("""val PROFILE_SUFFIXES""").findAll(src).count(),
            "danh sách hậu tố khai đúng MỘT lần — viết tay hai lần thì bản sau sẽ chỉ cập nhật một nơi",
        )
        assertTrue(
            Regex("""val PROFILE_SUFFIXES:\s*List<String>\s*=\s*ProfileScope\.LAUNCHER_SUFFIXES""")
                .containsMatchIn(src),
            "phải là ProfileScope.LAUNCHER_SUFFIXES NGUYÊN VẸN — mọi phép `+`/`filter`/`buildList` ở đây đều là một " +
                "danh sách thứ hai mà `ProfileScopeTest` không nhìn thấy",
        )
        assertEquals(
            ProfileScope.LAUNCHER_SUFFIXES, WorkspacePrefs.PROFILE_SUFFIXES,
            "và giá trị CHẠY phải bằng nhau (bài này ở :app nên gọi được cả hai)",
        )
        // Trần ô + số tệp ClusterNav sinh bằng mã ở `:core` ⇒ ở đây chỉ cần chốt rằng chúng ĐÃ tới nơi.
        assertTrue(
            "slot_${WorkspaceState.SLOT_CAP - 1}" in WorkspacePrefs.PROFILE_SUFFIXES,
            "`slot_*` phải sinh tới hết SLOT_CAP — trần đã đổi một lần (4 → 6)",
        )
        assertTrue(
            ProfileScope.SNAPSHOT_SUFFIXES.all { it in WorkspacePrefs.PROFILE_SUFFIXES },
            "ảnh chụp cấu hình ClusterNav cũng là khoá theo hồ sơ ⇒ xoá hồ sơ phải xoá cả chúng",
        )
    }

    /**
     * ⚠⚠ [SOÁT P2-2] Xoá hồ sơ phải xoá **mọi khoá của nó**, và danh sách hậu tố khai ĐÚNG MỘT chỗ.
     *
     * Bản cũ chỉ sửa `profiles` + `active_profile` ⇒ `<tên>__slot_*`, `__scenes`, `__dock_*`… nằm nguyên trên đĩa;
     * [WorkspacePrefs.addProfile] không kiểm khoá cũ nên đặt lại đúng cái tên vừa xoá sẽ **nạp nguyên cấu hình cũ**,
     * kể cả `aw:<id>` của những id đã bị thu hồi ⇒ ô ra thẻ *"app đã bị gỡ"*. Đây cũng là điều làm id widget của hồ
     * sơ bị xoá được nhả đúng lúc (dò đọc đĩa ⇒ hết kể id đó ⇒ lượt thu hồi kế tiếp thấy nó là rác).
     */
    @Test
    fun `xoa ho so xoa moi khoa cua no, danh sach hau to o dung mot cho`() {
        val src = code(prefs())
        val del = SourceRoots.body(src, "fun deleteProfile(name: String)")
        assertTrue(
            "profileKeys(name)" in del && ".remove(" in del,
            "deleteProfile phải xoá mọi khoá của hồ sơ đó (không thì prefs phình vô hạn và tên cũ nạp lại rác)",
        )
    }

    /**
     * ⚠ Và danh sách đó phải **ĐẦY ĐỦ**: mọi hậu tố mà mã thật ghép vào tên hồ sơ (`key("…")` / `keyOf(x, "…")`)
     * phải nằm trong [ProfileScope.LAUNCHER_SUFFIXES].
     *
     * Không có phép kiểm này thì bản sau thêm một khoá theo-hồ-sơ và lượt xoá hồ sơ bỏ sót nó — im lặng, chỉ lộ ra
     * khi ai đó tạo lại hồ sơ cùng tên và thấy dữ liệu lạ.
     *
     * ⚠ Quét **CẢ HAI** tệp (`WorkspacePrefs.kt` + `WorkspacePrefsProfile.kt`): S4 tách phần chụp–áp/nhân bản/chuyển
     * đổi ra tệp thứ hai, và tệp đó là nơi ghép nhiều hậu tố nhất (`preset`, `slot_$i`, `dock_*`, `grid_layout`).
     * Quét một tệp là đúng cái hình dạng "bài canh nhìn nửa sự thật" mà `SourceRoots` đã phải vá hai lần.
     */
    @Test
    fun `moi khoa theo ho so deu co trong danh sach hau to`() {
        val src = code(prefs()) + "\n" + code(prefsProfile())
        val used = Regex("""\bkey(?:Of)?\((?:[A-Za-z]+,\s*)?(?:"([^"]+)"|(K_[A-Z_]+))\)""").findAll(src)
            .map { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
            .map { it.substringBefore('$') }   // `slot_$it` / `slot_$i` → `slot_`
            .distinct().toList()
        // Hằng `K_*` là tên Kotlin, không phải giá trị ⇒ tra ngược qua bảng dưới (bài canh phải nói về GIÁ TRỊ thật).
        val constants = mapOf(
            "K_GRID" to "grid_layout", "K_THEME" to "theme_mode", "K_UNITS" to "unit_prefs",
            "K_WALL" to "wallpaper_prefs", "K_AUTOSTART" to "launcher_autostart", "K_LANG" to "lang",
        )
        val suffixes = ProfileScope.LAUNCHER_SUFFIXES
        val missing = used.mapNotNull { raw ->
            val s = constants[raw] ?: raw
            when {
                s == "slot_" -> if (suffixes.none { it.startsWith("slot_") }) s else null
                s.startsWith("K_") -> s   // hằng chưa khai trong bảng trên ⇒ bài này đang nhìn nửa sự thật
                s in suffixes -> null
                else -> s
            }
        }
        assertEquals(
            emptyList<String>(), missing,
            "khoá theo hồ sơ KHÔNG có trong ProfileScope.LAUNCHER_SUFFIXES ⇒ xoá hồ sơ sẽ để nó mồ côi: $missing",
        )
        assertTrue(used.size >= 8, "phép quét phải thấy đủ các khoá (đang thấy ${used.size}) — dưới mức này là nó hỏng")
    }

    /**
     * ⚠ Chỉ khoá THEO HỒ SƠ được vào danh sách. Khoá **theo XE** mà lọt vào thì xoá một hồ sơ sẽ xoá luôn danh sách
     * hồ sơ / lịch sử mở app / hình học khi chiếu của **cả xe** — lỗi tệ hơn lỗi đang vá.
     *
     * ⚠ S4 · R3(a) **đảo chiều bốn khoá**: `theme_mode` · `unit_prefs` · `wallpaper_prefs` · `launcher_autostart`
     * trước đây nằm ở danh sách cấm này, nay là khoá **theo hồ sơ** (owner: *"profile cover… tất cả mọi thứ"*).
     * Bài giữ nguyên hình dạng, chỉ đổi nội dung — và nó vẫn là thứ chặn ca "xoá hồ sơ mất dữ liệu của cả xe".
     */
    @Test
    fun `khoa theo XE KHONG duoc nam trong danh sach hau to`() {
        listOf("profiles", "active_profile", "boot_profile", "migrated_scenes_v1", "recent_apps", "last_display_id")
            .forEach {
                assertTrue(
                    it !in WorkspacePrefs.PROFILE_SUFFIXES,
                    "`$it` là khoá theo XE — xoá một hồ sơ không được làm mất nó",
                )
                assertEquals(
                    ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(it),
                    "và `:core` phải khai nó là khoá theo XE kèm lý do (ProfileScope.DEVICE_KEYS)",
                )
            }
        // Chiều ngược: bốn khoá S4 vừa đưa về hồ sơ phải THẬT SỰ tới nơi.
        listOf("theme_mode", "unit_prefs", "wallpaper_prefs", "launcher_autostart", "lang").forEach {
            assertTrue(
                it in WorkspacePrefs.PROFILE_SUFFIXES,
                "S4 · R3(a): `$it` nay THEO HỒ SƠ — thiếu nó thì hồ sơ chỉ cover được một nửa",
            )
        }
    }

    /**
     * ⚠ Và **hồ sơ MỚI phải bắt đầu trống** — chốt cho máy đã chạy bản cũ.
     *
     * `deleteProfile` nay dọn sạch, nhưng máy đang chạy trên xe thì còn nguyên khoá của mọi hồ sơ từng bị xoá bằng bản
     * cũ. Đặt lại đúng cái tên đó mà không dọn thì hồ sơ "mới" nạp cấu hình của một hồ sơ người dùng tưởng đã xoá.
     */
    @Test
    fun `ho so moi bat dau trong`() {
        val body = SourceRoots.body(code(prefs()), "fun addProfile(name: String)")
        assertTrue(
            "profileKeys(clean)" in body && ".remove(" in body,
            "addProfile phải dọn khoá cũ của cái tên đó — máy đã chạy bản cũ còn khoá mồ côi trên đĩa",
        )
        assertTrue(
            Regex("""if\s*\(\s*isNew\s*\)\s*profileKeys""").containsMatchIn(body),
            "và CHỈ dọn khi thật sự mới: tên đã có thì đây là lượt 'chuyển sang', dọn là MẤT cấu hình của hồ sơ đó",
        )
    }

    // ══ S4 · T2 — ba bài khoá lại ba bài học của lượt chuyển sang "hồ sơ giữ tất cả" ═══════════════

    /**
     * ⚠⚠ **THỨ TỰ của lượt đổi hồ sơ là một phần của hợp đồng** (R5): chụp A → đặt con trỏ → áp B → gọi lại applier.
     *
     * Ba cách hỏng, cả ba đều im lặng và cả ba đều mất dữ liệu người dùng:
     *  • chụp **sau** khi đổi con trỏ ⇒ ảnh của hồ sơ A bị ghi vào ô của B (A mất sạch, B bị ghi đè);
     *  • áp **trước** khi đổi con trỏ ⇒ đọc ảnh của B nhưng khoá `key()` vẫn trỏ A;
     *  • thiếu `reapplyAll` ⇒ đĩa đúng mà **không có gì đang chạy biết** ([ĐO] 0 chỗ nào trong dự án đăng ký
     *    `registerOnSharedPreferenceChangeListener`, nên không có đường tự-nhận-thay-đổi nào cả).
     *
     * Bài so **vị trí** chứ không chỉ "có gọi không": đúng nửa mà một bài "có gọi đúng hàm chưa" không thể thấy.
     */
    @Test
    fun `doi ho so chup A truoc, dat con tro, roi ap B, roi goi lai applier`() {
        val body = SourceRoots.body(code(repo()), "override fun switchProfile(name: String): HomeUiState")
        val snap = body.indexOf("snapshotClusterNav(")
        val point = body.indexOf("setActiveProfile(")
        val apply = body.indexOf("applyClusterNav(")
        val reapply = body.indexOf("reapplyAll()")
        assertTrue(snap >= 0, "phải CHỤP hồ sơ đang rời — thiếu nó thì mọi thứ họ vừa chỉnh biến mất")
        assertTrue(point in (snap + 1)..Int.MAX_VALUE, "phải chụp TRƯỚC khi đổi con trỏ (ảnh của A không được vào ô của B)")
        assertTrue(apply > point, "phải áp SAU khi đổi con trỏ (khoá `key()` đọc theo hồ sơ đang dùng)")
        assertTrue(reapply > apply, "và gọi lại applier SAU khi đã ghi — trước đó thì chưa có gì mới để áp")
    }

    /**
     * ⚠⚠ Lượt chuyển **cảnh → hồ sơ** phải chạy TRƯỚC lượt `load()` đầu tiên, tức trước lượt dọn rác widget đầu tiên.
     *
     * Id widget bên thứ ba của một cảnh **chỉ** nằm trong chuỗi `<hồ sơ>__scenes` cho tới khi chuyển xong. Để lượt
     * dọn (`AppWidgetIds.orphaned` → `AppWidgetSlotHost.reclaim`, chạy ở nhịp render đầu) đi trước là để chính lượt
     * nâng cấp **xoá vĩnh viễn** widget của người dùng — mất dữ liệu ở đúng lượt cài bản mới.
     */
    @Test
    fun `luot chuyen canh chay o init, truoc luot load dau tien`() {
        val src = code(repo())
        val initAt = src.indexOf("init {")
        val loadAt = src.indexOf("override fun load()")
        assertTrue(initAt in 0 until loadAt, "phải ở khối `init` (chạy lúc dựng repository), không ở trong `load()`")
        assertTrue(
            "prefs.migrateScenesOnce()" in SourceRoots.body(src, "init {"),
            "và khối đó phải gọi đúng lượt chuyển một-lần",
        )
        // Lưới an toàn còn lại: cho tới khi chuyển xong, phép dò id widget PHẢI còn đọc chuỗi cảnh cũ.
        val probe = SourceRoots.body(code(prefs()), "fun widgetIdsOtherProfiles(): Set<Int>")
        assertTrue(
            "LEGACY_SCENES" in probe,
            "phép dò phải còn đọc `<hồ sơ>__scenes` — đó là thứ DUY NHẤT giữ id widget của cảnh trước lượt chuyển",
        )
    }

    /**
     * ⚠ Ảnh chụp cấu hình ClusterNav phải chụp **cả khoá VẮNG MẶT** (dưới dạng `null` tường minh).
     *
     * `sp.all` chỉ trả khoá có mặt. Bỏ qua khoá vắng thì lượt áp cũng bỏ qua nó ⇒ nó **giữ nguyên giá trị của hồ sơ
     * vừa rời**: A chưa từng bật bong bóng, B bật ⇒ A → B → A và A tự nhiên có bong bóng, vĩnh viễn, không có đường
     * quay lại. [PrefSnapshot] có thẻ kiểu `n` đúng cho ca này và lượt áp phải dịch nó thành `remove`.
     */
    @Test
    fun `anh chup giu ca khoa vang mat, va luot ap xoa dung khoa do`() {
        val src = code(prefsProfile())
        val snap = SourceRoots.body(src, "internal fun WorkspacePrefs.snapshotClusterNav(profile: String)")
        assertTrue(
            "associateWith" in snap,
            "phải ánh xạ TỪ danh sách khoá (khoá vắng ⇒ `null`), không lọc theo `sp.all` — xem KDoc của hàm",
        )
        assertTrue("PrefSnapshot.encode(" in snap, "phép mã hoá phải nằm ở `:core` (kiểm được off-car)")
        val apply = SourceRoots.body(src, "internal fun WorkspacePrefs.applyClusterNav(profile: String)")
        assertTrue(Regex("""null\s*->\s*e\.remove\(k\)""").containsMatchIn(apply), "`null` ⇒ XOÁ khoá")
        // Và phải ghi ĐÚNG KIỂU: `getBoolean` trên giá trị ghi bằng `putString` NÉM, và chỗ đọc là dịch vụ trên xe.
        listOf("putBoolean", "putInt", "putLong", "putFloat", "putString", "putStringSet").forEach {
            assertTrue("e.$it(" in apply, "lượt áp phải giữ kiểu `$it` — mất kiểu thì hỏng trên đường, không hỏng ở đây")
        }
    }

    /**
     * ⚠⚠ [SOÁT PASS 1 · 2026-09-14] Lượt ÁP phải lọc theo [ProfileScope], không ghi mù mọi khoá trong ảnh chụp.
     *
     * Ảnh chụp sống trên đĩa của xe **lâu hơn** bản phân loại đã sinh ra nó. Một khoá bị xếp lại sang "theo XE" ở
     * bản sau (đã xảy ra thật: `cast_enabled`, OQ2) vẫn còn nguyên trong ảnh cũ ⇒ không lọc thì lượt đổi hồ sơ
     * **vẫn** ghi đè nó, tức quyết định "khoá này thuộc cả xe" chỉ có hiệu lực trên máy mới cài — đúng loại lỗi
     * chỉ hiện trên xe của người đã dùng bản trước.
     */
    @Test
    fun `luot ap loc theo ProfileScope, khong ghi mu moi khoa trong anh chup`() {
        val apply = SourceRoots.body(code(prefsProfile()), "internal fun WorkspacePrefs.applyClusterNav(profile: String)")
        assertTrue(
            Regex("""PrefSnapshot\.decode\(raw\)\s*\.filterKeys""").containsMatchIn(apply),
            "phải lọc khoá giải mã theo danh sách khoá của CHÍNH tệp đó (ProfileScope.CLUSTERNAV_KEYS) — ảnh chụp cũ " +
                "có thể mang khoá nay đã xếp theo XE",
        )
    }

    /**
     * ⚠⚠ [SOÁT PASS 1 · 2026-09-14] Lượt chuyển cảnh phải **dồn tên đã lấy** qua từng hồ sơ.
     *
     * Cảnh là dữ liệu theo hồ sơ ở bản cũ ⇒ hai hồ sơ đều có thể có cảnh tên *"Đi làm"*. `ScenesMigration.plan` chỉ
     * tránh trùng với danh sách nó được đưa, nên đưa cùng một `profiles()` cho mọi lượt thì cả hai gấp ra cùng một
     * tên và vòng ghi bỏ bản thứ hai ⇒ **mất hẳn một cảnh** ở đúng lượt nâng cấp, im lặng (R2 đòi hậu tố `" 2"`).
     *
     * Và ảnh chụp của hồ sơ nguồn phải được làm mới TRƯỚC khi chép, cùng lý do `duplicateActiveProfile` chụp trước:
     * ảnh cũ ⇒ hồ sơ vừa chuyển ra đời mang cấu hình ClusterNav của một lần đổi hồ sơ nào đó trong quá khứ.
     */
    @Test
    fun `luot chuyen don ten da lay va chup lai anh truoc khi chep`() {
        val body = SourceRoots.body(code(prefsProfile()), "internal fun WorkspacePrefs.migrateScenesOnce()")
        assertTrue(
            "existingNames = taken" in body,
            "mỗi lượt gấp kế hoạch phải nhận danh sách tên ĐÃ DỒN, không phải `profiles()` nguyên bản",
        )
        val accumulate = body.indexOf("taken += plan.newProfiles")
        assertTrue(accumulate >= 0, "và phải dồn tên vừa sinh vào danh sách đó")
        val snap = body.indexOf("snapshotClusterNav(active)")
        val write = body.indexOf("writeRecord(")
        assertTrue(snap >= 0, "phải chụp lại cấu hình ClusterNav của hồ sơ nguồn")
        assertTrue(snap < write, "và chụp TRƯỚC khi chép sang hồ sơ mới (ảnh cũ ⇒ bản chép khác bản gốc ngay lúc ra đời)")
    }
}
