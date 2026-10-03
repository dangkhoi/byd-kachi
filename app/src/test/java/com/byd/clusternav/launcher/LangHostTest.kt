package com.byd.clusternav.launcher

import com.byd.clusternav.Lang as ClusterNavLang
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ HÀNH VI LÚC CHẠY của chỗ map ngôn ngữ → `Locale` / mẫu ngày, và của `Lang.t/f` ở `:app` ═════════════════════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` §4.5 · R6 · R7. `LauncherLocaleContractTest` canh HÌNH DẠNG mã (ai ghi,
 * ai dựng Locale); bài này canh GIÁ TRỊ — các hàm thuần của [LangHost] chỉ đọc `Strings.current`, gọi được ở JVM.
 *
 * Mỗi bài tự trả `Strings.current` về VI ([restore]) — `var` toàn cục rò từ bài này sang bài khác là họ lỗi "chạy
 * một mình thì xanh, chạy cả gói thì đỏ".
 */
class LangHostTest {

    @AfterEach fun restore() { Strings.current = Lang.VI }

    private fun at(lang: Lang) { Strings.current = lang }

    @Test
    fun `moi tieng mot Locale dung thu muc tai nguyen`() {
        val want = mapOf(
            Lang.VI to Locale("vi"),
            Lang.EN to Locale.ENGLISH,
            Lang.ZH to Locale.SIMPLIFIED_CHINESE,   // zh-CN ⇒ values-zh-rCN
            Lang.TH to Locale("th"),                 // KHÔNG kèm TH — xem bài lịch dưới
            Lang.MS to Locale("ms"),
        )
        assertEquals(Lang.entries.toSet(), want.keys, "bài phải phủ MỌI Lang — thêm tiếng thì thêm dòng ở đây")
        want.forEach { (lang, loc) -> at(lang); assertEquals(loc, LangHost.locale(), "$lang") }
    }

    /**
     * [ĐO] JDK 17 dựng `BuddhistCalendar` cho `th_TH` (năm 2569), libcore Android 10/12 luôn Gregorian. Locale tiếng
     * Thái của [LangHost] không kèm quốc gia ⇒ JVM test và xe cùng ra năm dương lịch. Vế thứ hai chứng minh phép thử
     * CÓ cắn: cùng ngày đó với `th_TH` ra năm khác.
     */
    @Test
    fun `tieng Thai khong ra lich Phat giao`() {
        at(Lang.TH)
        assertEquals("", LangHost.locale().country, "Locale tiếng Thái phải KHÔNG có quốc gia")
        val day = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, Calendar.OCTOBER, 3) }.time
        fun year(loc: Locale) = SimpleDateFormat("yyyy", loc).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(day)
        assertEquals("2026", year(LangHost.locale()))
        assertNotEquals("2026", year(Locale("th", "TH")), "phép thử phải phân biệt được lịch Phật giáo — nếu không nó mù")
    }

    /** R6: giọng nói chỉ có hai tiếng — EN cho người chọn English, tiếng Việt cho mọi tiếng khác. */
    @Test
    fun `locale giong noi chi la Viet hoac Anh`() {
        Lang.entries.forEach { lang ->
            at(lang)
            assertEquals(if (lang == Lang.EN) Locale.ENGLISH else Locale("vi"), LangHost.voiceLocale(), "$lang")
        }
    }

    /**
     * T2 — `:wake` truyền tiếng TƯỜNG MINH (ảnh chụp ngữ pháp; `Strings.current` ở đó luôn VI): kết quả chỉ phụ thuộc
     * tiếng truyền vào, không phụ thuộc màn. Nhận cả tiếng giao diện lẫn tiếng giọng nói đã suy (phép `voice` luỹ đẳng).
     */
    @Test
    fun `locale giong noi tuong minh khong phu thuoc Strings current`() {
        Lang.entries.forEach { ui ->
            at(ui)
            Lang.entries.forEach { l ->
                assertEquals(if (l == Lang.EN) Locale.ENGLISH else Locale("vi"), LangHost.voiceLocale(l), "màn $ui · lang $l")
            }
        }
    }

    /** §4.5: VI/EN y byte như trước; ZH đổi thứ tự (月/日); TH/MS giữ mẫu chung — chỉ tên thứ đổi theo Locale. */
    @Test
    fun `mau ngay theo tieng`() {
        Lang.entries.forEach { lang ->
            at(lang)
            assertEquals(if (lang == Lang.ZH) "M月d日 EEEE" else "EEEE, dd/MM", LangHost.datePattern(), "$lang")
        }
        at(Lang.ZH)
        val day = GregorianCalendar(2026, Calendar.OCTOBER, 3).time
        val out = SimpleDateFormat(LangHost.datePattern(), LangHost.locale()).format(day)
        assertTrue(out.startsWith("10月3日 ") && out.contains("星期"), "ZH: $out")
        at(Lang.VI)
        assertEquals("03/10", SimpleDateFormat(LangHost.datePattern(), LangHost.locale()).format(day).substringAfter(", "))
    }

    /**
     * `Lang.t/f` của `:app` (màn cũ + mọi `Lang.t` ở launcher) uỷ quyền cho `Strings`: VI/EN y byte như bản hai nhánh
     * cũ; ZH/TH/MS KHÔNG BAO GIỜ ra tiếng Việt (thiếu dòng ⇒ tiếng Anh). Bảng trong repo có thể đã có dòng khi T4 ráp
     * bản dịch, nên vế ZH/TH/MS chỉ khoá điều luôn đúng: khác bản Việt, và chỗ trống `{0}` đã được thay.
     */
    @Test
    fun `Lang t va f cua app di qua bang dich chung`() {
        at(Lang.VI)
        assertEquals("đang tải… 42%", ClusterNavLang.f("đang tải… {0}%", "downloading… {0}%", 42))
        assertEquals("tải thất bại", ClusterNavLang.t("tải thất bại", "download failed"))
        at(Lang.EN)
        assertEquals("downloading… 42%", ClusterNavLang.f("đang tải… {0}%", "downloading… {0}%", 42))
        assertEquals("network error: null", ClusterNavLang.f("lỗi mạng: {0}", "network error: {0}", null), "null ⇒ \"null\" như \"\$x\"")
        for (lang in listOf(Lang.ZH, Lang.TH, Lang.MS)) {
            at(lang)
            assertNotEquals("tải thất bại", ClusterNavLang.t("tải thất bại", "download failed"), "$lang: lùi về tiếng Việt")
            val f = ClusterNavLang.f("đang tải… {0}%", "downloading… {0}%", 42)
            assertTrue("42" in f && "{0}" !in f && "đang tải" !in f, "$lang: $f")
        }
    }
}
