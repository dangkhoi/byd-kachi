package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.behind.SlotReturn.Back
import com.byd.clusternav.launcher.behind.SlotReturn.Where
import com.byd.clusternav.launcher.camera.CameraGuard
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
 * Ô ⇄ TOÀN MÀN (spec shortcuts-autostart §4.4.3 dòng 9 · R1.8 · T-M2/T-M6). Fixture NGUYÊN VĂN `am stack list` chụp trên máy
 * ảo 02/10 (`docs/diagnostics/behind-home-emulator-2026-10-01/finish/`), VietMap task 2613, ô = màn ảo 173:
 *  - `tm2-in-slot`  — trước K7: VietMap ở màn ảo 173, màn nhà đang hiện;
 *  - `tm2-detached` — sau K7: VietMap stack 302 trên display 0, ĐANG HIỆN, màn nhà ẩn;
 *  - `tm2-detached-home` — sau phím HOME: VietMap stack 302 ẩn ngay dưới màn nhà;
 *  - `tm2-returned` — sau K8: VietMap về màn ảo 173 (stack 303), pid giữ;
 *  - `tm2-intent-after` — sau Intent từ HOME (T-M2 a): KHÔNG đổi gì (`am_new_intent` tại chỗ);
 *  - `tm6-marked-behind` / `tm6-after-k8` — VietMap (2613) + YT Music (2614) trong S do Kachi tạo (dấu bền), Waze (2616)
 *    sau màn nhà KHÔNG dấu; rồi K8 VietMap về màn ảo 173.
 */
class SlotReturnTest {

    private fun text(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-02-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture $name")

    private fun parse(name: String) = StackParse.parse(text(name))

    private val vm = "vn.vietmap.live"
    private val vmComp = "vn.vietmap.live/vn.vietmap.live.MainActivity"
    private val homes = listOf(
        "com.byd.launcher/com.byd.clusternav.launcher.KachiHome",
        "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity",
    )
    private val k12 = "GO_HOME_FENCE"

    // ── Lệnh ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `K7 trung tung byte lenh da do T-M2(b), khong nhay don, dau dola duoc thoat`() {
        // Viết tay từ `finish/tm2/b1-vietmap-k7/k7.txt` (lệnh chạy bằng `su 2000`, đã bỏ cặp nháy) — không suy từ mã.
        assertEquals(
            "am start --display 0 --windowingMode 1 -f 0x20000000 -a android.intent.action.MAIN " +
                "-c android.intent.category.LAUNCHER -n vn.vietmap.live/.MainActivity",
            SlotReturn.detachCmd("vn.vietmap.live/.MainActivity"),
        )
        assertTrue(SlotReturn.detachCmd("com.google.android.youtube/com.google.android.apps.youtube.app.application.Shell\$HomeActivity")
            .endsWith("Shell\\\$HomeActivity"))
        assertThrows(IllegalArgumentException::class.java) { SlotReturn.detachCmd("x/y'; reboot; '") }
        assertThrows(IllegalArgumentException::class.java) { SlotReturn.detachCmd("x y/z") }
    }

    @Test
    fun `rao K7 - camera da biet thi dung dang K10, chua biet thi chi cong man nha dang hien`() {
        val k7 = SlotReturn.detachCmd(vmComp)
        assertEquals(CameraGuard.unlessCameraOnHome("com.byd.avc/", homes, k7), SlotReturn.guardedDetachCmd("com.byd.avc/", homes, vmComp))
        val noSig = SlotReturn.guardedDetachCmd(null, homes, vmComp)
        assertFalse(noSig.contains(";; *) "), "không có nhánh 'mọi thứ khác' chạy lệnh: $noSig")
        assertTrue(noSig.contains(homes.joinToString("|") { "*\"$it \"*" } + ") $k7 ;;"), noSig)
        assertThrows(IllegalArgumentException::class.java) { CameraGuard.onHomeUnlessCamera(null, emptyList(), k7) }
        assertThrows(IllegalArgumentException::class.java) { CameraGuard.onHomeUnlessCamera(null, homes, "echo 'x'") }
    }

    /** Chạy THẬT trên `/bin/sh` với `am` giả in fixture máy ảo — cùng khuôn `CameraGuardTest`. */
    @Test
    fun `rao K7 chay that - man nha dang hien thi mo, app dang o truoc hay doc hong thi khong`(@TempDir dir: Path) {
        if (!File("/bin/sh").canExecute()) return
        val cmd = SlotReturn.guardedDetachCmd(null, homes, vmComp)
        val k7 = SlotReturn.detachCmd(vmComp)
        assertEquals(listOf("am stack list", k7), run(cmd, text("tm2-in-slot"), dir.resolve("a")), "màn nhà đang hiện ⇒ K7 chạy")
        assertEquals(listOf("am stack list"), run(cmd, text("tm2-detached"), dir.resolve("b")), "app đang toàn màn ⇒ màn nhà ẩn ⇒ không chạy")
        assertEquals(listOf("am stack list"), run(cmd, "", dir.resolve("c")), "đọc hỏng ⇒ không chạy")
        // [ĐO máy ảo 02/10] màn camera ĐỨNG THAY (`com.byd.avc/android.app.Activity`, APK không mã chỉ có trên máy ảo) ở
        // đỉnh display 0 ⇒ màn nhà `visible=false` ⇒ không chạy, dù biết hay chưa biết dấu hiệu camera.
        val cam = text("cam-standin-top")
        assertEquals(listOf("am stack list"), run(cmd, cam, dir.resolve("d")))
        val dl3 = SlotReturn.guardedDetachCmd("com.byd.avc/", homes, vmComp)
        assertEquals(listOf("am stack list"), run(dl3, cam, dir.resolve("e")))
        // DẪN XUẤT: camera là lớp phủ TRONG SUỐT (màn nhà vẫn `visible=true` dưới nó). Dấu hiệu đã biết (DL3) ⇒ nhánh camera
        // đứng trước ⇒ không chạy. Chưa biết (DL5/hồ sơ chung) ⇒ CHẠY — giới hạn ghi rõ ở spec (cú chạm diễn ra trên màn nhà,
        // cùng mức với đường Intent của ngăn kéo) [CHƯA BIẾT trên ROM BYD: OC-7].
        val overlay = cam.lines().joinToString("\n") { l -> if ("launcher.KachiHome bounds" in l) l.replace("visible=false", "visible=true") else l }
        assertEquals(listOf("am stack list"), run(dl3, overlay, dir.resolve("f")))
        assertEquals(listOf("am stack list", k7), run(cmd, overlay, dir.resolve("g")))
    }

    private fun run(cmd: String, stacks: String, dir: Path): List<String> {
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

    // ── Phân loại vị trí task (đọc thật) ─────────────────────────────────────────────────────────────────────

    @Test
    fun `vi tri task tren tung buoc do T-M2`() {
        assertEquals(Where.IN_SLOT, SlotReturn.whereIs(parse("tm2-in-slot"), 2613, 173))
        assertEquals(Where.FRONT_MAIN, SlotReturn.whereIs(parse("tm2-detached"), 2613, 173))
        assertEquals(Where.HIDDEN_MAIN, SlotReturn.whereIs(parse("tm2-detached-home"), 2613, 173))
        assertEquals(Where.IN_SLOT, SlotReturn.whereIs(parse("tm2-returned"), 2613, 173))
        assertEquals(Where.GONE, SlotReturn.whereIs(parse("tm2-returned"), 9999, 173))
        // T-M2 (a): Intent từ HOME không đưa app ra khỏi ô — bản đọc sau Intent vẫn IN_SLOT ⇒ `fullByIntent = false`.
        assertEquals(Where.IN_SLOT, SlotReturn.whereIs(parse("tm2-intent-after"), 2613, 173))
        assertEquals(vmComp, SlotReturn.slotTask(parse("tm2-in-slot"), 173, vm)?.comp)
        assertNull(SlotReturn.slotTask(parse("tm2-detached"), 173, vm), "app đã rời ô ⇒ không có đối tượng cho K7")
        assertNull(SlotReturn.slotTask(parse("tm2-in-slot"), 0, vm), "display 0 không phải ô")
    }

    @Test
    fun `R1-8 chi task MANG DAU va dung hinh S - app nguoi dung tu mo roi bam HOME khong doi hanh vi`() {
        val e = parse("tm6-marked-behind")
        val marks = mapOf(2613 to vm, 2614 to "com.google.android.apps.youtube.music")
        assertEquals(2613, SlotReturn.markedBehind(e, marks, vm)?.taskId)
        assertNull(SlotReturn.markedBehind(e, marks, "com.waze"), "Waze sau màn nhà nhưng KHÔNG mang dấu ⇒ golden")
        assertNull(SlotReturn.markedBehind(e, mapOf(2613 to "com.waze"), vm), "dấu sai gói ⇒ không nhận")
        assertNull(SlotReturn.markedBehind(parse("tm6-after-k8"), marks, vm), "đã về ô ⇒ không còn sau màn nhà")
        assertEquals(Back.IN_SLOT, SlotReturn.afterK8(parse("tm6-after-k8"), 2613, 173))
        assertEquals(Back.GOLDEN, SlotReturn.afterK8(e, 2613, 173), "K8 không ăn (vẫn ẩn display 0) ⇒ golden")
        assertEquals(Back.FRONT_RESTORED, SlotReturn.afterK8(parse("tm2-detached"), 2613, 173))
    }

    // ── Chuỗi (shell ghi âm) ─────────────────────────────────────────────────────────────────────────────────

    private inner class Rig(reads: List<String>) {
        val log = ArrayList<String>()
        private val q = ArrayDeque(reads)
        private var last = ""
        val sh: (String) -> String = { cmd ->
            log += cmd
            if (cmd == BehindHomePlan.LIST_CMD) (q.removeFirstOrNull() ?: last).also { last = it } else ""
        }
        val seq = SlotReturnSequence(sh, k12, sleep = {})
    }

    private val list = BehindHomePlan.LIST_CMD
    private val k8 = BehindHomePlan.bringToFrontCmd(173, vmComp)

    @Test
    fun `tach ra toan man - doc, K7 qua rao, doc lai thay app truoc display 0`() {
        val r = Rig(listOf("tm2-in-slot", "tm2-detached").map(::text))
        val out = r.seq.detach(173, vm, null, homes)
        assertEquals(2613, out.taskId, out.line)
        assertEquals(listOf(list, SlotReturn.guardedDetachCmd(null, homes, vmComp), list), r.log)
    }

    @Test
    fun `tach khong len (rao chan) - khong lap lenh, khong K12, bao that bai`() {
        val r = Rig(listOf(text("tm2-in-slot")))
        val out = r.seq.detach(173, vm, null, homes)
        assertNull(out.taskId, out.line)
        assertEquals(1, r.log.count { it.contains("-f 0x20000000") }, "K7 bắn đúng MỘT lần: ${r.log}")
        assertFalse(r.log.contains(k12))
        assertEquals(1 + SlotReturnSequence.DETACH_READS, r.log.count { it == list }, "đọc lại có trần")
    }

    /**
     * Review lượt 4 [P2]: K7 chạy, app rời ô, nhưng người dùng bấm HOME (hoặc camera lên trên) TRƯỚC lần đọc thấy nó toàn màn
     * ⇒ mọi lần đọc lại là `tm2-detached-home` (NGUYÊN VĂN: app ẩn ngay dưới màn nhà). Bản cũ trả `null` ⇒ host đo lại ô ⇒ bộ
     * đo chưa từng thấy app ở ô nên không bao giờ kết luận ⇒ ô ĐEN câm. Nay: K8 về ô ngay trong cùng chuỗi.
     */
    @Test
    fun `K7 xong ma app an sau man nha truoc lan doc thay toan man - K8 ve o ngay, khong o den`() {
        val r = Rig(listOf(text("tm2-in-slot")) + List(SlotReturnSequence.DETACH_READS) { text("tm2-detached-home") } + text("tm2-returned"))
        val out = r.seq.detach(173, vm, null, homes)
        assertNull(out.taskId, out.line)
        assertEquals(Back.IN_SLOT, out.back, out.line)
        assertEquals(listOf(list, SlotReturn.guardedDetachCmd(null, homes, vmComp)) + List(SlotReturnSequence.DETACH_READS) { list } + listOf(k8, list), r.log)
        // K8 không ăn (vẫn ẩn display 0) ⇒ bên gọi đi golden; không K12 (app không ở trước).
        val stuck = Rig(listOf(text("tm2-in-slot"), text("tm2-detached-home")))
        val o2 = stuck.seq.detach(173, vm, null, homes)
        assertEquals(Back.GOLDEN, o2.back, o2.line)
        assertFalse(stuck.log.contains(k12))
    }

    @Test
    fun `K7 xong ma app dong mat (doc duoc, khong con task) - GONE, khong K8`() {
        // DẪN XUẤT từ `tm2-detached-home`: bỏ dòng task VietMap (2613) — app đóng trong lúc đọc lại.
        val gone = text("tm2-detached-home").lines().filterNot { "taskId=2613" in it }.joinToString("\n")
        val r = Rig(listOf(text("tm2-in-slot"), gone))
        val out = r.seq.detach(173, vm, null, homes)
        assertNull(out.taskId)
        assertEquals(Back.GONE, out.back, out.line)
        assertFalse(r.log.any { it.startsWith("am start --display 173") }, r.log.toString())
        // Đọc hỏng toàn bộ sau K7 ⇒ KHÔNG đoán là đã đóng (back = null — giữ hành vi đo lại ô).
        val unread = Rig(listOf(text("tm2-in-slot"), ""))
        assertNull(unread.seq.detach(173, vm, null, homes).back)
    }

    /**
     * Review lượt 5 [P3]: K7 đưa app rời ô sang display 0 nhưng mọi lần đọc lại nó ở ngoài hai hình đã đo — DẪN XUẤT từ
     * `tm2-detached` (NGUYÊN VĂN) với task 2613 đổi `visible=true` → `visible=false`: stack 302 vẫn là ĐỈNH display 0 (ROM
     * chậm hơn 8 × 250 ms / màn tắt) ⇒ `ELSEWHERE`. Bản trước trả `null` ⇒ host đo lại ô ⇒ ô ĐEN câm (bộ đo mới chưa từng thấy
     * app ở ô). Nay coi như toàn màn: thẻ + K8 khi màn nhà hiện lại; 0 lệnh thêm, không K8 tại chỗ (app có thể đang hiện).
     */
    @Test
    fun `K7 xong ma app o display 0 ngoai hinh da do - coi nhu toan man, khong o den, 0 lenh them`() {
        val topHidden = text("tm2-detached").lines()
            .joinToString("\n") { if ("taskId=2613" in it) it.replace("visible=true", "visible=false") else it }
        assertEquals(Where.ELSEWHERE, SlotReturn.whereIs(StackParse.parse(topHidden), 2613, 173))
        val r = Rig(listOf(text("tm2-in-slot"), topHidden))
        val out = r.seq.detach(173, vm, null, homes)
        assertEquals(2613, out.taskId, out.line)
        assertNull(out.back, out.line)
        assertEquals(listOf(list, SlotReturn.guardedDetachCmd(null, homes, vmComp)) + List(SlotReturnSequence.DETACH_READS) { list }, r.log)
        // Về sau: màn nhà hiện lại (app ẩn đúng hình S) ⇒ bringBack K8 như mọi lượt toàn màn.
        val back = Rig(listOf(text("tm2-detached-home"), text("tm2-returned")))
        assertEquals(Back.IN_SLOT, back.seq.bringBack(173, 2613, homes).result)
        // Rời sang display KHÁC (không phải 0, không phải ô) ⇒ không nhận là toàn màn (giữ hành vi đo lại ô).
        val other = text("tm2-detached").lines().joinToString("\n") { it.replace("displayId=0", "displayId=5") }
        val o2 = Rig(listOf(text("tm2-in-slot"), other)).seq.detach(173, vm, null, homes)
        assertNull(o2.taskId, o2.line)
        assertNull(o2.back, o2.line)
    }

    /**
     * Review lượt 5 [P2] — KHÔNG BAO GIỜ CHE CAMERA: K7 xong rồi camera lên trên trước lần đọc thấy app toàn màn. DẪN XUẤT từ
     * `cam-standin-top` (NGUYÊN VĂN máy ảo 02/10: `com.byd.avc` đứng thay camera ở đỉnh display 0, VietMap ẩn đúng hình S ngay
     * dưới) với task VietMap 2676 → 2613 (task của ô trong `tm2-in-slot`). Bản lượt 4 K8 ngay ở đây: trên ROM mà K8 làm app nổi
     * lên display 0 thì app che camera rồi K12 đưa màn nhà đè camera. Nay: 0 lệnh đổi cửa sổ thêm, coi như toàn màn (thẻ),
     * K8 khi màn nhà hiện lại (camera tắt ⇒ app toàn màn hiện lại như người dùng vừa xin).
     */
    @Test
    fun `K7 xong ma camera len tren - khong K8 duoi camera, khong K12, coi nhu toan man`() {
        val camTop = text("cam-standin-top").replace("taskId=2676:", "taskId=2613:")
        val e = StackParse.parse(camTop)
        assertEquals(Where.HIDDEN_MAIN, SlotReturn.whereIs(e, 2613, 173))
        assertFalse(BehindHomePlan.homeOnTop(e, homes), "camera đứng thay ở đỉnh ⇒ màn nhà không ở đỉnh")
        val r = Rig(listOf(text("tm2-in-slot"), camTop))
        val out = r.seq.detach(173, vm, null, homes)
        assertEquals(2613, out.taskId, out.line)
        assertNull(out.back, out.line)
        assertEquals(listOf(list, SlotReturn.guardedDetachCmd(null, homes, vmComp)) + List(SlotReturnSequence.DETACH_READS) { list }, r.log)
        assertFalse(r.log.any { it.startsWith("am start --display 173") }, "K8 dưới camera: ${r.log}")
        assertFalse(r.log.contains(k12), "K12 khi camera đang hiện: ${r.log}")
    }

    @Test
    fun `app khong o o - 0 lenh doi cua so`() {
        val r = Rig(listOf(text("tm2-detached-home")))
        assertNull(r.seq.detach(173, vm, null, homes).taskId)
        assertEquals(listOf(list), r.log)
    }

    @Test
    fun `ve lai o - K8 dung task, doc lai thay o man ao`() {
        val r = Rig(listOf("tm2-detached-home", "tm2-returned").map(::text))
        val out = r.seq.bringBack(173, 2613, homes)
        assertEquals(Back.IN_SLOT, out.result, out.line)
        assertEquals(listOf(list, k8, list), r.log)
    }

    @Test
    fun `man nha vua hien ma he chua ha co visible - doc lai roi moi K8`() {
        val r = Rig(listOf("tm2-detached", "tm2-detached-home", "tm2-returned").map(::text))
        assertEquals(Back.IN_SLOT, r.seq.bringBack(173, 2613, homes).result)
        assertEquals(listOf(list, list, k8, list), r.log)
    }

    @Test
    fun `nguoi dung van dang dung app toan man - KEEP, 0 lenh`() {
        val r = Rig(listOf(text("tm2-detached")))
        val out = r.seq.bringBack(173, 2613, homes)
        assertEquals(Back.KEEP, out.result, out.line)
        assertFalse(r.log.any { it.startsWith("am start") }, r.log.toString())
        assertEquals(SlotReturnSequence.FRONT_TRIES, r.log.size, "đọc lại có trần")
    }

    @Test
    fun `app da dong khi dang toan man - GONE, 0 lenh, khong tu mo lai`() {
        val r = Rig(listOf(text("tm2-returned")))
        assertEquals(Back.GONE, r.seq.bringBack(173, 9999, homes).result)
        assertEquals(listOf(list), r.log)
    }

    @Test
    fun `doc hong - giu nguyen, 0 lenh`() {
        val r = Rig(listOf(""))
        assertEquals(Back.KEEP, r.seq.bringBack(173, 2613, homes).result)
        assertEquals(listOf(list), r.log)
    }

    @Test
    fun `K8 lai len TRUOC display 0 - K12 roi bao golden`() {
        val r = Rig(listOf("tm2-detached-home", "tm2-detached").map(::text))
        val out = r.seq.bringBack(173, 2613, homes)
        assertEquals(Back.FRONT_RESTORED, out.result, out.line)
        assertEquals(k12, r.log.last(), r.log.toString())
    }

    @Test
    fun `K8 khong an (van an display 0) - golden, khong K12`() {
        val r = Rig(listOf(text("tm2-detached-home")))
        assertEquals(Back.GOLDEN, r.seq.bringBack(173, 2613, homes).result)
        assertFalse(r.log.contains(k12))
        assertEquals(1, r.log.count { it == k8 }, "K8 đúng một lần: ${r.log}")
    }

    /**
     * Review lượt 6 [P2] — KHÔNG BAO GIỜ CHE CAMERA ở đường về ô từ `onStart` / chạm thẻ: bấm HOME rồi camera lùi lên TRƯỚC lần
     * đọc của `bringBack`. DẪN XUẤT từ `cam-standin-top` (NGUYÊN VĂN máy ảo 02/10: camera đứng thay ở đỉnh display 0, màn nhà
     * `visible=false`, VietMap ẩn đúng hình S) với task 2676 → 2613. Bản lượt 5 K8 ngay dưới camera (trên ROM mà K8 làm app nổi
     * lên display 0 thì app che camera, rồi K12 đưa màn nhà đè camera). Nay: KEEP, 0 lệnh; lượt sau (màn nhà hiện lại) K8.
     */
    @Test
    fun `ve lai o ma camera dang o tren - KEEP, khong K8 duoi camera, khong K12`() {
        val camTop = text("cam-standin-top").replace("taskId=2676:", "taskId=2613:")
        assertEquals(Where.HIDDEN_MAIN, SlotReturn.whereIs(StackParse.parse(camTop), 2613, 173))
        val r = Rig(listOf(camTop))
        val out = r.seq.bringBack(173, 2613, homes)
        assertEquals(Back.KEEP, out.result, out.line)
        assertEquals(listOf(list), r.log, "0 lệnh đổi cửa sổ khi camera ở trên")
        // Camera tắt, màn nhà hiện lại (app ẩn ngay dưới màn nhà — NGUYÊN VĂN `tm2-detached-home`) ⇒ lượt sau K8 về ô.
        val later = Rig(listOf("tm2-detached-home", "tm2-returned").map(::text))
        assertEquals(Back.IN_SLOT, later.seq.bringBack(173, 2613, homes).result)
        assertEquals(listOf(list, k8, list), later.log)
    }

    /**
     * Review lượt 6 [P2] — R1.8 cùng cổng: ô dựng lại đúng lúc camera lùi đang ở trên (nổ máy rồi lùi ngay) ⇒ GOLDEN (đường
     * mở ô 2.83 — force-stop + `am start --display <vd>`), 0 K8. Fixture NGUYÊN VĂN `cam-standin-top`: VietMap 2676 ẩn đúng
     * hình S (stack 351 chỉ một task), mang dấu.
     */
    @Test
    fun `R1-8 - o dung lai luc camera dang o tren - golden, 0 K8`() {
        val cam = text("cam-standin-top")
        val marks = mapOf(2676 to vm)
        assertEquals(2676, SlotReturn.markedBehind(StackParse.parse(cam), marks, vm)?.taskId, "có task mang dấu sau màn nhà")
        val r = Rig(listOf(cam))
        val out = r.seq.bringBackMarked(173, vm, marks, homes)
        assertEquals(Back.GOLDEN, out.result, out.line)
        assertEquals(listOf(list), r.log)
    }

    @Test
    fun `R1-8 - app Kachi day ra sau man nha ve o bang K8, khong force-stop`() {
        val r = Rig(listOf("tm6-marked-behind", "tm6-after-k8").map(::text))
        val out = r.seq.bringBackMarked(173, vm, mapOf(2613 to vm), homes)
        assertEquals(Back.IN_SLOT, out.result, out.line)
        assertEquals(2613, out.taskId)
        assertEquals(listOf(list, k8, list), r.log)
        assertFalse(r.log.any { it.contains("force-stop") })
    }

    @Test
    fun `R1-8 - khong dau hoac doc hong thi golden, 0 lenh doi cua so`() {
        val none = Rig(listOf(text("tm6-marked-behind")))
        assertEquals(Back.NOT_BEHIND, none.seq.bringBackMarked(173, "com.waze", mapOf(2613 to vm), homes).result)
        assertEquals(listOf(list), none.log)
        val unread = Rig(listOf(""))
        assertEquals(Back.UNREAD, unread.seq.bringBackMarked(173, vm, mapOf(2613 to vm), homes).result)
        assertEquals(listOf(list), unread.log)
    }
}
