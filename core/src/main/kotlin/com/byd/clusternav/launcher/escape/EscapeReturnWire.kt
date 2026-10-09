package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.ShellAppLauncher

/**
 * 2.98 · R18 — bảng Kachi gửi daemon: [api] = bảng tên hàm đời ROM (`null` = TẮT: daemon gỡ bộ nghe, không dời gì) ·
 * [slots] = {màn ảo ô → gói đang hiện ở ô đó}. Daemon KHÔNG đọc được màn `FLAG_PRIVATE` của Kachi từ uid 2000
 * ([ĐO máy ảo 09/10] `DisplayManagerGlobal.getDisplayInfo` trả rỗng) ⇒ Kachi là nguồn sự thật duy nhất của bảng này.
 */
data class EscapeReturnConfig(val api: EscapeReturnApi?, val slots: Map<Int, String>) {
    val enabled: Boolean get() = api != null

    companion object {
        val OFF = EscapeReturnConfig(null, emptyMap())
    }
}

/** Một dòng daemon báo về Kachi (log + cầu chì bền). */
sealed interface EscapeReport {
    /** Bộ nghe đã đăng ký với bảng [api]. */
    data class Ready(val api: String) : EscapeReport

    /** Bộ nghe không bật / đã gỡ — [why] (bảng tắt · sai đời API · lỗi phản chiếu). */
    data class Off(val why: String) : EscapeReport

    /** Đã dời task [task] (stack [stack]) của [pkg] về màn ảo [vd] trong [ms] ms tính từ lúc nhận sự kiện. */
    data class Moved(val pkg: String, val task: Int, val stack: Int, val vd: Int, val ms: Long) : EscapeReport

    /** Sự kiện thoát ô không dời — [why] = một giá trị [EscapeReturnGuard.Why] hoặc lý do cơ chế. */
    data class Skipped(val pkg: String?, val task: Int, val vd: Int, val why: String) : EscapeReport

    /** Cầu chì ngắt: [scope] gói [pkg] / tất cả; [persist] = Kachi phải ghi bền (dấu hiệu NPE 08-01 / task mất). */
    data class Tripped(val scope: Scope, val pkg: String?, val why: String) : EscapeReport {
        val persist: Boolean get() = scope == Scope.PERSIST
    }

    enum class Scope { PKG, ALL, PERSIST }
}

/**
 * ═══ 2.98 · R18 — dây giữa Kachi và daemon cho "app thoát ô ⇒ stack về lại màn ảo ô" (thuần, `:core`) ═══════════════════
 *
 * Kachi → daemon: thân UTF-8 của khung điều khiển ([com.byd.clusternav.system.inputd.InputWireProtocol.controlFrame]):
 * ```
 * kachi-esc 1
 * api a10r47
 * vd 5 com.waze
 * ```
 * Daemon → Kachi: một dòng `esc …` mỗi sự kiện trên CHÍNH socket đó (chiều ngược, đọc bởi `InputDaemonClient`).
 *
 * Đến từ kênh ⇒ giải mã khắt khe, không ném: dòng lạ bỏ qua, gói phải khớp [ShellAppLauncher.PKG], màn ảo ≥ 1, tối đa
 * [MAX_SLOTS] mục, bảng api chỉ tra theo id ([EscapeReturnApi.byId]).
 */
object EscapeReturnWire {

    const val HEADER = "kachi-esc 1"
    const val MAX_SLOTS = 16

    /** Trần chữ của một lý do trong báo cáo (log, không phải dữ liệu). */
    private const val WHY_MAX = 120

    fun encodeConfig(c: EscapeReturnConfig): String = buildString {
        append(HEADER).append('\n')
        c.api?.let { append("api ").append(it.id).append('\n') }
        for ((vd, pkg) in c.slots.entries.sortedBy { it.key }.take(MAX_SLOTS)) append("vd ").append(vd).append(' ').append(pkg).append('\n')
    }

    /** `null` = không phải bảng R18 (đầu sai) ⇒ daemon bỏ qua khung, giữ bảng cũ. */
    fun decodeConfig(text: String): EscapeReturnConfig? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull() != HEADER) return null
        var api: EscapeReturnApi? = null
        val slots = LinkedHashMap<Int, String>()
        for (line in lines.drop(1)) {
            val f = line.split(' ')
            when {
                f.size == 2 && f[0] == "api" -> api = EscapeReturnApi.byId(f[1])
                f.size == 3 && f[0] == "vd" -> {
                    val vd = f[1].toIntOrNull()?.takeIf { it >= 1 } ?: continue
                    val pkg = f[2].takeIf { it.matches(ShellAppLauncher.PKG) } ?: continue
                    if (slots.size < MAX_SLOTS) slots[vd] = pkg
                }
            }
        }
        return EscapeReturnConfig(api, slots)
    }

    fun encodeReport(r: EscapeReport): String = when (r) {
        is EscapeReport.Ready -> "esc ready ${tok(r.api)}"
        is EscapeReport.Off -> "esc off ${tok(r.why)}"
        is EscapeReport.Moved -> "esc moved ${r.pkg} ${r.task} ${r.stack} ${r.vd} ${r.ms}"
        is EscapeReport.Skipped -> "esc skip ${r.pkg ?: "-"} ${r.task} ${r.vd} ${tok(r.why)}"
        is EscapeReport.Tripped -> "esc trip ${r.scope.name.lowercase()} ${r.pkg ?: "-"} ${tok(r.why)}"
    }

    /** `null` = dòng không phải báo cáo R18 (bỏ qua). */
    fun parseReport(line: String): EscapeReport? {
        val f = line.trim().split(' ')
        if (f.size < 3 || f[0] != "esc") return null
        fun pkg(s: String): String? = s.takeIf { it != "-" && it.matches(ShellAppLauncher.PKG) }
        return when (f[1]) {
            "ready" -> EscapeReport.Ready(f[2])
            "off" -> EscapeReport.Off(f[2])
            "moved" -> if (f.size == 7) {
                val p = pkg(f[2]) ?: return null
                EscapeReport.Moved(p, f[3].toIntOrNull() ?: return null, f[4].toIntOrNull() ?: return null,
                    f[5].toIntOrNull() ?: return null, f[6].toLongOrNull() ?: return null)
            } else null
            "skip" -> if (f.size == 6) {
                EscapeReport.Skipped(pkg(f[2]), f[3].toIntOrNull() ?: return null, f[4].toIntOrNull() ?: return null, f[5])
            } else null
            "trip" -> if (f.size == 5) {
                val scope = EscapeReport.Scope.entries.firstOrNull { it.name.equals(f[2], ignoreCase = true) } ?: return null
                EscapeReport.Tripped(scope, pkg(f[3]), f[4])
            } else null
            else -> null
        }
    }

    /** Một token không khoảng trắng, có trần — lý do đi vào một trường của dòng báo cáo. */
    fun tok(s: String): String = s.trim().replace(Regex("\\s+"), "_").ifEmpty { "?" }.take(WHY_MAX)
}
