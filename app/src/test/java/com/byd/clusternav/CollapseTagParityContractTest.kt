package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WIRING contract for the Option-B COLLAPSIBLE CARDS (task `ui-redesign-options`): each feature card is a
 * header that is always visible + a body that folds. Header↔body are matched by `android:tag`
 * (`toggle_X` / `body_X`) and looked up with `findViewWithTag` from `decorView`, NOT by `@+id` — precisely so
 * the change added NO new id to the 95-id sealed set. That escape hatch has a blind spot:
 * [LayoutVariantIdParityTest] only locks `@+id` parity across layout variants, so a tag edit in ONE variant
 * (e.g. renaming `toggle_seat` only in `layout-w960dp/`) slips past every existing test.
 *
 * WHY THIS MATTERS — and why it is only a P3, not the on-car crash [LayoutVariantIdParityTest] guards:
 * the folded (`GONE`) state is applied ONLY programmatically by `wireCollapse`; no `body_*` sets
 * `android:visibility="gone"` in XML (verified 2026-09-05). So a tag mismatch FAILS OPEN — `wireCollapse`
 * no-ops (its `?: return`) and the body stays at its XML default (visible/expandable); it can NOT strand a
 * body permanently hidden. The visible symptom is a card that refuses to collapse on one screen size —
 * cosmetic, not a crash. This test locks that boundary anyway (defense in depth: the moment a `body_*` ever
 * gains an XML `gone`, a tag mismatch WOULD strand it).
 *
 * Locks:
 *  • PARITY — both layout variants declare the SAME set of `toggle_*` / `body_*` / `sum_*` tags (both
 *    directions), the tag analog of [LayoutVariantIdParityTest] test 1.
 *  • CODE↔LAYOUT — every tag literal MainActivity looks up (`wireCollapse(header, body, …)`,
 *    `setSummary(tag, …)`, `findViewWithTag<…>("…")`) is DECLARED in every variant of `activity_main`, the
 *    tag analog of [LayoutVariantIdParityTest] test 2 (catches a tag renamed in code but not layout, or
 *    dropped from one variant).
 *  • PAIRING — every `toggle_X` has a matching `body_X` and vice-versa, so `wireCollapse` can bind each card.
 *
 * ── PHÉP THỬ LÀM-ĐỎ (P5.3): xoá / đổi tên một `android:tag="toggle_seat"` ở CHỈ `layout-w960dp/` ⇒ test
 * PARITY + CODE↔LAYOUT ĐỎ. Bỏ `body_nav` khỏi cả hai layout mà code vẫn gọi `wireCollapse(…, "body_nav", …)`
 * ⇒ CODE↔LAYOUT ĐỎ (PARITY vẫn xanh — đúng lý do cần cả hai vế, y như LayoutVariantIdParityTest).
 */
class CollapseTagParityContractTest {

    private val narrow: String by lazy { SourceRoots.text("src/main/res/layout/activity_main.xml") }
    private val wide: String by lazy { SourceRoots.text("src/main/res/layout-w960dp/activity_main.xml") }
    private val variants get() = listOf("narrow" to narrow, "wide" to wide)

    /** Tên tag gập được KHAI BÁO trong một file layout (`android:tag="toggle_/body_/sum_…"`). */
    private fun declaredCollapseTags(xml: String): Set<String> =
        DECLARE_TAG.findAll(xml).map { it.groupValues[1] }.filter { it.isCollapseTag() }.toSet()

    private fun String.isCollapseTag(): Boolean =
        startsWith("toggle_") || startsWith("body_") || startsWith("sum_")

    // ── 1 · PARITY: hai biến thể khai báo cùng tập tag gập ────────────────────
    @Test
    fun `both layout variants declare the same collapse tag set`() {
        val n = declaredCollapseTags(narrow)
        val w = declaredCollapseTags(wide)
        assertTrue(n.size >= 15, "quét hụt tag gập ở bản dọc (chỉ thấy ${n.size}) — regex hỏng?")
        assertEquals(
            emptySet<String>(), n - w,
            "tag gập có ở layout/ nhưng THIẾU ở layout-w960dp/ ⇒ card đó không gập được trên đầu xe (1280dp).",
        )
        assertEquals(
            emptySet<String>(), w - n,
            "tag gập có ở layout-w960dp/ nhưng THIẾU ở layout/ ⇒ card đó không gập được trên màn hẹp (dev).",
        )
    }

    // ── 2 · CODE↔LAYOUT: mọi tag code tra phải khai báo ở MỌI biến thể ────────
    @Test
    fun `every collapse tag MainActivity looks up is declared in both variants`() {
        val src = KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/MainActivity.kt"))

        val used = buildSet {
            WIRE_COLLAPSE.findAll(src).forEach { add(it.groupValues[1]); add(it.groupValues[2]) }
            SET_SUMMARY.findAll(src).forEach { add(it.groupValues[1]) }
            FIND_BY_TAG.findAll(src).forEach { add(it.groupValues[1]) }
        }.filter { it.isCollapseTag() }.toSortedSet()

        assertTrue(used.size >= 15, "quét hụt tag code tra (chỉ thấy ${used.size}: $used) — regex hỏng?")

        val problems = mutableListOf<String>()
        for ((name, xml) in variants) {
            val declared = declaredCollapseTags(xml)
            for (tag in used) if (tag !in declared) {
                problems += "$name: MainActivity tra tag \"$tag\" nhưng layout không khai báo ⇒ findViewWithTag=null → card không gập."
            }
        }
        assertEquals(emptyList<String>(), problems, problems.joinToString("\n"))
    }

    // ── 3 · PAIRING: mỗi toggle_X có body_X và ngược lại ──────────────────────
    @Test
    fun `every toggle tag has a matching body tag in both variants`() {
        for ((name, xml) in variants) {
            val tags = declaredCollapseTags(xml)
            val toggles = tags.filter { it.startsWith("toggle_") }.map { it.removePrefix("toggle_") }.toSet()
            val bodies = tags.filter { it.startsWith("body_") }.map { it.removePrefix("body_") }.toSet()
            assertEquals(
                emptySet<String>(), toggles - bodies,
                "$name: có toggle_X mà không có body_X ⇒ wireCollapse không có thân để gập: ${toggles - bodies}",
            )
            assertEquals(
                emptySet<String>(), bodies - toggles,
                "$name: có body_X mà không có toggle_X ⇒ thân không có header để mở/đóng: ${bodies - toggles}",
            )
        }
    }

    private companion object {
        val DECLARE_TAG = Regex("""android:tag="([A-Za-z0-9_]+)"""")
        val WIRE_COLLAPSE = Regex("""wireCollapse\(\s*"([A-Za-z0-9_]+)"\s*,\s*"([A-Za-z0-9_]+)"""")
        val SET_SUMMARY = Regex("""setSummary\(\s*"([A-Za-z0-9_]+)"""")
        val FIND_BY_TAG = Regex("""findViewWithTag<[^>]*>\(\s*"([A-Za-z0-9_]+)"""")
    }
}
