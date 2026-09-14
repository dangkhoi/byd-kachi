package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V2 pha NGHE · BẢNG MÔ HÌNH sherpa — GHIM SỐ + GIẤY PHÉP + FAIL-SAFE CỔNG ════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html`. Khoá lại những thứ **hỏng-thì-không-ai-thấy**: sha256 bị sửa
 * nhầm cho gói giả đi lọt; một mô hình chưa ghim mà vẫn cho tải; giấy phép NC/ND lọt vào bản phát hành; và bẫy
 * `bpeVocab` (§Impl Log) — nếu ai đổi lại thành `bpe.model` thô thì biasing chết im lặng. Không cần xe.
 */
class SherpaModelCatalogTest {

    // ══ (1) Mô hình mặc định tải được + đã ghim đủ ════════════════════════════════════════════════════════

    @Test
    fun `default model is the Apache Zipformer-vi and is fully pinned`() {
        val m = SherpaModelCatalog.byId(SherpaModelCatalog.DEFAULT_ID)
        assertEquals("zipformer-vi-2025-04-20", m.id)
        assertEquals("Apache-2.0", m.license)
        assertTrue(m.downloadable, "mô hình mặc định PHẢI tải được (đã ghim mọi tệp)")
        m.files.forEach { f ->
            assertTrue(f.pinned, "tệp ${f.name} phải ghim cả sha256 lẫn size")
            assertEquals(64, f.sha256.length, "sha256 của ${f.name} phải đủ 64 hex")
            assertTrue(f.url.startsWith("https://"), "URL của ${f.name} phải HTTPS")
        }
    }

    @Test
    fun `default model names its four transducer files and total bytes sum them`() {
        val m = SherpaModelCatalog.ZIPFORMER_VI
        listOf(m.encoder, m.decoder, m.joiner, m.tokens).forEach { name ->
            assertNotNull(m.file(name), "thiếu tệp thành phần $name trong danh sách tải")
        }
        assertEquals(261_057_692L + 5_165_084L + 4_104_465L + 25_847L, m.totalBytes)
    }

    // ══ (2) BẪY bpeVocab — KHÔNG bao giờ là `bpe.model` thô, KHÔNG nằm trong danh sách TẢI ═════════════════

    @Test
    fun `bpeVocab is a shipped asset table, never the raw sentencepiece model`() {
        SherpaModelCatalog.ALL.forEach { m ->
            assertFalse(
                m.bpeVocab.endsWith(".model"),
                "${m.id}: bpeVocab KHÔNG được là bpe.model thô — sherpa 1.13.8 từ chối ⇒ biasing chết im lặng",
            )
            assertTrue(m.bpeVocab.endsWith(".bpe_vocab.txt"), "${m.id}: bpeVocab phải là bảng piece+score .bpe_vocab.txt")
            // Bảng BPE đóng theo APK làm asset, KHÔNG tải qua mạng ⇒ không nằm trong files.
            assertTrue(m.files.none { it.name == m.bpeVocab }, "${m.id}: bpeVocab là ASSET, không được nằm trong danh sách tải")
        }
    }

    // ══ (3) Mô hình có cổng (hataphu) — FAIL-SAFE: chưa mirror thì KHÔNG tải ══════════════════════════════

    @Test
    fun `gated hataphu model is declared MIT but not downloadable until mirrored`() {
        val m = SherpaModelCatalog.HATAPHU_VI
        assertEquals("MIT", m.license)
        assertFalse(m.downloadable, "mô hình có cổng CHƯA ghim sha256 ⇒ phải bị từ chối tải (fail-safe)")
        assertTrue(m.files.none { it.pinned }, "mọi tệp hataphu phải để trống pin cho tới khi mirror")
    }

    // ══ (4) Tra id lạ / rỗng quay về mặc định (pref cũ / hỏng không được làm câm tính năng) ════════════════

    @Test
    fun `byId falls back to default for unknown or blank id`() {
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI, SherpaModelCatalog.byId(null))
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI, SherpaModelCatalog.byId(""))
        assertEquals(SherpaModelCatalog.ZIPFORMER_VI, SherpaModelCatalog.byId("khong-ton-tai"))
        assertEquals(SherpaModelCatalog.HATAPHU_VI, SherpaModelCatalog.byId("zipformer-hataphu-vi"))
    }

    @Test
    fun `catalog lists both models and no NC or ND licensed model is present`() {
        assertEquals(2, SherpaModelCatalog.ALL.size)
        assertTrue(SherpaModelCatalog.ALL.none { it.license.contains("NC", ignoreCase = true) })
        assertTrue(SherpaModelCatalog.ALL.none { it.license.contains("ND", ignoreCase = true) })
    }

    @Test
    fun `directory is namespaced under sherpa and distinct per model`() {
        assertEquals("sherpa/zipformer-vi-2025-04-20", SherpaModelCatalog.ZIPFORMER_VI.dir)
        assertEquals("sherpa/zipformer-hataphu-vi", SherpaModelCatalog.HATAPHU_VI.dir)
    }

    @Test
    fun `default model uses modified_beam_search so biasing is available`() {
        assertEquals("modified_beam_search", SherpaModelCatalog.ZIPFORMER_VI.decodingMethod)
        assertEquals(3.0f, SherpaModelCatalog.HOTWORDS_SCORE)
        assertEquals("bpe", SherpaModelCatalog.MODELING_UNIT)
    }
}
