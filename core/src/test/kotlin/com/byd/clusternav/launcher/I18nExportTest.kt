package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * ═══ BỘ SINH danh sách cặp cần dịch — `core/build/i18n/pairs.tsv` + `missing-<code>.tsv` ═════════════════════════
 *
 * Không phải bài canh: là CÔNG CỤ (spec `kachi-i18n-zh-th-ms.html` T1 *"công cụ xuất danh sách cặp cần dịch"*). Chạy:
 * `./gradlew :core:test --tests "*I18nExportTest*"`. Người dịch làm trên `pairs.tsv` (định dạng `vi⇥en⇥kind⇥where⇥cap`,
 * thoát như bảng dịch); `missing-<code>.tsv` là phần CÒN THIẾU của từng bảng, cùng định dạng.
 *
 * Chỉ khẳng định những gì luôn phải đúng với một tệp xuất hợp lệ (đầu bảng, số dòng, 5 ô) — nội dung đủ/thiếu là việc
 * của `I18nCoverageTest`.
 */
class I18nExportTest {

    @Test
    fun `xuat pairs tsv va missing tsv`() {
        val pairs = I18nPairs.pairs
        assertTrue(pairs.isNotEmpty(), "không có cặp nào ⇒ bộ quét/duyệt hỏng")
        val out = I18nPairs.outDir().resolve("pairs.tsv")
        Files.writeString(out, I18nPairs.tsv(pairs))
        val missing = I18nPairs.TRANSLATED.associateWith { I18nPairs.writeMissing(it) }

        val lines = Files.readAllLines(out)
        assertEquals(I18nPairs.HEADER, lines.first())
        assertEquals(pairs.size + 1, lines.size, "một cặp một dòng (ô có xuống dòng phải đã thoát)")
        assertTrue(lines.drop(1).all { it.split('\t').size == 5 }, "mỗi dòng đúng 5 ô (ô có tab phải đã thoát)")
        assertEquals(pairs.size, pairs.map { it.vi to it.en }.toSet().size, "khoá (vi, en) trùng trong tệp xuất")

        val byKind = pairs.groupingBy { it.kind.code }.eachCount().toSortedMap()
        println("[i18n] $out — ${pairs.size} cặp · theo loại $byKind")
        missing.forEach { (lang, path) -> println("[i18n] $path — thiếu ${I18nPairs.missing(lang).size} (${lang.code})") }
    }
}
