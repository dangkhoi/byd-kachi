package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.behind.BehindHomeSequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L4 · D1 — sổ kết quả chuyến nói THẬT. Khoá ca hiện trường 2.86 (owner 03/10: chạy nền / nhạc không làm gì mà Cài đặt vẫn
 * *"đã chạy lúc HH:mm"*): [ĐO máy ảo `e2e/e6-no-app-slot`] bản 2.86 ghi NGUYÊN VĂN
 * `code=RAN;…;d=ready after 20263ms | com.waze:bg-NO_STAGE | music:com.google.android.apps.youtube.music:NO_STAGE` — mọi
 * bước 0 lệnh mà mã chuyến là RAN. Nay mã chuyến SUY từ mã từng bước ([TripOutcome.tripCode]).
 */
class TripOutcomeTest {

    private val waze = "com.waze"
    private val ytm = "com.google.android.apps.youtube.music"

    @Test
    fun `chuyen e6 - moi buoc khong lam gi la NOOP, KHONG BAO GIO RAN`() {
        // Hai bước của bản ghi 2.86 nguyên văn, dịch sang mã: `bg-NO_STAGE` + `music:…:NO_STAGE`.
        val steps = listOf(
            TripStep(waze, TripStepKind.BACKGROUND, TripOutcome.ofBehind(BehindHomeSequence.Result.NO_STAGE)),
            TripStep(ytm, TripStepKind.MUSIC, TripStepCode.NO_STAGE),
        )
        assertEquals(TripGate.Code.NOOP, TripOutcome.tripCode(steps))
        assertEquals(TripGate.Code.NOOP, TripOutcome.tripCode(emptyList()), "hết hạn trước bước đầu ⇒ không làm gì")
    }

    @Test
    fun `ma chuyen - het dat la RAN, lan la PARTIAL, chi gui lenh ma chua thay phat la PARTIAL`() {
        val moved = TripStep(waze, TripStepKind.BACKGROUND, TripStepCode.MOVED)
        val playing = TripStep(ytm, TripStepKind.MUSIC, TripStepCode.PLAYING)
        val noSession = TripStep("com.google.android.youtube", TripStepKind.MUSIC, TripStepCode.NO_SESSION)
        val sent = TripStep(ytm, TripStepKind.MUSIC, TripStepCode.SENT)
        assertEquals(TripGate.Code.RAN, TripOutcome.tripCode(listOf(moved, playing)))
        assertEquals(TripGate.Code.PARTIAL, TripOutcome.tripCode(listOf(moved, noSession)))
        assertEquals(TripGate.Code.PARTIAL, TripOutcome.tripCode(listOf(sent)), "đã làm mà chưa thấy kết quả ≠ không làm gì")
        assertEquals(TripGate.Code.NOOP, TripOutcome.tripCode(listOf(noSession)))
        // App đã ở ô / đã chạy sẵn = đạt điều người dùng muốn (app đang chạy), không phải "bỏ qua".
        assertEquals(TripGate.Code.RAN, TripOutcome.tripCode(listOf(
            TripStep(waze, TripStepKind.BACKGROUND, TripStepCode.IN_SLOT),
            TripStep(ytm, TripStepKind.BACKGROUND, TripStepCode.ALREADY_RUNNING),
        )))
    }

    @Test
    fun `ma tung buoc - tu ket qua chuoi chay ngam, loai tru, cong nhac`() {
        assertEquals(TripStepCode.NO_STAGE, TripOutcome.ofBehind(null))
        assertEquals(TripStepCode.SYSTEM_APP, TripOutcome.ofBehind(BehindHomeSequence.Result.SYSTEM_APP), "D1(e): không còn KEPT_UNDER chung chung")
        assertEquals(TripStepCode.NO_CHANNEL, TripOutcome.ofBehind(BehindHomeSequence.Result.NO_CHANNEL))
        assertEquals(TripStepCode.HOME_RESTORED, TripOutcome.ofBehind(BehindHomeSequence.Result.X_FRONT_HOME_RESTORED))
        BehindHomeSequence.Result.values().forEach { TripOutcome.ofBehind(it) }   // đủ nhánh, không ném
        assertEquals(TripStepCode.SYSTEM_APP, TripOutcome.ofSkip(TripPlan.Why.SYSTEM_APP))
        assertEquals(TripStepCode.IN_SLOT, TripOutcome.ofSkip(TripPlan.Why.IN_SLOT))
        assertEquals(TripStepCode.UNKNOWN_MEDIA, TripOutcome.ofGate(TripMusicPlan.Gate.UNKNOWN_MEDIA))
        assertEquals(TripStepCode.SELF_PLAYING, TripOutcome.ofGate(TripMusicPlan.Gate.SELF_PLAYING))
        assertEquals(null, TripOutcome.ofGate(TripMusicPlan.Gate.GO))
    }

    /**
     * Review 287 [P3]: đường ô sống, ROM bỏ qua khoá giữ chỗ ⇒ `ANCHOR_IN_FRONT` (X ở lại dưới app C trong màn ảo ô, BEHIND-HOME
     * TỰ TẮT cho cả tiến trình). Bản cũ gộp vào HOME_RESTORED ("đang chạy sau màn nhà") ⇒ ảnh chụp Cài đặt giấu đúng sự kiện làm
     * tính năng tắt (các bước sau chỉ thấy DISABLED). [P1]: đọc lại hỏng sau lệnh ⇒ `UNREAD` = CHƯA RÕ, không bao giờ RAN.
     */
    @Test
    fun `giu cho len truoc man nha co ma rieng, doc lai hong la chua ro`() {
        val anchor = TripOutcome.ofBehind(BehindHomeSequence.Result.ANCHOR_IN_FRONT)
        assertEquals(TripStepCode.ANCHOR_IN_FRONT, anchor)
        assertTrue(anchor != TripStepCode.HOME_RESTORED, "không được nói 'sau màn nhà' khi X còn trong chỗ dàn dựng")
        val unread = TripOutcome.ofBehind(BehindHomeSequence.Result.UNREAD)
        assertEquals(TripStepCode.UNREAD, unread)
        assertEquals(TripStepCode.Result.UNCONFIRMED, unread.result)
        assertEquals(TripGate.Code.PARTIAL, TripOutcome.tripCode(listOf(TripStep(waze, TripStepKind.BACKGROUND, unread))), "chưa rõ ≠ đã chạy")
        // Khứ hồi qua sổ bền (tên enum ASCII).
        val steps = listOf(TripStep(waze, TripStepKind.BACKGROUND, anchor), TripStep(ytm, TripStepKind.MUSIC, TripStepCode.SLOT_NOT_READY))
        assertEquals(steps, TripOutcome.decode(TripOutcome.encode(steps)))
    }

    /**
     * Review 287 [P2]: K4-VIEW của app Ở Ô mà ô chưa có màn ảo lúc giao (cổng trả `null`) hoặc màn ảo ô không có task của app
     * (`TripMusicView.NOT_IN_SLOT` ⇒ `X_NOT_STAGED`) ⇒ 0 lệnh ⇒ `SLOT_NOT_READY` (NOOP) — không "đã gửi", không dàn qua chỗ khác.
     */
    @Test
    fun `VIEW cua app o o - o chua san la SLOT_NOT_READY, ngoai o theo ma chuoi chay ngam`() {
        assertEquals(TripStepCode.SLOT_NOT_READY, TripOutcome.ofView(inSlot = true, r = null))
        assertEquals(TripStepCode.SLOT_NOT_READY, TripOutcome.ofView(true, BehindHomeSequence.Result.X_NOT_STAGED))
        assertEquals(TripStepCode.Result.NOOP, TripStepCode.SLOT_NOT_READY.result)
        assertEquals(TripStepCode.MOVED, TripOutcome.ofView(true, BehindHomeSequence.Result.MOVED), "ở lại ô / về ô")
        assertEquals(TripStepCode.NOT_STAGED, TripOutcome.ofView(false, BehindHomeSequence.Result.X_NOT_STAGED))
        assertEquals(TripStepCode.NO_STAGE, TripOutcome.ofView(false, null))
    }

    @Test
    fun `so ket qua - truong s= khu hoi, ban ghi cu khong co s= van doc duoc`() {
        // [ĐO máy ảo 03/10 `p3/e2e-L4/e6c-hidden`] bản ghi L4 nguyên văn.
        val l4 = "v=1;trip=n39.t302904423;code=RAN;at=1791024362549;d=ready after 20839ms;" +
            "s=com.waze:B:MOVED,com.google.android.apps.youtube.music:M:PLAYING"
        val r = TripGate.decodeResult(l4)!!
        assertEquals(TripGate.Code.RAN, r.code)
        assertEquals(listOf(TripStep(waze, TripStepKind.BACKGROUND, TripStepCode.MOVED), TripStep(ytm, TripStepKind.MUSIC, TripStepCode.PLAYING)), r.steps)
        assertEquals(l4, TripGate.encodeResult(r), "khứ hồi đúng từng byte")
        // [ĐO máy ảo 03/10 `p3/e2e-L4/e9`] YouTube là app hệ thống ⇒ một bước SYSTEM_APP, chuyến NOOP.
        val sys = TripGate.decodeResult("v=1;trip=n39.t303484682;code=NOOP;at=1791024930687;d=ready after 20492ms;s=com.google.android.youtube:M:SYSTEM_APP")!!
        assertEquals(TripGate.Code.NOOP, sys.code)
        assertEquals(TripStepCode.SYSTEM_APP, sys.steps.single().code)
        // Bản ghi 2.86 (không `s=`) — vẫn đọc được, không bước nào.
        val old = TripGate.decodeResult("v=1;trip=n39.t299521250;code=RAN;at=1791020963986;d=ready after 20263ms | com.waze:bg-NO_STAGE")!!
        assertTrue(old.steps.isEmpty())
    }

    /**
     * [ĐO máy ảo 03/10 `p3/e2e-L4/e12-channel-down`]: kênh `PORT_CLOSED` ⇒ chuỗi SẴN không chạy ⇒ chuyến không chạy, sổ giữ
     * nguyên kết quả chuyến TRƯỚC (`n39.t304770881`, RAN). Lần nổ máy mới (claim tắt-máy mới ⇒ id khác) ⇒ `NOT_RUN`: Cài đặt
     * phải nói "lần này chưa chạy", không để dòng kết quả cũ trông như của lần này.
     */
    @Test
    fun `lan no may nay - chua chay thi noi ra, dang chay thi noi dang chay`() {
        val last = TripGate.decodeResult("v=1;trip=n39.t304770881;code=RAN;at=1791026228257;d=ready after 20240ms;s=com.waze:B:MOVED")
        val fired = TripGate.Ledger("n39.t304770881", TripGate.Phase.FIRED, 1)
        assertEquals(TripGate.Now.SHOWN, TripGate.now("n39.t304770881", fired, last))
        assertEquals(TripGate.Now.NOT_RUN, TripGate.now("n39.t304860000", fired, last), "claim mới, chuyến chưa chạy")
        assertEquals(TripGate.Now.RUNNING, TripGate.now("n39.t304860000", TripGate.Ledger("n39.t304860000", TripGate.Phase.CLAIMED, 1), last))
        assertEquals(TripGate.Now.SHOWN, TripGate.now("n39.t304860000", TripGate.Ledger("n39.t304860000", TripGate.Phase.FIRED, 1), last),
            "FIRED mà ghi kết quả hỏng ⇒ không nói sai 'chưa chạy'")
        assertEquals(TripGate.Now.NOT_RUN, TripGate.now("n40.b", null, null), "chưa từng có chuyến nào")
    }

    @Test
    fun `ma hoa de dai - muc la bi bo, khong nem, khong dau ngan cua so`() {
        val raw = "com.waze:B:MOVED,bad,x;y:B:MOVED,com.a:Q:MOVED,com.b:M:NOPE,com.c:N:OPENED"
        assertEquals(
            listOf(TripStep(waze, TripStepKind.BACKGROUND, TripStepCode.MOVED), TripStep("com.c", TripStepKind.NORMAL, TripStepCode.OPENED)),
            TripOutcome.decode(raw),
        )
        val many = (1..12).map { TripStep("com.a$it", TripStepKind.BACKGROUND, TripStepCode.MOVED) }
        val enc = TripOutcome.encode(many)
        assertEquals(TripOutcome.MAX_STEPS, TripOutcome.decode(enc).size)
        assertTrue(';' !in enc && '=' !in enc, "không mang dấu ngăn của sổ: $enc")
    }
}
