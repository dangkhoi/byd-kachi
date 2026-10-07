package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * 2.96 · WAKE-KEYWORDS-ASSET — khoá lỗi "quên chép": 2.05 (owner chọn A 22/09 — Hey Kachi dễ nổ hơn) sửa `voice/kws/keywords.txt`
 * + bảng ghim [WakeModelCatalog] nhưng KHÔNG chép sang tệp ĐÓNG TRONG APK (`app/src/main/assets/voice/kws/`, từ 1.89) ⇒ xe chạy
 * bản cũ 348 B suốt 2.05–2.95, và mỗi lần khởi động xoá-chép lại gói vì lệch ghim [ĐO log xe 07/10]. Bài này bắt mọi lệch giữa
 * ba nơi: 5 tệp trong APK == 5 tệp ở `voice/kws/` từng byte, và cỡ + sha256 == dòng ghim của catalog.
 */
class WakeKeywordsAssetPinTest {

    private fun repo(rel: String): Path =
        listOf(Paths.get(rel), Paths.get("..", rel)).firstOrNull(Files::exists) ?: error("không thấy $rel")

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun `tep dong trong APK khop voice-kws va khop ghim catalog`() {
        val pack = WakeModelCatalog
        pack.files.forEach { f ->
            val asset = Files.readAllBytes(repo("app/src/main/assets/voice/kws/${f.name}"))
            val source = Files.readAllBytes(repo("voice/kws/${f.name}"))
            assertArrayEquals(source, asset, "${f.name}: APK assets lệch voice/kws/ — chép lại cả hai")
            assertEquals(f.bytes, asset.size.toLong(), "${f.name}: cỡ lệch ghim")
            assertEquals(f.sha256, sha(asset), "${f.name}: sha lệch ghim")
        }
    }
}
