package com.byd.clusternav.launcher.voice

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá đường side-load mô hình voice (owner 2026-09-15, xe không internet): tệp chép từ USB/adb phải qua ĐÚNG phép
 * kiểm bytes + sha256 như đường mạng; sai là xoá đích + lỗi nói rõ; thiếu tệp ⇒ `null` để caller đi đường mạng.
 */
class VoiceModelSideloadTest {

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun tmpDir(): File = Files.createTempDirectory("sideload").toFile().apply { deleteOnExit() }

    @Test
    fun `tep dung bytes va sha256 duoc chep vao dich`() {
        val dir = tmpDir()
        val payload = ByteArray(200_000) { (it * 31).toByte() }
        val src = File(dir, "model.onnx").apply { writeBytes(payload) }
        val out = File(dir, "staging/model.onnx")
        val err = VoiceModelSideload.copyVerified(src, out, payload.size.toLong(), sha256(payload))
        assertNull(err, "tệp đúng phải OK")
        assertTrue(out.isFile && out.length() == payload.size.toLong(), "đích phải có đủ byte")
        assertEquals(sha256(payload), sha256(out.readBytes()), "nội dung đích y hệt nguồn")
    }

    @Test
    fun `sai sha256 thi xoa dich va bao loi noi ro side-load`() {
        val dir = tmpDir()
        val payload = ByteArray(1000) { 7 }
        val src = File(dir, "tokens.txt").apply { writeBytes(payload) }
        val out = File(dir, "staging/tokens.txt")
        val err = VoiceModelSideload.copyVerified(src, out, payload.size.toLong(), "00".repeat(32))
        // Chuỗi đi qua Lang.t (vi/en) — chỉ khoá phần bất biến "side-load" + tên tệp, không khoá ngôn ngữ hiện hành.
        assertNotNull(err); assertTrue(err!!.contains("side-load") && err.contains("tokens.txt"), "lỗi phải nói tệp side-load sai: $err")
        assertFalse(out.exists(), "đích phải bị xoá — không để lại tệp hỏng cho bước Verifying")
    }

    @Test
    fun `sai kich thuoc cung bi tu choi du sha co the trung`() {
        val dir = tmpDir()
        val payload = ByteArray(10) { 1 }
        val src = File(dir, "a.bin").apply { writeBytes(payload) }
        val out = File(dir, "s/a.bin")
        val err = VoiceModelSideload.copyVerified(src, out, 11L, sha256(payload))
        assertNotNull(err); assertFalse(out.exists())
    }

    @Test
    fun `khong co tep hoac tep rong thi candidate null de di duong mang`() {
        val dir = tmpDir()
        assertNull(VoiceModelSideload.candidate(dir, "missing.onnx"))
        File(dir, "empty.onnx").writeBytes(ByteArray(0))
        assertNull(VoiceModelSideload.candidate(dir, "empty.onnx"), "tệp 0 byte không phải side-load hợp lệ")
        assertNull(VoiceModelSideload.candidate(null, "x"), "không có thư mục ngoài (vắng thẻ) ⇒ null, không ném")
        File(dir, "ok.onnx").writeBytes(ByteArray(5) { 2 })
        assertNotNull(VoiceModelSideload.candidate(dir, "ok.onnx"))
    }

    @Test
    fun `nguon khong doc duoc thi bao loi side-load chu khong nem ra ngoai`() {
        val dir = tmpDir()
        // Nguồn là THƯ MỤC (hoặc tệp bị thu quyền đọc trên thẻ) ⇒ `inputStream()` ném. `install` gọi hàm này trên
        // luồng nền và KHÔNG có try/catch bao quanh ⇒ ném ra đây là giết luồng cài, người dùng thấy nút treo.
        val src = File(dir, "encoder.onnx").apply { mkdirs() }
        val out = File(dir, "staging/encoder.onnx")
        val err = VoiceModelSideload.copyVerified(src, out, 10L, "00".repeat(32))
        assertNotNull(err); assertTrue(err!!.contains("side-load") && err.contains("encoder.onnx"), "lỗi: $err")
        assertFalse(out.exists(), "không để lại tệp cụt")
    }

    @Test
    fun `nhip tien trinh bao tong byte tang dan va chot dung co tep`() {
        val dir = tmpDir()
        val payload = ByteArray(300_000) { (it % 251).toByte() }   // > 4 khối 64 KB ⇒ nhiều nhịp
        val src = File(dir, "big.onnx").apply { writeBytes(payload) }
        val seen = mutableListOf<Long>()
        val err = VoiceModelSideload.copyVerified(
            src, File(dir, "s/big.onnx"), payload.size.toLong(), sha256(payload),
        ) { seen += it }
        assertNull(err)
        assertTrue(seen.size >= 4, "phải có nhiều nhịp để thanh tiến trình nhúc nhích: ${seen.size}")
        assertEquals(seen.sorted(), seen, "tổng byte phải TĂNG DẦN")
        assertEquals(payload.size.toLong(), seen.last(), "nhịp cuối = đúng cỡ tệp")
    }

    /**
     * ⚠ **Đổi có chủ ý ở T8** (spec `kachi-voice-feedback.html`): trước đây MỌI `/` bị từ chối; nay `/` được
     * phép làm **dấu ngăn đoạn** vì gói ĐỌC mang một cây thư mục (`espeak-ng-data/lang/aav/vi`) — cấm `/` là cấm
     * luôn cả gói. Phần **chống leo thư mục thì không nới một ly**: mỗi đoạn vẫn phải khác rỗng, khác `.`/`..`,
     * không `\`/`:`, và cả chuỗi không bắt đầu bằng `/`. Bài này khoá đúng ranh giới đó.
     */
    @Test
    fun `duong dan nhieu doan duoc nhan, moi kieu leo thu muc bi tu choi`() {
        val dir = tmpDir()
        File(dir, "evil.txt").writeBytes(ByteArray(3))
        File(dir, "espeak-ng-data/lang/aav").mkdirs()
        File(dir, "espeak-ng-data/lang/aav/vi").writeBytes(ByteArray(7))
        // Tên luôn tới từ bản ghim, nhưng lớp này vẫn tự chặn: một call site tương lai quên `requireSafe` thì
        // side-load KHÔNG được trở thành đường leo ra khỏi thư mục import (CLAUDE.md §4.1).
        assertNull(VoiceModelSideload.candidate(dir, "../evil.txt"))
        assertNull(VoiceModelSideload.candidate(dir, "sub/../../evil.txt"))
        assertNull(VoiceModelSideload.candidate(dir, "/etc/passwd"))
        assertNull(VoiceModelSideload.candidate(dir, "a//b"))
        assertNull(VoiceModelSideload.candidate(dir, "a\\b"))
        assertNull(VoiceModelSideload.candidate(dir, ".."))
        assertNotNull(VoiceModelSideload.candidate(dir, "evil.txt"), "tên thuần vẫn phải nhận")
        assertNotNull(
            VoiceModelSideload.candidate(dir, "espeak-ng-data/lang/aav/vi"),
            "T8: cây thư mục của gói ĐỌC phải chép nguyên được từ USB",
        )
    }

    @Test
    fun `sha256 so khong phan biet hoa thuong`() {
        val dir = tmpDir()
        val payload = "hello".toByteArray()
        val src = File(dir, "h").apply { writeBytes(payload) }
        val err = VoiceModelSideload.copyVerified(src, File(dir, "o/h"), payload.size.toLong(), sha256(payload).uppercase())
        assertNull(err)
    }
}
