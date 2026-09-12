package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ClusterNavSettingsModel.AutostartToggle
import com.byd.clusternav.launcher.ClusterNavSettingsModel.VkStatus
import com.byd.clusternav.speedbadge.BadgeLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * IA v2 · T1 — quyết định thuần của các điều khiển ClusterNav dựng lại trong Kachi Settings.
 *
 * ## Bài nào đáng giá ở đây
 * Không phải bài "hàm trả về đúng cái nó viết" — mà bài khoá đúng **chỗ dễ chép sai** khi T4 dựng section:
 *  • mã lưu chủ đề là `"system"` chứ KHÔNG phải `"auto"` (đoán sai ⇒ `Choice.fromCode` lùi im lặng về SYSTEM, tức
 *    đúng-vì-may, và sẽ sai ngày có mã mới);
 *  • loại trừ tự-chiếu **bất đối xứng** (bật thì tắt cái kia, tắt thì không đụng) — chép thành đối xứng là mất trạng
 *    thái "cả hai cùng tắt";
 *  • dải cỡ biển báo đọc biên từ [BadgeLayout] chứ không viết cứng 60/240;
 *  • chế độ cụm chỉ có HAI nấc thật, và phép quy đổi lúc đọc phải giống `Prefs.navClusterScreenMode`.
 */
class ClusterNavSettingsModelTest {

    // ── Chủ đề ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `ma luu chu de khop ThemeMode Choice cua man cu`() {
        assertEquals("light", ClusterNavSettingsModel.themeChoiceCode(ThemeMode.DAY))
        assertEquals("dark", ClusterNavSettingsModel.themeChoiceCode(ThemeMode.NIGHT))
        // ⚠ KHÔNG phải "auto": `ThemeMode.Choice` của ClusterNav chỉ có system/light/dark (ThemeMode.kt:26–30).
        assertEquals("system", ClusterNavSettingsModel.themeChoiceCode(ThemeMode.AUTO))
    }

    @Test
    fun `moi che do giao dien deu co ma luu, khong ca nao roi ra ngoai`() {
        // `when` đã exhaustive nên bài này canh thứ khác: ba mã phải KHÁC NHAU. Ánh xạ hai chế độ về cùng một mã là
        // cách hỏng im lặng nhất — nút vẫn bấm được, màn nâng cao vẫn đổi, chỉ là đổi sai chiều.
        val codes = ThemeMode.values().map { ClusterNavSettingsModel.themeChoiceCode(it) }
        assertEquals(codes.size, codes.distinct().size, "hai chế độ dùng chung một mã lưu: $codes")
        assertTrue(codes.all { it.isNotBlank() })
    }

    // ── Tỉ lệ chia đôi ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `chin nac ti le chia doi, don vi la phan tram nua TRAI`() {
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 70, 80, 90), ClusterNavSettingsModel.splitRatioOptions())
        assertEquals("1:9", ClusterNavSettingsModel.splitRatioLabel(10))
        assertEquals("5:5", ClusterNavSettingsModel.splitRatioLabel(50))
        assertEquals("9:1", ClusterNavSettingsModel.splitRatioLabel(90))
    }

    @Test
    fun `nhan ti le luon cong lai bang muoi`() {
        ClusterNavSettingsModel.splitRatioOptions().forEach { pct ->
            val (l, r) = ClusterNavSettingsModel.splitRatioLabel(pct).split(":").map { it.toInt() }
            assertEquals(10, l + r, "nhãn '$l:$r' cho $pct% không cộng lại thành 10 phần")
        }
    }

    // ── Tự chiếu: loại trừ nhau ──────────────────────────────────────────────────────────────────

    @Test
    fun `bat tu chieu toan man thi tat tu chieu chia doi`() {
        assertEquals(true to false, ClusterNavSettingsModel.autostartExclusive(true, true, AutostartToggle.FULL))
        assertEquals(true to false, ClusterNavSettingsModel.autostartExclusive(true, false, AutostartToggle.FULL))
    }

    @Test
    fun `bat tu chieu chia doi thi tat tu chieu toan man`() {
        assertEquals(false to true, ClusterNavSettingsModel.autostartExclusive(true, true, AutostartToggle.SPLIT))
        assertEquals(false to true, ClusterNavSettingsModel.autostartExclusive(false, true, AutostartToggle.SPLIT))
    }

    @Test
    fun `TAT mot cong tac thi KHONG dung toi cai kia`() {
        // [ĐO] CastAutostart.kt:32–61 không có nhánh `else` — cố ý. "Cả hai cùng tắt" là trạng thái hợp lệ; làm phép
        // loại trừ đối xứng sẽ tự bật cái kia lên, tức người dùng tắt một thứ mà máy bật một thứ khác.
        assertEquals(false to false, ClusterNavSettingsModel.autostartExclusive(false, false, AutostartToggle.FULL))
        assertEquals(false to false, ClusterNavSettingsModel.autostartExclusive(false, false, AutostartToggle.SPLIT))
        assertEquals(false to true, ClusterNavSettingsModel.autostartExclusive(false, true, AutostartToggle.FULL))
        assertEquals(true to false, ClusterNavSettingsModel.autostartExclusive(true, false, AutostartToggle.SPLIT))
    }

    @Test
    fun `khong bao gio ra trang thai ca hai cung bat`() {
        val states = listOf(false to false, false to true, true to false, true to true)
        states.forEach { (f, s) ->
            AutostartToggle.values().forEach { t ->
                val (nf, ns) = ClusterNavSettingsModel.autostartExclusive(f, s, t)
                // Ngoại lệ DUY NHẤT: đầu vào đã sai sẵn (cả hai bật) mà người dùng TẮT một cái — lúc đó hàm không
                // được tự ý sửa cái kia, nên trạng thái sai cũ vẫn còn một nửa. Không có đường nào TẠO RA nó.
                val inputWasBroken = f && s
                if (!inputWasBroken) {
                    assertTrue(!(nf && ns), "từ ($f,$s) chạm $t ra ($nf,$ns) — hai bộ tự chiếu tranh cùng một cụm")
                }
            }
        }
    }

    // ── Cỡ biển báo ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nac co bien bao nam tron trong bien cua BadgeLayout`() {
        val opts = ClusterNavSettingsModel.badgeSizeOptions()
        assertEquals(BadgeLayout.SIZE_MIN_DP, opts.first())
        assertEquals(BadgeLayout.SIZE_MAX_DP, opts.last())
        assertEquals(listOf(60, 80, 100, 120, 140, 160, 180, 200, 220, 240), opts)
        // Không nấc nào bị `clampSizeDp` sửa lại — nấc bị kẹp là một cú chạm không có tác dụng.
        opts.forEach { assertEquals(it, BadgeLayout.clampSizeDp(it), "nấc $it bị kẹp lại") }
    }

    @Test
    fun `cac nac cach deu va tang dan`() {
        val opts = ClusterNavSettingsModel.badgeSizeOptions()
        opts.zipWithNext { a, b ->
            assertEquals(BadgeLayout.SIZE_STEP_DP, b - a, "hai nấc $a → $b không cách đều")
        }
    }

    // ── Trạng thái phím vô-lăng ──────────────────────────────────────────────────────────────────

    @Test
    fun `ba trang thai phim vo-lang dung nhu man cu`() {
        assertEquals(VkStatus.ACTIVE, ClusterNavSettingsModel.voiceKeyStatus(enabled = true, bound = true))
        assertEquals(VkStatus.DISCONNECTED, ClusterNavSettingsModel.voiceKeyStatus(enabled = true, bound = false))
        assertEquals(VkStatus.OFF, ClusterNavSettingsModel.voiceKeyStatus(enabled = false, bound = false))
    }

    @Test
    fun `tat thi khong hoi tiep du dich vu con dang noi`() {
        // [ĐO] MainActivity.kt:998–1016 hỏi `!enabled` TRƯỚC. `NavAccessibilitySource.connected` là cờ của dịch vụ
        // hệ thống và nó còn `true` một lúc sau khi người dùng tắt ⇒ hỏi `bound` trước sẽ báo "ĐANG HOẠT ĐỘNG" cho
        // một tính năng vừa bị tắt.
        assertEquals(VkStatus.OFF, ClusterNavSettingsModel.voiceKeyStatus(enabled = false, bound = true))
    }

    // ── Chế độ cụm ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chi hai nac che do cum, dung gia tri da do duoc`() {
        val opts = ClusterNavSettingsModel.clusterModeOptions()
        assertEquals(listOf(0, 3), opts.map { it.value }, "OFF=0 và FULL=3 (giá trị PROVEN rc=0) — không bày 1/2")
        assertTrue(opts.all { it.label.isNotBlank() && it.labelEn.isNotBlank() })
    }

    @Test
    fun `quy doi gia tri da luu giong het Prefs navClusterScreenMode`() {
        assertEquals(ClusterNavSettingsModel.NavClusterMode.OFF, ClusterNavSettingsModel.clusterModeOf(0))
        assertEquals(ClusterNavSettingsModel.NavClusterMode.ON, ClusterNavSettingsModel.clusterModeOf(3))
        // Giá trị đời cũ (SIMPLE=1 / SMALL=2) phải gộp về Bật — đúng phép quy đổi lúc ĐỌC của Prefs.kt:74–78. Bộ
        // chọn hiện một nấc còn runtime dùng nấc khác là sai IM LẶNG, chỉ lộ ra khi nhìn cụm.
        assertEquals(ClusterNavSettingsModel.NavClusterMode.ON, ClusterNavSettingsModel.clusterModeOf(1))
        assertEquals(ClusterNavSettingsModel.NavClusterMode.ON, ClusterNavSettingsModel.clusterModeOf(2))
        assertEquals(ClusterNavSettingsModel.NavClusterMode.ON, ClusterNavSettingsModel.clusterModeOf(99))
    }
}
