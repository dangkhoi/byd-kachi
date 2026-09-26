package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.SavedPlace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá **[P1] Pass 1 (2026-09-26)** của spec `kachi-voice-rearchitecture-and-remaining.html` §8.2 (A): ảnh chụp ngữ pháp
 * mà tiến trình chính ghi phải đọc lại **y nguyên** ở `:wake` (round-trip), sống qua tệp hỏng (không ném), giữ đúng
 * Unicode tiếng Việt, và **đủ** cho ba chỗ dùng: hotword (`placeLabels`), parser (`profiles`/`placeLabels`), dispatcher
 * (`homeState().savedPlaces`).
 */
class VoiceGrammarSnapshotTest {

    private val nha = SavedPlace("Nhà", "123 Nguyễn Trãi, Hà Nội", 21.0045, 105.8412)
    private val congTy = SavedPlace("Công ty", "Keangnam Landmark 72")
    private val snap = VoiceGrammarSnapshot(
        profiles = listOf("Mặc định", "Vợ", "Nhà A"),
        activeProfile = "Vợ",
        places = listOf(nha, congTy),
        writtenAtMs = 1_758_900_000_000L,
    )

    // ── Round-trip ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `encode roi decode ra dung anh chup - thu tu ho so, ho so dang dung, so dia chi ca toa do lan khong, moc ghi`() {
        val d = VoiceGrammarSnapshot.decode(snap.encode())
        assertEquals(snap, d.snapshot)
        assertNull(d.problem, "ảnh chụp sạch không có lý do bỏ dòng nào")
    }

    @Test
    fun `dang luu tu doc duoc - header + mot ban ghi mot dong, dong place dung ma hoa SavedPlaces`() {
        val text = snap.encode()
        val lines = text.trimEnd().split('\n')
        assertEquals("kachi-grammar\tv1\t1758900000000", lines[0])
        assertEquals("active\tVợ", lines[1])
        assertEquals(listOf("profile\tMặc định", "profile\tVợ", "profile\tNhà A"), lines.subList(2, 5))
        // Cùng chuỗi mà `WorkspacePrefs` ghi vào `saved_places` (DRY — không có bộ mã hoá thứ hai).
        assertEquals("place\tNhà|123 Nguyễn Trãi, Hà Nội|21.0045|105.8412", lines[5])
        assertEquals("place\tCông ty|Keangnam Landmark 72||", lines[6])
    }

    @Test
    fun `unicode tieng Viet (dau ghep, chu hoa) giu nguyen qua round-trip`() {
        val s = VoiceGrammarSnapshot(
            profiles = listOf("Ông Bà", "Chị Hai", "Nguyễn Văn A"),
            activeProfile = "Chị Hai",
            places = listOf(SavedPlace("Nhà Quê", "Ấp 3, xã Tân Thạnh, Long An")),
        )
        assertEquals(s, VoiceGrammarSnapshot.decode(s.encode()).snapshot)
    }

    // ── Hỏng / thiếu ⇒ rỗng + lý do, KHÔNG ném ─────────────────────────────────────────────────────────────

    @Test
    fun `null hoac rong - EMPTY + ly do`() {
        listOf(null, "", "   \n").forEach {
            val d = VoiceGrammarSnapshot.decode(it)
            assertEquals(VoiceGrammarSnapshot.EMPTY, d.snapshot)
            assertNotNull(d.problem)
        }
    }

    @Test
    fun `header sai hoac version la - EMPTY + ly do, khong doan dinh dang`() {
        val bad = VoiceGrammarSnapshot.decode("kachi-profile\tv1\tX\nprofile\tVợ\n")
        assertEquals(VoiceGrammarSnapshot.EMPTY, bad.snapshot)
        assertTrue(bad.problem!!.contains("header"), bad.problem)
        val v2 = VoiceGrammarSnapshot.decode("kachi-grammar\tv2\t0\nprofile\tVợ\n")
        assertEquals(VoiceGrammarSnapshot.EMPTY, v2.snapshot)
        assertTrue(v2.problem!!.contains("version"), v2.problem)
    }

    @Test
    fun `dong hong - bo dung dong do, giu phan con lai, co ly do`() {
        val text = "kachi-grammar\tv1\tabc\n" +      // mốc không phải số ⇒ 0, không ném
            "active\tVợ\n" +
            "profile\tMặc định\n" +
            "profile\t\n" +                            // hồ sơ rỗng ⇒ bỏ
            "rác\tgì đó\n" +                           // bản ghi lạ ⇒ bỏ
            "place\tNhà|chỉ có ba trường|1\n" +        // 3 trường ⇒ SavedPlaces.decode bỏ ⇒ bỏ dòng
            "place\tCông ty|Keangnam Landmark 72||\n" +
            "profile\tVợ\n"
        val d = VoiceGrammarSnapshot.decode(text)
        assertEquals(listOf("Mặc định", "Vợ"), d.snapshot.profiles)
        assertEquals("Vợ", d.snapshot.activeProfile)
        assertEquals(listOf(congTy), d.snapshot.places)
        assertEquals(0L, d.snapshot.writtenAtMs)
        assertNotNull(d.problem)
    }

    @Test
    fun `ten ho so co tab van tron ven (tach limit 2), xuong dong bi khu`() {
        val s = VoiceGrammarSnapshot(profiles = listOf("A\tB", "C\r\nD"), activeProfile = "A\tB")
        val d = VoiceGrammarSnapshot.decode(s.encode()).snapshot
        assertEquals(listOf("A\tB", "C D"), d.profiles)
        assertEquals("A\tB", d.activeProfile)
    }

    /**
     * SOÁT 2.68 · Pass 2 — **[problem] đi vào logcat** (`VoiceGrammarSnapshotStore.read` ghi `Log.i`), mà tệp này
     * mang địa chỉ nhà người dùng ⇒ lý do bỏ dòng KHÔNG được nhắc lại nội dung. Ca thật: tệp lệch dòng (mất tiền tố
     * `place\t`) ⇒ chính dòng địa chỉ trở thành "header"/"bản ghi lạ". Bài này khoá: không mẩu nào của số nhà/tên
     * đường/nhãn/toạ độ lọt vào lý do.
     */
    @Test
    fun `ly do bo dong KHONG chua noi dung tep - khong ro ri dia chi ra logcat`() {
        val diaChi = "Nhà|123 Nguyễn Trãi, Hà Nội|21.0045|105.8412"
        listOf(
            diaChi,                                                        // tệp lệch dòng: địa chỉ ở dòng header
            "kachi-grammar\tv1\t0\n$diaChi\n",                            // mất tiền tố `place` ⇒ "bản ghi lạ"
            "kachi-grammar\t$diaChi\t0\n",                                // địa chỉ chui vào ô version
        ).forEach { raw ->
            val problem = VoiceGrammarSnapshot.decode(raw).problem
            assertNotNull(problem, "tệp hỏng phải có lý do: $raw")
            listOf("123", "Nguyễn", "Trãi", "21.0045", "105.8412", "Nhà", "Hà Nội").forEach { bit ->
                assertTrue(!problem!!.contains(bit), "lý do rò mẩu \"$bit\" ra logcat: $problem")
            }
        }
        // Mã bản ghi ASCII ngắn thì VẪN được nói nguyên văn (không thì mất hẳn khả năng chẩn đoán định dạng).
        val la = VoiceGrammarSnapshot.decode("kachi-grammar\tv1\t0\nplace_v2\tx\n").problem
        assertTrue(la!!.contains("place_v2"), la)
    }

    /**
     * Pass 3 · P3 — khe còn lại của bài trên: tệp lệch dòng cũng làm một **tên hồ sơ** thành `kind`, và tên hồ sơ
     * ASCII một từ (*"Alice"*, *"Mom"*) có thể là tên người thật. Mọi mã hợp lệ của định dạng đều viết thường ⇒ chữ HOA
     * không bao giờ là mã, chỉ có thể là dữ liệu người dùng.
     */
    @Test
    fun `ten ho so ASCII mot tu (chu HOA) khong duoc noi nguyen van trong ly do`() {
        listOf("Alice", "Mom", "A.Nguyen", "NhaQue").forEach { ten ->
            val problem = VoiceGrammarSnapshot.decode("kachi-grammar\tv1\t0\n$ten\n").problem
            assertNotNull(problem, "dòng lạ `$ten` phải có lý do")
            assertTrue(!problem!!.contains(ten), "lý do rò tên hồ sơ \"$ten\" ra logcat: $problem")
        }
        // Đối chứng: mã viết thường vẫn nguyên văn (chẩn đoán định dạng không bị mất).
        assertTrue(VoiceGrammarSnapshot.decode("kachi-grammar\tv1\t0\nplace-v3\tx\n").problem!!.contains("place-v3"))
    }

    // ── Đủ cho ba chỗ dùng ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `homeState mang dung profiles, activeProfile, savedPlaces cho VoiceDispatcher`() {
        val st = snap.homeState()
        assertEquals(listOf("Mặc định", "Vợ", "Nhà A"), st.profiles)
        assertEquals("Vợ", st.activeProfile)
        assertEquals(listOf(nha, congTy), st.savedPlaces)
        assertEquals(listOf("Nhà", "Công ty"), snap.placeLabels())
    }

    @Test
    fun `chua co anh chup - homeState la mac dinh HomeUiState (khong te hon truoc ban va)`() {
        val st = VoiceGrammarSnapshot.EMPTY.homeState()
        assertEquals(HomeUiState().profiles, st.profiles)
        assertEquals(HomeUiState().activeProfile, st.activeProfile)
        assertTrue(st.savedPlaces.isEmpty())
    }

    /**
     * Chính hai câu bị mất theo Pass 1 — parser + dispatcher nhận đúng dữ liệu từ ảnh chụp thì hiểu lại được.
     * *"về nhà"* có bí danh sẵn trong `VoicePlaces.ALIASES` nên **parse** ra `NavigateSaved("Nhà")` kể cả khi sổ rỗng;
     * thứ cần ảnh chụp ở câu ấy là **dispatcher** (`SavedPlaces.find(state().savedPlaces, "Nhà")`). Nhãn tự đặt
     * (*"Nhà Quê"*) thì cần ảnh chụp ngay từ lúc parse.
     */
    @Test
    fun `parser va dispatcher voi du lieu tu anh chup hieu lai 'doi sang ho so X' va 've nha'`() {
        val s = snap.copy(places = snap.places + SavedPlace("Nhà Quê", "Long An"))
        val d = VoiceGrammarSnapshot.decode(s.encode()).snapshot
        val apps = listOf("YouTube")
        assertEquals(VoiceIntent.Profile("Vợ"), VoiceIntentParser.parseOne("đổi sang hồ sơ Vợ", d.profiles, apps, d.placeLabels()))
        assertEquals(VoiceIntent.NavigateSaved("Nhà"), VoiceIntentParser.parseOne("về nhà", d.profiles, apps, d.placeLabels()))
        assertEquals(VoiceIntent.NavigateSaved("Nhà Quê"), VoiceIntentParser.parseOne("đến nhà quê", d.profiles, apps, d.placeLabels()))
        assertEquals(nha, com.byd.clusternav.launcher.SavedPlaces.find(d.homeState().savedPlaces, "Nhà"), "dispatcher giải được địa chỉ từ homeState()")
        // Đối chứng: ngữ pháp rỗng (trạng thái trước bản vá) — hồ sơ không parse, nhãn tự đặt không parse, dispatcher không giải được.
        val empty = VoiceGrammarSnapshot.EMPTY
        assertTrue(VoiceIntentParser.parseOne("đổi sang hồ sơ Vợ", empty.profiles, apps, empty.placeLabels()) !is VoiceIntent.Profile)
        assertTrue(VoiceIntentParser.parseOne("đến nhà ngoại", empty.profiles, apps, empty.placeLabels()) !is VoiceIntent.NavigateSaved)
        assertNull(com.byd.clusternav.launcher.SavedPlaces.find(empty.homeState().savedPlaces, "Nhà"))
    }
}
