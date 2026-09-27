package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LayoutPreset
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.all
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.apps
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.expect
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.one
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.profiles
import com.byd.clusternav.launcher.voice.VoiceIntentParserHarness.unknown

/**
 * ═══ V1 · BÀI CANH BỘ PHÂN TÍCH Ý ĐỊNH ════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R7. Nguồn câu mẫu: **44 mẫu câu tiếng Việt [ĐO] nguyên văn** trong
 * `docs/diagnostics/kiki-car-RE-2026-09-14.md` §7(c) — chúng là chứng cứ *"người Việt thật sự nói thế nào với trợ
 * lý trên xe"*, đã qua kiểm chứng thị trường; ta chuyển thể sang tập đóng của Kachi.
 *
 * ## Vì sao nhiều ca kỳ vọng [VoiceIntent.Unknown] — và đó là ĐÚNG
 * Radio, truyền hình, tin tức, hỏi đáp, toán: Kachi **cố ý không làm** (phương án C, RE §8.2 — Kiki giữ phần từ
 * vựng mở, Kachi giữ phần xe + launcher chạy offline). Khoá chúng lại bằng test để lần sau không ai "tiện tay"
 * thêm một nhánh đoán mò: một câu trả lời sai còn tệ hơn một câu *"tôi không làm được việc này"*.
 */
class VoiceIntentParserTest {

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    // ══ A · DẪN ĐƯỜNG (Kiki §7c #1–#6) — điểm đến là từ vựng MỞ, giữ nguyên văn ════════════════════════

    @Test fun `dan duong giu nguyen van diem den`() = expect(
        "Dẫn đường đến chợ Bến Thành" to VoiceIntent.Nav("chợ Bến Thành"),
        "Chỉ đường đến số 72 Nguyễn Cơ Thạch" to VoiceIntent.Nav("số 72 Nguyễn Cơ Thạch"),
        "Chỉ đường đến trạm xăng gần nhất" to VoiceIntent.Nav("trạm xăng gần nhất"),
        "Chỉ đường đến Hà Nội" to VoiceIntent.Nav("Hà Nội"),
        "Dẫn đường tới sân bay Nội Bài" to VoiceIntent.Nav("sân bay Nội Bài"),
        "Navigate to Ben Thanh market" to VoiceIntent.Nav("Ben Thanh market"),
    )

    /**
     * *"trạm sạc"* chứa cụm *"sạc"* của xe ở nhiều biến thể — nếu đem điểm đến so với từ vựng xe thì một
     * câu dẫn đường sẽ biến thành lệnh sạc pin. Ca này khoá luật *"sau động từ NAV thì KHÔNG khớp từ vựng xe"*.
     */
    @Test fun `diem den KHONG bi khop nham vao tu vung xe`() = expect(
        "Chỉ đường đến trạm sạc gần nhất" to VoiceIntent.Nav("trạm sạc gần nhất"),
        "Dẫn đường đến chợ Gió" to VoiceIntent.Nav("chợ Gió"),
    )

    // ══ B · NHẠC (Kiki §7c #7–#13) — tên bài/ca sĩ/thể loại = từ vựng MỞ ══════════════════════════════

    @Test fun `mo bai mo nhac ra Media QUERY`() = expect(
        "Mở bài Nồng nàn Hà Nội" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Nồng nàn Hà Nội"),
        "Mở nhạc Trữ tình" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Trữ tình"),
        "Mở nhạc Bolero" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Bolero"),
        "Mở nhạc trẻ remix" to VoiceIntent.Media(VoiceMediaOp.QUERY, "trẻ remix"),
        "Phát bài Diễm xưa" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Diễm xưa"),
    )

    @Test fun `lenh phat khong co ten bai`() = expect(
        "Phát nhạc" to VoiceIntent.Media(VoiceMediaOp.PLAY),
        "Mở nhạc" to VoiceIntent.Media(VoiceMediaOp.PLAY),
        "Tắt nhạc" to VoiceIntent.Media(VoiceMediaOp.PAUSE),
        "Dừng nhạc" to VoiceIntent.Media(VoiceMediaOp.PAUSE),
        "Chuyển bài tiếp theo" to VoiceIntent.Media(VoiceMediaOp.NEXT),
        "Bài trước" to VoiceIntent.Media(VoiceMediaOp.PREV),
        "Next track" to VoiceIntent.Media(VoiceMediaOp.NEXT),
    )

    // ══ C · ÂM LƯỢNG (Kiki §7c #14–#16) ═══════════════════════════════════════════════════════════════

    /** *"tối đa"* là ĐÍCH, không phải một nấc. ⚠ 1.90: đo trên `fan` (`max = 7`, `min = 0`) thay `vol` (đã xoá). */
    @Test fun `tang gio toi da la lenh tuyet doi`() = expect(
        "Tăng gió tối đa" to VoiceIntent.Control("fan", 7),
        "Giảm gió" to VoiceIntent.Control("fan", null, -1),
        "Tăng gió" to VoiceIntent.Control("fan", null, 1),
    )

    // ══ D · NHỮNG THỨ KACHI CỐ Ý KHÔNG LÀM (Kiki §7c #18–#41, #44) ════════════════════════════════════

    @Test fun `radio truyen hinh tin tuc hoi dap deu KHONG doan mo`() {
        unknown("Mở radio", VoiceUnknownReason.NO_OBJECT)
        unknown("Mở kênh VOV Giao thông", VoiceUnknownReason.NO_OBJECT)
        unknown("Tin thời sự hôm nay", VoiceUnknownReason.NO_VERB)
        unknown("Quốc ca được sáng tác năm nào", VoiceUnknownReason.NO_VERB)
        unknown("Thời tiết hôm nay thế nào", VoiceUnknownReason.NO_OBJECT)
        unknown("12 x 15 bằng bao nhiêu", VoiceUnknownReason.NO_OBJECT)
        unknown("Kiki ơi", VoiceUnknownReason.NO_VERB)
        unknown("Tốc độ giới hạn ở đây là 50km/h", VoiceUnknownReason.NO_VERB)
        unknown("Tìm trạm xăng gần đây", VoiceUnknownReason.NO_VERB)
        unknown("", VoiceUnknownReason.EMPTY)
    }

    // ══ E · NÚT XE — 5 kiểu ControlKind ═══════════════════════════════════════════════════════════════

    @Test fun `TOGGLE bat tat`() = expect(
        "Bật đèn đọc" to VoiceIntent.Control("readl", 1),
        "Tắt đèn đọc" to VoiceIntent.Control("readl", 0),
        "Bật điều hoà" to VoiceIntent.Control("ac_auto", 1),
        "Tắt lọc bụi" to VoiceIntent.Control("pm25", 0),
        "Bật lấy gió trong" to VoiceIntent.Control("recirc", 1),
        "Turn on the reading light" to VoiceIntent.Control("readl", 1),
    )

    @Test fun `COVER mo dong`() = expect(
        "Mở kính trước trái" to VoiceIntent.Control("win_lf", 1),
        "Đóng kính trước trái" to VoiceIntent.Control("win_lf", 0),
        "Đóng kính sau phải" to VoiceIntent.Control("win_rr", 0),
        "Mở rèm che nắng" to VoiceIntent.Control("sunshade", 1),
    )

    @Test fun `STEP tuyet doi va tuong doi`() = expect(
        "Đặt nhiệt độ 22" to VoiceIntent.Control("temp", 22),
        "Đặt nhiệt độ hai mươi hai" to VoiceIntent.Control("temp", 22),
        "Đặt nhiệt độ hai mươi tư" to VoiceIntent.Control("temp", 24),
        "Set temperature to 24" to VoiceIntent.Control("temp", 24),
        "Tăng gió" to VoiceIntent.Control("fan", null, 1),
        "Giảm gió 2 nấc" to VoiceIntent.Control("fan", null, -2),
        "Đặt gió tối đa" to VoiceIntent.Control("fan", 7),
    )

    /** Giá trị ngoài dải bị **kẹp bằng chính `ControlDef.clamp`** — hai bề mặt không được có hai luật kẹp. */
    @Test fun `STEP ngoai dai bi kep theo ControlDef`() = expect(
        "Đặt nhiệt độ 99" to VoiceIntent.Control("temp", 33),
        "Đặt nhiệt độ 5" to VoiceIntent.Control("temp", 17),
    )

    // ⚠ (V) FEATURE-FILTER 2026-09-17: hai ca `chế độ lái` (nút `drive_mode`) đã gỡ cùng nút — owner chấm NO.
    // ⚠ UX-OVERHAUL · WP8 2026-09-20: ca `màu đèn viền` (`ambient_color`) gỡ cùng nút. Hai ca còn lại vẫn khoá
    // đúng luật "chọn theo NHÃN lựa chọn, không theo số thứ tự" — và cả hai đều là nhãn **nhiều từ**, tức vẫn phủ
    // ca khó nhất của luật ấy.
    // ⚠⚠ 1.90: cả HAI ca (`headlight_mode` · `camera_view`) gỡ cùng nút ⇒ mốc nay là `seatc` (*"Mức 1/2"*, nhãn
    // lựa chọn nhiều từ) — vẫn phủ ca khó nhất: parser đọc theo chỉ số thì *"mức 2"* ra 1.
    @Test fun `SELECT chon theo nhan lua chon`() = expect(
        "ghế mát mức 2" to VoiceIntent.Control("seatc", 2),
        "ghế mát mức 1" to VoiceIntent.Control("seatc", 1),
    )

    // ⚠ (V) FEATURE-FILTER 2026-09-17: hai ca `Sạc ngay` (`start_charging`) và `Gập gương` (`mirror_fold_btn`)
    // đã gỡ cùng hai nút — owner chấm NO.
    @Test fun `BUTTON bam mot phat`() = expect(
        "Lọc ngay" to VoiceIntent.Control("pm25_clean_now", null),
    )

    // ══ F · BA CẶP NHÃN LỒNG NHAU (backlog L-RE2) — luật "dãy dài nhất thắng" ══════════════════════════

    /**
     * [ĐO] `docs/PROJECT-BACKLOG.md` L-RE2: ba cặp nút dùng CHUNG feature-id nhưng nghĩa khác, và nhãn cái này
     * **chứa** nhãn cái kia. Nhận nhầm ở đây là bấm nhầm một nút ảnh hưởng tầm nhìn ban đêm.
     */
    @Test fun `khong nhan nham giua ba cap nhan long nhau`() = expect(
        "Bật camera 360" to VoiceIntent.Control("cam", 1),
        "Bật đèn pha" to VoiceIntent.Control("headl", 1),
        // ⚠⚠ 1.90 — CẢ BA CẶP đã tan (lời giải cuối cho L-RE2): `camera_view` · `headlight_mode` ·
        // `brightness_gear` đều xoá (`hud_brightness` purge ở WP8). Hai câu còn lại vẫn phải trỏ ĐÚNG nút.
        // ⚠⚠ UX-OVERHAUL · WP8 2026-09-20 — cặp thứ BA (*"độ sáng màn"* vs *"độ sáng HUD"*) **hết tồn tại**:
        // `hud_brightness` purge theo triage owner (#63), và đó cũng là lời giải cho chính bug L-RE2 mà bài này
        // sinh ra để canh — hai nút ấy dùng CHUNG feature-id `1276174360`, nên chỉ một trong hai từng nói thật.
        // `brightness_gear` (độ sáng màn chính) là cái ĐÚNG và nó ở lại; ca *"độ sáng HUD"* nay phải KHÔNG hiểu
        // được, và bài `khong nhan nham` vẫn còn hai cặp lồng nhau để canh.
    )

    /**
     * CLOSE-2 2026-09-26 — [ĐO E2E máy ảo 3 lượt] năm cụm nút đã gỡ (khoá xe · mở khoá cửa · rời xe · âm lượng ·
     * độ sáng màn) trả "Chưa rõ cần làm gì"/"thử nêu mức" thay vì tên tính năng đã bỏ ⇒ thêm vào [VoiceFeatureGone.ALL].
     * Bài này khoá: câu ra Unknown (không rơi sang nút khác) VÀ bảng gone có dòng khớp cho cụm ấy.
     */
    @Test fun `nam cum nut da go sau WP8 phai ra Unknown va co dong trong bang gone`() {
        mapOf(
            "khoá xe" to "khoá xe", "mở khoá cửa" to "mở khoá cửa", "rời xe" to "gói rời xe",
            "giảm âm lượng" to "âm lượng", "đặt độ sáng màn 8" to "độ sáng màn",
        ).forEach { (line, label) ->
            val got = one(line)
            assertTrue(got !is VoiceIntent.Control && got !is VoiceIntent.Read, "\"$line\" đã gỡ ⇒ không được thành lệnh xe, ra: $got")
            assertTrue(VoiceFeatureGone.ALL.any { it.label == label }, "bảng gone phải có dòng \"$label\"")
        }
    }

    /**
     * SOÁT 2.68 (senior review CLOSE-2) — **đường ĐỌC còn sống phải THẮNG bảng gone** (luật khai §2 của
     * [VoiceFeatureGone]). [ĐO] dòng mới `["am","luong"]` từng biến *"âm lượng bao nhiêu"* `Read(media_vol)` →
     * `Unknown(FEATURE_GONE)`: [VoiceFeatureGone.match] còn được hỏi ở đường câu-HỎI (`objectOnlyRead`) TRƯỚC lượt
     * tra datum, mà `media_vol` (`AudioManager.getStreamVolume`, mức PROVEN) chưa hề bị gỡ — chỉ NÚT `vol` bị gỡ.
     * Cờ [VoiceFeatureGone.Gone.readAlive] + `match(forRead = true)` giữ cả hai vế; bài này khoá cả hai.
     */
    @Test fun `SOAT 2 68 - cum da go chan cau LENH nhung KHONG chan cau HOI khi datum doc con song`() {
        assertEquals(VoiceIntent.Read("media_vol"), one("âm lượng bao nhiêu"), "câu HỎI về datum còn sống")
        assertEquals(VoiceIntent.Read("media_vol"), one("âm lượng đang bao nhiêu"), "cùng câu, thêm từ đệm")
        val cmd = one("giảm âm lượng")
        assertTrue(cmd is VoiceIntent.Unknown && cmd.reason == VoiceUnknownReason.FEATURE_GONE, "câu LỆNH vẫn phải là 'đã bỏ', ra: $cmd")
        // Mọi dòng `readAlive` phải THẬT SỰ có datum đọc mang đúng cụm đó — không được dùng cờ này để né bài canh.
        VoiceFeatureGone.ALL.filter { it.readAlive }.forEach { g ->
            assertNull(VoiceFeatureGone.match(VoiceLexicon.tokenize(g.words.joinToString(" ")), forRead = true),
                "dòng readAlive \"${g.label}\" phải được bỏ qua ở đường câu HỎI")
            assertNotNull(VoiceFeatureGone.match(VoiceLexicon.tokenize(g.words.joinToString(" "))),
                "…nhưng vẫn phải chặn ở đường câu LỆNH")
        }
    }

    /** Nhãn của một nút đã purge thì phải trở về KHÔNG HIỂU — không được rơi sang nút gần giống nào khác. */
    @Test fun `nhan cua nut da purge o WP8 khong duoc roi sang nut khac`() {
        listOf("Đặt độ sáng HUD 3", "Bật HUD kính lái", "Bật gạt mưa", "Đặt màu đèn viền xanh lá", "Đặt mức tái tạo cao")
            .forEach { line ->
                val got = one(line)
                assertTrue(
                    got !is VoiceIntent.Control && got !is VoiceIntent.Read,
                    "\"$line\" nói về một nút đã purge ở WP8 ⇒ không được thành lệnh xe, ra: $got",
                )
            }
    }

    /**
     * ═══ WP8 · ĐIỀU KIỆN KẾT NẠP của [VoiceFeatureGone.HARD_BLOCK] — canh bằng MÁY, không bằng lời ═══════════
     *
     * Bảng chặn cứng được hỏi **trước mọi phép khớp**, nên một từ lọt vào đó sẽ giết **mọi** câu chứa nó — kể cả
     * câu của một tính năng đang chạy tốt, và giết **im lặng** (người lái chỉ thấy *"đã bỏ"*). KDoc của bảng đặt ra
     * đúng một điều kiện cho việc thêm từ: *không nhãn/từ-đồng-nghĩa nào còn chứa nó*. Điều kiện ấy tới nay chỉ là
     * một câu văn kèm *"[ĐO grep sau purge]"* — tức nó đúng ở thời điểm viết và không có gì giữ cho nó còn đúng.
     *
     * Bài này biến nó thành phép kiểm: mỗi từ chặn cứng phải **không** xuất hiện trong từ vựng SINH từ bộ đăng ký.
     * Hệ quả thực dụng: hôm nào ai đó thêm lại một nút có chữ `hud` trong nhãn, bài này đỏ **trước** khi nút đó ra
     * xe và chết không hiểu vì sao.
     *
     * ⚠ Chỉ phủ được từ vựng TĨNH (nút · datum · nhóm · gói lệnh). Nhãn app do máy cài sinh ra là động ⇒ một app
     * tên *"HUD …"* vẫn lọt; đó là giới hạn đã biết, không phải chỗ quên.
     */
    @Test fun `moi tu chan cung KHONG duoc nam trong tu vung dang song`() {
        val vocab = VoiceGrammar.terms(profiles, apps)
        VoiceFeatureGone.HARD_BLOCK.forEach { blocked ->
            val clash = vocab.filter { blocked in it.words }
            assertTrue(
                clash.isEmpty(),
                "từ chặn cứng \"$blocked\" còn nằm trong từ vựng đang sống ⇒ nó sẽ giết chính tính năng đó: " +
                    clash.joinToString { "${it.kind}/${it.id}=${it.words}" },
            )
        }
    }

    /**
     * Canary đi kèm bài trên: cụm *"kính lái"* là từ đồng nghĩa của `window` từ 1.66 và nó **vẫn phải sống**. Đây
     * là nửa thứ hai của bug WP8 — chặn *"HUD kính lái"* mà chặn luôn *"kính lái"* thì đổi một lệnh sai thành một
     * tính năng mất.
     */
    @Test fun `chan cung HUD khong giet lenh kinh lai`() = expect(
        "Mở kính lái" to VoiceIntent.Control("win_lf", 1),
        "Đóng kính lái" to VoiceIntent.Control("win_lf", 0),
    )

    // ══ G · NHÃN TRÙNG giữa ĐỌC và HÀNH ĐỘNG — loại động từ quyết định ════════════════════════════════

    /**
     * 18 nhãn trùng giữa mục ĐỌC và HÀNH ĐỘNG (`CapabilityCatalog.collidingLabels`). *"Kính trước-trái"* vừa là
     * datum (% mở) vừa là nút (đóng/mở); *"Gạt mưa"* vừa là badge trạng thái vừa là nút.
     */
    @Test fun `cung mot cum dong tu quyet dinh xem hay bam`() = expect(
        "Xem kính trước trái" to VoiceIntent.Read("window_lf"),
        "Mở kính trước trái" to VoiceIntent.Control("win_lf", 1),
        // ⚠ WP8: cặp *"Gạt mưa"* (datum `wiper_state` + nút `wiper`) đã purge cả hai ⇒ bỏ khỏi bài. Hai cặp còn
        // lại vẫn phủ đúng luật *"loại động từ quyết định xem hay bấm"* trên nhãn TRÙNG.
        "Xem cửa sổ trời" to VoiceIntent.Read("sunroof_state"),
        "Mở cửa sổ trời" to VoiceIntent.Control("sunroof", 1),
    )

    // ══ H · ĐỌC THÔNG TIN ═════════════════════════════════════════════════════════════════════════════

    @Test fun `doc thong tin xe`() = expect(
        "Xem pin" to VoiceIntent.Read("soc"),
        "Pin còn bao nhiêu" to VoiceIntent.Read("soc"),
        "Đọc tốc độ" to VoiceIntent.Read("speed", aloud = true),
        "Kiểm tra áp suất lốp trước trái" to VoiceIntent.Read("tyre_p_fl"),
        "Xem nhiệt ngoài xe" to VoiceIntent.Read("ext_temp"),
        "Show battery" to VoiceIntent.Read("soc"),
        "Xem tầm hoạt động EV" to VoiceIntent.Read("ev_range_km"),
    )

    /** *"đọc"* ⇒ chờ NGHE, *"xem"* ⇒ chờ NHÌN. Hôm nay cả hai ra chữ (chưa có TTS — R8) nhưng ý định khác nhau. */
    @Test fun `doc to va xem la hai y dinh khac nhau`() {
        assertEquals(VoiceIntent.Read("soc", aloud = true), one("Đọc pin"))
        assertEquals(VoiceIntent.Read("soc", aloud = false), one("Xem pin"))
    }

    // ══ I · GÓI LỆNH · LAUNCHER · HỒ SƠ · APP ═════════════════════════════════════════════════════════

    /** Tên gói lệnh bắt đầu bằng một động từ ⇒ luật "cả câu là TÊN của việc" (xem `headMatch`). */
    @Test fun `goi lenh thang nut cung ten`() = expect(
        "Mở hết kính" to VoiceIntent.Macro("mac_win_open_all"),
        "Đóng hết kính" to VoiceIntent.Macro("mac_win_close_all"),
        "Close all" to VoiceIntent.Macro("mac_win_close_all"),
    )

    @Test fun `launcher ho so va app`() = expect(
        "Mở Cài đặt" to VoiceIntent.Launcher("launcher_settings"),
        "Mở ứng dụng" to VoiceIntent.Launcher("launcher_apps"),
        "Mở ứng dụng VTV Go" to VoiceIntent.OpenApp("VTV Go"),
        "Mở YouTube" to VoiceIntent.OpenApp("YouTube"),
        "Chuyển sang hồ sơ Vợ" to VoiceIntent.Profile("Vợ"),
        "Đổi sang Mặc định" to VoiceIntent.Profile("Mặc định"),
    )

    // ══ J · CÂU GHÉP (Kiki §7c #42) ═══════════════════════════════════════════════════════════════════

    @Test fun `cau ghep tach theo thu tu noi`() {
        assertEquals(
            listOf(VoiceIntent.Nav("Bitexco"), VoiceIntent.Media(VoiceMediaOp.QUERY, "trẻ")),
            all("Chỉ đường đến Bitexco và mở nhạc trẻ"),
        )
        assertEquals(
            listOf(VoiceIntent.Macro("mac_win_close_all"), VoiceIntent.Control("readl", 1)),
            all("Đóng hết kính rồi bật đèn đọc"),
        )
        assertEquals(
            listOf(VoiceIntent.Control("readl", 1), VoiceIntent.Control("headl", 0)),
            all("Bật đèn đọc và tắt đèn pha"),
        )
    }

    /**
     * [ĐO] mẫu câu Kiki #10 — chữ *"và"* nằm TRONG tên bài hát. Tách vô điều kiện sẽ gửi đi nửa cái tên.
     * Luật *"mọi vế phải hiểu được, không thì trả nguyên câu"* tự xử ca này mà không cần biết bài hát nào có chữ "và".
     */
    @Test fun `va nam trong ten bai hat thi KHONG tach`() = assertEquals(
        listOf(VoiceIntent.Media(VoiceMediaOp.QUERY, "Cỏ dại và hoa dành dành")),
        all("Mở bài Cỏ dại và hoa dành dành"),
    )

    // ══ K · KHÔNG DẤU · HOA THƯỜNG · DẤU CÂU ══════════════════════════════════════════════════════════

    /**
     * Chuỗi vào tầng này có thể tới từ bàn phím xe (thường **không dấu**), từ kịch bản test, và mai kia từ ASR.
     * Không chịu được cả ba dạng thì đường thử bằng chữ vô dụng ngay từ đầu.
     */
    @Test fun `khong dau hoa thuong dau cau deu ra cung mot y dinh`() {
        val want = VoiceIntent.Control("readl", 1)
        listOf("Bật đèn đọc", "bat den doc", "BẬT ĐÈN ĐỌC", "  bật  đèn   đọc !! ", "Kachi, bật đèn đọc")
            .forEach { assertEquals(want, one(it), "câu: \"$it\"") }
    }
}
