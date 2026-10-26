package com.byd.clusternav.launcher.telegram

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Cổng tin cũ cho lệnh xe (senior review 26/10 P0-1) — test bằng GIÁ TRỊ, không quét nguồn. */
class TelegramFreshnessTest {

    private val now = 1_800_000_000L
    private val ctl = TelegramCommand.Control("trunk", 1)

    @Test fun `lenh xe trong luot ton dong bi bo`() {
        assertFalse(TelegramFreshness.allow(isCarControl = true, fromBacklog = true, dateSec = now, nowSec = now),
            "lệnh xe trong lượt tồn đọng ⇒ bỏ, kể cả date 'mới' (đồng hồ xe có thể sai lúc vừa nổ máy)")
    }

    @Test fun `lenh xe go chu qua 2 phut bi bo`() {
        assertFalse(TelegramFreshness.allow(true, false, now - 3 * 60, now))
        assertTrue(TelegramFreshness.allow(true, false, now - 30, now))
        assertTrue(TelegramFreshness.allow(true, false, now + 60, now), "đồng hồ xe chậm hơn Telegram ⇒ vẫn mới")
        assertFalse(TelegramFreshness.allow(true, false, now + 10 * 60, now), "lệch 10 phút về tương lai ⇒ không tin")
    }

    @Test fun `nut bam khong co date - chi cong ton dong chan`() {
        assertTrue(TelegramFreshness.allow(true, false, null, now))
        assertFalse(TelegramFreshness.allow(true, true, null, now))
    }

    @Test fun `diem den va nhac DUOC ton dong (usecase gui tu nha)`() {
        val nav = TelegramCommand.Nav("Office")
        val music = TelegramCommand.Music("x", isQuery = true)
        listOf(nav, music).forEach { c ->
            val car = TelegramFreshness.isCarControl(c, null)
            assertFalse(car, "$c không ghi xe")
            assertTrue(TelegramFreshness.allow(car, fromBacklog = true, dateSec = now - 20 * 3600, nowSec = now))
        }
    }

    @Test fun `phan loai lenh xe dung`() {
        assertTrue(TelegramFreshness.isCarControl(ctl, null))
        listOf("ac", "seat", "misc").forEach {
            assertTrue(TelegramFreshness.isCarControl(TelegramCommand.Unknown(""), it), "nút domain $it ghi xe")
        }
        listOf("nav", "music", "menu", "page").forEach {
            assertFalse(TelegramFreshness.isCarControl(TelegramCommand.Unknown(""), it), "nút domain $it không ghi xe")
        }
    }
}
