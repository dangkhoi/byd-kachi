package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI CANH THANG CỠ CHỮ (type scale) ══════════════════════════════════════════════════════════════════════
 *
 * Quét **mã nguồn** các bề mặt Settings đã áp design system và **đỏ** khi có `setTextSize(…COMPLEX_UNIT_SP, <số>)`
 * viết tại chỗ thay vì đi qua [KachiType]. Spec `docs/specs/kachi-design-system.html` (R1/R5).
 *
 * ## ⚠⚠ Bài này quét MÃ NGUỒN ⇒ phải ở đúng module `:app` (nơi sở hữu mã nó quét)
 * Cùng họ lỗi đã bắt hai lần: bài đặt ở `:core` mà quét mã `:app` ⇒ `:core:test` báo UP-TO-DATE ngay ca nó sinh
 * ra để bắt (S1); và bài đúng module mà gradle không biết đầu vào cũng UP-TO-DATE (G1). `app/build.gradle.kts` đã
 * khai `inputs.dir("src/main/java")` cho tác vụ test nên đổi nội dung tệp là test chạy lại — cùng cơ chế
 * [SpacingScaleContractTest].
 *
 * ## Phạm vi — vì sao chỉ Settings
 * Design system đang áp **Settings trước** (spec §R7). Ngăn kéo / thanh nút / widget còn dựng cỡ chữ tay ở 14 tệp
 * là **pha sau** (OQ1) — đưa chúng vào bài canh bây giờ thì đỏ hàng loạt cho việc chưa tới lượt. Khi lan tới đâu,
 * thêm tệp vào [SURFACES] tới đó.
 *
 * ## KHÔNG thuộc phạm vi
 * Ô vẽ Canvas ([TyreBoardView]/[DoorBoardView]) tính cỡ chữ theo **tỉ lệ cạnh ô** (`m * 0.xxx`) — đó là kích
 * thước hình học bất biến với cỡ ô, không phải một bậc chữ giao diện; chúng không dùng `setTextSize(sp)` số tay.
 */
class TypeScaleContractTest {

    /**
     * Bề mặt đã áp KachiType. `SettingsRows` là NGUỒN component (dùng `KachiType.apply` nội bộ).
     *
     * Mở rộng dần theo §R7: sau Settings, đã lan sang ngăn kéo / thanh trên / overlay / ô workspace / bảng vẽ.
     * Ô lưới mật độ cao (`CapabilityGridSection` 11.5/10sp — tệp đã xoá ở R-UI (m)) và ô vẽ Canvas
     * ([TyreBoardView]/[DoorBoardView] theo tỉ
     * lệ) KHÔNG ở đây — cỡ của chúng là ngoại lệ có lý do (mật độ / hình học), KHÔNG phải bậc chữ giao diện.
     */
    private val SURFACES = listOf(
        "SettingsSections.kt", "SettingsSectionsHome.kt",
        "SettingsPanel.kt", "TopStripPicker.kt", "SettingsRows.kt",
        "AppDrawer.kt", "KachiTopStrip.kt", "OverlayHeads.kt", "WorkspaceView.kt", "LayoutEditorPanel.kt",
        // [SOÁT Pass 1 · 2026-09-16] Năm hàm dựng thẻ trong ô tách khỏi `WorkspaceView.kt` (trần 500 dòng).
        // Vào bài canh NGAY, cùng lý do `AppDrawerApps.kt`: một bề mặt đã áp design system không được rơi
        // ra khỏi phạm vi chỉ vì đổi tên tệp — và glyph `＋` được miễn nằm đúng trong tệp mới này.
        "WorkspaceViewCards.kt",
        // T6 tách phần danh sách app ra khỏi `AppDrawer` (trần 500 dòng). Tệp mới vẽ chữ ⇒ phải ở trong bài canh
        // NGAY, không thì một bề mặt đã áp design system tự rơi ra khỏi phạm vi chỉ vì đổi tên tệp.
        "AppDrawerApps.kt",
        // L6-debt 2026-09-27: ô lưới (widget · khả năng · huy hiệu loại) tách khỏi `AppDrawer` (trần 500 dòng) — hai
        // `setTextSize` mang marker `[type scale]` nay ở đây; bề mặt đã áp design system không được rơi khỏi phạm vi.
        "AppDrawerTiles.kt",
        // T4 · IA v2 — năm section mới của Cài đặt. Cùng lý do `AppDrawerApps.kt`: chúng là bề mặt Settings, tức
        // phạm vi GỐC của bài này; để ngoài thì một section mới có thể `setTextSize` số tay mà không ai thấy.
        "SettingsSectionsBars.kt", "SettingsSectionsNav.kt", "SettingsSectionsCast.kt",
        "SettingsSectionsKeys.kt", "SettingsSectionsCar.kt",
        // 2.76 · R1 — camera tách khỏi `SettingsSectionsCar` (hai tầng người lái / kỹ thuật). Cùng lý do trên.
        "SettingsSectionsCamera.kt",
        // V-CLUSTER (2026-09-30) — khối khung/DPI tách khỏi `SettingsSectionsCast` (trần 500 dòng). Cùng lý do trên.
        "SettingsSectionsCastGeometry.kt",
    )

    /**
     * NGOẠI LỆ tường minh: một dòng mang marker `[type scale]` được miễn — dùng cho cỡ KHÔNG thuộc 5 bậc mà có lý
     * do tại chỗ (glyph trang trí cỡ lớn như `＋`, nhãn ô lưới mật độ cao). Cùng khuôn `allowedRaw` của
     * [SpacingScaleContractTest] / `SAME_ON_PURPOSE` của i18n: ngoại lệ phải NÓI lý do ngay tại dòng, không im lặng.
     */
    private val EXEMPT_MARKER = "[type scale]"

    /**
     * Marker **một mình** không đủ — phải có LÝ DO ngay sau nó (tối thiểu bấy nhiêu ký tự).
     *
     * ⚠⚠ [SOÁT ĐỘC LẬP 2026-09-12 · pha 2] Bản đầu của cửa miễn chỉ so chuỗi: `line.contains(EXEMPT_MARKER)`.
     * [ĐO] thử phá: chèn `textSize = 99f   // [type scale]` (marker TRẦN, không một chữ lý do) vào
     * `OverlayHeads.kt` ⇒ **BUILD SUCCESSFUL, 0 đỏ**. Tức cửa miễn thành một **câu thần chú** dán vào là im bài —
     * đúng cái mà `allowedRaw` của [SpacingScaleContractTest] (`allowedRaw[key].isNullOrBlank()` ⇒ trị RỖNG không
     * được tính là ngoại lệ) và `SAME_ON_PURPOSE` của i18n (có hẳn một bài đòi `why.isNotBlank()`) đã chặn từ
     * trước. Áp lại đúng khuôn ĐÃ CÓ, không phát minh khuôn mới.
     */
    private val EXEMPT_REASON_MIN = 12

    /**
     * Số dòng ĐANG được miễn — ghim lại, cùng lẽ với `SAME_ON_PURPOSE` (danh sách ngoại lệ phải là dữ liệu THẤY
     * ĐƯỢC, không phải thứ mọc thêm lặng lẽ).
     *
     * 3 = 2 nhãn ô lưới mật độ cao (`AppDrawerTiles` 11.5/10sp) + 1 huy hiệu loại (`AppDrawerTiles` 8.5sp). Thêm
     * một ngoại lệ thì phải sửa con số này ⇒ nó hiện ra trong diff và người review phải đồng ý, thay vì một
     * dòng comment lọt qua.
     *
     * Lịch sử: 4 → **3** ở FIX286 · ES7 (owner 03/10) — glyph trang trí `＋` 32sp của ô trống (`WorkspaceViewCards.emptyAdd`)
     * GỠ cùng hàm của nó vì khung trống nay trong suốt, chỉ còn nút ⇄. Hạ con số là siết, không nới: một ngoại lệ
     * mới chen vào chỗ trống ấy nay sẽ đỏ.
     */
    private val EXEMPT_LINES = 3

    /**
     * BA cách đặt cỡ chữ bằng số TAY — phải chặn **cả ba**, không chỉ cách hay gặp nhất.
     *
     * ⚠⚠ [SOÁT ĐỘC LẬP 2026-09-12] Bản đầu của bài này chỉ có [rawTwoArg]. [ĐO] thử phá: chèn
     * `textSize = 99f` **và** `setTextSize(88f)` vào `SettingsSections.kt` ⇒ **BUILD SUCCESSFUL, 0 đỏ** — tức bài
     * canh bỏ qua đúng hai trong ba cách viết ra cùng một lỗi. `TextView.setTextSize(Float)` một-tham-số dùng
     * **COMPLEX_UNIT_SP** theo tài liệu Android, và `textSize = …` của Kotlin biên dịch về đúng hàm đó ⇒ cả hai là
     * cỡ chữ SP viết tay, chỉ khác chính tả.
     *
     * Đây **cùng một họ lỗi** với `px()` lách `SpacingScaleContractTest` (bài đó chỉ soi `dp(`/`dpi(` nên một hàm
     * đổi dp mang tên khác đi qua sạch). Luật đã rút ra khi đó: chặn **NGUYÊN NHÂN** (đặt cỡ chữ bằng số) chứ không
     * chặn **HIỆN TƯỢNG** (một chính tả cụ thể).
     */
    private val rawTwoArg = Regex("""setTextSize\s*\(\s*[\w.]*COMPLEX_UNIT_\w+\s*,\s*\d""")
    private val rawOneArg = Regex("""setTextSize\s*\(\s*\d""")
    private val rawProperty = Regex("""(?<![\w])textSize\s*=\s*\d""")
    private val handWritten = listOf(rawTwoArg, rawOneArg, rawProperty)

    /**
     * Dòng MÃ của một bề mặt kèm dòng THÔ cùng số. Mã = đã **bỏ MỌI chú thích nhưng GIỮ số dòng** bằng bộ quét có trạng
     * thái dùng chung ([KotlinSource.stripComments] `keepLines`) — để không bắt con số trong KDoc mà vẫn báo đúng số dòng
     * của tệp gốc. Dòng thô để đọc marker [EXEMPT_MARKER]: marker là chú thích DÒNG đứng cuối chính dòng mã.
     *
     * 2.93 wave 2C · TEST-STRIP-COPIES — bản cũ xoá chú thích khối bằng regex `.*?` rồi cắt `//` tay ở mỗi dòng: một chuỗi
     * chứa `//` cắt mất phần mã phía sau, một chuỗi chứa cặp mở-khối nuốt mã tới dấu đóng thật (FAIL-OPEN — KDoc
     * [KotlinSource]). Marker nay đọc từ dòng THÔ (bản cũ: dòng đã xoá khối) ⇒ marker đặt trong một chú thích khối trên CÙNG
     * dòng mã cũng được nhận — quy ước repo luôn đặt nó ở chú thích dòng (`AppDrawerTiles`), không dòng nào đổi kết quả.
     */
    private fun codeLines(file: String): List<Pair<String, String>> {
        val raw = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$file")
        return KotlinSource.stripComments(raw, keepLines = true).lines().zip(raw.lines())
    }

    /** Lý do khai sau [EXEMPT_MARKER] trên chính dòng đó; `null` = dòng không khai ngoại lệ. */
    private fun exemptReason(line: String): String? {
        val at = line.indexOf(EXEMPT_MARKER)
        return if (at < 0) null else line.substring(at + EXEMPT_MARKER.length).trim()
    }

    /** Ngoại lệ HỢP LỆ = marker + lý do đủ dài. Marker trần KHÔNG miễn được gì. */
    private fun exemptOk(line: String): Boolean = (exemptReason(line)?.length ?: -1) >= EXEMPT_REASON_MIN

    /** Dòng MÃ (đã bỏ chú thích — [codeLines]) có đặt cỡ chữ bằng số tay. */
    private fun handWrites(code: String): Boolean = handWritten.any { it.containsMatchIn(code) }

    @Test
    fun `Settings khong dat co chu bang so tay - phai qua KachiType`() {
        val offenders = mutableListOf<String>()
        SURFACES.forEach { f ->
            val lines = codeLines(f)
            lines.forEachIndexed { i, (code, rawLine) ->
                val raw = handWrites(code)
                // Ngoại lệ tường minh (glyph/ô lưới): dòng tự khai lý do bằng marker. Marker TRẦN không miễn —
                // nếu không thì cửa miễn chỉ là một câu thần chú dán vào để bài canh im (xem [EXEMPT_REASON_MIN]).
                if (exemptReason(rawLine) != null) {
                    if (raw && !exemptOk(rawLine)) {
                        offenders += "$f:${i + 1}: marker `$EXEMPT_MARKER` KHÔNG kèm lý do (cần ≥ " +
                            "$EXEMPT_REASON_MIN ký tự): ${rawLine.trim()}"
                    }
                    return@forEachIndexed
                }
                if (raw) offenders += "$f:${i + 1}:${rawLine.trim()}"
            }
            // Lời gọi VẮT NHIỀU DÒNG (`setTextSize(\n  TypedValue.COMPLEX_UNIT_SP,\n  14f)`) không hiện trên một
            // dòng nào ⇒ quét thêm cả tệp một lượt. Không có bước này thì chỉ cần bấm Enter là lách được bài canh.
            // Chỉ bỏ ra những dòng miễn HỢP LỆ: dòng mang marker trần vẫn phải đi qua phép quét này.
            val flat = lines.filterNot { exemptOk(it.second) }.joinToString("\n") { it.first }
            if (handWritten.any { it.containsMatchIn(flat) } && offenders.none { it.startsWith("$f:") }) {
                offenders += "$f: (lời gọi vắt nhiều dòng)"
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Cỡ chữ ở bề mặt đã áp design system phải đi qua KachiType (thang cỡ chữ), KHÔNG đặt bằng số tay " +
                "(cả `setTextSize(UNIT, n)`, `setTextSize(n)` lẫn `textSize = n`); ngoại lệ phải khai marker " +
                "`$EXEMPT_MARKER` kèm lý do tại dòng:\n" + offenders.joinToString("\n"),
        )
    }

    /**
     * Số dòng miễn trừ bị **GHIM** — mở thêm một cửa miễn phải là quyết định thấy được trong diff.
     *
     * Cùng lẽ với bài `moi muc trong danh sach cho phep trung deu co ly do, va deu dung toi` của i18n: một danh
     * sách ngoại lệ không ai đếm sẽ lớn dần cho tới lúc bài canh chỉ còn canh những chỗ không ai định sửa.
     */
    @Test
    fun `so dong duoc mien tru bi ghim`() {
        val exempt = SURFACES.flatMap { f ->
            codeLines(f).mapIndexedNotNull { i, (code, rawLine) ->
                if (handWrites(code) && exemptReason(rawLine) != null) "$f:${i + 1}" else null
            }
        }
        assertEquals(
            EXEMPT_LINES, exempt.size,
            "số dòng miễn thang cỡ chữ đổi — nếu là ngoại lệ CHÍNH ĐÁNG thì sửa EXEMPT_LINES (và nói lý do ở KDoc " +
                "của nó); nếu không thì chuyển dòng đó về KachiType:\n" + exempt.joinToString("\n"),
        )
    }

    /**
     * Type scale phải giữ **ĐÚNG** 5 bậc — thêm bậc tuỳ tiện là quay lại "18 cỡ chữ" mà nó sinh ra để chống.
     *
     * ⚠⚠ [SOÁT ĐỘC LẬP 2026-09-12] Bản đầu đếm `const val (DISPLAY|TITLE|SECTION|BODY|CAPTION)` rồi đòi `== 5` —
     * tức nó chỉ kiểm *"5 bậc đã biết còn đủ"*, KHÔNG kiểm *"không có bậc thứ sáu"*. [ĐO] thử phá: thêm
     * `const val MICRO = 9.5f` ⇒ **BUILD SUCCESSFUL, 0 đỏ**, đúng ca mà tên bài và KDoc của nó hứa sẽ bắt. Nay so
     * **TẬP** tên bậc đọc được với tập mong đợi ⇒ thêm, xoá hay đổi tên một bậc đều đỏ.
     */
    @Test
    fun `type scale co dung 5 bac`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiType.kt")
        val found = Regex("""const val (\w+)\s*=\s*[\d.]+f""").findAll(src).map { it.groupValues[1] }.toSet()
        val want = setOf("DISPLAY", "TITLE", "SECTION", "BODY", "CAPTION")
        assertTrue(
            found == want,
            "KachiType phải có ĐÚNG 5 bậc $want — thấy $found (thừa ${found - want}, thiếu ${want - found})",
        )
    }
}
