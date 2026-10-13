package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R23 — ảnh BYD CHỈ được đóng gói trong bản `vehicleTest` (owner 13/10: "cho phép dùng ảnh firmware BYD trong bản kiểm
 * thử"); bản phát hành chỉ ĐỌC lúc chạy từ gói hệ thống `com.byd.carsettings.plugins` (không phân phối lại).
 *
 * Khoá: (1) không tệp nào trong `src/main|debug|release` mang tên ảnh BYD hoặc trùng BYTE với ảnh đóng ở `src/vehicleTest`
 * (đổi tên không lọt); (2) đường release của [OemCarArt] chỉ dùng `createPackageContext` + `getIdentifier`, không asset/R
 * drawable; (3) chỉ mã `src/vehicleTest` gán móc `OemCarArt.bundled`; (4) manifest chính khai `<queries>` cho gói BYD và không
 * khai provider kiểm thử.
 */
class OemCarArtSourceSetContractTest {

    private val shippedRoots = listOf("src/main", "src/debug", "src/release")

    /** Mẫu tên tài nguyên ảnh xe của CarSettingsPlugins [ĐO `unzip -l` fw-2606]. */
    private val bydNames = Regex("common_top_view_img|common_default_6125f|setting_roof_|dilink_audio_car|dipilot_.*_car_img")

    private fun filesUnder(dir: String): List<Path> {
        if (!SourceRoots.exists(dir)) return emptyList()
        return Files.walk(SourceRoots.path(dir)).use { s -> s.filter { Files.isRegularFile(it) }.toList() }
    }

    private fun sha(p: Path): String =
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)).joinToString("") { "%02x".format(it) }

    @Test
    fun `khong anh BYD nao trong source set phat hanh - ten lan noi dung`() {
        val shipped = shippedRoots.flatMap { filesUnder(it) }
        assertTrue(shipped.isNotEmpty())
        val named = shipped.filter { bydNames.containsMatchIn(it.fileName.toString()) }
        assertEquals(emptyList<Path>(), named, "tên ảnh BYD trong source set phát hành")
        val bundled = filesUnder("src/vehicleTest/assets").filter { it.toString().endsWith(".png") }
        val bundledSha = bundled.map(::sha).toSet()
        val images = shipped.filter { n -> listOf(".png", ".webp", ".jpg", ".jpeg").any { n.toString().lowercase().endsWith(it) } }
        val copies = images.filter { sha(it) in bundledSha }
        assertEquals(emptyList<Path>(), copies, "bản sao (đổi tên) của ảnh BYD trong source set phát hành")
        bundled.filter { it.toString().contains("/oem-car/") }
            .forEach { assertTrue(bydNames.containsMatchIn(it.fileName.toString()), "ảnh lạ trong oem-car: $it") }
    }

    /**
     * 3.01 R25 — mô hình 3D BYD (`car.mesh`/`wheel.mesh` của AutoVideo.apk, hoặc `.glb` dựng từ nó) CHỈ ở
     * `src/vehicleTest/assets/oem-3d/`; bản phát hành đọc lúc chạy từ gói `com.byd.avc` qua `createPackageContext`, và chỉ mã
     * vehicleTest gán móc `Car3dModelSource.bundled`. Không mô hình mẫu nào được đóng thay (owner: không có ⇒ bảng 2D).
     */
    @Test
    fun `mo hinh 3D BYD chi o vehicleTest, ban phat hanh chi doc goi he thong`() {
        val shipped = shippedRoots.flatMap { filesUnder(it) }
        val models = shipped.filter { n -> listOf(".mesh", ".glb", ".gltf").any { n.toString().lowercase().endsWith(it) } }
        assertEquals(emptyList<Path>(), models, "lưới/mô hình 3D trong source set phát hành")
        val meshes = filesUnder("src/vehicleTest/assets").filter { it.toString().endsWith(".mesh") }
        meshes.forEach { assertTrue(it.toString().contains("/oem-3d/"), "lưới BYD ngoài oem-3d: $it") }
        // Soát 3.1 Pass 2: MỌI tệp trích BYD (lưới, ảnh vật liệu, CSV màu) — không chỉ lưới > 1 MB (wheel.mesh 203 KB, CSV 266 B).
        // So cỡ trước (rẻ) rồi mới băm — cây source set phát hành có hàng nghìn tệp.
        val byd = filesUnder("src/vehicleTest/assets")
        val bydSizes = byd.map { Files.size(it) }.toSet()
        val bydSha = byd.map(::sha).toSet()
        assertEquals(emptyList<Path>(), shipped.filter { Files.size(it) in bydSizes && sha(it) in bydSha }, "bản sao đổi tên của tệp BYD")
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/Car3dModelSource.kt")
        assertTrue(src.contains("createPackageContext(BydAvmModels.PACKAGE, 0).assets"))
        val setters = shipped.filter { it.toString().endsWith(".kt") && Regex("""Car3dModelSource\.bundled\s*=""").containsMatchIn(Files.readString(it)) }
        assertEquals(emptyList<Path>(), setters, "mã phát hành không được gán nguồn mô hình đóng gói")
        assertTrue(SourceRoots.text("src/main/AndroidManifest.xml").contains("<package android:name=\"com.byd.avc\" />"))
        assertFalse(SourceRoots.exists("src/main/assets/car3d"), "không mô hình mẫu trong bản phát hành")
    }

    @Test
    fun `duong release chi doc tu goi he thong`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/OemCarArt.kt")
        val system = SourceRoots.body(src, "private fun fromSystem(")
        assertTrue(system.contains("createPackageContext(PKG") && system.contains("getIdentifier("))
        listOf("assets", "R.drawable", "openFd", "File(").forEach {
            assertFalse(src.contains(it), "OemCarArt (main) không được đọc ảnh từ chỗ khác: $it")
        }
        assertTrue(src.contains("const val PKG = \"com.byd.carsettings.plugins\""))
        // Ảnh chưa đo hình học không bao giờ được chọn (mặt nạ lệch ⇒ "miếng xanh dán lệch").
        assertTrue(system.contains("CarArtCatalog.byKey(name) ?: return null"))
        // [ĐO máy ảo 13/10] lỗi thật lượt đầu: `…use { decodeStream(it, null, bounds) } ?: return null` — lượt đo cỡ LUÔN trả
        // null ⇒ ảnh BYD luôn "hỏng" ⇒ lùi ảnh mặc định im lặng. Không lấy kết quả lượt đo cỡ làm điều kiện.
        val decode = SourceRoots.body(src, "fun decode(")
        assertFalse(Regex("""decodeStream\(it, null, bounds\)\s*}\s*\?:""").containsMatchIn(decode))
        assertTrue(decode.contains("bounds.outWidth != art.srcW"), "cỡ ảnh khác số đo ⇒ từ chối (hình học sẽ lệch)")
    }

    @Test
    fun `chi vehicleTest gan moc anh dong goi, manifest khai queries`() {
        val shippedKt = shippedRoots.flatMap { filesUnder(it) }.filter { it.toString().endsWith(".kt") }
        val setters = shippedKt.filter { Regex("""OemCarArt\.bundled\s*=""").containsMatchIn(Files.readString(it)) }
        assertEquals(emptyList<Path>(), setters, "mã phát hành không được gán nguồn ảnh đóng gói")
        val vt = filesUnder("src/vehicleTest").filter { it.toString().endsWith(".kt") }
            .filter { Regex("""OemCarArt\.bundled\s*=""").containsMatchIn(Files.readString(it)) }
        assertEquals(listOf("OemCarArtTestProvider.kt"), vt.map { it.fileName.toString() })
        val main = SourceRoots.text("src/main/AndroidManifest.xml")
        assertTrue(Regex("""<queries>\s*<package android:name="com\.byd\.carsettings\.plugins"\s*/>""").containsMatchIn(main))
        assertFalse(main.contains("OemCarArtTestProvider"))
        assertTrue(SourceRoots.text("src/vehicleTest/AndroidManifest.xml").contains(".OemCarArtTestProvider"))
    }
}
