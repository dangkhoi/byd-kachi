package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ N2 — BỐN SECTION CLUSTERNAV CHỈ ĐƯỢC ĐI QUA **MỘT CẦU** ═════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` **N2**: *"với khoá ClusterNav, đi qua một cầu duy nhất
 * [ClusterNavBridge] bọc Prefs/NavConnect/coordinator — không rải `Prefs.set…` trong section"*.
 *
 * ## Vì sao luật này cần MÁY canh, không chỉ cần KDoc
 * Viết thẳng `Prefs.setBadgeEnabled(context, on)` trong một section **ngắn hơn** và **chạy đúng** — nó chỉ sai ở chỗ
 * không ai thấy: đường ghi thứ hai đó bỏ qua mọi tác dụng phụ mà cầu đang gánh (`speedSign.onBadgeEnabledChanged()`,
 * auto-start VietMap, `NavRepository.reapplyClusterMode`, kẹp `BadgeLayout.clampCenter`…). Kết quả là **prefs đổi mà
 * cụm không đổi** — hỏng im lặng, và chỉ lộ ra khi ngồi trong xe. Cùng họ với ba bản vá phải gỡ ở phiên 21/07
 * (CLAUDE.md §3): đường tắt đúng-về-mặt-trí-nhớ nhưng bỏ mất một bước thật.
 *
 * Và khi màn ClusterNav cũ bị gỡ (OQ1), cầu phải là **nguồn duy nhất** — mỗi lời gọi thẳng còn sót là một chỗ nữa
 * phải đi tìm lúc đó.
 *
 * ## Hai chiều bài này canh
 *  1. **CẤM** section chạm lớp lưu/điều phối của ClusterNav ([FORBIDDEN]);
 *  2. **ĐÒI** mỗi section thật sự có gọi `bridge.` (một section "sạch" vì nó rỗng thì không chứng minh được gì).
 */
class ClusterNavSettingsWiringContractTest {

    private fun code(name: String): String =
        SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    /** Bốn section dựng lại điều khiển của màn ClusterNav (IA v2 §4.1 nhóm 5–8). */
    private val sections = listOf(
        "SettingsSectionsNav.kt", "SettingsSectionsCast.kt", "SettingsSectionsKeys.kt", "SettingsSectionsCar.kt",
    )

    /**
     * Những thứ section **không được** chạm — mỗi mục kèm lý do, theo lệ `SettingsCatalog.NOT_SETTINGS`.
     *
     * Khoá theo **tiền tố lời gọi** chứ không theo tên `import`: một section có thể `import` để đọc hằng (xem
     * [ALLOWED] bên dưới) mà vẫn không ghi gì.
     */
    private val forbidden: Map<String, String> = mapOf(
        "Prefs.set" to "ghi thẳng prefs của ClusterNav ⇒ bỏ mọi tác dụng phụ mà cầu đang gánh (áp live, kẹp biên, " +
            "đánh thức lớp phủ) — prefs đổi mà cụm không đổi",
        "Prefs.add" to "cùng lý do Prefs.set: danh sách gán phím có bất biến một-mã-một-đích do cầu/`:core` giữ",
        "Prefs.remove" to "cùng lý do Prefs.set",
        "NavConnect." to "cấp quyền qua dadb là chuỗi nhiều bước có thứ tự (selfGrant → ensureConnected → " +
            "grantAccessibility); gọi lẻ một bước là cấp một nửa rồi im",
        "NavRepository." to "bật/tắt đầu ra cụm phải đi kèm `speedSign.onOutputEnabled` — cầu giữ cặp đó",
        "SimpleCastRuntime" to "coordinator là process-singleton có executor nối tiếp; lấy thẳng là mở đường thứ " +
            "hai vào cùng một hàng đợi lệnh shell",
        ".coordinator" to "cùng lý do SimpleCastRuntime",
        "VmOverlayPosition" to "ghi vị trí bong bóng phải kèm broadcast sang bản VietMap sửa đổi — cầu giữ cặp đó",
        "ThemeMode." to "chủ đề gương hai store ở tầng lưu bền (`PrefsWorkspaceRepository.persist`), không ở UI",
        "SeatComfortApplier" to "áp HAL cho ghế có hai đường (applySeat cho từng ghế / applyNow cho cả bộ) và " +
            "chọn nhầm đường thì không tắt được ghế — cầu đã chốt đường đúng",
        "Pm25FilterApplier" to "bật/tắt lọc-liên-tục và quick-clean là hai việc khác nhau; cầu giữ đúng cặp",
    )

    /**
     * NGOẠI LỆ tường minh — mỗi mục kèm lý do, không im lặng.
     *
     * Chỉ có MỘT, và nó là một thiếu sót ĐÃ BIẾT của cầu chứ không phải một lối thiết kế: cầu có
     * `recircOnStart`/`setRecircOnStart` (ghi khoá) nhưng **không** có đường "áp ngay" tương ứng, trong khi hành vi
     * chuyển từ `HomePanels` sang phải giữ nguyên (bật ⇒ áp NGAY, không chờ lần nổ máy sau). Ghi ra đây để lần sau
     * ai mở cầu ra sửa thì thấy — thay vì để nó là một lời gọi lạc trong một tệp không ai đọc lại.
     */
    private val allowed: Map<String, String> = emptyMap()   // 2026-09-13: applyRecircNow đã vào cầu — 0 ngoại lệ

    @Test
    fun `section ClusterNav khong cham thang lop luu cua ClusterNav`() {
        val offenders = mutableListOf<String>()
        sections.forEach { file ->
            val src = code(file)
            src.lines().forEachIndexed { i, line ->
                forbidden.keys.filter { it in line }
                    .filterNot { allowed.keys.any { ok -> ok in line } }
                    .forEach { offenders += "$file:${i + 1}  '$it'  →  ${line.trim().take(90)}" }
            }
        }
        assertEquals(
            emptyList<String>(), offenders.sorted(),
            "section phải đi qua `bridge.…` (IA v2 · N2). Lời gọi thẳng bỏ qua tác dụng phụ mà cầu đang gánh ⇒ " +
                "prefs đổi mà cụm không đổi:\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun `moi section ClusterNav that su dung cau`() {
        sections.forEach { file ->
            val calls = Regex("""\bbridge\.\w+\(""").findAll(code(file)).count()
            assertTrue(
                calls >= 3,
                "$file chỉ gọi cầu $calls lần — một section 'sạch' vì nó rỗng thì không chứng minh được gì",
            )
        }
    }

    /** Danh sách ngoại lệ không được rữa: mục không còn trong mã thì phải bỏ khỏi danh sách. */
    @Test
    fun `danh sach ngoai le khong bi rua`() {
        val all = sections.joinToString("\n") { code(it) }
        assertEquals(
            emptyList<String>(), allowed.keys.filterNot { it in all }.sorted(),
            "mục ngoại lệ không còn trong mã — bỏ khỏi `allowed` cho khỏi rữa",
        )
        assertTrue(allowed.values.all { it.isNotBlank() }, "mỗi ngoại lệ phải kèm LÝ DO")
        assertTrue(forbidden.values.all { it.isNotBlank() }, "mỗi mục cấm cũng phải kèm LÝ DO")
    }

    /**
     * Chiều ngược: cầu là **của Settings**, nhưng Settings KHÔNG được dựng ra nó.
     *
     * [ClusterNavBridge] cần `applicationContext` + một đường post về luồng vẽ + `activityProvider`; dựng nó trong
     * một section (chỗ chỉ có `Context` của View) là mời đúng lỗi *"giữ Activity trong một vật sống lâu"* mà KDoc
     * của cầu chặn ngay ở constructor. Nó được dựng ĐÚNG MỘT LẦN ở composition-root ([KachiHomeWiring]).
     */
    @Test
    fun `cau duoc dung dung MOT lan, o composition-root`() {
        val wiring = code("KachiHomeWiring.kt")
        assertTrue(wiring.contains("ClusterNavBridge("), "cầu phải dựng ở KachiHomeWiring")
        assertTrue(wiring.contains("app = applicationContext"), "và phải nhận applicationContext, không phải Activity")
        val builders = (sections + listOf("SettingsSections.kt", "SettingsPanel.kt", "HomePanels.kt"))
            .filter { code(it).contains("ClusterNavBridge(") }
        assertEquals(
            emptyList<String>(), builders,
            "chỉ composition-root được dựng cầu; các tệp này đang tự dựng bản thứ hai: $builders",
        )
    }

    /**
     * Mã [BridgeMsg] phải dịch qua **tài nguyên**, và `when` phải **tường minh** — không `getIdentifier`.
     *
     * `resources.getIdentifier("kachi_bridge_" + msg.name.lowercase())` hỏng IM LẶNG (trả `0` ⇒ `getString(0)` ném
     * lúc chạy, trên xe, đúng lúc đang cấp quyền hỏng). `when` vét cạn trên enum thì thiếu một nhánh là không biên
     * dịch được — mà đó chính là ca "thêm một BridgeMsg rồi quên viết chuỗi".
     */
    @Test
    fun `moi ma BridgeMsg deu co chuoi tai nguyen`() {
        val fn = SourceRoots.body(code("KachiHomeWiring.kt"), "internal fun bridgeMsgRes(")
        assertTrue(!fn.contains("getIdentifier"), "cấm tra tài nguyên theo TÊN — nó hỏng im lặng lúc chạy")
        assertTrue(!fn.contains("else ->"), "`when` phải vét cạn: `else` biến 'quên một mã' thành lỗi im lặng")
        BridgeMsg.values().forEach { msg ->
            assertTrue(fn.contains("BridgeMsg.${msg.name}"), "thiếu nhánh cho BridgeMsg.${msg.name}")
        }
        val keys = Regex("""R\.string\.(kachi_bridge_\w+)""").findAll(fn).map { it.groupValues[1] }.toList()
        assertEquals(
            BridgeMsg.values().size, keys.size,
            "mỗi mã đúng một khoá tài nguyên",
        )
        assertEquals(keys.size, keys.toSet().size, "không được hai mã dùng chung một câu — chúng nói hai việc khác nhau")
    }

    /**
     * ═══ HAI CHỖ KHAI DẢI TỈ LỆ CHIA CỤM — PHẢI BẰNG NHAU ════════════════════════════════════════════════════
     *
     * [SOÁT SENIOR 2026-09-13] `ClusterNavSettingsModel.splitRatioOptions()` (`:core`, dựng dải cho màn Cài đặt) và
     * [com.byd.clusternav.modules.clustercast.simplified.CastProfile.SPLIT_PERCENTS] (`:core`, dải mà bộ chiếu
     * THẬT nhận) là **hai lời khai độc lập** của cùng một dải — cả hai đang là `(10..90 step 10)`, nhưng không có
     * gì bắt chúng đi cùng nhau.
     *
     * Trôi một bước là hỏng **im lặng, chỉ trên xe**: màn Cài đặt bày một nấc mà `CastProfile.of(...)` không có
     * hồ sơ ⇒ `normalizePercent` kéo về nấc gần nhất (hoặc cú chạm không có tác dụng gì) — đúng họ lỗi "chặn im
     * lặng" mà `DockConfig.setEnabled` và trần-8-mục đã phải vá. Bài này đứng ở `:app` vì đây là tầng **ghép** hai
     * nguồn đó lại (`ClusterNavBridge.splitPctOptions`).
     */
    @Test
    fun `dai ti le cua man Cai dat khop dai cua bo chieu`() {
        assertEquals(
            com.byd.clusternav.modules.clustercast.simplified.CastProfile.SPLIT_PERCENTS.toList(),
            ClusterNavSettingsModel.splitRatioOptions(),
            "hai chỗ khai dải tỉ lệ đã trôi khỏi nhau: màn Cài đặt sẽ bày một nấc mà bộ chiếu không có hồ sơ ⇒ " +
                "cú chạm bị kéo về nấc khác (hoặc không có tác dụng) mà không nói một lời nào",
        )
    }
}
