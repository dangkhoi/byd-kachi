package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceReply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ C + E (owner test xe 2026-09-19) — BÀI CHẠY THẬT, không quét nguồn ═══════════════════════════════════════
 *
 * Hai phản hồi của owner sau lượt lái thử 1.79:
 *  • **C** — *"cốp/ca-pô chỉ nên mở khi xe dừng"*: mở lúc đang chạy thì cốp che kính hậu, ca-pô bật lên che kính
 *    lái. Gate ở [VoiceDispatcher.runControl], tập mã ở [CtlSafetyPolicy.REQUIRES_STATIONARY].
 *  • **E** — *"nút nào đọc được thì đừng trả lời mù"*: tới 1.79 mọi TOGGLE/COVER đều nhận đúng một câu (✓ + đuôi
 *    *"chưa kiểm trên xe"*) kể cả khi xe **đang trả lời được**, và một lệnh **không ăn** cũng được báo ✓.
 *
 * ## Vì sao dựng [VoiceDispatcher] THẬT
 * Cùng lý do đã ghi ở KDoc [VoiceStepReadbackTest]: thứ cần khoá là **hành vi** (*"có bắn lệnh không"*, *"câu trả
 * lời là câu nào trong ba câu"*) — không đọc ra được từ một chuỗi ký tự. Đi thẳng bằng [VoiceDispatcher.execute]
 * với một [VoiceIntent] dựng tay thay vì [VoiceDispatcher.submit] một câu chữ: bài này canh tầng THI HÀNH, và để
 * bộ phân tích chen vào giữa là biến mỗi lượt đổi từ vựng thành một lượt đỏ giả ở đây (lượt D của cùng phiên đổi
 * đúng cụm *"kính"*, và bài này không được đỏ vì chuyện đó).
 */
class VoiceActGateReadbackTest {

    /**
     * Cổng xe giả — ghi lại lệnh đã bắn, và trả mức *"xe đang báo"* theo một **hàng đợi** cho mỗi id.
     *
     * Hàng đợi chứ không phải một con số vì ca quan trọng nhất của E là *"lượt đọc đầu trả mức CŨ, lượt sau trả
     * mức mới"* (bộ phận chạy vật lý vài giây) — một con số cố định không dựng lại được ca đó.
     */
    private class Port(private val reads: MutableMap<String, MutableList<Int?>>) : CarControlPort {
        val fired = ArrayList<String>()
        var readCount = 0
            private set

        override fun toggle(id: String, on: Boolean): Boolean { fired += "toggle:$id:$on"; return true }
        override fun step(id: String, value: Int): Boolean { fired += "step:$id:$value"; return true }
        override fun cover(id: String, open: Boolean): Boolean { fired += "cover:$id:$open"; return true }
        override fun select(id: String, index: Int): Boolean { fired += "select:$id:$index"; return true }
        override fun press(id: String): Boolean { fired += "press:$id"; return true }
        override fun readState(id: String): Int? {
            readCount++
            val q = reads[id] ?: return null
            return if (q.size > 1) q.removeAt(0) else q.firstOrNull()
        }
    }

    private class Rig(reads: Map<String, List<Int?>> = emptyMap(), private val speedKmh: Int? = null) {
        val port = Port(reads.mapValues { it.value.toMutableList() }.toMutableMap())
        val said = ArrayList<String>()

        /** `fresh` = `false` ⇒ [VoiceDispatcher.freshCar] trả `null` ⇒ tầng gọi phải lùi về ảnh chụp [HomeUiState]. */
        fun dispatcher(fresh: Boolean = true) = VoiceDispatcher(
            control = { port },
            state = {
                HomeUiState(
                    profiles = listOf("Mặc định"),
                    carStatus = CarStatus(drivetrain = CarStatus.Drivetrain(speedKmh = speedKmh)),
                )
            },
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
            // Lượt đọc lại chạy trên luồng NỀN; bài cần kết quả tất định nên chạy thẳng.
            background = { it() },
            freshCar = { id ->
                if (!fresh) null
                else CarStatus(drivetrain = CarStatus.Drivetrain(speedKmh = speedKmh)).takeIf { id == "speed" }
            },
        )

        fun run(id: String, value: Int) = dispatcher().execute(listOf(VoiceIntent.Control(id, value)))
    }

    // ══ C · cốp/ca-pô chỉ mở khi xe DỪNG ═══════════════════════════════════════════════════════════════════

    /** Xe đang chạy ⇒ **không một byte nào** ra bus, và câu trả lời nói ra ĐIỀU KIỆN mở được. */
    @Test
    fun `xe dang chay thi KHONG mo cop`() {
        val r = Rig(speedKmh = 37)
        r.run("trunk", 1)
        assertEquals(emptyList<String>(), r.port.fired, "xe đang chạy mà vẫn bắn lệnh mở cốp là đúng thứ C chặn")
        assertEquals(listOf(VoiceReply.notWhileMoving(VoiceIntent.Control("trunk", 1))), r.said)
        assertTrue(r.said.single().contains("dừng"), "câu trả lời phải nói ĐIỀU KIỆN mở được: ${r.said}")
    }

    /**
     * ⚠ 1.85 — ca này TRƯỚC ĐÂY bắn `hood` ở 5 km/h. Mã `hood` đã xoá ([ĐO xe 2026-09-20 §4] xe không có ca-pô
     * điện), và một ca gate trỏ tới mã KHÔNG tồn tại thì **xanh vì không có gì bắn ra cả** — tức nó hết canh được
     * điều nó sinh ra để canh (đúng họ *"bài canh là trang trí"* mà dự án đã bắt vài lần).
     *
     * Giữ đúng tính chất đáng giá của nó — **tốc độ NHỎ nhưng khác 0 vẫn là "đang chạy"** — bằng một mã còn sống.
     * Ca 37 km/h ở trên không thay được ca này: 37 thì ai cũng đồng ý là đang chạy, còn 5 km/h (bò trong bãi xe)
     * chính là chỗ một phép so `> 0` dễ bị "nới cho tiện" thành `> 10`.
     */
    @Test
    fun `toc do nho nhung khac 0 van la dang chay`() {
        val r = Rig(speedKmh = 5)
        r.run("trunk", 1)
        assertEquals(emptyList<String>(), r.port.fired, "5 km/h vẫn là đang chạy — cốp mở ra che kính hậu")
        assertEquals(listOf(VoiceReply.notWhileMoving(VoiceIntent.Control("trunk", 1))), r.said)
    }

    /** Xe đứng yên (0 km/h) ⇒ mở bình thường. Đây là ca dùng THƯỜNG NHẤT của nút này. */
    @Test
    fun `xe dung yen thi mo cop binh thuong`() {
        val r = Rig(speedKmh = 0)
        r.run("trunk", 1)
        assertEquals(listOf("cover:trunk:true"), r.port.fired)
    }

    /**
     * **ĐÓNG** cốp thì không bị gate, kể cả đang chạy — chặn nó lại là chặn đúng đường chữa.
     *
     * Đây là nửa dễ quên của một cổng an toàn: người lái phát hiện cốp đang mở lúc đang chạy thì việc họ cần làm
     * là **đóng**, và một lời từ chối ở đó biến gate thành cái bẫy.
     */
    @Test
    fun `dong cop thi khong bi gate du dang chay`() {
        val r = Rig(speedKmh = 60)
        r.run("trunk", 0)
        assertEquals(listOf("cover:trunk:false"), r.port.fired, "đóng cốp là đường CHỮA, không được chặn")
    }

    /**
     * Không đọc được tốc độ (`null` ở cả đường tươi lẫn ảnh chụp) ⇒ **CHO PHÉP** (fail-open).
     *
     * Có chủ ý: [ĐO] `speed` ở mức PROVEN nên trên xe thật gate có số để chạy; `null` gần như chỉ xảy ra off-car /
     * máy ảo, nơi chẳng có cốp nào để bung. Chọn fail-closed thì mỗi lần đọc hụt trên một chiếc xe đang **đỗ** sẽ
     * thành một lời từ chối cho việc hoàn toàn an toàn — tức cổng an toàn tự biến thành lỗi.
     */
    @Test
    fun `khong doc duoc toc do thi van cho mo`() {
        val r = Rig(speedKmh = null)
        r.run("trunk", 1)
        assertEquals(listOf("cover:trunk:true"), r.port.fired, "null ≠ đang chạy — không được từ chối")
    }

    /** Đường đọc TƯƠI hụt ⇒ lùi về ảnh chụp [HomeUiState], và ảnh chụp nói đang chạy thì vẫn phải chặn. */
    @Test
    fun `doc tuoi hut thi lui ve anh chup va van chan`() {
        val r = Rig(speedKmh = 42)
        r.dispatcher(fresh = false).execute(listOf(VoiceIntent.Control("trunk", 1)))
        assertEquals(emptyList<String>(), r.port.fired, "ảnh chụp đã nói 42 km/h — không được bỏ qua vì đọc tươi hụt")
    }

    /** Nút KHÔNG thuộc tập gate vẫn chạy khi đang chạy — *"mở kính lúc đang chạy"* là việc bình thường. */
    @Test
    fun `nut ngoai tap gate van chay khi xe dang chay`() {
        val r = Rig(speedKmh = 80)
        r.run("window", 1)
        assertEquals(listOf("toggle:window:true"), r.port.fired)
    }

    // ══ E · TOGGLE/COVER đọc-được thì ĐỌC LẠI xác nhận ══════════════════════════════════════════════════════

    /** Có `readKey` + xe báo ĐÚNG mức ⇒ câu *đã xác nhận*, **BỎ** đuôi *"chưa kiểm trên xe"*. */
    @Test
    fun `doc lai khop thi bo duoi chua-kiem`() {
        val r = Rig(reads = mapOf("recirc" to listOf(1)))
        r.run("recirc", 1)
        val want = VoiceIntent.Control("recirc", 1)
        assertEquals(listOf(VoiceReply.doneConfirmed(want)), r.said)
        assertFalse(r.said.single().contains("chưa kiểm"),
            "vừa ĐỌC LẠI trên chính xe này ⇒ nói 'chưa kiểm' là nói sai: ${r.said}")
        assertTrue(r.said.single().startsWith("✓"))
        assertEquals(1, r.port.readCount, "khớp ngay ⇒ KHÔNG đọc lần hai (một lượt chờ vô ích)")
    }

    /**
     * Xe báo mức CŨ ở cả hai lượt đọc ⇒ **✗ xe không nhận lệnh**.
     *
     * Đây là nửa còn lại của E, và là nửa owner thấy: tới 1.79 ca này báo ✓. Một nút hai mức chỉ có hai mức nên
     * không mức nào là *"xe kẹp về dải của nó"* — vẫn ở mức cũ nghĩa là **chưa xảy ra gì**.
     */
    @Test
    fun `doc lai van lech thi bao xe khong nhan lenh`() {
        val r = Rig(reads = mapOf("recirc" to listOf(0)))
        r.run("recirc", 1)
        assertEquals(1, r.said.size, "một lệnh ⇒ đúng MỘT câu trả lời. Thấy: ${r.said}")
        assertTrue(r.said.single().startsWith("✗"), "không ăn thì phải là ✗, không phải ✓: ${r.said}")
        assertEquals(VoiceReply.failed(VoiceIntent.Control("recirc", 1)), r.said.single())
        assertEquals(2, r.port.readCount, "lệch ⇒ đọc lại đúng MỘT lần nữa, không nhiều hơn")
    }

    /**
     * ⚠ Ca quan trọng nhất của E: **xe chưa kịp áp** — lượt đọc đầu trả mức CŨ, lượt sau trả mức mới.
     *
     * Không có lượt đọc lại thì đây là một câu *"✗ xe không nhận lệnh"* trên một lệnh hoàn toàn thành công — sai
     * đúng theo kiểu làm người ta thôi tin cả tính năng. Với kính/cốp (chạy vật lý vài giây) đây là ca THƯỜNG.
     */
    @Test
    fun `xe cham mot nhip thi luot doc lai cuu duoc cau tra loi`() {
        val r = Rig(reads = mapOf("recirc" to listOf(0, 1)))
        r.run("recirc", 1)
        assertEquals(listOf(VoiceReply.doneConfirmed(VoiceIntent.Control("recirc", 1))), r.said)
        assertTrue(ControlTileState.shared.isOn("recirc"),
            "bảng dùng chung phải mang mức THẬT — hai bề mặt nói cùng một điều về một cái xe")
    }

    /** Lệnh TẮT cũng phải xác nhận được (mức đọc về 0 = đúng ý muốn, không phải *"chưa ăn"*). */
    @Test
    fun `lenh tat doc lai khop thi cung xac nhan`() {
        val r = Rig(reads = mapOf("recirc" to listOf(0)))
        r.run("recirc", 0)
        assertEquals(listOf(VoiceReply.doneConfirmed(VoiceIntent.Control("recirc", 0))), r.said)
    }

    /**
     * **Không** đọc được (`null`) ⇒ giữ NGUYÊN câu 1.79, còn cả đuôi hedge. Đây là sự thành thật, không phải thiếu sót.
     *
     * `drl` có `readKey` nhưng cổng xe giả không có hàng đợi cho nó ⇒ `readState` trả `null` — đúng ca off-car /
     * trim không provision.
     */
    @Test
    fun `khong doc duoc thi giu nguyen cau 1_79 - dau hedge da bo 2026-09-21`() {
        val r = Rig()
        r.run("drl", 1)
        val want = VoiceIntent.Control("drl", 1)
        assertEquals(listOf(VoiceReply.done(want)), r.said)
        // 2026-09-21 owner bỏ hẳn chấm + đuôi "chưa kiểm" ⇒ câu done không còn hedge cho mọi mục.
        assertFalse(r.said.single().contains("chưa kiểm"),
            "đuôi 'chưa kiểm' đã bỏ hẳn: ${r.said}")
    }

    /**
     * ⚠ [SOÁT lượt E · P1] **BỘ PHẬN CHẠY BẰNG MÔ-TƠ: lệch dai dẳng ⇒ ✓ + hedge, KHÔNG phải ✗.**
     *
     * `win_lf` là [ControlKind.COVER] với `args` 0/1/2, nhưng khoá ĐỌC của nó (`window_lf` →
     * `getWindowOpenPercent`) trả **phần trăm 0–100**. Lệnh ĐÓNG trên một cửa đang mở 100% ⇒ đọc 100 rồi chờ 300 ms
     * vẫn còn 90 (cửa kính mất vài giây) ⇒ so mức ra *lệch* ở cả hai lượt. Không có cổng
     * [CtlSafetyPolicy.MOVES_SLOWLY] thì câu trả lời là *"✗ xe không nhận lệnh"* cho một lệnh ở mức **PROVEN**, tức
     * một khẳng định sai về một việc vừa chạy thật — đúng thứ làm người lái thôi tin cả tính năng.
     *
     * Lượt đọc lại là bằng chứng **một chiều**: chứng minh được THÀNH CÔNG, không chứng minh được THẤT BẠI.
     */
    @Test
    fun `bo phan chay cham lech dai dang thi KHONG bao xe khong nhan lenh`() {
        val r = Rig(reads = mapOf("win_lf" to listOf(100, 90)))
        r.run("win_lf", 0)
        val want = VoiceIntent.Control("win_lf", 0)
        assertEquals(listOf("cover:win_lf:false"), r.port.fired, "lệnh vẫn phải được bắn")
        assertFalse(r.said.single().startsWith("✗"),
            "cửa kính đang đóng dở, 300 ms chưa về 0 — báo ✗ ở đây là khẳng định sai: ${r.said}")
        assertEquals(listOf(VoiceReply.done(want)), r.said, "câu đúng là câu 1.79 (✓ + hedge), không phải ✗")
    }

    /** …nhưng bộ phận chạy cham mà **kịp tới đích** thì vẫn bỏ được hedge (không mất phần lợi của E). */
    @Test
    fun `bo phan chay cham ma toi dich thi van xac nhan`() {
        val r = Rig(reads = mapOf("win_lf" to listOf(100, 0)))
        r.run("win_lf", 0)
        assertEquals(listOf(VoiceReply.doneConfirmed(VoiceIntent.Control("win_lf", 0))), r.said)
    }

    /**
     * …và nhóm điện/khí thì **vẫn phải** nghe được *"xe không nhận lệnh"* — đó là nửa owner yêu cầu ở E.
     *
     * Cặp bài này khoá hai chiều của cùng một luật: nới [CtlSafetyPolicy.MOVES_SLOWLY] quá tay (nhét `recirc` vào)
     * sẽ làm bài này đỏ, còn bỏ cổng đi thì bài trên đỏ.
     */
    @Test
    fun `nhom dien-khi khong duoc mien cau xe khong nhan lenh`() {
        listOf("recirc", "drl", "defrost", "defrost_rear").forEach { id ->   // ⚠ 1.90: `anion` đã xoá
            assertFalse(CtlSafetyPolicy.movesSlowly(id),
                "'$id' đổi mức gần như tức thì ⇒ lệch dai dẳng ĐÚNG là 'lệnh không ăn', không được miễn")
        }
    }

    /** Nút KHÔNG có `readKey` ⇒ không đi hỏi gì cả (một lượt HAL thừa mỗi lần bấm) và giữ câu cũ. */
    @Test
    fun `nut khong co readKey thi khong doc lai gi`() {
        val r = Rig(reads = mapOf("readl" to listOf(0)))
        r.run("readl", 1)
        assertEquals("", ControlRegistry.byId("readl")!!.readKey, "tiền đề của bài: `readl` chưa có đường đọc")
        assertEquals(0, r.port.readCount, "không có đường đọc mà vẫn hỏi là một lượt HAL thừa")
        assertEquals(listOf(VoiceReply.done(VoiceIntent.Control("readl", 1))), r.said)
    }

    /**
     * [ControlKind.BUTTON] ⇒ **không** đọc lại, dù mã của nó có đường đọc.
     *
     * Nút bấm-một-phát: mức sau khi bấm không nói gì về việc cú bấm có tới hay không, nên so mức ở đó sẽ báo
     * *"✗ xe không nhận lệnh"* cho một cú bấm hoàn toàn bình thường — một câu sai trên một việc đúng.
     */
    @Test
    fun `nut BUTTON thi khong doc lai gi`() {
        val id = ControlRegistry.ALL.first { it.kind == ControlKind.BUTTON }.id
        val r = Rig(reads = mapOf(id to listOf(0)))
        r.run(id, 1)
        assertEquals(0, r.port.readCount, "BUTTON không có mức để so — đọc lại là sinh ra một câu ✗ giả")
        assertTrue(r.said.single().startsWith("✓"), "cú bấm thành công phải là ✓: ${r.said}")
    }
}
