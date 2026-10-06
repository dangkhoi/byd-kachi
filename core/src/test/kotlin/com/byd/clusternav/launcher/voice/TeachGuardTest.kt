package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.TeachGuard.Code
import com.byd.clusternav.launcher.voice.TeachGuard.Level
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.91 VOICE-APP-NAMES · C5 — cổng an toàn, MỖI DÒNG của bảng §4.7 một ca (R7 AC). Gói `com.example.*` là bộ thử;
 * gói của bảng đích lấy từ [VoiceAppTargets] (không viết cứng).
 *
 * Sai lệch spec có ghi: bảng §4.7 vừa nói *"mở đầu bằng động từ ⇒ CHẶN"* vừa lấy *"xem phim"* (mở đầu bằng *"xem"*) làm
 * ví dụ CẢNH BÁO. Luật nghiêm hơn thắng (CLAUDE.md §16) ⇒ *"xem phim"* bị CHẶN; ca CẢNH BÁO gần-lệnh dùng một tên
 * KHÔNG mở đầu bằng động từ.
 */
class TeachGuardTest {

    private val flix = "com.example.flix"
    private val maps = "com.example.maps"
    private val youtube = VoiceAppTargets.byKey(VoiceAppTargets.YOUTUBE)!!.packages.first()
    private val labels = listOf("Netflix" to flix, "Maps" to maps, "YouTube" to youtube)
    private val taught = listOf(TaughtName(flix, TaughtSource.SPEECH, "nep leag", "Netflix"))
    private val ctx = TeachContext(
        keys = VoiceAppIndex.build(labels, labels.map { it.second }.toSet(), taught).keys,
        taught = taught,
        profiles = listOf("Mặc định", "Vợ Yêu"),
        places = listOf("Nhà", "Công ty"),
        wakePhrases = listOf("hey kachi"),
    )

    private fun v(name: String, pkg: String = flix, src: TaughtSource = TaughtSource.SPEECH) = TeachGuard.check(ctx, pkg, name, src)

    private fun blocked(name: String, code: Code, pkg: String = flix) {
        val got = v(name, pkg)
        assertEquals(Level.BLOCK, got.level, "«$name» phải bị CHẶN: $got")
        assertTrue(got.has(code), "«$name» phải có lý do $code: $got")
    }

    private fun warned(name: String, code: Code, pkg: String = flix) {
        val got = v(name, pkg)
        assertEquals(Level.WARN, got.level, "«$name» phải CẢNH BÁO: $got")
        assertTrue(got.has(code), "«$name» phải có lý do $code: $got")
    }

    @Test fun `hinh dang hong bi chan`() {
        blocked("ờ", Code.SHAPE); blocked("ab", Code.SHAPE); blocked("một hai ba bốn năm", Code.SHAPE)
        blocked("x".repeat(TeachSample.MAX_CHARS + 1), Code.SHAPE)
    }

    // ── Quyết định điều phối 2.91 (spec §7 OQ4 · §9 F1): sàn 3 chữ cái; tên GIỌNG 3 chữ cái cần ≥ 2 lượt giống hệt ──

    @Test fun `ten giong ba chu cai mot luot bi chan cho toi khi nghe lai dung chuoi ay`() {
        // [ĐO máy ảo 06/10] TTS "mở Chrome" ⇒ mô hình in «mở cơm» ở 2/3 tốc độ.
        val once = TeachGuard.check(ctx, maps, "cơm", TaughtSource.SPEECH, takes = 1)
        assertEquals(Level.BLOCK, once.level, "$once")
        assertTrue(once.has(Code.SHORT_ONE_TAKE), "$once")
        assertEquals("3", once.reasons.first { it.code == Code.SHORT_ONE_TAKE }.detail)
        // Cổng va chạm vẫn chạy ĐỦ ở lượt đầu: SHORT_ONE_TAKE là lý do DUY NHẤT làm nó chặn ở đây.
        assertTrue(once.reasons.filter { it.code.level == Level.BLOCK }.all { it.code == Code.SHORT_ONE_TAKE }, "$once")
        val twice = TeachGuard.check(ctx, maps, "cơm", TaughtSource.SPEECH, takes = TeachGuard.MIN_TAKES_SHORT)
        assertFalse(twice.has(Code.SHORT_ONE_TAKE), "$twice")
        assertEquals(Level.WARN, twice.level, "lưu được sau 'Vẫn lưu' (một từ đơn): $twice")
        assertTrue(twice.has(Code.ONE_WORD))
    }

    @Test fun `ten ngan van phai qua moi cong va cham du da lap lai`() {
        // Lặp lại đủ lượt KHÔNG mở cổng va chạm: trùng câu lệnh / tiền tố / động từ / hồ sơ vẫn CHẶN.
        // «ghe» = chuỗi THẬT mô hình in cho TTS "mở Gmail" [ĐO máy ảo 06/10, spec §9 F1] — bỏ dấu = «ghế» ⇒ tiền tố lệnh ghế.
        listOf("pin" to Code.COMMAND, "ghe" to Code.COMMAND_PREFIX, "tắt" to Code.RESERVED_WORD, "vợ" to null).forEach { (n, code) ->
            val v = TeachGuard.check(ctx, maps, n, TaughtSource.SPEECH, takes = 3)
            if (code == null) assertEquals(Level.BLOCK, v.level, "«$n» (2 chữ cái) dưới sàn tuyệt đối: $v")
            else { assertEquals(Level.BLOCK, v.level, "«$n»: $v"); assertTrue(v.has(code), "«$n» phải có $code: $v") }
        }
    }

    @Test fun `ten go ba chu cai khong can lap lai va ten da luu khong doi them luot`() {
        val typed = TeachGuard.check(ctx, maps, "cơm", TaughtSource.TYPED, takes = 1)
        assertFalse(typed.has(Code.SHORT_ONE_TAKE), "tên gõ là chủ ý của người dùng — sàn 3, không cần lượt: $typed")
        assertEquals(Level.WARN, typed.level)
        val savedCtx = ctx.copy(taught = ctx.taught + TaughtName(maps, TaughtSource.SPEECH, "cơm", "Maps"))
        val again = TeachGuard.check(savedCtx, maps, "cơm", TaughtSource.SPEECH, takes = 1)
        assertEquals(Level.ALREADY, again.level, "đã lưu ⇒ ĐÃ HIỂU SẴN, không đòi thêm lượt: $again")
        assertFalse(again.has(Code.SHORT_ONE_TAKE))
    }

    @Test fun `ten bon chu cai tro len mot luot du`() {
        val v = TeachGuard.check(ctx, maps, "tóp tóp", TaughtSource.SPEECH, takes = 1)
        assertFalse(v.has(Code.SHORT_ONE_TAKE), "$v")
        assertEquals(Level.NEW, v.level)
    }

    @Test fun `trung nguyen van mot cum lenh bi chan`() { blocked("đèn đọc", Code.COMMAND); blocked("cài đặt", Code.COMMAND) }

    @Test fun `tien to cua mot cum lenh bi chan`() {
        blocked("nhiệt", Code.COMMAND_PREFIX)
        // Một lý do cho mỗi loại — không lặp cùng một câu chục lần trong hộp dạy.
        assertEquals(1, v("nhiệt").reasons.count { it.code == Code.COMMAND_PREFIX })
    }

    @Test fun `mo dau bang dong tu bi chan ke ca xem phim`() { blocked("tắt máy xe", Code.RESERVED_WORD); blocked("xem phim", Code.RESERVED_WORD) }

    @Test fun `tu noi tu dem cum danh dau va cau tra loi bi chan`() {
        blocked("rồi sao nữa", Code.RESERVED_WORD)
        blocked("bằng lăng tím", Code.RESERVED_WORD)
        blocked("thôi", Code.RESERVED_WORD)
        blocked("đồng ý", Code.RESERVED_WORD)
    }

    @Test fun `menh de o va tu so dung dau bi chan`() { blocked("phim ô số hai", Code.SLOT); blocked("hai phim hay", Code.SLOT) }

    @Test fun `ten ho so va noi trong so dia chi bi chan`() { blocked("vợ yêu", Code.PROFILE); blocked("công ty", Code.PLACE) }

    @Test fun `chua cau goi Kachi bi chan`() = blocked("hey kachi phim", Code.WAKE)

    @Test fun `nhan ten da day va cach noi bang dich cua app khac bi chan`() {
        blocked("maps", Code.OTHER_APP)                    // nhãn thật của app khác
        blocked("nep leag", Code.OTHER_APP, pkg = maps)    // tên đã dạy của app khác
        blocked("du túp", Code.OTHER_APP)                  // cách nói bảng đích của YouTube
    }

    @Test fun `mo ten ra mot lenh khong phai mo app bi chan`() = blocked("nhạc trữ tình", Code.IS_COMMAND)

    @Test fun `da hieu san va trung mau truoc khong luu`() {
        val known = v("du túp", pkg = youtube)
        assertEquals(Level.ALREADY, known.level, "$known")
        assertTrue(known.has(Code.ALREADY_KNOWN))
        val dup = v("nép lêag")
        assertTrue(dup.has(Code.DUPLICATE), "$dup")
    }

    @Test fun `da hieu san thang canh bao mot tu - ca do may ao gmail`() {
        // [ĐO máy ảo 06/10] TTS "mở gờ meo" ⇒ mô hình in «mở gmail»; Kachi đã mở Gmail bằng nhãn ⇒ KHÔNG lưu (bản đầu: CẢNH BÁO ⇒ lưu).
        val gmail = "com.example.gmail"
        val labels2 = labels + ("Gmail" to gmail)
        val ctx2 = ctx.copy(keys = VoiceAppIndex.build(labels2, labels2.map { it.second }.toSet(), taught).keys)
        val got = TeachGuard.check(ctx2, gmail, "gmail", TaughtSource.SPEECH)
        assertEquals(Level.ALREADY, got.level, "$got")
        assertTrue(got.has(Code.ALREADY_KNOWN) && got.has(Code.ONE_WORD))
    }

    @Test fun `mo ten ra app khac qua khop mo canh bao`() {
        val got = v("netflik", pkg = maps)
        assertEquals(Level.WARN, got.level, "$got")
        assertTrue(got.has(Code.STEALS_FUZZY) && got.has(Code.NEAR_OTHER_APP), "$got")
    }

    @Test fun `ten chua mot tu cua lenh canh bao`() = warned("pin hoa hồng", Code.MISMATCH_WORD)

    @Test fun `ten lam rung dong hotword tinh canh bao khong bias`() {
        val static = SherpaBiasing.hotwordsFile(ctx.places, ctx.profiles).lines()
            .first { it.startsWith("MỞ ") && it.split(' ').size >= 3 && TeachGuard.check(ctx, flix, it.removePrefix("MỞ ").lowercase() + " đẹp", TaughtSource.SPEECH).level != Level.BLOCK }
        val name = static.removePrefix("MỞ ").lowercase() + " đẹp"
        val got = v(name)
        assertTrue(got.has(Code.NO_BIAS), "$got")
        assertTrue(got.reasons.first { it.code == Code.NO_BIAS }.detail.contains(static))
        assertFalse(TeachGuard.check(ctx, flix, name, TaughtSource.TYPED).has(Code.NO_BIAS), "tên gõ không vào hotword")
    }

    @Test fun `gan ten da day cua app khac canh bao`() = warned("nep leog", Code.NEAR_OTHER_APP, pkg = maps)

    /**
     * Soát 2.93 Pass 4 (P3): phán quyết dựng tệp tĩnh THẲNG ([SherpaBiasing.build]) — không qua ô nhớ MỘT chỗ của
     * [SherpaBiasing.hotwordsFile] ⇒ 🎤 kế ở hộp dạy (cùng bộ đầu vào phiên lệnh) trúng nhớ, không dựng lại. Thử ĐỎ: đưa lời
     * gọi ở `probe` về `hotwordsFile(ctx.places, ctx.profiles)`. Ô nhớ là trạng thái TOÀN CỤC ⇒ cùng giả định chạy tuần tự
     * như [SherpaBiasingMemoTest] (bật chạy song song của Jupiter thì lớp này cũng cần `@Isolated`).
     */
    @Test fun `phan quyet khong day tep cua phien lenh ra khoi o nho`() {
        val session = SherpaBiasing.hotwordsFile(ctx.places, ctx.profiles, taught, listOf("Ghi âm"))
        val got = v("nep leog", pkg = maps)
        assertTrue(got.level != Level.BLOCK, "phải qua tầng A thì bước hotword mới chạy: $got")
        assertSame(session, SherpaBiasing.hotwordsFile(ctx.places, ctx.profiles, taught, listOf("Ghi âm")))
    }

    @Test fun `mot tu duy nhat canh bao`() = warned("phimhay", Code.ONE_WORD)

    // ── 2.93 VOICE-TEACH-SHORT-HOMOGRAPH — quyết định điều phối: CẢNH BÁO, KHÔNG chặn ──────────────────────────────────

    /**
     * [ĐO off-car 06/10, spec voice-app-names §9 F6] dạy «nay» cho Drive ⇒ *"mở cái này"* (trước: không hiểu) mở Drive —
     * *"này"* là chữ của cụm hỏi *"thế này"* (`VoiceLexicon.READ_TAILS`), bỏ dấu cùng `nay`. Tên vẫn LƯU được (sau *Vẫn
     * lưu*); lý do nói đúng chữ va chạm thay vì chỉ "một từ đơn".
     */
    @Test fun `ten mot am tiet trung chu cua cau lenh canh bao dong hinh, khong chan`() {
        val got = TeachGuard.check(ctx, maps, "nay", TaughtSource.SPEECH, takes = TeachGuard.MIN_TAKES_SHORT)
        assertEquals(Level.WARN, got.level, "quyết định 2.93: CẢNH BÁO, không CHẶN: $got")
        assertTrue(got.has(Code.HOMOGRAPH) && got.has(Code.ONE_WORD), "$got")
        assertTrue(got.reasons.first { it.code == Code.HOMOGRAPH }.detail.isNotBlank())
        // Một lượt (tên ngắn) vẫn CHẶN vì SHORT_ONE_TAKE — cảnh báo đồng hình không mở hay đóng cổng nào khác.
        assertEquals(Level.BLOCK, TeachGuard.check(ctx, maps, "nay", TaughtSource.SPEECH, takes = 1).level)
    }

    /**
     * Dữ liệu sẵn có, không bảng thứ hai: MỌI chữ của câu mẫu tĩnh mà tự nó không bị chặn (không trùng lệnh/tiền tố/động
     * từ/từ đệm…) đều phải ra CẢNH BÁO đồng hình — và lý do chỉ ra một câu mẫu CÓ chữ ấy (có dấu, người dùng nhận ra).
     */
    @Test fun `moi chu cua cau mau tinh khong bi chan deu canh bao dong hinh kem cau mau`() {
        val examples = VoiceCommandCatalog.groups(lang = com.byd.clusternav.launcher.Lang.VI).flatMap { g -> g.examples.map { it.phrase } }
        val words = examples.flatMap { VoiceLexicon.tokenize(it) }.map { it.raw.lowercase() }.distinct()
            .filter { TeachSample.shape(it) is TeachSample.Sample }
        var checked = 0
        words.forEach { w ->
            val got = TeachGuard.check(ctx, maps, w, TaughtSource.TYPED)
            if (got.level == Level.BLOCK || got.level == Level.ALREADY) return@forEach
            checked++
            val r = got.reasons.firstOrNull { it.code == Code.HOMOGRAPH }
            assertTrue(r != null, "«$w» là chữ của câu mẫu mà không cảnh báo đồng hình: $got")
            assertTrue(VoiceLexicon.tokenize(r!!.detail).any { it.norm == VoiceLexicon.deaccent(w) }, "lý do phải chỉ câu có «$w»: ${r.detail}")
        }
        assertTrue(checked > 0, "bài phải soát được ít nhất một chữ không bị chặn (soát ${words.size})")
    }

    @Test fun `ten nhieu tu hoac chu ngoai cau lenh khong canh bao dong hinh`() {
        assertFalse(v("phimhay").has(Code.HOMOGRAPH), "chữ không có trong câu lệnh nào")
        assertFalse(v("tóp tóp", pkg = maps).has(Code.HOMOGRAPH), "tên nhiều từ chỉ khớp NGUYÊN dãy — không đồng hình một chữ")
    }

    @Test fun `ten moi sach la MOI`() {
        val got = v("tóp tóp", pkg = maps)
        assertEquals(Level.NEW, got.level, "$got")
        assertTrue(got.reasons.isEmpty())
    }

    @Test fun `may do hoi quy chan ten cuop cau doi ho so`() {
        // A không chặn "hồ sơ vợ yêu" (không trùng nguyên văn tên hồ sơ), nhưng có nó thì câu mẫu "chuyển sang hồ sơ Vợ
        // Yêu" thành mở app ⇒ E phải nêu đúng câu bị cướp.
        val cand = TaughtName(maps, TaughtSource.SPEECH, "hồ sơ vợ yêu", "Maps")
        val r = TeachGuard.regression(ctx, cand)
        assertFalse(r.ok)
        assertTrue(r.changed.any { it.contains("Vợ Yêu") }, "${r.changed}")
        assertTrue(r.checked > 50, "phải soát cả danh mục, soát ${r.checked}")
    }

    @Test fun `may do hoi quy cho qua ten sach va kiem ten goi duoc`() {
        val ok = TeachGuard.regression(ctx, TaughtName(maps, TaughtSource.SPEECH, "tóp tóp", "Maps"))
        assertTrue(ok.ok, "${ok.changed}")
        val shadowed = TeachGuard.regression(ctx, TaughtName(flix, TaughtSource.SPEECH, "maps", "Netflix"))
        assertFalse(shadowed.reachable, "tên bị nhãn app khác che ⇒ không gọi được ⇒ không cho lưu")
    }
}
