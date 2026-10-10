package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ═══ 3.0 R1+R2 `NAV-MULTI-PROVIDER-ALL3` · `NAV-BY-PROVIDER-SPLIT` — khoá lỗi xe 10/10 ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-300-plan.html` R1 · R2 · luật ở [VoiceNavProviders]. [ĐO xe 10/10, Seal fw 2606, Kachi 2.99]: câu nhiều app
 * nuốt cả đuôi *"bằng … và …"* vào TÊN điểm đến ⇒ chỉ một app nhận một địa chỉ rác (VietMap tra hỏng ⇒ `MAIN/LAUNCHER`). Bài khoá: mỗi
 * app nêu ra được MỘT ý định riêng mang đúng điểm đến sạch, và các câu một app / không app giữ nguyên.
 */
class VoiceNavMultiProviderTest {

    private val apps = listOf("Google Maps", "VietMap", "Waze", "YouTube")
    private fun parse(s: String, places: List<String> = emptyList()) = VoiceIntentParser.parse(s, apps = apps, places = places)
    private fun nav(q: String, vararg keys: String) = keys.map { VoiceIntent.Nav(q, it) }

    @Test
    fun `TEST CASE backlog - Ben Thanh bang Google Maps, VietMap, va Waze ⇒ ca 3 app cung diem den`() {
        assertEquals(
            nav("chợ Bến Thành", VoiceAppTargets.GMAPS, VoiceAppTargets.VIETMAP, VoiceAppTargets.WAZE),
            parse("dẫn đường đến chợ Bến Thành bằng Google Maps, VietMap, và Waze"),
        )
    }

    @Test
    fun `bien the chu ASR - khong dau phay, thu tu khac, hoac, voi`() {
        val gvw = nav("chợ bến thành", VoiceAppTargets.GMAPS, VoiceAppTargets.VIETMAP, VoiceAppTargets.WAZE)
        assertEquals(gvw, parse("dẫn đường đến chợ bến thành bằng google maps vietmap và waze"))
        assertEquals(gvw, parse("dẫn đường tới chợ bến thành bằng google maps với vietmap với waze"))
        assertEquals(
            nav("chợ bến thành", VoiceAppTargets.WAZE, VoiceAppTargets.GMAPS),
            parse("chỉ đường đến chợ bến thành bằng waze hoặc google maps"),
        )
        // Tên NGHE LỆCH vẫn qua đúng ba tầng của mệnh đề chọn app: *"vietma"* (rụng âm cuối) · *"quay"* (cách nói Waze đã khai).
        assertEquals(gvw, parse("dẫn đường đến chợ bến thành bằng google maps vietma và quay"))
    }

    /** [ĐO xe 10/10 21:24:52] chuỗi sherpa *"… bằng google maps và vietmap"* — trước bản vá: `Nav(query = "… bằng google maps và vietmap")`. */
    @Test
    fun `log xe 10-10 - bang google maps va vietmap ⇒ hai app, diem den sach`() {
        assertEquals(
            nav("chợ bến thành", VoiceAppTargets.GMAPS, VoiceAppTargets.VIETMAP),
            parse("dẫn đường đến chợ bến thành bằng google maps và vietmap"),
        )
    }

    /** [ĐO xe 10/10] *"dẫn đường đến công ty một bằng vietmap và google"* — nơi ĐÃ LƯU cũng nhân theo app (đường `NavigateSaved` có sẵn). */
    @Test
    fun `log xe 10-10 - noi da luu bang vietmap va google ⇒ NavigateSaved cho tung app`() {
        assertEquals(
            listOf(
                VoiceIntent.NavigateSaved("công ty một", VoiceAppTargets.VIETMAP),
                VoiceIntent.NavigateSaved("công ty một", VoiceAppTargets.GMAPS),
            ),
            parse("dẫn đường đến công ty một bằng vietmap và google", places = listOf("công ty một")),
        )
    }

    @Test
    fun `trung ten app ⇒ mot y dinh, khong ban hai lan`() {
        assertEquals(nav("bitexco", VoiceAppTargets.GMAPS), parse("dẫn đường đến bitexco bằng google maps và google"))
    }

    @Test
    fun `doi chung - cau mot app va cau khong app giu nguyen`() {
        assertEquals(nav("bitexco", VoiceAppTargets.WAZE), parse("dẫn đường đến bitexco bằng waze"))
        assertEquals(listOf(VoiceIntent.Nav("cầu bằng lăng", null)), parse("dẫn đường đến cầu bằng lăng"))
        // Đuôi có chữ KHÔNG phải tên app ⇒ không phải danh sách ⇒ đường cũ (không đoán).
        assertEquals(listOf(VoiceIntent.Nav("bến xe bằng xe máy và waze", null)), parse("dẫn đường đến bến xe bằng xe máy và waze"))
        // Câu ghép thật (vế sau là lệnh khác) không bị nhầm thành danh sách.
        val ghep = parse("dẫn đường đến chợ bến thành bằng waze rồi bật điều hòa")
        assertEquals(VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.WAZE), ghep.first())
        assertEquals(2, ghep.size)
    }

    /** Soát 3.0 Pass 1 — chữ *"bằng"* hay *"và"* nằm TRONG tên điểm đến không làm lệch danh sách app (cổng 1: đuôi phải phủ trọn tới hết câu). */
    @Test
    fun `soat Pass 1 - bang va trong ten diem den van tach dung danh sach app`() {
        assertEquals(
            nav("cầu bằng lăng", VoiceAppTargets.WAZE, VoiceAppTargets.VIETMAP),
            parse("dẫn đường đến cầu bằng lăng bằng waze và vietmap"),
        )
        assertEquals(
            nav("quán cỏ và hoa", VoiceAppTargets.WAZE, VoiceAppTargets.GMAPS),
            parse("dẫn đường đến quán cỏ và hoa bằng waze và google maps"),
        )
        // Tên app KHÔNG phải app dẫn đường trong đuôi (YouTube) ⇒ không phải danh sách app NAV ⇒ đường cũ (không mở nhạc nhầm).
        assertEquals(1, parse("dẫn đường đến chợ bến thành bằng google maps và youtube").size)
        // Đuôi kết bằng từ nối (ASR cắt sớm) ⇒ không đoán tên thứ ba, đường cũ.
        assertEquals(1, parse("dẫn đường đến chợ bến thành bằng google maps và vietmap và").size)
    }

    @Test
    fun `nhac khong nhan theo app - mot bai hai app la hai luong tieng`() {
        val got = parse("phát bài diễm xưa bằng spotify và youtube")
        assertEquals(1, got.size)
        assertEquals(VoiceIntent.Media::class, got.single()::class)
    }
}
