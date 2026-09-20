package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceSynonyms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 1.85 · NỐI ROUTE ĐÃ RE TRÊN XE 2026-09-20 — mỗi ca khoá MỘT con số đo được ═══════════════════════════════
 *
 * Nguồn: `docs/diagnostics/oncar-1.84-session-2026-09-20.md` §3 (route GHI) + §4 (findings feature). Bốn việc của
 * lượt này, và ở đây là **hai chiều** cho từng việc — vì cả bốn đều thuộc họ lỗi *"compile xanh, rc=0, im lặng làm
 * sai"* mà chỉ người ngồi trong xe mới thấy:
 *
 *  1. **bụi mịn NGOÀI xe** — cùng getter với ô trong cabin, khác **chỉ số phần tử** (javadoc BYD: *"first = in,
 *     second = out"*). Ca nguy hiểm: lùi về ô [0] ⇒ hiện số TRONG cabin dưới nhãn *"ngoài xe"*.
 *  2. **khoá trẻ em** — hai bên là hai feature-id tách nhau, và giá trị ghi NGƯỢC (2 = bật).
 *  3. **nhiệt cài đặt** — đọc sai `area` (0) ⇒ sentinel ⇒ "—"; phải là `AC_TEMPERATURE_MAIN` = 1.
 *  4. **`hood`** — xoá hẳn, và "xoá" phải nghĩa là *biến mất khỏi mọi bảng*, không chỉ khỏi registry.
 *
 * (Vế `ac_auto` — gió tự động, cả hai chiều đều ĐẢO — nằm ở `ControlReadKeyTest` §5, cạnh các ca `readInverted`
 * khác để một người đọc về cờ đảo thấy hết họ ấy trong một chỗ.)
 */
class HalWire0920Test {

    // ══ 1 · BỤI MỊN NGOÀI XE = ô [1] của `getPM2p5Value()` ════════════════════════════════════════════════════

    /**
     * Một lời gọi HAL, hai datum, hai con số KHÁC nhau — và không được lẫn.
     *
     * Mồi mảng `[8, 22]` đúng hình dạng [ĐO car log 2026-09-20] `onPM2p5ValueChanged in:8 out:22`.
     */
    @Test fun `pm25 trong cabin lay o 0, ngoai xe lay o 1`() {
        val gw = FakeHalGateway(getters = mapOf("getPM2p5Value" to "[8, 22]"))
        val t = HalBindingTable(gw)
        assertEquals(8, t.readInt("pm25_value"), "ô trong cabin phải là phần tử ĐẦU")
        assertEquals(22, t.readInt("pm25_outside"), "ô ngoài xe phải là phần tử THỨ HAI")
        assertNotEquals(
            t.readInt("pm25_value"), t.readInt("pm25_outside"),
            "hai datum đọc ra CÙNG một số ⇒ chỉ số phần tử đã bị bỏ qua, và lỗi ấy hoàn toàn im lặng",
        )
    }

    /**
     * Cả hai datum đi qua **đúng một** getter — nếu ai đó "sửa" bằng cách bịa một getter tên `getOutCarPM2p5` thì
     * ca này đỏ. [ĐO nguồn] grep toàn bộ cây decompile + javadoc SDK: **không có** getter nào như thế.
     */
    @Test fun `bui mijn ngoai xe khong duoc bia mot getter rieng`() {
        val inside = TelemetryRegistry.byId("pm25_value")!!.bindingKey
        val outside = TelemetryRegistry.byId("pm25_outside")!!.bindingKey
        assertEquals("BYDAutoPM2p5Device.getPM2p5Value", outside)
        assertEquals(inside, outside, "hai ô bụi mịn phải dùng CÙNG getter; khác nhau chỉ ở chỉ số phần tử")
        assertEquals(1, HalReadTables.ARRAY_INDEX["pm25_outside"], "ngoài xe = ô thứ hai")
        assertNull(HalReadTables.ARRAY_INDEX["pm25_value"], "ô [0] đã có đường mặc định — khai lại là hai cơ chế cho một việc")
    }

    /**
     * ⚠⚠ **Giá trị KHÔNG-mảng + chỉ số > 0 ⇒ `null`, tuyệt đối không lùi về số thuần.**
     *
     * Đây là ca đắt nhất của cả lượt. Nếu một ROM/trim trả về **một số đơn** (chỉ đo trong cabin), đường lùi sẽ
     * hiện **số trong cabin dưới nhãn "ngoài xe"** — một con số sai mà trông như đang sống, tệ hơn hẳn "—". Đúng
     * họ lỗi *"nhãn hứa việc A, hiện việc B"* mà dự án đã trả giá ở nút *"Kính 50%"* và cặp `lock`/`door`.
     */
    @Test fun `khong phai mang thi o ngoai xe im lang chu KHONG lay so trong cabin`() {
        val single = HalBindingTable(FakeHalGateway(getters = mapOf("getPM2p5Value" to "8")))
        assertEquals(8, single.readInt("pm25_value"), "ô trong cabin vẫn đọc được từ một số đơn")
        assertNull(single.readInt("pm25_outside"), "một số đơn KHÔNG chứa số ngoài xe ⇒ phải là '—'")

        // Mảng ngắn hơn chỉ số — cùng một lẽ.
        val short = HalBindingTable(FakeHalGateway(getters = mapOf("getPM2p5Value" to "[8]")))
        assertNull(short.readInt("pm25_outside"), "mảng một ô ⇒ chưa có số ngoài xe")

        // Và phép thuần đứng riêng cũng phải nói đúng thế.
        assertNull(HalBindingTable.coerceIntAt("8", 1))
        assertNull(HalBindingTable.coerceIntAt(null, 1))
        assertEquals(8, HalBindingTable.coerceIntAt("8", 0), "chỉ số 0 vẫn nhận số thuần như cũ")
        assertEquals(22, HalBindingTable.coerceIntAt("[8, 22]", 1))
    }

    /** Dấu *"chưa kiểm trên xe"* phải còn: thứ tự phần tử là [ĐO nguồn], chưa phải [ĐO] trên xe owner. */
    @Test fun `bui mijn ngoai xe giu dau chua kiem tren xe`() {
        assertEquals(
            EvidenceTier.NEEDS_CAR, TelemetryRegistry.byId("pm25_outside")!!.tier,
            "chỉ được lên PROVEN sau khi một lượt đọc trên xe in ra cả hai ô",
        )
        assertEquals(
            EvidenceTier.PROVEN, TelemetryRegistry.byId("pm25_value")!!.tier,
            "tiền đề: ô trong cabin ĐÃ chạy thật — nên nếu ngoài xe câm thì đó là chuyện của chỉ số, không của getter",
        )
    }

    // ══ 2 · KHOÁ TRẺ EM — hai bên, hai id, giá trị NGƯỢC ═════════════════════════════════════════════════════

    @Test fun `hai ben khoa tre em tro toi hai feature-id KHAC nhau tren cung device`() {
        val l = ControlRegistry.byId("child_lock")!!
        val r = ControlRegistry.byId("child_lock_r")!!
        assertEquals("BYDAutoFeatureIds.Door.DOOR_LOCK_COMMAND_AREA_CHILDLOCK_LEFT_SET", l.bindingKey)
        assertEquals("BYDAutoFeatureIds.Door.DOOR_LOCK_COMMAND_AREA_CHILDLOCK_RIGHT_SET", r.bindingKey)
        assertNotEquals(
            l.bindingKey, r.bindingKey,
            "hai bên dùng CÙNG một lệnh ⇒ một trong hai nút nói dối (đúng họ lỗi `lock`/`door` một byte)",
        )
        listOf(l, r).forEach {
            assertEquals("BYDAutoDoorLockDevice", it.halDevice, "${it.id}: device đo được là DOOR_LOCK, không phải BODYWORK")
            assertEquals(EvidenceTier.PROVEN, it.tier, "${it.id}: [ĐO xe 2026-09-20] rc=0 + owner xác nhận cửa thật")
        }
    }

    /** ⚠ BẪY: ghi **2 → BẬT** · ghi **1 → TẮT** ([ĐO xe]). Đảo lại là *"bật khoá trẻ em"* mở chốt ra. */
    @Test fun `khoa tre em ghi 2 de BAT va 1 de TAT`() {
        listOf("child_lock", "child_lock_r").forEach { id ->
            val def = ControlRegistry.byId(id)!!
            assertEquals(listOf(2), HalBindingTable.writeArgs(def, 1).toList(), "$id: BẬT phải gửi 2")
            assertEquals(listOf(1), HalBindingTable.writeArgs(def, 0).toList(), "$id: TẮT phải gửi 1")
        }
    }

    /** Nhãn phải nói BÊN NÀO — một nút hứa cả xe mà khoá nửa xe là chỗ tệ nhất để hứa quá. */
    @Test fun `nhan khoa tre em noi ro ben nao`() {
        val l = ControlRegistry.byId("child_lock")!!
        val r = ControlRegistry.byId("child_lock_r")!!
        assertTrue(l.label.contains("trái"), "nhãn trái: '${l.label}'")
        assertTrue(r.label.contains("phải"), "nhãn phải: '${r.label}'")
        assertTrue(l.labelEn!!.contains("left", ignoreCase = true), "nhãn EN trái: '${l.labelEn}'")
        assertTrue(r.labelEn!!.contains("right", ignoreCase = true), "nhãn EN phải: '${r.labelEn}'")
        assertNotEquals(l.label, r.label)
    }

    /**
     * Cụm MƠ HỒ (*"khoá trẻ em"*, không nêu bên) trỏ về **một** nút — tiền lệ owner đã duyệt ở 1.80 cho *"mở
     * kính"* → kính LÁI. Bài này chặn hai hướng sai: để cụm ấy không trỏ đâu (thành NO_OBJECT), hoặc cho nó trỏ
     * tới nút PHẢI (một lựa chọn tuỳ tiện mà nhãn nút không giải thích được).
     */
    @Test fun `cum mo ho khoa tre em tro ve nut TRAI`() {
        assertTrue(
            "khoa tre em" in (VoiceSynonyms.CONTROL["child_lock"] ?: emptyList()),
            "cụm mơ hồ phải trỏ về nút trái",
        )
        assertTrue(
            "khoa tre em" !in (VoiceSynonyms.CONTROL["child_lock_r"] ?: emptyList()),
            "cụm mơ hồ KHÔNG được đồng thời trỏ về nút phải — hai nút tranh nhau một cụm là nhập nhằng im lặng",
        )
    }

    // ══ 3 · NHIỆT CÀI ĐẶT — đọc đúng `area` ══════════════════════════════════════════════════════════════════

    /**
     * [ĐO nguồn] javadoc BYD cho `getTemprature(int area)` liệt kê **MAIN(1) · DEPUTY(2) · REAR(3) · OUT(4)**; `0`
     * (`AC_TEMPERATURE_MAIN_DEPUTY`) là *type* của đường GHI `setAcTemperature`, không phải vùng ĐỌC ⇒ đọc area 0
     * trả `AC_COMMAND_INVALID_VALUE` và ô hiện "—" (đúng [ĐO xe 2026-09-20] *"nhiệt cài đặt = X"*).
     */
    @Test fun `nhiet cai dat doc area MAIN chu khong phai 0`() {
        assertEquals(1, HalReadTables.readArg("inside_temp"), "area 0 không phải vùng ĐỌC ⇒ sentinel ⇒ '—'")
        assertEquals(
            1, readPathOf("inside_temp")!!.arg,
            "đường đọc đã phân giải cũng phải mang area 1, không chỉ bảng tra",
        )
        // Nút `temp` mượn đường đọc của datum này và vẫn phải ra area 1 (nó đã tự ghi đè từ 1.69).
        assertEquals(1, readPathOf("temp")!!.arg, "nút nhiệt độ đọc cùng vùng với datum")
    }

    @Test fun `nhiet cai dat chay end-to-end qua area dung`() {
        // Gateway giả phân biệt theo arg: area 1 có số, area 0 thì không (đúng hành vi khung).
        val gw = FakeHalGateway(gettersByArg = mapOf("getTemprature" to mapOf(1 to "22", 0 to null)))
        assertEquals(22, HalBindingTable(gw).readInt("inside_temp"), "đọc area 1 phải ra 22")
    }

    // ══ 4 · `hood` XOÁ HẲN — phải biến mất khỏi MỌI bảng, không chỉ registry ══════════════════════════════════

    /**
     * [ĐO xe 2026-09-20 §4] owner xác nhận xe KHÔNG có ca-pô điện (chỉ cốp sau). Một mã chết còn sót trong một
     * bảng phụ là cách nó sống lại: `CONFIRM_REQUIRED`/`MOVES_SLOWLY` giữ tên thì người sau đọc ra *"nút này còn"*,
     * còn `VoiceSynonyms` giữ cụm thì `VoiceGrammarCoverageTest` đỏ vì cụm trỏ tới hư không.
     */
    @Test fun `hood bien mat khoi moi bang, khong chi khoi registry`() {
        assertNull(ControlRegistry.byId("hood"), "registry")
        assertNull(CapabilityCatalog.pick("hood"), "catalog (đường mà ô đã lưu tra vào)")
        assertNull(CapabilityCatalog.kindOf("hood"), "kindOf — cổng của thanh nút/ô")
        assertNull(CapabilityDescriptions.of("hood"), "diễn giải")
        assertNull(CapabilityCatalog.HIDDEN_FROM_PICKER["hood"], "bảng ẩn (mã đã xoá thì không còn gì để ẩn)")
        assertNull(VoiceSynonyms.CONTROL["hood"], "cách nói")
        assertTrue("hood" !in CtlSafetyPolicy.CONFIRM_REQUIRED, "cổng xác nhận")
        assertTrue("hood" !in CtlSafetyPolicy.REQUIRES_STATIONARY, "gate theo vận tốc")
        assertTrue("hood" !in CtlSafetyPolicy.MOVES_SLOWLY, "bộ phận chạy mô-tơ")
        assertTrue(
            ControlRegistry.ALL.none { it.bindingKey.contains("HOOD", ignoreCase = true) },
            "không nút nào còn trỏ tới lệnh ca-pô",
        )
    }

    /** Cốp thì **ở lại** đủ ba bảng — xoá ca-pô không được kéo theo thứ xe thật sự có. */
    @Test fun `cop van con nguyen trong ca ba bang an toan`() {
        assertTrue("trunk" in CtlSafetyPolicy.CONFIRM_REQUIRED)
        assertTrue("trunk" in CtlSafetyPolicy.REQUIRES_STATIONARY)
        assertTrue("trunk" in CtlSafetyPolicy.MOVES_SLOWLY)
        assertTrue(ControlRegistry.byId("trunk") != null)
    }

    // ══ 5 · Bất biến chung: không hai nút nào dùng chung MỘT lệnh với CÙNG tham số ════════════════════════════

    /**
     * Luật này sinh ra ở [P0] 2026-09-11 (`lock`/`door` gửi y hệt một byte cho hai nghĩa đối nghịch). Lượt 1.85
     * thêm **hai** nút mới nên phải soi lại cả bộ: hai mã cùng `bindingKey` thì tham số PHẢI khác.
     *
     * ⚠ Đây là bản THU HẸP của `ControlWriteArgsTest` (bài đó có danh sách cho phép đầy đủ) — ở đây chỉ soi các
     * mã mà lượt này chạm tới, để nếu đỏ thì biết ngay là do lượt này gây ra.
     */
    @Test fun `bon nut cua luot 1_85 khong trung lenh trung tham so`() {
        val touched = listOf("ac_auto", "child_lock", "child_lock_r", "trunk", "temp")
            .mapNotNull { ControlRegistry.byId(it) }
        val seen = mutableMapOf<String, String>()
        touched.forEach { def ->
            listOf(0, 1).forEach { primary ->
                val sig = "${def.bindingKey}|${HalBindingTable.writeArgs(def, primary).toList()}"
                val other = seen.put(sig, "${def.id}($primary)")
                assertNull(other, "trùng lệnh+tham số: ${def.id}($primary) và $other")
            }
        }
    }
}
