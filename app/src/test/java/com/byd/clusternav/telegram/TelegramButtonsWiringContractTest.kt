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
    fun `lenh xe tu Telegram di dung cong cua VoiceControlDispatch, khong cong rieng (3-1-20)`() {
        // Owner 26/10 trên xe: cổng số P riêng của Telegram chặn kính/cốp khi xe đang đậu. Gỡ hẳn; cổng tốc độ duy nhất
        // là CtlSafetyPolicy.blocked trong VoiceControlDispatch.run (chỉ cốp cần 0 km/h).
        val svc = code("TelegramBridgeService.kt")
        assertTrue(svc.contains("VoiceControlDispatch(") && svc.contains("dispatch.run(VoiceIntent.Control("), "thi hành qua VoiceControlDispatch")
        listOf("blockedExternal", "readMotionFull", "gear P").forEach { assertTrue(!svc.contains(it), "không còn cổng riêng: $it") }
        val vcd = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/VoiceControlDispatch.kt")
        assertTrue(vcd.contains("CtlSafetyPolicy.blocked(def.id, arg"), "cổng cốp 0 km/h nằm ở VoiceControlDispatch")
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

    @Test
    fun `cau tra loi cua xe la cau THAT, cho co tran (review 26-10 P2-2)`() {
        // VoiceReadback nói BẤT ĐỒNG BỘ (đọc lại sau ~300 ms). Đọc một StringBuilder ngay sau run() luôn rỗng ⇒ câu chung.
        val run = code("TelegramBridgeService.kt").substringAfter("internal fun runControl(").substringBefore("\n    }\n")
        assertTrue(run.contains("said.await(CTL_REPLY_WAIT_MS"), "runControl phải CHỜ câu trả lời (có trần)")
        assertTrue(!run.contains("StringBuilder"), "không đọc bộ đệm chữ ngay sau run()")
        assertTrue(run.contains("no confirmation from the car yet"), "quá trần ⇒ nói thật, không bịa ✓")
        // Nút không được vứt câu của xe để thay bằng câu cố định (dạng `runControl(...).let { "…" }`).
        val disp = code("TelegramMenuDispatch.kt")
        assertTrue(!Regex("""runControl\([^\n]*\)\)\.let \{ """").containsMatchIn(disp), "dispatch* phải trả câu của runControl")
    }

    @Test
    fun `pha ve doc moi control mot lan (review 26-10 P2-6)`() {
        val svc = code("TelegramBridgeService.kt")
        assertTrue(svc.contains("withReadCache { renderBoard(app, cb, toast) }"), "vẽ bảng sau nút: gom đọc")
        assertTrue(svc.contains("withReadCache { renderBoard(app, TelegramCallback(n, domain, \"open\"), \"\", n) }"), "gửi bảng mới: gom đọc")
        val disp = code("TelegramMenuDispatch.kt")
        val read = disp.substringAfter("fun TelegramBridgeService.readCtl(").substringBefore("\n}\n")
        assertTrue(read.contains("cache.containsKey(id)") && read.contains("cache?.put(id, v)"), "readCtl dùng bộ nhớ của lượt vẽ")
        // Pha GHI không được bọc cache (−/+ phải đọc tươi trước khi đặt).
        val handle = svc.substringAfter("private fun handleMenuCallback(").substringBefore("\n    }\n")
        assertTrue(!handle.contains("withReadCache { dispatchMenu"), "dispatchMenu (ghi xe) không được dùng số đọc cũ")
    }

    @Test
    fun `statusRow chi nhan MA MAU, khong nhan ten chu (crash xe 3-1-18)`() {
        // SettingsRows.statusRow → Color.parseColor(color). Truyền "MUT"/"GREEN" chữ trần ⇒ IllegalArgumentException
        // "Unknown color" ⇒ trang Cài đặt › Dẫn đường sập MỖI lần mở (lỗi xe 26/10). Quét MỌI tệp trong launcher/.
        val dir = "src/main/java/com/byd/clusternav/launcher"
        val bad = Regex("""statusRow\(\s*(?:if\s*\(.+?\)\s*)?"(?!#)""")
        val files = com.byd.clusternav.testsupport.SourceRoots.path(dir).toFile().listFiles { f -> f.name.endsWith(".kt") }!!
        assertTrue(files.size > 50, "phải quét được thư mục launcher (tìm thấy ${files.size} tệp)")
        val hits = files.filter { bad.containsMatchIn(com.byd.clusternav.testsupport.SourceRoots.codeOf("$dir/${it.name}")) }.map { it.name }
        assertTrue(hits.isEmpty(), "statusRow được truyền chuỗi không phải mã màu ở: $hits")
    }

    @Test
    fun `play ten bai mo thang video bai dau (chuyen bai ca khi dang phat - owner 26-10)`() {
        // MEDIA_PLAY_FROM_SEARCH bị app ĐANG PHÁT bỏ qua ⇒ "báo đã play mà vẫn hát bài cũ". Đi đường watch như giọng nói.
        val svc = code("TelegramBridgeService.kt")
        val body = svc.substringAfter("private fun playQuery(").substringBefore("private fun playPendingMusicIfInteractive")
        assertTrue(body.contains("VoiceAppIntents.watchHandoff(") && body.contains("VoiceYoutubeResolver.firstVideoIdBounded("),
            "playQuery phải giải video_id rồi mở URL watch")
        assertTrue(body.indexOf("watchHandoff(") < body.indexOf("Intent(launch.action)"), "watch TRƯỚC, search chỉ là đường lùi")
    }

    @Test
    fun `ban phim nut duoi o go - gui mot lan, nut mo bang, loi nhac thanh lenh`() {
        val svc = code("TelegramBridgeService.kt")
        val handle = svc.substringAfter("private fun handleUpdate(").substringBefore("private fun backoffMs(")
        assertTrue(handle.contains("ensureReplyKeyboard(app, api,"), "mọi update hợp lệ đảm bảo bàn phím đã gửi")
        assertTrue(handle.indexOf("handlePromptButton(") in 0 until handle.indexOf("handleMenuCallback("), "🔎 Song / ➕ New trước menu")
        assertTrue(svc.contains("is TelegramCommand.Board -> sendBoard(app, api, chatId, c.domain)"), "nút bàn phím mở bảng")
        val api = code("TelegramApi.kt")
        assertTrue(api.contains("\"reply_to_message\"") && api.contains("parseText(text, isControlId, replyTo)"),
            "tin trả lời lời nhắc phải mang chữ của lời nhắc vào bộ phân tích")
        val kb = code("TelegramPromptKeyboard.kt")
        assertTrue(kb.contains("TelegramReplyKeyboard.VERSION") && kb.contains("if (ok) Prefs.setTelegramKbVersion"),
            "chỉ ghi phiên bản bàn phím khi gửi được")
    }
}
