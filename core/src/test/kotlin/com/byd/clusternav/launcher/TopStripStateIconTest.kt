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
     * PHẢI thuộc một trong **BỐN** họ đã có luật. Từ 2.76 bảng hoãn [DEFERRED] **RỖNG** — không còn datum nào
     * "biết là thiếu nhưng để sau".
     *
     * ## Vì sao quét NGUỒN chứ không đọc mắt
     * Câu hỏi của owner (*"còn chip nào vẫn còn missing"*) sẽ quay lại mỗi lần ai đó thêm một datum enum. Một bảng
     * kê viết tay trả lời được đúng một lần rồi rữa; máy quét thì đỏ ngay ở lần thêm thứ hai (CLAUDE.md §10).
     *
     * Nhận diện: nhánh của [TelemetryReadout] `format` gọi một hàm **đổi cờ/mã thành CHỮ** (`yesNo` · `openShut` ·
     * `levelText` · `TelemetryEnums.text`) hoặc dựng chữ tại chỗ bằng [Strings.t]. Bốn hàm ấy là toàn bộ đường sinh
     * chữ trạng thái trong tệp — datum SỐ không đi qua chúng (nó `toString()`/làm tròn).
     *
     * ## Bốn họ
     *  1. BẬT/TẮT → sáng/mờ ([TelemetryReadout.isOnOff]) · 2. THANG MỨC → số ([CapabilityDots.maxLevel]) ·
     *  3. HAI CHẾ ĐỘ → cặp hình ([CapabilityIcons.hasStateIcons]) · 4. **NHIỀU CHẾ ĐỘ (≥ 3)** → CHỮ đã dịch từ bảng
     *  OEM ([TelemetryEnums], 2.76 R8): 4–6 hình cho một khái niệm là hoa văn nền, chữ là câu trả lời đúng —
     *  nhưng phải là chữ có nghĩa (*"Sẵn sàng"*), không phải mã thô (*"2"*), và mã lạ phải nói *"mã N"*.
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
            m.groupValues[1].takeIf {
                listOf("yesNo(", "openShut(", "levelText(", "Strings.t(", "TelemetryEnums.text(").any(branch::contains)
            }
        }
        assertTrue(wordy.size >= 10, "chỉ thấy ${wordy.size} datum in chữ trạng thái — phép nhận diện đã hỏng")
        // 2.76: hai datum mã thô [P3 UX8] phải LỘ RA ở đây (chúng đi qua bảng enum) — nếu không thì bài đang mù
        // đúng họ thứ tư mà lượt này thêm.
        assertTrue("power_level" in wordy && "headlight_feedback" in wordy, "họ 4 không được nhận diện: $wordy")

        val missing = wordy.filterNot { id ->
            TopStripConfig.isChippable(id).not() ||                 // không lên được thanh trên ⇒ không phải việc ở đây
                TelemetryReadout.isOnOff(id) ||                     // họ 1 — sáng/mờ (spec icon-consistency R3)
                // họ 2 — MỨC-TRONG-HÌNH (2.76 L7): datum thang mức PHẢI có họ hình theo mức khai ở
                // `CapabilityIcons.LEVEL` (khoá = hình của nút nó trỏ tới); còn "một hình + con số" (R4 cũ) từ nay
                // là thiếu — owner 27/09 đã gạch con số.
                (CapabilityDots.maxLevel(id) >= 1 && CapabilityDots.iconOverride(id) in CapabilityIcons.levelIconTable()) ||
                CapabilityIcons.hasStateIcons(id) ||                // họ 3 — hình theo chế độ (UX8)
                TelemetryEnums.size(id) >= 3 ||                     // họ 4 — chữ từ bảng OEM ≥ 3 trạng thái (2.76 R8)
                id in DEFERRED
        }
        assertEquals(
            emptyList<String>(), missing,
            "datum in CHỮ trạng thái trên chip mà chưa có luật nào phủ — thêm hình theo chế độ " +
                "(CapabilityIcons.STATE), bảng enum ≥ 3 mục (TelemetryEnums), hoặc khai hoãn kèm lý do ở DEFERRED",
        )
        // Khai hoãn phải là một QUYẾT ĐỊNH đọc được, không phải một cái tên bỏ vào cho test xanh.
        DEFERRED.forEach { (id, why) ->
            assertNotNull(TelemetryRegistry.byId(id), "$id không còn trong bộ đăng ký — bỏ khỏi DEFERRED")
            assertTrue(why.length >= 40, "$id: lý do hoãn quá mỏng ('$why')")
            assertFalse(CapabilityIcons.hasStateIcons(id), "$id đã có hình theo chế độ ⇒ bỏ khỏi DEFERRED")
        }
    }

    /**
     * ═══ 2.76 — RỖNG, có chủ ý ═══════════════════════════════════════════════════════════════════════════════
     *
     * Bảng này từng có 7 mục (2.75: cửa ×4 · cửa sổ trời · `ac_mode_auto` · `ac_wind_auto`), mỗi mục kèm điều kiện
     * mở khoá. Owner 2026-09-27: *"làm hết tất cả off-car nợ … không kéo dài ra nữa"* ⇒ cả bảy đóng trong 2.76
     * (spec `kachi-276-closing.html` R8/R9): cửa + cửa sổ trời sang họ 3 với cặp hình MỞ/ĐÓNG sinh từ `gen-car.py`;
     * hai chỉ báo AUTO sang họ 3 với cặp `ic-mode-auto`/`ic-mode`. Giữ bảng (thay vì xoá) để nợ MỚI, nếu có, vẫn
     * phải ghi lý do tại chỗ — và bài [bang hoan RONG] đỏ nếu ai bỏ mục vào mà không mở lại cuộc bàn với owner.
     * Bảng đầy đủ + bằng chứng: `docs/diagnostics/offcar-2026-09-27/chips-icons-close.md`.
     */
    private val DEFERRED: Map<String, String> = emptyMap()

    @Test
    fun `bang hoan RONG tu 2_76 - moi datum in chu trang thai deu co luat that`() {
        assertTrue(DEFERRED.isEmpty(), "2.76 đóng hết nợ UX8; mục mới ở đây cần owner duyệt: ${DEFERRED.keys}")
    }

    // ── 4b–4d (2.76 R8/R9: cửa · cửa sổ trời · tự động/tay · họ 4 mã→chữ) tách theo VAI sang
    //    `TopStripStateIcon276Test` — tệp này giữ luật chung + máy soát; tệp kia giữ các ca của lượt đóng 2.76.

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

    // ── 5b. 2.76 L7 — họ THANG MỨC nay là MỨC-TRONG-HÌNH (owner 27/09: "không cần số 1-2") ─────────────────────

    /**
     * Cả **bốn** datum ghế × **ba** mức: mức 1 = hình `-1` (một dấu), mức 2 = hình khái niệm (hai dấu), mức 0 = hình
     * khái niệm + MỜ; **không một chữ số nào** trên chip ở mức 1/2. Chưa đọc ⇒ `"· —"` + trung tính như cũ. Đo cả ghế
     * phụ và cả mát lẫn sưởi để không một nhánh nào ăn may nhờ một cặp mã trùng.
     */
    @Test
    fun `ca 4 chip thang muc noi MUC bang HINH - moi muc 0-1-2 x suoi-mat x lai-phu`() {
        // [ĐO xe 09-17] thang ghế: raw 1 = TẮT · 2 = mức 1 · 3 = mức 2 (ControlLevels; `seath` [SUY] cùng thang).
        fun status(raw: Int?) = CarStatus(climate = CarStatus.Climate(
            seatHeatRaw = raw, seatVentRaw = raw, seatHeatRRaw = raw, seatVentRRaw = raw,
        ))
        val families = mapOf(
            "seat_heat_state" to "ic-seat-heat-left", "seat_vent_state" to "ic-seat-vent-left",
            "seat_heat_state_r" to "ic-seat-heat-right", "seat_vent_state_r" to "ic-seat-vent-right",
        )
        assertEquals(families.keys, TelemetryRegistry.ALL.map { it.id }.filter { CapabilityDots.maxLevel(it) >= 1 }.toSet())
        families.forEach { (id, concept) ->
            val short = TelemetryRegistry.byId(id)!!.shortLabel
            val off = one(id, status(1)); val lv1 = one(id, status(2)); val lv2 = one(id, status(3)); val unread = one(id, status(null))
            assertEquals(concept, off.icon, "$id mức 0: hình khái niệm"); assertEquals(ChipTone.INACTIVE, off.tone)
            assertEquals("$concept-1", lv1.icon, "$id mức 1: hình MỘT dấu"); assertEquals(ChipTone.ACTIVE, lv1.tone)
            assertEquals(concept, lv2.icon, "$id mức 2: hình khái niệm = HAI dấu"); assertEquals(ChipTone.ACTIVE, lv2.tone)
            assertNotEquals(lv1.icon, lv2.icon, "$id: hai mức phải là hai hình")
            listOf(off, lv1, lv2).forEach { c -> assertEquals(short, c.text, "$id: chỉ nhãn, thấy '${c.text}'") }
            assertEquals("$short · ${TelemetryView.PLACEHOLDER}", unread.text); assertEquals(ChipTone.NEUTRAL, unread.tone)
            // Cùng bảng cho chip, ô nút, widget: hình mức 1 của datum = forLevel(hình NÚT của nó, 1).
            assertEquals(CapabilityIcons.forLevel(CapabilityDots.iconOverride(id), 1), lv1.icon)
        }
    }

    /**
     * Bảng `CapabilityIcons.LEVEL` phải LÀNH: khoá là hình của một nút THANG MỨC có `readKey`; số hình = số mức của nút
     * ấy; hình cao nhất = chính khoá (không tệp thứ ba y hệt); mọi tên khác nhau; mức 0/`null`/vượt bảng ⇒ `null`.
     */
    @Test
    fun `bang hinh theo muc lanh - khoa la hinh nut thang muc, so hinh bang so muc`() {
        val table = CapabilityIcons.levelIconTable()
        assertEquals(4, table.size, "bốn họ ghế (sưởi/mát × lái/phụ)")
        table.forEach { (concept, icons) ->
            val def = ControlRegistry.ALL.single { it.icon == concept }
            assertTrue(ControlVisuals.isLevelScale(def) && def.readKey.isNotEmpty(), "$concept: phải là nút thang mức có datum")
            assertEquals(ControlVisuals.tickCount(def), icons.byLevel.size, "$concept: một hình cho mỗi mức của nút")
            assertEquals(concept, icons.byLevel.last(), "$concept: hình khái niệm = mức cao nhất")
            assertEquals(icons.byLevel.size, icons.byLevel.toSet().size, "$concept: mỗi mức một hình khác nhau")
            icons.byLevel.forEach { assertTrue(it.startsWith("ic-"), "$it không phải tên hình") }
            assertEquals(null, CapabilityIcons.forLevel(concept, 0)); assertEquals(null, CapabilityIcons.forLevel(concept, null))
            assertEquals(null, CapabilityIcons.forLevel(concept, icons.byLevel.size + 1), "mức vượt bảng ⇒ null (chip in số)")
        }
        assertEquals(null, CapabilityIcons.forLevel("ic-defrost", 1), "hình không khai họ mức ⇒ null")
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

    /** Họ 4 cũng phải LÀNH: ≥ 3 mục (2 mục = họ 3, phải khai hình), có trong registry, không chồng ba họ kia. */
    @Test
    fun `bang enum nhieu che do khong chong len ba ho kia va co it nhat 3 muc`() {
        val ids = listOf(TelemetryEnums.POWER_LEVEL, TelemetryEnums.HEADLIGHT_MODE).map { it.id }
        assertEquals(listOf("power_level", "headlight_feedback"), ids)
        ids.forEach { id ->
            val n = TelemetryEnums.size(id)
            assertTrue(n >= 3, "$id: $n mục — 2 mục là họ HAI CHẾ ĐỘ, phải khai hình")
            assertNotNull(TelemetryRegistry.byId(id))
            assertFalse(TelemetryReadout.isOnOff(id)); assertEquals(0, CapabilityDots.maxLevel(id))
            assertFalse(CapabilityIcons.hasStateIcons(id), "$id: hình theo chế độ VÀ bảng chữ = hai luật một ô")
            assertEquals(TopStripConfig.isChippable(id), true)
        }
        assertEquals(0, TelemetryEnums.size("ac_cycle"), "datum hai chế độ không có bảng enum")
    }
}
