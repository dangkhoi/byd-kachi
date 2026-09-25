package com.byd.clusternav.automation

import android.content.Context
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.BydHalGateway
import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.launcher.automation.RainDefrostAction
import com.byd.clusternav.launcher.automation.RainDefrostOwner
import com.byd.clusternav.launcher.automation.RainDefrostPolicy
import com.byd.clusternav.launcher.automation.RainDefrostState
import com.byd.clusternav.rainDefrostEnabled
import com.byd.clusternav.rainDefrostFront
import com.byd.clusternav.rainDefrostRear

/**
 * ═══ AUTOMATION #1 · MƯA → TỰ SẤY KÍNH · PHẦN CHẠM XE ════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R1 (§Design › :app). Mọi **luật** nằm ở `:core`
 * ([RainDefrostPolicy] · [RainDefrostOwner]); lớp này chỉ làm ba việc mà chỉ tầng Android làm được: **đọc cảm
 * biến mưa**, **đọc trạng thái sấy**, **ghi hai nút sấy**.
 *
 * ## Ba đường, mỗi đường đi qua hạ tầng ĐÃ CÓ (không mở reflection mới)
 *  1. **Đọc mưa** — feature-id `SETTING_FRONT_RAIN_WIPER_SPEED` trên `BYDAutoSettingDevice`, qua
 *     [BydHalGateway.featureGet]. Không có datum nào cho nó (nó không phải một ô hiển thị), nên đây là **chỗ
 *     duy nhất** của automation đọc feature-id thô — cùng công thức mà cầu kiểm thử `hal getid` dùng.
 *  2. **Đọc sấy** — `carControl.readState(<mỏ neo>)`, tức đi qua `ControlDef.readKey`
 *     (`defrost_front_state` → `BYDAutoAcDevice.getAcDefrostState(1)`, `defrost_rear_state` → `(2)`). Mỏ neo =
 *     nút ĐẦU trong [selection] ⇒ V7 chọn riêng sấy sau thì đọc chính nó, không đọc một nút không ai ghi (xem
 *     KDoc [selection]). ⚠ **Phải đọc TỪ XE**, không phải một cờ RAM: cờ RAM không thấy người lái bấm nút sấy
 *     trên màn xe, tức R1.5 chết (xem hợp đồng stage 1).
 *  3. **Ghi sấy** — `carControl.toggle("defrost"/"defrost_rear", on)` cho **những nút đã chọn**, tức đúng cửa mà
 *     một cú chạm dùng (`AC_DEFROST_FRONT_STATE_SET` / `AC_DEFROST_REAR_STATE_SET`, CAPTEST OK). Không tự dựng
 *     lệnh HAL thứ hai: dự án đã trả giá cho đúng việc ấy ở cặp `lock`/`door` (hai nhãn đối nghịch gửi cùng một
 *     byte).
 *
 * ## Ký ức R1.5 là cờ RAM, và [state] là ĐÚNG MỘT bản cho cả tiến trình
 * [RainDefrostState] mang hai bit (*sấy này của tôi* · *người lái đã tự tắt trong cơn mưa này*). Hai bản sao =
 * hai câu trả lời cho *"được phép tắt hộ không"*, và cái sai sẽ tắt một cái sấy người lái đang cần. Không lưu bền
 * — lý do đầy đủ ở KDoc [RainDefrostState] (nổ máy lại thì quên là hướng sai **an toàn**).
 *
 * ## Degrade-safe
 * Off-car: `featureGet` → `null` ⇒ [RainDefrostPolicy.plausible] → `null` ⇒ [RainDefrostOwner.stepUnknown] ⇒
 * `Leave`, không ghi gì, ký ức không đổi. Mọi thân hàm bọc `runCatching` ⇒ KHÔNG BAO GIỜ ném ra ngoài (bộ test
 * đầy đủ chạy off-car nên đường này phải không ném).
 */
object RainDefrostApplier {

    const val TAG = "KachiRainDefrost"

    /**
     * Tên HẰNG của feature-id đọc cảm biến mưa — bind theo **tên**, không dán số.
     *
     * [ĐO xe 2026-09-20] id là `1196425250`, nhưng `BYDAutoFeatureIds` gán giá trị trong `static {}` theo
     * `isCanFD`/`isToyota` (R11) ⇒ con số chỉ đúng cho một cấu hình. Số đo được ghi ở [MEASURED_ID] để đối chiếu
     * và làm đường lùi khi ROM không có bảng tên.
     */
    const val RAIN_CONST = "BYDAutoFeatureIds.Setting.SETTING_FRONT_RAIN_WIPER_SPEED"

    /** [ĐO xe 2026-09-20] giá trị của [RAIN_CONST] trên xe owner — đường lùi khi tra tên thất bại. */
    const val MEASURED_ID = 1196425250

    /** Device phơi cảm biến mưa — [ĐO] `docs/diagnostics/oncar-1.84-session-2026-09-20.md` §1. */
    const val RAIN_DEVICE = "android.hardware.bydauto.setting.BYDAutoSettingDevice"

    /** Mã nút sấy TRƯỚC trong `ControlRegistry` (đường đọc + đường ghi đều tra từ đây). */
    const val CTL_FRONT = "defrost"

    /**
     * Mã nút sấy SAU (kính sau + gương chiếu hậu trên xe này).
     *
     * ⚠ V7 (owner 2026-09-25) **đảo một quyết định của 1.85**: R1.3 chốt *"bật cả hai, không chỉ kính lái"*, nay
     * người dùng CHỌN — trước · sau · cả hai ([selection]). Lý do: hai cái sấy có hai chi phí khác nhau (kính sau
     * + gương ăn điện liên tục) và owner muốn dùng riêng được từng cái. Mặc định vẫn là **cả hai** nên hành vi của
     * người không vào Cài đặt không đổi.
     */
    const val CTL_REAR = "defrost_rear"

    /**
     * Ký ức giữa hai nhịp — xem KDoc lớp. `@Volatile` vì [tick] chạy trên thread nền của
     * [AutomationService] còn [reset] có thể đến từ luồng vẽ (người dùng gạt công tắc).
     */
    @Volatile
    private var state = RainDefrostState()

    /** Trạng thái hiện tại — chỉ cho nhật ký/chẩn đoán đọc. */
    fun stateNow(): RainDefrostState = state

    /**
     * Quên ký ức — gọi khi công tắc bị TẮT.
     *
     * Không có bước này thì: tắt automation lúc đang mưa (sấy do nó bật, `owned = true`) → bật lại khi trời đã
     * khô → nhịp đầu tiên thấy `owned` còn `true` và **tắt** cái sấy mà người lái có thể đã tự bật giữa lúc ấy.
     */
    fun reset() {
        state = RainDefrostState()
    }

    /**
     * MỘT nhịp đánh giá. GỌI TỪ THREAD NỀN.
     *
     * Hai ca no-op (và **quên ký ức**): công tắc chính TẮT, **hoặc** V7 bỏ tích cả hai ô kính ([selection] rỗng).
     * Cả hai đều là *"không còn việc gì"* nên phải cùng đi qua [reset]: giữ lại `owned` của một cơn mưa cũ rồi bật
     * lại tính năng lúc trời đã khô là tắt một cái sấy mà người lái có thể vừa tự bật.
     *
     * @return hành động đã thực hiện, cho nhật ký của [AutomationService].
     */
    fun tick(ctx: Context): RainDefrostAction {
        val app = ctx.applicationContext
        val pick = selection(app)
        if (!Prefs.rainDefrostEnabled(app) || pick.isEmpty()) {
            reset()
            return RainDefrostAction.Leave
        }
        return runCatching {
            val raw = readRain(app)
            val speed = RainDefrostPolicy.plausible(raw)
            if (speed == null) {
                // KHÔNG ĐỌC ĐƯỢC ≠ TRỜI KHÔ. Giữ nguyên tất cả — nếu coi như khô thì một lần đọc lỗi giữa cơn
                // mưa sẽ TẮT cái sấy đang cần (xem KDoc `RainDefrostPolicy.plausible`).
                Log.d(TAG, "mưa: không đọc được (raw=$raw) ⇒ giữ nguyên")
                state = RainDefrostOwner.stepUnknown(state).state
                return@runCatching RainDefrostAction.Leave
            }
            val anchor = pick.first()
            val defrostOn = readDefrost(app, anchor)
            if (defrostOn == null) {
                // Cùng lẽ: không biết sấy đang bật hay tắt thì mọi quyết định đều là đoán. `decide` nhận
                // `Boolean` nên nó buộc phải chọn một phía — chọn hộ nó ở đây là bật sấy giữa trời nắng (nếu
                // đoán "đang tắt") hoặc bỏ mất lượt bật (nếu đoán "đang bật").
                Log.d(TAG, "sấy: không đọc được trạng thái ($anchor) ⇒ giữ nguyên")
                state = RainDefrostOwner.stepUnknown(state).state
                return@runCatching RainDefrostAction.Leave
            }
            val step = RainDefrostOwner.step(state, speed, defrostOn)
            val before = state
            state = step.state
            Log.i(
                TAG,
                "mưa=$speed (mưa? ${RainDefrostPolicy.isRaining(speed)}) · sấy[$anchor]=$defrostOn · chọn=$pick · " +
                    "owned=${step.state.owned} suppressed=${step.state.suppressed} ⇒ ${step.action.javaClass.simpleName}",
            )
            when (step.action) {
                // ⚠ Nhận chủ quyền CHỈ khi lệnh bật tới được xe — xem KDoc `RainDefrostOwner.unclaim`. Ghi hỏng mà
                // vẫn nhận thì nhịp sau đọc `sấy=tắt` + `owned=true` = đúng dấu hiệu "người lái tự tắt" ⇒ im tới
                // hết cơn mưa vì một lần rc≠0.
                RainDefrostAction.TurnOn -> if (!writeSelected(app, pick, true)) {
                    state = RainDefrostOwner.unclaim(before, step)
                    Log.i(TAG, "ghi bật sấy KHÔNG tới xe ⇒ nhả chủ quyền, nhịp sau thử lại")
                }
                RainDefrostAction.TurnOff -> writeSelected(app, pick, false)
                RainDefrostAction.Leave -> Unit
            }
            step.action
        }.getOrElse {
            Log.w(TAG, "nhịp mưa→sấy lỗi (degrade-safe, bỏ qua)", it)
            RainDefrostAction.Leave
        }
    }

    /**
     * V7 — những nút sấy người dùng ĐÃ CHỌN, theo thứ tự [CTL_FRONT] rồi [CTL_REAR].
     *
     * ## Thứ tự là một hợp đồng, không phải tình cờ
     * Phần tử ĐẦU là **mỏ neo**: nó là nút được ĐỌC LẠI ở nhịp sau ([readDefrost]) và là nút quyết định chủ
     * quyền ([writeSelected]). Kính TRƯỚC đứng trước vì khi cả hai được chọn thì nó là cái người lái nhìn xuyên
     * qua — nhưng bất biến thật sự là *"cái được đọc phải là một cái đang được ghi"*: đọc `defrost` trong khi chỉ
     * ghi `defrost_rear` nghĩa là mỗi nhịp đều đọc về `tắt`, rồi ra lệnh bật, rồi lại đọc về `tắt` — automation
     * ghi HAL mỗi 5 phút suốt chuyến mà không bao giờ thấy việc mình làm.
     *
     * Rỗng = cả hai ô đều bỏ tích ⇒ [tick] coi như tính năng TẮT. Cố ý **không** lùi về "ghi cả hai": một người
     * vừa bỏ tích cả hai ô rồi thấy xe bật cả hai cái sấy sẽ không có cách nào hiểu vì sao.
     */
    fun selection(app: Context): List<String> = buildList {
        if (Prefs.rainDefrostFront(app)) add(CTL_FRONT)
        if (Prefs.rainDefrostRear(app)) add(CTL_REAR)
    }

    /**
     * Đọc cảm biến mưa → số thô, `null` nếu không đọc được / sentinel.
     *
     * Tra id theo **tên hằng** trước (`featureIdByName`), lùi về [MEASURED_ID] khi ROM không có bảng tên — đường
     * lùi ấy là lý do con số vẫn phải có mặt trong mã.
     */
    private fun readRain(app: Context): Int? = runCatching {
        val gw = BydHalGateway(app)
        val id = gw.featureIdByName(RAIN_CONST) ?: MEASURED_ID
        val raw = gw.featureGet(RAIN_DEVICE, id) ?: return@runCatching null
        // Sentinel (`NOT_PROVISIONED` / `INVALID`) là một con số THẬT nếu chỉ `toInt()` — và nó lớn hơn ngưỡng
        // mưa, tức nó sẽ bật sấy giữa trời nắng. Lọc bằng chính bộ lọc của `:core`, không tự so tay.
        if (HalBindingTable.rawIsSentinel(raw)) {
            Log.d(TAG, "mưa: sentinel ($raw) — feature không có trên trim này")
            return@runCatching null
        }
        HalBindingTable.coerceInt(raw)
    }.getOrNull()

    /** Nút sấy [controlId] đang bật? Đọc **từ xe** qua `readKey` của nút. `null` = chưa đọc được. */
    private fun readDefrost(app: Context, controlId: String): Boolean? = runCatching {
        AppContainer.get(app).carControl.readState(controlId)?.let { it != 0 }
    }.getOrNull()

    /**
     * Ghi những nút **đã chọn** (V7). Một nút hỏng KHÔNG chặn nút kia — xe từ chối một lệnh là chuyện thường.
     *
     * @param pick danh sách của [selection] (không rỗng, thứ tự là hợp đồng — xem KDoc ở đó).
     * @return lệnh cho nút **MỎ NEO** (`pick.first()`) đã tới xe chưa. Chỉ nó quyết định chủ quyền, vì nó cũng là
     *   nút được ĐỌC lại ở nhịp sau; nút thứ hai hỏng riêng thì automation vẫn theo dõi đúng cái nó đang giữ, và
     *   nhịp sau sẽ không hiểu lầm thành *"người lái tự tắt"*.
     */
    private fun writeSelected(app: Context, pick: List<String>, on: Boolean): Boolean {
        val control = runCatching { AppContainer.get(app).carControl }.getOrNull() ?: run {
            Log.i(TAG, "không có cổng điều khiển (off-car) — bỏ ghi sấy")
            return false
        }
        var anchorOk = false
        pick.forEachIndexed { i, id ->
            val ok = runCatching { control.toggle(id, on) }.getOrDefault(false)
            if (i == 0) anchorOk = ok
            Log.i(TAG, "toggle($id, $on) ⇒ $ok")
        }
        return anchorOk
    }
}
