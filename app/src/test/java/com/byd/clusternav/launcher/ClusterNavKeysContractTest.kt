package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PHÉP KIỂM CỦA **R2/R9** phía ClusterNav (IA v2 · §4.3): mỗi khoá mà [SettingsCatalog.CLUSTERNAV_KEYS] khai phải
 * tồn tại **nguyên văn** trong đúng tệp nguồn đang ghi nó.
 *
 * ## Vì sao bài này PHẢI có, và phải nằm ở `:app`
 * IA v2 gộp màn ClusterNav vào Kachi bằng cách **dựng lại điều khiển, ghi cùng khoá**. Nghĩa là mỗi mục mới của
 * Settings là một chuỗi khoá **viết tay** — và một chuỗi viết tay sai chính tả thì mọi thứ vẫn biên dịch, vẫn chạy,
 * vẫn lưu được: nó chỉ lưu vào một khoá **không runtime nào đọc**. Bật "Tự lọc bụi mịn" ⇒ prefs có thêm một dòng,
 * `Pm25FilterApplier` không thấy gì, xe không lọc. Không có lỗi, không có cảnh báo, không có bài test nào đỏ.
 *
 * Dự án đã ăn đúng bẫy này một lần ở quy mô nhỏ: spec S1 §2 ghi khoá lấy gió là `recirc_on_start`, mã thật là
 * `recirc_on_start_enabled` — và bài `khoa lay gio trong khai dung ten that trong Prefs` sinh ra để chặn. Bài này là
 * **bản mở rộng** của đúng khuôn đó cho cả 31 khoá của IA v2, thay vì một khoá.
 *
 * Nằm ở `:app` vì nó quét nguồn `:app`: [ĐO] lượt soát 2026-09-11 chứng minh `:core:test` báo **UP-TO-DATE** khi chỉ
 * nguồn `:app` đổi ⇒ đặt ở `:core` là một dấu xanh không chạy. Chi tiết ở KDoc [SettingsCoverageContractTest].
 *
 * ⚠ Bộ quét cố ý **không** tự suy ra tệp: nó đọc bảng [SettingsCatalog.CLUSTERNAV_KEYS] rồi tra sang [SOURCES]. Nếu
 * để nó tìm khoá trong *"bất kỳ tệp nào của `:app`"* thì một khoá `simple_cast_prefs` gõ nhầm thành tên một khoá của
 * `clusternav_prefs` vẫn xanh — mà đó đúng là kiểu nhầm dễ xảy ra nhất khi hai bộ prefs cùng có `*_enabled`.
 */
class ClusterNavKeysContractTest {

    /**
     * Tệp prefs → các tệp nguồn được phép khai khoá của nó.
     *
     * `clusternav_prefs` có **hai** tệp vì `VmOverlayPosition` mở cùng tệp đó để ghi `vm_bubble_x/y` (nó là phía GỬI
     * của bong bóng VietMap, tách khỏi `Prefs` vì còn bắn broadcast sang bản mod). Đó là sự thật của mã, không phải
     * một ngoại lệ nới cho bài test dễ qua.
     */
    private val SOURCES: Map<String, List<String>> = mapOf(
        "clusternav_prefs" to listOf(
            "src/main/java/com/byd/clusternav/Prefs.kt",
            "src/main/java/com/byd/clusternav/VmOverlayPosition.kt",
            // ⚠ 1.66 — tệp THỨ BA mở cùng `clusternav_prefs`: ba khoá V3 của đường giọng nói
            // (`voice_mic_source` · `voice_confirm_ids` · `voice_follow_up_ms`) nằm ở `PrefsVoiceV3.kt` dưới dạng
            // **hàm mở rộng của chính [Prefs]** (trần 500 dòng, CLAUDE.md §4.1). Cùng tệp prefs, cùng bề mặt gọi
            // — không phải một cửa thứ hai vào chỗ lưu; đó là lý do nó được đứng ở đây, và là điều bài canh này
            // vẫn kiểm được (khoá phải tồn tại NGUYÊN VĂN trong một trong ba tệp).
            "src/main/java/com/byd/clusternav/PrefsVoiceV3.kt",
            // 1.70 — tệp THỨ TƯ cùng `clusternav_prefs`: khoá daemon chạm (`inputd_disabled` · `inputd_token`)
            // tách sang `PrefsInputd.kt` (hàm mở rộng của [Prefs], trần 500 dòng) — cùng tệp prefs, cùng lẽ V3.
            "src/main/java/com/byd/clusternav/PrefsInputd.kt",
        ),
        "simple_cast_prefs" to listOf(
            "src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt",
        ),
        "clusternav_theme" to listOf("src/main/java/com/byd/clusternav/ThemeMode.kt"),
        "clusternav_lang" to listOf("src/main/java/com/byd/clusternav/Lang.kt"),
    )

    /** Mã đã BỎ CHÚ THÍCH — chặn kiểu "đạt test" bằng cách viết tên khoá vào một dòng `//`. */
    private fun codeOf(rel: String): String = SourceRoots.codeOf(rel)

    private fun sourcesFor(prefsFile: String): List<Pair<String, String>> =
        SOURCES.getValue(prefsFile).map { it to codeOf(it) }

    /**
     * Khoá có mặt nguyên văn (`"khoá"`) không — hoặc, với khoá **dựng động**, dạng chưa nội suy (`"tiền_tố$`).
     *
     * `Prefs.seatComfortLevel` ghi `"seat_level_$seatIndex"` cho 4 ghế trong một hàm chung, nên `"seat_level_0"` không
     * tồn tại nguyên văn ở đâu. Tha theo **tiền tố đã khai tường minh** ([SettingsCatalog.CLUSTERNAV_DYNAMIC_KEY_PREFIXES]),
     * không phải theo "bắt đầu bằng seat" — cùng kỷ luật đã dùng cho họ khoá `slot_*` ở [SettingsCatalog.SLOT_KEY_PREFIX].
     */
    private fun declares(src: String, key: String): Boolean {
        if (src.contains("\"$key\"")) return true
        return SettingsCatalog.CLUSTERNAV_DYNAMIC_KEY_PREFIXES.keys.any { prefix ->
            key.startsWith(prefix) && src.contains("\"$prefix\$")
        }
    }

    // ── Bộ quét có thật sự đọc được gì không ─────────────────────────────────────────────────────

    /**
     * Chốt chống **bộ quét hỏng mà vẫn xanh**: đường dẫn sai ⇒ `SourceRoots.path` ném, nhưng một tệp đọc được mà
     * rỗng (hoặc trỏ nhầm sang tệp khác) thì `contains` chỉ trả false hàng loạt — và nếu bảng khoá cũng rỗng thì
     * "0 khoá thiếu" là một câu nói vô nghĩa.
     */
    @Test
    fun `bo quet doc duoc ca bon tep nguon`() {
        SOURCES.forEach { (prefsFile, rels) ->
            rels.forEach { rel ->
                val src = codeOf(rel)
                assertTrue(src.length > 500, "tệp '$rel' chỉ đọc được ${src.length} ký tự — nghi bộ quét trỏ sai")
            }
            // Tên TỆP prefs cũng phải có nguyên văn trong một trong các tệp đó: đổi tên tệp prefs mà quên sửa bảng
            // thì mọi khoá vẫn tìm thấy, còn bảng thì đang nói về một tệp không còn tồn tại.
            assertTrue(
                rels.any { codeOf(it).contains("\"$prefsFile\"") },
                "tên tệp prefs '$prefsFile' không có trong ${rels}",
            )
        }
        assertTrue(
            SettingsCatalog.CLUSTERNAV_KEYS.size >= 30,
            "bảng khoá chỉ có ${SettingsCatalog.CLUSTERNAV_KEYS.size} khoá — IA v2 §4.3 kiểm kê ≥ 30",
        )
    }

    // ── R9 · mọi khoá khai trong danh mục phải tồn tại THẬT, đúng tệp ────────────────────────────

    @Test
    fun `moi khoa ClusterNav ton tai nguyen van trong dung tep nguon`() {
        val missing = SettingsCatalog.CLUSTERNAV_KEYS.filterNot { (key, prefsFile) ->
            sourcesFor(prefsFile).any { (_, src) -> declares(src, key) }
        }
        assertEquals(
            emptyMap<String, String>(), missing,
            "khoá khai trong SettingsCatalog.CLUSTERNAV_KEYS không có trong tệp nguồn tương ứng — gõ sai một ký tự " +
                "thì Settings vẫn lưu được, chỉ là lưu vào một khoá KHÔNG runtime nào đọc (bật lọc bụi mà xe không " +
                "lọc, không báo lỗi gì)",
        )
    }

    /**
     * Khoá **cố ý không lên UI** cũng phải là khoá THẬT.
     *
     * Một danh sách loại trừ trỏ vào khoá không tồn tại là danh sách đang rữa: nó vẫn đọc như một quyết định có lý
     * do, nhưng thứ nó loại trừ đã biến mất từ lâu — và người sau sẽ tin nó thay vì đi đọc mã.
     */
    @Test
    fun `khoa co y khong len UI cung phai la khoa that`() {
        // ⚠ 1.66 — quét CẢ BA tệp của `clusternav_prefs` (xem chú thích ở [SOURCES]), không chỉ tệp đầu: khoá ẩn
        // `voice_follow_up_ms` khai ở `PrefsVoiceV3.kt`. Ghim tệp đầu là biến mọi lượt tách tệp hợp lệ thành đỏ giả.
        val prefs = SOURCES.getValue("clusternav_prefs").joinToString("\n") { codeOf(it) }
        val ghosts = SettingsCatalog.CLUSTERNAV_HIDDEN_KEYS.keys.filterNot { declares(prefs, it) }
        assertEquals(emptyList<String>(), ghosts, "khoá ẩn không tồn tại trong họ tệp Prefs — danh sách đang rữa")
    }

    /** Tệp prefs đã khai mà không khoá nào dùng = rác tích lại, và nó nới lỏng bài canh cho lần sau. */
    @Test
    fun `khong tep prefs nao khai thua`() {
        val used = SettingsCatalog.CLUSTERNAV_KEYS.values.toSet()
        assertEquals(
            SettingsCatalog.CLUSTERNAV_PREFS_FILES.keys.sorted(), used.sorted(),
            "danh sách tệp prefs của ClusterNav lệch với các khoá đang khai",
        )
        assertEquals(
            SOURCES.keys.sorted(), SettingsCatalog.CLUSTERNAV_PREFS_FILES.keys.sorted(),
            "bảng tệp→nguồn của bài test lệch với bảng ở `:core` — một trong hai đang nói về tệp không còn nữa",
        )
    }

    /**
     * Mốc **theo từng tệp nguồn**: mỗi tệp phải đóng góp ít nhất một khoá tìm thấy.
     *
     * Thiếu một mốc = nhánh đó đã chết mà bài vẫn xanh — đúng kỷ luật "mốc cho từng nhánh bộ quét" mà
     * [SettingsCoverageContractTest] đang dùng.
     */
    @Test
    fun `moi tep nguon deu dong gop it nhat mot khoa`() {
        mapOf(
            "enabled" to "Prefs.kt (tệp chính của ClusterNav)",
            "vm_bubble_x" to "VmOverlayPosition.kt (cùng tệp prefs, khác tệp nguồn)",
            "cast_enabled" to "SimpleCastRuntime.kt (tệp prefs THỨ HAI của ClusterNav)",
            "theme_choice" to "ThemeMode.kt (tệp prefs riêng, đọc được ở attachBaseContext)",
            "lang" to "Lang.kt (chỗ lưu ngôn ngữ dùng chung cả APK)",
        ).forEach { (key, branch) ->
            val prefsFile = SettingsCatalog.CLUSTERNAV_KEYS[key]
            assertTrue(prefsFile != null, "khoá mốc '$key' biến mất khỏi bảng ⇒ nhánh '$branch' không còn được canh")
            assertTrue(
                sourcesFor(prefsFile!!).any { (_, src) -> declares(src, key) },
                "mốc '$key' không tìm thấy ⇒ nhánh '$branch' đã chết",
            )
        }
    }

    // ── Ràng buộc ngược: khoá của ClusterNav đã lên UI thì phải có CHỦ trong danh mục ────────────

    @Test
    fun `khoa co UI deu co chu hoac la khoa di kem`() {
        val ownerless = SettingsCatalog.CLUSTERNAV_KEYS.keys.filter { key ->
            SettingsCatalog.groupOf(key) == null && key !in SettingsCatalog.CLUSTERNAV_COMPANION_KEYS
        }
        assertEquals(
            emptyList<String>(), ownerless,
            "khoá có UI mà không mục nào nhận và cũng không khai là khoá đi kèm",
        )
    }
}
