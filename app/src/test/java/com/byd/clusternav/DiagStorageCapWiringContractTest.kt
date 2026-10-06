package com.byd.clusternav

import com.byd.clusternav.core.DiagFiles
import com.byd.clusternav.launcher.CarImageStore
import com.byd.clusternav.launcher.PhotoStore
import com.byd.clusternav.launcher.WallArtBuilder
import com.byd.clusternav.launcher.WallpaperStore
import com.byd.clusternav.launcher.testbridge.TestBridgeReply
import com.byd.clusternav.launcher.voice.VoiceModelSideload
import com.byd.clusternav.launcher.voice.VoiceUtteranceLog
import com.byd.clusternav.launcher.voice.VoiceWavProbe
import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * DIAG-CAP-USERDATA (2.92 · P1 mất dữ liệu · spec `docs/specs/kachi-292-diag-cap.html`).
 *
 * Tới 2.91 `DiagStorageCap` dọn CẢ `getExternalFilesDir(null)` xuống ~150 MB, tệp cũ nhất đi trước — cùng cây ấy
 * chứa ảnh trình chiếu, hình nền, ảnh xe, hồ sơ xuất và gói giọng side-load. Bản sửa đổi sang DANH SÁCH CHO PHÉP
 * (`:core` [DiagFiles]); bài này khoá hai việc:
 *
 *  1. **Mọi** lời gọi `getExternalFilesDir` trong mã nguồn phải có mặt trong bảng [writers] và được xếp loại
 *     (chẩn đoán / người dùng / chỉ đọc). Thêm một bộ ghi mới mà không xếp loại ⇒ đỏ — không ai "vô tình" đặt dữ
 *     liệu người dùng vào đường bộ dọn chạm tới, cũng không ai thêm log mà quên cho bộ dọn biết (log phình vô hạn).
 *  2. Đường thật mỗi bộ ghi tạo ra (đọc từ HẰNG thật hoặc chuỗi trong mã) khớp đúng luật [DiagFiles.isDiagnostic]:
 *     chẩn đoán ⇒ `true`, người dùng ⇒ `false`. Đổi tên thư mục/tiền tố ở bộ ghi mà không đổi luật ⇒ đỏ.
 *
 * Và chốt bộ dọn chỉ đi qua lối danh-sách-cho-phép (gỡ ⇒ đỏ).
 */
class DiagStorageCapWiringContractTest {

    private enum class Kind { DIAG, USER, READER, CAP }

    /**
     * @param pins chuỗi phải có trong MÃ (đã bỏ chú thích) của tệp — đường mà bộ ghi thật sự tạo ra.
     * @param samples đường TƯƠNG ĐỐI gốc thư mục ngoài mà bộ ghi tạo ra (dựng từ hằng thật khi hằng không private).
     * @param calls số lời gọi `getExternalFilesDir` trong tệp (senior review 2.92 Pass 3 [P3]): xếp loại theo TỆP mà không
     *   đếm thì lời gọi THỨ HAI thêm vào một tệp đã có trong bảng (vd một log mới trong `PhotoStore`) lọt qua im lặng.
     */
    private class Writer(val kind: Kind, val pins: List<String>, val samples: List<String>, val calls: Int = 1)

    /** Khoá = đường tệp tương đối gốc source (`com/byd/…/X.kt`). */
    private val writers: Map<String, Writer> = mapOf(
        // ── chẩn đoán: bộ dọn được xoá, cũ trước, trong trần 150 MB ──
        "com/byd/clusternav/launcher/KachiLog.kt" to Writer(
            Kind.DIAG, listOf("""FOLDER = "kachi-logs""""),
            listOf("kachi-logs/usage-1759740000000.log", "kachi-logs/crash-1759740000000-4242.log", "kachi-logs/captest-report.txt"),
        ),
        "com/byd/clusternav/modules/clustercast/ClusterDiag.kt" to Writer(
            Kind.DIAG, listOf("""getExternalFilesDir(null), "diag")""", """"diag-${'$'}stamp.txt""""),
            listOf("diag/diag-20261006-093000.txt"),
        ),
        "com/byd/clusternav/launcher/testbridge/TestBridgeReply.kt" to Writer(
            Kind.DIAG, listOf("getExternalFilesDir(DIR)"), listOf("${TestBridgeReply.DIR}/20261006-093000-123-state.json"),
        ),
        "com/byd/clusternav/NavNotifLog.kt" to Writer(
            Kind.DIAG, listOf(""""nav_notif_log_${'$'}{System.currentTimeMillis()}.csv""""), listOf("nav_notif_log_1759740000000.csv"),
        ),
        "com/byd/clusternav/NavNotifRawLog.kt" to Writer(
            Kind.DIAG, listOf(""""nav_notif_raw_${'$'}{System.currentTimeMillis()}.csv""""), listOf("nav_notif_raw_1759740000000.csv"),
        ),
        "com/byd/clusternav/launcher/voice/VoiceUtteranceLog.kt" to Writer(
            Kind.DIAG, listOf("""EXPORT_PREFIX + stampFormat.format(Date()) + ".zip"""", """SimpleDateFormat("yyyyMMdd-HHmmss-SSS""""),
            listOf("${VoiceUtteranceLog.EXPORT_PREFIX}20261006-093000-123.zip"),
        ),
        "com/byd/clusternav/launcher/testbridge/TestBridgeWav.kt" to Writer(
            Kind.DIAG, listOf("File(dir, VoiceWavProbe.FILE_NAME)"), listOf(VoiceWavProbe.FILE_NAME),
        ),
        "com/byd/clusternav/launcher/testbridge/TestBridgeKws.kt" to Writer(
            Kind.DIAG, listOf("File(dir, VoiceWavProbe.FILE_NAME)"), listOf(VoiceWavProbe.FILE_NAME),
            calls = 2,   // đọc lại (`readPcmFile`) + chép vào — cùng một tệp `kachi-voice-test.wav`
        ),
        // ── người dùng: bộ dọn KHÔNG BAO GIỜ chạm ──
        "com/byd/clusternav/launcher/PhotoStore.kt" to Writer(
            Kind.USER, listOf("File(base, FOLDER)"), listOf("${PhotoStore.FOLDER}/IMG_0001.jpg"),
        ),
        "com/byd/clusternav/launcher/WallpaperStore.kt" to Writer(
            Kind.USER, listOf("File(base, FOLDER)"),
            listOf("${WallpaperStore.FOLDER}/beach.jpg", "${WallpaperStore.FOLDER}/${WallArtBuilder.CACHE_DIR}/beach-1.png"),
        ),
        "com/byd/clusternav/launcher/CarImageStore.kt" to Writer(
            Kind.USER, listOf("File(base, FOLDER)"), listOf("${CarImageStore.FOLDER}/my-car.png"),
        ),
        "com/byd/clusternav/launcher/ProfileIoStore.kt" to Writer(
            Kind.USER, listOf("""DIR = "profiles"""", "File(it, DIR)"), listOf("profiles/Gia dinh-20261006-0930.kachi"),
        ),
        "com/byd/clusternav/launcher/voice/VoiceModelStore.kt" to Writer(
            Kind.USER, listOf("VoiceModelSideload.IMPORT_SUBDIR"),
            listOf("${VoiceModelSideload.IMPORT_SUBDIR}/zipformer-vi-int8/encoder.onnx"),
        ),
        // ── chỉ đọc: không tạo tệp nào trong cây ngoài ──
        "com/byd/clusternav/launcher/voice/VoiceWavProbe.kt" to Writer(Kind.READER, listOf("File(it, FILE_NAME)"), emptyList()),
        "com/byd/clusternav/launcher/camera/CameraSignalController.kt" to Writer(Kind.READER, listOf("if (f.isFile) f.absolutePath"), emptyList()),
        // ── chính bộ dọn ──
        "com/byd/clusternav/DiagStorageCap.kt" to Writer(Kind.CAP, emptyList(), emptyList()),
    )

    private val call = Regex("""\bgetExternalFilesDirs?\(""")

    /** Bỏ chú thích bằng ĐÚNG bộ quét của [SourceRoots.codeOf] ([KotlinSource.stripComments]) — KDoc nhắc tên hàm không phải lời gọi. */
    private fun code(p: Path): String = KotlinSource.stripComments(Files.readString(p))

    /**
     * Gốc quét = mã sản phẩm của `:app`/`:core`/`:car-integration` (spec R4) + nguồn RIÊNG của biến thể `vehicleTest`
     * (`app/src/vehicleTest/java` — cùng `:app`, cùng bộ dọn chạy trong bản ấy; senior review 2.92 Pass 3 [P3]).
     */
    private fun roots(): List<Path> = SourceRoots.moduleSourceRoots() +
        listOf("app/src/vehicleTest/java", "../app/src/vehicleTest/java").map { Path.of(it) }.filter { Files.exists(it) }

    private fun callSites(): Map<String, String> = roots().flatMap { root ->
        Files.walk(root).use { s ->
            s.filter { it.toString().endsWith(".kt") || it.toString().endsWith(".java") }.toList()
        }.map { root.relativize(it).toString().replace('\\', '/') to code(it) }
    }.filter { (_, c) -> call.containsMatchIn(c) }.toMap()

    @Test
    fun `moi loi goi getExternalFilesDir deu duoc xep loai chan doan hay nguoi dung`() {
        assertTrue(roots().any { it.toString().replace('\\', '/').endsWith("app/src/vehicleTest/java") }, "phải quét cả vehicleTest")
        val sites = callSites()
        val found = sites.keys
        val unclassified = found - writers.keys
        assertTrue(
            unclassified.isEmpty(),
            "bộ ghi MỚI vào thư mục ngoài chưa xếp loại: $unclassified — chẩn đoán ⇒ thêm đường vào `:core` DiagFiles; " +
                "dữ liệu người dùng ⇒ ghi Kind.USER (bộ dọn không bao giờ chạm). Rồi thêm vào bảng của bài này.",
        )
        assertEquals(writers.keys, found, "bảng có mục không còn gọi getExternalFilesDir — sửa bảng cho thật")
        // Xếp loại theo LỜI GỌI, không chỉ theo tệp: thêm lời gọi thứ hai vào một tệp đã xếp loại ⇒ đỏ, xếp loại lại.
        sites.forEach { (file, src) ->
            assertEquals(writers.getValue(file).calls, call.findAll(src).count(), "$file: số lời gọi getExternalFilesDir đổi — xếp loại lại")
        }
    }

    @Test
    fun `duong that cua moi bo ghi khop luat danh sach cho phep`() {
        val sites = callSites()
        writers.forEach { (file, w) ->
            val src = sites.getValue(file)
            w.pins.forEach { pin -> assertTrue(pin in src, "$file: không còn thấy «$pin» — đường ghi đã đổi, xếp loại lại") }
            w.samples.forEach { sample ->
                when (w.kind) {
                    Kind.DIAG -> assertTrue(DiagFiles.isDiagnostic(sample), "$file ghi «$sample» — phải nằm trong DiagFiles")
                    Kind.USER -> assertFalse(DiagFiles.isDiagnostic(sample), "$file là DỮ LIỆU NGƯỜI DÙNG «$sample» — bộ dọn không được chạm")
                    Kind.READER, Kind.CAP -> Unit
                }
            }
        }
        assertEquals(DiagFiles.VOICE_TEST_WAV, VoiceWavProbe.FILE_NAME, "một tên tệp mẫu tiếng, hai chỗ phải trùng")
        assertEquals(DiagFiles.TEST, TestBridgeReply.DIR)
    }

    @Test
    fun `bo don chi di qua loi danh sach cho phep`() {
        val cap = callSites().getValue("com/byd/clusternav/DiagStorageCap.kt")
        val locked = SourceRoots.body(cap, "private fun enforceLocked(app: Context)")
        assertTrue("StorageCapPlanner.selectDiagForDeletion(" in locked, "planner lọc theo DiagFiles")
        assertFalse(Regex("""\bselectForDeletion\(""").containsMatchIn(cap), "cấm gọi planner KHÔNG lọc")
        assertTrue("diagnosticFiles(base)" in locked, "chỉ liệt kê đường cho phép")
        assertFalse("collectFiles(base" in cap, "cấm duyệt cả cây ngoài (bản 2.91)")
        assertFalse("pruneEmptyDirs(base)" in cap, "cấm dọn thư mục rỗng ở gốc — photos/ wallpapers/ car/ là cố ý")
        assertTrue("DiagFiles.DIRS.forEach { d -> runCatching { pruneEmptyDirs(File(base, d)) } }" in locked)
        val enumerate = SourceRoots.body(cap, "private fun diagnosticFiles(base: File): List<File>")
        assertTrue("DiagFiles.DIRS.forEach" in enumerate && "DiagFiles.isDiagnostic(it.name)" in enumerate)
        // Senior review 2.92 Pass 3 [P3]: ở GỐC lọc TÊN trước `isFile` ⇒ thư mục người dùng (`photos/`, `sherpa/`…) không bị
        // cả `stat` (KDoc + spec R3 "không liệt kê, không cả stat") — bản đầu `it.isFile && …` stat mọi mục ở gốc.
        assertTrue("DiagFiles.isDiagnostic(it.name) && it.isFile" in enumerate, "gốc: tên trước, stat sau")
        // Liên kết tượng trưng trong (hoặc mang tên) thư mục cho phép không được dẫn bộ dọn ra chỗ khác — cả lúc liệt kê
        // lẫn lúc dọn thư mục rỗng. Hành vi cần hệ tệp thật ⇒ ghim mã (gỡ một chốt ⇒ đỏ).
        assertTrue("it.isDirectory && !isLink(it)" in enumerate, "không đi vào thư mục cho phép là liên kết")
        assertTrue("if (!isLink(c)) collectFiles(c, out)" in SourceRoots.body(cap, "private fun collectFiles(dir: File, out: MutableList<File>)"))
        val prune = SourceRoots.body(cap, "private fun pruneEmptyDirs(dir: File)")
        assertTrue(prune.removePrefix("{").trimStart().startsWith("if (isLink(dir)) return"), "dọn rỗng: chốt liên kết ĐẦU hàm")
        assertTrue("c.isDirectory && !isLink(c)" in prune)
        assertTrue("Files.isSymbolicLink(f.toPath())" in SourceRoots.body(cap, "private fun isLink(f: File): Boolean"))
    }
}
