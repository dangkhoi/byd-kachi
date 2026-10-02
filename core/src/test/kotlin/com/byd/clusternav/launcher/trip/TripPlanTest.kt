package com.byd.clusternav.launcher.trip

import com.byd.clusternav.modules.clustercast.StackParse
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Khoá KẾ HOẠCH CHUYẾN (spec shortcuts-autostart R2.1 · R2.3–R2.5 · C6): mã hoá `trip_apps`, thứ tự nền → nhạc → bình
 * thường, loại trừ đo được trong tiến trình, chờ bằng sự thật (fixture `am stack list` NGUYÊN VĂN máy ảo 02/10 + xe 29/09),
 * và K10 *Mở bình thường* chạy THẬT trên `/bin/sh` với `am` giả.
 */
class TripPlanTest {

    private val home = "com.byd.launcher/com.byd.clusternav.launcher.KachiHome"
    /** Đúng hai dạng `DefaultHome.shownComponents` trả: alias HOME + activity thật. */
    private val homes = listOf(home, "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity")
    private val sig = "com.byd.avc/"

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/$name.txt")

    // ── trip_apps ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `ma hoa trip_apps - khu hoi, de dai khi doc, chat khi ghi`() {
        val list = listOf(TripApp("com.waze", true), TripApp("vn.vietmap.live", false))
        assertEquals("com.waze|B,vn.vietmap.live|N", TripAppCodec.encode(list))
        assertEquals(list, TripAppCodec.decode(TripAppCodec.encode(list)))
        // gói lạ bỏ · kiểu lạ ⇒ Chạy nền · trùng giữ lần đầu · không có `|` ⇒ Chạy nền
        assertEquals(
            listOf(TripApp("a.b", true), TripApp("c.d", true)),
            TripAppCodec.decode("a.b|Z,x y|B,a.b|N,'; rm -rf /|B,c.d"),
        )
        assertEquals(TripAppCodec.MAX, TripAppCodec.decode((1..9).joinToString(",") { "p.a$it|B" }).size)
        assertTrue(TripAppCodec.decode(null).isEmpty() && TripAppCodec.decode(" ").isEmpty())
    }

    @Test
    fun `chi MOT app Mo binh thuong - cai dau thang, chon cai moi thi cai cu ve Chay nen`() {
        assertEquals(
            listOf(TripApp("a.a", false), TripApp("b.b", true)),
            TripAppCodec.sanitize(listOf(TripApp("a.a", false), TripApp("b.b", false))),
        )
        val cur = listOf(TripApp("a.a", false), TripApp("b.b", true))
        assertEquals(listOf(TripApp("a.a", true), TripApp("b.b", false)), TripAppCodec.setMode(cur, "b.b", background = false))
        assertEquals(listOf(TripApp("a.a", true), TripApp("b.b", true)), TripAppCodec.setMode(cur, "a.a", background = true))
        assertEquals(listOf(TripApp("b.b", true)), TripAppCodec.remove(cur, "a.a"))
    }

    @Test
    fun `ngan keo chot - giu kieu cu cua app con chon, app moi la Chay nen, theo thu tu cham`() {
        val cur = listOf(TripApp("a.a", false), TripApp("b.b", true))
        assertEquals(listOf(TripApp("c.c", true), TripApp("a.a", false)), TripAppCodec.apply(cur, listOf("c.c", "a.a")))
    }

    // ── thứ tự + loại trừ ────────────────────────────────────────────────────────────────────────────────────

    private val facts = TripPlan.Facts(
        selfPkg = "com.byd.launcher",
        installed = setOf("com.waze", "vn.vietmap.live", "com.google.android.deskclock", "com.android.settings", "com.byd.launcher", "x.normal"),
        system = setOf("com.android.settings"),
        inSlots = setOf("vn.vietmap.live"),
        cameraKnown = true,
    )

    @Test
    fun `thu tu - moi app nen, roi nhac, roi app binh thuong CUOI`() {
        val cfg = TripConfig(
            apps = listOf(TripApp("x.normal", false), TripApp("com.waze", true), TripApp("com.google.android.deskclock", true)),
            music = TripMusic(TripMusicMode.YT_MUSIC, "son tung"),
        )
        assertEquals(
            listOf(
                TripPlan.Step.Background("com.waze"),
                TripPlan.Step.Background("com.google.android.deskclock"),
                TripPlan.Step.Music(TripMusic(TripMusicMode.YT_MUSIC, "son tung")),
                TripPlan.Step.Normal("x.normal"),
            ),
            TripPlan.steps(cfg, facts),
        )
    }

    @Test
    fun `loai tru do trong tien trinh - chinh minh, chua cai, dang o o, app he thong, camera chua biet`() {
        val cfg = TripConfig(
            apps = listOf(
                TripApp("com.byd.launcher", true), TripApp("gone.app", true), TripApp("vn.vietmap.live", true),
                TripApp("com.android.settings", true), TripApp("x.normal", false),
            ),
        )
        assertEquals(
            listOf(
                TripPlan.Step.Skip("com.byd.launcher", TripPlan.Why.SELF),
                TripPlan.Step.Skip("gone.app", TripPlan.Why.NOT_INSTALLED),
                TripPlan.Step.Skip("vn.vietmap.live", TripPlan.Why.IN_SLOT),
                TripPlan.Step.Skip("com.android.settings", TripPlan.Why.SYSTEM_APP),
                TripPlan.Step.Skip("x.normal", TripPlan.Why.CAMERA_UNKNOWN),
            ),
            TripPlan.steps(cfg, facts.copy(cameraKnown = false)),
        )
        // App hệ thống được MỞ BÌNH THƯỜNG (không move-task nào chạm nó — R0.6 chỉ cấm đẩy ra sau).
        assertEquals(
            listOf(TripPlan.Step.Normal("com.android.settings")),
            TripPlan.steps(TripConfig(listOf(TripApp("com.android.settings", false))), facts),
        )
    }

    @Test
    fun `nhac Tat hay Theo player cua xe - khong co buoc nao`() {
        assertTrue(TripPlan.steps(TripConfig(music = TripMusic(TripMusicMode.CAR)), facts).isEmpty())
        assertTrue(TripConfig(music = TripMusic(TripMusicMode.CAR, "abc")).empty)
        assertFalse(TripConfig(music = TripMusic(TripMusicMode.YOUTUBE)).empty)
    }

    // ── R2.3 — chờ bằng sự thật ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cho theo thu tu - khoi dong, man chinh, o song, HOME yen 3 lan`() {
        assertEquals(TripPlan.Wait.BOOT, TripPlan.waitFor(false, true, 1, 1, 3))
        assertEquals(TripPlan.Wait.HOME_SCREEN, TripPlan.waitFor(true, false, 1, 1, 3))
        assertEquals(TripPlan.Wait.SLOTS, TripPlan.waitFor(true, true, 2, 0, 3))
        assertEquals(TripPlan.Wait.HOME_STEADY, TripPlan.waitFor(true, true, 2, 1, 2))
        assertNull(TripPlan.waitFor(true, true, 2, 1, 3))
        assertNull(TripPlan.waitFor(true, true, 0, 0, 3), "bố cục không có ô app thì không chờ ô sống")
    }

    @Test
    fun `HOME yen - stack DANG HIEN tren cung display 0 chua HOME cua Kachi (fixture nguyen van)`() {
        // [ĐO máy ảo 02/10] KachiHome đỉnh display 0, VietMap trong ô ⇒ yên.
        assertTrue(TripPlan.homeTopVisible(StackParse.parse(fixture("am-stack-list-emulator-2026-10-02-tm1-after")), homes))
        // [ĐO máy ảo 02/10] Kachi bị giết, app ra sau nhà nổi lên che: hai stack RỖNG của KachiHomeActivity nằm trên
        // nhưng không hiện ⇒ stack hiện trên cùng là VietMap ⇒ KHÔNG yên.
        assertFalse(TripPlan.homeTopVisible(StackParse.parse(fixture("am-stack-list-emulator-2026-10-02-tm1-killed-surfaced")), homes))
        // [ĐO xe 29/09] màn nhà ở đỉnh ⇒ yên; app toàn màn ở đỉnh / camera ở đỉnh ⇒ không.
        assertTrue(TripPlan.homeTopVisible(StackParse.parse(fixture("am-stack-list-oncar-2026-09-29-stuck-home-top")), homes))
        assertFalse(TripPlan.homeTopVisible(StackParse.parse(fixture("am-stack-list-oncar-2026-09-29-fullscreen-app-top")), homes))
        assertFalse(TripPlan.homeTopVisible(StackParse.parse(fixture("am-stack-list-oncar-2026-09-29-camera-top-derived")), homes))
        assertFalse(TripPlan.homeTopVisible(emptyList(), homes), "đọc hỏng ⇒ không bao giờ coi là yên")
    }

    /**
     * Phá thử M7 (02/10) lọt khi chỉ có các fixture trên: "đầu danh sách" và "đang hiện" cho CÙNG kết quả ở mọi bản đọc
     * nguyên văn. Ca phân biệt — stack RỖNG không hiện của `KachiHomeActivity` nằm TRÊN stack HOME đang hiện — có thật về
     * hình dạng ([ĐO] `tm1-killed-surfaced`: hai stack rỗng 71/53 nằm trên stack đang hiện), nên dựng nó bằng cách
     * DẪN XUẤT từ chính fixture đó: thay khối stack đang hiện (VietMap) bằng dòng task HOME nguyên văn của `tm1-after`.
     */
    @Test
    fun `HOME yen - stack rong khong hien nam tren HOME khong lam sai ket qua (fixture DAN XUAT)`() {
        val killed = fixture("am-stack-list-emulator-2026-10-02-tm1-killed-surfaced")
        val homeLine = fixture("am-stack-list-emulator-2026-10-02-tm1-after").lines().first { "launcher.KachiHome bounds" in it }
        val derived = killed.lines().joinToString("\n") { l -> if ("vn.vietmap.live/" in l && "taskId=" in l) homeLine else l }
        val entries = StackParse.parse(derived)
        assertEquals(listOf(71, 53, 122), entries.filter { it.displayId == 0 }.map { it.stackId }.distinct(), "thứ tự z giữ nguyên bản đọc")
        assertFalse(entries.first { it.displayId == 0 }.visible, "stack đầu danh sách display 0 phải là stack rỗng KHÔNG hiện")
        assertTrue(TripPlan.homeTopVisible(entries, homes), "stack ĐANG HIỆN trên cùng là HOME ⇒ yên")
    }

    /**
     * Khoá review lượt 2 [P2]: cửa sổ PIP (stack `pinned`, LUÔN trên cùng — A10 r47 `ActivityDisplay.java:302-322`, A12 r34
     * `TaskDisplayArea.java:576-588`) nổi trên màn nhà đang hiện. Lấy "stack đang hiện đầu tiên" thì PIP che mắt phép đo
     * ⇒ streak = 0 tới hết hạn chuyến (GMaps dẫn đường thu về PIP lúc lên xe — mọi app/nhạc của chuyến không chạy).
     * Fixture DẪN XUẤT: khối stack PIP NGUYÊN VĂN (id 54, GMaps) của `camera-under-pip-derived` đặt trên `stuck-home-top`
     * (xe 29/09, HOME đang hiện). Bỏ phép bỏ-qua-PIP ở [com.byd.clusternav.launcher.behind.BehindHomePlan.topVisibleStackId] ⇒ đỏ.
     */
    @Test
    fun `HOME yen - cua so PIP luon tren cung khong che mat phep do (fixture DAN XUAT)`() {
        val pipBlock = fixture("am-stack-list-oncar-2026-09-29-camera-under-pip-derived").split("\n\n").first { "Stack id=54 " in it }
        val e = StackParse.parse(pipBlock + "\n\n" + fixture("am-stack-list-oncar-2026-09-29-stuck-home-top"))
        val first0 = e.first { it.displayId == 0 }
        assertTrue(first0.isPinned && first0.visible, "dẫn xuất đúng: đầu danh sách display 0 là PIP đang hiện")
        assertTrue(TripPlan.homeTopVisible(e, homes), "PIP trên màn nhà đang hiện ⇒ vẫn yên")
        val camUnderPip = StackParse.parse(fixture("am-stack-list-oncar-2026-09-29-camera-under-pip-derived"))
        assertFalse(TripPlan.homeTopVisible(camUnderPip, homes), "camera dưới PIP ⇒ không yên (bỏ PIP không được làm lộ HOME giả)")
    }

    /**
     * Khoá lỗi E2E (5) [ĐO máy ảo 02/10, `c5a-trip-generic`, fixture NGUYÊN VĂN `e2e-standard-home`]: sau lượt
     * `MY_PACKAGE_REPLACED` (`KachiAutostart` → `am start -n …KachiHomeActivity`) màn nhà đang hiện là task
     * `…KachiHomeActivity` trong stack `standard` (stack `home` id=0 RỖNG, nằm đáy). Bản chỉ nhận alias cho streak = 0 suốt
     * 180 s ⇒ chuyến `EXPIRED`, không mở app nào. Đổi bên nào cũng đỏ: bỏ dạng activity khỏi [homes] ⇒ bài này đỏ.
     */
    @Test
    fun `HOME yen - man nha la KachiHomeActivity trong stack standard (fixture nguyen van E2E)`() {
        val e = StackParse.parse(fixture("am-stack-list-emulator-2026-10-02-e2e-standard-home"))
        assertTrue(e.none { it.displayId == 0 && it.activityType == "home" }, "stack home RỖNG (không task nào) trong bản đọc này")
        assertTrue(TripPlan.homeTopVisible(e, homes), "màn nhà Kachi (dạng activity) đang hiện trên cùng ⇒ yên")
        assertFalse(TripPlan.homeTopVisible(e, listOf(home)), "chỉ nhận alias ⇒ không bao giờ yên (lỗi E2E 5)")
        assertEquals(TripPlan.Normal.UNREAD, TripPlan.normalOutcome("", e, sig, homes), "HOME vẫn ở trước ⇒ thử lại, không bỏ")
    }

    // ── R2.5 — Mở bình thường (K10) ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `lenh mo - khong nhay don, dau dola lop long duoc thoat, component la bi tu choi`() {
        val yt = "com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell\$HomeActivity"
        val cmd = TripPlan.launchCmd(yt)
        assertFalse(cmd.contains('\''), cmd)
        assertTrue(cmd.endsWith("-n com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell\\\$HomeActivity"), cmd)
        assertTrue(cmd.startsWith("am start --display 0 --windowingMode 1 "), cmd)
        assertThrows(IllegalArgumentException::class.java) { TripPlan.launchCmd("a/b;reboot") }
        assertThrows(IllegalArgumentException::class.java) { TripPlan.launchCmd("a/b'c") }
    }

    private fun runSh(cmd: String, stacks: String, dir: Path): List<String> {
        val bin = Files.createDirectories(dir.resolve("bin"))
        val log = dir.resolve("log").toFile().apply { writeText("") }
        val fx = dir.resolve("stacks.txt").toFile().apply { writeText(stacks) }
        bin.resolve("am").toFile().apply {
            writeText("#!/bin/sh\necho \"am \$*\" >> \"\$LOG\"\nif [ \"\$1 \$2\" = \"stack list\" ]; then cat \"\$FIXTURE\"; fi\nexit 0\n")
            setExecutable(true)
        }
        val pb = ProcessBuilder("/bin/sh", "-c", cmd).redirectErrorStream(true)
        pb.environment().apply { put("PATH", "$bin:/usr/bin:/bin"); put("LOG", log.path); put("FIXTURE", fx.path) }
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue(p.waitFor(20, TimeUnit.SECONDS), "shell treo: $out")
        return log.readLines().filter { it.isNotBlank() }
    }

    @Test
    fun `K10 chay that - mo khi HOME hien, khong mo khi camera hay app khac o truoc, dola den nguyen chu`(@TempDir dir: Path) {
        if (!File("/bin/sh").canExecute()) return
        val yt = "com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell\$HomeActivity"
        val cmd = TripPlan.normalCmd(sig, homes, yt)
        val opened = runSh(cmd, fixture("am-stack-list-oncar-2026-09-29-stuck-home-top"), dir.resolve("a"))
        assertEquals(2, opened.size, "$opened")
        assertTrue(opened[1].endsWith("-n $yt"), "shell phải đưa tới `am` ĐÚNG chữ `\$HomeActivity`: ${opened[1]}")
        assertEquals(listOf("am stack list"), runSh(cmd, fixture("am-stack-list-oncar-2026-09-29-camera-top-derived"), dir.resolve("b")))
        assertEquals(listOf("am stack list"), runSh(cmd, fixture("am-stack-list-oncar-2026-09-29-fullscreen-app-top"), dir.resolve("c")))
        assertEquals(listOf("am stack list"), runSh(cmd, "", dir.resolve("d")))
        // [ĐO E2E 02/10] màn nhà là `…KachiHomeActivity` trong stack standard ⇒ vẫn MỞ (lỗi E2E 5: trước đây không mở).
        val std = runSh(cmd, fixture("am-stack-list-emulator-2026-10-02-e2e-standard-home"), dir.resolve("e"))
        assertEquals(2, std.size, "$std")
        assertTrue(std[1].endsWith("-n $yt"), std[1])
    }

    @Test
    fun `ket qua K10 - mo, camera (thu lai), app khac o truoc (bo), doc hong (thu lai)`() {
        val homeTop = StackParse.parse(fixture("am-stack-list-oncar-2026-09-29-stuck-home-top"))
        assertEquals(TripPlan.Normal.OPENED, TripPlan.normalOutcome("Starting: Intent { cmp=x/y }", homeTop, sig, homes))
        assertEquals(TripPlan.Normal.CAMERA, TripPlan.normalOutcome("", StackParse.parse(fixture("am-stack-list-oncar-2026-09-29-camera-top-derived")), sig, homes))
        assertEquals(TripPlan.Normal.OTHER_FRONT, TripPlan.normalOutcome("", StackParse.parse(fixture("am-stack-list-oncar-2026-09-29-fullscreen-app-top")), sig, homes))
        assertEquals(TripPlan.Normal.UNREAD, TripPlan.normalOutcome("", emptyList(), sig, homes))
        assertEquals(TripPlan.Normal.UNREAD, TripPlan.normalOutcome("Starting: Intent\nError: Activity not started", homeTop, sig, homes))
    }
}
