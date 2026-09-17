package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VISUAL-REFRESH P3 · AC1.6 — tầng vẽ hỏi MỘT giao diện ([CarArtSource]); đổi nguồn ảnh không sửa `CarPartStyle`
 * hay ba bảng ═══════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Chứng minh bằng mã, không bằng máy ảo (Canvas/Path là Android): (1) [CarArtPainter] nhận nguồn qua constructor và
 * **chỉ** hỏi các hàm của giao diện; (2) `CarPartStyle` (`:core`) không nhắc tới bất kỳ nguồn nào; (3) ba bảng dựng
 * painter mặc định và không gọi hình học của `CarFrames`/`VectorCarArt` cho việc VẼ (chỉ tra tên mảnh); (4) giao diện
 * đủ cho `RasterCarArt` sau này: mọi hàm trả hộp bao theo hệ 24×24, không lộ `Piece`/`CarFrames` ra ngoài.
 *
 * Nguồn GIẢ: một lớp trong bài này implement [CarArtSource] (compile được là bằng chứng giao diện không rò rỉ kiểu
 * của nguồn vector); vì `Path`/`RectF` là Android nên nó không chạy trên JVM — không cần: bài này canh HỢP ĐỒNG.
 */
class CarArtSourceContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    /** Nguồn giả compile được với đúng chữ ký của giao diện — đổi giao diện là bài này đỏ TRƯỚC khi ai đó viết RasterCarArt. */
    @Suppress("unused")
    private class FakeSource : CarArtSource {
        override fun parts(face: CarFace): List<CarArtPart> = emptyList()
        override fun path(face: CarFace, id: String): android.graphics.Path? = null
        override fun bounds(face: CarFace, id: String, out: android.graphics.RectF): Boolean = false
        override fun frameBounds(face: CarFace, out: android.graphics.RectF) = Unit
        override fun openFrameBounds(face: CarFace, out: android.graphics.RectF) = Unit
        override fun highlightRef(face: CarFace): String = "body"
    }

    @Test
    fun `painter chi hoi giao dien, khong hoi nguon cu the`() {
        val painter = code("CarArtPainter.kt")
        assertTrue(painter.contains("class CarArtPainter(private val src: CarArtSource = VectorCarArt)"), "painter phải nhận nguồn qua constructor")
        // Ngoài giá trị mặc định ở constructor, không dòng nào nhắc VectorCarArt/CarFramesGenerated.
        val body = painter.substringAfter("class CarArtPainter(")
        assertFalse(body.contains("VectorCarArt."), "painter gọi thẳng VectorCarArt")
        assertFalse(body.contains("CarFramesGenerated"), "painter đọc thẳng tệp sinh")
        // Hình học chỉ qua src.* (trừ CarFrames.fit — phép phóng duy nhất, spec R1).
        val direct = Regex("""CarFrames\.(\w+)\(""").findAll(body).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("fit"), direct, "painter gọi CarFrames ngoài fit: $direct")
    }

    @Test
    fun `CarPartStyle o core khong biet nguon anh`() {
        val style = SourceRoots.codeOf("../core/src/main/kotlin/com/byd/clusternav/launcher/CarPartStyle.kt")
        listOf("CarFrames", "VectorCarArt", "RasterCarArt", "android.").forEach {
            assertFalse(style.contains(it), "CarPartStyle nhắc '$it' — bảng :core phải không biết nguồn ảnh")
        }
    }

    @Test
    fun `ba bang dung painter mac dinh va chi tra ten manh`() {
        listOf("TyreBoardView.kt", "DoorBoardView.kt", "CarMiniView.kt").forEach { name ->
            val src = code(name)
            assertTrue(src.contains("CarArtPainter()"), "$name phải dựng painter với nguồn mặc định (đổi nguồn = một chỗ)")
            val allowed = Regex("""CarFrames\.(\w+)""").findAll(src).map { it.groupValues[1] }.toSet()
            assertTrue(allowed.all { it == "pieceIdOf" || it == "wheelIdOf" }, "$name dùng CarFrames ngoài tra tên mảnh: $allowed")
        }
    }

    @Test
    fun `giao dien khong lo kieu cua nguon vector`() {
        val iface = SourceRoots.body(code("CarArtSource.kt"), "internal interface CarArtSource")
        listOf("Piece", "CarFrames", "CarFramesGenerated").forEach { assertFalse(iface.contains(it), "CarArtSource lộ '$it'") }
        assertTrue(iface.contains("fun bounds(face: CarFace, id: String, out: RectF): Boolean"), "hộp bao hệ 24×24 là hợp đồng (§4.8 (2))")
    }

    /**
     * MỌI token [CarInk] phải có màu trong bảng `CarArtPainter.colors`.
     *
     * ⚠ Bảng dùng `colors.getValue(look.fill)` — thiếu một token là **ném lúc VẼ trên xe** (`NoSuchElementException`
     * trong `onDraw` ⇒ launcher mất bảng lốp/bảng cửa/widget xe), và `CarPartStyle` ở `:core` có thể thêm token mà
     * không thấy bảng ở `:app`. Bảng là `Map` chứ không phải `when` vét cạn nên trình biên dịch không giữ hộ ⇒ canh
     * bằng mã: mọi tên enum phải xuất hiện trong thân bảng. (Không gọi `colors` được: `Color.parseColor` là Android.)
     */
    @Test
    fun `moi token CarInk co mau trong bang cua painter`() {
        // `SourceRoots.body` là cho THÂN HÀM (nó vượt danh sách tham số rồi tìm `{`/`=`), không cho một literal
        // `mapOf(...)` ⇒ cắt vùng bằng chính hai mốc của bảng; mốc kết `\n    )` là thụt lề thành viên của lớp.
        val src = code("CarArtPainter.kt")
        val head = "private val colors: Map<CarInk, Int> = mapOf("
        val at = src.indexOf(head)
        assertTrue(at >= 0, "không còn bảng màu CarInk trong CarArtPainter — bài này đang quét vùng không tồn tại")
        val end = src.indexOf("\n    )", at)
        assertTrue(end > at, "không tìm được cuối bảng màu CarInk")
        val table = src.substring(at, end)
        val missing = CarInk.values().filterNot { Regex("""CarInk\.${it.name}\b""").containsMatchIn(table) }
        assertEquals(emptyList<CarInk>(), missing, "token CarInk không có màu ⇒ getValue() ném lúc vẽ trên xe: $missing")
    }
}
