package com.byd.clusternav.perf

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R4 + R6 — dây nối của các sửa hiệu năng dài hạn (docs/diagnostics/perf-inventory-2026-10-08.md). Luật thuần ở
 * `:core` `SlotVdNameTest`; ở đây khoá call site thật (CLAUDE.md §8).
 */
class PerfR6WiringContractTest {

    private fun code(p: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$p")

    /** R4 [ĐO máy ảo 08/10]: tên có dấu thời gian ⇒ +124 B `display_settings.xml` mỗi lần dựng ô, vĩnh viễn. */
    @Test
    fun `man ao o dat ten on dinh qua SlotVdName, khong dau thoi gian`() {
        val host = code("launcher/VdAppHost.kt")
        assertTrue(host.contains("SlotVdName.pick(slot, SlotVdOwner.liveNames())"))
        assertFalse(host.contains("kachi-slot-\$slot-\${System.currentTimeMillis()}"), "tên có dấu thời gian quay lại ⇒ tệp phình")
        // §6: khoá xoay (bug "YouTube co vào giữa") vẫn chạy y nguyên, cùng thứ tự.
        val lock = host.indexOf("wm set-user-rotation lock -d \$displayId 0")
        val fix = host.indexOf("wm set-fix-to-user-rotation -d \$displayId enabled")
        assertTrue(lock in 0 until fix, "hai lệnh khoá xoay giữ nguyên thứ tự")
        assertTrue(code("launcher/SlotVdOwner.kt").contains("fun liveNames(): Set<String>"))
    }

    /** R6 [ĐO log xe SL6 08/10]: 2 797 dòng `emit lane` / ~37 phút vì khoá log theo cửa sổ chạy chữ (đổi mỗi 700 ms). */
    @Test
    fun `emit lane log theo ten duong day du, khong theo cua so chay chu`() {
        val cb = code("ClusterBroadcaster.kt")
        assertTrue(cb.contains("val emitKey = \"\$emitIcon|\$emitSeg|\$lastCleanRoad\""))
        assertFalse(cb.contains("val emitKey = \"\$emitIcon|\$emitSeg|\$emitRoad\""))
    }

    /**
     * Soát hiệu năng 2.98 vs 2.97 [ĐO máy ảo 09/10, docs/diagnostics/perf-298-vs-297-2026-10-09.md]: `setText` cùng chữ mỗi nhịp 10 s
     * làm màn nhà vẽ lại 31 khung/5 phút, và khi R7 đang che thì 9 cửa sổ gương vẽ lại theo (310 khung/5 phút). Chỉ gán khi đổi.
     */
    @Test
    fun `dong ho thanh tren chi gan khi chu doi`() {
        val top = code("launcher/KachiTopStrip.kt")
        assertTrue(top.contains("if (clock.text?.toString() != it) clock.text = it"))
        assertTrue(top.contains("if (dateText.text?.toString() != it) dateText.text = it"))
        assertFalse(top.contains("clock.text = SimpleDateFormat("), "gán thẳng mỗi nhịp quay lại ⇒ màn nhà + gương vẽ lại mỗi 10 s")
    }
}
