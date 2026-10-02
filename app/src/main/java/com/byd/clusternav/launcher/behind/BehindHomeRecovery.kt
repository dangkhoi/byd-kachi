package com.byd.clusternav.launcher.behind

import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import com.byd.clusternav.AdbKeys
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.modules.clustercast.StackParse
import com.byd.clusternav.modules.navaccess.AccessibilityRebind

/**
 * ═══ BEHIND-HOME — đường TRẢ LẠI khi Kachi đã chết (CLAUDE.md §5) ═══════════════════════════════════════════════════
 *
 * [ĐO máy ảo 02/10, `impl-probe/e6`] giết Kachi khi một app đang nằm sau màn nhà ⇒ hệ resume CHÍNH app đó, che toàn
 * màn, màn nhà không tự lên lại (KDoc [BehindMarks]). Lượt này chạy ở đầu chuỗi SẴN (`EarlyShellChannel.readyChain`:
 * kênh UP + màn tương tác, mức TIẾN TRÌNH — chạy được cả khi màn chính chưa dựng lại), MỘT lần mỗi tiến trình
 * ([ranThisProcess]):
 *  - không có dấu ⇒ trả ngay, 0 lệnh shell (mọi lần thức của người chưa từng dùng lối tắt/giọng nói "vào ô");
 *  - có dấu ⇒ một `am stack list`; stack đỉnh display 0 đang hiện chỉ gồm task có dấu ⇒ `GO_HOME_UNLESS_CAMERA`
 *    (K12, byte 2.83 — không bao giờ đè camera lùi); tỉa dấu của task đã mất.
 *
 * Bốn câu §4 của lệnh duy nhất nó bắn = bốn câu của [AccessibilityRebind.GO_HOME_UNLESS_CAMERA] (display 0 · HOME mặc
 * định · chỉ đưa stack home lên · không state bền).
 */
object BehindHomeRecovery {

    /**
     * MỘT lượt mỗi TIẾN TRÌNH (không phải mỗi lần thức). Chỉ cái chết của Kachi làm app có dấu nổi lên che màn nhà (KDoc
     * lớp); cùng một tiến trình mà app có dấu ở trước màn nhà thì đó là NGƯỜI LÁI tự mở nó (ngăn kéo, lối tắt *Toàn màn*,
     * giọng nói…) — chuỗi SẴN chạy lại ở MỖI lần màn bật (`EarlyShellChannel.readyChain`), nên thiếu chốt này thì một lần
     * tắt/bật màn (nút tắt màn của xe) đẩy app người lái đang dùng ra sau màn nhà [SUY từ mã `EarlyShellChannel.kt`
     * `readyChain`: một lượt mỗi `lastScreenOnAt`]. Cờ chỉ làm Kachi BỚT việc (cùng luật `BehindHomeRunner.disabledReason`,
     * CLAUDE.md §5), và đặt TRƯỚC khi đọc dấu: tiến trình mới luôn có lượt đầu của nó.
     */
    private val ranThisProcess = AtomicBoolean(false)

    /**
     * Gọi từ chuỗi SẴN. Lượt thứ hai trở đi của cùng tiến trình ⇒ trả ngay ([ranThisProcess]). Không có dấu ⇒ trả ngay
     * (một lần đọc prefs). Có dấu ⇒ chạy trên luồng `kachi-behind` (mutex với lượt đẩy; không chặn `KeyReady.prepare`
     * đang đợi phía sau trên luồng `kachi-ready`).
     */
    fun onReady(app: Context) {
        if (!ranThisProcess.compareAndSet(false, true)) return
        if (BehindMarksStore(app).read().isEmpty()) return
        BehindHomeRunner.execute("recovery") {
            val measured = try {
                run(app)
            } catch (e: IOException) {
                Log.e(BehindHomeRunner.TAG, "recovery lỗi I/O", e); false
            } catch (e: RuntimeException) {
                Log.e(BehindHomeRunner.TAG, "recovery lỗi", e); false
            }
            // Không đọc được sự thật ⇒ lượt này KHÔNG tính: lần thức sau của cùng tiến trình được đo lại (không mất lượt).
            if (!measured) ranThisProcess.set(false)
        }
    }

    /** `true` = đã đọc được `am stack list` và quyết xong; `false` = không đọc được (kênh đứt / bản đọc rỗng). */
    private fun run(app: Context): Boolean {
        val store = BehindMarksStore(app)
        val marks = store.read()
        if (marks.isEmpty()) return true
        val keys = AdbKeys.ensure(app)
        val entries = StackParse.parse(LocalDeviceShell.run(keys, BehindHomePlan.LIST_CMD) ?: return false)
        if (entries.isEmpty()) return false
        val surfaced = BehindMarks.surfaced(entries, marks)
        if (surfaced) LocalDeviceShell.run(keys, AccessibilityRebind.GO_HOME_UNLESS_CAMERA)
        val kept = BehindMarks.prune(entries, marks)
        if (kept != marks) store.write(kept)
        Log.i(BehindHomeRunner.TAG, "recovery dấu=${marks.size} nổi-lên=$surfaced giữ=${kept.size}")
        return true
    }
}
