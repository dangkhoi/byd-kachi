package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá **BẢNG THAM SỐ GHI** (`HalBindingTable.writeArgs`) — thuần, kiểm được off-car.
 *
 * ## Vì sao tệp này tồn tại
 * Dự án đã bị **HAI lần** cùng một họ lỗi: một nút mang nhãn hứa việc A nhưng gửi đúng lệnh của việc B.
 *  1. 2026-09-11 (sáng): nút nhãn *"Kính 50%"* ghi ĐÚNG CÙNG lệnh với kính cửa lái (không có "nửa" nào). Lần đó
 *     vá bằng test chặn **nhãn chứa `%`** — chặn đúng *hiện tượng*, không chặn *nguyên nhân*.
 *  2. 2026-09-11 (chiều, lượt soát độc lập): `lock` "Khoá xe" và `door` "Mở cửa" khai CÙNG `bindingKey` và cùng
 *     rơi vào nhánh `else` ⇒ **cùng một byte cho hai nghĩa đối nghịch** ⇒ gói "Rời xe" không khoá xe. Test nhãn-`%`
 *     ở lần 1 KHÔNG thể bắt được lần 2.
 *
 * ⇒ Tệp này khoá **nguyên nhân**: hai mã dùng chung một lệnh xe thì tham số gửi đi phải KHÁC nhau, trừ khi được
 * khai tường minh là *cùng một việc* ([KNOWN_SAME_ACTION]). Thêm nút mới đè lên lệnh có sẵn ⇒ test đỏ, buộc người
 * viết phải nói rõ đó là bí danh hay là nghĩa khác.
 *
 * ⚠ Test này KHÔNG chứng minh xe nhận lệnh (phần đó cần xe). Nó chỉ chặn **tự mâu thuẫn nội bộ** — thứ off-car
 * chứng minh được và đã hai lần lọt.
 */
class ControlWriteArgsTest {

    private fun args(id: String, primary: Int): IntArray =
        HalBindingTable.writeArgs(requireNotNull(ControlRegistry.byId(id)) { "không có nút $id" }, primary)

    /**
     * Cặp mã CÙNG lệnh xe được phép sinh tham số y hệt — vì chúng là **cùng một việc**, chỉ khác kiểu ô.
     * Mỗi mục PHẢI có lý do. Danh sách này là chỗ duy nhất được nới; nới thì phải viết lý do.
     */
    private val KNOWN_SAME_ACTION = mapOf(
        // "Kính cửa lái" (TOGGLE) và "Kính trước-trái" (COVER) là CÙNG một cửa kính — cố ý giữ hai ô vì hai kiểu
        // điều khiển khác nhau (bật/tắt vs mở/đóng/dừng). Cùng nghĩa ⇒ cùng tham số là ĐÚNG.
        setOf("window", "win_lf") to "cùng cửa kính lái, khác kiểu ô (TOGGLE vs COVER)",
    )

    /**
     * ⚠🚗 **NỢ TRÊN XE — biết là SAI nhưng chưa sửa được off-car.** Ba cặp dưới đây khai chung một mã lệnh nhưng
     * mang nghĩa KHÁC nhau (rõ nhất: "Độ sáng màn" và "Độ sáng HUD" không thể là cùng một thanh ghi). Khác với
     * [KNOWN_SAME_ACTION], đây KHÔNG phải "cùng việc nên cùng tham số" — đây là **chưa biết tham số đúng**.
     *
     * Vì sao không tự sửa: cả ba đi đường **feature-id số** mà ngữ nghĩa tham số phải dump trên xe theo trim
     * (backlog `L-RE`, catalog §E). Tự nghĩ ra giá trị là đúng thứ đã gây ra lỗi P0 khoá cửa. Cả ba mã đều ở mức
     * chưa-kiểm-trên-xe nên ô đã mang chấm cảnh báo — người dùng không bị hứa suông.
     *
     * Danh sách này **không được phình**: bài `danh sach no xe khong duoc muc rua` đòi mỗi mục vẫn còn trùng thật,
     * nên khi ai đó tách được tham số thì phải xoá mục tương ứng khỏi đây.
     */
    private val COLLISION_PENDING_CAR = mapOf(
        setOf("cam", "camera_view") to "feature-id 3001: bật camera vs chọn góc — cần dump tham số trên xe",
        setOf("headl", "headlight_mode") to "feature-id 1276153912: bật đèn pha vs chọn chế độ — cần dump",
        setOf("brightness_gear", "hud_brightness") to "feature-id 1276174360: sáng màn vs sáng HUD — một trong hai SAI id",
    )

    // ── P0: khoá cửa ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `khoa xe va mo cua KHONG duoc gui cung mot byte`() {
        // Giá trị theo tài liệu dự án (locked=2 / unlocked=1) — xem KDoc writeArgs.
        assertArrayEquals(intArrayOf(2), args("lock", 1), "bật 'Khoá / mở khoá' phải gửi 2 (khoá)")
        assertArrayEquals(intArrayOf(1), args("lock", 0), "tắt 'Khoá / mở khoá' phải gửi 1 (mở khoá), KHÔNG phải 0")
        // `door` là NÚT BẤM một chiều (mở khoá). Trước đây nó là TOGGLE nhãn "Mở cửa" — tức **tắt nó thì khoá xe**
        // mà nhãn không nói ⇒ đúng họ lỗi P0 vừa dọn. Nút bấm thì không có mặt-tắt để nói dối.
        assertEquals(ControlKind.BUTTON, ControlRegistry.byId("door")?.kind, "phải là nút BẤM, không phải TOGGLE")
        assertArrayEquals(intArrayOf(1), args("door", 1), "bấm 'Mở khoá cửa' gửi 1 (mở khoá)")
        assertArrayEquals(intArrayOf(1), args("door", 0), "nút bấm KHÔNG có mặt tắt ⇒ vẫn là 1, không bao giờ khoá")
        assertFalse(
            args("lock", 1).contentEquals(args("door", 1)),
            "hai nhãn nghĩa đối nghịch mà gửi cùng byte ⇒ ít nhất một nhãn nói dối người lái",
        )
    }

    @Test
    fun `goi Roi xe ket bang buoc KHOA, khong phai buoc mo`() {
        val leave = requireNotNull(ActionMacros.byId("mac_leave"))
        val last = leave.steps.last()
        assertEquals("lock", last.controlId, "bước cuối của 'Rời xe' phải là khoá xe")
        assertArrayEquals(
            intArrayOf(2), args(last.controlId, last.arg),
            "bước cuối 'Rời xe' phải gửi 2 (khoá). Gửi 1 = MỞ khoá rồi rời xe — đúng lỗi P0 đã tìm ra.",
        )
    }

    // ── Cùng họ: giá trị TẮT phải là giá trị xe hiểu, không phải 0 ───────────────────────────────

    @Test
    fun `mua tu dong kinh tat gui 2 chu khong phai 0`() {
        assertArrayEquals(intArrayOf(1), args("rain_close", 1))
        assertArrayEquals(
            intArrayOf(2), args("rain_close", 0),
            "tài liệu ghi ON=1/OFF=2; gửi 0 là giá trị không có trong tài liệu ⇒ xe bỏ qua, người dùng tưởng đã tắt",
        )
    }

    // ── Luật chung: một lệnh xe, hai mã ⇒ tham số phải khác ─────────────────────────────────────

    /**
     * LUẬT, tách riêng để chạy được trên **cả** registry thật lẫn dữ liệu giả. Trả danh sách cặp vi phạm.
     * Tách ra vì một phép kiểm chỉ chạy trên dữ liệu thật thì không ai biết nó có bắt được gì hay không.
     */
    private fun offenders(defs: List<ControlDef>, allowed: Set<Set<String>>): List<String> {
        val out = mutableListOf<String>()
        defs.filter { it.bindingKey.isNotBlank() }.groupBy { it.bindingKey }
            .filterValues { it.size > 1 }
            .forEach { (key, group) ->
                for (i in group.indices) for (j in i + 1 until group.size) {
                    val a = group[i]
                    val b = group[j]
                    val identical = (0..1).all { p ->
                        HalBindingTable.writeArgs(a, p).contentEquals(HalBindingTable.writeArgs(b, p))
                    }
                    if (identical && setOf(a.id, b.id) !in allowed) {
                        out += "$key: ${a.id}(\"${a.label}\") ≡ ${b.id}(\"${b.label}\")"
                    }
                }
            }
        return out
    }

    @Test
    fun `hai ma dung chung mot lenh xe thi tham so phai KHAC nhau`() {
        assertEquals(
            emptyList<String>(),
            offenders(ControlRegistry.ALL, KNOWN_SAME_ACTION.keys + COLLISION_PENDING_CAR.keys),
            "Hai mã gửi y hệt nhau trên cùng một lệnh xe. Nếu là CÙNG một việc thì khai vào KNOWN_SAME_ACTION " +
                "kèm lý do; nếu là việc KHÁC mà chưa biết tham số đúng thì khai vào COLLISION_PENDING_CAR (nợ xe); " +
                "nếu biết tham số thì tách ở HalBindingTable.writeArgs. " +
                "Đây là họ lỗi 'nhãn hứa việc A, gửi lệnh việc B' đã lọt 2 lần.",
        )
    }

    /**
     * Danh sách nợ-xe phải **luôn còn đúng**: mỗi cặp trong [COLLISION_PENDING_CAR] vẫn phải thật sự trùng tham số.
     * Ai tách được một cặp mà quên xoá khỏi danh sách ⇒ bài này đỏ ⇒ danh sách không biến thành lời bào chữa vĩnh viễn.
     */
    @Test
    fun `danh sach no xe khong duoc muc rua`() {
        val stale = COLLISION_PENDING_CAR.keys.filter { pair ->
            val defs = pair.mapNotNull { ControlRegistry.byId(it) }
            defs.size == 2 && !(0..1).all { p ->
                HalBindingTable.writeArgs(defs[0], p).contentEquals(HalBindingTable.writeArgs(defs[1], p))
            }
        }
        assertEquals(
            emptyList<Set<String>>(), stale,
            "cặp này KHÔNG còn trùng tham số nữa ⇒ đã sửa được thì xoá khỏi COLLISION_PENDING_CAR",
        )
        assertEquals(3, COLLISION_PENDING_CAR.size, "còn đúng 3 cặp nợ xe; thêm cặp mới phải là quyết định tường minh")
    }

    /**
     * Chốt phép kiểm trên **có răng**: dựng lại đúng tình huống P0 bằng hai nút GIẢ (mã không có nhánh riêng nên
     * cả hai đi qua `else`, y như `lock`/`door` bản cũ) và đòi luật phải nêu đích danh cặp đó.
     *
     * Không có bài này thì `offenders(...)` trả rỗng vì registry đã sạch, và ta không phân biệt được "luật đúng"
     * với "luật không bao giờ chạy" — đúng loại test trang trí mà lượt soát vừa tìm ra ở chỗ khác.
     */
    @Test
    fun `phep kiem co rang - dung lai ca P0 bang nut gia thi phai bat duoc`() {
        val key = "BYDAutoFakeDevice.setSomething"
        val fake = listOf(
            ControlDef("fake_on", "Mở gì đó", "ic-door", ControlKind.TOGGLE, bindingKey = key),
            ControlDef("fake_off", "Đóng gì đó", "ic-lock", ControlKind.TOGGLE, bindingKey = key),
        )
        val found = offenders(fake, emptySet())
        assertEquals(1, found.size, "hai nút giả cùng lệnh, cùng tham số ⇒ luật phải nêu đúng 1 cặp: $found")
        assertTrue(found.single().contains("fake_on"), "thông báo phải nêu ĐÍCH DANH cặp vi phạm")

        // Và khai vào danh sách cho phép thì luật im — nới có kiểm soát, không phải nới mù.
        assertEquals(emptyList<String>(), offenders(fake, setOf(setOf("fake_on", "fake_off"))))
    }

    // ── Bảo toàn: mọi mã đã proven KHÔNG được đổi ───────────────────────────────────────────────

    @Test
    fun `cac cong thuc da proven giu NGUYEN tung byte`() {
        assertArrayEquals(intArrayOf(1, 2), args("seatc", 1), "ghế mát bật → [ghế lái, mức 1]")
        assertArrayEquals(intArrayOf(1, 1), args("seatc", 0), "ghế mát tắt → [ghế lái, tắt]")
        assertArrayEquals(intArrayOf(1, 2), args("seath", 1))
        assertArrayEquals(intArrayOf(2), args("steer_heat", 1))
        assertArrayEquals(intArrayOf(1), args("steer_heat", 0))
        assertArrayEquals(intArrayOf(1, 1), args("win_lf", 1))
        assertArrayEquals(intArrayOf(2, 0), args("win_rf", 0))
        assertArrayEquals(intArrayOf(3, 1), args("win_lr", 1))
        assertArrayEquals(intArrayOf(4, 1), args("win_rr", 1))
        assertArrayEquals(intArrayOf(1, 1, 1, 1), args("windows_all", 1))
        assertArrayEquals(intArrayOf(1, 1), args("window", 1))
        assertArrayEquals(intArrayOf(1), args("trunk", 1))
        assertArrayEquals(intArrayOf(2), args("trunk", 0))
        assertArrayEquals(intArrayOf(1), args("pm25_clean_now", 1))
        assertArrayEquals(intArrayOf(1), args("seat_memory", 0), "nút bấm-1-phát luôn gửi 1")
    }

    @Test
    fun `ma khong co nhanh rieng van gui thang gia tri chinh`() {
        val def = requireNotNull(ControlRegistry.byId("readl"))
        assertArrayEquals(intArrayOf(1), HalBindingTable.writeArgs(def, 1))
        assertArrayEquals(intArrayOf(0), HalBindingTable.writeArgs(def, 0))
    }
}
