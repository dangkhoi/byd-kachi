package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceLaunch
import com.byd.clusternav.launcher.voice.VoiceReply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.byd.clusternav.launcher.voice.VoiceRiskTable

/**
 * ═══ V1.1 · HÀNH VI của hai đường mới — Ô và APP ĐÍCH ════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R15 · R17**. Dựng [VoiceDispatcher] **thật**, cùng lệ với
 * [VoiceDispatcherSafetyTest]: thứ cần khoá ở đây là *"cái gì được gọi, với đối số nào, theo thứ tự nào"* —
 * không đọc ra được từ một chuỗi nguồn.
 *
 * Lớp này không chạm `android.*` trên các đường được kiểm: [VoiceAppIntents.Handoff] và [VoiceAppIntents.Coords]
 * là hai `data class` thuần, còn `startActivity` thì bài tự cấp một lambda giả.
 */
class VoiceHandoverTest {

    private companion object {
        const val YT_MUSIC = "com.google.android.apps.youtube.music"
        const val GMAPS = "com.google.android.apps.maps"
        const val VIETMAP = "vn.vietmap.live"
        const val SPOTIFY = "com.spotify.music"
    }

    /** Bộ giàn: mọi cổng ra đều ghi lại thay vì làm thật. */
    private class Rig(
        labels: Map<String, String> = mapOf("YouTube" to "com.google.android.youtube"),
        private val playing: String? = null,
        private val geocoded: VoiceAppIntents.Coords? = null,
        preset: LayoutPreset = LayoutPreset.QUAD,
    ) {
        val said = ArrayList<String>()
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        val assigned = ArrayList<Pair<Int, String>>()
        val opened = ArrayList<String>()
        val asked = ArrayList<String>()
        private val yes = ArrayList<() -> Unit>()
        private val no = ArrayList<() -> Unit>()
        var geocodeCalls = 0

        fun agreeLast() = yes.removeAt(yes.size - 1).invoke()

        val dispatcher = VoiceDispatcher(
            control = { error("bài này không chạm nút xe") },
            state = { HomeUiState(workspace = WorkspaceState(preset)) },
            media = { error("bài này không chạm transport nhạc") },
            appsByLabel = { labels },
            openApp = { pkg -> opened += pkg; true },
            openAppList = {},
            openSettings = {},
            onSwitchProfile = {},
            onListen = {},
            confirm = { q, y, n -> asked += q; yes += y; no += n },
            // ⚠ V3 · R7 (1.66): mặc định **không hỏi gì cả** (owner 2026-09-16). Bài này canh CƠ CHẾ của
            // cổng hỏi-lại, nên nó bật MỌI mã hỏi-được — không thì mọi ca dưới đây chạy thẳng và bài
            // trở thành một bài canh cho chính cái mặc định, không phải cho cổng.
            confirmIds = { VoiceRiskTable.askableIds().toSet() },
            say = { said += it },
            assignAppToSlot = { slot, pkg -> assigned += slot to pkg; true },
            sendToApp = { h -> sent += h; true },
            geocode = { geocodeCalls++; geocoded },
            mediaPackage = { playing },
            background = { it() },
            onUi = { it() },
        )

        /** Chạy một câu, **tự đồng ý** ở cổng CONFIRM của từ vựng mở (R16) để tới được phần cần đo. */
        fun run(text: String) {
            dispatcher.submit(text)
            while (yes.isNotEmpty()) agreeLast()
        }
    }

    // ══ (1) Ô — câu hỏi gốc của owner ════════════════════════════════════════════════════════════════════

    /**
     * *"Mở YouTube vào ô số 2"* phải đi **đường của ngăn kéo**, và phải là ô **1** của mảng (0-based).
     *
     * Phép đổi 1-based → 0-based chỉ được có ở **đúng một** chỗ; nếu nó cũng xảy ra ở `:core` thì mọi câu vào
     * nhầm ô bên cạnh và không test nào của `:core` thấy được.
     */
    @Test
    fun `mo app vao o di duong ngan keo va doi dung chi so`() {
        val r = Rig()
        r.run("mở youtube vào ô số 2")
        assertEquals(listOf(1 to "com.google.android.youtube"), r.assigned)
        assertTrue(r.opened.isEmpty(), "có ô thì KHÔNG được mở toàn màn — đó là một việc khác")
        assertTrue(r.said.single().startsWith("✓"), "phải báo đã làm; nhận được: ${r.said}")
    }

    /** Không nêu ô ⇒ y như 1.49: mở toàn màn, không đụng ô nào. */
    @Test
    fun `khong neu o thi van mo toan man`() {
        val r = Rig()
        r.run("mở youtube")
        assertEquals(listOf("com.google.android.youtube"), r.opened)
        assertTrue(r.assigned.isEmpty(), "không nêu ô mà lại gắn vào một ô")
    }

    /**
     * Ô ngoài bố cục ⇒ **nói ra con số thật**, và KHÔNG làm gì cả.
     *
     * Lùi về *"mở toàn màn"* mới là ca tệ: người ta nói rõ *"vào ô số 6"*, làm một việc khác rồi báo ✓ là nói dối.
     */
    @Test
    fun `o ngoai bo cuc thi noi ra so o that va khong lam gi`() {
        val r = Rig(preset = LayoutPreset.QUAD)   // 4 ô
        r.run("mở youtube vào ô số 6")
        assertTrue(r.assigned.isEmpty() && r.opened.isEmpty(), "ngoài dải mà vẫn làm gì đó")
        assertTrue(r.said.single().contains("4"), "câu trả lời phải nêu số ô thật; nhận được: ${r.said}")
    }

    /** Bố cục đổi thì câu trả lời đổi theo — số ô đọc từ state **lúc nói**, không phải một hằng. */
    @Test
    fun `so o doc tu bo cuc dang dung`() {
        val r = Rig(preset = LayoutPreset.ONE)
        r.run("mở youtube vào ô số 2")
        assertTrue(r.said.single().contains("1"), "bố cục 1 ô mà không nói ra; nhận được: ${r.said}")
    }

    // ══ (2) NHẠC — chọn app ══════════════════════════════════════════════════════════════════════════════

    /** Câu nêu đích danh ⇒ đúng app đó, đúng đường đã [ĐO] (có `extra.focus`). */
    @Test
    fun `phat bai bang YT Music di dung cua da do`() {
        val r = Rig(labels = mapOf("YouTube Music" to YT_MUSIC))
        r.run("phát bài diễm xưa bằng youtube music")
        val h = r.sent.single()
        assertEquals(YT_MUSIC, h.pkg)
        assertEquals("diễm xưa", h.query)
        val action = h.launch as VoiceLaunch.Action
        assertEquals(VoiceLaunch.ACTION_MEDIA_PLAY_FROM_SEARCH, action.action)
        assertEquals(VoiceLaunch.FOCUS_ANY_AUDIO, action.extras[VoiceLaunch.EXTRA_MEDIA_FOCUS])
    }

    /**
     * Không nêu app ⇒ đi vào **app đang phát**, không vào app mặc định.
     *
     * Mở YouTube Music đè lên Spotify đang phát là hai luồng nhạc cùng lúc — thứ người lái phải dừng xe mới dẹp.
     */
    @Test
    fun `khong neu app thi di vao phien nhac dang chay`() {
        val r = Rig(labels = mapOf("YouTube Music" to YT_MUSIC, "Spotify" to SPOTIFY), playing = SPOTIFY)
        r.run("phát bài diễm xưa")
        assertEquals(SPOTIFY, r.sent.single().pkg)
    }

    /** Không có phiên nào ⇒ app nhạc đầu tiên có mặt theo thứ tự bảng. */
    @Test
    fun `khong co phien nhac thi lay app nhac dau tien co mat`() {
        val r = Rig(labels = mapOf("YouTube Music" to YT_MUSIC, "Spotify" to SPOTIFY))
        r.run("phát bài diễm xưa")
        assertEquals(YT_MUSIC, r.sent.single().pkg)
    }

    /** Nêu một app **chưa cài** ⇒ nói *"chưa cài"*, **không** lặng lẽ đổi sang app khác. */
    @Test
    fun `app duoc goi ten ma chua cai thi noi chua cai, khong tu doi app`() {
        val r = Rig(labels = mapOf("YouTube Music" to YT_MUSIC))
        r.run("phát bài diễm xưa bằng spotify")
        assertTrue(r.sent.isEmpty(), "đã đổi sang app khác — người ta nói *bằng Spotify* là có lý do")
        assertTrue(r.said.single().contains("Spotify"), "câu trả lời phải gọi tên app; nhận được: ${r.said}")
    }

    // ══ (3) DẪN ĐƯỜNG — chọn app + toạ độ ════════════════════════════════════════════════════════════════

    /** Google Maps có cửa CHỮ ⇒ bắn thẳng, **không** tốn một lượt tra toạ độ nào (CLAUDE.md §6). */
    @Test
    fun `Google Maps di duong chu va khong geocode`() {
        val r = Rig(labels = mapOf("Bản đồ" to GMAPS))
        r.run("dẫn đường tới chợ Bến Thành bằng google maps")
        val h = r.sent.single()
        assertEquals(GMAPS, h.pkg)
        assertEquals("chợ Bến Thành", h.query)
        assertTrue((h.launch as VoiceLaunch.Uri).template.startsWith("geo:0,0?q="))
        assertEquals(0, r.geocodeCalls, "app có cửa chữ mà vẫn đi tra toạ độ ⇒ một lượt mạng không cần thiết")
    }

    /**
     * VietMap **không** có cửa chữ ⇒ tra toạ độ, **đọc lại tên nơi tra được**, rồi mới bắn URI toạ độ.
     *
     * Hai điều được khoá ở đây: có một cổng hỏi lại thứ hai (tên do bên tra cứu trả về khác câu người ta nói),
     * và URI đi ra là khuôn `vietmaplive://` lấy từ nguồn Kiki.
     */
    @Test
    fun `VietMap tra toa do, doc lai ten roi moi ban`() {
        val r = Rig(
            labels = mapOf("VietMap Live" to VIETMAP),
            geocoded = VoiceAppIntents.Coords(10.7717, 106.7043, "Chợ Bến Thành"),
        )
        r.run("dẫn đường tới chợ bến thành bằng việt map")
        assertEquals(1, r.geocodeCalls)
        assertTrue(r.asked.any { it.contains("Chợ Bến Thành") }, "phải đọc lại TÊN tra được; đã hỏi: ${r.asked}")
        val h = r.sent.single()
        assertEquals(VIETMAP, h.pkg)
        assertTrue((h.launch as VoiceLaunch.Uri).template.startsWith("vietmaplive://companion/navigation?"))
        assertEquals(10.7717, h.coords!!.lat)
    }

    /**
     * Tra không ra toạ độ ⇒ **mở app** rồi nói rõ là chưa giao được — không có dấu ✓ rỗng.
     *
     * [SOÁT Pass 3 · P2] Và câu nói phải là *"chưa tra được điểm đến"*, **không** phải *"app này không nhận
     * điểm đến"*: cái sau là một kết luận đã đo (đúng mãi), cái này là một lượt mạng hỏng (thử lại có thể được).
     * Dùng chung một câu là đổ lỗi cho app về một lần mất sóng, và người lái sẽ thôi không thử lại nữa.
     */
    @Test
    fun `khong tra duoc toa do thi mo app va noi ro LA DO TRA CUU`() {
        val r = Rig(labels = mapOf("VietMap Live" to VIETMAP), geocoded = null)
        r.run("dẫn đường tới chợ bến thành bằng việt map")
        assertTrue(r.sent.isEmpty(), "không có toạ độ mà vẫn bắn một URI thiếu tham số")
        assertEquals(listOf(VIETMAP), r.opened, "vẫn phải mở app — đó là phần chắc chắn làm được")

        val target = VoiceAppTargets.byKey(VoiceAppTargets.VIETMAP)!!
        val intent = VoiceIntent.Nav("chợ bến thành", VoiceAppTargets.VIETMAP)
        assertEquals(VoiceReply.navNoPlace(intent, target), r.said.last())
        assertTrue(
            r.said.last() != VoiceReply.navOpenedNoHandover(intent, target),
            "lượt tra cứu hỏng KHÁC 'app không có cửa nhận điểm đến' — hai câu, hai chuyện",
        )
    }

    /** Không app dẫn đường nào trên xe ⇒ nói thẳng, không bắn gì. */
    @Test
    fun `khong co app dan duong nao thi noi thang`() {
        val r = Rig(labels = mapOf("YouTube" to "com.google.android.youtube"))
        r.run("dẫn đường tới chợ Bến Thành")
        assertTrue(r.sent.isEmpty() && r.opened.isEmpty())
        assertTrue(r.said.single().startsWith("✗"))
    }

    // ══ (4) Cổng CONFIRM của từ vựng mở (R16) ════════════════════════════════════════════════════════════

    /**
     * Tên bài / điểm đến **luôn** đi qua một cổng hỏi lại, và **không có gì được bắn** trước khi người ta đồng ý.
     *
     * Đoạn ấy do bộ nhận dạng TỰ DO đọc ra (R16) — kém chính xác hơn hẳn phần còn lại của câu. Với điểm đến thì
     * [ĐO] Google Maps/Waze **bắt đầu dẫn luôn**, tức xe được chỉ sang hướng khác trước khi ai kịp đọc.
     */
    @Test
    fun `tu vung mo luon hoi lai truoc khi ban`() {
        val r = Rig(labels = mapOf("YouTube Music" to YT_MUSIC, "Bản đồ" to GMAPS))
        r.dispatcher.submit("phát bài diễm xưa bằng youtube music")
        assertEquals(1, r.asked.size, "phải hỏi lại")
        assertTrue(r.sent.isEmpty(), "CHƯA đồng ý mà đã bắn ⇒ cổng vô nghĩa")
        r.agreeLast()
        assertEquals(1, r.sent.size)
    }

    /** Lệnh transport (*"tạm dừng"*) **không** có tên bài nào để đọc lại ⇒ vẫn chạy thẳng như trước. */
    @Test
    fun `lenh transport khong bi hoi lai`() {
        val r = Rig()
        r.dispatcher.preview("tạm dừng").also { intents ->
            assertTrue(intents.isNotEmpty())
        }
        assertTrue(r.asked.isEmpty())
    }
}
