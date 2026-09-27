package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ UX8 — CHIP THANH TRÊN: trạng thái nói bằng HÌNH, không bằng CHỮ (họ thứ BA) ══════════════════════════════
 *
 * Owner 2026-09-27, nhìn thanh trên của xe thật: *"chip header → chế độ lấy gió đổi icon trong / ngoài, bỏ chữ
 * trong / ngoài đi chứ; check xem còn chip nào vẫn còn missing như thế này, mình có làm cái này 1 lần rồi mà?"*
 *
 * Lượt *"đã làm 1 lần"* là `docs/specs/kachi-datum-icon-consistency.html` (09-21/22) và nó phủ **hai** họ:
 *  • **BẬT/TẮT** → một hình, sáng/mờ theo [TelemetryView.onOff] (`TopStripTest.chip datum bat-tat…`);
 *  • **THANG MỨC** → một hình + số mức (`TopStripTest.chip ghe…`).
 *
 * Khe lọt là họ **HAI CHẾ ĐỘ**: *lấy gió trong* ↔ *ngoài* đều là trạng thái đang chạy, **không cái nào là "tắt"**,
 * nên sáng/mờ không nói được cái nào ⇒ chip đành in chữ. Bài này khoá ba việc:
 *  1. chip của datum hai chế độ đổi **hình** theo chế độ và **không còn chữ** trạng thái (VI + EN);
 *  2. **KHÔNG datum chippable nào khác** còn in chữ trạng thái mà chưa được khai — đó chính là câu *"check xem
 *     còn chip nào vẫn còn missing"*, và nó được trả lời bằng **máy quét nguồn**, không bằng mắt (§13.6);
 *  3. hai họ cũ **vẫn đúng** — đo lại, không giả định.
 */
class TopStripStateIconTest {

    private fun one(id: String, status: CarStatus, labels: Boolean = true): ChipView =
        TopStripChips.render(TopStripConfig(listOf(id), showLabels = labels), status).single()

    private fun cycle(recirc: Boolean?) = CarStatus(climate = CarStatus.Climate(recircOn = recirc))

    // ── 1. `ac_cycle` — hình đổi theo chế độ, chữ "Trong"/"Ngoài" biến mất ──────────────────────────────────

    /**
     * [ĐO đọc mã 2026-09-27] trước lượt này chip in `"Chế độ lấy gió · Trong"` (`TelemetryReadout.format` đổi
     * `recircOn` thành chữ qua [Strings.t]). Nay: nhãn ngắn + **hình của đúng chế độ**.
     *
     * Hình *trong* dùng lại `ic-recirc` — chính hình của nút *"Lấy gió trong"* ([ControlRegistry]) ⇒ chip · ô nút ·
     * bộ chọn vẫn MỘT hình cho MỘT khái niệm (spec kachi-datum-icon-consistency R1).
     */
    @Test
    fun `chip che do lay gio doi HINH theo che do, va khong con chu Trong-Ngoai`() {
        val inside = one("ac_cycle", cycle(true))
        val outside = one("ac_cycle", cycle(false))
        assertEquals("ic-recirc", inside.icon, "lấy gió TRONG dùng lại đúng hình của nút Lấy gió trong")
        assertEquals("ic-air-fresh", outside.icon, "lấy gió NGOÀI là hình riêng, cặp đôi cùng khoang xe")
        assertNotEquals(inside.icon, outside.icon, "hai chế độ mà cùng hình thì chip không nói được gì")

        val short = TelemetryRegistry.byId("ac_cycle")!!.shortLabel
        listOf(inside, outside).forEach { c ->
            assertEquals(short, c.text, "chip hai chế độ chỉ còn NHÃN — hình nói chế độ, thấy: '${c.text}'")
            setOf("Trong", "Ngoài", "Recirc", "Fresh").forEach { w ->
                assertFalse(c.text.contains(w), "chip KHÔNG được còn chữ '$w', thấy: '${c.text}'")
            }
            assertEquals(ChipTone.ACTIVE, c.tone, "đọc được ⇒ sáng: cả hai chế độ đều là trạng thái SỐNG")
        }
        // Câu cho trình đọc màn hình VẪN đủ chữ: người không thấy hình mất hết thông tin nếu chỉ còn nhãn.
        assertEquals("Chế độ lấy gió: Trong", inside.desc)
        assertEquals("Chế độ lấy gió: Ngoài", outside.desc)
    }

    /**
     * Chưa đọc được ⇒ hình **trung tính** + [ChipTone.NEUTRAL]: vẽ sẵn một chiều gió lúc chưa biết là một lời
     * khẳng định không ai đo được (luật *"không biết ≠ đang tắt"* của thanh trên). Và **không** hiện `"· —"` —
     * cùng quyết định B10 (owner 2026-09-23) đã áp cho datum bật/tắt.
     */
    @Test
    fun `chua doc duoc che do lay gio thi hinh TRUNG TINH, khong ve bua mot chieu gio`() {
        val c = one("ac_cycle", cycle(null))
        assertEquals("ic-air-intake", c.icon, "chưa biết ⇒ khoang xe không có đầu mũi tên")
        assertNotEquals("ic-recirc", c.icon, "hình *trong* lúc chưa đọc = nói dối")
        assertNotEquals("ic-air-fresh", c.icon, "hình *ngoài* lúc chưa đọc = nói dối")
        assertEquals(ChipTone.NEUTRAL, c.tone, "chưa đọc ⇒ trung tính, KHÔNG mờ (mờ = 'đang tắt')")
        assertEquals(TelemetryRegistry.byId("ac_cycle")!!.shortLabel, c.text)
        assertFalse(c.text.contains("—"), "B10: không có dấu gạch vô nghĩa, thấy: '${c.text}'")
    }

    @Test
    fun `tat nhan thi chip hai che do chi con HINH`() {
        val c = one("ac_cycle", cycle(false), labels = false)
        assertEquals("", c.text, "tắt nhãn + hai chế độ ⇒ chip chỉ-hình")
        assertEquals("ic-air-fresh", c.icon)
        assertTrue(c.desc.isNotEmpty(), "câu cho trình đọc màn hình không được rỗng theo")
    }

    /** Bản **tiếng Anh** là đường riêng (nhãn đi qua `shortEn`), và chữ `Recirc`/`Fresh` cũng không được lọt ra. */
    @Test
    fun `chip hai che do noi tieng Anh bang nhan EN, khong in Recirc hay Fresh`() {
        try {
            Strings.current = Lang.EN
            val inside = one("ac_cycle", cycle(true))
            assertEquals("Air intake", inside.text, "nhãn EN ngắn của `ac_cycle`")
            assertEquals("ic-recirc", inside.icon)
            assertEquals("Recirculation mode: Recirc", inside.desc, "câu đọc vẫn đủ, bằng EN")
            val outside = one("ac_cycle", cycle(false))
            assertEquals("Air intake", outside.text)
            assertEquals("ic-air-fresh", outside.icon)
        } finally {
            Strings.current = Lang.VI
        }
    }

    // ── 2. `pm25_online` — cùng cơ chế, đúng một dòng khai thêm ─────────────────────────────────────────────

    /**
     * Datum **liveness** (cảm biến còn trả lời không), không phải công tắc — nên nó không vào [TelemetryReadout]
     * `boolOf` (KDoc ở đó cấm mở phạm vi) mà vào bảng hai chế độ.
     *
     * Trạng thái **đáng báo** chỉ có một (*chết*) nên nó là cái mang hình riêng; *còn sống* và *chưa đọc* dùng
     * chung hình cảm biến và phân biệt bằng **sắc thái** — đúng cách datum bật/tắt đang làm.
     */
    @Test
    fun `chip cam bien bui min doi hinh khi cam bien chet, khong in chu Co-Khong`() {
        val alive = one("pm25_online", CarStatus(climate = CarStatus.Climate(pm25Online = true)))
        val dead = one("pm25_online", CarStatus(climate = CarStatus.Climate(pm25Online = false)))
        val unread = one("pm25_online", CarStatus())
        assertEquals("ic-sensor", alive.icon)
        assertEquals("ic-sensor-off", dead.icon, "cảm biến chết = hình gạch chéo, không phải chữ 'Không'")
        assertEquals("ic-sensor", unread.icon, "chưa đọc dùng chung hình với ca LÀNH, khác nhau ở sắc thái")
        assertEquals(ChipTone.NEUTRAL, unread.tone)
        assertEquals(ChipTone.ACTIVE, alive.tone)
        val short = TelemetryRegistry.byId("pm25_online")!!.shortLabel
        listOf(alive, dead, unread).forEach { c ->
            assertEquals(short, c.text, "thấy: '${c.text}'")
            setOf("Có", "Không", "Yes", "No").forEach { w ->
                assertFalse(c.text.contains(w), "chip KHÔNG được còn chữ '$w', thấy: '${c.text}'")
            }
        }
        assertEquals("Cảm biến bụi mịn: Không", dead.desc, "câu đọc vẫn nói đủ")
    }

    // ── 3. Chữ `AUTO` trên chip viết THƯỜNG (owner 2026-09-27) ──────────────────────────────────────────────

    /**
     * *"gió auto chạy ngon, nhưng cần đổi chữ AUTO thành viết thường, không cần viết hoa — **trên header chip
     * thôi**"*. Nên bài này đo CẢ HAI vế: chip xuống chữ thường, **ô nút giữ nguyên** `"AUTO"` viết hoa.
     */
    @Test
    fun `chip gio viet auto thuong, o NUT van giu AUTO viet hoa`() {
        fun wind(level: Int?, autoRaw: Int?) =
            one("ac_wind", CarStatus(climate = CarStatus.Climate(fanLevel = level, acWindAutoRaw = autoRaw)))
        assertEquals("Gió · auto 1", wind(1, 0).text, "đang AUTO + đọc được mức ⇒ chữ thường + số THẬT")
        assertEquals("Gió · auto", wind(null, 0).text, "AUTO mà mức chưa về")
        assertEquals("Gió · 2", wind(2, null).text, "chưa đọc được cờ auto ⇒ tuyệt đối không nói auto")
        assertFalse(wind(1, 0).text.contains(ClimateAuto.AUTO), "chip KHÔNG còn chữ AUTO viết hoa")
        // Câu cho trình đọc màn hình đọc GIÁ TRỊ GỐC (nó là chữ của bề mặt rộng) — không phải bản viết thường.
        assertTrue(wind(1, 0).desc.contains("AUTO 1"), "thấy: '${wind(1, 0).desc}'")

        // Ô NÚT: cùng khái niệm, khác bề mặt — giữ nguyên chữ của màn AC gốc trên xe.
        val fan = ControlRegistry.byId("fan")!!
        assertEquals(ClimateAuto.AUTO, ControlVisuals.stepText(fan, 3, autoOn = true), "ô nút KHÔNG đổi")
        assertEquals("auto", ClimateAuto.narrowAuto(ClimateAuto.AUTO), "phép hạ chữ sống đúng một bản")
    }

    // ── 4. SOÁT TOÀN BỘ — "còn chip nào vẫn còn missing như thế này?" ───────────────────────────────────────

    /**
     * Datum đặt được lên thanh trên mà chữ giá trị của nó là một **từ trạng thái** (không phải số/đơn vị) thì
     * PHẢI thuộc một trong ba họ đã có luật, hoặc được **khai hoãn kèm lý do** ở [DEFERRED].
     *
     * ## Vì sao quét NGUỒN chứ không đọc mắt
     * Câu hỏi của owner (*"còn chip nào vẫn còn missing"*) sẽ quay lại mỗi lần ai đó thêm một datum enum. Một bảng
     * kê viết tay trả lời được đúng một lần rồi rữa; máy quét thì đỏ ngay ở lần thêm thứ hai (CLAUDE.md §10).
     *
     * Nhận diện: nhánh của [TelemetryReadout] `format` gọi một hàm **đổi cờ/mã thành CHỮ** (`yesNo` · `openShut` ·
     * `levelText`) hoặc dựng chữ tại chỗ bằng [Strings.t]. Ba hàm ấy là toàn bộ đường sinh chữ trạng thái trong
     * tệp — datum SỐ không đi qua chúng (nó `toString()`/làm tròn).
     */
    @Test
    fun `KHONG chip nao con in chu trang thai ma chua duoc khai (soat bang may)`() {
        // `codeOf` (đã bỏ chú thích): phép đếm ngoặc không bị một dấu `{` trong câu chữ làm lệch, và không nhánh
        // nào bị nhận diện qua một dòng đã bị bình luận ra.
        val body = SourceRoots.body(
            SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/TelemetryReadout.kt"),
            "private fun format(id: String, s: CarStatus): String? = when (id) {",
        )
        val marks = Regex("""^\s*"([a-z0-9_]+)"\s*->""", RegexOption.MULTILINE).findAll(body).toList()
        assertTrue(marks.size > 40, "chỉ đọc được ${marks.size} nhánh — bài đang quét vùng SAI (quét tràn = test giả)")
        val wordy = marks.mapIndexedNotNull { i, m ->
            val end = marks.getOrNull(i + 1)?.range?.first ?: body.length
            val branch = body.substring(m.range.first, end)
            m.groupValues[1].takeIf { listOf("yesNo(", "openShut(", "levelText(", "Strings.t(").any(branch::contains) }
        }
        assertTrue(wordy.size >= 10, "chỉ thấy ${wordy.size} datum in chữ trạng thái — phép nhận diện đã hỏng")

        val missing = wordy.filterNot { id ->
            TopStripConfig.isChippable(id).not() ||                 // không lên được thanh trên ⇒ không phải việc ở đây
                TelemetryReadout.isOnOff(id) ||                     // họ 1 — sáng/mờ (spec icon-consistency R3)
                CapabilityDots.maxLevel(id) >= 1 ||                 // họ 2 — số mức + chấm (R4)
                CapabilityIcons.hasStateIcons(id) ||                // họ 3 — hình theo chế độ (UX8, lượt này)
                id in DEFERRED
        }
        assertEquals(
            emptyList<String>(), missing,
            "datum in CHỮ trạng thái trên chip mà chưa có luật nào phủ — thêm hình theo chế độ " +
                "(CapabilityIcons.STATE) hoặc khai hoãn kèm lý do ở DEFERRED",
        )
        // Khai hoãn phải là một QUYẾT ĐỊNH đọc được, không phải một cái tên bỏ vào cho test xanh.
        DEFERRED.forEach { (id, why) ->
            assertNotNull(TelemetryRegistry.byId(id), "$id không còn trong bộ đăng ký — bỏ khỏi DEFERRED")
            assertTrue(why.length >= 40, "$id: lý do hoãn quá mỏng ('$why')")
            assertFalse(CapabilityIcons.hasStateIcons(id), "$id đã có hình theo chế độ ⇒ bỏ khỏi DEFERRED")
        }
    }

    /**
     * Đã SOÁT, chưa làm — mỗi dòng là một quyết định, kèm điều kiện mở khoá (`.kiro/steering/trace-den-tan-cung`:
     * *"không thể" phải kèm bằng chứng + điều kiện mở khoá*). Bảng đầy đủ:
     * `docs/diagnostics/offcar-2026-09-26/ux-ux8-header-state-icons.md`.
     */
    private val DEFERRED: Map<String, String> = mapOf(
        "door_lf" to "cửa MỞ là việc đáng BÁO (GroupBoard xếp ALERT) chứ không phải một chế độ; hình cửa " +
            "đóng/mở thuộc bộ sinh KHÁC (gen-car.py từ design/car/top.svg) ⇒ cần 5 biến thể mới + một lượt " +
            "nhìn trên xe trước khi bỏ chữ 'Mở'. Mở khoá: owner duyệt cặp hình cửa ở lượt xe kế",
        "door_rf" to "cùng lý do với door_lf — bốn cửa là một họ, làm thì làm cả họ trong một lượt (cùng bộ " +
            "sinh gen-car.py, cùng lượt nhìn trên xe). Mở khoá: cùng door_lf",
        "door_lr" to "cùng lý do với door_lf — bốn cửa là một họ, làm thì làm cả họ trong một lượt (cùng bộ " +
            "sinh gen-car.py, cùng lượt nhìn trên xe). Mở khoá: cùng door_lf",
        "door_rr" to "cùng lý do với door_lf — bốn cửa là một họ, làm thì làm cả họ trong một lượt (cùng bộ " +
            "sinh gen-car.py, cùng lượt nhìn trên xe). Mở khoá: cùng door_lf",
        "sunroof_state" to "cùng họ MỞ/ĐÓNG với bốn cửa và cùng bộ sinh hình xe (gen-car.py); ngoài ra xe owner " +
            "KHÔNG có cửa sổ trời ([ĐO] 2026-09-25 getSunroofPosition = 65535) nên không đo được trên xe. " +
            "Mở khoá: một xe có cửa sổ trời, hoặc owner chấp nhận hình chưa đo",
        "ac_mode_auto" to "chữ giá trị là 'AUTO' — một dấu BỐN KÝ TỰ mà chính màn AC gốc của xe dùng, và owner " +
            "2026-09-27 xin GIỮ nó (chỉ viết thường). Bộ icon chưa có cặp hình tự-động/chỉnh-tay. " +
            "Mở khoá: owner chốt một cặp hình thay được chữ AUTO",
        "ac_wind_auto" to "cùng câu hỏi với ac_mode_auto ('đang tự động hay chỉnh tay') và cùng chữ AUTO; hơn " +
            "nữa chip gió (ac_wind) đã trả lời đúng việc ấy bằng 'auto n' nên một cặp hình mới sẽ là bề mặt " +
            "THỨ BA cho một sự thật. Mở khoá: cùng ac_mode_auto",
    )

    // ── 5. Hai họ CŨ vẫn đúng — đo lại, không giả định ──────────────────────────────────────────────────────

    /**
     * 13 datum BẬT/TẮT: bài cũ (`TopStripTest.chip datum bat-tat…`) đo **một** đại diện (sấy kính). Owner hỏi
     * *"còn chip nào vẫn còn missing như thế này"* ⇒ đo **cả mười ba**, ở cả hai trạng thái thật.
     */
    @Test
    fun `ca 13 chip bat-tat van khong in chu Bat-Tat - do lai ca ho`() {
        fun all(on: Boolean) = CarStatus(
            climate = CarStatus.Climate(acOn = on, anionOn = on, defrostFrontOn = on, defrostRearOn = on),
            body = CarStatus.Body(emergencyAlarm = on),
            lights = CarStatus.Lights(
                lowBeam = on, highBeam = on, frontFog = on, rearFog = on,
                leftTurn = on, rightTurn = on, sideLight = on, drl = on,
            ),
        )
        val ids = TelemetryRegistry.ALL.map { it.id }.filter { TelemetryReadout.isOnOff(it) }
        assertEquals(13, ids.size, "bản kê bật/tắt đổi số ⇒ bài này phải được đọc lại cùng lượt")
        listOf(true to ChipTone.ACTIVE, false to ChipTone.INACTIVE).forEach { (on, tone) ->
            ids.forEach { id ->
                val c = one(id, all(on))
                assertEquals(TelemetryRegistry.byId(id)!!.shortLabel, c.text, "$id (bật=$on) thấy: '${c.text}'")
                assertEquals(tone, c.tone, "$id (bật=$on): trạng thái phải nằm ở SẮC THÁI")
                assertNotNull(c.icon, "$id: trạng thái nằm ở icon ⇒ phải có icon")
            }
        }
    }

    /** Bốn datum THANG MỨC: chỉ còn nhãn + SỐ, không còn chữ *"Tắt"/"Mức n"* trên chip. */
    @Test
    fun `ca 4 chip thang muc van khong in chu Muc hay Tat`() {
        val ids = TelemetryRegistry.ALL.map { it.id }.filter { CapabilityDots.maxLevel(it) >= 1 }
        assertEquals(4, ids.size, "bốn datum ghế (lái + phụ, sưởi + mát)")
        val status = CarStatus(
            climate = CarStatus.Climate(
                seatHeatRaw = 3, seatVentRaw = 1, seatHeatRRaw = 1, seatVentRRaw = 3,
            ),
        )
        ids.forEach { id ->
            val c = one(id, status)
            setOf("Mức", "Level", "Tắt", "Off").forEach { w ->
                assertFalse(c.text.contains(w), "$id KHÔNG được còn chữ '$w', thấy: '${c.text}'")
            }
        }
    }

    // ── 6. Bảng khai phải LÀNH — không datum nào rơi vào hai họ ─────────────────────────────────────────────

    @Test
    fun `bang hinh theo trang thai khong chong len hai ho cu, va moi hinh la mot ten khac nhau`() {
        val table = CapabilityIcons.stateIconTable()
        assertTrue(table.isNotEmpty(), "bảng rỗng = lượt UX8 đã bị gỡ mất")
        table.forEach { (id, s) ->
            assertNotNull(TelemetryRegistry.byId(id), "$id không có trong bộ đăng ký telemetry")
            assertFalse(TelemetryReadout.isOnOff(id), "$id đã là datum BẬT/TẮT — hai luật cùng nói về một ô")
            assertEquals(0, CapabilityDots.maxLevel(id), "$id đã chạy theo THANG MỨC — hai luật cùng nói về một ô")
            assertEquals(
                listOf(0, 1), s.icons.keys.sorted(),
                "$id: mã trạng thái phải là 0/1 liên tục — mã thưa là dấu hiệu bảng đọc và bảng hình đã lệch",
            )
            assertEquals(s.icons.size, s.icons.values.toSet().size, "$id: hai chế độ mang CÙNG một hình ⇒ vô nghĩa")
            (s.icons.values + s.unknown).forEach { ic ->
                assertTrue(ic.startsWith("ic-"), "$id: '$ic' không phải tên hình")
            }
            // Datum nào khai hình thì bảng ĐỌC phải trả được mã — bằng không con số không ai sinh ra và chip
            // lặng lẽ đứng ở hình trung tính mãi mãi (CLAUDE.md §8: hàm mới phải có chỗ gọi thật).
            assertNotNull(
                TelemetryReadout.of(id, CarStatus())?.let { CapabilityIcons.forState(id, it.state) },
                "$id: không tra ra hình nào",
            )
        }
        // Chiều ngược (*"bảng ĐỌC có sinh ra mã cho mọi datum đã khai hình không"*) đo ở
        // `TelemetryReadoutTest.hinh trang thai va chu trang thai khong bao gio lech` — ở đó có nguồn mồi
        // wiredStatus nên phép đo là THẬT, không phải đọc lại chính bảng khai.
    }
}
