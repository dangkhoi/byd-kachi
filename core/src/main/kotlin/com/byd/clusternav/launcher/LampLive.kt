package com.byd.clusternav.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ═══ 3.01 LAMPLIVE — TRẠNG THÁI ĐÈN THẬT theo SỰ KIỆN của HAL helper (thuần `:core`, MỘT nguồn sự thật) ══════════════════
 *
 * Tên cũ `TurnSignalLive` (stage 7 TURN). Owner 13/10 *"nối luôn đi, đâu cần camera xi nhan bật mới sáng"* ⇒ hộp không còn chỉ
 * mang xi-nhan: helper (`hal-helper/KachiHalMain.java`) nay chuyển tiếp MỌI `onLightOn/Off(type)` trên CÙNG listener sẵn có, nên
 * hộp mang thêm các đèn mà sự kiện báo được. Nguồn = socket `127.0.0.1:19322` mà `HalSignalClient` (`:app`) nghe — không socket
 * thứ hai, không poll. Ai cần helper chạy thì xin qua `HalSignalHub` (đếm nhu cầu) — lớp này KHÔNG khởi gì.
 *
 * ## Ngữ nghĩa giá trị
 * - `null` = socket KHÔNG nối (không ai cần · helper chết · đang nối lại) ⇒ [overlay] để nguyên số đọc getter của `CarStatus`.
 * - [Lamps] = socket ĐANG nối. `left/right` luôn có (helper gửi ảnh chụp hai bên ngay khi nối — [ĐO xe 09-26]: getter
 *   `getLightStatus(4/5)` LUÔN 0 nên xi-nhan CHỈ đúng qua đây). Các trường còn lại `null` = helper CHƯA thấy sự kiện của đèn đó
 *   kể từ khi nó khởi ⇒ giữ getter cho trường đó (getter cốt/pha/sương mù/DRL đọc được [ĐO xe 09-16 CapTest ✅]).
 * - Thang `type` = thang `getLightStatus` (`HalReadTables.readArg`): 1 hông · 2 cốt · 3 pha · 4/5 xi-nhan · 6/7 sương mù trước/sau
 *   ([ĐO RE] `BYDAutoLightDevice.LIGHT_*` + app Kim `VehicleBridge.createLightListener` dùng cùng khoá `area`). DRL tới qua
 *   callback riêng `onDayRunningLightStateChanged` (1=BẬT · 2=TẮT, `DAY_RUNNING_LIGHT_ON/OFF`).
 *
 * ## Ai được ghi
 * `HalSignalClient` (nối được ⇒ [publish] ảnh hai bên TẮT, mỗi lần ĐỔI ⇒ [publish], đứt dây/dừng ⇒ [clear]) và — CHỈ bản
 * `vehicleTest` — `DummyCarReceiver` (`--ez turn_l/turn_r`). Bài canh `LampLiveContractTest` khoá danh sách đó.
 *
 * ## Chi phí
 * Không luồng, không nhịp: một `StateFlow` ghi từ luồng đọc socket sẵn có, thu bởi vòng thu trạng thái xe của HOME (`collectHome`,
 * chỉ khi HOME ≥ STARTED). Một lượt phát mỗi lần một đèn ĐỔI.
 */
class LampLive {

    /** Trạng thái đèn theo socket. `null` ở một trường = chưa có sự kiện cho đèn đó ⇒ giữ getter. */
    data class Lamps(
        val left: Boolean,
        val right: Boolean,
        val lowBeam: Boolean? = null,
        val highBeam: Boolean? = null,
        val frontFog: Boolean? = null,
        val rearFog: Boolean? = null,
        val side: Boolean? = null,
        val drl: Boolean? = null,
    ) {
        /** Đặt trạng thái theo `type` của `onLightOn/Off` (thang `getLightStatus`). `type` lạ ⇒ trả NGUYÊN đối tượng. */
        fun withType(type: Int, on: Boolean): Lamps = when (type) {
            TYPE_SIDE -> copy(side = on)
            TYPE_LOW -> copy(lowBeam = on)
            TYPE_HIGH -> copy(highBeam = on)
            TYPE_LEFT -> copy(left = on)
            TYPE_RIGHT -> copy(right = on)
            TYPE_FRONT_FOG -> copy(frontFog = on)
            TYPE_REAR_FOG -> copy(rearFog = on)
            else -> this
        }
    }

    private val flow = MutableStateFlow<Lamps?>(null)

    val state: StateFlow<Lamps?> get() = flow

    /** Socket đang nối và báo trạng thái này (gọi được từ bất kỳ luồng nào). */
    fun publish(lamps: Lamps) { flow.value = lamps }

    /** Chỉ xi-nhan (số giả `vehicleTest`, test cũ) — các đèn khác `null` ⇒ giữ getter. */
    fun publish(left: Boolean, right: Boolean) = publish(Lamps(left, right))

    /**
     * Socket không còn nối ⇒ không còn biết ⇒ bảng lùi về số đọc của `CarStatus`. Soát Pass 11 [P2] (TURN-POLL-SERVED): số đọc của
     * datum hộp vừa đè đã KHÔNG được poll suốt lúc hộp sống ⇒ có thể cũ hàng giờ, và màn sẽ bày nó tới nhịp chậm kế (≤ 10 s) — báo
     * tập ấy qua [onServedDropped] để vòng poll đọc lại NGAY một lượt (đường `CarStatusRepository.onEvents`: ≤ 8 getter, chỉ datum màn
     * đang bày). Hộp không đè gì ⇒ không báo.
     */
    fun clear() {
        val was = served(flow.value)
        flow.value = null
        if (was.isNotEmpty()) onServedDropped(was)
    }

    /** Tập datum hộp vừa thôi đè (xem [clear]) — `AppContainer` nối vào `CarStatusRepository.onEvents`. Mặc định: không ai nghe. */
    @Volatile var onServedDropped: (Set<String>) -> Unit = {}

    /** 3.1.10 · TURN-POLL-SERVED — datum hộp đang đè LÚC NÀY ([served]); đọc giá trị, không thu dòng (chỗ thu duy nhất vẫn là `collectHome`). */
    fun served(): Set<String> = served(flow.value)

    companion object {
        /** Thang `type` của `onLightOn/Off` = `getLightStatus` — `BYDAutoLightDevice.LIGHT_*` (jadx-tmap :44-62). */
        const val TYPE_SIDE = 1
        const val TYPE_LOW = 2
        const val TYPE_HIGH = 3
        const val TYPE_LEFT = 4
        const val TYPE_RIGHT = 5
        const val TYPE_FRONT_FOG = 6
        const val TYPE_REAR_FOG = 7

        /** Hộp của tiến trình chính (chỗ sống của `HalSignalClient` và HOME). */
        val shared = LampLive()

        /**
         * Đè `lights` bằng trạng thái socket khi có: xi-nhan LUÔN đè; đèn khác chỉ đè khi trường socket khác `null`. `null` (không
         * socket) hoặc không đổi gì ⇒ trả NGUYÊN [s] (cùng đối tượng — không phát thêm lượt vẽ).
         */
        /**
         * 3.1.10 · TURN-POLL-SERVED — datum mà [overlay] ĐÈ khi hộp đang sống: số đọc của chúng không bao giờ tới màn ⇒ vòng poll không
         * đọc chúng (`AppContainer` trừ khỏi nhu cầu của `CarDataAdapter`). Xi-nhan luôn đè; đèn khác chỉ khi helper đã báo (field ≠ null).
         * Hộp `null` (helper vắng/đứt) ⇒ rỗng ⇒ nhịp chậm kế đọc lại như cũ. Cùng một bảng với [overlay] — `LampLiveServedTest` đo khớp.
         */
        fun served(t: Lamps?): Set<String> {
            if (t == null) return emptySet()
            val out = HashSet<String>(); out += "light_left_turn"; out += "light_right_turn"
            if (t.lowBeam != null) out += "light_low_beam"
            if (t.highBeam != null) out += "light_high_beam"
            if (t.frontFog != null) out += "light_front_fog"
            if (t.rearFog != null) out += "light_rear_fog"
            if (t.side != null) out += "light_side"
            if (t.drl != null) out += "light_drl"
            return out
        }

        fun overlay(s: CarStatus, t: Lamps?): CarStatus {
            if (t == null) return s
            val l = s.lights
            val out = l.copy(
                leftTurn = t.left,
                rightTurn = t.right,
                lowBeam = t.lowBeam ?: l.lowBeam,
                highBeam = t.highBeam ?: l.highBeam,
                frontFog = t.frontFog ?: l.frontFog,
                rearFog = t.rearFog ?: l.rearFog,
                sideLight = t.side ?: l.sideLight,
                drl = t.drl ?: l.drl,
            )
            return if (out == l) s else s.copy(lights = out)
        }
    }
}
