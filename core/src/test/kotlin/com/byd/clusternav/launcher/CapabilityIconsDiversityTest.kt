package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ U6 · ICON PHẢI PHÂN BIỆT ĐƯỢC **TRONG CÙNG MỘT MÀN CUỘN** ═══════════════════════════════════════════════
 *
 * ## Vì sao cần bài này khi đã có [CapabilityIconsTest]
 * Bài kia đo **tổng** số icon phân biệt được (và một ca cực đoan: cả nhóm dùng đúng một icon). Cả hai phép đó đều
 * XANH với hiện trạng mà owner soi ảnh ra được:
 *  • [ĐO] ảnh lưới ngăn kéo 2026-09-12/13 — nhóm Năng lượng: **9/28 ô cùng tia sét**, **6/28 cùng con đường**;
 *    nhóm Động lực: **6/14 cùng đồng hồ tốc**; nhóm Khí hậu: **5/12 cùng nhiệt kế**, **4/12 cùng chiếc lá**.
 *  • Tổng lúc đó là 34 icon — con số nghe rất khá, trong khi màn hình thật thì vẫn là một bức tường tia sét.
 *
 * Tức **phân bố** mới là thứ người dùng nhìn thấy, không phải tổng. Bài này đo đúng phân bố, theo từng nhóm, vì
 * nhóm chính là một vùng cuộn trong bộ chọn.
 *
 * ## Đo trên thứ NGƯỜI DÙNG THẬT SỰ THẤY
 * Đếm trên [CapabilityCatalog.all] (đã lọc mã cố ý ẩn) chứ không trên [TelemetryRegistry.ALL]: một ô không bày ra
 * thì nó không làm hàng ô nào rối cả, và đếm nó vào sẽ bắt bài này đi chữa một thứ không ai thấy.
 */
class CapabilityIconsDiversityTest {

    /**
     * TRẦN ĐO ĐƯỢC hôm nay của các nhóm U6 **chưa** chạm tới → lý do chưa chạm.
     *
     * ## ⚠ Đây là NỢ NHÌN THẤY ĐƯỢC, không phải danh sách tha bổng
     * U6 chốt phạm vi đúng ba nhóm owner soi ảnh ra (Năng lượng · Động lực · Khí hậu). Năm nhóm còn lại vẫn quá
     * trần [CapabilityIcons.MAX_PER_DOMAIN], và cách trung thực nhất để nói điều đó là **ghim con số thật** kèm
     * lý do — thay vì `@Ignore`, hay đặt trần chung bằng số xấu nhất (làm mất luôn tác dụng của bài cho ba nhóm
     * đã chữa).
     *
     * Bảng **tự rữa theo hai chiều**: chữa được một nhóm mà quên hạ số ở đây thì bài ĐỎ (chiều "đã tốt hơn số
     * ghim"), và làm xấu đi thì cũng ĐỎ. Không có đường đi nào để một hồi quy trượt qua im lặng.
     */
    private val pendingCeiling: Map<Domain, Pair<Int, String>> = mapOf()

    /**
     * Nhóm đã chữa — áp trần cứng, không có ngoại lệ nào.
     *
     * ## U7: danh sách này nay là **TOÀN BỘ 8 lĩnh vực**, và [pendingCeiling] rỗng
     * U6 chữa ba nhóm dày nhất và ghi nợ năm nhóm còn lại; cả năm đều quá trần vì cùng MỘT lý do: khác biệt của
     * chúng nằm ở **VỊ TRÍ** (bánh nào · cửa nào · đèn nào · vùng cảm biến nào · thành phần toạ độ nào), mà icon
     * theo khái niệm thì không nói được vị trí. U7 vẽ bộ hình XE theo vị trí nên cái nợ đó tan chứ không phải
     * được tha: mỗi mã có vị trí nay tra ra một hình mang đúng vị trí ấy (xem [CapabilityIcons] · bài
     * `ma co vi tri thi hinh cung phai co vi tri do`).
     *
     * ⚠ [pendingCeiling] rỗng thì bài `nhom chua chua phai ghim dung tran do duoc` không lặp lần nào — nên phép
     * canh thật sự nằm ở bài trần CỨNG bên dưới, và nó nay phủ cả 8 lĩnh vực. Đừng đưa một lĩnh vực ngược lại vào
     * danh sách nợ để "cho xanh": bài `moi nhom deu duoc noi toi` bắt mọi lĩnh vực phải nằm ở đúng một trong hai
     * danh sách, và ghi nợ thì phải viết được lý do.
     */
    private val doneDomains = Domain.TELEMETRY

    /**
     * Icon → mã, đếm trên **đúng những ô bộ chọn bày ra** trong nhóm này.
     *
     * ⚠ Đếm CẢ ô xem lẫn ô bấm, vì đó là thứ nằm cạnh nhau trên màn: [ĐO] ảnh 2026-09-12 hàng *"Mục tiêu sạc ·
     * Giới hạn sạc · Sạc không dây · Sạc ngay"* là **bốn NÚT** cùng một tia sét — đếm riêng phía đọc sẽ báo XANH
     * trong khi owner đang nhìn đúng bốn ô giống hệt nhau. Chỉ bỏ ô NHÓM (nó nằm ở mục riêng phía trên, không
     * trộn vào lĩnh vực — xem [CapabilityPicker.singlesOf]).
     */
    private fun shownIconUse(d: Domain): Map<String, List<String>> =
        CapabilityCatalog.all().filter { it.domain == d && !it.group }.groupBy({ it.icon }, { it.id })

    private fun maxShare(d: Domain): Int = shownIconUse(d).values.maxOfOrNull { it.size } ?: 0

    // ── 1 · Ba nhóm U6 chạm tới: trần CỨNG ──────────────────────────────────────────────────────────

    @Test
    fun `trong nhom da chua, khong hinh nao mang qua ba o`() {
        val over = doneDomains.mapNotNull { d ->
            val bad = shownIconUse(d).filterValues { it.size > CapabilityIcons.MAX_PER_DOMAIN }
            if (bad.isEmpty()) null else "$d → " + bad.map { (i, ids) -> "$i ×${ids.size} $ids" }
        }
        assertEquals(
            emptyList<String>(), over,
            "quá ${CapabilityIcons.MAX_PER_DOMAIN} ô cùng một hình trong MỘT nhóm ⇒ icon thành hoa văn nền, " +
                "người dùng quay lại đọc chữ trong ô 40dp (mà chữ thì bị cắt — đúng bệnh U1 sinh ra để chữa)",
        )
    }

    /**
     * Và ba nhóm đó phải **thật sự đa dạng**, không chỉ "không quá 3".
     *
     * Không có phép này thì một nhóm 32 ô chia đều 11 hình × 3 ô vẫn xanh — đúng luật chữ mà trật ý. Sàn dưới là
     * **số ĐO ĐƯỢC sau U6**, không phải số mong muốn; nó chỉ được đi lên.
     */
    @Test
    fun `ba nhom da chua giu duoc so hinh da dat`() {
        // Sàn = số hình ĐO ĐƯỢC sau lượt vá gần nhất, không phải số mong muốn. Chỉ được đi LÊN.
        val floor = mapOf(
            Domain.ENERGY to 15, Domain.DRIVETRAIN to 12, Domain.CLIMATE to 12,
            // U7 — năm lĩnh vực còn lại, sau khi bộ hình xe theo vị trí thay cho gộp-theo-tiền-tố.
            Domain.TYRES to 8, Domain.BODY to 26, Domain.LIGHTS to 16, Domain.SAFETY to 24, Domain.IDENTITY to 7,
        )
        assertEquals(doneDomains.toSet(), floor.keys, "sàn phải phủ đúng các nhóm đã chữa")
        floor.forEach { (d, min) ->
            val use = shownIconUse(d)
            assertTrue(
                use.size >= min,
                "$d: còn ${use.size} hình cho ${use.values.sumOf { it.size }} ô (sàn đã đạt là $min) — " +
                    "${use.mapValues { it.value.size }}",
            )
        }
    }

    /**
     * Ô XEM và NÚT của **cùng một việc** phải mang **cùng một hình**.
     *
     * [ĐO] ảnh 2026-09-13: sau khi gợi ý loại rời khỏi nhãn, hai ô cùng tên *"Drive mode"* nằm cách nhau vài hàng —
     * một cái vẽ NÚM CHỌN, một cái vẽ CẦN SỐ. Người dùng đọc ra hai việc khác nhau, trong khi khác biệt thật chỉ là
     * *"xem"* và *"bấm"* (đã nói ở dòng phụ). Cặp trùng tên chính là định nghĩa sẵn có của *"cùng một việc"*
     * ([CapabilityCatalog.collidingLabels]), nên bài này dùng lại nó thay vì kê tay một danh sách cặp.
     */
    @Test
    fun `o xem va nut cua cung mot viec mang cung mot hinh`() {
        val mismatched = doneDomains.flatMap { d ->
            CapabilityCatalog.all()
                .filter { it.domain == d && !it.group && it.label in CapabilityCatalog.collidingLabels() }
                .groupBy { it.label }
                .filterValues { g -> g.map { it.icon }.distinct().size > 1 }
                .map { (label, g) -> "$d · \"$label\" → " + g.map { "${it.id}=${it.icon}" } }
        }
        assertEquals(
            emptyList<String>(), mismatched,
            "cùng một việc mà hai ô hai hình ⇒ người dùng đọc ra hai việc; khác biệt xem/bấm đã ở dòng phụ rồi",
        )
    }

    // ── 2 · Ba lỗi ĐO ĐƯỢC trên ảnh, khoá từng cái một ──────────────────────────────────────────────

    /**
     * Owner báo đích danh: *"3 ô rpm/mô-men dùng glyph ắc-quy"*. Nguyên nhân: `ic_motor` cũ vẽ **hộp bo góc có cực
     * lồi bên phải** — ở cỡ ô 40dp nó đọc ra đúng là cục ắc-quy. Bài canh **nghĩa**, không canh tên tệp: ba mã này
     * không được dùng chung hình với bất kỳ mã PIN nào.
     */
    @Test
    fun `rpm va mo-men KHONG dung hinh cua pin`() {
        val batteryIcons = listOf("soc", "soh_oem", "target_soc", "charging_pct", "is_charging")
            .map { CapabilityIcons.forTelemetry(it, Domain.ENERGY) }.toSet()
        listOf("motor_front_rpm", "motor_rear_rpm", "motor_front_torque").forEach { id ->
            val icon = CapabilityIcons.forTelemetry(id, Domain.DRIVETRAIN)
            assertTrue(icon !in batteryIcons, "$id mang hình của pin ($icon) — nó là chuyển động quay, không phải pin")
        }
        // Và vòng tua ≠ mô-men: cùng một trục, hai đại lượng khác nhau.
        assertEquals("ic-rpm", CapabilityIcons.forTelemetry("motor_front_rpm", Domain.DRIVETRAIN))
        assertEquals("ic-rpm", CapabilityIcons.forTelemetry("motor_rear_rpm", Domain.DRIVETRAIN))
        assertEquals("ic-torque", CapabilityIcons.forTelemetry("motor_front_torque", Domain.DRIVETRAIN))
        // Máy xăng ≠ mô-tơ điện: trên DM-i hai vòng tua nằm cạnh nhau.
        assertEquals("ic-engine", CapabilityIcons.forTelemetry("engine_rpm", Domain.DRIVETRAIN))
    }

    /** *"Còn đi được bao xa"* ≠ *"đã đi được bao xa"* — trước U6 cả sáu ô cùng hình con đường. */
    @Test
    fun `tam hoat dong KHAC quang duong da di`() {
        val range = listOf("ev_range_km", "fuel_range_km", "batt_range_bodywork")
            .map { CapabilityIcons.forTelemetry(it, Domain.ENERGY) }.toSet()
        val driven = listOf("odometer", "ev_mileage_km", "trip_km")
            .map { CapabilityIcons.forTelemetry(it, Domain.ENERGY) }.toSet()
        assertEquals(1, range.size, "ba mục tầm chạy phải cùng MỘT hình (chúng cùng khái niệm)")
        assertEquals(1, driven.size, "ba mục quãng đường đã đi phải cùng MỘT hình")
        assertTrue(range.first() != driven.first(), "tầm chạy và odo là hai câu hỏi khác nhau")
    }

    /** Trạng thái CỔNG sạc, trạng thái BỘ sạc, công suất, lượng đã nạp — bốn thứ, trước U6 là một tia sét. */
    @Test
    fun `ho sac tach thanh cac khai niem rieng`() {
        val ids = listOf("is_charging", "charge_power", "charging_capacity_kwh", "charging_state", "charger_work_state")
        val icons = ids.map { CapabilityIcons.forTelemetry(it, Domain.ENERGY) }
        assertTrue(
            icons.toSet().size >= 4,
            "họ sạc chỉ còn ${icons.toSet().size} hình cho ${ids.size} ô: ${ids.zip(icons)}",
        )
        assertTrue(
            CapabilityIcons.forTelemetry("charging_state", Domain.ENERGY) !=
                CapabilityIcons.forTelemetry("charger_work_state", Domain.ENERGY),
            "trạng thái cổng trên XE và trạng thái THIẾT BỊ sạc là hai câu trả lời khác nhau",
        )
    }

    /** Ba ô PM2.5 từng gần trùng cả TÊN lẫn HÌNH; hình phải tách trước, tên tách ở `TelemetryRegistry`. */
    @Test
    fun `ba o bui min khong con dung chung mot hinh voi nhau lan voi ion am`() {
        val icons = listOf("pm25_level", "pm25_value", "pm25_online", "anion_state")
            .map { CapabilityIcons.forTelemetry(it, Domain.CLIMATE) }
        assertTrue(icons.toSet().size >= 3, "bốn ô khí sạch chỉ còn ${icons.toSet().size} hình: $icons")
        assertEquals("ic-sensor", CapabilityIcons.forTelemetry("pm25_online", Domain.CLIMATE),
            "mục này nói về THIẾT BỊ (cảm biến sống hay chết), không nói về không khí")
        assertEquals("ic-leaf", CapabilityIcons.forTelemetry("anion_state", Domain.CLIMATE),
            "chiếc lá nay chỉ còn nghĩa 'đang làm sạch không khí'")
    }

    // ── 3 · Nợ của các nhóm chưa chạm: ghim số THẬT, tự rữa hai chiều ────────────────────────────────

    @Test
    fun `nhom chua chua phai ghim dung tran do duoc`() {
        pendingCeiling.forEach { (d, entry) ->
            val (ceiling, why) = entry
            val now = maxShare(d)
            assertTrue(why.length >= 40, "$d: ghi nợ thì phải nói được VÌ SAO chưa làm, không thì đây là chỗ cất nợ")
            assertTrue(
                ceiling > CapabilityIcons.MAX_PER_DOMAIN,
                "$d đã trong ngưỡng ⇒ bỏ khỏi danh sách nợ, đừng giữ một ngoại lệ không cần tới",
            )
            assertEquals(
                ceiling, now,
                "$d: trần đo được đã đổi ($ceiling → $now). Tốt lên thì HẠ số ghim (hoặc bỏ hẳn dòng này); " +
                    "xấu đi thì đây là hồi quy — ${shownIconUse(d).mapValues { it.value.size }}",
            )
        }
    }

    @Test
    fun `moi nhom deu duoc noi toi - khong nhom nao tang hinh`() {
        val covered = doneDomains.toSet() + pendingCeiling.keys
        val missed = Domain.TELEMETRY.filterNot { it in covered }
        assertEquals(
            emptyList<Domain>(), missed,
            "lĩnh vực telemetry không nằm trong nhóm 'đã chữa' lẫn nhóm 'còn nợ' ⇒ nó ra khỏi tầm quét mà " +
                "không ai quyết định gì: $missed",
        )
        assertTrue(
            doneDomains.none { it in pendingCeiling },
            "một nhóm không thể vừa đã-chữa vừa còn-nợ",
        )
    }
}
