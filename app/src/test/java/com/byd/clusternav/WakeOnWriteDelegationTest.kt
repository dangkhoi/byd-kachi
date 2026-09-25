package com.byd.clusternav

import com.byd.clusternav.launcher.CarControlPort
import com.byd.clusternav.launcher.HalGateway
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT Pass 2 · 2026-09-26 · P1] `WakeOnWriteControl` phải UỶ QUYỀN cả [CarControlPort], không liệt kê tay ═══
 *
 * Bệnh bài này khoá: lớp bọc `AppContainer.carControl` khai `: CarControlPort` rồi tự viết **đúng bảy** hàm, nên mọi
 * thành viên có THÂN MẶC ĐỊNH mà nó quên viết lại rơi im lặng về mặc định của giao diện thay vì xuống
 * `CarControlAdapter`. Hai cửa đã rơi như thế trong bản dựng thật:
 *  • `wiredOnThisCar` → mặc định `true` ⇒ *"xe này không có nút đó"* (V3 · R11) không bao giờ nói được trên xe;
 *  • `writeFailureIsReal` → mặc định `false` ⇒ cổng hoàn nguyên của ô đơn (R4(b)) mất tác dụng trên CẢ xe thật.
 * Compile vẫn xanh vì cả hai có thân mặc định — chỉ chiếc xe biết. Bài này đi qua **đúng đồ thị DI thật**
 * ([AppContainer.carControl]) với một gateway *"trông như xe"* (bảng feature-id CÓ mặt), nên nó đo lời khai của
 * `CarControlAdapter`, không đo mặc định giao diện.
 */
class WakeOnWriteDelegationTest {

    /**
     * Gateway *"như xe"*: có bảng feature-id THẬT (`featureMapAvailable`) nhưng KHÔNG device nào chứa id nào
     * (`deviceForFeature` = null) ⇒ theo `featureAbsentOnCar`, nút đi đường feature-id là **vắng thật**, còn nút đi
     * đường named-method thì không trả lời được ⇒ *"không vắng"*. Đúng hai nhánh cần để phân biệt mặc định-vs-uỷ-quyền.
     */
    private object CarLikeGateway : HalGateway {
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? = null
        override fun namedInt(deviceFqn: String, method: String, args: IntArray): Long? = null
        override fun featureGet(deviceFqn: String, id: Int): String? = null
        override fun featureSet(deviceFqn: String, id: Int, value: Int): Long? = null
        override fun settingGet(key: String): String? = null
        override fun settingSet(key: String, value: Int): Long? = null
        override fun localGet(target: String, method: String, arg: Int?): String? = null
        override fun localSet(target: String, method: String, args: IntArray): Boolean = false
        override fun featureMapAvailable(): Boolean = true
        override fun deviceForFeature(featureId: Int): String? = null
    }

    private fun carControl(): CarControlPort = AppContainer(
        shellTransportInit = { error("không cần cho bài thuần") },
        windowDispatcherInit = { error("không cần cho bài thuần") },
        workspaceRepositoryInit = { error("không cần cho bài thuần") },
        inputDaemonClientInit = { error("không cần cho bài thuần") },
        carGatewayInit = { CarLikeGateway },
    ).carControl

    /** `fan` = STEP đi feature-id 501219340; bảng có mặt mà không device nào chứa id ⇒ *"xe này KHÔNG có nút"*. */
    @Test
    fun `wiredOnThisCar cua xe chay xuong adapter, khong rot ve mac dinh true`() {
        assertFalse(
            carControl().wiredOnThisCar("fan"),
            "lớp bọc phải chuyển câu hỏi xuống CarControlAdapter — `true` ở đây = đã rơi về mặc định giao diện",
        )
    }

    /**
     * Cổng hoàn nguyên của ô đơn: `pm25` đi named-method ⇒ bảng không khai vắng, mà bảng CÓ mặt ⇒ một cú ghi trả
     * `false` là *"xe thật từ chối"* ⇒ `true`. Mặc định giao diện là `false` nên bài này đỏ ngay nếu mất uỷ quyền.
     */
    @Test
    fun `writeFailureIsReal cua xe chay xuong adapter, khong rot ve mac dinh false`() {
        assertTrue(
            carControl().writeFailureIsReal("pm25"),
            "trên xe có bảng HAL thật, cú `false` phải được coi là thật — `false` ở đây = đã rơi về mặc định giao diện",
        )
    }

    /** Và nút VẮNG trên chính chiếc xe ấy thì `false` không phải bằng chứng gì ⇒ không hoàn nguyên. */
    @Test
    fun `nut vang tren xe thi false khong duoc coi la that`() {
        assertFalse(carControl().writeFailureIsReal("fan"))
    }

    /**
     * Mặt sau của `by inner`: một thành viên có THÂN MẶC ĐỊNH gọi cửa GHI (`coverLevel = cover(id, level > 0)`) sẽ
     * chạy với `this` = `inner` ⇒ **mất lượt đánh thức** `HalAbsentCache` (owner 2026-09-24 "action phải chuyển NGAY").
     * Nên cả SÁU cửa ghi phải ghi đè tận tay **và** gọi `wake(id)`; bài này canh nguồn (CLAUDE.md §8).
     */
    @Test
    fun `sau cua GHI phai ghi de tan tay va danh thuc read-cache`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/WakeOnWriteControl.kt")
        listOf("toggle", "step", "cover", "coverLevel", "select", "press").forEach { door ->
            val line = src.lines().firstOrNull { it.contains("override fun $door(") }
            assertTrue(line != null, "cửa ghi `$door` phải được ghi đè tận tay — `by inner` KHÔNG đánh thức read-cache")
            assertTrue(line!!.contains("wake(id)"), "cửa ghi `$door` phải gọi `wake(id)`: $line")
        }
    }
}
