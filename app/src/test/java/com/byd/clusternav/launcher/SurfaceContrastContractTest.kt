package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import com.byd.clusternav.testsupport.Wcag.fmt
import com.byd.clusternav.testsupport.Wcag.luminance
import com.byd.clusternav.testsupport.Wcag.over
import com.byd.clusternav.testsupport.Wcag.ratio
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VISUAL-REFRESH P1 · T1 — BÀI CANH TƯƠNG PHẢN CỦA CHẤT LIỆU BỀ MẶT ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-visual-refresh.html` §R3 (AC3.1–AC3.3) + §6.4.
 *
 * ## Vì sao KHÔNG gộp vào [ThemePaletteContractTest]
 * Tệp kia đã ~485 dòng và canh **bảng màu nói chung**; phần dưới đây canh riêng **chất liệu bề mặt** (gradient hai
 * đầu · sắc lĩnh vực · trạng thái bật · và từ WP1: *không* mép, *không* viền). Hai bộ luật, hai vòng đời: bảng màu
 * đổi vì chủ đề, chất liệu đổi vì thiết kế. Gộp lại thì mỗi lần một bên đỏ, người sửa phải đọc cả hai để biết mình
 * đang vi phạm luật nào.
 *
 * ## ⚠ Mọi phép đo ở đây TRỘN trước, đo sau
 * Vai `surf*` phần lớn có kênh trong suốt (`#AARRGGBB`), và một mã alpha **không tồn tại trên màn** cho tới khi nó
 * nằm trên một nền. Xem KDoc [com.byd.clusternav.testsupport.Wcag].
 */
class SurfaceContrastContractTest {

    /**
     * Chữ trên thẻ ĐANG BẬT — đo trên **nền đã trộn**, vì `surfOn*` bán trong suốt.
     *
     * Mực ở đây là [KachiPalette.ink], **không** phải [KachiPalette.onAccent]: bản sáng trộn ra `#b5c3ef`, chữ
     * trắng trên đó chỉ **1.75:1**. Đúng cái bẫy đã ghi ở [KachiPalette.inkOnAccent] — cùng một vai "chữ trên nền
     * nhấn", nhưng nền ĐẶC và nền BÁN TRONG SUỐT đòi hai hướng mực ngược nhau.
     */
    @Test
    fun `chu tren the dang bat dat 4_5 to 1 o ca hai bang`() {
        val bad = mutableListOf<String>()
        forEachPalette { name, p ->
            listOf("surfOnFrom", "surfOnTo").forEach { g ->
                val r = ratio(p.ink, over(role(p, g), p.bg))
                if (r < 4.5) bad += "$name ink trên $g(trộn trên bg) = ${fmt(r)}"
            }
        }
        assertEquals(emptyList<String>(), bad, "chữ trên thẻ BẬT dưới 4.5:1: $bad")
    }

    /**
     * Thẻ BẬT phải **nhìn ra được** là đang bật — ràng buộc (c) của owner 2026-09-16.
     *
     * Đo bằng bước sáng so với thẻ thường, không đo bằng "có đổi màu không": đổi sắc mà giữ nguyên độ sáng thì
     * người mù màu (≈ 8 % nam giới) không thấy gì khác — và trên màn đầu máy ban ngày thì cả người không mù màu
     * cũng khó thấy.
     */
    @Test
    fun `the dang bat khac the thuong o ca hai bang`() {
        forEachPalette { name, p ->
            val on = over(p.surfOnFrom, p.bg)
            val off = over(p.surfFrom, p.bg)
            val step = ratio(on, off)
            assertTrue(
                step >= 1.20,
                "$name: thẻ BẬT chỉ khác thẻ thường ${fmt(step)}× — cần ≥ 1.20× để trạng thái chọn nhìn ra được " +
                    "mà không phải đọc chữ.",
            )
        }
    }

    /**
     * Thẻ CHẤT LIỆU phải tách được khỏi nền — **hai bảng dùng hai cơ chế**, y như bài [the tach duoc khoi nen].
     *
     * Bản TỐI: bước sáng thật (đỉnh gradient sáng hơn nền 1.23×) ⇒ đủ một mình.
     * Bản SÁNG: thẻ trắng trên nền sáng chỉ hơn nhau 1.13× ⇒ nó đi qua bài này **nhờ chân viền**
     * [KachiPalette.surfLine] ≥ 3:1. Bài khoá **tính chất**, không khoá cơ chế, nên nó tự đảo chiều nếu ai làm
     * phẳng bảng tối.
     *
     * ## ⚠⚠ [WP1 · 2026-09-20] CHÂN VIỀN NAY **KHÔNG CÒN ĐƯỢC VẼ** bởi [KachiTheme.surface] — ghi ra, không giấu
     * Owner *"bỏ viền đi luôn"* ⇒ `surface()` gỡ mọi `setStroke`, nên [KachiPalette.surfLine] chỉ còn là **một mã
     * màu trong bảng** + đường opt-in của `card(stroke = …)`/`pill(stroke = …)`. Hệ quả đo được: **trên bảng SÁNG,
     * thẻ nội dung nay chỉ tách khỏi nền 1.13×** và không có đường kẻ nào — tức bài này VẪN xanh bằng một chân mà
     * màn hình không còn dùng.
     *
     * Cố ý **không** đổi bài thành "chỉ đo bước sáng": làm vậy thì bảng SÁNG đỏ, và hai cách chữa duy nhất là (a)
     * hạ sàn 1.15 — nới luật an toàn, hoặc (b) đổi thang chói bảng sáng — đúng thứ WP1 đã chốt GIỮ NGUYÊN (lớp che
     * thẻ-kính-trên-ảnh ở tone COOL chỉ dư ~0.01 so với sàn 4.5). Đây là **quyết định của owner** (giữ 1.13× hay
     * cho phép một mép ở riêng bảng sáng), đã ghi ở `docs/_handoff/ux-wp1-edges-removed.md`.
     */
    @Test
    fun `the chat lieu tach duoc khoi nen o ca hai bang`() {
        forEachPalette { name, p ->
            val top = over(p.surfFrom, p.bg)
            val border = ratio(over(p.surfLine, top), top)
            val step = ratio(top, p.bg)
            assertTrue(
                border >= 3.0 || step >= 1.15,
                "$name: thẻ chất liệu KHÔNG tách được khỏi nền — viền ${fmt(border)} (cần ≥ 3.0) và bước sáng " +
                    "${fmt(step)} (cần ≥ 1.15); phải đạt một trong hai.",
            )
        }
    }

    /**
     * ═══ [SOÁT Pass 4] BA BẬC PHẢI THẬT SỰ LÀ BA BẬC ═════════════════════════════════════════════════════════
     *
     * Màn chính có đúng ba mặt chồng lên nhau: **nền màn** → **khay** (ô làm việc) → **thẻ nội dung**. Lượt P1
     * chỉ động vào bậc trên cùng, nên bậc giữa vẫn bằng bậc dưới và cả ba đọc thành một mảng xám — đó là toàn bộ
     * nội dung lời chê *"đổi mà nhìn không ra"*.
     *
     * Bài khoá **từng bậc một**, không khoá tổng: khay có thể lệch khỏi nền mà thẻ vẫn chìm trong khay (hoặc
     * ngược lại), và một phép đo gộp sẽ cho một con số đẹp trong khi màn hình vẫn phẳng.
     *
     * Sàn 1.12× cho bậc khay/nền và 1.15× cho bậc thẻ/khay: bảng SÁNG không thể đạt 1.15× ở bậc dưới vì nền màn
     * đã gần trắng (mọi thứ sáng hơn nó đều là trắng), ở đó việc tách do [KachiPalette.lineStrong] gánh — đúng cơ
     * chế mà bài `the chat lieu tach duoc khoi nen` đã mô tả cho cặp thẻ/nền.
     */
    @Test
    fun `nen khay the la ba bac nhin ra duoc`() {
        forEachPalette { name, p ->
            val well = over(p.slot, p.bg)
            val card = over(p.surfFrom, well)
            val wellStep = ratio(well, p.bg)
            val cardStep = ratio(card, well)
            val wellBorder = ratio(over(p.lineStrong, well), well)
            assertTrue(
                wellStep >= 1.12 || wellBorder >= 3.0,
                "$name: KHAY không tách khỏi nền màn — bước ${fmt(wellStep)}× (cần ≥ 1.12) và viền " +
                    "${fmt(wellBorder)} (cần ≥ 3.0); phải đạt một trong hai.",
            )
            assertTrue(
                cardStep >= 1.15,
                "$name: THẺ chìm trong KHAY — chỉ ${fmt(cardStep)}× (cần ≥ 1.15). Khay và thẻ cùng sắc độ thì " +
                    "thẻ hết chỗ nổi lên; đây đúng là chỗ bảng SÁNG từng hỏng (khay #ffffff + thẻ #ffffff).",
            )
        }
    }

    /**
     * ═══ [UX-OVERHAUL WP1 · iteration 2026-09-20] BỀ MẶT KHÔNG CÒN MÉP, KHÔNG CÒN VIỀN ═══════════════════════
     *
     * **Bài này ĐẢO CHIỀU chính bản WP1 sáng cùng ngày** (vốn *đòi* `GLASS_SHEEN`+`GLASS_SHADE` = bevel 1px). Owner
     * xem ảnh bản đó: *"better, bị bug gạch trên đầu mỗi khung, bỏ viền đi luôn"* ⇒ gỡ HẲN mép **và** viền.
     *
     * ## Vì sao khoá bằng lệnh CẤM, không khoá bằng ngưỡng cường độ
     * Bản sáng cùng ngày đã thử đúng cách "khoá cường độ": cho phép mép nhưng bắt nó 1px + bán trong suốt + đi cặp
     * sheen/shade. Nó **vẫn** bị gọi là gạch. Cộng `surfEdge`/`surfOnEdge` (Pass-4 → Pass-5) thì đây là lần thứ BA
     * cùng một họ lỗi, và kết luận đo được là: cái sai nằm ở **HÌNH DẠNG** — một hình chữ nhật ghim vào cạnh thẻ
     * đọc ra thành một VẠCH ở mọi alpha, mọi độ dày. Ngưỡng cường độ không bắt được điều đó; lệnh cấm thì có.
     *
     * Bốn tone vì thế phân biệt nhau **CHỈ bằng MÀU FILL** ([KachiTheme.surfacePair]). Chiều nổi do chuyển sắc DỌC
     * gánh một mình — bài `chuyen sac doc con du manh de thay the mep sang o bang toi` khoá đúng phần đó.
     */
    @Test
    fun `khong con mep hay vien tren be mat`() {
        val body = SourceRoots.body(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt"), "fun surface(",
        )
        val banned = listOf(
            "GLASS_SHEEN" to "vệt sáng đỉnh (mép kính)",
            "GLASS_SHADE" to "vệt tối đáy (mép kính)",
            "setStroke" to "viền quanh thẻ",
            "Gravity.TOP" to "lớp ghim vào cạnh trên",
            "Gravity.BOTTOM" to "lớp ghim vào cạnh dưới",
            "setLayerHeight" to "dải mỏng ghim vào một cạnh",
            "setLayerGravity" to "lớp ghim vào một cạnh",
        ).filter { (needle, _) -> needle in body }
        assertEquals(
            emptyList<Pair<String, String>>(), banned,
            "surface() mọc lại mép/viền — owner 2026-09-20 đã chê ĐÚNG hình dạng này hai lần (Pass-5 nét đỉnh đặc, " +
                "WP1 bevel 1px bán trong suốt). Phân biệt tone bằng MÀU FILL (surfacePair), chiều nổi bằng chuyển " +
                "sắc DỌC. Đang có: $banned",
        )
        // Hai vai màu mép — của Pass-4 (surfEdge/surfOnEdge) VÀ của WP1 (glassSheen/glassShade) — không được mọc lại
        // dưới bất kỳ tên nào: đây là chỗ ba lượt trước đều đi qua trước khi chạm tới chỗ vẽ.
        val roles = KachiPalette::class.java.declaredFields.map { it.name }
        assertEquals(
            emptyList<String>(), listOf("surfEdge", "surfOnEdge", "glassSheen", "glassShade").filter { it in roles },
            "vai màu MÉP mọc lại ở KachiPalette — bề mặt WP1 không có mép nào, nên một vai như thế là vai chết (và " +
                "là lời mời vẽ lại gạch)",
        )
    }

    /**
     * Và vì mép sáng đã đi (Pass-5 nét đỉnh, WP1 bevel 1px), **chiều nổi nằm HẾT trong chuyển sắc** ⇒ chuyển sắc
     * phải còn đủ mạnh để đọc ra.
     *
     * Con số đo được ghi thẳng vào bảng §6.4 (dòng `surfFrom ÷ surfTo`). Bảng SÁNG được miễn vì `#ffffff` ÷
     * `#eff3f9` chỉ ~1.06×: đỉnh của nó đã trắng hết cỡ nên **không nâng thêm được**, và ở đó việc tách thẻ đi qua
     * chân viền của bài `the chat lieu tach duoc khoi nen o ca hai bang` — chân đó sau WP1 không còn được
     * [KachiTheme.surface] vẽ, xem KDoc của chính bài kia (đã ghi rõ, không giấu).
     */
    @Test
    fun `chuyen sac doc con du manh de thay the mep sang o bang toi`() {
        val p = KachiPalette.DARK
        val step = ratio(over(p.surfFrom, p.bg), over(p.surfTo, p.bg))
        assertTrue(
            step >= 1.20,
            "chuyển sắc thẻ bảng TỐI chỉ ${fmt(step)}× (cần ≥ 1.20): sau khi Pass-5 gỡ nét đỉnh và WP1 gỡ mép kính " +
                "+ viền, đây là TOÀN BỘ chiều nổi của thẻ — làm phẳng nó là trả màn hình về đúng mảng xám của P1.",
        )
    }

    /**
     * Sắc lĩnh vực phải vượt ngưỡng **nhìn ra được**, không chỉ khác `CLEAR`.
     *
     * [ĐO] bản P1 dùng 7 % (tối) / 4.7 % (sáng) ⇒ bước sáng so với thẻ trơ chỉ **1.07–1.15×**, tức là bằng hoặc
     * dưới chính cái ngưỡng 1.15× mà bài `the chat lieu tach duoc khoi nen` dùng để nói *"mắt đọc ra được"*. Một
     * sắc tồn tại trong bảng màu mà không tồn tại trên màn thì nó chỉ là vài byte APK.
     *
     * Sàn 1.10× (không phải 1.15×) vì tint là lớp **thứ hai** chồng lên một bề mặt đã có bậc và có mép sáng: nó
     * chỉ cần đủ để trả lời *"ô nào là Khí hậu"* trong một lưới, không cần tự mình tách một mặt phẳng.
     */
    @Test
    fun `sac linh vuc dat nguong nhin ra duoc`() {
        val bad = mutableListOf<String>()
        forEachPalette { name, p ->
            val plain = over(p.surfFrom, p.bg)
            TINT_DOMAINS.forEach { d ->
                val step = ratio(over(p.domainTint(d), plain), plain)
                if (step < 1.10) bad += "$name $d = ${fmt(step)}×"
            }
        }
        assertEquals(emptyList<String>(), bad, "sắc lĩnh vực dưới ngưỡng nhìn ra được (cần ≥ 1.10×): $bad")
    }

    /**
     * **Nguồn sáng của cả hệ ở TRÊN** ⇒ đỉnh chuyển sắc không bao giờ được TỐI hơn đáy.
     *
     * [SOÁT Pass 5] Bài cũ (`mep sang khong bao gio toi hon dinh gradient`) canh đúng tính chất này nhưng canh
     * trên lớp mép sáng — lớp đó đã bị gỡ. Tính chất thì **không** mất theo: nó chuyển xuống chính cặp
     * `surfFrom`/`surfTo` (và `surfOnFrom`/`surfOnTo`), nơi bây giờ chiều nổi thật sự nằm. Đảo chiều cặp này là
     * đổ bóng ngược, và cả màn hình đọc sai nổi/chìm.
     *
     * ⚠ Bảng SÁNG cũng phải đạt: `#ffffff` ≥ `#eff3f9`. Không có miễn trừ ở đây vì đây là **chiều**, không phải
     * **cường độ** — miễn trừ của bảng sáng ở các bài trên là về cường độ.
     *
     * ## ⚠ Cặp BẬT (`surfOnFrom`/`surfOnTo`) **không** nằm trong bài này — [ĐO], không phải bỏ sót
     * Hai vai đó bán trong suốt và mang **mật độ sắc nhấn**, không mang ánh sáng: bảng TỐI đo ra đỉnh sáng hơn
     * đáy (alpha 60 % → 35 % của một xanh sáng hơn nền), còn bảng SÁNG đo ra **ngược lại** (alpha 30 % → 20 % của
     * một xanh TỐI hơn nền trắng ⇒ đỉnh đậm hơn). Ép cùng một chiều cho cả hai là ép đổi mã màu của trạng thái
     * BẬT, việc đó có bài riêng canh (`chu tren the dang bat…`, `the dang bat khac the thuong…`) và không phải
     * phạm vi của Pass 5.
     */
    @Test
    fun `dinh chuyen sac khong bao gio toi hon day`() {
        forEachPalette { name, p ->
            val hi = over(p.surfFrom, p.bg)
            val lo = over(p.surfTo, p.bg)
            assertTrue(
                luminance(hi) >= luminance(lo),
                "$name: surfFrom TỐI hơn surfTo ⇒ chuyển sắc đổ ngược chiều sáng (nguồn sáng của cả hệ ở TRÊN)",
            )
        }
    }

    /**
     * Sắc lĩnh vực **không được** đẩy mực nào xuống dưới sàn, ở **cả hai** đầu gradient.
     *
     * [ĐO] đây là chỗ bản sáng suýt hỏng: ở 7 % (đúng mức của bản tối) thì `mut2`/`accentInk`/`slate` tụt xuống
     * 4.25–4.35:1. Chiều tác dụng của tint **ngược nhau** giữa hai bảng — trên nền tối nó làm sáng lên, trên thẻ
     * trắng nó làm tối đi và ăn thẳng vào tương phản của chữ. Vì thế bản sáng dùng 4.7 %.
     */
    @Test
    fun `sac linh vuc khong lam tut tuong phan o ca hai bang`() {
        val bad = mutableListOf<String>()
        forEachPalette { name, p ->
            TINT_DOMAINS.forEach { d ->
                val tint = p.domainTint(d)
                listOf("surfFrom", "surfTo").forEach { s ->
                    val ground = over(tint, over(role(p, s), p.bg))
                    ALL_INKS.forEach { ink ->
                        val r = ratio(role(p, ink), ground)
                        if (r < 4.5) bad += "$name $ink trên $s+$d = ${fmt(r)}"
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), bad, "sắc lĩnh vực đẩy mực xuống dưới 4.5:1: $bad")
    }

    /** Sắc lĩnh vực phải **nhìn ra được** — không thì nó chỉ là vài byte APK và một lời hứa suông. */
    @Test
    fun `sac linh vuc nhin ra duoc va khong trung nhau`() {
        forEachPalette { name, p ->
            val plain = over(p.surfFrom, p.bg)
            val seen = HashMap<String, String>()
            TINT_DOMAINS.forEach { d ->
                val tinted = over(p.domainTint(d), plain)
                assertTrue(
                    tinted != plain,
                    "$name: lĩnh vực $d không có sắc riêng — tra ra CLEAR nghĩa là thiếu một dòng ở domainTints",
                )
                val dup = seen.put(tinted, d)
                assertTrue(dup == null, "$name: $d và $dup cho ra CÙNG một nền ⇒ mắt không phân biệt được")
            }
        }
        assertEquals(
            TINT_DOMAINS.sorted(), KachiPalette.DARK.domainTints.keys.sorted(),
            "bảng sắc lĩnh vực phải phủ đúng danh sách Domain đang dùng",
        )
        assertEquals(
            KachiPalette.DARK.domainTints.keys.sorted(), KachiPalette.LIGHT.domainTints.keys.sorted(),
            "hai bảng phải phủ CÙNG tập lĩnh vực — thiếu một khoá ở một bảng là một vùng mất sắc ở đúng chủ đề đó",
        )
    }

    /**
     * ═══ P1b · thẻ TRÊN ẢNH NỀN — đo trên **nền tệ nhất có thể**, không đo trên một ảnh cụ thể ═══════════════
     *
     * Owner 2026-09-16 (kèm ảnh chụp trên xe): *"cái màu đen, xám của mình, khi nhét thêm hình nền vào, nó lại
     * không đẹp nữa"*. Hai vai `surf*OverArt` là bước chuẩn bị cho P1b (spec §4.10).
     *
     * ## Vì sao đo trên trắng tinh VÀ đen tuyền
     * Ảnh nền do người dùng chọn ⇒ launcher **không biết** cái gì nằm dưới thẻ. Cùng một lẽ đã ghi cho
     * [KachiPalette.scrimBtn] (nút ⇄ nằm trên pixel của app đang chiếu): vai nào nằm trên nội dung không biết
     * trước thì nó phải **một mình** bảo đảm đọc được ở hai cực. Đo trên một tấm ảnh mẫu là đo một ca may mắn.
     *
     * ## Bài này KHÔNG đòi [KachiPalette.mut] đạt sàn — và đó là chủ ý, có ghi
     * [ĐO] ở 80 %: `mut` chỉ còn 3.15–3.91:1. Nâng alpha lên để `mut` đạt 4.5 thì thẻ gần như đục lại, tức là
     * quay về đúng cái *"miếng vá"* owner chê. Lời giải đúng nằm ở P1b (lớp che 35–50 % + chọn mực theo độ chói
     * đo được của vùng ảnh dưới thẻ), không nằm ở con số alpha. Bài khoá cái **đã đúng** ([ink]) và để lại dấu
     * cho cái **chưa đủ** — thay vì im lặng hoặc hạ sàn.
     */
    @Test
    fun `the tren anh nen giu duoc chu chinh o hai cuc`() {
        val bad = mutableListOf<String>()
        forEachPalette { name, p ->
            listOf("surfFromOverArt", "surfToOverArt").forEach { s ->
                listOf("#ffffff" to "ảnh sáng trắng", "#000000" to "ảnh tối đen").forEach { (art, why) ->
                    val ground = over(role(p, s), art)
                    val r = ratio(p.ink, ground)
                    if (r < 4.5) bad += "$name ink trên $s ($why) = ${fmt(r)}"
                }
            }
        }
        assertEquals(emptyList<String>(), bad, "chữ chính trên thẻ-trên-ảnh dưới 4.5:1 ở một trong hai cực: $bad")
    }

    /** Và hai vai đó phải thật sự **bán trong suốt** — đặc thì cả ý tưởng "cửa sổ nhìn xuống ảnh" mất sạch. */
    @Test
    fun `vai the tren anh nen phai ban trong suot`() {
        forEachPalette { name, p ->
            listOf("surfFromOverArt", "surfToOverArt").forEach { s ->
                val a = role(p, s).removePrefix("#").take(2).toInt(16)
                assertTrue(
                    a in 0xB3..0xE6,
                    "$name.$s có alpha ${"%02x".format(a)} — cần trong khoảng B3..E6 (70–90 %): đục hơn thì ảnh " +
                        "không lọt qua (thẻ lại thành miếng vá), trong hơn thì chữ chính tụt dưới sàn.",
                )
            }
        }
    }

    /**
     * Sinh **bằng máy** bảng đo §6.4 của spec và ghi ra `docs/diagnostics/` (AC6.6).
     *
     * Vì sao là một `@Test` chứ không phải một script rời: script phải có người nhớ chạy, còn bài canh chạy mỗi
     * lượt build. Chép tay bảng này là cách chắc chắn nhất để tài liệu và bảng màu lệch nhau — đúng bệnh mà
     * `documentation-and-backlog.md` R2.1 (*code + doc atomic*) sinh ra để chặn.
     */
    @Test
    fun `sinh bang do tuong phan cua tai lieu`() {
        val out = StringBuilder()
        out.append("# Bảng đo tương phản — VISUAL-REFRESH P1 (§6.4)\n\n")
        out.append("> SINH BẰNG MÁY từ `KachiPalette` bởi `SurfaceContrastContractTest.sinh bang do tuong phan cua tai lieu`.\n")
        out.append("> **Không sửa tay** — sửa bảng màu rồi chạy lại `:app:testDebugUnitTest`.\n\n")
        forEachPalette { name, p ->
            out.append("## Bảng $name\n\n| Cặp | Vai | Sàn | Đo được | Kết |\n|---|---|---|---|---|\n")
            fun row(pair: String, role: String, floor: Double, v: Double) =
                out.append("| `$pair` | $role | $floor | **${fmt(v)}** | ${if (v >= floor) "✅" else "❌"} |\n")
            val top = over(p.surfFrom, p.bg)
            val bot = over(p.surfTo, p.bg)
            val on = over(p.surfOnFrom, p.bg)
            val on2 = over(p.surfOnTo, p.bg)
            val sunk = over(p.fieldSunken, p.bg)
            row("INK trên surfFrom", "chữ chính, đỉnh gradient", 4.5, ratio(p.ink, top))
            row("INK trên surfTo", "chữ chính, đáy gradient", 4.5, ratio(p.ink, bot))
            row("MUT trên surfFrom", "nhãn phụ, đỉnh", 4.5, ratio(p.mut, top))
            row("MUT trên surfTo", "nhãn phụ, đáy", 4.5, ratio(p.mut, bot))
            row("MUT2 trên surfTo", "mực mờ nhất, đáy", 4.5, ratio(p.mut2, bot))
            row("INK trên fieldSunken", "chữ trong ô lõm", 4.5, ratio(p.ink, sunk))
            row("INK trên surfOnFrom", "chữ trên thẻ BẬT, đỉnh", 4.5, ratio(p.ink, on))
            row("INK trên surfOnTo", "chữ trên thẻ BẬT, đáy", 4.5, ratio(p.ink, on2))
            // ⚠ Hai dòng dưới đo MỘT tính chất ("thẻ tách được khỏi nền") bằng HAI cơ chế, và bảng phải nói đúng
            // như thế: chấm ❌ cho chân viền của bảng TỐI là sai sự thật — ở bảng tối việc tách thẻ do bước sáng
            // gánh (1.23×). ⚠⚠ [WP1 2026-09-20] `surfLine` KHÔNG còn được `KachiTheme.surface()` vẽ (owner bỏ hết
            // viền) ⇒ với bảng SÁNG, chân đang đỡ con số này là một cơ chế màn hình không dùng nữa. Giữ dòng để
            // con số vẫn hiện ra cho owner quyết, KHÔNG im lặng bỏ. Xem `the chat lieu tach duoc khoi nen…`.
            val border = ratio(over(p.surfLine, top), top)
            val step = ratio(top, p.bg)
            out.append(
                "| `surfLine trên surfFrom` + `surfFrom ÷ bg` | tách thẻ khỏi nền (**viền ≥ 3.0 HOẶC bước ≥ 1.15**) " +
                    "— ⚠ WP1: viền KHÔNG còn được vẽ, chỉ còn bước sáng trên màn " +
                    "| 3.0 / 1.15 | viền **${fmt(border)}** · bước **${fmt(step)}** | " +
                    "${if (border >= 3.0 || step >= 1.15) "✅" else "❌"} |\n",
            )
            row("surfOnFrom ÷ surfFrom", "bước sáng BẬT↔thường", 1.20, ratio(on, top))
            // ── [SOÁT Pass 4] Ba bậc của màn chính + sức mạnh của mép sáng: đây là những con số nói *"nhìn ra
            //    được hay không"*, tách khỏi những con số nói *"đọc được hay không"* ở trên.
            val wellTop = over(p.slot, p.bg)
            val wellStep = ratio(wellTop, p.bg)
            val wellBorder = ratio(over(p.lineStrong, wellTop), wellTop)
            out.append(
                "| `slot ÷ bg` + `lineStrong trên slot` | bậc 1 · khay ô làm việc trên nền màn " +
                    "(**bước ≥ 1.12 HOẶC viền ≥ 3.0**) | 1.12 / 3.0 | bước **${fmt(wellStep)}** · viền " +
                    "**${fmt(wellBorder)}** | ${if (wellStep >= 1.12 || wellBorder >= 3.0) "✅" else "❌"} |\n",
            )
            row("surfFrom ÷ slot", "bậc 2 · thẻ nội dung trên khay", 1.15, ratio(over(p.surfFrom, wellTop), wellTop))
            // ⚠ [SOÁT Pass 5] Dòng `surfEdge trên surfFrom` (mép sáng, sàn 2.20) ĐÃ BỎ cùng với chính lớp mép
            //    sáng; WP1 bỏ tiếp cặp `glassSheen`/`glassShade`. Thay bằng chuyển sắc — nay đó là toàn bộ chiều
            //    nổi của thẻ. Bảng SÁNG miễn (đỉnh đã trắng tinh, không nâng thêm được).
            val slope = ratio(top, bot)
            // ⚠ [SOÁT 1.69 · P3] Miễn trừ đo bằng **ĐỘ SÁNG**, không so mã màu. Bản trước viết
            // `p.surfFrom.equals("#ffffff")`: lý do miễn là *"đỉnh đã sáng hết cỡ nên không nâng thêm được"*,
            // mà một mã màu thì không nói ra được lý do ấy — đổi đỉnh bảng sáng thành `#fefefe` là miễn trừ
            // **bốc hơi im lặng** và bảng này đỏ ở một dòng chẳng ai hiểu vì sao. Đo thì nó tự đúng.
            val slopeOk = slope >= 1.20 || luminance(top) >= 0.95
            out.append(
                "| `surfFrom ÷ surfTo` | chuyển sắc DỌC = toàn bộ chiều nổi (bảng SÁNG miễn: đỉnh đã trắng) " +
                    "| 1.20 | **${fmt(slope)}** | ${if (slopeOk) "✅" else "❌"} |\n",
            )
            row("INK trên slot", "chữ ô nhóm, đỉnh khay", 4.5, ratio(p.ink, wellTop))
            row("MUT2 trên slotTo", "nhãn nhóm mờ nhất, đáy khay", 4.5, ratio(p.mut2, over(p.slotTo, p.bg)))
            row("lineStrong trên bg", "mốc cũ phải giữ", 3.0, ratio(over(p.lineStrong, p.bg), p.bg))
            // WP1 · R1.1 — hàng `emptyLine trên emptyFill` đã ĐỔI CHÂN: gạch đứt ô trống bị gỡ (và vai `emptyLine`
            // xoá khỏi bảng màu), nên thứ phải đo nay là **bước sáng của NỀN ô trống** — xem
            // `ThemePaletteContractTest.o trong tach duoc khoi nen bang mau`.
            row("emptyFill ÷ bg", "ô trống tách nền bằng MÀU (gạch đứt đã gỡ)", 1.15, ratio(p.emptyFill, p.bg))
            row("ON_ACCENT trên gradFrom", "mốc cũ phải giữ", 4.5, ratio(p.onAccent, p.gradFrom))
            // P1b — thẻ trên ẢNH NỀN: đo ở hai cực vì ảnh do người dùng chọn (xem bài `the tren anh nen...`).
            listOf("#ffffff" to "ảnh sáng", "#000000" to "ảnh tối").forEach { (art, why) ->
                row("INK trên surfFromOverArt ($why)", "P1b · chữ chính trên ảnh", 4.5, ratio(p.ink, over(p.surfFromOverArt, art)))
                row("MUT trên surfFromOverArt ($why)", "P1b · nhãn phụ — CẦN scrim ở P1b", 4.5, ratio(p.mut, over(p.surfFromOverArt, art)))
            }
            out.append("\n### Sắc lĩnh vực $name — mực TỆ NHẤT trên thẻ đã tint\n\n")
            out.append("| Lĩnh vực | Mã tint | Nền đỉnh | Nền đáy | Mực tệ nhất | Bước sáng |\n|---|---|---|---|---|---|\n")
            TINT_DOMAINS.forEach { d ->
                val t = p.domainTint(d)
                val tf = over(t, top)
                val tt = over(t, bot)
                val worst = ALL_INKS.minOf { minOf(ratio(role(p, it), tf), ratio(role(p, it), tt)) }
                out.append("| $d | `$t` | `$tf` | `$tt` | **${fmt(worst)}** | ${fmt(ratio(tf, top))}× |\n")
            }
            out.append("\n")
        }
        // Gốc kho tìm bằng cách đi NGƯỢC từ tệp bảng màu cho tới thư mục có `docs/` — không giả định working
        // directory của Gradle (bài học `SourceRoots`: Gradle đặt cwd theo module, nên mọi đường dẫn tương đối
        // "chắc chắn đúng" đều đã sai ít nhất một lần).
        val root = generateSequence(
            SourceRoots.path("src/main/java/com/byd/clusternav/launcher/KachiPalette.kt").toAbsolutePath().toFile(),
        ) { it.parentFile }.firstOrNull { java.io.File(it, "docs/diagnostics").isDirectory }
            ?: error("không tìm thấy gốc kho (thư mục chứa docs/diagnostics)")
        val target = java.io.File(root, "docs/diagnostics/visual-refresh-2026-09-16/contrast-table.md")
        target.parentFile?.mkdirs()
        target.writeText(out.toString())
        assertTrue(target.length() > 0, "không ghi được bảng đo — kiểm quyền ghi vào docs/diagnostics/")
    }

    private fun forEachPalette(block: (String, KachiPalette) -> Unit) {
        block("TỐI", KachiPalette.DARK); block("SÁNG", KachiPalette.LIGHT)
    }

    private fun role(p: KachiPalette, name: String): String =
        KachiPalette::class.java.getDeclaredField(name).apply { isAccessible = true }.get(p) as String

    private companion object {
        /** Vai MỰC được đo — giữ đồng bộ với `ThemePaletteContractTest.ALL_INKS`. */
        val ALL_INKS = listOf("ink", "ink2", "mut", "mut2", "icon", "accentInk", "green", "amber", "red", "cyan", "orange", "slate")

        /** Tám lĩnh vực có sắc riêng — khoá của [KachiPalette.domainTints]. Thứ tự = thứ tự khai của `Domain`. */
        val TINT_DOMAINS = listOf("ENERGY", "DRIVETRAIN", "CLIMATE", "TYRES", "BODY", "LIGHTS", "IDENTITY", "INFOTAINMENT")
    }
}
