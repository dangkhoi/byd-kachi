package com.byd.clusternav.telegram

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá các BẤT BIẾN của menu nút Telegram (spec `kachi-telegram-buttons.html`). Quét nguồn như
 * [com.byd.clusternav.automation.PendingNavWiringContractTest] — bắt được off-car những luật dễ trôi khi ai đó
 * sửa đường thi hành. Hành vi JSON/parse đã có test thuần ở `:core` (`TelegramButtonsTest`).
 */
class TelegramButtonsWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/telegram/$rel")

    @Test
    fun `nonce gate - nut bang cu KHONG thi hanh`() {
        val svc = code("TelegramBridgeService.kt")
        // QD-16: callback nonce ≠ bảng sống ⇒ toast "bảng cũ", return TRƯỚC khi dispatch.
        assertTrue(svc.contains("cb.nonce != boardNonce(app)"), "phải so nonce của callback với bảng sống (bền)")
        val gate = svc.substringAfter("cb.nonce != boardNonce(app)")
        assertTrue(gate.take(220).contains("return"), "nonce sai ⇒ return (không thi hành)")
    }

    @Test
    fun `answerCallback truoc khi cho xe (R-nf4)`() {
        val svc = code("TelegramBridgeService.kt")
        // R-nf4/QD-18: answerCallback (đóng vòng xoay <1s) phải xuất hiện TRƯỚC dispatchMenu (chờ xe).
        val iAnswer = svc.indexOf("api.answerCallback(it)")
        val iDispatch = svc.indexOf("dispatchMenu(app, cb)")
        assertTrue(iAnswer in 1 until iDispatch, "answerCallback phải gọi TRƯỚC dispatchMenu (R-nf4)")
    }

    @Test
    fun `gate cong tac dieu khien xe (P0-1)`() {
        val svc = code("TelegramBridgeService.kt")
        assertTrue(svc.contains("cb.domain in CAR_CONTROL_DOMAINS && !cfg.controlEnabled"),
            "nút ac/seat/misc phải gate theo công tắc controlEnabled (P0-1)")
    }

    @Test
    fun `mot bang song - go nut bang cu + nho message id (QD-17)`() {
        val svc = code("TelegramBridgeService.kt")
        assertTrue(svc.contains("clearMarkup("), "QD-17: gỡ nút bảng cũ khi mở bảng mới")
        assertTrue(svc.contains("setTelegramBoardMsg("), "QD-17: nhớ message_id bảng sống")
    }

    @Test
    fun `nonce ben qua lan tat may (P1-2)`() {
        val svc = code("TelegramBridgeService.kt")
        assertTrue(svc.contains("Prefs.telegramBoardNonce") && svc.contains("bumpTelegramBoardNonce"),
            "nonce đọc/tăng từ prefs (bền), không field RAM")
    }

    @Test
    fun `seat off tat ca mat lan suoi (P1-4)`() {
        val disp = code("TelegramMenuDispatch.kt")
        val off = disp.substringAfter("cb.id == \"off\"").take(260)
        assertTrue(off.contains("seatc\$suffix") && off.contains("seath\$suffix"),
            "Tắt ghế phải tắt CẢ mát (seatc) lẫn sưởi (seath)")
    }

    @Test
    fun `lenh xe tu menu di cong toc do NGOAI`() {
        // dispatch* gọi runControl → runControl dùng blockedExternal (P0-2). Không mở đường ghi thứ hai.
        val svc = code("TelegramBridgeService.kt")
        assertTrue(svc.contains("blockedExternal("), "runControl phải qua cổng tốc độ NGOÀI")
        val disp = code("TelegramMenuDispatch.kt")
        assertTrue(disp.contains("runControl(app"), "dispatch* thi hành control qua runControl (đường có sẵn)")
    }

    @Test
    fun `transport nhac qua MediaBridge (khong duong thu hai)`() {
        val disp = code("TelegramMenuDispatch.kt")
        assertTrue(disp.contains("MediaBridge(app)"), "transport dùng MediaBridge có sẵn")
        listOf(".prev()", ".next()", ".pause()", ".play()").forEach {
            assertTrue(disp.contains(it), "transport phải gọi $it")
        }
    }

    @Test
    fun `nav tu menu dat pending voi GPS bat buoc`() {
        val disp = code("TelegramMenuDispatch.kt")
        assertTrue(disp.contains("PendingNavStore.write(app, dest, requireGps = true)"),
            "dẫn đường từ nút cũng GPS bắt buộc (owner 26/10)")
    }

    @Test
    fun `step temp la lenh tuyet doi (doc tuoi roi set)`() {
        // QD-08: −/+ đọc giá trị hiện tại rồi gửi MỘT lệnh tuyệt đối, không gửi lệnh tương đối.
        val disp = code("TelegramMenuDispatch.kt")
        assertTrue(disp.contains("readCtl(app, \"temp\")") && disp.contains("coerceIn(17, 33)"),
            "stepTemp đọc tươi + kẹp dải rồi set tuyệt đối")
    }

    @Test
    fun `setMyCommands dang ky 9 lenh tieng Anh`() {
        val svc = code("TelegramBridgeService.kt")
        assertTrue(svc.contains("setMyCommands(MENU_COMMANDS)"), "đăng ký gợi ý lệnh khi vòng bắt đầu")
        listOf("\"menu\"", "\"home\"", "\"office\"", "\"go\"", "\"play\"", "\"ac\"", "\"cancel\"", "\"status\"", "\"places\"").forEach {
            assertTrue(svc.contains(it), "MENU_COMMANDS phải có lệnh $it")
        }
    }

    @Test
    fun `cong toc do NGOAI dung chieu - chan thi RETURN tu choi (review 26-10 M2)`() {
        // Lượt review 26/10 đảo `if (blockedExternal)` thành `if (!blockedExternal)` mà 15 test vẫn xanh. Khoá HÌNH DẠNG:
        // đúng một dòng gọi, không phủ định, và nhánh đúng là trả câu từ chối (không thi hành).
        val svc = code("TelegramBridgeService.kt")
        assertTrue(svc.contains("if (CtlSafetyPolicy.blockedExternal(c.controlId, arg, motion)) {"), "gọi cổng đúng chiều")
        assertTrue(!svc.contains("!CtlSafetyPolicy.blockedExternal("), "không được phủ định cổng an toàn")
        val branch = svc.substringAfter("if (CtlSafetyPolicy.blockedExternal(c.controlId, arg, motion)) {").take(160)
        assertTrue(branch.trimStart().startsWith("return \""), "bị chặn ⇒ return câu từ chối NGAY")
        assertTrue(svc.contains("CtlSafetyPolicy.readMotionFull("), "cổng ngoài cần cả số (P) lẫn tốc độ")
    }

    @Test
    fun `cong tin cu dung truoc moi thi hanh (review 26-10 P0-1)`() {
        val svc = code("TelegramBridgeService.kt")
        val body = svc.substringAfter("private fun handleUpdate(")
        val iGate = body.indexOf("TelegramFreshness.allow(")
        val iMenu = body.indexOf("handleMenuCallback(")
        val iExec = body.indexOf("execute(app, api, cfg, u)")
        assertTrue(iGate in 1 until iMenu && iGate < iExec, "cổng tin cũ phải chạy TRƯỚC menu callback và execute")
        assertTrue(svc.contains("runCatching { handleUpdate(app, api, fresh, u, fromBacklog, nowSec) }"),
            "vòng polling đưa cờ tồn đọng xuống, và một update lỗi không giết vòng")
    }

    @Test
    fun `loi polling lui cho, khong hoi lai ngay (review 26-10 P1-2 P1-3)`() {
        val svc = code("TelegramBridgeService.kt")
        val loop = svc.substringAfter("private fun loop(").substringBefore("private fun handleUpdate(")
        assertTrue(loop.contains("if (updates == null)") && loop.contains("sleep(backoffMs(failures))"),
            "getUpdates lỗi ⇒ lùi chờ luỹ tiến")
        assertTrue(loop.contains("runCatching {\n                    api.getUpdates("), "getUpdates ném KHÔNG thoát vòng")
        val api = code("TelegramApi.kt")
        assertTrue(api.contains("List<TelegramUpdate>? {"), "getUpdates phân biệt lỗi (null) với không có tin (rỗng)")
    }

    @Test
    fun `toast buoc nhiet quat noi that (review 26-10 P2-5)`() {
        val disp = code("TelegramMenuDispatch.kt")
        assertTrue(!disp.contains("\"❄️ temp adjusted\"") && !disp.contains("\"💨 fan adjusted\""), "không báo 'adjusted' khống")
        assertTrue(disp.contains("lastTempStepMs") && disp.contains("lastFanStepMs"), "debounce nhiệt và quạt mốc riêng")
    }
}
