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
        order(r, "windowManager.defaultDisplay.displayId", "if (want < 1 || on != want)", "finishAndRemoveTask()", "return", "resumed?.countDown()")
        val manifest = SourceRoots.text("src/main/AndroidManifest.xml")
        val entry = Regex("(?s)<activity\\s+android:name=\"\\.launcher\\.behind\\.StageCoverActivity\".*?/>").find(manifest)?.value
        assertTrue(entry != null && entry.contains("android:exported=\"false\"") && entry.contains("android:excludeFromRecents=\"true\""),
            "lớp che: không exported, không vào danh sách gần đây: $entry")
    }

    @Test
    fun `ben thi hanh - ma rieng cho khong kenh va da tat, dong log no-stage, mot Kit moi luot`() {
        val once = SourceRoots.body(runner, "private fun runOnce(what: String, body: (Kit) -> BehindHomeSequence.Outcome): BehindHomeSequence.Outcome {")
        assertTrue(once.contains("BehindHomeSequence.Result.DISABLED") && once.contains("BehindHomeSequence.Result.NO_CHANNEL"))
        order(once, "disabledReason?.let", "val sh = shell() ?: return", "Kit(seq, StagingDisplay(app), sh, app)")
        val sb = SourceRoots.body(runner, "fun startBehind(x: String, stages: List<BehindHomePlan.Stage>, done: (BehindHomeSequence.Outcome) -> Unit = {}): BehindHomePlan.Stage? {")
        order(sb, "BehindHomePlan.stagingSlot(stages, x)", "Log.i(TAG, \"no-stage X=", "return null")
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
