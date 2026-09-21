package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP9 / T9 — dây nối tải + giải nén giọng bé OTA. Grep-based (dự án không dùng Robolectric): pin chỗ mà một
 * lượt refactor dễ làm rơi âm thầm.
 */
class ClipVoiceWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val store by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt") }
    private val row by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/ClipVoiceRow.kt") }
    private val settings by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt") }

    @Test
    fun `store bung archive bang java util zip, co guard chong leo thu muc`() {
        assertTrue(store.contains("KachiClipVoiceCatalog.isArchivePack(pack)"), "chỉ bung khi là gói archive")
        assertTrue(store.contains("java.util.zip.ZipInputStream"), "dùng java.util.zip có sẵn, không viết decompressor")
        assertTrue(store.contains("requireSafe(name)"), "mỗi mục zip đi qua guard chống zip-slip")
        assertTrue(store.contains("canon.path.startsWith(destRoot.path"), "và kiểm canonicalPath nằm trong đích")
        assertTrue(store.contains("zip.delete()"), "xoá tệp nén sau khi bung (để renameTo đưa CÂY clip vào đích)")
    }

    @Test
    fun `hang Cai dat tai giong be qua OTA_PACK, san sang doc tep da bung`() {
        assertTrue(settings.contains("ClipVoiceRow(context, rows).build(body)"), "màn Cài đặt phải dựng hàng giọng bé")
        assertTrue(row.contains("KachiClipVoiceCatalog.OTA_PACK"), "hàng dùng đúng gói OTA")
        assertTrue(row.contains("VoiceModelStore.install(context, pack)"), "tải + bung qua đường cài chung")
        // Readiness đo bằng tệp ĐÃ BUNG — nhưng luật ấy phải ở MỘT chỗ (store), không chép vào tầng vẽ.
        assertTrue(
            row.contains("VoiceModelStore.isReady(context, pack)"),
            "hàng phải hỏi store, không tự kiểm tệp (bản sao thứ hai của luật lưu bền sẽ lệch)",
        )
        assertEquals(
            0, Regex("""INDEX_FILE|NUM_FILE""").findAll(row).count(),
            "tầng vẽ không được biết tên tệp dấu hiệu — đó là việc của KachiClipVoiceCatalog/VoiceModelStore",
        )
    }

    /**
     * ⚠ `isReady` phải hỏi [KachiClipVoiceCatalog.EXTRACTED_FILES] cho gói archive. Bỏ nhánh này thì gói đã lắp
     * xong báo *"chưa cài"* (cái `.zip` đã bị xoá) ⇒ `install()` mất đường thoát sớm và `remove()` xoá gói đang
     * chạy tốt trước khi tải lại 10,8 MB.
     */
    @Test
    fun `isReady hoi dau hieu da BUNG cho goi archive, khong hoi cai zip da xoa`() {
        val fn = SourceRoots.body(store, "fun isReady(")
        assertTrue(
            fn.contains("KachiClipVoiceCatalog.isArchivePack(pack)") &&
                fn.contains("KachiClipVoiceCatalog.EXTRACTED_FILES"),
            "isReady phải rẽ nhánh cho gói archive sang danh sách tệp đã bung",
        )
        assertEquals(
            listOf(KachiClipVoiceCatalog.INDEX_FILE, KachiClipVoiceCatalog.NUM_FILE),
            KachiClipVoiceCatalog.EXTRACTED_FILES,
            "dấu hiệu 'đã lắp' của gói clip = đúng hai bảng tra mà ClipSpeaker đọc",
        )
    }
}
