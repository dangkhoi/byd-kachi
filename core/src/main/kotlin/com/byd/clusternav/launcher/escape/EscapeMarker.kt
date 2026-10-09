package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.ShellAppLauncher

/**
 * ═══ 2.98 · R7 — DẤU BỀN "Kachi đã đổi app [pkg] sang freeform" (thuần, `:core`) ═══════════════════════════════════════
 *
 * CLAUDE.md §5: freeform là state đổi NGOÀI tiến trình và sống dai hơn nó — [ĐO máy ảo 09/10, evidence §3] Android nhớ chế
 * độ freeform THEO COMPONENT (`TaskLaunchParamsModifier`, `launch_params`), qua cả khởi động lại, chỉ xoá khi task hiện toàn
 * màn một lần (mã 89 mode 1). Nên dấu được ghi TRƯỚC lệnh đổi chế độ và chỉ xoá khi đã trả về toàn màn (hoặc gói đã gỡ):
 * tiến trình chết giữa chừng ⇒ lượt quét lúc khởi động thấy dấu ⇒ nhận lại hoặc trả.
 *
 * [task] = task id lúc nhận (đổi sau khởi động lại — bên đọc tìm lại theo [pkg]); [rect] = khung ô trên display 0 lúc nhận
 * (so với khung ô hiện tại để biết có phải kéo lại không).
 */
data class EscapeMarker(val pkg: String, val slot: Int, val task: Int, val rect: PxRect)

/** Mã hoá/giải mã danh sách dấu cho SharedPreferences — `pkg|slot|task|l,t,r,b` nối bằng `;`. */
object EscapeMarkers {

    /** Trần số dấu (mỗi gói một dấu; số ô có hạn) — quá trần bỏ dấu cũ nhất. */
    const val MAX = 8

    fun encode(list: List<EscapeMarker>): String =
        list.takeLast(MAX).joinToString(";") { m ->
            "${m.pkg}|${m.slot}|${m.task}|${m.rect.left},${m.rect.top},${m.rect.right},${m.rect.bottom}"
        }

    /** Chuỗi lạ ⇒ bỏ mục, không ném (đến từ đĩa). Một gói xuất hiện hai lần ⇒ giữ mục sau. */
    fun decode(raw: String?): List<EscapeMarker> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = LinkedHashMap<String, EscapeMarker>()
        for (item in raw.split(';')) {
            val f = item.trim().split('|')
            if (f.size != 4) continue
            val pkg = f[0].trim()
            if (!pkg.matches(ShellAppLauncher.PKG)) continue
            val slot = f[1].trim().toIntOrNull()?.takeIf { it >= 0 } ?: continue
            val task = f[2].trim().toIntOrNull()?.takeIf { it > 0 } ?: continue
            val r = f[3].split(',').mapNotNull { it.trim().toIntOrNull() }
            if (r.size != 4) continue
            val rect = PxRect(r[0], r[1], r[2], r[3]).takeUnless { it.isEmpty } ?: continue
            out.remove(pkg)
            out[pkg] = EscapeMarker(pkg, slot, task, rect)
        }
        return out.values.toList()
    }

    /** Thêm/thay dấu của cùng gói (mới nhất ở cuối). */
    fun upsert(list: List<EscapeMarker>, m: EscapeMarker): List<EscapeMarker> = (list.filter { it.pkg != m.pkg } + m).takeLast(MAX)

    fun remove(list: List<EscapeMarker>, pkg: String): List<EscapeMarker> = list.filter { it.pkg != pkg }
}
