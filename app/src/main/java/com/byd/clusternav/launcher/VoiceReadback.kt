package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceReply

/**
 * ═══ GHI XONG THÌ **ĐỌC LẠI XE** RỒI MỚI NÓI ══════════════════════════════════════════════════════════════════
 *
 * Tách khỏi [VoiceDispatcher] ngày 2026-09-19 (lượt E) vì trần 500 dòng (CLAUDE.md §4.1), và tách theo **VAI**:
 * tệp kia trả lời *"câu này là việc gì, đi đường nào"*, tệp này chỉ trả lời **một** câu hỏi — *"xe có thật sự ở
 * mức mình vừa ghi không, và nói gì cho người lái"*. Cả hai lối vào ([step] · [act]) dùng chung đúng một khuôn ba
 * nhánh, nên để chúng cạnh nhau là cách duy nhất khiến chúng không lệch nhau về sau.
 *
 * ## Khuôn BA NHÁNH (chung cho cả hai lối vào — đây là bất biến của tệp)
 *  1. **không đọc được** (`null`: off-car · máy ảo · nút chưa có đường đọc · trim không provision) ⇒ [VoiceReply.done]
 *     — câu có đuôi *"chưa kiểm trên xe"* nếu mức bằng chứng thấp. Đây là **sự thành thật**, không phải thiếu sót:
 *     không có gì để hứa thì không hứa;
 *  2. **đọc được, KHỚP** ⇒ câu *đã xác nhận* (bỏ hedge) — [VoiceReply.doneConfirmed] / [VoiceReply.doneActual];
 *  3. **đọc được, LỆCH** ⇒ đọc lại **đúng MỘT lần** sau [READBACK_SETTLE_MS] trên luồng NỀN rồi mới nói. Một lần,
 *     không phải một vòng lặp: sau chừng ấy mà vẫn lệch thì đó là chỗ lệch THẬT và người lái cần nghe nó.
 *
 * ## Vì sao nhánh 3 phải chờ, không nói ngay
 * Lượt đọc chạy vài ms sau lượt ghi, và bus có thể còn mang giá trị CŨ. Nói ngay *"xe không nhận lệnh"* ở đó là
 * **báo một cái sai trên một lệnh hoàn toàn thành công** — và nó sai đúng theo kiểu làm người ta thôi tin cả tính
 * năng. Với TOGGLE/COVER thân xe (kính · cốp) khoảng chờ ấy còn cần hơn nữa: bộ phận **chạy vật lý vài giây**, nên
 * lượt đọc đầu gần như chắc chắn còn thấy mức cũ.
 *
 * `onUi` có mặt vì [ControlTileState] và câu nói đều thuộc luồng VẼ; `background` để lượt chờ không chặn luồng ấy
 * (xe đang chạy). Cả hai là lambda ⇒ bài kiểm chạy thẳng, tất định, off-car.
 */
internal class VoiceReadback(
    private val control: () -> CarControlPort,
    private val say: (String) -> Unit,
    private val onUi: (() -> Unit) -> Unit,
    private val background: (() -> Unit) -> Unit,
) {

    /**
     * ═══ R5 · NÚT [ControlKind.STEP] — đọc lại **CON SỐ** (spec `kachi-voice-feedback.html` T10) ══════════════
     *
     * Tới 1.65 câu trả lời dựng từ **con số vừa gửi**, nên *"đặt nhiệt độ 24"* trên một chiếc xe kẹp về 17 vẫn
     * nghe là *"✓ Đặt Nhiệt độ = 24"* — máy nói dối đúng cái ca người lái không tự kiểm được vì đang nhìn đường.
     *
     * Ở nhánh KHỚP, [VoiceReply.doneActual] cố ý trả về **đúng câu cũ** (không thêm chữ nào cho một kết quả
     * khớp — xem KDoc ở đó); ở nhánh LỆCH nó nói ra **cả hai** con số và **không** suy diễn nguyên nhân (kẹp dải ·
     * chưa kịp áp · nút không ăn — ba nguyên nhân cho cùng một chỗ lệch, phân biệt cần một phép đo trên xe).
     *
     * Trạng thái ô cũng sửa theo số thật, để thanh nút và câu nói không nói hai điều khác nhau về một cái xe.
     */
    fun step(shown: VoiceIntent.Control, st: ControlTileState) {
        val port = control()
        // Câu không nêu đích (`value == null`) thì không có gì để so — giữ nguyên câu cũ. Bộ phân tích không
        // sinh ra ca này cho STEP (xem `VoiceIntentParser.step`), nhưng một nhánh mới mai sau thì có thể.
        val want = shown.value ?: run { say(VoiceReply.done(shown)); return }
        val first = runCatching { port.readStep(shown.id) }.getOrNull()
        if (first == null || first == want) {
            say(if (first == null) VoiceReply.done(shown) else VoiceReply.doneActual(shown, first))
            return
        }
        background {
            runCatching { Thread.sleep(READBACK_SETTLE_MS) }
            val again = runCatching { port.readStep(shown.id) }.getOrNull() ?: first
            onUi {
                st.setValue(shown.id, again)
                say(VoiceReply.doneActual(shown, again))
            }
        }
    }

    /**
     * ═══ E (owner test xe 2026-09-19) · NÚT BẬT/TẮT + MỞ/ĐÓNG — đọc lại **MỨC** ═══════════════════════════════
     *
     * Owner: nút nào **đọc được** thì đừng trả lời mù. Tới 1.79 mọi TOGGLE/COVER đều nhận đúng một câu —
     * [VoiceReply.done] kèm đuôi *"chưa kiểm trên xe"* — kể cả những nút mà xe **đang trả lời được ngay lúc đó**
     * (`sunroof` · `trunk` · `recirc` · `drl` · `anion` · `ac_auto` · 4 kính riêng… đều có [ControlDef.readKey]).
     * Hai chuyện sai cùng lúc: (a) hedge *"chưa kiểm"* nói về mức bằng chứng **tĩnh** trong mã, trong khi bằng
     * chứng **vừa được tạo ra trên chính chiếc xe này, giây vừa rồi** — mạnh hơn hẳn; (b) lệnh **không ăn** (xe
     * nhận `rc=0` rồi chẳng làm gì — ca đã thấy thật với `sunroof`, và với `hood` trước khi nút ấy bị xoá ở
     * 1.85) vẫn được báo ✓, tức người lái tưởng
     * cốp đã mở.
     *
     * ⇒ Cùng khuôn ba nhánh của [step], chỉ khác **đơn vị so sánh**: STEP so *con số*, còn ở đây so **mức bật/tắt**
     * (`> 0`) vì đó đúng là ngữ nghĩa mà [CarControlPort.readState] trả về (0/1 cho TOGGLE, chỉ số cho COVER) và
     * mọi phép biến đổi của trim (thang mức ghế · AUTO đảo cờ `readInverted`) đã được làm **một chỗ** ở
     * `HalBindingTable.readState` — ở đây không lặp lại phép nào.
     *
     * Nhánh 3 lệch-sau-khi-chờ ⇒ [VoiceReply.failed] (*"xe không nhận lệnh"*), **không** phải ✓: khác với STEP
     * (nơi lệch còn có thể là *"xe kẹp về dải của nó"*, một kết quả hợp lệ), một nút bật/tắt chỉ có hai mức nên
     * không mức nào là "kẹp" — vẫn ở mức cũ nghĩa là **chưa xảy ra gì**.
     *
     * ⚠ …**trừ** bộ phận chạy bằng mô-tơ ([CtlSafetyPolicy.MOVES_SLOWLY]): ở đó 300 ms là *đang chạy dở*, không phải
     * *không ăn*, nên chỗ lệch dai dẳng rơi về [VoiceReply.done] (✓ + đuôi hedge — đúng câu 1.79) thay vì một lời
     * khẳng định sai. Lượt đọc lại là bằng chứng **một chiều**: nó chứng minh được THÀNH CÔNG, không chứng minh được
     * THẤT BẠI. [ĐO off-car] không có cổng ấy thì lệnh *"đóng kính trước trái"* — mức **PROVEN**, đã chạy thật trên
     * xe owner — gần như **luôn** bị báo *"✗ xe không nhận lệnh"*, vì khoá đọc của nó là phần trăm 0–100 và cửa kính
     * mất vài giây mới về 0.
     *
     * @param arg mức vừa GHI (>0 = bật/mở) — lấy từ chính giá trị đã gửi, không đọc lại từ đâu khác.
     */
    fun act(shown: VoiceIntent.Control, def: ControlDef, arg: Int, st: ControlTileState) {
        val port = control()
        val wantOn = arg > 0
        val first = runCatching { port.readState(def.id) }.getOrNull()
        // Nhánh 1 — không đọc được ⇒ giữ nguyên câu 1.79 (còn cả đuôi hedge). Không bịa một lời xác nhận.
        if (first == null) { say(VoiceReply.done(shown)); return }
        if ((first > 0) == wantOn) { say(VoiceReply.doneConfirmed(shown)); return }
        background {
            runCatching { Thread.sleep(READBACK_SETTLE_MS) }
            val again = runCatching { port.readState(def.id) }.getOrNull() ?: first
            onUi {
                val confirmed = (again > 0) == wantOn
                // ⚠ [SOÁT lượt E · P1] Lượt đọc lại là bằng chứng **một chiều**: khớp ⇒ chắc chắn ăn; còn lệch thì
                // chỉ kết luận được *"chưa ăn"* với bộ phận đổi mức gần như tức thì. Với bộ phận chạy bằng mô-tơ
                // ([CtlSafetyPolicy.MOVES_SLOWLY] — kèm con số đo) 300 ms là đang-chạy-dở, nên ở đó câu đúng là câu
                // 1.79 (✓ + đuôi hedge): lệnh đã được xe nhận (`rc=0`), chỉ là chưa kiểm được tới đích.
                val slow = CtlSafetyPolicy.movesSlowly(def.id)
                // Bảng dùng chung phải mang mức THẬT, kể cả khi lệnh không ăn: nếu không, ô trên thanh nút sáng
                // như đã bật trong khi câu nói vừa bảo là chưa — đúng bất biến mà `ControlTileState.shared` sinh
                // ra để giữ. Chỉ TOGGLE có ô hai-mức để sửa; COVER/SELECT không giữ cờ `on` nào (xem `runControl`).
                // Bộ phận đang chạy dở thì lượt đọc KHÔNG đáng tin hơn mức lạc quan ⇒ không ghi đè (ghi đè là để
                // ô hiện "đang mở" cho một cửa kính vừa được lệnh ĐÓNG và đang đóng thật).
                if (def.kind == ControlKind.TOGGLE && !(slow && !confirmed)) st.setOn(def.id, again > 0)
                say(
                    when {
                        confirmed -> VoiceReply.doneConfirmed(shown)
                        slow -> VoiceReply.done(shown)
                        else -> VoiceReply.failed(shown)
                    },
                )
            }
        }
    }

    private companion object {
        /**
         * Chờ bao lâu rồi đọc lại khi lượt đọc ĐẦU báo khác mức vừa gửi.
         *
         * 300 ms là một **giả định có chủ ý**, chưa phải phép đo: [CHƯA BIẾT] xe mất bao lâu từ lúc nhận lệnh tới
         * lúc bus mang mức mới (spec `kachi-voice-feedback.html` §7 **OQ6** ghi cách chốt — bấm giờ giữa `write`
         * và lần đọc đầu tiên trả mức mới, trên xe thật). Chọn số này vì nó nằm dưới ngưỡng người ta cảm thấy là
         * *"máy treo"* (~500 ms) mà vẫn dôi so với một nhịp CAN thường.
         *
         * ⚠ Với bộ phận **chạy vật lý** (kính · cốp · cửa sổ trời) 300 ms gần như chắc chắn **chưa** đủ — chúng
         * mất vài giây. Hệ quả ấy nay được chặn ở tầng KẾT LUẬN, không ở hằng này: mã trong
         * [CtlSafetyPolicy.MOVES_SLOWLY] có chỗ lệch dai dẳng ⇒ trả về câu ✓ + hedge chứ **không** bao giờ thành
         * *"✗ xe không nhận lệnh"* ([SOÁT lượt E · P1] — trước bản vá đó, *"đóng kính trước trái"* bị báo ✗ gần như
         * mọi lần). Nới hằng này cho đủ một cửa kính là bắt **mọi** câu trả lời chậm thêm vài giây ⇒ cách chữa đúng
         * khi có số đo trên xe là khoảng chờ **theo từng nút**, không phải một hằng chung nới rộng.
         */
        const val READBACK_SETTLE_MS = 300L
    }
}
