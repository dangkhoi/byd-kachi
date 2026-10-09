package com.byd.clusternav.launcher.escape

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.byd.clusternav.modules.clustercast.ClusterProfile
import com.byd.clusternav.modules.clustercast.StackParse
import com.byd.clusternav.system.FreeformSeedStore
import java.io.IOException

/**
 * ═══ 2.98 · R18 — đường TRẢ một lần cho máy đã chạy bản R7 (SUPERSEDED) ════════════════════════════════════════════════════
 *
 * Quyết định thuần ở [LegacyFreeformUndo] (`:core`). Chạy MỘT lượt mỗi tiến trình, lúc kênh shell vừa lên
 * (`LauncherWindows.sweepFloating("shell-up")`), trên luồng nền của bên gọi. 0 lệnh khi không có dấu `kachi_slot_escape` (mọi máy
 * chưa từng chạy R7); có dấu ⇒ 1 `am stack list` + một lệnh mã 89 mode 1 toTop 0 cho mỗi task freeform cần trả.
 */
internal object LegacyFreeformCleanup {

    private const val TAG = SlotEscapeReturn.TAG

    @Volatile private var done = false

    @SuppressLint("ApplySharedPref")   // commit() đồng bộ: dấu chỉ được xoá SAU khi task đã trả xong, và phải nằm trên đĩa ngay
    fun runOnce(ctx: Context, sh: (String) -> String) {
        if (done) return
        val prefs = ctx.applicationContext.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)
        val markers = LegacyFreeformUndo.decode(prefs.getString(LegacyFreeformUndo.KEY, null))
        if (markers.isEmpty()) { done = true; return }
        val codes = ClusterProfile.resolveCached(ctx).taskBinder?.takeIf { it.usableOn(Build.VERSION.SDK_INT) }
        if (codes == null) {
            // R7 chỉ từng bật khi có bảng mã đúng đời API ⇒ dấu ở đây là dấu lạ (sao lưu/khôi phục chéo máy): không lệnh, xoá.
            prefs.edit().remove(LegacyFreeformUndo.KEY).commit()
            Log.i(TAG, "dọn R7: ${markers.size} dấu nhưng đời ROM không có mã 89 ⇒ xoá dấu, 0 lệnh")
            done = true
            return
        }
        val out = shellOrNull(sh, LIST_CMD) ?: run {
            Log.w(TAG, "dọn R7: đọc stack hỏng ⇒ lượt sau (tiến trình kế)")
            return
        }
        val steps = LegacyFreeformUndo.plan(markers, StackParse.parse(out)) { installed(ctx, it) }
        val keep = ArrayList<LegacyFreeformUndo.Marker>()
        val undone = HashMap<String, Boolean>()
        for (s in steps) {
            when (s) {
                is LegacyFreeformUndo.Step.Undo -> {
                    val ok = s.tasks.all { t ->
                        TaskBinderCodes.parseOk(shellOrNull(sh, codes.modeCmd(t, TaskBinderCodes.MODE_FULLSCREEN, toTop = false)).orEmpty())
                    }
                    if (!ok) keep += s.marker
                    undone[s.marker.pkg] = ok
                }
                is LegacyFreeformUndo.Step.Forget -> Unit
                is LegacyFreeformUndo.Step.Keep -> keep += s.marker
            }
        }
        val e = prefs.edit()
        if (keep.isEmpty()) e.remove(LegacyFreeformUndo.KEY) else e.putString(LegacyFreeformUndo.KEY, LegacyFreeformUndo.encode(keep))
        e.commit()
        Log.i(TAG, "dọn R7: " + steps.joinToString(" · ") { s ->
            when (s) {
                is LegacyFreeformUndo.Step.Undo ->
                    "${s.marker.pkg} task ${s.tasks} ⇒ toàn màn ${if (undone[s.marker.pkg] == true) "OK" else "HỎNG (giữ dấu)"}"
                is LegacyFreeformUndo.Step.Forget -> "${s.marker.pkg} đã gỡ ⇒ xoá dấu"
                is LegacyFreeformUndo.Step.Keep -> "${s.marker.pkg} chưa có task freeform ⇒ giữ dấu"
            }
        })
        done = true
    }

    /** Lỗi kênh (dadb) / lệnh ⇒ `null` + một dòng log; dấu còn ⇒ tiến trình sau làm tiếp. */
    private fun shellOrNull(sh: (String) -> String, cmd: String): String? = try {
        sh(cmd)
    } catch (e: IOException) {
        Log.w(TAG, "dọn R7: '$cmd' hỏng kênh: ${e.javaClass.simpleName}"); null
    } catch (e: RuntimeException) {
        Log.w(TAG, "dọn R7: '$cmd' hỏng: ${e.javaClass.simpleName}: ${e.message}"); null
    }

    private fun installed(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private const val LIST_CMD = "am stack list"
}
