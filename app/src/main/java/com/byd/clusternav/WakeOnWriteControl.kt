package com.byd.clusternav

import com.byd.clusternav.launcher.CarControlPort
import com.byd.clusternav.launcher.CarDataAdapter

/**
 * Bọc [CarControlPort]: SAU mỗi lệnh GHI (toggle/step/cover/select/press), ĐÁNH THỨC đường đọc của chính datum
 * điều khiển đó — quên trạng thái "nguội" trong [HalAbsentCache] để nhịp poll KẾ đọc lại NGAY.
 *
 * ## Vì sao cần (owner 2026-09-24 "khi có action phải chuyển NGAY")
 * Datum khí hậu (nhiệt/gió/gió-trong) có thật nhưng lúc boot AC HAL chưa sẵn ⇒ 3 lần đọc null liên tiếp ⇒
 * `HalAbsentCache` xử "nguội" ⇒ chỉ thử lại mỗi 60s→10min ⇒ ô/chip/widget giữ default (22 / gió 4) rất lâu dù
 * xe đã 26° / gió 1. Khi người dùng vừa tác động thì đó là dấu hiệu CHẮC CHẮN datum ấy đang sống ⇒ quên nguội,
 * poll kế đọc tươi. Grace-window của tile ([ControlTileState.touch]) chặn đọc-rách giữa lúc HAL chưa settle.
 *
 * Quên theo CẢ HAI khoá (K1b 2026-09-25): mã NÚT (đường `readControls` · nhịp nhanh, cache theo mã nút từ K1b) và
 * `readKey` (mã DATUM · nhịp chậm). Control không có readKey ⇒ chỉ quên khoá nút.
 *
 * ## ⚠ `by inner` — KHÔNG được liệt kê tay các cửa ĐỌC ([SOÁT Pass 2 · 2026-09-26 · P1])
 * Tới bản trước lớp này khai `: CarControlPort` rồi tự viết **đúng bảy** hàm, nên mọi thành viên CÓ THÂN MẶC ĐỊNH
 * mà nó quên viết lại **im lặng rơi về mặc định của giao diện**, chứ không xuống [inner]. [ĐO đọc nguồn] hai cửa đã
 * rơi như thế trong bản dựng thật (`AppContainer.carControl` = lớp này):
 *  • `wiredOnThisCar` → mặc định `true` ⇒ câu *"xe này không có nút đó, chờ cũng vô ích"* (V3 · R11) **không bao giờ**
 *    nói được trên xe, và cổng `failureIsReal` của ô đơn (R4(b)) luôn nhận `true` ⇒ mất tác dụng;
 *  • `readStep` → mặc định `null` ⇒ `VoiceReadback` không bao giờ đọc lại được mức THẬT sau một lệnh STEP (R5).
 * Uỷ quyền cả giao diện (`by inner`) rồi chỉ **ghi đè** các cửa GHI: thêm thành viên mới vào [CarControlPort] từ nay
 * tự chảy xuống [inner], không phải nhớ sửa tệp này (`WakeOnWriteDelegationTest` canh lại điều đó).
 *
 * ⚠ **Mặt sau của `by inner`**: một thành viên mới của [CarControlPort] CÓ THÂN MẶC ĐỊNH mà thân ấy lại gọi một cửa
 * GHI (kiểu `coverLevel = cover(id, level > 0)`) sẽ chạy với `this` = [inner] ⇒ **mất lượt đánh thức**. Vì thế sáu cửa
 * ghi phải được ghi đè TẬN TAY ở đây (`coverLevel` là đúng ca đó), và bài kiểm canh nguồn ghim cả sáu. [ĐO đọc nguồn
 * 2026-09-26] `actByKind` là **hàm mở rộng** (`LauncherPorts.kt:139`), không phải thành viên, nên nó vẫn dispatch qua
 * lớp này ⇒ gói lệnh/giọng nói vẫn đánh thức đúng.
 */
internal class WakeOnWriteControl(
    private val inner: CarControlPort,
    private val data: CarDataAdapter,
) : CarControlPort by inner {

    // K1b (2026-09-25): quên CẢ khoá nút (đường readControls) lẫn khoá datum readKey (đường nhịp chậm) — phép
    // phân giải nằm ở :core ([CarDataAdapter.forgetAbsentControl]) để test thuần khoá được.
    private fun wake(id: String) = data.forgetAbsentControl(id)

    override fun toggle(id: String, on: Boolean): Boolean = inner.toggle(id, on).also { wake(id) }
    override fun step(id: String, value: Int): Boolean = inner.step(id, value).also { wake(id) }
    override fun cover(id: String, open: Boolean): Boolean = inner.cover(id, open).also { wake(id) }
    override fun coverLevel(id: String, level: Int): Boolean = inner.coverLevel(id, level).also { wake(id) }
    override fun select(id: String, index: Int): Boolean = inner.select(id, index).also { wake(id) }
    override fun press(id: String): Boolean = inner.press(id).also { wake(id) }
    // Các cửa ĐỌC (`readState`/`readStep`/`wiredOnThisCar`…) KHÔNG liệt kê ở đây: `by inner` đã chuyển hết, và một
    // danh sách tay là đúng cách để cửa mới lại rơi về mặc định giao diện (xem KDoc lớp).
}
