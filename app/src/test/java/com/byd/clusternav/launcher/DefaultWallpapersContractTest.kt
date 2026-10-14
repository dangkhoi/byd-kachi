package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.5 — bộ ảnh nền MẶC ĐỊNH (`assets/wall-default/`, owner 14/10): ảnh owner + hình nền BYD Seal có bản quyền ⇒ CHỈ trong
 * APK dựng tại máy owner, KHÔNG BAO GIỜ trong repo công khai; bản clone sạch phải dựng được và lùi nền vẽ bằng mã.
 * Quyết định nguồn: [WallSourcePolicy] (test ở `:core`).
 */
class DefaultWallpapersContractTest {

    private val repo: Path = listOf(Paths.get(""), Paths.get(".."))
        .map { it.toAbsolutePath().normalize() }
        .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }

    private fun git(vararg args: String): Pair<Int, String> {
        val p = ProcessBuilder(listOf("git") + args).directory(repo.toFile()).redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "git ${args.joinToString(" ")} treo")
        return p.exitValue() to out
    }

    @Test
    fun `repo khong chua anh wall-default nao va thu muc bi gitignore`() {
        val (code, tracked) = git("ls-files", "--", "*wall-default*", "**/wall-default/**")
        assertEquals(0, code, tracked)
        assertEquals("", tracked.trim(), "ảnh nền mặc định (BYD có bản quyền) đã vào git — repo công khai")
        listOf("main", "vehicleTest", "release").forEach { set ->
            val (ig, out) = git("check-ignore", "-q", "app/src/$set/assets/wall-default/wall-01.jpg")
            assertEquals(0, ig, "app/src/$set/assets/wall-default/ phải bị .gitignore chặn: $out")
        }
    }

    @Test
    fun `doc thu muc asset luc chay - khong danh sach cung, loi thi rong`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/DefaultWallpapers.kt")
        assertTrue("assets.list(ASSET_DIR)" in src, "phải khám phá ảnh bằng AssetManager.list")
        assertTrue("WallSourcePolicy.bundledFrom(" in src, "lọc + thứ tự ổn định dùng chung luật thư mục người dùng")
        assertFalse(Regex("""wall-\d\d""").containsMatchIn(src), "không được ghi cứng tên tệp ảnh")
        val list = SourceRoots.body(src, "fun list(")
        assertTrue("catch (e: IOException)" in list && "Log.w(" in list, "lỗi đọc asset ⇒ log + rỗng (lùi nền vẽ bằng mã)")
    }

    @Test
    fun `anh dong goi di CUNG duong anh nguoi dung - giam co theo man, kinh, thread nen`() {
        val ctrl = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WallpaperController.kt")
        val st = SourceRoots.body(ctrl, "fun step(")
        val io = st.indexOf("submitIo")
        assertTrue(io in 0 until st.indexOf("DefaultWallpapers.loadScaled("), "giải mã asset trong thread nền")
        assertTrue(st.indexOf("DefaultWallpapers.artCache(") in io until st.indexOf("onUi {"), "nấu kính asset trong thread nền")
        assertTrue("wall.setPhoto(" in st && "WallArtStore.swap(" in st, "cùng WallView + kho kính")
        // Ảnh đóng gói không phụ thuộc công tắc ảnh người dùng — chỉ ảnh USER bị bỏ khi vừa tắt.
        assertTrue("src == WallSourcePolicy.Source.USER && !prefs().enabled" in st)
        assertTrue("if (bundled && !shownBundled) showDefault()" in st, "ảnh đóng gói hỏng ⇒ nền vẽ bằng mã")
        val dw = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/DefaultWallpapers.kt")
        assertTrue("WallpaperStore.decodeScaled(" in SourceRoots.body(dw, "fun loadScaled("), "một luật hạ cỡ (inSampleSize) cho cả hai nguồn")
        val store = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WallpaperStore.kt")
        val dec = SourceRoots.body(store, "internal fun decodeScaled(")
        assertTrue("inJustDecodeBounds" in dec && "inSampleSize" in dec, "giải mã theo cỡ màn, không bitmap 4K")
        // Bộ đệm kính của asset ở chỗ RIÊNG: bộ dọn thư mục người dùng coi khoá asset là mồ côi.
        assertTrue("File(ctx.cacheDir, ART_DIR)" in dw)
    }

    @Test
    fun `reload chon nguon bang chinh sach, khong dung them nhip`() {
        val ctrl = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WallpaperController.kt")
        val bg = SourceRoots.body(SourceRoots.body(ctrl, "fun reload()"), "submitIo {")
        assertTrue("DefaultWallpapers.list(ctx)" in bg, "đọc danh sách asset ở thread nền")
        assertTrue("WallSourcePolicy.resolve(prefs().enabled, userImages, bundledImages)" in SourceRoots.body(ctrl, "private fun applySource()"))
        val dw = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/DefaultWallpapers.kt")
        listOf("Thread(", "Handler(", "Timer(", "Executors.").forEach { assertFalse(it in dw, "không thêm luồng/nhịp: $it") }
    }
}
