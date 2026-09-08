package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Wiring lock for B3 (on-car 2026-09-07): auto-start VietMap tách ra một foreground-service RIÊNG
 * ([VietMapAutostartService]) để KHÔNG block chuỗi boot-setup (ghế / lọc bụi / nav / voice-key) khi nhánh
 * bóng poll chờ VietMap vào map (tuỳ network). Owner: *"tách riêng cái vietmap ra, không liên quan đến app
 * chung của mình"*.
 *
 * Runtime cần Android (Service, Context, dadb) và `:app` không có Robolectric — nên như
 * [VmFloatWhitelistWiringTest], khoá bằng cách đọc SOURCE. Comment bị strip trước ([KotlinSource]) nên một
 * nhắc-tới trong comment không thể thoả contract. Đây là mặt tiếp giáp thật (producer boot/UI → consumer
 * service → runNow), nếu ai đó nối lại đồng bộ vào boot thì test này ĐỎ.
 */
class VietMapAutostartServiceWiringTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun readSrc(relative: String) =
        KotlinSource.stripComments(app("src/main/java/com/byd/clusternav/$relative").toFile().readText())

    private val boot by lazy { readSrc("BootSetupService.kt") }
    private val service by lazy { readSrc("VietMapAutostartService.kt") }
    private val main by lazy { readSrc("MainActivity.kt") }
    private val badge by lazy { readSrc("modules/clustercast/BadgePlacementController.kt") }
    private val autostart by lazy { readSrc("VietMapAutostart.kt") }
    private val manifest by lazy { app("src/main/AndroidManifest.xml").toFile().readText() }

    @Test
    fun `boot-setup KHONG con goi runNow dong bo — tach ra service rieng`() {
        assertTrue(
            boot.contains("VietMapAutostartService.startForBoot("),
            "BootSetupService phải khởi động VietMap qua service riêng (startForBoot), không gọi runNow đồng bộ",
        )
        assertFalse(
            boot.contains("VietMapAutostart.runNow(") || boot.contains("VietMapAutostart.ensureRunning("),
            "BootSetupService KHÔNG được gọi trực tiếp runNow/ensureRunning nữa (sẽ block chuỗi boot-setup)",
        )
    }

    @Test
    fun `service la noi DUY NHAT goi runNow`() {
        assertTrue(
            service.contains("VietMapAutostart.runNow("),
            "VietMapAutostartService phải là nơi gọi VietMapAutostart.runNow (trên thread nền của nó)",
        )
    }

    @Test
    fun `mo app + bat toggle bong + toggle badge deu di qua service`() {
        assertTrue(main.contains("VietMapAutostartService.startForAppOpen("), "MainActivity phải start service (mở app + toggle bóng)")
        assertTrue(badge.contains("VietMapAutostartService.startForAppOpen("), "BadgePlacementController phải start service khi bật badge")
        // ensureRunning đã bị gỡ (code chết) — không call site nào còn dùng.
        assertFalse(autostart.contains("fun ensureRunning("), "ensureRunning phải được gỡ (không còn call site)")
    }

    @Test
    fun `manifest dang ky VietMapAutostartService (never exported)`() {
        assertTrue(manifest.contains(".VietMapAutostartService"), "AndroidManifest phải khai báo VietMapAutostartService")
    }
}
