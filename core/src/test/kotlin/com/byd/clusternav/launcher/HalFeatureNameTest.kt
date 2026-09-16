package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V3 · R11 — BIND THEO **TÊN HẰNG**, tra số + tra device lúc chạy ═════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` R11.
 *
 * ## Cái bài này khoá — [ĐO nguồn fw-dl3 2026-09-16]
 * `BYDAutoFeatureIds` khai `public static final int X;` **không giá trị** rồi gán trong `static {}` theo cấu hình
 * xe (`isCanFD` / `isToyota`), ví dụ `ENGINE_FRONT_MOTOR_SPEED` = 1141899272 **hoặc** 1141901320. Vì thế mọi số
 * decimal chép từ một bản decompile chỉ đúng cho **một** cấu hình; trên xe khác nó không tồn tại và
 * `AbsBYDAutoDevice.checkDeviceFeatures` từ chối — đúng 12 datum im lặng của lượt xe 09-16.
 *
 * Hai "chiếc xe" dưới đây khai **cùng một tên** với **hai số khác nhau**; cùng một registry phải đọc được cả hai.
 */
class HalFeatureNameTest {

    private val key = "BYDAutoFeatureIds.Engine.ENGINE_POWER"
    private val name = "Engine.ENGINE_POWER"

    @Test
    fun `routeOf nhan dang tien to BYDAutoFeatureIds`() {
        val r = HalBindingTable.routeOf(key)
        assertTrue(r is BindingRoute.FeatureName, "nhận $r")
        assertEquals(name, (r as BindingRoute.FeatureName).constName)
        // ⚠ Phải xét TRƯỚC nhánh `BYDAuto…`: `BYDAutoFeatureIds` cũng bắt đầu bằng "BYDAuto", và nếu rơi vào
        // nhánh kia thì `deviceFqn` dựng ra `android.hardware.bydauto.featureids.BYDAutoFeatureIds` — một lớp
        // không tồn tại ⇒ mọi dòng bind-theo-tên im lặng thành "off-car".
        assertTrue(HalBindingTable.routeOf("BYDAutoBodyworkDevice.setMoonRoofState") is BindingRoute.NamedMethod)
    }

    @Test
    fun `hai cau hinh xe — cung mot TEN, hai SO, deu doc duoc`() {
        listOf(1141899272 to "70", 1141901320 to "71").forEach { (id, raw) ->
            val table = HalBindingTable(
                FakeHalGateway(features = mapOf(id to raw), featureNames = mapOf(name to id)),
            )
            assertEquals(raw, table.readRaw("motor_power"))
        }
    }

    @Test
    fun `khong tra duoc TEN thi unavailable — KHONG doan mot con so`() {
        // Xe không có hằng ấy (trim khác / ROM khác): trả `null` ⇒ ô hiện "—". Đoán một số là bắn vào một
        // feature-id của **việc khác** — đúng họ lỗi `lock`/`door` một byte.
        val table = HalBindingTable(FakeHalGateway(features = mapOf(1141899272 to "70")))
        assertNull(table.readRaw("motor_power"))
    }

    @Test
    fun `device dich lay tu BANG cua framework, khong doan theo Domain`() {
        val fake = FakeHalGateway(
            features = mapOf(1 to "9"),
            featureNames = mapOf(name to 1),
            featureDevices = mapOf(1 to "android.hardware.bydauto.engine.BYDAutoEngineDevice"),
        )
        assertEquals("9", HalBindingTable(fake).readRaw("motor_power"))
        assertEquals("android.hardware.bydauto.engine.BYDAutoEngineDevice", fake.lastFeatureGetDevice)
    }

    @Test
    fun `khong co bang device thi LUI VE phep doan cu — bang la cai thien, khong phai mot cong`() {
        // `motor_power` thuộc Domain.ENERGY ⇒ phép đoán cũ trỏ `BYDAutoStatisticDevice`. Thiếu bảng thì mọi thứ
        // chạy y như 1.65; nếu bảng biến thành một cổng thì một ROM không có `BYDAutoDeviceFeaturesMap` sẽ làm
        // câm hết telemetry — đúng thứ CLAUDE.md §3 cấm.
        val fake = FakeHalGateway(features = mapOf(1 to "9"), featureNames = mapOf(name to 1))
        assertEquals("9", HalBindingTable(fake).readRaw("motor_power"))
        assertEquals(HalBindingTable.featureDeviceFqn(Domain.ENERGY), fake.lastFeatureGetDevice)
    }

    @Test
    fun `describeWrite noi ro day la duong TRA-TEN, va no tinh duoc off-car`() {
        val def = ControlRegistry.byId("child_lock")!!
        val (route, device) = HalBindingTable.describeWrite(def)
        assertTrue(route.startsWith("feature_name:"), "nhận $route")
        assertTrue(device.isNotBlank(), "vẫn phải nói device sẽ dùng khi không tra được bảng")
    }

    @Test
    fun `moi bindingKey dang TEN trong registry deu dung tien to va co phan ten`() {
        val keys = (TelemetryRegistry.ALL.map { it.bindingKey } + ControlRegistry.ALL.map { it.bindingKey })
            .filter { HalBindingTable.routeOf(it) is BindingRoute.FeatureName }
        assertTrue(keys.isNotEmpty(), "tiền đề: đã có dòng bind theo tên")
        keys.forEach { k ->
            assertTrue(k.startsWith(HalBindingTable.FEATURE_IDS_CLASS + "."), k)
            val const = (HalBindingTable.routeOf(k) as BindingRoute.FeatureName).constName
            assertTrue(const.isNotBlank() && !const.endsWith("."), k)
            // Tên hằng của framework là UPPER_SNAKE, có thể kèm một lớp lồng PascalCase ở đầu.
            assertTrue(const.matches(Regex("([A-Z][A-Za-z0-9]*\\.)?[A-Z][A-Z0-9_]*")), "tên hằng lạ: $const")
        }
    }
}
