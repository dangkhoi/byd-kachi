package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ U9 · "MỘT CHIẾC XE" — bảng lớn và icon 24dp phải dùng CÙNG chuỗi path ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-car-boards.html` §3 **R1** (*"ba board dùng CÙNG khung xe với bộ icon v2"*) và **R5**
 * (*"test khoá 3 board dùng chung một nguồn path khung xe — cùng hằng, không chép path"*).
 *
 * ## Vì sao một bài canh mới, khi đã có [IconGeometryContractTest]
 * Bài kia đo **hình học của các tệp drawable** — nó xanh trơn với một bảng Canvas vẽ tay một chiếc xe hoàn toàn
 * khác, vì bảng Canvas không phải drawable. Và đó đúng là hiện trạng trước U9: kiểm kê U7 §1 ghi thẳng rằng ba
 * `*BoardView` đang vẽ lại **cùng một hình** bằng `drawRoundRect` + `drawLine` với tỉ lệ tự chọn. Hai chiếc xe
 * khác nhau trên cùng một màn hình là thứ **không bài nào** bắt được cho tới bài này.
 *
 * ## Ba bất biến được khoá
 *  1. **Không tự vẽ nữa** — ba bảng không còn `drawRoundRect(body…)` / `drawLine(` và đều phải gọi [CarFrames].
 *  2. **Chuỗi path là BẢN SAO NGUYÊN VĂN của icon** — mọi chuỗi path trong `CarFrames.kt` phải xuất hiện
 *     **từng ký tự** trong `android:pathData` của một tệp `res/drawable/ic_car_*.xml`, và ngược lại trọn bộ
 *     `ic_car_top_tyre_<góc>.xml` (thân + hai vạch kính + đúng bánh đó) phải có mặt trong `CarFrames.kt`.
 *     Đây là phép so **hai chiều**: chép thiếu một path thì chiều này đỏ, sửa lệch một số thập phân thì chiều
 *     kia đỏ.
 *  3. **Phóng GIỮ TỈ LỆ** — `ScaleToFit.CENTER`, và chỗ duy nhất được dựng ma trận phóng là [CarFrames].
 *
 * Bài *"0 mã màu hex"* KHÔNG lặp lại ở đây: `ThemePaletteContractTest` quét **mọi** tệp `.kt` trong `launcher/`
 * nên `CarFrames.kt` đã nằm trong phạm vi nó từ lúc tệp ra đời — thêm một bản thứ hai chỉ tạo hai nơi phải sửa.
 */
class CarFramesSourceContractTest {

    private val frames by lazy { code("src/main/java/com/byd/clusternav/launcher/CarFrames.kt") }

    /**
     * Các bảng `BOARD` vẽ bằng Canvas — chúng là toàn bộ chỗ từng (hoặc có thể) tự vẽ thân xe.
     *
     *
     * `DoorBoardView.kt` vào danh sách ở U9 pha 2: nó ra đời SAU [CarFrames] nên chưa bao giờ "tự vẽ", nhưng mọi bất
     * biến còn lại (sàn nét · phóng giữ tỉ lệ · không cấp phát trong `onDraw` · chép path trước khi biến hình) áp
     * cho nó y hệt — bỏ nó ra ngoài là để một bảng sống ngoài tầm bài canh.
     */
    // ⚠ `RadarBoardView.kt` + `SideBoardView.kt` đã XOÁ 2026-09-16 cùng toàn bộ ADAS/an toàn (owner) — hai nhóm
    // duy nhất dùng chúng (`g_parking` · `g_adas`) cũng không còn.
    private val boardFiles = listOf("TyreBoardView.kt", "DoorBoardView.kt")

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private fun boardCode(name: String): String = code("src/main/java/com/byd/clusternav/launcher/$name")

    private fun drawableDir(): Path = SourceRoots.path("src/main/res/drawable")

    /** Mọi `android:pathData` của một tệp drawable, theo đúng thứ tự khai. */
    private fun pathData(file: String): List<String> {
        val text = drawableDir().resolve(file).toFile().readText()
        return PATH_DATA.findAll(text).map { it.groupValues[1] }.toList()
    }

    /** Mọi chuỗi path khai trong `CarFrames.kt` (đã bỏ chú thích ⇒ chỉ còn hằng thật). */
    private fun framePaths(): List<String> = LITERAL.findAll(frames).map { it.groupValues[1] }.toList()

    // ── 1 · ba bảng không còn tự vẽ thân xe ────────────────────────────────────────────────────────────────

    @Test
    fun `ba bang khong con tu ve than xe`() {
        boardFiles.forEach { name ->
            val src = boardCode(name)
            assertTrue(src.contains("CarFrames."), "$name phải lấy hình xe từ CarFrames, không tự dựng")
            assertFalse(
                src.contains("drawRoundRect(body"),
                "$name vẫn vẽ thân xe bằng chữ nhật bo góc — đó là chiếc xe THỨ HAI, khác hẳn bộ icon v2",
            )
            assertFalse(
                src.contains("drawLine("),
                "$name vẫn vẽ vạch kính bằng `drawLine` — vạch kính là một nhánh của CarFrames.TOP_GLASS",
            )
        }
    }

    /**
     * Chuỗi path chỉ được khai ở **một** tệp.
     *
     * Không có bài này thì cách dễ nhất để "sửa cho nhanh" một bảng là chép chuỗi path vào chính nó — đúng cái
     * bệnh `CarFrames` sinh ra để chữa, và bài số 1 ở trên vẫn xanh vì tệp đó *cũng* còn gọi `CarFrames.`.
     */
    @Test
    fun `chi CarFrames duoc giu chuoi path`() {
        Files.list(SourceRoots.path("src/main/java/com/byd/clusternav/launcher")).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.endsWith(".kt") && it != "CarFrames.kt" }
                .toList()
        }.forEach { name ->
            val found = LITERAL.findAll(boardCode(name)).map { it.value }.toList()
            assertTrue(found.isEmpty(), "$name giữ chuỗi path riêng $found — nguồn hình xe phải là CarFrames")
        }
    }

    // ── 2 · chuỗi path khớp NGUYÊN VĂN với drawable ────────────────────────────────────────────────────────

    @Test
    fun `moi chuoi path cua CarFrames deu co that trong bo icon`() {
        val inIcons = Files.list(drawableDir()).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.startsWith("ic_car_") && it.endsWith(".xml") }
                .toList()
        }.flatMap(::pathData).toSet()
        val declared = framePaths()
        assertTrue(declared.size >= EXPECTED_PATHS, "CarFrames phải khai ít nhất $EXPECTED_PATHS chuỗi path")
        declared.forEach { d ->
            assertTrue(
                d in inIcons,
                "chuỗi path này KHÔNG có trong tệp `ic_car_*.xml` nào ⇒ bảng lớn đã trôi khỏi bộ icon: $d",
            )
        }
    }

    /**
     * Chiều ngược lại: trọn bộ một icon lốp (thân · hai vạch kính · đúng bánh đó) phải có mặt trong `CarFrames`.
     *
     * Lấy `ic_car_top_tyre_<góc>.xml` làm mẫu vì nó là tệp DUY NHẤT chứa cả ba mảnh mà bảng lốp vẽ ⇒ một tệp
     * chứng minh được cả khung lẫn bánh cùng đến từ một chiếc xe.
     */
    @Test
    fun `tron bo icon lop tung goc deu nam trong CarFrames`() {
        val declared = framePaths().toSet()
        TyreCorner.values().forEach { corner ->
            val file = "ic_car_top_tyre_${SUFFIX.getValue(corner)}.xml"
            val parts = pathData(file)
            assertEquals(3, parts.size, "$file phải là thân + vạch kính + bánh (3 path)")
            parts.forEach { part ->
                assertTrue(
                    part in declared,
                    "$file có một path mà CarFrames không giữ ⇒ bảng lớn vẽ thiếu/khác icon: $part",
                )
            }
        }
    }

    // ── 3 · phóng giữ tỉ lệ, và chỉ một chỗ dựng ma trận ───────────────────────────────────────────────────

    @Test
    fun `phong hinh xe GIU TI LE, khong keo gian`() {
        assertTrue(
            frames.contains("Matrix.ScaleToFit.CENTER"),
            "phải phóng bằng ScaleToFit.CENTER — kéo giãn một chiều là làm ra chiếc xe thứ hai (R1)",
        )
        listOf("ScaleToFit.FILL", "ScaleToFit.START", "ScaleToFit.END").forEach {
            assertFalse(frames.contains(it), "$it không giữ tỉ lệ ⇒ hình xe lệch khỏi icon")
        }
        boardFiles.forEach { name ->
            assertFalse(
                boardCode(name).contains("setRectToRect("),
                "$name tự dựng ma trận phóng — phải đi qua CarFrames.fit để chỉ có MỘT phép phóng",
            )
        }
    }

    /**
     * Nét khung xe có **SÀN** (R1: *"không mảnh hơn 1.6dp tương đương"*) và sàn đó lấy từ thang [KachiSpace].
     *
     * Tỉ lệ một mình (`m * 0.018`) là đủ ở ô lớn nhưng ở ô nhỏ nhất mà bảng còn dựng, nó xuống dưới một pixel
     * mực — cùng họ lỗi mà các bảng Canvas đã phải vá cho cỡ chữ và cho khe nhãn↔thân xe.
     */
    @Test
    fun `net khung xe co SAN lay tu thang khoang cach`() {
        boardFiles.forEach { name ->
            val src = boardCode(name)
            assertTrue(src.contains("strokeFloorPx"), "$name phải có sàn nét khung xe")
            assertTrue(src.contains("Sp.STROKE"), "$name phải lấy sàn từ KachiSpace, không đẻ số riêng")
        }
    }

    /**
     * Không cấp phát trong `onDraw` — [CarFrames] đưa thêm ba họ đối tượng mới (`Matrix`, `Path`, `RectF`) vào
     * đúng chỗ nóng nhất của bảng.
     *
     * `Goi2FeatureWiringContractTest` đã canh `Paint(` / `Color.parseColor(` cho riêng bảng lốp; bài này phủ ba
     * họ mới cho **cả ba** bảng, vì cả ba đều vừa mọc thêm `carMatrix`/`carPath`.
     */
    @Test
    fun `khong cap phat Matrix Path RectF trong onDraw`() {
        boardFiles.forEach { name ->
            val draw = SourceRoots.body(boardCode(name), "override fun onDraw")
            // Mẫu đòi tên đứng RIÊNG: `canvas.drawPath(` cũng kết thúc bằng "Path(" nhưng nó là lời gọi vẽ, không
            // phải một lượt cấp phát — bản đầu của bài này đỏ oan đúng ở đó.
            listOf("Matrix", "Path", "RectF").forEach { type ->
                assertFalse(
                    Regex("""(?<![A-Za-z0-9_.])$type\(""").containsMatchIn(draw),
                    "$name cấp phát `$type(` trong onDraw — onDraw chạy theo nhịp trạng thái xe",
                )
            }
        }
    }

    /**
     * Bảng không được `transform` thẳng vào [Path] dùng chung của [CarFrames].
     *
     * Path của `CarFrames` là đối tượng **một bản cho cả tiến trình**; biến hình tại chỗ là hỏng hình cho mọi
     * bảng còn lại đang mở — và lỗi đó chỉ lộ ra khi có từ hai bảng trở lên trên màn, tức đúng ca người dùng hay
     * dùng nhất.
     */
    @Test
    fun `bang chep path truoc khi bien hinh`() {
        boardFiles.filter { boardCode(it).contains("carPath") }.forEach { name ->
            val src = boardCode(name)
            assertTrue(src.contains("carPath.set(CarFrames."), "$name phải chép sang carPath trước khi transform")
            assertFalse(
                Regex("""CarFrames\.\w+(\([^)]*\))?\.transform\(""").containsMatchIn(src),
                "$name biến hình THẲNG vào path dùng chung của CarFrames",
            )
        }
    }

    // ── 4 · U9 pha 2: bộ phận mở được — đủ, và KHÔNG có hằng nào không ai gọi ──────────────────────────────

    /**
     * Mọi [CarPart] mà `:core` biết đều phải có **hình** trong [CarFrames].
     *
     * Thiếu một mục thì `CarFrames.part()` trả `null` và bảng **im lặng bỏ qua** đúng bộ phận đó — người lái thấy
     * một chiếc xe không có cửa sổ trời và không có gì báo. Đọc enum từ chính source của `:core` (không chép tám tên
     * vào đây) để thêm một bộ phận ở `:core` mà quên hình là đỏ ngay.
     */
    @Test
    fun `moi bo phan cua core deu co hinh trong CarFrames`() {
        val core = code("src/main/java/com/byd/clusternav/launcher/GroupBoardModel.kt")
        val body = SourceRoots.body(core, "enum class CarPart")
        val parts = Regex("""\b([A-Z][A-Z_0-9]{2,})\b""").findAll(body).map { it.groupValues[1] }.toSet()
        assertTrue(parts.size >= EXPECTED_PARTS, "đọc hụt enum CarPart (thấy $parts)")
        // Quét cả tệp (đã bỏ chú thích): `CarFrames` chỉ nhắc `CarPart.` ở đúng bảng ánh xạ, và `SourceRoots.body`
        // không cắt được một `val` gán bằng `mapOf(…)` (nó nuốt cặp ngoặc rồi đi tìm dấu `=` kế tiếp).
        parts.forEach {
            assertTrue(
                frames.contains("CarPart.$it"),
                "CarFrames thiếu hình của bộ phận $it ⇒ bảng bỏ qua nó, im lặng",
            )
        }
    }

    /**
     * ⚠⚠ **Mọi thành viên công khai của [CarFrames] phải có ÍT NHẤT một chỗ gọi** — CLAUDE.md §8.
     *
     * KDoc của `CarFrames` tự đặt ra luật này (*"một hằng không ai gọi thì không bài canh nào phát hiện được khi nó
     * trôi khỏi icon"*) nhưng trước U9 pha 2 **không có gì kiểm nó**. Dự án đã trả giá đúng chỗ này một lần:
     * `CastShell.evictVd` viết cẩn thận, compile sạch, và **chưa từng được gọi lần nào**.
     */
    @Test
    fun `moi thanh vien cong khai cua CarFrames deu co cho goi`() {
        val api = Regex("""\n    (?:fun|val) (\w+)""").findAll(frames).map { it.groupValues[1] }.toSet()
        assertTrue(api.size >= EXPECTED_API, "đọc hụt API của CarFrames (thấy $api)")
        val callers = Files.list(SourceRoots.path("src/main/java/com/byd/clusternav/launcher")).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it.endsWith(".kt") && it != "CarFrames.kt" }
                .toList()
        }.joinToString("\n") { boardCode(it) }
        api.forEach {
            assertTrue(callers.contains("CarFrames.$it"), "CarFrames.$it không có chỗ gọi nào ⇒ hình trôi mà không ai thấy")
        }
    }

    /**
     * Bảng cửa phóng theo khung **THÂN + bộ phận**, không phóng theo thân không.
     *
     * [ĐO] vạt cửa mở ra tới `x 3.9 … 20.1` trong khi thân chỉ `7.5 … 16.5` ⇒ phóng theo [CarFrames.frameBounds] sẽ
     * cắt cụt đúng bốn thứ mà bảng đó sinh ra để hiện.
     */
    @Test
    fun `bang cua phong theo khung co ca bo phan`() {
        val draw = SourceRoots.body(boardCode("DoorBoardView.kt"), "override fun onDraw")
        assertTrue(draw.contains("CarFrames.openFrameBounds("), "bảng cửa phải lấy khung kèm vạt cửa")
        assertFalse(draw.contains("CarFrames.frameBounds("), "phóng theo thân không thôi sẽ cắt cụt bốn vạt cửa")
    }

    private companion object {
        val PATH_DATA = Regex("""android:pathData="([^"]+)"""")

        /** Chuỗi path SVG viết trong mã Kotlin: nháy kép, bắt đầu bằng lệnh `M`. */
        val LITERAL = Regex(""""(M[0-9][^"]*)"""")

        /**
         * Thân + hai vạch kính (một chuỗi) + 4 bánh = 6 (U9 pha 1), + 4 vạt cửa · cốp · nóc · 2 mảnh rèm · gương
         * = **15** (U9 pha 2).
         */
        const val EXPECTED_PATHS = 15

        /** 4 cửa + cốp + nóc + rèm + gương — số bộ phận mở được mà `:core` khai ở `CarPart`. */
        const val EXPECTED_PARTS = 8

        /**
         * Số thành viên công khai TỐI THIỂU của `CarFrames` — chặn ca mẫu regex đọc hụt rồi bài xanh trơn.
         *
         * Hiện có 9: `topFrame` · `wheelRowGap` · `wheel` · `wheelBounds` · `frameBounds` · `fit` · `part` ·
         * `partBounds` · `openFrameBounds`.
         */
        const val EXPECTED_API = 9

        /**
         * Hậu tố tên tệp icon của từng góc — quy ước **TPMS** (`fl·fr·rl·rr`).
         *
         * Viết ra ở đây chứ không suy từ `TyreCorner.name`: kiểm kê U7 §2 ghi bộ đăng ký dùng tới BỐN quy ước hậu
         * tố khác nhau cho cùng một góc xe (thân xe đảo thành `lf·rf·lr·rr`), nên một phép suy "tự động" sẽ đúng
         * cho họ này và sai câm lặng cho họ kia.
         */
        val SUFFIX: Map<TyreCorner, String> = mapOf(
            TyreCorner.FRONT_LEFT to "fl",
            TyreCorner.FRONT_RIGHT to "fr",
            TyreCorner.REAR_LEFT to "rl",
            TyreCorner.REAR_RIGHT to "rr",
        )
    }
}
