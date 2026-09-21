package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP9 / T9 — gói giọng bé tải qua OTA dưới dạng **một archive .zip** (spec R9.1: ghim sha256 từng tệp cho 1 546
 * clip là bất khả ⇒ đóng một `.zip`, ghim sha256 của chính nó, bung sau khi tải).
 */
class ClipVoicePackContractTest {

    private val pack = KachiClipVoiceCatalog.OTA_PACK

    @Test
    fun `goi clip OTA la mot tep zip da ghim, tai duoc`() {
        assertEquals(1, pack.files.size, "gói clip phải là MỘT tệp .zip (không phải 1 546 tệp rời)")
        val zip = pack.files.single()
        assertTrue(zip.name.endsWith(".zip"), "tệp duy nhất phải là archive .zip")
        assertTrue(zip.pinned, "archive phải ghim sha256 + bytes (nếu không thì tải mù)")
        assertTrue(pack.downloadable, "đã ghim ⇒ tải được qua mạng")
        assertEquals(pack.files.sumOf { it.bytes }, pack.totalBytes, "tổng byte = byte của archive")
        assertTrue(zip.url.startsWith("https://"), "URL tải phải là HTTPS")
    }

    @Test
    fun `dir bung ra trung cho side-load doc, va la goi archive`() {
        assertEquals(KachiClipVoiceCatalog.DIR, pack.dir, "OTA và side-load phải cho ra CÙNG cây thư mục")
        assertTrue(KachiClipVoiceCatalog.isArchivePack(pack), "VoiceModelStore phải nhận ra đây là gói cần giải nén")
        assertFalse(
            KachiClipVoiceCatalog.isArchivePack(SherpaTtsCatalog.PIPER_VI_VAIS1000),
            "gói Piper KHÔNG phải archive (tải từng tệp) — không được bung nhầm",
        )
    }
}
