package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ U7 · MÃ CÓ VỊ TRÍ THÌ **HÌNH** CŨNG PHẢI CÓ ĐÚNG VỊ TRÍ ĐÓ ══════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-icon-set-v2.html` §3 **R2** (*"4 cửa/kính/lốp/ghế/xi-nhan = 4 biến thể FL/FR/RL/RR trên
 * cùng khung xe"*) và §5 **T4**.
 *
 * ## Vì sao cần bài này khi đã có [CapabilityIconsDiversityTest]
 * Bài kia đo **phân bố** — "không hình nào mang quá ba ô trong một nhóm". Phép đó **xanh** với một cách chữa sai:
 * gán cho bốn cái lốp bốn hình bất kỳ khác nhau (vd bốn glyph trừu tượng rời rạc) là đủ qua trần, trong khi người
 * lái vẫn không đọc ra *"cái nào là bánh trước-trái"*. Nói cách khác: bài kia canh **khác nhau**, bài này canh
 * **đúng** — bốn hình phải khác nhau ĐÚNG Ở CHỖ vị trí, và chỗ đó phải nói ra được bằng chính tên tệp.
 *
 * ## Cách đo: đọc HẬU TỐ THẬT trong mã, không kê tay danh sách mã
 * Bộ đăng ký dùng **hai** quy ước hậu tố cho cùng một góc xe (lịch sử: TPMS theo `fl/fr/rl/rr`, thân xe theo
 * `lf/rf/lr/rr`, ghế theo `driver/passenger`). Bài này chuẩn hoá tất cả về một thang
 * [Corner] rồi so với hậu tố của tên hình — nên thêm một mã có vị trí ở quy ước nào cũng bị soi, và **không** có
 * một danh sách chép tay nào phải giữ đồng bộ.
 */
class CapabilityIconPositionTest {

    /** Thang vị trí chuẩn hoá — bốn góc, hai bên, hai đầu. */
    private enum class Corner { FL, FR, RL, RR, LEFT, RIGHT, FRONT, REAR, ALL }

    /**
     * Mã có chữ chỉ vị trí nhưng **cố ý KHÔNG** mang hình theo vị trí — kèm lý do (lệ
     * [CapabilityCatalog.HIDDEN_FROM_PICKER]: danh sách loại trừ phải bắt viết lý do).
     */
    private val noPositionShape: Map<String, String> = mapOf(
        // ⚠ UX-OVERHAUL · WP8 2026-09-20 — ba mã mô-tơ (`motor_front_rpm` · `motor_rear_rpm` ·
        // `motor_front_torque`) đã **purge** khỏi registry ⇒ rời danh sách này, đúng cách nó phải "tự rữa"
        // (bài `danh sach loai tru vi tri khong bi rua` đòi mọi mã khai ở đây phải còn tồn tại).
        // `defrost_rear` ĐÃ RỜI danh sách này ở U7 lượt 2: nay có `ic-car-rear-defrost` (khung nhìn từ sau +
        // sóng nhiệt trên kính hậu) nên nó đi qua phép canh như mọi mã có vị trí khác — đúng cách danh sách
        // này phải "tự rữa" (trả nợ thì rời danh sách, không để lại dòng chết).
        // H1 · T2: `defrost_front_state` — chữ "front" ở đây là KÍNH TRƯỚC (một trong hai tấm kính sấy được),
        // không phải một GÓC của thân xe như `_fl`/`_rr`. Cặp sấy vẫn phân biệt được bằng hình: trước dùng
        // `ic-defrost` (kính chắn gió + sóng nhiệt), sau dùng `ic-car-rear-defrost` (khung nhìn từ sau) — đúng cặp
        // mà nút `defrost`/`defrost_rear` đang dùng, nên ô XEM và nút cùng một việc mang cùng một hình.
        "defrost_front_state" to
            "\"front\" = kính trước, không phải góc thân xe; cặp trước/sau đã khác hình (ic-defrost vs " +
                "ic-car-rear-defrost) nên người dùng vẫn phân biệt được",
        "mac_win_open_all" to
            "gói lệnh: hình (ic_window_open) ĐÃ vẽ cả bốn ô kính trên khung xe + mũi tên hạ kính, chỉ tên tệp là " +
                "tên cũ (giữ tên để khoá lưu bền của người dùng không đổi)",
        "mac_win_close_all" to
            "cùng lý do mac_win_open_all: ic_window_close vẽ bốn ô kính trên khung xe + mũi tên nâng kính, "
                + "tên tệp giữ nguyên để khoá lưu bền của người dùng không đổi",
    )

    /** Vị trí ĐỌC TỪ MÃ — `null` nghĩa là mã này không nói gì về chỗ. */
    private fun cornerOfId(id: String): Corner? {
        fun tok(vararg t: String) = t.any { Regex("(^|_)$it(_|$)").containsMatchIn(id) }
        return when {
            tok("fl", "lf") -> Corner.FL
            tok("fr", "rf") -> Corner.FR
            tok("rl", "lr") -> Corner.RL
            tok("rr") -> Corner.RR
            tok("driver") -> Corner.FL
            tok("passenger") -> Corner.FR
            tok("left") -> Corner.LEFT
            tok("right") -> Corner.RIGHT
            tok("front") -> Corner.FRONT
            tok("rear") -> Corner.REAR
            tok("all") -> Corner.ALL
            else -> null
        }
    }

    /**
     * Hậu tố nào của TÊN HÌNH được coi là "nói đúng vị trí này".
     *
     * Bốn góc chấp nhận **cả hậu tố bên** (`-l`/`-r`): có mã nói *"trước-trái"* mà hình vẽ được chỉ phân biệt nổi
     * BÊN trái — ép nó phải mang `-fl` sẽ là ép vẽ một thứ không có thật.
     */
    private fun iconSaysCorner(icon: String, corner: Corner): Boolean {
        fun end(vararg t: String) = t.any { icon.endsWith("-$it") }
        fun has(t: String) = icon.endsWith("-$t") || icon.contains("-$t-")
        return when (corner) {
            Corner.FL -> end("fl", "lf", "l")
            Corner.FR -> end("fr", "rf", "r")
            Corner.RL -> end("rl", "lr", "l")
            Corner.RR -> end("rr", "r")
            Corner.LEFT -> end("l", "fl", "rl", "lf", "lr")
            Corner.RIGHT -> end("r", "fr", "rr", "rf")
            Corner.FRONT -> has("front")
            Corner.REAR -> has("rear")
            Corner.ALL -> end("all")
        }
    }

    private fun positioned(): List<Pair<CapabilityPick, Corner>> =
        CapabilityCatalog.allIncludingHidden().mapNotNull { p ->
            if (p.id in noPositionShape) null else cornerOfId(p.id)?.let { p to it }
        }

    // ── 1 · mỗi mã có vị trí ⇒ hình mang đúng vị trí ấy ─────────────────────────────────────────────

    @Test
    fun `ma co vi tri thi hinh cung phai co vi tri do`() {
        val bad = positioned()
            .filterNot { (p, c) -> iconSaysCorner(p.icon, c) }
            .map { (p, c) -> "${p.id} ($c) → ${p.icon}" }
        assertEquals(
            emptyList<String>(), bad,
            "mã nói rõ nó ở góc nào mà HÌNH thì không ⇒ bốn ô \"Áp lốp …\" lại trông y hệt nhau và người lái " +
                "vẫn phải đọc chữ trong ô 40dp (đúng bệnh U1/U6 sinh ra để chữa). Nếu cố ý không vẽ theo vị trí " +
                "thì khai vào noPositionShape KÈM lý do",
        )
        // Chốt chống bộ quét hỏng: quét rỗng thì câu "0 mã sai" là câu nói vô nghĩa.
        // Sàn 40 → 28 sau khi owner gỡ toàn bộ ADAS/an toàn 2026-09-16 (12 mã có vị trí của điểm mù · chuyển làn ·
        // cắt ngang sau · cảnh báo mở cửa · dây an toàn · người ngồi đã xoá). Sàn là chốt chống bộ quét hỏng, không
        // phải mục tiêu — hạ nó đúng bằng số đã mất, không hạ thêm.
        // ⚠ WP8 2026-09-20: 28 → 27 = −4 mã đèn viền trước/sau (màu + độ sáng × 2) +3 mã mô-tơ vừa RỜI
        // `noPositionShape` (chúng bị purge nên không còn bị trừ ở đây nữa). Đúng bằng số đã mất.
        assertTrue(positioned().size >= 27, "chỉ soi được ${positioned().size} mã có vị trí — nghi phép đọc hậu tố hỏng")
    }

    // ── 2 · hai góc KHÁC nhau không được dùng chung một hình ────────────────────────────────────────

    /**
     * Đây mới là phép canh *"khác nhau ĐÚNG ở chỗ vị trí"*: `tyre_p_fl` và `tyre_p_fr` cùng trỏ vào một hình thì
     * bài trên vẫn xanh (hình có hậu tố `-fl`, hợp lệ với góc FL) nhưng hai ô vẫn giống hệt nhau trên màn.
     */
    @Test
    fun `hai goc khac nhau khong dung chung mot hinh`() {
        val clash = positioned().groupBy({ it.first.icon }, { it.second })
            .filterValues { it.distinct().size > 1 }
            .map { (icon, corners) -> "$icon ← ${corners.distinct()}" }
        assertEquals(
            emptyList<String>(), clash,
            "một hình gánh hai vị trí khác nhau ⇒ hai ô cạnh nhau trông y hệt mà nói hai chỗ khác nhau trên xe",
        )
    }

    // ── 3 · danh sách loại trừ tự rữa ──────────────────────────────────────────────────────────────

    @Test
    fun `danh sach loai tru vi tri khong bi rua`() {
        val ids = CapabilityCatalog.allIncludingHidden().map { it.id }.toSet()
        noPositionShape.forEach { (id, why) ->
            assertTrue(id in ids, "$id không còn trong bộ đăng ký — bỏ khỏi noPositionShape")
            assertTrue(cornerOfId(id) != null, "$id không còn chữ chỉ vị trí nào — bỏ khỏi noPositionShape")
            assertTrue(why.length >= 40, "$id: loại trừ thì phải nói được VÌ SAO, không thì đây là chỗ cất nợ")
        }
    }

    // ── 4 · bốn họ lớn phải đủ BỐN hình, không ba, không hai ────────────────────────────────────────

    @Test
    fun `bon lop bon kinh bon cua bon nhiet lop deu du bon hinh rieng`() {
        mapOf(
            "áp suất lốp" to listOf("tyre_p_fl", "tyre_p_fr", "tyre_p_rl", "tyre_p_rr"),
            "nhiệt lốp" to listOf("tyre_t_fl", "tyre_t_fr", "tyre_t_rl", "tyre_t_rr"),
            "kính (xem)" to listOf("window_lf", "window_rf", "window_lr", "window_rr"),
            "kính (bấm)" to listOf("win_lf", "win_rf", "win_lr", "win_rr"),
            "cửa" to listOf("door_lf", "door_rf", "door_lr", "door_rr"),
        ).forEach { (what, ids) ->
            val icons = ids.map { id ->
                CapabilityCatalog.pick(id)?.icon ?: error("mã $id biến mất khỏi bộ đăng ký — bài đang quét vùng sai")
            }
            assertEquals(4, icons.toSet().size, "$what: ${ids.zip(icons)} — bốn góc phải ra bốn hình")
        }
        // Và ô XEM với NÚT của CÙNG một cái kính phải cùng một hình (cùng luật với U6: một việc, một hình).
        listOf("lf", "rf", "lr", "rr").forEach { c ->
            assertEquals(
                CapabilityCatalog.pick("window_$c")?.icon, CapabilityCatalog.pick("win_$c")?.icon,
                "kính $c: ô xem và nút phải mang cùng một hình",
            )
        }
    }
}
