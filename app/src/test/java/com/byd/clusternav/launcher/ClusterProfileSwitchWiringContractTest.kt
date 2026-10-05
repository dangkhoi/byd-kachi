package com.byd.clusternav.launcher

import com.byd.clusternav.VmOverlayPosition
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V-CLUSTER — nối dây tầng `:app` của *"Phần cụm lưu hết thành profile"* (owner 2026-09-30) ═══════════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §11.3 VC-R5/R7/R9 · §11.5 A2–A6. Đọc source đã bỏ chú thích
 * ([SourceRoots.codeOf]) và cắt thân bằng [SourceRoots.body] — không `substringAfter/Before` (quét tràn = test giả).
 *
 * Hành vi của phép ghim chạy thật ở `:core` (`CastSessionPinTest`); ở đây canh những dây mà JVM không dựng được
 * (`SharedPreferences`, `Context`, `View`): đổi hồ sơ không chạm phiên chiếu, `create()` chốt bản chờ đúng chỗ, màn Cài
 * đặt nói giá trị của PHIÊN và chỉ nói giá trị hồ sơ khi nó khác.
 */
class ClusterProfileSwitchWiringContractTest {

    private fun launcher(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    // ── VC-R5 — đổi hồ sơ KHÔNG dựng/gỡ phiên chiếu, KHÔNG lệnh wm/am mới lên cụm ─────────────────────────────

    /**
     * Mọi thứ [ClusterNavBridge.reapplyAll] được phép làm là gọi lại applier CÓ SẴN. Một lời gọi mở/đóng projection,
     * dispatch, resize, áp hình học hay một chuỗi `wm`/`am` trong thân này là cụm đổi trước mặt người lái vì một cú
     * chạm chip hồ sơ — đúng thứ VC-R5 cấm. `cast_enabled` đi đường HOÃN (bản chờ), không đi qua đây.
     */
    @Test
    fun `reapplyAll khong dung go phien chieu va khong ban lenh len cum`() {
        val body = SourceRoots.body(launcher("ClusterNavBridgeReapply.kt"), "internal fun ClusterNavBridge.reapplyAll()")
        listOf(
            "setCastEnabled", "applyCastPendingNow", "openProjection", "closeProjection", "closeOrphanProjection",
            "dispatch(", "applySplitRatioLive", "setSplitPct", "applyPinned", "applySavedProfile", "resizeActive",
            "setDensity", "setGeometry", "resetGeometry", "restoreCluster", "deepRescue", "FloatingBubbleService",
            "coordinator", "\"wm ", "\"am ",
        ).forEach { bad -> assertFalse(body.contains(bad), "reapplyAll chứa '$bad' — lượt đổi hồ sơ chạm phiên chiếu/cụm") }
        assertTrue(body.contains("AutomationService.sync(app)"), "A3: camera_signal_enabled + luật dẫn đường theo hồ sơ cần đồng bộ FGS")
    }

    /**
     * Review 2.89 Pass 3 · vietmap-dock-r2-2 — đổi hồ sơ giữa phiên phải chạy lại lượt điều kiện nền (lượt SẴN của hồ sơ trước có thể
     * đã TRẢ appop vẽ nổi của VietMap; hồ sơ mới bật bóng thì cần nó lại) — đúng lượt nền `AppPrereqs.onReady` (luồng riêng, không mở
     * app), không phải `VietMapAutostartService` (mở app — cố ý bỏ). Thử ĐỎ: xoá dòng `step("app.prereqs")`.
     */
    @Test
    fun `Pass 3 - doi ho so chay lai luot dieu kien nen, khong mo app`() {
        val body = SourceRoots.body(launcher("ClusterNavBridgeReapply.kt"), "internal fun ClusterNavBridge.reapplyAll()")
        assertTrue(body.contains("step(\"app.prereqs\") { AppPrereqs.onReady(app) }"), body)
        listOf("VietMapAutostartService", "AppPrereqs.ensure(", "startForAppOpen").forEach {
            assertFalse(body.contains(it), "đổi hồ sơ không được mở app (`$it`)")
        }
    }

    /** Lượt đổi hồ sơ ở tầng dữ liệu: chụp → trỏ → áp → reapplyAll, và KHÔNG một đường nào vào coordinator. */
    @Test
    fun `switchProfile khong cham coordinator`() {
        val body = SourceRoots.body(launcher("PrefsWorkspaceRepository.kt"), "override fun switchProfile(name: String): HomeUiState")
        listOf("SimpleCastRuntime", "coordinator", "setCastEnabled", "Projection", "dispatch(", "FloatingBubbleService")
            .forEach { bad -> assertFalse(body.contains(bad), "switchProfile chứa '$bad'") }
    }

    // ── VC-R7 — `cast_enabled` chốt ở lần khởi động tiến trình kế ───────────────────────────────────────────────

    /**
     * Bản chờ phải được chốt TRƯỚC khi dựng coordinator (mọi chỗ đọc Cast đi qua `coordinator(...).prefs` — spec K1), và
     * chốt BẬT→TẮT phải kèm lượt dọn projection mồ côi (thiếu nó là cụm hai chủ). Đảo thứ tự hoặc quên nửa sau ⇒ đỏ.
     */
    @Test
    fun `create chot ban cho TRUOC khi dung coordinator va don projection mo coi khi chot TAT`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt")
        val body = SourceRoots.body(src, "private fun create(app: Context): SimpleCastCoordinator")
        val commit = body.indexOf("prefs.commitCastEnabledPending()")
        val build = body.indexOf("SimpleCastCoordinator(")
        val orphan = body.indexOf("coordinator.closeOrphanProjection()")
        assertTrue(commit in 0 until build, "chốt bản chờ phải đứng TRƯỚC SimpleCastCoordinator(")
        assertTrue(orphan > build, "dọn projection mồ côi cần coordinator đã dựng")
        assertTrue(body.contains("atStart.closeOrphan"), "chỉ dọn khi vừa chốt BẬT→TẮT")
    }

    /** Nút *Áp ngay* đi ĐÚNG đường thật (setCastEnabled xoá bản chờ ở tầng thi hành), không ghi prefs trực tiếp. */
    @Test
    fun `ap ngay di duong that setCastEnabled`() {
        val cast = launcher("ClusterNavBridgeCast.kt")
        val apply = SourceRoots.body(cast, "fun ClusterNavBridge.applyCastPendingNow()")
        assertTrue(apply.contains("setCastEnabled(it)"))
        assertFalse(apply.contains("prefs.set"), "không đường tắt vào prefs — mở/đóng projection phải đi cùng")
        val runtime = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt")
        val set = SourceRoots.body(runtime, "override fun setCastEnabled(enabled: Boolean)")
        assertTrue(set.contains("remove(CastEnableDeferral.PENDING_KEY)"), "cú chạm tường minh xoá bản chờ trong cùng lượt ghi")
    }

    // ── VC-R9 — màn nói giá trị của PHIÊN; giá trị hồ sơ chỉ hiện khi khác ────────────────────────────────────

    /** Bốn thanh −/+ và chip DPI đọc bản ghim của phiên; dải + ô nhớ theo tỉ lệ PHIÊN (sửa refute C5/C2). */
    @Test
    fun `cau hinh hoc doc ban ghim va ti le phien`() {
        val geo = launcher("ClusterNavBridgeGeometry.kt")
        listOf(
            "fun ClusterNavBridge.geometryBounds(target: CastGeometryTarget): CastBounds",
            "fun ClusterNavBridge.geometryDensity(target: CastGeometryTarget): Int",
        ).forEach { sig ->
            val body = SourceRoots.body(geo, sig)
            assertTrue(body.contains("sessionConfig(target)"), "$sig phải đọc cấu hình của PHIÊN")
            assertFalse(body.contains("savedConfig(") || body.contains("prefs."), "$sig không được đọc prefs của hồ sơ")
        }
        val session = SourceRoots.body(geo, "private fun ClusterNavBridge.sessionConfig(target: CastGeometryTarget): DisplayConfig?")
        assertTrue(session.contains(".pinned"), "cấu hình phiên = bản ghim")
        assertFalse(session.contains("prefs."))
        // Review 2.89 Pass 3 · cluster-r2-1 — ĐỔI GHIM có lý do: dải đi qua MỘT khung gốc theo kiểu khung của PHIÊN
        // (`ClusterRectLayout.editFrame`), vẫn theo tỉ lệ PHIÊN.
        assertTrue(SourceRoots.body(geo, "fun ClusterNavBridge.geometryBand(target: CastGeometryTarget)").contains("editFrame(target)"))
        val frame = SourceRoots.body(geo, "private fun ClusterNavBridge.editFrame(target: CastGeometryTarget): CastBounds {")
        assertTrue(frame.contains("ClusterRectLayout.editFrame(frameStyle(), target.side, bandPct(), width, height)"), frame)
        // Mốc tự kết bằng `{` của khối `runCatching` — thân biểu thức nhiều dòng thụt 4 khoảng thì phép cắt theo `=` dừng ở dòng đầu.
        val saved = SourceRoots.body(geo, "private fun ClusterNavBridge.savedConfig(target: CastGeometryTarget): DisplayConfig? = runCatching {")
        // 2.89 · B1b — ĐỔI GHIM có lý do: ô nhớ còn theo KIỂU KHUNG của phiên (cụm Chữ nhật ⇒ khoá `__RECT`), đúng chỗ lượt chỉnh
        // tay ghi (`CastSessionPin.resizeSlotBody` dùng `CastProfile.of(side, current.leftPercent, frameStyle)`).
        assertTrue(saved.contains("CastProfile.of(it, bandPct(), style)"), "ô nhớ của hồ sơ theo tỉ lệ PHIÊN (đúng chỗ lượt chỉnh tay ghi)")
        assertTrue(saved.contains("frameStyle()"), "kiểu khung lấy từ PHIÊN, không từ lựa chọn của hồ sơ")
        assertTrue(SourceRoots.body(geo, "private fun ClusterNavBridge.frameStyle(): CastStyle").contains("castStyleSession()?.frame"))
        assertTrue(SourceRoots.body(geo, "private fun ClusterNavBridge.bandPct(): Int").contains("sessionSplitPct()"))
    }

    /**
     * Review 2.89 Pass 3 · cluster-r2-1 — ba chỗ của bộ chỉnh (dải kẹp X/Y, "Đặt lại", mặc định khi chưa ghim) và dòng phụ hồ sơ
     * đều đi qua khung gốc của PHIÊN: phiên Chữ nhật không bao giờ kẹp/đặt lại theo cả cụm (hai nửa chồng nhau · app dưới nền ADAS
     * lưu vào `__RECT`). Thử ĐỎ: trả `resetGeometry` về `CastBounds(bandMin, 0, bandMax, height)`. Phép số thuần ở `:core`
     * (`CastGeometryClampTest` · Pass 3).
     */
    @Test
    fun `Pass 3 - bo chinh khung theo khung goc cua phien (Chu nhat khong theo ca cum)`() {
        val geo = launcher("ClusterNavBridgeGeometry.kt")
        assertTrue(SourceRoots.body(geo, "fun ClusterNavBridge.resetGeometry(target: CastGeometryTarget)")
            .contains("setGeometryBounds(target, editFrame(target))"))
        assertTrue(SourceRoots.body(geo, "private fun ClusterNavBridge.boundsOf(target: CastGeometryTarget, config: DisplayConfig?): CastBounds")
            .contains("config?.bounds ?: editFrame(target)"))
        assertTrue(SourceRoots.body(geo, "private fun ClusterNavBridge.clampToBand(target: CastGeometryTarget, bounds: CastBounds): CastBounds {")
            .contains("CastGeometryGuard.clampBounds(bounds, f.left, f.right, f.top, f.bottom)"))
        val diff = SourceRoots.body(geo, "fun ClusterNavBridge.geometryProfileDiffers(target: CastGeometryTarget): CastGeometryProfileValue?")
        assertTrue(diff.contains("if (frameStyle() == CastStyle.RECT) ClusterRectLayout.pin(savedConfig(target), editFrame(target))"), diff)
        assertFalse(Regex("""CastBounds\(bandMin, 0, bandMax""").containsMatchIn(geo), "không còn khung gõ theo cả cụm")
    }

    /** Dòng phụ "Hồ sơ này: …" chỉ vẽ khi `geometryProfileDiffers` trả khác `null` — không có dòng phụ luôn hiện. */
    @Test
    fun `dong phu ho so chi hien khi khac`() {
        val block = launcher("SettingsSectionsCastGeometry.kt")
        val note = SourceRoots.body(block, "private fun profileNote(target: CastGeometryTarget): String?")
        assertTrue(note.contains("bridge.geometryProfileDiffers(target) ?: return null"))
        assertTrue(SourceRoots.body(block, "fun rebuild()").contains("profileNote(target)?.let"))
        val diff = SourceRoots.body(
            launcher("ClusterNavBridgeGeometry.kt"),
            "fun ClusterNavBridge.geometryProfileDiffers(target: CastGeometryTarget): CastGeometryProfileValue?",
        )
        assertTrue(diff.contains("takeIf { it != CastGeometryProfileValue(densityOf(session), boundsOf(target, session)) }"),
            "so hai phía bằng CÙNG phép dựng — không thì chưa đổi hồ sơ đã có dòng phụ giả")
    }

    /**
     * Senior review Pass 2 — một NỬA khi tỉ lệ PHIÊN ≠ tỉ lệ HỒ SƠ: lần chia đôi kế ghim ô nhớ của tỉ lệ HỒ SƠ (khoá khác,
     * dải khác), nên dòng *"Hồ sơ này: … — áp dụng từ lần chiếu sau"* in bản ghi của tỉ lệ PHIÊN là nói sai. Phải im,
     * và chặn ĐỨNG TRƯỚC lượt đọc ô nhớ — dòng tỉ lệ "Đang chia … · hồ sơ này …" đã nói chuyện đó.
     */
    @Test
    fun `dong phu khung cua mot nua im khi ti le phien khac ti le ho so`() {
        val diff = SourceRoots.body(
            launcher("ClusterNavBridgeGeometry.kt"),
            "fun ClusterNavBridge.geometryProfileDiffers(target: CastGeometryTarget): CastGeometryProfileValue?",
        )
        val guard = diff.indexOf("if (target.side != null && sessionSplitPct() != splitPct()) return null")
        assertTrue(guard >= 0, "thiếu chốt tỉ lệ phiên ≠ tỉ lệ hồ sơ cho một nửa")
        assertTrue(guard < diff.indexOf("savedConfig(target)"), "chốt phải đứng TRƯỚC lượt đọc ô nhớ của hồ sơ")
    }

    /**
     * Công tắc Cast + dòng "áp dụng từ lần nổ máy sau" + Áp ngay; tỉ lệ nói số của phiên.
     *
     * ⚠ ĐẢO CHIỀU có chủ ý — FIX286 · PI4 (owner 03/10 *"2 theo đề xuất"*, spec `kachi-286-field-fixes.html` §3.3; VC-R9
     * spec S4 §11.4.8 sửa cùng lượt): bản 2.84 khoá `on = bridge.castEnabledForProfile()` (= `pending ?: sống`) ⇒ cụm
     * TẮT mà ô hiện ✓, chạm lật thành TẮT ⇒ phải chạm HAI lần (FIELD-285-0310 *"phải bật lại thủ công"*). Nay công tắc vẽ
     * giá trị ĐANG CHẠY; hàm cũ bị gỡ hẳn (không còn đường nào vẽ ý của hồ sơ lên công tắc); dòng chờ + Áp ngay giữ nguyên.
     */
    @Test
    fun `man chieu cum noi that ve cast_enabled va ti le`() {
        val cast = launcher("SettingsSectionsCast.kt")
        val master = SourceRoots.body(cast, "private fun rebuildMaster()")
        assertTrue(master.contains("on = bridge.castEnabled(),"), "công tắc = giá trị ĐANG CHẠY (FIX286 · PI4)")
        assertFalse(Regex("""castEnabledForProfile""").containsMatchIn(cast + launcher("ClusterNavBridgeCast.kt")),
            "đường vẽ ý của hồ sơ lên công tắc phải gỡ hẳn — không để hàm chết, không để công tắc quay lại pending ?: sống")
        val switchAt = master.indexOf("on = bridge.castEnabled(),")
        assertTrue(switchAt < master.indexOf("bridge.castEnabledPending() ?: return"), "dòng chờ đứng SAU công tắc")
        assertTrue(master.contains("bridge.castEnabledPending() ?: return"), "dòng chờ + Áp ngay chỉ khi có bản chờ")
        assertTrue(master.contains("bridge.applyCastPendingNow()"))
        assertTrue(master.contains("R.string.kachi_cast_enabled_pending"))
        val split = SourceRoots.body(cast, "private fun rebuildSplit()")
        assertTrue(split.contains("bridge.sessionSplitPct()?.takeIf { it != profilePct } ?: return"), "dòng tỉ lệ chỉ khi phiên ≠ hồ sơ")
        val preview = SourceRoots.body(cast, "private fun refreshPreview(state: SimpleCastState)")
        assertTrue(preview.contains("state.leftPercent"), "ô xem trước vẽ tỉ lệ ĐANG chia (phiên)")
        assertFalse(preview.contains("bridge.splitPct()"), "không vẽ số hồ sơ lưu cho lần chia KẾ")
        val refresh = SourceRoots.body(cast, "private fun refreshStatus()")
        listOf("rebuildMaster()", "rebuildSplit()", "geometryBlock.rebuild()").forEach {
            assertTrue(refresh.contains(it), "refreshStatus phải dựng lại '$it' sau mỗi hành động")
        }
    }

    /** Chuỗi mới có đủ hai ngôn ngữ; câu hồ sơ ở màn chính không còn loại trừ "khung hình khi chiếu" (đã theo hồ sơ). */
    @Test
    fun `chuoi hai ngon ngu va cau ho so khong con noi nguoc`() {
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        listOf(
            "kachi_cast_enabled_pending", "kachi_cast_enabled_apply_now", "kachi_cast_state_on_short",
            "kachi_cast_state_off_short", "kachi_cast_split_session_differs", "kachi_cast_geom_profile_differs",
        ).forEach { key ->
            assertTrue(vi.contains("name=\"$key\""), "VI thiếu $key")
            assertTrue(en.contains("name=\"$key\""), "EN thiếu $key")
        }
        assertFalse(vi.contains("khung hình khi chiếu lên cụm, và Giới thiệu"), "VI: câu cũ loại trừ hình học chiếu — nay sai")
        assertFalse(en.contains("the cluster casting geometry, and About"), "EN: câu cũ loại trừ hình học chiếu — nay sai")
        // Senior review bản gộp 2.84: `rain_defrost_*` (kachi-automation V8, cùng bản) vẫn theo XE (`ProfileScope.DEVICE_KEYS`)
        // mà nằm ở nhóm Tiện nghi xe — câu "mọi thiết lập … trừ …" phải kể tên nó, không thì màn Cài đặt nói ngược phạm vi.
        val noteVi = Regex("""name="kachi_home_profile_note">([^<]*)<""").find(vi)?.groupValues?.get(1).orEmpty()
        val noteEn = Regex("""name="kachi_home_profile_note">([^<]*)<""").find(en)?.groupValues?.get(1).orEmpty()
        assertTrue(noteVi.contains("Tự sấy kính khi mưa"), "VI: câu hồ sơ phải loại trừ tự sấy kính (theo xe): $noteVi")
        assertTrue(noteEn.contains("Auto-defrost when it rains"), "EN: profile note must exclude rain defrost (per car): $noteEn")
        // Senior review bản gộp 2.84 lượt 2 — khoá TỔNG QUÁT thay cho kể tay: mọi mục Cài đặt có khoá theo XE nằm NGOÀI bốn
        // nhóm mà câu đã loại trừ theo tên nhóm (Hệ thống · Giọng nói · Hồ sơ tài xế · Giới thiệu) phải được câu kể đích danh
        // bằng ĐÚNG nhãn của mục. Lượt 1 kể tay còn sót `nav_default_app` (nhóm Dẫn đường, khoá `voice_nav_default_app` theo
        // XE); một mục theo xe thêm sau ở nhóm khác sẽ làm bài này đỏ thay vì để câu nói ngược phạm vi.
        // Lượt 3: nhãn EN `null` từng làm `contains("")` ĐÚNG với mọi câu ⇒ vế EN rỗng nghĩa — nay đòi nhãn có thật.
        fun assertNamed(e: SettingsEntry, why: String) {
            assertTrue(noteVi.contains(e.label), "VI: câu hồ sơ phải kể $why '${e.label}' (${e.id}): $noteVi")
            val labelEn = e.labelEn
            assertTrue(labelEn != null && noteEn.contains(labelEn), "EN: profile note must name $why '$labelEn' (${e.id}): $noteEn")
        }
        fun scopeOf(e: SettingsEntry) = e.prefKey?.let { ProfileScope.scopeOf(it) }
        val namedByGroup = setOf(SettingsGroup.SYSTEM, SettingsGroup.VOICE, SettingsGroup.PROFILES, SettingsGroup.ABOUT)
        val perCar = SettingsCatalog.ENTRIES.filter { e ->
            e.group !in namedByGroup && scopeOf(e) == ProfileScope.Scope.DEVICE
        }
        assertTrue(perCar.isNotEmpty(), "bộ lọc rỗng — bài canh mất tác dụng (ProfileScope/Catalog đổi?)")
        perCar.forEach { e -> assertNamed(e, "mục theo xe") }
        // Senior review bản gộp 2.84 lượt 3 — chiều NGƯỢC: câu loại trừ TRỌN nhóm Hệ thống và Giới thiệu (không rào "hầu
        // hết" như nhóm Giọng nói), nên một mục theo HỒ SƠ nằm trong hai nhóm ấy phải được câu kể đích danh là ngoại lệ.
        // [ĐO code] lượt 2 còn nói cả nhóm Hệ thống theo xe trong khi `launcher_autostart` (hậu tố hồ sơ,
        // `ProfileScope.LAUNCHER_PERSONAL_SUFFIXES`) và `headless_autostart` (ảnh chụp ClusterNav) theo HỒ SƠ.
        val wholeGroup = setOf(SettingsGroup.SYSTEM, SettingsGroup.ABOUT)
        SettingsCatalog.ENTRIES
            .filter { e -> e.group in wholeGroup && scopeOf(e) == ProfileScope.Scope.PROFILE }
            .forEach { e -> assertNamed(e, "mục theo HỒ SƠ trong nhóm câu loại trừ trọn") }
    }

    /**
     * Senior review Pass 1 — `geometryProfileDiffers` so cả VỊ TRÍ khung, nên dòng phụ phải nói cả vị trí (góc trên-trái):
     * hai khung cùng W×H lệch chỗ mà dòng phụ chỉ in W×H là in đúng con số đang hiện — người lái không thấy khác gì.
     */
    @Test
    fun `dong phu ho so noi ca vi tri khung, hai ngon ngu cung so doi so`() {
        listOf("src/main/res/values/strings_kachi.xml", "src/main/res/values-en/strings_kachi.xml").forEach { path ->
            val line = SourceRoots.text(path).lines().single { "name=\"kachi_cast_geom_profile_differs\"" in it }
            (1..5).forEach { n -> assertTrue(line.contains("%$n\$d"), "$path: thiếu %$n\$d — $line") }
        }
        val note = SourceRoots.body(launcher("SettingsSectionsCastGeometry.kt"), "private fun profileNote(target: CastGeometryTarget): String?")
        assertTrue(note.contains("b.left, b.top"), "profileNote phải truyền góc trên-trái vào chuỗi")
    }

    // ── A6 — bong bóng VietMap kẹp cả lúc ĐỌC ──────────────────────────────────────────────────────────────────

    @Test
    fun `vi tri bong bong VietMap kep luc doc`() {
        assertEquals(0, VmOverlayPosition.clampX(-40))
        assertEquals(VmOverlayPosition.CLUSTER_WIDTH - VmOverlayPosition.BUBBLE_WIDTH, VmOverlayPosition.clampX(99_999))
        assertEquals(VmOverlayPosition.CLUSTER_HEIGHT - VmOverlayPosition.BUBBLE_HEIGHT, VmOverlayPosition.clampY(99_999))
        assertEquals(700, VmOverlayPosition.clampX(700), "giá trị hợp lệ không đổi")
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/VmOverlayPosition.kt")
        assertTrue(SourceRoots.body(src, "fun x(ctx: Context): Int").contains("clampX("), "x() phải kẹp lúc đọc")
        assertTrue(SourceRoots.body(src, "fun y(ctx: Context): Int").contains("clampY("), "y() phải kẹp lúc đọc")
    }
}
