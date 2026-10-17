package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.12 · CAR3D-SEAT-ICON-OVERLAP — nối dây "icon ghế nhường chấm có việc" (Filament/View không chạy off-device). Owner 17/10:
 * *"2 icon sưởi, mát ghế nó đè lên kính trước, làm kính trước hơi khó nhấn"*. Phép hình học ở `:core` ([Car3dDeconflict],
 * `Car3dDeconflictTest`); bài này ĐỎ khi gỡ một mắt nối.
 */
class Car3dSeatIconWiringContractTest {

    private fun src(n: String) = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/$n"))
    private val widget = src("Car3dWidgetView.kt")
    private val taps = src("Car3dTaps.kt")
    private val hero = src("Car3dHeroLayer.kt")

    @Test
    fun `icon ghe doc vung cham cua CUNG khung - chieu cham truoc, dat icon sau`() {
        assertTrue(widget.contains("hero.zones = taps.zones"), "hai lớp chung một bộ đệm vùng")
        val frame = widget.substringAfter("if (r.render(t)) {").substringBefore("} else if")
        val t = frame.indexOf("taps.project("); val h = frame.indexOf("hero.project(")
        assertTrue(t in 0 until h, "chấm chiếu TRƯỚC (ghi vùng), icon đặt SAU (đọc vùng) — ngược lại thì icon né vùng của khung cũ")
    }

    @Test
    fun `cham co viec giu vi tri, ghi vung cung va mem, icon dat qua Car3dDeconflict`() {
        val project = taps.substringAfter("fun project(").substringBefore("fun logOnce(")
        assertFalse(taps.contains("freeY") || taps.contains("blocked"), "luật cũ chấm-né-icon đã gỡ")
        assertTrue(project.contains("zones.clear()") && project.contains("zones.addHard(") && project.contains("zones.addSoft("))
        assertTrue(project.indexOf("zones.clear()") < project.indexOf("zones.addHard("))
        val place = hero.substringAfter("fun project(").substringBefore("val show = Car3dPaintChoice.tyres")
        assertTrue(place.contains("Car3dDeconflict.place(") && place.contains("z.truncateHard(base)"), "cụm tạm bỏ khỏi vùng cứng sau khi đặt")
    }

    @Test
    fun `cu nhan trong o cham cua cham thuoc cham, khong thuoc icon ghe`() {
        val down = widget.substringAfter("override fun onInterceptTouchEvent(").substringBefore("return !barGesture")
        assertTrue(down.contains("heroGesture = hero.owns(ev.x, ev.y) && !taps.claims(ev.x, ev.y)"))
        assertTrue(taps.contains("Car3dDeconflict.claimed(x, y, zones)"))
    }
}
