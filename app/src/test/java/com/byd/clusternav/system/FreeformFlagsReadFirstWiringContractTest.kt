package com.byd.clusternav.system

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 · R13 (soát Pass 1 [P2]) — dây nối "đọc cờ freeform TRONG tiến trình, 0 shell". Bản đầu của R13 đọc bằng `settings get`
 * qua shell ⇒ vẫn 4 lượt kênh dadb lúc nổ máy (2 launcher + 2 chiếu) — số lệnh không giảm, chỉ đổi ghi thành đọc. Khoá: cả hai
 * đường ghi cờ nhận CÙNG bộ đọc `FreeformSeedStore.readGlobal` (`Settings.Global.getString`, một ContentProvider trong tiến
 * trình); luật thuần ở `:core` (`FreeformSeedPolicyTest` · `FreeformFlagsReadFirstTest`). CLAUDE.md §8: hàm mới có chỗ gọi.
 */
class FreeformFlagsReadFirstWiringContractTest {

    private fun code(p: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$p")

    @Test
    fun `mot bo doc Settings Global trong tien trinh, khong qua shell`() {
        val store = code("system/FreeformSeedStore.kt")
        val read = SourceRoots.body(store, "fun readGlobal(")
        assertTrue(read.contains("Settings.Global.getString(app.contentResolver, key)"), "đọc trong tiến trình")
        assertTrue(read.contains("runCatching"), "đọc hỏng ⇒ null ⇒ bên gọi ghi như cũ (fail-safe)")
        assertFalse(read.contains("settings get"), "không phải lệnh shell")
    }

    @Test
    fun `duong launcher va duong chieu deu dung CUNG bo doc`() {
        val store = code("system/FreeformSeedStore.kt")
        assertTrue(SourceRoots.body(store, "fun forLauncher(").contains("readFlag = readGlobal(app)"), "launcher: FreeformSeedPolicy nhận bộ đọc")
        val runtime = code("modules/clustercast/simplified/SimpleCastRuntime.kt")
        assertTrue(runtime.contains("globalSettingReader = com.byd.clusternav.system.FreeformSeedStore.readGlobal(app)"), "chiếu: coordinator nhận bộ đọc")
        assertTrue(
            SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinator.kt")
                .contains("readGlobalSetting = globalSettingReader"),
            "coordinator chuyển bộ đọc xuống CastGeometryController",
        )
    }
}
