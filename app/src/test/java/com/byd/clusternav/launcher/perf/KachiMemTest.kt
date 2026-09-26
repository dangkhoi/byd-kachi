package com.byd.clusternav.launcher.perf

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CLOSE-4 · WAKE-MALLOPT — khoá HAI thứ mà "compile xanh" không nói được ═══════════════════════════════
 *
 * 1. **Đường KHÔNG CÓ `.so` phải là no-op im lặng.** Bài này chạy trên JVM của máy dev, nơi
 *    `System.loadLibrary("kachimem")` chắc chắn ném [UnsatisfiedLinkError] — tức nó chạy đúng cái ca mà bản
 *    release phải sống sót: lib thiếu (build native lỗi, `pm install` mất lib, ROM chặn `dlopen`). Nếu [KachiMem]
 *    lỡ gọi `Debug`/`Log` trước cổng `loaded` thì bài này đỏ bằng `RuntimeException("Stub!")` của android.jar —
 *    và đó chính là cách duy nhất off-device để chứng minh cổng ấy còn đúng.
 *
 * 2. **Call site có thật** (CLAUDE.md §8 — `CastShell.evictVd` viết xong, KDoc đủ, chưa chạy lần nào). Bốn mốc
 *    pha + khối build native được khoá bằng nội dung source đã bỏ chú thích, nên viết token vào comment không
 *    làm bài xanh được.
 */
class KachiMemTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    // ─── 1. Đường thiếu `.so`: no-op, không ném ────────────────────────────────────────────────────────────

    @Test
    fun `khong co lib thi available false`() {
        assertFalse(KachiMem.available(), "JVM không có libkachimem.so ⇒ available() phải false")
    }

    @Test
    fun `khong co lib thi trim la no-op tra false`() {
        // Gọi hai lần: lần hai chứng minh không có state nào bị hỏng và không có lần nạp lib thứ hai.
        assertFalse(KachiMem.trim("test"), "thiếu lib ⇒ trim phải trả false")
        assertFalse(KachiMem.trim("test lần hai"), "thiếu lib ⇒ trim vẫn phải trả false, không ném")
    }

    @Test
    fun `khong co lib thi decayNow la no-op tra false`() {
        assertFalse(KachiMem.decayNow(), "thiếu lib ⇒ decayNow phải trả false")
    }

    // ─── 2. Call site + khối build ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `bon moc pha goi trim hoac decay`() {
        val recognizer = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt")
        assertTrue(
            recognizer.contains("KachiMem.trim(\"sau nạp mô hình"),
            "mốc pha 1 (nạp xong mô hình ASR) mất call site trong VoiceRecognizer.build",
        )
        assertTrue(
            recognizer.contains("KachiMem.trim(\"sau nhả mô hình\")"),
            "mốc pha 2 (nhả mô hình — BG-20) mất call site trong VoiceEngine.release",
        )

        val wake = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt")
        assertTrue(wake.contains("KachiMem.decayNow()"), ":wake không còn đặt M_DECAY_TIME = 0 lúc onCreate")
        assertTrue(
            wake.contains("KachiMem.trim(\"phiên lệnh xong\")"),
            "mốc pha 3 (phiên lệnh xong, hết lượt nhường micro) mất call site trong resumeTask",
        )

        val tts = code("src/main/java/com/byd/clusternav/launcher/voice/PiperTtsService.kt")
        assertTrue(
            tts.contains("KachiMem.trim(\"sau đọc xong câu\")"),
            "mốc pha 4 (:tts đọc xong một câu) mất call site trong PiperTtsService.sendDone",
        )
    }

    @Test
    fun `trim khong nam tren duong audio`() {
        // Luật 2 của [KachiMem]: chỉ ở mốc pha. Hai lớp này chạy MỖI KHÚC audio ⇒ một `trim` ở đây là một đường
        // trễ mới trên đúng chỗ không được phép có (CLAUDE.md §6).
        listOf("VoiceWakeAsr.kt", "VoiceCapture.kt", "VoiceWakeListener.kt").forEach { f ->
            val src = code("src/main/java/com/byd/clusternav/launcher/voice/$f")
            assertFalse(src.contains("KachiMem"), "$f nằm trên đường audio — không được gọi KachiMem ở đó")
        }
    }

    @Test
    fun `build native khai tuong minh ndk cmake va chi arm64`() {
        val gradle = code("build.gradle.kts")
        assertTrue(gradle.contains("ndkVersion = \"30.0.16248370\""), "ndkVersion phải ghim tường minh (NDK r30 LTS)")
        assertTrue(gradle.contains("path = file(\"src/main/cpp/CMakeLists.txt\")"), "thiếu externalNativeBuild cmake path")
        assertTrue(gradle.contains("version = \"3.31.6\""), "CMake phải ghim phiên bản")
        assertTrue(
            gradle.contains("abiFilters += listOf(\"arm64-v8a\")"),
            "abiFilters phải vẫn CHỈ arm64-v8a — thêm ABI là APK tải qua 4G của xe phình thêm mỗi bản vá",
        )
    }

    @Test
    fun `cmake la C thuan khong STL`() {
        val cmake = SourceRoots.text("src/main/cpp/CMakeLists.txt")
        assertTrue(cmake.contains("LANGUAGES C"), "phải khai C thuần — C++ kéo theo libc++_shared.so vào APK")
        assertFalse(cmake.contains("ANDROID_STL"), "không được khai ANDROID_STL")
        assertFalse(cmake.contains(".cpp"), "không được có nguồn C++")
        assertTrue(cmake.contains("-Os"), "tệp không nằm trên đường nóng ⇒ tối ưu theo kích cỡ")

        // `codeOf` (bỏ chú thích) chứ không `text`: KDoc của tệp C có NHẮC `M_PURGE_ALL` để nói *"không dùng"* —
        // quét thô sẽ báo sai đúng cái nó định cấm.
        val c = code("src/main/cpp/kachimem.c")
        assertTrue(c.contains("mallopt(M_PURGE, 0)"), "thiếu lời gọi M_PURGE")
        assertTrue(c.contains("mallopt(M_DECAY_TIME, 0)"), "thiếu lời gọi M_DECAY_TIME 0")
        assertFalse(c.contains("M_PURGE_ALL"), "M_PURGE_ALL chỉ có từ API 34 — xe là API 29/31 ⇒ không dùng")
        // Tên hàm JNI phải khớp package + lớp, nếu không thì `nativePurge` ném UnsatisfiedLinkError lúc gọi (chứ
        // không phải lúc `loadLibrary`) — tức lỗi rơi vào runtime trên xe, không ai thấy off-car.
        assertEquals(
            2,
            Regex("Java_com_byd_clusternav_launcher_perf_KachiMem_native(Purge|DecayNow)").findAll(c).count(),
            "hai hàm JNI phải mang đúng tên mangling của com.byd.clusternav.launcher.perf.KachiMem",
        )
    }
}
