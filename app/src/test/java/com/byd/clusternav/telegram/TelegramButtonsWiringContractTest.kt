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
}
