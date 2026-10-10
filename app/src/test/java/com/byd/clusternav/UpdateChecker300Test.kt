package com.byd.clusternav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.0 (owner 10/10) — `UpdateChecker` đọc đúng tên tệp kênh `Kachi-3.0-release.apk` và chọn nó là bản cao nhất cạnh các bản 2.9x
 * (cùng [UpdateChecker.RE_APK] đã đăng từ 2.97 `1907bde` tới nay — đã `git show` so). Luật so ở `:core` `OtaVersion300Test`.
 */
class UpdateChecker300Test {

    @Test
    fun `ten tep 3_0 doc duoc va la ban cao nhat`() {
        val names = listOf("Kachi-2.98-release.apk", "Kachi-3.0-release.apk", "Kachi-2.99-release.apk", "Kachi-2.97-release.apk")
        val vers = names.mapNotNull { UpdateChecker.RE_APK.matchEntire(it)?.groupValues?.get(1) }
        assertEquals(listOf("2.98", "3.0", "2.99", "2.97"), vers)
        val best = vers.reduce { a, b -> if (UpdateChecker.cmp(b, a) > 0) b else a }
        assertEquals("3.0", best)
        assertTrue(UpdateChecker.cmp("3.0", "2.99") > 0)
    }
}
