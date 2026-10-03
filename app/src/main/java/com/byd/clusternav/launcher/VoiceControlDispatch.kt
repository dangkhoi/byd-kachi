package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.launcher.voice.partNotOnThisCar

/**
 * ═══ MỘT NÚT XE TỪ GIỌNG NÓI — và **báo xong** cho làn ghi ════════════════════════════════════════════════════
 *
 * Tách khỏi [VoiceDispatcher] ngày 2026-09-27 (2.76 · VOICE-WRITE-LANE) vì trần 500 dòng (CLAUDE.md §4.1), theo
 * **VAI**: tệp kia trả lời *"câu vừa nói là việc gì, đi đường nào, vế nào chạy trước"*; tệp này chỉ trả lời *"MỘT
 * nút xe: ghi gì, chờ ở đâu, nói gì — và lúc nào thì XONG"*. Mã của [run] là `runControl` của 2.75 **chuyển
 * nguyên**, chỉ thêm đúng một điều: mọi lối ra đều gọi [done] **một lần**, để `VoiceDispatcher.runFrom` biết lúc
 * nào vế kế tiếp được ghi (xem KDoc `VoiceWriteLane`).
 *
 * ## Hợp đồng `done`
 * Được gọi **đúng một lần** trên **mọi** đường: nút lạ · đang AUTO không có nấc thấp hơn · chặn vì xe đang chạy ·
 * ghi xong (một lệnh, đồng bộ) · ghi xong (hai lệnh + nhịp 400 ms, về qua `onUi`). Ở nhánh hai lệnh nó đến **sau**
 * nhịp chờ — đó chính là điều VOICE-WRITE-LANE cần: vế sau của câu ghép không được ghi vào giữa nhịp ấy. Lượt đọc
 * lại của [VoiceReadback] (300 ms, luồng nền) **không** giữ `done`: nó chỉ ĐỌC, không ghi, và bắt vế sau chờ nó là
 * cộng 300 ms vào mọi câu ghép mà không đổi một bit nào trên xe.
 */
internal class VoiceControlDispatch(
    private val control: () -> CarControlPort,
    private val state: () -> HomeUiState,
    private val say: (String) -> Unit,
    private val freshCar: (String) -> CarStatus?,
    private val onUi: (() -> Unit) -> Unit,
    private val background: (() -> Unit) -> Unit,
) {

    /**
     * ═══ R5 + E · đọc lại xe rồi mới nói — [VoiceReadback] (trần 500 dòng) ════════════════════════════════════
     * Dựng **một lần** cho cả đời cầu: nó chỉ cầm chính những lambda mà cầu này đã cầm.
     */
    private val readback = VoiceReadback(control = control, say = say, onUi = onUi, background = background)

    /** UX4 · thi hành [ClimateAuto.StepPlan]; nhánh rời-AUTO chờ 400 ms nên phải xuống luồng nền — xem [VoiceClimateStep]. */
    private val climate = VoiceClimateStep(control = control, onUi = onUi, background = background)

    /**
     * Một nút.
     *
     * Lệnh **tương đối** (*"tăng gió"*) được quy về tuyệt đối **ở đây**. `:core` cố ý không làm việc này (xem KDoc
     * [VoiceIntent.Control.relative]): nó không biết xe đang ở mức nào.
     *
     * ## ═══ H1 · MỐC để cộng phải là mức THẬT CỦA XE, không phải mức trong RAM ═════════════════════════════
     * [ĐO] tester 1.66: *"điều hoà chỉnh lung tung, quất một phát như lò heo quay"*. Gốc: mốc lấy từ
     * [ControlTileState.shared] — một bảng **lạc quan**, khởi tạo bằng `ControlDef.value` (gió **4** · nhiệt **22**) và
     * chỉ đổi khi chính Kachi bấm. Người lái chỉnh gió ở màn BYD gốc thì bảng này không hề biết ⇒ [ĐO xe 2026-09-16]
     * xe đang **gió 1**, nói *"tăng gió"*, Kachi tính 4 + 1 và bắn **5** — nhảy bốn nấc trong một câu.
     *
     * Nay hỏi xe trước ([CarControlPort.readState] — đi qua `ControlDef.readKey`, khoá ĐỌC, **không** phải `bindingKey`
     * là khoá GHI). Đọc không được (`null`: off-car · máy ảo · nút chưa có đường đọc) ⇒ **lùi về đúng hành vi 1.68**,
     * vì ở đó thật sự không có con số nào tốt hơn — và một con số bịa thì tệ hơn hẳn một con số cũ.
     *
     * Một lượt đọc cho MỘT câu lệnh, theo yêu cầu — không phải vòng poll (ngân sách [ĐO xe 1.68] 33 lượt đọc HAL/phút).
     *
     * @param done báo *"vế này đã ghi xong"* cho làn ghi — hợp đồng ở KDoc lớp.
     */
    fun run(i: VoiceIntent.Control, done: () -> Unit) {
        val def = ControlRegistry.byId(i.id)
        if (def == null) { say(VoiceReply.failed(i)); done(); return }
        val st = ControlTileState.shared
        // ═══ UX4 — nấc ĐÁY của nút có `autoId` tên là **AUTO**, không phải mức 0 ══════════════════════════════
        // Cùng bảng quyết định THUẦN mà cú chạm −/+ dùng ([ClimateAuto.stepPlan] ← `ControlTileFactory.nudge`): nói
        // *"giảm gió"* ở mức 1 phải **bật gió tự động**, chứ không ghi mức 0 ([ĐO xe 2026-09-20] xe **bỏ qua** lệnh
        // ấy ⇒ ngón tay và câu nói làm hai việc khác nhau cho cùng một ô). Nút không khai `autoId` đi nhánh `SetLevel`
        // y như trước — không một `if (def.id == "fan")` nào (CLAUDE.md §7). `autoOn` lấy từ ẢNH CHỤP đang có
        // (`ac_auto` nạp cùng `fan` — `CarDataDemand.controlsOf`), KHÔNG đọc thêm một lượt HAL: ngân sách là MỘT lượt
        // đọc cho MỘT câu ([ĐO xe 1.68] 33 lượt/phút), và `null` = *"chưa biết"* đã cho đúng nhánh bật-auto ở nấc đáy.
        val plan = if (i.relative == 0) null else {
            val actual = runCatching { control().readState(def.id) }.getOrNull() ?: st.value(def)
            val autoOn = ClimateAuto.autoOnFromControl(state().carStatus.controls[def.autoId])
            ClimateAuto.stepPlan(def, actual, i.relative * def.step, autoOn)
        }
        val arg = plan?.shown ?: (i.value ?: 1)
        val shown = if (i.relative != 0) VoiceIntent.Control(def.id, arg) else i
        // Đang AUTO mà còn nói *"giảm"* (`act = false`): mã này không có nấc TẮT ⇒ **không bắn gì**, nhưng vẫn NÓI RA.
        if (plan != null && !plan.act) { say(VoiceReply.autoLevel(def.id)); done(); return }
        // ═══ C (owner test xe 2026-09-19) · CỐP/CA-PÔ chỉ MỞ được khi xe đang DỪNG ════════════════════════
        //
        // Đặt **trước** [CarControlPort.actByKind], sau khi đã biết `arg`: chỉ chặn lượt MỞ (`arg > 0`) — đóng
        // cốp lúc đang chạy là việc nên làm, chặn nó lại là chặn đúng đường chữa. Tập mã ở
        // [CtlSafetyPolicy.REQUIRES_STATIONARY] (KDoc ở đó giải thích vì sao kính/cửa sổ trời KHÔNG vào).
        //
        // ## Đọc TƯƠI, và `null` ⇒ CHO PHÉP (fail-open) — một lựa chọn có chủ ý
        // Hỏi [freshCar] trước vì vòng poll chỉ đọc datum **đang hiện trên màn** (`CarDataDemand`), nên ảnh chụp
        // có thể mang tốc độ của lần cuối cái ô ấy còn trên màn — dùng nó để gate là gate bằng một con số cũ.
        //
        // Không đọc được (`null`) thì **cho mở**: [ĐO] `speed` ở mức PROVEN (`TelemetryRegistry`), tức trên xe
        // thật gate này có số để chạy; `null` gần như chỉ xảy ra off-car/máy ảo, và ở đó chẳng có cốp nào để bung.
        // Chọn fail-CLOSED thì mọi lần đọc hụt trên xe đỗ sẽ thành một lời từ chối cho một việc hoàn toàn an toàn
        // (mở cốp lúc đỗ là ca dùng **thường nhất** của nút này) — tức một gate an toàn tự biến thành lỗi.
        if (CtlSafetyPolicy.requiresStationary(def.id) && arg > 0) {
            val kmh = runCatching { freshCar("speed") }.getOrNull()?.drivetrain?.speedKmh
                ?: state().carStatus.drivetrain.speedKmh
            if (kmh != null && kmh > 0) { say(VoiceReply.notWhileMoving(shown)); done(); return }
        }
        // ═══ [SOÁT 2.74 · P2] Cú ghi RỜI AUTO chờ 400 ms ⇒ đuôi *"nói gì"* phải là một LỜI GỌI LẠI ═════════════
        //
        // `VoiceSession` gọi lớp này trên luồng VẼ, nên nhánh hai-lệnh của [VoiceClimateStep] trả lời từ luồng nền
        // (qua `onUi`), ba nhánh còn lại trả lời ngay trên luồng gọi. Viết thành `fun` CỤC BỘ — không phải một
        // `private fun` sáu tham số — để `def`/`arg`/`shown`/`st`/`plan` giữ đúng nghĩa tại chỗ: mã dưới đây y
        // nguyên bản trước, chỉ khác ở chỗ nó được gọi từ đâu. Và [done] đứng **cuối** nó: vế sau của câu ghép chỉ
        // được ghi khi vế này đã nói xong (VOICE-WRITE-LANE).
        fun finish(ok: Boolean) {
            // Ghi lại trạng thái lạc quan y như cú chạm: hai bề mặt phải nói cùng một điều về MỘT cái xe.
            if (ok) when (def.kind) {
                ControlKind.TOGGLE -> st.setOn(def.id, arg > 0)
                ControlKind.STEP -> st.setValue(def.id, arg)
                ControlKind.SELECT -> st.setSel(def.id, arg)
                else -> Unit
            }
            if (!ok) {
                // ═══ R5 (live-state) — *"hỏng lần này"* và *"xe này không có"* là HAI câu khác nhau ═════════
                //
                // [ĐO xe 2026-09-16] `ac_auto` khai feature `1324355606`, id đó **không nằm trong bảng của xe
                // owner**, mà owner xác nhận xe **CÓ** điều hoà auto. Tới 1.68 cả hai ca đều ra đúng một câu
                // (*"xe không nhận lệnh"*), nên người lái nói *"điều hoà"*, nghe báo hỏng, rồi **thử lại** — mãi.
                // Tester nêu đúng chỗ này: *"điều hoà với lọc bụi nó không hiểu là cái gì"*.
                //
                // Phép phân biệt nằm ở tầng BIẾT XE (cổng điều khiển hỏi bảng feature-id thật); `:core` chỉ giữ
                // **hình dạng** của ca và câu chữ ([VoiceReply.uncontrollable]). Không đọc được bảng ⇒
                // `wiredOnThisCar` trả `true` ⇒ y nguyên câu cũ, không bao giờ đoán bừa là *"xe không có"*.
                val absent = VoiceReply.uncontrollable(shown) { id ->
                    !runCatching { control().wiredOnThisCar(id) }.getOrDefault(true)
                }
                // FIX286 · SR4 — vế hẹp hơn của `absent`: xe TỰ BÁO không có bộ phận (cổng có-mặt) ⇒ câu riêng.
                val noPart = absent && VoiceReply.uncontrollable(shown) { id ->
                    runCatching { control().partAbsentOnThisCar(id) }.getOrDefault(false)
                }
                say(
                    when {
                        noPart -> VoiceReply.partNotOnThisCar(shown)
                        absent -> VoiceReply.notOnThisCar(shown)
                        else -> VoiceReply.failed(shown)
                    },
                )
                done()
                return
            }
            // ═══ E (owner test xe 2026-09-19) · nút nào ĐỌC ĐƯỢC thì đọc lại xác nhận, không trả lời mù ═════════
            //
            // Ba lối, và cái thứ ba là chỗ thành thật: nút **không có** [ControlDef.readKey] thì giữ nguyên câu 1.79
            // (còn cả đuôi *"chưa kiểm trên xe"*) — ở đó thật sự không có gì để kiểm, nên hedge là đúng.
            //
            // Cổng kind CỐ Ý hẹp hơn *"có readKey"*: [ControlKind.BUTTON] là nút bấm-một-phát (`pm25_clean_now`), mức
            // sau khi bấm **không nói gì** về việc cú bấm có tới hay không ⇒ so mức ở đó sẽ báo *"xe không nhận lệnh"*
            // cho một cú bấm hoàn toàn bình thường. [ControlKind.SELECT] cũng vậy: mức của nó là **chỉ số lựa chọn**,
            // `> 0` không mang nghĩa *bật* (chỉ số 0 là một lựa chọn hợp lệ, không phải "tắt").
            when {
                // UX4 · `EnableAuto` (auto BẬT, không ghi mức): mức xe đang thổi KHÔNG đổi, nên đọc-lại-so-mức sẽ nói
                // *"đã đặt Gió = 1"* — đúng số, sai việc. Câu thuật trạng thái mới là câu thật.
                plan?.auto == true -> say(VoiceReply.autoLevel(def.id))
                def.kind == ControlKind.STEP -> readback.step(shown, st)
                (def.kind == ControlKind.TOGGLE || def.kind == ControlKind.COVER) && def.readKey.isNotBlank() ->
                    readback.act(shown, def, arg, st)
                else -> say(VoiceReply.done(shown))
            }
            done()
        }
        if (plan == null) finish(runCatching { control().actByKind(def.id, arg) }.getOrDefault(false))
        else climate.apply(def, plan) { ok -> finish(ok) }
    }
}
