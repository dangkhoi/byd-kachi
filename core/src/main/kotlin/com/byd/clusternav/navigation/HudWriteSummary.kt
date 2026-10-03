package com.byd.clusternav.navigation

/**
 * ═══ FIX286 R-HUD (S4) — tóm tắt chuỗi rc của một lần ghi HAL dẫn đường (thuần, `:core`) ═══════════════════════════
 *
 * `BydHal.writeNavFrame` / `clearNavFrame` trả chuỗi ` TÊN=giá-trị` nối nhau (vd ` INSTRUMENT_SEND_NAVI_STATUS_SET=0
 * PATHNAME=skip sdk.simple!=SecurityException`) — dài ~300 ký tự, không đặt vừa một dòng trạng thái. Dòng *HUD* ở
 * *Cài đặt › Dẫn đường* chỉ cần ba con số để anh em chụp màn: bao nhiêu mục HAL nhận (`0`), bao nhiêu bị từ chối /
 * ném lỗi, bao nhiêu bỏ qua vì đã bị từ chối trước đó (`skip`, cache của `BydHal`).
 */
object HudWriteSummary {

    data class Counts(val ok: Int, val rejected: Int, val skipped: Int, val noDevice: Boolean)

    fun of(rc: String?): Counts {
        val s = rc.orEmpty()
        if (s.contains(NO_DEVICE)) return Counts(0, 0, 0, noDevice = true)
        var ok = 0
        var rejected = 0
        var skipped = 0
        TOKEN.findAll(s).forEach { m ->
            val failed = m.groupValues[2] == "!="
            val v = m.groupValues[3]
            when {
                failed -> rejected++
                v == "skip" -> skipped++
                v == "0" -> ok++
                else -> rejected++
            }
        }
        return Counts(ok, rejected, skipped, noDevice = false)
    }

    private const val NO_DEVICE = "InstrumentDevice null"
    private val TOKEN = Regex("""([A-Za-z0-9_.]+)(!?=)(\S+)""")
}
