package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.trip.TripStepCode
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ L4 (FIELD-286-BEHIND) — bài canh TĨNH cho phần `:app` của bản vá ═══════════════════════════════════════════════
 *
 * Owner 03/10 (xe thật, 2.86): chạy nền + nhạc YouTube không chạy, Cài đặt vẫn "đã chạy". Mỗi bài khoá một điều đã ĐO trên
 * máy ảo 03/10 (`p3/e2e-L4/…`) hoặc một quyết định D1–D5 của điều phối (thay owner):
 *  - D2(a) màn ảo ẨN: cùng cờ 8|256 với màn ảo ô, KHÔNG lệnh `wm` (R0.7), đăng ký/gỡ đăng ký với cổng ownership;
 *  - lớp che phải `onResume` TRƯỚC khi dựng giữ chỗ ([ĐO `e6-hidden` lượt 1]: giữ chỗ bị tỉa `recent-task-trimmed`);
 *  - lớp che tự gỡ nếu bị đẩy sang display khác (rào an toàn cho ROM không tôn trọng cờ 256 — [CHƯA BIẾT] trên ROM BYD);
 *  - D1(b) mỗi mã bước có MỘT câu ở CẢ 5 thư mục tài nguyên;
 *  - D5 chip *Chạy nền* của app hệ thống mờ + lý do, đo CÙNG phép với chuyến (`InstalledApps.isSystem`).
 */
class L4TripBehindContractTest {

    private val staging by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/behind/StagingDisplay.kt") }
    private val cover by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/behind/StageCoverActivity.kt") }
    private val runner by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/behind/BehindHomeRunner.kt") }
    private val result by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsTripResult.kt") }
    private val settings by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSectionsTrip.kt") }
    private val start by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/trip/TripStart.kt") }

    private fun order(src: String, vararg marks: String) {
        var at = -1
        for (m in marks) {
            val i = src.indexOf(m, at + 1)
            assertTrue(i > at, "thứ tự sai/thiếu: '$m' phải đứng sau mốc trước trong:\n$src")
            at = i
        }
    }

    @Test
    fun `man ao an - cung co 8 or 256 voi o, khong lenh wm, dang ky truoc khi tra id, go dang ky khi nha`() {
        assertTrue(staging.contains("const val FLAGS = 8 or 256"), "cùng cờ màn ảo ô (OWN_CONTENT_ONLY | DESTROY_CONTENT_ON_REMOVAL)")
        val host = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/VdAppHost.kt")
        assertTrue(host.contains("h.surface, 8 or 256)"), "cờ của màn ảo ô đổi thì màn ảo ẩn phải đổi theo")
        assertFalse(staging.contains("\"wm ") || staging.contains("settings put"), "R0.7: không thêm trạng thái hệ thống bền")
        val create = SourceRoots.body(staging, "override fun create(): Int? {")
        // Chủ DUY NHẤT của mọi màn ảo Kachi là SlotVdOwner (luật `SlotHostingLifecycleContractTest`); "ô" ÂM riêng mỗi lượt
        // ⇒ không trùng ô thật, hai lượt dàn không nhả màn ảo của nhau (rào nhả D2).
        order(create, "createVirtualDisplay(", "registerLauncherVirtualDisplay(id)", "NEXT_KEY.getAndDecrement()",
            "SlotVdOwner.adopt(OWNER, k, name, VdLease(v, id, dispatcher::unregisterLauncherVirtualDisplay))")
        assertTrue(staging.contains("val NEXT_KEY = AtomicInteger(-1)"), "khoá âm, giảm dần")
        val release = SourceRoots.body(staging, "fun release() {")
        order(release, "SlotVdOwner.release(OWNER, k)", "r.close()", "quitSafely()")
    }

    @Test
    fun `lop che - cho onResume tren dung man ao truoc khi tra ve, tu go neu nam sai display`() {
        val c = SourceRoots.body(staging, "override fun cover(vd: Int): Boolean = try {")
        order(c, "StageCoverActivity.arm(vd)", "setLaunchDisplayId(vd)", "app.startActivity(i, opts)", "StageCoverActivity.awaitResumed(COVER_RESUME_MS)")
        val r = SourceRoots.body(cover, "override fun onResume() {")
        // Soát 2.87 · P3 — ĐỔI GHIM có lý do: đọc display đi qua `shownOn()` (API hiện hành theo mức API, xem bài dưới).
        order(r, "val on = shownOn()", "if (want < 1 || on != want)", "finishAndRemoveTask()", "return", "resumed?.countDown()")
        val manifest = SourceRoots.text("src/main/AndroidManifest.xml")
        val entry = Regex("(?s)<activity\\s+android:name=\"\\.launcher\\.behind\\.StageCoverActivity\".*?/>").find(manifest)?.value
        assertTrue(entry != null && entry.contains("android:exported=\"false\"") && entry.contains("android:excludeFromRecents=\"true\""),
            "lớp che: không exported, không vào danh sách gần đây: $entry")
    }

    /**
     * Soát 2.87 · P3 + soát vòng 2 (gộp bản vá 04/10 — ĐỔI tên/KDoc/danh sách có lý do: bản cũ mô tả nhánh API 31 của F2 đã
     * bị bỏ khi gộp, thân bài lại khẳng định điều ngược lại). Hình cuối cùng:
     *  - `StagingDisplay` GIỮ `Display.getRealMetrics` của display 0 (deprecated API 31) CÓ CHỦ Ý, một chỗ, chặn
     *    DEPRECATION đúng MỘT dòng ngay trên nó. Lý do thật (KDoc ở chỗ gọi): A10 = cỡ logic display 0 [ĐO nguồn r47
     *    `Display.java:1074-1080`]; A12 trả maxBounds của cấu hình NGỮ CẢNH — CÙNG nguồn với bản thay `maximumWindowMetrics`
     *    [ĐO nguồn r34 `Display.java:1429-1469`] ⇒ bản thay không hơn gì, cỡ trên DL5 lúc chiếu cụm [CHƯA BIẾT] (dòng log
     *    `stage create … phys=` để đo trên xe).
     *  - Chỉ `StageCoverActivity` rẽ nhánh theo mức API: `Context.getDisplay()` từ API 30, `getDefaultDisplay` (deprecated
     *    API 30) chỉ ở nhánh API 29, chặn DEPRECATION trên đúng hàm đó.
     *  - Không còn import mồ côi của bản F2 (`Build`, `WindowManager`).
     */
    @Test
    fun `getRealMetrics display 0 giu co chu y, chi lop che re nhanh theo muc API`() {
        val create = SourceRoots.body(staging, "override fun create(): Int? {")
        assertTrue("dm.getDisplay(Display.DEFAULT_DISPLAY)?.getRealMetrics(it)" in create, "cỡ lấy từ display 0 (giữ lời gọi 2.86)")
        assertFalse("maximumWindowMetrics" in stripComments(staging), "không đọc cỡ theo cấu hình ngữ cảnh (A12: cùng nguồn maxBounds, KDoc)")
        assertEquals(1, Regex("""getRealMetrics\(""").findAll(staging).count(), "getRealMetrics chỉ ở MỘT chỗ")
        assertEquals(1, Regex("""@Suppress\("DEPRECATION"\)""").findAll(staging).count(), "chặn DEPRECATION đúng một chỗ ở StagingDisplay")
        assertTrue(Regex("""@Suppress\("DEPRECATION"\)\s*\n\s*val m = DisplayMetrics\(\)\.also \{ dm\.getDisplay\(Display\.DEFAULT_DISPLAY\)\?\.getRealMetrics\(it\) \}""")
            .containsMatchIn(staging), "chặn DEPRECATION nằm NGAY trên dòng getRealMetrics, không phủ cả hàm/lớp")
        // Soát vòng 3 [P3] — ĐỔI GHIM có lý do: "WxH ≠ PxQ ⇒ phải sửa" báo động GIẢ khi màn xoay / có `wm size` (mode không xoay,
        // không theo override) ⇒ dòng log mang thêm `rot=` + phân loại `so=` của [StageSize] (khớp · xoay · lệch-đối-chiếu…).
        assertTrue("phys=\$phys" in create && "?.mode }" in create, "dòng log có cỡ vật lý để đo DL5 trên xe ([CHƯA BIẾT])")
        assertTrue("StageSize.verdict(m.widthPixels, m.heightPixels, mode?.physicalWidth, mode?.physicalHeight)" in create &&
            "rot=\$rot so=\${so.tag}" in create, "dòng log phân loại cỡ theo cả hai chiều xoay, kèm rotation")
        assertEquals(emptyList<String>(), unusedImports(staging), "StagingDisplay không còn import mồ côi")
        val shown = SourceRoots.body(cover, "private fun shownOn(")
        order(shown, "Build.VERSION.SDK_INT >= Build.VERSION_CODES.R", "display?.displayId ?: Display.INVALID_DISPLAY", "legacyDisplayId()")
        assertEquals(1, Regex("""defaultDisplay""").findAll(cover).count(), "getDefaultDisplay chỉ ở MỘT chỗ")
        assertTrue("windowManager.defaultDisplay.displayId" in SourceRoots.body(cover, "private fun legacyDisplayId("))
        Regex("""@Suppress\("DEPRECATION"\)[^\n]*\n\s*(private fun \w+)""").findAll(cover).map { it.groupValues[1] }.toList().let { fns ->
            assertEquals(listOf("private fun legacyDisplayId"), fns, "chặn DEPRECATION chỉ trên hàm của nhánh API 29")
        }
        assertEquals(1, Regex("""@Suppress\("DEPRECATION"\)""").findAll(cover).count(), "không chặn DEPRECATION ở chỗ khác của lớp che")
    }

    /** Bỏ chú thích `//…` và `/* … */` (đủ cho bài canh import — không phải bộ phân tích Kotlin). */
    private fun stripComments(src: String): String =
        src.replace(Regex("""(?s)/\*.*?\*/"""), " ").replace(Regex("""//[^\n]*"""), " ")

    /** Import mà tên (hoặc bí danh) không xuất hiện trong MÃ (ngoài dòng import, ngoài chú thích) — Kotlin không báo lỗi. */
    private fun unusedImports(src: String): List<String> {
        val code = stripComments(src)
        val imports = Regex("""(?m)^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$""").findAll(code).toList()
        val body = Regex("""(?m)^import\s+.*$""").replace(code, " ")
        return imports.mapNotNull { m ->
            val name = m.groupValues[2].ifEmpty { m.groupValues[1].substringAfterLast('.') }
            m.groupValues[1].takeIf { name != "*" && !Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(body) }
        }
    }

    @Test
    fun `ben thi hanh - ma rieng cho khong kenh va da tat, dong log no-stage, mot Kit moi luot`() {
        val once = SourceRoots.body(runner, "private fun runOnce(what: String, body: (Kit) -> BehindHomeSequence.Outcome): BehindHomeSequence.Outcome {")
        assertTrue(once.contains("BehindHomeSequence.Result.DISABLED") && once.contains("BehindHomeSequence.Result.NO_CHANNEL"))
        // Soát vòng 2 — ĐỔI GHIM có lý do: lượt thu hồi màn ảo ẩn chạy TRƯỚC cổng DISABLED (bài dưới); hai mã 0-lệnh vẫn
        // đứng trước khi dựng chuỗi, mỗi lượt vẫn một `StagingDisplay` mới.
        order(once, "val sh = shell()", "val hidden = StagingDisplay(app)", "disabledReason?.let", "if (sh == null) return",
            "BehindHomeSequence(", "Kit(seq, hidden, sh, app)")
        val sb = SourceRoots.body(runner, "fun startBehind(x: String, stages: List<BehindHomePlan.Stage>, done: (BehindHomeSequence.Outcome) -> Unit = {}): BehindHomePlan.Stage? {")
        order(sb, "BehindHomePlan.stagingSlot(stages, x)", "Log.i(TAG, \"no-stage X=", "return null")
    }

    /**
     * Review 287 [P3]: màn ảo ẩn bị GIỮ (K7 bị rào chặn / đọc hỏng / chuỗi ném giữa chừng — `failed()` cố ý không nhả) trước đây
     * sống tới khi BYD giết Kachi (màn ảo + luồng `kachi-stage` + `ImageReader` cỡ display 0, rò thêm mỗi lượt). Nay: sổ [LIVE]
     * cả tiến trình; MỖI lượt `kachi-behind` thu hồi TRƯỚC thân lượt (quyết định + rào nhả ở `:core` `HiddenStageReclaim`, có test).
     *
     * Soát vòng 2 [P3] (ĐỔI GHIM có lý do): thu hồi đứng TRƯỚC cả cổng `DISABLED`. Bản trước trả `DISABLED` ở dòng đầu ⇒ lượt
     * giữ màn ảo mà cũng là lượt TẮT BEHIND-HOME (`ANCHOR_IN_FRONT` khi K7 bị camera chặn) thì từ đó mọi lượt dừng trước thu
     * hồi ⇒ đúng cái rò bản vá vòng 1 định đóng. Thu hồi chỉ cần kênh (một lệnh chỉ đọc + nhả trong tiến trình).
     */
    @Test
    fun `man ao an bi giu - so LIVE ca tien trinh, moi luot thu hoi truoc than luot`() {
        val once = SourceRoots.body(runner, "private fun runOnce(what: String, body: (Kit) -> BehindHomeSequence.Outcome): BehindHomeSequence.Outcome {")
        order(once, "val sh = shell()", "val hidden = StagingDisplay(app)", "if (sh != null) HiddenStageReclaim.run(sh, hidden, app.packageName)",
            "disabledReason?.let", "if (sh == null) return", "Kit(seq, hidden, sh, app)", "body(kit)")
        assertEquals(1, Regex("""HiddenStageReclaim\.run\(""").findAll(once).count(), "một lượt thu hồi mỗi lượt chạy")
        val create = SourceRoots.body(staging, "override fun create(): Int? {")
        order(create, "SlotVdOwner.adopt(", "vdId = id", "LIVE[id] = this")
        order(SourceRoots.body(staging, "fun release() {"), "SlotVdOwner.release(OWNER, k)", "LIVE.remove(it, this)", "vdId = null")
        assertTrue(staging.contains("override fun kept(): Collection<Int> = LIVE.keys.filter { it != vdId }"), "không tính màn ảo của chính lượt")
        assertTrue(SourceRoots.body(staging, "override fun reclaim(vd: Int) {").contains("LIVE[vd]?.takeIf { it !== this }?.release()"),
            "nhả bằng release() của CHÍNH lượt tạo nó (lease + mặt vẽ + luồng)")
    }

    /** D1(b): `when` của [reasonRes] phải phủ ĐỦ mã, và mỗi khoá câu phải có ở CẢ 5 thư mục (thiếu ⇒ Android lùi về tiếng Việt). */
    @Test
    fun `moi ma buoc co MOT cau, du 5 tieng`() {
        val fn = SourceRoots.body(result, "internal fun reasonRes(code: TripStepCode): Int = when (code) {")
        val pairs = Regex("TripStepCode\\.([A-Z_]+) -> R\\.string\\.([a-z_]+)").findAll(fn).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(TripStepCode.values().map { it.name }.toSet(), pairs.keys, "mỗi mã một nhánh")
        assertEquals(pairs.size, pairs.values.toSet().size, "mỗi mã một câu riêng")
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { folder ->
            val xml = SourceRoots.text("src/main/res/$folder/strings_kachi.xml")
            (pairs.values + listOf("kachi_trip_res_noop", "kachi_trip_res_partial", "kachi_trip_step", "kachi_trip_step_music",
                "kachi_trip_system_bg", "kachi_trip_music_link_hint", "kachi_trip_now_running", "kachi_trip_now_wait_channel",
                "kachi_trip_now_wait_home")).forEach { key ->
                assertTrue(xml.contains("<string name=\"$key\">"), "$folder thiếu $key")
            }
        }
        val status = SourceRoots.body(settings, "private fun statusRows(list: LinearLayout, context: Context, rows: SettingsRows) {")
        order(status, "TripStart.now(context)", "TripGate.Now.NOT_RUN ->", "ShellReadiness.isUp()", "TripStart.last(context) ?: return",
            "tripResultRows(list, context, rows, r)")
    }

    @Test
    fun `D5 - chip Chay nen cua app he thong mo, cham chi noi ly do, do CUNG phep voi chuyen`() {
        val paint = SourceRoots.body(settings, "private fun paint() {")
        order(paint, "InstalledApps.isSystem(context, a.pkg)", "R.string.kachi_trip_system_bg", "options.indexOfFirst { it.first == BG }",
            "alpha = DIM", "setOnClickListener { Toast.makeText(context, reason", "list.addView(rows.note(reason))")
        assertTrue(start.contains("private fun isSystem(pkg: String): Boolean = InstalledApps.isSystem(app, pkg)"), "chuyến đo bằng CÙNG phép")
    }

    @Test
    fun `D3 iii - kieu khong phat tiep ma o trong thi noi can link`() {
        assertTrue(settings.contains("if (!cfg.music.mode.resumable && q.isEmpty()) extra.addView(rows.note(context.getString(R.string.kachi_trip_music_link_hint)))"))
    }
}
