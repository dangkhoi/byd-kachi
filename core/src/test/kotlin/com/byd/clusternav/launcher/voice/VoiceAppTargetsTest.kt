package com.byd.clusternav.launcher.voice

import com.byd.clusternav.navigation.NavApps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1.1 · BẢNG ĐÍCH — khoá lại **phép đo**, không khoá một ý thích ══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R17**. Mỗi bài dưới đây canh một dòng của bảng [ĐO] trong KDoc
 * [VoiceAppTargets]: đổi một `action`, một khuôn URI hay một mức bằng chứng mà không đo lại thì bài này đỏ —
 * đúng tinh thần CLAUDE.md §10 (*"test khoá lại bài học, không phải thủ tục"*).
 */
class VoiceAppTargetsTest {

    // ══ (1) Tính toàn vẹn của bảng ═══════════════════════════════════════════════════════════════════════

    /** Mã đích là **hợp đồng** giữa `:core` và `:app` ⇒ không được trùng và không được rỗng. */
    @Test
    fun `ma dich duy nhat va co nhan doc duoc`() {
        val keys = VoiceAppTargets.ALL.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "hai dòng trùng mã ⇒ `byKey` trả về dòng nào là ngẫu nhiên")
        VoiceAppTargets.ALL.forEach {
            assertTrue(it.key.isNotBlank(), "mã rỗng")
            assertTrue(it.label.isNotBlank(), "${it.key} thiếu nhãn — câu trả lời sẽ đọc ra mã cho người dùng")
            assertTrue(it.packages.isNotEmpty(), "${it.key} không có gói nào ⇒ không bao giờ chọn được")
        }
    }

    /** Mọi đích phải có ít nhất một cách NÓI, khai ở [VoiceSynonyms.APP_TARGETS] — nếu không thì gọi tên nó bằng gì? */
    @Test
    fun `moi dich deu co it nhat mot cach noi`() {
        VoiceAppTargets.ALL.forEach {
            assertTrue(it.spoken.isNotEmpty(), "${it.key} không có cách nói nào")
            it.spoken.forEach { phrase ->
                assertEquals(phrase.lowercase(), phrase, "cách nói `$phrase` còn chữ hoa — hợp đồng là chữ thường, không dấu")
                assertEquals(VoiceLexicon.deaccent(phrase), phrase, "cách nói `$phrase` còn dấu")
            }
        }
    }

    /**
     * Gói dẫn đường phải lấy từ [NavApps] — **không** chép tay một tên gói nào.
     *
     * [NavApps] là nguồn sự thật đã có, và nó còn bị khoá với `res/xml/nav_accessibility_config.xml` bởi
     * `NavPackageRosterSyncTest`. Một bản sao thứ hai ở đây là đúng lỗi 5-bản-sao mà lớp ấy sinh ra để dọn.
     */
    @Test
    fun `goi dan duong lay tu roster NavApps`() {
        val roster = NavApps.GMAPS + NavApps.WAZE + NavApps.VIETMAP
        VoiceAppTargets.NAV.forEach { t ->
            t.packages.forEach { pkg -> assertTrue(pkg in roster, "${t.key} dùng gói `$pkg` ngoài roster NavApps") }
        }
    }

    // ══ (2) Từng dòng — khoá đúng phép đo 2026-09-14 ═════════════════════════════════════════════════════

    /** [ĐO] YT Music: `MEDIA_PLAY_FROM_SEARCH` + `query` + **bắt buộc** `extra.focus`; thiếu focus ⇒ chỉ mở Home. */
    @Test
    fun `YT Music giu dung ba thanh phan da do`() {
        val t = VoiceAppTargets.byKey(VoiceAppTargets.YT_MUSIC)!!
        val launch = t.launch as VoiceLaunch.Action
        assertEquals(VoiceLaunch.ACTION_MEDIA_PLAY_FROM_SEARCH, launch.action)
        assertEquals(VoiceLaunch.QUERY_EXTRA, launch.extra)
        assertEquals(
            VoiceLaunch.FOCUS_ANY_AUDIO,
            launch.extras[VoiceLaunch.EXTRA_MEDIA_FOCUS],
            "thiếu `extra.focus` thì [ĐO] ý-định chỉ mở màn Home — hỏng IM LẶNG",
        )
        assertEquals(VoiceAppEvidence.MEASURED, t.evidence)
    }

    /** [ĐO] YouTube là app VIDEO, đi cửa `ACTION_SEARCH`; bản trên máy ảo chặn ⇒ mức bằng chứng phải là CHƯA BIẾT. */
    @Test
    fun `YouTube di cua ACTION_SEARCH va van la chua biet`() {
        val t = VoiceAppTargets.byKey(VoiceAppTargets.YOUTUBE)!!
        assertEquals(VoiceLaunch.ACTION_SEARCH, (t.launch as VoiceLaunch.Action).action)
        assertEquals(VoiceAppEvidence.UNKNOWN, t.evidence, "máy ảo rơi vào màn *Update your app* ⇒ chưa đo được")
    }

    /**
     * [ĐO] Google Maps đi `geo:`, **không** `google.navigation:`.
     *
     * `google.navigation:` rơi vào màn *"Update Google Maps"* trên bản của máy ảo ⇒ nó nằm ở [VoiceAppTarget.coord]
     * với mức *chờ xe*, không nằm ở đường chính. Đảo hai cái này là đảo một đường đã đo lấy một đường chưa đo.
     */
    @Test
    fun `Google Maps di geo chu khong di google navigation`() {
        val t = VoiceAppTargets.byKey(VoiceAppTargets.GMAPS)!!
        val uri = (t.launch as VoiceLaunch.Uri).template
        assertTrue(uri.startsWith("geo:0,0?q="), "đường chính phải là `geo:` đã đo; đang là `$uri`")
        assertFalse((t.launch as VoiceLaunch.Uri).needsCoords, "đường chính không được đòi toạ độ")
        assertEquals(VoiceAppEvidence.MEASURED, t.evidence)
        assertTrue(t.coord!!.template.startsWith("google.navigation:ll="), "đường toạ độ giữ nguyên cho ngày đo trên xe")
        assertEquals(VoiceAppEvidence.AWAITING_CAR, t.coordEvidence)
    }

    /** [ĐO] Waze resolve đúng qua `waze://?q=…&navigate=yes`, và có lưới an toàn `geo:`. */
    @Test
    fun `Waze di waze scheme va co luoi an toan geo`() {
        val t = VoiceAppTargets.byKey(VoiceAppTargets.WAZE)!!
        assertEquals("waze://?q=${VoiceLaunch.SLOT}&navigate=yes", (t.launch as VoiceLaunch.Uri).template)
        assertEquals("geo:0,0?q=${VoiceLaunch.SLOT}", (t.fallback as VoiceLaunch.Uri).template)
        assertEquals(VoiceAppEvidence.MEASURED, t.evidence)
    }

    /**
     * [ĐO] VietMap **không có cửa chữ** — và cơ chế đúng (toạ độ) lấy từ nguồn Kiki đã decompile.
     *
     * Đây là dòng dễ bị "sửa cho đẹp" nhất: ai đó thấy `OpenOnly` sẽ tưởng là chỗ chưa làm xong và nhét một
     * `geo:` vào. [ĐO] `geo:` kèm `-p vn.vietmap.live` ⇒ *unable to resolve intent*. Bài này chặn đúng việc đó.
     */
    @Test
    fun `VietMap chi mo app bang duong chu, va nhan diem den bang TOA DO`() {
        val t = VoiceAppTargets.byKey(VoiceAppTargets.VIETMAP)!!
        assertEquals(VoiceLaunch.OpenOnly, t.launch, "[ĐO] VietMap không đăng ký cửa chữ nào")
        assertFalse(t.handsOver)
        assertTrue(t.needsCoords, "phải đi đường toạ độ, không thì câu dẫn đường VietMap không bao giờ giao được")
        val uri = t.coord!!.template
        assertTrue(uri.startsWith("vietmaplive://companion/navigation?"), "khuôn lấy từ nguồn Kiki; đang là `$uri`")
        listOf(VoiceLaunch.LAT, VoiceLaunch.LNG, VoiceLaunch.SLOT).forEach {
            assertTrue(uri.contains(it), "khuôn VietMap thiếu chỗ trống $it")
        }
        assertEquals(VoiceAppEvidence.AWAITING_CAR, t.coordEvidence, "máy ảo không có GPS fix ⇒ chờ xe, không phải chưa biết")
    }

    // ══ (3) Luật chọn đường — CLAUDE.md §6 ═══════════════════════════════════════════════════════════════

    /**
     * **App đang chạy tốt bằng đường CHỮ thì không bị đổi sang đường toạ độ**, dù bảng có sẵn.
     *
     * Đây là §6 viết thành bài canh: đổi đường của Google Maps/Waze (đã đo) để chữa cho VietMap (chưa đo) là
     * đúng lỗi *"suýt làm hỏng hai app đang ổn để chữa cho một app"* mà CLAUDE.md ghi lại.
     */
    @Test
    fun `app co cua chu giu nguyen duong chu du da co toa do`() {
        listOf(VoiceAppTargets.GMAPS, VoiceAppTargets.WAZE).forEach { key ->
            val t = VoiceAppTargets.byKey(key)!!
            assertEquals(t.launch, t.destinationLaunch(hasCoords = true), "$key bị đổi sang đường toạ độ")
            assertEquals(t.launch, t.destinationLaunch(hasCoords = false))
            assertFalse(t.needsCoords, "$key không được bắt geocode — đó là một lượt mạng không cần thiết")
        }
    }

    /** App KHÔNG có cửa chữ thì chỉ đi được khi có toạ độ; không có thì `null` (⇒ mở app trơn + nói rõ). */
    @Test
    fun `app khong co cua chu chi di duoc khi co toa do`() {
        val t = VoiceAppTargets.byKey(VoiceAppTargets.VIETMAP)!!
        assertEquals(t.coord, t.destinationLaunch(hasCoords = true))
        assertNull(t.destinationLaunch(hasCoords = false), "không có toạ độ mà vẫn trả một đường ⇒ bắn một URI thiếu tham số")
    }

    // ══ (4) Tra cứu ══════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `tra theo cach noi, co loc theo loai`() {
        assertEquals(VoiceAppTargets.YT_MUSIC, VoiceAppTargets.bySpoken(listOf("youtube", "music"))?.key)
        assertEquals(VoiceAppTargets.GMAPS, VoiceAppTargets.bySpoken(listOf("ban", "do", "google"))?.key)
        assertNull(VoiceAppTargets.bySpoken(listOf("youtube"), VoiceAppKind.NAV), "lọc loại phải chặn app khác loại")
        assertNull(VoiceAppTargets.bySpoken(listOf("khong", "co", "that")))
    }

    @Test
    fun `tra theo ma va nhan an toan voi ma la`() {
        assertNotNull(VoiceAppTargets.byKey(VoiceAppTargets.WAZE))
        assertNull(VoiceAppTargets.byKey("ma-khong-co"), "mã lạ phải trả null, không được ném")
        assertNull(VoiceAppTargets.byKey(null))
        assertEquals("ma-la", VoiceAppTargets.labelOf("ma-la"), "mã lạ ⇒ trả chính mã, không sập")
    }

    @Test
    fun `chon goi dau tien co mat tren may`() {
        val gmaps = VoiceAppTargets.byKey(VoiceAppTargets.GMAPS)!!
        assertEquals("com.google.android.apps.maps", gmaps.packageIn(setOf("com.google.android.apps.maps", "x.y")))
        assertNull(gmaps.packageIn(setOf("x.y")), "app chưa cài ⇒ null, chỗ gọi nói *chưa cài*")
    }

    /** Số từ dài nhất phải khớp bảng — nó là trần vòng quét của `VoiceIntentParser.appAfterMarker`. */
    @Test
    fun `tran quet cach noi khop bang`() {
        val longest = VoiceAppTargets.ALL.flatMap { it.spoken }.maxOf { VoiceLexicon.tokenize(it).size }
        assertEquals(longest, VoiceAppTargets.LONGEST_SPOKEN, "trần quét lệch ⇒ cách nói dài nhất không bao giờ khớp")
    }
}
