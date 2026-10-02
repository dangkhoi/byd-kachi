package com.byd.clusternav.launcher.behind

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.byd.clusternav.launcher.DefaultHome
import com.byd.clusternav.launcher.KachiPerf
import com.byd.clusternav.modules.navaccess.AccessibilityRebind
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * ═══ BEHIND-HOME — bên THI HÀNH: luồng + mutex + phần Android của chuỗi ═════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R0 · §4.2 (A4). Chuỗi và mọi quyết định ở `:core`
 * ([BehindHomeSequence] · [BehindHomePlan]); lớp này chỉ:
 *  1. chạy chuỗi trên MỘT luồng nền của cả tiến trình (`kachi-behind`) ⇒ hai lượt không bao giờ chồng lệnh (R-nf4);
 *  2. cấp [BehindHomeSequence.AnchorPort] thật — mở/gỡ [BehindAnchorActivity] bằng API trong tiến trình, hỏi
 *     `PackageManager` xem có phải app hệ thống;
 *  3. ghi MỘT dòng `KachiBehind` mỗi lượt + đếm [KachiPerf.Counter.BEHIND_FAIL] khi lùi về O1 (R0.5).
 *
 * Không có kênh ⇒ không lệnh nào (kết quả [BehindHomeSequence.Result.KEPT_UNDER]); mọi lệnh đi qua kênh hiện có nên
 * vẫn chịu cổng thi hành READY-AT-HOME (`ShellReadiness.admit`).
 */
class BehindHomeRunner(ctx: Context, private val shell: () -> ((String) -> String)?) {

    private val app = ctx.applicationContext
    private val ui = Handler(Looper.getMainLooper())

    /**
     * R0.1 — đẩy [a] (dưới [b] trong màn ảo [vd]) ra sau màn nhà. [done] chạy trên luồng chính. Bên gọi đã mở [b] vào
     * màn ảo TRƯỚC (đường mở ô sẵn có) — đó là bước 1 của chuỗi đã đo.
     */
    fun evict(vd: Int, a: String, b: String, done: (BehindHomeSequence.Outcome) -> Unit = {}) =
        submit("evict vd=$vd A=$a B=$b", done) { it.evict(vd, a, b) }

    /**
     * R0.3 — chạy [x] phía sau màn nhà qua một ô đang sống ([stages], chọn bằng [BehindHomePlan.stagingSlot]). Không có
     * ô sống ⇒ `null` trả ngay, 0 lệnh (§4.2.4) — bên gọi nói lý do `kachi_sc_no_stage`. Điểm gọi: lối tắt kiểu *Chạy
     * ngầm* (U5) và chuyến lên xe (R1/R2) — nhóm B/C của spec.
     */
    fun startBehind(x: String, stages: List<BehindHomePlan.Stage>, done: (BehindHomeSequence.Outcome) -> Unit = {}): BehindHomePlan.Stage? {
        val stage = BehindHomePlan.stagingSlot(stages, x) ?: return null
        submit("behind X=$x", done) { it.startBehind(x, stage) }
        return stage
    }

    private fun submit(what: String, done: (BehindHomeSequence.Outcome) -> Unit, body: (BehindHomeSequence) -> BehindHomeSequence.Outcome) {
        execute(what) {
            val out = try {
                runOnce(what, body)
            } catch (e: IOException) {
                failed(what, e)
            } catch (e: RuntimeException) {
                failed(what, e)
            }
            Log.i(TAG, out.line)
            // ALREADY_RUNNING không phải hỏng (app đang sống ⇒ cố ý 0 lệnh) — không đếm vào bộ đếm lùi O1.
            if (!out.moved && out.result != BehindHomeSequence.Result.ALREADY_RUNNING) KachiPerf.add(KachiPerf.Counter.BEHIND_FAIL)
            ui.post { done(out) }
        }
    }

    /**
     * Kênh ném giữa chuỗi (dadb đứt, cổng thi hành từ chối) ⇒ KHÔNG để lọt ra luồng nền (lọt = sập HOME = crash-loop
     * mỗi lần tắt máy — cùng lý do `EarlyShellChannel.guarded`). Gỡ giữ chỗ (có thể đã dựng) rồi lùi O1.
     */
    private fun failed(what: String, e: Exception): BehindHomeSequence.Outcome {
        Log.e(TAG, "$what: chuỗi lỗi giữa chừng — lùi O1, gỡ giữ chỗ", e)
        val gone = try { AndroidAnchor(app).removeAll() } catch (re: RuntimeException) { Log.w(TAG, "gỡ giữ chỗ hỏng", re); -1 }
        return BehindHomeSequence.Outcome(BehindHomeSequence.Result.KEPT_UNDER, "$what -> KEPT_UNDER (${e.javaClass.simpleName}) anchors=$gone")
    }

    private fun runOnce(what: String, body: (BehindHomeSequence) -> BehindHomeSequence.Outcome): BehindHomeSequence.Outcome {
        // Dòng kết quả là NHẬT KÝ (in qua `Log.i` ở [submit]) — viết không dấu để bài canh i18n không coi là chữ trên màn.
        disabledReason?.let { return BehindHomeSequence.Outcome(BehindHomeSequence.Result.KEPT_UNDER, "$what -> disabled ($it), 0 cmd") }
        val sh = shell() ?: return BehindHomeSequence.Outcome(BehindHomeSequence.Result.KEPT_UNDER, "$what -> no channel, 0 cmd")
        val seq = BehindHomeSequence(
            sh, AndroidAnchor(app), app.packageName, AccessibilityRebind.GO_HOME_UNLESS_CAMERA,
            homeComps = DefaultHome.shownComponents(app),
        )
        val out = body(seq)
        if (out.result == BehindHomeSequence.Result.ANCHOR_IN_FRONT) disable("anchor-in-front")
        return out
    }

    /** Phần Android của chuỗi — xem KDoc [BehindHomeSequence.AnchorPort]. */
    private class AndroidAnchor(private val ctx: Context) : BehindHomeSequence.AnchorPort {
        private val cn = ComponentName(ctx, BehindAnchorActivity::class.java)
        override val component: String = cn.flattenToString()

        override fun start(): Boolean = runCatching {
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(BehindHomePlan.MAIN_DISPLAY).toBundle()
            opts.putBoolean(BehindHomePlan.AVOID_MOVE_TO_FRONT, true)
            val i = Intent().setComponent(cn).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION,
            ).putExtra(BehindAnchorActivity.EXTRA_PID, Process.myPid())
            ctx.startActivity(i, opts)
            true
        }.onFailure { Log.w(TAG, "mở giữ chỗ hỏng", it) }.getOrDefault(false)

        override fun removeAll(): Int {
            val am = ctx.getSystemService(ActivityManager::class.java) ?: return 0
            var n = 0
            for (t in runCatching { am.appTasks }.getOrDefault(emptyList())) {
                val base = runCatching { t.taskInfo?.baseIntent?.component }.getOrNull()
                if (base == cn && runCatching { t.finishAndRemoveTask() }.isSuccess) n++
            }
            return n
        }

        override fun isSystemApp(pkg: String): Boolean = runCatching {
            ctx.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(true)

        override fun markBehind(taskId: Int, pkg: String): Boolean = BehindMarksStore(ctx).add(taskId, pkg)

        override fun unmarkBehind(taskId: Int) = BehindMarksStore(ctx).remove(taskId)
    }

    companion object {
        const val TAG = "KachiBehind"

        /** MỘT luồng cho cả tiến trình = mutex BEHIND-HOME (R-nf4). Daemon: không giữ tiến trình sống. */
        private val EXEC = Executors.newSingleThreadExecutor { r -> Thread(r, "kachi-behind").apply { isDaemon = true } }

        /**
         * Lý do BEHIND-HOME bị TẮT trong tiến trình này — chỉ do một PHÉP ĐO đặt (giữ chỗ bị ROM chạy thật / lên trước
         * màn nhà). Cờ RAM này chỉ làm Kachi BỚT việc (lùi O1), không bao giờ quyết một lệnh đổi cửa sổ (CLAUDE.md §5).
         */
        @Volatile var disabledReason: String? = null
            private set

        fun disable(reason: String) {
            if (disabledReason == null) disabledReason = reason
        }

        /** Đẩy [task] lên luồng `kachi-behind` (mutex BEHIND-HOME). Hàng đợi từ chối ⇒ log, không ném. */
        internal fun execute(what: String, task: () -> Unit) {
            try {
                EXEC.execute(task)
            } catch (e: RejectedExecutionException) {
                Log.w(TAG, "$what: hàng đợi từ chối", e)
            }
        }
    }
}
