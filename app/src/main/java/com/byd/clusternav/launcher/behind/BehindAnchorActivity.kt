package com.byd.clusternav.launcher.behind

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.byd.clusternav.launcher.KachiPerf

/**
 * ═══ BEHIND-HOME · activity GIỮ CHỖ — chỉ để có một stack S nằm đáy display 0 ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.2.5 (A3). [BehindHomeRunner] mở activity này kèm khoá
 * Bundle [BehindHomePlan.AVOID_MOVE_TO_FRONT]: `ActivityStarter` A10 `:1868-1870` đặt `mDoResume = false` ⇒ hệ dựng
 * task + stack MỚI dưới đáy display 0 mà KHÔNG chạy activity ([ĐO máy ảo] O3, `03`/`10`; anchor của chính Kachi: T-M1).
 * Task đó được thêm vào danh sách gần đây ngay lúc dựng (A10 `ActivityStarter.java:1731-1732`, nhánh `!mDoResume`) ⇒
 * `ActivityManager.getAppTasks()` của Kachi thấy nó và `finishAndRemoveTask()` gỡ được dù activity chưa từng `onCreate`.
 *
 * ## `onCreate` chạy = phép TỰ ĐO thất bại (R0.5a)
 * Khi mọi thứ đúng, [onCreate] KHÔNG BAO GIỜ chạy. Nó chạy ⇒ ROM bỏ qua khoá ⇒ stack giữ chỗ đã lên TRƯỚC màn nhà:
 * ghi một dòng `KachiBehind anchor-ran`, đếm [KachiPerf.Counter.BEHIND_ANCHOR_RAN], tắt BEHIND-HOME trong tiến trình
 * ([BehindHomeRunner.disable]) và tự gỡ task của mình. Không giao diện (`Theme.NoDisplay` đòi `finish()` trước
 * `onResume` — làm ngay ở đây).
 */
class BehindAnchorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Giữ chỗ MỒ CÔI của một tiến trình đã chết (Kachi bị giết giữa lượt, rồi hệ resume nó vì là stack kế tiếp)
        // KHÔNG phải bằng chứng ROM bỏ qua khoá ⇒ chỉ tự gỡ, không tắt tính năng. Phân biệt bằng pid của bên mở.
        if (intent?.getIntExtra(EXTRA_PID, -1) == Process.myPid()) {
            Log.w(BehindHomeRunner.TAG, "anchor-ran — ROM bỏ qua ${BehindHomePlan.AVOID_MOVE_TO_FRONT}; tắt BEHIND-HOME trong tiến trình")
            KachiPerf.add(KachiPerf.Counter.BEHIND_ANCHOR_RAN)
            BehindHomeRunner.disable("anchor-ran")
        } else {
            Log.i(BehindHomeRunner.TAG, "anchor mồ côi của tiến trình trước bị resume — tự gỡ, không tắt tính năng")
        }
        finishAndRemoveTask()
    }

    companion object {
        /** Pid của tiến trình MỞ giữ chỗ — `BehindHomeRunner` gắn vào Intent. */
        const val EXTRA_PID = "com.byd.clusternav.behind.PID"
    }
}
