package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ H1 · T5 — NÚT ĐỌC BẰNG KHOÁ ĐỌC, KHÔNG BẰNG KHOÁ GHI ════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-live-state-ux.html` §2.2 · §4.3 · **V-u3**.
 *
 * ## [ĐO] bệnh bị khoá lại ở đây — tester 1.66: *"điều hoà chỉnh lung tung, quất một phát như lò heo quay"*
 * Hai lỗi chồng lên nhau:
 *  1. lệnh tương đối (*"tăng gió"*) cộng vào **mặc định trong RAM** (gió 4 · nhiệt 22) chứ không vào mức thật —
 *     [ĐO xe 2026-09-16] xe đang **gió 1**, nói *"tăng gió"*, lệnh bắn đi là **5**;
 *  2. đường đọc lại đi qua `ControlDef.bindingKey`, mà đó là khoá **GHI** (`fan` = `501219340` =
 *     `AC_WIND_LEVEL_SET`; `temp` = `BYDAutoAcDevice.setAcTemperature`) ⇒ đọc qua setter trả rác/null, nên lỗi (1)
 *     không thể tự chữa.
 *
 * Bài này khoá cả hai: gỡ dòng vá ở [readPathOf] ra thì `fan` lại chạm id `_SET` và ca dưới ĐỎ ngay.
 *
 * ## ⚠ BA nút còn RỖNG `readKey` — cố ý, và đây là chỗ ghi lại vì sao
 * `readl` · `sunshade` · `windows_all`. Cả ba còn ở mức [CHƯA BIẾT]/[SUY] ngay trong spec (§4.5): chưa ai đo được
 * một getter nào trả đúng thứ nhãn hứa, nên đoán một cái tên ở đây là dựng lại đúng gốc bệnh §2.2 dưới dạng khác.
 *
 * ## ✅ SÁU nút đã nối ở lượt T2 (2026-09-16) — mỗi cái một phép đo, không cái nào là suy luận
 * `seatc` `seath` `defrost` `defrost_rear` `ac_auto` `vol`, đo trên xe owner (DL3, 1.68 vehicleTest) ngày
 * **2026-09-16** (`docs/diagnostics/oncar-trace-2026-09-16b/README.md` §1 · `hal-reads.txt`):
 *  • `getSeatVentilatingState(1)` = **3** · `getSeatHeatingState(1)` = **1** — [ĐO] cả hai sống trên
 *    **`BYDAutoSettingDevice`**; gọi CÙNG tên ấy trên device AC trả `null` (`hal-reads.txt:10-11`). Số trả về là
 *    **mã mức của khung**, đổi sang mức người dùng bằng [ControlLevels] — và vì `seatc`/`seath` là nút bật/tắt nên
 *    [HalBindingTable.readState] quy tiếp về 0/1 (*"mức 0 = tắt"*);
 *  • `getAcDefrostState(1)` = 0 · `getAcDefrostState(2)` = 0 — một getter, hai **area**, nên tham số khai ở
 *    [HalReadTables.readArg] theo mã datum chứ không nhân đôi dòng registry;
 *  • `getAcControlMode()` = **0** = `AC_CTRLMODE_AUTO` (`ac/BYDAutoAcDevice.java:29-30`) trong khi owner xác nhận
 *    màn xe đang AUTO ⇒ đúng ca [ControlDef.readInverted];
 *  • `vol` → `AudioManager.getStreamVolume` — [BindingRoute.Local], KHÔNG chạm HAL xe (`BydHalGateway.localGet:126`).
 *
 * ⚠ Nửa **GHI** của `ac_auto` KHÔNG đổi một byte: feature `1324355606` không có trong `BYDAutoFeatureIds` của xe này,
 * và lệnh *"cấm bắn lệnh khí hậu theo phỏng đoán"* của spec còn nguyên hiệu lực. **Đọc được ≠ ghi được.**
 */
class ControlReadKeyTest {

    // ══ 1 · Bài chống gõ nhầm (spec V-u3) ═════════════════════════════════════════════════════════════════

    @Test
    fun `moi readKey phai tra ra mot datum CO THAT`() {
        val bad = ControlRegistry.ALL
            .filter { it.readKey.isNotBlank() && TelemetryRegistry.byId(it.readKey) == null }
            .map { "${it.id} → ${it.readKey}" }
        assertTrue(
            bad.isEmpty(),
            "readKey trỏ vào mã datum không tồn tại ⇒ readState trả null LẶNG LẼ (ô ⚠ mà không ai biết vì sao): $bad",
        )
    }

    @Test
    fun `readArg khong duoc mo coi - co tham so ma khong co duong doc la du lieu chet`() {
        val orphan = ControlRegistry.ALL.filter { it.readArg != null && it.readKey.isBlank() }.map { it.id }
        assertTrue(orphan.isEmpty(), "khai readArg mà không khai readKey thì tham số đó không ai đọc: $orphan")
    }

    // ⚠ (V) FEATURE-FILTER 2026-09-17: bài `readKey khong duoc tro vao datum doc theo phan tu mang` đã gỡ cùng
    // bảng `HalBindingTable.ARRAY_INDEX` — bảng ấy chỉ từng chứa `charging_eta_hour`/`charging_eta_min`, hai ô
    // owner chấm NO. Không còn datum nào đọc-theo-phần-tử-mảng ⇒ không còn cái bẫy để canh.

    // ══ 2 · Nút đọc bằng khoá ĐỌC, không bằng khoá GHI ════════════════════════════════════════════════════

    @Test
    fun `nut co readKey khong con di qua bindingKey`() {
        val wired = ControlRegistry.ALL.filter { it.readKey.isNotBlank() }
        assertTrue(wired.size >= 10, "tiền đề: phải có ít nhất 10 nút đã nối đường đọc, thấy ${wired.size}")
        // Ràng buộc đi kèm: mọi nút đã nối phải đọc RA được một đường (không có nút nào khai readKey mà
        // `readPathOf` vẫn trả null) — ca `moi readKey phai tra ra mot datum CO THAT` ở trên là nửa kia.
        wired.forEach { def ->
            val datum = TelemetryRegistry.byId(def.readKey)!!
            val path = readPathOf(def.id)!!
            assertEquals(datum.bindingKey, path.key, "${def.id} phải đọc bằng getter của datum ${datum.id}")
            assertEquals(datum.id, path.id, "${def.id} phải mang mã DATUM (để tra halDevice/INVALID_VALUES)")
            assertTrue(
                path.key != def.bindingKey,
                "${def.id} vẫn đang đọc qua khoá GHI '${def.bindingKey}' — đúng gốc bệnh §2.2",
            )
        }
    }

    @Test
    fun `nut chua co duong doc thi tra null, KHONG lui ve khoa GHI`() {
        // `readl` chưa khai readKey. Gateway giả dưới đây trả giá trị cho CẢ hai phía: nếu code lùi về bindingKey
        // (feature 1330643002) thì nó sẽ đọc ra 9 — một con số của cái khoá GHI, tức đúng thứ phải chặn.
        val gw = FakeHalGateway(features = mapOf(1330643002 to "int=9"))
        assertNull(readPathOf("readl"), "readl chưa khai readKey ⇒ không được phân giải ra đường đọc nào")
        assertNull(HalBindingTable(gw).readState("readl"))
        assertNull(HalBindingTable(gw).readState("khong_ton_tai"))
    }

    @Test
    fun `sau nut cua lich T2 da noi het, ba nut CHUA BIET van con rong`() {
        // Hai chiều, và cả hai đều là **hợp đồng với KDoc lớp này**: nối thiếu một nút T2 ⇒ ĐỎ (đúng phần scope
        // owner nói *"làm hết toàn bộ"*); lặng lẽ đoán một getter cho ba mã còn [CHƯA BIẾT] ⇒ cũng ĐỎ.
        listOf("seatc", "seath", "defrost", "defrost_rear", "ac_auto", "vol").forEach { id ->
            assertTrue(
                ControlRegistry.byId(id)!!.readKey.isNotBlank(),
                "$id nằm trong lịch T2 (getter đã ĐO trên xe 2026-09-16) mà vẫn chưa có đường đọc",
            )
        }
        listOf("readl", "sunshade", "windows_all").forEach { id ->
            assertEquals(
                "", ControlRegistry.byId(id)!!.readKey,
                "$id còn ở mức [CHƯA BIẾT]/[SUY] trong spec §4.5 — đoán một getter ở đây là dựng lại gốc bệnh §2.2",
            )
        }
    }

    /**
     * ═══ [SOÁT 1.69 · P1] ĐỘ PHỦ THẬT của đường đọc — con số, không phải cảm giác ═══════════════════════════
     *
     * Bản đầu của §Tasks T2 trong `docs/specs/kachi-live-state-ux.html` ghi *"51/54 nút có đường đọc; ba mã còn
     * rỗng"*. [ĐO] đếm bằng máy ngày 2026-09-17: **17/54**, còn **37** nút chưa có đường đọc — tức tài liệu báo
     * dư 34 nút. Đó đúng họ lỗi mà CLAUDE.md §2 cấm (một con số chưa đo được viết ra như đã đo) và nó nguy hiểm
     * hơn một bug: owner đọc "51/54" thì coi T2 đã xong và chuyển sang T3.
     *
     * Bài này ghim con số để nó **không thể** lệch với tài liệu lần nữa: nối thêm một nút ⇒ ca này ĐỎ ⇒ người nối
     * phải sửa dòng T2 trong CÙNG lượt (R2.1 — doc và code atomic).
     *
     * ⚠ Đếm theo *"phân giải ra được một đường đọc"* ([readPathOf]) chứ không chỉ theo `readKey`: một nút mà mã
     * của nó TRÙNG mã một datum cũng đọc được, và đó là đa số 11 nút ngoài 6 nút của lượt T2.
     */
    @Test
    fun `dung 17 tren 47 nut co duong doc`() {
        val wired = ControlRegistry.ALL.filter { readPathOf(it.id) != null }.map { it.id }
        val blind = ControlRegistry.ALL.map { it.id } - wired.toSet()
        // ⚠ (V) 2026-09-17: 54 → 47 nút (owner gỡ 7 nút NO). [ĐO] độ phủ GIỮ NGUYÊN **17** — cả 7 nút bị gỡ
        // đều nằm trong nhóm chưa có đường đọc, nên độ phủ tương đối còn TĂNG (17/54 → 17/47).
        assertEquals(47, ControlRegistry.ALL.size, "số nút đổi ⇒ đếm lại cả hai vế rồi sửa §Tasks T2 của spec")
        assertEquals(
            17, wired.size,
            "độ phủ đường đọc đổi (thấy ${wired.size}/47; chưa có đường đọc: ${blind.sorted()}). " +
                "Sửa dòng T2 trong docs/specs/kachi-live-state-ux.html NGAY trong lượt này (R2.1), đừng chỉ sửa số ở đây.",
        )
    }

    // ══ 3 · Định tuyến thật qua gateway giả ═══════════════════════════════════════════════════════════════

    @Test
    fun `doc fan di qua getAcWindLevel chu KHONG cham id AC_WIND_LEVEL_SET`() {
        // Gateway mồi CẢ hai: getter thật trả 1 ([ĐO xe 2026-09-16]), còn feature-id GHI 501219340 trả 7.
        // Đọc ra 7 nghĩa là code vẫn đang hỏi cái setter — ca này ĐỎ đúng lúc đó.
        val gw = FakeHalGateway(getters = mapOf("getAcWindLevel" to "1"), features = mapOf(501219340 to "int=7"))
        assertEquals(1, HalBindingTable(gw).readState("fan"))
    }

    @Test
    fun `doc temp dung area 1 chu khong dung area 0 cua datum`() {
        // [ĐO] `ac/BYDAutoAcDevice.java:689`: area ngoài 1..4 trả thẳng -2147482645 (SENTINEL_INVALID). Datum
        // `inside_temp` khai area 0, nên nút `temp` PHẢI ghi đè bằng 1 (= AC_TEMP_MAIN, ghế lái).
        val gw = FakeHalGateway(
            gettersByArg = mapOf("getTemprature" to mapOf(0 to "int=-2147482645", 1 to "25")),
        )
        val table = HalBindingTable(gw)
        assertEquals(25, table.readState("temp"))
        assertEquals(1, gw.getterArgs["getTemprature"], "phải gọi getTemprature(1)")
        assertEquals(1, ControlRegistry.byId("temp")!!.readArg)
    }

    @Test
    fun `nut khong ghi de thi muon luon tham so cua datum`() {
        // `win_lr` → datum `window_lr`, mà tham số area 3 đã khai một chỗ duy nhất ở HalBindingTable.readArg.
        val gw = FakeHalGateway(getters = mapOf("getWindowOpenPercent" to "98"))
        assertEquals(98, HalBindingTable(gw).readState("win_lr"))
        assertEquals(3, gw.getterArgs["getWindowOpenPercent"], "mượn area của datum window_lr")
        assertNull(ControlRegistry.byId("win_lr")!!.readArg, "không cần ghi đè thì để null — một con số, một chỗ")
    }

    @Test
    fun `off-car va sentinel deu ra null, khong ra so rac`() {
        assertNull(HalBindingTable(FakeHalGateway()).readState("fan"), "off-car ⇒ null (⚠), không phải 0")
        val sentinel = FakeHalGateway(getters = mapOf("getAcWindLevel" to "int=-2147482648 float=null buf=-"))
        assertNull(HalBindingTable(sentinel).readState("fan"))
    }

    // ══ 4 · Đường ĐỌC của datum KHÔNG đổi một byte ════════════════════════════════════════════════════════

    @Test
    fun `duong doc cua telemetry giu nguyen nhu 1_68`() {
        TelemetryRegistry.ALL.forEach { spec ->
            val p = readPathOf(spec.id)
            assertNotNull(p, "${spec.id} phải luôn phân giải được")
            assertEquals(spec.bindingKey, p!!.key, "${spec.id} đổi khoá đọc")
            assertEquals(spec.domain, p.domain)
            assertEquals(HalBindingTable.readArg(spec.id), p.arg, "${spec.id} đổi tham số getter")
        }
    }

    // ══ 5 · Cờ ĐẢO (ac_auto) — cơ chế có bài canh kể cả khi chưa nút nào bật ═══════════════════════════════

    @Test
    fun `applyInverted doi 0 thanh dang bat va giu null nguyen ven`() {
        // [ĐO xe 2026-09-16] `getAcControlMode()` = 0 = AC_CTRLMODE_AUTO ⇒ AUTO đang BẬT (ac/BYDAutoAcDevice.java:29-30).
        assertEquals(1, applyInverted(0, inverted = true))
        assertEquals(0, applyInverted(1, inverted = true), "MANUAL = 1 ⇒ AUTO đang tắt")
        assertEquals(0, applyInverted(0, inverted = false), "không khai đảo thì không được tự đảo")
        assertNull(applyInverted(null, inverted = true), "chưa đọc được thì không có mặt nào để đảo")
    }

    @Test
    fun `ac_auto doc duoc roi ma duong GHI van dung yen`() {
        val def = ControlRegistry.byId("ac_auto")!!
        assertEquals("ac_mode_auto", def.readKey, "T2 đã nối đường ĐỌC qua getAcControlMode")
        assertTrue(def.readInverted, "AC_CTRLMODE_AUTO = 0 ⇒ phải khai cờ đảo, nếu không ô nói ngược")
        assertEquals(
            "1324355606", def.bindingKey,
            "⚠ đọc được KHÔNG kéo theo ghi được: id này không có trong BYDAutoFeatureIds của xe, spec cấm bắn " +
                "lệnh khí hậu theo phỏng đoán",
        )
        // [ĐO xe 2026-09-16] getter trả 0 ⇒ nút phải hiện ĐANG BẬT (1). Gỡ cờ đảo ra thì ca này ĐỎ ngay.
        assertEquals(1, HalBindingTable(FakeHalGateway(getters = mapOf("getAcControlMode" to "0"))).readState("ac_auto"))
        assertEquals(0, HalBindingTable(FakeHalGateway(getters = mapOf("getAcControlMode" to "1"))).readState("ac_auto"))
    }

    // ══ 6 · Sáu nút của lượt T2 — mỗi cái đi qua ĐÚNG getter đã đo trên xe ═════════════════════════════════

    @Test
    fun `sau nut T2 doc bang getter da do tren xe, khong bang khoa GHI`() {
        // Cặp mã → (getter thật, tham số int). Chép từ phép đo, không từ trí nhớ — `hal-reads.txt`.
        mapOf(
            "seatc" to ("getSeatVentilatingState" to 1),
            "seath" to ("getSeatHeatingState" to 1),
            "defrost" to ("getAcDefrostState" to 1),
            "defrost_rear" to ("getAcDefrostState" to 2),
            "ac_auto" to ("getAcControlMode" to null),
            "vol" to ("getStreamVolume" to null),
        ).forEach { (id, expect) ->
            val path = readPathOf(id)!!
            assertEquals(expect.first, path.key.substringAfter('.'), "$id đọc sai getter")
            assertEquals(expect.second, path.arg, "$id gọi sai tham số (area/seatID)")
            assertTrue(path.key != ControlRegistry.byId(id)!!.bindingKey, "$id vẫn đang đọc qua khoá GHI")
        }
    }

    @Test
    fun `hai o say kinh dung CHUNG mot getter nhung KHAC area`() {
        // Một getter, hai vùng: nếu ai đó bỏ tham số thì cả hai ô đọc ra cùng một con số và không bài nào khác
        // thấy được. Gateway giả trả hai giá trị KHÁC nhau theo area để chỗ lẫn lộn lộ ra ngay.
        val gw = FakeHalGateway(gettersByArg = mapOf("getAcDefrostState" to mapOf(1 to "1", 2 to "0")))
        val table = HalBindingTable(gw)
        assertEquals(1, table.readState("defrost"), "sấy TRƯỚC đọc area 1")
        assertEquals(0, table.readState("defrost_rear"), "sấy SAU đọc area 2")
    }

    @Test
    fun `am luong doc bang AudioManager cua Android, khong cham HAL xe`() {
        // [BindingRoute.Local] — đường duy nhất trong registry không đi qua BYDAuto. Gateway giả mồi CẢ một getter
        // HAL trùng tên: nếu code đi nhầm sang đường HAL thì nó đọc ra 30 và ca này ĐỎ.
        val gw = FakeHalGateway(locals = mapOf("getStreamVolume" to "12"), getters = mapOf("getStreamVolume" to "30"))
        assertEquals(12, HalBindingTable(gw).readState("vol"))
        assertEquals(BindingRoute.Local("AudioManager", "getStreamVolume"), HalBindingTable.routeOf(readPathOf("vol")!!.key))
    }
}
