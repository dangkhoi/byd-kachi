package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ H1 — *"TĂNG GIÓ"* PHẢI CỘNG VÀO MỨC THẬT CỦA XE, KHÔNG VÀO MẶC ĐỊNH TRONG RAM ═══════════════════════
 *
 * ## [ĐO] bệnh nó khoá — tester 1.66: *"điều hoà chỉnh lung tung, quất một phát như lò heo quay"*
 * `VoiceDispatcher.runControl` quy lệnh tương đối về tuyệt đối bằng [ControlTileState.shared], mà bảng đó **lạc
 * quan**: nó khởi tạo bằng `ControlDef.value` (gió **4** · nhiệt **22**) và chỉ đổi khi chính Kachi bấm. Người lái
 * chỉnh gió ở màn BYD gốc thì nó không hề biết ⇒ [ĐO xe 2026-09-16] xe đang **gió 1**, nói *"tăng gió"*, Kachi tính
 * 4 + 1 và bắn **5** — nhảy bốn nấc trong một câu, đúng cái *"quất một phát"*.
 *
 * ## Vì sao gọi thẳng [VoiceDispatcher.execute] chứ không [VoiceDispatcher.submit]
 * Thứ cần khoá ở đây là **phép tính mốc**, không phải bộ phân tích câu (từ vựng *"tăng"/"giảm"* thuộc workstream
 * khác và đang đổi trong cùng phiên). Đưa thẳng [VoiceIntent.Control] có `relative` vào thì bài này đo đúng một
 * thứ, và không đỏ lây khi bảng từ đồng nghĩa được sửa.
 */
class VoiceRelativeStepTest {

    /** Cổng xe giả: ghi lại lệnh đã bắn, và trả về *"xe đang báo mức nào"* theo bảng do bài đặt. */
    private class Port(
        private val reads: Map<String, Int?>,
        /** Mã nút mà "chiếc xe giả" này KHÔNG có (bảng feature-id thật thiếu id) — xem `wiredOnThisCar`. */
        private val absent: Set<String> = emptySet(),
    ) : CarControlPort {
        val fired = ArrayList<String>()
        val readIds = ArrayList<String>()

        /** Nút vắng thì lệnh HỎNG — đúng như trên xe thật, nơi `write` trả sentinel. */
        private fun ok(id: String): Boolean = id !in absent

        override fun wiredOnThisCar(id: String): Boolean = id !in absent

        override fun toggle(id: String, on: Boolean): Boolean { fired += "toggle:$id:$on"; return ok(id) }
        override fun step(id: String, value: Int): Boolean { fired += "step:$id:$value"; return true }
        override fun cover(id: String, open: Boolean): Boolean { fired += "cover:$id:$open"; return true }
        override fun select(id: String, index: Int): Boolean { fired += "select:$id:$index"; return true }
        override fun press(id: String): Boolean { fired += "press:$id"; return true }

        override fun readState(id: String): Int? { readIds += id; return reads[id] }

        // Lượt đọc-lại SAU khi ghi (R5) dùng cửa riêng; trả chính con số vừa gửi để bài này không lẫn hai việc.
        override fun readStep(id: String): Int? = null
    }

    private class Rig(reads: Map<String, Int?> = emptyMap(), absent: Set<String> = emptySet()) {
        val port = Port(reads, absent)
        val said = ArrayList<String>()

        fun dispatcher() = VoiceDispatcher(
            control = { port },
            state = { HomeUiState(profiles = listOf("Mặc định")) },
            media = { error("bài này không chạm tới nhạc") },
            appsByLabel = { emptyMap() },
            openApp = { false },
            openAppList = {},
            openSettings = {},
            onSwitchProfile = {},
            onListen = {},
            confirm = { _, _, n -> n() },
            say = { said += it },
            assignAppToSlot = { _, _ -> error("bài này không gắn app vào ô") },
            sendToApp = { error("bài này không giao việc cho app đích") },
            geocode = { error("bài này không tra toạ độ") },
            mediaPackage = { null },
            background = { it() },
        )
    }

    private fun up(id: String, steps: Int = 1) = listOf(VoiceIntent.Control(id, null, relative = steps))

    // ══ 1 · Ca của tester: xe đang ở mức thấp, RAM nói mức cao ═══════════════════════════════════════════

    @Test
    fun `tang gio cong vao muc THAT cua xe chu khong vao mac dinh trong RAM`() {
        // [ĐO xe 2026-09-16] xe đang gió 1. RAM mặc định là 4 ⇒ bản 1.68 bắn 5.
        val r = Rig(mapOf("fan" to 1))
        ControlTileState.shared.setValue("fan", 4)
        r.dispatcher().execute(up("fan"))
        assertEquals(listOf("step:fan:2"), r.port.fired, "phải là 1 + 1 = 2; thấy 5 nghĩa là vẫn cộng vào RAM")
        assertEquals(listOf("fan"), r.port.readIds, "đúng MỘT lượt đọc cho MỘT câu lệnh (ngân sách 33 đọc/phút)")
    }

    @Test
    fun `nhiet do tuong doi cung lay moc tu xe`() {
        val r = Rig(mapOf("temp" to 25))
        ControlTileState.shared.setValue("temp", 22)
        r.dispatcher().execute(up("temp"))
        assertEquals(listOf("step:temp:26"), r.port.fired, "25 + 1 = 26, KHÔNG phải 22 + 1 = 23")
    }

    @Test
    fun `giam nhieu nac van tinh tu moc that`() {
        val r = Rig(mapOf("fan" to 6))
        ControlTileState.shared.setValue("fan", 4)
        r.dispatcher().execute(up("fan", steps = -2))
        assertEquals(listOf("step:fan:4"), r.port.fired, "6 − 2 = 4")
    }

    // ══ 2 · Không đọc được ⇒ NGUYÊN hành vi 1.68 (không bịa số) ═══════════════════════════════════════════

    @Test
    fun `xe khong tra loi thi lui ve dung hanh vi 1_68`() {
        val r = Rig()   // readState → null (off-car · máy ảo · nút chưa có đường đọc)
        ControlTileState.shared.setValue("temp", 22)
        r.dispatcher().execute(up("temp"))
        assertEquals(listOf("step:temp:23"), r.port.fired, "null ⇒ dùng mức RAM y như 1.68, không bịa một con số khác")
    }

    @Test
    fun `cong xe nem thi van chay tiep bang muc RAM`() {
        val port = object : CarControlPort {
            val fired = ArrayList<String>()
            override fun toggle(id: String, on: Boolean) = true
            override fun step(id: String, value: Int): Boolean { fired += "step:$id:$value"; return true }
            override fun cover(id: String, open: Boolean) = true
            override fun select(id: String, index: Int) = true
            override fun press(id: String) = true
            override fun readState(id: String): Int? = error("trim thiếu lớp HAL → ClassNotFound")
        }
        ControlTileState.shared.setValue("fan", 4)
        VoiceDispatcher(
            control = { port }, state = { HomeUiState(profiles = listOf("Mặc định")) },
            media = { error("không dùng") }, appsByLabel = { emptyMap() }, openApp = { false },
            openAppList = {}, openSettings = {}, onSwitchProfile = {}, onListen = {},
            confirm = { _, _, n -> n() }, say = {}, assignAppToSlot = { _, _ -> false },
            sendToApp = { false }, geocode = { null }, mediaPackage = { null }, background = { it() },
        ).execute(up("fan"))
        assertEquals(listOf("step:fan:5"), port.fired, "một câu trả lời đẹp hơn không đáng làm hỏng cả lệnh")
    }

    // ══ 3 · Bảng trạng thái dùng chung phải mang con số THẬT ══════════════════════════════════════════════

    @Test
    fun `bang trang thai dung chung nhan so that, khong nhan so suy ra tu RAM`() {
        val r = Rig(mapOf("fan" to 1))
        ControlTileState.shared.setValue("fan", 4)
        r.dispatcher().execute(up("fan"))
        assertEquals(
            2, ControlTileState.shared.value(ControlRegistry.byId("fan")!!),
            "thanh nút và câu nói phải cùng nói một điều về MỘT cái xe",
        )
    }

    @Test
    fun `lenh TUYET DOI khong di hoi xe mot luot thua`() {
        val r = Rig(mapOf("fan" to 1))
        r.dispatcher().execute(listOf(VoiceIntent.Control("fan", 3)))
        assertEquals(listOf("step:fan:3"), r.port.fired)
        assertTrue(r.port.readIds.isEmpty(), "câu đã nêu đích thì không có gì để hỏi — mỗi lượt đọc là một lượt HAL")
    }

    /**
     * ═══ R5 — *"hỏng lần này"* và *"XE NÀY KHÔNG CÓ"* phải là HAI câu khác nhau ════════════════════════
     *
     * [ĐO xe 2026-09-16] `ac_auto` khai feature `1324355606`, id ấy **không có** trong bảng của xe owner, trong
     * khi owner xác nhận xe **CÓ** điều hoà auto. Tới 1.68 cả hai ca ra cùng một câu, nên người lái nói
     * *"điều hoà"*, nghe báo hỏng, rồi thử lại — mãi. Tester nêu đúng chỗ này.
     *
     * Bài khoá **cả hai chiều**: vắng ⇒ câu *"trên xe này"*; hỏng-mà-có-mặt ⇒ giữ nguyên câu cũ. Thiếu chiều thứ
     * hai thì một bản vá quá tay sẽ đọc *"xe này không có"* cho mọi lỗi tạm thời, và đó là một câu **sai**.
     */
    @Test
    fun `nut VANG tren xe thi noi thang, nut hong binh thuong thi giu nguyen cau cu`() {
        val vang = Rig(absent = setOf("ac_auto"))
        vang.dispatcher().execute(listOf(VoiceIntent.Control("ac_auto", 1)))
        assertTrue(
            vang.said.single().contains("trên xe này"),
            "nút mà bảng feature-id của xe KHÔNG có phải nói thẳng là xe này không có: ${vang.said}",
        )

        val hong = Rig()
        hong.dispatcher().execute(listOf(VoiceIntent.Control("ac_auto", 1)))
        assertTrue(
            !hong.said.single().contains("trên xe này"),
            "cổng nói 'có' mà lệnh vẫn hỏng ⇒ là lỗi LẦN NÀY, không được đổ cho chiếc xe: ${hong.said}",
        )
    }

    /**
     * Cổng mặc định (`NoCar`, mọi bản giả cũ, máy ảo) phải trả `true` — nếu không thì Kachi đọc to *"chưa điều
     * khiển được trên xe này"* cho **mọi** nút ở khắp nơi trừ đúng một chiếc xe.
     */
    @Test
    fun `mac dinh cua cong la KHONG BIET, va khong biet nghia la cu thu`() {
        assertTrue(NoCar.wiredOnThisCar("ac_auto"), "mặc định phải là 'cứ thử đi', không phải 'xe không có'")
    }

    // ══ 4 · T2 (2026-09-16) — SÁU nút còn lại của H1, owner: *"làm hết toàn bộ scope"* ═══════════════════

    /**
     * Lượt trước chỉ `fan`/`temp`/`recirc` có đường đọc, nên *"tăng âm lượng"* vẫn cộng vào **mặc định RAM** (12) —
     * đúng cùng một bệnh, chỉ khác cái nút. Nay `vol` đọc bằng `AudioManager.getStreamVolume` (mã datum
     * `media_vol`), nên mốc là số THẬT.
     *
     * Bài đi qua [VoiceDispatcher] thật, không qua bảng: nếu ai gỡ `readKey` của `vol` ra thì cổng giả không được
     * hỏi nữa và ca này ĐỎ ngay ở dòng `readIds`.
     */
    @Test
    fun `tang am luong cong vao muc THAT cua xe`() {
        val r = Rig(mapOf("vol" to 7))
        ControlTileState.shared.setValue("vol", 12)
        r.dispatcher().execute(up("vol"))
        assertEquals(listOf("step:vol:8"), r.port.fired, "7 + 1 = 8, KHÔNG phải 12 + 1 = 13")
        assertEquals(listOf("vol"), r.port.readIds, "phải HỎI XE trước khi cộng")
    }

    /**
     * Bốn nút bật/tắt của lượt T2 — ở đây khoá đúng thứ bề mặt GIỌNG NÓI thật sự phụ thuộc: lệnh vẫn bắn đúng, và
     * đường ĐỌC của cả bốn còn nguyên. (Phép đọc ra số nào thì `ControlReadKeyTest`/`ControlLevelsTest` ở `:core`
     * đã khoá bằng gateway giả; nhắc lại phép tra ở đây vì nếu `readKey` đứt thì câu trả lời của Kachi là chỗ người
     * lái nhận ra đầu tiên, và bài này là bài duy nhất chạy qua [VoiceDispatcher] thật.)
     */
    @Test
    fun `bon nut bat tat cua T2 van ban dung lenh va van con duong doc`() {
        listOf("seatc", "seath", "defrost", "defrost_rear").forEach { id ->
            val r = Rig(mapOf(id to 1))
            r.dispatcher().execute(listOf(VoiceIntent.Control(id, 1)))
            assertEquals(listOf("toggle:$id:true"), r.port.fired, "$id phải bắn đúng một lệnh bật")
            assertTrue(ControlRegistry.byId(id)!!.readKey.isNotBlank(), "$id mất đường đọc")
        }
    }
}
