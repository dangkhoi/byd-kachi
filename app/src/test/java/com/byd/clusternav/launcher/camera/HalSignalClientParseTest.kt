package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Khoá hợp đồng dây giữa helper HAL (uid shell, `hal-helper/KachiHalMain.java`) và [HalSignalClient].
 *
 * Hai đầu nằm ở HAI tiến trình và HAI ngôn ngữ, không có kiểu dùng chung nào ép chúng khớp nhau — nên dòng chữ
 * này chính là mặt tiếp giáp, và đây là chỗ duy nhất kiểm được nó off-car.
 */
class HalSignalClientParseTest {

    @Test
    fun `boc duoc topic va type tu dong giao thuc that`() {
        // Đúng byte mà KachiHalMain.line() sinh ra.
        assertEquals(
            HalSignalClient.TOPIC_ON to 4,
            HalSignalClient.parseLine("""{"topic":"light.onLightOn","type":4}"""),
        )
        assertEquals(
            HalSignalClient.TOPIC_OFF to 5,
            HalSignalClient.parseLine("""{"topic":"light.onLightOff","type":5}"""),
        )
    }

    @Test
    fun `dong rac tra null`() {
        assertNull(HalSignalClient.parseLine(null))
        assertNull(HalSignalClient.parseLine(""))
        assertNull(HalSignalClient.parseLine("hello world"))
        assertNull(HalSignalClient.parseLine("KachiHal: đang phục vụ 127.0.0.1:19322"))
        assertNull(HalSignalClient.parseLine("""{"topic":"light.onLightOn"}"""))          // thiếu type
        assertNull(HalSignalClient.parseLine("""{"type":4}"""))                          // thiếu topic
        assertNull(HalSignalClient.parseLine("""{"topic":"","type":4}"""))                // topic rỗng
        assertNull(HalSignalClient.parseLine("""{"topic":"light.onLightOn","type":"x"}""")) // type không phải số
    }

    @Test
    fun `chiu duoc khoang trang, thu tu truong va truong them vao`() {
        // Ba ca này là lý do parseLine dùng hai phép tìm RỜI thay vì một khuôn cả dòng: giao thức thêm trường
        // hoặc đổi thứ tự thì client vẫn đọc được, thay vì mù im lặng.
        assertEquals(
            HalSignalClient.TOPIC_ON to 4,
            HalSignalClient.parseLine("""  { "topic" : "light.onLightOn" , "type" : 4 }  """),
        )
        assertEquals(
            HalSignalClient.TOPIC_ON to 5,
            HalSignalClient.parseLine("""{"type":5,"topic":"light.onLightOn"}"""),
        )
        assertEquals(
            HalSignalClient.TOPIC_OFF to 4,
            HalSignalClient.parseLine("""{"topic":"light.onLightOff","type":4,"ts":12345}"""),
        )
    }

    @Test
    fun `type la hang dung phia trai va phai`() {
        // Thang type do framework BYD quy định ([ĐO] 4=trái, 5=phải) — ghim lại để không ai đổi nhầm một đầu.
        assertEquals(4, HalSignalClient.TYPE_LEFT)
        assertEquals(5, HalSignalClient.TYPE_RIGHT)
    }
}
