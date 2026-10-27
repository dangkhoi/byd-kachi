package com.byd.clusternav.telegram

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Dây nối Telegram 3.1.21 — owner 26/10: *"không đọc ghi gì vào xe hết, an toàn data privacy, chỉ làm basic"*. Bài đầu
 * là bài canh PHẠM VI: mọi tệp trong `telegram/` + cầu Cài đặt của nó không được chạm đường xe nào.
 */
class TelegramButtonsWiringContractTest {

    private val dir = "src/main/java/com/byd/clusternav/telegram"
    private fun code(rel: String) = SourceRoots.codeOf("$dir/$rel")

    @Test
    fun `telegram khong doc khong ghi xe (pham vi owner 26-10)`() {
        val coreDir = "../../../../../../../../core/src/main/kotlin/com/byd/clusternav/launcher/telegram"
        val coreFiles = SourceRoots.path("$dir/$coreDir").toFile().listFiles { f -> f.name.endsWith(".kt") }!!.map { "$coreDir/${it.name}" }
        assertTrue(coreFiles.size >= 6, "phải quét được cả nửa :core (thấy $coreFiles)")
        val files = SourceRoots.path(dir).toFile().listFiles { f -> f.name.endsWith(".kt") }!!.map { it.name } +
            listOf("../launcher/ClusterNavBridgeTelegram.kt", "../launcher/SettingsTelegramRows.kt") + coreFiles
        assertTrue(files.size >= 11, "phải quét được thư mục telegram/ (thấy $files)")
        val banned = listOf("AppContainer", "carControl", "readFresh", "readState", "VoiceControlDispatch", "CarStatus",
            "BydHal", "HalBinding", "ControlRegistry", "CtlSafetyPolicy", "MediaBridge", "carStatusRepository")
        files.forEach { f ->
            val src = code(f)
            banned.forEach { b -> assertTrue(!src.contains(b), "$f dùng '$b' — Telegram không được đọc/ghi xe") }
        }
    }

    @Test
    fun `play ten bai mo thang video bai dau, link nhac vao dung app`() {
        val svc = code("TelegramBridgeService.kt")
        val q = svc.substringAfter("private fun playQuery(").substringBefore("private fun playPendingMusicIfInteractive")
        assertTrue(q.contains("VoiceAppIntents.watchHandoff(") && q.contains("VoiceYoutubeResolver.firstVideoIdBounded("),
            "tên bài: giải video_id rồi mở URL watch (chuyển bài cả khi đang phát)")
        assertTrue(q.indexOf("watchHandoff(") < q.indexOf("Intent(launch.action)"), "watch TRƯỚC, search chỉ là đường lùi")
        assertTrue(q.contains("listOfNotNull(appKey, Prefs.voiceMusicDefaultApp(app)"), "app gõ kèm thắng app mặc định")
        val link = svc.substringAfter("private fun openMusicLink(").substringBefore("private fun playQuery(")
        assertTrue(link.contains("MusicLink.appOf(url)") && link.contains("setPackage(pkg)"), "link nhạc mở thẳng app theo host")
    }

    @Test
    fun `vietmap can toa do - tra bang duong cua giong noi, hong thi bo app va noi ra`() {
        val nav = code("TelegramBridgeService.kt").substringAfter("internal fun setDestination(").substringBefore("/** Tên trong sổ")
        assertTrue(nav.contains("needsCoords") && nav.contains("VoiceGeocoder.resolveBounded("), "app cần toạ độ ⇒ tra toạ độ")
        assertTrue(nav.contains("if (usable.isEmpty()) return"), "không app nào dẫn được ⇒ KHÔNG đặt điểm đến (hết lỗi 'Set…' mà không dẫn)")
        assertTrue(nav.indexOf("if (usable.isEmpty()) return") < nav.indexOf("PendingNavStore.write("), "kiểm trước khi ghi")
    }

    @Test
    fun `ban phim nut gui mot lan, loi nhac va link Google Maps duoc noi`() {
        val svc = code("TelegramBridgeService.kt")
        assertTrue(svc.substringAfter("private fun handleUpdate(").contains("ensureReplyKeyboard(app, api, chatId)"))
        assertTrue(svc.contains("TelegramReplyKeyboard.SONG_PROMPT") && svc.contains("TelegramReplyKeyboard.DEST_PROMPT"))
        assertTrue(svc.contains("MapsShareLink.parse(resolveMapsLink("), "link chia sẻ Google Maps được mở rồi đọc")
        val api = code("TelegramApi.kt")
        assertTrue(api.contains("\"reply_to_message\"") && api.contains("parseText(text, replyTo)"))
        val kb = code("TelegramPromptKeyboard.kt")
        assertTrue(kb.contains("if (ok) Prefs.setTelegramKbVersion"), "chỉ ghi phiên bản bàn phím khi gửi được")
        val prefs = SourceRoots.codeOf("src/main/java/com/byd/clusternav/PrefsTelegram.kt")
        assertTrue(prefs.contains("nowMs - at in 0..PENDING_MUSIC_TTL_MS"), "nhạc chờ hết hạn 24 h (review 26/10 P1)")
        val bridge = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeTelegram.kt")
        assertTrue(bridge.contains("if (usable.isEmpty()) return false"), "Cài đặt không đặt điểm đến mà VietMap không mở được")
        assertTrue(kb.contains("instanceFollowRedirects = false") && kb.contains("getHeaderField(\"Location\")"),
            "link rút gọn: chỉ đọc header chuyển hướng, không tải trang")
    }

    @Test
    fun `statusRow chi nhan MA MAU, khong nhan ten chu (crash xe 3-1-18)`() {
        val d = "src/main/java/com/byd/clusternav/launcher"
        val bad = Regex("""statusRow\(\s*(?:if\s*\(.+?\)\s*)?"(?!#)""")
        val files = SourceRoots.path(d).toFile().listFiles { f -> f.name.endsWith(".kt") }!!
        assertTrue(files.size > 50, "phải quét được thư mục launcher (tìm thấy ${files.size} tệp)")
        val hits = files.filter { bad.containsMatchIn(SourceRoots.codeOf("$d/${it.name}")) }.map { it.name }
        assertTrue(hits.isEmpty(), "statusRow được truyền chuỗi không phải mã màu ở: $hits")
    }

    /** Owner 27/10: bàn phím nút mất sau lần bấm đầu (tin ForceReply thay nó) ⇒ mọi câu trả lời chữ mang lại nó. */
    @Test
    fun `cau tra loi chu tron mang lai ban phim nut`() {
        val h = SourceRoots.body(code("TelegramBridgeService.kt"), "private fun handleUpdate(")
        assertTrue(h.contains("val say = { text: String -> api.sendMessage(chatId, text, TelegramReplyKeyboard.markup()) }"))
        listOf("say(navigate(app, c))", "say(playMusic(app, c))", "say(TelegramInlineKeyboard.HELP)").forEach {
            assertTrue(h.contains(it), "thiếu: $it")
        }
    }

    /** Owner 27/10: Go ⇒ nơi đã lưu ⇒ CHỌN app (gmaps / vietmap / cả hai) + ghim nơi thường dùng. */
    @Test
    fun `bam noi da luu thi hien bang chon app, chon app moi dat diem den`() {
        val b = SourceRoots.body(code("TelegramBridgeService.kt"), "private fun handlePlacesButton(")
        val go = b.substringAfter("\"go\" ->").substringBefore("\"pin\" ->")
        assertTrue(go.contains("picker(") && !go.contains("setDestination("), "bấm nơi KHÔNG dẫn ngay — hiện bảng chọn app")
        val pick = b.substringAfter("\"app\" ->")
        assertTrue(pick.contains("TelegramMenu.appChoice(cb.id)") && pick.contains("setDestination(app, place, choice.second)"))
        assertTrue(b.substringAfter("\"pin\" ->").substringBefore("\"app\" ->").contains("TelegramMenu.togglePin("))
    }
}
