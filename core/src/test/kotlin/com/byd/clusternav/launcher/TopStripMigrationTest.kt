package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ UX5b · DI TRÚ danh sách chip ĐÃ LƯU — [TopStripConfig.migrate] ═══════════════════════════════════════════
 *
 * Bài riêng một tệp vì nó canh một thứ khác hẳn phần còn lại của `TopStripTest`: không phải *"chip hiện ra chữ gì"*
 * mà **"cấu hình đã nằm trên đĩa của xe đang chạy sẽ thành cái gì khi nạp"**. Đây là loại lỗi tệ nhất của dự án nếu
 * làm sai — nó **xoá bố cục người ta đã đặt**, và nó xảy ra một lần, lúc khởi động, không ai kịp thấy.
 *
 * Bối cảnh: owner 2026-09-27, nhìn máy ảo: *"sao còn ghế mát và ghế sưởi riêng, với ghế sao không có ghế lái hay
 * ghế phụ? 2 ghế nó khác nhau mà"*. Chip GỘP đã có từ UX5 (2026-09-26) nhưng **không ai tự đổi** cho người đang
 * dùng máy, nên trên thanh vẫn là hai hình ghế đứng cạnh nhau — một trong hai gần như luôn chỉ để nói *"đang tắt"*.
 */
class TopStripMigrationTest {

    private val legacyDefault = listOf(TopStripConfig.PM25, TopStripConfig.TEMP, TopStripConfig.ENERGY)

    // ── Luật 1 · hai chip ghế LẺ ⇒ MỘT chip gộp, tại vị trí của cái đầu tiên ─────────────────────────

    @Test
    fun `hai chip ghe LE gop thanh MOT chip, giu dung vi tri cua cai dau tien`() {
        assertEquals(
            listOf(TopStripConfig.PM25, TopStripConfig.SEAT, "soc"),
            TopStripConfig.migrate(listOf(TopStripConfig.PM25, "seat_heat_state", "seat_vent_state", "soc")),
            "chip gộp phải thay vào CHỖ của chip lẻ đầu tiên — không đẩy xuống cuối (thứ tự là thứ người ta đặt)",
        )
        // Chỉ MỘT trong hai chip lẻ cũng di trú: một hình ghế mang chữ chế độ vẫn là bề mặt cũ, và người dùng sẽ
        // không bao giờ tự tìm ra chip gộp nếu không có gì đổi.
        assertEquals(
            listOf("soc", TopStripConfig.SEAT),
            TopStripConfig.migrate(listOf("soc", "seat_vent_state")),
        )
        assertEquals(
            listOf(TopStripConfig.SEAT, "soc"),
            TopStripConfig.migrate(listOf("seat_heat_state", "soc")),
        )
    }

    @Test
    fun `da co chip GOP san thi khong nhan doi`() {
        // Ca thật: ai đã đặt chip gộp (UX5) **và** còn giữ một chip lẻ. Kết quả phải là đúng MỘT chip gộp.
        assertEquals(
            listOf(TopStripConfig.SEAT, "soc"),
            TopStripConfig.migrate(listOf(TopStripConfig.SEAT, "seat_heat_state", "soc")),
        )
        assertEquals(
            listOf("soc", TopStripConfig.SEAT),
            TopStripConfig.migrate(listOf("soc", "seat_vent_state", TopStripConfig.SEAT)),
            "chip lẻ đứng TRƯỚC chip gộp ⇒ chỗ của nó là chỗ của chip gộp, và cái sau bị bỏ",
        )
    }

    // ── Luật 2 · đúng mặc định CŨ ⇒ nâng lên mặc định MỚI ───────────────────────────────────────────

    @Test
    fun `dung mac dinh CU thi nang len mac dinh MOI`() {
        assertEquals(TopStripConfig.DEFAULT_IDS, TopStripConfig.migrate(legacyDefault))
        // …và đó là đường mà người *chưa từng sửa gì* đi qua khi nạp prefs cũ.
        assertEquals(TopStripConfig.DEFAULT_IDS, TopStripConfig.decode(legacyDefault.joinToString(",")).ids)
    }

    @Test
    fun `lech mot chut so voi mac dinh CU thi KHONG nang`() {
        // Thứ tự khác ⇒ người ta đã sắp lại ⇒ không đụng.
        val reordered = listOf(TopStripConfig.ENERGY, TopStripConfig.PM25, TopStripConfig.TEMP)
        assertEquals(reordered, TopStripConfig.migrate(reordered))
        // Thiếu một chip ⇒ người ta đã gỡ ⇒ không đụng (và tuyệt đối không "trả lại" chip đã gỡ).
        val trimmed = listOf(TopStripConfig.PM25, TopStripConfig.TEMP)
        assertEquals(trimmed, TopStripConfig.migrate(trimmed))
        // Thêm một chip ⇒ đã sửa ⇒ không đụng.
        val extended = legacyDefault + "soc"
        assertEquals(extended, TopStripConfig.migrate(extended))
    }

    /**
     * ⚠ ĐÁNH ĐỔI NÓI THẲNG: ai **cố ý** giữ đúng ba chip cũ sẽ thấy hai chip ghế mọc thêm **một lần** — trên đĩa,
     * *"chưa từng sửa gì"* và *"đã sửa và đang muốn đúng ba chip này"* là **cùng một chuỗi**, nên không có cách nào
     * phân biệt. Điều bài này khoá là hậu quả của đánh đổi ấy **có đường ra**: gỡ hai chip ghế đi thì lần nạp sau
     * chúng KHÔNG mọc lại (danh sách lúc đó đã khác mặc định cũ ⇒ rơi vào luật 3 "không đụng").
     */
    @Test
    fun `go hai chip ghe di thi lan sau KHONG moc lai`() {
        val afterUserRemovesThem = listOf(TopStripConfig.PM25, TopStripConfig.TEMP, TopStripConfig.ENERGY, "soc")
        assertEquals(afterUserRemovesThem, TopStripConfig.migrate(afterUserRemovesThem))
        assertEquals(afterUserRemovesThem, TopStripConfig.decode(afterUserRemovesThem.joinToString(",")).ids)
    }

    // ── Luật 3 · còn lại KHÔNG ĐỤNG + ba tính chất chung ───────────────────────────────────────────

    @Test
    fun `danh sach khong lien quan den ghe thi tra NGUYEN vat cu`() {
        val ids = listOf("soc", "tyre_p_fl", TopStripConfig.ENERGY)
        assertSame(ids, TopStripConfig.migrate(ids), "không có gì để làm thì không được dựng danh sách mới")
        assertEquals(emptyList<String>(), TopStripConfig.migrate(emptyList()))
    }

    @Test
    fun `luy dang - chay lai khong doi gi nua`() {
        listOf(
            listOf(TopStripConfig.PM25, "seat_heat_state", "seat_vent_state", "soc"),
            legacyDefault,
            TopStripConfig.DEFAULT_IDS,
            listOf("soc", "seat_vent_state", TopStripConfig.SEAT),
            emptyList(),
        ).forEach { input ->
            val once = TopStripConfig.migrate(input)
            assertEquals(once, TopStripConfig.migrate(once), "di trú phải LUỸ ĐẲNG, đầu vào: $input")
            assertEquals(once.size, once.toSet().size, "kết quả không được có mã trùng, đầu vào: $input")
        }
    }

    @Test
    fun `ket qua luon la ma dat duoc va khong vuot tran`() {
        // [TopStripConfig] `init` sẽ `require` — bài này gọi qua [TopStripConfig.decode] để đi đúng đường thật.
        // Chip lẻ đứng ĐẦU để nó thật sự đi qua nhánh gộp (đặt cuối thì `take(CAP)` cắt mất và bài xanh giả).
        val many = listOf("seat_heat_state") + TelemetryRegistry.ALL.take(TopStripConfig.CAP).map { it.id }
        val cfg = TopStripConfig.decode(many.joinToString(","))
        assertTrue(cfg.ids.size <= TopStripConfig.CAP, "di trú không được đẩy danh sách vượt trần")
        assertTrue(cfg.ids.all { TopStripConfig.isChippable(it) })
        assertEquals(TopStripConfig.SEAT, cfg.ids.first(), "tiền đề: nhánh gộp đã chạy thật")
    }

    /**
     * Cửa vào THẬT của phép di trú là [TopStripConfig.decode] (chỗ duy nhất nạp chuỗi bền — `WorkspacePrefs`), nên
     * phải đo ở đó chứ không chỉ đo hàm thuần: nếu ai gỡ lượt gọi trong `decode` thì `migrate` thành một hàm không
     * ai gọi (đúng hình dạng `CastShell.evictVd`, CLAUDE.md §8) và cấu hình cũ **không bao giờ** được di trú.
     */
    @Test
    fun `decode co goi di tru - khong phai mot ham khong ai goi`() {
        assertEquals(
            listOf(TopStripConfig.PM25, TopStripConfig.SEAT),
            TopStripConfig.decode("chip_pm25,seat_heat_state,seat_vent_state").ids,
        )
        // Và mọi hồ sơ đều đi qua cùng cửa ấy (một hàm thuần ⇒ không có trạng thái riêng theo hồ sơ nào).
        assertEquals(
            TopStripConfig.decode("chip_pm25,seat_vent_state,seat_heat_state").ids,
            TopStripConfig.decode("chip_pm25,seat_heat_state,seat_vent_state").ids,
        )
    }

    /**
     * ═══ [P1 · SOÁT Opus 2026-09-27] `applyMigration = false` ⇒ **chỉ lọc**, và đó là đường ra của người dùng ═════
     *
     * Ca hay xảy ra nhất sau khi nâng cấp: mở *Tuỳ biến* trên mặc định MỚI rồi **gỡ cả hai chip ghế**. Chuỗi còn lại
     * trên đĩa là **đúng** mặc định CŨ, nên luật 2 của [TopStripConfig.migrate] không thể phân biệt *"chưa từng sửa"*
     * với *"vừa gỡ"* — và vì `decode` chạy mỗi lượt đọc, không có cổng thì hai chip mọc lại **mãi mãi**.
     *
     * Cổng nằm ở `decode`, mốc bền nằm ở `WorkspacePrefs.K_STRIP_MIGRATED` (bài canh dây nối:
     * `TopStripWiringContractTest.phep di tru chip chi chay dung mot lan moi ho so`). Bài này khoá phần **thuần**:
     * tắt cổng thì danh sách đi ra y như đi vào, còn bật cổng thì nâng lên mặc định mới — hai hành vi, một hàm.
     */
    @Test
    fun `tat cong di tru thi chi con loc - danh sach cua nguoi dung duoc ton trong`() {
        val bare = listOf(TopStripConfig.PM25, TopStripConfig.TEMP, TopStripConfig.ENERGY)
        val saved = bare.joinToString(",")
        // BẬT (lượt đầu, hồ sơ chưa đóng mốc) ⇒ nâng lên mặc định MỚI: đúng điều owner xin ở UX5b.
        assertEquals(TopStripConfig.DEFAULT_IDS, TopStripConfig.decode(saved).ids, "lượt đầu phải nâng mặc định")
        // TẮT (mốc đã đóng) ⇒ tôn trọng đúng thứ người dùng để lại, KHÔNG mọc lại chip ghế.
        assertEquals(
            bare, TopStripConfig.decode(saved, applyMigration = false).ids,
            "đã di trú rồi mà vẫn nâng lại thì người dùng không bao giờ gỡ được hai chip ghế",
        )
        // Tắt cổng KHÔNG tắt phép LỌC: mã không đặt được vẫn phải rụng (chuỗi sửa tay / mã đã xoá ở bản sau).
        assertEquals(
            bare, TopStripConfig.decode("$saved,khong_co_ma_nay", applyMigration = false).ids,
            "tắt cổng di trú không được tắt luôn phép lọc mã đặt được",
        )
        // Và tắt cổng cũng không được gộp hai chip ghế lẻ (đó là luật 1 của phép di trú).
        val singles = TopStripConfig.decode("chip_pm25,seat_heat_state,seat_vent_state", applyMigration = false).ids
        assertEquals(3, singles.size, "tắt cổng ⇒ hai chip ghế lẻ giữ nguyên, không gộp")
    }
}
