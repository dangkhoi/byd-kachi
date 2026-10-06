package com.byd.clusternav.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DIAG-CAP-USERDATA (2.92, P1 mất dữ liệu): bộ dọn ~150 MB từng quét CẢ thư mục ngoài của app và xoá tệp cũ nhất
 * trước — mà ảnh trình chiếu / hình nền / ảnh xe / hồ sơ xuất / gói giọng side-load nằm cùng cây và thường là cũ
 * nhất [ĐO mã `DiagStorageCap.enforceLocked` 2.91]. Bài này khoá:
 *  1. luật xếp loại [DiagFiles.isDiagnostic] — đúng những đường bộ ghi chẩn đoán tạo ra, không hơn;
 *  2. [StorageCapPlanner.selectDiagForDeletion] — thư mục người dùng KHÔNG BAO GIỜ được chọn, kể cả khi cũ nhất
 *     và to nhất; phần chẩn đoán vẫn dọn cũ-trước đúng trần 150 MB như trước.
 */
class DiagFilesTest {

    private val mb = 1024L * 1024L
    private fun entry(id: String, sizeMb: Long, ageMs: Long) = StorageCapPlanner.Entry(id, sizeMb * mb, ageMs)

    /** Đường của MỌI kho người dùng hiện có (khớp `DiagStorageCapWiringContractTest` bên `:app`). */
    private val userPaths = listOf(
        "photos/IMG_0001.jpg",                          // PhotoStore — trình chiếu
        "wallpapers/beach.jpg",                         // WallpaperStore
        "wallpapers/.kachi-art/beach-1-2-3x4-FILL-35.png", // WallArt — bộ đệm nằm TRONG thư mục hình nền
        "car/my-car.png",                               // CarImageStore
        "profiles/Gia dinh-20261006-0930.kachi",        // ProfileIoStore
        "sherpa/import/zipformer-vi-int8/encoder.onnx", // VoiceModelStore — side-load
    )

    @Test
    fun `duong cua bo ghi chan doan duoc xep la chan doan`() {
        listOf(
            "kachi-logs/usage-1759740000000.log",
            "kachi-logs/crash-1759740000000-4242.log",
            "kachi-logs/captest-report.txt",
            "kachi-logs/sub/deeper/frame.png",
            "diag/diag-20261006-093000.txt",
            "test/20261006-093000-123-state.json",
            "castlog/cast_cast_v0.61_1784809230068.txt",
            "nav_notif_log_1759740000000.csv",
            "nav_notif_raw_1759740000000.csv",
            "kachi-voice-20261006-093000-123.zip",
            "kachi-voice-test.wav",
        ).forEach { assertTrue(DiagFiles.isDiagnostic(it), "phải là chẩn đoán: $it") }
    }

    @Test
    fun `du lieu nguoi dung va duong la khong bao gio la chan doan`() {
        (userPaths + listOf(
            "random.bin",                       // tệp lạ ở gốc — mặc định KHÔNG chạm
            "kachi-logs",                       // chính thư mục (không phải tệp bên trong)
            "diag",                             // tệp TÊN "diag" ở gốc ≠ thư mục diag/
            "photos/kachi-logs/x.log",          // tên thư mục chẩn đoán lồng trong thư mục người dùng
            "Kachi-Logs/usage-1.log",           // khác hoa thường ⇒ không phải bộ ghi của ta
            "kachi-voice-backup.zip",           // trùng tiền tố nhưng mốc không phải số
            "kachi-voice-.zip",                 // mốc rỗng
            "kachi-voice--.zip",                // mốc không có chữ số nào (senior review 2.92 Pass 3)
            "nav_notif_log_---.csv",            // như trên — mốc của mọi bộ ghi luôn có chữ số
            "nav_notif_log_1.txt",              // sai đuôi
            "nav_notif_log_１２.csv",            // chữ số toàn hình — không phải mốc của bộ ghi
            "kachi-voice-test.wav.bak",
            "", "/", "/kachi-logs/x.log", "kachi-logs/../photos/a.jpg", "./kachi-logs/x.log",
            "kachi-logs//x.log", "kachi-logs/x.log/", "kachi-logs\\x.log",
        )).forEach { assertFalse(DiagFiles.isDiagnostic(it), "KHÔNG được là chẩn đoán: «$it»") }
    }

    @Test
    fun `thu muc nguoi dung khong bao gio bi chon du cu nhat va to nhat`() {
        // Người dùng: 1,2 GB, CŨ HƠN mọi log. Chẩn đoán: 200 MB > trần 150 ⇒ dọn đúng log cũ nhất, không tệp nào khác.
        val entries = userPaths.mapIndexed { i, p -> entry(p, sizeMb = 200, ageMs = i.toLong()) } + listOf(
            entry("kachi-logs/usage-old.log", sizeMb = 60, ageMs = 1_000),
            entry("diag/diag-mid.txt", sizeMb = 70, ageMs = 2_000),
            entry("nav_notif_log_3000.csv", sizeMb = 70, ageMs = 3_000),
        )
        assertEquals(listOf("kachi-logs/usage-old.log"), StorageCapPlanner.selectDiagForDeletion(entries))
    }

    @Test
    fun `chi rieng du lieu nguoi dung vuot tran thi khong xoa gi`() {
        val entries = userPaths.map { entry(it, sizeMb = 500, ageMs = 1) } + entry("random.bin", 900, 0)
        assertTrue(StorageCapPlanner.selectDiagForDeletion(entries).isEmpty())
        // Trần âm (vô nghĩa) xưa nay = "xoá hết" — nay "hết" chỉ là hết phần CHẨN ĐOÁN.
        assertTrue(StorageCapPlanner.selectDiagForDeletion(entries, capBytes = -1).isEmpty())
    }

    @Test
    fun `nguoi dung to khong day log ra som - tran chi tinh phan chan doan`() {
        // Log 100 MB < trần; ảnh 5 GB. Bản 2.91 tính chung ⇒ xoá ảnh. Nay: không xoá gì.
        val entries = listOf(
            entry("photos/a.jpg", sizeMb = 5_000, ageMs = 1),
            entry("kachi-logs/usage-1.log", sizeMb = 100, ageMs = 2),
        )
        assertTrue(StorageCapPlanner.selectDiagForDeletion(entries).isEmpty())
    }

    @Test
    fun `phan chan doan don cu truoc qua moi thu muc cho phep`() {
        val entries = listOf(
            entry("test/c.json", sizeMb = 50, ageMs = 300),
            entry("castlog/a.txt", sizeMb = 50, ageMs = 100),
            entry("kachi-voice-20261006-1.zip", sizeMb = 50, ageMs = 200),
            entry("kachi-logs/d.log", sizeMb = 50, ageMs = 400),
            entry("wallpapers/old.jpg", sizeMb = 50, ageMs = 0),
        )
        // Chẩn đoán 200 MB, trần 120 ⇒ bỏ 2 tệp cũ nhất (castlog 100, zip 200); ảnh nền cũ hơn vẫn nguyên.
        assertEquals(
            listOf("castlog/a.txt", "kachi-voice-20261006-1.zip"),
            StorageCapPlanner.selectDiagForDeletion(entries, capBytes = 120L * mb),
        )
    }

    @Test
    fun `id tuyet doi khong khop gi - hong o huong an toan`() {
        // Bộ dọn phải đưa id TƯƠNG ĐỐI; lỡ đưa tuyệt đối thì kết quả là không xoá gì, không phải xoá nhầm.
        val entries = listOf(entry("/sdcard/Android/data/x/files/kachi-logs/a.log", sizeMb = 900, ageMs = 1))
        assertTrue(StorageCapPlanner.selectDiagForDeletion(entries).isEmpty())
    }
}
