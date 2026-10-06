package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá **DÂY NỐI** của việc dọn dev/debug UI khỏi màn Cài đặt.
 *
 * ## ⚠ Bài này đã ĐẢO CHIỀU ở bản release production (owner 2026-09-21)
 * Bản đầu (UX-OVERHAUL · WP7, owner 2026-09-20: *"ẩn hết đồ dev/debug/log/lấy-info-xe khỏi UI, giữ chức năng chạy
 * qua adb"*) canh rằng mọi bề mặt dev **đứng SAU cổng** `DevMode.unlocked`. Owner chốt lại gọn hơn cho bản giao xe:
 * *"DỌN HẾT dev/debug UI khỏi màn Cài đặt, CHỈ GIỮ đúng công tắc Chế độ kiểm thử qua adb"* ⇒ các bề mặt ấy không
 * còn **ở đâu cả** trên màn, gác hay không gác đều hết nghĩa.
 *
 * Nên bài không bị xoá mà **đổi câu hỏi** — cùng lý do nó tồn tại từ đầu: đây là loại bất biến không nhìn thấy
 * được off-car. Mọi thứ vẫn biên dịch và test hành vi vẫn xanh kể cả khi một bề mặt dev **mọc lại**; nó chỉ lộ khi
 * có người mở màn Cài đặt trên xe. Ba nhóm:
 *  1. **Công tắc test-mode là bề mặt đồ đo DUY NHẤT còn lại** trong khối *Nâng cao*.
 *  2. **Không bề mặt dev nào mọc lại** — Diagnostics · VietMap-data · Gõ-lệnh-chữ · CAPTEST · nhật-ký-voice.
 *  3. **Đường thoát THẬT của người dùng ở lại** — hai nút cứu-hộ cast KHÔNG bị gỡ và cũng KHÔNG bị gác (cụm đang
 *     tối là việc của người lái, không phải của người viết code).
 *
 * ⚠ KHẢ NĂNG thì không mất, chỉ BỀ MẶT mất: `KachiTestBridge` vẫn nhận `say`/`captest`/`prefs_set`/`voice_dump`. Bài
 * [duong adb khong bi cat theo] ở dưới ghim điều đó — dọn UI mà dọn luôn đường adb thì lượt lên xe sau mất sạch công cụ.
 *
 * ⚠ Đính chính (2.93 wave 2B · DIAG-SCREENS-UNREACHABLE): câu cũ *"hai màn chẩn đoán vẫn mở được bằng `am start -n`"* SAI —
 * `exported=false` ⇒ uid shell bị từ chối (AOSP 10 r47 `checkStartAnyActivityPermission`; [ĐO xe 29/09]). Nay hai màn mở
 * được CHỈ bằng lệnh cầu `diag_screen` (sau công tắc test-mode) — một LỆNH, không phải bề mặt UI; luật *"không dựng lại lối
 * vào trong Cài đặt"* giữ nguyên ([hai man chan doan chi mo qua lenh cau sau cong test-mode]).
 */
class DevSurfaceGateContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }
    private val cast by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCast.kt") }
    private val voiceModel by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt") }
    private val devMode by lazy { code("src/main/java/com/byd/clusternav/launcher/DevMode.kt") }

    /**
     * [DevMode] gác bằng CHÍNH cửa sổ test-mode, KHÔNG bằng một khoá `dev_mode` riêng (bẫy hai-bản-sao).
     *
     * ⚠ [ĐO 2026-09-21] Sau lượt dọn, `DevMode.unlocked` có **0 chỗ gọi trong mã sản phẩm** — owner giữ tệp lại
     * (một cổng fail-safe đã đo, dùng được ngay nếu cần bày lại đồ đo nào). Bài này vì thế canh **hình dạng** của
     * cổng chứ không canh chỗ dùng: hôm nào có người nối lại thì nó phải nối vào cái cổng đúng, không phải dựng
     * cổng thứ hai.
     */
    @Test
    fun `cong dev gac bang cua so test-mode, khong sinh khoa thu hai`() {
        assertTrue(devMode.contains("TestBridgeStore.remainingMinutes"), "cổng đọc chính cửa sổ test-mode")
        assertTrue(devMode.contains(".getOrDefault(false)"), "đọc hỏng ⇒ ĐÓNG (mặc định fail-safe cho đồ dev)")
        assertEquals(
            0, Regex(""""dev_mode"""").findAll(devMode).count(),
            "không được sinh khoá dev_mode thứ hai — mở cửa thứ hai cho cùng một quyền là bẫy hai-bản-sao",
        )
    }

    /**
     * Khối *Nâng cao* của nhóm Hệ thống: **đúng một** bề mặt — công tắc test-mode.
     *
     * Ghim bằng cách ĐẾM chứ không chỉ bằng `contains`: `contains` một mình vẫn xanh khi có ai đó thêm bề mặt thứ
     * hai vào cùng khối, mà đó chính là hình dạng của lần trôi kế tiếp. Ô tick của công tắc do `testBridge(body)`
     * dựng bên trong nó, nên khối này **không được** còn lời gọi `body.addView(` nào.
     */
    @Test
    fun `khoi Nang cao chi con cong tac test-mode`() {
        val fn = SourceRoots.body(sections, "private fun system(")
        val advanced = fn.substringAfter("R.string.kachi_sub_advanced")
        assertTrue(advanced.contains("testBridge(body)"), "công tắc *Chế độ kiểm thử qua adb* phải còn")
        assertEquals(
            0, Regex("""body\.addView\(|\w+Console\(""").findAll(advanced).count(),
            "khối *Nâng cao* chỉ được gọi `testBridge(body)`; mọi hàng dựng thêm ở đây là đồ dev mọc lại",
        )
    }

    /** Năm bề mặt dev đã GỠ: không tệp section nào dựng lại chúng. */
    @Test
    fun `khong be mat dev nao con duoc dung trong Cai dat`() {
        val surfaces = listOf(
            "openDiagnostics()" to "mở DiagActivity",
            "openVietMapData()" to "mở màn dữ liệu/widget VietMap",
            "CapTestConsole(" to "bảng kiểm từng nút (CAPTEST)",
            "VoiceTextConsole(" to "ô gõ lệnh chữ",
            "VoiceUtteranceLog." to "ô tích + nút xuất nhật ký lượt nói",
        )
        val built = surfaces.filter { (needle, _) ->
            sections.contains(needle) || cast.contains(needle) || voiceModel.contains(needle)
        }
        assertEquals(
            emptyList<String>(), built.map { "${it.first} (${it.second})" },
            "bề mặt dev được dựng lại trong Cài đặt — owner 2026-09-21 chốt gỡ HẾT, chỉ giữ công tắc test-mode",
        )
        // `logRows` là hàm đã xoá: một lời gọi rỗng còn lại thì không ai đọc ra là nó rỗng.
        assertFalse(voiceModel.contains("logRows("), "khối nhật ký voice phải gỡ cả hàm lẫn lời gọi")
    }

    /** Hai nút cứu-hộ cast là đường thoát THẬT của người lái ⇒ ở lại, và KHÔNG bị gác sau cổng nào. */
    @Test
    fun `cuu ho cast o lai va khong bi gac`() {
        val fn = SourceRoots.body(cast, "private fun rescue(")
        assertTrue(fn.contains("restoreCluster()"), "nút trả cụm về đồng hồ phải còn — người lái cần")
        assertTrue(fn.contains("deepRescue("), "nút dọn sạch cụm phải còn")
        assertFalse(fn.contains("DevMode"), "hai nút này không được gác sau cổng dev")
    }

    /**
     * Dọn BỀ MẶT không được dọn theo KHẢ NĂNG: đường adb phải còn nguyên.
     *
     * Đây là nửa còn lại của lời giao ("các đường adb VẪN GIỮ"), và là nửa dễ mất im lặng nhất — gỡ một nút thì
     * thấy ngay, gỡ một nhánh `when` của receiver thì chỉ lượt lên xe sau mới biết.
     */
    @Test
    fun `duong adb khong bi cat theo`() {
        val bridge = code("src/main/java/com/byd/clusternav/launcher/testbridge/KachiTestBridge.kt")
        // Ba lệnh dev đi qua [TestBridgeNoHome] (chạy được cả khi màn chính chưa lên — đúng ca đang chẩn đoán).
        val noHome = code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeNoHome.kt")
        assertTrue(bridge.contains("TestBridgeCommands.SAY ->"), "`say` phải còn: ô Gõ lệnh chữ nay CHỈ còn đường này")
        listOf("CAPTEST", "PREFS_SET", "VOICE_DUMP").forEach {
            assertTrue(
                noHome.contains("TestBridgeCommands.$it ->"),
                "mất nhánh `$it` ⇒ lượt lên xe sau không còn công cụ, mà không bài nào khác thấy",
            )
        }
    }

    /**
     * ═══ 2.93 wave 2B · DIAG-SCREENS-UNREACHABLE — hai màn chẩn đoán: GIỮ, mở CHỈ qua lệnh cầu sau cổng test-mode ═══
     *
     * Ba điều, đúng thứ tự quyết định (điều phối thay owner, mặc định đã chọn — spec `kachi-293-misc.html` OQ3):
     *  1. lối DUY NHẤT là lệnh `diag_screen` của cầu, và nó đứng SAU cổng `TestBridgeStore.isOn` (cùng cổng mọi lệnh);
     *  2. KHÔNG một mã sản phẩm nào khác mở hai màn ấy — lớp Activity chỉ được nhắc ở tệp ánh xạ của cầu. 2.93 wave 2C ·
     *     DIAG-BRIDGE-DEAD-OPENERS: hai cửa cũ `ClusterNavBridge.openDiagnostics()`/`openVietMapData()` (0 chỗ gọi) đã GỠ cả
     *     định nghĩa ⇒ bài nay đòi 0 lần xuất hiện của hai tên ấy (định nghĩa HAY chỗ gọi) — siết, không nới;
     *  3. hai màn vẫn `exported=false` — một lệnh của CHÍNH app, không mở cửa cho app khác.
     * Luật *"không bề mặt dev nào trong Cài đặt"* (bài trên) KHÔNG đổi: lệnh cầu không phải bề mặt UI.
     */
    @Test
    fun `hai man chan doan chi mo qua lenh cau sau cong test-mode`() {
        val bridge = code("src/main/java/com/byd/clusternav/launcher/testbridge/KachiTestBridge.kt")
        val noHome = code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeNoHome.kt")
        val screens = code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeScreens.kt")
        assertTrue(noHome.contains("TestBridgeScreenCommands.DIAG_SCREEN -> TestBridgeScreens.run(app, cmd, reply)"), "lối duy nhất")
        val gate = bridge.indexOf("TestBridgeStore.isOn(")
        assertTrue(gate in 0 until bridge.indexOf("TestBridgeCommands.parse("), "cổng test-mode đứng TRƯỚC phân tích")
        assertTrue(SourceRoots.body(bridge, "private fun dispatch(").contains("TestBridgeNoHome.handle(app, cmd, reply)"),
            "lệnh không cần màn chính đi qua `dispatch` — tức SAU cổng")
        listOf("DiagActivity::class.java", "VietMapWidgetDiagActivity::class.java").forEach {
            assertTrue(screens.contains(it), "ánh xạ tên → $it")
        }
        assertTrue(screens.contains("app.startActivity(Intent(app, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))"),
            "mở bằng Context của CHÍNH app (cùng tiến trình ⇒ exported=false không cản)")
        // (2) Không mã sản phẩm nào khác chạm hai lớp ấy / gọi hai cửa cũ.
        val main = SourceRoots.path("src/main/java")
        val touching = java.nio.file.Files.walk(main).use { s ->
            s.filter { it.toString().endsWith(".kt") }.filter { f ->
                val src = SourceRoots.codeOf("src/main/java/" + main.relativize(f).toString().replace('\\', '/'))
                src.contains("DiagActivity::class") || src.contains("VietMapWidgetDiagActivity::class")
            }.map { it.fileName.toString() }.toList()
        }.toSet()
        assertEquals(setOf("TestBridgeScreens.kt"), touching, "chỉ tệp ánh xạ của cầu nhắc hai lớp (hai cửa cũ đã gỡ ở wave 2C)")
        val mentions = java.nio.file.Files.walk(main).use { s ->
            s.filter { it.toString().endsWith(".kt") }.map { f ->
                Regex("""\b(openDiagnostics|openVietMapData)\(""")
                    .findAll(SourceRoots.codeOf("src/main/java/" + main.relativize(f).toString().replace('\\', '/'))).count()
            }.toList().sum()
        }
        assertEquals(0, mentions, "hai cửa cũ của cầu Cài đặt đã gỡ — mọc lại định nghĩa hay chỗ gọi là mọc lại bề mặt")
        val manifest = SourceRoots.text("src/main/AndroidManifest.xml")
        listOf(".modules.clustercast.DiagActivity", ".vietmapwidget.VietMapWidgetDiagActivity").forEach { name ->
            val block = manifest.substringAfter("android:name=\"$name\"").substringBefore("/>")
            assertTrue(block.contains("android:exported=\"false\""), "$name phải giữ exported=false")
        }
    }
}
